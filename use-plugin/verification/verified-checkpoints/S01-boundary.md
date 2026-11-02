# S01 boundary guarantee and feasibility gate

2026-10-31. Target: one global MAS cut, with every admitted mutation settled,
all subsequent domain transitions excluded and all domain timer/deadline clocks
held. Snapshot, publisher, authenticated control and durable ingress remain live.
Draining may advance the requested boundary; requested and frozen cuts must have
separate identities. Jason cycle ACKs alone are explicitly insufficient.

This gate tests the linked binaries, not a proposed coordinator. Until S01
passes, **no strict execution scope is supported** and
`-Djacamo.execution.mode=VERIFIED_CHECKPOINTS` must reject before platform
initialization. The default remains `PASSIVE`. Rejection is a capability result,
not a domain verdict. There is no verified-token/review protocol in this stage.

Required executable probes:

- Real Jason all-agent ACK: no next reasoning cycle while paused.
- Already admitted artifact and GroupBoard operation can finish after that ACK.
- An artifact internal operation using `await_time` can mutate after the ACK;
  a workspace action-request rule is not a comprehensive internal-operation gate.
- A new external workspace action can mutate after the ACK.
- An actual NPL obligation can become UNFULFILLED by its unchanged scheduler
  while all Jason cycles remain paused.
- Strict/invalid mode rejects before init/create/start effects, including the
  USE bridge launcher; passive mode remains compatible.

These are isolated component runtime probes. They are not external Auction live
acceptance or manual desktop acceptance. S01 stays unchecked because the positive
whole-platform admission/quiescence/logical-time guarantee is not established.

## Result: feasibility blocked

All 17 distinct selected tests pass: 2 JaCaMo startup tests, 6 component runtime
probes, 2 bridge startup/provenance tests, 5 cycle-ledger tests and 2 existing
control-contract tests. The successful probes prove the insufficiency of the
current pause boundary. They do not prove strict checkpoints.

| Lane | Actual evidence | Missing integration |
|---|---|---|
| Jason | Both admitted real agent incarnations ACK; cycle counters stay unchanged while paused | Common source clock and wait/intention producer barrier |
| CArtAgO admitted work | Operation finishes after all Jason ACKs | Full in-flight/await lifecycle and drain receipts |
| Moise board | Actual GroupBoard player state changes after all Jason ACKs | Board/NPL mutation participant and watermark |
| Internal timer | `await_time` completes and changes an observable property while paused, without another action-request hook | Admission and resume safe points below the action-request rule |
| External action | New action changes the property while paused | Admission for every domain mutation origin |
| NPL deadline | Real obligation becomes UNFULFILLED while Jason cycles remain paused | Freeze actual deadline clock, scheduler and transition execution |

`S01-linked-bytecode.txt` is javap of the exact linked binaries, identified in
`S01-linked-artifacts.json`. In CArtAgO 3.1, `Workspace.doInternalOp` enqueues
directly into `opTodo`, without the workspace `processActionRequest` path.
`Artifact.await_time` and external sessions need kernel lifecycle coverage, too.
In NPL 0.6.1, `StateTransitions` reads `System.currentTimeMillis()` and `TimeTerm`
uses `java.util.Date` for source deadlines. `NPLInterpreter.setStateManager`
exists, but replacing/stopping its updater does not replace those source time
reads, artifact wakeups, or Jason timers. Freezing an observed clock is inadequate.

Required scope extension: pinned source changes/integration in **CArtAgO**
(all mutation admission, await/resume/external sessions, drain), **NPL/Moise**
(shared logical time, deadline and board transition scheduling) and **Jason**
(`.wait`/timer/intention producers), plus deliberately versioned rebuilt artifacts.
No supported whole-platform hook was established from the two current repositories
and these linked public APIs. An arbitrary blocking callback can run under a
platform lock and is not an acceptable barrier implementation. A future S01 rerun
must prove the positive barrier/time behavior before S02–S05/S07.

No coordinator, snapshot-token/review protocol, auto-approval, or Acknowledge &
Continue UI is claimed or supplied by this S01 package. Strict mode rejects all
scopes, without a case-name allowlist. Existing passive semantics are retained.

## Reproduction and artifacts

From `jacamo/` (no clean and no full test run):

```powershell
.\gradlew.bat test --tests CheckpointFeasibilityTest jar publishToMavenLocal --console=plain
```

Existing Gradle `fixTab` invokes Ant eagerly during configuration. This build
added the missing final newline to the external Auction JCM; only that incidental
newline was removed again. Final `git diff -- examples/auction` is empty. No ASL,
XML, deadline, or semantic assertion was changed. Do not mistake that existing
build side effect for case modification needed by the feature.

From `use/`:

```powershell
mvn -B -pl jacamo-bridge-jacamo -am '-Dtest=CheckpointFeasibilityTest,BridgeExecutionControlTest,ReasoningCycleGateTest,RuntimeControlContractTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -o -B -pl use-assembly -am '-DskipTests' package
.\use-plugin\verification\verified-checkpoints\stage-and-check-package.ps1
```

The last script copies the exact resolved runtime JARs into `new/bridge/runtime`,
creates an explicit installed classpath and `new/bin/start_use_s01.bat`, verifies
actual code sources/SHA-256, loads the installed plugin against the host MSystem,
and checks strict rejection using the unchanged external Auction entry point.
The negative startup probe creates no live MAS. A final selected test rerun also
checks provenance against the rebuilt JaCaMo JAR manifest. See
`S01-final-tests.log`, `S01-package-receipt.json`, `S01-packaged-code-sources.log`,
`S01-installed-loader-smoke.log`, and `S01-packaged-strict-rejection.log`.

The post-build staged directory is the delivered runnable artifact; the base
Maven ZIP does not include this script's additional runtime staging. Its generated
classpath uses this machine's absolute installation path; rerun the staging script
after moving/rebuilding the directory. `start_use_s01.bat` starts passive USE.
Strict checkpoint execution is unavailable. Manual desktop acceptance and S07
live Auction verified-checkpoint acceptance were not performed.
