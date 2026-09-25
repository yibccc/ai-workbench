"""Isolated Linux/WSL deployment smoke test. Never touches ai-workbench volumes."""
import base64
import argparse
import json
import os
import secrets
import subprocess
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
options = parser.parse_args()
env = dict(os.environ, POSTGRES_PORT="15439", REDIS_PORT="16389", APP_PORT=str(PORT), APP_BIND="127.0.0.1",
           POSTGRES_DB="deploy_validation", POSTGRES_USER="deploy_validation",
           POSTGRES_PASSWORD=secrets.token_hex(24), WORKBENCH_BOOTSTRAP_USERNAME="validation",
           WORKBENCH_BOOTSTRAP_PASSWORD=secrets.token_urlsafe(24), WORKBENCH_COOKIE_SECURE="false",
           DEEPSEEK_API_KEY="",
           WORKBENCH_WS_ALLOWED_ORIGINS=f"http://127.0.0.1:{PORT}")
cmd = ["docker", "compose", "-p", PROJECT, "--env-file", ".env.example"]

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
    print("Changed-bootstrap restart, account lifecycle, session persistence, data and credential-leak checks passed")
    print(scalar("select max(version::int) from flyway_schema_history where success;"))
finally:
    # Fixed isolated project only; never uses the default production project.
    if owned:
        docker("down", "--volumes", "--remove-orphans")
