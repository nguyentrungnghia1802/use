# Phase 25 decision package

Status: initial blocked package superseded by **D25-01 ACCEPTED** below, 2026-09-19.
The pending-choice section records the earlier state, not the current decision.
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


## D25-01 accepted — final canonical V1, 2026-09-19

Authority: the user's continuation instruction explicitly delegates P25.1 and
ordinary research decisions to the agent after an evidence-first audit. This
supersedes the earlier user-input blocker; it does not constitute final user
acceptance of the entire project.

Decision: retain the exact frozen Ecore and Structural Mapping V1 as the final
canonical target for this roadmap. Do not create V2 or edit V1. Final here means
versioned project baseline for the supported scope, not universal completeness
of JaCaMo semantics or a ban on future research versions.

### Technical/research audit

Reparsed every EClass/feature/inheritance edge with the read-only P21 inventory:
[evidence/phase25/metamodel-audit.json](evidence/phase25/metamodel-audit.json).
Counts recompute to 37/67/63/14; the chosen target diff is empty. Structural
Mapping V1's seven projections and exact source identities are validated by
MappingTransformationTest, including wrong schema, byte-snapshot consistency,
fingerprint changes, target changes, naming collisions and actual USE compilation.

Inspected vision/scope success criteria, metamodel baseline, mapping contract,
research boundaries, roadmap and all P26–28 requirements. Vision success criterion
1 explicitly names audited/frozen V1. Roadmap P26 explicitly permits retaining
V1. Searches of checked-in Core, research and project documentation and thesis
report markdown found no mandatory V2 artifact or requirement that demands a
new canonical metamodel. This conclusion is limited to repository requirements;
it is not a claim about unpublished external thesis requirements.

Read upstream research baseline/architecture/capability/identity/candidate matrix,
machine-readable inventory and source manifest, and reconciled them with Phase 16
implementation reconciliation and Phase 20/23/24 evidence. The upstream 1.3.1 /
Jason 3.3.2 research snapshot is not the tested 1.3.0 / Jason 3.3.0 target. No pin
upgrade is implied. RuntimeMappingLoader/Validator, V1RuntimeBindingAdapter,
RuntimeSemanticAction, RuntimeMutationEngine and RuntimeMirrorService show that
selectors, exact identity, operation lifecycle, ordered mutation and snapshots
are separate from structural EClass vocabulary. RuntimeVerificationEngine's
checkpoint/pre-state/history contract and CrossDimensionalVerifier distinguish
current projected state from declared structural links and trace observations.

| Remaining requirement | Representation/evidence | Does V2 solve an unmet requirement? |
|---|---|---|
| P26 structural coverage, IR, trace, generated .use/.cmd | V1 plus VP001–VP007; exact IDs, text/direct pipeline and compile gates | No missing structural target found in supported fixtures |
| P26 property/operation mirror and PRE/POST | VP002 attributes / VP003 operations, exact trace and OpId correlation | No; lifecycle belongs to runtime services |
| P26 organisation / Jason state | Instance-sensitive observations retained in RuntimeTrace; structural links checked separately | V1 does not represent all dynamic instances; a V2 would need new semantics, not just new names |
| P26 normative/translation reconciliation | Preserve Norm structure, derived OE facts; safe guard subset and authored OCL | General NPL/deontic/effect equivalence is unproven regardless of EClass count |
| P27 multi-case and migration | Auction + Counter Team; synthetic alternate-target RuntimeMigrationTest | Existing two-case reuse and adapter seam work without changing V1 |
| P27 hardening/security/package/reproducibility | Service/resource tests, clean reactor and installed package gates | Engineering gates, independent of a new metamodel |
| P28 acceptance and unsupported boundary report | Explicit COMPLETE / SUPPORTED_SUBSET_COMPLETE / EXPLICITLY_UNSUPPORTED / OUT_OF_SCOPE rows | Task explicitly accepts unsupported boundaries; not authorization to hide missing implementation |
| Standalone launcher/board integration | Phase 20 runtime OS and board adapter limitation | V2 alone cannot validate OS/NPL semantics or supply a connector |

The broader vision's dynamic mental/organisation verification remains a supported
subset, not fully realized. No claim that every JaCaMo fact is in MSystemState is
made. Preserving these explicit limitations is consistent with P28's final status
contract and the user's instruction to retain standalone/NPL boundaries.

### Alternatives, necessity and risks

A. **Freeze V1 (selected):** preserves reconstructed source provenance, tested
projection semantics and exact identities. Risk: consumers may confuse structural
Norm/Role/Goal with runtime instances. Mitigation: explicit trace-only dispositions,
final mapping compatibility report and unsupported diagnostics; no promotion of
observation to OCL state. Evidence: two real component cases and mutation/negative
controls, rather than convenience or compatibility alone.

B. **Create V2:** could add instance-sensitive roles/missions/goals or a trace model,
but no approved requirement needs those to become structural objects in this
roadmap. It would require identity/cardinality/lifecycle contracts and connector
semantics not supplied merely by a new Ecore. Risks: inventing research semantics,
new OCL equivalence assumptions, migration and fixture churn. Rejected absent a
necessary, independently specified behavior that current trace/projections cannot
support within the declared subset. P21 migration tools remain available.

C. **Modify V1 in place:** would break frozen hash/identity contracts and provenance
without demonstrated structural defect. Rejected. A demonstrated future defect
requires explicit versioned reconciliation, not hash-only acceptance.

Backward compatibility: canonical Ecore/mapping/schema/freeze bytes, SemanticIds,
fixtures, examples and existing generated structural/OCL goldens remain unchanged.
Phase 26 will version the runtime contract explicitly and reject incompatible old
draft documents with a stable diagnostic rather than reinterpret them silently.
No new unsupported feature is enabled. New runtime freeze resources and package
checks are necessary; structural migration is an exact empty diff.

P25.2 decision: no LOSSY or unproven normative semantics enabled; no new
thesis-specific rule invented. P25.3: Counter Team remains the second case.
P25.4: no outstanding external input for Phase 26. Exact migration impact is the
empty final-target diff plus runtime metadata/version/freeze/package reconciliation.
Focused audit command (MappingTransformationTest, RuntimeMigrationTest,
RuntimeMappingTest, ConstraintOclTest, CounterTeamIntegrationTest,
LiveJaCaMoAuctionIntegrationTest): **27/27 PASS**, zero failures/errors/skips.
Full reactor and Git closure recorded after their gates.

Full reactor on this unchanged production baseline: 298/298 PASS, zero failures/errors/skips (phase25-reactor.log, 2026-09-19). Migration tooling: 2/2 PASS. Phase 25 decision gate complete; runtime freeze is Phase 26 work.
