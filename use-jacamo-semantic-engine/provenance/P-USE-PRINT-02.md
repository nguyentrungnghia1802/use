# P-USE-PRINT-02 — deterministic annotation serialization

Owner: neutral `Use75ModelPrinter`, outside original selected use4Goal source.
No host or original engine file is patched. Source headers/hashes and P-CTX-01
remain unchanged.

Phase20 `Use4GoalPackagedReplayIT` reproduced
`REPLAY_CANONICAL_BASELINE_MODEL_MISMATCH` in a fresh installed USE JVM.
The test-owned `installed-model-diff.txt` showed identical model elements and
annotation values with different key order: JDK immutable `Map.of` iteration
is randomized between JVM processes. Host `MMPrintVisitor.visitAnnotation`
iterates `MElementAnnotation.getValues()` directly.

The adapter overrides only that public visitor method and delegates all syntax
to the host with a new `MElementAnnotation` holding a `TreeMap` copy. It does not
mutate annotations/native models, change mappings/semantics, drop metadata or
relax replay equality/hash validation. Existing native/legacy printing is
unchanged; this adapter is selected only for canonical CSL v4.

Regression: `Use75CslBackendTest.equalAnnotationValuesHavePortableTextWithoutMutatingNativeModels`
and the original failing isolated installed replay test. Evidence under
`use-plugin/verification/semantic-migration/phase-20/`.
