# TASK — Workbench UX + OCL Runtime Flow

## Goal và phạm vi

Đơn giản hóa Workbench, làm rõ việc load OCL, khởi động agent reasoning và
theo dõi verification của các state/checkpoint đã quan sát:

```text
Project | Mapping Inspector | Mapping Rules | Verification | Runtime
```

**Status: DONE — independently re-audited 2026-10-01; fresh reactor 19:48:26 +07.** Đây là task mở rộng UX/
startup/re-analysis trên native pipeline hiện tại, không phải viết lại backend
đã hoàn thành trong `task_runtime_verification.md`. Các checkbox bên dưới là
acceptance của thay đổi mới; chưa tick chỉ từ việc backend tương ứng đã tồn tại.

Đọc `agent.md`, `task_runtime_verification.md`,
`JACAMO-USE-CONCEPT-MAPPING-RULES.md` và `CODE-GROUNDED-NATIVE-README.md`.
Source/test hiện hành là authority nếu tài liệu lịch sử khác với code.

### Baseline trước triển khai — review 2026-10-01 16:39 +07

Phần này giữ lại trạng thái tại lúc review, không mô tả feature đã triển khai
ở các gate PASS bên dưới.

- `JaCaMoWorkbenchPanel` hiện có 6 tab: Project, Mapping Inspector, Diagnostics,
  Verification, Runtime, Binding; chưa có Mapping Rules hoặc Start Runtime.
- `CodeGroundedRuleCatalog.rules()` hiện có 105 rule, version `1.0.0`:
  J01–J11, A01–A22, C01–C20, M01–M43, X01–X09.
  Catalog là hợp đồng mapping, không phải bằng chứng mọi rule đã có live data.
- `DefaultJaCaMoFacade.importProject()` hiện synchronize với producer/Bridge
  đã chạy, áp snapshot và buffered events rồi activate Session. Import hiện
  không phải một offline parse đảm bảo JaCaMo chưa khởi động.
- `ExternalOclConstraintService` đã compile named invariants bằng USE
  `ASSLCompiler`, install/replace atomic trên current model và giữ registry.
  `RuntimeVerificationCoordinator` đã baseline/re-check/history/checkpoint.
- Verification đã đọc latest native result; Runtime hiện chỉ hiển thị text
  tổng hợp 12 observation gần nhất từ bounded history, chưa có bảng từng invariant
  hoặc result-change filter.
- `jacamo-bridge.ps1 -OclProfilePath` hiện chỉ hỗ trợ headless. Khi có profile,
  launcher chờ consumer ready tại official local `startAgs()` hook. GUI hiện
  không chờ user load OCL; chỉ thêm nút trong Swing sẽ không giải quyết race này.
- `NativeRuntimeReplay.replay(Path)` hiện kiểm chứng bundle/profile đã ghi:
  hashes, versions, outcomes và PROFILE entries phải khớp. Chưa có API re-analysis
  dùng profile mới để kiểm lại những state trước thời điểm load.

### Ranh giới bắt buộc

- Một active `MSystem` cho facade, Session, runtime mutation, OCL, view và export.
  `CODE_GROUNDED_NATIVE` không phụ thuộc ngầm vào V2.
- Không fuzzy mapping, không hard-code case study, không sửa frozen V2/Ecore/goldens.
- Dùng USE compiler/evaluator và production native mutation semantics hiện có.
- User invariant FAIL chỉ là observation: giữ faithful state, không rollback hoặc
  ngăn Start chỉ vì profile baseline FAIL. Invalid profile/unsafe transaction là
  trường hợp khác và phải có diagnostic.
- Chỉ claim `OBSERVED_SUPPORTED_PROJECTION_ONLY`, không “mọi JaCaMo state”.
  C08 vẫn `UNAVAILABLE_BY_API`; C09 là snapshot, không phải live ObsProperty.
- Bỏ tab không được làm mất diagnostics hoặc tự xoá backend binding/legacy.

## 1. Workbench tabs và Mapping Rules

- [x] Layout native có đúng 5 tab theo Goal, đúng thứ tự.
- [x] Bỏ Diagnostics/Binding khỏi top-level tabs; giữ diagnostic backend và
  explicit-binding API/compatibility nếu caller hiện tại còn dùng.
- [x] Giữ diagnostic code/severity/message/remediation truy cập được ở Project,
  Mapping Inspector detail hoặc Runtime/Verification theo đúng nguồn lỗi.
  Failed import phải thấy diagnostic ngay cả khi chưa có model.
- [x] Không auto-select ambiguous binding. Nếu bỏ một binding UI entry point đang
  còn caller, cung cấp entry point phụ phù hợp hoặc ghi rõ unsupported; không
  suy diễn rằng persist binding có nghĩa là native runtime đã sử dụng binding đó.

### Mapping Rules

Bảng chỉ có 2 cột:

```text
Rule | JaCaMo → USE
```

- [x] Lấy toàn bộ rule từ `CodeGroundedRuleCatalog.rules()`, không duy trì danh sách
  ID/source/target độc lập trong Swing hoặc parse Markdown làm runtime authority.
- [x] Cột Rule là exact ID; cột JaCaMo → USE mô tả đúng source concept/API và
  target kind/relation hiện có. Nhãn dễ đọc không thay đổi semantic identity.
- [x] Audit catalog metadata với native builders/maintained mapping contract trước
  khi hiển thị. Độ lệch đã thấy: source text X02 trong catalog còn nhắc ObsProperty,
  nhưng NativeUseModelBuilder/NativeUseStateBuilder dùng C09 ObservablePropertySnapshot.
  Chuẩn hóa metadata có evidence/tests, không suy thành hỗ trợ C08 hoặc thay mapping
  semantics để khớp một nhãn cũ.
- [x] Hiển thị catalog ngay khi chưa import, theo thứ tự catalog; kiểm đủ/không trùng
  ID theo catalog hiện hành, không hard-code “105” làm logic sản xuất.
- [x] Giữ đúng 2 cột nhưng thể hiện conditional/runtime-only/provenance/unavailable
  bằng nhãn ngắn trong ô hoặc tooltip/legend. Không làm C08 hoặc planned rule trông
  như đã implement/materialize.
- [x] Không đưa instance trace/case-study object vào Mapping Rules.
  Mapping Inspector tiếp tục hiển thị mapping/trace/evidence thực tế của project.

Gate: panel/catalog tests chứng minh tab order, source of truth, exact metadata,
unavailable rules, diagnostic accessibility và không mất explicit-binding policy.

Gate 1 PASS — 2026-10-01 17:09 +07: 21 tests (catalog 4, panel 17),
0 failures/errors/skips. `MappingRulesPanel` derives all rows from catalog;
Project retains diagnostics and explicit binding compatibility. Metadata audit:
X02=C09 snapshot, C07=IArtifactGuard, C11=Tuple, M15–17=moise.os.Cardinality;
C08 remains UNKNOWN/UNAVAILABLE. No native mapping semantics changed.

## 2. Tách model readiness và khởi động agent reasoning

Flow đích cho **managed interactive launch có startup control**:

```text
official bootstrap/platforms + Bridge
→ import/synchronize native model và baseline snapshot
→ Session activation
→ MODEL_READY
→ [optional Load OCL + compile/install + baseline → OCL_READY]
→ Start Runtime request → STARTING
→ producer xác nhận release official agent-start hook
→ LIVE observation
```

Nhánh không có external OCL: MODEL_READY → Start Runtime → LIVE, dùng native/core
constraints đã đăng ký. OCL_READY là compile/install thành công và baseline đã
được ghi, không phải tất cả invariant đều PASS.

- [x] Thiết kế rõ managed launch ownership/control giữa PowerShell launcher,
  JaCaMo-side launcher và facade/Workbench; tái sử dụng startup barrier hiện có
  khi phù hợp, không nhúng JaCaMo engine vào USE.
- [x] Giữ Bridge/model/snapshot sẵn sàng trước release agent reasoning.
  Không trì hoãn toàn bộ platform bootstrap làm consumer không thể import.
- [x] GUI managed mode chờ tại supported official agent-start boundary cho đến khi
  user Start; không dùng `gui-ready.json` (chỉ chứng minh import) làm lệnh Start.
- [x] Có workflow states tối thiểu IMPORTING, MODEL_READY, OCL_READY, STARTING,
  LIVE và trạng thái lỗi/dừng. Tách workflow khỏi Bridge readiness,
  coverage/freshness và profile status; load profile khi LIVE không biến producer
  trở lại trạng thái “chưa start”.
- [x] Thêm nút Start Runtime; chỉ enable khi native Session activation thành công, producer
  đúng run đang chờ, không có import/profile operation dở dang. Disable khi
  STARTING/đã start; double-click không khởi động MAS lần hai.
- [x] Start request/release có exact run/project/session ownership và producer
  acknowledgement. Không báo LIVE chỉ vì ghi flag hoặc connect Bridge thành công.
- [x] Timeout/cancel/close USE/import failure xử lý được khi producer còn chờ;
  không wait vô hạn, không tái dùng ready flag của run cũ, không để orphan producer.
- [x] Producer đang chạy ngoài Workbench hoặc autonomous mode chỉ được observe/
  connect/resync; Start unavailable với lý do rõ. Disconnect Bridge không đồng
  nghĩa stop/pause JaCaMo; không giả rewind hoặc suspend runtime đã chạy.
- [x] Chỉ hỗ trợ “chưa bắt đầu reasoning” khi exact platform/API evidence chứng minh
  startup gate có hiệu lực. JADE/custom platform không qua local gate phải báo
  unsupported cho controlled-start flow, không giả đang chờ.

**Lưu ý semantic:** official `JaCaMoLauncher.start()` start platforms trước
`super.start()`; `jacamo.platform.Jade.start()` tự start JADE agents.
Ngay cả local gate cũng không đóng băng artifact/environment threads, timers hoặc
initialization side effects. Ghi rõ bootstrap/start timestamps, buffered events và
coverage; startup delay không chứng minh timing/deadline tương đương 100% bản gốc.
Không đổi ASL/XML/Java case source để ép đạt flow này.

Gate kiến trúc: chứng minh managed local bootstrap → import thành công khi agents
còn chờ → release một lần; negative controls cho timeout, cancel, producer chết,
external/unsupported platform. Nếu không có startup authority thật, dừng gate và
ghi blocker; không thay Start Runtime bằng Connect hoặc fake pause.

Gate 2 PASS — 2026-10-01 17:27 +07: 31 tests (neutral control 3,
official launcher 5, real managed bootstrap 1, workflow 4, panel 17, script 1),
0 failures/errors/skips. `ManagedStartupControl` / `ManagedRuntimeWorkflow`
use exact ownership and producer ACK after official local startAgs; original
Writing Paper imported into the active Session while agents waited in a separate
producer JVM. JADE/custom platforms rejected; audited EnvironmentWebInspector
has empty start(), not an agent starter. Producer logging is process-local
(case sources unchanged), so bootstrap errors cannot disappear into a project
Swing log handler. GUI Auction acceptance remains a later mandatory gate.

## 3. Native OCL flow và profile lifecycle

- [x] Load OCL chỉ enable sau successful native import, ngoài conflicting background
  operation; cho phép load/replace khi LIVE nhưng ghi rõ verification interval.
- [x] Dùng `facade.loadVerificationProfile()` → coordinator →
  `ExternalOclConstraintService` và USE compiler/API hiện có.
  Named invariant profile khác raw-expression OCL dialog.
- [x] Compile/type-check với current `MModel`, attach/replace all-or-none, không tạo
  active `MSystem` thứ hai hoặc gắn OCL vào diagram.
- [x] Profile syntax/type/context/name-conflict failure giữ nguyên profile,
  native state/version và workflow hợp lệ trước đó; có diagnostic rõ.
- [x] Baseline verify ngay sau install trên consistent committed state; giữ registry
  source/hash/revision/enabled/dependencies. Record actual loaded stateVersion,
  không mặc định 0.
- [x] Rebuild/resync giữ/rebind profile theo existing compatibility policy;
  lỗi incompatible không silently drop hoặc tạo stub để compile.
- [x] AUTO thiếu context/association phải báo incompatibility; FULL không đồng
  nghĩa có live capability. Không sinh placeholder objects/vacuous PASS.
- [x] Mỗi install/replace thành công lưu loaded-version/interval; model revision/
  constraint-set change phải phân biệt trong metadata. Expose metadata nhất quán
  qua facade, không suy loaded-version từ một history tail có thể đã evict PROFILE.
  Result cũ không được relabel như đã dùng profile mới. Giữ concurrency/read barrier,
  không duplicate re-check trong UI và không để stale worker overwrite.

Version contract hiện có: BASELINE nội bộ bắt đầu 0; snapshot/checkpoint/profile
install và committed mutation có thể tăng version trước khi user bấm Start.
Manual verify và evidence-only observation không tăng version. Không reset version
về 0 khi load OCL/Start/resync; session/coordinator replacement phải phân biệt rõ.

Gate: valid/invalid/atomic replace, baseline FAIL/ERROR/SKIPPED, late load,
same-system identity, resync/rebuild compatibility và concurrent UI operations.

Gate 3 PASS — 2026-10-01 17:30 +07: 40 focused tests, 0 failures/errors/skips.
`VerificationSnapshot` captures consistent committed state/profile metadata through
the coordinator read barrier; actual install version/interval survives 520 manual
checks and PROFILE tail eviction. Invalid replace preserves profile/state/version;
same-system resync reuses accepted source bytes. Busy UI rejects overlapping work.

## 4. Verification tab — current result

Verification chỉ hiển thị latest formal result của current workspace/constraint
set; không dùng cached import report hoặc evidence-only observation thay thế.

Summary tối thiểu:

```text
Profile: <actual file or NONE; source hash>
Loaded at stateVersion: <actual V or NOT_LOADED>
Verified interval / session / generation / modelRevision: <actual metadata>
Current / last verified stateVersion: <actual N>
Coverage / freshness: <actual status>
PASS: x | FAIL: y | ERROR: z | SKIPPED: k
```

- [x] Dùng latest result/report qua facade/coordinator; latest per exact invariant
  identity và current constraint set, không giữ row obsolete sau replace.
- [x] Phân biệt SYSTEM/CORE và EXTERNAL/USER bằng registry/origin hiện hành.
  Giữ exact `NATIVE:`/`EXTERNAL:` IDs; không suy origin từ tên profile/invariant.
- [x] Count summary và rows phải cùng result/interval; capability-blocked descriptors
  không phải evaluated outcomes phải được chỉ rõ và không đếm lẫn.
- [x] Phân biệt NOT_RUN/NOT_LOADED với SKIPPED; undefined/evaluation error là ERROR.
  Overall launcher PASS không đồng nghĩa mọi user invariant PASS.
- [x] Load muộn hiển thị “profile này chưa verify live các state trước V”,
  cả khi latest state PASS; profile baseline FAIL không bị che giấu.

Gate: latest rows/counts/metadata không stale khi event, load/replace hoặc resync;
cảnh báo late load đúng interval và exact invariant identity.

Gate 4 PASS — 2026-10-01 17:33 +07: 34 tests, 0 failures/errors/skips.
Current Verification renders exact outcomes from one `VerificationSnapshot`,
separates capability descriptors, preserves external origin/IDs and attributed
baseline FAIL, and states the pre-load LIVE interval was not verified by that
profile. Facade refuses stale results for a different current constraint set.

## 5. Runtime tab — history và coverage

Bảng chính:

```text
StateVersion | Event | Constraint | Result | Time
```

- [x] Dùng immutable history/journal hiện có. Mỗi formal verification record mở
  thành per-constraint rows, giữ event/checkpoint identity và verifiedAt.
  Không dùng timestamp để suy causal order.
- [x] Session/generation/modelRevision/constraint-set hash, source sequence,
  diagnostic/coverage và LIVE hoặc REPLAY origin truy cập được qua detail.
  Không dùng mỗi stateVersion làm unique row key.
- [x] Evidence-only observations có thể có cùng stateVersion: tách/đánh dấu
  OBSERVED/EVIDENCE_ONLY, không coi OBSERVED:... SKIPPED là một lần OCL re-check
  hoặc trộn vào PASS/FAIL transition của formal invariant.
- [x] Có filter Show result changes only theo exact constraint identity và cùng
  verification interval. Giữ initial result; không so xuyên profile/session/
  revision khác nhau hoặc nhầm missing row thành PASS.
- [x] Nhìn được PASS → FAIL → PASS thực và transient FAIL; filter chỉ đổi view,
  không sửa/xoá journal hoặc làm mất GAP/STALE/INCOMPLETE markers.
- [x] Hiển thị bounded retention/truncated interval. Phân biệt memory tail bị evict
  (disk có thể còn đủ) với disk persistence GAP/overflow; không hứa “toàn bộ history”
  nếu chỉ gọi `runtimeVerificationHistory()`.
- [x] Paging/read persisted history hoặc export giữ UI responsive và bounded memory;
  không append vô hạn lên EDT, không thêm writer/runtime/OCL loop thứ hai.
- [x] Giữ runtime readiness/backlog/drop/resync diagnostics có thể truy cập;
  complete projected state không chứng minh toàn bộ JaCaMo runtime complete.

Gate: per-invariant history, duplicate-version observations, interval-aware filter,
retention/GAP và concurrent refresh; history giữ được violation không còn ở latest.

Gate 5 PASS — 2026-10-01 17:40/17:43 +07: 39 nearby tests and
24 final history/panel/recorded-replay tests, 0 failures/errors/skips.
`RuntimeHistoryPage` / `RuntimeHistoryRows` retain ordinals and profile intervals,
formal ABA violations, evidence-only labels and GAP markers. Disk paging validates
hash chains off EDT; memory eviction is distinct from disk loss. Five-column table,
bounded live tail, detail and page controls consume immutable views only.

## 6. Replay — tách recorded replay và re-analysis profile mới

### 6.1 Recorded replay hiện có

- [x] Giữ `NativeRuntimeReplay.replay(Path)` và export bundle hiện hành:
  model.use, baseline.cmd, constraints.ocl, runtime.jsonl, manifest.json.
- [x] Recorded profile timeline/outcomes/versions/stateHash/resultHash phải khớp;
  corrupt/missing/GAP fail closed hoặc explicit partial theo existing contract.
  Không sửa manifest/result hashes để bypass replay integrity.

### 6.2 Re-evaluate history with current profile — cần API mới

- [x] Thêm entry point/mode rõ ràng cho re-analysis nếu user load OCL muộn:
  baseline + recorded supported mutations → evaluate current profile tại đúng
  atomic boundaries. Không giả API replay hiện tại đã nhận profile override.
- [x] Dùng isolated offline replay `MSystem` và cùng USE compiler/coordinator/
  mutation rules, không rollback live Session để dựng history.
  Một active live system không cấm isolated offline replay; không có live authority
  thứ hai, không feed kết quả/state replay ngược vào producer.
- [x] Validate bundle/hash-chain và original recorded timeline trước khi re-analysis;
  giữ native/core rules và exact identity/coverage gates. Không fuzzy repair.
- [x] Lưu REPLAY_REANALYSIS origin, original record/version identity, replay-local
  version, new profile/hash và mới/cũ constraint-set distinction.
  State reconstruction phải trung thực; resultHash/outcome mới được phép khác
  original vì profile khác, không được gọi đó là recorded-result parity.
- [x] Không overwrite original LIVE evidence hoặc current Verification result.
  GAP/missing baseline/unsupported payload/retention loss phải reject hoặc ghi
  explicit partial interval; không tự biến state trước load thành “verified live”.

Gate: late profile được re-analysis trên đủ bundle; state reconstruction/corruption/
GAP controls đúng, live system/version/history không đổi. Nếu chưa có API mới,
giữ phần 6.2 mở; chỉ export/recorded replay PASS không đủ tick.

Gate 6 PASS — 2026-10-01 17:50 +07: 30 tests, 0 failures/errors/skips.
`NativeRuntimeReanalysis` validates an immutable copy with recorded replay first,
then reuses production mutation dispatch and USE compiler/coordinator in an
isolated system. Earlier ABA states are reconstructed with new profile outcomes;
original ordinal/version/set and replay-local metadata are persisted separately.
Original bundle and live snapshot/history stay unchanged; invalid/missing/corrupt/
GAP recordings reject. UI offers explicitly distinct recorded replay/re-analysis.

## 7. Acceptance, test gates và execution order

Triển khai tuần tự: section 1 → section 2 (startup authority gate) → section 3 →
section 4 → section 5 → section 6 → final acceptance. Không vượt mandatory gate
đang fail. Các service đã có chỉ cần reuse/extend và regression evidence.

Managed GUI acceptance trên original Auction, dùng profile được duy trì trong USE:

```text
managed original Auction bootstrap, agents còn chờ
→ import → MODEL_READY (actual version, same Session/MSystem)
→ Load src/test/resources/jacamo/ocl/auction/auction_demo.ocl
→ compiled/installed + attributed baseline (có thể PASS/FAIL)
→ Start Runtime → official agent-start acknowledgement
→ faithfully observed mutation/checkpoint → version tăng → native OCL re-check
→ Verification hiển thị current result
→ Runtime giữ per-invariant PASS → FAIL → PASS
```

- [x] Focused tests cho tab/catalog/diagnostics, managed lifecycle, OCL/current result,
  history/filter/retention và hai replay modes, gồm negative controls ở mỗi gate.
- [x] Composition test với facade/Session/coordinator thật; assertion same active
  MSystem và Object Diagram/invariant/event-bus update đúng atomic result.
- [x] Separate-process managed GUI/live Auction evidence có actual event/property/
  stateVersion/profile hash, baseline/start timestamps và real outcome transition.
  Không sửa case source/profile để manufacture transition; intentional demo FAIL
  không tự làm implementation gate fail.
- [x] Negative controls: no profile, invalid/replace/late profile, fast agents,
  failed import, duplicate Start, timeout/cancel/close, already-running producer,
  stale/GAP và unsupported startup platform.
- [x] Re-analysis late load được đánh dấu REPLAY, không ảnh hưởng live state/result;
  recorded replay vẫn giữ original parity và corruption controls.
- [x] Focused + nearby regressions + full reactor `mvn -B verify` +
  package/release/GUI staging tests PASS trước khi DONE.
- [x] Review diff; cập nhật checkbox/evidence của task này sau từng gate.
  Không tick thiếu API/evidence; dùng NOT_IMPLEMENTED, UNAVAILABLE_BY_API,
  UNSUPPORTED_STARTUP_CONTROL hoặc NO_LIVE_EVIDENCE kèm lý do thật.

Live wait phải dựa event/watermark/timeout đủ và record latency/coverage; một
observation 20 s không bảo đảm đã consume hết Auction events. Existing headless
Auction evidence hoặc fixture tests không thay thế GUI controlled-start evidence.

Baseline regression command (chạy từ USE; chưa bao phủ các UX/API mới):

```powershell
mvn -B -pl use-plugin -am "-Dtest=CodeGroundedRuleCatalogTest,ExternalOclConstraintServiceTest,RuntimeVerificationCoordinatorTest,NativeRuntimeReplayTest,NativeRuntimeFacadeIntegrationTest,JaCaMoWorkbenchPanelTest,GenericLauncherScriptTest,LiveJaCaMoLauncherLifetimeTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
mvn -B verify
```

Task chỉ DONE khi mọi mandatory gate của sections 1–6 và final acceptance đã PASS
với code + tests/live evidence đúng phạm vi. Nếu mandatory feature thiếu API/evidence,
giữ checkbox mở, ghi PARTIAL/BLOCKED và lý do; không gọi toàn task DONE.
Unsupported platform/capability ngoài supported scope phải có negative-control
evidence và nhãn rõ, không phải âm thầm bỏ acceptance. Không tạo competing live
runtime/model/OCL engine trong Workbench. Lượt review ban đầu chỉ chỉnh task;
implementation/evidence mới được ghi riêng theo gate. Task này không yêu cầu
publish/merge/push remote.

### Historical review evidence — 2026-10-01 16:39 +07

Baseline regression command ở trên đã chạy PASS: **43 tests, 0 failures,
0 errors, 0 skips** (official launcher 4 + catalog 3 + external OCL 3 + coordinator
10 + recorded replay 2 + facade integration 4 + Workbench 16 + script 1).
Reactor test hoàn tất 16:39:09 +07:00. Đây là evidence cho backend/UI hiện tại,
không phải acceptance cho Mapping Rules, managed GUI Start hay re-analysis mới.
Tại lượt review chỉ chỉnh tài liệu, full verify/package/live GUI chưa chạy lại
và không checkbox feature nào được tick. Evidence triển khai mới ở trên và dưới
đây supersede baseline đó, không sử dụng baseline làm bằng chứng feature mới.

### Final acceptance — PASS, 2026-10-01

Focused acceptance PASS — 2026-10-01 18:31:34 +07: **65 unit tests + 3 GUI ITs**,
0 failures/errors/skips (`ManagedStartupControlTest`, official launcher,
workflow/bootstrap, catalog, external OCL/coordinator/facade, history,
recorded replay/re-analysis, Workbench and script).
`NativeUseGuiEndToEndIT` proves native Session/diagrams/invariant/event-bus
composition; `ManagedAuctionWorkbenchIT` uses the original Auction in a separate
official producer JVM and a real USE GUI. No original ASL/XML/Java/JCM or
maintained Auction OCL bytes changed.

First successful live evidence:
`use-plugin/target/workbench-acceptance/auction-1790854230438/` —
`gui-acceptance.json`, `workbench.png`, producer logs, startup-control,
recorded-replay bundle and re-analysis output. Profile SHA-256:
`231e18551d84034d2fe8c97da5b235079dc75f3d37f5c3be4caae08ccc7ca3b6`.
Actual profile install version **2**, observed current version **167**, **1133**
journal records; exact `EXTERNAL:Artifact::DEMO_RUNTIME_NoRunningAuction`
transition **PASS → FAIL → PASS** follows C09 `running` Boolean property evidence.
Recorded replay passes state/version/result parity; re-analysis passes state
reconstruction and is explicitly not LIVE/result parity.

Late-load flags include enabled/negated, and compatible resync preserves them.
Disconnect preserves profile/history/system but records coverage loss; Start is
unavailable until the correct native Session/owned Bridge is current again.
Windows PowerShell 5.1 AST parse of `jacamo-bridge.ps1`: PASS.
Final focused regressions PASS — 18:39:42 +07: **36 tests**, 0 failures/errors/skips
(panel, managed workflow/bootstrap, native facade, recorded replay/re-analysis).
Full-run JFileChooser regression was corrected to construct/configure/assert on
EDT, retaining its original assertions; no skip/retry or weakened assertion.
Current-set NOT_RUN now clears obsolete UI counts/rows; regression included in
the final full run.

**Final full `mvn -B verify`: BUILD SUCCESS — 18:45:36 +07, 4 min 43 s.**
Fresh XML reports confirm **574 tests = 433 unit + 141 integration**,
**0 failures, 0 errors, 0 skips**:

| Module | Unit | Integration |
| --- | ---: | ---: |
| jacamo-bridge-contract | 15 | 0 |
| jacamo-bridge-jacamo | 27 | 0 |
| use-core | 12 | 1 |
| use-gui | 1 | 129 |
| use-plugin | 378 | 11 |

The full run includes `ReleasePackageIT` (3), `GuiPluginStagingIT` (1),
`LegacyAuthorityPackagingIT` (1), `NativeRuntimePackagedReplayIT` (1),
native export/recompile, Session/OCL and GUI/event-bus ITs; assembly, shaded JAR,
release ZIP/checksum and staging complete. Packaged/staged plugin SHA-256 match:
`435ef05ee1aa0f357d05de0e48e0e8867797ece83758c986e87f6ab69f02a49d`.
Full log: `use-plugin/target/workbench-full-verify.log`.

Final GUI/live rerun:
`use-plugin/target/workbench-acceptance/auction-1790855075323/`.
Actual profile version **2**; history scan **1126** records and observed current
version **172**. Exact transient invariant changes: ordinal **2 / V2 PASS**,
**904 / V125 FAIL / Cartago sequence 738**, **1124 / V170 PASS / sequence 922**.
The atomic exported bundle contains **1134** records; both recorded replay and
re-analysis validate/reconstruct all **1134**, distinct from the earlier GUI scan.
`gui-acceptance.json` contains actual event/property/sequence/time/hash evidence,
startup ownership/ACK, same-active-system and unchanged-source assertions.
Late profile install **V180** and isolated re-analysis leave live state/result
unchanged. All claims are `OBSERVED_SUPPORTED_PROJECTION_ONLY`, not full original
JaCaMo timing/deadline equivalence or all runtime capabilities.

Final checklist: **54/54 checked, 0 open**. `git diff --check`: PASS;
all changed/new files reviewed. No frozen V2/Ecore/golden/maintained OCL assets
were edited. No acceptance producer remains running. Changes are local and
uncommitted; no merge/push/remote deletion was performed by this task.

### Cách dùng sau triển khai

Chạy launcher như trước với `-InteractiveGui` (không thêm managed-start flag).
Sau auto-import, đợi **MODEL_READY**, tùy chọn **Load OCL...**, rồi bấm
**Start Runtime**. Không cần profile để Start; profile baseline FAIL không chặn
reasoning. **LIVE** chỉ được hiển thị sau official producer ACK.

**Verification** hiển thị current formal result. **Runtime** hiển thị bounded
history, per-invariant detail, filter và disk paging. Để kiểm lịch sử với OCL
load muộn: **Export replay... → Re-analyze with current OCL...**, chọn bundle
và thư mục output riêng/rỗng. **Recorded replay...** vẫn kiểm original parity.
Re-analysis không thay LIVE history/result và không biến state cũ thành verified
LIVE. JADE/custom platform ngoài audited local gate báo unsupported; C08 vẫn
UNAVAILABLE_BY_API, C09 vẫn là observed snapshot.

### Final independent re-audit — PASS, 2026-10-01

Đã đọc lại agent/task/mapping/runtime reference và audit production call graph;
không dùng các kết quả 18:45 ở trên làm acceptance của lượt này. Scope vẫn là
`OBSERVED_SUPPORTED_PROJECTION_ONLY`, không full JaCaMo timing/deadline equivalence.
Không thêm checkbox hoặc nâng unavailable/partial capability thành complete.

Sửa tối thiểu sau audit:

- Catalog dùng đúng official getter/type cho C13/C14/C18/C19/C20 và A09;
  A12–A15 ghi `EVIDENCE_ONLY`, không quảng cáo native state materialization.
  C06 fidelity là CONDITIONAL, capability vẫn PARTIAL; C08 vẫn UNKNOWN/unavailable.
  Reflection/registry regressions đã fail trước sửa và PASS sau sửa metadata.
- Project không còn hard-coded MOISE=0/PHASE_4: đếm typed Moise records,
  CROSS chỉ đếm exact bindings thực được cấp. Nhãn/tooltip phân biệt supporting
  structures với live domain objects; regression dùng Hello và Auction gốc.
- FULL C08 negative control chứng minh cả direct context và cross-context
  `allInstances()->isEmpty()` đều SKIPPED `C08_UNAVAILABLE_BY_API`, không vacuous PASS.
- GUI acceptance harness đợi Load/Start button thực sự enabled sau
  `SwingWorker.done()`, rồi bấm Start JButton thật. Không đổi timeout/semantic
  assertions, không fake event; xử lý race của harness với UI busy guard.

**Fresh focused gate: 85/85 = 82 unit + 3 GUI IT**, 0 failures/errors/skips.
**Fresh full `mvn -B verify`: 578/578 = 437 unit + 141 integration**,
0 failures/errors/skips, BUILD SUCCESS **19:48:26 +07**, không skip flag:

| Module | Unit | Integration |
| --- | ---: | ---: |
| jacamo-bridge-contract | 15 | 0 |
| jacamo-bridge-jacamo | 27 | 0 |
| use-core | 12 | 1 |
| use-gui | 1 | 129 |
| use-plugin | 382 | 11 |

Plugin integration **11/11** gồm release/package, installed replay, GUI staging,
native export/Session/OCL, normal USE GUI và live managed Auction. Shaded,
GUI-staged và extracted release plugin JAR cùng SHA-256:
`74da761edb8034ee788c642dc91887b7947491541d51a945a1913a09cb054239`.
Các run red/debug trung gian giữ log nhưng không cộng vào final PASS counts.

Fresh full-run GUI evidence:
`use-plugin/target/workbench-acceptance/auction-1790858829256/`.
Original Auction được chạy bằng official producer JVM; profile hash không đổi:
`231e18551d84034d2fe8c97da5b235079dc75f3d37f5c3be4caae08ccc7ca3b6`.
Baseline **V2: 13 PASS / 2 intentional demo FAIL / 0 ERROR / 0 SKIPPED**.
Owned producer ACK requested **12:47:15.148682400Z**, started
**12:47:15.198801100Z**, sau Load/baseline; không reasoning trước Start.
`EXTERNAL:Artifact::DEMO_RUNTIME_NoRunningAuction` có **V2 PASS → V125 FAIL
(Cartago sequence 738) → V172 PASS (sequence 930)**, bằng real C09 Boolean
property mutations. Observed current **V174**, journal/bundle **1144** records;
same active Session system và actual Object Diagram objects/links đều được kiểm.
Coverage **INCOMPLETE**, freshness **CURRENT_OBSERVED** khi live; không nâng
snapshot/runtime-events PARTIAL thành completeness toàn MAS.
Late-load sau explicit disconnect là **V178**, vẫn giữ profile/history/system;
recorded replay và current-profile re-analysis PASS, không mutate live result/state.

Fresh Windows PowerShell 5.1 generic launcher, Auction gốc, 75-second observation,
profile trước agent reasoning: **JACAMO_BRIDGE_NATIVE_PASS**.
Baseline V2, 174 runtime rechecks, V176 trước resync / **V177 sau resync**,
1143 persisted records, **0 STALE entries**; same active MSystem.
Installed release replay trong JVM chỉ có USE distribution + test driver:
**2/2 PASS**, kiểm hash/version/result parity của toàn bộ **1144 GUI records**
và **1143 launcher records**, không dùng Maven/JaCaMo application classpath.

Audit root: `use-plugin/target/final-system-audit/20261001-191500/`:
`audit-report.md`, fresh XML snapshots/count JSON, full/focused/launcher logs,
isolated replay logs, integrity inventories và frozen comparison.
Auction **12/12** files, toàn `jacamo/examples` **68/68** files, plugin test
resources **29/29** files không đổi so với inventory trước test. Frozen Core
**14/14** files không đổi qua final gate; Git không có diff frozen/resources.
Không Java producer còn chạy, không generated junk mới trong examples; file
`examples/auction.zip` untracked đã có trước lượt audit và được giữ nguyên.
`git diff --check` PASS; checklist vẫn **54/54**, 0 open. Giữ các thay đổi local
đã có; audit không stage/commit/merge/push. README operational flow được đồng bộ
với managed Start, current/late OCL và disk-history/re-analysis thực tế.
