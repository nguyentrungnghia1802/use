# Kế hoạch implementation chính thức — JaCaMo → CSL/iStar/OCL → USE 7.5

Ngày lập: **17/10/2026**, Asia/Bangkok. Repository triển khai duy nhất: `D:/_CODE_BANK/Project_/08_Thesis/use`.

**Trạng thái triển khai: DONE — Phase 0–22 hoàn tất với executable evidence cho `USE4GOAL_OBSERVED_V1`. Kiến trúc đã xác minh: external JaCaMo/TCP → versioned mapping → original CSL/iStar → native USE7.5 OCL, một authoritative Session/MSystem.** Lượt coding hiện tại đã được người dùng cho phép. Checkbox chỉ được đánh dấu theo executable evidence; canonical runtime đã gọi original engine trên native USE state và actual CArtAgO/MOISE trong focused tests. New-profile external JaCaMo/TCP live acceptance Phase16 đã pass; GUI gate Phase17 đã pass; canonical replay Phase18 và source-run/package replay Phase20 đã pass; production Counter/full archive Phase21 và final clean/full archive Phase22 đều pass; không coi test artifact trong JVM của JUnit là live MAS external.

Execution checkpoint 17/10/2026: branch `main`, HEAD giữ nguyên. Phase 0 pass: 693 unit/regression + 151 integration/package + 198 JUnit 3 tests, không failure/error/skip. Evidence bền vững tại `use-plugin/verification/semantic-migration/phase-0/acceptance.json`, `baseline-test-suites.json`, `baseline-verify.log`; original marking probe 7 checks và native synthetic probe 200 boundaries (không phải live new engine) có logs/hashes. Architecture/profile freeze ở `use-plugin/docs/project/use4goal-engine-architecture.md` / `use4goal-observed-v1.json`. Source correction đã chốt: default frame 16 MiB, không phải 4 MiB trong audit; không thay source quota. Canonical runtime/core integration đã được focused-test với actual CArtAgO/MOISE; external new-profile MAS gate Phase16 đã pass. Phase 1 inventory/original oracle đã pass; Phase 2 extraction/module wiring đã pass; Phase 3 host linkage/package đã pass; Phase 4 A/B/C parity đã pass; Phase 5 canonical backend/H + current regression đã pass; Phase 6 D/frontend admission đã pass; Phase 7 E/F/G và contextual seam đã pass; Phase 8 I/J/native adapters đã pass; Phase 9 versioned mapping contracts đã pass; Phase 10 K/canonical static native route đã pass; Phase 11 L/canonical goal definitions and bindings đã pass; Phase 12 canonical/native contract mapping and full regression đã pass; Phase 13 canonical runtime state mapping đã pass (821 reactor tests); Phase 14 runtime owner/session acceptance đã pass; Phase 15 transaction/fault/publication gate đã pass (848 reactor tests); Phase16 external live/lifecycle/direct-feed parity đã pass (r17), full r16 replay regression đã sửa/focused pass; Phase17 UI/live/heartbeat/legacy replay acceptance đã pass (44 focused tests r5); Phase18 replay acceptance đã pass (32 focused + 2 installed compatibility tests); Phase19 routing/expected-difference gate đã pass; Phase20 full R6 đã pass (869 Surefire +155 Failsafe +198 legacy JUnit3, không failure/error/skip); Phase21 full R9/distribution đã pass (878 Surefire +159 Failsafe +198 legacy JUnit3 và repeated full archive); Phase22 clean/full regression đã pass (880 Surefire +159 Failsafe +198 legacy JUnit3), strict A–V và repeated full USE ZIP live acceptance4/4 pass; final DoD17/17 có evidence.

## 1. Phạm vi, baseline và nguồn có thẩm quyền

Đã đọc **toàn bộ 916 dòng, gồm cả 23 mục và bảng evidence**, của [báo cáo audit Desktop](C:/Users/NTNghia/OneDrive/Desktop/report.md). SHA-256 của báo cáo: `E8F3A60B11C52B73136EA2BBAEDC92F40F9B70FF1C670D1BF70D1D49A43AE7DC`. Báo cáo này là input, không bị xóa hay sửa. Kết luận về embedded trong báo cáo thuộc mục tiêu trước; **không phải prerequisite của kế hoạch này**.

| Repository | HEAD đã đối chiếu | Quyền trong implementation tương lai |
|---|---|---|
| `use` | `6fdfe5ee584b97a48f961e74e6e218d2c3020210`, branch `main` | Thay đổi theo từng phase, bảo toàn các chức năng hiện tại; không tự reset/clean hoặc tạo branch |
| `../use4Goal` | `6f3e73801239fd8bfb94ca8d04ba531b903c0ac9` | **REFERENCE_ONLY**; không sửa, build tại chỗ, hay deploy vào distribution của reference |
| `../jacamo` | `04fc08df259d94045f6cfc95637d56faa1cb33b2` | **REFERENCE_ONLY**; không sửa. Live fixture/staging và evidence tương lai đặt dưới `use/.../target` |

Baseline `use` sạch theo Git trước công việc; `implementation-plan.md` cũ là file tracked và không được lấy làm bằng chứng implementation hiện tại. Reference có sẵn hai thay đổi `Classroom/classroom.soil`, `Classroom/classroom.use`, thư mục `META-INF/`, và hai thay đổi Auction `.jcm`/`.jcm-deps.gradle`; phải bảo toàn. Tại thời điểm thay plan, `use/implementation-plan.md` cũ tồn tại và đã xóa theo yêu cầu; `use/report.md` không tồn tại, không có file nào khác bị xóa. `use/implementation-report.md` có sẵn, được giữ nguyên; không tạo hoặc thay thế file đó.

### Quy ước trạng thái và bằng chứng

- `TODO`: việc chưa thực hiện trong kiến trúc đích.
- `EXISTING`: đã xác minh có source; **không đồng nghĩa** đáp ứng kiến trúc mới hoặc test pass.
- `PARTIAL`: có một phần implementation/capability, còn khoảng trống cụ thể.
- `BLOCKED`: thiếu điều kiện bắt buộc có tên; không tự thay bằng suy đoán.
- `UNSUPPORTED`: feature nằm ngoài capability của profile; phải có diagnostic và trace, không phải constraint violated.
- `DONE`: chỉ dùng trong lượt implementation khi required task/tests/AC của phase có executable evidence; không suy phase pass thành toàn project/DoD đã pass.

Mọi class/interface/method ghi **MỚI** bên dưới là API dự kiến của dự án, không phải API đã tồn tại của JaCaMo/use4Goal/USE. Trước mỗi phần sửa, coding agent phải mở lại source thực tế; nếu baseline đã đổi thì ghi delta vào provenance của implementation, cập nhật checkpoint liên quan và giữ nguyên các invariant kiến trúc trong mục 3. Không dùng số test/lần build hoặc artifact cũ làm bằng chứng migration mới pass.

### Quy ước đường dẫn

Tất cả đường dẫn không có ổ đĩa tương đối với root `use/`:

| Ký hiệu | Mở rộng chính xác |
|---|---|
| `P/` | `use-plugin/src/main/java/org/tzi/use/plugins/jacamo/` |
| `S/` | `P/semantics/` |
| `R/` | `P/codegrounded/runtime/` |
| `B/` | `jacamo-bridge-jacamo/src/main/java/org/jacamo/bridge/adapter/` |
| `C/` | `jacamo-bridge-contract/src/main/java/org/jacamo/bridge/contract/` |
| `G/` | `../use4Goal/goal/src/main/java/org/vnu/sme/goal/` — chỉ đọc |
| `GR/` | `../use4Goal/goal/src/main/resources/` — chỉ đọc |
| `GT/` | `../use4Goal/goal/src/test/java/org/vnu/sme/goal/` — chỉ đọc |
| `E/` | **MỚI** `use-jacamo-semantic-engine/src/main/java/org/vnu/sme/goal/` — original FQCN |
| `A/` | **MỚI** `use-jacamo-semantic-engine/src/main/java/org/tzi/use/semantic/engine/` — adapters trung lập |
| `ET/` | **MỚI** `use-jacamo-semantic-engine/src/test/java/` |
| `PT/` | `use-plugin/src/test/java/org/tzi/use/plugins/jacamo/` |

## 2. Current system — evidence đã đối chiếu source

### 2.1. Call graph và APIs thật

| ID / source thật | Method/cơ chế đã kiểm | Trạng thái và quyết định |
|---|---|---|
| U01 — [reactor POM](pom.xml), [plugin POM](use-plugin/pom.xml) | USE `7.5.0`, Java 21; modules contract, official adapter, assembly/core/gui/plugin; plugin `1.0.1` | EXISTING; không có module/dependency engine `goal`, không còn embedded provider |
| U02 — [OfficialProjectAdapter](jacamo-bridge-jacamo/src/main/java/org/jacamo/bridge/adapter/OfficialProjectAdapter.java:44), [OfficialMoiseAdapter](jacamo-bridge-jacamo/src/main/java/org/jacamo/bridge/adapter/OfficialMoiseAdapter.java:64) | `adapt(JaCaMoProject,Path)`; `load()` → `OS.loadOSFromURI()`; `capture()` | KEEP official model/provenance; declaration không chứng minh runtime population |
| U03 — [JaCaMoBridgePlatform](jacamo-bridge-jacamo/src/main/java/org/jacamo/bridge/adapter/JaCaMoBridgePlatform.java:125), [SnapshotCoordinator](jacamo-bridge-jacamo/src/main/java/org/jacamo/bridge/adapter/SnapshotCoordinator.java:69) | `start()`, `capture(revision,maxAttempts)`, source-local cuts, events; `stop()/close()` | KEEP producer/TCP/Jason–CArtAgO–MOISE–NPL observers; không global atomic clock |
| U04 — [BridgeClient](use-plugin/src/main/java/org/tzi/use/plugins/jacamo/bridge/BridgeClient.java:65) | `synchronize(Runnable beforeSnapshot)` subscribe trước snapshot; bounded buffering, watermark suppression; `control()`, `close()` | KEEP negotiation/admission/old subscription fence; không thêm semantic rule vào transport |
| U05 — [DefaultJaCaMoFacade](use-plugin/src/main/java/org/tzi/use/plugins/jacamo/DefaultJaCaMoFacade.java:158) | `importProject()` → `synchronizeBridge()` → `buildNativeSemantic()`; Start/Pause/Resume, resync/disconnect | MODIFY chọn program/session mới; KEEP identity, activation ticket và old-owner revocation |
| U06 — [CodeGroundedNativePipeline](use-plugin/src/main/java/org/tzi/use/plugins/jacamo/codegrounded/CodeGroundedNativePipeline.java:20) | `build(snapshot,mode[,configuration])`; `buildSemanticBundle()` kiểm source hash/graph, nhưng import production hiện gọi `build()` | ADAPT thêm explicit program path, không giả định auto-derive đã nối UI |
| U07 — [NativeUseModelBuilder](use-plugin/src/main/java/org/tzi/use/plugins/jacamo/codegrounded/use/NativeUseModelBuilder.java), [NativeUseStateBuilder](use-plugin/src/main/java/org/tzi/use/plugins/jacamo/codegrounded/use/NativeUseStateBuilder.java:21), [NativeUseSessionActivator](use-plugin/src/main/java/org/tzi/use/plugins/jacamo/codegrounded/NativeUseSessionActivator.java:7) | `buildCsl()` dùng local `CslUseBinding`; state builder tạo `MSystem`; CSL initial population rỗng; activator `Session.setSystem()` trên EDT | KEEP một state owner; backend mới trả model/binding vào cùng pipeline |
| U08 — [NativeRuntimeProjector](use-plugin/src/main/java/org/tzi/use/plugins/jacamo/codegrounded/runtime/NativeRuntimeProjector.java:102) | `applySnapshot()`, `apply(RuntimeEvent)`, protocol savepoint/restore, epoch/revision/sequence/incarnation, reconcile | KEEP; chỉ đổi runtime target mapping/session seam |
| U09 — [NativeRuntimeMutationEngine](use-plugin/src/main/java/org/tzi/use/plugins/jacamo/codegrounded/runtime/NativeRuntimeMutationEngine.java) | `savepoint()`, `rollback()`, native mutation, typed identity/index; semantic Port hiện `connect(first,second)` là binary | KEEP writer; ADAPT generic n-ary mutation riêng nếu profile admit; không suy MModel support đồng nghĩa writer đã support |
| U10 — [RuntimeVerificationCoordinator](use-plugin/src/main/java/org/tzi/use/plugins/jacamo/codegrounded/runtime/RuntimeVerificationCoordinator.java:323) | `transaction()` có native/monitor/operation savepoints; `acceptedSnapshot()` reset khi run/generation/revision đổi; `finish()` native OCL, semantic evaluate, journal, UI; `read()` marshal EDT | KEEP coordinator, MODIFY staged engine savepoints/commit; đọc cảnh báo transaction tại mục 8 |
| U11 — [NativeOperationCheckpoints](use-plugin/src/main/java/org/tzi/use/plugins/jacamo/codegrounded/runtime/NativeOperationCheckpoints.java) | `observe()`, `savepoint()/restore()`, `clear()`, exact correlation/signature/artifact/performer, pinned pre-state | KEEP invocation conformance; không thay bằng historical TaskMarking |
| U12 — [SemanticOclCompiler](use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/ocl/SemanticOclCompiler.java:15), [OclEvaluationAdapter](use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/ocl/OclEvaluationAdapter.java:14) | `predicate()`, `transitionPredicate()`, `condition()`; native Boolean type gate; `Evaluator.eval(expr,before,after,bindings)` và Value classification | KEEP/generalize sang adapter trung lập; không downgrade |
| U13 — [OrganizationalMappingRules](use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/mapping/OrganizationalMappingRules.java:12), [SemanticTransformationService](use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/mapping/SemanticTransformationService.java:11) | `derive()` nhận domain/contracts/requirements authored; `fromOfficial()/fromEcore()` nhận config, normalize + trace | REUSE_WITH_ADAPTER; chưa có full automatic mapping; không chuyển local IR thành canonical core mới |
| U14 — [SemanticRuntimeVerifier](use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/runtime/SemanticRuntimeVerifier.java:33), [SemanticObservationMapper](use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/runtime/SemanticObservationMapper.java:32), [RequirementBindingResolver](use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/istar/RequirementBindingResolver.java:16) | Local monitor + coverage/observer gates, typed runtime Port, exact player/context/self/episode binding | KEEP quality/correlation/observers, route recurrence theo engine profile |
| U15 — [ManagedRuntimeWorkflow](use-plugin/src/main/java/org/tzi/use/plugins/jacamo/ManagedRuntimeWorkflow.java:43), [RuntimeControlContract](jacamo-bridge-contract/src/main/java/org/jacamo/bridge/contract/RuntimeControlContract.java:16) | File waiting/request/started ACK gắn producer PID; TCP actions **STATUS/PAUSE/RESUME**, không STOP/RESTART | EXISTING external architecture. Disconnect không dừng JaCaMo; Pause chỉ Jason cycle boundary |
| U16 — [NativeRuntimeReplay](use-plugin/src/main/java/org/tzi/use/plugins/jacamo/codegrounded/runtime/NativeRuntimeReplay.java), [PluginClassLoader](use-gui/src/main/java/org/tzi/use/runtime/util/PluginClassLoader.java:18) | Replay model/SOIL/profile/bindings; loader có **static URLClassLoader**, parent delegation, URL set qua các lần đăng ký | KEEP replay cũ theo version; đóng gói một bản engine, không mặc định isolated child-first plugin loader |
| U17 — [JaCaMoLauncher reference](../jacamo/src/main/java/jacamo/infra/JaCaMoLauncher.java:52) | `main()` → `init/create/start`, `finish(...,stopJVM,...)`, có `System.exit` | REFERENCE_ONLY; framework chỉ chạy trong process producer. Không gọi entrypoint này trong USE |

Đường hiện tại: official producer → TCP → BridgeClient → native projector/coordinator → local CSL/iStar + **native USE OCL** → evidence. Việc OCL đã native không chứng minh đã tích hợp `org.vnu.sme.goal`. Profile hiện có `NATIVE_CURRENT`, `CSL_OBSERVER_V1/V2`; trục `NativeProjectionMode.AUTO/FULL` là materialization, không phải lựa chọn engine.

### 2.2. Giữ và chuyển đổi dần

**KEEP**: official adapters; TCP authentication/fingerprint/capability; SnapshotAdmission; sequence/watermark/dedup/identity/incarnation; projector + mutation writer; accepted cut/coverage/gap; native operation checkpoints; source observers; native OCL; Session/MSystem identity; UI/evidence/replay và old profile readers.

**RETIRE_LATER**: `CslSchema`, `RequirementModel`, `CslSchemaValidator` ở vai trò semantic authority mới; `TemporalGoalMonitor`/local recurrence chỉ ở profile mới. Giữ source và replay compatibility cho profile cũ. `CslStructuralPolicyCompiler` có phần backend/source policy cần giữ; không thay mọi kiểm tra đó bằng validator gốc vốn không biết JaCaMo binding.

## 3. Kiến trúc được chốt cho implementation

### 3.1. Deployment và dependency direction

```text
PROCESS A: JaCaMo/Jason/CArtAgO/MOISE/NPL
  official adapters + Snapshot/Event producer + existing Bridge server
                 │ authenticated TCP / neutral bridge contract
PROCESS B: USE 7.5
  BridgeClient → admission/sequence/watermark → versioned JaCaMo mapping
                 │
                 ├─ real CslModel + real GoalModel + typed OCL specs
                 └─ accepted runtime mutation/observation plan
                           ↓
       USE backend + original use4Goal marking/propagation core
                           ↓
       ONE Session.MSystem / MSystemState → native OCL → iStar → result
```

```text
Build dependencies (mũi tên = depends on):
  jacamo-bridge-jacamo → jacamo-bridge-contract + JaCaMo framework
  use-plugin          → jacamo-bridge-contract
  use-plugin          → use-jacamo-semantic-engine → use-core 7.5 [provided]
  use-plugin          → use-gui 7.5 [provided] → use-core
  semantic-engine     → ANTLR4 runtime 4.9.3

Data direction:
  JaCaMo adapters → bridge contract → mapping → engine models
                 → USE backend → authoritative native state

Forbidden:
  semantic-engine X→ use-plugin / bridge DTOs / JaCaMo / use-gui / TCP
  bridge-contract X→ semantic-engine
```

Module engine gồm core gốc và adapters trung lập. Mapping + runtime source policy nằm `use-plugin`; producer không phụ thuộc engine. Thứ tự khai báo Maven không tự thay dependency graph: giữ reactor hiện có, thêm dependency đúng, kiểm cả assembly không tạo cycle. Có thể sắp modules core/gui/engine/plugin/assembly để dễ đọc sau khi kiểm POM assembly; không tạo dependency engine→plugin.

### 3.2. Các invariant không được thương lượng

1. External TCP là đường chạy hợp lệ và phải có live acceptance; không tạo nhiệm vụ phục hồi embedded/JVM loader runtime JaCaMo.
2. `CslModel` và `GoalModel` gốc là canonical M1 của **profile mới**; local IR chỉ compatibility hoặc derived backend data, không quyền quyết định semantics song song.
3. Một `MSystem` authoritative trong phiên active; engine nhận read-view/pinned copies **của cùng system/cut**, không tạo `MSystem` thứ hai để dùng file trace compiler.
4. Mapping rules ở ngoài engine. Thay mapping V1→V2 thay program/hash/bindings, không sửa parser/model/Boolean recurrence gốc.
5. Agent ≠ CSL Entity; belief ≠ authoritative world; Jason active goal ≠ iStar satisfaction; Plan ≠ Task nếu chưa có abstraction; SEQUENCE/PARALLEL ≠ plain AND; Norm ≠ invariant mặc định.
6. `PARTIAL` không phải `COMPLETE`; missing không phải false; unsupported không phải violated. Quality/result phải là dữ liệu độc lập engine Boolean.
7. Runtime writer chỉ áp observation đã admission; cùng state version cho invariant, contract, goal/task, journal và UI. Violation có bằng chứng được ghi nhận, không rollback để che vi phạm.
8. Original core reuse theo file/capability cụ thể; không cam kết mơ hồ “100% toàn plugin”. Patch API và semantic extension là hai loại khác nhau, đều phải có provenance.

### 3.3. Phương án đã so sánh

| Phương án | Lợi ích | Giới hạn | Quyết định |
|---|---|---|---|
| Full `goal-7.1.1-complete.jar` | Reuse binary nhanh | UI/BPMN/solver/legacy, API live thiếu, chưa chứng minh binary 7.5 | REFERENCE_ONLY cho experiment/parity; không chọn production |
| Copy toàn plugin vào `use-plugin` | Dễ gọi Java | Duplicate authority, khó provenance/closure, accidental UI dependency | Không chọn |
| **Extract selected original core vào module riêng** | Compile 7.5, test/hash rõ, giữ FQCN, adapters độc lập | Cần backend v4, session/quality và API seam contextual | **Chọn** |
| Maven dependency engine-only đã publish | Update/distribute tốt | Chỉ hữu ích sau extraction đúng và release artifact | Hình thức phân phối của phương án chọn, không prerequisite repository ngoài |

JAR goal đã audit có **0 `org/tzi/use/` classes**; không viện dẫn duplicate host giả định để loại nó. Release mới vẫn phải scan thực tế. Không chọn dùng nguyên JAR vì boundary/semantics, không vì khẳng định archive hiện tại chứa USE 7.1.1.

## 4. Engine source manifest và dependency closure

### 4.1. Quy tắc extraction

Reuse mode manifest: `ORIGINAL_COPY`, `ORIGINAL_WITH_ADAPTER`, `PATCHED_WITH_PROVENANCE`, `REFERENCE_ONLY`, `DO_NOT_REUSE`. `ORIGINAL_WITH_ADAPTER` vẫn phải giữ hash source gốc; adapter là file khác. Checklist inventory **đã thực thi và verify Phase1–7, final Phase22**. Manifest125 entries ghi đủ working/HEAD/target hashes, FQCN, imports/closure, adapter/patch/license và parity IDs; shared metadata kế thừa từ root manifest. Evidence: `phase-22/original-source-checks.log`, source manifest và A–J trong final matrix.

- [x] Mỗi file extracted có upstream repository/HEAD/path/SHA-256, target path/SHA-256, FQCN, license, reuse mode, dependency closure, patch ID và test ID.
- [x] Ghi working-copy hash; nếu khác `git show HEAD:path` thì ghi dirty delta, không giả file tại HEAD và working copy giống nhau.
- [x] Giữ original FQCN khi hợp lý; lưu GPLv3/license/attribution từ `../use4Goal/LICENSE`. Không copy source rồi bỏ header/notice.
- [x] Ghi generator/version/options/grammar hash cho generated parser; generate từ grammar pinned, không lấy `target` cũ làm source of truth.
- [x] Source inventory/package expansion đóng danh sách ở Phase 1; file mới xuất hiện upstream không tự được include bằng wildcard release.
- [x] Manifest đã tạo và packaged: `use-jacamo-semantic-engine/src/main/resources/META-INF/use4goal-provenance.json`; license/notice dưới cùng `META-INF/licenses/`; patch inventory dưới `use-jacamo-semantic-engine/provenance/`. Lượt planning không tạo; lượt implementation đã tạo, preserve attribution và verify actual archive.

### 4.2. Manifest source cần migrate/tham chiếu

Target `E/...` giữ suffix từ `G/...`; grammar target `use-jacamo-semantic-engine/src/main/resources/grammars/`; generated code ở `target/generated-sources/antlr4`. Không thêm `useplugin.xml` của goal vào module engine.

| Source file/package | Upstream path | Target path | Reuse mode | Patch? |
|---|---|---|---|---|
| CSL grammar | `GR/grammars/CSL.g4` | `.../resources/grammars/CSL.g4` | ORIGINAL_COPY | Không |
| Generated CSL lexer/parser/visitor/listener | Grammar trên, namespace `org.vnu.sme.goal.dsl.csl.parser` | `target/generated-sources/antlr4` | ORIGINAL_COPY (generated provenance) | Không sửa generated code |
| CSL AST: 20 file | `G/dsl/csl/ast/` | `E/dsl/csl/ast/` | ORIGINAL_COPY | Không |
| CSL M1/validator/utilities: 46 file | `G/dsl/csl/mm/` | `E/dsl/csl/mm/` | ORIGINAL_COPY | Không; legacy interfaces thuộc closure không biến thành backend |
| CslCompiler | `G/dsl/csl/parser/CslCompiler.java` | `E/dsl/csl/parser/CslCompiler.java` | ORIGINAL_WITH_ADAPTER | Không; admission ngoài facade |
| CslBuildingVisitor | `G/dsl/csl/parser/CslBuildingVisitor.java` | `E/dsl/csl/parser/CslBuildingVisitor.java` | ORIGINAL_COPY | Không |
| CslModelFactory | `G/dsl/csl/parser/CslModelFactory.java` | `E/dsl/csl/parser/CslModelFactory.java` | ORIGINAL_COPY | Không |
| CslCoreOclSyntax | `G/dsl/csl/parser/CslCoreOclSyntax.java` | `E/dsl/csl/parser/CslCoreOclSyntax.java` | ORIGINAL_WITH_ADAPTER | Không; host syntax only, chưa domain type-check |
| CslOclState | `G/dsl/csl/ocl/CslOclState.java` | `E/dsl/csl/ocl/CslOclState.java` | ORIGINAL_COPY | Không; hai-method protocol |
| NativeOclEvaluator | `G/trace/cslTrace/nativecsl/NativeOclEvaluator.java` | `E/trace/cslTrace/nativecsl/NativeOclEvaluator.java` | ORIGINAL_WITH_ADAPTER | Không; compiler legacy branch còn import; cấm production evaluator route |
| iStar grammar | `GR/grammars/IStar.g4` | `.../resources/grammars/IStar.g4` | ORIGINAL_COPY | Không |
| Generated IStar lexer/parser/visitor/listener | Grammar trên, namespace `org.vnu.sme.goal.dsl.istar.parser` | `target/generated-sources/antlr4` | ORIGINAL_COPY (generated provenance) | Không sửa generated code |
| iStar AST: 6 file | `G/dsl/istar/ast/` | `E/dsl/istar/ast/` | ORIGINAL_COPY | Không |
| iStar M1/validator/activation/context: 27 file | `G/dsl/istar/mm/` | `E/dsl/istar/mm/` | ORIGINAL_COPY | Không; admitted subset |
| IStarCompiler | `G/dsl/istar/parser/IStarCompiler.java` | `E/dsl/istar/parser/IStarCompiler.java` | ORIGINAL_WITH_ADAPTER | Không |
| IStarBuildingVisitor | `G/dsl/istar/parser/IStarBuildingVisitor.java` | `E/dsl/istar/parser/IStarBuildingVisitor.java` | ORIGINAL_WITH_ADAPTER | Không; wrapper chặn syntax bị bỏ mất |
| IStarModelFactory | `G/dsl/istar/parser/IStarModelFactory.java` | `E/dsl/istar/parser/IStarModelFactory.java` | ORIGINAL_COPY | Không |
| GoalMarking | `G/verify/conformance/semantics/GoalMarking.java` | `E/verify/conformance/semantics/GoalMarking.java` | ORIGINAL_COPY | Không đổi recurrence |
| TaskMarking | `G/verify/conformance/semantics/TaskMarking.java` | `E/verify/conformance/semantics/TaskMarking.java` | ORIGINAL_COPY | Không đổi history/status domain |
| IStarMarking | `G/verify/conformance/semantics/IStarMarking.java` | `E/verify/conformance/semantics/IStarMarking.java` | ORIGINAL_WITH_ADAPTER | Không |
| IStarPropagation | `G/verify/conformance/semantics/IStarPropagation.java` | `E/verify/conformance/semantics/IStarPropagation.java` | ORIGINAL_WITH_ADAPTER | Không; không có public `saturate()` |
| GoalTaskStatus / QualityStatus / ContributionPolarity | Ba file cùng `G/verify/conformance/semantics/` | Ba file cùng `E/verify/conformance/semantics/` | ORIGINAL_COPY | Không |
| Contextual instance model | `G/dsl/iscn/goalmodelinstance/mm/GoalModelInstance.java` | Cùng suffix `E/` | ORIGINAL_COPY | Không |
| Contextual evaluation DTO | `G/dsl/iscn/goalmodelinstance/parser/GoalModelInstanceEvaluation.java` | Cùng suffix `E/` | ORIGINAL_COPY | Không |
| Contextual translator | `G/dsl/iscn/goalmodelinstance/parser/GoalModelInstanceTranslator.java` | Cùng suffix `E/` | PATCHED_WITH_PROVENANCE | **P-CTX-01**, API topology metadata và dependency propagation, mô tả dưới |
| Contextual input records/utilities | `G/dsl/iscn/mm/{AggregateMode,AggregateResult,IStarScenarioModel,ScenarioInstance,ScenarioStmt}.java` | Cùng suffix `E/` | ORIGINAL_COPY | Không; không cần ISCN text parser trong live route |
| Native host OCL APIs | `use-core/.../{OCLCompiler,Evaluator,Context,Symtable,Value,...}` | Dependency host, **không copy** | REFERENCE_ONLY | Không sửa host mặc định |
| Original tests/fixtures được chọn | `GT/conformance/semantics/GoalTaskMarkingTest.java`, `GT/csl/view/CslUseViewModelTest.java`, parser/model/context tests theo closure | `ET/...` + test resources/provenance | ORIGINAL_WITH_ADAPTER | Chỉ đổi test host harness/import fixtures khi cần, ghi patch test |
| csl.ecore / istar.ecore | `../use4Goal/docs/model/` | Test/reference resources + hash, không codegen live mặc định | REFERENCE_ONLY | Crosswalk M2, không coi Ecore là Java implementation đã đồng bộ |
| Csl2UseTranslator | `G/translate/csl2use/Csl2UseTranslator.java` | Reference/test oracle legacy; không production backend | DO_NOT_REUSE (v4 backend) | Không bỏ `requireLegacyRuntime()` |
| CslSystemState / Compiler | `G/dsl/aol/state/` | Reference/test fixture legacy, không canonical M0 | REFERENCE_ONLY | Không kéo toàn AOL parser chỉ để có live state |
| CslStateEvaluationSession | `G/verify/cslstate/CslStateEvaluationSession.java` | Reference-only, không migrate live facade | DO_NOT_REUSE (live session) | Private/final/hard-wired subset; chọn session adapter mới |
| IStarUseTraceCompiler / Evaluator / OclConstraintCompiler | `G/trace/istarTrace/` | Reference parity; không live event path | REFERENCE_ONLY | Không expose toàn file/SOIL runner; dùng pure core + P-CTX-01 |
| CslOclPropertyResolver | `G/dsl/csl/ocl/CslOclPropertyResolver.java` | Legacy reference | DO_NOT_REUSE (v4 resolver) | Không rewrite theo play-chain cũ |
| CslOclTransitionEvaluator / CslOclFormulaParser | `G/verify/cslstate/` | Offline/reference | DO_NOT_REUSE (production OCL) | Không regex @pre hoặc subset downgrade |
| CslUseViewModel / UI packages | `G/dsl/csl/view/`, `G/dsl/istar/view/`, actions/diagrams | Reference-only | DO_NOT_REUSE (live backend/UI wholesale) | Không tạo second MModel cho state authority |
| BPMN/checker/symbolic/Kodkod/SAT4J | `G/verify/conformance/`, `G/verify/cslstate/` phần ngoài closure | Optional offline extension ngoài release live ban đầu | REFERENCE_ONLY | Không solver/init install vào module live |
| Full goal plugin descriptor/POM/Main | `GR/useplugin.xml`, `GR/META-INF/MANIFEST.MF`, `../use4Goal/goal/pom.xml` | Không copy | DO_NOT_REUSE | Không duplicate plugin registration/main class |

### 4.3. Đóng danh sách package, dependency, adapter và parity

CSL AST 20 file (đuôi `.java`): `CslAttributeCS`, `CslCardinalityCS`, `CslCardinalityConstraintCS`, `CslCompatibilityCS`, `CslDataTypeCS`, `CslEndpointCS`, `CslEntityCS`, `CslEntityMembershipCS`, `CslEnumCS`, `CslGroupCS`, `CslGroupMemberCS`, `CslInvariantCS`, `CslLinkOptionCS`, `CslModelCS`, `CslRelationCS`, `CslRoleCS`, `CslRoleEntityRelationCS`, `CslRoleMembershipCS`, `CslSourceLocationCS`, `CslSubgroupMembershipCS`.

CSL mm 46 file: `AttributeDefinition`, `Cardinality`, `CardinalityConstraint`, `CardinalityTarget`, `Compatibility`, `CslAttribute`, `CslCardinality`, `CslCardinalityConstraint`, `CslCardinalityTarget`, `CslCardinalityTargetKind`, `CslCompatibility`, `CslCompatibilityType`, `CslContainment`, `CslDataType`, `CslEndpoint`, `CslEntity`, `CslEntityMembership`, `CslEnum`, `CslGeneralization`, `CslGroup`, `CslGroupMember`, `CslInvariant`, `CslModel`, `CslNamedDataType`, `CslOwner`, `CslPrimitiveType`, `CslRelation`, `CslRole`, `CslRoleEntityRelation`, `CslRoleEntityRelationType`, `CslRoleInheritance`, `CslRoleMembership`, `CslRoleRelation`, `CslScope`, `CslSemanticValidator`, `CslSubgroupMembership`, `EntityDefinition`, `GroupEntity`, `GroupSpecification`, `RelationKind`, `RoleDefinition`, `RoleEntityRelation`, `RoleInheritance`, `RoleRelation`, `StructuralSpecification`, `SubGroup`.

iStar AST 6 file: `ActorDefCS`, `DependencyCS`, `ElementBodyCS`, `IStarModelCS`, `IStarOclConstraintCS`, `RelCS`.

iStar mm 27 file: `Actor`, `Agent`, `AndRefinement`, `AssocKind`, `Association`, `ConcreteIntentionalElement`, `ContextResolution`, `Contribution`, `ContributionType`, `Dependency`, `Goal`, `GoalActivationGraph`, `GoalModel`, `GoalModelValidator`, `GoalTaskElement`, `GoalType`, `GoalTypeRefinementPolicy`, `IStarOclConstraint`, `IntentionalElement`, `NeededBy`, `OrRefinement`, `Qualification`, `Quality`, `Refinement`, `Resource`, `Role`, `Task`.

| Checklist nhóm | Dependency closure đã thấy | Reuse/target | Adapter/patch bắt buộc | Parity và acceptance |
|---|---|---|---|---|
| [x] CSL grammar/generated | ANTLR4 4.9.3; header FQCN | Original grammar + generated `E` namespace | Compiler admission MỚI; không sửa parser output | A: accepted/rejected text và vị trí lỗi tương đương |
| [x] CSL AST/mm/validator | JDK, nội bộ AST/mm, legacy interfaces trong mm | REUSE_100_PERCENT, engine | Canonical snapshots cho model mutable/read ownership | B/C: đủ type/default/optional/mutable/owner/end order, validators cùng diagnostics |
| [x] CSL compiler/builder/factory | AST/mm, generated, `CslCoreOclSyntax` → host ANTLR3; `NativeOclEvaluator` → `CslOclState` | REUSE_WITH_ADAPTER, engine | V4 admission + native domain OCL type-check; subset class chỉ closure compatibility | A/B/C/I: không dùng branch legacy cho v4 production |
| [x] iStar grammar/parser/model | ANTLR4, 6 AST/27 mm, JDK | REUSE_WITH_ADAPTER, engine | Parse-tree capability scanner trước builder | D: subset model parity; unsupported activation/forall/pick/top-level association không bị mất im lặng |
| [x] GoalActivationGraph/ContextResolution/validator | mm only | REUSE_WITH_ADAPTER, engine | Explicit root activation/context binding policy bên ngoài | D/G/L: đúng graph/owner/type, không tự tạo trigger OCL |
| [x] Goal/TaskMarking + IStarMarking/Propagation/enums | iStar mm + JDK | REUSE_100_PERCENT core, engine | Quality gate, occurrence/history owner; không gọi private `saturate()` | E/F/G: so đủ field, không chỉ GUI enum |
| [x] Contextual instance | 5 ISCN mm + 3 instance files + iStar mm + marking | REUSE_WITH_SMALL_PATCH, engine | P-CTX-01; matcher explicit, không default all-context=true | G/L/O: source/instance trace rõ, dependency không cross-context |
| [x] Native OCL adapter | host use-core + ANTLR3, no use-gui | REUSE_WITH_ADAPTER, `A/use75/` | Generalize current plugin compiler/result contract | I/J: Boolean, Value, @pre/before/after/correlation đúng |

**P-CTX-01 đã triển khai, verified:** `E/dsl/iscn/goalmodelinstance/parser/GoalModelInstanceTranslator.java` expose public `topology(GoalModel,IStarScenarioModel,InstanceContextMatcher)` và `propagateDependencies(GoalModel,IStarMarking)`; DTO `GoalModelInstanceTopology` giữ structured occurrence/full binding stack. `topology` từ chối script statements/missing explicit cast, matcher bắt buộc; dùng lại original Builder và dependency bodies, giữ `evaluate(...)` cũ. Không parse occurrence ID bằng regex, không private reflection và không viết dependency algorithm thứ hai. Patch diff/API hashes có trong provenance, original-source oracle + G/L/O pass; patch không bổ sung activation/forall/pick syntax semantics.

Một lần snapshot topology có thể khởi tạo từ cast bằng `evaluate(source, emptyScenario, explicitMatcher)` thật; để live dependency không phải replay file/script mỗi event, P-CTX-01 vẫn cần seam nhận marking hiện tại. Không gọi private method bằng reflection. Trạng thái `TaskMarking.fromStatus(VIOLATED)` bị cấm; refinement/dependency ngoài status-domain profile phải reject sớm hoặc report UNSUPPORTED.

### 4.4. Source hashes mẫu đã kiểm trong lượt lập kế hoạch

Toàn manifest per-file sẽ tạo Phase 1, không suy các hash dưới là đủ closure.

| Source dưới use4Goal | SHA-256 |
|---|---|
| `goal/src/main/resources/grammars/CSL.g4` | `5BCA5C44933F2619EEAC837279E09A995A293383EBE3795D058D7E5319D3D224` |
| `goal/src/main/resources/grammars/IStar.g4` | `177EABD15E56C644C98BFA4E7A6F2B2E0E18B67BDFE68AC01D872A13EA72D503` |
| `G/dsl/csl/parser/CslCompiler.java` | `706BA4A4562F2176CCD6D9D1C9C5A9FFA65B7D05D2B0CD50783A84D203740966` |
| `G/dsl/csl/mm/CslModel.java` | `8DA2070C384ED555CBBE5F77426213EF809D420594D11F2E9C2725352C03F227` |
| `G/dsl/istar/parser/IStarBuildingVisitor.java` | `5F0C04BA2C8BAEC7BDA084A975B97B67ADA04D5821610CE6F464B83CE5B5CE51` |
| `G/dsl/iscn/goalmodelinstance/parser/GoalModelInstanceTranslator.java` | `7C9B7B27C29A328A69C5609399CC54BA170F99E2E56DD79589E6ED4BBA9B44A0` |
| `G/verify/conformance/semantics/GoalMarking.java` | `E9DDCC1BFBAFEF0C987BE6C7A017F8CE88C56A16E3C757BC637D1E1246660F48` |
| `G/verify/conformance/semantics/TaskMarking.java` | `E5EAE4CD7B10994E5D71C5C7782FF04DE3972352F17F5C5FA0732D39EB9AF07D` |
| `G/verify/conformance/semantics/IStarPropagation.java` | `90E8FF0662F03889A4EC977CD1296C57F4DBDDE2A0CBCDE76AFE937B25E35DE3` |

## 5. Capability và semantics profile mới

Tên dự kiến **`USE4GOAL_OBSERVED_V1`** cho engine semantics, **`JACAMO_MAPPING_V1`** cho mapping; là hai version độc lập. Khóa program chứa engine source hash/capability manifest/backend version/mapping rule hash và authored specification hash. Thay mapping không thay engine semantics. Giá trị property/JSON hiện tại không được tự diễn giải thành profile mới.

| Feature | Thực tế original / current | Admission của profile mới |
|---|---|---|
| CSL Entity/Role/OrgCtx, attrs, enum, inheritance, entity-centered association, invariant | Frontend v4 có model/validator; native runtime backend legacy chưa tương ứng | Implement backend mục 7; Role always concrete trong `CslRole`, same-instance inheritance; không đưa source abstract role thành concrete mà bỏ diagnostic |
| OrgCtx declarations | Nested M1, không runtime population | Preserve owner/declaration trace; live context/enactment do observation |
| Named/opaque datatype | M1 có representation, native codec không tự có | Schema có thể khai báo; live attribute dùng type chưa có exact codec → UNSUPPORTED trước activate, không String hóa |
| Goal types ACHIEVE/NONE/MAINTAIN/SUSTAIN | Core recurrence thật, NONE false→PENDING | Gọi original `update`; published expected differences với local monitor |
| Task historical pre/post | `preSeen || pre`, `postSeen || ((preSeen || pre) && post)`; không VIOLATED | Giữ nguyên; invocation conformance riêng, không trộn pre/post của các invocation để gọi contract PASS |
| AND/OR, sufficient Make/Break | `IStarPropagation` có thực thi; `saturate` private, cap 10.000 | Public assign/fire/closePending + quality boundary; không lấy partial propagation làm complete |
| Dependency/contextual instances | Translator có instantiate và private dependency saturation | Reuse P-CTX-01; matcher theo exact player/context; không giả `IStarPropagation` tự xử lý social dependency |
| Graph activation | `GoalActivationGraph.of()` từ refinement/dependency | Root demand do explicit profile/binding; không gọi đây là support declared activation OCL |
| Declared activation, forall/pick, top-level actor associations, description/condLang không lưu | Grammar có syntax; builder/model thiếu dispatch/fields | UNSUPPORTED có vị trí source trước builder. Giữ source text/trace. Có thể giữ description như metadata khi được author chấp nhận, không coi là engine support; extension mới phải profile riêng |
| Resource/Quality, Help/Hurt/qualification/needed-by | Model có, không mọi relation tạo verdict live | Chỉ advertise executable subset có tests. Quan hệ chưa có operational semantics → TRACE_ONLY/UNSUPPORTED, không auto fulfilled |
| NPL/norm, Jason mental/execution, MOISE order/scheme | Current observers/provenance có một phần | Giữ observer kết quả riêng; MOISE Scheme/Mission có trace/mapping admitted riêng, không ép CSL type hoặc đổi OPlan SEQUENCE/PARALLEL thành AND tương đương |

Source correction Phase3: native USE `=` là SPECIAL và có thể trả true khi cả hai operand undefined; `ExpStdOp` strict operations mới propagate Undefined. Không được lấy Boolean return type làm bằng chứng source coverage; required-field/cut/interval gate phải chạy độc lập.

Quality adapter giữ ít nhất: binding resolution, source completeness, cut continuity, population completeness, native OCL outcome, operation interval certainty, generation/incarnation. Engine marking chỉ nhận **known Boolean**. Khi GAP/UNKNOWN/ERROR/PARTIAL: không gọi update với false; giữ last known marking nội bộ, hạ kết quả bên ngoài thành inconclusive/skipped/error tương ứng. Snapshot resync không chứng minh MAINTAIN/SUSTAIN đúng qua missing interval. Reset/deactivation chỉ khi có chứng cứ, không vì missing.

Với Task post có @pre/correlation, PRE chưa có terminal/post không cung cấp đủ Boolean tuple cho `TaskMarking.update`: **defer whole update** đến interval có đủ known pre/post/required, hiển thị invocation IN_PROGRESS riêng. Không dùng `post=false` đại diện “chưa biết post”. State-only historical Task có thể advance ở cut nếu cả pre/post đã known. Failed invocation và historical pending/fulfilled ở các interval sau vẫn được báo tách biệt.

Leaf temporal records là history authority; propagation là derived aggregate. Không dùng `assignGoalTask()`/`fromStatus()` để reconstruct rồi ghi đè `stable/condition/preSeen/postSeen` của leaf. Build aggregate input từ leaf records mỗi accepted boundary, propagate trên topology admitted; chất lượng của dependency/AND/OR phải mang witness/dependency set, không dùng kết quả thiếu input như proof hoàn chỉnh. Đối với OR, một witness FULFILLED đủ có thể chứng minh outcome; kết luận negative cần đủ population/branches. Profiles không biểu diễn được Task-parent receiving VIOLATED phải reject/UNSUPPORTED, không ép về PENDING.

## 6. Module/API thiết kế — tất cả mục ADD là MỚI

```text
use-jacamo-semantic-engine/
  pom.xml                            (parent USE7.5, JDK21, ANTLR4)
  src/main/java/org/vnu/sme/goal/...  (selected original sources theo manifest)
  src/main/java/org/tzi/use/semantic/engine/
    api/         EngineCapabilities, EngineDiagnostic, EngineSemanticsProfile
    frontend/    CslFrontend, IStarFrontend, IStarCapabilityAdmission
    use75/       Use75CslModelBackend, Use75ModelBinding, OclContractSpec,
                 NativeOclBackend, NativeOclOutcome, UseStateReadView
    runtime/     Use4GoalRuntimeSessionAdapter, EngineCheckpoint, EngineInputs,
                 EngineOccurrenceKey, EngineStepResult
  src/main/resources/grammars/{CSL,IStar}.g4
  src/main/resources/META-INF/{use4goal-provenance.json,licenses/...}
  provenance/                        (upstream/patch inventory; không full plugin)
  src/test/...                       (parity/backend/marking tests)

use-plugin/
  .../semantics/profile/             (profile selection, compatibility)
  .../semantics/mapping/             (JaCaMo rules/program/binding/diagnostics)
  .../semantics/runtime/             (quality, source→engine adapters)
  .../codegrounded/runtime/          (existing writer/checkpoints/coordinator)
  .../ui/                            (existing Workbench/results)
```

| Contract/class MỚI | Package/owner | Input → output và điều kiện |
|---|---|---|
| `JaCaMoMappingProfile` | `S/mapping/` | ID/version/capabilities + ModelSnapshot + authored spec → SemanticProgram; không giữ mutable runtime state |
| `MappingRuleSet` | `S/mapping/` | Frozen rules + rule hashes + loss decisions; dùng source IDs chuẩn, không tên fuzzy |
| `JaCaMoStaticMappingAdapter` | `S/mapping/` | Static official model + chosen profile → canonical models/contracts/bindings, gọi validators gốc |
| `SemanticProgram` | `S/mapping/` | Giữ real CslModel + real GoalModel + engine-neutral OclContractSpec list + binding/trace/profile hashes; freeze sau validate (models gốc có setters, không tuyên bố tự immutable) |
| `SemanticBindingIndex` | `S/mapping/` | Static source-ID → CSL/iStar declaration/contract ID → backend symbol. Runtime incarnation index tách mutable sidecar, không nằm canonical M1 |
| `MappingDiagnostic` / `UnsupportedMappingDecision` | `S/mapping/` | Source location/ID, rule/profile version, target, reason, disposition; unsupported không là verification failure |
| `RuntimeStateMapping` / `JaCaMoRuntimeStateMapping` | `S/mapping/` | **Already-admitted** snapshot/event + program/index → typed mutation plan + quality facts; không tự accept TCP bytes |
| `SemanticMutationPlan` | `S/runtime/` | Operations typed/upsert/link/delete và source cut/version; apply vào mutation Port của một writer; no model rebuild |
| `JaCaMoOccurrenceBindingAdapter` | `S/runtime/` | Exact player/role/context/incarnation → neutral engine cast/topology/binding keys; reuse current resolver policy |
| `ObservationQualityGate` | `S/runtime/` | Cut/coverage/binding/invocation → admitted known EngineInputs hoặc explicit diagnostic; engine không import contract completeness enum |
| `RuntimeEngineSessionOwner` | `S/runtime/` | Run/program/generation owner + engine adapter/cache/binding/pre-state lifecycle; orchestrated bởi coordinator |
| `SemanticResultAdapter` | `S/runtime/` | Original marking + quality/witness + native Value outcome → versioned SemanticVerificationEnvelope/UI/journal |
| `Use75CslModelBackend` | `A/use75/` | Real CslModel + neutral contracts/extensions → MModel + Use75ModelBinding; không nhận ModelSnapshot |
| `NativeOclBackend` | `A/use75/` | Native compile/evaluate trên host model và pair-state; generalized current implementation, không dùng subset |
| `Use4GoalRuntimeSessionAdapter` | `A/runtime/` | Original GoalModel/topology + known inputs + prior immutable records → staged EngineStepResult; savepoint/restore/reset/close. Không MSystem writer |

Không tạo generic ServiceLoader/plugin framework mới để chọn mapping; registry/factory explicit là đủ. Backend extension specs cho M0 player/enactment/context helpers là trung lập. Source-specific validation giữ ở plugin. `SemanticOclCompiler` và `OclEvaluationAdapter` cũ có thể delegate adapter mới, không để engine import `SemanticVerdict` của plugin.

## 7. Static pipeline và CSL → USE 7.5 backend

### 7.1. Thứ tự khởi tạo bắt buộc

1. Facade tạo pending import ticket; BridgeClient negotiate/subscribe-before-snapshot, kiểm project/revision/fingerprint. Mapping dùng ModelSnapshot được chọn, không parse `.jcm` bằng parser tự viết.
2. Freeze MappingProfile/RuleSet/authored requirements/contracts. Không suy domain schema hoặc iStar conditions từ tên variable/Jason goal.
3. `JaCaMoStaticMappingAdapter` → `SemanticProgram`: tạo real CslModel qua original AST/factory hoặc constructors công khai có validator; real GoalModel qua original factory/model APIs; OCL là source specs, chưa compiled AST.
4. `CslFrontend`/`IStarFrontend` admission cho text; **luôn** `CslSemanticValidator.validate()`/`GoalModelValidator.validate()` cho models tạo từ API. Validate cross-binding + capability, giữ source-target trace.
5. `Use75CslModelBackend` tạo MModel bằng host UseModelApi: symbol/type pass, classifier/attribute/inheritance/association pass, operations/helper extension pass. Kết quả phải atomically fail trước Session activation khi lỗi.
6. Native OCL compile trên **MModel đã có type/context symbols**; invariants/Goal/Task Boolean, operation pre/post có parameter/result và inside-post context đúng. CSL parser syntax pass không thay domain type-check.
7. Tạo đúng một pending `MSystem` qua state builder đã adapt, new owner/compiled registry; apply accepted initial runtime cut bằng projector/coordinator. Không temporal update qua intermediate object creation.
8. `NativeUseSessionActivator` publish chính system đó nếu ticket/current owner còn hợp lệ; MODEL_READY. Start ACK/release reasoning chỉ dành managed workflow, external producer đang chạy chỉ connect/resync; không đòi mọi producer có startup hold.
9. Fail import giữ session cũ nếu chưa trao ownership; revoke subscription/ticket mới, không active model nửa chừng.

### 7.2. Checklist backend có phạm vi semantics cụ thể

| Checklist | Implement bằng API thật / policy | Gate |
|---|---|---|
| [x] Entity | `UseModelApi.createClass`, source-name collision mapping deterministic; không gộp Agent | CslModel→MModel structural parity |
| [x] Role | `createClass`, same-instance `createGeneralization(child,parent)`; inherited attrs một identity, no parent play-chain | `oclIsKindOf`, inherited attr, one object identity; source abstract role mapping không im lặng |
| [x] OrgCtx | `CslGroup.isOrganizationalContext()/orgContexts()`; declaration owner sidecar | Không tự sinh runtime composition/population từ nesting |
| [x] Attributes | Exact type/optional/mutable/default; inherited duplicate checks; native MAttribute | Missing runtime field giữ Undefined/quality; authored default không coi là observed value |
| [x] Datatypes | 4 primitives; `createEnumeration`; `createDataType` chỉ declaration khi opaque type | Exact enum codec; named opaque runtime codec absent → UNSUPPORTED; không String cast |
| [x] Inheritance | Same-kind, single parent admitted; validator trước native construction | Cycle/mixed-kind/multiple parent negative |
| [x] Associations | `createAssociation` array-based endpoints, cardinality/role/aggregation order giữ nguyên | Navigation và cardinality đúng; binary composition only nơi grammar/validator cho phép |
| [x] N-ary nếu admitted | **Schema supported; canonical live runtime UNSUPPORTED**: MModel giữ đủ n ends, native runtime admission reject trước activate vì writer mới vẫn binary | `Use75CslBackendTest.preservesEnumDatatypesModifiersAssociationOrderAndNarySchemaAdmission`; `CanonicalCslNativePipelineTest.liveAdmissionRejectsMissingTypeCodecAndUnverifiedNaryWriter`. Create/remove/export/rollback n-ary live chỉ bắt buộc nếu profile tương lai admit; hiện không advertise support |
| [x] Invariants | Emit/compile **user invariants** bằng native API; no legacy translator omission | Positive/negative/undefined và export/recompile |
| [x] Role semantics | M1 role specialization; M0 player/role occurrence/context là explicit helper extension | Vai trò/context khác nhau không merge; one role object per accepted incarnation |
| [x] Context declaration | Preserved owner/nesting trace, không fabricated instance link | Declaration-vs-population negative test |
| [x] Compatibility/profile restrictions | Core validator + neutral backend policies; MOISE scope/direction outside adapter | Không map broad compatibility thành local invariant nếu thiếu explicit abstraction |
| [x] Model validation | Original validators + native structure/export checks | Reject before activate, preserve diagnostics |
| [x] Source-target trace | Declaration ID↔native symbol; plugin bổ sung JaCaMo source ID↔declaration | Deterministic hash, location, collision/reversal tests |

Reference không cung cấp canonical v4 runtime state cho đường mới: policy đã được đóng tại Phase0/profile và triển khai/test trong Phase5/13–15. Chọn default bảo toàn observation: missing required field làm quality incomplete; không tạo fact bằng default; mutable=false mà observed value đổi vẫn giữ fact và báo contract/policy outcome theo specification đã chốt. Không âm thầm reject observation thật để tạo state “đẹp”. Named datatype không có semantics/codec phải bị chặn ở profile, không hứa full arbitrary datatype support.

## 8. Runtime transaction, native OCL, iStar và history

```text
TCP → BridgeClient validation/admission → NativeRuntimeProjector protocol fence
 → JaCaMoRuntimeStateMapping (admitted data only)
 → RuntimeVerificationCoordinator serialized boundary
   [pin before + native/index/protocol/operation/engine/quality savepoints]
   → apply typed mutations/reconcile → pin after
   → native invariant + exact operation pre/post
   → quality/correlation gate → original GoalMarking/TaskMarking
   → original IStarPropagation + contextual dependency seam
   → stage engine results/history + stateVersion + evidence
   → commit/publish immutable result → UI/journal
```

### 8.1. Transaction boundaries và lỗi

Source nuance cần sửa có chủ đích: `RuntimeVerificationCoordinator.transaction()` hiện rollback `action.call()/structureTransitionValid()`; `finish()` nằm **sau** catch đó. `finish()` có semantic savepoint cục bộ, journal/snapshot failures chuyển coverage stale; đây **không phải** full distributed atomic transaction đã sẵn có.

- Savepoint scope mới bao gồm native object/link/attribute image, identity/runtime binding index, operation active intervals, raw engine leaf history, topology/version, observation quality, activation episode, staged aggregate; projector protocol image được phối hợp cùng transaction đang có.
- Before/after pin từ `new MSystemState(name, system.state())`: constructor host copy object states và linksets, cùng MSystem/model identity. Test derived attributes/links và object identity; không coi arbitrary live reference là immutable.
- Mapping/mutation/structural/schema exception trước commit: rollback native+index+engine+operation+protocol staged changes. Rejected event/gap evidence riêng; không advance temporal history/stateVersion như accepted boundary.
- OCL false/undefined/error là **verification outcome**, không mặc định throw/rollback observed fact. Chỉ known Boolean được đưa vào core. Unsupported mapping có thể reject batch trước mutation hoặc giữ evidence-only theo profile, phải nhất quán.
- Engine internal exception: discard staged engine update; publish explicit degraded result cho accepted native state hoặc rollback whole boundary theo transaction contract đã pin. Chọn default **native accepted + semantic ERROR + no new history + coverage tainted**, không giấu real fact; riêng native mutation exception rollback toàn bộ. Test cả hai error classes.
- Journal/snapshot persistence failure sau native commit: không giả rollback JaCaMo/source; giữ state đã quan sát, mark gap/stale, không publish successful verified cut, revoke temporal continuity. Replay acceptance không được pass thiếu entry.
- Violation đúng (ví dụ p<0) commit state và failure evidence. HARD pause chỉ theo RuntimeConstraintPolicy/capability, không đổi ERROR/UNDEFINED thành false để pause.
- Rollback engine savepoint không sửa MSystem; session adapter không có event subscription/writer thứ hai.

### 8.2. OCL production checklist

- [x] Compile invariant trên exact native class; quantification/population gate vẫn áp dụng.
- [x] Compile Goal condition trên actor/self/context binding đã xác minh.
- [x] Compile Task precondition; kiểm @pre không hợp lệ trong pre context.
- [x] Compile Task postcondition bằng native inside-post context, không regex.
- [x] Boolean type gate: expression khác Boolean bị reject trước activate.
- [x] Typed `self`, player/context và declared variables; không global name lookup mơ hồ.
- [x] Operation arguments đúng descriptor/signature và correlation; overload không chọn bằng tên.
- [x] Native OCL supports typed `result` qua exact supplied binding; CArtAgO logger hiện không quan sát return value nên live postcondition cần return → **UNSUPPORTED/SKIPPED**, không bịa value. NativeOclBackendTest + CanonicalOperationCheckpointsTest chứng minh cả typed gate và missing producer return path.
- [x] `@pre` dùng before/after native snapshots khác nhau của exact invocation; state-only Task contract có riêng semantics.
- [x] Before state pin tại operation PRE đã accepted, không lấy snapshot cuối cùng tùy ý.
- [x] After state pin tại operation terminal boundary + required source cut; detect interference/overlap.
- [x] Undefined giữ native classification; không Boolean hóa.
- [x] Invalid/runtime errors/cancelled compile được diagnostic riêng; host có Value/exception path nào thì giữ, không tự tạo invalid API.
- [x] Coverage source/capability/population/gap gate trước verdict tổng hợp.
- [x] Expression cache theo MModel identity + program/profile/hash + context/signature + source body; bounded, clear on model replacement/stop; no cross-model reuse.

Native APIs đã có: `OCLCompiler.compileExpression`, `Context.setInsidePostCondition`, `UseModelApi.createPrePostConditionEx`, `Evaluator.eval(expr,before,after,VarBindings)`. `OclContractSpec`/`NativeOclBackend` là adapter MỚI quanh chúng, không engine interpreter mới. Các signature host quan trọng hiện diện tại USE7.5; source equality của 9/13 API trong report không thay migration build tests.

### 8.3. Original iStar calls và quality

- `GoalMarking.initial(type).update(active,predicate).status()` theo occurrence/episode, **không viết recurrence tương đương mới**.
- `TaskMarking.initial().update(required,pre,post).status()` cho history profile; invocation failure nằm result contract riêng. Khi Task post chứa @pre, input chỉ admitted nếu có pinned interval/bindings đầy đủ.
- `IStarMarking.withGoalMarking/withTaskMarking` xây aggregate input; `IStarPropagation.assignGoalTask/fire/closePending` chỉ trên admitted model/status; giữ leaf records ngoài status-only reconstructions.
- `GoalActivationGraph.of()` dùng graph demand, explicit root demand policy; declared activation syntax bị reject đến khi extension có version.
- `GoalModelInstanceTranslator` + P-CTX-01 dùng cast/context matcher và dependency bodies gốc; không tự broadcast all instances hoặc parse tên để chọn player.
- Topology rebuild chỉ khi accepted cast/role/context/incarnation change, không mỗi property delta. Preserve history chỉ cho exact stable keys; re-enactment/incarnation/episode mới khởi tạo lại.
- Unknown source có thể giữ known internal history nhưng output quality không complete. ACHIEVE witness chỉ còn hợp lệ trong same proven episode; MAINTAIN/SUSTAIN không “heal” history gap bằng current true; Task không nối hai invocation thành một contract.
- Bounded topology/marking/history/pins; capacity exhausted → diagnostic/gap, không drop để giả live.

### 8.4. Threading

Current writer/evaluation chạy EDT. Giữ serial writer và native UI ownership; parse/map/schema compile/start/pause/transport waits/background I/O không giữ EDT. Đầu tiên instrument thời gian từng bước; native OCL/propagation nặng phải chạy worker trên **pinned before/after** và immutable program/typed bindings, với boundary order và publication fence `(run,generation,programHash,stateVersion,ownerTicket)`. Không publish kết quả cũ sau restart.

Để temporal history không out-of-order: một ordered evaluation lane/session, kết quả staged commit theo boundary; bounded ingress/backpressure, không coalesce bỏ observation có ý nghĩa temporal. Không worker đọc live MSystemState. Không giữ facade/delivery lock khi `invokeAndWait` trong khi EDT chờ worker/close của lock đó. GUI chỉ observe immutable result; cancel/close không join callback từ EDT.

## 9. Lifecycle và ownership

Ownership key: `producer run UUID/sessionId + generation + source revision + mapping/profile/rule hash + engine/capability hash + program hash + activation ticket`; occurrence bổ sung `source requirement ID + actor/player + role/context + incarnation + episode`. Consumer connection ID không thay producer run UUID.

| Transition | TCP/process ownership | Models/native state | Engine/history/cache/pre-state |
|---|---|---|---|
| IMPORT | Pending Bridge subscription với handoff fence; không stop producer đang live | Build pending CslModel/GoalModel/MModel + one pending MSystem; fail giữ active cũ | New owner/history chưa advance intermediate mutations |
| MODEL_READY | Bridge đã accept initial cut; managed producer có thể đang hold reasoning | Publish one MSystem/Session, frozen program/hash | Initialize baseline markings một lần; OCL cache đúng model |
| START | Managed: file request/started ACK thật; unowned external: connect/observe, không giả launcher control | Không rebuild schema; accept cut sau ACK | Begin/continue same run history theo continuity |
| PAUSE | Existing authenticated TCP ExecutionControl; capability/agent incarnation ACK | Jason cycle paused, CArtAgO/MOISE/timer vẫn có thể đổi; state tiếp tục nhận facts | Không clear history; verify accepted boundaries; separate sync/control status |
| RESUME | ACK + subscribe/resync fence; no global-freeze claim | Same model/system nếu source revision không đổi | Preserve only proven continuity; gap vẫn tainted |
| STOP (monitor session) | Revoke delivery, close subscription/connection/control, cancel tasks; **không kill external process** | Giữ final system read-only/offline hoặc detach theo UI policy; no USE exit | Freeze final result; release pending pre-state, caches/listeners/queues; history owner closed |
| RESTART (monitor) | Stop monitor → reconnect/import; same producer run vẫn phải nhận đúng identity | New consumer session; reuse model chỉ khi hash/equality gate; one active owner | Mặc định fresh history có `HISTORY_START_AFTER_RUN_START`, không carry latch thiếu evidence |
| REIMPORT | New ticket; source/profile khác → new program/MModel/system atomic replace | Không mutate active old MModel; stale callback bị fence | New cache/markings/binding owner; dispose old after handoff |
| DISCONNECT | Chỉ observation; producer vẫn chạy | Retain last state, stale indication | Gap/coverage lost; abort/taint incomplete intervals, không certificate complete |
| RESYNC | New subscription ready trước replacement cut; pending buffering/watermarks; old subscription fenced | Same program+run có thể reuse system/projector; source revision đổi đi REIMPORT | Không reset tùy tiện same-run known history; new generation/incarnation reset keys; missing interval vẫn hiện |
| Producer runtime restart | Launcher/process owner tạo new producer UUID/generation; TCP negotiate lại | Binding reconcile exact new identity; no in-place old revision assumption | Reset whole run histories/operations/norm observer state; reject late old events |

**Phạm vi control:** Source chỉ có STATUS/PAUSE/RESUME; không tự gọi `control(STOP)` hay `control(RESTART)` không tồn tại. UI mới nếu ghi “Stop/Restart” phải ghi rõ **monitor session**. Test restart producer dùng launcher hiện có/process handle do test sở hữu, shutdown producer trong fixture/harness; không buộc thêm remote stop protocol. Nếu sau này yêu cầu điều khiển process launcher trong UI, tách tính năng managed-process ownership có consent/config riêng; không ảnh hưởng engine và không phải blocker vertical slice này.

| Resource | Owner duy nhất | Reset/dispose rule |
|---|---|---|
| TCP connection/subscription/auth secret handles | Facade/BridgeClient | Close on disconnect/stop/reimport handoff; secret không ghi vào evidence |
| Producer run UUID/generation/revision | Producer authority + validated consumer mirror | Không tạo giả khi reconnect; generation change reset run-bound state |
| CslModel/GoalModel | SemanticProgram | Freeze after validation; replace on program/profile change |
| MModel/MSystem | Host backend/Session activation owner | Cache identity check; one writer; model replacement revokes old owner |
| Compiled native OCL | Program-scoped backend registry | Bounded; clear/recompile on model/profile/body/signature change |
| Goal/Task leaf history + aggregate topology | RuntimeEngineSessionOwner | Per occurrence/episode; reset on run/incarnation/episode boundary, savepoint atomically |
| Runtime binding/quality index | Runtime mapping owner inside coordinator | Reconcile accepted cuts, remove/tombstone old incarnation, rollback on failed mutation |
| Invocation pre-state checkpoints | NativeOperationCheckpoints | Exact correlation; release complete/abort/timeout/gap/stop, bounded pins |
| Replay history/evidence | Versioned replay session, offline | Không subscribe live; same engine/mapping hash hoặc explicit incompatibility |

## 10. File/Class Change Matrix

Các path viết tắt mở rộng theo mục 1; không có action DELETE cho semantic implementation hiện tại. “Current role” ghi baseline audit; Action ADD chỉ phân biệt file mới so với baseline. Trạng thái thực thi lấy từ checkbox/Status và evidence của từng phase, không mặc định mọi ADD còn TODO.

| File/Class | Current role | Future role | Action | Phase |
|---|---|---|---|---|
| `pom.xml` | Reactor USE7.5 | Add engine module/dependency management | MODIFY | 2–3 |
| `use-jacamo-semantic-engine/pom.xml` | Chưa có | Selected engine artifact, host provided | ADD | 2–3 |
| `E/dsl/csl/{ast,mm,parser}` + grammar | Chưa có | Original canonical CSL | ADD | 2–4 |
| `E/dsl/istar/{ast,mm,parser}` + grammar | Chưa có | Original canonical iStar subset | ADD | 6 |
| `E/verify/conformance/semantics/` selected 7 files | Chưa có | Original marking/propagation | ADD | 7 |
| `E/dsl/iscn/...` selected files | Chưa có | Contextual instance engine + patch seam | ADD | 6–7 |
| `A/api`, `A/frontend`, `A/use75`, `A/runtime` | Chưa có | Neutral adapters/capabilities/backend/session | ADD | 3–8,14 |
| Engine manifest/license/patch inventory | Chưa có | Exact origin/provenance release inputs | ADD | 1–3,7 |
| `use-plugin/pom.xml` | Plugin shade/test/package | Engine dependency, no host/duplicate engine | MODIFY | 2–3,21 |
| `B/OfficialProjectAdapter`, `OfficialMoiseAdapter`, Jason adapter | Official extraction | Same neutral definitions; OfficialProjectAdapter bổ sung producer class/interface evidence, không đổi DTO shape | ADAPT | 9–12,16,21 |
| `B/DeclaredArtifactTypeProof` | Chưa có | Copy exact non-initializing application subclass/@OPERATION signatures + bytecode hash/provider URI into SourceEvidence | ADD | 21 |
| `A/api/EngineInstallationGuard` | Chưa có | Strict host/ANTLR/resource/provenance/license/class origin/duplicate installed engine guard | ADD | 21 |
| `P/DefaultJaCaMoFacade.awaitCanonicalIngress` | Receiver admitted native cuts faster than bounded original worker | Background-only backpressure, same 8-pin/16MiB quota; no EDT wait | ADD | 21 |
| `R/RuntimeVerificationCoordinator.fileCleanupCompletion` | No observable EDT file cleanup completion | Owned cleanup stage, completion after daemon attempt, background caller barrier | ADD | 22 |
| `P/DefaultJaCaMoFacade.closeOwnedRuntimeProjector` | Direct close of active/replaced/failed-import projectors | Track all owned file retirement in existing ingress barrier, short independent lock, no EDT await | ADD | 22 |
| `PT/DefaultJaCaMoFacadeTest` / `PT/codegrounded/RuntimeVerificationCoordinatorTest` | Facade/native lifecycle tests | 2.048-file regression and EDT cleanup completion, existing assertions retained | MODIFY | 22 |
| `PT/bridge/SeparateJvmBridgeTest` | Native snapshot gate inherited optional rich-cycle defaults | Explicit WORLD, actual agent/source metadata assertions, same production bootstrap quota | MODIFY | 22 |
| `P/codegrounded/use/DomainProjection.declaredOperation` | Consumer reflection interface | Additional exact producer-evidence overload; keep old reflection compatibility path | ADAPT | 21 |
| `PT/semantics/InstalledEngineMain`, `PackagedCounterProducer`, `Use4GoalPackagedPluginIT` | Chưa có | Isolated installed consumer + production Counter source/bridge JARs + default quota/live/replay/inventory/negative gates | ADD | 21–22 |
| `use-plugin/examples/csl-counter/`, `tools/jacamo-bridge.ps1`, `docs/project/use4goal-run.md` | Generic helper lacked engine classpath / installed layout / Java env fallback | Ship authored real CSL/iStar/OCL example, WORLD/FULL selection, runnable source/archive workflow | ADAPT | 21 |
| `B/JaCaMoBridgePlatform`, `SnapshotCoordinator`, snapshot sources | External producer/cuts | Same transport/observation source | KEEP | 13,16,20 |
| `C/*` DTO/schema/admission/control | Neutral contract | No semantic-engine dependency; additive only if exact evidence field needed | KEEP | 0,13,16,18 |
| `P/bridge/BridgeClient`/transport/mirror | TCP admission/handoff | Same validation/coverage behavior | KEEP | 16,20 |
| `P/DefaultJaCaMoFacade` / `JaCaMoFacade` | Lifecycle and import | Explicit engine/program/session selection, monitor stop/restart, await connection+file retirement outside EDT | MODIFY | 10–17,22 |
| `P/ManagedRuntimeWorkflow` | File startup ACK | Same managed startup and real PID ACK | KEEP | 16–17 |
| `P/codegrounded/CodeGroundedNativePipeline` | Native/local profile build | New explicit program route | ADAPT | 5,10–12,14 |
| `P/codegrounded/use/NativeUseModelBuilder` | Native/local schema | Delegate new profile to CslModel backend | ADAPT | 5,10 |
| `P/codegrounded/use/NativeUseStateBuilder` | One MSystem init | Same system factory, neutral new binding support | ADAPT | 5,14 |
| `P/codegrounded/NativeUseSessionActivator` | Atomic host Session publish | Keep exact system identity/ticket | KEEP | 14–17 |
| `R/NativeRuntimeProjector` | Accepted snapshot/event projection | Keep protocol, invoke mapping/session hooks | ADAPT | 13–16 |
| `R/NativeRuntimeMutationEngine` | Single native writer/Port | Typed new model bindings/n-ary if admitted | ADAPT | 5,13,15 |
| `R/RuntimeVerificationCoordinator` | EDT transaction/verification/journal | Engine session staged savepoint/commit/worker fence; owned file cleanup completion | MODIFY | 14–16,22 |
| `R/NativeOperationCheckpoints` | Exact operation contracts | Keep before/after/correlation; neutral bindings adapter | ADAPT | 8,12,15 |
| `S/runtime/SemanticRuntimeVerifier` | Local recurrence orchestration | Engine profile selection + quality/observers/result facade | MODIFY | 7–8,14 |
| `S/runtime/SemanticObservationMapper` | Local IR→M0 | Reusable observation planning via new runtime binding | ADAPT | 13 |
| `S/ocl/SemanticOclCompiler`, `OclEvaluationAdapter`, registry/coverage | Real USE OCL | Delegate/generalize neutral host backend, preserve old API | ADAPT | 8,12 |
| `S/mapping/OrganizationalMappingRules`, `FunctionalMappingRules`; `S/istar/OPlanOutcomeMapper`, `MoiseRequirementBinder` | Local mapping IR | Versioned rule providers/explicit abstractions | ADAPT | 9–12 |
| `S/mapping/SemanticTransformationService`, `MappingRuleCatalog`, `TraceComplement`, `MappingBundle*` | Provenance/source normalization | V2 program/bindings/provenance; legacy reader retained | ADAPT | 9–12,18 |
| `S/mapping/JaCaMoMappingProfile`, `MappingRuleSet`, `SemanticProgram`, `SemanticBindingIndex`, diagnostics | Chưa có | Versioned static semantics outside engine | ADD | 9–12 |
| `S/mapping/RuntimeStateMapping`, `JaCaMoRuntimeStateMapping` | Chưa có | Accepted runtime→typed plan | ADD | 13 |
| `S/runtime/{RuntimeEngineSessionOwner,ObservationQualityGate,JaCaMoOccurrenceBindingAdapter,SemanticResultAdapter,SemanticMutationPlan}` | Chưa có | Quality/exact occurrence/lifecycle seams | ADD | 7,13–15 |
| `S/profile/SemanticProjectionProfile`, configuration | Native/local selectors | Add engine/mapping IDs without old profile reinterpretation | MODIFY | 9,14,17 |
| `S/csl/CslSchema` / `CslSchemaValidator` | Local M1/validation | Compatibility only after migration gates | RETIRE_LATER | 19,22 |
| `S/csl/CslUseBinding` / structural policy / scalar codec | Local backend/4 scalars | Reusable host hooks; legacy profile wrapper remains | ADAPT | 5,8,13,19 |
| `S/istar/RequirementModel`, `TemporalGoalMonitor`, local recurrence | Local canonical/history | Old profile/replay; new profile uses original engine | RETIRE_LATER | 7,18–19,22 |
| `S/istar/RequirementBindingResolver` | Exact runtime occurrence | Policy reuse, adapt target ID model | ADAPT | 7,11,13 |
| Norm/mental/execution observers, evidence quality | Source-specific observer | Remain outside iStar Boolean core | KEEP | 13–18 |
| `S/exploration/BoundedExplorer`, roundtrip/MDE | Local offline features | Explicit old profile until separately ported | KEEP | 18–20 |
| `R/NativeRuntimeReplay`, replay manifest/readers | Native/local replay | Versioned new program/engine/hash loader | MODIFY | 18 |
| `P/ui/JaCaMoWorkbenchPanel`, `SemanticEvidencePanel`, goal views | Current UI | New profile/program diagnostics, original status + quality | MODIFY | 17 |
| `use-plugin/src/assembly/release.xml`, release/compatibility/README/docs | Current package contract | Engine provenance/fixtures/profile support in same distribution | MODIFY | 17–18,21–22 |
| `use-core/`, `use-gui/` | Host APIs/UI/loader | Host remains authority; no planned semantic edits | KEEP | 0–22 |
| `../use4Goal/**`, `../jacamo/**` | Reference | Read-only source provenance/oracle | REFERENCE_ONLY | 0–22 |

Deprecated markers/docs only appear after compatibility route exists. Physical removal of old semantic source is **not required** by this plan's acceptance; `RETIRE_LATER` means remove from new production call path when gates pass, not delete old replay support.

## 11. Thứ tự triển khai, build và test discipline

Thực hiện Phase 0→22 theo số. Phase 19 chỉ chuẩn bị deprecation/compatibility và bỏ duplicate authority ở **new profile**; quyết định retire/default-switch cuối cùng đợi Phase 20–21 rồi xác nhận ở Phase 22. Không dependency vòng Phase19↔Phase20.

Mỗi phase: mở lại files liên quan → kiểm baseline → thay đổi nhỏ có provenance → build/tests phù hợp → ghi evidence → mới đánh checkbox. Không sửa reference để tests pass; build reference parity source trong `use/.../target` hoặc test-only artifact/classloader riêng, không tạo duplicate FQCN trên production classpath. Test oracle gốc phải pinned và độc lập bản có patch; fixture golden có provenance, không lấy output mới tự làm expected.

Quy tắc bảo toàn evidence khi implementation chạy `clean`: output thô có thể sinh dưới `target`, nhưng trước khi clean phải lưu command/result/log summaries, source hashes, acceptance JSON và checksum inventory cần bàn giao vào `use-plugin/verification/semantic-migration/` (ngoài Maven target). Lệnh final `clean verify` ghi log vào thư mục evidence này để Maven không xóa file log đang mở trên Windows. Không copy credentials/secret files. Không dùng Maven clean trên reference repos; không dùng git clean/reset. Đây là điều chỉnh vận hành cho yêu cầu final clean/full tests, không đổi semantics hoặc giảm test gates.

Lệnh dự kiến chạy **ở giai đoạn implementation**, không chạy trong lượt này:

```powershell
Set-Location 'D:\_CODE_BANK\Project_\08_Thesis\use'
java -version
mvn -version
# Từ Phase 2, sau khi module đã được thêm:
mvn -B -pl use-jacamo-semantic-engine -am test
# Reactor hiện tại đã dùng Failsafe; lệnh verify gồm integration gates:
mvn -B -pl use-plugin -am verify
```

JDK cần 21. Kiểm `JAVA_HOME` thực tế, không hardcode ổ Java từ hội thoại cũ. Khi chọn `-Dtest`/`-Dit.test` trong reactor, cấu hình không-match cho upstream modules có chủ đích; **không dùng no-tests flag trong final acceptance**. Dùng `"-D...=..."` cho PowerShell properties phức tạp. Không `git clean/reset`; không xóa baseline artifacts người dùng. Package-only không được thay `verify` cuối. Lệnh release GUI phải lấy từ `use-plugin/docs/project/15-build-release-operations.md` và output distribution thực có, không dùng đường dẫn 7.1.1 của use4Goal.

---

<!-- PHASES_BEGIN -->

# Phase 0 — Baseline & Architecture Freeze

## Objective

Đóng baseline, supported capability và semantics profile trước bất kỳ migration source nào.

## Why this phase exists

Source hiện tại không chứa embedded và chưa tích hợp goal engine; report có phần prerequisite same-PID thuộc mục tiêu cũ. Không để assumption này quay lại hoặc gọi local monitor là original engine.

## Dependencies

Desktop report toàn văn; quyền chỉ sửa `use` trong lượt implementation sau; JDK21/Maven; không phụ thuộc engine module mới.

## Current reusable implementation

EXISTING U01–U17; các tests source native/TCP/OCL/replay hiện tại, chưa chạy lại trong lượt lập kế hoạch.

## Source files/classes involved

`pom.xml`, `use-plugin/pom.xml`, `use-plugin/README.md`, `docs/project/15-build-release-operations.md` dưới plugin; U04/U08/U10/U11; G compiler/model/marking và reference POM.

## Files/classes to add

Tương lai: architecture/capability freeze resources và baseline test evidence dưới `use-plugin/target/semantic-migration/phase-0/`; không tạo implementation giả.

## Files/classes to modify

Chỉ project architecture/compatibility docs khi cần ghi profile target; chưa sửa semantic Java trong phase này.

## Files/classes NOT to modify

`../use4Goal/**`, `../jacamo/**`, Desktop report; host core/gui; existing local recurrence và bridge contracts.

## Detailed tasks

- [x] Mở lại report toàn văn và source entrypoints U01–U17; chụp Git HEAD/status/hashes baseline, bảo toàn dirty reference files.
  - [x] Ghi chính xác module/profile/deployment hiện có; phân loại EXISTING/PARTIAL/UNSUPPORTED, không lấy target binaries làm source.
  - [x] Chạy baseline `mvn -B -pl use-plugin -am verify`, ghi riêng unit/integration/package failures và nguyên nhân; lỗi có sẵn vẫn phải có owner/triage trước đổi default.
- [x] Freeze `USE4GOAL_OBSERVED_V1` và `JACAMO_MAPPING_V1`, grammar hashes, supported features mục 5, root demand/episode policy.
  - [x] Đóng policy required/mutable/default/opaque datatype, Boolean quality/gap và Task historical versus invocation.
  - [x] Định nghĩa expected differences NONE/Task/local/symbolic; cấm claim semantic equivalence ngoài intersection.
- [x] Chốt external TCP, một MSystem, source/mapping/engine dependency direction và monitor Stop/Restart semantics.
  - [x] Đặt ngân sách queue/history/journal/topology và mục tiêu UI latency có số đo baseline; không chọn số tùy tiện rồi bỏ events để đạt.
  - [x] Ghi complete predicate cho live acceptance; schemes/missions chỉ asserted theo capability exact.

## Semantic requirements

Các invariant mục 3.2 áp dụng. Không downgrade current OCL; không dùng grammar parse success làm executable capability.

## Runtime requirements

Giữ external producer, managed/unowned control ownership, identity/watermarks và existing reset behavior; không start runtime trong lúc static model chưa admission nếu dùng managed hold.

## Error / unsupported handling

Thiếu domain abstraction/contract → mapping diagnostic, không invent. Baseline red test → record/triage, không chỉnh expected để che failure. Version/reference drift → BLOCKED ở checkpoint bị ảnh hưởng cho đến khi re-audit.

## Tests

- [x] Unit test: baseline unit suite và profile parsing hiện có.
- [x] Integration test: current native/TCP/session checkpoints.
- [x] Negative test: unsupported/mismatch/gap fixtures hiện có.
- [x] Parity test: lập danh sách oracle gốc độc lập và expected-difference ledger, chưa claim parity pass.
- [x] Regression test: OCL/manual USE/export/replay/legacy profiles baseline.

## Acceptance Criteria

- [x] AC0.1 Baseline source/test outputs có version, command và failures rõ; không dùng số từ phiên cũ.
- [x] AC0.2 Supported profile và error/quality policy được đóng, external TCP không có embedded prerequisite.
- [x] AC0.3 Reference/host ownership và dependency DAG được phê chuẩn bằng source evidence; không cycle.

## Risks

Baseline tests red do journal/cut/correlation; thiếu formal spec cho full language. Khoanh profile có evidence, tách lỗi baseline và lỗi migration.

## Rollback / compatibility strategy

Không thay runtime selection/default; giữ source hiện tại. Lưu checkpoint Git diff/evidence, không reset/clean working tree.

## Status

DONE — xác minh bằng `use-plugin/verification/semantic-migration/phase-0/acceptance.json`: reactor BUILD SUCCESS; 693 Surefire + 151 Failsafe + 198 JUnit 3 tests, không failure/error/skip; 7 upstream marking checks và native probe 200 boundaries pass. Reference hashes giữ nguyên. Source correction: frame default là 16 MiB ở HEAD hiện tại; Failsafe journal 512 MiB là test override, production vẫn 64 MiB.

# Phase 1 — Engine Source Extraction Inventory

## Objective

Chốt closed source/dependency/provenance manifest để copy selected core có kiểm soát.

## Why this phase exists

`goal` có UI/BPMN/solver/legacy; copying một package lớn kéo theo backend sai và plugin registration trùng. Compiler CSL còn dependency subset evaluator dù production sẽ native.

## Dependencies

Phase 0 capability freeze; source lists/hashes mục 4; reference HEAD chính xác.

## Current reusable implementation

EXISTING CSL 20 AST/46 mm/4 parser; iStar 6 AST/27 mm/3 parser; 7 marking files và contextual closure được đọc từ source.

## Source files/classes involved

`G/dsl/csl/{ast,mm,parser,ocl}`, `G/dsl/istar/{ast,mm,parser}`, selected conformance semantics, `G/dsl/iscn/...`, `GR/grammars`, `../use4Goal/LICENSE`, test fixtures.

## Files/classes to add

Source inventory/provenance specification dưới module `provenance/` tương lai, machine-readable manifest/resource theo mục 4.1; không copy engine Java ở phase inventory.

## Files/classes to modify

Chỉ manifest draft/architecture docs trong `use` nếu cần; không POM/runtime Java ở phase này.

## Files/classes NOT to modify

Reference repos, generated parser cũ, original files tại upstream, host source, current local semantic implementation.

## Detailed tasks

- [x] Expand từng nhóm manifest thành từng file; `rg '^import '` + fully qualified references + compile closure checklist.
  - [x] Bao gồm CslCoreOclSyntax→ANTLR3/USE và CslCompiler→NativeOclEvaluator→CslOclState; không suy AST/mm cần EMF.
  - [x] Contextual closure đúng 5 ISCN mm + 3 instance files, kiểm nested records/imports; loại product LTS/BPMN khỏi selected marking set.
- [x] Ghi per-file upstream commit/path/hash/license/FQCN và reuse mode, target, patch placeholder.
  - [x] So working copy với HEAD; fixture Classroom dirty và grammar legacy ghi riêng, không lấy làm v4 positive.
  - [x] Record grammar generation options visitor/listener + ANTLR 4.9.3; danh sách generated classes/tokens/interp provenance.
- [x] Chọn oracle original compiler/marking/context tests độc lập.
  - [x] Tạo test-only reference snapshot/harness kế hoạch dưới `use/target`, không build/write vào reference.
  - [x] Ghi P-CTX-01 API gap và parity fixtures; không thêm patch khác không có compile evidence.
- [x] Kiểm license/attribution và loại descriptor/Main/UI/solver auto-install/copy tasks khỏi extraction.

## Semantic requirements

Hash-original là file-level claim trong admitted capability; không xem API shim legacy trong mm là runtime v4 implementation.

## Runtime requirements

Inventory/extraction không cần JaCaMo process hay TCP; production dependency không có absolute sibling path.

## Error / unsupported handling

Closure kéo GUI/solver thì phân tích đúng import và tách adapter; không bỏ class bằng stub để compile. Missing source license/hash → BLOCKED extraction file đó.

## Tests

- [x] Unit test: validate manifest schema/hash/path uniqueness.
- [x] Integration test: reference closure resolve trong isolated test harness.
- [x] Negative test: forbidden package/import/duplicate FQCN và tampered source hash.
- [x] Parity test: pin original normalized model/diagnostic/marking fixtures.
- [x] Regression test: repository status/hashes reference không đổi.

## Acceptance Criteria

- [x] AC1.1 Mỗi extracted file có full provenance/dependency và target, không wildcard mơ hồ.
- [x] AC1.2 Closure không UI/solver/JaCaMo/plugin dependency trong engine; compatibility subset được ghi có lý do.
- [x] AC1.3 Oracle/test inputs pin được, P-CTX-01 có bounded patch scope và expected API behavior.

## Risks

Transitive imports ngoài inventory; examples không hợp grammar current. Dùng compile closure và explicit negative fixture.

## Rollback / compatibility strategy

Chưa đổi production route; manifest có revision, có thể loại file chưa cần mà không sửa upstream.

## Status

DONE — 125 source inputs (123 Java + 2 grammars), 12 regenerated parser files, 19 original oracle assertions và 6 negative manifest guards pass. Evidence: `use-plugin/verification/semantic-migration/phase-1/acceptance.json`, `original-acceptance.json`, `inventory-checks.json`, pinned archive và original observations. Compile selected original source trên host 7.5; không phải migrated production claim. P-CTX-01 scope/fixture ghi trong manifest/README. Source working tree CRLF và git HEAD LF: normalized bytes bằng nhau, exact raw hashes đều được giữ. Failed initial oracle assumption được lưu; không sửa upstream semantics.

# Phase 2 — Create Semantic Engine Module

## Objective

Tạo artifact engine-only trong reactor và extract original closure đã duyệt, chưa đổi USE production path.

## Why this phase exists

Module boundary ngăn JaCaMo mapping/UI nhiễm core, cho phép build/hash/parity độc lập và reuse tối đa original FQCN.

## Dependencies

Phase 1 closed manifest; parent USE7.5/JDK21; Maven graph kiểm tra assembly thực tế.

## Current reusable implementation

EXISTING host use-core/use-gui và plugin shade; original source closure trong reference. Goal POM chỉ tham khảo cấu hình ANTLR, không copy nguyên.

## Source files/classes involved

Root/plugin POM, engine upstream manifest mục 4; `use-assembly/pom.xml`; `use-plugin/src/assembly/release.xml` đọc để tránh module cycle.

## Files/classes to add

`use-jacamo-semantic-engine/pom.xml`, `E/...` original sources theo manifest (CSL/iStar/marking/context), grammar resources, provenance/license, initial `ET` harness.

## Files/classes to modify

Root `pom.xml` thêm module; `use-plugin/pom.xml` dependency engine compile cùng reactor. Chưa sửa semantic runtime Java/default profile.

## Files/classes NOT to modify

Reference source/POM; host Java; `goal` descriptor/Main; generated code thủ công; existing bridge and local monitor.

## Detailed tasks

- [x] Tạo engine POM parent `org.tzi.use:use:7.5.0`, artifact `use-jacamo-semantic-engine`, Java21.
  - [x] `use-core` 7.5 provided; ANTLR4 4.9.3 compile/runtime; JUnit test dependency phù hợp reactor.
  - [x] Không use-gui/bridge/Jason/Cartago/Moise/NPL/solver dependencies; không maven-install pluginClone tasks.
- [x] Copy exact approved source/grammar/license; giữ package/FQCN/hash, chưa apply P-CTX-01.
  - [x] Generate chỉ CSL/IStar grammar với đúng options; target generated source không commit giả làm handwritten parser.
  - [x] Compatibility NativeOclEvaluator/CslOclState được included theo closure, đánh rõ non-production route.
- [x] Nối plugin dependency và verify DAG; không đồng thời install full goal JAR.
  - [x] Giữ native/local profile behavior khi engine chưa chọn.
  - [x] Tạo test assert engine không depend plugin/GUI/bridge/framework, không có plugin descriptor gốc.

## Semantic requirements

Chỉ extraction nguyên source; không đổi enum/status/recurrence để compile nhanh; chưa quảng bá support ngoài frozen profile.

## Runtime requirements

Không static singleton chứa MSystem/program/history, không engine startup hook/TCP listener/producer launcher.

## Error / unsupported handling

Compile error ghi exact source/API/version để Phase 3 xử lý; không bỏ validators/classes hay tạo fake API. Hash mismatch fail build provenance check.

## Tests

- [x] Unit test: module/provenance hash sanity.
- [x] Integration test: Maven reactor resolves engine dependency once.
- [x] Negative test: engine import/dependency forbidden và duplicate descriptor guard.
- [x] Parity test: unpatched copied files hash bằng upstream.
- [x] Regression test: current plugin suite không chuyển engine tự động.

## Acceptance Criteria

- [x] AC2.1 Reactor có module engine và dependency DAG đúng, reference nguyên vẹn.
- [x] AC2.2 Selected sources giữ original provenance/FQCN; không copy full plugin/UI/solver.
- [x] AC2.3 Existing profile vẫn gọi implementation cũ; no automatic migration/default switch.

## Risks

Duplicate artifact/FQCN, transitive host shading, assembly ordering. Enforce one engine ownership và inspect dependency tree.

## Rollback / compatibility strategy

Engine opt-in, revert riêng module wiring nếu chưa release; không xóa existing semantic source hoặc reference.

## Status

DONE — Maven reactor test pass 696/696 (693 existing + 3 extraction/module tests), zero failure/error/skip; selected 123 original Java + 12 generated Java compile, exact 125 input hashes match pinned oracle archive. No original patch applied. Dependency DAG and negative closure guards pass; protected hashes unchanged. Evidence `use-plugin/verification/semantic-migration/phase-2/{acceptance.json,test-suites.json,reactor-test.log,dependency-boundary.json,extraction-checks.json}`. Existing runtime Java/default profile untouched; new profile not enabled.

# Phase 3 — Compile use4Goal Core Against USE 7.5

## Objective

Build selected unpatched core against host7.5 và khóa class identity/dependencies.

## Why this phase exists

9/13 API source giống nhau chỉ chứng minh khả thi, không phải binary/API closure đã chạy. Goal được build với parent7.1.1; migration cần test thật.

## Dependencies

Phase 2 module/source/grammar, JDK21, host API source và Phase1 oracle harness.

## Current reusable implementation

EXISTING native OCL APIs của host; original CSL syntax facade; ANTLR3 và ANTLR4 namespace riêng có thể coexist.

## Source files/classes involved

Engine POM/generated sources; `CslCoreOclSyntax`, compiler closures; host OCLCompiler/Context/Symtable/Evaluator/MSystemState; POMs provided scopes.

## Files/classes to add

`A/api/EngineCapabilities`, `EngineDiagnostic`, `EngineSemanticsProfile`; `ET` host linkage/loader/API compatibility tests.

## Files/classes to modify

Engine/root dependency management và generation setup; source adapter nếu compiler chứng minh signature mismatch. Original file patch chỉ khi bắt buộc, thêm patch ID/hash/tests trước.

## Files/classes NOT to modify

Host use-core/use-gui Java mặc định; upstream sources/POM; core marking behavior; existing plugin profiles.

## Detailed tasks

- [x] Build `mvn -B -pl use-jacamo-semantic-engine -am test`; kiểm clean generated parser từ pinned grammar trong module target.
  - [x] Ghi effective dependency tree/versions cho USE/ANTLR/EMF nếu liên quan; resolve convergence trước package.
  - [x] Không đưa host7.1 JAR hoặc full goal complete JAR vào production classpath.
- [x] Link smoke compile native OCL trên tiny host model và evaluate known Boolean/undefined.
  - [x] Kiểm `MSystemState` snapshot constructor, native `@pre` compile context API hiện tại, không tạo API thay thế.
  - [x] Nếu cần source/API patch, chứng minh reproduction, giữ patch nhỏ và record expected-difference category.
- [x] Package inventory engine: không `org/tzi/use/uml`, `parser`, GUI host classes; namespace adapter mới `org/tzi/use/semantic/engine` được phép.
  - [x] Kiểm host `MModel.class`, `Expression.class` identity từ plugin/engine giống host.
  - [x] Kiểm chỉ một ANTLR4 version cùng generated 4.9.3; ANTLR3 dùng host.

## Semantic requirements

Compilation repair không đổi language profile/marking semantics; no subset OCL fallback nếu native linkage lỗi.

## Runtime requirements

Core library load không tạo thread/process/socket/MSystem tự động; loader đúng hiện trạng static parent-delegating PluginClassLoader.

## Error / unsupported handling

Host linkage/API mismatch làm build fail, không catch bỏ qua. Unsupported feature vẫn diagnostic, không remove source validation để vượt compile.

## Tests

- [x] Unit test: generated/compiler/linkage APIs.
- [x] Integration test: engine gọi host7.5 OCL trong plugin test classpath.
- [x] Negative test: duplicate host classes/7.1 dependency/version skew.
- [x] Parity test: original unpatched pure core behavior/hash.
- [x] Regression test: full current plugin tests sau wiring.

## Acceptance Criteria

- [x] AC3.1 Fresh selected source build với USE7.5/JDK21 và pinned generators pass.
- [x] AC3.2 Host class identity đúng; artifact không bundle host cũ hoặc engine duplicate.
- [x] AC3.3 Mọi patch/constraint dependency có provenance, không unrecorded semantic change.

## Risks

Scope provided không tự truyền cho consumer test, effective POM drift, unshaded dependency thiếu runtime. Kiểm cả engine test và actual plugin artifact.

## Rollback / compatibility strategy

Giữ new route opt-in; pin phiên bản đã test, không sửa reference để đáp ứng host.

## Status

DONE — 5 engine tests + 1 actual PluginClassLoader/host identity test pass; package inventory and dependency tree pass. Host USE7.5 provided / ANTLR3 3.5.3 and runtime ANTLR4 4.9.3; no old host/plugin/solver copies. No original core patch required. Full existing regression after wiring: Phase2 693 existing tests; existing production Java unchanged in this phase. Evidence `use-plugin/verification/semantic-migration/phase-3/{acceptance.json,test-suites.json,engine-package-test.log,plugin-host-linkage.log,artifact-inventory.json,engine-dependency-tree.txt}`. Initial undefined test assumption and native source correction retained: native equality handles undefined specially, strict p>0 gives Undefined; quality must be gated before original engine input even if OCL returns Boolean.

# Phase 4 — CSL Parser / AST / Model / Validator Integration

## Objective

Expose real CslModel compiler facade và parity toàn frontend/validator trong admitted CSLv4 profile.

## Why this phase exists

Canonical CSL không còn local CslSchema; original grammar/model/validator phải được gọi thật trước native backend.

## Dependencies

Phase 3 build, Phase0 supported CSL policy, Phase1 oracle và v4 fixtures.

## Current reusable implementation

EXISTING `CslCompiler.compile(Path/String/String,sourceName)`, `CslBuildingVisitor`, `CslModelFactory.create`, `CslSemanticValidator.validate`, `CslModel.isRevisedCore`.

## Source files/classes involved

`E/dsl/csl/{ast,mm,parser}`, `CSL.g4`, `CslCoreOclSyntax`, `NativeOclEvaluator` compile closure; `GT/csl/view/CslUseViewModelTest` v4 fixture, legacy Classroom source negative.

## Files/classes to add

`A/frontend/CslFrontend`; `ET` CSL parser/model/validator parity tests, normalized model serializer test utility, capability diagnostics.

## Files/classes to modify

Engine capability manifest/test resources/provenance; original parser/AST/mm chưa có thay đổi semantic dự kiến.

## Files/classes NOT to modify

Original grammar/builder/model semantics; legacy translator guard; upstream examples; local CslSchema callers ở profile cũ.

## Detailed tasks

- [x] Facade gọi original in-memory compiler, giữ sourceName/location và original diagnostics.
  - [x] Admit canonical v4 explicitly; legacy version acceptance của parser không tự thành live backend support.
  - [x] Models tạo từ API cũng chạy validator, không bypass bằng constructor success.
- [x] So normalized AST/M1 với original: types, attrs/default/optional/mutable, nested owners, generalization, compatibilities, associations/invariants/end order.
  - [x] Cover forward refs, collision, invalid bounds, same-kind cycles, n-ary entity-centered restrictions.
  - [x] Crosswalk grammar/Java/Ecore, ghi features chỉ annotation hoặc legacy API; không codegen Ecore thay original POJO.
- [x] Chặn assumption compiler syntax OCL pass = native domain typecheck pass.
  - [x] Wrong context/property/type invariant được giữ để Phase5/8 reject đúng stage.
  - [x] Pin source fixture hiện hợp grammar; Classroom `group` legacy phải expected-rejection/legacy route riêng.

## Semantic requirements

Real CslModel canonical; preserve original behaviors trong supported subset; richer datatypes không làm phẳng để lọt local IR.

## Runtime requirements

Parse/validate background, stateless per compile; không tạo MSystem/population hay temporal history.

## Error / unsupported handling

Syntax/semantic/capability errors tách stage; v4 backend absent chưa được fallback sang Csl2UseTranslator. Missing source trace không được fabricate line number.

## Tests

- [x] Unit test: valid/invalid CSL constructs và model accessors.
- [x] Integration test: text → original compiler/factory/validator → real model facade.
- [x] Negative test: legacy syntax, invalid ref/type/cycle/bounds và capability rejection.
- [x] Parity test: A/B/C với original oracle, cùng normalized diagnostics.
- [x] Regression test: native/local import/export tests không đổi.

## Acceptance Criteria

- [x] AC4.1 Facade trả đúng `org.vnu.sme.goal.dsl.csl.mm.CslModel`, validators gốc được gọi có call evidence.
- [x] AC4.2 A/B/C parity pass, unsupported/version diagnostics deterministic.
- [x] AC4.3 CSL frontend không chứa JaCaMo rules, không tạo fake live instances.

## Risks

Version prefix guard và example drift; model normalization mất ownership. So full structure và original error classes/location.

## Rollback / compatibility strategy

Giữ original parser APIs; new facade additive, old CslSchema/profile route còn nguyên.

## Status

DONE — 28 engine tests pass, gồm 23 frontend tests; 18 full AST/M1/diagnostic parity fixtures so với pinned original parser được generate/compile độc lập + 2 actual upstream fixtures có hash. API-built model validator/version admission tested; exact original model và source diagnostics preserved; native OCL typecheck deferred explicitly. A/B/C pass. Crosswalk `use-jacamo-semantic-engine/provenance/csl-feature-crosswalk.md`; evidence `use-plugin/verification/semantic-migration/phase-4/{acceptance.json,test-suites.json,frontend-test.log,normalized-parity/}`. Existing production route unchanged; wiring regression Phase2 remains applicable. Test formatting assumption failure retained; no original semantic source patch.

<!-- PHASES_0_4_END -->

# Phase 5 — CSL → USE 7.5 Backend

## Objective

Tạo native MModel từ real CslModel v4, dùng exact host APIs, giữ M1 semantics và runtime binding trace.

## Why this phase exists

Legacy Csl2UseTranslator có guard v4 và play-chain encoding; diagram CslUseViewModel không complete backend. CslUseBinding hiện nhận local/source IR, không phải canonical original model.

## Dependencies

Phase 4 canonical CSL/validator; Phase3 host linkage; policy mục 7.2. Runtime n-ary acceptance chỉ bật sau Phase13.

## Current reusable implementation

EXISTING native builder/exporter/state builder, CslUseBinding operation hooks/symbol allocation/structural policies; host UseModelApi có createClass/Enumeration/DataType/Generalization/Association/Invariant APIs.

## Source files/classes involved

`E/dsl/csl/mm`, `S/csl/CslUseBinding`, `CslStructuralPolicyCompiler`, `ScalarValueCodec`; native builder/exporter/state builder; `use-core/.../api/UseModelApi.java`.

## Files/classes to add

`A/use75/Use75CslModelBackend`, `Use75ModelBinding`, neutral M0 extension specification/type codec contracts; `ET` backend/export/type/identity tests.

## Files/classes to modify

NativeUseModelBuilder/StateBuilder result/binding seam; ProjectionBindingRegistry nhận canonical metadata; NativeUseExporter chọn new-profile datatype visitor adapter; NativeUseStructure thêm datatype/global annotations chỉ cho new marker. CslUseBinding cũ giữ nguyên. Source-verified defect bổ sung P-USE-PRINT-01, không sửa host printer.

## Files/classes NOT to modify

CslModel/validator gốc; legacy translator/requireLegacyRuntime guard; host APIs; JaCaMo frameworks/references; no new MSystem in engine backend.

## Detailed tasks

- [x] Implement hai-pass schema từ canonical CslModel, không roundtrip bắt buộc qua CslSchema mất type/default/owner.
  - [x] Classifier/symbol/enums/types trước, attrs/generalizations/associations sau; preserve ordered endpoints và context declaration metadata.
  - [x] Role subclass là cùng identity, inherited attrs; không play-chain parent objects; OrgCtx nesting không tự sinh runtime containment.
- [x] Thực hiện toàn checklist mục 7.2, mỗi feature có capability/test/diagnostic.
  - [x] M0 player/context/enactment helpers ở neutral extension contract, phân biệt authored CSL types với technical helpers.
  - [x] User invariants native compile/install đầy đủ; attrs optional/default/mutable/opaque có declared policy.
  - [x] N-ary schema tests; chưa advertise live n-ary trước writer test Phase13.
- [x] Return model/binding/source-neutral trace cho plugin join source IDs; export/recompile structural equality.
  - [x] Exact schema/model identity; backend không create state/populate declarations.
  - [x] Fail atomically before Session activation; unsupported type/semantic feature có source-target diagnosis.

## Semantic requirements

Entity/Role/OrgCtx giữ kind; CSL v4 same-instance inheritance; role concrete semantics/abstract source abstraction explicit; backend không chứa rule group/mission/Jason.

## Runtime requirements

Một MSystem do native state builder tạo; models/bindings frozen per program; schema compile background, no GUI lock.

## Error / unsupported handling

Unimplemented codec/association/policy → explicit UNSUPPORTED admission, không silently skip invariant hay flatten String. Native API error fail pending import, giữ active cũ.

## Tests

- [x] Unit test: mỗi row backend checklist, inherited attr/default/enum/n-ary/schema collision.
- [x] Integration test: real CslModel → native MModel → exporter/recompile → Session model.
- [x] Negative test: mixed kind/cycle/bad bounds/opaque runtime codec/missing policy.
- [x] Parity test: H structural/model parity trên canonical features; legacy translator chỉ oracle cho intersection legacy có tên.
- [x] Regression test: old native/local profile exports/OCL/operations vẫn pass.

## Acceptance Criteria

- [x] AC5.1 Real CslModel là input authority; no legacy translator invocation trong new backend.
- [x] AC5.2 Checklist H pass, Role cùng identity, OrgCtx declaration không fabricated M0.
- [x] AC5.3 Backend không tạo MSystem thứ hai và có deterministic symbol/source-target trace.

## Risks

Richer model không khớp local binding inventory, n-ary writer gap, authored defaults nhầm actual value. Giữ adapters typed và admission tách schema/live support.

## Rollback / compatibility strategy

New backend chọn riêng theo profile; old CslUseBinding không bị xóa; có thể revert new route mà không reinterpret recordings cũ.

## Status

DONE — full reactor 731/731 tests pass (35 engine, 589 plugin), zero failures/errors/skips. Real CslModel backend, original validation, typed codecs, ordered n-ary M1 schema, native user invariants, same-identity Role, declaration-only OrgCtx, explicit player/context compatibility and deterministic native bindings tested. Existing state builder/exporter/Session activation use the exact same MModel/MSystem. Source-based defect fixed by P-USE-PRINT-01 adapter: host visitModel omits named datatypes; no host or original core patch, full structural equality retained. Live opaque codec/n-ary writer gates remain explicit until supported. Evidence `use-plugin/verification/semantic-migration/phase-5/{acceptance.json,test-suites.json,backend-test.log,plugin-canonical-focused.log,full-regression.log,datatype-export-reproduction.log}`; patch notes in engine provenance. Reference hashes/status unchanged.

# Phase 6 — iStar Parser / Model Integration

## Objective

Đưa original iStar AST/model/compiler/validator/context models vào API facade với capability admission chống semantic loss.

## Why this phase exists

Grammar current parse activation/forall/pick/top-level associations nhưng builder/model chưa lưu/thực thi đầy đủ. Parse success không được thành silent acceptance.

## Dependencies

Phase3 extracted original compile; Phase0 admitted subset/expected differences; Phase1 oracle.

## Current reusable implementation

EXISTING IStarCompiler.compile(Path/String), IStarBuildingVisitor, IStarModelFactory, GoalModelValidator, GoalActivationGraph, ContextResolution, Actor/Agent/Role/Goal/Task/Dependency/Refinement và contextual records.

## Source files/classes involved

`E/dsl/istar/{ast,mm,parser}`, IStar.g4, `E/dsl/iscn/mm` selected, instance model/evaluation/translator; grammar/model/Ecore crosswalk report.

## Files/classes to add

`A/frontend/IStarFrontend`, `IStarCapabilityAdmission`; typed unsupported feature diagnostic; `ET` iStar frontend/model/context parity fixtures.

## Files/classes to modify

Engine capability manifest/test resources. P-CTX-01 deferred Phase7; parser/model semantics không patch để support full language trong profile này.

## Files/classes NOT to modify

Original grammar/Goal fields/Refinement hierarchy; original validator behavior; reference sources, plugin local RequirementModel route.

## Detailed tasks

- [x] Gọi original compiler/factory/validator cho subset; API-built GoalModel cũng validate.
  - [x] Parse tree admission scan bằng generated parser trước original builder, không regex language scanner.
  - [x] Reject activation/forall/pick/top-level associations/unsupported condLang và metadata bị mất theo policy mục5; ghi token span/source text.
- [x] Pin executable features riêng với syntax-only features.
  - [x] Goal types, conditions, Task pre/post, AND/OR, supported contribution/dependency/context validation.
  - [x] Resource/Quality/NeededBy/qualification có model không được tự advertise operational verdict nếu chưa test.
- [x] Validate activation graph/refinement ownership/type policies và contextual model closure.
  - [x] Freeze read ownership vì GoalModel/Goal/Task có mutation APIs; không cho UI mutate program đang live.
  - [x] Định danh source element/actor không bị collision giữa OS/actor scopes.

## Semantic requirements

Real GoalModel canonical; Goal activation graph khác declared activation clause. Không đổi MOISE goal type enum sang temporal GoalType tự động.

## Runtime requirements

Frontend không tạo thread/MSystem hoặc temporal history; context matcher input explicit, không default true cho live.

## Error / unsupported handling

Chặn trước visitor nhánh throw/ignored feature; original diagnostics giữ nguyên. Chưa support full grammar thì result UNSUPPORTED, không rơi về local RequirementModel rồi gắn nhãn original.

## Tests

- [x] Unit test: actor/goal/task/dep/refinement/context model APIs và admission tree scan.
- [x] Integration test: text → original parser/factory/validator → real GoalModel.
- [x] Negative test: activation/forall/pick/condLang/top-level actor association và invalid graph.
- [x] Parity test: D original AST/M1/validator/capability intersection.
- [x] Regression test: local RequirementModel/current goal views vẫn hoạt động.

## Acceptance Criteria

- [x] AC6.1 Original compiler/model/validator dùng thật, D parity pass.
- [x] AC6.2 Unsupported syntax không mất im lặng; source diagnostics đúng feature/location.
- [x] AC6.3 M1 model không bị runtime mutation và không chứa JaCaMo binding rules.

## Risks

Header/description syntax bị builder bỏ, AST-normalizer che missing metadata. So parse-tree feature inventory và model provenance.

## Rollback / compatibility strategy

Giữ subset version fixed; full grammar extension là profile/phase tương lai riêng, không sửa source gốc ngầm.

## Status

DONE — 64 engine tests pass, gồm 29 iStar frontend tests và 16 original AST/M1/validator parity fixtures (D). Generated parse-tree admission rejects ignored/throwing syntax with actual Unicode-safe token spans; empty authored contracts rejected rather than discarded. Original GoalModel/Goal/Task/Actor remain unchanged; OwnedGoalModel guards read ownership and stale indexes, does not claim immutable upstream POJOs. Old profile route unchanged, Phase5 full regression remains applicable. Evidence `use-plugin/verification/semantic-migration/phase-6/{acceptance.json,test-suites.json,istar-frontend-test.log,normalized-parity/}`. One original error renders unordered Set<GoalType>; only that set suffix normalized for cross-loader parity, raw diagnostics/initial failure retained, no semantic expected result/core patch.

# Phase 7 — GoalMarking / TaskMarking / Propagation Integration

## Objective

Gọi original recurrence/propagation/contextual dependency, có neutral session/history API và parity trước nối live source.

## Why this phase exists

Local TemporalGoalMonitor không tương đương NONE/Task original; IStarPropagation không tự xử lý contextual social dependencies và không có public generic saturate API.

## Dependencies

Phase6 real GoalModel/subset, Phase0 semantics/status-domain freeze, Phase1 original oracle.

## Current reusable implementation

EXISTING GoalMarking/TaskMarking/IStarMarking/IStarPropagation và enums; GoalActivationGraph; GoalModelInstanceTranslator.evaluate/InstanceContextMatcher; private instantiate/dependency bodies.

## Source files/classes involved

Selected 7 marking files; contextual translator/records; `GT/conformance/semantics/GoalTaskMarkingTest`; local TemporalGoalMonitor/RequirementBindingResolver là reference comparisons.

## Files/classes to add

`A/runtime/{Use4GoalRuntimeSessionAdapter,EngineInputs,EngineOccurrenceKey,EngineCheckpoint,EngineStepResult}`; `E/dsl/iscn/goalmodelinstance/parser/GoalModelInstanceTopology` (MỚI); tests E/F/G.

## Files/classes to modify

**Chỉ bản copy** GoalModelInstanceTranslator cho P-CTX-01; provenance/patch inventory. Không patch GoalMarking/TaskMarking recurrence/enum để khớp local monitor.

## Files/classes NOT to modify

Reference translator; core marking/propagation semantics; local monitor production route; USE state writer/bridge.

## Detailed tasks

- [x] Build original recurrence API wrapper nhận known Boolean input; leaf history immutable savepoint/restore/reset.
  - [x] Key per neutral occurrence/activation episode; goal deactivation reset theo original, Task reset do owner policy chứ không `required=false` tự clear.
  - [x] So all fields ACHIEVE/NONE/MAINTAIN/SUSTAIN và preSeen/postSeen, không chỉ final status.
- [x] P-CTX-01 expose structured topology/binding metadata và existing dependency propagation, giữ evaluate signature/behavior.
  - [x] Occurrence identity chứa full structured binding stack: original oracle tạo requested[bob] UNKNOWN và requested[bob,alice] FULFILLED riêng. Không broadcast status theo source goal ID; reject ambiguous first-owner fallback và context chưa chứng minh trước translator. Evidence Phase1 `original-topology-baseline.txt` / `original-observations.json`.
  - [x] Matcher explicit và topology instantiate không file/SOIL/MSystem.
  - [x] So patched new seam với original evaluate trên same cast/script; original behavior không bị sửa ngầm.
- [x] Compose leaf records → aggregate input → original public propagation → contextual dependency closure.
  - [x] Không ghi status-reconstructed aggregate đè leaf temporal fields.
  - [x] Reject Task-parent/other graph cho status VIOLATED không biểu diễn được; contract FAIL độc lập historical Task.
  - [x] Guard activation/dependency/refinement graph size và context cross-product; no silent partial fixpoint.
- [x] Giữ core cap 10.000 unchanged: profile ban đầu admit graph có bound hội tụ chứng minh được dưới cap, loại opposing/cyclic contribution patterns chưa chứng minh.
  - [x] Core không trả convergence flag; nếu cần mở rộng phải thêm patch instrumentation có version/tests riêng, không báo “converged” từ timeout/cap ngầm.
  - [x] Unknown/partial/gap được wrapper đánh dấu no-input/no-update, không false; quality policy final ở plugin Phase14.

## Semantic requirements

Original NONE false=PENDING; Task historical không VIOLATED; graph activation explicit. Original raw marking/parity và externally certified quality là hai output riêng.

## Runtime requirements

Session adapter không giữ MSystem/subscription/GUI/JaCaMo object; ordered per-boundary staged results, bounded history, topology reuse theo cast revision.

## Error / unsupported handling

Status-domain conflict/nonconvergent unsupported graph → fail admission/diagnostic, không catch thành false. Internal exception discard staged history; policy integration Phase15.

## Tests

- [x] Unit test: all temporal Boolean traces/episodes/historical Task fields.
- [x] Integration test: contextual graph + AND/OR + dependency + Make/Break admitted subset.
- [x] Negative test: cross-context links, VIOLATED Task injection, unknown-as-false, oversized/opposing graph.
- [x] Parity test: E/F/G and P-CTX-01 old/new seam; explicit local/symbolic differences ledger.
- [x] Regression test: unchanged old TemporalGoalMonitor expectations/profile behavior.

## Acceptance Criteria

- [x] AC7.1 Original marking/propagation files unchanged hashes, calls chứng minh được; P-CTX-01 có patch proof.
- [x] AC7.2 E/F/G pass, no cross-context or leaf-history loss; no invented public framework method.
- [x] AC7.3 Unknown/gap không cập nhật Boolean false; status differences versioned rõ.

## Risks

Aggregate fromStatus mất leaf history; dependency latching/domain conflicts; silent saturation cap. Restrict admitted graph và preserve original oracle.

## Rollback / compatibility strategy

New engine opt-in, old recurrence vẫn nguyên; P-CTX-01 revert độc lập nếu seam parity fail, không chuyển sang local recurrence để giả engine reuse.

## Status

DONE — 78 engine tests pass; 4,096 Goal and 1,536 Task boundary comparisons against isolated original source, complete old evaluate output parity for shared/separate contexts, AND/OR/contextual dependency/Make/Break tests and unknown/episode/owner/size/depth/status-domain negatives. Focused reactor regression: 17 engine + 6 unchanged old TemporalGoalMonitor/GoalView tests pass. P-CTX-01 APPLIED_VERIFIED; seven marking files and original translator methods unchanged. Evidence `use-plugin/verification/semantic-migration/phase-7/{acceptance.json,runtime-engine-final.log,compatibility-regression.log,contextual-parity.txt}`. Source correction: derived status roundtrip and pre-validator API graph limits documented in `provenance/runtime-profile-admission.md`; no silent fixpoint-cap claim.

# Phase 8 — Native OCL Backend Integration

## Objective

Kết hợp use4Goal models/markings với host USE7.5 OCL production, tái sử dụng current native compiler/evaluation boundary.

## Why this phase exists

Reference có cả native/subset/symbolic/regex transition evaluators. Chọn sai route làm mất @pre, type/undefined/error semantics.

## Dependencies

Phase5 native MModel/types, Phase6 conditions/Task model, Phase3 host API linkage.

## Current reusable implementation

EXISTING SemanticOclCompiler predicate/transitionPredicate/condition; OclEvaluationAdapter native pair-state/classification; SemanticConstraintRegistry/coverage và NativeOperationCheckpoints.

## Source files/classes involved

U11/U12, S/ocl package, host OCLCompiler/Context/Evaluator/VarBindings/Value; reference IStarOclConstraintCompiler/trace evalBoolean chỉ audit/parity source.

## Files/classes to add

`A/use75/{OclContractSpec,NativeOclBackend,NativeOclOutcome,UseStateReadView}`; neutral compilation cache contract; `ET` pair-state/type/native API tests.

## Files/classes to modify

Current SemanticOclCompiler/OclEvaluationAdapter thành compatibility delegates hoặc share implementation qua neutral result adapter; registry/binding adapters nếu nhận new model/specs.

## Files/classes NOT to modify

Host OCL grammar/evaluator; original Goal/Task recurrence; reference weak/regex evaluator; NativeOperationCheckpoints exact correlation invariants.

## Detailed tasks

- [x] Generalize native compiler/evaluator vào neutral adapter, không kéo plugin SemanticVerdict/type imports vào engine.
  - [x] Preserve original old APIs để existing tests/callers không đổi.
  - [x] Compile CSL invariant, Goal condition, Task pre/post trên exact class/type context; Boolean gate mỗi entrypoint.
- [x] Thực hiện toàn OCL checklist mục8.2.
  - [x] Native parameter/result symbols, `setInsidePostCondition`, real before/after, inherited enum/navigation/collection expressions.
  - [x] Missing observed result/arg/correlation báo rõ; no post synthesized từ current property.
- [x] Cache theo exact MModel/program/contract/binding context và bounded owner lifecycle.
  - [x] Evaluate false/undefined/runtime exception thành native outcome riêng; contract false không tự bật pause ngoài policy.
  - [x] OCL native performance instrumentation; background pinned-state path không read live system.

## Semantic requirements

Không dùng NativeOclEvaluator subset hoặc CslOclTransitionEvaluator regex làm production/fallback; before/after có type/model identity đúng.

## Runtime requirements

Một authoritative MSystem; snapshots read-only cùng owner; pre-state giữ tới terminal/abort/timeout và bounded cleanup.

## Error / unsupported handling

Compile/type errors fail static activation; runtime Value undefined/exception trả explicit outcome + quality. Unsupported result/codec không tạo Boolean false hoặc fake return value.

## Tests

- [x] Unit test: Boolean gate/context/arguments/result/undefined/invalid and cache ownership.
- [x] Integration test: native invariant và p=0→1 @pre PASS, p=0→2 FAIL; navigation/collection @pre.
- [x] Negative test: no PRE, foreign MModel AST, stale invocation/incarnation, concurrent interference.
- [x] Parity test: I/J native host oracle; weak subset chỉ intersection legacy riêng.
- [x] Regression test: existing OclSemanticBackendTest/NativeOperationCheckpointTest/manual OCL.

## Acceptance Criteria

- [x] AC8.1 All mục8.2 checklist có test; production backend native USE7.5 only.
- [x] AC8.2 Pair-state @pre chứng minh bằng before/after witness, không state/state hoặc text replacement.
- [x] AC8.3 Quality outcomes/cache lifecycle đúng và old OCL profiles không regression.

## Risks

OCL delegates tạo dependency cycle, stale Expression, pre-state memory. Đặt neutral types ở engine và keep source-quality ở plugin.

## Rollback / compatibility strategy

Giữ old signatures/wrappers và profile routes; không đổi language grammar/host version để sửa lỗi adapter.

## Status

DONE — 85 engine tests + 9 focused existing native OCL/checkpoint/invocation tests pass, zero failures/errors/skips. Shared NativeOclCompilation/NativeOclOutcome are the production implementation behind preserved plugin APIs. Native Boolean gate, exact typed symbols, before/after @pre 0→1 PASS and 0→2 FAIL, navigation/collection @pre, enum/inheritance, explicit player/orgContext, undefined/errors, coverage, model/AST/cache ownership, bounded cleanup and background pinned-state evaluation verified. Existing invocation tests verify real missing PRE/incarnation/signature/argument/overlap/timeout behavior; new-profile wiring remains Phase14–16. Evidence `use-plugin/verification/semantic-migration/phase-8/{acceptance.json,engine-test-suites.json,acceptance-regression.log}`. No host/original recurrence patch.

# Phase 9 — Mapping Abstraction Layer

## Objective

Định nghĩa versioned JaCaMo mapping/program/binding contracts ngoài CSL/iStar engine để thay rule-set không sửa core.

## Why this phase exists

Current derive/fromOfficial dùng config IR authored, chưa complete auto pipeline; mapping cuối chưa chốt và source semantics phong phú hơn core target.

## Dependencies

Phase4/6 canonical models, Phase8 OCL specs, Phase0 profile/version policies; existing trace/normalizer/catalog.

## Current reusable implementation

EXISTING SourceModelNormalizer, MappingRuleCatalog, TraceComplement, MappingBundle/Codec, SemanticTransformationService, organizational/functional rule providers và explicit unsupported decisions.

## Source files/classes involved

`S/mapping/*`, `S/profile/*`, `S/binding/UseProjectionBinding`, neutral C/ModelSnapshot/semantic contract; canonical model APIs từ engine.

## Files/classes to add

`S/mapping/{JaCaMoMappingProfile,MappingRuleSet,SemanticProgram,SemanticBindingIndex,MappingDiagnostic,UnsupportedMappingDecision,JaCaMoStaticMappingAdapter}`; profile registry/factory explicit; schema/config tests.

## Files/classes to modify

SemanticTransformationService/TraceComplement/bundle attachments phiên bản mới; profile selection/configuration; giữ legacy reader và original rule providers.

## Files/classes NOT to modify

Engine original sources/markings/grammar; bridge DTO làm semantics container; existing legacy recording/schema contract in-place; reference repos.

## Detailed tasks

- [x] Implement các contract mục6 với version/hash/capabilities/input authored rules explicit.
  - [x] SemanticProgram chứa real models, neutral OCL specs và sidecar binding/trace; không dùng local CslSchema/RequirementModel làm canonical new authority.
  - [x] Static declaration index immutable; runtime incarnation index thuộc owner khác; distinguish source ID/target declaration/native symbol/runtime identity.
- [x] Tái dùng catalog/normalizer/loss decisions, bổ sung target engine IDs và exact versioned schema.
  - [x] MappingRuleSet không phụ thuộc UI/transport; engine không import C DTO hoặc plugin rules.
  - [x] Unsupported decisions giữ original source value/location/abstraction reason, không success Boolean.
- [x] Profile V1/V2 replaceability test bằng cùng source khác explicit domain view/rule binding.
  - [x] Engine original file hashes không đổi, program/hash/binding thay đúng.
  - [x] Model/config/profile hash mismatch reject; no mutable System property switch giữa run.

## Semantic requirements

Tuân toàn invariant mục3.2; one rule/disposition per source-target mapping claim, partial loss explicit; text export nếu thêm là derived, không authority thứ hai.

## Runtime requirements

Mapping profile frozen per program; new revision/profile buộc new owner/reimport, runtime transport độc lập semantic version.

## Error / unsupported handling

Missing authored schema/condition/abstraction → diagnostic yêu cầu binding, không phát minh API/requirement. Unsupported mandatory feature chặn activation; optional TRACE_ONLY đi evidence rõ.

## Tests

- [x] Unit test: profile/rule hashes/binding uniqueness/diagnostic serialization.
- [x] Integration test: ModelSnapshot + authored input → SemanticProgram real model types.
- [x] Negative test: ambiguous IDs/changed source hash/unsupported mandatory feature/profile mismatch.
- [x] Parity test: source provenance/loss decisions của legacy mapping không bị mất khi adapter.
- [x] Regression test: existing SemanticTransformationTest/MappingReplay/old config reader.

## Acceptance Criteria

- [x] AC9.1 Mapping outside engine, compile-time forbidden imports pass.
- [x] AC9.2 Profile change không sửa core, deterministic program/binding/provenance hashes.
- [x] AC9.3 Không silent lossy conversion qua local IR; legacy bundles vẫn đọc theo đúng version.

## Risks

Mapping thâm nhập core, hai authoritative representations, source Ecore/official discrepancies. Giữ sidecar và original validator/capability gates.

## Rollback / compatibility strategy

New profile/config schema additive; old JSON/profile readers giữ nguyên, không mass-migrate authored files.

## Status

DONE — 11 focused plugin tests + 4 engine boundary/provenance tests pass. Versioned immutable profile/rule-set/static binding contracts produce original canonical CslModel/GoalModel; complete preserved source feature decisions match legacy mapping and legacy bundle/replay tests pass. Profile V2 + changed domain view changes exact program/binding/M1 hashes without core changes. Required unsupported decisions return structured import diagnostics, distinct from verification FAIL. Source/model/profile/duplicate/Agent-as-Entity/mutable M1/JSON negatives pass. SemanticProgram owns no MSystem. Source-verified refinement: MappingRuleSet.Contract is the unbound declaration; neutral OclContractSpec requires actual MClass/MOperation and is resolved after native schema in Phase12, avoiding a fake model or legacy IR. Evidence `use-plugin/verification/semantic-migration/phase-9/{acceptance.json,test-suites.json,profile-replacement.json,acceptance-final.log}`.

<!-- PHASES_5_9_END -->

# Phase 10 — JaCaMo → real CslModel Static Mapping

## Objective

Map official static ModelSnapshot sang original CslModel v4 và exact semantic/native binding index.

## Why this phase exists

Current OrganizationalMappingRules trả CslMonitorConfiguration/local CslSchema và cần caller author domain. Import chưa có new engine mapping path.

## Dependencies

Phase9 contracts, Phase4 CSL frontend/model, Phase5 backend, source official semantic contract đầy đủ/type evidence.

## Current reusable implementation

EXISTING OfficialProjectAdapter/Moise adapter, JacamoSpecificationModel, SourceModelNormalizer, MoiseUseSymbols, OrganizationalMappingRules, DomainProjection và trace/loss decisions.

## Source files/classes involved

U02/U06/U07/U13; `S/mapping/*`, canonical CslModelFactory/CslModel/validator, source org/group/role/artifact/property metadata, pipeline.

## Files/classes to add

`S/mapping/JaCaMoCslMappingRules` và `JACAMO_MAPPING_V1` rule provider/config; static mapping K fixtures; program→backend binding adapter.

## Files/classes to modify

JaCaMoStaticMappingAdapter/SemanticBindingIndex, organizational provider adapter, CodeGroundedNativePipeline new explicit-program build route; chưa switch default.

## Files/classes NOT to modify

Core CslModel/validator/grammar, official producer extraction APIs, local CslSchema authority của old profiles, reference repos.

## Detailed tasks

- [x] Group→OrgCtx, selected source roles→CSL Role, exact source inheritance/owner trace theo original M1 APIs.
  - [x] Single parent/same-kind/collision guards; abstract MOISE role chỉ map qua explicit abstraction decision, không lặng lẽ đổi thành concrete role.
  - [x] MOISE cardinality/scoped compatibility nằm source trace/neutral backend policy/OCL extension khi admitted; không nhét legacy membership counts vào v4 để giả grammar support.
- [x] Artifact class→authored CSL Entity view với exact Java/property typed binding; declarations không tạo actual instances.
  - [x] Enum/scalar properties theo codec/capability, opaque value diagnostic.
  - [x] Agent là M0 player/observer; beliefs mental evidence, không automatic Entity/attribute authoritative fact.
- [x] Tạo real CslModel trực tiếp hoặc qua original AST/factory, gọi validator gốc; build MModel bằng Phase5.
  - [x] Source-target trace mỗi rule, deterministic schema/program hash, no lossy local schema conversion.
  - [x] Text .csl export không bắt buộc live; nếu thêm emitter thì original parse/model roundtrip và versioned loss sidecar.
- [x] Không auto-author domain invariant/Goal/Task từ tên ASL/operation; authored specification tham gia program hash.

## Semantic requirements

Giữ M1 declaration/M0 population tách biệt; không hidden mapping trong engine; context/role bounds chỉ verified khi observed population đủ.

## Runtime requirements

Static mapping chạy một lần per source/profile revision; output frozen, no live framework references/connection dependency trong model.

## Error / unsupported handling

Missing type/exact source binding/multiple parent/abstract abstraction/scoped policy → decision rõ; mandatory unsupported chặn import new profile, không fallback âm thầm.

## Tests

- [x] Unit test: group/role/artifact typed rules, stable symbol/index hashes.
- [x] Integration test: official ModelSnapshot → real CslModel → original validator → MModel.
- [x] Negative test: homonymous source IDs, partial source definition, belief-as-world, unsupported inheritance/codec.
- [x] Parity test: K/M1 structural claim với current rules trên admitted intersection; differences có profile.
- [x] Regression test: existing SemanticTransformationTest/native import và source-preservation tests.

## Acceptance Criteria

- [x] AC10.1 Mapping tạo actual original CslModel và validator được gọi; K pass.
- [x] AC10.2 Full source/binding/provenance trace, không fake M0 hoặc unreported semantic loss.
- [x] AC10.3 New build route dùng Phase5 backend, old profiles giữ nguyên.

## Risks

MOISE role abstraction/cardinality không đồng cấu CSLv4; richer artifacts/Java overload thiếu source evidence. Chỉ admit explicit views và keep source complement.

## Rollback / compatibility strategy

Mapping profile opt-in; lưu authored config nguyên bản, không migrate old JSON in place hoặc sửa `.jcm` nguồn.

## Status

DONE — 20 focused plugin tests + 4 engine boundary/provenance tests pass. JaCaMoCslMappingRules enriches actual CslModel from exact selected MOISE Group/Role definitions and authored Artifact views; original validator, same-identity inheritance, singular declaration ownership, explicit abstract→concrete view decisions, typed scalar/enum and opaque/type/partial/ambiguity negatives pass. CanonicalUseBindingAdapter joins source IDs to exact native symbols. buildCanonicalProgram uses Phase5 backend and existing state builder/exporter/Session, one empty MSystem with no fabricated population. Legacy structural intersection/source complement/native import regressions pass. Source correction: DomainProjection.propertyType widens integer to Real in old native mode; canonical path instead reuses strict ScalarValueCodec plus exact CSL type and does not change old route. MOISE contextual counts/scoped links remain explicitly TRACE_ONLY in V1, declaration members are not runtime cardinality claims. Native contracts gated until Phase12. Evidence `use-plugin/verification/semantic-migration/phase-10/{acceptance.json,acceptance-final.log,canonical-static.use}`. Reference statuses unchanged; 125 selected source/hash/import checks pass.

# Phase 11 — JaCaMo → real GoalModel Static Mapping

## Objective

Map authored JaCaMo goal/task abstractions sang original iStar GoalModel và exact source/runtime occurrence bindings.

## Why this phase exists

Active Jason goal/MOISE achievement acknowledgement không tự là iStar satisfied Goal; OPlan ordering không tương đương AND outcome graph.

## Dependencies

Phase9 mapping contracts, Phase6 iStar frontend/validator, Phase7 contextual engine, Phase10 static program/bindings.

## Current reusable implementation

EXISTING FunctionalMappingRules/OPlanOutcomeMapper/MoiseRequirementBinder, RequirementBindingResolver exact binding policy, official schemes/missions/goals/roles source trace.

## Source files/classes involved

`S/mapping/FunctionalMappingRules`, existing OPlan/missions binders; original GoalModel/Actor/Agent/Role/Goal/Task/Dependency/AndRefinement/OrRefinement/validator/activation graph; source observers.

## Files/classes to add

`S/mapping/JaCaMoGoalMappingRules`, authored goal abstraction/binding configuration; L fixtures với two actors/two contexts/AND/OR/dependency.

## Files/classes to modify

JaCaMoStaticMappingAdapter/SemanticProgram/SemanticBindingIndex, functional providers thành versioned output adapter; no original recurrence edits.

## Files/classes NOT to modify

Original iStar grammar/model to force unsupported features; local RequirementModel old profile; source OPlan/ASL/MOISE documents.

## Detailed tasks

- [x] Tạo real GoalModel qua original public model/factory APIs và validator; actors/element IDs namespaced deterministic.
  - [x] iStar Role/Agent là authored intentional actor view, không automatic CSL Entity.
  - [x] Temporal type/condition/root activation policy explicit, không cast MOISE enum/name.
- [x] Map Task chỉ khi abstraction có pre/post/binding/source operation/execution evidence scope.
  - [x] Preserve MOISE scheme/mission/OGoal/OPlan source and order descriptor; selected outcome aggregation không claim sequence/parallel execution equivalence.
  - [x] Norm remains observer; mission commitment và board reported satisfaction có distinct provenance/quality.
- [x] Tạo AND/OR và contextual dependency graph trong original model admitted subset.
  - [x] Exact depender/dependee/dep end/actor context direction; preserve source-target relation trace.
  - [x] Task status-domain/unsupported activation/refinement admission, root demand and episode policy explicit.
- [x] Join actor/goal/task definition IDs với separate runtime binding plan; không instantiate all declarations thành live fulfilled occurrences.

## Semantic requirements

Original GoalModel canonical; GoalType/Task historical semantics theo engine profile. Missing binding/condition không tự defaults true để “có output”.

## Runtime requirements

Occurrence templates chưa temporal history; actual cast đến accepted runtime population. Same source role name ở hai contexts/OS không merge.

## Error / unsupported handling

Unsupported OPlan/trigger/query translation → loss/unsupported mapping decision; no dummy Goal/Task behavior. Mandatory abstraction missing chặn profile import.

## Tests

- [x] Unit test: rule/actor/Goal/Task/dependency/refinement IDs, loss/order descriptor.
- [x] Integration test: official scheme/roles + authored abstraction → real GoalModel → original validator/context engine.
- [x] Negative test: Jason goal-as-satisfaction, sequence-as-AND, wrong temporal enum, ambiguous player/context.
- [x] Parity test: L/D/G model/graph against original factory and contextual oracle; local expected differences explicit.
- [x] Regression test: existing OPlanSemanticsTest/observer/norm/current goal-view scenarios.

## Acceptance Criteria

- [x] AC11.1 Real GoalModel/validator được gọi và L pass; no custom RequirementModel authority mới.
- [x] AC11.2 Mapping constraints/trace preserve order/context/source semantics, unsupported không mất.
- [x] AC11.3 Instance activation/satisfaction chưa bị suy từ declaration hay name.

## Risks

Authored goal mapping còn chưa chốt, social dependency direction/cast explosion. Version rule set và admit minimal complete slice trước.

## Rollback / compatibility strategy

Profile/version riêng cho new engine; old FunctionalMappingRules results/local monitor không bị reinterpret.

## Status

DONE — 29 focused plugin + 46 engine frontend/context/recurrence/provenance tests pass. Authored original GoalModel is joined to exact Actor/Goal/Task source selectors in GoalRuntimeBindingPlan; original compiler/factory/validator/context engine are called. Two independent context sessions prove AND/OR/dependency results and no cross-context history transfer; model/aggregate parity uses original APIs, with isolated original source parity retained in Phases6–7. Two-agent/Group/Role/Scheme/Mission/Counter fixture added under use only. FunctionalMappingRules + ExecutionDescriptor reused for exact source order/enabling; legacy local OPlanOutcomeMapper/MoiseRequirementBinder result IR is reference-only for this route. Explicit OUTCOME_ONLY is required; sequence/parallel scheduler equivalence never claimed. Missing source/self/actor/task contract/root demand, wrong temporal enum, active goal-as-satisfaction, Norm-as-condition and mismatched outcome graph rejected. Declaration templates create no runtime cast/history/MSystem. Phase9 test fixtures now supply the mandatory intentional binding metadata; assertions unchanged. Evidence `use-plugin/verification/semantic-migration/phase-11/{acceptance.json,acceptance-final.log,goal-mapping.json}`; live new-profile acceptance remains Phase22.

# Phase 12 — OCL Contract Mapping

## Objective

Nối authored/source-backed CSL invariant, Goal condition, Task/operation contracts vào native OCL specs và exact runtime checkpoint bindings.

## Why this phase exists

Model có OCL text chưa đủ: cần native context/signature/argument/result và @pre before/after; ASL/NPL text không tự thành OCL.

## Dependencies

Phase8 native backend, Phase10/11 program/bindings, U11 operation checkpoints và source exact descriptors.

## Current reusable implementation

EXISTING CslUseBinding exact operation descriptor/reflection hooks; SemanticOclCompiler native symbols; NativeOperationCheckpoints performer/correlation/capability checks; external OCL policy registry.

## Source files/classes involved

S/csl operation contract/binding code, S/ocl registry/compiler, R/NativeOperationCheckpoints, official operation metadata; original Goal/Task constraint model accessors.

## Files/classes to add

`S/mapping/JaCaMoOclContractMapping`, operation→Task binding descriptor/crosswalk; contract input validation and I/J/K/L fixture specs.

## Files/classes to modify

SemanticProgram/BindingIndex, native operation checkpoint registry adapters nhận neutral specs thay local-only schema; existing wrappers preserve signatures.

## Files/classes NOT to modify

Host evaluator, original TaskMarking semantics, bridge protocol tùy tiện, ASL/NPL sources, old config contracts.

## Detailed tasks

- [x] Map exact invariant/condition text với source language/body/owner/location/hash; explicit authored OCL giữ provenance riêng source-derived abstraction.
  - [x] Không string-replace ASL belief/context/plan guard hoặc norm modality thành OCL.
  - [x] Match operation class/name/parameter types/result descriptor, overload exact; reflection chỉ trên class thực có, không execute custom code để đoán effect.
- [x] Compile toàn contract bằng Phase8; join with original Goal/Task constraint IDs.
  - [x] Pre/post/native @pre, argument/result symbols và before/after witness spec; producer field absent → declared capability unavailable.
  - [x] Distinguish state-conditioned historical Task input với exact invocation contract result.
- [x] Runtime pre-state retention/correlation/actor/artifact incarnation binding và cleanup policy.
  - [x] Interference/overlap/unobserved result negative, no fallback “nearest” event.
  - [x] External/manual OCL policies vẫn dùng shared native state và HARD pause contract existing.

## Semantic requirements

Boolean type checks fail static compile; condition body/hash tied to canonical target. Counter increment contract thực `self.p = self.p@pre + 1`, không reconstructed arithmetic evaluator.

## Runtime requirements

Only accepted PRE/terminal cut creates/consumes interval; checkpoints thuộc current run/program; exact bindings cloned/pinned cho worker.

## Error / unsupported handling

No PRE/result/signature/correlation → SKIPPED/UNSUPPORTED with reason; native false → contract FAIL distinct historical Task status; no auto false-on-error.

## Tests

- [x] Unit test: operation descriptor/overload/constraint context mapping.
- [x] Integration test: mapped Counter pre/post compile và native pair-state evaluate.
- [x] Negative test: ASL-as-OCL, wrong type, missing result, stale invocation và overlapping changes.
- [x] Parity test: I/J với native USE oracle và unchanged original model contract slots.
- [x] Regression test: NativeInvocationSemanticsTest/OclSemanticBackendTest/current user OCL policy.

## Acceptance Criteria

- [x] AC12.1 Full OCL checklist mục8.2 traceable tới specs/bindings/tests.
- [x] AC12.2 Correct @pre/args/result witness; unavailable evidence không thành failure/success giả.
- [x] AC12.3 Invocation conformance và Task history xuất hai semantics rõ trong result model.

## Risks

Missing producer return values, operation interleaving, property@pre model mismatch. Restrict feature theo advertised observed capability, preserve diagnostics.

## Rollback / compatibility strategy

Keep existing operation registry/readers; new contract mapping tied profile hash, không chỉnh expected old recordings.

## Status

DONE — Full reactor 809 tests pass (617 plugin, 85 engine), zero failures/errors/skips. JaCaMoOclContractMapping binds canonical Goal/Task condition slots and authored CSL/OCL to exact native MClass/MOperation on the one pipeline model; existing DomainProjection descriptor/reflection/signature hooks and NativeOperationCheckpoints correlation/pins reused. Native @pre 0→1 PASS/0→2 FAIL, args p0, missing PRE, changed completion args, wrong incarnation/overlap, required property coverage and native compile/type/context/slot/body/origin negatives pass. Real reflected int-returning Method demonstrates result is statically typed while the current logger does not observe it: detailed UNSUPPORTED + aggregate SKIPPED, no synthesized value. INVOCATION_CONFORMANCE is explicitly separate from original Task history, wired in Phase14–15. Required OriginalCslAttribute coverage supported; old native/CSL/manual OCL/bridge/replay/UI regressions all pass. Evidence `use-plugin/verification/semantic-migration/phase-12/{acceptance.json,full-test-suites.json,full-regression.log,canonical-ocl-inventory.json}`. No host evaluator/core recurrence patch; new-profile live acceptance remains Phase22.

# Phase 13 — Runtime State Mapping

Execution evidence (17/10/2026): canonical runtime mapping/writer injection, exact occurrence cast adapter và quality gate đã có. `phase-13/canonical-runtime-focused-r7.log`: 17 focused/regression tests pass. Full regression lần đầu: 627 plugin tests, 8 ERROR ở fixture canonical do TraceComplement Base64 vượt guard chuỗi 1 MiB; các lỗi được giữ trong `phase-13/full-regression.log`. Root fix bổ sung overload encode với explicit internal string bound (total 16 MiB/depth 64 giữ nguyên); default TCP/config encoder/decoder không đổi. `phase-13/asset-regression-r2.log`: contract/admission tests và 13 plugin tests pass, gồm negative limits và old mapping replay. Full regression sau fix **BUILD SUCCESS: 821 tests (629 plugin, 85 engine), 0 failure/error/skip**; evidence tổng hợp tại `phase-13/acceptance.json` và `full-test-suites.json`.

Source correction: native generic `link()` đã dùng ordered `MObject[]` cho n-ary. New route tái sử dụng writer đó, chỉ mở live capability khi MappingRuleSet chọn explicit tuple authority/source; không viết một binary adapter giả n-ary. M0 writer/savepoint/rollback giữ nguyên authority. CArtAgO/MOISE fixture là thư viện thật trong test JVM, **không thay** live external JaCaMo/TCP gate Phase 16/20–22.

## Objective

Ánh xạ admitted snapshot/event sang native typed mutation kế hoạch và engine-neutral occurrence inputs trên canonical bindings mới.

## Why this phase exists

Static models không cập nhật actual state; existing SemanticObservationMapper/local schema phải adapt mà giữ identity/incarnation/coverage fidelity.

## Dependencies

Phase10–12 program/contract/binding, Phase5 native schema, U08/U09 admission/writer/Port, official snapshot/event shape hiện tại.

## Current reusable implementation

EXISTING SemanticObservationMapper artifact/property/group/agent, NativeRuntimeMutationEngine semantic Port/identity metadata/reconcile, projector ranks/removals, source quality/mental/norm observers.

## Source files/classes involved

`S/runtime/SemanticObservationMapper`, `R/NativeRuntimeMutationEngine`, `NativeRuntimeProjector`, original source contract runtime fields, RequirementBindingResolver policy.

## Files/classes to add

`S/mapping/{RuntimeStateMapping,JaCaMoRuntimeStateMapping}`, `S/runtime/{SemanticMutationPlan,JaCaMoOccurrenceBindingAdapter,ObservationQualityGate}`; M/N/P/Q/R tests.

## Files/classes to modify

Current observation mapper/native writer target binding seam; NativeObjectBindings/runtime incarnation index adapters; typed enum/n-ary ports only when admitted.

## Files/classes NOT to modify

Bridge admission/sequence/watermark policy, engine original models/recurrence, source framework objects, old mapper route before parity.

## Detailed tasks

- [x] Runtime mapping nhận đã-admitted data và frozen SemanticProgram/bindings; emit typed plan, không compile static schema mỗi event.
  - [x] Artifact UUID/incarnation/property identity → exact native object/slot/type; observed deletes/disposal reconcile only authoritative scope.
  - [x] GroupBoard role enact/quit/player/context → exact role occurrence links; no declaration-created population.
  - [x] Typed enum/scalar decoding; missing fields/authoritative source absent vẫn partial, không default/false/empty-complete.
- [x] Runtime index joins source→canonical declaration→native object; preserve alias/tombstone/generation/episode and context scope.
  - [x] Context cast update/withdrawal recompute topology only khi population change; stable keys preserve allowed history.
  - [x] Mental/Jason/Norm/mission evidence dùng observer channels, không write domain property như fact trừ authored abstraction.
- [x] Adapt Port/writer savepoints cho new bindings.
  - [x] N-ary schema supported chỉ có live mutation khi authoritative tuple input và full create/remove/rollback/export tests; thiếu tuple producer → live capability UNSUPPORTED, không giả binary links tương đương.
  - [x] Snapshot population replacement và event delta cross-check source authority, no blanket deletes on PARTIAL snapshot.
- [x] Quality gate outputs known Boolean candidate only khi exact context/source/capability/correlation đủ; giữ discontinuity separately.

## Semantic requirements

Một authoritative state, belief/goal declaration/norm observer không tự biến domain truth. Missing/partial population không vacuous PASS để chứng nhận full constraint.

## Runtime requirements

Mapping plan applied trong writer transaction; never mutate live MSystem từ TCP callback thread; epoch/revision fence trước plan và trước commit.

## Error / unsupported handling

Type/identity ambiguity fail batch trước mutation hoặc evidence-only theo rule; rollback on native error. PARTIAL/GAP không convert false; unsupported codec/n-ary có trace/capability report.

## Tests

- [x] Unit test: typed property/role/context/identity plan và removal scope.
- [x] Integration test: M/N accepted snapshots+deltas → native state + exact occurrence index.
- [x] Negative test: P/R partial/stale/disposed/reincarnated/unknown property/duplicate identity.
- [x] Parity test: current mapper behavior trên admitted scalar/role intersection và expected typed extensions.
- [x] Regression test: current SharedStateAcceptance/BridgeRuntime/coverage/observer tests.

## Acceptance Criteria

- [x] AC13.1 M/N state updates pass trên canonical binding, no static recompile/fake population.
- [x] AC13.2 P/Q/R source quality/identity semantics giữ nguyên; one writer/system.
- [x] AC13.3 Unsupported runtime features explicit; topology/binding rollback không leak old incarnation.

## Risks

Source-local cuts non-atomic; partial replacement; old binary Port hides n-ary limitation; context cross-talk. Exact source scope/key/feature tests bắt buộc.

## Rollback / compatibility strategy

Profile routing keeps old observation mapper; retain neutral producer schema unless additive versioned evidence field thật sự thiếu và có tests both sides.

## Status

DONE — focused/runtime/negative/parity intersections và full reactor regression pass; xem `use-plugin/verification/semantic-migration/phase-13/acceptance.json`. Live external new-profile gate vẫn ở Phase 16/20–22.

# Phase 14 — Runtime Engine Session

## Objective

Nối canonical program, original engine adapter và shared native runtime vào lifecycle owner của USE.

## Why this phase exists

CslStateEvaluationSession nguyên bản là file/legacy/subset workflow, không có attach MSystem/live/update lifecycle. Không tạo fake API trên class đó hoặc second state engine.

## Dependencies

Phase7/8 engine/OCL, Phase9–13 mapping/bindings/runtime plans; U05/U07/U10/U14 lifecycle/coordinator/verifier.

## Current reusable implementation

EXISTING facade import/activation tickets, coordinator shared system, SemanticRuntimeVerifier observer/quality/evidence hooks, acceptedSnapshot reset, original pure marking session Phase7.

## Source files/classes involved

DefaultJaCaMoFacade, CodeGroundedNativePipeline, NativeUseSessionActivator, RuntimeVerificationCoordinator, SemanticRuntimeVerifier, SemanticProjectionProfile/configuration.

## Files/classes to add

`S/runtime/RuntimeEngineSessionOwner`, `SemanticResultAdapter`; session ownership/engine profile configuration; S/O lifecycle tests.

## Files/classes to modify

Facade/new profile import path, pipeline/state builder binding injection, coordinator/verifier construction/session seam; old constructor path retained.

## Files/classes NOT to modify

Reference CslStateEvaluationSession/trace file compiler; original marking recurrence; host Session/MSystem APIs; no new standalone CSL/iStar runtime process.

## Detailed tasks

- [x] Create program/engine/OCL/cache/binding/quality owner at pending import; activate only after validators/schema/native compile + accepted cut.
  - [x] Instantiate one MSystem in state builder and assert `Session.system == projector.system == verification.system`; engine receives state read-view, không constructor MSystem.
  - [x] Initial cut = one baseline semantic boundary, no history for object-by-object construction.
- [x] SemanticRuntimeVerifier chooses old monitor or original engine adapter by immutable profile, giữ mental/norm/invocation/coverage hooks.
  - [x] Original GoalMarking/TaskMarking/Propagation actual calls, results include engine/profile/hash/raw status/quality/witness.
  - [x] Session identity key đầy đủ; acceptedSnapshot same-owner preserve known history, new-owner reset; cache model identity fence.
- [x] Implement savepoint/reset/close resource contract cho owner và evaluator lanes; integrate further transaction guarantees Phase15.
  - [x] STOP monitor freezes final result, release transient checkpoints/cache/listeners; START/REIMPORT creates/reuses owner chỉ theo policy mục9.
  - [x] Stale delayed result/callback không activate old model/history.
- [x] Bounded engine history/topology/worker queue metrics, session diagnostics; no mutable static engine owner.

## Semantic requirements

Raw core status không là observation certainty; no NONE/Task reinterpretation. Runtime history never inside canonical mutable GoalModel definition.

## Runtime requirements

One authoritative active MSystem; lifecycle mục9; worker waits/background compile không block EDT. External producer may continue after monitor stop.

## Error / unsupported handling

New import failure giữ old session nếu handoff chưa commit; runtime quality/error outputs explicit. Missing engine/program mismatch chặn new profile, không fallback local hidden.

## Tests

- [x] Unit test: owner key/savepoint/reset/close/idempotence/cache revocation.
- [x] Integration test: one Session/system + real original engine call witnesses và Goal satisfaction O.
- [x] Negative test: stale callback/foreign MModel/missing binding/unknown input không false.
- [x] Parity test: known accepted trace gives Phase7 raw marking parity; quality overlay independent.
- [x] Regression test: old profiles/default/manual OCL/observers/Session replacement.

## Acceptance Criteria

- [x] AC14.1 Original engine chạy trên state/cut projector đã accept; system identity assertion pass.
- [x] AC14.2 Lifecycle/quality/history owners tách đúng, no second live MSystem/event subscription.
- [x] AC14.3 O/S tests pass, old profile remains usable và defaults chưa đổi tự động.

## Risks

Verifier final-field/local-config coupling, observer loss khi thay recurrence, history cache leak. Add explicit session seam, tests cho every old hook.

## Rollback / compatibility strategy

Factory/profile lựa chọn rõ; retain old verifier constructor path and compatibility recordings. Close new owner trước fallback/reimport, không chạy song song hai authorities.

## Status

DONE — `use-plugin/verification/semantic-migration/phase-14/acceptance.json`: final focused acceptance 36/36 (14 neutral engine + 22 plugin), zero failure/error/skip; original raw Goal/Task/AND/OR/contextual dependency calls, one authoritative host system, exact owner/cut/cache fences, same-run history/new-generation reset, close on EDT, bounded 8 pins / 16 MiB serialized estimates, checkpoint restore and old-profile/manual native regressions pass. Full initial reactor ran 832 tests: 830 passed; 2 fixture/source-guard defects were corrected and rerun green in acceptance-r4.log. Consolidated suite coverage is 832/832; this is not a claim that the initial full invocation was green. Full clean release regression remains Phase22. Source correction: standalone Reviewer and contextual dependency endpoint are distinct original occurrences; only the bound endpoint propagates. Native Evaluator lacks force cancellation; finite worker cleanup/future revocation verified, no arbitrary evaluation kill claim. Phase15 fault/atomic publication, Phase16 external TCP and Phase17 UI gates remain pending.

<!-- PHASES_10_14_END -->

# Phase 15 — Transaction / Savepoint / History Integration

## Objective

Đảm bảo native state, bindings, operation intervals, original engine history và published result cùng accepted boundary; lỗi không latch state chưa commit.

## Why this phase exists

Current transaction catch chỉ bao action/structure; finish/evaluation/journal có failure boundaries riêng. Thêm engine vào đó mà coi mọi thứ đã atomic sẽ gây divergent history/replay.

## Dependencies

Phase13 mapping plan, Phase14 session owner, Phase8 native pair-state, source U08/U09/U10/U11.

## Current reusable implementation

EXISTING native savepoint/rollback, protocol image, local semantic/operation savepoints, stateVersion/snapshot/journal; copied host MSystemState snapshots.

## Source files/classes involved

R/RuntimeVerificationCoordinator transaction/acceptedSnapshot/finish/read, NativeRuntimeProjector, NativeRuntimeMutationEngine, NativeOperationCheckpoints; S/runtime owner/verifier/result adapters.

## Files/classes to add

Transaction boundary/staged engine commit DTO nếu existing Mutation không đủ; failure-injection tests và ordered evaluation/publish fence tests.

## Files/classes to modify

Coordinator/owner savepoint orchestration, projector protocol rollback join, runtime binding index and operation cleanup; preserve public legacy transaction behavior through adapter.

## Files/classes NOT to modify

Original Boolean recurrence/propagation bodies, host state rollback APIs, source JaCaMo execution, TCP identity rules; no distributed transaction claim.

## Detailed tasks

- [x] Thực hiện failure/commit policy mục8.1, xác định rõ commit point và source/protocol/semantic-history quality dimensions.
  - [x] Native mutation exception → rollback native/index/operation/engine/protocol staged images; no temporal advance.
  - [x] Native accepted + semantic evaluator internal error → keep observed state, discard semantic staged history, report ERROR + semantic-history discontinuity; không cáo buộc source sequence bị GAP nếu sequence vẫn đúng.
  - [x] Native false/undefined/error outcome không mặc định exception/rollback; violation evidence preserved.
- [x] Pin true before/after snapshots và typed invocation variables; original markings stage/commit đúng boundary order.
  - [x] Rejected snapshot/event restore exact previous owner/history; accepted run/generation/revision change resets đúng scope.
  - [x] Phase7 aggregate/leaf/topology savepoints và Phase13 binding quality indexes được restore chung.
- [x] Merge native/user OCL, original engine result, invocation/norm/mental outcomes theo namespaced IDs.
  - [x] Không overwrite mất external OCL outcomes khi new semantic envelope thay legacy list.
  - [x] Journal/snapshot failure after commit → stale/gap output và temporal quality taint; no fake verified cut/replay completeness.
- [x] Ordered evaluation worker/publish fencing + backpressure theo mục8.4; UI no deadlock.
  - [x] Fault injection ở mutation/validation/evaluation/propagation/snapshot/journal/notify từng bước.
  - [x] Observer callback failure không rollback accepted source fact hoặc retry semantic history hai lần.

## Semantic requirements

At-most-once semantic advance per accepted boundary; same state/cut hash for OCL/Goal/Task; no stale state certified after persistence failure.

## Runtime requirements

Một serial writer, one ordered engine lane/session; no listener calls dưới lock có thể chờ EDT vòng ngược; bounded pins/work queues; close/cancel fences pending work.

## Error / unsupported handling

Mỗi lỗi có policy rollback/commit-degraded/evidence-only rõ. Rollback failure quarantine owner, không cho fresh session reuse half-restored system/history.

## Tests

- [x] Unit test: engine/native/index/operation savepoint roundtrip và exactly-once advance.
- [x] Integration test: accepted event→state/OCL/marking/journal/UI cùng version; worker ordering/close fence.
- [x] Negative test: injected failure at every boundary, journal overflow, stale result, rollback failure.
- [x] Parity test: full-known committed trace raw engine history equals original; rejected trace has no extra step.
- [x] Regression test: NativeSemanticFailureTest/NativeInvocationSemanticsTest/shared-state/replay/pause policies.

## Acceptance Criteria

- [x] AC15.1 Failure matrix không có history/native divergence hoặc violation bị che bằng rollback.
- [x] AC15.2 State/version/program/engine result/journal quality nhất quán; external OCL outcomes preserved.
- [x] AC15.3 UI/thread ordering/cleanup tests pass, old transaction clients không regression.

## Risks

finish ngoài catch, persistence after commit, reentrant notification, unbounded pinned state. Explicit staged commit và failure tests trước live acceptance.

## Rollback / compatibility strategy

Giữ old-profile adapter và transaction test contracts; enable new session route sau fault-injection gate; không giữ hai writers để rollback.

## Status

DONE — `use-plugin/verification/semantic-migration/phase-15/acceptance.json`: full-regression-r14.log 848/848 reactor tests, zero failure/error/skip. Fixed-clock focused r13 15/15. Native/index/interval rollback, rollback-failure quarantine, post-commit semantic errors, true native @pre worker pins, namespaced external OCL preservation, exact enactment/context credit, topology/history rollback, journal/snapshot/view fault stages, ordered publication, cache cleanup and existing bridge/native/replay/UI regressions pass. Baseline failures and prior r9/r12 failures retained; unique boundary publication replaces colliding appliedAt timestamps. Legacy TCP sporadic r9 root remains unproven; r10/r11/r12/r14 pass without retries or relaxed admission. Original core recurrence unchanged. Full clean/package and new-profile external MAS acceptance remain Phases16–22.

# Phase 16 — Bridge/TCP Runtime Integration

## Objective

Chạy new semantic profile qua existing external authenticated Bridge/TCP với admission, pause/resume, reconnect/resync và producer restart thật.

## Why this phase exists

Semantic engine integration không được phụ thuộc transport; current TCP machinery có giá trị và không cần thay bằng embedded provider.

## Dependencies

Phase14/15 runtime owner/transaction, Phase13 typed mapping, existing bridge contract/producer/launcher; tests fixture process do harness sở hữu.

## Current reusable implementation

EXISTING BridgeClient/LocalTcpBridgeTransport/BridgeMirrorStateMachine/SnapshotAdmission, JaCaMoBridgePlatform/SnapshotCoordinator, ExecutionControl, ManagedRuntimeWorkflow và jacamo-bridge.ps1.

## Source files/classes involved

U02–U05/U08/U15; C/RuntimeControlContract; B/snapshot sources; `PT/NativeResyncHandoffRegressionTest`, `PT/bridge/SeparateJvmBridgeTest`, `PT/semantics/LiveMasDefectRegressionTest`.

## Files/classes to add

New-profile TCP consumer/fixture test configuration, `PT/semantics/Use4GoalExternalBridgeIT` (MỚI); lifecycle/resync/producer-restart tests/evidence.

## Files/classes to modify

Facade wiring new session; launcher/test consumer profile/config passing nếu thiếu. Bridge source chỉ modify khi test phát hiện lỗi thực tế, additive contract change phải version; không rewrite transport.

## Files/classes NOT to modify

JaCaMo reference/framework; contract Actions để bịa STOP/RESTART; official source extraction; engine import TCP classes; current external profile availability.

## Detailed tasks

- [x] Connect existing TCP route to new canonical program/session; source identity/fingerprint/auth/capabilities unchanged.
  - [x] Verify subscribe-before-snapshot handoff/pending events/watermark suppression; old subscription revoke trước activation mới.
  - [x] Required source/capability absent diagnostic, no native/local fallback giả use4Goal.
- [x] Test START managed file ACK và unowned external connect; PAUSE/RESUME real Jason incarnation ACK + resync.
  - [x] Monitor STOP closes ingress/subscription/session; producer remains launcher-owned; USE/manual OCL vẫn sống.
  - [x] Monitor RESTART/REIMPORT and producer restart are distinct tests; producer new UUID/generation resets history/index/pre-state.
- [x] Test Q/R/S: disconnect/gap/partial snapshot/stale event/stale snapshot/source revision/new generation/reconnect.
  - [x] Same-run resync không double count history; gap continuity không falsely healed.
  - [x] External process can terminate by `.stopMAS` without closing consumer USE; consumer reports disconnect/stale.
- [x] Bounded repeated cycles, resources/threads/subscriptions/queues/file handles and old owner references.
  - [x] Process evidence producer PID ≠ USE PID; engine direct Java within USE, no extra CSL/iStar process/transport.
  - [x] Child producer started by test/launcher is valid architecture; no obsolete “no child process/same PID” release gate.

## Semantic requirements

Transport-neutral mapping/engine; only accepted neutral data affects state/history. Source-local watermark cuts không được quảng bá global atomic transaction.

## Runtime requirements

Authentication/fingerprint/limits preserve; TCP waits không EDT; no process-kill fallback của plugin; process teardown trong test chỉ handle do test/launcher sở hữu.

## Error / unsupported handling

Disconnect→stale/coverage/gap; new identity→fresh owner; unsupported pause capability không fake ACK. No same-JVM blocker.

## Tests

- [x] Unit test: lifecycle fences/new profile consumer selection.
- [x] Integration test: real external TCP producer→USE new session, pause/resume/stop-monitor/reimport/restart-producer.
- [x] Negative test: auth/distribution/project mismatch, out-of-order/stale/gap/overflow/disconnect.
- [x] Parity test: same admitted event recording direct test feed vs TCP gives same semantic result.
- [x] Regression test: current bridge suites/NativeResyncHandoff/managed startup modes.

## Acceptance Criteria

- [x] AC16.1 Q/R/S/V scoped live gates pass with actual external process/PID/bridge observations.
- [x] AC16.2 Stop/disconnect/producer stop không đóng USE; new generation no old history/listener delivery.
- [x] AC16.3 TCP remains backward compatible và engine code transport-independent.

## Risks

Reconnect race, producer/file ACK stale, partial source cuts and journal quota. Keep source/owner fences và fail explicit when capacity exceeded.

## Rollback / compatibility strategy

Original external runtime route vẫn tồn tại; new engine profile opt-in; không xóa launcher/TCP mode hoặc mutate reference JCM.

## Status

DONE — `use-plugin/verification/semantic-migration/phase-16/acceptance.json`. R17 focused 9/9 (live2/2), no failure/error/skip. Actual TCP recording/direct-feed parity passes native hash, exact occurrence/full-stack history/raw markings and native outcomes. Live verifies managed ACK/unowned observer, native @pre, real agents/Artifact/MOISE, PAUSE/RESUME, 3 monitor cycles with zero old caches/pins and bounded threads, producer UUID/PID replacement, same-source one-host-system reuse, gap reconnect, manual OCL after Stop, .stopMAS producer-only exit. Full r16: 854 tests, 1 replay error retained honestly; root cause separated nonblocking EDT revocation from worker delivery settlement and recognized owned offline replay, original ReplayStepFacadeTest passes r17. R16 bridge/admission/negative/handoff/managed suites passed; no claim r16 full green. Phase20–22 still require final full/clean/package gates. Protected hashes unchanged; official main-workspace alias correction and legacy single-declaration compatibility verified.

# Phase 17 — Workbench Integration

## Objective

Cho người dùng chọn/import/chạy new profile và đọc native OCL + original iStar results/quality đúng trong USE Workbench hiện tại.

## Why this phase exists

CLI/static engine test không chứng minh UI/session state integration; UI hiện có Start/Pause/Resume nhưng không monitor Stop/Restart hay mapping engine selection.

## Dependencies

Phase14–16 sessions/lifecycle/live profile, Phase9 configuration/diagnostics, existing Workbench/actions/goal/evidence views.

## Current reusable implementation

EXISTING JaCaMoWorkbenchPanel background actions/timer/viewEpoch, SemanticEvidencePanel, facade shell/menu/API, shared Session diagrams/manual OCL.

## Source files/classes involved

`P/ui/JaCaMoWorkbenchPanel`, `SemanticEvidencePanel`, goal-view adapters; JaCaMoFacade/DefaultJaCaMoFacade; plugin descriptor/actions; user-workflow docs.

## Files/classes to add

New-profile configuration/diagnostic view adapter và original engine occurrence view model (chỉ nếu existing result model chưa đủ); GUI selection/lifecycle tests.

## Files/classes to modify

Workbench/profile selection/import commands, facade monitor session Stop/Restart methods (MỚI), evidence/Goal views and README/workflow docs; descriptor chỉ khi add action cần thiết.

## Files/classes NOT to modify

Full use4Goal UI/BPMN views copied wholesale; host GUI/plugin loader; raw engine recurrence; existing native/manual OCL feature semantics.

## Detailed tasks

- [x] Selection trước import: engine semantics/mapping profile/authored spec version/capability, no mutable live switch.
  - [x] Show real canonical model/validator diagnostics/source trace; new label không chỉ rename local result.
  - [x] Display raw original status và observation quality/invocation conformance riêng; NONE PENDING không show VIOLATED.
- [x] Keep Start Runtime managed ACK, Pause/Resume Jason controls; add clearly named Stop/Restart **monitor session** actions.
  - [x] External unowned producer info/process ownership explicit; Stop monitor không kill JaCaMo.
  - [x] Import/Reimport/Disconnect/Resync/producer-generation changes cancel old callbacks/viewEpoch.
- [x] Bind all views/native diagrams/manual OCL vào same active Session system/cut; immutable result snapshots không tự trở thành state authority.
  - [x] Unsupported/missing/partial visible, no empty-table means PASS.
  - [x] Parse/compile/control/I/O background; no EDT join/wait; bounded timer/listener cleanup.
- [x] Document PowerShell run workflow theo actual USE7.5 package paths, verify generated artifacts before launch.
  - [x] Không dùng `use4Goal/use/.../use-7.1.1` path hoặc đòi copy engine sang reference.
  - [x] Giữ existing projects/OCL profiles/exports/actions usable.

## Semantic requirements

UI không đổi verdict/status để giống old monitor, không hide quality/unsupported. Source lines chỉ khi evidence có thật.

## Runtime requirements

Responsive/cancellable import/control/session teardown; viewEpoch+owner fence chống late update; removeNotify/close dừng timers/listeners đúng scope.

## Error / unsupported handling

Action capability disabled kèm reason; failed import giữ old session, failed reconnect stale. Manual OCL false không tự coi runtime PAUSED nếu policy chưa yêu cầu.

## Tests

- [x] Unit test: status/result/quality formatting, selection/readiness/action gates.
- [x] Integration test: Workbench import→model ready→start→pause/resume→stop-monitor→reimport, same system identity.
- [x] Negative test: unsupported profile, late UI callback, cancelled import, missing runtime capability.
- [x] Parity test: GUI raw engine values match engine/evidence for same committed version.
- [x] Regression test: SemanticEvidencePanelTest/ManagedAuctionWorkbenchIT/native GUI/OCL/export features.

## Acceptance Criteria

- [x] AC17.1 Người dùng chạy new profile bằng workflow hiện tại, original calls/result chứng minh được.
- [x] AC17.2 UI/manual OCL/diagrams cùng system, no block/deadlock và no stale owner view.
- [x] AC17.3 Stop/Restart scope rõ; existing features không bị xóa/đổi nghĩa.

## Risks

UI label che Task/NONE differences, timer/resource leaks, blocking coordinator. Test UI event heartbeat và delayed control/network paths.

## Rollback / compatibility strategy

Engine profile opt-in; keep current screens/actions/default profile and compatibility readers, new controls additive.

## Status

DONE — `use-plugin/verification/semantic-migration/phase-17/acceptance.json`, r5 44/44 no failure/error/skip. Includes original live Workbench (same Session/diagrams/manual OCL, raw engine UI parity, Stop/Restart/reimport), legacy Auction native/OCL/replay/reanalysis/step replay, two external TCP lifecycle/parity tests, controls12 and focused UI/selection/publication/session guards. EDT heartbeat 222 samples p95 delay62.9611ms against250ms budget. R3 reproduced live bootstrap latest/drain race; R4 deterministic completed-cut/later-blocked-work timeout; both fixed by exact captured bootstrap cut and retained-result lookup before drain, r5 regression passes. Explicit selection freezes accepted bytes; versioned source/program trace cached off EDT; raw leaf/aggregate/full stack/quality displayed without converting NONE/Task. Launcher semantic parameters and negative pre-staging checks pass. New Program/trace tab intentionally extends UI count, native expected semantics unchanged. Protected hashes and original engine inventory unchanged. Final full/clean/package gates still required.

# Phase 18 — Replay / Evidence / Compatibility

## Objective

Lưu đủ canonical program/engine/mapping/cut/history semantics để replay new profile và đọc recordings cũ đúng version.

## Why this phase exists

Replay chỉ native `.use/.soil` không tự tái hiện temporal engine, quality gaps, Task invocations hoặc mapping profile. Old recordings dùng local monitor khác nghĩa.

## Dependencies

Phase9 provenance/program schema, Phase14–17 owner/results/journal/GUI, current replay schema/readers freeze.

## Current reusable implementation

EXISTING NativeRuntimeReplay/exportBundle/replay, VerificationSnapshot/bindingMetadata, journal retained cuts, MappingBundle/TraceComplement/Codec, historical profile/version gates.

## Source files/classes involved

R/NativeRuntimeReplay, runtime replay manifest/resources, S/mapping and S/evidence attachments, snapshot serialization/quality/retention, old compatibility test fixtures.

## Files/classes to add

Versioned `SemanticProgramManifest`/codec và engine history/input/evidence replay schema (MỚI); T fixtures with expected native state/engine statuses/quality/hashes.

## Files/classes to modify

Replay loader/exporter/journal semantic attachment, result schema/versioned readers; release/compatibility manifest/docs; keep old schema rejection rules explicit.

## Files/classes NOT to modify

Recorded historical files/golden in-place, original core semantics to match old output, source JaCaMo, host OCL; no connection/live subscription in replay mode.

## Detailed tasks

- [x] Version new replay program payload: source/mapping/profile/engine/grammar/backend/capability hashes, canonical model representation/authored specs/binding trace, accepted boundary order/watermarks/quality.
  - [x] Models with setters serialized deterministically without relying on Java default object serialization/hashCode.
  - [x] Store native model/state baseline, exact invocation before/after/bindings/correlation and temporal input/reset boundaries; preserve gap events.
- [x] Replay chooses exact legacy profile or supported original engine version, never silent reinterpretation.
  - [x] Engine hash unavailable → explicit incompatibility/migration diagnostic; old local NONE/Task recordings route local compatibility.
  - [x] New replay can start from full baseline and replay admitted engine inputs, or versioned checkpoint + tail with validated ownership; no arbitrary partial tail certified complete.
- [x] Preserve evidence trace source→model→native state→OCL→raw marking→quality→result same cut/hash.
  - [x] Tamper/missing contract/model/profile/sequence rejects; secrets/connection credentials not in bundle.
  - [x] Replayed @pre native pair-state, not weak subset/regex; restart reset reproduced.
- [x] Full history retention/capacity policy: bounded runtime, archived replay completeness explicitly recorded; overflow cannot give pass replay.

## Semantic requirements

Replay reproduces **accepted observed semantics**, không claim all JaCaMo executions/liveness; profile/version differences remain visible.

## Runtime requirements

Replay one offline Session system, no concurrent live writer/connection; cancel/close releases pre-state/history/cache. Live→replay mode switch revokes owner.

## Error / unsupported handling

Missing input/history/model hash incompatible → error/inconclusive; no mark unknown false or replay partial as complete. Old explicitly unsupported schema stays rejected unless reader implemented/tested.

## Tests

- [x] Unit test: program/history codec/schema/hash/ownership validation.
- [x] Integration test: live export→replay yields same native state/OCL/core markings/quality per boundary.
- [x] Negative test: tampered/missing/gapped bundle, wrong engine/profile/version, bad @pre baseline.
- [x] Parity test: T replay original core vs live accepted trace, old local recording old expected results.
- [x] Regression test: NativeRuntimeReplay/GenericStepReplay/SemanticMappingReplay/PackagedReplay suites.

## Acceptance Criteria

- [x] AC18.1 T pass with complete trace/provenance, same semantics/cut not just final p value.
- [x] AC18.2 Old replay behavior/version rejection preserved; new engine không reinterpret silently.
- [x] AC18.3 No live network or second state owner during replay; bounded retention/overflow quality honest.

## Risks

Engine upgrade compatibility, Java model mutability/default serialization, lost pre-state, old schema hard rejection. Explicit codecs/versioned archives and independent parity oracle.

## Rollback / compatibility strategy

Add new format/version, keep existing readers and originals; migration converter optional/outside live, never overwrite recordings.

## Status

DONE — `use-plugin/verification/semantic-migration/phase-18/acceptance.json`. R4 32/32 focused tests (2 real external TCP ITs) and R6 2/2 installed legacy replay ITs pass, zero failure/error/skip. Canonical replay3.0 stores exact source/rules/profile/models/bindings/trace and implementation/grammar/native hashes; native baseline and every pending/completed result hash validated in recorded order. Native @pre 0→1 PASS, 0→2 FAIL/Task PENDING, missing PRE, tamper, gap/resync/generation reset and worker Step/Previous/Reset pass. Canonical live→offline switch revokes old engine/cache/pins; no live transport opened for replay. Old native/local2.0 readers retain semantics. Legacy reanalysis rejects3.0 explicitly pending a separately versioned policy. Live bundle archived with hashes. R5 no-test filter invocation error retained, corrected actual old Failsafe parameters in R6; required package ITs ran. Final new-profile package/live/full-clean gates remain Phases20–22. Protected hashes and original-source inventory unchanged.

# Phase 19 — Retire Duplicate Semantic Implementations: Gate Preparation

## Objective

Loại duplicate authority khỏi **new profile call graph**, chuẩn bị deprecation và evidence gates; chưa xóa old semantic source/default-switch.

## Why this phase exists

Core mới không được có hai canonical CSL/iStar definitions, nhưng local NONE/Task/replay semantics chưa thể gọi tương đương. Physical retire phải đợi full integration/package gates.

## Dependencies

Phase4–18 functional/parity/compatibility routes; actual retirement/default decision chỉ sau Phase20–21 và xác nhận Phase22.

## Current reusable implementation

EXISTING old CslSchema/RequirementModel/TemporalGoalMonitor/validator/binding APIs và old profile/replay; new canonical engine/profile route được triển khai ở phases trước.

## Source files/classes involved

S/csl local schema/validator/binding, S/istar local model/monitor, SemanticRuntimeVerifier, profile factories, BoundedExplorer/MDE/roundtrip/old replay callers; dependency/call graph tooling/tests.

## Files/classes to add

Compatibility routing/deprecation inventory và parity/expected-difference ledger resources/docs; guards test no local semantic authority in new profile.

## Files/classes to modify

Profile factories/routing docs, compatibility wrappers/deprecated annotations khi safe. Không remove source, package exclusion hoặc change default trước gates.

## Files/classes NOT to modify

Original engine semantics; historical recordings; existing source-specific quality/policy validation và current native OCL; reference repos; semantic source deletion prohibited at this checkpoint.

## Detailed tasks

- [x] Inventory actual callers cho CslSchema/RequirementModel/TemporalGoalMonitor/CslSchemaValidator/custom recurrence.
  - [x] New profile canonical path không gọi local recurrence/IR as authority; backend temporary derived IR phải deterministic/lossless và không editable độc lập.
  - [x] Old profile/offline exploration/roundtrip/replay callers được list riêng và còn route usable.
- [x] Classify equivalence versus intentional behavior change theo profile.
  - [x] NONE/Task/symbolic differences không “sửa parity” bằng expected mới trên legacy profile.
  - [x] Core well-formedness chuyển validator gốc; source-binding/coverage/structural policies vẫn adapter trách nhiệm.
- [x] Tạo deprecation plan per file/API với keep/retire-later decision, tests/gates/rollback owner.
  - [x] Chỉ deprecate usage mới; không DELETE source hoặc drop packaged reader cần recording cũ.
  - [x] Candidate switch/default được ghi pending Phase22; Phase19 exit không phụ thuộc tests chưa có trong Phase20/21.
- [x] Add guard new engine route never secretly falls back local if native/type/capability fails.

## Semantic requirements

One canonical CslModel/GoalModel new profile; behavior differences explicit, old local runtime still versioned compatibility, not mixed authority.

## Runtime requirements

Không chạy hai writers/sessions trên cùng live state để “shadow compare”; parity uses pinned read-only/offline test sessions with independent ownership.

## Error / unsupported handling

Parity failure → retain both versioned routes, fix mapping/adapter or explicit expected difference; no forced delete/default switch. Unsupported old replay feature remains explicit.

## Tests

- [x] Unit test: profile routing and forbidden-local-recurrence guards.
- [x] Integration test: old/new profile independently import/verify/replay with correct IDs.
- [x] Negative test: forced unavailable engine must fail new profile, không fallback hidden.
- [x] Parity test: equivalence ledger complete + known intentional differences tested.
- [x] Regression test: old explorer/roundtrip/native/CSL observer profiles retain behavior.

## Acceptance Criteria

- [x] AC19.1 New call graph có one canonical original engine authority, no duplicate M1/recurrence.
- [x] AC19.2 Old profile/replay preserved và retire candidates có proof/gates, không deletion.
- [x] AC19.3 Final retire/default switch remains gated by Phase20–22, dependency sequence không vòng.

## Risks

Orphaned old recording/offline feature, mislabel parity, premature package exclusion. Keep file-by-file matrix và compatibility fixtures.

## Rollback / compatibility strategy

Retain old implementations/readers, explicit profile selection; no destructive rollback/reset. Default choice reversible bằng config/versioned release.

## Status

DONE — `use-plugin/verification/semantic-migration/phase-19/acceptance.json`: 61 executable tests pass, zero failure/error/skip; R1 mixed-authority defect reproduced and R2 guarded before mapper/cache creation. Inventory31 caller/source files and ED01–ED08 expected-difference/deprecation ledger added. Canonical route original M1/owner/null local verifier; invalid native OCL cannot fallback. Old native/CSL config, explorer/roundtrip/replay retained and tested. No source deletion, no default switch; compatibility implementations KEEP/RETIRE_LATER. Final full/package/clean gates20–22 remain.

<!-- PHASES_15_19_END -->

# Phase 20 — Full Integration Testing

## Objective

Chạy toàn matrix A–V và static/runtime vertical slice thật; sửa lỗi đến khi mọi required gate đạt, không đổi semantics/expected để che regression.

## Why this phase exists

Parser/model/marking pass riêng không chứng minh source observations, one MSystem, native @pre, contextual propagation và GUI/replay tích hợp đúng.

## Dependencies

Phase0–19 checkpoints; fresh JDK21/reactor artifacts; full matrix và live scenario mục12–13; external producer dependencies/fingerprint thực.

## Current reusable implementation

EXISTING CslVerticalSliceTest/LiveMasDefectRegressionTest/SharedStateAcceptanceTest/NativeResyncHandoffRegressionTest/OclSemanticBackendTest/NativeOperationCheckpointTest và separate-JVM bridge fixtures; original parity harness đã pin.

## Source files/classes involved

All new engine/mapping/session/transaction paths; PT/semantics và PT/codegrounded/runtime/bridge/UI suites; contract/official adapter tests; original test sources read-only.

## Files/classes to add

`PT/semantics/Use4GoalExternalBridgeIT`, `Use4GoalVerticalSliceTest`, `Use4GoalLifecycleIT`, `Use4GoalReplayParityTest` (MỚI, tên điều chỉnh nếu repo conventions yêu cầu); fixture dưới plugin test resources, evidence dưới target.

## Files/classes to modify

Test harness/build test filters nếu cần; production files đúng owner của defect được tìm ra, mỗi fix quay lại phase gate tương ứng. Không broad refactor vì test fail.

## Files/classes NOT to modify

Reference repos; golden original semantics không có documented delta; current tests để giảm assertions/skip lỗi; host/source fixture người dùng.

## Detailed tasks

- [x] Run matrix A–V, log command/source/engine/mapping hashes, failures/errors/skips và evidence paths.
  - [x] Unit/parity/negative ở engine, native/shared-state/transaction ở plugin, contract/TCP/source tests đều thực thi.
  - [x] Full `mvn -B -pl use-plugin -am verify`; kiểm Failsafe thật chạy `*IT`, không chỉ Surefire unit.
- [x] Thực hiện live scenario mục13: at least two agents, actual Counter/GroupBoard/roles, scheme/mission observer nếu admitted, real original models/OCL/pre-post/AND/OR/dependency.
  - [x] Assert original engine call and code source hash, exact native stateVersion/system identity/correlation/watermarks; không mock producer/core cho live claim.
  - [x] p=0→1 positive, p=0→2 negative @pre fixture, p<0 invariant failure, unknown/gap/stale/resync/restart cases.
- [x] STOP monitor→START/IMPORT và producer stop/restart nhiều vòng có bounds, cleanup metrics và callback fencing.
  - [x] No old latch/pre-state/topology/listener; no USE exit/deadlock; source generation distinction đúng.
  - [x] Replay new and legacy profiles; same accepted semantic results/quality boundary-by-boundary.
- [x] Triage defects: classify source capability unsupported, semantic bug, adapter mapping, race/resource, baseline environment.
  - [x] Fix actual cause then rerun affected focused tests; broaden full suite sau change/failure concern mới.
  - [x] Required live/package/environment unavailable → gate BLOCKED/PARTIAL, không count mock/skip as pass.

## Semantic requirements

Compare both structural fidelity and original core behavior; intended local-vs-original differences versioned, native invalid/undefined/gap not false.

## Runtime requirements

External JaCaMo process supported, engine in USE process; one authoritative active state; bounded resources/latency test budget pinned Phase0.

## Error / unsupported handling

Failure remains visible until fixed/explicit supported-profile rejection; test environmental blocker documented, no complete claim. Journal quota overflow cannot be bypassed silently bằng unlimited retention.

## Tests

- [x] Unit test: all engine/frontend/backend/mapping/quality/session suites.
- [x] Integration test: matrix H–V/live scenario + lifecycle/UI/replay.
- [x] Negative test: all rejection/unsupported/type/coverage/correlation/ownership/resource failures.
- [x] Parity test: A–G/I/J/K/L/T and documented semantic difference ledger.
- [x] Regression test: full current native/CSL observer/OCL/TCP/USE features + all new gates.

## Acceptance Criteria

- [x] AC20.1 Required matrix A–V pass có executable evidence; unsupported capability tests assert explicit rejection, không skip required features.
- [x] AC20.2 Live two-process vertical slice chứng minh original models/core and one native state, no hidden fallback.
- [x] AC20.3 Baseline/new regressions resolved, cleanup/order/UI/replay requirements pass; findings không che bằng status.

## Risks

Flaky timing/source-local cuts, stale built artifacts, quotas raised only in tests, test process classpath tải code sai. Use handshakes/time bounds/code source hashes and packaged follow-up.

## Rollback / compatibility strategy

No default/retirement promotion on failed gate; use working old profile. Preserve failure evidence and narrow fixes, no source reset/clean.

## Status

DONE — `use-plugin/verification/semantic-migration/phase-20/acceptance.json`: full R6 BUILD SUCCESS, 869 Surefire +155 Failsafe +198 separate JUnit3, zero failure/error/skip; all A–V source/current installed-replay gates pass and exact XML counts/report hashes match this invocation. R1 obsolete selection guard, R2 cross-JVM salted annotation order and R4 shared one-tab UI helper failures retained; fixes rerun focused and full green without changing native semantic expected results. P-USE-PRINT-02 changes only neutral printer ordering, no original core/host patch. Actual external TCP, direct-feed parity, native @pre/negative/gap/reset/lifecycle/one-state/UI and new/old installed replay pass. Production Counter/sample packaging, installation guards, 64MiB release-quota and actual user helper deployment remain mandatory Phase21 gates; headless classpath missing-engine probe is recorded there and prevents final handover until fixed. Protected hashes unchanged.

# Phase 21 — Packaged Distribution Testing

## Objective

Chứng minh artifact/distribution thực tế chứa đúng một engine, resolve đúng host7.5 và chạy workflow/replay/live TCP không dựa IDE/test classpath.

## Why this phase exists

Current package shade/stage có side effects vào `use-gui/lib/plugins`, release ZIP mang fixed artifact names. Module compile pass chưa đảm bảo user launch đúng JAR/source generation.

## Dependencies

Phase20 test gate, Phase18 replay manifests, existing plugin assembly/release/staging and GUI loader mechanism.

## Current reusable implementation

EXISTING plugin Shade3.6/services transformer, release.xml, package staging task, checksums/release manifests, GuiPluginStagingIT/NativeRuntimePackagedReplayIT/SemanticPackagedReplayIT.

## Source files/classes involved

`use-plugin/pom.xml`, `src/assembly/release.xml`, release/compatibility resources, `use-gui/lib/plugins` stage, engine provenance/licenses; full-goal JAR reference inventory read-only.

## Files/classes to add

`PT/semantics/Use4GoalPackagedPluginIT` (MỚI), packaged live fixture/profile/model/provenance resources and artifact inventory test utilities under test/target.

## Files/classes to modify

Shade/artifact inclusion/resource filters, release.xml/manifests/NOTICE/license distribution, run/build README/workflow docs; no duplicate full goal plugin install.

## Files/classes NOT to modify

Host PluginClassLoader implementation để che duplicate packaging; reference distributions; original license/headers; test reports/production fixtures to hide failed feature.

## Detailed tasks

- [x] Package engine exactly once inside existing JaCaMo plugin shaded artifact (phương án chọn); engine module JAR publish cho dependency nhưng không đồng thời cài thêm duplicate engine/goal JAR vào plugins directory.
  - [x] Exclude host classes, not new adapter namespace; no old goal descriptor/Main-Class/USE7.1 classes; merge only required resources.
  - [x] Keep ANTLR4 same generator/runtime version, host ANTLR3 from7.5; no solver unless explicit offline separate artifact.
- [x] Inspect actual ZIP/JAR entries, class counts/duplicate FQCN/resources and CodeSource/ClassLoader identity.
  - [x] Original core hashes/provenance/patch/license all packaged; service transformer không được coi là tự sửa descriptor collision.
  - [x] Detect stale GUI-stage copy/parallel goal plugin with same namespace; fail diagnostic before unsafe mixed engine use.
- [x] Fresh isolated USE7.5 test install under `use-plugin/target`, không sibling reference; use generated archive/run paths actually present.
  - [x] Discover plugin, import real program, manual/native OCL, diagrams, original engine results, TCP live external producer, monitor lifecycle, replay.
  - [x] No test output directory/absolute development path/native mock class used to pass package acceptance.
- [x] Run final reactor verify with package ITs, checksum manifest against actual bytes; update reproducible PowerShell steps/evidence.
  - [x] Profile/config fixtures included, distribution no default semantic switch without Phase22 decision.
  - [x] Test declared production quotas, or record large-fixture profile capacity explicitly; no test-only huge quota masks default release failure.

## Semantic requirements

Packaged profile/hash/canonical models/core match tested source; no switch to local engine/subset due missing class/resource.

## Runtime requirements

Host owns MModel/MSystem/Expression/Value classes; plugin owns one engine copy; external JaCaMo owns framework classes/process. Parent delegation alone không đảm bảo duplicate domain classes safe.

## Error / unsupported handling

Duplicate/stale JAR or missing provenance/resource → release fail; clean **isolated test install** only after resolved paths verified, không delete user plugins/reference artifacts automatically.

## Tests

- [x] Unit test: artifact inventory/manifest/duplicate detection.
- [x] Integration test: U packaged plugin discovery/OCL/live TCP/engine/UI/replay.
- [x] Negative test: old/duplicate goal JAR, missing ANTLR/model/profile/resource, wrong host/version.
- [x] Parity test: packaged result vs reactor source-run accepted semantics/hash.
- [x] Regression test: all existing packaged/staging/native runtime/GUI tests.

## Acceptance Criteria

- [x] AC21.1 U pass trong fresh USE7.5 distribution không test/IDE classpath, source/engine hashes đúng.
- [x] AC21.2 No duplicate host/engine/plugin registration; attribution/provenance/resources/checksums đầy đủ.
- [x] AC21.3 User run instructions reproducible từ actual release ZIP, existing plugin/USE functionality retained.

## Risks

Static global PluginClassLoader generations, shaded resource collision, old stage JAR, provided dependency missing. Verify actual installed archive/code source, không chỉ dependency tree.

## Rollback / compatibility strategy

Engine opt-in in package; maintain prior release compatibility selection; restore isolated test install from its own archive, không overwrite reference/user install ngoài scope.

## Status

DONE — full R9: **878 Surefire +159 Failsafe +198 legacy JUnit3**, 250 suites, zero failure/error/skip. Strict matrix A–V pass. Full USE ZIP được giải nén và chạy lại đúng bốn checks (Counter step1 PASS, step2 FAIL đúng native @pre; inventory và 9 deployment negatives), giữ nguyên JAR đã build. Source helper, installed headless helper và launcher GUI thật đều pass; actual `.stopMAS` producer exit0, USE vẫn sống. Evidence: `use-plugin/verification/semantic-migration/phase-21/acceptance.json`, `full-verify-r9.log`, `full-archive-live.log`, `full-archive-counter-{1,2}/`, `gui-helper-r2-acceptance.json`, `artifact-hashes.json`, `failsafe-engine-ownership.json`.

Production consumer chỉ dùng installed host+plugin và isolated driver JDK/host, không framework/project/test-output classpath; producer compile Counter thật trong example, real GroupBoard/SchemeBoard/agents qua TCP. Default journal64MiB và WORLD observations được kiểm; FULL rich trace không có cùng capacity guarantee. Installation guard giữ strict single-copy/version/hash/resource/license checks. Cả hai live cases chứng minh original calls, raw marking vs invocation conformance, manual OCL/diagram, repeated monitor lifecycle và replay parity. Windows console `conhost.exe` được ghi executable chính xác và phân biệt với JVM/runtime child. Failsafe dependency exclusions sửa duplicate engine/ANTLR do shaded JAR thay vì hạ guard.

R1–R8 lỗi/negative evidence được giữ, bao gồm production classpath coupling, rich-trace GAP, pin exhaustion, console-host baseline và duplicate Failsafe classpath; không sửa expected semantic verdict để xanh. Profile được phát hành opt-in; default vẫn NATIVE_CURRENT, không retire source/readers cũ. Final clean reactor/repeated full-archive gate tiếp tục bắt buộc Phase22.

# Phase 22 — Final Acceptance & Safe Retirement Decision

## Objective

Đối chiếu toàn Definition of Done, live/replay/package evidence và quyết định migration profile/retirement an toàn; bàn giao runnable implementation sau này.

## Why this phase exists

Không thể tuyên bố hoàn tất từ import/model-ready hoặc test counts riêng lẻ; cần chứng minh original engine, native OCL/shared state và production workflow từ đầu đến cuối.

## Dependencies

Phase20 full integration và Phase21 distribution pass; Phase19 deprecation/compatibility inventory; checklist mục12–15.

## Current reusable implementation

Toàn bộ existing system và migrated implementation từ Phase0–21; source provenance/expected-difference ledger và baseline regression evidence.

## Source files/classes involved

Final engine/mapping/runtime call graph, release manifest/docs, tests A–V/live acceptance results, legacy profile/replay paths và change matrix.

## Files/classes to add

Chỉ acceptance artifacts tương lai dưới target/release docs theo repository conventions; không thêm code chỉ để gắn nhãn pass.

## Files/classes to modify

Compatibility/deprecation/default selection/release docs sau khi gates pass. Final cleanup defect sửa tại `P/DefaultJaCaMoFacade`, `R/RuntimeVerificationCoordinator`; regression tại `PT/DefaultJaCaMoFacadeTest`, `PT/codegrounded/RuntimeVerificationCoordinatorTest`; `PT/bridge/SeparateJvmBridgeTest` pin WORLD observation đúng native snapshot scope. Physical deletion semantic source **không thuộc yêu cầu hoàn thành**; nếu đề xuất sau này cần separate reviewed change/proof.

## Files/classes NOT to modify

Reference repos/Desktop report; original core semantics hoặc historical evidence để match kết quả; existing source chưa có safe parity/compatibility proof.

## Detailed tasks

- [x] Run final static+live vertical slice từ generated package, thu artifact hashes/commands/PIDs/model/cut/core-call/native OCL/history/evidence.
  - [x] Kiểm tất cả steps mục13 và DoD mục15, không có required test skipped.
  - [x] Manual/native OCL và features USE hiện tại chạy cùng Session; stop/disconnect không đóng USE.
- [x] Audit final dependency/source diff và provenance/patch inventory.
  - [x] Mapping V1→V2 replaceability test engine hashes unchanged, no engine→plugin/JaCaMo/TCP import.
  - [x] No second authoritative MSystem, no hidden local/weak evaluator fallback, original Goal/Task/propagation calls thật.
- [x] Review Phase19 candidates dựa parity + versioned expected differences + old recording reader tests.
  - [x] Chỉ retire khỏi new route khi proven; giữ compatibility source/API/readers cho old profile. Nếu proof thiếu, retain và ghi RETIRE_LATER.
  - [x] Default new profile switch chỉ khi compatibility policy/documented upgrade path pass; không buộc xóa local monitor để coi hoàn tất.
- [x] Handover exact run/verify/replay/debug instructions, supported capabilities/limitations, failures nếu còn.
  - [x] Không claim toàn CSL/iStar grammar/formal conformance/JaCaMo liveness khi chỉ admitted observed profile.
  - [x] Nếu còn required gate BLOCKED/PARTIAL, không tuyên bố implementation hoàn tất; tiếp tục sửa trong scope hoặc nêu external blocker có evidence.

## Semantic requirements

Definition of Done tập trung selected original core + explicit profile/native state fidelity; neither missing/unsupported nor intentional profile differences represented as violated.

## Runtime requirements

External two-process TCP architecture accepted; one active authoritative USE system; restart/epoch/cleanup/UI/resource guarantees chứng minh bằng actual repeated runs.

## Error / unsupported handling

Any required gate failure blocks final acceptance, không semantic downscope ngầm. Unsupported optional language features documented/rejected with tested diagnostics.

## Tests

- [x] Unit test: final affected source suite after last changes.
- [x] Integration test: packaged live end-to-end acceptance and lifecycle.
- [x] Negative test: quality/gap/unsupported/ownership/packaging rejection gates.
- [x] Parity test: original source/hash/behavior and replay evidence complete.
- [x] Regression test: entire required reactor/legacy/native/OCL/TCP/UI/package suite pass after final modifications.

## Acceptance Criteria

- [x] AC22.1 Mọi required DoD có executable artifact, exact command/version và verified result.
- [x] AC22.2 Old semantic retirement chỉ theo proof/version, legacy functionality retained; no premature deletion.
- [x] AC22.3 Handover chạy được từ release archive, limitations honest, no same-JVM prerequisite.

## Risks

Final default switch tạo regression chưa test, paper-wide claims vượt scope, acceptance environment khác dev. Rerun impacted/full gate theo material changes và packaged reproducibility.

## Rollback / compatibility strategy

Keep explicit profile selectors/old readers and known package versions; rollback release/profile without source reset, recording rewrite hoặc second live authority.

## Status

DONE — **`mvn -B clean verify` BUILD SUCCESS**, kết thúc 18/10/2026 13:53:19 Asia/Bangkok; **880 Surefire +159 Failsafe =1.039 reactor tests trong250 suites**, thêm **198 legacy JUnit3**, zero failure/error/skip. Strict matrix **A–V (22/22)** có installed-live requirement pass. Full USE7.5 ZIP được giải nén và chạy lại bốn packaged checks với chính JAR đã build, không package/rebuild giữa chừng: Counter step1 invocation PASS, step2 invocation FAIL đúng native @pre, inventory và9 deployment negatives pass. Không còn required task BLOCKED/PARTIAL.

Evidence chuẩn: `use-plugin/verification/semantic-migration/phase-22/acceptance.json` ánh xạ **17/17 DoD** tới artifacts; `clean-full-verify.log`, `test-suites.json`, `matrix-results.json`, `legacy-junit3.log`, `full-archive-live.log`, `full-archive-counter-{1,2}/`, `artifact-hashes.json`, `compiled-class-reproducibility.json`, `protected-inputs.json`, `lifecycle-cleanup-acceptance.json`. Full archive live PIDs: USE23192/JaCaMo13456 và USE13872/JaCaMo5780; original GoalMarking/TaskMarking/IStarPropagation/contextual dependency calls thật, real models/validators, same host Session/MSystem, native invariant/@pre, source-local cuts, repeated monitor teardown và replay parity đã được assert. Consumer không framework/project/test-output classpath; parent harness/isolated driver chỉ phục vụ kiểm thử, không nằm trong production plugin.

Hai installed consumers đạt heartbeat p95 **57,23ms /47,18ms** với production64MiB journal (thực dùng11.661.226 /11.255.532 bytes), eight pins/16MiB giữ nguyên; WORLD profile explicit. Mỗi case hai Stop/Restart cycles có old owner terminated, queue/cache/pins=0; source-run ba cycles, producer PID/UUID replacement, unowned observer, direct-feed parity, legacy/manual OCL và native diagram pass. Actual `.stopMAS` producer exit0 không đóng USE. Final process audit không còn Java process. Windows `conhost.exe` được nhận diện riêng, không có JVM/runtime child do USE tạo.

R1 compile lỗi tên biến screenshot driver đã sửa và focused compile pass. R2 giữ logs native HelloWorld per-cycle trace overflow và filesystem cleanup race; WORLD test vẫn dùng bootstrap quota1.024, thêm metadata/agent assertions và giữ riêng rich-trace regression. Cleanup regression2.048 files tái hiện red trước fix; coordinator expose owned `fileCleanupCompletion`, facade dùng retirement barrier cho active/replaced/failed-import projector và TCP, chỉ await ngoài EDT. Focused expanded gate **42/42** pass rồi toàn clean/full gate trên pass; không thay expected engine/OCL verdict. Snapshot screenshot nay hiển thị bảng raw marking/quality riêng.

Source/final integrity: **123 Java+2grammar inputs** và7 provenance negative guards pass; **242 unique original core classes** byte-identical so với trước clean,1.395/1.403 class entries unchanged, chỉ hai source files sửa cleanup và nested debug line tables đổi; không sửa thuật toán core. **2.035 production input hashes**,7 artifact hashes,6 protected input hashes và reference Git statuses/HEAD giữ nguyên. `main`/HEAD giữ nguyên, không branch/worktree/commit/push.

Release giữ **NATIVE_CURRENT default**, **USE4GOAL_OBSERVED_V1 opt-in**; old source/API/replay/explorer retained theo Phase19. Không physical retirement hoặc automatic default switch. Hoàn tất cho admitted observed profile; unsupported grammar, symbolic/offline semantics, ordering/scheduling equivalence và arbitrary nonterminating native OCL force-cancel không được claim. FULL cycle trace vẫn capacity-dependent và phải báo GAP; exact replay không reinterpret incompatible binaries/resource-exhaustion errors. Run/build/verify/replay hướng dẫn: `use-plugin/docs/project/use4goal-run.md`; handoff full archive `use-assembly/target/use-7.5.0-use-bin.zip` + `.sha256`.

<!-- PHASES_END -->

## 12. Test Matrix A–V — required gates

Trạng thái cuối theo Phase22 `matrix-results.json`: **880 unit +159 integration +198 legacy JUnit3**, zero failure/error/skip; required installed-live gates và repeated full USE archive4/4 pass. `phase-22/test-suites.json` đối chiếu đúng250 XML suites/case names/hashes với clean reactor log. Tên test MỚI có thể điều chỉnh theo conventions, nhưng scenario/assertion/gate không được bỏ. Parity oracle original chạy isolated test-only source/artifact pinned upstream; patch tests so với original, không so hai wrappers cùng new implementation.

| ID | Scenario / input | Required assertions và evidence | Phase / loại | Status |
|---|---|---|---|---|
| A | CSL parser parity | Same pinned grammar/runtime/text → same AST + rejection diagnostics/positions; legacy Classroom group syntax negative | 3–4,20; unit/parity/negative | DONE — Phase22/matrix-results.json |
| B | CSL model parity | Real CslModel có exact attrs/default/mutable/optional/enums/owner/inheritance/association endpoint order/invariant; no local IR authority | 4–5,20; parity | DONE — Phase22/matrix-results.json |
| C | CSL validator parity | Duplicate/unknown/cycle/kind/cardinality/entity-centered/nested context cases same diagnostics as original | 4,20; unit/negative/parity | DONE — Phase22/matrix-results.json |
| D | iStar parser/model parity | Actor/Goal/Task/refinement/dependency same; grammar-only unsupported features rejected trước builder; no activation silently lost | 6,20; unit/negative/parity | DONE — Phase22/matrix-results.json |
| E | GoalMarking parity | Each GoalType full fields/status over false→true→false→true, activation/deactivation/reentry; NONE false PENDING | 7,14–15,20; parity | DONE — Phase22/matrix-results.json |
| F | TaskMarking parity | Original historical preSeen/postSeen formula; no VIOLATED injection; unknown tuple deferred; invocation conformance separate | 7,12,15,20; parity/negative | DONE — Phase22/matrix-results.json |
| G | AND/OR/dependency/contribution/context | Original IStarPropagation + P-CTX-01; same raw outcomes/metadata as original; exact context pairing, no hidden local recurrence, bounded convergence admission | 7,11,20; integration/parity/negative | DONE — Phase22/matrix-results.json |
| H | CslModel→MModel | Entity/Role/OrgCtx/type/enum/generalization/association/n-ary schema/invariant trace; same role identity; declaration no population; export/recompile | 5,10,20; integration | DONE — Phase22/matrix-results.json |
| I | Native OCL invariant | True/false/undefined/error/non-Boolean, typed navigation/collections, population partial; host7.5 compiler/evaluator actual | 8,12,20; native/parity/negative | DONE — Phase22/matrix-results.json |
| J | Native @pre pre/post | Before0→after1 PASS, before0→after2 FAIL; pinned two states, args/result/collections/navigation, missing PRE/overlap/incarnation negative | 8,12,15,20; native/integration | DONE — Phase22/matrix-results.json |
| K | JaCaMo static→CslModel | Official definitions→original validated CSL; Group/Role/authored Artifact view; Agent/belief not automatic Entity/world; exact trace/hash | 9–10,20; static/integration | DONE — Phase22/matrix-results.json |
| L | JaCaMo static→GoalModel | Authored abstractions→original validated iStar; GoalType/Task/Dependency explicit, OPlan order preserved; no direct enum casts | 9,11,20; static/integration | DONE — Phase22/matrix-results.json |
| M | Runtime Artifact property | Real Counter observable property p typed update, exact UUID/incarnation/class/slot/stateVersion, authoritative reconciliation | 13,16,20; runtime | DONE — Phase22/matrix-results.json |
| N | Runtime Role enactment | Real GroupBoard enact/quit, player/context/role indices correct, same-name roles/contexts distinct, incarnation reset | 13,16,20; runtime/negative | DONE — Phase22/matrix-results.json |
| O | Goal satisfaction | Native predicate known→original GoalMarking update and original aggregate/dependency calls, raw status+quality+witness exact cut | 7,14–17,20; runtime/live | DONE — Phase22/matrix-results.json |
| P | Partial population | Missing/partial source→no fabricated instance/false/vacuous certified PASS; observed false vs unknown separated | 13–16,20; negative | DONE — Phase22/matrix-results.json |
| Q | Gap/resync | Lost interval→tainted temporal quality; same-run resync handoff/watermarks suppress duplicates, no healed MAINTAIN/SUSTAIN certificate | 15–16,18,20; negative/runtime | DONE — Phase22/matrix-results.json |
| R | Stale event/snapshot | Old session/generation/revision/sequence/incarnation rejected, no mutation/history; partial cut cannot delete authoritative population | 13,15–16,20; negative | DONE — Phase22/matrix-results.json |
| S | Restart/generation | Monitor restart distinct producer restart; new run resets latch/pre-state/index; reimport/stop cleanup bounded; USE alive | 14–17,20; lifecycle | DONE — Phase22/matrix-results.json |
| T | Replay | Same accepted inputs/cuts/program→same native state/OCL/raw engine marking/quality; old profile old semantics; tamper/version mismatch rejects | 18,20–21; replay/parity | DONE — Phase22/matrix-results.json |
| U | Packaged plugin | Fresh USE7.5 ZIP install, one engine, host class identity, no IDE/test classpath, provenance/license/hash/resources present; current features pass | 21–22; package | DONE — Phase22/matrix-results.json |
| V | Real live JaCaMo TCP | Actual external producer/USE separate PIDs; >=2 Jason agents, Counter operation, GroupBoard/roles, scheme/mission scope, full static/runtime/engine/UI/replay witnesses | 16,20–22; live/package | DONE — Phase22/matrix-results.json |

### Quality/negative matrix bắt buộc bổ sung

| Input quality/failure | Native state | Core input/history | Result phải thấy |
|---|---|---|---|
| Known true, exact binding, full required coverage | Apply accepted mutation | Advance true | Native PASS + original status theo temporal history |
| Known false, exact/full | Giữ observed false/fact | Advance false | FAIL hoặc original PENDING/VIOLATED tùy semantics; không đổi NONE thành VIOLATED |
| Undefined property/value | Giữ Undefined và source quality | Không Boolean-coerce | UNDEFINED + context/source diagnostic |
| Native expression exception/invalid/type mismatch runtime | Giữ accepted fact | Không advance invalid tuple | ERROR, no fake violation/pause |
| PARTIAL/UNKNOWN/missing player/context | Không create default population/world truth | No input, retain known internal history | SKIPPED/INCONCLUSIVE với missing source/binding |
| GAP/disconnect | Last observed native state stale; resync reconcile admitted scope | Temporal continuity tainted | No MAINTAIN/SUSTAIN certificate across gap |
| Unsupported feature/codec/refinement | Reject pending program hoặc evidence-only đúng profile | Không feed core | UNSUPPORTED/loss trace, không violated |
| PRE only, post chưa biết | Pin before bounded | Defer Task tuple needing post | Invocation IN_PROGRESS riêng, no post=false placeholder |
| Post false, exact interval | Keep after actual | Historical Task per known tuple, no Task VIOLATED | Invocation FAIL distinct historical Task PENDING/status |
| New producer run/generation | Reconcile fresh population/bindings | New history/cache/episode owner | No old ACHIEVE/Task latch/norm history |
| Mutation failure | Rollback native/index/protocol/staged history | Restore | Rejected evidence, no accepted temporal step |
| Semantic internal error after native accept | Keep accepted native state | Discard new semantic history, taint continuity | ERROR/degraded, no successful verified cut |
| Journal overflow/write failure | Keep committed observed state | Fail certification/retention continuity | GAP/STALE and replay incomplete explicit |

## 13. Live acceptance vertical slice — kịch bản tái lập chính thức

Fixtures đã triển khai trong `use-plugin/src/test/resources/semantics/original-engine-live/` và production example `use-plugin/examples/csl-counter/`; staging tại `use-plugin/target`, durable evidence ở `use-plugin/verification/semantic-migration/phase-22/`. Source fixtures dùng a/b; shipped Counter dùng alice/bob, Group team, Scheme workflow/Mission m. Không sửa reference để dựng test. Tất cả20 proof obligations dưới đây được đối chiếu với `phase-22/acceptance.json`, `matrix-results.json`, source live receipts và hai full-archive Counter receipts.

### 13.1. Model và capability được pin

- [x] **Hai actual Jason agents** `a`, `b`; actual CArtAgO `Counter` có observable integer property `p`, operation `increment()` tăng 1. Negative fixture operation tăng 2 hoặc typed `set(-1)` chỉ để test assertion, không repair runtime.
- [x] Actual MOISE organization có Group `team`, Roles `worker`, `reviewer` enacted bởi hai agents; `service` Scheme / `work` Mission nếu selected source capability admit. Commitment/goal acknowledgement giữ observer evidence, không auto iStar fulfillment.
- [x] Authored static mapping profile creates **real CSL v4 model** có Counter Entity, Team OrgCtx và Role views; M0 players/enactments là explicit backend extension. No Agent→Entity coercion.
- [x] **Real GoalModel** có authored Goal/Task bindings, one AND parent với ít nhất 2 children và one OR parent với branches riêng; no child có hai refinement parents. Dependency dùng standalone endpoints không là refinement parents theo original validator. Không dùng unsupported activation/forall/pick để dựng required slice.
- [x] Native invariant `self.p >= 0` trên Counter; operation pre `self.p >= 0`, post `self.p = self.p@pre + 1`; Goal condition dùng observed counter predicate với exact declared binding/context (không giả actor có attribute p).
- [x] Task pre/post bound đúng Counter invocation/player/context; known tuple only. AND/OR/dependency graph phải pass original GoalModelValidator/GoalTypeRefinementPolicy trước run; root demand explicit.
- [x] Engine original source/provenance/patch hash, program/profile/rule/capability hashes và source fingerprint ghi vào artifact.

### 13.2. Các bước và proof obligation

| Bước | Hành động | Evidence/assertion bắt buộc |
|---|---|---|
| 1 | Launch external producer qua existing official Bridge launcher/test owner; mở actual USE7.5 packaged consumer | Producer PID khác USE PID, framework fingerprint/JCM identity đúng; TCP handshake authentication pass |
| 2 | Import `.jcm` với selected mapping/engine/authored spec | Exact source revision/project key; pending ticket không active schema nửa chừng |
| 3 | Mapping tạo real CslModel | Runtime class FQCN/code source/hash đúng migrated module; declarations/source trace đúng |
| 4 | Mapping tạo real GoalModel | Original actor/Goal/Task/refinement/dependency types; no local RequirementModel canonical authority |
| 5 | Validators gốc pass | CslSemanticValidator/GoalModelValidator call diagnostics; no skip/bypass/stub |
| 6 | Build native MModel, compile all contracts, create/activate one MSystem | Backend binding/export hash/type gate; `Session.system` identity bằng projector/coordinator system |
| 7 | Start managed reasoning hoặc observe already-live external run | File ACK/producer runUUID/generation truth; >=2 actual agent-incarnation observations, actual GroupBoard roles |
| 8 | Accept initial source-local cut | Counter runtime UUID/incarnation p=0, required source completeness/watermarks; no definition-generated objects |
| 9 | Agent a thực hiện increment qua CArtAgO | Actual PRE/SUCCEEDED correlation/signature/performer/artifact observations qua TCP, no direct test-only native state mutation |
| 10 | Project to accepted native state | p=1 in same MSystem, exact stateVersion/cut/hash, native invariant PASS |
| 11 | Evaluate Task/operation native @pre | Before p=0, after p=1 distinct pinned states, post PASS; test wrong increment → FAIL; missing PRE → explicit unavailable |
| 12 | Call original GoalMarking/TaskMarking | Adapter call witness + code source/provenance + expected complete records; a/b context/performer không cross-feed Task invocation |
| 13 | Propagate AND/OR/dependency | Original IStarPropagation + P-CTX-01 invoked; expected instance graph/raw markings/quality, endpoints admissible; no local recurrence fallback |
| 14 | UI/manual OCL/evidence | All show same committed boundary; original status vs quality/invocation separate, source trace real; manual `allInstances`/native OCL vẫn hoạt động |
| 15 | Negative p<0 + missing/gap/unsupported variants | Invariant FAIL on real negative fact, ACHIEVE latch theo profile; undefined/partial/gap không false/success giả; policy pause only negotiated HARD case |
| 16 | Pause/Resume | Jason cycle ACK, platforms may progress; resync fence preserves source/quality rules, no global freeze claim |
| 17 | Stop monitor → Start/Import lại | USE stays responsive/alive/manual OCL; old subscription/owner/pre-state history revoked, same producer run history-start policy explicit |
| 18 | Stop/restart actual producer trong owned test harness | USE remains alive; new producer UUID/generation, no old latch/binding/operation/norm state; old delayed events rejected |
| 19 | Export/replay accepted run | Same program/engine/profile/version/input boundaries → same native state/OCL/core records/quality, not just final status |
| 20 | Repeat from packaged distribution | No IDE/test classpath dependency; license/provenance/hash/classloader checks and no duplicate engine/host |

Call witness phải đến adapter thực gọi original compiled core hoặc test instrumentation quan sát call stack/code source; không fake counter/log label. Live runtime witnesses phải là actual Jason/CArtAgO/MOISE observations; synthetic data dùng riêng unit/negative tests. Engine Java chạy trong USE JVM; **producer JaCaMo riêng JVM/PID và TCP là acceptance đúng**.

## 14. Risk / limitation register

| Risk | Evidence đã biết | Mức / hướng xử lý | Phase/gate |
|---|---|---|---|
| CSLv4 vs legacy state | CslModel guard, CslSystemState play-chain/exact-type | Cao; canonical real M1 + native v4 backend; không bỏ guard | 0,4–5,H |
| Csl2UseTranslator legacy | `translate()` requireLegacyRuntime; role encoding khác, không emit đủ user invariants | Cao; DO_NOT_REUSE v4 production; native invariant tests | 5,8,H/I |
| CslSystemState/CslStateEvaluationSession | Legacy M0/file append/private hard-wired subset, không attach MSystem API | Cao; reference-only; new neutral session adapter core reuse | 7,14 |
| iStar grammar/model gaps | activation/description/condLang/top-level association/forall/pick missing builder/model support | Cao; parse-tree admission; extension profile riêng nếu cần, không scope creep | 0,6,D |
| NONE different local monitor | Original false PENDING, local false VIOLATED | Cao; explicit semantics profile/expected differences | 7,18–19,E/T |
| Historical Task vs invocation | preSeen/postSeen latch không exact invocation, cannot VIOLATED | Cao; preserve original history, keep NativeOperationCheckpoints result separate | 7,12,15,F/J |
| Missing/undefined Boolean hóa | Reference evalBoolean false-on-error/subset behaviors | Cao; native Value + quality gate, defer unknown tuples | 8,13–15,I/P/Q |
| USE7.1.1→7.5 compatibility | Some host API files differ; source equality not full binary proof | Trung bình/cao; recompile source, linkage/package tests, host provided | 3,8,21,U |
| Loader/FQCN ownership | Host static URLClassLoader generations, parent delegation | Cao; one engine artifact, exact host class identity; no duplicate goal plugin | 3,21,U |
| Duplicate engine copies | Full goal JAR + shaded selected source collision | Cao; inventory actual archives/startup guard; current goal JAR has 0 host USE classes, không claim ngược | 2–3,21 |
| Dependency/context API private | Translator private topology stack/dependency saturation | Trung bình/cao; bounded P-CTX-01 patch + original parity, no reflection/new recurrence | 1,7,G |
| Propagation cap/status domain | Private cap 10.000; Task VIOLATED invalid | Cao; admitted graph bound/status guard, no silent convergence claim; instrumentation patch only if later versioned | 7,G |
| State/history lifecycle | Native state/engine leaf/topology/quality/pre-state may reset khác scope | Cao; owner key/savepoint/explicit restart/resync policy | 14–16,S |
| Transaction/persistence boundaries | finish outside mutation catch; journal can overflow after commit | Cao; staged history/commit-degraded/quality policy, fault injection | 15,Q/T |
| Native before/after | Snapshot copies object/link state; derived views and interference need test | Cao; pinned states/typed bindings/operation interval witnesses, bounded retention | 8,12,15,J |
| EDT block/deadlock | Current full verification on EDT, read invokeAndWait | Cao; background static/control/I/O; ordered pinned-state lane/publication fence; no lock cycle | 14–17/UI tests |
| Canonical duplicate semantic layer | CslSchema/RequirementModel/TemporalGoalMonitor existing callers | Cao; new profile core authority, old compatibility retained, retirement after gates | 9,19,22 |
| Replay compatibility | Old profiles/local recurrence and schema version rejection | Cao; explicit new program/engine format/version, old readers/no reinterpretation | 18,T |
| TCP disconnect/resync/restart | Subscribe-before-cut races, source-local clock, new producer generation | Cao; keep current admission/watermark/fence; new generation reset, gap doesn't heal history | 13–16,Q/R/S |
| Rich types/default/abstract roles | Local codec4 scalars; core role always concrete, opaque types undeclared runtime semantics | Cao; typed explicit policy/abstraction; no default/String/identity invention | 0,5,10,13 |
| Source/M2/grammar mismatch | Ecore annotation vs actual Java features; stale example/JAR | Trung bình/cao; pin files/hash, crosswalk, fresh generated parser/test oracle | 1,4,6,A–D |
| Optional solver/BPMN scope | Symbolic NONE/Task differs observed recurrence; solver build extra dependencies | Trung bình; offline optional/reference, no live primary semantics | 1–2,18–19 |
| Unfinished final mapping | Domain/goal/OCL abstractions not universally inferable | Cao; profile rules/config authored and versioned; unsupported/loss visible | 9–12,K/L |

Nhận định lịch sử lúc planning: chưa có evidence migration build/pass/live. Lượt implementation hiện tại đã bổ sung clean/full/live/package/parity/cleanup evidence trong Phase22; full formal language conformance ngoài admitted capability vẫn không được claim. Ưu tiên supported complete vertical slice và core preservation; full grammar/opaque datatype/solver extensions ngoài capability ban đầu không được quảng bá đã support.

## 15. Final Definition of Done và bàn giao

**17/17 tiêu chí DONE** cho admitted observed profile, đối chiếu lần lượt **D01–D17** trong `use-plugin/verification/semantic-migration/phase-22/acceptance.json`. Các XML reports/logs/receipts/recordings/hash audits là executable evidence; checkbox không thay thế chúng. Default cũ được giữ, new profile opt-in, không xóa compatibility source/readers.

- [x] Selected use4Goal CSL core **thật**, original FQCN/provenance/hash/license/patch inventory đã integrated/build against USE7.5.
- [x] Real `org.vnu.sme.goal.dsl.csl.mm.CslModel` là canonical CSL M1 của new profile; original validator/factory/parser APIs được gọi theo actual path.
- [x] Real `org.vnu.sme.goal.dsl.istar.mm.GoalModel` là canonical iStar M1; syntax-only gaps explicit admission/UNSUPPORTED.
- [x] Original GoalMarking/TaskMarking/IStarMarking/IStarPropagation/contextual dependency algorithm dùng thật; raw semantics/parity và intentional differences có evidence.
- [x] Native USE7.5 OCLCompiler/Evaluator là production backend, Boolean/type gates/typed bindings/native @pre/before-after/undefined/error/coverage/cache ownership pass.
- [x] JaCaMo mapping nằm ngoài engine; V1→V2 đổi rule/profile/program mà original core source hashes không đổi.
- [x] External JaCaMo process → authenticated TCP/Bridge → USE process hoạt động thật; no embedded prerequisite; engine itself không thêm process/socket/transport.
- [x] Một authoritative active Session MSystem, same model/system/cut cho projector/native OCL/engine/UI/manual OCL; no parallel MSystem for original file-based engine workflow.
- [x] Two-agent/Counter/GroupBoard/roles static+runtime vertical slice mục13 pass, supported Scheme/Mission/NPL scope đúng evidence.
- [x] Test matrix A–V, unit/integration/negative/parity/regression pass; required live/package tests thực thi, không skip hoặc thay bằng mocks.
- [x] Gap/resync/partial/missing/stale/revision/generation/incarnation/invocation correlation behavior đúng, no false/complete coercion.
- [x] Monitor Stop/Start/Reimport và producer restart cleanup no stale history/thread/listener/subscription/pre-state; `.stopMAS`/producer exit không đóng USE.
- [x] UI responsive/cancellable, no EDT/network/worker lock cycle; finite-workload cleanup và owner/future revocation đã test; native arbitrary force-cancel UNSUPPORTED. Production quota/capacity behavior explicit and tested.
- [x] Replay accepted semantics native/engine/quality/hash đúng; historical profiles/readers không reinterpret/overwrite.
- [x] Fresh packaged USE7.5 distribution live/manual OCL/replay pass; one engine/host identity, no test classpath, exact checksum/provenance/license.
- [x] Old semantic implementations chỉ retire khỏi new authority sau parity/version/compatibility gates; source/readers cần old recordings vẫn được bảo toàn. Không xóa source chỉ để gọi migration hoàn tất.
- [x] User run/build/verify/replay instructions đúng actual `use` distribution và supported profiles, không lẫn use4Goal7.1.1/reference run paths.

Final required gates đã đạt, không còn blocker. Bàn giao runnable archive `use-assembly/target/use-7.5.0-use-bin.zip` (SHA-256 sidecar), plugin ZIP `use-plugin/target/use-jacamo-plugin-1.0.1-v2-frozen.zip`, hướng dẫn `use-plugin/docs/project/use4goal-run.md` và exact artifact hashes tại Phase22. Không commit/push theo yêu cầu. Capability limits trong profile/risk register vẫn được giữ; DONE không có nghĩa hỗ trợ mọi grammar/JaCaMo abstraction hoặc scheduling/liveness equivalence.

## 16. Final check của lượt lập kế hoạch gốc

Đây là xác nhận phạm vi tài liệu, không đánh dấu các implementation phase đã hoàn thành:

- Đã đọc toàn bộ Desktop report và đối chiếu current POM/source/compiler/models/recurrence/transaction/loader/bridge/test harness/build docs; source có ưu tiên khi khác plan cũ.
- Đã xóa chính xác root `use/implementation-plan.md` cũ trước khi tạo file này; root `use/report.md` không tồn tại nên không có file cần xóa. Desktop report giữ nguyên.
- Không sửa Java/POM/source/test/reference; không copy engine, build, run tests hay migration trong lượt planning.
- Tài liệu chọn external TCP hợp lệ; embedded same-JVM không là prerequisite. Mapping ở ngoài original engine, one authoritative MSystem.
- Có 23 phases 0–22, mỗi phase đủ template/checklist/tests/AC/risk/rollback/status TODO; có change matrix, source manifest/closure/hashes, dependency graph, runtime/lifecycle ownership, test matrix, live acceptance và DoD.
- File đầu ra duy nhất được tạo: **`D:/_CODE_BANK/Project_/08_Thesis/use/implementation-plan.md`**. Không tạo report/implementation-report/temp planning files.

Lượt lập kế hoạch đã dừng tại tài liệu này. Lượt coding hiện tại tiếp tục tuần tự Phase 0–22 theo yêu cầu mới; trạng thái thực tế xem execution checkpoint ở đầu file và evidence từng phase.
