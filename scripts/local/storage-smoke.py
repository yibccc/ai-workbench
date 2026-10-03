"""Real Nginx -> packaged backend -> RustFS boundary cases for verify-compose's owned stack."""
import hashlib
import json
import struct
import urllib.error
import urllib.request
import uuid
import zlib
import concurrent.futures
import time


def pdf_exact(size):
    header = b"%PDF-1.7\n"
    objects = [b"1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n",
               b"2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n",
               b"3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 100 100] >>\nendobj\n"]
    padding = size - 400
    for _ in range(5):
        data = header + b" " * padding
        offsets = []
        for obj in objects:
            offsets.append(len(data))
            data += obj
        xref = len(data)
        data += b"xref\n0 4\n0000000000 65535 f \n" + b"".join(f"{offset:010} 00000 n \n".encode() for offset in offsets)
        data += f"trailer\n<< /Size 4 /Root 1 0 R >>\nstartxref\n{xref}\n%%EOF\n".encode()
        if len(data) == size:
            return data
        padding += size - len(data)
    raise AssertionError("PDF boundary fixture length")


def png_exact(size):
    def chunk(name, content):
        return struct.pack(">I", len(content)) + name + content + struct.pack(">I", zlib.crc32(name + content))
    header = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", 2, 2, 8, 2, 0, 0, 0))
    image = chunk(b"IDAT", zlib.compress(b"\0" + b"\0" * 6 + b"\0" + b"\0" * 6))
    end = chunk(b"IEND", b"")
    return header + image + chunk(b"wbSt", b"\0" * (size - len(header + image + end) - 12)) + end


def storage_cases(http, base, csrf, request, require, docker=None, scalar=None):
    status, body = request("/api/me/posts", {"type": "BLOG", "title": "proxy boundary", "bodyMarkdown": "preserved proxy body"})
    require(status == 201, "Proxy storage draft creation")
    post, version, ids = json.loads(body)["postId"], 0, []

    def upload(name, content):
        boundary = "wb" + uuid.uuid4().hex
        prefix = (f'--{boundary}\r\nContent-Disposition: form-data; name="expectedVersion"\r\n\r\n{version}\r\n'
                  f'--{boundary}\r\nContent-Disposition: form-data; name="requestId"\r\n\r\n{uuid.uuid4()}\r\n'
                  f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="{name}"\r\nContent-Type: application/x-client-lie\r\n\r\n').encode()
        data = prefix + content + f"\r\n--{boundary}--\r\n".encode()
        req = urllib.request.Request(base + f"/api/me/posts/{post}/attachments", data=data,
                                     headers={"Content-Type": "multipart/form-data; boundary=" + boundary, "X-XSRF-TOKEN": csrf})
        try:
            with http.open(req, timeout=90) as response:
                return response.status, json.loads(response.read())
        except urllib.error.HTTPError as error:
            return error.code, json.loads(error.read())

    pdf, png = pdf_exact(20_971_520), png_exact(5_242_880)
    evidence = []
    for index, content in enumerate((pdf, pdf, png, png)):
        status, result = upload("exact.pdf" if index < 2 else "exact.png", content)
        require(status == 201 and result["attachment"]["state"] == "READY", "Exact boundary proxy upload / packaged format worker")
        version = result["version"]
        ident = result["attachment"]["id"]
        ids.append(ident)
        with http.open(base + f"/api/me/posts/{post}/attachments/{ident}", timeout=90) as response:
            digest = hashlib.sha256()
            length = 0
            while block := response.read(65536):
                length += len(block)
                digest.update(block)
            require(length == len(content) and digest.hexdigest() == hashlib.sha256(content).hexdigest(), "Proxy download original SHA/length")
            require(response.headers["Cache-Control"] == "no-store, private" and response.headers["X-Content-Type-Options"] == "nosniff", "Proxy download private headers")
            require(not response.headers.get("Location"), "No storage redirect")
        evidence.append({"size": length, "sha256": digest.hexdigest()})
    require(upload("one.md", b"a")[0] == 409, "50 MiB current collection plus one byte")
    require(upload("over.pdf", pdf_exact(20_971_521))[0] == 413, "20 MiB business file plus one byte after proxy multipart")
    require(upload("over.png", png_exact(5_242_881))[0] == 413, "5 MiB image plus one byte after proxy multipart")
    status, draft = request(f"/api/me/posts/{post}")
    require(status == 200 and json.loads(draft)["draft"]["bodyMarkdown"] == "preserved proxy body", "Boundary failures preserve draft text")
    require(json.loads(draft)["version"] == version, "Boundary failures have no partial reservations")
    print(json.dumps({"proxyStorage": "PASS", "currentTotal": 52_428_800, "files": evidence,
                      "formatWorker": "actual packaged JAR", "provider": "RustFS", "singlePlusOne": 413, "collectionPlusOne": 409}))
    if docker is not None and scalar is not None:
        crash_case(http, base, csrf, request, require, docker, scalar)
    return post, ids


def crash_case(http, base, csrf, request, require, docker, scalar):
    """Kill only verify-compose's freshly owned JVM after its durable reservation commits."""
    status, body = request("/api/me/posts", {"type": "BLOG", "title": "crash recovery", "bodyMarkdown": "body survives JVM kill"})
    require(status == 201, "Crash test draft")
    post = str(uuid.UUID(json.loads(body)["postId"]))
    boundary, ident = "wb" + uuid.uuid4().hex, str(uuid.uuid4())
    content = (f'--{boundary}\r\nContent-Disposition: form-data; name="expectedVersion"\r\n\r\n0\r\n'
               f'--{boundary}\r\nContent-Disposition: form-data; name="requestId"\r\n\r\n{ident}\r\n'
               f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="crash.md"\r\n\r\npersistent reservation'
               f'\r\n--{boundary}--\r\n').encode()

    def pending_upload():
        req = urllib.request.Request(base + f"/api/me/posts/{post}/attachments", data=content,
                                     headers={"Content-Type": "multipart/form-data; boundary=" + boundary, "X-XSRF-TOKEN": csrf})
        try:
            with http.open(req, timeout=90) as response:
                return response.status
        except urllib.error.HTTPError as error:
            return error.code
        except (OSError, TimeoutError):
            return 0

    paused = False
    with concurrent.futures.ThreadPoolExecutor(max_workers=1) as pool:
        try:
            docker("pause", "rustfs")
            paused = True
            future = pool.submit(pending_upload)
            reserved = ""
            for _ in range(100):
                reserved = scalar(f"SELECT id::text FROM community_attachments WHERE post_id='{post}' AND request_id='{ident}' AND state='UPLOADING';")
                if reserved:
                    break
                require(not future.done(), "Upload finished before kill synchronization point")
                time.sleep(0.1)
            require(reserved, "Durable UPLOADING reservation before real JVM kill")
            attachment = str(uuid.UUID(reserved))
            docker("kill", "--signal", "KILL", "backend")
            docker("unpause", "rustfs")
            paused = False
            require(future.result(timeout=20) != 201, "Killed JVM cannot report READY")
            require(scalar(f"SELECT state FROM community_attachments WHERE id='{attachment}';") == "UPLOADING", "Killed process leaves durable pending state")
            # Deterministic lease-expiry injection, not a shortened production reservation policy.
            scalar(f"UPDATE community_attachments SET reservation_expires_at='2000-01-01T00:00:00Z' WHERE id='{attachment}';")
            docker("up", "-d", "--no-deps", "--no-build", "--force-recreate", "backend")
            for _ in range(90):
                try:
                    if request("/api/auth/me")[0] == 200:
                        break
                except (OSError, urllib.error.URLError):
                    pass
                time.sleep(1)
            require(request("/api/auth/me")[0] == 200, "New JVM retains independent Redis session")
            status, result = request(f"/api/me/posts/{post}/attachments/recover", {})
            recovered = json.loads(result)
            require(status == 200 and recovered["version"] == 2 and any(r["attachmentId"] == attachment and r["state"] == "FAILED" for r in recovered["results"]), "Real crash recovery releases quota")
            status, result = request(f"/api/me/posts/{post}")
            draft = json.loads(result)
            require(status == 200 and draft["draft"]["bodyMarkdown"] == "body survives JVM kill" and not draft["draft"]["attachmentIds"], "Crash recovery preserves body and removes reservation only")
            status, result = request(f"/api/me/posts/{post}/attachments/cleanup", {})
            require(status == 200 and any(r["attachmentId"] == attachment and r["state"] == "DELETED" for r in json.loads(result)["results"]), "Crash orphan deletion preserves tombstone/key")
            require(scalar(f"SELECT count(*) FROM community_attachments WHERE id='{attachment}' AND state='DELETED' AND object_key='community/attachments/{attachment}';") == "1", "Crash compensation keeps exact durable key")
            print(json.dumps({"realJvmCrash": "PASS", "syncPoint": "committed UPLOADING while RustFS paused", "killSignal": "KILL", "newJvm": True,
                              "deadlineInjection": "explicit past timestamp", "recoverVersion": 2, "bodyPreserved": True, "tombstonePreserved": True}))
        finally:
            if paused:
                docker("unpause", "rustfs")
