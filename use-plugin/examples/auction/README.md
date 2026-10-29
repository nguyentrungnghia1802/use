# Auction — bộ input hiện hành

Mở USE (Java 21) → Plugins → JaCaMo → use4Goal verification.
Chọn năm inputs ngay trong thư mục này:

1. CSL: `auction.csl`
2. JaCaMo: `auction.jcm` (hoặc project Auction tương thích tại vị trí khác)
3. iStar: `auction.istar`
4. Mapping: `auction-mapping.xml`
5. OCL: `auction.ocl`

Bấm Prepare + Import, rồi Start Runtime. JaCaMo chạy process riêng qua TCP;
USE giữ một authoritative MSystem. Kết quả ở Execution & Results.

Contract v3: Participant hoàn thành lượt tham gia theo callback/XML; chỉ nhánh
.fail mô phỏng được khai báo đúng source site và mã lỗi được chấp nhận.
Auction vẫn cần bid thực sự được Artifact chấp nhận. Missing không thành PASS.
COMPLETE_OBSERVED nói về requirements quan sát được, không phải full state.
WithinDeadline vẫn tắt theo yêu cầu trước; không tuyên bố kiểm chứng deadline.

Source runtime nằm trong `src/` và `build.gradle`. Không có mapper Java riêng.
Các profile khác, model cũ, Draw.io, provenance và license/source parity fixtures
đã chuyển tới `use-plugin/src/test/resources/auction-reference` trong repository;
không dùng chúng thay bộ input hiện hành. Byte gốc và hash được giữ ở đó.
Trong distribution: bộ này ở `examples/auction-current`, reference ở `reference/auction`;
`examples/auction` trong ZIP vẫn là toy native cũ cho compatibility.
