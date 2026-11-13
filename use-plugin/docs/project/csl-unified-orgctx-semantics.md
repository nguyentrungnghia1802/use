# CSL: hợp nhất Agent và Organization qua OrgCtx

**Cập nhật 14/11/2026:** profile thực thi đã được triển khai và được đặc tả tại
[`CSL-ORGCTX-UNIFIED-V1.md`](CSL-ORGCTX-UNIFIED-V1.md). Phần khoảng cách bên dưới
ghi nhận trạng thái trước thay đổi, không mô tả runtime mới.

Ngày đối chiếu: 13/11/2026. Tài liệu ghi nhận định nghĩa người dùng vừa làm rõ;
đây là cơ sở điều chỉnh thiết kế, chưa phải tuyên bố runtime đã triển khai đủ.

## Ngữ nghĩa được yêu cầu

CSL có ba khái niệm thành phần chính:

- **OrgCtx**: một chủ thể có định danh và trạng thái, biểu diễn agent đơn hoặc
  một tổ chức/tập thể. Organization và Agent được hợp nhất về ngữ nghĩa tại CSL.
  Một tổ chức có thể đóng role như một agent ở context lớn hơn. Không suy một
  context là agent đơn chỉ vì hiện nó có 0 context con.
- **Role**: vị trí/trách nhiệm theo một context. Role occurrence liên kết chủ thể
  đang đảm nhận nó với context tương ứng; state của role không mặc nhiên trở
  thành state toàn cục của player.
- **Entity**: thực thể phi ý định, ví dụ phiên đấu giá, sản phẩm hoặc đơn hàng.
  Artifact có phương thức Java vẫn có thể biểu diễn Entity: có operation không
  đồng nghĩa với có mục tiêu chủ động. Entity không trở thành role player.

OrgCtx đại diện chủ thể có thể có mô hình mục tiêu ở iStar; CSL vẫn là ngôn ngữ
cấu trúc/trạng thái. Điều này không đưa chương trình thực thi JaCaMo vào CSL.

## Đối chiếu NormML

Nguồn sơ cấp: Karen Figueiredo và Viviane Torres da Silva, *Norm-ML: A Modeling
Language to Model Norms*, ICAART 2011, tr. 232–237, DOI
[10.5220/0003179502320237](https://doi.org/10.5220/0003179502320237).
[Bản PDF, mục 3.1 và Hình 1](https://pdfs.semanticscholar.org/ace5/edffdcf5c2f5fcd2a3678551bb6a3e2b63e6.pdf).

NormML phân biệt Agent, Role và Organization. Hình 1 có các quan hệ
AgentOfOrganization, RoleOfOrganization, AgentCanPlayRole và organizationHierarchy;
phần văn bản phân biệt norm áp dụng cho agent, role, agent khi đóng role, và
nhóm agent thông qua Organization. Các phân biệt này là cơ sở tham khảo;
việc hợp nhất Agent/Organization dưới đây là lựa chọn của CSL trong nghiên cứu
này, không phải ngữ nghĩa được gán ngược cho NormML.

NormML cũng dùng Entity như một loại resource kế thừa SecureUML. Vì vậy,
định nghĩa Entity phi ý định trong CSL cần được trình bày theo vai trò của nó
trong CSL, không khẳng định NormML chưa từng có khái niệm tên Entity.

## Hai quan hệ cần giữ độc lập sau khi hợp nhất

Tên sau đây là ký hiệu ngữ nghĩa đề xuất, chưa phải cú pháp DSL đã được thêm:

```text
memberOf(member: OrgCtx, group: OrgCtx)
plays(player: OrgCtx, role: Role, context: OrgCtx)
```

`memberOf` diễn tả thuộc nhóm/thành phần tổ chức. `plays` diễn tả đảm nhận
trách nhiệm trong một context. Một role phải được xác định trong context mà
player đang đảm nhận nó. Không dùng một cạnh OrgCtx–Role thiếu context để
trộn role cùng tên trong các tổ chức khác nhau.

Ví dụ ở mức instance:

```text
memberOf(bob, auctionGroup)
plays(bob, auctioneer, auctionGroup)
memberOf(alice, auctionGroup)
plays(alice, participant, auctionGroup)

memberOf(providerTeam, company)
plays(providerTeam, supplier, marketplace)
```

Tất cả bob, alice, providerTeam, company, auctionGroup và marketplace đều là
OrgCtx. Ví dụ cuối cho thấy tổ chức có thể đảm nhận role như một chủ thể; không
cần biến providerTeam thành Entity hoặc tạo một Agent giả khác để đóng role.

Các tập có thể được dùng khi viết OCL:

```text
directMembers(g) = { x | memberOf(x, g) }
rolePlayers(g)   = { x | tồn tại r: plays(x, r, g) }
participants(g)  = directMembers(g) ∪ rolePlayers(g)
```

Tập tham gia cuối là một phép tổng hợp đề xuất. Nó không làm mất hai quan hệ
gốc: một thành viên có thể chưa nhận role; một chủ thể bên ngoài có thể được
giao trách nhiệm mà không phải bộ phận cấu thành của tổ chức. Nếu một miền yêu
cầu mọi role player phải là thành viên trực tiếp, điều đó được viết thành một
ràng buộc miền, không suy mặc định cho mọi mô hình.

Khả năng/quyền được đóng role cũng cần phân biệt với việc đang thực sự đóng
role. Program dùng chung hoặc quan hệ iStar participates-in ở mức định nghĩa
không tự chứng minh mọi agent nguồn hiện đang enact tất cả các role đó.

## Chứa khai báo, thành viên runtime và part-of

Ecore/grammar hiện tại dùng containment OrgCtx để sở hữu **khai báo M1**.
Một khai báo AgentContext không tự tạo Bob/Alice; các instance và quan hệ được
đồng bộ từ runtime. Không đồng nhất containment này với memberOf ở M0.

Nếu cần `partOf` mang nghĩa composition sở hữu thực sự, phải xác định thêm
quy tắc vòng, số owner và vòng đời. Không tự ép membership nhiều tổ chức thành
một cây có duy nhất một cha chỉ vì cú pháp khai báo lồng nhau là cây. Cardinality
và việc membership trực tiếp có truyền bắc cầu hay không cần được đặc tả riêng.

## Khoảng cách với implementation hiện tại

- Native backend cho OrgContext kế thừa RuntimePlayer, nên đã có khả năng một
  context làm player. Entity không thuộc RuntimePlayer.
- Producer hiện gắn parent cho organization/group, nhưng agent node luôn có
  parent rỗng. Adapter không hỗ trợ đầy đủ agent-context làm con của group-context.
- Projector tạo AtomicAgent và singleton capability-context riêng, rồi chọn
  AtomicAgent làm player cho agent nguồn. Cách này là lựa chọn tích hợp trước
  đây, chưa thực hiện đầy đủ việc hợp nhất chủ thể Agent/Organization mà người
  dùng vừa xác định.
- CslContextScopes đang phục vụ declaration scope và có tối đa một parent;
  nó chưa phải quan hệ memberOf độc lập với enactment. Không nên dùng nó làm
  bằng chứng rằng membership runtime nhiều tổ chức đã được hỗ trợ.

Hướng điều chỉnh là dùng OrgCtx làm chủ thể chuẩn của mô hình CSL cho cả agent
và tổ chức, giữ định danh Agent JaCaMo như thông tin nguồn, và đồng bộ membership
cùng enactment bằng các quan hệ riêng có bằng chứng. Entity tiếp tục là dữ liệu
phi ý định. Nguồn chỉ cung cấp role roster thì chỉ khẳng định plays; không tự
bịa memberOf độc lập nếu chưa có dữ liệu hoặc quy tắc miền được khai báo.

Không sửa grammar, runtime hoặc model Auction trong bước đối chiếu này; tránh
biến các ký hiệu đề xuất thành một tính năng được tuyên bố là đã hoạt động.
