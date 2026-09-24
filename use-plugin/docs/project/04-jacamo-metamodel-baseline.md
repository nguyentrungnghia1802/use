# JaCaMo Metamodel Baseline

> **V2 migration status — 2026-09-23:** Active semantic specification is Metamodel V2 + Mapping 2.1 (`WORKING_BASELINE`), under `Core/*/version-2/`. Production consumers are not yet migrated; the fresh pre-migration build fails after V1 resource relocation. V1 release/closure claims below are historical, not current V2 acceptance. See [migration baseline](v2-migration/phase29-pre-migration-baseline.md) and [active selection contract](v2-migration/active-baseline-policy.md).


## 1. Vai trò

`JaCaMo-Metamodel.ecore` là semantic vocabulary baseline dùng cho mapping và verification.

Nó không phải:
- runtime state model;
- USE model;
- OCL AST;
- case-study model.

---

## 2. Baseline hiện tại

Baseline reconstruction hiện có:
- 37 EClass;
- 67 EAttribute đã khai báo/type xác định;
- 63 EReference;
- 14 inheritance edge;
- 3 visible-but-unresolved attributes chưa được khai báo vì datatype chưa đủ bằng chứng.

Dimensions:
- Agent/Jason;
- Environment/CArtAgO;
- Organisation/Moise;
- cross-dimensional references.

---

## 3. Cross-dimensional relations quan trọng

Phải preserve:
- `ExternalAction.operation → AbsOperation`
- `ObsProperty.obsproperty → Belief`
- `OGoal.OGoalToGoal → Goal`
- `Agent.joinWorkspace → Workspace`
- `Agent.artifact → Artifact`
- `Role.players → Agent`
- `Organisation.deploysAgent → Agent`
- `Plan.RefArtifact → Artifact`

Không tạo duplicate binding class ở metamodel nếu existing relation đã đủ semantics.

---

## 4. Unresolved source facts

Các phần visible nhưng datatype/default chưa đủ bằng chứng phải giữ explicit:
- `ObsProperty.initialValue`
- `AbsOperation.paramName`
- `TriggeringEvent.addAndDel`

`Message.isBroadcast`:
- EBoolean đã xác định;
- original author default không được suy ra chỉ từ intrinsic Ecore Boolean default.

Mapping/translator không được fabricate các field unresolved này.

---

## 5. Inheritance cần provenance/review

Baseline reconstruction hiện preserve:
- `Norm extends Organisation`
- `Group extends Organisation`
- `Role extends Organisation`
- `Scheme extends Organisation`
- `TriggeringEvent extends Action`

Implementation phải map đúng file canonical hiện tại, nhưng tài liệu/research claim phải phân biệt:
- "reconstruction declares X"
với
- "original authors unquestionably intended X".

---

## 6. Evolution

Khi Ecore thay đổi:
1. compute new fingerprint;
2. classify added/changed/removed;
3. update mapping;
4. update schema/audit;
5. run coverage validator;
6. bump baseline version;
7. regenerate fixtures;
8. rerun full test suite.

Không sửa Ecore để tiện code mà không cập nhật evidence/audit.

---

## 7. Verification responsibilities từ Ecore

Ecore cung cấp:
- vocabulary;
- ownership;
- type;
- multiplicity;
- containment;
- inheritance;
- navigation graph.

Ecore **không tự cung cấp** mọi semantic OCL rule.

Ví dụ `Role.min` và `Role.max` tồn tại không tự động chứng minh rule `min <= max`. Rule đó cần source semantics hoặc verification profile riêng.


## Final target decision (Phase 25–26)

D25-01 retains the exact frozen structural V1 as final for the supported scope.
Runtime Mapping V1 is reconciled separately; see [final contract audit](phase26-runtime-mapping-audit.md).
No general runtime-instance, standalone launcher or NPL equivalence is implied.
