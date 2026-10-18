# Host compatibility adapters

No upstream use4Goal core file is patched by these adapters.

## P-USE-PRINT-01 — USE 7.5 named datatype export

At host commit `6fdfe5ee584b97a48f961e74e6e218d2c3020210`,
`use-core/src/main/java/org/tzi/use/uml/mm/MMPrintVisitor.java::visitModel`
visits enumerations, classes, associations and constraints, but does not visit
`MModel.dataTypes()`. `visitDataType(MDataType)` already exists and prints valid
native syntax. The first canonical plugin integration test failed structural
round-trip equality because `Money` disappeared; its log is retained under
Phase 5 evidence.

`Use75ModelPrinter` repeats only the host model traversal and adds the missing
datatype visitation. It calls the original visitor methods for all syntax and
expressions. `NativeUseExporter` selects this adapter only for the canonical v4
marker; historical model export/hashes retain their prior route. Regression
tests assert that named datatype declarations and typed attributes both survive
native compile/print/recompile; structural equality is not weakened. The
reference repository and USE core printer source remain unchanged.

## P-USE-PRINT-02 — deterministic annotation key order

The Phase20 isolated installed replay reproduced different annotation key order
between JVM processes because host `visitAnnotation` traverses the original map.
The neutral printer delegates that method with a sorted copy of the values;
native models, annotation contents, original engine algorithms and strict replay
equality remain unchanged. See `P-USE-PRINT-02.md` and the retained cross-JVM diff.
