# 02 — Runtime Capability Matrix

Status meanings:

- **SUPPORTED_SOURCE**: upstream source/API directly exposes enough information to observe the phenomenon.
- **SUPPORTED_WITH_NORMALIZATION**: upstream event exists but plugin must split/classify payload into normalized events.
- **POLL_DIFF**: authoritative state exists; delta should be computed by snapshots.
- **PARTIAL**: observable, but exact semantic mapping or lifecycle completeness is not yet proven.
- **DEFER**: do not put in Runtime Mapping V1 until semantics are proven.

| Dimension | Runtime fact/event | Upstream evidence/API | Runtime identity available | Candidate normalized event | Likely USE-side concept | Status |
|---|---|---|---|---|---|---|
| JaCaMo | platform startup | `JaCaMoLauncher.create/start` | project/platform | `STREAM_STARTED` / checkpoint only | none; sync lifecycle | SUPPORTED_SOURCE |
| Jason | agent exists/running | Jason local runtime / `AgArch.getAgName()` | agent name | `AGENT_DISCOVERED` | Agent `MObject` trace | PARTIAL |
| Jason | belief base snapshot | `Agent.getBB()` | agent + literal | snapshot fact | Belief object/value/link depending projection | SUPPORTED_SOURCE |
| Jason | event added | `CircumstanceListener.eventAdded` | agent + Trigger/Event | classify into belief/goal/event categories | TriggeringEvent/Belief/Goal | SUPPORTED_WITH_NORMALIZATION |
| Jason | goal started | `GoalListener.goalStarted` | agent + goal trigger | `GOAL_STARTED` | Goal runtime state/trace | SUPPORTED_SOURCE |
| Jason | goal state lifecycle | GoalListener callbacks + states | agent + goal trigger | `GOAL_*` | Goal state/trace | SUPPORTED_SOURCE |
| Jason | intention added/dropped | CircumstanceListener | agent + intention | `INTENTION_*` | no stable USE target yet | DEFER |
| Jason | action start | `AgArch.act(ActionExec)` interception | agent + action term | `AGENT_ACTION_STARTED` | Action / projected operation trace | SUPPORTED_SOURCE |
| Jason | action result | `AgArch.actionExecuted(ActionExec)` | agent + same ActionExec/correlation strategy | `AGENT_ACTION_COMPLETED/FAILED` | operation/action trace | SUPPORTED_SOURCE |
| Jason | outbound message | `AgArch.sendMsg/broadcast` | sender + Message | `MESSAGE_SENT` | Message if projected | PARTIAL |
| Jason | inbound mailbox | `checkMail`, Circumstance mailbox | receiver + Message | `MESSAGE_RECEIVED` with custom bridge | Message if projected | PARTIAL |
| CArtAgO | current workspace agents | `ICartagoController.getCurrentAgents()` | `AgentId` | snapshot | workspace membership | SUPPORTED_SOURCE |
| CArtAgO | current artifacts | `getCurrentArtifacts()` | `ArtifactId` | snapshot | Artifact `MObject` trace | SUPPORTED_SOURCE |
| CArtAgO | artifact info/state | `getArtifactInfo(name)` | ArtifactId/name | snapshot | Artifact + obs properties | SUPPORTED_SOURCE |
| CArtAgO | operation requested | `ICartagoLogger.opRequested` | AgentId + ArtifactId + Op | `ARTIFACT_OPERATION_REQUESTED` | trace/history only initially | SUPPORTED_SOURCE |
| CArtAgO | operation started | `opStarted` | `OpId` + ArtifactId + Op | `ARTIFACT_OPERATION_STARTED` | `MOperation` enter | SUPPORTED_SOURCE |
| CArtAgO | operation suspended | `opSuspended` | OpId | `ARTIFACT_OPERATION_SUSPENDED` | runtime trace | PARTIAL |
| CArtAgO | operation resumed | `opResumed` | OpId | `ARTIFACT_OPERATION_RESUMED` | runtime trace | PARTIAL |
| CArtAgO | operation completed | `opCompleted` | OpId | `ARTIFACT_OPERATION_COMPLETED` | `MOperation` exit | SUPPORTED_SOURCE |
| CArtAgO | operation failed | `opFailed` | OpId + message/descr | `ARTIFACT_OPERATION_FAILED` | operation fail | SUPPORTED_SOURCE |
| CArtAgO | signal/percept | `newPercept(... signal, added, removed, changed)` | ArtifactId | `ARTIFACT_SIGNAL` | runtime trace/query | PARTIAL |
| CArtAgO | obs property added | `newPercept.added[]` | ArtifactId + property | `OBS_PROPERTY_ADDED` | projected MAttribute value / dynamic state | SUPPORTED_WITH_NORMALIZATION |
| CArtAgO | obs property changed | `newPercept.changed[]` | ArtifactId + property | `OBS_PROPERTY_CHANGED` | `SET_ATTRIBUTE` | SUPPORTED_WITH_NORMALIZATION |
| CArtAgO | obs property removed | `newPercept.removed[]` | ArtifactId + property | `OBS_PROPERTY_REMOVED` | unset/undefined attribute | SUPPORTED_WITH_NORMALIZATION |
| CArtAgO | artifact created | `artifactCreated` | ArtifactId + creator AgentId | `ARTIFACT_CREATED` | object lifecycle, only trace-backed | SUPPORTED_SOURCE |
| CArtAgO | artifact disposed | `artifactDisposed` | ArtifactId + disposer | `ARTIFACT_DISPOSED` | object lifecycle, only trace-backed | SUPPORTED_SOURCE |
| CArtAgO | agent focus artifact | `artifactFocussed` | AgentId + ArtifactId | `ARTIFACT_FOCUSED` | association link if modeled | SUPPORTED_SOURCE |
| CArtAgO | focus removed | `artifactNoMoreFocussed` | AgentId + ArtifactId | `ARTIFACT_UNFOCUSED` | delete link if modeled | SUPPORTED_SOURCE |
| CArtAgO | artifact linked | `artifactsLinked` | two ArtifactIds + AgentId | `ARTIFACTS_LINKED` | association if exact mapping exists | SUPPORTED_SOURCE |
| CArtAgO | agent joins workspace | `agentJoined` | AgentId | `WORKSPACE_AGENT_JOINED` | Agent-Workspace link if mapped | SUPPORTED_SOURCE |
| CArtAgO | agent quits workspace | `agentQuit` | AgentId | `WORKSPACE_AGENT_QUIT` | link removal if mapped | SUPPORTED_SOURCE |
| Moise | OE agents | `OE` agent collection | OEAgent id | snapshot/diff | Agent organisational alias | POLL_DIFF |
| Moise | group instances | `OE` / GroupInstance | group instance id/spec | `GROUP_INSTANCE_*` | Group object/link state | POLL_DIFF |
| Moise | role players | `GroupInstance.getPlayers()` | OEAgent + role + group | `ROLE_PLAYER_ADDED/REMOVED` | Role.players link | POLL_DIFF |
| Moise | scheme instances | `OE.getSchemes()` / SchemeInstance | scheme instance id/spec | `SCHEME_INSTANCE_*` | Scheme object/state | POLL_DIFF |
| Moise | mission players | `SchemeInstance.getPlayers()` | OEAgent + mission + scheme | `MISSION_COMMITTED/UNCOMMITTED` | Agent-Mission link/state | POLL_DIFF |
| Moise | goal instance state | `SchemeInstance.getGoals()` / GoalInstance | scheme + goal | `ORGANISATIONAL_GOAL_STATE_CHANGED` | OGoal runtime state | POLL_DIFF |
| Moise | responsible groups | `getResponsibleGroups()` | group + scheme | relation delta | association link | POLL_DIFF |
| Moise | permissions/obligations | `OEAgent` / `RolePlayer` derived APIs | agent + norm-derived permission | `NORMATIVE_ENTITLEMENT_CHANGED` | verification-only until semantics frozen | PARTIAL |
| Moise/NPL | norm activation/violation lifecycle | not proven as stable public event source in this audit | unknown | none | none | DEFER |

## Recommended V1 runtime-event subset

Start with the events that are both observable and have a clear USE mutation/checkpoint:

```text
CArtAgO:
- OBS_PROPERTY_ADDED / CHANGED / REMOVED
- ARTIFACT_OPERATION_STARTED / COMPLETED / FAILED
- ARTIFACT_CREATED / DISPOSED (trace-backed only)
- WORKSPACE_AGENT_JOINED / QUIT if Agent.joinWorkspace is materialized
- ARTIFACT_FOCUSED / UNFOCUSED if corresponding relation is materialized

Jason:
- GOAL_STARTED / state transitions
- AGENT_ACTION_STARTED / COMPLETED / FAILED
- belief add/remove only after exact Event/Trigger classification and USE target semantics are proven

Moise:
- ROLE_PLAYER_ADDED / REMOVED
- MISSION_COMMITTED / UNCOMMITTED
- ORGANISATIONAL_GOAL_STATE_CHANGED
- GROUP/SCHEME lifecycle when matched to known semantic instances
```

Do not include every observable callback in the first mapping just because it exists.
