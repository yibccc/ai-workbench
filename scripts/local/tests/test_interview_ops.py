"""Safety boundaries for private storage policy maintenance and complete bundles."""
import copy
from dataclasses import asdict
import hashlib
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
from pathlib import Path
import runpy
import tempfile
import threading
from argparse import Namespace
import unittest
from unittest.mock import patch
import uuid

SCRIPTS = Path(__file__).resolve().parents[1]
storage = runpy.run_path(str(SCRIPTS / "initialize-storage.py"))
bundle = runpy.run_path(str(SCRIPTS / "interview-backup.py"))


class PolicyMaintenanceTest(unittest.TestCase):
    def setUp(self):
        self.bucket = "private-ops-test"
        self.old = storage["policy_for"](self.bucket, (storage["PREFIXES"][0],))
        self.values = {"RUSTFS_ACCESS_KEY": "synthetic-root", "RUSTFS_SECRET_KEY": "root-secret",
                       "WORKBENCH_STORAGE_ACCESS_KEY": "synthetic-app", "WORKBENCH_STORAGE_SECRET_KEY": "app-secret",
                       "WORKBENCH_STORAGE_ENDPOINT": "http://127.0.0.1:9000", "WORKBENCH_STORAGE_BUCKET": self.bucket}
        self.writes = []

    def initialize(self, policy, update=False, previous_name=None):
        shared_name = "workbench-attachments-" + self.bucket
        name = storage["policy_name_for"](self.bucket, self.values["WORKBENCH_STORAGE_ACCESS_KEY"])
        policies = {shared_name: copy.deepcopy(policy)}
        if previous_name == name:
            policies[name] = copy.deepcopy(policy)
        test = self

        class FakeClient:
            def __init__(self, *args):
                self.is_root = len(args) > 1 and args[1] == "synthetic-root"

            def request(self, method, path, query=None, data=b"", headers=None, **kwargs):
                if not self.is_root:
                    return 403, b""
                if method in ("PUT", "DELETE"):
                    test.writes.append((method, path, query, data))
                    if path.endswith("add-canned-policy"):
                        policies[query["name"]] = json.loads(data)
                    return 200, b""
                if method == "HEAD":
                    return 200, b""
                if query == {"policy": ""}:
                    return 404, b""
                if query == {"acl": ""}:
                    return 200, b"<AccessControlPolicy><AccessControlList/></AccessControlPolicy>"
                if path.endswith("list-canned-policies"):
                    return 200, json.dumps({key: {} for key in policies}).encode()
                if path.endswith("info-canned-policy"):
                    return 200, json.dumps({"policy": policies[query["name"]]}).encode()
                if path.endswith("user-info"):
                    return 200, json.dumps({"status": "enabled", "policyName": previous_name or shared_name}).encode()
                return 403, b""

        initialize = storage["initialize"]
        with patch.dict(initialize.__globals__, Client=FakeClient, probe_prefix=lambda *args: None,
                        expect=lambda stage, result, statuses: storage["expect"](stage, result, statuses)
                        if stage not in {"remove-exact-probe-object", "outside-prefix-denied", "bucket-list-denied",
                                         "bucket-policy-management-denied", "bucket-acl-management-denied",
                                         "admin-management-denied", "self-management-denied"} else b""):
            return initialize(self.values, self.values["WORKBENCH_STORAGE_ENDPOINT"], self.bucket,
                              self.values["WORKBENCH_STORAGE_ACCESS_KEY"], update)

    def test_old_policy_requires_explicit_maintenance_before_any_write(self):
        with self.assertRaises(storage["SetupFailure"]):
            self.initialize(self.old)
        self.assertEqual([], self.writes)

    def test_explicit_known_policy_update_preserves_password_and_deny(self):
        result = self.initialize(self.old, update=True)
        self.assertEqual("initialized-and-verified", result["status"])
        policies = [json.loads(data) for _, path, _, data in self.writes if path.endswith("add-canned-policy")]
        self.assertEqual([storage["policy_for"](self.bucket)], policies)
        self.assertTrue(all(query["name"] != "workbench-attachments-" + self.bucket
                            for _, path, query, _ in self.writes if path.endswith("add-canned-policy")))
        self.assertFalse(any(path.endswith("add-user") for _, path, _, _ in self.writes))

    def test_existing_dedicated_canonical_policy_needs_no_maintenance(self):
        name = storage["policy_name_for"](self.bucket, self.values["WORKBENCH_STORAGE_ACCESS_KEY"])
        self.initialize(storage["policy_for"](self.bucket), previous_name=name)
        self.assertFalse(any(path.endswith("add-canned-policy") or path.endswith("add-user")
                             for _, path, _, _ in self.writes))

    def test_shared_canonical_policy_is_rebound_only_by_explicit_maintenance(self):
        with self.assertRaises(storage["SetupFailure"]):
            self.initialize(storage["policy_for"](self.bucket))
        self.assertEqual([], self.writes)
        self.initialize(storage["policy_for"](self.bucket), update=True)
        bind = [query for _, path, query, _ in self.writes if path.endswith("set-user-or-group-policy")]
        self.assertEqual([{"policyName": storage["policy_name_for"](self.bucket, self.values["WORKBENCH_STORAGE_ACCESS_KEY"]),
                           "userOrGroup": self.values["WORKBENCH_STORAGE_ACCESS_KEY"], "isGroup": "false"}], bind)

    def test_broad_unknown_policy_cannot_be_overwritten(self):
        policy = copy.deepcopy(self.old)
        policy["Statement"][0]["Resource"] = [f"arn:aws:s3:::{self.bucket}/*"]
        with self.assertRaises(storage["SetupFailure"]):
            self.initialize(policy, update=True)
        self.assertEqual([], self.writes)

    def test_conflicting_user_binding_is_rejected_before_policy_update(self):
        with self.assertRaises(storage["SetupFailure"]):
            self.initialize(self.old, update=True, previous_name="foreign-policy")
        self.assertEqual([], self.writes)

    def test_unknown_semantic_policy_fields_fail_closed(self):
        policy = copy.deepcopy(self.old)
        policy["Statement"][0]["NotResource"] = []
        with self.assertRaises(storage["SetupFailure"]):
            storage["policy_shape"](policy)


class BundleCompletenessTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.folder = Path(self.temporary.name)
        self.payload = b"\xef\xbb\xbf# Private original\r\n"
        key = "interview/resumes/" + str(uuid.uuid4()) + "/" + str(uuid.uuid4()) + ".md"
        self.entry = bundle["ObjectEntry"].parse({"key": key, "contentType": "text/markdown; charset=UTF-8", "size": len(self.payload),
                         "sha256": hashlib.sha256(self.payload).hexdigest(), "references": [{"kind": "current-resume", "owner": str(uuid.uuid4()), "id": str(uuid.uuid4())}],
                         "file": hashlib.sha256(key.encode()).hexdigest() + ".bin"})

    def tearDown(self):
        self.temporary.cleanup()

    def complete_bundle(self):
        (self.folder / "objects").mkdir()
        (self.folder / "objects" / self.entry.file).write_bytes(self.payload)
        (self.folder / "database.dump").write_bytes(b"synthetic custom dump bytes")
        bundle["write_json"](self.folder / "manifest.json", {"objects": [asdict(self.entry)]})
        bundle["write_json"](self.folder / "complete.json", {"format": "workbench-private-bundle-v1", "sha256": {
            name: bundle["digest"](self.folder / name) for name in ("database.dump", "manifest.json")}})

    def test_valid_exact_original_bytes_verify(self):
        self.complete_bundle()
        self.assertEqual([asdict(self.entry)], bundle["verify_bundle"](self.folder)["objects"])

    def test_incomplete_bundle_is_never_restoreable(self):
        (self.folder / "incomplete.json").write_text("{}", encoding="utf-8")
        with self.assertRaises(bundle["BundleFailure"]):
            bundle["verify_bundle"](self.folder)

    def test_coexisting_incomplete_marker_cannot_claim_success(self):
        self.complete_bundle()
        (self.folder / "incomplete.json").write_text("{}", encoding="utf-8")
        with self.assertRaises(bundle["BundleFailure"]):
            bundle["verify_bundle"](self.folder)

    def test_completion_cleanup_failure_never_installs_success(self):
        incomplete = self.folder / "incomplete.json"
        incomplete.mkdir()  # Simulate a marker that cannot be unlinked.
        complete = self.folder / "complete.json"
        with self.assertRaises(OSError):
            bundle["finish_run"](incomplete, complete, {"status": "complete"})
        self.assertTrue(incomplete.exists())
        self.assertFalse(complete.exists())

    def test_completion_write_failure_keeps_a_failure_marker(self):
        incomplete = self.folder / "incomplete.json"
        incomplete.write_text("{}", encoding="utf-8")
        complete = self.folder / "complete.json"
        original = bundle["write_json"]

        def fail_completion(path, value):
            if path == complete:
                raise OSError("simulated marker write failure")
            original(path, value)

        with patch.dict(bundle["finish_run"].__globals__, write_json=fail_completion):
            with self.assertRaises(OSError):
                bundle["finish_run"](incomplete, complete, {"status": "complete"})
        self.assertEqual("incomplete", json.loads(incomplete.read_text(encoding="utf-8"))["status"])
        self.assertFalse(complete.exists())

    def test_missing_required_object_and_same_length_corruption_fail(self):
        self.complete_bundle()
        path = self.folder / "objects" / self.entry.file
        path.unlink()
        with self.assertRaises(bundle["BundleFailure"]):
            bundle["verify_bundle"](self.folder)
        path.write_bytes(b"x" * len(self.payload))
        with self.assertRaises(bundle["BundleFailure"]):
            bundle["verify_bundle"](self.folder)

    def test_dump_or_manifest_corruption_fails(self):
        self.complete_bundle()
        (self.folder / "database.dump").write_bytes(b"tampered")
        with self.assertRaises(bundle["BundleFailure"]):
            bundle["verify_bundle"](self.folder)

    def test_path_traversal_and_arbitrary_object_prefix_are_rejected(self):
        value = asdict(self.entry)
        value["file"] = "../../outside.md"
        with self.assertRaises(bundle["BundleFailure"]):
            bundle["ObjectEntry"].parse(value)
        value = asdict(self.entry)
        value["key"] = "outside/" + str(uuid.uuid4())
        with self.assertRaises(bundle["BundleFailure"]):
            bundle["ObjectEntry"].parse(value)

    def test_existing_database_and_source_database_are_rejected(self):
        class ExistingDatabase:
            def scalar(self, *args):
                return "postgres\nd10_restore_existing"
        with self.assertRaises(bundle["BundleFailure"]):
            bundle["Docker"].absent_database(ExistingDatabase(), "container", "user", "d10_restore_existing")
        with self.assertRaises(bundle["BundleFailure"]):
            bundle["fresh_database"]("d10_restore_source", "d10_restore_source")

    def test_new_container_cannot_reuse_unprepared_old_or_source_volume(self):
        def container(volume, role):
            return {"Mounts": [{"Type": "volume", "Name": volume, "Destination": "/data" if role == "rustfs" else "/var/lib/postgresql/data"}],
                    "Config": {"Labels": {"com.docker.compose.project": "new-project", "com.docker.compose.service": role}}}
        class OldVolume:
            def run(self, *args):
                return json.dumps([{"CreatedAt": "2025-01-01T01:00:00+00:00", "Labels": {"com.docker.compose.project": "new-project"}}]).encode()
        source = {"pgVolumes": ["source-pg"], "rustfsVolumes": ["source-fs"]}
        with self.assertRaises(bundle["BundleFailure"]):
            bundle["fresh_target_volumes"](OldVolume(), container("target-pg", "postgres"), container("target-fs", "rustfs"), source, {})
        with self.assertRaises(bundle["BundleFailure"]):
            bundle["fresh_target_volumes"](OldVolume(), container("source-pg", "postgres"), container("target-fs", "rustfs"), source, {})

    def test_streaming_download_has_independent_exact_size_gate(self):
        payload = self.payload
        class Handler(BaseHTTPRequestHandler):
            def do_GET(self):
                self.send_response(200)
                self.send_header("Content-Length", str(len(payload)))
                self.end_headers()
                self.wfile.write(payload)
            def log_message(self, *args):
                pass
        server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        try:
            client = storage["Client"]("http://127.0.0.1:" + str(server.server_port))
            path = self.folder / "original"
            self.assertEqual(200, client.request("GET", "/original", response_file=path, expected_size=len(payload))[0])
            self.assertEqual(payload, path.read_bytes())
            with self.assertRaises(storage["SetupFailure"]):
                client.request("GET", "/original", response_file=self.folder / "oversize", expected_size=len(payload) - 1)
            with self.assertRaises(storage["SetupFailure"]):
                client.request("GET", "/original", response_file=self.folder / "short", expected_size=len(payload) + 1)
        finally:
            server.shutdown()
            server.server_close()
            thread.join()


class TargetVolumeSafetyTest(unittest.TestCase):
    def setUp(self):
        self.project = "new-project"
        self.source = {"pgVolumes": ["source-pg"], "rustfsVolumes": ["source-fs"]}
        self.volumes = {}
        self.containers = {}
        for role, destination in (("postgres", "/var/lib/postgresql/data"), ("rustfs", "/data")):
            name = self.project + "_" + {"postgres": "postgres-data", "rustfs": "rustfs-data"}[role]
            self.volumes[name] = {"Name": name, "CreatedAt": "2026-10-04T01:00:00+00:00", "Driver": "local", "Options": None, "Scope": "local",
                                  "Labels": {"com.docker.compose.project": self.project,
                                             "com.docker.compose.volume": "postgres-data" if role == "postgres" else "rustfs-data"}}
            self.containers[role] = {"Id": role + "-container", "Mounts": [{"Type": "volume", "Name": name, "Destination": destination}],
                                     "Config": {"Labels": {"com.docker.compose.project": self.project, "com.docker.compose.service": role}}}
        self.proof = {"format": "workbench-fresh-volumes-v1", "project": self.project, "volumes": {
            role: {"name": info["Mounts"][0]["Name"], "identity": bundle["volume_identity"](self.volumes[info["Mounts"][0]["Name"]])}
            for role, info in self.containers.items()}}
        test = self

        class FakeDocker:
            def run(self, *args):
                if args[:2] == ("volume", "inspect"):
                    return json.dumps([test.volumes[args[-1]]]).encode()
                if args[0] == "ps":
                    name = args[-1].removeprefix("volume=")
                    return "\n".join(info["Id"] for info in test.containers.values()
                                     if any(m["Name"] == name for m in info["Mounts"])).encode()
                raise AssertionError("Unexpected Docker invocation")

        self.docker = FakeDocker()

    def verify(self):
        bundle["fresh_target_volumes"](self.docker, self.containers["postgres"], self.containers["rustfs"], self.source, self.proof)

    def test_prepared_exact_roles_and_exclusive_mounts_pass(self):
        self.verify()

    def test_swapped_volume_roles_are_refused_even_with_same_creation_time(self):
        pg, fs = self.containers["postgres"]["Mounts"][0], self.containers["rustfs"]["Mounts"][0]
        pg["Name"], fs["Name"] = fs["Name"], pg["Name"]
        with self.assertRaises(bundle["BundleFailure"]):
            self.verify()

    def test_recreated_or_foreign_owned_volume_cannot_use_old_proof(self):
        name = self.containers["postgres"]["Mounts"][0]["Name"]
        self.volumes[name]["CreatedAt"] = "2026-10-04T01:00:01+00:00"
        with self.assertRaises(bundle["BundleFailure"]):
            self.verify()
        self.volumes[name]["Labels"]["com.docker.compose.project"] = "foreign"
        with self.assertRaises(bundle["BundleFailure"]):
            self.verify()

    def test_other_stopped_container_mounting_target_is_refused(self):
        self.containers["other"] = copy.deepcopy(self.containers["postgres"])
        self.containers["other"]["Id"] = "foreign-stopped-container"
        with self.assertRaises(bundle["BundleFailure"]):
            self.verify()

    def test_wrong_data_mount_destination_is_refused(self):
        self.containers["postgres"]["Mounts"][0]["Destination"] = "/tmp"
        with self.assertRaises(bundle["BundleFailure"]):
            self.verify()

    def test_prepare_refuses_existing_volume_before_mutation(self):
        with tempfile.TemporaryDirectory() as folder:
            args = Namespace(target_project=self.project, target_pg_volume=self.project + "_postgres-data",
                             target_rustfs_volume=self.project + "_rustfs-data", fresh_volumes_record=str(Path(folder) / "proof.json"))
            calls = []

            class ExistingVolume:
                def run(self, *command):
                    calls.append(command)
                    self.assertion = command == ("volume", "ls", "--format", "{{.Name}}")
                    if not self.assertion:
                        raise AssertionError("Existing volume must not be mutated")
                    return args.target_pg_volume.encode()

            with patch.dict(bundle["prepare_volumes"].__globals__, Docker=ExistingVolume, RUNTIME=Path(folder)):
                with self.assertRaises(bundle["BundleFailure"]):
                    bundle["prepare_volumes"](args)
            self.assertEqual([("volume", "ls", "--format", "{{.Name}}")], calls)
            self.assertFalse(Path(args.fresh_volumes_record).exists())

    def test_prepare_creates_only_exact_absent_role_volumes_with_proof(self):
        with tempfile.TemporaryDirectory() as folder:
            args = Namespace(target_project=self.project, target_pg_volume=self.project + "_postgres-data",
                             target_rustfs_volume=self.project + "_rustfs-data", fresh_volumes_record=str(Path(folder) / "proof.json"))
            created = []
            volumes = self.volumes

            class NewVolumes:
                def run(self, *command):
                    if command[:2] == ("volume", "create"):
                        created.append(command[-1])
                        return command[-1].encode()
                    if command[:2] == ("volume", "inspect"):
                        return json.dumps([volumes[command[-1]]]).encode()
                    if command == ("volume", "ls", "--format", "{{.Name}}") or command[0] == "ps":
                        return b""
                    raise AssertionError("Unexpected Docker invocation")

            with patch.dict(bundle["prepare_volumes"].__globals__, Docker=NewVolumes, RUNTIME=Path(folder)):
                self.assertEqual("fresh-volumes-prepared", bundle["prepare_volumes"](args)["status"])
            self.assertEqual([args.target_pg_volume, args.target_rustfs_volume], created)
            proof = json.loads(Path(args.fresh_volumes_record).read_text(encoding="utf-8"))
            self.assertEqual(self.proof["volumes"], proof["volumes"])
            self.assertFalse(Path(args.fresh_volumes_record + ".incomplete").exists())


class WriterSourceSafetyTest(unittest.TestCase):
    def setUp(self):
        self.pg = {"Id": "source-pg-id", "NetworkSettings": {"Networks": {
            "source-net": {"NetworkID": "source-network-id", "Aliases": ["postgres", "source-pg"]}},
            "Ports": {"5432/tcp": [{"HostIp": "127.0.0.1", "HostPort": "25432"}]}}}
        self.writer = {"Config": {"Env": ["POSTGRES_HOST=postgres", "POSTGRES_PORT=5432", "POSTGRES_DB=source_db"]},
                       "NetworkSettings": {"Networks": {"source-net": {"NetworkID": "source-network-id"}}}}
        self.args = Namespace(source_database="source_db", source_schema="public", source_project="source-project", source_pg_container="source-pg")

    def test_compose_database_and_actual_pg_network_must_match(self):
        bundle["compose_writer_identity"](self.writer, self.pg, self.args)
        self.writer["Config"]["Env"][-1] = "POSTGRES_DB=daily_db"
        with self.assertRaises(bundle["BundleFailure"]):
            bundle["compose_writer_identity"](self.writer, self.pg, self.args)
        self.writer["Config"]["Env"][-1] = "POSTGRES_DB=source_db"
        self.writer["NetworkSettings"]["Networks"]["source-net"]["NetworkID"] = "foreign-network"
        with self.assertRaises(bundle["BundleFailure"]):
            bundle["compose_writer_identity"](self.writer, self.pg, self.args)

    def test_direct_spring_url_override_is_the_actual_database(self):
        self.writer["Config"]["Env"].append("SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/daily_db")
        with self.assertRaises(bundle["BundleFailure"]):
            bundle["compose_writer_identity"](self.writer, self.pg, self.args)

    def test_all_compose_identities_preflight_before_any_stop(self):
        self.args.writer_container = ["valid", "daily"]
        self.args.native_writer_state = None
        self.args.writers_exclusive = True
        valid = copy.deepcopy(self.writer)
        valid["Id"] = "valid-writer-id"
        daily = copy.deepcopy(valid)
        daily["Config"]["Env"][-1] = "POSTGRES_DB=daily_db"
        pg = self.pg
        calls = []

        class FakeDocker:
            def inspect(self, name, *args, **kwargs):
                return pg if name == "source-pg" else valid if name == "valid" else daily
            def run(self, *args):
                calls.append(args)
                raise AssertionError("No process should be stopped after failed preflight")

        with self.assertRaises(bundle["BundleFailure"]):
            bundle["pause_writers"](FakeDocker(), self.args)
        self.assertEqual([], calls)

    def test_native_environment_capture_hash_and_database_are_required(self):
        with tempfile.TemporaryDirectory() as folder:
            env_path = Path(folder) / "synthetic.env"
            env_path.write_text("POSTGRES_DB=source_db\nPOSTGRES_HOST=127.0.0.1\nPOSTGRES_PORT=25432\n", encoding="utf-8")
            state_path = Path(folder) / "backend.json"
            self.args.env_file, self.args.native_writer_state = str(env_path), str(state_path)
            entry = {"root": str(bundle["ROOT"]), "directory": str(bundle["ROOT"]), "workingDirectory": str(bundle["ROOT"]),
                     "service": "backend", "moduleDirectory": str(bundle["ROOT"] / "backend"), "captureError": None}
            state_path.write_text(json.dumps(entry), encoding="utf-8")
            with patch.dict(bundle["native_writer_identity"].__globals__, RUNTIME=Path(folder)):
                with self.assertRaises(bundle["BundleFailure"]):
                    bundle["native_writer_identity"](self.args, self.pg)
                entry.update(envFile=str(env_path), envFileSha256=bundle["digest"](env_path),
                             databaseIdentity={"database": "source_db", "host": "127.0.0.1", "port": 25432, "schema": "public"})
                state_path.write_text(json.dumps(entry), encoding="utf-8")
                self.assertEqual(state_path, bundle["native_writer_identity"](self.args, self.pg))
                entry["captureError"] = "DATABASE_IDENTITY_UNCONFIRMED"
                state_path.write_text(json.dumps(entry), encoding="utf-8")
                with self.assertRaises(bundle["BundleFailure"]):
                    bundle["native_writer_identity"](self.args, self.pg)
                entry["captureError"] = None
                state_path.write_text(json.dumps(entry), encoding="utf-8")
                env_path.write_text("POSTGRES_DB=daily_db\n", encoding="utf-8")
                with self.assertRaises(bundle["BundleFailure"]):
                    bundle["native_writer_identity"](self.args, self.pg)

    def test_application_database_url_and_explicit_single_schema_are_the_actual_database(self):
        configured = bundle["configured_database"]
        self.assertEqual({"host": "127.0.0.1", "port": 25432, "database": "source_db", "schema": "isolated"},
                         configured({"DATABASE_URL": "jdbc:postgresql://127.0.0.1:25432/source_db?currentSchema=isolated", "POSTGRES_DB": "ignored"}, "localhost", 5432))
        self.assertEqual("direct_db", configured({"SPRING_DATASOURCE_URL": "jdbc:postgresql://localhost/direct_db",
                         "DATABASE_URL": "jdbc:postgresql://localhost/ignored"}, "localhost", 5432)["database"])
        for url in ("jdbc:postgresql://localhost/db?currentSchema=", "jdbc:postgresql://localhost/db?currentSchema=a,b",
                    "jdbc:postgresql://localhost/db?currentSchema=a&currentSchema=b", "jdbc:postgresql://localhost/db?sslmode=require", ""):
            with self.subTest(url=url):
                with self.assertRaises(bundle["BundleFailure"]):
                    configured({"DATABASE_URL": url}, "localhost", 5432)

    def test_unresolved_spring_or_jvm_database_override_cannot_be_guessed(self):
        for values in ({"SPRING_APPLICATION_JSON": "{}"}, {"SPRING_CONFIG_LOCATION": "other.properties"},
                       {"JAVA_TOOL_OPTIONS": "-Dspring.datasource.url=jdbc:postgresql://localhost/other"}):
            with self.assertRaises(bundle["BundleFailure"]):
                bundle["configured_database"](values, "localhost", 5432)
        self.assertEqual("ai_workbench", bundle["configured_database"]({"JAVA_TOOL_OPTIONS": "-Xmx128m"}, "localhost", 5432)["database"])


class NativeHostEnvironmentTest(unittest.TestCase):
    def test_native_host_uses_its_modules_without_mutating_caller_or_database_env(self):
        function = bundle["native_powershell_environment"]
        environment = {"PSModulePath": "foreign-edition-modules", "SPRING_DATASOURCE_URL": "synthetic-database-url", "POSTGRES_PASSWORD": "synthetic"}
        with patch.dict(function.__globals__["os"].environ, environment, clear=True):
            child = function()
            self.assertFalse(any(key.lower() == "psmodulepath" for key in child))
            self.assertEqual("synthetic-database-url", child["SPRING_DATASOURCE_URL"])
            self.assertEqual("foreign-edition-modules", function.__globals__["os"].environ["PSModulePath"])


class ReferencedObjectJsonTest(unittest.TestCase):
    def test_multiline_aggregate_keeps_both_required_prefix_objects(self):
        owner, resume_id, attachment_id = (str(uuid.uuid4()) for _ in range(3))
        entries = [
            {"key": f"interview/resumes/{owner}/{resume_id}.md", "contentType": "text/markdown; charset=UTF-8", "size": 0,
             "sha256": hashlib.sha256(b"").hexdigest(), "references": [{"kind": "current-resume", "owner": owner, "id": resume_id}]},
            {"key": f"community/attachments/{attachment_id}", "contentType": "text/markdown; charset=UTF-8", "size": 0,
             "sha256": hashlib.sha256(b"").hexdigest(), "references": [{"kind": "community-draft", "owner": owner, "id": str(uuid.uuid4())}]}]
        class Snapshot:
            def scalar(self, container, user, database, sql):
                return "0" if "LEFT JOIN" in sql else json.dumps(entries, indent=2)
        objects = bundle["referenced_objects"](Snapshot(), "snapshot", "user", "d10_restore_snapshot", "isolated")
        self.assertEqual([entry["key"] for entry in entries], [entry.key for entry in objects])


if __name__ == "__main__":
    unittest.main()
