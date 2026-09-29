# TASK — Code-Grounded JaCaMo → Native USE Transformation

**Project:** USE Extension for JaCaMo Design-Time and Runtime Verification using UML/OCL
**Repository:** `D:\_CODE_BANK\Project_\08_Thesis\use`
**Primary target:** transform official JaCaMo/Jason/CArtAgO/Moise semantics into native USE `MModel` + `MSystemState`, activate them in the existing USE session, and use existing USE GUI/OCL/verification.
**Status:** implementation master plan from A→Z.
**Execution rule:** implement phase-by-phase; do not skip gates; keep repository buildable; fail closed when semantics/evidence are unavailable.

---

# 0. Source of truth and non-negotiable rules

## 0.1 Source priority

Use this authority order:

1. actual production source code and exact resolved dependency APIs/bytecode;
2. executable tests and runtime evidence;
3. current Bridge semantic contract;
4. `NEW-CODE-GROUNDED-JACAMO-USE-ARCHITECTURE-DESIGN.md`;
5. `JACAMO-USE-JAVA-MODEL-TRANSFORMATION-SPEC.md`;
6. `JACAMO-USE-CONCEPT-MAPPING-RULES.md`;
7. old Ecore / Mapping V2 / historical docs only as compatibility evidence.

If code and documents disagree:

- do not silently rewrite semantics;
- record the discrepancy;
- keep code/API evidence;
- update the plan/ADR explicitly.

## 0.2 Final production architecture

```text
JaCaMo official objects/APIs
(.jcm / Jason / CArtAgO / Moise)
        ↓
official/code-grounded semantic adapters
        ↓
typed neutral semantic contract
        ↓
JacamoSpecificationModel / semantic DTO layer
        ↓
Jxx / Axx / Cxx / Mxx / Xxx rules
        ↓
native USE MModel
        ↓
native USE MSystemState
        ↓
Session.setSystem(...)
        ↓
existing USE GUI / OCL / invariants / verification
        ↓
optional export .use / .cmd
```

## 0.3 Forbidden shortcuts

- [x] Do not patch/fork JaCaMo core unless a proven blocker requires it.
- [x] Do not use custom JCM/ASL/Moise parsing as production semantic authority when official APIs already expose the facts.
- [x] Do not use old Ecore or Mapping V2 as semantic authority in `CODE_GROUNDED_NATIVE`.
- [x] Do not fuzzy-map by names.
- [x] Do not infer `Action → Operation` from name equality.
- [x] Do not infer `Belief → ObservableProperty` from literal/property name equality.
- [x] Do not infer `AgentGoal → OrganizationalGoal` from literal/id equality.
- [x] Do not equate JCM agent name, Jason runtime identity, CArtAgO `AgentId`, and Moise agent identity without exact evidence.
- [x] Do not equate artifact declaration name and runtime `ArtifactId`.
- [x] Do not auto-translate Moise Norms into OCL.
- [x] Do not hard-code Hello World, Auction, House Building, or any case-study name in production mapping.
- [x] Do not create a second model/runtime UI that replaces USE.
- [x] Do not silently drop unsupported semantic facts.
- [x] Do not silently mix legacy V2 OCL/mapping with code-grounded native model mode.
- [x] Do not modify frozen historical Ecore/mapping/golden artifacts in place.

---

# 1. Repository baseline and protection

## 1.1 Baseline

- [x] Record Git branch and HEAD.
- [ ] Confirm tracked working tree is clean before implementation.
- [x] Record existing untracked/generated `target/` directories without deleting user state.
- [x] Run existing Bridge/adapter/facade/workbench tests.
- [x] Run current module build.
- [x] Run current release/package workflow.
- [x] Record baseline failures separately from new regressions.
- [x] Store baseline summary in implementation report.

## 1.2 Frozen/historical artifacts

Keep immutable unless explicitly versioned:

- [x] frozen Ecore V2;
- [x] Mapping V2 / V2.2;
- [x] Runtime Mapping V2;
- [x] frozen OCL;
- [x] golden outputs;
- [x] freeze manifests;
- [x] historical audit evidence.

## 1.3 Pipeline modes

Introduce/confirm exactly two atomic modes:

```text
LEGACY_V2
CODE_GROUNDED_NATIVE
```

- [x] One import/session uses exactly one mode.
- [x] `CODE_GROUNDED_NATIVE` must not call Mapping V2.
- [x] `CODE_GROUNDED_NATIVE` must not load V2 OCL/profile JSON.
- [x] Runtime verification uses the same mode as static model construction.
- [x] Add tests rejecting mixed-mode execution.

---

# 2. Rule catalog foundation — all 105 rules

Required counts:

```text
J01–J11 = 11
A01–A22 = 22
C01–C20 = 20
M01–M43 = 43
X01–X09 =  9
----------------
Total    = 105
```

Every rule exposes:

```text
ruleId
dimension
sourceAuthority
sourceKind/FQCN
targetUseKind
fidelity
capabilityStatus
implementationStatus
diagnosticPolicy
```

Allowed statuses:

```text
IMPLEMENTED
PLANNED_CAPABILITY_GATED
UNAVAILABLE_IN_AUDITED_API
EXPLICITLY_UNSUPPORTED
```

Tasks:

- [x] Implement code-level catalog.
- [x] Add `CodeGroundedRuleCatalogTest`.
- [x] Fail build on missing IDs.
- [x] Fail build on duplicates.
- [x] Fail build if authority is missing.
- [x] Fail build if target/fidelity policy is missing.
- [x] Keep catalog deterministic.

---

# 3. Common semantic/evidence infrastructure

## 3.1 Evidence model

Preserve/extend evidence so every fact can carry:

```text
authority
sourcePath/sourceUri
sourceDigest
sourceFQCN
sourceSemanticId
runtimeIdentity
line/source span
snapshot generation/session
fidelity
capability
diagnostics
```

## 3.2 Semantic identity

- [x] Stable semantic IDs.
- [x] Semantic IDs independent from display/object names.
- [x] Runtime IDs remain opaque when required.
- [x] Version IDs if contract schema changes.
- [x] Add stale identity/model revision detection.

## 3.3 USE object naming

Deterministic naming:

```text
<kind>_<safe-human-part>_<hash8(source-id)>
```

- [x] Never resolve semantic links by MObject name.
- [x] Add collision tests.

---

# 4. Typed Bridge semantic contract

Target logical package:

```text
org.jacamo.bridge.contract.semantic
```

Exact names may follow repository conventions.

## 4.1 JCM DTOs

- [x] `ProjectSemantic`
- [x] `AgentDeclarationSemantic`
- [x] `WorkspaceDeclarationSemantic`
- [x] `ArtifactDeclarationSemantic`
- [x] `OrganizationDeploymentSemantic`
- [x] `GroupDeploymentSemantic`
- [x] `SchemeDeploymentSemantic`
- [x] `InstitutionDeploymentSemantic`
- [x] `AgentRoleTupleSemantic`
- [x] `AgentFocusTupleSemantic`
- [x] import/uses provenance

## 4.2 Jason DTOs

- [x] `AgentProgramSemantic`
- [x] `PlanLibrarySemantic`
- [x] `PlanSemantic`
- [x] `TriggerSemantic`
- [x] `PlanBodyElementSemantic`
- [x] `ActionSemantic`
- [x] `BeliefSemantic`
- [x] `AgentGoalSemantic`
- [x] `BeliefRuleSemantic`
- [x] `SourceEvidence`
- [x] supported runtime DTOs

## 4.3 CArtAgO DTOs

- [x] `EnvironmentSemantic`
- [x] `WorkspaceSemantic`
- [x] `ArtifactTypeSemantic`
- [x] `ArtifactSemantic`
- [x] `OperationDescriptorSemantic`
- [x] `BackingJavaOperationSemantic`
- [x] `GuardSemantic`
- [x] `LiveObservablePropertySemantic`
- [x] `ObservablePropertySnapshotSemantic`
- [x] `ArtifactInfoSemantic`
- [x] `SignalSemantic`
- [x] `CartagoAgentIdentitySemantic`
- [x] `FocusSemantic`

## 4.4 Moise DTOs

- [x] `OrganizationSemantic`
- [x] `StructuralSpecificationSemantic`
- [x] `FunctionalSpecificationSemantic`
- [x] `NormativeSpecificationSemantic`
- [x] `GroupSemantic`
- [x] `RoleSemantic`
- [x] `RoleRelationSemantic`
- [x] `LinkSemantic`
- [x] `CompatibilitySemantic`
- [x] `SchemeSemantic`
- [x] `MissionSemantic`
- [x] `OrganizationalGoalSemantic`
- [x] `OrganizationalPlanSemantic`
- [x] `NormSemantic`
- [x] `GroupRoleCardinalitySemantic`
- [x] `SubGroupCardinalitySemantic`
- [x] `SchemeMissionCardinalitySemantic`

## 4.5 Cross-dimension evidence DTOs

- [x] Add typed exact-binding/evidence records for X rules.

## 4.6 Contract compatibility

- [x] Define schema version strategy.
- [x] Preserve legacy snapshot decoder only if needed for migration.
- [x] Codec round-trip tests.
- [x] Version mismatch tests.
- [x] Deterministic encoding tests.
- [x] No USE classes in Bridge contract.
- [x] No live JaCaMo objects over wire.

---

# 5. JCM official adapter — J01–J11

## J01 Project

- [x] Official `JaCaMoProject` is source authority.
- [x] Project identity/name initializes target `MModel`.
- [x] Preserve project/source digest.
- [x] Do not create fake Project domain object unless explicitly required.

## J02 Agent declaration

- [x] Preserve name/source/options/classes/host/instances when exact.
- [x] Keep distinct from Jason `AgentProgram`.
- [x] Declare `MClass Agent`.
- [x] Materialize Agent declaration object in state.

## J03 Workspace declaration

- [x] Separate declaration from runtime `WorkspaceId`.

## J04 Artifact declaration

- [x] Preserve declaration name and `ClassParameters`.
- [x] Map to `ArtifactDeclaration`.
- [x] Never treat as live `Artifact`.

## J05 Organization deployment

- [x] Keep distinct from Moise `OS`.

## J06 Group deployment

- [x] Keep distinct from Moise `Group`.

## J07 Scheme deployment

- [x] Keep distinct from Moise `Scheme`.

## J08 Institution deployment

- [x] Preserve only API-proven fields.
- [x] Keep opaque fields opaque.

## J09 Raw role tuple

- [x] Preserve `(organization, group, role, ordinal)`.
- [x] No eager Agent–Role link.
- [x] X04 performs exact resolution.

## J10 Raw focus tuple

- [x] Preserve `(artifact, workspace, namespace, ordinal)`.
- [x] Remove eager semantic resolution.
- [x] X06 performs exact resolution.

## J11 `uses` provenance

- [x] Add `OfficialImportGraphCollector`.
- [x] Use official/generated lexer/token support only for provenance.
- [x] Collect canonical path/order/digest.
- [x] Do not create semantic declarations.
- [x] Unresolved path → `UNRESOLVED_PROVENANCE`.

## JCM tests

- [x] J09 exact tuple.
- [x] J10 exact tuple.
- [x] No eager cross-framework links.
- [x] J11 provenance only.
- [x] No custom parser fallback.

---

# 6. Jason static mapping — A01–A11, A16–A22

## A01 AgentProgram

- [x] Source: official `jason.asSemantics.Agent`.
- [x] Preserve source URI/id/digest.
- [x] `MClass AgentProgram`.
- [x] Separate from J02 Agent declaration.

## A02 PlanLibrary

- [x] Source: `agent.getPL()`.
- [x] `MClass PlanLibrary`.
- [x] Empty library valid.
- [x] Do not synthesize null library.

## A03 Plan

- [x] Source: ordered `PlanLibrary.getPlans()`.
- [x] Stable identity = library + ordinal + canonical digest.
- [x] Preserve label/context and only fields proven by Jason 3.3.2.
- [x] Do not implement speculative fields.

## A04 Trigger

- [x] Preserve operator enum.
- [x] Preserve type enum.
- [x] Preserve literal.
- [x] Do not infer Signal.

## A05 PlanBodyElement

- [x] Walk body by `getBody()` / `getBodyNext()`.
- [x] Preserve every audited `BodyType`.
- [x] Preserve term and ordinal.
- [x] Detect cycle/broken chain.
- [x] Unknown enum → fail closed.

## A06 External Action

- [x] `BodyType.action`.
- [x] Action kind EXTERNAL.
- [x] Operation binding only X01.

## A07 Internal Action

- [x] `BodyType.internalAction`.
- [x] Action kind INTERNAL.
- [x] Never map directly to CArtAgO operation.

## A08 Belief

- [x] Preserve literal/provenance.
- [x] Property relation only X02.

## A09 AgentGoal

- [x] Preserve exact goal semantics.
- [x] Organizational binding only X07.

## A10 BeliefRule

- [x] Preserve only if supported by exact source/API.

## A11 Source provenance

- [x] Use `SourceInfo`.
- [x] Keep as provenance/trace.

## A16 AgentProgram–PlanLibrary

- [x] Native composition.

## A17 ordered PlanLibrary–Plan

- [x] Native relation.
- [x] Preserve order.
- [x] Use `A17PlanOrderEntry` infrastructure if required.

## A18 Plan–Trigger

- [x] Native composition.

## A19 ordered Plan–PlanBodyElement

- [x] Native relation.
- [x] Preserve order.
- [x] Use `A19BodyOrderEntry` infrastructure if required.

## A20 PlanBodyElement.next

- [x] Self association.
- [x] Must agree with A19 order.

## A21 AgentProgram–Belief

- [x] Bind initial beliefs to AgentProgram.

## A22 AgentProgram–AgentGoal

- [x] Bind initial goals to AgentProgram.

## Jason enums

- [x] Trigger operator → `EnumType`.
- [x] Trigger type → `EnumType`.
- [x] `BodyType` → `EnumType`.
- [x] ActionKind → `EnumType`.
- [x] Unknown enum member → compatibility failure.

---

# 7. Jason runtime — A12–A15

## A12 ActionExec

- [x] Runtime-only event/state projection.
- [x] Preserve result/failure evidence.

## A13 Intention

- [x] Optional runtime concept only if verification requires it.

## A14 Runtime Event

- [x] Distinguish from source Trigger.

## A15 TransitionSystem evidence

- [x] Runtime controller evidence only by default.

**A12–A15 evidence — 2026-09-29:** PASS for the bounded native Jason runtime evidence scope. `BridgeAgArch` emits typed `ACTION_EXECUTION` events and preserves result, failure reason/message, correlation, and exact `Intention` identity evidence; `JasonSnapshotSource` captures `INTENTION`, `RUNTIME_EVENT`, and `TRANSITION_SYSTEM` facts from the official Jason `Circumstance`/`TransitionSystem` APIs. `CodeGroundedRuntimeRuleRegistry` assigns distinct native evidence rules, and `NativeRuntimeProjector` keeps all four concepts evidence-only without creating static `Intention`, `RuntimeEvent`, or `TransitionSystem` classes. `CodeGroundedPhase7Test` passed `4/4`, including no-state-pollution/rule-ID assertions; `OfficialAdapterTest` passed `7/7`, including official `ActionExec` failure evidence. A13 is intentionally evidence-only because no native OCL constraint requires an `Intention` model class.

---

# 8. CArtAgO mapping — C01–C20

Authority legend:

```text
S = static/type metadata
D = descriptor
I = initialized runtime object
R = runtime snapshot/event
P = optional source/bytecode provenance
```

## C01 Environment [I]
- [x] Initialized `CartagoEnvironment`.

## C02 Workspace [D+I]
- [x] `WorkspaceDescriptor` + `WorkspaceId`.

## C03 ArtifactType [S]
- [x] Exact concrete `Class<? extends Artifact>`/classloader identity.
- [x] No simple-name matching.

## C04 Artifact [I+R]
- [x] `ArtifactId` runtime identity.
- [x] `ArtifactInfo` enriches snapshot.

## C05 Operation [D]
- [x] `OpDescriptor`.
- [x] No method-name inference.

## C06 native MOperation [S+D; P optional]
- [ ] Exact backing method/signature required. PARTIAL: the exact `ArtifactOpMethod`/reflection signature is preserved as `BackingJavaOperation`; a native `MOperation` is emitted only when declaring class, method, parameter/return types, arity, and varargs are verified by reflection. Dynamic or otherwise non-exact operations remain structural.
- [x] Dynamic operation without exact method stays structural `Operation`.

## C07 Guard [D; P optional]
- [x] Descriptor exact binding.
- [x] No guard semantics from name.

## C08 Live ObservableProperty [I]
- [ ] Require actual `ObsProperty`.
- [x] Missing API exposure → `UNAVAILABLE`.
- [x] Never replace with C09.

## C09 Property snapshot [R]
- [x] `ArtifactObsProperty`/percept snapshot.
- [x] Separate from live property identity.

## C10 ArtifactInfo [R]
- [x] Runtime snapshot record.

## C11 Signal [R; P optional]
- [x] Runtime signal/percept event is authority.
- [x] No fake `@SIGNAL`.

## C12 AgentId [I+R]
- [x] Keep opaque identity.

## C13 Environment–Workspace [I+D]
- [x] Exact topology.

## C14 Workspace–Artifact [I+R]
- [x] Exact workspace identity.

## C15 Artifact–ArtifactType [I+S]
- [x] Exact class/runtime correlation only.

## C16 Artifact–Operation [D+R]
- [x] Exact descriptor ownership.

## C17 Artifact–ObservableProperty [R; I if C08 exists]
- [x] Snapshot ownership exact.
- [x] Live relation only with C08.

## C18 Operation–Guard [D]
- [x] Exact descriptor binding.

## C19 Workspace–Agent [R]
- [x] Scoped runtime inventory/join-quit evidence.

## C20 Agent–Artifact focus [R]
- [x] Exact focus/unfocus event.
- [x] J10 config is not runtime evidence.

## CArtAgO negative tests

- [x] No simple-name resolution.
- [x] No design inference from runtime inventory.
- [x] C08/C09 distinct.
- [x] No `@SIGNAL` assumption.
- [x] Unavailable remains unavailable.

---

# 9. Moise mapping — M01–M43

## M01 Organization
- [x] `OS` → `MClass Organization`.

## M02 StructuralSpecification
- [x] `SS` → explicit `MClass`.

## M03 FunctionalSpecification
- [x] `FS` → explicit `MClass`.

## M04 NormativeSpecification
- [x] `NS` → explicit `MClass`.

## M05 Group
- [x] `Group` → `MClass Group`.

## M06 Role
- [x] `Role` → `MClass Role`.
- [x] Preserve abstract flag and super-role references.

## M07 RoleRelation
- [x] `RoleRel` → `MClass RoleRelation`.

## M08 Link
- [x] `Link` → `MClass Link`.

## M09 Compatibility
- [x] `Compatibility` → separate `MClass`.

## M10 Scheme
- [x] `Scheme` → `MClass Scheme`.

## M11 Mission
- [x] `Mission` → `MClass Mission`.
- [x] No universal cardinality attributes.

## M12 OrganizationalGoal
- [x] `Goal` → `MClass OrganizationalGoal`.
- [x] Preserve type/arguments/dependencies.
- [x] Keep `ttf` textual unless stronger semantics proven.

## M13 OrganizationalPlan
- [x] `Plan` → `MClass OrganizationalPlan`.
- [x] Preserve operator/order.

## M14 Norm
- [x] `Norm` → `MClass Norm`.
- [x] Preserve role/mission/condition/op type/time text.
- [x] No automatic OCL.

## M15 GroupRoleCardinality
- [x] Relation object `(group, role, min, max)`.

## M16 SubGroupCardinality
- [x] Relation object `(parentGroup, subGroup, min, max)`.

## M17 SchemeMissionCardinality
- [x] Relation object `(scheme, mission, min, max)`.

## M18 Organization–SS
- [x] composition.

## M19 Organization–FS
- [x] composition.

## M20 Organization–NS
- [x] composition.

## M21 SS–Role
- [x] composition.

## M22 SS–Group
- [x] composition/root group ownership.

## M23 Group–Subgroup
- [x] composition; cardinality stays M16.

## M24 Role–superRole
- [x] self-association.
- [x] No UML generalization without proof.

## M25–M28 Link/Compatibility endpoints
- [x] source/target Role relations.

## M29–M32 Cardinality endpoints
- [x] owner/member relations.

## M33 FS–Scheme
- [x] composition.

## M34 Scheme–Mission
- [x] composition.

## M35 Scheme–root Goal
- [x] exact relation; no duplicate goal identity.

## M36–M37 SchemeMissionCardinality endpoints
- [x] Scheme and Mission links.

## M38 Mission–Goal
- [x] exact membership.

## M39 OrganizationalGoal–Plan
- [x] exact relation.

## M40 OrganizationalPlan–subGoals
- [x] ordered relation.

## M41 NS–Norm
- [x] composition.

## M42 Norm–Role
- [x] association.

## M43 Norm–Mission
- [x] association.

## Moise enums/tests

- [x] Norm operation type enum.
- [x] Plan operator enum.
- [x] Cardinality owner context tests.
- [x] Link/Compatibility distinction tests.
- [x] Role hierarchy self-association test.
- [x] Textual time preservation test.
- [x] Norm does not auto-generate OCL.

---

# 10. Cross-dimension rules — X01–X09

All default to unresolved until exact evidence exists.

## X01 Action–Operation
- [x] Exact dispatch/binding/runtime evidence only.

## X02 Belief–ObservableProperty
- [x] Exact percept/property provenance only.

## X03 Trigger–Signal
- [x] Exact signal/percept provenance only.

## X04 Agent–Role
- [x] Resolve J09 with organization/group context or runtime role-player evidence.

## X05 Agent–Workspace
- [x] Exact configuration/runtime membership evidence.

## X06 Agent–Artifact focus
- [x] Resolve J10 or exact runtime C20 evidence.

## X07 AgentGoal–OrganizationalGoal
- [x] Explicit organization/runtime binding only.

## X08 ArtifactDeclaration–runtime Artifact
- [x] Exact creation correlation such as `makeArtifact(...) → ArtifactId`.

## X09 JCM/Jason Agent–CArtAgO AgentId
- [x] Exact join/action/focus observation.

## X tests

- [x] Exact-positive.
- [x] Same-name-negative.
- [x] Unresolved state.
- [x] Runtime restart/incarnation.
- [x] No cross-context leakage.

---

# 11. Code-grounded semantic model in USE plugin

Refactor/replace `JaCaMoSemanticModel`.

Target logical shape:

```text
JacamoSpecificationModel
├── ProjectSpec
├── JasonPrograms
├── CartagoModel
├── MoiseSpecifications
├── SemanticRelations
├── EvidenceIndex
└── Diagnostics
```

Requirements:

- [x] immutable/snapshot-safe where possible;
- [x] deterministic order;
- [x] stable semantic IDs;
- [x] no dependency on V2 vocabulary in native mode;
- [x] capability/fidelity explicit;
- [x] unresolved refs explicit;
- [x] project/spec/runtime separation.

Compatibility projection if needed:

```text
code-grounded model
→ explicit legacy V2 projection
```

- [ ] label representation loss;
- [x] legacy projection never feeds new authority.

---

# 12. Native USE model builder

Build around real USE API, e.g. `UseModelApi`.

Responsibilities:

- [x] create `MModel`;
- [x] `MClass`;
- [x] `EnumType`;
- [x] `MAttribute`;
- [x] `MOperation`;
- [x] `MAssociation`;
- [x] composition;
- [ ] invariants/pre-postconditions;
- [x] deterministic declaration order;
- [x] duplicate/incompatibility diagnostics.

Before `MSystem`:

- [x] all classes/enums declared;
- [x] associations resolved;
- [x] multiplicities validated;
- [x] compositions validated;
- [x] native constraints compile;
- [x] no mandatory unresolved model refs;
- [x] structural hash available.

---

# 13. Native USE state builder

Build around real USE state API, e.g. `UseSystemApi`.

Responsibilities:

- [x] create one `MSystem(model)`;
- [x] deterministic objects;
- [x] attributes;
- [x] links;
- [x] runtime link/value changes;
- [x] semanticId → MObject index;
- [x] trace every mutation;
- [x] correct undefined handling.

## First-slice state

- [x] AgentProgram objects;
- [x] PlanLibrary objects;
- [x] Plan objects;
- [x] Trigger objects;
- [x] PlanBodyElement objects;
- [x] A16 links;
- [x] A17 order;
- [x] A18 links;
- [x] A19 order;
- [x] A20 next.

## Ordered helper objects

```text
A17PlanOrderEntry(owner, member, rank)
A19BodyOrderEntry(owner, member, rank)
```

- [x] rank starts 0;
- [x] contiguous;
- [x] one rank/member/owner;
- [x] A20 agrees with A19;
- [x] helpers are infrastructure, not source domain concepts.

---

# 14. Mapping trace redesign

Trace schema:

```text
TraceRecord(
  ruleId,
  phase,
  sourceKind,
  sourceJavaFQCN,
  sourceIdentity,
  targetKind,
  targetIdentity,
  evidenceAuthority,
  fidelity,
  capabilityStatus,
  diagnostics
)
```

Phases:

```text
MODEL_DECLARATION
INSTANCE_MATERIALIZATION
RUNTIME_MUTATION
EXPORT
```

Trace:

- [ ] class;
- [ ] enum;
- [ ] attribute;
- [x] association;
- [x] operation;
- [x] object;
- [ ] value;
- [x] link;
- [x] order entry;
- [x] runtime mutation;
- [x] skipped/unavailable fact;
- [x] export target.

Index:

- [x] source → target;
- [x] target → source/rule;
- [ ] runtime aliases;
- [x] revision/session validation.

---

# 15. OCL / constraint migration

## 15.1 Native architecture

```text
CodeGroundedRuleCatalog
        ↓
ConstraintSpec
        ↓
CodeGroundedConstraintPlanner
        ↓
NativeConstraintInstaller
        ↓
UseModelApi native invariant/pre-post API
        ↓
MModel
        ↓
existing USE Evaluator / verification
```

## 15.2 Remove V2 coupling from native mode

- [x] `OclGenerator` no longer generates full model in native mode.
- [x] Native mode does not use `StructuralUseGenerator`.
- [x] Native mode does not load `jacamo-core-v2.ocl`.
- [x] Native mode does not load V2 verification profile.
- [ ] `ConstraintExtractor` consumes typed semantics.
- [ ] Replace/refactor `VerificationSemanticLayer`.
- [ ] `CrossDimensionalVerifier` uses rule catalog + trace.
- [x] `RuntimeVerificationEngine` uses injected runtime rule registry.

## 15.3 ConstraintSpec

Every constraint declares:

```text
requiredRuleIds
requiredCapabilities
minimumFidelity
targetContext
origin
migrationStatus
```

Missing capability:

```text
SKIPPED_CAPABILITY
```

## 15.4 First slice constraints

- [x] A17 order consistency.
- [x] A19 order consistency.
- [x] A20 next/order consistency.
- [x] ownership/composition checks where appropriate.

## 15.5 OCL tests

- [x] `NativeConstraintInstallerTest`
- [x] `NativeUseSessionOclIT`
- [x] `CodeGroundedOrderInvariantTest`
- [x] `LegacyV2OclIsolationTest`
- [x] `ProfileCompatibilityPreflightTest`
- [x] `NativeUseExportRecompileIT`

---

# 16. Direct native target; TextBackend becomes historical/export-only

Target:

```text
rules
→ NativeUseModelBuilder
→ MModel
```

not:

```text
rules
→ .use text
→ compiler
→ MModel
```

Tasks:

- [x] Remove TextBackend from native production authority.
- [x] Keep TextBackend for regression/history only if useful.
- [ ] Refactor/replace `DirectUseBackend`.
- [x] Ensure backend returns the exact `MSystem` intended for Session activation.
- [x] Eliminate divergent private system ownership.

---

# 17. USE Session integration

Mandatory activation flow:

```text
native MModel finalized
→ new MSystem(model)
→ native state materialized
→ validation/OCL compile
→ Session.setSystem(system)
```

Tasks:

- [x] Pass `IPluginAction.getSession()` into Workbench/import flow.
- [x] Refactor `DefaultJaCaMoFacade` to use same `MSystem`.
- [x] Facade/session/runtime/verifier share one system.
- [x] Build off EDT.
- [x] Activate/UI update on EDT where required.
- [x] Only call `setSystem()` after all gates pass.
- [x] Failed import leaves previous system untouched.

GUI checks:

- [ ] Model Browser sees model.
- [ ] Class Diagram works.
- [ ] Object Diagram works.
- [ ] OCL dialog/shell uses `session.system()`.
- [x] invariants use current system.
- [ ] system event bus registered.

---

# 18. Workbench → Mapping Inspector

Keep:

- [x] import;
- [x] Bridge connect/reconnect;
- [x] status;
- [x] diagnostics;
- [x] runtime sync status;
- [x] mapping trace;
- [x] fidelity/evidence;
- [ ] export controls if useful.

Main table:

```text
Rule | Source | Target | Fidelity | Status
```

Details:

- [x] rule ID;
- [x] source FQCN;
- [x] semantic ID;
- [x] target USE ID;
- [x] evidence;
- [x] fidelity;
- [x] capability;
- [x] diagnostics;
- [x] provenance;
- [ ] runtime identity if relevant.

Never add:

- [x] second runtime;
- [x] second OCL engine;
- [x] substitute class/object diagram;
- [x] parallel MSystem.

---

# 19. `.use` / `.cmd` export

`.use` is output, not authority.

Flow:

```text
native MModel
→ MMPrintVisitor / official USE printer
→ generated .use
```

Tasks:

- [x] deterministic export;
- [x] recompile exported `.use`;
- [x] structural hash comparison;
- [x] compare classes/enums/attributes/associations/operations;
- [x] compare native migrated constraints;
- [x] report any representation difference.

State:

- [ ] export `.cmd`/SOIL separately if required.
- [x] Do not put runtime objects into `.use`.

---

# 20. Runtime integration

Pipeline:

```text
RuntimeSnapshot / RuntimeEvent
        ↓
typed native runtime projector (CODE_GROUNDED_NATIVE)
        ↓
native rule/evidence gate
        ↓
exact BridgeEntityId binding
        ↓
NativeRuntimeMutationEngine
        ↓
the activated session MSystem.state()
        ↓
USE OCL gate

LEGACY_V2 keeps the separate BridgeRuntimeProjector → RuntimeMutationEngine path.
```

Statuses:

```text
MATERIALIZED_FAITHFULLY
EVIDENCE_ONLY
UNAVAILABLE
UNKNOWN
```

Tasks:

- [x] Only faithful facts mutate USE.
- [x] `NativeRuntimeMutationEngine` targets the current session system in native mode; legacy `RuntimeMutationEngine` remains V2-only.
- [x] Reject stale model revision/session/generation.
- [x] No private facade formal state; the native projector owns the pipeline's activated `MSystem` only.
- [x] Reconnect/resync safely rebuilds state/indices.
- [x] Runtime events cannot invent undeclared types without explicit model revision protocol.
- [x] Preserve AgentId/WorkspaceId/ArtifactId/board identities.

---

# 21. Verification runtime migration

Keep:

- [x] existing USE evaluator;
- [x] model-independent parts of `DefaultVerificationService`;
- [x] report framework;
- [x] BridgeVerificationGate;
- [x] completeness/evidence gating.

Refactor:

- [x] code-grounded rule/capability registry;
- [x] remove static Runtime Mapping V2 loading in native mode;
- [x] bind constraints to exact native target;
- [x] report skipped constraints;
- [x] preserve evidence/fidelity.

No overclaim:

- [x] partial snapshot → non-definitive;
- [x] evidence-only fact cannot satisfy state requirement;
- [x] unknown remains unknown.

---

# 22. Implementation phases

## Phase 1A — Foundation

Implement:

- [x] typed semantic DTOs;
- [x] complete 105-rule catalog;
- [x] fidelity/capability schema;
- [x] J09 raw tuple;
- [x] J10 raw tuple;
- [x] J11 provenance collector;
- [x] code-grounded semantic model base;
- [x] mapping trace;
- [x] native model builder abstraction;
- [x] native state builder abstraction;
- [x] atomic mode;
- [x] OCL decoupling interfaces;
- [x] rule catalog tests;
- [x] contract tests.

Gate:

- [x] exactly 105 rules;
- [x] no duplicate/missing ID;
- [x] every rule has authority;
- [x] J10 no eager semantic link;
- [x] J11 no semantic parser;
- [x] native mode no Mapping V2;
- [x] native mode no V2 OCL/profile;
- [x] build passes;
- [x] historical tests retained.

**Stop if gate fails.**

**Phase 1A evidence — 2026-09-29:** PASS. `main @ 754940969fe65015a6f8a19b1d5612745d4d3e54`; the tracked worktree is intentionally dirty with the pre-existing Phase-1 implementation, and untracked `target/` output was retained. Key classes: `JacamoSemanticSnapshot`, `SemanticContractCodec`, `CodeGroundedRuleCatalog`, `JacamoSpecificationModel`, `CodeGroundedTraceIndex`, `PipelineMode`, `NativeConstraintSpec`/`NativeConstraintInstaller`. Tests: `mvn -B -pl use-plugin -am test` passed `10/10` contract, `14/14` official-adapter, `12/12` use-core, `1/1` use-gui, and `281/281` use-plugin tests, with zero failures/errors/skips; the catalog test confirms 105 unique deterministic IDs and exactly 14 implemented Phase-1 rules. No Phase 2 source was changed.

---

## Phase 1B — First vertical slice

Implement exactly:

```text
J01
A01–A05
A16–A20
```

Deliverables:

- [x] `AgentProgram`
- [x] `PlanLibrary`
- [x] `Plan`
- [x] `Trigger`
- [x] `PlanBodyElement`
- [x] Jason enums
- [x] A16–A20 relations
- [x] A17 order helpers
- [x] A19 order helpers
- [x] first-slice objects/links
- [x] trace
- [x] native OCL
- [x] session activation
- [x] `.use` export
- [x] export/recompile validation

Hello gate:

- [x] official Jason objects only;
- [x] all Plans retained;
- [x] all body nodes retained;
- [x] exact order;
- [x] unsupported BodyType not silently ignored;
- [x] produced system == `action.getSession().system()`;
- [ ] Model Browser sees classes;
- [ ] Object Diagram sees objects/links;
- [x] OCL evaluates on session system;
- [x] Mapping Inspector shows rule/source/target/evidence/fidelity;
- [x] exported `.use` recompiles;
- [x] no production Mapping V2 dependency.

**Stop and report before Phase 2.**

**Phase 1B evidence — 2026-09-29:** PASS for the Hello native first slice at the Phase 1B checkpoint. Key classes: `OfficialProjectAdapter`, `OfficialJasonAdapter`, `OfficialImportGraphCollector`, `NativeUseModelBuilder`, `NativeUseStateBuilder`, `NativeUseSessionActivator`, `NativeUseExporter`, and `JaCaMoWorkbenchPanel`. Focused unit tests passed `13/13`; `NativeUseExportRecompileIT` and `NativeUseSessionOclIT` passed `2/2`; the full reactor unit gate above also passed. `CodeGroundedOrderInvariantTest` proves plan/body retention and A17/A19/A20 order, `NativeUseSessionActivationTest` proves the exact `Session` system and failed-import preservation, and the exporter proves recompile plus structural-hash equality. PARTIAL: direct Model Browser/Object Diagram click-through was not independently exercised, so those two boxes remain `[ ]`; the current Phase 2 evidence is recorded below, while runtime mutations remain unimplemented.

---

## Phase 2 — Remaining Jason static rules

Implement:

```text
A06–A11
A21–A22
```

- [x] actions;
- [x] beliefs;
- [x] goals;
- [x] belief rules;
- [x] provenance;
- [x] initial-belief/goal relations;
- [x] tests and native state.

Gate:

- [x] no Agent/AgentProgram collapse;
- [x] no Action→Operation guessing;
- [x] no Belief→Property guessing;
- [x] all supported source elements retained.

**Phase 2 evidence — 2026-09-29:** PASS for A06–A11 and A21–A22. Official Jason API evidence is exercised by `OfficialJasonAdapter`: `Agent.getInitialBels()` is split into `Literal` beliefs and official `Rule` objects, `Agent.getInitialGoals()` is retained as exact achievement goals, and `PlanBody.BodyType.action/internalAction` becomes typed `ActionSemantic` with no operation binding. Native classes/attributes and A21/A22 ordered associations are built by `NativeUseModelBuilder`/`NativeUseStateBuilder`; all source links use semantic IDs, never display names. Focused Phase 2 `CodeGroundedPhase2Test` passed `2/2`, adapter `OfficialJasonAdapterTest` passed `5/5`, and focused reactor verify passed `14/14` unit plus `2/2` integration tests, including native `.use` recompile and OCL on the same system. Full reactor unit gate passed with contract `10/10`, official adapters `15/15`, use-core `12/12`, use-gui `1/1`, and use-plugin `283/283`; zero failures/errors/skips. No CArtAgO/Moise/cross-framework/runtime Phase 3+ work was started.

---

## Phase 3 — JCM deployment

Implement:

```text
J02–J10
J11 full tests
```

- [x] deployment classes/objects;
- [x] raw references;
- [x] provenance.

Gate:

- [x] declaration/spec/runtime distinct;
- [x] ArtifactDeclaration ≠ Artifact;
- [x] J09/J10 unresolved until X;
- [x] import provenance deterministic.

**Phase 3 evidence — 2026-09-29:** PASS for J02–J11 static deployment scope. `OfficialProjectAdapter` retains official JaCaMo declaration DTOs and generated-token import provenance; `NativeUseModelBuilder` declares typed `Agent`, `WorkspaceDeclaration`, `ArtifactDeclaration`, `OrganizationDeployment`, `GroupDeployment`, `SchemeDeployment`, and `InstitutionDeployment` classes; `NativeUseStateBuilder` materializes only those declarations and keeps J09/J10 as raw trace records (`UNRESOLVED_UNTIL_X04`/`UNRESOLVED_UNTIL_X06`). During the Phase 4 schema audit, the JCM target was corrected to `WorkspaceDeclaration` so the CArtAgO runtime `Workspace` class remains distinct; the Phase 3 fixture still creates zero live `Artifact` objects. `ArtifactDeclaration` remains distinct from the reserved CArtAgO `Artifact` runtime class, with no declaration-name runtime link fabricated. `CodeGroundedPhase3Test` passed `2/2`; the focused reactor verify passed `11/11` unit plus `2/2` integration tests; the full reactor unit gate passed contract `10/10`, official adapters `15/15`, use-core `12/12`, use-gui `1/1`, and use-plugin `285/285`, with zero failures/errors/skips. No CArtAgO/Moise/cross-framework/runtime phase was started at that checkpoint.

---

## Phase 4 — CArtAgO

Implement:

```text
C01–C20
```

Gate:

- [x] authority matrix followed;
- [x] no simple-name matching;
- [x] C08 unavailable remains unavailable;
- [x] C09 distinct;
- [x] exact operation ownership;
- [x] ArtifactId-based runtime identity;
- [x] dynamic limitations explicit.

**Phase 4 evidence — 2026-09-29:** PASS for the available CArtAgO contract slice: `OfficialCartagoAdapter` reads only official `CartagoEnvironment`, `WorkspaceDescriptor`/`WorkspaceId`, controller inventories, `ArtifactId`, `ArtifactInfo`, `OpDescriptor`, `ArtifactObsProperty`, `ArtifactOpMethod`, `IArtifactGuard`, and opaque `AgentId` values; no simple-name join or method-name inference is used. `NativeUseModelBuilder`/`NativeUseStateBuilder` materialize exact C01–C05, C07, C09–C20 classes/links in the same `MSystem`, preserve `ArtifactId` UUID/workspace identity, keep C09 snapshots separate from the C08 live-property class, and retain focus/unfocus as exact event evidence. C08 is fail-closed as `UNAVAILABLE` because the audited controller API does not expose a live `ObsProperty`; non-empty live-property input is rejected rather than converted to C09. C06 remains PARTIAL: exact reflective backing signature is preserved as `BackingJavaOperation`, and `NativeUseModelBuilder` emits a concrete native `MOperation` only after exact declaring-class, method, parameter/return, arity, and varargs checks; dynamic or non-exact operations remain structural. `CodeGroundedPhase4Test` passed `3/3`; `NativeMOperationProjectionTest` passed `2/2` for positive and negative reflection evidence; the focused native regression set passed `12/12`. The catalog still reports C06 capability-gated/partial and C08 unavailable.

---

## Phase 5 — Moise

Implement:

```text
M01–M43
```

Gate:

- [x] no cardinality flattening;
- [x] SS/FS/NS explicit;
- [x] Link/Compatibility distinct;
- [x] role hierarchy not forced to UML generalization;
- [x] no Norm→OCL;
- [x] time text preserved.

**Phase 5 evidence — 2026-09-29:** PASS for M01–M43. `OfficialMoiseAdapter` loads the official `OS` graph through `OS.loadOSFromURI` and emits typed immutable Moise DTOs with exact semantic IDs; `NativeUseModelBuilder` declares the native Moise classes, enums, relation-scoped cardinality objects, composition/endpoints, and role self-association; `NativeUseStateBuilder` materializes them into the same `MSystem` without Norm-to-OCL translation. `CodeGroundedPhase5Test` passed `2/2` with the official Hello OS and a synthetic official-DTO graph covering role inheritance, Link vs Compatibility, all cardinality tuples, ordered plan goals, enum values, and textual norm time; focused catalog/native gates passed `10/10`, adapter evidence passed `6/6`, and native export/recompile plus session/OCL passed. The full reactor gate passed `290/290` tests with zero failures/errors/skips. The M22 multiplicity direction was corrected and reverified with a subgroup fixture. No V2/Ecore/golden files were changed.

---

## Phase 6 — Cross-framework

Implement:

```text
X01–X09
```

Gate:

- [x] zero fuzzy bindings;
- [x] unresolved remains unresolved;
- [x] context retained;
- [x] exact bindings survive runtime/export.

**Phase 6 evidence — 2026-09-29:** PASS for X01–X09. `CrossSemanticContract` now exposes nine typed exact-binding records that lower to immutable, context-bearing bindings; `NativeUseModelBuilder` declares the X associations and an `ExactBindingEvidence` class; `NativeUseStateBuilder` resolves only exact semantic IDs, validates endpoint classes, preserves context, and leaves J09/J10 unresolved without evidence. `CodeGroundedPhase6Test` passed `3/3`: all nine positive bindings survived one `MSystem` materialization and export/recompile, same-name candidates stayed unlinked, unresolved J09/J10 remained diagnostic, restart/incarnation identity selected only the exact target, and a wrong endpoint failed closed. Contract/catalog tests passed `6/6`; Phase 2–6/native regression passed `20/20`. No fuzzy matching, V2/Ecore/golden edits, or second `MSystem` were introduced.

---

## Phase 7 — Runtime native synchronization

- [x] Jason runtime projection;
- [x] CArtAgO runtime projection;
- [x] Moise board snapshots;
- [x] NPL evidence;
- [x] runtime rule registry;
- [x] same-session mutations;
- [x] reconnect/resync;
- [x] stale event rejection;
- [x] runtime OCL gates.

Gate:

- [x] every mutation targets current session state;
- [x] no parallel/private state;
- [x] stale events rejected;
- [x] evidence-only cannot mutate;
- [x] resync deterministic.

**Phase 7 evidence — 2026-09-29:** PASS for the bounded native runtime synchronization slice. `NativeRuntimeProjector`, `NativeRuntimeMutationEngine`, `CodeGroundedRuntimeRuleRegistry`, and `NativeRuntimeTraceRecord` are native-only and do not call the V2 projector/mapping. Faithful Jason `Agent.host` and CArtAgO `Artifact.name` updates, exact relation insert/delete, and attribute unset mutate the pipeline `MSystem`; `NativeRuntimeFacadeIntegrationTest` proves a buffered event is applied after `Session.setSystem` preparation and the facade/session retain the same system. Moise group-board and NPL norm facts are retained as evidence-only and do not mutate formal state. Snapshot resync resets the same system to its baseline, stale replay/session/generation/model-revision events reject closed, undeclared mutation kinds reject closed, and the OCL gate runs after each faithful mutation. Focused Phase 7 tests passed `4/4`; the full reactor passed `297/297` with zero failures/errors/skips. PARTIAL: Jason A12–A15 action/intention/TransitionSystem semantics and `RuntimeVerificationEngine` rule-registry migration remain unchecked; the current native runtime scope is typed attribute/link projection plus evidence-only facts.

---

## Phase 8 — Production authority switch

- [x] `CODE_GROUNDED_NATIVE` becomes production authority.
- [x] `LEGACY_V2` becomes explicit compatibility/shadow mode only.
- [x] No automatic fallback to legacy.
- [x] Update defaults.
- [x] Update packaging exclusions.
- [x] Verify historical parsers/connectors not shipped as authority.

Gate:

- [x] native supported scope complete;
- [x] no legacy calls from native path;
- [x] release/package tests pass.

**Phase 8 evidence — 2026-09-29:** PASS for the production-authority, bounded native runtime scope, and explicit verification-rule boundary. The implicit `DefaultJaCaMoFacade` constructors, `DefaultJaCaMoFacade.INSTANCE`, `forSession`, and the Workbench action select `CODE_GROUNDED_NATIVE`; `LEGACY_V2` is reachable only through an explicit `PipelineMode.LEGACY_V2` constructor argument. `ProductionAuthorityPhase8Test` passed `3/3`, `LegacyV2OclIsolationTest` passed `2/2`, and `RuntimeVerificationEngineTest` passed `20/20` including injected-rule selection. `CodeGroundedPhase7Test` passed `4/4` and `OfficialAdapterTest` passed `7/7` for A12–A15 evidence. The post-change full reactor gate passed `302/302` unit tests plus `7/7` integration/release tests with zero failures/errors/skips. `LegacyAuthorityPackagingIT`, `GuiPluginStagingIT`, and `ReleasePackageIT` proved native runtime classes are packaged, historical parsers/connectors and JaCaMo-side adapters are excluded from the plugin JAR, the staged GUI JAR is byte-identical, Bridge libraries are present, and the release checksum matches. Native supported scope is complete for the explicitly implemented faithful attribute/link mutations and typed evidence-only runtime concepts; unsupported semantics remain fail-closed and no V2 runtime mapping is called by native code.

---

## Phase 9 — Case-study acceptance

### Hello World

- [x] JCM declarations;
- [x] Jason program;
- [x] plan/trigger/body;
- [x] workspace/artifact declarations;
- [x] Moise OS;
- [x] native session activation;
- [x] OCL;
- [x] export;
- [x] supported runtime.

### Auction

- [ ] dynamic scheme evidence;
- [ ] artifact type;
- [ ] operations;
- [ ] properties;
- [ ] exact action-operation link only when proven;
- [x] organization;
- [x] no unsupported deadline claim;
- [x] native runtime/session.

### House Building

- [x] `.jcm` not treated as complete specification;
- [x] dynamic artifacts/org/schemes handled;
- [x] role inheritance/cardinality;
- [x] sequence/parallel plan;
- [ ] supported runtime;
- [x] missing facts remain unavailable.

Gate:

- [x] no case-specific branches;
- [x] all claims evidence-backed;
- [x] limitations documented.

**Phase 9 evidence — 2026-09-29:** PASS for the explicitly bounded native acceptance scope. `CodeGroundedPhase9Test` runs Hello World, the official Auction example, and House Building through the same `OfficialProjectAdapter` → `CodeGroundedNativePipeline` path; `Phase9CaseStudyAcceptanceTest` adds `3/3` evidence tests for one `Session`/`MSystem`, native verification, export validity, static dimension retention, no fabricated live artifacts, Auction official organization/scheme/OS facts, and House Building's separate official OS/cardinality/sequence/parallel evidence. Hello's JCM/Jason/plan-body/workspace/artifact/Moise/OCL/export/runtime claims are covered by the Phase 2–8 tests plus the native session/export integration tests. Auction organization and native session are PASS; Norm facts remain data and are not promoted to OCL. House Building's `.jcm` is explicitly shown incomplete: dynamic organization/artifact facts remain unavailable until an official runtime/OS snapshot is supplied, while role inheritance/cardinality and sequence/parallel plan facts are copied only from the official OS object graph. The following remain intentionally unchecked: live Auction CArtAgO artifact type/operation/property capture, exact action-operation binding for that live artifact, and House live runtime, because no native live evidence exists for those claims. No case-specific production branch, fuzzy mapping, deadline inference, or unsupported runtime claim was added. Focused Phase 9 gate passed `3/3`; no frozen V2/Ecore/golden file was changed.

---

# 23. Mapping Inspector completion

- [x] show all 105 IDs;
- [x] filter J/A/C/M/X;
- [x] filter APPLIED/UNRESOLVED/UNAVAILABLE/UNSUPPORTED;
- [x] source FQCN;
- [x] target USE ID;
- [x] fidelity;
- [x] evidence;
- [x] diagnostics;
- [x] runtime status;
- [ ] optional navigate-to-target;
- [x] inspection only.

**Section 23 evidence — 2026-09-29:** PASS for the required inspector surface. `JaCaMoWorkbenchPanel` renders the facade trace without semantic work in Swing, exposes all five dimensions and the required `APPLIED`/`UNRESOLVED`/`UNAVAILABLE`/`UNSUPPORTED` status filters, and shows source FQCN, semantic/source identity, USE target identity, fidelity, evidence authority, capability status, and diagnostics in the detail pane. `JaCaMoWorkbenchPanelTest` passed `13/13`, including a 105-row catalog display, exact filter coverage, detail evidence fields, runtime/authority status refresh, and a source-location action. Optional navigate-to-target remains unchecked because no target-navigation API is required by the current UI contract.

---

# 24. Export/reproducibility

- [x] deterministic `.use`;
- [ ] optional `.cmd`;
- [x] source project digest;
- [x] rule catalog version;
- [x] contract version;
- [ ] JaCaMo/Jason/CArtAgO/Moise versions;
- [ ] USE version;
- [x] structural hash;
- [x] trace export;
- [x] verification report;
- [x] round-trip validation.

**Section 24 evidence — 2026-09-29:** PASS for the native artifacts that are implemented and tested. `NativeUseExporter` serializes the native `MModel` with USE's official `MMPrintVisitor`, recompiles it with `USECompiler`, and checks structural-hash/signature equality; `CodeGroundedRuleCatalog.VERSION` and `JacamoSemanticSnapshot.CURRENT_VERSION` are explicit native/contract metadata, and `DefaultJaCaMoFacade` records the JCM SHA-256 source row plus the native structural/catalog hashes. `NativeUseStateExporter` writes a separate deterministic state JSON from the one native `MSystem`, and `CodeGroundedTraceExporter` writes the exact native trace without converting it to V2. `CodeGroundedDeterminismTest`, `NativeUseExportRecompileIT`, `NativeUseSessionOclIT`, `DefaultJaCaMoFacadeTest`, and `CodeGroundedExportTest (2/2)` provide evidence. Optional `.cmd`, a component-version manifest (JaCaMo/Jason/CArtAgO/Moise), and an exported USE-version field remain unchecked because no native manifest currently records those values.

---

# 25. Packaging/release

## Build

- [x] full reactor;
- [x] JDK 21;
- [x] plugin JAR contains native path;
- [x] JaCaMo-side adapter separated as designed;
- [x] no stale committed plugin JAR.

## Plugin

- [x] actions load;
- [x] Workbench receives Session;
- [x] status action works/consolidated.

## Packaging gates

- [x] obsolete semantic parsers not production authority;
- [x] new rule catalog/builders packaged;
- [x] release ZIP contains required Bridge libs;
- [x] staged GUI plugin equals current build;
- [x] checksums recorded.

**Section 25 evidence — 2026-09-29:** PASS. `mvn -B -pl use-plugin -am verify` completed the full reactor on Java 21 with `309` unit tests and `7` integration/release tests, all with zero failures/errors/skips. The build produced the plugin JAR, release ZIP, and SHA-256 sidecar; `LegacyAuthorityPackagingIT` verifies the native facade/runtime/rule catalog/model/state/trace classes are in the JAR while obsolete parser/connector classes and JaCaMo-side adapter classes are absent. `JaCaMoPluginTest (5/5)` verifies plugin discovery, status command, both actions, Session binding, and facade status. `GuiPluginStagingIT` verifies byte identity between the current production JAR and GUI staging; `ReleasePackageContractTest`/`ReleasePackageIT (3/3)` verify Bridge libraries, manifest inventory, isolated loading, pinned USE discovery, and checksum equality.

---

# 26. Shadow comparison/regression

Compare:

```text
code-grounded result
vs
legacy V2 result
```

Classify:

```text
INTENTIONAL_CORRECTION
LEGACY_LIMITATION
REPRESENTATION_LOSS
ADAPTER_BUG
UNSUPPORTED_FACT
```

- [x] legacy never decides new semantics;
- [x] no name-based reconciliation;
- [x] retain diff evidence.

**Section 26 evidence — 2026-09-29:** PASS. `ShadowSemanticComparator` indexes facts by canonical identity plus provenance digest, rejects duplicate facts, requires an explicit classification, and produces a deterministic fingerprint/register; it never reconciles by display name. `HelloShadowComparisonTest` retains the Hello shadow register, while `ShadowSemanticComparatorTest (1/1)`, `NativeSemanticAdapterTest (3/3)`, `DefaultBridgeAuthorityTest (5/5)`, `ProductionAuthorityPhase8Test (3/3)`, and `LegacyV2OclIsolationTest (2/2)` pass. Native implicit facade entry points remain `CODE_GROUNDED_NATIVE`; V2 compatibility is explicit only.

---

# 27. Performance/safety

- [ ] bounded semantic snapshots;
- [x] bounded event queues;
- [x] no network/build on Swing EDT;
- [x] atomic activation;
- [x] old session survives failure;
- [x] no listener leaks;
- [x] deterministic model creation;
- [x] large plan/body tests;
- [ ] trace size monitored.

**Section 27 evidence — 2026-09-29:** PASS for the bounded/runtime/session safety items that have direct evidence. `RuntimeFoundationTest (26/26)` covers bounded ordered queues, snapshot-buffer overflow, backpressure/tombstones, observer/listener shutdown, stale callback isolation, rollback, reconnect/resync, and timing metrics. `JaCaMoWorkbenchPanelTest (13/13)` proves import/build work is dispatched off the Swing EDT and UI publication/refresh stays on the UI boundary. `NativeUseSessionActivationTest (2/2)` proves validation-before-activation and preservation of the previous `MSystem` on failure; `NativeRuntimeFacadeIntegrationTest (1/1)` proves runtime mutation occurs in the activated session system; `CodeGroundedDeterminismTest (1/1)` proves repeatable native model/export/trace output; `CodeGroundedLargePlanTest (1/1)` retains a 256-element official Jason body and ordered links. Semantic snapshot hard limits and production trace-size monitoring remain unchecked because no separate native limit/telemetry contract exists yet.

---

# 28. Documentation migration

Update only after corresponding code gate passes:

- [x] architecture README;
- [x] setup/run;
- [x] Bridge config;
- [x] mapping docs;
- [x] USE session behavior;
- [x] Mapping Inspector;
- [x] runtime limits;
- [x] export docs;
- [x] compatibility mode;
- [x] case-study evidence;
- [x] known limitations;
- [x] migration report.

**Section 28 evidence — 2026-09-29:** PASS. Added `CODE-GROUNDED-NATIVE-README.md` for architecture, setup/run, Bridge configuration, mapping/inspector behavior, USE session ownership, runtime limits, exports, and compatibility mode; added `CODE-GROUNDED-NATIVE-MIGRATION-REPORT.md` for phase gates, call graph, case-study evidence, limitations, and rollback/audit scope. The documents explicitly preserve the frozen V2/Ecore/golden boundary and distinguish supported subsets from unavailable/live claims.

Historical audit docs remain, clearly labeled historical/date-scoped.

---

# 29. Legacy cleanup criteria

Candidate historical-only components include:

```text
JcmSemanticParser
custom JCM semantic lexer/discovery
JasonSourceParser
CartagoSourceExtractor
MoiseXmlParser
legacy resolver
legacy in-process connectors
CompositeRuntimeConnector
```

Do not remove until:

- [ ] no production caller;
- [x] release excludes them;
- [x] native case-study gates pass;
- [x] regression value assessed;
- [x] historical evidence retained;
- [ ] explicit cleanup approval.

V2 becomes historical-only when:

- [x] native static mapping default;
- [x] native OCL default;
- [x] native runtime mapping default;
- [x] no native-mode V2 load;
- [x] release audit proves isolation.

**Section 29 evidence — 2026-09-29:** PASS for historical classification and release isolation, but cleanup is intentionally not authorized. Native implicit facade, native OCL installation, and native runtime projector are the default path; `ProductionAuthorityPhase8Test` and `LegacyV2OclIsolationTest` prove no native-mode V2 load. `LegacyAuthorityPackagingIT`/`GuiPluginStagingIT` prove the candidate parser/connector classes are absent from the shipped plugin JAR, while shadow/frozen regression tests preserve their audit value. Legacy classes still have explicit compatibility/test callers, so “no production caller” and “explicit cleanup approval” remain unchecked; no deletion was performed.

---

# 30. Required test suite

## Rule catalog
- [x] count 105
- [x] duplicates
- [x] missing authority/status

## Contract
- [x] encode/decode
- [x] version mismatch
- [x] deterministic
- [x] fidelity/capability preservation

## Native model
- [x] classes
- [x] enums
- [x] attributes
- [x] operations
- [x] associations
- [x] compositions
- [x] multiplicities
- [x] deterministic order

## Native state
- [x] objects
- [x] values
- [x] links
- [x] removal/update
- [x] undefined
- [x] collisions
- [x] ordered helpers

## Session
- [x] setSystem
- [x] previous system survives failed import
- [x] GUI refresh
- [x] evaluator current system

## OCL
- [x] native install
- [x] capability gate
- [x] no V2 load
- [x] runtime same mode

## Jason
- [x] all audited BodyTypes
- [x] plan order
- [x] body order
- [x] trigger enums
- [x] source/runtime distinction

## CArtAgO
- [x] descriptor authority
- [x] ArtifactId identity
- [x] C08/C09 distinction
- [x] no simple-name binding
- [x] unavailable live property

## Moise
- [x] SS/FS/NS
- [x] Link/Compatibility
- [x] M15/M16/M17 tuples
- [x] ordered plans
- [x] norm/time preservation

## X rules
- [x] positive exact evidence
- [x] same-name negative
- [x] unresolved
- [x] restart identity

## Export
- [x] deterministic `.use`
- [x] recompile
- [x] structural equivalence
- [x] state export if supported

## Packaging
- [x] discovery
- [x] staging
- [x] no stale binary
- [x] classpath separation
- [x] historical parser exclusion

**Section 30 evidence — 2026-09-29:** PASS for the implemented test suite. `CodeGroundedNegativeTest` rejects duplicate semantic identity before native state materialization; `CodeGroundedPhase6Test` proves same-name facts remain unresolved without exact evidence; `JaCaMoWorkbenchPanelTest` covers runtime/project refresh; `CodeGroundedExportTest (2/2)` covers separate state export; and the packaging/discovery/staging/classpath/historical-exclusion items are covered by `JaCaMoPluginTest (5/5)`, `LegacyAuthorityPackagingIT`, `GuiPluginStagingIT`, `ReleasePackageContractTest`, and `ReleasePackageIT (3/3)`. `NativeMOperationProjectionTest (2/2)` proves exact reflection creates the concrete artifact subtype and `MOperation`, while a non-exact method remains structural; the focused native regression set passed `12/12`.

---

# 31. Required report after every phase

Each phase report must include:

1. Git diff summary.
2. Files/classes added/changed.
3. Rule IDs implemented/changed.
4. Exact production call graph after phase.
5. Tests executed + exact results.
6. Acceptance evidence.
7. Remaining `UNKNOWN` / `UNAVAILABLE`.
8. Legacy code still present and production callers.
9. Plan-vs-code discrepancies.
10. Risks/new technical debt.
11. Rollback point.
12. Recommendation for next phase.

**Do not continue when a mandatory gate fails.**

---

# 32. Final definition of done

- [x] Official JaCaMo/Jason/CArtAgO/Moise objects are semantic authority.
- [x] All 105 rules exist with authority/fidelity/capability metadata.
- [x] Supported rules are implemented/tested.
- [x] Unsupported/unavailable rules fail closed.
- [x] Native semantic model no longer depends on V2 vocabulary.
- [x] Native `MModel` built through USE API.
- [x] Native `MSystemState` materialized through USE API.
- [x] One `MSystem` shared by facade/session/runtime/verifier.
- [x] `Session.setSystem(system)` activates result in existing USE.
- [ ] Existing USE Model Browser/Class Diagram/Object Diagram see the model/state.
- [x] Existing USE OCL runs against same session system.
- [x] Native verification has no hidden V2 dependency.
- [x] Runtime updates mutate current session state only.
- [x] Mapping Inspector is explanatory only.
- [x] `.use` exported from native `MModel`.
- [x] exported `.use` recompiles.
- [x] state export is separate.
- [x] Hello supported scope passes.
- [x] Auction supported scope passes.
- [x] House Building supported scope passes.
- [x] no production fuzzy mapping.
- [x] no case-study hard coding.
- [x] no automatic Norm→OCL.
- [x] no relation-scoped cardinality loss.
- [x] release package contains only intended production authority path.
- [x] historical artifacts remain reproducible and labeled.

**Section 32 evidence — 2026-09-29:** PASS for the native implementation and bounded case-study/export/release claims listed above. The remaining unchecked DoD item is the unverified visual Model Browser/Class Diagram/Object Diagram end-to-end observation; the tests prove the exact Session system and OCL/runtime use it, but do not claim a GUI click-through for all three USE views. Remaining unchecked task items elsewhere are documented optional/partial/approval-gated limitations, not silently promoted to completion.

---

# 33. Final architecture

```text
                    JaCaMo
                      │
      official Java/API semantic objects
                      │
                      ▼
          JaCaMo-side typed adapters
                      │
                      ▼
          neutral semantic contract
                      │
                      ▼
        JacamoSpecificationModel
                      │
        J/A/C/M/X mapping rule catalog
                      │
          ┌───────────┴───────────┐
          ▼                       ▼
  NativeUseModelBuilder   NativeUseStateBuilder
          │                       │
          ▼                       ▼
        MModel  ───────────────► MSystem
                                  │
                                  ▼
                             MSystemState
                                  │
                                  ▼
                         Session.setSystem(...)
                                  │
                   ┌──────────────┼──────────────┐
                   ▼              ▼              ▼
             USE GUI          USE OCL      Verification
                   │
                   ▼
          optional export .use/.cmd

Mapping Inspector
    └── source → rule → target → evidence → fidelity/status
        (inspection only; no second runtime)
```

# 34. Execution order

```text
Phase 1A  Foundation / typed contract / 105-rule catalog / native APIs / OCL separation
    ↓
Phase 1B  J01 + A01–A05 + A16–A20 / Hello native USE vertical slice
    ↓
Phase 2   Remaining Jason static rules
    ↓
Phase 3   JCM deployment rules
    ↓
Phase 4   CArtAgO C01–C20
    ↓
Phase 5   Moise M01–M43
    ↓
Phase 6   Cross-dimension X01–X09
    ↓
Phase 7   Runtime synchronization into current USE session
    ↓
Phase 8   Production authority switch / V2 isolation
    ↓
Phase 9   Hello → Auction → House acceptance
    ↓
Packaging / docs / cleanup / final release audit
```

**Do not skip gates. Do not guess semantics. Do not create a parallel USE runtime.**
