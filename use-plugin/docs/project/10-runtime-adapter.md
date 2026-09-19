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

## 12. Phase 10 live connector evidence and contract

Pinned runtime APIs:

- Jason interpreter `3.3.0`;
- CArtAgO `3.1`;
- Moise `1.1` (which itself pins Jason `3.3.0` and CArtAgO `3.1`).

### 12.1 Jason

- Initial state is read from the live Agent belief base.
- Belief/goal deltas use `CircumstanceListener` and `GoalListener`.
- Action enter/result uses a custom `AgArch` (`act`/`actionExecuted`) and keeps one correlation ID across the
  action lifecycle. Connector connect/disconnect owns activation of that architecture bridge.
- Runtime identity is an explicit Agent-name-to-semantic-ID binding; one semantic trace may have both Jason and
  Moise runtime aliases.
- Outbound/inbound message methods are an explicit bridge surface only. Jason 3.3 does not expose mailbox changes
  through `CircumstanceListener`; automatic inbound message capture is therefore not claimed.

### 12.2 CArtAgO

- Discovery and authoritative snapshots use `ICartagoController.getCurrentArtifacts()` and `getArtifactInfo()`.
- Lifecycle, observable-property, signal, and operation events use the workspace `ICartagoLogger` callbacks.
- `OpId` supplies operation and Agent correlation. Enter, exit, and failure events retain the mapped operation,
  runtime operation, Agent identity, and the same correlation ID.
- Bindings are exact workspace/artifact/property/operation bindings. Unbound artifacts remain discoverable but do
  not emit mutations because no semantic trace can be proven.
- A bound observable property absent from an authoritative snapshot emits `OBS_PROPERTY_REMOVED`; the USE value is
  set to undefined during resync so a stale value is not reported as current.

The original `CartagoBasicContext.makeArtifact` test blocker was a fixture classloading problem, not connector
logic. CArtAgO 3.1's default factory loads the supplied name with `Class.forName` and instantiates it with
`Class.newInstance`; the fixture must therefore be a public top-level `Artifact` with a public zero-argument
constructor on the test runtime classpath. `CartagoBasicContext.makeArtifact` catches the underlying action failure
and rethrows a cause-less `CartagoException`, so a workspace logger was required to expose the real failure. The
runtime fixture now meets that classloading contract, and the real create/init/discover path is covered by the live
test.

### 12.3 Moise

- Authoritative state is read from a real `moise.oe.OE`: organisation, group instances, scheme instances, role
  players, mission players, and goal instance state.
- Moise OE 1.1 has no public state-change listener API. The supported delta mechanism is explicit controlled
  polling (`pollChanges`) with deterministic snapshot diffing; no synthetic organisation runtime is used.
- An unbound OE agent/group/scheme is still emitted with no semantic ID, so snapshot application quarantines it and
  prevents the mirror from claiming `LIVE`; it is not silently ignored.
- Mission commitment is tested with the actual Moise deontic check: the role-to-mission obligation exists in the
  real `OS`, otherwise Moise correctly rejects the commitment.
- `OEAgent.getObligations()`/`getPermissions()` expose derived permissions but not NPL norm activation,
  fulfilment, violation, or expiration lifecycle. `NORM_STATE_CHANGED` is therefore not emitted or claimed.

### 12.4 Composite synchronization and staleness

- `CompositeRuntimeConnector` combines the three connector snapshots and renumbers child events into one monotonic
  stream while preserving child event identity and correlation provenance.
- Initial synchronization subscribes before taking the authoritative snapshot, buffers concurrent deltas, applies
  the snapshot, then replays only post-snapshot events. `LIVE` is set only after this succeeds.
- Connector health can be refreshed explicitly. A disconnected child makes the composite unhealthy and the mirror
  transitions to `STALE`; stale data is not claimed as live.
- Reconnect always performs a full snapshot before returning to `LIVE`. The live Auction integration test removes
  a CArtAgO observable property while disconnected and verifies that full resync changes the USE value to undefined.
- Live connectors only mutate imported objects with exact trace bindings. Automatic creation of unbound dynamic
  runtime entities is intentionally unsupported because the runtime APIs do not provide enough evidence to invent
  a static semantic identity.

## 13. v1.0.1 workspace lifecycle

P1 is fixed in the production facade. Import, rebuild, and user-profile load build a
candidate workspace first and install it through one lifecycle operation. If a runtime
service exists, `RuntimeMirrorService.replaceWorkspace` drains/stops the old queue,
transfers only runtime keys whose semantic ID, target kind, and USE target still match,
replaces mutation and verification consumers together, and applies an authoritative
snapshot before returning `LIVE`.

A failed build leaves the published workspace and live consumers untouched. A failed
replacement snapshot transitions the mirror to `ERROR` and disconnects; it does not
continue with a mixed workspace. Late callbacks remain attached to the old stream and
cannot enter replacement consumers. In-flight operation correlations and historical
runtime report lists are workspace-local and intentionally end at the replacement
boundary.

## Phase 16 authority audit

The [project capability matrix](../research/jacamo_runtime_research/IMPLEMENTATION_RECONCILIATION.md) distinguishes observation from USE mutation. Intrinsic event kinds require their authoritative dimension; conflicts raise RUNTIME_AUTHORITY_CONFLICT. Jason actions are trace-only; CArtAgO owns artifact operation checkpoints. Moise state currently remains trace-only. Upstream API availability is not implemented mirror support.
