# 01 — JaCaMo Runtime Architecture

## 1. Main runtime picture

JaCaMo is an integration runtime. The most useful mental model for USE mirroring is:

```text
JaCaMoLauncher / JaCaMo project configuration
                 |
        +--------+---------+
        |        |         |
        v        v         v
      Jason    CArtAgO    Moise
      Agent    Environment Organisation
      runtime  runtime     runtime
        |        |         |
        +--------+---------+
                 |
          JaCaMo integration
```

Do not design one generic "JaCaMo object changed" mapping before understanding which runtime dimension owns the fact.

## 2. JaCaMo orchestration runtime

### 2.1 Launcher

`JaCaMoLauncher` extends Jason `RunLocalMAS`.

It:

- parses the `.jcm` project;
- installs JaCaMo runtime services;
- creates platform wrappers;
- creates CArtAgO environment/workspaces/artifacts;
- creates Moise organisation runtime representation when configured;
- creates Jason agents;
- starts platforms before agents;
- stops agents before shutting down platforms.

### 2.2 Runtime service integration

`JaCaMoRuntimeServices` extends Jason `LocalRuntimeServices` and registers `CAgentArch` and `JaCaMoAgArch` as default agent architectures.

### 2.3 `JaCaMoAgArch`

At agent initialization it derives runtime integration work from `.jcm` parameters:

- workspaces the agent should join;
- artifacts the agent should focus;
- organisation groups/roles;
- auto-focus on organisation group and organisation board artifacts.

It injects achievement goals such as the integration goals for environment focus and initial roles. This means static `.jcm` relationships become concrete runtime interactions after the platform is available.

## 3. Jason runtime domain

### 3.1 Runtime entities/state

Candidate observable semantic state:

- Agent identity/name
- Belief base (literals/rules represented in belief base)
- Circumstance
  - events
  - intentions
  - mailbox
  - selected/executing state
- Goals and lifecycle states
- External actions and action result
- Messages
- Reasoning cycle boundaries

### 3.2 Relevant public hooks

`CircumstanceListener` callbacks:

- `eventAdded(Event)`
- `intentionAdded(Intention)`
- `intentionDropped(Intention)`
- `intentionSuspended(...)`
- `intentionWaiting(...)`
- `intentionResumed(...)`
- `intentionExecuting(...)`

`GoalListener` lifecycle states include:

- pending
- executing
- suspended
- resumed
- waiting
- achieved
- dropped
- failed
- finished

Callbacks include goal started/finished/failed/suspended/waiting/resumed/executing.

`AgArch` exposes/hook points:

- `perceive()`
- `checkMail()`
- `act(ActionExec)`
- `actionExecuted(ActionExec)`
- `sendMsg(Message)`
- `broadcast(Message)`
- reasoning-cycle start/finish

Agent belief base is available via `Agent.getBB()`.

### 3.3 Runtime mapping implications

High-confidence normalization candidates:

- goal lifecycle -> `GOAL_*` events
- action start/result -> `AGENT_ACTION_STARTED` / `AGENT_ACTION_COMPLETED|FAILED`
- belief-event triggers -> belief add/remove only after exact Trigger classification
- message outbound -> observable via architecture interception
- message inbound -> requires architecture/mailbox interception; do not claim a dedicated CircumstanceListener message callback
- intentions are observable but are not currently part of the frozen structural mapping contract unless explicitly modeled

## 4. CArtAgO runtime domain

### 4.1 Runtime entities/state

- Workspace
- Agent joined to workspace (`AgentId` / context)
- Artifact (`ArtifactId`)
- Artifact observable properties
- Operation invocation (`OpId`)
- Operation parameters (`Op`)
- Signals/percepts (`Tuple`)
- Focus relation: Agent -> Artifact
- Artifact links
- Artifact lifecycle

### 4.2 Exact logger callbacks in CArtAgO 3.1

`ICartagoLogger` provides:

- `opRequested(when, who, aid, op)`
- `opStarted(when, opId, aid, op)`
- `opSuspended(...)`
- `opResumed(...)`
- `opCompleted(...)`
- `opFailed(..., msg, descr)`
- `newPercept(when, aid, signal, added[], removed[], changed[])`
- `artifactCreated(when, artifactId, creator)`
- `artifactDisposed(when, artifactId, disposer)`
- `artifactFocussed(when, who, artifactId, filter)`
- `artifactNoMoreFocussed(when, who, artifactId)`
- `artifactsLinked(when, agentId, linking, linked)`
- `agentJoined(when, agentId)`
- `agentQuit(when, agentId)`

This is the strongest upstream basis for Runtime Mapping V1.

### 4.3 Authoritative snapshot API

`ICartagoController` exposes:

- current agents
- current artifacts
- artifact information

This supports initial full sync and drift/resync in addition to delta events.

### 4.4 Operation identity

`OpId` carries:

- numeric operation instance ID
- `ArtifactId`
- performing `AgentId`
- operation name

Therefore operation correlation can be based on upstream identity rather than guessed names.

### 4.5 Runtime mapping implications

Very strong candidates:

```text
artifactCreated              -> traced object existence/lifecycle
artifactDisposed             -> traced object lifecycle removal
newPercept added property    -> set/create projected observable state
newPercept changed property  -> SET_ATTRIBUTE
newPercept removed property  -> UNSET/undefined projected attribute
opStarted                    -> OPERATION_ENTER
opCompleted                  -> OPERATION_EXIT
opFailed                     -> OPERATION_FAIL
artifactFocussed             -> INSERT_LINK only if focus relation exists in USE semantics
artifactNoMoreFocussed       -> DELETE_LINK only if mapped
artifactsLinked              -> INSERT_LINK only when exact mapped association exists
agentJoined/agentQuit        -> workspace membership link mutation only if projected
```

Never create an arbitrary semantic object only because CArtAgO reports an unknown artifact.

## 5. Moise runtime domain

### 5.1 Runtime object graph

Moise `OE` (Organisational Entity) maintains runtime instances including:

- `OEAgent`
- `GroupInstance`
- `SchemeInstance`
- `RolePlayer`
- `MissionPlayer`
- `GoalInstance`
- `PlanInstance`

It also references the organisation specification `OS`.

### 5.2 Group runtime state

`GroupInstance` includes:

- group specification identity
- role players
- subgroup instances
- responsible schemes
- well-formedness / cardinality-related runtime state

### 5.3 Scheme runtime state

`SchemeInstance` includes:

- scheme specification identity
- root goal instance
- mission players
- responsible groups
- goal instances
- plan instances
- well-formedness checks against mission cardinalities

Goal instances carry runtime goal state; mission players connect agents to missions.

### 5.4 Agent normative runtime state

`OEAgent`/`RolePlayer` expose derived obligations/permissions from the current organisational situation.

Important limitation: this does not by itself prove a complete event lifecycle for norm activation, fulfilment, violation and expiration suitable for direct `NORM_STATE_CHANGED` mapping. Treat detailed norm lifecycle as a later research layer.

### 5.5 Observation strategy

For Moise 1.1, use authoritative `OE` snapshot + deterministic diff when no stable public change-listener API is available for the needed state.

Candidate normalized deltas:

- OE agent created/removed
- role player added/removed
- group instance created/removed
- scheme instance created/removed
- mission commitment added/removed
- goal instance state changed
- responsible-group relation changed
- derived permission/obligation set changed (report/verification only until semantics are formally fixed)

## 6. Important JaCaMo-specific organisation implementation detail

JaCaMo's `Moise` platform wrapper creates organisational runtime infrastructure inside CArtAgO workspaces:

- an organisation workspace;
- `OrgBoard` artifact;
- `GroupBoard` artifacts;
- `SchemeBoard` artifacts.

Therefore the same organisational phenomenon may be visible both as CArtAgO artifact activity and as Moise OE state.

**Do not double-apply both as independent semantic mutations.**

Recommended source authority:

- Environment semantics: CArtAgO
- Agent mind semantics: Jason
- Organisation semantics: Moise OE
- CArtAgO events generated by organisation-board artifacts: integration/transport evidence unless a rule explicitly declares them authoritative

## 7. Runtime layers to preserve in the thesis

```text
UPSTREAM RUNTIME API
        |
        v
Connector-specific raw observation
        |
        v
Normalized RuntimeEvent
        |
        v
Exact Runtime Identity
        |
        v
SemanticId / TraceIndex
        |
        v
Runtime Mapping V1
        |
        v
USE mutation / operation checkpoint
        |
        v
OCL verification
```
