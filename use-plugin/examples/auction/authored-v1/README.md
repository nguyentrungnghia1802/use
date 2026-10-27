# Auction authored models v1

Bộ input này giữ nguyên bytes của CSL/iStar/OCL Desktop đã ghi trong `provenance.json`.
Đây là mode authored mới; không thay thế profile generated hoặc recording lịch sử.

Trong JaCaMo Workbench của USE, chọn:

1. Profile `JACAMO_DECLARATIVE_XML`, model input mode `AUTHORED`.
2. Mapping: `authored-v1/auction-mapping.xml`.
3. OCL: `authored-v1/auction_constraints_final_fixed.ocl`.
4. CSL: `authored-v1/auction_csl.csl`.
5. iStar: `authored-v1/auction_istar_final.istar`.
6. Đặt `journal quota (MiB)` là `256` trước import cho run Auction đầy đủ. Mặc định vẫn là 64 MiB; khi hết quota, journal ghi GAP và kết quả không còn được coi là đầy đủ.
7. `Prepare + Import...`: chọn `auction.jcm` ở thư mục cha của `authored-v1`.
   Khi kết nối producer đã chạy bên ngoài, dùng `Import JaCaMo Project...`.
8. Sau khi import/capability gate cho phép, `Start Runtime` giải phóng reasoning.
   `Pause Jason`/`Resume Jason` giữ semantics ACK tại cycle boundary.
9. `Stop JaCaMo` chỉ dừng process do Workbench này sở hữu;
   `Stop monitor` chỉ dừng consumer. `Restart JaCaMo` chuẩn bị run mới và import lại;
   bấm `Start Runtime` để bắt đầu run đó.

Trong checkout đã build, classpath dùng
`jacamo-bridge-jacamo/target/jacamo-bridge-classpath.txt`. Trong distribution,
dùng `Runtime libs...` chọn file text chứa classpath các JAR framework JaCaMo
được cài trên máy; script dùng các Bridge JAR đi kèm distribution.
JDK 21 và Windows PowerShell cần có để chuẩn bị bản sao project. Project có
`gradlew.bat` được build trong staging; source gốc không bị chỉnh sửa.

CSL/iStar là canonical inputs; Java không sinh graph khác thay chúng.
Role `auction_capabilities` là definition-only abstraction; không tạo parent enactment.
Belief attributes là JSON chứa stored literals/rules của đúng player, không phải world truth.

**Giới hạn acceptance hiện tại:** xem `plan.md` và evidence `verification/authored-models`.
Static/native và component gates không thay cho live/package/main-GUI acceptance.
MOISE provider hiện không cung cấp exact internal transition timestamps;
`SchemeOrder`/`BidEvidence` không được gán timestamp publication để tạo PASS giả.
Thiếu source evidence phải hiển thị undefined/INCOMPLETE; không hiểu SKIPPED là PASS.
