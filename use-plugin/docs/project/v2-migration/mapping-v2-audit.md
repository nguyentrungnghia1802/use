# Phase 31 — Mapping V2 audit

> Phase 44 supersession: Mapping V2 is now `FROZEN`; its final exact hash and
> compatibility pointer are in `release/v2-freeze-manifest.json`. The open and
> partial descriptions below intentionally preserve the Phase 31 point in time.
>
> Current acceptance: downstream consumer gates PASS after **350/350 clean reactor**
> and **11/11 Python tests**. Earlier run counts/OPEN descriptions below retain
> their historical stage. See [final acceptance](phase35-acceptance.md); phase
> merge/post-merge/push closure PASS. V2 remains WORKING_BASELINE.

Current status: **PARTIAL; V2-ORDER-001 decision approved and target-only projection implemented at component boundaries**.
See [2.2 implementation evidence](phase31-order-projection.md). The following
sections preserve the pre-projection 2.1 audit scope and regression run.

Historical 2.1 status: **PARTIAL; semantic decision was required**. V2 remains the
active WORKING_BASELINE, not frozen. No production consumer has been migrated by
these test-only audit fixtures. No canonical Ecore, Mapping or schema bytes changed.

## Independently verified contract

`V2MappingAuditTest` loads the supplied JSON with its supplied Draft 2020-12 schema,
then resolves exact owner-qualified source identities against native EMF Ecore.
The schema checks version/closed shapes; additional checks validate the exact Ecore
fingerprint, package/namespace, unique rule IDs and exhaustive source coverage.
The actual V1 fingerprint is rejected by a mutation control. Other controls reject
unknown root/nested fields, orphan sources, duplicate IDs, wrong datatypes,
wrong opposite aliases and changed default expressions.

All 21 classes, 7 enums, 48 attributes and 37 references are covered. There are
zero source inheritance edges; no fabricated inheritance is needed. The mapping
contains 34 emitted relationships and three opposite aliases. Source and target
types, containment direction, bounds, explicit defaults, requiredness and enum
literal names/values are audited. Compiling the generated fixture detects target
identifier errors/collisions; negative controls exercise a missing type and the
unescaped reserved `operations` role. Original/escaped names remain in mapping
rules. This is audit provenance, not completed production trace integration.

`mapping-v2.use` compiles through the real USECompiler. Mapping hashes and exact
Ecore compatibility pointer are in `mapping-v2-validation.json`. Re-run the audit
after any canonical change; the old result cannot authorize new input bytes.
Structural compilation does **not** prove lossless instance mapping.

## Projection inventory and limits

`mapping-v2-projection-audit.json` inventories all seven supplied projection
contracts, source anchors, target concepts, prerequisites, assumptions and stated
information loss. Native source elements and structural binding IDs are checked.

- VP001 concrete Artifact subtype requires independently resolved implementation
  identity/ancestry. The compiler fixture uses an explicit test-owned subtype.
- VP002 property state requires external value type evidence. Property name/arity
  alone supplies no state type; keep its structural object when unresolved.
- VP003 operations require externally resolved ordered parameter names/types and
  result type. The compiler fixture supplies an explicit signature; it does not
  infer one from arity. Java behavior is outside that fixture.
- Remaining Agent/Environment and Organisation anchors reuse V2 structural
  elements. They establish link provenance, not extra executable semantics.
- VP007 preserves Norm data, enum and relations. Conditions/time constraints remain
  source strings; no automatic deontic-to-OCL interpretation is claimed.

These contracts are case independent. Runtime state updates and OCL binding of
resolved projections remain downstream implementation gates. The current audit
does not claim complete executable projection or runtime acceptance.

## Semantic fidelity gate

The supplied ordered opposite contract fails for a native-EMF-valid counterexample.
For each of R021/R022/R023, all 24 link insertion permutations fail to preserve
both source orders; the same test finds two matching positive-control permutations.
All target states pass structural validation. See
[the decision and cross-layer impact](phase31-ordered-opposite-decision.md) and
`ordered-opposite-counterexample.json`. The passing boundary test demonstrates
the blocker; it is not a passing fidelity acceptance test.

Consistency review: Ecore permits independent end order; Mapping retains both
orders but its canonical association cannot represent every such instance through
current USE insertion. IR, transformation, trace, runtime mapping and OCL navigation
must adopt the approved representation or explicit supported-domain restriction
together. Phase 32–35 dependent design is held pending that decision. Phase 29/30
consumer migration gates remain OPEN under the already approved staged schedule.

## Validation

- Focused reactor: `mvn -B -pl use-plugin -am
  -Dtest=V2MappingAuditTest,V2OppositeOrderingAuditTest,V2EcoreAuditTest,PreMigrationBaselineTest
  -Dsurefire.failIfNoSpecifiedTests=false test`: **11/11 PASS**.
- Full `mvn -B verify`: **317 executed, 3 failures, 73 errors, 0 skipped**.
  The 76 failing test identities exactly equal the pre-migration baseline; no new
  failing test identities. Plugin packaging/failsafe is not reached. See
  `phase31-regression.json`; full log remains in `target/phase31-full-regression.log`.
- Canonical input bytes are unchanged from the starting revision. The final
  fixture generator only trims an extra blank line at EOF; all 11 focused tests
  are rerun after that formatting fix. No golden output was edited to suppress
  a failing test. `git diff --check` passes before the audit commit.
- Python inventory/diff regression: 9/9 PASS.

Phase 31 is not fully complete, and no release/build PASS is claimed.
