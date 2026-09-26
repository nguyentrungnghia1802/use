# Final feasibility verdict and answers to the 60 pre-implementation questions

## Executive verdict

The target architecture is **`FEASIBLE_WITH_ADAPTER`**. JaCaMo can remain the semantic/execution authority and USE can become a formal verification mirror through an independent Bridge that uses official APIs and the official `jacamo.platform.Platform`/Jason `AgArch` extension points. No audited capability currently requires a JaCaMo core patch.

The statement is deliberately narrower than “everything is directly exposed” or “frozen V2 represents every official fact exactly.” CArtAgO descriptors/properties become authoritative after initialization, Jason does not expose a universal event for every belief-base mutation, Moise role/subgroup cardinality is relation-scoped, several runtime facts have no guaranteed faithful USE target, and no official API yields one globally atomic cut across Jason, CArtAgO and Moise. The sound design therefore uses capability-labelled immutable snapshots, per-source event watermarks, exact identity, buffered validated cuts, explicit projection status and resync.

## Capability verdicts

| Capability | Verdict | Evidence/limit |
|---|---|---|
| Official JCM parse to project object | `FULLY_FEASIBLE` | `JaCaMoProjectParser.parse` returns `JaCaMoProject` without launch |
| Launcher-equivalent load-only semantics | `FEASIBLE_WITH_ADAPTER` | Source paths/directives/packages must be configured like `JaCaMoLauncher.init`; there is no single safe `loadOnly()` facade |
| Jason program/AST extraction | `FULLY_FEASIBLE` | `Agent.parseAS` + `jason.pl.PlanLibrary`/AST; adapter still normalizes to contract/V2 |
| Complete static CArtAgO model before init | `FEASIBLE_WITH_LIMITATION` | Dynamic artifacts/properties/operations require initialization/runtime; Java source is not authority |
| Official Moise OS extraction | `FULLY_FEASIBLE` | `OS.loadOSFromURI` + SS/FS/NS graph |
| Runtime Jason observation | `FEASIBLE_WITH_ADAPTER` | Goal/action/plan hooks available; belief deltas need reconciliation |
| Runtime CArtAgO observation | `FEASIBLE_WITH_ADAPTER` | Controllers/descriptors/logger callbacks; name-based info lookup must be ID-revalidated |
| Runtime Moise/ORA4MAS/NPL observation | `FEASIBLE_WITH_ADAPTER` | Group/Scheme boards and NPL listener/collections |
| Exact relation-scoped Moise cardinality capture | `FULLY_FEASIBLE` | Preserve `(group, role, min, max)` and `(parentGroup, subGroup, min, max)` from official APIs in Bridge/provenance |
| Frozen-V2 projection of relation-scoped cardinality | `FEASIBLE_WITH_LIMITATION` | `SUPPORTED_SUBSET` or `REPRESENTATION_LOSS` when intrinsic attributes lose context; never arbitrary merge/select |
| Runtime fact capture | `FEASIBLE_WITH_ADAPTER` | Commitments, goal state, norm lifecycle and scheme/group instance context remain exact contract/evidence facts |
| Runtime fact OCL queryability | `FEASIBLE_WITH_LIMITATION` | Only faithfully materialized dependencies are queryable; evidence-only facts capability-block dependent OCL |
| One globally atomic cross-subsystem snapshot | `NOT_FEASIBLE_WITH_CURRENT_API` | Validated interval cut with per-source watermarks is feasible and selected |
| Exact cross-dimensional mapping | `FEASIBLE_WITH_LIMITATION` | Only official/config/runtime/binding evidence; unresolved otherwise |
| Bridge as independent module | `FEASIBLE_WITH_ADAPTER` | Compile near JaCaMo runtime and expose neutral DTOs |
| Bridge through official hook | `FULLY_FEASIBLE` | `Platform.setJcmProject/init/start/stop`, plus default/custom `AgArch` |
| Independent JaCaMo and USE processes | `FEASIBLE_WITH_ADAPTER` | Neutral schema/BridgeClient boundary is proved by mandatory Phase-D separate-JVM smoke; production transport remains an evidence-driven Phase H decision |
| Specific production transport | `UNKNOWN_REQUIRES_MORE_AUDIT` | Must benchmark/security-review candidates; intentionally not selected here |
| Frozen V2 reuse | `FULLY_FEASIBLE` for migration scope | No gap blocks migration; known cardinality/runtime representation loss remains explicit and hashes stay unchanged |
| Existing planning/materialization/trace/OCL/backend reuse | `FEASIBLE_WITH_ADAPTER` | Replace input authority and strengthen version/identity guards |
| Automatic norm-to-OCL translation | `NOT_FEASIBLE_WITH_CURRENT_API` as an equivalence claim | NPL lifecycle and OCL have different semantics; explicit future translation proof required |
| Migration complexity | High but staged | Central frontend/runtime boundary changes; backend foundation is reusable |

## Where the current USE Plugin is architecturally misaligned

- `JcmSemanticParser` and `MoiseXmlParser` duplicate semantics owned by official JaCaMo/Moise loaders.
- `CartagoSourceExtractor` treats Java syntax as the primary source for concepts whose authoritative definitions/state can be dynamic after artifact initialization.
- `JasonSourceParser` already uses official Jason parsing but masks/works around official directive/include behavior rather than receiving the launcher-configured semantic program.
- `SemanticResolver` exists partly because separately reconstructed dimensions lack authoritative cross-links; the target resolver validates exact IDs/evidence instead of reconstructing meaning.
- current Jason/CArtAgO/Moise runtime connectors hold in-process Java objects and accept a URI-shaped label, so they do not provide an actual independent-process boundary.

The whole project is not “wrong.” Frozen V2, structural/runtime mappings, semantic IR boundary, projections/order evidence, planners, materialization backends, trace, OCL profiles and verification engines are the valid foundation. Most require a new authoritative data source, not replacement.

## Major blockers and recommendation

No current blocker requires a core patch or prevents Phase A after user approval. The major implementation gates are API-version compatibility, Platform/AgArch lifecycle ordering, identity recreation, validated snapshot cuts, dynamic model revisions, faithful runtime projection/OCL gating, early separate-JVM proof, event gaps/backpressure, production transport security and original-case evidence. Follow Phases A–K in `17-migration-roadmap.md`; do not big-bang replace or delete the legacy path.

Deliberately deferred semantic decisions are: whether full relation-level cardinality needs a V2.x/V3 or target-only projection; and, through the post-canonical `Runtime Verification Projection Review`, whether each captured runtime fact remains evidence-only, receives a target-only USE runtime representation, or requires a new metamodel version. Original Auction plan/deadline semantics and other already recorded API limits also remain explicitly bounded.

## Answers 1–10 — JCM/project

1. **Parser exact class?** `jacamo.project.parser.JaCaMoProjectParser`, generated from `jacamo/src/main/javacc/JaCaMoProjectParser.jj` at JaCaMo revision `3866858a7ebf6be85d9199c13a09cf4bfb8191be`. The audited entry is `parse(String directory)` on a parser constructed with the JCM input stream.

2. **Output object?** `jacamo.project.JaCaMoProject`, which extends Jason `jason.mas2j.MAS2JProject`.

3. **Project object fields?** Inherited MAS name/agents/source paths plus JaCaMo workspaces, organizations, institutions, custom platforms and platform parameters. Exact APIs include inherited `getAgents()` and JaCaMo `getWorkspaces()`, `getOrgs()`, `getInstitutions()`, `getCustomPlatforms()`/`getPlatformParameters()`; parameter objects contain artifacts, configured groups/schemes, workspace/role/focus relations and instance policy.

4. **Includes resolve where?** JCM `uses` actions call `JaCaMoProject.importProject(directory,fileName)`, parse the imported `.jcm`/`.mas2j` and merge it. ASL `include` is separate: JaCaMo calls `registerDirectives()` and `Include.setSourcePath(project.getSourcePaths())`, then Jason's `jason.asSyntax.directives.Include.process(...)` resolves it. The Bridge must not merge source text itself.

5. **`instances` resolve where?** The parser stores instance policy in official agent parameters. Named forms clone/set names; `JaCaMoLauncher.createAgs()` expands numeric instances into concrete launch parameters. ModelSnapshot distinguishes declaration/template from actual live incarnations.

6. **Workspace declarations?** `JaCaMoProject.getWorkspaces()` returns `JaCaMoWorkspaceParameters`; each has its official name/configuration. Runtime `WorkspaceId` UUIDs do not exist at parse time.

7. **Artifact declarations?** `JaCaMoWorkspaceParameters.getArtifacts()` returns the configured artifact map/class parameters. It covers declarations, not every dynamic artifact or initialized observable property.

8. **Organization declarations?** `JaCaMoProject.getOrgs()` returns `JaCaMoOrgParameters` with OS source and configured group/scheme instances; `getInstitutions()` is separate and currently outside frozen V2 unless explicitly scoped.

9. **Group/player declarations?** `JaCaMoOrgParameters.getGroups()` exposes configured groups and player/role configuration parsed from JCM; `getSchemes()` exposes configured schemes. These are configuration facts; ORA4MAS boards provide live state.

10. **Load-only lifecycle point?** Yes: call the official parser without launcher `create/start`. There is also a post-`JaCaMoLauncher.init`, pre-`create` point. There is no single public `loadOnly()` that safely reproduces every source-path/directive/package action, so launcher-equivalent setup is `FEASIBLE_WITH_ADAPTER`.

## Answers 11–25 — Jason

11. **`.asl` parser exact class?** Public semantic entry points are `jason.asSemantics.Agent.parseAS(Reader,String)` and `parseAS(InputStream,String)` in Jason 3.3.2; they use the generated `jason.asSyntax.parser.as2j` parser. Use the `Agent` result rather than invoking grammar internals directly.

12. **Agent object?** `jason.asSemantics.Agent`; program APIs include `getInitialBels()`, `getInitialGoals()` and `getPL()`, while live APIs include `getBB()` and `getTS()`.

13. **PlanLibrary API?** `jason.pl.PlanLibrary implements Iterable<jason.asSyntax.Plan>` with `getPlans()`, `add(...)`, `remove(...)`, `getCandidatePlans(...)` and `addListener(jason.pl.PlanLibraryListener)`.

14. **Plan API?** `jason.asSyntax.Plan`; exact semantic accessors include `getLabel()`, `getTrigger()`/`getTriggerEvent()`, `getContext()` and `getBody()`.

15. **TriggeringEvent API?** `jason.asSyntax.Trigger`; `getOperator()`, `getType()`, `getLiteral()`, `isAddition()`, `isGoal()` and related predicates expose the triggering event.

16. **Plan body representation?** Linked `jason.asSyntax.PlanBody` nodes with `getBodyType()`, `getBodyTerm()` and `getBodyNext()`; `getPlanSize()` is available. Preserve order and full AST/provenance even though V2 selects only `Action` steps.

17. **BeliefBase?** Runtime `Agent.getBB()` returns `jason.bb.BeliefBase`; initial literals come from `getInitialBels()`. It is mutable runtime authority and must be copied, not transported as a Java object.

18. **Goal representation?** Initial goals are official `Literal` objects from `getInitialGoals()`. Goal events use `Trigger`/`jason.asSemantics.Event`, and live lifecycle is visible through `TransitionSystem`, `Circumstance` and `GoalListener`. V2 maps exact achievement/test kinds where exposed.

19. **Action representation?** Static external actions are `PlanBody` nodes whose body type is `action`; runtime execution is `jason.asSemantics.ActionExec` selected/executed by the architecture chain.

20. **Internal vs external action?** `PlanBody.BodyType.internalAction` versus `BodyType.action` is an official exact distinction. Belief/goal update/test body terms are other types and must not be forced into V2 `Action`.

21. **Source location?** `jason.asSyntax.SourceInfo` exposes `getSrcFile()`, `getSrcLine()`, `getBeginSrcLine()` and `getEndSrcLine()`. No source column was exposed in the audited class; the contract must not invent one.

22. **Include/directive model?** JaCaMo registers directives/source paths; `jason.asSyntax.directives.Include` has `setSourcePath(...)`, `getSourcePaths()` and `process(...)`. Use that official environment and preserve per-source provenance; unknown directives fail explicitly.

23. **Belief listener?** No universal public `BeliefBaseListener` was found in the inspected 3.3.2 API. `Circumstance.addEventListener(CircumstanceListener)` observes update events that reach the circumstance, but complete mutation coverage is unproven; snapshots/reconciliation are required.

24. **Goal listener?** `TransitionSystem.addGoalListener(jason.asSemantics.GoalListener)` with remove/get counterparts provides the official hook; `CircumstanceListener` also observes event-queue changes.

25. **Action lifecycle hook?** A custom official `jason.architecture.AgArch` observes `act(ActionExec)` and `actionExecuted(ActionExec)`. `RuntimeServices.registerDefaultAgArch(String)` can include dynamically created agents; `AgArch.init()/stop()` establish incarnation lifecycle.

## Answers 26–40 — CArtAgO

26. **Workspace API?** `CartagoEnvironment.getRootWSP()`/`resolveWSP(String)` return `WorkspaceDescriptor`; for local workspaces `WorkspaceDescriptor.getWorkspace()` yields `cartago.Workspace`, and `WorkspaceId` carries name/full name/UUID.

27. **Enumerate workspace?** Local `Workspace.getChildWSPs()` enumerates child descriptors. `CartagoEnvironment.getController(String)` obtains one workspace's `ICartagoController`; `getCurrentAgents()`/`getCurrentArtifacts()` enumerate its contents. Remote child enumeration is not proven by this local API and must be capability-labelled.

28. **ArtifactId?** `cartago.ArtifactId` with `getId()` returning UUID, plus `getName()`, `getArtifactType()`, `getWorkspaceId()` and `getCreatorId()`. The UUID, not local name, anchors live identity.

29. **Artifact type?** `ArtifactId.getArtifactType()` is exact for the live artifact; `ArtifactInfo.getId()` returns the corresponding full ID.

30. **Operation descriptors?** `ArtifactInfo.getOperations()` returns `List<OpDescriptor>`. `OpDescriptor.getOp()` returns `IArtifactOp`, whose `getName()`, `getNumParameters()` and `isVarArgs()` expose the signature basics; `ArtifactOpMethod` exposes a backing `Method` where applicable.

31. **Operation parameter names?** Only conditionally through `ArtifactOpMethod.getMethod().getParameters()`, and reliable only with `-parameters` or explicit official metadata. Otherwise publish positional unknowns. Dynamic operations may have no Java `Method`.

32. **Parameter types?** For a method-backed operation, Java reflection `Method.getParameterTypes()`/`getGenericParameterTypes()` provides them (`REQUIRES_REFLECTION`). For dynamic operations the audited descriptor guarantees name/arity, not types.

33. **Result type?** Artifact operation methods are generally `void`; output commonly uses `OpFeedbackParam<T>` parameters. The Bridge models feedback parameters explicitly and does not invent a single result type; dynamic operations may expose no result metadata.

34. **Observable property definition?** After creation, `ArtifactInfo.getObsProperties()` returns current `ArtifactObsProperty` objects. Properties can be defined dynamically by artifact code, so complete pre-initialization definitions are unavailable in general.

35. **Property value?** `ArtifactObsProperty.getValues()`/`getValue(int)` (and typed helpers), with `getName()`, `getId()`/`getFullId()`. Values belong to RuntimeSnapshot/Event state.

36. **Focus/unfocus event?** `ICartagoLogger.artifactFocussed(...)` and `artifactNoMoreFocussed(...)` provide the official callbacks.

37. **Join/quit?** `ICartagoLogger.agentJoined(...)` and `agentQuit(...)`.

38. **Create/dispose?** `ICartagoLogger.artifactCreated(...)` and `artifactDisposed(...)` with official `ArtifactId`.

39. **Operation start/end/fail?** `ICartagoLogger` exposes `opRequested`, `opStarted`, `opSuspended`, `opResumed`, `opCompleted` and `opFailed`, using `OpId` where available and carrying artifact/operation data.

40. **Authoritative snapshot?** Per-workspace point observations are authoritative: `getCurrentAgents()`, `getCurrentArtifacts()` and `getArtifactInfo(String)`. The name-based info lookup must be revalidated by comparing `ArtifactInfo.getId()` with the enumerated ID. No global atomic snapshot API was found; buffer + enumerate + revalidate + retry yields the selected consistent cut.

## Answers 41–57 — Moise/ORA4MAS/NPL

41. **OS parser?** Static `moise.os.OS.loadOSFromURI(String)` in Moise 1.1.

42. **OS root object?** `moise.os.OS`, with `getSS()`, `getFS()`, `getNS()` and `getURI()`.

43. **Role API?** `moise.os.ss.SS.getRolesDef()/getRoleDef(String)` and `moise.os.ss.Role`, including `isAbstract()`, group/link/norm accessors and inherited official ID from `MoiseElement`.

44. **Role hierarchy?** `Role.getSuperRoles()`, `getAllSuperRoles()`, `getSubRoles()` and related entailment accessors expose the official hierarchy.

45. **Group/cardinality?** `SS.getRootGrSpec()`; `moise.os.ss.Group.getRoles()`, `getRoleCardinality(Role)`, `getSubGroups()` and `getSubGroupCardinality(Group)`. `moise.os.Cardinality.getMin()/getMax()` supplies exact relation-scoped bounds. The Bridge preserves exact `(group, role, min, max)` and `(parentGroup, subGroup, min, max)` facts. Frozen `Role`/`Group` cardinality attributes are only `SUPPORTED_SUBSET` or `REPRESENTATION_LOSS` when context is lost; different contexts are never arbitrarily merged or selected.

46. **Link API?** `Group.getLinks()` returns `moise.os.ss.Link`; inherited `RoleRel.getSource()/getTarget()/getScope()/getExtendsToSubGroups()/isBiDir()` plus `Link.getTypeStr()` expose V2 link fields.

47. **Scheme?** `FS.getSchemes()/findScheme(String)` and `moise.os.fs.Scheme`; `getRoot()`, `getMissions()`, `getGoals()` and `getPlans()` expose the functional structure.

48. **Mission?** `Scheme.getMissions()/getMission(String)` and `moise.os.fs.Mission.getGoals()`; cardinality comes from `Scheme.getMissionCardinality(...)`.

49. **OGoal?** `moise.os.fs.Goal`; `getDescription()`, `getType()`, `getMinAgToSatisfy()`, `getTTF()`, `getPlan()`, arguments/dependencies and official ID.

50. **OPlan/operator?** `moise.os.fs.Plan`; `getOp()`, `getTargetGoal()` and ordered `getSubGoals()` expose sequence/parallel/choice decomposition and target.

51. **Norm?** `NS.getNorms()/getNorm(String)` and `moise.os.ns.Norm`; `getType()`, `getRole()`, `getMission()`, `getCondition()` and `getTimeConstraint()`.

52. **OE runtime root?** The current JaCaMo path does not expose one classic `moise.oe.OE` as its runtime authority. It uses CArtAgO ORA4MAS `OrgBoard`, `GroupBoard` and `SchemeBoard`; adapt those actual boards. Treat a classic OE adapter as a separate future capability.

53. **Role player?** `GroupBoard.getGrpState()` returns `ora4mas.nopl.oe.Group`; inherited `CollectiveOE.getPlayers()` returns `Player` objects with `getAg()` and `getTarget()` (role). Board/artifact incarnation scopes the tuple.

54. **Mission commitment?** `SchemeBoard.getSchState()` returns `ora4mas.nopl.oe.Scheme`, whose inherited `getPlayers()` represents agent-target commitments; `SchemeBoard.commitMission/leaveMission` and observable `commitment` state/events supply lifecycle evidence. The commitment remains an exact scheme-incarnation/mission/agent Bridge fact even if no current faithful `MSystemState` target exists.

55. **OGoal state?** ORA4MAS scheme state provides `getDoneGoals()`, `isSatisfied(Goal)`, arguments and related dynamic facts; `SchemeBoard.goalAchieved/resetGoal` change it. This is runtime state traced to the static `Goal`, not a new static definition or permission to overwrite it. It is OCL-queryable only after faithful runtime materialization.

56. **Runtime listener/event?** Board artifact operations/properties are observable through `ICartagoLogger`; `OrgArt.getNormativeEngine()` returns `NPLInterpreter`, whose `addListener(NormativeListener)` observes `created`, `fulfilled`, `unfulfilled`, `inactive`, `failure` and `sanction`. Interpreter collections (`getActive`, `getFulfilled`, `getUnFulfilled`, `getInactive`) support reconciliation. Capture preserves canonical identity, session/generation, board incarnation, evidence, provenance and completeness; facts without a faithful USE target remain evidence-only and block dependent OCL rather than receiving fabricated values.

57. **Canonical IDs?** Static OS entities use OS URI/digest + official ID/path. Live group/scheme boards use session/generation + CArtAgO artifact UUID + instance ID. Role plays and commitments use board incarnation + definition ID + agent incarnation. NPL norm instances use board scope + official source token + Bridge discriminator; stability across engine rebuild is not assumed.

## Answers 58–60 — integration

58. **Bridge insertion point?** Primary hook is an official custom `jacamo.platform.Platform`: JaCaMo reflectively creates it, calls `setJcmProject(JaCaMoProject)`, `init`, `start` and `stop`. It registers/adds a Bridge `AgArch` before agent creation and attaches CArtAgO/ORA4MAS/NPL observers as authorities become ready. No core patch is required.

59. **Independent-process transport feasible?** Yes, `FEASIBLE_WITH_ADAPTER`: Bridge-side official objects are copied into neutral versioned DTOs; USE uses a BridgeClient. No live Java object/shared classpath crosses the boundary, and USE's production-side semantic backend needs no JaCaMo runtime classes. Phase B proves deterministic classpath-neutral serialization and Phase D proves the boundary in two JVMs using a minimal/test mechanism. The production transport mechanism remains deliberately unresolved until Phase H benchmarks/security/packaging evidence selects one.

60. **Snapshot/event consistency achievable?** Yes as a validated consistent cut, not as global atomicity: register/buffer listeners, record per-source watermarks, copy/revalidate subsystem state, publish a capability-labelled snapshot, atomically replace USE state, then replay events strictly after the accepted watermarks. Gaps, overflow, stale generation or unknown identity force quarantine/resync.

## Final recommendation

Approve implementation only as the staged A–K roadmap. Start with baseline/API compatibility and the classpath-neutral contract, prove Hello vertically, integrate USE and pass the Phase-D separate-JVM smoke, shadow-compare, then migrate original Auction and House before selecting/hardening the production transport and changing the default. Preserve all frozen V2 assets and the legacy path until replacement evidence is complete.

## Audit stop

This document authorizes no production implementation. After consistency and Git-scope validation of the audit outputs, **STOP and wait for user review**.
