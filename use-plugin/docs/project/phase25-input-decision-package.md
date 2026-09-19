# Phase 25 decision package

Status: **BLOCKED on final metamodel research decision**, 2026-09-19.
Baseline inspected: `614160114a0aa25f8dcc1e3998c72b24026fa214` on main,
also confirmed by `git ls-remote origin refs/heads/main`. Working tree was clean.
Classification: TEST/EVIDENCE CHANGE and documentation only. No contract change.

## Phase 24 evidence verification

Inspected the preserved `target/phase-evidence/phase24-reactor.log`: BUILD SUCCESS,
298 tests (core 12+1, GUI 1+129, plugin 152+3), zero failures/errors/skips.
The preserved `phase24-postmerge.log` records 13 passing tests, zero
failures/errors/skips, for ConstraintClosureTest, ConstraintOclTest,
CounterTeamIntegrationTest and LiveJaCaMoAuctionIntegrationTest. Implementation
commit `8a3d8040` is followed only by the documentation closure `61416011`.
These are verified historical logs, not a newly run reactor.
Counter evidence summary retains 33 accepted events and SUPPORTED_SUBSET_COMPLETE.
Metrics commit `0e9474a0` remains an ancestor. Core contract diff from Phase 16
closure `7a848105` is empty. No regression warrants reopening Phases 16–24.

## P25.1 prepared input

Current frozen V1: 37 classes, 67 declared attributes, 63 references, 14 inheritance
edges, seven verification projections. All 37 names remain represented by
`semantic/MetamodelKind.java`; absence from one runtime scenario is not proof that
a structural concept is unused. Canonical SHA-256:

- Ecore: `c0aafab786c5ff3fcb468aeaf1b18b62865292e6590ffca2b9b2e962a9067fe7`
- Mapping V1: `3279f46cb128a252247c2667cd5ceb9329366162487dbd864950792aa2cd61b8`

Runtime mechanics actually require exact object, attribute, association and
operation targets; operation correlation/pre-state; ordered events; authoritative
snapshots and stream identity; trace-only/unsupported outcomes. These are already
separated from EClass names by RuntimeSemanticAction, RuntimeBindingContract and
RuntimeTargetResolver. Current V1RuntimeBindingAdapter anchors object/relation
targets to classMappings/referenceMappings and attributes/operations to VP002/VP003.
Jason and Moise observations without proven state slots remain trace evidence.
No additional EClass is proven necessary by the two current supported cases.

Review candidates, **not authorized deletions**: Norm/Group/Role/Scheme inheriting
Organisation, and TriggeringEvent inheriting Action, require research provenance
review before simplification. ObsProperty.initialValue, AbsOperation.paramName and
TriggeringEvent.addAndDel remain unresolved source facts, not fields to invent.
No exhaustive dead-metamodel claim is made. The minimum engineering recommendation
is to retain V1 and its projections for the currently proven verification subset.

### Migration impact matrix

| Layer / exact files or components | V1 final | New canonical V2 |
|---|---|---|
| Core/Metamodel/JaCaMo-Metamodel.ecore; Core/Mapping/jacamo-use-mapping-v1.json and schema | Preserve bytes; audit final identity | Exact structural diff, reviewed new version and mapping coverage/freeze |
| semantic/MetamodelKind.java; extraction; mapping/TransformationPlanner.java | Preserve semantics | Reconcile changed kinds, ownership and projection targets |
| trace/TraceBuilder.java; TraceIndex; TraceRuntimeTargetAdapter | Preserve exact identity | Rebuild exact trace targets; reject unresolved migration |
| runtime/V1RuntimeBindingAdapter.java; runtime mapping draft resource | Audit existing anchors; finalize version/status in Phase 26 | Rebind reviewed final anchors; retain source selectors/actions where unchanged |
| OCL profiles; generated .use/.cmd; golden fixtures | Regenerate and compare | Rebind contexts/navigation, regenerate and compile/type-check |
| RuntimeEvent, RuntimeTrace, connectors, queue | No semantic change expected | No change unless new evidence proves source semantics changed |
| RuntimeMigrationTest, RuntimeMappingTest, MappingTransformationTest | Final compatibility and negative gates | New target fixtures plus old source-contract regressions |
| ConstraintOclTest, CounterTeamIntegrationTest, LiveJaCaMoAuctionIntegrationTest | Revalidate existing supported subset | Revalidate target-specific mirror/OCL behavior |

Paths above are relative to `use-plugin` or its Java package
`src/main/java/org/tzi/use/plugins/jacamo`. An exact feature-level V1-to-V2 diff
cannot be produced before a V2 artifact exists; no rename or deletion is inferred.
`tools/metamodel_diff.py` is ready and read-only. Fresh validation on 2026-09-19:
`python -m unittest discover -s use-plugin/tools -p test_metamodel_diff.py`: **2 PASS**.
Running the tool with canonical V1 as both inputs returns empty changes and rename
candidates. This is a tooling control, not a V2 migration result.

## Decision D25-01 — pending user research choice

Question: is frozen V1 the final canonical target for this project, or is an
externally supplied/approved V2 required, and if so does it replace V1 or supplement
it as a verification profile?

Facts: tracked Ecore inventory contains only V1; roadmap section 25 and task P25.1
reserve this choice to the user. The continuation request preserves the baseline
but does not explicitly designate it the final research metamodel. Source/tests
can establish compatibility, not choose the thesis's final metamodel.

Safe default while pending: preserve V1 and keep runtime mapping draft; do not
freeze a purported final mapping. Recommendation: select V1 final if no external
research requirement mandates V2. Options and impact:

1. **V1 final**: proceed with Phase 26 V1 reconciliation and runtime mapping freeze.
2. **V2 replaces V1**: provide approved Ecore/provenance, then execute the matrix.
3. **V2 supplementary profile**: provide its approved scope/relationship; keep V1
   canonical and reconcile only the explicitly approved verification profile.

No choice has been recorded as accepted. Phase 25 exit and sequential Phase 26
finalization are blocked on this decision. Phase 27/28 final-target acceptance
cannot substitute for it. This is a research input, not routine implementation
permission. No phase is falsely marked complete.

## P25.2 / P25.3 disposition

No additional mandatory research-semantic decision was found in current scope.
Retain NOT_EMITTED for unapproved LOSSY/SOUND_SUBSET rules, unsupported native
effects/arithmetic and Jason context translation; retain explicit normative gaps.
No requested thesis-specific rule or alternative trace representation requires
invented semantics. Existing boundary tests remain authoritative (Phase 23/24).
Counter Team already supplies case 2, so no external case selection is required.

Standalone Phase 20 limitations remain unchanged: checked-in static organisation
XML is not a validated launcher OS; in-process harness-driven operations do not
prove autonomous Agent -> Artifact -> Organisation execution; organisation-board
adapter/full NPL lifecycle and full-project E2E remain unproven or unsupported.
See `phase20-runtime-evidence.md` and `phase24-translation-multicase-evidence.md`.

## Documentation and continuation

Reviewed task, roadmap, metamodel baseline, Phase 20/21/24 evidence and current
binding/action/kind source. Updated this package and Phase 25 task progress only.
No production/test/resource changes; no new reactor claim. After D25-01 is supplied,
record its date/source, finish P25.4, and execute Phase 26 through Phase 28 in order.
