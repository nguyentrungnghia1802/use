# Phân loại lỗi phương pháp và lỗi đặc tả Auction

Ngày 14/11/2026. **Có cả hai nhóm lỗi, không thể quy toàn bộ vấn đề về việc viết
thiếu OCL.** Profile OrgCtx mới đã chạy được trên JaCaMo thật. Trong 8 lỗi được
kích hoạt và xác nhận độc lập, đặc tả hiện tại phát hiện/chặn 3, bỏ sót 5.
Thêm ràng buộc có thể sửa một phần, nhưng các lỗi còn lại cần dữ liệu/ngữ nghĩa
kiểm tra mà luồng hiện tại chưa cung cấp. Không sửa ngầm chúng trong chiến dịch này.

## 1. Ngữ nghĩa CSL đã triển khai

Đặc tả chuẩn của profile: [CSL_ORGCTX_UNIFIED_V1](../../docs/project/CSL-ORGCTX-UNIFIED-V1.md).

- **OrgCtx:** một chủ thể có identity/state, có thể là agent cá nhân hoặc tổ
  chức/nhóm. Mỗi agent có đúng một OrgCtx chuẩn, đồng thời dùng làm role player
  và observer Artifact. Không tạo thêm AtomicAgent hoặc wrapper đại diện.
- **Role:** occurrence trách nhiệm của một OrgCtx trong một OrgCtx ngữ cảnh.
  Tổ chức cũng có thể đóng role tại cấp cao hơn. Không biến Role thành agent.
- **Entity:** thực thể phi ý định, không làm role player hay organizational member.
- `memberOf` và `plays` độc lập, nhiều–nhiều về chủ thể/context. Xóa enactment
  không xóa membership. `members`, `rolePlayers`, `roleContexts`, `participants`
  là các navigation OCL có ngữ nghĩa xác định; `participants` bỏ trùng chủ thể.
- Khai báo OrgCtx lồng nhau chỉ sở hữu declaration; không tự tạo instance,
  membership hay enactment. Runtime scope, direct membership và role context
  là ba quan hệ riêng.

Đã cập nhật [auction.csl](../../examples/auction/state-only/auction.csl): AuctionOrg
chứa kiểu auctionGroup; group khai báo hai role, kiểu agent auction_capabilities
và Entity Auction. XML vẫn **8 correspondence thuộc tính**, không thêm tên
agent, Jason plan hay iStar Task. iStar vẫn 4 viewpoint, 3 dependency và 2
participates-in, đọc chính các OrgCtx/player trong CSL.

Ở snapshot cuối của ca gốc: **14 object CSL**, gồm 5 agent OrgCtx, 1 organization,
1 group, 5 role occurrence và 2 Auction. Có 1 membership group→organization;
5 player agent đến từ roster Moise. Không có AtomicAgent. Producer không tự suy
direct membership của agent từ roster role. Multi-membership và organization
làm player ở cấp cao hơn được kiểm thử ở kernel/snapshot trung lập; không tuyên
bố Moise producer đã quan sát enactment tổ chức nếu nguồn chưa có fact đó.

Phần này sửa một hạn chế **nhóm 1: ngữ nghĩa biểu diễn/projection**, thay vì thêm
đối tượng phụ vào riêng ví dụ để làm nó chạy được. Backend legacy giữ profile cũ;
module state-verification chọn profile mới một cách tường minh.

## 2. Tiêu chí phân loại

Phân loại theo **từng yêu cầu kiểm tra**, không gán nhãn đúng/sai cho cả phương pháp:

1. Xác nhận lỗi chương trình bằng witness độc lập với CSL/iStar.
2. Giữ nguyên producer, dữ liệu, binary checker và trace. Chỉ đổi CSL, iStar
   hoặc correspondence trong mapping.
3. Nếu sửa đặc tả có thể phân biệt ca sai với ca đúng bằng dữ liệu đã có:
   **nhóm 2 — lỗi đặc tả** đối với tính chất đó.
4. Nếu các trạng thái mà yêu cầu coi là đúng/sai không phân biệt được từ dữ liệu
   evaluator nhận, hoặc thiếu ngữ cảnh khởi tạo/đánh giá nghĩa vụ hoàn thành:
   **nhóm 1 — hạn chế phương pháp thực thi/quan sát**. Không kết luận rằng bản
   thân OCL hay ba khái niệm CSL không đủ sức biểu đạt.
5. Một rule bắt mutant nhưng cũng chặn ca đúng bị loại: đó không phải bản sửa hợp lệ.

`APPROVE` hiện có nghĩa **không thấy vi phạm từ dữ liệu/đặc tả hiện có**.
Task/Achieve còn PENDING không tự thành lỗi. HOLD_FAILURE khi invariant false
hoặc goal VIOLATED; dữ liệu không đủ/không đọc được là HOLD_UNVERIFIED.

## 3. Các lỗi trong chương trình được kiểm tra

| Ca lỗi đã xác nhận | Kết quả checker hiện tại | Phân loại nguyên nhân khi bỏ sót |
| --- | --- | --- |
| Artifact nhận giá âm | CSL ValidBid và iStar SafeBid chặn; không chạy plan after_bid_1 | Đã phát hiện trong phạm vi này |
| Giá tốt nhất tăng từ 10 lên 20 | iStar BidNeverIncreases chặn; các invariant CSL vẫn đạt; không chạy after_bid_2 | Đã phát hiện trong phạm vi này |
| Auction chạy mà auctioneer không focus | CSL RunningAuctionIsObserved chặn; không chạy after_start | Đã phát hiện trong phạm vi này |
| Start hoàn thành nhưng running vẫn false | APPROVE; after_start chạy; start còn chờ | **1 — thiếu milestone/lifecycle để phân biệt trạng thái trung gian hợp lệ với start đã hoàn thành sai** |
| Start không tạo Artifact nào | APPROVE; after_start chạy; không có graph Auction để kiểm tra | **1 — lấy Artifact làm điểm khởi tạo graph, thiếu Request/Scheme có trước nó và nghĩa vụ tạo object** |
| Artifact ghi task khác Service được yêu cầu | APPROVE; cả hai AuctionCompleted fulfilled | **1 — chưa quan sát/map tham số Service từ Scheme; chỉ có giá trị task thực tế** |
| Stop công bố winner=ghost, không thuộc participant | APPROVE; cả hai AuctionCompleted fulfilled | **2 đối với điều kiện winner phải là participant của cohort cố định:** roster/name đã có, nhưng thiếu rule. Điều kiện này chưa chứng minh đúng người thắng |
| Stop công bố một participant hợp lệ nhưng khác người có bid thắng | APPROVE; cả hai AuctionCompleted fulfilled | **1 đối với tính đúng người thắng:** chưa có bid-owner/best-bidder hoặc lịch sử bid gắn player trong dữ liệu đầu vào |

Đặc biệt, `wrong_winner` và `wrong_eligible_winner` là hai yêu cầu khác nhau:
**kiểm tra người thắng thuộc tập hợp hợp lệ** có thể sửa bằng đặc tả hiện tại;
**kiểm tra đó đúng là người thắng** chưa thể suy từ giá thấp nhất và tên winner.
Không gọi việc thêm `WinnerIsParticipant` là đã kiểm chứng thuật toán chọn winner.
Nếu roster có thể thay đổi trong phiên, rule này còn cần participant-at-bid hoặc
membership lịch sử; chiến dịch hiện dùng cohort cố định.

Thiếu `best_bid` là ca thứ 12 thuộc kiểm tra coverage: HOLD_UNVERIFIED, không
chạy after_start. Đây là bảo vệ thiếu dữ liệu, không phải phát hiện lỗi nghiệp vụ.
Hai đối chứng hợp lệ đều không báo lỗi, gồm bid kém hơn không đổi giá tốt nhất
và dịch vụ có tên `wrong_service` khi đó chính là tên dịch vụ được yêu cầu.
Ca gốc chạy 123 checkpoint, 2 AuctionCompleted; không có oracle độc lập bao phủ
mọi yêu cầu của chương trình gốc nên không suy rằng toàn bộ chương trình đúng.

## 4. Thí nghiệm chỉ đổi đặc tả trên cùng trace

Có **17 phép replay**; [assessment.json](specification-replays/assessment.json)
ghi hash từng trace, factor thay đổi, decision và checkpoint đầu tiên giữ dừng.

| Chỉ thay một factor | Kết quả | Ý nghĩa |
| --- | --- | --- |
| Bỏ invariant focus trong CSL | Ca thiếu focus đổi từ HOLD ở #36 sang APPROVE trên prefix 36 snapshot | Ví dụ lỗi **nhóm 2** nếu người viết bỏ điều kiện cần thiết; dữ liệu focus đã đủ |
| Bỏ Maintain BidNeverIncreases trong iStar | Ca giá tăng đổi từ HOLD ở #50 sang APPROVE trên cùng prefix | Ví dụ lỗi **nhóm 2** ở iStar; propagation/observation hiện tại đã bắt được khi rule tồn tại |
| Viết nhầm best_bid thành best_bdi trong XML | Ca đúng thành HOLD_UNVERIFIED ở #36 với ATTRIBUTE_VALUE_MISSING | Ví dụ lỗi **nhóm 2** ở cấu hình mapping; provider đã có trường đúng |
| Chỉ thêm WinnerIsParticipant | ghost bị HOLD ở #60; cả hai đối chứng đúng vẫn APPROVE | Xác định phần thiếu đặc tả ở bảng trên; không cần thêm source data/plan mapping |
| Cùng WinnerIsParticipant, nhưng winner là participant khác người thắng | Vẫn APPROVE 101 checkpoint | Bằng chứng rule trên chỉ là điều kiện cần |
| Cấm literal wrong_service | Bắt sai dịch vụ, nhưng cũng chặn dịch vụ hợp lệ có chính tên đó, cùng ở #36 | Loại bản sửa hardcode; thiếu expected Service không được chữa bằng blacklist |
| Bắt mọi Auction luôn running | Bắt start sai, nhưng chặn cả ca đúng khi stop ở #56 | Loại bản sửa lẫn vòng đời |
| Bắt mọi group luôn có Auction | Chặn cả ca thiếu Artifact và startup hợp lệ ngay #1 | Loại bản sửa lẫn nghĩa vụ khởi tạo |

Các replay dừng tại HOLD đầu tiên. Khi đổi decision, không sử dụng phần sau để
dự đoán JaCaMo sẽ hành xử thế nào. Chúng chứng minh quan hệ đặc tả–decision trên
prefix đã ghi; bằng chứng gate thực sự chặn plan tiếp theo nằm trong các ca live.
Các variant chỉ nằm ở thư mục chẩn đoán, không thay âm thầm bộ model chính.

## 5. Vì sao một số lỗi thuộc nhóm 1 chứ không chỉ thiếu OCL?

[MethodVersusSpecificationTest](../../modules/state-verification/src/test/java/org/tzi/use/state/MethodVersusSpecificationTest.java)
có 5 kiểm thử đối chứng:

- Cùng dữ liệu Artifact=false có thể là tạo object xong nhưng chưa start, hoặc
  là start đã hoàn thành sai. Oracle biết milestone phân biệt được; evaluator
  chỉ đọc state hiện tại không được cấp milestone đó.
- Không có Artifact là hợp lệ ở startup và sai sau nghĩa vụ tạo đã hoàn thành.
  Dữ liệu Request/Scheme không đi vào CSL hiện tại; graph còn chưa được tạo.
- Cùng task=A là đúng nếu Service=A và sai nếu Service=B. Tham số kỳ vọng không
  nằm trong snapshot, nên so task với một literal không giải quyết được tổng quát.
- **Chỉ trong fixture chẩn đoán**, khi bổ sung expectedService làm dữ liệu và
  mapping thuộc tính, OCL `task = expectedService` phân biệt được đúng/sai.
- Tương tự, khi fixture có best_bidder, OCL so winner với expectedWinner phân
  biệt được. Bỏ constraint thì dữ liệu đó tự nó không sinh vi phạm.

Hai fixture cuối chứng minh CSL/OCL **biểu đạt được phép so sánh khi có dữ liệu**.
Chúng không phải producer live đã có Scheme hoặc best_bidder. Thêm các quan sát
đó thuộc thay đổi phương pháp/adapter; sau đó vẫn cần đặc tả đúng để sử dụng nó.
Tên plan/outcome trong audit không được đưa vào iStar làm receipt cho Task.

## 6. Kiểm chứng và khả năng tái lập

- Semantic engine: **110 tests**, state-verification: **23 tests**, không failure/
  error/skipped, gồm live test hai JVM cho Counter. Xem
  [unit-test-summary.json](unit-test-summary.json) và hai build log bên cạnh.
- Live campaign: **12 ca** = 1 gốc + 2 hợp lệ + 8 lỗi nghiệp vụ + 1 coverage.
  [assessment](live/assessment.json) trả `FAIL_KNOWN_COUNTEREXAMPLES` vì còn
  5 lỗi bị bỏ sót. Exit code 2 là kết quả đánh giá checker thất bại, không phải
  Maven/JaCaMo không khởi động.
- Source mutant, diff, oracle log, trace, decision và probe plan tiếp theo đều
  nằm trong từng thư mục con của [live](live/). Các hash code, binary, model gốc
  được giữ nguyên trong cả campaign/replay.
- 17 diagnostic replay đạt kỳ vọng, **bao gồm kỳ vọng rằng rule sửa sai sẽ báo
  lỗi trên ca đúng**. Đây không phải tuyên bố checker đã đúng toàn diện.

Chạy từ repository `use`, sau khi build module state-verification:

```bash
python3 use-plugin/tools/verify-state-checker.py \
  --output /tmp/auction-classification-new --seconds 8 --baseline-seconds 18
python3 use-plugin/tools/classify-state-faults.py \
  --campaign /tmp/auction-classification-new --output /tmp/auction-replays-new
```

Kết quả ban đầu ở `baseline/` bị dừng vì đối chứng Service viết hoa không hợp lệ:
Moise parse `WRONG_SERVICE` thành biến Jason, dẫn đến lỗi tham số operation.
Không dùng lần đó để phân loại phương pháp; giữ log để kiểm tra. Đã sửa đối chứng
thành atom hợp lệ `wrong_service`, kiểm tra riêng ở `service-positive-control/`,
rồi chạy lại **toàn bộ** ở `live/`. Không đổi Artifact/ASL gốc của project.

Giới hạn còn lại: cut sau terminal plan không phải transaction cô lập, không
rollback side effect; iStar hiện có graph theo Auction chứ chưa theo từng agent;
không chứng minh mỗi participant đã thực hiện bid. Các giới hạn này và các ca
chưa có oracle không được chuyển thành kết luận mọi lỗi đã được tìm thấy.
