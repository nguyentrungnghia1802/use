# Đề xuất auction-false — chưa triển khai

Trạng thái: SOURCE_REVIEW_ONLY. Chưa tạo/copy auction-false, chưa sửa source
Auction, chưa chạy mutation/live test, chưa commit/push. Các kết quả dưới đây là
dự kiến từ source/contract/policy hiện tại; không phải evidence thực thi.

## Phương án đề nghị: stop công bố winner nhưng quên đóng auction

Copy đầy đủ hai đầu vào hiện tại thành các thư mục riêng:

- `jacamo/examples/auction` -> `jacamo/examples/auction-false`.
- `use/use-plugin/examples/auction` -> `use/use-plugin/examples/auction-false`.

Giữ tên `auction.jcm`, MAS `auction`, package/class AuctionArtifact, agents,
hai yêu cầu auction, ASL, MOISE, CSL, iStar và OCL. Tên folder phân biệt biến thể;
không cần đổi các định danh được mapping bind. Chỉ sửa ứng dụng Java của bản sao.

Trong `src/env/auction_env/AuctionArtifact.java`, bỏ đúng cập nhật sau khỏi stop():

```diff
 @OPERATION public void stop() {
     if (!getObsProperty("running").booleanValue())
         failed("The protocol is not running, why to stop it?!");
-    getObsProperty("running").updateValue(false);
     getObsProperty("winner").updateValue(new Atom(currentWinner));
 }
```

Đây là lỗi logic nghiệp vụ: operation có thể trả thành công, công bố winner,
nhưng auction còn mở. Nó kiểm tra được việc USE phát hiện hành vi sai dù Jason/
CArtAgO không báo exception. Fault không phụ thuộc Auction ID, agent, bid amount,
winner hoặc Swing milestone number. Áp dụng cho mọi stop của bản sao.

Hash Java source hiện tại ở cả hai bản Auction:
`34036fc220274853e39f93682cdb22c350fe6a06739669860f2a66afe606ff4b`.

## Vì sao assertions hiện tại đủ phát hiện

- CSL `Auction::stop()` pre RunningForStop: `self.running`.
- CSL post Closed: `not self.running`.
- iStar Task A_decide_t2 pre: `self.running`; post: `not self.running`.
- XML operation op-stop gắn RunningForStop/Closed vào đúng stop(), action receipt
  op-stop và exact source step A_decide_t2.
- Policy chọn terminal Task checkpoint
  `task-post:execution-steps:A_decide_t2`. Operation stop thành công không tự tạo
  một OPERATION_FAILURE cut; lỗi phải được phát hiện bằng native POST/Task POST.
- Native inventory giữ applicable POST/TASK_POST FAIL và yêu cầu HELD_FAILURE.

Không đổi nội dung CSL/iStar/OCL hoặc acceptedFailure. Chỉ repin SHA source Java
của bản sao trong mapping và để compile/preflight tạo binary proof/program/policy
hash mới từ source thật. Nếu dùng hash cũ, import bị reject là lỗi integrity,
không chứng minh được lỗi Goal/Task cần demo.

## Luồng dự kiến

1. Prepare/import và khởi tạo bình thường; running ban đầu false.
2. Start chuyển running=true; các bid/goal steps tiếp tục được kiểm tra theo
   mapping hiện tại. Không sửa nhánh random .fail vốn đã được acceptedFailure
   công nhận là một completed attempt, tách biệt actual accepted bid.
3. Đến decide, source gọi stop(). Guard hợp lệ nhưng bug giữ running=true,
   operation vẫn trả success và cập nhật winner theo currentWinner thật.
4. Terminal marker được policy chọn đóng admission; các công việc đã admit
   drain/yield, rồi clock và snapshot được giữ theo barrier chung.
5. USE nhận exact cut: RunningForStop dự kiến PASS; Closed và Task POST dự kiến
   FAIL do running vẫn true. Hiển thị invocation, scope và before/after evidence;
   không tự release, giữ HELD_FAILURE.
6. A_decide_t2 không có proof postcondition thành công. Chuỗi phụ thuộc
   A_decide_t2 -> C_decide -> A_decide -> O_decide -> O_root phải phản ánh kết quả
   của native engines; không ép toàn bộ Goal thành FAILED/VIOLATED.
7. Acknowledge & Continue chỉ review/release exact token. running vẫn true và
   lỗi/evidence gốc vẫn trong lịch sử; không sửa state, retry stop hay tạo PASS.

Một POST=false không tự chứng minh source execution FAILED: Java stop ở biến thể
này có thể báo SUCCEEDED. Vì vậy chỉ cam kết kiểm chứng native POST FAIL và hold;
nhãn Goal/Task cuối phải ghi theo native output khi chạy thật. O_root không được
chứng nhận hoàn thành dựa trên console winner/operation success.

Barrier giữ toàn MAS trong supported scope. Hai auction có thể cùng có lỗi trong
một frozen cut nếu công việc tương ứng đã được admit trước đó. Không ép số lần
pause/ack hoặc thứ tự a1/a2 để làm demo trông đúng; lịch sử từng execution vẫn riêng.

## Các lựa chọn không phù hợp cho demo FAIL đầu tiên

| Fault | Hạn chế với đặc tả hiện tại |
|---|---|
| Đảo dấu so sánh bid | O_bid_accepted có thể chỉ còn PENDING; Goal condition false tương lai không tự là applicable FAIL. |
| Công bố sai winner | Hiện Closed chỉ kiểm tra running; chưa có assertion winner=currentWinner hay minimum-price correctness. Có thể phát hiện khoảng trống coverage thay vì tạo hold chắc chắn. |
| Chèn .fail vào nhánh bid đang được acceptedFailure cho phép | Completed attempt không đồng nghĩa failed requirement. |
| Bỏ start hoặc lỗi start | Phát hiện sớm qua Opened/Task POST, nhưng ít thể hiện đoạn Start -> Bid -> Decide. |

## Kế hoạch kiểm chứng sau khi thống nhất đề xuất

- [ ] Copy và lưu manifest file/hash để chứng minh Auction gốc không đổi; chỉ
      Java fault và metadata repin của bản sao được khác.
- [ ] Preflight/compile/import thành công với artifact class của đúng bản sao;
      không dùng nhầm cached/original AuctionArtifact.
- [ ] Test focused PRE=true, POST=false trên before/after thật, nguồn operation
      vẫn success; không sửa assertions hoặc fabricate verdict.
- [ ] Live auction-false: đi qua các cut hợp lệ, giữ exact failure ở decide;
      xác minh không có advancement/release khi chưa review.
- [ ] Review trên UI; kiểm tra original failure/state/hash/evidence còn nguyên,
      và những failure token tiếp theo vẫn review được nếu runtime tạo ra.
- [ ] Xác minh execution isolation, audit/replay và đối chiếu healthy Auction.
- [ ] Ghi nhận chính xác nhãn raw/certified Goal/Task, applicable failure và
      source success; đánh dấu thiếu evidence riêng, không xem là expected FAIL.

Không thay đổi verifier/framework/pause policy theo tên auction-false; không full
clean verify, không commit/push. Chỉ đánh dấu checklist sau khi có evidence thật.
