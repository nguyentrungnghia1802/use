# 00 — Research Baseline and Evidence Policy

## 1. Objective

Determine what JaCaMo actually creates, maintains, exposes, and changes at runtime so the thesis project can later answer:

> Which concrete JaCaMo runtime fact/event can be represented by which USE `MObject`, attribute value, association/link, `MOperation` lifecycle, or verification-only trace object?

This document is evidence collection, not final mapping design.

## 2. Primary upstream baseline

### JaCaMo

Repository: `jacamo-lang/jacamo`

Audited `main` commit:

`3866858a7ebf6be85d9199c13a09cf4bfb8191be`

Relevant source files:

- `build.gradle`
- `src/main/java/jacamo/infra/JaCaMoLauncher.java`
- `src/main/java/jacamo/infra/JaCaMoRuntimeServices.java`
- `src/main/java/jacamo/infra/JaCaMoAgArch.java`
- `src/main/java/jacamo/platform/Cartago.java`
- `src/main/java/jacamo/platform/Moise.java`
- `src/main/java/jacamo/project/JaCaMoProject.java`
- `src/main/java/jacamo/project/JaCaMoAgentParameters.java`
- `src/main/java/jacamo/project/JaCaMoWorkspaceParameters.java`
- `src/main/java/jacamo/project/JaCaMoOrgParameters.java`
- `examples/auction/auction.jcm`
- `examples/auction/src/env/auction_env/AuctionArtifact.java`
- `examples/auction/src/org/auction-os.xml`

### Jason

Repository: `jason-lang/jason`

Current source APIs inspected:

- `jason/asSemantics/CircumstanceListener.java`
- `jason/asSemantics/GoalListener.java`
- `jason/architecture/AgArch.java`
- `jason/asSemantics/Agent.java`
- `jason/asSemantics/Circumstance.java`

JaCaMo main declares Jason interpreter `3.3.2`. The latest directly inspected source is the repository current branch; exact `3.3.2` release tagging must be reconciled if implementation is pinned by artifact rather than commit.

### CArtAgO

Repository: `CArtAgO-lang/cartago`

Version audited: tag `v3.1`

Key APIs:

- `cartago.ICartagoLogger`
- `cartago.ICartagoController`
- `cartago.OpId`

### Moise

Repository: `moise-lang/moise`

Version audited: tag `v1.1`

Key runtime classes:

- `moise.oe.OE`
- `moise.oe.OEAgent`
- `moise.oe.GroupInstance`
- `moise.oe.SchemeInstance`
- related `RolePlayer`, `MissionPlayer`, `GoalInstance`, `PlanInstance`

## 3. JaCaMo startup facts

`JaCaMoLauncher` parses the `.jcm`, creates custom platforms, CArtAgO environment, Moise organisation platform when present, institutions when present, and agents. Platform `start()` calls occur before `super.start()` starts the Jason agents.

Conceptual startup order:

```text
parse .jcm
  -> create custom platform wrappers
  -> create CArtAgO platform wrapper
  -> create Moise platform wrapper (when organisations exist)
  -> create institution platform wrapper (when institutions exist)
  -> create Jason agents
  -> start platforms
  -> start agents
```

This matters for runtime synchronization: artifacts, organisation workspaces and boards can exist before agent reasoning cycles begin.

## 4. Important integration fact

JaCaMo registers both:

- `jaca.CAgentArch`
- `jacamo.infra.JaCaMoAgArch`

as default agent architectures through `JaCaMoRuntimeServices`.

`JaCaMoAgArch` states explicitly that communication/agent management come from Jason local infrastructure while perceive/act are delegated to CArtAgO. It also creates initial goals to focus/join workspaces/artifacts and to adopt initial organisation roles.

Therefore runtime semantics cross dimensions through the agent architecture, not merely through static `.jcm` declarations.

## 5. Evidence classification

### FACT

Direct source/API evidence.

### NORMALIZATION

Plugin-level event vocabulary proposed to represent upstream callbacks. Example: upstream `ICartagoLogger.newPercept(...)` containing changed observable properties may normalize into one or more `OBS_PROPERTY_CHANGED` events.

### CANDIDATE_MAPPING

Proposed USE-side mutation target. Must be validated against actual generated USE model and TraceIndex.

### UNSUPPORTED/OPEN

Do not fabricate. Keep explicit until source/API + USE target semantics are proven.

## 6. Compatibility blocker

The thesis plugin documentation currently records Jason `3.3.0`, whereas current JaCaMo main uses Jason `3.3.2`. Runtime Mapping V1 must either:

- target the project's existing pinned stack and audit those exact source versions, or
- deliberately update the compatibility baseline with regression evidence.

Do not mix the two silently.
