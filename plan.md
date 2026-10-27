# Plan — use4Goal UI + JaCaMo selective runtime verification

Cập nhật: 2026-10-27. **Đây là scope implementation hiện hành**, thay toàn bộ task P00–P10 và phần G00–G13 lịch sử. Đọc `use-plugin/docs/agent/agent.md`, banner `task.md`, rồi file này; khi scope cũ mâu thuẫn thì theo yêu cầu mới trong file này. Không thực hiện các task cũ chỉ để đóng checklist.

## 1. Quyết định kiến trúc

**Implement trong repository `use`; tái sử dụng UI/workflow và semantic logic use4Goal qua adapter. JaCaMo là external execution source, chỉ cung cấp dữ liệu cần cho CSL/requirements.**

- Chỉ sửa `D:/_CODE_BANK/Project_/08_Thesis/use`, branch hiện tại. Không branch/worktree/reset/git clean/commit/push.
- `../use4Goal`, `../jacamo`, Desktop: READ ONLY. Được migrate selected source vào `use` với hash/license/patch provenance; không sửa upstream.
- Input production: `.jcm` + authored `.csl` + authored `.istar` + case mapping `.xml` + authored `.ocl`. `.jcm` trỏ tới ASL/MOISE/Artifact cần dùng. `.drawio` chỉ reference; không là runtime input.
- CSL/iStar authored là canonical model. XML khai báo correspondence, selectors, identity, activation, operation/Task/Goal bindings, conversions và optional observer requirements. Không Java mapper riêng cho mỗi case.
- Giữ native USE 7.5 OCL, original CSL/iStar models, `GoalMarking`, `TaskMarking`, `IStarPropagation`; không viết engine/OCL evaluator khác.
- **Một authoritative Session/MModel/MSystem/MSystemState live.** History/before-state và UI DTO là immutable views của accepted state, không là runtime thứ hai.

```text
GUI/workflow được tái sử dụng từ use4Goal
  └─ chọn JCM + CSL + iStar + XML + OCL
                 ↓ validate + resolve bindings
          requirement-driven observation plan
                 ↓ capability negotiation
JaCaMo external process → existing TCP Bridge
                 ↓
selected initial state + relevant deltas + required execution events
                 ↓ admission / identity / sequence / coverage
existing XML mapping + native projector → same USE state
                 ↓
native OCL → original GoalMarking / TaskMarking / propagation
                 ↓
use4Goal UI: state timeline • Goal/Task status • evidence
```

Đây là **runtime verification của observed execution/prefix**, không phải chứng minh mọi execution có thể. Không chạy BPMN simulator hoặc symbolic whole-process solver để thay JaCaMo.

## 2. Current implementation và phần phải thay

| Thành phần | Source đã đối chiếu | Quyết định |
|---|---|---|
| use4Goal UI | `../use4Goal/goal/src/main/java/org/vnu/sme/goal/feature/cslstate/CslStateEvaluatorForm.java`, `ConformanceReportBuilder.java`; `dsl/csl/view`, `dsl/istar/view` | Reuse actual source/workflow; thay input/backend binding cho JaCaMo, không chỉ bắt chước giao diện |
| use4Goal state session | `verify/cslstate/CslStateEvaluationSession.java`: dùng `CslSystemState`; `loadBpmn`, `evaluateBpmnTrace`, `validateWholeBpmnProcess` | Không dùng nguyên session này làm live owner; tách reusable UI/result presentation khỏi BPMN dependency |
| use4Goal OCL subset | `trace/cslTrace/nativecsl/NativeOclEvaluator.java` có parser/evaluator subset riêng | REFERENCE_ONLY trong production mới; không thay native USE OCL bằng subset |
| Original engine đã integrate | `use-jacamo-semantic-engine/.../runtime/Use4GoalRuntimeSessionAdapter.java` gọi marking/propagation gốc | KEEP; không copy thêm engine hoặc tạo FQCN ownership thứ hai |
| Authored/static preparation | `MappingInputs`, `GenericMappingEngine`, `GenericBindingPlan`, `XmlBindingPreflight`, `XmlNativePreparation` | KEEP/ADAPT cho selective observation; không generate đè CSL/iStar |
| Producer plan hiện có | `ObservationRequestPlan`; `RuntimeFactSources.producerFields/producerCartagoKinds`; `CapabilityRequirements` | EXTEND theo dependency closure. Hiện chưa chứng minh minimal/selective end-to-end; lọc consumer không đủ |
| Bridge/native runtime | `BridgeClient`, `SnapshotCoordinator`, `NativeRuntimeProjector`, `XmlRuntimeStateMapping`, `RuntimeEngineSessionOwner`, `XmlTaskCheckpoints` | KEEP/ADAPT; không rewrite TCP, transaction/checkpoint hoặc recurrence |
| Workbench hiện tại | `JaCaMoWorkbenchPanel`, `DefaultJaCaMoFacade`, `OwnedJaCaMoRuntime` | Reuse services/lifecycle; nhường main workflow cho UI use4Goal, giữ compatibility entry point |

Baseline có evidence: `use-plugin/verification/authored-models/P10-source-confirmed-reactor.json` ghi 1372 unit + 173 integration tests PASS; legacy 198 và acceptance tools 15 PASS. `P10-source-confirmed-full-archive.log` ghi 3 full-ZIP cold live/exact-replay cases PASS. Đây là baseline kiến trúc cũ, **không chứng minh selective producer hoặc full use4Goal UI mới đã hoàn tất**.

GUI cũ có run bị `CARTAGO_BOUNDARY_CUT_NOT_STABLE`, được giữ trong `P10-final-main-gui/first-run-not-accepted.json`; tool báo GAP/SKIPPED đúng. Restart sau đó đã quan sát đủ 5 Task posts, nhưng chuỗi final GUI Pause/Resume/Stop chưa đóng acceptance. Giữ evidence; không carry checkbox DONE sang scope mới.

## 3. Thu thập tối thiểu nhưng đủ semantics

Observation plan là dependency closure của: CSL runtime scope người dùng chọn + OCL/Goal/Task AST reads + XML bindings + dữ liệu cần chứng minh identity/completeness. UI hiển thị thêm trường nào phải đăng ký field đó vào plan; không âm thầm kéo full runtime.

| Dữ liệu | Chính sách |
|---|---|
| Initial state | Snapshot **trong selected CSL scope**: Entity/Role/OrgCtx, values và links cần thiết; không snapshot toàn MAS |
| State changes | Create/update/remove/link/unlink của selected objects/fields; giữ artifact incarnation, actor/player/context đúng |
| Population | `allInstances`, count, navigation/role cardinality cần đầy đủ population tương ứng hoặc báo INCOMPLETE; không coi tập được sample là COMPLETE |
| Task execution | Chỉ mapped operations/steps. Giữ invocation, performer, args/result nếu có, success/failure và before/after cần cho contract |
| Beliefs | Không full belief base mặc định. Chỉ predicate/attribute được binding hoặc UI read explicitly; belief không là world truth |
| MOISE/NPL/time | Bật provider khi requirement đọc đúng concept đó. Không bắt mọi case có scheme history, NPL, clock hoặc exact step instrumentation |
| Trace metadata | Luôn giữ runId/generation/revision/plan hash, identity/incarnation, ordering/watermark và quality cần admission/replay |

**Không đồng nhất state delta với Task event.** Bid thành công nhưng không cải thiện giá vẫn phải được ghi nếu Task cần; failed bid không có delta vẫn cần outcome. Không drop A→B→A hoặc intermediate state cần MAINTAIN/SUSTAIN. Chỉ deduplicate/coalesce khi chứng minh verification semantics không thay đổi; mặc định giữ thứ tự relevant boundaries.

Không suy arbitrary requirement từ JCM hoặc tên field. Dynamic/unbounded reads phải mở rộng declared observation scope có kiểm chứng, hoặc fail closed. Unsupported/missing/PARTIAL/GAP/ERROR không thành false/FAIL. Temporal contracts strict và source-confirmed có version riêng; không đổi oracle để giảm dữ liệu cần thu.

## 4. Loại khỏi critical path

- Full JaCaMo model/runtime mirror; gửi mọi belief/intention/plan/cycle và mọi Artifact property theo mặc định.
- Theo dõi chính xác mọi Jason step/MOISE internal truth-change timestamp dù requirements không sử dụng.
- Mandatory NPL/deadline/history observers cho mọi case; mở rộng generic framework ngoài những instruction/capability case inputs thực sự cần.
- Embedded same-JVM, viết TCP khác, viết CSL/iStar engine hoặc OCL evaluator mới.
- Bắt UI JaCaMo giả lập BPMN bằng synthetic BPMN graph; whole-process/all-path proof, solver/boundary/BPMN tooling là ngoài scope mới.
- Tiếp tục mở rộng custom Workbench riêng thay vì reuse use4Goal workflow.
- Chạy full reactor/live/package sau mỗi task hoặc lặp lại chỉ để hoàn thành checklist cũ.

Loại task khỏi plan **không có nghĩa xóa source/profile/test cũ**. Compatibility giữ nguyên cho tới khi replacement có tests; không migrate/reset dữ liệu người dùng.

## 5. Task theo dependency order

Mọi tên class mới dưới đây là **API nội bộ dự kiến**, không phải API framework đã tồn tại. Inspect source liên quan trước khi thêm; reuse seam hiện có nếu đáp ứng. Mỗi task: focused baseline → implement nhỏ nhất → focused/nearby tests → evidence rồi tick. Không nhảy mandatory gate đang fail.

- [x] **S01 — Tách workflow use4Goal khỏi execution/backend BPMN**
  - Goal: xác định và migrate đúng closure UI cần reuse, không copy full plugin.
  - Files: reference `feature/cslstate/{CslStateEvaluatorForm,ConformanceReportBuilder}`, `feature/opencsl`, `feature/openistar`, `dsl/*/view`; existing `use-plugin/.../ui/use4goal` và view provenance manifest.
  - Change: chọn reusable panels/controllers/result types; adapter/presenter ở `use-plugin`, model/marking ở semantic-engine. Tách BPMN evaluator, symbolic report và `CslStateEvaluationSession` live ownership khỏi JaCaMo mode. Giữ source hash/license/patch inventory, tránh duplicate FQCN/dependency cycle.
  - Test: UI dependency/ownership checks; original views/results nhận model và read-only data từ existing session, không instantiate second engine/runtime.
  - AC: có working UI/backend seam kiểm chứng được, không dependency BPMN/solver trong JaCaMo path; actual reuse có provenance. Không yêu cầu rewrite toàn bộ UI backend trước khi có seam.
  - Dependency: none. Status: VERIFIED — extracted `ui/use4goal/conformance/ObservedConformancePanel` giữ actual `buildConformanceAolPanel` layout/navigation; presenter chỉ đọc immutable facade evidence, không compile AOL/BPMN hoặc tạo state/engine. Existing Workbench nối tab này vào cùng facade/model; capture ở worker, update trên EDT với epoch/model guard. Đây là UI/backend seam của S01, chưa phải main workflow acceptance S05.
  - Evidence: `use-plugin/verification/selective-runtime/S01-baseline.log` tái hiện seam chưa tồn tại; `S01-regression.log` BUILD SUCCESS, 26 tests (3 workflow/provenance + 3 reused views + 20 Workbench), 0 failure/error/skip. `META-INF/use4goal-workflow-provenance.json` pin upstream bytes/commit/license, target hash và patch inventory. Tests xác minh selected layout từ upstream, navigation không reevaluate history, GAP overlay giữ raw marking nhưng qualified UNKNOWN, player/context và detach/reimport; original view ownership regression pass.

- [x] **S02 — Biên dịch selected observation plan từ năm inputs**
  - Goal: quyết định được cần đọc gì và vì sao, trước khi chạy producer.
  - Files: `MappingInputs`, `GenericBindingPlan`, `XmlBindingPreflight`, native OCL dependency visitor, `CapabilityRequirements`, `ObservationRequestPlan`.
  - Change: mở rộng plan hiện có với selected types/instances/populations, fields/links, events/contracts, identity closure và optional capability requests. Native AST + XML quyết định read set, không regex/fuzzy matching. Freeze version/hash vào program/recording; GUI có bảng «requirement → source → field/event → capability».
  - Test: hai vocabulary khác nhau; remove requirement làm read set giảm; thêm OCL navigation/allInstances làm closure tăng đúng; unsupported selector/unmapped read/missing capability fail closed. Counter không bị yêu cầu Auction/NPL/step providers.
  - AC: một plan có version, deterministic, xác minh đủ dependencies; đủ dữ liệu cho selected scope nhưng không mặc định full source. Hash đổi thì reprepare/resync, không mutate plan giữa run.
  - Dependency: S01. Status: VERIFIED (compiled plan/handoff; producer enforcement là gate S03). `ObservationReadSet` dùng native AST cho attributes/@pre/navigation/populations; `XmlBindingPreflight` gồm CSL inline invariants và implicit invariant context population. `SelectedObservationPlanner` nối XML input/table/join/dataset với source/provider, compile conservative capture bounds. `ObservationCaptureScope/1.1` + handoff `ObservationRequestPlan/1.2` pin scope/read-set hashes và source epoch; overload 1.1 giữ compatibility. Facade publish scope từ accepted native preparation. Helpers được pin vào replay implementation manifest. GUI có read-only Observation plan preview; declaration không bị gọi là observed COMPLETE.
  - Evidence: `use-plugin/verification/selective-runtime/S02-closure.log` BUILD SUCCESS: 52 plugin tests + 3 contract tests, 0 failure/error/skip; `S02-nearby-regression.log` thêm original views/coverage/session regressions. Tests gồm authored Auction + Stock actual five-input static/native preparation, native navigation/allInstances/@pre/tuple, CSL inline invariants, removing/renaming reads đổi scope/hash, property/class scope union, broad dataset không bị narrow sai, source lineage, invalid XML/ownership/missing coverage, handoff tamper/version downgrade. `S02-baseline.log` tái hiện read-set seam chưa tồn tại.
  - Giới hạn có chủ ý: dataset PROPERTY toàn cục cần inventory mọi Artifact class; planner chỉ lọc property names khi mọi direct table consumer có positive exact predicate. Đây là scope khai báo rộng, không chứng minh minimal inventory. S03 phải ghi nhận enumeration limit và chứng minh không capture/publish unselected payload. Không dùng kết quả static này để claim producer selective hoặc live/package acceptance.

- [x] **S03 — Producer chỉ quan sát scope đã negotiate**
  - Goal: giảm capture/publication tại nguồn, không chỉ drop fields sau khi gửi TCP.
  - Files: `jacamo-bridge-contract` observation plan/receipts; `jacamo-bridge-jacamo/.../adapter/{FrameworkObservationProviders,OfficialCartagoAdapter,CartagoSnapshotSource,JasonSnapshotSource,MoiseBoardSnapshotSource,SnapshotCoordinator}` và neutral providers được request.
  - Change: enforce plan trên initial/resync/callback capture. Reuse stable identity crosswalk; bounded reads chỉ cho selected scope. Nếu official API bắt buộc enumerate toàn bộ inventory để lọc, ghi giới hạn đó nhưng không capture payload/state không cần. Snapshot chỉ initial/resync hoặc required contract boundaries; không quét full MAS ở mỗi event. Không có listeners/providers không được yêu cầu.
  - Test: spy/counter tại capture seam chứng minh field/artifact/provider không liên quan không bị đọc/publish; state update, creation/removal/recreation, role enactment; no-change success/failed operation vẫn được gửi khi cần; coherent cut retry/gap negatives.
  - AC: unselected property changes/idle cycles không tạo domain checkpoints; required events không mất. Coherence chỉ yêu cầu các nguồn plan phụ thuộc; missing required source giữ GAP/INCOMPLETE. Có đo captured fields, callbacks, wire bytes, checkpoints theo cùng workload/baseline, không đặt con số giảm tùy tiện.
  - Dependency: S02. Status: VERIFIED — capture/source SPI gate. Production activation installs frozen capture scope, selected MOISE kinds and optional NPL selection before agent execution. Before negotiation, CArtAgO emits metadata-only/PARTIAL bootstrap, without property values/operation payloads/board roster probes; MOISE/NPL wait for the plan. Requested publication/operation boundaries retain their evidence; no-change and failed invocations remain observable. Initial/resync/stable-boundary capture applies class/property predicates before payload acquisition; retired source cannot re-register loggers.
  - Evidence: `use-plugin/verification/selective-runtime/S03-acceptance.log` BUILD SUCCESS, 27 adapter + 3 contract tests, 0 failure/error/skip. Real CArtAgO fixture verifies ArtifactInfo reader not called for unselected classes; opaque unselected values not inspected/serialized; noise changes publish no events; no-change success/failed operation preserved; 0→1→0 publications retained; dispose/recreation changes UUID; frozen scope; stable local cut/receipt; optional NPL reads/subscriptions = 0; unselected MOISE suppliers = 0; 1000 idle Jason cycles do not republish stored content; topology/watermark retries and gap regressions pass. `S03-source-selection.log` also verifies authored Auction/Stock static preparation against scope 1.1, including NPL/MOISE requests.
  - Measurement (same live CArtAgO workload, capture SPI, not external-MAS throughput): selected 12 facts / 31,941 encoded fact bytes / 37,974 framed snapshot bytes; full 357 / 980,181 / 1,119,530. Framed size uses production ContractCodec + existing TCP int32 prefix; actual external TCP/end-to-end receipts remain S07. No percentage target or synthetic verdict equivalence claimed.
  - Root-cause regression: `S03-retired-logger-reproduction.log` shows old full-capture logger still serializing ignored properties after close without attach; cleanup now always unregisters actual logger owners and rejects reopening. `S03-baseline.log` records the missing source-scope seam.
  - Limit: official controller inventory enumeration remains necessary; getArtifactInfo may create framework property snapshots internally. The adapter avoids unselected ArtifactInfo calls when class scope is bounded, and never converts unselected values into payload. Broad PROPERTY datasets/NPL polymorphic discovery intentionally retain wider identity inventory. Consumer admission/equivalence, full distribution and GUI acceptance are separate S04–S07 gates.

- [x] **S04 — Project deltas và đánh giá goal trên cùng authoritative state**
  - Goal: JaCaMo thay execution source BPMN; semantics verification vẫn qua existing USE/use4Goal engine.
  - Files: `BridgeClient`, `NativeRuntimeProjector`, `XmlRuntimeStateMapping`, `XmlTaskCheckpoints`, `ConstraintCoverageResolver`, `RuntimeEngineSessionOwner`, `Use4GoalRuntimeSessionAdapter`.
  - Change: initial selected snapshot + ordered deltas cập nhật một MSystem trong existing transaction/savepoint; evaluate native OCL ở relevant boundaries và đưa qualified inputs vào original markings/propagation. Reuse invocation checkpoints/@pre. UI history đọc accepted cuts; typed immutable views không giữ live state thứ hai.
  - Test: before/after/@pre và no-change Task; exact actor/context isolation; ACHIEVE/MAINTAIN/SUSTAIN, activation reset, AND/OR/dependency; partial/stale/gap/rollback; omitted property khác unknown property.
  - AC: mọi verdict trace được về cut + source evidence + requirement; history đúng theo original semantics, không biến terminal JaCaMo goal thành satisfied iStar goal. Đối chiếu reduced trace với relevant projection của full baseline trên cùng recorded execution; không yêu cầu wire/source hashes giống nhau giữa hai modes.
  - Dependency: S03. Status: VERIFIED — native admission/semantic component gate. `NativeCaptureScopeGuard` validates captured scope/hash against accepted native preparation before writes, pins selective epoch and rejects mismatch/downgrade; projector rollback keeps the same MSystem/object/value and requires resync. Immutable historical state images are borrowed from existing SnapshotRetention via facade, matched by exact result hash/version; no historical row is paired with a newer live image. Original engine/operation/@pre implementations are retained.
  - Evidence: `use-plugin/verification/selective-runtime/S04-baseline.log` reproduces acceptance of a wrong-scope empty snapshot. `S04-acceptance.log` BUILD SUCCESS, 51 plugin tests + 17 original engine tests + contract/adapter regressions, 0 failure/error/skip. Same recorded execution full vs selected projection gives native invariant aggregate AND body `PASS→FAIL→PASS`; scoped delta accepted, wrong-scope delta/snapshot rejected without value corruption, legacy downgrade rejected, proper resync accepted. Existing exact performer/activation/demand/@pre, history fault rollback, goal type/AND/OR/episode, stale/gap/restart and portable legacy XML replay tests pass. External TCP/packaged/source-correlated Goal live acceptance remains S07, not inferred from component evidence.

- [x] **S05 — Main workflow bằng giao diện use4Goal**
  - Goal: người dùng thao tác trong reused use4Goal verification UI, không phải tự vận hành debug Workbench.
  - Files: closure S01, `JaCaMoWorkbenchAction`, `JaCaMoWorkbenchPanel`, `DefaultJaCaMoFacade`, existing CSL/iStar views và UI tests.
  - Change: entry từ main USE mở reused workflow với năm file inputs, validation/read-set preview, Prepare/Connect/Start, state timeline và Goal/Task/OCL evidence. JaCaMo mode không yêu cầu BPMN/boundary/AOL input. Thay các control BPMN ở mode này bằng runtime source controls; giữ dialogs/views phù hợp. Timeline có state change và execution-only boundary riêng. Status «Observed prefix», coverage, raw/propagated status, failed checkpoint/reason rõ ràng. Old Workbench giữ compatibility/advanced entry; không thêm dashboard/engine thứ hai.
  - Test: file choosers thật, model/view identity, EDT responsiveness, live state navigation, correct failure/UNKNOWN display, repeated import/dispose/listener cleanup.
  - AC: mở GUI chính → chọn inputs → thấy actual reused CSL/iStar/state/result UI → chạy JaCaMo → theo dõi goal; không cần CLI để điều khiển. Native/source/core details nằm ở evidence/advanced panel.
  - Dependency: S01 + S04. Status: VERIFIED — actual NEW main workflow acceptance PASS trên final ZIP, không dùng legacy Workbench thay thế.
  - Evidence: `use-plugin/verification/selective-runtime/S07-main-workflow-final/receipt.json` và screenshots `01`–`19`: actual installed MainWindow → Plugins / JaCaMo / **use4Goal verification...** → năm file choosers thật (JCM/CSL/iStar/XML/OCL) → Prepare/Start. CSL/iStar views, state navigation Previous/Next, native OCL PASS/FAIL, original raw FULFILLED và qualified UNKNOWN được quan sát trực tiếp. Pause/Resume, Stop/Restart và repeated Prepare/Import đều qua GUI. Không inject inputs/lifecycle qua reflection hoặc test facade; launcher chỉ đặt cửa sổ màn trái, sampler chỉ đọc ownership/cleanup.
  - Lifecycle evidence: ba producer PIDs 28500/13944/30236 và ba runId/MSystem identities khác nhau, USE PID 23176; cùng một live MSystem trong từng run. Restart/reimport trở về baseline stateVersion 2, original contexts/Task POST rỗng. Đóng dialog khi run 3 còn LIVE dừng producer, timer, subscription/engine/control/output threads; Session listeners 1→2→1, mở lại owner mới 2 rồi dispose trở về 1. Cả hai panel cũ detach CSL/iStar và dừng timer. Native USE OCL vẫn evaluate sau dispose. Tất cả sampled shown windows ở màn trái.
  - Semantic scope: GUI run đầu quan sát 4 qualified Task POST targets và actual violation/UNKNOWN; không gọi mọi Goal/Task là satisfied. Full required Task coverage thuộc cold Auction gate đã PASS, không suy từ ảnh GUI. Các IT `Use4GoalWorkbenchIT`/`ManagedAuctionWorkbenchIT` là legacy regression bổ sung, KHÔNG là evidence chính cho workflow mới.

- [x] **S06 — Lifecycle, resync và portable selective replay/package**
  - Goal: không carry state/history sai qua reconnect/restart; phân phối chạy độc lập nguồn Desktop.
  - Files: `OwnedJaCaMoRuntime`, `DefaultJaCaMoFacade`, existing replay/manifest/engine checkpoints, release manifest/assembly và installed tests.
  - Change: reuse owned/unowned process separation, optional capability-gated Pause/Resume, Stop/Restart/Reimport. Resync theo cùng frozen plan; restart/reset engine/bindings/checkpoints theo epoch. Recording lưu read scope/quality/events cần tái hiện, không full source payload bắt buộc; old dialects fail closed theo version/hash policy hiện có. Package selected UI closure + five inputs cho Auction/Case B.
  - Test: old-mode regression, stale generation, plan mismatch, gap/resync không hồi sinh missing history, Stop→Start mới, input deletion replay, no duplicate classes, cold package; Stop producer giữ USE sống.
  - AC: exact state/result replay trong cùng selective dialect; owned child/listeners/state cũ không leak; unowned producer không bị kill. Pause chỉ Jason ACK boundary, không claim atomic pause toàn platform.
  - Dependency: S04 + S05. Status: VERIFIED — cold package, exact replay, and lifecycle gate PASS. Fresh official release archive built: `use-assembly/target/use-7.5.0-use-bin.zip` (117,204,505 bytes, SHA-256 `ff10b72811065d468f9689a80211ea326344c6bd940317b9082d756dce6c3ee5`) and `use-plugin-1.0.1.jar` (SHA-256 `120364ada04228555b95b9996ede0e1732f763b968e3cda24dcc0c68b34cdb50`). Cold exact replay verified after deletion of the four copied authored files (.csl/.istar/.xml/.ocl). The JCM and referenced source remain present; this cold receipt does not prove their deletion. The separate S06 component test removes all five copied inputs/source, but is not cold-package evidence.
  - Evidence: `InstalledAuthoredPluginIT` qualifies `use-7.5.0-use-bin.zip` with 3/3 PASS across `stock` (Case B, 200 replay entries), `auction` (2278 replay entries), and `auction-confirmed` (1892 replay entries). Receipts in `use-plugin/verification/authored-models/P09-cold-installed/` verify `status: PASS`, `sameMSystem: true`, `selectedInputsDeleted: true`. `Use4GoalPackagedPluginIT` qualifies `use-7.5.0-use-bin.zip` with 4/4 PASS (97.25s), confirming zero duplicate classes, single core pinned ANTLR 4.9.3, deployment guards rejecting missing/corrupted resources, and counter execution with pre/post assertions. `SemanticPackagedReplayIT` (3.5s), `Use4GoalPackagedReplayIT` (7.8s), `NativeStepReplayGuiIT` (2.5s), and `AuctionPauseResumeIT` (43.5s) all pass.

- [x] **S07 — Live acceptance và closeout**
  - Goal: chứng minh pipeline mới thực sự chạy; không dùng tests chỉ tăng coverage.
  - Files: reuse focused XML/operation/engine/UI tests; `InstalledAuthoredPluginIT`/driver; `ManagedAuctionWorkbenchIT`; `plan.md` và evidence của scope mới.
  - Change/test:
    - [x] Auction thật: năm inputs authored, hai Auction instances, role inheritance/player binding; create/start, improving bid, non-improving success, failed bid, stop và recreation. Đối chiếu selected state + relevant Task/goal evidence, không ép mọi requirement PASS.
    - [x] Case B vocabulary khác: chỉ đổi models/XML/OCL/JCM, không thêm Java mapper; kiểm tra goal condition TRUE→FALSE và MAINTAIN/SUSTAIN nếu model khai báo.
    - [x] Capability không request không active; missing required field/population/capability vẫn UNKNOWN/INCOMPLETE; stale/gap/resync và restart không false credit.
    - [x] Đo capture/wire/checkpoint cho cùng workload; chứng minh unselected data không đi qua production lane và relevant semantic verdicts không mất.
    - [x] Full regression một lần khi source ổn: `mvn -B clean verify`, core legacy JUnit3 và acceptance tools. Khi có failure: focused root-cause fix rồi chỉ rerun gate cần thiết; không sửa assertion semantic để xanh.
    - [x] Full ZIP cold Auction + Case B, replay sau input deletion và actual main-GUI inputs/timeline/results/Start/Stop/Restart; Pause/Resume khi supported. GUI automation chỉ màn trái theo yêu cầu người dùng, không động màn phải.
  - AC: mọi task required có executable evidence trên source/package tương ứng; source freeze/provenance/receipts cập nhật. Không dùng baseline 1372+173 để claim acceptance source mới. Nếu chỉ manual GUI bị giới hạn môi trường, ghi rõ bước/check chưa chạy; không tick test đó hoặc tuyên bố full acceptance.
  - Dependency: S02–S06. Status: VERIFIED — full clean verify/cold archive đã PASS và ba closeout gaps đã được xác minh bằng receipt mới. Full reactor `mvn -B clean verify` completed with 100% BUILD SUCCESS across all 8 modules (total time: 01:22 h, 0 failures, 0 errors, log preserved at `use-plugin/verification/selective-runtime/S07-full-clean-verify-final.log`). Cold archive qualification passes on final `use-7.5.0-use-bin.zip` with exact replay after source input deletion. Actual new main GUI acceptance ở `S07-main-workflow-final/receipt.json`; qualification-launcher host-close errors và correction được giữ/giải thích riêng, không gọi toàn bộ log là zero errors.
  - Evidence:
    1. Full clean verify: `use-plugin/verification/selective-runtime/S07-full-clean-verify-final.log` (8/8 reactor modules SUCCESS, 43 failsafe tests PASS in `use-plugin`).
    2. Cold ZIP qualification: `InstalledAuthoredPluginIT` on `use-7.5.0-use-bin.zip` (3/3 PASS: `stock`, `auction`, `auction-confirmed`), receipts in `use-plugin/verification/authored-models/P09-cold-installed/` with `status: PASS`, `sameMSystem: true`, `selectedInputsDeleted: true`.
    3. Final package gates: `Use4GoalPackagedPluginIT` on `use-7.5.0-use-bin.zip` (4/4 PASS).
    4. Case B focused gate: `OwnedSecondCaseIT` (PASS in 15.09s), verified `nativeWeights: ["-1", "0", "1", "2"]`, `Pallet.weight` updated by `Counter.set(-1)`.
    5. Live Auction: `AuctionPackagedPluginIT` (4/4 PASS in 1363s), `AuthoredAuctionOutcomeIT` (1/1 PASS in 93.79s), `AuctionPauseResumeIT` (1/1 PASS in 43.49s).
    6. Main GUI mới: `S07-main-workflow-final/receipt.json`, `probe-history.jsonl`, screenshots `01`–`19`, two disposed snapshots và thread dumps. `verify_evidence.py` PASS: 565 samples, 3 distinct run/model owners, 2 dispose cycles, source 3304 files khớp freeze v5. Legacy GUI ITs chỉ bổ sung regression.
    7. Source/package link: `S07-final-source-package.json` pin freeze v5/hash, final reactor log/hash, final ZIP/hash, three matching cold qualifications và new GUI receipt/hash. Chỉ bridge bootstrap khác freeze v4; production không đổi trong closeout này, nên không chạy lại full reactor.

## 6. Definition of Done

- [x] Actual use4Goal UI/workflow được reuse cho JaCaMo mode; không phải chỉ selected model views hoặc giao diện mới mô phỏng.
- [x] Năm inputs authored compile/bind đúng; thêm case không cần Java case-specific mapping.
- [x] Producer thực thi frozen observation plan: initial selected state, relevant deltas và required execution events; không full state/BB/cycle mặc định.
- [x] Một live USE state, native OCL, original marking/propagation; no-change Task, history/activation/quality đúng.
- [x] GUI hiển thị state timeline, Goal/Task/OCL verdict và evidence đúng observed prefix; không quảng bá all-path proof.
- [x] Auction + Case B live, negative/compatibility/replay/lifecycle/package/final regression và main-GUI gates có evidence.
- [x] `plan.md` phản ánh status thực; không commit/push hoặc sửa read-only repos.

Evidence cũ giữ tại `use-plugin/verification/authored-models/`; scope mới chỉ thêm receipts/logs thực sự cần. Không tạo report/plan song song. Chỉ tick task khi AC đạt. Legacy/component GUI results được phân biệt với actual new main-workflow receipt.

### S07 final closeout — three audited gaps

- [x] **Actual main GUI** — `verification/selective-runtime/S07-main-workflow-final/receipt.json` PASS. Main menu mới, năm real file choosers, Prepare/Start, CSL/iStar views, observed timeline, original/native evidence, Pause/Resume, Stop/Restart, repeated import/dispose và measured cleanup đều đã exercise trên final ZIP. Host launch probe không tự chọn input hoặc gọi runtime actions. Hai harness host-close failures (thiếu Shell rồi thiếu readline) được giữ trong `host.log`/`host-exit-smoke.log`; source/API xác nhận chúng thuộc launcher kiểm tra, sau workflow cleanup. Final harness khởi tạo ordinary Shell.run và `host-shell-close.log` exit 0 không exception. Không sửa production để xử lý harness.
- [x] **Replay wording** — final cold receipts Stock/Auction/Auction-confirmed chứng minh exact replay sau khi xóa BỐN copied authored files CSL/iStar/XML/OCL; JCM/ASL/MOISE/Artifact source vẫn tồn tại. `selectedInputsDeleted=true` trong historical receipts chỉ được diễn giải theo phạm vi đó. Test S06 component riêng xóa toàn bộ copied source/five inputs, không được gọi là cold evidence. Chọn thu hẹp wording; không sửa expected verdict hay chạy lại cold/full gate không cần thiết.
- [x] **Source freeze cuối** — `S07-source-freeze-v5.json` pin 3304 files sau fix `JaCaMoBridgePlatform.activateObservationPlan()` publish initial CArtAgO cut ngay sau observationsReady, không đợi selected Jason step. Source hiện tại khớp toàn bộ v5. `S07-final-source-package.json` liên kết log `S07-full-clean-verify-final.log` BUILD SUCCESS (1394 unit + 173 IT, 0 failure/error/skip), final ZIP SHA-256 `ff10b72811065d468f9689a80211ea326344c6bd940317b9082d756dce6c3ee5`, three cold receipts và actual GUI receipt. Bridge compiled class khớp class trong final ZIP. Không có production/test-semantic change mới; không chạy lại clean verify.

Kết thúc closeout: không còn Java process của các run kiểm tra; main/user windows ở màn phải không bị thao tác. Chỉ thay plan và qualification evidence/harness; không commit/push, không sửa read-only repos. DoD được tick với phạm vi observed execution và replay deletion chính xác ở trên; không có claim all-goals PASS, exact internal MOISE transition time hoặc complete-history recovery từ partial evidence.

Git delivery: sau closeout, user đã yêu cầu commit/push toàn bộ source/test/docs/evidence hợp lệ lên branch hiện tại. Journal lớn được version lossless `.gz` với hash/index/restore helper; raw files và build outputs giữ local. Yêu cầu xuất bản mới thay giới hạn không commit/push của giai đoạn implementation; không thay semantics hoặc acceptance.
