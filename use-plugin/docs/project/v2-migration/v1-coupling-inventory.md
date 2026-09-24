# V1 coupling inventory — intake

Machine-readable lexical occurrences: [v1-coupling-occurrences.json](v1-coupling-occurrences.json).
The reproducible baseline-revision scan contains 8,817 conservative exact-token occurrences
across 305 text files, plus the checked-in old plugin JAR.
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
does not pass yet. Every occurrence now has an explicit file/line disposition; semantic migration remains pending. No dead-code REMOVE claim is made without evidence.

Reproduce with `python use-plugin/tools/v1_coupling_audit.py --revision eeb98c41c2d398b15b74101773ea440bcaa1af18 --output <file>`. Two independent runs were byte-identical; unique paths, line counts and complete classification were checked. Scope includes whole-repository strong identities and plugin removed class/feature names, IDs, projections and count assertions. The old binary `use-gui/lib/plugins/use-jacamo-plugin-1.0.1.jar` is MIGRATE through packaging, never edited in place. Grouped line numbers avoid duplicating historical source text.

## Consumer reconciliation follow-up

The table and occurrence index above describe intake revision eeb98c41. Active
production migration now removes the V1 kind bridges, MAS constructor and
V1RuntimeBindingAdapter; the default loader, IR/parser, transformation, trace,
OCL and runtime targets use V2. The explicit historical loader/tests and preserved
V1 artifacts remain KEEP_HISTORICAL. Current working ZIP replaces the plugin and
canonical resources during installation; the historical binary is not hand-patched.
A production Java search finds no version-1 resource access or V1 count decisions.
The final executable gate is recorded separately in Phase 35 acceptance evidence.
