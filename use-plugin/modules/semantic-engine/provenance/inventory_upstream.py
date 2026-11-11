#!/usr/bin/env python3
"""Inventory/verify the selected read-only use4Goal source. No upstream writes.

The production engine never depends on this script or a sibling checkout.
Extraction is an explicit later-phase operation and never overwrites differing
files. SHA-256 hashes describe exact source bytes, including original line ends.
"""
from __future__ import annotations

import argparse
import copy
import hashlib
import json
import re
import subprocess
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
MODULE = ROOT / "use-jacamo-semantic-engine"
UPSTREAM = ROOT.parent / "use4Goal"
PREFIX = Path("goal/src/main/java/org/vnu/sme/goal")
PIN = "6f3e73801239fd8bfb94ca8d04ba531b903c0ac9"
MANIFEST = MODULE / "src/main/resources/META-INF/use4goal-provenance.json"
GROUPS = {
    "dsl/csl/ast": (20, ["A", "B"]),
    "dsl/csl/mm": (46, ["B", "C", "H"]),
    "dsl/csl/parser": (4, ["A", "B", "C", "I"]),
    "dsl/istar/ast": (6, ["D"]),
    "dsl/istar/mm": (27, ["D", "G", "L"]),
    "dsl/istar/parser": (3, ["D"]),
    "dsl/iscn/mm": (5, ["G"]),
    "dsl/iscn/goalmodelinstance/mm": (1, ["G"]),
    "dsl/iscn/goalmodelinstance/parser": (2, ["G"]),
}
MARKINGS = [
    "GoalMarking", "TaskMarking", "IStarMarking", "IStarPropagation",
    "GoalTaskStatus", "QualityStatus", "ContributionPolarity",
]
EXTRAS = [
    "dsl/csl/ocl/CslOclState.java",
    "trace/cslTrace/nativecsl/NativeOclEvaluator.java",
] + [f"verify/conformance/semantics/{name}.java" for name in MARKINGS]
REFERENCES = [
    ("docs/model/csl.ecore", "REFERENCE_ONLY", "M2 crosswalk; Java/grammar remain executable authority"),
    ("docs/model/istar.ecore", "REFERENCE_ONLY", "Grammar/model gaps must be admitted explicitly"),
    ("goal/src/test/java/org/vnu/sme/goal/conformance/semantics/GoalTaskMarkingTest.java",
     "REFERENCE_ONLY", "Original recurrence assertions"),
    ("goal/src/test/java/org/vnu/sme/goal/csl/mm/CslSemanticValidatorTest.java",
     "REFERENCE_ONLY", "Original validation fixtures"),
    ("goal/src/test/java/org/vnu/sme/goal/csl/view/CslUseViewModelTest.java",
     "REFERENCE_ONLY", "V4 input fixtures only; do not copy GUI dependencies"),
    ("goal/src/main/resources/examples/Classroom/classroom.csl",
     "REFERENCE_ONLY", "Legacy group syntax expected rejection for current v4 frontend"),
    ("goal/src/main/java/org/vnu/sme/goal/translate/csl2use/Csl2UseTranslator.java",
     "DO_NOT_REUSE", "Legacy play-chain backend; v4 guard must remain"),
    ("goal/src/main/java/org/vnu/sme/goal/dsl/aol/state/CslSystemState.java",
     "REFERENCE_ONLY", "Legacy M0; not the authoritative native runtime"),
    ("goal/src/main/java/org/vnu/sme/goal/verify/cslstate/CslStateEvaluationSession.java",
     "DO_NOT_REUSE", "File workflow/private subset evaluator, not live MSystem attachment"),
]


def sha(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def git(*args: str) -> bytes:
    return subprocess.run(["git", "-C", str(UPSTREAM), *args], check=True,
                          capture_output=True).stdout


def inside_root(path: Path) -> Path:
    resolved = path.resolve()
    if not resolved.is_relative_to(ROOT.resolve()):
        raise ValueError(f"WRITE_OUTSIDE_USE:{path}")
    return resolved


def selected_paths() -> list[tuple[Path, list[str]]]:
    result = []
    for folder, (expected, tests) in GROUPS.items():
        paths = sorted((UPSTREAM / PREFIX / folder).glob("*.java"))
        if len(paths) != expected:
            raise ValueError(f"CLOSURE_COUNT_CHANGED:{folder}:{len(paths)}!={expected}")
        result.extend((path.relative_to(UPSTREAM), tests) for path in paths)
    result.extend((PREFIX / path, ["E", "F", "G"] if path.startswith("verify/") else ["A", "I"])
                  for path in EXTRAS)
    result.extend((Path(f"goal/src/main/resources/grammars/{name}.g4"), tests)
                  for name, tests in (("CSL", ["A"]), ("IStar", ["D"])))
    return sorted(result, key=lambda item: item[0].as_posix())


def make_manifest() -> dict:
    head = git("rev-parse", "HEAD").decode().strip()
    if head != PIN:
        raise ValueError(f"UPSTREAM_REVISION_CHANGED:{head}")
    files = []
    for relative, tests in selected_paths():
        source = UPSTREAM / relative
        data = source.read_bytes()
        text = data.decode("utf-8-sig")
        package_match = re.search(r"(?m)^package\s+([\w.]+)\s*;", text)
        package = package_match[1] if package_match else None
        fqcn = f"{package}.{source.stem}" if package else None
        if relative.suffix == ".java":
            target = Path("src/main/java") / relative.relative_to("goal/src/main/java")
        else:
            target = Path("src/main/resources/grammars") / source.name
        imports = re.findall(r"(?m)^import\s+(?:static\s+)?([\w.*]+)\s*;", text)
        head_data = git("show", f"HEAD:{relative.as_posix()}")
        contextual = source.name == "GoalModelInstanceTranslator.java"
        compatibility = source.name == "NativeOclEvaluator.java"
        files.append({
            "upstreamPath": relative.as_posix(), "upstreamCommit": head,
            "sourceSha256": sha(data), "headSha256": sha(head_data),
            "workingBytesDifferFromHead": data != head_data,
            "gitStatus": git("status", "--porcelain=v1", "--", relative.as_posix()).decode().strip(),
            "targetModule": MODULE.name, "targetPath": target.as_posix(),
            "targetSha256": sha(data), "fqcn": fqcn, "imports": imports,
            "reuseMode": "ORIGINAL_WITH_ADAPTER" if contextual or compatibility or "parser" in str(relative)
                         else "ORIGINAL_COPY",
            "plannedPatchIds": ["P-CTX-01"] if contextual else [],
            "appliedPatchIds": [], "extractionStatus": "TODO",
            "productionEvaluationAllowed": not compatibility,
            "requiredAdapters": (["CslFrontend", "Use75CslModelBackend"] if "/csl/" in relative.as_posix()
                                 else ["IStarFrontend", "Use4GoalRuntimeSessionAdapter"]),
            "parityTests": tests,
            "acceptance": "Byte-identical selected core; admitted-profile behavior equals pinned original",
            "license": "GPL-3.0 (upstream LICENSE; retain attribution)",
        })
    references = []
    for path, mode, reason in REFERENCES:
        data = (UPSTREAM / path).read_bytes()
        references.append({"upstreamPath": path, "sourceSha256": sha(data),
                           "reuseMode": mode, "reason": reason})
    license_data = (UPSTREAM / "LICENSE").read_bytes()
    return {
        "schemaVersion": "1.0.0", "upstreamRepository": "use4Goal",
        "upstreamCommit": head, "targetModule": MODULE.name,
        "javaRelease": 21, "antlrVersion": "4.9.3", "hostUseVersion": "7.5.0",
        "hostUseScope": "provided", "sourceOfTruth": "PINNED_WORKING_COPY_BYTES",
        "files": files, "referenceOnly": references,
        "license": {"upstreamPath": "LICENSE", "sourceSha256": sha(license_data),
                    "targetPath": "src/main/resources/META-INF/licenses/use4Goal-GPL-3.0.txt"},
        "generation": {
            "visitor": True, "listener": True, "packageFrom": "ORIGINAL_GRAMMAR_HEADER",
            "classesPerGrammar": ["Lexer", "Parser", "Visitor", "BaseVisitor", "Listener", "BaseListener"],
            "output": "target/generated-sources/antlr4",
            "copyOldGeneratedTarget": False,
        },
        "plannedPatches": [{
            "id": "P-CTX-01",
            "source": "dsl/iscn/goalmodelinstance/parser/GoalModelInstanceTranslator.java",
            "scope": "Public structured topology/bindings and existing dependency saturation seam",
            "semanticChange": False, "status": "TODO", "parityTests": ["G"],
        }],
        "forbiddenProductionDependencies": [
            "use-plugin", "use-gui", "jacamo-bridge-contract", "jacamo-bridge-jacamo",
            "jason", "cartago", "moise", "npl", "kodkod", "sat4j",
        ],
    }


def verify(manifest: dict, targets: bool = False) -> dict:
    files = manifest["files"]
    paths, classes = set(), set()
    for patch in manifest.get("plannedPatches", []):
        if not patch.get("status", "").startswith("APPLIED"):
            continue
        for item in [dict(targetPath=patch["patchPath"], sha256=patch["patchSha256"])] + patch.get("newApiFiles", []):
            target = inside_root(MODULE / item["targetPath"])
            if sha(target.read_bytes()) != item["sha256"]:
                raise ValueError(f"PATCH_HASH_MISMATCH:{target}")
    for entry in files:
        if entry["targetPath"] in paths:
            raise ValueError("DUPLICATE_TARGET")
        paths.add(entry["targetPath"])
        if entry["fqcn"]:
            if entry["fqcn"] in classes:
                raise ValueError("DUPLICATE_FQCN")
            classes.add(entry["fqcn"])
        target = inside_root(MODULE / entry["targetPath"])
        if not target.is_relative_to(MODULE.resolve()):
            raise ValueError("TARGET_ESCAPE")
        if sha((UPSTREAM / entry["upstreamPath"]).read_bytes()) != entry["sourceSha256"]:
            raise ValueError("SOURCE_HASH_MISMATCH")
        if targets and (not target.is_file() or sha(target.read_bytes()) != entry["targetSha256"]):
            raise ValueError(f"TARGET_HASH_MISMATCH:{target}")
        for dependency in entry["imports"]:
            if dependency.startswith(("jason.", "cartago.", "moise.", "npl.", "kodkod.",
                                      "org.sat4j.", "org.tzi.use.gui.", "org.tzi.use.plugins.",
                                      "org.jacamo.bridge.")):
                raise ValueError(f"FORBIDDEN_DEPENDENCY:{dependency}")
    generated = {f"org.vnu.sme.goal.dsl.{language.lower()}.parser.{language}{suffix}"
                 for language in ("CSL", "IStar")
                 for suffix in ("Lexer", "Parser", "Visitor", "BaseVisitor", "Listener", "BaseListener")}
    packages = {name.rsplit(".", 1)[0] for name in classes}
    for entry in files:
        for dependency in entry["imports"]:
            if not dependency.startswith("org.vnu.sme.goal."):
                continue
            candidate = dependency
            if dependency.endswith(".*") and dependency[:-2] in packages:
                continue
            while candidate and candidate not in classes and candidate not in generated:
                candidate = candidate.rsplit(".", 1)[0] if "." in candidate else ""
            if not candidate:
                raise ValueError(f"UNCLOSED_INTERNAL_IMPORT:{dependency}")
    return {"files": len(files), "javaSources": len(classes), "grammarInputs": 2,
            "closedImports": True, "duplicateFqcn": False, "forbiddenImports": False}


def self_test(manifest: dict) -> dict:
    verify(manifest)
    negative = []
    for name, alter in [
        ("sourceHash", lambda m: m["files"][0].update(sourceSha256="0" * 64)),
        ("duplicateTarget", lambda m: m["files"].append(copy.deepcopy(m["files"][0]))),
        ("duplicateFqcn", lambda m: m["files"].append(dict(
            m["files"][0], targetPath="src/main/java/duplicate/Duplicate.java"))),
        ("targetEscape", lambda m: m["files"][0].update(targetPath="../../outside-use")),
        ("forbiddenImport", lambda m: m["files"][0]["imports"].append("jason.asSemantics.Agent")),
        ("unclosedImport", lambda m: m["files"][0]["imports"].append("org.vnu.sme.goal.missing.Missing")),
        *(([("patchApiHash", lambda m: m["plannedPatches"][0]["newApiFiles"][0].update(sha256="0" * 64))])
          if manifest.get("plannedPatches", [{}])[0].get("newApiFiles") else []),
    ]:
        modified = copy.deepcopy(manifest)
        alter(modified)
        try:
            verify(modified)
        except ValueError:
            negative.append(name)
        else:
            raise AssertionError(f"NEGATIVE_NOT_REJECTED:{name}")
    return {"positive": "PASS", "negativeRejected": negative}


def archive(manifest: dict, path: Path) -> None:
    path = inside_root(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(path, "w", compression=zipfile.ZIP_DEFLATED) as output:
        for entry in manifest["files"] + [manifest["license"]]:
            name = entry["upstreamPath"]
            data = (UPSTREAM / name).read_bytes()
            if sha(data) != entry["sourceSha256"]:
                raise ValueError("SOURCE_CHANGED_DURING_ARCHIVE")
            item = zipfile.ZipInfo(name, (2026, 9, 18, 0, 0, 0))
            item.compress_type = zipfile.ZIP_DEFLATED
            output.writestr(item, data)


def extract(manifest: dict) -> None:
    verify(manifest)
    for entry in manifest["files"] + [manifest["license"]]:
        target = inside_root(MODULE / entry["targetPath"])
        data = (UPSTREAM / entry["upstreamPath"]).read_bytes()
        if target.exists() and target.read_bytes() != data:
            raise ValueError(f"REFUSE_OVERWRITE_DIFFERING_TARGET:{target}")
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(data)
        entry["extractionStatus"] = "ORIGINAL"
    verify(manifest, targets=True)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--write", action="store_true")
    parser.add_argument("--verify", action="store_true")
    parser.add_argument("--self-test", action="store_true")
    parser.add_argument("--extract", action="store_true")
    parser.add_argument("--oracle-archive", type=Path)
    options = parser.parse_args()
    manifest = (json.loads(MANIFEST.read_text("utf-8"))
                if options.verify or options.extract else make_manifest())
    checks = verify(manifest, targets=options.verify and any(
        entry.get("extractionStatus") != "TODO" for entry in manifest["files"]))
    if options.self_test:
        checks["selfTest"] = self_test(manifest)
    if options.oracle_archive:
        archive(manifest, options.oracle_archive)
    if options.extract:
        extract(manifest)
    if options.write or options.extract:
        inside_root(MANIFEST).parent.mkdir(parents=True, exist_ok=True)
        MANIFEST.write_text(json.dumps(manifest, indent=2, ensure_ascii=False) + "\n", "utf-8")
    print(json.dumps(checks, indent=2))


if __name__ == "__main__":
    main()
