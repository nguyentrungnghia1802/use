# Constraint Translation and OCL Architecture

> Active V2: Plan.context and Artifact guard sourceFacts preserve constraint
> provenance. `jacamo-core-v2.ocl` checks that a present Action.operation anchor
> has EXTERNAL kind; the optional V2 relation is not made mandatory by an old
> ExternalAction rule. Ordered source navigation uses rank-derived query bindings.
> Norm strings and unsupported Java/Jason semantics remain untranslated. See
> [Phase 35 contract](v2-migration/phase35-transformation.md).

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

Current Phase 24 implementation: every extracted Jason plan context remains
`UNSUPPORTED_JASON_APPLICABILITY`. Exact variable/property bindings alone do not
prove that plan applicability is a global `Plan` invariant or identify the correct
runtime checkpoint. No partial expression is emitted. The typed expression parser
remains available for explicit contracts; it does not establish Jason semantics.

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

## 11. Current supported contract (Phase 24; supersedes Phase 6 translation scope)

- `[OUR-EXT]` The typed Constraint IR records expression type, context/operation binding, status, source provenance,
  assumptions, and semantic dependencies without depending on USE parser types.
- `[SEMANTIC-CLARIFICATION]` CArtAgO guards require exactly one pure return AST,
  an exact guarded operation and matching parameter order/names/types, and primitive
  boolean return. Referenced parameters must be primitive `int` or `boolean`.
  Literals, comparisons, Boolean equality and logical operators are supported.
  `&&`/`||` preserve truth values because operands are pure, total scalar expressions.
  Unused parameters of other types are allowed. Arithmetic, assignment, calls,
  reference equality, floating point and arbitrary method bodies remain unsupported.
  Auction `canBid(String item, int amount) { return amount >= 0; }` remains exact.
- `[SEMANTIC-CLARIFICATION]` Jason contexts remain unsupported even with explicit
  property bindings: plan applicability is not a global invariant.
- `[OUR-EXT]` Only EXACT constraints emit. No SOUND_SUBSET preservation contract is
  currently approved; SOUND_SUBSET, LOSSY and UNSUPPORTED remain in the manifest as
  NOT_EMITTED entries with status, source hash, dependencies and reasons/assumptions.
- `[OUR-EXT]` Authored postconditions require an explicit source-backed contract. `@pre` is represented directly in
  the IR; arbitrary Java effects never produce a postcondition.
- `[OUR-EXT]` `jacamo-core.ocl` contains reusable cross-dimensional checks and an adjacent manifest with rationale
  and evidence. Case OCL is loaded only from within the project root, retains its origin and SHA-256, and is compiled
  together with translated/core constraints against the generated model.
- `[SEMANTIC-CLARIFICATION]` Moise Norm elements remain structural. The extractor never emits a Norm-derived OCL
  obligation, permission, or prohibition.

The complete rule inventory, positive/negative/ambiguity evidence and two-case
acceptance are in [Phase 24 evidence](phase24-translation-multicase-evidence.md).
