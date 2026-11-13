# Đồng bộ thuộc tính CSL và đánh giá iStar từ state

Ngữ nghĩa OrgCtx và kết quả phân loại lỗi phương pháp/đặc tả: xem
[`csl-classification-20261114`](../../../verification/csl-classification-20261114/REPORT.md).
Tool phát hiện được một số lỗi nhưng còn các counterexample bị bỏ sót;
APPROVE không chứng minh toàn bộ ví dụ đúng. Chiến dịch ngày 13/11 được giữ
nguyên làm bằng chứng của phiên bản trước khi thống nhất OrgCtx.

Luồng chạy này thực hiện yêu cầu: JaCaMo công bố state, mapping chỉ nối các thuộc
tính với CSL, iStar chỉ đánh giá OCL trên CSL. `attribute-mapping.xml` có tám
tương ứng thuộc tính, không chứa plan, Task, action receipt hay danh sách agent.

## Tổ chức, role và player trong CSL

`AuctionOrg` chứa context `auctionGroup`; context này khai báo hai role
`auctioneer`, `participant` và entity `Auction`, như cấu trúc ví dụ Auction gốc.
`auction_capabilities` là kiểu OrgCtx của từng agent, được khai báo trong scope
auctionGroup. Runtime tạo đúng một OrgCtx làm chủ thể cho mỗi agent nguồn;
không tạo AtomicAgent/wrapper thứ hai. Việc khai báo role không tự tạo người chơi.

Ba dòng mapping identity chọn organization theo ID specification (`auction`),
group theo type (`auctionGroup`), agent theo tên chương trình ASL
(`auction_capabilities`). Chúng không phụ thuộc tên instance `aorg`, `agrp`, Bob,
Alice hay số lượng agent. Năm dòng còn lại map thuộc tính Artifact sang `Auction`.

Ở mỗi snapshot, runtime đọc **roster hiện tại của GroupBoard Moise**, tạo/xóa
role occurrence, nối `InContext` tới group và `Plays` tới OrgCtx của player.
Không lấy danh sách `players` trong JCM làm bằng chứng rằng agent đã adopt role.
`OrgContext` kế thừa `RuntimePlayer`, nên một context tổ chức cũng có thể đóng
role ở tổ chức cấp cao hơn; không chuyển nó thành entity hoặc atomic agent.
Snapshot trung lập hỗ trợ player loại này và có kiểm thử trực tiếp. Adapter
JaCaMo đọc player agent Jason từ Moise thành OrgCtx; không tự suy diễn rằng một
organization đang đóng role nếu nguồn chưa cung cấp enactment đó.

`members`/`memberOf` là membership trực tiếp; `rolePlayers`/`roleContexts` đến
từ enactment. `participants` là hợp hai nhóm chủ thể, bỏ trùng. Nguồn Moise
cung cấp group–parent membership từ hierarchy, còn roster chỉ tạo role facts.
Một agent có thể thuộc nhiều nhóm và rời role mà không mất membership. Chi tiết
profile và giới hạn provider ở
[`CSL_ORGCTX_UNIFIED_V1`](../../../docs/project/CSL-ORGCTX-UNIFIED-V1.md).

`CslContextScopes` lưu context lồng nhau; `CslEntityScopes` nối Auction với group.
Với Artifact tạo trong workspace tổ chức, scope phải có đúng một instance phù hợp
với context khai báo. Nếu có nhiều group cùng type trong workspace thì runtime
từ chối snapshot do thiếu thông tin scope, thay vì chọn tùy tiện.

Snapshot INITIAL xảy ra trước các plan adopt role của JaCaMo. Vì thế invariant
giới hạn auctioneer tối đa một, và bắt buộc đúng một khi group đã có Auction.
Goal Maintain `RolesConsistent` cũng đọc role population qua CSL. Khi agent rời
role, liên kết bị xóa và OCL nhìn thấy ngay ở checkpoint tiếp theo.

Chạy từ thư mục repository `use` bằng JDK 21:

```bash
bash use-plugin/tools/run-state-only.sh
```

Lần đầu script build module `state-verification` và các module native mà nó cần.
Sau khi sửa code, dùng `--build`. `USE_MAVEN` có thể trỏ tới executable Maven.
Cửa sổ có các tab CSL state, iStar Goal/Task, OCL conditions, Checkpoints và
Attribute mapping. Nhấn Stop JaCaMo để dừng producer. Đóng cửa sổ cũng dừng
producer thuộc phiên này. Log và các snapshot/quyết định nằm trong thư mục
`/tmp/use-csl-state-*` được in ở terminal.

Demo âm có kết quả xác định:

```bash
bash use-plugin/tools/run-state-only.sh --counter
```

Counter chạy các plan con để đổi giá trị `0 → 1 → 2 → -1`. Tại `2`, Task
`TargetReached` fulfilled. Tại `-1`, invariant CSL và Goal Maintain bị vi phạm;
producer bị giữ trước plan đổi giá trị thành `999`. Mapping Counter chỉ có một
dòng attribute. Kiểm thử live khởi chạy hai JVM thật và kiểm tra toàn bộ chuỗi này.

## Ý nghĩa của iStar trong chế độ này

Model gồm đúng bốn nút: `agent AuctionGroup`, `role Auctioneer`,
`role Participant`, và `actor auction_capabilities`. Tổ chức phụ thuộc hai role
để mở, nhận giá và kết thúc đấu giá: **đúng ba dependency**. Actor năng lực
`participates-in` cả hai role; các Goal cùng tên `start`, `bid`, `decide` là
các view của cùng một Goal thực thi trong cùng Auction, không thêm dependency
từ role tới actor năng lực. `actor` là cú pháp mở rộng của project, được giữ thành
`GenericActor` khi parse và khi tạo mô hình instance; không đổi ngầm thành Agent
hoặc Role.

`// @csl-state Auction` chọn **ngữ cảnh dữ liệu OCL**, không phải ánh xạ actor
iStar sang agent JaCaMo. Cả bốn nút là các góc nhìn mục tiêu cho từng phiên
Auction; mỗi phiên có một đồ thị đánh giá riêng. Nó không tạo một đồ thị cho
từng Bob/Alice, và không đánh đồng entity Auction với agent AuctionGroup.
OCL truy vấn role/player thực tế qua scope CSL của phiên đó. Số lượng và trạng
thái agent thật tiếp tục nằm trong CSL.

Runtime dùng profile đã có `ROLE_SPECIALIZATION_V1` cho quan hệ một Goal được
thực hiện bởi một Task, và topology `SINGLE_OWNER_CONTEXT_V1` cho bốn góc nhìn
cùng một Auction. Điều này giữ các refinement/dependency nối với cùng occurrence,
để trạng thái đi từ Task → Goal năng lực → Goal role → Goal tổ chức.

Frontend state dùng tên cục bộ theo actor. Vì vậy `Auctioneer.start` và
`auction_capabilities.start` có định danh khai báo riêng; chỉ quan hệ
`participates-in` và tên/type Goal trùng nhau mới làm chúng chia sẻ activation
và marking. Compiler legacy vẫn giữ quy tắc định danh global. Nhiều actor cùng
cung cấp một Goal của Role bị từ chối vì cần quyết định quantifier rõ ràng;
runtime không tự chọn một người chơi.

Goal `start` có ba Task tương ứng hiệu ứng của `makeArtifact`, `focus`, `start`:
quan sát UUID mới trong snapshot, focus của auctioneer, và `running=true`.
Quan hệ `CslFocuses` đọc observer thực tế từ CArtAgO, không suy từ plan hoặc tên
Artifact. Goal `bid` kiểm tra focus của participant và chứng cứ giá tốt đầu tiên.
Goal `decide` nhớ trạng thái running rồi quan sát stopped; `no_winner` vẫn hợp lệ.

`ObserveBid` chỉ chứng minh ít nhất một improving bid trong phiên. Một lời gọi
`bid` thành công với giá kém hơn không đổi state; snapshot không phân biệt nó
với việc không gọi action. Model này không chứng minh mỗi participant đã bid.
`winner` chỉ được Artifact công bố trong `stop`, không phải lúc mỗi bid.

Ngữ cảnh hiện tại chỉ được tạo khi đã quan sát một Artifact Auction. Vì vậy,
nếu plan start không tạo Artifact nào, chưa có graph tương ứng để kết luận
Goal start bị lỗi. Để phát hiện thiếu object theo từng yêu cầu, cần thêm context
request/scheme có trước Artifact và quan sát completion từ nguồn. Hiện chưa
quan sát Scheme arguments/mission commitments, nên cũng chưa chứng minh
`task = Service` đúng theo tham số của scheme hoặc toàn bộ plan `do_auction`.

Các Task trong file iStar này mô tả đạt được một trạng thái. `pre` và `post` là
các predicate OCL trên CSL, được đưa vào recurrence `TaskMarking` gốc; không phải
chứng nhận một action Jason đã được chạy. False ở predicate Task/Achieve có thể
là progress; invariant false hoặc Maintain/Sustain bị violated thì giữ dừng.
Một Task đã fulfilled giữ lịch sử theo đúng recurrence gốc, dù predicate hiện
tại có thể đổi lại. Tab OCL hiển thị giá trị hiện tại riêng với marking.

Nếu dùng `@pre` trong POST, nó tham chiếu snapshot CSL được duyệt trước lần cập
nhật hiện tại. Nó không tham chiếu đầu một Task Jason. Các tính chất về action
đã thực thi chỉ chứng minh được khi chúng được biểu diễn bằng dữ liệu CSL có
nguồn quan sát phù hợp.

Goal Maintain có `@pre` được biên dịch như một predicate chuyển trạng thái giữa
hai snapshot CSL. `BidNeverIncreases` dùng cơ chế này để phát hiện best_bid tăng,
kể cả khi Goal bid đã fulfilled trong lịch sử; object mới được nhận diện bằng
`Auction.allInstances@pre()->excludes(self)` và chưa có giá trước đó để so sánh.

`// @csl-state Auction` chọn kiểu object dữ liệu cho mô hình iStar này.
Mỗi UUID Artifact tạo một object CSL và một lịch sử iStar riêng.
Tạo/xóa instance không cần sửa mapping.
State snapshot đầy đủ mới có thể xác nhận xóa object; snapshot thiếu giữ dừng.

Tên plan/agent và outcome nguồn chỉ nằm trong audit của checkpoint. Chúng không
được đưa vào evaluator iStar, cũng không quyết định Goal/Task đạt hay không.
Một plan source FAILED vẫn có thể nhận APPROVE nếu CSL/iStar không có vi phạm;
Jason tự xử lý failure theo ngữ nghĩa chương trình của nó. Trong Auction, nhánh
`.fail` là hành vi đã có trong ví dụ, không phải kết quả Task iStar tự suy ra.

## Điều phối và phạm vi bản triển khai

Adapter cài entry/exit marker tổng quát vào bản plan trong bộ nhớ, kể cả plan
rỗng và plan include; file ASL gốc không bị sửa. Kết thúc plan đóng admission ở
barrier JaCaMo đã có, chờ công việc đã nhận vào settle rồi chụp physical state.
USE cập nhật cùng native MSystem, chạy native OCL và engine marking/propagation
gốc, hiển thị kết quả rồi trả decision có xác thực cho đúng token/hash. Không
có Continue bỏ qua lỗi trong cửa sổ này. Mất mạng/timeout/coverage giữ dừng.

Đây là cửa sổ chạy riêng trong module plugin `modules/state-verification`, dùng
backend USE thật; menu Import của Workbench compact cũ chưa đọc namespace mapping
mới. `.mrv`, model và mapping compact cũ giữ nguyên; chạy chế độ mới bằng script
ở trên. Phiên mới dùng chính `auction.jcm`/ASL/Artifact nguồn hiện tại, với bộ
model state trong thư mục này. Những condition cũ như `step.succeeded` không thể
dùng trong chế độ chỉ đọc CSL và bị từ chối khi import.

Bản này hỗ trợ thuộc tính scalar String/Boolean/Integer/Real của Artifact local,
identity name/UUID/workspace, cấu trúc tổ chức/group/role, agent và enactment thực tế,
và một kiểu context CSL cho mỗi đồ thị iStar.
Các actor trong cùng graph có thể cùng đánh giá object context đó. Đồ thị nhiều
kiểu context cần quan hệ CSL để nối các occurrence; hiện được từ chối rõ ràng.
Belief collections, field Java nội bộ, OCL operation contracts và Resources chưa
được nhập vào luồng state này. Không tự gán chúng giá trị mặc định hoặc bỏ qua
những cấu hình được yêu cầu nhưng không hỗ trợ.

Barrier cho cut ổn định sau các terminal đã chạy đồng thời; nó không biến mỗi
plan thành transaction cô lập. Side effect đã thực thi không được rollback.
Plan chứa custom scheduler, dynamic code/agent creation hoặc producer ngoài
barrier không có bảo đảm kiểm chứng đầy đủ của phiên chạy này.

Chạy project/model khác:

```bash
bash use-plugin/tools/run-state-only.sh --project \
  /path/project.jcm /path/attribute-mapping.xml /path/model.csl /path/model.istar
```
