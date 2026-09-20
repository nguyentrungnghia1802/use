# Final engineering acceptance and evidence

> **2026-09-20 final completeness update:** Direct launcher-board observation and
> AgentSpeak-driven standalone control now PASS. Original Auction plan/deadline
> equivalence remains unsupported (B). The new audit supersedes older adapter-gap
> and component-only claims below; historical results remain historical. See
> [final audit](phase20-final-completeness-audit.md).


This is the current Phase 27–28 acceptance record, superseding historical test
counts as current evidence. D25-01 retains Ecore/Structural Mapping V1 as the final
supported target; Runtime Mapping V1/schema 2.0.0 remains FROZEN. Scope is
observe-only verification, not runtime control or universal JaCaMo equivalence.

## Capability matrix (P27.1 / P28.1)

All test names below are executable classes under src/test/java/org/tzi/use/plugins/jacamo.
Fresh Surefire/Failsafe XML and generated case artifacts are collected by
`tools/build_closure_evidence.py`; the new standalone/reactor bundles use
`tools/runtime/collect_final_audit.py`. COMPLETE always refers to the stated requirement.

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
| Standalone launcher control | SUPPORTED_SUBSET_COMPLETE | MoiseBoardSnapshotSource, forBoards, shared mirror pipeline | LauncherMirrorProbe positive and negative readiness; actual AgentSpeak role/mission/goal and resync | phase20-final-completeness-audit |
| Original Auction standalone equivalence | EXPLICITLY_UNSUPPORTED | Invalid/incomplete static fixture semantics (B) | Original/schema probes; self-referencing plan and informal deadline have no proven executable meaning | phase20-final-completeness-audit |
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
| Original Auction standalone equivalence | Fixture XML XSD mismatch, self-referencing plan and informal deadline (B); board adapter gap resolved | Derived standalone control PASS; original equivalence not claimed | Positive/negative launcher harness; future explicit valid source plan/deadline contract |
| NPL activation/fulfilment/violation/time | OE 1.1 exposes derived obligations/permissions, not full NPL lifecycle | MOISE_NO_NPL_NORM_LIFECYCLE; no inferred normative OCL | MoiseRuntimeConnectorTest and normativeSnapshot unsupported inventory; future upstream lifecycle adapter/formal contract |
| General autonomous/cross-dimensional causal equivalence | Launcher control proves actual AgentSpeak artifact and organisation operations, not a general causal mapping or original Auction semantics | Control has explicit permission and no guessed source deadline | LauncherMirrorProbe; original fixture boundary retained |
| General temporal/liveness verification | Finite traces cannot prove eventual completion/deadlines | Bounded ordering outcomes; missing terminal evidence cannot imply eventuality | RuntimeHistoryVerifierTest; future time/event semantics |
| Arbitrary pre/post inference | @pre works for exact explicit authored/translated contracts, not arbitrary effects | OP_FAIL produces SKIPPED; missing pre-state/checkpoint stays explicit | RuntimeVerificationEngineTest, ConstraintClosureTest; future proof-backed translation |
| Percept/action causal cross-dimensional inference | Source links do not prove delivery or runtime invocation joining | Source-link checks only, no invented causal rule | CrossDimensionalVerifierTest; future authoritative correlation |
| False guard with no terminal callback | CArtAgO can suspend before opStarted; upstream API boundary | Correlation begins at opStarted; no fabricated OP_FAIL | CartagoRuntimeConnectorTest and Auction evidence; future explicit timeout semantics |
| Long-duration memory/large-load guarantees | Runtime ledger/report retention is in memory; only local smoke samples | Metrics are observations, no throughput/SLA promise | Queue/backpressure and latency tests; future measured retention/load design |
| Interactive installed GUI and other toolchains | Automated suite is Windows/JDK21/component pins; isolated smoke is not manual GUI evidence | Explicit manual environment boundary in README/KNOWN-LIMITATIONS | Workbench headless tests + ReleasePackageIT; future manual/cross-platform matrix |
| Runtime launch/configuration in workbench | Connector configuration is a service API responsibility | Workbench observes configured runtime; no external launcher UI | DefaultJaCaMoFacadeTest, JaCaMoWorkbenchPanelTest; future explicit UI scope |
| Runtime repair/control | Observe-only architecture | Reports never block/repair JaCaMo actions | Both live tests and architecture contract; a future change requires separate authorization/specification |


## Final verification provenance

Date: 2026-09-20. Implementation revision: `0a745d8dc75a0af22405c5f5856be60b1947ee71`.
Phase 27 evidence/integration baseline: `8981723efcad584c6d998b0b4e06dc1661cd8b11`.
Classification: TEST/EVIDENCE CHANGE; NO CONTRACT CHANGE.

All autonomous engineering work is closed for the explicitly bounded capabilities
above. **Final user acceptance is pending.** The unconditional project label
`CORE LOGIC / CODING COMPLETE` is not asserted before that confirmation, as required
by P28.6. Unsupported capabilities are closed by explicit scope decisions, not by
changing their test outcome to PASS. No new release tag or publication is claimed.

## P28.2 evidence bundle and provenance

`evidence/closure/relocated-evidence.zip` preserves the independently built
`0a745d8d` checkout, its original manifest, XML results for all 307 tests, canonical
Ecore/mapping/schema/freeze files, OCL/provenance resources, generated model/state,
traces, runtime events, mirror summary, reports, both cases, compatibility and the
tested release ZIP/checksum. Its SHA-256 is recorded in validation.json. The
original log and manifest remain separate and unchanged. The temporary checkout
is no longer the only copy of this evidence.

`evidence/closure/final-evidence.zip` preserves the final module run, generated
case/mirror artifacts, release package, canonical resources and module XML reports.
`final-state.json` identifies the actual tested revision, command, source-equivalence
check, all file hashes, exact suite counts and package digest. This is a module
run, not a relabelled new 307-test reactor. The retained clean reactor proves the
unchanged executable/test/build inputs; documentation-only closure commits do not
invalidate that evidence. Git closure is recorded separately to avoid pretending
a commit can contain its own final hash.

Reproduction: from the repository root run `mvn --batch-mode clean verify` on the
pinned Windows 11/JDK 21.0.5/Maven 3.9.9 toolchain. The final module command is
`mvn --batch-mode -pl use-plugin verify`. Tests regenerate the three target evidence
directories above. Compare archive SHA-256 with its sidecar before using a bundle.

## Historical P28.4 verification gates (superseded by final completeness gates below)

| Gate | Command / evidence | Result |
|---|---|---|
| Full reactor | `mvn --batch-mode clean verify`, implementation 0a745d8d | 307/307; 13 core + 130 GUI + 161 plugin + 3 release IT; zero failures/errors/skips; retained 2026-09-19 evidence |
| Independent relocated checkout | same command, clone --no-hardlinks, 0a745d8d | 307/307, zero failures/errors/skips; clean before/after; relocated-verify.log |
| Final focused lifecycle | recorded Phase 27 focused command/result | 43/43, zero failures/errors/skips; historical run, not newly rerun |
| Final full module / Phase 27 post-merge regression | `mvn --batch-mode -pl use-plugin verify` on integrated 8981723e | 164/164 PASS, zero failures/errors/skips (161 unit/component + 3 release IT); final-module-verify.log and final-state.json |
| Focused mapping/runtime, Auction, Counter | named suites within final module gate, enumerated in final-state.json | no redundant separate invocation; actual per-suite counts retained |
| Installed plugin, inventory, hashes | ReleasePackageIT in final module gate | isolated child JVM, both canonical loaders, 30 ZIP entries, exact source bytes and SHA-256 |

Mirror summary: INITIAL_SYNC, AFTER_STATE_CHANGE, OPERATION_ENTER_EXIT_FAIL and
RECONNECT_RESYNC; zero unexplained drift and zero dropped/rejected/failed events
in the supported scenario. Negative tests deliberately exercise explicit failures;
these do not turn the supported positive scenario into an unrestricted guarantee.

## P28.5 documentation and final state

Reviewed phase27 audit, README, architecture, final metamodel/mapping audit,
runtime/verification contracts, acceptance, risk/limitations, compatibility,
roadmap and task checklist. The final V1 selection and pinned dependencies remain
unchanged. Historical phase counts remain historical. Existing closure pointers were verified against this document. No executable implementation or frozen byte was changed.

Phase 27 was already fast-forward integrated/pushed at 8981723e when this session
started; live remote refs confirmed it. This phase preserves its existing traceability
matrix and supplies the explicit post-merge gate. Phase 28 uses its own phase branch, then
fast-forward integration, post-merge smoke and push under agent.md section 16.

## P28.6 final user acceptance packet

The matrix, test evidence, frozen mapping status, mirror result, multi-case scope,
unsupported boundaries and package locations above form the acceptance packet.
Remaining non-engineering work: select thesis narratives/figures, rehearse an
interactive installed demo if desired, and provide final user acceptance. Such
acceptance does not promote standalone/NPL, untested platforms or arbitrary
semantics to supported. The checklist intentionally keeps user confirmation open.

## Historical integration and final smoke

Phase 28 was fast-forward merged to main as `42b38396874b4f080a1e2aaa1fb5cbdbf931f11a`
and pushed successfully to origin/main and origin/phase/28-project-closure.
Both remote refs were queried and matched. Post-merge installed-package smoke:
`mvn --batch-mode -pl use-plugin failsafe:integration-test failsafe:verify`: **3/3 PASS**,
zero failures/errors/skips. See evidence/closure/git-closure.json,
post-merge-smoke.log and post-merge-ReleasePackageIT.xml. The following closure-record
commit contains documentation/evidence only; executable, package and canonical
inputs remain identical. Final user confirmation is still the only acceptance gate
not satisfiable by the agent. Phase 20 unchecked full-project steps intentionally
remain unproven under its documented alternative exit.

## Final completeness acceptance update

The new [Phase 20 audit](phase20-final-completeness-audit.md) supersedes the older
component-only and board-adapter-gap claims. P20.2–P20.5 are each
SUPPORTED_SUBSET_COMPLETE. Original Auction full standalone semantics and upstream
in-process thread quiescence remain B / EXPLICITLY_UNSUPPORTED; neither blocks
closure of supported engineering. Source, dependency and frozen-contract boundaries
are unchanged except the explicit read-only launcher-board adapter capability.

The exhaustive current checklist inventory is
[evidence/final-completeness/remaining-checkboxes.json](evidence/final-completeness/remaining-checkboxes.json):
A=0, B=2, C=29, D=2. C are recurring contract templates; D is the single final
acceptance decision represented in two places. Engineering qualifies for the
bounded CORE LOGIC / CODING COMPLETE disposition after all gates pass, but P28.6
requires user confirmation before the unconditional final project label.

The only user confirmation is acceptance of the delivered implementation and
these explicit supported/unsupported boundaries. It does not require inventing
Auction plan/deadline semantics or accepting an unproven full original E2E claim.

## Fresh verification gates

- Focused runtime/integration selection: **33/33 PASS**, zero failures/errors/skips (`evidence/final-completeness/focused.log`). Includes both Auction and Counter integration tests and 2 board adapter regressions.
- Current full clean reactor: **309/309 PASS**: 13 core + 130 GUI + 163 plugin unit/component + 3 release integration; zero failures/errors/skips (`reactor.log`, `current/validation.json`).
- Original-fixture/schema probe preserves intentional original parse/XSD rejection and successful non-equivalent builder control.
- Standalone mirror positive: **1/1 PASS**; missing-role negative readiness: **1/1 PASS** after clean reactor and regenerated classpath.
- Installed plugin/package gate: **3/3 PASS** as part of the reactor, with 30-entry ZIP and checksum validation.

The current manifest records the resumed baseline plus dirty source hashes; it does
not pretend the baseline commit alone contains this implementation. The subsequent
relocated gate verifies the committed source in an independent clean checkout.
