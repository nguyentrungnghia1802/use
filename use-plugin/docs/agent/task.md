# USE JaCaMo Plugin — Full Implementation Task Plan

> Checklist này bao phủ toàn bộ dự án từ baseline freeze đến runtime verification, hardening và release. Không bỏ qua phase vì "để sau". Thứ tự có thể điều chỉnh chỉ khi dependency kỹ thuật bắt buộc, nhưng mọi task vẫn phải hoàn thành trước final release.

## Global Definition of Done

- [ ] Canonical Ecore baseline audited and frozen.
- [ ] Mapping V1 structurally complete, internally consistent, ambiguity-free.
- [ ] Plugin loads in pinned USE.
- [ ] JaCaMo project import works from `.jcm`.
- [ ] Jason/CArtAgO/Moise extraction works for supported project.
- [ ] Semantic model + trace complete.
- [ ] USE model/state generation works.
- [ ] Constraint translation supported subset works.
- [ ] Core and case-study OCL work.
- [ ] Binding/resolver ambiguity path works.
- [ ] Runtime adapter works against real JaCaMo.
- [ ] Runtime OCL detects violations.
- [ ] Auction E2E positive and negative scenarios work.
- [ ] Full tests/build/docs/release complete.

---

# Phase 0 — Baseline Freeze

Read:
- `docs/project/04-jacamo-metamodel-baseline.md`
- `docs/project/05-metamodel-mapping-contract.md`
- `docs/project/16-research-evidence-boundaries.md`

## P0.1 Metamodel audit
- [ ] Parse canonical `JaCaMo-Metamodel.ecore` programmatically.
- [ ] Assert expected EClass count from file.
- [ ] Enumerate all EAttributes with owner/type/bounds/default.
- [ ] Enumerate all EReferences with owner/target/bounds/containment.
- [ ] Enumerate all inheritance edges.
- [ ] Extract unresolved annotations.
- [ ] Record Ecore SHA-256.
- [ ] Create/update `metamodel-audit.md`.
- [ ] Create/update `metamodel-freeze-manifest.json`.
- [ ] Add tests that fail on unnoticed structural drift.

## P0.2 Mapping schema
- [ ] Create `jacamo-use-mapping.schema.json`.
- [ ] Require qualified source identity.
- [ ] Define target kinds.
- [ ] Define multiplicity structure.
- [ ] Define association/composition end schema.
- [ ] Define projection schema.
- [ ] Define unresolved/review flag schema.
- [ ] Define mapping/evolution metadata.
- [ ] Add schema validation test.

## P0.3 Mapping coverage audit
- [ ] Verify every EClass has exactly one structural mapping.
- [ ] Verify every declared EAttribute has mapping.
- [ ] Verify every EReference has mapping.
- [ ] Verify every inheritance edge has mapping.
- [ ] Verify source owner exists.
- [ ] Verify source feature exists.
- [ ] Verify reference target matches Ecore.
- [ ] Verify containment matches Ecore.
- [ ] Verify forward multiplicity matches Ecore.
- [ ] Verify target association names unique.
- [ ] Verify source keys unique.
- [ ] Reject unqualified `operation`-style IDs.
- [ ] Explicitly preserve unresolved visible attributes as unresolved, not guessed.
- [ ] Audit all review-flag inheritance entries.

## P0.4 Projection audit
For each VP001–VP007:
- [ ] source defined;
- [ ] target defined;
- [ ] need/rationale defined;
- [ ] direction defined;
- [ ] lossless/lossy status defined;
- [ ] assumptions defined;
- [ ] fallback defined;
- [ ] trace requirement defined;
- [ ] test case defined.

## P0.5 Freeze Mapping V1
- [ ] Generate mapping SHA-256.
- [ ] Create `mapping-audit.md`.
- [ ] Create `mapping-freeze-manifest.json`.
- [ ] Record Ecore fingerprint expected by mapping.
- [ ] Mark status `LOCKED_BASELINE_V1`.
- [ ] Run full audit from clean checkout.
- [ ] Commit and merge Phase 0.

Acceptance:
- [ ] "Mapping V1 is canonical, structurally complete for the frozen metamodel, internally consistent, and has no known mapping ambiguity."

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
- [ ] Define trace JSON schema.
- [ ] Implement in-memory trace index.
- [ ] Record class/attribute/association/object/operation traces.
- [ ] Persist trace.

## P7.2 Runtime key support
- [ ] Add runtime identity fields without requiring live runtime.
- [ ] Lookup indexes by semantic ID/runtime ID/USE ID.

## P7.3 Resolver
- [ ] Exact ID.
- [ ] Explicit reference.
- [ ] Owner-qualified.
- [ ] Unique typed scope.
- [ ] Explicit binding.
- [ ] Failure.

## P7.4 Binding schema
- [ ] Create JSON schema.
- [ ] Validate canonical IDs.
- [ ] Persist user choice.
- [ ] Add reason/provenance.
- [ ] Detect stale binding after project change.

## P7.5 Ambiguity tests
- [ ] Same operation name on two artifacts.
- [ ] Same local symbol in multiple agents.
- [ ] Multiple organisation instances.
- [ ] Invalid target kind.
- [ ] Binding resolves exactly one target.

Acceptance:
- [ ] No formal resolution depends on fuzzy name matching.

---

# Phase 8 — Verification Service and Offline Reporting

Read:
- `docs/project/11-verification-engine.md`
- `docs/project/12-plugin-ui-workflow.md`

## P8.1 Constraint registry
- [ ] Load translated constraints.
- [ ] Load core OCL.
- [ ] Load case/user OCL.
- [ ] Record origin/dependencies.

## P8.2 Full check
- [ ] Check structure/multiplicity.
- [ ] Check all invariants.
- [ ] Capture results.

## P8.3 Operation check
- [ ] Support pre-state.
- [ ] Evaluate preconditions.
- [ ] Evaluate postconditions.
- [ ] Preserve correlation.

## P8.4 Result/diagnostic
- [ ] PASS/FAIL/ERROR/SKIPPED.
- [ ] Context object.
- [ ] OCL source.
- [ ] trace back to JaCaMo.
- [ ] export JSON/Markdown.

## P8.5 Offline Auction E2E
- [ ] Valid project passes expected checks.
- [ ] Violation fixture fails expected constraint.
- [ ] Report points to correct JaCaMo source.

Acceptance:
- [ ] Complete design-time workflow usable without runtime.

---

# Phase 9 — Runtime Adapter Foundation

Read:
- `docs/project/10-runtime-adapter.md`
- reference current USE `plugin_monitor` architecture/source

## P9.1 Connector abstraction
- [ ] Define lifecycle.
- [ ] Define capability reporting.
- [ ] Define snapshot API.
- [ ] Define event subscription API.

## P9.2 Normalized events
- [ ] Implement event types.
- [ ] sequence/timestamp.
- [ ] correlation ID.
- [ ] payload schema.
- [ ] serialization test.

## P9.3 Mutation engine
- [ ] CREATE.
- [ ] DESTROY.
- [ ] SET.
- [ ] INSERT.
- [ ] DELETE.
- [ ] OP_ENTER.
- [ ] OP_EXIT.
- [ ] OP_FAIL.

## P9.4 Event queue
- [ ] Single correctness-first ordered queue.
- [ ] Backpressure metrics.
- [ ] No silent drop.
- [ ] graceful stop.

## P9.5 Synthetic connector
- [ ] Replay JSON event stream.
- [ ] Full snapshot.
- [ ] disconnect/reconnect.
- [ ] drift/resync tests.

Acceptance:
- [ ] Runtime mirror works end-to-end with synthetic events.

---

# Phase 10 — Live JaCaMo Runtime Connectors

Read:
- `docs/project/10-runtime-adapter.md`
- current JaCaMo/Jason/CArtAgO/Moise APIs only as needed

## P10.1 Runtime spike/evidence
- [ ] Identify supported official hook for Agent state/events.
- [ ] Identify CArtAgO operation/property event hook.
- [ ] Identify Moise organisation runtime hook.
- [ ] Document capability gaps.

## P10.2 Jason connector
- [ ] Connect lifecycle.
- [ ] Initial Agent snapshot.
- [ ] Belief change events.
- [ ] Goal events where accessible.
- [ ] Action execution events.
- [ ] Message events if useful.
- [ ] Runtime Agent identity → trace.

## P10.3 CArtAgO connector
- [ ] Artifact instance discovery.
- [ ] Observable property snapshot.
- [ ] Property change events.
- [ ] Operation enter/exit/failure.
- [ ] Signal events if supported/needed.
- [ ] Correlate operation to Artifact/Agent where runtime supplies evidence.

## P10.4 Moise connector
- [ ] Organisation instance discovery.
- [ ] Role/group state.
- [ ] Mission/scheme state where exposed.
- [ ] Normative state only where API semantics clear.
- [ ] Explicitly document unsupported runtime semantics.

## P10.5 Full synchronization
- [ ] Build initial runtime snapshot.
- [ ] Compare with imported static model.
- [ ] Create dynamic runtime objects/links if design supports them.
- [ ] Mark mirror LIVE only after successful sync.

## P10.6 Reconnect/resync
- [ ] Detect disconnect.
- [ ] Mark STALE.
- [ ] Reconnect.
- [ ] Full resync.
- [ ] Verify no stale results claimed as current.

Acceptance:
- [ ] Real JaCaMo Auction state can be mirrored into USE.

---

# Phase 11 — Runtime Verification

Read:
- `docs/project/10-runtime-adapter.md`
- `docs/project/11-verification-engine.md`
- `docs/project/07-constraint-translation-and-ocl.md`

## P11.1 Event-driven checks
- [ ] Check after state-changing event.
- [ ] Correlate results with event ID.
- [ ] Store snapshot/version.

## P11.2 Runtime operation contracts
- [ ] Operation start resolves USE operation.
- [ ] Evaluate precondition.
- [ ] Capture pre-state.
- [ ] Apply runtime deltas.
- [ ] Evaluate postcondition at exit.
- [ ] Handle failure/abort.

## P11.3 Incremental dependencies
- [ ] Build constraint dependency index.
- [ ] Reevaluate affected constraints only.
- [ ] Keep full-check fallback.
- [ ] Equivalence regression: targeted vs full results.

## P11.4 Drift detection
- [ ] Periodic authoritative snapshot.
- [ ] Compare mirror.
- [ ] Diagnostic differences.
- [ ] Resync policy.

## P11.5 Runtime report
- [ ] current connection state;
- [ ] event;
- [ ] constraint;
- [ ] violation;
- [ ] source trace;
- [ ] latency.

Acceptance:
- [ ] Runtime violation detected from real JaCaMo execution with correct trace.

---

# Phase 12 — Plugin UI Completion

Read:
- `docs/project/12-plugin-ui-workflow.md`

## P12.1 Import UI
- [ ] `.jcm` chooser.
- [ ] project summary.
- [ ] parse diagnostics.
- [ ] mapping compatibility.
- [ ] generation result.

## P12.2 Trace/mapping view
- [ ] source → target table/tree.
- [ ] filter by dimension/status.
- [ ] navigate source.
- [ ] show projection rule.

## P12.3 Verification dashboard
- [ ] constraint list.
- [ ] result status.
- [ ] context object.
- [ ] violation detail.
- [ ] source navigation.

## P12.4 Runtime dashboard
- [ ] OFFLINE/CONNECTING/SYNCING/LIVE/STALE/ERROR.
- [ ] event counters.
- [ ] queue depth.
- [ ] last sync.
- [ ] reconnect/resync controls.

## P12.5 Binding resolution dialog
- [ ] show ambiguous source.
- [ ] exact candidates.
- [ ] candidate owner/type/source.
- [ ] persist explicit binding.
- [ ] no fuzzy auto-select.

Acceptance:
- [ ] Full workflow usable from USE UI.

---

# Phase 13 — Hardening and Quality

Read:
- `docs/project/13-testing-quality.md`
- `docs/project/18-risk-register.md`

## P13.1 Full test matrix
- [ ] unit.
- [ ] parser fixtures.
- [ ] mapping audit.
- [ ] golden.
- [ ] USE integration.
- [ ] runtime synthetic.
- [ ] runtime live.
- [ ] Auction E2E.

## P13.2 Error resilience
- [ ] malformed project.
- [ ] partial source.
- [ ] unsupported syntax.
- [ ] mapping mismatch.
- [ ] stale binding.
- [ ] disconnect.
- [ ] USE mutation error.

## P13.3 Security
- [ ] path traversal tests.
- [ ] no arbitrary code execution during static import.
- [ ] safe classpath handling.
- [ ] safe export paths.

## P13.4 Performance
- [ ] baseline import timing.
- [ ] baseline full-check timing.
- [ ] runtime latency metrics.
- [ ] optimize only measured bottlenecks.
- [ ] regression benchmark.

## P13.5 Compatibility
- [ ] pinned USE version.
- [ ] pinned JaCaMo version.
- [ ] compatibility matrix.
- [ ] clean environment test.

Acceptance:
- [ ] Stable repeated runs without semantic drift.

---

# Phase 14 — Auction Final E2E and Thesis Evidence

Read:
- `docs/project/14-auction-case-study.md`
- `docs/project/17-end-to-end-acceptance.md`

## P14.1 Reproducible project
- [ ] Pin Auction source commit.
- [ ] Pin all tool versions.
- [ ] Store verification profile.
- [ ] Store bindings if truly needed.

## P14.2 Offline evidence
- [ ] import log.
- [ ] semantic summary.
- [ ] generated `.use`.
- [ ] initial `.cmd`.
- [ ] generated OCL.
- [ ] core/case OCL.
- [ ] trace.
- [ ] PASS scenario.
- [ ] FAIL scenario.

## P14.3 Runtime evidence
- [ ] real run.
- [ ] event log.
- [ ] valid bid scenario.
- [ ] closed auction invalid bid.
- [ ] invalid amount.
- [ ] reconnect/resync.
- [ ] violation report with source trace.

## P14.4 Research evidence
- [ ] distinguish translated vs authored OCL.
- [ ] distinguish structural vs runtime.
- [ ] document unsupported semantics.
- [ ] document assumptions.
- [ ] preserve hashes/versions.

Acceptance:
- [ ] Thesis demo can be repeated from clean checkout.

---

# Phase 15 — Release

Read:
- `docs/project/15-build-release-operations.md`
- `docs/project/17-end-to-end-acceptance.md`

## P15.1 Release build
- [ ] clean checkout.
- [ ] full build.
- [ ] full tests.
- [ ] mapping audit.
- [ ] Auction E2E.
- [ ] plugin load smoke.

## P15.2 Documentation
- [ ] README install.
- [ ] user workflow.
- [ ] developer architecture.
- [ ] known limitations.
- [ ] compatibility matrix.
- [ ] changelog.

## P15.3 Package
- [ ] plugin JAR.
- [ ] canonical metamodel/mapping resources.
- [ ] core OCL.
- [ ] example.
- [ ] license/notice as required.
- [ ] release manifest.

## P15.4 Git
- [ ] final phase branch clean.
- [ ] final commits.
- [ ] merge.
- [ ] push.
- [ ] tag release.
- [ ] push tag.

## P15.5 Final acceptance
- [ ] Every checkbox in `docs/project/17-end-to-end-acceptance.md` verified.
- [ ] No known P0/P1 correctness blocker.
- [ ] Final report generated.
