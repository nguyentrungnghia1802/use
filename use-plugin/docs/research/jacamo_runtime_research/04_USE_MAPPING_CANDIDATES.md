# 04 — Candidate JaCaMo Runtime -> USE Comparison Matrix

This file is intentionally the bridge document to compare against the existing USE transformation and frozen structural Mapping V1.

## 1. Decision procedure for every runtime fact

For each row below, the USE project must answer:

1. Does a static semantic element exist?
2. Does TraceIndex already point to a USE declaration/object/link/operation?
3. Is this runtime phenomenon a state mutation, an operation lifecycle event, a history-only event, or unsupported?
4. Does the current generated USE model have a place to store this information without altering frozen structural semantics?
5. Which OCL checkpoint should run afterward?

## 2. CArtAgO — strongest first mapping target

| JaCaMo/CArtAgO runtime | Required static trace | Candidate USE target/action | Verification checkpoint | Decision |
|---|---|---|---|---|
| observable property changed | Artifact + projected property | `SET_ATTRIBUTE` on traced `MObject.MAttribute` | AFTER_MUTATION | HIGH |
| observable property added | exact projected property | set previously undefined/projected value; **not create arbitrary MAttribute** | AFTER_MUTATION | HIGH/POLICY |
| observable property removed | exact projected property | set undefined/remove projected runtime value according to USE API | AFTER_MUTATION | HIGH/POLICY |
| operation started | Artifact + AbsOperation -> projected MOperation | `OPERATION_ENTER` with args | OPERATION_PRE | HIGH |
| operation completed | same correlation | `OPERATION_EXIT` | OPERATION_POST | HIGH |
| operation failed | same correlation | `OPERATION_FAIL`; POST skipped | operation failure report | HIGH |
| artifact created | semantic Artifact instance must already be known or explicitly supported dynamic instance | `CREATE_OBJECT` only if trace-backed | AFTER_MUTATION | RESTRICTED |
| artifact disposed | exact MObject | `DESTROY_OBJECT` or runtime tombstone policy | AFTER_MUTATION | RESTRICTED |
| agent joined workspace | Agent + Workspace relation | insert exact mapped Agent-Workspace association link | AFTER_MUTATION | COMPARE WITH R045/R? |
| agent quit workspace | same | delete exact link | AFTER_MUTATION | COMPARE |
| artifact focused | exact Agent/Artifact accessibility relation | insert link only if model contains focus/access relation | AFTER_MUTATION | OPEN |
| artifact unfocused | same | delete link | AFTER_MUTATION | OPEN |
| artifacts linked | mapped reference between artifact types | insert association link | AFTER_MUTATION | OPEN |
| signal/percept signal | none necessarily | RuntimeTrace only unless semantic projection exists | TRACE_ONLY | OPEN |

## 3. Jason

| Jason runtime | Required trace | Candidate USE handling | Checkpoint | Decision |
|---|---|---|---|---|
| goal started | semantic Goal + runtime instance identity | runtime state/history; object mutation only if Goal instance representation is explicit | AFTER_MUTATION / TRACE | OPEN |
| goal achieved/failed/dropped/... | same | goal-state attribute or RuntimeTrace projection | AFTER_MUTATION | OPEN |
| belief addition | semantic Belief exact match | create/set/link only if current USE model represents belief instances dynamically | AFTER_MUTATION | OPEN |
| belief removal | same | delete/unset/link delete | AFTER_MUTATION | OPEN |
| action started | semantic Action/ExternalAction and exact target operation if resolved | RuntimeTrace + optional operation correlation | ACTION_PRE or TRACE | MEDIUM |
| action completed/failed | same | RuntimeTrace/result | ACTION_POST or TRACE | MEDIUM |
| outbound/inbound message | semantic Message mapping must be proven | trace/history or dynamic object | TRACE | DEFER V1 |
| intention lifecycle | no frozen target currently proven | trace-only extension | TRACE | DEFER |
| reasoning-cycle start/end | no domain object | metrics/checkpoint only | none | DEFER |

### Critical distinction

A Jason `ExternalAction` and a CArtAgO `AbsOperation` may already be structurally related by the static model. Runtime action and CArtAgO operation events should be **correlated**, not necessarily turned into two independent USE operation executions.

Preferred V1 authority for actual environment operation contract checking: CArtAgO `OpId` operation lifecycle. Jason action events provide cross-dimensional evidence that the agent intended/performed the mapped external action.

## 4. Moise

| Moise runtime | Required static trace | Candidate USE target/action | Checkpoint | Decision |
|---|---|---|---|---|
| OEAgent exists | semantic Agent alias | alias/state only; avoid duplicate Agent object | SNAPSHOT | HIGH conceptually |
| RolePlayer added | Agent + Role + Group | `INSERT_LINK` Role.players (and group context if represented) | AFTER_MUTATION | HIGH after target audit |
| RolePlayer removed | same | `DELETE_LINK` | AFTER_MUTATION | HIGH after target audit |
| GroupInstance created | group specification + runtime instance policy | object only if USE models runtime group instances | AFTER_MUTATION | OPEN |
| SchemeInstance created | scheme spec + instance policy | object if runtime instances modeled | AFTER_MUTATION | OPEN |
| MissionPlayer added | Agent + Mission + Scheme | link/state | AFTER_MUTATION | HIGH after target audit |
| MissionPlayer removed | same | link removal | AFTER_MUTATION | HIGH after target audit |
| GoalInstance state changed | OGoal/Goal exact binding | state attribute/history | AFTER_MUTATION | OPEN |
| responsible group changed | Group + Scheme | association mutation | AFTER_MUTATION | OPEN |
| permission/obligation set changed | Norm/Role/Mission exact semantics | verification-only normative state | NORMATIVE checkpoint later | DEFER |
| norm fulfilled/violated/expired | complete NPL runtime semantics needed | none yet | none | DEFER |

## 5. Potential double-source conflicts

### Organisation via CArtAgO vs Moise

JaCaMo implements Moise organisation boards as CArtAgO artifacts. The plugin must not do:

```text
GroupBoard observable update -> mutate Role.players
AND
Moise OE diff -> mutate the same Role.players again
```

Choose a semantic authority.

Recommended:

```text
Moise OE = organisation semantic authority
CArtAgO org-board events = transport/diagnostic correlation
```

### Jason action vs CArtAgO operation

Recommended:

```text
Jason action = agent-side cross-dimensional evidence
CArtAgO OpId = authoritative artifact operation lifecycle for PRE/POST
```

## 6. Proposed V1 Runtime Mapping rule shape

```json
{
  "id": "RM-CARTAGO-OBS-CHANGED",
  "source": {
    "runtime": "CARTAGO",
    "eventKind": "OBS_PROPERTY_CHANGED"
  },
  "requirements": {
    "runtimeIdentity": "ARTIFACT_PROPERTY",
    "semanticTraceRequired": true,
    "useTargetKind": "ATTRIBUTE"
  },
  "target": {
    "mutation": "SET_ATTRIBUTE"
  },
  "verification": {
    "checkpoint": "AFTER_MUTATION"
  }
}
```

The JSON must not contain Auction-specific object names or OCL expressions.

## 7. Required USE-side audit before final mapping

Compare every HIGH/MEDIUM row with:

- generated MClass/MAttribute/MAssociation/MOperation;
- current instance materialization behavior;
- TraceIndex target kinds;
- runtime mutation API capabilities;
- undefined/unset representation;
- operation enter/exit PRE/@pre/POST semantics;
- Verification Profile V1 alterations;
- frozen Mapping V1 source IDs for relevant relations.

Only then promote candidate rules to Runtime Mapping V1.
