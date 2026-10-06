# Runtime inventory — snapshot verification migration

## Current disposition after migration — 2026-10-06

Attribute cleanup uses native projection 3.0.0 and `NativeObjectBindings` for
exact aliases/provenance. Snapshot 1.2.0 and replay 2.0.0 preserve metadata outside
MAttributes. Current attribute inventory and acceptance evidence are in
[exposed-attribute-audit.md](exposed-attribute-audit.md) and task.md section 11.
References to section 23 below describe the preceding completed task revision.

Workbench UI is simplified on 2026-10-06: the independent Projection Rules tab
contains Rule / JaCaMo Concept / USE Concept. Import, Load OCL and Start Runtime
keep their existing facade calls. GoalViewPanel and RuntimeHistoryRows are removed;
the snapshot, journal, Goal DTO, trace, OCL/control and replay infrastructure below
is retained. Earlier UI evidence describes the earlier revision.

| Path | Final disposition and consumer |
| --- | --- |
| Official platform, BridgeAgArch, typed snapshot sources, authenticated transport | KEEP; one source topology and ordered bounded stream; callback-triggered checkpoints and explicit health observation |
| BridgeControlledLauncher / BridgeLocalExecutionControl / RuntimeControlContract | ADD; official reasoning-cycle control, exact all-agent ACK and capability gating; no console pause or domain repair |
| NativeRuntimeProjector / MutationEngine / RuntimeVerificationCoordinator | MIGRATED; one EDT writer and checkpoint snapshot/OCL flow on the active Session system |
| NativeOperationCheckpointEvaluator / compiler constraint registry | ADD/MIGRATED; typed exact PRE/POST/@pre, dependency routing and fingerprint-bound enforcement |
| RuntimeControlService / VerificationViolation / SnapshotRetention | ADD; freeze-before-request, separate control/sync states, authoritative confirmation/resume, bounded immutable evidence |
| BridgeCheckpointSource | ADD; explicit source/correlation/watermark and observed causation; no boundary domain object |
| GoalViewSnapshot | ADD; immutable presentation read over the active coordinator, supported exact board state only |
| RuntimeEventJournal / replay / reanalysis / step controller | KEEP/MIGRATED; capability/policy/checkpoint timeline, strict hashes and explicit detached replay mode |
| Old connector/mapping/mutation/verifier/bridge-adapter implementations (61 classes) | TEST_ONLY; moved out of main with preserved historical tests; forbidden in production JAR |
| Default facade LEGACY_V2 branch, old binding APIs/UI, Skeleton facade | REMOVED; native constructor rejects the old mode, status/menu actions share the Session facade |
| Frozen Ecore/Mapping/OCL/runtime resources | HISTORICAL_ONLY; bytes preserved, no production automatic load/fallback |

Persistent Object Diagram follow-up (2026-10-05): runtime schema extension now
initializes stored attribute slots and ordinary association link sets in the
existing `MSystemState`; it never calls `MSystem.reset()`. Atomic native view
updates rebind recreated objects and their incident links to current object
instances, including when USE reuses an object name for another class. Hidden
binary association-class nodes/edges are resolved from both visibility maps;
recreated calculated placement binds fresh edge waypoints instead of disposed
ones. Hidden state and fixed node positions are retained. Native graph/attribute parity is checked
through import, live mutation, resync, pause/resume and reconnect; exact commands,
counterexamples and phase evidence are recorded in task.md section 23.

The chronological Phase A audit below is **historical baseline**, including its
then-current MIGRATE/BLOCKED/proposed statuses. It is not the final runtime path.
Current commands/results and before/after measurements are in task.md section 23.

Audit checkpoint: 2026-10-05. Branch `main`, HEAD `55f38081`; preflight had
94 tracked modified / 18 untracked files and empty staged diff. The full preflight
diff is preserved in `target/runtime-snapshot-preflight.diff`. Existing changes
implement generic projection policy 2.1.0 and native live relations; they are
retained. This inventory describes the current code, not completed migration.

## Active entry points and ownership

| Path / producer | Event or snapshot | Ordering / identity | Mutation / verification target | Lifecycle owner and consumer | Disposition |
| --- | --- | --- | --- | --- | --- |
| JaCaMoWorkbenchAction / DefaultJaCaMoFacade native constructors | Official ModelSnapshot, RuntimeSnapshot, RuntimeEvent | Bridge session/generation/revision + exact ids | Native pipeline's active USE MSystem | Facade workspace; normal USE Session | KEEP entry, MIGRATE workflow |
| LiveJaCaMoLauncherMain / ManagedStartupControl | Waiting/start request/start ACK | Exact project/run/producer ownership | Official `startAgs()` only | Launcher process; ManagedRuntimeWorkflow | KEEP startup boundary; no pause authority |
| JaCaMoBridgePlatform | Typed events and stable snapshots | Per-source watermarks, bounded queue, generation | TCP publication, never USE domain mutation | Official Platform start/close | MIGRATE checkpoint/control integration |
| BridgeAgArch / BridgeRuntimeRegistry / JasonSnapshotSource | Agent birth/death, changed current beliefs/goals, action lifecycle | Agent incarnation, occurrence tokens, declaration binding, source sequence | Native Agent/selected Belief/AgentGoal state | One architecture chain per official agent, registry handle | KEEP exact evidence; MIGRATE boundary metadata |
| CartagoSnapshotSource / OfficialCartagoAdapter | Workspace/agent/artifact/property/operation/focus cuts, logger events | Official UUID/workspace-qualified identities; no bare-name target lookup | Workspace/Artifact/property/focus/join rules | SnapshotSource attach/close registers/unregisters workspace loggers | KEEP identity and lifecycle; MIGRATE operation checkpoints |
| MoiseBoardSnapshotSource | GroupBoard/SchemeBoard cuts and parent relations | Organisation/board-instance-qualified BridgeEntityId, players bound to declarations | Native role links, Scheme/Mission/Goal instances | Platform owns disjoint declared/dynamic sources | MIGRATE polling to supported checkpoint capture; global coverage stays PARTIAL |
| NplBoardSnapshotSource | NormativeBoard listener events and cuts | Exact board/interpreter ids and source sequence | Evidence-only normative state | Source owns interpreter listeners and close | KEEP trace/unsupported boundary |
| SnapshotCoordinator | Stable topology/watermark cut plus buffered replay | Capture retries and monotonic per-source watermarks | Contract RuntimeSnapshot | Platform source lifecycle | KEEP, MIGRATE checkpoint source metadata |
| LocalTcpBridgeServer / LocalTcpBridgeTransport | Authenticated loopback framed protocol | Nonce/HMAC, bounded replay/subscriber queues and ACK tokens | None directly | Server, subscription thread, BridgeClient | KEEP transport; control needs a versioned capability/command contract |
| BridgeClient / BridgeMirrorStateMachine | Validated ordered events and bootstrap replacement | Session/generation/revision, ledger, sequence/incarnation guards | Neutral fact evidence before projection | Facade owns connection and replacement | KEEP fail-closed ordering |
| NativeRuntimeProjector / NativeRuntimeMutationEngine | Snapshot and single event transactions | Exact runtime id → semantic alias → native target; savepoint rollback | Same native active MSystem; model extensions restore state in that system | Facade NativeWorkspace/projector | KEEP; MIGRATE checkpoint routing |
| RuntimeVerificationCoordinator / ExternalOclConstraintService | BASELINE/SNAPSHOT/EVENT/PROFILE/MANUAL/COVERAGE records | EDT single writer, stateVersion, constraint hash, source coverage | Full eligible native invariants on materialized changes | Native projector/coordinator | MIGRATE into the single checkpoint/snapshot verifier |
| RuntimeEventJournal / RuntimeCheckpointStore | Bounded tail and capped disk recording; baseline + eight snapshot files | Exact state/result hashes and persisted ordinal | Diagnostic history, no second active model | Coordinator run directory/close | KEEP recording; MIGRATE failing-snapshot pinning and typed retention |
| NativeReplayStepController / NativeRuntimeReplay / NativeRuntimeReanalysis | Explicit offline recording/reanalysis | Immutable recorded protocol and hash verification | Isolated replay MSystem or explicitly activated replay Session | Explicit replay action, live delivery detached | KEEP offline purpose, forbid as a simultaneous live verifier |
| DefaultJaCaMoFacade LEGACY_V2 branch / NativeSemanticAdapter / BridgeRuntimeProjector | Frozen V2 adaptation and legacy RuntimeEvent | Legacy trace binding + bridge admission | Workspace DirectUseBackend system + RuntimeMutationEngine / RuntimeVerificationEngine | Still reachable by explicit PipelineMode.LEGACY_V2 constructor | REMOVE production branch after replacement tests pass; do not call it historical yet |
| RuntimeMirrorService / CompositeRuntimeConnector / JasonRuntimeConnector / CartagoRuntimeConnector / MoiseRuntimeConnector | Old connector DTO snapshots/events | Old OrderedRuntimeEventQueue / RuntimeIdentity / connector generation | Old RuntimeMutationEngine and observer verifier | No construction caller in current production facade; direct historical tests | TEST_ONLY/HISTORICAL candidate; migrate tests before deletion |
| RuntimeMappingLoader / RuntimeMappingValidator / V2RuntimeRuleRegistry | Frozen runtime JSON mapping | Mapping hash/id and frozen rules | Legacy mutation/checkpoint engine | Explicit legacy branch and historical tests | REMOVE active load; preserve frozen resources as HISTORICAL_ONLY |

## Hooks, queues, polling and verification triggers

Jason observation uses `BridgeAgArch.init`, `reasoningCycleFinished`, `act`,
`actionExecuted` and `stop`; source attach does not install a second live Jason
architecture. The old JasonRuntimeConnector separately installs Circumstance and
Goal listeners, but has no current native construction caller. CArtAgO installs
one logger per discovered workspace and publishes operation requested/started/
completed/failed, new percept, artifact birth/disposal, focus/unfocus and join/quit.
NPL listeners attach per official interpreter and detach on source close.

The platform publisher drains its bounded event queue and polls snapshot sources
every 500 ms. Moise board polling currently produces verification-relevant native
mutations, so this timer is a **migration target**, not acceptable final primary
checkpoint semantics. Server subscribers have bounded queues/replay. Client has a
bounded bootstrap buffer and one live socket reader. Native facade delivery uses
a bounded pending buffer; coordinator marshals the single writer to the EDT.
Old RuntimeMirrorService has a scheduled drift executor and an old ordered queue;
it is not used by the active native path.

Current native events run full eligible invariants whenever mutation returns
MATERIALIZED; no safe dependency-targeted checkpoint routing exists yet. Native
operation metadata is materialized, but operation logger events are trace-only;
there is no native PRE/@pre/POST contract evaluator. The legacy verifier has
operation correlation, PRE/POST and a ConstraintDependencyIndex; reuse its
semantics after extracting it from the obsolete production pipeline. Never treat
OP_FAIL as a successful POST or guess an operation exit from elapsed quiet time.

## System/state construction and resynchronization

NativeUseStateBuilder creates the import MSystem; NativeUseSessionActivator
attaches it to the normal USE Session. Compatible native resync reuses that
projector/system, applies authoritative snapshot and drains buffered events.
NativeRuntimeMutationEngine performs domain mutations under coordinator EDT
transactions with rollback; supported model extensions reset/restore state in
the same MSystem. Old callbacks are gated by workspace delivery ownership.

DirectUseBackend constructs the legacy system; remove its active runtime consumer.
NativeUseSoilExporter creates an isolated validation/recompile system;
NativeRuntimeReplay/Reanalysis create explicit offline systems. Step replay may
replace the active Session only after live observation is detached. These are
not permission to create a hidden concurrent verification system.

Current gap/coverage handling marks STALE/SKIPPED. Native authoritative snapshots
reconcile complete CArtAgO relation absence; partial Moise absence never deletes
boards. No general measured zero-supported-subset drift report exists in the
native facade yet; legacy RuntimeMirrorService.compareSnapshot/drift monitor
cannot be used as evidence for native drift support.

## Duplicate/legacy review

KEEP exact native policy/identity/trace, platform/transport lifecycle, same-session
activation and offline replay boundaries. MIGRATE native full-every-mutation
verification, timer-driven Moise observation, diagnostic snapshot storage and
primary UI. REMOVE explicit legacy runtime branch, old live verifier/mutation
path and dead connector construction surface only after migrated tests pass.
Frozen mapping files and prior measured reports are HISTORICAL_ONLY, not runtime
fallback. Direct legacy connector tests are TEST_ONLY candidates. Runtime-control
semantics are BLOCKED_REVIEW; see `jacamo-pause-resume-audit.md`.

`RuntimeMappingLoader.loadDefault` loads the frozen current V2 resource; its
separate `HISTORICAL_ROOT` points to version-1 resources only for explicit
historical access. There is no default V1 fallback. The old
`runtime.RuntimeSnapshot` DTO remains consumed by old connector/verifier tests;
it is not safe to delete before migrating those consumers. The native
`codegrounded.runtime.VerificationSnapshot` is currently just a consistent
result/profile read, not the typed retained verification-state contract in
Phase D. Neither DTO establishes runtime pause authority.

Producer observation alone does not evaluate OCL. The native mutation engine
returns its disposition to RuntimeVerificationCoordinator; MATERIALIZED invokes
the full eligible invariant set. Snapshot, manual verification, profile change
and coverage change have their own coordinator entries. The legacy branch
instead invokes RuntimeVerificationEngine through BridgeRuntimeProjector.
Transport, registry, journals and offline exports are not additional live OCL
evaluators. This distinction is the verification trigger for the paths above.

No new duplicate listener was found in the active path; reopening Workbench starts
only a cached status timer, not a network subscription. No runtime JCM/ASL parse
occurs per native event. Moise polling rebuilds its official OS representation;
its caching/change-boundary design needs review during migration. Registry lookup
by agent display name is producer-side declaration evidence; native target binding
still requires exact declaration/runtime ids. Do not substitute that registry
lookup for target identity resolution.

## Fresh baseline evidence

`target/runtime-snapshot-baseline.log`: **205 tests / 16 suites PASS**, zero
failures/errors/skips; six-module package/verify completed 2026-10-05 10:47:55 +07.
Focused coordinator, native runtime/replay/trace, facade, workflow, Workbench,
functional/environment relation and selective projection suites ran with native
OCL/Shell regression and actual ManagedAuctionWorkbenchIT.

Auction evidence: `use-plugin/target/workbench-acceptance/auction-1791171951433`.
Current native inventory: 17 classes, 36 objects, 72 links, zero exposed Beliefs.
All five official agents have 61 raw current beliefs each (305 total), rather
than accumulated native history. Selective lifecycle/negative-provider tests
already pass; retain this code. Ten focus / ten join / five role / ten commitment
links exist before resync and preserve parity afterward. GUI screenshots,
recorded replay/reanalysis and same active system assertions pass. Baseline
checkpoint behavior is SNAPSHOT plus full invariant checks after materialized
events; no automatic runtime pause or Goal View exists.

The 17 exposed classifier names are Agent, AgentGoal, Artifact, AuctionArtifact,
Belief, Group, Mission, Organization, OrganizationalGoal, Scheme, Workspace,
auctionGroup, auction_Organization, auction_capabilities_Agent, doAuction_Scheme,
auctioneer and participant. The last two are contextual Agent–Group
**association classes**, with five link objects in total, not ordinary Role
classes. The 36-object inventory includes those link objects: five program
agents, two workspaces, two AuctionArtifact instances, one organisation, one
group, two runtime schemes, twelve organisational goals, six missions and five
role link objects. Classes with no instances remain in the model.

Additional trace gate `target/runtime-snapshot-trace-baseline.log`:
**7 tests / 3 suites PASS**, zero failures/errors/skips, completed
2026-10-05 10:58:16 +07. RuntimeTraceTest, CodeGroundedPhase6Test and
NativeRuntimeBridgeCoverageTest provide explicit trace/current-source regression.

Migration has not started beyond audit tests/documentation. No production path
has been removed while the control semantics decision is unresolved.
