"""Validate a completed reactor run against the semantic migration A-V gates.

Uses the current invocation's log, not stale XML left by earlier focused runs.
No build, test execution, source mutation, or reference repository writes.
"""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[2]
MATRIX = {
    "A": ["CslFrontendParityTest"],
    "B": ["CslFrontendParityTest", "Use75CslBackendTest"],
    "C": ["CslFrontendParityTest"],
    "D": ["IStarFrontendParityTest"],
    "E": ["OriginalRuntimeSessionTest"],
    "F": ["OriginalRuntimeSessionTest", "CanonicalAsyncInvocationTest"],
    "G": ["OriginalRuntimeSessionTest", "CanonicalGoalMappingTest", "OriginalEngineSessionIntegrationTest"],
    "H": ["Use75CslBackendTest", "CanonicalCslNativePipelineTest"],
    "I": ["NativeOclBackendTest", "CanonicalOclContractMappingTest"],
    "J": ["NativeOclBackendTest", "CanonicalAsyncInvocationTest", "CanonicalOperationCheckpointsTest"],
    "K": ["CanonicalCslMappingTest", "CanonicalMappingContractTest"],
    "L": ["CanonicalGoalMappingTest"],
    "M": ["CanonicalRuntimeProjectionTest", "Use4GoalExternalBridgeIT"],
    "N": ["CanonicalRuntimeAdmissionTest", "Use4GoalExternalBridgeIT"],
    "O": ["OriginalEngineSessionIntegrationTest", "Use4GoalExternalBridgeIT", "Use4GoalWorkbenchIT"],
    "P": ["CanonicalRuntimeAdmissionTest", "SnapshotCoverageRegressionTest"],
    "Q": ["NativeResyncHandoffRegressionTest", "CanonicalReplayTest", "Use4GoalExternalBridgeIT"],
    "R": ["CanonicalTransactionFailureTest", "CanonicalNativeFaultMatrixTest"],
    "S": ["CanonicalMonitorLifecycleTest", "Use4GoalExternalBridgeIT", "Use4GoalWorkbenchIT"],
    "T": ["CanonicalReplayTest", "SemanticMappingReplayTest", "NativeRuntimeReplayTest", "Use4GoalPackagedReplayIT"],
    "U": ["Use4GoalPackagedReplayIT", "NativeRuntimePackagedReplayIT", "SemanticPackagedReplayIT", "GuiPluginStagingIT", "ReleasePackageIT"],
    "V": ["Use4GoalExternalBridgeIT", "Use4GoalWorkbenchIT"],
}
PATTERN = re.compile(
    r"Tests run: (\d+), Failures: (\d+), Errors: (\d+), Skipped: (\d+), "
    r"Time elapsed: .*? --? in ([\w.$]+)"
)


def validate(log: Path, legacy: Path | None = None, require_installed_live: bool = False) -> dict:
    text = log.read_text(encoding="utf-8", errors="replace")
    if "BUILD SUCCESS" not in text or "BUILD FAILURE" in text:
        raise ValueError("REACTOR_NOT_SUCCESSFUL")
    if "failsafe:" not in text or "integration-test" not in text:
        raise ValueError("FAILSAFE_NOT_EXECUTED")
    suites = []
    for match in PATTERN.finditer(text):
        counts = dict(zip(("tests", "failures", "errors", "skipped"), map(int, match.groups()[:4])))
        if counts["tests"] < 1 or any(counts[key] for key in ("failures", "errors", "skipped")):
            raise ValueError("NONPASS_REQUIRED_SUITE:" + match[5])
        suites.append(dict(counts, suite=match[5]))
    if not suites:
        raise ValueError("NO_EXECUTED_TEST_SUITES")
    by_name = {entry["suite"].rsplit(".", 1)[-1]: entry for entry in suites}
    matrix = {}
    required = {gate: list(names) for gate, names in MATRIX.items()}
    if require_installed_live:
        required["U"] += ["Use4GoalPackagedPluginIT", "EngineInstallationGuardTest"]
        required["V"] += ["Use4GoalPackagedPluginIT"]
        required["K"] += ["DeclaredArtifactTypeProofTest"]
    for gate, names in required.items():
        missing = set(names) - by_name.keys()
        if missing:
            raise ValueError("MATRIX_MISSING:" + gate + ":" + ",".join(sorted(missing)))
        matrix[gate] = {"status": "DONE", "suites": [by_name[name] for name in names]}
    legacy_result = None
    if legacy:
        content = legacy.read_text(encoding="utf-8", errors="replace")
        found = re.search(r"OK \((\d+) tests\)", content)
        if not found or int(found[1]) != 198 or "FAILURES!!!" in content:
            raise ValueError("LEGACY_JUNIT3_NOT_PASS")
        legacy_result = {"tests": int(found[1]), "log": str(legacy)}
    baseline_path = ROOT / "use-plugin/verification/semantic-migration/phase-0/baseline-inputs.json"
    baseline = json.loads(baseline_path.read_text(encoding="utf-8-sig"))
    protected = []
    for item in baseline["protected"]:
        actual = hashlib.sha256(Path(item["path"]).read_bytes()).hexdigest().upper()
        if actual != item["sha256"]:
            raise ValueError("PROTECTED_SOURCE_CHANGED:" + item["path"])
        protected.append(dict(item, unchanged=True))
    return {
        "reactorLog": str(log), "logSha256": hashlib.sha256(log.read_bytes()).hexdigest(),
        "unit": sum(s["tests"] for s in suites if not s["suite"].endswith("IT")),
        "integration": sum(s["tests"] for s in suites if s["suite"].endswith("IT")),
        "failures": 0, "errors": 0, "skipped": 0,
        "matrix": matrix, "suites": suites, "legacyJUnit3": legacy_result,
        "protectedInputs": protected,
        "installedLiveRequired": require_installed_live,
        "scope": ("admitted observed profile including installed real Counter, production quotas and deployment rejection gates; full archive receipts are recorded separately"
                  if require_installed_live else "admitted observed profile; packaged live/production-quota release gate remains a separate Phase21 assertion"),
    }


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--log", type=Path, required=True)
    parser.add_argument("--legacy-log", type=Path)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--require-installed-live", action="store_true")
    args = parser.parse_args()
    target = args.output.resolve()
    if not target.is_relative_to(ROOT):
        raise ValueError("ACCEPTANCE_OUTPUT_OUTSIDE_USE")
    result = validate(args.log.resolve(), args.legacy_log.resolve() if args.legacy_log else None, args.require_installed_live)
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(json.dumps(result, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(json.dumps({"unit": result["unit"], "integration": result["integration"], "gates": len(result["matrix"]), "legacy": result["legacyJUnit3"]}))


if __name__ == "__main__":
    main()
