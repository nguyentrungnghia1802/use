# USE Transformation

## 1. Mục tiêu

Biến `JaCaMoSemanticModel + Mapping V1` thành:
- USE structural model;
- initial state;
- operation definitions;
- trace model;
- generated constraints;
- diagnostics.

---

## 2. Hai backend

### 2.1 Text backend
Sinh:
- `<project>.use`
- `<project>.cmd`
- generated OCL file(s)

Ưu điểm:
- reproducible;
- dễ inspect;
- tốt cho thesis/demo/golden test.

### 2.2 Direct API backend
Tạo/update:
- `MModel`
- `MSystemState`
- operation state

Ưu điểm:
- runtime;
- không cần round-trip file.

Hai backend phải dựa trên cùng `TransformationPlan`.

---

## 3. Class generation

Metamodel classes dùng chung:
- có thể generate vào model;
- hoặc import một base USE model nếu USE import mechanism phù hợp.

Concrete Artifact subclasses:
- generate theo resolved Artifact Java types.

Name collision:
- deterministic suffix/hash;
- trace giữ original fully-qualified identity.

---

## 4. Attributes

Declared EAttribute:
- type theo Mapping V1;
- source value unset → USE undefined/omitted theo backend;
- không fabricate default nếu source không khai báo.

Observable Property Projection:
- chỉ generate typed attribute khi VP002 preconditions pass.

---

## 5. Associations/compositions

- preserve forward role;
- preserve forward multiplicity;
- generated reverse role không được coi source semantics;
- association name deterministic;
- links sinh từ semantic references.

---

## 6. Inheritance

Preserve Ecore baseline exactly.
Review flags không làm transform tự đổi inheritance.

---

## 7. Operations

VP003:
- operation owner = concrete Artifact subclass;
- name/signature resolved;
- parameter names/types preserved;
- trace tới `AbsOperation/Operation`.

Nếu unresolved:
- không fake parameter;
- keep structural operation object only.

---

## 8. Initial state

Materialization order:
1. create all objects;
2. set scalar attributes;
3. insert composition links;
4. insert association links;
5. initialize projected state;
6. validate multiplicities;
7. run initial invariants.

---

## 9. `.cmd` generation

Dùng deterministic object names.
Script phải idempotent ở level generation, không nhất thiết executable hai lần trên cùng state.

Output order ổn định:
- by semantic ID;
- then target kind.

---

## 10. USE compile gate

Generated `.use` chỉ accepted khi:
- parser compile success;
- type check success;
- no duplicate classifiers;
- no duplicate association names;
- no invalid role/multiplicity;
- generated OCL parse/type-check success.

---

## 11. Round-trip trace

Mỗi generated declaration/object/link phải có trace record.

Nếu text backend không hỗ trợ metadata inline, trace lưu JSON riêng:
```text
trace.json
```

---

## 12. Verification manifest

Mỗi transformation run sinh:
```text
manifest.json
```

Chứa:
- project hash;
- Ecore hash;
- mapping hash;
- plugin version;
- generated artifact hashes;
- OCL profile hashes;
- timestamp;
- warnings/errors count.
