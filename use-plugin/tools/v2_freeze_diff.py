"""Prove that the Phase 44 freeze changes status/provenance, not V2 semantics.

The command compares the candidate checkout with an explicit reviewed Git base.
It fails if Metamodel V2 changes or if a structural/runtime mapping change falls
outside the reviewed metadata-only allowlist. It never edits canonical inputs.
"""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import subprocess


ROOT = Path(__file__).resolve().parents[2]
ECORE = "use-plugin/Core/Metamodel/version-2/jacamo_v2_complete.ecore"
MAPPING = "use-plugin/Core/Mapping/version-2/jacamo-use-mapping-v2.json"
RUNTIME = (
    "use-plugin/src/main/resources/org/tzi/use/plugins/jacamo/runtime/"
    "jacamo-use-runtime-mapping-v2.json"
)
RUNTIME_SCHEMA = (
    "use-plugin/src/main/resources/org/tzi/use/plugins/jacamo/runtime/"
    "runtime-mapping-v2.schema.json"
)

ALLOWED = {
    MAPPING: {
        "/contract/compilerGateRule",
        "/reviewFlags/7/mappingAction",
        "/reviewFlags/7/note",
        "/reviewFlags/7/severity",
        "/reviewFlags/7/source",
        "/status",
        "/targetUSE/sourceEvidence/3",
        "/targetUSE/validationScope",
    },
    RUNTIME: {"/status", "/targetContract/mappingSha256"},
    RUNTIME_SCHEMA: {"/properties/status/enum/0"},
}


def git(*arguments: str, binary: bool = False):
    return subprocess.check_output(
        ["git", *arguments], cwd=ROOT, text=not binary, encoding=None if binary else "utf-8"
    )


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def base_bytes(revision: str, path: str) -> bytes:
    return git("show", f"{revision}:{path}", binary=True)


def candidate_bytes(path: str) -> bytes:
    return (ROOT / path).read_bytes()


def json_changes(before, after, pointer=""):
    changes = []
    if type(before) is not type(after):
        return [{"pointer": pointer, "before": before, "after": after}]
    if isinstance(before, dict):
        for key in sorted(before.keys() | after.keys()):
            child = f"{pointer}/{key}"
            if key not in before:
                changes.append({"pointer": child, "before": None, "after": after[key]})
            elif key not in after:
                changes.append({"pointer": child, "before": before[key], "after": None})
            else:
                changes.extend(json_changes(before[key], after[key], child))
    elif isinstance(before, list):
        if len(before) != len(after):
            changes.append({"pointer": f"{pointer}/length", "before": len(before), "after": len(after)})
        for index, (left, right) in enumerate(zip(before, after)):
            changes.extend(json_changes(left, right, f"{pointer}/{index}"))
    elif before != after:
        changes.append({"pointer": pointer, "before": before, "after": after})
    return changes


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--base", required=True, help="Reviewed pre-freeze Git revision")
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()

    base_revision = git("rev-parse", args.base).strip()
    head_revision = git("rev-parse", "HEAD").strip()
    old_ecore = base_bytes(base_revision, ECORE)
    new_ecore = candidate_bytes(ECORE)
    if old_ecore != new_ecore:
        raise SystemExit("Metamodel V2 changed; use the V2 Minor-Change Fast Path before freeze")

    contracts = {}
    for path, allowed in ALLOWED.items():
        old = base_bytes(base_revision, path)
        new = candidate_bytes(path)
        changes = json_changes(json.loads(old), json.loads(new))
        actual = {change["pointer"] for change in changes}
        if actual != allowed:
            unexpected = sorted(actual - allowed)
            missing = sorted(allowed - actual)
            raise SystemExit(f"Unreviewed freeze diff for {path}: unexpected={unexpected}, missing={missing}")
        contracts[path] = {
            "beforeSha256": sha256(old),
            "afterSha256": sha256(new),
            "changedPointers": changes,
            "semanticRulesChanged": False,
        }

    result = {
        "schemaVersion": "1.0.0",
        "phase": 44,
        "status": "PASS",
        "classification": "FREEZE_METADATA_ONLY",
        "baseRevision": base_revision,
        "candidateRevision": head_revision,
        "candidateWorkingTreeDirty": bool(git("status", "--porcelain").strip()),
        "metamodel": {
            "path": ECORE,
            "beforeSha256": sha256(old_ecore),
            "afterSha256": sha256(new_ecore),
            "changed": False,
            "minorChangeFastPathInvoked": False,
        },
        "contracts": contracts,
        "conclusion": (
            "Ecore, structural rules, runtime match/action rules and target-only order projection are unchanged; "
            "only reviewed freeze status, provenance and exact compatibility fingerprints changed."
        ),
    }
    output = args.output if args.output.is_absolute() else ROOT / args.output
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(result, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(json.dumps({"status": "PASS", "output": str(output), "baseRevision": base_revision}, indent=2))


if __name__ == "__main__":
    main()
