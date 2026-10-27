"""Read-only closeout checks; does not drive the GUI or alter any verdict."""
import hashlib
import json
from pathlib import Path

root = Path(__file__).resolve().parents[4]
evidence = Path(__file__).resolve().parent
load = lambda name: json.loads((evidence / name).read_text(encoding="utf8"))
records = [json.loads(line) for line in (evidence / "probe-history.jsonl").read_text().splitlines()]
assert records and not any("probeError" in row for row in records)
assert all(w["x"] < 0 for row in records for w in row["windows"] if w.get("showing"))
assert all(row["sameMSystem"] for row in records if "sameMSystem" in row)
first, restarted, imported = map(load, ("run1-before-stop.json", "run2-ready.json", "run3-ready.json"))
assert len({r["producer.pid"] for r in (first, restarted, imported)}) == 3
assert len({r["producer.runId"] for r in (first, restarted, imported)}) == 3
assert len({r["nativeSystem"] for r in (first, restarted, imported)}) == 3
for name in ("run2-ready-result.json", "run3-ready-result.json"):
    result = load(name)
    core = result.get("semantic", {}).get("observation", {}).get("originalEngine", {})
    assert not core.get("contexts", [])
    assert not any(c.get("kind") == "TASK_POST" for c in result.get("semantic", {}).get("constraints", []))
for name in ("disposed1.json", "disposed2.json"):
    row = load(name)
    assert row["facadeClosed"] and row["sessionListeners"] == 1
    assert all(not p.get("displayable", False) and not p.get("timerRunning", False)
               and p.get("cslDetached", True) and p.get("istarDetached", True) for p in row["panels"])
reopened = load("reopened2.json")
assert reopened["facade"] != first["facade"]
assert reopened["sessionListeners"] == 2 and not reopened["workspacePresent"]
assert reopened["producer.state"] == "IDLE" and reopened["producer.pid"] == 0
for name in ("threads-after-dispose.txt", "threads-after-dispose2.txt"):
    assert not any(line.startswith('"') and any(token in line for token in (
        "use-original-engine-", "jacamo-bridge-tcp-subscription", "use-jason-runtime-control", "owned-jacamo-output"))
        for line in (evidence / name).read_text(errors="replace").splitlines())
live = load("run1-evidence.json")
assert live["acceptedTaskPosts"] and "TaskMarking.update" in live["originalCalls"]
assert "('INVARIANT_BODY', 'FAIL')" in live["constraintObservations"]
assert "('INVARIANT_BODY', 'PASS')" in live["constraintObservations"]
assert "Exception" not in (evidence / "host-shell-close.log").read_text()
freeze = json.loads((evidence.parent / "S07-source-freeze-v5.json").read_text())
assert all(hashlib.sha256((root / name).read_bytes()).hexdigest() == digest for name, digest in freeze.items())
print(f"MAIN_WORKFLOW_CLOSEOUT_PASS samples={len(records)} runs=3 disposeCycles=2 sourceFiles={len(freeze)}")
