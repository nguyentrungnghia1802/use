# USE JaCaMo Plugin — Project Documentation

## 1. Mục đích của bộ tài liệu

Bộ tài liệu này là **source of truth về kiến trúc và kế hoạch triển khai** cho dự án mở rộng USE nhằm hỗ trợ kiểm chứng hệ thống đa tác tử JaCaMo bằng UML/OCL, từ design-time đến runtime verification.

Mục tiêu cuối:

> Nhập một JaCaMo project thực tế, xây dựng một biểu diễn USE tương đương có traceability rõ ràng, chuyển dịch các constraint JaCaMo có semantics phù hợp sang OCL khi có thể, cho phép bổ sung OCL verification riêng, và đồng bộ runtime JaCaMo vào `MSystemState` của USE để đánh giá OCL trên trạng thái chạy thật.

Dự án **không**:
- thay thế JaCaMo runtime;
- chạy Jason/CArtAgO/Moise bên trong USE;
- dùng OCL làm bộ điều khiển Agent ở giai đoạn chính;
- biến mọi Moise Norm thành OCL;
- đoán mapping từ tên file/tên action khi semantics không đủ bằng chứng.

Dự án **có**:
- JaCaMo metamodel baseline bằng Ecore;
- metamodel mapping JaCaMo → USE;
- parser/extractor cho JaCaMo project;
- semantic intermediate model;
- USE model/state generation;
- constraint translation có kiểm soát;
- core/case-study OCL;
- traceability và optional binding;
- runtime adapter;
- runtime verification;
- reporting, diagnostics, tests và case study Auction.

---

## 2. Source of truth

Thứ tự ưu tiên khi có mâu thuẫn:

1. `Core/Metamodel/JaCaMo-Metamodel.ecore`
2. `Core/Mapping/jacamo-use-mapping-v1.json` sau khi Mapping Baseline V1 được freeze
3. `docs/project/*.md`
4. `docs/agent/task.md`
5. implementation hiện tại
6. heuristic/import assistance

Nếu code mâu thuẫn với metamodel/mapping/docs đã freeze, **sửa code**, không âm thầm đổi specification.

Mọi thay đổi breaking ở Ecore hoặc mapping phải:
- bump version;
- cập nhật freeze manifest;
- cập nhật audit;
- cập nhật tests;
- cập nhật tài liệu ảnh hưởng;
- không silently reinterpret model cũ.

---

## 3. Kiến trúc một dòng

```text
JaCaMo Project
(.jcm + .asl + CArtAgO Java + Moise XML)
        ↓
Extraction / Parsing
        ↓
JaCaMo Semantic Model
        ↓
Metamodel Mapping + Constraint Translation
        ↓
USE Model + Initial State + Trace
        ↓
OCL Verification
        ↓
Runtime Adapter
        ↓
Live USE MSystemState
        ↓
Continuous/Event-driven OCL Verification
```

---

## 4. Các tài liệu

| File | Nội dung |
|---|---|
| `01-vision-scope.md` | Mục tiêu nghiên cứu, phạm vi, non-goals, success criteria |
| `02-system-architecture.md` | Kiến trúc tổng thể và boundaries |
| `03-repository-structure.md` | Cấu trúc repository/module/file đề xuất |
| `04-jacamo-metamodel-baseline.md` | Baseline Ecore, invariants nghiên cứu, unresolved |
| `05-metamodel-mapping-contract.md` | Contract JaCaMo Ecore → USE |
| `06-semantic-model-and-extraction.md` | Project loader, parser, semantic IR |
| `07-constraint-translation-and-ocl.md` | Nguồn constraint và translator |
| `08-use-transformation.md` | Sinh MModel/.use, MSystemState/.cmd |
| `09-traceability-binding-resolver.md` | Trace, resolver, optional binding |
| `10-runtime-adapter.md` | Runtime events/state → USE state |
| `11-verification-engine.md` | OCL execution, scheduling, results |
| `12-plugin-ui-workflow.md` | Plugin UX, menu, views, diagnostics |
| `13-testing-quality.md` | Test pyramid, fixtures, quality gates |
| `14-auction-case-study.md` | Case study end-to-end |
| `15-build-release-operations.md` | Build, packaging, CI, release |
| `16-research-evidence-boundaries.md` | Claim discipline, evidence labels |
| `17-end-to-end-acceptance.md` | Definition of Done toàn dự án |
| `18-risk-register.md` | Rủi ro kỹ thuật/nghiên cứu và mitigation |
| `19-roadmap.md` | Thứ tự phase và dependencies |

Agent implementation:
- `docs/agent/agent.md`
- `docs/agent/task.md`

---

## 5. Reference implementations cần đối chiếu

Trong quá trình triển khai, ưu tiên đọc trực tiếp source hiện tại của:
- USE core và manual;
- USE plugin mechanism;
- USE `plugin_monitor` như một reference cho runtime verification integration;
- JaCaMo `.jcm` grammar/documentation;
- Jason, CArtAgO, Moise APIs đúng version của project.

Không copy API từ memory nếu source hiện tại có thể kiểm được.

---

## 6. Nguyên tắc lõi

1. **Correctness trước convenience.**
2. **No guessing:** unresolved phải được giữ unresolved.
3. **Metamodel mapping ≠ instance binding.**
4. **Design-time verification ≠ runtime verification.**
5. **Normative/deontic semantics ≠ OCL semantics.**
6. **USE là verification mirror, JaCaMo vẫn là execution engine.**
7. **Traceability là first-class artifact.**
8. **Constraint translation phải có provenance và lossiness metadata.**
9. **Mọi generated artifact phải deterministic.**
10. **Mọi phase phải có test/acceptance criteria trước khi merge.**
