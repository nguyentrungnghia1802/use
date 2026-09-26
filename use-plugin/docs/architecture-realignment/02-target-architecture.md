# Target architecture understanding

## Current versus target

```text
CURRENT
files -> plugin-owned reconstruction -> semantic IR -> frozen V2 -> USE
live Java objects in USE process -> in-process connectors -> MSystemState

TARGET
official JaCaMo/Jason/CArtAgO/Moise APIs
  -> JaCaMo-side Bridge
  -> ModelSnapshot / RuntimeSnapshot / RuntimeEvent
  -> USE Bridge client + V2 Semantic Adapter
  -> JaCaMoSemanticModel / SemanticId / TraceIndex
  -> frozen V2 structural + runtime mappings
  -> USE MModel / MSystemState
  -> explicit OCL verification
```

The target is a formal verification mirror. JaCaMo executes the system and owns its semantics; USE mirrors declared and observed facts, applies explicit formal constraints, and reports results. USE does not control JaCaMo execution or reinterpret unavailable semantics.

## Authority by layer

| Layer | Authority | Role |
|---|---|---|
| JCM/project | `JaCaMoProjectParser` + `JaCaMoProject` at exact JaCaMo revision | Project declarations, include merge, instance declarations/configuration |
| Agent program/runtime | Jason `Agent`, AST, `PlanLibrary`, `BeliefBase`, `TransitionSystem`, listeners/`AgArch` | Parsed program and live agent state/lifecycle |
| Environment | CArtAgO workspace/controller, artifact IDs/info/descriptors, logger | Live workspaces, artifact incarnations, operations, observable values/events |
| Organisation | Moise `OS` object graph and JaCaMo ORA4MAS boards/NPL engine | Static SS/FS/NS and launched organisational state/norm lifecycle |
| Contract | Bridge | Loss-aware serialization, evidence, identity, capabilities, ordering; never semantic invention |
| Vocabulary | Ecore V2 | Defines the 21 in-scope concept types and features expected by the adapter/mapping |
| Structural transformation | Mapping V2.2 | Deterministic V2-to-USE classes/attributes/associations/compositions/projections |
| Runtime transformation | Runtime Mapping V2 | Authorized normalized mutations against exact trace targets |
| Verification | Authored core/case/user OCL | Formal policy over the mapped model/state |

## Roles of the main artifacts

- **Ecore V2** is a frozen semantic vocabulary and adapter conformance target. It is not a JCM/ASL/Java/XML parser and does not need transport/session fields.
- **Structural Mapping V2.2** maps all V2 classifiers/features into USE, including enums, opposite deduplication, required/default metadata, order ranks and verified projections.
- **Runtime Mapping V2** maps normalized runtime event kinds to exact USE mutations. It does not discover source meaning.
- **Semantic IR** remains a useful dependency firewall. `JaCaMoSemanticModel` is populated by the Bridge adapter, so USE mapping/OCL code does not depend on JaCaMo libraries.
- **Bridge** reads official objects, assigns lossless canonical/evidence records, creates validated snapshot/event contracts and lives with the launched JaCaMo version. Exact Bridge capture is distinct from the fidelity of any frozen-V2/USE projection.
- **ModelSnapshot** carries project/static program/specification information and capability gaps. It may be produced in load-only mode.
- **RuntimeSnapshot** is the authoritative state baseline for an active session/generation and is used for initial materialization and resync.
- **RuntimeEvent** is a delta with identity, ordering, correlation and optional PRE/POST boundaries.
- **MModel** is compiled before business execution from frozen V2 plus every static/enriched descriptor actually available. It may be rebuilt/versioned when a genuinely new runtime type/projection appears; values never belong here.
- **MSystemState** is materialized from a declared offline plan only when labelled non-runtime, or from the faithfully representable subset of an authoritative `RuntimeSnapshot` for live verification. Events mutate it after the snapshot cut. Captured facts with no faithful target representation remain in Bridge evidence/reporting and are not fabricated as USE objects, links or attributes.
- **OCL** is explicit policy. Moise norms remain model/state facts unless a human-authored constraint intentionally refers to them.
- **Trace/identity** connects official IDs and provenance to Bridge IDs, `SemanticId`, V2 mapping decisions, and USE model/state elements.

## Logical modules

The audit fixes logical boundaries, not the final repository/build layout:

1. `bridge-contract`: neutral immutable DTO/schema definitions, canonical encodings, validation and compatibility tests; no USE, EMF or JaCaMo dependency, no live platform object, and no shared-classpath assumption. Its wire form must serialize/deserialize deterministically across process boundaries.
2. `bridge-jacamo`: compiled against the exact launched JaCaMo/Jason/CArtAgO/Moise/NPL versions; official load-only extractors, platform/AgArch hooks, snapshot coordinator and transport SPI.
3. `use-plugin` Bridge client: contract decoder, capability negotiation, `BridgeSemanticAdapter`, `BridgeRuntimeConnector`, trace and diagnostics; no live JaCaMo object dependency in production mode.
4. Existing USE backend: semantic IR, frozen mappings, materialization, OCL and verification.

Candidate implementation paths are named in the roadmap; they are not created in this audit. The physical choice between Maven modules in `use` and a separately built Bridge distribution is a Phase B build-isolation gate.

## Lifecycle

| Point | Available facts | Allowed output |
|---|---|---|
| Official parse + `parserFinished` | JCM declarations/imports/instances | Project portion of ModelSnapshot |
| `setupDefault`, directive/source-path registration, official Jason/Moise load | Parsed agent AST/program and OS specifications; reflectable artifact classes | Complete load-only ModelSnapshot with explicit gaps |
| JaCaMo `create`/platform initialization | Workspaces/artifact instances/boards/agents constructed | First authoritative RuntimeSnapshot after readiness barrier |
| JaCaMo `start`/business execution | Live deltas | RuntimeEvent stream and periodic/resync snapshots |

There is a clear post-parse/pre-create point in `JaCaMoLauncher.init`. There is no general built-in “all subsystems initialized but no meaningful execution” callback: custom platforms start before Jason agents, but relative platform order and artifact creation differ. The Bridge therefore establishes an explicit readiness barrier and captures on request; it must not assume `Platform.start()` itself is a global quiescent point.

## MModel and MSystemState strategy

Before runtime, build the stable 21-class V2 substrate and all exact static entities/relations from ModelSnapshot. Add concrete artifact subclasses/operations only when official reflection/descriptor evidence satisfies VP001/VP003. Do not fabricate properties whose definition exists only in artifact initialization.

For offline analysis, a declared-state plan may represent JCM initial declarations, but its report must say `DECLARED_NOT_RUNTIME`. For live analysis, create/replace the MSystemState from a RuntimeSnapshot, record its cut/watermarks, then accept only events from the same session/generation after the cut.

Runtime capture and USE materialization are separate capabilities. Mission commitments, organizational-goal state, norm instance/lifecycle and scheme/group runtime-instance context are always preserved when officially observable, with identity, board incarnation, session/generation, evidence, provenance and completeness. A selected OCL profile may read one of those facts only when an approved adapter has faithfully materialized that dependency into `MSystemState`; otherwise the result is `INCONCLUSIVE`, `NOT_EVALUATED` or capability-blocked.

## Process-boundary proof milestones

- Phase B proves that the neutral contract is deterministic and independent of USE, EMF and all live JaCaMo subsystem types.
- Phase D must run Process A (`JaCaMo + Bridge`) and Process B (`USE + BridgeClient`) in separate JVMs over a minimal/test transport or process-neutral recorded/framed mechanism. It proves model, snapshot and event flow, restart/session rejection, reconnect/resnapshot and fail-closed schema mismatch.
- Phase H benchmarks and selects the production transport, then adds authentication, TLS where remote, bounded backpressure, resume/ack, fuzzing, packaging, performance and failure injection. The audit does not select gRPC, WebSocket, HTTP or any other production mechanism.

## Failure policy

- Unsupported capability: explicit `UNAVAILABLE`/`SUPPORTED_SUBSET`, not an empty value misrepresented as complete.
- Unknown identity, stale generation, sequence gap, illegal value, or untraced target: quarantine and mark mirror stale; do not mutate USE.
- Snapshot mismatch/drift: discard candidate cut and retry or reconnect/resync.
- API version mismatch: fail startup before producing an authoritative snapshot.
- OCL projection unavailable: constraint is capability-blocked with evidence; never weaken it silently.

## Thesis alignment

This direction sharpens the thesis contribution: a versioned semantic bridge from executable multi-agent platforms to a traceable USE verification mirror. It separates platform semantics, neutral observation, metamodel correspondence, runtime state materialization and formal policy. That boundary is more defensible than claiming a second parser is equivalent to the platform that actually executes the source.
