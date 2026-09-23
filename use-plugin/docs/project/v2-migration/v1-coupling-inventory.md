# V1 coupling inventory — intake

Machine-readable lexical occurrences: [v1-coupling-occurrences.json](v1-coupling-occurrences.json).
The captured tracked-file scan contains 1,400 conservative exact-token occurrences.
It is an intake index, not proof all semantic dependencies are resolved.

| Area | Disposition | Migration impact |
|---|---|---|
| MappingLoader/MappingModel | MIGRATE | Old paths, namespace, frozen hashes, no enum/opposite/default contract |
| MetamodelKind, parser outputs, resolver | MIGRATE | Removed V1 classes and owner-qualified feature identities; preserve source provenance separately |
| TransformationPlanner/InstancePlanner | MIGRATE | Native enum types, aliases, required/default values, ownership, projection anchors |
| TraceBuilder and bindings | MIGRATE | V2 rule identities, stale bindings, opposite link direction |
| V1RuntimeBindingAdapter, runtime manifests | MIGRATE | V1 target classes/feature anchors; upstream callbacks stay reusable |
| RuntimeMappingLoader | VERSION_ABSTRACTION | Structural fingerprint compatibility through centralized selection |
| Core/case OCL and verification profile | MIGRATE | V1 navigation/contexts require actual V2 compile gates |
| Tests/goldens/release assembly/POM | MIGRATE | Broken relocated paths, old resource names, expected V1 semantics |
| Core/version-1 and committed historical evidence | KEEP_HISTORICAL | Do not rewrite bytes, counts or past verdicts |
| Active docs/tools | REVIEW_REQUIRED | Separate historical claims from V2 specification and executable status |

No REMOVE disposition is asserted without a symbol-level review. Exact old class
names can also be source-language concepts and cannot be globally deleted/replaced.
Projection IDs VP001–VP007 occur in both baselines; identical ID spelling does not
prove identical source anchors. Existing `metamodel_diff.py` is reusable; its
class-oriented output needs complementary enum/package/default coverage.

Cross-layer status: Ecore/source-mapping preliminary audit agrees; IR, transformation,
trace, runtime target bindings and OCL still use V1. Therefore the consistency gate
does not pass yet. Full occurrence-by-occurrence disposition is pending.
