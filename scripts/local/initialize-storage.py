"""Initialize an explicitly selected private RustFS bucket and restricted application identity.

Credentials come from an ignored env file or the process environment, never command
arguments or output. Existing bucket policies, public ACLs, or conflicting named
policies fail closed. No bucket/object inventory, lifecycle rule, or bulk delete.
"""
from __future__ import annotations
import argparse
import datetime as dt
import hashlib
import hmac
import http.client
import json
import os
from pathlib import Path
import re
import sys
from urllib.parse import quote, urlsplit
import uuid
import xml.etree.ElementTree as ET

ADMIN = "/rustfs/admin/v3/"
PREFIX = "community/attachments/"


class SetupFailure(Exception):
    def __init__(self, stage, status=None):
        self.stage, self.status = stage, status
        super().__init__(stage)


def load_env(path):
    values = dict(os.environ)
    if path:
        for line in Path(path).read_text(encoding="utf-8-sig").splitlines():
            if not line.strip() or line.lstrip().startswith("#"):
                continue
            match = re.fullmatch(r"([A-Za-z_][A-Za-z0-9_]*)=(.*)", line)
            if not match:
                raise SetupFailure("env-file-format")
            value = match[2].strip()
            if len(value) >= 2 and value[0] == value[-1] and value[0] in "\"'":
                value = value[1:-1]
            values[match[1]] = value
    return values


def policy_for(bucket):
    return {"Version": "2012-10-17", "Statement": [
        {"Effect": "Allow", "Action": ["s3:PutObject", "s3:GetObject", "s3:DeleteObject"],
         "Resource": [f"arn:aws:s3:::{bucket}/{PREFIX}*"]},
        {"Effect": "Deny", "Action": ["admin:*"]}]}


def policy_shape(policy):
    statements = []
    for item in policy.get("Statement", []):
        if any(item.get(k) for k in ("NotAction", "NotResource", "Principal", "NotPrincipal")):
            raise SetupFailure("existing-policy-shape")
        actions, resources = item.get("Action", []), item.get("Resource", [])
        statements.append(json.dumps({"Effect": item.get("Effect"), "Action": sorted([actions] if isinstance(actions, str) else actions),
                                      "Resource": sorted([resources] if isinstance(resources, str) else resources), "Condition": item.get("Condition") or {}}, sort_keys=True))
    return policy.get("Version"), tuple(sorted(statements))


class Client:
    def __init__(self, endpoint, access="", secret="", region="us-east-1"):
        self.endpoint, self.access, self.secret, self.region = urlsplit(endpoint), access, secret, region

    def request(self, method, path, query=None, data=b"", headers=None):
        path = quote(path, safe="/-_.~")
        query = "&".join(f"{quote(str(k), safe='-_.~')}={quote(str(v), safe='-_.~')}" for k, v in sorted((query or {}).items()))
        actual = {"host": self.endpoint.netloc}
        actual.update({k.lower(): v for k, v in (headers or {}).items()})
        if self.access:
            now = dt.datetime.now(dt.timezone.utc)
            stamp, day = now.strftime("%Y%m%dT%H%M%SZ"), now.strftime("%Y%m%d")
            digest = hashlib.sha256(data).hexdigest()
            actual.update({"x-amz-date": stamp, "x-amz-content-sha256": digest})
            names = sorted(actual)
            canonical = "\n".join((method, path, query, "".join(k + ":" + actual[k].strip() + "\n" for k in names), ";".join(names), digest))
            scope = f"{day}/{self.region}/s3/aws4_request"
            string_to_sign = "\n".join(("AWS4-HMAC-SHA256", stamp, scope, hashlib.sha256(canonical.encode()).hexdigest()))
            key = ("AWS4" + self.secret).encode()
            for value in (day, self.region, "s3", "aws4_request"):
                key = hmac.new(key, value.encode(), hashlib.sha256).digest()
            signature = hmac.new(key, string_to_sign.encode(), hashlib.sha256).hexdigest()
            actual["authorization"] = f"AWS4-HMAC-SHA256 Credential={self.access}/{scope}, SignedHeaders={';'.join(names)}, Signature={signature}"
        connection_class = http.client.HTTPSConnection if self.endpoint.scheme == "https" else http.client.HTTPConnection
        connection = connection_class(self.endpoint.hostname, self.endpoint.port, timeout=20)
        try:
            connection.request(method, path + ("?" + query if query else ""), body=data, headers=actual)
            response = connection.getresponse()
            body = response.read(1_048_577)
            if len(body) > 1_048_576:
                raise SetupFailure("bounded-response")
            return response.status, body
        finally:
            connection.close()


def expect(stage, result, statuses):
    if result[0] not in statuses:
        raise SetupFailure(stage, result[0])
    return result[1]


def initialize(values, endpoint, bucket):
    uri = urlsplit(endpoint)
    if uri.scheme not in ("http", "https") or uri.hostname not in ("127.0.0.1", "localhost", "rustfs") or uri.username or uri.password or uri.path not in ("", "/") or uri.query or uri.fragment:
        raise SetupFailure("explicit-local-endpoint")
    if values.get("WORKBENCH_STORAGE_ENDPOINT") != endpoint or values.get("WORKBENCH_STORAGE_BUCKET") != bucket or not re.fullmatch(r"[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]", bucket):
        raise SetupFailure("explicit-target-matches-config")
    keys = ("RUSTFS_ACCESS_KEY", "RUSTFS_SECRET_KEY", "WORKBENCH_STORAGE_ACCESS_KEY", "WORKBENCH_STORAGE_SECRET_KEY")
    if any(not values.get(k) or values[k].startswith("replace_with_") for k in keys):
        raise SetupFailure("configure-separate-random-credentials")
    if values[keys[0]] == values[keys[2]] or values[keys[1]] == values[keys[3]]:
        raise SetupFailure("management-application-separation")
    region = values.get("WORKBENCH_STORAGE_REGION", "us-east-1")
    root = Client(endpoint, values[keys[0]], values[keys[1]], region)
    app = Client(endpoint, values[keys[2]], values[keys[3]], region)
    anonymous = Client(endpoint)
    status = root.request("HEAD", "/" + bucket)[0]
    if status == 404:
        expect("create-private-bucket", root.request("PUT", "/" + bucket), (200,))
    elif status != 200:
        raise SetupFailure("bucket-existence", status)
    expect("bucket-has-no-policy", root.request("GET", "/" + bucket, {"policy": ""}), (404,))
    acl = ET.fromstring(expect("bucket-acl", root.request("GET", "/" + bucket, {"acl": ""}), (200,)))
    if any(e.tag.endswith("URI") and e.text and ("AllUsers" in e.text or "AuthenticatedUsers" in e.text) for e in acl.iter()):
        raise SetupFailure("bucket-is-private")
    policy, name = policy_for(bucket), "workbench-attachments-" + bucket
    policies = json.loads(expect("list-application-policies", root.request("GET", ADMIN + "list-canned-policies"), (200,)))
    if not isinstance(policies, dict):
        raise SetupFailure("policy-list-shape")
    if name in policies:
        info = json.loads(expect("read-existing-policy", root.request("GET", ADMIN + "info-canned-policy", {"name": name}), (200,)))
        if policy_shape(info["policy"]) != policy_shape(policy):
            raise SetupFailure("conflicting-existing-policy")
    else:
        expect("create-application-policy", root.request("PUT", ADMIN + "add-canned-policy", {"name": name}, json.dumps(policy).encode(), {"content-type": "application/json"}), (200,))
    user = root.request("GET", ADMIN + "user-info", {"accessKey": values[keys[2]]})
    user_data = json.dumps({"secretKey": values[keys[3]], "status": "enabled", "policy": name}).encode()
    if user[0] in (400, 404):
        expect("create-application-user", root.request("PUT", ADMIN + "add-user", {"accessKey": values[keys[2]]}, user_data, {"content-type": "application/json"}), (200,))
    elif user[0] == 200:
        info = json.loads(user[1])
        previous_name = info.get("policyName")
        if not previous_name or info.get("status") != "enabled":
            raise SetupFailure("conflicting-existing-application-user")
        previous = json.loads(expect("read-existing-user-policy", root.request("GET", ADMIN + "info-canned-policy", {"name": previous_name}), (200,)))
        if policy_shape(previous["policy"]) != policy_shape(policy):
            raise SetupFailure("conflicting-existing-application-user-policy")
    else:
        raise SetupFailure("application-user-existence", user[0])
    # The explicit designated application account receives only the declared policy; never replace its password.
    expect("bind-application-policy", root.request("PUT", ADMIN + "set-user-or-group-policy", {"policyName": name, "userOrGroup": values[keys[2]], "isGroup": "false"}), (200,))
    check = json.loads(expect("read-application-policy", root.request("GET", ADMIN + "info-canned-policy", {"name": name}), (200,)))
    if policy_shape(check["policy"]) != policy_shape(policy):
        raise SetupFailure("policy-read-back")
    key = PREFIX + str(uuid.uuid4())
    payload = b"# Workbench private storage verification\n"
    try:
        expect("application-put", app.request("PUT", "/" + bucket + "/" + key, data=payload), (200,))
        downloaded = expect("application-get", app.request("GET", "/" + bucket + "/" + key), (200,))
        if downloaded != payload:
            raise SetupFailure("original-bytes")
        for method in ("GET", "HEAD"):
            expect("anonymous-" + method.lower(), anonymous.request(method, "/" + bucket + "/" + key), (403,))
        expect("outside-prefix-denied", app.request("PUT", "/" + bucket + "/outside/" + str(uuid.uuid4()), data=b"denied"), (403,))
        expect("bucket-policy-management-denied", app.request("PUT", "/" + bucket, {"policy": ""}, b"{}", {"content-type": "application/json"}), (403,))
        expect("bucket-acl-management-denied", app.request("PUT", "/" + bucket, {"acl": ""}, headers={"x-amz-acl": "private"}), (403,))
        expect("admin-management-denied", app.request("GET", ADMIN + "list-users"), (403,))
        expect("self-management-denied", app.request("PUT", ADMIN + "add-user", {"accessKey": values[keys[2]]}, user_data, {"content-type": "application/json"}), (403,))
        expect("application-delete", app.request("DELETE", "/" + bucket + "/" + key), (204,))
        expect("object-absent-after-delete", root.request("HEAD", "/" + bucket + "/" + key), (404,))
    finally:
        expect("remove-exact-probe-object", root.request("DELETE", "/" + bucket + "/" + key), (204,))
    return {"status": "initialized-and-verified", "endpoint": endpoint, "bucket": bucket,
            "private": True, "managementSeparated": True, "applicationOperations": "Put/Get/Delete in community/attachments only"}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--env-file")
    parser.add_argument("--endpoint", required=True)
    parser.add_argument("--bucket", required=True)
    args = parser.parse_args()
    try:
        print(json.dumps(initialize(load_env(args.env_file), args.endpoint, args.bucket)))
        return 0
    except SetupFailure as error:
        print(json.dumps({"status": "failed", "stage": error.stage, "httpStatus": error.status}))
    except Exception as error:
        print(json.dumps({"status": "failed", "errorType": type(error).__name__}))
    return 1


if __name__ == "__main__":
    sys.exit(main())
