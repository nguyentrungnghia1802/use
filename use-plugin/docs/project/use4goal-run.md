# Chạy CSL/iStar gốc trên USE 7.5

Thực hiện trong repository **use**; `use4Goal` và `jacamo` chỉ là nguồn tham
chiếu. Runtime JaCaMo chạy JVM riêng, Bridge TCP authenticated. Engine CSL/iStar
nằm trong plugin USE, dùng native USE 7.5 OCL và cùng một Session/MSystem.
Profile mặc định vẫn là `NATIVE_CURRENT`; profile gốc là lựa chọn tường minh.

## Case mapping policy (current)

Production dùng `JACAMO_DECLARATIVE_XML` với rule/binding và capability requirements
trong XML. Không còn Java mapper/observer riêng cho Auction hoặc fallback theo tên
project/agent/task/field. `AUCTION_OBSERVED_V1` và replay case dialect 1.1 / archive
3.1–3.2 đã retire: cần import lại bằng XML declarative để ghi recording mới; không
bỏ hash fence hoặc diễn giải lại recording cũ. Historical Java/tests được giữ tại
`docs/agent/retired-case-java`, không compile/đóng gói, không tính vào gate hiện tại.

Completeness theo native contract inventory: mọi authored OCL và invariant CSL đều
bắt buộc dù context là helper/base type. SKIPPED/UNDEFINED/ERROR hoặc thiếu aggregate
result giữ INCOMPLETE. Chỉ invariant helper do backend tạo mới có thể là supporting;
mọi FAIL thật vẫn hiển thị VIOLATION. COMPLETE_OBSERVED chỉ nói về requirement được
khai báo và observed prefix, không chứng minh toàn bộ source coverage hoặc realtime.

## Main authored project and model-folder UI

Plugins → JaCaMo / use4Goal verification...: chọn project `.jcm` ở dòng đầu,
sau đó Browse **System model folder**. Thư mục phải chứa đúng một `.csl`,
một `.istar`, một `.xml` và tối đa một `.ocl` (không bắt buộc); tên file bất kỳ,
đuôi file không phân biệt hoa/thường. Chỉ đọc trực tiếp thư mục được chọn,
không quét thư mục con. Thiếu loại bắt buộc hoặc trùng bất kỳ loại nào đều báo lỗi;
Prepare kiểm tra lại thư mục. `.jcm` có thể nằm ở thư mục project khác.
Prepare + Import rồi Start Runtime. Kết quả mở trong cửa sổ
modeless riêng; Show results mở lại. Các tabs CSL/iStar/verification dùng cùng
facade/MSystem với input. Đóng kết quả chỉ ẩn; đóng input dispose kết quả và
đóng phiên như trước. Main Results có state selector/Previous/Next/failure checkpoint trở lại,
verdict/coverage boxes ở dưới. Export .use/.cmd và Load OCL thứ hai vẫn bị bỏ.

Goal & Task Status giữ một dòng cho mỗi actor Goal/Task definition của model;
social dependums không có marking riêng không thành dòng UNKNOWN. Chọn dòng
để xem occurrences, original history và current qualified quality riêng. OCL/binding checks là bảng
riêng; FAIL giữ expression/witness thật. Winning Artifact không chứng minh
norm/deadline hoặc mọi Task đã đạt. Ví dụ input và phạm vi requirements ở
`examples/auction/README.md` và `src/test/resources/auction-reference/compact-v1/README.md`.
Forensic Workbench APIs/navigation, raw journal và replay vẫn giữ nguyên.

Runtime classpath tự điền từ `use.jacamo.runtime.classpath-file` nếu có override,
nếu không thì dùng file checkout đã build tại
`D:/_CODE_BANK/Project_/08_Thesis/use/jacamo-bridge-jacamo/target/jacamo-bridge-classpath.txt`;
nếu máy không có đường dẫn này thì dùng mặc định do facade cung cấp từ installation.
Giữ ô đường dẫn và nút Runtime libs để thay thủ công. Không tự tải thư viện qua mạng;
file classpath vẫn phải tồn tại và các JAR bên trong phải hợp lệ khi khởi chạy producer.

Không chọn file `.ocl` nghĩa là không thêm ràng buộc ngoài. Hệ thống freeze một asset
OCL rỗng và pin digest rỗng trong bản XML hiệu lực, không sửa XML trên đĩa; các luật
nhúng trong `.csl`/`.istar` vẫn được compile và yêu cầu đầy đủ. Nếu chọn `.ocl`,
checksum phải khớp XML như trước. Binding tham chiếu luật không tồn tại vẫn bị reject.

Compatibility profile là cách diễn giải/project dữ liệu sang USE, không phải chế độ
nới lỏng kiểm chứng. Chọn `JACAMO_DECLARATIVE_XML` cho bộ model hiện tại:
XML khai báo các binding, nguồn quan sát và requirements. `NATIVE_CURRENT` dùng
projection native hiện tại; `USE4GOAL_OBSERVED_V1` dùng bộ mapping JSON cũ;
`CSL_OBSERVER_V1`/`V2` dùng cấu hình observer CSL JSON cũ, khác version capability.
Các profile cũ được giữ để tương thích và cần đúng cấu hình của chúng.

Trong profile XML, `AUTHORED` đọc model `.csl`/`.istar` viết tay, XML nối ngữ nghĩa
với JaCaMo. `GENERATED` tạo model từ rule trong XML và source project; XML phải được
viết cho chế độ generated. Đây không phải tự suy luận ngữ nghĩa từ mọi project.
Chuyển dropdown không tự chuyển đổi bộ model. Các case study hiện tại dùng `AUTHORED`.

## Build và thử từ source

PowerShell (điều chỉnh đường dẫn JDK/Maven nếu máy cài nơi khác):

```powershell
cd 'D:\_CODE_BANK\Project_\08_Thesis\use'
$env:JAVA_HOME = 'D:\DevTools\Java\jdk-21.0.5'
$env:Path = "$env:JAVA_HOME\bin;D:\DevTools\apache-maven-3.9.9-bin\apache-maven-3.9.9\bin;$env:Path"
mvn -B verify
if ($LASTEXITCODE -ne 0) { throw 'BUILD_FAILED' }
```

Full archive có trong `use-assembly/target/use-7.5.0-use-bin.zip`. Plugin ZIP
riêng: `use-plugin/target/use-jacamo-plugin-1.0.1-v2-frozen.zip`; tên v2 được giữ
để tương thích, archive có thêm profile/recording3.0 opt-in. Consumer không
được chạy với `target/classes` trộn lẫn JAR cũ.

Tạo classpath framework cho **producer** (chỉ thư viện được cung cấp riêng):

```powershell
mvn -B -pl jacamo-bridge-jacamo -am dependency:build-classpath '-Dmdep.outputFile=target/jacamo-bridge-classpath.txt' '-Dmdep.includeScope=runtime'
if ($LASTEXITCODE -ne 0) { throw 'CLASSPATH_FAILED' }
& .\use-plugin\tools\jacamo-bridge.ps1 `
  -JcmPath "$PWD\use-plugin\examples\csl-counter\counter.jcm" `
  -SemanticProfile USE4GOAL_OBSERVED_V1 `
  -ObservationProfile WORLD `
  -MappingSpecificationPath "$PWD\use-plugin\examples\csl-counter\mapping.json" `
  -RuntimeClasspathFile "$PWD\jacamo-bridge-jacamo\target\jacamo-bridge-classpath.txt" `
  -InteractiveGui -SkipBuild -TimeoutSeconds 1800 `
  -EvidenceDirectory "$PWD\use-plugin\target\manual-csl-counter"
```

`-ObservationProfile WORLD` vẫn chạy/quan sát agent, artifact, organization và
control thật; nó bỏ optional Jason mental/execution trace theo từng reasoning
cycle vì mapping này chỉ dùng world/organization outcomes. `AUTO` chọn WORLD
cho profile gốc và giữ FULL cho các profile cũ. Có thể yêu cầu FULL tường minh;
rich trace có cycle counter thay đổi liên tục, cần capacity/workload phù hợp.
Tràn buffer/journal vẫn phải báo GAP, không có bảo đảm history đầy đủ khi quá
quota. Live Counter acceptance dùng WORLD với production defaults64MiB;
FULL trace được kiểm riêng trong regression, không quảng bá cùng capacity.

Helper stage bản sao project, compile `src/env/*.java` bằng JDK nếu project
không có Gradle wrapper, tính fingerprint framework rồi mở JaCaMo và USE.
Source gốc không bị sửa. Cần runtime dependencies đã resolve trong Maven
cache; không chép JaCaMo framework vào plugin consumer.

Trong Workbench, xác nhận profile `USE4GOAL_OBSERVED_V1` và đường dẫn mapping,
Import JCM staging được helper hiển thị, chờ MODEL_READY rồi Start. Chọn các
tab Program/trace, Original engine history, Native/OCL để so sánh. Counter p
đi qua1,2,-1: post increment PASS, invariant Nonnegative FAIL tại -1 là đúng.
Mở native object diagram và evaluate OCL `Counter.allInstances()->size() = 1`
trên chính Session đó. Pause/Resume cần ACK các agent; Stop/Restart monitor
không đóng producer. `.stopMAS` của producer không đóng USE; helper tiếp tục
chờ người dùng đóng GUI.

## Từ archive mới giải nén

Giải nén full USE archive vào thư mục riêng thuộc workspace, ví dụ
`use-plugin/target/manual-install/use-7.5.0`. Thư mục có `bin/start_use.bat`,
`lib/use-gui.jar`, `lib/plugins/use-jacamo-plugin-1.0.1.jar`, `bridge/lib/`,
`tools/jacamo-bridge.ps1`, `examples/csl-counter/`. Không cài thêm engine JAR
hoặc full goal plugin trùng `org.vnu.sme.goal` vào `lib/plugins`.

Dùng helper `tools/jacamo-bridge.ps1` trong thư mục đã giải nén, JCM/mapping
trong `examples/csl-counter`, và `-RuntimeClasspathFile` trỏ tới file classpath
framework đã tạo. Các đường dẫn JAR trong file phải còn tồn tại. Installed
helper lấy contract/adapter từ `bridge/lib` và consumer từ hai JAR đã cài,
không cần target/classes, Maven test classes hoặc repository tham chiếu.
`bin/start_use.bat` chỉ mở USE; helper còn đảm nhiệm producer/Bridge và startup
ownership. Không dùng đường dẫn cũ `use-7.1.1`.

## Kiểm thử archive đã build

Sau `mvn -B verify` tại root `use`, có thể lặp đúng bốn packaged acceptance
checks với **full USE ZIP**, giữ nguyên JAR đã build (hai workload Counter,
inventory, deployment negatives). Parent harness dùng test dependencies; USE
consumer do nó mở chỉ có installed host JAR và driver độc lập, nạp plugin qua
loader thật. Không chạy Maven package lần nữa giữa build và archive test.

```powershell
cd 'D:\_CODE_BANK\Project_\08_Thesis\use\use-plugin'
$taskReport = [xml](Get-Content -Raw '.\target\failsafe-reports\TEST-org.tzi.use.plugins.jacamo.semantics.Use4GoalPackagedPluginIT.xml')
$taskCp = ($taskReport.testsuite.properties.property | Where-Object name -eq 'java.class.path').value
$taskArchive = (Resolve-Path '..\use-assembly\target\use-7.5.0-use-bin.zip').Path
java "-Duse.packaged.hostArchive=$taskArchive" -cp $taskCp org.tzi.use.plugins.jacamo.semantics.Use4GoalPackagedPluginIT
if ($LASTEXITCODE -ne 0) { throw 'FULL_ARCHIVE_ACCEPTANCE_FAILED' }
```

Kết quả phải có `FULL_USE_ARCHIVE_ACCEPTANCE_PASS checks=4`. Receipts và replay
được tạo trong `use-plugin/target/installed-*`; kết quả Counter1 phải có
invocation PASS, Counter2 phải FAIL với `before.p=0`, `after.p=2`. Raw Task2
vẫn PENDING theo engine gốc. Receipt ghi PID/executable, source/JAR origins,
default quota, heartbeat, cleanup và replay parity. Windows có thể tạo
`System32/conhost.exe` cho process Java console; test chỉ cho phép console host
này, vẫn từ chối mọi JVM/runtime con do USE tạo.

## Bằng chứng interface qua process boundary

Producer dùng `Class.forName(..., false, loader)` và API reflection trên subclass
CArtAgO thật để sao chép exact `@OPERATION` signature, provider URI và SHA-256
bytecode vào `SourceEvidence` của declaration. Không initialize class chỉ để
khảo sát interface. USE kiểm tra authority, owner/declaration, signature, loại
evidence và hash trước khi tạo native operation; không cần chép Artifact hay
framework class sang consumer. Đây là bằng chứng type/interface, không phải
bằng chứng Artifact instance đã tồn tại hoặc operation đã chạy. Instance,
property và PRE/POST vẫn phải đến từ accepted runtime observations với identity,
incarnation và correlation riêng. Interface/proof thiếu hoặc sai bị từ chối;
không tự chế operation từ tên trong mapping. Consumer reflection fallback cũ
được giữ cho compatibility, không dùng để vượt gate packaged canonical profile.

## Replay, chẩn đoán và giới hạn

Export Runtime Replay khi runtime quiescent (Pause + verify hoàn tất), rồi mở
recording để Step/Previous/Reset. Recording3.0 lưu program/model/rules/bindings,
engine/native executable hashes và accepted cuts. Replay yêu cầu đúng binary;
không tự chuyển recording gốc sang local monitor. Old2.0 vẫn dùng reader cũ.
STOP/REIMPORT/RESTART cấp history/identity mới; GAP không chứng minh lại khoảng
đã mất. Runtime disconnect hiển thị STALE/GAP thay vì Boolean false.

Stop/Close thu hồi callback trên EDT; TCP và filesystem cleanup chạy bên ngoài
EDT. Caller background chờ completion của cả connection lẫn file cleanup trước
khi hoàn tất retirement hoặc mở monitor mới. Close trên EDT vẫn trả về nhanh;
không được chặn EDT để chờ daemon xóa thư mục runtime.

TaskMarking gốc là historical preSeen/postSeen; invocation pre/post là bằng
chứng tương quan riêng. Cột **Certified execution** dùng certificate
`CERTIFIED_EXECUTION/1.0` của runtime owner: Task và ACHIEVE Goal đã hoàn thành
với input được admit giữ FULFILLED cho cùng context/player/role/episode, dù
marker hiện tại đã kết thúc hoặc nguồn quan sát sau đó thiếu dữ liệu. Certificate
lưu original occurrence key + accepted state version/hash trong semantic result,
checkpoint/rollback cùng engine, được journal/replay giữ lại; UI không tự nâng raw
FULFILLED thành chứng minh. Parent được qualify lại từ các leaf có bằng chứng;
original marking/propagation không đổi. Episode/run/model mới không mượn proof cũ.
MAINTAIN/SUSTAIN/NONE và invariant vẫn theo semantics/current evidence riêng.
Nguồn STALE/GAP không xóa một sự thật lịch sử đã chứng minh, nhưng coverage hiện
tại vẫn INCOMPLETE; OCL observer SKIPPED vẫn chưa được verify. Không gọi toàn bộ
requirements COMPLETE chỉ vì đã có winner hoặc vài Task FULFILLED.
Recording mới khai báo executionEvidencePolicy. Với implementation hash tương
thích, recording không có field này dùng CURRENT_CUT_ONLY/1.0, không tự nâng kết
quả hoặc thay hash lịch sử. Strict implementation/engine hash gate vẫn giữ nguyên:
recording từ binary trước revision này cần archive/binary tương ứng; bản mới sẽ
reject IMPLEMENTATION_INCOMPATIBLE thay vì bỏ guard hoặc viết lại recording. NONE false có thể PENDING theo engine gốc. Missing,
undefined, error, partial và unsupported không bị đổi thành FAIL. Mapping
hiện tại không tuyên bố bảo toàn toàn bộ JaCaMo/iStar grammar hoặc liveness;
SEQUENCE/PARALLEL chỉ giữ trace khi chưa có abstraction được chứng minh.

Production journal mặc định64MiB,512 tail entries,8 pinned cuts/16MiB,
4096 compiled expressions/occurrences. Hết quota phải hiển thị GAP/unavailable,
không âm thầm bỏ dữ liệu. Canonical TCP receiver chờ worker hoàn tất cut trước
khi admit tiếp; backpressure chạy ngoài EDT, không tăng pin quota. Producer vẫn
có bounded TCP buffer và phải báo GAP nếu tốc độ vượt capacity. Native
Evaluator chưa có API force-cancel an toàn;
cleanup đã kiểm thử với workload hữu hạn, không cam kết kill tùy ý một OCL
không kết thúc. Diagnostics `ENGINE_INSTALLATION_*` chặn thiếu/trùng engine,
grammar, provenance, license, sai host7.5/ANTLR4.9.3; sửa bản cài thay vì fallback.

Auction: các input hiện hành nằm trực tiếp trong `use-plugin/examples/auction`: `auction.csl`, `auction.jcm`, `auction.istar`, `auction-mapping.xml`, `auction.ocl`. ZIP: `examples/auction-current`. Các profile/oracle cũ nằm trong `src/test/resources/auction-reference`, không phải input mặc định.


## Xem state theo instance: AOL và ISCN

Sau Prepare + Import / Start Runtime, cửa sổ kết quả có:
- **Execution & Results**: chọn milestone/boundary, xem Goal/Task/OCL và verdict.
- **CSL state (AOL)**: renderer AOL gốc hiển thị instance/attribute của CSL projection được XML chọn ở đúng state đó. Undefined không thành false. Links chỉ lấy từ native state image được giữ lại, không suy ra từ tên hoặc dựng MSystem khác.
- **iStar instances (ISCN)**: chọn context cụ thể ở combobox; actor instances và goal/task occurrences tách theo player/role. Huy hiệu xanh F = fulfilled, vàng P = pending, xám ? = unknown; failed/violated có dấu !. Tên ngắn phục vụ hiển thị; identity đầy đủ vẫn nằm trong evidence. Instance graph dùng original topology/renderer và chính marking đã ghi tại cut; không compile/evaluate một scenario mới để thay kết quả verification.
- **Advanced / Diagnostics**: CSL definitions, iStar definitions, Verification Details. Projection Rules / Program trace / Observation plan đã bỏ khỏi authored workflow; debug Workbench cũ giữ nguyên.

Đổi milestone ở Execution & Results rồi mở AOL/ISCN để xem cùng cut. Đổi context
không trộn actor hoặc episode; topology không khớp hoặc cut đang pending phải báo
unavailable. Reset/reimport/dispose xóa nội dung view. Đây là observed-state viewer,
không phải full-state acquisition hay whole-process exploration từ BPMN.


## Project input và đặc tả hệ thống

Project JaCaMo (.jcm cùng ASL/MOISE/Artifact/resources) là input nguồn thực thi.
CSL/iStar/mapping XML/extra OCL là các đặc tả hệ thống được tác giả soạn thủ công
và chọn/import theo project; việc chọn file không biến chúng thành source runtime.
XML mode AUTHORED nối identity, source features, runtime observations, context,
player, task/goal và native contracts; không tự dịch project thành model ở mode này.

Tool hỗ trợ annotation `/* @use-native-ocl 1 ... */` trong file CSL/iStar để đặt
native USE operation/observer contracts cạnh model. Đây là extension có version
của adapter, không tuyên bố là cú pháp core của use4Goal. Original parser/model và
native USE compiler vẫn là authority. Model bytes được pin trong XML và replay;
clauses nhúng cùng file .ocl bổ sung được kiểm inventory/context/type/collision.
CSL invariant và iStar condition/pre/post gốc vẫn dùng cú pháp chuẩn sẵn có.
File .ocl ngoài có thể comment-only khi không thêm requirement; base contracts
không lặp lại ở file này. Thêm yêu cầu mới cần bổ sung binding XML tương ứng.

## Enactment và điểm lỗi

CSL definitions: OrgCtx đại diện Agent có đường nét đứt `may enact` theo correspondence
`enactsRoles` trong mapping XML. Đây là khả năng ở cấp định nghĩa, không phải sở hữu Role.
AOL: đường `enacts` đòi hỏi cùng frozen cut có Context–Agent và Agent–Role (`Plays`)
đúng identity. Đổi state dùng lại link đã lưu; Role bị gỡ thì state mới mất link.

**Failure Point** mở lỗi đầu tiên theo bộ lọc. Dropdown chọn các lỗi khác theo boundary;
Goal/Task, OCL/constraints và Diagnostics được tách riêng. Lỗi giữ nguyên qua nhiều
boundary xuất hiện một lần; sau phục hồi rồi tái diễn là điểm mới. UNKNOWN/SKIPPED
không là failure. Supporting Task failure không tự biến selected requirement thành VIOLATION.
Nút **Milestones** trở về state nghiệp vụ sau khi mở một lỗi ở audit boundary.

Đã bỏ Inspect Milestone Interval khỏi Execution & Results. Khi cần truy nguyên,
**Audit details → Show all audit boundaries** mở lịch sử kỹ thuật đầy đủ. Đây là
chế độ chẩn đoán, không làm tăng số milestone nghiệp vụ mặc định hoặc thay verdict.

Failure Point mặc định ghi số milestone của state nghiệp vụ liên quan. Tool giữ
nguyên exact snapshot/evidence của lỗi, kể cả lỗi ở giữa hai milestone. Bật
**Audit details** chuyển cả danh sách điểm lỗi và chọn state sang số boundary;
tắt để trở về milestone. Lỗi trước khi có state được nhận ghi Before first milestone.
Milestone là snapshot CSL/USE đã chiếu từ JaCaMo theo `presentation/state` trong
mapping (object, thuộc tính, liên kết được chọn); Goal/Task và OCL là kết quả
đánh giá gắn với snapshot. Event kỹ thuật không đổi state được gộp; vẫn giữ
initial/latest cuts và audit đầy đủ.

Invariant lifecycle: mapping may declare `runtime/invariantActivations/activate`
with `LATCH_ONCE_QUALIFIED/1.0`, an exact native CSL invariant target, qualified
table and context identity column. It starts operational scope for that context
incarnation only; it never changes predicate truth or clears later violations.
Readiness needs a complete source table/row and complete same-cut native roles.
Before readiness the rule is unavailable, not vacuously passing. The source-backed
activation certificate is included in invariant body evidence. Raw native USE
checks outside this observed workflow still evaluate the unconditional predicate.

Derived attributes now contribute transitive native AST dependencies. Receiver
identity and enclosing iterator bindings are preserved; cycles and derived @pre
reads lacking a supported pinned pre-view are rejected as unsupported rather than
borrowing current values. Failure Point retains an unresolved episode across
pending/SKIPPED cuts; qualified PASS/FULFILLED is required before a repeat becomes
a new episode. Body counterexamples remain individually inspectable, without an
extra duplicate entry for the matching aggregate.

## Kiểm chứng riêng từng execution/Entity CSL

Execution & Results bắt buộc chọn một execution đã quan sát; không có All executions.
Mỗi root Goal là một instance riêng trong context/episode của execution, không phải
hai root đóng góp vào một Goal chung. Dòng Goal/Task mang nhãn Entity đang chọn.
Milestone phản ánh các giá trị Entity đã bind của execution đó; Audit details vẫn
trỏ về exact native cut/boundary trong một MSystem chung.

Mỗi context freeze bộ native OCL riêng. Outer owner population là tập object đã bind
với scope: Entity/resource/self, role/player/context và structural ancestors. Observer
được nối tường minh bằng `<scope><observer projectRef="..." subjectColumn="..."/>`:
program, epoch, project và physical subject phải khớp đúng một object. Không ghép
bằng tên class/agent, vị trí, id a1/a2 hoặc suy đoán từ giá trị thuộc tính.

Native USE vẫn đánh giá nguyên predicate và giữ existential/negation, @pre/invocation
và coverage gates. Scope thiếu/không duy nhất owner hoặc thiếu required aggregate giữ
INCOMPLETE. Shared structural/group constraints được kiểm tra trong mỗi scope liên
quan; biểu thức chủ động đọc dữ liệu chung vẫn giữ nguyên nghĩa đó. Generated helper
requirements giữ phân loại supporting/required trong native inventory. COMPLETE_OBSERVED
ở pane kết quả yêu cầu root iStar và toàn bộ OCL bắt buộc của execution có bằng chứng.

CSL State (AOL) và ISCN có bộ chọn execution đồng bộ với Results. AOL là projection
của trạng thái native ở exact cut: chỉ hiển thị Entity và các context/role/player đã
bind. Nét liền là liên kết cấu trúc native; nét đứt xanh `participates (execution)` là
liên kết trình bày có cast/role-lifetime/commitment witness. Nó không tạo association
CSL mới hay thay đổi MSystem. Cùng ở group chưa đủ để tự nối tham gia mọi Entity.
Một agent có thể có liên kết ở cả hai auction khi có binding tham gia cho cả hai.

`presentationState.objectQuality` giữ chất lượng riêng từng object: một Entity chưa
có dữ liệu không được che mất Entity độc lập đã có dữ liệu. Các giá trị không đủ
bằng chứng và scope thiếu vẫn unavailable; nguồn coverage được báo riêng. Không tạo
MSystem, model hành vi hoặc engine đánh giá thứ hai. Source/replay ghi đủ evidence gốc.
