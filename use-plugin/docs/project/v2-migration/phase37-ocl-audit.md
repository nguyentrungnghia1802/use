# Phase 37 OCL migration audit

Status: COMPONENT GATES PASS; workflow closure pending. Active V2 semantic inputs remain unchanged and WORKING_BASELINE.

## Origin and binding inventory

| Origin | Constraint family | V2 binding and provenance |
|---|---|---|
| TRANSLATED | CArtAgO guards | Exact Operation semantic ID, projected concrete owner/signature and guard sourceFacts/span/hash; proven primitive int/boolean relational/logical subset only |
| TRANSLATED, not emitted | Jason Plan.context | Exact Plan dependency and source provenance retained as UNSUPPORTED_JASON_APPLICABILITY; no global invariant inferred from applicability |
| CORE | OperationAnchorRequiresExternalKind | Action.operation and Action.kind; optional operation remains optional; explicit VP004 extension rationale in jacamo-core-v2.ocl |
| CORE | order_<mapping-rule> | Each authoritative ordered source feature from TransformationPlan.orderProjections; rank uniqueness/range, target uniqueness and membership bijection; exact Mapping V2 source identity in descriptor ID |
| CASE | AuctionInitiallyOpen, AuctionOpenForBid, PositiveBidAmount | AuctionArtifact.open; placeBid(item:String, amount:Integer); authored auction.ocl provenance, not inferred operation effects |
| CASE | NonNegative, AllowedRequest, ObservedResult | Counter.count; setValue(value:Integer); authored counter.ocl provenance, with negative runtime control |
| USER | Loaded user profiles | Independent allowed-root loader; example UserOpen exercises source line/origin retention; USE compiler rejects invalid classes/navigation |

The old jacamo-core.ocl is historical and never selected by loadCore. No V1 OCL
navigation is silently rebound. Generated source-order constraints are necessary
to establish the target-only rank representation's validity, not duplicates of
ordinary USE multiplicity checks. Mapping source identity is provenance, not proof
of a complete event dependency set: order invariants remain conservatively global.

## Repairs

Two exact owner names Aa/BB produce equal Java String.hashCode values for distinct
V2 Plan identities. The new source-backed regression failed with one constraint ID
for two source constraints. ConstraintExtractor now uses SHA-256 of the complete
semantic identity; unsupported conditions retain distinct deterministic identities.
This changes generated constraint IDs and provenance manifests, not source
SemanticIds, core/case expressions, metamodel, mapping or target model semantics.

Order projection invariants previously fell through the registry's compiled USER
fallback. GeneratedOcl now carries the exact plan's order metadata. The registry
uses that proof to assign CORE and Mapping V2 provenance. Name similarity alone
cannot establish origin; raw compiled constraints without generation proof retain
the explicit USER fallback. No fabricated JSON source line is assigned.

## Translation and evaluation boundaries

Impure guards, arithmetic with unproven Java overflow/division semantics, unknown
calls, mismatched parameter names/types and unapproved LOSSY/SOUND_SUBSET conditions
remain UNSUPPORTED or not emitted with provenance. Arbitrary Java bodies never
become postconditions. Norm values/strings remain structural; no deontic-to-OCL
translation is introduced. PRE/POST and @pre use explicit source/profile contracts.
Undefined evaluation remains ERROR where defined by the verification contract.

## Gates

RED: ConstraintClosureTest reproduced the owner-hash collision. Separately,
OfflineVerificationServiceTest reproduced CORE order provenance mislabeled USER.
Focused gate 43/43 and full reactor 354/354 PASS, zero skips. Post-merge gate pending. Existing positive/negative, PRE/POST/@pre,
undefined/error, case/user loader, source-order and CounterTeam tests are reused.
New negative control rejects the historical ExternalAction OCL context against V2
with USE_MODEL_INVALID and the offending context in the compiler diagnostic.

Cross-layer review: unchanged canonical Ecore/Mapping and V2 IR feed unchanged
transformation and exact trace. Only constraint identity/provenance changes;
MSystemState and runtime mapping contracts are preserved. All five Phase 36 golden
digests remain unchanged in the focused regression.
