# JaCaMo → USE Metamodel Mapping Contract

> Active contract: Metamodel V2 + Mapping 2.2.0 WORKING_BASELINE. Production selection, source identity, projection and runtime contracts are described in [active baseline policy](v2-migration/active-baseline-policy.md), [transformation](v2-migration/phase35-transformation.md) and [runtime targets](v2-migration/phase35-runtime-targets.md). The V1 counts, freeze rules and IDs in the historical contract below do not govern active V2.


## 1. Mục tiêu

`jacamo-use-mapping-v1.json` là canonical contract biến **JaCaMo metamodel concepts** sang **USE concepts**.

Nó dùng chung cho mọi case study.

Nó không chứa:
- `bidder1`;
- `auction1`;
- runtime object IDs;
- case-specific OCL;
- hard-coded Auction semantics.

---

## 2. Generic rules

| Ecore | USE |
|---|---|
| EPackage | model namespace/context |
| EClass | class |
| abstract EClass | abstract class |
| EAttribute | attribute |
| eSuperType | generalization |
| containment EReference | composition |
| non-containment EReference | association |
| EString | String |
| EInt | Integer |
| EBoolean | Boolean |

Multiplicity phải preserve forward semantics của EReference.

---

## 3. Qualified identity

Không dùng feature name trần làm source key.

Sai:
```text
operation
```

Đúng:
```text
Artifact.operation
ExternalAction.operation
```

Canonical source key:
```text
dSML4JaCaMo::<EClass>#<feature>
```

Inheritance:
```text
dSML4JaCaMo::<Subclass>->super::<Superclass>
```

Mỗi mapping entry phải có unique ID.

---

## 4. Target association naming

Target association name phải deterministic và unique:

```text
<SourceClass>_<sourceFeature>_<TargetClass>
```

Ví dụ:
```text
Agent_joinWorkspace_Workspace
ExternalAction_operation_AbsOperation
Artifact_operation_AbsOperation
```

Forward role giữ exact EReference name.
Reverse role là generated helper và phải đánh `reverseAuthoritative=false` nếu Ecore không có eOpposite.

---

## 5. Containment semantics

Containment EReference:
- target USE construct: composition;
- source/container ở diamond side;
- target multiplicity copy EReference bounds;
- reverse end không được diễn giải như source-authored eOpposite;
- ownership uniqueness cần được kiểm qua composition semantics và tests.

Không tự sinh bidirectional semantics vượt quá source.

---

## 6. 7 verification projections

### VP001 — Concrete Artifact Type Projection
Source:
- concrete CArtAgO Artifact implementation resolved từ project.

Target:
- USE subclass của `Artifact`.

Need:
- để model typed state/operations theo artifact concrete type.

Lossiness:
- có thể lossy nếu Java type system có construct USE không biểu diễn.

Assumption:
- class identity phải resolve chắc chắn từ project/source path/classpath.

### VP002 — Observable Property State Projection
Source:
- concrete observable property có resolved name/type.

Target:
- USE attribute trên concrete Artifact subclass.

Need:
- OCL state invariant/pre/post tự nhiên.

Fallback:
- nếu không resolve type/name thì giữ structural `ObsProperty`, không đoán attribute.

### VP003 — CArtAgO Operation Projection
Source:
- concrete Artifact operation signature.

Target:
- USE operation trên concrete Artifact subclass.

Need:
- anchor cho OCL pre/post.

Fallback:
- signature unresolved → chỉ giữ structural `AbsOperation/Operation`.

### VP004 — ExternalAction ↔ Operation Trace
Source:
- `ExternalAction.operation`.

Target:
- association structural + trace tới projected USE operation khi VP003 tồn tại.

Need:
- cross-dimensional verification Jason action ↔ CArtAgO operation.

### VP005 — ObsProperty ↔ Belief Trace
Source:
- `ObsProperty.obsproperty`.

Target:
- mapped association; optionally state attribute từ VP002.

Need:
- percept/belief consistency.

### VP006 — OGoal ↔ Goal Trace
Source:
- `OGoal.OGoalToGoal`.

Target:
- mapped association.

Need:
- organisation goal ↔ agent goal consistency.

### VP007 — Norm Preservation
Source:
- `Norm`, `Nrole`, `NMission`, attributes.

Target:
- USE class/object + associations.

Need:
- giữ normative structure.

Forbidden:
- tự động compile obligation/permission/prohibition thành OCL invariant mà không có formal translation contract.

---

## 7. Mapping audit gates

Mapping chỉ được freeze khi:
- 37/37 classes covered;
- 67/67 declared attributes covered;
- 63/63 references covered;
- 14/14 inheritance covered;
- source keys unique;
- target names unique where required;
- owner/target correct;
- multiplicity correct;
- containment correct;
- 7 projections documented;
- unresolved source facts explicitly represented;
- no ambiguous unqualified identifiers;
- JSON schema validation passes;
- Ecore fingerprint matches freeze manifest.

The plugin also checks the SHA-256 of the mapping file itself against the frozen
manifest, after schema and source-identity validation. A schema-valid target edit
(for example, changing a reference multiplicity) raises `MAPPING_HASH_MISMATCH`
and blocks transformation. Restore the frozen file, or rerun the complete mapping
audit before deliberately reconciling the manifest. This check does not authorize
automatic reconciliation. Git preserves the exact Ecore and mapping JSON bytes.

A load first reads the mapping and schema once into private byte snapshots. Only
after schema validation and mapping JSON parsing succeed does it read the freeze
manifest and Ecore once into their private snapshots. This preserves fail-fast
diagnostics: an invalid mapping schema raises `MAPPING_SCHEMA_INVALID` even when a
downstream Ecore or manifest input is unavailable. Schema validation,
source-identity parsing and fingerprint checks use those same snapshots; the
manifest is parsed once for both fingerprint checks. Replacing a path during a
load cannot authorize semantics from an earlier read using bytes from a later
read. This is per-file snapshot consistency, not an atomic filesystem transaction
across four files; inconsistent versions still fail the applicable schema,
identity or fingerprint check.

---

## 8. Evolution policy

Fingerprint mismatch:
- do not continue silently;
- classify diff;
- block breaking changes until reconciliation.

Added feature:
- generate suggestion;
- mark `REVIEW_REQUIRED`.

Removed/renamed:
- stale mapping;
- block transform.

Changed type/bounds/containment/supertype:
- breaking mapping review.

---

## 9. Output of mapping engine

Mapping engine không trực tiếp tạo runtime values.
Nó tạo `TransformationPlan` chứa:
- target class definitions;
- attributes;
- associations/compositions;
- inheritance;
- projection slots;
- trace rule IDs;
- diagnostics.

Instance transformation sẽ áp dụng plan lên project semantic model.


## Final target decision (Phase 25–26)

The D25-01 V1 decision and Phase 26 Runtime Mapping V1 audit are retained as
historical evidence. Active production uses the V2 contracts identified at the top
of this document. No general standalone launcher or NPL equivalence is implied.
