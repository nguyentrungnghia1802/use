# Implementation report — JaCaMo → CSL / iStar / OCL → USE

Ngày nghiệm thu: 16/10/2026. Repository triển khai duy nhất: D:/_CODE_BANK/Project_/08_Thesis/use. Java 21; không tạo branch/commit. Báo cáo này mô tả code và các lần chạy thực tế; các bảng thiết kế gốc trong implementation-plan.md giữ vai trò baseline lịch sử.

## 1. Kết quả và phạm vi tuyên bố

Đã triển khai đường projection/monitoring vào native USE hiện có, thêm CSL/iStar/OCL theo profile opt-in có version, cùng trace/complement, runtime evidence, replay, bộ khám phá hữu hạn và inverse cấu trúc giới hạn. Mặc định vẫn là NATIVE_CURRENT. Không có interpreter JaCaMo thứ hai và không có USE system cạnh tranh với runtime đang kết nối.

Các tên “supported” trong báo cáo luôn kèm fragment và điều kiện coverage. State được kiểm tra là một vector các observation cut đã xác nhận theo từng nguồn. Không có bằng chứng global atomicity của MAS, không suy liveness vô hạn từ trace hữu hạn, không claim tương đương toàn bộ NPL hay semantic round-trip JaCaMo.

Hai extension được thực hiện độc lập: FINITE_FIXED_POPULATION_V1 nhận quan hệ chuyển trạng thái hữu hạn do người dùng cung cấp; ECORE_STRUCTURAL_LENS_V1 chỉ sửa fragment cấu trúc của model Ecore đã giữ complement. Chúng không tổng hợp successor Java/ASL hoặc executable body từ OCL.

## 2. Source of truth, baseline và quyền ghi

Yêu cầu implementation đầy đủ trong attachment faf5f843-3927-46d3-b7f1-98370c4aa873 thay thế yêu cầu nghiên cứu chỉ đọc ban đầu. Đã đọc report và master plan; source hiện tại được ưu tiên khi có discrepancy.

| Nguồn | Baseline / kiểm tra |
|---|---|
| USE | main, HEAD 00b27a1f5385720a135e2adbc65c8523ffa4e9bd; baseline có implementation-plan.md chưa tracked; không reset thay đổi |
| JaCaMo | HEAD 04fc08df259d94045f6cfc95637d56faa1cb33b2; hai file auction có thay đổi sẵn, được giữ nguyên |
| use4Goal | HEAD 6f3e73801239fd8bfb94ca8d04ba531b903c0ac9; hai file Classroom có thay đổi sẵn, được giữ nguyên |
| Desktop report.md | SHA-256 dd7c2a70514b8fbdac8945f3e1270fe44c69a623f6161284cf8261349941da82 |
| Ecore được cung cấp | SHA-256 4ae51638a078f0a933982844063993084f17d420694ac8a868d95b9df063c35c; bản bundled giữ nguyên 21 EClass / 7 EEnum |

Các thư mục target chứa log, generated model, benchmark/replay và fixture staging. Package phase stage plugin vào use-gui/lib/plugins, vẫn nằm trong USE. Không build hoặc sửa hai repository tham chiếu.

## 3. Kiến trúc cuối và điểm vào code

~~~mermaid
flowchart TD
  S[Official JCM / ASL / MOISE adapters] --> N[NormalizedSourceModel + decisions]
  E[Dynamic Ecore loader] --> N
  N --> B[MappingBundle + TraceComplement]
  B --> P[CodeGroundedNativePipeline + CslUseBinding]
  P --> M[One native MModel / MSystem]
  J[Jason cycle / CArtAgO logger / MOISE boards / NPL listeners] --> C[SnapshotCoordinator: local cuts + source watermarks]
  C --> R[NativeRuntimeProjector]
  R --> W[RuntimeVerificationCoordinator: serialized writer]
  W --> M
  M --> O[Native OCL state / exact invocation pair]
  O --> T[TemporalGoalMonitor + explicit bindings]
  T --> V[Seven-valued evidence / immutable snapshot / journal]
  V --> U[Read-only evidence UI]
  V --> Y[Portable replay + mapping complement]
~~~

- Static: CodeGroundedNativePipeline.build(snapshot, mode, configuration) hoặc buildSemanticBundle(snapshot, mode, bundle). Configuration opt-in qua use.jacamo.semantics.profile và use.jacamo.semantics.config; thiếu/mismatch config bị từ chối. Schema mới được cài trên cùng model builder; export/recompile vẫn sử dụng native USE.
- Runtime: NativeRuntimeProjector.applySnapshot/apply, NativeRuntimeMutationEngine và RuntimeVerificationCoordinator.transaction/read/finish dùng writer barrier của USE EDT. Identity index, aliases, tombstone, metadata và semantic monitor cùng tham gia savepoint/rollback cho mutation bị từ chối.
- Verification: SemanticRuntimeVerifier là participant của coordinator, không sở hữu MSystem. SemanticOclCompiler, SemanticConstraintRegistry, SemanticInvariantEvaluator và OclEvaluationAdapter dùng compiler/evaluator của USE. RequirementBindingResolver kiểm tra exact player/role/context/self/episode.
- View: ProjectionBindingRegistry cung cấp inventory immutable; ProjectionReadView không trao mutable USE object cho consumer. SemanticEvidencePanel hiển thị verdict, lifecycle, raw/evaluated marking, episode, quality/gap và witnesses từ cùng snapshot.
- Replay: NativeRuntimeReplay giữ đường replay v2 hiện hành; mapping.json là supplement optional có hash, phải khớp annotation trên model. Replay cũ không có mapping supplement vẫn dùng được.
- Explorer là evaluator offline riêng biệt, chỉ mượn immutable model schema để tạo isolated offline state; không đọc/ghi connected live MSystem. SourceLens trả artifact bytes/patch plan trong bộ nhớ; không ghi reference source.

## 4. Mapping cuối: model, state và behavior

| JaCaMo concept | Target và mức hỗ trợ | Điều không được suy diễn |
|---|---|---|
| Organization definition | OS provenance; derive schema context/role từ structural specification; preserve source assets | Organization không tự thành một runtime OrgCtx hay generic Entity |
| Group definition / board | M1 OrgCtx type / M0 context occurrence theo board UUID và OS/group source ID | Declaration không tự tạo runtime group; context ownership không có nghĩa composition destruction |
| Role definition / accepted player tuple | M1 role classifier / M0 role occurrence + Plays + InContext; cardinalities, selected symmetric compatibility, single inheritance | Configured request không phải accepted enactment; không tạo thêm occurrence cho mỗi parent |
| Agent | AtomicAgent/RuntimePlayer observer, exact runtime incarnation; declaration chỉ là provenance khi nhiều instances | Agent không kế thừa CSL domain Entity; role binding không chỉ dựa display name |
| Link | Explicitly authored typed association hoặc enactment relation do adapter xác nhận | Source MOISE scoped/directional Link không mặc nhiên là domain association |
| Artifact / Property | Explicit CSL Entity classifier / typed scalar attribute; raw property occurrence evidence theo full identity | Không tự đoán String/object codec; duplicate-name scalar, tuple arity, null/object và type drift phải được từ chối/unsupported |
| Belief | Mental helper thuộc agent; namespace/negation/annotation/rule/term evidence; admitted ground stored-query fragment sang OCL | Belief không viết vào artifact/world slot; absence không là explicit negation; không general Jason entailment |
| AGoal / Event / Intention | Separate execution/mental observations; event/means occurrence tokens; source complement | Active !g, intention biến mất hoặc action success không tự là iStar satisfaction |
| OGoal | Source goal definition và SchemeBoard observer waiting/enabled/satisfied/arguments/committed/achieved; authored iStar binding + OCL | MOISE reported satisfaction và intentional marking là hai loại bằng chứng |
| OPlan | ExecutionDescriptor giữ operator/order/children/rate/prerequisites/source oracle; AND/OR outcome riêng với transition checks | SEQUENCE/PARALLEL đều AND outcome nhưng không tương đương behavior; CHOICE không bị đổi thành XOR |
| Mission / Scheme | Functional observer helpers, exact scheme instance UUID, commitments và goal states; preference IDs trong complement | Không map thành Entity chỉ để chứa metadata; preference không tự là assignment scheduler |
| Jason Plan / context | Explicit TaskBinding + JasonAbstractionPolicy chỉ chọn exact program/plan/body/context/target contract | Không tự dịch toàn ASL plan thành Task hoặc biến context thành arbitrary OCL |
| Operation / Action | Reflected declared operation interface + observed exact invocation; typed pre/post, params/result khi source có bằng chứng | Contract không tạo executable implementation; logger không chứng minh Java nonvoid result hoặc Jason ActionExec↔OpId causation |
| Norm | NPL activation/lifecycle observer, reported state và lifecycle consistency riêng; independent derivation INCONCLUSIVE | Norm không trở thành invariant; inactive không là violation; permission chưa dùng không là obligation failure |
| Workspace / Environment | Observer helpers + exact topology/identity metadata, không trộn domain schema | Global MAS state/clock không được giả định từ topology |
| Signal | Event/provenance evidence khi source cung cấp | Không suy persistent state effect hoặc causal Task invocation từ signal name |

MappingRuleCatalog kiểm kê toàn bộ 21 class và 7 enum. Normalizer buộc mỗi feature/reference có decision, không im lặng bỏ source data; source raw assets/complement được giữ và kiểm hash. Ecore thiếu các semantics runtime như intention continuation, temporal clock, concrete scalar property values/types và operation Java effects; không bù bằng suy đoán.

## 5. Semantics profile và runtime correctness

CSL_OBSERVER_V1 có context/atomic player/role occurrence/enactment, scalar domain, invariant, invocation @pre và ACHIEVE. V2 bổ sung structural policy, NONE/MAINTAIN/SUSTAIN/Task, AND/OR/dependency, functional, mental/execution observation và reported NPL lifecycle. Manifest đóng gói: org/tzi/use/plugins/jacamo/semantics/capabilities.json; parity với SemanticsProfile được test.

M2 là bundled Jacamo source Ecore và CSLMonitor.ecore có observer subpackages riêng. M1 là CslSchema + RequirementModel + contracts/bindings. M0 là native objects/links/values. Runtime helpers không bị gắn nhãn domain Entity. M2 helper attributes V2 được đối chiếu với model thực; compiled association inventory giữ ordered/n-ary ends và qualifiers.

OCL lấy semantics của host compiler/evaluator; không viết boolean interpreter thay USE. Registry có authored/generated invariants và native backend conditions. Kết quả gồm PASS, FAIL, UNDEFINED, ERROR, UNSUPPORTED, SKIPPED, INCONCLUSIVE; legacy view là projection có kiểm soát. Quantifier/negation/navigation/allInstances dùng AST dependency coverage; partial population không chứng minh universal statement bằng domain đang thiếu. Unsupported codec khác FAIL của predicate.

Temporal semantics trên episode đã bind:

| Type | Trace đầy đủ | Marking |
|---|---|---|
| NONE | T,F | T,F theo hiện tại |
| ACHIEVE | F,T,F | U,T,T, lưu firstSatisfied |
| MAINTAIN | T,F,T | T,F,F, lưu firstViolated |
| SUSTAIN | F,T,F,T | U,T,F,F |
| Task | Exact invocation pre/post | Current invocation conformance, không latch thành công quá khứ |

AND/OR dùng evaluated child markings và ba giá trị; parent còn áp dụng temporal operator của chính nó. Dependency cần exact bound occurrence. Missing/undefined/error không tự thành false hay satisfaction. Binding/episode/incarnation đổi làm tách history. Gap rồi resync chỉ phục hồi current coverage, không xóa thiếu hụt lịch sử MAINTAIN/SUSTAIN. Trace dừng sớm không tạo bằng chứng unbounded liveness violation.

@pre lấy immutable pre-state theo exact invocation: source/run/generation/revision, artifact UUID, operation signature, correlation và observed prefix. Overlap A/B giữ hai pre-state; suspend/resume giữ pre ban đầu; duplicate/capacity/timeout/failure có reason và witness. Không ghép bằng operation name. CArtAgO boundary là ICARTAGOLOGGER_OBSERVED_PREFIX, không claim scheduler-wide atomic interval hay causation giữa interpreter và artifact.

Identity bao gồm authority/dimension/kind/scope/local ID/incarnation. Object ổn định trong cùng incarnation; recreate/restart tạo object/lifetime mới và từ chối late event của incarnation cũ. Partial omission giữ object/property; authoritative complete scope mới được reconcile deletion. Role leave/rejoin tạo occurrence mới nếu được quan sát; polling không nhìn thấy khoảng rời role phải ghi continuity unproven.

SnapshotCoordinator ghi source-vector watermarks, retries/topology/overflow và scope metadata. Bản sửa D03 dùng local capture interval của từng nguồn; Jason cycle cache publication và normalized callback sequence được bảo vệ trong local cut. Không khóa toàn MAS; raw live interpreter extras là evidence-only. NPL selected-board completeness không là global population proof. Callback sau local cut còn ở replay tail; GAP/stale/out-of-order/duplicate-content conflict khiến fail-closed/resync theo protocol.

## 6. Normative, exploration và inverse: giới hạn chính xác

NplActivationCrosswalk dùng public created annotation/subject/unifier và board UUID; recurrent activation phải có key riêng hoặc AMBIGUOUS. Terminal listener copy được crosswalk với activation, TimeTerm giải tại recorded epoch. NormativeObservation giữ reported state/deadline/subject/modality/lifetime; lifecycle consistency chỉ kiểm trace có coverage. Full deontic entailment, sanctions và MOISE maintenance normative realization không được tuyên bố đã hỗ trợ.

BoundedExplorer duyệt DFS mọi path trong FINITE_FIXED_POPULATION_V1. State vector hữu hạn đầy đủ, fixed population/links, finite scalar domains, clock/actors/assumptions explicit; guard/invariant/postcondition do USE evaluate, monitor history giữ riêng cho mỗi path. Không merge chỉ theo domain state, không POR chưa chứng minh. All/mixed/none terminal scenarios cho CONFORMANT_WITHIN_BOUNDS/WEAK/NON_CONFORMANT; cutoff, zero terminal, undefined/unknown cho INCONCLUSIVE. Witness hiển thị tối đa 128 bước, truncation rõ. WitnessConcretizer chỉ xác nhận khi supplied complete observation vectors/source actor/digests khớp; nếu chưa có thì abstract-only.

SourceLens get/put có source/target/complement hash và exact ownership. Supported structural edits: selected context/role names/cardinality/abstractness, artifact interface/property/operation name/arity, OPlan operator/order/outcome. PutGet/GetPut xét normalized AST. No-op giữ bytes nguyên; edited XML có thể đổi formatting/comments, original bytes vẫn trong complement. Stale/tamper/name collision/ambiguous ownership không ghi source. AND thiếu operator không thể chọn SEQUENCE/PARALLEL. Property scalar type/value hoặc Java/ASL executable behavior ngoài fragment là UNSUPPORTED. Đây là syntactic structural lens, không phải semantic round-trip JaCaMo.

## 7. Discrepancies và regression đã điều tra

- D01: static official project adapter không có live Cartago environment. Operation interface được admit từ exact reflected declared @OPERATION và gắn authority REFLECTED_DECLARED_OPERATION; invocation vẫn đòi runtime signature/UUID, không tạo runtime object từ declaration.
- D02: MissionSemantic trước đó thiếu getPreferables(). Thêm preferredMissionSemanticIds theo official API; empty supplement giữ wire/hash cũ, nonempty giữ source preference IDs nhưng không invent scheduler semantics.
- D03: Global watermark start/end gây starvation khi nhiều source chạy. Chuyển sang local cuts có topology/overflow validation, named metadata và Jason publication barrier; không hạ yêu cầu stable authoritative source.
- NPL zero selected boards trước bị coi UNAVAILABLE, làm bridge negotiation thất bại dù không có normative population nào được chọn. Đổi thành PARTIAL với explicit no-global-population diagnostic; observation của board lỗi vẫn UNAVAILABLE.
- Cartago singleton test fixtures: shutdown environment không tự quit mọi created agent, làm ảnh hưởng case sau. Các fixture mới quit exact created actor trước shutdown; gate source isolation đã chạy lại.
- Legacy Auction checksum thất bại vì file sibling có thay đổi sẵn thêm aorg_2. CanonicalReferenceFixture đọc git show HEAD:examples/auction/auction.jcm, kiểm đúng pinned checksum, stage dependencies dưới USE target. Golden expectations không đổi; không sửa/reset file của người dùng. Case nhiều runtime instances được kiểm riêng.
- Discovery cũ còn stale test class trong target và một wildcard fully-qualified chọn 0 tests. Đã clean module target, dùng exact simple names hoặc *Test; zero discovery không được tính acceptance. JUnit platform run không đồng nghĩa toàn bộ legacy JUnit3 core suite đã chạy.

## 8. Whole-plan audit: Phase 0–20 và task nhỏ

Mọi task hiện hành có một trạng thái cuối; không có TODO chưa phân loại. DONE chỉ xác nhận fragment và evidence ghi cùng dòng. Có 76 dòng task/gate: {"DONE":68,"UNSUPPORTED":6,"PARTIAL":2}. Không có BLOCKED còn chặn supported flow. Các hạn chế source/semantics được giữ UNSUPPORTED; phần measurement chưa thực hiện giữ PARTIAL.

| Phase | Kết quả | Phạm vi / điều kiện |
|---|---|---|
| 00 | DONE | Baseline, profile freeze, native/official adapter regression |
| 01 | DONE | G1 real CArtAgO + GroupBoard → native objects/links → OCL/@pre → ACHIEVE → replay |
| 02 | DONE trong fragment | CSL structure/policy; scoped MOISE compatibility và lifecycle ngoài fragment là UNSUPPORTED |
| 03 | DONE | Ecore + official frontends, 28-rule catalog, immutable source/feature decisions/complement |
| 04 | DONE trong observer fragment | Functional Scheme/Mission/Goal/Commitment, actual board oracle và exact instance binding |
| 05 | DONE trong abstraction | Original OPlan operator/order/prerequisites; outcome và execution evidence tách riêng |
| 06 | PARTIAL | Execution observer + explicit static Task abstraction DONE; Jason-to-OpId causation UNSUPPORTED |
| 07 | DONE trong ground fragment | Mental vs world separation; structured literals/rules; no general entailment |
| 08 | DONE | Actual USE compiler/evaluator, coverage-aware seven verdicts và paired states |
| 09 | DONE | Versioned temporal truth tables, Task/refinement/dependency, binding/episodes/gaps |
| 10 | DONE trong observation scope | Local cut vector, immutable evidence; global atomicity UNSUPPORTED |
| 11 | DONE | Identity/incarnation/partial omission/authoritative deletion/lifetime/resync |
| 12 | PARTIAL | Exact void operation invocation verification DONE; logger return/cross-interpreter causal mapping UNSUPPORTED |
| 13 | PARTIAL | Reported NPL lifecycle DONE; full independent normative semantics/sanctions UNSUPPORTED |
| 14 | DONE | Same native model/system/writer; binding inventory/read view/export/rollback |
| 15 | DONE | Detailed evidence, failure handling, static complement, replay/Step parity |
| 16 | DONE trong FINITE_FIXED_POPULATION_V1 | Explicit finite relation, bounded classification/frontier/witness, no general successor synthesis |
| 17 | PARTIAL | ECORE_STRUCTURAL_LENS_V1 laws DONE; official executable source inverse và semantic round-trip UNSUPPORTED |
| 18 | DONE | 65 matrix rows + full relevant 662 tests + installed replay ITs, no required skip |
| 19 | PARTIAL | Corrected finite workload + full-evaluation hash parity DONE; full live MAS SLA/long run unmeasured |
| 20 | DONE trong named deliverable | Package, claim/test/hash evidence, report and whole-plan status audit; unsupported areas giữ nguyên |

### 8.1. Ledger đầy đủ

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

### 8.2. Acceptance AC01–AC14

| Gate | Kết quả cho named profile | Bằng chứng chính |
|---|---|---|
| AC01 | DONE | SemanticTransformationTest, EcoreSourceLoaderTest, MappingRuleCatalog; mỗi feature có decision + complement |
| AC02 | DONE | CslVerticalSliceTest, FunctionalLiveBoardTest, NplLiveBoardObservationTest; SeparateJvmBridgeTest dùng MAS thật |
| AC03 | DONE | ObserverIdentityLifecycleTest, SharedStateAcceptanceTest, artifact/board recreate trong live slice |
| AC04 | DONE | CslNativeBindingTest, SemanticBindingTest, real Plays/InContext và typed Integer state |
| AC05 | DONE | Real invariant p=0/1 PASS, p=-1 FAIL còn state witness |
| AC06 | DONE trong observed interval | Real @pre 0→1 PASS / 0→2 FAIL; overlap và wrong completion descriptor negative tests |
| AC07 | DONE | TemporalGoalMonitorTest, normative gap/replay test; NONE/ACHIEVE/MAINTAIN/SUSTAIN/Task truth tables |
| AC08 | DONE trong admitted Task scope | JasonTaskConfigurationTest assert missing causation UNSUPPORTED; active goal/intention không dùng thay satisfaction |
| AC09 | DONE trong selected-board scope | Actual GroupBoard/SchemeBoard operations; source goal/commitment data keyed exact UUID |
| AC10 | DONE | Profile admission, full rule decisions, seven-valued unsupported evidence, capability manifest |
| AC11 | DONE | MappingBundle/TraceComplement, semantic failure witnesses, native/semantic replay hashes |
| AC12 | DONE | Fresh full relevant 662 tests, 2 installed IT cases, G1 live path; zero failure/error/skip |
| AC13 | DONE | Versioned capability manifest + configuration admission + báo cáo/ledger/acceptance JSON |
| AC14 | DONE | Claim limits explicit: local cuts, finite relation, reported NPL, restricted structural lens |

Round-trip ở đây là inverse của owned structural view do SourceLens.get tạo; không nhận arbitrary USE object-diagram edits và không là inverse tổng quát của MappingBundle.fromOfficial. Các luật được kiểm theo normalized AST, không claim executable JaCaMo semantic equivalence.

## 9. Kết quả kiểm thử và acceptance matrix

[Fresh full relevant regression](<D:/_CODE_BANK/Project_/08_Thesis/use/target/semantics-final-acceptance.log>) kết thúc BUILD SUCCESS lúc 12:12:01 +07:00 ngày 16/10/2026, tổng 05:37.

| Module | Tests | Failures | Errors | Skips |
|---|---:|---:|---:|---:|
| jacamo-bridge-contract | 18 | 0 | 0 | 0 |
| jacamo-bridge-jacamo | 68 | 0 | 0 | 0 |
| use-core | 14 | 0 | 0 | 0 |
| use-gui | 1 | 0 | 0 | 0 |
| use-plugin | 561 | 0 | 0 | 0 |
| Tổng full relevant | 662 | 0 | 0 | 0 |

[Final focused + installed package gate](<D:/_CODE_BANK/Project_/08_Thesis/use/target/semantics-final-packaged-smoke.log>): 20/20 pass, 0 failure/error/skip; gồm 2 installed IT cases và 18 tests có trong full suite. Union của full run + installed cases là 664 test cases, không cộng chồng 20 vào 662. [Package build](<D:/_CODE_BANK/Project_/08_Thesis/use/target/semantics-final-package.log>) thành công; skipTests chỉ dùng trong package build, không được tính làm test acceptance.

JUnit Platform discovery đã xác nhận count từng module. Full relevant ở đây là các *Test được runner này discover; không claim đã chạy toàn bộ legacy JUnit3 core tests hoặc mọi GUI IT/manual pixel flow. Những native OCL/runtime assertions mới chạy trong plugin suite. Không dùng exit 0 + zero discovery hay assumption skip làm gate pass.

### 9.1. 65 dòng matrix gốc → executable anchor

Các row dưới đây trỏ tới test source thực, method đã kiểm tồn tại và kết quả trong full run hoặc installed gate. I05 ghi excluded normative realization; authored MAINTAIN có oracle riêng tại F03. Test class loại unit/native typed snapshot không được gọi là full live MAS integration.

| ID | Status / scope | Test / oracle |
|---|---|---|
| A01 | DONE trong named profile | [CslStructuralPolicyTest](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/test/java/org/tzi/use/plugins/jacamo/semantics/CslStructuralPolicyTest.java>) — rejectsNestingCyclesKindsInvalidBoundsAndUnsupportedSourceParentProjection |
| A02 | DONE trong named profile | [CslStructuralPolicyTest](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/test/java/org/tzi/use/plugins/jacamo/semantics/CslStructuralPolicyTest.java>) — inheritanceEnactmentCompatibilityAndCardinalitiesUseOneRoleOccurrence |
| A03 | DONE trong named profile | [CslStructuralPolicyTest](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/test/java/org/tzi/use/plugins/jacamo/semantics/CslStructuralPolicyTest.java>) — rejectsNestingCyclesKindsInvalidBoundsAndUnsupportedSourceParentProjection |
| A04 | DONE trong named profile | [CslStructuralPolicyTest](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/test/java/org/tzi/use/plugins/jacamo/semantics/CslStructuralPolicyTest.java>) — inheritanceEnactmentCompatibilityAndCardinalitiesUseOneRoleOccurrence |
| A05 | DONE trong named profile | [CslVerticalSliceTest](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/test/java/org/tzi/use/plugins/jacamo/semantics/CslVerticalSliceTest.java>) — acceptedRuntimeEnactmentRetainsAnAuthoredPerContextCardinalityViolation |
| A06 | DONE trong named profile | [CslVerticalSliceTest](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/test/java/org/tzi/use/plugins/jacamo/semantics/CslVerticalSliceTest.java>) — realArtifactAndAcceptedGroupBoardFeedSameSystemInvariantTransitionAchieveAndReplay |
| B01 | DONE trong named profile | [CslVerticalSliceTest](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/test/java/org/tzi/use/plugins/jacamo/semantics/CslVerticalSliceTest.java>) — realArtifactAndAcceptedGroupBoardFeedSameSystemInvariantTransitionAchieveAndReplay |
| B02 | DONE trong named profile | [ObserverIdentityLifecycleTest](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/test/java/org/tzi/use/plugins/jacamo/semantics/ObserverIdentityLifecycleTest.java>) — sameRunDiffBeliefReappearanceRestartPartialOmissionAndCompleteDeletionRespectLifetimes |
| B03 | DONE trong named profile | [SharedStateAcceptanceTest](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/test/java/org/tzi/use/plugins/jacamo/semantics/SharedStateAcceptanceTest.java>) — homonymousRolesInTwoOfficialOrganizationSpecificationsCannotCrossBind |
| B04 | DONE trong named profile | [SharedStateAcceptanceTest](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/test/java/org/tzi/use/plugins/jacamo/semantics/SharedStateAcceptanceTest.java>) — sameRoleInTwoContextsIsDistinctAndObservedLeaveReadoptCreatesANewOccurrence |
| C01 | DONE trong named profile | [CslVerticalSliceTest](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/test/java/org/tzi/use/plugins/jacamo/semantics/CslVerticalSliceTest.java>) — realArtifactAndAcceptedGroupBoardFeedSameSystemInvariantTransitionAchieveAndReplay |
| C02 | DONE trong named profile | [ObserverIdentityLifecycleTest](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/test/java/org/tzi/use/plugins/jacamo/semantics/ObserverIdentityLifecycleTest.java>) — sameRunDiffBeliefReappearanceRestartPartialOmissionAndCompleteDeletionRespectLifetimes |
| C03 | DONE trong named profile | [CslVerticalSliceTest](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/test/java/org/tzi/use/plugins/jacamo/semantics/CslVerticalSliceTest.java>) — realArtifactAndAcceptedGroupBoardFeedSameSystemInvariantTransitionAchieveAndReplay |
| C04 | DONE trong named profile | [TemporalGoalMonitorTest](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/test/java/org/tzi/use/plugins/jacamo/semantics/TemporalGoalMonitorTest.java>) — historyGapReactivationAndIncarnationHaveExplicitSemantics |
| C05 | DONE trong named profile | [SnapshotCoordinatorTest](<D:/_CODE_BANK/Project_/08_Thesis/use/jacamo-bridge-jacamo/src/test/java/org/jacamo/bridge/adapter/SnapshotCoordinatorTest.java>) — retriesConcurrentTopologyChangeAndReplaysOnlyAfterAcceptedWatermark |
| C06 | DONE trong named profile | [RuntimeVerificationCoordinatorTest](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/test/java/org/tzi/use/plugins/jacamo/codegrounded/RuntimeVerificationCoordinatorTest.java>) — duplicateIsIdempotentWhileConflictRewindGapAndStaleIdentitiesCannotPass |
| D01 | DONE trong named profile | [MentalStateProjectionTest](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/test/java/org/tzi/use/plugins/jacamo/semantics/MentalStateProjectionTest.java>) — changingStoredKnowledgeKeepsWorldStateAndOclQueriesSeparateWithClosedScopeAdmission |
| D02 | DONE trong named profile | [JasonStructuredObservationTest](<D:/_CODE_BANK/Project_/08_Thesis/use/jacamo-bridge-jacamo/src/test/java/org/jacamo/bridge/adapter/JasonStructuredObservationTest.java>) — completeStoredCutPreservesNamespaceNegationAnnotationRulesAndNumericTermIdentity |
| D03 | DONE trong named profile | [JasonStructuredObservationTest](<D:/_CODE_BANK/Project_/08_Thesis/use/jacamo-bridge-jacamo/src/test/java/org/jacamo/bridge/adapter/JasonStructuredObservationTest.java>) — completeStoredCutPreservesNamespaceNegationAnnotationRulesAndNumericTermIdentity |
| D04 | DONE trong named profile | [MentalStateProjectionTest](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/test/java/org/tzi/use/plugins/jacamo/semantics/MentalStateProjectionTest.java>) — liveArtifactValueAndLaggingMentalPerceptRemainIndependentlyQueryableAfterBeliefRemoval |
| E01 | DONE trong named profile | [CslSchemaTest](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/test/java/org/tzi/use/plugins/jacamo/semantics/CslSchemaTest.java>) — strictScalarBindingDoesNotWidenOrTruncateObservations |
| E02 | DONE trong named profile | [SharedStateAcceptanceTest](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/test/java/org/tzi/use/plugins/jacamo/semantics/SharedStateAcceptanceTest.java>) — duplicatePropertyOccurrenceAndTypeDriftRejectWholeUpdateWithoutReplacingSlot |
| E03 | DONE trong named profile | [CslSchemaTest](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/test/java/org/tzi/use/plugins/jacamo/semantics/CslSchemaTest.java>) — strictScalarBindingDoesNotWidenOrTruncateObservations |
| E04 | DONE trong named profile | [SharedStateAcceptanceTest](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/test/java/org/tzi/use/plugins/jacamo/semantics/SharedStateAcceptanceTest.java>) — duplicatePropertyOccurrenceAndTypeDriftRejectWholeUpdateWithoutReplacingSlot |
| E05 | DONE trong named profile | [CslVerticalSliceTest](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/test/java/org/tzi/use/plugins/jacamo/semantics/CslVerticalSliceTest.java>) — realArtifactAndAcceptedGroupBoardFeedSameSystemInvariantTransitionAchieveAndReplay |
| F01 | DONE trong named profile | [TemporalGoalMonitorTest](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/test/java/org/tzi/use/plugins/jacamo/semantics/TemporalGoalMonitorTest.java>) — independentlyExpectedTemporalTraces |
| F02 | DONE trong named profile | [TemporalGoalMonitorTest](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/test/java/org/tzi/use/plugins/jacamo/semantics/TemporalGoalMonitorTest.java>) — independentlyExpectedTemporalTraces |
| F03 | DONE trong named profile | [TemporalGoalMonitorTest](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/test/java/org/tzi/use/plugins/jacamo/semantics/TemporalGoalMonitorTest.java>) — independentlyExpectedTemporalTraces |
| F04 | DONE trong named profile | [TemporalGoalMonitorTest](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/test/java/org/tzi/use/plugins/jacamo/semantics/TemporalGoalMonitorTest.java>) — independentlyExpectedTemporalTraces |
| F05 | DONE trong named profile | [TemporalGoalMonitorTest](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/test/java/org/tzi/use/plugins/jacamo/semantics/TemporalGoalMonitorTest.java>) — missingBindingCannotRepairMaintainHistoryAndKnownWitnessSurvivesOnlySameEpisode |
| F06 | DONE trong named profile | [TemporalGoalMonitorTest](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/test/java/org/tzi/use/plugins/jacamo/semantics/TemporalGoalMonitorTest.java>) — historyGapReactivationAndIncarnationHaveExplicitSemantics |
| F07 | DONE trong named profile | [TemporalGoalMonitorTest](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/test/java/org/tzi/use/plugins/jacamo/semantics/TemporalGoalMonitorTest.java>) — historyGapReactivationAndIncarnationHaveExplicitSemantics |
| G01 | DONE trong named profile | [TemporalGoalMonitorTest](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/test/java/org/tzi/use/plugins/jacamo/semantics/TemporalGoalMonitorTest.java>) — refinementUsesEvaluatedChildrenAndDependencyUsesBoundOccurrence |
| G02 | DONE trong named profile | [TemporalGoalMonitorTest](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/test/java/org/tzi/use/plugins/jacamo/semantics/TemporalGoalMonitorTest.java>) — refinementUsesEvaluatedChildrenAndDependencyUsesBoundOccurrence |
| G03 | DONE trong named profile | [TemporalGoalMonitorTest](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/test/java/org/tzi/use/plugins/jacamo/semantics/TemporalGoalMonitorTest.java>) — refinementUsesEvaluatedChildrenAndDependencyUsesBoundOccurrence |
| G04 | DONE trong named profile | [OPlanSemanticsTest](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/test/java/org/tzi/use/plugins/jacamo/semantics/OPlanSemanticsTest.java>) — sequenceCounterexampleParallelInterleavingsAndMissingCausalityAreDifferent |
| G05 | DONE trong named profile | [OPlanSemanticsTest](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/test/java/org/tzi/use/plugins/jacamo/semantics/OPlanSemanticsTest.java>) — outcomeChoiceAcceptsOneOrBothAndSequenceStillRetainsItsOrder |
| H01 | DONE trong named profile | [CslVerticalSliceTest](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/test/java/org/tzi/use/plugins/jacamo/semantics/CslVerticalSliceTest.java>) — realArtifactAndAcceptedGroupBoardFeedSameSystemInvariantTransitionAchieveAndReplay |
| H02 | DONE trong named profile | [NativeInvocationSemanticsTest](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/test/java/org/tzi/use/plugins/jacamo/codegrounded/runtime/NativeInvocationSemanticsTest.java>) — overlappingInvocationsKeepTheirOwnPreStateAndSuspendResumeNeverOverwriteIt |
| H03 | DONE trong named profile | [NativeInvocationSemanticsTest](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/test/java/org/tzi/use/plugins/jacamo/codegrounded/runtime/NativeInvocationSemanticsTest.java>) — sourceFailureDuplicateCapacityTimeoutAndMissingBoundaryNeverProducePostPass |
| H04 | DONE trong named profile | [NativeInvocationSemanticsTest](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/test/java/org/tzi/use/plugins/jacamo/codegrounded/runtime/NativeInvocationSemanticsTest.java>) — overlappingInvocationsKeepTheirOwnPreStateAndSuspendResumeNeverOverwriteIt |
| H05 | DONE trong named profile | [NativeInvocationSemanticsTest](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/test/java/org/tzi/use/plugins/jacamo/codegrounded/runtime/NativeInvocationSemanticsTest.java>) — wrongIncarnationSignatureAndCompletionArgumentsCannotBorrowAMatchingPre |
| H06 | DONE trong named profile | [OclSemanticBackendTest](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/test/java/org/tzi/use/plugins/jacamo/semantics/OclSemanticBackendTest.java>) — collectionsNavigationAtPreAndTypedResultUsePinnedNativeStates |
| I01 | DONE trong named profile | [CslVerticalSliceTest](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/test/java/org/tzi/use/plugins/jacamo/semantics/CslVerticalSliceTest.java>) — realArtifactAndAcceptedGroupBoardFeedSameSystemInvariantTransitionAchieveAndReplay |
| I02 | DONE trong named profile | [FunctionalObservationTest](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/test/java/org/tzi/use/plugins/jacamo/semantics/FunctionalObservationTest.java>) — commitmentLeaveRejoinProducesNewLifetimeAndAllocationViolationIsRetained |
| I03 | DONE trong named profile | [FunctionalLiveBoardTest](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/test/java/org/tzi/use/plugins/jacamo/semantics/FunctionalLiveBoardTest.java>) — realPublicSchemeBoardCutCarriesIndependentArgumentsAndUuidIntoSameNativeState |
| I04 | DONE trong named profile | [FunctionalLiveBoardTest](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/test/java/org/tzi/use/plugins/jacamo/semantics/FunctionalLiveBoardTest.java>) — inheritedBoardOperationsGiveWaitingEnabledSatisfiedAndExactCommittedDoneAgents |
| I05 | UNSUPPORTED normative realization; exclusion reviewed | [SemanticsProfileTest](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/test/java/org/tzi/use/plugins/jacamo/semantics/SemanticsProfileTest.java>) — v1CannotAdmitUnimplementedTemporalOrNormativeSemantics |
| J01 | DONE trong named profile | [NplLiveBoardObservationTest](<D:/_CODE_BANK/Project_/08_Thesis/use/jacamo-bridge-jacamo/src/test/java/org/jacamo/bridge/adapter/NplLiveBoardObservationTest.java>) — realNplListenerAndPublicGettersKeepActivationFulfillmentDeadlineMissAndUnusedPermissionDistinct |
| J02 | DONE trong named profile | [NplLiveBoardObservationTest](<D:/_CODE_BANK/Project_/08_Thesis/use/jacamo-bridge-jacamo/src/test/java/org/jacamo/bridge/adapter/NplLiveBoardObservationTest.java>) — stateDeadlineMaintenanceInactivityAndSameNameBoardReplacementHaveDistinctSourceLifetimes |
| J03 | DONE trong named profile | [NormativeObservationTest](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/test/java/org/tzi/use/plugins/jacamo/semantics/NormativeObservationTest.java>) — reportedLifecycleStaysSeparateFromIndependentDerivationAndIsReplayedWithoutAClock |
| J04 | DONE trong named profile | [NplLiveBoardObservationTest](<D:/_CODE_BANK/Project_/08_Thesis/use/jacamo-bridge-jacamo/src/test/java/org/jacamo/bridge/adapter/NplLiveBoardObservationTest.java>) — stateDeadlineMaintenanceInactivityAndSameNameBoardReplacementHaveDistinctSourceLifetimes |
| K01 | DONE trong named profile | [SourceLensTest](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/test/java/org/tzi/use/plugins/jacamo/semantics/SourceLensTest.java>) — getPutPreservesUnchangedBytesAndPutGetNormalizesSelectedStructure |
| K02 | DONE trong named profile | [SourceLensTest](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/test/java/org/tzi/use/plugins/jacamo/semantics/SourceLensTest.java>) — getPutPreservesUnchangedBytesAndPutGetNormalizesSelectedStructure |
| K03 | DONE trong named profile | [SourceLensTest](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/test/java/org/tzi/use/plugins/jacamo/semantics/SourceLensTest.java>) — staleDigestComplementTamperNameCollisionAndAmbiguousOwnershipNeverWriteSource |
| L01 | DONE trong named profile | [CslVerticalSliceTest](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/test/java/org/tzi/use/plugins/jacamo/semantics/CslVerticalSliceTest.java>) — realArtifactAndAcceptedGroupBoardFeedSameSystemInvariantTransitionAchieveAndReplay |
| L02 | DONE trong named profile | [SharedStateAcceptanceTest](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/test/java/org/tzi/use/plugins/jacamo/semantics/SharedStateAcceptanceTest.java>) — oneSharedArtifactChangeReevaluatesAllBoundActorsOnTheSameVersion |
| L03 | DONE trong named profile | [CslVerticalSliceTest](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/test/java/org/tzi/use/plugins/jacamo/semantics/CslVerticalSliceTest.java>) — realArtifactAndAcceptedGroupBoardFeedSameSystemInvariantTransitionAchieveAndReplay |
| L04 | DONE trong named profile | [CslVerticalSliceTest](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/test/java/org/tzi/use/plugins/jacamo/semantics/CslVerticalSliceTest.java>) — realArtifactAndAcceptedGroupBoardFeedSameSystemInvariantTransitionAchieveAndReplay |
| L05 | DONE trong named profile | [NativeRuntimePackagedReplayIT](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/test/java/org/tzi/use/plugins/jacamo/codegrounded/NativeRuntimePackagedReplayIT.java>) — installedReleaseReplaysNativeOclHistoryWithoutMavenOrJaCaMoClasspath |
| M01 | DONE trong named profile | [BoundedExplorerTest](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/test/java/org/tzi/use/plugins/jacamo/semantics/BoundedExplorerTest.java>) — finiteOracleClassifiesAllMixedNoneAndUnknownWithoutGeneralMasClaim |
| M02 | DONE trong named profile | [BoundedExplorerTest](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/test/java/org/tzi/use/plugins/jacamo/semantics/BoundedExplorerTest.java>) — differentTemporalHistoriesAtTheSameDomainStateAreNeverMergedAndActorsInterleave |
| M03 | DONE trong named profile | [BoundedExplorerTest](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/test/java/org/tzi/use/plugins/jacamo/semantics/BoundedExplorerTest.java>) — missingFrameArbitraryBehaviorAndBackendUndefinedAreExplicit |
| N01 | DONE trong named profile | [VerificationLoadScenarioTest](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/test/java/org/tzi/use/plugins/jacamo/semantics/VerificationLoadScenarioTest.java>) — boundedLoadMatrixKeepsFullEvaluationAndReplayHashesIdenticalWithAndWithoutBindingCache |
| N02 | DONE trong named profile | [VerificationLoadScenarioTest](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/test/java/org/tzi/use/plugins/jacamo/semantics/VerificationLoadScenarioTest.java>) — twoSourceWritersAreSerializedWithoutDroppingSharedStateUpdatesAndReplayRetainsAcceptedOrder |

Live oracles: CslVerticalSliceTest dùng real artifact/logger/GroupBoard; FunctionalLiveBoardTest dùng inherited public SchemeBoard operations; adapter NplLiveBoardObservationTest dùng real NPL listener/getters/timers; SeparateJvmBridgeTest khởi chạy actual JaCaMo MAS và consumer JVM tách biệt. SharedStateAcceptanceTest và VerificationLoadScenarioTest dùng typed normalized observations + native backend, không đo runtime scheduling của nhiều Jason agents. OPlanSemanticsTest so sánh structural prerequisites với actual MOISE Goal API; TemporalGoalMonitorTest là truth-table oracle độc lập. SourceLensTest/BoundedExplorerTest là offline fragment tests.

### 9.2. Lỗi đã phát hiện và kết quả sửa

Các log thất bại lịch sử được giữ trong target; không coi là kết quả cuối. Current-source run trước có 550 plugin cases, lỗi checksum Auction/user changes và runtime readiness. Sau sửa nguồn cut, SeparateJvmBridgeTest pass với 866 facts; lần full tiếp 555 cases chỉ còn oracle mới kỳ vọng complete cho replay có gap. Oracle được sửa thành hashes match + PARTIAL_COVERAGE_GAP, không đổi replay semantics.

Các strong acceptance tests sau đó phát hiện fixture benchmark khai báo COMPLETE nhưng thiếu standalone property facts của official contract. Đã sửa fixture và buộc mọi initial raw goal SATISFIED, mọi measured raw goal xác định, actual p0 bằng delta. Số đo cũ (semantic-performance-before-hoist.json và các run trước khi sửa property facts) không dùng nghiệm thu. Current full/focused gates đã chạy lại với dữ liệu đủ coverage.

Một lần gọi direct Surefire goal không qua test-compile resolve local snapshot dependency cũ, gây NoSuchMethodError; đây không phải release smoke hợp lệ. Lệnh tái lập luôn chạy reactor test-compile trước goal để lấy contract/adapters hiện tại. Actual isolated release tests sau đó pass. Strict duplicate OS ID rejection cũng được giữ; fixture hai OS dùng distinct specification IDs thay vì làm yếu loader.

## 10. Performance / concurrency đã đo

Artifact được nghiệm thu: [use-plugin/target/semantic-performance.json](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/target/semantic-performance.json>). Java 21.0.5+9-LTS-239, Windows 11 amd64, 16 logical processors, maxHeap 4219469824 bytes, GC G1 Young Generation, G1 Concurrent GC, G1 Old Generation, seed 0. Scope: finite SYNTHETIC_ADAPTER_CONTRACT; unpaced accepted boundaries, không full live MAS throughput/SLA.

| Agents/artifacts/props/context/goals/invariants | Warmup/measured boundaries | Direct p50/p95/p99 ms | Cached p50/p95/p99 ms | Cached boundaries/s | Journal/replay bytes |
|---|---|---|---|---:|---:|
| 1/1/1/1/1/1 | 4/16 | 2.165 / 3.061 / 3.061 | 2.276 / 2.903 / 2.903 | 439.52 | 222081 / 236720 |
| 10/10/10/10/10/10 | 4/12 | 18.570 / 24.896 / 24.896 | 16.136 / 21.257 / 21.257 | 62.80 | 2496759 / 2523883 |
| 50/100/10/10/100/10 | 2/6 | 159.712 / 186.861 / 186.861 | 170.802 / 186.588 / 186.588 | 5.96 | 11600369 / 11701969 |
| 50/10/10/10/1000/1 | 0/2 | 109.639 / 136.503 / 136.503 | 111.158 / 117.236 / 117.236 | 8.76 | 5433975 / 6276352 |

Cả hai đường đều dùng immutable configuration đã load, actual USE OCL và full reevaluation mọi requirement sau mỗi accepted boundary. Reference path bỏ class/binding lookup cache; không tắt predicates/history, không gộp/drop events. Logical result hashes, final state hash và complete replay phải bằng nhau. Cached không được giả định nhanh hơn ở mọi workload; samples hữu hạn, thứ tự chạy/JIT/GC có ảnh hưởng. Workload 1000 goals chỉ có 2 measured boundaries nên p95/p99 là max sample, không là percentile estimate cho production population.

RuntimePerformanceStats giữ exact nearest-rank của recent 1024 samples/metric cùng all-time count/mean/max; không lưu unbounded timing history. Có writer wait, native mutation, export, OCL, semantic evaluation, iStar propagation, snapshot và journal timings. Một số phase timings lồng nhau (iStar nằm trong semantic), không cộng các percentile để suy total latency. JSON giữ heap delta/GC/retention/backlog và replay size; heap delta không chứng minh leak-free.

Concurrency oracle: hai source writer threads ghi cùng artifact, barrier bắt đầu, mỗi stream 10 ordered updates; coordinator serialize tất cả 20, journal gồm baseline+snapshot+20 entries, no gap và replay cùng endpoint hash. Shared-state oracle riêng dùng bốn actor/context bindings trên một artifact, raw condition đổi đồng thời và ACHIEVE giữ đúng historical witness. Source queue overflow/backpressure/gap, bounded retention và same-run resync có regression anchors riêng.

## 11. Files / classes đã thay đổi và đối chiếu thiết kế

Có 39 tracked files sửa và 132 files mới trước khi tạo báo cáo này; thêm implementation-report.md. Inventory đầy đủ bên dưới gồm 172 files, không tính target/generated binaries bị Git ignore. USE core/GUI engine source không cần sửa; runtime plugin/bridge và resources/tests được mở rộng tại seam hiện có.

| Thành phần | Implementation thực tế | Lý do / reuse |
|---|---|---|
| Static transformation | [SemanticTransformationService.java](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/mapping/SemanticTransformationService.java>) | Reuse official adapters, native catalog/pipeline; không tạo engine đối thủ |
| Source M2 frontend | [JacamoEcoreSourceLoader.java](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/mde/JacamoEcoreSourceLoader.java>) | Dynamic EMF exact Ecore + typed records; không cần generated Java metamodel khác |
| CSL binding | [CslUseBinding.java](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/csl/CslUseBinding.java>) | Cài M1 trên cùng MModel, tách observer/domain |
| Runtime mapping | [SemanticObservationMapper.java](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/runtime/SemanticObservationMapper.java>) | Typed observer mappings qua native mutation port |
| Mutation/transaction | [RuntimeVerificationCoordinator.java](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/codegrounded/runtime/RuntimeVerificationCoordinator.java>) | Reuse savepoint/writer/journal; không thêm SemanticMutationPlan engine riêng |
| OCL/temporal | [SemanticRuntimeVerifier.java](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/runtime/SemanticRuntimeVerifier.java>) | Actual USE evaluator + participant monitor |
| Exact invocation | [NativeOperationCheckpoints.java](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/codegrounded/runtime/NativeOperationCheckpoints.java>) | Reuse existing operation service, exact copied pre-state và descriptor |
| Source cuts | [SnapshotCoordinator.java](<D:/_CODE_BANK/Project_/08_Thesis/use/jacamo-bridge-jacamo/src/main/java/org/jacamo/bridge/adapter/SnapshotCoordinator.java>) | LocalCut/vector watermark/topology; Jason publication barrier |
| Normative observer | [NplActivationCrosswalk.java](<D:/_CODE_BANK/Project_/08_Thesis/use/jacamo-bridge-jacamo/src/main/java/org/jacamo/bridge/adapter/NplActivationCrosswalk.java>) | Public activation provenance/copy matching, no fake invariant |
| Evidence/replay | [NativeRuntimeReplay.java](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/codegrounded/runtime/NativeRuntimeReplay.java>) | Optional mapping supplement, backwards-compatible old catalogue |
| Read-only binding/view | [ProjectionBindingRegistry.java](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/binding/ProjectionBindingRegistry.java>) | Immutable consumer inventory; no mutable state ownership |
| Bounded extension | [BoundedExplorer.java](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/exploration/BoundedExplorer.java>) | Isolated offline finite relation evaluator, no live second state |
| Inverse extension | [SourceLens.java](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/roundtrip/SourceLens.java>) | Offline owned structural view, compare-and-set complement |
| G1 integration | [CslVerticalSliceTest.java](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/test/java/org/tzi/use/plugins/jacamo/semantics/CslVerticalSliceTest.java>) | Proposed LiveCslVerificationIT realized as discovered JUnit Test |
| Load benchmark | [VerificationLoadScenarioTest.java](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/test/java/org/tzi/use/plugins/jacamo/semantics/VerificationLoadScenarioTest.java>) | Proposed benchmark class realized in test source set with manifest |
| Lens laws | [SourceLensTest.java](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/test/java/org/tzi/use/plugins/jacamo/semantics/SourceLensTest.java>) | Proposed RoundTripLawTest covered by explicit source-lens law tests |

### 11.1. Full change inventory

| Status | File |
|---|---|
| NEW | [implementation-plan.md](<D:/_CODE_BANK/Project_/08_Thesis/use/implementation-plan.md>) |
| NEW | [implementation-report.md](<D:/_CODE_BANK/Project_/08_Thesis/use/implementation-report.md>) |
| MODIFIED | [jacamo-bridge-contract/src/main/java/org/jacamo/bridge/contract/ContractPayloads.java](<D:/_CODE_BANK/Project_/08_Thesis/use/jacamo-bridge-contract/src/main/java/org/jacamo/bridge/contract/ContractPayloads.java>) |
| MODIFIED | [jacamo-bridge-contract/src/main/java/org/jacamo/bridge/contract/RuntimeSnapshot.java](<D:/_CODE_BANK/Project_/08_Thesis/use/jacamo-bridge-contract/src/main/java/org/jacamo/bridge/contract/RuntimeSnapshot.java>) |
| MODIFIED | [jacamo-bridge-contract/src/main/java/org/jacamo/bridge/contract/semantic/MoiseSemanticContract.java](<D:/_CODE_BANK/Project_/08_Thesis/use/jacamo-bridge-contract/src/main/java/org/jacamo/bridge/contract/semantic/MoiseSemanticContract.java>) |
| MODIFIED | [jacamo-bridge-contract/src/main/java/org/jacamo/bridge/contract/semantic/SemanticContractCodec.java](<D:/_CODE_BANK/Project_/08_Thesis/use/jacamo-bridge-contract/src/main/java/org/jacamo/bridge/contract/semantic/SemanticContractCodec.java>) |
| MODIFIED | [jacamo-bridge-jacamo/src/main/java/jason/infra/local/LiveJaCaMoLauncherMain.java](<D:/_CODE_BANK/Project_/08_Thesis/use/jacamo-bridge-jacamo/src/main/java/jason/infra/local/LiveJaCaMoLauncherMain.java>) |
| MODIFIED | [jacamo-bridge-jacamo/src/main/java/org/jacamo/bridge/adapter/BridgeAgArch.java](<D:/_CODE_BANK/Project_/08_Thesis/use/jacamo-bridge-jacamo/src/main/java/org/jacamo/bridge/adapter/BridgeAgArch.java>) |
| MODIFIED | [jacamo-bridge-jacamo/src/main/java/org/jacamo/bridge/adapter/BridgeRuntimeRegistry.java](<D:/_CODE_BANK/Project_/08_Thesis/use/jacamo-bridge-jacamo/src/main/java/org/jacamo/bridge/adapter/BridgeRuntimeRegistry.java>) |
| MODIFIED | [jacamo-bridge-jacamo/src/main/java/org/jacamo/bridge/adapter/CartagoSnapshotSource.java](<D:/_CODE_BANK/Project_/08_Thesis/use/jacamo-bridge-jacamo/src/main/java/org/jacamo/bridge/adapter/CartagoSnapshotSource.java>) |
| NEW | [jacamo-bridge-jacamo/src/main/java/org/jacamo/bridge/adapter/JasonCycleObservation.java](<D:/_CODE_BANK/Project_/08_Thesis/use/jacamo-bridge-jacamo/src/main/java/org/jacamo/bridge/adapter/JasonCycleObservation.java>) |
| NEW | [jacamo-bridge-jacamo/src/main/java/org/jacamo/bridge/adapter/JasonGroundQueryCompiler.java](<D:/_CODE_BANK/Project_/08_Thesis/use/jacamo-bridge-jacamo/src/main/java/org/jacamo/bridge/adapter/JasonGroundQueryCompiler.java>) |
| MODIFIED | [jacamo-bridge-jacamo/src/main/java/org/jacamo/bridge/adapter/JasonSnapshotSource.java](<D:/_CODE_BANK/Project_/08_Thesis/use/jacamo-bridge-jacamo/src/main/java/org/jacamo/bridge/adapter/JasonSnapshotSource.java>) |
| NEW | [jacamo-bridge-jacamo/src/main/java/org/jacamo/bridge/adapter/JasonStructuredTerms.java](<D:/_CODE_BANK/Project_/08_Thesis/use/jacamo-bridge-jacamo/src/main/java/org/jacamo/bridge/adapter/JasonStructuredTerms.java>) |
| NEW | [jacamo-bridge-jacamo/src/main/java/org/jacamo/bridge/adapter/MoiseBoardArtifactObservation.java](<D:/_CODE_BANK/Project_/08_Thesis/use/jacamo-bridge-jacamo/src/main/java/org/jacamo/bridge/adapter/MoiseBoardArtifactObservation.java>) |
| MODIFIED | [jacamo-bridge-jacamo/src/main/java/org/jacamo/bridge/adapter/MoiseBoardSnapshotSource.java](<D:/_CODE_BANK/Project_/08_Thesis/use/jacamo-bridge-jacamo/src/main/java/org/jacamo/bridge/adapter/MoiseBoardSnapshotSource.java>) |
| MODIFIED | [jacamo-bridge-jacamo/src/main/java/org/jacamo/bridge/adapter/MoiseGoalObservation.java](<D:/_CODE_BANK/Project_/08_Thesis/use/jacamo-bridge-jacamo/src/main/java/org/jacamo/bridge/adapter/MoiseGoalObservation.java>) |
| NEW | [jacamo-bridge-jacamo/src/main/java/org/jacamo/bridge/adapter/NplActivationCrosswalk.java](<D:/_CODE_BANK/Project_/08_Thesis/use/jacamo-bridge-jacamo/src/main/java/org/jacamo/bridge/adapter/NplActivationCrosswalk.java>) |
| MODIFIED | [jacamo-bridge-jacamo/src/main/java/org/jacamo/bridge/adapter/NplBoardSnapshotSource.java](<D:/_CODE_BANK/Project_/08_Thesis/use/jacamo-bridge-jacamo/src/main/java/org/jacamo/bridge/adapter/NplBoardSnapshotSource.java>) |
| MODIFIED | [jacamo-bridge-jacamo/src/main/java/org/jacamo/bridge/adapter/OfficialMoiseAdapter.java](<D:/_CODE_BANK/Project_/08_Thesis/use/jacamo-bridge-jacamo/src/main/java/org/jacamo/bridge/adapter/OfficialMoiseAdapter.java>) |
| MODIFIED | [jacamo-bridge-jacamo/src/main/java/org/jacamo/bridge/adapter/SnapshotCoordinator.java](<D:/_CODE_BANK/Project_/08_Thesis/use/jacamo-bridge-jacamo/src/main/java/org/jacamo/bridge/adapter/SnapshotCoordinator.java>) |
| MODIFIED | [jacamo-bridge-jacamo/src/main/java/org/jacamo/bridge/adapter/SnapshotSource.java](<D:/_CODE_BANK/Project_/08_Thesis/use/jacamo-bridge-jacamo/src/main/java/org/jacamo/bridge/adapter/SnapshotSource.java>) |
| NEW | [jacamo-bridge-jacamo/src/test/java/org/jacamo/bridge/adapter/JasonStructuredObservationTest.java](<D:/_CODE_BANK/Project_/08_Thesis/use/jacamo-bridge-jacamo/src/test/java/org/jacamo/bridge/adapter/JasonStructuredObservationTest.java>) |
| NEW | [jacamo-bridge-jacamo/src/test/java/org/jacamo/bridge/adapter/NplActivationCrosswalkTest.java](<D:/_CODE_BANK/Project_/08_Thesis/use/jacamo-bridge-jacamo/src/test/java/org/jacamo/bridge/adapter/NplActivationCrosswalkTest.java>) |
| NEW | [jacamo-bridge-jacamo/src/test/java/org/jacamo/bridge/adapter/NplLiveBoardObservationTest.java](<D:/_CODE_BANK/Project_/08_Thesis/use/jacamo-bridge-jacamo/src/test/java/org/jacamo/bridge/adapter/NplLiveBoardObservationTest.java>) |
| MODIFIED | [jacamo-bridge-jacamo/src/test/java/org/jacamo/bridge/adapter/SnapshotCoordinatorTest.java](<D:/_CODE_BANK/Project_/08_Thesis/use/jacamo-bridge-jacamo/src/test/java/org/jacamo/bridge/adapter/SnapshotCoordinatorTest.java>) |
| NEW | [use-plugin/Core/Metamodel/csl-monitor-v1/CSLMonitor.ecore](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/Core/Metamodel/csl-monitor-v1/CSLMonitor.ecore>) |
| NEW | [use-plugin/Core/Metamodel/jacamo-source-v2/jacamo_v2.ecore](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/Core/Metamodel/jacamo-source-v2/jacamo_v2.ecore>) |
| MODIFIED | [use-plugin/pom.xml](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/pom.xml>) |
| MODIFIED | [use-plugin/src/main/java/org/tzi/use/plugins/jacamo/codegrounded/CodeGroundedNativePipeline.java](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/codegrounded/CodeGroundedNativePipeline.java>) |
| MODIFIED | [use-plugin/src/main/java/org/tzi/use/plugins/jacamo/codegrounded/constraint/ExternalOclConstraintService.java](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/codegrounded/constraint/ExternalOclConstraintService.java>) |
| MODIFIED | [use-plugin/src/main/java/org/tzi/use/plugins/jacamo/codegrounded/runtime/NativeOperationCheckpoints.java](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/codegrounded/runtime/NativeOperationCheckpoints.java>) |
| MODIFIED | [use-plugin/src/main/java/org/tzi/use/plugins/jacamo/codegrounded/runtime/NativeRuntimeMutationEngine.java](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/codegrounded/runtime/NativeRuntimeMutationEngine.java>) |
| MODIFIED | [use-plugin/src/main/java/org/tzi/use/plugins/jacamo/codegrounded/runtime/NativeRuntimeProjector.java](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/codegrounded/runtime/NativeRuntimeProjector.java>) |
| MODIFIED | [use-plugin/src/main/java/org/tzi/use/plugins/jacamo/codegrounded/runtime/NativeRuntimeReplay.java](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/codegrounded/runtime/NativeRuntimeReplay.java>) |
| MODIFIED | [use-plugin/src/main/java/org/tzi/use/plugins/jacamo/codegrounded/runtime/RuntimePerformanceStats.java](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/codegrounded/runtime/RuntimePerformanceStats.java>) |
| MODIFIED | [use-plugin/src/main/java/org/tzi/use/plugins/jacamo/codegrounded/runtime/RuntimeVerificationCoordinator.java](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/codegrounded/runtime/RuntimeVerificationCoordinator.java>) |
| MODIFIED | [use-plugin/src/main/java/org/tzi/use/plugins/jacamo/codegrounded/runtime/RuntimeVerificationResult.java](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/codegrounded/runtime/RuntimeVerificationResult.java>) |
| MODIFIED | [use-plugin/src/main/java/org/tzi/use/plugins/jacamo/codegrounded/use/DomainProjection.java](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/codegrounded/use/DomainProjection.java>) |
| MODIFIED | [use-plugin/src/main/java/org/tzi/use/plugins/jacamo/codegrounded/use/MoiseDomainProjection.java](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/codegrounded/use/MoiseDomainProjection.java>) |
| MODIFIED | [use-plugin/src/main/java/org/tzi/use/plugins/jacamo/codegrounded/use/MoiseUseSymbols.java](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/codegrounded/use/MoiseUseSymbols.java>) |
| MODIFIED | [use-plugin/src/main/java/org/tzi/use/plugins/jacamo/codegrounded/use/NativeProjectionAudit.java](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/codegrounded/use/NativeProjectionAudit.java>) |
| MODIFIED | [use-plugin/src/main/java/org/tzi/use/plugins/jacamo/codegrounded/use/NativeUseModelBuilder.java](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/codegrounded/use/NativeUseModelBuilder.java>) |
| MODIFIED | [use-plugin/src/main/java/org/tzi/use/plugins/jacamo/codegrounded/use/NativeUseStateBuilder.java](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/codegrounded/use/NativeUseStateBuilder.java>) |
| NEW | [use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/binding/ProjectionBindingRegistry.java](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/binding/ProjectionBindingRegistry.java>) |
| NEW | [use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/binding/ProjectionReadView.java](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/binding/ProjectionReadView.java>) |
| NEW | [use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/binding/UseProjectionBinding.java](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/binding/UseProjectionBinding.java>) |
| NEW | [use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/csl/CslSchema.java](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/csl/CslSchema.java>) |
| NEW | [use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/csl/CslSchemaValidator.java](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/csl/CslSchemaValidator.java>) |
| NEW | [use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/csl/CslStructuralPolicy.java](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/csl/CslStructuralPolicy.java>) |
| NEW | [use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/csl/CslStructuralPolicyCompiler.java](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/csl/CslStructuralPolicyCompiler.java>) |
| NEW | [use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/csl/CslUseBinding.java](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/csl/CslUseBinding.java>) |
| NEW | [use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/csl/ScalarValueCodec.java](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/csl/ScalarValueCodec.java>) |
| NEW | [use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/evidence/MappingBundleCodec.java](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/evidence/MappingBundleCodec.java>) |
| NEW | [use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/evidence/SemanticConstraintResult.java](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/evidence/SemanticConstraintResult.java>) |
| NEW | [use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/evidence/SemanticJson.java](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/evidence/SemanticJson.java>) |
| NEW | [use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/evidence/SemanticMappingAttachment.java](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/evidence/SemanticMappingAttachment.java>) |
| NEW | [use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/evidence/SemanticVerdict.java](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/evidence/SemanticVerdict.java>) |
| NEW | [use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/evidence/SemanticVerificationEnvelope.java](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/evidence/SemanticVerificationEnvelope.java>) |
| NEW | [use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/evidence/TraceComplement.java](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/evidence/TraceComplement.java>) |
| NEW | [use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/exploration/BoundedExplorer.java](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/exploration/BoundedExplorer.java>) |
| NEW | [use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/exploration/ConformanceSummary.java](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/exploration/ConformanceSummary.java>) |
| NEW | [use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/exploration/ExplorationBounds.java](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/exploration/ExplorationBounds.java>) |
| NEW | [use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/exploration/TransitionProfile.java](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/exploration/TransitionProfile.java>) |
| NEW | [use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/exploration/WitnessConcretizer.java](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/exploration/WitnessConcretizer.java>) |
| NEW | [use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/istar/IntentionalMarking.java](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/istar/IntentionalMarking.java>) |
| NEW | [use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/istar/MoiseRequirementBinder.java](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/istar/MoiseRequirementBinder.java>) |
| NEW | [use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/istar/OPlanOutcomeMapper.java](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/istar/OPlanOutcomeMapper.java>) |
| NEW | [use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/istar/RequirementBindingResolver.java](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/istar/RequirementBindingResolver.java>) |
| NEW | [use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/istar/RequirementModel.java](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/istar/RequirementModel.java>) |
| NEW | [use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/istar/RequirementViewSnapshot.java](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/istar/RequirementViewSnapshot.java>) |
| NEW | [use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/istar/TaskBinding.java](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/istar/TaskBinding.java>) |
| NEW | [use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/istar/TemporalGoalMonitor.java](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/istar/TemporalGoalMonitor.java>) |
| NEW | [use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/mapping/FunctionalMappingRules.java](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/mapping/FunctionalMappingRules.java>) |
| NEW | [use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/mapping/JasonAbstractionPolicy.java](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/mapping/JasonAbstractionPolicy.java>) |
| NEW | [use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/mapping/MappingBundle.java](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/mapping/MappingBundle.java>) |
| NEW | [use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/mapping/MappingRuleCatalog.java](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/mapping/MappingRuleCatalog.java>) |
| NEW | [use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/mapping/NormalizedSourceModel.java](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/mapping/NormalizedSourceModel.java>) |
| NEW | [use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/mapping/OrganizationalMappingRules.java](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/mapping/OrganizationalMappingRules.java>) |
| NEW | [use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/mapping/SemanticTransformationService.java](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/mapping/SemanticTransformationService.java>) |
| NEW | [use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/mapping/SourceModelNormalizer.java](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/mapping/SourceModelNormalizer.java>) |
| NEW | [use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/mde/JacamoEcoreSourceLoader.java](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/mde/JacamoEcoreSourceLoader.java>) |
| NEW | [use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/model/ExecutionDescriptor.java](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/model/ExecutionDescriptor.java>) |
| NEW | [use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/normative/NormLifecycleMonitor.java](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/normative/NormLifecycleMonitor.java>) |
| NEW | [use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/normative/NormPolicyProfile.java](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/normative/NormPolicyProfile.java>) |
| NEW | [use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/normative/NormativeObservation.java](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/normative/NormativeObservation.java>) |
| NEW | [use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/normative/NormativeObservationMapper.java](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/normative/NormativeObservationMapper.java>) |
| NEW | [use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/normative/NormativeUseBinding.java](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/normative/NormativeUseBinding.java>) |
| NEW | [use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/observer/ExecutionObservation.java](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/observer/ExecutionObservation.java>) |
| NEW | [use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/observer/FunctionalObservation.java](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/observer/FunctionalObservation.java>) |
| NEW | [use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/observer/FunctionalObservationMapper.java](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/observer/FunctionalObservationMapper.java>) |
| NEW | [use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/observer/FunctionalUseBinding.java](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/observer/FunctionalUseBinding.java>) |
| NEW | [use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/observer/MentalObservationMapper.java](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/observer/MentalObservationMapper.java>) |
| NEW | [use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/observer/MentalStateObservation.java](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/observer/MentalStateObservation.java>) |
| NEW | [use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/observer/MentalUseBinding.java](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/observer/MentalUseBinding.java>) |
| NEW | [use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/ocl/CompiledSemanticConstraint.java](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/ocl/CompiledSemanticConstraint.java>) |
| NEW | [use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/ocl/ConstraintCoverageResolver.java](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/ocl/ConstraintCoverageResolver.java>) |
| NEW | [use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/ocl/JasonConditionFragment.java](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/ocl/JasonConditionFragment.java>) |
| NEW | [use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/ocl/MentalPredicateBinding.java](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/ocl/MentalPredicateBinding.java>) |
| NEW | [use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/ocl/OclEvaluationAdapter.java](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/ocl/OclEvaluationAdapter.java>) |
| NEW | [use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/ocl/SemanticConstraintRegistry.java](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/ocl/SemanticConstraintRegistry.java>) |
| NEW | [use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/ocl/SemanticInvariantEvaluator.java](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/ocl/SemanticInvariantEvaluator.java>) |
| NEW | [use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/ocl/SemanticOclCompiler.java](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/ocl/SemanticOclCompiler.java>) |
| NEW | [use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/profile/CslMonitorConfiguration.java](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/profile/CslMonitorConfiguration.java>) |
| NEW | [use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/profile/SemanticProjectionProfile.java](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/profile/SemanticProjectionProfile.java>) |
| NEW | [use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/profile/SemanticsProfile.java](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/profile/SemanticsProfile.java>) |
| NEW | [use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/profile/UnsupportedFeature.java](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/profile/UnsupportedFeature.java>) |
| NEW | [use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/roundtrip/SourceLens.java](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/roundtrip/SourceLens.java>) |
| NEW | [use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/roundtrip/SourcePatchPlan.java](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/roundtrip/SourcePatchPlan.java>) |
| NEW | [use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/roundtrip/SupportedEditProfile.java](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/roundtrip/SupportedEditProfile.java>) |
| NEW | [use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/runtime/InvocationBoundary.java](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/runtime/InvocationBoundary.java>) |
| NEW | [use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/runtime/InvocationCoveragePolicy.java](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/runtime/InvocationCoveragePolicy.java>) |
| NEW | [use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/runtime/OPlanTransitionMonitor.java](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/runtime/OPlanTransitionMonitor.java>) |
| NEW | [use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/runtime/ObservationCoverage.java](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/runtime/ObservationCoverage.java>) |
| NEW | [use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/runtime/SemanticObservationEnvelope.java](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/runtime/SemanticObservationEnvelope.java>) |
| NEW | [use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/runtime/SemanticObservationMapper.java](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/runtime/SemanticObservationMapper.java>) |
| NEW | [use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/runtime/SemanticRuntimeVerifier.java](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/runtime/SemanticRuntimeVerifier.java>) |
| MODIFIED | [use-plugin/src/main/java/org/tzi/use/plugins/jacamo/ui/JaCaMoWorkbenchPanel.java](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/ui/JaCaMoWorkbenchPanel.java>) |
| NEW | [use-plugin/src/main/java/org/tzi/use/plugins/jacamo/ui/SemanticEvidencePanel.java](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/ui/SemanticEvidencePanel.java>) |
| NEW | [use-plugin/src/main/resources/org/tzi/use/plugins/jacamo/semantics/capabilities.json](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/resources/org/tzi/use/plugins/jacamo/semantics/capabilities.json>) |
| NEW | [use-plugin/src/test/java/org/jacamo/bridge/adapter/CslRuntimeTestRegistry.java](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/test/java/org/jacamo/bridge/adapter/CslRuntimeTestRegistry.java>) |
| MODIFIED | [use-plugin/src/test/java/org/tzi/use/plugins/jacamo/bridge/CanonicalCasesBridgePipelineTest.java](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/test/java/org/tzi/use/plugins/jacamo/bridge/CanonicalCasesBridgePipelineTest.java>) |
| MODIFIED | [use-plugin/src/test/java/org/tzi/use/plugins/jacamo/bridge/SeparateJvmBridgeTest.java](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/test/java/org/tzi/use/plugins/jacamo/bridge/SeparateJvmBridgeTest.java>) |
| MODIFIED | [use-plugin/src/test/java/org/tzi/use/plugins/jacamo/codegrounded/AuctionDomainFixture.java](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/test/java/org/tzi/use/plugins/jacamo/codegrounded/AuctionDomainFixture.java>) |
| MODIFIED | [use-plugin/src/test/java/org/tzi/use/plugins/jacamo/codegrounded/AuctionExternalOclProfileTest.java](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/test/java/org/tzi/use/plugins/jacamo/codegrounded/AuctionExternalOclProfileTest.java>) |
| MODIFIED | [use-plugin/src/test/java/org/tzi/use/plugins/jacamo/codegrounded/DomainRuntimeProjectionTest.java](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/test/java/org/tzi/use/plugins/jacamo/codegrounded/DomainRuntimeProjectionTest.java>) |
| MODIFIED | [use-plugin/src/test/java/org/tzi/use/plugins/jacamo/codegrounded/NativeRuntimeReplaySmokeMain.java](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/test/java/org/tzi/use/plugins/jacamo/codegrounded/NativeRuntimeReplaySmokeMain.java>) |
| NEW | [use-plugin/src/test/java/org/tzi/use/plugins/jacamo/codegrounded/runtime/NativeInvocationSemanticsTest.java](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/test/java/org/tzi/use/plugins/jacamo/codegrounded/runtime/NativeInvocationSemanticsTest.java>) |
| NEW | [use-plugin/src/test/java/org/tzi/use/plugins/jacamo/codegrounded/runtime/NativeSemanticFailureTest.java](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/test/java/org/tzi/use/plugins/jacamo/codegrounded/runtime/NativeSemanticFailureTest.java>) |
| NEW | [use-plugin/src/test/java/org/tzi/use/plugins/jacamo/evidence/CanonicalReferenceFixture.java](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/test/java/org/tzi/use/plugins/jacamo/evidence/CanonicalReferenceFixture.java>) |
| NEW | [use-plugin/src/test/java/org/tzi/use/plugins/jacamo/semantics/BoundedExplorerTest.java](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/test/java/org/tzi/use/plugins/jacamo/semantics/BoundedExplorerTest.java>) |
| NEW | [use-plugin/src/test/java/org/tzi/use/plugins/jacamo/semantics/CslCounterArtifact.java](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/test/java/org/tzi/use/plugins/jacamo/semantics/CslCounterArtifact.java>) |
| NEW | [use-plugin/src/test/java/org/tzi/use/plugins/jacamo/semantics/CslGroupBoard.java](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/test/java/org/tzi/use/plugins/jacamo/semantics/CslGroupBoard.java>) |
| NEW | [use-plugin/src/test/java/org/tzi/use/plugins/jacamo/semantics/CslNativeBindingTest.java](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/test/java/org/tzi/use/plugins/jacamo/semantics/CslNativeBindingTest.java>) |
| NEW | [use-plugin/src/test/java/org/tzi/use/plugins/jacamo/semantics/CslSchemaTest.java](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/test/java/org/tzi/use/plugins/jacamo/semantics/CslSchemaTest.java>) |
| NEW | [use-plugin/src/test/java/org/tzi/use/plugins/jacamo/semantics/CslSchemeBoard.java](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/test/java/org/tzi/use/plugins/jacamo/semantics/CslSchemeBoard.java>) |
| NEW | [use-plugin/src/test/java/org/tzi/use/plugins/jacamo/semantics/CslStructuralPolicyTest.java](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/test/java/org/tzi/use/plugins/jacamo/semantics/CslStructuralPolicyTest.java>) |
| NEW | [use-plugin/src/test/java/org/tzi/use/plugins/jacamo/semantics/CslVerticalSliceTest.java](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/test/java/org/tzi/use/plugins/jacamo/semantics/CslVerticalSliceTest.java>) |
| NEW | [use-plugin/src/test/java/org/tzi/use/plugins/jacamo/semantics/DetailedVerdictTest.java](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/test/java/org/tzi/use/plugins/jacamo/semantics/DetailedVerdictTest.java>) |
| NEW | [use-plugin/src/test/java/org/tzi/use/plugins/jacamo/semantics/EcoreSourceLoaderTest.java](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/test/java/org/tzi/use/plugins/jacamo/semantics/EcoreSourceLoaderTest.java>) |
| NEW | [use-plugin/src/test/java/org/tzi/use/plugins/jacamo/semantics/FunctionalLiveBoardTest.java](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/test/java/org/tzi/use/plugins/jacamo/semantics/FunctionalLiveBoardTest.java>) |
| NEW | [use-plugin/src/test/java/org/tzi/use/plugins/jacamo/semantics/FunctionalObservationTest.java](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/test/java/org/tzi/use/plugins/jacamo/semantics/FunctionalObservationTest.java>) |
| NEW | [use-plugin/src/test/java/org/tzi/use/plugins/jacamo/semantics/JasonAbstractionPolicyTest.java](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/test/java/org/tzi/use/plugins/jacamo/semantics/JasonAbstractionPolicyTest.java>) |
| NEW | [use-plugin/src/test/java/org/tzi/use/plugins/jacamo/semantics/JasonTaskConfigurationTest.java](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/test/java/org/tzi/use/plugins/jacamo/semantics/JasonTaskConfigurationTest.java>) |
| NEW | [use-plugin/src/test/java/org/tzi/use/plugins/jacamo/semantics/MentalStateProjectionTest.java](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/test/java/org/tzi/use/plugins/jacamo/semantics/MentalStateProjectionTest.java>) |
| NEW | [use-plugin/src/test/java/org/tzi/use/plugins/jacamo/semantics/NormativeObservationTest.java](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/test/java/org/tzi/use/plugins/jacamo/semantics/NormativeObservationTest.java>) |
| NEW | [use-plugin/src/test/java/org/tzi/use/plugins/jacamo/semantics/OPlanSemanticsTest.java](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/test/java/org/tzi/use/plugins/jacamo/semantics/OPlanSemanticsTest.java>) |
| NEW | [use-plugin/src/test/java/org/tzi/use/plugins/jacamo/semantics/ObservationEnvelopeTest.java](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/test/java/org/tzi/use/plugins/jacamo/semantics/ObservationEnvelopeTest.java>) |
| NEW | [use-plugin/src/test/java/org/tzi/use/plugins/jacamo/semantics/ObserverIdentityLifecycleTest.java](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/test/java/org/tzi/use/plugins/jacamo/semantics/ObserverIdentityLifecycleTest.java>) |
| NEW | [use-plugin/src/test/java/org/tzi/use/plugins/jacamo/semantics/OclSemanticBackendTest.java](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/test/java/org/tzi/use/plugins/jacamo/semantics/OclSemanticBackendTest.java>) |
| NEW | [use-plugin/src/test/java/org/tzi/use/plugins/jacamo/semantics/RuntimeMetricDistributionTest.java](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/test/java/org/tzi/use/plugins/jacamo/semantics/RuntimeMetricDistributionTest.java>) |
| NEW | [use-plugin/src/test/java/org/tzi/use/plugins/jacamo/semantics/SemanticBindingTest.java](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/test/java/org/tzi/use/plugins/jacamo/semantics/SemanticBindingTest.java>) |
| NEW | [use-plugin/src/test/java/org/tzi/use/plugins/jacamo/semantics/SemanticCapabilityManifestTest.java](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/test/java/org/tzi/use/plugins/jacamo/semantics/SemanticCapabilityManifestTest.java>) |
| NEW | [use-plugin/src/test/java/org/tzi/use/plugins/jacamo/semantics/SemanticMappingReplayTest.java](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/test/java/org/tzi/use/plugins/jacamo/semantics/SemanticMappingReplayTest.java>) |
| NEW | [use-plugin/src/test/java/org/tzi/use/plugins/jacamo/semantics/SemanticPackagedReplayIT.java](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/test/java/org/tzi/use/plugins/jacamo/semantics/SemanticPackagedReplayIT.java>) |
| NEW | [use-plugin/src/test/java/org/tzi/use/plugins/jacamo/semantics/SemanticTransformationTest.java](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/test/java/org/tzi/use/plugins/jacamo/semantics/SemanticTransformationTest.java>) |
| NEW | [use-plugin/src/test/java/org/tzi/use/plugins/jacamo/semantics/SemanticsProfileTest.java](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/test/java/org/tzi/use/plugins/jacamo/semantics/SemanticsProfileTest.java>) |
| NEW | [use-plugin/src/test/java/org/tzi/use/plugins/jacamo/semantics/SharedStateAcceptanceTest.java](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/test/java/org/tzi/use/plugins/jacamo/semantics/SharedStateAcceptanceTest.java>) |
| NEW | [use-plugin/src/test/java/org/tzi/use/plugins/jacamo/semantics/SourceLensTest.java](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/test/java/org/tzi/use/plugins/jacamo/semantics/SourceLensTest.java>) |
| NEW | [use-plugin/src/test/java/org/tzi/use/plugins/jacamo/semantics/TemporalGoalMonitorTest.java](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/test/java/org/tzi/use/plugins/jacamo/semantics/TemporalGoalMonitorTest.java>) |
| NEW | [use-plugin/src/test/java/org/tzi/use/plugins/jacamo/semantics/VerificationLoadArtifact.java](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/test/java/org/tzi/use/plugins/jacamo/semantics/VerificationLoadArtifact.java>) |
| NEW | [use-plugin/src/test/java/org/tzi/use/plugins/jacamo/semantics/VerificationLoadScenarioTest.java](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/test/java/org/tzi/use/plugins/jacamo/semantics/VerificationLoadScenarioTest.java>) |
| NEW | [use-plugin/src/test/java/org/tzi/use/plugins/jacamo/ui/SemanticEvidencePanelTest.java](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/test/java/org/tzi/use/plugins/jacamo/ui/SemanticEvidencePanelTest.java>) |
| NEW | [use-plugin/src/test/resources/semantics/functional/functional.jcm](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/test/resources/semantics/functional/functional.jcm>) |
| NEW | [use-plugin/src/test/resources/semantics/functional/src/agt/a.asl](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/test/resources/semantics/functional/src/agt/a.asl>) |
| NEW | [use-plugin/src/test/resources/semantics/functional/src/org/functional.xml](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/test/resources/semantics/functional/src/org/functional.xml>) |
| NEW | [use-plugin/src/test/resources/semantics/profile-v1/monitor.jcm](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/test/resources/semantics/profile-v1/monitor.jcm>) |
| NEW | [use-plugin/src/test/resources/semantics/profile-v1/src/agt/a.asl](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/test/resources/semantics/profile-v1/src/agt/a.asl>) |
| NEW | [use-plugin/src/test/resources/semantics/profile-v1/src/org/monitor.xml](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/test/resources/semantics/profile-v1/src/org/monitor.xml>) |
| NEW | [use-plugin/src/test/resources/semantics/structural/src/agt/a.asl](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/test/resources/semantics/structural/src/agt/a.asl>) |
| NEW | [use-plugin/src/test/resources/semantics/structural/src/org/structural.xml](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/test/resources/semantics/structural/src/org/structural.xml>) |
| NEW | [use-plugin/src/test/resources/semantics/structural/structural.jcm](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/test/resources/semantics/structural/structural.jcm>) |

## 12. Reproduce và sử dụng

PowerShell, từ D:/_CODE_BANK/Project_/08_Thesis/use. Các lệnh dưới đây là những command shape đã dùng; log cụ thể ở bảng test. Chọn exact simple test names hoặc *Test để tránh discovery rỗng.

~~~powershell
$env:JAVA_HOME='D:\DevTools\Java\jdk-21.0.5'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
$mvn='D:\DevTools\apache-maven-3.9.9-bin\apache-maven-3.9.9\bin\mvn.cmd'

& $mvn -o -fae -pl jacamo-bridge-contract,jacamo-bridge-jacamo,use-core,use-gui,use-plugin test-compile org.apache.maven.plugins:maven-surefire-plugin:3.2.5:test '-Dtest=*Test' '-Dsurefire.failIfNoSpecifiedTests=false' *> target/semantics-final-acceptance.log

& $mvn -o -pl use-plugin -am package '-DskipTests' *> target/semantics-final-package.log

& $mvn -o -pl use-plugin -am test-compile org.apache.maven.plugins:maven-surefire-plugin:3.2.5:test '-Dtest=SharedStateAcceptanceTest,VerificationLoadScenarioTest,NativeInvocationSemanticsTest,NativeSemanticFailureTest,NormativeObservationTest,NativeRuntimePackagedReplayIT,SemanticPackagedReplayIT,CslVerticalSliceTest' '-Dsurefire.failIfNoSpecifiedTests=false' *> target/semantics-final-packaged-smoke.log
~~~

Selected gates dùng cùng reactor test-compile + explicit Surefire goal; ví dụ:

~~~powershell
& $mvn -o -pl use-plugin -am test-compile org.apache.maven.plugins:maven-surefire-plugin:3.2.5:test '-Dtest=CslVerticalSliceTest,OclSemanticBackendTest,TemporalGoalMonitorTest,NormativeObservationTest' '-Dsurefire.failIfNoSpecifiedTests=false'
& $mvn -o -pl use-plugin -am test-compile org.apache.maven.plugins:maven-surefire-plugin:3.2.5:test '-Dtest=BoundedExplorerTest,SourceLensTest,EcoreSourceLoaderTest' '-Dsurefire.failIfNoSpecifiedTests=false'
& $mvn -o -pl use-plugin -am test-compile org.apache.maven.plugins:maven-surefire-plugin:3.2.5:test '-Dtest=VerificationLoadScenarioTest,RuntimeMetricDistributionTest' '-Dsurefire.failIfNoSpecifiedTests=false'
~~~

Maven offline đòi các pinned dependencies đã có trong local repository. USE distribution là host khi chạy installed plugin; Process-B replay smoke loại JaCaMo/Jason/CArtAgO/MOISE/NPL implementation jars khỏi classpath. Package test chạy từ plugin ZIP vừa build, kiểm actual code source location và native state/result hashes. Không dùng IDE classpath làm bằng chứng release isolation.

Opt-in cấu hình production: đặt Java system properties use.jacamo.semantics.profile=CSL_OBSERVER_V1 hoặc CSL_OBSERVER_V2 và use.jacamo.semantics.config=<absolute JSON path>. JSON được serialize bằng CslMonitorConfiguration.bytes(), phải có schema, explicit source correspondence, authored conditions/bindings; không tự bind runtime instance bằng display name. CLI/GUI launcher phải truyền properties vào đúng USE JVM. Đường default NATIVE_CURRENT không đòi config này. Không có GUI authoring editor mới cho CSL schema/requirements; evidence UI là read-only.

Entry point để tái lập G1: CslVerticalSliceTest.realArtifactAndAcceptedGroupBoardFeedSameSystemInvariantTransitionAchieveAndReplay. Fixture tạo real CArtAgO artifact và GroupBoard, thực hiện official adoptRole/increment/incrementTwo/property mutations, lấy adapter observations và project vào native MSystem. Không phải demo mô phỏng chỉ tạo USE objects. Mốc p=0→1→-1 giữ FAIL và ACHIEVE witness; 0→2 tạo negative @pre; malformed cut rollback, property removal và artifact/group reincarnation đều có assertion/replay. Đây là accepted adapter-boundary slice; không lấy nó làm bằng chứng toàn bộ Jason agent program đúng.

## 13. Giới hạn và research issues còn lại

| Mục | Trạng thái | Lý do / khả năng mở rộng |
|---|---|---|
| Global atomic MAS snapshot | UNSUPPORTED | Official runtimes không cung cấp một shared transaction/clock; local published cuts và vector watermarks có provenance riêng |
| Full CSL↔MOISE compatibility/lifecycle | UNSUPPORTED | Scoped/directional/extending compatibility, source collective-player correspondence và composition destruction cần formal mapping mới; declaration ownership không đủ |
| Jason ActionExec↔CArtAgO OpId | UNSUPPORTED | Không có proven public causal token trong source đã kiểm; cần producer instrumentation/capability mới, không name/time pairing |
| Java nonvoid operation result từ logger | UNSUPPORTED | Logger hiện không cung cấp typed return value; USE backend có thể evaluate result khi được cấp đúng nguồn |
| General Jason logical consequence | UNSUPPORTED | Variables, rules, internal predicates/custom belief bases không được thay bằng stored set membership |
| Full independent NPL equivalence / sanctions | UNSUPPORTED | Observer reported lifecycle và trace consistency khác derivation; recorded clock không là replay clock; maintenance generator limitation của source còn nguyên |
| General successor synthesis/model checking | UNSUPPORTED | Arbitrary Java/ASL/native scheduling không suy từ state contracts; finite relation extension có bounds/assumptions riêng |
| General inverse / semantic round-trip | UNSUPPORTED | Missing executable behavior, property types/values và causal semantics không reconstruct từ target; selected structural lens chỉ normalized syntax |
| Full live MAS throughput, open-loop arrival-rate SLA | PARTIAL | Có finite adapter-contract workload, phase percentiles và bounded queues/history; không có uninstrumented-vs-instrumented full MAS slowdown measurement |
| GUI manual visual inspection | PARTIAL | Automated native/UI contract tests và evidence-panel assertions; không coi đây là manual pixel QA mọi Swing flow |

OCL evaluation và native publication vẫn chạy trong serialized writer/EDT: workload lớn có thể ảnh hưởng responsiveness. Metrics đo wall-time có instrumentation; heap delta gồm GC, không là retained-heap/leak proof. Ring timing samples, bounded queues, retention và activation crosswalk có bounds cụ thể; journal on-disk tăng theo trace cho đến quota/gap. Không cắt/gộp temporal events để làm benchmark nhanh. Timeouts của explorer được kiểm giữa evaluations; không claim native evaluator có hard interrupt deadline.

Future work cần formal specification và additional source capability: causal observer token giữa Jason và CArtAgO; consistent multi-source cut protocol với rollback/pausing được chứng minh; independent normative interpreter oracle; systematic complete successor abstraction/refinement; supported JCM/MOISE/ASL edit lenses; long-duration live rate and retained-heap profiling. Các mục này không được đánh dấu DONE bởi compilation hoặc sample trace.

## 14. Artifact/hash audit và trạng thái cuối

[Per-run acceptance manifest](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/target/semantic-acceptance.json>) nối named claims → phase/tasks/matrix → test reports/log hashes → source/profile/model assets và release checksums. [Release ZIP](<D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/target/use-jacamo-plugin-1.0.1-v2-frozen.zip>) SHA-256: 984bb6c5f6455cf27f54cc7b493cc6bd2edee4109fbd5b7a85323196b99ea15c. Built JAR và staged JAR cùng SHA-256 978df371e8726649bc5174ce85902734a5897017e4aa624590b561e87b753272. Ba class RuntimeVerificationCoordinator, NativeOperationCheckpoints, SemanticRuntimeVerifier trong JAR đã đối chiếu byte hash bằng target/classes hiện tại.

Non-document changed-source/test inventory hash: 0ffc4fd7ab623a7b337c03558bd84f348a02afada688a60416657eea6ecbb4a7. Báo cáo, ledger và per-run manifest nằm trong USE. Package build có test skip theo lệnh; acceptance lấy từ các run đã thực thi riêng ở §9, không từ build skip. Target logs/bundles/manifest là generated artifacts và có thể bị clean; cần giữ/copy release/evidence bundle khi dùng cho luận văn.

Đã đối chiếu lại git diff --check; branch vẫn main, không commit/reset. Bốn file có thay đổi sẵn của jacamo/use4Goal giữ nguyên SHA-256 so với baseline. Desktop report.md và jacamo_v2.ecore giữ nguyên hashes tại §2. Không sửa hoặc build hai reference repositories.

Các task đã được duyệt hết Phase 0–20: {"DONE":68,"UNSUPPORTED":6,"PARTIAL":2}. Các PARTIAL còn lại thuộc measurement live MAS chưa chạy; các UNSUPPORTED thuộc những semantics/source capability được nêu cụ thể, không là placeholder implementation. Bộ observer verification và hai extension mang tên/version được nghiệm thu đúng phạm vi; không chứng nhận toàn bộ hành vi JaCaMo hoặc full bidirectionality.
