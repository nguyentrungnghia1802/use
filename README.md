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

Trong USE, chọn **Plugins → JaCaMo → Run experiment...** và mở một tệp `.mrv`.
Hộp thoại mở tại `use-plugin/src/main/resources/example` khi chạy trong workspace
hoặc tại `examples/` của bản cài độc lập. Ví dụ `auction/auction.mrv` và
`counter/counter.mrv` nằm ngay ở thư mục gốc của từng case.

`.mrv` là tệp ZIP với `mrv.json` (phiên bản 1) ở gốc chỉ rõ bốn tệp
`.jcm`, XML mapping, `.csl` và `.istar`. Gói cũng chứa cây `src/` của JaCaMo,
gồm mã agent, tổ chức và mã Java của Artifact nếu có. USE giải nén theo cấu
trúc dự án, rồi tự biên dịch Artifact từ `src/env` cạnh tệp `.jcm`.

Để cập nhật một gói sau khi sửa nguồn, chạy lệnh sau trong thư mục của case.
Với tài nguyên ngoài `src/`, thêm `--include FILE` cho từng tệp cần đóng gói.

```bash
python ../../../../../tools/pack-mrv.py auction.jcm attribute-mapping.xml auction.csl auction.istar
```

`WorkbenchMain` nhận `experiment.mrv [--headless] [--seconds=N]` và hỗ trợ cú
pháp bốn đường dẫn cho các script hiện có.

Phiên bản plugin lấy từ `revision` trong POM gốc. Có thể thử bản đóng gói với
phiên bản khác bằng `mvn -B package -Drevision=1.0.2`.

## Tìm source ở đâu?

- Code tích hợp: `use-plugin/src/main/java/org/tzi/use/plugins/jacamo/`.
- Entry point GUI và CLI: `entrypoint/`.
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
