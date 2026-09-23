# V2-ORDER-001 — target-only order projection implementation

Status: **WORKING; component gates PASS, end-to-end V2 consumer migration OPEN**.
User-approved option 1 preserves the full V2 ordered instance domain. No Ecore
bytes or USE core code changed. No source class names appear in the generic
projection implementation. Membership-only failure evidence remains mandatory.

## Contract and representation

Mapping/schema 2.2.0 adds `orderProjection` with a closed shape. It explicitly
separates canonical membership from source order. Each ordered many-valued source
EReference generates `OrderEntry_<ruleId>` with Integer `rank`, an owner endpoint
and a value endpoint typed by that rule. Both authoritative opposite directions
generate independent entries. Generated reverse helpers do not acquire source
order. Single-valued and unordered features do not generate entries.

`OrderProjectionPlanner` derives specifications from exact mapping entries and
aliases. It accepts resolved authoritative lists, including explicit empty lists;
it rejects missing, duplicate, unresolved, extra or membership-inconsistent lists.
Input list order is retained. Entry IDs hash a length-prefixed tuple of source
feature, owner semantic ID and target semantic ID, independent of rank. The same
membership therefore has the same entry identities after reorder.

`MappingLoader.loadWorking` snapshots input bytes, validates the strict supplied
schema and exact embedded Ecore hash, then checks source kinds, types, bounds,
ordered/unique/opposite metadata and coverage. Historical frozen loading remains
separate. The default production selector is still a Phase 32/35 migration gate.

`TransformationPlan` carries enums and order specifications. Both materializers
consume the same augmented ObjectPlan/LinkPlan. Membership aliases are deduplicated
in canonical orientation. Projection links are separate associations, never extra
membership links or source containment edges. Existing verification profile
application preserves the new plan fields.

## Navigation, validity and trace

Each source owner has a query `ordered_<ruleId>() : Sequence(Target)`. It reads
only order entries, sorts by rank and collects typed values. `OrderNavigationBinding`
requires the exact owner-qualified EReference identity. It does not rewrite
arbitrary OCL strings or resolve bare feature names. Raw membership navigation
must not be used as an authoritative ordered source path.

Generated OCL invariants enforce rank uniqueness, bounds `0 <= rank < size`,
target uniqueness and exact set equality with membership. Together these enforce
contiguous ranks and a membership bijection, including empty lists. Trace records
identify each ordered query, projected row and endpoint link with source identity,
mapping rule and `ORDER_V1` projection provenance.

## Runtime integration

`REPLACE_ORDER` / `RELATION_REORDER` is an explicit normalized action requiring
the working `OrderRuntimeBindingContract`; frozen V1 mappings do not authorize it.
Payload lists carry exact source feature and endpoint semantic IDs. The existing
RuntimeMutationEngine resolves them through trace, validates the entire order
snapshot and membership before writing, then changes only rank attributes. No
membership insert/delete occurs. Drift comparison checks expected ranks.

The existing runtime queue, sequence checks, mirror lifecycle, disconnect and
authoritative reconnect/resync are reused. Tests use a generic synthetic connector;
this does not claim real Jason/CArtAgO/Moise source order extraction is migrated.
Membership-changing snapshots require a freshly projected, traced candidate and
coherent workspace installation; reorder refuses stale membership rather than
inventing rows or silently changing membership. `resync` builds and validates that
candidate using shared materialization. Full dynamic V2 connector integration
remains a downstream runtime gate.

## Executable evidence

`OrderProjectionTest` proves generic cyclic independent order, deterministic output,
actual shell-command/direct parity, unchanged membership, invalid/missing lists,
composition ownership, non-opposite and unordered controls, traced rank-only
mutation, invalid rank detection, exact OCL binding, live queued reorder, drift,
disconnect/reconnect and authoritative order restoration. Loader mutations reject
stale hash, changed ordering/opposite/type, duplicate IDs and altered rank contract.

`V2OppositeOrderingAuditTest` retains all 72 insertion-only counterexample
permutations and positive controls. It additionally feeds each native EMF case
through the production 2.2 loader/planner/projector and verifies every authoritative
navigation through OCL, both directions, with exactly four membership links.

Focused regression: **27 tests PASS**, including the native Ecore/mapping audits,
historical static baselines and runtime identity/mapping controls. The added
normalized action is explicitly excluded from V1 authority while taxonomy coverage
still checks every event kind. Full regression: **325 executed, 3 failures, 73 errors, 0 skipped**; the same
76 failing test identities as the pre-migration baseline. No new failing identities.
See `order-projection-working-manifest.json` and `order-projection-impact.json`.
This is not a full build PASS.

## Remaining integration gates

- Migrate default selection, Semantic IR and parser output through Phases 32–35.
- Resolve source lists from authoritative parser/runtime evidence, never arbitrary
  object iteration or membership insertion order.
- Complete default facade OCL/trace/runtime binding and real source snapshot gates
  in their dependent phases; component PASS is not whole-project V2 acceptance.
- Re-run Phase 29/30 consumer gates after migration; retain staged scheduling.

The small runtime adapter work in this task is required by the explicitly approved
cross-layer ordering contract, not a relocation of unrelated production migration
into Phase 29. V2 remains WORKING_BASELINE, not FROZEN.
