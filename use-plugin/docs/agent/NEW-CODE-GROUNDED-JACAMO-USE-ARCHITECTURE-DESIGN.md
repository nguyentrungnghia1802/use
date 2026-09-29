# New Architecture Design — Code-Grounded JaCaMo → Native USE Transformation

**Document purpose:** architectural direction for refactoring the current USE–JaCaMo project.  
**Audience:** implementation AI/engineer who will first inspect the existing repository, compare this design with the two mapping specifications, then produce a repository-specific migration plan.  
**Status:** target design; this document does **not** prescribe exact file edits until the repository has been audited.

Companion specifications:

1. `JACAMO-USE-JAVA-MODEL-TRANSFORMATION-SPEC.md`
2. `JACAMO-USE-CONCEPT-MAPPING-RULES.md`

The implementation plan must be derived by reading the **actual current project code** and reconciling it with these three documents.

---

# 1. Primary goal

The system must transform official JaCaMo/Jason/CArtAgO/Moise semantics into a model that is owned and executed by the **existing USE application**.

The final production flow is:

```text
JaCaMo design/runtime sources
(.jcm, .asl, CArtAgO Java classes, Moise XML)
        ↓
official JaCaMo/Jason/CArtAgO/Moise APIs and objects
        ↓
JaCaMo-side adapters
        ↓
neutral/code-grounded semantic contract
        ↓
USE-side semantic model
        ↓
mapping rules Jxx / Axx / Cxx / Mxx / Xxx
        ↓
native USE MModel
        ↓
native USE MSystemState
        ↓
existing USE application
        ├─ existing model browser
        ├─ existing object diagrams
        ├─ existing OCL engine
        ├─ existing invariant/pre-post facilities
        └─ verification/reporting
```

The project must **not** introduce a second modeling runtime or a separate model editor that competes with USE.

A JaCaMo workbench/panel may remain, but its role is inspection, import/control, diagnostics and mapping trace only.

---

# 2. Non-negotiable architectural principles

## 2.1 Code/API is the semantic authority

The transformation must be grounded in the official Java object models actually used by the inspected JaCaMo distribution.

Authority order:

```text
1. actual production source/code and exact resolved dependency API/bytecode
2. executable tests and runtime evidence
3. code-grounded semantic contract
4. mapping specifications
5. old Ecore/mapping artifacts as historical/reference material only
```

Do not derive production semantics primarily from filenames, regexes, old Ecore classifiers, or name similarity.

---

## 2.2 Preserve source layers

The JaCaMo model is not one unified Java object graph.

The implementation must preserve at least these layers:

```text
A. project/deployment specification
   JaCaMoProject
   JaCaMoAgentParameters
   JaCaMoWorkspaceParameters
   JaCaMoOrgParameters
   ...

B. Jason program model
   Agent
   PlanLibrary
   Plan
   Trigger
   PlanBody
   Literal
   ...

C. CArtAgO type/runtime model
   Artifact subclass metadata
   OpDescriptor
   ArtifactId
   ArtifactInfo
   ObsProperty
   ArtifactObsProperty
   ...

D. Moise specification model
   OS
   SS
   FS
   NS
   Role
   Group
   Scheme
   Mission
   Goal
   Plan
   Norm
   ...

E. runtime state
   Jason runtime state
   CArtAgO runtime identities/events
   ORA4MAS/NPL/Moise runtime observations
```

These layers may be linked only by exact source/configuration/runtime evidence.

---

## 2.3 Type transformation and instance materialization are separate

The implementation must explicitly separate:

```text
MODEL / TYPE
source semantic concepts
    → MClass / MAttribute / MAssociation / MOperation / EnumType
    → MModel
```

from:

```text
OBJECT / STATE
concrete source/deployment/runtime objects
    → MObject / links / values
    → MSystemState
```

Do not confuse a Java source type with a runtime object.

Example:

```text
jason.asSyntax.Plan
    determines the source semantic shape of Plan
        ↓
A03
MClass Plan

one concrete parsed Plan object
        ↓
instance materialization
MObject : Plan
```

---

## 2.4 Fail closed; never guess cross-dimensional semantics

Forbidden assumptions include:

```text
same name => same semantic object
action name == operation name => bound
belief literal == property name => percept origin
agent goal literal == organizational goal id => same goal
JCM agent name == CArtAgO AgentId == Moise runtime agent identity
artifact declaration name == runtime ArtifactId
```

Unproven relationships remain unresolved/UNKNOWN/evidence-only.

---

# 3. Source-side design

## 3.1 JCM / project adapter

The source adapter must consume the official JaCaMo project/configuration model.

Expected source authority includes:

```text
jacamo.project.JaCaMoProject
jacamo.project.JaCaMoAgentParameters
jacamo.project.JaCaMoWorkspaceParameters
jacamo.project.JaCaMoOrgParameters
jacamo.project.JaCaMoGroupParameters
jacamo.project.JaCaMoSchemeParameters
jacamo.project.JaCaMoInstParameters
jason.mas2j.ClassParameters
```

Responsibilities:

- copy exact project/deployment declarations;
- preserve raw role/focus reference tuples before resolution;
- preserve source paths/options/parameters required for provenance;
- preserve declaration identity separately from runtime identity;
- never fabricate Jason/CArtAgO/Moise objects that are not contained in the project model.

The JCM adapter is a configuration/specification adapter, not a whole-system parser.

---

## 3.2 Jason adapter

Use official Jason program objects.

Core source concepts include:

```text
jason.asSemantics.Agent
jason.pl.PlanLibrary
jason.asSyntax.Plan
jason.asSyntax.Trigger
jason.asSyntax.PlanBody
jason.asSyntax.Literal
jason.asSyntax.Rule
jason.asSyntax.SourceInfo
```

Responsibilities:

- expose AgentSpeak program definitions through official object access;
- preserve plan trigger/context/body structure;
- preserve `PlanBody` node kind, term and next relation;
- distinguish external action, internal action, goal/test/belief-update/etc. body kinds;
- preserve source/provenance;
- keep runtime objects such as `TransitionSystem`, `Circumstance`, `Intention`, runtime `Event`, and `ActionExec` out of the static program model unless explicitly modeled as runtime facts.

Do not reconstruct ASL semantics with a custom text parser if official Jason objects already provide the information.

---

## 3.3 CArtAgO adapter

Use official CArtAgO type/runtime evidence.

Relevant concepts include:

```text
Artifact subclass metadata
cartago.Workspace
cartago.WorkspaceId
cartago.Artifact
cartago.ArtifactId
cartago.ArtifactDescriptor
cartago.ArtifactInfo
cartago.OpDescriptor
cartago.ArtifactOpMethod
cartago.ArtifactGuardMethod
cartago.ObsProperty
cartago.ArtifactObsProperty
cartago.AgentId
```

Responsibilities:

- preserve Java artifact type identity separately from artifact instance identity;
- preserve runtime `WorkspaceId`, `ArtifactId`, `AgentId` when observed;
- expose operation descriptors from CArtAgO's real operation setup/metadata;
- expose observable properties only when established by authoritative type/runtime evidence;
- distinguish live `ObsProperty` from `ArtifactObsProperty` snapshots;
- treat native USE `MOperation`/`MAttribute` generation as a projection that requires sufficient type/signature evidence.

Java-source AST analysis may remain a secondary provenance/enrichment source only when required, never as the primary authority when CArtAgO itself exposes stronger semantics.

---

## 3.4 Moise adapter

Use the official Moise object graph:

```text
moise.os.OS
moise.os.ss.SS
moise.os.ss.Role
moise.os.ss.Group
moise.os.ss.RoleRel
moise.os.ss.Link
moise.os.ss.Compatibility
moise.os.fs.FS
moise.os.fs.Scheme
moise.os.fs.Mission
moise.os.fs.Goal
moise.os.fs.Plan
moise.os.ns.NS
moise.os.ns.Norm
```

Responsibilities:

- preserve OS → SS/FS/NS structure;
- preserve role/group/scheme/mission/goal/plan/norm definitions;
- preserve Link and Compatibility as distinct source concepts;
- preserve role hierarchy explicitly;
- preserve textual time semantics unless a stronger engine contract proves normalization;
- preserve relation-scoped cardinalities exactly.

Required cardinality forms:

```text
(group, role, min, max)
(parentGroup, subGroup, min, max)
(scheme, mission, min, max)
```

Do not flatten these into universal `Role.min/max`, `Group.min/max`, or `Mission.min/max`.

---

# 4. Neutral semantic boundary

The code should expose a plugin-owned, immutable or snapshot-safe semantic representation between official JaCaMo objects and USE transformation logic.

Conceptually:

```text
JacamoSpecificationModel
├── project
├── jasonPrograms
├── cartagoModel
├── moiseSpecifications
└── evidence / identity / diagnostics
```

A suggested conceptual shape is:

```text
JacamoSpecificationModel
  project: JacamoProjectSpec
  jasonPrograms: List<JasonProgramSpec>
  artifactTypes: List<CartagoArtifactTypeSpec>
  organizations: List<MoiseOSSpec>
  relations: List<SemanticRelation>
  evidence: EvidenceIndex
```

This is a design boundary, not a requirement to use these exact Java names.

The implementation AI must inspect the current `JaCaMoSemanticModel`, neutral Bridge DTOs and related classes before deciding whether to:

- extend them;
- replace selected structures;
- introduce versioned DTOs;
- add compatibility adapters.

Avoid a big-bang rewrite if the existing model can be safely evolved.

---

# 5. Mapping rule architecture

The mapping specification is the primary transformation contract.

Rule families:

```text
Jxx = JaCaMo/JCM project/deployment
Axx = Jason/Agent/BDI
Cxx = CArtAgO
Mxx = Moise
Xxx = cross-dimensional exact relations
```

Each implementation rule must expose enough metadata to create a trace record.

Conceptual rule interface:

```java
interface ConceptMappingRule<S> {
    RuleId id();
    boolean supports(S source);
    void declareModel(S source, UseModelBuilder target, MappingTrace trace);
}
```

Instance/state behavior should remain separate:

```java
interface InstanceMappingRule<S> {
    RuleId id();
    void materialize(S source, UseStateBuilder target, MappingTrace trace);
}
```

The exact interface/class names must be chosen after inspecting the current codebase.

---

# 6. Mapping trace is first-class

Every generated target element must be traceable.

Minimum trace data:

```text
ruleId
source dimension
source FQCN / source semantic kind
source semantic identity
source evidence
target USE kind
target USE identity
fidelity
status
diagnostic
```

Example:

```text
ruleId: M05
source: moise.os.ss.Group
sourceId: ...
targetKind: MClass
targetName: Group
fidelity: EXACT
status: APPLIED
```

This trace powers the Mapping Inspector UI and runtime resolution.

---

# 7. Native USE target design

## 7.1 Static model

The final structural target must be an actual USE:

```text
org.tzi.use.uml.mm.MModel
```

using native USE constructs:

```text
MClass
MAttribute
MAssociation
MOperation
EnumType
generalization only when semantic equivalence is proven
```

The implementation may temporarily retain the existing text/compiler path if that is the safest migration step:

```text
semantic rules
    → generated .use text
    → USE compiler
    → MModel
```

but the desired architecture is:

```text
semantic rules
    → native USE model builder/API
    → MModel
```

The resulting `MModel`, not generated text, is the production object.

---

## 7.2 Runtime/state model

Concrete project and runtime facts must materialize into:

```text
MSystemState
```

using native USE objects, links and attribute values.

Examples:

```text
francois : Agent
team : Group
auctioneer : Role
gui1 : Artifact
plan42 : Plan
```

and exact associations established by the mapping.

Runtime events update the same `MSystemState`; they do not create a parallel state engine.

---

# 8. Integration with existing USE

This is a mandatory architectural requirement.

After import succeeds:

```text
new MModel + MSystemState
        ↓
activate/install in current USE session
```

The model must be usable by existing USE facilities.

Expected integration targets include:

```text
existing model browser
existing object diagram
existing OCL evaluator
existing invariant/pre/post mechanisms
existing shell/session
existing verification/report path
```

The implementation AI must inspect the actual USE application/session/model-loading APIs and identify the correct activation path.

Do not create a custom substitute for the USE runtime.

---

# 9. Workbench / Mapping Inspector

The current JaCaMo plugin UI should evolve into an import/control/inspection surface.

Permitted responsibilities:

```text
select/import JaCaMo project
connect/reconnect Bridge
show source/contract status
show mapping rules applied
show mapping trace
show evidence/fidelity/diagnostics
navigate to generated USE element if feasible
show runtime synchronization status
```

Not permitted:

```text
own a second domain model runtime
implement a second OCL engine
implement a separate object diagram engine
replace USE model browser
replace USE state/session management
```

The central view should resemble:

```text
Rule | Source semantic element | Target USE element | Fidelity | Status
A03  | Jason Plan              | MClass Plan        | EXACT/...| Applied
C05  | OpDescriptor            | MClass Operation   | EXACT    | Applied
M05  | Moise Group             | MClass Group       | EXACT    | Applied
X01  | Action → Operation      | MAssociation       | ...      | Unresolved/Applied
```

---

# 10. Existing code: reuse vs refactor philosophy

The implementation AI must inspect the repository before deciding exact edits.

Default expectation:

## KEEP where correct

Likely reusable foundations include:

```text
Bridge transport and contract infrastructure
authentication/schema/version checks
snapshot coordination
runtime identity/session/generation
gap/resync handling
trace infrastructure
OCL engine integration
verification services
plugin packaging/discovery
USE core and GUI
```

## REFACTOR

Expected refactor areas:

```text
semantic DTO/model vocabulary
NativeSemanticAdapter
structural mapping rules
TransformationPlanner or successor
InstancePlanner or successor
DirectUseBackend / UseModelBuilder path
mapping trace schema if rule IDs are missing
JaCaMoWorkbenchPanel
cross-dimensional resolution
runtime target resolution
```

## HISTORICAL / NON-PRODUCTION

Custom source reconstruction components that duplicate official framework semantics should not return to production authority.

Examples may include legacy:

```text
JcmSemanticParser
JasonSourceParser
CartagoSourceExtractor
MoiseXmlParser
legacy fuzzy resolver
old in-process connectors
```

The implementation AI must verify their current call graph before changing or removing anything.

Do not delete historical tests/evidence merely because production no longer calls these classes.

---

# 11. Old Ecore / Mapping V2 policy

The old Ecore and Mapping V2 artifacts are no longer the primary source semantic authority for this new direction.

They may remain useful for:

```text
historical comparison
regression evidence
compatibility tests
terminology
old goldens
migration trace
```

They must not force the new code-grounded model to preserve a known lossy representation.

Example:

```text
official Moise:
(group, role, min, max)
```

must not be flattened merely because an old Ecore placed `min/max` on Role.

If compatibility with the frozen mapping is required, implement it as an explicit compatibility/export projection and label representation loss.

Do not silently mutate old frozen artifacts in place.

---

# 12. Runtime design

Static design transformation and runtime synchronization must share identity/trace but remain distinct.

```text
STATIC
ModelSnapshot
    ↓
JacamoSpecificationModel
    ↓
mapping rules
    ↓
MModel + initial/project MSystemState

RUNTIME
RuntimeSnapshot / RuntimeEvent
    ↓
runtime semantic adapter
    ↓
exact trace/identity resolution
    ↓
MSystemState mutation
    ↓
existing USE verification
```

Only facts with sufficient evidence may mutate USE state.

Other facts remain:

```text
EVIDENCE_ONLY
UNKNOWN
UNAVAILABLE
```

Do not fabricate target state to make OCL appear complete.

---

# 13. Mapping fidelity policy

Every rule/result must use an explicit classification.

Recommended values:

```text
EXACT
SUPPORTED_SUBSET
CONDITIONAL
RUNTIME_ONLY
PROVENANCE_ONLY
REPRESENTATION_LOSS
UNKNOWN
UNSUPPORTED
```

Definitions must be documented in code and UI.

A transformation must never silently convert `UNKNOWN` into an ordinary target element.

---

# 14. Compatibility and migration strategy

Do not replace the entire working system at once.

Preferred migration pattern:

```text
1. inspect current repository
2. identify actual production call path
3. introduce new code-grounded rule path beside reusable infrastructure
4. prove one vertical slice
5. compare old/new output
6. move production authority to new path
7. retain old path as historical oracle until migration is stable
8. remove/deprecate only after explicit tests prove no production caller remains
```

The first vertical slice should be small enough to prove native USE integration.

Recommended initial concept scope:

```text
J01 project
A01 AgentProgram
A02 PlanLibrary
A03 Plan
A04 Trigger
A05 PlanBodyElement
A16..A20 core Jason relations
```

Success means these become native USE model elements in the existing USE session.

---

# 15. Testing architecture

Every migration phase must test both semantic fidelity and integration.

## Unit tests

For each rule:

```text
official/normalized source input
→ exact target USE construct
→ correct trace rule ID
→ correct fidelity
```

Include negative tests proving that unsupported evidence does not create a target.

## Model tests

Validate:

```text
classes
attributes
enums
associations
compositions
operation projections
multiplicity/order
duplicate identity prevention
```

## State tests

Validate:

```text
MObject creation
attribute assignment
link insertion/removal
runtime identity recreation
no name-based collapse
```

## Integration tests

Required progression:

```text
Hello World
→ Auction
→ House Building
```

Do not hard-code case-specific branches.

House Building is particularly important because its `.jcm` alone is not the complete system specification; dynamic organization/artifact behavior originates elsewhere.

## Native USE acceptance test

A migration is not complete merely because `.use` text compiles.

The gate is:

```text
import JaCaMo
→ native MModel active in current USE application
→ MSystemState active
→ existing USE OCL evaluates against it
→ existing USE UI can inspect it
```

---

# 16. What the implementation AI must do first

Before editing code, the implementation AI must audit the current repository and produce a new repository-specific plan.

It must inspect at least:

```text
module layout
current production entry point
DefaultJaCaMoFacade/import flow
Bridge ModelSnapshot DTOs
JaCaMoBridgePlatform
OfficialProjectAdapter
OfficialJasonAdapter
CArtAgO adapters/snapshot sources
OfficialMoiseAdapter
NativeSemanticAdapter
JaCaMoSemanticModel
ActiveBaseline / MappingLoader if still active
TransformationPlanner
InstancePlanner
TextBackend
DirectUseBackend
TraceBuilder / TraceIndex
BridgeRuntimeProjector
RuntimeMutationEngine
JaCaMoWorkbenchAction
JaCaMoWorkbenchPanel
USE model/session activation APIs
tests and packaging rules
```

For every item, classify:

```text
KEEP
REFACTOR
REPLACE
DEPRECATE
HISTORICAL_ONLY
UNKNOWN_NEEDS_AUDIT
```

Do not infer status from class names alone; inspect callers and tests.

---

# 17. Required plan output from the implementation AI

After reading the project and the three design/mapping documents, the AI must return a plan containing:

### A. Current-state call graph

```text
JaCaMo source/runtime
→ exact current adapter classes
→ contract
→ USE semantic model
→ transformation
→ MModel/MSystemState
→ GUI/session/OCL
```

Include exact classes/files.

### B. Gap analysis against this design

For every architectural layer:

```text
already correct
partially correct
missing
conflicting
obsolete
```

### C. File/class impact matrix

For example:

```text
path/class
current responsibility
target responsibility
action
why
dependent tests
```

### D. Migration phases

Each phase must have:

```text
goal
files/classes changed
rules implemented
tests added/updated
acceptance gate
rollback point
```

### E. Mapping-rule coverage plan

Show exactly where:

```text
Jxx
Axx
Cxx
Mxx
Xxx
```

will be implemented and traced.

### F. Native USE integration plan

Identify the actual USE APIs/classes required to:

```text
create/build MModel
create MSystemState
install/activate model in current USE session
refresh existing USE UI
run existing OCL/verification
```

This section must be based on source inspection, not guessed API names.

### G. Risk list

At minimum:

```text
semantic representation loss
Bridge contract versioning
runtime identity mismatch
USE session activation
old V2 compatibility
plugin packaging
case-study regressions
cross-dimensional unresolved bindings
```

---

# 18. Constraints for implementation

The implementation must not:

```text
patch JaCaMo core unless a proven blocker requires it
reintroduce custom ASL/XML semantics as production authority
create fuzzy/name-based semantic links
hard-code Hello/Auction/House behavior
auto-translate Moise norms into OCL
collapse runtime identities into source declaration names
open a second modeling/runtime UI instead of USE
silently discard unsupported source semantics
silently modify frozen historical artifacts
```

---

# 19. Definition of done

The new architecture is considered implemented only when all of the following hold:

```text
1. official JaCaMo/Jason/CArtAgO/Moise objects are the production semantic authority;
2. all production transformation rules have stable J/A/C/M/X rule IDs;
3. each generated USE element is traceable to source evidence and rule;
4. static transformation produces native USE MModel;
5. instance/runtime materialization uses native MSystemState;
6. the generated model/state is active in the existing USE session;
7. existing USE OCL/verification operates on that model;
8. Mapping Inspector is explanatory only;
9. unresolved cross-dimension facts remain unresolved/evidence-only;
10. no case-specific hard coding exists;
11. Hello, Auction and House pass their explicitly supported acceptance scope;
12. historical mappings/parsers no longer determine production semantics.
```

---

# 20. Final target

```text
                     JaCaMo
                        │
          official code/API/object model
                        │
                        ▼
             Code-grounded semantic model
                        │
            J/A/C/M/X transformation rules
                        │
                        ▼
               Native USE MModel
                        │
                        ▼
             Native USE MSystemState
                        │
                        ▼
               Existing USE runtime
               /       |        \
             OCL   diagrams   verification

Mapping Inspector
    │
    └── observes source → rule → USE target
        but does not replace USE
```

This is the architectural direction. The exact migration plan must be produced only after the implementation AI audits the current repository and compares the actual code against this design and the two companion mapping specifications.
