"""Read-only package checks; never execute any incoming prototype code."""
from pathlib import Path, PurePosixPath
import hashlib
import json
import re
import stat

ROOT = Path(__file__).resolve().parents[4]
PACKAGE = ROOT / '.workbench/inbox/WB-20261003-community-oss-3f9aaa-r3'
files = sorted(p for p in PACKAGE.rglob('*') if p.is_file())
failures = []
inventory = []
for path in PACKAGE.rglob('*'):
    if path.is_symlink() or (getattr(path.lstat(), 'st_file_attributes', 0) & stat.FILE_ATTRIBUTE_REPARSE_POINT):
        failures.append({'type': 'link', 'file': str(path.relative_to(PACKAGE))})
    if not path.resolve().is_relative_to(PACKAGE.resolve()):
        failures.append({'type': 'outside', 'file': str(path.relative_to(PACKAGE))})
patterns = [
    rb'-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----',
    rb'\b(?:ghp_|github_pat_)[A-Za-z0-9_]{20,}',
    rb'\bLTAI[A-Za-z0-9]{16,}\b',
    rb'\bAKIA[A-Z0-9]{16}\b',
]
for path in files:
    data = path.read_bytes()
    rel = path.relative_to(PACKAGE).as_posix()
    inventory.append({'file': rel, 'bytes': len(data), 'sha256': hashlib.sha256(data).hexdigest()})
    if path.suffix.lower() not in {'.png', '.jpg', '.jpeg'} and any(re.search(p, data) for p in patterns):
        failures.append({'type': 'possible_secret', 'file': rel})
    if path.name in {'task.json', 'implement.jsonl', 'check.jsonl'}:
        failures.append({'type': 'forbidden_lifecycle_input', 'file': rel})

def verify_manifest(base):
    manifest = base / 'SHA256SUMS.txt'
    rows = []
    names = set()
    for line in manifest.read_text(encoding='utf-8-sig').splitlines():
        if not line.strip():
            continue
        match = re.fullmatch(r'([0-9a-fA-F]{64})\s+\*?(.+)', line)
        if not match:
            failures.append({'type': 'manifest_syntax', 'file': manifest.relative_to(PACKAGE).as_posix()})
            continue
        expected, name = match.groups()
        relative = PurePosixPath(name)
        target = base / name
        if relative.is_absolute() or '..' in relative.parts or not target.resolve().is_relative_to(base.resolve()):
            failures.append({'type': 'manifest_outside', 'file': name})
            continue
        names.add(name)
        actual = hashlib.sha256(target.read_bytes()).hexdigest() if target.is_file() else None
        rows.append({'file': name, 'matches': actual == expected.lower()})
        if actual != expected.lower():
            failures.append({'type': 'checksum_mismatch', 'file': target.relative_to(PACKAGE).as_posix()})
    actual_names = {p.relative_to(base).as_posix() for p in base.rglob('*') if p.is_file() and p != manifest}
    missing = sorted(actual_names - names)
    extra = sorted(names - actual_names)
    if missing or extra:
        failures.append({'type': 'manifest_coverage', 'missing': missing, 'extra': extra})
    return {'file': manifest.relative_to(PACKAGE).as_posix(), 'entries': len(rows), 'all_match': all(x['matches'] for x in rows), 'missing': missing, 'extra': extra}

manifests = [verify_manifest(PACKAGE), verify_manifest(PACKAGE / 'assets/prototype-r2')]
handoff = (PACKAGE / 'HANDOFF.md').read_text(encoding='utf-8')
listed = set(re.findall(r'\| \[[^\]]+\]\(([^)]+)\)', handoff))
unlisted = sorted({x['file'] for x in inventory} - listed)
if unlisted:
    failures.append({'type': 'handoff_file_list', 'missing': unlisted})
req = (PACKAGE / 'requirements.md').read_text(encoding='utf-8')
acc = (PACKAGE / 'acceptance.md').read_text(encoding='utf-8')
ac_ids = sorted(set(re.findall(r'^### (AC-\d+)', req, re.M)))
tc_ids = sorted(set(re.findall(r'^### (TC-\d+)', acc, re.M)))
referenced = set(re.findall(r'AC-\d+', acc))
report = {
    'package': PACKAGE.relative_to(ROOT).as_posix(),
    'file_count': len(files),
    'prototype_file_count': sum(x['file'].startswith('assets/prototype-r2/') for x in inventory),
    'manifests': manifests,
    'ac_ids': ac_ids,
    'tc_ids': tc_ids,
    'acs_without_acceptance_reference': sorted(set(ac_ids) - referenced),
    'failures': failures,
    'inventory': inventory,
    'limitations': ['Static package checks only.', 'Secret scan covers limited formats; no secret values are printed.', 'No incoming HTML, scripts, engineering tests or cloud operations executed.'],
}
output = Path(__file__).with_name('package-verification.json')
output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
print(json.dumps({k: v for k, v in report.items() if k != 'inventory'}, ensure_ascii=False, indent=2))
raise SystemExit(bool(failures))
