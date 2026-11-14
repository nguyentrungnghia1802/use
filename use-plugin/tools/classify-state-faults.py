#!/usr/bin/env python3
"""Compare specification-only changes on the same frozen JaCaMo observation prefixes."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess

ROOT = Path(__file__).resolve().parents[2]
MODULE = ROOT / "use-plugin"
MODELS = ROOT / "use-plugin/src/main/resources/example/auction"


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--campaign", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    campaign, output = args.campaign.resolve(), args.output.resolve()
    frozen = json.loads((campaign / "frozen-inputs.json").read_text())
    for name, expected in frozen.items():
        path = Path(name) if Path(name).is_absolute() else ROOT / name
        if digest(path) != expected:
            raise SystemExit("Recorded checker/source differs from current replay inputs: " + name)
    if output.exists():
        raise SystemExit("Choose a new output directory; prior diagnostic evidence is preserved")
    output.mkdir(parents=True)
    original = (MODELS / "auction.csl").read_text()

    def appended(rule):
        end = original.rindex("}")
        return original[:end] + "\n" + rule + "\n}\n"

    no_focus, count = re.subn(r"    context Auction inv RunningAuctionIsObserved:.*?;\n", "", original, flags=re.S)
    if count != 1:
        raise SystemExit("Cannot isolate the existing focus constraint")
    variants = {
        "baseline": original,
        "without-bid-monotonicity": original,
        "mapping-typo": original,
        "winner-member": appended("""    context Auction inv WinnerIsParticipant:
        self.running or self.winner = 'no_winner' or
        self.cslEntityScope.oclAsType(auctionGroup).participant->exists(r |
            r.player.oclIsKindOf(auction_capabilities) and
            r.player.oclAsType(auction_capabilities).name = self.winner);"""),
        "without-focus": no_focus,
        "service-blacklist": appended("    context Auction inv DenyWrongLiteral: self.running implies self.task <> 'wrong_service';"),
        "always-running": appended("    context Auction inv AlwaysRunning: self.running;"),
        "always-object": appended("    context auctionGroup inv AlwaysHasAuction: self.cslScopedEntities->exists(e | e.oclIsKindOf(Auction));"),
    }
    for name, text in variants.items():
        (output / (name + ".csl")).write_text(text)
    # Freeze the other two specs beside the variants; every replay uses identical copies.
    for name in ("attribute-mapping.xml", "auction.istar"):
        (output / name).write_bytes((MODELS / name).read_bytes())
    istar = (output / "auction.istar").read_text()
    without_bid, count = re.subn(r"        goal BidNeverIncreases : Maintain condition \{\[.*?\]\}\n", "", istar, flags=re.S)
    if count != 1:
        raise SystemExit("Cannot isolate the iStar monotonic-bid condition")
    (output / "without-bid-monotonicity.istar").write_text(without_bid)
    mapping = (output / "attribute-mapping.xml").read_text()
    if mapping.count("property:best_bid") != 1:
        raise SystemExit("Cannot isolate the mapped bid attribute")
    (output / "mapping-typo.xml").write_text(mapping.replace("property:best_bid", "property:best_bdi"))
    cp = os.pathsep.join([str(MODULE / "target/classes")] + (MODULE / "target/runtime-classpath.txt").read_text().strip().split(os.pathsep))
    # expected decision, with positive controls explicitly checking false alarms.
    comparisons = [
        ("baseline", "valid_non_improving_bid", "APPROVE"),
        ("baseline", "valid_service_literal", "APPROVE"),
        ("baseline", "missing_focus", "HOLD_FAILURE"),
        ("without-focus", "missing_focus", "APPROVE"),
        ("baseline", "increasing_best_bid", "HOLD_FAILURE"),
        ("without-bid-monotonicity", "increasing_best_bid", "APPROVE"),
        ("mapping-typo", "valid_non_improving_bid", "HOLD_UNVERIFIED"),
        ("winner-member", "valid_non_improving_bid", "APPROVE"),
        ("winner-member", "valid_service_literal", "APPROVE"),
        ("winner-member", "wrong_winner", "HOLD_FAILURE"),
        ("winner-member", "wrong_eligible_winner", "APPROVE"),
        ("service-blacklist", "wrong_service", "HOLD_FAILURE"),
        ("service-blacklist", "valid_service_literal", "HOLD_FAILURE"),
        ("always-running", "start_keeps_stopped", "HOLD_FAILURE"),
        ("always-running", "valid_non_improving_bid", "HOLD_FAILURE"),
        ("always-object", "missing_artifact", "HOLD_FAILURE"),
        ("always-object", "valid_non_improving_bid", "HOLD_FAILURE"),
    ]
    evidence = []
    for variant, case, expected in comparisons:
        trace = campaign / case / "run/consumer-history.jsonl"
        result_file = output / (variant + "--" + case + ".json")
        mapping_file = "mapping-typo.xml" if variant == "mapping-typo" else "attribute-mapping.xml"
        istar_file = "without-bid-monotonicity.istar" if variant == "without-bid-monotonicity" else "auction.istar"
        command = ["java", "-cp", cp, "org.tzi.use.plugins.jacamo.bridge.StateReplayMain",
                   str(output / mapping_file), str(output / (variant + ".csl")),
                   str(output / istar_file), str(trace), str(result_file)]
        run = subprocess.run(command, cwd=ROOT, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=30)
        (output / (result_file.stem + ".log")).write_text(run.stdout)
        if run.returncode:
            raise SystemExit("Replay failed: " + result_file.stem + "\n" + run.stdout[-3000:])
        result = json.loads(result_file.read_text())
        factor = "iStar condition" if variant == "without-bid-monotonicity" else "mapping source field" if variant == "mapping-typo" else "CSL invariant" if variant != "baseline" else "baseline control"
        row = {"variant": variant, "case": case, "changedFactor": factor, "expectedDiagnosticDecision": expected,
               "observedDecision": result["decision"], "firstHoldSequence": result["firstHoldSequence"],
               "evaluatedCheckpoints": result["evaluatedCheckpoints"], "traceSha256": digest(trace),
               "diagnosticExpectationMet": result["decision"] == expected}
        evidence.append(row)
        print(json.dumps(row), flush=True)
    # Confirm that the source traces and the original code/specs stayed untouched.
    unchanged = all(digest(Path(name) if Path(name).is_absolute() else ROOT / name) == value for name, value in frozen.items())
    summary = {"scope": "Same recorded-state prefixes; one specification factor (CSL, iStar, or mapping) changed at a time. No prediction beyond a changed HOLD decision.",
               "originalFrozenInputsUnchanged": unchanged, "comparisons": evidence,
               "diagnosticControlsPassed": unchanged and all(r["diagnosticExpectationMet"] for r in evidence),
               "interpretation": "An expected HOLD on a valid control demonstrates a rejected repair, not checker correctness."}
    (output / "assessment.json").write_text(json.dumps(summary, indent=2) + "\n")
    (output / "specification-hashes.json").write_text(json.dumps({p.name: digest(p) for p in output.iterdir() if p.suffix in (".csl", ".xml", ".istar")}, indent=2) + "\n")
    return 0 if summary["diagnosticControlsPassed"] else 2


if __name__ == "__main__":
    raise SystemExit(main())
