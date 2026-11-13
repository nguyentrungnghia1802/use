# CSL_ORGCTX_UNIFIED_V1 — ngữ nghĩa thực thi cho Auction

Ngày: 14/11/2026. Đây là profile thực thi được chọn bởi module
`state-verification`. Cú pháp ba khái niệm v4 được giữ nguyên; thay đổi nằm ở
ngữ nghĩa chủ thể/quan hệ M0. Backend legacy `observedPlayers()` được giữ để
không đổi ngầm những ứng dụng cũ. Profile mới dùng `unifiedContexts()`.

## 1. Miền đối tượng

Ba tập instance miền CSL là OrgCtx, Role và Entity, rời nhau về kiểu:

- **OrgCtx:** chủ thể cá nhân hoặc tập thể có identity/state. Agent JaCaMo,
  organization và group đều được biểu diễn bằng một OrgCtx chuẩn cho mỗi
  identity nguồn. Không tạo AtomicAgent thứ hai hoặc một wrapper-player giả.
- **Role:** occurrence của một định nghĩa trách nhiệm, có đúng một player
  OrgCtx và một context OrgCtx. State thuộc role occurrence. Kế thừa Role dùng
  cùng identity occurrence, không tạo chuỗi role-player mới.
- **Entity:** thực thể phi ý định, có identity/state và các liên kết nghiệp vụ.
  Entity không làm role player hay thành viên tổ chức. Có operation ở Java
  không làm một Artifact trở thành chủ thể ý định.

Agent/Organization không phải hai kiểu thành phần CSL riêng. Thuộc tính nguồn
`kind` dùng khi đọc JaCaMo không tạo ra một nhánh kiểu agent ở mô hình đích.

## 2. Khai báo và instance

`orgContext` lồng nhau sở hữu khai báo kiểu. Việc khai báo một AgentContext
không tạo sẵn Bob/Alice và không cố định số lượng instance. Nesting không phải
generalization, không tự enact role và không tự sinh membership.

`CslContextScopes` giữ scope được quan sát khi nguồn cung cấp parent cụ thể.
Nó có tối đa một declaration-scope parent và không thay thế membership.
Một agent instance có kiểu được khai báo trong group vẫn có thể tham gia nhiều
group instance; không ép nó thành nhiều bản sao chỉ để có một parent.

## 3. Hai quan hệ độc lập

```text
memberOf : OrgCtx × OrgCtx
plays    : OrgCtx × Role × OrgCtx
```

Membership trực tiếp không có self-link. Hai đầu có cardinality `0..*`;
không suy quyền sở hữu, xóa cascade hoặc tính bắc cầu. Một cấu trúc part-of có
ownership/acyclicity mạnh hơn phải có ràng buộc riêng, không suy từ membership.

`plays(p,r,g)` được thực hiện bằng `Plays(p,r)` và `InContext(g,r)`. Kiểu Role
phải được khai báo trong kiểu context tương ứng. Một player có thể có nhiều
occurrence ở các context; các quy tắc compatibility vẫn áp dụng cho cùng
player trong cùng context. Organization dùng đúng quan hệ này để đóng role
ở context cao hơn.

Membership không bắt buộc phải kèm role; enactment không tự tạo membership
trực tiếp. Cùng một OrgCtx có thể là thành viên của công ty A và đảm nhận role
supplier trong marketplace B.

Các navigation tiền định nghĩa cho OCL:

| Navigation | Ý nghĩa |
| --- | --- |
| `x.memberOf` | Các nhóm có membership trực tiếp của x |
| `g.members` | Các OrgCtx là thành viên trực tiếp của g |
| `g.roles` | Các role occurrence trong g |
| `r.player`, `r.orgContext` | Chủ thể và context của role occurrence |
| `g.rolePlayers` | Tập player từ các role trong g, bỏ trùng |
| `x.roleContexts` | Các context nơi x đang đóng role |
| `g.participants` | `g.members ∪ g.rolePlayers`, không làm mất hai tập gốc |

Các tên kernel được bảo vệ khỏi xung đột với attribute/role navigation miền.
Chúng là quan hệ ngữ nghĩa M0 tiền định nghĩa, không phải các association M1
OrgCtx–OrgCtx tùy ý hoặc cú pháp membership mới chưa có parser.

## 4. Entity và quan sát

Entity thuộc scope quan sát qua `CslEntityScopes`. Trong adapter hiện tại,
Artifact phải xác định được đúng một instance của kiểu scope đã khai báo;
không có hoặc có nhiều ứng viên thì giữ dừng vì thiếu binding rõ ràng.

`CslFocuses` nối OrgCtx agent nguồn với Entity Artifact. Cùng một OrgCtx làm
player và observer, không dùng hai identity đích khác nhau để nối bằng wrapper.
Focus được đọc từ observer CArtAgO tại cut ổn định.

Các association M1 entity-centered vẫn thuộc grammar/schema v4. Adapter live
này chỉ có writer cho kernel/scope/focus; association miền khác cần provider
và bị từ chối rõ ràng, không được tuyên bố là đã đồng bộ.

## 5. Quy tắc ánh xạ và snapshot

- Mỗi nguồn agent/organization/group được chọn có tối đa một kiểu OrgCtx đích.
  Identity bền theo run + UUID nguồn + kiểu đích; nhiều agent cùng chương trình
  tạo nhiều instance, không thêm dòng mapping theo từng tên agent.
- Tám correspondence thuộc tính Auction được giữ nguyên. Membership và role
  là các fact cấu trúc riêng, không là plan–Task mapping.
- Producer cung cấp membership group–parent từ hierarchy/ownership được quan
  sát ở Moise. Roster role chỉ tạo enactment; không tự bịa membership trực tiếp
  cho từng agent. Agent membership không có trong nguồn vẫn cần provider phù hợp.
- Membership nhiều nhóm, role withdrawal và source retirement cập nhật độc lập.
  Thay đổi role không tự xóa membership. Snapshot không complete không được
  xóa state hay chứng nhận dữ liệu vắng mặt.
- Wire protocol 2.0 yêu cầu trường memberships, kể cả danh sách rỗng. Replay
  phân loại lỗi chỉ nhận trace phiên bản hiện tại; không tự coi trace cũ thiếu
  dimension membership là bằng chứng hoàn chỉnh cho profile mới.

## 6. Auction trong profile này

AuctionOrg chứa kiểu auctionGroup; auctionGroup khai báo Auction, auctioneer,
participant và kiểu OrgCtx auction_capabilities. Ở runtime có một OrgCtx cho
aorg, một cho agrp và năm cho Bob/Alice/Maria/Francois/Giacomo. Bob đóng role
auctioneer; bốn người còn lại đóng participant. Group là member của tổ chức;
player agent được thể hiện qua role, không suy thêm direct membership.

CSL kiểm tra số role, giá không âm, focus khi running và ID Auction duy nhất.
iStar vẫn mô tả các viewpoint mục tiêu cho từng phiên. Việc chọn Auction làm
ngữ cảnh dữ liệu OCL không biến Entity Auction thành actor/agent.

## 7. Khả năng ngôn ngữ không đồng nghĩa với độ bao phủ quan sát

Profile có thể biểu diễn Request/Scheme bằng Entity/OrgCtx, thuộc tính phase,
expectedService/bestBidder và OCL tương ứng. Producer hiện chưa cung cấp các
dữ liệu đó. Không được suy từ đây rằng mọi action/mission đã được kiểm chứng.
Đánh giá phân biệt lỗi phương pháp và lỗi đặc tả phải giữ nguyên runtime/data
khi thử sửa đặc tả; nếu cần bổ sung quan sát mới thì ghi rõ đây là thay đổi
ở phương pháp/adapter trước khi đánh giá phần constraint còn thiếu.
