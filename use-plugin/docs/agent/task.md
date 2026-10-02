# TASK — Generic Step-by-Step Replay

## Goal

Ưu tiên: ỔN ĐỊNH → ĐƠN GIẢN → DETERMINISTIC → tính năng.

Recorded runtime bundle → Stop/Disconnect live observation → Open Step Replay
→ Reset / Previous / Next → isolated replay MSystem
→ normal USE Object Diagram → existing USE OCL evaluation → deterministic parity.

Status: DONE — tất cả 27 MANDATORY checkboxes có evidence PASS ngày 2026-10-02.
Không commit/push. Checklist chỉ tick khi có code + test/evidence thật.

### MANDATORY / boundaries

Reset / Previous / Next; isolated replay MModel/MSystem; normal Object Diagram
refresh; existing USE OCL; deterministic state/result parity; generic proof bằng
synthetic journal + Hello + Auction; không duplicate/orphan/listener leak.
Chỉ một selected replay system trong Session, không live writer chạy song song.
Không fuzzy identity, case/artifact/property/OCL hardcoding, fake live event,
V2 fallback hoặc sửa case semantics/frozen Ecore/V2/goldens.
Giữ OBSERVED_SUPPORTED_PROJECTION_ONLY, C08 unavailable và C09 representation thật.

### Code foundation đã kiểm tra

- RuntimeEventJournal + NativeRuntimeReplay đã có bundle/hash-chain validation,
  baseline SOIL, exact identity index và native applyEntry dispatch.
- NativeRuntimeProjector/MutationEngine/RuntimeVerificationCoordinator đã xử lý
  mutation, version, OCL/result hashes và AtomicStateChangedEvent; reuse nguyên semantics.
- USECompiler, ShellCommandCompiler, ASSLCompiler, Evaluator, MSystem/Session và
  normal USE views đã có. ExternalOclConstraintService chỉ là adapter hiện có.
- Checkpoint gần nhất chỉ có SOIL/version/hash, thiếu protocol context; phase này
  luôn reconstruct baseline, không checkpoint seek/cache.
- disconnectRuntime() ghi COVERAGE/STALE, không stop producer. Existing Hello/Auction
  tests export valid bundle trước disconnect; bundle sau coverage loss không complete.
  Session swap đóng các view; Object Diagram giữ system cố định; phải reopen/detach đúng.

Thực hiện sections 1–7 theo thứ tự, tests section 8 đi cùng mỗi gate, section 9 cuối.
Mandatory gate fail thì dừng; OPTIONAL/FUTURE không block DONE.

## 1. Reuse current journal/replay foundation

- [x] Revalidate HEAD/diff; mở rộng NativeRuntimeReplay thành retained replay context
  có open/close và cursor. Dùng chung validator/identity-index/applyEntry với batch
  replay; không engine replay, mutation hoặc OCL thứ hai.
- [x] Open immutable recorded bundle, validate toàn bộ files/schema/hash-chain/
  payload/ordinal/stateVersion/stateHash/resultHash bằng existing recorded replay.
  Corrupt, thiếu baseline, hash mismatch, GAP hoặc report không complete: reject
  trước Session activation; không repair/partial navigation.

Evidence gate 1: NativeRuntimeReplay.Bundle/Cursor reuse compiler, exact index và
applyEntry; private immutable copy. NativeStepReplayTest (2), NativeRuntimeReplayTest
(2), NativeRuntimeReanalysisTest (3): 7/7 PASS, 0 failures/errors/skips (fresh Maven
test 2026-10-02 09:45 +07). Missing baseline/hash corruption/STALE reject navigation.

## 2. Generic Step index

- [x] Step 0 = baseline; mỗi committed EVENT/SNAPSHOT thực sự đổi reconstructed
  stateHash tạo một Step cho cả atomic transaction. Snapshot là observed transition,
  không suy ra các mutation trung gian. No-op MATERIALIZED không thành Step.
- [x] Không Step cho ACK/log/network/evidence-only/duplicate/profile metadata/
  manual verify. Vẫn dispatch non-Step journal records đúng ordinal để giữ
  ledger/watermark/profile/version/result parity; không lọc mất causal records.
- [x] Index nhỏ: stepIndex, transitionOrdinal/endOrdinal, recorded stateVersion,
  event/source, stateHash. EndOrdinal gồm trailing non-Step records trước next
  transition/cuối bundle; Step ≠ stateVersion. Hiển thị latest formal result,
  không dùng last evidence-only observation thay OCL result.

Evidence gate 2: NativeRuntimeReplay.Step index; retainedCursorSharesBatchValidationAndPreservesNonStepProfileAndNoopVersions
PASS: baseline/atomic snapshot/B/A = 4 index entries; trailing PROFILE/manual/no-op/
same-state resync retained, duplicate idempotent; every endOrdinal reconstructed
with exact recorded version/state/result hashes. Formal selection uses coordinator.latest.

## 3. Simple replay controller

- [x] Reset: reconstruct baseline và forward tới endOrdinal của Step 0.
- [x] Previous: reconstruct baseline rồi forward tới Step trước; không inverse
  mutation, USE undo hoặc chỉnh original system.
- [x] Next: forward context hiện tại đến endOrdinal của Step kế tiếp bằng dispatch
  hiện có; boundary 0/last và recording baseline-only đúng, không count tăng sai.
- [x] Một navigation operation tại một thời điểm; disable buttons khi busy.
  Chỉ activate candidate hoàn chỉnh/parity PASS; error giữ state hợp lệ trước đó,
  báo diagnostic, không publish half-state hoặc worker result đã hết hạn.

Gate 3 controller evidence: NativeStepReplayTest 5/5 PASS; five repeated Reset/
Next/Next/Previous/Next sequences, first/last boundaries, invalid reopen/failed
baseline reconstruction preserves selection, overlap + close-during-open cancels
candidate. NativeStepReplayGuiIT also PASS: disabled rapid second click, normal views,
stable listener counts and isolated Session through repeated reconstruction/reopen.

## 4. Baseline reconstruction

- [x] Reuse USECompiler(model.use) → NativeUseSoilExporter/ShellCommandCompiler
  (baseline.cmd) → exact semanticId index → native forward replay; model/objects
  riêng. Giữ original engine baseline cho snapshot resetToBaseline về sau.
- [x] Assert original stateVersion/stateHash/resultHash tại từng record/endOrdinal;
  reconstruct lại protocol/profile từ journal, không restore riêng SOIL checkpoint
  hoặc sửa hash/version để khớp. Không cache mutable historical MSystemState.

Evidence gate 4: NativeRuntimeReplay.Bundle.reconstruct/atStep + Cursor dispatch;
exact semanticEvidence/resultHash/SOIL equality versus retained full forward at
each selected Step; original model/system unchanged. Fresh focused gate 25/25 PASS,
0 failures/errors/skips (2026-10-02 09:54 +07), includes OCL/coordinator/re-analysis.

## 5. Replay isolation

- [x] Chỉ Enter Replay sau khi live delivery/subscription và in-flight writer đã
  dừng/settle; disable Start/Connect/Resync khi replay. Chốt bundle bất biến tại
  consistent export boundary TRƯỚC terminal disconnect coverage marker như flow
  hiện có, rồi disconnect và validate trước Open; không xoá GAP/STALE đã ghi.
  Nếu chỉ Disconnect, ghi đúng “stop observation”, không claim producer đã stop.
- [x] Session.setSystem(replaySystem) trên EDT sau validation; một selected replay
  system, không Bridge nối vào replay, không share original MModel/MObject.
  Close/reconstruct dọn replay-owned context/listeners/files; không mutate original
  system/bundle. Không Return Live/reconnect tự động trong phase này.

## 6. USE GUI refresh

- [x] Runtime tab chỉ cần Open recording, Step: N/Total (baseline 0),
  StateVersion, Event, Source và [Reset] [Previous] [Next]; lỗi/coverage vẫn rõ.
  Không timeline editor, live context routing hoặc gọi paging là semantic Step.
- [x] Reuse normal Object Diagram/Model Browser/Class Invariant/OCL dialog.
  Forward dùng AtomicStateChangedEvent; Reset/Previous reconstruct rồi Session swap:
  detach old listeners và reopen normal views, refresh đúng object/link/attribute.
  Không custom diagram; không yêu cầu giữ layout/selection phức tạp.
- [x] IO/compile/reconstruct orchestration trên worker, existing coordinator/EDT
  boundary cho writes/views; live timer không ghi đè replay Verification.
  Chặn manual SOIL/diagram edits/reset/undo/redo/flag edits phá parity trong replay;
  normal OCL query/evaluation vẫn dùng selected replay state.

Evidence gates 5–6: DefaultJaCaMoFacade offline admission + delivery settle barrier;
ReplayStepFacadeTest rejects connected/STALE open, preserves disconnect evidence and
routes reads/exports to selected replay. NativeStepReplayGuiIT verifies actual normal
diagram membership/deletion, Model Browser, OCL dialog, invariant/Verification outcomes,
disabled rapid clicks, stable listeners and no old-system GUI subscribers. MainWindow
unregisters old bus; EvalOCLDialog/ViewFrame detach; ClassInvariantView revokes completed
worker callbacks before checkpoint result installation (SKIPPED cannot become vacuous PASS).
Fresh GUI/foundation gate: 29/29 PASS, 0 failures/errors/skips, 2026-10-02 10:20 +07.

## 7. Reuse existing USE OCL

- [x] Dùng recorded native invariants + PROFILE source/flags/timeline;
  ExternalOclConstraintService → USE ASSLCompiler/Evaluator và coordinator hiện có.
  Không viết checker/verification engine mới, không duplicate OCL semantics.
- [x] Sau mỗi Step, normal views và Verification hiển thị kết quả existing USE
  evaluation của current replay state. Giữ PASS/FAIL/ERROR/SKIPPED, coverage và
  profile attribution; FAIL không rollback. Đủ journal không nâng INCOMPLETE
  capability thành complete JaCaMo; C08 không vacuous PASS, replay không LIVE.

## 8. Generic tests

- [x] Synthetic parameterized journal: exact IDs/names/property values khác nhau;
  create/delete/update/unset/link, atomic batch, no-op/duplicate/evidence/profile,
  baseline-only và first/last boundaries; không dùng fixture làm live evidence.
- [x] Bắt buộc Reset → Next → Next → Previous → Next. So cùng objects/attributes,
  links, stateHash, recorded version và OCL outcomes/resultHash với full forward
  replay ở cùng Step; repeated Reset/Previous/Next không count drift.
- [x] Hello original tutorial + Auction original JCM: reuse
  HelloWorldSemanticInventoryIT/ManagedAuctionWorkbenchIT để record; stop/disconnect
  trước Step replay. Cùng controller, real recorded events/OCL, không sửa source/
  profile để manufacture transition; artifacts/evidence chỉ phía USE.
- [x] Repeated navigation/close/reopen: 0 duplicate identity, 0 orphan link,
  no listener leak/old-system callback, original system/bundle unchanged.
  GUI test kiểm normal diagram membership/deletion và OCL/Verification actual state.
- [x] Negative controls: corrupt/missing/hash/GAP/STALE/incompatible payload,
  failed reconstruction, rapid clicks/close while busy; fail closed, Session cũ
  không bị thay bởi candidate lỗi. INCOMPLETE nhưng intact supported projection
  giữ nguyên capability/SKIPPED, không bị coi là corrupt chỉ vì coverage label.

Evidence gates 7–8 (synthetic/GUI): NativeGenericStepReplayTest 11/11 PASS,
three identity/value variants, 9 semantic transitions each, attribute set/unset,
link insert/delete, create/delete + multi-property atomic batch. No-op/duplicate/
evidence/manual/profile preserve ordinal and version; disabled/negated PROFILE retained.
INCOMPLETE intact projection remains navigable with original SKIPPED. Hash/chain/GAP/
schema/scope/truncated/incompatible payload reject before activation; forced Next
parity error restores prior valid state and publishes 0 half-state notifications,
requires reconstruction before retry. Combined fresh focused gate: 62/62 PASS,
0 failures/errors/skips, 2026-10-02 10:30 +07. At that gate Hello/Auction recordings
were pending; the completed fresh case-study evidence follows.

Fresh completed gate 8: focused 67/67 PASS, 0 failures/errors/skips, Maven finished
2026-10-02 11:14:35 +07 (`use-plugin/target/step-final-focused.log`). Same
StepReplayProof exercises real normal Object Diagram/Model Browser/invariant/OCL
views, Reset/Next/Next/Previous/Next, every recorded semantic Step and reopen:

| Recording under `use-plugin/target` | Status | Steps | Records |
| --- | --- | ---: | ---: |
| `hello-world-object-audit/AUTO-1790913834292` | PASS | 130 | 780 |
| `hello-world-object-audit/FULL-1790914043324` | PASS | 56 | 459 |
| `workbench-acceptance/auction-1790914268918` | PASS | 171 | 1134 |

Each proof compares stateVersion, SOIL, state/result hashes and native OCL outcomes
with full forward replay. No duplicate identities/orphan links/listener drift;
original state/recording/source unchanged. Counts differ between recordings by
observed interval, not inferred AUTO/FULL runtime semantics. Auction uses unchanged
auction_demo.ocl and a real producer PASS → FAIL → PASS transition.
Regression fixes: propagate the actual EDT activation error and restore prior
Session; clean constructor-failure Object Diagram subscribers/sort listener/view
count; test setup restores USE diagram properties and selects the exact owned OCL
dialog. New activation-failure and failed-diagram regressions PASS without weakening
any state/parity/source/API assertions. At that focused gate full reactor/release
was pending; the completed final evidence is in section 9.

Final ownership review: the first full verify completed PASS at 11:32 +07, but
subsequent GUI/Session admission hardening required a new final verify. One parent
USE window reuses its existing Workbench dialog; an empty/foreign facade cannot
open replay over a Session it does not own or replace another facade's read-only
replay. Dedicated GUI close/reopen and facade ownership regressions PASS;
fresh focused gate 65/65, 0 failures/errors/skips, finished 2026-10-02 11:34:23 +07
(`use-plugin/target/step-owner-focused.log`). The ownership-hardened full verify
completed PASS as detailed below (`use-plugin/target/step-final-owner-reactor.log`).

## 9. Final acceptance

- [x] Focused new Step tests + existing recorded replay/runtime/OCL/Session/GUI
  regressions PASS; fresh reports có từng test mới, không chỉ suite cũ.
- [x] Synthetic + Hello + Auction chứng minh Reset/Previous/Next, isolated Session,
  normal USE diagram/OCL, deterministic parity và no duplicate/orphan/leak.
- [x] Full reactor verify + package/release/staging/installed replay và native
  export/OCL/Session regressions PASS; ghi commands/counts/failures/errors/skips.
- [x] Review diff/call graph/checkbox và update usage/evidence theo code thực tế.
  DONE khi mọi MANDATORY gate PASS; không đòi OPTIONAL/FUTURE được implement.

### Final gate — ownership-hardened production code

Commands from USE root:

~~~powershell
mvn -B -pl use-plugin -am "-Dtest=ExternalOclConstraintServiceTest,NativeGenericStepReplayTest,NativeRuntimeReanalysisTest,NativeRuntimeReplayTest,NativeStepReplayGuiIT,NativeStepReplayTest,NativeUseGuiEndToEndIT,RuntimeVerificationCoordinatorTest,ReplayStepFacadeTest,JaCaMoWorkbenchPanelTest" "-Dsurefire.failIfNoSpecifiedTests=false" "-Djava.awt.headless=false" test
mvn -B "-Djava.awt.headless=false" verify
~~~

Focused: 65/65 PASS, 0 failures/errors/skips, finished 11:34:23 +07.
Full reactor: BUILD SUCCESS, 612/612 reported tests across 140 fresh XML suites,
0 failures/errors/skips; finished 2026-10-02 11:50:12 +07. Reports counted only
when written after this final run started; stale focused/earlier XML excluded.

| Module | Unit | Integration | Status |
| --- | ---: | ---: | --- |
| jacamo-bridge-contract | 15 | 0 | PASS |
| jacamo-bridge-jacamo | 27 | 0 | PASS |
| use-core | 12 | 1 | PASS |
| use-gui | 1 | 129 | PASS |
| use-plugin | 411 | 16 | PASS |

Package/release: ReleasePackageIT 3/3, LegacyAuthorityPackagingIT 1/1,
NativeRuntimePackagedReplayIT 1/1, GuiPluginStagingIT 1/1 PASS (6/6 included in
full totals). Installed child JVM loads production plugin without Maven/JaCaMo
classpath and proves recorded + Step replay, Reset/Next/Next/Previous/Next,
hash parity, read-only reset rejection and closed Session. Distribution ZIPs,
release checksum and byte-identical plugin/GUI staging confirmed; staged SHA-256
`a6706356df29f060e6c792b259aeb880a13396ec17580b415586a63153afc9d9`.

Final real recorded GUI evidence under `use-plugin/target`:

| Evidence directory | Steps | Records | Final stateVersion | Status |
| --- | ---: | ---: | ---: | --- |
| `hello-world-object-audit/AUTO-1790915981608` | 130 | 780 | 132 | PASS |
| `hello-world-object-audit/FULL-1790916149836` | 74 | 584 | 76 | PASS |
| `workbench-acceptance/auction-1790916388338` | 173 | 1138 | 174 | PASS |

All three `step-replay-proof.json` reports validate every selected Step against
the shared full forward dispatcher: identical SOIL/version/stateHash/resultHash/
OCL outcomes; 0 duplicate identities, 0 orphan links, stable listener counts,
unchanged original state/bundle/source. NativeStepReplayGuiIT 3/3 also proves
normal USE diagram/browser/invariant/OCL views, failed constructor cleanup and
single Workbench owner with clean close/reopen. Auction keeps the authored OCL
and records true producer ACK and PASS → FAIL → PASS; terminal disconnect STALE
remains in the original journal, never silently cleared to achieve parity.

Final logs: `step-owner-focused.log`, `step-final-owner-reactor.log`. README usage,
call graph and all checkboxes reviewed against current implementation. Git diff
checked with `core.whitespace=cr-at-eol`; no frozen Ecore/V2/golden or case-source
changes. USE main still at `fa819ab06b0549c12d1a1a7f8ffcb7522857e0f2`; 26 modified
files + 6 new implementation/test files remain uncommitted (including the user's
replacement task plan). JaCaMo retains only its pre-existing `examples/auction.zip`;
no generated source-tree junk. No commit/push; awaiting user approval.

Scope remains OBSERVED_SUPPORTED_PROJECTION_ONLY. C08 stays unavailable and
INCOMPLETE/SKIPPED capabilities stay explicit. All OPTIONAL/FUTURE items below
remain unimplemented as requested, and do not block this phase's DONE.

Acceptance:
Any supported recorded case study → Stop runtime/Disconnect observation
→ Open Step Replay → Reset / Previous / Next → correct isolated MSystemState
→ normal USE Object Diagram → existing USE OCL → deterministic parity
→ no duplicate / orphan / listener leak.

Commands từ USE root (test mới phải được tạo; không skip acceptance):
~~~powershell
mvn -B -pl use-plugin -am "-Dtest=*StepReplay*Test,*ReplayStep*Test,NativeRuntimeReplayTest,RuntimeVerificationCoordinatorTest,ExternalOclConstraintServiceTest,NativeRuntimeFacadeIntegrationTest,JaCaMoWorkbenchPanelTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
mvn -B verify
~~~
Full verify phải thực sự chạy Step GUI IT mới, Hello/Auction, release/staging/
installed replay và native export/Session/OCL suites; package-only không đủ.
Không lấy log/XML lịch sử làm PASS và không weaken assertions.

## OPTIONAL / FUTURE — không block DONE phase đầu

- Concurrent Live + Replay; Return Live without disconnect; complex dual-context routing.
- Fast checkpoint seek, random-seek optimization, advanced caching.
- Partial GAP/prefix navigation (future); repair/suy diễn missing state luôn bị cấm.
- Go to Step chỉ khi reuse cùng seek primitive rất đơn giản, sau mandatory gate.
- Autoplay, breakpoint, speed control, timeline editor, advanced layout retention.
- Current external OCL re-analysis trong Step UI: có batch foundation sẵn,
  giữ API/regression hiện có; profile override/new-result timeline không mandatory.
