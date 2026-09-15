# Testing and Quality Strategy

## 1. Principle

Mỗi phase phải có test trước khi merge.
Không chấp nhận "GUI chạy được" là evidence duy nhất.

---

## 2. Test pyramid

### Unit
- source key parsing;
- mapping schema;
- type mapping;
- multiplicity;
- name sanitizer;
- expression translation;
- trace lookup;
- event normalization.

### Parser fixtures
- valid/minimal;
- invalid syntax;
- include;
- duplicate symbols;
- ambiguous references;
- edge cases mỗi dimension.

### Contract tests
- Ecore ↔ mapping coverage;
- mapping ↔ USE target validity;
- semantic IR invariants;
- generated OCL compile.

### Golden tests
Input project → exact deterministic:
- `.use`;
- `.cmd`;
- generated OCL;
- trace;
- diagnostics.

### Integration
- load generated model into USE;
- create state;
- run check;
- operation pre/post.

### Runtime integration
- synthetic event stream;
- reconnect/resync;
- ordering;
- trace miss;
- state drift.

### End-to-end
Auction project:
- import;
- offline check;
- runtime;
- positive/negative scenarios;
- report.

---

## 3. Mapping audit test

Automated assert:
- all 37 classes;
- all 67 attributes;
- all 63 references;
- all 14 inheritance;
- unresolved list exact;
- no duplicate source key;
- no duplicate generated association name;
- projections schema-valid.

Counts must come from canonical Ecore at test runtime, not duplicated constants only.

---

## 4. Mutation/negative tests

Bắt buộc có:
- remove mapping entry → audit fails;
- wrong owner → audit fails;
- wrong reference target → audit fails;
- wrong multiplicity → audit fails;
- mapping hash mismatch → block;
- invalid OCL → compile fail;
- ambiguous operation → require binding;
- runtime unknown object → no wrong state mutation.

---

## 5. Quality gates

Before merge:
- format/lint pass;
- unit pass;
- integration pass affected modules;
- no new warnings unless documented;
- docs updated;
- task checklist updated.

Before release:
- full test suite;
- Auction E2E;
- clean checkout build;
- plugin load test in target USE distribution;
- generated artifact reproducibility.

---

## 6. Performance gates

Measure, not optimize prematurely.

Record:
- project import time;
- model generation time;
- full OCL check time;
- runtime event-to-result latency;
- memory.

`JaCaMoFacade.performanceMetrics()` exposes the latest observed values: durations are nanoseconds, used memory is
the current JVM heap sample in bytes, and zero means the corresponding measurement is not yet available. Runtime
event-to-result latency begins at connector receipt and ends when that event's verification result is reported; it
therefore includes queueing and mutation work. These values are evidence/diagnostics, not performance guarantees.

Set project-specific thresholds after baseline measurements; do not invent performance guarantees before data.

---

## 7. Compatibility matrix

Track:
- Java version;
- Maven version;
- USE commit/version;
- JaCaMo version;
- Jason/CArtAgO/Moise versions;
- OS used for verification.

Pin versions for thesis experiments.
