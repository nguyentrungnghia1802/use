# Hello World — observed world requirements v1

## Chạy trên main USE GUI

Mở **Plugins → JaCaMo / use4Goal verification...** (entry five-input hiện có).
Browse đúng bộ dưới đây, không trộn file Auction/Stock:

| Ô input | File trong thư mục này |
|---|---|
| CSL state model | `hello.csl` |
| JaCaMo project | `helloworld.jcm` |
| iStar requirements | `hello.istar` |
| Mapping XML | `hello-mapping.xml` |
| OCL requirements | `hello.ocl` |

Bấm **Prepare + Import**, xem CSL/iStar/Observation plan; sau MODEL_READY bấm
**Start Runtime**. JaCaMo chạy process riêng qua TCP. Trong observed execution,
Console `jacamo/gui` tăng numMsg tới 12; `italy/gui` và `usa/gui` có thể nhận lời
chào của Bob. France/Brazil có thể vẫn bằng 0. Mở timeline và native OCL evidence.
Stop JaCaMo không đóng USE; Restart là run mới. Show JaCaMo GUI bật nếu muốn
thấy MAS Console và năm cửa sổ display gốc. Runtime libs có thể để trống khi
chạy từ checkout có classpath build sẵn; installed package cần chọn classpath
text của bộ JaCaMo tương thích. Giữ journal 256 MiB cho phiên bình thường.

## Ý nghĩa chính xác

Source là tutorial `jacamo/doc/tutorials/hello-world/code/helloworld`, KHÔNG có
folder hello-world trong `jacamo/examples`. Năm agent user (francois, maria,
giacomo, alice, bob), năm GUIConsole; hello_org/jacamo_team (type team trong o1.xml) có rv/rl/rc/rs;
scheme instance hello_eng dùng type hello_sch.
Các ASL/OS còn nguyên; GUIConsole có presentation adaptation được ghi hash:
headless không tạo JFrame nhưng vẫn init numMsg=0 và mỗi printMsg cộng một.
Chạy GUI vẫn giữ display gốc. Không sửa repo jacamo.

CSL chỉ mô hình hóa Console passive state và GreetingRun là **workspace
execution viewpoint**, không coi agent là Entity hoặc tạo thêm MOISE group.
XML thực thi exact class/property/workspace joins, identity UUID/incarnation,
source checksums và original frontend/validator. iStar là authored requirement
của một observer tập thể: ít nhất 12 messages tại workspace jacamo và giữ count
không âm. Hai goals độc lập vì original engine không biểu diễn VIOLATED của Maintain
dưới parent Achieve; không ép loại goal hoặc nuốt violation để vượt admission. Không gán credit cho agent khác.

`TwelveMessagesObserved` KHÔNG chứng minh chính xác chuỗi ký tự hoặc thứ tự
MOISE. numMsg không chứa message text. Sequence/norm/mission/role compliance,
Jason beliefs và per-invocation iStar Tasks ngoài scope profile này, được ghi
TRACE_ONLY trong XML. Không bịa 12 task marking từ số đếm. `hello.ocl` kiểm
NonnegativeMessages và native printMsg pre/post với @pre thật khi operation
checkpoint đầy đủ; missing checkpoint/population giữ UNKNOWN/SKIPPED.

`case-manifest.json` liệt kê năm inputs và hash. `provenance.json` ghi upstream
commit, hash gốc/local, adaptations. Đây là bundle authored five-input;
CSL/iStar là production authored requirements, không phải generated-model oracle.

Trong prefix có đủ coverage, cả TwelveMessagesObserved và NonnegativeCounts
được original GoalMarking đánh giá FULFILLED. Maintain dùng exists + forAll
với implication về workspace: tương đương select/notEmpty + forAll, có test
native OCL cho bốn trường hợp (rỗng/khác workspace/count đúng/count âm).
Không đổi Maintain thành Achieve. Quality chưa đủ vẫn giữ UNKNOWN.

`build.gradle` giữ nguyên upstream làm reference (default grid và các remote
subprojects của tutorial nằm ngoài bundle này). Entry được hỗ trợ/kiểm thử ở
đây là main USE GUI năm inputs; launcher compile source cần thiết bằng runtime
classpath đã negotiate, không yêu cầu chạy các Gradle tasks tham khảo đó.
