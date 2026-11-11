# Compact Semantic Mapping Specification (v3)

Namespace: `urn:use:jacamo-mapping:3`
Version: `3.0`

## 1. Syntax Overview

A compact semantic mapping document must declare `xmlns="urn:use:jacamo-mapping:3"` and `version="3.0"`.
It contains three top-level sections:
1. `<csl>`: Concept and member correspondences from JaCaMo to CSL.
2. `<istar>`: Intentional correspondences from JaCaMo to iStar (Actors, Goals, Tasks, Resources).
3. `<runtime>`: Concise runtime declarations (actor activation/episode policies, required capabilities, presentation milestones).

```xml
<?xml version="1.0" encoding="utf-8"?>
<mapping xmlns="urn:use:jacamo-mapping:3" version="3.0" id="case-mapping-id">
  <csl>
    <map source="..." target="..." />
  </csl>
  <istar>
    <map source="..." target="..." />
  </istar>
  <runtime>
    <actorRuntime actor="..." activationPolicy="..." episodePolicy="..." />
    <requirement id="..." provider="..." ... />
    <milestones kind="DOMAIN" />
  </runtime>
</mapping>
```

## 2. Source-Reference Grammar

Source references follow the URI-like format `scheme:authority/path`:

| Kind | Syntax | Example | Resolves To |
|---|---|---|---|
| Artifact Type | `artifact:<fqcn>` | `artifact:auction_env.AuctionArtifact` | CArtAgO Artifact Java class |
| Observable Property | `artifact:<fqcn>/property:<name>` | `artifact:auction_env.AuctionArtifact/property:running` | Artifact observable property |
| Internal Field | `artifact:<fqcn>/field:<name>` | `artifact:auction_env.AuctionArtifact/field:currentWinner` | Artifact Java member field |
| Operation Signature | `artifact:<fqcn>/op:<name>(<paramTypes>)` | `artifact:auction_env.AuctionArtifact/op:start(java.lang.String)` | Artifact `@OPERATION` |
| Moise Group | `moise:<org>/group:<name>` | `moise:auction/group:auctionGroup` | Moise OS group specification |
| Moise Role | `moise:<org>/role:<name>` | `moise:auction/role:auctioneer` | Moise OS role definition |
| Moise Scheme | `moise:<org>/scheme:<name>` | `moise:auction/scheme:doAuction` | Moise OS scheme specification |
| Moise Goal | `moise:<org>/goal:<name>` | `moise:auction/goal:auction` | Moise OS goal definition |
| Agent Program | `agent:<name>` | `agent:auction_capabilities` | Jason agent program/asl |
| Agent Initial Goal | `asl:<path>/goal:<trigger>` | `asl:src/agt/auction_capabilities.asl/goal:do_auction` | Jason initial achievement goal |
| Plan Step Range | `asl:<path>/plan:<trigger>/step:<name>[<first>..<after>]` | `asl:src/agt/auction_capabilities.asl/plan:do_auction(Id,P)/step:createScheme[0..3]` | AST body instruction partition |

## 3. Target-Reference Grammar

Target references identify elements in compiled CSL or iStar models:

| Target Model | Syntax | Example | Resolves To |
|---|---|---|---|
| CSL Classifier | `<EntityOrContext>` | `Auction`, `auctionGroup` | CslEntity or CslContext |
| CSL Attribute | `<EntityOrContext>.<attribute>` | `Auction.running`, `Auction.task` | CslAttribute |
| CSL Role End | `<Context>.<role>` | `auctionGroup.auctioneer` | CslRole inside context |
| iStar Actor | `<ActorName>` | `auction_capabilities`, `Auction` | iStar Actor / Role |
| iStar Goal | `<ActorName>.<GoalName>` | `auction_capabilities.do_auction` | iStar Goal |
| iStar Task | `<ActorName>.<TaskName>` | `auction_capabilities.createScheme` | iStar Task |
| iStar Resource | `<ResourceName>` | `SchemeBoard`, `AuctionArtifact` | iStar Resource |

## 4. Runtime Declarations

### 4.1 Actor Runtime Policy
`<actorRuntime actor="<ActorName>" activationPolicy="<policy>" episodePolicy="<policy>" />`
- `activationPolicy`: e.g. `OBSERVED_SCHEME_COMMITMENT` (default: `EXPLICIT_ENACTMENT`)
- `episodePolicy`: e.g. `RUN_SCHEME_PLAYER_INVOCATION` (default: `STATIC_LIFETIME`)

### 4.2 Requirement Capabilities
`<requirement id="<id>" provider="<provider>" ... />`
- Declares external observation providers (`cartago.operation-history.v1`, `moise.scheme-lifecycle.v1`, `npl.lifecycle.v1`, `npl.source-clock.v1`).

### 4.3 Presentation Milestones
`<milestones kind="DOMAIN" />`
- Indicates that presentation state transitions are filtered to domain-level attribute changes rather than low-level internal observation cuts.

