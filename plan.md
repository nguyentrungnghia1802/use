# Hello World và House Building — authored case studies

## Responsive runtime results
- [x] RL01 — Prepare scoped timeline/failure data on background worker with immutable projection cache; keep navigation/evidence semantics.
- [x] RL02 — Coalesce UI publication, defer hidden graphs/diagnostics, expose received/verified state progress.
- [ ] RL03 — Reduce repeated certificate/hash work; focused tests, Auction regression and new package only.

RL evidence: use-plugin/verification/responsive-results. Immutable presentation
preparation is off EDT; exact-cut projection reuse, selected-scope/import guards,
one worker at a time and lazy visible graph rendering preserve audit records.
Journal single serialization retains canonical bytes and hash-chain validation.
Repeated same-record UI application benchmark: 83.3 ms before, 0.2 ms after;
linked application with hidden graphs: 311.7 ms before, 6.6 ms after. Cold load
still has setup cost; these isolated figures are not an end-to-end latency claim.
Full Auction gate remains unchecked: two source runs reach two qualified fulfilled
roots with no journal gap, but AuthoredAuctionResultsIT asserts five capability
players per execution while the captured admitted scope contains fewer. Original
assertion and both failing attempts retained; no case-wide PASS claimed or semantic
assertion weakened. Focused verification and final package receipts are recorded
separately from that unresolved case acceptance assumption.

Build/run convention (2026-10-30): keep one runnable installation at
use-assembly/target/new/bin/start_use.bat. Maven package replaces that directory
after staging succeeds and preserves its work directory. Do not create extra
randomly named runnable installations for routine verification or delivery.

Repo sửa: `use`; branch hiện tại `main`; không commit/push. `jacamo` và
`use4Goal` chỉ đọc. Kế hoạch này thay plan cũ theo yêu cầu; evidence lịch sử
trong `use-plugin/verification` được giữ nguyên.

## Mục tiêu và kiến trúc

Tạo `use-plugin/examples/hello-world` và `house-building`, học pipeline Auction:
JaCaMo external process → existing TCP/admission → generic XML rules → một
USE MSystem → native OCL + original use4Goal GoalMarking/propagation → timeline.
Mỗi case có source project, CSL, iStar, mapping XML, authored OCL, provenance,
README chọn đủ năm inputs. Không viết case-specific production Java mapper.

Formal models là authored requirements có phạm vi rõ: Hello World quan sát
Console.numMsg; House Building quan sát tám procurement auctions và successful
construction operation receipts. GUI drawing không phải state authority.
MOISE sequence/parallel không được đổi thành claim execution-order từ AND;
order được kiểm riêng khi có source operation evidence. Agent/belief không
thành CSL Entity/world truth; missing/partial/unsupported không thành false.
Không giả model covers mọi JaCaMo internals/norms/epistemic state.

## Dependency order và tasks

- [x] **C01 — Source audit và provenance**
  - Source: `jacamo/doc/tutorials/hello-world/code/helloworld` và
    `jacamo/examples/house-building` (Hello World không nằm trong examples).
  - Inspect JCM/ASL/OS/Artifact thật; copy runtime/build sources cần thiết;
    manifest hash từng file và mọi adaptation. Không copy target/log/cache.
  - House Building condition-wait patch đã được user chấp thuận trước đây:
    giữ version/provenance, không tuyên bố byte-identical upstream. Headless
    presentation adaptation phải giữ nguyên public operations/domain state.
  - Gate PASS: `NewCaseStudyBundlesTest` kiểm từng upstream/local hash và patch inventory; source tham chiếu read-only không đổi.
- [x] **C02 — Hello World five-input bundle** (sau C01)
  - CSL Console và context; iStar observed-outcome requirements; XML source
    assertions/runtime joins/physical identities/value conversions/occurrences;
    OCL invariants. Pin exact inputs và optional JCM layout policy đã có.
  - Gate: original frontends/validators + generic mapping + native backend/OCL
    compile; tampered inputs fail closed. PASS trong `new-cases-focused-final.log`.
- [x] **C03 — House Building five-input bundle** (sau C01)
  - CSL ProcurementAuction và workspace context. BuildProgress là observer,
    không biến execution receipt thành Entity. XML yêu cầu neutral verified
    operation-history capability của simulator.House; completed operations và
    first successful source sequences đủ evidence mới populate observer.
  - iStar đầy đủ construction outcomes theo source, AND chỉ là outcome
    conjunction; OCL kiểm state ranges và observed construction order riêng.
  - Gate PASS: original validators/native compile; unknown source/partial population không thành false. Xem hai focused logs bên dưới.
- [x] **C04 — Focused semantic/regression tests** (sau C02/C03)
  - Provenance và five-input checksums; source selectors; models/frontends;
    native OCL; wrong inputs; exact numeric/string conversions; no custom
    mapper/fallback; existing Auction static compatibility nếu shared code đổi.
  - Gate PASS: 33 focused tests và 25 operation/nearby tests (hai tập có overlap); 0 failure/error/skip. Không cộng chúng thành số tests duy nhất.
- [x] **C05 — Live Hello World và House Building** (sau C04)
  - Real external JaCaMo + TCP; one MSystem; observe actual Console.numMsg và
    eight AuctionArt states; verified successful construction receipts; original
    engine called; native verification; timeline; Stop leaves USE alive;
    restart/reset ownership và replay portable có executable evidence.
  - Headless tests chỉ dùng documented source presentation adaptation; không
    fake framework events hoặc domain state. Domain FAIL khác test failure.
  - Gate PASS: `AdditionalAuthoredCasesIT` 2/2, native PRE/POST và @pre,
    original goals, timeline, Stop/Restart, replay sau xóa toàn bộ copied inputs.
- [x] **C06 — Package và hướng dẫn GUI** (sau C05)
  - Đóng gói examples trong ZIP theo assembly hiện có; verify bytes/hash trong
    ZIP, README năm inputs/GUI Prepare/Start/Stop/Restart và limitations.
  - Gate PASS: package BUILD SUCCESS; 50 bundle files + 4 JARs có byte/hash
    parity trong final ZIP và bản giải nén mới. README có bảng năm inputs.

## Definition of Done

Hai bundles dùng được với main five-input workflow; input nhất quán, portable,
provenance rõ. Static/native compile và live riêng từng case PASS; state thực
và original marking có receipts; partial/unknown được giữ. Không yêu cầu toàn
bộ goal PASS nếu source không đạt requirement. Package chứa đủ bundle; không
sửa upstream/source oracle để ép semantics. Không claim manual GUI/cold live
khi chỉ chạy service-level live và archive checks.

## Evidence cuối (2026-10-28)

- Logs được giữ trong `use-plugin/verification/additional-cases/final/`.
- Focused: `new-cases-focused-final.log`: BUILD SUCCESS,
  adapter 7 + plugin 26 tests, 0 failure/error/skip.
- Operation/nearby: `new-case-operation-focused.log`: BUILD
  SUCCESS; xem tổng trong log. Chạy vì live test phát hiện native pre/post
  không được gọi với raw XML M0 physical identity.
- Baseline lỗi thật: `new-case-population-baseline.log` chứng minh observer
  complete table thiếu source-cut proof; `new-case-operation-baseline.log`
  chứng minh callback dùng framework semantic ID bị phân loại EVENT.
- Shared fixes: subscribe run-owned logger cho workspace động trước agent
  action; controller read không công nhận birth trước CREATED, publish birth
  và admission cùng monitor; observer COMPLETE table giữ genuine source-cut
  proof; native operation receiver chọn exact physical envelope identity trong
  XML profile, vẫn check program/scope/incarnation. Không đổi consumer admission.
- ContractsAwarded dùng positive winner cho từng trong tám named source tasks,
  không count=8/forall có thể chấp nhận tám bản sao cùng task. House observer
  joins official workspace parentSemanticId; không thay physical UUID/workspace.
- Final acceptance: `new-cases-acceptance-completed.log`: BUILD SUCCESS,
  7 tests = 2 live + 5 bundle/semantic tests, 0 failure/error/skip. Không chạy
  lại full reactor regression hoặc live case khác. Packaging skip tests là
  bước riêng sau acceptance này, không tính là test evidence.
- Hello receipt: `../hello-world-9596413875591659644/receipt.json` (relative
  từ thư mục final): 2 goals FULFILLED, same MSystem, native printMsg pre/post;
  Stop/Restart đổi PID/run và dừng cả hai child processes; replay kiểm 758 entries,
  final state/result hashes match sau xóa TOÀN BỘ copied inputs/source.
- House receipt: `../house-building-3676368246113313720/receipt.json`: 8 auctions
  có winner, 10 completed construction operations, 15 goals FULFILLED, native
  bid pre/post; same MSystem, Stop/Restart cleanup; replay kiểm 2826 entries và
  final state/result hashes match sau xóa toàn bộ copied inputs/source.
- Operation evidence nằm trong `operation-checks.json` từng receipt directory:
  real invocation, before/after witness, native @pre; không dùng fabricated
  operation/capability facts. `state.json`, `result.json`, `program.json`, raw
  CArtAgO events và frozen replay được giữ cùng receipt.
- Hai final results giữ coverage tổng INCOMPLETE / CURRENT_OBSERVED. Invariant
  `OrgContext::CslV4RoleCompatibility` SKIPPED vì POPULATION_UNPROVEN; không
  claim role/norm compliance. Các goal được claim đều có qualified evidence;
  final native constraints không có FAIL/ERROR. Headless inspector warning không
  được dùng làm domain evidence hoặc bị đổi thành PASS.
- Một lần House test cũ timeout khi đi mọi UI Next (mỗi Next dựng lại prefix,
  dẫn tới O(n²)); harness cuối validate toàn recording tuyến tính, so exact
  final hashes, rồi kiểm Next/Previous/reset riêng. Không sửa production replay
  hay nới semantic assertions. Đợt `new-cases-acceptance-final.log` bị ngắt EOF
  không được tính PASS; chỉ log `completed` và receipts trên là final evidence.
- Package: `new-cases-package-final.log` BUILD SUCCESS; final ZIP
  `use-assembly/target/use-7.5.0-use-bin.zip`, SHA-256
  `f48724a287a13cffee05ebacdec7272a4c730fd2dbe883b3dbd2c682932f3815`.
  `final/package-checks.json` ghi từng file/JAR hash và fresh extraction.
- `final/source-freeze.json` và `final/acceptance.json` liên kết đúng source,
  compiled classes, logs/receipts và package cuối; không dùng log cũ thay gate.
- Chạy GUI: mở `bin/start_use.bat` trong bản giải nén mới được ghi ở
  `package-checks.json`; Plugins → JaCaMo / use4Goal verification..., chọn năm
  file theo README của từng case. Kiểm thử live qua service API của chính main
  workflow; chưa tự thao tác GUI hoặc chạy cold live từ ZIP. Hai việc này không
  thuộc gate C05/C06 và không được diễn giải là đã có manual evidence.

**Trạng thái:** C01–C06 và DoD trong phạm vi observed-world hai case đã đạt.
Không sửa `jacamo`/`use4Goal`, không commit/push. Các thay đổi người dùng có
trước task được giữ; source freeze đánh dấu working tree dirty.


## Update — flexible source import (2026-10-28)

Yêu cầu mới supersede chính sách pin source khi import GUI thông thường.
C01–C06 và source freeze ở trên là evidence phiên bản trước thay đổi này.

- [x] F01: GUI default validated source; Strict source checksums explicit.
- [x] F02: Project relocation/renaming và compatible source edits; selectors,
  official digest/binding/capability checks còn hiệu lực. Không rewrite input.
- [x] F03: Freeze actual source hashes riêng với XML reference pins; frozen
  revision tamper bị reject. Replay integrity không phụ thuộc đường dẫn cũ.
- [x] F04: Focused input/UI/strict regression + actual external Auction
  Prepare/Import qua TCP; package và fresh extraction cho người dùng chạy.

Scope: chỉ chính sách SOURCE JaCaMo. CSL/iStar/OCL vẫn là bộ authored requirements
pin theo specification. XML không tự suy diễn requirements tùy ý. API cũ giữ
strict để bảo toàn historical tests/replay; GUI tạo selection với flag explicit.
Status: PASS, evidence `use-plugin/verification/flexible-source-import/`.
`flexible-source-import-final.log`: 20 tests, 0 failure/error/skip, BUILD SUCCESS.
Actual external Auction + authored-v1 Prepare/Import đạt MODEL_READY qua real
producer/TCP, source giữ nguyên và child đã dừng. `FlexibleSourceImportTest`
kiểm relocated/renamed project + ASL revision, missing asserted group, frozen
source tamper và explicit strict; UI test kiểm default unchecked/captured flag.
Test negative đầu dùng role name không được assertions của XML này ràng buộc;
fixture negative cuối đổi group type mà XML có require rõ. Không đổi assertion
expected exception thành pass, không thêm giả định role semantics vào Java.
`flexible-source-import-package.log`: BUILD SUCCESS (skip tests sau focused gate).
`acceptance.json` liên kết source hashes/JAR/ZIP và fresh launcher. Chỉ import
và source-policy gate được chạy trong update này; không claim full Auction
execution/manual GUI mới hoặc sửa original engine/quality semantics.

Fresh GUI launcher: `D:\_CODE_BANK\Project_\08_Thesis\use\use-assembly\target\flexible-source-import-0gh8ds6_\use-7.5.0\bin\start_use.bat`.


## Update — compact business timeline and selective verification (2026-10-28)

- [x] T01: Summary/detail navigation; count milestones separately from audit boundaries,
  preserve failures/gaps, original journal, user selection, epoch ownership and replay.
- [x] T02: Declarative presentation projection in XML; frozen native domain values,
  read-set closure, smaller versioned Auction observed-state profile without full BB,
  exact MOISE timestamps/norm demands. Keep authored-v1 bytes and semantics intact.
- [x] T03: KNOWN_DERIVED qualifies correctly; separately show last certified historical
  task/goal result with exact occurrence/episode/run identity; unknown current remains
  unknown. Detailed Task tuple diagnostic, no raw-to-qualified promotion.
- [x] T04: Reuse AST dependencies and safe native evaluation memoization for unchanged
  inputs on ordered pinned cuts; do not skip original temporal recurrence, operation
  pre/post, gap handling or before/after. Unsupported dependency forms evaluate normally.
- [x] T05: Requirement-scoped coverage separately from overall source coverage; unknown,
  invalid, skipped and source gaps remain explicit. No global INCOMPLETE -> PASS rewrite.
- [x] T06: Focused UI/semantic/negative/regression gates; real external Auction from
  jacamo/examples/auction over TCP, compact timeline, verification, Stop/Restart/replay;
  final package byte parity and documented five inputs. No other live case studies.

Profile boundary: minimal Auction is an explicit authored state/outcome abstraction,
not a proof of every original Jason Task or normative compliance. Native operation
pre/post remains real invocation evidence; full authored-v1 iStar Task model remains
available. A fixed 30-state cap is forbidden: summary reduces evidence noise and may
fold bids into detail; violations and actual semantic milestones must remain visible.


### Acceptance — compact update (2026-10-28)

**T01–T06 PASS trong phạm vi update này.** Evidence chính:
`use-plugin/verification/compact-auction/final/acceptance.json`.
Không diễn giải kết quả này thành full normative compliance hoặc final GUI
acceptance của mọi profile/case study.

- `final/acceptance-tests.log`: **BUILD SUCCESS; 32 tests; 0 failure/error/skip**
  = 31 focused/nearby + 1 actual external Auction. Chỉ chạy live
  `D:/_CODE_BANK/Project_/08_Thesis/jacamo/examples/auction/auction.jcm`.
- `BusinessTimelineTest`: 1284 audit cuts thành 3 fixture domain phases;
  giữ failure/gap/stale/progress, exact latest; summary/detail/interval toolbar
  và compact state text trên EDT. Không có fixed-cap production algorithm.
- `ObservedTimelineHistoryTest`: prefix 550 cuts sống qua RAM-tail eviction,
  pending/completed dedup, quota gap và reopen/ownership change; không xóa journal.
- `CertifiedGoalHistoryTest`: KNOWN_DERIVED, certificate theo run/player/episode;
  missing current không được promote từ history. UNDEFINED vẫn INCOMPLETE.
  Exact journal completion không bị pending anchor cũ của cùng accepted boundary,
  state hash/profile ghi đè; profile khác hoặc gap không được certify bằng cut cũ.
- Goal predicate false hiển thị CONDITION_FALSE; original iStar marking quyết
  định goal VIOLATED. Boolean false của invariant/pre/post vẫn là FAIL. Không
  sửa stored semantic verdict, original recurrence hoặc native OCL evaluator.
- `DependencyGoalEvaluationTest`: native predicate reuse khi attr không liên quan
  đổi; attr dependency đổi phải evaluate; mất coverage phải SKIPPED; clear/reset;
  unsupported collection form quay về native evaluator mỗi cut. Temporal step,
  invocation PRE/POST và @pre không được cache. Existing native dependency visitor
  và invariant checkpoint selection được reuse; cleanup memo ở lane termination.
- `CompactAuctionProfileTest`, `DeclarativeOperationCheckpointTest`,
  `XmlObserverCoverageTest`, `ObservedConformanceWorkflowTest`: original models,
  native schema/OCL, selected capabilities, exact physical operation identity,
  coverage preservation và main workflow UI wiring. Không thêm case-specific Java.
- Actual live receipt: **424 audit boundaries → 6 milestones**.
  Hai physical Auction instances kết thúc, hai AuctionFinished FULFILLED /
  KNOWN_DERIVED; same authoritative MSystem; native invocation PRE/POST PASS.
  Requirements tại cut cuối KNOWN; overall source coverage vẫn INCOMPLETE /
  CURRENT_OBSERVED. Helper role population SKIPPED còn trong detail, không đổi
  thành PASS. Minimal profile chỉ kiểm state/outcome đã authored; không claim
  full Jason Task, exact MOISE transition timestamp hoặc NPL compliance.
- Receipt `../receipt.json`: external PID 24832, restart PID 8264;
  cả hai dừng; USE vẫn sống. Restart đổi PID/run; replay xác minh 499
  entries bằng frozen program; không cắt recording theo summary.
- Baseline failures được giữ riêng: `baseline-exact-field-join.log` (incomplete
  receipt của Artifact khác làm join mất qualification; profile dùng existing
  QUALIFIED_PHYSICAL_REFERENCE + đúng joined capability proof và two-stage
  identity/assignSnapshot), `baseline-pending-and-binding.log` (pending thành
  milestone + test thiếu self binding), `baseline-stale-pending-anchor.log`
  (pending copy ghi đè completion), `baseline-replay-directory.log` (harness
  export vào thư mục non-empty). Final harness dùng thư mục replay riêng;
  không nới replay exporter hoặc semantic assertions để vượt gate.
- `final/source-freeze.json`: source + compiled class hashes của working tree
  cuối, actual external JaCaMo source hashes, 4 original marking/propagation
  sources khớp ORIGINAL_COPY provenance. Bốn full authored-v1 model/spec files
  giữ byte parity với package trước; XML Git blob có normalization EOL nên
  freeze ghi riêng working-byte hash và normalized HEAD comparison.
- `final/package.log`: BUILD SUCCESS, skip tests ở bước package sau test gate.
  `final/package-checks.json`: 6 compact bundle files + 4 current JARs byte/hash
  parity trong ZIP và fresh extraction; 25 changed compiled main classes +
  4 original engine classes khớp packaged class bytes. Final ZIP SHA-256:
  `c713e059c0b70a392c21d8297ed9dbd37f7923357de541125e4cb700a3c82420`.
- Main GUI launcher mới: `D:\_CODE_BANK\Project_\08_Thesis\use\use-assembly\target\compact-auction-hp5al2vr\use-7.5.0\bin\start_use.bat`.
  Plugins → JaCaMo / use4Goal verification...; chọn CSL/iStar/XML/OCL từ
  `use-plugin/examples/auction/compact-v1/`, JCM external như trên. README trong
  thư mục compact-v1 có đủ năm inputs và giới hạn semantics. Không trộn với
  bốn file authored-v1 cũ. Summary mặc định; Inspect milestone interval hoặc
  Show all audit boundaries để xem evidence đầy đủ. Profile cũ không có
  presentation policy vẫn giữ native state changes, không tự bỏ CSL changes.
- Đây là component UI + actual main-workflow service/TCP live và archive byte
  verification; **chưa thao tác GUI thủ công/cold live từ final ZIP trong update
  này**. Không dùng log này để claim hai việc đó. Không chạy full regression,
  live case khác; không sửa upstream `jacamo`/`use4Goal`, không commit/push.


## Update — detached live results / stable Goal–Task table (2026-10-28)

Latest user request supersedes T01 navigation in the main authored UI. Keep raw
journal/replay and advanced legacy workbench APIs; no oracle changes or false PASS.

- [x] U01: Reproduce authored Auction obligations/FAIL with real witness; distinguish
  winner/domain success from deadline/norm/Task requirements. Preserve unknown.
- [x] U02: One stable summary row per actual iStar Goal/Task/dependum definition;
  retain per-context/player/episode details separately. OCL checks in a separate
  table with expression, exact object/invocation and witness; no semantic evaluator.
- [x] U03: Main authored input window removes Export .use/.cmd and Load OCL buttons;
  no observed-prefix navigation in live results. Modeless results window opens on
  successful Start, reopenable, same facade/model, closes with input owner; no
  duplicate polling/listeners/engine. Legacy workbench forensic navigation retained.
- [x] U04: Focused UI/qualification tests, actual external Auction only, package
  byte parity and fresh launcher. Record evidence under verification/live-results-ui.


### Acceptance — detached live results (2026-10-28)

U01–U04 PASS cho yêu cầu audit/UI này. Không có nghĩa mọi requirement của
Auction authored-v1 đều đạt; không đổi normative FAIL/UNKNOWN thành PASS.
Evidence chain: `use-plugin/verification/live-results-ui/final/acceptance.json`.

- Baseline `live-results-ui-baseline.log`: hai test fail đúng requirement
  (Export .use còn trong input; tabs kết quả còn là con của input).
- Final `tests.log`: BUILD SUCCESS, **24 tests, 0 failure/error/skip** = 23
  focused/nearby + 1 actual external Auction. Các test cũ giữ assertions; không
  đổi expected result/oracle/OCL/framework để làm xanh. Không chạy full reactor
  regression hoặc live case khác.
- `AuthoredAuctionResultsIT` dùng external `jacamo/examples/auction/auction.jcm`
  + đúng bốn authored-v1 inputs như ảnh người dùng. Receipt
  `../auction-9535956303843528274/receipt.json`: hai Auction thực sự có winner
  trong cùng MSystem; 140 samples, **15 model Goal/Task/dependum rows cố định**;
  child PID 4660 đã dừng, USE sống. Native OCL thật vẫn ghi một aggregate FAIL.
  `result.json` và `domain-and-observer-state.json` là accepted final manual cut;
  `last-*` là diagnostic samples trước đó, không thay final proof.
- Root cause FAIL đã reproduce: WithinDeadline trên actual AuctionObligation,
  ví dụ activatedAt=6636, deadlineAt=16636, endedAt=16651, unfulfilled=true.
  Producer log vẫn có hai Winner lines. ASL xử lý oblUnfulfilled bằng
  admCommand("goalSatisfied(bid)") cho scheme đi tiếp; không xóa vi phạm đã
  xảy ra. Winner không phải bằng chứng mọi participant bid/Task/norm đã đạt.
- UNKNOWN cuối profile đầy đủ được giải thích từ actual original core:
  XML_TASK_TUPLE_UNAVAILABLE:CURRENT_STEP_MARKER_MISSING;
  các derived goals giữ DERIVED_INPUT_UNAVAILABLE. Raw historical marking
  FULFILLED được giữ trong instance detail, không promote thành current known.
  SchemeOrder/BidEvidence thiếu exact observer fields vẫn SKIPPED/INCOMPLETE.
- Bảng Goal/Task lấy fixed definitions từ real GoalModel, không lấy số dòng
  từ callback/OCL population. Summary bảo thủ current observed instances;
  per-context/player/episode/raw/quality còn trong selected detail. OCL và
  binding checks tách bảng, giữ expression/object/invocation/witness.
- Main authored workflow không còn Export .use/.cmd, Load OCL thứ hai, hoặc
  Observed Prefix navigation. Start success tự mở modeless Results window;
  Show results mở lại. Cùng facade/MSystem/views, không thêm runtime/engine.
  `DetachedAuthoredWorkflowTest` xác minh owner, reuse một window, hide-on-close,
  và dispose khi input owner đóng bằng native Swing peers (không thao tác GUI
  thủ công hoặc cold-installed live). Timer và listener detach vẫn do input
  lifecycle hiện có sở hữu; results window không thêm polling timer.
- Main live presenter dùng captureCurrent(): chỉ immutable current cut +
  in-memory writer tail cho exact same-cut completion; không scan/hash toàn disk
  prefix hoặc retained snapshots. Legacy forensic capture()/journal/replay giữ
  nguyên; UI main không cung cấp navigation nữa theo yêu cầu mới.
- Full authored read-set vẫn nặng: baseline consumer có backlog 1–1.5 phút sau
  source, journal 74–89 MiB/256 MiB, **không có gap/quota overflow**. Không claim
  UI refactor giải quyết toàn bộ verifier throughput hoặc source timing.
  Live test chờ tối đa 180s cho consumer tiếp nhận hai Auction outcomes, vẫn
  assert actual fields/bridge LIVE/no gap, không nới semantics thành log-based
  success. Bản compact-v1 là requirements khác, không tự thay authored-v1.
- `live-results-ui-current-cut.log` giữ baseline timeout100s với native state
  chưa theo kịp. `live-results-ui-drain-gate.log` đạt domain gates nhưng lỗi ở
  test finally đọc coordinator sau stop; sửa diagnostics test nhận monitor đã
  retire, không sửa production lifecycle hay semantic assertions. Chỉ final
  tests.log được tính acceptance PASS. Producer shutdown có thể còn warning
  framework; test exit/process ownership pass không phải claim log sạch tuyệt đối.
- `source-freeze.json` ghi đúng 13 source/test/doc files + 18 compiled UI class
  hashes; bốn original marking/propagation source hashes khớp provenance; bốn
  authored formal inputs giữ nguyên bytes so với compact-update freeze.
- `package.log`: BUILD SUCCESS, skip tests sau focused/live gate. ZIP SHA-256
  `f971c839b30ea0a32dcb75f66695deaf6a379938d51912ee1566c6085cd78f21`. `package-checks.json` xác minh 4 JARs, 18 compiled classes,
  4 original engine classes, updated README/docs và formal inputs trong final
  ZIP và fresh extraction. Không claim manual GUI/cold ZIP live acceptance.
- Launcher mới: `D:\_CODE_BANK\Project_\08_Thesis\use\use-assembly\target\live-results-ui-78alqhom\use-7.5.0\bin\start_use.bat`.
  Plugins → JaCaMo / use4Goal verification...; chọn đủ năm inputs, Prepare +
  Import → Start Runtime; kết quả mở riêng. Click Goal/Task xem instance details;
  click OCL FAIL xem witness. Không cần mở Workbench cũ để xem main results.
- Không commit/push. Các thay đổi có trước task được giữ; không sửa `jacamo`,
  `use4Goal`, normative OCL hoặc semantic assertions của Auction.


## Update — restore state browsing / distinguish original history (2026-10-28)

Latest request restores state navigation in the detached results window; U03's
navigation removal is historical, superseded here. Keep fixed M1 Goal/Task rows,
separate OCL checks, one native state, original engines, fail-closed coverage.

- [x] V01: Reproduce UNKNOWN display and unsupported dependency rows; expose
  original engine aggregate history separately from current qualified evidence.
  Do not turn missing markers or winner log into a qualified result.
- [x] V02: Restore state selector / Previous / Next / failure checkpoint and
  summary/all audit modes in detached results; selected-cut evidence and footer
  remain pinned, user selection survives refresh. Preserve actual accepted state
  commands in the existing bounded hash-chain journal for browsing beyond RAM.
- [x] V03: Restore bottom verdict + completeness/freshness boxes with explicit
  COMPLETE_OBSERVED / INCOMPLETE / VIOLATION / PENDING / NOT_EVALUATED semantics.
- [x] V04: Focused tests, historical payload/replay compatibility, actual external
  Auction only, final package/hash checks and user instructions; no commit/push.


### Acceptance — restored state browser (2026-10-28)

V01–V04 PASS theo yêu cầu mới. U01–U04 và T01–T06 là evidence các revision
trước; việc bỏ navigation ở U03 đã được supersede, không rollback cửa sổ kết
quả tách riêng hoặc mang lại Export/Load OCL dư thừa.
Evidence: `use-plugin/verification/restored-state-browser/final/acceptance.json`.

- UNKNOWN có hai nguyên nhân được đối chiếu source: current Task tuple cần
  exact step marker/PRE/POST; sau step, marker mất thì current quality UNKNOWN
  trong khi original historical marking vẫn FULFILLED/PENDING. Derived current
  quality phụ thuộc các latest inputs nên cũng unavailable. Không đổi original
  engine recurrence, không dùng log winner hay dòng SUCCEEDED trong intention
  chưa chạy làm evidence. UI lấy aggregateStatus trực tiếp, typed originalStatus;
  không parse strings/fuzzy-match để suy ra satisfaction.
- Fixed table nay có **12 actor Goal/Task definitions** cho Auction. Ba social
  dependums D_start/D_bid/D_decide vẫn nguyên trong GoalModel và diagram, nhưng
  original IStarMarking.initial chỉ index gm.allElements (actor elements), không
  có ba runtime markings riêng. Không thêm ba hàng UNKNOWN không có occurrences.
- Hai cột riêng: Original history (raw) / Current evidence. Nhiều observed
  instances có raw statuses khác nhau hiển thị MIXED và chi tiết từng context/
  player; không phát minh AND/ANY hoặc promote raw history thành qualified PASS.
  Current UNKNOWN, coverage GAP/INCOMPLETE và norm violations giữ nguyên.
- Main Results đã có Go to State / Previous / Next / failure checkpoint, summary
  hoặc all audit boundaries, inspect interval. State, evidence, Goal/Task/OCL
  và footer cùng selected cut. Chọn quá khứ không bị refresh tự nhảy về live.
  Modeless results / same facade / one MSystem / fixed rows vẫn giữ.
- Footer đặt lại ở dưới: Verification result và Completeness/freshness. Có
  VIOLATION, INCOMPLETE, PENDING, COMPLETE_OBSERVED, NOT_EVALUATED. OCL PASS
  không làm footer COMPLETE nếu Goal/Task hiện tại chưa có evidence; raw history
  không được dùng để chữa current UNKNOWN. COMPLETE_OBSERVED không phải proof
  toàn bộ JaCaMo.
- Historical SOIL lấy từ frozen.image().soil() của đúng accepted cut, không
  đọc live state mới lúc engine completion publish. Optional acceptedStateCommands
  nằm trong payload ENGINE_RESULT của journal hiện có: cùng quota/hash chain/
  lifecycle/cleanup, hash phải bằng result.stateHash trên append và page read.
  Entry default constructor và recording cũ không có field vẫn đọc được.
  Presenter dùng images theo exact state hash sau RAM eviction, chỉ hiển thị
  text; không tạo hoặc mutate MSystem thứ hai. Recording cũ thiếu image giữ
  thông báo unavailable, không giả ảnh bằng state hiện tại. Quota vẫn fail closed.
- `baseline.log`: hai test fail trước sửa (không có state navigation/footer;
  social dependum bị thêm thành marked row). `focused.log`: **36 tests PASS,
  0 failure/error/skip**; state selection pinned, footer, raw vs qualified,
  images vượt memory tail/reopen và invalid state hash bị reject; existing
  timeline/quota/ownership/provenance/Swing lifecycle và replay-budget tests pass.
  Chỉ sửa assertion UI 'no navigation' để theo yêu cầu mới; không sửa expected
  semantic results. Final footer-only guard được focused test sau live gate;
  runtime/journal/Goal history code không đổi sau live gate.
- `auction-live.log`: **1 actual external Auction test PASS**, không skip. Dùng
  `jacamo/examples/auction/auction.jcm` + bốn authored-v1 files đúng bộ; 124
  samples giữ 12 rows; hai Artifact có winner thật trong cùng MSystem; native
  obligations vẫn có aggregate FAIL. Original core thực sự có FULFILLED,
  PENDING, UNKNOWN; test assert lịch sử FULFILLED được hiện, không đòi mọi Goal
  đều pass. Journal page có frozen state commands; UI actual cuts chọn Previous
  được. PID 25784 dừng, USE sống. Receipt directory:
  `use-plugin/verification/live-results-ui/auction-5174455660464896815/`.
- Cùng live gate export và replay recording mới: **2255 entries PASS**, so state/
  result hashes theo existing replay verifier. `result.json` và
  `domain-and-observer-state.json` là final accepted cut; `last-*` chỉ diagnostic
  samples. `replay-receipt.json` ghi kết quả, không tính log winner thay assertion.
- Tổng relevant gates **37 tests = 36 focused + 1 live/replay**, không failure/
  error/skip. Không full regression, không live case khác. Full authored source
  vẫn có source-to-consumer backlog và missing exact evidence; không claim đã
  tối ưu toàn bộ verifier hoặc làm all requirements đạt.
- `ui-fixture.png` là offscreen native Swing render đã inspect layout (fixture,
  không manual desktop acceptance). Source freeze pin 15 source/test/doc files,
  22 compiled classes; original marking/propagation 4 sources và bốn authored
  formal inputs không đổi. Không sửa `jacamo`/`use4Goal` hoặc source oracle.
- `package.log`: BUILD SUCCESS (skip tests sau gates). `package-checks.json`
  xác minh 4 JARs, 22 relevant compiled classes, original engine classes,
  formal inputs và updated docs trong final ZIP/fresh extraction. ZIP SHA-256:
  `aa4ef3f4b4bf8469df0fb9366e579d1a22046988a841ce60b9e8f46355a2a804`.
- Launcher: `D:\_CODE_BANK\Project_\08_Thesis\use\use-assembly\target\restored-state-browser-lygw_tdw\use-7.5.0\bin\start_use.bat`.
  Chọn năm inputs như trước → Prepare + Import → Start Runtime → Results.
  Dùng Go to State hoặc Previous/Next để xem state cũ; click Goal/Task/OCL để
  đọc evidence cùng cut. Show all audit boundaries mở mọi recorded cut;
  default summary không xóa journal. Không claim manual GUI/cold-installed
  live từ final ZIP. Không commit/push; giữ các thay đổi người dùng có trước.


## W — Certified execution evidence (2026-10-28, latest user request)

Supersedes V's current-cut-only qualification for completed Task / ACHIEVE
occurrences, not original engine semantics or observer/OCL requirements.

- [x] W01 — Runtime-owned completion certificates: exact context / original
  EngineOccurrenceKey (binding stack + episode), run/program/profile-owned ledger;
  preserve only admitted Task/ACHIEVE completion, no certificate from raw history.
- [x] W02 — Same-lane atomic checkpoint/rollback, reset/prune/close; missing current
  marker or later gap retains a historical fact, not current coverage. MAINTAIN,
  SUSTAIN, NONE, current invariants and new episodes never borrow the certificate.
- [x] W03 — Native result / persisted replay evidence / fixed model UI display
  certified execution separately from current quality. Legacy recordings without
  explicit policy select CURRENT_CUT_ONLY only when implementation hashes match;
  cross-binary historical recordings remain strict INCOMPATIBLE (use their pinned
  archive), never bypass hashes or rewrite old evidence. Unsupported policies fail closed.
- [x] W04 — Focused semantics/UI/nearby regression, real external Auction plus
  replay, package updated JAR/ZIP; document executable scope honestly.

Temporary user test override WithinDeadline remains disabled only in authored-v1
OCL + mapping with updated provenance. It does not resolve missing observer fields.
No semantic oracle, original marking/propagation, JaCaMo/use4Goal reference source,
TCP admission or authoritative MSystem is replaced. No commit/push.


### W acceptance — certified execution, source final

Evidence: `use-plugin/verification/certified-execution/final/acceptance.json`.
W01–W04 PASS for this requested change, not a claim all Auction requirements pass.

- `ExecutionEvidence` is run/program-owned metadata on the existing ordered
  runtime lane. Only admitted original Task/ACHIEVE leaf completion creates a
  certificate, pinned to context + EngineOccurrenceKey (binding stack/episode),
  accepted state version/hash. It never creates another MSystem/marking engine.
- `quality` still describes current observation. `executionQuality` and
  `executionEvidence` separately retain certified historical completion when the
  current marker is absent. Parents are qualified from current graph evidence,
  original aggregate/recurrence unchanged. Raw FULFILLED alone is insufficient.
- Ledger joins engine checkpoint/restore and failed-step rollback; identity
  reset/close clears it and live-key pruning bounds it. A new actor/context or
  episode cannot borrow proof; original deactivation/reset retracts its active
  qualification. MAINTAIN/SUSTAIN/NONE are not latched by this ledger. Invariants,
  invocation pre/post, observer checks and current coverage keep their own rules.
- Focused/nearby **38 tests PASS, 0 failure/error/skip**: missing-marker retention,
  no proof from raw history, actor/context/episode separation, ACHIEVE vs other
  Goal types, reset/pruning/ledger rollback, original engine + production renderer,
  derived qualification, reopening UI without cached certificates, state browser,
  native state persistence, timeline and upstream view provenance.
- **One final actual Auction IT PASS** using unchanged external
  `jacamo/examples/auction/auction.jcm` and authored-v1 inputs; one authoritative
  MSystem, two observed winners, 100 samples with 12 fixed rows, no journal gap;
  producer PID 6564 stopped and USE survived. Final sample: 8 FULFILLED,
  4 UNKNOWN (O_root/O_bid/P_bid/P_bid_t2). 3310 historical-qualified rows across
  audited cuts are repeated observations, not 3310 distinct task completions.
  Source evidence: `verification/live-results-ui/auction-16697455635546969058/`.
- New recording **2211 entries PASS**, exact state/result hashes. Final ZIP fresh
  extraction cold replay also **2211 PASS**, isolated driver checks installed
  class origins (no project/plugin classes on application classpath). Driver only
  adds test orchestration. Whole plugin bytes match compiled artifact; six runtime
  classes and four original marking/propagation classes checked in archive.
- Certificate + nested record bytecode now pinned by SemanticProgramManifest.
  New manifest declares CERTIFIED_EXECUTION/1.0. Policy selection tolerates the
  empty projector baseline but cannot switch after occurrence histories exist.
- Historical V recording from a different implementation is **EXPECTED REJECTED**
  with REPLAY_DECLARATIVE_IMPLEMENTATION_INCOMPATIBLE. The hash guard is unchanged;
  use its matching old archive. No cross-binary migration/rewrite is claimed.
  CURRENT_CUT_ONLY default applies only after the normal implementation hash gate.
- Baseline evidence: new ledger regression initially could not compile because
  that capability did not exist. During development the live certificate gate
  passed but replay exposed the too-strict baseline policy selector; root cause
  fixed without loosening semantic assertions. Final tests above were rerun after
  that fix and after provenance pinning; earlier receipts remain historical.
- Package BUILD SUCCESS after tests. ZIP SHA-256:
  `c00b212f9bd5111b8bee1f852d495a15bab442e1b9f6d95bc058598a3d30452e`.
  Close the old USE JVM and launch
  `use-assembly/target/certified-execution-final-20261028/use-7.5.0/bin/start_use.bat`,
  then Prepare + Import / Start Runtime. Do not reuse an already loaded old JAR.
- Current OCL observer SKIPPED and incomplete coverage remain honest; temporary
  user-requested WithinDeadline override is preserved. No all-goals-complete or
  full-source-coverage claim. No full reactor regression, other live case study,
  manual desktop/cold-live GUI acceptance, commit or push was performed here.


## X — Essential CSL milestones, backlog and ANY qualification (2026-10-28)

Latest user scope: reduce Auction business milestones and source noise without
losing required verification evidence; explain/fix the four UNKNOWNs. Only focused
checks and real external `jacamo/examples/auction` live acceptance; no other live
case, full regression, commit or push.

- [x] X01 — Reproduce incorrect OR/ANY quality and internal receiver counting;
  qualify using original propagation over admitted/certified leaf facts, not an
  all-children quality rule. Never assign success to an unsuccessful participant.
- [x] X02 — XML-owned DOMAIN milestone policy and identity-only selections.
  Select Auction business fields plus OrgCtx/role occurrence identity; preserve
  failure/quality transitions in audit with exact failure navigation, not as new
  business states. Keep all audit cuts/state navigation. No fixed production cap.
- [x] X03 — Stop acquiring/projecting unused optional belief extensions for the
  Auction authored profile. First prove native requirement read set does not read
  them. Keep exact step/operation/identity/MOISE/norm evidence. Optional belief
  attributes remain undefined, not fabricated empty/false. Generic infrastructure
  and original semantic oracles stay unchanged.
- [x] X04 — Expose completion delay with exact observed/accepted/verified times;
  distinguish delayed lossless processing from missing data. Measure Auction live
  milestones/audit/delay/journal gap and required evidence, then replay and package.
- [x] X05 — Focused and nearby tests, Auction-only external TCP acceptance,
  final ZIP class/hash and cold replay checks, evidence/status update.

### X acceptance — essential states and correct historical qualification

Evidence: `use-plugin/verification/essential-state/final/acceptance.json`.
X01–X05 are verified for the changes listed above, not a claim of real-time
processing, complete source observability, or all Auction requirements passing.

- Baseline focused tests reproduced two actual faults: OR/ANY demanded unavailable
  alternatives, and internal dependency receivers counted as extra source Goal
  instances. Original use4Goal propagation is now run over admitted/certified leaf
  facts to qualify derived completion. No custom AND/OR recurrence or winner-based
  completion. Original GoalMarking/TaskMarking/IStarPropagation remain unchanged.
- A third live issue was missing current scope rows after valid completion. Existing
  per-run ContextSession now checkpoints its frozen execution view alongside the
  original histories/certificates. Missing current casts can show historical proof;
  they cannot mint proof, evaluate current predicates, or claim complete bindings.
  The result explicitly adds CURRENT_SCOPE_UNAVAILABLE_HISTORICAL_COMPLETION_ONLY.
- DOMAIN policy is declarative, generic and opt-in. This profile selects OrgCtx,
  role occurrence identities and Auction.id/running/task/best_bid/winner/currentWinner.
  Pending results retain policy metadata without inventing an empty world. Full
  audit/replay and exact failure navigation remain available. No 30-state cap,
  truncation or event dropping is used in production.
- Native AST read set proves no active clause reads optional beliefs, unused
  AuctionObligation fields or AuctionMonitor.now. Selected XML omits those native
  projections and optional BB/cycle streams. Raw NPL/clock capability requirements
  remain. CSL/iStar oracles and active OCL bodies are unchanged; the prior user
  WithinDeadline test override remains disabled. Full-observer XML is preserved in
  references/ and packaged; restoring deadline requires clause/binding/hash changes.
  Missing native observer class fails compile rather than vacuously passing.
- Final focused/nearby 41 tests + one real external Auction IT PASS: 0 failures,
  errors or skips in the selected tests. Source was unchanged external
  jacamo/examples/auction/auction.jcm. Two winners, one MSystem, fixed 12 UI rows,
  74 samples, producer PID 28544 stopped, no journal gap. UI browser exercised on
  Swing EDT; this is not a fresh manual desktop acceptance.
- Final run: **13 milestones / 1030 audit boundaries**, **10 FULFILLED / 2 UNKNOWN**.
  O_root/O_bid are FULFILLED. Remaining P_bid/P_bid_t2 include Alice in two schemes
  with no admitted terminal completion certificate; other players keep their own
  completion. 15 TASK_PRE PASS, 13 TASK_POST PASS, no TASK_POST FAIL in this run.
  Source logs report Alice late and dropped intentions. Received bid and winner
  are distinct from completing P_bid_t2. Exact cancellation cause is not inferred
  solely from console. Runs are nondeterministic; failed/missing actors may differ.
- Active BidEvidence/SchemeOrder still lack exact observer inputs and remain
  SKIPPED; current casts can remain unavailable. **INCOMPLETE is still correct**.
  Do not claim these two remaining summary UNKNOWNs are proof of a framework bug
  or change them to PASS because an auction has a winner.
- Real producer observedAt→verifiedAt delay: max **41329.983 ms**, p95 **38867.395 ms**,
  602 producer result samples, manual/baseline excluded. Last cartago observation
  15:23:14.251074900Z verified at 15:23:55.581057800Z. The 48 ms manual check measures
  a local check only. Consumer backlog remains; this change does NOT solve all
  performance bottlenecks. Ordering/hash/gap/admission stay strict. No detected gap
  and exact replay demonstrate consistency of this recorded projection, not full
  observability or a caught-up/end-of-stream guarantee. Console winner precedes
  Jason stop terminal receipt; live test now waits for both real stop receipts.
- Current recording replay **1643 entries PASS**, same accepted state/result hashes.
  Final ZIP fresh extraction isolated cold replay also **1643 PASS**, host libs plus
  test driver only on app classpath; plugin/engine loaded from installed plugin JAR.
  Whole plugin bytes, ten changed runtime/UI class entries, four original engine
  classes, selected inputs/docs/reference XML match build/source. No full reactor
  test suite or unrelated case live run. Package build uses skipTests after gates.
- ZIP SHA-256: `60e61a5b19a93860767a369830b498226875aa707e5b726903e7fbcbfbc7c5a0`.
  Close old USE JVM, launch
  `use-assembly/target/essential-states-final-v2-20261028/use-7.5.0/bin/start_use.bat`,
  choose the same five authored-v1 inputs plus external Auction JCM, Prepare+Import,
  Start Runtime. The new selected mapping must be reimported; old loaded program/JAR
  cannot update itself. All evidence and a 27-file source freeze are indexed in
  acceptance.json. No commit/push or changes to jacamo/use4Goal reference repos.

## Y — Correct selected-requirement completeness (2026-10-28)

- [x] Y01 — Reproduce Resource-as-Goal misclassification and duplicate unavailable
  counts; emit real element kind, preserve resource/task admission, count invariant
  aggregate once while retaining body evidence.
- [x] Y02 — Explicit versioned selected-requirement coverage; keep overall source
  coverage separate. Unknown/unsupported/error/stale/missing still block completion;
  legacy profiles keep strict behavior.
- [x] Y03 — Preserve strict authored XML/OCL as reference; migrate active authored
  observer contract to declared public confirmation semantics reusing existing neutral
  provider. Bid evidence guards on qualified SATISFIED state, not a timestamp.
  No fabricated transition time, no oracle or original marking changes.
- [x] Y04 — Focused/nearby tests, real external Auction and replay, final package /
  cold replay, source/input provenance and honest acceptance evidence. No other live
  case, full regression, commit/push or manipulation of the user's current runtime.

### Y acceptance — selected requirements / explicit public observer v2

Evidence index: `use-plugin/verification/selected-requirements/final/acceptance.json`.
This supersedes X's unavailable strict observer checks only when the user selects
updated authored-v1 mapping + OCL together. Strict inputs/receipts remain intact.

- Three new regression tests reproduced three failures: Resource counted as a
  Goal/Task UNKNOWN, invariant body + aggregate double count, and overall PARTIAL
  source enumeration preventing completion despite qualified selected requirements.
  Runtime now emits actual model elementKind; Resource/Quality/supporting elements
  are not Goal/Task marks. Resources still gate exact Task occurrence/episode through
  original EngineInputs needed-by checks. Five existing engine resource/derived-policy
  tests passed; no engine semantic implementation was changed.
- SELECTED_REQUIREMENTS/1.0 is explicitly emitted by the XML runtime. UI enables
  scoped completeness only on PINNED_NATIVE_CUT + OBSERVED_PREFIX. It still requires
  current freshness, admitted Goal/Task states and all required native/binding checks.
  UNKNOWN/SKIPPED/UNDEFINED/UNSUPPORTED/ERROR/stale remain INCOMPLETE; FAIL/VIOLATED
  remain VIOLATION. Legacy envelopes without policy keep all-source coverage gate.
  Overall source coverage is neither overwritten nor asserted COMPLETE.
- Strict XML/OCL preceding this change are preserved byte-for-byte under
  authored-v1/references/auction-mapping-strict-v1.xml and
  auction_constraints_strict_v1.ocl. Active inputs explicitly declare
  AUCTION_PUBLIC_CONFIRMATION/2.0 (hashes + expected difference in provenance).
  ObservedBidEvidence uses a qualified Boolean SATISFIED fact, not a timestamp.
  ConfirmedSchemeOrder checks first qualified source publication confirmations from
  existing MOISE_PUBLIC_CONFIRMATION/1.0 and source-clock provider. It does NOT prove
  exact internal MOISE truth-transition order. No poll/TCP/return time substitution.
  Missing source clock/prefix remains unknown; no provider/admission guard weakened.
  Native positive/negative tests show true PASS, real counterexample FAIL and unknown
  inputs UNDEFINED when the native Boolean result depends on missing evidence.
- Initial broad focused/nearby run: **48 plugin + 5 semantic-engine tests PASS**.
  Its Auction IT then failed the new gate because the manual cut was temporarily
  partial, while last-result source cuts had both observer invariants PASS. This
  failure is retained in manual-cut-baseline.log. The IT now waits for a completed
  current source cut evaluating BOTH contracts as PASS OR FAIL, never specifically
  for PASS. A timeout still fails. Only this test synchronization changed afterward.
- Final verification reran **5 directly focused tests + 1 real external Auction IT**:
  BUILD SUCCESS, 0 failures/errors/skips. No unrelated live case or full regression.
  Live source: unchanged jacamo/examples/auction/auction.jcm. One MSystem, two winners,
  58 samples, 12 stable rows; producer PID 14652 stopped and USE session survived.
  Receipt: verification/live-results-ui/auction-5275946803128123890/.
- Final live **12/12 Goal/Task FULFILLED**, **5 invariant aggregates PASS**, including
  both observer contracts and three CSL population/compatibility invariants.
  UI verdict **COMPLETE_OBSERVED**; Requirements KNOWN; overall coverage still
  INCOMPLETE. No FAIL. **12 milestones / 1017 audit boundaries**, full audit retained.
  Ten Resource occurrences remain in typed runtime evidence without hidden UNKNOWN
  Goal rows. Milestone count depends on actual source changes, not a runtime cap.
- Native scheme confirmations are actual source callback evidence: start/bid-enabled
  4399/4399 and 5695/5695 ms, bid-satisfied/decide-enabled 11743/11743 and 12829/12829 ms;
  decide-satisfied 14168 and 15316 ms. Equal timestamps mean callback granularity,
  not proof of simultaneous internal transitions. Both schemes carry actual/done/
  committed player sets. Source runtime remains nondeterministic; future failed or
  unobserved participants can legitimately prevent COMPLETE_OBSERVED.
- **1596 replay entries PASS**, exact final state/result hashes. Fresh final ZIP
  isolated cold replay also **1596 PASS** with installed plugin/engine class origins
  checked. Whole plugin bytes, four affected class entries, four original engine
  classes and nine model/input/docs/reference files match build/source.
  Source freeze pins 22 files plus plan hash; CSL/iStar and original marking sources
  also verified unchanged against X freeze. No reference repo edits or commit/push.
- ZIP SHA-256: `75dd232d328696b3bd9617ecd7f77dad385a257a2527554f928cfbad800b5442`.
  Launch fresh `use-assembly/target/selected-requirements-final-20261028/use-7.5.0/bin/start_use.bat`.
  Close old USE and reimport updated mapping/OCL together with unchanged authored
  CSL/iStar and external Auction JCM. No already-loaded JVM/program auto-update.
- Limits: WithinDeadline remains user-disabled; exact internal transition strict
  profile is still unavailable; full JaCaMo internals are not certified. Backlog
  remains (final source-to-verification delay 32188 ms); this is not a realtime
  performance fix or caught-up guarantee. No manual desktop/final-package live GUI
  acceptance is claimed; live test uses production facade/presenter and Swing EDT,
  and final-package gate is isolated cold replay. Other user runtime was untouched.

## Z — Terminal failure evidence and business-only milestones (2026-10-29)

- [x] Z01 — Retain qualified source FAILED task receipts on the existing run/context/
  exact occurrence/invocation ledger, atomic checkpoint/rollback/prune/reset. Keep
  original TaskMarking and propagation unchanged; unknown must never become failure.
- [x] Z02 — Present FAILED separately from original raw PENDING; aggregate fixed
  model rows without crediting another actor/episode or poisoning a satisfied ANY.
  Derived parents use original marking/quality, not a new failure recurrence.
- [x] Z03 — Remove pending/bootstrap/unqualified audit-only cuts from XML DOMAIN
  milestone summary. Display only selected admitted CSL changes; no raw SOIL fallback
  in domain preview. All cuts, exact failures and detail/replay remain available.
  Preserve legacy profiles, no case-name/string-command filters or fixed state cap.
- [x] Z04 — Focused/nearby tests, real external Auction + replay, final package and
  isolated ZIP replay, source/evidence freeze. No other live case, full regression,
  commit/push, reference source modification or user runtime interference.

### Z acceptance — terminal source outcomes and XML business-only milestones

Evidence: `use-plugin/verification/terminal-milestones/final/acceptance.json`.
Z01–Z04 PASS for this requested scope. No general whole-process correctness claim.

- Source failure is recorded only when the existing generic XML checkpoint gate
  supplies an exact source FAILED marker, matching certified native PRE/POST tuple,
  active qualified demand, same scope/player/role interval/episode and a known false
  POST. A missing marker, false predicate alone, ERROR/UNSUPPORTED or partial source
  cannot create a terminal receipt. No Auction name, goal/agent name or SOIL command
  filter is used in the new production logic. Source selection stays in XML.
- Existing ExecutionEvidence ledger/checkpoint map now holds TASK_FAILED receipts
  under CERTIFIED_TERMINAL_EXECUTION/1.0 with exact invocation/state version/hash.
  It participates in existing atomic rollback, topology pruning, epoch reset/close;
  a retry/new occurrence cannot borrow it. Conflicting success/failure for one exact
  key fails closed. No second MSystem, marking engine, transport or listener.
- Runtime presents executionStatus FAILED independently from raw original TaskMarking
  PENDING and persists EXECUTION_OUTCOME FAIL with the certificate. This prevents a
  known failure reverting to UNKNOWN after marker retirement or disappearing from
  runtime results. Source Goal/Task summary can show FAILED; derived parents retain
  original propagation (e.g. PENDING), and an ANY with a successful alternative is
  not rewritten into a failed original Goal. Original marking sources are unchanged.
- DOMAIN summary uses admitted XML presentation metadata. Pending/unqualified cuts
  and initial empty bootstrap are audit-only. Removal of the last selected object
  after a populated world remains an actual CSL state. Creation/removal/role links/
  selected attributes remain meaningful states, even when their native SOIL contains
  !new/!insert. Default view displays selected CSL values; explicit audit details keep
  full native SOIL/image. All audit cuts, exact failure button, history and replay
  remain intact. Legacy/DOMAIN_AND_EXECUTION profiles keep their prior selection.
- Baseline: new ledger test initially could not compile because terminal receipt API
  was absent. Executable timeline regression then reproduced an unwanted initial
  pending milestone containing infrastructure SOIL. Both logs retained. Additional
  test fixture repairs used a valid original two-child AND and a pure callback with
  EXACT_SELF for deterministic failure; no semantic oracle/production source behavior
  or expected PASS was relaxed to hide a failure.
- Final **64 plugin focused/nearby tests + 5 engine resource/derived tests PASS**, zero
  failures/errors/skips. Includes deterministic real Jason .fail callbacks through
  production XmlTaskCheckpoints + native OCL + RuntimeEngineSessionOwner: terminal
  FAIL persists after marker retirement; another actor is not credited; owner and
  ledger rollback/restore/pruning are checked. Renderer-only raw Pending or an
  unverified FAILED flag cannot create a failure. Generic fixture uses different
  model/task/actor identities from Auction. OR/AND semantics and missing facts stay
  under original propagation. New pending-preview and empty-population removal
  regressions PASS, workflow/source provenance gate PASS.
- **One final actual external Auction IT PASS**, unchanged
  jacamo/examples/auction/auction.jcm. One MSystem, two winners, 68 samples, 12 fixed
  model rows; producer PID 18832 stopped and USE session survived, no journal gap.
  Actual run: **12 business milestones / 1025 audit boundaries**, no raw SOIL in
  default business preview. **12/12 Goal/Task FULFILLED**, selected requirements
  COMPLETE_OBSERVED, overall source coverage remains INCOMPLETE as before.
- This nondeterministic live run had **zero failed task occurrences**; it is NOT
  evidence of a live Auction FAILED branch. That branch is independently established
  by deterministic real Jason callback/native owner component acceptance above.
  A later source .fail is expected to show FAILED and a retained execution failure,
  rather than claiming all tasks fulfilled because another participant won.
- Current recording **1617 entries replay PASS**. Fresh final ZIP isolated loader
  replay also **1617 PASS**, identical accepted state/result hashes and installed
  plugin/engine class origins. Whole plugin bytes, nine relevant class entries,
  four original engine classes, nine input/docs/reference files match build/source.
  Source freeze pins 23 files + plan hash. Unchanged semantic input receipt proves
  CSL/iStar/mapping/OCL and original marking/propagation are unchanged since Y.
- ZIP SHA-256: `e55880423ef2278e6b265026ec500c5e06611ceafe1aef782505a25cb5d8acf3`.
  Stop owned runtime, close old USE; launch
  `use-assembly/target/terminal-milestones-final-20261029/use-7.5.0/bin/start_use.bat`,
  choose the same five authored-v1/external Auction inputs and Prepare+Import/Start.
  Do not reuse an old loaded JVM or replay a recording with incompatible binary.
- Limits unchanged: source-to-consumer backlog still exists (recorded max around
  37.7 seconds; final 13 ms manual check is local, not producer lag). No realtime or
  stream-drained guarantee. Strict internal transition-time profile remains limited,
  deadline remains user-disabled. No unrelated case live/full regression/manual
  desktop or installed live GUI acceptance, commit/push, reference repo modifications
  or interference with the user's separate running runtime in this change.


## AA — Eliminate case-specific production semantics and close completeness loopholes

Latest request: prohibit case-study hardcoding. Framework API/protocol identifiers and
explicit XML/OCL/example data remain valid; case-specific Java execution is retired.
No reference-repository changes, commit/push, unrelated live cases or oracle edits.

- [x] AA01 — Reproduce helper-context authored OCL false COMPLETE and missing-contract
  omissions. Replace context whitelist with frozen native contract inventory; every
  authored/CSL requirement stays mandatory, unavailable is never PASS.
- [x] AA02 — Remove compiled legacy case-specific mapping/observation routes, hooks,
  classes and fallback. Explicit retired profile/recording requests fail closed with
  migration diagnostic; generic XML and framework/native paths remain. Preserve
  historical source/evidence as nonproduction reference where needed.
- [x] AA03 — Architecture guard across production modules; neutral XML-driven Case B,
  missing capability/unsupported/quality and focused runtime/UI regressions.
- [x] AA04 — Actual external Auction, fresh replay, package and isolated installed
  replay; freeze source/evidence. No full-source completeness or all-case guarantee.

### AA closeout — PASS for no-case-production scope

Evidence: `use-plugin/verification/no-case-production/final/acceptance.json`.

- Reproduced real false COMPLETE: an authored native helper-context invariant was
  SKIPPED but excluded by context-name filtering. Native contract inventory now
  identifies authored/CSL requirements by exact IDs and provenance; missing aggregate
  rows also block completion. Supporting backend helper checks are explicit, never
  an exemption for authored OCL. Any actual FAIL still yields VIOLATION.
- Removed 37 case-specific Java production classes, old provider hooks, facade/model/
  projector/owner/replay branches and CLI flags. XML uses the same existing generic
  execution lane and authoritative MSystem. `declarativeMapping/applyMappingCut` now
  name that actual neutral seam. No replacement case graph, field/agent/task/operation
  constants or fallback were added. All production Java in plugin/producer/engine
  and the launcher pass the new source/compiled-class architecture guard.
- 114 historical files (37 production Java, 76 tests, one vocabulary) are retained
  in `docs/agent/retired-case-java` with hash manifest, outside Maven compilation and
  distribution. Their assertions are not weakened or counted as current coverage.
  The metadata oracle adapter used by one parser comparison exists in TEST sources
  only. Old case profile/dialect replay is explicitly unsupported; retain the old
  distribution for old recordings, or reimport through declarative XML. No silent
  old-recording reinterpretation/hash bypass. Native/explicit JSON routes remain.
- Focused regression PASS: 98 plugin + 9 producer tests, zero failures/errors/skips.
  Includes XML-driven Case B static/M0/native OCL, absent/partial/error/unsupported
  gates, native helper authored OCL, missing required aggregate, checkpoints/@pre,
  exact failed Jason task/rollback/history and UI. Regression also caught shared
  neutral capture calls inside the retired observer wrapper; those calls now live
  in independent captureCapabilities at the same framework boundaries. Workbench
  also now honors an explicit configured strict-checksum choice; authored main UI
  retains its flexible default. No semantic assertions or source oracles changed
  to force green. Tests for removed public API/profile were migrated explicitly.
- Actual unchanged external `jacamo/examples/auction/auction.jcm` IT PASS, 68 samples,
  same MSystem, two completed auctions, 12 fixed model rows, 12 domain milestones /
  994 audit boundaries, no journal gap. Owned producer PID 21560 stopped; USE survived.
  This run has two actual certified terminal Task failures, so selected verdict is
  VIOLATION (P_bid_t2 FAILED; parent P_bid original PENDING). Organization ANY goals
  can still be FULFILLED. This is correct observed verification, NOT an all-green
  case claim. Source coverage remains INCOMPLETE; maximum/final pinned source delay
  37649 ms is completion backlog, not a stream-drained or realtime guarantee.
- Fresh source replay PASS 1568 entries. Fresh final ZIP isolated-loader replay PASS
  1568, identical final native state/result hashes, installed plugin/engine origins.
  ZIP SHA-256 `0596d054a299e278f9be39b5b2733494ea37bf0c29e19a2149844314f4df08de`.
  Both packaged production JARs have zero retired Auction classes; current build
  bytes and four original marking/propagation classes match. Four authored input
  files match source. Install: `use-assembly/target/no-case-production-final-20261029-0153/use-7.5.0`.
- Final source/asset/plan hashes are recorded in source-freeze.json. Post-gate edits
  only restore incidental test-file line endings / trim trailing whitespace (no
  token or line-number changes) and update docs/evidence. No original engine edits,
  reference-repository changes, full reactor regression, other live case, new
  desktop GUI exercise, branch/worktree, commit or push in this scope. Case B is a
  component/metamorphic proof, not proof of every possible future case/capability.

- Additional launcher/managed-workflow/architecture gate PASS: 10 focused tests,
  zero failures/errors/skips (launcher-focused.log). Retired profile/flag assertions
  now enforce absence and the neutral observation-plan input. No domain assertion
  was relaxed. Final recompile matches all 771 packaged plugin and 116 producer
  classes byte-for-byte. The 37 other plugin target outputs are excluded by the
  EXISTING maven-jar-plugin rules for legacy text extraction/project discovery;
  these are not new or unexpected package omissions. Exact inventory is retained
  in final-bytecode-check.json. No executable change after package/live/replay.


## AB — Declarative attempt completion versus accepted bid and requirement violation

Explicit user semantics: a participant completes the selected decision/attempt step
whether its source-selected outcome succeeds or takes the declared simulated failure;
organization completion still needs at least one real accepted bid. Failure evidence
is retained; execution failure alone is not a verification violation. Source/reference
repos unchanged. No case rules in Java; exact failure acceptance and requirement
selection are declarative, no blanket accept-any-error behavior.

- [x] AB01 — Regression: actual source failure site observation, exact declared
  accepted termination vs other failures/missing proof; native predicate false and
  raw execution failure cannot independently create VIOLATION.
- [x] AB02 — Neutral callback tuple/accepted-failure binding and selected intentional
  requirement targets; preserve original marking, quality, lifecycle and native OCL.
- [x] AB03 — Version authored Auction contracts explicitly: participant attempt,
  collective accepted bid evidence, ANY, independent OCL; update hashes/provenance
  without editing historical oracles/strict references.
- [x] AB04 — Focused/nearby regressions, actual external Auction, package + fresh
  isolated replay and source freeze. Deterministic negatives cover all-failed attempts,
  unrelated failures/actors, genuine OCL FAIL, missing evidence and unsupported rules.

AB01–AB03 evidence (2026-10-29): `verification/attempt-semantics/final/focused-and-live.log`
contains 88 plugin + 2 producer focused tests, zero failures/errors/skips. Covers
actual nested Jason `.fail` source site, original TaskMarking fulfillment for the
exact XML-accepted failure, no credit to another player, missing proof SKIPPED,
wrong instruction or framework error not accepted, all attempts without actual
acceptance cannot fulfill the collective AND, genuine native invariant FALSE still
VIOLATION, and missing selected root cannot COMPLETE. ProductionCaseIsolationTest
passes for all production mapping/provider/engine Java; original marking source
is unchanged. XML `acceptedFailure` requires exact instruction/type/line AND public
callback `sourceFailure`, with the decoder enforcing the selected actual source hash.
The case v3 adds O_bid_attempt/O_bid_accepted and chooses O_root as the result
requirement; all authored native invariants/operation contracts remain mandatory.
Old success-only iStar/XML are byte-preserved in authored-v1/references; strict
observer references and CSL/OCL bodies are unchanged. The old model hash assertion
now checks that preserved reference, and its stale OCL checksum is corrected to the
already user-authorized current OCL bytes. These are declared contract differences,
not weakened production assertions. Previous AA live receipts describe the earlier
success-only interpretation; they are not current final verdict evidence.
AB04 PASS — `use-plugin/verification/attempt-semantics/final/acceptance.json`.
Final focused+actual Auction verify BUILD SUCCESS: 88 plugin tests, 2 producer
and 1 actual external Auction IT, zero failures/errors/skips. Live evidence:
`use-plugin/verification/live-results-ui/auction-10849287875472857408`.
Actual maria/alice .fail callbacks BOTH match instruction/internalAction/line41,
source hash and exact public failure code; TASK_POST PASS means attempt completion,
not invented successful Artifact operations. Two source Auctions finish, all 14
model Goal/Task rows FULFILLED, selected verdict COMPLETE_OBSERVED, no native FAIL,
13 domain milestones / 1019 retained audit boundaries. SAME authoritative MSystem;
producer PID 8376 was stopped by the test. Source replay PASS 1604 entries.
Final package BUILD SUCCESS, ZIP SHA-256
`86fd4e75be581229ba38c50854129598514b089955cf5fc009a4f7ac919c6b53`.
Cold isolated replay from that extracted ZIP PASS 1604 entries and same final
result hash `3006ce2f7431e98402486f5e1f1cf23a7b887005c10e8ad8da105bb90293e407`.
771 plugin + 116 producer packaged classes match current target byte-for-byte;
all four original marking/propagation class hashes match pre-change package.
All 11 authored bundle/reference files equal package bytes. Source freeze records
644 source/assets/tests; mutable plan checkbox text is explicitly excluded with
its initial hash retained. Reference JaCaMo Auction sources unchanged.
First successful live run is retained separately; the final rerun follows exact
failure-reason hardening, not a rerun to seek a lucky outcome. Deterministic wrong
instruction/reason, missing proof, all attempts/no accepted outcome, wrong actor,
native false and selected-root missing negatives all PASS.
Limits: focused Swing main workflow tested, no new manual desktop GUI exercise;
source-to-verification max 34820 ms remains honestly reported (not caught-up proof).
Overall source coverage stays INCOMPLETE; current active selected requirements are
known. Previously disabled WithinDeadline remains disabled; strict references are
unchanged. No unrelated case live tests/full clean reactor, branch or commit/push.


## UI tabs — simplified authored results

- [x] UI01 — Keep Execution & Results / CSL model / iStar model as primary tabs; nest Projection Rules, Program / trace, Observation plan and Verification Details under Advanced / Diagnostics. Preserve legacy Workbench and existing lifecycle. Focused UI tests: 33 PASS, 0 failures/errors/skips. Package BUILD SUCCESS. Evidence: `use-plugin/verification/ui-tabs/receipt.json`. Final ZIP and extracted JAR contain exact current workbench class. Layout-only update; no fresh live acceptance needed or claimed. Earlier AB package hashes identify the preceding build; the current UI ZIP hash is in this new receipt.


## Auction cleanup / Git checkpoint

- [x] Remove retired Java-profile case-rules XML; update pinned asset inventory (21 assets), assembly/release metadata, and current five-input README. Preserve active profile/oracle/provenance/license fixtures. Focused Auction/UI/package contract tests: 25 PASS, zero failures/errors/skips. See `use-plugin/verification/auction-cleanup/receipt.json`. Raw verification logs/recordings remain local; source/test/docs and selected receipts form the Git checkpoint. No verification semantics changed by cleanup.


## Clean Auction layout

- [x] Move old profiles/oracles/provenance to `use-plugin/src/test/resources/auction-reference`; promote current inputs to `use-plugin/examples/auction/{auction.csl,auction.istar,auction-mapping.xml,auction.ocl}`, with project/source and README. Exact source/model/contract bytes retained. Tests, docs, package inventory and acceptance script paths updated. 51 focused tests PASS; package BUILD SUCCESS. Final ZIP has active bundle `examples/auction-current`, fixtures `reference/auction`, zero stale `examples/auction-semantic` entries. Old native toy ZIP path remains compatible. Stage and ZIP byte checks PASS, formal inputs pinned with -text attributes. Evidence: `use-plugin/verification/auction-layout/receipt.json`. No semantic change or fresh live acceptance claim.


## UI instance-state views — AOL / ISCN

- [x] SV01 — Audit original use4Goal IStarScenarioView/IStarScenarioViewModel and AOL renderer. Reuse AOL diagram/layout sources with pinned license/source/target hashes; replace legacy CslSystemState parameter with immutable accepted-state presentation DTO, never a legacy evaluator. Provenance: META-INF/use4goal-state-views-provenance.json.
- [x] SV02 — Selected immutable Cut carries its observation payload; state navigation notifies read-only views and empty history clears them. AOL uses XML-selected CSL instance values plus exact retained native binary links. ISCN uses each recorded context's source/actor/player/role/kind trace and original instance expansion/renderer. Require bijective topology agreement, borrow qualified markings; missing raw fulfilled cannot create a badge. No extra MSystem/OCL/marking engine or case constants.
- [x] SV03 — Main tabs Execution & Results / CSL state (AOL) / iStar instances (ISCN). Advanced contains model definitions and Verification Details only; original debug Workbench unchanged. Player display names short; context/player identities preserved in evidence.
- [x] SV04 — 42 focused UI/history/architecture tests PASS, no failures/errors/skips. Genuine recorded Auction cut covers 2 contexts, 6 actor instances per context; neutral Parcel case verifies attributes and links; missing evidence UNKNOWN; navigation/reset and original view provenance/lifecycle pass. Rendered AOL/ISCN images inspected. Evidence under use-plugin/verification/state-views. No fresh manual desktop GUI/live producer run claimed; these are presentation adapters over already verified cuts, with semantics unchanged.


## Auction system specifications / model-native OCL

- [x] MS01 — Read both current Desktop Draw.io files recursively (cells, ownership, edges, embedded cslSource and semanticAudit). Preserve copies/hashes in auction-reference/diagram-v4. Explicitly record contradictions: capability Role versus OrgCtx/Agent labels, stale task owners/participates-in, strict success-only versus previously user-approved attempt semantics. Historical v4 default retained capability Role/specialization pending clarification; user has now explicitly selected Agent/OrgCtx. Superseded by AC01–AC03 below. Do not claim all visual metadata agrees or graph is a verbatim Draw.io copy.
- [x] MS02 — Current CSL structure matches embedded Draw.io cslSource; operation OCL resides in explicit @use-native-ocl 1 block. iStar keeps original Goal/Task predicates and Artifact Resources, with observer contracts in versioned block. Optional .ocl is comment-only; XML AUTHORED binds source/identity/context/field/operation/task/goal/capability, has no model-generation actions. Current hashes updated.
- [x] MS03 — Implement generic ModelNativeContracts collection from pinned CSL/iStar comments plus optional external native OCL; original DSL parsers and native USE compiler remain unchanged. Empty optional file allowed; wrong annotation version, non-Boolean, duplicate clauses and missing XML bindings fail closed. RequirementInventory includes embedded clauses. Replay implementation fence includes new adapter. UI labels distinguish runtime project input from hand-authored system specs.
- [x] MS04 — Final focused regression + actual external Auction using current root specification files, source replay, package/cold installed replay and exact byte/hash checks. Only tick after executable receipts. Source freeze and evidence in verification/model-native-contracts. No commit/push.


MS04 evidence: verification/model-native-contracts/acceptance.json. Final focused
47 tests PASS + 1 actual Auction over TCP PASS, same MSystem, zero native failures;
optional .ocl comment-only, all 7 embedded native clauses compiled and installed,
CSL structure matches pinned Draw.io cslSource. Live selected root COMPLETE_OBSERVED,
11 domain milestones / 1022 audit boundaries. Participant raw/qualified UNKNOWN
remains visible where no exact terminal marker was admitted; no blanket success.
Source replay and isolated installed-JAR replay PASS 1611 entries with identical
final result hash 13a3cce31adade6e855c14bb413e761361cb46e22ff4cde465837293145ae8cb.
Package BUILD SUCCESS; current Auction files equal final ZIP bytes; relevant
native/UI classes match tested targets. Original GoalMarking/TaskMarking/
IStarMarking/IStarPropagation unchanged; Desktop source hashes unchanged.
This MS04 receipt describes the historical v4 default. User clarification now
selects Agent/OrgCtx; AC01–AC03 below supersede its model and runtime bindings.
No claim of verbatim graph equivalence to contradictory diagram metadata. Deadline remains disabled; no manual GUI/full-source completeness claim.

## User-selected singleton Agent/OrgCtx revision (supersedes MS01/MS02 defaults)

- [x] AC01 — Capability is a concrete iStar Agent, one occurrence per exact physical player; Tasks/resources move inside Agent, Role responsibilities delegate by SAME_PLAYER. CSL capability becomes a separate singleton-agent OrgCtx, not Role inheritance. Keep original unary-realization admission profile and marking core; no fabricated operation success.
- [x] AC02 — Neutral singleton context representation, same-player dependency expansion and Agent interval/checkpoint binding. Preserve exact run/incarnation/scheme/invocation, rollback and unavailable semantics; no case constants in Java. Focused negative tests must forbid cross-player credit and missing context evidence.
- [x] AC03 — Actual Auction TCP with current specifications, same MSystem, task checkpoints, native contracts, source replay; rebuild distribution and verify package bytes. Update README/evidence after executable gates.

Bid policy: completed participation attempt and accepted Artifact bid are distinct. Exact simulated terminal failure may close the attempt; it never proves accepted bid. Non-improving operation success is still an attempt. Administrative scheme progress alone never proves an accepted bid.

AC01–AC03 executable evidence: `use-plugin/verification/single-agent-context/acceptance.json`.
61 focused tests pass (3 semantic topology adapter + 58 plugin tests), no failure/error/skip.
Actual external Auction final gate: `model-native-contracts/single-agent-diagnostic-live.log` BUILD SUCCESS;
`live-results-ui/auction-293052928902650835`: same authoritative MSystem, five distinct singleton
Agent contexts linked bijectively to physical AtomicAgent objects; exact same-player responsibility
and Task stop certificates; original collective root FULFILLED; selected COMPLETE_OBSERVED;
no domain failures; both observer invariants PASS. Source replay and isolated final installed-JAR
replay PASS 1601 entries, identical final result hash
88399046ab97842f2f493d299eb6e18dfd82b443ffd3d3c13fae05816168bff7.
Package BUILD SUCCESS; active Auction bytes and tested classes equal ZIP; original marking/propagation
class hashes unchanged. Source freeze covers final production/tests and read-only project/diagrams.

Limits retained: capability Agent declares all skills; tasks not demanded/executed for a player
remain UNKNOWN, not falsely fulfilled. XML selects the collective requirement; COMPLETE_OBSERVED
is not all capabilities of all agents or full source coverage. OrgContext helper population invariant
can remain SKIPPED for POPULATION_UNPROVEN. Historical failed live attempts are retained, including
source cast PARTIAL; no quality/Stop assertion was disabled to obtain the successful final run.
No fresh manual desktop GUI test, no commit/push.

## Result scope and instance evidence presentation

- [x] RV01 — Display selected Goal/Task requirements explicitly; keep raw/certified summary and UNKNOWN unchanged. Mark supporting definitions, show counts by certified status; no unsupported N/A inference.
- [x] RV02 — Inspect exact player/context/occurrence evidence from presenter metadata, never parse identities from free text. State navigation/reset clears stale details; original verdict/marking/mapping remain unchanged.
- [x] RV03 — Focused scope/summary/history/negative tests, visual render inspection, package build and final ZIP class-byte check. No repeat of unrelated live cases/full regression for this presentation-only change.

RV01–RV03 evidence: `use-plugin/verification/result-scope/receipt.json`.
Only 3 UI sources and their provenance metadata changed since the Agent/OrgCtx freeze.
Fixed an existing UI footer guard: OCL-only legacy/direct cuts with missing declared Goal/Task,
and explicit selected targets with no occurrence, must be INCOMPLETE. Reproduced by the existing
RestoredStateBrowserTest without changing its expected result; baseline failure retained.
37 focused tests PASS, zero failures/errors/skips, final package BUILD SUCCESS. A pinned genuine
Auction cut verifies 2 fulfilled Start instances and 8 UNKNOWN skill instances; identity/evidence
remain exact and original result hash is unchanged. Rendered images inspected. Backend semantic
and original use4Goal class bytes equal previous accepted installed package; Auction specification
bytes equal final ZIP. No fresh live producer or manual desktop GUI test claimed for this UI-only
change. No commit/push.


## Enactment views and failure navigation

- [x] EV01 — Validate explicit singleton-context enactment mapping; render M1 possibilities and frozen M0 actual-player links without changing marking semantics.
- [x] EV02 — Failure Point selects chronological Goal/Task, OCL and diagnostic points with exact evidence; simplify main navigation.
- [x] EV03 — Focused negative tests, Auction live/replay, visual inspection and final package/source receipt.

EV01–EV03 evidence: `use-plugin/verification/enactment-navigation/receipt.json`.
43 focused tests PASS; 12 relevant view/navigation/isolation tests re-run PASS after final UI geometry changes.
Actual external Auction + source replay PASS (1618 entries, 19 domain milestones).
Final isolated installed-JAR replay PASS with exact source-manifest final state/result hashes.
114 live implementation resources/classes match final archive; 110 tested UI classes match package;
297 original engine class bytes equal prior accepted package. Frozen same-cut enactment joins are
checked against actual CslAgentContexts and Plays links for all 5 players. No cross-player
inference; removed Role changes current view but cannot erase a frozen historical view.
Failure selector distinguishes Goal/Task, OCL and Diagnostics; missing evidence is not a failure.
Default interval inspection removed from main live results; audit remains optional.
Original marking/selected requirements unchanged. New source freeze and read-only source hashes
verified. Source/service acceptance and offscreen visual QA, not a fresh manual desktop GUI claim.
Prior failed logs retained (test render harness defaults, compile-time UI generic type), no semantic
assertion weakened. No commit/push.

## Failure Point milestone labels
- [x] FP01 — Default failure labels use associated business milestones; exact failure evidence retained; Audit details alone switches to boundary numbering.
- [x] FP02 — Focused navigation/grouping tests, package and source-byte checks; explain milestone scope from actual presentation specification.

FP01–FP02 evidence: `use-plugin/verification/failure-milestones/receipt.json`.
33 focused tests PASS; package BUILD SUCCESS. Default dropdown and selected-state caption
use the associated milestone; Audit details explicitly switches to exact boundary numbers.
Exact failure cuts/evidence remain unchanged, including after background refresh and mode
switches. Repeated state phases and pre-first-state diagnostics have negative tests.
114 accepted live backend resources/classes and 297 original engine classes match final
package. Only 2 UI sources and workflow attribution metadata changed in production.
No new live MAS/replay or manual desktop GUI claim for this presentation-only adjustment.

## Population correctness repair
- [x] PC01 — Transitive derived-attribute AST dependencies: exact receivers, cycle/pre-state fail-closed handling, shared coverage/read-set scheduling; partial roles cannot become PASS/FAIL.
- [x] PC02 — Persistent failure episodes across pending/unknown cuts; qualified recovery only; merge aggregate/body display without dropping object witnesses.
- [x] PC03 — Explicit XML activation for an authored invariant after qualified source roster and matching materialized context; latch per context incarnation, keep later complete-empty failure. Original invariant predicate unchanged.
- [x] PC04 — Focused negative/regression tests, external Auction only, source and installed replay, final package/provenance/source freeze. No full clean verify or unrelated case study.

PC01–PC04 evidence: `use-plugin/verification/population-repair/receipt.json`.
51 focused tests and 19 package/navigation/provenance/isolation tests PASS. Actual external
Auction PASS with 17 milestones / 1045 audit cuts, same authoritative MSystem and unmodified
source; zero startup population FAILs, qualified waiting observed, operational population
body and aggregate genuinely PASS with source activation certificate. Source and final
isolated installed-JAR replay both PASS 1655 entries and identical final state/result hashes.
119 live implementation resources/classes equal final package, 110 tested UI class bytes
equal package; 297 original engine classes equal the prior accepted archive. Predicate
size() = 1 unchanged; activation is explicit mapping semantics, not inferred from outcome.
Negative tests retain genuine complete-empty/removal failures and unavailable evidence;
cycle/@pre receiver cases fail closed. UNKNOWN/MIXED of supporting skills stay unchanged.
Final source freeze verified. No fresh manual desktop GUI claim, unrelated live cases,
full clean verify, commit or push.

## Project-first specification folder selection
- [x] UI01 — JCM first; one model-folder picker; exact direct-child CSL/iStar/XML required, external OCL optional; reject duplicate/missing types and revalidate before preparation.
- [x] UI02 — Automatically select producer classpath file, retain manual Runtime libs override; explain compatibility and model input modes.
- [x] UI03 — Focused folder/UI/optional-OCL tests, package and visual QA. Preserve model predicates and runtime semantics.

UI01–UI03 evidence: `use-plugin/verification/folder-inputs/receipt.json`.
40 focused folder/UI/input/native-model tests PASS; package BUILD SUCCESS. Missing/duplicate
required types and duplicate optional OCL reject; arbitrary filenames and nested-file exclusion
verified. Prepare rescans the selected folder before producer creation. Omitted external OCL
freezes as an empty asset in effective XML (original file unchanged); all 7 embedded Auction
contracts remain installed, supplied OCL checksum mismatch still rejects, frozen inputs replay
round-trip verified. Source and installed-JAR offscreen Swing render inspected; 111 UI class
bytes and changed backend classes equal final package, 297 original engine classes unchanged.
125 entries in the automatically selected producer classpath exist. Runtime libs/manual override
preserved. Script syntax PASS. No fresh live MAS/manual desktop exercise, full clean verify,
other case-study execution, commit or push for this input-selection change.

## Per-execution/entity verification views
- [x] EX01 — Audit exact scope/entity bindings and two independent engine contexts; freeze display-only native entity references under the writer barrier.
- [x] EX02 — Separate execution selector, scoped Goal/Task summaries and domain milestones; retain whole-state OCL/run verdict visibly; synchronize ISCN context labels and selection.
- [x] EX03 — Negative isolation/history/navigation tests, actual external Auction only, replay/package and visual QA; no case-name inference or engine marking changes.

EX01–EX03 evidence: `use-plugin/verification/execution-scopes/receipt.json`.
Audit found two already independent engine contexts; UI definition-wide aggregation and
anonymous context labels caused ambiguity. Added exact scope/native Entity view receipts
under the writer barrier, execution selector, per-scope Goal/Task summaries and domain
milestones; ISCN labels and selection synchronize by exact context ID. Missing selected
context clears the graph, never substitutes another. AOL and OCL retain full shared state.
62 focused tests PASS; actual external Auction PASS with same authoritative MSystem,
unmodified source, two distinct Entity/root bindings and 5 milestones per execution.
Source and final isolated installed-JAR replay PASS 1608 entries, identical final hashes.
119 live implementation entries equal final package; 113 tested UI class bytes match;
297 original engine classes unchanged. Source freeze707 checked; packaged case bytes equal
source, with no case specification edits. Both original Swing ISCN diagrams and Execution
views rendered/visually inspected. No fresh manual desktop GUI claim, other live case
studies, full clean verify, marking changes, commit or push. Preserved failed harness logs
for first-combo selector and missing explicit test quantifier configuration; semantic
assertions were not weakened.

## Strict single-execution Goal/OCL/AOL views
- [x] SX01 — Remove aggregate execution selection; keep independent per-context root identities and explicit Entity labels.
- [x] SX02 — Native OCL checks scoped to explicit bound owners/observer subjects, retain shared structural constraints, missing/ambiguous bindings fail closed.
- [x] SX03 — Scoped AOL graph with witnessed execution participation links; synchronize all selectors without inferring membership from shared group.
- [x] SX04 — Focused isolation/quantifier/negative UI tests, external Auction only, source and installed replay, final source/package/visual evidence.

SX01–SX04 evidence: `use-plugin/verification/single-execution/receipt.json`.
Two independent native root/Entity bindings now drive one mandatory execution selector;
there is no aggregate option. Results, AOL and ISCN synchronize exact context identity.
Native OCL outer owners are scoped by explicit bindings; a private monitor matches
program/epoch/project/physical subject, while shared group contracts still apply to
both relevant executions. Native predicates/quantifiers/coverage gates are unchanged.
AOL keeps native structure and adds witnessed participation as a presentation overlay,
never derives participation from shared group membership or asserts bid success.
59 focused regression tests PASS; final UI/provenance closeout 12 tests PASS.
Actual unmodified external Auction PASS: roots independently FULFILLED, 5/6 dynamic
business milestones, one private monitor per execution and all 5 native invariants PASS.
Source and final isolated installed-JAR replay PASS 1615 entries, identical final hashes.
120 live implementation entries match final archive, 113 tested UI class bytes match,
297 original engine classes remain unchanged. Offscreen Swing Results/AOL/ISCN renders
inspected for both executions. Source/input/build/test freeze verified. Existing failed
harness/regression attempts and the final renderer provenance mismatch are retained;
the latter was corrected by recording the changed source hash, without weakening tests.
No fresh manual desktop GUI claim, unrelated live case studies, full clean verify,
marking changes, commit or push.

## Execution failure navigation and capability-summary audit
- [x] FN01 — Restore individually selectable scoped OCL counterexamples, preserve Goal/Task categories and exact execution/history isolation; retain native aggregate/negation authority.
- [x] FN02 — Distinguish unmet conditions from failures in navigation; explain empty failure lists without changing verification truth.
- [x] FN03 — Trace capability MIXED/UNKNOWN to actual source assignments and demand evidence; report the cause and proposed applicability model without coercing unknowns. Focused tests, package and offscreen UI evidence only.

FN01–FN03 evidence: `use-plugin/verification/execution-failure-navigation/receipt.json`.
The supplied CONDITION_FALSE is unmet progress, not a failed Goal or invariant.
Scoped native object counterexamples are individually navigable again when their
exact aggregate fails; passing negated/quantified aggregates cannot create raw-body
failure points. Failures remain separated by execution and Goal/Task/OCL/diagnostic
category. Unmet conditions have a separate navigation category and do not change
verification truth. Native engine, demand/assignment logic and case inputs unchanged.
36 focused tests PASS; archive BUILD SUCCESS. Final source/package byte checks PASS:
120 accepted backend entries unchanged, 114 tested UI class bytes match final package,
297 original engine classes unchanged. Offscreen navigation/condition and authentic
recorded Auction views inspected. No fresh MAS/manual desktop GUI run claimed.
Capability audit traces 1 fulfilled start performer plus 4 unknown skill occurrences
per execution to positive-only source demand rows and absent applicability evidence.
The proposed source-backed NOT_ASSIGNED distinction is not implemented or inferred.
Retained red tests and stale legacy aggregate-GUI expectation failure; the legacy
fixture/hash and raw 2-fulfilled/8-unknown assertions remain unchanged, while its UI
check now tests independent scopes and rejects missing scoped-native OCL evidence.

## Source-backed applicability and iStar associations
- [x] AP01 — Typed membership of source-confirmed committed-goal players; explicit assignment witness, no negative inference from absent rows.
- [x] AP02 — Present NOT_ASSIGNED independently from missing evidence, preserve raw native history and selected-root verification.
- [x] AP03 — Render declared actor associations in SR/SD, reduce default contract clutter, preserve the authored semantics.
- [x] AP04 — Recheck scoped failure categories and queue/gap behavior; focused regression, external Auction, package.

AP01–AP04: evidence at use-plugin/verification/capability-applicability/receipt.json
and live-results-ui/auction-13471184572337605195. Focused core/navigation suite:
38 tests PASS; external Auction service test PASS with two separate fulfilled
roots, exact 1 fulfilled + 4 not-assigned start performers per execution and one
not-assigned bid performer. Source replay PASS 1622 entries. The recorded scoped
false checks are only unmet GOAL_CONDITION values, no failed native invariant.
Latest completion delay was 46290 ms; transport journal reports no gap. This
supports ordered accepted-prefix correctness for this run, not full source
coverage or unlimited buffering. UI/provenance closeout checks and package source
parity are recorded in the final receipt. Fresh offscreen Swing rendering and
actual external MAS execution, no fresh manual desktop GUI claim. Raw native
engine sources and unmodified external project remain semantic authorities.
