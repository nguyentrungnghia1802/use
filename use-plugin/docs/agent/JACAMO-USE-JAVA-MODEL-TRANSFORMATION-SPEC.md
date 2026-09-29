# JaCaMo–USE Java Model Transformation Specification

**Status:** code-derived draft for review  
**Purpose:** define transformation rules from the *official Java semantic/object models* used by JaCaMo/Jason/CArtAgO/Moise to native USE model constructs.  
**Important:** this is **not** a source-text mapping and **not** a re-statement of the old Ecore/JSON mapping. The Java APIs/objects are the semantic authority for the source side.

## 1. Evidence baseline

### JaCaMo side
Audited JaCaMo source revision:

- `3866858a7ebf6be85d9199c13a09cf4bfb8191be`
- `jacamo.project.JaCaMoProject`
- `jacamo.project.parser.JaCaMoProjectParser`
- Jason semantic/runtime API (`jason.asSyntax`, `jason.asSemantics`, `jason.pl`, `jason.bb`)
- CArtAgO 3.1 API (`cartago`, `cartago.events`, `cartago.tools`)
- Moise 1.1 API (`moise.os.*`) plus ORA4MAS/NPL runtime API

Audited dependency revisions recorded by the source audit:

- CArtAgO 3.1, tag `v3.1`, commit `440cd41c1810ceef6a627477c461776b3200236b`
- Moise 1.1, tag `v1.1`, commit `c68d4b7068c56b7a42e657bdc160f55f2d366ea8`
- Jason was audited against the version resolved by that JaCaMo checkout.

### USE side
Audited USE repository:

- `https://github.com/nguyentrungnghia1802/use`
- inspected revision `59dd582fad63d1a5a19fa8306e3dbed4afe881bc`
- USE 7.5.0

Native target constructs confirmed in the repository/API:

- `MModel`
- `EnumType`
- `MClass`
- `MAttribute`
- `MAssociation`
- composition through association aggregation semantics
- `MOperation`
- `MSystemState` for later object/state materialization

The USE grammar/API evidence includes `USEBase.gpart`, `UseModelApi`, `MModel`, `ASTAssociation`, and the plugin's `DirectUseBackend`.

---

# 2. Core rule

Do **not** transform arbitrary Java implementation classes by reflection:

```text
Java class name -> same-named USE class
```

That would be wrong.

Transform only **semantic source objects/types** established by the official JaCaMo/Jason/CArtAgO/Moise APIs:

```text
Official semantic Java object/type
        ↓
Source adapter
        ↓
Code-derived semantic rule
        ↓
Native USE construct
```

A Java source type and a runtime/source object are different levels:

```text
jason.asSyntax.Plan        -> defines the source semantic TYPE
one parsed Plan instance   -> later becomes a USE OBJECT of the mapped Plan class
```

Therefore the transformation has two phases:

```text
PHASE A — MODEL/TYPE
official Java semantic types
    -> MModel/MClass/MAttribute/MAssociation/MOperation/EnumType

PHASE B — INSTANCE/STATE
official parsed/runtime objects
    -> MSystemState / MObject / links / values
```

This document focuses on **Phase A** and records Phase-B consequences where necessary.

---

# 3. Target construction primitives

| USE construct | Use in transformation |
|---|---|
| `MModel` | root target model for one imported JaCaMo project/model view |
| `MClass` | first-class semantic concepts that must have identity and links |
| `MAttribute` | scalar values intrinsic to one semantic object |
| `MAssociation` | non-owning semantic references/relations |
| composition association | source-owned structural containment |
| `EnumType` | finite source semantic kinds/operators |
| `MOperation` | native UML operation projection **only when the source signature is sufficiently exact** |
| `MSystemState` | later materialization of actual project/runtime objects |

---

# 4. JCM / JaCaMo project rules

| Rule | Official JaCaMo Java authority | Meaning in code | Recommended USE target | Fidelity / decision |
|---|---|---|---|---|
| `JCM_PROJECT_TO_MODEL` | `jacamo.project.JaCaMoProject extends jason.mas2j.MAS2JProject` | Parsed/merged project definition and deployment configuration | `MModel` root + model metadata | **EXACT** for project identity/configuration surface |
| `JCM_AGENT_DECLARATION` | `JaCaMoProject.getAgents()` → `JaCaMoAgentParameters` | Agent declaration/configuration: source/class/options, initial config, workspaces, roles, focus, instance policy | **No new MClass.** Later materialize an `Agent`/deployment object and relations | **NO_DIRECT_STATIC_TARGET** |
| `JCM_WORKSPACE_DECLARATION` | `JaCaMoProject.getWorkspaces()` → `JaCaMoWorkspaceParameters` | Declared workspace and artifact class parameters | **No new MClass.** Contributes later `Workspace`/`Artifact` objects | **NO_DIRECT_STATIC_TARGET** |
| `JCM_ORG_DECLARATION` | `JaCaMoProject.getOrgs()` → `JaCaMoOrgParameters` | OS source + configured group/scheme instances | **No new MClass.** Links deployment to Moise specification | **NO_DIRECT_STATIC_TARGET** |
| `JCM_USES_IMPORT` | `JaCaMoProject.importProject(...)` | Official merge semantics for `uses` | No target element; affects provenance and merged project content | **EXACT, PROVENANCE** |

**Important:** `JaCaMoAgentParameters` is configuration, **not** a parsed Jason AST and **not** a live agent. A mapping that treats it as the same thing as `jason.asSemantics.Agent` is semantically wrong.

---

# 5. Jason model rules

## 5.1 Source authority

The code audit establishes these official source objects:

- `jason.asSemantics.Agent`
- `jason.pl.PlanLibrary`
- `jason.asSyntax.Plan`
- `jason.asSyntax.Trigger`
- `jason.asSyntax.PlanBody`
- `jason.asSyntax.Literal`
- `jason.asSyntax.Rule`
- goal terms / initial goals from `Agent.getInitialGoals()`
- `jason.asSyntax.SourceInfo`
- runtime `jason.asSemantics.ActionExec`

## 5.2 Mapping rules

| Rule | Jason Java source | Exact data/API | Recommended USE target | Fidelity / note |
|---|---|---|---|---|
| `JASON_AGENT_PROGRAM` | `jason.asSemantics.Agent` | `getInitialBels()`, `getInitialGoals()`, `getPL()` | `MClass AgentProgram` | **EXACT STRUCTURAL**. Recommended to separate program definition from deployed/live Agent |
| `JASON_PLAN_LIBRARY` | `jason.pl.PlanLibrary` | iterable ordered plans, `getPlans()` | ordered composition `AgentProgram -> Plan` | **EXACT STRUCTURAL**; a standalone `PlanLibrary` class is optional and normally unnecessary |
| `JASON_PLAN` | `jason.asSyntax.Plan` | `getLabel()`, `getTrigger()`, `getContext()`, `getBody()` | `MClass Plan`; scalar `label`, canonical `context`; relations to Trigger and body | **EXACT/SUPPORTED_SUBSET** depending on body preservation |
| `JASON_TRIGGER` | `jason.asSyntax.Trigger` | operator/type/literal | `MClass Trigger` (compatibility name: `Event`) + enums/attributes | **EXACT** for exposed trigger fields |
| `JASON_PLAN_BODY` | `jason.asSyntax.PlanBody` | `getBodyType()`, `getBodyTerm()`, `getBodyNext()` | ordered composition `Plan -> PlanBodyElement` | **EXACT** if all body kinds are represented; mapping only actions is a **SUPPORTED_SUBSET** |
| `JASON_EXTERNAL_ACTION` | `PlanBody.BodyType.action` | body term, name/arity/args | `MClass Action`, `kind=EXTERNAL` | **EXACT** for action identity/kind; CArtAgO target op requires separate evidence |
| `JASON_INTERNAL_ACTION` | `PlanBody.BodyType.internalAction` | body term | `MClass Action`, `kind=INTERNAL` | **EXACT**; must never be treated as CArtAgO operation |
| `JASON_BELIEF` | `jason.asSyntax.Literal` from initial/live belief source | canonical literal | `MClass Belief` + `literal : String` | **EXACT** for literal; property origin is separate cross-framework evidence |
| `JASON_RULE` | `jason.asSyntax.Rule` | rule semantic object | `MClass BeliefRule` or `Rule` | **SOURCE CONCEPT PRESENT**; omitting it is an explicit scope reduction |
| `JASON_AGENT_GOAL` | initial goal / goal semantic object | literal + achievement/test form where exposed | `MClass AgentGoal` | **EXACT** for supported goal kind/literal |
| `JASON_SOURCE_INFO` | `jason.asSyntax.SourceInfo` | source file, line range | provenance fields/trace, not domain class | **PROVENANCE ONLY** |
| `JASON_ACTION_EXEC` | `jason.asSemantics.ActionExec` | live action execution/correlation | Phase B / runtime event, not MModel type | **RUNTIME ONLY** |

### Critical Jason relation rules

```text
AgentProgram 1 *-- * Plan
Plan         1 *-- 1 Trigger
Plan         1 *-- * PlanBodyElement [ordered]
```

Do not infer:

```text
Action -> CArtAgO Operation
Trigger -> CArtAgO Signal
AgentGoal -> Moise Goal
```

from equal names. Those are separate evidence-based cross-framework rules.

---

# 6. CArtAgO model rules

## 6.1 Source authority

The code audit identifies:

- `cartago.CartagoEnvironment`
- `WorkspaceDescriptor`
- `WorkspaceId`
- `ICartagoController`
- `ArtifactId`
- `ArtifactInfo`
- `OpDescriptor`
- `ArtifactOpMethod`
- `ArtifactObsProperty`
- `ICartagoLogger`
- concrete Java classes extending `cartago.Artifact`

## 6.2 Mapping rules

| Rule | CArtAgO Java source | Exact data/API | Recommended USE target | Fidelity / note |
|---|---|---|---|---|
| `CARTAGO_ENVIRONMENT` | `CartagoEnvironment` | root workspace resolution | `MClass Environment` or one root environment object in Phase B | structural root; exact only after environment initialization |
| `CARTAGO_WORKSPACE` | `WorkspaceDescriptor` / `WorkspaceId` | path/name/UUID, child workspace relation | `MClass Workspace` | **EXACT** for observed initialized workspace |
| `CARTAGO_ARTIFACT` | `ArtifactId` + `ArtifactInfo` | stable UUID, name, type, workspace, creator, ops, obs props | `MClass Artifact` | **EXACT** for observed initialized artifact |
| `CARTAGO_CONCRETE_ARTIFACT_TYPE` | concrete `Class<? extends cartago.Artifact>` | exact Java implementation type | concrete `MClass` subclass of `Artifact` | **EXACT WHEN TYPE RESOLVED** |
| `CARTAGO_OPERATION_STRUCTURAL` | `OpDescriptor` | operation name/arity | `MClass Operation` + `name`, `arity` | **EXACT** even when full Java signature is unavailable |
| `CARTAGO_OPERATION_NATIVE` | `ArtifactOpMethod.getMethod()` + reflection | parameter/result detail where actually exposed | native `MOperation` on concrete Artifact class | **OPTIONAL PROJECTION**; only when signature evidence is complete |
| `CARTAGO_OBS_PROPERTY_STRUCTURAL` | `ArtifactObsProperty` | name, ordered values, runtime value types | `MClass ObservableProperty` / `Property` | **EXACT AFTER PROPERTY EXISTS** |
| `CARTAGO_OBS_PROPERTY_NATIVE` | property + exact stable type evidence | property value type | native `MAttribute` on concrete Artifact class | **OPTIONAL PROJECTION**; do not infer from source-call syntax alone |
| `CARTAGO_SIGNAL` | official percept/logger callback payload | signal identity/name/args when observed | `MClass Signal` | **EXACT WHEN OBSERVED/DECLARED BY OFFICIAL API** |
| `CARTAGO_AGENT_WORKSPACE` | `ICartagoController.getCurrentAgents()` | agent presence in workspace | `MAssociation Agent--Workspace` in Phase B | **EXACT RUNTIME RELATION** |
| `CARTAGO_FOCUS` | focus/unfocus official callbacks | agent-artifact focus | `MAssociation Agent--Artifact` in Phase B | **EXACT RUNTIME RELATION** |

### Important CArtAgO rule

Do not force:

```text
OpDescriptor -> MOperation
```

unconditionally.

The official API guarantees operation name/arity after descriptor discovery, but full parameter names/types may require a backing Java `Method`; dynamic operations may not expose one. Therefore the safe design is:

```text
OpDescriptor
    -> first-class structural Operation object
    -> optional MOperation projection when exact signature evidence exists
```

The same principle applies to observable properties and `MAttribute`.

---

# 7. Moise model rules

## 7.1 Source authority

Static authority is the object graph loaded by:

```java
moise.os.OS.loadOSFromURI(String)
```

with:

- `moise.os.OS`
- `moise.os.ss.Role`
- `moise.os.ss.Group`
- `moise.os.ss.Link`
- `moise.os.fs.Scheme`
- `moise.os.fs.Mission`
- `moise.os.fs.Goal`
- goal decomposition/plan object
- `moise.os.ns.NS` and norm objects

## 7.2 Mapping rules

| Rule | Moise Java source | Exact data/API | Recommended USE target | Fidelity / note |
|---|---|---|---|---|
| `MOISE_OS` | `moise.os.OS` | OS ID/source, `getSS()`, `getFS()`, `getNS()` | `MClass OrganizationSpecification` (compatibility: `Organization`) | **EXACT STRUCTURAL** |
| `MOISE_SS_CONTAINER` | structural specification object | structural namespace | usually no standalone MClass | **CONTAINER ONLY** unless user must query SS explicitly |
| `MOISE_FS_CONTAINER` | functional specification object | functional namespace | usually no standalone MClass | **CONTAINER ONLY** |
| `MOISE_NS_CONTAINER` | `moise.os.ns.NS` | normative namespace | usually no standalone MClass | **CONTAINER ONLY** |
| `MOISE_ROLE` | `moise.os.ss.Role` | ID, abstract flag, super-role hierarchy | `MClass Role` | **EXACT** |
| `MOISE_ROLE_INHERITANCE` | Role super-role API | directed hierarchy | USE generalization if semantics match; otherwise self-association `superRole` | **EXACT WITH TARGET CHOICE** |
| `MOISE_GROUP` | `moise.os.ss.Group` | ID, nested groups, role/subgroup cardinalities, links | `MClass Group` | **EXACT** |
| `MOISE_GROUP_ROLE_CARDINALITY` | `Group.getRoleCardinality(Role)` | cardinality belongs to `(Group, Role)` pair | relation object / association-class style structure with `min`,`max` | **EXACT ONLY IF RELATION-SCOPED** |
| `MOISE_SUBGROUP_CARDINALITY` | `Group.getSubGroupCardinality(Group)` | cardinality belongs to `(parentGroup, subGroup)` pair | relation object / association-class style structure with `min`,`max` | **EXACT ONLY IF RELATION-SCOPED** |
| `MOISE_LINK` | `moise.os.ss.Link` | source/target role, type, scope, flags | `MClass Link` + associations to source/target `Role` | **EXACT/SUPPORTED_SUBSET** for exposed fields |
| `MOISE_SCHEME` | `moise.os.fs.Scheme` | ID, root goal, missions | `MClass Scheme` | **EXACT** |
| `MOISE_MISSION` | `moise.os.fs.Mission` | ID, cardinality, goal membership | `MClass Mission` | **EXACT** |
| `MOISE_GOAL` | `moise.os.fs.Goal` | ID, type, description, agents-to-satisfy/time, decomposition | `MClass OrganizationalGoal` / `OGoal` | **EXACT/SUPPORTED_SUBSET** according to exposed fields |
| `MOISE_OPLAN` | goal decomposition/plan object | operator + ordered subgoals | `MClass OrganizationalPlan` / `OPlan` + operator `EnumType` | **EXACT** for supported operators |
| `MOISE_NORM` | NS norm object | ID, role, mission, modality, condition, time constraint | `MClass Norm` | **EXACT/SUPPORTED_SUBSET**; never auto-convert into OCL |

### Critical Moise cardinality rule

The code-level API shows that role/subgroup cardinality is **relation-scoped**:

```text
Group.getRoleCardinality(Role)
Group.getSubGroupCardinality(Group)
```

Therefore this is not faithful:

```text
Role.minCardinality
Role.maxCardinality
```

when one Role is reused in different Groups with different bounds.

The code-faithful USE representation must retain the pair identity, for example:

```text
GroupRoleConstraint
    group -> Group
    role  -> Role
    min   : Integer
    max   : Integer
```

and similarly for parent/subgroup cardinality, unless a validated native USE association-class API is selected.

---

# 8. Cross-framework rules

These rules require stronger evidence than name equality.

| Rule | Source evidence required | USE target | Policy |
|---|---|---|---|
| `AGENT_USES_PROGRAM` | JCM agent source/class configuration + exact Jason loaded program identity | `Agent -- AgentProgram` association | exact declaration linkage |
| `AGENT_IN_WORKSPACE` | JCM membership or CArtAgO controller/runtime evidence | `Agent -- Workspace` | never infer from name |
| `AGENT_FOCUSES_ARTIFACT` | JCM focus declaration or CArtAgO focus callback | `Agent -- Artifact` | exact evidence only |
| `AGENT_PLAYS_ROLE` | JCM configured player relation or ORA4MAS runtime board | `Agent -- Role` | exact evidence only; retain group-instance context in provenance/runtime relation |
| `ACTION_CALLS_OPERATION` | official dispatch/binding/runtime correlation, or explicit exact binding | `Action -- Operation` | **never** name-only |
| `BELIEF_FROM_PROPERTY` | exact percept/property provenance | `Belief -- ObservableProperty` | **never** literal-name matching |
| `TRIGGER_FROM_SIGNAL` | exact percept/signal provenance | `Trigger -- Signal` | **never** literal-name matching |
| `AGENT_GOAL_TO_ORG_GOAL` | explicit organizational binding/event evidence | `AgentGoal -- OrganizationalGoal` | same literal is insufficient |

---

# 9. What should become an MClass, and what should not?

## Strong MClass candidates

These are first-class semantic concepts with identity/relations:

```text
AgentProgram
Plan
Trigger
PlanBodyElement / Action
Belief
BeliefRule
AgentGoal

Environment
Workspace
Artifact
Operation
ObservableProperty
Signal

OrganizationSpecification
Role
Group
Link
Scheme
Mission
OrganizationalGoal
OrganizationalPlan
Norm
```

## Normally NOT separate MClasses

These are containers, configuration, provenance, or native target metadata:

```text
JaCaMoProject            -> MModel root
PlanLibrary              -> ordered containment relation
SS / FS / NS             -> namespace/container unless explicitly queryable
SourceInfo               -> trace/provenance
JaCaMoAgentParameters    -> Phase-B deployment materialization
JaCaMoWorkspaceParameters-> Phase-B deployment materialization
JaCaMoOrgParameters      -> Phase-B deployment materialization
```

---

# 10. Phase-B object rule (later)

After Phase-A classes/relations exist:

```text
official source/runtime object
        ↓
stable semantic ID / trace
        ↓
MSystemState
        ↓
MObject / link / attribute value
```

Examples:

```text
one jason.asSyntax.Plan instance
    -> one MObject : Plan

one moise.os.ss.Role definition object
    -> one MObject : Role

one CArtAgO ArtifactId incarnation
    -> one MObject : Artifact (or exact concrete artifact MClass)

one JCM agent declaration / runtime incarnation
    -> one MObject : Agent
```

This is why **Java class -> MClass** and **Java object -> MObject** must not be mixed.

---

# 11. Proposed Java transformation architecture

```java
interface SourceSemanticAdapter<S, N> {
    N adapt(S officialObject);
}

interface UseTypeRule<N> {
    void declareTypes(N semantic, UseModelBuilder use);
    void declareRelations(N semantic, UseModelBuilder use);
}

interface UseInstanceRule<N> {
    void materialize(N semantic, UseStateBuilder state, TraceIndex trace);
}
```

Recommended passes:

```text
PASS 1  Declare classes/enums
PASS 2  Declare attributes/operations
PASS 3  Declare associations/compositions/generalizations
PASS 4  Validate target MModel
PASS 5  Materialize project objects into MSystemState
PASS 6  Attach runtime identities/events
```

---

# 12. Rules that must never be implemented

```text
if (projectName == "hello-world") ...
if (projectName == "auction") ...
if (projectName == "house-building") ...

if (action.name == operation.name) bind()
if (belief.literal == property.name) bind()
if (goal.literal == oGoal.id) bind()
```

Also forbidden:

```text
for every Java class in classpath:
    create same-named MClass
```

Only official semantic classes/objects selected by the source adapters participate.

---

# 13. Main code-derived conclusions

1. `JaCaMoProject` is primarily a **project/deployment configuration authority**, not a unified parsed model containing all Jason/CArtAgO/Moise semantics.
2. Jason already exposes a rich Java semantic model; custom ASL regex parsing is unnecessary for the authoritative path.
3. CArtAgO is partly runtime/descriptive: initialized `ArtifactInfo`, `ArtifactId`, operation descriptors and observable properties are stronger authority than Java-source heuristics.
4. Moise has a strong static Java object graph through `OS.loadOSFromURI`.
5. Moise cardinalities are relation-scoped in the official API; an exact new USE transformation should preserve that context.
6. CArtAgO operation/property projection to native `MOperation`/`MAttribute` must be conditional on exact type/signature evidence.
7. Cross-dimension links require official correlation/declaration evidence, never equal-name inference.
8. The transformation should be implemented in Java using official source adapters and native USE model builders/APIs; Ecore/JSON can remain validation/documentation artifacts rather than source semantic authority.

---

# 14. Recommended first implementation slice

Start only with:

```text
JaCaMoProject      -> MModel
Jason AgentProgram -> MClass AgentProgram
Jason Plan         -> MClass Plan
Jason Trigger      -> MClass Trigger
Jason PlanBody     -> MClass PlanBodyElement
```

plus:

```text
AgentProgram --composition--> Plan
Plan --composition--> Trigger
Plan --ordered composition--> PlanBodyElement
```

Compile/validate that MModel in USE first.

Only after this passes, continue with Belief/Goal, then CArtAgO, then Moise, and finally deployment/runtime objects.
