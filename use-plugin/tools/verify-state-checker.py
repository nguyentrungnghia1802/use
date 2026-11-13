#!/usr/bin/env python3
"""Run real JaCaMo mutants against a frozen CSL/iStar checker and independent source witnesses."""
import argparse
import collections
import difflib
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import signal
import subprocess
import time

ROOT = Path(__file__).resolve().parents[2]
EXAMPLE = ROOT / "use-plugin/examples/auction"
MODELS = EXAMPLE / "state-only"
MODULE = ROOT / "use-plugin/modules/state-verification"
ASL = Path("src/agt/auction_capabilities.asl")
ARTIFACT = Path("src/env/auction_env/AuctionArtifact.java")
CASES = [
    ("original", "baseline", "", ""),
    ("valid_non_improving_bid", "valid", "", ""),
    ("valid_service_literal", "valid", "", ""),
    ("negative_bid", "fault", "Auction::ValidBid", "after_bid_1"),
    ("increasing_best_bid", "fault", "BidNeverIncreases", "after_bid_2"),
    ("missing_focus", "fault", "Auction::RunningAuctionIsObserved", "after_start"),
    ("start_keeps_stopped", "fault", "", "after_start"),
    ("missing_artifact", "fault", "", "after_start"),
    ("wrong_service", "fault", "", "after_start"),
    ("wrong_winner", "fault", "", ""),
    ("wrong_eligible_winner", "fault", "", ""),
    ("missing_property", "coverage", "ATTRIBUTE_VALUE_MISSING", "after_start"),
]

PROBE = r'''package verification_oracle;
import java.nio.file.*;
import jason.asSemantics.*;
import jason.asSyntax.*;
public class mark extends DefaultInternalAction {
    @Override public Object execute(TransitionSystem ts, Unifier u, Term[] args) throws Exception {
        String tag=((StringTerm)args[0]).getString();
        String row=tag+"\t"+ts.getAgArch().getAgName()+"\t"+System.currentTimeMillis()+"\n";
        Files.writeString(Path.of("verification-next-plan.txt"),row,StandardOpenOption.CREATE,StandardOpenOption.APPEND);
        System.out.println("VERIFICATION_NEXT_PLAN "+row.trim());
        return true;
    }
}
'''


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def replace_one(text, old, new):
    if text.count(old) != 1:
        raise ValueError(f"Mutation anchor is not unique: {old!r}")
    return text.replace(old, new, 1)


def prepare(case, project):
    project.mkdir(parents=True)
    shutil.copytree(EXAMPLE / "src", project / "src")
    shutil.copy2(EXAMPLE / "auction.jcm", project / "auction.jcm")
    if case == "original":
        return
    # A valid two-agent cohort removes races between competing writers; each auction still has its own graph.
    jcm = (project / "auction.jcm").read_text()
    for name in (("francois", "giacomo") if case == "wrong_eligible_winner" else ("maria", "francois", "giacomo")):
        jcm = re.sub(r"^\s*agent " + name + r"\s*:.*\n", "\n", jcm, flags=re.M)
        jcm = re.sub(r"^\s*" + name + r"\s+participant\s*\n", "\n", jcm, flags=re.M)
    if case == "valid_service_literal":
        # Moise parses serialized goal arguments as Jason terms: uppercase bare text is a variable.
        jcm, count = re.subn(r'"flight_ticket\([^"\n]+\)"', '"wrong_service"', jcm)
        if count != 2:
            raise ValueError("Cannot set legitimate requested Service control")
    (project / "auction.jcm").write_text(jcm)

    asl = (project / ASL).read_text()
    focus = "" if case == "missing_focus" else "focus(ArtId);"
    body = f'''?goalArgument(Sch,auction,"Id",Id);
      ?goalArgument(Sch,auction,"Service",S);
      makeArtifact(Id,"auction_env.AuctionArtifact",[],ArtId);
      {focus}
      start(S)[artifact_id(ArtId)]'''
    if case == "missing_artifact":
        body = 'verification_oracle.mark("fault_missing_artifact")'
    start = f'''+!start[scheme(Sch)]
   <- !verification_start(Sch); !verification_after_start.
+!verification_start(Sch) <- {body}.
+!verification_after_start <- verification_oracle.mark("after_start").

'''
    asl, n = re.subn(r"\+!start\[scheme\(Sch\)\].*?(?=\n\+!decide)", lambda _: start, asl, flags=re.S)
    if n != 1:
        raise ValueError("Cannot isolate original start plan")
    first = "-5" if case == "negative_bid" else "10"
    bid = f'''+!bid[scheme(Sch)]
   <- ?goalArgument(Sch,auction,"Id",Id);
      lookupArtifact(Id,AId); focus(AId);
      !verification_one_bid(AId,{first}); !verification_after_bid_1;
      !verification_one_bid(AId,20); !verification_after_bid_2.
+!verification_one_bid(AId,V) <- bid(V)[artifact_id(AId)].
+!verification_after_bid_1 <- verification_oracle.mark("after_bid_1").
+!verification_after_bid_2 <- verification_oracle.mark("after_bid_2").

'''
    asl, n = re.subn(r"\+!bid\[scheme\(Sch\)\].*?(?=\n\+winner\(W\))", lambda _: bid, asl, flags=re.S)
    if n != 1:
        raise ValueError("Cannot isolate original bid plan")
    (project / ASL).write_text(asl)
    probe = project / "src/env/verification_oracle/mark.java"
    probe.parent.mkdir(parents=True)
    probe.write_text(PROBE)

    java = (project / ARTIFACT).read_text()
    java = replace_one(java, 'getObsProperty("task").updateValue(task);',
        'getObsProperty("task").updateValue(task);\n'
        '        System.out.println("VERIFICATION_ORACLE START running="+getObsProperty("running").booleanValue()+" expected="+task+" actual="+getObsProperty("task").stringValue());')
    java = replace_one(java, 'getObsProperty("winner").updateValue(new Atom(currentWinner));',
        'getObsProperty("winner").updateValue(new Atom(currentWinner));\n'
        '        System.out.println("VERIFICATION_ORACLE STOP expected="+currentWinner+" actual="+getObsProperty("winner").getValue());')
    java = replace_one(java, 'ObsProperty opCurrentValue  = getObsProperty("best_bid");',
        'ObsProperty opCurrentValue  = getObsProperty("best_bid");\n'
        '        double verificationPrevious=opCurrentValue.doubleValue();')
    java = replace_one(java, '        System.out.println("Received bid "+bidValue',
        '        System.out.println("VERIFICATION_ORACLE BID previous="+verificationPrevious+" input="+bidValue+" actual="+opCurrentValue.doubleValue());\n'
        '        System.out.println("Received bid "+bidValue')
    if case == "increasing_best_bid":
        java = replace_one(java, "if (bidValue < opCurrentValue.doubleValue())", "if (true)")
    elif case == "start_keeps_stopped":
        java = replace_one(java, 'getObsProperty("running").updateValue(true);', 'getObsProperty("running").updateValue(false);')
    elif case == "wrong_service":
        java = replace_one(java, 'getObsProperty("task").updateValue(task);', 'getObsProperty("task").updateValue("wrong_service");')
    elif case == "wrong_winner":
        java = replace_one(java, 'getObsProperty("winner").updateValue(new Atom(currentWinner));', 'getObsProperty("winner").updateValue(new Atom("ghost"));')
    elif case == "wrong_eligible_winner":
        java = replace_one(java, 'getObsProperty("winner").updateValue(new Atom(currentWinner));', 'getObsProperty("winner").updateValue(new Atom(currentWinner.equals("alice") ? "maria" : "alice"));')
    elif case == "missing_property":
        java, n = re.subn(r'^\s*defineObsProperty\("best_bid"[^\n]*\n', '\n', java, flags=re.M)
        if n != 1:
            raise ValueError("Cannot remove best_bid declaration")
    (project / ARTIFACT).write_text(java)
    diffs = []
    for name in (Path("auction.jcm"), ASL, ARTIFACT):
        diffs.extend(difflib.unified_diff((EXAMPLE / name).read_text().splitlines(True),
            (project / name).read_text().splitlines(True), fromfile="original/"+str(name), tofile=case+"/"+str(name)))
    (project.parent / "mutation.diff").write_text("".join(diffs))


def records(work):
    history = work / "consumer-history.jsonl"
    return [json.loads(line) for line in history.read_text().splitlines()] if history.exists() else []


def source_oracle(case, project, work, rows):
    log = (work / "producer.log").read_text(errors="replace") if (work / "producer.log").exists() else ""
    bids = [tuple(map(float, x)) for x in re.findall(r"VERIFICATION_ORACLE BID previous=(\S+) input=(\S+) actual=(\S+)", log)]
    starts = re.findall(r"VERIFICATION_ORACLE START running=(true|false) expected=(.*?) actual=(.*)", log)
    stops = re.findall(r"VERIFICATION_ORACLE STOP expected=(\S+) actual=(\S+)", log)
    probes_file = project / "verification-next-plan.txt"
    probe_rows = probes_file.read_text().splitlines() if probes_file.exists() else []
    probes = dict(collections.Counter(row.split("\t")[0] for row in probe_rows))
    snapshots = [r["request"]["snapshot"] for r in rows]
    unfocused = 0
    for s in snapshots:
        auctioneers = {e["player"] for e in s.get("enactments", []) if e["role"] == "auctioneer"}
        unfocused += sum(a["properties"].get("running") is True and not auctioneers.intersection(a.get("observers", [])) for a in s["artifacts"])
    evidence = {
        "instrumented": case != "original",
        "bidCalls": len(bids), "validNonImprovingCalls": sum(v >= p and a == p for p, v, a in bids),
        "incorrectBidUpdates": sum(a != min(p, v) for p, v, a in bids),
        "negativeBidCalls": sum(v < 0 for _, v, _ in bids),
        "startCalls": len(starts), "startReturnedStopped": sum(r == "false" for r, _, _ in starts),
        "wrongServiceCalls": sum(expected != actual for _, expected, actual in starts),
        "stopCalls": len(stops), "wrongWinnerCalls": sum(expected != actual for expected, actual in stops),
        "runningWithoutAuctioneerFocus": unfocused, "nextPlanProbes": probes,
        "maximumArtifactCount": max((len(s["artifacts"]) for s in snapshots), default=0),
        "missingRequiredProperties": sum("best_bid" not in a["properties"] for s in snapshots for a in s["artifacts"]),
    }
    fault = {
        "negative_bid": evidence["negativeBidCalls"] > 0,
        "increasing_best_bid": evidence["incorrectBidUpdates"] > 0,
        "missing_focus": unfocused > 0,
        "start_keeps_stopped": evidence["startReturnedStopped"] > 0,
        "missing_artifact": probes.get("fault_missing_artifact", 0) > 0 and evidence["maximumArtifactCount"] == 0,
        "wrong_service": evidence["wrongServiceCalls"] > 0,
        "wrong_winner": evidence["wrongWinnerCalls"] > 0,
        "wrong_eligible_winner": evidence["wrongWinnerCalls"] > 0,
        "missing_property": evidence["missingRequiredProperties"] > 0,
    }.get(case, False)
    return evidence, fault


def evaluate(case, kind, rule, forbidden, project, work, rows, returncode):
    evidence, fault = source_oracle(case, project, work, rows)
    responses = [r["response"] for r in rows]
    holds = [r for r in rows if r["response"]["decision"] != "APPROVE"]
    violations = sorted({(c["id"], c["kind"]) for r in rows for c in r["response"].get("result", {}).get("checks", []) if c["verdict"] == "FAIL" and c["kind"] == "CSL_INVARIANT"})
    violated_goals = sorted({e["element"] for r in rows for e in r["response"].get("result", {}).get("elements", []) if e["status"] == "VIOLATED"})
    final_elements = responses[-1].get("result", {}).get("elements", []) if responses else []
    completed = sum(e["element"] == "AuctionCompleted" and e["status"] == "FULFILLED" for e in final_elements)
    matched = rule and (any(rule in x[0] for x in violations) or rule in violated_goals)
    continuation = bool(forbidden and evidence["nextPlanProbes"].get(forbidden, 0))
    if not rows or returncode != 0:
        classification = "NO_VERDICT_INFRASTRUCTURE"
    elif kind == "baseline":
        classification = "BASELINE_WITHIN_OBSERVED_SCOPE" if not holds else "BASELINE_REQUIRES_INVESTIGATION"
    elif kind == "valid":
        classification = "NO_FALSE_ALARM" if not holds and completed == 2 and evidence["validNonImprovingCalls"] > 0 and not evidence["incorrectBidUpdates"] and evidence["nextPlanProbes"].get("after_bid_2", 0) else "CONTROL_FAILED"
    elif not fault:
        classification = "FAULT_NOT_ACTIVATED"
    elif kind == "coverage":
        classification = "UNVERIFIED_AND_BLOCKED" if any(r["response"]["decision"] == "HOLD_UNVERIFIED" and rule in r["response"]["diagnostic"] for r in holds) and not continuation else "COVERAGE_GUARD_FAILED"
    elif matched and any(r["response"]["decision"] == "HOLD_FAILURE" for r in holds):
        classification = "DETECTED_AND_BLOCKED" if not continuation else "DETECTED_BUT_CONTINUATION_OBSERVED"
    elif not holds:
        classification = "MISSED_VIOLATION"
    else:
        classification = "HELD_WITHOUT_MATCHING_DIAGNOSIS"
    return {
        "case": case, "groundTruth": kind, "faultActivatedIndependently": fault,
        "classification": classification, "checkpoints": len(rows), "exitCode": returncode,
        "decisions": dict(collections.Counter(r["decision"] for r in responses)),
        "completedAuctions": completed, "failedInvariants": violations, "violatedGoals": violated_goals,
        "firstHoldSequence": holds[0]["request"]["snapshot"]["sequence"] if holds else None,
        "expectedBlockedProbe": forbidden, "continuationProbeObserved": continuation,
        "sourceOracle": evidence,
    }


def assess(output):
    """A detected bad example passes its checker test; an approved bad example fails it."""
    results = json.loads((output / "results.json").read_text())
    frozen = json.loads((output / "frozen-inputs.json").read_text())
    unchanged = all(digest(Path(name) if Path(name).is_absolute() else ROOT / name) == value for name, value in frozen.items())
    checks = []
    for row in results:
        expected = {"valid": "NO_FALSE_ALARM", "fault": "DETECTED_AND_BLOCKED", "coverage": "UNVERIFIED_AND_BLOCKED"}.get(row["groundTruth"])
        checks.append({"case": row["case"], "exampleFaultConfirmed": row["faultActivatedIndependently"] if row["groundTruth"] == "fault" else None,
                       "observed": row["classification"], "checkerTestPassed": row["classification"] == expected if expected else None})
    faults = [r for r in results if r["groundTruth"] == "fault" and r["faultActivatedIndependently"]]
    summary = {"checkerAssessment": "PASS_ON_SELECTED_CASES" if unchanged and all(c["checkerTestPassed"] is not False for c in checks) else "FAIL_KNOWN_COUNTEREXAMPLES",
               "frozenInputsUnchanged": unchanged, "confirmedFaultCases": len(faults),
               "detectedFaultCases": sum(r["classification"] == "DETECTED_AND_BLOCKED" for r in faults),
               "missedFaultCases": sum(r["classification"] == "MISSED_VIOLATION" for r in faults), "cases": checks,
               "interpretation": "Finite fault-injection evidence, not a universal correctness proof. Original baseline has no independent all-properties oracle."}
    (output / "assessment.json").write_text(json.dumps(summary, indent=2)+"\n")
    print(json.dumps({k: summary[k] for k in ("checkerAssessment", "frozenInputsUnchanged", "confirmedFaultCases", "detectedFaultCases", "missedFaultCases")}), flush=True)
    return 0 if summary["checkerAssessment"] == "PASS_ON_SELECTED_CASES" else 2


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path)
    parser.add_argument("--assess", type=Path, help="Assess an existing campaign; exit 2 if a known bad example was missed")
    parser.add_argument("--seconds", type=int, default=8)
    parser.add_argument("--baseline-seconds", type=int, default=18)
    parser.add_argument("--cases", nargs="*", choices=[c[0] for c in CASES])
    parser.add_argument("--csl", type=Path, default=MODELS / "auction.csl")
    parser.add_argument("--istar", type=Path, default=MODELS / "auction.istar")
    parser.add_argument("--mapping", type=Path, default=MODELS / "attribute-mapping.xml")
    args = parser.parse_args()
    if args.assess:
        return assess(args.assess.resolve())
    if args.output is None:
        parser.error("--output is required for a new campaign")
    output = args.output.resolve()
    if output.exists():
        raise SystemExit("Output directory already exists; choose a fresh audit directory")
    output.mkdir(parents=True)
    args.csl,args.istar,args.mapping=args.csl.resolve(),args.istar.resolve(),args.mapping.resolve()
    frozen_paths = [args.csl,args.istar,args.mapping]
    frozen_paths += [EXAMPLE / ASL, EXAMPLE / ARTIFACT, EXAMPLE / "auction.jcm"]
    classpath_entries = [MODULE / "target/classes"] + [Path(p) for p in (MODULE / "target/runtime-classpath.txt").read_text().strip().split(os.pathsep)]
    binaries = [p for p in classpath_entries if p.is_file()]
    binaries += sorted((MODULE / "target/classes").rglob("*.class"))
    frozen = {str(p.relative_to(ROOT)) if p.is_relative_to(ROOT) else str(p): digest(p) for p in frozen_paths + binaries}
    (output / "frozen-inputs.json").write_text(json.dumps(frozen, indent=2)+"\n")
    cp = os.pathsep.join(str(p.resolve()) for p in classpath_entries)
    results = []
    selected = [c for c in CASES if not args.cases or c[0] in args.cases]
    for case, kind, rule, forbidden in selected:
        case_dir = output / case
        project, work = case_dir / "project", case_dir / "run"
        prepare(case, project)
        work.mkdir()
        seconds = args.baseline_seconds if kind == "baseline" else args.seconds
        command = ["java", "-Duse.state.work="+str(work), "-cp", cp, "org.tzi.use.state.StateWorkbenchMain",
                   str(project / "auction.jcm"), str(args.mapping), str(args.csl), str(args.istar), "--headless", "--seconds="+str(seconds)]
        started = time.monotonic()
        with (work / "consumer.log").open("w") as log:
            process = subprocess.Popen(command, cwd=ROOT, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
            try:
                code = process.wait(timeout=seconds+30)
            except subprocess.TimeoutExpired:
                os.killpg(process.pid, signal.SIGTERM)
                try:
                    code = process.wait(timeout=5)
                except subprocess.TimeoutExpired:
                    os.killpg(process.pid, signal.SIGKILL)
                    code = process.wait()
        rows = records(work)
        result = evaluate(case, kind, rule, forbidden, project, work, rows, code)
        result["wallSeconds"] = round(time.monotonic()-started, 3)
        results.append(result)
        (case_dir / "result.json").write_text(json.dumps(result, indent=2)+"\n")
        # Preserve full local logs but remove the short-lived transport credential from the review artifact.
        (work / "bridge-secret.hex").unlink(missing_ok=True)
        (output / "results.json").write_text(json.dumps(results, indent=2)+"\n")
        print(json.dumps({k: result[k] for k in ("case", "classification", "checkpoints", "completedAuctions", "wallSeconds")}), flush=True)
        if kind == "valid" and result["classification"] != "NO_FALSE_ALARM":
            raise SystemExit("Positive control failed; mutant results cannot yet be trusted")
    for p in frozen_paths + binaries:
        key = str(p.relative_to(ROOT)) if p.is_relative_to(ROOT) else str(p)
        if digest(p) != frozen[key]:
            raise SystemExit("Checker or original example changed during audit: "+key)
    print("Frozen checker and original example hashes unchanged.", flush=True)
    return assess(output)


if __name__ == "__main__":
    raise SystemExit(main())
