# Phase 22 runtime verification

Classification: BUG FIX RESTORING EXISTING CONTRACT + TEST/EVIDENCE CHANGE.
Frozen Ecore/structural mapping and runtime mapping selectors are unchanged.

## Checkpoint contract

| Checkpoint | Trigger and mode | Context and stale behavior |
|---|---|---|
| SNAPSHOT | authoritative full snapshot; full structure/invariants | LIVE only; snapshot version/fingerprint; non-current request is SKIPPED |
| AFTER_MUTATION | applied mapped state delta; conservative targeted/full fallback | LIVE only; event, correlation, exact trace and mirror version |
| OPERATION_PRE | exact traced projected operation enter | LIVE only; compiled signature, args, self and captured pre-state |
| OPERATION_POST | matching successful exit; @pre from captured state | LIVE only; failed operation yields SKIPPED; missing/mismatched correlation ERROR |
| STREAM_BOUNDARY | disconnect/resync/workspace retirement | pre-state discarded; SKIPPED evidence, no current truth claim |

Mirror publishes snapshot verification before releasing buffered deltas. Snapshot
observer failure closes the subscription and retires stream state. Non-applied
mutations produce ERROR, never false PASS. STALE/OFFLINE/ERROR checkpoints produce
SKIPPED without calling OCL. Operation pre-state is captured once; wrong target or
semantic identity cannot consume another operation's pre-state. Terminal reuse and
stream-retired correlation cannot produce a valid POST.

## History representation

VerificationSemanticLayer supports inheritance/multiplicity profile decisions,
not event-history slots. RuntimeHistoryVerifier therefore evaluates finite
RuntimeTrace histories independently of OCL. Rules cover strict per-generation
sequence, one start per invocation, exact start-before-terminal and explicit
happened-before pairs. Missing pair evidence is SKIPPED; rejected/retired events
are ERROR evidence; invalid recorded order is FAIL. An unfinished finite trace is
SKIPPED, not a fabricated liveness/deadline violation. Callers pass an exported or
in-memory RuntimeTrace; no speculative metamodel or case branch is added.

## Attribution/export

Reports add a first-class checkpoint and original TraceRecord provenance. JSON
retains event ID/sequence/runtime ID/semantic ID/correlation, result correlation,
USE context, source spans, fingerprints and structure validity. Exact TraceIndex
lookups support navigation; there is no similarly-named fallback. Existing report
constructors remain source-compatible, defaulting to DIAGNOSTIC without provenance.
Schema version remains 1.0.0 with additive output fields (no report JSON input
schema is widened). Existing consumers of fields remain compatible.

## Evidence

RED: staleMirrorCannotEvaluateCurrentInvariantOrCapturePreState failed against
Phase 21 with expected true / actual false. Focused engine/foundation regression
then passed. Full reactor results recorded below after completion.

RuntimeVerificationEngineTest covers Auction invariant/PRE FAIL, POST/@pre,
operation failure SKIPPED, valid/invalid explicit history order, stale suppression,
exact terminal identity, duplicate terminal, export provenance, stream retirement,
backpressure, targeted/full equivalence and snapshot-before-delta ordering.
RuntimeHistoryVerifierTest adds generic cross-target, duplicate terminal,
unknown/retired generation and finite-prefix controls. Existing live Auction,
reconnect/resync and independent drift-comparison tests remain mandatory gates.

The Phase 20 standalone launcher limitation is unchanged. E2E evidence covers the
supported in-process/synthetic mirrored subset; it does not establish autonomous
full Agent -> Artifact -> Organisation launcher execution.

Full reactor `mvn --batch-mode verify`: 292/292 PASS, zero failures/errors/skips (2026-09-19).
