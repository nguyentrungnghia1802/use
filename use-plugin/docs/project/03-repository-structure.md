# Repository Structure

## 1. Mục tiêu tổ chức

Repository phải giúp:
- Agent chỉ đọc phần liên quan;
- core logic không phụ thuộc UI;
- parser/mapping/runtime test độc lập;
- generated artifacts không lẫn source-of-truth;
- tài liệu khớp module.

---

## 2. Cấu trúc đề xuất

```text
repo/
├── pom.xml
├── README.md
│
├── Core/
│   ├── Metamodel/
│   │   ├── JaCaMo-Metamodel.ecore
│   │   ├── metamodel-freeze-manifest.json
│   │   └── metamodel-audit.md
│   │
│   ├── Mapping/
│   │   ├── jacamo-use-mapping-v1.json
│   │   ├── jacamo-use-mapping.schema.json
│   │   ├── mapping-freeze-manifest.json
│   │   └── mapping-audit.md
│   │
│   └── OCL/
│       ├── core/
│       │   └── jacamo-core.ocl
│       ├── generated/
│       │   └── README.md
│       └── schema/
│           └── constraint-provenance.schema.json
│
├── docs/
│   ├── project/
│   └── agent/
│
├── use-plugin/
│   ├── pom.xml
│   └── src/
│       ├── main/
│       │   ├── java/.../
│       │   │   ├── plugin/
│       │   │   ├── project/
│       │   │   ├── parser/
│       │   │   │   ├── jcm/
│       │   │   │   ├── jason/
│       │   │   │   ├── cartago/
│       │   │   │   └── moise/
│       │   │   ├── semantic/
│       │   │   ├── mapping/
│       │   │   ├── constraint/
│       │   │   ├── transform/
│       │   │   ├── trace/
│       │   │   ├── binding/
│       │   │   ├── useadapter/
│       │   │   ├── runtime/
│       │   │   ├── verification/
│       │   │   ├── diagnostics/
│       │   │   └── ui/
│       │   └── resources/
│       │       ├── metamodel/
│       │       ├── mapping/
│       │       └── ocl/
│       └── test/
│           ├── java/.../
│           └── resources/
│
├── examples/
│   └── auction/
│       ├── jacamo/
│       ├── verification/
│       │   ├── auction.ocl
│       │   └── binding.json
│       ├── expected/
│       │   ├── generated.use
│       │   ├── generated.cmd
│       │   └── trace.json
│       └── scenarios/
│
├── test-fixtures/
│   ├── jcm/
│   ├── asl/
│   ├── cartago/
│   ├── moise/
│   ├── mapping/
│   └── runtime/
│
└── scripts/
    ├── verify-metamodel.*
    ├── audit-mapping.*
    ├── run-auction-e2e.*
    └── package-plugin.*
```

---

## 3. Quy tắc dependency

```text
parser → semantic
mapping → semantic + mapping schema
constraint → semantic + trace
transform → semantic + mapping + USE adapter
runtime → trace + USE adapter + verification
ui → services/facades only
```

Không cho:
- `semantic` import `ui`;
- parser gọi Swing;
- runtime parser file lại;
- OCL translator phụ thuộc case Auction;
- mapping engine hard-code 37 class bằng `if/else` nếu mapping file đã định nghĩa.

---

## 4. Generated files

Không commit generated outputs chung vào source, ngoại trừ:
- golden fixtures;
- expected outputs của tests;
- case study artifacts dùng cho thesis reproducibility.

Generated path runtime:
```text
build/jacamo-use/<project-id>/
```

Có thể chứa:
```text
model.use
initial-state.cmd
generated.ocl
trace.json
diagnostics.json
manifest.json
```

---

## 5. Package naming

Khuyến nghị Java package root:

```text
org.tzi.use.plugins.jacamo
```

Subpackages đúng theo structure phía trên.

Không dùng package tên quá generic như:
- `util`;
- `common`;
- `helper`;

trừ khi nội dung thật sự cross-cutting nhỏ và rõ.
