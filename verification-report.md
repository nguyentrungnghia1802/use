# Báo cáo kiểm thử độc lập JaCaMo → CSL + iStar + OCL → USE

Ngày audit: 16/10/2026, Asia/Bangkok. Nguồn được kiểm: working tree hiện tại của USE tại HEAD 00b27a1f5385720a135e2adbc65c8523ffa4e9bd. Đây là audit đọc/kiểm thử; không sửa implementation, test có sẵn, implementation-plan.md hoặc implementation-report.md.

## 1. Executive verdict

**C. PARTIALLY VERIFIED.** Có implementation chạy thật cho phép chiếu có kiểu, native USE/OCL, monitor temporal, replay, explorer hữu hạn và lens cấu trúc. Tuy nhiên, các claim về fail-safe synchronization và coverage bị phản ví dụ thực thi bác bỏ. Không thể nghiệm thu toàn bộ named supported profile ở trạng thái hiện tại.

Các phát hiện chặn nghiệm thu:

| ID | Severity | Kết quả độc lập |
|---|---|---|
| D01 | CRITICAL | Snapshot cũ cùng run được nhận sau event mới; p=-1/FAIL bị thay bằng p=0/CURRENT_OBSERVED/PASS. Replay cũng chứng nhận complete cho chuỗi này. |
| D02 | CRITICAL | AtomicAgent.allInstances()->size() = 1 trả PASS và intentional SATISFIED khi Jason population chỉ PARTIAL. |
| D03 | HIGH | NORM_ACTIVATED của một activation mới bị production BridgeMirrorStateMachine từ chối như unknown incarnation; live subscription chuyển RESYNC_REQUIRED/ngắt. |
| D04 | HIGH | Task bind player b được SATISFIED bởi invocation thực của a trên cùng artifact. Matching kiểm contract/self nhưng bỏ performer. Contract OCL của operation vẫn đúng; không đủ chứng minh Task của actor đã bind. |
| D05 | LOW | Missing complement của SourceLens được API nhận vào Projection nhưng put ném NullPointerException, thay vì conflict/unsupported có cấu trúc. |
| D06 | MEDIUM | NPL binary là 0.6.1 trong adapter tests, 0.6 trong plugin/live; sourceVersion hardcode API-profile 0.6.1 cần phân biệt actual artifact version. |
| D07 | MEDIUM | Actual CArtAgO 3.1 await không phát suspended/resumed callbacks; consumer phase tests không chứng minh producer coverage. |

Bằng chứng tích cực cũng được chạy lại độc lập: clean reactor 662 test, 2 installed IT, 198 legacy USE tests; oracle temporal mới; 1.000 lượt hai writer cạnh tranh; host/installed @pre; actual MAS với hai agents, hai roles, scheme/mission và p=0→1→-1. Các count này **không phủ định các defect**. Phần live snapshot/OCL/ACHIEVE/replay hoạt động; full production subscription gate FAILED do D03.

## 2. Environment / revisions / tính toàn vẹn nguồn

### 2.1. Nguồn và cách sử dụng

Đã nạp toàn bộ ba tài liệu yêu cầu, đọc các section/ledger/matrix/acceptance/source index và đối chiếu source hiện tại. [R] là chuẩn semantics; [PLAN] là hợp đồng thiết kế; [CLAIMS] chỉ là danh sách claim. Những câu trong tài liệu lịch sử như “chưa chạy build” mô tả lượt lập kế hoạch trước, không được dùng để thay thế yêu cầu audit mới. PDF được trích đủ 6 trang; Table I/Definitions 3–7 còn được đối chiếu bằng ảnh trang 3–4. Đã dùng skill pdf để đọc và kiểm tra công thức; không sửa PDF.

Quy ước bằng chứng trong báo cáo: **Theo source USE/adapter** là kết luận từ code hiện tại; **Theo report.md/PDF** là yêu cầu/semantics tham chiếu; **Quan sát audit** là kết quả chạy mới; **Suy luận audit** được ghi rõ khi vượt quan sát hữu hạn. Không coi tên test hoặc một mapping rule trong catalog là proof của behavior.

| Thành phần | Giá trị đã kiểm |
|---|---|
| USE | main; HEAD 00b27a1f5385720a135e2adbc65c8523ffa4e9bd; trước audit đã có 39 tracked modifications và 133 untracked files |
| JaCaMo, chỉ đọc | HEAD 04fc08df259d94045f6cfc95637d56faa1cb33b2; hai thay đổi auction có sẵn |
| use4Goal, chỉ đọc | HEAD 6f3e73801239fd8bfb94ca8d04ba531b903c0ac9; hai thay đổi Classroom có sẵn |
| Java | D:/DevTools/Java/jdk-21.0.5; 21.0.5+9-LTS-239 |
| Maven | D:/DevTools/apache-maven-3.9.9-bin/apache-maven-3.9.9/bin/mvn.cmd; 3.9.9 |
| OS / CPU | Windows 11 amd64; 16 logical processors; benchmark JVM max heap 4.219.469.824 bytes |
| Host / plugin | USE 7.5.0; plugin 1.0.1; contract 1.1.0 wire capability trong live handshake |
| JaCaMo dependencies trên plugin test classpath | JaCaMo 1.3.1, Jason 3.3.2, CArtAgO/JaCa 3.1, MOISE 1.1, NPL 0.6 |
| NPL trong official-adapter test JVM | npl-0.6.1.jar; cùng Jason 3.3.2; xem D06 |
| EMF production dependencies | ecore/xmi 2.39.0; emf.common 2.42.0, theo actual plugin test classpath |

Đây là clean **working-tree build**, không phải pristine commit checkout: phần implementation mới chưa commit là đối tượng audit. Không tạo branch/commit/reset. Kiểm SHA-256 của toàn bộ 2.906 tracked + untracked files tồn tại trước audit không thấy thay đổi byte. Build có cập nhật ignored targets và staged plugin JAR trong USE; những side effects này được phân biệt với source edits. [PRESERVE]

### 2.2. Hash tài liệu và artifact

| Input | SHA-256 |
|---|---|
| Desktop report.md | dd7c2a70514b8fbdac8945f3e1270fe44c69a623f6161284cf8261349941da82 |
| implementation-plan.md | 91a30554e14ed6973821d9470a32c1fdba495b15409a3f79887947e4bee58691 |
| implementation-report.md | c2776a66b852a444783a920021e53824247cc6c82f70d1768317e7203b97115a |
| jacamo_v2.ecore | 4ae51638a078f0a933982844063993084f17d420694ac8a868d95b9df063c35c |
| PDF 6 trang | 398a8b1f803971b151af46a0ce689c4cb5f3e75b8f9c31df0abf60f1536f3dab |
| Fresh plugin ZIP | 66a703aabbe2a36317a10ff2a22ccbb079d335c7079d56850b07ec7e60ddbfe7 |
| Fresh shaded JAR / staged JAR | 6cb9b7756db1150690eb2968861a50e0c4ca1b3a40039d564286937f8f2e488c |
| Fresh USE GUI distribution JAR | 58aca2de4201fa6a25b653aee2e28dd5de07ef675527de0f89de644e81cfa052 |

Hash ZIP/JAR khác release hash của lượt implementation là bình thường khi rebuild; chỉ các hash mới trên được dùng làm bằng chứng binary audit. Danh sách source baseline và protected-input hashes ở [BASELINE], [PRESERVE]; tên artifact build thực là use-plugin/target/use-plugin-1.0.1.jar, được stage thành use-jacamo-plugin-1.0.1.jar.

## 3. Clean build result

Đã xác minh resolved paths nằm trong repository USE trước Maven clean. Clean xóa build artifacts của root và sáu modules; không xóa source. Một lần chuẩn bị danh sách path ban đầu vướng Join-Path với chuỗi rỗng, chưa thực hiện xóa; đã sửa chính command audit rồi chạy Maven clean thành công. Không có sửa production.

| Lần chạy mới | Kết quả | Evidence |
|---|---|---|
| Maven offline clean | VERIFIED; tất cả 7 reactor projects SUCCESS, 3,383 s; hoàn tất 12:20:37 +07 | [BASELINE] cleanProof |
| Clean compile + explicit Surefire 3.2.5 *Test | VERIFIED; BUILD SUCCESS; 5 phút 39 s; 12:26:29 +07 | [CLEANLOG], [COUNTS] |
| Fresh package -DskipTests | VERIFIED; 52,187 s; 12:27:32 +07 | [PACKAGELOG], [PKGHASH] |
| Reactor test-compile + 2 installed IT | VERIFIED; BUILD SUCCESS; 1 phút 38 s; 12:29:55 +07 | [SMOKELOG] |
| Isolated added installed driver | VERIFIED; actual plugin code sources point to installed shaded JAR | [INSTALLED] |

Package với skipTests chỉ chứng minh packaging, không được cộng là tests đã chạy. Những lần biên dịch harness audit thiếu classpath hoặc sai cú pháp fixture đã được sửa **trong target**, không thay code được audit; không tính là implementation failures. Maven đã chạy tuần tự. Khi một command test-compile tái tạo classes, không dùng output compilation tạm thời của thời điểm đó làm kết luận missing production class.

## 4. Test inventory và mức fidelity

| Module / runner | Suites | Cases mới đếm | Failures | Errors | Skips |
|---|---:|---:|---:|---:|---:|
| jacamo-bridge-contract / Jupiter | 4 | 18 | 0 | 0 | 0 |
| jacamo-bridge-jacamo / Jupiter | 15 | 68 | 0 | 0 | 0 |
| use-core / Jupiter/ArchUnit | 3 | 14 | 0 | 0 | 0 |
| use-gui / Jupiter | 1 | 1 | 0 | 0 | 0 |
| use-plugin / Jupiter | 166 | 561 | 0 | 0 | 0 |
| Tổng clean *Test | 189 | **662** | **0** | **0** | **0** |
| Installed IT chạy riêng | 2 | **2** | 0 | 0 | 0 |
| Legacy core AllTests qua junit.textui.TestRunner | suite tổng hợp | **198** | 0 | 0 | 0 |

Union có 664 cases của claim cũ và thêm 198 legacy core cases ngoài Jupiter; không cộng các scoped reruns vào union. 198 legacy tests không được trình bày như 198 thesis semantics cases. Các lần textui đầu thiếu production module-path dependencies là setup errors của harness; runner cuối dùng clean core/test classes + đầy đủ dependency classpath và báo OK (198 tests). [LEGACY]

Bổ sung audit không sửa tests có sẵn:

| Oracle mới | Kết quả / giới hạn |
|---|---|
| AuditOracles.temporal | 4 temporal types × 64 Boolean traces × 6 boundaries = 1.536 checks; 256 reactivations; 27 AND/OR three-child vectors; VERIFIED |
| AuditOracles.stale | D01 tái hiện; FAILED semantic requirement |
| AuditOracles.partialPopulation | D02 tái hiện; FAILED coverage requirement |
| AuditOracles.concurrency | 1.000 paired rounds / 2.000 accepted events / 2.002 journal entries; replay endpoint equal; VERIFIED cho normalized contracts |
| AuditBackend | 26 checks gồm @pre 1→2/1→3, enum, collections, arithmetic, undefined, pre-copy, malformed expression, 7 finite branch relations; missing complement tái hiện D05 |
| AuditLiveMas | MAS thật, 2 agents và organization/environment; snapshot path đạt 0→1→-1; production subscription FAILED vì D03 |
| AuditRecordedFlow | 8 exact Cartago events từ MAS thật; increment POST PASS 0→1; Task b bị credit bởi operation a (D04); replay complete |
| AuditReplayInputs | 3 corruptions đều bị từ chối; gồm recomputed outer manifest hash |
| AuditInstalledMain | JVM mới: host JAR + test driver; production classes lấy từ installed plugin JAR; state/invariant/@pre/temporal/replay/complement qua được |

3.852 assertions của AuditOracles là các assertion bên trong một executable audit, không phải 3.852 JUnit test cases. Không gọi dữ liệu normalized synthetic là actual Jason scheduling. Tất cả 65 matrix anchors trong implementation-report được kiểm method tồn tại và tên testcase thực sự có trong fresh XML, kể cả L05 qua installed IT. Điều đó xác minh discovery, không tự xác minh toàn bộ yêu cầu của từng row. [ANCHORS]

## 5. Phase-by-phase verification

Mỗi phase được xét theo acceptance và phạm vi thực tế; không lấy ledger DONE làm oracle. Các subtask được bao phủ bằng 76 dòng ở mục 6, 65 required-matrix rows ở mục 28 và phân tích kỹ thuật ở mục 7–27. Proposed class names không nhất thiết phải trở thành class riêng: việc gộp trách nhiệm được chấp nhận nếu đường runtime và tests chứng minh chức năng đó.

| Phase | Reported | Verified | Kết luận độc lập |
|---|---|---|---|
| 0 Baseline | DONE | VERIFIED | Clean build/version/byte baseline hiện tại có evidence mới. |
| 1 Vertical slice | DONE | PARTIAL | Actual data/OCL/@pre/ACHIEVE/replay chạy; admission/coverage không fail-safe (D01/D02). |
| 2 CSL structure | DONE trong fragment | PARTIAL | Core structure đúng trong tests; scoped MOISE/lifecycle excluded có thật; global population proof còn D02. |
| 3 MDE mapping | DONE | VERIFIED | Loader, normalization, complement/codec, supplemented frontend đạt phạm vi đã công bố. Explicit target config vẫn bắt buộc. |
| 4 Functional | DONE trong observer | VERIFIED | Exact scheme UUID, arguments, commitments, reported waiting/enabled/satisfied; không independent normative goal derivation. |
| 5 OPlan | DONE trong abstraction | VERIFIED | Operator/order/predecessor giữ; outcome và executive history không bị nhập thành một AND. |
| 6 Jason execution | PARTIAL | PARTIAL | Structured current observations/static Task admission có; ActionExec→OpId causation unsupported. |
| 7 Beliefs | DONE trong ground fragment | PARTIAL | 12/10 coexist và removal verified; actual perception 10→12 toàn đường Jason chưa được nghiệm thu riêng. |
| 8 OCL | DONE | FAILED | Compiler/evaluator đúng các oracle đã chạy, nhưng permitted expression có false PASS do coverage D02. |
| 9 iStar | DONE | PARTIAL | Pure recurrence/truth tables đạt; Task performer binding D04 và input-quality D01/D02 ngăn blanket acceptance. |
| 10 Runtime adapter | DONE trong observation scope | PARTIAL | Local source cuts/DTO immutability có; NPL family chưa đi qua live mirror (D03). |
| 11 Identity/sync | DONE | FAILED | Object lifecycle tests đạt; same-run stale snapshot rewind là counterexample trực tiếp. |
| 12 Operations | PARTIAL | PARTIAL | Paired @pre/descriptor và overlap có; source await lifecycle không bảo đảm callbacks D07, result/causation excluded. |
| 13 NPL | PARTIAL | PARTIAL | Real NPL API/listener tests đạt; subscription failure/version split chưa được xử lý trong named flow. |
| 14 Native USE binding | DONE | VERIFIED | Cùng MModel/MSystem/writer; typed mutation và malformed rollback; no shadow live system. |
| 15 Evidence/replay | DONE | PARTIAL | Integrity/recomputation tốt; tái tạo cả verdict sai do D01/D04 nên không phải semantic certification độc lập. |
| 16 Finite explorer | DONE trong finite table | VERIFIED | Bounded finite abstraction được kiểm; không successor synthesis của JaCaMo. |
| 17 Lens | PARTIAL | PARTIAL | Structural laws cho non-null input đạt; D05; không official JCM/ASL/Java semantic inverse. |
| 18 Tests | DONE | PARTIAL | Count/discovery có thật; các required adversarial cases lọt regression. |
| 19 Performance | PARTIAL | PARTIAL | Cache/direct finite workload parity có; full live-MAS SLA/retained-heap chưa có. |
| 20 Final acceptance | DONE trong named deliverable | FAILED | Package hoạt động nhưng critical semantic defects và failed live stream còn mở. |

## 6. Task-by-task verification

Đếm độc lập đúng **76** task/gate: reported 68 DONE, 6 UNSUPPORTED, 2 PARTIAL. Verified distribution của audit: VERIFIED=41, FAILED=9, UNSUPPORTED=6, PARTIAL=20. “VERIFIED” ở một task hẹp không xóa defect của task/gate tích hợp. “UNSUPPORTED” là exclusion được xác nhận, không phải một case đã verified tương đương nguồn.

| Phase | Task | Reported status | Verified status | Evidence | Problems / giới hạn |
|---|---|---|---|---|---|
| 00 | P00-T01 | DONE | VERIFIED | Clean build + source baseline + SemanticsProfileTest | Baseline hiện tại là dirty working tree đã pin, không pristine commit. |
| 00 | P00-T02 | DONE | VERIFIED | Clean build + source baseline + SemanticsProfileTest | Baseline hiện tại là dirty working tree đã pin, không pristine commit. |
| 00 | P00-T03 | DONE | VERIFIED | Clean build + source baseline + SemanticsProfileTest | Baseline hiện tại là dirty working tree đã pin, không pristine commit. |
| 00 | P00-T04 | DONE | VERIFIED | Clean build + source baseline + SemanticsProfileTest | Baseline hiện tại là dirty working tree đã pin, không pristine commit. |
| 01 | P01-F1 | DONE | VERIFIED | CslSchemaTest/CslNativeBindingTest/CslVerticalSliceTest + AuditBackend + installed driver | Verified cho complete admitted observations; không bao gồm D01/D02. |
| 01 | P01-F2 | DONE | VERIFIED | CslSchemaTest/CslNativeBindingTest/CslVerticalSliceTest + AuditBackend + installed driver | Verified cho complete admitted observations; không bao gồm D01/D02. |
| 01 | P01-F3 | DONE | FAILED | AuditOracles.stale/partialPopulation; recorded-flow | D01 snapshot rewind, D02 false PASS và D04 Task actor attribution. |
| 01 | P01-F4 | DONE | VERIFIED | CslSchemaTest/CslNativeBindingTest/CslVerticalSliceTest + AuditBackend + installed driver | Verified cho complete admitted observations; không bao gồm D01/D02. |
| 01 | P01-F5 | DONE | VERIFIED | CslSchemaTest/CslNativeBindingTest/CslVerticalSliceTest + AuditBackend + installed driver | Verified cho complete admitted observations; không bao gồm D01/D02. |
| 01 | P01-F6 | DONE | VERIFIED | CslSchemaTest/CslNativeBindingTest/CslVerticalSliceTest + AuditBackend + installed driver | Verified cho complete admitted observations; không bao gồm D01/D02. |
| 01 | P01-F7 | DONE | FAILED | AuditOracles.stale/partialPopulation; recorded-flow | D01 snapshot rewind, D02 false PASS và D04 Task actor attribution. |
| 01 | P01-F8 | DONE | FAILED | AuditOracles.stale/partialPopulation; recorded-flow | D01 snapshot rewind, D02 false PASS và D04 Task actor attribution. |
| 01 | P01-REGRESSION | DONE | VERIFIED | CslSchemaTest/CslNativeBindingTest/CslVerticalSliceTest + AuditBackend + installed driver | Verified cho complete admitted observations; không bao gồm D01/D02. |
| 01 | P01-GATE | DONE | FAILED | AuditOracles.stale/partialPopulation; recorded-flow | D01 snapshot rewind, D02 false PASS và D04 Task actor attribution. |
| 02 | P02-T01 | DONE | VERIFIED | CslStructuralPolicyTest; fresh 65-anchor audit | CSL core + authored symmetric compatibility; không full MOISE policy. |
| 02 | P02-T02 | DONE | VERIFIED | CslStructuralPolicyTest; fresh 65-anchor audit | CSL core + authored symmetric compatibility; không full MOISE policy. |
| 02 | P02-T03 | UNSUPPORTED | UNSUPPORTED | OrganizationalMappingRules.derive; CslStructuralPolicyCompiler | Full scoped policy/composition/declaration-lifecycle nằm ngoài fragment; lý do semantics có thật. |
| 02 | P02-T04 | UNSUPPORTED | UNSUPPORTED | OrganizationalMappingRules.derive; CslStructuralPolicyCompiler | Full scoped policy/composition/declaration-lifecycle nằm ngoài fragment; lý do semantics có thật. |
| 02 | P02-GATE | DONE | PARTIAL | Structural tests pass; D02 coverage resolver | Core structure đạt; population-dependent proof chưa an toàn toàn profile. |
| 03 | P03-T01 | DONE | VERIFIED | EcoreSourceLoaderTest/SemanticTransformationTest/MappingBundleCodecTest | Là supplemented frontend + explicit configuration; không tự sinh tất cả target semantics từ Ecore. |
| 03 | P03-T02 | DONE | VERIFIED | EcoreSourceLoaderTest/SemanticTransformationTest/MappingBundleCodecTest | Là supplemented frontend + explicit configuration; không tự sinh tất cả target semantics từ Ecore. |
| 03 | P03-T03 | DONE | VERIFIED | EcoreSourceLoaderTest/SemanticTransformationTest/MappingBundleCodecTest | Là supplemented frontend + explicit configuration; không tự sinh tất cả target semantics từ Ecore. |
| 03 | P03-T04 | DONE | VERIFIED | EcoreSourceLoaderTest/SemanticTransformationTest/MappingBundleCodecTest | Là supplemented frontend + explicit configuration; không tự sinh tất cả target semantics từ Ecore. |
| 03 | P03-GATE | DONE | VERIFIED | EcoreSourceLoaderTest/SemanticTransformationTest/MappingBundleCodecTest | Là supplemented frontend + explicit configuration; không tự sinh tất cả target semantics từ Ecore. |
| 04 | P04-T01 | DONE | VERIFIED | FunctionalObservationTest/FunctionalLiveBoardTest; actual MAS scheme snapshot | Board-reported fragment; normative maintenance vẫn unsupported. |
| 04 | P04-T02 | DONE | VERIFIED | FunctionalObservationTest/FunctionalLiveBoardTest; actual MAS scheme snapshot | Board-reported fragment; normative maintenance vẫn unsupported. |
| 04 | P04-T03 | DONE | VERIFIED | FunctionalObservationTest/FunctionalLiveBoardTest; actual MAS scheme snapshot | Board-reported fragment; normative maintenance vẫn unsupported. |
| 05 | P05-T01 | DONE | VERIFIED | OPlanSemanticsTest; ExecutionDescriptor/OPlanTransitionMonitor source | Outcome/enabledness abstraction; polling history luôn inconclusive khi causal boundary thiếu. |
| 04 | P04-T04 | DONE | VERIFIED | FunctionalObservationTest/FunctionalLiveBoardTest; actual MAS scheme snapshot | Board-reported fragment; normative maintenance vẫn unsupported. |
| 04 | P04-GATE | DONE | VERIFIED | FunctionalObservationTest/FunctionalLiveBoardTest; actual MAS scheme snapshot | Board-reported fragment; normative maintenance vẫn unsupported. |
| 05 | P05-T02 | DONE | VERIFIED | OPlanSemanticsTest; ExecutionDescriptor/OPlanTransitionMonitor source | Outcome/enabledness abstraction; polling history luôn inconclusive khi causal boundary thiếu. |
| 05 | P05-T03 | DONE | VERIFIED | OPlanSemanticsTest; ExecutionDescriptor/OPlanTransitionMonitor source | Outcome/enabledness abstraction; polling history luôn inconclusive khi causal boundary thiếu. |
| 05 | P05-T04 | DONE | VERIFIED | OPlanSemanticsTest; ExecutionDescriptor/OPlanTransitionMonitor source | Outcome/enabledness abstraction; polling history luôn inconclusive khi causal boundary thiếu. |
| 05 | P05-GATE | DONE | VERIFIED | OPlanSemanticsTest; ExecutionDescriptor/OPlanTransitionMonitor source | Outcome/enabledness abstraction; polling history luôn inconclusive khi causal boundary thiếu. |
| 06 | P06-T01 | DONE | VERIFIED | JasonStructuredObservationTest/JasonTaskConfigurationTest | Static Task admission và cycle observations; không end-to-end ActionExec causation. |
| 07 | P07-T01 | DONE | VERIFIED | JasonStructuredObservationTest/MentalStateProjectionTest | Ground/closed/default-BB fragment; rules không biến thành world truth. |
| 06 | P06-T02 | DONE | VERIFIED | JasonStructuredObservationTest/JasonTaskConfigurationTest | Static Task admission và cycle observations; không end-to-end ActionExec causation. |
| 06 | P06-T03 | UNSUPPORTED | UNSUPPORTED | SemanticRuntimeVerifier/JasonAbstractionPolicy | Không public proven ActionExec↔OpId token trong implementation; instrumentation mới có thể mở rộng. |
| 07 | P07-T02 | DONE | VERIFIED | JasonStructuredObservationTest/MentalStateProjectionTest | Ground/closed/default-BB fragment; rules không biến thành world truth. |
| 07 | P07-T03 | DONE | PARTIAL | MentalStateProjectionTest.liveArtifactValueAndLaggingMentalPerceptRemainIndependentlyQueryableAfterBeliefRemoval | Đã kiểm 12/10 coexist + belief removal; chưa có đúng scenario perception update 10→12 qua một actual Jason agent. |
| 08 | P08-T01 | DONE | VERIFIED | AuditBackend + installed @pre; OclSemanticBackendTest | Native syntax/type/evaluator được kiểm. |
| 09 | P09-T01 | DONE | PARTIAL | 1.536 temporal oracle boundaries + TemporalGoalMonitorTest + recorded-flow | Recurrence thuần đúng trong oracle; Task actor attribution sai (D04). |
| 08 | P08-T02 | DONE | FAILED | AuditOracles.partialPopulation | Registration/evaluator có thật nhưng coverage trước PASS bị D02 bác bỏ. |
| 08 | P08-T03 | DONE | FAILED | AuditOracles.partialPopulation | Registration/evaluator có thật nhưng coverage trước PASS bị D02 bác bỏ. |
| 09 | P09-T02 | DONE | VERIFIED | SemanticEvidencePanelTest/SemanticBindingTest | Read-only DTO assertions; manual pixel QA NOT_TESTED. |
| 10 | P10-T01 | DONE | VERIFIED | SnapshotCoordinatorTest/RuntimeSnapshotContractTest; source local-cut implementation | Không có global linearizable cut claim. |
| 11 | P11-T01 | DONE | PARTIAL | ObserverIdentityLifecycleTest/CslVerticalSliceTest/SharedStateAcceptanceTest | Stable/recreate/partial guards đạt; same-run stale snapshot admission vẫn sai D01. |
| 10 | P10-T02 | DONE | FAILED | Actual MAS D03 + direct NPL admission reproducer | Tất cả observed families chưa đi qua production mirror được. |
| 11 | P11-T02 | DONE | PARTIAL | ObserverIdentityLifecycleTest/CslVerticalSliceTest/SharedStateAcceptanceTest | Stable/recreate/partial guards đạt; same-run stale snapshot admission vẫn sai D01. |
| 11 | P11-T03 | DONE | PARTIAL | ObserverIdentityLifecycleTest/CslVerticalSliceTest/SharedStateAcceptanceTest | Stable/recreate/partial guards đạt; same-run stale snapshot admission vẫn sai D01. |
| 12 | P12-T01 | DONE | PARTIAL | Actual overlap + NativeInvocationSemanticsTest + actual recorded operation | Per-invocation pre giữ đúng; actual await không sinh suspended/resumed callbacks (D07); 64 MiB quota giới hạn một continuous trace. |
| 12 | P12-T02 | DONE | PARTIAL | Actual overlap + NativeInvocationSemanticsTest + actual recorded operation | Per-invocation pre giữ đúng; actual await không sinh suspended/resumed callbacks (D07); 64 MiB quota giới hạn một continuous trace. |
| 12 | P12-T03 | UNSUPPORTED | UNSUPPORTED | CartagoSnapshotSource.operationPayload + JasonTaskConfigurationTest | Nonvoid source result / Jason causal token thiếu; backend result parameter khác nguồn runtime result. |
| 13 | P13-T01 | DONE | PARTIAL | NplLiveBoardObservationTest/NormativeObservationTest + actual MAS D03 | Listener/getter/lifetime tests đạt nhưng new activation bị production bridge reject. |
| 13 | P13-T02 | DONE | PARTIAL | NplLiveBoardObservationTest/NormativeObservationTest + actual MAS D03 | Listener/getter/lifetime tests đạt nhưng new activation bị production bridge reject. |
| 13 | P13-T03 | UNSUPPORTED | UNSUPPORTED | NormativeUseBinding/SemanticRuntimeVerifier; upstream maintenance branch | Reported lifecycle không independent deontic equivalence; không fake derivation. |
| 13 | P13-T04 | DONE | PARTIAL | NplLiveBoardObservationTest/NormativeObservationTest + actual MAS D03 | Listener/getter/lifetime tests đạt nhưng new activation bị production bridge reject. |
| 14 | P14-T01 | DONE | VERIFIED | SemanticBindingTest/native transaction/read view/export parity | Một authoritative MSystem cho mỗi session; typed malformed rollback. |
| 15 | P15-T01 | DONE | PARTIAL | Fresh replay/installed smoke/corruption tests | Hash/codec/endpoint parity đạt; D01/D04 cũng replay được sai semantics. |
| 14 | P14-GATE | DONE | VERIFIED | SemanticBindingTest/native transaction/read view/export parity | Một authoritative MSystem cho mỗi session; typed malformed rollback. |
| 15 | P15-GATE | DONE | PARTIAL | AuditReplayInputs + actual replay | Integrity pass không đồng nghĩa source-cut validity/actor correctness. |
| 16 | P16-T01 | DONE | VERIFIED | BoundedExplorerTest + 7 independent finite branch relations | Chỉ explicit finite relation; không JaCaMo successor synthesis. |
| 16 | P16-T02 | DONE | VERIFIED | BoundedExplorerTest + 7 independent finite branch relations | Chỉ explicit finite relation; không JaCaMo successor synthesis. |
| 16 | P16-GATE | DONE | VERIFIED | BoundedExplorerTest + 7 independent finite branch relations | Chỉ explicit finite relation; không JaCaMo successor synthesis. |
| 17 | P17-T01 | DONE | PARTIAL | SourceLensTest + AuditBackend missing complement | Supported non-null laws pass; D05 malformed public input không có structured conflict. |
| 17 | P17-T02 | UNSUPPORTED | UNSUPPORTED | SourceLens supported field set/source Ecore missing typed values | Không official JCM/ASL/Java inverse; semantics ngoài fragment. |
| 17 | P17-GATE | DONE | PARTIAL | SourceLensTest + AuditBackend missing complement | Supported non-null laws pass; D05 malformed public input không có structured conflict. |
| 18 | P18-T01 | DONE | PARTIAL | 65/65 method anchors exist/discovered; semantic matrix bên dưới | Có row anchor yếu hơn requirement; D01/D02/D03/D04 lọt test suite. |
| 18 | P18-T02 | DONE | VERIFIED | Fresh XML independently recounted 662 + 2 IT | Count chính xác; không dùng count làm semantic acceptance. |
| 19 | P19-T01 | DONE | VERIFIED | Fresh semantic-performance.json; 4 cache/direct matrices | Finite normalized inputs; hash equality được kiểm. |
| 19 | P19-T02 | PARTIAL | PARTIAL | Fresh phase metrics + 1.000-round writer stress | Full 1/10/50 actual MAS slowdown/open-loop/SLA/retained heap chưa đo. |
| 20 | P20-T01 | DONE | PARTIAL | Fresh ZIP + 2 IT + AuditInstalledMain | Installed backend/replay verified; full packaged live MAS adapter path chưa qua D03. |
| 20 | P20-T02 | DONE | PARTIAL | Claim audit in this report | Report count accurate nhưng some DONE/AC claims cần hạ do falsification. |
| 18 | P18-GATE | DONE | FAILED | New falsification oracles and actual bridge run | Required semantic gates có defect dù regression xanh. |
| 19 | P19-GATE | PARTIAL | PARTIAL | Fresh phase metrics + 1.000-round writer stress | Full 1/10/50 actual MAS slowdown/open-loop/SLA/retained heap chưa đo. |
| 20 | P20-GATE | DONE | FAILED | D01/D02/D03/D04 | Không đạt final acceptance cho toàn named profile. |

P01-F1–F8 được đối chiếu với scenario G1/Integer/identity/current invariant/paired transition/leaf monitor/evidence/coordinator của [PLAN] §17. P02–P20 được đối chiếu cả 18 trường/phases, acceptance A00–A20, AC01–AC14, chứ không chỉ các bullet của ledger. Các trường “out of scope” được giữ; không biến exclusion hợp lý thành defect chỉ vì source có thêm chức năng. Ngược lại, D01/D02 nằm trong admission/coverage đã claim và không thể giải thích bằng full-JaCaMo-equivalence exclusion.

## 7. Mapping audit: source meaning, target, runtime, trace và inverse

Theo [R] §4–5/9, một concept cùng tên ở hai ngôn ngữ không bảo đảm cùng semantics. Theo source, MappingRuleCatalog chỉ khai báo 28 high-level decisions; phần tạo structure nằm ở OrganizationalMappingRules/CslUseBinding, observer mapping nằm ở các mapper riêng. SemanticTransformationService nhận **CslMonitorConfiguration đã authored**, rồi gắn source/features/complement; nó không tự suy ra đúng OCL/goal conditions của mọi ASL/Java input. [S-MAPPING], [S-RULES], [S-ORG]

| ID | Source concept / nghĩa cần giữ | Target và implementation | Runtime / trace / loss | Verified / inverse |
|---|---|---|---|---|
| M01 | Organization: SS+FS+NS; spec khác deployment | OS namespace/provenance; derive context/role từ SS | Full original source contract ở complement; FS/NS đi observer khác | VERIFIED fragment; không inverse official OS hoàn chỉnh |
| M02 | Group: spec và actual board instance | CSL OrgContext subtype; SemanticObservationMapper.group | Board UUID/runtimeIdentity → context object; contextual bounds; declaration không tạo population | VERIFIED structure; sync admission chịu D01; Ecore group fields có structural lens |
| M03 | Role: shared role definition, inheritance, position | CSL RoleOccurrence subtype; Plays + InContext | Một object/lifetime cho tuple player/role/context; same-instance inheritance; multi-parent excluded | VERIFIED subset; scoped MOISE policy UNSUPPORTED; Ecore role edits có lens |
| M04a | Compatibility: permission co-enactment có scope/direction | CslStructuralPolicy.Compatibility cho authored symmetric relation | Không tự weaken source directional/extending policy; source relation giữ trong decisions | VERIFIED core / UNSUPPORTED full source mapping |
| M04b | MOISE Link: type/scope/direction/extends | Structural policy/trace; world association chỉ khi authored | Không coi declaration là actual communication link hay iStar dependency | PARTIAL; inverse cần original policy, chưa official patch generation |
| M10 | Agent: running interpreter/player | AtomicAgent <: RuntimePlayer; optional intentional viewpoint | Exact incarnation/crosswalk; không CSL Entity. Agent count query chịu D02 | PARTIAL runtime profile; no runtime restore |
| RT01 | Enactment: accepted tuple, không startup request | Role object + player/context links | GroupBoard players authoritative; two contexts và leave/readopt có tests | VERIFIED selected observations; hidden polling lifetime unproven |
| M16 | Environment: topology/source authority | Metadata/helper scope | Không tự thành Entity; external state không quan sát không được invented | VERIFIED observation representation; no behavioral inverse |
| M17 | Workspace: technical scope/location/join/focus | WorkspaceObservation helper và metadata | UUID/full path/parent/env; không tự OrgCtx hoặc domain ownership | VERIFIED selected topology; Ecore deployment syntax không runtime inverse |
| M18 | Artifact: active Java component | Explicit registered CSL Entity **data view** | Exact class alias + runtime UUID; guards/Java/await/private state ở source | VERIFIED scalar view; no Java synthesis |
| M20 | Property: fullId/name/tuple/annotations/type/value | Strict scalar Attribute/USE Value | Unique name + arity 1; duplicates/tuples/null/type drift reject hoặc explicit unsupported | VERIFIED codec/profile; Ecore thiếu datatype/value nên inverse chỉ name/arity |
| M11 | Belief: stored literal/rule/query theo owner | MentalObservation + StoredBeliefObservation | Namespace, negation, annotations, provenance, term tree, rule flag giữ riêng world | VERIFIED ground fragment; general entailment UNSUPPORTED |
| M12 | AGoal: goal attempt/query occurrence | Execution observer + authored independent requirement | Event/means token khác literal; active !g không satisfy requirement | VERIFIED observation; no BDI semantic inverse |
| M08 | OGoal: performance/achievement/maintenance/min/all/args | Functional goal helper; optional MoiseRequirementBinder | Board state/committed/achieved/arguments keyed by scheme; temporal type authored | VERIFIED reported fragment; maintenance generation excluded |
| M06 | Scheme: functional spec and instance | SchemeObservation helper; not Entity | Actual board UUID, responsible group/arguments; two instances không merge | VERIFIED scoped source cut; no scheduler inverse |
| M07 | Mission: goal-set/allocation/preference | Functional allocation + CommitmentOccurrence | Mission/scheme/player/lifetime; leave/rejoin; min/max distinct | VERIFIED observer; preference preserved, không tự assignment behavior |
| M09 | OPlan: ordered operator/children/rate/prerequisites | ExecutionDescriptor + OPlanOutcomeMapper + OPlanTransitionMonitor | SEQUENCE and PARALLEL share AND outcome only; original operator/order preserved | VERIFIED selected abstraction; structural lens recovers operator/order |
| M13 | Plan: trigger/context/body/control/unifier | Executive AST; explicit TaskBinding/JasonAbstractionPolicy | Exact program/plan/body/context/contract admission; remainder trace-only | PARTIAL; runtime action causation UNSUPPORTED |
| CTX | Plan context: logical consequence/existence of unifier | Ground/pure mental query or authored OCL | Not a general Boolean string substitution; open/rule/custom-BB queries excluded | VERIFIED restricted query / UNSUPPORTED general translation |
| M14 | Event: trigger queue occurrence, selected/pending | Cycle execution observation/journal | Tokens + source + current means; no persistent world Entity invented | VERIFIED observation; no queue/scheduler inverse |
| M15 | Action: internal/external/success/failure/side effects | Invocation evidence / approved Task abstraction | Name/success/disappearing intention không prove world requirement; ActionExec token missing | PARTIAL; no general effect synthesis |
| M19 | Operation: signature/guard/await/invocation | MOperation + pre/post + paired state | Exact signature/args/UUID/correlation; authored contract không Java implementation | VERIFIED void interval; actor-specific Task D04; source phases D07 |
| M21 | Signal: transient percept event | Raw event/provenance, lastSignal evidence | Actual audit_changed(1), audit_changed(-1) captured; không durable world truth | VERIFIED recorded observation; delivery equivalence/inverse UNSUPPORTED |
| M05 | Norm: modality/activation/aim/deadline/lifecycle | NormOccurrence observer + lifecycle consistency | Raw EVIDENCE_ONLY remains observation; reported failure khác independent derivation | PARTIAL; D03 blocks live activation; full NPL equivalence UNSUPPORTED |

Bảy EEnums cũng có catalog entries E01–E07. Đây là **vocabulary preservation**, không full semantic translation: NPL còn prohibition; MOISE còn performance/default khác Ecore; custom link type không được cắt về ba literals; source ActionKind hai literals không bao hết Jason BodyType; AgentGoalType TEST không được cast thành ACHIEVE. Các source values ngoài Ecore được giữ trong official supplement/complement. [S-RULES]

Theo jacamo_v2.ecore: có 21 EClass/7 EEnum, thiếu current intentions, pending queue continuations, live incarnation, concrete property datatype/value, scoped MOISE pair cardinality, NPL clock/lifetime và Java effects. JacamoEcoreSourceLoader dùng đúng bundled copy riêng, native EMF validation, origins/defaults/references. fromEcore bắt buộc official supplements + explicit correspondences; chỉ compare concept/names/selected artifact type, không tự chứng minh hai frontend source models tương đương behavioral. SemanticTransformationTest kiểm same native hash khi đã cấp cấu hình/correspondence tương thích; không phải automatic Ecore-to-JaCaMo runtime semantics proof.

Trace/complement giữ original bytes/contracts và per-feature decisions. Khôi phục unchanged source từ complement chứng minh preservation của phần chưa sửa, không chứng minh CSL đủ biểu đạt Java/BDI behavior. Inverse thực hiện hiện nay thuộc SourceLens riêng; không inverse tổng quát của fromOfficial.

## 8. CSL audit

### 8.1. M2/M1/M0 và semantics tham chiếu

Theo PDF IV-B/C và [R] §6–7/10, organizational state và domain state dùng chung σ; passive Role khác active Agent; collective OrgCtx có thể là player; constraints Φ xác định admissible states. Các lựa chọn detailed role occurrence/lifetime là encoding/profile của implementation, không được trình bày như công thức đã có nguyên vẹn trong PDF.

Theo source [S-CSL], CslSchema phân ba classifier kinds ORG_CONTEXT/ROLE/ENTITY, attributes, association ends, single parent, invariants và contracts. CSLMonitor.ecore là M2 resource có observer namespace riêng; CslSchema/RequirementModel là M1; actual MObjects/MLinks/Values là M0. RuntimePlayer, AtomicAgent, WorkspaceObservation, functional/mental/normative helpers không kế thừa DomainEntity.

| Hạng mục | Bằng chứng kiểm lại | Kết quả |
|---|---|---|
| OrgCtx / nesting | CslStructuralPolicyTest, validator cycle/kind/bounds; no initial runtime population | VERIFIED |
| Role inheritance | Same role MObject inherits type/attributes; không parent play-chain | VERIFIED |
| Collective player | OrgContext <: RuntimePlayer; native links trên cùng context object | VERIFIED backend encoding; no automatic MOISE group-as-agent inference |
| Entity / Attribute | Actual artifact p:Integer; strict scalar binding | VERIFIED selected data view |
| Association | Native MAssociation, ordered ends/qualifiers trong binding inventory; explicit domain relations | VERIFIED schema/API; không tự map source MOISE policy |
| Compatibility | Authored symmetric policy allowed/disallowed, generated OCL | VERIFIED fragment; scoped/directional source semantics UNSUPPORTED |
| Ownership/composition | Declaration metadata không tự lifecycle/destruction | UNSUPPORTED runtime source correspondence, được báo rõ |
| Invariant registration | CslUseBinding.install tạo native invariant; SemanticRuntimeVerifier duyệt system.model().classInvariants; dynamic native registry refresh | VERIFIED registration, không bị omit như legacy translator |
| Negative state | p=-1 còn trong MSystemState, INVARIANT_BODY/INVARIANT FAIL + witness | VERIFIED; actual MAS và installed backend |
| Population safety | Required allInstances completeness | FAILED do D02; không nghiệm thu coverage-aware CSL toàn profile |

Theo PDF Definition 3/5, σ vi phạm Φ không admissible. Monitor giữ observed violating state làm counterexample là đúng mục đích runtime; intentional markings trên state đó chỉ có nghĩa diagnostic/prefix. Báo cáo kết quả không được dùng ACHIEVE vẫn SATISFIED ở p=-1 để kết luận unified model conformant. Source vẫn giữ FAIL song song; không rollback counterexample. [LIVEFINAL]

### 8.2. Khác biệt với use4Goal hiện tại

Đã đối chiếu read-only CslModel.requireLegacyRuntime, Csl2UseTranslator, GoalMarking, TaskMarking và symbolic semantics. use4Goal revised core v4 vẫn có backend guard; legacy translator/markings khác named monitor này. GoalMarking.NONE false là PENDING; TaskMarking dùng preSeen/postSeen lịch sử và không biểu diễn VIOLATED. USE thesis monitor không copy các recurrence đó, dùng TemporalGoalMonitor riêng trên native shared state. Không có production dependency vào use4Goal. [G-MARK], [G-TASK], [S-TEMP]

Không được coi việc cả hai cùng tên CSL là proof equivalence. Phần được kiểm là native CSL_OBSERVER_V1/V2 với quy ước đã công bố, không một implementation đầy đủ của mọi grammar/Ecore/use4Goal backend.

## 9. USE object-state audit

Theo source USE, MModel/MClass/MAttribute/MAssociation là schema. MSystem owns current MSystemState; MObject identity độc lập object slots của MObjectState trong từng state; MLink giữ ordered endpoint objects và qualifiers. new MSystemState(name,current) sao chép object states/link sets, giữ shared MObject identity; copy vẫn mutable theo API nên cần owner barrier. Native coordinator dùng copy pinned cho invocation/replay, không expose một live mutable pre-state cho callback UI. [U-STATE], [U-API], [U-EVAL], [S-COORD]

| Case | Đã kiểm thực tế | Kết luận |
|---|---|---|
| Artifact create→update | CslVerticalSliceTest và actual MAS; cùng UUID/cùng MObject | VERIFIED |
| Dispose→recreate cùng name | Real CArtAgO slice assert new UUID/new MObject/new USE name; binding cũ không transfer achievement | VERIFIED |
| GroupBoard recreate | Real board dispose/recreate; context/role cũ bị loại | VERIFIED |
| Agent restart cùng name | ObserverIdentityLifecycleTest dùng proven new incarnation/cycle evidence; dependent mental/role/commitment retirement | VERIFIED ở normalized observation boundary; full restart MAS stress NOT_TESTED |
| R tại hai groups | SharedStateAcceptanceTest hai contexts khác identity; source OS homonyms riêng | VERIFIED typed runtime projection |
| Hai schemes cùng spec | FunctionalLiveBoardTest hai SchemeBoard thật, arguments/UUID/goals tách biệt | VERIFIED |
| Links / malformed rollback | Native/semantic binding tests; savepoints gồm indexes/metadata/protocol/monitor; incident links không orphan | VERIFIED tested scope |
| Pre-copy | Independent p=1 pre vẫn 1 sau current 2 rồi 3; navigation @pre tests | VERIFIED |
| Same-run resync | Unchanged incarnation giữ object; stale snapshot có thể rewind values | PARTIAL / D01 |

Một snapshot A enact R trong C, world X.p=v cần a:AtomicAgent, c:C<:OrgContext, r:R<:RoleOccurrence, x:X<:DomainEntity, Plays(a,r), InContext(c,r), x.p:IntegerValue(v). Mental observations thuộc a qua AgentMentalState/StoredBeliefObservation; không ghi belief vào x.p. Goal binding tách player, role type, context ID, self x, episode. Actual output ở p=1 có 82 objects và 2 role occurrences; đây gồm observer helpers, không phải 82 CSL domain entities. [LIVEFINAL]

## 10. OCL audit

SemanticOclCompiler dùng host USE parser/type checker; OclEvaluationAdapter tạo Evaluator và truyền pre/post/VarBindings. Không regex-rewrite @pre. Native operation parameters và result được khai báo trước compile; missing type/syntax bị reject khi admission. [S-OCL], [S-EVAL]

| Operation / case | Oracle mới hoặc test source đã kiểm | Kết quả |
|---|---|---|
| Invariant / arithmetic | Nonnegative 0/1 PASS, -1 FAIL | VERIFIED actual source snapshots + installed JAR |
| allInstances / forAll / exists / collections | Host collection/filter/quantifier, tuple, nested navigation tests | VERIFIED evaluator; population safety FAILED D02 |
| Enum | Colour::GREEN trong independent native + OCL expression | VERIFIED host; không mở rộng ScalarValueCodec thành arbitrary enum property codec |
| Undefined | 1 div 0 > 1 → UNDEFINED | VERIFIED; USE equality semantics có thể cho false khi undefined so defined, không tự rewrite dialect |
| Syntax/type error | self.missing=1 rejected; registry không install half-invalid profile | VERIFIED |
| Unsupported source codec | Long/object/tuple/null/duplicate/type drift excluded rõ | VERIFIED admission tests; không false predicate FAIL |
| @pre positive | before=1, after=2, p=p@pre+1 → PASS | VERIFIED independent source backend và installed backend |
| @pre negative | cùng before=1, after=3 → FAIL; pre slot vẫn 1 | VERIFIED independent source/installed backend |
| Actual operation | Recorded opId(0,increment,counter,a), p 0→1, POST true | VERIFIED exact actual callbacks |
| Overlap | Separate A/B invocation states; source tests + repeated real CArtAgO experiment mục 17 | VERIFIED pair-state logic trong scope có boundary |
| Missing pre/failure/abort/timeout/capacity/wrong descriptor | NativeInvocationSemanticsTest explicit negative paths | VERIFIED classified skip/inconclusive; nonvoid result from logger UNSUPPORTED |

Bảy verdicts được giữ: PASS=true khi đủ evidence; FAIL=false có dữ liệu; UNDEFINED=host undefined; ERROR=compile/evaluation/protocol error; UNSUPPORTED=ngoài capability; SKIPPED=không applicable/thiếu observation/pre/disabled; INCONCLUSIVE=aggregate/history/bounds không đủ chứng cứ. Legacy view chỉ là projection; không được đọc một SKIPPED trong legacy rồi mất detailed reason.

Invariant body và quantified aggregate là hai records khác nhau. Với complete selected board nhưng global moise:org PARTIAL, per-context cardinality BODY có thể PASS trong khi global invariant aggregate SKIPPED. Một false witness có coverage đúng đủ để FAIL; empty population chỉ PASS nếu population proof hợp lệ. Đây là thiết kế đúng, nhưng ConstraintCoverageResolver.unavailable không áp completeness cho AtomicAgent/RuntimePlayer/WorkspaceObservation như áp DomainEntity. D02 là lỗi của coverage admission, không lỗi toán tử size của USE.

## 11. iStar semantics audit

Theo PDF IV-A/Table I: leaf Goal đọc condition; Task true khi pre và post, violated khi pre true/post false, undetermined khi pre chưa true; AND/OR dùng evaluated children; dependency dùng dependee. Paper mô tả history types nhưng không quy định hết unknown/activation/reset/run semantics. Những điểm này của [PLAN] §13 là profile policy, không được gắn nhãn “exact complete PDF formalization”.

| Element | Independent expected trace | Actual / trạng thái |
|---|---|---|
| NONE | T,F,T → SAT,VIOL,SAT | VERIFIED |
| ACHIEVE | F,T,F → UND,SAT,SAT trong cùng episode | VERIFIED; actual MAS và installed driver |
| MAINTAIN | T,F,T → SAT,VIOL,VIOL | VERIFIED |
| SUSTAIN | F,T,F,T → UND,SAT,VIOL,VIOL | VERIFIED |
| Activation | known off đóng episode; on reset history | 256 new oracle reactivations VERIFIED |
| Missing binding/unknown activation | không transfer latch sang identity chưa xác nhận | Existing explicit tests + source guard VERIFIED |
| History gap | Maintain/Sustain không repair bằng current true alone | Existing gap/replay tests + source recurrence VERIFIED |
| Task | Không dùng preSeen/postSeen latch; matching invocation required | Formula/current pairing VERIFIED; actor attribution FAILED D04 |
| AND/OR | 27 vectors của ba children; false/true witness precedence; no empty refinement | VERIFIED new oracle |
| Compound | propagate evaluated child, sau đó temporal transform của parent | VERIFIED source and existing tests |
| Dependency | evaluated dependee occurrence; own binding thiếu → UND | VERIFIED tests/source; cycles reject, no fixed-point semantics claimed |
| Shared state | một change đánh lại mọi bound requirement trên cùng version | SharedStateAcceptanceTest bốn actors; VERIFIED typed observations |

Pure recurrence oracle 1.536 boundaries không phát hiện sai trong Boolean complete-episode fragment. Tuy nhiên monitor chỉ đúng khi input/binding đúng: D01/D02 có thể đưa false evidence vào monitor; D04 credit sai actor. Vì vậy phase iStar tổng thể chỉ PARTIAL, dù recurrence class đạt các truth tables.

OPlan được kiểm riêng: ExecutionDescriptor giữ original operator, ordinal, source goal ID, rate và precondition graph. OPlanSemanticsTest so prerequisites với actual MOISE Goal.getPreConditionGoals cho sáu flat/nested fixtures; source sequence reverse khác forward, sequence khác parallel. OPlanOutcomeMapper: choice→OR, sequence/parallel→AND chỉ là outcome. OPlanTransitionMonitor kiểm paired causal history nếu có; polling-only execution-history trả INCONCLUSIVE. Không có collapse toàn bộ execution thành AND; không giả XOR/exclusive scheduler cho CHOICE. [S-OPLAN]

## 12. Jason runtime audit

Theo source BridgeAgArch.reasoningCycleFinished, current beliefs lấy getBB; goals từ circumstances events/pending/selected + intentions, structured mental/execution từ JasonCycleObservation. Copy trên agent thread, kèm cycle. OfficialJasonAdapter.getInitialGoals thuộc static source extraction; không được sử dụng làm current goals oracle của semantic execution mapper. [S-JASON]

| Case | Fidelity / kết quả |
|---|---|
| BB facts / rules / namespace / explicit negation / annotation | Actual Jason Agent/AST ở JasonStructuredObservationTest; copied tree giữ fields; VERIFIED |
| Events / pending / selected / intention stacks | Actual Circumstance/Intention/IntendedMeans API trong producer tests; tokens riêng, suspended field và current step preserved; VERIFIED boundary |
| Hai equal literals, hai occurrences | Object/means/event token, không dùng structural literal làm ID; tests independently distinct; VERIFIED |
| Plan definition vs executing plan | Source AST/program ID khác runtime means/current-step/unifier; VERIFIED representation |
| Goal completion/failure | Raw execution evidence; intention absence không iStar satisfaction; full BDI execution proof UNSUPPORTED |
| Actual two-agent boot | Production launcher thật, common-cartago/common-moise initialization, full source snapshots; VERIFIED |
| Every BDI micro-step | Cycle-end hook không quan sát mọi sense/deliberate/act semantic micro-transition | UNSUPPORTED claim |
| Custom BB/query semantics | Closed ground default-BB fragment; rules/custom BB/open variables rejected for entailment | UNSUPPORTED general entailment |

**Belief/domain test yêu cầu:** existing MentalStateProjectionTest tạo artifact p=12, separate actual Jason Agent BB price(10), materialize cả hai rồi remove price(10); world vẫn 12. Đây là real artifact + actual Jason AST/API, không một full percept-delivery run. Đã xác minh separation/query/removal. Chưa có test hiện tại chứng minh đúng chuỗi “agent A perceive 10→12, mismatch biến mất” qua actual scheduling của JaCaMo. Vì vậy P07-T03 và D04 matrix perception row chỉ PARTIAL cho exact request mới; không gọi removal là perception update. Pure mental query tests không cưỡng bức belief đồng bộ với property.

## 13. CArtAgO audit

Actual CartagoSnapshotSource dùng controllers/ArtifactInfo cho population/properties/descriptors, ICartagoLogger cho creation/dispose/property delta/operation/signal. Property tuple/fullId/annotation/type được copy; consumer chỉ admit explicit scalar views. Official property truth tách Jason percept. [S-CARTAGO]

| Runtime feature | Kiểm lại | Kết quả |
|---|---|---|
| Create / update / dispose / same-name recreate | Real slice + UUID asserts | VERIFIED |
| Multiple same-name properties / arity>1 / null/object | Actual adapter representation + strict scalar/rejection tests | VERIFIED observation/rejection; generic collection domain schema UNSUPPORTED |
| Operation requested/start/success/failure | Actual operation traces và source descriptor lookup; request không phải pre | VERIFIED admitted void interval |
| Signal | Actual audit_changed(1), audit_changed(-1) callbacks ghi từ real MAS | VERIFIED recorded evidence; no durable truth inference |
| Concurrent operation interference | Actual A await, B mutation, A resume; xem mục 17 | Pair-state verification có evidence |
| Suspend/resume lifecycle callbacks | Logger interface có methods nhưng actual CArtAgO 3.1 await không gọi chúng | PARTIAL capability, D07 |
| Java return value | operationPayload không cung cấp source Java nonvoid return | UNSUPPORTED; backend typed result support không bù được missing runtime value |

D07 được tìm từ experiment thực: property commit ở await được quan sát, A vẫn in-flight, B hoàn thành trước A; không có suspended/resumed callback. Cached source tag cartago-v3.1 Artifact.await (khoảng lines 810–880) commit properties, nhả/đợi lock và restore execution context, không gọi logger methods đó. Không dùng sự tồn tại interface method để claim một phase đã được source phát. Pre ban đầu vẫn giữ được đến completion; điều này xác minh @pre, không xác minh full lifecycle-observation coverage.

## 14. MOISE audit

Theo official adapter/source: SS roles/groups/cardinalities/compatibility/link giữ riêng FS/NS. Runtime oracle là actual GroupBoard.getGrpState/SchemeBoard.getSchState/getGoalStates, không JCM initial player requests. Group/scheme binding dùng UUID + source spec + runtimeIdentity; actual board-operation accepted state authoritative.

| Hạng mục | Evidence | Kết quả |
|---|---|---|
| Role adoption/leave | CslVerticalSliceTest, SharedStateAcceptanceTest, actual MAS a-worker/b-reviewer | VERIFIED |
| Compatibility/cardinality | Authored profile tests; negative accepted state giữ FAIL; unsupported source scope explicit | VERIFIED selected fragment |
| Mission commit / leave / new commitment lifetime | FunctionalObservationTest + actual commitMission(work) callback/cut | VERIFIED observer |
| Waiting→enabled→satisfied | FunctionalLiveBoardTest inherited public board operations | VERIFIED source-reported states |
| Goal arguments/committed/achieved agents | Actual SchemeBoard cut, exact goal/scheme identity and arrays | VERIFIED |
| Multiple schemes same spec | Two real boards/argument isolation | VERIFIED |
| Goal min/all/performance | Source definition/board acknowledgement preserved; no universal >=min replacement | VERIFIED representation/reported evaluation |
| OPlan | Source prerequisite oracle + ordered descriptor | VERIFIED outcome/enabledness abstraction |
| Independent MOISE deontic satisfaction / maintenance | Source maintenance generator TODO retained; board acknowledgement ≠ independently derived truth | UNSUPPORTED |

Actual live MAS final cut có scheme service_run, mission work, organizational goal g; a committed mission qua CArtAgO operation. Thí nghiệm world goal Ready không yêu cầu g phải satisfied; hai nghĩa được giữ riêng. Scheme/mission không bị ép thành DomainEntity.

## 15. NPL audit

NplBoardSnapshotSource gắn listeners created/fulfilled/unfulfilled/inactive, copy NormInstance, dùng NplActivationCrosswalk cho board UUID + public created annotation/subject/unifier. NormativeObservation giữ modality/deadline/state/clock; SemanticRuntimeVerifier tách NORM_REPORTED_STATE, NORM_LIFECYCLE_TRACE_CONSISTENCY và NORM_DERIVATION. Derivation luôn INCONCLUSIVE, không dùng replay wall clock phán lại deadline. [S-NPL], [S-VERIFY]

| Case | Quan sát độc lập |
|---|---|
| ACTIVE trước deadline | Adapter test API thật; chưa done không bị đổi thành violation |
| FULFILLED / UNFULFILLED | Real listener/getter/timer tests pass, exact activation identity/crosswalk |
| INACTIVE | Separate state, không “inactive ⇒ fulfilled” |
| Permission unused | Không coi như missed obligation |
| State deadline / board recreate | Tests thật giữ UUID/lifetime; ambiguous activation không merge |
| Gap/resync | Reported current state có thể phục hồi; lifecycle history vẫn inconclusive |
| New norm qua production bridge | FAILED D03: listener phát NORM_ACTIVATED, mirror chỉ nhận CREATED/ADDED/JOINED/COMMITTED/focus là creation |
| Independent source equivalence / sanctions / MOISE maintenance | UNSUPPORTED, là giới hạn thực của implementation/selected source fragment |

New minimal reproducer dùng empty current NPL population, watermark 0 rồi NORM_ACTIVATED sequence 1: BridgeMirrorStateMachine.apply trả BRIDGE_UNKNOWN_INCARNATION và state RESYNC_REQUIRED. Cùng lỗi xuất hiện trong MAS thật khi mission commitment kích hoạt norm. Test producer-unit/getter trước đó không đi qua mirror consumer, vì vậy tất cả có thể xanh trong khi integration hỏng. [NPLREPRO], [LIVEFINAL], [S-MIRROR]

Version caveat D06: fresh adapter test XML dùng NPL 0.6.1; plugin/live classpath dùng NPL 0.6. Các component binary hashes được live handshake ghi, nhưng không được trộn hai test environments thành một normative guarantee. Nhánh source bảo trì incomplete vẫn độc lập với khác biệt version này; D03 còn có reproducer thuần consumer nên không được quy toàn bộ lỗi cho upstream NPL.

## 16. Snapshot / synchronization audit

SnapshotCoordinator.capture buffer-first, lấy local cuts/watermarks của từng source, so topology và retry bounded; Jason publication dùng local lock. Đây là vector observation cut, không global transaction. NativeRuntimeProjector.apply(event) kiểm session/generation/revision, EventLedger, sourceSequence, gaps và resync. NativeRuntimeMutationEngine reconcile chỉ complete scope; immutable DTO/canonical JSON tách khỏi live Java objects. Những cơ chế này có source và regression evidence thực. [S-SNAPSHOT], [S-PROJ]

| Injection | Expected | Observed / đánh giá |
|---|---|---|
| Exact duplicate event | Idempotent | VERIFIED existing ledger/coordinator tests |
| Same event ID khác content | Reject + stale/resync | VERIFIED |
| Out-of-order / missing event / wrong run / wrong revision | No false proof | VERIFIED event path tests |
| PARTIAL snapshot bỏ object/property | Giữ last-known, không infer absent/delete | VERIFIED independent oracle + real slice |
| COMPLETE scoped absence | Retire đúng authority/incarnation, remove incident links | VERIFIED tested Jason/Cartago/board scopes |
| Topology change / overflow capture | Retry hoặc incomplete | VERIFIED SnapshotCoordinatorTest |
| Stale same-run snapshot sau event | Reject/mark stale; không rewind current proof | **FAILED D01** |
| Population thiếu + allInstances | SKIPPED/INCONCLUSIVE | **FAILED D02** |
| New NPL activation | Admit new observed lifetime hoặc structured unsupported | **FAILED D03** trong mirror |
| Agent cycle regression | Reject atomically | VERIFIED normalized-cycle test |
| Hidden leave/adopt giữa polls | Continuity unproven | Source metadata giữ caveat; không proof không có hidden episode |
| Every source concurrently / delayed source | Không có global mixed-cut claim | PARTIAL: source-local stress tests; full combined Jason+Cartago+MOISE+NPL soak NOT_TESTED |

D01 root cause: applySnapshot(snapshot,session,nextGeneration) kiểm revision và duplicate fact IDs nhưng không so snapshot.endWatermarks với sourceWatermarks đang accepted. Sau applying facts, nó clear sourceWatermarks và chép watermark của snapshot vào. Điều này cho phép rollback vector từ cartago=1 xuống 0, dù same object/session/generation. BridgeMirrorStateMachine.replace cũng không có monotonic comparison. Event guards không bù được đường replaceSnapshot này.

Observation freshness phải tách “fresh input at source” khỏi “value còn tồn tại”. D02 cho thấy một allInstances query có thể đúng với phần population đang materialized nhưng không đủ để kết luận về toàn population; đây là core supported-profile error, không global-linearizability limitation.

Same-run complete resync cũng không tự repair temporal gaps. Current monitor giữ historyGap cho MAINTAIN/SUSTAIN; ACHIEVE chỉ giữ witness cùng confirmed binding/episode. Guard này đúng trong các tests hiện có, nhưng snapshot D01 có thể đưa cut cũ vào một episode đang chạy nên tổng thể synchronization vẫn FAILED.

## 17. Concurrency / race audit

### 17.1. Writer serialization: 1.000 iterations

AuditOracles tạo hai thread writers với per-round barrier, hai ordered source streams cùng sửa một domain object. Kết quả mới: **1.000 rounds, 2.000 events, 2.002 persisted journal entries, no gap**, complete replay và final result hash bằng current result. Elapsed cả run và replay khoảng 12,080 s trong workload này. Không dùng duration đó làm full-MAS throughput. [ORACLES]

Đây là normalized contract stress test, không 1.000 simultaneous Jason agents. Nó xác minh EDT serialization/journal/state projection theo accepted sequence. Nó không chứng minh đúng causal delivery từ mọi external runtime.

### 17.2. Real CArtAgO overlap: 100 rounds

Đã viết artifact audit tạm và dùng hai CartagoBasicContext agents thật. Mỗi vòng:

1. Reset p=0.
2. A bắt đầu slow, pre(A)=0; A ghi p=1 rồi await guard, nhả artifact lock.
3. B bắt đầu fast ở p=1, ghi p=2 và hoàn thành.
4. Unblock guard; A tiếp tục, ghi p=3 và hoàn thành.

Expected độc lập: B post p=p@pre+1 PASS với before=1/after=2; A post FAIL với before=0/after=3. Kết quả: **2 runs × 50 rounds, 200 post checks, 1.600 actual Cartago callbacks**, mọi vòng giữ đúng invocation pre; mỗi replay có 802 entries và endpoint hashes khớp. Witness interference liệt kê invocation còn lại; phases chỉ có started vì D07. [OVERLAP1], [OVERLAP2]

Một run thử 100 vòng trong một journal đã chạm default quota 64 MiB khoảng vòng 88: result bị chuyển SKIPPED/STALE với JOURNAL_OVERFLOW, export không được coi complete. Đây là **fail-closed quota behavior**, không một lỗi @pre. Để chạy đủ 100 observations, audit dùng hai traces độc lập 50 vòng, giữ nguyên production quota; không sửa/tăng quota để che giới hạn. Không claim đây là một continuous 100-round complete trace.

Actual await không sinh opSuspended/opResumed callbacks trong 100 rounds; đã kiểm source tham chiếu và observation thật. Lần harness đầu chờ callback không được runtime cung cấp nên timeout; điều kiện oracle được sửa thành committed property boundary p=1 trước B, không sửa implementation. D07 yêu cầu hạ lifecycle-observation claim, không hạ pair-state oracle đã đạt.

Chưa chạy 1.000 actual MAS overlap scenarios hay full all-subsystems race soak; phạm vi đó NOT_TESTED. Hai agents của overlap là CArtAgO contexts; actual Jason MAS ở mục 22 là một thí nghiệm khác, không trộn fidelity.

## 18. Replay audit

NativeRuntimeReplay v2 copy bundle vào isolated owned directory, validate exact file catalog/version/limits/hashes, parse schema/bindings/mapping, reconstruct native states/monitor và so logical result/state hashes. Original input không được write. NativeReplayStepController mở read-only Session và next/previous reconstruct states. Current integrated replay tests và installed smoke đều chạy lại thành công. [S-REPLAY], [SMOKELOG]

| New adversarial input | Expected / actual |
|---|---|
| Runtime file thêm một byte x, manifest giữ nguyên | Reject REPLAY_FILE_HASH_MISMATCH:runtime.jsonl |
| Xóa event giữa trace, recompute outer runtime file hash | Reject REPLAY_CHAIN_CORRUPT:4 |
| Sửa invariant body trong mapping bundle, recompute outer mapping file hash | Reject MAPPING_BUNDLE_VERSION_OR_HASH_MISMATCH |
| Old native replay / semantic mapping optional | Existing backward/installed tests pass |
| Known coverage gap / persistence failure | Không complete; existing tests kiểm partial-gap/hash parity và stale/error publication |
| Actual MAS p=0→1→-1 snapshot trace | Complete, 10 entries, endpoint state/result hash khớp |
| Exact actual invocation stream | Complete, 10 entries, POST @pre tái tính đúng |
| Stale snapshot D01 | **Cũng complete**: replay chứng minh reproducibility của accepted input, không chứng minh input được admit đúng |
| Task b credited by a D04 | Sai attribution được tái hiện deterministically; hash stability không phải semantics correctness |

Kết luận: file/hash-chain integrity và deterministic recomputation được VERIFIED trong cases đã chạy. Source cut validity và actor-binding correctness là PARTIAL/FAILED theo D01/D04. Không gọi replay là JaCaMo runtime restore hoặc independent oracle của chính recurrence/code mapping mà nó reuse. [CORRUPT], [RECORDED]

## 19. Bounded explorer audit

Theo source BoundedExplorer/TransitionProfile: FINITE_FIXED_POPULATION_V1 yêu cầu finite states, fixed object/link population, complete attribute vectors, actor/source labels, executive state, logical clock, requirements/roots và assumptions. Explorer dựng một offline MSystem từ schema, không truy cập connected live state; DFS giữ từng TemporalGoalMonitor.Snapshot theo path, không state merging/POR. Guard/post dùng USE OCL, post có two-state @pre. Không tự sinh Java/ASL successors.

| Case | Kết quả đã xác nhận |
|---|---|
| All/mixed/no conformant terminal scenarios | CONFORMANT_WITHIN_BOUNDS / WEAK / NON_CONFORMANT |
| New independent finite branch oracle | 7 exhaustive nonempty branch relations cho cùng predicate; counts và classification đúng |
| Zero complete execution / nonterminal deadlock | INCONCLUSIVE, không vacuous strong conformance |
| Loop/step/node/population/clock/timeout cutoff | Frontier + INCONCLUSIVE; timeout giữa evaluations, không hard interrupt Evaluator |
| Shared domain / histories cùng endpoint khác MAINTAIN history | Không merge; terminal markings khác nhau; existing oracle pass |
| Missing frame/scalar vector / arbitrary successor approval | UNSUPPORTED hoặc structured cutoff; không giả effect từ OCL |
| Undefined terminal goal / unknown guard | INCONCLUSIVE |
| Witness concretization | Chỉ match supplied complete value trace/source actor/digest; mismatch rejected/spurious |

Theo PDF Definition 7, Π và root markings phải có complete scenarios; risk là trục riêng. Bounded conformance chỉ thuộc explicit relation trong bounds. Một runtime trace không đủ phân loại mọi JaCaMo behavior. Invariant-violating abstract states bị đánh không-proven-admissible/frontier, không được silently xóa rồi trả strong. Không có proof abstraction đủ/sound với tổng interpreter; UNSUPPORTED cho claim ấy. [S-EXPLORER], [BACKEND]

## 20. Round-trip / lens audit

SourceLens.get load exact supplied Ecore vocabulary, produce owned target fields và full original XMI complement. put compare source hash, exact source asset, metamodel hash, regenerate projection để detect complement/target tamper, validate fields/reference rebasing và unchanged features. Nó trả proposed bytes/patch plan trong memory, không sửa sibling source/live MAS. [S-LENS]

| Edit / law | Actual supported status |
|---|---|
| GetPut no-op | VERIFIED byte equality của unchanged input |
| PutGet role/context name/cardinality/abstractness | VERIFIED normalized AST trong Ecore structural view |
| OPlan children reorder / SEQUENCE↔PARALLEL | VERIFIED exact operator/ordered children/reference rebase |
| AND chưa explicit original operator | AMBIGUOUS, không chọn ngầm sequence/parallel |
| Unsupported Java effect/property datatype/value | UNSUPPORTED; supplied Property không có các features này |
| Stale source/metamodel / tamper / collision / multi-owner | CONFLICT/AMBIGUOUS được test, không write source |
| Missing complement | FAILED API robustness D05: Projection cho phép null, put dereference k.sourceHash |
| Full official JaCaMo round-trip | UNSUPPORTED: không inverse JCM/MOISE/ASL/Java transformation |
| Semantic round-trip / live restore | UNSUPPORTED; structural AST laws không simulation/trace-equivalence proof |

Cụm “bidirectional” chỉ hợp lệ kèm tên ECORE_STRUCTURAL_LENS_V1 và supported owned fields. fromOfficial mapping bundle và SourceLens không tạo một inverse tổng quát chỉ vì đều giữ complement. Không có arbitrary USE object-diagram edit → JaCaMo behavior implementation.

## 21. Packaged artifact audit

Fresh Maven package tạo ZIP và shaded JAR; staged JAR hash bằng build JAR. Hai existing installed ITs load plugin bằng USE plugin loader trong JVM mới, không đưa production target/classes vào child classpath. Child test driver có thể nằm trong test-classes; code-source assertions bảo đảm NativeRuntimeReplay/Coordinator đến từ installed JAR. [PKGHASH], [SMOKELOG]

Đã bổ sung AuditInstalledMain, compile với **USE distribution JAR**, child classpath chỉ host use-gui.jar + ignored audit driver classes. Driver reflect load semantic compiler/evaluator/monitor/replay bằng plugin classloader, kiểm protection-domain code source đúng installed JAR. Không có Maven JaCaMo/Jason/CArtAgO/MOISE/NPL implementation dependencies trên child classpath.

| Required package case | Evidence / verified |
|---|---|
| Static mapping resources / complement | Installed mapping bundle decode và presence verified; EMF/capability resources packaged; full new official-source mapping invocation trong isolated child NOT_TESTED |
| Runtime snapshot projection | Actual MAS snapshot bundle reconstructed bởi installed replay, native state hash checked; VERIFIED replay path |
| Passing/violated invariant | Independent installed host/native evaluator 0 PASS, -1 FAIL; actual snapshot replay includes same negative state |
| @pre | Installed semantic compiler/evaluator before=1→2 PASS, 1→3 FAIL |
| Temporal history | Installed TemporalGoalMonitor ACHIEVE F/T/F→U/T/T; semantic replay carries history |
| Replay/Step/read-only | 2 ITs + own driver pass; fresh actual-runtime replay decoded |
| Live production stream | Không được coi package-pass là giải quyết D03; packaged live MAS all-family subscription chưa qua |

Vì “static mapping” của existing package gate là consumed mapping/complement và model replay, chưa fresh official frontend transformation không runtime deps, P20-T01 vẫn PARTIAL theo yêu cầu package audit mở rộng. Có bằng chứng binary/backend hoạt động; chưa đủ claim mọi source entry point behave giống source tree.

## 22. Live JaCaMo end-to-end audit

Đã boot **actual JaCaMoLauncher/BridgeControlledLauncher** trong child JVM, actual Jason agents a/b, CArtAgO environment, actual MOISE boards và production TCP/HMAC transport. Fixture chỉ ở target/verification-audit; không sửa examples/repository tham chiếu. [LIVEHARNESS], [LIVEFINAL]

| Thành phần tối thiểu | Dữ liệu thực |
|---|---|
| Agents | a chạy audit plan; b idle; exact Jason incarnations trong recorded binding |
| Group / roles | actual org/team GroupBoard; a worker, b reviewer, hai accepted players |
| Scheme / mission / OGoal | service_run:service; mission work; organizational goal g; a commitMission(work) |
| Workspace / artifact | declared main → actual /main/main; actual AuditCounter UUID |
| Mutable property / operation / signal | p:Integer; increment và set; audit_changed(1), audit_changed(-1) callbacks |
| OCL | AuditEntity::Nonnegative = self.p>=0; increment post self.p=self.p@pre+1 |
| iStar | Ready ACHIEVE, self.p>=1, binding a/worker/team/x/episode |

Scenario đã quan sát:

| Boundary | Actual state / binding | OCL | Ready |
|---|---|---|---|
| Start before enactment | p=0, 0 role objects; actor binding unavailable | Nonnegative PASS | UND/quality SKIPPED |
| Joined/enacted/committed | p=0, 2 roles; a/b true runtime IDs; scheme mission commitment present | Nonnegative PASS; Ready condition false | ACTIVE, raw VIOLATED, evaluated UND, no history gap |
| a increment | opId(0,increment,counter,a); p=1; cùng artifact incarnation | Nonnegative PASS; recorded POST 0→1 PASS | SATISFIED |
| a set(-1) | p=-1 vẫn trong native state; exact negative signal captured | Nonnegative FAIL với p=-1 witness | ACHIEVE vẫn SATISFIED cùng episode; current quality FAIL |
| Replay | Snapshot trace 10 entries | Final state/result hashes bằng live endpoint | Same temporal result |

Final actual snapshot replay hashes: state 53eaccb50f85a7f2e3caf31c73146b992e7e85f56a2dd45fc9d294cd69180681; result 0dc025f4fbd0c09033a7203693a4236dee9d9ecc2f184d5cd339801903fcfb10. Exact operation replay là bundle khác, cũng 10 entries; giữ riêng để không trộn state version của hai trace.

**Gate tổng thể: PARTIAL, production subscription subgate FAILED.** Khi norm mới được kích hoạt bởi mission commitment, production BridgeClient/mirror reject NORM_ACTIVATED với unknown incarnation và ngắt. Để tiếp tục falsify backend, audit giữ riêng validated raw Cartago callback stream và fresh snapshot request path. Đường này dùng dữ liệu thật nhưng **không đi qua complete accepted production subscription sau lỗi**; không được nâng thành full production end-to-end PASS. Errors không bị xóa khỏi summary.

Một số harness staging attempts trước final bị newline-sensitive JCM syntax, reserved scheme identifier, thiếu common initialization plans, sai workspace action annotation, unbounded collector hoặc journal quota. Các lỗi fixture/harness đó được phân biệt với D03 production bug. Final fixture dùng focusing artifact ID thực và bounded selected callbacks; script không đổi source implementation. Actual final signal count=2, 2 agents, replay complete; process exit=1 là expected audit failure do bridge errors vẫn được assert.

## 23. Performance result

Fresh clean test tái tạo semantic-performance.json; số dưới lấy lại từ JSON mới, không copy số cũ. Scope là SYNTHETIC_ADAPTER_BOUNDARY_SELECTED_SCALAR_AND_ENACTMENT_OBSERVATIONS_NOT_FULL_LIVE_MAS_THROUGHPUT. Cache chỉ class/binding lookup; cả hai paths vẫn full OCL/all requirements/full temporal reevaluation. [PERF]

| Agents/artifacts/properties/contexts/goals/invariants | Warmup/measured | Direct p50/p95/p99 ms | Cached p50/p95/p99 ms | Cached boundaries/s | Cached journal/replay bytes |
|---|---|---|---|---:|---:|
| 1/1/1/1/1/1 | 4/16 | 2,156 / 4,100 / 4,100 | 2,066 / 3,216 / 3,216 | 454,61 | 222077 / 236716 |
| 10/10/10/10/10/10 | 4/12 | 18,659 / 23,508 / 23,508 | 15,677 / 23,670 / 23,670 | 61,61 | 2496756 / 2523880 |
| 50/100/10/10/100/10 | 2/6 | 144,854 / 201,098 / 201,098 | 138,260 / 168,653 / 168,653 | 6,81 | 11600380 / 11701980 |
| 50/10/10/10/1000/1 | 0/2 | 93,236 / 102,916 / 102,916 | 88,962 / 95,515 / 95,515 | 10,84 | 5433979 / 6276356 |

Cả 4 matrices có logical hash list equality direct/cached, final state hash equality và complete replay. Số đo sau clean khác số implementation report, nhưng cùng benchmark scope và correctness conditions. Không có optimization bỏ constraint/temporal events để đạt throughput.

Per-stage metrics có writer wait, USE mutation, snapshot build, USE export, OCL, semantic evaluation, iStar, journal; vòng timing bounded 1.024 samples. OCL_EVALUATION legacy và SEMANTIC_EVALUATION là hai phép đo riêng; iStar nằm trong semantic cost. Không cộng p95 của các phase để suy p95 end-to-end. Source capture latency của actual JaCaMo, live slowdown và replay latency không được benchmark hiện tại đo đủ; replay correctness/bytes khác replay performance measurement.

Heap deltas của bốn cached runs lần lượt khoảng +87 MB, +135 MB, -94 MB, -257 MB; dấu âm do GC, không retained-heap/leak proof. Max recent serialized snapshots khoảng 17,4 MB trong workload 50 agents/100 artifacts; timing ring bounded không chứng minh tất cả heap bounded. 1.000-goal workload chỉ 2 measured samples, p95/p99 là max sample; không statistical production percentile. Global context count mới lên 10 trong matrix này, chưa planned 100 contexts; long 10k/100k history/rate 1/10/50 Hz/SLA chưa được thực hiện.

Actual overlap default journal quota đã được chạm và fail-closed; hai 50-round traces không bị overflow, replay đầy đủ. Unbounded raw collector OOM ở harness trước không được gán thành production heap defect. Full 1/10/50 autonomous MAS performance remains PARTIAL; synthetic population có 50 agents không phải 50 running interpreters.

## 24. Traceability và code-quality audit

### 24.1. Mười results chọn ngẫu nhiên có seed

Population sample: 39 selected OCL body/goal/post/contract result records trên actual invocation replay (exact count ở [TRACE]); Fisher–Yates seed 20261016; sample 10 without replacement. Mỗi result occurrence được khóa bằng journal ordinal, resultHash, stateVersion, kind/id/object; không chỉ constraint display name. Đã truy qua config schema, sourceId, complement decision M02/M18 và prior runtime snapshot fact; replay đã re-evaluate toàn trace.

| Sample | Journal ordinal / stateVersion | OCL/result | Object/source mapping | Evidence chain |
| 1 | 1 / 1 | CslContext_team::CslPolicy_58249677f208b0c8 [PASS] | CslContext_team_94b715765418c9cf; M02, 5 decisions | result/state hashes → schema classifier → moise:organization:audit_live:audit:group:team|team → exact runtime fact: có |
| 2 | 3 / 2 | AuditEntity::Nonnegative [PASS] | AuditEntity_6f2cd36711fc9ca6; M18, 2 decisions | result/state hashes → schema classifier → artifact-class:org.tzi.use.plugins.jacamo.semantics.AuditCounter → exact runtime fact: có |
| 3 | 5 / 4 | REQUIREMENT:ready [PASS] | AuditEntity_6f2cd36711fc9ca6; M18, 2 decisions | result/state hashes → schema classifier → artifact-class:org.tzi.use.plugins.jacamo.semantics.AuditCounter → exact runtime fact: có |
| 4 | 2 / 1 | AuditEntity::Nonnegative [PASS] | AuditEntity_6f2cd36711fc9ca6; M18, 2 decisions | result/state hashes → schema classifier → artifact-class:org.tzi.use.plugins.jacamo.semantics.AuditCounter → exact runtime fact: có |
| 5 | 5 / 4 | CslContext_team::CslPolicy_eda455121343b396 [PASS] | CslContext_team_94b715765418c9cf; M02, 5 decisions | result/state hashes → schema classifier → moise:organization:audit_live:audit:group:team|team → exact runtime fact: có |
| 6 | 2 / 1 | CslContext_team::CslPolicy_eda455121343b396 [PASS] | CslContext_team_94b715765418c9cf; M02, 5 decisions | result/state hashes → schema classifier → moise:organization:audit_live:audit:group:team|team → exact runtime fact: có |
| 7 | 5 / 4 | CslContext_team::CslPolicy_58249677f208b0c8 [PASS] | CslContext_team_94b715765418c9cf; M02, 5 decisions | result/state hashes → schema classifier → moise:organization:audit_live:audit:group:team|team → exact runtime fact: có |
| 8 | 7 / 5 | CslContext_team::CslPolicy_58249677f208b0c8 [PASS] | CslContext_team_94b715765418c9cf; M02, 5 decisions | result/state hashes → schema classifier → moise:organization:audit_live:audit:group:team|team → exact runtime fact: có |
| 9 | 2 / 1 | CslContext_team::CslPolicy_58249677f208b0c8 [PASS] | CslContext_team_94b715765418c9cf; M02, 5 decisions | result/state hashes → schema classifier → moise:organization:audit_live:audit:group:team|team → exact runtime fact: có |
| 10 | 4 / 3 | AuditEntity::Nonnegative [PASS] | AuditEntity_6f2cd36711fc9ca6; M18, 2 decisions | result/state hashes → schema classifier → artifact-class:org.tzi.use.plugins.jacamo.semantics.AuditCounter → exact runtime fact: có |

Với AuditEntity: result → Nonnegative/Ready/increment OCL → exact x/stateVersion → classifier source alias artifact-class:AuditCounter → M18 registration + attribute p binding → actual property fullId/values ở snapshot/event. Với CslContext_team: cardinality body → compiled CslPolicy ID → role/group schema + M02/group-role-bound decision → exact source group/team definition → actual board UUID/players. Dữ liệu đầy đủ (expression, hashes, object ID, source ID) ở [TRACE]; không cần đoán source từ USE display name.

10/10 selected chains được tái dựng từ bundle/config/recorded facts. Body PASS của per-context cardinality không bị đánh tráo thành global aggregate PASS; aggregate thực tế SKIPPED khi population unproven. Những sample không có scalar witness vẫn có board fact và reconstructed MSystemState. Bổ sung kiểm thủ công negative Nonnegative tại p=-1 và actual increment POST 0→1 (không tính thêm vào random 10).

Hạn chế: SemanticConstraintResult không lưu trực tiếp ruleId/source location/constraint source hash trong từng row; phải join schema/complement/envelope. Sample này không chứng minh traceability cho mọi unsupported/native observer class hoặc tất cả feature decisions. Rule M18 ở đây trỏ registered data view; không proof Java operation body. Đây là traceability trong admitted projection, không complete semantics provenance.

### 24.2. Architecture / dead code / exceptions

Source imports và call paths xác nhận contract neutral không import USE/JaCaMo; producer adapter phụ thuộc official runtime; consumer semantic records không giữ live Jason/Cartago objects. Một active native MSystem per session; coordinator owns writer. Offline explorer/replay tạo isolated systems có purpose khác, không hai authoritative live mirrors. Default NATIVE_CURRENT và opt-in CSL binding cùng pipeline; no production use4Goal dependency. [LAYERS]

Các lớp legacy native/frozen translator và verification layer vẫn còn để compatibility, vì vậy không kết luận dead chỉ do không nằm trong CSL path. NativeOclEvaluator/regex @pre của use4Goal không được gọi làm backend mới. Có package-level dependency ngược qua binding read view/inventory đến native kernel; đây là coupling thực, không một strict acyclic package graph như sơ đồ khái niệm. Không tìm thấy module cycle trong reactor dependency direction; không có exhaustive unused-code proof.

Quét TODO/FIXME/placeholder và broad/empty catches trên semantic packages/adapter/runtime được lưu ở [QUALITY]. Không thấy TODO placeholder trong phần semantic core được quét. Có broad catches: BoundedExplorer chuyển backend errors thành cutoff; CartagoSnapshotSource ghi descriptor diagnostics; BridgeAgArch invalidates incomplete cycle; các đường này có explicit quality. Empty catches ở cleanup/shutdown/file deletion tồn tại; source fallback exceptions có thể làm giảm diagnostic detail. LocalTcpBridgeServer.acceptLoop catch Exception gần như không log là điểm đáng theo dõi về diagnosability, chưa có executable false PASS từ branch đó. Không gán toàn bộ catch(Exception) thành bug.

Không có JaCoCo/branch coverage nên không tuyên bố mọi unsupported branch/dead feature flag đã được exhaustive kiểm. Capability manifest parity và resource tests xác nhận public selected flags; default/opt-in behavior có regression. Model/class existence, inventory 172 files và 65 method anchors chỉ là static/discovery checks, không behavioral proof.

## 25. Defect list và reproductions

Tất cả defect dưới đây được ghi nhận, **không implement fix**. Harness/oracle source ở [AUDITSRC]; kết quả JSON/log được link cụ thể. Input normalized của D01/D02 dùng existing fixture factory cho data construction, nhưng expected verdict và adversarial sequence được viết mới, không gọi lại existing test assertions để suy correctness.

### D01 — Same-run stale snapshot rewind tạo false current PASS

- Requirement: [R] §10.4–10.5; [PLAN] §10/15, P11/A11, C02/C04: stale không được trở thành current proof.
- Source: NativeRuntimeProjector.applySnapshot lines 100–140; BridgeMirrorStateMachine.replace lines 40–50. [S-PROJ], [S-MIRROR]
- Reproduce: initial COMPLETE Cartago snapshot watermark 0, x.p=0; apply event sequence 1 đưa x.p=-1 → FAIL; apply lại original snapshot cùng run/generation/revision.
- Expected: reject stale cut hoặc last-known/stale non-PASS, watermark không rewind.
- Actual: accepted, same MObject=true, p=0, invariant=PASS, freshness=CURRENT_OBSERVED, watermark reset từ snapshot cũ; replay complete=true với 4 entries.
- Root cause: snapshot path không monotonic-check vector trước mutation; overwrite sourceWatermarks làm mất accepted prefix mới.
- Severity **CRITICAL**. Không phải vấn đề UUID; object identity ổn định nhưng value/time semantics sai. [ORACLES]

### D02 — Incomplete player population được dùng để chứng minh allInstances equality

- Requirement: [PLAN] §6.1/10.2/12; every population dependency cần coverage; missing không false/empty truth.
- Source: ConstraintCoverageResolver.unavailable lines 12–28 chỉ domain/organization và annotated helpers; AtomicAgent/RuntimePlayer không có matching source completeness guard. populationComplete có AtomicAgent branch nhưng dùng cho invariant aggregation, không goal predicate admission. [S-COVER]
- Reproduce: one materialized agent; cartago COMPLETE, jason PARTIAL, moise:org PARTIAL; requirement NONE condition AtomicAgent.allInstances()->size()=1, exact actor/role/context binding available.
- Expected: SKIPPED/INCONCLUSIVE/UND vì global agent population unproven.
- Actual: GOAL PASS, marking SATISFIED. Root cause ở dependency/coverage resolver; native OCL size() tính đúng materialized set.
- Severity **CRITICAL**. Pure temporal truth table/cache parity không phát hiện được false input evidence. [ORACLES]

### D03 — NPL activation không được mirror nhận như creation

- Requirement: P10/P13/P15 và mandatory live gate cần admit actual NPL observed lifecycle.
- Source producer: NplBoardSnapshotSource.Attached.created → emit NORM_ACTIVATED; consumer BridgeMirrorStateMachine.apply creation predicate lines 87–94 không gồm NORM_ACTIVATED. [S-NPL], [S-MIRROR]
- Reproduce thuần consumer: empty NPL snapshot/watermark0, valid new norm identity, sequence1/NORM_ACTIVATED; outcome BRIDGE_UNKNOWN_INCARNATION, RESYNC_REQUIRED.
- Reproduce actual MAS: same error trên norm-instance mới trong /main/org:team.service_run sau mission commit; BridgeClient ends subscription.
- Expected: normalize/admit first authoritative activation hoặc explicit supported lifecycle handling; không làm hỏng một stream có typed lifecycle source.
- Actual fail-closed, **không false PASS** ở mirror; availability/integration gate thất bại.
- Severity **HIGH**. Direct NativeRuntimeProjector normative tests pass vì chúng bypass BridgeMirror; đây là nguyên nhân test-layer gap. [NPLREPRO], [LIVEFINAL]

### D04 — Task actor binding không tham gia invocation matching

- Requirement: [PLAN] §13, Task exact actor/player/context/self binding + matching invocation; active/success của action khác không đủ để credit Task actor đã chọn.
- Source: SemanticRuntimeVerifier.evaluate Task branch, lines 112–120, matching chỉ TASK_CONFORMANCE + contractId + self object. RequirementBindingResolver xác nhận b enact reviewer nhưng không kiểm performer của operation. CartagoSnapshotSource.operationPayload không xuất exact performer semantic ID. [S-VERIFY], [S-BIND], [S-CARTAGO]
- Reproduce bằng **actual recorded callbacks**, không fabricated success: b idle, TaskB bind b/reviewer/team/x; a chạy increment, actual opId(0,increment,counter,a). Before=0/after=1, operation post PASS. TaskB đổi UND→SATISFIED.
- Expected đối với actor-specific Task: invocation của a không credit b; SKIPPED hoặc UNSUPPORTED nếu actor causation chưa có proof.
- Actual: TaskB SATISFIED; replay tái hiện cùng marking/hash. Source Task binding token trong before/result chứa exact Jason b incarnation, nên không thể giải thích là cùng agent/name alias.
- Severity **HIGH**: missing actor-attribution semantics trong supported ordinary operation-backed Task path. Nếu intention của profile chỉ là global operation observation, phải định nghĩa và label rõ như vậy; hiện exact actor binding và Task result gây hiểu nhầm về agent thực hiện. **Không cáo buộc OCL post sai**; nó đúng cho invocation a. Không tự sửa/ghép performer từ OpId display string trong audit. [RECORDED]

### D05 — Missing lens complement gây NullPointerException

- Source: SourceLens.Projection constructor copy target nhưng không validate complement; put lines 24–26 dereference ngay.
- Reproduce: get valid structural source; new Projection(old.profile,old.target,null); put(source,emptyEdit,projection).
- Actual NPE: cannot invoke TraceComplement.sourceHash because k is null. No source writes, no false APPLIED.
- Expected: explicit invalid-input/conflict/unsupported diagnostic tại boundary, hoặc constructor reject có contract rõ.
- Severity **LOW**: robustness trên caller input ngoài mandatory complement contract; không làm mất source hay sai successful lens laws. [BACKEND], [S-LENS]

### D06 — NPL version/profile provenance chưa nhất quán giữa test environments

- Fresh classpaths: adapter NPL 0.6.1; plugin/live NPL 0.6 do mediation/provided dependency path. SHA-256 binaries khác: 0.6=fdf43a2cec55145fd31531c5eda95fa96c6440322887d2ff3b0598da3f2f1d43; 0.6.1=956818edfe9aeff7c4fde35bf1dd276cd71e1da62daf77a2a15ab08f87903332.
- NplActivationCrosswalk.observe hardcode sourceVersion=NPL_0.6.1_PUBLIC_API, còn actual distribution fingerprint ghi đúng binary hash 0.6 trong live run.
- Đây có thể là tên API-profile tương thích, **không nên đọc nó như actual artifact version**. Claim normative API tests của 0.6.1 không tự chứng minh production path 0.6 tương đương. Không có evidence phiên này rằng hai versions có toàn bộ lifecycle behavior giống nhau.
- Severity **MEDIUM** về reproducibility/profile admission; không quy D03 cho upstream version vì D03 có minimal pure-consumer repro. [S-NPLCROSS], [COUNTS], [LIVEFINAL]

### D07 — Source await không phát lifecycle callbacks mà consumer có handlers

- Actual 100 overlaps dùng Artifact.await; property commit, B completes, A continues được quan sát; suspendedCallbacks=0, resumedCallbacks=0 ở cả hai 50-round runs.
- Actual cartago-3.1.jar javap của Artifact.await cho thấy commitObsStateChanges, Condition.await, restoreOpExecContext; không call opSuspended/opResumed. Cached matching source tag cũng có interface/log manager methods nhưng await không gọi. [C-AWAIT], [C-BYTECODE]
- NativeInvocationSemanticsTest injects phase events chứng minh **consumer xử lý phase nếu được cung cấp**, không producer phát đầy đủ phases. Pre của A vẫn được giữ đúng ở actual test; source phases witness chỉ [started].
- Severity **MEDIUM** về capability/claim coverage. Full suspend/resume observation phải PARTIAL/UNSUPPORTED cho path này; không suy từ method existence. Không sửa CArtAgO hoặc producer trong audit.

### Những điều không được nâng thành defect thiếu bằng chứng

Quota overflow được báo stale/skip và export reject là fail-safe expected. OOM của unbounded temporary collector là lỗi harness, không production leak proof. Existing source homonym/group cardinality exclusions có formal-loss reasons. Broad catches cleanup không tự false PASS. Không phát hiện collapse SEQUENCE/PARALLEL trong execution descriptor. Không có bằng chứng artifact belief bị đồng bộ cưỡng bức vào world value.

## 26. Severity và impact

| Severity | IDs | Tác động |
|---|---|---|
| CRITICAL | D01, D02 | False passing proof trong profile đã admit; chặn nghiệm thu semantics |
| HIGH | D03, D04 | Production stream không hoạt động đầy đủ; actor-specific Task bị credit sai |
| MEDIUM | D06, D07 | Version/capability evidence chưa đủ để mang claim qua environments/source boundaries |
| LOW | D05 | Malformed public lens input không structured rejection; no source loss |
| INFO | Trace-row joins, remaining coupling/cleanup catches | Cần join bundle/registry; chưa có demonstrated incorrect verdict từ riêng chúng |

Scope/priority không được đổi theo số lượng tests xanh: một D01 hoặc D02 đủ để không chọn verdict A/B. Không có fix, commit hoặc silent expectation change trong code được audit. Mọi suggested remediation dưới đây chỉ là acceptance obligations cho lượt khác: monotonic snapshot admission, population coverage closure, new norm lifecycle admission, explicit Task performer semantics, capability/version pinning và malformed-input diagnostics.

## 27. Unsupported / Partial re-evaluation

### 27.1. Sáu ledger UNSUPPORTED có lý do gì?

| Task | Verified status | Giới hạn thực hay phần chưa implement? |
|---|---|---|
| P02-T03 | UNSUPPORTED | Symmetric core compatibility không bảo toàn mọi scoped/directional/extending policy; composition destruction không suy từ declaration. Cần formal extension/implementation mới, không upstream impossibility tuyệt đối. |
| P02-T04 | UNSUPPORTED | Runtime ownership và source collective-player correspondence chưa có semantic binding/proof; M1 containment không đủ. Backend có thể represent collective player, nên đây là selected-profile exclusion. |
| P06-T03 | UNSUPPORTED | Chưa có proven public ActionExec↔OpId causal token trong implementation. Có thể thêm instrumentation/capability; không được suy từ tên/time. |
| P12-T03 | UNSUPPORTED | Source logger không cung cấp nonvoid result/causal token theo contract đang dùng; typed OCL result parameter không phải runtime extractor. |
| P13-T03 | UNSUPPORTED | Independent NPL entailment/sanctions chưa implement; MOISE maintenance normative generator còn source limitation. Reported lifecycle khác full normative verification. |
| P17-T02 | UNSUPPORTED | Lens chỉ owned Ecore structural fields. Arbitrary official Java/ASL inverse không unique và không được implement; thiếu type/value trong source metamodel không được invented. |

Sáu exclusions có thể được xác nhận, nhưng không phải tất cả đều “không thể làm” về lý thuyết. Một số là công việc nghiên cứu/adapter chưa implement. Hiện code có diagnostic/trace cho selected exclusion; không được dùng UNSUPPORTED này để che D01/D02/D03/D04 vốn nằm trong flow đã claim.

### 27.2. Hai ledger PARTIAL chưa phản ánh hết phần thiếu

P19-T02/P19-GATE đúng là PARTIAL: live MAS open-loop rate/slowdown/SLA/retained heap chưa đo; plan còn axes 100 contexts, 10k/100k history, 10 in-flight operations chưa benchmark đầy đủ. Tuy nhiên audit tìm thêm task PARTIAL/FAILED ở phần sync/coverage/Task/NPL/lens/package/perception scope, nên câu “chỉ còn hai PARTIAL measurement, không blocker supported flow” không được xác nhận.

Các giới hạn không thay đổi: no full BDI/Java equivalence, no global atomic MAS cut, no generic belief entailment, no unbounded liveness, no general successor synthesis, no semantic/runtime inverse. Những giới hạn đã công bố đúng không phải lỗi. Actual 100 CArtAgO overlaps và 1.000 normalized writer rounds không mở rộng claim thành đầy đủ mọi interleaving.

## 28. Final acceptance matrix

### 28.1. AC01–AC14 của master plan

| Gate | Nội dung | Verified status | Evidence / reason |
|---|---|---|---|
| AC01 | Static subset | VERIFIED | 28-rule catalog/feature decisions, supplemented Ecore/official fronts + exact mapping complement; không automatic behavior synthesis |
| AC02 | Real runtime | VERIFIED | Actual APIs và two-agent MAS; full stream failure được tách ở AC12 |
| AC03 | Identity | VERIFIED | Same UUID same object; real artifact/board recreate; normalized restart; không full MAS restart soak |
| AC04 | USE state | VERIFIED | Typed Integer, exact objects/links, no phantom static population, pre-copy |
| AC05 | State OCL | FAILED | Negative predicate đúng nhưng D01 nhận stale state và chứng nhận false current PASS |
| AC06 | Transition OCL | VERIFIED | Independent 1→2/1→3; actual operation; 100 real overlaps giữ preA=0/preB=1 |
| AC07 | Temporal | PARTIAL | Pure recurrences verified; Task attribution D04 và input quality D02 |
| AC08 | No heuristic satisfaction | FAILED | Task bound b credited by a, despite exact b role binding |
| AC09 | MOISE binding | VERIFIED | Real board scoped tuple/goal/commitment; no global normative equivalence |
| AC10 | Unsupported | PARTIAL | Main exclusions explicit; missing phase/source-version assumptions cần chỉnh D06/D07 |
| AC11 | Trace/evidence | PARTIAL | 10 random chains reconstructed/replay integrity; semantic bad cuts/Task cũng replay |
| AC12 | End-to-end | PARTIAL | 0→1→-1 + same replay; full accepted stream FAILED D03 |
| AC13 | Documented profile | PARTIAL | Explicit manifest/schema present; claim fidelity/version/lifecycle cần hạ |
| AC14 | Claims bounded | VERIFIED | Finite explorer/structural lens/general-equivalence exclusions được nêu đúng |

### 28.2. Toàn bộ 65 required matrix rows

Các test methods dưới đây đã được kiểm tồn tại và thực sự discovered ở fresh XML. Verified status đánh theo semantic scope ở [PLAN] §20 và yêu cầu audit, không copy status DONE. Những row VERIFIED có giới hạn normalized/source-oracle/live đã phân loại ở các mục trước; không ngầm là full MAS integration.

| ID | Verified status | Fresh executable anchor | Problems / scope |
|---|---|---|---|
| A01 | VERIFIED | CslStructuralPolicyTest.rejectsNestingCyclesKindsInvalidBoundsAndUnsupportedSourceParentProjection | Fresh executable anchor đúng case trong named/typed/source boundary đã nêu |
| A02 | VERIFIED | CslStructuralPolicyTest.inheritanceEnactmentCompatibilityAndCardinalitiesUseOneRoleOccurrence | Fresh executable anchor đúng case trong named/typed/source boundary đã nêu |
| A03 | VERIFIED | CslStructuralPolicyTest.rejectsNestingCyclesKindsInvalidBoundsAndUnsupportedSourceParentProjection | Fresh executable anchor đúng case trong named/typed/source boundary đã nêu |
| A04 | VERIFIED | CslStructuralPolicyTest.inheritanceEnactmentCompatibilityAndCardinalitiesUseOneRoleOccurrence | Fresh executable anchor đúng case trong named/typed/source boundary đã nêu |
| A05 | VERIFIED | CslVerticalSliceTest.acceptedRuntimeEnactmentRetainsAnAuthoredPerContextCardinalityViolation | Fresh executable anchor đúng case trong named/typed/source boundary đã nêu |
| A06 | VERIFIED | CslVerticalSliceTest.realArtifactAndAcceptedGroupBoardFeedSameSystemInvariantTransitionAchieveAndReplay | Fresh executable anchor đúng case trong named/typed/source boundary đã nêu |
| B01 | VERIFIED | CslVerticalSliceTest.realArtifactAndAcceptedGroupBoardFeedSameSystemInvariantTransitionAchieveAndReplay | Fresh executable anchor đúng case trong named/typed/source boundary đã nêu |
| B02 | VERIFIED | ObserverIdentityLifecycleTest.sameRunDiffBeliefReappearanceRestartPartialOmissionAndCompleteDeletionRespectLifetimes | Fresh executable anchor đúng case trong named/typed/source boundary đã nêu |
| B03 | VERIFIED | SharedStateAcceptanceTest.homonymousRolesInTwoOfficialOrganizationSpecificationsCannotCrossBind | Fresh executable anchor đúng case trong named/typed/source boundary đã nêu |
| B04 | VERIFIED | SharedStateAcceptanceTest.sameRoleInTwoContextsIsDistinctAndObservedLeaveReadoptCreatesANewOccurrence | Fresh executable anchor đúng case trong named/typed/source boundary đã nêu |
| C01 | VERIFIED | CslVerticalSliceTest.realArtifactAndAcceptedGroupBoardFeedSameSystemInvariantTransitionAchieveAndReplay | Fresh executable anchor đúng case trong named/typed/source boundary đã nêu |
| C02 | FAILED | ObserverIdentityLifecycleTest.sameRunDiffBeliefReappearanceRestartPartialOmissionAndCompleteDeletionRespectLifetimes | D01: stale snapshot rewind; event guards riêng vẫn pass |
| C03 | VERIFIED | CslVerticalSliceTest.realArtifactAndAcceptedGroupBoardFeedSameSystemInvariantTransitionAchieveAndReplay | Fresh executable anchor đúng case trong named/typed/source boundary đã nêu |
| C04 | PARTIAL | TemporalGoalMonitorTest.historyGapReactivationAndIncarnationHaveExplicitSemantics | Current resync/history tests pass; admission D01 còn mở |
| C05 | VERIFIED | SnapshotCoordinatorTest.retriesConcurrentTopologyChangeAndReplaysOnlyAfterAcceptedWatermark | Fresh executable anchor đúng case trong named/typed/source boundary đã nêu |
| C06 | VERIFIED | RuntimeVerificationCoordinatorTest.duplicateIsIdempotentWhileConflictRewindGapAndStaleIdentitiesCannotPass | Fresh executable anchor đúng case trong named/typed/source boundary đã nêu |
| D01 | VERIFIED | MentalStateProjectionTest.changingStoredKnowledgeKeepsWorldStateAndOclQueriesSeparateWithClosedScopeAdmission | Fresh executable anchor đúng case trong named/typed/source boundary đã nêu |
| D02 | VERIFIED | JasonStructuredObservationTest.completeStoredCutPreservesNamespaceNegationAnnotationRulesAndNumericTermIdentity | Fresh executable anchor đúng case trong named/typed/source boundary đã nêu |
| D03 | VERIFIED | JasonStructuredObservationTest.completeStoredCutPreservesNamespaceNegationAnnotationRulesAndNumericTermIdentity | Fresh executable anchor đúng case trong named/typed/source boundary đã nêu |
| D04 | PARTIAL | MentalStateProjectionTest.liveArtifactValueAndLaggingMentalPerceptRemainIndependentlyQueryableAfterBeliefRemoval | 12/10 + removal verified; actual percept-update 10→12 chưa test |
| E01 | VERIFIED | CslSchemaTest.strictScalarBindingDoesNotWidenOrTruncateObservations | Fresh executable anchor đúng case trong named/typed/source boundary đã nêu |
| E02 | VERIFIED | SharedStateAcceptanceTest.duplicatePropertyOccurrenceAndTypeDriftRejectWholeUpdateWithoutReplacingSlot | Fresh executable anchor đúng case trong named/typed/source boundary đã nêu |
| E03 | VERIFIED | CslSchemaTest.strictScalarBindingDoesNotWidenOrTruncateObservations | Fresh executable anchor đúng case trong named/typed/source boundary đã nêu |
| E04 | VERIFIED | SharedStateAcceptanceTest.duplicatePropertyOccurrenceAndTypeDriftRejectWholeUpdateWithoutReplacingSlot | Fresh executable anchor đúng case trong named/typed/source boundary đã nêu |
| E05 | VERIFIED | CslVerticalSliceTest.realArtifactAndAcceptedGroupBoardFeedSameSystemInvariantTransitionAchieveAndReplay | Fresh executable anchor đúng case trong named/typed/source boundary đã nêu |
| F01 | VERIFIED | TemporalGoalMonitorTest.independentlyExpectedTemporalTraces | Fresh executable anchor đúng case trong named/typed/source boundary đã nêu |
| F02 | VERIFIED | TemporalGoalMonitorTest.independentlyExpectedTemporalTraces | Fresh executable anchor đúng case trong named/typed/source boundary đã nêu |
| F03 | VERIFIED | TemporalGoalMonitorTest.independentlyExpectedTemporalTraces | Fresh executable anchor đúng case trong named/typed/source boundary đã nêu |
| F04 | VERIFIED | TemporalGoalMonitorTest.independentlyExpectedTemporalTraces | Fresh executable anchor đúng case trong named/typed/source boundary đã nêu |
| F05 | FAILED | TemporalGoalMonitorTest.missingBindingCannotRepairMaintainHistoryAndKnownWitnessSurvivesOnlySameEpisode | D02: incomplete agent population tạo new goal satisfaction |
| F06 | VERIFIED | TemporalGoalMonitorTest.historyGapReactivationAndIncarnationHaveExplicitSemantics | Fresh executable anchor đúng case trong named/typed/source boundary đã nêu |
| F07 | VERIFIED | TemporalGoalMonitorTest.historyGapReactivationAndIncarnationHaveExplicitSemantics | Fresh executable anchor đúng case trong named/typed/source boundary đã nêu |
| G01 | VERIFIED | TemporalGoalMonitorTest.refinementUsesEvaluatedChildrenAndDependencyUsesBoundOccurrence | Fresh executable anchor đúng case trong named/typed/source boundary đã nêu |
| G02 | VERIFIED | TemporalGoalMonitorTest.refinementUsesEvaluatedChildrenAndDependencyUsesBoundOccurrence | Fresh executable anchor đúng case trong named/typed/source boundary đã nêu |
| G03 | VERIFIED | TemporalGoalMonitorTest.refinementUsesEvaluatedChildrenAndDependencyUsesBoundOccurrence | Fresh executable anchor đúng case trong named/typed/source boundary đã nêu |
| G04 | VERIFIED | OPlanSemanticsTest.sequenceCounterexampleParallelInterleavingsAndMissingCausalityAreDifferent | Fresh executable anchor đúng case trong named/typed/source boundary đã nêu |
| G05 | VERIFIED | OPlanSemanticsTest.outcomeChoiceAcceptsOneOrBothAndSequenceStillRetainsItsOrder | Fresh executable anchor đúng case trong named/typed/source boundary đã nêu |
| H01 | VERIFIED | CslVerticalSliceTest.realArtifactAndAcceptedGroupBoardFeedSameSystemInvariantTransitionAchieveAndReplay | Fresh executable anchor đúng case trong named/typed/source boundary đã nêu |
| H02 | VERIFIED | NativeInvocationSemanticsTest.overlappingInvocationsKeepTheirOwnPreStateAndSuspendResumeNeverOverwriteIt | Fresh executable anchor đúng case trong named/typed/source boundary đã nêu |
| H03 | VERIFIED | NativeInvocationSemanticsTest.sourceFailureDuplicateCapacityTimeoutAndMissingBoundaryNeverProducePostPass | Fresh executable anchor đúng case trong named/typed/source boundary đã nêu |
| H04 | PARTIAL | NativeInvocationSemanticsTest.overlappingInvocationsKeepTheirOwnPreStateAndSuspendResumeNeverOverwriteIt | Injected consumer phase handling pass; actual await không emit phases D07 |
| H05 | VERIFIED | NativeInvocationSemanticsTest.wrongIncarnationSignatureAndCompletionArgumentsCannotBorrowAMatchingPre | Fresh executable anchor đúng case trong named/typed/source boundary đã nêu |
| H06 | VERIFIED | OclSemanticBackendTest.collectionsNavigationAtPreAndTypedResultUsePinnedNativeStates | Fresh executable anchor đúng case trong named/typed/source boundary đã nêu |
| I01 | VERIFIED | CslVerticalSliceTest.realArtifactAndAcceptedGroupBoardFeedSameSystemInvariantTransitionAchieveAndReplay | Fresh executable anchor đúng case trong named/typed/source boundary đã nêu |
| I02 | VERIFIED | FunctionalObservationTest.commitmentLeaveRejoinProducesNewLifetimeAndAllocationViolationIsRetained | Fresh executable anchor đúng case trong named/typed/source boundary đã nêu |
| I03 | VERIFIED | FunctionalLiveBoardTest.realPublicSchemeBoardCutCarriesIndependentArgumentsAndUuidIntoSameNativeState | Fresh executable anchor đúng case trong named/typed/source boundary đã nêu |
| I04 | VERIFIED | FunctionalLiveBoardTest.inheritedBoardOperationsGiveWaitingEnabledSatisfiedAndExactCommittedDoneAgents | Fresh executable anchor đúng case trong named/typed/source boundary đã nêu |
| I05 | UNSUPPORTED | SemanticsProfileTest.v1CannotAdmitUnimplementedTemporalOrNormativeSemantics | Maintenance normative realization excluded; authored MAINTAIN là case khác |
| J01 | PARTIAL | NplLiveBoardObservationTest.realNplListenerAndPublicGettersKeepActivationFulfillmentDeadlineMissAndUnusedPermissionDistinct | Real listener/API pass; new activation fails production mirror D03 |
| J02 | PARTIAL | NplLiveBoardObservationTest.stateDeadlineMaintenanceInactivityAndSameNameBoardReplacementHaveDistinctSourceLifetimes | Source lifecycle tests pass; complete production stream gate blocked D03 |
| J03 | VERIFIED | NormativeObservationTest.reportedLifecycleStaysSeparateFromIndependentDerivationAndIsReplayedWithoutAClock | Fresh executable anchor đúng case trong named/typed/source boundary đã nêu |
| J04 | PARTIAL | NplLiveBoardObservationTest.stateDeadlineMaintenanceInactivityAndSameNameBoardReplacementHaveDistinctSourceLifetimes | Board/activation identity tests pass; new occurrence stream admission D03 |
| K01 | VERIFIED | SourceLensTest.getPutPreservesUnchangedBytesAndPutGetNormalizesSelectedStructure | Fresh executable anchor đúng case trong named/typed/source boundary đã nêu |
| K02 | VERIFIED | SourceLensTest.getPutPreservesUnchangedBytesAndPutGetNormalizesSelectedStructure | Fresh executable anchor đúng case trong named/typed/source boundary đã nêu |
| K03 | PARTIAL | SourceLensTest.staleDigestComplementTamperNameCollisionAndAmbiguousOwnershipNeverWriteSource | Stale/ambiguous edits reject; missing complement NPE D05 |
| L01 | PARTIAL | CslVerticalSliceTest.realArtifactAndAcceptedGroupBoardFeedSameSystemInvariantTransitionAchieveAndReplay | Actual MAS snapshot path succeeds; production subscription fails D03 |
| L02 | VERIFIED | SharedStateAcceptanceTest.oneSharedArtifactChangeReevaluatesAllBoundActorsOnTheSameVersion | Fresh executable anchor đúng case trong named/typed/source boundary đã nêu |
| L03 | VERIFIED | CslVerticalSliceTest.realArtifactAndAcceptedGroupBoardFeedSameSystemInvariantTransitionAchieveAndReplay | Fresh executable anchor đúng case trong named/typed/source boundary đã nêu |
| L04 | VERIFIED | CslVerticalSliceTest.realArtifactAndAcceptedGroupBoardFeedSameSystemInvariantTransitionAchieveAndReplay | Fresh executable anchor đúng case trong named/typed/source boundary đã nêu |
| L05 | PARTIAL | NativeRuntimePackagedReplayIT.installedReleaseReplaysNativeOclHistoryWithoutMavenOrJaCaMoClasspath | Installed replay/backend pass; fresh static frontend + packaged live full path chưa test |
| M01 | VERIFIED | BoundedExplorerTest.finiteOracleClassifiesAllMixedNoneAndUnknownWithoutGeneralMasClaim | Fresh executable anchor đúng case trong named/typed/source boundary đã nêu |
| M02 | VERIFIED | BoundedExplorerTest.differentTemporalHistoriesAtTheSameDomainStateAreNeverMergedAndActorsInterleave | Fresh executable anchor đúng case trong named/typed/source boundary đã nêu |
| M03 | VERIFIED | BoundedExplorerTest.missingFrameArbitraryBehaviorAndBackendUndefinedAreExplicit | Fresh executable anchor đúng case trong named/typed/source boundary đã nêu |
| N01 | VERIFIED | VerificationLoadScenarioTest.boundedLoadMatrixKeepsFullEvaluationAndReplayHashesIdenticalWithAndWithoutBindingCache | Fresh executable anchor đúng case trong named/typed/source boundary đã nêu |
| N02 | PARTIAL | VerificationLoadScenarioTest.twoSourceWritersAreSerializedWithoutDroppingSharedStateUpdatesAndReplayRetainsAcceptedOrder | Quota/gap behavior verified; long soak/arrival-rate backlog chưa đo |

### 28.3. Adversarial gate bổ sung theo yêu cầu mới

| Input / negative scenario | Kết quả audit | Scope / evidence |
|---|---|---|
| Invalid Ecore/source enum/reference/required fields | VERIFIED rejection | EcoreSourceLoaderTest; EMF validation; no external reference fetch-as-model |
| Missing role/context enactment | VERIFIED SKIPPED/UND | Actual initial MAS before accepted players; resolver guard |
| Unknown artifact type | PARTIAL | Explicit registration/source admission inspected; dedicated new unknown live-class case NOT_TESTED |
| Unsupported property type / duplicate slots / arity>1 / null | VERIFIED rejection/unsupported | CslSchemaTest + SharedStateAcceptanceTest; no scalar coercion |
| Malformed OCL / unsupported property access | VERIFIED admission error | AuditBackend / registry tests |
| Unsupported OCL/source expression fragment | VERIFIED explicit exclusion in tested fragments | JasonTaskConfigurationTest/MentalPredicateBinding; no general OCL compatibility guarantee |
| Duplicated semantic ID | VERIFIED reject/rollback | Runtime snapshot duplicate guard, loader/profile/runtime tests |
| Stale model revision/run/generation | VERIFIED event rejection | Native runtime/bridge regression; snapshot revision checked |
| Stale same-run snapshot | FAILED | D01 new executable |
| Wrong incarnation/signature/arguments | VERIFIED no accidental post PASS | NativeInvocationSemanticsTest / exact source descriptors |
| Corrupt snapshot property type | VERIFIED rollback + stale/error | Real CslVerticalSliceTest malformed full snapshot |
| Corrupt replay / missing event / altered mapping | VERIFIED fail-closed | Three new corruption oracles, including recomputed outer hashes |
| Missing invocation pre | VERIFIED skipped/inconclusive | NativeInvocationSemanticsTest; not inferred from request/completion |
| Mismatched scheme / foreign goal | VERIFIED rejected/atomic rollback | FunctionalObservationTest rejects foreign goal semanticId and invalid board UUID/cut |
| Unknown normative lifecycle | VERIFIED decode rejection | New AuditBackend UNKNOWN_LIFECYCLE → IllegalArgumentException; full transport recovery scenario NOT_TESTED |
| New observed NPL activation | FAILED production stream | D03 minimal consumer + actual MAS |
| Partial agent population/allInstances | FAILED | D02 false PASS/SATISFIED |
| Actor-specific Task with other performer | FAILED attribution guarantee | D04 actual a operation / b binding |
| Missing lens complement | PARTIAL fail-safe robustness | NPE visible, no write/no APPLIED; D05 |
| Every-subsystem concurrent soak | NOT_TESTED | Không thay 1.000 normalized rounds bằng full live MAS stress |

Final acceptance cho toàn named profile **không đạt**. Các explicit UNSUPPORTED không được tính là PASS cho full JaCaMo semantics; các NOT_TESTED/PARTIAL không được che bởi exit 0 của các test suites khác.

## 29. Final verdict

**C. PARTIALLY VERIFIED.**

Đã có nền kiến trúc khả thi và substantial executable evidence: official source extractors/adapters, một native MModel/MSystem per session, typed state/link mutation, actual USE OCL/@pre, iStar recurrence có version, scoped functional/mental/normative views, deterministic replay, finite-table explorer và structural lens. Actual JaCaMo snapshot data đi đến OCL/ACHIEVE/violation/replay được; exact operation pre-state cũng được kiểm bằng 100 real overlaps.

Chưa được dùng kết quả hiện tại để chứng nhận toàn supported monitoring profile: D01/D02 tạo false PASS; D03 làm mất production stream; D04 không chứng minh Task của actor được bind. D05–D07 và các scope gaps cần được ghi như limitations/acceptance work, không gọi DONE chung.

Kiến trúc thực tế được xác minh:

~~~mermaid
flowchart TD
  A[Official static extractors + explicit authored config] --> B[Mapping bundle + trace/complement]
  B --> C[Native USE schema/binding]
  R[Actual Jason / CArtAgO / MOISE / NPL] --> S[Source-local snapshots/events]
  S --> P[Admission + identity + serialized native writer]
  C --> P
  P --> O[USE typed objects/links/values + OCL]
  O --> G[iStar temporal/requirement monitor]
  G --> E[Evidence + journal + replay]
  P -. D01 stale-cut defect .-> E
  O -. D02 population coverage defect .-> E
  R -. D03 norm stream admission .-> P
  G -. D04 performer attribution .-> E
~~~

Một lượt sửa sau cần xác nhận lại các counterexamples đã lưu, rồi kiểm accepted live subscription đến hết scenario và package path; audit này không sửa code hoặc quyết định thay semantics để làm test xanh. Full JaCaMo behavioral equivalence, unbounded model checking và semantic/runtime bidirectionality tiếp tục UNSUPPORTED. Không có bộ native USE class nào tự bổ sung được các source semantics còn thiếu.

## 30. Exact commands used và evidence index

### 30.1. Build/runner commands chính xác

PowerShell; working directory mặc định D:/_CODE_BANK/Project_/08_Thesis/use; paths cố định của môi trường audit này.

~~~powershell
$env:JAVA_HOME='D:\DevTools\Java\jdk-21.0.5'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
$mvn='D:\DevTools\apache-maven-3.9.9-bin\apache-maven-3.9.9\bin\mvn.cmd'

# Trước clean: các absolute targets đã được kiểm nằm trong resolved USE root.
& $mvn -o clean

& $mvn -o -fae -pl jacamo-bridge-contract,jacamo-bridge-jacamo,use-core,use-gui,use-plugin test-compile org.apache.maven.plugins:maven-surefire-plugin:3.2.5:test '-Dtest=*Test' '-Dsurefire.failIfNoSpecifiedTests=false' *> target/verification-audit/clean-regression.log

& $mvn -o -pl use-plugin -am package '-DskipTests' *> target/verification-audit/clean-package.log

& $mvn -o -pl jacamo-bridge-contract,jacamo-bridge-jacamo,use-core,use-gui,use-plugin test-compile org.apache.maven.plugins:maven-surefire-plugin:3.2.5:test '-Dtest=NativeRuntimePackagedReplayIT,SemanticPackagedReplayIT' '-Dsurefire.failIfNoSpecifiedTests=false' *> target/verification-audit/installed-smokes.log

git status --short
git rev-parse HEAD
git diff --check
~~~

Classpath audit được trích bằng XML property java.class.path từ fresh plugin Surefire report; không resolve local stale snapshots rồi gọi test goal riêng. Với USE core Java module-path, textui runner cần append đầy đủ clean production classes/dependencies; lệnh cuối thành công:

~~~powershell
$coreAuditCp=(Resolve-Path -LiteralPath 'use-core/target/test-classes').Path+';'+(Get-Content -LiteralPath 'target/verification-audit/classpath.txt' -Raw)+';'+(Get-Content -LiteralPath 'target/verification-audit/core-classpath.txt' -Raw)
Push-Location 'use-core'
try {
  & 'D:\DevTools\Java\jdk-21.0.5\bin\java.exe' -Xmx1024m -cp $coreAuditCp junit.textui.TestRunner org.tzi.use.AllTests *> '../target/verification-audit/legacy-core.log'
} finally { Pop-Location }
~~~

### 30.2. Added oracle commands

Working directory use/use-plugin. Source files dưới ../target/verification-audit/src; chỉ harness/oracle tạm được biên dịch, không production edits. Các executable dùng existing fixture factories khi cần source models, nhưng expected assertions/adversarial input được audit viết mới.

~~~powershell
$auditCp=Get-Content -LiteralPath '../target/verification-audit/classpath.txt' -Raw
& 'D:\DevTools\Java\jdk-21.0.5\bin\javac.exe' -proc:none -cp "../target/verification-audit/classes;$auditCp" -d '../target/verification-audit/classes' '../target/verification-audit/src/org/tzi/use/plugins/jacamo/semantics/AuditOracles.java'
& 'D:\DevTools\Java\jdk-21.0.5\bin\java.exe' -Xmx1024m -cp "../target/verification-audit/classes;$auditCp" org.tzi.use.plugins.jacamo.semantics.AuditOracles *> '../target/verification-audit/oracles-1000.log'

& 'D:\DevTools\Java\jdk-21.0.5\bin\javac.exe' -proc:none -cp $auditCp -d '../target/verification-audit/classes' '../target/verification-audit/src/org/tzi/use/plugins/jacamo/semantics/AuditBackend.java'
& 'D:\DevTools\Java\jdk-21.0.5\bin\java.exe' -Xmx1024m -cp "../target/verification-audit/classes;$auditCp" org.tzi.use.plugins.jacamo.semantics.AuditBackend *> '../target/verification-audit/backend.log'

& 'D:\DevTools\Java\jdk-21.0.5\bin\javac.exe' -proc:none -cp $auditCp -d '../target/verification-audit/classes' '../target/verification-audit/src/org/tzi/use/plugins/jacamo/semantics/AuditRecordedFlow.java'
& 'D:\DevTools\Java\jdk-21.0.5\bin\java.exe' -Xmx1024m -cp "../target/verification-audit/classes;$auditCp" org.tzi.use.plugins.jacamo.semantics.AuditRecordedFlow *> '../target/verification-audit/recorded-flow.log'

& 'D:\DevTools\Java\jdk-21.0.5\bin\javac.exe' -proc:none -cp $auditCp -d '../target/verification-audit/classes' '../target/verification-audit/src/org/tzi/use/plugins/jacamo/semantics/AuditReplayInputs.java'
& 'D:\DevTools\Java\jdk-21.0.5\bin\java.exe' -Xmx1024m -cp "../target/verification-audit/classes;$auditCp" org.tzi.use.plugins.jacamo.semantics.AuditReplayInputs *> '../target/verification-audit/corruption.log'
~~~

Live harness dùng absolute child classpath vì child cwd là staged project. Command shape thực thi cuối (harness ghi final fixture và production launcher child; process exit 1 là D03 còn bị assert):

~~~powershell
$auditEntries=$auditCp.Split(';') | Where-Object { $_ -ne '' } | ForEach-Object { [System.IO.Path]::GetFullPath($_) }
$liveCp=([System.IO.Path]::GetFullPath('../target/verification-audit/classes'))+';'+($auditEntries -join ';')
& 'D:\DevTools\Java\jdk-21.0.5\bin\javac.exe' -proc:none -cp "../target/verification-audit/classes;$auditCp" -d '../target/verification-audit/classes' '../target/verification-audit/src/org/tzi/use/plugins/jacamo/semantics/AuditCounter.java' '../target/verification-audit/src/org/tzi/use/plugins/jacamo/semantics/AuditLiveMas.java'
& 'D:\DevTools\Java\jdk-21.0.5\bin\java.exe' -Xmx1024m -cp $liveCp org.tzi.use.plugins.jacamo.semantics.AuditLiveMas *> '../target/verification-audit/live-mas-final.log'

# Actual overlap, lặp executable hai lần; mỗi lần code có iterations=50.
& 'D:\DevTools\Java\jdk-21.0.5\bin\javac.exe' -proc:none -cp "../target/verification-audit/classes;$auditCp" -d '../target/verification-audit/classes' '../target/verification-audit/src/org/tzi/use/plugins/jacamo/semantics/AuditOverlapArtifact.java' '../target/verification-audit/src/org/tzi/use/plugins/jacamo/semantics/AuditLiveOverlap.java'
& 'D:\DevTools\Java\jdk-21.0.5\bin\java.exe' -Xmx1024m -cp "../target/verification-audit/classes;$auditCp" org.tzi.use.plugins.jacamo.semantics.AuditLiveOverlap *> '../target/verification-audit/live-overlap-run1.log'
Copy-Item -LiteralPath '../target/verification-audit/live-overlap-results.json' -Destination '../target/verification-audit/live-overlap-results-run1.json'
& 'D:\DevTools\Java\jdk-21.0.5\bin\java.exe' -Xmx1024m -cp "../target/verification-audit/classes;$auditCp" org.tzi.use.plugins.jacamo.semantics.AuditLiveOverlap *> '../target/verification-audit/live-overlap-run2.log'
Copy-Item -LiteralPath '../target/verification-audit/live-overlap-results.json' -Destination '../target/verification-audit/live-overlap-results-run2.json'
~~~

### 30.3. Installed isolation và bytecode

Working directory root USE:

~~~powershell
& 'D:\DevTools\Java\jdk-21.0.5\bin\javac.exe' -proc:none -cp 'use-gui/target/use-gui.jar' -d 'target/verification-audit/classes' 'target/verification-audit/src/AuditInstalledMain.java'
$auditInstall=Join-Path (Resolve-Path -LiteralPath 'target/verification-audit').Path 'installed/lib/plugins'
New-Item -ItemType Directory -Force -Path $auditInstall | Out-Null
Copy-Item -LiteralPath 'use-plugin/target/use-plugin-1.0.1.jar' -Destination (Join-Path $auditInstall 'use-jacamo-plugin-1.0.1.jar')
& 'D:\DevTools\Java\jdk-21.0.5\bin\java.exe' -Xmx1024m -cp 'use-gui/target/use-gui.jar;target/verification-audit/classes' AuditInstalledMain $auditInstall (Join-Path $auditInstall 'use-jacamo-plugin-1.0.1.jar') 'target/verification-audit/live-mas-poll2/replay' 'target/verification-audit/live-mas-poll2/configuration.json' *> 'target/verification-audit/installed-audit.log'

& 'D:\DevTools\Java\jdk-21.0.5\bin\javap.exe' -classpath 'C:\Users\NTNghia\.m2\repository\org\jacamo\cartago\3.1\cartago-3.1.jar' -p -c cartago.Artifact > 'target/verification-audit/cartago-artifact-bytecode.txt'
~~~

Driver installed đã dùng poll2 bundle có true runtime 0→1→-1; final actual run là independent bundle ở live-mas-final. Không tráo hashes giữa hai runs. Các random UUIDs/source paths có thể làm result hashes khác khi sinh fixture mới; acceptance là hash equality giữa run và replay của **cùng** input, không hash equality giữa các independent runtime incarnations.

Ngoài commands trên: rg/rg --files và numbered source reads; Node fs/hash/XML report inventory; pypdf extract 6 pages và pdftoppm render pages 3/4; Git readonly status/diff/HEAD ba repos. Các outputs tạm đều ở ignored target hoặc module target. Chỉ source-independent report này là authored deliverable mới. Input/output hashes và final preservation được xác nhận lại trước khi ghi báo cáo.

### 30.4. File/class evidence pointers

Các links sau trỏ tới actual files của audit hoặc source đã đọc. Paths có line là entry point kiểm chứng; API/method semantics được mô tả tại section tương ứng. Evidence dưới target có thể mất khi clean; các phản ví dụ, expected/actual và hashes chính đã được ghi ngay trong báo cáo này.

| Source anchor | File / API |
|---|---|
| R | [R](C:/Users/NTNghia/OneDrive/Desktop/report.md) |
| PLAN | [PLAN](D:/_CODE_BANK/Project_/08_Thesis/use/implementation-plan.md) |
| CLAIMS | [CLAIMS](D:/_CODE_BANK/Project_/08_Thesis/use/implementation-report.md) |
| PDF | [PDF](<C:/Users/NTNghia/OneDrive/Desktop/Thesis/CSL/On Checking the Conformance between iStar and BPMN_261012_173041_1.pdf>) |
| ECORE | [ECORE](C:/Users/NTNghia/OneDrive/Desktop/Thesis/UML/jacamo_v2.ecore) |
| BASELINE | [BASELINE](D:/_CODE_BANK/Project_/08_Thesis/use/target/verification-audit/baseline.json) |
| PRESERVE | [PRESERVE](D:/_CODE_BANK/Project_/08_Thesis/use/target/verification-audit/source-preservation-check.json) |
| CLEANLOG | [CLEANLOG](D:/_CODE_BANK/Project_/08_Thesis/use/target/verification-audit/clean-regression.log) |
| COUNTS | [COUNTS](D:/_CODE_BANK/Project_/08_Thesis/use/target/verification-audit/clean-test-inventory.json) |
| PACKAGELOG | [PACKAGELOG](D:/_CODE_BANK/Project_/08_Thesis/use/target/verification-audit/clean-package.log) |
| PKGHASH | [PKGHASH](D:/_CODE_BANK/Project_/08_Thesis/use/target/verification-audit/package-hashes.json) |
| SMOKELOG | [SMOKELOG](D:/_CODE_BANK/Project_/08_Thesis/use/target/verification-audit/installed-smokes.log) |
| INSTALLED | [INSTALLED](D:/_CODE_BANK/Project_/08_Thesis/use/target/verification-audit/installed-audit.log) |
| LEGACY | [LEGACY](D:/_CODE_BANK/Project_/08_Thesis/use/target/verification-audit/legacy-core.log) |
| ANCHORS | [ANCHORS](D:/_CODE_BANK/Project_/08_Thesis/use/target/verification-audit/matrix-anchor-audit.json) |
| ORACLES | [ORACLES](D:/_CODE_BANK/Project_/08_Thesis/use/target/verification-audit/oracle-results.json) |
| BACKEND | [BACKEND](D:/_CODE_BANK/Project_/08_Thesis/use/target/verification-audit/backend-results.json) |
| LIVEFINAL | [LIVEFINAL](D:/_CODE_BANK/Project_/08_Thesis/use/target/verification-audit/live-mas-final/summary.json) |
| LIVEHARNESS | [LIVEHARNESS](D:/_CODE_BANK/Project_/08_Thesis/use/target/verification-audit/src/org/tzi/use/plugins/jacamo/semantics/AuditLiveMas.java) |
| RECORDED | [RECORDED](D:/_CODE_BANK/Project_/08_Thesis/use/target/verification-audit/recorded-flow-results.json) |
| NPLREPRO | [NPLREPRO](D:/_CODE_BANK/Project_/08_Thesis/use/target/verification-audit/npl-admission.json) |
| OVERLAP1 | [OVERLAP1](D:/_CODE_BANK/Project_/08_Thesis/use/target/verification-audit/live-overlap-results-run1.json) |
| OVERLAP2 | [OVERLAP2](D:/_CODE_BANK/Project_/08_Thesis/use/target/verification-audit/live-overlap-results-run2.json) |
| CORRUPT | [CORRUPT](D:/_CODE_BANK/Project_/08_Thesis/use/target/verification-audit/corruption-results.json) |
| PERF | [PERF](D:/_CODE_BANK/Project_/08_Thesis/use/target/verification-audit/fresh-performance.json) |
| TRACE | [TRACE](D:/_CODE_BANK/Project_/08_Thesis/use/target/verification-audit/traceability-sample.json) |
| LAYERS | [LAYERS](D:/_CODE_BANK/Project_/08_Thesis/use/target/verification-audit/layer-import-scan.txt) |
| QUALITY | [QUALITY](D:/_CODE_BANK/Project_/08_Thesis/use/target/verification-audit/code-quality-scan.txt) |
| AUDITSRC | [AUDITSRC](D:/_CODE_BANK/Project_/08_Thesis/use/target/verification-audit/src) |
| C-AWAIT | [C-AWAIT](C:/Users/NTNghia/AppData/Local/Temp/codex-use-jacamo-audit/cartago-v3.1/src/main/java/cartago/Artifact.java:810) |
| C-BYTECODE | [C-BYTECODE](D:/_CODE_BANK/Project_/08_Thesis/use/target/verification-audit/cartago-artifact-bytecode.txt) |
| CP-PLUGIN | [CP-PLUGIN](D:/_CODE_BANK/Project_/08_Thesis/use/target/verification-audit/classpath.txt) |
| CP-ADAPTER | [CP-ADAPTER](D:/_CODE_BANK/Project_/08_Thesis/use/jacamo-bridge-jacamo/target/surefire-reports/TEST-org.jacamo.bridge.adapter.NplLiveBoardObservationTest.xml) |
| G-MARK | [G-MARK](D:/_CODE_BANK/Project_/08_Thesis/use4Goal/goal/src/main/java/org/vnu/sme/goal/verify/conformance/semantics/GoalMarking.java:16) |
| G-TASK | [G-TASK](D:/_CODE_BANK/Project_/08_Thesis/use4Goal/goal/src/main/java/org/vnu/sme/goal/verify/conformance/semantics/TaskMarking.java:7) |
| S-MAPPING | [S-MAPPING](D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/mapping/SemanticTransformationService.java:11) |
| S-RULES | [S-RULES](D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/mapping/MappingRuleCatalog.java:14) |
| S-ORG | [S-ORG](D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/mapping/OrganizationalMappingRules.java:12) |
| S-CSL | [S-CSL](D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/csl/CslUseBinding.java:19) |
| S-TEMP | [S-TEMP](D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/istar/TemporalGoalMonitor.java:24) |
| S-COORD | [S-COORD](D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/codegrounded/runtime/RuntimeVerificationCoordinator.java:157) |
| S-PROJ | [S-PROJ](D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/codegrounded/runtime/NativeRuntimeProjector.java:100) |
| S-COVER | [S-COVER](D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/ocl/ConstraintCoverageResolver.java:12) |
| S-OCL | [S-OCL](D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/ocl/SemanticOclCompiler.java:35) |
| S-EVAL | [S-EVAL](D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/ocl/OclEvaluationAdapter.java) |
| S-VERIFY | [S-VERIFY](D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/runtime/SemanticRuntimeVerifier.java:112) |
| S-BIND | [S-BIND](D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/istar/RequirementBindingResolver.java:16) |
| S-CARTAGO | [S-CARTAGO](D:/_CODE_BANK/Project_/08_Thesis/use/jacamo-bridge-jacamo/src/main/java/org/jacamo/bridge/adapter/CartagoSnapshotSource.java:361) |
| S-SNAPSHOT | [S-SNAPSHOT](D:/_CODE_BANK/Project_/08_Thesis/use/jacamo-bridge-jacamo/src/main/java/org/jacamo/bridge/adapter/SnapshotCoordinator.java) |
| S-JASON | [S-JASON](D:/_CODE_BANK/Project_/08_Thesis/use/jacamo-bridge-jacamo/src/main/java/org/jacamo/bridge/adapter/JasonCycleObservation.java) |
| S-NPL | [S-NPL](D:/_CODE_BANK/Project_/08_Thesis/use/jacamo-bridge-jacamo/src/main/java/org/jacamo/bridge/adapter/NplBoardSnapshotSource.java:43) |
| S-NPLCROSS | [S-NPLCROSS](D:/_CODE_BANK/Project_/08_Thesis/use/jacamo-bridge-jacamo/src/main/java/org/jacamo/bridge/adapter/NplActivationCrosswalk.java:14) |
| S-MIRROR | [S-MIRROR](D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/bridge/BridgeMirrorStateMachine.java:40) |
| S-REPLAY | [S-REPLAY](D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/codegrounded/runtime/NativeRuntimeReplay.java:29) |
| S-EXPLORER | [S-EXPLORER](D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/exploration/BoundedExplorer.java:17) |
| S-LENS | [S-LENS](D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/roundtrip/SourceLens.java:16) |
| S-OPLAN | [S-OPLAN](D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/model/ExecutionDescriptor.java) |
| U-STATE | [U-STATE](D:/_CODE_BANK/Project_/08_Thesis/use/use-core/src/main/java/org/tzi/use/uml/sys/MSystemState.java:134) |
| U-API | [U-API](D:/_CODE_BANK/Project_/08_Thesis/use/use-core/src/main/java/org/tzi/use/api/UseSystemApi.java) |
| U-EVAL | [U-EVAL](D:/_CODE_BANK/Project_/08_Thesis/use/use-core/src/main/java/org/tzi/use/uml/ocl/expr/Evaluator.java:36) |

Fresh matrix test source files:

- [CslStructuralPolicyTest](D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/test/java/org/tzi/use/plugins/jacamo/semantics/CslStructuralPolicyTest.java)
- [CslVerticalSliceTest](D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/test/java/org/tzi/use/plugins/jacamo/semantics/CslVerticalSliceTest.java)
- [ObserverIdentityLifecycleTest](D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/test/java/org/tzi/use/plugins/jacamo/semantics/ObserverIdentityLifecycleTest.java)
- [SharedStateAcceptanceTest](D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/test/java/org/tzi/use/plugins/jacamo/semantics/SharedStateAcceptanceTest.java)
- [TemporalGoalMonitorTest](D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/test/java/org/tzi/use/plugins/jacamo/semantics/TemporalGoalMonitorTest.java)
- [SnapshotCoordinatorTest](D:/_CODE_BANK/Project_/08_Thesis/use/jacamo-bridge-jacamo/src/test/java/org/jacamo/bridge/adapter/SnapshotCoordinatorTest.java)
- [RuntimeVerificationCoordinatorTest](D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/test/java/org/tzi/use/plugins/jacamo/codegrounded/RuntimeVerificationCoordinatorTest.java)
- [MentalStateProjectionTest](D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/test/java/org/tzi/use/plugins/jacamo/semantics/MentalStateProjectionTest.java)
- [JasonStructuredObservationTest](D:/_CODE_BANK/Project_/08_Thesis/use/jacamo-bridge-jacamo/src/test/java/org/jacamo/bridge/adapter/JasonStructuredObservationTest.java)
- [CslSchemaTest](D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/test/java/org/tzi/use/plugins/jacamo/semantics/CslSchemaTest.java)
- [OPlanSemanticsTest](D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/test/java/org/tzi/use/plugins/jacamo/semantics/OPlanSemanticsTest.java)
- [NativeInvocationSemanticsTest](D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/test/java/org/tzi/use/plugins/jacamo/codegrounded/runtime/NativeInvocationSemanticsTest.java)
- [OclSemanticBackendTest](D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/test/java/org/tzi/use/plugins/jacamo/semantics/OclSemanticBackendTest.java)
- [FunctionalObservationTest](D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/test/java/org/tzi/use/plugins/jacamo/semantics/FunctionalObservationTest.java)
- [FunctionalLiveBoardTest](D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/test/java/org/tzi/use/plugins/jacamo/semantics/FunctionalLiveBoardTest.java)
- [SemanticsProfileTest](D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/test/java/org/tzi/use/plugins/jacamo/semantics/SemanticsProfileTest.java)
- [NplLiveBoardObservationTest](D:/_CODE_BANK/Project_/08_Thesis/use/jacamo-bridge-jacamo/src/test/java/org/jacamo/bridge/adapter/NplLiveBoardObservationTest.java)
- [NormativeObservationTest](D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/test/java/org/tzi/use/plugins/jacamo/semantics/NormativeObservationTest.java)
- [SourceLensTest](D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/test/java/org/tzi/use/plugins/jacamo/semantics/SourceLensTest.java)
- [NativeRuntimePackagedReplayIT](D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/test/java/org/tzi/use/plugins/jacamo/codegrounded/NativeRuntimePackagedReplayIT.java)
- [BoundedExplorerTest](D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/test/java/org/tzi/use/plugins/jacamo/semantics/BoundedExplorerTest.java)
- [VerificationLoadScenarioTest](D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/test/java/org/tzi/use/plugins/jacamo/semantics/VerificationLoadScenarioTest.java)

[R]: <C:/Users/NTNghia/OneDrive/Desktop/report.md>
[PLAN]: <D:/_CODE_BANK/Project_/08_Thesis/use/implementation-plan.md>
[CLAIMS]: <D:/_CODE_BANK/Project_/08_Thesis/use/implementation-report.md>
[PDF]: <C:/Users/NTNghia/OneDrive/Desktop/Thesis/CSL/On Checking the Conformance between iStar and BPMN_261012_173041_1.pdf>
[ECORE]: <C:/Users/NTNghia/OneDrive/Desktop/Thesis/UML/jacamo_v2.ecore>
[BASELINE]: <D:/_CODE_BANK/Project_/08_Thesis/use/target/verification-audit/baseline.json>
[PRESERVE]: <D:/_CODE_BANK/Project_/08_Thesis/use/target/verification-audit/source-preservation-check.json>
[CLEANLOG]: <D:/_CODE_BANK/Project_/08_Thesis/use/target/verification-audit/clean-regression.log>
[COUNTS]: <D:/_CODE_BANK/Project_/08_Thesis/use/target/verification-audit/clean-test-inventory.json>
[PACKAGELOG]: <D:/_CODE_BANK/Project_/08_Thesis/use/target/verification-audit/clean-package.log>
[PKGHASH]: <D:/_CODE_BANK/Project_/08_Thesis/use/target/verification-audit/package-hashes.json>
[SMOKELOG]: <D:/_CODE_BANK/Project_/08_Thesis/use/target/verification-audit/installed-smokes.log>
[INSTALLED]: <D:/_CODE_BANK/Project_/08_Thesis/use/target/verification-audit/installed-audit.log>
[LEGACY]: <D:/_CODE_BANK/Project_/08_Thesis/use/target/verification-audit/legacy-core.log>
[ANCHORS]: <D:/_CODE_BANK/Project_/08_Thesis/use/target/verification-audit/matrix-anchor-audit.json>
[ORACLES]: <D:/_CODE_BANK/Project_/08_Thesis/use/target/verification-audit/oracle-results.json>
[BACKEND]: <D:/_CODE_BANK/Project_/08_Thesis/use/target/verification-audit/backend-results.json>
[LIVEFINAL]: <D:/_CODE_BANK/Project_/08_Thesis/use/target/verification-audit/live-mas-final/summary.json>
[LIVEHARNESS]: <D:/_CODE_BANK/Project_/08_Thesis/use/target/verification-audit/src/org/tzi/use/plugins/jacamo/semantics/AuditLiveMas.java>
[RECORDED]: <D:/_CODE_BANK/Project_/08_Thesis/use/target/verification-audit/recorded-flow-results.json>
[NPLREPRO]: <D:/_CODE_BANK/Project_/08_Thesis/use/target/verification-audit/npl-admission.json>
[OVERLAP1]: <D:/_CODE_BANK/Project_/08_Thesis/use/target/verification-audit/live-overlap-results-run1.json>
[OVERLAP2]: <D:/_CODE_BANK/Project_/08_Thesis/use/target/verification-audit/live-overlap-results-run2.json>
[CORRUPT]: <D:/_CODE_BANK/Project_/08_Thesis/use/target/verification-audit/corruption-results.json>
[PERF]: <D:/_CODE_BANK/Project_/08_Thesis/use/target/verification-audit/fresh-performance.json>
[TRACE]: <D:/_CODE_BANK/Project_/08_Thesis/use/target/verification-audit/traceability-sample.json>
[LAYERS]: <D:/_CODE_BANK/Project_/08_Thesis/use/target/verification-audit/layer-import-scan.txt>
[QUALITY]: <D:/_CODE_BANK/Project_/08_Thesis/use/target/verification-audit/code-quality-scan.txt>
[AUDITSRC]: <D:/_CODE_BANK/Project_/08_Thesis/use/target/verification-audit/src>
[C-AWAIT]: <C:/Users/NTNghia/AppData/Local/Temp/codex-use-jacamo-audit/cartago-v3.1/src/main/java/cartago/Artifact.java:810>
[C-BYTECODE]: <D:/_CODE_BANK/Project_/08_Thesis/use/target/verification-audit/cartago-artifact-bytecode.txt>
[CP-PLUGIN]: <D:/_CODE_BANK/Project_/08_Thesis/use/target/verification-audit/classpath.txt>
[CP-ADAPTER]: <D:/_CODE_BANK/Project_/08_Thesis/use/jacamo-bridge-jacamo/target/surefire-reports/TEST-org.jacamo.bridge.adapter.NplLiveBoardObservationTest.xml>
[G-MARK]: <D:/_CODE_BANK/Project_/08_Thesis/use4Goal/goal/src/main/java/org/vnu/sme/goal/verify/conformance/semantics/GoalMarking.java:16>
[G-TASK]: <D:/_CODE_BANK/Project_/08_Thesis/use4Goal/goal/src/main/java/org/vnu/sme/goal/verify/conformance/semantics/TaskMarking.java:7>
[S-MAPPING]: <D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/mapping/SemanticTransformationService.java:11>
[S-RULES]: <D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/mapping/MappingRuleCatalog.java:14>
[S-ORG]: <D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/mapping/OrganizationalMappingRules.java:12>
[S-CSL]: <D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/csl/CslUseBinding.java:19>
[S-TEMP]: <D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/istar/TemporalGoalMonitor.java:24>
[S-COORD]: <D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/codegrounded/runtime/RuntimeVerificationCoordinator.java:157>
[S-PROJ]: <D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/codegrounded/runtime/NativeRuntimeProjector.java:100>
[S-COVER]: <D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/ocl/ConstraintCoverageResolver.java:12>
[S-OCL]: <D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/ocl/SemanticOclCompiler.java:35>
[S-EVAL]: <D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/ocl/OclEvaluationAdapter.java>
[S-VERIFY]: <D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/runtime/SemanticRuntimeVerifier.java:112>
[S-BIND]: <D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/istar/RequirementBindingResolver.java:16>
[S-CARTAGO]: <D:/_CODE_BANK/Project_/08_Thesis/use/jacamo-bridge-jacamo/src/main/java/org/jacamo/bridge/adapter/CartagoSnapshotSource.java:361>
[S-SNAPSHOT]: <D:/_CODE_BANK/Project_/08_Thesis/use/jacamo-bridge-jacamo/src/main/java/org/jacamo/bridge/adapter/SnapshotCoordinator.java>
[S-JASON]: <D:/_CODE_BANK/Project_/08_Thesis/use/jacamo-bridge-jacamo/src/main/java/org/jacamo/bridge/adapter/JasonCycleObservation.java>
[S-NPL]: <D:/_CODE_BANK/Project_/08_Thesis/use/jacamo-bridge-jacamo/src/main/java/org/jacamo/bridge/adapter/NplBoardSnapshotSource.java:43>
[S-NPLCROSS]: <D:/_CODE_BANK/Project_/08_Thesis/use/jacamo-bridge-jacamo/src/main/java/org/jacamo/bridge/adapter/NplActivationCrosswalk.java:14>
[S-MIRROR]: <D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/bridge/BridgeMirrorStateMachine.java:40>
[S-REPLAY]: <D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/codegrounded/runtime/NativeRuntimeReplay.java:29>
[S-EXPLORER]: <D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/exploration/BoundedExplorer.java:17>
[S-LENS]: <D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/roundtrip/SourceLens.java:16>
[S-OPLAN]: <D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/model/ExecutionDescriptor.java>
[U-STATE]: <D:/_CODE_BANK/Project_/08_Thesis/use/use-core/src/main/java/org/tzi/use/uml/sys/MSystemState.java:134>
[U-API]: <D:/_CODE_BANK/Project_/08_Thesis/use/use-core/src/main/java/org/tzi/use/api/UseSystemApi.java>
[U-EVAL]: <D:/_CODE_BANK/Project_/08_Thesis/use/use-core/src/main/java/org/tzi/use/uml/ocl/expr/Evaluator.java:36>
