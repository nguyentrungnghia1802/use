# Phase 21 migration readiness

Baseline: `ff604b2f`, frozen structural V1 unchanged. Classification: INTERNAL
IMPLEMENTATION CHANGE + TEST/EVIDENCE CHANGE. No runtime capability promotion.

## Dependency audit (P21.1)

Search scope: production runtime package, MetamodelKind, TraceRecord/TraceIndex,
TransformationPlan and runtime mapping draft. No direct MetamodelKind/EClass/
EAttribute dependency, V1 classifier literals, structural rule IDs or Auction
branches were found in connectors, queue, RuntimeEvent or RuntimeTrace.

| Dependency | Classification | Disposition |
|---|---|---|
| VP002/VP003/classMappings/referenceMappings validation | removable coupling / required V1 binding | V1RuntimeBindingAdapter implements RuntimeBindingContract |
| Runtime alias to USE target lookup | legitimate adapter boundary | TraceRuntimeTargetAdapter uses existing TraceIndex; category + exact semantic ID + resolved/projected status |
| MSystem/MObject/MAttribute APIs | legitimate USE backend mechanics | retained; independent of Ecore vocabulary |
| CartagoArtifactBinding property/operation maps | supplied target binding | retained; target names provided by import/configuration |
| MoiseRuntimeBinding agents/groups/schemes | runtime concepts and exact semantic IDs | retained; no V1 generated classifier names |
| MetamodelKind enum, TransformationPlanner, TraceBuilder | required current V1 semantic/structural binding | reconcile when V2 arrives, outside connectors/queue |
| Auction names in fixture tests | test-only | existing regression retained |

## Adapter contract (P21.2/P21.3)

RuntimeTargetResolver.Request carries runtime identity, optional exact semantic ID
and target category; Target carries resolved semantic identity, USE ID and original
TraceRecord provenance. No fuzzy fallback or second TraceIndex exists. Missing,
stale, wrong category and semantic mismatch fail before mutation. The mutation
engine accepts mapping/target dependencies; its default constructor retains V1.
Dynamic restoration still requires the original traced object/class authorization.
RuntimeBindingContract separates source/action validation from target anchors.
The default loader still validates frozen V1; synthetic alternate adapters are
explicit injection, not an automatic bypass or a claim of final V2 support.

## Diff tooling (P21.4)

Run `python use-plugin/tools/metamodel_diff.py BEFORE.ecore AFTER.ecore [ARTIFACT ...]`.
Output is sorted JSON; input files are never modified. Supply structural mapping,
projection/runtime mapping JSON, OCL profiles and golden .use/.cmd as artifacts.
The report lists exact class/feature additions/removals, inheritance and attribute/
reference metadata changes (type, bounds, target, containment), exact-token
references per artifact and all indirect review layers. Structurally identical
removed/added classes are rename candidates requiring review, never accepted
renames. Token references are conservative review hints, not a semantic OCL parser.
All supplied artifacts require regeneration/review on a structural change even
when no direct token reference exists. No V2 artifact is required for testing.

## Compatibility evidence (P21.5)

RuntimeMigrationTest covers alternate structural anchors and exact resolution
(success, missing, wrong kind, wrong identity, stale, deterministic provenance).
A synthetic compiled Vessel.level model exercises the same RuntimeEvent,
RuntimeTrace, ordered queue, snapshot comparison, mutation and resync mechanics.
Existing Jason/CArtAgO/Moise connector regressions run unchanged: connectors accept
binding data and do not know the target metamodel vocabulary. This demonstrates
reuse for the supported subset; a future V2 still requires semantic/mapping/
projection/OCL/golden-output reconciliation and full compatibility validation.

Validation commands:
- Focused Java gate: RuntimeMigrationTest, RuntimeFoundationTest,
  RuntimeMappingTest, JasonRuntimeConnectorTest, CartagoRuntimeConnectorTest,
  MoiseRuntimeConnectorTest (28 passing before the additional alternate model test).
- `python -m unittest discover -s use-plugin/tools -p test_metamodel_diff.py`: 2 PASS.
- Full reactor and post-merge evidence recorded below after execution.

Phase 20 standalone limitation remains exactly as documented in
[phase20-runtime-evidence.md](phase20-runtime-evidence.md). Runtime mapping remains
DRAFT_WAITING_FOR_METAMODEL_V2. No frozen Ecore/mapping bytes were changed.
