# JaCaMo Semantic Model and Extraction

> V2 migration status: [Phase 33 component contract](v2-migration/phase33-semantic-ir.md)
> supersedes the V1 inventory/root assumptions below for the new V2 IR constructor.
> Active kinds are descriptor-derived; project metadata and source facts are separate.
> Production parser migration and no-V1-leakage acceptance remain OPEN (P34).

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

Phase 2 IR hiện dùng `MetamodelKind` enum có đúng một giá trị cho mỗi EClass
trong Ecore đóng băng; `SemanticElement` là record bất biến mang kind đó,
attribute scalar có kiểu và reference giữ nguyên spelling dù chưa resolve.
`JaCaMoSemanticModel` giữ MAS, source index, symbol index (nhiều ID khi tên
trùng), diagnostics và các node theo thứ tự ID ổn định. Parser Phase 3 sẽ
tạo các node này; discovery Phase 2 chỉ xây project graph, không giả lập
semantic node cho source chưa parse.

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

Phase 3 parser strategy:
- JCM discovery/parser handles the documented declarations and execution paths;
- AgentSpeak is parsed as a source-preserving supported subset, and unsupported
  expressions remain visible through diagnostics;
- Java artifacts use the JDK compiler tree API in parse-only mode, never class
  loading or execution;
- Moise XML uses a DTD/entity-disabled DOM parser;
- resolution is exact ID, owner-qualified name, or unique typed scope only.

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

One JCM file contains one `mas` declaration. Tokens after its closing brace produce
`JCM_TRAILING_CONTENT` at the first extra token; move shared declarations to an
included project. A partial import retains elements from available sources and
reports located missing/invalid-source diagnostics, but does not count as a
successful import. Missing source contents and ambiguous targets remain unresolved.

Static import reads Java source and inspects classpath entries without initializing
project classes. Bytecode-only artifacts produce `CARTAGO_BYTECODE_UNSUPPORTED`;
they are not executed to infer source semantics. Invalid archives and default
classpath links escaping the project produce actionable diagnostics.

---

## 8. Cache/incremental

Sau MVP:
- hash từng source;
- chỉ reparse file đổi;
- rebuild affected symbol scopes;
- preserve stable IDs nếu semantic identity không đổi.

Runtime adapter dùng model đã resolve, không reparse project liên tục.

## Phase 24 guard extraction boundary

Guard extraction accepts one return statement with a whitelisted pure expression
AST. Assignments, calls and multi-statement bodies retain source provenance and
CARTAGO_GUARD_BODY_UNSUPPORTED; the constraint extractor preserves an UNSUPPORTED
record. Source import never executes project code. The second-case integration
harness separately compiles and runs its trusted checked-in fixture as test setup.
See [translation inventory](phase24-translation-multicase-evidence.md).

## Phase 27 scalar validation

Moise XML integer fields reject malformed/overflow values; Boolean fields accept
only true/false/1/0. Invalid scalars produce MOISE_ATTRIBUTE_INVALID (ERROR,
PARSING), source/semantic context and remediation, with no invented value.
StaticProjectImporterTest protects both the valid and invalid boundaries.
