# USE component disposition

## Phase 10 final production disposition (2026-09-26)

The migration gate is now closed. `DefaultJaCaMoFacade` has one production
semantic authority: the validated Bridge contract. The legacy compatibility
flag was exercised as the Phase 9 rollback rehearsal and then retired; requesting
it now fails with `SEMANTIC_AUTHORITY_REMOVED:legacy-compatibility`. There is no
fallback from Bridge configuration, transport, distribution, project identity,
schema, capability, snapshot, or materialization failure.

Historical source remains in this repository so the audited parser and connector
tests, fixtures, goldens, and three known failures remain reproducible. It is not
in the release artifact. `maven-jar-plugin` exclusions plus
`LegacyAuthorityPackagingIT` prove that the following authority classes and their
support call path are absent from the shipped JAR:

- custom JCM/Jason/CArtAgO/Moise extraction: `StaticProjectImporter`,
  `JcmSemanticParser`, `JasonSourceParser`, `CartagoSourceExtractor`,
  `MoiseXmlParser`, `SemanticResolver`, `JcmLexer`, `JcmProjectLoader` and their
  discovery/intermediate helpers;
- in-process runtime authority: Jason/CArtAgO/Moise concrete connectors,
  registry/monitor bindings, `CompositeRuntimeConnector`,
  `SyntheticRuntimeConnector`, `RuntimeConnector`, `RuntimeMirrorService`,
  `RuntimeService`, and `RuntimeSubscription`.

One-to-one replacements are the official `JaCaMoProject`/Jason AST/Moise OS
adapters and `SnapshotCoordinator` in `jacamo-bridge-jacamo`, the neutral
contract in `jacamo-bridge-contract`, and the validated Bridge client/mirror/
runtime projector in `use-plugin`. `OfficialAdapterTest`, `ContractTest`,
`CanonicalCasesBridgePipelineTest`, `SeparateJvmBridgeTest`,
`LocalTcpBridgeTransportTest`, `DefaultBridgeAuthorityTest`, and the packaging
IT are the replacement proof. Production call-path scans find no caller of the
excluded implementations.

The following foundations remain shipped because they are target-side formal
verification machinery, not duplicate source authority: semantic IR,
`OrderEvidenceLoader`, frozen mapping/runtime-mapping loaders, transformation
and instance planners, direct/text materialization, trace, runtime mutation,
OCL, verification, diagnostics, and UI integration. Frozen resources and
historical evidence were not modified.

## Decision vocabulary

No component is immediately deleted. `DEPRECATE_AFTER_MIGRATION` and `REMOVE_AFTER_PROVEN_REPLACEMENT` require shadow parity, case-study gates and explicit approval. The current foundation is separated from the misaligned frontend boundary.

## Required component-by-component classification

| Component | Current role | Target role | Disposition | Why / replacement | Exact dependencies | Migration risk | Tests affected |
|---|---|---|---|---|---|---|---|
| `StaticProjectImporter` | Orchestrates custom discovery/parsing/resolution into semantic IR | Orchestrate `BridgeClient` ModelSnapshot validation and native adapter | `REFACTOR` | Keep orchestration contract; replace source path with Bridge semantic contract | Bridge contract/client, `JaCaMoSemanticModel`, diagnostics | High: central call path and dirty current edits | importer, facade, golden and multicase tests; new contract fixtures |
| `JcmProjectLoader` / discovery | Finds JCM/source inputs | Project selector/config only; JaCaMo resolves imports | `DEPRECATE_AFTER_MIGRATION` | Official `JaCaMoProjectParser` owns JCM semantics | JaCaMo Bridge project adapter | Medium: CLI/UI path behavior | loader/discovery tests; official `uses` parity |
| `JcmLexer` / `JcmSemanticParser` | Reimplements JCM grammar and semantic extraction | Legacy shadow comparator only | `REMOVE_AFTER_PROVEN_REPLACEMENT` | Replaced by official `JaCaMoProjectParser` -> `JaCaMoProject` adapter | Bridge project adapter | High: hidden supported-subset assumptions | parser tests reclassified as legacy; official Hello/Auction/House parity |
| `JasonSourceParser` | Uses Jason parser selectively, masks directives, normalizes into IR | Official Jason AST adapter on Bridge side | `REPLACE` | Use launcher-equivalent official directives/includes and AST directly | Jason 3.3.2 API, Bridge contract | High: include/directive and body-order drift | Jason fixtures, includes, source provenance, AST digest |
| `CartagoSourceExtractor` | JDK AST scan of artifact Java source | Optional labelled provenance/enrichment tool only | `DEPRECATE_AFTER_MIGRATION` | Runtime descriptor/controller/logger become authority; source cannot prove dynamic initialization | CArtAgO 3.1 reflection/runtime APIs | High: property/operation completeness changes | Java extractor tests retained as diagnostic; descriptor parity/new dynamic tests |
| `MoiseXmlParser` | DOM reconstruction of OS XML | Official `OS.loadOSFromURI` object-graph adapter on Bridge side | `REPLACE` | Moise loader/object model owns schema/semantics | Moise 1.1 OS API | High: order/cardinality/norm expression representation | OS fixtures and official Auction/House canonical graph tests |
| `SemanticResolver` | Resolves cross-file/cross-dimensional symbols | Contract/reference validation boundary | `REFACTOR` | Resolve canonical Bridge IDs only; no frontend semantic reconstruction | identity contract, binding validator | High | ambiguity/stale/missing-reference tests |
| `ExactSemanticResolver` | Exact resolver implementation | Exact Bridge ID and binding resolver | `KEEP_WITH_ADAPTER` | Exact/fail-closed policy remains correct; change its input namespace | `BridgeEntityId`, `SemanticId` | Medium | exact, zero/multiple and stale generation tests |
| `binding.json` path | Supplements missing relations | Explicit, schema-validated augmentation with canonical endpoint selectors | `KEEP_WITH_ADAPTER` | Needed only for real API/evidence gaps, never parser workaround | model revision, identity and provenance schema | High if stale bindings silently apply | schema, uniqueness, drift and negative tests |
| `JaCaMoSemanticModel` | Neutral internal aggregate | Native adapter output conforming to frozen V2 | `KEEP_WITH_ADAPTER` | Valuable decoupling layer; populate from contract DTOs and carry completeness/provenance | Bridge contract, `SemanticId` | Medium | semantic IR and deterministic serialization tests |
| `SemanticId` | Deterministic source identity | Canonical Bridge-aware structured identity | `REFACTOR` | Current name/path identity lacks session/generation/incarnation | identity contract | High | collision, recreation, round-trip tests |
| extraction/model records | Carry normalized concepts and provenance | Transport-neutral/native model DTO boundary | `KEEP_WITH_ADAPTER` | Reuse concepts but add evidence/completeness/version fields where necessary | contract schema | Medium | record validation/serialization tests |
| `TransformationPlanner` | Plans V2 classes/associations/projections | Same role against Bridge-derived IR/model revision | `KEEP_WITH_ADAPTER` | Backend planning is sound and source-agnostic | frozen Mapping V2.2, semantic model | Medium | planning golden, projection and deterministic order tests |
| `VerificationSemanticLayer` | Applies verification-profile selection/closure | Same role; consume source completeness and model revision | `KEEP_WITH_ADAPTER` | Valid abstraction independent of parser authority | verification profile V2 | Low/medium | profile closure and current failing closure regression |
| `InstancePlanner` | Builds initial USE object/link plan | Build declared/offline plan or authoritative snapshot plan explicitly | `KEEP_WITH_ADAPTER` | Planning reusable; must distinguish non-runtime from runtime state | model revision, identity/trace | Medium | materialization, opposites and snapshot tests |
| text backend/materialization | Generates deterministic USE model/state commands | Preserve as diagnostic/reference backend | `KEEP` | Not coupled to JaCaMo parsing | USE syntax and planners | Low | golden output digests after intentional source parity approval |
| `DirectUseBackend` | Mutates USE API directly | Transactionally materialize accepted model/snapshot/events | `KEEP_WITH_ADAPTER` | Correct backend boundary; add revision/generation guard | USE 7.5.0 API, planners | High: rollback and partial apply | direct backend, transaction, invalid event tests |
| `TraceBuilder` / `TraceIndex` | Bidirectional source/V2/USE trace | Extend chain to Bridge IDs, relation IDs and evidence | `KEEP_WITH_ADAPTER` | Thesis-critical reusable foundation | identity contract, USE identities | High | round-trip, completeness and collision tests |
| structural mapping loader/validator | Loads Mapping V2.2 | Unchanged frozen mapping authority | `KEEP` | Independent of source frontend | frozen mapping/schema hashes | Low | V2 mapping/freeze gates |
| projections/order evidence | Represents verified mappings not native in Ecore | Same role with Bridge provenance | `KEEP_WITH_ADAPTER` | Needed for V2.2 fidelity; change source evidence ingestion only | mapping, provenance | Medium | projection/order evidence tests |
| `RuntimeMapping` | Maps runtime event targets to USE mutations | Map versioned Bridge contract kinds/IDs | `KEEP_WITH_ADAPTER` | Frozen targets remain valid; input validation expands | Runtime Mapping V2, identity, capabilities | High | mapping schema, stale/unknown event tests |
| `RuntimeMutationEngine` | Applies runtime mutations/rollback | Same, guarded by session/generation/model revision | `KEEP_WITH_ADAPTER` | Existing transaction semantics are valuable | direct backend, trace, mapping | High | idempotency, rollback, pre/post tests |
| `RuntimeMirrorService` | Subscribe-before-snapshot, buffer, replace/resync | Bridge-client state machine and validated-cut consumer | `REFACTOR` | Core algorithm remains; current DTO lacks session/generation/vector watermarks | `BridgeClient`, new contract, mutation engine | High | races, gaps, backpressure, reconnect/resync |
| `JasonRuntimeConnector` | Wraps caller-supplied in-process Jason objects behind `jacamo:` URI | Observation logic moves to Bridge; USE receives DTOs | `MOVE_TO_BRIDGE` | In-process object coupling prevents process independence | Jason API, Bridge AgArch | High | connector tests split into Bridge adapter and client contract tests |
| `CartagoRuntimeConnector` | Wraps live CArtAgO objects in USE process | Observation/enumeration moves to Bridge | `MOVE_TO_BRIDGE` | CArtAgO runtime authority belongs with JaCaMo process | CArtAgO controller/logger | High | workspace/artifact snapshot/logger tests |
| `MoiseRuntimeConnector` | Wraps in-process board sources | Observation/NPL adapter moves to Bridge | `MOVE_TO_BRIDGE` | Board identity/listeners belong with runtime authority | ORA4MAS/NPL API | High | board/norm lifecycle tests |
| `CompositeRuntimeConnector` | Combines three in-process connectors | Transport-neutral `BridgeClient` session | `REPLACE` | Composition happens in Bridge SnapshotCoordinator; USE consumes one validated contract | transport SPI/client | High | capability, ordering and failure injection tests |
| runtime codecs/validators | JSON/event validation for current DTO | Versioned neutral contract codec/validator | `REFACTOR` | Preserve fail-closed discipline; schema changes materially | contract schema, canonical digest | Medium/high | schema compatibility, fuzz and security tests |
| `ConstraintExtractor` / expression parser | Extracts custom/source constraints | Only explicit supported inputs; never derive norms automatically | `KEEP_WITH_ADAPTER` | Useful, but input provenance must be separated | semantic model/provenance | Medium | origin and unsupported-expression tests |
| `OclGenerator` / `OclProfileLoader` | Generates/loads core/case OCL | Same, bound to model/profile revision | `KEEP` | Backend verification foundation remains correct | frozen OCL/profile artifacts | Low | hash/profile/compile gates |
| `ConstraintRegistry` / dependency index | Registers and scopes constraints | Same with Bridge entity IDs and completeness gates | `KEEP_WITH_ADAPTER` | Needed for incremental verification | trace, model revision | Medium | closure/dependency/invalidation tests |
| `DefaultVerificationService` | Static verification | Same against Bridge-derived MModel/MSystemState | `KEEP_WITH_ADAPTER` | Source-independent verification engine | USE runtime, registry | Medium | full reports and attribution tests |
| `RuntimeVerificationEngine` / history verifier | PRE/POST/runtime verification | Same, driven by correlated Bridge events and accepted snapshots | `KEEP_WITH_ADAPTER` | Correct observer role; must reject gaps/stale sessions | runtime mirror, trace, OCL | High | operation lifecycle, history and resync tests |
| diagnostics/report exporters | Emits evidence/report output | Add contract version/capability/source completeness | `KEEP_WITH_ADAPTER` | Existing observability remains useful | evidence model | Low/medium | stable schemas and redaction tests |
| Workbench UI/action layer | Drives import/runtime/verification | Select endpoint/project, show readiness/gaps/revisions and reports | `KEEP_WITH_ADAPTER` | UI should not host semantic logic | facade/client/services | Medium | UI smoke/error-state tests |
| legacy parser fixtures/goldens | Validate current supported subset | Frozen comparison oracle during shadow migration | `HISTORICAL_ONLY` then `TEST_ONLY` | Required to detect intentional differences, not future authority | legacy frontend | Low | keep immutable until replacement accepted |

## Components to add

`ADD` is an implementation action rather than one of the disposition states for existing code. These additions are required; their physical module layout is finalized by the Phase B dependency spike.

| New component | Current role | Target role | Action | Why / replacement | Exact dependencies | Migration risk | Required tests |
|---|---|---|---|---|---|---|---|
| neutral Bridge contract/schema | Absent | Versioned `ModelSnapshot`, `RuntimeSnapshot`, `RuntimeEvent`, envelope, capability and completeness DTOs | `ADD` | Decouple JaCaMo authority from USE and transport | JDK + approved neutral codec/schema only | High: schema can accidentally leak implementation types | round-trip, canonical digest, compatibility, fuzz/limits |
| `JaCaMoBridgePlatform` | Absent | Official JaCaMo insertion/lifecycle and endpoint host | `ADD` | Replaces unofficial/in-process bootstrap coupling | `jacamo.platform.Platform`, Phase B contract | High: lifecycle/readiness | launcher lifecycle, failure/stop, no core patch |
| `BridgeAgArch` | Absent | Agent incarnation plus action start/completion observation | `ADD` | Gives exact Jason lifecycle/correlation through official architecture API | Jason `AgArch`, `RuntimeServices` | High: chain order can affect behavior | dynamic agents, delegation, success/failure, cleanup |
| project/Jason/CArtAgO/Moise adapters | Fragmented in USE custom frontend/connectors | Convert official objects/callbacks to neutral facts | `ADD` | Moves authority-bound work beside JaCaMo | exact subsystem versions/APIs | High: completeness/API drift | API drift, official fixture and negative capability tests |
| `SnapshotCoordinator` | Partial algorithm in USE mirror only | Buffer-first validated cross-subsystem cut with watermarks | `ADD` | No subsystem offers a global atomic snapshot | official snapshot/listener adapters | Critical | concurrent mutation, retry, overflow, partial capability |
| `BridgeTransport` SPI | Absent | Neutral request/stream abstraction | `ADD` | Preserve semantic contract while deferring evidence-based transport choice | contract only | Medium | in-memory/recorded conformance; later production transport suite |
| USE `BridgeClient` | Absent | Handshake/model/snapshot/event client with acknowledgement/resume | `ADD` | Replaces in-process connector composition | contract/selected transport | High | schema/version/auth/gap/reconnect tests |
| `ContractValidator` | Current runtime validator is DTO-specific | Validate envelope, identity, revision, references, capabilities and limits before mutation | `ADD` | Cross-process input is untrusted and versioned | contract schema, identity rules | Critical | malformed/conflicting/stale payload and resource-limit tests |
| `NativeSemanticAdapter` | Absent | Map accepted ModelSnapshot to `JaCaMoSemanticModel` without source reconstruction | `ADD` | Lets the backend remain source-agnostic | semantic IR, exact resolver | High | semantic parity, unresolved relation, deterministic model tests |
| shadow semantic comparator | Absent | Compare legacy and official paths by canonical facts/evidence | `ADD` | Enables reviewed replacement rather than blind golden changes | both frontends, canonical identity | Medium | mutation sensitivity, deterministic diff, known delta fixtures |

## Architectural diagnosis

### Foundation valid

Frozen V2, Mapping V2.2, Runtime Mapping V2, semantic IR shape, planners, projections, materialization, trace, OCL profiles and verification engines are a coherent formal-verification foundation.

### Architectural boundary misaligned

The static frontend currently reconstructs JCM/Moise semantics and CArtAgO descriptors from text/source while JaCaMo's official objects own those meanings. Runtime connectors require live objects in the USE process and therefore contradict the required independent JaCaMo process boundary.

### Implementation reusable

Most code after `JaCaMoSemanticModel` and most mutation/verification machinery needs a new data source and stronger identity/version guards, not replacement.

### Frontend needs replacement

Custom JCM/Moise parsing, source-authoritative CArtAgO extraction and in-process connector composition are replaced only after the official Bridge path proves parity and broader case fidelity. This is staged migration, not a claim that all existing implementation is wrong.

## Exhaustive current source-class ledger

The following is an exhaustive ledger of the **165** production Java classes in
`src/main/java/org/tzi/use/plugins/jacamo` in the audited working tree. A row's
disposition applies to every class explicitly listed in that row, except where a
more specific row names an exception. This closes the inventory at class level
without pretending that every value-object needs a different architecture.

`OrderEvidenceLoader` is an existing untracked working-tree class supplied before
this audit; it was read for classification and was not modified. Its disposition
is based on its current exact use of schema-validated, source-hash-checked order
evidence—not on an assumption about an eventual Bridge implementation.

| Source package / exact classes | Disposition | Target role and rationale | Test migration |
|---|---|---|---|
| root: `DefaultJaCaMoFacade` | `REFACTOR` | Select explicit legacy/Bridge mode, validate capabilities and remove silent source-authority fallback. | facade/import lifecycle and Bridge-unavailable tests |
| root: `JaCaMoFacade`, `JaCaMoPlugin`, `JaCaMoStatusAction`, `JaCaMoStatusCommand`, `JaCaMoWorkbenchAction` | `KEEP_WITH_ADAPTER` | Keep the public/plugin/UI integration seam; surface endpoint readiness, revision and incompleteness. | plugin packaging and UI readiness/error tests |
| root: `SkeletonJaCaMoFacade` | `HISTORICAL_ONLY` | Retain only while the legacy facade contract needs a fallback/test seam; do not make it a Bridge fallback. | legacy facade behavior and migration-config tests |
| `binding`: `BindingEntry`, `BindingFile`, `BindingStore` | `KEEP_WITH_ADAPTER` | Preserve as exact, schema-validated augmentation/provenance after canonical endpoint migration. | binding schema, zero/one/multiple match and stale model-revision tests |
| `constraint`: `ConstraintExpressionParser`, `ConstraintExtractor`, `ConstraintSpec`, `Expression`, `TranslationStatus`, `TypeEnvironment` | `KEEP_WITH_ADAPTER` | Keep explicit constraint extraction/translation subsets; add Bridge provenance and retain norm/OCL separation. | source provenance, unsupported expression and no-auto-deontic tests |
| `diagnostics`: `Diagnostic`, `Phase`, `Severity` | `KEEP` | Stable diagnostic vocabulary; extend by additive codes only if approved. | evidence/report schema and severity routing tests |
| `extraction`: `CartagoSourceExtractor` | `DEPRECATE_AFTER_MIGRATION` | Retain only as labelled source-provenance/enrichment/shadow tool; CArtAgO runtime descriptors become authority. | extractor regression kept as diagnostic plus descriptor parity tests |
| `extraction`: `JasonSourceParser`, `JcmSemanticParser`, `MoiseXmlParser` | `REPLACE` | Replace authority path with official Jason/JCM/Moise object adapters; retain old tests for shadow comparison. | official API parity and canonical Hello/Auction/House tests |
| `extraction`: `StaticProjectImporter`, `SemanticResolver` | `REFACTOR` | Convert from reconstruction/orchestration to ModelSnapshot adaptation and exact canonical-reference validation. | importer contract, unresolved-reference and shadow-diff tests |
| `extraction`: `ElementDraft`, `ExtractionContext`, `ImportResult`, `XmlSourcePositions` | `KEEP_WITH_ADAPTER` | Reuse only as neutral intermediate/provenance support after removing XML as semantic authority. | DTO/provenance and source-span tests |
| `extraction`: `OrderEvidenceLoader` | `KEEP_WITH_ADAPTER` | Preserve its exact schema/hash/membership validation for frozen order projections; Bridge supplies canonical IDs/provenance. | order-evidence schema, stale hash and exact membership tests |
| `mapping`: `ActiveBaseline`, `MappingException`, `MappingLoader`, `MappingModel`, `StructuralUseGenerator`, `TargetAssociationSpec`, `TargetAttributeSpec`, `TargetClassSpec`, `TargetOperationSpec`, `TransformationPlan`, `TransformationPlanner`, `UseNameAllocator`, `V2MappingValidator` | `KEEP` | Frozen V2 structural mapping/planning remains source-agnostic. | existing V2/mapping/hash/planning determinism gates |
| `mapping`: `OrderNavigationBinding`, `OrderProjectionPlanner`, `OrderProjectionSpec`, `ProjectionDiagnostic` | `KEEP_WITH_ADAPTER` | Keep target-only ordered projections while carrying Bridge/source evidence rather than parser-derived order. | projection/order evidence and opposite consistency tests |
| `materialization`: `DirectUseBackend`, `InstancePlan`, `InstancePlanner`, `LinkPlan`, `MaterializationException`, `ObjectPlan`, `TextBackend` | `KEEP_WITH_ADAPTER` | Existing planners/backends materialize accepted model/snapshot revisions; add transactional version guards. | text/direct parity, rollback, snapshot replacement and golden-diff tests |
| `ocl`: `OclGenerator`, `OclProfileLoader` | `KEEP` | Frozen OCL/profile mechanics remain valid; only bind reports to Bridge revisions/capabilities. | OCL hash, compilation and profile-selection tests |
| `project`: `ImportService`, `JcmProjectLoader`, `ProjectDiscoveryResult`, `ProjectEdge`, `ProjectEdgeKind`, `ProjectGraph`, `ProjectRoot`, `SourceFile`, `SourceKind`, `SourceSpan` | `DEPRECATE_AFTER_MIGRATION` | Retain configuration/project-selection and legacy source graph during shadow mode; official JCM/import/directive semantics replace it as authority. | JCM `uses` and directive parity; legacy discovery isolated |
| `project`: `JcmLexer` | `REMOVE_AFTER_PROVEN_REPLACEMENT` | The lexer is a custom JCM grammar authority and is removed only after official parser parity and approval. | legacy parser tests archived as shadow fixtures |
| `resolution`: `ExactSemanticResolver`, `ResolutionRequest`, `ResolutionResult` | `KEEP_WITH_ADAPTER` | Preserve exact/fail-closed resolution policy, switching to Bridge canonical IDs and version scopes. | ambiguity, relation-evidence, stale-generation and binding tests |
| `runtime`: `CartagoRuntimeAccess`, `CartagoRuntimeConnector`, `JasonMonitorAgArch`, `JasonRuntimeConnector`, `JasonRuntimeConnectorRegistry`, `MoiseBoardSnapshotSource`, `MoiseNormativeSnapshot`, `MoiseRuntimeBinding`, `MoiseRuntimeConnector`, `OfficialCartagoRuntimeAccess` | `MOVE_TO_BRIDGE` | These touch live subsystem objects; retain/adapt their observations beside the JaCaMo process rather than passing objects into USE. | official Bridge adapter/lifecycle/API drift tests |
| `runtime`: `CompositeRuntimeConnector`, `RuntimeConnector`, `RuntimeService`, `RuntimeSubscription` | `REPLACE` | Replace in-process connector composition with one versioned BridgeClient/transport session; retain only test adapters until proven replacement. | contract handshake, capability, reconnect and no-live-object tests |
| `runtime`: `SyntheticRuntimeConnector` | `TEST_ONLY` | Preserve deterministic event/snapshot injection for client and mutation tests, not production authority. | record/replay, malformed input and failure-injection tests |
| `runtime`: `CartagoArtifactBinding`, `OrderProjectionRuntimeBinding`, `OrderRuntimeBindingContract`, `RuntimeBindingContract`, `RuntimeIdentity`, `RuntimeTrace`, `TraceRuntimeTargetAdapter`, `V2RuntimeBindingAdapter` | `KEEP_WITH_ADAPTER` | Retain binding/trace concepts; replace local-name identity with canonical Bridge entity/relation/session/generation identity. | identity round-trip, binding uniqueness and stale-event tests |
| `runtime`: `ConnectorCapability`, `ConnectorState`, `DriftResyncPolicy`, `MirrorState`, `QueueMetrics`, `RuntimeRetention` | `REFACTOR` | Generalize to contract capability/completeness, source watermarks, acknowledgement and explicit gap/resync states. | capability negotiation, backpressure, gap and resync tests |
| `runtime`: `MutationResult`, `MutationStatus`, `OperationRuntimeOutcome`, `RuntimeDriftDifference`, `RuntimeDriftReport`, `RuntimeMappingException`, `RuntimeQueueBackpressureException` | `KEEP_WITH_ADAPTER` | Preserve result/diagnostic value types, adding session/model-revision/evidence fields where needed. | report/error schema and fault-injection tests |
| `runtime`: `OrderedRuntimeEventQueue`, `RuntimeEvent`, `RuntimeEventCodec`, `RuntimeEventKind`, `RuntimeEventObserver`, `RuntimeEventValidator`, `RuntimeSnapshot` | `REFACTOR` | Upgrade current protocol types to neutral event/snapshot envelope with immutable identity, generation, model revision, source sequences and watermarks. | schema, idempotency, duplicate-conflict and validated-cut tests |
| `runtime`: `RuntimeMapping`, `RuntimeMappingCompatibility`, `RuntimeMappingLoader`, `RuntimeMappingValidator`, `RuntimeSemanticAction`, `RuntimeTargetResolver`, `RuntimeValues` | `KEEP_WITH_ADAPTER` | Frozen target semantics stay; input adaptation/validation handles the new Bridge contract. | runtime mapping freeze, unknown target and capability gate tests |
| `runtime`: `RuntimeMirrorService`, `RuntimeMutationEngine` | `REFACTOR` | Retain subscribe-before-snapshot/replacement/mutation algorithm; make it session/generation/model-revision/watermark aware. | atomic USE replacement, queue race, resync and rollback tests |
| `semantic`: `AttributeValue`, `Dimension`, `MetamodelKind`, `ProjectDeclaration`, `SemanticDebugWriter`, `SemanticElement`, `SemanticIdRegistry`, `SemanticKindRegistry`, `SemanticReference`, `SourceProvenance` | `KEEP_WITH_ADAPTER` | Preserve neutral semantic vocabulary/provenance but add contract evidence/completeness where required. | canonical serialization, provenance and source-to-V2 tests |
| `semantic`: `JaCaMoSemanticModel` | `KEEP_WITH_ADAPTER` | Native output of accepted ModelSnapshot; no direct parser or runtime object dependencies. | model revision/determinism and adapter parity tests |
| `semantic`: `SemanticId` | `REFACTOR` | Encode project/session/generation/incarnation-aware canonical identity while preserving deterministic legacy compatibility during migration. | collision, recreation and USE-name mapping tests |
| `trace`: `OrderProjectionTrace`, `TraceBuilder`, `TraceIndex`, `TraceRecord`, `TraceStore` | `KEEP_WITH_ADAPTER` | Extend bidirectional trace from source/semantic/USE to Bridge entity/relation/event evidence. | source-to-USE round trip, projection and replay trace tests |
| `ui`: `BindingResolutionPanel`, `JaCaMoWorkbenchPanel` | `KEEP_WITH_ADAPTER` | Retain UI shell; present endpoint capability, unresolved relations, stale state and reviewable evidence rather than hiding them. | UI smoke, disabled action and error state tests |
| `verification`: `ConstraintDependencyIndex`, `ConstraintDescriptor`, `ConstraintKind`, `ConstraintOrigin`, `ConstraintRegistry`, `CrossDimensionalVerifier`, `DefaultVerificationService`, `OperationCheck`, `OperationRequest`, `RuntimeHistoryVerifier`, `RuntimeVerificationAttribution`, `RuntimeVerificationEngine`, `RuntimeVerificationReport`, `RuntimeVerificationReportExporter`, `VerificationCheckpoint`, `VerificationOutcome`, `VerificationReport`, `VerificationReportExporter`, `VerificationResult`, `VerificationService` | `KEEP_WITH_ADAPTER` | Reusable verification backend; require accepted state, exact correlation and capability/completeness attribution. | static/runtime/PRE/POST/gap/inconclusive report tests |
| `verification`: `VerificationProfile`, `VerificationProfileException`, `VerificationProfileLoader`, `VerificationSemanticLayer` | `KEEP_WITH_ADAPTER` | Preserve profile selection/closure, adding required Bridge capability declarations; do not merge NPL semantics into OCL. | profile closure, capability denial and norm/OCL separation tests |

The ledger's class count is a release gate: a future source addition must receive an
explicit row or be covered by a revised, reviewed row before it is considered in the
architecture migration. It is not authorization to delete any listed class.
