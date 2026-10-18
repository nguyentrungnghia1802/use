# Frozen architecture: USE4GOAL_OBSERVED_V1

This document records the Phase 0 decisions for the source migration in
`implementation-plan.md`. It does not enable a production engine/profile. The
machine-readable companion is `use4goal-observed-v1.json`. This is the historical
architecture freeze, not an installation guide. The implemented opt-in workflow
and release paths are documented in [use4goal-run.md](use4goal-run.md); the default
native profile remains selected unless the user explicitly chooses the original
engine and an authored mapping specification.

## Authority and deployment

The producer is the existing external JaCaMo/Jason/CArtAgO/MOISE/NPL process.
Authenticated TCP carries the existing neutral bridge contract. The consumer is
USE 7.5. Mapping belongs to the JaCaMo plugin and depends on the bridge contract
and original engine models. The engine depends on neither bridge/JaCaMo nor the
plugin or GUI. USE core is provided by the host. Only one active Session MSystem
is authoritative; the engine reads pinned states of that system and owns only
semantic history. A pending import is not a second active runtime.

The canonical definitions for the new profile are original CslModel and GoalModel.
Old NATIVE_CURRENT/CSL_OBSERVER profiles and replay retain their existing semantics.
The original parser/model/validator/marking source is preserved by file hash.
Only the contextual translator needs the separately inventoried P-CTX-01 API seam;
its dependency/instantiation algorithms are not replaced.

## Interpretation policy

* Agent declarations identify players/observers, not CSL Entities. Beliefs are
  mental observations. Active Jason goals do not certify iStar satisfaction.
* Artifact views, OCL conditions and intentional abstractions are explicit authored
  mappings. Missing abstractions are diagnostics rather than invented requirements.
* A CSL Role subtype is the same role instance, with inherited attributes. OrgCtx
  nesting owns declarations, not runtime populations. Abstract source roles need
  an explicit abstraction decision because original CSL Roles are concrete.
* Only known, typed observations populate native values. Missing required fields
  make coverage incomplete; authored defaults are not fabricated runtime facts.
  Immutable-attribute changes preserve observed facts and produce a separate
  policy violation. Opaque runtime types without exact codecs are unsupported.
* Roots have explicit authored demand/episode bindings. Descendant demand uses
  original GoalActivationGraph. Unimplemented activation syntax/forall/pick or
  discarded grammar features are rejected before the original visitor.
* Sequence and parallel retain source execution descriptors. Selected outcome
  refinements are explicit abstractions, not execution-order equivalence.
  Norm lifecycle remains a separate observer, not an implicit OCL invariant.
* Known Boolean inputs alone reach original GoalMarking/TaskMarking. Unknown,
  partial, gap, undefined and errors never become Boolean false. Resync cannot
  certify MAINTAIN/SUSTAIN continuity through a lost interval.
* A Task requiring an invocation postcondition gets no update until its complete
  known tuple exists. PRE-only is invocation-in-progress, not post=false.

## Intentional expected differences

Original NONE false is PENDING; local TemporalGoalMonitor NONE false is VIOLATED.
Original ACHIEVE latches a witnessed truth within its activation episode. Original
TaskMarking accumulates preSeen/postSeen and cannot be VIOLATED. Invocation FAIL
belongs to NativeOperationCheckpoints, independently of historical Task marking.
Symbolic bounded NONE/Task semantics are not an oracle for live recurrence.
Propagation results never overwrite the leaf recurrence's history fields.

The isolated original-source oracle also observes two distinct requester
occurrences for a dependency: a standalone requester bound only to its owner,
and the requester bound to both sides of the social dependency. Fulfillment of
the latter does not fulfill the former. Occurrence identity therefore includes
the full structured binding stack, not merely source element/player/context.
The original translator contains first-instance fallback when a required owner
is missing from a stack. The new profile must reject ambiguous/unproven fallback
through admission; it must not silently broadcast one occurrence's status.

## Ownership and error boundary

Owners are keyed by producer run/generation/source revision, mapping/program/engine
hashes, plus occurrence player/context/incarnation/episode. New producer runs reset
history; reconnect is not a new producer identity. Monitor Stop closes observation
and semantic resources while retaining an offline final native system. It neither
stops an unowned producer nor calls JaCaMo main/System.exit in USE. Producer restart
tests use process handles owned by the test/launcher. Existing control actions are
STATUS/PAUSE/RESUME; Jason pause is not atomic suspension of other platforms.

Native mutation failures restore native/index/protocol/operation/semantic staged
images. Known violations remain observed facts. Semantic evaluation failures after
native acceptance preserve the actual native state, discard the new semantic step,
and explicitly taint semantic continuity. Postcommit persistence failures degrade
coverage/replay; they do not pretend to roll back the external runtime.

## Bounds and responsiveness

Preserve current production defaults: 16 MiB frames, 1,024 consumer bootstrap events,
8,192 producer/ledger events, 64 MiB journal, 512 journal-tail entries, 8 retained
native cuts/16 MiB serialized history, and 4,096 compiled-expression entries. Larger
test-only journal settings are recorded as overrides, never default-release proof.
New topology/engine input queues are bounded explicitly in the companion profile.
Exhaustion reports an unavailable/gap condition instead of dropping semantic events.

Source correction to the Desktop audit: at the pinned HEAD, CanonicalJson.java
declares DEFAULT_MAX_BYTES = 16 * 1024 * 1024, not 4 MiB. The current source and
baseline take precedence; this migration does not change the limit. The existing
Failsafe configuration uses a 512 MiB journal for larger acceptance fixtures while
the production default remains 64 MiB. A pass under that override is not evidence
that the same large recording fits the production default.

The native coordinator currently serializes mutation/verification on the EDT.
Static compile and network/control waits stay in background tasks. Migration must
measure existing native timings and use an ordered pinned-state evaluation lane
for heavy OCL/propagation. UI acceptance measures a 50 ms heartbeat during control,
import and live evaluation; target p95 delay is at most 250 ms in the declared
two-agent fixture. This is an acceptance target, not a measured baseline claim.
No worker reads mutable live state or publishes past an owner/version fence.

## TCP admission and monitor lifecycle

An unchanged same-run snapshot with identical source watermarks and no native
feature changes refreshes the observed cut without advancing original temporal
history. A changed observation still advances. Reconnect preserves an explicit
history gap; it does not recover unobserved MAINTAIN/SUSTAIN continuity.

Canonical control commands wait for the exact accepted evaluation boundary on
the control worker before admission. The command and ownership check are then
registered together on the native writer. PAUSE/RESUME confirmation uses that
completed native cut, not an asynchronous PENDING placeholder. A PAUSED reconnect
refreshes STATUS without implicitly resuming Jason.

Monitor Stop and host Session replacement retire publication on the same EDT
barrier as installation. They revoke delivery and semantic ownership immediately;
TCP close and file cleanup run off the EDT. Background restart waits for the old
ingress to close before subscribing again. Stop retains a stale final system for
ordinary native/manual OCL; restart creates fresh monitor history. Producer
replacement with the same source can reuse the host system while resetting the
run identity, checkpoints, bindings and original histories. These are distinct
operations and do not invent producer STOP/RESTART protocol commands.

The source adapter distinguishes the implicit CArtAgO root `/main` from an
explicit JCM workspace named `main`: JaCaMo Cartago.start creates the latter as
`/main/main`. Both declarations are retained, and artifact aliases resolve through
the exact JCM workspace declaration and actual WorkspaceId. Legacy DTOs containing
one declaration retain their single-declaration compatibility path.

The Phase 0 synthetic probe of the unchanged native coordinator accepted 200
boundaries on one system. Seventeen heartbeat samples had p95/max delay 6.3808 ms;
native OCL p95 was 1.28 ms, mutation p95 0.6477 ms, snapshot p95 1.4119 ms, and
journal p95 1.0183 ms. The probe includes warmup and runs beside the baseline
suite; these are observed timings of this small synthetic model, not a live MAS
benchmark or an SLA. Its source/compiled probe and JSON measurements are retained
under the Phase 0 evidence directory for reproducibility.

## Evidence

`use-plugin/target/semantic-migration/phase-0/baseline-inputs.json` records checkout,
toolchain input and protected-reference hashes. `baseline-verify.log` and
`baseline-result.json` record the actual initial reactor verify. Test failures,
overrides and missing measurements must be retained and triaged before Phase 0
acceptance; no historical result is promoted to a current pass.

The initial reactor verify completed successfully: 693 Surefire and 151 Failsafe
tests, no failures/errors/skips. The separately invoked legacy suite passed all
198 tests. Its standalone classpath must put test resources before main classes
and include Surefire's module-path entries; the first two harness attempts and
their corrected invocation are preserved. No expected result was changed.

Before any final Maven clean, required baseline/phase acceptance artifacts and
their checksum inventory are archived under
`use-plugin/verification/semantic-migration/`, outside Maven target. Final clean
build logs are also written there so Windows does not attempt to delete its open
log file. Raw credentials are excluded. Reference repositories are never cleaned.
