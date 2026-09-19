# Verification Engine

## 1. Responsibilities

Verification service:
- load/compile OCL;
- maintain constraint registry;
- evaluate initial snapshot;
- evaluate runtime updates;
- capture violation details;
- map violations back through trace.

---

## 2. Constraint registry

```text
ConstraintDescriptor
- id
- name
- context
- kind: INV | PRE | POST | QUERY
- origin: TRANSLATED | CORE | CASE | USER
- sourcePath
- sourceSpan?
- dependencies
- enabled
```

---

## 3. Evaluation modes

### Full check
- structure;
- multiplicity;
- all invariants.

Use:
- import completion;
- resync;
- final acceptance.

### Targeted check
Evaluate constraints affected by changed semantic/state dependencies.

Use runtime optimization after baseline correctness.

### Operation check
- pre at enter;
- post at exit;
- preserve `@pre`.

---

## 4. Result model

```text
VerificationResult
- runId
- timestamp
- constraintId
- outcome: PASS | FAIL | ERROR | SKIPPED
- contextObject
- explanation
- sourceTrace[]
- runtimeEventIds[]
```

---

## 5. Severity

Constraint metadata may classify:
- INFO
- WARNING
- ERROR
- CRITICAL

OCL truth result remains true/false; severity is reporting policy.

---

## 6. Violation navigation

From violation:
1. USE object/operation;
2. trace;
3. semantic element;
4. JaCaMo source span;
5. related runtime event.

UI must allow this chain.

---

## 7. Unsupported/undefined

Distinguish:
- OCL evaluates `undefined`;
- constraint compile error;
- missing runtime data;
- unsupported translation;
- unresolved trace.

Do not collapse tất cả thành FAIL.

---

## 8. Baseline verification

At import:
- model compiles;
- structure valid;
- initial state valid;
- translated OCL compiles;
- core/case OCL compiles;
- full check run.

---

## 9. Runtime verification lifecycle

States:
```text
OFFLINE
MODEL_READY
CONNECTING
SYNCING
LIVE
STALE
ERROR
```

Only `LIVE` means runtime results are current.

---

## 10. Reports

Export:
- JSON machine-readable;
- Markdown summary;
- optional CSV for experiment analysis.

Report must include versions/hashes để reproducibility.
