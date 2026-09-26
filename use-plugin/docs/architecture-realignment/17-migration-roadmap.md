# Migration roadmap A–K

This roadmap begins only after approval of this audit. Paths/classes named as “new” are proposed implementation targets, not files created by the audit. Every phase is independently reviewable and preserves a working legacy path until its rollback gate is retired.

## Phase A — Preserve and qualify the baseline

- **Objective:** Freeze reproducible USE/JaCaMo inputs, dirty-state facts, current failures, official case hashes and API/version compatibility targets.
- **Why:** Shadow comparison and rollback are meaningless without an immutable reference; current frozen manifest and audited dependency graph differ at JaCaMo/Jason patch versions.
- **Current state:** USE HEAD `215784b648a906e4db6086352946aba5bda10f98` has pre-existing frontend edits and three Maven failures; JaCaMo HEAD `3866858a7ebf6be85d9199c13a09cf4bfb8191be` declares 1.3.1/Jason 3.3.2; freeze manifest pins 1.3.0/3.3.0.
- **Target state:** Machine-readable baseline manifest records revisions, file hashes, dirty paths, test counts/failures, dependency graphs and canonical case manifests without modifying frozen artifacts.
- **Files/classes affected:** New evidence/baseline files under `use-plugin/docs/project/architecture-realignment/` or release-evidence staging; test harness only. No production class.
- **Exact dependencies:** JDK 21, Maven reactor, JaCaMo distributions 1.3.0/1.3.1, Jason 3.3.0/3.3.2, CArtAgO 3.1, Moise 1.1, NPL 0.6.1, USE 7.5.0.
- **Implementation steps:** Snapshot Git/file hashes; reproduce three Maven failures; run JaCaMo tests in an isolated copy; verify 21/48/37/3 inventory and frozen hashes; vendor/verify canonical manifests; create API compatibility matrix.
- **Non-goals:** Fixing production failures, changing goldens, selecting transport, editing Ecore/mappings.
- **Risks:** Dirty edits are misattributed; JaCaMo Gradle configuration rewrites line endings; dependency artifacts drift.
- **Tests:** Baseline-manifest schema/hash test; clean-copy JaCaMo test; repeated Maven failure signature comparison.
- **Regression gates:** No production/frozen file changes; known failures have identical or explained signatures; protected user changes preserved.
- **Evidence outputs:** Baseline JSON/Markdown, dependency trees, test reports, canonical-source manifests, checkout-content diff proof.
- **Definition of Done:** Another machine can reproduce exact inputs/results and distinguish pre-existing failures from migration changes.
- **Rollback/compatibility notes:** Documentation/evidence only; remove generated staging without touching existing work. Legacy execution remains default.

## Phase B — Define the neutral contract and Bridge API adapters

- **Objective:** Implement versioned transport-neutral DTO/schema, canonical identity, capability model and official source adapters behind tests, with deterministic process-boundary serialization.
- **Why:** USE and JaCaMo must share semantics without classpath/live-object coupling or transport lock-in.
- **Current state:** Current runtime DTOs lack session/generation/model revision/vector watermarks; static frontend reconstructs semantics.
- **Target state:** Contract module plus `ProjectModelAdapter`, Jason/Moise/CArtAgO adapters produce validated `ModelSnapshot`, candidate `RuntimeSnapshot` and `RuntimeEvent` fixtures. The neutral contract depends on neither USE nor EMF/platform libraries, contains no live subsystem object and requires no shared classpath.
- **Files/classes affected:** Proposed new module/package `org.jacamo.bridge.contract` and `org.jacamo.bridge.adapter`; JSON Schema or equivalent neutral schema; no edits to frozen V2. Physical module location is decided by a dependency spike before merge.
- **Exact dependencies:** Official JaCaMo 1.3.1 project API, Jason 3.3.2, CArtAgO 3.1, Moise 1.1, NPL 0.6.1; contract module itself depends only on JDK/schema codec chosen by approved ADR.
- **Implementation steps:** Define envelope/DTO/enums; implement canonical codec/digest and deterministic cross-process round-trip; define exact relation-scoped cardinality facts; implement JCM/Jason/OS walks; implement descriptor/runtime wrappers; preserve runtime fact identity/provenance/projection status; add capability/unresolved facts; add compatibility probes for pinned graph; publish schema evolution rules.
- **Non-goals:** Network server, USE materialization, case-specific normalization, custom parser fallback.
- **Risks:** Official mutable objects leak into DTOs; API drift; contract mirrors implementation classes; fields falsely marked complete.
- **Tests:** Contract round-trip/fuzz; serialize in one classpath/process and deserialize in a classpath-isolated consumer; official-object fixture tests; relation-cardinality multi-context test; API signature/drift; unsupported-field negative tests; deterministic digest.
- **Regression gates:** Contract has no USE/EMF dependency; no core JaCaMo patch; no source-name matching; frozen hashes unchanged.
- **Evidence outputs:** Schema, field-to-API evidence matrix, capability report, compatibility test reports, ADR for physical module boundary.
- **Definition of Done:** Static official objects deterministically produce a versioned validated ModelSnapshot with explicit gaps, and every contract message round-trips without USE/EMF/live JaCaMo types or shared classpath.
- **Rollback/compatibility notes:** New isolated module; no current plugin path consumes it. Can be removed without changing legacy behavior.

## Phase C — Hello World Bridge vertical prototype

- **Objective:** Prove official load, Bridge insertion, initialized snapshot and events on canonical Hello end-to-end on the JaCaMo side.
- **Why:** Hello exercises all three dimensions with lower dynamic complexity and validates the extension approach early.
- **Current state:** Canonical fixture exists as unaccepted working input; no official Bridge path exists.
- **Target state:** `JaCaMoBridgePlatform`, Bridge `AgArch`, controller/logger and board/NPL adapters produce Hello ModelSnapshot/RuntimeSnapshot/events and cleanly stop.
- **Files/classes affected:** Proposed `JaCaMoBridgePlatform`, `BridgeAgArch`, `SnapshotCoordinator`, adapter readiness registry; Hello bridge integration fixtures/tests.
- **Exact dependencies:** Phase B contract; `jacamo.platform.Platform`; Jason `RuntimeServices`/`AgArch`; CArtAgO environment/controller/logger; ORA4MAS boards/NPL listener.
- **Implementation steps:** Register Platform in test JCM/config without altering canonical case semantics; inject AgArch before agents; attach sources before snapshot; capture validated cut; export in-memory/recorded contract; stop/detach; compare official inventory/provenance.
- **Non-goals:** Production transport choice, USE client, Auction/House, performance tuning.
- **Risks:** Platform order before authorities ready; AgArch ordering changes execution; missed early board/artifact events.
- **Tests:** Canonical hash; load-only parity; lifecycle/readiness; action/focus/role events; same-name recreation probe; thread/listener cleanup.
- **Regression gates:** Original Hello behavior still runs; no core patch; no custom parser fallback; incomplete sources labelled.
- **Evidence outputs:** Recorded Hello contract trace, capability map, snapshot digest, lifecycle logs, official-object inventory diff.
- **Definition of Done:** A canonical Hello run produces reproducible official ModelSnapshot and accepted validated runtime cut without modifying USE.
- **Rollback/compatibility notes:** Remove Bridge platform declaration/test harness to return to unchanged JaCaMo run; contract artifacts remain non-production.

## Phase D — USE native Bridge adapter

- **Objective:** Consume validated contract data through the current V2 planning/materialization/trace/verification foundation.
- **Why:** Demonstrates that the backend can be retained while replacing semantic authority.
- **Current state:** `DefaultJaCaMoFacade`/`StaticProjectImporter` lead to custom frontend; runtime connectors accept in-process objects.
- **Target state:** `BridgeClient`, `ContractValidator` and `NativeSemanticAdapter` feed `JaCaMoSemanticModel`; upgraded mirror DTO/state machine initializes USE from Hello snapshots/events. A mandatory smoke harness runs `JaCaMo + Bridge` and `USE + BridgeClient` in separate JVMs before any production transport is selected.
- **Files/classes affected:** `DefaultJaCaMoFacade`, `StaticProjectImporter`, `JaCaMoSemanticModel`, `SemanticId`, `ExactSemanticResolver`, `TraceIndex`, `RuntimeMirrorService`, runtime codecs/validators/mapping/mutation engine; new client/adapter classes.
- **Exact dependencies:** Phase B schema/codec; frozen V2/Mapping/Runtime Mapping; USE 7.5.0; existing planners/backends/verification services.
- **Implementation steps:** Validate envelope/IDs; adapt ModelSnapshot; compile MModel; adapt RuntimeSnapshot; transactionally materialize only faithful facts and retain evidence-only facts; apply events with guards; expose explicit legacy/bridge mode; generate trace/provenance and reports; launch Process A (`JaCaMo + Bridge`) and Process B (`USE + BridgeClient`) over a minimal/test transport or process-neutral recorded/framed mechanism; prove snapshot/event flow, Bridge restart -> new session, stale session/generation rejection, reconnect/resnapshot and fail-closed schema/version mismatch.
- **Non-goals:** Remove legacy parsers/connectors, choose or harden a production transport, change mappings/OCL.
- **Risks:** Identity sanitization collisions, partial state apply, model-revision mismatch, golden differences.
- **Tests:** Contract fixtures; MModel compile; direct/text parity; snapshot replacement; evidence-only runtime-fact/OCL gating; stale event rejection; trace round-trip; Hello case OCL; mandatory separate-JVM smoke proving no live object/shared classpath and no JaCaMo runtime classes in USE production-side semantic backend.
- **Regression gates:** Frozen hashes/pass gates; legacy mode behavior unchanged; failed Bridge import cannot fall through silently; transaction rollback proven.
- **Evidence outputs:** Hello USE model/state/trace/report digests and contract-to-USE attribution matrix.
- **Definition of Done:** Hello Bridge data reaches USE and verifies through the existing backend with no source reconstruction, and the separate-JVM smoke passes all model/snapshot/event, restart, reconnect and mismatch criteria without selecting a production transport.
- **Rollback/compatibility notes:** Feature flag/facade strategy keeps legacy path default; remove/disable Bridge client without data migration.

## Phase E — Shadow semantic and runtime comparison

- **Objective:** Run legacy and Bridge frontends side by side, classify every difference and establish replacement confidence.
- **Why:** Deleting/replacing parsers before an evidence-backed semantic diff would lose supported behavior or preserve legacy mistakes blindly.
- **Current state:** Legacy goldens and tests exist; no normalized cross-frontend comparator.
- **Target state:** Deterministic comparator aligns canonical IDs/provenance, reports exact/missing/extra/different/unsupported facts and runtime fingerprints.
- **Files/classes affected:** New shadow comparison/evidence tooling and tests; legacy frontend remains untouched; possibly diagnostics/report schema.
- **Exact dependencies:** Phase D adapter, existing legacy pipeline, canonical contract codec, TraceIndex.
- **Implementation steps:** Define comparison canonical form; compare Hello; classify each delta against official API/source; fix adapter bugs only; approve intentional corrections separately; repeat snapshot/event replay; store signed/digested reports.
- **Non-goals:** Force byte equality where legacy was wrong, update goldens automatically, enable Bridge as default.
- **Risks:** Comparing display names instead of semantics; official subset limitations mislabelled as regressions; dirty baseline obscures cause.
- **Tests:** Comparator self-tests, mutation sensitivity, deterministic reports, known-difference fixtures, current three failure reproduction.
- **Regression gates:** No assertion weakening; every difference has evidence and disposition; legacy remains runnable; frozen artifacts unchanged.
- **Evidence outputs:** Per-entity semantic diff, reviewed delta register, accepted-correction records, runtime replay diff.
- **Definition of Done:** Hello differences are exhausted and Bridge path meets or explicitly exceeds supported legacy semantics.
- **Rollback/compatibility notes:** Comparator is additive; legacy remains authoritative production path until Phase I.

## Phase F — Original Auction migration

- **Objective:** Add original Auction dynamic artifacts, operations, organization boards, norms and correlated runtime verification.
- **Why:** Auction is the first material test of dynamic cross-dimensional semantics and supported deontic boundary.
- **Current state:** Reduced/programmatic Auction controls exist; original standalone semantics are not proven.
- **Target state:** Original source runs with official Bridge snapshots/events; supported structure/runtime verifies in USE with explicit original-semantic limitations.
- **Files/classes affected:** Generic Bridge readiness/correlation adapters, contract fields only if generic evidence requires a compatible minor version, Auction fixtures/profile/tests; no case branch in production.
- **Exact dependencies:** Phases C–E; original Auction sources; CArtAgO operation logger; Group/Scheme boards; NPL listener; runtime verifier.
- **Implementation steps:** Verify hashes; official-load; observe dynamic artifact UUIDs/descriptors; correlate action/operation; capture board/norm state; materialize/replay; reconnect; compare legacy controls; document self-reference/deadline result.
- **Non-goals:** Claim full original E2E without plan/deadline evidence, encode Auction names, auto deontic-to-OCL conversion.
- **Risks:** Early dynamic events missed, operation correlation unavailable, runtime nondeterminism, semantic overclaim.
- **Tests:** Dynamic create/dispose/recreate; success/failure PRE/POST; exact role-cardinality relation facts; roles/commitments/goals/norm lifecycle with faithful-materialization versus evidence-only assertions; negative OCL; resync/replay.
- **Regression gates:** Hello remains green; reduced controls remain correctly labelled; unsupported boundaries unchanged unless new evidence closes them.
- **Evidence outputs:** Original-source manifest, capability map, event/correlation log, state/report digests, supported-versus-unsupported verdict.
- **Definition of Done:** Original Auction supported scope passes generic path; every excluded original semantic is explicit with evidence.
- **Rollback/compatibility notes:** Auction profile/fixtures are additive; disable Bridge mode to preserve previous controls.

## Phase G — Original House-Building migration

- **Objective:** Prove includes, instances, scale, dynamic auctions, organization/build phases and complex resync generically.
- **Why:** House is the strongest available case for source composition and evolving runtime identity.
- **Current state:** No accepted official Bridge coverage; legacy importer fails/partially reconstructs the original case.
- **Target state:** 22 actual agent incarnations and observed artifacts/boards/relations flow through the same generic architecture with explicit unsupported formation/cross-phase facts.
- **Files/classes affected:** Generic directive/provenance, snapshot scaling and identity code if tests expose defects; House fixtures/profile/tests only for expected data.
- **Exact dependencies:** Phases B–F; official local/external Jason includes; House Java artifacts; Moise OS; JaCaMo/ORA4MAS runtime.
- **Implementation steps:** Verify dependency/source manifests; prove official include/instances; capture staged snapshots/events; verify exact relation-scoped hierarchy/cardinality/mission/OPlan Bridge facts and explicit V2 loss; inject disconnect at phase boundary; replay and compare fingerprints; scan production for case constants.
- **Non-goals:** Static inference of nondeterministic winners, representation of formation compatibility by invented V2 feature, claiming unobserved external template semantics.
- **Risks:** Runtime length/flakiness, external include drift, event volume, dynamic model revision, non-deterministic winner outcomes.
- **Tests:** Include cycles/path/version; declaration-to-incarnation; 22-agent identity; repeated role/subgroup multi-context cardinality; eight-auction lifecycle; sequence/parallel; reconnect during transition; bounded queues.
- **Regression gates:** Hello/Auction and all frozen/backend suites pass; no case-specific generic code; resource budgets recorded rather than guessed.
- **Evidence outputs:** House manifests, phase snapshots, event log, identity graph, verification reports, scale metrics and unresolved register.
- **Definition of Done:** House supported scope passes generic official path and resync without stale/collapsed identities.
- **Rollback/compatibility notes:** House assets/profile removable independently; generic fixes remain covered by earlier cases.

## Post-Phase-G decision gate — Runtime Verification Projection Review

After Bridge plus the three canonical cases stabilize, review each captured runtime fact (mission commitment, organizational-goal state, norm instance/lifecycle, scheme/group runtime context) and decide one of: remain evidence-only; receive a target-only USE runtime projection outside frozen Runtime Mapping V2; or require a separately approved metamodel version. The same evidence may open a separate V2.x/V3 review for full relation-level cardinality. This gate records a decision; it does not authorize frozen-resource changes, and it is not a prerequisite for facts whose evidence-only status already satisfies the thesis scope.

## Phase H — Production transport selection, security and resilience

- **Objective:** Benchmark, select and harden one production Bridge transport while retaining the already-proved neutral contract and independent JVMs.
- **Why:** Phase D proves process independence; the production mechanism still requires measured deployment, security, resilience and packaging evidence.
- **Current state:** The Phase-D separate-JVM boundary smoke passes over a minimal/test mechanism; production transport remains undecided.
- **Target state:** Authenticated bounded request/stream client/server supports negotiation, snapshot, acknowledgements, resume/resync and shutdown under failures.
- **Files/classes affected:** `BridgeTransport` SPI implementation, endpoint/client configuration, credential/redaction handling, process integration harness; no semantic adapter/mapping changes.
- **Exact dependencies:** Approved transport ADR/library version; contract schema; JDK 21; packaging for both JaCaMo and USE.
- **Implementation steps:** Benchmark candidates; threat/deployment review; select through ADR; implement negotiation/limits/backpressure/resume/ack; add authentication and TLS where remote; package; fuzz and failure-inject partitions/restarts/malformed payloads; confirm semantic results match the Phase-D boundary proof.
- **Non-goals:** Remote control of JaCaMo, Java serialization, semantic changes to fit protocol, multiple production transports initially.
- **Risks:** Dependency/classloader conflict, insecure defaults, buffer memory, incompatible proxies/firewalls, false exactly-once claim.
- **Tests:** Separate-JVM Hello/Auction/House over the selected production transport; kill/restart; overflow; schema mismatch; auth failure; fuzz/size/decompression; packaging/performance; thread/resource cleanup.
- **Regression gates:** In-memory/recorded contract fixtures still pass; no live object crosses process; localhost default; no secret/path leakage.
- **Evidence outputs:** ADR with measurements, threat model, compatibility matrix, failure-injection reports, package manifests/SBOM.
- **Definition of Done:** Selected transport preserves contract semantics and recovers safely across tested failures in separate processes.
- **Rollback/compatibility notes:** SPI permits fallback to recorded/in-memory test transport; legacy plugin remains available through Phase I.

## Phase I — Switch the authoritative frontend

- **Objective:** Make official Bridge input the default production authority after all acceptance gates.
- **Why:** This realizes the intended architecture and stops custom reconstruction from deciding production semantics.
- **Current state:** Bridge is opt-in/shadow; legacy importer/connectors remain default.
- **Target state:** `DefaultJaCaMoFacade` defaults to Bridge client; legacy requires an explicit compatibility flag and emits deprecation scope.
- **Files/classes affected:** facade/bootstrap/UI/config/documentation; readiness/diagnostic surfaces; packaging descriptors.
- **Exact dependencies:** Accepted Phases A–H and all case/evidence gates; selected transport package.
- **Implementation steps:** Add migration/config validation; expose endpoint/capabilities; switch default; prohibit silent fallback; update user docs; run upgrade/rollback rehearsal.
- **Non-goals:** Remove legacy code, alter frozen artifacts, claim unsupported semantics.
- **Risks:** Deployment configuration failures, user reliance on undocumented legacy behavior, silent stale mirror.
- **Tests:** Fresh/upgrade config, unavailable Bridge, explicit legacy opt-in, UI error/readiness, full regression/canonical matrix.
- **Regression gates:** Zero silent fallback; all required capabilities visible; rollback rehearsal passes; evidence reviewed.
- **Evidence outputs:** Release candidate reports, config migration guide, default-path trace, approval record.
- **Definition of Done:** Supported installations use Bridge authority by default and fail clearly when it is unavailable/incompatible.
- **Rollback/compatibility notes:** One supported release window keeps explicit legacy flag; rolling back config/package restores previous default without rewriting models.

## Phase J — Deprecate and remove proven legacy frontend pieces

- **Objective:** Retire custom authority only after its replacement and rollback window are proven.
- **Why:** Avoid permanent duplicate semantics and reduce maintenance without premature deletion.
- **Current state:** Legacy JCM/Jason/CArtAgO/Moise frontend and in-process connectors still compile behind compatibility mode.
- **Target state:** Deprecated components are isolated/removed according to the disposition table; immutable historical tests/evidence remain.
- **Files/classes affected:** `JcmLexer`, `JcmSemanticParser`, `JcmProjectLoader`, `JasonSourceParser`, `CartagoSourceExtractor`, `MoiseXmlParser`, old connector registry/composite and associated packaging/tests; exact removals require a separately approved diff.
- **Exact dependencies:** Phase I usage/evidence, user approval, replacement coverage map, archival policy.
- **Implementation steps:** Measure remaining use; prove each replacement test; archive source/goldens/evidence; remove registrations then classes in reviewed slices; update docs/dependency graph; run mutation test preventing fallback reintroduction.
- **Non-goals:** Delete semantic IR/backend/trace/OCL, erase historical evidence, remove a component with an unfilled capability.
- **Risks:** Hidden utility coupling, lost diagnostic functionality, test coverage deletion disguised as cleanup.
- **Tests:** Compile/dependency analysis; full regression; canonical cases; package smoke; source scan for removed registrations/fallback.
- **Regression gates:** One-to-one replacement evidence for every removal; no test merely deleted; explicit approval; frozen artifacts unchanged.
- **Evidence outputs:** Removal ledger, archived legacy manifest, dependency reduction report, final disposition status.
- **Definition of Done:** No production semantics depend on custom frontend/in-process authority and every removed capability has proven replacement.
- **Rollback/compatibility notes:** Preserve a tagged pre-removal release and migration guide; rollback is version-level after the compatibility window.

## Phase K — Thesis evidence, final regression and packaging

- **Objective:** Produce auditable final artifacts and claims aligned exactly with validated scope.
- **Why:** The thesis contribution is the authority/adapter/formal-mirror method, not just a green build.
- **Current state:** Phase-specific evidence exists; final reproducibility and claim matrix not assembled.
- **Target state:** Clean reproducible release candidate with complete A–K evidence, case matrices, limitations, package/SBOM and no unresolved release blocker.
- **Files/classes affected:** Release/evidence/docs/build packaging only unless a defect is found and returned to its owning phase.
- **Exact dependencies:** All prior phase artifacts; clean JDK/Maven/JaCaMo environments; USE 7.5.0; selected Bridge distribution.
- **Implementation steps:** Clean-build all modules; verify frozen hashes; run all cases/cross-process/security/determinism/performance gates; assemble evidence index; independently verify claims; tag/package only after approval.
- **Non-goals:** Last-minute feature/refactor expansion, hiding limitations, changing goldens without semantic review.
- **Risks:** Evidence drift, non-reproducible environment, overclaiming original Auction/House semantics, ignored skips.
- **Tests:** Complete suites and clean-environment replay; package install/uninstall; evidence schema/link/hash validation.
- **Regression gates:** Zero unexpected failures/skips; known semantic limits present in README/reports/thesis; all source/package hashes verified.
- **Evidence outputs:** Final matrix, test reports, contract recordings, models/traces/reports, performance/security results, SBOM, reproducibility guide.
- **Definition of Done:** A reviewer can reproduce every stated capability and distinguish supported subset, limitation and unsupported semantics.
- **Rollback/compatibility notes:** Release artifacts are immutable; defects return to the owning phase and generate a new candidate, never an in-place evidence edit.

## Global phase rule

No phase may modify frozen V2 resources, weaken a test, hide an unsupported fact, introduce case branching, or patch JaCaMo core without a new evidence-backed proposal and user approval. Phase completion is not authorization to start the next phase when its review gate requires approval.
