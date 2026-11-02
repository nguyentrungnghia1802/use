# JaCaMo–USE: verified checkpoints

**Closeout 2026-11-02:** generic support across authored cases; implemented
[`generic-checkpoints-20261102/PLAN.md`](use-plugin/verification/generic-checkpoints-20261102/PLAN.md).
This extends live scope to Hello World/House Building and fixes common admission,
context ownership, UI/state grouping and validation paths. Existing semantic
assertions and fail-closed guarantees remain mandatory. All extension gates now
have evidence; current acceptance and limits are in
[`generic-checkpoints-20261102/REPORT.md`](use-plugin/verification/generic-checkpoints-20261102/REPORT.md).

2026-10-31 · Repositories `use/` and `../jacamo/` · Branch `main`.
Apply `../AGENTS.md`. This supersedes the old performance/case-study plan,
preserved with its evidence in pre-feature commit `fded6f66`.

## Required behavior

Use **Verified checkpoints** as the single main owned-runtime workflow. At a configured source
checkpoint, JaCaMo closes subsequent execution admission, settles admitted work
and publishes a supported frozen cut. USE verifies exactly that cut. Qualified
PASS continues automatically. Failure, unavailable required evidence, ERROR,
timeout or disconnect retain the hold. **Acknowledge & Continue** records review
and releases that checkpoint; it never changes its failure to PASS. Preserve the
existing passive mode, state navigation, execution isolation and failure history.

## Initial source audit (before this feature)

| Current implementation | Consequence |
|---|---|
| JaCaMo `infra/JaCaMoLauncher.startAgs/finish`, `infra/JaCaMoAgArch`, `platform/Cartago`, `platform/Moise` | Agents, artifact operations and organization activity have separate lifecycles. No global verification barrier exists here. |
| USE `BridgeControlledLauncher`, `BridgeLocalExecutionControl`, `ReasoningCycleGate` | Plugin-side controller already admits exact agent incarnations and collects cycle ACKs. Reuse this work; its ACKs do not freeze CArtAgO/Moise. |
| `RuntimeControlContract` | Guarantee is explicitly `JASON_REASONING_CYCLE_BOUNDARY`, with in-flight artifact/organization activity excluded. Preserve that meaning. |
| `JaCaMoBridgePlatform`, `BridgeCheckpointSource` | Emit callback-batch boundaries (up to 32 events/50 ms) and observations; currently do not stop execution. |
| `RuntimeControlService` | Existing consumer-initiated pause/resync confirmation is different from a producer-first frozen checkpoint. |
| `BusinessTimeline` | Milestones compact observed history for display; their numbers are not source stop tokens. |
| `jacamo-bridge-jacamo/pom.xml` | Loads Maven `org.jacamo:jacamo:1.3.1`; editing the sibling source does not automatically replace that JAR. |
| External Auction `auction-os.xml`, `auction_capabilities.asl` | `bid` has a 10-second deadline and plans use `.wait`; verification latency can alter behavior if source time continues. |

The inspected public CArtAgO 3.1 Workspace/Environment APIs provide operation and
rule-engine hooks; no whole-platform pause/resume primitive was found in those
inspected APIs. Jason pause alone cannot establish the requested strict guarantee.

## Boundary, scope and time

**Authorized scope extension (2026-10-31):** the user approved continuing with
minimal source patches in CArtAgO, NPL/Jason and Moise integration. Source overlays,
original binary/source hashes, patch provenance and reproducible builds belong in
`../jacamo/checkpoint-dependencies/`; downloaded upstream checkouts live in
`../checkpoint-deps/`. Keep official Maven coordinates unchanged and publish only
distinct checkpoint versions locally. This authorization supersedes the earlier
two-repository restriction for these hooks; it does not waive S01.

The extended S01 boundary is one owned local MAS. Kernel operation steps, external
artifact sessions, normative transitions and local Jason cycles participate in a
shared admission ledger. A step already running settles or yields at a declared
await boundary before READY. Queued work is distinct from admitted executing work;
drained requested and actual cut identities remain separate. Source timers use one
logical clock, frozen only once the admitted work/notification ledger is empty.
Admission waits precede platform locks; await resumption releases artifact locks
before reacquiring admission. Stop cancels the run, never approves its token.
Unmanaged mutation threads, unsupported schedulers/distributed execution and
uncovered clock producers must fail strict preflight. Positive runtime evidence
is still required before this boundary can be advertised as supported.

1. Declare checkpoint selection in a versioned policy using admitted mapping/source
   identities: Task pre/post, qualified operation completion or business-field
   transition. Link source checkpoint IDs to display milestones. Do not turn every
   callback, trace command or poll into a business milestone.
2. Start with one global barrier per MAS. Executions can share the same physical
   agents; per-Auction views do not imply independent executable schedulers. Retain
   exact execution scope in every request, decision and failure.
3. Close admission before the next transition. Track/drain already admitted work;
   require all relevant agent-incarnation and mutation-lane ACKs. Keep publisher,
   control, snapshot and disk queue workers running. Never wait while holding an
   artifact, source-listener, scheduler or Swing lock.
4. READY requires a coherent cut and source watermark vector. Record requested
   boundary and actual frozen boundary separately: draining may advance the state.
   Never label a later quiescent cut as a freeze of an earlier requested state.
5. Autonomous/internal artifact operations, external events and Moise/NPL timers
   must participate or make strict mode unsupported for that scope. Detect drift,
   invalidate the token and retain the hold rather than approve stale evidence.
6. Strict mode needs source-owned logical time, including actual deadline/timer
   schedulers. Freezing a displayed clock is insufficient. Continuing wall time
   may be a separately declared weaker mode; do not silently use it for strict
   Auction acceptance. Do not patch case deadlines to make a test pass.

**Feasibility gate S01:** prove the actual admission/time hooks first. If a required
hook belongs to external CArtAgO/Moise/NPL and cannot be covered from these two
repositories, identify that missing hook and the scope extension needed. Do not
ship Jason-only pause under the label of exact whole-platform freeze.

## Protocol and release policy

Negotiate a separate versioned capability; keep existing Pause/Resume semantics.

- Request: session/run, generation, model revision, unique checkpoint token and
  sequence, kind, source event/occurrence, execution scope, boundary-policy hash,
  producer snapshot hash, watermark vector, barrier cohort and clock proof, and
  admitted model/input hashes.
- Decision: exact owner/token/source snapshot plus accepted USE cut/state hash,
  verification-result hash, applicable-check inventory, command ID, diagnostic and
  `APPROVE`, `HOLD_FAILURE`, `HOLD_UNVERIFIED` or `ACKNOWLEDGE_AND_CONTINUE`.
  Producer snapshot hashes and USE state hashes describe different representations;
  link both explicitly rather than equating them.
- One active global token, one effective release. Authenticate commands; duplicates
  return the prior outcome, stale/out-of-order/foreign replies cannot advance another
  cut. Generic Resume must not bypass a verification hold.
- Evaluate required checks applicable at this boundary. Future root goals being
  PENDING are legitimate progress; a failed required Task postcondition is not.
  Missing/UNKNOWN/SKIPPED required applicable checks or incomplete barrier proof
  retain the hold. Reuse authored semantics, including accepted bid-decline behavior.
- Timeout, disconnect, UI close and monitor restart never auto-release. Reconnect
  authenticates/reconciles the held token. A drifted or replaced cut needs a new
  valid barrier; an acknowledgement for an old token cannot authorize it.
- Persist request, frozen cut, verdict, failure, manual acknowledgement and release
  receipt. Acknowledgement is an override, not PASS or a domain repair. Keep the
  original Failure Point. Replay validates history without controlling a live MAS.

State flow: `RUNNING → QUIESCING → VERIFYING → RUNNING` on qualified exact-cut
approval; failure goes to `HELD_FAILURE`; error/unknown/disconnect goes to
`HELD_UNVERIFIED`. Held states continue only after explicit exact-token review and
a producer release receipt. Stop remains available and terminates the owned runtime.

## Responsibilities and UI

**JaCaMo:** execution-neutral checkpoint coordinator/SPI, admission/barrier
participants, source clock integration, lifecycle and held-token ledger. No USE,
Swing or case-study dependency in that coordinator.

**USE:** versioned wire contract and official bridge adapter, exact-cut verification
worker, existing native MSystem/Goal/Task/OCL engines, durable history/replay and
review controls. Keep network/waits/preparation off EDT and retain one native writer.

UI: Prepare + Import and Start use verification automatically; show `Running`, `Waiting for verification`,
`Held — violation`, `Held — verification unavailable`, `Continuing`. Show execution,
milestone and reason; put protocol details in Advanced/Audit. Bind **Acknowledge &
Continue** to the active held token, disable while pending, and change status only
from the producer receipt. Historical navigation must not redirect that command.
Retain saved/processed/pending counters and original verdicts. Render the exact
evaluated source cut before sending APPROVE. Disk audit catch-up must not animate
old states over the active source checkpoint.

### UI follow-up after user feedback (2026-10-31)

The earlier S01–S08 evidence proved the opt-in protocol, not that the main UI
selected it by default or rendered each result before release. The user observed
the passive path catching up after the MAS finished and requested one main path.

- [x] U01 — Remove passive/verified selector from the main authored workflow;
  owned Prepare + Import always enables verification. Keep Jason cycle controls
  in Advanced with their original limited guarantee.
- [x] U02 — Require exact-cut UI presentation before approval; presentation error
  holds and retains native truth. Source-token UI cuts bypass audit-prefix loading.
- [x] U03 — Focused protocol/UI/history tests and real desktop external Auction;
  prove displayed-before-release ordering, review via the actual button and UI heartbeat.
- [x] U04 — Rebuild `target/new`, verify installed bytes/code sources, freeze new
  evidence. Preserve the previous package and all prior logs/replay; no commit/push.

Current follow-up evidence: [UI-FOLLOWUP.md](use-plugin/verification/verified-checkpoints/UI-FOLLOWUP.md).
23 focused tests pass, including the real desktop Auction. The run displayed 5
cuts before decisions, with 3 automatic and 2 reviewed release receipts. Actual
desktop controls/tabs were exercised through Computer Use; this is not human
acceptance sign-off. Swing heartbeat p95 66 ms, max 177 ms. Native truth and the
full audit are retained. The default journal quota is 512 MiB; explicit quotas
and quota-loss holds remain enforced. Packaging identity and source/artifact/evidence
freeze passed: `UI-final-final-package-receipt.json` and
`UI-source-artifact-evidence-freeze.json`. Run `use-assembly/target/new/bin/start_use.bat`.

## Implementation and acceptance

### Stability follow-up (2026-11-01)

The user's normal full runs exposed gaps beyond the earlier bounded desktop
handshake test. Treat the previous U/S receipts as historical bounded evidence.

- [x] C01 — Select admitted semantic Task terminal/source-domain boundaries;
  observation-only artifact construction must not create an initialization hold.
  Required unknown/error still holds at an applicable selected boundary.
- [x] C02 — Stable execution identities and separate presentation timelines/cursors;
  switching execution or CSL/iStar tabs cannot regroup/renumber the same history.
  A cut missing another execution cannot erase its retained qualified state.
- [x] C03 — Repeated exact-token desktop acknowledgement; ensure pending state
  clears and the next failed checkpoint's button is enabled independently.
- [x] C04 — Read an evicted invocation prefix from checked persisted evidence;
  present final settled native Task/Goal results after the last source release.
  Trace Execution 2's INCOMPLETE to its actual missing/stale evidence, never force PASS.
- [x] C05 — Focused regressions and unchanged full Auction completion for both
  executions, tab/scope navigation and two distinct failing desktop reviews;
  build/identify final `target/new`, retain existing user's run/evidence, no commit/push.

Initial diagnosis: changing execution rebuilt checkpoint history through compact
BusinessTimeline; programmatic diagram scope synchronization could invoke the
selection listener again; late unrelated/unbound cuts replaced selected state;
final engine completions were not presented after source checkpoint mode took
over. Current user logs and read-only thread dumps are retained under
`use-plugin/verification/checkpoint-stability`. Their earlier complete native
recording contains two FULFILLED roots even though the UI showed INCOMPLETE.
Another real initialization hold was caused by using observation requirements
(`makeArtifact`) as milestones and by treating RAM-tail eviction as evidence loss.

Final stability evidence: [REPORT.md](use-plugin/verification/checkpoint-stability/REPORT.md).
`focused-live-2.log` passes the full unchanged Auction: two native FULFILLED roots,
both UI executions COMPLETE_OBSERVED, 8 automatic releases and two distinct
failed tokens reviewed with the same real desktop button. Ten cuts were displayed
before their decisions. Separate source/Entity histories and tab navigation pass;
the focused regression retains 13 states and its selected cursor on scope changes.
Swing heartbeat p95 120 ms, max 746 ms for this full run. `final-package.log` passes
35 focused tests and builds the current `target/new`; actual loaded code sources,
installed byte parity and `final-freeze.json` pass. The previous user's complete
installation/work/history is retained at `target/new-before-stability-20261101`.
Overall source coverage retains its bounded INCOMPLETE label where appropriate;
native requirements/results are not rewritten. No full clean verify or commit/push.

### Milestone isolation follow-up (2026-11-01)

- [x] M01 — Check the reported disk journal and freeze exact native cuts; distinguish
  unchanged diagnostics from actual scoped Entity/Task changes. The 1,891-entry
  chain and native result hashes are verified; no cross-execution Entity copying
  is found. See `use-plugin/verification/milestone-isolation/REPORT.md`.
- [x] M02 — Reproduce before fix and group only presentation-equivalent scoped
  cuts; retain the full audit and exact latest representative. Final 42 focused
  tests pass, including the authentic 8-cut / 5-milestone recording, proof ordering,
  retained Task failure and scope/audit navigation.
- [x] M03 — Run unchanged external Auction with verified barriers, receipt ordering
  and independent execution/tab/audit navigation. An observation-mode harness
  baseline is retained separately and does not satisfy this gate. The explicit
  verified run `live-1793520132787` proves eight DISPLAYED-before-APPROVE releases,
  both native roots/UI executions complete, and stable tab/audit navigation.
- [x] M04 — Preserve the previous installed user run, package `target/new`, and
  check installed byte parity and actual loaded JaCaMo identity. The previous tree
  is `target/new-before-milestone-isolation-20261101`; the journal hash is unchanged.
  `milestone-final-package-receipt.json` proves the current plugin/bridge/GUI bytes,
  loaded patched JaCaMo SHA-256 `f5a31568fbc2487322d985e00ab6cecac987f2edc296bf14cd155c56c217275e`
  and installed-loader smoke. No commit/push or full clean verify.

- [x] S00 — Push pre-feature checkpoint; inspect both source paths; create workspace
  AGENTS and this plan. USE `fded6f66` pushed; JaCaMo remote already current.
- [x] S01 — Prove admission/quiescence/time hooks and supported scopes; negative
  tests reject unsupported autonomous/timed components before strict-mode start.
  Extended source-hook evidence: `S01-extended-runtime/positive-proof.txt`,
  `S01-final-probes.log`, `S01-extended-admission-tests.log` and
  `S01-extended-profile-tests.log`. Scope is the owned local source-admitted MAS;
  raw threads/clocks, unsupported timer actions/platforms and remote execution
  are rejected. This checks S01 only, not the end-to-end feature or live Auction.
- [x] S02 — Implement JaCaMo coordinator/SPI, participants, clocks and latched hold;
  add authenticated versioned protocol, exact tokens and idempotent release.
- [x] S03 — Connect USE ingestion/verification decisions to exact cuts; preserve
  native truth, identity, prefix ordering, durable queues and history/replay.
- [x] S04 — Add mode/status/review UI; preserve per-execution failures and navigation.
- [x] S05 — Test blocked next transition, in-flight drain, shared-agent executions,
  clock behavior, required unknown/error, stale/duplicate replies, timeout,
  disconnect/reconnect, UI/monitor close, Stop, new epochs and listener cleanup.
- [x] S06 — Build the modified JaCaMo artifact and wire it into the USE producer
  classpath/package deliberately. Verify actual code-source paths and SHA-256;
  do not silently replace official version bytes or load an old cached JAR.
- [x] S07 — Unchanged external Auction live acceptance: repeated hold/verify/release,
  genuine failing required check held until review, retained failure, both executions,
  correct timeout semantics, no next admitted domain transition while held, replay.
- [x] S08 — Deliver one runnable `use-assembly/target/new`, final source/JAR/evidence
  freeze and an honest support report. Separate live MAS/offscreen Swing from fresh
  manual desktop acceptance. No full clean verify or unrelated live case studies.

DoD: S01–S08 proven by executable evidence; actual source hold and verified cut
agree; no unacknowledged release/lost admitted event, forced verdict or case-specific
production logic; original failures remain; final package loads the intended JaCaMo
implementation. Existing passive queue/replay evidence does not prove this feature.

**Current status (2026-10-31): S01–S08 proven for the supported owned local source-admitted profile.**
Source patches, identity checks and executable admission/time/lifecycle probes are
in `../jacamo/checkpoint-dependencies/`. The initial blocked S01 findings below
remain the baseline for the original unmodified dependencies, not the current
patched source-hook capability. Gate-by-gate evidence, scope and limitations are in
[`FINAL-SUPPORT.md`](use-plugin/verification/verified-checkpoints/FINAL-SUPPORT.md).
The successful live run has 3 automatic release receipts, 11 reviewed release
receipts and 6 genuine native failed cuts; original Auction sources are unchanged.
Actual installed JaCaMo `1.3.1-checkpoint-SNAPSHOT` has SHA-256
`f5a31568fbc2487322d985e00ab6cecac987f2edc296bf14cd155c56c217275e`.
Run `use-assembly/target/new/bin/start_use.bat`. Live MAS, offscreen UI and installed
loader checks passed; fresh manual desktop acceptance has not been performed.
Disconnect replay retains its real coverage gap. No commit/push or full clean verify.

**Initial S01 baseline (before authorized dependency patches):**
Executed probes prove Jason cycle ACKs while CArtAgO in-flight work, GroupBoard
mutation, internal `await_time`/new external actions and actual NPL deadline expiry
can still change domain state. Strict startup now rejects before platform
initialization; the old Jason Pause/Resume guarantee is unchanged. At that stage
S01 remained unchecked because whole-platform admission/quiescence/logical time
was unproven; S02–S05/S07 had not been implemented over the failed mandatory gate.

Evidence and the required dependency-scope extension are in
[`S01-boundary.md`](use-plugin/verification/verified-checkpoints/S01-boundary.md).
17 distinct focused tests passed (2 JaCaMo startup, 6 real component runtime,
2 bridge feasibility/provenance, 5 cycle ledger, 2 existing control contract).
This includes negative feasibility evidence, not strict-mode acceptance.

Historical S06/S08 partial packaging evidence: JaCaMo was built/published under the
distinct `1.3.1-checkpoint-s01-SNAPSHOT` coordinates; the runnable S01/passive
installation was then `use-assembly/target/new`. Packaged code-source/SHA-256, installed
plugin loader and strict startup rejection pass. Neither S06 nor S08 is marked
complete by that historical evidence alone. That stage had no live Auction
verified-checkpoint acceptance. The old runnable tree is now preserved at
`use-assembly/target/new-before-verified-20261031-193121`; the current `target/new`
is the final supported checkpoint build identified above.
