# Final engineering acceptance and evidence

This is the current Phase 27â€“28 acceptance record, superseding historical test
counts as current evidence. D25-01 retains Ecore/Structural Mapping V1 as the final
supported target; Runtime Mapping V1/schema 2.0.0 remains FROZEN. Scope is
observe-only verification, not runtime control or universal JaCaMo equivalence.

## Capability matrix (P27.1 / P28.1)

All test names below are executable classes under src/test/java/org/tzi/use/plugins/jacamo.
Fresh Surefire/Failsafe XML and generated case artifacts are collected by
`tools/build_closure_evidence.py`. COMPLETE always refers to the stated requirement.

| Requirement | Final status | Production component | Test / evidence | Contract document |
|---|---|---|---|---|
| Final metamodel baseline | COMPLETE | MappingLoader / canonical Ecore | MappingTransformationTest; phase25 metamodel-audit.json | 04-jacamo-metamodel-baseline; phase26 audit |
| Structural mapping coverage/freeze | COMPLETE | MappingValidator, TransformationPlanner | MappingTransformationTest negative mutations/hashes | 05-metamodel-mapping-contract |
| Safe static import | SUPPORTED_SUBSET_COMPLETE | JcmProjectLoader, StaticProjectImporter | JcmProjectLoaderTest, StaticProjectImporterTest | 06-semantic-model-and-extraction |
| Semantic IR/exact identity | COMPLETE | JaCaMoSemanticModel, SemanticId | SemanticModelTest, SemanticIdTest | 06-semantic-model-and-extraction |
| Deterministic .use generation | COMPLETE | TransformationPlanner, TextBackend | MappingTransformationTest, GoldenPipelineTest | 08-use-transformation |
| .cmd / initial state | COMPLETE | InstancePlanner, DirectUseBackend | InstanceMaterializationTest, GoldenPipelineTest | 08-use-transformation |
| Exact trace/binding | COMPLETE | TraceIndex, ExactSemanticResolver, BindingStore | TraceBindingTest, HotfixBindingTest | 09-traceability-binding-resolver |
| RuntimeEvent canonical schema | COMPLETE | RuntimeEvent, RuntimeEventCodec | RuntimeFoundationTest, RuntimeAuthorityTest | runtime-event-identity |
| RuntimeTrace disposition | COMPLETE | RuntimeTrace | RuntimeTraceTest | runtime-event-identity |
| Exact runtime identities | SUPPORTED_SUBSET_COMPLETE | RuntimeIdentity, TraceRuntimeTargetAdapter, connectors | RuntimeIdentityHardeningTest, RuntimeAliasTest, RuntimeMigrationTest | runtime-event-identity |
| Canonical runtime mapping | COMPLETE | RuntimeMappingLoader, RuntimeMappingValidator | RuntimeMappingTest freeze/draft/negative tests; ReleasePackageIT | phase26-runtime-mapping-audit |
| Mirror synchronization | SUPPORTED_SUBSET_COMPLETE | RuntimeMutationEngine, RuntimeMirrorService | RuntimeFoundationTest, HotfixLifecycleTest, both case drift summaries | 10-runtime-adapter |
| Full standalone JaCaMo runtime E2E | EXPLICITLY_UNSUPPORTED | Current MoiseRuntimeConnector requires OE | Phase 20 pinned launcher probes and recorded technical limitation | phase20-runtime-evidence |
| OCL/runtime verification | SUPPORTED_SUBSET_COMPLETE | RuntimeVerificationEngine, OfflineVerificationService | RuntimeVerificationEngineTest, OfflineVerificationServiceTest | 11-verification-engine |
| PRE/POST and @pre | SUPPORTED_SUBSET_COMPLETE | RuntimeVerificationEngine | RuntimeVerificationEngineTest; Auction/Counter reports | phase22-verification-evidence |
| Finite ordering/history | SUPPORTED_SUBSET_COMPLETE | RuntimeHistoryVerifier | RuntimeHistoryVerifierTest | phase22-verification-evidence |
| Cross-dimensional source links | SUPPORTED_SUBSET_COMPLETE | CrossDimensionalVerifier | CrossDimensionalVerifierTest | phase23-cross-dimensional-evidence |
| Normative OE observation subset | SUPPORTED_SUBSET_COMPLETE | MoiseNormativeSnapshot | MoiseRuntimeConnectorTest, CounterTeamIntegrationTest | phase23-cross-dimensional-evidence |
| Constraint translation subset | SUPPORTED_SUBSET_COMPLETE | ConstraintExtractor, OclGenerator | ConstraintClosureTest, ConstraintOclTest | phase24-translation-multicase-evidence |
| Violation report/navigation | COMPLETE | VerificationReportExporter, RuntimeVerificationReportExporter, JaCaMoWorkbenchPanel | OfflineVerificationServiceTest, RuntimeVerificationEngineTest, JaCaMoWorkbenchPanelTest | 11-verification-engine; 12-plugin-ui-workflow |
| Workbench workflow | SUPPORTED_SUBSET_COMPLETE | DefaultJaCaMoFacade, JaCaMoWorkbenchPanel | DefaultJaCaMoFacadeTest, HotfixLifecycleTest, JaCaMoWorkbenchPanelTest | 12-plugin-ui-workflow |
| Package and installed plugin load | COMPLETE | Maven assembly/shade, release manifest, plugin loader | ReleasePackageContractTest, ReleasePackageIT (30 entries, hashes, isolated child JVM) | 15-build-release-operations |
| Reproducible build on pinned toolchain | SUPPORTED_SUBSET_COMPLETE | Maven reactor | clean relocated checkout full verify; evidence source/input hashes | 15-build-release-operations |
| Case 1 Auction | SUPPORTED_SUBSET_COMPLETE | Shared production pipeline | GoldenPipelineTest, AuctionSourceRuntimeTest, LiveJaCaMoAuctionIntegrationTest | 14-auction-case-study |
| Case 2 Counter Team | SUPPORTED_SUBSET_COMPLETE | Same shared production pipeline | CounterTeamIntegrationTest; phase24-counter-evidence | phase24-translation-multicase-evidence |

No MISSING/CONFLICT capability is accepted as complete. Unsupported standalone
E2E uses the already approved Phase 20 technical-limitation exit, not a new scope
cut introduced by this audit. NPL lifecycle and runtime control are outside the
supported verification subset. No V2 was fabricated to close a checklist.

## Unsupported and subset boundaries (P28.3)

| Boundary | Why / source evidence | Visible or diagnostic behavior | Protection / future enablement |
|---|---|---|---|
| General Java/Jason syntax and arbitrary Java effects | Static syntax extraction cannot establish executable behavior; phase24 translation inventory | Located unsupported/unresolved diagnostics; no guessed scalar, target or partial OCL | StaticProjectImporterTest, ConstraintClosureTest; requires separately proven source semantics |
| Unbound/dynamic runtime entities | Exact semantic/USE target absent; pinned connector authority/identity contract | Quarantine or explicit trace/mapping failure, no invented object | RuntimeIdentityHardeningTest, RuntimeFoundationTest, CartagoRuntimeConnectorTest; needs authoritative identity/binding contract |
| Jason mind / Moise runtime instance equivalence | Frozen target has no general mental/group-instance/mission/goal runtime slots | Trace-only observation, not USE mutation equivalence | RuntimeMappingTest, RuntimeMigrationTest, MoiseRuntimeConnectorTest; requires evidence-backed target evolution |
| Standalone .jcm mirror E2E | Phase20 launcher: fixture XML XSD mismatch and ora4mas.nopl.oe.Group/Scheme versus moise.oe.OE adapter gap | No workbench launch/configuration claim; documented technical limitation | Preserved Phase20 probes; both current cases explicitly in-process; future fixture reconciliation and board adapter |
| NPL activation/fulfilment/violation/time | OE 1.1 exposes derived obligations/permissions, not full NPL lifecycle | MOISE_NO_NPL_NORM_LIFECYCLE; no inferred normative OCL | MoiseRuntimeConnectorTest and normativeSnapshot unsupported inventory; future upstream lifecycle adapter/formal contract |
| Autonomous Agentâ†’Artifactâ†’Organisation chain | Current harness issues artifact operations and creates an explicit programmatic OE | Case reports state test-driven component execution and static XML provenance only | Both case integration tests; future true launcher scenario/causal correlation |
| General temporal/liveness verification | Finite traces cannot prove eventual completion/deadlines | Bounded ordering outcomes; missing terminal evidence cannot imply eventuality | RuntimeHistoryVerifierTest; future time/event semantics |
| Arbitrary pre/post inference | @pre works for exact explicit authored/translated contracts, not arbitrary effects | OP_FAIL produces SKIPPED; missing pre-state/checkpoint stays explicit | RuntimeVerificationEngineTest, ConstraintClosureTest; future proof-backed translation |
| Percept/action causal cross-dimensional inference | Source links do not prove delivery or runtime invocation joining | Source-link checks only, no invented causal rule | CrossDimensionalVerifierTest; future authoritative correlation |
| False guard with no terminal callback | CArtAgO can suspend before opStarted; upstream API boundary | Correlation begins at opStarted; no fabricated OP_FAIL | CartagoRuntimeConnectorTest and Auction evidence; future explicit timeout semantics |
| Long-duration memory/large-load guarantees | Runtime ledger/report retention is in memory; only local smoke samples | Metrics are observations, no throughput/SLA promise | Queue/backpressure and latency tests; future measured retention/load design |
| Interactive installed GUI and other toolchains | Automated suite is Windows/JDK21/component pins; isolated smoke is not manual GUI evidence | Explicit manual environment boundary in README/KNOWN-LIMITATIONS | Workbench headless tests + ReleasePackageIT; future manual/cross-platform matrix |
| Runtime launch/configuration in workbench | Connector configuration is a service API responsibility | Workbench observes configured runtime; no external launcher UI | DefaultJaCaMoFacadeTest, JaCaMoWorkbenchPanelTest; future explicit UI scope |
| Runtime repair/control | Observe-only architecture | Reports never block/repair JaCaMo actions | Both live tests and architecture contract; a future change requires separate authorization/specification |

## Evidence and reproduction (P28.2 / P28.4)

Canonical command from repository root: `mvn --batch-mode clean verify` using
Oracle JDK 21.0.5 and Maven 3.9.9 on Windows. Focused commands and exact final
counts, revisions, timings and package digests are recorded in
`evidence/closure/validation.json` after execution. The bundle tool refuses failed,
errored or skipped test reports and inventories source/canonical/generated hashes.

Generated bundle: `use-plugin/target/closure-evidence.zip`, alongside
`closure-evidence.json` and its SHA-256. It contains the final Ecore, structural
mapping/schema/freeze, runtime mapping/schema/freeze, OCL profiles/provenance,
both cases' model/state/trace/events/reports/summaries, mirror-correctness output,
compatibility, complete test XML, candidate distribution ZIP and checksum.
The manifest records exact HEAD and dirty state; a dirty build is not labelled
a clean-commit result. Runtime timestamps and UUIDs are intentionally variable.

Phase 20 source/upstream evidence is preserved and linked, not silently rerun or
promoted by the final regression. The immutable v1.0.0 tag and historical v1.0.1
evidence are untouched. No new release tag/publication is implied by a Git push.

## Final project status (P28.5 / P28.6)

Engineering gates and presentation are recorded after final validation. User
acceptance is a separate final gate: this document does not impersonate the user
or mark their confirmation received. Until confirmation, the literal roadmap
label `CORE LOGIC / CODING COMPLETE` remains pending final acceptance even when
all autonomous engineering work is complete.

Remaining non-engineering work: thesis exposition and research argument,
selection/preparation of a manual demo environment, and final user acceptance.
Unproven standalone/NPL/autonomous-chain claims must not appear as thesis results.
