# Auction chuẩn: JaCaMo → CSL/iStar/OCL → USE 7.5

Case này dùng source Auction coordination tutorial đã pin trong
`case-manifest.json`, XML mapping, CSL/iStar/OCL và hai drawio oracle.
JaCaMo chạy **process riêng qua TCP authenticated**; plugin dùng compiler/model
CSL/iStar gốc và native USE OCL trên cùng Session/MSystem.

Trong source checkout, case nằm ở `use-plugin/examples/auction`. Trong ZIP đã
giải nén, case nằm ở **`examples/auction-semantic`**. `examples/auction` trong ZIP
vẫn là toy native cũ để giữ tương thích; không dùng toy đó cho pipeline này.
Không sửa các input đã pin để vượt validation. README này không đổi source hay
oracle; XML là specification được Java loader/selector/model rules thực thi.

## Build và mở từ source

PowerShell tại root repository `use`, dùng JDK 21 và Maven 3.9.9:

```powershell
mvn -B verify
if ($LASTEXITCODE -ne 0) { throw 'BUILD_FAILED' }
mvn -B -pl jacamo-bridge-jacamo -am dependency:build-classpath `
  '-Dmdep.outputFile=target/jacamo-bridge-classpath.txt' '-Dmdep.includeScope=runtime'
if ($LASTEXITCODE -ne 0) { throw 'CLASSPATH_FAILED' }
$case = Join-Path $PWD 'use-plugin\examples\auction'
& .\use-plugin\tools\jacamo-bridge.ps1 `
  -JcmPath (Join-Path $case 'auction.jcm') `
  -SemanticProfile AUCTION_OBSERVED_V1 `
  -MappingSpecificationPath (Join-Path $case 'auction_jacamo_to_csl_rules.xml') `
  -RuntimeClasspathFile "$PWD\jacamo-bridge-jacamo\target\jacamo-bridge-classpath.txt" `
  -RuntimeJournalMaxBytes 268435456 `
  -InteractiveGui -SkipBuild -TimeoutSeconds 1800 `
  -EvidenceDirectory "$PWD\use-plugin\target\manual-auction"
```

Nếu Maven chưa có artifact reactor SNAPSHOT để resolve dependencies, chạy
`mvn -B -pl jacamo-bridge-jacamo -am install` rồi tạo classpath lại. Framework
JaCaMo/Jason/CArtAgO/MOISE/NPL phải đúng fingerprint của case. File classpath
chỉ cung cấp dependencies cho producer; không chép framework vào USE consumer.
Không cần build/sửa repository `jacamo` hay `use4Goal`.

## Chạy từ full ZIP đã giải nén

Full archive: `use-assembly/target/use-7.5.0-use-bin.zip`. Giải nén vào thư mục
riêng; root cài đặt phải có `lib/use-gui.jar`, `lib/plugins/`, `bridge/lib/`,
`tools/`, `examples/auction-semantic/`. Mở PowerShell tại root đó:

```powershell
$case = Join-Path $PWD 'examples\auction-semantic'
$frameworkClasspath = 'D:\duong-dan-cua-ban\jacamo-bridge-classpath.txt'
& .\tools\jacamo-bridge.ps1 `
  -JcmPath (Join-Path $case 'auction.jcm') `
  -SemanticProfile AUCTION_OBSERVED_V1 `
  -MappingSpecificationPath (Join-Path $case 'auction_jacamo_to_csl_rules.xml') `
  -RuntimeClasspathFile $frameworkClasspath `
  -RuntimeJournalMaxBytes 268435456 `
  -InteractiveGui -SkipBuild -TimeoutSeconds 1800 `
  -EvidenceDirectory "$PWD\target\manual-auction"
```

Thay `$frameworkClasspath` bằng file đã tạo ở bước source; mọi JAR trong file
phải còn tồn tại. Helper dùng bridge/plugin JAR của bản cài đặt, stage bản sao
JCM/Java, compile Artifact và ghi recipe bootstrap; input chuẩn không bị sửa.
Không cài thêm full use4Goal plugin hoặc engine JAR trùng FQCN.

## Bấm trên Workbench

1. Trong cửa sổ USE do helper mở, mở JaCaMo Workbench nếu chưa hiện. Profile phải
   là **AUCTION_OBSERVED_V1**, mapping là `auction_jacamo_to_csl_rules.xml`.
2. Bấm **Import JaCaMo Project...**, chọn JCM **staging mà helper hiển thị**. JCM này chứa đúng
   bootstrap Bridge được ghi nhận. Chờ `MODEL_READY`; chưa Start thì các request
   `a1/a2` không tự trở thành Artifact instances.
3. Kiểm tra **Program / trace**: case/source/program/bundle hash, real CslModel,
   real GoalModel, policy iStar, XML rule/selector trace và các OCL category.
4. Bấm **Start Runtime** một lần. Hai scheme và hai Auction được tạo bởi source thật.
   Kết quả random có thể khác nhau; không kỳ vọng mọi Participant đều bid.
5. Trong phần kết quả semantic, xem **Constraints / invocation conformance**,
   **Original iStar history**, **Case evidence**, **Coverage and decisions**.
   Dùng scheme/artifact/player/goal-invocation/step/obligation để theo từng hàng.
6. Mở object/class diagram từ Workbench/USE và evaluate native OCL:
   `Auction.allInstances()->size()` hoặc
   `Auction.allInstances()->forAll(a | a.best_bid >= 0)`. Đây là cùng native state
   mà plugin đã chấp nhận, không phải một runtime phụ.
7. **Pause Jason / Resume Jason** chờ ACK của Jason agents. Pause không đóng băng CArtAgO/MOISE
   đang chạy hoặc đồng hồ NPL. **Stop monitor** dừng quan sát của USE; producer
   vẫn chạy. **Restart monitor** mở history quan sát mới, không restart MAS.
8. Dùng export/replay có sẵn trong Workbench để lưu accepted cut. Replay dùng
   recording `3.1.0` / program `1.1.0` với nguồn/time evidence đóng băng; không
   đọc lại source repo hoặc dùng đồng hồ máy replay. Stop producer không đóng USE.

## Đọc kết quả đúng

- Bid tốt hơn đổi `best_bid/currentWinner`; bid không cải thiện vẫn có thể
  **SUCCEEDED** và được tính là actual bid. `winner` chỉ công bố khi `stop`.
- `.fail` của Jason khác source operation failure, OCL contract FAIL, raw Task
  PENDING và propagated Goal status. Không ghi công operation của player khác.
- MOISE `done`, mission commitment, `goalSatisfied(bid)` admin và actual bid là
  các evidence khác nhau. Admin recovery không sửa một obligation đã unfulfilled
  và không tự tạo thành tích iStar cho Participant không thực hiện Task.
- **INCOMPLETE/PARTIAL/GAP/UNAVAILABLE/UNDEFINED/ERROR/UNSUPPORTED** không phải
  Boolean false hoặc FAIL. Xem diagnostic/quality cùng raw và propagated status.
- Ba dependency tới refined parent và description metadata của iStar được
  xử lý bằng policy/adapter có provenance; strict original limitations vẫn được
  ghi lại. Không thay đổi graph/oracle âm thầm.
- Timestamp publication không chứng minh thời điểm transition nội bộ MOISE.
  Clock nguồn dùng wall time của process NPL, không bảo đảm monotonicity. Những
  field không quan sát được vẫn unavailable; không suy diễn full temporal PASS.
- Deadline bid gốc là 10 giây, decide là một giờ. Test source API/arithmetic
  một giờ không đồng nghĩa đã quan sát một giờ live expiration.
- NPL0.6.1 lấy `created` và absolute deadline ở hai lần đọc clock riêng.
  Live đã quan sát delta10001ms cho duration khai báo10giây. Tool giữ nguyên
  raw timestamps; không làm tròn activation/deadline. Vì vậy equality về duration
  trong OCL oracle literal có thể FAIL do khác biệt này, ngoài nguyên nhân norm
  UNFULFILLED. Đây là giới hạn của điều kiện đã cho, không phải full temporal PASS.

## Quota và phạm vi acceptance

Profile Auction opt-in tự bật step/clock/scheme observers, dùng WORLD observation
và default **256 MiB** journal (`RuntimeJournalMaxBytes = 268435456`). Native và
profile Counter cũ giữ default 64 MiB. Đây là budget hữu hạn theo workload;
run canonical source/TCP đã đo khoảng 203–250 MB cho một prefix hoàn chỉnh của hai Auction.
GUI giữ buffer1024 và timeout5giây hiện có; MaxBufferedEvents/BridgeTimeoutMillis
chỉ áp dụng cho consumer headless của helper, không được coi là cấu hình GUI.
Không thay wire frame, original history hoặc engine pin limits. Không có bảo đảm giữ history
vô hạn: khi hết quota phải báo GAP; export sớm hoặc Stop monitor, không bỏ guard
để làm PASS. Recording3.1 nhận journal tối đa256MiB; các file khác giữ bound cũ.
Workload có toàn bộ bid thất bại, late commitment và thêm operation controls có
thể vượt256MiB. Nếu chủ động cấu hình journal lớn hơn256MiB (ví dụ384MiB =
402653184 bytes), export dùng recording3.2 và ghi `runtimeByteLimit` đã chốt khi
tạo runtime. Replay dùng đúng budget đó, không lấy lại property của máy đang
replay. Budget phải là số nguyên, lớn hơn256MiB và không vượt trần1GiB hiện có;
chỉ áp dụng cho `runtime.jsonl`. Thiếu/sai budget hoặc hạ version bị từ chối.
Recording2.x/3.0/3.1 giữ nguyên giới hạn riêng, không nhận override này.

Build/package và static import không tự chứng minh live completion. Chỉ receipts
E01/E02 và final clean/full/installed Q08 của cùng artifact mới chứng minh bản
release đó. Counter/toy/native tests không được coi là acceptance cho Auction này.
