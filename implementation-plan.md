# MASTER IMPLEMENTATION PLAN — JaCaMo → CSL / iStar / OCL → USE

Ngày lập: 16/10/2026. Phiên bản kế hoạch: 1.0. Ngôn ngữ triển khai dự kiến: Java 21. Repository implementation duy nhất: D:/_CODE_BANK/Project_/08_Thesis/use.

**Live execution plan.** Các bảng phase giữ baseline thiết kế; trạng thái hiện hành và bằng chứng nằm trong ledger bên dưới. Implementation chỉ ghi trong USE; hai repository tham chiếu giữ read-only. Không suy trạng thái thực hiện từ các dòng “Trạng thái đầu” lịch sử.


## Live execution ledger — 16/10/2026

The original phase tables below are the planning baseline; this ledger records implementation evidence. New code stays in USE on main; no commit/branch.

| Task | Status | Evidence / next dependency |
|---|---|---|
| P00-T01 | DONE | Full report/master plan read; HEAD/status/source audit and baseline isolated; one pipeline design retained. |
| P00-T02 | DONE | Fresh reactor compilation + 25 native regression tests, 0 failures/errors/skips; target/semantics-baseline.log. |
| P00-T03 | DONE | Profile selection/admission + seven-valued result model compiled; SemanticsProfileTest: 3 pass, 0 skips. |
| P00-T04 | DONE | Official adapters/snapshot baseline selected suite passed; see target/semantics-profile-tests.log; default native regression baseline 25 pass. |
| P01-F1 | DONE | 7 profile/schema tests pass, including strict scalar rejection, typed operation parameters, @pre 0->1 PASS / 0->2 FAIL, undefined distinct, pre-query rejected; target/semantics-schema-tests.log. |
| P01-F2 | DONE | CSL explicit schema compiles/exports/recompiles in same native pipeline; 6 binding/schema/native audit tests pass after fixing reserved USE end symbol. |
| P01-F3 | DONE | Phase-1 semantic gate: 14 tests, 0 failures/errors/skips; target/semantics-phase1-semantic-gate.log. Real GroupBoard/CArtAgO, strict Integer, 0->1/0->2 @pre, ACHIEVE, partial/gap, malformed rollback, replacement identity and replay endpoint hashes. |
| P01-F4 | DONE | Phase-1 semantic gate: 14 tests, 0 failures/errors/skips; target/semantics-phase1-semantic-gate.log. Real GroupBoard/CArtAgO, strict Integer, 0->1/0->2 @pre, ACHIEVE, partial/gap, malformed rollback, replacement identity and replay endpoint hashes. |
| P01-F5 | DONE | Phase-1 semantic gate: 14 tests, 0 failures/errors/skips; target/semantics-phase1-semantic-gate.log. Real GroupBoard/CArtAgO, strict Integer, 0->1/0->2 @pre, ACHIEVE, partial/gap, malformed rollback, replacement identity and replay endpoint hashes. |
| P01-F6 | DONE | Phase-1 semantic gate: 14 tests, 0 failures/errors/skips; target/semantics-phase1-semantic-gate.log. Real GroupBoard/CArtAgO, strict Integer, 0->1/0->2 @pre, ACHIEVE, partial/gap, malformed rollback, replacement identity and replay endpoint hashes. |
| P01-F7 | DONE | Phase-1 semantic gate: 14 tests, 0 failures/errors/skips; target/semantics-phase1-semantic-gate.log. Real GroupBoard/CArtAgO, strict Integer, 0->1/0->2 @pre, ACHIEVE, partial/gap, malformed rollback, replacement identity and replay endpoint hashes. |
| P01-F8 | DONE | Phase-1 semantic gate: 14 tests, 0 failures/errors/skips; target/semantics-phase1-semantic-gate.log. Real GroupBoard/CArtAgO, strict Integer, 0->1/0->2 @pre, ACHIEVE, partial/gap, malformed rollback, replacement identity and replay endpoint hashes. |
| P01-REGRESSION | DONE | 29 native regression tests pass, 0 failures/errors/skips, including live callbacks, operations, bridge coverage, reconnect and replay; target/semantics-phase1-gate.log. Fully qualified semantic wildcard selected no semantic tests; corrected explicit class-name discovery passed in the separate semantic gate. |
| P01-GATE | DONE | All F1-F8 accepted in named v1 observation profile; 14 semantic + 29 native regression tests pass. GroupBoard player registration is adapter-boundary fidelity, not a full Jason MAS claim; global MOISE coverage remains PARTIAL. |
| P02-T01 | DONE | Versioned CSLMonitor.ecore validates with native EMF Diagnostician, no unresolved proxies; namespace distinguishes Entity/Role/OrgContext from observer Player/AtomicAgent/ContextOccurrence/RoleOccurrence. Java M1 validator checks ownership, nesting cycles, kinds, bounds and source role inheritance. |
| P02-T02 | DONE | Final Phase-2 gate: 30 tests pass, 0 failures/errors/skips; target/semantics-phase2-gate.log. Native inheritance/collective player/two contexts, compatibility allowed/disallowed, nesting bounds and real per-context role-cardinality FAIL retained. |
| P02-T03 | UNSUPPORTED | Full MOISE scoped/directional/extending compatibility is outside the symmetric CSL relation; no automatic weakening. Composition destruction is explicitly UNSUPPORTED_COMPOSITION_DESTRUCTION; observer deletes only observed instances, preserves surviving parts. Structural UML aggregation/composition remains testable. |
| P02-T04 | UNSUPPORTED | M1 declaration ownership is validated/preserved; runtime ownership/lifecycle is not inferred from declaration containment and is emitted as UNSUPPORTED:RUNTIME_DECLARATION_OWNERSHIP when selected. CSL collective players are representable by the backend; JaCaMo source group-as-player correspondence is not inferred from arbitrary player-name strings. |
| P02-GATE | DONE | Named structural observer fragment accepted; optional source-policy/lifecycle areas P02-T03/T04 explicitly UNSUPPORTED and present in evidence. 30-test gate passed; native MOISE regressions intact. |
| P03-T01 | DONE | 12 tests pass, 0 failures/errors/skips: exact supplied 21 EClass/7 EEnum, default-vs-explicit XML origin, immutable features/references, unknown enum/required field/external reference rejections; target/semantics-emf-tests.log. Frozen native V2 unchanged; source Ecore exact-byte copy is separately packaged. |
| P03-T02 | DONE | 28-rule catalog, official normalization and feature preservation validated; every feature has one decision; numeric canonicalization makes portable decode equal. 15-test final gate: target/semantics-transformation-codec-tests.log. |

| P03-T03 | DONE | MappingBundle/TraceComplement retains full immutable source assets, feature origins, rule/disposition/loss and checked digests; tamper/missing decisions rejected; same native pipeline admits verified bundle. |

| P03-T04 | DONE | Explicitly supplemented Ecore and official-contract inputs compile to equal native structural hashes. Unsupplemented runtime correspondence is rejected, not inferred from EReferences. SemanticTransformationTest. |

| P03-GATE | DONE | 15 tests pass, 0 failures/errors/skips; bundled exact Ecore + validated IR + versioned complement/codec + supported frontend parity. No inverse or behavioral equivalence claimed. |

| P04-T01 | DONE | Exact FS definitions, min/all thresholds, raw arguments/TTF/location/dependencies, pair-scoped allocation bounds and mission preferences retained. Official Mission.getPreferables omission corrected as additive compatible supplement (D02). |

| P04-T02 | DONE | Same native model/state functional helper binding passes real SchemeBoard UUID/arguments tests and exact per-instance state/sets. target/semantics-functional-gate.log: plugin 17 tests and adapter 3 tests pass, 0 failures/errors/skips. |

| P04-T03 | DONE | Mission allocation invariant retains accepted-state FAIL; leave/rejoin produces a new commitment occurrence. Real inherited OrgBoard/GroupBoard/SchemeBoard operations give waiting -> enabled -> satisfied and exact committed/done agent lists. No count-based replacement for NPL satisfaction. |

| P05-T01 | DONE | ExecutionDescriptor retains exact operator/ordinal/rate/goal references and ancestor-sequence enabling prerequisites; OPlanSemanticsTest compares every goal to actual Goal.getPreConditionGoals for six flat/nested operator fixtures. |

| P04-T04 | DONE | Explicit MoiseRequirementBinder selects public board acknowledgement with authored NONE/ACHIEVE/MAINTAIN/SUSTAIN; it does not cast source goalType. Raw TTF/source maintenance retained with explicit unsupported normative realization; semantic envelope reports board source separately. |

| P04-GATE | DONE | Functional and OPlan consolidated suites pass: target/semantics-functional-gate.log (17 plugin + 3 adapter), target/semantics-oplan-tests.log (12 plugin + 3 adapter). Exact live board oracle, argument isolation, allocation failure and source contract regressions. |

| P05-T02 | DONE | OPlanOutcomeMapper emits authored OR for choice, AND for sequence/parallel; child occurrence correspondence required. One/both choice branches accepted as outcome, no inferred exclusion or scheduler. |

| P05-T03 | DONE | OPlanTransitionMonitor distinguishes exact invocation pre-state from callback order, FAIL witness for out-of-order enabling, accepts both parallel interleavings, INCONCLUSIVE for missing causal boundary/gap. Enabledness fragment and explicit incomplete history emitted by native semantic envelope. |

| P05-T04 | DONE | Invalid ordinal/reference/duplicate/cycle admission and custom operator UNSUPPORTED supported; descriptor retained in native binding metadata/replay payload. No full-behavior proof from polling. |

| P05-GATE | DONE | 4 OPlan tests plus functional/transformation/native source regressions pass (target/semantics-oplan-tests.log). Outcome and enabledness abstraction are versioned; execution-history from polling remains INCONCLUSIVE by design. |

| P06-T01 | DONE | Cycle-thread structured execution observation preserves test/achievement/failure triggers, event and IntendedMeans occurrence tokens, nested/suspended intention stacks, plan/current-step/substitution/source evidence. Existing achievement-only default projection remains unchanged. |

| P07-T01 | DONE | Structured namespace/negative/term/annotation/Rule data copied via official Jason AST at cycle end. Open/cyclic/relational/internal query nodes are retained or explicitly excluded, never coerced into facts. |

| P06-T02 | DONE | Production optional Jason Task configuration now covered by JasonTaskConfigurationTest: exact source program/plan/body/context/target operation admitted, compiled typed contract and source decisions preserved; wrong query rejected, runtime missing causation explicitly UNSUPPORTED. target/semantics-task-helper-gate.log: all 5 Task/helper tests passed; latest full current-source gate revalidates them. |

| P06-T03 | UNSUPPORTED | Existing source APIs expose no proven ActionExec-to-OpId causal token. Authored step is statically admitted, but runtime result is UNSUPPORTED:JASON_ACTION_TO_CARTAGO_INVOCATION_CAUSATION_UNAVAILABLE. Production negative test confirms no inferred success from action name/disappearing intention. See P12-T03 scope. |

| P07-T02 | DONE | Mental/StoredBelief/Execution observer helpers share native MModel/MSystem. STORED_MEMBERSHIP distinct from restricted default-BB, complete, ground rule-free entailment; actual OCL compiler/evaluator used for both admitted fragments. |

| P07-T03 | DONE | Live CArtAgO value 12 and stored percept 10 coexist; complete belief removal changes observer query only and leaves artifact at 12. Recorded replay endpoint hashes agree. target/semantics-jason-mental-gate.log: 6 plugin + 5 adapter tests pass. |

| P08-T01 | DONE | 25-test compiler/temporal gate passes: actual host quantifiers, navigation @pre, typed parameters/result, undefined propagation, nested property dependency and per-model/profile AST cache. target/semantics-compiler-temporal-gate.log. |

| P09-T01 | DONE | Five independent temporal tests pass for all types, Task current invocation truth, evaluated-child refinement/dependency, reset/reactivation, changed binding, savepoint/idempotence and missing-binding history gap. target/semantics-compiler-temporal-gate.log. |

| P08-T02 | DONE | All native invariants (including dynamic user additions), operation pre/post and authored activation/condition ASTs enter typed registry. Quantified invariant verdict separated from body witnesses; negation after quantification, no undefined-body false coercion. |

| P08-T03 | DONE | Native evaluation uses pinned before/after states and seven-valued aggregation; invalid syntax/type rejects admission. USE division undefined compared by > remains UNDEFINED; equality follows StandardOperationsAny.Op_equal (undefined equals defined is false), documented host dialect rather than rewritten. |

| P09-T02 | DONE | Separate intentional UI displays raw/evaluated/lifecycle/quality/episode/history witness; all seven constraint verdicts kept read-only. target/semantics-native-failure-binding-gate.log: 21 tests pass, 0 failures/errors/skips. |

| P10-T01 | DONE | 8 adapter snapshot tests + 14 plugin tests pass (target/semantics-observation-identity-tests.log): scoped source watermark, topology metadata/backward decode, overflow/retry behavior, deep immutable envelope and result/replay observation scope digests. |

| P11-T01 | DONE | Same-name real artifact and GroupBoard UUID recreation verified; new contexts/role occurrences, old identity absent, source polling continuity remains explicit. Live vertical replay hashes agree; target/semantics-observation-identity-tests.log. |

| P10-T02 | DONE | All observed families use existing typed transport and source authority; exact board/agent freshness gated separately from global population. Snapshot metadata and accepted source prefix now enter the same semantic evidence, no second runtime store. |

| P11-T02 | DONE | Agent cycle regression rejected atomically; complete BB removal/reappearance has new occurrence; same-run state identity preserved; proven restart retires mental/role/commitment dependents; PARTIAL omission preserves and COMPLETE population deletes. ObserverIdentityLifecycleTest. |

| P11-T03 | DONE | Additional matrix gate passes with two configured official JCM agent instances retaining separate objects/provenance (no declaration alias), and actual GroupBoard disposal removes context/role before a later poll. target/semantics-acceptance-matrix-gate.log: 12 adapter + 14 plugin tests pass, 0 failures/errors/skips. |

| P12-T01 | DONE | 9 invocation/native/live tests pass, 0 failures/errors/skips (target/semantics-invocation-gate.log). Producer tags exact logger source-prefix proof; requested is not pre, completion follows committed observable deltas, global atomicity explicitly unsupported. |

| P12-T02 | DONE | Independent invocation pre states, suspend/resume, failure/timeout/capacity and exact descriptor proof. Completion signature/argument mismatch now INCONCLUSIVE; wrong incarnation cannot borrow pre. NativeInvocationSemanticsTest 4/4 and real CslVerticalSliceTest 2/2 pass in final 20-test gate. |

| P12-T03 | UNSUPPORTED | ICartagoLogger exposes no Java return value and no proven Jason ActionExec-to-OpId causation. Nonvoid return-dependent postchecks and configured Jason-step runtime conformance explicitly UNSUPPORTED. OCL backend supports typed result when explicitly supplied; no name/time pairing used. Source: CAgentArch.pendingActions is protected; OpId exposes artifact/performer but not ActionExec correlation. |

| P13-T01 | DONE | Real NPL 0.6.1 listener/getter + copy-crosswalk tests pass; exact NormativeBoard UUID, public created epoch/subject/substitution digest validated, ambiguous collisions not merged. Source raw EVIDENCE_ONLY status retained in observer metadata. target/semantics-normative-gate.log. |

| P13-T02 | DONE | Reported activation/fulfillment/deadline miss/unused permission verified against real NPL; state/clock/deadline evidence kept in separate helper vocabulary. Lifecycle consistency and source-reported violation separated from independent derivation; deterministic replay endpoint hashes agree. 3 adapter + 7 plugin tests pass. |

| P13-T03 | UNSUPPORTED | Independent full NPL/MOISE deontic equivalence, sanctions and MOISE maintenance-goal generator are not claimed. os2nopl maintenance branch remains TODO in read-only source; timer/conditions cannot be replaced with ordinary OCL invariants. NORM_DERIVATION is INCONCLUSIVE, no replay-clock deadline recomputation. |

| P13-T04 | DONE | Real NPL state-deadline/inactive/recreated-board tests plus normative lifetime gap retention; resync can restore reported state but lifecycle stays INCONCLUSIVE, replay hashes match while trace remains PARTIAL_COVERAGE_GAP. NormativeObservationTest 3/3 pass in final 20-test gate. |

| P14-T01 | DONE | One native model/system/writer with typed transactional savepoints, immutable read view and binding inventory (source IDs, helper kinds, n-ary end order/qualifiers); V2 M2 observer attributes match compiled helpers. 25 tests pass, 0 failures/errors/skips: target/semantics-binding-evidence-gate.log; native GUI regression gate 21 pass. Existing atomic native batch is reused instead of a second SemanticMutationPlan engine. |

| P15-T01 | DONE | MappingBundle/TraceComplement attached to native model, optional hash-checked mapping.json included in backwards-compatible replay catalogue; static provenance enters semantic result hash. Replay validates model/mapping equality, unchanged old bundles remain valid. Seven verdicts, temporal state, gap and failure evidence preserved. 25-test evidence/native gate passes. |

| P14-GATE | DONE | Same-system identity/export parity, typed values, retained negative state, malformed rollback, read-only GUI and invariant registry exercised across Phase-1/2/8/14 gates. M2 capability subpackage explicitly describes observer attributes; association symbols come from compiled binding inventory. |

| P15-GATE | DONE | Native replay and seven-valued evidence verified, including journal/view failure and unexpected semantic exception: publishes STALE/ERROR with prior verdict as witness, never cached PASS. NativeSemanticFailureTest 3/3; installed native + semantic replay pass in 20-test final scope. |

| P16-T01 | DONE | FINITE_FIXED_POPULATION_V1 requires explicit complete scalar vectors, fixed links/population, actor/source labels, executive state, logical clock and authored bindings/assumptions. Native USE OCL evaluates invariants/guards/pair-state @pre post; DFS carries each context monitor history with no merging/POR. |

| P16-T02 | DONE | 14 exploration/OCL/temporal/binding tests pass (target/semantics-exploration-gate.log). Oracle covers conformant/weak/nonconformant, unknown/zero terminals, loop/step/population/clock cutoffs, two-actor shared state, distinct histories at identical endpoint, spurious observation witness rejection. Results export full bounds/assumptions/frontier and typed checks; scope is finite relation only. |

| P16-GATE | DONE | Named finite relation extension accepted; no synthesis of Java/ASL/NPL successors, no live behavioral equivalence or unbounded proof. State merging/POR disabled; witness matching covers only supplied complete observation vectors, provenance digests retained. |

| P17-T01 | DONE | Restricted offline Ecore structural lens implements GetPut byte equality when unchanged; PutGet normalized role/context/interface and OPlan operator/order edits; exact source/complement digest checks, unselected feature equality, reference rebasing and no writes to sibling source/live MAS. 15 tests pass, 0 failures/errors/skips: target/semantics-roundtrip-gate.log. |

| P17-T02 | UNSUPPORTED | Ecore structural lens cannot reconstruct arbitrary Java/ASL or live execution. Supplied Property has name/arity but no scalar datatype/value; selected type/value/effect edits are explicitly UNSUPPORTED. Ecore role/group cardinalities are source declarations, not the richer MOISE pair-scoped policy. Official JCM/MOISE/Java patch generation and semantic equivalence are not claimed by ECORE_STRUCTURAL_LENS_V1. |

| P17-GATE | DONE | Named restricted lens accepted with stale/conflict/collision/multi-owner/AND ambiguity tests and exact preservation of references/unselected values. Edited XML layout/comments not preserved; original bytes remain in complement. No general bidirectional semantics claim. |

| P18-T01 | DONE | All 65 matrix rows audited with exact executable test anchors in implementation-report.md, distinguishing unit/typed observation/live oracle and explicit excluded cases. Shared artifact reevaluates all four actor-bound goals on one version; duplicate property/type drift and same-role context/OS cross-binding negative oracles pass. |

| P18-T02 | DONE | Fresh current-source full relevant JUnit platform run: contract 18, official adapters 68, core 14, GUI 1, plugin 561; 662 tests total, zero failures/errors/skips. target/semantics-final-acceptance.log. Packaged replay adds two isolated IT cases; final 20-test scoped gate also passes. Legacy JUnit3 core tests are not claimed discovered by this runner. |

| P19-T01 | DONE | Corrected authoritative property facts; all initial raw goals SATISFIED, no later raw UNDETERMINED; finite matrix 1/10/50 agents, 1/10/100 artifacts and up to 1000 goals. Cache/direct full reevaluation hashes and complete replay match; two source writers retain 20 updates. Final 20-test gate passes (target/semantics-final-packaged-smoke.log); accepted metrics target/semantic-performance.json under use-plugin. |

| P19-T02 | PARTIAL | Measured maximum-throughput finite normalized adapter-contract workload, bounded timing rings/retention and concurrent-writer/replay correctness. Full live MAS open-loop arrival-rate/slowdown/SLA and long-duration retained-heap measurements remain unmeasured; no general scalability claim. |

| P20-T01 | DONE | Fresh package succeeded (target/semantics-final-package.log). NativeRuntimePackagedReplayIT and SemanticPackagedReplayIT pass in isolated JVMs using installed ZIP/JAR + USE distribution; static complement, seven-valued violation/temporal history and Step read-only parity. Part of 20/20 pass gate, no skip. |

| P20-T02 | DONE | Whole-plan phase/task/acceptance audit completed; implementation-report.md records actual architecture, mapping, supported profile, changed files, exact commands, tests, measurements and remaining limitations. Per-run claim/hash manifest: use-plugin/target/semantic-acceptance.json. |

| P18-GATE | DONE | 662 full relevant tests and two installed replay IT cases accepted. No skip/zero-discovery counted as semantic acceptance; historical failure logs remain, fixed causes documented in final report. |

| P19-GATE | PARTIAL | Named finite normalized-contract workload accepted with full verdict/state/property assertions, cache/direct hash parity, retained history bounds and two-source serialization/replay. Full live MAS rate/slowdown/SLA and long-duration retained-heap claim remains unmeasured. |

| P20-GATE | DONE | Final named observer/finite-explorer/structural-lens deliverable accepted: package success, current-source 662 tests, two installed replay ITs, full read-only input hash preservation and diff check. UNSUPPORTED/PARTIAL subareas stay explicit; not a blanket all-JaCaMo semantic equivalence claim. |

<!-- EXECUTION_LEDGER_END -->

Execution discrepancy D01 (16/10/2026): OfficialProjectAdapter produces static JCM/ASL/OS definitions but no live CArtAgO environment. CSL operation contracts now also admit an exact reflected declared @OPERATION interface via DomainProjection.declaredOperation; authority is explicitly REFLECTED_DECLARED_OPERATION, never a fabricated runtime descriptor. Invocations still require the official runtime signature/UUID/correlation. If the declared Java class is unavailable, schema admission rejects with DECLARED_OPERATION_SOURCE_INTERFACE_UNAVAILABLE. Same native operation installer is reused. This makes static configured interfaces usable before the first runtime snapshot.


Execution discrepancy D03 (16/10/2026): Full live regression showed global start/end watermark validation can reject every cut when agents continue cycling while other sources are captured. SnapshotCoordinator now validates each source within its own capture interval and records that source's end watermark; callbacks after an earlier source cut remain replay tail even if other sources are still being read. Topology and overflow checks remain mandatory. Metadata names LOCAL_CAPTURE_INTERVAL and explicitly declares globalAtomicity UNSUPPORTED. This implements a vector of validated local cuts, never a globally atomic MAS state. Jason additionally supplies captureCut under a shared publication barrier: immutable cycle observation publication and normalized callback sequence are excluded from the same cut; raw live interpreter extras remain evidence-only. Unit cut tests and actual SeparateJvmBridgeTest (3/3, 866 observed facts) passed in target/semantics-publication-cut-gate.log. This still does not provide global MAS atomicity.

Execution discrepancy D02 (16/10/2026): The existing MissionSemantic omitted Mission.getPreferables(). An additive preferredMissionSemanticIds supplement is now captured through the official API, preallocating cross-scheme IDs. Empty preferences preserve the original 1.0 wire shape/hash; the decoder accepts that old shape and rejects unrelated fields. Nonempty preference IDs are validated and retained in the source/observer complement; they are not assignment priorities or iStar dependency semantics.

## 1. Executive Summary

Chọn **một pipeline native USE duy nhất**, mở rộng pipeline hiện có bằng semantic profile và binding rõ ràng. Giữ official adapters, bridge protocol, identity, event ledger, coordinator, native state và replay. Thêm lớp CSL organizational/domain semantics, observer state, iStar requirements/monitor và detailed verification results trong use-plugin. Không nối thêm một MSystem do use4Goal sở hữu; không sao chép translator legacy vào production.

Mục tiêu có thể cam kết là kiểm tra state và transition của **phép chiếu có kiểu, có coverage**, rồi đánh giá yêu cầu iStar trên trace quan sát hoặc một transition abstraction hữu hạn. Không cam kết full BDI equivalence, arbitrary Java effects, toàn bộ MOISE/NPL hoặc inverse tổng quát. Đây là kết luận giữ nguyên từ report, mục 1, 10–12, 15. [R]

Mốc đầu tiên là Phase 1 chạy thật: A enact R trong C; domain view X có p:Integer; invariant Nonnegative; contract increment dùng immutable invocation pre-state; leaf ACHIEVE XReady. Các phase đánh số 2–20 mở rộng nền này; các phần nền của Phase 8/10/11/12/14/15 phải có bản tối thiểu ngay trong Phase 1, không chờ đến cuối roadmap.

Ba mức nghiệm thu độc lập:

| Mốc | Sản phẩm phải chứng minh | Giới hạn claim |
|---|---|---|
| G1 — Vertical slice | Official runtime observation → cùng MSystem → OCL state/two-state → leaf ACHIEVE → evidence | Monitoring profile v1 |
| G2 — Thesis monitor | Organizational/domain profile đã chọn; functional/Jason/mental observations; temporal monitor; replay; migration | Các feature trong capability manifest; norm có scope riêng |
| G3 — Research extensions | Bounded explorer và offline round-trip trên fragment công bố | Bounded/observational/structural claims có điều kiện; không unbounded proof |

Quy ước trạng thái ban đầu: **EXISTING** = tìm thấy implementation, không hàm ý đã chạy lại; **PARTIAL** = có nền nhưng thiếu semantics/coverage/integration; **TODO** = công việc thiết kế cụ thể chưa viết; **BLOCKED** = chưa được phép nâng claim do thiếu prerequisite được nêu; **UNSUPPORTED** = ngoài profile, phải có diagnostic. Không đánh DONE cho code chưa được tạo và kiểm thử.

## 2. Source of Truth và bằng chứng

### 2.1. Thứ tự và phạm vi sử dụng

1. [report.md][R] là chuẩn kiến trúc/semantics cao nhất của kế hoạch. Mọi mở rộng dưới đây là **đề xuất**, không sửa kết luận của report.
2. Source hiện tại quyết định API và trạng thái implementation. Khi source khác giả định trong thiết kế: ghi discrepancy, thêm regression case, cập nhật profile có version; không sửa semantics để vượt guard.
3. [PDF][P], IV-A/Table I/Definition 1 và IV-C/Definitions 3–7, là cơ sở formal. Các lựa chọn initialization, episode, unknown và runtime boundary mà PDF chưa xác định được ghi là policy của profile.
4. [jacamo_v2.ecore][E] là abstraction đầu vào được giao; không phải runtime metamodel hoàn chỉnh. Không đánh đồng với file frozen jacamo_v2_complete.ecore trong USE. [U18]
5. use4Goal là nguồn đối chiếu semantics/ý tưởng và các khác biệt đã biết. Không tạo production dependency vào repository này.

### 2.2. Baseline xác minh trong lượt lập kế hoạch

| Nguồn | Revision/fingerprint | Trạng thái |
|---|---|---|
| use | 00b27a1f5385720a135e2adbc65c8523ffa4e9bd; main, ahead origin/main 1 | Working tree sạch trước khi tạo kế hoạch |
| jacamo | 04fc08df259d94045f6cfc95637d56faa1cb33b2 | Hai thay đổi auction có sẵn; chỉ đọc |
| use4Goal | 6f3e73801239fd8bfb94ca8d04ba531b903c0ac9 | Hai thay đổi Classroom có sẵn; chỉ đọc |
| report.md | SHA-256 dd7c2a70514b8fbdac8945f3e1270fe44c69a623f6161284cf8261349941da82 | Đã đọc và đối chiếu các đường source trọng tâm |
| jacamo_v2.ecore | SHA-256 4ae51638a078f0a933982844063993084f17d420694ac8a868d95b9df063c35c | 21 EClass, 7 EEnum; đã đọc classifier/features |
| PDF, 6 trang | SHA-256 398a8b1f803971b151af46a0ce689c4cb5f3e75b8f9c31df0abf60f1536f3dab | Đã đọc text cả 6 trang; định nghĩa/table đối chiếu với report |

USE Maven parent có 6 module: jacamo-bridge-contract, jacamo-bridge-jacamo, use-core, use-gui, use-plugin, use-assembly. Kiểm kê Git tại thời điểm audit: 2.773 tracked files; tương ứng 51 / 48 / 1.232 / 684 / 611 / 8 file trong các module trên. Đây là audit kiến trúc toàn repository và các đường xử lý liên quan, không tuyên bố đọc từng dòng của mọi GUI/generic UML class. Sau đợt dọn kiến trúc ngày 17/10/2026, JaCaMo chạy trong JVM độc lập và USE nhận model/runtime/control qua authenticated loopback TCP; các module nhúng runtime và bản vá framework phục vụ nhúng đã được loại bỏ. [U01]

Dependency baseline: JaCaMo 1.3.1, Jason 3.3.2, CArtAgO/JaCa 3.1, MOISE 1.1, NPL 0.6.1 theo JaCaMo artifact; host USE 7.5.0, use4Goal parent 7.1.1. MOISE pom riêng tham chiếu Jason 3.3.0/NPL 0.6, nên Phase 0 phải pin effective dependency tree và runtime fingerprints, không suy từ một pom. Lượt này đọc pom và javap, **không chạy Maven build/test**. [U01] [J01] [N01]

Source dependency Jason/CArtAgO/MOISE đã có tại thư mục audit tạm được report chỉ rõ; phải giữ version/hash trong evidence bundle tương lai, không phụ thuộc đường dẫn tạm để replay. Với NPL, đã đọc API/bytecode của JAR 0.6.1 và đọc source chính thức StateTransitions/NPLInterpreter trên upstream; source master là tham khảo, không tự chứng minh byte-identical với JAR 0.6.1. [N01] [N02] [N03]

Các mã [Bxx], [Uxx], [Gxx], [Jxx], [Nxx], [Txx] trỏ tới chỉ mục source cuối file. Tên/class mới được ghi **proposed**; chỉ mục existing chỉ chứa path đã tìm thấy.

## 3. Current System Audit

### 3.1. Thành phần phải giữ và điểm mở rộng

| Existing component | Implementation/API xác nhận | Quyết định cho roadmap | Trạng thái |
|---|---|---|---|
| OfficialProjectAdapter | Parse project và sinh semantic contract | Giữ extractor; thêm rule admission/trace phía consumer | EXISTING [B01] |
| OfficialJasonAdapter | adapt; official Agent.parseAS; typed plans/body/beliefs/goals; initial goals | Giữ; mở rộng metadata/query fragment, không dùng làm current-goal oracle | EXISTING [B02] |
| OfficialMoiseAdapter | load/capture; SS roles/groups/cardinalities/relations, FS/NS | Giữ semantic IDs và full functional IR; không parse XML bằng tên lại | EXISTING [B03] |
| OfficialCartagoAdapter | capture; controller population, UUID, property fullId/valueTypes, op descriptors | Giữ; thêm scoped coverage/type admission | EXISTING [B04] |
| BridgeAgArch | reasoningCycleFinished; beliefs, achievement triggers, action events | Giữ hook; mở rộng structured mental observations/occurrences khi cần | PARTIAL [B05] |
| JasonSnapshotSource | capture, topologyFingerprint, registry-backed state, fallback evidence | Giữ; không nâng fallback thành faithful complete | PARTIAL [B06] |
| CartagoSnapshotSource | capture; ICartagoLogger; property delta, operation/lifecycle callbacks | Giữ toàn bộ observer infrastructure; bổ sung schema/coverage checks | EXISTING [B07] |
| MoiseBoardSnapshotSource | capture; accepted players/commitments/goal state; poll | Giữ; source completeness hiện luôn PARTIAL, cần scoped completeness | PARTIAL [B08] |
| MoiseGoalObservation | capture/decode; board identity, goalState arity 5, goalArgument arity 4 | Giữ làm board-state oracle; không thay bằng Jason goal heuristics | EXISTING [B09] |
| NplBoardSnapshotSource | capture/listeners active/fulfilled/unfulfilled/inactive | Giữ evidence-only, mở rộng incarnation/structured norm evidence | PARTIAL [B10] |
| SnapshotCoordinator | capture; buffer first, watermarks/topology/retry; Capture(snapshot,replay) | Giữ; mở rộng envelope cut evidence, không phát minh global clock | EXISTING [B11] |
| ReasoningCycleGate | exact-incarnation admission, pause/ACK/epoch/departure | Giữ ở control layer; không dùng như global artifact/NPL freeze | EXISTING [B12] |
| BridgeEntityId | authority, dimension, kind, scope, localId, incarnation; canonical/parse | Giữ nguyên định danh; bổ sung trace crosswalk có proof | EXISTING [B13] |
| NativeUseModelBuilder | build hai pass; classes/operations/properties/constraints | Mở seam chọn semantic binding; một model cho một session | PARTIAL [U03] |
| NativeProjectionPolicy | VERSION 3.0.0; native vocabulary/class admission | Giữ policy cũ cho session cũ; CSL profile có policy explicit riêng trong cùng pipeline | EXISTING [U04] |
| MoiseDomainProjection | contextual role association class, functional objects/operator/order | Giữ behavior cũ; tái sử dụng validation/IDs; CSL binding không gán nhãn lại encoding này | PARTIAL [U05] |
| NativeRuntimeMutationEngine | apply/savepoint/rollback/tombstones; authoritative reconcile | Mở typed mutation/binding seam; giữ kernel transaction/identity; cải thiện snapshot diff | PARTIAL [U07] |
| RuntimeVerificationCoordinator | transaction/read/finish/freeze; EDT writer, journal, snapshots | Một owner; thêm semantic checkpoint participant, monitor state và detailed envelope | PARTIAL [U08] |
| ExternalOclConstraintService | installSource; compile invariants; evaluate; AST dependencies | Tái sử dụng compiler/registry/policy; phân loại chi tiết trước legacy mapping | PARTIAL [U09] |
| NativeOperationCheckpoints | observe; key session+generation+artifact+correlation; OperationCheck | Giữ correlation/pre-state; tách classified failure/unsupported/capacity diagnostics | PARTIAL [U10] |
| CodeGroundedNativePipeline | build; same model identity; export/recompile structural hash | Giữ điểm vào duy nhất; nhận semantic profile và mapping bundle | EXISTING [U02] |
| NativeRuntimeProjector | applySnapshot/apply; revision, sequence, dedup, resync | Giữ admission/ledger; snapshot CSL dùng reconcile identity, không reset mọi object | PARTIAL [U11] |
| NativeUseStateBuilder / NativeObjectBindings | tạo initial state, ID aliases/metadata | Tái sử dụng; CSL profile không materialize declaration thành live instance | EXISTING [U06] [U12] |
| NativeRuntimeReplay / journal / checkpoint / retention | export/replay, hashes, snapshots | Mở bundle có profile/markings/complement, không viết replay engine thứ hai | PARTIAL [U13] [U14] [U15] |
| GoalViewSnapshot | DTO đọc Scheme/OrganizationalGoal runtimeState | Giữ org-goal view; thêm intentional marking riêng, không đổi nhãn runtimeState thành satisfaction | PARTIAL [U16] |

Không thay thế bridge, identity scheme, evaluator USE hoặc coordinator. Phần **thay thế bên trong profile mới** là quyết định mapping/codec hardcoded hiện nay và cách gộp verdict; API legacy vẫn có wrapper tương thích. Phần **không dùng làm backend mới**: use4Goal Csl2UseTranslator legacy, NativeOclEvaluator subset, CslOclTransitionEvaluator regex @pre, BPMN explorer, translator historical V1. [G01] [G02] [G05]

### 3.2. Những khoảng trống bắt buộc giải quyết

| Gap | Bằng chứng hiện tại | Hạng mục xử lý |
|---|---|---|
| Native không phải CSL | Agent/Belief/AgentGoal/Workspace/Artifact/Organization/Group/Scheme/OrganizationalGoal/Mission; role là MAssociationClass | P0/P1/P2/P14: versioned binding, tách CSL khỏi observer [U04] [U05] |
| Numeric type khác yêu cầu p:Integer | DomainProjection.propertyType đưa Byte/Short/Integer sang Real | P1 strict typed codec; không đổi behavior mặc định cũ [U17] |
| v4 execution guard | CslModel.requireLegacyRuntime chặn v4 | Không bỏ guard; CSL-compatible IR/backend trong USE [G01] |
| User invariants thiếu ở legacy translator | translate không duyệt model.invariants | P1/P8/P14 bijection constraint manifest ↔ compiled registry [G02] |
| Snapshot resync có reset | NativeRuntimeProjector.applySnapshot gọi mutations.resetToBaseline | P1 minimal identity-preserving diff; P11 mở rộng lifecycle/resync, test pending @pre [U11] |
| Undefined bị gộp ERROR | ExternalOclConstraintService.evaluate; DefaultVerificationService | P1 detailed typed outcome; P8 dùng chung evaluator result adapter [U09] [U19] |
| Postcompile parameters | UseModelApi string pre/post overload khai báo self/result nhưng chưa thêm op parameters | Tái sử dụng cách compiler context trong NativeOperationCheckpointTest; kiểm parser diagnostics/Boolean type [U20] [T02] |
| Temporal khác PDF | Symbolic NONE latch như ACHIEVE; TaskMarking preSeen/postSeen không có violated | P9 profile explicit/truth-table tests; không copy monitor đó [G03] [G04] [G06] |
| NPL không phải invariant | Adapter EVIDENCE_ONLY; native norm translation unsupported; generator TODO maintenance | P13 normative observer/monitor độc lập, giữ unsupported [B10] [U05] [J04] |
| Ecore/grammar/Java không đồng bộ | CSL thiếu features dùng trong annotations; iStar builder bỏ một số grammar features | P2/P3 schema crosswalk/validation trong USE, không sửa use4Goal [G07] [G08] [R] §7.5 |

## 4. Architecture — một đường production

~~~mermaid
flowchart TD
  JS[JaCaMo specification / official parsers] --> SC[Existing semantic contracts]
  SC --> MAP[Profile admission + mapping + TraceComplement]
  MAP --> CSL[CSL schema + iStar requirements + OCL contracts]
  CSL --> PIPE[CodeGroundedNativePipeline / native builders]
  PIPE --> MODEL[One authoritative MModel and MSystem per session]
  JR[JaCaMo runtime] --> AD[Existing BridgeAgArch / snapshot sources]
  AD --> CUT[SnapshotCoordinator + wire contracts]
  CUT --> ADMIT[NativeRuntimeProjector admission / identity / coverage]
  ADMIT --> OBS[Immutable CSL state observation + observer evidence]
  OBS --> MUT[NativeRuntimeMutationEngine + semantic binding]
  MODEL --> MUT
  MUT --> CO[RuntimeVerificationCoordinator serialized commit]
  CO --> OCL[USE OCL on pinned current / invocation pre states]
  OCL --> IM[iStar monitor: previous marking to current marking]
  IM --> RES[Detailed result / evidence / counterexample]
  RES --> JOURNAL[Existing journal / checkpoints / replay]
  RES --> VIEW[USE object diagram + verification / goal views]
~~~

Semantic profile là lựa chọn **trước import/schema compilation**, bất biến trong model revision. NativeProjectionMode AUTO/FULL vẫn là mức materialization; không dùng nó như nhãn semantics. Đề xuất thêm SemanticProjectionProfile với giá trị NATIVE_CURRENT và CSL_OBSERVER_V1, cấu hình use.jacamo.semantics.profile. Session cũ mặc định NATIVE_CURRENT. Chuyển profile cần import/new session; không đổi nghĩa schema đang có.

Một proposed UseProjectionBinding cung cấp symbol/type/relationship bindings và runtime mapping handlers cho builders/engine đang có. Native mapping hiện tại trở thành default implementation sau refactor nhỏ, được khóa bằng regression tests. Proposed CslUseBinding cung cấp binding cho CSL schema/observer. Hai binding strategies dùng chung pipeline/transaction/evaluator/identity/journal; không có hai active mirrors để reconcile.

iStar marking là immutable semantic state bên cạnh CSL state, thuộc cùng checkpoint/version; không phải một MSystem thứ hai. Observer evidence có thể nằm ngoài USE nếu constraint không cần; khi materialize phải có namespace/kind rõ. Một helper MObject cho NormOccurrence/BeliefObservation không phải CSL Entity.

## 5. Semantic Principles

| Nguyên tắc bắt buộc | Hệ quả coding / review |
|---|---|
| M2 ≠ M1 ≠ M0 ≠ behavior | Ecore classifier, definition, live object, runtime transition có DTO/IDs riêng |
| Agent ≠ CSL Entity | AtomicAgent là player helper; iStar Agent là viewpoint có binding |
| Belief ≠ world fact | Mental predicates đọc observer; artifact domain view đọc source authority CArtAgO |
| Active Jason goal ≠ requirement satisfied | Satisfaction chỉ từ authored OCL và temporal profile |
| Declaration ≠ enacted/live instance | JCM initial role request không tạo accepted Plays trước board evidence |
| Missing ≠ false/deleted | Gating coverage; partial snapshot không được reconcile absence |
| Unsupported ≠ fail; exception ≠ violation | Seven-valued result domain tại §12/16 |
| OCL postcondition ≠ operation implementation | Không chạy OCL để giả lập arbitrary Java effect |
| AND ≠ execution order/concurrency | OPlan operator/order/enabledness giữ riêng |
| Monitor ≠ enforcement | Mặc định REPORT_ONLY; invariant false không tự pause/rollback JaCaMo |
| One writer / pinned snapshots | Không đọc live mutable MSystemState từ worker hoặc callback thread |
| Counterexample phải tồn tại | Invariant false không rollback observed state; chỉ rollback malformed projection |
| Names chỉ để trình bày | ID/crosswalk có evidence; ambiguity thành diagnostic |
| Một trace tốt ≠ mọi behavior đúng | Verdict luôn có scope/prefix/bounds/assumptions |

Mục tiêu correctness của supported projection α và predicate translation C: với đủ coverage và kiểu hợp lệ, Eval_source(q,s) = Eval_USE(C(q),α(s)). Eval_source phải được xác định là world predicate, stored belief, Jason entailment hay board-reported state. Không trộn bốn oracle. Với transitions, kiểm correspondence của selected observable boundaries; không suy full simulation từ vài tests. [R] §14.

## 6. Supported Profile

### 6.1. SemanticsProfile v1 — phạm vi G1

Profile proposed JACAMO_CSL_MONITOR/1.0 gồm: source concept admission; M2/M1/M0 mapping; identity/incarnation; source scope/coverage; schema/constraint hashes; state/transition boundary; type codecs; OCL dialect/capability; leaf ACHIEVE policy; unsupported catalog.

| Thành phần | Điều kiện admission v1 | Target |
|---|---|---|
| Group | Definition có exact source ID; live GroupBoard identity và accepted players | CSL OrgCtx type + context occurrence |
| Role | Simple concrete position, exact group-role reference; chưa multiple inheritance/scoped policy | CSL Role + RoleOccurrence |
| Agent | Official runtime incarnation; exact registry/crosswalk | AtomicAgent player |
| Enactment | Actual player/role/context tuple; lifetime known hoặc explicitly observation-lifetime | Plays + InContext |
| Artifact | Explicit domain-view registration bằng artifact type semantic ID và source evidence | CSL Entity subtype; runtime UUID object |
| Property | Unique occurrence cho slot; arity=1; approved stable scalar type | Typed CSL attribute / USE Value |
| OCL invariant | USE-compiled Boolean; registered source ID; dependency closure covered | Current-state evaluation |
| Contract | Authored operation binding; signature/arguments/correlation; paired boundaries | pre(before), post(before,after) |
| Goal | Leaf ACHIEVE; stable requirement ID; exact actor/role/context/self binding | Historical satisfaction latch |

Integer codec v1 nhận Byte/Short/Integer trong miền IntegerValue của USE; giữ Integer, không tự đổi Real. Real codec chỉ finite Float/Double và công bố giới hạn floating-point/precision; không bảo toàn Java overflow hay arithmetic execution. Boolean/String nhận giá trị đúng kiểu. Long ngoài range, null cho required slot, tuple/object/duplicate name/type drift trả diagnostic. Không suy property type từ lần quan sát đầu rồi đổi schema im lặng.

V1 đánh lại mọi constraint/goal admitted sau accepted boundary để có oracle correctness. Incremental selection chỉ tối ưu ở P19 sau dependency-equivalence tests. OCL allInstances/quantifier chỉ được PASS khi population dependency đầy đủ trong scope đã khai báo, không chỉ vì có một self object.

### 6.2. Mở rộng theo capability

P2–P15 lần lượt bổ sung structural, functional, mental, temporal và normative capabilities. Mỗi capability ghi introduced profile version, prerequisites, source coverage, loss và regression tests. Chỉ bump major khi thay meaning; thêm capability không được làm thay đổi verdict của trace v1. Compound/refinement/Task/temporal types ngoài ACHIEVE chưa được quảng bá trong G1.

## 7. Unsupported Profile

| Feature | Trạng thái ban đầu / lý do | Hướng được phép sau này |
|---|---|---|
| Full JaCaMo ↔ CSL behavioral equivalence | UNSUPPORTED: abstraction không injective, thiếu scheduler/Java/BDI semantics | Không là mục tiêu DoD |
| Arbitrary Java/internal action synthesis từ OCL | UNSUPPORTED: contracts không xác định một implementation | Template có proof/handwritten binding |
| Generic Jason Plan → Task | UNSUPPORTED v1 | P6 explicit finite abstraction; remainder TRACE_ONLY |
| Arbitrary belief/rule → world truth | UNSUPPORTED | P7 stored observations hoặc explicit typed query |
| MOISE maintenance goal normative realization | PARTIAL/UNSUPPORTED generator path có TODO | P4/P13 quan sát trực tiếp; không tự gán Maintain |
| Multiple MOISE role parents → CSL v4 single parent | UNSUPPORTED trực tiếp | Complement + extension đã formalize; không drop parent |
| Scoped/directional compatibility/link policy → plain CSL relation | PARTIAL | Policy layer riêng; reject nếu constraint cần semantics chưa có |
| Scheme/Mission/Norm → Entity tự động | UNSUPPORTED | Functional/normative observer metamodel có purpose |
| OPlan full execution qua AND/OR | UNSUPPORTED | P5 executive trace/transition policy giữ operator/order |
| Arbitrary structured CArtAgO values | UNSUPPORTED v1 | P2/P10 explicit codecs/schema |
| Global linearizable MAS cut từ timestamps/agent pause | UNSUPPORTED | P11 controlled boundary với assumptions/capability riêng |
| Unbounded liveness/full model checking | UNSUPPORTED | P16 finite bounds có cutoff report |
| Live inverse: USE edit → mutate JaCaMo private state | UNSUPPORTED | Ngoài roadmap; command adapter cần yêu cầu riêng |
| Activation/forall/pick chỉ parse được ở use4Goal | PARTIAL/UNSUPPORTED tại backend mới cho đến khi có semantics tests | P3/P9 admission, không suy từ grammar |

Mọi encountered element phải có MappingDecision: admitted, abstracted, trace-only hoặc unsupported với source ID/reason. Explicitly excluded không phải silently ignored; preflight trả coverage report ngay cả khi feature không materialize.

## 8. Source → Target Mapping Matrix

DIRECT dưới đây là khả năng biểu diễn trong fragment có điều kiện, **không phải trạng thái đã implement**. O = required OCL; I = required iStar; RT = runtime binding. Inverse chỉ nói fragment offline có complement; toàn bộ row đều chịu P17 admission.

| Rule | Source | Target / mapping type | Lost semantics / điều kiện | O / I cần thiết | RT | Inverse |
|---|---|---|---|---|---|---|
| M01 | Organization | CSL context namespace / PARTIAL | Tách OS definition/deployment; FS/NS đi view khác | O structural Φ; I actor correspondences | Organization/group board scopes | Skeleton + complement; không toàn OS behavior |
| M02 | Group | CSL OrgCtx / DIRECT trong subset | Nested spec không sinh fixed population; contextual bounds giữ riêng | O nesting/cardinality; I context binding | Board ID → c:C | Structural có trace; accepted state không là startup request |
| M03 | Role | CSL Role / PARTIAL | Source shared global role; Ecore containment/cardinality không đủ; multi-parent có thể unsupported | O enactment/abstractness/compatibility | r:R có player/context/lifetime | Single-parent subset; nguyên source khác giữ complement |
| M04 | Link | Structural policy + trace / PARTIAL | Type/scope/direction/extendsSubgroups; CSL v4 không generic Role–Role association | O approved policy predicates; I không tự dependency | Link policy observations; không fake message links | Chỉ khi original policy giữ đủ |
| M05 | Norm | Normative contract/observer / TRACE_ONLY v1 | Modality/condition/deadline/lifecycle, không state invariant mặc định | O pure predicates nếu approved; I duty requirement binding explicit | NPL instance events/state | Original normative source + supported edits; không infer từ invariant |
| M06 | Scheme | Functional definition/instance scope / PARTIAL | Không tự là OrgCtx/Entity; responsible groups/arguments/lifecycle | O allocation checks; I instance of goal model | SchemeBoard UUID/scope | Structural + complement, behavior không tự phục hồi |
| M07 | Mission | Functional allocation / PARTIAL | Goal membership/preference/scheme-mission bounds | O commitments/cardinality; I actor means binding nếu authored | Commitment(player,mission,scheme,lifetime) | Membership/bounds subset |
| M08 | OGoal | Intentional requirement + organizational observation / ABSTRACTED | performance/achievement/maintenance khác temporal iStar; args/min/TTF cần bind | O world hoặc board predicate; I explicit temporal type | waiting/enabled/satisfied + committed/achieved | Definition subset có complement; no behavioral inverse |
| M09 | OPlan | Refinement outcome + execution descriptor / PARTIAL | AND/OR không giữ scheduler; operator + ordered children bắt buộc | O predecessor/enabledness tests; I AND/OR | Step/partial-order evidence | Tree/operator/order structural round-trip |
| M10 | Agent | Atomic player; optional iStar Agent viewpoint / DIRECT runtime identity | BDI state/behavior không ở CSL Entity | O player relations; I explicit actor binding | Agent incarnation/cross-authority alias | Declaration subset; không runtime restore |
| M11 | Belief | Mental observer / TRACE_ONLY hoặc PARTIAL typed predicate | Owner, namespace, terms, negation, annotations, rules/entailment | O stored/query contract; I chỉ khi authored requirement | Per-agent cycle observation | Selected initial facts syntax; không world semantics |
| M12 | AGoal | Invocation observer + explicit requirement correspondence / ABSTRACTED | Achievement/test, attempts, failures, unifier, intention | O authored condition; I requirement riêng | Owner+occurrence+origin | Source declaration complement; không reconstruct intention |
| M13 | Plan | Executive AST/trace; optional Task / ABSTRACTED | Trigger/context/body/subplans/control/unification | O supported applicability + pre/post; I Task only explicit | Selected plan/IntendedMeans evidence | Skeleton/complement; no generic semantic round-trip |
| M14 | Event | Trigger definition / occurrence journal / TRACE_ONLY | Queue/origin/order/lifetime không là persistent attribute | O event-derived predicate only declared | Source+sequence+occurrence+correlation | Definition fragment; replay quan sát, không restore scheduler |
| M15 | Action | Invocation / Task abstraction / PARTIAL | Internal/external/feedback/side effects | O pre/post effects authored; I means binding | ActionExec + operation correlation if exact | Contract/template only |
| M16 | Environment | Runtime topology/authority / TRACE_ONLY | Không là passive domain Entity; external world không đầy đủ | O observer topology predicates if needed | Environment/run ID | Deployment skeleton |
| M17 | Workspace | Workspace observer; optional domain/context correspondence / PARTIAL | WSP ≠ organizational context mặc định; join ≠ focus | O topology/membership; I chỉ explicit H | UUID/full path/parent/environment | Deployment structure có trace |
| M18 | Artifact | CSL Entity **domain view** / ABSTRACTED | Java/guards/await/internal state giữ ngoài CSL | O selected world attributes/contracts; I authored goal predicates | Artifact UUID/incarnation/workspace | Domain schema/template, không Java behavior |
| M19 | Operation | MOperation signature + invocation contract / PARTIAL | Contract không là Java body/effect proof; ongoing/suspend | O typed pre/post @pre; I Task binding optional | OpId/signature/args/phase | Signature/template + original source |
| M20 | Property | CSL Attribute / DIRECT scalar unique; otherwise PARTIAL | Ecore chỉ name/arity; value/fullId/type/annotations cần source supplement | O typed value constraints | Property occurrence → declared slot | Scalar interface subset; structured values riêng |
| M21 | Signal | Transient observer event / TRACE_ONLY | No durable truth without declared accumulator/window | O event/history predicates; I activation only explicit | Artifact+sequence+payload | Recorded replay; no delivery equivalence |
| E01 | NormType OBLIGATION/PERMISSION | Typed normative enum / DIRECT vocabulary | Không tự bảo toàn lifecycle; NPL còn PROHIBITION ngoài Ecore | P13, không translate sang Boolean invariant | Preserve raw modality | Exact literal + provenance |
| E02 | GoalType ACHIEVEMENT/MAINTENANCE | Source-goal enum + explicit iStar mapping / PARTIAL | MOISE thêm performance, default performance khác Ecore achievement | P4/P9; tuyệt đối không name-only enum cast | Preserve actual source type | Exact source enum nếu complement đủ |
| E03 | OPlanOperator SEQUENCE/PARALLEL/CHOICE | Execution descriptor enum + AND/OR / PARTIAL | Giữ original operator + ordinal; AND alone mất order | P5 | Observed enabling/done transitions | Có structural operator/order |
| E04 | LinkType AUTHORITY/COMMUNICATION/ACQUAINTANCE | Policy enum / PARTIAL | Source cho custom link types; không cắt về 3 literals | P2/P3 policy constraints | Không suy communication event | Original string/type declaration |
| E05 | LinkScope INTRA_GROUP/INTER_GROUP | Typed policy scope / DIRECT vocabulary | Context/extendsSubgroups/direction vẫn cần | P2 | Exact groups/contexts | Có nếu scope/flags giữ đủ |
| E06 | ActionKind INTERNAL/EXTERNAL | Execution descriptor enum / DIRECT vocabulary | Không phân loại đủ Jason BodyType | P6 contract admission | Action/operation evidence | Syntax subset |
| E07 | AgentGoalType ACHIEVEMENT/TEST | Trigger/query kind / DIRECT vocabulary | Test query không là Achieve requirement | P6/P7 | Goal occurrence/query result | Initial declaration subset |

Evidence: toàn bộ 21 EClass/7 EEnum từ [E]; đối chiếu [B02] [B03] [B04] [B05] [J02] [J03] [J04] và [R] §4–5, 9. Compatibility, GroupRoleCardinality, SchemeMissionCardinality, runtime Intention/IntendedMeans không có EClass tương ứng trong E được giao: bổ sung từ official contract, không bịa EMF feature. MDE loader phải phát SOURCE_ABSTRACTION_INCOMPLETE khi supplementary source bắt buộc nhưng thiếu.

## 9. Static Transformation Architecture

Proposed semantic packages đặt dưới use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics; gọi tắt SP trong các phase. Đây là **vị trí dự kiến mới**, không phải existing code. Subpackages: profile, model, mapping, csl, observer, istar, ocl, runtime, normative, evidence, mde, exploration, roundtrip. Existing codegrounded packages vẫn là native integration owner.

Input thứ nhất là JacamoSemanticSnapshot/JacamoSpecificationModel từ official adapters. Input thứ hai là EMF instance theo E được giao, kèm metamodel hash và optional source supplements. Cả hai đi qua cùng proposed SourceModelNormalizer tạo typed source view; không có hai rule engines. Với EMF thiếu group-role scope/values/arguments, transformation trả missing capability; không tạo default giả.

Proposed MappingBundle gồm CslSchema, RequirementModel, ConstraintSet, CorrespondenceRegistry, TraceComplement, MappingDecision list, source/model/profile hashes. Mọi output element có source IDs, rule ID/version, applicability, loss và location. Symbol allocation tái sử dụng MoiseUseSymbols; tên an toàn/collision-free chỉ là carrier, registry giữ exact IDs.

M2: CslSchema có OrgCtxType, RoleType, EntityType, AttributeDef, AssociationDef/Ends, Generalization, Compatibility và Φ. Core inheritance là Entity→Entity hoặc Role→Role; OrgCtx nesting là containment declaration, không phải source generalization. Các technical UML supertypes ở §11 chỉ là backend encoding. Runtime AtomicPlayer/Enactment nằm trong observer/runtime schema, không thêm thành CSL Entity M2. Organizational policy vượt core như scoped compatibility nằm trong extension có namespace/version.

M1: selected source declarations → types/constraints/intentional graph; artifact schema cần authored domain registration, không suy từ tên class. Invariants sinh và invariants authored đều có manifest. Validate trước khi tạo MModel: references, kind, acyclic/single-parent core inheritance, containment, association end policy, bounds, Boolean conditions, exact actor binding.

M0: declaration không sinh runtime population. Nếu cần show static examples, đánh sourceLayer=SPECIFICATION và không cho current-world constraints dùng chúng. Source fragments giữ trong complement để offline inverse; digest một mình không đủ tái tạo source.

## 10. Runtime Transformation Architecture

### 10.1. Contract tích hợp

Tái sử dụng RuntimeSnapshot/RuntimeEvent/RuntimeFact và ContractEnvelope, không đổi schema wire cũ theo kiểu phá vỡ. RuntimeSnapshot hiện chứa snapshotId/modelRevision/capture interval/start-end watermarks/facts/sourceCompleteness/fingerprint; run/session/generation có ở envelope/events, topology dùng trong coordinator nhưng chưa được xuất thành field snapshot riêng. Proposed SemanticObservationEnvelope **kết hợp** các nguồn đó, không suy run từ snapshot hash. [B11] [B14] [B15]

~~~text
SemanticObservationEnvelope (proposed, immutable)
  schemaVersion, profileVersion, projectionVersion
  runId/sessionId, generation, modelRevision, sourceContractVersion
  snapshotId, acceptedStateVersion, captureStart, captureEnd
  startWatermarks, endWatermarks, replayBoundary
  topologyBefore, topologyAfter, sourceVersions, capabilities
  sourceCompleteness, scopedCoverage, freshness
  agents[exact identity/incarnation/program reference]
  contexts[group runtime identity/spec/parent]
  enactments[player, roleType, context, lifetime/evidence]
  functional[schemes, missions, commitments, goal observations]
  artifacts[UUID, type, workspace], workspaces[UUID,parent,environment]
  properties[fullId, owner, name, typed values, annotations]
  mentalObservations[owner, cycle, provenance, kind, occurrence]
  events/deltas[source sequence, correlation, causality, phase]
  mapping decisions, diagnostics, unsupported features
~~~

Sao chép sâu values/collections trước khi rời source boundary; không truyền live Jason Term/board iterator/ArtifactObsProperty mutable sang consumer. Reuse ContractSupport canonicalization và semantic DTOs trước khi thêm representation mới.

### 10.2. Admission và commit

1. Validate run/generation/revision/profile/wire capability. Exact ID alias phải có registry proof.
2. EventLedger xử lý duplicate/content mismatch; source sequence kiểm stale/gap; timestamp chỉ evidence.
3. Chuyển faithful facts sang immutable typed semantic commands; evidence-only lưu provenance, không mutate world.
4. Coverage theo dependency closure của từng constraint: selected object có thể complete trong source PARTIAL, nhưng chỉ nếu producer đưa proof rõ cho scope đó. Không tự nâng source completeness hoặc cho allInstances pass trên một phần population.
5. Một serialized coordinator transaction: savepoint state/bindings/protocol/monitor; tạo object trước links; update typed values; reconcile **chỉ explicit deletes hoặc complete scoped absence**.
6. Check schema/type/referential integrity. Adapter malformed → rollback batch, mark stale/error; invariant/cardinality requirement false → giữ counterexample và báo FAIL.
7. Pin after MSystemState và invocation pre; evaluate OCL; update relevant markings trên **cùng after version**.
8. Lưu/publish một semantic evidence envelope. Callback/view không nhìn thấy state nửa batch.

V1 dùng snapshots và normalized property/role updates có boundary xác định; mở rộng event taxonomy ở P10–P12. Không hứa cập nhật sau từng BDI micro-step khi source chỉ có cycle hook.

## 11. USE Binding Architecture

### 11.1. Encoding chuẩn cho profile mới

| Semantic element | USE representation proposed | Identity/state |
|---|---|---|
| Atomic player | AtomicAgent subtype của abstract RuntimePlayer | Bridge runtime identity+incarnation; không là Entity |
| Context C | C subtype OrgContext; OrgContext cũng là RuntimePlayer | Group board instance identity; có thể collective-player ở capability P2 |
| Role R | R subtype RoleOccurrence | Một object r cho player/role/context/lifetime; state thuộc r |
| Artifact domain X | X subtype DomainEntity | Exact artifact incarnation; p:Integer |
| Plays | RuntimePlayer[1] player ↔ RoleOccurrence[*] enactedRoles | MLink; technical runtime relation |
| InContext | OrgContext[1] context ↔ RoleOccurrence[*] roles | MLink; technical runtime relation |
| Context nesting | Runtime parent relation với bounds đã khai báo | Không suy population từ declaration containment |
| World associations | MAssociation, optional aggregation/composition | Chỉ authored domain relation hợp lệ CSL |
| Mental/functional/normative evidence | Observer classes khi cần OCL, hoặc immutable sidecar | sourceKind=OBSERVER, không CSL Entity |

RuntimePlayer/AtomicAgent/OrgContext/RoleOccurrence/DomainEntity là backend carriers của profile, không khẳng định các class này đã tồn tại trong source. Role inheritance của CSL subset dùng USE generalization **trên cùng role occurrence**, không tạo parent-role play chain. Không cho association class cũ và role occurrence mới cùng đại diện một enactment trong cùng session.

Cardinality group-role phải kiểm trên từng context và exact role membership closure; global Plays multiplicity không thay constraint đó. Nếu MOISE inheritance/compatibility vượt subset đã chứng minh thì trả UNSUPPORTED, không gán UML subtype rồi coi là tương đương.

Workspace là identity/location observer mặc định. Chỉ tạo Controls(r,x) khi domain binding khai báo; không tự nối do cùng tên hoặc cùng workspace. OCL goal self có thể là x, r hoặc c nhưng phải chỉ rõ classifier và object ID; actor/context vẫn có riêng trong GoalBinding.

### 11.2. Native API được dùng

| Bước | API thực tế | Boundary |
|---|---|---|
| Schema | UseModelApi.createClass/createAttribute/createGeneralization/createAssociation; ModelFactory/MModel | Một lần mỗi model revision |
| Association class | createAssociationClass; createLinkObjectEx | Giữ native profile cũ; chỉ dùng CSL khi semantics thật sự đòi link object |
| Invariant | createInvariant hoặc ASSLCompiler.compileInvariants + registry | Compile all-or-nothing, type-check Boolean |
| Operation contract | typed MOperation; parser Context/post flag; createPrePostConditionEx | Declare parameters/result trước compile |
| Runtime create/link | UseSystemApi.create(system,false); createObjectEx/createLinkEx | Qua coordinator/engine, không né assertUserMutationAllowed |
| Update | setAttributeValueEx(MObject,MAttribute,Value) | Typed values; no string-concatenated SOIL từ untrusted names |
| Delete | deleteLinkEx/deleteObjectEx | Exact incarnation và authoritative evidence |
| Copy | new MSystemState(name,currentState) | Deep copy object slots/link sets; MObject identity giữ lại |
| Evaluate | Evaluator.eval(expr,pre,post,VarBindings) | Fresh evaluator mỗi evaluation; pinned states |
| Check/publish | checkStructure; coordinator coherent view event | Requirement failure được giữ, projection corruption rollback |

[U20] [U21] [U22] [U23]. Snapshot copy không tự immutable theo type: không expose mutable reference, không cho caller setAttribute trên pre copy. Object names allocate từ canonical ID hash có collision detection; không tái dùng tên qua incarnation trong cùng history.

Không rebuild MModel/MSystem mỗi cycle. Unknown type/schema drift ở v1 yêu cầu explicit recompile/resync; additive schema extension hiện có trong MSystemState chỉ là API kỹ thuật, không tự cho phép đổi profile/rewrite history.

## 12. OCL Architecture

Backend chính là **USE OCL compiler và Evaluator của host 7.5.0**. Không chọn native subset/regex evaluator của use4Goal và không thêm parser OCL thứ hai. Collections/navigation/allInstances/quantifiers/user operations chỉ admitted khi host compile/type-check được và dependency/coverage policy hỗ trợ.

Proposed SemanticOclCompiler/OclEvaluationAdapter tái sử dụng registry/compiler hiện có; classify typed Value **trước** khi mapping legacy. ExternalOclConstraintService và DefaultVerificationService gọi chung adapter; không duy trì hai semantics evaluator.

| Verdict | Nghĩa chuẩn |
|---|---|
| PASS | Boolean true với binding/boundary/coverage cần thiết hợp lệ |
| FAIL | Boolean false trên evaluation có đủ dữ liệu; có context/witness |
| UNDEFINED | Backend trả UndefinedValue/null; optional/missing expression value không thành false |
| ERROR | Syntax/type/binding/evaluator/protocol failure có phase/error code; không là violated |
| UNSUPPORTED | Expression/concept/type nằm ngoài capability/profile, chưa được evaluate |
| SKIPPED | Disabled, wrong checkpoint, missing pre, chưa có required observation hoặc chưa applicable |
| INCONCLUSIVE | Aggregate claim/history/bounded exploration không đủ chứng cứ; không Boolean-level false |

USE có thể gộp một số invalid arithmetic thành UndefinedValue; giữ backend value/diagnostics, không tuyên bố phân biệt OCL null/invalid chuẩn đầy đủ nếu host không cung cấp. Universal invariant aggregation: false witness đủ dữ liệu → FAIL; nếu không false và có undefined → UNDEFINED; evaluator error → ERROR; tất cả true và population complete → PASS. Existential đối ngẫu; không dùng expanded invariant conversion undefined→false để quyết định violation. Kiểm coverage trước vacuous truth.

State constraints dùng after; pre dùng invocation before; post dùng đúng before/after với self/args/result. @pre chỉ compile trong post context. Không regex replace, không truyền cùng state vào hai tham số. Pre false là contract precondition FAIL nhưng Task marking undetermined; không tự ngăn source operation.

Operation post report riêng là Boolean verdict của expression; **contract conformance** cần pre true và success boundary hợp lệ. Pre false/undefined/error hoặc failure/abort → post-conformance SKIPPED/INCONCLUSIVE kèm reason; không nói contract được thỏa chỉ vì post tình cờ true.

Capability admission giữ expression source/AST hash, source IDs, target model revision, context, required features/classes/population/sources và checkpoint applicability. Nếu backend không compile được: ERROR với stage; nếu preflight nhận diện feature intentionally excluded: UNSUPPORTED. Không đổi loại để làm test pass.

## 13. iStar Architecture

### 13.1. Representation và binding

Proposed RequirementModel gồm Actor/Agent/Role viewpoints, Goal/Task, Condition, AND/OR refinement, Dependency và explicit CorrespondenceRegistry. P1 chỉ leaf ACHIEVE. Mỗi occurrence khóa bởi requirementId + actor binding + context incarnation + optional scheme/role occurrence + episodeId; requirement ID giữ ổn định qua rename nhờ trace, episode không tự giữ qua run restart.

GoalBinding tách intentional actor, player identity, role type/occurrence, context identity và OCL self object; không resolve bằng same-name USE class. GoalEvaluation giữ previous/current marking, condition verdict, evidence state ID và firstSatisfied checkpoint.

### 13.2. Temporal profile được đề xuất

Profile v1 quy định leaf ACHIEVE trước khi có true là UNDETERMINED; true → SATISFIED; đã SATISFIED thì latch trong cùng episode. False trước achievement không là liveness violation. Đây là operational choice đã công bố trong report, không gán thành công thức đầy đủ do PDF cung cấp.

P9 mở version/capabilities và khóa recurrence bằng tests:

| Type | Proposed rule trên complete observation trong episode | Unknown/history gap |
|---|---|---|
| NONE | Theo propagated value hiện tại | Current unknown → UNDETERMINED; không latch |
| ACHIEVE | Previous satisfied hoặc current true → satisfied; còn lại undetermined | Giữ achievement có witness trong cùng identity/episode; condition result vẫn báo quality hiện tại |
| MAINTAIN | Bất kỳ observed false → violated và latch; current true khi lịch sử đầy đủ → satisfied | Gap chưa repair làm history verdict inconclusive; không tự phục hồi violation |
| SUSTAIN | Trước first true: undetermined; sau first true: false → violated và latch; true → satisfied | Nếu gap có thể chứa loss sau achievement: undetermined/inconclusive, trừ đã có violation witness |
| Task | Tại matching invocation: pre∧post → satisfied; pre∧¬post → violated; ¬pre → undetermined | Không có paired invocation → skipped/undetermined; không latch preSeen/postSeen qua invocations |

raw leaf Boolean false được propagated thành violated theo Table I; temporal transform có policy riêng, đặc biệt ACHIEVE false-prefix → undetermined. PDF chưa quy định toàn bộ recurrence/reset/unknown: bảng này là đề xuất của JACAMO_CSL_MONITOR, phải được document bằng truth tables. Deadline/termination chỉ tạo violation ACHIEVE khi requirement định nghĩa closed obligation với đủ evidence; finite prefix kết thúc tùy ý không đủ.

AND: tất cả child evaluated T → T; có F → F; còn lại U. OR: có T → T; tất cả F → F; còn lại U. Sau propagation áp temporal type **của parent**. Empty children/multiple conflicting condition-refinement sources bị reject; không suy vacuous goal success. Dependency truyền evaluated marking của bound dependee occurrence; missing binding → U + diagnostic, không tự dùng self của depender. Cyclic dependency/refinement chưa có fixed-point policy → UNSUPPORTED.

Activation: explicit predicate/binding; enter tạo episode; inactive đánh INACTIVE trong lifecycle và không gọi là satisfied/violated; reactivation tạo episode mới. No implicit null temporal type default. Reset có trace; manual OCL rerun trên cùng checkpoint không tạo episode hoặc lặp satisfaction events.

Để coding agent không phải đoán recurrence, chốt các quyết định bổ sung sau cho extension profile proposed JACAMO_CSL_MONITOR/2.0; v1 vẫn giữ nguyên ACHIEVE semantics:

- Activation mặc định trong G1 là constant true được ghi trong requirement spec; từ v2, root activation là authored predicate, children cùng activation episode của root trừ explicit binding khác. Unknown activation không mở hoặc reset episode, chỉ báo unavailable.
- Initial marking là UNDETERMINED trước accepted activation boundary; MAINTAIN xét predicate ngay tại boundary đầu, nên false đầu tiên tạo violation. SUSTAIN false đầu tiên chưa từng true vẫn undetermined. Không có goalType=null fallback.
- Proven violation của MAINTAIN/SUSTAIN latch trong episode, kể cả condition sau đó unknown/error. Missing evaluation có thể che false làm history continuity unproven; true về sau không tự repair. Repair phải replay đủ missing interval hoặc mở monitoring episode mới được ghi rõ, không rewrite history cũ.
- ACHIEVE đã có witness true trong cùng verified episode giữ satisfaction, còn current condition verdict/coverage vẫn phản ánh missing/error. Nếu role/context incarnation hoặc episode continuity chưa xác nhận, không gắn latch cũ vào occurrence mới; archive evidence cũ và báo binding/episode unknown.
- Known deactivation, role/context disposal hoặc run end đóng episode và archive marking; không tự tạo failure liveness. Reactivation tạo episode mới, không carry temporal latch. Deadline-based violation chỉ bật bằng explicit requirement deadline capability với complete interval evidence.
- Task marking là per invocation, pre=false luôn undetermined; pre=true/post=false violated; pre=true/post=true satisfied. Post undefined/error hoặc unconfirmed boundary giữ undetermined và detailed quality; “last completed invocation” nếu hiển thị là history DTO, không temporal latch của Task.

### 13.3. Reconcile use4Goal/PDF

Không copy symbolic case ACHIEVE,NONE hoặc TaskMarking latches. Actor mapping H phải explicit, không same-name class lookup. Tất cả context instances dùng cùng shared after state; MVP reevaluate toàn bộ admitted goals khi shared state đổi. GoalViewSnapshot hiện là organizational observation view, cần pane/DTO riêng cho requirement marking; không sửa runtimeState=SATISFIED thành iStar result. [G03] [G04] [G06] [U16] [P]

## 14. MOISE / NPL Architecture

Ba model riêng: OrganizationDefinition (SS/FS/NS), FunctionalObservation (scheme instances/commitments/goalState), NormativeObservation (NPL instances/events). Tái sử dụng OfficialMoiseAdapter, MoiseBoardSnapshotSource, MoiseGoalObservation và NplBoardSnapshotSource.

SchemeInstance identity gồm source spec + actual board artifact/workspace/run incarnation. MissionCommitment có player/mission/scheme/lifetime. GoalObservation có scheme instance, goal spec, arguments, state, committed/achieved agent IDs và authority. Không gộp hai schemes cùng spec; không suy minAgents satisfaction chỉ bằng doneCount>=min vì source có nhánh all/cache/choice/mission semantics riêng. [B03] [B08] [B09] [J03]

P4 ưu tiên board-reported state làm oracle cho organizational goal. iStar condition “board goal satisfied” chỉ được dùng khi requirement tác giả chọn đúng nghĩa đó; world-goal condition khác được kiểm độc lập.

Normative monitoring không đi theo Norm→invariant mặc định. NPL lưu obligation/permission/prohibition, maintenance condition, aim, time/state deadline và lifecycle. Adapter hiện là evidence-only. Bytecode JAR 0.6.1 và source chính thức cho thấy transition xử lý aim/maintenance/deadline riêng; normative observation cần clock/source identity. [N01] [N02] [N03]

P13 bổ sung structured observations và monitor đối chiếu events/state; pure OCL chỉ kiểm phần state predicate đã khai báo. Phải pin và đối chiếu source đúng artifact trước khi claim independent normative equivalence. Nhánh MOISE generator maintenance còn TODO nên giữ PARTIAL/UNSUPPORTED; iStar MAINTAIN chạy trên authored predicate không chứng minh nhánh normative đó đã hoàn chỉnh. [J04]

## 15. Identity & Synchronization

| Đối tượng / tình huống | Chính sách |
|---|---|
| Definition | Qualified source semantic ID + source digest/model revision; location không là identity duy nhất |
| Agent | session/run + official runtime incarnation; cross Jason/CArtAgO/MOISE aliases từ registry proof |
| Artifact | UUID + environment/workspace/run scope; dispose/recreate cùng tên tạo MObject mới |
| Workspace | UUID/full path/parent/environment; remote scope riêng |
| Context/group/scheme | Board identity+incarnation+spec/deployment binding; board display name không đủ |
| Role occurrence | context + roleType + player + enactment lifetime; leave/adopt tạo lifetime mới khi có evidence |
| Property | Artifact incarnation + fullId; slot name/type được schema binding kiểm uniqueness |
| Invocation | session+generation+artifact incarnation+correlation+exact signature; per-invocation pre |
| Duplicate | Event ID+payload hash; same payload idempotent, conflicting content → ERROR/resync |
| Stale/out-of-order/gap | Không apply trái sequence; mark dependent coverage stale; bounded buffering chỉ khi contract hỗ trợ; resync |
| Partial/missing | Giữ last-known evidence nhưng không gọi current; không delete hoặc assert absent |
| Complete scoped resync | Diff upsert/remove trong scope authority; unchanged incarnation giữ MObject và goal history |
| New run/revision | Explicit reset/migration; invalidate pending pre/history/bindings không tương thích |
| Topology changed | Snapshot retry; nếu tiếp tục bất ổn → incomplete và resync, không giả stable cut |

Nếu enactment lifetime không có event/counter authoritative, một tuple biến mất rồi xuất hiện giữa hai polls không thể phát hiện. Phải ghi CONTINUITY_UNPROVEN, không hứa bảo toàn hidden leave/adopt hoặc temporal episode. Tương tự signals/transient violations có thể mất nếu không journal callbacks.

Agent pause chỉ admission/ACK theo reasoning boundary; CArtAgO/NPL/external I/O không bị freeze mặc định. RuntimeMonitor boundary là observed cut với source watermarks, không toàn bộ MAS linearization point. Hai operations overlap giữ pre riêng; post có thể chứa interference của agent khác, nên report invocation contract trên interval, không claim isolated local effect.

## 16. Verification Result Model

Proposed SemanticVerdict có 7 giá trị tại §12. Proposed SemanticVerificationEnvelope liên kết existing VerificationSnapshot bằng snapshotId/version/hash và detailed results/markings, versioned riêng; integration boundary gửi native snapshot DTO cùng envelope khi cần. Pure semantic records không import concrete engine/coordinator. Legacy VerificationOutcome PASS/FAIL/ERROR/SKIPPED và history decoder không đổi nghĩa khi đọc bundle cũ. New evaluator phân loại đầy đủ trước; với CSL profile mới, legacy presentation map UNDEFINED/UNSUPPORTED/INCONCLUSIVE sang SKIPPED kèm detailed code, không xóa original verdict. Với default NATIVE_CURRENT, wrapper giữ behavior hiện tại, gồm undefined→ERROR, cho đến migration được version hóa riêng; không đổi regression semantics của session cũ.

~~~text
SemanticConstraintResult (proposed)
  resultId, constraintId, requirementId?, ruleId, profileVersion
  sourceElementIds, sourceLocations, sourceDigests, transformationVersion
  runId, generation, modelRevision, schemaHash, constraintHash
  stateVersion, snapshotId, beforeSnapshotId?, afterSnapshotId?
  invocationId?, actorId?, playerId?, roleOccurrenceId?, contextId?
  verdict, evaluationStage, backendValue, diagnosticCode
  evidenceRefs, witnessObjects/links/values, unsupportedReason?
  coverage[required/observed/missing scopes, population proof, freshness]
  sourceVersions, sourceWatermarks, observedAt, appliedAt, verifiedAt
  traceRefs, historyContinuity, assumptions
GoalEvaluation
  stableRequirementId, episodeId, binding
  previousMarking, propagatedMarking, currentMarking
  conditionResultId, firstSatisfiedEvidence?, violationEvidence?
~~~

Không serialize live MObject/MSystemState references vào evidence. Witness giữ semantic IDs + state/version + typed values. Kết quả root/aggregate có claim scope riêng: observed prefix, closed episode, bounded exploration. Invariant fail tạo STATE_INVALID diagnostic; vẫn giữ observed state và goal diagnostic trên state đó, ghi ngoài admissible-state assumption của PDF.

Replay dùng journal/checkpoint đang có, bổ sung profile/schema/constraint versions, bindings, monitor checkpoints và normalized observations. Replay verification tái tính OCL và markings, so semantic hash độc lập wall-clock duration. Corruption/gap/version mismatch → INCONCLUSIVE/ERROR, không skip event rồi trả complete. Offline replay sở hữu replay session riêng, không trở thành live source thứ hai hoặc ghi ngược JaCaMo.

## 17. Phase 0 → Phase 20 — work packages có thể giao cho coding agent

Mỗi phase có đủ 18 trường dưới đây. EXISTING chỉ nói code nền hiện diện. Tests trong các phase là **công việc phải chạy khi implement**, chưa phải kết quả lượt lập kế hoạch.

Các foundation được thực hiện **ngay trong P1**: F1 profile/IR; F2 strict schema/binding; F3 accepted observation/identity diff; F4 current OCL; F5 invocation @pre; F6 leaf ACHIEVE; F7 detailed result/coverage; F8 coordinator/replay integration tối thiểu. P8/P10/P11/P12/P14/P15 mở rộng F2–F8; không là prerequisites chưa tồn tại khiến P1 không thể chạy.

### Phase 0 — Baseline / Architecture Freeze

| # | Trường | Công việc cụ thể |
|---|---|---|
| 1 | Mục tiêu | Chốt baseline, single-owner architecture và regression oracle trước khi sửa production code. |
| 2 | Scope | Inventory module/dependency/source, active facade/bridge/native path; freeze semantics decisions ở §3–5. |
| 3 | Out of scope | Implement mapping, norm synthesis, UI redesign, đổi branch hoặc sửa repo tham chiếu. |
| 4 | Existing reuse | Toàn bộ B01–B15, U01–U23; NativeProjectionAuditTest; LiveCartagoNativeVerificationTest. |
| 5 | Existing cần sửa | Chưa sửa production ở P0. Dự kiến chỉ thêm characterization tests tại existing test packages khi bắt đầu coding; không “dọn” unrelated code. |
| 6 | New proposed | SP/profile/SemanticsProfile.java; SP/profile/SemanticProjectionProfile.java; SP/profile/UnsupportedFeature.java; semantic-baseline.json trong resources mới. |
| 7 | Package/module | use-plugin; contract tiếp tục không phụ thuộc USE/JaCaMo; bridge adapter tiếp tục ở jacamo-bridge-jacamo. |
| 8 | Input/output | HEAD/digests/report/API → baseline manifest + capability/gap inventory + quyết định binding seam. |
| 9 | Data model | Profile ID/version, source artifact fingerprints, mapping/temporal/coverage rules, expected compatibility range. |
| 10 | Mapping rules | Ghi current native encoding và CSL target encoding tách biệt; không rename current native thành CSL. |
| 11 | Runtime behavior | Document owner thread, snapshot/event admission, read-only/control boundary, failure paths. |
| 12 | OCL semantics | Khóa current legacy four outcomes và intended detailed seven outcomes; không thay mặc định ngoài profile mới. |
| 13 | Error/unsupported | Manifest mismatch → reject import trước mutation; chưa biết effective dependency → capability unavailable. |
| 14 | Tests | Baseline bridge/official adapter/native projector/OCL/checkpoint/replay suites; packaged smoke; ghi counts/commands/JDK/classpath. |
| 15 | Acceptance | A00: tests nền chạy hoặc failures được phân loại có reproducible evidence; quyết định nào giữ/mở/thay/không dùng được trace tới §3; không hai state owners. |
| 16 | Dependency | Entry point. P1 chỉ bắt đầu thay code khi baseline evidence và profile contract đã có; không yêu cầu thêm user approval. |
| 17 | Rủi ro | Build artifacts cũ, JDK/dependency mediation, frozen resources/classloader; không dùng target/classes cũ để claim clean build. |
| 18 | Trạng thái đầu | PARTIAL: audit tài liệu/source đã thực hiện; profile classes, build/test baseline chưa thực hiện. |

P0 phải lưu effective dependency tree cho runtime producer và consumer riêng. use-plugin production hiện phụ thuộc contract; bridge-jacamo là test scope, USE/Jason/CArtAgO/MOISE provided. EMF XMI hiện test scope. Nếu P3 cần EMF import production, chỉ đổi scope/dependency tối thiểu trong use-plugin với packaged tests; không kéo toàn use4Goal hay bridge runtime vào consumer shaded JAR. [U01]

### Phase 1 — Semantics Profile v1 + Vertical Slice

| # | Trường | Công việc cụ thể |
|---|---|---|
| 1 | Mục tiêu | Chứng minh G1 end-to-end từ official runtime observation, gồm state/transition/leaf requirement. |
| 2 | Scope | F1–F8; Group/Role/atomic player/enactment; selected domain X.p:Integer; invariant; increment contract; ACHIEVE. |
| 3 | Out of scope | Inheritance/compatibility nâng cao, generic plans, norms/missions/scheme semantics, inverse/explorer. |
| 4 | Existing reuse | B03/B04/B07/B08/B11/B13/B14; U02/U03/U06–U15/U19–U23; fixtures của T01/T02. |
| 5 | Existing cần sửa | NativeUseModelBuilder/StateBuilder, NativeRuntimeMutationEngine/Projector, RuntimeVerificationCoordinator, ExternalOclConstraintService, NativeOperationCheckpoints, CodeGroundedNativePipeline và facade composition. Default native behavior giữ nguyên. |
| 6 | New proposed | SP/model/SemanticModel.java; SP/csl/CslSchema.java; SP/csl/CslUseBinding.java; SP/runtime/SemanticObservation.java; SP/runtime/SemanticObservationMapper.java; SP/ocl/OclEvaluationAdapter.java; SP/istar/LeafAchieveMonitor.java; SP/evidence/SemanticVerificationEnvelope.java. |
| 7 | Package/module | use-plugin semantic packages; optional generic envelope capability trong contract nếu wire chưa đủ, không đưa MObject vào wire. |
| 8 | Input/output | Official semantic model + real snapshot/deltas + authored XReady/Nonnegative/increment → một native session + detailed results/markings. |
| 9 | Data model | Definition IDs, runtime incarnations, role occurrence lifetime, typed scalar, bound goal/episode, per-invocation pre, seven verdicts. |
| 10 | Mapping rules | M02/M03/M10/M18/M20; Plays(a,r), InContext(c,r); exact artifact domain registration; no belief→world mapping. |
| 11 | Runtime behavior | Reuse projector admission; same MSystem/MModel/object for unchanged incarnation; atomic diff batch; same-run resync giữ monitor/pre khi continuity được xác nhận. |
| 12 | OCL semantics | invariant(after); pre(before); post(before,after); @pre compiled correctly; goal self explicitly x; false-prefix ACHIEVE U, true latch T. |
| 13 | Error/unsupported | Missing group/agent/property coverage → SKIPPED; unknown type → UNSUPPORTED; undefined → UNDEFINED; malformed batch → ERROR+rollback; negative p giữ FAIL. |
| 14 | Tests | Proposed CslVerticalSliceIT, CslNativeBindingTest, LeafAchieveMonitorTest, InvocationAtPreTest, DetailedVerdictTest; real CartagoSnapshotSource + official GroupBoard/MoiseBoardSnapshotSource, không mock toàn semantic input. |
| 15 | Acceptance | A01: đầy đủ bảng scenario dưới đây; exact IDs/evidence; source PARTIAL không bị relabel COMPLETE; legacy tests vẫn pass; không alternative shadow system. |
| 16 | Dependency | Sau P0; tạo minimal contracts mà P2/P8/P9/P10/P11/P12/P14/P15 mở rộng. |
| 17 | Rủi ro | Native hardcoded base classes/associations, snapshot reset, Cartago numeric Real policy, board alias resolution; cần explicit binding seam. |
| 18 | Trạng thái đầu | TODO: infrastructure nền EXISTING nhưng vertical slice CSL+iStar chưa có. |

Scenario G1 phải chạy với fixture application nằm trong use-plugin/src/test/resources và artifact test class trong use-plugin/src/test/java. Dùng fixture spec tự chứa, không sửa examples/auction hoặc Classroom.

| Boundary | Expected native state | Invariant Nonnegative | increment post | Goal XReady |
|---|---|---|---|---|
| Initial | a:AtomicAgent, c:C, r:R, x:X; Plays/InContext; p=0 | PASS | SKIPPED chưa invocation | UNDETERMINED |
| start op-1 | Lưu pre(op-1).x.p=0 | PASS | Chưa post | UNDETERMINED |
| commit/complete op-1 | Cùng x; p=1; exact success/correlation | PASS | PASS: 1=0+1 | SATISFIED, witness state |
| later observed update | p=-1 giữ trong state | FAIL với x/p=-1 | Chỉ applicable khi có invocation | Vẫn SATISFIED cùng episode |
| independent negative contract | Before=0, after=2; invocation khác | PASS ở after=2 | FAIL: 2≠0+1 | Theo independent episode/trace |
| partial/missing observation | Không default p=0/false; last-known evidence có freshness | SKIPPED nếu dependency thiếu | SKIPPED thiếu paired boundary | Không tạo achievement mới |

Test phải assert actual MClass attribute type Integer, MObject identities, MLinks và Value; không chỉ assert chuỗi export. Exact role/agent/context binding có thể lấy official board state tại adapter boundary khi full MAS startup không phù hợp CI, nhưng vẫn phải dùng real runtime APIs và nêu fidelity. Test G1 không được được đánh pass khi nguồn unavailable rồi bị assumptions skip.

### Phase 2 — CSL Organizational / Domain Model

| # | Trường | Công việc cụ thể |
|---|---|---|
| 1 | Mục tiêu | Hoàn thiện CSL-compatible structural profile M2/M1/M0, không lẫn runtime helpers với Entity. |
| 2 | Scope | Organization/group/nesting/OrgCtx/role/inheritance/compatibility/link/cardinality; Agent/OrgCtx players; domain entities/attrs/associations/aggregation/composition/invariants. |
| 3 | Out of scope | Full MOISE policy equivalence; generic Role–Role association trái v4; multiple inheritance tự hạ thành single parent. |
| 4 | Existing reuse | OfficialMoiseAdapter semantic IR; MoiseDomainProjection.validate; MoiseUseSymbols; UseModelApi; B08 accepted tuples. |
| 5 | Existing cần sửa | NativeUseModelBuilder, NativeUseStateBuilder, NativeProjectionPolicy admission seam, DomainProjection codec entrypoints; không overwrite frozen V2 metamodel. |
| 6 | New proposed | SP/csl/CslSchemaValidator.java; SP/csl/CslStructuralPolicy.java; SP/mapping/OrganizationalMappingRules.java; versioned CSL schema Ecore/resource trong use-plugin/Core/Metamodel/csl-monitor-v1/. |
| 7 | Package/module | SP/csl, SP/mapping trong use-plugin; M2 resources cùng repo. |
| 8 | Input/output | Structural/domain definitions + explicit policy bindings → CslSchema + Φ + source-target bindings. |
| 9 | Data model | Three CSL classifier kinds; single-parent core generalization; declaration ownership; association ordered ends/bounds/kind; role policies; runtime player/enactment distinct. |
| 10 | Mapping rules | M01–M04/M17–M20; group-role cardinality quan hệ; OrgCtx-as-player cùng object; inheritance cùng role occurrence. |
| 11 | Runtime behavior | Validate context nesting/lifecycle; no automatic population from containment; actual adopt/leave update role occurrence. |
| 12 | OCL semantics | Scope/multiplicity/abstractness/compatibility constraints generated có IDs; ownership constraints chỉ khi profile đã định nghĩa. |
| 13 | Error/unsupported | Kind mismatch/cycle/invalid bounds → ERROR; scoped MOISE policy chưa formalize → UNSUPPORTED; unknown domain ownership không auto composition. |
| 14 | Tests | Nesting, Role inheritance same identity, collective player, same role hai contexts, compatibility allowed/disallowed, per-group bounds, aggregation/composition deletion policy. |
| 15 | Acceptance | A02: M2/load/validation + M1 compile + M0 model/state tests đồng nhất; no phantom populations; unsupported policy giữ source. |
| 16 | Dependency | P1 binding nền; cung cấp schema cho P3 và expansion P14; có thể thiết kế song song P10 sau freeze DTO. |
| 17 | Rủi ro | PDF không quyết định mọi enactment rule; CSL Ecore use4Goal có annotation/feature gaps; không copy lỗi vào metamodel mới. |
| 18 | Trạng thái đầu | PARTIAL: source/native structural projection có; CSL revised runtime semantics chưa có. |

### Phase 3 — JaCaMo Metamodel → CSL / iStar / OCL

| # | Trường | Công việc cụ thể |
|---|---|---|
| 1 | Mục tiêu | Một rule catalog đủ 21 EClass + 7 EEnum và source supplements, có loss/inverse metadata. |
| 2 | Scope | Load E đã giao; normalize definitions; execute §8 rules; emit MappingBundle/TraceComplement. |
| 3 | Out of scope | Giả E đủ runtime; sửa external Ecore/use4Goal; regenerate mọi interpreter object thành Entity. |
| 4 | Existing reuse | JacamoSemanticSnapshot/metadata/contracts, JacamoSpecificationModel validation, CodeGroundedRuleCatalog/trace collector, ActiveBaseline distinction. |
| 5 | Existing cần sửa | CodeGroundedNativePipeline input bundle; NativeProjectionAudit trace admission; use-plugin/pom.xml chỉ nếu EMF import production cần dependency mới. |
| 6 | New proposed | SP/mde/JacamoEcoreSourceLoader.java; SP/mapping/SourceModelNormalizer.java; SP/mapping/MappingRuleCatalog.java; SP/mapping/MappingBundle.java; SP/evidence/TraceComplement.java. |
| 7 | Package/module | use-plugin SP/mde/mapping/evidence; versioned resources mới, không sửa frozen manifests để nhét semantics mới. |
| 8 | Input/output | EMF source instance + exact metamodel hash + supplementary official source OR existing semantic snapshot → cùng canonical source IR → same mapping bundle. |
| 9 | Data model | Source feature origin, definition/instance tags, transformation rule/version, fidelity/loss, unmatched/unsupported fields, original source AST/fragments. |
| 10 | Mapping rules | M01–M21/E01–E07; enum literal identity exact; source performance/custom link preserved even ngoài Ecore vocabulary. |
| 11 | Runtime behavior | Transformation static; runtime contract không lấy Agent.roles EReference làm actual enacted state. |
| 12 | OCL semantics | Condition EString chỉ thành OCL sau explicit language/binding/fragment validation; not source-code evaluation. |
| 13 | Error/unsupported | Missing required supplements → SOURCE_ABSTRACTION_INCOMPLETE; unknown enums/features → explicit decision; no default achievement cho unknown performance. |
| 14 | Tests | Rule coverage enumeration; every feature mapped/traced/excluded with reason; invalid references/cycles; Ecore-vs-source default differences; deterministic output; semantic IDs collision. |
| 15 | Acceptance | A03: mọi concept/enum có decision, loss và evidence; equivalent supported inputs từ hai frontends cho same semantic output; no silent field drop. |
| 16 | Dependency | P1/P2 schema/IR; expanded iStar contracts đợi P4–P9 nhưng rule có thể trả trace-only trước. |
| 17 | Rủi ro | Group.roles containment, missing contextual bounds/property values, no EID; opaque strings không cứu runtime semantics. |
| 18 | Trạng thái đầu | TODO; existing native rules/trace là nền, không phải transformer CSL/iStar mới. |

### Phase 4 — MOISE Functional Model / iStar

| # | Trường | Công việc cụ thể |
|---|---|---|
| 1 | Mục tiêu | Bind đúng functional definitions/instances/observations với requirements; giữ board authority. |
| 2 | Scope | Scheme, mission, goal types/arguments/dependencies/minAgents/TTF/commitments/done agents/responsible groups. |
| 3 | Out of scope | Tự xem mission là Goal/Task/Entity; full NPL equivalence; source maintenance TODO được coi implemented. |
| 4 | Existing reuse | OfficialMoiseAdapter FS/NS DTOs, MoiseBoardSnapshotSource, MoiseGoalObservation, materializeFunctional và GoalViewSnapshot. |
| 5 | Existing cần sửa | Functional binding hooks trong NativeRuntimeMutationEngine; goal view thêm requirement binding link; trace export giữ arguments/source goal type. |
| 6 | New proposed | SP/observer/FunctionalObservation.java; SP/istar/MoiseRequirementBinder.java; SP/mapping/FunctionalMappingRules.java. |
| 7 | Package/module | use-plugin; adapter metadata expansion nếu cần nằm jacamo-bridge-jacamo/contract trong USE repo. |
| 8 | Input/output | OrganizationSemantic FS + board snapshots → definition graph, instance scopes, functional observer và optional iStar bindings. |
| 9 | Data model | SchemeDefinition/Instance, MissionDefinition/Commitment, GoalDefinition/Observation; arguments immutable typed/raw; finite/all cardinality tags; clock semantics. |
| 10 | Mapping rules | M06–M09; source goal type preserved; iStar temporal type do requirement contract chọn, không enum name cast. |
| 11 | Runtime behavior | Reevaluate all bound scheme/context goals sau shared change; done/committed exact agents, instances cùng spec tách biệt. |
| 12 | OCL semantics | Board-state predicates khác world conditions; allocation/bounds checks đủ coverage; deadline không tự state invariant. |
| 13 | Error/unsupported | Unknown goalState/arity/context → error/partial; min/TTF unparsable → unsupported; maintenance normative realization PARTIAL. |
| 14 | Tests | Hai schemes cùng spec khác arguments; all/min thresholds; commitments leave/rejoin; waiting/enabled/satisfied; performance vs achievement; stale board. |
| 15 | Acceptance | A04: exact scheme/goal/mission IDs; source/observer/intentional status phân biệt; no cross-instance contamination. |
| 16 | Dependency | P3 catalog + P1 runtime; P5 dùng full operator/goal graph; P13 cần allocation scope từ phase này. |
| 17 | Rủi ro | Scheme.isSatisfied Java và NPL rules có khác coverage/paths; dùng public board observation làm oracle thay shortcut. |
| 18 | Trạng thái đầu | PARTIAL: functional projection/goal observation đã có; intentional correspondence/coverage còn thiếu. |

### Phase 5 — OPlan Semantics

| # | Trường | Công việc cụ thể |
|---|---|---|
| 1 | Mục tiêu | Bảo toàn cấu trúc operator/order và kiểm semantics của execution abstraction được chọn. |
| 2 | Scope | CHOICE→OR outcome; SEQUENCE→AND+ordered predecessors; PARALLEL→AND+partial-order/concurrency evidence. |
| 3 | Out of scope | Claim AND/OR tương đương scheduler MOISE; automatically execute plans từ goal graph. |
| 4 | Existing reuse | OfficialMoiseAdapter ordered subgoals; MoiseDomainProjection decompositionOperator/orderInParent; source Goal.getPreConditionGoals/Plan.getPreviousSubGoals. |
| 5 | Existing cần sửa | Existing trace export/replay payload giữ full execution descriptor; functional mapping không flatten mất operator. |
| 6 | New proposed | SP/model/ExecutionDescriptor.java; SP/istar/OPlanOutcomeMapper.java; SP/runtime/OPlanTransitionMonitor.java. |
| 7 | Package/module | SP/model/istar/runtime; không thêm operator execution vào CSL Entity core. |
| 8 | Input/output | Source operator+child order+dependencies + goal-state trace → refinement outcome + explicit execution constraints/evidence. |
| 9 | Data model | Operator raw/canonical, ordered child IDs/ordinal, predecessor graph, enabledness observation, causal edges, abstraction assumptions. |
| 10 | Mapping rules | Choice giữ at-least-one outcome, không tự mutual-exclusion; sequence constraints riêng; parallel không ép một total order. |
| 11 | Runtime behavior | Compare official enabled/done transitions khi có coverage; callbacks arrival order không mặc định causal order. |
| 12 | OCL semantics | State goal outcome bằng OCL; predecessor/before relations kiểm trên transition/history, không chỉ current AND. |
| 13 | Error/unsupported | Missing predecessor/causal evidence → INCONCLUSIVE; custom operator → UNSUPPORTED; cycle/ordinal mismatch → ERROR. |
| 14 | Tests | Same children sequence vs parallel: static output khác; sequence out-of-order counterexample; parallel two interleavings; choice one/both outcomes; nested plans. |
| 15 | Acceptance | A05: recover original operator/order từ trace; outcome-equivalent trees không bị gọi behavior-equivalent; executable oracle đối chiếu selected source fragment. |
| 16 | Dependency | P4 graph + P1 trace; richer temporal monitor P9 và causal contract P10/P11 hỗ trợ expansion; P16 dùng approved descriptors. |
| 17 | Rủi ro | Hidden enabling transitions/timestamps; absence of observed sequence violation không chứng minh all executions. |
| 18 | Trạng thái đầu | PARTIAL: operator/ordinal đã được giữ; execution semantics checker chưa có. |

### Phase 6 — Jason Goal / Plan / Event / Action

| # | Trường | Công việc cụ thể |
|---|---|---|
| 1 | Mục tiêu | Định nghĩa intentional/executive abstraction có admission, thay generic Plan→Task. |
| 2 | Scope | AGoal achievement/test; plan/trigger/context; Event/Intention/IntendedMeans; internal/external Action với explicit contracts. |
| 3 | Out of scope | Full unification/recursion/control flow/custom selection/arbitrary internal actions; behavioral inverse. |
| 4 | Existing reuse | OfficialJasonAdapter AST/body/source evidence; BridgeAgArch; JasonSnapshotSource; action correlation; Jason 3.3.2 AST APIs. |
| 5 | Existing cần sửa | BridgeAgArch observation payload khi thêm structured fields; JasonSnapshotSource capability reporting; semantic trace classification, không đổi interpreter. |
| 6 | New proposed | SP/observer/ExecutionObservation.java; SP/mapping/JasonAbstractionPolicy.java; SP/istar/TaskBinding.java; SP/ocl/JasonConditionFragment.java. |
| 7 | Package/module | Consumer SP; producer extensions trong jacamo-bridge-jacamo; neutral DTO additions vào contract có version. |
| 8 | Input/output | Official plan AST + selected runtime occurrence + authored abstraction contract → Task definition/invocation or trace-only diagnostic. |
| 9 | Data model | Trigger operator/kind/literal, plan/source ID, body ordinal, intention/means/goal occurrence, substitution/query meaning, action phase/result. |
| 10 | Mapping rules | M12–M15; finite ground/pure fragment only; Boolean context = existence of unifier chỉ khi contract nói rõ và finite domain có coverage. |
| 11 | Runtime behavior | Copy trên agent cycle/boundary; initialGoals không current goals; plan library definition khác running IntendedMeans. |
| 12 | OCL semantics | Context/precondition bound đúng mental/world views; post trên matching invocation; test goals là query evidence, không auto ACHIEVE. |
| 13 | Error/unsupported | Recursive/open-term/side-effect queries → UNSUPPORTED hoặc Jason-authoritative query result; không catch rồi true/false. |
| 14 | Tests | Two equal literals different occurrences; nested intentions; suspended/pending; test vs achievement; negative/failed goal; ordered body; unsupported recursion/internal action. |
| 15 | Acceptance | A06: Task only with source contract+binding; all ignored execution AST nodes have trace-only reason; no “intention disappeared ⇒ achieved”. |
| 16 | Dependency | P3 source normalization, P7 mental binding, P8/P12 invocation engine; definition admission có thể song song P7. |
| 17 | Rủi ro | Custom BB/agent architecture; trigger-object lifetime không phải mọi goal-attempt identity; limited cycle visibility. |
| 18 | Trạng thái đầu | PARTIAL: AST/current achievement observations có; generic Task semantics UNSUPPORTED, approved abstraction TODO. |

### Phase 7 — Belief / Mental State / Domain State

| # | Trường | Công việc cụ thể |
|---|---|---|
| 1 | Mục tiêu | Giữ riêng tri thức được quan sát và state môi trường, gồm perception lag. |
| 2 | Scope | Owner, namespace, predicate/terms, explicit negation, annotations/provenance, fact/rule, occurrence/state, typed stored/query views. |
| 3 | Out of scope | Arbitrary entailment trong USE; world truth suy từ belief; xóa domain entity khi agent quên belief. |
| 4 | Existing reuse | BridgeAgArch BB snapshot; JasonBeliefEvidence provenance/demand logic; NativeProjectionPolicy.exposesBelief; CArtAgO authority. |
| 5 | Existing cần sửa | Structured belief payload/capabilities; observer binding hook trong mutation engine; policy không đổi scope của native default. |
| 6 | New proposed | SP/observer/MentalStateObservation.java; SP/mapping/BeliefDomainBinding.java; SP/ocl/MentalPredicateBinding.java. |
| 7 | Package/module | use-plugin SP/observer/mapping/ocl; contract/adapter typed supplements nếu cần. |
| 8 | Input/output | BB literals/cycle + explicit query/domain schema → observer state and separately approved domain predicates. |
| 9 | Data model | Literal AST encoding or canonical structured terms; rule body; epistemic owner; valid observation boundary; source annotations; query answer set/closed-world contract. |
| 10 | Mapping rules | M11; no default belief→Entity; same literal different source/namespace/owner không merge. |
| 11 | Runtime behavior | Belief add/remove/annotation change updates observer only; world p/price independently from artifact. |
| 12 | OCL semantics | StoredBelief membership ≠ logicalConsequence; existential ground query admission explicit; absence only false khi BB/query scope complete. |
| 13 | Error/unsupported | Unparsed/cyclic/open terms giữ raw evidence + UNSUPPORTED typed query; missing cycle cut → SKIPPED. |
| 14 | Tests | artifact.price=12 và agent believes price=10 cùng tồn tại; positive/explicit negative; namespaces; annotations/source changes; rule vs fact; late percept; removal. |
| 15 | Acceptance | A07: hai authority/value/time được query độc lập, no overwritten world/mental slots; preserved fields coverage report. |
| 16 | Dependency | P1 observer vocabulary, P3 source rules; feeds P6 applicability và P8 constraints. |
| 17 | Rủi ro | Current selective domain belief projection không phải full mental model; không mở thành full BB rồi làm đổi existing domain projection. |
| 18 | Trạng thái đầu | PARTIAL: literal/provenance baseline có; full structured mental contract/query profile chưa có. |

### Phase 8 — OCL Compiler / Verification Service

| # | Trường | Công việc cụ thể |
|---|---|---|
| 1 | Mục tiêu | Một typed verification service cho state/predicate/Task pre/post và đầy đủ detailed outcomes. |
| 2 | Scope | Extend F4/F5/F7: collections/navigation/allInstances/quantifiers; source/feature dependencies; compile cache/version; two-state evaluation. |
| 3 | Out of scope | Tự viết OCL interpreter, regex @pre, arbitrary Java/Jason effects, silently changing failed expressions. |
| 4 | Existing reuse | ExternalOclConstraintService, DefaultVerificationService, ConstraintRegistry/DependencyIndex, USE compiler/Context/Evaluator; T02 parameter-context pattern. |
| 5 | Existing cần sửa | Hai service gọi chung typed result adapter; coordinator result aggregation; profile install transaction; old API mapping compatibility. |
| 6 | New proposed | SP/ocl/SemanticOclCompiler.java; SP/ocl/ConstraintCoverageResolver.java; SP/ocl/CompiledSemanticConstraint.java; extend OclEvaluationAdapter từ P1. |
| 7 | Package/module | use-plugin SP/ocl/profile; USE core API reuse, không fork evaluator. |
| 8 | Input/output | Constraint spec + MModel/bindings + pinned state pair/coverage → compiled registry + detailed verdict/witness. |
| 9 | Data model | Constraint kind, self/arg/result types, source/model/profile hash, AST features, required populations/sources, checkpoint policy. |
| 10 | Mapping rules | Explicit condition language and binding; Goal conditions not auto invariants; Task post compiled with pre context. |
| 11 | Runtime behavior | Compile once/rebind on revision; evaluate pinned states; no independent mutable session; immutable invocation pre. |
| 12 | OCL semantics | §12 authoritative; quantifier undefined/empty population rules tested; host limitations declared; same compiler for all constraint kinds. |
| 13 | Error/unsupported | Syntax/type ERROR vs excluded feature UNSUPPORTED; current coverage missing SKIPPED; aggregate INCONCLUSIVE; retain backend diagnostics. |
| 14 | Tests | Seven outcomes; division/undefined host behavior; collection/quantifier/navigation; allInstances incomplete population; @pre navigation; typed parameters/result; atomic invalid profile install. |
| 15 | Acceptance | A08: source constraints biject registry; no user invariant omission; body undefined never becomes ordinary FAIL; pre=0 after=1/2 oracle. |
| 16 | Dependency | F4/F5 established P1; full schema P2/P14 and mental/functional capabilities feed admissions; P9 consumes immutable outcomes. |
| 17 | Rủi ro | AST visitor missing dependency causes unsound selection; retain full reevaluation fallback; backend null/invalid not always distinct. |
| 18 | Trạng thái đầu | PARTIAL: native OCL có; detailed classification/full semantic bindings cần mở rộng. |

### Phase 9 — iStar Temporal Semantics

| # | Trường | Công việc cụ thể |
|---|---|---|
| 1 | Mục tiêu | Versioned monitor phù hợp §13 và explicit reconciliation PDF/use4Goal. |
| 2 | Scope | NONE/ACHIEVE/MAINTAIN/SUSTAIN, Task, AND/OR/Dependency/compound; actors/roles/contexts; initial/activation/episode/reset/termination. |
| 3 | Out of scope | Resource/quality/contribution/forall/pick không có policy; dependency cycles; copy symbolic NONE latch/Task preSeen behavior. |
| 4 | Existing reuse | LeafAchieveMonitor P1; existing trace/profile/checkpoint infrastructure; use4Goal chỉ reference; shared USE state. |
| 5 | Existing cần sửa | Coordinator semantic participant; GoalViewSnapshot integration bằng distinct requirement DTO; replay adds marking checkpoints. |
| 6 | New proposed | SP/istar/RequirementModel.java; SP/istar/IntentionalMarking.java; SP/istar/TemporalGoalMonitor.java; SP/istar/TaskMonitor.java; SP/istar/RequirementBindingResolver.java. |
| 7 | Package/module | SP/istar/profile, consumer only; no production dependency use4Goal. |
| 8 | Input/output | Requirement graph + previous marking + complete/partial detailed condition results + binding/episode → next marking/evidence. |
| 9 | Data model | Raw propagated vs evaluated marking, lifecycle, first achievement, violation latch, history gap, episode identity, bound dependency endpoints. |
| 10 | Mapping rules | §13; AND/OR propagate evaluated child, then own temporal transform; dependency explicit cross-context occurrence. |
| 11 | Runtime behavior | Reevaluate all affected contexts on same shared after version; idempotent same checkpoint; reset only explicit episode/run policy. |
| 12 | OCL semantics | U/error/unsupported condition không là false; Task uses exact invocation pair; no satisfaction heuristic from Jason/MOISE names. |
| 13 | Error/unsupported | Missing binding/coverage → U + code; history gap inconclusive; unsupported graph construct rejected before monitoring. |
| 14 | Tests | Truth tables and traces F/T/F per type; parent NONE with child ACHIEVE; parent MAINTAIN with OR; dependency different context; deactivate/reactivate; termination without deadline; gap before/after achievement. |
| 15 | Acceptance | A09: recurrence spec and test oracle agree; no null defaults; historical satisfaction and current evaluation quality distinct; all contexts interference evaluated. |
| 16 | Dependency | P8 typed results + P1 F6; full graph from P4/P5/P6; P15 replay preserves monitor semantics. |
| 17 | Rủi ro | PDF underspecification; require documented profile choice with counterexample tests, không tự gọi exact PDF implementation. |
| 18 | Trạng thái đầu | TODO beyond P1 leaf; use4Goal semantics hiện không được dùng làm oracle chung. |

### Phase 10 — Runtime State Adapter

| # | Trường | Công việc cụ thể |
|---|---|---|
| 1 | Mục tiêu | Hoàn thiện §10 envelope và source authority/coverage cho tất cả observed families. |
| 2 | Scope | Run/generation/revision/profile/snapshot/watermark/topology; agents/enactments/functional/mental/artifact/property/events; deep immutability. |
| 3 | Out of scope | Rewrite adapters/transport; private field scraping; nâng silence/partial thành complete. |
| 4 | Existing reuse | B01–B11/B13–B15, semantic DTOs/canonical codecs, BridgeClient/MirrorStateMachine. |
| 5 | Existing cần sửa | SnapshotCoordinator export topology/cut diagnostics; source-specific scope coverage; versioned optional contract fields/capability; semantic consumer mapper. |
| 6 | New proposed | SP/runtime/SemanticObservationEnvelope.java; SP/runtime/ObservationCoverage.java; neutral contract ScopedCoverage.java nếu producer proof cần wire. |
| 7 | Package/module | Producer: jacamo-bridge-jacamo; shared neutral additions: contract; consumer: SP/runtime; all trong USE repo. |
| 8 | Input/output | Official source cuts/events + transport envelope → immutable semantic observations + provenance/coverage. |
| 9 | Data model | §10.1; exact selected facts, population completeness, per-field authority/freshness, coverage dependencies and diagnostics. |
| 10 | Mapping rules | Definitions from static bundle; faithful values mutate; evidence-only kept external/helper; unsupported retained. |
| 11 | Runtime behavior | Capture buffer-first; revalidate watermarks/topology; retry bounded; callbacks replay relative exact cut, no invented total order. |
| 12 | OCL semantics | Constraint required scope proven independently of source global PARTIAL; allInstances requires population proof. |
| 13 | Error/unsupported | Stable cut unavailable → incomplete/resync; unavailable board/agent → explicit source/field quality; no default empty set. |
| 14 | Tests | Deep immutability; producer source mutation during cut; topology change; queue overflow; envelope backwards decoding; field partiality; actual API observations. |
| 15 | Acceptance | A10: all mandatory fields sourced explicitly; no values guessed from display names; source completeness preserved; every gap visible to verification. |
| 16 | Dependency | Extends P1 F3/F7; DTO freeze can run with P2; full functional/mental fields follow P4/P7. |
| 17 | Rủi ro | Public APIs không cho full global state; observation profile must record this, not block all selected predicate checks needlessly. |
| 18 | Trạng thái đầu | PARTIAL: rich snapshots/events có; envelope/scoped coverage/topology export cần hoàn thiện. |

### Phase 11 — Identity / Lifecycle / Synchronization

| # | Trường | Công việc cụ thể |
|---|---|---|
| 1 | Mục tiêu | Mở rộng F3 thành synchronizer bảo toàn incarnation/history và phát hiện continuity loss. |
| 2 | Scope | Create/delete/recreate/restart; adopt/leave; artifact/agent/group/scheme lifecycle; duplicate/stale/out-of-order/gap/resync/topology. |
| 3 | Out of scope | Timestamp total order; missing=deleted; current complete snapshot tự repair mọi temporal history đã mất. |
| 4 | Existing reuse | BridgeEntityId, EventLedger, NativeObjectBindings/tombstones, NativeRuntimeProjector admission, SnapshotCoordinator, gate/control protocol. |
| 5 | Existing cần sửa | Projector.applySnapshot reconciliation; mutation savepoints include semantic lifetimes; coordinator save/reset protocol/markings; producer exact lifecycle evidence. |
| 6 | New proposed | SP/runtime/ScopedSnapshotReconciler.java; SP/runtime/ObservationContinuity.java; SP/runtime/EnactmentLifetimeRegistry.java. |
| 7 | Package/module | Consumer SP/runtime + existing runtime/trace; source lifecycle supplements trong adapter module của USE. |
| 8 | Input/output | Accepted observations + previous bindings/watermarks → atomic identity-preserving delta / stale-resync result. |
| 9 | Data model | Active/retired identities, exact aliases, per-source sequences, observed lifetime tokens, scope coverage, continuity epochs. |
| 10 | Mapping rules | §15; source context và role type bắt buộc trong enactment; same-name different scope/incarnation không alias. |
| 11 | Runtime behavior | Diff unchanged objects; reconcile only authoritative scopes; duplicate no-op; conflicting event stops dependent proof; preserve pending invocation pre only if identity/cut continuity verified. |
| 12 | OCL semantics | Current proof sau resync có thể phục hồi; MAINTAIN/SUSTAIN history vẫn INCONCLUSIVE nếu gap chưa replay/repair; ACHIEVE witness giữ đúng episode. |
| 13 | Error/unsupported | Old incarnation update rejected; alias collision ERROR; hidden leave/adopt giữa polls → CONTINUITY_UNPROVEN; broken source → SKIPPED current checks. |
| 14 | Tests | Same-name artifact recreate; agent restart; same role hai groups; leave/rejoin; partial omission; complete delete; pending @pre across same-run resync; duplicate/content mismatch; stale/gap/topology. |
| 15 | Acceptance | A11: unchanged MObject identity across snapshots; reincarnation new object/name; no implicit deletion; stale state không giữ cached PASS như current evidence. |
| 16 | Dependency | P1 F3/F5 baseline, P10 coverage; P12/P15 dùng continuity; P9 history-gap semantics đã chốt. |
| 17 | Rủi ro | Native snapshot reset path và static baseline objects; source board IDs thiếu UUID; observer-only lifetimes không chứng minh source lifetime. |
| 18 | Trạng thái đầu | PARTIAL: nhiều guards hiện có; full scoped diff/lifetime/temporal continuity chưa đủ. |

### Phase 12 — CArtAgO Operations / Transitions

| # | Trường | Công việc cụ thể |
|---|---|---|
| 1 | Mục tiêu | Mở F5 thành contract monitoring cho invocation boundaries, overlap và failures có evidence. |
| 2 | Scope | requested/started/suspended/resumed/completed/failed/abort; pre-state; correlation; signature/args/result; property delta/signal. |
| 3 | Out of scope | Một callback=atomic MAS action; arbitrary Java effect extraction; request-time state thay start-time @pre. |
| 4 | Existing reuse | CartagoSnapshotSource ICartagoLogger; NativeOperationCheckpoints; OperationCheck; DefaultVerificationService.begin/completeOperation. |
| 5 | Existing cần sửa | NativeOperationCheckpoints typed classification/capacity/timeout/abort evidence; operation event payload; coordinator pairing/coverage; contract compiler argument context. |
| 6 | New proposed | SP/runtime/InvocationBoundary.java; SP/runtime/InvocationCoveragePolicy.java; extend P1 operation binding/typed result adapter. |
| 7 | Package/module | Producer adapter + neutral fields trong contract; consumer existing runtime và SP/runtime/ocl. |
| 8 | Input/output | Official operation phases + ordered observed domain updates → paired before/after checkpoint + pre/post results. |
| 9 | Data model | Per-invocation key, exact signature and args, source-start cut ID/watermark, immutable pre, completion state, interference set, failed/aborted reason. |
| 10 | Mapping rules | M19; OpRequested không pre; OpStarted với exact operation/correlation mới mở invocation; Signal giữ journal, không tự state effect. |
| 11 | Runtime behavior | Các invocations overlap có pre riêng; suspension không overwrite pre; complete dùng observed after đã apply đủ deltas theo boundary proof. |
| 12 | OCL semantics | @pre = invocation start boundary, không last callback; interference được ghi; contract interval check không chứng minh local-effect isolation. |
| 13 | Error/unsupported | Missing/duplicate pre, capacity eviction, timeout, incarnation mismatch → explicit SKIPPED/INCONCLUSIVE; unsupported argument type → UNSUPPORTED; internal evaluator failure → ERROR. |
| 14 | Tests | 0→1 PASS/0→2 FAIL; overlap A/B với khác pre; suspend/resume; failure/abort; late completion; dispose mid-operation; parameter/result typing; signal/property ordering. |
| 15 | Acceptance | A12: pre snapshots không đổi sau source updates; pairing exact; không post PASS khi failed invocation hoặc source boundary chưa được chứng minh. |
| 16 | Dependency | P1 F5, P8 compiler, P10/P11 boundary/continuity; P6 TaskMonitor consumes invocation results. |
| 17 | Rủi ro | Callback-to-domain-state ordering và hidden state; không gọi blocking source API bên trong logger nếu có nguy cơ lock; missing cut proof phải hiện ra trong verdict. |
| 18 | Trạng thái đầu | PARTIAL: correlated pre/post có; abort/interference/producer-cut proof và detailed failures cần mở rộng. |

Một pre-state copy đúng ở USE chỉ chứng minh trạng thái consumer trước event. Muốn gọi nó là pre-state thực của JaCaMo, phải chứng minh producer operation-start boundary và ordered domain updates đã đồng bộ đến cut đó. Với sources khác nhau, không có proof chỉ từ timestamps. Khi chưa đủ, trả PRE_BOUNDARY_UNCONFIRMED. G1 dùng serial fixture có explicit boundary assertions; P12 thêm concurrent evidence tests.

### Phase 13 — MOISE / NPL Normative Monitoring

| # | Trường | Công việc cụ thể |
|---|---|---|
| 1 | Mục tiêu | Theo dõi normative lifecycle có authority; tách khỏi intentional và state-invariant claims. |
| 2 | Scope | Norm definition/instance; obligation/permission; optional prohibition capability; condition/maintenance/aim/deadline; active/fulfilled/unfulfilled/inactive; mission commitments. |
| 3 | Out of scope | Norm→invariant mặc định; complete deontic equivalence/sanction synthesis; hoàn thiện maintenance generator ở repo khác. |
| 4 | Existing reuse | NplBoardSnapshotSource listeners/getters; OfficialMoiseAdapter NS; MOISE generator reference; actual NPL 0.6.1 runtime APIs/bytecode. |
| 5 | Existing cần sửa | Norm observation identity/structured payload/lifecycle listeners trong USE adapter; capability/coverage reporting; coordinator evidence envelope. |
| 6 | New proposed | SP/normative/NormativeObservation.java; SP/normative/NormLifecycleMonitor.java; SP/normative/NormPolicyProfile.java; normative fixtures/tests. |
| 7 | Package/module | USE bridge adapter emits neutral evidence; use-plugin SP/normative consumes; no NPL runtime fork. |
| 8 | Input/output | NS definition + official NPL events/state + clock/commitment scope → normative observation/transition results. |
| 9 | Data model | Board artifact incarnation, norm definition, activation occurrence, subject/substitution, modality, condition/aim, time-or-state deadline, state, clock evidence. |
| 10 | Mapping rules | M05/E01; OCL only pure bound state predicates; historical obligation/permission logic separate; no permission→must-do. |
| 11 | Runtime behavior | Compare lifecycle events/authoritative state; recurrent activation explicit; async deadline transitions journaled; no “not active ⇒ fulfilled”. |
| 12 | OCL semantics | Deadline/fulfillment check requires temporal evidence; source-reported unfulfilled khác world requirement false; maintenance condition không iStar MAINTAIN tự động. |
| 13 | Error/unsupported | Unresolved activation identity/clock → partial/inconclusive; source maintenance feature incomplete → unsupported; raw EVIDENCE_ONLY never relabeled verified normative equivalence. |
| 14 | Tests | Activation, fulfillment, deadline miss, inactivity, permission unused, state deadline, recurring same norm/substitution, board recreate, late done, listener gap. |
| 15 | Acceptance | A13: correct authoritative instance binding; source versions/clock/state evidence; normative verdict separates derivation from reported state; unsupported cases explicit. |
| 16 | Dependency | P4 allocation + P10/P11 lifetimes + P15 evidence; independent of P16 explorer; optional capability, mandatory if thesis claims normative conformance. |
| 17 | Rủi ro | Current norm ID hash norm/agent/unifier + session:board-name chưa chứng minh activation lifetime; no v0.6.1 source tag; maintenance generator TODO. |
| 18 | Trạng thái đầu | PARTIAL observer; independent full normative equivalence BLOCKED bởi source/version/formal coverage, generic translation UNSUPPORTED. |

Không tự phát minh activation token từ display name. Producer phải cung cấp lifecycle token/crosswalk đủ để nối created→fulfilled/unfulfilled; nếu nhiều candidates có cùng raw norm/unifier và không phân giải được, giữ AMBIGUOUS_NORM_INSTANCE. NPL clock và timer thread nằm ngoài agent pause; replay cần recorded logical time/transition evidence, không sử dụng thời gian máy replay để tái phán deadline.

### Phase 14 — USE Model / State Binding

| # | Trường | Công việc cụ thể |
|---|---|---|
| 1 | Mục tiêu | Tổng quát F2/F3 cho các capability mở rộng, giữ một native model/state owner. |
| 2 | Scope | MModel/MClass/MAttribute/MAssociation/(Class)/MObject/MObjectState/MLink/MSystem/State; schema, constraints, lifecycle, copy, views. |
| 3 | Out of scope | Recreate model mỗi cycle; second USE state trong use4Goal; production state machine suy từ tên operation. |
| 4 | Existing reuse | U02–U08/U12/U20–U23; native exporters/UseSystemApi; model-extension API chỉ khi admitted. |
| 5 | Existing cần sửa | Builders/engine gọi binding SPI; projection audit/symbol trace; exporters annotate new kinds; structural checks phân adapter failure vs requirement failure. |
| 6 | New proposed | SP/binding/UseProjectionBinding.java; SP/binding/SemanticMutationPlan.java; SP/binding/ProjectionReadView.java; CslUseBinding P1 mở rộng. |
| 7 | Package/module | SPI trong use-plugin SP/binding; native integration depends on SPI; CslUseBinding depends on SPI+USE API, không phụ thuộc facade/engine. |
| 8 | Input/output | MappingBundle + accepted observation → compiled MModel/binding registry → native mutation batches/checkpoints. |
| 9 | Data model | Source classifier/attribute/relationship IDs ↔ native symbols; helper kind; strict codecs; typed commands; structural/semantic constraint distinction. |
| 10 | Mapping rules | §11; exact end order/qualifiers; CSL same-instance role inheritance; profile-specific association class policy. |
| 11 | Runtime behavior | Validate mutation plan trước apply; one writer; create then values then links/deletes ordered; publish one view update; pinned copies for checks. |
| 12 | OCL semantics | All Φ registered; expressions context binds correct model revision; schema mismatch invalidates compiler cache/pre states explicitly. |
| 13 | Error/unsupported | Unknown class/attribute/type → reject+resync; real invariant violation giữ state; no null-class fallback to Entity/String. |
| 14 | Tests | MModel/MSystem identity; typed values; same IDs across states; link ends/qualifiers; rollback malformed batch; negative state retained; export/recompile parity. |
| 15 | Acceptance | A14: one active authoritative system; no carrier misclassified Entity; backend invariants biject manifest; native GUI receives consistent object diagram. |
| 16 | Dependency | F2/F3 P1; P2/P3 schema; P10/P11 updates; extends binding for P4/P7/P13. |
| 17 | Rủi ro | Legacy hardcoded DomainProjection names; generated symbol collisions; view/user mutation guards; additive extensions with pending @pre. |
| 18 | Trạng thái đầu | PARTIAL: native API/builders mạnh; CSL binding/schema policy chưa hoàn chỉnh. |

SPI cần được thêm ở bản tối thiểu trong P1, P14 tổng quát hóa; không chờ đến P14 mới có đường nối. Không đưa interface này vào use-core hoặc contract vì nó phụ thuộc target UML semantics. Refactor default behavior bằng characterization tests, không viết một mutation engine thứ hai cho CSL.

### Phase 15 — Verification Result / Evidence / Replay

| # | Trường | Công việc cụ thể |
|---|---|---|
| 1 | Mục tiêu | Tái lập verdict/violation với source provenance, profile và intentional history. |
| 2 | Scope | Detailed §16 envelope; constraint/source/state/before-after/actor-context/witness/coverage/versions/trace; counterexample/replay. |
| 3 | Out of scope | Runtime restoration of agent threads/external world; treating replay as new live source; legacy hash rewrite. |
| 4 | Existing reuse | RuntimeVerificationResult/VerificationSnapshot, journal/checkpoint/retention, NativeRuntimeReplay/StepController, CodeGroundedTraceIndex. |
| 5 | Existing cần sửa | Bundle manifest/version codec; semantic participant snapshots/savepoints; UI/exporters preserve detailed verdict; old bundle reader remains supported. |
| 6 | New proposed | SP/evidence/SemanticConstraintResult.java; SP/evidence/SemanticVerdict.java; SP/evidence/SemanticReplayCodec.java; TraceComplement và envelope P1/P3 mở rộng. |
| 7 | Package/module | use-plugin SP/evidence + existing native runtime/trace; portable artifacts nằm trong USE project/output khi chạy tests. |
| 8 | Input/output | Committed boundary + OCL + marking + trace → immutable result, witness, hash-linked replay bundle. |
| 9 | Data model | §16; schema/profile/model/constraint/complement hashes; before/after IDs; per-episode marking and history-continuity metadata. |
| 10 | Mapping rules | Source IDs→semantic element→native symbol→runtime occurrence→result; sourceKind domains không bị mất. |
| 11 | Runtime behavior | One commit publishes aligned state+markings+result; failed persistence flags evidence loss; replay recomputes same logical outcomes/markings. |
| 12 | OCL semantics | Store original typed outcome; aggregate inconclusive not pass; replay compile with recorded backend/profile version. |
| 13 | Error/unsupported | Tamper/hash/version/gap → reject/inconclusive; unsupported original feature giữ lý do; no swallowed journal failure. |
| 14 | Tests | Round-trip serialization seven outcomes; old bundle compatibility; exact counterexample source lookup; replay same markings; tampered/truncated/gapped trace; eviction/pinned checkpoints. |
| 15 | Acceptance | A15: from FAIL navigate exact object/value/state/source; replay gives same semantic result hash; clock durations excluded from logical hash; no mutable model references in evidence. |
| 16 | Dependency | F7/F8 P1; P3 complement; all monitor capabilities contribute; P16/P17 consume bundle/provenance. |
| 17 | Rủi ro | Existing hash schema fixed; new enum into old valueOf reader breaks compatibility; marking rollback/replay omission causes divergent history. |
| 18 | Trạng thái đầu | PARTIAL: evidence/replay infrastructure có; full semantics envelope/marking replay cần thêm. |

### Phase 16 — Bounded Behavior Verification

| # | Trường | Công việc cụ thể |
|---|---|---|
| 1 | Mục tiêu | Phân tích finite supported transition abstraction sau khi monitor ổn định; không dùng BPMN. |
| 2 | Scope | JaCaMo observed traces hoặc explicit abstract transition system; bounded state/steps/loops/populations/clock/interleavings; complete terminal traces; counterexamples. |
| 3 | Out of scope | Full JaCaMo model checking; trace replay tự chứng minh all behavior; unbounded proof/fairness; arbitrary Java successor synthesis. |
| 4 | Existing reuse | USE model/state/OCL, requirement monitor, normalized traces/replay; use4Goal Algorithm/Kodkod reference only, không import BPMN engine. |
| 5 | Existing cần sửa | Existing result/export integration nhận exploration evidence; build config chỉ khi chọn thêm solver sau finite executable prototype. |
| 6 | New proposed | SP/exploration/TransitionProfile.java; SP/exploration/BoundedExplorer.java; SP/exploration/ExplorationBounds.java; SP/exploration/ConformanceSummary.java. |
| 7 | Package/module | use-plugin SP/exploration; solver adapter optional cùng repo, no fork/use4Goal dependency. |
| 8 | Input/output | Explicit initial-state set + finite transitions + requirements/bounds → explored trace set + classification/risk/cutoff/witness. |
| 9 | Data model | Nodes=(projected state, executive descriptor, all-context markings, history); transitions include authority/guard/effect/frame assumptions; explored frontier/cutoffs. |
| 10 | Mapping rules | Only transitions approved by P5/P6/P12 abstraction; no “all fields not mentioned unchanged” unless declared frame condition. |
| 11 | Runtime behavior | Enumerate shared-state interleavings; evaluate every context after step; DFS/BFS finite; state merging includes temporal/executive state, not domain snapshot alone. |
| 12 | OCL semantics | State admissibility Φ; pre/guards/post; effect relation needs executable finite generator; runtime violations stay evidence even outside admissible abstract states. |
| 13 | Error/unsupported | Unsupported transition → UNSUPPORTED; cutoff/timeout/unexplored/zero complete scenarios → INCONCLUSIVE; solver error separate diagnostic. |
| 14 | Tests | Finite oracle with all conformant/mixed/none; no terminal trace; loop/population cutoff; two-agent interference; state merge different history; spurious witness concretization. |
| 15 | Acceptance | A16: statuses below exact; complete bounds/assumptions/frontier report; counterexample relates back to source; no unbounded wording. |
| 16 | Dependency | G2 stable P8/P9/P10/P11/P12/P14/P15 plus approved execution abstraction P5/P6; not prerequisite G1. |
| 17 | Rủi ro | State explosion, unsound effects/frame/POR/merge; no normative/external transition unless modeled. |
| 18 | Trạng thái đầu | TODO; behavioral-equivalence claim BLOCKED đến khi transition profile/concretization có evidence; full model checking UNSUPPORTED. |

Classification cho tập complete scenarios Π của **abstraction trong bounds**:

| Status | Điều kiện |
|---|---|
| CONFORMANT_WITHIN_BOUNDS | Π không rỗng; exhaustive trong configuration; mọi complete scenario/root/context satisfied |
| WEAK | Exhaustive; có cả conformant và non-conformant complete scenarios |
| NON_CONFORMANT | Exhaustive; Π không rỗng; không scenario nào conformant |
| INCONCLUSIVE | Cutoff, incomplete transition coverage, unknown terminal goals, chưa exhaustive hoặc Π rỗng |
| UNSUPPORTED | Source/transition/condition vượt admitted exploration profile |

Risk là trục riêng: một concrete violated-root witness đủ xác nhận risky trong scope; chưa tìm thấy witness khi exploration incomplete không là non-risky proof. Một observed non-conformant trace là counterexample/prefix evidence; chưa đủ phân loại toàn JaCaMo là NON_CONFORMANT theo Definition 7. Solver/exploration có thể bắt đầu bằng bounded enumerator trên finite scalar/role transitions, chỉ thêm Kodkod khi đã có oracle parity; không lấy USE evaluator làm successor generator.

### Phase 17 — Bidirectional Transformation

| # | Trường | Công việc cụ thể |
|---|---|---|
| 1 | Mục tiêu | Offline get/put có conflict detection cho supported structural/source fragment. |
| 2 | Scope | Typed domain schema, selected group/role/cardinality, initial scalar facts/interfaces, OPlan tree/operator/order; trace/complement; supported edits. |
| 3 | Out of scope | Arbitrary Java/ASL generation từ OCL; live state restore; full semantic round-trip; sửa source trong jacamo/use4Goal. |
| 4 | Existing reuse | Official source AST/provenance contracts, MappingBundle, TraceComplement, hash/version validation; source parsers as read-only oracles. |
| 5 | Existing cần sửa | Trace exporter giữ full original fragments/locations/ownership; explicit source-source update ownership; UI/export accepts proposed patch artifacts. |
| 6 | New proposed | SP/roundtrip/SupportedEditProfile.java; SP/roundtrip/SourceLens.java; SP/roundtrip/RoundTripConflict.java; SP/roundtrip/SourcePatchPlan.java. |
| 7 | Package/module | use-plugin SP/roundtrip; generated source/patch fixtures/output **trong repo USE**, không ghi vào hai reference checkouts. |
| 8 | Input/output | get(J)→CSL+iStar+OCL+K; put(Jold,targetEdit,K)→Jnew artifact/patch hoặc CONFLICT/AMBIGUOUS/UNSUPPORTED. |
| 9 | Data model | Original AST/fragments/comments khi yêu cầu byte preservation; IDs/location/digest; OPlan operator/order; unsupported semantic complement; provenance/version/edit ownership. |
| 10 | Mapping rules | Inverse column §8; only fields with declared update law; original unknown fragments kept opaque unchanged. |
| 11 | Runtime behavior | Offline transformation; compare source digest before put; no writes to live JaCaMo or private interpreter state. |
| 12 | OCL semantics | Target contracts constrain accepted edits; không synthesize unique implementation; observational equivalence only declared predicate profile. |
| 13 | Error/unsupported | Stale complement/source changed→CONFLICT; multi-owner/plan alternatives→AMBIGUOUS; behavior edit ngoài fragment→UNSUPPORTED. |
| 14 | Tests | GetPut, PutGet normalized AST; unchanged-source byte tests only if promised; rename/collision; sequence/parallel inverse; unsupported effect edit; stale digest. |
| 15 | Acceptance | A17: laws hold for admitted edit set; loss/complement explicit; syntactic reconstruction, structural round-trip và semantic equivalence được label riêng. |
| 16 | Dependency | P3/P5/P15 trace/source ownership; có thể làm sau static G2, không cần explorer P16; semantic claims cần oracle from monitor tests. |
| 17 | Rủi ro | Many-to-one mappings, complement out-of-date, digest không chứa source, preserved whole Java file không chứng minh target semantics expressive. |
| 18 | Trạng thái đầu | TODO restricted lens; full bidirectional semantics UNSUPPORTED. |

Luật dự kiến trên normalization N của supported representation: put(J,get(J),K)=J trong phạm vi đã hứa; get(put(J,e,K))=N(e) cho admissible edit e. Không chọn ngầm một inverse khi nhiều source realizations phù hợp. Nếu chỉ phục hồi original source từ complement thì gọi syntactic preservation của unchanged part, không gọi “CSL bảo toàn toàn bộ semantics”.

### Phase 18 — Test Strategy

| # | Trường | Công việc cụ thể |
|---|---|---|
| 1 | Mục tiêu | Test matrix §20 làm executable evidence theo phase, không có tests mirror implementation. |
| 2 | Scope | Unit semantics, native binding, official adapter boundary, live E2E, replay/migration, source-oracle and property tests. |
| 3 | Out of scope | Assumptions/skip được tính pass cho mandatory G1; tests chỉ tạo USE objects rồi gọi live runtime integration. |
| 4 | Existing reuse | T01–T08 và bridge tests; fixtures/native exporter/replay helpers; JUnit 5.8.2, Surefire/Failsafe configs. |
| 5 | Existing cần sửa | Characterization tests khi seam thay đổi; test runner config nếu discovery sai; existing test expectations chỉ đổi khi semantics/profile version có lý do. |
| 6 | New proposed | semantic test package mirror production; resources semantics/profile-v1; LiveCslVerificationIT/NormLifecycleIT/RoundTripLawTest/BoundedExplorerTest. |
| 7 | Package/module | use-plugin/src/test/java/org/tzi/use/plugins/jacamo/semantics và src/test/resources; producer tests trong bridge module. |
| 8 | Input/output | Matrix row+oracle+fixture → machine-readable test report/evidence bundle/source hashes. |
| 9 | Data model | Test case ID, required capability, expected verdict/marking, observation quality, invariant witness, source versions, execution result. |
| 10 | Mapping rules | Every catalog rule/admission/loss branch at least one positive or unsupported case; all enum literal paths covered. |
| 11 | Runtime behavior | Per-test isolated environment/session IDs; deterministic barriers/latches; terminate runtime threads; no unrelated repository mutation. |
| 12 | OCL semantics | Truth-table/pair-state independent oracle; test undefined/error distinct; no heuristic goal expected values. |
| 13 | Error/unsupported | Environment unavailable→BLOCKED test evidence, không green acceptance; intended unsupported feature must assert exact reason. |
| 14 | Tests | §20 là authoritative matrix; §20.3 lệnh chạy dự kiến; regression/native packaged path included. |
| 15 | Acceptance | A18: counts/discovery/baseline failures tracked; G1 actual runtime data; all required matrix rows pass for admitted profile. |
| 16 | Dependency | Bắt đầu P0, chạy liên tục từng change; không đợi P18 mới viết tests; gate P20. |
| 17 | Rủi ro | Flaky timers/port conflicts/shared singleton Cartago; stale target artifacts; tests skipped; transitive runtime version drift. |
| 18 | Trạng thái đầu | PARTIAL: nhiều native/bridge tests tồn tại; semantic tests mới chưa viết/chạy. |

### Phase 19 — Performance / Concurrency

| # | Trường | Công việc cụ thể |
|---|---|---|
| 1 | Mục tiêu | Đo bottleneck và tối ưu sau correctness, giữ equivalence với full reevaluation. |
| 2 | Scope | Agents/artifacts/properties/roles/goals/constraints; rates; latency, throughput, heap/retention, replay size; concurrent source traffic. |
| 3 | Out of scope | SLA/throughput giả định chưa đo; drop callback/checkpoint âm thầm; optimization làm đổi monitor history. |
| 4 | Existing reuse | RuntimePerformanceStats, backlog/highWatermark, snapshot retention, coordinator timings, replay size/hash evidence. |
| 5 | Existing cần sửa | Add distribution/phase timings, queue saturation metrics; worker scheduling chỉ khi pinned state/version-safe; dependency selector guarded fallback. |
| 6 | New proposed | SP/benchmark/VerificationLoadScenario.java trong test/benchmark source set; benchmark manifest/result schema; no production dependency JMH trừ khi chọn explicit. |
| 7 | Package/module | use-plugin test/benchmark trong repo USE; metrics extension existing runtime; no external service requirement. |
| 8 | Input/output | Reproducible fixture/config/runtime fingerprints → p50/p95/p99, throughput, memory/replay metrics + correctness hashes. |
| 9 | Data model | Population/rates/workload mix, warmup/measure duration, machine/JVM, cutoff/backpressure, sampling/GC info, per-stage timings. |
| 10 | Mapping rules | Same semantic profile; no extra lossy projection for fast benchmark; isolate measurement of excluded vs admitted concepts. |
| 11 | Runtime behavior | One serialized writer; worker results only publish for intended version; slow consumers flag backlog/stale; no coalescing temporal events without proof. |
| 12 | OCL semantics | Incremental evaluation output must equal full oracle, including allInstances/navigation/shared dependencies and every temporal update boundary. |
| 13 | Error/unsupported | Saturation/gap → incomplete/resync; timeout/result staleness explicit; resource cutoff INCONCLUSIVE, không PASS. |
| 14 | Tests | Load matrix §24; two-agent shared writes; reorder/gap stress; long history retention; same-run resync; serial vs optimized parity. |
| 15 | Acceptance | A19: measured budgets and reproducible configs; no semantic hash divergence; bounded memory/queue behavior; baseline and optimized results report. |
| 16 | Dependency | G2 correctness/test gates; metrics can add early but optimize after P18 coverage; before large P16 runs. |
| 17 | Rủi ro | EDT stalls, snapshot copies, OCL quantifier cost, retention/file I/O, source locks; measurements perturb timing. |
| 18 | Trạng thái đầu | PARTIAL: timings/backlog/retention có; scalable benchmarks/percentiles chưa có. |

### Phase 20 — Final Acceptance

| # | Trường | Công việc cụ thể |
|---|---|---|
| 1 | Mục tiêu | Chứng nhận đúng supported thesis deliverable bằng evidence, không gom feature thiếu vào chữ “complete”. |
| 2 | Scope | 14 tiêu chí người dùng + phase acceptance A00–A19 theo claim manifest; source traceability, tests, reproducibility, limitations. |
| 3 | Out of scope | Tuyên bố unbounded/full JaCaMo equivalence hoặc inverse tổng quát. |
| 4 | Existing reuse | Existing release/version/compatibility manifests, native package smoke, trace/evidence/replay exporters. |
| 5 | Existing cần sửa | Release/compatibility/profile capability manifests mới; documentation/UI claims; không sửa historical fingerprints để che incompatibility. |
| 6 | New proposed | Thesis acceptance manifest, implementation-report.md khi có code, supported-profile documentation, reproducible example/replay bundles trong USE. |
| 7 | Package/module | Repo USE; docs/test/release artifacts; referential repos read-only. |
| 8 | Input/output | Test/benchmark/replay/mapping evidence → acceptance report per capability/claim, remaining unsupported register. |
| 9 | Data model | Claim ID→profile/capability→source/rule→tests/results→bundle hashes→limits. |
| 10 | Mapping rules | No admitted source element disappears; all lossy rules report limitations; inverse scope explicit. |
| 11 | Runtime behavior | Launch real fixture, observe multiple states/incarnations, E2E failure/recovery, replay same violation and markings. |
| 12 | OCL semantics | Current state/two-state checks real; exact @pre; temporal truth tables; conditions/bindings independently inspectable. |
| 13 | Error/unsupported | Feature incomplete→PARTIAL/UNSUPPORTED/BLOCKED with reason; không “green” vì hidden skip/empty context. |
| 14 | Tests | Full required matrix + production packaged smoke + G1 fixture + backward compatibility; review evidence bundle fresh from clean build. |
| 15 | Acceptance | A20: §21/26 checklists fulfilled for named deliverable; extension claims P16/P17 only with own evidence; no silent exceptions. |
| 16 | Dependency | P0/P1 and relevant P2–P15/P18/P19; P16/P17 required only for corresponding explicit extension claim, otherwise report unfinished scope. |
| 17 | Rủi ro | Overclaiming from samples; using old binary results; ambiguous profile version; missing independent reproducibility. |
| 18 | Trạng thái đầu | TODO; không có final implementation acceptance trong lượt chỉ lập kế hoạch này. |

## 18. File / Class Change Matrix

### 18.1. Path convention và trách nhiệm

Mọi path trong bảng này là **repo-relative từ D:/_CODE_BANK/Project_/08_Thesis/use**. Existing path đã được tìm trong inventory. New path/package là **proposed**, chưa được tạo. Việc nhiều phase nhắc cùng class là mở rộng một class theo capability, không tạo nhiều implementation.

SP = use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/. ST = use-plugin/src/test/java/org/tzi/use/plugins/jacamo/semantics/. Hai prefix này chỉ viết gọn path dự kiến mới. Resources semantic profile dự kiến dưới use-plugin/src/main/resources/org/tzi/use/plugins/jacamo/semantics/. Suffix class/file mới ở phase được resolve dưới SP/ST tương ứng.

| Phase | Existing file/class | Action | New file/class — proposed | Purpose | Dependency |
|---|---|---|---|---|---|
| 0 | pom.xml; use-plugin/pom.xml; jacamo-bridge-jacamo/pom.xml | Audit/pin effective versions; chỉ đổi build khi có incompatibility cụ thể | SP/profile/SemanticsProfile.java; SemanticProjectionProfile.java; UnsupportedFeature.java | Profile/capability contract | Report/API baseline |
| 0 | use-plugin/src/main/java/org/tzi/use/plugins/jacamo/codegrounded/trace/NativeComponentVersionManifest.java | Reuse/extend manifest metadata | semantic-baseline.json resource | Provenance/runtime compatibility | Official artifact hashes |
| 1,14 | use-plugin/src/main/java/org/tzi/use/plugins/jacamo/codegrounded/CodeGroundedNativePipeline.java | Extend build composition, giữ one model/system guard | SP/binding/UseProjectionBinding.java; ProjectionReadView.java | One native pipeline with explicit profile SPI | MappingBundle/USE API |
| 1,2,14 | use-plugin/src/main/java/org/tzi/use/plugins/jacamo/codegrounded/use/NativeUseModelBuilder.java | Add binding input, default unchanged | SP/csl/CslSchema.java; CslUseBinding.java | CSL types/Φ ↔ native schema | Profile/source contract |
| 1,14 | use-plugin/src/main/java/org/tzi/use/plugins/jacamo/codegrounded/use/NativeUseStateBuilder.java | Profile-aware initial population/bindings | SP/binding/SemanticMutationPlan.java | Typed create/update/link plan; no fake live declarations | CslUseBinding |
| 1,2 | use-plugin/src/main/java/org/tzi/use/plugins/jacamo/codegrounded/use/NativeProjectionPolicy.java; NativeProjectionProfile.java; NativeProjectionMode.java | Preserve existing vocabulary and AUTO/FULL; expose profile-specific admission seam | SP/profile/SemanticProjectionProfile.java | Separate meaning from amount of projection | Import configuration |
| 1,2 | use-plugin/src/main/java/org/tzi/use/plugins/jacamo/codegrounded/use/DomainProjection.java | Keep old codec default; new binding chooses strict codec | SP/csl/ScalarValueCodec.java | Integer/Real/Boolean/String policy and diagnostics | USE Value types |
| 2,4,5 | use-plugin/src/main/java/org/tzi/use/plugins/jacamo/codegrounded/use/MoiseDomainProjection.java | Reuse validate/IDs/operator/order; profile branches avoid duplicate enactment | SP/csl/CslStructuralPolicy.java; SP/mapping/FunctionalMappingRules.java | Organizational and functional semantics distinct | OfficialMoiseAdapter DTOs |
| 1,10,11,14 | use-plugin/src/main/java/org/tzi/use/plugins/jacamo/codegrounded/runtime/NativeRuntimeMutationEngine.java | Execute semantic plans via existing kernel/savepoints/bindings/tombstones | SP/runtime/SemanticObservationMapper.java; ScopedSnapshotReconciler.java | No second mutation engine | Native binding SPI |
| 1,10,11 | use-plugin/src/main/java/org/tzi/use/plugins/jacamo/codegrounded/runtime/NativeRuntimeProjector.java | Extend snapshot diff/admission; default regression guarded | SP/runtime/ObservationContinuity.java; EnactmentLifetimeRegistry.java | Identity/coverage across resync | EventLedger/BridgeEntityId |
| 1,8,9,15 | use-plugin/src/main/java/org/tzi/use/plugins/jacamo/codegrounded/runtime/RuntimeVerificationCoordinator.java | Own semantic participant savepoints, OCL/marking commit/envelope | SP/runtime/SemanticCheckpointParticipant.java | Atomic state+monitor+result publication | One existing writer |
| 1,8 | use-plugin/src/main/java/org/tzi/use/plugins/jacamo/codegrounded/constraint/ExternalOclConstraintService.java | Share typed evaluation adapter/compiler/coverage | SP/ocl/SemanticOclCompiler.java; OclEvaluationAdapter.java; ConstraintCoverageResolver.java | Seven outcomes without duplicate evaluator | USE OCL AST/compiler |
| 1,8,12 | use-plugin/src/main/java/org/tzi/use/plugins/jacamo/verification/DefaultVerificationService.java; OperationCheck.java | Reuse pre copying/two-state evaluation; detailed methods/wrapper | SP/ocl/CompiledSemanticConstraint.java | Exact pre/post/context/error handling | Existing registry |
| 1,12 | use-plugin/src/main/java/org/tzi/use/plugins/jacamo/codegrounded/runtime/NativeOperationCheckpoints.java | Extend pairing lifecycle/result categorization | SP/runtime/InvocationBoundary.java; InvocationCoveragePolicy.java | @pre per invocation and source-cut evidence | Cartago events |
| 1,9 | use-plugin/src/main/java/org/tzi/use/plugins/jacamo/codegrounded/runtime/GoalViewSnapshot.java | Keep organizational observation DTO; add explicit requirement references | SP/istar/LeafAchieveMonitor.java; TemporalGoalMonitor.java; TaskMonitor.java; IntentionalMarking.java | Requirements separate from board/runtime state | Typed OCL/GoalBinding |
| 1,9,15 | use-plugin/src/main/java/org/tzi/use/plugins/jacamo/DefaultJaCaMoFacade.java; JaCaMoFacade.java | Compose selected profile and immutable result/requirement view | SP/istar/RequirementBindingResolver.java | Same session/system, no alternate runtime | Pipeline/coordinator |
| 9,15 | use-plugin/src/main/java/org/tzi/use/plugins/jacamo/ui/JaCaMoWorkbenchPanel.java | Add semantic result/requirement view hook; cached reads | SP/ui/RequirementStatusPanel.java | Show verdict/coverage/previous-current marking | Immutable DTO; no evaluator in UI |
| 3 | use-plugin/src/main/java/org/tzi/use/plugins/jacamo/codegrounded/model/JacamoSpecificationModel.java | Reuse canonical official source validation; thin normalized frontend view | SP/mde/JacamoEcoreSourceLoader.java; SP/mapping/SourceModelNormalizer.java; MappingRuleCatalog.java; MappingBundle.java | One source rule catalog; missing EMF features explicit | Profile + source supplements |
| 3,17 | use-plugin/src/main/java/org/tzi/use/plugins/jacamo/codegrounded/trace/CodeGroundedTraceIndex.java; CodeGroundedTraceExporter.java | Add complement references/versioned loss metadata | SP/evidence/TraceComplement.java | Recoverable source ownership/AST/fragments | Source digests |
| 2,3 | use-plugin/Core/Metamodel/version-2/jacamo_v2_complete.ecore; use-plugin/release/v2-freeze-manifest.json | Read/reuse distinction, **không overwrite frozen artifacts** | use-plugin/Core/Metamodel/csl-monitor-v1/CSL.ecore; RuntimeObservation.ecore; Trace.ecore | New versioned M2 resources only where EMF needed | Validated semantic records/serialization |
| 4 | jacamo-bridge-jacamo/src/main/java/org/jacamo/bridge/adapter/OfficialMoiseAdapter.java; MoiseBoardSnapshotSource.java; MoiseGoalObservation.java | Reuse; additive exact functional coverage metadata | SP/observer/FunctionalObservation.java; SP/istar/MoiseRequirementBinder.java | Definition/instance/board evidence | P3/P10 contracts |
| 5 | use-plugin/src/main/java/org/tzi/use/plugins/jacamo/codegrounded/use/MoiseDomainProjection.java | Preserve decompositionOperator/orderInParent | SP/model/ExecutionDescriptor.java; SP/runtime/OPlanTransitionMonitor.java | Outcome vs ordered/parallel execution | Source operator/trace |
| 6,7 | jacamo-bridge-jacamo/src/main/java/org/jacamo/bridge/adapter/OfficialJasonAdapter.java; BridgeAgArch.java; JasonSnapshotSource.java; JasonBeliefEvidence.java | Reuse parser/hooks; additive structured observations/capabilities | SP/observer/ExecutionObservation.java; MentalStateObservation.java; SP/mapping/JasonAbstractionPolicy.java; BeliefDomainBinding.java | No generic plan/world-fact inference | Runtime cycle/source provenance |
| 10 | jacamo-bridge-contract/src/main/java/org/jacamo/bridge/contract/RuntimeSnapshot.java; RuntimeEvent.java; ContractPayloads.java | Add optional versioned fields only when needed; decode old payloads | ScopedCoverage.java (proposed in same contract package); SP/runtime/SemanticObservationEnvelope.java | Cut/coverage metadata without USE/JaCaMo imports | Protocol negotiation |
| 10,11 | jacamo-bridge-jacamo/src/main/java/org/jacamo/bridge/adapter/SnapshotCoordinator.java; CartagoSnapshotSource.java | Extend exported cut/topology/scope evidence | Adapter-side coverage helpers only after proving reuse gap | Stable observed cut | Existing loggers/watermarks |
| 13 | jacamo-bridge-jacamo/src/main/java/org/jacamo/bridge/adapter/NplBoardSnapshotSource.java | Extend norm activation/board incarnation/structured evidence | SP/normative/NormativeObservation.java; NormLifecycleMonitor.java; NormPolicyProfile.java | Separate normative monitoring | Actual NPL API |
| 15 | use-plugin/src/main/java/org/tzi/use/plugins/jacamo/codegrounded/runtime/VerificationSnapshot.java; RuntimeVerificationResult.java; RuntimeEventJournal.java; RuntimeCheckpointStore.java; NativeRuntimeReplay.java | Extend via versioned semantic envelope/bundle; preserve old codec/hashes | SP/evidence/SemanticVerdict.java; SemanticConstraintResult.java; SemanticVerificationEnvelope.java; SemanticReplayCodec.java | Evidence/marking replay | Typed results/profile |
| 16 | use-plugin/src/main/java/org/tzi/use/plugins/jacamo/codegrounded/runtime/NativeRuntimeReplay.java | Read-only reuse for concrete witness/trace replay | SP/exploration/TransitionProfile.java; BoundedExplorer.java; ExplorationBounds.java; ConformanceSummary.java | Finite JaCaMo abstraction, no BPMN | G2 + transition oracle |
| 17 | use-plugin/src/main/java/org/tzi/use/plugins/jacamo/codegrounded/trace/CodeGroundedTraceExporter.java | Reuse trace/source references | SP/roundtrip/SourceLens.java; SupportedEditProfile.java; SourcePatchPlan.java; RoundTripConflict.java | Offline get/put fragment | Complement/digests |
| 18 | use-plugin/src/test/java/org/tzi/use/plugins/jacamo/codegrounded/LiveCartagoNativeVerificationTest.java; NativeOperationCheckpointTest.java; NativeRuntimeBridgeCoverageTest.java | Preserve/extend regression tests, no fake live claims | ST/csl/CslVerticalSliceIT.java; ST/istar/TemporalGoalMonitorTest.java; ST/ocl/DetailedVerdictTest.java; further matrix tests | Executable semantic evidence | Every phase |
| 19 | use-plugin/src/main/java/org/tzi/use/plugins/jacamo/codegrounded/runtime/RuntimePerformanceStats.java; SnapshotRetention.java | Add measured distributions/limits, preserve correctness | use-plugin/src/test/java/org/tzi/use/plugins/jacamo/semantics/benchmark/VerificationLoadScenario.java | Reproducible load/semantic parity | G2 correctness |
| 20 | use-plugin/compatibility.json; use-plugin/release/release-manifest.json | New version/capability declaration after tests, preserve frozen versions | implementation-report.md; docs/jacamo-csl-supported-profile.md; acceptance manifest (all proposed) | Evidence-backed delivery | Acceptance gates |

Các class SP chưa liệt kê đầy đủ suffix trong bảng nhưng được nêu ở phase thuộc cùng path convention; coding agent phải giữ responsibility, được gộp record nhỏ trong owner file thay vì tạo hàng chục microclasses. Tên proposed không là cam kết API public.

### 18.2. Tránh duplicate source/verification infrastructure

Existing org.tzi.use.plugins.jacamo.semantic.JaCaMoSemanticModel đã có normalized IR/source/symbol indexes; VerificationSemanticLayer đã có explicit modifications cho frozen TransformationPlan. Tuy nhiên layer sau chỉ REMOVE_INHERITANCE/OVERRIDE_FORWARD_MULTIPLICITY, không phải CSL/iStar monitor. [U24] [U25]

Proposed SemanticModel chỉ là **target bundle/semantic view** trên JacamoSpecificationModel và existing metadata, không copy toàn bộ Jason/MOISE/CArtAgO source DTOs. Nếu có thể biểu diễn bằng existing immutable types thì reuse trực tiếp. Không đưa frozen structural VerificationSemanticLayer vào runtime để coi việc bỏ inheritance/đổi multiplicity là proof CSL. Existing diagnostics/source spans/hash helpers được tái sử dụng khi compatible; không merge hai ID schemes bằng display name.

## 19. Dependency Graph

### 19.1. Module và package direction

~~~text
jacamo-bridge-jacamo -> jacamo-bridge-contract + official JaCaMo runtime
use-plugin          -> jacamo-bridge-contract + host USE APIs (provided)
use-gui             -> use-core
use-core            -> generic UML/OCL/SOIL infrastructure
use-plugin tests    -> jacamo-bridge-jacamo + official runtime
use4Goal            -> reference only; no production/build dependency
~~~

Within use-plugin:

~~~text
profile/model/observer/evidence records
   <- mapping / mde / istar definitions
   <- binding SPI (USE types only at backend boundary)
   <- CslUseBinding / OCL compiler + evaluator adapter
   <- existing native builders/engine/coordinator
   <- facade / UI composition

requirement monitor <- typed OCL outcomes + immutable observations/markings
replay              <- recorded profile/bindings/results + same pipeline
exploration         <- admitted transitions + same binding/OCL/monitor
roundtrip           <- mapping trace/complement + source edit profile
~~~

Không để CslUseBinding gọi facade/coordinator; nó sinh binding/mutation plan, kernel hiện có thực thi. Native pipeline composition chọn binding, không để profile/model depend on concrete engine. Producer contract không import org.tzi.use.*, consumer semantic model không import live Jason/Cartago objects. UI chỉ consume immutable envelopes.

### 19.2. Thứ tự dependency của phases

~~~mermaid
flowchart LR
  P0 --> P1
  P1 --> P2
  P2 --> P3
  P3 --> P4
  P4 --> P5
  P3 --> P6
  P3 --> P7
  P1 --> P10
  P10 --> P11
  P11 --> P12
  P2 --> P14
  P14 --> P8
  P7 --> P8
  P8 --> P9
  P4 --> P9
  P5 --> P9
  P6 --> P9
  P12 --> P9
  P9 --> P15
  P11 --> P15
  P4 --> P13
  P15 --> P13
  P15 --> P19
  P5 --> P16
  P9 --> P16
  P12 --> P16
  P15 --> P16
  P3 --> P17
  P5 --> P17
  P15 --> P17
  P19 --> P20
  P13 --> P20
  P18[Tests start P0 and accompany every phase] --> P20
~~~

P1 đã thực hiện F1–F8 nên diagram không tạo vòng chờ P8/P14 mới làm được P1. Sau G1, **có thể phát triển song song** P2/P3 schema và P10/P11 runtime extensions khi contracts đã chốt; P6/P7 extraction/mental test design; normative fixtures sau P4 và round-trip edit spec sau P3/P5. Những phần bắt buộc tuần tự: profile → mapping admission → schema/bindings → accepted state → OCL outcomes → marking commit → evidence.

P16 và P17 là extension tracks, không phụ thuộc nhau. P18 thực hiện xuyên suốt, không chặn đầu vào bằng yêu cầu “đợi Phase 18”. P19 đo sớm được nhưng tối ưu chỉ sau correctness; P20 phải ghi rõ extension nào chưa nghiệm thu.

## 20. Test Matrix A–Z

### 20.1. Existing regression anchors

Các class sau tồn tại và đã đọc source/method signatures; **chưa chạy lại ở lượt này**:

| Anchor | Test có sẵn | Điều bảo vệ |
|---|---|---|
| T01 | LiveCartagoNativeVerificationTest | Official Cartago/logger→facade/native OCL/history/replay; transient violation/property removal/artifact recreate/gap |
| T02 | NativeOperationCheckpointTest | Exact pre/post correlation; real USE @pre; false pre report-only; failed post skipped; typed parameter compile context |
| T03 | NativeRuntimeBridgeCoverageTest | Queue overflow, mirror rejection/subscription failure invalidate cached PASS |
| T04 | SelectiveVerificationProjectionTest | Belief provenance/demand-driven projection; framework artifacts exclusion; unresolved provider evidence |
| T05 | NativeGenericStepReplayTest | Create/delete/value/link mutations; atomic rollback; corrupt replay controls |
| T06 | ReconnectSnapshotTest | Same producer resync vs new generation retention/reset |
| T07 | VerificationSnapshotContractTest | Immutable portable snapshot/evidence contract |
| T08 | NativeProjectionAuditTest | Explicit dependency reason for classes/associations/objects |

Bridge adapter module cũng có SnapshotCoordinatorTest, OfficialCartagoAdapterTest, OfficialMoiseIdentityTest, MoiseGoalObservationTest, MoiseBoardSnapshotSourceSelectionTest. Những tests này bảo vệ substrate, không thay thế CSL/iStar tests mới.

### 20.2. Required matrix

| ID | Nhóm / case | Oracle và expected result | Phase |
|---|---|---|---|
| A01 | CSL OrgCtx nesting | Valid declaration tree compile; cycle reject; no phantom context population | 2/14 |
| A02 | Role inheritance | Single role object inherits attributes; parent play-chain không được tạo | 2 |
| A03 | Multi-parent source role | Explicit UNSUPPORTED/loss with all parents preserved, không drop parent | 2/3 |
| A04 | Compatibility | Allowed/disallowed pair, same context/player; scoped unsupported không tự “compatible” | 2 |
| A05 | Multiplicity/cardinality | Per-context group-role bounds; observed violation giữ state + FAIL | 2/14 |
| A06 | Invariant manifest | Every authored/generated Φ compiled/registered; p=-1 witness retained | 1/8 |
| B01 | Artifact recreate same name | Different UUID/new MObject; old incarnation event rejected | 11 |
| B02 | Agent restart | New incarnation, no accidental old role/goal episode latch | 11 |
| B03 | Role same name two groups/OS | Exact group-role semantic binding, no cross-context alias | 2/11 |
| B04 | Leave/adopt same tuple | Distinct observed lifetime; polling hidden interval reports continuity unproven | 11 |
| C01 | Partial snapshot | Missing object/property does not delete/default false; dependent check SKIPPED | 10/11 |
| C02 | Stale/out-of-order | No mutation; stale evidence and resync required | 11 |
| C03 | Gap/overflow | Cached PASS invalidated; dependent goals not newly satisfied; journal loss explicit | 10/11/15 |
| C04 | Resync | Same identities stable; current proof restored only with scope coverage; past gap still affects temporal proof | 11 |
| C05 | Topology change during capture | Retry stable cut or incomplete diagnostic; no mixed accepted cut | 10 |
| C06 | Duplicate event/content mismatch | Idempotent exact duplicate; same ID different content ERROR | 11 |
| D01 | Belief positive/negative | p and ~p observations preserved; absence not explicit negation | 7 |
| D02 | Belief namespace/terms/annotations | No merge across owner/namespace/source; raw+structured values agree | 7 |
| D03 | Rule vs stored fact | Rule retained as rule/evidence; no fake ground world fact | 3/7 |
| D04 | Stale perception | Artifact price=12, belief price=10 queried independently | 7 |
| E01 | Scalar property types | Integer stays Integer; Boolean/String/finite Real faithful in declared carrier | 1/10/14 |
| E02 | Duplicate property names | Different fullIds preserved in evidence; scalar slot mapping rejected unless explicit collection schema | 2/10 |
| E03 | Arity > 1 / object / null | UNSUPPORTED codec or explicit admitted tuple schema; no truncate/stringify-as-world silently | 2/10 |
| E04 | Type change | Integer→Double fails strict Integer binding; no hidden schema type mutation | 1/14 |
| E05 | Property removal/addition | Slot becomes undefined/absent per schema with coverage; re-add maps correct fullId/lifetime | 10/11 |
| F01 | NONE T→F | Current marking T→F under complete observation | 9 |
| F02 | ACHIEVE F→T→F | U→T→T, same episode; witness retained | 1/9 |
| F03 | MAINTAIN T→F→T | T→F→F; past violation not cleared | 9 |
| F04 | SUSTAIN F→T→F→T | U→T→F→F under declared profile | 9 |
| F05 | Goal missing/undefined/error | No false/new satisfaction; detailed verdict + U/quality; historical latch policy tested | 8/9 |
| F06 | Activation/reset/termination | Fresh episode explicit; early trace stop no automatic liveness FAIL | 9 |
| F07 | Temporal history gap | Maintain/Sustain cannot be proven from current resync alone | 9/11 |
| G01 | AND/OR three-valued propagation | Truth tables independently specified, empty refinement rejected | 9 |
| G02 | Compound parent temporal type | Parent NONE follows evaluated children; parent MAINTAIN latches own violation | 9 |
| G03 | Dependency cross actor/context | Correct bound dependee occurrence; unresolved binding U | 9 |
| G04 | SEQUENCE vs PARALLEL | Same AND outcome, different operator/order/execution constraints and traces | 5 |
| G05 | CHOICE | OR outcome, source enabling/commitment separate; no assumed XOR | 5 |
| H01 | @pre positive/negative | Same before p=0, after 1 PASS; after 2 FAIL; pre object values unchanged | 1/8/12 |
| H02 | Overlapping operations | A/B retain distinct pre cuts/correlations; interference recorded | 12 |
| H03 | Failure/abort/timeout | No successful post-conformance; SKIPPED/inconclusive with evidence; capacity eviction visible | 12 |
| H04 | Suspend/resume | Original pre retained; no new invocation on resume | 12 |
| H05 | Wrong incarnation/signature/args | No accidental match by operation name; exact mismatch diagnostic | 12 |
| H06 | Params/result and @pre navigation | Correct typed compiler context; missing field/type diagnostics explicit | 8/12 |
| I01 | MOISE enactment | Accepted board player tuple produces exactly Plays/InContext; rejected adopt request does not | 1/4/11 |
| I02 | Mission commitment | Same mission across scheme instances distinct; leave reflected with authority | 4 |
| I03 | Goal state/arguments | waiting/enabled/satisfied and committed/achieved lists from public board contract | 4 |
| I04 | Min/all and performance | Selected predicate vs board oracle; no generic >=min shortcut | 4 |
| I05 | Maintenance source limitation | Explicit unsupported normative realization; authored iStar MAINTAIN not conflated | 4/13 |
| J01 | Norm activation/fulfillment | Correct activation instance/lifetime; created→fulfilled evidence | 13 |
| J02 | Deadline/violation/inactive | Recorded time/state deadline; unfulfilled distinct from inactive | 13 |
| J03 | Permission unused | No obligation violation inferred | 13 |
| J04 | Recurrent norm/board recreate | Distinct identity or explicit ambiguity; no merge by raw text | 13 |
| K01 | GetPut | Same normalized supported source AST after get/put, opaque fragments preserved | 17 |
| K02 | PutGet | Supported edit reflected exactly modulo declared normalization | 17 |
| K03 | Unsupported/stale/ambiguous edit | UNSUPPORTED/CONFLICT/AMBIGUOUS, no generated arbitrary body | 17 |
| L01 | Mandatory live E2E | Actual JaCaMo adapter observation→native objects/links/values→OCL→ACHIEVE→result | 1/18 |
| L02 | Shared-state interference | Change by B reevaluates goals bound to A/C on same state version | 9/12 |
| L03 | Replay violation | Same source/constraint/state witnesses, outcomes and previous/current markings | 15 |
| L04 | Invalid projection rollback | No half-state/view; real requirement violation remains observable | 11/14 |
| L05 | Legacy/package compatibility | Current default launch/old profile/old replay still works; new feature version negotiated | 0/15/20 |
| M01 | Bounded classification | Exhaustive finite oracle all/mixed/none; zero terminal/cutoff inconclusive | 16 |
| M02 | Temporal state merge | Same domain state different marking/history not merged unsoundly | 16 |
| M03 | Spurious counterexample | Concrete/source trace confirmation or explicit abstract-only witness | 16 |
| N01 | Concurrency/load parity | Optimized output equals full oracle under same normalized input/version | 19 |
| N02 | Backpressure/retention | Saturation visible; bounded resources; gap/incomplete not silent data loss | 19 |

Expected values trong matrix là oracle thiết kế, không kết quả tests hiện tại. Mỗi row cần test/result ID + version + fixture hash trước khi mark DONE.

### 20.3. Lệnh kiểm thử dự kiến và điều kiện chạy

Chỉ thực hiện các lệnh này **khi bước implementation được yêu cầu**, không chạy trong lượt chỉ ghi kế hoạch. Working directory là repository USE; Java 21 và Maven 3.9.9 đã tìm thấy. Dùng PowerShell quoting cho -D để tránh tách property.

~~~powershell
mvn -pl use-plugin -am test-compile
mvn -pl use-plugin -am test-compile org.apache.maven.plugins:maven-surefire-plugin:3.2.5:test '-Dtest=NativeOperationCheckpointTest,NativeRuntimeBridgeCoverageTest,SelectiveVerificationProjectionTest,NativeGenericStepReplayTest,ReconnectSnapshotTest,VerificationSnapshotContractTest,NativeProjectionAuditTest,LiveCartagoNativeVerificationTest' '-Dsurefire.failIfNoSpecifiedTests=false'
mvn -pl jacamo-bridge-jacamo -am test
mvn -pl use-plugin -am verify '-Dit.test=CslVerticalSliceIT' '-Dfailsafe.failIfNoSpecifiedTests=false'
~~~

Lệnh cuối chỉ áp dụng sau khi proposed IT tồn tại; use-plugin hiện có Failsafe 3.2.5. P0 phải xác nhận test discovery/counts của các module, không coi exit 0 + zero discovered tests là nghiệm thu. Targeted direct Surefire goal pin version là phương án cho baseline unit tests; khi build config được chuẩn hóa, dùng lifecycle command đã chứng minh chạy đúng engines. Không chạy lại toàn suite liên tục nếu scoped tests đã pass và không có thay đổi mới.

Packaged verification riêng cần assembly/plugin packaging và launch bằng distributed classpath; không dùng IDE classpath để chứng nhận shade/provided/runtime isolation. Package phase hiện stage plugin vào use-gui/lib/plugins trong USE, cần ghi side effects/cleanup trong test evidence. Test resources/generated source/replay phải nằm trong USE/test temp ownership; không build hoặc sửa hai reference repositories.

## 21. Acceptance Criteria

| Gate | Điều kiện quan sát được | Evidence tối thiểu |
|---|---|---|
| AC01 Static subset | Rule catalog/emitted CSL+iStar+OCL trace to source, mọi source feature có decision | Model/profile/trace hashes, rule coverage |
| AC02 Real runtime | Official adapters actual runtime objects/APIs, không toàn mock | Source authority/version, raw normalized observations |
| AC03 Identity | Multi-state stable objects; restart/recreate lifetime tách biệt | Native object identity assertions, aliases/tombstones |
| AC04 USE state | Correct classes/attributes/types/links/context | Native MSystemState tests, object/link/value witnesses |
| AC05 State OCL | Nonnegative pass at 0/1, fail at -1 retained | Detailed result + state/predicate/witness |
| AC06 Transition OCL | Real paired @pre positive/negative, immutable invocation pre | Exact correlation/start-end IDs, two copied states |
| AC07 Temporal | Required type truth tables/episodes/history-gap handling | Deterministic traces + previous/current markings |
| AC08 No heuristic satisfaction | Goal based on authored OCL/binding; Task exact invocation | Negative cases active !g/disappeared intention/false pre |
| AC09 MOISE binding | Accepted role/goal/commitment state keyed exact context/scheme | Public board evidence + native links |
| AC10 Unsupported | Every excluded/lossy element carries source/reason/capability | Preflight/result coverage inventory |
| AC11 Trace/evidence | Violation traces to source and reproducible counterexample | Bundle verification + source/schema/profile hashes |
| AC12 End-to-end | Mandatory G1 live path + G2 required tests pass | Fresh test report/counts, no required skips |
| AC13 Documented profile | Supported types/operators/boundaries/quality/limits explicit | Profile manifest + docs + implementation report |
| AC14 Claims bounded | No equivalence/full checking outside admitted profile | Claim-to-evidence review and limitations register |

G1 nghiệm thu riêng cho ACHIEVE/simple domain fragment. G2 chỉ đánh complete cho **named admitted capability set**; không dùng G1 để claim NONE/MAINTAIN/SUSTAIN/Task/norms đã có. Nếu triển khai thesis temporal scope gồm P9 thì toàn F/G/H tests thuộc scope bắt buộc. Bounded/inverse claims còn phải đạt A16/A17; thiếu thì ghi PARTIAL/UNSUPPORTED, không giấu bằng chữ “extension”.

## 22. Risk Register

| Risk | Tác động | Mitigation / trigger | Owner phase |
|---|---|---|---|
| R01 CSL v4/legacy mismatch | Sai role identity/player semantics | Explicit profile; no guard bypass; same-instance tests | 0/2/14 |
| R02 Source abstraction thiếu | Bịa bounds/types/state | Metamodel supplement contract; missing feature diagnostic | 3 |
| R03 Identity/cross-authority alias | Gán role/goal/property sai object | Canonical ID + exact binding proof; ambiguity fail closed | 10/11 |
| R04 Snapshot reset/recreate | @pre/history continuity hỏng | Same-run scoped diff; pending invocation/resync tests | 1/11 |
| R05 Global-cut overclaim | Mixed observations tạo proof sai | Watermarks/topology/quality; controlled boundary assumptions | 10/11 |
| R06 Numeric type/precision | Integer constraints đổi meaning | Strict typed codec; finite/range checks; no silent Real conversion | 1/8/14 |
| R07 Undefined/exception as false | False violation/satisfaction | Detailed Value classification, aggregation/coverage tests | 1/8 |
| R08 Temporal dialect mismatch | NONE latch, Task incorrectly fulfilled | Versioned recurrence/truth tables; independent oracle | 9 |
| R09 Norms/liveness shortcut | Permission/deadline/maintenance sai | NPL observer semantics, clocks, no generic invariant | 13 |
| R10 Missed transient steps/signals | Maintain/Sustain safety proof sai | Callback journaling/gap detection; history quality remains explicit | 10–12 |
| R11 OCL dependency underapproximation | Skip affected goal/invariant | Full reevaluation baseline; conservative dependency closure | 8/19 |
| R12 Operator collapse | Sequence/parallel lost | Raw operator + ordered children + execution descriptor + inverse tests | 5/17 |
| R13 Packaging/classloader drift | Tests pass IDE, production fails | Pin fingerprints; provided boundary; packaged smoke | 0/20 |
| R14 Source tag/binary drift | Sai API/semantics oracle | Artifact hashes + javap/source match; NPL exact source verification before equivalence claim | 0/13 |
| R15 Explorer abstraction unsound | Spurious/missed counterexamples | Declared transitions/effects/frame; source concretization; bounds | 16 |
| R16 Complement/update ambiguity | Invalid round-trip source | Lens laws/admitted edit set/digest conflicts; offline output | 17 |
| R17 EDT/queue/retention load | UI freeze/loss/stale results | Metrics/bounded queues; version-pinned workers only after correctness | 19 |
| R18 Result/replay migration | Unknown enum/hash incompatibility | New detailed envelope/version; old decoder/hashes preserved | 15/20 |
| R19 Hidden lifecycle between polls | Latch inherited across unknown episode | Continuity-unproven flag; no false semantic round-trip | 11 |
| R20 User code/external I/O executes during parsing | Side effects while extracting | Existing safe official parsing boundary; no runtime effect evaluation in mapping | 0/3/6 |

## 23. Migration / Backward Compatibility

1. **Additive opt-in** CSL_OBSERVER_V1 selected at import; existing pipeline mode CODE_GROUNDED_NATIVE remains. AUTO/FULL retains materialization meaning. No fallback from invalid CSL profile into native semantics silently.
2. Default native handlers/builders remain observable-equivalent under characterization tests. Refactor kernel/dispatch only as needed; schema binding new profile không bị existing NativeProjectionPolicy.allowsClass reject do hardcoded names.
3. Frozen V2 source contract/mapping/hash resources không bị sửa để “match” external E. Resource mới có namespace/profile/version riêng. Existing VerificationSemanticLayer không thay thế CSL semantics.
4. Wire additions negotiated by capability/schema version; absent field maps UNKNOWN/UNAVAILABLE, not zero/empty truth. Old producer/new consumer admission report says unavailable capabilities. New producer/old consumer vẫn nhận fields cũ trong protocol supported path.
5. Detailed SemanticVerdict không inject vào old RuntimeVerificationResult decoder enum khi đọc bundle v1. Versioned envelope retains detailed code + conservative legacy view. Serializer tests cover all seven verdicts.
6. Source-model/profile revision change invalidates compiled constraints/operation pairing/monitor episodes unless explicit compatible migration proves preservation. Same source run resync not model revision; preserve IDs/history only with continuity.
7. Replay v1 loads with its original native profile and hashes. Replay new profile requires matching compiler/mapping/profile versions; do not relabel historical traces CSL.
8. UI reads immutable snapshots with state/marking version visible; existing org goal view retained as observation. New requirement view cannot issue direct writes or instantiate evaluator/system.
9. Source/runtime inverse stays offline. Generated JaCaMo examples/patches are artifacts in USE. No automatic edit/deploy into sibling repositories or running MAS.

## 24. Performance Plan

Correctness baseline: every accepted semantic boundary gets complete applicable OCL + all bound goal reevaluation; serialized writer; identity-preserving diffs; bounded queues/retention. Benchmarks report source-observation time, queue wait, decode/admission, mutation, snapshot copy, OCL, iStar, serialization/persistence separately.

| Axis | Initial planned levels | Measurements |
|---|---|---|
| Agents | 1, 10, 50; controlled larger runs 100/500 when feasible | Capture/cycle overhead, cross-agent interference, backlog |
| Artifacts/properties | 1/1, 10/10, 100/10; independently vary occurrences/duplicates | Mutation throughput, codec/schema rejection rates |
| Roles/contexts | 1, 10, 100 occurrences; same roles across contexts | Enactment/diff/constraint cost |
| Goals/constraints | 1, 10, 100, 1.000 authored predicates | OCL vs temporal propagation time, dependency overhead |
| Snapshot/update rate | 1, 10, 50 accepted boundaries/s plus burst | p50/p95/p99 end-to-end latency, gap/saturation rate |
| History | 100, 10.000, 100.000 events under bounded retention | Heap/GC, journal bytes/event, replay size/time |
| Operation overlap | 1, 2, 10 in-flight admitted invocations | Pre-copy memory, correlation/timeout behavior, interval evidence |

Các levels là workload configs dự kiến, không là performance guarantee. Warmup/measurement duration, JVM heap/GC, machine/JDK/dependency/profile versions và random seed phải ghi trong run manifest. Separate deterministic adapter-boundary load from full live MAS load; không lấy synthetic throughput làm full-MAS claim.

Existing RuntimePerformanceStats chỉ count/total/max/last; muốn percentiles cần bounded histogram/reservoir, ghi overhead. Tránh serialize entire state/journal mỗi tiny evidence-only event nếu không cần, nhưng chỉ thay khi trace/temporal parity chứng minh event không mang semantic boundary.

Optimization candidates theo thứ tự evidence: avoid redundant schema work → typed diff/batch → compile cache → dependency-based constraint reevaluation → pinned-state workers → retention/storage tuning. State-space optimization/POR chỉ sau P16 oracle và independence proof. Coalescing T/F/T thành T có thể xóa violation nên mặc định cấm cho temporal admitted features.

Performance acceptance: công bố measured budget theo fixture; overload có bounded backpressure/gap/resync policy và no false PASS. Không đặt arbitrary hard latency target vào DoD trước baseline measurements.

## 25. Round-trip Strategy

Profile chia ba mức rõ ràng:

| Mức | Có thể theo roadmap | Điều kiện |
|---|---|---|
| Syntactic/structural round-trip | TODO restricted get/put | Selected schema/declarations/tree+order+enum literals; complete trace/complement; normalized AST laws |
| Observational semantic round-trip | PARTIAL/research obligation | Cùng supported predicates/observations/transition assumptions; differential tests/source oracle; không suy từ AST equality |
| Full runtime/behavioral round-trip | UNSUPPORTED | Missing planner/scheduler/unification/Java/external effects; observation map không injective |

Edit ownership matrix của P17 phải nêu: class/role rename có gì update; group-role cardinality đến relation nguồn nào; scalar type/value changes admitted ra sao; OPlan AND edit có đủ original operator để biết sequence/parallel không; requirement condition edit là authored contract hay phải sửa source logic. Với source behavior chưa xác định, put trả conflict/unsupported thay vì sinh một Java/ASL body đoán.

Complement lưu original AST/source fragments và source locations/digests, semantic IDs, operator/order, unsupported features, provenance, mapping version, edit ownership. Nếu user source đã thay bên ngoài: three-way diff nếu profile hỗ trợ, còn lại CONFLICT. Không gọi “bidirectional” chỉ vì reconstruct model nhìn gần giống.

## 26. Final Definition of Done

Hệ thống được coi hoàn thành cho named supported profile khi:

- [ ] AC01–AC14 đều có fresh executable evidence và linked test/result IDs.
- [ ] G1 actual runtime vertical slice p=0→1→-1 và negative @pre 0→2 chạy; đúng native Integer/link/object identity và ACHIEVE history.
- [ ] Toàn bộ capability đã claim có source mapping/coverage/loss/unsupported policy; thiếu dữ liệu không tạo passing proof.
- [ ] Mỗi current state/result/marking cùng checkpoint version; immutable pre per invocation và replay preserve history.
- [ ] Source MOISE states, Jason execution observations, mental beliefs và iStar satisfaction tách biệt.
- [ ] Defined temporal operators/Task/compound/dependency admitted được test theo profile; không lấy use4Goal discrepancies làm default.
- [ ] Normative, bounded, inverse claims nếu có đã đạt A13/A16/A17 riêng; phần ngoài scope ghi rõ PARTIAL/UNSUPPORTED.
- [ ] Existing default native bridge launch/verification/replay/package regressions pass; reference repositories giữ nguyên.
- [ ] Có implementation-report.md khi code đã viết, liệt kê actual files/commands/counts/results/limitations; không ghi kết quả theo kế hoạch như thể đã chạy.
- [ ] Reproducibility bundle có source/profile/model/constraint hashes, trace/complement, versions, bounds/assumptions và counterexamples.

**Tại thời điểm lập kế hoạch, không checkbox nào ở trên được đánh complete cho implementation mới.** Audit/read-only evidence và sự tồn tại code nền không thay executable acceptance.

## 27. Recommended Implementation Order

1. **P0:** pin baseline/dependencies, chạy characterization suites, freeze profile/SPI/error model.
2. **P1:** triển khai F1–F8 trong một vertical slice nhỏ; chỉ dừng milestone khi official runtime observation qua native OCL/@pre/ACHIEVE có evidence. Không bắt đầu generic mapping/explorer trước G1.
3. **P2 → P3:** hoàn thiện structural schema và source rule catalog/EMF frontend/complement; giữ single source normalizer/rule catalog.
4. **P10 → P11** đồng thời với bước 3 sau freeze DTO: scoped completeness, immutable envelope, lifecycle/resync/gaps. P14 mở rộng binding theo schema P2, không tạo pipeline mới.
5. **P4 → P5:** functional observations, explicit iStar bindings và operator/order/execution descriptors.
6. **P7 và phần definition/admission P6:** mental state tách world state; Jason finite abstraction; không hứa general plans.
7. **P8 expansion → P12 expansion → P9:** rich typed conditions/invocations trước temporal/Task/refinement monitor hoàn chỉnh. P1 đã có versions tối thiểu.
8. **P15 expansion:** replay đầy đủ semantic history/markings/coverage; P18 chạy xuyên suốt mỗi bước, G2 required matrix pass.
9. **P13:** normative observation/monitor theo actual source/version, giữ maintenance limitation; bật capability khi evidence đủ.
10. **P19:** benchmark correctness baseline, sau đó optimization có parity tests.
11. **P16 và P17:** triển khai independent restricted research extensions với bounds/lens laws, có thể làm song song sau prerequisites, không biến thành gate ẩn của G1.
12. **P20:** packaged/live/replay acceptance + claim audit; xuất kết quả theo capability thực sự hoàn tất.

Quy tắc giao việc: mỗi change chỉ nhận named profile/capability và matrix rows cụ thể; preserve existing user work; không sửa jacamo/use4Goal; không tạo branch theo yêu cầu hiện tại; không thêm norm/state-machine/behavior semantics chỉ từ tên. Khi chưa đủ evidence, cập nhật chính xác PARTIAL/BLOCKED/UNSUPPORTED và lý do thay vì đổi semantics.

## Phụ lục A. Chỉ mục source đã xác minh

Các link dưới trỏ tới file thật; line chỉ là entry point của class/method tại baseline, không thay thế đọc implementation. Nguồn report chứa chỉ mục chi tiết 120 references cho dependency source/formal definitions. Web NPL references chỉ dùng bổ sung provenance/structure; runtime normative claims phải pin artifact/version như P0/P13.

| Mã | File / entry point | API hoặc nội dung được dùng |
|---|---|---|
| B01 | [OfficialProjectAdapter.java:40][B01] | adapt(JaCaMoProject,Path) |
| B02 | [OfficialJasonAdapter.java:38][B02] | adapt/parse official AST |
| B03 | [OfficialMoiseAdapter.java:52][B03] | load/capture official OS; copyOrganization |
| B04 | [OfficialCartagoAdapter.java:43][B04] | capture official environment/controller/ArtifactInfo |
| B05 | [BridgeAgArch.java:21][B05] | reasoningCycleFinished / action events |
| B06 | [JasonSnapshotSource.java:31][B06] | capture/topologyFingerprint/completeness |
| B07 | [CartagoSnapshotSource.java:37][B07] | capture / ICartagoLogger callbacks |
| B08 | [MoiseBoardSnapshotSource.java:24][B08] | capture/poll/completeness; players/commitments/goalStates |
| B09 | [MoiseGoalObservation.java:10][B09] | capture/decode goalState/goalArgument |
| B10 | [NplBoardSnapshotSource.java:28][B10] | capture/listeners/id; evidence-only norm state |
| B11 | [SnapshotCoordinator.java:24][B11] | capture; watermarks/topology/buffered replay |
| B12 | [ReasoningCycleGate.java:10][B12] | admit/pause/boundary/ACK/epoch |
| B13 | [BridgeEntityId.java:10][B13] | canonical/parse; authority/dimension/kind/scope/localId/incarnation |
| B14 | [RuntimeSnapshot.java:7][B14] | snapshot metadata, facts, sourceCompleteness |
| B15 | [RuntimeEvent.java:7][B15] | session/generation/source sequence/correlation/watermark |
| G01 | [CslModel.java:8][G01] | isRevisedCore/requireLegacyRuntime |
| G02 | [Csl2UseTranslator.java:32][G02] | translate; legacy play chain; omitted source invariants |
| G03 | [GoalMarking.java:6][G03] | update/status; activation episode and defaults |
| G04 | [TaskMarking.java:4][G04] | preSeen/postSeen; cannot represent violated Task |
| G05 | [CslOclTransitionEvaluator.java:13][G05] | scalar self-path @pre substitution backend |
| G06 | [CslIStarSymbolicSemantics.java:31][G06] | leafGoal/leafTask; symbolic NONE/ACHIEVE grouping |
| G07 | [csl.ecore:11][G07] | core v4 M2; role same-instance; annotations/features mismatch |
| G08 | [IStarBuildingVisitor.java:12][G08] | visitModel/buildRel; grammar-to-AST feature gaps |
| G09 | [NativeOclEvaluator.java:12][G09] | limited native OCL backend |
| G10 | [CslSemanticValidator.java:13][G10] | validateCore; same-kind inheritance/entity-centered associations |
| G11 | [istar.ecore:90][G11] | intentional M2/condition/temporal definitions |
| G12 | [Goal.java:4][G12] | Java Goal fields id/type/condition |
| G13 | [CSL.g4:1][G13] | revised core syntax; M1 vs M0 separation |
| G14 | [IStar.g4:1][G14] | conditions/activation/relations syntax; not runtime proof |
| J01 | [build.gradle:7][J01] | JaCaMo 1.3.1 build/dependencies |
| J02 | [JaCaMoAgArch.java:31][J02] | initial role/focus requests become Jason achievement events |
| J03 | [Goal.java:114][J03] | source types/arguments/min/TTF/predecessors |
| J04 | [os2nopl.java:346][J04] | NPL enabling/goal obligation generator; maintenance limitation |
| J05 | [Moise.java:36][J05] | actual ORA4MAS boards startup |
| J06 | [Scheme.java:259][J06] | source organizational satisfaction/cache/commitment rules |
| J07 | [Agent.java:540][J07] | initial goals consumed; BB/PL/query semantics |
| J08 | [Artifact.java:32][J08] | operation lock/await/observable commit behavior |
| J09 | [Role.java:27][J09] | global role definitions/defaults/superRoles |
| T01 | [LiveCartagoNativeVerificationTest.java:27][T01] | Existing regression source; not executed in this planning turn |
| T02 | [NativeOperationCheckpointTest.java:15][T02] | Existing regression source; not executed in this planning turn |
| T03 | [NativeRuntimeBridgeCoverageTest.java:17][T03] | Existing regression source; not executed in this planning turn |
| T04 | [SelectiveVerificationProjectionTest.java:10][T04] | Existing regression source; not executed in this planning turn |
| T05 | [NativeGenericStepReplayTest.java:20][T05] | Existing regression source; not executed in this planning turn |
| T06 | [ReconnectSnapshotTest.java:7][T06] | Existing regression source; not executed in this planning turn |
| T07 | [VerificationSnapshotContractTest.java:10][T07] | Existing regression source; not executed in this planning turn |
| T08 | [NativeProjectionAuditTest.java:11][T08] | Existing regression source; not executed in this planning turn |
| U01 | [pom.xml:2][U01] | reactor modules, USE/JDK version |
| U02 | [CodeGroundedNativePipeline.java:15][U02] | build; model/system identity; export hash parity |
| U03 | [NativeUseModelBuilder.java:15][U03] | build; schema/domain/operation/constraint projection |
| U04 | [NativeProjectionPolicy.java:8][U04] | VERSION/BASE_CLASSES/decide/allowsClass |
| U05 | [MoiseDomainProjection.java:20][U05] | install/validate/materializeFunctional; deferred advanced roles/norms |
| U06 | [NativeUseStateBuilder.java:16][U06] | build; native state/bindings |
| U07 | [NativeRuntimeMutationEngine.java:40][U07] | apply/savepoint/rollback/upsert/delete/reconcile |
| U08 | [RuntimeVerificationCoordinator.java:28][U08] | transaction/read/finish/freeze/acceptedSnapshot |
| U09 | [ExternalOclConstraintService.java:43][U09] | installSource/evaluate/requiredClasses; undefined categorization |
| U10 | [NativeOperationCheckpoints.java:15][U10] | observe; exact correlated operation pre/post |
| U11 | [NativeRuntimeProjector.java:17][U11] | applySnapshot/apply; ledger/revision/sequence/resync |
| U12 | [NativeObjectBindings.java:9][U12] | exact semantic object aliases/metadata |
| U13 | [NativeRuntimeReplay.java:25][U13] | exportBundle/replay; hashes/versioned state replay |
| U14 | [RuntimeEventJournal.java:15][U14] | append/gap/history/hash chain |
| U15 | [RuntimeCheckpointStore.java:10][U15] | store/checkpoint evidence |
| U16 | [GoalViewSnapshot.java:11][U16] | read organizational goal observations; no iStar marking evaluator |
| U17 | [DomainProjection.java:24][U17] | propertyType/propertyValue; current numeric Real carrier |
| U18 | [ActiveBaseline.java:9][U18] | active/fromCheckout/packaged; frozen V2 baseline |
| U19 | [DefaultVerificationService.java:24][U19] | beginOperation/completeOperation/evaluateCondition/invariant |
| U20 | [UseModelApi.java:72][U20] | createClass/attribute/association/invariant/prePost; typed compiler context |
| U21 | [UseSystemApi.java:53][U21] | createObject/link/setAttribute/delete/native API |
| U22 | [MSystemState.java:60][U22] | copy constructor at 134; native objects/link sets; extension API |
| U23 | [Evaluator.java:36][U23] | eval(expression,preState,postState,bindings) |
| U24 | [JaCaMoSemanticModel.java:16][U24] | normalized source/provenance indexes |
| U25 | [VerificationSemanticLayer.java:11][U25] | frozen-plan inheritance/multiplicity decisions |
| U26 | [JaCaMoWorkbenchPanel.java:24][U26] | facade-based UI/cached runtime status |
| U27 | [pom.xml:2][U27] | compile/test/provided dependencies; packaging/Failsafe |
| U28 | [RuntimeConstraintPolicy.java:8][U28] | SOFT/REPORT_ONLY, HARD/PAUSE_ON_FAIL policy |
| U29 | [VerificationSnapshot.java:12][U29] | versioned immutable portable state image |
| U30 | [RuntimeVerificationResult.java:12][U30] | four-outcome native result/hash codec |
| U31 | [NativeProjectionMode.java:6][U31] | AUTO/FULL; no separate production pipeline |
| U32 | [NativeProjectionProfile.java:6][U32] | forMode/statusFor; AUTO/FULL materialization |
| U33 | [MObject.java:38][U33] | object identity/value/state contract |
| U34 | [OperationCheck.java:7][U34] | immutable pre-state ownership contract |
| U35 | [RuntimePerformanceStats.java:6][U35] | record/snapshot timings count/total/max/last |
| U36 | [JacamoSpecificationModel.java:18][U36] | immutable official source IR/reference validation |
| U37 | [CodeGroundedTraceIndex.java:11][U37] | source-target trace index |
| U38 | [DefaultJaCaMoFacade.java:49][U38] | native pipeline/import/bridge runtime/view composition |
| N01 | [npl-0.6.1.jar][N01] | Đã đọc bằng javap: NPLInterpreter getters/verifyNorms, NormInstance modality/aim/maintenance/deadline, StateTransitions updateActive/updateInactive/updateDeadline; binary SHA-256 956818edfe9aeff7c4fde35bf1dd276cd71e1da62daf77a2a15ab08f87903332 |
| N02 | [Official NPL StateTransitions source][N02] | Upstream source tham khảo; chưa coi master là source pin của artifact 0.6.1 |
| N03 | [Official NPL NPLInterpreter source][N03] | Upstream source tham khảo về lifecycle API/verification; artifact hiện hành phải kiểm độc lập |

Các file source dependency ở thư mục tạm có provenance/version trong report; nếu thư mục bị dọn, phục hồi source đúng artifact/tag và xác minh hashes trước khi dùng lại. Không lấy file source mới nhất thay bằng chứng baseline.

### Reference links

[R]: <C:/Users/NTNghia/OneDrive/Desktop/report.md>
[E]: <C:/Users/NTNghia/OneDrive/Desktop/Thesis/UML/jacamo_v2.ecore>
[P]: <C:/Users/NTNghia/OneDrive/Desktop/Thesis/CSL/On Checking the Conformance between iStar and BPMN_261012_173041_1.pdf>
[B01]: <D:/_CODE_BANK/Project_/08_Thesis/use/jacamo-bridge-jacamo/src/main/java/org/jacamo/bridge/adapter/OfficialProjectAdapter.java:40>
[B02]: <D:/_CODE_BANK/Project_/08_Thesis/use/jacamo-bridge-jacamo/src/main/java/org/jacamo/bridge/adapter/OfficialJasonAdapter.java:38>
[B03]: <D:/_CODE_BANK/Project_/08_Thesis/use/jacamo-bridge-jacamo/src/main/java/org/jacamo/bridge/adapter/OfficialMoiseAdapter.java:52>
[B04]: <D:/_CODE_BANK/Project_/08_Thesis/use/jacamo-bridge-jacamo/src/main/java/org/jacamo/bridge/adapter/OfficialCartagoAdapter.java:43>
[B05]: <D:/_CODE_BANK/Project_/08_Thesis/use/jacamo-bridge-jacamo/src/main/java/org/jacamo/bridge/adapter/BridgeAgArch.java:21>
[B06]: <D:/_CODE_BANK/Project_/08_Thesis/use/jacamo-bridge-jacamo/src/main/java/org/jacamo/bridge/adapter/JasonSnapshotSource.java:31>
[B07]: <D:/_CODE_BANK/Project_/08_Thesis/use/jacamo-bridge-jacamo/src/main/java/org/jacamo/bridge/adapter/CartagoSnapshotSource.java:37>
[B08]: <D:/_CODE_BANK/Project_/08_Thesis/use/jacamo-bridge-jacamo/src/main/java/org/jacamo/bridge/adapter/MoiseBoardSnapshotSource.java:24>
[B09]: <D:/_CODE_BANK/Project_/08_Thesis/use/jacamo-bridge-jacamo/src/main/java/org/jacamo/bridge/adapter/MoiseGoalObservation.java:10>
[B10]: <D:/_CODE_BANK/Project_/08_Thesis/use/jacamo-bridge-jacamo/src/main/java/org/jacamo/bridge/adapter/NplBoardSnapshotSource.java:28>
[B11]: <D:/_CODE_BANK/Project_/08_Thesis/use/jacamo-bridge-jacamo/src/main/java/org/jacamo/bridge/adapter/SnapshotCoordinator.java:24>
[B12]: <D:/_CODE_BANK/Project_/08_Thesis/use/jacamo-bridge-jacamo/src/main/java/org/jacamo/bridge/adapter/ReasoningCycleGate.java:10>
[B13]: <D:/_CODE_BANK/Project_/08_Thesis/use/jacamo-bridge-contract/src/main/java/org/jacamo/bridge/contract/BridgeEntityId.java:10>
[B14]: <D:/_CODE_BANK/Project_/08_Thesis/use/jacamo-bridge-contract/src/main/java/org/jacamo/bridge/contract/RuntimeSnapshot.java:7>
[B15]: <D:/_CODE_BANK/Project_/08_Thesis/use/jacamo-bridge-contract/src/main/java/org/jacamo/bridge/contract/RuntimeEvent.java:7>
[G01]: <D:/_CODE_BANK/Project_/08_Thesis/use4Goal/goal/src/main/java/org/vnu/sme/goal/dsl/csl/mm/CslModel.java:8>
[G02]: <D:/_CODE_BANK/Project_/08_Thesis/use4Goal/goal/src/main/java/org/vnu/sme/goal/translate/csl2use/Csl2UseTranslator.java:32>
[G03]: <D:/_CODE_BANK/Project_/08_Thesis/use4Goal/goal/src/main/java/org/vnu/sme/goal/verify/conformance/semantics/GoalMarking.java:6>
[G04]: <D:/_CODE_BANK/Project_/08_Thesis/use4Goal/goal/src/main/java/org/vnu/sme/goal/verify/conformance/semantics/TaskMarking.java:4>
[G05]: <D:/_CODE_BANK/Project_/08_Thesis/use4Goal/goal/src/main/java/org/vnu/sme/goal/verify/cslstate/CslOclTransitionEvaluator.java:13>
[G06]: <D:/_CODE_BANK/Project_/08_Thesis/use4Goal/goal/src/main/java/org/vnu/sme/goal/verify/cslstate/CslIStarSymbolicSemantics.java:31>
[G07]: <D:/_CODE_BANK/Project_/08_Thesis/use4Goal/docs/model/csl.ecore:11>
[G08]: <D:/_CODE_BANK/Project_/08_Thesis/use4Goal/goal/src/main/java/org/vnu/sme/goal/dsl/istar/parser/IStarBuildingVisitor.java:12>
[G09]: <D:/_CODE_BANK/Project_/08_Thesis/use4Goal/goal/src/main/java/org/vnu/sme/goal/trace/cslTrace/nativecsl/NativeOclEvaluator.java:12>
[G10]: <D:/_CODE_BANK/Project_/08_Thesis/use4Goal/goal/src/main/java/org/vnu/sme/goal/dsl/csl/mm/CslSemanticValidator.java:13>
[G11]: <D:/_CODE_BANK/Project_/08_Thesis/use4Goal/docs/model/istar.ecore:90>
[G12]: <D:/_CODE_BANK/Project_/08_Thesis/use4Goal/goal/src/main/java/org/vnu/sme/goal/dsl/istar/mm/Goal.java:4>
[G13]: <D:/_CODE_BANK/Project_/08_Thesis/use4Goal/goal/src/main/resources/grammars/CSL.g4:1>
[G14]: <D:/_CODE_BANK/Project_/08_Thesis/use4Goal/goal/src/main/resources/grammars/IStar.g4:1>
[J01]: <D:/_CODE_BANK/Project_/08_Thesis/jacamo/build.gradle:7>
[J02]: <D:/_CODE_BANK/Project_/08_Thesis/jacamo/src/main/java/jacamo/infra/JaCaMoAgArch.java:31>
[J03]: <C:/Users/NTNghia/AppData/Local/Temp/codex-use-jacamo-audit/moise-v1.1/src/main/java/moise/os/fs/Goal.java:114>
[J04]: <C:/Users/NTNghia/AppData/Local/Temp/codex-use-jacamo-audit/moise-v1.1/src/main/java/ora4mas/nopl/tools/os2nopl.java:346>
[J05]: <D:/_CODE_BANK/Project_/08_Thesis/jacamo/src/main/java/jacamo/platform/Moise.java:36>
[J06]: <C:/Users/NTNghia/AppData/Local/Temp/codex-use-jacamo-audit/moise-v1.1/src/main/java/ora4mas/nopl/oe/Scheme.java:259>
[J07]: <C:/Users/NTNghia/AppData/Local/Temp/codex-use-jacamo-audit/jason-3.3.2-src/jason/asSemantics/Agent.java:540>
[J08]: <C:/Users/NTNghia/AppData/Local/Temp/codex-use-jacamo-audit/cartago-v3.1/src/main/java/cartago/Artifact.java:32>
[J09]: <C:/Users/NTNghia/AppData/Local/Temp/codex-use-jacamo-audit/moise-v1.1/src/main/java/moise/os/ss/Role.java:27>
[T01]: <D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/test/java/org/tzi/use/plugins/jacamo/codegrounded/LiveCartagoNativeVerificationTest.java:27>
[T02]: <D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/test/java/org/tzi/use/plugins/jacamo/codegrounded/NativeOperationCheckpointTest.java:15>
[T03]: <D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/test/java/org/tzi/use/plugins/jacamo/codegrounded/NativeRuntimeBridgeCoverageTest.java:17>
[T04]: <D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/test/java/org/tzi/use/plugins/jacamo/codegrounded/SelectiveVerificationProjectionTest.java:10>
[T05]: <D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/test/java/org/tzi/use/plugins/jacamo/codegrounded/NativeGenericStepReplayTest.java:20>
[T06]: <D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/test/java/org/tzi/use/plugins/jacamo/codegrounded/ReconnectSnapshotTest.java:7>
[T07]: <D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/test/java/org/tzi/use/plugins/jacamo/codegrounded/VerificationSnapshotContractTest.java:10>
[T08]: <D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/test/java/org/tzi/use/plugins/jacamo/codegrounded/NativeProjectionAuditTest.java:11>
[U01]: <D:/_CODE_BANK/Project_/08_Thesis/use/pom.xml:2>
[U02]: <D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/codegrounded/CodeGroundedNativePipeline.java:15>
[U03]: <D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/codegrounded/use/NativeUseModelBuilder.java:15>
[U04]: <D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/codegrounded/use/NativeProjectionPolicy.java:8>
[U05]: <D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/codegrounded/use/MoiseDomainProjection.java:20>
[U06]: <D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/codegrounded/use/NativeUseStateBuilder.java:16>
[U07]: <D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/codegrounded/runtime/NativeRuntimeMutationEngine.java:40>
[U08]: <D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/codegrounded/runtime/RuntimeVerificationCoordinator.java:28>
[U09]: <D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/codegrounded/constraint/ExternalOclConstraintService.java:43>
[U10]: <D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/codegrounded/runtime/NativeOperationCheckpoints.java:15>
[U11]: <D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/codegrounded/runtime/NativeRuntimeProjector.java:17>
[U12]: <D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/codegrounded/trace/NativeObjectBindings.java:9>
[U13]: <D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/codegrounded/runtime/NativeRuntimeReplay.java:25>
[U14]: <D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/codegrounded/runtime/RuntimeEventJournal.java:15>
[U15]: <D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/codegrounded/runtime/RuntimeCheckpointStore.java:10>
[U16]: <D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/codegrounded/runtime/GoalViewSnapshot.java:11>
[U17]: <D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/codegrounded/use/DomainProjection.java:24>
[U18]: <D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/mapping/ActiveBaseline.java:9>
[U19]: <D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/verification/DefaultVerificationService.java:24>
[U20]: <D:/_CODE_BANK/Project_/08_Thesis/use/use-core/src/main/java/org/tzi/use/api/UseModelApi.java:72>
[U21]: <D:/_CODE_BANK/Project_/08_Thesis/use/use-core/src/main/java/org/tzi/use/api/UseSystemApi.java:53>
[U22]: <D:/_CODE_BANK/Project_/08_Thesis/use/use-core/src/main/java/org/tzi/use/uml/sys/MSystemState.java:60>
[U23]: <D:/_CODE_BANK/Project_/08_Thesis/use/use-core/src/main/java/org/tzi/use/uml/ocl/expr/Evaluator.java:36>
[U24]: <D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantic/JaCaMoSemanticModel.java:16>
[U25]: <D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/verification/profile/VerificationSemanticLayer.java:11>
[U26]: <D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/ui/JaCaMoWorkbenchPanel.java:24>
[U27]: <D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/pom.xml:2>
[U28]: <D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/codegrounded/constraint/RuntimeConstraintPolicy.java:8>
[U29]: <D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/codegrounded/runtime/VerificationSnapshot.java:12>
[U30]: <D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/codegrounded/runtime/RuntimeVerificationResult.java:12>
[U31]: <D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/codegrounded/use/NativeProjectionMode.java:6>
[U32]: <D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/codegrounded/use/NativeProjectionProfile.java:6>
[U33]: <D:/_CODE_BANK/Project_/08_Thesis/use/use-core/src/main/java/org/tzi/use/uml/sys/MObject.java:38>
[U34]: <D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/verification/OperationCheck.java:7>
[U35]: <D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/codegrounded/runtime/RuntimePerformanceStats.java:6>
[U36]: <D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/codegrounded/model/JacamoSpecificationModel.java:18>
[U37]: <D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/codegrounded/trace/CodeGroundedTraceIndex.java:11>
[U38]: <D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/DefaultJaCaMoFacade.java:49>
[N01]: <C:/Users/NTNghia/.m2/repository/org/jacamo/npl/0.6.1/npl-0.6.1.jar>
[N02]: https://raw.githubusercontent.com/moise-lang/npl/master/src/main/java/npl/StateTransitions.java
[N03]: https://raw.githubusercontent.com/moise-lang/npl/master/src/main/java/npl/NPLInterpreter.java

## Phụ lục B. Kiểm tra consistency của chính kế hoạch

Đã kiểm tra bằng script chỉ đọc và inspection:

- Có đủ 27 mục chính theo cấu trúc yêu cầu, Phase 0–20 liên tục; mỗi phase có đúng 18 trường.
- Có source-to-target matrix cho toàn bộ 21 EClass/7 EEnum, file/class change matrix, dependency graph, test matrix, acceptance/risk/migration/performance/round-trip/DoD.
- 84 local source/test entry points trỏ tới file tồn tại và line hợp lệ; thêm một local NPL binary và hai official upstream source references. Các reference definitions được kiểm tra không thiếu.
- Existing full paths trong change matrix tồn tại; hai full paths chưa tồn tại là file M2/benchmark **proposed**, đã gắn nhãn. Prefix SP/ST và các tên class mới đều là proposed.
- P1 có nền F1–F8 nên không chờ vòng vào P8/P10/P11/P12/P14/P15; các phase này mở rộng một implementation. Profile mới dùng một native pipeline, không thêm use4Goal MSystem.
- Git status USE chỉ có implementation-plan.md mới; không Java/XML/Ecore/pom/test thay đổi. Branch vẫn main, không tạo commit/branch.
- Git HEAD/status của jacamo/use4Goal khớp baseline; hashes bốn file có thay đổi từ trước không đổi. Report/PDF/Ecore đầu vào cũng giữ nguyên SHA-256.
- Không chạy Maven build/test/benchmark trong lượt này. Kết quả JShell trong report là evidence của lượt nghiên cứu trước, không được nâng thành tests của implementation mới.

Các file có thay đổi sẵn trong repository chỉ đọc được bảo toàn:

| Repository / file | SHA-256 trước và sau không đổi |
|---|---|
| jacamo: examples/auction/.jcm-deps.gradle | 3b7706de71f0362c2fd5e1da6a90f1d918f4ef1426b5dadbd6ca67a38eb3f050 |
| jacamo: examples/auction/auction.jcm | 1b7fd99e1ceea67381b2957908bd41d3387aa7b4b98fadc26abf588fcee98e2a |
| use4Goal: goal/src/main/resources/examples/Classroom/classroom.soil | 10d4d9acc89e42c6b7fe7a821b5a42b8404173155e12e0412de352e652585001 |
| use4Goal: goal/src/main/resources/examples/Classroom/classroom.use | bb857fce61a9a8a11e207ae13dc198387242af7d3c759205090c831d2a81aaa1 |

Kế hoạch kết thúc tại đây. Chưa implement code, chưa tạo implementation-report.md; khi coding bắt đầu phải dùng profile/acceptance/test matrix trong file này để báo tiến độ thực tế.
