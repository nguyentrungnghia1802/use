# Kiểm tra Hello World và House Building — 2026-11-02

## Kết quả luồng chính hiện tại

Cả hai case bị từ chối trong **Prepare + Import** khi bật Verified Checkpoints.
Chưa có source checkpoint, chưa chạy Goal/OCL live, và chưa tới chỗ tạo milestone.
Không được hiểu kết quả probe hoàn tất là case chạy PASS.

| Case | Kết quả | Các nhóm nguyên nhân được tái hiện |
|---|---|---|
| hello-world | ERROR / CHECKPOINT_SOURCE_UNSUPPORTED | Swing/AWT chưa được admit; GUIConsole có System.currentTimeMillis |
| house-building | ERROR / CHECKPOINT_SOURCE_UNSUPPORTED | Swing/AWT, cartago.tools.GUIArtifact, ASL includes, internal action .intend chưa được admit |

Probe bật `verifiedCheckpointMode(true)` rõ ràng, dùng plugin JAR đang cài trong
`target/new`, không tự acknowledge, không ép verdict và không đổi source case.
Các file `hello-world.log`, `house-building.log`, mỗi case `probe.json` giữ nguyên
stack trace/rejection list. Cả hai có **0 checkpoint token**.

JaCaMo thực sự nạp có phiên bản `1.3.1-checkpoint-SNAPSHOT`, SHA-256
`f5a31568fbc2487322d985e00ab6cecac987f2edc296bf14cd155c56c217275e`.
Probe checkout dùng dependency trong Maven cache; fingerprint/code source được
ghi ở `runtime-code-sources.log`, không suy ra việc nạp từ tên version.

## Nguyên nhân và phân loại

1. **Phạm vi admission chưa bao phủ source hai case.**
   `CheckpointBoot.admit` duyệt toàn bộ Java/ASL/XML/JCM trong source tree;
   `SourceAdmission` áp dụng các pattern/allowlist bảo thủ. Source sử dụng Swing/AWT
   và `cartago.tools.GUIArtifact` bị chặn. `GridDisplay.java` cũng được quét dù entry
   đang chọn là Console. Đây không phải model/OCL tự báo violation.

   Riêng Hello, `System.currentTimeMillis()` nằm trong nhánh cập nhật text GUI,
   nhưng scanner vẫn từ chối ngay cả khi producer được chạy headless. Chưa có
   cơ chế chứng minh/phân loại clock chỉ phục vụ presentation để admit nhánh đó.
   Không thể bỏ kiểm tra clock hoặc cho phép toàn bộ Swing để gọi là đã chứng minh
   whole-MAS barrier.

2. **House có sai lệch cách giải include giữa admission và loader.**
   `OfficialProjectLoader` thêm cả `src/agt` và `src/agt/inc` vào source paths và
   cung cấp chúng cho Jason Include. Những file `common.asl`, `org_code.asl`,
   `org_goals.asl` thực tế nằm trong `src/agt/inc`.
   Tuy nhiên, `SourceAdmission.inspect` dòng 78 chỉ dùng
   `file.getParent().resolve(include)`, nên các include từ `company*.asl` và
   `giacomo.asl` bị báo `UNADMITTED_ASL_INCLUDE` dù loader có đường dẫn hợp lệ.
   Đây là lỗi/thiếu sót của admission resolver, không phải bằng chứng file bị thiếu.

   `.intend` trong `inc/org_code.asl` cũng không thuộc allowlist hiện tại. Cần
   audit đúng implementation/hook của action trước khi mở hỗ trợ; không sửa ASL
   domain để né scanner.

3. **Phát hiện thêm lỗi báo validation.**
   `NewCaseStudyBundlesTest` chạy 5 bài: **4 PASS, 1 FAIL, 0 ERROR, 0 SKIPPED**.
   Bài tải hai bundle hợp lệ, kiểm hash/provenance và compile native models đã qua.
   Bài negative `projectExecutionContextRequiresAnExplicitVersionedAbstractionAndNeverAnAgentSource`
   mong `IllegalArgumentException` cho metadata thiếu `contextAbstraction`, nhưng
   nhận `NullPointerException` tại `SemanticBindingIndex.validate:51`.
   Nguyên nhân là `Set.of(...).contains(null)`. Đây là lỗi validation khi input
   không hợp lệ, không phải nguyên nhân trực tiếp khiến hai bundle hợp lệ không
   khởi chạy. Không sửa test để chấp nhận NPE.

4. **Bộ lọc UI checkpoint loại cả hai context hợp lệ theo workspace.**
   Từ snapshot native thực tế của bài live quan sát, `ScopePresentationProbe`
   phát lại bằng chính JAR cài hiện tại. Cả Hello và House đều có một native
   context, `scopeView.complete=true`, `domainBound=false`, `entities={}`.
   Đây là profile `PROJECT_EXECUTION_VIEW/1.0` theo workspace, không phải context
   có bound resource như Auction.

   `ExecutionScopeViews.boundContext/admittedChoices` chỉ chấp nhận context có
   Entity và domainBound không false. `PreparationCache.prepare(...,true,true)`
   do đó trả **0 execution labels, 0 audit states, 0 milestones** cho cả hai
   snapshot, dù các goal native đã FULFILLED. Xem mỗi case `frozen-ui.json`.
   Đây là tái hiện bằng dữ liệu đóng băng, không phải đã vượt source admission.
   Nó chứng minh chỉ mở preflight vẫn chưa làm UI hai case hoạt động đầy đủ.

   Scoped invariant owners cũng chưa bao phủ các object Console/ProcurementAuction/
   BuildProgress trong context này: có row `EXECUTION_CONSTRAINT_OWNER_UNAVAILABLE`.
   Không được lấy kết quả global hoặc của execution khác để ép scoped checks PASS.

## Ý nghĩa đối với hệ thống

Việc dừng trước khi reasoning khi capability chưa được chứng minh phù hợp với
nguyên tắc fail closed. Tuy nhiên, bản hiện tại **chưa hỗ trợ chạy hai bundle này
qua luồng Verified Checkpoints**, có resolver nhận diện sai include hợp lệ, và
UI checkpoint loại các workspace context không có Entity resource. Thành công
live Auction không chứng minh hỗ trợ mọi case khác.

Các kết quả observed-mode trong tài liệu cũ là evidence lịch sử. Chúng không được
dùng thay cho acceptance Verified Checkpoints. Bài live quan sát hiện có được chạy
riêng để đối chứng tầng mapping/native runtime: `AdditionalAuthoredCasesIT`
**2/2 PASS, 0 failures/errors/skips**, `observation-control-tests.log` BUILD SUCCESS.

- Hello: cả 2 goal FULFILLED, 770 replay entries; native PRE/POST, cùng MSystem,
  Stop/Restart, replay sau khi xóa toàn bộ copied inputs đều qua.
- House: cả 15 goal FULFILLED, 3.035 replay entries; các kiểm tra tương ứng đều qua.
- Coverage tổng thể vẫn INCOMPLETE; không đổi thành PASS hoặc claim normative/
  mental-state coverage đầy đủ.
- Receipts gốc nằm ở `verification/additional-cases/hello-world-2710205972145095189`
  và `verification/additional-cases/house-building-10184017443313623345`.
  Bản sao receipt/result trong mỗi thư mục case của báo cáo liên kết với
  `frozen-ui.json` để chứng minh lỗi lọc context bằng dữ liệu hoàn tất thực tế.

Live control là kiểm thử service-level; lỗi UI được xác minh bằng replay snapshot
qua class cài hiện tại. Không claim hai case đã chạy desktop Verified acceptance.

## Thứ tự khắc phục cần thiết

- Dùng source/include resolution nhất quán với official loader, vẫn kiểm đường
  dẫn nằm trong admitted source set và giữ hash identities.
- Audit action `.intend` và phần GUI/provider/clock của hai source; chỉ admit phần
  có guarantee cụ thể và executable evidence, không whitelist theo tên case.
- Sửa null handling của metadata validation, giữ nguyên yêu cầu abstraction.
- Hỗ trợ explicit workspace/project execution views trong admission UI và scoped
  constraint ownership, không gán Entity giả hoặc mượn toàn bộ state execution khác.
- Sau khi qua preflight mới kiểm tra checkpoint của operation, in-flight
  construction/await_time, dynamic organization, exact-cut UI, automatic release,
  missing evidence/error hold và native Goal/OCL outcomes của từng case.

Lần kiểm tra này không sửa production/model/source, không thay assertion/verdict,
không build lại `target/new`, không full clean verify, không commit/push. Producer
Auction đã tồn tại trước lượt kiểm tra được giữ nguyên; probe chỉ dừng các child
do chính nó tạo bằng lifecycle bình thường.
