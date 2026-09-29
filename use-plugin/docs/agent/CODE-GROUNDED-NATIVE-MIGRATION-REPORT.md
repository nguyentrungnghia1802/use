# Code-Grounded Native migration report

**Date:** 2026-09-29  
**Scope:** Phase 2 onward, through the completed gates in `task.md`  
**Authority:** official Java/API adapters -> neutral contract -> native USE

## Gate summary

| Gate | Result | Evidence/checkpoint |
|---|---|---|
| Jason phase | PASS | `CodeGroundedPhase2Test`, `f872441c` |
| JCM phase | PASS | `CodeGroundedPhase3Test`, `f0663ddf` |
| CArtAgO phase | PASS | `CodeGroundedPhase4Test`, `52a60351` |
| Moise phase | PASS | `CodeGroundedPhase5Test`, `3339961b` |
| Cross-dimension phase | PASS | `CodeGroundedPhase6Test`, `82b017c2` |
| Native runtime phase | PASS | `CodeGroundedPhase7Test`, `da561665` |
| Native authority/verification | PASS | `ProductionAuthorityPhase8Test`, `496f87f9` |
| Case-study acceptance | PASS (bounded scope) | `Phase9CaseStudyAcceptanceTest 2/2`, `bfb22e4b` |
| Mapping Inspector | PASS | `JaCaMoWorkbenchPanelTest 13/13`, `3f649c90` |
| Export/reproducibility | PASS (implemented subset) | `CodeGroundedExportTest 2/2`, `4a2048b8` |
| Packaging/release | PASS | reactor `309/309`, integration `7/7`, `8b1e0e82` |
| Shadow comparison | PASS | `HelloShadowComparisonTest`, `4be438e2` |
| Runtime safety | PASS (implemented subset) | `RuntimeFoundationTest 26/26`, `ebbeca6b` |

## Case-study evidence

- **Hello World:** official JCM/Jason/Moise facts are materialized through the
  native path; native OCL and same-session verification pass.
- **Auction:** official organization/scheme/cardinality facts are retained;
  no CArtAgO artifact or live deadline claim is fabricated when the static
  snapshot does not provide it. Full standalone original plan/deadline
  equivalence remains explicitly unsupported.
- **House Building:** JCM-only input remains fail-closed when organization,
  CArtAgO, or OS facts are absent. A separately loaded official OS snapshot
  proves the supported role/cardinality and sequence/parallel subset; those
  facts are not silently inserted into the JCM-only pipeline.

## Production call graph

```text
DefaultJaCaMoFacade.importProject
  -> OfficialProjectLoader/OfficialProjectAdapter (JaCaMo-side process)
  -> ModelSnapshot.semanticContract
  -> CodeGroundedNativePipeline.build
  -> NativeUseModelBuilder.build
  -> NativeUseStateBuilder.build
  -> NativeUseExporter.export + structural recompile check
  -> NativeUseSessionActivator.activate
  -> Session.setSystem(nativeSystem)
  -> NativeConstraintInstaller / NativeRuntimeProjector / verification
  -> JaCaMoWorkbenchPanel view records
```

The `MSystem` created by the native pipeline is the system used by Session,
formal state reporting, runtime mutation, OCL, and verification. The inspector
and exported trace are read-only evidence surfaces.

## Known limitations and remaining work

The unchecked task items are deliberate: optional `.cmd`; exported JaCaMo,
Jason, CArtAgO, Moise, and USE component-version manifest; bounded semantic
snapshot hard limits; trace-size telemetry; optional inspector target
navigation; optional native `MOperation` projection; and destructive legacy
cleanup pending explicit approval. Live claims that lack official/runtime
evidence remain `UNKNOWN`, `UNAVAILABLE`, or `UNSUPPORTED`.

Historical V2/Ecore/golden artifacts are not modified. The release candidate
still carries frozen V2 resources for reproducibility, while packaging and
authority tests prove that the native implicit path and separate adapter are
the production route.

## Rollback and audit

The phase checkpoints are ordinary Git commits listed above. Generated
`target/` output is untracked. The user's pre-existing `agent.md`, deleted
`tasks/task-01.md`, three design/mapping files, and generated target output
were intentionally not included in the phase commits.
