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

## 11. Phase 8 implementation contract

- `ConstraintRegistry` joins every compiled USE invariant/pre/postcondition to translated, core, case, or user
  provenance. It preserves dependencies, source spans, OCL text, and SHA-256 fingerprints of the generated model,
  translated manifest, and loaded profiles.
- `DefaultVerificationService` checks structure/multiplicity separately, then evaluates compiled invariants per
  context object so OCL undefined remains `ERROR` instead of being collapsed into `FAIL`. Disabled constraints are
  `SKIPPED`.
- Operation checks snapshot the USE pre-state at enter, bind `self` and arguments, evaluate preconditions, and later
  evaluate postconditions against the captured pre-state and supplied post-state. Correlation IDs and runtime event
  IDs are retained in every operation result.
- JSON and Markdown exports contain the four outcomes, context, explanation, OCL source, JaCaMo semantic trace,
  correlation, event IDs, and reproducibility fingerprints. The service remains headless and does not require a
  live JaCaMo runtime.

## 12. Phase 11 runtime verification contract

- `RuntimeMirrorService` exposes a headless `RuntimeEventObserver` around its single ordered mutation boundary.
  Runtime verification observes but never controls or blocks JaCaMo execution.
- Every authoritative snapshot increments a mirror version and runs a full structure/invariant check. Every
  projected state-changing delta runs after the USE mutation and retains the runtime event and correlation IDs.
- `OP_ENTER` resolves the exact traced USE object and projected operation, converts arguments according to the
  compiled USE signature, snapshots the pre-state, and evaluates preconditions. `OP_EXIT` evaluates explicit
  postconditions with USE's pre/post evaluator, preserving `@pre`; `OP_FAIL`/abort records `SKIPPED` postconditions
  rather than pretending that a post-state contract ran.
- `ConstraintDependencyIndex` indexes declared semantic dependencies. Targeted evaluation also includes enabled
  invariants without declared dependencies because they may be global; an unknown change with no conservative
  selection uses a full-check fallback. The targeted evaluator and full evaluator share the same invariant path.
- Drift checks request an authoritative connector snapshot and compare all projected object existence, scalar
  value, and association mutations with the current USE mirror. Each difference has diagnostic code
  `RUNTIME_MIRROR_DRIFT`, event/runtime identity, target, expected value, and actual value. Policies are
  `REPORT_ONLY` and `AUTO_RESYNC`; periodic checks use a daemon scheduler and any resync returns to `LIVE` only
  after a fresh full synchronization.
- `RuntimeVerificationReport` stores connection state, snapshot version/fingerprint, event, verification results,
  diagnostics, and evaluation latency. Its JSON and Markdown exporters preserve violation context, OCL source,
  source trace, correlation, and runtime event IDs.

Phase 11 evidence (2026-09-15): `mvn -pl use-plugin test` passes 61 tests. The real Auction integration uses live
Jason 3.3.0, CArtAgO 3.1, and Moise 1.1, closes the Auction, rejects `placeBid(item1, 0)`, and asserts that the
runtime report contains a failing OCL result correlated to the exact CArtAgO event and imported Artifact trace.

## Phase 22 current checkpoint contract

See [runtime verification evidence](phase22-verification-evidence.md). Checkpoints
are first-class; non-LIVE requests are SKIPPED without OCL evaluation, and stream
boundaries retire pre-state. Snapshot verification precedes buffered deltas.
RuntimeHistoryVerifier checks recorded ordering independently of OCL. Reports
preserve checkpoint, exact trace/source spans, event and result correlation.

Phase 23 adds `CrossDimensionalVerifier` for exact source-declared cross-dimensional
link checks. These are structural binding checks, not inferred behavioral rules;
see [supported scope and unsupported boundaries](phase23-cross-dimensional-evidence.md).
