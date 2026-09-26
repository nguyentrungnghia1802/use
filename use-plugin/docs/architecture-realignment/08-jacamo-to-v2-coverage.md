# Official JaCaMo semantics to frozen V2 coverage

## Scope and legend

Coverage was checked against all 21 EClasses, 48 attributes, 37 references and three opposite pairs in `Core/Metamodel/version-2/jacamo_v2_complete.ecore`.

- `EXACT`: official data maps without semantic invention and without losing required source context.
- `SUPPORTED_SUBSET`: V2 intentionally stores a selected faithful subset; extra official details stay in contract/provenance.
- `REPRESENTATION_LOSS`: a known official detail is not represented directly in V2.
- `API_NOT_EXPOSED`: V2 can represent the relation, but the audited source alone does not establish it.
- `UNSUPPORTED`: official value/construct cannot be coerced into the selected V2 field.
- `V2_GAP`: in-scope official concept cannot be faithfully represented by V2 or approved state/trace/projection layers and blocks the required migration scope. No such blocking gap is proven in this audit; known `REPRESENTATION_LOSS` still exists.

## EClass coverage

| V2 EClass | Official authority | Covered fields/relations | Fidelity | Required adapter rule |
|---|---|---|---|---|
| `Organization` | Moise `OS` SS/FS/NS | id, contained groups/schemes/norms | EXACT | Build from official OS graph, not XML DOM |
| `Group` | Moise `ss.Group` | id, roles, subgroups, links; parent/subgroup cardinality is relation-scoped | SUPPORTED_SUBSET / REPRESENTATION_LOSS | Preserve every `(parentGroup, subGroup, min, max)` tuple in Bridge/provenance; do not collapse conflicting contexts into intrinsic attributes |
| `Role` | Moise `ss.Role`; group-board player state | id, abstract, superRoles, runtime agents; group/role cardinality is relation-scoped | SUPPORTED_SUBSET / REPRESENTATION_LOSS | Preserve every `(group, role, min, max)` tuple; static hierarchy/players remain independently exact when evidenced |
| `Link` | Moise `ss.Link` | type, scope, source/target, extension/bidirection where exposed | SUPPORTED_SUBSET | Reject link kinds outside enum rather than coercing |
| `Norm` | Moise NS norm | id, modality, condition, deadline, role, mission | SUPPORTED_SUBSET | Only obligation/permission map to `NormType`; retain other modalities as unsupported evidence |
| `Scheme` | Moise `fs.Scheme` | id, root goal, missions | EXACT | Runtime scheme instance is a separate identity/state fact |
| `Mission` | Moise `fs.Mission` | id, cardinality, goals | EXACT | Commitments remain runtime tuples |
| `OGoal` | Moise `fs.Goal` | id, description, type, agents-to-satisfy, time, plan | SUPPORTED_SUBSET | Preserve any extra goal semantics in provenance |
| `OPlan` | Moise goal decomposition/plan | operator, ordered subgoals | EXACT for sequence/parallel/choice | Unsupported operators fail closed |
| `Agent` | JCM declaration, Jason program/runtime | name, source, plans, beliefs, goals, roles, workspaces, artifacts | SUPPORTED_SUBSET | Runtime incarnation lives in Bridge/trace identity |
| `Plan` | Jason `Plan` | label, context, trigger, ordered selected body actions | SUPPORTED_SUBSET | Retain complete official AST/provenance outside compact V2 fields |
| `Event` | Jason `Trigger` | operator, type, literal; optional signal | SUPPORTED_SUBSET | `signal` needs exact percept provenance |
| `Action` | Jason `PlanBody` | name, arity, internal/external kind; optional operation | SUPPORTED_SUBSET | `operation` needs exact selection/binding/runtime evidence |
| `Belief` | Jason literal/belief base | literal; optional property | SUPPORTED_SUBSET | `property` needs exact CArtAgO percept origin |
| `AGoal` | Jason initial/runtime goal | literal, achievement/test kind; optional organizational goal | SUPPORTED_SUBSET | Organizational link needs explicit JaCaMo/Moise evidence |
| `Environment` | initialized CArtAgO environment | contained workspaces | EXACT after initialization | A load-only declared environment is explicitly incomplete |
| `Workspace` | JCM declaration + CArtAgO `WorkspaceId` | name, artifacts, agents | EXACT after identity reconciliation | Keep declared-to-runtime trace |
| `Artifact` | JCM declaration + CArtAgO `ArtifactId`/`ArtifactInfo` | name, type, operations, properties, focused agents | EXACT after initialization | UUID retained outside V2 |
| `Operation` | CArtAgO `OpDescriptor` | name, arity, exact observed property/signal relations | SUPPORTED_SUBSET | Parameters/results stay in contract because V2 has no fields |
| `Property` | CArtAgO `ArtifactObsProperty` | name, arity | EXACT after definition exists | Runtime values are state, not structural model |
| `Signal` | CArtAgO signal/percept metadata | name, arity | EXACT when official metadata/event exists | Do not infer from a Jason literal alone |

## Attributes with constrained domains

| V2 field | Official mapping | Result |
|---|---|---|
| `Norm.type` | Moise obligation/permission | EXACT for those literals; other modalities `UNSUPPORTED` |
| `OGoal.type` | Moise achievement/maintenance | EXACT for exposed matching kinds; otherwise `UNSUPPORTED` |
| `OPlan.operator` | sequence/parallel/choice | EXACT for matching operators |
| `Link.type/scope` | Moise link type/scope | EXACT for V2 enum members; fail closed otherwise |
| `Action.kind` | Jason `PlanBody.BodyType.action/internalAction` | EXACT |
| `AGoal.type` | Jason achievement/test syntax | EXACT |
| cardinality/time/condition/context/literal fields | official object canonical text/value | SUPPORTED_SUBSET; preserve typed/original value in provenance |

## Exhaustive 48-attribute coverage

| EClass | Every V2 attribute | Official source and fidelity |
|---|---|---|
| `Organization` | `id` | Moise OS/organization ID: `EXACT` |
| `Group` | `id`, `minCardinality`, `maxCardinality` | `id`: `EXACT`; cardinality attributes: `SUPPORTED_SUBSET` for one explicitly selected parent context, otherwise `REPRESENTATION_LOSS`; exact parent/subgroup tuples remain in Bridge/provenance |
| `Role` | `id`, `isAbstract`, `minCardinality`, `maxCardinality` | `id`/`isAbstract`: `EXACT`; cardinality attributes: `SUPPORTED_SUBSET` for one explicitly selected group context, otherwise `REPRESENTATION_LOSS`; exact group/role tuples remain in Bridge/provenance |
| `Link` | `type`, `scope`, `extendsSubgroups`, `bidirectional` | Moise `Link`/`RoleRel`: `EXACT` for enum-supported values; otherwise `UNSUPPORTED` |
| `Norm` | `id`, `type`, `condition`, `timeConstraint` | Moise NS norm: `SUPPORTED_SUBSET`; obligation/permission exact, other modality unsupported |
| `Scheme` | `id` | Moise scheme definition ID: `EXACT` |
| `Mission` | `id`, `minCardinality`, `maxCardinality` | Moise mission/cardinality: `EXACT` |
| `OGoal` | `id`, `description`, `type`, `minAgentsToSatisfy`, `timeToFulfill` | Moise goal: `SUPPORTED_SUBSET`; canonical original values retained |
| `OPlan` | `operator` | Moise plan operator: `EXACT` for sequence/parallel/choice |
| `Agent` | `name`, `source` | JCM/Jason official parameters/source: `EXACT`; name is not live incarnation identity |
| `Plan` | `label`, `context` | Jason `Plan`: `EXACT` canonical AST text; full AST/provenance retained |
| `Event` | `operator`, `type`, `literal` | Jason `Trigger`: `EXACT` canonical values |
| `Action` | `name`, `arity`, `kind` | Jason body node: `EXACT` for represented external/internal actions |
| `Belief` | `literal` | Jason literal: `EXACT` canonical AST text |
| `AGoal` | `literal`, `type` | Jason goal literal/kind: `EXACT` for achievement/test |
| `Environment` | none | No attribute to map |
| `Workspace` | `name` | JCM/runtime workspace name: `EXACT`; UUID stays in identity contract |
| `Artifact` | `name`, `type` | CArtAgO `ArtifactId`: `EXACT` after initialization |
| `Operation` | `name`, `arity` | CArtAgO `IArtifactOp`: `EXACT` when descriptor exists |
| `Property` | `name`, `arity` | CArtAgO observable property: `EXACT` after definition exists |
| `Signal` | `name`, `arity` | CArtAgO emitted signal/descriptor: `EXACT` when officially exposed |

## Exhaustive 37-reference coverage

| EClass | Every V2 reference | Official evidence and fidelity |
|---|---|---|
| `Organization` | `groups`, `schemes`, `norms` | Moise SS/FS/NS containment: `EXACT` |
| `Group` | `roles`, `subGroups`, `links` | Moise group object graph: `EXACT` |
| `Role` | `superRoles` | Moise role hierarchy: `EXACT` |
| `Role` | `agents` | JCM configured players or runtime group-board players: `EXACT` with evidence; otherwise `API_NOT_EXPOSED` |
| `Link` | `sourceRole`, `targetRole` | Moise `RoleRel` endpoints: `EXACT` |
| `Norm` | `role`, `mission` | Moise norm endpoints: `EXACT` |
| `Scheme` | `rootGoal`, `missions` | Moise scheme graph: `EXACT` |
| `Mission` | `goals` | Moise mission goal membership: `EXACT` |
| `OGoal` | `plan` | Moise goal decomposition: `EXACT` when present |
| `OPlan` | `subGoals` | Ordered Moise plan subgoals: `EXACT` for supported operators |
| `Agent` | `plans`, `beliefs`, `goals` | Official Jason program/initial or live state: `SUPPORTED_SUBSET` |
| `Agent` | `roles`, `workspaces`, `artifacts` | Exact JCM/runtime relation evidence only; otherwise `API_NOT_EXPOSED` |
| `Plan` | `triggeringEvent`, `actions` | Jason `Plan` trigger and selected ordered body actions: `SUPPORTED_SUBSET` |
| `Event` | `signal` | Exact CArtAgO percept-to-Jason correlation only; otherwise `API_NOT_EXPOSED` |
| `Action` | `operation` | Exact dispatch/binding correlation only; otherwise `API_NOT_EXPOSED` |
| `Belief` | `property` | Exact percept/property provenance only; otherwise `API_NOT_EXPOSED` |
| `AGoal` | `organizationalGoal` | Exact organizational binding/event evidence only; otherwise `API_NOT_EXPOSED` |
| `Environment` | `workspaces` | Initialized CArtAgO workspace tree: `EXACT` for enumerated local scope, capability-limited for unenumerable remote scope |
| `Workspace` | `artifacts`, `agents` | Controller enumeration/JCM membership: `EXACT` for an accepted cut/config relation |
| `Artifact` | `operations`, `properties`, `agents` | Descriptor/property enumeration and focus state: `EXACT` after initialization; design-time may be partial |
| `Operation` | `signals`, `properties` | Official descriptor or operation-correlated delta only; otherwise `API_NOT_EXPOSED` |

The three opposite pairs—`Role.agents`/`Agent.roles`, `Workspace.agents`/`Agent.workspaces`, and `Artifact.agents`/`Agent.artifacts`—are created as one exact relation and materialized bidirectionally. Independent resolution of each end is forbidden.

## Relation-scoped cardinality rule

Official Moise facts are `GroupRoleCardinality(groupId, roleId, min, max)` and `ParentSubGroupCardinality(parentGroupId, subGroupId, min, max)`. The Bridge and provenance layers preserve those exact relation keys. When the same role or subgroup occurs in multiple contexts with different bounds, the adapter must not select the first value, merge bounds or claim exact V2 fidelity. Any verification depending on the missing context is capability-gated and returns `INCONCLUSIVE`/`NOT_EVALUATED` until a faithful target projection exists.

## Cross-dimensional references

| Reference | Evidence that is accepted | Static availability | Runtime availability | Status without evidence |
|---|---|---:|---:|---|
| `Agent.roles` ↔ `Role.agents` | JCM explicit role triplet or group-board role-player tuple | Exact for configured relation | Exact | `API_NOT_EXPOSED` |
| `Agent.workspaces` ↔ `Workspace.agents` | JCM membership and/or controller join state | Exact for configured relation | Exact | `API_NOT_EXPOSED` |
| `Agent.artifacts` ↔ `Artifact.agents` | JCM focus triplet or CArtAgO focus callback/controller state | Exact for configured relation | Exact | `API_NOT_EXPOSED` |
| `Action.operation` | official action dispatch correlation or exact explicit binding | Sometimes via binding | Exact during action execution | `API_NOT_EXPOSED` |
| `Belief.property` | CArtAgO percept/property provenance carried into the Jason update | Rare | Exact only for correlated percept | `API_NOT_EXPOSED` |
| `AGoal.organizationalGoal` | official organizational event/binding linking the Jason goal to Moise goal | Sometimes via binding | Exact if correlated | `API_NOT_EXPOSED` |
| `Event.signal` | official signal-to-percept-to-trigger correlation | Rare | Exact if correlation survives delivery | `API_NOT_EXPOSED` |
| `Operation.properties/signals` | official artifact descriptor or observed operation-correlated deltas | Partial | Exact for observed correlation | `API_NOT_EXPOSED` |

Name equality, arity similarity and literal text equality are never accepted evidence. `binding.json` may assert an exact relation only when both canonical endpoints resolve uniquely, its provenance is recorded and schema validation succeeds.

## Runtime concepts not represented as structural EClasses

Agent incarnation, artifact/workspace UUID, group/scheme board incarnation, mission commitment, organizational-goal state, norm instance/lifecycle, operation execution and snapshot/event watermarks are dynamic state/trace concepts. They always belong to the Bridge contract, `TraceIndex` and runtime evidence/reporting when observable. They belong to USE `MSystemState` and become OCL-queryable only if a faithful approved projection exists; current frozen Runtime Mapping V2 must not be stretched to invent one. Their absence as EClasses does not by itself block the architecture migration.

## Gap decision

No `V2_GAP` blocks the architecture migration. This is not a claim that frozen V2 is fully faithful to every official semantic detail. The gaps/losses found are:

1. API/evidence gaps for some cross-dimensional references;
2. lifecycle/state facts intentionally outside the structural metamodel;
3. compact V2 representations that require full official details to remain in provenance;
4. CArtAgO facts unavailable before initialization.
5. relation-scoped Moise role/subgroup cardinality that intrinsic-looking V2 attributes cannot always preserve;
6. runtime facts that may remain evidence-only until a faithful USE runtime projection is approved.

Changing Ecore is not required to begin this migration. After Bridge and canonical cases stabilize, a separate cardinality/V2.x/V3 review and the named `Runtime Verification Projection Review` may decide whether thesis requirements justify target-only projections or a new metamodel version. Such a change is permitted only if a future official in-scope concept satisfies all five gates: authoritative source evidence, faithful representation impossible in V2, existing projections/state/trace insufficient, not a Bridge/parser limitation, and measured migration impact.

## Coverage verdict

- Static model coverage: `FEASIBLE_WITH_ADAPTER`.
- Runtime target coverage: `FEASIBLE_WITH_ADAPTER`.
- Cross-dimensional relation coverage: `FEASIBLE_WITH_LIMITATION`, fail closed where correlation is absent.
- Frozen V2 reuse: `FULLY_FEASIBLE` for this migration scope, with the declared cardinality/runtime representation losses and capability gates; not a claim of universal semantic fidelity.
