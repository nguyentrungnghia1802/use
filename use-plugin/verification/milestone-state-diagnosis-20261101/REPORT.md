# Báo cáo milestone có CSL/USE state trùng nhau

Đã tái hiện đúng Execution 1, Auction `a2`, trong lần chạy
`20261101-235317-594-a2eb8b48`. Milestone 2–3 giống nhau; milestone 4–5–6
cũng giống nhau ở phần CSL/USE state. Đây là vấn đề còn tồn tại sau bản sửa trước.

## Bằng chứng trực tiếp

- Journal `run-5537625689257953083/runtime-14799534229439657508.jsonl`:
  1.870 bản ghi, chuỗi hash được kiểm tra đầy đủ, 7 checkpoint EVALUATED.
  SHA-256: `0972519d181b555f33367a2e54497606e982c5537e531e7ed69015673d35cb6d`.
- Phát lại kết quả native nguyên gốc bằng chính presenter/projection/timeline từ
  **JAR plugin đang cài**, không tạo verdict thay thế. `fromMap` kiểm tra hash
  từng kết quả native. Xem `installed-comparison.log` và `provenance.json`.
- Plugin cài và plugin build cùng SHA-256:
  `61ed0afa669d952f7e6dd5d2913c24fc811cb6ae5545c9dcb10764d1eecd0a62`.
  Không phải đang chạy nhầm JAR plugin cũ.
- Kết quả đầy đủ, gồm từng row/evidence trước–sau:
  `exact-milestone-comparison.json`. Dữ liệu native đóng băng:
  `exact-results.json.gz`.

| Milestone Execution 1 | Ordinal đại diện | CSL/USE state a2 | Thay đổi khiến code tạo milestone |
|---|---:|---|---|
| 1 | 978 | Chưa bắt đầu; running=false | State ban đầu đã có binding |
| 2 | 1084 | Running=true, chưa có bid tốt nhất | CSL thay đổi |
| 3 | 1383 | **Giống milestone 2** | `P_bid_t1` của alice, francois, giacomo, maria: UNKNOWN → FULFILLED |
| 4 | 1517 | best_bid=34.0288526698709, currentWinner=maria | CSL thay đổi |
| 5 | 1678 | **Giống milestone 4** | `P_bid_t2`, `P_bid`, `C_bid` của giacomo: UNKNOWN → FULFILLED |
| 6 | 1756 | **Giống milestone 4 và 5** | Các kết quả tương ứng của alice: UNKNOWN → FULFILLED |
| 7 | 1869, native đã settle | Running=false, winner=maria | CSL thay đổi |

Không chỉ text trông giống nhau: với 2–3, hash CSL giống nhau và hash toàn bộ
scoped presentation objects/links cũng giống nhau. Điều tương tự đúng cho 4–5–6.
Hash snapshot toàn MSystem khác nhau, phù hợp với snapshot toàn MAS và trạng thái
kiểm chứng nội bộ tiếp tục tiến triển; không thể dùng khác biệt global hash để
khẳng định CSL riêng của a2 đã đổi.

Giá bid trong producer log giải thích vì sao a2 không đổi ở 4–5–6:
giacomo gửi `100.40520034813508`, alice gửi `51.40545234373113`, đều không tốt hơn
`34.0288526698709` của maria. `AuctionArtifact.bid` vẫn hoàn tất operation khi giá
không tốt hơn; chỉ cập nhật `best_bid/currentWinner` khi `bidValue < best_bid`.
Vì vậy việc có thêm kết quả Task được chứng nhận không đồng nghĩa có CSL state mới.

## Nguyên nhân trong code

`ObservedConformancePanel.PreparationCache.prepare` gọi
`CheckpointTimeline.group` sau khi project về đúng execution.
`CheckpointTimeline.signature`, dòng 35–40, tính hash từ:

`CSL businessState + danh sách (kind, element, context, player, occurrence, status,
required, certified)`.

`group`, dòng 17–19, tạo milestone mới bất cứ khi nào hash này đổi. Do đó chỉ cần
`UNKNOWN → FULFILLED` của một Goal/Task occurrence là đủ tạo milestone, kể cả toàn
bộ CSL objects/links không đổi. Thuật toán đang nhóm **state + kết quả kiểm chứng**,
trong khi phần state hiển thị bên trái chỉ thể hiện CSL/USE state của execution.
Đó là nguyên nhân của cặp và bộ ba state giống nhau.

Không tìm thấy việc Entity a1 bị chép vào a2 trong các cut này. Đây cũng không phải
lỗi vẽ lặp một snapshot: các checkpoint và kết quả Task là khác nhau, nhưng code
đang dùng chúng làm tiêu chí tách **milestone trạng thái**.

## Vì sao bản sửa và kiểm thử trước chưa xử lý hết

Bản sửa trước loại thay đổi metadata/quality khỏi tiêu chí tách, nhưng vẫn chủ
đích giữ Goal/Task progress trong signature. Test
`recordedOtherExecutionMetadataDoesNotAddMilestonesButOwnTaskProgressDoes` còn
xác nhận hai cut cùng CSL nhưng khác kết quả Task được tách. Vì vậy test xanh
chứng minh quy tắc cũ hoạt động, chưa chứng minh yêu cầu milestone CSL không lặp.

Theo cách người dùng đang dùng màn hình, đây là sai lệch quy tắc gom milestone và
cách trình bày, cần sửa tiếp; không nên giải thích tất cả các state lặp là đã ổn.

## Hướng sửa đúng với yêu cầu state không lặp

Gom các cut **liên tiếp có cùng CSL objects/attributes/links của đúng execution**
vào một milestone trạng thái. Với bản ghi này, 7 milestone hiện tại tương ứng
4 trạng thái CSL liên tiếp: `[1], [2,3], [4,5,6], [7]`.

Giữ các lần Task/Goal/OCL đánh giá và source checkpoint bên trong milestone đó,
vẫn có thể xem từng snapshot/kết quả/token gốc trong Audit. Mọi checkpoint vẫn
phải được kiểm chứng và hiển thị kết quả trước quyết định; domain state không đổi
không phải lý do bỏ kiểm chứng hay tự release. Failure và Acknowledge & Continue
tiếp tục bám exact token, không phụ thuộc số milestone trên Swing.

Kiểm thử tiếp theo cần kiểm tra đồng thời: nhóm 2–3 và 4–5–6 thành cùng state;
giữ nguyên mọi native assertion/verdict/evidence và số audit cut; Task failure
mới vẫn hiển thị và giữ dừng dù không tạo CSL state mới. Không sửa semantic
assertions để đạt số milestone mong muốn.

## Phạm vi lần kiểm tra này

Chỉ tạo công cụ phân tích và báo cáo; không sửa production, mô hình, source
Auction, cài đặt hoặc tiến trình đang chạy. Không build lại, chạy live MAS mới,
commit/push. Đây là phát lại bằng code cài hiện tại và evidence của lần chạy người
dùng; không được gọi là một lần desktop acceptance mới.
