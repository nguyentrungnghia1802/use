"""Collect fresh full-reactor evidence after `mvn --batch-mode clean verify`.

Run from any directory with Python 3. Uses only the standard library. No source
or canonical artifact is modified. Runtime evidence remains run-specific.
"""
from pathlib import Path
import hashlib
import json
import subprocess
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[2]
PLUGIN = ROOT / "use-plugin"
TARGET = PLUGIN / "target"


def git(*args):
    return subprocess.check_output(["git", *args], cwd=ROOT, text=True).strip()


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    files = set()
    suites = []
    for module in ("use-core", "use-gui", "use-plugin"):
        reports = sorted((ROOT / module / "target").glob("*-reports/TEST-*.xml"))
        if not reports:
            raise SystemExit(f"Missing reactor reports: {module}")
        for report in reports:
            suite = ET.parse(report).getroot()
            counts = {key: int(suite.get(key, "0")) for key in ("tests", "failures", "errors", "skipped")}
            if any(counts[key] for key in ("failures", "errors", "skipped")):
                raise SystemExit(f"Non-passing correctness report: {report}")
            suites.append({"module": module, "suite": suite.get("name"), **counts,
                           "seconds": float(suite.get("time", "0"))})
            files.add(report)
    required_suites = {"ReleasePackageIT", "CounterTeamIntegrationTest",
                       "LiveJaCaMoAuctionIntegrationTest", "RuntimeMappingTest",
                       "RuntimeFoundationTest", "GoldenPipelineTest"}
    if not required_suites.issubset({s["suite"].split(".")[-1] for s in suites}):
        raise SystemExit("Incomplete regression/release report set")

    for directory in (PLUGIN / "Core", PLUGIN / "src/main/resources/org/tzi/use/plugins/jacamo/runtime",
                      PLUGIN / "src/main/resources/org/tzi/use/plugins/jacamo/ocl",
                      PLUGIN / "src/main/resources/org/tzi/use/plugins/jacamo/verification",
                      TARGET / "phase14-auction-evidence", TARGET / "phase19-mirror-evidence",
                      TARGET / "phase24-counter-evidence"):
        if not directory.is_dir():
            raise SystemExit(f"Missing evidence directory: {directory}")
        files.update(p for p in directory.rglob("*") if p.is_file())
    package = TARGET / "use-jacamo-plugin-1.0.1.zip"
    checksum = package.with_suffix(".zip.sha256")
    if not package.is_file() or not checksum.is_file() or checksum.read_text().split()[0].lower() != sha(package):
        raise SystemExit("Missing or mismatched package checksum")
    with zipfile.ZipFile(package) as archive:
        if sum(not entry.is_dir() for entry in archive.infolist()) != 30:
            raise SystemExit("Unexpected release inventory")
    files.update((package, checksum, PLUGIN / "compatibility.json", PLUGIN / "KNOWN-LIMITATIONS.md",
                  PLUGIN / "release/release-manifest.json"))
    files.update((PLUGIN / "docs/project").glob("phase2*-*.md"))
    files.update(p for p in (PLUGIN / "docs/project/evidence").rglob("*") if p.is_file())

    source = {}
    for relative in git("ls-files").splitlines():
        p = ROOT / relative
        if p.is_file() and (relative.startswith("use-plugin/src/") or relative.endswith("pom.xml")
                            or relative.startswith("use-plugin/Core/")):
            # LF normalization supports the existing Windows checkout policy;
            # canonical binary hash entries below always use exact bytes.
            source[relative] = hashlib.sha256(p.read_bytes().replace(b"\r\n", b"\n")).hexdigest()
    summary = {
        "schemaVersion": "1.0.0", "sourceRevision": git("rev-parse", "HEAD"),
        "workingTreeStatus": git("status", "--porcelain"),
        "command": "mvn --batch-mode clean verify",
        "counts": {key: sum(s[key] for s in suites) for key in ("tests", "failures", "errors", "skipped")},
        "suites": suites, "sourceSha256LfNormalized": source,
        "artifactSha256": {p.relative_to(ROOT).as_posix(): sha(p) for p in sorted(files)},
        "limitations": ["In-process supported-subset cases, not standalone JaCaMo/NPL E2E",
                        "Runtime timestamps/UUIDs vary; no cross-toolchain archive identity claim",
                        "Final user acceptance is separate from automated engineering gates"],
    }
    manifest = TARGET / "closure-evidence.json"
    manifest.write_text(json.dumps(summary, indent=2) + "\n", encoding="utf-8")
    bundle = TARGET / "closure-evidence.zip"
    with zipfile.ZipFile(bundle, "w", zipfile.ZIP_DEFLATED) as archive:
        archive.write(manifest, "closure-evidence.json")
        for p in sorted(files):
            archive.write(p, p.relative_to(ROOT).as_posix())
    bundle.with_suffix(".zip.sha256").write_text(sha(bundle) + "  " + bundle.name + "\n", encoding="ascii")
    print(json.dumps({"sourceRevision": summary["sourceRevision"], "counts": summary["counts"],
                      "artifactCount": len(files), "bundle": str(bundle), "sha256": sha(bundle)}, indent=2))


if __name__ == "__main__":
    main()
