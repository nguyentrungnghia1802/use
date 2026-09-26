# Static / initialized / runtime availability matrix

## Legend

- `EXACT`: directly exposed by the cited official object/API at that phase.
- `PARTIAL`: some fields exist, but completeness is not guaranteed.
- `—`: concept does not yet exist or no audited API exposes it.
- Model facts go to `ModelSnapshot`; live values/relations go to `RuntimeSnapshot` and `RuntimeEvent`.
- No `PARTIAL` cell permits semantic guessing.
- `EXACT` in this matrix describes source observation, not necessarily an exact frozen-V2 or `MSystemState` projection.

## Availability by concept

| Concept/fact | Parse/load-only | After initialization, before business run | Runtime only | Exact authority/API | Listener/event | Stable source identity | Notes |
|---|---:|---:|---:|---|---|---|---|
| JCM project identity/config | EXACT | EXACT | EXACT | `JaCaMoProjectParser.parse` -> `JaCaMoProject` | No generic project listener | project namespace + canonical source URI/digest | Configuration, not live subsystem state |
| Imported `uses` projects | EXACT | EXACT | EXACT | `JaCaMoProject.importProject` | None | imported URI/digest | Preserve merge provenance |
| Agent declarations/instances policy | EXACT | EXACT | EXACT | `JaCaMoProject.getAgents()` / `AgentParameters` | None | declaration path + name/index | Not a live incarnation |
| Workspace declarations | EXACT | EXACT | EXACT | `JaCaMoProject.getWorkspaces()` | None | project + declared path | Runtime UUID comes later |
| Artifact declarations | EXACT | EXACT | EXACT | `JaCaMoWorkspaceParameters.getArtifacts()` | None | declaration path/name | Dynamic artifacts absent |
| Org/group/scheme declarations | EXACT | EXACT | EXACT | `JaCaMoOrgParameters` | None | project + definition/instance name | Actual boards come later |
| Jason expanded program AST | EXACT with configured directives | EXACT | EXACT/mutable | `Agent.parseAS`, `PlanLibrary`, AST terms | `PlanLibraryListener` for later change | source URI + AST/source position-derived key | Source columns unavailable |
| Jason initial beliefs/goals | EXACT | EXACT | superseded by live state | `Agent.getInitialBels/getInitialGoals` | Runtime via selected hooks | agent declaration + AST key | Initial declarations are not current beliefs |
| Live agent incarnation | — | EXACT once agent initialized | EXACT | Bridge `AgArch.init`, `RuntimeServices` | Bridge `AgArch.init/stop` | Bridge-issued UUID + session/generation | Same local name may recur |
| Live belief base | — | EXACT point read | EXACT | `Agent.getBB()` | PARTIAL through circumstance/update events | agent incarnation + canonical literal key | Snapshot reconciliation required |
| Live goals/circumstance | — | EXACT point read | EXACT | `Agent.getTS()/getC()` | `GoalListener`, `CircumstanceListener` | agent incarnation + event/intention identity | No global order |
| Action request/result | — | — | EXACT | `AgArch.act/actionExecuted`, `ActionExec` | Direct hook | agent incarnation + correlation UUID | Operation link requires evidence |
| CArtAgO runtime workspace | — | EXACT after environment creation | EXACT | `CartagoEnvironment`, `WorkspaceId`, controller | logger join/create callbacks | official workspace UUID + session/generation | Declaration link retained separately |
| CArtAgO artifact instance/type | declared artifacts PARTIAL | EXACT | EXACT | `ArtifactId`, `ArtifactInfo` | create/dispose callbacks | official artifact UUID | Local name is not identity |
| Operation name/arity | source/declaration PARTIAL | EXACT for descriptors | EXACT | `OpDescriptor` | operation lifecycle logger | artifact UUID + signature | Dynamic descriptors can lack reflection metadata |
| Operation parameter names/types | source PARTIAL | PARTIAL | PARTIAL | `ArtifactOpMethod.getMethod()` + reflection | None | descriptor/signature | Names depend on `-parameters`/metadata |
| Observable property definition | source PARTIAL | EXACT for created properties | EXACT | `ArtifactInfo`, `ArtifactObsProperty` | percept/property deltas | artifact UUID + property full ID/name/index | Definitions can be dynamic |
| Observable property value | — | EXACT point read | EXACT | `ArtifactObsProperty` | percept/property deltas | property identity above | Value is state, not V2 structural field |
| Agent workspace/focus relation | JCM configuration PARTIAL | EXACT | EXACT | project config + controller/logger | join/quit, focus/unfocus | tuple of incarnations | Carry relation source/provenance |
| Moise OS definitions | EXACT | EXACT | EXACT | `OS.loadOSFromURI`, SS/FS/NS | Static | OS URI/digest + official ID | Official object graph is authority |
| Role cardinality in group | EXACT | EXACT | EXACT | `Group.getRoleCardinality(Role)` | Static | group definition + role definition | Exact Bridge relation fact; V2 role attributes can lose group context |
| Subgroup cardinality in parent | EXACT | EXACT | EXACT | `Group.getSubGroupCardinality(Group)` | Static | parent group + subgroup definition | Exact Bridge relation fact; V2 group attributes can lose parent context |
| Group/scheme runtime instances | configured instances PARTIAL | EXACT after board creation | EXACT | `GroupBoard`, `SchemeBoard`, `ArtifactId` | CArtAgO lifecycle plus board hooks | board artifact UUID | Dynamic instances absent at parse time |
| Role players | configured roles PARTIAL | EXACT point read | EXACT | group board state | board/property/operation evidence | group-board + role + agent incarnation | Do not infer by name |
| Mission commitments | — | empty/exact if initialized | EXACT | scheme board state | board/property/operation evidence | scheme-board + mission + agent incarnation | Runtime tuple, not static V2 object; may remain evidence-only |
| Organizational-goal state | — | initial state after board create | EXACT | scheme board state | board/NPL evidence | scheme-board + goal definition | State separate from `OGoal` definition; may remain evidence-only |
| Norm definitions | EXACT | EXACT | EXACT | Moise NS objects | Static | OS + norm ID | Modalities outside V2 remain explicit |
| Norm instances/lifecycle | — | EXACT after NPL init | EXACT | `NPLInterpreter` collections | `NormativeListener` | board incarnation + scoped source token | Stability across rebuild unproven; no automatic frozen-V2 runtime projection |
| Cross-subsystem relations | explicit config/binding only | PARTIAL | EXACT only when official correlated evidence exists | configuration + callbacks + explicit `binding.json` | Depends on relation | tuple of canonical endpoint IDs | Never use similarity/fuzzy matching |
| Whole-system snapshot | — | validated cut | validated cut | coordinated Bridge protocol | buffered event sources | snapshot/session/generation IDs | Not globally atomic |

## What can build the USE `MModel` before runtime

The following are sufficient before business execution:

1. frozen Ecore V2 vocabulary and Mapping V2.2;
2. official merged JCM project declarations;
3. official Jason AST after directives/includes resolve;
4. official Moise OS object graph;
5. declared CArtAgO artifact classes plus only descriptor facts actually exposed by an initialized load-only environment or explicitly incomplete static enrichment;
6. exact, provenance-bearing `binding.json` relations where the official objects do not expose a cross-dimensional link.

The core 21 V2 classifiers can therefore compile into an `MModel` before runtime. Dynamic concrete artifact subtypes/properties may enrich the model through a new model revision after initialization; consumers must negotiate that revision before applying its state/events.

## What can build the initial `MSystemState`

- A declaration-only/offline state may contain explicitly labelled configured objects, but it is not called authoritative runtime state.
- The first authoritative `MSystemState` is built only from an accepted `RuntimeSnapshot` after subsystem initialization and identity validation.
- Snapshot materialization creates only live agents, workspace/artifact instances, role relations and other runtime facts for which a faithful target mapping exists. Other captured facts remain in Bridge trace/evidence/reporting with explicit projection status; it must not represent missing or unrepresentable values with guessed defaults.
- OCL may evaluate a runtime dependency only when that dependency is faithfully materialized into `MSystemState`; otherwise the selected constraint is `INCONCLUSIVE`, `NOT_EVALUATED` or capability-blocked.
- Event application begins strictly after the snapshot watermarks for the same session/generation/model revision.

## Availability conclusion

The matrix supports `FEASIBLE_WITH_ADAPTER`. It rejects two stronger claims: (1) that raw source alone yields a complete CArtAgO runtime model, and (2) that current official APIs yield a single atomic snapshot across Jason, CArtAgO and Moise.
