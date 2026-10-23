"""Validate a completed reactor run against the semantic migration A-V gates.

Uses the current invocation's log, not stale XML left by earlier focused runs.
No build, test execution, source mutation, or reference repository writes.
"""
from __future__ import annotations

import argparse
import base64
import hashlib
import io
import json
from pathlib import Path
import re
import zipfile

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
LOST_REPORT_PATH = r"C:\Users\NTNghia\OneDrive\Desktop\report.md"
LOST_REPORT_HASH = "E8F3A60B11C52B73136EA2BBAEDC92F40F9B70FF1C670D1BF70D1D49A43AE7DC"


def auction_exceptions(path: Path) -> dict:
    require(path.resolve().is_relative_to(ROOT), "AUCTION_EXCEPTION_OUTSIDE_USE")
    value = read_json(path)
    expected = {"path": LOST_REPORT_PATH, "sha256": LOST_REPORT_HASH, "status": "LOST",
                "reason": "User confirmed original report lost and authorized Auction-only exception on 2026-10-22"}
    require(value.get("schemaVersion") == "1.0.0" and value.get("profile") == "AUCTION_ACCEPTANCE_V1" and
            value.get("exceptions") == [expected], "AUCTION_EXCEPTION_NOT_AUTHORIZED")
    return value


def apply_auction_exceptions(protected: list, policy: dict) -> None:
    exception = policy["exceptions"][0]
    matches = [p for p in protected if p["path"] == exception["path"] and p["sha256"] == exception["sha256"]]
    require(len(matches) == 1, "AUCTION_EXCEPTION_BASELINE_MISMATCH")
    item = matches[0]
    if not item["unchanged"]:
        require(item.get("reason") == "PROTECTED_INPUT_MISSING" and not Path(item["path"]).exists(), "AUCTION_EXCEPTION_ONLY_MISSING")
        item.update(status="LOST", acceptedException=True, exceptionReason=exception["reason"])


def protected_inputs(collect_missing: bool = False) -> list:
    baseline_path = ROOT / "use-plugin/verification/semantic-migration/phase-0/baseline-inputs.json"
    baseline = json.loads(baseline_path.read_text(encoding="utf-8-sig"))
    protected = []
    for item in baseline["protected"]:
        path = Path(item["path"])
        if collect_missing and not path.is_file():
            protected.append(dict(item, unchanged=False, status="BLOCKED", reason="PROTECTED_INPUT_MISSING"))
            continue
        actual = sha(path.read_bytes()).upper()
        if actual != item["sha256"]:
            raise ValueError("PROTECTED_SOURCE_CHANGED:" + item["path"])
        protected.append(dict(item, unchanged=True))
    return protected


def validate(log: Path, legacy: Path | None = None, require_installed_live: bool = False,
             collect_missing: bool = False) -> dict:
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
    protected = protected_inputs(collect_missing)
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


def sha(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def read_json(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8-sig"))


def require(condition: bool, diagnostic: str) -> None:
    if not condition:
        raise ValueError(diagnostic)


def repository_path(value: str) -> Path:
    path = (ROOT / value).resolve()
    require(path.is_relative_to(ROOT), "AUCTION_EVIDENCE_OUTSIDE_USE")
    return path


def recording_program(path: Path, receipt: dict) -> dict:
    """Verify the entire recording, not just a claimed successful replay count."""
    with zipfile.ZipFile(path) as z:
        names = z.namelist()
        require(len(names) == len(set(names)), "AUCTION_DUPLICATE_RECORDING_ENTRY")
        require(z.getinfo("manifest.json").file_size <= 65536, "AUCTION_MANIFEST_BOUND")
        manifest = json.loads(z.read("manifest.json"))
        version = manifest["schemaVersion"]
        require(version in ("3.1.0", "3.2.0"), "AUCTION_RECORDING_SCHEMA")
        limit = manifest.get("runtimeByteLimit", 256 * 1024**2)
        require(type(limit) is int and (256 * 1024**2 < limit <= 1024**3
                if version == "3.2.0" else "runtimeByteLimit" not in manifest), "AUCTION_REPLAY_BUDGET")
        require(set(names) == set(manifest["files"]) | {"manifest.json"}, "AUCTION_RECORDING_CATALOG")
        for name, expected in manifest["files"].items():
            require(z.getinfo(name).file_size <= (limit if name == "runtime.jsonl" else 64 * 1024**2), "AUCTION_RECORDING_BOUND:" + name)
            digest = hashlib.sha256()
            with z.open(name) as source:
                for chunk in iter(lambda: source.read(1024**2), b""):
                    digest.update(chunk)
            require(digest.hexdigest() == expected, "AUCTION_RECORDING_HASH:" + name)
        for field in ("stateHash", "resultHash"):
            require(manifest["final" + field[0].upper() + field[1:]] == receipt[field], "AUCTION_RECORDING_FINAL:" + field)
        require(manifest["programHash"] == receipt["programHash"], "AUCTION_RECORDING_PROGRAM")
        program = json.loads(z.read("program.json"))
        require(program["schemaVersion"] == "1.1.0" and program["programHash"] == receipt["programHash"], "AUCTION_PROGRAM_SCHEMA")
        return program


def validate_house_fixture(log: Path) -> dict:
    text = log.read_text(encoding="utf-8", errors="replace")
    matches = re.findall(r"HOUSE_BUILDING_DOMAIN_LIVE_PASS fixture=HOUSE_BUILDING_CONDITION_WAIT_V1 evidence=(.+)", text)
    require(len(matches) == 1, "HOUSE_FIXTURE_LIVE_WITNESS")
    directory = repository_path(matches[0].strip())
    expected_path = ROOT / "use-plugin/src/test/resources/house-building-condition-wait-v1/manifest.json"
    expected = read_json(expected_path)
    proof = read_json(directory / "fixture-manifest.json")
    require(all(proof.get(k) == v for k, v in expected.items()) and
            proof["manifestSha256"] == sha(expected_path.read_bytes()), "HOUSE_FIXTURE_MANIFEST")
    staged = repository_path(proof["stagedPath"])
    require(staged.is_relative_to(directory / "staging") and sha(staged.read_bytes()) == expected["fixtureSha256"], "HOUSE_FIXTURE_STAGE_HASH")
    reference = ROOT.parent / "jacamo/examples/house-building/src/agt/giacomo.asl"
    require(sha(reference.read_bytes()) == expected["originalSha256"], "HOUSE_FIXTURE_REFERENCE_CHANGED")
    summary = read_json(directory / "summary.json")
    require(summary["status"] == "PASS" and summary["fixtureProfile"] == expected["profile"] and
            summary["sourceUnchanged"] is True and summary["sameActiveSystem"] is True and
            summary["auctionArtifacts"] == 8 and summary["directOwnerLinks"] == 1, "HOUSE_FIXTURE_LIVE_RESULT")
    return dict(profile=expected["profile"], manifestSha256=proof["manifestSha256"],
                originalSha256=expected["originalSha256"], fixtureSha256=expected["fixtureSha256"],
                scope=expected["expectedDifference"], evidence=str(directory))


def validate_auction(result: dict, archive: Path, evidence: Path) -> dict:
    """Explicit Auction qualification; old A–V/Counter results alone never qualify it.

    Index schema: {archiveSha256, runs: {canonical: <dir>, controlled: <dir>,
    development: <dir>}}. Each directory contains actual driver/replay receipts.
    Historical Q receipts establish coverage; fresh full-run suites and final
    archive/class bytes establish requalification of the delivered implementation.
    """
    require(result["legacyJUnit3"] is not None, "AUCTION_LEGACY_REQUIRED")
    require("clean:" in Path(result["reactorLog"]).read_text(encoding="utf-8", errors="replace"), "AUCTION_CLEAN_REACTOR_REQUIRED")
    house_fixture = validate_house_fixture(Path(result["reactorLog"]))
    index = read_json(evidence)
    archive_hash = sha(archive.read_bytes())
    require(index["archiveSha256"] == archive_hash, "AUCTION_ARCHIVE_HASH")
    by_name = {s["suite"]: s for s in result["suites"]}
    history = {}
    pins = None
    for gate in [f"Q{n:02}" for n in range(1, 8)] + ["E01", "E02"]:
        path = ROOT / "use-plugin/verification/auction" / gate / "acceptance.json"
        old = read_json(path)
        require(old["result"] == "ACCEPTED", "AUCTION_GATE_NOT_ACCEPTED:" + gate)
        for suite in old["suites"]:
            current = by_name.get(suite["suite"])
            require(current is not None and current["tests"] >= suite["tests"], "AUCTION_FRESH_SUITE_MISSING:" + suite["suite"])
        current_pins = {p["id"]: p for p in old["assetPins"]}
        require(len(current_pins) == 22, "AUCTION_ASSET_COUNT")
        require(pins is None or pins == current_pins, "AUCTION_HISTORICAL_PIN_MISMATCH")
        pins = current_pins
        history[gate] = {"receiptSha256": sha(path.read_bytes()), "status": "REQUALIFIED_BY_FULL_RUN"}
    for pin in pins.values():
        require(sha((ROOT / "use-plugin/examples/auction" / pin["path"]).read_bytes()) == pin["sha256"], "AUCTION_CANONICAL_CHANGED:" + pin["id"])
    require(set(index["runs"]) == {"canonical", "controlled", "development"}, "AUCTION_REQUIRED_LIVE_RUNS")
    with zipfile.ZipFile(archive) as outer:
        jar_names = [n for n in outer.namelist() if n.endswith(".jar") and ("/lib/" in n)]
        jars = {n: zipfile.ZipFile(io.BytesIO(outer.read(n))) for n in jar_names}
        try:
            live = {key: validate_auction_run(key, repository_path(value), archive_hash, pins, jars)
                    for key, value in index["runs"].items()}
        finally:
            for jar in jars.values():
                jar.close()
    return {"status": "PASS", "archive": str(archive), "archiveSha256": archive_hash,
            "history": history, "assetPins": list(pins.values()), "live": live, "houseBuildingFixture": house_fixture}


def validate_task_roster(kind: str, receipt: dict) -> None:
    # Five live agents/roles do not imply five mission commitments. Canonical
    # mParticipant min=1 is preserved; the controlled profile explicitly commits
    # its entire roster and must still prove all five exact players.
    agents, players = set(receipt["requiredPauseAgents"]), set(receipt["players"])
    require(len(agents) == 5 and len(receipt["players"]) == len(players) and
            2 <= len(players) <= 5 and players <= agents, "AUCTION_TASK_ROSTER")
    if kind != "canonical":
        require(players == agents, "AUCTION_CONTROLLED_TASK_ROSTER")


def validate_auction_run(kind: str, directory: Path, archive_hash: str, pins: dict, jars: dict) -> dict:
    run = read_json(directory / "run.json")
    r = read_json(directory / "consumer-receipt.json")
    require(run["fullHostArchiveSha256"] == archive_hash, "AUCTION_STALE_LIVE_ARCHIVE:" + kind)
    scope = {"canonical": "INSTALLED_CANONICAL_AUCTION_LIVE", "controlled": "INSTALLED_CONTROLLED_AUCTION_LIVE",
             "development": "DEVELOPMENT_CONTROLLED_AUCTION_LIVE"}[kind]
    require(r["status"] == "PASS" and r["scope"] == scope, "AUCTION_LIVE_SCOPE:" + kind)
    require(r["producerPid"] > 0 and r["consumerPid"] > 0 and r["producerPid"] != r["consumerPid"], "AUCTION_PID_PAIR")
    require(r["runtimeChildProcesses"] == 0 and r["officialProducerExitUseSurvives"] is True, "AUCTION_RUNTIME_OWNERSHIP")
    require(r["journalGap"] is False and 0 < r["journalBytes"] < r["journalLimit"], "AUCTION_LIVE_GAP")
    require(0 <= r["heartbeatP95Millis"] < 250, "AUCTION_EDT_BOUND")
    validate_task_roster(kind, r)
    require(len(r["schemes"]) == 2, "AUCTION_CAST")
    require(set(r["acknowledgedPauseAgents"]) == set(r["requiredPauseAgents"]) and len(r["requiredPauseAgents"]) == 5, "AUCTION_PAUSE_ACKS")
    tasks = {"A_start_t1", "A_start_t2", "A_decide_t1", "A_decide_t2", "P_bid_t1", "P_bid_t2"}
    require(len(r["scopedTerminals"]) == 2 and all(set(s) == tasks for s in r["scopedTerminals"].values()), "AUCTION_TASK_SCOPE")
    require(set(r["nativeOperationClauses"]) == {"COMPILED:post start::Opened", "COMPILED:post stop::Closed",
            "COMPILED:pre bid::RunningForBid", "COMPILED:pre start::Idle", "COMPILED:pre stop::RunningForStop"}, "AUCTION_NATIVE_CLAUSES")
    require({"TaskMarking.update", "IStarPropagation.closePending", "GoalModelInstanceTranslator.propagateDependencies"} <= set(r["originalCalls"]), "AUCTION_ORIGINAL_CORE_CALLS")
    require(len(r["monitorCycles"]) == 2 and all(c["closed"] and c["terminated"] and
            all(c[f] == 0 for f in ("expressions", "pendingPins", "pinnedSerializedBytes", "queue")) for c in r["monitorCycles"]), "AUCTION_RETIRED_STATE")
    require(r["guard"]["hostVersion"] == "7.5.0" and r["guard"]["antlrVersion"] == "4.9.3", "AUCTION_ENGINE_VERSION")
    if kind != "development":
        require(len(r["engineClasses"]) == 8 and len(set(r["engineClasses"].values())) == 1, "AUCTION_ENGINE_OWNER")
        require(all("/lib/plugins/" in value.replace("\\", "/") for value in r["engineClasses"].values()), "AUCTION_INSTALLED_LOADER")
        require(not any(token in r["classpath"].lower().replace("\\", "/") for token in ("/test-classes", "junit", "jason-interpreter", "cartago-", "jacamo-1.")), "AUCTION_CONSUMER_FRAMEWORK_CLASSPATH")
    else:
        # Development driver uses the reactor artifact and its real application
        # loader. EngineInstallationGuard supplies ownership/hash checks; do not
        # invent cold-plugin origins absent from this receipt schema.
        origin = r["guard"]["engineOrigin"].replace("\\", "/")
        require(origin.endswith("/use-plugin/target/use-plugin-1.0.1.jar") and
                "/use-plugin/target/use-plugin-1.0.1.jar" in r["classpath"].replace("\\", "/"), "AUCTION_DEVELOPMENT_ENGINE_ORIGIN")
    command = run["producerCommand"]
    producer_cp = command[command.index("-cp") + 1].lower().replace("\\", "/")
    require(not any(token in producer_cp for token in ("/test-classes", "junit")), "AUCTION_PRODUCER_TEST_CLASSPATH")
    accepted = read_json(directory / "accepted.json")
    replay = read_json(directory / "replay-report.json")
    require(replay["complete"] is True and replay["checkedEntries"] == r["replayCheckedEntries"] > 0, "AUCTION_REPLAY_INCOMPLETE")
    for field in ("stateHash", "resultHash"):
        require(accepted[field] == r[field] == replay["final" + field[0].upper() + field[1:]], "AUCTION_REPLAY_PARITY:" + field)
    program = recording_program(directory / "recording.zip", r)
    core = accepted["semantic"]["observation"]["originalEngine"]
    require(core["evaluated"] == "PINNED_NATIVE_CUT" and core["engineProfile"] == "DERIVED_AND_RESOURCE_V1" and
            core["historyQuality"] == "OBSERVED_PREFIX", "AUCTION_ACCEPTED_CORE_CUT")
    for field in ("programHash", "stateHash"):
        require(core[field] == r[field], "AUCTION_CORE_IDENTITY:" + field)
    for field in ("cslHash", "goalHash"):
        require(core[field] == program[field] == r["semanticProgram"][field], "AUCTION_CORE_MODEL_HASH:" + field)
    require(sha(json.dumps(program["source"], sort_keys=True, separators=(",", ":"), ensure_ascii=False).encode("utf-8")) ==
            r["semanticProgram"]["sourceHash"] == r["caseProgram"]["sourceHash"], "AUCTION_OFFICIAL_SOURCE_HASH")
    case_manifest = program["case"]["manifest"]
    require(sha(base64.b64decode(case_manifest["contentBase64"], validate=True)) == case_manifest["sha256"], "AUCTION_CASE_MANIFEST_HASH")
    require(len(core["classHashes"]) == 5 and all(program["implementation"].get(name) == digest
            for name, digest in core["classHashes"].items()), "AUCTION_RUNTIME_CORE_CLASS_HASH")
    for key, expected in program["implementation"].items():
        resource = key.lstrip("/") if key.startswith("/") else key.replace(".", "/") + ".class"
        owners = [(name, jar) for name, jar in jars.items() if resource in jar.namelist()]
        require(owners and all(sha(jar.read(resource)) == expected for _, jar in owners), "AUCTION_DEPLOYED_CLASS_HASH:" + key)
        if key.startswith("org.vnu.sme.goal."):
            require(len(owners) == 1 and "/lib/plugins/" in owners[0][0], "AUCTION_DUPLICATE_ENGINE:" + key)
    for name, digest in r["guard"].items():
        if name in ("hostVersion", "antlrVersion", "engineOrigin"):
            continue
        resource = ("META-INF/use4goal-provenance.json" if name == "provenanceHash" else
                    name if name.startswith("grammars/") else name.replace(".", "/") + ".class")
        owners = [jar for jar in jars.values() if resource in jar.namelist()]
        require(owners and all(sha(jar.read(resource)) == digest for jar in owners), "AUCTION_GUARD_DEPLOYED_HASH:" + name)
    assets = {a["id"]: a for a in program["case"]["assets"]}
    require(set(assets) == set(pins), "AUCTION_PROGRAM_ASSETS")
    receipt_assets = {a["id"]: a["sha256"] for a in r["caseProgram"]["assetHashes"]}
    require(set(receipt_assets) == set(assets), "AUCTION_RECEIPT_ASSET_CATALOG")
    changed = set()
    for ident, asset in assets.items():
        digest = sha(base64.b64decode(asset["contentBase64"], validate=True))
        require(digest == asset["sha256"] == receipt_assets[ident], "AUCTION_PROGRAM_ASSET_HASH:" + ident)
        if digest != pins[ident]["sha256"]:
            changed.add(ident)
    require(changed == (set() if kind == "canonical" else {"src-jcm", "input-ocl", "case-rules"}), "AUCTION_UNDECLARED_VARIANT:" + str(changed))
    require(program["cslHash"] == r["semanticProgram"]["cslHash"] and program["goalHash"] == r["semanticProgram"]["goalHash"], "AUCTION_CANONICAL_MODEL_HASH")
    if kind != "canonical":
        variant = read_json(directory / "control-profile.json")
        fixture_dir = directory / "fixture-control"
        fixture = read_json(fixture_dir / "manifest.json")
        require(variant["canonicalSourceUnchanged"] is True and variant["lateRole"] is True and
                variant["canonicalManifestSha256"] == sha((ROOT / "use-plugin/examples/auction/case-manifest.json").read_bytes()), "AUCTION_CONTROL_CANONICAL_SOURCE")
        require(variant["variantManifestSha256"] == fixture["inputCaseManifestSha256"] == program["case"]["manifest"]["sha256"], "AUCTION_CONTROL_INPUT_HASH")
        require(variant["changedAssets"] == {ident: assets[ident]["sha256"] for ident in changed}, "AUCTION_CONTROL_VARIANT_HASH")
        for name, digest in fixture["sourceHashes"].items():
            location = fixture_dir / ("classes/" + name if name.endswith(".class") else name)
            require(sha(location.read_bytes()) == digest, "AUCTION_CONTROL_SOURCE_HASH:" + name)
            if not name.endswith(".class"):
                require(sha((ROOT / "use-plugin/src/test/resources/auction-controls" / name).read_bytes()) == digest, "AUCTION_CONTROL_SOURCE_STALE:" + name)
        require(read_json(fixture_dir / "ready.json")["producerPid"] == r["producerPid"], "AUCTION_CONTROL_PRODUCER_IDENTITY")
        attribution = read_json(directory / "controlled-attribution-acceptance.json")
        require(attribution["status"] == "PASS" and attribution["actualBidPlayers"] == ["alice", "maria"] and len(attribution["distinctInvocations"]) == 5, "AUCTION_ACTOR_ATTRIBUTION")
        require(r["controlledNativeAtPre"] is True and r["controlledOperationCount"] == 5 and r["controlledOriginalFailedInvocations"] == 8, "AUCTION_CONTROLLED_NATIVE_PRE")
        require(max(attribution["originalStartVersions"].values()) < min(attribution["originalStopVersions"].values()), "AUCTION_TWO_OVERLAPPING_INSTANCES")
    return {"status": "PASS", "scope": scope, "producerPid": r["producerPid"], "consumerPid": r["consumerPid"],
            "receiptSha256": sha((directory / "consumer-receipt.json").read_bytes()),
            "recordingSha256": sha((directory / "recording.zip").read_bytes()), "stateHash": r["stateHash"], "resultHash": r["resultHash"]}


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--log", type=Path, required=True)
    parser.add_argument("--legacy-log", type=Path)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--require-installed-live", action="store_true")
    parser.add_argument("--require-auction", action="store_true")
    parser.add_argument("--archive", type=Path)
    parser.add_argument("--auction-evidence", type=Path)
    parser.add_argument("--auction-exceptions", type=Path)
    args = parser.parse_args()
    target = args.output.resolve()
    if not target.is_relative_to(ROOT):
        raise ValueError("ACCEPTANCE_OUTPUT_OUTSIDE_USE")
    if args.require_auction and not (args.archive and args.auction_evidence and args.legacy_log and args.require_installed_live):
        parser.error("Auction requires --archive, --auction-evidence, --legacy-log and --require-installed-live")
    if args.auction_exceptions and not args.require_auction:
        parser.error("--auction-exceptions is restricted to --require-auction")
    result = validate(args.log.resolve(), args.legacy_log.resolve() if args.legacy_log else None, args.require_installed_live, args.require_auction)
    if args.require_auction:
        result["auction"] = validate_auction(result, args.archive.resolve(), args.auction_evidence.resolve())
        if args.auction_exceptions:
            policy = auction_exceptions(args.auction_exceptions)
            apply_auction_exceptions(result["protectedInputs"], policy)
            result["authorizedExceptions"] = dict(policy, policySha256=sha(args.auction_exceptions.read_bytes()))
        blocked = any(not p["unchanged"] and not p.get("acceptedException", False) for p in result["protectedInputs"])
        result["status"] = "BLOCKED" if blocked else "ACCEPTED_WITH_EXCEPTIONS" if result.get("authorizedExceptions") else "ACCEPTED"
        result["scope"] = "Auction explicit final archive qualification plus A–V/Counter functional gates; authorized LOST is not a verified hash and does not waive default historical integrity"
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(json.dumps(result, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(json.dumps({"unit": result["unit"], "integration": result["integration"], "gates": len(result["matrix"]), "legacy": result["legacyJUnit3"]}))
    if result.get("status") == "BLOCKED":
        raise SystemExit("PROTECTED_INPUT_MISSING: functional receipts collected; acceptance remains BLOCKED")


if __name__ == "__main__":
    main()
