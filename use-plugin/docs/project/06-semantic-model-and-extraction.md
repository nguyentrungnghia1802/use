# JaCaMo Semantic Model and Extraction

## 1. Input contract

Input chính:
```text
<project>.jcm
```

`.jcm` là entry point.

Loader phải resolve:
- Agent configurations và `.asl`;
- workspace/artifact declarations;
- Java source/class path;
- organisation declarations và Moise XML;
- include/source-path directives;
- execution/platform parameters cần cho identity/resolution.

Không coi tất cả file là entry point ngang hàng.

---

## 2. Extraction pipeline

```text
JCM Loader
   ↓
ProjectGraph
   ├── AgentSource
   ├── ArtifactSource
   ├── OrganisationSource
   └── Include
   ↓
Dimension Parsers
   ↓
Raw Semantic Nodes
   ↓
Cross-file Resolver
   ↓
Normalized JaCaMoSemanticModel
```

---

## 3. Semantic IR

Khuyến nghị immutable/mostly immutable IR.

Root:
```text
JaCaMoSemanticModel
- projectId
- projectRoot
- mas
- diagnostics
- sourceIndex
- symbolIndex
```

Core nodes:
```text
SemanticElement
- id
- kind
- name
- sourceSpan
- provenance
- attributes
- references
```

Typed subclasses/records cho:
- MAS
- Agent
- Belief
- Rule
- Goal
- Plan
- Context
- Body
- Action
- Workspace
- Artifact
- ObsProperty
- AbsOperation
- Organisation
- Role
- Group
- Scheme
- Mission
- OGoal
- OPlan
- Norm
- remaining metamodel concepts.

---

## 4. Parser responsibilities

### JCM
Parse:
- MAS name;
- agents;
- workspaces;
- artifacts;
- organisations;
- groups/schemes;
- roles/focus references;
- source paths;
- platform/execution parameters;
- includes.

### Jason/AgentSpeak
Parse:
- initial beliefs;
- rules;
- initial goals;
- plans;
- triggering event;
- context;
- body terms;
- external/internal actions;
- messages;
- source position.

Không cần implement full Jason interpreter.

### CArtAgO
Extract:
- artifact class;
- init/constructor relevant declaration;
- observable property declarations/updates;
- operation annotations/signatures;
- guard operations;
- internal operations;
- signals/await semantics khi cần;
- parameter names/types;
- source positions.

Ưu tiên parser/AST hoặc reflection/class metadata có kiểm soát thay vì regex nếu source complexity cao.

### Moise
Parse:
- structural specification;
- roles/groups/links/formation constraints;
- functional specification;
- schemes/missions/organisational goals/plans;
- normative specification;
- norm role/mission/type/time constraint;
- instance config từ JCM.

---

## 5. Cross-file resolution

Resolver phải phân biệt:
- declaration;
- instance;
- symbolic reference;
- runtime identity.

Resolution order:
1. exact qualified identity;
2. explicit project reference;
3. semantic relation from source;
4. unique symbol in correct scope;
5. optional binding file;
6. unresolved/error.

Không dùng fuzzy name matching làm formal resolution.

---

## 6. Source provenance

Mỗi semantic element phải trace tới:
- file path;
- line/column or byte range nếu có;
- parser;
- source hash;
- original spelling.

Generated element phải trace tới source(s).

---

## 7. Error recovery

Parser có thể tiếp tục sau lỗi cục bộ để trả diagnostics, nhưng:
- element invalid không được coi valid;
- reference unresolved không được fake;
- transformation phải có policy block/warn tùy severity.

---

## 8. Cache/incremental

Sau MVP:
- hash từng source;
- chỉ reparse file đổi;
- rebuild affected symbol scopes;
- preserve stable IDs nếu semantic identity không đổi.

Runtime adapter dùng model đã resolve, không reparse project liên tục.
