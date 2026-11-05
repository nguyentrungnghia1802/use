"""Independently reconcile persisted native cuts against the producer ledger.

Raw cuts and source spool frames remain local. This publishes compact receipts,
including digests, without copying private transport credentials or snapshots.
"""
import collections
import gzip
import hashlib
import json
import os
import re
from pathlib import Path

HERE = Path(__file__).resolve().parent
PLUGIN = HERE.parents[1]
sha = lambda data: hashlib.sha256(data).hexdigest()
canonical = lambda value: json.dumps(value, sort_keys=True, separators=(",", ":"), ensure_ascii=False).encode()


def ledger_for(run):
    spool = Path(os.environ["TEMP"]) / "jacamo-bridge-spool"
    for path in sorted(spool.glob("verified-checkpoints-*.frames"), key=lambda p: p.stat().st_mtime, reverse=True):
        records = []
        with path.open("rb") as stream:
            while header := stream.read(4):
                assert len(header) == 4
                size = int.from_bytes(header, "big")
                digest, frame = stream.read(32), stream.read(size)
                assert len(frame) == size and hashlib.sha256(frame).digest() == digest
                record = json.loads(frame)
                if not records and record.get("run") != run:
                    break
                records.append(record)
        if records:
            return path, records
    raise AssertionError("Missing producer ledger: " + run)


def summarize(folder):
    receipt = json.loads((folder / "audit-receipt.json").read_bytes())
    cuts = [json.loads(gzip.decompress(p.read_bytes())) for p in folder.glob("cut-*.json.gz")]
    by_token = {d["identity"]["token"]: d for d in cuts}
    assert cuts and len(by_token) == len(cuts) == receipt["presentedCuts"]
    run = cuts[0]["identity"]["run"]
    ledger, records = ledger_for(run)
    releases = [r for r in records if r["kind"] == "RELEASE_RECEIPT"]
    decisions = {r["token"]: r for r in records if r["kind"] in ("DECISION", "ACKNOWLEDGED")}
    assert len(releases) == len(cuts)
    reviewed = 0
    reconciled = []
    for release in releases:
        token = release["token"]
        cut, decision = by_token[token], decisions[token]
        identity, details = cut["identity"], decision["details"]
        for key in ("token", "run", "session", "generation", "revision", "sequence", "policyHash", "executionScope"):
            assert release[key] == decision[key] == identity[key], (token, key)
        assert release["details"]["resultHash"] == details["resultHash"]
        assert release["details"]["commandId"] == details["commandId"]
        assert details["checks"] == cut["checks"]
        native = cut["native"]["result"]
        assert details["acceptedUseCutHash"] == native["stateHash"]
        assert details["resultHash"] == sha(canonical({"version": "APPLICABLE_NATIVE_REQUIRED_INVENTORY/1.0",
                                                      "nativeResultHash": native["resultHash"], "checks": cut["checks"]}))
        barrier = cut["barrier"]
        assert barrier["admissionClosed"] and barrier["clockFrozen"]
        assert not barrier["active"] and barrier["pending"] == 0
        assert all("WithinDeadline" not in k for k in cut["checks"])
        applicable = [v for v in cut["checks"].values() if v["applicability"] == "APPLICABLE"]
        manual = release["details"]["reviewOverride"]
        if manual:
            assert details["action"] == "ACKNOWLEDGE_AND_CONTINUE"
            assert any(v["truth"] == "FAIL" for v in applicable)
            assert all(v["truth"] in ("PASS", "FAIL") for v in applicable)
            reviewed += 1
        else:
            assert details["action"] == "APPROVE" and all(v["truth"] == "PASS" for v in applicable)
        reconciled.append({"sequence": release["sequence"], "checkpoint": release["checkpointId"], "token": token,
                           "reviewedFailure": manual, "nativeResultHash": native["resultHash"], "resultHash": details["resultHash"],
                           "acceptedUseCutHash": details["acceptedUseCutHash"]})
    assert reviewed == receipt["reviewedFailedCuts"]
    source_failures = {}
    for path in folder.glob("hold-*.json.gz"):
        hold = json.loads(gzip.decompress(path.read_bytes()))
        for row in hold["retainedNativeNonPass"]:
            if row["kind"] == "TASK_POST" and row["verdict"] == "FAIL":
                witness = row["witness"]
                assert witness["observationQuality"] == "COMPLETE_FOR_EXPRESSION"
                source_failures[row["invocation"]] = {k: row[k] for k in ("id", "invocation", "expression", "backendValue", "verdict", "stateVersion")}
                source_failures[row["invocation"]]["phase"] = witness["phase"]
                source_failures[row["invocation"]]["player"] = witness["player"]
    final = json.loads(gzip.decompress((folder / "final-native.json.gz").read_bytes()))
    contexts = final["result"]["semantic"]["observation"]["originalEngine"]["contexts"]
    roots = [{"scope": c["scopeView"]["bindings"]["scheme"],
              "roots": [{"sourceElement": v["sourceElement"], "aggregateStatus": v["aggregateStatus"], "quality": v["quality"],
                         "executionQuality": v.get("executionQuality"), "historicalExecutionCertificates": len(v.get("executionEvidence", {}))}
                        for v in c["occurrences"].values() if v.get("sourceElement") == "O_root"]} for c in contexts]
    producer_logs = {}
    for prepared in (PLUGIN / "target/owned-runtime").glob("*/prepared.json"):
        if json.loads(prepared.read_bytes()).get("runId") != run:
            continue
        stderr = prepared.parent / "producer.stderr.log"
        producer_logs = {"stderrPath": str(stderr), "stderrSha256": sha(stderr.read_bytes()),
                         "sourceFailureLines": [line for line in stderr.read_text(encoding="utf-8", errors="replace").splitlines()
                                                if "code(.fail)" in line or "fail while bidding" in line or "didn't bid on time" in line]}
        assert any("code(.fail)" in line for line in producer_logs["sourceFailureLines"]) or not source_failures
        break
    assert producer_logs
    assert receipt["sourceUnchanged"] and not receipt["unavailable"] and len(receipt["settledScopes"]) == 2
    return {"directory": str(folder.relative_to(HERE)), "producerRun": run, "sourceUnchanged": True,
            "presentedCuts": len(cuts), "automaticReleaseReceipts": len(releases) - reviewed,
            "reviewedFailureReleaseReceipts": reviewed, "settledScopes": sorted(receipt["settledScopes"]),
            "applicableUnavailable": receipt["unavailable"], "sourceFailures": list(source_failures.values()),
            "producerLogs": producer_logs, "sourceFileHashes": receipt.get("sourceHashes", {}),
            "barriersClosedDrainedClockFrozen": True, "exactIdentityChecksAndHashesReconciled": True,
            "sourceLedger": {"path": str(ledger), "sha256": sha(ledger.read_bytes()),
                             "kinds": dict(collections.Counter(r["kind"] for r in records))},
            "shutdownHolds": [{"checkpoint": r.get("checkpointId"), "diagnostic": r["details"].get("diagnostic")}
                              for r in records if r["kind"] == "HOLD_UNVERIFIED"],
            "finalCoverage": final["result"]["coverage"], "finalOriginalRoots": roots,
            "finalInvariantOutcomes": final["result"]["outcomes"],
            "requirementInventory": final["result"]["semantic"]["observation"]["requirementInventory"],
            "releases": reconciled,
            "localRawEvidence": {p.name: sha(p.read_bytes()) for p in sorted(folder.glob("*.gz"))}}


if __name__ == "__main__":
    runs = [summarize(p.parent) for p in sorted(HERE.glob("natural-*/audit-receipt.json"))]
    assert runs
    focused_log = (HERE / "focused-build.log").read_text(encoding="utf-8", errors="replace")
    test_rows = re.findall(r"Tests run: (\d+), Failures: (\d+), Errors: (\d+), Skipped: (\d+), Time elapsed: .*? -- in (\S+)", focused_log)
    counts = dict(zip(("tests", "failures", "errors", "skipped"), [sum(int(row[i]) for row in test_rows) for i in range(4)]))
    assert "BUILD SUCCESS" in focused_log and counts["tests"] > 0 and not any(counts[k] for k in ("failures", "errors", "skipped"))
    result = {"method": "PERSISTED_NATIVE_CUT_TO_PRODUCER_LEDGER_RECONCILIATION", "runs": runs,
              "focusedTests": {**counts, "classes": [row[4] for row in test_rows], "logSha256": sha((HERE / "focused-build.log").read_bytes())},
              "noProductionLogicChanges": True, "deadlineAssertionActive": False, "manualDesktopAcceptance": False}
    (HERE / "summary.json").write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8", newline="\n")
    print("NATURAL_AUCTION_SOURCE_RECEIPTS_RECONCILED", len(runs))
