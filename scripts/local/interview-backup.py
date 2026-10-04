"""Coordinated private PostgreSQL + exact-key object bundles, and fresh-target restore.

All credentials come from an ignored env file. No ListBucket, source overwrite,
automatic restart, orphan export, or bulk cleanup. Writers stay stopped until the
operator explicitly restarts them. Online deletion does not delete older bundles.
"""
from __future__ import annotations

import argparse
from dataclasses import asdict, dataclass
import datetime as dt
import hashlib
import json
import os
from pathlib import Path
import re
import runpy
import shutil
import subprocess
import sys
from urllib.parse import parse_qs, urlsplit
import uuid

STORAGE = runpy.run_path(str(Path(__file__).with_name("initialize-storage.py")))
Client, SetupFailure = STORAGE["Client"], STORAGE["SetupFailure"]
ROOT = Path(__file__).resolve().parents[2]
BACKUPS = ROOT / ".local-backups" / "ai-interview"
RUNTIME = ROOT / ".local-runtime"
IDENTIFIER = re.compile(r"[a-z_][a-z0-9_]{0,62}")
NEW_DATABASE = re.compile(r"d10_restore_[a-z0-9_]{1,40}")
SHA = re.compile(r"[a-f0-9]{64}")
OBJECT_KEY = re.compile(r"(?:community/attachments/[0-9a-f-]{36}|interview/resumes/[0-9a-f-]{36}/[0-9a-f-]{36}\.md)")
CONTENT_TYPES = {"image/jpeg", "image/png", "image/webp", "application/pdf", "text/markdown; charset=UTF-8"}


class BundleFailure(Exception):
    def __init__(self, stage: str):
        self.stage = stage
        super().__init__(stage)


def require(condition: bool, stage: str) -> None:
    if not condition:
        raise BundleFailure(stage)


def now() -> str:
    return dt.datetime.now(dt.timezone.utc).isoformat()


def encoded(value: object) -> bytes:
    return (json.dumps(value, ensure_ascii=False, sort_keys=True, indent=2) + "\n").encode("utf-8")


def write_json(path: Path, value: object) -> None:
    # Completion markers are installed last using a same-directory atomic rename.
    temporary = path.with_name(path.name + ".partial")
    with temporary.open("xb") as output:
        output.write(encoded(value))
        output.flush()
        os.fsync(output.fileno())
    temporary.replace(path)


def digest(path: Path) -> str:
    return STORAGE["file_sha256"](path)


def finish_run(incomplete: Path, complete: Path, value: object) -> None:
    # Never publish success before a remaining failure-prone cleanup operation.
    incomplete.unlink()
    try:
        write_json(complete, value)
    except Exception as error:
        if not incomplete.exists():
            try:
                write_json(incomplete, {"status": "incomplete", "completionFailedAt": now()})
            except Exception:
                error.add_note("incomplete-marker-recreation-failed")
        raise


def identifier(value: str) -> str:
    require(bool(IDENTIFIER.fullmatch(value)), "explicit-sql-identifier")
    return value


def fresh_database(value: str, source: str) -> str:
    require(bool(NEW_DATABASE.fullmatch(value)) and value != source, "fresh-database-name")
    return value


def bundle_path(value: str, create: bool = False) -> Path:
    path = Path(value).resolve()
    require(path != BACKUPS.resolve() and path.is_relative_to(BACKUPS.resolve()), "bundle-below-ignored-backups")
    require(not path.is_symlink(), "bundle-symlink")
    if create:
        require(not path.exists(), "bundle-already-exists")
        path.mkdir(parents=True)
    else:
        require(path.is_dir(), "bundle-not-found")
    return path


@dataclass(frozen=True)
class ObjectEntry:
    key: str
    contentType: str
    size: int
    sha256: str
    references: list[dict]
    file: str

    @classmethod
    def parse(cls, value: dict) -> "ObjectEntry":
        require(isinstance(value, dict) and set(value) == {"key", "contentType", "size", "sha256", "references", "file"}, "object-entry-shape")
        key = value["key"]
        require(isinstance(key, str) and bool(OBJECT_KEY.fullmatch(key)), "object-logical-key")
        require(value["contentType"] in CONTENT_TYPES, "object-content-type")
        maximum = 1_048_576 if key.startswith("interview/") or value["contentType"].startswith("text/") else (
            5_242_880 if value["contentType"].startswith("image/") else 20_971_520)
        require(type(value["size"]) is int and 0 <= value["size"] <= maximum, "object-size")
        require(isinstance(value["sha256"], str) and bool(SHA.fullmatch(value["sha256"])), "object-sha256")
        expected_file = hashlib.sha256(key.encode("utf-8")).hexdigest() + ".bin"
        require(value["file"] == expected_file, "object-filename-key-hash")
        require(isinstance(value["references"], list) and len(value["references"]) > 0, "object-references")
        for reference in value["references"]:
            require(isinstance(reference, dict) and set(reference) == {"kind", "owner", "id"}
                    and reference["kind"] in {"current-resume", "community-draft", "community-revision"}, "object-reference-shape")
            for name in ("owner", "id"):
                require(isinstance(reference[name], str) and str(uuid.UUID(reference[name])) == reference[name], "object-reference-uuid")
        return cls(**value)


class Docker:
    def __init__(self) -> None:
        self.command = ["docker"] if shutil.which("docker") else ["wsl.exe", "-d", "Ubuntu", "--exec", "docker"]

    def run(self, *args: str, input_bytes: bytes | None = None, output: Path | None = None) -> bytes:
        # No expanded Compose config/env/stdout/stderr is printed: it can contain secrets.
        with output.open("xb") if output else open(os.devnull, "wb") as destination:
            result = subprocess.run(self.command + list(args), input=input_bytes, stdout=destination if output else subprocess.PIPE,
                                    stderr=subprocess.PIPE, check=False)
        require(result.returncode == 0, "docker-" + args[0] + "-failed")
        return result.stdout or b""

    def inspect(self, container: str, project: str, service: str, running: bool = True) -> dict:
        require(bool(re.fullmatch(r"[a-zA-Z0-9][a-zA-Z0-9_.-]{0,100}", container)), "container-name")
        info = json.loads(self.run("inspect", container))[0]
        labels = info["Config"].get("Labels") or {}
        require(labels.get("com.docker.compose.project") == project and labels.get("com.docker.compose.service") == service,
                "container-project-service-ownership")
        require(not running or info["State"]["Running"], "data-container-not-running")
        return info

    def scalar(self, container: str, user: str, database: str, sql: str) -> str:
        return self.run("exec", "-i", container, "psql", "-X", "-q", "-v", "ON_ERROR_STOP=1", "-U", identifier(user),
                        "-d", identifier(database), "-At", input_bytes=sql.encode("utf-8")).decode("utf-8").strip()

    def absent_database(self, container: str, user: str, database: str) -> None:
        names = self.scalar(container, user, "postgres", "SELECT datname FROM pg_database;").splitlines()
        require(database not in names, "existing-destination-database")

    def restore_database(self, container: str, user: str, database: str, archive: Path) -> None:
        self.absent_database(container, user, database)
        self.run("exec", container, "createdb", "-U", identifier(user), "-T", "template0", identifier(database))
        # Stream the archive through stdin: no cross-shell cp path or secret command args.
        with archive.open("rb") as source:
            result = subprocess.run(self.command + ["exec", "-i", container, "pg_restore", "-U", identifier(user), "-d",
                                                   identifier(database), "--exit-on-error", "--no-owner"],
                                    stdin=source, stdout=subprocess.DEVNULL, stderr=subprocess.PIPE, check=False)
        require(result.returncode == 0, "postgres-restore-failed-target-retained")


def volume_names(info: dict) -> list[str]:
    service = (info["Config"].get("Labels") or {}).get("com.docker.compose.service")
    destination = {"postgres": "/var/lib/postgresql/data", "rustfs": "/data"}.get(service)
    require(destination is not None, "data-volume-service-role")
    mounts = [m for m in info["Mounts"] if m["Destination"] == destination]
    require(len(mounts) == 1 and mounts[0]["Type"] == "volume", "explicit-named-data-volume-role")
    require(not any(m["Destination"] != destination and destination.startswith(m["Destination"].rstrip("/") + "/")
                    for m in info["Mounts"]), "data-parent-mount-refused")
    return [mounts[0]["Name"]]


def runtime_record(value: str) -> Path:
    path = Path(value).resolve()
    require(path.is_relative_to(RUNTIME.resolve()) and path != RUNTIME.resolve(), "proof-below-ignored-runtime")
    return path


def volume_identity(info: dict) -> str:
    identity = {key: info.get(key) for key in ("Name", "CreatedAt", "Driver", "Labels", "Options", "Scope")}
    return hashlib.sha256(encoded(identity)).hexdigest()


def prepare_volumes(args: argparse.Namespace) -> dict:
    require(bool(re.fullmatch(r"[a-z0-9][a-z0-9_-]{0,62}", args.target_project)), "explicit-target-project")
    record = runtime_record(args.fresh_volumes_record)
    require(not record.exists() and not record.with_name(record.name + ".incomplete").exists(), "fresh-volume-record-already-exists")
    names = {"postgres": args.target_pg_volume, "rustfs": args.target_rustfs_volume}
    for role, name in names.items():
        require(name == args.target_project + "_" + {"postgres": "postgres-data", "rustfs": "rustfs-data"}[role], "fresh-volume-name-role")
    docker = Docker()
    existing = docker.run("volume", "ls", "--format", "{{.Name}}").decode("utf-8").splitlines()
    require(set(names.values()).isdisjoint(existing), "existing-target-volume-refused")
    require(not docker.run("ps", "-aq", "--filter", "label=com.docker.compose.project=" + args.target_project).strip(),
            "existing-target-project-containers-refused")
    record.parent.mkdir(parents=True, exist_ok=True)
    incomplete = record.with_name(record.name + ".incomplete")
    write_json(incomplete, {"status": "incomplete", "project": args.target_project, "startedAt": now()})
    prepared = {}
    for role, name in names.items():
        docker.run("volume", "create", "--label", "com.docker.compose.project=" + args.target_project,
                   "--label", "com.docker.compose.volume=" + {"postgres": "postgres-data", "rustfs": "rustfs-data"}[role], name)
        info = json.loads(docker.run("volume", "inspect", name))[0]
        labels = info.get("Labels") or {}
        require(info.get("Name") == name and labels.get("com.docker.compose.project") == args.target_project
                and labels.get("com.docker.compose.volume") == {"postgres": "postgres-data", "rustfs": "rustfs-data"}[role]
                and info.get("Driver") == "local" and not info.get("Options"), "created-volume-identity")
        require(not docker.run("ps", "-aq", "--filter", "volume=" + name).strip(), "prepared-volume-already-attached")
        prepared[role] = {"name": name, "identity": volume_identity(info)}
    finish_run(incomplete, record, {"format": "workbench-fresh-volumes-v1", "project": args.target_project,
                                   "preparedAt": now(), "volumes": prepared})
    return {"status": "fresh-volumes-prepared", "project": args.target_project, "record": str(record)}


def local_storage(values: dict, endpoint: str, bucket: str, identity_env: str) -> tuple[object, object]:
    uri = urlsplit(endpoint)
    require(uri.scheme in ("http", "https") and uri.hostname in ("127.0.0.1", "localhost") and not uri.username
            and not uri.password and not uri.query and not uri.fragment and uri.path in ("", "/"), "explicit-loopback-storage")
    require(values.get("WORKBENCH_STORAGE_ENDPOINT") == endpoint and values.get("WORKBENCH_STORAGE_BUCKET") == bucket,
            "explicit-storage-matches-env")
    require(values.get(identity_env) == values.get("WORKBENCH_STORAGE_ACCESS_KEY") and bool(values.get(identity_env)), "explicit-app-identity-env")
    require(bool(values.get("WORKBENCH_STORAGE_SECRET_KEY")) and values.get("WORKBENCH_STORAGE_ACCESS_KEY") != values.get("RUSTFS_ACCESS_KEY"), "separate-app-credentials")
    region = values.get("WORKBENCH_STORAGE_REGION", "us-east-1")
    app = Client(endpoint, values[identity_env], values["WORKBENCH_STORAGE_SECRET_KEY"], region)
    root = Client(endpoint, values.get("RUSTFS_ACCESS_KEY", ""), values.get("RUSTFS_SECRET_KEY", ""), region)
    return app, root


def endpoint_matches_container(endpoint: str, info: dict) -> None:
    uri = urlsplit(endpoint)
    bindings = info["NetworkSettings"]["Ports"].get("9000/tcp") or []
    require(any(int(p["HostPort"]) == uri.port and p["HostIp"] in ("127.0.0.1", "0.0.0.0") for p in bindings), "storage-container-endpoint")


def no_connections(docker: Docker, container: str, user: str, database: str) -> None:
    count = docker.scalar(container, user, "postgres", f"SELECT count(*) FROM pg_stat_activity WHERE datname='{identifier(database)}';")
    require(count == "0", "source-has-uncontrolled-connections")


def configured_database(values: dict, default_host: str, default_port: int) -> dict:
    url = values.get("SPRING_DATASOURCE_URL") if "SPRING_DATASOURCE_URL" in values else values.get("DATABASE_URL")
    if "SPRING_DATASOURCE_URL" in values or "DATABASE_URL" in values:
        require(isinstance(url, str) and bool(url), "writer-database-url")
        require(url.startswith("jdbc:postgresql://"), "writer-database-url")
        parsed = urlsplit(url[5:])
        require(not parsed.username and not parsed.password and not parsed.fragment, "writer-database-url")
        query = parse_qs(parsed.query, strict_parsing=True, keep_blank_values=True)
        require(set(query).issubset({"currentSchema"}) and all(len(v) == 1 for v in query.values()), "writer-database-url-options")
        host, port, database = parsed.hostname, parsed.port or 5432, parsed.path.removeprefix("/")
        schema = query.get("currentSchema", ["public"])[0]
    else:
        host, port, database = values.get("POSTGRES_HOST", default_host), int(values.get("POSTGRES_PORT", default_port)), values.get("POSTGRES_DB", "ai_workbench")
        schema = "public"
    require(isinstance(host, str) and bool(re.fullmatch(r"[a-zA-Z0-9][a-zA-Z0-9.-]*", host)) and 1 <= port <= 65535, "writer-database-host-port")
    require(not any(values.get(key) for key in ("SPRING_DATASOURCE_HIKARI_SCHEMA", "SPRING_APPLICATION_JSON", "SPRING_CONFIG_LOCATION",
            "SPRING_CONFIG_ADDITIONAL_LOCATION", "SPRING_CONFIG_IMPORT", "SPRING_CONFIG_NAME")), "unresolved-writer-database-override")
    require(not any(re.search(r"-D(?:spring\.|DATABASE_URL(?:=|\s)|POSTGRES_(?:HOST|PORT|DB)(?:=|\s))", values.get(key, ""), re.I)
            for key in ("JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS")), "unresolved-writer-jvm-database-override")
    return {"host": host.lower(), "port": port, "database": identifier(database), "schema": identifier(schema)}


def native_writer_identity(args: argparse.Namespace, pg: dict) -> Path:
    state_path = runtime_record(args.native_writer_state)
    require(state_path.is_file(), "native-writer-state-not-found")
    entry = json.loads(state_path.read_text(encoding="utf-8-sig"))
    require("captureError" in entry and not entry["captureError"] and isinstance(entry.get("databaseIdentity"), dict),
            "native-writer-database-identity-unconfirmed")
    env_path = Path(args.env_file).resolve()
    require(isinstance(entry.get("envFile"), str) and Path(entry["envFile"]).resolve() == env_path
            and entry.get("envFileSha256") == digest(env_path), "native-writer-environment-identity")
    values = STORAGE["load_env"](str(env_path))
    identity = configured_database(values, "localhost", 5432)
    require(entry.get("databaseIdentity") == identity and identity["host"] in {"localhost", "127.0.0.1"}
            and identity["database"] == args.source_database and identity["schema"] == args.source_schema,
            "native-writer-source-database")
    bindings = pg["NetworkSettings"]["Ports"].get("5432/tcp") or []
    require(any(int(item["HostPort"]) == identity["port"] and item["HostIp"] in {"127.0.0.1", "0.0.0.0"}
                for item in bindings), "native-writer-source-postgres-endpoint")
    require(entry.get("root") == str(ROOT) and entry.get("service") == "backend" and entry.get("moduleDirectory") == str(ROOT / "backend")
            and entry.get("directory") == str(ROOT) and entry.get("workingDirectory") == str(ROOT), "native-writer-repository")
    return state_path


def compose_writer_identity(info: dict, pg: dict, args: argparse.Namespace) -> None:
    values = dict(value.split("=", 1) for value in info["Config"].get("Env", []) if "=" in value)
    identity = configured_database(values, "postgres", 5432)
    require(identity["database"] == args.source_database and identity["schema"] == args.source_schema
            and identity["port"] == 5432, "compose-writer-source-database")
    networks = info["NetworkSettings"].get("Networks") or {}
    pg_networks = pg["NetworkSettings"].get("Networks") or {}
    require(any(name in networks and networks[name].get("NetworkID") == network.get("NetworkID")
                and identity["host"] in (network.get("Aliases") or [])
                for name, network in pg_networks.items()), "compose-writer-source-postgres-network")


def native_powershell_environment() -> dict[str, str]:
    # A Python child of pwsh can inherit its edition-specific module paths.
    # Let Windows PowerShell initialize its own standard modules without
    # changing the caller or the captured database environment.
    return {key: value for key, value in os.environ.items() if key.lower() != "psmodulepath"}


def pause_writers(docker: Docker, args: argparse.Namespace) -> list[dict]:
    require(args.writers_exclusive and bool(args.writer_container or args.native_writer_state), "explicit-exclusive-writers-required")
    pg = docker.inspect(args.source_pg_container, args.source_project, "postgres")
    owned = []
    for container in args.writer_container:
        info = docker.inspect(container, args.source_project, "backend", running=False)
        compose_writer_identity(info, pg, args)
        owned.append({"container": container, "id": info["Id"]})
    state_path = None
    if args.native_writer_state:
        require(os.name == "nt", "native-writer-windows-only")
        state_path = native_writer_identity(args, pg)
        checked = subprocess.run(["powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File",
                                  str(Path(__file__).with_name("pause-backup-writer.ps1")), "-StateFile", str(state_path),
                                  "-EnvFile", str(Path(args.env_file).resolve()), "-ValidateOnly"],
                                 stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=False, env=native_powershell_environment())
        require(checked.returncode == 0, "native-writer-identity-preflight-failed")
    # Verify every identity before stopping any of them.
    for writer in owned:
        docker.run("stop", "--time", "60", writer["id"])
        state = json.loads(docker.run("inspect", writer["id"]))[0]["State"]
        require(not state["Running"], "writer-still-running")
    if state_path:
        result = subprocess.run(["powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File",
                                 str(Path(__file__).with_name("pause-backup-writer.ps1")), "-StateFile", str(state_path),
                                 "-EnvFile", str(Path(args.env_file).resolve())],
                                stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=False, env=native_powershell_environment())
        require(result.returncode == 0, "native-writer-identity-stop-failed")
        owned.append({"nativeState": str(Path(args.native_writer_state).resolve())})
    return owned


def database_state(docker: Docker, container: str, user: str, database: str, schema: str) -> dict:
    schema = identifier(schema)
    tables = docker.scalar(container, user, database,
                           f"SELECT tablename FROM pg_tables WHERE schemaname='{schema}' ORDER BY tablename;").splitlines()
    require({"flyway_schema_history", "user_resumes", "resume_objects", "community_attachments", "community_draft_attachments",
             "community_revision_attachments"}.issubset(tables), "required-migrated-tables")
    state = {}
    for table in tables:
        table = identifier(table)
        sql = (f"SELECT json_build_object('count',count(*),'sha256',encode(sha256(convert_to(coalesce("
               f"string_agg(to_jsonb(t)::text,E'\\n' ORDER BY to_jsonb(t)::text),''),'UTF8')),'hex')) FROM {schema}.{table} t;")
        state[table] = json.loads(docker.scalar(container, user, database, sql))
    failed = docker.scalar(container, user, database, f"SELECT count(*) FROM {schema}.flyway_schema_history WHERE NOT success;")
    require(failed == "0", "failed-flyway-migration")
    return state


def referenced_objects(docker: Docker, container: str, user: str, database: str, schema: str) -> list[ObjectEntry]:
    schema = identifier(schema)
    invalid = docker.scalar(container, user, database, f"""SET search_path TO {schema};
SELECT count(*) FROM user_resumes u LEFT JOIN resume_objects o ON o.user_id=u.user_id AND o.id=u.current_object_id
WHERE u.source_kind='MD_FILE' AND (o.id IS NULL OR o.status<>'READY');""").splitlines()[-1]
    require(invalid == "0", "current-resume-original-not-ready")
    sql = f"""SET search_path TO {schema};
SELECT coalesce(json_agg(q ORDER BY q.key),'[]'::json) FROM (
 SELECT o.storage_key AS key, o.content_type AS \"contentType\",o.byte_size AS size,o.sha256,
        json_build_array(json_build_object('kind','current-resume','owner',u.user_id,'id',o.id)) AS references
 FROM user_resumes u JOIN resume_objects o ON o.user_id=u.user_id AND o.id=u.current_object_id
 WHERE u.source_kind='MD_FILE' AND o.status='READY'
 UNION ALL
 SELECT a.object_key AS key,a.verified_content_type AS \"contentType\",a.actual_size AS size,a.sha256,
        (SELECT json_agg(r ORDER BY r.kind,r.id) FROM (
           SELECT 'community-draft' AS kind,d.owner_id AS owner,d.post_id AS id
           FROM community_draft_attachments d WHERE d.attachment_id=a.id AND d.owner_id=a.owner_id AND d.post_id=a.post_id
           UNION ALL
           SELECT 'community-revision',r.owner_id,r.revision_id
           FROM community_revision_attachments r WHERE r.attachment_id=a.id AND r.owner_id=a.owner_id AND r.post_id=a.post_id
        ) r) AS references
 FROM community_attachments a WHERE a.state='READY' AND (
   EXISTS(SELECT 1 FROM community_draft_attachments d WHERE d.attachment_id=a.id AND d.owner_id=a.owner_id AND d.post_id=a.post_id)
   OR EXISTS(SELECT 1 FROM community_revision_attachments r WHERE r.attachment_id=a.id AND r.owner_id=a.owner_id AND r.post_id=a.post_id))
) q;"""
    # Quiet psql suppresses the SET command tag. json_agg(record) may contain
    # line breaks between records; parse the entire aggregate without truncation.
    values = json.loads(docker.scalar(container, user, database, sql))
    entries = []
    for value in values:
        value["file"] = hashlib.sha256(value["key"].encode()).hexdigest() + ".bin"
        entries.append(ObjectEntry.parse(value))
    require(len({entry.key for entry in entries}) == len(entries), "duplicate-object-key")
    return entries


def check_file(path: Path, size: int, sha256: str) -> None:
    require(path.is_file() and not path.is_symlink() and path.resolve() == path.absolute() and path.stat().st_size == size, "object-file-size")
    require(digest(path) == sha256, "object-file-sha256")


def export_objects(app: object, bucket: str, entries: list[ObjectEntry], folder: Path) -> None:
    folder.mkdir()
    for entry in entries:
        partial = folder / (entry.file + ".partial")
        STORAGE["expect"]("export-required-object", app.request("GET", "/" + bucket + "/" + entry.key,
                             response_file=partial, expected_size=entry.size), (200,))
        check_file(partial, entry.size, entry.sha256)
        partial.replace(folder / entry.file)


def verify_bundle(folder: Path) -> dict:
    complete_path = folder / "complete.json"
    require(not (folder / "incomplete.json").exists()
            and complete_path.is_file() and not complete_path.is_symlink(), "bundle-not-complete")
    complete = json.loads(complete_path.read_text(encoding="utf-8"))
    require(complete.get("format") == "workbench-private-bundle-v1", "bundle-format")
    for name in ("database.dump", "manifest.json"):
        path = folder / name
        require(path.is_file() and not path.is_symlink() and digest(path) == complete.get("sha256", {}).get(name), "bundle-" + name + "-checksum")
    manifest = json.loads((folder / "manifest.json").read_text(encoding="utf-8"))
    entries = [ObjectEntry.parse(value) for value in manifest["objects"]]
    require(len({entry.key for entry in entries}) == len(entries), "duplicate-object-key")
    for entry in entries:
        check_file(folder / "objects" / entry.file, entry.size, entry.sha256)
    return manifest


def fresh_target_volumes(docker: Docker, pg: dict, fs: dict, source: dict, proof: dict) -> None:
    pg_volumes, fs_volumes = volume_names(pg), volume_names(fs)
    require(len(pg_volumes) == 1 and len(fs_volumes) == 1 and pg_volumes[0] != fs_volumes[0], "fresh-separate-data-volumes")
    require(set(pg_volumes + fs_volumes).isdisjoint(source["pgVolumes"] + source["rustfsVolumes"]), "source-volume-refused")
    project = pg["Config"]["Labels"]["com.docker.compose.project"]
    require(proof.get("format") == "workbench-fresh-volumes-v1" and proof.get("project") == project,
            "fresh-volume-preparation-record")
    for role, container, volumes in (("postgres", pg, pg_volumes), ("rustfs", fs, fs_volumes)):
        for volume_name in volumes:
            volume = json.loads(docker.run("volume", "inspect", volume_name))[0]
            labels = volume.get("Labels") or {}
            require(labels.get("com.docker.compose.project") == project
                    and labels.get("com.docker.compose.volume") == {"postgres": "postgres-data", "rustfs": "rustfs-data"}[role],
                    "target-volume-project-role")
            require(proof.get("volumes", {}).get(role) == {"name": volume_name, "identity": volume_identity(volume)},
                    "target-volume-preparation-identity")
            attached = docker.run("ps", "-aq", "--no-trunc", "--filter", "volume=" + volume_name).decode("utf-8").splitlines()
            require(attached == [container["Id"]], "target-volume-other-container-refused")


def backup(args: argparse.Namespace) -> dict:
    values = STORAGE["load_env"](args.env_file)
    user = identifier(values.get("POSTGRES_USER", ""))
    database, schema = identifier(args.source_database), identifier(args.source_schema)
    require(database == values.get("POSTGRES_DB"), "source-database-matches-env")
    fresh_database(args.snapshot_database, database)
    app, _ = local_storage(values, args.source_endpoint, args.source_bucket, args.app_identity_env)
    docker = Docker()
    pg = docker.inspect(args.source_pg_container, args.source_project, "postgres")
    fs = docker.inspect(args.source_rustfs_container, args.source_project, "rustfs")
    endpoint_matches_container(args.source_endpoint, fs)
    docker.absent_database(args.source_pg_container, user, args.snapshot_database)
    folder = bundle_path(args.bundle, create=True)
    write_json(folder / "incomplete.json", {"status": "incomplete", "startedAt": now()})
    owned = pause_writers(docker, args)
    no_connections(docker, args.source_pg_container, user, database)
    before = database_state(docker, args.source_pg_container, user, database, schema)
    docker.run("exec", args.source_pg_container, "pg_dump", "-U", user, "-d", database,
               "--format=custom", "--no-owner", output=folder / "database.dump")
    no_connections(docker, args.source_pg_container, user, database)
    docker.restore_database(args.source_pg_container, user, args.snapshot_database, folder / "database.dump")
    snapshot = database_state(docker, args.source_pg_container, user, args.snapshot_database, schema)
    require(snapshot == before, "dump-source-state-mismatch")
    entries = referenced_objects(docker, args.source_pg_container, user, args.snapshot_database, schema)
    export_objects(app, args.source_bucket, entries, folder / "objects")
    no_connections(docker, args.source_pg_container, user, database)
    require(database_state(docker, args.source_pg_container, user, database, schema) == before, "source-changed-during-export")
    manifest = {"format": "workbench-private-bundle-v1", "createdAt": now(), "schema": schema,
                "source": {"project": args.source_project, "database": database, "pgContainer": pg["Id"], "pgVolumes": volume_names(pg),
                            "rustfsContainer": fs["Id"], "rustfsVolumes": volume_names(fs),
                            "endpointSha256": hashlib.sha256(args.source_endpoint.encode()).hexdigest(), "bucket": args.source_bucket},
                "writersStopped": owned, "snapshotDatabase": args.snapshot_database,
                "databaseState": snapshot, "objects": [asdict(entry) for entry in entries],
                "retention": "Online deletion does not remove older dumps or object bundles."}
    write_json(folder / "manifest.json", manifest)
    finish_run(folder / "incomplete.json", folder / "complete.json",
               {"format": "workbench-private-bundle-v1", "completedAt": now(),
                "sha256": {name: digest(folder / name) for name in ("database.dump", "manifest.json")}})
    return {"status": "backup-complete", "bundle": str(folder), "objects": len(entries), "writers": "stopped-restart-explicitly", "snapshotDatabase": args.snapshot_database}


def restore(args: argparse.Namespace) -> dict:
    folder = bundle_path(args.bundle)
    manifest = verify_bundle(folder)  # Every local byte is checked before any target mutation.
    values = STORAGE["load_env"](args.env_file)
    database = fresh_database(args.target_database, manifest["source"]["database"])
    require(database == values.get("POSTGRES_DB"), "target-database-matches-env")
    user = identifier(values.get("POSTGRES_USER", ""))
    require(args.target_project != manifest["source"]["project"]
            and hashlib.sha256(args.target_endpoint.encode()).hexdigest() != manifest["source"]["endpointSha256"]
            and args.target_bucket != manifest["source"]["bucket"], "fresh-target-distinct-from-source")
    app, root = local_storage(values, args.target_endpoint, args.target_bucket, args.app_identity_env)
    docker = Docker()
    pg = docker.inspect(args.target_pg_container, args.target_project, "postgres")
    fs = docker.inspect(args.target_rustfs_container, args.target_project, "rustfs")
    endpoint_matches_container(args.target_endpoint, fs)
    require(pg["Id"] != manifest["source"]["pgContainer"] and fs["Id"] != manifest["source"]["rustfsContainer"], "source-container-refused")
    proof_path = runtime_record(args.fresh_volumes_record)
    require(proof_path.is_file() and not proof_path.with_name(proof_path.name + ".incomplete").exists(), "fresh-volume-record-not-complete")
    proof = json.loads(proof_path.read_text(encoding="utf-8"))
    fresh_target_volumes(docker, pg, fs, manifest["source"], proof)
    docker.absent_database(args.target_pg_container, user, database)
    STORAGE["expect"]("fresh-target-bucket-required", root.request("HEAD", "/" + args.target_bucket), (404,))
    restore_id = uuid.uuid4().hex
    incomplete = folder / ("restore-" + restore_id + "-incomplete.json")
    record = {"status": "incomplete", "startedAt": now(), "project": args.target_project, "database": database,
              "bucket": args.target_bucket, "endpointSha256": hashlib.sha256(args.target_endpoint.encode()).hexdigest()}
    write_json(incomplete, record)
    STORAGE["initialize"](values, args.target_endpoint, args.target_bucket, values[args.app_identity_env])
    docker.restore_database(args.target_pg_container, user, database, folder / "database.dump")
    no_connections(docker, args.target_pg_container, user, database)
    restored_state = database_state(docker, args.target_pg_container, user, database, manifest["schema"])
    require(restored_state == manifest["databaseState"], "restored-database-content-or-flyway-mismatch")
    entries = [ObjectEntry.parse(value) for value in manifest["objects"]]
    require([asdict(entry) for entry in referenced_objects(docker, args.target_pg_container, user, database, manifest["schema"])] == manifest["objects"], "restored-reference-manifest-mismatch")
    for entry in entries:
        local = folder / "objects" / entry.file
        STORAGE["expect"]("restore-object-put", app.request("PUT", "/" + args.target_bucket + "/" + entry.key, data=local,
                             headers={"content-type": entry.contentType}), (200,))
        check = folder / ("verify-" + restore_id + "-" + entry.file)
        try:
            STORAGE["expect"]("restore-object-read-back", app.request("GET", "/" + args.target_bucket + "/" + entry.key,
                                 response_file=check, expected_size=entry.size), (200,))
            check_file(check, entry.size, entry.sha256)
        finally:
            if check.exists():
                check.unlink()
    require(database_state(docker, args.target_pg_container, user, database, manifest["schema"]) == manifest["databaseState"], "target-changed-during-object-restore")
    no_connections(docker, args.target_pg_container, user, database)
    record.update(status="restore-complete", completedAt=now(), postgresVerified=True, objectsVerified=len(entries))
    finish_run(incomplete, folder / ("restore-" + restore_id + "-complete.json"), record)
    return {"status": "restore-complete", "database": database, "bucket": args.target_bucket, "objects": len(entries), "source": "not-written"}


def parser() -> argparse.ArgumentParser:
    cli = argparse.ArgumentParser(description=__doc__)
    commands = cli.add_subparsers(dest="operation", required=True)
    export = commands.add_parser("backup", help="Stop owned exclusive writers; snapshot PG and export exact required objects")
    imported = commands.add_parser("restore", help="Restore a complete bundle only into new DB + bucket on distinct named volumes/project")
    check = commands.add_parser("verify", help="Read-only local dump/manifest/object SHA verification")
    preparation = commands.add_parser("prepare-volumes", help="Create only absent, explicit target data volumes and record their identities before Compose starts")
    preparation.add_argument("--target-project", required=True)
    preparation.add_argument("--target-pg-volume", required=True)
    preparation.add_argument("--target-rustfs-volume", required=True)
    preparation.add_argument("--fresh-volumes-record", required=True, help="New ignored .local-runtime proof file, required again for restore")
    imported.add_argument("--fresh-volumes-record", required=True, help="Proof created by prepare-volumes before starting target containers")
    for command in (export, imported, check):
        command.add_argument("--bundle", required=True, help="Explicit path below .local-backups/ai-interview")
    for command in (export, imported):
        command.add_argument("--env-file", required=True, help="Ignored source/target env; credentials never arguments")
        command.add_argument("--app-identity-env", required=True, help="Name of env variable selecting app access key, never its value")
    for prefix, command in (("source", export), ("target", imported)):
        for field in ("project", "pg-container", "rustfs-container", "database", "endpoint", "bucket"):
            command.add_argument("--" + prefix + "-" + field, required=True)
    export.add_argument("--source-schema", required=True)
    export.add_argument("--snapshot-database", required=True, help="New d10_restore_<suffix> used to read the exact dump")
    export.add_argument("--writer-container", action="append", default=[], help="Explicit owned backend Compose container; may repeat")
    export.add_argument("--native-writer-state", help="Exact repo .local-runtime backend PID state, checked before stopping")
    export.add_argument("--writers-exclusive", action="store_true", help="Assert listed writers are all source writers/cleanup; every remaining PG connection causes failure")
    return cli


def main() -> int:
    args = parser().parse_args()
    try:
        if args.operation == "backup":
            result = backup(args)
        elif args.operation == "restore":
            result = restore(args)
        elif args.operation == "prepare-volumes":
            result = prepare_volumes(args)
        else:
            manifest = verify_bundle(bundle_path(args.bundle))
            result = {"status": "bundle-verified", "objects": len(manifest["objects"])}
        print(json.dumps(result))
        return 0
    except (BundleFailure, SetupFailure) as error:
        print(json.dumps({"status": "failed", "stage": error.stage}))
    except Exception as error:
        result = {"status": "failed", "errorType": type(error).__name__}
        if "incomplete-marker-recreation-failed" in getattr(error, "__notes__", ()):
            result["cleanupStage"] = "incomplete-marker-recreation-failed"
        print(json.dumps(result))
    return 1


if __name__ == "__main__":
    sys.exit(main())
