Ran command: `Get-ChildItem -Path "D:\_CODE_BANK\Project_\08_Thesis"`
Ran command: `Get-ChildItem -Path "D:\_CODE_BANK\Project_\08_Thesis" -Directory -Hidden -Recurse -Filter ".git" | Select-Object FullName`
Ran command: `$repos = @(
    "D:\_CODE_BANK\Project_\08_Thesis\JaCaMo",
    "D:\_CODE_BANK\Project_\08_Thesis\JaCaMo\temp\use-source",
    "D:\_CODE_BANK\Project_\08_Thesis\jacamo-v100-audit-baseline",
    "D:\_CODE_BANK\Project_\08_Thesis\use",
    "D:\_CODE_BANK\Project_\08_Thesis\use-phase15-candidate-check",
    "D:\_CODE_BANK\Project_\08_Thesis\use-phase15-exact-6cccfc65",
    "D:\_CODE_BANK\Project_\08_Thesis\use-phase15-final-check",
    "D:\_CODE_BANK\Project_\08_Thesis\use-v100-independent-audit"
)

foreach ($r in $repos) {
    Write-Host "=== Repository: $r ==="
    if (Test-Path $r) {
        git -C $r rev-parse --show-toplevel
        Write-Host -NoNewline "Branch: "; git -C $r branch --show-current
        Write-Host -NoNewline "HEAD SHA: "; git -C $r rev-parse HEAD
        Write-Host "Remotes:"
        git -C $r remote -v
        Write-Host "Status summary:"
        git -C $r status --short
        Write-Host "Latest 3 commits:"
        git -C $r log -n 3 --oneline
    } else {
        Write-Host "Path not found!"
    }
    Write-Host ""
}`

I am inspecting the Git repositories across `D:\_CODE_BANK\Project_\08_Thesis` to identify canonical repositories, commit history, branches, and working tree states. Standing by for task completion.

Ran command: `Get-ChildItem -Path "D:\_CODE_BANK\Project_\08_Thesis\JaCaMo" | Select-Object Name, Mode, Length`
Ran command: `Get-ChildItem -Path "D:\_CODE_BANK\Project_\08_Thesis\artical" | Select-Object Name, Mode, Length`
Ran command: `Get-ChildItem -Path "D:\_CODE_BANK\Project_\08_Thesis\use" | Select-Object Name, Mode, Length`
Viewed USE-JaCaMo-v1.0.0-independent-audit.md:1-200
Ran command: `Get-Content -Path "D:\_CODE_BANK\Project_\08_Thesis\USE-JaCaMo-v1.0.0-independent-audit.md" -TotalCount 100`
Ran command: `Get-Content -Path "D:\_CODE_BANK\Project_\08_Thesis\USE-JaCaMo-v1.0.0-independent-audit.md" | Select-Object -Skip 100 -First 150`
Ran command: `git log --graph --oneline -n 25`
Ran command: `git log --oneline --reverse | Select-Object -First 30`
Ran command: `git log --grep="Phase" --oneline`
Ran command: `git log --oneline -n 35 3ef6049a`
Ran command: `git show --stat da3762ef`
Ran command: `git diff --name-status 30d480db HEAD`
Ran command: `git diff --name-status 30d480db HEAD | Where-Object { -not ($_ -match "use-plugin") }`
Ran command: `git diff 30d480db HEAD pom.xml`
Ran command: `Get-Content use-plugin\pom.xml`
Ran command: `Get-Content use-plugin\pom.xml -TotalCount 40`
Ran command: `git grep -n "useplugin.xml" use-core use-gui`
Ran command: `Get-Content use-gui\src\main\java\org\tzi\use\runtime\util\PluginRegistry.java -TotalCount 120`
Ran command: `git grep -n "registerPlugin" use-gui use-core`
Ran command: `Get-Content use-gui\src\main\java\org\tzi\use\runtime\MainPluginRuntime.java -TotalCount 100`
Ran command: `git grep -n "MainPluginRuntime.run" use-gui use-core`
Ran command: `git grep -n "MainPluginRuntime" use-gui use-core`
Ran command: `Get-Content use-gui\src\main\java\org\tzi\use\main\gui\swing\MainSwing.java | Select-Object -Skip 35 -First 45`
Ran command: `git grep -n "pluginDir" use-core`
Ran command: `Get-Content use-core\src\main\java\org\tzi\use\config\Options.java | Select-Object -Skip 430 -First 25`
Ran command: `Get-Content use-plugin\src\main\resources\useplugin.xml`
Ran command: `Get-ChildItem -Path "use-plugin\src\main\java" -Recurse -Filter "*.java" | Select-Object FullName`
Ran command: `Get-ChildItem -Path "use-plugin\src\main\java" -Recurse -Filter "*.java" | ForEach-Object { $_.FullName.Substring((Resolve-Path "use-plugin\src\main\java").Path.Length + 1) }`
Ran command: `Get-ChildItem -Path "use-plugin\src\test\java" -Recurse -Filter "*.java" | ForEach-Object { $_.FullName.Substring((Resolve-Path "use-plugin\src\test\java").Path.Length + 1) }`
Ran command: `Get-ChildItem -Path "D:\_CODE_BANK\Project_\08_Thesis\JaCaMo" -Recurse | Where-Object { -not ($_.FullName -match "\\temp\\|\\\.git\\") } | Select-Object FullName`
Ran command: `Get-ChildItem -Path "use-plugin\Core" -Recurse | Select-Object FullName`
Ran command: `Get-ChildItem -Path "use-plugin\docs" -Recurse | Select-Object FullName`
Ran command: `Get-Content use-plugin\docs\project\02-system-architecture.md -TotalCount 100`
Ran command: `Get-Content use-plugin\docs\project\02-system-architecture.md | Select-Object -Skip 100 -First 100`
Ran command: `Get-Content use-plugin\docs\project\04-jacamo-metamodel-baseline.md`
Ran command: `Get-Content use-plugin\Core\Metamodel\JaCaMo-Metamodel.ecore -TotalCount 25`
Ran command: `Get-Content use-plugin\docs\project\05-metamodel-mapping-contract.md -TotalCount 100`
Ran command: `Get-Content use-plugin\docs\project\05-metamodel-mapping-contract.md | Select-Object -Skip 100 -First 100`
Ran command: `python -c "import json; data=json.load(open('use-plugin/Core/Mapping/jacamo-use-mapping-v1.json', encoding='utf-8')); print('Keys:', list(data.keys())); print('Stats:', data.get('statistics'))"`
Ran command: `Get-Content use-plugin\src\main\java\org\tzi\use\plugins\jacamo\mapping\MappingLoader.java -TotalCount 100`
Ran command: `Get-Content use-plugin\src\main\java\org\tzi\use\plugins\jacamo\mapping\MappingLoader.java | Select-Object -Skip 100 -First 100`
Ran command: `git grep "org.eclipse.emf" use-plugin`
Ran command: `Get-Content D:\_CODE_BANK\Project_\08_Thesis\JaCaMo\audit\ValidateEcore.java -TotalCount 50`
Ran command: `Get-Content use-plugin\src\main\java\org\tzi\use\plugins\jacamo\extraction\StaticProjectImporter.java -TotalCount 100`
Ran command: `Get-Content use-plugin\src\main\java\org\tzi\use\plugins\jacamo\mapping\TransformationPlanner.java -TotalCount 100`
Ran command: `Get-Content use-plugin\src\main\java\org\tzi\use\plugins\jacamo\mapping\StructuralUseGenerator.java -TotalCount 100`
Ran command: `Get-Content use-plugin\src\main\java\org\tzi\use\plugins\jacamo\DefaultJaCaMoFacade.java -TotalCount 100`
Ran command: `Get-Content use-plugin\src\main\java\org\tzi\use\plugins\jacamo\DefaultJaCaMoFacade.java | Select-Object -Skip 230 -First 100`
Ran command: `Get-Content use-plugin\src\main\java\org\tzi\use\plugins\jacamo\materialization\DirectUseBackend.java -TotalCount 100`
Ran command: `Get-Content use-plugin\src\main\java\org\tzi\use\plugins\jacamo\verification\DefaultVerificationService.java -TotalCount 100`
Ran command: `Get-Content use-plugin\src\main\java\org\tzi\use\plugins\jacamo\runtime\RuntimeMutationEngine.java -TotalCount 100`
Ran command: `Get-Content use-plugin\src\main\java\org\tzi\use\plugins\jacamo\verification\RuntimeVerificationEngine.java -TotalCount 100`
Ran command: `Get-Content use-plugin\src\main\java\org\tzi\use\plugins\jacamo\runtime\CartagoRuntimeConnector.java -TotalCount 100`
Ran command: `Get-Content use-plugin\src\main\java\org\tzi\use\plugins\jacamo\runtime\JasonRuntimeConnector.java -TotalCount 70`
Ran command: `Get-Content use-plugin\src\main\java\org\tzi\use\plugins\jacamo\runtime\MoiseRuntimeConnector.java -TotalCount 70`
Ran command: `Get-Content use-plugin\src\test\java\org\tzi\use\plugins\jacamo\runtime\LiveJaCaMoAuctionIntegrationTest.java -TotalCount 100`
Ran command: `Get-Content use-plugin\src\main\java\org\tzi\use\plugins\jacamo\ui\JaCaMoWorkbenchPanel.java -TotalCount 100`
Ran command: `Get-Content use-plugin\src\main\java\org\tzi\use\plugins\jacamo\JaCaMoWorkbenchAction.java -TotalCount 70`
Ran command: `Get-Content use-plugin\src\main\java\org\tzi\use\plugins\jacamo\JaCaMoPlugin.java -TotalCount 70`
Ran command: `Get-Content use-plugin\src\main\java\org\tzi\use\plugins\jacamo\JaCaMoStatusCommand.java -TotalCount 70`
Ran command: `Get-Content use-plugin\src\main\java\org\tzi\use\plugins\jacamo\JaCaMoStatusAction.java -TotalCount 70`
Ran command: `Get-Content use-plugin\src\main\java\org\tzi\use\plugins\jacamo\SkeletonJaCaMoFacade.java -TotalCount 70`
Ran command: `Get-ChildItem -Path "D:\_CODE_BANK\Project_\08_Thesis\use" -Filter "*mvn*" , "*gradle*"`
Ran command: `Get-ChildItem -Path "D:\_CODE_BANK\Project_\08_Thesis\use" | Where-Object { $_.Name -match "mvn|gradle" }`
Ran command: `mvn -version; java -version`

I am checking the installed Maven and Java runtime versions on your system to document exact build prerequisites. Standing by for command completion.

Ran command: `Write-Host "JAVA_HOME: $env:JAVA_HOME"; (Get-Command java).Source`
Ran command: `Get-Content use-plugin\docs\project\15-build-release-operations.md`
Ran command: `Get-Content use-plugin\src\test\java\org\tzi\use\plugins\jacamo\release\ReleasePackageIT.java -TotalCount 100`
Ran command: `Get-ChildItem -Path "use-plugin\target" | Select-Object Name, Length, LastWriteTime`
Ran command: `Get-ChildItem -Path "use-gui\target", "use-core\target", "use-assembly\target" -Filter "*.jar" | Select-Object FullName, Length`
Ran command: `Get-Content use-plugin\docs\project\03-repository-structure.md`
Ran command: `Get-Content use-plugin\docs\project\07-constraint-translation-and-ocl.md`
Ran command: `Get-Content use-plugin\docs\project\10-runtime-adapter.md`
Ran command: `Get-Content use-plugin\docs\project\11-verification-engine.md`
Ran command: `Get-Content use-plugin\docs\project\12-plugin-ui-workflow.md`
Ran command: `Get-Content use-plugin\docs\project\14-auction-case-study.md`
Ran command: `Get-Content use-plugin\docs\project\16-research-evidence-boundaries.md`
Ran command: `Get-Content use-plugin\docs\project\17-end-to-end-acceptance.md`
Ran command: `Get-Content use-plugin\KNOWN-LIMITATIONS.md`
Ran command: `Get-Content use-plugin\compatibility.json`
Ran command: `Get-Content D:\_CODE_BANK\Project_\08_Thesis\V100FacadeAudit.java -TotalCount 70`
Ran command: `Get-ChildItem -Path "use-plugin\src\test\java" -Recurse -Filter "*Test.java" | Select-Object Name`
Ran command: `Get-ChildItem -Path "use-plugin\target\surefire-reports", "use-plugin\target\failsafe-reports" -Filter "*.txt" | ForEach-Object { Get-Content $_.FullName | Select-String "Tests run:" }`
Ran command: `Get-Content use-plugin\docs\agent\task.md -TotalCount 120`
Ran command: `Select-String -Path "use-plugin\docs\agent\task.md" -Pattern "^# Phase"`

# BÁO CÁO TOÀN DIỆN: FULL PROJECT ORIENTATION & USE-JACAMO PLUGIN EXECUTION GUIDE

---

# A. Executive Summary

### 1. Dự án này hiện đang làm gì?
Dự án là đề tài luận văn thạc sĩ nghiên cứu về **Verification & Validation (V&V) cho Multi-Agent Systems (MAS)**, cụ thể là tích hợp framework **JaCaMo** (gồm 3 chiều trừu tượng: **Jason** cho Agent/Mental state, **CArtAgO** cho Environment/Artifacts, và **Moise** cho Organisation/Norms) với môi trường mô hình hóa hình thức **USE (UML-based Specification Environment)**.

Mục tiêu cốt lõi:
1. **Thiết kế & Đóng băng (Design-time / Static)**: Trích xuất một dự án JaCaMo từ file cấu hình `.jcm`, mã nguồn Jason `.asl`, artifact CArtAgO Java, và đặc tả tổ chức Moise `.xml` thành một mô hình trung gian ngữ nghĩa (**Semantic IR**).
2. **Chuyển đổi sang mô hình hình thức USE (Transformation)**: Dựa trên bộ quy tắc ánh xạ chuẩn hóa `jacamo-use-mapping-v1.json` và siêu mô hình `JaCaMo-Metamodel.ecore`, tự động sinh ra mô hình cấu trúc UML (`.use`) và trạng thái khởi tạo (`.cmd` / `MSystemState`).
3. **Đặc tả & Kiểm chứng ràng buộc OCL (Constraint Verification)**: Ánh xạ các điều kiện guard của CArtAgO thành OCL Preconditions, duy trì các bất biến cấu trúc chung (`jacamo-core.ocl`) và các luật nghiệp vụ riêng (`auction.ocl`), sau đó chạy kiểm chứng tự động bằng OCL Evaluator của USE.
4. **Giám sát & Kiểm chứng động thời gian thực (Runtime Verification)**: Kết nối trực tiếp vào runtime in-process của Jason, CArtAgO và Moise để bắt các sự kiện (thêm niềm tin, gọi thao tác, thay đổi thuộc tính quan sát, gia nhập nhóm/vai trò), chuyển dịch trạng thái tương ứng trên **USE Verification Mirror** (`MSystemState`), và kiểm tra OCL Invariants / Pre-Post conditions tức thời mà **không làm gián đoạn hay can thiệp luồng thực thi của JaCaMo** (Observe-only).

### 2. Tình trạng hiện tại của Codebase (Tính đến HEAD `848e18e3`)
- Dự án đã trải qua trọn vẹn **15 Phase phát triển (Phase 0 đến Phase 15)**.
- Đã đóng gói bản release candidate **v1.0.0** (`use-jacamo-plugin-1.0.0.zip` và `use-plugin-1.0.0.jar`).
- Toàn bộ suite gồm **119 unit/component tests** (Surefire) và **3 release integration tests** (Failsafe) trong module `use-plugin` đều đang **PASS 100%** (265 tests nếu chạy toàn bộ reactor USE).
- Đã thực hiện kiểm định độc lập (**Independent Audit** ngày 16/09/2026) ghi nhận kết quả rất vững chắc, đồng thời chỉ ra **2 điểm nghẽn kiến trúc quan trọng (P1 & P2)** cần xử lý trước khi đưa ra bản v1.0.1 hoàn thiện.

---

# B. Repository Map

Vị trí gốc: `D:\_CODE_BANK\Project_\08_Thesis`

Dựa trên kiểm tra Git thực tế (`git rev-parse`, `git remote -v`, `git status`, `git log`), phân bổ các thư mục như sau:

```text
D:\_CODE_BANK\Project_\08_Thesis
├── artical/                                   [Tham khảo] Chứa 5 bài báo PDF nền tảng (Bài báo 1 đến 5)
├── JaCaMo/                                    [Canonical Metamodel Repository]
│   ├── Core/                                  Chứa JaCaMo-Metamodel.ecore (DSML4JaCaMo 2024), ảnh & drawio
│   ├── mapping/                               Chứa jacamo-use-mapping-v1.json, schema, freeze-manifest
│   ├── audit/                                 Script audit Python & Java kiểm tra Ecore/EMF & Mapping
│   └── docs/                                  Kế hoạch & tài liệu metamodel
├── use/                                       [Canonical USE + Thesis Plugin Repository]
│   ├── use-core/                              Upstream USE Core (MModel, MSystemState, OCL Evaluator)
│   ├── use-gui/                               Upstream USE GUI (Swing UI, MainWindow, Diagram views)
│   ├── use-assembly/                          Upstream USE Assembly (Tạo distribution JAR use-gui.jar)
│   └── use-plugin/                            [Thesis Source] Toàn bộ code Plugin JaCaMo của luận văn
├── jacamo-v100-audit-baseline/                [Audit Baseline] Clone độc lập của JaCaMo repo tại commit 849dc33
├── use-v100-independent-audit/                [Audit Clone] Clone sạch độc lập của USE repo tại commit 848e18e3
├── use-phase15-candidate-check/               [Check Workspace] Thư mục kiểm thử candidate Phase 15
├── use-phase15-exact-6cccfc65/                [Check Workspace] Thư mục kiểm thử exact commit 6cccfc65
├── use-phase15-final-check/                   [Check Workspace] Thư mục kiểm thử cuối Phase 15
└── USE-JaCaMo-v1.0.0-independent-audit.md     [Audit Report] Báo cáo audit độc lập v1.0.0
```

### Bảng định danh Canonical Repositories (Dựa trên bằng chứng Git)

| Định danh | Đường dẫn | Git Remote URL | Branch | HEAD SHA | Trạng thái Working Tree | Mục đích |
|---|---|---|---|---|---|---|
| **A. Canonical JaCaMo Repo** | `D:\_CODE_BANK\Project_\08_Thesis\JaCaMo` | `https://github.com/nguyentrungnghia1802/JaCaMo-Metamodel-Research.git` | `main` | `849dc33bf6baddfa3d4f4394364d8b2685bc815e` | Clean | Chứa Ecore DSML4JaCaMo gốc, bộ script audit EMF, và Mapping V1 gốc đã freeze. |
| **B. Canonical USE Repo** | `D:\_CODE_BANK\Project_\08_Thesis\use` | `https://github.com/nguyentrungnghia1802/use.git` | `main` | `848e18e34b048bac1bfcbc491b14200a62abd25d` | Clean | Repository phát triển chính. Chứa toàn bộ USE upstream và module `use-plugin`. |
| **C. Canonical Mapping/Ecore** | `JaCaMo/Core/` & `JaCaMo/mapping/` (và bản copy đồng bộ tại `use/use-plugin/Core/`) | Thuộc 2 repo trên | `main` | Khớp byte 100% | Clean | `JaCaMo-Metamodel.ecore`, `jacamo-use-mapping-v1.json`, `freeze-manifest.json`. |
| **D. Canonical Plugin Source** | `D:\_CODE_BANK\Project_\08_Thesis\use\use-plugin` | Thuộc USE repo | `main` | `848e18e3` | Clean | Toàn bộ 114 class Java, tài nguyên OCL, schemas, tests của plugin. |

> [!NOTE]
> Các thư mục như `use-phase15-*`, `use-v100-independent-audit`, `jacamo-v100-audit-baseline` là các bản checkout phụ dùng trong đợt release audit Phase 15. Bạn **không cần mở hay can thiệp** vào các thư mục này. Chỉ làm việc trực tiếp trên `D:\_CODE_BANK\Project_\08_Thesis\use`.

---

# C. Thesis Architecture

Toàn bộ kiến trúc luận văn tuân thủ nguyên tắc phân tầng đơn hướng (**Unidirectional Layered Architecture**), phân định ranh giới nghiêm ngặt theo tài liệu [02-system-architecture.md](file:///D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/docs/project/02-system-architecture.md):

```text
       +-------------------------------------------------------------------------+
       |                           JaCaMo Source Project                         |
       |               (.jcm project file, .asl, Java Artifact, Moise .xml)      |
       +-------------------------------------------------------------------------+
                                            │
                                            ▼
       +─────────────────────────────────────────────────────────────────────────+
       │ Phase 2 & 3: Project Discovery & Extraction Layer                       │
       │  - JcmProjectLoader, JcmSemanticParser                                  │
       │  - JasonSourceParser, CartagoSourceExtractor, MoiseXmlParser            │
       +─────────────────────────────────────────────────────────────────────────+
                                            │
                                            ▼
       +─────────────────────────────────────────────────────────────────────────+
       │ Phase 2: JaCaMo Semantic Model (Intermediate Representation - IR)       │
       │  - Normalized POJOs: JaCaMoSemanticModel, SemanticElement, SemanticId   │
       │  - Semantic Key: jacamo:<projectId>:<dimension>:<kind>:<owner>:<id>     │
       +─────────────────────────────────────────────────────────────────────────+
                       │                                         │
                       ▼                                         ▼
       +───────────────────────────────+         +───────────────────────────────+
       │ Phase 4: Mapping Engine       │         │ Phase 6: Constraint Extractor │
       │  - MappingLoader (JSON+Ecore) │         │  - ConstraintExtractor        │
       │  - TransformationPlanner      │         │  - ConstraintExpressionParser │
       │  - StructuralUseGenerator     │         │  - TypeEnvironment            │
       +───────────────────────────────+         +───────────────────────────────+
                       │                                         │
                       ▼                                         ▼
       +───────────────────────────────+         +───────────────────────────────+
       │ Generated USE Specification   │         │ Generated & Authored OCL      │
       │  - Classes, Attributes, Assoc │         │  - jacamo-core.ocl (Core)     │
       │  - Projections VP001 - VP003  │         │  - auction.ocl (Case study)   │
       +───────────────────────────────+         │  - CArtAgO Guards -> Preconds │
                       │                         +───────────────────────────────+
                       │                                         │
                       └───────────────────┬─────────────────────┘
                                           ▼
       +─────────────────────────────────────────────────────────────────────────+
       │ Phase 5 & 8: USE In-Memory Verification Engine                          │
       │  - USECompiler: Biên dịch textual model -> in-memory MModel             │
       │  - DirectUseBackend: Khởi tạo MSystem & MSystemState ban đầu            │
       │  - ConstraintRegistry: Đăng ký Invariants & Pre/Post conditions         │
       │  - DefaultVerificationService: Đánh giá OCL Evaluator offline           │
       +─────────────────────────────────────────────────────────────────────────+
                                           ▲
                                           │ (Cập nhật trạng thái thông qua Mutation)
       +───────────────────────────────────┴─────────────────────────────────────+
       │ Phase 9, 10 & 11: Live Runtime Verification Adapter                     │
       │  - Connectors: JasonRuntimeConnector, CartagoRuntimeConnector,          │
       │                MoiseRuntimeConnector, CompositeRuntimeConnector         │
       │  - Queue: OrderedRuntimeEventQueue (Single consumer, backpressure)      │
       │  - Mutation: RuntimeMutationEngine (Thực thi mutations trên USE state)  │
       │  - Engine: RuntimeVerificationEngine (Targeted OCL checking, Pre/Post)  │
       +─────────────────────────────────────────────────────────────────────────+
                                           ▲
                                           │ (Lắng nghe sự kiện in-process)
       +───────────────────────────────────┴─────────────────────────────────────+
       │                           JaCaMo Live Runtime                           │
       │              (Jason TransitionSystem, CArtAgO ICartagoController,       │
       │               Moise Organizational Entity OE)                           │
       +─────────────────────────────────────────────────────────────────────────+
```

### Ranh giới kiến trúc (Architectural Boundaries)
1. **Extraction Layer**: Không bao giờ phụ thuộc vào USE API hay Swing UI. Chỉ đọc file cú pháp và tạo các bản nháp phần tử (`ElementDraft`).
2. **Semantic Model**: Không phụ thuộc USE, không phụ thuộc UI. Dùng định danh ổn định `SemanticId` và truy xuất nguồn gốc `SourceProvenance`.
3. **Mapping Engine**: Đọc siêu mô hình Ecore và mapping schema để lập kế hoạch chuyển đổi (`TransformationPlan`), không trực tiếp parse file `.asl` hay Java.
4. **USE Adapter**: Nhận kế hoạch chuyển đổi, gọi parser chính thức của USE (`USECompiler`) để tạo `MModel` và `MSystemState`.
5. **Runtime Verification Adapter**: Hoạt động ở chế độ quan sát (**Observe-only**), nhận sự kiện runtime, chuyển thành `RuntimeEvent`, đưa qua hàng đợi `OrderedRuntimeEventQueue`, áp dụng lên `MSystemState` qua `RuntimeMutationEngine`, và kích hoạt kiểm chứng OCL mục tiêu (`runTargetedVerification`). Không bao giờ chặn hay can thiệp luồng chạy của JaCaMo.

---

# D. USE Plugin Architecture

### 1. Phân biệt Upstream USE và Thesis Code
- **Upstream USE (University of Bremen)**:
  - Gồm 3 module Maven trong `pom.xml`: `use-core`, `use-gui`, `use-assembly`.
  - Toàn bộ commit gốc từ SVN Uni Bremen kết thúc ở commit `30d480db` (PR #141).
  - Không có bất kỳ dòng code nào trong `use-core`, `use-gui`, `use-assembly` bị sửa đổi cho thesis!
- **Thesis Code**:
  - Được bổ sung vào USE như một module Maven thứ 4 trong `pom.xml`: `<module>use-plugin</module>`.
  - Bắt đầu từ commit `da3762ef` ("First commit").
  - Toàn bộ tính năng luận văn nằm trọn vẹn trong `use-plugin/` và tuân thủ chuẩn Plugin Framework sẵn có của USE.

### 2. USE Plugin Discovery & Lifecycle Flow
Khi USE khởi động, cơ chế nạp plugin diễn ra như sau (chứng minh bằng code thực tế):

```text
Main.main() / MainSwing.main()
  │
  ├─► MainSwing.java (Dòng 54-62):
  │     Kiểm tra Options.doPLUGIN == true.
  │     Lấy đường dẫn plugin: Options.pluginDir = homeDir/lib/plugins
  │     Gọi reflection: MainPluginRuntime.run(pluginDir)
  │
  ├─► MainPluginRuntime.java (Dòng 55-90):
  │     Quét thư mục pluginDir tìm tất cả file *.jar
  │     Lặp qua từng file JAR: gọi PluginRuntime.getInstance().registerPlugin(jarName, url)
  │
  ├─► PluginRegistry.java (Dòng 48-75):
  │     Mở JarFile, tìm file descriptor "useplugin.xml" bên trong JAR
  │     Gọi PluginParser().parsePlugin(inputStream)
  │     Đọc thông tin: plugin id, name, class, commands, actions
  │
  ├─► PluginRuntime.java (Dòng 130-150):
  │     Khởi tạo class plugin: org.tzi.use.plugins.jacamo.JaCaMoPlugin (implements IPlugin)
  │     Đăng ký các Extension Points:
  │       - Shell Command: "jacamo status" -> JaCaMoStatusCommand
  │       - GUI Action 1: Menu "JaCaMo" -> "Status" -> JaCaMoStatusAction
  │       - GUI Action 2: Menu "JaCaMo" -> "Open Workbench..." -> JaCaMoWorkbenchAction
  │
  └─► MainWindow.java (Upstream GUI):
        Gắn menu "JaCaMo" lên thanh Menu Bar của USE GUI.
```

### 3. File Descriptor: `useplugin.xml`
Nằm tại [use-plugin/src/main/resources/useplugin.xml](file:///D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/resources/useplugin.xml):
- **Plugin ID**: `org.tzi.use.plugins.jacamo`
- **Class**: `org.tzi.use.plugins.jacamo.JaCaMoPlugin`
- **Command**: `jacamo status` (thực thi qua [JaCaMoStatusCommand.java](file:///D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/JaCaMoStatusCommand.java))
- **Actions**:
  1. Menu `JaCaMo` -> `Status`: [JaCaMoStatusAction.java](file:///D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/JaCaMoStatusAction.java) (hiển thị popup thông báo trạng thái Phase 1 skeleton).
  2. Menu `JaCaMo` -> `Open Workbench...`: [JaCaMoWorkbenchAction.java](file:///D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/JaCaMoWorkbenchAction.java) (mở cửa sổ chính `JaCaMoWorkbenchPanel`).

---

# E. Complete Thesis File Guide

Dưới đây là tài liệu chi tiết về các nhóm file mã nguồn cốt lõi trong `use-plugin/src/main/java`.

---

### 1. `org.tzi.use.plugins.jacamo` (Facade & Entry Points)

#### [DefaultJaCaMoFacade.java](file:///D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/DefaultJaCaMoFacade.java)
- **Purpose**: Trục điều phối trung tâm (Stateful Application Service / Facade) của plugin. Toàn bộ logic import, mapping, USE model materialization, runtime connection và verification đều tập trung ở đây.
- **Why it exists**: Cô lập toàn bộ business logic phức tạp của plugin ra khỏi giao diện người dùng Swing ([JaCaMoWorkbenchPanel](file:///D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/ui/JaCaMoWorkbenchPanel.java)).
- **Important fields**:
  - `workspace`: Chứa toàn bộ snapshot hiện tại của project (model USE, trace index, constraint registry, report).
  - `runtime`: Quản lý lifecycle kết nối runtime (`RuntimeMirrorService`).
  - `runtimeVerification`: Engine điều phối kiểm chứng runtime (`RuntimeVerificationEngine`).
- **Important methods**:
  - `importProject(Path jcmFile)`:
    - *Input*: Đường dẫn file `.jcm`.
    - *Output*: `ProjectSummary`.
    - *Logic*: Gọi `build(candidate, null)`, nạp model, mapping, sinh model USE và chạy full verification ban đầu.
    - *Failure cases*: Bắn `IllegalArgumentException("IMPORT_JCM_REQUIRED")` nếu không phải `.jcm`, hoặc `IMPORT_FAILED` nếu parse lỗi.
  - `rebuild()`:
    - *Input*: Không (dùng lại `entry` và `userProfile`).
    - *Output*: `ProjectSummary` mới.
    - *Logic*: Tái tạo lại workspace từ đầu.
    - *Finding P1 Warning*: Khi đang có kết nối LIVE, `rebuild()` tạo `workspace` mới nhưng không cập nhật con trỏ `MSystem` của `runtime`, dẫn đến drift!
  - `configureRuntime(RuntimeConnector connector, URI endpoint, int queueCapacity)`:
    - *Input*: Connector triển khai, URI endpoint, kích thước hàng đợi.
    - *Logic*: Khởi tạo `RuntimeMirrorService`, `RuntimeMutationEngine`, gắn `RuntimeVerificationEngine` làm observer.
  - `connectRuntime()` / `disconnectRuntime()` / `reconnectRuntime()`:
    - Điều khiển vòng đời kết nối tới JaCaMo.

#### [JaCaMoPlugin.java](file:///D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/JaCaMoPlugin.java)
- **Purpose**: Triển khai interface `IPlugin` của USE.
- **Why it exists**: Entry point chính để `PluginRuntime` của USE kích hoạt plugin.
- **Important methods**:
  - `getName()`: Trả về `"JaCaMo"`.
  - `run(IPluginRuntime runtime)`: Không cần tác dụng phụ khi khởi động.

#### [JaCaMoWorkbenchAction.java](file:///D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/JaCaMoWorkbenchAction.java)
- **Purpose**: Triển khai `IPluginActionDelegate`.
- **Why it exists**: Hook vào menu bar của USE GUI (`JaCaMo -> Open Workbench...`).
- **Important methods**:
  - `performAction(IPluginAction action)`: Mở hộp thoại `JDialog` chứa `JaCaMoWorkbenchPanel` kích thước 1100x720.

---

### 2. `org.tzi.use.plugins.jacamo.project` & `extraction` (Discovery & Parsers)

#### [JcmProjectLoader.java](file:///D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/project/JcmProjectLoader.java)
- **Purpose**: Đọc và phân tích cây thư mục của một dự án JaCaMo từ file `.jcm`.
- **Logic**: Tìm các file liên quan: Jason `.asl`, CArtAgO Java, Moise `.xml`, kiểm tra SHA-256 từng file, dựng `ProjectGraph`.

#### [StaticProjectImporter.java](file:///D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/extraction/StaticProjectImporter.java)
- **Purpose**: Thực hiện pipeline import tĩnh hoàn chỉnh (Phase 3).
- **Important methods**:
  - `importProject(Path jcmPath)`:
    - *Input*: File `.jcm`.
    - *Output*: `ImportResult` chứa `JaCaMoSemanticModel`.
    - *Logic*: Lần lượt gọi `JcmSemanticParser`, `JasonSourceParser`, `CartagoSourceExtractor`, `MoiseXmlParser`, sau đó gọi `SemanticResolver.resolve(context)` để nối các tham chiếu chéo.
    - *Finding P2 Warning*: `StaticProjectImporter` chỉ gọi `SemanticResolver` thông thường mà không gọi `ExactSemanticResolver` kết hợp với file `binding.json`, dẫn đến lỗi `RESOLUTION_AMBIGUOUS` khi có 2 Artifact cùng tên thao tác!

#### [CartagoSourceExtractor.java](file:///D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/extraction/CartagoSourceExtractor.java)
- **Purpose**: Trích xuất thông tin static từ file Java source của CArtAgO Artifacts.
- **Logic**: Quét các annotation `@OPERATION`, `@GUARD`, các thuộc tính quan sát (`defineObsProperty`), kiểu dữ liệu tham số và kiểu trả về.

#### [JasonSourceParser.java](file:///D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/extraction/JasonSourceParser.java)
- **Purpose**: Parse mã nguồn AgentSpeak (`.asl`).
- **Logic**: Phân tích niềm tin ban đầu (`beliefs`), mục tiêu (`goals`), kế hoạch (`plans`), ngữ cảnh kích hoạt (`triggering events`), và các hành động bên ngoài (`external actions`).

#### [MoiseXmlParser.java](file:///D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/extraction/MoiseXmlParser.java)
- **Purpose**: Parse đặc tả tổ chức Moise từ file `.xml`.
- **Logic**: Trích xuất cấu trúc nhóm (`Group`), sơ đồ (`Scheme`), vai trò (`Role`), mục tiêu tổ chức (`Goal`), nhiệm vụ (`Mission`), và quy phạm (`Norm`).

---

### 3. `org.tzi.use.plugins.jacamo.semantic` (Intermediate Representation - IR)

#### [JaCaMoSemanticModel.java](file:///D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantic/JaCaMoSemanticModel.java)
- **Purpose**: Mô hình ngữ nghĩa chuẩn hóa (Normalized IR) đại diện cho toàn bộ hệ thống MAS, độc lập với USE.
- **Structure**: Lưu trữ `ProjectRoot`, phần tử gốc `MAS`, danh sách `SemanticElement`, chỉ mục nguồn `sourceIndex`, và chẩn đoán `diagnostics`.

#### [SemanticId.java](file:///D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantic/SemanticId.java)
- **Purpose**: Định danh chuẩn hóa toàn cục cho mọi phần tử trong hệ thống MAS.
- **Format**: `jacamo:<projectId>:<dimension>:<kind>:<ownerPath>:<localId>`.
- **Ví dụ**: `jacamo:auction:agent:Agent:MAS:auctioneer`.

---

### 4. `org.tzi.use.plugins.jacamo.mapping` & `materialization` (Transformation & USE Generation)

#### [MappingLoader.java](file:///D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/mapping/MappingLoader.java)
- **Purpose**: Nạp và kiểm định hợp đồng ánh xạ `jacamo-use-mapping-v1.json`.
- **Logic**:
  1. Kiểm tra JSON Schema (Draft 2020-12) qua thư viện NetworkNT.
  2. Đọc file `JaCaMo-Metamodel.ecore` bằng DOM parser XML (`DocumentBuilderFactory`), trích xuất toàn bộ 37 class, 67 attribute, 63 reference, 14 inheritance edges.
  3. Kiểm tra mã băm SHA-256 của Ecore và Mapping so với `freeze-manifest.json`.
  4. Đảm bảo mapping bao phủ 100% các phần tử trong Ecore và tên target không bị xung đột.
  5. Trả về đối tượng `MappingModel`.

#### [TransformationPlanner.java](file:///D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/mapping/TransformationPlanner.java)
- **Purpose**: Lập kế hoạch chuyển đổi mô hình từ Semantic IR sang USE Specifications.
- **Logic**:
  - Chuyển 37 EClass thành 37 TargetClassSpec.
  - Áp dụng các phép chiếu kiểm chứng:
    - **VP001**: Chiếu Artifact cụ thể thành lớp con trong USE (VD: `AuctionArtifact < Artifact`).
    - **VP002**: Chiếu thuộc tính quan sát thành USE Attribute (VD: `open : Boolean`).
    - **VP003**: Chiếu CArtAgO operation thành USE Operation (VD: `placeBid(amount : Integer)`).

#### [StructuralUseGenerator.java](file:///D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/mapping/StructuralUseGenerator.java)
- **Purpose**: Sinh chuỗi văn bản đặc tả mô hình `.use` hợp lệ cho trình biên dịch USE.

#### [DirectUseBackend.java](file:///D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/materialization/DirectUseBackend.java)
- **Purpose**: Cầu nối trực tiếp với API của upstream USE.
- **Logic**:
  - Truyền chuỗi `.use` vào `USECompiler.compileSpecification(..., new ModelFactory())` để sinh ra `MModel`.
  - Khởi tạo hệ thống `MSystem(model)`.
  - Gọi `system.state().createObject(cls, name)` và `setAttributeValue()` để khởi tạo các đối tượng và thuộc tính ban đầu.
  - Gọi `system.state().createLink(association, ...)` để tạo các liên kết.
  - Gọi `system.state().checkStructure(...)` và `check(...)` để xác thực tính đúng đắn cấu trúc ban đầu.

---

### 5. `org.tzi.use.plugins.jacamo.verification` & `ocl` (Verification Engine)

#### [DefaultVerificationService.java](file:///D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/verification/DefaultVerificationService.java)
- **Purpose**: Dịch vụ kiểm chứng OCL offline độc lập.
- **Important methods**:
  - `runFullVerification(MSystem system, ConstraintRegistry registry, TraceIndex trace)`: Kiểm tra cấu trúc (`checkStructure`), lặp qua mọi `MClassInvariant`, đánh giá qua `org.tzi.use.uml.ocl.expr.Evaluator`, trả về `VerificationReport`.
  - `runTargetedVerification(...)`: Chỉ đánh giá tập bất biến bị ảnh hưởng bởi thay đổi trạng thái (tối ưu hiệu năng runtime).
  - `beginOperation(...)` / `completeOperation(...)`: Snapshot pre-state, kiểm tra precondition khi gọi thao tác, và kiểm tra postcondition (giữ nguyên `@pre`) khi kết thúc thao tác.

#### [RuntimeVerificationEngine.java](file:///D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/verification/RuntimeVerificationEngine.java)
- **Purpose**: Điều phối viên kiểm chứng OCL thời gian thực theo hướng sự kiện (Event-driven OCL Coordinator).
- **Role**: Triển khai `RuntimeEventObserver`. Khi nhận thông báo state thay đổi từ `RuntimeMutationEngine`, tra cứu `ConstraintDependencyIndex` để tìm các invariant bị ảnh hưởng và chạy `runTargetedVerification`.

---

### 6. `org.tzi.use.plugins.jacamo.runtime` (Connectors & Mutation Engine)

#### [RuntimeMutationEngine.java](file:///D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/runtime/RuntimeMutationEngine.java)
- **Purpose**: **Thành phần DUY NHẤT** được phép sửa đổi trạng thái `MSystemState` của USE ở thời gian chạy.
- **Supported mutations**: `CREATE_OBJECT`, `DESTROY_OBJECT`, `SET_ATTRIBUTE`, `INSERT_LINK`, `DELETE_LINK`, `OP_ENTER`, `OP_EXIT`, `OP_FAIL`, `OBS_PROPERTY_ADDED`, `OBS_PROPERTY_CHANGED`, `OBS_PROPERTY_REMOVED`.

#### [CartagoRuntimeConnector.java](file:///D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/runtime/CartagoRuntimeConnector.java)
- **Purpose**: Kết nối tới CArtAgO 3.1 runtime in-process.
- **Mechanism**: Gắn `CartagoLoggerAdapter` vào workspace để nhận callback khi operation bắt đầu/kết thúc, hoặc khi observable property thay đổi.

#### [JasonRuntimeConnector.java](file:///D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/runtime/JasonRuntimeConnector.java)
- **Purpose**: Kết nối tới Jason 3.3.0 runtime in-process.
- **Mechanism**: Gắn `CircumstanceListener` và `GoalListener` vào `TransitionSystem` của agent; dùng `JasonMonitorAgArch` để theo dõi thực thi hành động (`act`).

#### [MoiseRuntimeConnector.java](file:///D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/runtime/MoiseRuntimeConnector.java)
- **Purpose**: Kết nối tới Moise 1.1 runtime in-process.
- **Mechanism**: Do Moise OE không có listener event công khai, connector dùng cơ chế lấy snapshot định kỳ và tính toán sai khác (`pollChanges`) trên `moise.oe.OE`.

---

# F. Ecore Explanation

### 1. Thông tin Metamodel Canonical
- **File path**:
  - Gốc: `JaCaMo/Core/JaCaMo-Metamodel.ecore`
  - Đóng gói trong plugin: [use-plugin/Core/Metamodel/JaCaMo-Metamodel.ecore](file:///D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/Core/Metamodel/JaCaMo-Metamodel.ecore)
- **Package Name**: `dSML4JaCaMo`
- **nsURI**: `urn:dsml4jacamo:2024:reconstructed`
- **nsPrefix**: `dSML4JaCaMo`
- **Provenance**: Tái tạo bảo thủ từ bài báo "DSML4JaCaMo" (2024, Hình 1 trang 3, DOI: 10.15439/2024F6157).
- **Cấu trúc số lượng**: 37 EClass, 67 EAttribute, 63 EReference, 14 quan hệ kế thừa (generalizations), 3 thuộc tính chưa rõ kiểu được lưu trong annotation (`ObsProperty.initialValue`, `AbsOperation.paramName`, `TriggeringEvent.addAndDel`).

### 2. Cách thức Plugin sử dụng Ecore
```text
JaCaMo-Metamodel.ecore
       │
       ▼ (MappingLoader.java sử dụng javax.xml.parsers.DocumentBuilderFactory)
Đọc cây DOM XML
       │
       ▼
Trích xuất EcoreKeys (37 Classes, 67 Attributes, 63 References, 14 Inheritances)
       │
       ▼
Đối chiếu 1-1 với jacamo-use-mapping-v1.json
```
- **Plugin có load Ecore ở runtime không?**
  Không. Plugin chỉ đọc Ecore ở pha khởi tạo cấu hình (Design-time / Mapping Load) để đối soát tính đầy đủ 100% của bộ quy tắc mapping và xác thực mã băm SHA-256 so với `freeze-manifest.json`.
- **Có EMF generated Java classes không?**
  **HOÀN TOÀN KHÔNG**. Không có các class dạng `DSML4JaCaMoPackage`, `DSML4JaCaMoFactory`, hay `impl.*`.
- **Dùng Dynamic EObject hay Generated EObject?**
  **KHÔNG DÙNG CẢ HAI**. Trong plugin không có dependency của Eclipse EMF runtime (`org.eclipse.emf.*`).
- **Instance model hiện tại tồn tại ở đâu?**
  Instance model được trích xuất thẳng từ file nguồn JaCaMo thành các đối tượng Java thuần (POJO) trong `JaCaMoSemanticModel` (`SemanticElement`), sau đó được ánh xạ trực tiếp sang đối tượng của USE (`MModel`, `MObject`, `MSystemState`).

---

# G. Mapping JSON Explanation

### 1. Cấu trúc Schema `jacamo-use-mapping-v1.json`
Nằm tại [use-plugin/Core/Mapping/jacamo-use-mapping-v1.json](file:///D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/Core/Mapping/jacamo-use-mapping-v1.json). Các trường chính gồm:
- `schemaVersion`: `"1.0.0"`
- `mappingId`: `"jacamo-to-use-core-v1"`
- `sourceMetamodel`: Khai báo nsURI `urn:dsml4jacamo:2024:reconstructed` và SHA-256 của Ecore.
- `targetUSE`: Khai báo phiên bản USE mục tiêu (`7.5.0`).
- `policies`: Các chính sách đặt tên (qualified source keys `dSML4JaCaMo::<Class>#<feature>`, target association naming `<Source>_<feature>_<Target>`, xử lý containment).
- `classMappings` (37 mục): Ánh xạ 1-1 từng EClass sang USE Class.
- `attributeMappings` (67 mục): Ánh xạ từng EAttribute sang USE Attribute (EString -> String, EInt -> Integer, EBoolean -> Boolean).
- `inheritanceMappings` (14 mục): Ánh xạ từng quan hệ kế thừa sang USE Generalization (`<`).
- `referenceMappings` (63 mục): Ánh xạ từng EReference sang `USE_ASSOCIATION` hoặc `USE_COMPOSITION`.
- `verificationProjections` (7 mục: VP001 đến VP007): Các quy tắc chiếu phục vụ kiểm chứng.
- `runtimeMaterialization`: Chính sách ánh xạ trạng thái động sang `MSystemState`.
- `unresolvedSourceFeatures` (3 mục): Ghi nhận 3 thuộc tính chưa xác định kiểu từ bài báo gốc.
- `statistics`: Tổng hợp số lượng kiểm toán.

### 2. Bảng trạng thái triển khai trong code

| Nhóm Mapping | Số lượng | Trạng thái trong Code | Nơi xử lý (Class / File) |
|---|---|---|---|
| `classMappings` | 37 | **IMPLEMENTED** | `MappingLoader.java`, `TransformationPlanner.java` (tạo `TargetClassSpec`) |
| `attributeMappings` | 67 | **IMPLEMENTED** | `TransformationPlanner.java` (tạo `TargetAttributeSpec`) |
| `inheritanceMappings` | 14 | **IMPLEMENTED** | `TransformationPlanner.java` (gán `superclasses`) |
| `referenceMappings` | 63 | **IMPLEMENTED** | `TransformationPlanner.java` (tạo `TargetAssociationSpec`) |
| **VP001** (Concrete Artifact Class) | Quy tắc chiếu | **IMPLEMENTED** | `TransformationPlanner.java:34-45` (tạo lớp con của `Artifact`) |
| **VP002** (ObsProperty State) | Quy tắc chiếu | **IMPLEMENTED** | `TransformationPlanner.java:54-62` (tạo attribute trên Artifact con) |
| **VP003** (CArtAgO Operation) | Quy tắc chiếu | **IMPLEMENTED** | `TransformationPlanner.java:63-71` (tạo operation trên Artifact con) |
| **VP004** (Action -> Operation Trace) | Quy tắc chiếu | **IMPLEMENTED** | `TraceBuilder.java` |
| **VP005** (ObsProperty -> Belief Trace) | Quy tắc chiếu | **IMPLEMENTED** | `TraceBuilder.java` |
| **VP006** (OGoal -> Goal Trace) | Quy tắc chiếu | **IMPLEMENTED** | `TraceBuilder.java` |
| **VP007** (Norm Preservation) | Quy tắc chiếu | **PARTIALLY IMPLEMENTED** | Lưu cấu trúc Norm trong mô hình USE; **chưa** tự động biên dịch deontic logic thành OCL |

---

# H. JaCaMo → USE Data Flow

Bảng tổng hợp luồng chuyển đổi dữ liệu từ file nguồn tới mô hình hình thức:

| Bước | Thành phần | Đã triển khai? | Class / File thực hiện | Dữ liệu Đầu vào | Dữ liệu Đầu ra | Khoảng trống / Giới hạn |
|---|---|---|---|---|---|---|
| 1. Discovery | Đọc cấu trúc dự án | **IMPLEMENTED** | `JcmProjectLoader.java` | File `.jcm` và đường dẫn gốc | `ProjectGraph` | Chỉ hỗ trợ file cục bộ, không kéo repo từ xa |
| 2. Parsing | Parse cú pháp các chiều | **IMPLEMENTED** | `JcmSemanticParser`, `JasonSourceParser`, `CartagoSourceExtractor`, `MoiseXmlParser` | `.jcm`, `.asl`, `.java`, `.xml` | Danh sách `ElementDraft` | Chỉ hỗ trợ subset cú pháp định sẵn; không parse body Java phức tạp |
| 3. Resolution | Nối tham chiếu chéo | **PARTIAL** | `SemanticResolver.java` | `ElementDraft` | `JaCaMoSemanticModel` | **Finding P2**: Chưa tích hợp `ExactSemanticResolver` với file `binding.json` vào luồng import chính |
| 4. Planning | Lập kế hoạch mô hình USE | **IMPLEMENTED** | `TransformationPlanner.java` | `JaCaMoSemanticModel`, `MappingModel` | `TransformationPlan` | Chỉ hỗ trợ chiếu Artifact đơn giản (VP001-VP003) |
| 5. Textual Gen | Sinh mã đặc tả `.use` / `.cmd` | **IMPLEMENTED** | `StructuralUseGenerator.java`, `TextBackend.java` | `TransformationPlan`, `InstancePlan` | Chuỗi mã nguồn `.use` và `.cmd` | Đã hoàn thiện, sinh mã chuẩn xác |
| 6. Materialize | Nạp vào USE in-memory | **IMPLEMENTED** | `DirectUseBackend.java` | Chuỗi `.use` và `InstancePlan` | `MModel`, `MSystemState` | Cần `USECompiler` của USE host |
| 7. Traceability | Xây dựng chỉ mục truy vết | **IMPLEMENTED** | `TraceBuilder.java` | Semantic Model + Instance Plan | `TraceIndex` | Khớp 100% các đối tượng được sinh ra |

---

# I. OCL Architecture

### 1. Nguồn gốc của OCL trong hệ thống
OCL trong dự án đến từ 4 nguồn rõ ràng:
1. **Translated OCL**:
   - Được trích xuất tự động từ mã nguồn JaCaMo bởi [ConstraintExtractor.java](file:///D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/constraint/ConstraintExtractor.java).
   - Hiện tại: Tự động dịch các method `@GUARD` của CArtAgO có biểu thức so sánh số học đơn giản thành Precondition trên Operation của USE (Ví dụ: `canBid(int amount) { return amount >= 0; }` thành precondition của `placeBid`).
   - Ngữ cảnh Jason (`context`) hiện được đánh dấu là `UNSUPPORTED` nếu chưa giải quyết được binding thuộc tính, **không tự ý đoán mò**.
2. **JaCaMo Core OCL (`jacamo-core.ocl`)**:
   - Nằm tại [use-plugin/src/main/resources/org/tzi/use/plugins/jacamo/ocl/jacamo-core.ocl](file:///D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/resources/org/tzi/use/plugins/jacamo/ocl/jacamo-core.ocl).
   - Chứa các bất biến cấu trúc dùng chung: tính nhất quán giữa External Action và Operation, kiểm tra Role players, v.v.
3. **Case-specific OCL (`<project>.ocl`)**:
   - Ví dụ: `src/test/resources/auction/verification/auction.ocl`.
   - Chứa các ràng buộc nghiệp vụ riêng của bài toán đấu giá (VD: `PositiveBidAmount`, `AuctionOpenForBid`, `AuctionInitiallyOpen`).
4. **User-loaded OCL Profile**:
   - Nạp qua nút `Load OCL...` trên giao diện người dùng.

### 2. Chu trình xử lý OCL
```text
OCL Sources (Core + Case + Translated + User)
      │
      ▼
OclProfileLoader.java (Kiểm tra SHA-256, nạp text)
      │
      ▼
OclGenerator.java (Ghép vào cuối khối 'constraints' trong file .use)
      │
      ▼
USECompiler.compileSpecification(...) (Parse cú pháp OCL, gán vào MModel)
      │
      ▼
ConstraintRegistry.load(...) (Đăng ký metadata, phân loại INV / PRE / POST)
      │
      ▼
DefaultVerificationService / RuntimeVerificationEngine (Đánh giá bằng OCL Evaluator)
      │
      ▼
VerificationReport (Xuất kết quả PASS / FAIL / ERROR / SKIPPED kèm Trace)
```

---

# J. Runtime Architecture

### 1. Phân loại mức độ sẵn sàng của các thành phần Runtime

| Thành phần | Phân loại | Bằng chứng Code thực tế | Ghi chú |
|---|---|---|---|
| **Runtime Event Model** | **IMPLEMENTED** | `RuntimeEvent.java`, `runtime-event-v1.schema.json` | Chuẩn hóa sự kiện với sequence, timestamp, payload |
| **Runtime Event Queue** | **IMPLEMENTED** | `OrderedRuntimeEventQueue.java` | Hàng đợi đơn tiêu thụ, kiểm soát backpressure, zero-drop |
| **Jason Listener** | **IMPLEMENTED** | `JasonRuntimeConnector.java`, `JasonMonitorAgArch.java` | Bắt belief, goal, và hành động qua `CircumstanceListener` |
| **CArtAgO Listener** | **IMPLEMENTED** | `CartagoRuntimeConnector.java`, `WorkspaceLogger` | Bắt operation enter/exit và observable property thay đổi |
| **Moise Poller** | **IMPLEMENTED** | `MoiseRuntimeConnector.java` | Lấy snapshot `moise.oe.OE` định kỳ và tính toán diff |
| **State Synchronizer** | **IMPLEMENTED** | `RuntimeMirrorService.java` | Quản lý snapshot ban đầu, tái đồng bộ (resync), kiểm tra drift |
| **USE Mutation Engine** | **IMPLEMENTED** | `RuntimeMutationEngine.java` | Thực thi thay đổi giá trị thuộc tính, thêm/xóa link trên `MSystemState` |
| **Runtime Verification** | **IMPLEMENTED** | `RuntimeVerificationEngine.java` | Kiểm chứng OCL mục tiêu dựa trên `ConstraintDependencyIndex` |
| **Full Lifecycle Rebuild**| **SKELETON / BUGGY** | `DefaultJaCaMoFacade.java:81-119` | **Finding P1**: Khi gọi `rebuild()`, runtime vẫn gắn với workspace cũ |
| **Interactive UI Connector**| **DECLARED IN DESIGN**| `JaCaMoWorkbenchPanel.java:134` | UI có nút kết nối nhưng thiếu controller cấu hình tham số live ngoài đời |

### 2. Chi tiết 2 phát hiện kiểm toán quan trọng (P1 & P2)
- **P1 - Workspace Rebuild Detachment**:
  - *Vị trí*: [DefaultJaCaMoFacade.java](file:///D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/DefaultJaCaMoFacade.java#L81-L125).
  - *Hiện tượng*: Khi đang ở trạng thái LIVE, nếu người dùng bấm `Rebuild` hoặc nạp OCL mới, facade tạo một `Workspace` mới (chứa `MSystem` mới). Tuy nhiên, `this.runtime` và `this.runtimeVerification` vẫn giữ con trỏ tới `MSystem` cũ. Các sự kiện runtime tiếp theo vẫn cập nhật vào `MSystem` cũ, trong khi lệnh `runFullVerification()` kiểm tra trên `MSystem` mới. Kết quả: Báo cáo kiểm chứng tĩnh bỏ lọt các vi phạm runtime!
- **P2 - Binding Resolution Pipeline Bypass**:
  - *Vị trí*: [DefaultJaCaMoFacade.java:240](file:///D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/DefaultJaCaMoFacade.java#L240) và [StaticProjectImporter.java:27](file:///D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/extraction/StaticProjectImporter.java#L27).
  - *Hiện tượng*: Module `resolution` có class `ExactSemanticResolver` hoạt động hoàn hảo trong unit test với file `binding.json`. Tuy nhiên, trong luồng import chính của `DefaultJaCaMoFacade`, code lại gọi thẳng `StaticProjectImporter` (vốn chỉ dùng `SemanticResolver` thường). Khi gặp 2 artifact có cùng tên thao tác, import bị văng lỗi `IMPORT_FAILED: RESOLUTION_AMBIGUOUS` mà không nạp file `binding.json` do người dùng cấu hình.

---

# K. Tests

Bảng ma trận kiểm thử chi tiết của `use-plugin` (122 tests):

| Test Class | Đối tượng kiểm thử | Dữ liệu đầu vào | Kết quả mong đợi | Loại Test | Trạng thái |
|---|---|---|---|---|---|
| `CompatibilityManifestTest` | Tính tương thích phiên bản | `compatibility.json` | Khớp phiên bản pin JDK, Maven, USE, JaCaMo | Unit | **PASS** (1/1) |
| `ConstraintOclTest` | Dịch ràng buộc & OCL AST | Mã nguồn Java/ASL & AST | Trích xuất đúng Guard precondition, giữ `@pre` | Unit | **PASS** (7/7) |
| `DefaultJaCaMoFacadeTest` | Vòng đời Facade & import | Fixture Auction | Import thành công, xuất báo cáo, quản lý trạng thái | Component | **PASS** (11/11) |
| `EvidenceNormalizerTest` | Chuẩn hóa ký tự xuống dòng | Văn bản CRLF/LF | Đưa về LF đồng nhất để kiểm tra SHA-256 | Unit | **PASS** (2/2) |
| `EvidenceSourceCommitTest` | Đối soát mã băm commit | Workspace Git | Các file fixture khớp byte với commit Git | Unit | **PASS** (1/1) |
| `StaticProjectImporterTest` | Trích xuất dự án JaCaMo | File `.jcm` và fixture | Trích xuất đúng các chiều Agent, Env, Org | Component | **PASS** (8/8) |
| `GoldenPipelineTest` | Pipeline golden tái lập | Auction test resources | 14 artifact sinh ra khớp mã SHA-256 golden | Integration | **PASS** (1/1) |
| `JaCaMoPluginTest` | Nạp plugin vào USE | Descriptor `useplugin.xml` | Khởi tạo plugin, đăng ký command/action | Unit | **PASS** (4/4) |
| `MappingTransformationTest` | Đọc mapping & sinh mô hình | Mapping V1 & Ecore | Ánh xạ đủ 37 class, sinh cấu trúc `.use` hợp lệ | Unit | **PASS** (10/10) |
| `InstanceMaterializationTest`| Tạo đối tượng USE in-memory | Instance plan | Tạo thành công đối tượng và link trong `MSystem` | Unit | **PASS** (2/2) |
| `JcmProjectLoaderTest` | Quét file `.jcm` & includes | Thư mục `jcm/minimal` | Nhận diện đúng cây đồ thị project | Unit | **PASS** (11/11) |
| `ProjectModelTest` | Cấu trúc Project Graph | Model project | Duyệt đúng các quan hệ phụ thuộc | Unit | **PASS** (3/3) |
| `ReleasePackageContractTest` | Hợp đồng gói release ZIP | `release-manifest.json` | Cấu trúc file trong zip khớp 100% manifest | Contract | **PASS** (1/1) |
| `AuctionSourceRuntimeTest` | Khởi tạo in-process runtime | Fixture Auction | Nạp thành công Jason Agent & CArtAgO env | Integration | **PASS** (1/1) |
| `CartagoRuntimeConnectorTest`| Bắt sự kiện CArtAgO | CArtAgO workspace | Bắt đúng event khi gọi op và đổi obs prop | Integration | **PASS** (1/1) |
| `JasonRuntimeConnectorTest` | Bắt sự kiện Jason | TransitionSystem Jason | Bắt đúng event khi thêm belief/thực thi plan | Integration | **PASS** (1/1) |
| `MoiseRuntimeConnectorTest` | Bắt sự kiện Moise | `moise.oe.OE` | Bắt đúng event khi phân vai và gia nhập nhóm | Integration | **PASS** (1/1) |
| `CompositeRuntimeConnectorTest`| Ghép 3 connector | Snapshot 3 chiều | Đánh số thứ tự sự kiện đơn điệu (monotonic) | Integration | **PASS** (1/1) |
| `LiveJaCaMoAuctionIntegrationTest`| Kiểm chứng Auction thời gian thực | Jason + CArtAgO + Moise | Phát hiện vi phạm OCL khi đấu giá đóng, resync 0 drift | E2E Live | **PASS** (1/1) |
| `RuntimeFoundationTest` | Hàng đợi & Mutation Engine | Chuỗi sự kiện mô phỏng | Thực thi đúng các phép biến đổi trên `MSystemState` | Component | **PASS** (14/14) |
| `SemanticIdTest` | Định dạng định danh ID | Chuỗi ID ngữ nghĩa | Parse và encode chuẩn xác | Unit | **PASS** (4/4) |
| `SemanticModelTest` | Mô hình Semantic IR | Các draft phần tử | Đóng băng thành công IR bất biến | Unit | **PASS** (3/3) |
| `TraceBindingTest` | Truy vết nguồn và đích | Semantic vs USE targets | Tra cứu 2 chiều chính xác | Unit | **PASS** (4/4) |
| `JaCaMoWorkbenchPanelTest` | Giao diện Swing Workbench | Mock/Real Facade | Hiển thị đủ 6 tab, cập nhật trạng thái UI | UI Unit | **PASS** (7/7) |
| `OfflineVerificationServiceTest`| Kiểm chứng OCL offline | Model & Invariants | Đánh giá chính xác PASS/FAIL/ERROR | Component | **PASS** (4/4) |
| `VerificationSemanticLayerTest`| Lớp ngữ nghĩa kiểm chứng | Baseline transformation | Bổ sung các cấu trúc phục vụ kiểm chứng | Unit | **PASS** (1/1) |
| `RuntimeVerificationEngineTest`| Kiểm chứng động theo sự kiện | Sự kiện runtime & OCL | Bắt đúng vi phạm khi thuộc tính bị sửa đổi | Component | **PASS** (14/14) |
| `ReleasePackageIT` | Kiểm thử gói release cài đặt | `use-jacamo-plugin-1.0.0.zip`| Nạp plugin trong JVM con cô lập không có Maven classpath | Release IT | **PASS** (3/3) |

---

# L. Phase 1–15 Reconstruction

Dựa trên lịch sử Git commit thực tế và tài liệu [task.md](file:///D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/docs/agent/task.md):

- **Phase 0: Baseline Freeze** (`da3762ef`, `849dc33`)
  - *Mục tiêu*: Khóa siêu mô hình `JaCaMo-Metamodel.ecore` (37 class, 67 attr, 63 ref, 14 inh) và hợp đồng `jacamo-use-mapping-v1.json`.
  - *Kết quả*: Kiểm toán EMF vượt qua, tạo `freeze-manifest.json`.
- **Phase 1: USE Plugin Skeleton** (`3ef6049a`, `ad4c82aa`)
  - *Mục tiêu*: Thiết lập module `use-plugin`, tạo `useplugin.xml`, menu và command `jacamo status`.
  - *Kết quả*: USE nhận diện và nạp được plugin JAR.
- **Phase 2: Project Discovery & Semantic IR** (`d95ee4da`)
  - *Mục tiêu*: Xây dựng `JcmProjectLoader` quét file `.jcm`, thiết lập mô hình `JaCaMoSemanticModel` và định danh `SemanticId`.
- **Phase 3: Dimension Parsers & Resolver** (`33fe776f`)
  - *Mục tiêu*: Xây dựng các parser cho Jason, CArtAgO, Moise và bộ giải quyết tham chiếu `SemanticResolver`.
- **Phase 4: Mapping Engine & USE Transformation** (`380698bd`)
  - *Mục tiêu*: Xây dựng `MappingLoader`, `TransformationPlanner`, triển khai các phép chiếu VP001-VP003, sinh mã `.use`.
- **Phase 5: Instance Materialization** (`41a3b2f4`)
  - *Mục tiêu*: Xây dựng `DirectUseBackend` gọi `USECompiler` để tạo `MModel`, `MSystem`, `MSystemState` và sinh mã `.cmd`.
- **Phase 6: Constraint Extraction & OCL** (`2fdb611e`)
  - *Mục tiêu*: Dịch CArtAgO guards thành OCL Preconditions, nạp `jacamo-core.ocl` và case study OCL.
- **Phase 7: Traceability & Binding Resolver** (`922f5c2b`)
  - *Mục tiêu*: Xây dựng `TraceBuilder` lưu vết 2 chiều nguồn-đích; xây dựng `ExactSemanticResolver` và schema `binding-v1.schema.json`.
- **Phase 8: Verification Service & Offline Reporting** (`b9ce2aa3`)
  - *Mục tiêu*: Xây dựng `DefaultVerificationService`, đánh giá invariant/pre/post qua OCL Evaluator, xuất báo cáo JSON/Markdown.
- **Phase 9: Runtime Adapter Foundation** (`a6a9b9e0`)
  - *Mục tiêu*: Thiết lập `RuntimeEvent`, hàng đợi `OrderedRuntimeEventQueue`, và engine đột biến `RuntimeMutationEngine`.
- **Phase 10: Live Runtime Connectors** (`beab1361`)
  - *Mục tiêu*: Triển khai `JasonRuntimeConnector`, `CartagoRuntimeConnector`, `MoiseRuntimeConnector` in-process.
- **Phase 11: Runtime Verification** (`d3dae89b`)
  - *Mục tiêu*: Triển khai `RuntimeVerificationEngine`, bắt sự kiện runtime, cập nhật state và kiểm chứng OCL tức thời.
- **Phase 12: Plugin UI Completion** (`dc7bfdfe`)
  - *Mục tiêu*: Xây dựng giao diện Swing [JaCaMoWorkbenchPanel.java](file:///D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/ui/JaCaMoWorkbenchPanel.java) với 6 tabs.
- **Phase 13: Hardening and Quality** (`00140fd0`)
  - *Mục tiêu*: Bổ sung kiểm thử hồi quy, chuẩn hóa xử lý lỗi, kiểm soát backpressure, củng cố ranh giới hợp đồng.
- **Phase 14: Auction Evidence & Final Verification** (`19846fa5`)
  - *Mục tiêu*: Chạy thực nghiệm ca đấu giá Auction E2E, xuất 14 file bằng chứng khoa học có kiểm soát mã băm commit.
- **Phase 15: Release Packaging & Independent Audit** (`a4951e91`, `848e18e3`)
  - *Mục tiêu*: Đóng gói `use-jacamo-plugin-1.0.0.zip`, chạy integration test trên JVM độc lập, thực hiện audit độc lập.

---

# M. Build Guide (Hướng dẫn Build từ trạng thái sạch)

### 1. Điều kiện tiên quyết (Prerequisites)
- **Hệ điều hành**: Windows 10/11 64-bit.
- **JDK**: **Oracle JDK 21 hoặc OpenJDK 21** (Dự án đã pin và kiểm định với JDK 21.0.5).
  > [!WARNING]
  > **LỖI THƯỜNG GẶP TRÊN MÁY BẠN**: Lệnh `java -version` mặc định trên máy bạn hiện đang trỏ về **Java 1.8.0_501** (`C:\Program Files (x86)\Common Files\Oracle\Java\java8path\java.exe`), trong khi `JAVA_HOME` trỏ về `D:\DevTools\Java\jdk-21.0.5`. Maven tự động nhận đúng Java 21 qua `JAVA_HOME`, nhưng khi chạy lệnh `java` trực tiếp, bạn **BẮT BUỘC** phải dùng đường dẫn Java 21!
- **Maven**: Apache Maven 3.9.x (Đã có sẵn tại `D:\DevTools\apache-maven-3.9.9-bin\apache-maven-3.9.9`).

### 2. Mở dự án trong IDE
- Mở thư mục: `D:\_CODE_BANK\Project_\08_Thesis\use` bằng **IntelliJ IDEA** hoặc **VS Code**.
- Đảm bảo cấu hình **Project SDK = JDK 21**.

### 3. Các lệnh Build thực tế từ Terminal (PowerShell)

Mở PowerShell tại thư mục `D:\_CODE_BANK\Project_\08_Thesis\use`:

```powershell
# Đảm bảo PATH ưu tiên Java 21 của bạn
$env:JAVA_HOME = "D:\DevTools\Java\jdk-21.0.5"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"

# 1. Build toàn bộ dự án USE và Plugin (chạy tất cả test)
mvn clean verify

# 2. Hoặc nếu chỉ muốn build nhanh riêng module use-plugin:
mvn clean package -pl use-plugin -DskipTests
```

Sau khi build thành công:
- File JAR của Plugin: `D:\_CODE_BANK\Project_\08_Thesis\use\use-plugin\target\use-plugin-1.0.0.jar`
- File ZIP Release: `D:\_CODE_BANK\Project_\08_Thesis\use\use-plugin\target\use-jacamo-plugin-1.0.0.zip`
- File JAR USE Host: `D:\_CODE_BANK\Project_\08_Thesis\use\use-gui\target\use-gui.jar`

---

# N. Run Plugin Guide (Hướng dẫn chạy USE + Plugin từ đầu)

### 1. Chuẩn bị môi trường nạp Plugin
Theo quy ước của USE upstream (xem [Options.java](file:///D:/_CODE_BANK/Project_/08_Thesis/use/use-core/src/main/java/org/tzi/use/config/Options.java#L444)), USE tìm plugin trong thư mục `lib/plugins` nằm cùng cấp với file JAR thực thi hoặc trong thư mục cài đặt `USE_HOME`.

Hãy tạo thư mục chạy thử nghiệm (hoặc dùng cấu trúc distribution có sẵn):
```powershell
# Tạo thư mục chạy thử nghiệm
New-Item -ItemType Directory -Force -Path "D:\_CODE_BANK\Project_\08_Thesis\use\deploy\lib\plugins"

# Copy file JAR của host USE
Copy-Item "D:\_CODE_BANK\Project_\08_Thesis\use\use-gui\target\use-gui.jar" -Destination "D:\_CODE_BANK\Project_\08_Thesis\use\deploy\use-gui.jar"

# Copy file plugin JAR vào đúng thư mục mà USE quét tìm
Copy-Item "D:\_CODE_BANK\Project_\08_Thesis\use\use-plugin\target\use-plugin-1.0.0.jar" -Destination "D:\_CODE_BANK\Project_\08_Thesis\use\deploy\lib\plugins\use-plugin-1.0.0.jar"
```

### 2. Khởi chạy USE GUI với Java 21
```powershell
cd D:\_CODE_BANK\Project_\08_Thesis\use\deploy

# Chạy USE GUI bằng Java 21
& "D:\DevTools\Java\jdk-21.0.5\bin\java.exe" -jar use-gui.jar
```

### 3. Dấu hiệu chứng minh Plugin đã nạp thành công
1. **Trên giao diện (UI)**:
   - Trên thanh Menu Bar của USE xuất hiện menu mới có tên **`JaCaMo`**.
   - Khi bấm vào menu `JaCaMo`, xuất hiện 2 menu items:
     - `Open Workbench...` (Mở giao diện làm việc chính của Plugin).
     - `Status` (Bật popup thông báo `JaCaMo plugin ready`).
2. **Trong Log Console**:
   - Khi USE khởi động, console xuất hiện các dòng log:
     ```text
     Searching for plugins in: [...lib\plugins]
     Plugin filename(s) [use-plugin-1.0.0.jar]
     Plugin [JaCaMo] found.
     PluginDescriptor created.
     ```
3. **Trong USE Interactive Shell**:
   - Gõ lệnh: `jacamo status`
   - USE Shell trả về kết quả: `JaCaMo plugin ready (Phase 1 skeleton)`.

---

# O. Run Auction Guide (Hướng dẫn chạy Case Study Auction)

### 1. Luồng thực hiện hiện tại (Supported Flow)
Dự án đã có sẵn case study mẫu tại:
`D:\_CODE_BANK\Project_\08_Thesis\use\use-plugin\src\test\resources\auction\auction.jcm`

Các bước thực hiện trên giao diện Workbench:
1. Trong USE GUI, chọn menu **`JaCaMo` -> `Open Workbench...`**. Cửa sổ **USE JaCaMo Workbench** sẽ mở ra.
2. Trên thanh công cụ trên cùng, nhấn nút **`Import JaCaMo Project...`**.
3. Duyệt và chọn file:
   `D:\_CODE_BANK\Project_\08_Thesis\use\use-plugin\src\test\resources\auction\auction.jcm`
4. **Quan sát kết quả tự động**:
   - Tab **`Project`**: Hiển thị ID `auction`, số lượng file nguồn, thống kê 4 chiều MAS/Jason/CArtAgO/Moise, trạng thái mapping `LOCKED_BASELINE_V1`.
   - Tab **`Trace`**: Hiển thị bảng truy vết chi tiết từng phần tử (Agent `auctioneer`, Artifact `auction1`, Group `auction_group`, Scheme `auction_scheme`) sang các Class/Object tương ứng của USE.
   - Tab **`Diagnostics`**: Hiển thị danh sách cảnh báo hoặc thông tin dịch (không có lỗi FATAL).
   - Tab **`Verification`**: Tự động hiển thị kết quả kiểm chứng OCL offline ban đầu (`USE_STRUCTURE`, các bất biến Core, và các điều kiện của Auction).
5. Nhấn nút **`Load OCL...`** nếu muốn bổ sung profile OCL người dùng.
6. Nhấn nút **`Run Full Verification`** để đánh giá lại toàn bộ ràng buộc trên trạng thái hiện tại.
7. Nhấn nút **`Export Report...`** để xuất báo cáo kiểm định ra file Markdown hoặc JSON.

---

# P. Troubleshooting

1. **Lỗi Java Version Mismatch (`UnsupportedClassVersionError: ... class file version 65.0`)**:
   - *Nguyên nhân*: Lệnh `java` mặc định trong PATH trỏ về Java 8 (`1.8.0_501`), trong khi code plugin được biên dịch bằng Java 21 (class file version 65).
   - *Khắc phục*: Luôn sử dụng đường dẫn tuyệt đối tới Java 21: `& "$env:JAVA_HOME\bin\java.exe" -jar ...`.
2. **Lỗi `Plugin menu JaCaMo không xuất hiện`**:
   - *Nguyên nhân*: File JAR của plugin không nằm đúng thư mục `lib/plugins/` tương đối so với file `use-gui.jar`, hoặc thư mục chạy không có quyền đọc.
   - *Khắc phục*: Đảm bảo cấu trúc:
     ```text
     <run_dir>/
     ├── use-gui.jar
     └── lib/
         └── plugins/
             └── use-plugin-1.0.0.jar
     ```
3. **Lỗi `IMPORT_FAILED: RESOLUTION_AMBIGUOUS` khi import dự án có 2 Artifact cùng tên hàm (Finding P2)**:
   - *Nguyên nhân*: StaticProjectImporter chưa tích hợp nạp file `binding.json`.
   - *Khắc phục*: Tạm thời chỉ import các dự án JaCaMo có tên thao tác phân biệt giữa các Artifact, hoặc chờ bản vá tích hợp `ExactSemanticResolver`.
4. **Lỗi `Runtime vi phạm nhưng Full Verification báo PASS` sau khi Rebuild (Finding P1)**:
   - *Nguyên nhân*: Vòng đời kết nối runtime bị ngắt khỏi workspace mới sau khi gọi `rebuild()`.
   - *Khắc phục*: Nếu cần rebuild hoặc nạp lại OCL, hãy ngắt kết nối (`Disconnect`) trước, sau đó mới Rebuild và kết nối lại (`Connect`).

---

# Q. Current Implementation Boundary (Ranh giới thực thi hiện tại)

Để đảm bảo tính trung thực học thuật của luận văn, đây là **ranh giới chính xác** những gì code ĐÃ LÀM ĐƯỢC và CHƯA LÀM ĐƯỢC:

### 1. Những gì ĐÃ HOÀN TOÀN LÀM ĐƯỢC:
- Đọc, phân tích cú pháp và trích xuất đa chiều tĩnh từ file `.jcm`, `.asl`, `.java`, `.xml` của JaCaMo.
- Ánh xạ 100% siêu mô hình DSML4JaCaMo Ecore sang USE Model, tự động sinh mã `.use` và `.cmd` chuẩn xác.
- Khởi tạo trực tiếp mô hình hình thức trong bộ nhớ USE (`MModel`, `MSystem`, `MSystemState`).
- Dịch tự động CArtAgO `@GUARD` thành OCL Precondition.
- Đánh giá toàn diện OCL Invariants, Preconditions, Postconditions (giữ nguyên `@pre`) cả ở chế độ tĩnh lẫn thời gian thực.
- Bắt sự kiện thời gian thực từ Jason, CArtAgO và Moise in-process, cập nhật trạng thái động của USE qua hàng đợi an toàn và kiểm tra OCL tức thời.

### 2. Ranh giới CHƯA THỰC HIỆN / NGOÀI PHẠM VI (Out of Scope):
- **Không khởi chạy toàn bộ file `.jcm` độc lập bên ngoài**: Runtime test hiện tại chạy in-process với các API Java của Jason 3.3.0, CArtAgO 3.1, Moise 1.1. Chưa có wrapper tự động kích hoạt tiến trình JaCaMo command-line độc lập từ UI.
- **Không tự động dịch thân hàm Java tùy ý**: Không tự động reverse-engineer mã lệnh Java phức tạp trong Artifact thành OCL postcondition.
- **Không tự động dịch Deontic Normative Logic thành OCL**: Các quy phạm Moise Norm (Obligation/Permission) được giữ nguyên dưới dạng cấu trúc lớp/đối tượng trong USE, **chưa có engine tự động chuyển đổi logic nghĩa vụ deontic thành OCL Invariant**.
- **Không can thiệp luồng JaCaMo (Observe-only)**: Khi OCL bị vi phạm, hệ thống chỉ ghi nhận báo cáo và thông báo, **không tự động block hay hủy bỏ hành động của Agent**.

---

# R. Missing Components (Các thành phần còn thiếu)

Dựa trên kết quả audit v1.0.0, trước khi có thể coi là bản hoàn thiện tuyệt đối (v1.0.1+), dự án còn thiếu 3 thành phần kỹ thuật sau:

1. **Vá lỗi P1 (Runtime Workspace Lifecycle Reconnection)**:
   - Trong [DefaultJaCaMoFacade.java](file:///D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/DefaultJaCaMoFacade.java), khi `rebuild()`, `loadVerificationProfile()`, hoặc `importProject()` được gọi lại trên một kết nối đang mở, cần hủy kết nối cũ hoặc chuyển giao con trỏ `MSystem` mới sang cho `RuntimeMirrorService` và `RuntimeVerificationEngine`.
2. **Vá lỗi P2 (Tích hợp Binding Resolution vào StaticProjectImporter)**:
   - Trong [StaticProjectImporter.java](file:///D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/extraction/StaticProjectImporter.java), thay vì gọi `SemanticResolver` thông thường, cần cho phép truyền đối tượng `BindingFile` (nạp từ `binding.json` trong thư mục dự án) và sử dụng `ExactSemanticResolver` để tự động hóa giải các điểm nhập nhằng (ambiguities).
3. **UI Runtime Connector Configuration**:
   - Hiện tại phương thức `configureRuntime(...)` trong `DefaultJaCaMoFacade` chỉ được gọi qua code test hoặc programmatic API. Tab `Runtime` trên giao diện người dùng cần bổ sung một Dialog cấu hình để người dùng nhập tham số endpoint hoặc chọn chạy in-process connector trực tiếp từ giao diện.

---

# S. Recommended Next Implementation Step (Đề xuất bước tiếp theo)

Khi bạn sẵn sàng chuyển sang giai đoạn phát triển tiếp theo (Development Phase), lộ trình đề xuất là:

1. **Bước 1 (Hotfix P1)**: Sửa lifecycle trong [DefaultJaCaMoFacade.java](file:///D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/DefaultJaCaMoFacade.java) để đồng bộ hóa `RuntimeMirrorService` với `workspace` mới khi `rebuild()` hoặc nạp OCL. Thêm regression test từ `V100FacadeAudit.java`.
2. **Bước 2 (Hotfix P2)**: Cập nhật [StaticProjectImporter.java](file:///D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/extraction/StaticProjectImporter.java) và [DefaultJaCaMoFacade.java](file:///D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/DefaultJaCaMoFacade.java) để tự động nạp `binding.json` từ thư mục project và giải quyết nhập nhằng qua `ExactSemanticResolver`.
3. **Bước 3 (Đóng gói v1.0.1)**: Chạy lại toàn bộ test suite và xuất xưởng release tag `use-jacamo-plugin-v1.0.1`.

---
*Báo cáo phân tích Read-Only đã hoàn tất. Tôi đang dừng lại và chờ chỉ thị tiếp theo từ bạn trước khi thực hiện bất kỳ thay đổi nào trên codebase.*