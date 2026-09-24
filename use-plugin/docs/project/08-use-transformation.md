# USE Transformation

> Active V2 migration: [profile contract](v2-migration/phase33-verification-profile.md)
> supersedes VSP001-VSP005 below. The V2 profile preserves the full baseline plan;
> V1 overrides apply only to explicit historical fixtures. Parser/materialization
> now use V2; whole-pipeline clean regression PASS 350/350.
> [Phase 35 implementation/evidence](v2-migration/phase35-transformation.md) now
> defines active V2 projections, defaults, membership/order trace and consumer migration.

## 1. Mục tiêu

Biến `JaCaMoSemanticModel V2 + Mapping V2` thành:
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
- type theo active Mapping V2;
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

---

## 13. Historical JaCaMo Verification Metamodel/Profile V1

The historical V1 pipeline used an explicit semantic layer over the immutable Mapping V1 transformation plan. Its
profile was packaged as `jacamo-verification-profile-v1.json`; those decisions now apply only to explicit historical
fixtures and never rewrite the historical Ecore or Mapping V1.

- `[OUR-EXT]` VSP001-VSP004 remove `Norm`, `Group`, `Role`, and `Scheme` inheritance from `Organisation` only in
  the effective verification model. Concrete declarations therefore do not inherit the aggregate obligations
  R004-R007.
- `[SEMANTIC-CLARIFICATION]` VSP005 changes the effective forward multiplicity of R047
  (`Belief.triggeredBy`) from `1` to `0..1`, because an initial belief has source existence independent of a
  `TriggeringEvent`. The frozen mapping remains unchanged.
- No other multiplicity or association kind is changed by Profile V1. Every decision is checked against the
  expected baseline class/rule before application; a mismatch fails with `VERIFICATION_PROFILE_BASELINE_MISMATCH`.

The source extraction contract for Auction is:

- one Jason plan-body action is one semantic object owned through R053 (`Plan.hasAction`); it is not also inserted
  into R055 (`Body.bodyterm`) and is never duplicated. R055 remains available for independently represented
  non-Action body terms;
- inherited Action features such as R057 (`Action.nextAction`) apply to `ExternalAction` and `InternalAction`;
- Moise/JCM evidence produces R007, R015, R019, R020, and R021 in the frozen Ecore direction;
- a Jason achievement goal is linked by R048 only when its exact same-agent triggering event is present;
- unresolved or ambiguous references stay unresolved and may require binding. Binding is never used to fabricate a
  mandatory link.

Phase 5 validation on 2026-09-15 confirms deterministic text and direct backends from the same effective plan. The
Auction initial state passes USE structure, multiplicity, and initial invariant checks with no
`MATERIALIZATION_COMPOSITION_CONFLICT`, `MATERIALIZATION_REQUIRED_LINK_MISSING`, or other ERROR diagnostic.
