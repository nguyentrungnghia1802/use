# Đánh giá bộ kiểm chứng bằng lỗi chủ động — 13/11/2026

**Kết luận về bộ tool hiện tại: chưa đạt kiểm chứng đầy đủ Auction.** Bảy loại
lỗi đã được kích hoạt trong các bản sao: tool phát hiện và chặn đúng ba loại,
bỏ sót bốn loại. Đây là kết quả trên tập lỗi đã chọn, không phải tỷ lệ bảo đảm
cho mọi chương trình.

`assessment.json` ghi `FAIL_KNOWN_COUNTEREXAMPLES`. Lệnh đánh giá trả exit code
**2** vì có lỗi bị lọt. Một ví dụ sai nhưng bị phát hiện/chặn đúng được tính là
**checker test đạt**; một ví dụ sai vẫn được cho chạy tiếp là **checker test lỗi**.

## Cách chạy và xác định đúng/sai độc lập

- Chạy lại build và 17 kiểm thử state hiện có, gồm test JaCaMo/CSL hai JVM.
- Chạy Auction gốc, rồi chín bản sao được kiểm soát, đều qua producer JaCaMo thật,
  mapping, native CSL, iStar và execution barrier. Không sửa source ví dụ gốc.
- Giữ nguyên model CSL, iStar, mapping và binary checker trong toàn bộ chiến dịch.
  `frozen-inputs.json` lưu SHA-256; sau chạy xác nhận tất cả không đổi.
- Các bản sao dùng hai agent Bob/Alice và hai phiên đấu giá, thay random bằng
  bid 10 rồi 20. Mức giá 20 là bid kém hơn nhưng vẫn thành công theo Artifact gốc.
  Giảm cohort và cố định input nhằm làm lỗi có thể lặp lại, không thay ràng buộc
  để khớp kết quả. Auction gốc vẫn chạy đủ năm agent.
- Oracle ở Java source ghi trước/input/sau của bid, Service mong đợi/thực tế và
  currentWinner mong đợi/winner công bố. Nó không dựa vào verdict của checker.
  Đối chiếu bid với `min(previous, input)` được tính riêng trong Python.
- Plan con thực hiện bước cần kiểm tra; **plan tiếp theo** ghi marker độc lập.
  Ca đúng xác nhận marker thực sự hoạt động. Ở các ca bị chặn, marker sau bước
  lỗi không xuất hiện. Không nhầm một action nằm trước checkpoint với việc
  chương trình đã được phép chạy tiếp sau checkpoint.
- `mutation.diff`, source bản sao, `producer.log`, `verification-next-plan.txt`
  và trace JSON đầy đủ được giữ theo từng ca. Secret vận chuyển đã được xóa.
  Auction gốc không chèn oracle: các counter oracle bằng 0 ở ca đó nghĩa là
  không đo bằng instrumentation, không có nghĩa là không thực hiện action.

## Kết quả từng ca

| Ca | Sự thật từ ví dụ/oracle | Verdict của tool | Đánh giá checker |
| --- | --- | --- | --- |
| Auction gốc | Không chèn lỗi; 128 checkpoint, hai AuctionCompleted fulfilled | APPROVE | Chỉ xác nhận các thuộc tính được quan sát |
| Bid kém hơn hợp lệ | 4 lời gọi bid; 2 lời gọi giữ nguyên best_bid đúng quy tắc min; hai phiên hoàn tất | APPROVE | Đạt ca đối chứng, không báo nhầm |
| Giá âm | Oracle ghi 2 lần bid(-5), vi phạm yêu cầu giá không âm | HOLD_FAILURE: Auction::ValidBid và SafeBid | Phát hiện đúng; không chạy plan sau bid |
| best_bid tăng | Oracle ghi 2 cập nhật sai 10 → 20; quy tắc đúng phải giữ 10 | HOLD_FAILURE: BidNeverIncreases VIOLATED; các invariant CSL không fail | Phát hiện đúng ở iStar; không chạy plan sau bid thứ hai |
| Bỏ focus | Hai Artifact running nhưng auctioneer không nằm trong observers | HOLD_FAILURE: Auction::RunningAuctionIsObserved | Phát hiện đúng; không chạy plan sau start |
| start vẫn stopped | start trả thành công nhưng running=false; oracle xác nhận 2 lần | APPROVE; Goal start PENDING; plan sau start đã chạy | **Bỏ sót hậu điều kiện sau khi plan hoàn tất** |
| Không tạo Artifact | Plan start con hoàn tất, marker sau start chạy hai lần, không có Auction nào | APPROVE; không có instance iStar Auction để đánh giá | **Bỏ sót do thiếu expected context** |
| Sai Service | Giá trị task bị thay bằng WRONG_SERVICE, khác input của start | APPROVE; cả hai AuctionCompleted fulfilled | **Bỏ sót lỗi dữ liệu** |
| Sai winner | Công bố ghost thay cho currentWinner=alice, oracle xác nhận 2 lần | APPROVE; cả hai AuctionCompleted fulfilled | **Bỏ sót kết quả sai** |
| Thiếu best_bid | Thuộc tính bắt buộc không có trong snapshot | HOLD_UNVERIFIED: ATTRIBUTE_VALUE_MISSING; không chạy tiếp | Chặn đúng khi thiếu dữ liệu; không coi UNKNOWN là phát hiện ngữ nghĩa |

Trong ba ca lỗi bị phát hiện, action/plan nguồn vẫn có thể SUCCEEDED. Chính
verdict CSL/iStar khiến barrier giữ dừng; việc không thấy marker không phải
do chỉ kiểm tra exit code Java. Ca giá tăng đặc biệt xác nhận iStar có thể
chặn trong khi CSL invariants vẫn PASS.

## Điều phát hiện được ở lần chạy ví dụ gốc

Giacomo và Alice mỗi người có một terminal `+!bid` FAILED; checker vẫn APPROVE,
và hai AuctionCompleted vẫn fulfilled. Điều này **không tự động chứng minh**
checker sai nếu yêu cầu chỉ là có ít nhất một bid trong phiên: đó chính là
ngữ nghĩa aggregate hiện tại. Nhưng không được diễn giải kết quả này thành
mọi participant đã hoàn thành nghĩa vụ bid. Muốn kiểm tra từng người cần dữ
liệu participation/commitment/completion tương ứng và ràng buộc định lượng rõ.

## Vì sao có bốn ca bị lọt

1. Gate cho phép Task/Goal Achieve chưa đạt như một trạng thái đang tiến triển.
   Chưa có lifecycle/context cho biết **start đã kết thúc và hậu điều kiện phải
   đúng tại thời điểm này**. Vì vậy running=false chỉ làm Goal start PENDING.
2. Graph gắn với Artifact đã tồn tại. Không có Artifact thì không có graph để
   biểu diễn object lẽ ra phải được tạo; đánh giá các invariant còn lại vẫn qua.
3. CSL chưa có expected Id/Service từ request/Scheme để so sánh với Artifact.
4. Chưa đối chiếu winner công bố với dữ liệu best bidder/currentWinner. Predicate
   stopped không đủ để chứng minh giá trị winner được xuất ra là đúng.

Đây là khiếm khuyết của **bộ kiểm chứng như một phương pháp hoàn chỉnh**, dù
engine có thể đang tính đúng các predicate đã được khai báo. Không sửa ví dụ
để che lỗi và không sửa model giữa các ca để làm bảng kết quả đẹp hơn.

Ưu tiên sửa tiếp là context Request/Scheme có trước Artifact, dữ liệu milestone
completion và expected parameters, rồi dữ liệu winner/bidder cần để kiểm tra
kết quả. Các dữ liệu này vẫn có thể cập nhật vào CSL qua mapping thuộc tính;
không cần đổi sang ghép tên plan JaCaMo với Task iStar.

## Tái lập

Sau khi build module state-verification và chuẩn bị runtime-classpath.txt:

```bash
python3 use-plugin/tools/verify-state-checker.py \
  --output /tmp/use-checker-audit-new --seconds 8 --baseline-seconds 18
```

Đánh giá lại tập bằng chứng đã ghi, không chạy lại Java:

```bash
python3 use-plugin/tools/verify-state-checker.py \
  --assess use-plugin/verification/checker-audit-20261113
```

Lệnh thứ hai hiện trả 2, đúng với bốn counterexample đã biết. Exit 0 trong một
chiến dịch tương lai chỉ nghĩa là các ca được chọn đạt tiêu chí; không phải
chứng minh phổ quát rằng checker hoặc ví dụ không còn lỗi.
