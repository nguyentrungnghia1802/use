# ADR — Runtime Verification Pause/Resume Extension

Status: **ACCEPTED by the user**, 2026-10-05. Implemented supported subset;
executable evidence and final gate status are recorded in `docs/agent/task.md`.

## Uncontested target boundary

JaCaMo remains the execution engine; USE remains the verification mirror.
One checkpoint/snapshot verification pipeline uses the active USE Session
MSystem/MSystemState. Import, Object Diagram, Model Browser, formal OCL,
Goal/violation evidence and native USE views must refer to this same active state.
Existing exact projection/trace and transactional local USE rollback are reused.
There is no rollback of JaCaMo execution, kill, automatic repair, forced goal
achievement, belief correction or mission/artifact domain mutation.

Dedicated RuntimeControlService may request only pause/resume for explicit
inspection or explicitly approved HARD OCL false results. All constraints,
including native HARD rules, default REPORT_ONLY. Severity/provenance alone do
not grant control authority; an exact fingerprint-bound policy must approve it.
OCL ERROR/UNDEFINED/SKIPPED are separate outcomes
and do not automatically pause. Verification FAIL and control failure are
separate diagnostic states. The connector owns no OCL logic and the UI owns no
runtime/control semantics. No Swing button click or console-text parsing is used.

Required order: freeze failing snapshot, retain prior checkpoint, create/publish
violation, request pause, observe authoritative safe-boundary confirmation,
authoritative resync, re-evaluate and classify CONFIRMED / TRANSIENT_NOT_REPRODUCED /
CONFIRMATION_ERROR. Preserve before/after-pause snapshots separately. Never
auto-resume. Explicit Resume must revalidate exact runtime/generation and
synchronization before verification returns LIVE.

## Separate lifecycle contracts

Synchronization: OFFLINE / MODEL_READY / CONNECTING / SYNCING / LIVE / STALE /
ERROR. Runtime control: RUNNING / PAUSE_REQUESTED / PAUSED / RESUME_REQUESTED.
Control capability and control diagnostics are separate; an unavailable API is
not represented as a successfully paused runtime.

- RUNNING → PAUSE_REQUESTED only on explicit request or approved HARD FAIL,
  admitted for the exact current producer/generation and valid synchronization.
- PAUSE_REQUESTED → PAUSED only after the chosen capability's actual confirmation;
  timeout/failure/disconnect must not manufacture PAUSED.
- Repeated pause requests share one in-flight request; additional failures attach
  to the active violation or bounded diagnostic queue without repeated control.
- PAUSED → RESUME_REQUESTED only on explicit request after successful required
  resync; repeated Resume is idempotent while in flight or already running.
- RESUME_REQUESTED → RUNNING requires runtime ACK plus required authoritative
  synchronization; verification remains SYNCING/STALE until successful resync.
- Disconnect never silently resumes a producer. If reconnection targets the same
  exact producer, recover control state from its capability/status evidence;
  a different/new generation invalidates old control assumptions.
- Project replacement detaches old listeners/services and preserves historical
  violations separately; old workspace callbacks cannot mutate the new workspace.
- Pause confirmation/resume failures are reported independently of OCL FAIL.

PAUSED requires a boundary ACK from every requested current Jason agent. Dynamic
agent arrival is admitted while release is disabled; an agent incarnation change
invalidates its previous ACK. Departure is accounted for explicitly, never by
pretending the departed incarnation ACKed. Missing/partial ACK or timeout remains
PAUSE_REQUESTED with a control error. Resume requires authoritative re-enable ACK
and subsequent resync; it cannot return verification to LIVE on a button click.

## Resolved semantic conflict and accepted scope

The pinned Jason 3.3.2 MAS Console Pause/Continue calls setPause on console output
only. Real tests prove silent agents continue executing. It therefore cannot
satisfy the task's safe runtime-pause/resync confirmation requirement while also
following the superseded K6 same-MAS-Console-API instruction.
See `docs/agent/jacamo-pause-resume-audit.md` for source hashes, exact method
anchors, actual execution evidence and the distinct official ExecutionControl
reasoning-cycle mechanism.

Accepted choice: adopt an explicitly capability-gated synchronous Jason
reasoning-cycle control contract, with no claim that in-flight CArtAgO/Moise work
is globally suspended. This changes scheduling and requires exact admitted-agent
membership and completion ACKs. Unsupported scheduler/platform/control ownership
must fail closed. Post-pause resync may show a transient violation because
environment operations can settle. UI must say “Jason agents paused”. The user
explicitly replaced the same-MAS-Console-API requirement with this contract.
Never invoke MASConsoleGUI.setPause or claim atomic whole-platform suspension.

## Protocol impact and migration plan

The original authenticated Bridge was readOnly with handshake, model/runtime
snapshot, subscribe and ACK. Protocol 1.1.0 adds capability-gated authenticated
RuntimeControlContract 1.0.0 carrying exact producer identity, generation, request id,
allowed pause/resume operation, reason and authoritative response/state.
Do not overload existing event meanings, weaken envelope validation or modify
frozen V2 Ecore/Mapping to make the feature run.

Reuse NativeRuntimeProjector/MutationEngine, RuntimeVerificationCoordinator,
ExternalOclConstraintService, snapshot coordinator and recording infrastructure.
Consumers now use typed checkpoint retention, dependency routing, violations and
immutable Goal evidence. The active LEGACY_V2 branch is rejected and obsolete connector/verifier
classes are test-only after replacement tests passed. Explicit offline
replay/reanalysis remains isolated and may not become a second live verifier.

The 2026-10-06 Workbench simplification removes its Verification, Goal, Trace and
Diagnostics views and their exclusive handlers. This does not remove the control,
snapshot, trace, OCL or replay APIs. Workbench exposes no custom result/check UI;
users inspect and check the active model with native USE facilities.

Current baseline remains intact: 205 tests PASS including actual Auction GUI
runtime/same-system/resync/replay. Control API audit and adapter regressions PASS.
The semantic decision is resolved; current implementation and migrated
acceptance evidence are tracked in task.md rather than inferred from this ADR.

### Versioned diagnostic relevance impact

RuntimeConstraintPolicy 1.1.0 adds optional `exactEvidenceTargets` (at most 64
current exact identities, each with a non-empty rationale). The old constructor
and 1.0.0 reader imply an empty selection. Native alias resolution must yield
exactly one current object before configuration; bare names and ambiguities fail
closed. This selection augments immutable violation/trace context only. It neither
creates domain relations/classes nor authorizes extra runtime mutation. The actual
compiled false context is always distinct from additional evidence endpoints.
Policies participate in the recorded timeline and approval fingerprint/hash.

Replay manifest 2.0.0 persists capabilities, policies, explicit boundaries and
native operation checkpoints, with a hashed internal bindings baseline. Policy
retains its 1.0.0 input support; earlier replay schemas are rejected explicitly
after technical attributes were removed. Frozen V2 metamodel/mapping bytes and
their historical manifests are unchanged. Observational performance counters
do not alter event order/control.

VerificationSnapshot 1.2.0 separates the seven synchronization states from the
four RuntimeControlContract states, retained independently in cut evidence.
An authoritative paused cut is synchronized LIVE with control PAUSED; resume
revalidation/resync is SYNCING until it succeeds. Neither synchronization enum
contains PAUSED/PAUSING/RESUMING. Immutable per-object bindingMetadata is separate
from exposed attributes and supplies specification/source identity to violation
tracing from the failing cut. Earlier 1.0.0/1.1.0 snapshot exports are historical
evidence. Hash algorithms are unchanged; projection 3.0.0 records its own native
domain surface and does not claim replay compatibility with older model schemas.
