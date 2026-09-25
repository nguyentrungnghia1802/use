# METAMODEL MAPPING V2.2 - FINAL FREEZE AUDIT

## Verdict

`jacamo-use-mapping-v2.json` is the **FROZEN** structural mapping for the exact supplied `jacamo_v2_complete.ecore`. The earlier V2.1 candidate audit found and corrected real V2.0 issues. V2.2 additionally preserves every independent authoritative many-valued source order through target-only ranks. The final mapping has exact declared-source coverage, explicit source-vs-target navigation, complete USE keyword escaping, strict JSON Schema shapes, and explicit EAttribute requiredness/default provenance.

Phase 29-43 migrated all active consumers and passed actual `USECompiler`, negative mutation, transformation, trace, OCL, runtime mapping, mirror-correctness, verification, package and two-case gates. Phase 44 therefore freezes the unchanged V2 Ecore semantics and the V2.2 mapping contract. Historical V1 loaders remain explicit reproducibility paths only; no production fallback exists.

## Canonical source and fingerprints

- Source of truth: `jacamo_v2_complete.ecore`
- Ecore SHA-256: `4ae51638a078f0a933982844063993084f17d420694ac8a868d95b9df063c35c`
- Mapping V2.2 frozen SHA-256: `fc03b90cf0729260747bfeffa6a6cd463eefd2259c0c3cd60ed22bd140ec48b1`
- Schema V2.2 SHA-256: `e9f555ad2e19cbf8382ff4c17d44179d3b47a13d03ee9a58c2b491f99b72c919`
- EPackage: `agentmetamodel`, nsURI `http://www.example.org/agentmetamodel`
- USE code inspected at repository revision `59dd582fad63d1a5a19fa8306e3dbed4afe881bc`, parent USE version `7.5.0`.

## Exact source coverage

| Source construct | Source count | V2.2 mapping | Result |
|---|---:|---:|---|
| EEnum | 7 | 7 | PASS |
| EClass | 21 | 21 | PASS |
| EAttribute | 48 | 48 | PASS |
| EReference source features | 37 | 37 | PASS |
| eSuperType | 0 | 0 | PASS |
| eOpposite pairs | 3 | 3 canonical associations + 3 aliases | PASS |
| Containment EReference | 19 | 19 compositions | PASS |
| Required EAttribute (`lowerBound=1`) | 42 | 42 requiredness records | PASS |
| Explicit EAttribute defaults | 21 | 21 default mappings | PASS |
| Conditional/reuse projections | 7 | 7 | PASS |

The 37 EReferences produce **34 emitted USE associations/compositions** plus **3 non-emitting eOpposite aliases**. This is intentional: an eOpposite pair is one bidirectional relation, not two independent associations.

## Problems found in V2.0 and corrections

### 1. `Artifact.operations` escape was emitted but not declared consistently

V2.0 correctly changed the target role to `ecore_Artifact_operations`, because lowercase `operations` is a USE grammar keyword, but `identifierPolicy.targetAssociationRoleEscapes` listed only `Norm.role`. Worse, `navigation.forward` still said `Artifact.operations`, so one field looked like target navigation while actually containing source navigation.

V2.1 fixes this by explicitly declaring all current target-only escapes:

- `Plan.context -> ecore_Plan_context`
- `Norm.role -> ecore_Norm_role`
- `Artifact.operations -> ecore_Artifact_operations`

and by replacing ambiguous navigation strings with separate `sourceForward`/`sourceReverse` and `targetForward`/`targetReverse` fields.

### 2. V2.0 schema was much too permissive

The V2.0 schema accepted many malformed nested objects because major sections were only `type: object`; association ends, navigation records, projections, statistics and most contract sections were not closed. A schema PASS therefore did not mean much.

V2.1 uses Draft 2020-12 closed shapes (`additionalProperties: false`) across the mapping, strict ID/source/multiplicity/identifier patterns, separate emitted-association vs alias shapes, strict navigation records, strict projection contracts, and strict top-level contract/statistics structures. Eleven negative schema mutation controls were run and all were rejected.

### 3. Required EAttribute semantics were under-specified

V2 has **42** EAttributes with `lowerBound=1`. USE scalar attribute declarations do not directly encode Ecore's attribute multiplicity. V2.0 carried the bounds but used an absence policy that could be read as allowing required/defaulted source attributes to remain undefined in USE.

V2.1 makes the boundary explicit:

- required + no explicit default -> `REQUIRE_RESOLVED_SOURCE_VALUE`;
- explicit Ecore default -> `MATERIALIZE_EXPLICIT_ECORE_DEFAULT_WHEN_SOURCE_UNSET`;
- optional + no default -> `LEAVE_UNDEFINED_IF_SOURCE_UNSET`.

Every attribute now has `sourceRequired` and `targetFidelity`. This does **not** pretend that USE MAttribute syntax itself contains an Ecore lower bound; the later instance-materialization gate is responsible for the missing runtime validity rule.

### 4. Pseudo source-feature names were misleading

V2.0 placed `Property#runtimeValueType` and `Operation#parameterTypesAndResult` under `unresolvedSourceFeatures`, even while noting that those features do not exist in V2 Ecore. They could be mistaken for real source identities.

V2.1 replaces that section with `externalProjectionRequirements` (`XPR001`, `XPR002`). They are explicitly `declaredInEcore=false` and are projection preconditions only. No invented feature can enter source coverage.

### 5. Containment fidelity needed a global caveat

Each containment reference still maps correctly to a USE `composition`, with the source/container as USE's first aggregation end and inverse multiplicity `0..1`. However, per-association `0..1` alone must not be described as proof of Ecore's global single-`eContainer` identity across different containment references. V2.1 explicitly requires instance materialization to preserve the actual source `eContainer` and reject conflicting containment links.

## USE source findings rechecked

At the inspected USE revision:

- `USEBase.gpart` accepts native `enum`, classes, attributes, association/composition, ordered ends, and attribute initialization syntax.
- `ASTAssociation.gen` applies aggregation/composition kind to the **first** association end, confirming that the mapping's container/source first end is the correct composition/diamond end.
- USE examples include an association with **both ends ordered**, so the three ordered `* <-> *` eOpposite associations are representable.
- `UseModelApi`/`MModel` support native enum/class/attribute/association concepts.
- The repository `UseNameAllocator` and USE grammar/editor keywords confirm that lowercase `context`, `role`, and `operations` cannot be emitted unchanged in the relevant identifier positions.
- The active plugin `ActiveBaseline`, `MappingLoader`, `MappingModel`, `TransformationPlanner`, `StructuralUseGenerator`, materialization, trace, OCL and runtime binding layers consume repository-canonical V2. Historical V1 loaders are separate explicit entry points.

## eOpposite mapping recheck

The canonical bidirectional relations remain:

- `R021` `Agent.roles <-> Role.agents` -> `Agent_roles_Role`; alias `R008` does not emit a second association.
- `R022` `Agent.workspaces <-> Workspace.agents` -> `Agent_workspaces_Workspace`; alias `R032` does not emit a second association.
- `R023` `Agent.artifacts <-> Artifact.agents` -> `Agent_artifacts_Artifact`; alias `R035` does not emit a second association.

For each pair, both declared multiplicities and both declared ordered flags are used on the corresponding USE ends. Both source navigations are authoritative.

## Projection audit

- **VP001**: `Artifact.type` is only the structural anchor; concrete CArtAgO implementation identity remains external evidence.
- **VP002**: `Artifact.properties -> Property` is structural. A typed USE attribute requires independently resolved runtime/property type evidence; arity/name are insufficient.
- **VP003**: `Artifact.operations -> Operation` is structural. `Operation.name/arity` do not justify invented parameter names/types/result.
- **VP004**: `Action.operation -> Operation` is reused; environment-operation interpretation applies only when mapped `Action.kind=EXTERNAL`.
- **VP005**: `Belief.property -> Property` is preserved without inferring percept-delivery timing.
- **VP006**: `AGoal.organizationalGoal -> OGoal` is preserved without inferring satisfaction lifecycle.
- **VP007**: `Norm` stays a class/object with native `NormType`, strings and associations; no obligation/permission-to-OCL translation is invented.

All structural bindings referenced by VP001-VP007 resolve to existing E/C/A/R/I IDs.

## Validation performed in this second pass

- XML parse and SHA-256 source identity: PASS.
- Exact EEnum/EClass/EAttribute/EReference/eSuperType coverage: PASS.
- Exact EEnum name/literal/value preservation: PASS.
- Exact EAttribute bounds/default provenance: PASS.
- Required/default/optional attribute policy consistency: PASS.
- Exact reference target/bounds/containment/ordered/unique metadata: PASS.
- eOpposite reciprocity and canonical/alias resolution: PASS.
- Composition first-end/diamond convention against USE parser source: PASS.
- Emitted association-name uniqueness: PASS.
- USE navigation namespace uniqueness: PASS.
- Attribute-vs-navigation collision check: PASS.
- Reserved target identifier check, including `operations`: PASS.
- Projection structural-binding resolution: PASS.
- Strict JSON Schema Draft 2020-12 validation: PASS.
- 11 malformed-schema mutation controls rejected: PASS.
- Grammar-oriented generated structural fixture inventory (7 enums / 21 classes / 34 relations): PASS.
- Actual USE 7.5.0 `USECompiler` compilation and text/direct materialization parity: PASS.
- Independent opposite-order projection and runtime reorder controls: PASS.
- Full source coverage, negative mutation, two-case and package/freeze-manifest gates: PASS.

### Freeze boundary

The freeze does not infer concrete runtime property types or operation signatures from names/arity, does not auto-convert Moise deontic semantics to OCL, and does not promote original Auction plan/deadline equivalence. Those boundaries remain explicit. Any semantic mapping change requires a new version and the documented diff/impact/regression loop.

## Final status

No remaining known mapping-artifact structural inconsistency or pending metamodel change exists. Every earlier integration/evidence blocker listed by the candidate audit is closed by executable Phase 29-44 evidence. Status is `FROZEN`; the authoritative hashes and unresolved semantic boundaries are recorded in `release/v2-freeze-manifest.json` and the Phase 44 closure record.
