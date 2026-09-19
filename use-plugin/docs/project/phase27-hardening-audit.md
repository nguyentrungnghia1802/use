# Phase 27 engineering hardening audit

Resumed on 2026-09-19 at `d7639c3d` on `phase/27-final-hardening`.
That commit is Phase 26, already integrated into main. Its recorded reactor is
299/299; this is historical evidence, not a new Phase 26 run. Six uncommitted
files contained three regression tests and their initial fixes. They were retained.
No canonical Ecore, structural mapping, runtime mapping, schema, freeze hash,
package inventory or dependency pin is changed by Phase 27.

Classification: BUG FIX RESTORING EXISTING CONTRACT; TEST/EVIDENCE CHANGE.
Normative rules: INV-002, INV-005, INV-006, INV-007 and INV-017 in agent.md.

## Corrective work (P27.2, P27.4, P27.6)

* Runtime observer exceptions at receive, before/after mutation and completion
  must not leave LIVE truth. RuntimeMirrorService records a stable failure code,
  event ID and recovery instruction, transitions ERROR, and preserves notification
  failures as suppressed causes. Worker failures also increment queue.failed.
  Recovery is disconnect followed by authoritative reconnect/resync.
* Cleanup attempts the subscription, ordered worker, stream consumers and transport
  even if an earlier cleanup action fails. Failed subscription cleanup remains
  retryable. Composite cleanup visits all children and retains suppressed failures.
* CArtAgO unregister failure stays ERROR, retains workspace/logger identity for
  retry, and does not silently turn into DISCONNECTED. A new registration cannot
  overwrite outstanding failed cleanup. Late logger generations remain rejected.
* Invalid Moise Boolean/integer scalars produce MOISE_ATTRIBUTE_INVALID with ERROR,
  PARSING, source span, semantic ID, input evidence and remediation. They emit no
  inferred false/text-number value. XML Boolean true/false/1/0 remain supported.
* Newly generated Auction evidence reads the current plugin version from
  compatibility.json instead of falsely labelling candidate 1.0.1 as 1.0.0.

Regression anchors: RuntimeFoundationTest (receive, three worker hooks, retry,
queue/correlation/late callback tests), CartagoRuntimeConnectorTest,
CompositeRuntimeConnectorTest, StaticProjectImporterTest, HotfixLifecycleTest,
RuntimeVerificationEngineTest, GoldenPipelineTest and LiveJaCaMoAuctionIntegrationTest.
New receive and unsubscribe negative controls failed before the corrective changes:
expected ERROR but observed LIVE; expected transport cleanup but observed none.
Focused lifecycle/parser/verification gate then passed 55/55. The subsequent
performance test adds one test; final counts are recorded in closure evidence.

The failure diagnostic string complements existing runtime state/queue/trace
evidence; it is not a new serialized Diagnostic schema. Expected queue rejection
continues to use existing correlation invalidation and explicit rejection evidence.
It is distinct from an observer infrastructure failure. Optional objectNameOrNull
in the verifier omits an unavailable display target on error/skip reporting; exact
mutation and formal target resolution still reject missing/ambiguous identities.

## Search and architecture audit (P27.1ÃƒÂ¢Ã¢â€šÂ¬Ã¢â‚¬Å“P27.5)

Production-only search: TODO, FIXME, HACK, TEMP, workaround, placeholder,
not implemented, UnsupportedOperationException, ignored catches and temporary V1.
No unresolved TODO/FIXME/HACK implementation task was found. JaCaMoFacade default
methods throw explicit not-configured/not-imported codes; DefaultJaCaMoFacade
implements the supported production workflow. AtomicMoveNotSupportedException
selects the documented non-atomic file replacement fallback; export failures and
cleanup failures are separately tested. No unsupported branch was promoted.

RuntimeMutationEngine uses RuntimeMapping selection and RuntimeSemanticAction.
V1RuntimeBindingAdapter is required by D25-01, not obsolete V2 migration debris.
RuntimeTrace records event disposition; TraceIndex maps semantic/USE/runtime
identities; report/history stores answer different questions. Offline and runtime
verification paths implement distinct checkpoints. No duplicate path was removed
without proof. Production search for Auction/AuctionArtifact/placeBid/closeAuction,
CounterTeam/counter-team/counter1/count_items found no case-specific branch.

Determinism anchors: SemanticIdTest, JcmProjectLoaderTest stable graph,
MappingTransformationTest repeated plans, GoldenPipelineTest exact normalized
.use/.cmd/trace/diagnostics/reports, RuntimeMappingTest strict selection,
RuntimeTraceTest and RuntimeFoundationTest sequence handling, CounterTeamIntegrationTest
repeat OCL generation, MoiseRuntimeConnector sorted snapshots and
ReleasePackageIT canonical byte/hash checks. Runtime UUID/timestamps are normalized
or compared semantically; archive identity across toolchains is not promised.

The final acceptance matrix in phase28-project-closure.md supplies the requirement
to component/test/evidence/document mapping. Missing/conflicting supported behavior
found above was corrected, rather than relabelled unsupported.

## Lifecycle and security review (P27.6ÃƒÂ¢Ã¢â€šÂ¬Ã¢â‚¬Å“P27.7)

HotfixLifecycleTest covers workspace/profile/reimport replacement, authoritative
resync and failed synchronization. RuntimeFoundationTest covers subscribe, queue
drain, rejection, late events and recovery; composite tests prevent duplicate child
subscriptions and retire old generations. Periodic drift is a replaceable daemon
executor, stopped on close; Moise uses caller-controlled pollChanges and owns no
polling scheduler. It therefore has no hidden Moise executor to shut down.

Security tests: JcmProjectLoaderTest include traversal, linked classpath escape,
invalid archives and explicit external source roots; StaticProjectImporterTest
classpath initialization sentinel and XML entity/DTD refusal;
ConstraintOclTest profile path escape; DefaultJaCaMoFacadeTest export path/link
and cleanup behavior. Linked-directory tests use Windows junctions if symlink
privileges are unavailable and must not skip the correctness check.
JAR discovery reads entries without extraction or class initialization. Production
import contains no ProcessBuilder/command execution. Only test harnesses compile
and execute their checked-in trusted artifact fixtures. Explicitly configured
external roots are intentional supported input, not traversal bypasses.

## Regression, cases, performance and release (P27.8ÃƒÂ¢Ã¢â€šÂ¬Ã¢â‚¬Å“P27.12)

The full reactor includes event codec/schema/trace/identity/mapping/mutation,
synthetic/Jason/CArtAgO/Moise, full sync/order/drift/reconnect, lifecycle,
PRE/POST/history, cross-dimensional/normative subset and violation attribution.
Auction and Counter Team exercise import, generated model/state, exact trace,
real in-process connectors, positive/negative checks and reconnect convergence.
Both remain SUPPORTED_SUBSET_COMPLETE; standalone JaCaMo/NPL E2E is unproven.

Measurements come from DefaultJaCaMoFacadeTest (import, generation, full check,
used/max heap), RuntimeFoundationTest (event receipt to applied mirror and queue
depth/high-watermark), RuntimeVerificationEngineTest (event to result), and Counter
elapsed scenario time. These are local samples without an SLA or load-scale claim.

Final clean-reactor, relocated checkout, isolated installed plugin, 30-entry ZIP,
canonical resource and checksum results are recorded in phase28-project-closure.md
and its machine-readable evidence. ReleasePackageIT loads the installed JAR in a
child JVM with only the USE host and test bootstrap (no Maven dependency/production classes
on that child classpath), including both installed canonical mapping loaders.

## Documentation impact (P27.13)

Reviewed: architecture, metamodel baseline, structural/runtime mapping, semantic
extraction, OCL translation, transformation, trace, runtime/verification, UI,
testing, both cases, build/release, research boundaries, acceptance, risk,
limitations, compatibility, README, agent and task. Updated the runtime/extraction
failure contract, README stale HEAD claim and current evidence/closure pointers.
Historical phase counts remain labelled historical. Canonical V1, package inventory,
compatibility pins, standalone launcher/OE/NPL and manual GUI limits remain intact.

## Final local implementation gate

`mvn --batch-mode clean verify`: **307/307 PASS**, zero failures/errors/skips
(13 core, 130 GUI, 161 plugin unit/component + 3 release integration), 2026-09-19.
Focused final lifecycle gate: **43/43 PASS**. The earlier final-code module verify
preceded the last lifecycle observer regression; the 307-test reactor covers it.
Local samples: import 85,440,300 ns; generation 24,027,100 ns; full check 509,100 ns;
used heap 24,932,352 bytes; event-to-mirror 285,200 ns; queue depth 0/high-water 1;
event-to-result 478,800 ns; Counter scenario 689,451,300 ns and 33 events.
Relocated clean-checkout and Git integration remain pending in this implementation
commit and are recorded in the subsequent evidence closure.

## Relocated clean checkout

Commit `0a745d8d` cloned independently with `git clone --no-hardlinks` to
`C:/Users/NTNghia/AppData/Local/Temp/use-jacamo-final-0a745d8d`.
Same pinned toolchain; `mvn --batch-mode clean verify` passed **307/307**,
zero failures/errors/skips, 2 min 29 s. Git status was empty before and after.
Auction and Counter generated .use/.cmd match after LF normalization.
See evidence/closure/relocated-manifest.json and relocated-verify.log.
This closes P27.11; the source implementation is unchanged after that gate.
