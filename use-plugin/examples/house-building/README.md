# House Building — observed world requirements v1

## Chạy trên main USE GUI

Mở main **Plugins → JaCaMo / use4Goal verification...**, chọn đúng năm inputs:

| Ô input | File trong thư mục này |
|---|---|
| CSL state model | `house.csl` |
| JaCaMo project | `house-building.jcm` |
| iStar requirements | `house.istar` |
| Mapping XML | `house-mapping.xml` |
| OCL requirements | `house.ocl` |

Bấm **Prepare + Import**, kiểm tra models/read-set, rồi **Start Runtime**.
Process JaCaMo riêng đấu thầu tám hạng mục, tạo ora4mas/group/scheme động,
thực hiện các operation xây nhà. Quan sát ProcurementAuction, BuildProgress,
CSL/iStar views và Goal/OCL evidence theo timeline. Stop giữ USE sống;
Restart tạo run/UUID/history mới. Chọn Show JaCaMo GUI để xem MAS Console
và House drawing; headless tests vẫn chạy domain operations nhưng không vẽ.
Runtime libs như Auction: checkout đã build có default classpath; installed
package chọn file classpath bộ runtime tương thích. Journal mặc định 256 MiB.

## Source và adaptations có version

Source: `jacamo/examples/house-building`, 22 agents sau khi expand instances:
Giacomo, A, B, C×5, D×13, E. Có tám tools.AuctionArt với task/maxValue/currentBid/
currentWinner; currentWinner ở case này LÀ public property (khác AuctionArtifact
của Auction). `bid(double)` chỉ thay winner và giá khi thấp hơn giá hiện tại;
non-improving bid vẫn thành công. Giá đầu là Java Integer, bid cập nhật Double;
XML chọn đúng public numeric types, converter parse finite numeric text thành
Real, không clamp/đổi NaN thành số. OCL post dùng min(old bid, offered bid).

JCM không khai báo organization tĩnh. Giacomo tạo OrgBoard(ora4mas),
GroupBoard(hsh_group), SchemeBoard(bhsch) từ house-os.xml trong ASL. Không thêm
organization vào JCM để làm test dễ chạy. OS/mission/roles giữ nguyên source.

Các delta local đều nằm trong provenance.json:
- Condition-wait fixture V1 đã được user chấp thuận: chờ formationStatus(ok)
  hiện có, không chỉ một future event có thể đã bị lỡ; upstream không sửa.
- makeArtifact/4 empty args + feedback thay overload /2 cho House GUI; tạo cùng
  một artifact, feedback không dùng vào domain. Cho phép API class proof hiện có.
- Headless presentation adapter của simulator.House bỏ JFrame/drawing ở headless;
  giữ toàn bộ public operations và waits. GUI mode vẫn vẽ như gốc.

## Verification scope

CSL ProcurementAuction là state public passive; BuildingRun là context của
workspace main, không giả một MOISE group tĩnh. BuildProgress là **external
observer class**, KHÔNG phải CSL Entity hay Java House internal state. XML yêu
cầu neutral `cartago.operation-history.v1`, pin source/binary proof, exact
artifact/workspace incarnation, STARTED/SUCCEEDED correlation và continuous
history từ creation. *At là first successful SOURCE SEQUENCE, không wall time
và không thời điểm internal MOISE goal trở thành true. -1 chỉ có nghĩa chưa
quan sát successful return trong complete prefix; thiếu evidence là undefined.

Original iStar engine đánh giá ContractsAwarded và 10 construction outcomes;
HouseRealized/ConstructionCompleted/RwdCompleted/PeeCompleted là AND outcome
conjunction. Không đổi SEQUENCE/PARALLEL của MOISE thành plain AND execution
semantics. Native OCL kiểm riêng thứ tự successful operation returns giữa site,
floors, walls, shell, services/exterior và interior. Đây không chứng minh mọi
operation bắt đầu theo thứ tự hoặc framework deadline/norm compliance.

Cả hai model là authored requirements cho observed execution. Một FAIL là
kết quả requirement của trace, không được sửa mapping để biến thành PASS.
Full MOISE role/norm/mission/epistemic verification và iStar per-invocation
TaskMarking không được claim bởi profile state/outcome này. Source OS vẫn có
đầy đủ roles/missions/norms để tham khảo; thiếu source evidence giữ UNKNOWN.

Nguồn read-only gốc ở folder jacamo không dùng trực tiếp với hash fixture này.
Chọn `house-building.jcm` trong bundle, hoặc copy TOÀN BỘ bundle sang chỗ khác.
Muốn đổi source/model/OCL phải tạo mapping revision và hash tương ứng; không
bỏ checksum guard. `case-manifest.json` là danh sách five inputs đồng phiên bản.

BuildProgress.workspace là execution-context workspace, được join qua official
parentSemanticId từ workspace chứa House về workspace procurement. Physical
House identity vẫn chứa /main/ora4mas và UUID thật, không thay identity.
ContractsAwarded yêu cầu positive winner witness cho TỪNG task trong tám task
source, thay count=8/forall (vốn chấp nhận nhầm tám bản sao cùng task). Không
suy ra complete population hoặc award từ chỉ số lượng.

`build.gradle` giữ nguyên upstream làm reference; build Gradle độc lập cần
bootstrap `.jcm-deps.gradle` theo JaCaMo. Workflow được hỗ trợ/kiểm thử ở đây
là năm inputs trong main USE GUI; launcher compile selected source với runtime
classpath, không phụ thuộc Gradle tasks hay generated files của checkout gốc.
