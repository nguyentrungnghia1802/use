# Phase 30 native structural audit

> Phase 44 supersession: the exact audited Ecore bytes and inventory are unchanged
> and are now `FROZEN` by `release/v2-freeze-manifest.json`. The status and counts
> below describe the Phase 30 point-in-time gate.
>
> Current acceptance: downstream consumer gates PASS after **350/350 clean reactor**
> and **11/11 Python tests**. Earlier run counts/OPEN descriptions below retain
> their historical stage. See [final acceptance](phase35-acceptance.md); phase
> merge/post-merge/push closure PASS. V2 remains WORKING_BASELINE.

Classification: TEST/EVIDENCE CHANGE. Canonical Ecore and mapping bytes unchanged.
Active specification: WORKING_BASELINE; production migration and phase acceptance OPEN.

`V2EcoreAuditTest` loads the supplied file using EMF Ecore/XMI 2.39.0 (Common
2.42.0), resolves the resource set, rejects remaining proxies, then invokes the
native `Diagnostician` over the entire EPackage. Result: OK, no resource errors
or unresolved proxies. XML-only intake was not promoted to native EMF validation.

Native inventory agrees with the independently generated XML inventory: 21 EClasses,
7 EEnums, 48 declared EAttributes, 37 declared EReferences, no inheritance edges.
Counts are derived, not production constants. Package `agentmetamodel`, nsPrefix
`agentmetamodel`, nsURI `http://www.example.org/agentmetamodel`; no explicit package
version annotation. SHA-256 and toolchain are in `metamodel-v2-working-manifest.json`.

Negative controls cover malformed XML, unresolved class and datatype references,
duplicate classifiers, cyclic inheritance, mutually containing opposite references,
and entity/DTD rejection. Invalid source is rejected rather than passed to mapping.
Native EMF may reject an unresolved reference while loading, before proxy inspection;
the test asserts that native diagnostic rather than assuming a later failure stage.

Verification: `mvn -B -pl use-plugin -Dtest=V2EcoreAuditTest,PreMigrationBaselineTest test`
passes 6 tests, zero failures/errors/skips. Audit tests are 4; two historical static
controls remain green. Python diff/intake regression: 9/9 PASS. Log retained at
`target/phase30-ecore-audit.log` (module-relative). Test-only EMF dependencies do not
enter plugin runtime/shaded packages.

Scope: structural validity of supplied Ecore, not proof of language/runtime
equivalence or representability of every valid instance in USE. Required/default
materialization, independent source ordering, identity migration, runtime target
bindings and OCL remain separate downstream gates. The full-suite failures recorded
at Phase 29 remain OPEN; no rollback, merge or phase-complete claim is made.
