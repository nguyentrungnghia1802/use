# Exact V1 to V2 impact classification

Inputs and exact diff: `metamodel-v1-to-v2-diff.json`; all 263 supplementary
structural change entries have dispositions in `metamodel-v1-to-v2-impact.json`.
The existing qualified-identity diff is reused; supplementary local-identity details
make same-spelling changes visible despite the global namespace replacement.
No rename or semantic equivalence is inferred from spelling similarity.

| Change family | Disposition and migration obligation |
|---|---|
| Namespace dSML4JaCaMo to agentmetamodel | Breaking source identity. Invalidate old mapping/trace fingerprint compatibility; preserve source IDs only with exact semantic proof. |
| Removed MAS/specification wrapper classes | Removed metamodel capability. Project/deployment envelope remains source metadata; do not invent a V2 MAS EClass. |
| Removed Rule/Body/BodyTerm/Context/Message/MentalNotes | Removed structural capability. Preserve raw source facts/provenance and explicit unsupported boundaries; no automatic conversion into Action. |
| Action hierarchy replaced by Action + ActionKind | Breaking representation. Distinguish internal/external from parsed syntax; no guessed operation receiver. |
| Goal/TriggeringEvent/ObsProperty/Organisation versus AGoal/Event/Property/Organization | Removed and added identities, not accepted renames. Migration requires source-backed adapters, correct features and ownership. |
| Operation family loses abstract/guard/internal/linked hierarchy | Breaking representation. Keep signature/guard/source-role facts as extraction metadata; no inherited V1 feature assumptions. |
| Organization/Group/Role/Link structure | Breaking ownership and cardinality representation. Do not equate role definitions with participation occurrences without provenance. |
| Scheme rootGoal, OGoal plan, OPlan subGoals, Mission goals | Breaking containment/reference direction. Preserve source goal tree; mission references do not own goals. |
| Primitive names/types/bounds/defaults and native enums | Breaking typed values. Explicit defaults only, exact enum name/literal conversion, required unresolved values fail materialization. |
| Bidirectional Agent–Role/Workspace/Artifact | Added authoritative inverse semantics. Deduplicate opposite links while retaining both traces and both declared orderings. |
| Seven conditional/reuse projections | Reconcile exact V2 anchors; same VP ID is not semantic compatibility. No invented Java signature or property type. |

Per-change classification is deliberately conservative: additions/removals describe
metamodel capabilities only; existing source-language constructs do not disappear
because V2 omits their EClasses. Every changed entry is SEMANTIC_BREAKING_CHANGE
until equivalence is established. No entry is prematurely labelled representation-only.
Each row enumerates IR, parser, structural mapping, projection, trace, runtime target
binding, OCL and case-study obligations. Runtime callback/queue mechanics are reusable;
only their exact target-binding compatibility is invalidated by this structural diff.

No canonical input is modified. Current code still consumes V1 vocabulary and
default paths. Phase 32–35 must implement the affected adapters before closing the
Phase 29/30 consumer gates; runtime/OCL migration is not claimed by this audit.
