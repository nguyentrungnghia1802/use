# Đối chiếu ngữ nghĩa JaCaMo – CSL – iStar

Đây là bản sửa ngữ nghĩa, thay thế mô hình sáu dependency trước đó. Việc các
kiểm thử cũ chạy qua không chứng minh rằng mô hình đó diễn đạt đúng source.

## Quan hệ và Goal

File iStar có đúng ba dependency: AuctionGroup yêu cầu `start`, `bid`, `decide`
từ Auctioneer/Participant. `auction_capabilities participates-in Auctioneer`
và `auction_capabilities participates-in Participant` là hai quan hệ riêng;
không phải hai role phụ thuộc vào một nhà cung cấp dịch vụ khác.

Goal cùng tên và cùng type trong actor năng lực và role được biên dịch thành
các view của cùng một occurrence, trong cùng context Auction. Vì vậy, demand
từ dependency tới `Auctioneer.start` kích hoạt occurrence của
`auction_capabilities.start`; marking của hai view thống nhất. Không tạo thêm
dependency xã hội. Các tên khai báo được định danh theo actor, nên không cần
đổi thành A_start/C_start hoặc viết alias cho từng Goal trong XML.

Frontend legacy vẫn giữ quy tắc ID global. Frontend state dùng scoped names.
Không có participates-in thì hai Goal cùng tên không tự liên kết. Goal type
khác nhau hoặc nhiều provider tranh cùng một Goal bị từ chối; không tự chọn
quantifier. Đây là các view ý định theo phiên Auction, chưa phải graph riêng
cho từng agent vật lý.

## Đối chiếu với code thực tế

| Source | Hiệu ứng và điều kiện thực tế | Biểu diễn đã sửa |
| --- | --- | --- |
| `+!start` | `makeArtifact(Id,...)`, `focus(ArtId)`, `start(S)`; Artifact init running=false, rồi start đặt running=true và task=S | Goal start có ba Task CreateAuctionArtifact, FocusAuctionArtifact, StartAuction. Creation dùng UUID mới và allInstances@pre; focus kiểm tra observer là player auctioneer; start kiểm tra running=true |
| `+!bid` | lookup và focus; có nhánh fail. Nếu gọi bid, chỉ giá tốt hơn mới đổi best_bid/currentWinner. Giá kém hơn vẫn thành công | FocusForBid kiểm tra participant focus. ObserveBid chỉ ghi nhận ít nhất một improving bid. Maintain BidNeverIncreases kiểm tra giá tốt nhất không tăng giữa hai snapshot |
| `+!decide` | Tìm Artifact đang focus rồi stop. stop yêu cầu running=true, đặt false, công bố currentWinner; không cấm no_winner | StopAuction nhớ pre running=true và post running=false. Trạng thái init không bị coi là đã stop. Không còn ép winner khác no_winner |
| `+!do_auction` | Tạo Scheme, đặt Id/Service/owner, focus Scheme, gán group và commit mission | **Chưa được biểu diễn đầy đủ:** CSL hiện không có Scheme/arguments/commitments để kiểm tra các bước này |

`winner` chỉ được cập nhật công khai trong stop; bid chỉ đổi private
`currentWinner`. Fixture đã sửa để winner vẫn là no_winner trong lúc bidding.
Không sửa ASL hoặc AuctionArtifact để tạo bằng chứng giả cho các predicate.

## CSL và quan sát nguồn

- Giữ AuctionOrg, auctionGroup, role occurrences, player/enactment, các capability
  context và Auction entities. OrgContext vẫn là RuntimePlayer.
- Thêm CslFocuses từ observer thực tế của CArtAgO tới AtomicAgent trong cohort
  JaCaMo được theo dõi; không suy focus từ tên plan hay artifact.
- Kiểm tra auctioneer đang quan sát Auction khi running, ID Auction duy nhất
  trong scope, và capability context đại diện đúng agent. Mapping giữ tám dòng
  thuộc tính; quan hệ focus được đồng bộ giống cấu trúc role/player.
- Giá không âm là yêu cầu nghiệp vụ của model; Artifact.bid(double) tự nó không
  cấm số âm. ASL của ví dụ sinh giá trong khoảng 10–110. Điều kiện giữ focus khi
  running phản ánh giao thức trong các plan đã cho, mạnh hơn guard riêng của
  lớp Artifact.

## Những điều chưa thể kết luận từ dữ liệu hiện có

1. Graph được tạo sau khi thấy Artifact. Nếu một plan start bỏ hẳn makeArtifact,
   không có expected request/context để phát hiện object bị thiếu. Cần context
   request/scheme tồn tại trước Artifact và liên kết bằng Id để kiểm tra đầy đủ.
2. Chưa so sánh task với đúng Service của Scheme, chưa chứng minh Id đúng với
   yêu cầu, hoặc mission đã được commit. Những dữ liệu đó chưa nằm trong CSL.
3. Hai lần cập nhật có cùng state không phân biệt được một bid kém hơn thành công
   với không gọi bid. Không chứng minh mọi participant đã bid, hoặc nhánh fail
   của một agent đã đạt Goal. Role bid hiện là chứng cứ tồn tại ở cấp phiên.
4. StopAuction kiểm tra chuyển trạng thái stopped, chưa so sánh winner công bố
   với private currentWinner chưa được quan sát. Snapshot cũng không chứng minh
   thứ tự từng action trong khoảng giữa hai checkpoint.

Các điểm này là giới hạn ngữ nghĩa còn lại, không được coi là đã verified chỉ
vì source chạy được hoặc các Task khác đã fulfilled. Để kiểm chứng từng plan
một cách đầy đủ cần mở rộng **dữ liệu CSL được quan sát**, vẫn có thể giữ XML
chỉ ánh xạ thuộc tính và không ghép plan JaCaMo với Task iStar.

## Bằng chứng

108 kiểm thử semantic engine và 17 kiểm thử state đã qua. Các ca kiểm thử bao
gồm activation của Goal shared, ba giai đoạn start, không tính nhầm init là
stop, stop không có bidder, thiếu focus, best_bid tăng sau khi Goal bid đã đạt,
hai Auction độc lập, và gate JaCaMo thật giữ dừng trước plan tiếp theo khi sai.

Chạy Auction thật: 124 checkpoint APPROVE; hai phiên hoàn tất. Mỗi Artifact có
năm observer nguồn. Cả hai view Goal start và các Task creation/focus/start đều
fulfilled. `receipt.json` và `last-checkpoint.json` giữ snapshot, trạng thái,
hash source/model và số kiểm thử. Không lưu secret xác thực.
