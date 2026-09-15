# Runtime Adapter

## 1. Mục tiêu

Mirror trạng thái chạy thật của JaCaMo vào USE `MSystemState` để OCL kiểm chứng runtime.

USE không điều khiển JaCaMo trong baseline.

```text
JaCaMo Runtime
      ↓ events/state
Runtime Adapter
      ↓ trace lookup
USE MSystemState
      ↓
OCL Evaluation
```

---

## 2. Runtime integration strategy

Implementation phải khảo sát API thực tế của:
- Jason runtime/event hooks;
- CArtAgO observability/operation events;
- Moise runtime/organisation events;
- JaCaMo integration points.

Ưu tiên:
1. official event/listener API;
2. plugin/monitor hook;
3. stable runtime API;
4. controlled polling nếu không có event;
5. instrumentation chỉ khi các cách trên không đủ.

Mỗi choice phải được document.

---

## 3. Normalized event model

```text
RuntimeEvent
- eventId
- timestamp
- sequence
- dimension
- kind
- runtimeSourceId
- semanticSourceId?
- payload
- correlationId?
```

Agent events:
- belief added/removed/changed;
- goal adopted/removed/achieved;
- action start/end;
- message sent/received.

Artifact events:
- operation enter/exit/failure;
- observable property created/changed/removed;
- signal;
- artifact lifecycle.

Organisation events:
- role adoption/removal;
- group/scheme instance;
- mission commitment/achievement/failure;
- norm state only if runtime API exposes semantics reliably.

---

## 4. State mutation mapping

Normalize runtime events thành:
- CREATE_OBJECT
- DESTROY_OBJECT
- SET_ATTRIBUTE
- INSERT_LINK
- DELETE_LINK
- OPERATION_ENTER
- OPERATION_EXIT
- OPERATION_FAIL

Mỗi mutation phải:
- reference trace;
- be ordered;
- be auditable;
- support rollback/recovery policy.

---

## 5. Operation contracts

Flow:
```text
JaCaMo operation starts
→ resolve projected USE operation
→ capture pre-state
→ evaluate preconditions
→ let JaCaMo continue (observe-only)
→ collect state changes
→ operation exits
→ evaluate postconditions
```

Nếu USE API yêu cầu explicit operation enter/exit, adapter dùng API tương ứng.

Violation không block action trong baseline; chỉ report.

---

## 6. Ordering/concurrency

JaCaMo có concurrency.
Adapter cần:
- monotonic sequence per connection;
- timestamps;
- correlation IDs;
- per-operation nesting/correlation;
- documented consistency model.

MVP:
- serialize normalized events vào single verification queue;
- preserve observed order;
- warn khi order không xác định.

Sau đó có thể hỗ trợ causal ordering.

---

## 7. Snapshot strategy

Modes:
- Event-driven delta;
- periodic full resync;
- manual resync.

Khuyến nghị:
- delta fast path;
- periodic/full resync safety net.

Resync so sánh:
```text
JaCaMo authoritative snapshot
vs
USE mirror
```
và report drift.

---

## 8. Failure handling

Connection lost:
- mark mirror STALE;
- stop claiming live verification;
- reconnect;
- full resync before LIVE.

Unknown runtime entity:
- resolve via trace;
- otherwise quarantine event.

USE mutation fails:
- emit fatal verification diagnostic;
- do not silently drop event.

---

## 9. Performance

Track:
- event throughput;
- mapping/trace lookup latency;
- state mutation latency;
- OCL evaluation latency;
- queue depth;
- dropped events = must remain 0 in correctness mode.

Optimization only after correctness:
- dependency-based invariant reevaluation;
- batching safe state updates;
- cached compiled OCL;
- selective snapshots.

---

## 10. Security/safety

Do not execute arbitrary project shell commands automatically.
Treat imported project as untrusted:
- validate paths;
- prevent path traversal;
- isolate reflection/class loading where possible;
- do not run Java code merely to inspect static structure unless user explicitly runs project/runtime.

## 11. Phase 9 foundation contract

- `RuntimeConnector` exposes lifecycle, capability reporting, authoritative full snapshots, and event
  subscriptions. `SyntheticRuntimeConnector` implements this contract from a schema-validated JSON replay file.
- Runtime Event V1 carries a monotonic sequence, timestamp, dimension, runtime/source identities, typed payload,
  and optional correlation ID. `runtime-event-v1.schema.json` rejects unknown structural fields, while kind-specific
  validation enforces mutation payload requirements.
- `RuntimeMutationEngine` is the single USE mutation boundary. It implements object create/destroy, attribute set,
  link insert/delete, and operation enter/exit/failure correlation. An event without a runtime/semantic trace is
  quarantined and cannot mutate state.
- `OrderedRuntimeEventQueue` is single-consumer and bounded. Out-of-order or backpressured submissions fail
  explicitly, metrics record depth/high-watermark/processed/rejected/failed, silent drops remain zero, and graceful
  stop drains accepted work.
- `RuntimeMirrorService` becomes `LIVE` only after a full snapshot applies successfully. Disconnect marks it
  `STALE`; reconnect performs a full resync before returning to `LIVE` and records the authoritative snapshot
  fingerprint.
