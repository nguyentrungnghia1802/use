# Stock observer: second authored specification

Dùng cùng `.jcm`/ASL/Java/MOISE ở thư mục cha, không thêm Java mapper.
Trong Workbench chọn `JACAMO_DECLARATIVE_XML` + `AUTHORED`, XML `stock-mapping.xml`,
CSL `stock.csl`, iStar `stock.istar`, OCL `stock.ocl`; `Prepare + Import...` chọn
`../counter.jcm`, rồi `Start Runtime`.

XML khai báo rõ Counter.p → Pallet.weight và group team → Warehouse. StockObserver
là viewpoint của từng context hiện hữu, không phải agent hay population role.
Mỗi physical group có đúng một viewpoint do specification này định nghĩa;
`viewpointDeclared` xác nhận đúng loại source row, không chứng minh toàn bộ group
population. Runtime vẫn kiểm tra identity/current cut của context đó.

Source Counter tạo giá trị 0 → 1 → 2 → -1. Native invariant NonnegativeWeight
phải FAIL ở -1 khi có observed population đầy đủ. StockSafe là Achieve goal nên
original historical marking có thể giữ fulfilled sau lần thỏa trước đó; không
được ép nó thành current invariant. Thiếu population/evidence giữ quality riêng.
Scope này không tuyên bố kiểm chứng Task invocation, MOISE sequence hoặc deadline.
