# System Architecture

> Active production baseline: frozen Metamodel V2 + Structural Mapping 2.2.0 + Runtime
> Mapping V2/schema 3.0.0. V1 material
> below is historical evidence only.

## 1. Kiến trúc logic

```text
                 +-----------------------------+
                 | JaCaMo Project              |
                 | .jcm .asl Java Moise XML   |
                 +--------------+--------------+
                                |
                                v
                 +-----------------------------+
                 | Project Discovery / Loader  |
                 +--------------+--------------+
                                |
                                v
                 +-----------------------------+
                 | Extraction Layer            |
                 | JCM | Jason | CArtAgO | Org |
                 +--------------+--------------+
                                |
                                v
                 +-----------------------------+
                 | JaCaMo Semantic Model (IR)  |
                 +--------------+--------------+
                                |
              +-----------------+------------------+
              |                                    |
              v                                    v
 +--------------------------+          +--------------------------+
 | Mapping Engine           |          | Constraint Extractor     |
 | Mapping V2               |          | + Translator             |
 +------------+-------------+          +------------+-------------+
              |                                     |
              v                                     v
 +--------------------------+          +--------------------------+
 | USE Structural Transform |          | Generated OCL Fragments  |
 +------------+-------------+          +------------+-------------+
              |                                     |
              +------------------+------------------+
                                 |
                                 v
                 +-----------------------------+
                 | USE Verification Model      |
                 | MModel + MSystemState       |
                 +--------------+--------------+
                                |
                   +------------+------------+
                   |                         |
                   v                         v
          Core/Case OCL               Runtime Adapter
                   |                         ^
                   v                         |
             OCL Evaluator             JaCaMo Runtime
                   |
                   v
          Verification Results
                   |
                   v
              UI / Reports
```

---

## 2. Architectural boundaries

### 2.1 Extraction Layer
Trách nhiệm:
- đọc file;
- parse syntax;
- resolve includes/source paths;
- giữ source locations;
- không tạo USE constructs trực tiếp.

Không được:
- gọi USE APIs;
- tự viết OCL;
- resolve ambiguity bằng name similarity không có bằng chứng.

### 2.2 Semantic Model
Trách nhiệm:
- normalized representation của project;
- typed theo JaCaMo vocabulary;
- stable IDs;
- source provenance;
- cross-file references.

Không phụ thuộc GUI.

### 2.3 Mapping Engine
Trách nhiệm:
- đọc mapping schema;
- validate fingerprint/version;
- map metamodel concepts sang target concepts;
- produce transformation plan.

Không parse `.asl`/Java/XML.

### 2.4 Constraint Translation
Trách nhiệm:
- nhận typed semantic expressions/guards/contexts;
- xác định translatable subset;
- sinh OCL AST/text;
- ghi assumption/lossiness/provenance.

Không được translate unsupported semantics bằng cách "best effort" im lặng.

### 2.5 USE Adapter
Trách nhiệm:
- tạo/update `MModel`;
- compile `.use` representation nếu dùng file path;
- tạo `MSystemState`;
- mutate object/link/value;
- invoke operation checking.

### 2.6 Runtime Adapter
Trách nhiệm:
- subscribe/poll runtime;
- normalize events;
- lookup trace;
- cập nhật state;
- trigger verification.

Không reparse toàn project trên mỗi event.

`RuntimeEventObserver` is the headless observation boundary around the ordered mutation path. `eventReceived` is
called synchronously at connector receipt and implementations must not block submission. Every received event has
one terminal lifecycle notification: `eventRejected` if it cannot enter the stream, `eventCompleted` after the
worker handles an accepted event (including mutation failure), or `eventStreamClosed` when the stream ends.
Observers discard stream-scoped timing and correlation state on close; they never mutate JaCaMo execution state.

### 2.7 UI
Trách nhiệm:
- import project;
- show status/progress;
- show trace;
- show OCL violations;
- navigate về source.

Không chứa business logic transformation.

---

## 3. Data flow types

### 3.1 Source artifact
```text
SourceFile
- path
- language/kind
- content hash
- parse diagnostics
```

### 3.2 Semantic identity
Khuyến nghị canonical key:

```text
jacamo:<projectId>:<dimension>:<kind>:<ownerPath>:<localId>
```

Ví dụ:

```text
jacamo:auction:agent:Agent:MAS:auctioneer
jacamo:auction:environment:Artifact:MAS/ws:auction1
```

The six fields are separated by `:`. `ownerPath` segments are separated by
`/`; each segment and `localId` use UTF-8 percent encoding for reserved
characters, so different source identities cannot collapse to the same ID.

### 3.3 USE identity
```text
use:<modelId>:<kind>:<qualifiedName>
```

### 3.4 Trace edge
```text
TraceEdge
- sourceId
- targetId
- ruleId
- phase
- confidence/status
- provenance
```

---

## 4. Plugin integration with USE

USE repository hiện có core/gui/assembly và plugin loading. Plugin phải:
- dùng public/stable API nếu có;
- tránh sửa `use-core` trừ khi plugin API thiếu capability bắt buộc;
- nếu phải patch core, cô lập patch và document reason;
- tham khảo `plugin_monitor` để hiểu runtime verification integration patterns;
- không phụ thuộc vào undocumented internals khi có alternative.

The release build produces a plugin JAR and ZIP. The JAR embeds the canonical
Ecore, mapping JSON/schema/freeze manifest, core OCL, plugin descriptor,
compatibility metadata, and release manifest. The ZIP also carries the source
copies, Auction example, documentation, license, and SHA-256 sidecar. The
packaging integration test compares embedded bytes with canonical sources and
loads the JAR extracted from the ZIP through the pinned USE plugin runtime.

`JaCaMoFacade` is the UI boundary for implemented import, transformation,
verification, binding, reporting, and runtime lifecycle services. The workbench
delegates these operations to the facade. Jason, CArtAgO, and Moise runtime
libraries are provided by the host classpath, as recorded in `compatibility.json`.

---

## 5. Error model

Mọi layer trả `Diagnostic` chuẩn:

```text
Diagnostic
- code
- severity: INFO | WARNING | ERROR | FATAL
- phase
- sourceLocation?
- semanticId?
- mappingRuleId?
- message
- evidence
- remediation
```

Principle:
- parse error → không fake semantic element;
- unresolved mapping → không guess;
- unsupported OCL translation → preserve source + warning;
- runtime trace miss → quarantine event + diagnostic;
- inconsistent state → report, không silently repair.

---

## 6. Determinism

Với cùng:
- project bytes;
- metamodel;
- mapping;
- plugin version;
- OCL profiles;

thì phải sinh cùng:
- semantic IDs;
- USE names;
- associations;
- trace;
- generated OCL;
- diagnostics ordering ổn định.

Điều này cần cho reproducibility của thesis.


## Final target decision (Phase 25–26)

D25-01 and the Phase 26 V1 contract are historical decisions superseded by the
active V2 migration. Current runtime target reconciliation is recorded in
[Phase 38](v2-migration/phase38-runtime-mapping.md) and mirror correctness in
[Phase 39](v2-migration/phase39-mirror-correctness.md). No general standalone
launcher or NPL equivalence is implied.
