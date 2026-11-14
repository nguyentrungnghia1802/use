# USE — CSL, OAL, iStar và JaCaMo

Project có bốn Maven module:

```text
use-core/       Lõi USE, UML và OCL
use-gui/        Giao diện USE
use-plugin/     Năm chức năng mô hình/bridge trong một source module
use-assembly/   Đóng gói bản USE chạy được
doc/            Kiến trúc và một báo cáo kiểm thử tổng quan
```

## Build và chạy

Cần JDK 21 và Maven 3.9. Chạy lệnh tại thư mục chứa file `pom.xml` này.
Lần đầu, import các JAR runtime được cung cấp vào Maven local repository:

```bash
python3 use-plugin/tools/install-runtime.py
mvn -B package -Dmaven.test.skip=true
bash use-assembly/target/new/bin/use
```

`USE_MAVEN` có thể trỏ tới Maven executable khi Maven chưa có trong `PATH`.
Lệnh package trên bỏ qua test; cách chạy test ở [doc/TESTING.md](doc/TESTING.md).
Windows dùng `use-assembly/target/new/bin/start_use.bat`.

Trong USE, chọn **Plugins → JaCaMo → Run experiment...**, rồi chọn project `.jcm`,
CSL, iStar và mapping thuộc tính. JaCaMo và kết quả CSL/iStar hiển thị cạnh nhau;
USE quản lý khởi chạy, dừng và khởi động lại phiên.

## Tìm source ở đâu?

- Code tích hợp: `use-plugin/src/main/java/org/tzi/use/plugins/jacamo/`.
- Entry point: `feature/{csl,oal,istar,istarscn,bridge}`.
- Frontend và view: `dsl/`; đánh giá native: `verify/`.
- Interface và checkpoint JaCaMo: `runtime/`; mapping và kết nối: `bridge/`.
- Parser gốc: `org/vnu/sme/goal/dsl/{csl,istar}/parser/` trong cùng `src/main/java`.
- Grammar ANTLR: `use-plugin/src/main/resources/grammars/`.
- Ví dụ: `use-plugin/src/main/resources/example/{auction,counter}/`.
- Test và fixture: `use-plugin/src/test/`; output sinh ra chỉ ở `target/`.

Auction có `.jcm`, `.csl`, `.istar`, `attribute-mapping.xml` và source JaCaMo trong
cùng thư mục. Counter minh họa việc giữ dừng khi phát hiện vi phạm. Hai ví dụ cũng
được đưa vào `examples/` của bản USE đóng gói.

[Kiến trúc và ngữ nghĩa](doc/ARCHITECTURE.md) · [Báo cáo kiểm thử tổng quan](doc/TESTING.md)
