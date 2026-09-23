# METAMODEL MAPPING V2.1 - SECOND-PASS AUDIT

## Verdict

`jacamo-use-mapping-v2.1.json` is the corrected **REVIEWED_CANDIDATE_V2** structural mapping for the exact supplied `jacamo_v2_complete.ecore`. This second pass found real issues in V2.0 and fixes them. The mapping now has exact declared-source coverage, explicit source-vs-target navigation, complete USE keyword escaping for the current V2 vocabulary, strict JSON Schema shapes, and explicit handling of EAttribute requiredness/default provenance.

It is deliberately **not labelled FROZEN**. That status would require migration of the repository's V1-pinned consumer plus an actual end-to-end `USECompiler` gate, mutation tests and refreshed freeze fingerprints. Calling it FROZEN before that would be a false claim, not an improvement.

## Canonical source and fingerprints

- Source of truth: `jacamo_v2_complete.ecore`
- Ecore SHA-256: `4ae51638a078f0a933982844063993084f17d420694ac8a868d95b9df063c35c`
- Mapping V2.1 SHA-256: `78ff207b20122c18b60a28ed03d1fabb57d9badd96518cf13f45c62eb73151f4`
- Schema V2.1 SHA-256: `ed74aa442928580217e5bd71e8662b1707f22764cbc73c8bb1f2bd032e963a98`
- EPackage: `agentmetamodel`, nsURI `http://www.example.org/agentmetamodel`
- USE code inspected at repository revision `59dd582fad63d1a5a19fa8306e3dbed4afe881bc`, parent USE version `7.5.0`.

## Exact source coverage

| Source construct | Source count | V2.1 mapping | Result |
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
- The current plugin `MappingLoader`, `MappingModel`, `TransformationPlanner`, and `StructuralUseGenerator` are still V1-shaped and must be migrated before this mapping can be consumed as repository-canonical V2.

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
- Grammar-oriented generated structural fixture inventory (7 enums / 21 classes / 34 relations): PASS as a static source-level gate.

### Important limitation

The last fixture check is **not** an actual invocation of `USECompiler`; the repository cannot be built/executed in this environment from the GitHub connector alone. Therefore the audit does not manufacture a compiler PASS. Actual compiler execution remains a freeze blocker.

## Final status

No remaining known **mapping-artifact structural inconsistency** was found after the V2.1 corrections above. The remaining blockers are integration/evidence blockers, not unfilled mapping entries:

1. migrate V1 `MappingLoader`/`MappingModel`/generator to schema 2.1;
2. implement native enum handling and eOpposite alias consumption;
3. enforce required/default attribute materialization policy;
4. migrate semantic kinds/extractors/trace/runtime bindings;
5. compile generated V2 `.use` with actual USE 7.5.0 `USECompiler`;
6. run negative mutation, two-case regression and freeze-manifest gates.

Only after those pass should `status` change from `REVIEWED_CANDIDATE_V2` to `FROZEN`.
