# OCL and verification impact

## Decision

Preserve the existing OCL/verification architecture and frozen artifacts. Change its authoritative input from custom reconstructed semantics/in-process objects to accepted Bridge model/state/event data. Do not translate Moise/NPL norms automatically into OCL.

## Existing reusable stack

| Current component/artifact | Target use | Impact |
|---|---|---|
| `OclProfileLoader` | Load core/case OCL profiles | Keep; bind result to model/profile hashes |
| `OclGenerator` | Generate supported structural constraints | Keep; source facts now carry Bridge provenance |
| `ConstraintExtractor` / `ConstraintExpressionParser` | Read explicitly supported constraint declarations | Keep with stricter origin/completeness metadata |
| `VerificationSemanticLayer` | Select verification closure/profile | Keep; add required Bridge capabilities |
| `ConstraintRegistry` / `ConstraintDependencyIndex` | Constraint identity/dependencies/invalidation | Keep; key dynamic dependencies through canonical Bridge/trace IDs |
| `DefaultVerificationService` | Static/current-state checks | Keep; execute only against a matching accepted model/state revision |
| `RuntimeVerificationEngine` / `RuntimeHistoryVerifier` | PRE/POST/history checks | Keep with correlated contract events and gap awareness |
| core OCL V2 | Frozen structural invariants | Unchanged hash/content |
| case OCL | Case-scoped assertions with explicit provenance | Preserve separation; no case rule enters generic core |

## Verification layers

```text
1. Contract validity
   schema + distribution + identity + completeness + revision
2. Structural validity
   frozen V2 / Mapping V2.2 / projection invariants
3. Authoritative snapshot validity
   MSystemState built from one accepted RuntimeSnapshot
4. Incremental/runtime validity
   Runtime Mapping V2 + PRE/POST/history over correlated events
5. Domain/case profiles
   explicit OCL selected for that project, never hard-coded in core
6. Normative lifecycle evidence
   Moise/NPL report kept separate from OCL results
```

A lower layer failure prevents stronger claims at higher layers.

## Static definitions versus runtime facts

| Static definition in frozen V2 | Distinct runtime fact/instance | Verification boundary |
|---|---|---|
| `Mission` | mission commitment `(scheme incarnation, mission, agent)` | OCL only if the commitment is faithfully materialized; otherwise evidence-only/capability-blocked |
| `OGoal` | satisfaction/progress state in one scheme-board incarnation | Never overwrite the static goal definition; evaluate only an approved state projection |
| `Norm` | NPL norm instance and lifecycle | Preserve NPL evidence separately; no automatic OCL translation or fabricated `Norm` attribute |
| `Scheme` / `Group` | runtime scheme/group instance and board context | Preserve incarnation in Bridge/trace; do not treat definition identity as runtime instance identity |

## Norms are not OCL

Moise norms have lifecycle, activation context, fulfillment/unfulfillment, deadlines, sanctions and an NPL execution semantics. OCL evaluates side-effect-free predicates over a USE model/state. Therefore:

- static `Norm` definitions may be represented in V2 and checked for structural integrity;
- NPL active/fulfilled/unfulfilled/inactive/failure events remain normative-runtime evidence;
- no deontic condition/deadline is converted into OCL without an explicit, reviewed translation contract and equivalence evidence;
- an OCL violation is not automatically a norm violation, and an NPL unfulfilled norm is not automatically an OCL invariant failure;
- reports may correlate both by canonical identity, while preserving distinct origin and semantics.

This preserves the existing manifest boundary for original Auction plan/deadline semantics.

## MModel and MSystemState gates

- Compile/load constraints only after the `ModelSnapshot` and frozen mapping identify one `modelRevision`.
- Static verification may run over a declared/offline state only when reports label it `DECLARED_NON_RUNTIME`.
- Live verification requires a complete-enough RuntimeSnapshot for every dependency of the selected constraint profile.
- Bridge capture alone does not make a runtime fact OCL-queryable. Every dynamic dependency must be faithfully materialized into `MSystemState` by an approved projection.
- An unresolved reference, evidence-only fact or missing capability produces `INCONCLUSIVE`, `NOT_EVALUATED` or capability-blocked attribution, not a default value that happens to satisfy OCL.
- On a model revision change, invalidate compiled dependencies, rebuild the MModel/state and only then resume checks.

## PRE/POST and event attribution

1. Bridge action/operation correlation creates the verification checkpoint identity.
2. PRE evaluates against the last accepted state whose required source watermarks precede the request.
3. State deltas are applied transactionally and attributed to canonical event IDs.
4. POST runs only after the completion/failure boundary and the required property/state watermark.
5. Missing correlation, event gap or stale generation yields `INCONCLUSIVE`; no result is assigned to the nearest action by name/time.
6. Reports include session, generation, model revision, snapshot ID, event/correlation IDs, constraint hash and source capability set.

## Translated and generated OCL

Any translated/generated OCL remains governed by:

- source expression and exact provenance;
- translator/version/hash;
- supported-language subset and explicit rejection reasons;
- generated OCL text/digest;
- compile result against the exact MModel;
- semantic-equivalence tests, not merely syntactic compilation.

Bridge migration does not broaden the translation subset. Unsupported Moise/Jason expressions remain unsupported.

## Regression plan

| Gate | Required evidence |
|---|---|
| Frozen artifacts | Existing core OCL/profile/freeze hashes unchanged |
| Structural | Existing V2/mapping/projection/OCL compile tests pass |
| Provenance | Every constraint/result traces to profile/source and Bridge entities |
| Capability | Missing required source makes result not evaluated/inconclusive |
| Runtime projection | Captured-but-unmaterialized commitment/goal/norm/instance facts remain evidence-only and block dependent OCL |
| Determinism | Snapshot + event replay produces identical report digest |
| PRE/POST | Success/failure/gap/resync cases attributed to exact correlation |
| Norm separation | NPL lifecycle cannot enter OCL registry without explicit translator metadata |
| Case isolation | Hello/Auction/House profiles cannot alter generic core constraints |

The current full Maven baseline has three failures—golden pipeline digest, constraint-closure expectation and instance-materialization golden digest—on the pre-existing dirty frontend state. Migration must preserve these as known baseline evidence, diagnose them independently and must not update a golden or lower an assertion merely to make the new path pass.

After Bridge and canonical-case stabilization, the `Runtime Verification Projection Review` decides per fact whether evidence-only status is sufficient, a target-only USE runtime projection is warranted, or a new metamodel version is required. This audit does not pre-authorize any of those implementations.

## Verdict

The OCL/verification backend is `FEASIBLE_WITH_ADAPTER` and primarily reusable. The material change is stronger input provenance, version/capability gating and runtime correlation—not a rewrite of the verification engine.
