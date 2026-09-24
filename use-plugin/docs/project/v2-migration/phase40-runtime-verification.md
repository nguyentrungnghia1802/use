# Phase 40 Runtime Verification V2

Status: focused 69/69 and full reactor 356/356 PASS, zero skips; workflow
closure pending. V2 remains WORKING_BASELINE.

## Checkpoint contract

| Checkpoint | Trigger and mirror state | Selection and result |
|---|---|---|
| SNAPSHOT | authoritative snapshot applied with zero residual drift; mirror has entered LIVE but buffered deltas are not released | full structure plus all enabled invariants; snapshot ID/version/fingerprint retained |
| AFTER_MUTATION | validated state mutation completed on the single consumer while LIVE | declared dependency targets plus every global invariant; any unknown dependency causes full fallback |
| OPERATION_PRE | exact OP_ENTER RuntimeKey -> SemanticId V2 -> object and VP003 operation while LIVE | typed arguments, one captured pre-state and PRE results; observation never blocks or controls JaCaMo |
| OPERATION_POST | matching OP_EXIT or OP_FAIL correlation, target, operation and stream generation | successful exit evaluates POST with captured `@pre`; failure/abort is SKIPPED; unmatched/stale/duplicate terminal is ERROR |
| STREAM_BOUNDARY | subscription closes, disconnects, reconnects or workspace changes | active pre-state and timing are retired; diagnostic states authoritative resync is required |

OFFLINE, MODEL_READY, CONNECTING, SYNCING, STALE and ERROR cannot produce a
current runtime verdict. Attempts are recorded as SKIPPED with
RUNTIME_MIRROR_NOT_CURRENT. Phase 39 proves the supported mirror state before this
engine evaluates it. JaCaMo remains the executing system; the observer never invokes
an agent action or artifact operation.

## Invariant and operation correctness

ConstraintDependencyIndex previously returned only global invariants when all
changed SemanticIds were unknown. A RED control showed that a global constraint
could therefore suppress the required full fallback. Unknown and mixed
known/unknown dependency sets now select RUNTIME_DEPENDENCY_UNINDEXED and a full
check. Known changes retain targeted evaluation plus global invariants. The
targeted evaluator shares the ordinary invariant path, so PASS/FAIL/ERROR and
undefined handling do not diverge.

Operation tests prove exact MObject/MOperation lookup through the active trace,
strict argument count/type conversion, a single captured pre-state, `@pre`, matching
successful exit, failure SKIPPED, stale/duplicate/cross-target terminal rejection,
backpressure tombstones and cleanup at the accepted sequence watermark. Connector
receipt remains a short concurrent-map write while a deliberately blocked OCL check
runs on the mirror consumer. RuntimeHistoryVerifier remains a finite recorded-trace
evaluator, explicitly separate from OCL: it checks increasing sequence, generation,
unique correlation and start-before-terminal, and reports an unfinished finite
prefix as SKIPPED rather than inventing a liveness failure.

## Violation attribution

Every runtime result now carries RuntimeVerificationAttribution. JSON output includes
constraint ID/name/origin, checkpoint, outcome, USE context and operation, actionable
explanation, OCL source, constraint source path/span, RuntimeEvent ID/kind/sequence,
correlation, V2 SemanticId, runtime source, runtime mapping rule, structural and
projection mapping rules, exact trace/source hash and result event IDs. Missing
registry descriptors are labeled SYSTEM rather than assigned a false CASE/CORE
origin. Markdown exposes the core review fields. Attribution is evidence only and
does not authorize a mutation.

## Cross-dimensional disposition

CrossDimensionalVerifier evaluates only exact source-declared structural relations
using the active Mapping V2 descriptor and membership association trace. Target-only
order support cannot substitute for source membership. The following runtime
behavioral relations remain explicitly unsupported because current sources do not
prove delivery/correlation or an instance policy:

- Jason ACTION evidence is not equated to the authoritative CArtAgO OpId lifecycle.
- workspace focus/access is not represented by an active mutable V2 runtime rule.
- observable-property delivery is not inferred to create a Jason belief.
- organisational goal state is not inferred to create or complete an agent goal.
- role/mission observations are not inferred to imply performed behavior.

This disposition preserves exact supported structural checks without deriving a
behavioral invariant from an EReference or name similarity.

## Consistency and evidence scope

Metamodel V2, Structural Mapping V2, Semantic IR, transformation, Phase 36 trace,
Phase 37 OCL and Phase 38 Runtime Mapping are unchanged. Phase 39 supplies the
current MSystemState; this phase changes conservative selection and reporting only.
No USE core, Ecore, mapping/hash, golden or case-specific production logic changes.

Focused evidence covers checkpoint sequencing, non-LIVE behavior, dependency
fallback, global invariants, PRE/POST/`@pre`, abort and duplicate controls, runtime
history, exact cross-dimensional membership and report export. Live Auction and
CounterTeam gates exercise the same production observer after zero-drift snapshots.
