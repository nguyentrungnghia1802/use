# Runtime architecture

## Target runtime topology

```text
JaCaMo JVM/process                                  USE JVM/process
------------------                                 ---------------
official JaCaMo project/platforms
  Jason + Bridge AgArch/listeners      contract     BridgeClient
  CArtAgO + controller/logger          <------->    ContractValidator
  ORA4MAS/Moise + NPL listeners                    RuntimeMirrorService
        \                                             RuntimeMapping V2
         SnapshotCoordinator                          RuntimeMutationEngine
         BridgeTransport SPI                          TraceIndex
                                                     RuntimeVerificationEngine
                                                     USE MSystem/MSystemState
```

JaCaMo is the execution/semantic authority. USE is an observational formal-verification mirror; it does not control JaCaMo execution.

## Runtime state machine

| State | Entry | Permitted behavior | Exit |
|---|---|---|---|
| `DISCONNECTED` | No authenticated session | No mutation or PRE/POST assurance | Connect/handshake |
| `NEGOTIATING` | Transport connected | Validate schema, versions, distribution, capabilities | Compatible -> `MODEL_SYNC`; otherwise fail |
| `MODEL_SYNC` | Session/generation known | Fetch/validate ModelSnapshot, compile MModel | Model revision accepted |
| `SNAPSHOT_SYNC` | Model ready | Buffer events, fetch/validate RuntimeSnapshot | Transactional replace succeeds |
| `LIVE` | Snapshot + watermarks accepted | Apply ordered idempotent events and verify | Gap/model change/disconnect |
| `RESYNC_REQUIRED` | Gap, overflow, drift or unknown identity | Quarantine deltas; request model/snapshot as needed | Successful replacement or failure |
| `STALE` | Authority unavailable | Read-only reports labelled stale; no live decision claim | Reconnect or stop |
| `STOPPED` | Explicit close | Reject all input | None |

## Validated snapshot algorithm

1. Attach every available listener and create bounded per-source buffers.
2. Capture start watermarks for each Jason agent source, CArtAgO logger/controller scope and Moise board/NPL source.
3. Enumerate/copy subsystem state into immutable DTOs.
4. Capture end watermarks and re-enumerate entity topology/identity sets.
5. Reject/retry if an unbuffered source changed, an identity set is inconsistent, a listener overflowed or the model revision changed.
6. Publish the accepted cut with start/end watermarks, capture interval and per-dimension completeness.
7. USE validates all references and fingerprints, materializes into a replacement state, then atomically swaps the mirror.
8. Replay buffered events whose source sequence is strictly after the accepted source watermark.

No global timestamp is used to fabricate order. A snapshot can be complete for one dimension and partial for another; required verification profiles specify the minimum capability set they accept.

## Runtime fact preservation and USE projection

The Bridge captures observable mission commitments, organizational-goal state, norm instances/lifecycle and scheme/group runtime-instance context independently of whether frozen V2/Runtime Mapping V2 can materialize them. Each fact retains canonical identity, session/generation, board/artifact incarnation, source evidence, snapshot/event provenance and completeness/capability status.

`RuntimeSnapshot` acceptance therefore has two outcomes per fact: faithfully materialized into the replacement `MSystemState`, or retained as `EVIDENCE_ONLY` in trace/runtime reports. There is no third path that coerces the fact into an existing V2 class/attribute/relation with a different meaning. Runtime events for evidence-only facts still advance Bridge evidence/history but do not invoke a fabricated USE mutation. OCL evaluation is enabled only for faithfully materialized dependencies; otherwise the relevant result is `INCONCLUSIVE`, `NOT_EVALUATED` or capability-blocked.

## Ordering, duplication and causality

- Each adapter serializes callback ingestion through a per-source monotonic `sourceSequence`.
- Event IDs make replay at-least-once and idempotent.
- Explicit `correlationId` links action/operation phases; `causationId` expresses an observed causal edge.
- Cross-source events without a causal edge form a partial order. The verification engine must not choose an arbitrary total order for semantic claims.
- Same sequence with different payload, sequence regression, or missing required predecessor causes quarantine and resync.
- Wall-clock time is diagnostic only; clock skew cannot resolve ordering.

## Backpressure and retention

- All queues are bounded by count and bytes.
- The Bridge advertises limits and current lag; USE acknowledges per-source watermarks.
- Coalescing is allowed only for an event kind whose contract declares it semantics-preserving, such as replaceable telemetry—not for operation/norm/role lifecycle.
- Overflow creates a durable `GAP` boundary and forces a snapshot. It never drops silently.
- Retention/resume tokens are transport concerns but must preserve contract IDs and source watermarks.

## Reconnect and resync

| Condition | Action |
|---|---|
| Brief disconnect, buffers retained, resume token valid | Resume same session/generation after acknowledged watermarks |
| Buffer gap or source capability changed | `RESYNC_REQUIRED`; replace RuntimeSnapshot |
| Bridge/JVM restart | New session; full model capability negotiation and snapshot |
| Static/dynamic model revision changed | Fetch/compile ModelSnapshot, then fetch matching RuntimeSnapshot |
| Drift fingerprint mismatch | Stop delta apply, export drift evidence, resnapshot |
| Event references unknown incarnation | Quarantine and resnapshot; never auto-create by name |

The existing `RuntimeMirrorService` subscribe-before-snapshot and replacement approach is retained conceptually, but its current `RuntimeSnapshot`/`RuntimeEvent` types must be upgraded with session, generation, model revision and per-source watermarks before cross-process use.

## Early separate-JVM proof

Phase D runs the JaCaMo Bridge and USE BridgeClient in separate JVMs over a minimal/test transport or process-neutral recorded/framed mechanism. It must prove ModelSnapshot compilation, RuntimeSnapshot replacement, RuntimeEvent application, no live/shared JaCaMo object or JaCaMo runtime class requirement on the USE semantic backend, new session after Bridge restart, old-session/generation rejection, basic reconnect/resnapshot and fail-closed schema/version mismatch. This proves the architecture boundary; it does not select a production transport.

Phase H later benchmarks/selects and hardens the production transport, including authentication, TLS where remote, bounded backpressure, resume/ack, security/fuzzing, packaging, performance and failure injection.

## PRE/POST verification

- PRE can be evaluated only when the action/operation request is correlated to an accepted state and no required source is stale/gapped.
- POST is evaluated after the matching completion/failure and all required state deltas up to the declared watermark are applied.
- If callback ordering cannot prove property deltas are visible at completion, POST waits for a reconciliation checkpoint or reports `INCONCLUSIVE`; it does not guess.
- Runtime verification observes and reports. It does not block or alter JaCaMo unless a future explicitly scoped control architecture is approved.

## Threading

- Listener callbacks perform only immutable copy/enqueue work; they do not call USE or block runtime threads on network I/O.
- Snapshot collection uses subsystem-safe public APIs and bounded retries.
- One Bridge serialization executor establishes order per source; one client mutation executor applies an accepted event stream transactionally.
- Shutdown detaches listeners and terminates executors with bounded timeouts. A thread leak is a failed gate.

## Security

- Default bind scope is local-only; remote exposure requires authenticated encrypted transport.
- No Java native deserialization, remote class loading, arbitrary source path access or reflective invocation from client payloads.
- Schema size/depth/string limits, decompression limits and canonical digest checks precede allocation/materialization.
- Secrets never enter ModelSnapshot, logs or evidence bundles; paths can be normalized/redacted while retaining a local trace mapping.
- Authorization separates read-model/read-runtime operations from any future control operation; this architecture defines read-only observation only.

## Performance and determinism gates

- Benchmark model extraction, snapshot bytes/time, peak queue memory, sustained event rate, resync time and USE mutation latency on Hello, Auction, House and a synthetic scale fixture.
- Establish budgets only from measurements; this audit invents no numeric target.
- Canonical entity ordering, normalized values and stable hashes make repeated unchanged snapshots byte/digest deterministic.
- Record/replay of one accepted snapshot plus event log must yield the same final fingerprint and verification report.

## Failure matrix

| Failure | Mirror behavior | Verification status |
|---|---|---|
| Unsupported schema/distribution | Refuse session | Unavailable |
| Partial optional capability | Accept only profiles not requiring it | Explicitly limited |
| Required listener missing | Snapshot-only mode if profile allows | Freshness-limited/inconclusive |
| Snapshot validation fails | Retry boundedly, then fail | No authoritative state |
| Queue overflow/gap | Stop apply and resync | Stale/inconclusive |
| Mutation rollback failure | Discard replacement state/session and rebuild | Failed, evidence retained |
| Authority disconnect | Freeze last state as stale | No current claim |

## Verdict

Cross-process runtime mirroring is `FEASIBLE_WITH_ADAPTER`. Its boundary must be proved in Phase D and productionized in Phase H. Correctness depends on explicit capability/completeness, faithful materialization status, partial ordering and resync boundaries. A single globally atomic cross-subsystem snapshot remains unavailable and is not claimed.
