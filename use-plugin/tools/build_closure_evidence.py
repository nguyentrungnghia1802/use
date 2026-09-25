"""Build and verify the Phase 44 final V2 reproducibility evidence bundle.

Run after the clean full-reactor, relocated-checkout and installed-package gates.
The command is read-only except for target/v2-final-evidence.{json,zip,zip.sha256}.
It uses only the Python standard library and never changes canonical resources.
"""

from __future__ import annotations

from pathlib import Path
import hashlib
import json
import subprocess
import xml.etree.ElementTree as ET
import zipfile


ROOT = Path(__file__).resolve().parents[2]
PLUGIN = ROOT / "use-plugin"
TARGET = PLUGIN / "target"
MIGRATION = PLUGIN / "docs/project/v2-migration"
REQUIRED_SUITES = {
    "V2EcoreAuditTest",
    "V2MappingAuditTest",
    "V2FinalFreezeTest",
    "GoldenPipelineTest",
    "ConstraintClosureTest",
    "TraceBindingTest",
    "RuntimeMappingTest",
    "RuntimeFoundationTest",
    "LiveJaCaMoAuctionIntegrationTest",
    "CounterTeamIntegrationTest",
    "MultiCaseV2PipelineTest",
    "ReleasePackageIT",
}
REQUIRED_GATES = (
    "phase44-candidate-gate.json",
    "phase44-focused-gate.json",
    "phase44-module-verify.json",
    "phase44-reactor-verify.json",
    "phase44-relocated-verify.json",
    "phase44-installed-smoke.json",
    "phase44-freeze-diff.json",
)
REQUIRED_GENERATED = (
    "phase35-auction-evidence/offline/auction.use",
    "phase35-auction-evidence/offline/auction.cmd",
    "phase35-auction-evidence/offline/auction-ocl.use",
    "phase35-auction-evidence/offline/translated-ocl-provenance.txt",
    "phase35-auction-evidence/offline/trace.json",
    "phase35-auction-evidence/offline/verification-pass.json",
    "phase35-auction-evidence/offline/verification-fail.json",
    "phase35-auction-evidence/runtime/event-log.json",
    "phase35-auction-evidence/runtime/verification-reports.json",
    "phase35-auction-evidence/runtime/scenario-summary.json",
    "phase24-counter-evidence/model.use",
    "phase24-counter-evidence/initial-state.cmd",
    "phase24-counter-evidence/trace.json",
    "phase24-counter-evidence/runtime-events.json",
    "phase24-counter-evidence/reports.json",
    "phase24-counter-evidence/summary.json",
    "phase19-mirror-evidence/mirror-correctness.json",
    "phase19-mirror-evidence/manifest.json",
)


def git(*arguments: str) -> str:
    return subprocess.check_output(["git", *arguments], cwd=ROOT, text=True, encoding="utf-8").strip()


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def require_file(path: Path) -> Path:
    if not path.is_file():
        raise SystemExit(f"Missing required evidence: {path}")
    return path


def passing_gate(path: Path) -> dict:
    record = json.loads(require_file(path).read_text(encoding="utf-8"))
    if record.get("status") != "PASS":
        raise SystemExit(f"Gate is not PASS: {path}")
    counts = record.get("counts")
    if counts and any(counts.get(key, 0) for key in ("failures", "errors", "skipped")):
        raise SystemExit(f"Gate has non-passing or skipped tests: {path}")
    return record


def reactor_reports() -> tuple[list[dict], set[Path]]:
    suites: list[dict] = []
    files: set[Path] = set()
    for module in ("use-core", "use-gui", "use-plugin"):
        reports = sorted((ROOT / module / "target").glob("*-reports/TEST-*.xml"))
        if not reports:
            raise SystemExit(f"Missing reactor reports: {module}")
        for report in reports:
            suite = ET.parse(report).getroot()
            counts = {key: int(suite.get(key, "0")) for key in ("tests", "failures", "errors", "skipped")}
            if any(counts[key] for key in ("failures", "errors", "skipped")):
                raise SystemExit(f"Non-passing correctness report: {report}")
            suites.append(
                {
                    "module": module,
                    "suite": suite.get("name"),
                    **counts,
                    "seconds": float(suite.get("time", "0")),
                }
            )
            files.add(report)
    observed = {entry["suite"].split(".")[-1] for entry in suites}
    missing = sorted(REQUIRED_SUITES - observed)
    if missing:
        raise SystemExit(f"Incomplete final correctness suite set: {missing}")
    return suites, files


def verify_release_package() -> tuple[Path, Path, Path, list[dict]]:
    release = json.loads((PLUGIN / "release/release-manifest.json").read_text(encoding="utf-8"))
    if release.get("status") != "FROZEN_V2_RELEASE_CANDIDATE":
        raise SystemExit("Release manifest is not the frozen V2 candidate")
    package = require_file(TARGET / release["artifact"])
    checksum = require_file(TARGET / release["integrity"]["sidecar"])
    if checksum.read_text(encoding="ascii").split()[0].lower() != sha256(package):
        raise SystemExit("Release ZIP sidecar mismatch")
    expected = {entry["path"]: entry for entry in release["packageEntries"]}
    inventory = []
    with zipfile.ZipFile(package) as archive:
        names = {entry.filename for entry in archive.infolist() if not entry.is_dir()}
        if names != set(expected):
            raise SystemExit(
                f"Release inventory mismatch: missing={sorted(set(expected) - names)}, "
                f"unexpected={sorted(names - set(expected))}"
            )
        for name in sorted(names):
            source = (PLUGIN / expected[name]["source"]).resolve()
            require_file(source)
            archived = archive.read(name)
            if archived != source.read_bytes():
                raise SystemExit(f"Release entry differs from declared source: {name}")
            inventory.append({"path": name, "sha256": hashlib.sha256(archived).hexdigest(),
                              "bytes": len(archived)})
    jar = require_file(TARGET / "use-plugin-1.0.1.jar")
    return package, checksum, jar, inventory


def stable_write(archive: zipfile.ZipFile, name: str, data: bytes) -> None:
    info = zipfile.ZipInfo(name, (1980, 1, 1, 0, 0, 0))
    info.compress_type = zipfile.ZIP_DEFLATED
    info.external_attr = 0o100644 << 16
    archive.writestr(info, data)


def main() -> None:
    suites, files = reactor_reports()
    gate_records = {}
    for name in REQUIRED_GATES:
        path = MIGRATION / name
        gate_records[name] = passing_gate(path)
        files.add(path)

    freeze = json.loads((PLUGIN / "release/v2-freeze-manifest.json").read_text(encoding="utf-8"))
    if freeze.get("status") != "FROZEN" or freeze.get("freeze") is not True:
        raise SystemExit("Unified V2 freeze manifest is not final")
    for resource in freeze["resources"].values():
        if "path" in resource:
            path = require_file(PLUGIN / resource["path"])
            if sha256(path) != resource["sha256"]:
                raise SystemExit(f"Frozen resource hash mismatch: {path}")
            files.add(path)
        if "schemaPath" in resource:
            path = require_file(PLUGIN / resource["schemaPath"])
            if sha256(path) != resource["schemaSha256"]:
                raise SystemExit(f"Frozen schema hash mismatch: {path}")
            files.add(path)
        if "manifestPath" in resource:
            path = require_file(PLUGIN / resource["manifestPath"])
            if sha256(path) != resource["manifestSha256"]:
                raise SystemExit(f"Frozen profile manifest hash mismatch: {path}")
            files.add(path)

    for relative in REQUIRED_GENERATED:
        files.add(require_file(TARGET / relative))
    scenario = json.loads((TARGET / "phase35-auction-evidence/runtime/scenario-summary.json")
                          .read_text(encoding="utf-8"))
    if scenario.get("artifactKind") != "V2_FROZEN_RUNTIME_SCENARIO_SUMMARY":
        raise SystemExit("Auction runtime evidence is not from the frozen V2 pipeline")
    if scenario.get("reconnectResync") != "PASS" or scenario.get("reconnectDriftDifferenceCount") != 0:
        raise SystemExit("Reconnect/resync evidence is incomplete")
    static = json.loads((TARGET / "phase35-auction-evidence/offline/manifest.json")
                        .read_text(encoding="utf-8"))
    if static.get("artifactKind") != "V2_FROZEN_STATIC_EVIDENCE_MANIFEST":
        raise SystemExit("Auction static evidence is not from the frozen V2 pipeline")
    counter = json.loads((TARGET / "phase24-counter-evidence/summary.json").read_text(encoding="utf-8"))
    if counter.get("status") != "SUPPORTED_SUBSET_COMPLETE":
        raise SystemExit("Case Study #2 evidence is incomplete")
    mirror = json.loads((TARGET / "phase19-mirror-evidence/mirror-correctness.json")
                        .read_text(encoding="utf-8"))
    if mirror.get("status") != "SUPPORTED_SUBSET_COMPLETE" or mirror.get("unexplainedDrift") != 0:
        raise SystemExit("Mirror correctness evidence is incomplete")

    package, checksum, jar, package_inventory = verify_release_package()
    files.update((package, checksum, jar))
    for relative in (
        "compatibility.json",
        "KNOWN-LIMITATIONS.md",
        "release/release-manifest.json",
        "release/v2-freeze-manifest.json",
        "docs/project/v2-migration/phase43-determinism.json",
        "docs/project/v2-migration/phase43-performance.json",
    ):
        files.add(require_file(PLUGIN / relative))
    for log in sorted(TARGET.glob("phase44-*.log")):
        files.add(log)

    counts = {key: sum(suite[key] for suite in suites) for key in ("tests", "failures", "errors", "skipped")}
    source = {}
    for relative in git("ls-files").splitlines():
        path = ROOT / relative
        if path.is_file() and (
            relative.startswith("use-plugin/src/")
            or relative.startswith("use-plugin/Core/")
            or relative.startswith("use-plugin/release/")
            or relative.endswith("pom.xml")
        ):
            source[relative] = sha256(path)

    artifacts = {path.relative_to(ROOT).as_posix(): sha256(path) for path in sorted(files)}
    summary = {
        "schemaVersion": "2.0.0",
        "phase": 44,
        "status": "PASS",
        "baseline": "V2_FROZEN",
        "sourceRevision": git("rev-parse", "HEAD"),
        "workingTreeStatus": git("status", "--porcelain"),
        "commands": {name: record.get("command") for name, record in gate_records.items()},
        "counts": counts,
        "zeroUnexpectedSkippedCorrectnessTests": counts["skipped"] == 0,
        "suites": suites,
        "frozenContracts": freeze["resources"],
        "package": {
            "path": package.relative_to(ROOT).as_posix(),
            "sha256": sha256(package),
            "jarPath": jar.relative_to(ROOT).as_posix(),
            "jarSha256": sha256(jar),
            "inventory": package_inventory,
        },
        "sourceSha256ExactBytes": source,
        "artifactSha256": artifacts,
        "boundaries": freeze["unresolvedBoundaries"],
        "reproducibility": {
            "archiveEntryOrder": "lexicographic",
            "archiveEntryTimestamp": "1980-01-01T00:00:00",
            "runtimeValues": "UUIDs, timestamps and observed durations remain run-specific and are captured exactly",
        },
    }
    manifest = TARGET / "v2-final-evidence.json"
    manifest.write_text(json.dumps(summary, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    bundle = TARGET / "v2-final-evidence.zip"
    with zipfile.ZipFile(bundle, "w") as archive:
        stable_write(archive, "v2-final-evidence.json", manifest.read_bytes())
        for path in sorted(files, key=lambda item: item.relative_to(ROOT).as_posix()):
            stable_write(archive, path.relative_to(ROOT).as_posix(), path.read_bytes())
    sidecar = bundle.with_suffix(".zip.sha256")
    sidecar.write_text(f"{sha256(bundle)}  {bundle.name}\n", encoding="ascii")
    print(
        json.dumps(
            {
                "status": "PASS",
                "sourceRevision": summary["sourceRevision"],
                "counts": counts,
                "artifactCount": len(files),
                "bundle": str(bundle),
                "sha256": sha256(bundle),
            },
            indent=2,
        )
    )


if __name__ == "__main__":
    main()
