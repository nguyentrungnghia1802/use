# 03 — Runtime Identity Model Required Before Runtime Mapping

## 1. Rule

Runtime Mapping answers **how to mutate**. Identity resolution answers **what target to mutate**.

No event may mutate USE until its runtime identity resolves exactly to a semantic identity and then to a USE target.

```text
RuntimeKey / Alias
    -> SemanticId
    -> TraceIndex
    -> UseId
    -> MObject / MAttribute / MAssociation / MOperation
```

## 2. Jason identity

Primary runtime key:

```text
jason-agent:<agentName>
```

Examples:

```text
jason-agent:bob
jason-agent:alice
```

Candidate subordinate identities:

```text
jason-goal:<agentName>:<goal-canonical-form-or-runtime-correlation>
jason-action:<agentName>:<correlationId>
jason-message:<messageId-or-generated-correlation>
```

Avoid using literal text alone as a unique long-lived identity when multiple concurrent instances can exist.

## 3. CArtAgO identity

Artifact identity should use the real workspace/artifact identity, not only display name.

Conceptual key:

```text
cartago-artifact:<workspaceFullName>:<artifactId-or-name>
```

Operation invocation identity can use `OpId` fields:

```text
cartago-op:<workspace>:<artifact>:<numericOpId>:<opName>:<agentId>
```

`OpId` already carries artifact, operation name and performing agent identity, so it is the preferred correlation anchor for enter/exit/fail.

Observable property target key:

```text
cartago-obs:<workspace>:<artifact>:<propertyName>
```

This key is only accepted if the property was statically projected or otherwise has an exact semantic binding. Unknown property names must not create new semantic attributes automatically.

## 4. Moise identity

Separate specification identity from runtime instance identity.

Examples:

```text
moise-agent:<organisation>:<agentId>
moise-group-instance:<organisation>:<groupInstanceId>:<groupSpecId>
moise-role-player:<organisation>:<groupInstanceId>:<agentId>:<roleId>
moise-scheme-instance:<organisation>:<schemeInstanceId>:<schemeSpecId>
moise-mission-player:<organisation>:<schemeInstanceId>:<agentId>:<missionId>
moise-goal-instance:<organisation>:<schemeInstanceId>:<goalId>
```

A runtime group/scheme instance must not be equated with its static specification object unless the USE representation deliberately models instances that way. This is a major point to compare with the current USE metamodel/transformation.

## 5. Alias joining across dimensions

The same logical agent may have multiple runtime identities:

```text
Jason bob
CArtAgO AgentId for bob
Moise OEAgent bob
```

Do not collapse them by fuzzy name. Maintain aliases pointing to the same `SemanticId` only when exact project/runtime evidence establishes the relation.

```text
RuntimeAlias[]
  jason-agent:bob
  cartago-agent:<...bob...>
  moise-agent:aorg:bob
        -> semantic Agent bob
        -> USE Agent object bob
```

## 6. Static trace prerequisite

Before runtime, transformation should already have trace edges such as:

```text
semantic Agent bob -> USE MObject bob:Agent
semantic Artifact auction1 -> USE MObject auction1:AuctionArtifact
semantic AbsOperation bid -> USE MOperation AuctionArtifact::bid
semantic Agent.artifact/binding relation -> USE association/link where applicable
```

Runtime binding adds only the live alias layer.

## 7. Unknown runtime entity policy

```text
runtime entity observed
  -> no exact semantic candidate
  -> UNBOUND/QUARANTINED
  -> diagnostic
  -> no USE mutation
```

This is mandatory for artifact creation, dynamic groups/schemes, beliefs and goals. Discovery is allowed; semantic fabrication is not.
