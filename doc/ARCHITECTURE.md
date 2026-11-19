# Kiến trúc plugin

## Tổ chức source

Cấu trúc theo chức năng được tham khảo từ `use-goal-BPMN/goal/src/main`.
Plugin dùng một `pom.xml`, một cây `src/main/java`, một cây resource và một cây
test. Không còn các Maven module lồng nhau cho từng thành phần nhỏ.

```text
use-plugin/
  src/main/java/
    org/tzi/use/plugins/jacamo/
      integration/use/ Đăng ký plugin và action với USE
      gui/main/        Cửa sổ và hộp thoại
      gui/views/       Panel, cây mô hình và thành phần vẽ dùng chung
      runtime/         Khởi chạy JaCaMo, chọn checkpoint, thu thập trạng thái
      bridge/          Kiểu dữ liệu snapshot dùng chung
        mapping/       Đọc XML và chiếu dữ liệu nguồn sang CSL
        protocol/      Mã hóa, xác thực và trao đổi checkpoint
      session/         Vòng đời JVM con, lịch sử và cập nhật trạng thái USE
      verify/
        api/           Profile, chẩn đoán và kiểm tra cài đặt
        use75/         Mô hình native USE và OCL
        istar/         Đánh giá Goal/Task và lịch sử đánh giá
      tool/main/       Điểm vào CLI cho workbench và replay
    org/vnu/sme/goal/
      dsl/             Frontend, AST, metamodel và diagram CSL/iStar
      verify/          Ngữ nghĩa CSL và iStar tái sử dụng từ USE4Goal
  src/main/resources/
    grammars/           CSL.g4 và IStar.g4; ANTLR sinh parser vào target/
    example/            Auction và Counter, gồm cả source JaCaMo
    plugins/            Descriptor của các entry point mô hình
    META-INF/           License, attribution và thông tin nguồn gốc
  src/test/             Test và fixture phục vụ tái kiểm tra
  lib/checkpoint/       JAR/POM runtime được cung cấp, không phải source viết lại
  tools/                Import runtime, chạy và kiểm tra
```

Namespace `org.vnu.sme.goal` chứa frontend, mô hình và thuật toán kiểm chứng
được đối chiếu với USE4Goal. Namespace `org.tzi.use.plugins.jacamo` chứa
phần tích hợp JaCaMo, phiên chạy và giao diện plugin.

Một lần build tạo năm entry JAR cho CSL, OAL, iStar, iStarSCN và bridge. Bốn JAR
entry nhỏ tham chiếu triển khai dùng chung trong JAR plugin chính. Parser/engine
không bị nhân bản; không cần năm cây Maven source khác nhau. Assembly đưa cả bộ
vào USE. Các JAR JaCaMo được đóng gói riêng cho JVM nguồn.

## JaCaMo là thư viện được import

`JaCaMoRuntime` gọi `JaCaMoLauncher` gốc để đọc JCM, tạo hệ thống và chạy agent.
Jason thực thi plan;
CArtAgO thực thi Artifact; Moise/NPL quản lý tổ chức. Project không có source thay
thế các package framework `jacamo`, `jason`, `cartago` hay `moise`.

`JaCaMoCheckpoints` quan sát vòng đời plan, chọn checkpoint và giữ JaCaMo
cho đến khi USE chấp thuận. `StateProducer` thu thập snapshot đã đóng băng.
Bridge ánh xạ dữ liệu và trao đổi yêu cầu, kết quả. Runtime chạy trong JVM do USE
quản lý để tách thư viện nguồn khỏi classloader giao diện; người dùng không cần
mở ứng dụng JaCaMo riêng.

`VerificationSession` điều phối JVM con, kênh xác thực và lịch sử. Nó gọi
`SnapshotVerifier` để cập nhật `MSystem` và đánh giá CSL/iStar. Cửa sổ trong
`gui.main` điều khiển phiên, còn thành phần trong `gui.views` nhận kết quả để
hiển thị. Đóng workspace hoặc thoát USE sẽ dừng tiến trình thuộc phiên đó.

`ReplayMain` đọc snapshot đã ghi và báo cáo các phần tử hoàn thành qua trường
`fulfilledElements`. Mỗi mục chứa context, actor và tên phần tử theo mô hình
iStar được nạp cho lần replay.

### Ranh giới checkpoint

- Một lần chạy plan gốc cùng các plan con gọi đồng bộ trên cùng intention tạo
  một checkpoint khi plan gốc kết thúc. Quy tắc áp dụng cả cho thư viện ASL.
- Các plan độc lập, `!!Goal` và các nhánh intention mới vẫn có checkpoint riêng,
  kể cả khi chạy trên cùng agent. Không gom theo tên agent hay tên file.
- Message sang agent khác được giữ trước khi gửi, tạo checkpoint chuyển giao.
  Message ở cuối chuỗi plan được giữ đến khi plan gốc hoàn thành và được chấp
  thuận; nhờ đó kết thúc plan và trả kết quả dùng chung một checkpoint.
  Ask có chờ trả lời không được giữ đến cuối plan, vì sẽ gây chờ vòng.
- Thất bại/hủy của plan gốc cũng tạo checkpoint. Plan con cùng nhóm không tự
  tạo thêm checkpoint; lỗi không có plan tương ứng được quan sát ở cấp goal.
- Snapshot khởi tạo được kiểm tra riêng. Một checkpoint không bị bỏ qua vì
  giá trị không thay đổi. Request ghi invocation, intention, loại ranh giới,
  số plan con đã gom và các message đang được chuyển giao.

Ba checkpoint A giao việc → B hoàn thành → A hoàn thành áp dụng khi A chờ B
trả kết quả. Message bất đồng bộ, nhiều người nhận hoặc các lần chuyển giao
tiếp theo có số checkpoint theo luồng thực thi thực tế. Các goal phát sinh qua
Moise/artifact là các intention độc lập ở bên nhận; không suy đoán quan hệ
cha–con từ tên goal trùng nhau.

Phạm vi kiểm chứng là trạng thái tại ranh giới plan/nhóm plan. Trạng thái tính
toán bên trong nhóm không được kiểm tra từng bước; người thiết kế phải phân
chia plan phù hợp. Bộ công cụ không bảo đảm bắt lỗi tạm thời đã biến mất trước
ranh giới này. Thất bại thực thi và vi phạm OCL là hai thông tin khác nhau:
việc nhận checkpoint FAILED không tự biến một OCL đang đúng thành sai.

## CSL, iStar và mapping

- **OrgCtx** là chủ thể cá nhân hoặc tập thể; organization cũng có thể đóng role.
- **Role** là occurrence trách nhiệm, có player OrgCtx và context OrgCtx.
- **Entity** là thực thể phi ý định, không làm role player.
- Membership và enactment độc lập. `memberOf`/`members` mô tả thành viên;
  `roleContexts`/`rolePlayers` mô tả đảm nhiệm role; `participants` hợp hai tập.
- Nesting khai báo kiểu không tự tạo agent, membership hay enactment runtime.

Mapping chỉ nối thuộc tính/identity; Auction có tám correspondence, không nối
plan JaCaMo với Task iStar. Snapshot cung cấp cấu trúc tổ chức, role và focus thực
tế. Native CSL/OCL được đánh giá trước khi cập nhật trạng thái iStar. Mỗi Auction
có lịch sử riêng; không gộp kết quả hai phiên.

`HOLD_FAILURE` giữ dừng khi invariant false hoặc goal VIOLATED;
`HOLD_UNVERIFIED` giữ dừng khi dữ liệu không đủ/không đọc được. `APPROVE` chỉ nói
chưa thấy vi phạm từ dữ liệu và đặc tả hiện có. Goal PENDING chưa có nghĩa hoàn
thành và cũng không tự thành vi phạm.

File `.oal`/`.iscn` xuất từ màn hình là snapshot XML `use-model-state` v1, có tham
chiếu model, identity, link và marking. Mở lại snapshot không tạo chứng nhận live
mới; đây không phải parser command-script AOL/iStarSCN của upstream.

## Tài liệu và output

README hướng dẫn build/chạy; tài liệu project chỉ ở `doc/`. Một báo cáo kiểm thử
tổng quan ở `doc/TESTING.md`. Log, XML JUnit, parser sinh tự động, oracle output
và artifact nằm trong `target/` hoặc thư mục tạm; không dùng làm cấu hình và không
tạo thành các kho báo cáo trong source.
