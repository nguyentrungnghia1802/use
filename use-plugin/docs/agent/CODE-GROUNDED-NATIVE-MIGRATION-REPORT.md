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
| Packaging/release | PASS | reactor `315/315`, integration `7/7`, `1f622231` |
| Shadow comparison | PASS | `HelloShadowComparisonTest`, `4be438e2` |
| Runtime safety | PASS (implemented subset) | `RuntimeFoundationTest 26/26`, `ebbeca6b` |

## Baseline and invariant audit — 2026-09-29

The preserved audit baseline is recorded separately from the native migration
results. `use-plugin/docs/architecture-realignment/16-test-strategy.md` records
the 2026-09-26 reactor result as `use-core 12/12`, `use-gui 1/1`, and
`use-plugin 228` tests with three pre-existing failures and no errors/skips:
`GoldenPipelineTest` digest mismatch, `ConstraintClosureTest` expected `2` but
got `0`, and `InstanceMaterializationTest` golden digest mismatch. These are
classified as dirty-frontend baseline failures; no production code or golden
was changed to hide them. The current checkout remains intentionally dirty
because the user's `agent.md`, deleted `tasks/task-01.md`, three design/mapping
documents, and generated `target/` are preserved outside the phase commits.

Frozen V2/Ecore/OCL/mapping/golden/freeze-manifest and historical evidence
paths have no migration diff at the checkpoint. `V2FinalFreezeTest`,
`CompatibilityManifestTest`, `V2HardeningAuditTest`, and the release tests keep
their exact hashes/labels and prove the native path does not mutate or ship
the frozen authority as its semantic implementation.

The native isolation audit is backed by `NativeSemanticAdapterTest`,
`ProductionAuthorityPhase8Test`, `LegacyV2OclIsolationTest`,
`CodeGroundedPhase6Test`, `CodeGroundedNegativeTest`, `CodeGroundedPhase9Test`,
`NativeUseSessionActivationTest`, `NativeRuntimeFacadeIntegrationTest`, and
`JaCaMoWorkbenchPanelTest`. Together they cover official-API authority,
exact-ID-only binding, opaque runtime incarnations, collision rejection,
fail-closed unavailable/evidence-only facts, no case-specific production
branch, no second native `MSystem`, and same-session runtime/OCL/verification
use. `CodeGroundedIdentitySafetyTest` adds direct delimiter-safe opaque identity
and invalid-component regression coverage (`2/2`).

The post-checkpoint full reactor gate passed with contract `11/11`, official
adapters `17/17`, use-core `12/12`, use-gui `1/1`, use-plugin `315/315`, and
integration/release `7/7`, with zero failures/errors/skips. Checkpoint:
`1f622231`.

Capability-gated native constraints are now reported explicitly. The native
planner emits the C08 live-observable-property rule as
`SKIPPED_CAPABILITY`; `NativeConstraintInstaller.InstallationResult` carries
the skipped spec without installing an OCL invariant, and the facade exposes a
`SKIPPED` verification result with `SKIPPED_CAPABILITY`. `NativeConstraintInstallerTest`
passes `2/2`, including the no-OCL-installation regression.

Exact native operation projection is capability-gated rather than inferred.
`NativeUseModelBuilder.NativeOperationPlan` requires the exact declaring class,
method name, parameter types, return type, arity, and varargs flag to resolve
through reflection before creating a concrete artifact subtype and `MOperation`.
`NativeMOperationProjectionTest` passes `2/2`: the exact signature is projected
and exported with a stable structural hash, while a non-exact method remains a
structural `Operation` and is traced as not projected. No case-study name or
V2 model is used by this path.

The full post-checkpoint reactor rerun passed with contract `11/11`, official
adapters `17/17`, use-core `12/12`, use-gui `1/1`, use-plugin `315/315`, and
integration/release `7/7`, with zero failures/errors/skips.

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
navigation; non-exact/dynamic native `MOperation` projection; and destructive
legacy cleanup pending explicit approval. Live claims that lack official/runtime
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
