# Risk Register

## R1 — Ecore reconstruction differs from original author artifact
Impact: semantic mapping claim weak.
Mitigation:
- preserve provenance;
- keep review flags;
- verify source figures/papers;
- avoid overclaim.

## R2 — Mapping technically complete but semantically wrong
Impact: USE verifies wrong abstraction.
Mitigation:
- per-entry audit;
- cross-dimensional tests;
- case study;
- projection rationale.

## R3 — Parsing AgentSpeak/Java/Moise incompletely
Impact: missing model/state.
Mitigation:
- supported subset declared;
- parser diagnostics;
- fixtures;
- no silent fallback.

## R4 — Java Artifact behavior cannot be statically translated
Impact: postconditions unavailable.
Mitigation:
- only project signatures/state with evidence;
- allow user/case OCL;
- runtime observation.

## R5 — Norm → OCL semantic collapse
Impact: invalid research claim.
Mitigation:
- preserve Norm;
- separate deontic/runtime semantics;
- explicit combined rules only.

## R6 — Runtime events out of order
Impact: false violations.
Mitigation:
- sequence/correlation;
- serialized queue;
- resync;
- stale state status.

## R7 — USE plugin API instability
Impact: maintenance.
Mitigation:
- pin USE commit/version;
- isolate USE adapter;
- minimize core patches;
- contract tests.

## R8 — JaCaMo API version drift
Impact: runtime adapter breaks.
Mitigation:
- isolate connectors;
- compatibility matrix;
- integration tests per pinned version.

## R9 — Ambiguous operation/property resolution
Impact: wrong target.
Mitigation:
- owner-qualified IDs;
- deterministic resolution;
- explicit binding; never fuzzy auto-map.

## R10 — Performance
Impact: runtime backlog.
Mitigation:
- measure;
- cache trace/OCL;
- selective reevaluation;
- batching after correctness.

## R11 — Thesis scope explosion
Impact: incomplete product.
Mitigation:
- phase gates;
- each phase independently testable;
- runtime monitor baseline remains observe-only.
