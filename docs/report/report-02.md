# BÁO CÁO TOÀN DIỆN DỰ ÁN: USE JACAMO PLUGIN
## Bối Cảnh, Kiến Trúc Formal Verification và Đặc Tả Hệ Thống (Context Specification for AI)

> **Mục đích tài liệu:** Tài liệu này đóng vai trò là bản **Context Master Document (System Context & Architectural Specification)** tóm lược toàn bộ dự án `USE JaCaMo Plugin`. Khi cung cấp file này cho bất kỳ mô hình AI nào, AI có thể hiểu trọn vẹn ngữ cảnh kỹ thuật, cơ sở lý thuyết, kiến trúc phần mềm, các quyết định mô hình hóa (Metamodel V1/V2), quy trình kiểm chứng hai chế độ (Design-Time & Runtime), các quy tắc biên dịch OCL, lịch sử các phase (Phase 01 – 44) và các ranh giới/giới hạn nghiên cứu (Non-goals).

---

## 1. TỔNG QUAN DỰ ÁN & ĐỊNH VỊ NGHIÊN CỨU (EXECUTIVE SUMMARY)

### 1.1 Tên và Định nghĩa Dự Án
- **Tên dự án:** USE Extension for JaCaMo Design-Time and Runtime Verification using UML/OCL (`use-plugin`).
- **Nền tảng chủ (Host System):** Hệ thống **USE (UML-based Specification Environment)** phiên bản **7.5.0** (gồm 5 module Maven reactor: `pom.xml`, `use-core`, `use-gui`, `use-assembly`, `use-plugin`).
- **Mục tiêu cốt lõi:** Xây dựng một plugin mở rộng cho USE, đóng vai trò là một **Formal Verification Representation & Mirroring Engine**, có khả năng biến các ứng dụng Multi-Agent System (MAS) phát triển trên nền tảng **JaCaMo** thành mô hình hình thức trong USE, sử dụng ngôn ngữ ràng buộc **OCL (Object Constraint Language)** để kiểm chứng tính đúng đắn cả ở giai đoạn thiết kế (Design-Time) lẫn thời gian thực (Runtime).

### 1.2 Bài toán Khoa học & Đóng góp Kỹ thuật (Research Framing)
JaCaMo là nền tảng hàng đầu phát triển Multi-Agent Systems kết hợp 3 chiều trừu tượng:
1. **Jason (Agent Dimension):** Kiến trúc tác tử nhận thức BDI (Belief-Desire-Intention) dựa trên AgentSpeak.
2. **CArtAgO (Environment Dimension):** Môi trường tính toán hướng Artifact (Artifact-based Environment), cung cấp trạng thái quan sát được (`Observable Properties`) và thao tác tương tác (`Operations`).
3. **Moise (Organization Dimension):** Cấu trúc tổ chức, nhóm (`Groups`), đề án (`Schemes`), vai trò (`Roles`), nhiệm vụ (`Missions`) và quy chuẩn (`Norms`).

**Điểm nghẽn nghiên cứu:** JaCaMo cung cấp ngữ nghĩa thực thi (execution semantics) rất mạnh nhưng thiếu cơ chế kiểm chứng hình thức đa chiều (cross-dimensional formal verification) toàn diện đối với trạng thái hệ thống và ràng buộc nghiệp vụ.  
**Giải pháp của dự án:** Kết nối thế giới JaCaMo sang mô hình UML/OCL của USE:
```text
JaCaMo Multi-Agent System (.jcm, .asl, Java Artifacts, Moise XML)
                           │
                           ▼ (Static Discovery & Extraction)
               Semantic Intermediate Model (IR)
              ┌────────────┴────────────┐
              ▼                         ▼
   Structural Mapping V2        Constraint Extraction
              └────────────┬────────────┘
                           ▼
          USE Metamodel MModel + MSystemState + OCL
                           │
           ┌───────────────┴───────────────┐
           ▼                               ▼
Mode A: Design-Time Offline         Mode B: Live Runtime Mirror
(Multiplicity, structural OCL)     (Event queues, live OCL eval)
```

### 1.3 Kỷ luật Tuyên ngôn & Thứ bậc Chân lý (Authority & Claim Discipline)
Dự án tuân thủ nghiêm ngặt thứ tự ưu tiên khi đưa ra các tuyên ngôn về tính năng/ngữ nghĩa:
1. **Production Code (`src/main`):** Mã nguồn thực thi là chân lý tối thượng.
2. **Executable Tests & Evidence:** Các bộ test regression, golden fixtures và bằng chứng nghiệm thu (retained evidence logs).
3. **Frozen Canonical Contracts:** Metamodel Ecore V2 và Structural Mapping V2 (đã freeze, kiểm tra qua mã băm SHA-256).
4. **Active Architecture Documentation:** Tài liệu kiến trúc chuẩn trong `use-plugin/docs/project/`.
5. **Historical Documents:** Các plan cũ, task checklist hay v1.0.1 hotfix docs chỉ mang giá trị lịch sử tham khảo.

Nhãn trạng thái tiêu chuẩn được sử dụng:
- **`IMPLEMENTED`**: Đã hiện thực trong code production và được gọi trực tiếp.
- **`PARTIAL`**: Hỗ trợ một tập con có khai báo rõ ràng.
- **`TESTED ONLY`**: Chỉ kiểm chứng qua fixture/test cô lập, chưa thành tính năng generic.
- **`NOT IMPLEMENTED`**: Chưa có đường dẫn thực thi.
- **`OUT OF SCOPE`**: Cố ý không đưa vào phạm vi nghiên cứu/release.
- **`RESEARCH LIMITATION`**: Giới hạn ngữ nghĩa không thể tự suy diễn nếu thiếu bằng chứng.

---

## 2. KIẾN TRÚC HỆ THỐNG & CÁC LỚP XỬ LÝ (SYSTEM ARCHITECTURE)

Hệ thống được tổ chức thành 7 lớp kiến trúc phân tách nghiêm ngặt:

```text
 ┌─────────────────────────────────────────────────────────────┐
 │ 1. Extraction Layer                                         │
 │    - JCM Discovery, Jason ASL Parser, CArtAgO Java Extractor│
 │    - Moise XML Parser, Project-root binding.json Resolver   │
 └──────────────────────────────┬──────────────────────────────┘
                                │
                                ▼
 ┌─────────────────────────────────────────────────────────────┐
 │ 2. Semantic Intermediate Representation (IR) Layer          │
 │    - 6-part collision-safe Semantic IDs                     │
 │    - Immutable Ecore-aligned descriptors                    │
 └──────────────────────────────┬──────────────────────────────┘
                                │
               ┌────────────────┴────────────────┐
               ▼                                 ▼
 ┌───────────────────────────┐     ┌───────────────────────────┐
 │ 3. Mapping Engine (V2)    │     │ 4. Constraint Translation │
 │    - Canonical Mapping    │     │    - Context/guard parser │
 │    - Target-only Rank     │     │    - OCL Generator        │
 │      Projections          │     │    - Provenance tracking  │
 └─────────────┬─────────────┘     └─────────────┬─────────────┘
               └────────────────┬────────────────┘
                                │
                                ▼
 ┌─────────────────────────────────────────────────────────────┐
 │ 5. USE Transformation & Materialization Layer               │
 │    - MModel construction, Class/Association generation      │
 │    - Initial State MSystemState planning (!create, !insert) │
 └──────────────────────────────┬──────────────────────────────┘
                                │
               ┌────────────────┴────────────────┐
               ▼                                 ▼
 ┌───────────────────────────┐     ┌───────────────────────────┐
 │ 6. Verification Engine    │     │ 7. Runtime Adapter &      │
 │    - OCL Compiler         │     │    Mirroring Service      │
 │    - Constraint Registry  │     │    - In-process socket    │
 │    - Incremental checker  │     │    - Authoritative resync │
 │    - Violation navigation │     │    - RuntimeMutationEngine│
 └───────────────────────────┘     └───────────────────────────┘
```

### 2.1 Định danh Ngữ nghĩa Chuẩn hóa (6-part Semantic ID)
Để ngăn ngừa trùng lặp danh tính giữa các thực thể đa tác tử thuộc các file khác nhau, hệ thống áp dụng cấu trúc định danh 6 trường phân tách bằng dấu hai chấm `:`, các phân đoạn trong `ownerPath` phân tách bằng `/` và được UTF-8 percent-encoded:
$$\text{SemanticId} = \texttt{jacamo:\{projectId\}:\{dimension\}:\{kind\}:\{ownerPath\}:\{localId\}}$$
*Ví dụ:*
- `jacamo:auction:agent:Agent:MAS:auctioneer` (Tác tử auctioneer trong chiều Agent)
- `jacamo:auction:environment:Artifact:MAS/ws:auction1` (Artifact auction1 trong workspace ws thuộc chiều Environment)
- `jacamo:auction:organisation:Role:org1/group1:bidder` (Vai trò bidder trong group1)

---

## 3. BA CHIỀU MULTI-AGENT & QUAN HỆ LIÊN CHIỀU (CROSS-DIMENSIONAL BINDINGS)

### 3.1 Chi tiết 3 Chiều Trừu Tượng
1. **Agent Dimension (Jason):**
   - Đại diện cho niềm tin (`Belief`), mục tiêu (`Goal`), kế hoạch (`Plan`), kích hoạt (`TriggeringEvent`), hành động (`Action`).
   - Chú ý: TriggeringEvent kế thừa Action trong mô hình Ecore nhưng có ngữ nghĩa kích hoạt riêng biệt.
2. **Environment Dimension (CArtAgO):**
   - Đại diện cho không gian làm việc (`Workspace`), thực thể môi trường (`Artifact`), thuộc tính quan sát được (`ObsProperty`), thao tác (`AbsOperation` / `OperationCall`).
   - Thao tác artifact là nguồn gốc thay đổi trạng thái môi trường.
3. **Organisation Dimension (Moise):**
   - Đại diện cho tổ chức (`Organisation`), nhóm (`Group`), đề án (`Scheme`), vai trò (`Role`), nhiệm vụ (`Mission`), quy chuẩn (`Norm`).
   - Giữ nguyên cấu trúc phân cấp vai trò (`min`, `max` bounds), liên kết nhiệm vụ và điều kiện kích hoạt quy chuẩn.

### 3.2 Quan hệ Liên Chiều Cốt Lõi (Cross-Dimensional Relationships)
Hệ thống bảo toàn tuyệt đối 8 liên kết thực thể xuyên suốt các chiều:
- `ExternalAction.operation → AbsOperation`: Hành động ngoài của tác tử Jason liên kết tới thao tác artifact CArtAgO.
- `ObsProperty.obsproperty → Belief`: Thuộc tính quan sát được của Artifact ánh xạ tới niềm tin nhận thức của Agent.
- `OGoal.OGoalToGoal → Goal`: Mục tiêu tổ chức của Moise liên kết tới mục tiêu BDI của tác tử Jason.
- `Agent.joinWorkspace → Workspace`: Tác tử tham gia vào không gian làm việc môi trường.
- `Agent.artifact → Artifact`: Tác tử tương tác và giữ tham chiếu tới Artifact.
- `Role.players → Agent`: Tác tử nhận vai trò trong tổ chức.
- `Organisation.deploysAgent → Agent`: Tổ chức triển khai và phân công tác tử.
- `Plan.RefArtifact → Artifact`: Kế hoạch của tác tử tham chiếu tới Artifact đích.

---

## 4. TIẾN TRÌNH METAMODEL: TỪ V1 (HISTORICAL) ĐẾN V2 (FROZEN RELEASE CANDIDATE)

Dự án trải qua hai thế hệ Metamodel với bước chuyển giao quyết định tại Phase 29 – 44:

### 4.1 Metamodel V1 (Baseline lịch sử v1.0.1)
- Dựa trên `Core/Metamodel/version-1/` và `jacamo-use-mapping-v1.json`.
- Gồm 37 EClass, 67 EAttribute, 63 EReference, 14 Inheritance edges.
- **Hạn chế của V1:** Xảy ra hiện tượng ràng buộc phụ thuộc tĩnh cứng nhắc (coupling), gặp vấn đề với thứ tự quan hệ đối ngẫu (`ordered opposite fidelity blocker`), và khó mở rộng cho các hệ thống MAS có dynamic artifact phức tạp.

### 4.2 Metamodel V2 (Baseline chuẩn hoá hiện tại - Phase 44 Freeze)
- **Tập tin chuẩn:** `Core/Metamodel/version-2/jacamo_v2_complete.ecore` (SHA-256: `4ae51638...`).
- **Structural Mapping V2:** Phiên bản `2.2.0` (`jacamo-use-mapping-v2.json`, SHA-256: `fc03b90c...`).
- **Runtime Mapping V2:** Phiên bản `2.0.0`, schema `3.0.0` (`jacamo-use-runtime-mapping-v2.json`, SHA-256: `5b2c00f0...`).
- **Đặc trưng đột phá - Target-only Rank Projection:**
  - Vấn đề ở V1: Ecore định nghĩa các tham chiếu có thứ tự độc lập nhưng USE không hỗ trợ tự nhiên ngữ nghĩa ordered eOpposite hai chiều mà không làm sai lệch số phần tử.
  - Giải pháp V2: Sử dụng cơ chế chiếu thứ hạng (Rank Projection) chỉ nhắm vào đích (`target-only order projection`), bảo toàn thứ tự danh sách nguồn độc lập mà không suy diễn quan hệ hai chiều sai sự thật.
- **Nguyên tắc Khóa An Toàn (Fail-Closed Active Contract):**
  - Lớp `ActiveBaseline` tự động kiểm tra mã băm SHA-256 của Ecore, Mapping và Schema ngay lúc nạp.
  - Nếu phát hiện file bị sửa đổi, thiếu hoặc sai fingerprint, hệ thống lập tức **từ chối chạy (Fail-closed)**, tuyệt đối không âm thầm fallback về V1.

### 4.3 Bảy Chiếu Nghiệm Thu (7 Verification Projections - VP001 đến VP007)
| Mã | Tên Projection | Bản chất Hợp đồng Kỹ thuật |
| :---: | :--- | :--- |
| **VP001** | Concrete Artifact Type Projection | Template có điều kiện tạo subclass cụ thể cho `Artifact` trong USE từ class Java CArtAgO. |
| **VP002** | Observable Property State Projection | Template ánh xạ thuộc tính quan sát được thành attribute định kiểu trên subclass của Artifact. |
| **VP003** | CArtAgO Operation Projection | Template ánh xạ chữ ký method artifact thành USE Operation (hỗ trợ pre/postcondition). |
| **VP004** | ExternalAction-to-AbsOperation | Tái sử dụng ràng buộc cấu trúc liên kết hành động Jason với thao tác CArtAgO. |
| **VP005** | ObsProperty-to-Belief | Tái sử dụng ràng buộc cấu trúc giữa thuộc tính môi trường và niềm tin tác tử. |
| **VP006** | Organisational-goal-to-Goal | Tái sử dụng ràng buộc mục tiêu tổ chức Moise sang mục tiêu BDI. |
| **VP007** | Norm Structure Preservation | Bảo toàn cấu trúc, nhãn và ràng buộc định danh của Norm mà **không** tự ý chuyển ngữ nghĩa nghĩa vụ (Deontic) thành OCL tương đương. |

---

## 5. CƠ CHẾ KIỂM CHỨNG HAI CHẾ ĐỘ (DUAL VERIFICATION PIPELINE)

### 5.1 Chế độ A: Kiểm chứng Thiết Kế / Ngoại tuyến (Design-Time / Offline)
- **Kịch bản:** Chạy kiểm tra mô hình khi JaCaMo **chưa/không chạy**.
- **Quy trình:**
  1. Đọc file cấu hình `.jcm` (và project-root `binding.json` nếu có).
  2. Bóc tách AST của các file `.asl`, Java Artifact, Moise `.xml`.
  3. Xây dựng Semantic IR và ánh xạ sang mô hình USE (`.use` hoặc trực tiếp đối tượng `MModel`).
  4. Khởi tạo trạng thái ban đầu (`MSystemState` qua các lệnh shell `!create`, `!set`, `!insert`).
  5. Nạp Core OCL (`jacamo-core-v2.ocl`) và Project OCL (`verification/<project>.ocl`).
  6. USE OCL Evaluator chạy kiểm tra tính nhất quán cấu trúc, quan hệ bội số (multiplicity) và invariants ban đầu.

### 5.2 Chế độ B: Kiểm chứng Thời Gian Thực (Runtime Verification & Mirroring)
- **Kịch bản:** Kiểm chứng đồng thời khi hệ thống JaCaMo **đang thực thi trực tiếp**.
- **Nguyên tắc "Quan Sát Thuần Túy" (Observe-Only Mirror):**
  - USE plugin là một **tấm gương phản chiếu (verification mirror)**.
  - Plugin **không** can thiệp, không chặn luồng chạy (block), không làm thay đổi logic lập lịch của tác tử JaCaMo.
  - Khi phát hiện vi phạm ràng buộc OCL, hệ thống ghi nhận vi phạm (`violation report`), định vị nguồn gốc, phát cảnh báo, chứ không hành xử như một "control/enforcement agent".
- **Vòng đời Trạng thái Runtime Adapter (Lifecycle States):**
  $$\texttt{OFFLINE} \longrightarrow \texttt{MODEL\_READY} \longrightarrow \texttt{CONNECTING} \longrightarrow \texttt{SYNCING} \longrightarrow \texttt{LIVE} \rightleftharpoons \texttt{STALE} \ (\text{hoặc} \ \texttt{ERROR})$$
  - `OFFLINE`: Chưa nạp project hoặc đã đóng kết nối.
  - `MODEL_READY`: Đã nạp xong mô hình tĩnh, sẵn sàng kết nối.
  - `CONNECTING`: Đang khởi tạo bộ lắng nghe sự kiện từ socket/in-process connectors.
  - `SYNCING`: Đang thực hiện Snapshot có thẩm quyền (`Authoritative Snapshot`) để đồng bộ trạng thái ban đầu của MAS.
  - `LIVE`: Trạng thái hợp lệ duy nhất để chạy kiểm chứng OCL runtime.
  - `STALE`: Mất kết nối tạm thời; khi kết nối lại bắt buộc phải lấy lại snapshot mới (zero-drift resynchronization).
  - `ERROR`: Lỗi không thể khắc phục (tràn hàng đợi backpressure, sai lệch snapshot).

- **Đồng bộ Thao tác (Operation Pre/Postconditions):**
  Khi một Action/Operation diễn ra trong JaCaMo:
  $$\text{Operation Enter} \longrightarrow \text{Chụp pre-state} \longrightarrow \text{Kiểm OCL Precondition} \longrightarrow \text{JaCaMo chạy} \longrightarrow \text{Operation Exit} \longrightarrow \text{Kiểm OCL Postcondition (@pre)}$$

---

## 6. KHUNG RÀNG BUỘC OCL & NGUYÊN TẮC DỊCH THUẬT (OCL ARCHITECTURE)

### 6.1 Bốn Phân Vùng Ràng Buộc OCL
1. **Translated JaCaMo OCL:** Ràng buộc tự động trích xuất từ plan context của Jason hoặc guard của CArtAgO có ngữ nghĩa toán học/logic tường minh.
2. **JaCaMo Core OCL (`jacamo-core-v2.ocl`):** Ràng buộc nền tảng viết tay tái sử dụng cho mọi dự án (kiểm tra tính toàn vẹn đa chiều, sự tương thích hành vi tác tử và artifact).
3. **Case-Study / Domain OCL (`verification/<project>.ocl`):** Ràng buộc đặc thù bài toán nghiệp vụ (ví dụ: trong bài toán Đấu giá Auction: giá bid sau phải lớn hơn giá trước, số dư tài khoản không âm).
4. **User-Authored OCL:** Ràng buộc do người dùng nạp động từ giao diện Workbench.

### 6.2 Phân loại Khả Thi Dịch Thuật Ràng Buộc (Translation Feasibility)
Hệ thống tuân thủ nguyên tắc **"Không đoán mò ngữ nghĩa" (Provenance-aware, No guessing)**:
- **`EXACT`**: Ngữ nghĩa chuyển đổi tương đương 1-1 (ví dụ: so sánh số học `B >= A`).
- **`SOUND_SUBSET`**: Bảo toàn một hướng kiểm chứng an toàn (sound approximation).
- **`LOSSY`**: Chỉ cho phép khi có cờ cấu hình nghiên cứu và phải phát cảnh báo `WARNING`.
- **`UNSUPPORTED`**: Mã Java phức tạp, AgentSpeak plan nâng cao hoặc ngữ nghĩa Deontic không thể chuyển dịch -> **Giữ nguyên nguồn, không sinh OCL bậy bạ, phát sinh Diagnostic INFO/WARNING**.

---

## 7. CÁC ĐỘT PHÁ KỸ THUẬT & LỊCH SỬ SỬA LỖI (KEY HOTFIXES & MILESTONES)

### 7.1 Hai Lỗi Nghiêm Trọng Được Khắc Phục Ở Bản v1.0.1 (P1 & P2 Fixes)
- **Lỗi P1 (Workspace Lifecycle Race Condition):**
  - *Nguyên nhân:* Trước đây khi `DefaultJaCaMoFacade` rebuild hoặc reload profile, một Workspace mới được tạo ra nhưng `RuntimeMirrorService` vẫn giữ tham chiếu cũ tới `RuntimeMutationEngine` và `RuntimeVerificationEngine` (vốn sở hữu tham chiếu `MSystem` cũ).
  - *Giải pháp:* Hợp nhất toàn bộ luồng thay thế vào một phương thức duy nhất `installWorkspace`. Khi nạp lại, hệ thống hủy đăng ký stream cũ, làm rỗng hàng đợi sự kiện, chuyển giao các alias runtime hợp lệ sang đối tượng ngữ nghĩa mới, thay thế đồng thời cả mutation và verification consumer, rồi mới đưa trạng thái về `LIVE`.
- **Lỗi P2 (Bỏ quên production binding.json):**
  - *Nguyên nhân:* Lớp nạp dự án tĩnh `StaticProjectImporter` ban đầu gọi thẳng `SemanticResolver` mà không đọc file `binding.json` ở project root, dẫn tới ambiguity khi có nhiều artifact/action cùng tên.
  - *Giải pháp:* `StaticProjectImporter` bắt buộc đọc `binding.json`, sử dụng `ExactSemanticResolver` để phân giải kiểu chính xác dựa trên mã băm nội dung nguồn (`source hashes`). Nếu hash lệch báo lỗi `BINDING_STALE`, định dạng sai báo `BINDING_INVALID`.

### 7.2 Lịch Sử Phát Triển Qua 44 Phase
- **Phase 01 – 15 (V1 Foundation):** Thiết lập plugin skeleton, parser 3 chiều, semantic IR, transformation pipeline, OCL extraction, runtime foundation, connectors, UI và case study Auction v1.0.0.
- **Phase 16 – 28 (Hardening & V1 Closure):** Hoàn thiện phân quyền chiều runtime, định danh theo thế hệ (`generation-scoped event history`), launcher audit, hoàn thiện kiểm thử clean reactor v1.0.1.
- **Phase 29 – 35 (V2 Architecture Migration):** Chuyển đổi sang Metamodel V2 Ecore, giải quyết bài toán `ordered opposite fidelity`, chuẩn hóa ánh xạ V2 (350/350 tests PASS).
- **Phase 36 – 43 (V2 Hardening & Trace Integrity):** Hoàn thiện V2 trace binding, loại bỏ danh tính runtime rác (stale identities), tối ưu hóa hàng đợi backpressure và mirror correctness.
- **Phase 44 (Final V2 Freeze):** Khóa chính thức bộ ba Metamodel V2 + Structural Mapping V2 + Runtime Mapping V2. Đạt chuẩn tái lập build 100% (**374/374 tests PASS clean reactor**, kể cả khi build ở thư mục di dời hoàn toàn mới).

---

## 8. CÁC RANH GIỚI VÀ ĐIỀU KHÔNG TUYÊN BỐ (NON-GOALS & BOUNDARIES)

Để tránh hiểu nhầm về phạm vi năng lực khi AI phân tích hoặc sinh mã, dự án xác định rõ các **Non-goals**:
1. **Không thay thế JaCaMo Engine:** Plugin không phải là trình thông dịch Jason, không thực thi runtime CArtAgO, không thay thế bộ lập lịch tác tử.
2. **Không thực thi kiểm soát cưỡng chế (No Enforcement/Control Agent):** Plugin chỉ quan sát và báo cáo sai phạm OCL, không chủ động chặn (block) hay roll-back hành động của tác tử trong JaCaMo.
3. **Không dịch tổng quát ngữ nghĩa Nghĩa vụ (No General Deontic/NPL-to-OCL Translation):** Các quy chuẩn (`Norms`) của Moise chỉ được bảo toàn về mặt cấu trúc thực thể. Hệ thống không tự động chuyển đổi logic nghĩa vụ (Obligation/Permission/Sanction) thành OCL tương đương.
4. **Không dịch mã Java tuỳ ý:** Không dịch các thân hàm Java phức tạp trong Artifact thành biểu thức OCL.
5. **Không hỗ trợ launcher ngoài không có kiểm soát:** Kiểm chứng runtime tập trung vào tương tác API in-process có bảo đảm; không hỗ trợ khởi động các ứng dụng GUI `.jcm` độc lập chưa cấu hình adapter.

---

## 9. BẢN ĐỒ THƯ MỤC DỰ ÁN & TÀI LIỆU QUAN TRỌNG (REPOSITORY & DOCS MAP)

```text
use/
├── pom.xml                                # Maven reactor cấu hình 5 module
├── use-core/                              # Lõi USE: MModel, MSystemState, OCL Evaluator
├── use-gui/                               # Giao diện USE và hệ thống nạp plugin
├── docs/report/
│   ├── report-01.md                       # Báo cáo kỹ thuật sửa lỗi v1.0.1 Hotfix
│   └── report-02.md                       # BẢN BÁO CÁO TOÀN DIỆN NÀY (AI Context Document)
└── use-plugin/
    ├── pom.xml                            # Module plugin USE JaCaMo
    ├── Core/
    │   ├── Metamodel/
    │   │   ├── version-1/                 # Metamodel V1 (Historical)
    │   │   └── version-2/                 # Metamodel V2 (jacamo_v2_complete.ecore - CANONICAL)
    │   └── Mapping/
    │       ├── version-1/                 # Structural Mapping V1 (Historical)
    │       └── version-2/                 # Structural Mapping V2 (jacamo-use-mapping-v2.json)
    ├── src/main/java/.../jacamo/
    │   ├── DefaultJaCaMoFacade.java       # Mặt tiền API điều phối toàn bộ plugin
    │   ├── extraction/                    # Bóc tách AST JCM, ASL, Java, Moise XML
    │   ├── semantic/                      # Mô hình Semantic IR và 6-part Semantic ID
    │   ├── mapping/                       # Bộ đọc mapping, quy hoạch biến đổi và rank projection
    │   ├── materialization/               # Hiện thực hoá USE classes, associations, initial state
    │   ├── constraint/                    # Trích xuất và dịch ràng buộc JaCaMo sang OCL
    │   ├── trace/                         # Cầu nối truy vết Bidirectional Trace Resolver
    │   ├── runtime/                       # Runtime adapter, mutation engine, mirror service
    │   └── ui/                            # Giao diện Workbench tích hợp trong USE GUI
    ├── src/main/resources/.../jacamo/
    │   ├── ocl/                           # jacamo-core-v2.ocl (Core invariants)
    │   └── runtime/                       # jacamo-use-runtime-mapping-v2.json
    ├── src/test/resources/
    │   ├── auction/                       # Case study mẫu: Đấu giá Anh (English Auction MAS)
    │   └── counter-team/                  # Case study phụ: Kiểm tra genericity và closure
    └── docs/project/
        ├── 00-README.md                   # Chỉ mục tài liệu hướng dẫn chuẩn của dự án
        ├── 01-vision-scope.md             # Tầm nhìn, phạm vi và tiêu chí thành công
        ├── 02-system-architecture.md      # Thiết kế kiến trúc tổng thể và luồng dữ liệu
        ├── 07-constraint-translation...   # Cơ chế trích xuất và sinh biểu thức OCL
        ├── 10-runtime-adapter.md          # Đặc tả runtime adapter, lifecycle và snapshot
        └── v2-migration/                  # Hồ sơ chi tiết di chuyển và khóa Metamodel V2 (Phase 29-44)
```

---

## 10. HƯỚNG DẪN DÀNH CHO AI KHI XỬ LÝ / SINH MÃ TRONG DỰ ÁN (AI OPERATING GUIDELINES)

Khi được yêu cầu viết code, fix bug hoặc phân tích trong codebase này, AI cần tuân thủ 5 nguyên tắc vàng:
1. **Luôn dùng SemanticId chuẩn 6 thành phần:** Không bao giờ dùng tên trần (`bare names`) để phân giải quan hệ giữa các thực thể tác tử, artifact và tổ chức.
2. **Tuân thủ quy chế Fail-Closed:** Khi nạp cấu hình mapping hoặc schema, phải đối soát hash SHA-256; không âm thầm nuốt lỗi hoặc fallback sang dữ liệu mặc định không kiểm chứng.
3. **Tôn trọng mô hình Mirroring thuần túy:** Tuyệt đối không thêm logic điều khiển tác tử hoặc chặn luồng JaCaMo trong mã nguồn của adapter. Mọi mutation trên `MSystemState` phải thông qua `RuntimeMutationEngine` có truy vết (`TraceIndex`).
4. **Không suy diễn Deontic sang OCL:** Khi gặp Norms của Moise, chỉ bảo toàn metadata cấu trúc (Projection VP007), không tự ý sinh OCL invariants ép buộc nghĩa vụ đạo đức nếu không có quy tắc nghiệp vụ rõ ràng từ file case study OCL.
5. **Đảm bảo tính tái lập (Determinism):** Mọi phép sinh tên USE, quan hệ association và thứ tự diagnostics phải có thứ tự xác định (sorted/linked data structures), không phụ thuộc vào thứ tự ngẫu nhiên của JVM.
