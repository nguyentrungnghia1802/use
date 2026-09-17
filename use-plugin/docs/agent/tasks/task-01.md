# USE JaCaMo Plugin — Full Implementation Task Plan

> **HISTORICAL_EVIDENCE:** This task plan records the original v1.0.0 implementation checklist (Phases 0–15). It has been archived from `docs/agent/task.md` to `docs/agent/tasks/task-01.md` and is retained for provenance. It is not the current HEAD status checklist for v1.0.1 or subsequent roadmaps. For current onboarding and active documentation, see [00-README.md](../../project/00-README.md).
>
> Checklist này bao phủ toàn bộ dự án từ baseline freeze đến runtime verification, hardening và release. Không bỏ qua phase vì "để sau". Thứ tự có thể điều chỉnh chỉ khi dependency kỹ thuật bắt buộc, nhưng mọi task vẫn phải hoàn thành trước final release.

## Global Definition of Done

- [x] Canonical Ecore baseline audited and frozen.
- [x] Mapping V1 structurally complete, internally consistent, ambiguity-free.
- [x] Plugin loads in pinned USE.
- [x] JaCaMo project import works from `.jcm`.
- [x] Jason/CArtAgO/Moise extraction works for supported project.
- [x] Semantic model + trace complete.
- [x] USE model/state generation works.
- [x] Constraint translation supported subset works.
- [x] Core and case-study OCL work.
- [x] Binding/resolver ambiguity path works.
- [ ] Runtime adapter works against real JaCaMo.
- [x] Runtime OCL detects violations.
- [x] Auction E2E positive and negative scenarios work.
- [ ] Full tests/build/docs/release complete.

---

# Phase 0 — Baseline Freeze

Read:
- `docs/project/04-jacamo-metamodel-baseline.md`
- `docs/project/05-metamodel-mapping-contract.md`
- `docs/project/16-research-evidence-boundaries.md`

## P0.1 Metamodel audit
- [x] Parse canonical `JaCaMo-Metamodel.ecore` programmatically.
- [x] Assert expected EClass count from file.
- [x] Enumerate all EAttributes with owner/type/bounds/default.
- [x] Enumerate all EReferences with owner/target/bounds/containment.
- [x] Enumerate all inheritance edges.
- [x] Extract unresolved annotations.
- [x] Record Ecore SHA-256.
- [ ] Create/update `metamodel-audit.md`.
- [ ] Create/update `metamodel-freeze-manifest.json`.
- [x] Add tests that fail on unnoticed structural drift.

## P0.2 Mapping schema
- [x] Create `jacamo-use-mapping.schema.json`.
- [x] Require qualified source identity.
- [x] Define target kinds.
- [x] Define multiplicity structure.
- [x] Define association/composition end schema.
- [x] Define projection schema.
- [x] Define unresolved/review flag schema.
- [x] Define mapping/evolution metadata.
- [x] Add schema validation test.

## P0.3 Mapping coverage audit
- [x] Verify every EClass has exactly one structural mapping.
- [x] Verify every declared EAttribute has mapping.
- [x] Verify every EReference has mapping.
- [x] Verify every inheritance edge has mapping.
- [x] Verify source owner exists.
- [x] Verify source feature exists.
- [x] Verify reference target matches Ecore.
- [x] Verify containment matches Ecore.
- [x] Verify forward multiplicity matches Ecore.
- [x] Verify target association names unique.
- [x] Verify source keys unique.
- [x] Reject unqualified `operation`-style IDs.
- [x] Explicitly preserve unresolved visible attributes as unresolved, not guessed.
- [x] Audit all review-flag inheritance entries.

## P0.4 Projection audit
For each VP001–VP007:
- [x] source defined;
- [x] target defined;
- [x] need/rationale defined;
- [x] direction defined;
- [x] lossless/lossy status defined;
- [x] assumptions defined;
- [x] fallback defined;
- [x] trace requirement defined;
- [ ] test case defined.

## P0.5 Freeze Mapping V1
- [x] Generate mapping SHA-256.
- [ ] Create `mapping-audit.md`.
- [ ] Create `mapping-freeze-manifest.json`.
- [x] Record Ecore fingerprint expected by mapping.
- [ ] Mark status `LOCKED_BASELINE_V1`.
- [ ] Run full audit from clean checkout.
- [x] Commit and merge Phase 0.

Acceptance:
- [x] "Mapping V1 is canonical, structurally complete for the frozen metamodel, internally consistent, and has no known mapping ambiguity."

---

# Phase 1 — USE Plugin Skeleton

Read:
- `docs/project/02-system-architecture.md`
- `docs/project/03-repository-structure.md`
- `docs/project/12-plugin-ui-workflow.md`
- current USE plugin examples/source

## P1.1 Pin environment
- [x] Record USE commit/version.
- [x] Record Java version.
- [x] Record Maven version.
- [x] Record JaCaMo target version.
- [x] Add compatibility manifest.

## P1.2 Module
- [x] Create plugin module following current USE plugin mechanism.
- [x] Add minimum plugin metadata/resources.
- [x] Add dependency on allowed USE APIs.
- [x] Ensure root Maven build sees module if architecture requires it.
- [x] Add unit test module skeleton.

## P1.3 Load smoke test
- [x] Package plugin JAR.
- [x] Install/copy into USE plugin path.
- [x] Start USE.
- [x] Verify plugin discovered.
- [x] Add one harmless command/menu action.
- [x] Add automated smoke test where feasible.

## P1.4 Service boundaries
- [x] Define plugin facade.
- [x] Define import service interface.
- [x] Define verification service interface.
- [x] Define runtime service interface.
- [x] UI calls facade only.

Acceptance:
- [x] Plugin loads without patching unrelated USE behavior.
- [x] Clean build passes.

---

# Phase 2 — Project Discovery and Semantic IR

Read:
- `docs/project/06-semantic-model-and-extraction.md`
- `docs/project/02-system-architecture.md`

## P2.1 Project model
- [x] Implement project root abstraction.
- [x] Implement source file abstraction.
- [x] Implement source hash.
- [x] Implement source span.
- [x] Implement diagnostic model.

## P2.2 Semantic identity
- [x] Implement canonical semantic ID format.
- [x] Add deterministic ID tests.
- [x] Add collision tests.

## P2.3 Semantic IR
- [x] Implement root `JaCaMoSemanticModel`.
- [x] Implement Agent-dimension nodes required by Ecore.
- [x] Implement Environment-dimension nodes required by Ecore.
- [x] Implement Organisation-dimension nodes required by Ecore.
- [x] Implement generic attributes/references representation where appropriate.
- [x] Preserve metamodel kind for every node.
- [x] Preserve source provenance.

## P2.4 JCM entry loader
- [x] Accept `.jcm` path.
- [x] Validate project root.
- [x] Resolve include directives.
- [x] Resolve source paths.
- [x] Build project graph.
- [x] Detect cycles/path errors.
- [x] Add fixtures.

Acceptance:
- [x] Minimal `.jcm` project can be discovered without USE dependency.

---

# Phase 3 — Dimension Parsers and Resolver

Read:
- `docs/project/06-semantic-model-and-extraction.md`
- relevant JaCaMo/Jason/CArtAgO/Moise docs/source only

## P3.1 JCM parser
- [x] Parse MAS/application identity.
- [x] Parse agents.
- [x] Parse workspace declarations.
- [x] Parse artifact instances/types/parameters.
- [x] Parse organisation declarations.
- [x] Parse group/scheme runtime declarations.
- [x] Parse focus/roles where represented.
- [x] Parse source paths/platform parameters needed by model.
- [x] Preserve source spans.
- [x] Add positive/negative fixtures.

## P3.2 Jason parser adapter
- [x] Select parser strategy using current Jason APIs/grammar.
- [x] Parse initial beliefs.
- [x] Parse rules.
- [x] Parse goals.
- [x] Parse plans.
- [x] Parse triggering events.
- [x] Parse contexts.
- [x] Parse bodies/body terms.
- [x] Classify external/internal actions.
- [x] Parse messages/mental notes where supported.
- [x] Preserve expressions as typed AST or source expression nodes.
- [x] Add syntax error recovery diagnostics.
- [x] Add fixtures for supported syntax.

## P3.3 CArtAgO extractor
- [x] Resolve Artifact Java type from JCM.
- [x] Resolve source/classpath.
- [x] Extract Artifact identity.
- [x] Extract observable property declarations where statically knowable.
- [x] Extract operation signatures.
- [x] Extract parameter names/types.
- [x] Extract guard operations.
- [x] Extract internal operations.
- [x] Extract relevant signal/await declarations.
- [x] Preserve unresolved dynamic behavior explicitly.
- [x] Avoid executing arbitrary project code during static import.
- [x] Add fixtures.

## P3.4 Moise parser
- [x] Parse structural specification.
- [x] Parse roles.
- [x] Parse groups/subgroups.
- [x] Parse links/formation constraints.
- [x] Parse functional specification.
- [x] Parse schemes.
- [x] Parse missions.
- [x] Parse OGoals/OPlans.
- [x] Parse normative specification.
- [x] Parse Norm role/mission/type/time constraint.
- [x] Link JCM organisation instances to spec declarations.
- [x] Add fixtures.

## P3.5 Cross-file resolver
- [x] Build symbol index.
- [x] Resolve explicit references.
- [x] Resolve owner-qualified names.
- [x] Resolve source paths.
- [x] Resolve action-operation structural links when source provides enough semantics.
- [x] Resolve percept-belief link when represented.
- [x] Resolve OGoal-Goal link when represented.
- [x] Record unresolved/ambiguous references.
- [x] Never fuzzy auto-resolve.
- [x] Add duplicate-name tests.

Acceptance:
- [x] Auction project produces coherent semantic model across all three dimensions.

---

# Phase 4 — Mapping Engine and USE Structural Transformation

Read:
- `docs/project/05-metamodel-mapping-contract.md`
- `docs/project/08-use-transformation.md`

## P4.1 Mapping loader
- [x] Load JSON.
- [x] Validate schema.
- [x] Validate Ecore fingerprint.
- [x] Validate source keys against canonical Ecore.
- [x] Return typed mapping model.
- [x] Add bad mapping tests.

## P4.2 Transformation plan
- [x] Define target class spec.
- [x] Define target attribute spec.
- [x] Define association/composition spec.
- [x] Define inheritance spec.
- [x] Define operation projection spec.
- [x] Define projection diagnostic.
- [x] Generate deterministic plan from semantic model + mapping.

## P4.3 USE naming
- [x] Implement identifier sanitizer.
- [x] Preserve original name in trace.
- [x] Handle collisions deterministically.
- [x] Add reserved word tests.

## P4.4 Structural model
- [x] Generate mapped base classes.
- [x] Generate attributes.
- [x] Generate inheritance.
- [x] Generate associations/compositions.
- [x] Preserve multiplicities.
- [x] Validate no duplicate target names.

## P4.5 VP001 concrete Artifact type
- [x] Generate concrete Artifact subclass when resolved.
- [x] Stable naming from fully-qualified Java type.
- [x] Collision test.

## P4.6 VP002 observable property
- [x] Project typed state only when name/type resolved.
- [x] Keep structural ObsProperty representation.
- [x] Diagnostic when projection unsupported.

## P4.7 VP003 operation
- [x] Project resolved operation signature.
- [x] Keep structural operation object representation.
- [x] Link trace between both.
- [x] Diagnostic when unresolved.

## P4.8 VP004–VP007
- [x] Preserve ExternalAction-operation cross-dimension.
- [x] Preserve ObsProperty-belief cross-dimension.
- [x] Preserve OGoal-Goal cross-dimension.
- [x] Preserve Norm structurally without auto OCL deontic compilation.

Acceptance:
- [x] Deterministic transformation plan for Auction.
- [x] Generated USE model compiles.

---

# Phase 5 — Instance Materialization

Read:
- `docs/project/08-use-transformation.md`
- `docs/project/09-traceability-binding-resolver.md`

## P5.1 Object plan
- [x] One target object per mapped semantic instance where appropriate.
- [x] Deterministic object names.
- [x] Correct target class.

## P5.2 Values
- [x] Materialize resolved scalar values.
- [x] Preserve unset/undefined.
- [x] Do not invent Ecore clipped defaults.

## P5.3 Links
- [x] Composition links.
- [x] Association links.
- [x] Correct association identity.
- [x] Duplicate link prevention.

## P5.4 Text backend
- [x] Generate `.use`.
- [x] Generate initial `.cmd`.
- [x] Stable ordering.
- [x] Golden tests.

## P5.5 Direct USE backend
- [x] Build/load `MModel` through supported USE API.
- [x] Create/update `MSystemState`.
- [x] Ensure same semantics as text backend.
- [x] Contract tests compare outputs/state.

## P5.6 Initial validation
- [x] USE structure check.
- [x] Multiplicity check.
- [x] Initial invariant check.
- [x] Diagnostics with trace.

Acceptance evidence (2026-09-15): JaCaMo Verification Profile V1 is applied as a separate effective semantic layer;
the frozen Ecore and Mapping V1 are unchanged. `[OUR-EXT]` VSP001-VSP004 remove concrete verification inheritance
from `Organisation`, and `[SEMANTIC-CLARIFICATION]` VSP005 makes R047 optional only in the verification plan.
Jason extraction gives each Action one composition owner through R053 without duplication, Moise/JCM extraction
produces source-backed R007/R015/R019/R020/R021 links, and exact Jason goal triggers produce R048. Deterministic
text and direct USE backends agree; Auction passes structure, multiplicity, and initial invariant checks without
materialization ERROR diagnostics or fabricated binding links.

Acceptance:
- [x] Imported Auction initial state matches semantic model and passes expected baseline/verification checks.

---

# Phase 6 — Constraint Extraction, Translation and OCL

Read:
- `docs/project/07-constraint-translation-and-ocl.md`
- `docs/project/16-research-evidence-boundaries.md`

## P6.1 Constraint IR
- [x] Implement expression node types.
- [x] Implement context binding.
- [x] Implement variable/type environment.
- [x] Implement translation status.
- [x] Implement provenance/assumptions/dependencies.

## P6.2 Jason context extractor
- [x] Convert supported context expressions to Constraint IR.
- [x] Resolve variables/references.
- [x] Mark unsupported constructs.
- [x] Add exact/subset/unsupported tests.

## P6.3 CArtAgO guard extractor
- [x] Resolve guard owner/operation.
- [x] Convert supported condition to Constraint IR.
- [x] Link projected state.
- [x] Mark arbitrary Java semantics unsupported instead of guessing.

## P6.4 OCL generator
- [x] Generate valid context.
- [x] Generate logical/comparison/arithmetic expressions.
- [x] Generate property navigation.
- [x] Generate collection expressions required by supported subset.
- [x] Escape identifiers/literals correctly.
- [x] Deterministic formatting.
- [x] Provenance sidecar/manifest.

## P6.5 Operation preconditions
- [x] Attach translatable guards/conditions to projected operations.
- [x] Verify USE compiles precondition.

## P6.6 Postconditions
- [x] Support explicit/source-backed postconditions only.
- [x] Support `@pre` when semantic effect known.
- [x] Do not infer arbitrary Java implementation effects.

## P6.7 Core OCL
- [x] Create reusable cross-dimensional OCL profile.
- [x] Each invariant has rationale/evidence.
- [x] Avoid duplicate checks already guaranteed by USE multiplicity unless diagnostic purpose documented.

## P6.8 Case OCL loader
- [x] Load project `.ocl` profile.
- [x] Validate compile/type.
- [x] Track origin.

## P6.9 Norm boundary
- [x] Ensure Norm is not auto-converted to OCL obligation invariant.
- [x] Add regression test preventing accidental deontic collapse.

Acceptance:
- [x] Translated/core/case OCL compile.
- [x] Positive and negative fixture results match expectations.

---

# Phase 7 — Traceability, Resolver and Optional Binding

Read:
- `docs/project/09-traceability-binding-resolver.md`

## P7.1 Trace schema/model
- [x] Define trace JSON schema.
- [x] Implement in-memory trace index.
- [x] Record class/attribute/association/object/operation traces.
- [x] Persist trace.

## P7.2 Runtime key support
- [x] Add runtime identity fields without requiring live runtime.
- [x] Lookup indexes by semantic ID/runtime ID/USE ID.

## P7.3 Resolver
- [x] Exact ID.
- [x] Explicit reference.
- [x] Owner-qualified.
- [x] Unique typed scope.
- [x] Explicit binding.
- [x] Failure.

## P7.4 Binding schema
- [x] Create JSON schema.
- [x] Validate canonical IDs.
- [x] Persist user choice.
- [x] Add reason/provenance.
- [x] Detect stale binding after project change.

## P7.5 Ambiguity tests
- [x] Same operation name on two artifacts.
- [x] Same local symbol in multiple agents.
- [x] Multiple organisation instances.
- [x] Invalid target kind.
- [x] Binding resolves exactly one target.

Acceptance:
- [x] No formal resolution depends on fuzzy name matching.

---

# Phase 8 — Verification Service and Offline Reporting

Read:
- `docs/project/11-verification-engine.md`
- `docs/project/12-plugin-ui-workflow.md`

## P8.1 Constraint registry
- [x] Load translated constraints.
- [x] Load core OCL.
- [x] Load case/user OCL.
- [x] Record origin/dependencies.

## P8.2 Full check
- [x] Check structure/multiplicity.
- [x] Check all invariants.
- [x] Capture results.

## P8.3 Operation check
- [x] Support pre-state.
- [x] Evaluate preconditions.
- [x] Evaluate postconditions.
- [x] Preserve correlation.

## P8.4 Result/diagnostic
- [x] PASS/FAIL/ERROR/SKIPPED.
- [x] Context object.
- [x] OCL source.
- [x] trace back to JaCaMo.
- [x] export JSON/Markdown.

## P8.5 Offline Auction E2E
- [x] Valid project passes expected checks.
- [x] Violation fixture fails expected constraint.
- [x] Report points to correct JaCaMo source.

Acceptance:
- [x] Complete design-time workflow usable without runtime.

---

# Phase 9 — Runtime Adapter Foundation

Read:
- `docs/project/10-runtime-adapter.md`
- reference current USE `plugin_monitor` architecture/source

## P9.1 Connector abstraction
- [x] Define lifecycle.
- [x] Define capability reporting.
- [x] Define snapshot API.
- [x] Define event subscription API.

## P9.2 Normalized events
- [x] Implement event types.
- [x] sequence/timestamp.
- [x] correlation ID.
- [x] payload schema.
- [x] serialization test.

## P9.3 Mutation engine
- [x] CREATE.
- [x] DESTROY.
- [x] SET.
- [x] INSERT.
- [x] DELETE.
- [x] OP_ENTER.
- [x] OP_EXIT.
- [x] OP_FAIL.

## P9.4 Event queue
- [x] Single correctness-first ordered queue.
- [x] Backpressure metrics.
- [x] No silent drop.
- [x] graceful stop.

## P9.5 Synthetic connector
- [x] Replay JSON event stream.
- [x] Full snapshot.
- [x] disconnect/reconnect.
- [x] drift/resync tests.

Acceptance:
- [x] Runtime mirror works end-to-end with synthetic events.

---

# Phase 10 — Live JaCaMo Runtime Connectors

Read:
- `docs/project/10-runtime-adapter.md`
- current JaCaMo/Jason/CArtAgO/Moise APIs only as needed

## P10.1 Runtime spike/evidence
- [x] Identify supported official hook for Agent state/events.
- [x] Identify CArtAgO operation/property event hook.
- [x] Identify Moise organisation runtime hook.
- [x] Document capability gaps.

## P10.2 Jason connector
- [x] Connect lifecycle.
- [x] Initial Agent snapshot.
- [x] Belief change events.
- [x] Goal events where accessible.
- [x] Action execution events.
- [x] Message events if useful.
- [x] Runtime Agent identity → trace.

## P10.3 CArtAgO connector
- [x] Artifact instance discovery.
- [x] Observable property snapshot.
- [x] Property change events.
- [x] Operation enter/exit/failure.
- [x] Signal events if supported/needed.
- [x] Correlate operation to Artifact/Agent where runtime supplies evidence.

## P10.4 Moise connector
- [x] Organisation instance discovery.
- [x] Role/group state.
- [x] Mission/scheme state where exposed.
- [x] Normative state only where API semantics clear.
- [x] Explicitly document unsupported runtime semantics.

## P10.5 Full synchronization
- [x] Build initial runtime snapshot.
- [x] Compare with imported static model.
- [x] Create dynamic runtime objects/links if design supports them.
- [x] Mark mirror LIVE only after successful sync.

## P10.6 Reconnect/resync
- [x] Detect disconnect.
- [x] Mark STALE.
- [x] Reconnect.
- [x] Full resync.
- [x] Verify no stale results claimed as current.

Acceptance:
- [x] Real JaCaMo Auction state can be mirrored into USE.

Evidence (2026-09-15, implementation commit `84da2a97`):

- `mvn -pl use-plugin -Dtest='org.tzi.use.plugins.jacamo.runtime.*Test' test`: PASS, 10 tests, including
  real Jason 3.3.0, CArtAgO 3.1, Moise 1.1, composite ordering, initial-sync race, stale detection, and Auction live
  reconnect/full-resync coverage.
- `mvn -pl use-plugin package`: PASS, 57 tests; `target/use-plugin-7.5.0.jar` built.
- `mvn test`: PASS for all five reactor modules; use-core 12 tests, use-gui 1 test, use-plugin 57 tests.
- `mvn -pl use-plugin dependency:tree` with the three JaCaMo coordinates filtered: PASS and resolves Jason
  `3.3.0`, CArtAgO `3.1`, and Moise `1.1` as provided dependencies.
- Capability choices, `makeArtifact` classloading diagnosis, exact trace policy, and unsupported semantics are
  recorded in `docs/project/10-runtime-adapter.md` section 12.

---

# Phase 11 — Runtime Verification

Read:
- `docs/project/10-runtime-adapter.md`
- `docs/project/11-verification-engine.md`
- `docs/project/07-constraint-translation-and-ocl.md`

## P11.1 Event-driven checks
- [x] Check after state-changing event.
- [x] Correlate results with event ID.
- [x] Store snapshot/version.

## P11.2 Runtime operation contracts
- [x] Operation start resolves USE operation.
- [x] Evaluate precondition.
- [x] Capture pre-state.
- [x] Apply runtime deltas.
- [x] Evaluate postcondition at exit.
- [x] Handle failure/abort.

## P11.3 Incremental dependencies
- [x] Build constraint dependency index.
- [x] Reevaluate affected constraints only.
- [x] Keep full-check fallback.
- [x] Equivalence regression: targeted vs full results.

## P11.4 Drift detection
- [x] Periodic authoritative snapshot.
- [x] Compare mirror.
- [x] Diagnostic differences.
- [x] Resync policy.

## P11.5 Runtime report
- [x] current connection state;
- [x] event;
- [x] constraint;
- [x] violation;
- [x] source trace;
- [x] latency.

Acceptance:
- [x] Runtime violation detected from real JaCaMo execution with correct trace.

Evidence (2026-09-15): `RuntimeVerificationEngine` observes the single ordered mutation boundary, captures
operation pre-state before `OP_ENTER`, evaluates preconditions without blocking JaCaMo, preserves event/correlation
IDs, evaluates source-backed postconditions with USE's pre/post evaluator (`@pre`), and reports failed/aborted
operations explicitly. `ConstraintDependencyIndex` selects declared dependencies plus conservatively global
constraints and falls back to a full check when no safe selection can be proven. Authoritative snapshot comparison
reports exact object/attribute/link differences and supports report-only or periodic automatic full resync.
`RuntimeVerificationReport` JSON/Markdown includes connection state, event, snapshot version/fingerprint,
constraint outcome, violation context, JaCaMo trace, diagnostics, and measured evaluation latency.

- `mvn -pl use-plugin test`: PASS, 61 tests.
- Real `LiveJaCaMoAuctionIntegrationTest`: PASS against Jason 3.3.0, CArtAgO 3.1, and Moise 1.1; closing the
  live Auction and executing `placeBid(item1, 0)` produce event-correlated OCL failures whose source trace is the
  exact imported Auction Artifact semantic ID.
- `RuntimeVerificationEngineTest`: PASS for state-event violation, invalid precondition, abort, explicit
  postcondition with `@pre`, dependency selection/full fallback, targeted/full equivalence, drift diagnostic,
  periodic auto-resync, report JSON, snapshot version, event correlation, and source trace.

---

# Phase 12 — Plugin UI Completion

Read:
- `docs/project/12-plugin-ui-workflow.md`

## P12.1 Import UI
- [x] `.jcm` chooser.
- [x] project summary.
- [x] parse diagnostics.
- [x] mapping compatibility.
- [x] generation result.

## P12.2 Trace/mapping view
- [x] source → target table/tree.
- [x] filter by dimension/status.
- [x] navigate source.
- [x] show projection rule.

## P12.3 Verification dashboard
- [x] constraint list.
- [x] result status.
- [x] context object.
- [x] violation detail.
- [x] source navigation.

## P12.4 Runtime dashboard
- [x] OFFLINE/CONNECTING/SYNCING/LIVE/STALE/ERROR.
- [x] event counters.
- [x] queue depth.
- [x] last sync.
- [x] reconnect/resync controls.

## P12.5 Binding resolution dialog
- [x] show ambiguous source.
- [x] exact candidates.
- [x] candidate owner/type/source.
- [x] persist explicit binding.
- [x] no fuzzy auto-select.

Acceptance:
- [x] Full workflow usable from USE UI.

Evidence (2026-09-15, implementation commit `f54e6276`): the USE plugin descriptor exposes an always-enabled
`Open Workbench...` action backed by `DefaultJaCaMoFacade`. The Swing workbench delegates import, rebuild,
verification, report export, runtime lifecycle and binding persistence to the facade; it contains no parser,
mapping, transformation or runtime mutation logic. Project, source hash, dimension, mapping/generation,
diagnostic, trace/projection, verification/context/violation/source, and live runtime service data are displayed.
Binding candidates start with no selection and only an exact candidate ID selected by the user can be persisted.

- `mvn -pl use-plugin test`: PASS, 75 tests, including 7 Swing workbench tests, 6 real facade tests, USE plugin
  discovery/action smoke, real Auction import/offline verification, synthetic runtime lifecycle and live JaCaMo
  Auction integration.
- `mvn -pl use-plugin package -DskipTests`: PASS; `target/use-plugin-7.5.0.jar` built.
- `mvn test`: PASS for all five reactor modules; use-core 12 tests, use-gui 1 test, use-plugin 75 tests.

---

# Phase 13 — Hardening and Quality

Read:
- `docs/project/13-testing-quality.md`
- `docs/project/18-risk-register.md`

## P13.1 Full test matrix
- [x] unit.
- [x] parser fixtures.
- [x] mapping audit.
- [x] golden.
- [x] USE integration.
- [x] runtime synthetic.
- [x] runtime live.
- [x] Auction E2E.

## P13.2 Error resilience
- [x] malformed project.
- [x] partial source.
- [x] unsupported syntax.
- [x] mapping mismatch.
- [x] stale binding.
- [x] disconnect.
- [x] USE mutation error.

## P13.3 Security
- [x] path traversal tests.
- [x] no arbitrary code execution during static import.
- [x] safe classpath handling.
- [x] safe export paths.

## P13.4 Performance
- [x] baseline import timing.
- [x] baseline full-check timing.
- [x] runtime latency metrics.
- [x] optimize only measured bottlenecks.
- [x] regression benchmark.

## P13.5 Compatibility
- [x] pinned USE version.
- [x] pinned JaCaMo version.
- [x] compatibility matrix.
- [x] clean environment test.

Acceptance:
- [x] Stable repeated runs without semantic drift.

Evidence (2026-09-16): [Phase 13 coverage, commands, measurements and limits](../../project/13-testing-quality.md#8-phase-13-verification-evidence-2026-09-16).
Module package 108/108; full reactor `verify` 251/251 including 130 Failsafe tests;
independent mapping audit/mutations/Ecore+EMF/USE compilation passed. Three focused
runs each passed golden, real Auction and performance/verification tests (4/4).
A separate clone with an initially empty Maven repository passed 251/251 and
remained clean. No measured bottleneck warranted optimization; timing thresholds
were not invented. Compatibility is limited to the recorded Windows/JDK and real
in-process component versions. Production hardening commits: `34768a6e`, `0f24e5c2`.
These checkboxes record implementation/validation completion; Phase 13 branch
merge and push are still pending and are not authorized by this task.

Review fix (2026-09-16): mapping/Ecore/schema/manifest now use one private byte
snapshot per load. Three deterministic snapshot regressions were RED for the
expected inconsistent-read behavior, then GREEN (mapping tests 9/9; module
111/111; full reactor `mvn verify` 254/254). The existing Phase 13 evidence section and task report contain the
superseding results; earlier 108/251 counts above identify the pre-review revision.

Review fix round 2 (2026-09-16): downstream Ecore/manifest snapshots are now taken
only after mapping schema and JSON validation, restoring `MAPPING_SCHEMA_INVALID`
precedence when later inputs are missing without weakening the immutable
single-snapshot contract. The deterministic regression was RED with
`MAPPING_LOAD_FAILED`, then GREEN; mapping tests passed 10/10 and the module passed
112/112. Full reactor `mvn verify` and exact-production-commit clean checkout both
passed 255/255. Independent review of `f50a6af2..eae54750` found no Critical or
Important issue; its only Minor evidence-link finding is resolved by the tracked
Phase 13 quality document.

---

# Phase 14 — Auction Final E2E and Thesis Evidence

Read:
- `docs/project/14-auction-case-study.md`
- `docs/project/17-end-to-end-acceptance.md`

## P14.1 Reproducible project
- [x] Pin Auction source commit.
- [x] Pin all tool versions.
- [x] Store verification profile.
- [x] Store bindings if truly needed.

## P14.2 Offline evidence
- [x] import log.
- [x] semantic summary.
- [x] generated `.use`.
- [x] initial `.cmd`.
- [x] generated OCL.
- [x] core/case OCL.
- [x] trace.
- [x] PASS scenario.
- [x] FAIL scenario.

## P14.3 Runtime evidence
- [x] real run.
- [x] event log.
- [x] valid bid scenario.
- [x] closed auction invalid bid.
- [x] invalid amount.
- [x] reconnect/resync.
- [x] violation report with source trace.

## P14.4 Research evidence
- [x] distinguish translated vs authored OCL.
- [x] distinguish structural vs runtime.
- [x] document unsupported semantics.
- [x] document assumptions.
- [x] preserve hashes/versions.

Acceptance:
- [x] Thesis demo can be repeated from clean checkout.

Evidence (2026-09-16, source/evidence revision `d5636264`): `mvn -pl use-plugin verify`
passed **118/118** and the full reactor `mvn verify` passed **261/261** (13 core,
130 GUI including Failsafe, 118 plugin). The regenerated evidence contains exactly
14 declared artifacts: 11 offline and 3 runtime. Independent JSON/path/hash audit
found 13 runtime events, four balanced operation correlations (three `OP_EXIT`, one
expected `OP_FAIL`), 11 verification reports, and zero reconnect authoritative
snapshot differences. The manifest and runtime summary pin the checked-in source
commit, component versions, verification profile and LF-normalized source/resource
hashes. Independent review of `main..d5636264` found no Critical or Important issue;
its two Minor notes concern stricter future assertion hardening, not a mismatch in
the current artifacts. The documented limits remain: the live run uses real
in-process Jason/CArtAgO and a programmatic real-Moise API subset, not a standalone
`.jcm` launch or runtime loading of the checked-in Moise XML; arbitrary Java effects
and normative lifecycle semantics are not claimed. Clean-checkout reproduction and
merge/push remain the Phase 14 integration/Phase 15 release gates below.

---

# Phase 15 — Release

Read:
- `docs/project/15-build-release-operations.md`
- `docs/project/17-end-to-end-acceptance.md`

## P15.1 Release build
- [x] clean checkout.
- [x] full build.
- [x] full tests.
- [x] mapping audit.
- [x] Auction E2E.
- [x] plugin load smoke.

## P15.2 Documentation
- [x] README install.
- [x] user workflow.
- [x] developer architecture.
- [x] known limitations.
- [x] compatibility matrix.
- [x] changelog.

## P15.3 Package
- [x] plugin JAR.
- [x] canonical metamodel/mapping resources.
- [x] core OCL.
- [x] example.
- [x] license/notice as required.
- [x] release manifest.

## P15.4 Git
- [x] final phase branch clean.
- [x] final commits.
- [x] merge.
- [x] push.
- [x] tag release.
- [x] push tag.

## P15.5 Final acceptance
- [x] Every checkbox in `docs/project/17-end-to-end-acceptance.md` verified.
- [x] No known P0/P1 correctness blocker.
- [x] Final report generated.

Pre-integration evidence (2026-09-16): `mvn -pl use-plugin verify` passed
119 unit tests and 3 release integration tests; full reactor `mvn verify`
passed 265/265 across all five modules. A separate clone of the committed
Phase 15 branch with the candidate diff applied and no prior build outputs
passed `mvn clean verify` across the full reactor, including the isolated
installed-ZIP mapping smoke. The smoke was RED before bundling the JSON Schema
validator (`NoClassDefFoundError: com/networknt/schema/SpecificationVersion`)
and GREEN after the JAR bundled its runtime dependencies. The focused
auto-resync, Auction offline/live, and plugin smoke tests passed 7/7.
Independent canonical JaCaMo revision `849dc33` Ecore/EMF, mapping/schema,
negative mutation and USE compiler audits passed; all four canonical resource
hashes matched this plugin checkout. The actual ZIP inventory was 27 entries;
every ZIP entry matched its declared source, the JAR's canonical/version/license
resources matched source bytes, and its SHA-256 sidecar matched the archive.
Auction evidence contained 14 artifacts, 13 runtime events, 11 reports, and
zero reconnect drift differences. Exact release-commit clean clone, Git
integration, tag, and final package verification remain release gates.

Exact release implementation commit `6cccfc65` was cloned without prior build
outputs and passed `mvn clean verify`: 265/265 tests, 0 failures, 0 errors and
0 skips. Git integration, the final tagged-commit regression, tag push and
remote package verification remain release gates.

Final integration evidence (2026-09-16): remote branch `phase/15-release` was
verified at `558a3047`, then merged into `main` as `a4951e91`. The post-merge
full reactor passed 265/265 tests with no failures, errors or skips. The final
main commit was regression-tested, pushed, tagged `use-jacamo-plugin-v1.0.0`,
and the remote main/tag targets plus release ZIP/JAR/SHA-256 were verified.
