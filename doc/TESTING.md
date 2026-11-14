# Báo cáo kiểm thử tổng quan

Cập nhật: 14/11/2026, sau khi chuyển chính sách checkpoint sang interface JaCaMo
và gom plan con đồng bộ vào plan cha.
Đây là báo cáo kiểm thử duy nhất được duy trì trong source.

## Kết quả

| Phạm vi | Kết quả |
| --- | --- |
| Reactor bốn module, build bản USE đóng gói | BUILD SUCCESS |
| Parser, model, native OCL và semantics | 110 test qua, gồm đối chiếu nguồn gốc được tái sử dụng |
| Đồng bộ trạng thái, checkpoint và vòng đời JaCaMo | 37 test qua, gồm các JVM nguồn thật |
| Snapshot và giao diện trạng thái | 3 test qua |
| Tổng test plugin | **150 test, 0 failure, 0 error, 0 skipped** |
| Auction qua bridge với JaCaMo thật | 59 checkpoint trong test, 58 khi chạy form của USE đóng gói, so với mốc 128 trước đây; 2 đồ thị AuctionCompleted riêng, APPROVE |
| Counter qua bridge và form USE đóng gói | HOLD_FAILURE tại checkpoint 3, ở cuối plan cha; các giá trị tính toán trung gian không tạo checkpoint riêng |
| Bốn entry point mô hình | CSL/OAL/iStar/iStarSCN đều mở được file trong hai ca GUI |
| Đóng workspace | Tiến trình JaCaMo thuộc phiên dừng, credential tạm được xóa |
| Đóng gói | 5 entry JAR từ một source module; không chép implementation JaCaMo/Jason/CArtAgO/Moise vào plugin JAR |

Toàn bộ 150 test được chạy lại sau khi đổi chính sách checkpoint. Các ca mới
kiểm tra gọi con đồng bộ, nhiều plan độc lập trên cùng agent, atomic, `!!Goal`,
fork/join, lỗi trong fork, thất bại/hủy và goal không có plan. Luồng A giao việc
cho B rồi chờ trả kết quả có đúng ba checkpoint, chưa tính khởi tạo. Hai ca từ
chối riêng xác nhận B chưa được chạy trước chấp thuận chuyển giao và A chưa
được nhận kết quả trước chấp thuận hoàn thành của B. Các ca không đổi giá trị
vẫn tạo checkpoint, không phụ thuộc CSL hoặc mapping.

Giao diện năm plugin, bốn entry point mô hình và việc đóng tiến trình nguồn đã
được kiểm tra lại bằng bản cài đặt vừa build, với cả Auction và Counter. Các
parser/recurrence gốc tiếp tục có test đối chiếu; source tích hợp không chứa
implementation thay thế framework nguồn.
Số checkpoint Auction có thể dao động theo nhánh ngẫu nhiên và sự kiện nghĩa
vụ của JaCaMo; 59 là kết quả quan sát, không phải hằng số cấu hình.

## Chạy lại

Tại thư mục chứa `pom.xml` gốc:

```bash
mvn -B -pl use-plugin -am test \
  '-Dtest=**/jacamo/**/*Test' \
  -Dsurefire.failIfNoSpecifiedTests=false -DfailIfNoTests=false
mvn -B package -Dmaven.test.skip=true
```

Test/fixture nằm trong `use-plugin/src/test`; JUnit và oracle output nằm trong
`target/` và được Git bỏ qua. `use-plugin/tools/smoke/EmbeddedUseSmoke.java` kiểm
tra form, các entry point và vòng đời nguồn trên desktop thật. Output GUI có thể
đưa vào `/tmp`; không cần lưu log, snapshot, ảnh hay các báo cáo theo từng phase
vào source. Hai tool fault-injection/replay cũng nhận thư mục output riêng.

## Giới hạn kết luận

Test qua không chứng minh checker bắt mọi lỗi hoặc ví dụ đúng toàn diện.
Checkpoint hiện kiểm tra tại ranh giới plan/nhóm plan, không kiểm tra từng trạng
thái tính toán bên trong. Ví dụ Counter giữ vi phạm ở cuối plan cha để minh họa
phạm vi này; nó không còn tạo vi phạm rồi ghi đè bằng 999 trong cùng nhóm plan.
Các hạn chế về dữ liệu quan sát và đặc tả đã xác định vẫn còn:

- Thiếu Request/Scheme và mốc hoàn thành nên có thể bỏ sót việc không tạo Artifact
  hoặc start hoàn thành nhưng trạng thái vẫn chưa running.
- Chưa có Service kỳ vọng và dữ liệu bid-owner/best-bidder trong luồng CSL để kiểm
  tra đúng dịch vụ và đúng người thắng một cách tổng quát.
- Thiếu rule winner thuộc participant là lỗi đặc tả khi roster đã có; điều kiện
  đó chỉ kiểm tra tư cách participant, chưa chứng minh đúng người có giá thắng.

Các điểm trên phân biệt thiếu khả năng quan sát/khởi tạo của phương pháp với
thiếu ràng buộc trong đặc tả. APPROVE chỉ có nghĩa chưa tìm thấy vi phạm trong
phạm vi dữ liệu và đặc tả đang được kiểm tra.
