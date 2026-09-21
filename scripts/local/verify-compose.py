"""Isolated Linux/WSL deployment smoke test. Never touches ai-workbench volumes."""
import base64
import json
import os
import secrets
import subprocess
import time
import urllib.error
import urllib.request
import socket

PROJECT = "d10deployvalidation"
PORT = 18088
env = dict(os.environ, POSTGRES_PORT="15439", REDIS_PORT="16389", APP_PORT=str(PORT), APP_BIND="127.0.0.1",
           POSTGRES_DB="deploy_validation", POSTGRES_USER="deploy_validation",
           POSTGRES_PASSWORD=secrets.token_hex(24), WORKBENCH_AUTH_USER="validation",
           WORKBENCH_AUTH_PASSWORD=secrets.token_hex(24), DEEPSEEK_API_KEY="",
           WORKBENCH_WS_ALLOWED_ORIGINS=f"http://127.0.0.1:{PORT}")
cmd = ["docker", "compose", "-p", PROJECT, "--env-file", ".env.example"]

def docker(*args):
    return subprocess.run(cmd + list(args), env=env, check=True, capture_output=True, text=True).stdout

auth = "Basic " + base64.b64encode(
    (env["WORKBENCH_AUTH_USER"] + ":" + env["WORKBENCH_AUTH_PASSWORD"]).encode()).decode()
http = urllib.request.build_opener(urllib.request.ProxyHandler({}))

def request(path, authorization=auth, data=None):
    headers = {"Authorization": authorization} if authorization else {}
    if data is not None:
        headers["Content-Type"] = "application/json"
    req = urllib.request.Request(f"http://127.0.0.1:{PORT}" + path,
                                 data=json.dumps(data).encode() if data is not None else None,
                                 headers=headers)
    try:
        with http.open(req, timeout=15) as response:
            return response.status, response.read()
    except urllib.error.HTTPError as error:
        return error.code, error.read()

def websocket(origin, authorization=auth):
    with socket.create_connection(("127.0.0.1", PORT), timeout=10) as connection:
        headers = ["GET /ws/events HTTP/1.1", f"Host: 127.0.0.1:{PORT}",
                   "Upgrade: websocket", "Connection: Upgrade", "Sec-WebSocket-Version: 13",
                   "Sec-WebSocket-Key: " + base64.b64encode(secrets.token_bytes(16)).decode()]
        if origin:
            headers.append("Origin: " + origin)
        if authorization:
            headers.append("Authorization: " + authorization)
        connection.sendall(("\r\n".join(headers) + "\r\n\r\n").encode())
        return int(connection.recv(4096).split(b" ")[1])

owned = False
try:
    # Refuse to reuse an existing project's resources; only this invocation owns cleanup.
    assert not docker("ps", "-aq").strip(), "Validation project already exists"
    existing_volumes = subprocess.check_output(
        ["docker", "volume", "ls", "-q", "--filter", f"label=com.docker.compose.project={PROJECT}"], text=True)
    assert not existing_volumes.strip(), "Validation project volumes already exist"
    for password in ["", "x" * 73, "first\nsecond"]:
        result = subprocess.run(["docker", "run", "--rm", "--entrypoint",
                                 "/docker-entrypoint.d/40-workbench-auth.sh",
                                 "-e", "WORKBENCH_AUTH_USER", "-e", "WORKBENCH_AUTH_PASSWORD",
                                 PROJECT + "-frontend"],
                                env=dict(env, WORKBENCH_AUTH_PASSWORD=password), capture_output=True)
        assert result.returncode != 0, "Invalid credential accepted"
    owned = True
    docker("up", "-d", "--no-build", "--wait", "--wait-timeout", "180")
    print("Four services healthy")
    for path in ["/", "/api/projects", "/ws/events"]:
        assert request(path, None)[0] == 401
        assert request(path, "Basic d3Jvbmc6d3Jvbmc=")[0] == 401
    assert request("/")[0] == 200
    assert request("/some/spa/path")[0] == 200
    assert request("/api/not-found")[0] == 404
    assert request("/actuator/health")[0] == 404
    assert websocket(f"http://127.0.0.1:{PORT}") == 101
    assert websocket("https://evil.example") == 403
    assert websocket(None, None) == 401
    # Authenticated non-browser clients need no Origin; Origin isn't authentication.
    assert websocket(None) == 101
    print("Authentication, SPA/API routing and WebSocket origin checks passed")
    status, body = request("/api/projects", data={"name": "Deployment persistence fixture"})
    assert status == 200
    project_id = json.loads(body)["id"]
    docker("restart", "backend")
    for attempt in range(90):
        try:
            status, body = request("/api/projects")
            if status == 200:
                assert any(item["id"] == project_id for item in json.loads(body))
                break
        except (OSError, urllib.error.URLError):
            pass
        time.sleep(1)
    else:
        raise AssertionError("Restart persistence check timed out")
    print("Restart persistence passed")
    print(docker("exec", "-T", "postgres", "psql", "-U", "deploy_validation", "-d", "deploy_validation",
                 "-Atc", "select max(version::int) from flyway_schema_history where success;").strip())
finally:
    # Fixed isolated project only; never uses the default production project.
    if owned:
        docker("down", "--volumes", "--remove-orphans")
