# JaCaMo Bridge and neutral contract design

## Decision

Use a hybrid of the first two audited options:

1. an independent Bridge module/process component compiled against official JaCaMo/Jason/CArtAgO/Moise dependencies; and
2. the official `jacamo.platform.Platform` hook plus an official Jason `AgArch` to insert it into a launched system.

Do not patch or fork JaCaMo core. Keep transport behind an SPI until payload size, latency, deployment and threat-model probes select it.

## Option assessment

| Option | Static access | Runtime/listener access | Process isolation | Core maintenance cost | Verdict |
|---|---|---|---|---|---|
| 1. Independent Bridge module on JaCaMo side | Official project/load APIs | Official subsystem APIs | Yes | Low | `FEASIBLE_WITH_ADAPTER` |
| 2. Official extension/hook | `Platform.setJcmProject` gives the parsed project | Platform lifecycle + registered `AgArch`/loggers/listeners | Yes when the hook hosts an endpoint | Low | `FULLY_FEASIBLE` as insertion mechanism |
| 3. Patch/fork JaCaMo core | Maximum | Maximum | Possible | High; version drift and thesis scope risk | Rejected: no proven blocker requires it |

## Logical modules

```text
JaCaMoBridgePlatform (official JaCaMo Platform)
  -> ProjectModelAdapter       (JCM + official Jason AST + official Moise OS)
  -> JasonObservationAdapter  (AgArch/listeners/snapshots)
  -> CartagoObservationAdapter(controller/logger/snapshots)
  -> MoiseObservationAdapter  (boards/NPL listener/snapshots)
  -> SnapshotCoordinator      (buffered validated cut)
  -> BridgeContractCodec      (versioned immutable DTOs)
  -> BridgeTransport SPI      (not selected by this audit)

USE BridgeClient
  -> ContractValidator
  -> NativeSemanticAdapter
  -> existing V2 planning/materialization/trace/OCL/verification pipeline
```

These are logical boundaries. The physical Maven/Gradle repository layout is an implementation decision gated by dependency and packaging spikes. The neutral contract must not depend on USE, EMF, Jason, CArtAgO, Moise or a specific transport library; contain live subsystem objects; or require a shared classpath between the JaCaMo and USE processes. Its canonical wire form must serialize/deserialize deterministically across a process boundary.

## Lifecycle

| Stage | Bridge action | Readiness requirement |
|---|---|---|
| Platform construction | No semantic extraction and no thread with unbounded lifetime | Configuration valid |
| `Platform.setJcmProject` | Capture the exact parsed project reference; build a load-only `ModelSnapshot` candidate | Official source/directive/OS parsing successful |
| `Platform.init` | Validate endpoint/auth config and register capabilities | No business semantics executed |
| Before agent creation | Register Bridge `AgArch` or add it to official agent parameters | Architecture-chain probe passes |
| `Platform.start` | Start endpoint, attach CArtAgO/Moise observers as authorities appear, buffer events | Endpoint can report `INITIALIZING` |
| Authorities ready | Produce/validate first RuntimeSnapshot and publish session/generation | Snapshot accepted |
| Steady state | Stream events; periodic reconciliation | Watermarks monotonic, no overflow/gap |
| `Platform.stop` | Stop intake, publish terminal boundary if possible, close endpoint/listeners | No state accepted after close |

Platform list order alone does not prove CArtAgO/Moise readiness. Each adapter has an explicit readiness/capability state; the coordinator waits or reports incomplete instead of guessing.

## Contract envelope

Every `ModelSnapshot`, `RuntimeSnapshot` and `RuntimeEvent` is wrapped in:

| Field | Meaning/evidence |
|---|---|
| `schemaVersion` | Semantic contract version; validated before payload use |
| `messageType` | One of the negotiated contract types |
| `bridgeBuild` | Bridge artifact/version/hash |
| `distribution` | Exact JaCaMo/Jason/CArtAgO/Moise/NPL versions and available code hashes |
| `projectKey` | Canonical project namespace; source-root URI is redacted/normalized for transport as configured |
| `modelRevision` | Digest-backed revision of the static semantic model |
| `sessionId` | Unique JaCaMo process/Bridge session |
| `generation` | Monotonic generation within the endpoint/session lineage |
| `messageId` | Unique immutable message ID |
| `producedAt` | Bridge clock timestamp; never used as sole ordering evidence |
| `capabilities` | Per-source snapshot/event/field coverage bitmap |
| `completeness` | `COMPLETE`, `PARTIAL` or `UNAVAILABLE`, with reason codes |
| `watermarks` | Per-subsystem/per-source sequence positions |
| `evidence` | API/source kind, canonical source IDs, adapter version and provenance references |
| `payloadDigest` | Canonical payload digest for replay/determinism checks |

Unknown required fields, unsupported major schema versions, invalid digests and capability contradictions are rejected before materialization.

## `ModelSnapshot`

```text
ModelSnapshot
  project                    merged official JaCaMo project declarations
  sources[]                  canonical URI, digest, kind, include/import relation
  agents[]                   declarations, instance policy, official Jason AST
  workspaces[]               declarations and configured artifacts
  organizations[]            official OS graphs and configured instances
  groupRoleCardinalities[]   exact (groupId, roleId, min, max) facts
  parentSubGroupCardinalities[] exact (parentGroupId, subGroupId, min, max) facts
  artifactDescriptors[]      only facts exposed by official descriptors/reflection
  relations[]                exact configured/correlated cross-dimensional facts
  unresolved[]               typed missing/unsupported facts; never inferred
  projectionProvenance[]     source object/API -> neutral fact -> V2 target
```

Field sources are the APIs inventoried in `03`–`06`. A complete load-only snapshot is complete only for declared/static dimensions. Dynamic artifact properties or instances are not silently included in that completeness claim.

Relation-scoped cardinalities remain separate facts even when endpoint display IDs repeat. A later V2 projection may be `SUPPORTED_SUBSET` or `REPRESENTATION_LOSS`; that status never alters the exact Bridge tuple. Conflicting contexts are not merged, selected by iteration order or rewritten as one global role/group cardinality.

`modelRevision` changes whenever a semantic descriptor needed by the USE `MModel` changes, for example a dynamic concrete artifact subtype/property. Runtime messages for a new revision cannot be applied until the USE side has accepted and compiled that revision.

## `RuntimeSnapshot`

```text
RuntimeSnapshot
  snapshotId
  modelRevision
  captureStartedAt / captureCompletedAt
  startWatermarks{} / endWatermarks{}
  validationAttempts
  agents[] / beliefs[] / goals[] / plans[]
  workspaces[] / artifacts[] / operations[] / properties[]
  groupBoards[] / schemeBoards[] / rolePlayers[]
  missionCommitments[] / organizationalGoalStates[] / normStates[]
  relationState[]
  sourceCompleteness[]
  stateFingerprint
```

Each entity carries the canonical identity from `10-identity-trace-contract.md`, its source authority and observed revision. Snapshot values are immutable copies. The fingerprint is over canonical ordering and normalized values, not Java object identity.

Mission commitments, organizational-goal state, norm instances/lifecycle and scheme/group runtime-instance context are mandatory contract facts whenever the corresponding capability is available. Each carries canonical identity, session/generation, board/artifact incarnation, source evidence, snapshot/event provenance and completeness/capability status. The contract also records projection status; `EVIDENCE_ONLY` is valid and must not be treated as missing data or silently discarded.

## `RuntimeEvent`

```text
RuntimeEvent
  eventId
  sessionId / generation / modelRevision
  subsystem / sourceId / sourceSequence
  observedAt
  kind
  entityId / relationId
  correlationId / causationId
  before / after / payload
  watermark
  completeness
  evidence
```

- `sourceSequence` is assigned at the first Bridge callback boundary and is monotonic for that source adapter.
- No total cross-subsystem order is asserted. The consumer respects per-source order plus explicit causation/correlation.
- Event kinds are closed/versioned; unknown kinds are quarantined and trigger capability negotiation or resync.
- `before` is optional only where the official callback does not expose it; omission is explicit.

## Snapshot/event handshake

1. Subscribe and buffer all available sources.
2. Capture start watermarks.
3. Copy Jason, workspace/artifact and organization-board state.
4. Validate entity sets and source watermarks; retry on an invalid cut.
5. Publish snapshot with end watermarks and completeness.
6. USE validates identity/model revision, replaces its mirror transactionally, and acknowledges.
7. Bridge replays only buffered events after each snapshot watermark.
8. Gap, duplicate conflict, buffer overflow or generation change forces quarantine and resync.

At-least-once delivery is acceptable because event IDs make application idempotent. Exactly-once delivery is not promised by this contract.

## Transport SPI and independent process

The contract supports request/response for capabilities/model/snapshot and a bounded event stream with acknowledgement/resume tokens. Candidate implementations may be local IPC, HTTP/streaming, WebSocket or gRPC, but no choice is made in this audit. Selection gates are:

- Java/USE packaging compatibility;
- authenticated local/remote deployment model;
- backpressure and resumable sequence support;
- canonical schema/code generation and version negotiation;
- maximum measured model/snapshot/event volume;
- firewall/TLS/secret handling;
- deterministic record/replay tests.

No transport may serialize live Java objects or enable arbitrary class deserialization.

The first process-boundary proof is not deferred to production transport selection. Phase D must run `JaCaMo + Bridge` and `USE + BridgeClient` in distinct JVMs using a minimal/test transport or process-neutral recorded/framed mechanism. That gate proves ModelSnapshot -> MModel, RuntimeSnapshot -> state replacement, RuntimeEvent application, no shared/live JaCaMo objects, session renewal and stale-event rejection, reconnect/resnapshot, and fail-closed schema/version mismatch. Phase H retains responsibility for benchmarking and selecting the production transport plus authentication, TLS where remote, bounded backpressure, resume/ack, security/fuzzing, packaging, performance and failure injection.

## Failure policy

- Unsupported source semantics: publish a typed unresolved fact; fail the affected model gate if it is required.
- Listener unavailable: capability false; snapshots may continue with an explicit reduced freshness guarantee.
- Snapshot cannot stabilize: return incomplete/failed; never publish it as authoritative.
- Stale generation/model revision: reject before mutation.
- Unknown entity/reference: quarantine event and request resync.
- Bridge disconnect: freeze verification mirror as stale, stop accepting PRE/POST decisions, reconnect with a new generation handshake.
- Faithfully captured fact with no target representation: retain it in trace/runtime evidence/reporting with `EVIDENCE_ONLY`; do not fabricate a V2 relation/attribute. A dependent OCL profile is `INCONCLUSIVE`, `NOT_EVALUATED` or capability-blocked.

## Feasibility verdict

An independent-process Bridge using official extension points is `FEASIBLE_WITH_ADAPTER`. Globally atomic state is `NOT_FEASIBLE_WITH_CURRENT_API`, but the buffered validated-cut protocol is sufficient for a sound, explicitly bounded verification mirror. No current finding requires a JaCaMo core patch.
