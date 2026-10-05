# Generic semantic projection refactor — evidence

The inventories through the original completion report below describe policy
2.0.0 at their recorded cuts. The newer selective verification projection is
policy 2.1.0, specified by task §38; its evidence is appended after that report.

Current authority: `../agent/task.md` (2026-10-04 revision). The 2026-10-03
Role-as-plain-association / deferred Belief/Goal/functional graph decisions are
historical and superseded. Their independent identity, artifact, rollback,
same-system GUI and replay fixes are retained.

## Preflight and existing architecture

- Branch `main`, starting HEAD `55f38081`; 74 modified and five untracked files
  existed before this task. Staged diff was empty. No branch, reset or commit.
- Active semantic contract: 1.0.0; current native rule catalog before refactor:
  1.2.0. Frozen Mapping V2 and its historical materialization path are preserved.
- Active path: OfficialProjectAdapter / official subsystem adapters →
  JacamoSemanticSnapshot → JacamoSpecificationModel exact typed identities →
  CodeGroundedNativePipeline / CodeGroundedRuleCatalog / NativeProjectionProfile
  → NativeUseModelBuilder → NativeUseStateBuilder → trace and native session
  activation → NativeRuntimeProjector / NativeRuntimeMutationEngine → native OCL.
  DefaultJaCaMoFacade selects this existing path; no parallel transformer is added.
- Baseline focused gate: `mvn -B -pl use-plugin -am test
  -Dtest=MoiseDomainProjectionTest,DomainRuntimeProjectionTest,AuctionExternalOclProfileTest
  -Dsurefire.failIfNoSpecifiedTests=false` PASS 20/20;
  `use-plugin/target/projection-v2-baseline.log` (2026-10-04).

## Creation-point audit

| Existing location | Source semantic kind / current target | New target and reason | Gate |
|---|---|---|---|
| NativeUseModelBuilder | ASL identity / concrete class of Agent | `<basename>_Agent < Agent`, once per canonical source | generic agent tests |
| NativeUseModelBuilder, NativeProjectionProfile | Belief / AgentGoal trace-only | generic Belief / AgentGoal, exact owned literal objects and 0..* links | initial/runtime literal tests |
| DomainProjection.ensureArtifactClass | exact Java Artifact type / standalone concrete class | concrete subclass of the single Artifact base | type/collision tests |
| NativeUseModelBuilder, NativeRuntimeMutationEngine | observed property / concrete MAttribute and value | retain typed scalar flattening and removal semantics | official CArtAgO tests |
| NativeUseModelBuilder | operation backing signature / evidence-only | MOperation only for exact supported signatures; explicit unsupported diagnostics | operation tests |
| MoiseDomainProjection | OS/group / standalone specification classes | Organization / Group bases and concrete subclasses | contextual Moise tests |
| MoiseDomainProjection | contextual role / plain MAssociation | native MAssociationClass; no detached Role or enactment wrapper class | native API support gate and cardinality tests |
| NativeUseStateBuilder, NativeRuntimeMutationEngine | players / plain MLink | native MLinkObject of the exact contextual association class | player mutation/export/rollback tests |
| MoiseDomainProjection | Scheme/Goal/Mission / deferred trace | Scheme subclass; generic goal/mission objects; contextual references | functional graph tests |
| MoiseDomainProjection | OPlan / deferred trace | parent operator, explicit child ordinal and goal self-relation; no Plan class | nested/operator/order tests |
| NativeUseStateBuilder | source declarations and observed instances / MObject | extend existing two-pass state materialization; no behavior execution | static state and three cases |
| NativeRuntimeMutationEngine | exact runtime facts / object/link/value upserts | same active native system, association-class and functional/Jason state | authoritative mutation/resync tests |
| NativeUseSoilExporter | every object as ordinary object / every link as plain insert | emit native link-object creation once, then values/ordinary relations | SOIL replay parity |
| NativeUseExporter / NativeUseStructure | native structural printing/signature | preserve association-class kind, operations and trace in recompile parity | export/recompile tests |
| Norm / role hierarchy/link policy | constraint hooks / retained metadata | formal unsupported semantics explicit; no invented classes or OCL | negative/trace tests |

Native support is present in this checkout's UseModelApi.createAssociationClass,
UseSystemApi.createLinkObjectEx and native MAssociationClass / MLinkObject APIs.
NativeRoleAssociationSupportTest is the executable API/recompile gate. The USE
association-class parser omitted `genAnnotations` when generating its class,
although the ordinary class parser and native printer preserve annotations.
`ASTAssociationClass.genEmptyClass` now invokes that existing generator (one
line); annotated role export/recompile and SOIL link-object replay pass. External
JaCaMo/Jason/CArtAgO/Moise cores are not changed.

## BEFORE inventories

Static snapshots were captured from the baseline gate under
`use-plugin/target/domain-projection/{auction,hello-world,house-building}/`:
`native.use`, `state.cmd`, `inventory.json`. Subsequent gates regenerate those
paths; the lists below record their original BEFORE classifiers. Older
`target/moise-domain-evidence/*/inventory.json` files describe the historical
metamodel projection and must not be treated as the current baseline.

- Auction static classifiers: Agent, Workspace, auction, auctionGroup,
  auction_capabilities.
- Hello World static classifiers: Agent, Workspace, hello, hf, o1, team.
- House static classifiers with its separately loaded official OS: Agent,
  Workspace, companyA, companyB, companyC, companyD, companyE, giacomo,
  house_contruction, house_group. The original JCM alone contains no static OS;
  live OS discovery is an independent required gate.

The retained BEFORE live gates are PASS within their then-supported projection.
They do not prove the newly required literal/functional/association-class policy.

| Original case / BEFORE evidence directory under `use-plugin/target` | Classes | Associations | Objects | Links at recorded cut |
|---|---:|---:|---:|---:|
| Auction: `workbench-acceptance/auction-1791105185675` | 8 | 10 | 15 | 22 |
| Hello World FULL: `hello-world-object-audit/FULL-1791105120420` | 9 | 12 | 33 | 44 after resync; 25 before resync |
| House Building: `house-domain-live/1791105357778` | 14 | 19 | 39 | 52 |

BEFORE live concrete classifiers supplement the static lists with:
AuctionArtifact, Console, TupleSpace (Auction); GUIConsole, Console, TupleSpace
(Hello World); AuctionArt, House, Console, TupleSpace (House Building).
The `.use`, `.cmd`, JSON inventories and replay records in those exact directories
retain the actual object/link inventories and values, rather than reconstructed
or invented BEFORE facts. House's cut contained 22 observed agents; this is a
recorded runtime cut, not a claim that all declared instances were observed.

BEFORE association inventories (full identifiers):

- Auction: `focuses_31ab59d99894becb`, `auctionGroup_auctioneer`,
  `focuses_16be2f15f1226e51`, `containsGroup_73ab17591a6a417f`,
  `locatedIn_c02fe9fcbf5d4102`, `memberOf_38a7e51fcad80e3b`,
  `locatedIn_ede5b443f2efa002`, `focuses_7bd0026f6808ab6d`,
  `locatedIn_27ff83fdc3e87003`, `auctionGroup_participant`.
- Hello World: `containsGroup_1cfe11a1cb8f27a5`,
  `focuses_31ab59d99894becb`, `focuses_7bd0026f6808ab6d`,
  `focuses_a42b420e1fc504ac`, `locatedIn_223b016ed1f4664f`,
  `locatedIn_27ff83fdc3e87003`, `locatedIn_c02fe9fcbf5d4102`,
  `memberOf_38a7e51fcad80e3b`, `team_rc`, `team_rl`, `team_rs`, `team_rv`.
- House Building: `house_group_door_fitter`, `focuses_31ab59d99894becb`,
  `house_group_roofer`, `house_group_site_prep_contractor`,
  `locatedIn_c02fe9fcbf5d4102`, `memberOf_38a7e51fcad80e3b`,
  `containsGroup_f04816a5c8b60b6d`, `focuses_4c9466767216181e`,
  `house_group_bricklayer`, `focuses_cbe2be4bf7fc091e`,
  `house_group_window_fitter`, `house_group_painter`,
  `locatedIn_d0a89f038b8890ae`, `house_group_plumber`,
  `house_group_house_owner`, `locatedIn_902ef0f6fbf5aed4`,
  `focuses_7bd0026f6808ab6d`, `locatedIn_27ff83fdc3e87003`,
  `house_group_electrician`.

## AFTER inventories at the completed live verification cuts

The following are actual outputs of the coherent successful unfiltered reactor
gate on 2026-10-04 at 20:14:17 +07. This gate includes native GUI operation
refresh and captures the diagram AFTER state at the last recorded transition.
Counts include association classes as classifiers and link objects as both
objects and links. Literal counts depend on the observed runtime cut.

| Original case / AFTER evidence directory under `use-plugin/target` | Classes | Associations | Objects | Links | Role link objects |
|---|---:|---:|---:|---:|---:|
| Auction: `workbench-acceptance/auction-1791119475563` | 19 | 15 | 346 | 371 | 5 |
| Hello World FULL: `hello-world-object-audit/FULL-1791119152036` | 22 | 17 | 253 | 288 | 4 |
| House Building: `house-domain-live/1791119398070` | 32 | 22 | 1431 | 1468 | 1 |

All three include the ten approved bases listed below. Their exact additional
classifiers are:

- Auction: `auction_capabilities_Agent`, `AuctionArtifact`, `Console`,
  `TupleSpace`, `auction_Organization`, `auctionGroup`, `doAuction_Scheme`,
  association classes `auctioneer`, `participant`.
- Hello World: `hello_Agent`, `hf_Agent`, `GUIConsole`, `Console`, `TupleSpace`,
  `o1_Organization`, `team`, `hello_sch_Scheme`, association classes
  `rc`, `rl`, `rs`, `rv`.
- House Building: `companyA_Agent`, `companyB_Agent`, `companyC_Agent`,
  `companyD_Agent`, `companyE_Agent`, `giacomo_Agent`, `AuctionArt`, `House`,
  `Console`, `TupleSpace`, `house_contruction_Organization`, `house_group`,
  `build_house_sch_Scheme`, association classes `bricklayer`, `door_fitter`,
  `electrician`, `house_owner`, `painter`, `plumber`, `roofer`,
  `site_prep_contractor`, `window_fitter`.

The exact removed/renamed classifiers relative to this starting checkout are
`auction` → `auction_Organization`, `auction_capabilities` →
`auction_capabilities_Agent`; `o1` → `o1_Organization`, `hello` → `hello_Agent`,
`hf` → `hf_Agent`; `house_contruction` → `house_contruction_Organization` and
the six House Agent program names → their `_Agent` subclasses. Artifact/Group
names already present are retained with the approved base generalizations.
Their identities still resolve from source keys, not these display names.

This BEFORE checkout already suppressed framework and execution wrappers; its
8/9/14 live classifiers were an intentionally smaller, deferred projection.
The required bases, literal objects, functional graph and native association
classes therefore increase these actual baseline class counts to 19/22/32.
There is no invented class-count reduction. `Role`, `RoleEnactment`,
`bobRole`, `aliceRole`, `GroupInstance`, `SchemeInstance`, `MissionCommitment`,
`Plan`, `PlanLibrary`, `PlanBody`, `PlanBodyElement`, `Event`, `Action`,
`ActionExec`, `Intention`, `Environment`, `Property`, `Operation`, `Signal`,
`Norm`, `OPlan`, `ArtifactType`, `ObservablePropertySnapshot`,
`CartagoAgentIdentity` and case-specific wrappers remain absent. The contextual
role names above are native association classes, not forbidden detached roles.

All three use the same 13 generic association identifiers:

```text
hasBelief_7af1e2d0df6f50cb
hasGoal_b290dbd9a779c5b6
joins_38a7e51fcad80e3b
contains_5edb135952655dba
focuses_571f75a85a26cdfb
containsGroup_3b099fe0f17e5720
containsScheme_b68aadf44e894fc8
responsibleFor_53a4a8502ac19463
schemeGoals_c9467c67353d462e
schemeMissions_0b470722088249aa
missionGoals_a27559d4725ea6e7
committedTo_51e6caf3431c9349
subGoals_9416c6a65202312f
```

Their 2/4/9 contextual role association classes complete the 15/17/22
association inventories. Generic subgroup tests add specification-derived
`containsSubgroup` relations when the source actually contains subgroups.

Auction's five actual agents are `bob`, `alice`, `maria`, `francois`, `giacomo`;
`aorg`, `agrp`, `a1`, `a2`, `sch_a1`, `sch_a2` are actual native objects.
At the recorded cut it has 305 Beliefs, no remaining observed AgentGoals,
three Workspaces, two Console and two TupleSpace artifacts, 12 organizational
goal objects, six mission objects and five role link objects. The two initial
Bob goals exist simultaneously in the source/static boundary, then completed
Jason observations determine their runtime presence.

Hello World's final resync has 197 Beliefs, no remaining observed AgentGoals,
five Agents, eight Workspaces, five GUIConsole, seven Console and seven
TupleSpace objects, one Organization/Group/Scheme, 13 goals, four missions and
four role link objects. Seven workspace UUIDs and 19 artifact UUIDs match the
official producer exactly; the eighth Workspace is the source-only default
`main` declaration, not a fabricated alias of the observed root. Each of the
five explicitly declared child workspaces/artifacts aliases its actual native
object. Duplicate identities, duplicate links and orphan links are all zero.

House observes 22 agents, including expanded companyC/companyD declarations,
eight dynamically created AuctionArt objects and the exact `hsh_group`
instance; `giacomo`'s `house_owner` link object is the observed role relation.
Dynamic Organization/Group/Scheme, goal/mission relations and reconnect/resync
are verified without adding an OS deployment to the original JCM.

`gui-acceptance.json` (Auction), `summary.json` plus all phase object/link/model/
binding JSONs (Hello World), and `summary.json`, `house.use`, `house.cmd`
(House) retain exact names, values, owners, contexts and trace. Every case
recompiles its native `.use` and replays its `.cmd` against the direct inventory.
Source-before/source-after hash manifests and executable `sourceUnchanged`
assertions prove the original case sources were not changed.

## Implemented projection policy and exact state rules

`NativeProjectionPolicy` and the native rule catalog are version 2.0.0; the neutral
semantic contract remains 1.0.0 and frozen Mapping V2 is unchanged. The same native
builders implement AUTO and FULL. Unknown concepts return UNSUPPORTED rather than
creating a fallback class. The ten bases are exactly Agent, Belief, AgentGoal,
Workspace, Artifact, Organization, Group, Scheme, OrganizationalGoal and Mission.

Concrete classes use canonical ASL URI/digest, Java FQCN, OS identity, group-spec
identity or scheme-spec identity. Human labels are display names. Collision
suffixes derive from those exact identities; collisions and ambiguous source
references fail closed. No production mapping selects a case-study name.

| Generic native association / navigation | End multiplicities | Source authority |
|---|---|---|
| Agent–Belief (`hasBelief`) | Agent 1; Belief 0..* | initial literal or completed Jason BB cut |
| Agent–AgentGoal (`hasGoal`) | Agent 1; AgentGoal 0..* | initial goal or observed current achievement-goal occurrence |
| Agent–Workspace (`joins`, `joinedWorkspaces`) | 0..* both ends | exact join binding; focus supplies no membership |
| Workspace–Artifact (`contains`, `artifacts`) | Workspace 1; Artifact 0..* | declared/observed artifact owner |
| Agent–Artifact (`focuses`) | 0..* both ends | exact focus binding |
| Organization–Group (`containsGroup`) | Organization 1; Group 0..* | exact deployment/board OS owner |
| Organization–Scheme (`containsScheme`) | Organization 0..1; Scheme 0..* | exact deployed/observed scheme owner |
| Group–Scheme (`responsibleFor`) | 0..* both ends | JCM responsibility / official Scheme clone |
| Scheme–OrganizationalGoal (`schemeGoals`) | Scheme 0..1; Goal 0..* | source-only definitions or instance-owned occurrences |
| Scheme–Mission (`schemeMissions`) | Scheme 0..1; Mission 0..* | source-only definitions or instance-owned occurrences |
| Mission–OrganizationalGoal (`missionGoals`) | 0..* both ends | exact same-scheme mission references |
| Agent–Mission (`committedTo`) | 0..* both ends | official Scheme players/mission IDs |
| OrganizationalGoal–OrganizationalGoal (`subGoals`) | parent 0..1; child 0..* | exact OPlan child identity and `orderInParent` |
| contextual Role association class Agent–concrete Group | Agent min..max from source; Group 0..* | exact `(OS, groupSpec, role)` cardinality |
| contextual parent Group–child Group (`containsSubgroup`) | parent 0..1; child min..max from source | exact subgroup specification and deployment/observed parent |

An association class has semantic/context attributes and native `MLinkObject`
instances. Those objects are the relationships, not Role/RoleEnactment wrappers.
Native upper/lower multiplicities count connected Agent objects. An observed
runtime cardinality violation is retained and reported by native verification;
it is not repaired by fabricating or discarding players.

| Case | Exact contextual role multiplicities at Agent end | Group end |
|---|---|---|
| Auction | auctioneer 1; participant 0..300 | auctionGroup 0..* |
| Hello World | rc 1; rl 1; rs 1; rv 1 | team 0..* |
| House Building | bricklayer 1..2; door_fitter, electrician, house_owner, painter, plumber, roofer, site_prep_contractor, window_fitter each 1 | house_group 0..* |

Class, association-class and link-object annotations/values retain the exact
organization, group specification, role and deployment context. A display-name
collision gets a deterministic source-derived suffix; another organization or
group cannot reuse a role target just because its local role label matches.

Observable state is a supported typed scalar MAttribute on the concrete Artifact.
Removal makes the value undefined. Its source identity remains on the native
attribute annotation. Supported operation signatures become MOperations using
the official descriptor plus exact backing Java method, with parameter/result
types and signature-based overload identity. There is no String/OclAny fallback
for unsupported parameter/result types. Operation enter/exit/action correlation
stays evidence; USE does not execute Java/Jason actions.

JCM `beliefs:`/`goals:` are read through the official project parser and Jason's
official list parser. Non-ground project beliefs become BeliefRule in the rich
contract, matching Jason, and are not turned into asserted Belief objects.
Initial literals have INITIAL source provenance. Completed runtime cuts replace
the agent's currently observed literal objects, remove absent observations, allow
reappearance and distinguish simultaneous goal occurrences by actual Trigger
identity. No active/achieved/failed lifecycle status is invented.

OPlan stays in the rich semantic contract. Parent `decompositionOperator`, the
subgoal relation and child `orderInParent` retain sequence/parallel/nested order.
Mission references resolve exact existing goal occurrences within their Scheme.
Runtime responsible groups, commitments and available goal satisfaction states
update that same active system. Foreign Scheme ownership and unresolved goal
references are rejected before projection.

Actual AuctionArtifact state is `running : Boolean`, `task : String`,
`best_bid : Real`, `winner : String`. Native exact-backed operations include
`bid_710f147c4a85d1c6(p0 : Real)`, `init_c47ccac70bb5f8c6()`,
`start_279e11b9da13b9ca(p0 : String)`, `stop_8c07aad71c5585bb()`.
The original Hello World GUIConsole has `numMsg : Real` and an exact Java
`printMsg(String)` backing method; its native operation is
`printMsg_11cdbc9e721a46cf(p0 : String)`. Erased `Object`/feedback/varargs
operations on other artifacts remain unsupported, rather than borrowing this
signature. House's actual AuctionArt `currentBid` is Real; integer-valued
observations can update that Real attribute through the supported scalar map.

Native views refresh the existing model's classes, attributes and operations;
there is no copied GUI model. A runtime operation discovered after views open
now invalidates and rebuilds the ClassNode operation compartment. The focused
GUI assertion verifies both that displayed native signature and the same
MClass's native Class Browser HTML, together with same-Session-system identity.

`NativeRuntimeProjector.trace()` remains the bounded event trace with its existing
API. `projectionBindings()` is a separate current native inventory, including
class/association/attribute/operation sources, object aliases, values and ordered
link endpoint identities. Generic executable assertions cover every generated
class, attribute, operation, association, object, value and link. Snapshot sorting
applies Group before Scheme and parent relations; repeated identical cuts rebuild
identical SOIL on the same active MSystem.

Workspace declaration aliases use the actual root's `getChildWSP(name)` descriptor
and verify its UUID/full name against the official snapshot. The pinned JaCaMo
`jacamo.platform.Cartago.start` bytecode uses precisely this child-workspace API;
`CartagoEnvironment.resolveWSP` requires an absolute workspace path and cannot
resolve a bare JCM name. Artifact aliases reuse the same controller-revalidated
snapshot and require exact workspace identity, artifact name and Java class.
An identically named nested workspace never aliases the root's declared child.

Exact artifact declaration proof is retained as metadata on the approved Artifact
base even when the Java type is unavailable during cold import. The runtime
mutation engine validates that proof before creating a concrete class and binds
the later observed artifact to the declaration on one native object. Missing
proof, wrong name/type/workspace and incarnation redirects fail closed.
OfficialCartagoAdapterTest and NativeArtifactDeclarationBindingTest exercise
these boundaries; the live Hello World inventory also asserts object identity
for every officially supplied declaration/runtime alias.

Controller reads/registration use a separate capture lock; official logger
callbacks never wait for that lock. The callback monitor only protects local
identity/event bookkeeping and is not held across official controller reads.
A deterministic test blocks a read on the actual CartagoEnvironment monitor and
requires a real logger callback to finish before releasing the read. Per-source
watermarks in SnapshotCoordinator still reject a cut changed by authoritative
callbacks. No CArtAgO core change or timeout extension is used.

## Declared fidelity limits

- Norm keeps exact source, role, mission, condition, operation and deadline in
  metadata/trace with `UNSUPPORTED_NORM_TRANSLATION`. No Norm class or guessed
  normative OCL is generated. External supported OCL still evaluates natively.
- Role hierarchy, compatibility and role-link constraint semantics remain exact
  source evidence with `MOISE_ADVANCED_ROLE_SEMANTICS_DEFERRED`; they are not
  silently treated as enforced constraints. Goal dependencies and unsupported
  decomposition semantics retain explicit diagnostics/source evidence.
- Jason goal observations cover achievement events and current/pending intention
  triggers visible at the completed cycle boundary, not a fabricated global goal
  lifecycle. Failed cuts invalidate prior observations and mark coverage PARTIAL.
- Moise board polling is PARTIAL for global coverage. A validated board fact can
  be COMPLETE for its own supported fields. OCL requiring complete unavailable
  sources is SKIPPED, avoiding vacuous PASS.
- Non-scalar/unsupported observable values, erased Object/feedback/array/vararg
  operation types and unavailable method evidence are explicit unsupported facts.
  Runtime identifiers/DTOs, Jason execution stacks and framework bookkeeping
  artifacts remain evidence rather than additional exposed classes.

## Executable gate index

| Requirement group | Executable evidence |
|---|---|
| Classification, ten bases, canonical ASL subclasses, simultaneous initial literals, no case selectors, unknown concepts | GenericAgentProjectionTest; NativeProjectionProfileTest; DomainRuntimeProjectionTest |
| Official JCM/ASL parsing, nonground rules and completed-cycle observation | OfficialJasonAdapterTest; LiveJaCaMoLauncherLifetimeTest; GenericFunctionalRuntimeProjectionTest |
| Declared/observed workspaces and artifacts, exact aliasing, no focus-to-join inference | OfficialCartagoAdapterTest; NativeArtifactDeclarationBindingTest; NativeMOperationProjectionTest; HelloWorldSemanticInventoryIT |
| Dynamic scalar attributes, removal/undefined, same system and atomic identity rejection | DomainRuntimeProjectionTest; LiveCartagoNativeVerificationTest; RuntimeVerificationCoordinatorTest |
| Exact supported operation parameters/result/overloads; unsupported signature rejected | NativeMOperationProjectionTest; NativeUseGuiEndToEndIT |
| Native association-class API, contextual roles/cardinality, player mutation and violations | NativeRoleAssociationSupportTest; MoiseDomainProjectionTest; DomainRuntimeProjectionTest |
| Organization/Group/Scheme subclasses; planless sequence/parallel/nesting; mission/goal ownership; responsibility/commitments | MoiseDomainProjectionTest; GenericFunctionalRuntimeProjectionTest; OfficialMoiseIdentityTest; HouseBuildingDomainProjectionIT |
| Subgroup cardinality, source parent, dynamic parent order and owner rejection | GenericSubgroupProjectionTest |
| No forbidden classifiers/wrappers; exact identity and ambiguous references fail closed | GenericAgentProjectionTest; MoiseDomainProjectionTest; CodeGroundedNegativeTest; DomainRuntimeProjectionTest |
| All native targets traced; event trace remains bounded; aliases stay exact | GenericFunctionalRuntimeProjectionTest.assertBindingsComplete; NativeArtifactDeclarationBindingTest; Hello World phase binding exports |
| Native SOIL/export/recompile, direct/text parity and historical frozen regressions | CodeGroundedExportTest; NativeUseExportRecompileIT; NativeRoleAssociationSupportTest; V2MaterializationTest; MappingTransformationTest; ActiveBaselineTest |
| One active Session/OCL/GUI system, dynamic schema, replay/reanalysis and reconnect | NativeUseSessionOclIT; NativeUseGuiEndToEndIT; NativeStepReplayGuiIT; HelloWorldSemanticInventoryIT; ManagedAuctionWorkbenchIT; HouseBuildingDomainProjectionIT |
| Original three-case live genericity and unchanged sources | ManagedAuctionWorkbenchIT; HelloWorldSemanticInventoryIT (AUTO and FULL); HouseBuildingDomainProjectionIT |

Focused successful gates are recorded in the root `target/` logs:

- `projection-v2-resync-focused.log`: House live, native step replay and its
  related units; PASS.
- `projection-v2-relations-trace-focused.log`: 17 focused functional/Moise/
  operation tests; PASS.
- `projection-v2-cartago-identity-lock-focused.log`: eight adapter tests,
  16 plugin units, House live plus relevant native integration gates; PASS.
- `projection-v2-late-artifact-binding-focused.log`: four generic Agent and
  two late Artifact binding tests; PASS.
- `projection-v2-late-operation-gui-focused-retry.log`: six operation/Artifact
  binding units, four native GUI integration tests, core OCL integration and
  129 Shell integration tests; PASS at 19:21 +07.

The unfiltered `mvn -B verify` gate at 19:11 +07 is
`target/projection-v2-full-reactor-final.log`: all seven reactor modules PASS,
660 tests, zero failures/errors/skips. Counts are contract 15, official adapters
38, core units 12 plus OCL IT 1, GUI architecture unit 1 plus Shell IT 129,
plugin units 445 plus native/live/release ITs 19. Verify includes packaging and
the full assembly. The subsequent `projection-v2-full-reactor-release.log` run
passed all code/native/live-case gates but correctly failed ReleasePackageIT
because README had been edited after package. No assertion was weakened.
After holding packaged inputs stable, both explicit package gates pass:
`mvn -B -DskipTests package` at 19:47 +07 (`projection-v2-root-package.log`), and
`mvn -B -pl use-plugin -am -DskipTests package` at 19:51 +07
(`projection-v2-plugin-package.log`). These flags skip execution and retain test
compilation required by the Bridge launcher. The final coherent unfiltered rerun
is `target/projection-v2-final-verify.log`: PASS all seven modules at 20:14:17 +07,
660 tests with zero failures/errors/skips, including all three release checks.
AFTER case and diagram paths above are from this coherent final gate.

Earlier failed logs remain available: the old six-class GUI fixture expectation
was incompatible with the approved projection, and the blocked controller-read/
logger-callback investigation produced the deterministic lock test described
above. The later operation-compartment assertion initially required a cast to
the actual native ClassDiagramData API; its corrected focused gate passes.
These failures are not counted as final passing evidence.

## Diagram evidence contract

Auction and Hello World each export `object-diagram-before.{png,json,cmd}` and
`object-diagram-after.{png,json,cmd}`. BEFORE selects replay step 0, after
declarations and before behavior. AFTER selects the last recorded actual
transition, including the authoritative resync when present. Every capture
selects the exact native replay system in Session; it never synthesizes facts
or copies a display-only state. The selected step is recorded in JSON and the
replay proof verifies the state at every step.

Native PNGs show attribute values, literal text, actual link-object nodes and
their endpoints; JSON/SOIL contain the entire exact selected state even when the
diagram shows a readable detail selection. Native node bounds are asserted
inside the exported image. Current runtime goals are shown only when present;
an absent goal at the completed cut is not revived from the source declaration.
The final four PNGs were visually reviewed after the final live gate.
Auction BEFORE step 0: 21 objects/14 links; AFTER step 346: 346/371.
Hello FULL BEFORE step 0: 42/55; AFTER step 411: 253/288. Attributes are visible;
native node-bounds assertions pass. Complete JSON/SOIL retain all objects/links.

## Completion report (task §35)

```text
TASK
Generic JaCaMo → USE Semantic Projection Refactor

BASELINE
branch: main
start commit: 55f38081
end commit: 55f38081; no commit requested
active semantic/mapping/projection version: semantic contract 1.0.0; frozen Mapping V2 2.2.0 unchanged; native policy/catalog 2.0.0, same 105 rule IDs

ARCHITECTURE
confirmed existing pipeline: official adapters → neutral semantic snapshot → JacamoSpecificationModel → existing rule/projection builders → native USE → Session/OCL/runtime projector
changes to pipeline: classification and approved native targets within existing builders/runtime engine
confirmation no parallel case-specific transformer: default facade and class-creation audit; production case-selector negative test PASS

PROJECTION POLICY
kept exposed classes: Agent, Belief, AgentGoal, Workspace, Artifact, Organization, Group, Scheme, OrganizationalGoal, Mission
removed exposed classes: old unsuffixed Agent/OS classifiers renamed; forbidden framework/execution/wrapper classes absent in all three cases (already suppressed in the starting baseline)
flattened concepts: observable properties → typed Artifact attributes; exact operation signatures → MOperations; OPlan → goal operator/ordinal/relations
constraint-only concepts: supported native/external OCL; Norm translation unsupported with exact source diagnostics
trace-only concepts: Jason plans/execution AST, signals/action execution, unsupported advanced role/goal semantics

ROLE ASSOCIATION CLASS
USE support evidence: NativeRoleAssociationSupportTest; native MAssociationClass/MLinkObject, annotated recompile and SOIL PASS
implementation: exact (OS, groupSpec, role) association class; player is the link object itself
cardinality handling: source min/max at Agent end, concrete Group *; actual violations retained/reported
runtime player handling: authoritative adoption/removal, exact context, atomic rollback and idempotent resync
known limitations: advanced hierarchy/link/compatibility constraints are explicitly deferred, not inferred

AGENT DIMENSION
Agent mapping: canonical ASL basename_Agent subclass; declared/expanded instances use exact aliases
Belief mapping: owned literal:String objects from authoritative initial/current completed BB cuts
AgentGoal mapping: multiple owned initial/current achievement-trigger occurrences; no invented lifecycle
Plan exclusion: no Plan/PlanLibrary/PlanBody/Event/Action/Intention classes
runtime sync: completed-cycle observations, removal/reappearance and unsupported-cut coverage guards

ENVIRONMENT DIMENSION
Workspace mapping: declarations/observed UUID objects; exact child descriptor aliases; default main remains source-only without proof
Artifact mapping: exact Java FQCN concrete subclasses; unavailable cold declaration bound later only with proof
observable property mapping: typed scalar attributes, runtime values and removal→undefined
operation mapping: exact supported parameters/result/overload signatures only; actual GUIConsole.printMsg(String) supported
focus/join mapping: independent many-to-many associations; no focus→join inference
runtime sync: official controller cuts, independent callback lock, alias parity and dynamic schema on same system

ORGANISATION DIMENSION
Organization mapping: OSid_Organization; exact deployment/observed objects
Group mapping: specification subclasses/instances; exact containment/subgroup cardinality
Scheme mapping: specification subclasses and authoritative deployed/dynamic occurrences
OrganizationalGoal mapping: generic definition/instance-owned objects; only observed runtime states
OPlan flattening: parent operator, explicit child ordinal, exact nesting/self-relation
Mission mapping: contextual generic objects/min/max; same-scheme goal references and actual Agent commitments
Norm policy: UNSUPPORTED_NORM_TRANSLATION; exact source retained, no Norm class or guessed OCL
runtime sync: official board facts preserve responsibilities, commitments, goal state and exact parent binding

CASE STUDY EVIDENCE
Auction: PASS; 19 classes, 15 associations, 346 objects, 371 links; five role link objects
Hello World: AUTO/FULL PASS; 22 classes, 17 associations, 253 objects, 288 links; four role link objects
House Building: PASS; 32 classes, 22 associations, 1431 objects, 1468 links; 22 agents/eight AuctionArt/one observed owner role link object

BEFORE / AFTER
class count before: Auction 8; Hello FULL 9; House 14 at the retained BEFORE live cuts
class count after: Auction 19; Hello 22; House 32
removed classes: exact old Agent/OS names listed above; forbidden wrappers remain absent
added/renamed semantic classes: eight added bases, _Agent/_Organization subclasses, Scheme subclasses and native role association classes
association changes: source-derived 13 generic relations plus 2/4/9 contextual role association classes; min/max preserved
note: class counts rise from the already compact deferred baseline; no fabricated reduction is claimed

TEST EVIDENCE
focused: all indexed focused gates PASS, including late operation GUI and declaration identity/lock tests
case-study: original Auction, Hello AUTO/FULL and dynamic House PASS; original-source hashes unchanged
full regression: unfiltered mvn -B verify, 660 tests, seven modules PASS in target/projection-v2-final-verify.log
export/recompile: native direct/text/SOIL structural and state parity PASS; exact contextual Role annotations survive
session/OCL: same active Session/runtime/OCL system; replay explicitly selects isolated read-only historical system then restores live state; intended OCL FAIL and unavailable-source SKIPPED remain distinct

TRACE / IDENTITY
source→target: annotations/source traces and current projectionBindings cover every generated class/association/attribute/operation/object/value/link
runtime aliases: official identities bind one native object; missing/foreign/name-only/incarnation redirects rejected
collision policy: owner-qualified canonical identities and deterministic source-derived display suffixes; ambiguous references fail closed

DOCUMENTATION
updated: task.md; projection-refactor-before-after.md; 02-system-architecture.md; plugin/root README
historical preserved: frozen Core/Mapping V2, release manifest and earlier evidence
stale claims removed: plain Role/deferred literal/functional policy superseded; exact current projection and limits documented

KNOWN LIMITATIONS
Global Moise polling remains PARTIAL; complete-source OCL can be SKIPPED.
Advanced role constraints, goal dependencies and unsupported Norm/deadline equivalence remain explicit source evidence.
Unsupported non-scalar properties and Object/feedback/array/vararg method types produce no guessed native feature.
Jason current-goal observations are bounded by available completed-cycle triggers, not a fabricated global lifecycle.
No external JaCaMo/Jason/CArtAgO/Moise core edit; one necessary USE association-class annotation parser line.

FINAL STATUS
DONE — 118/118 checklist items supported by indexed executable evidence; no blocking semantic mismatch.
```

Repository: main / 55f38081; staged diff empty; 87 tracked modified and 14
untracked files include preserved starting work. git diff --check passes.
No external JaCaMo/Jason/CArtAgO/Moise core or frozen Mapping V2/manifest edit.

## Selective verification projection 2.1.0 — 2026-10-04 amendment

This section supersedes the unconditional literal/platform exposure in the
original 2.0.0 report. Starting state was main / 55f38081 with 87 tracked modified
and 14 untracked files; valid prior work was preserved.

**Root cause:** Auction's archived 305 native Belief objects have exactly the same
semantic IDs as the last completed Jason cuts: 61 per agent, no extras or missing
facts. Of those literals, 300 carry percept annotations and five are hidden Jason
library facts. The completed-cut removal logic already deletes absent literals.
The problem was wholesale exposure of the current belief base. The executable
baseline comparison is `target/selective-projection-baseline-audit.json`.

Console/TupleSpace originated in CArtAgO's default workspace infrastructure.
Artifact inheritance alone had admitted their types/instances into the public
model. Provider provenance now distinguishes these platform implementations from
application artifact implementations, with no display-name filter.

### Rule and retained fidelity

- Belief class remains. Instances require proved project-authored predicates,
  no percept/artifact duplicate or hidden-library flag, and active compiled OCL
  dependence on Belief or its navigation. All three conditions are necessary.
- Initial seeds carry exact source identity in model annotations. Runtime cuts
  replace them when observed. Removal, reappearance, profile replacement,
  invariant activation, successor incarnation and rollback are executable cases.
- Full raw Jason literals stay in producer observations/current-cut cache/journal.
  Missing provenance fails demanded projection closed; names are not evidence.
- Concrete Artifact classes need application-provider proof, or the same exact
  verified proof retained in a native classifier for standalone replay. A loadable
  class without provider location remains PARTIAL. Tests prove application types
  named Console/TupleSpace remain eligible; display.GUIConsole stays exposed.
- Excluded platform property/operation/focus/lifecycle facts remain evidence-only.
  The existing native Artifact, Scheme, OrganizationalGoal, Mission, contextual
  role relations and multiplicities continue through the same pipeline/system.
- Recorded replay retains exact state/result hashes. Reanalysis validates the
  recording, then permits only current-profile Belief/hasBelief selection changes.
  Every other native object, attribute and link is compared at every entry, with
  both domain-state hashes and changed-selection status persisted.

### Recorded inventories

Before counts refer to retained 2.0.0 live cuts. After counts refer to the passing
2.1.0 live gates indexed in task §38. Cut timing changes transient data/links;
these are measured inventories, not fixed case selectors or expected count rules.

| Case | Classes before → after | Objects before → after | Links before → after | Belief before → after | Raw Jason at after cut |
| --- | ---: | ---: | ---: | ---: | ---: |
| Auction | 19 → 17 | 346 → 37 | 371 → 62 | 305 → 0 | 305 |
| Hello World AUTO/FULL | 22 → 20 | 253 → 48 | 288 → 83 | 197 → 6 | 197 |
| House Building | 32 → 30 | 1431 → 285 | 1468 → 321 | 1358 → 216 | 1313 |

Native Console/TupleSpace types and objects disappear in all three cases.
Platform object removals are 4, 14 and 4 respectively. Domain artifact counts
remain AuctionArtifact 2, GUIConsole 5, AuctionArt 8 / House 1. Scheme / goal /
mission objects remain 2/12/6, 1/13/4 and 1/13/10. The after evidence contains the
complete class names, per-class object counts, exact IDs and current raw cuts.

Before evidence: Auction `auction-1791119475563`; Hello
`FULL-1791119152036`; House `1791119398070`. After evidence:
Auction `auction-1791126851132`; Hello `AUTO-1791126470343` /
`FULL-1791126651793`; House `1791126820419`, under the existing
`use-plugin/target/workbench-acceptance`, `hello-world-object-audit` and
`house-domain-live` roots. Auction/House have `selective-projection.json`; Hello
stores it as `summary.json.selectiveProjection`. Native object-diagram images
were inspected for Auction and Hello; their selection is explicitly labelled
while inventories retain the complete state.

### Tests and changed implementation

Focused gate PASS: 44 source/projection/coordinator/reanalysis tests, plus 130
native OCL/shell regressions and four standalone-replay/release-package tests,
in `target/selective-projection-focused-packaged-current.log` (178 total).
Auction, Hello AUTO/FULL and dynamic House live gates PASS. Final unfiltered
`mvn -B verify`: **667 tests, zero failures/errors/skips, seven modules PASS**, in
`target/selective-projection-reactor-complete.log`, completed 22:16:15 +07.
This includes full package/assembly, standalone replay, source/checksum parity,
GUI/OCL/export, runtime identity/resync and frozen-resource regressions.
`target/selective-projection-final-audit.json` consolidates before/after evidence;
`target/selective-projection-test-totals.json` totals 151 executed suites.
Exactly two classifiers disappear and none are added. All other domain-object
counts match (37/37, 42/42, 69/69) after excluding the selected Belief surface and
platform objects. Transient House cuts can have 215 or 216 eligible beliefs;
the table reports the final indexed cut.

Current-task source changes are limited to official belief/provider evidence and
the existing native projection/runtime/constraint/reanalysis path:

- Adapter: JasonBeliefEvidence, OfficialJasonAdapter, BridgeAgArch,
  OfficialCartagoAdapter, CartagoSnapshotSource.
- Native projection: NativeProjectionPolicy, DomainProjection,
  NativeUseModelBuilder, NativeUseStateBuilder, NativeRuntimeMutationEngine,
  ExternalOclConstraintService, RuntimeVerificationCoordinator,
  NativeRuntimeReanalysis.
- Tests: selective/lifecycle/provider/reanalysis coverage, source-parser tests,
  generic agent/declaration fixtures and the three live case inventories. No
  assertion was weakened to hide a semantic mismatch.
- Docs: plugin README, architecture, task §38 and this versioned evidence section.

No external framework-core edits, parallel transformer, branch, reset, stage or
commit. Existing advanced-role/Norm/dependency and source completeness limits
remain as recorded in §37. Platform types have no automatic promotion path;
unproved/omitted classifiers cannot be referenced successfully by native OCL.

**Closure:** DONE, 128/128 task items with indexed evidence. Main / 55f38081,
89 tracked modified and 17 untracked files including initial work; staged diff
empty, `git diff --check` PASS, protected Core/Mapping/manifest diff empty.

## Live relation amendment — 2026-10-05

The Auction diagram exposed gaps in the shared live observation path. Official
artifact observers were discarded; focus/unfocus and join/quit logger events were
trace-only; the implicit root was not bound to its actual workspace; and Moise
board state was published only by snapshots. The first live gate also identified
a neutral-mirror lifecycle rejection of the first typed focus relation.

The existing pipeline now projects exact official observer relations and live
focus/join lifecycle, reconciles relation absence only from COMPLETE CArtAgO
cuts, binds the implicit root to its official UUID and publishes supported Moise
board cuts every 500 ms. The neutral mirror recognises the specific focus
creation/removal lifecycle; first Moise board observations use CREATED, then
CHANGED. Identity conflicts, stale generations and sequence gaps still fail
closed. No case selector, extra classifier or parallel transformer is added.
Global Moise observation remains PARTIAL; missing boards are not deletion proof.

| Auction inventory | Previous policy 2.1.0 resynced cut | Repaired live cut |
| --- | ---: | ---: |
| Classes | 17 | 17 |
| Objects | 37 | 36 |
| Links | 62 | 72 |
| Workspace objects | 3 | 2 |
| Focus links | 0 | 10 |
| Join links | 10 | 10 |
| Contextual Agent–Group role links | 5 | 5 |
| Agent–Mission commitments | 10 | 10 |
| Exposed Beliefs | 0 | 0 |

Before is the retained `auction-1791126851132` resync cut, rather than the user's
initial live view with only root joins. Both repaired live runs
`auction-1791134899564` and `auction-1791135390093` assert all 10 focus, 10 join,
5 role and 10 commitment links **before manual resync**, in the same active
MSystem and visible native Object Diagram. Exact relation sets match after
resync. Full state remains five Agents, two application Artifacts, two Workspaces,
one Organization, one Group, two Schemes, twelve OrganizationalGoals, six
Missions and five contextual link objects. All 305 raw Jason beliefs remain
internal; Console/TupleSpace remain excluded.

Agent–Workspace, Agent–Artifact and Agent–Mission multiplicities remain `* ↔ *`;
Workspace–Artifact remains `1 ↔ *`. Contextual auctioneer and participant Agent
ends remain `1` and `0..300`, respectively, with Group ends `*`. Organisation
participation follows Agent–Group–Organization; no redundant direct relation is
invented. Quit retains the Agent and other workspace identities, while removing
only that workspace's joins/focus. Focus never implies a join.

Focused gate: **143 tests PASS** (12 adapter/mirror/native tests, 130 OCL/shell,
one real Auction GUI test), in `target/agent-relations-auction-lifecycle.log`.
Final relevant reactor: **668 tests / 149 suites PASS**, zero failures/errors/skips,
all seven modules, completed 2026-10-05 00:38:32 +07, in
`target/agent-relations-reactor-complete.log`. All unit tests and the selected
Auction/OCL/shell/GUI/export/replay/release-package integration gates ran. No
additional live case was requested by this amendment. Recorded replay,
reanalysis, step replay, source integrity and package checks all PASS.

Native `object-diagram-after.png` was visually inspected; its labelled detail
shows 20 of 36 objects while JSON/SOIL retain the complete inventory. Each live
run has `relations-before-resync.json`, `relations-after-resync.json`,
`selective-projection.json` and `gui-acceptance.json`. Consolidated evidence is
`target/agent-relations-final-audit.json` and `target/agent-relations-test-totals.json`.
Implementation and file scope are indexed in task section 39.

**Amendment closure:** DONE, 8/8 checks (136/136 overall). Main / 55f38081;
94 tracked modified and 18 untracked files include the preserved preflight work.
Staged diff is empty; `git diff --check` passes. No external framework core,
protected mapping/manifest edit, branch, reset, stage or commit.
