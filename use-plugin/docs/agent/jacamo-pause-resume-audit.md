# JaCaMo/Jason Pause/Resume API audit

Status: **SCOPE APPROVED**, 2026-10-05; supported reasoning-cycle path implemented
and exercised with real agents, all/partial ACK, membership changes, in-flight
CArtAgO work and actual Auction pause/resume. Current gates are in task.md section 23.
The user selected official ExecutionControl reasoning-cycle gating with per-agent
ACKs and replaced the same-MAS-Console-API requirement. This audit informs
the Phase B architecture decision and Phase I implementation.

## Exact dependency and evidence

The adapter depends on JaCaMo 1.3.1 and actually loads Jason interpreter 3.3.2.
`javap jacamo.infra.JaCaMoLauncher` proves it extends RunLocalMAS and does not
override createPauseButton; the audited Console path is inherited by JaCaMo.
Evidence is the installed binary and its matching sources JAR, not a different
checkout's remembered API. SHA-256:

- `jason-interpreter-3.3.2.jar`:
  `CD79268DDCFA45599284BDA64BD6980DBD86EEC6C862028CC276495AAB49154C`.
- Matching sources JAR:
  `2F4E450D1FB116A9BF2D4AB7EDDB2265B82FA4A303A97A64570C67710E2D1CE0`.

Selected source entries were extracted under `target/runtime-control-api`.
Only control-related symbols were read; no upstream file was edited.

## Actual MAS Console button path

`jason/infra/local/RunLocalMAS.java:462–474`, `createPauseButton()`:
Pause calls `MASConsoleGUI.get().setPause(true)`; Continue calls `setPause(false)`.
It does not call ExecutionControl, local agent sync signals or a global runtime
controller. The production class bytecode was checked with javap.

`jason/runtime/MASConsoleGUI.java:135–152`: `setPause` sets the console flag and
notifies waiting console writers. `append`, lines 157–166, waits on that flag.
`MASConsoleLogHandler.publish`, lines 47–49, calls console.append. Consequently
a thread logging through this handler can block at output; a silent agent or
a different logger handler continues. There is no all-agent pause ACK, no
reasoning-cycle barrier, no in-flight-operation drain and no Moise quiescence ACK.

This is not safe MAS execution pause. The same button path cannot satisfy the
task's `JaCaMo reaches PAUSED → authoritative resync → confirm violation`
guarantee. GUI click simulation would not improve that guarantee and is forbidden.

## Executable counterexample

`jason.infra.local.JasonConsolePauseSemanticsTest` executes the real APIs in child
JVMs. It never invokes JButton.doClick or substitutes a fake console/runtime.

Two official LocalAgArch agents execute a generic silent AgentSpeak loop. Calling
the real `MASConsoleGUI.setPause(true)` leaves both agents executing: the focused
cut moves from **[16,16] to [28,28] completed cycles** while the flag is true.
An actual console.append thread blocks until setPause(false). Evidence:
`jacamo-bridge-jacamo/target/runtime-control-api/console.json`. Cycle values are
measured, not expected fixed counts; assertions require both to advance.

Focused gate `target/runtime-pause-api-focused.log`: **13 PASS**, zero
failures/errors/skips (two new semantic audits, seven launcher lifecycle and four
snapshot coordinator regressions), completed 2026-10-05 10:50:06 +07.
Full adapter/neutral-contract regression also PASS in
`target/runtime-pause-api-adapter-regression.log`: **59 tests**, zero
failures/errors/skips, completed 2026-10-05 10:52:14 +07.

## Distinct official synchronous reasoning path

Jason offers `LocalExecutionControl` and `ExecutionControl` separately. An agent
configured with `Settings.setSync(true)` waits in LocalAgArch.waitSyncSignal at
the cycle boundary. `LocalExecutionControl.informAgToPerformCycle` and
`informAllAgsToPerformCycle` release agent sync signals. `receiveFinishedCycle`
reports reasoning-cycle completion; it is not a CArtAgO/Moise global barrier.

Source anchors: LocalAgArch.java:267–294, 419–479;
LocalExecutionControl.java:27–43, 51–76; ExecutionControl.java:118–146, 175–200.
RunLocalMAS.changeToDebugMode, lines 918–935, installs this controller and changes
agents to synchronous/verbose mode. It is a different button/path and changes
execution scheduling. There is no justification for silently switching a user's
MAS to it while claiming the existing MAS Console Pause semantics.

The second audit instantiates an official LocalExecutionControl with a test-only
ExecutionControl extension that disables automatic next-cycle release. Two
sync local agents remain at [0,0], advance to [1,1] after the first official
signal and [2,2] after the next, then wait again. Evidence:
`jacamo-bridge-jacamo/target/runtime-control-api/sync.json`.
This proves a candidate **synchronous reasoning-cycle control** mechanism only;
it does not prove global CArtAgO/Moise suspension or an asynchronous scheduler
equivalence. ExecutionControl's five-second timeout is not proof every agent has
reached a safe boundary. A production implementation would need exact membership,
per-agent completion ACKs, no timeout-based false PAUSED, dynamic-agent ownership
and unsupported-scheduler capability gating.

## Resolved historical decision and implemented impact

The original task K6 required the same API behind MAS Console Pause/Resume, while target
architecture and I3 require safe runtime pause confirmation. In the actual pinned
runtime these are different semantics. They cannot both be implemented faithfully
using that console API. No production fallback, kill, thread suspension or domain
mutation is acceptable.

Approved revision: explicit, capability-gated **Jason synchronous
reasoning-cycle pause** through official ExecutionControl, documenting that
already-running CArtAgO operations and independent Moise/NPL work may settle while
paused. Preserve the failing snapshot before request; authoritative post-pause
resync can classify the violation transient/not reproduced. Treat unsupported
schedulers, agents without control admission and missing ACKs as control errors,
not PAUSED. This is a different runtime scheduling/control contract and needs
per-agent ACKs before PAUSED and authoritative resync after pause and resume.

Historical alternative considered before approval: keep report-only verification and mark automatic Pause/Resume
unsupported/deferred. That also changes the task's mandatory E2E acceptance and
cannot be chosen silently.

The approved option is implemented with RuntimeControlContract 1.0.0 over the
authenticated Bridge protocol 1.1.0, one RuntimeControlService and the migrated
coordinator/facade/UI consumers. Frozen V2 metamodel/mapping bytes are preserved.
If full environment/organisation quiescence is required instead, source audit
must identify a separate authoritative barrier before claiming PAUSED; the APIs
above do not provide one. No upstream-core patch is justified by this evidence.

The current `BridgeExecutionControlTest` includes a fourth child-JVM scenario:
an actual CArtAgO operation on a GroupBoard with a generated generic OS starts
before pause, then finishes role adoption through the real GroupBoard/NPL path
while both Jason agents remain at their ACKed cycle counts. This executable
counterexample establishes the Moise/OrgBoard limitation; it does not introduce
domain mutation into RuntimeControlService. Evidence:
`jacamo-bridge-jacamo/target/runtime-control-api/execution-organisation.json`.
