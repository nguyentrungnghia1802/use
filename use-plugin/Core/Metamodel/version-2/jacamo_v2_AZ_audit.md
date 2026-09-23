# JaCaMo metamodel A-Z audit

## 1. Scope and method

Source under review: `jacamo_v2_attribute_only_researched.ecore`.
Baseline for structural comparison: original `jacamo_v2.ecore`.

Primary external references used for semantic verification:

1. JaCaMo source repository: https://github.com/jacamo-lang/jacamo
2. JaCaMo JCM reference: https://jacamo-lang.github.io/jacamo/jcm.html
3. JaCaMo official Hello World/tutorial: https://jacamo-lang.github.io/jacamo/tutorials/hello-world/readme.html
4. MOISE tutorial: https://moise-lang.github.io/doc/tutorial.pdf
5. Jason AgentSpeak syntax: https://jason-lang.github.io/jason/Jason.pdf
6. Boissier, Bordini, Hübner, Ricci (2020), *Multi-Agent Oriented Programming: Programming Multi-Agent Systems Using JaCaMo*, MIT Press.

The audit distinguishes three levels that are easy to mix accidentally:
- language/specification concepts (Jason, CArtAgO, MOISE),
- JaCaMo application/deployment concepts expressed in JCM,
- runtime relations such as an agent joining a workspace or focusing an artifact.

## 2. Structural delta from the original Ecore

Original `jacamo_v2.ecore`:
- 19 EClasses
- 29 EReferences
- 0 EAttributes

New file before cleanup:
- 22 EClasses
- 33 EReferences
- 35 EAttributes

After cleanup:
- 21 EClasses
- 33 EReferences
- 35 EAttributes

Classes added relative to the original:
- `OPlan` — semantically justified by MOISE global/goal plans.
- `Link` — semantically justified by MOISE inter-role links.
- `NewEClass21` — empty accidental class; removed.

## 3. Class audit

### 3.1 Agent dimension

- `Agent`: valid.
- `Plan`: valid.
- `Event`: valid as a Jason triggering-event abstraction.
- `Action`: valid, but the current single class loses the explicit internal/external action distinction used by JaCaMo/Jason.
- `Belief`: valid.
- `AGoal`: valid for an individual Jason agent goal.

### 3.2 Environment dimension

- `Environment`: valid as a high-level container, although JCM directly declares workspaces rather than requiring a named environment object.
- `Workspace`: valid.
- `Artifact`: valid.
- `Operation`: valid.
- `Property`: valid for observable property.
- `Signal`: valid for artifact-generated observable signals/events.

### 3.3 Organisation dimension

- `Organization`: valid as a high-level organisational specification/container.
- `Group`: valid.
- `Role`: valid.
- `Norm`: valid.
- `Scheme`: valid.
- `Mission`: valid.
- `OGoal`: valid.
- `OPlan`: valid and important; MOISE goal decomposition uses plans with operators such as sequence and parallel.
- `Link`: valid and important; MOISE structural specifications define links between roles.

## 4. Relationship audit

### Correct or broadly defensible

- `Organization -> Group[*]` containment.
- `Organization -> Scheme[*]` containment.
- `Organization -> Norm[*]` containment.
- `Group -> Group[*]` containment for subgroups.
- `Agent -> Plan[*]` containment.
- `Agent -> Belief[*]` containment.
- `Agent -> AGoal[*]` containment.
- `Environment -> Workspace[*]` containment.
- `Workspace -> Artifact[*]` containment.
- `Artifact -> Operation[*]` containment.
- `Artifact -> Property[*]` containment.
- `Action -> Operation[0..1]` is a reasonable JaCaMo conceptual mapping for external actions, but a generic `Action` can also be internal and therefore should not require a mapped operation.
- `Belief -> Property[0..1]` is a reasonable conceptual mapping for beliefs arising from observable properties.
- `Event -> Signal[0..1]` is a reasonable conceptual mapping for externally generated events, but not all Jason events come from artifact signals.

### Relationships that should be changed

#### R1. Agent <-> Role
Current:
- `Agent.role [0..*]`
- `Role.agent [0..1]`

Problem: a role in an organisation/group can be played by multiple agents subject to cardinality constraints. The inverse end must not be `0..1`.

Recommended conceptual multiplicities:
- `Agent.roles [0..*]`
- `Role.agents [0..*]`

A more faithful MOISE metamodel would introduce role participation/cardinality at the group-role relation instead of treating a role as simply contained by one group.

#### R2. Agent <-> Workspace
Current: one workspace per agent and one agent per workspace.

Problem: JaCaMo JCM explicitly allows repeated `join`, and a workspace can contain multiple agents.

Recommended:
- `Agent.workspaces [0..*]`
- `Workspace.agents [0..*]`

#### R3. Agent <-> Artifact
Current: one artifact per agent and one agent per artifact.

Problem: agents may focus multiple artifacts; several agents may focus the same artifact.

Recommended:
- `Agent.artifacts [0..*]`
- `Artifact.agents [0..*]`

Semantically, naming the relation `focusedArtifacts` / `focusingAgents` would be clearer than generic `artifact` / `agent`.

#### R4. Plan -> Event
Current: `Plan.events [0..*]`.

Problem: AgentSpeak/Jason defines a plan head with one triggering event `te`.

Recommended:
- `Plan.triggeringEvent [1]` containment.

#### R5. Plan -> Action
Current: `Plan.actions [0..*]`.

This is only a simplification. A Jason plan body can contain actions, achievement/test goals and belief updates, not only actions. If the metamodel aims at source-level fidelity, introduce a plan-body-element hierarchy or equivalent ordered structure. If it is intentionally conceptual, keep it but document the simplification.

#### R6. Norm -> Role / Mission
Current:
- `Norm.roles [0..*]`
- `Norm.missions [0..*]`

Problem: MOISE normative syntax binds a norm to one `role` and one `mission`.

Recommended:
- `Norm.role [1]`
- `Norm.mission [1]`

#### R7. Scheme / Mission / OGoal / OPlan
Current:
- `Scheme -> Mission[1..*]` containment
- `Mission -> OGoal[1..*]` containment
- `OGoal -> OPlan[0..*]` containment
- `OPlan -> OGoal[1..*]` reference

Problem: this reverses important parts of MOISE functional structure. In MOISE, the scheme has a goal-decomposition tree; missions collect/reference goals. A goal may have a plan that decomposes it into subgoals. Missions do not own the goals.

Recommended high-fidelity shape:
- `Scheme.rootGoal [1]` containment (or `Scheme.goals [1..*]` containment in a flat variant)
- `Scheme.missions [0..*]` containment
- `Mission.goals [1..*]` non-containment reference
- `OGoal.plan [0..1]` containment
- `OPlan.subGoals [1..*]` containment for a tree representation (or non-containment if all goals are flat-contained by Scheme)
- `OPlan.operator [1]` attribute

#### R8. Link
Current:
- `Role.link [0..1]`
- `Link.role [0..1]`

Problem: a MOISE link connects a source role to a target role and belongs to a group specification; the current binary shape cannot represent direction correctly and one role may participate in multiple links.

Recommended:
- `Group.links [0..*]` containment
- `Link.sourceRole [1]`
- `Link.targetRole [1]`
- remove generic `Role.link` or make derived/inverse collections if needed
- attributes: `type`, `scope`, `extendsSubgroups`, optionally `bidirectional`.

#### R9. Operation -> Property / Signal
Current:
- `Operation.property [1]`
- `Operation.signal [0..1]`

Problem: an operation can update zero, one, or multiple observable properties, and can emit zero or multiple signals/events.

Recommended:
- `Operation.properties [0..*]`
- `Operation.signals [0..*]`

Depending on intended semantics, these may be dependency/effect relations rather than ownership references.

#### R10. Group -> Role containment
Current: `Group.roles` is containment.

Caution: MOISE separates global role definitions from role occurrences/allowed roles in a group specification, where cardinality constraints are attached to the role-in-group relation. Direct containment can be accepted in a simplified conceptual metamodel, but it cannot represent the MOISE structure faithfully when the same role definition participates in multiple group specifications or when role-in-group cardinalities are needed.

High-fidelity solution: introduce an association class such as `RoleCardinality` / `RoleInGroup` containing `min`, `max` and referencing one `Role`.

## 5. Attribute audit

### Strongly supported / good

- `Organization.id`
- `Group.id`
- `Role.id`
- `Norm.id`
- `Norm.type`
- `Norm.condition`
- `Norm.timeConstraint`
- `Scheme.id`
- `Mission.id`
- `OGoal.id`
- `OGoal.description`
- `OGoal.type`
- `OGoal.timeToFulfill`
- `Agent.name`
- `Agent.source` (JCM/deployment-level attribute rather than pure Jason-agent semantics)
- `Plan.label`
- `Plan.context`
- `Event.operator`
- `Event.type`
- `Event.literal`
- `Workspace.name`
- `Artifact.name`
- `Artifact.type`
- `Operation.name`
- `Operation.arity`
- `Property.name`
- `Property.arity`
- `Signal.name`
- `Signal.arity`
- `Belief.literal` as a simplified representation
- `AGoal.literal` as a simplified representation
- `Action.name`, `Action.arity` as a simplified action signature

### Valid concept but needs a clearer modeling decision

#### `Role.isAbstract`
The 2020 JaCaMo presentation of MOISE includes abstract roles/inheritance as a modelling notion, so this is defensible. It is not, however, as directly evidenced as `id` in the common MOISE XML examples. Keep it if the metamodel targets the conceptual MOISE model; document the provenance.

#### `OGoal.minAgentsToSatisfy : EInt`
MOISE goals have `min` semantics. The concept is valid. The current integer representation needs a documented rule for the default/all-agents case; otherwise a richer type or explicit default/sentinel is safer.

### Important missing attributes

#### `OPlan.operator`
High priority. MOISE plans explicitly use operators such as `sequence` and `parallel` (and the metamodel/language may support other operators depending on version).

#### Mission cardinality
MOISE mission definitions have `min` and `max` cardinalities. Add for example:
- `Mission.minCardinality`
- `Mission.maxCardinality`

The exact type/default policy should follow the MOISE schema used by the target JaCaMo version.

#### Link attributes
At least:
- `Link.type` (e.g. authority/communication/acquaintance depending on MOISE version)
- `Link.scope` (e.g. intra-group/inter-group)
- `Link.extendsSubgroups`
- `Link.bidirectional` when the selected MOISE schema/version supports it.

#### Action kind
If keeping a single `Action` class instead of `InternalAction`/`ExternalAction`, add an attribute/enum distinguishing internal and external actions. JaCaMo/Jason explicitly distinguishes Jason internal actions (e.g. a leading `.`) from external artifact operations.

### Attributes intentionally not recommended as simple class attributes

- Role min/max cardinality: belongs to the Group-Role relation.
- Subgroup min/max cardinality: belongs to the parent-group/subgroup relation.

These should be association/reification concepts if full MOISE fidelity is required.

## 6. Overall assessment

The new metamodel is directionally better than the original because it adds missing domain vocabulary (`OPlan`, `Link`) and many evidence-backed attributes. However, it is not yet a source-of-truth-quality JaCaMo metamodel. The main remaining problems are structural rather than cosmetic:

1. runtime many-to-many relations are modeled as one-to-one on one side (`Workspace`, `Artifact`, `Role`);
2. Jason plan triggering-event cardinality is wrong;
3. MOISE goal/plan/mission ownership is modeled in the wrong direction;
4. `Link` is under-modeled;
5. norm role/mission multiplicities are too broad;
6. operation effects are constrained to one property/one signal;
7. some cardinalities belong to association relations, not to Role/Mission classes directly.

Recommendation: use `jacamo_v2_cleaned.ecore` only as the cleaned current snapshot, not yet as the final thesis metamodel. A next revision should implement the high-confidence structural corrections above while keeping traceability to the original model and to the 2020 JaCaMo/MOISE/Jason sources.
