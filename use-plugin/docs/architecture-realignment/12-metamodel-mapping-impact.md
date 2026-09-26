# Frozen V2 and mapping impact

## Current immutable baseline

| Artifact | Version/status | Path | Verified SHA-256 |
|---|---|---|---|
| Ecore metamodel | V2, `FROZEN` | `Core/Metamodel/version-2/jacamo_v2_complete.ecore` | `4ae51638a078f0a933982844063993084f17d420694ac8a868d95b9df063c35c` |
| Structural mapping | V2.2.0, `FROZEN` | `Core/Mapping/version-2/jacamo-use-mapping-v2.json` | `fc03b90cf0729260747bfeffa6a6cd463eefd2259c0c3cd60ed22bd140ec48b1` |
| Runtime mapping | 2.0.0/schema 3.0.0, `FROZEN` | `src/main/resources/org/tzi/use/plugins/jacamo/runtime/jacamo-use-runtime-mapping-v2.json` | `5b2c00f052010fb35a71eb7f50ae4650f8a09c7647332b47a7199c12cbf8a5f0` |
| Freeze manifest | V2 release candidate, not a published tag | `release/v2-freeze-manifest.json` | `7965aef8eae099e398de50cb30a65f1b6c5b9207a6500db678825340bca73c77` |

Inventory remains 7 enums, 21 classes, 48 attributes, 37 references and three opposite pairs.

## Decision

Keep the metamodel, Mapping V2.2, Runtime Mapping V2, projections and hashes unchanged during the architecture migration. `08-jacamo-to-v2-coverage.md` found no `V2_GAP` that blocks this migration, but it also documents known representation loss: relation-scoped Moise role/subgroup cardinality cannot always be preserved by frozen intrinsic-looking attributes, and several runtime facts have no automatically faithful USE runtime representation. Neither issue may be “fixed” by guessing or changing frozen artifacts in this round.

## Impact by layer

| Layer | Required impact | Forbidden shortcut |
|---|---|---|
| Ecore V2 | None | Add lifecycle fields merely because the Bridge needs them |
| Mapping V2.2 | None to its semantic rules/hashes | Edit mapping to accept guessed frontend facts |
| Verification projections | Keep; attach Bridge provenance and source completeness | Remove order/opposite projections to simplify migration |
| Runtime Mapping V2 | Keep target semantics; add a pre-mapping contract adapter outside frozen artifact | Change frozen event targets to mirror a transport schema |
| Semantic IR | Add contract provenance/completeness/identity compatibility around existing concepts | Leak Jason/CArtAgO/Moise classes into backend |
| Trace | Extend Bridge/source identity chain | Treat USE display name as authority ID |
| MModel | Rebuild from official ModelSnapshot using current planner/mapping | Mutate model ad hoc after accepting events for another revision |
| MSystemState | Initialize from the faithfully projectable subset of an accepted RuntimeSnapshot; retain other facts as evidence-only | Call declaration-only state “authoritative runtime” or fabricate relations/attributes for unmapped facts |

## Version drift requiring a compatibility gate

The frozen manifest pins JaCaMo 1.3.0 and Jason 3.3.0, while the audited JaCaMo repository declares 1.3.1 and resolves Jason 3.3.2. CArtAgO 3.1 and Moise 1.1 match. This is not evidence to edit the frozen files. Implementation Phase A/B must:

1. preserve the current pinned release evidence;
2. compile/run an API compatibility suite against the audited 1.3.1/3.3.2 graph;
3. record exact distribution fingerprints in Bridge capabilities;
4. decide whether the Bridge supports both graphs or whether a new compatibility manifest—not a rewritten frozen manifest—is required;
5. require explicit review before changing any release/version policy.

## MModel strategy

The frozen vocabulary is compiled first. The adapter then feeds exact official declarations/AST/OS/descriptors to the existing transformation/planning pipeline. If initialization reveals a concrete artifact descriptor needed to create a class/property absent from the current model revision, the Bridge emits a new ModelSnapshot/model revision. USE compiles a replacement MModel and rematerializes an accepted snapshot transactionally; runtime events cannot mutate an older model.

This is model evolution at runtime-contract level, not an Ecore change.

## MSystemState strategy

- Offline/configuration mode may materialize declared objects and labels the result `DECLARED_NON_RUNTIME`.
- Verification of a live system begins only from an accepted `RuntimeSnapshot` matching the current model revision.
- Existing opposites and projections remain responsible for structural consistency.
- Mission commitments, organizational-goal state, norm lifecycle, scheme/group runtime-instance context and execution correlations are always retained in Bridge state/trace/evidence/reporting when observable.
- Only facts faithfully materialized by the approved adapter/current runtime mapping become `MSystemState` dependencies. A selected OCL profile requiring an evidence-only fact is `INCONCLUSIVE`, `NOT_EVALUATED` or capability-blocked; it never receives a guessed/default value.
- Relation-scoped cardinality remains exact in Bridge/provenance. If multiple group/parent contexts cannot be represented without collapse, the frozen V2 projection reports `REPRESENTATION_LOSS` and dependent verification is gated.

## Deferred representation decision gates

After the Bridge and canonical Hello/Auction/House cases stabilize, conduct a separate **Runtime Verification Projection Review**. For each runtime fact it decides, with evidence, whether the fact remains evidence-only, receives a target-only USE runtime representation outside frozen Runtime Mapping V2, or truly requires a new metamodel version. A companion V2.x/V3 review may consider full relation-level cardinality only if thesis requirements demonstrate that the exact Bridge/provenance facts are insufficient. Neither review reopens frozen artifacts during the present architecture migration.

## Formal V2-gap gate

A proposal for V2.1/V3 is admissible only when all conditions hold:

1. a versioned official source/API proves the concept is in scope;
2. no V2 class/feature plus existing USE state/link/projection/trace can represent it faithfully;
3. the issue is not lack of initialization, listener, correlation or Bridge adapter work;
4. concrete Hello/Auction/House or a generic counterexample demonstrates semantic loss;
5. the proposal includes exact Ecore/mapping/runtime/OCL/trace migration impact and compatibility plan.

Until then, the result is `API_NOT_EXPOSED`, `REPRESENTATION_LOSS` or `UNSUPPORTED`, not `V2_GAP`.

## Regression gates

- Recompute and compare all frozen hashes before and after every migration phase.
- Run `V2EcoreAuditTest`, `V2MappingAuditTest`, `OrderProjectionTest`, `ActiveBaselineTest`, `RuntimeMappingTest`, `CompatibilityManifestTest` and `V2FinalFreezeTest`.
- Prove deterministic model/planning/materialization output for the same ModelSnapshot.
- Prove that a Bridge contract schema change alone cannot alter frozen mapping digests.
- Preserve Phase 44 evidence and label any new JaCaMo-version compatibility evidence separately.

## Verdict

Frozen V2 reuse is `FULLY_FEASIBLE` for the bounded architecture-migration scope, with explicit `REPRESENTATION_LOSS`/capability gates; this is not a claim of 100% fidelity to all official Moise/runtime semantics. Backend adapter work is `FEASIBLE_WITH_ADAPTER`. No frozen artifact in this file is authorized for modification by the audit.
