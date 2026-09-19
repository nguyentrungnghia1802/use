# Constraint Translation and OCL Architecture

## 1. Nguyên tắc

OCL không chỉ là "file nhập tay".
Constraint có thể đến từ nhiều nguồn, nhưng chỉ được translate khi semantics đủ rõ.

```text
JaCaMo Native Constraints/Conditions
        ↓
Constraint Extractor
        ↓
Typed Constraint IR
        ↓
Translation Feasibility
        ├── EXACT
        ├── SOUND_SUBSET
        ├── LOSSY
        └── UNSUPPORTED
        ↓
OCL Generator
```

---

## 2. Bốn nhóm OCL

### 2.1 Translated JaCaMo OCL
Sinh từ native conditions có semantics tương thích.

Ứng viên:
- Jason plan context;
- typed relational/arithmetic conditions;
- CArtAgO operation guard;
- artifact state conditions;
- selected structural consistency not already fully represented by USE.

Không auto-translate:
- arbitrary Java code;
- unsupported AgentSpeak semantics;
- full deontic lifecycle;
- side effects không model được.

### 2.2 JaCaMo Core OCL
Viết tay, reusable cho mọi project.

Ví dụ categories:
- accessibility consistency;
- cross-dimensional reference consistency;
- action-operation alignment;
- percept-belief alignment;
- goal alignment.

Mọi rule phải có rationale và evidence.

### 2.3 Case-study/domain OCL
Ví dụ Auction:
- status validity;
- bid amount positive;
- budget constraints;
- operation ordering;
- pre/postconditions.

### 2.4 Runtime evaluation
Không phải syntax OCL mới.
Đây là việc evaluate các constraints ở snapshot/event runtime.

---

## 3. Structural constraints

Multiplicity/typing/association structure nên được encode trực tiếp trong USE model.

Không sinh OCL duplicate nếu USE structure đã kiểm được.

Ví dụ:
```text
Norm.Nrole [1]
```
→ USE association multiplicity 1.

Không cần invariant chỉ để nói "Nrole tồn tại", trừ khi cần custom diagnostic/research experiment.

---

## 4. Constraint IR

```text
ConstraintSpec
- id
- sourceKind
- sourceSpan
- contextSemanticId
- expressionTree
- expectedType
- translationStatus
- assumptions[]
- dependencies[]
```

Expression nodes tối thiểu:
- Literal
- VariableRef
- PropertyRef
- UnaryOp
- BinaryOp
- LogicalOp
- Comparison
- Arithmetic
- Call
- CollectionPredicate
- Undefined/Unknown

---

## 5. Translation status

### EXACT
Semantics tương đương theo supported subset.

### SOUND_SUBSET
OCL rule bảo toàn một hướng verification được document.

### LOSSY
Chỉ dùng nếu user/research profile cho phép; phải warning và không gọi "equivalent".

### UNSUPPORTED
Không generate OCL; giữ source và diagnostic.

---

## 6. Jason context translation

Ví dụ:
```text
+!bid(A) : auction_open & budget(B) & B >= A
```

Chỉ translate nếu resolver xác định:
- `auction_open` map tới state property nào;
- `budget(B)` map tới Agent/Artifact state nào;
- variables/types hợp lệ.

Nếu chỉ biết syntax mà không biết semantic owner:
- không đoán;
- emit unresolved binding;
- optional project binding có thể giải.

---

## 7. CArtAgO guards

Operation guard có thể map thành OCL precondition khi:
- owner Artifact resolved;
- referenced state values được project sang USE;
- expression nằm trong supported subset.

Operation effects chỉ map thành postcondition nếu source semantics đủ rõ hoặc user cung cấp contract. Không reverse-engineer arbitrary Java body thành postcondition mặc định.

---

## 8. Moise Norm

Norm:
```text
role + mission + type + time constraint
```

được preserve thành model/state.

Không tự động:
```text
obligation → OCL invariant
```

Combined verification có thể viết OCL/research rule kiểm:
- agent plays role;
- mission assignment consistent;
- performed action consistent với state;
- external normative engine state nếu adapter cung cấp.

Nhưng deontic evaluation vẫn được phân biệt với OCL.

---

## 9. OCL packaging

Recommended:
```text
Core/OCL/core/jacamo-core.ocl
examples/<case>/verification/<case>.ocl
build/.../generated-derived.ocl
```

Manifest phải ghi:
- source;
- generator version;
- translation status;
- assumptions;
- dependency semantic IDs.

---

## 10. Validation

Generated OCL phải:
- parse/type-check trong USE;
- bind đúng class/operation;
- deterministic;
- có positive/negative fixture;
- fail build nếu generator sinh OCL invalid.
