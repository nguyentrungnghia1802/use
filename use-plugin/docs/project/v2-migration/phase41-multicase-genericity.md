# Phase 41 Multi-case V2 and genericity audit

Status: DONE. Focused and full-reactor gates pass with zero skipped tests.
V2 remains WORKING_BASELINE.

## Same production pipeline

MultiCaseV2PipelineTest passes Auction and CounterTeam as data into one parameterized
path. Both use StaticProjectImporter, canonical MappingLoader, TransformationPlanner,
VerificationSemanticLayer, InstancePlanner, OclGenerator, TextBackend,
DirectUseBackend, TraceBuilder, ConstraintRegistry, DefaultVerificationService,
RuntimeMappingLoader and RuntimeMutationEngine. Both initial states compile, satisfy
structure/invariants, contain CASE constraints, produce exact trace records and load
the same STRUCTURAL_MAPPING_V2 runtime contract. The generation step is deterministic.
No branch selects a transformer, mapping, runtime action or verifier by project name.

An executable source audit scans production Java plus active Metamodel V2, Mapping
V2, core OCL V2, Runtime Mapping V2 and verification-profile V2 resources. It rejects
the concrete agent/artifact/operation/project tokens used by either case. Case names,
object IDs, operation names and runtime aliases occur only in checked-in examples,
case OCL, explicit test bindings and evidence generation.

## Auction disposition

LiveJaCaMoAuctionIntegrationTest imports the checked-in project, generates the V2
model and initial state, builds trace/OCL and observes real pinned Jason, CArtAgO and
Moise components through the production connector/mirror/verifier pipeline. A valid
bid passes. Zero/negative amount and bidding while closed produce the authored CASE
violations with exact event/SemanticId/source navigation. Operation PRE/POST and
`@pre` are covered by the shared runtime-verification gate. Observable-property
removal becomes undefined, reconnect performs an authoritative resync and finishes
with zero drift. The fixture does not contain or invent highestBid/currentBid.

This is supported in-process component evidence. It does not upgrade the historical
derived launcher control into full original standalone Auction plan/deadline semantic
equivalence; that boundary remains explicitly unsupported.

## CounterTeam disposition

CounterTeamIntegrationTest imports and transforms the second project with the same
production components. Its exact Java guard is translated while unsupported Jason
applicability remains explicit. A positive setValue produces PASS, a negative value
produces the authored CASE FAIL with event/SemanticId attribution, and guardedSet
failure produces the operation-aborted boundary. The same exact trace, Runtime
Mapping, queue, mirror and verifier are used. Moise role/mission/goal observations
remain trace evidence; no norm lifecycle is inferred. Disconnect, source change,
reconnect and authoritative resync converge with zero drift.

## Genericity and historical-reference audit

Production source search finds no Auction, CounterTeam, concrete object ID,
case-operation or project-name dispatch. MappingLoader.loadFrozen and
VerificationProfileLoader.loadV1 are explicit historical/reproducibility entry
points; ActiveBaseline and default runtime/OCL selectors remain V2-only and never
fall back. Historical V1 resources, old namespace placement and stale migration
wording are recorded for the dedicated Phase 42 packaging and Phase 43 stale-reference
audits. Their presence is not a second active baseline and is not case logic.

Cross-dimensional behavior remains bounded by Phase 40: structural source relations
can be verified exactly, while agent-action/environment-operation equivalence,
focus/access, belief delivery, organisational-goal delivery and role/mission behavior
remain unsupported without source evidence. No case policy is promoted into core.

## Cross-layer consistency

Canonical Ecore/Mapping, Semantic IR, transformation, trace/binding, OCL and Runtime
Mapping bytes are unchanged. Phase 39 supplies the correct MSystemState and Phase 40
supplies runtime verdict attribution. This phase adds only a two-case production-path
gate and audit evidence; no USE core or example-specific production code changes.

## Acceptance evidence

- Focused multi-case and cross-layer gate: 24/24 PASS, 0 failures, 0 errors,
  0 skipped (`phase41-focused.json`).
- Full reactor gate: 359/359 PASS across USE core, GUI, assembly and plugin unit/
  integration suites, 0 failures, 0 errors, 0 skipped (`phase41-regression.json`).
- Phase consistency chain is intact: canonical Metamodel V2 and Structural Mapping V2
  are unchanged; both cases traverse Semantic IR, transformation, trace/binding, OCL,
  Runtime Mapping V2, MSystemState mirroring and runtime verification through the same
  production implementations.
