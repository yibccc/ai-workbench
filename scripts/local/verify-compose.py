"""Isolated Linux/WSL deployment smoke test. Never touches ai-workbench volumes."""
import base64
import argparse
import ipaddress
import json
import os
import re
import secrets
import shutil
import subprocess
import tempfile
import time
import urllib.error
import urllib.request
import socket
import uuid
from http.cookiejar import CookieJar
from pathlib import Path

PROJECT = "d10deployvalidation"
PORT = 18088
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--skip-build", action="store_true", help="Use already built isolated project images")
parser.add_argument("--browser-node", help="Run the same-browser backend-restart check with this Node executable")
parser.add_argument("--offline-password-check", action="store_true",
                    help="Run fixed password cases while the isolated backend has no public network route")
parser.add_argument("--strace-observe", action="store_true",
                    help="Requires WSL root and strace; count backend network syscalls during offline password cases")
options = parser.parse_args()
if options.strace_observe and not options.offline_password_check:
    parser.error("--strace-observe requires --offline-password-check")
if options.strace_observe and (not hasattr(os, "geteuid") or os.geteuid() != 0):
    parser.error("--strace-observe requires WSL root (wsl -d Ubuntu -u root -- ...)")
if options.strace_observe and not shutil.which("strace"):
    parser.error("--strace-observe requires strace installed in WSL Ubuntu")
offline_dir = tempfile.TemporaryDirectory(prefix="d10-offline-") if options.offline_password_check else None
offline_compose = None
if offline_dir:
    offline_compose = Path(offline_dir.name, "compose.offline.yaml")
    offline_compose.write_text("""services:
  backend:
    networks: [offline]
  postgres:
    networks: [offline]
  redis:
    networks: [offline]
  frontend:
    networks: [default, offline]
networks:
  offline:
    internal: true
""", encoding="utf-8")
env = dict(os.environ, POSTGRES_PORT="15439", REDIS_PORT="16389", APP_PORT=str(PORT), APP_BIND="127.0.0.1",
           POSTGRES_DB="deploy_validation", POSTGRES_USER="deploy_validation",
           POSTGRES_PASSWORD=secrets.token_hex(24), WORKBENCH_BOOTSTRAP_USERNAME="validation",
           WORKBENCH_BOOTSTRAP_PASSWORD=secrets.token_urlsafe(24), WORKBENCH_COOKIE_SECURE="false",
           DEEPSEEK_API_KEY="",
           WORKBENCH_WS_ALLOWED_ORIGINS=f"http://127.0.0.1:{PORT}")
cmd = ["docker", "compose"]
if offline_compose:
    cmd += ["-f", "compose.yaml", "-f", str(offline_compose)]
cmd += ["-p", PROJECT, "--env-file", ".env.example"]

def docker(*args):
    result = subprocess.run(cmd + list(args), env=env, capture_output=True, text=True)
    if result.returncode:
        raise RuntimeError(f"docker compose {' '.join(args)} failed ({result.returncode}):\n{result.stdout[-4000:]}\n{result.stderr[-4000:]}")
    return result.stdout


def require(condition, message):
    if not condition:
        raise AssertionError(message)


def scalar(sql):
    return docker("exec", "-T", "postgres", "psql", "-U", "deploy_validation", "-d",
                  "deploy_validation", "-Atc", sql).strip()


def offline_topology():
    backend_id = docker("ps", "-q", "backend").strip()
    require(backend_id, "Isolated backend container was not found")
    attached = json.loads(subprocess.check_output(
        ["docker", "inspect", "--format", "{{json .NetworkSettings.Networks}}", backend_id], text=True))
    require(len(attached) == 1, "Backend has more than one network during offline check")
    network_name = next(iter(attached))
    network = json.loads(subprocess.check_output(["docker", "network", "inspect", network_name], text=True))[0]
    require(network["Internal"], "Backend network is not Docker-internal")
    routes = docker("exec", "-T", "backend", "cat", "/proc/net/route").splitlines()[1:]
    require(not any(line.split()[1] == "00000000" for line in routes if len(line.split()) > 1),
            "Backend still has a default IP route")
    probe = subprocess.run(cmd + ["exec", "-T", "backend", "curl", "--noproxy", "*",
                                  "--connect-timeout", "3", "--max-time", "5", "--silent",
                                  "--output", "/dev/null", "http://1.1.1.1/"],
                           env=env, capture_output=True, text=True)
    require(probe.returncode != 0, "Backend external TCP probe unexpectedly succeeded")
    require(scalar("select 1;") == "1", "PostgreSQL was unavailable inside offline network")
    require(docker("exec", "-T", "redis", "redis-cli", "PING").strip() == "PONG",
            "Redis was unavailable inside offline network")
    print("Backend internal-only network, no default route, blocked external probe; PostgreSQL/Redis reachable")


def weak_problem(path, body):
    status, response = request(path, body)
    require(status == 400 and "密码过于常见" in json.loads(response).get("detail", ""),
            f"Local weak password was not rejected by {path}")


def offline_password_cases(admin_id, current_password):
    # These are synthetic, fixed v1 cases; no production credentials or model call is used.
    weak = "password123"
    for round_number in (1, 2):
        username = f"offline_password_probe_{round_number}"
        create_password = f"D10-Offline-Create-{round_number}-2026!"
        reset_password = f"D10-Offline-Reset-{round_number}-2026!"
        self_password = f"D10-Offline-Self-{round_number}-2026!"
        account_count = scalar("select count(*) from user_accounts;")
        weak_problem("/api/admin/users", {"username": username, "password": weak, "role": "USER"})
        require(scalar("select count(*) from user_accounts;") == account_count,
                "Weak-password create changed account count")
        status, body = request("/api/admin/users", {"username": username,
                                                    "password": create_password, "role": "USER"})
        require(status == 201, "Compliant account creation failed while backend was offline")
        probe_id = json.loads(body)["id"]
        before_probe = scalar(f"select password_hash from user_accounts where id='{probe_id}';")
        weak_problem(f"/api/admin/users/{probe_id}/reset-password", {"password": weak})
        require(scalar(f"select password_hash from user_accounts where id='{probe_id}';") == before_probe,
                "Weak-password reset changed the stored password")
        require(request(f"/api/admin/users/{probe_id}/reset-password",
                        {"password": reset_password})[0] == 200,
                "Compliant administrator reset failed while backend was offline")
        require(scalar(f"select password_hash from user_accounts where id='{probe_id}';") != before_probe,
                "Compliant administrator reset did not change the stored password")
        before_self = scalar(f"select password_hash from user_accounts where id='{admin_id}';")
        weak_problem("/api/auth/password", {"currentPassword": current_password, "newPassword": weak})
        require(scalar(f"select password_hash from user_accounts where id='{admin_id}';") == before_self
                and request("/api/auth/me")[0] == 200,
                "Weak self-change modified password or current session")
        require(request("/api/auth/password", {"currentPassword": current_password,
                                                "newPassword": self_password})[0] == 200,
                "Compliant self-change failed while backend was offline")
        require(scalar(f"select password_hash from user_accounts where id='{admin_id}';") != before_self
                and request("/api/auth/me")[0] == 200,
                "Compliant self-change did not retain current session")
        current_password = self_password
    require(scalar("select 1;") == "1"
            and docker("exec", "-T", "redis", "redis-cli", "PING").strip() == "PONG",
            "PostgreSQL or Redis failed during offline password cases")
    print("Two offline rounds of create, reset and self-change: weak rejected, compliant accepted")


def observe_offline_password_cases(admin_id, current_password):
    backend_id = docker("ps", "-q", "backend").strip()
    require(backend_id, "Isolated backend container was not found for strace")
    inspected = json.loads(subprocess.check_output(["docker", "inspect", backend_id], text=True))[0]
    require(inspected["Config"]["Labels"].get("com.docker.compose.project") == PROJECT,
            "Refusing to trace a backend outside the fixed isolated project")
    pid = int(inspected["State"]["Pid"])
    require(pid > 0 and Path(f"/proc/{pid}").exists(),
            "Docker backend host PID is not visible from this WSL distribution")
    networks = inspected["NetworkSettings"]["Networks"]
    require(len(networks) == 1, "Backend must have only its internal network during strace")
    network_name = next(iter(networks))
    network = json.loads(subprocess.check_output(["docker", "network", "inspect", network_name], text=True))[0]
    require(network["Internal"], "Refusing to trace without Docker-internal backend network")
    internal_ranges = [ipaddress.ip_network(item["Subnet"], strict=False)
                       for item in network["IPAM"]["Config"] if item.get("Subnet")]
    require(internal_ranges, "Internal Docker network has no inspectable subnet")

    with tempfile.TemporaryDirectory(prefix="d10-strace-") as trace_dir:
        trace_path = Path(trace_dir, "network.calls")
        descriptor = os.open(trace_path, os.O_CREAT | os.O_EXCL | os.O_WRONLY, 0o600)
        os.close(descriptor)
        trace = subprocess.Popen(["strace", "-f", "-qq", "-s", "0", "-e", "trace=%network",
                                  "-o", str(trace_path), "-p", str(pid)],
                                 stdout=subprocess.DEVNULL, stderr=subprocess.PIPE, text=True)
        try:
            time.sleep(0.5)
            if trace.poll() is not None:
                diagnostic = trace.communicate()[1]
                raise RuntimeError(f"strace could not attach to isolated backend (exit {trace.returncode}): "
                                   f"{diagnostic[-500:]}")
            offline_password_cases(admin_id, current_password)
        finally:
            if trace.poll() is None:
                trace.terminate()
            try:
                trace.communicate(timeout=5)
            except subprocess.TimeoutExpired:
                trace.kill()
                trace.communicate()

        trace_text = trace_path.read_text(encoding="utf-8", errors="replace")
        network_calls = len(re.findall(r"\b(?:socket|connect|accept|accept4|sendto|sendmsg|sendmmsg|recvfrom|recvmsg|recvmmsg)\(",
                                       trace_text))
        require(network_calls > 0, "strace attached but recorded no backend network syscalls")
        connect_calls = 0
        outbound_calls = 0
        dns_calls = 0
        external_destinations = set()
        unclassified_outbound = 0
        for line in trace_text.splitlines():
            if re.search(r"\bconnect\(", line):
                connect_calls += 1
            if not re.search(r"\b(?:connect|sendto|sendmsg|sendmmsg)\(", line):
                continue
            outbound_calls += 1
            if re.search(r"sin6?_port=htons\(53\)", line):
                dns_calls += 1
            ipv4 = re.search(r'sin_addr=inet_addr\("([^"]+)"\)', line)
            ipv6 = re.search(r'inet_pton\(AF_INET6, "([^"]+)"\)', line)
            address_text = ipv4.group(1) if ipv4 else ipv6.group(1) if ipv6 else None
            if address_text:
                address = ipaddress.ip_address(address_text)
                if not address.is_loopback and not any(address in subnet for subnet in internal_ranges):
                    external_destinations.add(str(address))
            elif "AF_UNIX" not in line:
                unclassified_outbound += 1
        require(not external_destinations,
                f"Backend attempted an address outside its internal network ({len(external_destinations)} destinations)")
        print("strace backend network syscalls during password cases: "
              f"total={network_calls}, outbound={outbound_calls}, connect={connect_calls}, "
              f"DNS-port={dns_calls}, external-addresses=0, unclassified-outbound={unclassified_outbound}")
        if unclassified_outbound:
            print("strace boundary: some sends had no destination sockaddr; existing socket destinations "
                  "cannot be inferred from this capture alone")


cookie_jar = CookieJar()
http = urllib.request.build_opener(urllib.request.ProxyHandler({}), urllib.request.HTTPCookieProcessor(cookie_jar))
csrf_token = None

def request(path, data=None):
    headers = {}
    if data is not None:
        headers["Content-Type"] = "application/json"
        if csrf_token:
            headers["X-XSRF-TOKEN"] = csrf_token
    req = urllib.request.Request(f"http://127.0.0.1:{PORT}" + path,
                                 data=json.dumps(data).encode() if data is not None else None,
                                 headers=headers)
    try:
        with http.open(req, timeout=15) as response:
            return response.status, response.read()
    except urllib.error.HTTPError as error:
        return error.code, error.read()

def websocket(origin, authenticated=True):
    with socket.create_connection(("127.0.0.1", PORT), timeout=10) as connection:
        headers = ["GET /ws/events HTTP/1.1", f"Host: 127.0.0.1:{PORT}",
                   "Upgrade: websocket", "Connection: Upgrade", "Sec-WebSocket-Version: 13",
                   "Sec-WebSocket-Key: " + base64.b64encode(secrets.token_bytes(16)).decode()]
        if origin:
            headers.append("Origin: " + origin)
        if authenticated:
            cookies = "; ".join(f"{cookie.name}={cookie.value}" for cookie in cookie_jar)
            headers.append("Cookie: " + cookies)
        connection.sendall(("\r\n".join(headers) + "\r\n\r\n").encode())
        response = b""
        while b"\r\n\r\n" not in response and len(response) < 8192:
            chunk = connection.recv(4096)
            if not chunk:
                break
            response += chunk
        head = response.split(b"\r\n\r\n", 1)[0].lower()
        return int(head.split(b" ")[1]), b"sec-websocket-accept:" in head

owned = False
try:
    # Refuse to reuse an existing project's resources; only this invocation owns cleanup.
    if docker("ps", "-aq").strip():
        raise RuntimeError("Validation project already exists")
    existing_volumes = subprocess.check_output(
        ["docker", "volume", "ls", "-q", "--filter", f"label=com.docker.compose.project={PROJECT}"], text=True)
    if existing_volumes.strip():
        raise RuntimeError("Validation project volumes already exist")
    existing_networks = subprocess.check_output(
        ["docker", "network", "ls", "-q", "--filter", f"label=com.docker.compose.project={PROJECT}"], text=True)
    if existing_networks.strip():
        raise RuntimeError("Validation project networks already exist")
    owned = True
    if options.skip_build:
        for service in ("backend", "frontend"):
            subprocess.run(["docker", "image", "inspect", f"{PROJECT}-{service}"],
                           check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    else:
        # Rebuild under this project so old images cannot mask the current config.
        docker("build")
    docker("up", "-d", "--no-build", "--wait", "--wait-timeout", "180")
    print("Four services healthy")
    if options.offline_password_check:
        offline_topology()
    require(request("/")[0] == 200, "SPA root did not return 200")
    require(request("/some/spa/path")[0] == 200, "SPA fallback did not return 200")
    require(request("/api/projects")[0] == 401, "Anonymous business API was not rejected")
    require(request("/api/auth/me")[0] == 401, "Anonymous account lookup was not rejected")
    anonymous_status, anonymous_accept = websocket(f"http://127.0.0.1:{PORT}", authenticated=False)
    require(anonymous_status in (401, 403) and not anonymous_accept, "Anonymous WebSocket was accepted")
    status, body = request("/api/auth/csrf")
    require(status == 200, "CSRF endpoint failed")
    csrf_token = json.loads(body)["token"]
    require(request("/api/auth/login", {"username": "validation", "password": "wrong"})[0] == 401,
            "Invalid login was accepted")
    status, body = request("/api/auth/login", {"username": env["WORKBENCH_BOOTSTRAP_USERNAME"],
                                              "password": env["WORKBENCH_BOOTSTRAP_PASSWORD"]})
    require(status == 200 and json.loads(body)["role"] == "ADMIN", "Bootstrap admin could not log in")
    account_id = json.loads(body)["id"]
    csrf_token = json.loads(request("/api/auth/csrf")[1])["token"]
    require(request("/api/auth/me")[0] == 200, "Authenticated account lookup failed")
    require(request("/api/not-found")[0] == 404, "Unknown API did not return 404")
    require(request("/actuator/health")[0] == 404, "Actuator was exposed through the public ingress")
    require(websocket(f"http://127.0.0.1:{PORT}") == (101, True), "Authenticated WebSocket upgrade failed")
    foreign_origin_status, foreign_origin_accept = websocket("https://evil.example")
    require(foreign_origin_status != 101 and not foreign_origin_accept,
            "Foreign Origin unexpectedly upgraded the WebSocket")
    missing_origin_status, missing_origin_accept = websocket(None)
    require(missing_origin_status != 101 and not missing_origin_accept,
            "Missing Origin unexpectedly upgraded the WebSocket")
    print("Application authentication, SPA/API routing and WebSocket origin checks passed")
    if options.browser_node:
        input_id = str(uuid.uuid4())
        scalar("INSERT INTO capture_inputs (id, content, captured_at, client_request_id, reference_at, "
               "zone_id, status, lease_expires_at, user_id) VALUES "
               f"('{input_id}', 'Isolated browser restart fixture', now(), '{input_id}', now(), "
               f"'Asia/Shanghai', 'PROCESSING', now() + interval '1 day', '{account_id}');")
        script = Path("frontend/e2e/compose-restart.mjs").resolve()
        browser_env = dict(env, D10_INPUT_ID=input_id)
        if options.browser_node.lower().endswith(".exe"):
            script = subprocess.check_output(["wslpath", "-w", str(script)], text=True).strip()
            browser_env["WSLENV"] = ":".join(filter(None, [browser_env.get("WSLENV"),
                "WORKBENCH_BOOTSTRAP_USERNAME/w", "WORKBENCH_BOOTSTRAP_PASSWORD/w", "D10_INPUT_ID/w"]))
        subprocess.run([options.browser_node, str(script)],
                       env=browser_env, check=True)
        print("Same-browser backend restart, STOMP reconnect, fallback GET and browser storage passed")
    status, body = request("/api/projects", {"name": "Deployment persistence fixture"})
    require(status == 200, "Authenticated project creation failed")
    project_id = json.loads(body)["id"]
    before_accounts = scalar("select count(*) || ':' || md5(string_agg(password_hash, '' order by username)) from user_accounts;")
    require(before_accounts.startswith("1:"), "Expected exactly one bootstrap account")
    original_password = env["WORKBENCH_BOOTSTRAP_PASSWORD"]
    reconfigured_username = "unexpected_bootstrap"
    reconfigured_password = secrets.token_urlsafe(24)
    env["WORKBENCH_BOOTSTRAP_USERNAME"] = reconfigured_username
    env["WORKBENCH_BOOTSTRAP_PASSWORD"] = reconfigured_password
    docker("up", "-d", "--no-deps", "--no-build", "--force-recreate", "backend")
    for attempt in range(90):
        try:
            status, body = request("/api/projects")
            if status == 200:
                require(any(item["id"] == project_id for item in json.loads(body)),
                        "Project was lost after backend restart")
                break
        except (OSError, urllib.error.URLError):
            pass
        time.sleep(1)
    else:
        raise AssertionError("Restart persistence check timed out")
    require(scalar("select count(*) || ':' || md5(string_agg(password_hash, '' order by username)) from user_accounts;")
            == before_accounts, "Changing bootstrap config modified the existing account")
    require(scalar("select count(*) from projects;") == "1", "Project count changed after backend recreation")
    require(request("/api/auth/logout", {})[0] == 200, "Existing session could not log out after recreation")
    csrf_token = json.loads(request("/api/auth/csrf")[1])["token"]
    require(request("/api/auth/login", {"username": reconfigured_username,
                                        "password": reconfigured_password})[0] == 401,
            "Changed bootstrap config created a new account")
    status, body = request("/api/auth/login", {"username": "validation", "password": original_password})
    require(status == 200 and json.loads(body)["role"] == "ADMIN", "Original bootstrap credential was changed")
    require("password" not in json.loads(body) and "hash" not in json.loads(body),
            "Login response exposed credential material")
    session_tokens = {cookie.value for cookie in cookie_jar if cookie.name == "WORKBENCH_SESSION"}
    require(session_tokens, "Authenticated session cookie was missing")
    created_password, reset_password, self_password = (secrets.token_urlsafe(24) for _ in range(3))
    response_bodies = [body]
    status, body = request("/api/admin/users", {"username": "credential_probe",
                                                "password": created_password, "role": "USER"})
    require(status == 201, "Credential probe account could not be created")
    response_bodies.append(body)
    probe_id = json.loads(body)["id"]
    status, body = request(f"/api/admin/users/{probe_id}/reset-password", {"password": reset_password})
    require(status == 200, "Credential probe password could not be reset")
    response_bodies.append(body)
    status, body = request("/api/auth/password", {"currentPassword": original_password,
                                                  "newPassword": self_password})
    require(status == 200 and request("/api/auth/me")[0] == 200,
            "Current session did not survive self-password change")
    response_bodies.append(body)
    session_tokens.update(cookie.value for cookie in cookie_jar if cookie.name == "WORKBENCH_SESSION")
    secrets_to_check = (original_password, reconfigured_password, created_password,
                        reset_password, self_password, *session_tokens)
    require(all(secret.encode() not in body for secret in secrets_to_check for body in response_bodies),
            "Account API response exposed credential material")
    password_hashes = scalar("select password_hash from user_accounts order by username;").splitlines()
    require(len(password_hashes) == 2 and all(secret not in password_hash
            for secret in secrets_to_check for password_hash in password_hashes),
            "Credential appeared as a stored password hash")
    logs = docker("logs", "--no-color", "backend", "frontend")
    require(all(secret not in logs for secret in secrets_to_check),
            "Credential appeared in application logs")
    if options.offline_password_check:
        if options.strace_observe:
            observe_offline_password_cases(account_id, self_password)
        else:
            offline_password_cases(account_id, self_password)
        offline_topology()
    print("Changed-bootstrap restart, account lifecycle, session persistence, data and credential-leak checks passed")
    print(scalar("select max(version::int) from flyway_schema_history where success;"))
finally:
    # Fixed isolated project only; never uses the default production project.
    try:
        if owned:
            docker("down", "--volumes", "--remove-orphans")
            require(not docker("ps", "-aq").strip(), "Validation containers remained after cleanup")
            require(not subprocess.check_output(
                ["docker", "volume", "ls", "-q", "--filter", f"label=com.docker.compose.project={PROJECT}"],
                text=True).strip(), "Validation volumes remained after cleanup")
            require(not subprocess.check_output(
                ["docker", "network", "ls", "-q", "--filter", f"label=com.docker.compose.project={PROJECT}"],
                text=True).strip(), "Validation networks remained after cleanup")
    finally:
        if offline_dir:
            offline_dir.cleanup()
