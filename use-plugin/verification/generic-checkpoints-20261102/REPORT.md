# Sửa Verified Checkpoints dùng chung — 2026-11-02

## Phạm vi và nguyên tắc

Đường runtime/mapping dùng chung chọn nguồn từ 5 input đã admission và policy
có version; không phân nhánh theo tên case, agent, Auction ID, số lượng kết quả,
winner hay số milestone Swing. Không thay CSL/iStar/OCL hoặc ép verdict để qua test.
Một MSystem và native Goal/Task/OCL engines tiếp tục là nguồn kết quả có thẩm quyền.

Kế hoạch và ranh giới: [PLAN.md](PLAN.md), [CAPABILITY-CONTRACT.md](CAPABILITY-CONTRACT.md).
Không thể suy ra hỗ trợ mọi chương trình Java/remote chưa có hook. Case mới phải
có mapping, source milestones/policy và capability được admission; thiếu evidence
hoặc capability không được tự coi là PASS.

## Nguyên nhân đã sửa

1. Admission chưa giải quyết include theo search path thực tế, đặc biệt URI
   `file:` tương đối; GUIArtifact và clock của Java source bị từ chối dù có thể
   bổ sung boundary. Giờ include được resolve/pin, callback GUI được kiểm tra,
   bytecode clock có nguồn/classpath xác định dùng logical clock chung. Raw
   thread/timer/domain callback chưa được bao phủ vẫn bị từ chối.
2. UI chỉ nhận scope có Entity nên workspace/project viewpoint hợp lệ bị mất
   execution/state. Giờ hỗ trợ cả hai topology; ownership theo declared join đến
   physical context, không dựa tên trường hoặc player dùng chung. Không có XML
   presentation thì chiếu các khai báo CSL và CLASS observer từ cùng frozen cut.
3. Milestone cũ tách thêm khi Task/check thay đổi dù CSL state giống nhau. Giờ
   consecutive scoped objects/attributes/links giống nhau cùng một milestone;
   mọi audit cut, native Task transition, failure và source token vẫn giữ nguyên.
   Fixture từ recording người dùng: 8 cuts -> 4 state milestones, phân bố
   `[1,1,2,2,3,3,4,4]`; không thay native assertions.
4. Observer có zero rows nhưng source/table COMPLETE bị coi là không có proof.
   Giờ có certificate exact program/epoch/cut/source proof cho population rỗng.
   Universal/existential giữ native semantics; partial cut mới thu hồi certificate.
5. Validation gọi `Set.of(...).contains(null)` gây NPE thay vì diagnostic nghiệp
   vụ. Giữ nguyên rule yêu cầu abstraction metadata; input không hợp lệ vẫn reject.
6. House journal chạm quota do JSON evidence lớn lặp lại. Verified declarative
   journal dùng lossless GZIP members, vẫn giữ toàn bộ record/hash chain; quota
   vật lý không đổi và vẫn fail closed. Export là plaintext canonical JSONL;
   schema 3.5 khai báo decoded budget có giới hạn. Có kiểm tra corruption, đọc
   concurrent prefix, quota và phục hồi exact completion đã rời RAM tail.
7. MOISE definition bị giải mã/so sánh toàn bộ trên EDT cho từng fact. Cache có
   giới hạn theo hash đầy đủ gồm provenance, thuộc admitted source; live identity,
   epoch, group và roster vẫn kiểm tra từng fact. Test đổi semantic shape,
   provenance, epoch và roster vẫn reject.
8. Lượt live cuối phát hiện GAP trước lỗi transport/resync: local CArtAgO capture
   chỉ có một giây để tìm stable interval trong khi nhiều agent thay đổi state.
   Terminal source marker đã được policy chọn giờ đóng admission trước capture;
   permit hiện tại buộc READY chờ evidence được publish. Vẫn kiểm tra sequence,
   topology và COMPLETE; 16 attempts với local capture budget 10 giây, không đổi
   verification timeout 30 giây. Không tìm được cut vẫn GAP/hold. Test dùng
   operation thật làm invalid hai lần rồi chứng minh lần thứ ba ổn định sau >1s.
9. Cửa sổ desktop có lúc vẽ trắng dù EDT và dữ liệu nội bộ vẫn hoạt động. Lượt
   software rendering vẽ đúng; launcher Windows chính dùng
   `-Dsun.java2d.d3d=false -Dsun.java2d.noddraw=true`. Acceptance desktop cuối dùng
   đúng hai cờ này. Đây là cấu hình rendering, không sửa state hoặc verdict.
10. Launcher chạy PowerShell 5 qua cmd có thể kế thừa module path của PowerShell 7,
    khiến Get-FileHash không được load. Script pin runtime dùng SHA-256 .NET trực
    tiếp. Test qua PowerShell 5/cmd chấp nhận bytes đúng, từ chối bytes bị sửa;
    chính start_use.bat đã khởi động USE thành công với -nogui và EOF.

## Evidence hiện tại

| Nhóm | Evidence | Kết quả |
|---|---|---|
| Source/include/GUI/clock | G01-relative-source-path-tests.log | Gradle source tests qua |
| Barrier operation/timer/GUI/queue/lifecycle | G02-gui-probes.log | 6 component probes qua |
| Native mapping/UI/review/architecture | G06-final-focused.log | 86 tests, 0 fail/error/skip |
| Journal/exact completion/replay budget | G06-storage-final-tests.log | 28 tests, 0 fail/error/skip |
| Early admission và stable interval | G02-G06-source-order-fix.log | 5 tests, 0 fail/error/skip |
| Package sau source-order fix | G08-source-order-package.log | 5 adapter + 24 plugin tests qua; package thành công |
| Installed loader và runtime identity cuối | G08-final-final-package-receipt.json | PASS, built/installed bytes khớp |
| Class bytes sau package launcher | G08-class-byte-parity.json | Tất cả .class của 4 JAR không đổi |
| Hash bootstrap qua PowerShell 5/cmd | launcher-hash-probe/receipt.json | Bytes đúng được nhận, bytes bị sửa bị từ chối |
| File chạy thật | G08-real-launcher-final.log | USE 7.5.0 khởi động thành công |

Các lượt test có overlap; không cộng thành số unique tests. `csl-counter/authored-v1`
được kiểm tra static mapping ở `G06-legacy-counter-inputs.json`; đây không phải
live checkpoint acceptance hoặc bằng chứng đã có policy milestones của bundle cũ.

## Live acceptance cuối

Tổng hợp máy đọc được: [FINAL-RECEIPT.json](FINAL-RECEIPT.json).

| Case | Evidence cuối | Kết quả |
|---|---|---|
| External Auction | G07-closeout-auction-2/receipt.json | 2 native roots và 2 UI executions hoàn tất; 9 tự release; 2 review trên desktop ở hai token khác nhau |
| Hello World | G07-closeout-hello-world/receipt.json | 2 native elements FULFILLED; UI COMPLETE_OBSERVED; 14 cut đã hiển thị trước tự release; replay 993 entries khớp cả hai hash cuối |
| House Building | G07-house-stable-artifacts/receipt.json | 15 native elements FULFILLED; UI COMPLETE_OBSERVED; 29 cut đã hiển thị trước tự release; replay 4.109 entries khớp cả hai hash cuối |

Auction đổi tab qua lại vẫn giữ scope/cut/state count riêng; hai execution có 3 và
4 state milestones tùy state thật của run. Hai lần click Acknowledge & Continue
được thực hiện qua Computer Use trên cửa sổ đang hiển thị; ảnh review-0.jpg và
review-1.jpg nằm cùng receipt. Native failure/unknown rows vẫn hiển thị sau khi
release, không bị đổi thành PASS. Swing heartbeat p95 109 ms, max 738 ms.

Hello/House dùng live MAS và JFrame thật, UI được kiểm tra bằng component API;
không gọi đây là manual desktop acceptance của hai case đó. Không có manual
acknowledgement trong hai run này. Mỗi released command được đối chiếu với
DISPLAYED trước đó, cùng toàn bộ identity và result hash. Manifest input hashes
và source Auction gốc được giữ nguyên.

House giữ journal nén 50.861.180 bytes (gồm phần shutdown sau export), dưới quota
512 MiB; canonical plaintext replay giữ đủ 593.432.107 bytes. Hello tương ứng
13.400.999 và 178.035.065 bytes. Bản journal gốc đã copy vào từng thư mục evidence
và đối chiếu SHA-256. Không giảm số record hoặc bỏ evidence để đạt quota.

## Phân biệt evidence và giữ lịch sử

Các thư mục `G07-hello-*`, `G07-house-*`, `G07-final-*` và các Auction chẩn đoán
được giữ lại, gồm cả failures. Chúng mô tả từng phiên bản trong quá trình sửa,
không thay cho acceptance cuối. `G07-final-house-building/replay-checked.json`
đã kiểm tra 4.271 entries và hash cuối; `G07-final-hello-world` kiểm tra 987 entries.
Một replay của implementation cũ bị từ chối `REPLAY_DECLARATIVE_IMPLEMENTATION_INCOMPATIBLE`
sau khi đổi code; không nới fingerprint để làm replay cũ xanh.

Các lượt review hết thời gian khi phiên bị ngắt và lượt cửa sổ vẽ trắng được giữ
nguyên, không tính là acceptance. Harness closeout chỉ tăng wall-clock chờ toàn
scenario 200 -> 420 giây, chờ thao tác review 75 -> 300 giây và pha review tổng
150 -> 660 giây; giữ nguyên mọi semantic/identity/UI/receipt assertions và timeout
production. Xem desktop-closeout/README.md. Lượt 200 giây có đủ hai native root
FULFILLED ở completion cuối nhưng quá hạn harness, vẫn lưu là thất bại.

Một export House trùng lúc build ghi lại class resources đã bị từ chối
REPLAY_IMPLEMENTATION_RESOURCE_MISSING. Không nới guard; run này giữ nguyên.
Acceptance House cuối chạy sau khi artifacts ổn định, export và replay đầy đủ.

Bản cài trước và mọi logs/replays được giữ tại
`use-assembly/target/new-before-generic-20261102`; package trước source-order cũng
giữ tại `new-before-generic-source-order-20261102`. Producer mồ côi cũ được yêu cầu
dừng qua stop.flag của đúng run trước khi lưu bản cài, không dùng kill làm pause.

## Bản chạy

`D:\_CODE_BANK\Project_\08_Thesis\use\use-assembly\target\new\bin\start_use.bat`

JaCaMo được nạp từ `new/bridge/runtime/jacamo-1.3.1-checkpoint-SNAPSHOT.jar`:
SHA-256 `7f3e0a1267d8bc6e20a2a975e6e3600eb6064d0a13b93b7c843d1c81d1bf33b2`.
Installed smoke: `INSTALLED_VERIFIED_CHECKPOINT_LOADER_SMOKE_PASS`;
runtime: `BRIDGE_CHECKPOINT_STRICT_SUPPORTED=true`.

Hash source và file cài cuối: source-artifact-hashes.json. Các producer/harness
được dọn theo lifecycle; không còn live case test nào chạy khi closeout.

Chưa commit/push. Không chạy full clean verify.
