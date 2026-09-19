# Source Manifest

## JaCaMo

- Repository: `jacamo-lang/jacamo`
- Commit: `3866858a7ebf6be85d9199c13a09cf4bfb8191be`
- Main files inspected:
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

## CArtAgO

- Repository: `CArtAgO-lang/cartago`
- Tag: `v3.1`
- Files:
  - `src/main/java/cartago/ICartagoLogger.java`
  - `src/main/java/cartago/ICartagoController.java`
  - `src/main/java/cartago/OpId.java`

## Jason

- Repository: `jason-lang/jason`
- JaCaMo main dependency: `jason-interpreter:3.3.2`
- Files inspected from current repository source:
  - `jason-interpreter/src/main/java/jason/asSemantics/CircumstanceListener.java`
  - `jason-interpreter/src/main/java/jason/asSemantics/GoalListener.java`
  - `jason-interpreter/src/main/java/jason/architecture/AgArch.java`
  - `jason-interpreter/src/main/java/jason/asSemantics/Agent.java`
  - `jason-interpreter/src/main/java/jason/asSemantics/Circumstance.java`

## Moise

- Repository: `moise-lang/moise`
- Tag: `v1.1`
- Files/classes:
  - `src/main/java/moise/oe/OE.java`
  - `src/main/java/moise/oe/OEAgent.java`
  - `src/main/java/moise/oe/GroupInstance.java`
  - `src/main/java/moise/oe/SchemeInstance.java`
  - related RolePlayer/MissionPlayer/GoalInstance/PlanInstance APIs

## Research limitation

This pack is source/API analysis. It does not claim that a fresh instrumented standalone JaCaMo 1.3.1 Auction execution was captured in this session. A live event trace is explicitly listed as the next evidence task before freezing Runtime Mapping V1.
