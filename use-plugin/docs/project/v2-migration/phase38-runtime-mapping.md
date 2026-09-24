# Phase 38 Runtime Mapping V2 reconciliation

Status: DONE; focused 19/19, full reactor 355/355 and post-merge 10/10 PASS. Commit 9cefe347 merged and pushed; metrics-only remote update preserved.
Canonical V2 resources remain WORKING_BASELINE; runtime mapping remains WORKING.

## Pinned source capabilities

The installed research pack's SOURCE_MANIFEST, 00_RESEARCH_BASELINE,
02_RUNTIME_CAPABILITY_MATRIX and IMPLEMENTATION_RECONCILIATION distinguish upstream
facts from proposed target mappings. pom.xml still pins Jason 3.3.0, CArtAgO 3.1,
Moise 1.1, Java 21 and USE 7.5.0; JaCaMo integration target is 1.3.0. Research
upstream JaCaMo commit 3866858a7ebf6be85d9199c13a09cf4bfb8191be uses Jason 3.3.2;
it is not the plugin dependency baseline. No dependency or connector upgrade.

| Source/API | Normalized events and payload | V2 effect / boundary |
|---|---|---|
| CArtAgO controller.getArtifactInfo, WorkspaceLogger.newPercept | OBS_PROPERTY_ADDED/CHANGED/REMOVED; exact bound artifact/property and typed scalar | VP002 attribute set/unset; AFTER_MUTATION; unknown property cannot mutate |
| WorkspaceLogger.opStarted/opCompleted/opFailed | OP_ENTER/EXIT/FAIL; OpId correlation, operation/signature, arguments/error | VP003 exact invocation; PRE/POST/failure checkpoints; CArtAgO is execution authority |
| artifactCreated/artifactDisposed | ARTIFACT_CREATED/DISPOSED with exact incarnation and workspace/artifact binding | TRACE_ONLY; no inferred runtime instance creation policy |
| newPercept signal | SIGNAL, label and values | TRACE_ONLY; no projected signal state |
| agentJoined/agentQuit, artifactFocussed/artifactNoMoreFocussed, artifactsLinked | upstream APIs exist; connector does not override them | explicit unsupported/deferred normalization and runtime relation policy; no invented link mutation |
| Agent.getBB, CircumstanceListener.eventAdded | BELIEF_ADDED/REMOVED, literal and exact agent alias | TRACE_ONLY; initial source belief objects do not prove mutable runtime literal identity |
| GoalListener started/finished/failed | GOAL_ADOPTED/REMOVED/ACHIEVED/FAILED, trigger/state/reason | TRACE_ONLY; no fabricated dynamic goal object |
| JasonMonitorAgArch.act/actionExecuted | ACTION_STARTED/SUCCEEDED/FAILED, exact ActionExec correlation | agent-side TRACE_ONLY; never duplicate CArtAgO operation enter |
| explicit message bridge | MESSAGE_SENT/RECEIVED | TRACE_ONLY; automatic inbound mailbox capture and intention lifecycle deferred |
| Moise OE and observed boards: agents, roles, missions, schemes/groups, goals | organisation/group/scheme, ROLE_ADOPTED/REMOVED, MISSION_COMMITTED/REMOVED, SCHEME_STATE_CHANGED; qualified instance keys | deterministic poll/diff, TRACE_ONLY; observation keys preserve instance context, not mutable specification targets |
| Moise responsible groups | upstream readable, no connector delta/target policy | deferred; no inferred structural relation mutation |
| OE derived permissions/obligations | normative snapshot only | bounded entitlement evidence; no NPL activation/violation lifecycle and no OCL conversion |
| NORM_STATE_CHANGED | taxonomy placeholder | UNSUPPORTED, no lifecycle claim |

Moise OE is organisational semantic authority. Organisation-board transport does
not create a second role/mission mutation. Polling observes endpoints, not every
intermediate transition; composite snapshot is not an atomic three-runtime cut.
Historical Phase 16 missing-callback descriptions are superseded by current
CartagoRuntimeConnector incarnation checks and lifecycle callbacks, and bounded
Moise normative snapshot/board support. These do not expand target semantics.

## Exact binding and generic actions

The 37 canonical rules retain their source selectors, authority, identity,
correlation, required payload, exact anchor, target kind, mutation/checkpoint,
unsupported reasons and evidence. RuntimeMappingCompatibility derives a per-rule
report, including independent authoritative rank targets for REPLACE_ORDER.
RuntimeKey -> active SemanticId V2 -> traced USE target remains mandatory. Structural
Mapping V2 and Runtime Mapping V2 are separate resources and loaders. V2 binding
validation uses exact class/reference/VP002/VP003/order descriptors. The target
contract pins mappingId, Ecore hash and Structural Mapping hash; historical versions
and incompatible fingerprints fail before mutation, without fallback.

CREATE_OBJECT/DESTROY_OBJECT/SET_ATTRIBUTE/INSERT_LINK/DELETE_LINK and operation
enter/exit/fail remain generic mechanics. REPLACE_ORDER is the existing generic
complete authoritative order action from Phase 35, not a new case action. Bare
ordered membership changes still require authoritative resynchronization.
No case names, instance-specific IDs or OCL expressions are introduced into rules.

## Repair and gates

A schema-valid custom rule could previously invert INSERT_LINK/DELETE_LINK while
changing action and mutation consistently. A source-backed negative control
reproduced acceptance. Validator now rejects incompatible source event/action
pairs independently of target binding, including property removal versus set and
normalized SET_ATTRIBUTE versus unset. Duplicate selector diagnostics are checked
first. This is source-semantics validation, not another event dispatch table.

Focused gate: 19/19 PASS, including actual pinned CArtAgO/Jason/Moise connectors,
board snapshot, composite, exact aliases, order projection and mapping negatives.
Canonical JSON/schema, Structural Mapping, Ecore and golden hashes are unchanged.
Cross-layer review: active V2 metamodel/mapping -> unchanged IR/transformation ->
Phase 36 trace -> Phase 37 OCL -> validated runtime rule -> exact MSystemState target
-> existing verification observer. Runtime mirror correctness is the next gate;
this phase does not claim broader runtime OCL coverage or final freeze.
