# Auction — project input và đặc tả hệ thống

Input thực thi là `auction.jcm` cùng project ASL/MOISE/Artifact/resources.
`auction.csl`, `auction.istar`, `auction-mapping.xml`, `auction.ocl` là đặc tả hệ
thống soạn thủ công, chưa tự động tạo; giao diện vẫn cho chọn/import chúng.

Mở USE → Plugins → JaCaMo → use4Goal verification. Chọn project .jcm và bộ đặc
tả trên, Prepare + Import rồi Start Runtime. JaCaMo chạy JVM riêng qua TCP;
CSL/AOL và iStar/ISCN dùng cùng authoritative USE MSystem.

XML mode AUTHORED nối source, identity/incarnation, context/player, field,
operation/task/goal, observer và capability. XML không tạo model thay file CSL/iStar.

## OCL nằm ở đâu

- CSL: cấu trúc theo Draw.io với lựa chọn Agent/OrgCtx đã được user xác nhận, hai population invariants gốc và
  năm operation pre/post clauses trong annotation native OCL có version.
- iStar: Goal conditions và Task pre/post gốc, cùng hai observer invariants.
- .ocl ngoài: chỉ thêm yêu cầu tùy chọn; hiện comment-only. Không lặp các base
  clauses. Khi thêm clause, khai báo oclClause/context và operation binding trong XML.

Cú pháp annotation riêng của tool, không phải core CSL/iStar:

```text
/* @use-native-ocl 1
context ...
inv/pre/post Name: ...
*/
```

Original parser đọc model/bỏ comment. Adapter đọc annotation có version, kiểm
hash và compile bằng native USE OCL. Tool khác không hiểu annotation sẽ không
kiểm các contract này. Duplicate/non-Boolean/unsupported/missing binding fail closed.
Không có evaluator riêng hay suy diễn arbitrary requirements từ project JaCaMo.

## Semantics và mâu thuẫn Draw.io

Hai Draw.io Desktop được pin tại src/test/resources/auction-reference/diagram-v4.
User đã chọn nhãn Agent/OrgCtx trong ảnh cho auction_capabilities, thay vì
capability Role trong cslSource cũ. CSL có OrgCtx auction_capabilities riêng cho
mỗi physical Jason agent; không còn Role cha hay specializes giữa các Role.
CslAgentContexts là helper của adapter nối context đến đúng một AtomicAgent đã
quan sát, không tạo agent thứ hai. Các optional belief JSON vẫn undefined khi
không yêu cầu/quan sát; không lấy full mental-state stream.

iStar có Agent auction_capabilities chứa các Task, Goal capability và Artifact
Resources. Auctioneer/Participant giữ Goal trách nhiệm; dependency SAME_PLAYER
chỉ nối tới capability của chính player đó trong cùng scheme. participates-in
chỉ hiện với Role occurrence mà player thật sự enact; không ép mỗi agent đóng
cả hai Role. Auction là context viewpoint, không phải agent JaCaMo mới.
Profile ROLE_SPECIALIZATION_V1 giữ admission unary Goal <- Task đã có; model
này không dùng Role inheritance. Original marking/propagation không đổi.

Policy đã được yêu cầu trước vẫn giữ: Participant hoàn thành lượt thử với exact
accepted .fail; Auction phải có bid thật được chấp nhận. O_bid_attempt và
O_bid_accepted là delta công khai so với note success-only cũ, không gọi graph
này là bản chép nguyên xi Draw.io. Java không chứa quyết định riêng cho Auction.
Admin goalSatisfied không tạo actual bid; belief không phải world truth.

Public confirmation order không phải exact internal truth-change order.
WithinDeadline vẫn tắt theo yêu cầu trước; bật lại phải có observer/clock evidence.
COMPLETE_OBSERVED là requirements đang bật ở observed cut, không phải full coverage.

ZIP: examples/auction-current; historical/reference: reference/auction.

Enactment display: `auction_capabilities` is an OrgCtx representing one physical Agent.
`enactsRoles` in the mapping declares the possible Role types (CSL diagram: dashed `may enact`).
AOL draws `enacts` only from the same frozen cut's `CslAgentContexts` and `Plays` links.
It does not assign every Role to every Agent, invent Role ownership or change Goal/Task marking.

Population lifecycle: `AuctioneerPopulation` keeps the exact `size() = 1` predicate.
The mapping explicitly activates it once per group incarnation, after a qualified
source roster contains the bound auctioneer and same-cut native roles are complete.
Before activation the result is SKIPPED/awaiting readiness, not PASS. Activation is
latched, so subsequent complete role loss is a real FAIL; incomplete evidence stays
unavailable. An activation proof is included in body evidence and replay.
