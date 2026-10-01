# JaCaMo ↔ USE Concept Mapping Rules

**File:** `JACAMO-USE-CONCEPT-MAPPING-RULES.md`  
**Status:** Maintained code-grounded rule contract; implementation status and evidence are tracked in `task.md`.
**Primary goal:** map JaCaMo/Jason/CArtAgO/Moise semantic concepts into **native USE concepts** so that the resulting model runs inside the existing USE application (`MModel` + `MSystemState`).  
**Non-goal:** create a second modeling/execution UI. A mapping panel, if present, is diagnostic only: it shows which source concept matched which rule and which native USE element was created.

---

# 1. Coding baseline

This mapping is based on the audited code/API boundary, not on name similarity and not on the old Ecore mapping as semantic authority.

## JaCaMo baseline

- JaCaMo checkout: `3866858a7ebf6be85d9199c13a09cf4bfb8191be`
- JaCaMo declared version: `1.3.1`
- Jason: `io.github.jason-lang:jason-interpreter:3.3.2`
- CArtAgO: `org.jacamo:cartago:3.1`
- Moise: `org.jacamo:moise:1.1`
- NPL: `org.jacamo:npl:0.6.1`

Important source facts:

- JCM parses to `jacamo.project.JaCaMoProject` plus parameter/configuration objects.
- Jason program semantics are represented by `jason.asSemantics.Agent`, `PlanLibrary`, `Plan`, `Trigger`, `PlanBody`, `Literal`, etc.
- CArtAgO 3.1 operation/type/runtime facts were confirmed from the exact resolved JAR/bytecode where source was unavailable.
- Moise specification semantics are represented by `moise.os.OS`, `SS`, `FS`, `NS`, `Role`, `Group`, `Scheme`, `Mission`, `Goal`, `Plan`, `Norm`, etc.
- Specification/configuration objects and runtime objects are separate.

## USE baseline

Target repository: `https://github.com/nguyentrungnghia1802/use`  
Target USE version: `7.5.0`

Native target concepts used by this mapping:

- `MModel`
- `MClass`
- `MAttribute`
- `MAssociation`
- composition aggregation semantics
- `MOperation`
- `EnumType`
- `MSystemState`

The implementation endpoint is always native USE:

```text
JaCaMo official objects
        ↓
semantic adapters
        ↓
mapping rules in this file
        ↓
USE MModel
        ↓
USE MSystemState
        ↓
existing USE application / OCL / object diagrams / verification
```

A generated `.use` / `.cmd` file is an **optional serialization/export**, not a second runtime.

---

# 2. Rule ID convention

| Prefix | Dimension |
|---|---|
| `Jxx` | JaCaMo/JCM project and deployment configuration |
| `Axx` | Jason / Agent / BDI program concepts |
| `Cxx` | CArtAgO environment/artifact concepts |
| `Mxx` | Moise organization concepts |
| `Xxx` | Cross-dimension relations requiring evidence from more than one subsystem |

The user-requested primary rule families are therefore:

```text
Agent/Jason: A01, A02, ...
CArtAgO:     C01, C02, ...
Moise:       M01, M02, ...
```

---

# 3. USE target interpretation

This file distinguishes two levels.

## 3.1 Model/type level

```text
source semantic concept
        ↓
MClass / MAttribute / MAssociation / MOperation / EnumType
        ↓
MModel
```

Example:

```text
Moise Group
    ↓ M05
USE class Group
    ↓
org.tzi.use.uml.mm.MClass
```

## 3.2 Instance/state level

A concrete parsed/deployed/runtime source object becomes state under the already-created USE model:

```text
source object
        ↓
MObject / attribute values / links
        ↓
MSystemState
```

Therefore:

```text
Java semantic CLASS/API shape  -> determines USE MClass schema
one actual source object       -> becomes an object in MSystemState
```

Do not confuse the two.

---

# 4. JCM / JaCaMo project rules

These rules preserve the project/deployment layer. JCM parameter objects are not Jason/CArtAgO/Moise runtime objects.

| ID | JaCaMo concept | Code authority | USE concept | Native USE target | Rule |
|---|---|---|---|---|---|
| `J01` | JaCaMo Project | `jacamo.project.JaCaMoProject` | model root | `MModel` | Create/identify the USE model corresponding to the imported JaCaMo project. |
| `J02` | Agent declaration | `jacamo.project.JaCaMoAgentParameters` | class `Agent` + state object | `MClass Agent`, later `MObject` | Agent declaration supplies declared name/source/configuration. It is not the Jason program object. |
| `J03` | Workspace declaration | `jacamo.project.JaCaMoWorkspaceParameters` | class `Workspace` + state object | `MClass Workspace`, later `MObject` | Preserve declared workspace separately from runtime `WorkspaceId`. |
| `J04` | Artifact declaration | `jason.mas2j.ClassParameters` stored by `JaCaMoWorkspaceParameters` | class `ArtifactDeclaration` | `MClass` | Preserve declaration name, Java class name and parameters. Do not treat it as a live `cartago.Artifact`. |
| `J05` | Organization declaration | `jacamo.project.JaCaMoOrgParameters` | class `OrganizationDeployment` | `MClass` | Deployment/configuration reference to a Moise organization specification. |
| `J06` | Group declaration / instance request | `jacamo.project.JaCaMoGroupParameters` | class `GroupDeployment` | `MClass` | Keep deployment request distinct from `moise.os.ss.Group` specification. |
| `J07` | Scheme declaration / instance request | `jacamo.project.JaCaMoSchemeParameters` | class `SchemeDeployment` | `MClass` | Keep deployment request distinct from `moise.os.fs.Scheme`. |
| `J08` | Institution declaration | `jacamo.project.JaCaMoInstParameters` | class `InstitutionDeployment` | `MClass` | Preserve only fields supported by code; opaque rule-engine objects remain unsupported/opaque. |
| `J09` | Agent-role raw tuple | `JaCaMoAgentParameters.roles : List<String[]>` | unresolved deployment reference | no direct semantic link until resolved | Tuple `(organization, group, role)` is preserved first; only `X04` may create Agent–Role link. |
| `J10` | Agent-focus raw tuple | `JaCaMoAgentParameters.focus : List<String[]>` | unresolved deployment reference | no direct semantic link until resolved | Tuple `(artifact, workspace, namespace)` is preserved first; only exact resolution/runtime evidence creates focus link. |
| `J11` | `uses` / imported project merge | `JaCaMoProject.importProject(...)` | provenance | trace/provenance | No domain `MClass`; imported content is already merged by official parser semantics. |

### JCM invariant

Never implement:

```text
JaCaMoAgentParameters == jason.asSemantics.Agent
JaCaMo artifact declaration == cartago.Artifact
JaCaMo organization declaration == moise.os.OS
```

They are different code/object layers.

---

# 5. Jason / Agent mapping rules

## 5.1 Core BDI concepts

| ID | Jason/Agent concept | Code authority | USE concept | Native USE target | Transformation |
|---|---|---|---|---|---|
| `A01` | Agent program | `jason.asSemantics.Agent` program surface: source, initial beliefs/goals, `getPL()` | class `AgentProgram` | `MClass` | One semantic AgentSpeak program definition. Keep separate from JCM deployed Agent. |
| `A02` | Plan library | `jason.pl.PlanLibrary` | class `PlanLibrary` | `MClass` | Represents the actual mutable/ordered Jason plan container; source snapshot is taken before mapping. |
| `A03` | Plan | `jason.asSyntax.Plan` | class `Plan` | `MClass` | Map label, trigger, context, body, goal condition/subplans when supported. |
| `A04` | Trigger | `jason.asSyntax.Trigger` | class `Trigger` | `MClass` | Preserve operator, type and literal. Do not infer a CArtAgO signal from name equality. |
| `A05` | Plan body node | `jason.asSyntax.PlanBody` / `PlanBodyImpl` | class `PlanBodyElement` | `MClass` | Preserve `BodyType`, body term and `next` relation. Body is linked/ordered, not just flat action text. |
| `A06` | External action body node | `PlanBody.BodyType.action` | class `Action` | `MClass` | Create Action with `kind = EXTERNAL`; operation target remains unresolved until `X01`. |
| `A07` | Internal action body node | `PlanBody.BodyType.internalAction` | class `Action` | `MClass` | Create Action with `kind = INTERNAL`; never map it to CArtAgO `MOperation`. |
| `A08` | Belief | `jason.asSyntax.Literal` in initial/live belief source | class `Belief` | `MClass` | Preserve canonical literal plus provenance; property origin requires `X02`. |
| `A09` | Agent goal | Jason initial/runtime goal semantic object | class `AgentGoal` | `MClass` | Preserve literal and achievement/test semantics when exposed. |
| `A10` | Jason rule | `jason.asSyntax.Rule` | class `BeliefRule` | `MClass` | Keep as a first-class concept if rule semantics are in verification scope. |
| `A11` | Source provenance | `jason.asSyntax.SourceInfo` | provenance/trace | not a domain `MClass` | Preserve file and line range in trace metadata. |
| `A12` | Runtime action execution | `jason.asSemantics.ActionExec` | runtime event | `MSystemState` mutation/event projection | Not part of the static `MModel` definition. |
| `A13` | Runtime intention | `jason.asSemantics.Intention` | runtime execution concept | runtime-only / optional `MClass` if verification requires it | Do not mix with source `Plan`. |
| `A14` | Runtime event | `jason.asSemantics.Event` | runtime event concept | runtime-only | Distinct from source `Trigger`. |
| `A15` | Runtime transition system | `jason.asSemantics.TransitionSystem` | runtime controller evidence | no direct static class by default | State/trace source, not an AgentSpeak design concept. |

## 5.2 Jason structural relations

| ID | Source relation | USE concept | Native target | Rule |
|---|---|---|---|---|
| `A16` | AgentProgram owns PlanLibrary | composition | `MAssociation` with composition semantics | `AgentProgram 1 *-- 1 PlanLibrary` |
| `A17` | PlanLibrary contains Plans | ordered composition | `MAssociation` | Preserve plan order from the source snapshot. |
| `A18` | Plan has Trigger | composition | `MAssociation` | `Plan 1 *-- 1 Trigger` where source trigger exists. |
| `A19` | Plan has body head/elements | ordered composition/chain | `MAssociation` | Preserve `getBodyNext()` order; never reorder by display text. |
| `A20` | PlanBodyElement next | self-association | `MAssociation` | Optional explicit chain relation when exact order/navigation is required. |
| `A21` | AgentProgram initial beliefs | composition/association | `MAssociation` | Initial beliefs belong to program specification snapshot. |
| `A22` | AgentProgram initial goals | composition/association | `MAssociation` | Initial goals belong to program specification snapshot. |

### Jason enum rules

Finite Jason semantic categories should become USE `EnumType` when the set is stable in the audited version:

- Trigger operator
- Trigger type
- Plan body `BodyType`
- Action kind (`EXTERNAL`, `INTERNAL` as normalized projection)

Do not fabricate enum members that do not exist in the audited code.

---

# 6. CArtAgO mapping rules

**Evidence discipline:** for CArtAgO 3.1, the exact JAR/bytecode is authoritative where source was unavailable.

## 6.1 Environment and artifact concepts

| ID | CArtAgO concept | Code authority | USE concept | Native USE target | Transformation |
|---|---|---|---|---|---|
| `C01` | Environment | `cartago.CartagoEnvironment` | class `Environment` | `MClass` | Root environment concept for initialized runtime/environment view. |
| `C02` | Workspace | `cartago.Workspace` / `WorkspaceId` / `WorkspaceDescriptor` | class `Workspace` | `MClass` | Definition/state concept; runtime UUID belongs to object identity/state, not model name. |
| `C03` | Artifact type | concrete Java subclass of `cartago.Artifact` | class `ArtifactType` | `MClass` | Preserve Java implementation class identity separately from artifact instance identity. |
| `C04` | Artifact instance/reference | `cartago.ArtifactId` + `ArtifactInfo` | class `Artifact` | `MClass` | `ArtifactId` determines runtime identity; one runtime artifact becomes an `MObject`. |
| `C05` | Operation descriptor | `cartago.OpDescriptor` | class `Operation` | `MClass` | Preserve structural operation metadata such as name/arity/type. |
| `C06` | Backing Java operation | `cartago.ArtifactOpMethod` + reflective `Method` | native operation projection | `MOperation` | **Conditional.** Create native `MOperation` only when signature evidence is sufficiently complete. |
| `C07` | Guard | `cartago.ArtifactGuardMethod` | class `Guard` or operation metadata | `MClass` / association | Preserve if verification scope uses guards; do not invent guard semantics from strings. |
| `C08` | Live observable property | `cartago.ObsProperty` | class `LiveObservableProperty` | `MClass` only in `FULL`; excluded from `AUTO` | The audited API does not expose live `ObsProperty` instances. Keep the capability `UNAVAILABLE`; never fabricate an object or values. |
| `C09` | Observable property snapshot | `cartago.ArtifactObsProperty` | `ObservablePropertySnapshot` | `MClass` + `MObject` in `MSystemState` | Materialize only an actual snapshot; preserve its exact identity and recorded values. This is not a live `ObsProperty`. |
| `C10` | Artifact information snapshot | `cartago.ArtifactInfo` | runtime snapshot record | state/provenance | Used to materialize/refresh Artifact, operations and observed properties. |
| `C11` | Signal | `Artifact.signal(...)` + runtime event/filter evidence | class `Signal` | `MClass` | There is no audited `@SIGNAL` annotation; create Signal only from supported runtime/API evidence. |
| `C12` | CArtAgO agent identity | `cartago.AgentId` | runtime agent identity | state/trace | Do not equate directly with Jason/JCM agent name; linking requires cross-rule evidence. |

## 6.2 CArtAgO structural relations

| ID | Source relation | USE concept | Native target | Rule |
|---|---|---|---|---|
| `C13` | Environment contains Workspaces | composition | `MAssociation` | Environment → Workspace. |
| `C14` | Workspace contains Artifacts | composition | `MAssociation` | Workspace → Artifact. |
| `C15` | Artifact has ArtifactType | association | `MAssociation` | Runtime artifact → exact Java artifact type identity. |
| `C16` | Artifact exposes Operations | composition/association | `MAssociation` | Preserve exact descriptor ownership. |
| `C17` | Artifact has ObservableProperties | composition | `MAssociation` | Materialize only properties actually established by source/runtime evidence. |
| `C18` | Operation uses Guard | association | `MAssociation` | Only when descriptor/bytecode exposes exact guard binding. |
| `C19` | Workspace contains/joined Agents | association | `MAssociation` | Runtime relation from controller/workspace observation. |
| `C20` | Agent focuses Artifact | association | `MAssociation` | Runtime/config relation; exact focus evidence only. |

### CArtAgO native projection rule

Structural `Operation` and the distinct live/snapshot property concepts remain explicit even when a native projection is unavailable.

```text
OpDescriptor
   -> Operation MClass/object          [always when descriptor exists]
   -> MOperation                       [only if exact signature evidence exists]

ObsProperty
   -> LiveObservableProperty MClass  [FULL profile only; currently no live instances/API]
ArtifactObsProperty
   -> ObservablePropertySnapshot MClass/object [only from an actual snapshot]
   -> values/valueTypes recorded as exact snapshot data; do not infer domain MAttributes
```

---

# 7. Moise mapping rules

## 7.1 Organization specification concepts

| ID | Moise concept | Code authority | USE concept | Native USE target | Transformation |
|---|---|---|---|---|---|
| `M01` | Organization specification | `moise.os.OS` | class `Organization` | `MClass` | Root Moise specification concept. |
| `M02` | Structural specification | `moise.os.ss.SS` | class `StructuralSpecification` | `MClass` | Preserve SS as explicit container to remain faithful to code model. |
| `M03` | Functional specification | `moise.os.fs.FS` | class `FunctionalSpecification` | `MClass` | Preserve FS as explicit container. |
| `M04` | Normative specification | `moise.os.ns.NS` | class `NormativeSpecification` | `MClass` | Preserve NS as explicit container. |
| `M05` | Group | `moise.os.ss.Group` | **class `Group`** | `MClass` | Direct code-grounded concept mapping. |
| `M06` | Role | `moise.os.ss.Role` | class `Role` | `MClass` | Preserve id, abstract flag and super-role relation. |
| `M07` | Role relation base | `moise.os.ss.RoleRel` | class `RoleRelation` | `MClass` | Preserve relation identity/context. |
| `M08` | Link | `moise.os.ss.Link` | class `Link` | `MClass` | Preserve source/target roles, scope/type/direction flags exposed by code. |
| `M09` | Compatibility | `moise.os.ss.Compatibility` | class `Compatibility` | `MClass` | Do not merge into Link unless code semantics explicitly justify it. |
| `M10` | Scheme | `moise.os.fs.Scheme` | class `Scheme` | `MClass` | Preserve scheme id, root goal, plans, missions. |
| `M11` | Mission | `moise.os.fs.Mission` | class `Mission` | `MClass` | Mission definition. Cardinality is not a universal Mission attribute. |
| `M12` | Organizational Goal | `moise.os.fs.Goal` | class `OrganizationalGoal` | `MClass` | Preserve type, description, args/dependencies, textual `ttf`, minimum agents where supported. |
| `M13` | Organizational Plan | `moise.os.fs.Plan` | class `OrganizationalPlan` | `MClass` | Preserve target goal, ordered/defined subgoals, operator and success-rate evidence. |
| `M14` | Norm | `moise.os.ns.Norm` | class `Norm` | `MClass` | Preserve condition, role, mission, operation type, textual time constraint. Do not auto-generate OCL. |
| `M15` | Group-role cardinality | `Group` + `CardinalitySet<Role>` | class `GroupRoleCardinality` | `MClass` | Exact tuple `(group, role, min, max)`. |
| `M16` | Parent-subgroup cardinality | `Group` + `CardinalitySet<Group>` | class `SubGroupCardinality` | `MClass` | Exact tuple `(parentGroup, subGroup, min, max)`. |
| `M17` | Scheme-mission cardinality | `Scheme` + `CardinalitySet<Mission>` | class `SchemeMissionCardinality` | `MClass` | Exact tuple `(scheme, mission, min, max)`. |

## 7.2 Moise structural relations

| ID | Source relation | USE concept | Native target | Rule |
|---|---|---|---|---|
| `M18` | OS owns SS | composition | `MAssociation` | Organization → StructuralSpecification |
| `M19` | OS owns FS | composition | `MAssociation` | Organization → FunctionalSpecification |
| `M20` | OS owns NS | composition | `MAssociation` | Organization → NormativeSpecification |
| `M21` | SS contains Roles | composition | `MAssociation` | StructuralSpecification → Role |
| `M22` | SS/root structure contains Groups | composition | `MAssociation` | StructuralSpecification → Group/root group |
| `M23` | Group contains Subgroups | composition | `MAssociation` | Preserve parent/subgroup hierarchy. |
| `M24` | Role has superRoles | self-association | `MAssociation` | **Do not convert directly to UML generalization** until semantic equivalence is separately proven. |
| `M25` | Link source Role | association | `MAssociation` | Link → source Role |
| `M26` | Link target Role | association | `MAssociation` | Link → target Role |
| `M27` | Compatibility source Role | association | `MAssociation` | Compatibility → source Role |
| `M28` | Compatibility target Role | association | `MAssociation` | Compatibility → target Role |
| `M29` | GroupRoleCardinality owner Group | association | `MAssociation` | exact owner endpoint |
| `M30` | GroupRoleCardinality member Role | association | `MAssociation` | exact member endpoint |
| `M31` | SubGroupCardinality parent Group | association | `MAssociation` | exact owner endpoint |
| `M32` | SubGroupCardinality child Group | association | `MAssociation` | exact member endpoint |
| `M33` | FS contains Schemes | composition | `MAssociation` | FunctionalSpecification → Scheme |
| `M34` | Scheme contains Missions | composition | `MAssociation` | Scheme → Mission |
| `M35` | Scheme has root Goal | association/composition according to chosen ownership model | `MAssociation` | Do not duplicate Goal identity. |
| `M36` | SchemeMissionCardinality owner Scheme | association | `MAssociation` | exact owner endpoint |
| `M37` | SchemeMissionCardinality member Mission | association | `MAssociation` | exact member endpoint |
| `M38` | Mission contains/references Goals | association | `MAssociation` | Preserve mission goal membership. |
| `M39` | OrganizationalGoal has Plan | association/composition | `MAssociation` | Preserve exact Moise goal-plan ownership/reference. |
| `M40` | OrganizationalPlan has subGoals | ordered association | `MAssociation` | Preserve operator plus source order when defined. |
| `M41` | NS contains Norms | composition | `MAssociation` | NormativeSpecification → Norm |
| `M42` | Norm applies to Role | association | `MAssociation` | Each Norm references 0..1 Role; one Role may be referenced by 0..* Norms. Scalar reference is unordered. |
| `M43` | Norm references Mission | association | `MAssociation` | Each Norm references 0..1 Mission; one Mission may be referenced by 0..* Norms. Scalar reference is unordered. |

### Moise enum rules

Where exact source enums are finite and verified, map them to USE `EnumType`, including:

- `moise.os.ns.NS.OpTypes` (`obligation`, `permission`)
- Moise organizational plan operator (`sequence`, `choice`, `parallel`)
- other finite goal/relation kinds only when exact enum/value sets are confirmed in the audited API

### Moise time rule

`Goal.ttf` remains textual in the audited source.

Do **not** silently transform:

```text
"10"
"10 min"
"tomorrow"
```

into numeric duration/time semantics unless a separate exact engine/parser contract proves the conversion.

---

# 8. Cross-dimension mapping rules

These rules are intentionally separate because no one source API proves them by itself.

| ID | Relation | Required evidence | USE target | Policy |
|---|---|---|---|---|
| `X01` | Jason Action → CArtAgO Operation | exact dispatch/binding/runtime correlation or explicit validated binding | `MAssociation Action--Operation` | Never same-name matching. |
| `X02` | Jason Belief → CArtAgO ObservablePropertySnapshot | exact percept/property provenance | `MAssociation Belief--ObservablePropertySnapshot` | Never literal/property-name matching; does not imply C08 live-property support. |
| `X03` | Jason Trigger → CArtAgO Signal | exact percept/signal provenance | `MAssociation Trigger--Signal` | Never trigger/signal-name matching. |
| `X04` | JCM/Jason Agent → Moise Role | exact JCM role tuple resolved against OS or runtime board role-player evidence | `MAssociation Agent--Role` | Preserve organization/group context in trace. |
| `X05` | Agent → Workspace | exact JCM workspace membership or CArtAgO runtime evidence | `MAssociation Agent--Workspace` | Do not infer from local names. |
| `X06` | Agent → Artifact focus | exact JCM focus resolution or CArtAgO runtime focus event | `MAssociation Agent--Artifact` | Runtime `ArtifactId` preferred for live identity. |
| `X07` | AgentGoal → OrganizationalGoal | explicit organizational binding/runtime evidence | `MAssociation AgentGoal--OrganizationalGoal` | Same literal is insufficient. |
| `X08` | JCM ArtifactDeclaration → runtime Artifact | capture of actual `Workspace.makeArtifact(...) -> ArtifactId` creation or equivalent exact observation | trace + `MAssociation` if modeled | Declaration name alone is insufficient. |
| `X09` | JCM Agent name → CArtAgO `AgentId` | exact join/action/focus observation | trace identity alias | String equality alone is not universal identity. |

---

# 9. Native USE execution contract

This is mandatory for implementation.

## 9.1 Primary result

After import/mapping, the plugin must produce or obtain:

```text
org.tzi.use.uml.mm.MModel
```

and install/use that model in the **existing USE application/session**.

The plugin must then materialize project/runtime objects in:

```text
MSystemState
```

so ordinary USE facilities operate on the result:

```text
USE class model
USE object/state views
USE OCL evaluation
USE invariants / pre-postconditions
USE object diagrams
USE existing shell/GUI model handling
```

## 9.2 Native model creation and optional text export

Production mapping builds `MModel` and `MSystemState` through native USE APIs. The optional `.use` export is a serialization/reproducibility artifact:

```text
native MModel
 -> USE printer
 -> .use artifact
 -> optional recompile/parity check
```

The generated text/compiler path is not the production semantic authority and must not replace native model/state construction.

## 9.3 No second execution UI

Forbidden architecture:

```text
JaCaMo import
 -> custom model
 -> custom second model editor/runtime window
```

Required architecture:

```text
JaCaMo import
 -> rules
 -> USE MModel/MSystemState
 -> current USE UI
```

---

# 10. Mapping UI contract

A mapping UI may exist, but it is **inspection-only**.

It may show:

| Source | Rule | Target | Status |
|---|---|---|---|
| `moise.os.ss.Group` | `M05` | `MClass Group` | applied |
| `jason.asSyntax.Plan` | `A03` | `MClass Plan` | applied |
| `cartago.OpDescriptor` | `C05` | `MClass Operation` | applied |
| exact action-operation evidence | `X01` | `MAssociation Action--Operation` | applied/not available |

Recommended fields:

```text
rule id
source subsystem
source FQCN
source semantic id
target USE kind
target USE name/id
fidelity
evidence
status
diagnostic
```

The panel does not own or execute the mapped model. USE does.

---

# 11. Fidelity labels

Every applied rule should record one of:

| Label | Meaning |
|---|---|
| `EXACT` | Source API fact is represented without semantic invention. |
| `SUPPORTED_SUBSET` | Target intentionally preserves only a defined faithful subset. |
| `CONDITIONAL` | Mapping is emitted only when additional exact evidence exists. |
| `RUNTIME_ONLY` | Fact belongs to `MSystemState`/runtime trace, not the static `MModel`. |
| `PROVENANCE_ONLY` | Kept in trace/evidence, not as a domain concept. |
| `UNKNOWN` | Code/API does not establish the fact; no target is fabricated. |

---

# 12. Implementation status authority

This document defines mapping semantics, not a forward implementation schedule. Phase gates, completed work, remaining gaps, and evidence are maintained in `task.md`; actual behavior is determined by production code and executable tests.

---

# 13. Hard constraints

Never implement any of the following:

```text
same name => same semantic object
same Java class name => same USE class meaning
JCM declaration => live runtime object
Agent name string => universal CArtAgO/Moise identity
Action name => Operation binding
Belief literal => ObservablePropertySnapshot binding
Agent goal literal => OrganizationalGoal binding
Moise Norm => automatically generated OCL
```

Never hard-code case-study names such as:

```text
Hello World
Auction
House Building
```

into production mapping rules.

---

# 14. Minimal example

For a Moise `Group`:

```text
Source:
    moise.os.ss.Group

Rule:
    M05

USE concept:
    class

Native target:
    MClass("Group")
```

For a concrete source Group object:

```text
moise.os.ss.Group object
        ↓ M05 schema already exists
MObject : Group
        ↓
MSystemState
```

For its role cardinality:

```text
Group + Role + Cardinality
        ↓ M15
MObject : GroupRoleCardinality
    min : Integer
    max : Integer
        ↓
links to Group and Role
```

This preserves the actual Moise code semantics; cardinality is not flattened into a universal `Role.min/max`.

---

# 15. Architectural conclusion

The production path defined by this mapping is:

```text
.jcm / .asl / Java artifact classes / Moise XML
        ↓
official JaCaMo/Jason/CArtAgO/Moise Java objects
        ↓
code-grounded adapters
        ↓
Jxx / Axx / Cxx / Mxx / Xxx rules
        ↓
native USE MModel
        ↓
native USE MSystemState
        ↓
existing USE application
```

The mapping viewer is only an explanation/trace surface:

```text
source concept
rule id
target USE concept
evidence
fidelity
```

It is not a replacement for USE and must not become a second model runtime.
