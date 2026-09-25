# Vision, Scope and Research Positioning

## 1. Vision

Xây dựng một plugin USE có khả năng biến một JaCaMo application thành một formal verification representation trong USE, sau đó dùng OCL để:
- kiểm tra model consistency;
- kiểm tra state invariant;
- kiểm tra operation pre/postcondition;
- kiểm tra consistency giữa Agent, Environment và Organisation;
- kiểm tra trạng thái runtime thực tế của JaCaMo.

Tên hướng kỹ thuật:

**USE Extension for JaCaMo Design-Time and Runtime Verification using UML/OCL**

---

## 2. Research framing

JaCaMo cung cấp execution semantics thông qua:
- Jason — Agent/BDI;
- CArtAgO — environment/artifact;
- Moise — organisation/normative.

USE cung cấp:
- UML-like formal model;
- OCL parser/evaluator;
- system states;
- operation pre/postcondition;
- snapshot checking;
- visualization/diagnostics.

Plugin kết nối hai phía:

```text
JaCaMo execution/model semantics
        ↓
formal representation + trace
        ↓
USE/OCL verification
```

Contribution không được mô tả đơn giản là "dùng OCL với JaCaMo lần đầu". Prior work đã dùng OCL cho model validation. Giá trị cần tập trung ở:
- faithful integrated JaCaMo representation;
- constraint preservation/translation;
- state/operation contracts;
- cross-dimensional verification;
- runtime mirroring và runtime checking.

---

## 3. Phạm vi đầy đủ

### 3.1 Design-time import
Input:
- `.jcm` entry point;
- Jason `.asl`;
- CArtAgO Java source/class metadata;
- Moise XML;
- include/source paths liên quan.

Output:
- semantic model;
- USE `.use` hoặc trực tiếp `MModel`;
- initial `.cmd` hoặc trực tiếp `MSystemState`;
- generated OCL fragments khi có semantics tương thích;
- trace model;
- diagnostics.

### 3.2 OCL framework
Hỗ trợ:
- structural verification;
- state invariants;
- operation pre/postconditions;
- cross-dimensional rules;
- reusable JaCaMo core OCL;
- case-study/domain OCL;
- translated JaCaMo constraints;
- user-authored OCL.

### 3.3 Runtime verification
Hỗ trợ:
- runtime connection;
- event/state extraction;
- trace lookup;
- `MSystemState` mutation;
- operation enter/exit;
- incremental verification;
- violation report liên kết ngược về JaCaMo source/runtime element.

### 3.4 Traceability
Mọi element quan trọng phải có:
- source identity;
- target USE identity;
- transformation rule;
- provenance;
- resolution status;
- optional runtime identity.

---

## 4. Non-goals

Không làm USE trở thành:
- Jason interpreter;
- CArtAgO runtime;
- Moise/NPL engine;
- agent planner;
- action enforcement controller trong baseline;
- automatic repair engine;
- source code generator thay thế JaCaMo;
- deontic-to-OCL compiler tổng quát.

Có thể nghiên cứu enforcement/repair về sau nhưng không làm thay đổi core architecture.

---

## 5. Hai mode verification

### Mode A — Offline / design-time

```text
Project → parse → transform → state → OCL check
```

Không cần JaCaMo chạy.

Dùng cho:
- structural consistency;
- initial state;
- generated model correctness;
- static/cross-dimensional relationship checks.

### Mode B — Runtime

```text
Running JaCaMo → adapter → USE state → OCL check
```

Dùng cho:
- dynamic beliefs/goals;
- observable property changes;
- operation invocation;
- role/mission/normative state;
- operation contracts;
- runtime cross-dimensional properties.

---

## 6. Success criteria

Dự án hoàn thành khi:
1. Metamodel V2, Structural Mapping V2 và Runtime Mapping V2 được audit và chỉ freeze sau khi đạt Phase 44.
2. Plugin import được JaCaMo project qua `.jcm`.
3. Semantic model bao phủ Agent, Environment, Organisation.
4. Transformation tạo USE model hợp lệ.
5. Initial state tạo được objects/links/values đúng.
6. Constraint translator có provenance và không đoán.
7. Core OCL + case-study OCL load/check được.
8. Runtime adapter đồng bộ ít nhất các event/state quan trọng của cả 3 dimension.
9. Pre/postcondition được kiểm qua operation enter/exit hoặc equivalent API.
10. Violation trace được về JaCaMo source/runtime element.
11. Auction case study có positive và negative runtime scenarios.
12. Test suite và build/release reproducible.
