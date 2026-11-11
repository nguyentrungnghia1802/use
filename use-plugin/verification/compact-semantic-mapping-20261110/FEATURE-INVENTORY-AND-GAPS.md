# Feature Inventory, Classification and Gap Matrix

Date: 2026-11-10
Status: P00 Baseline Inventory

## 1. Feature Classification Table

| Existing v2 XML Feature | Classification | New Authoritative Owner | Preservation Mechanism & Evidence |
|---|---|---|---|
| `<sources>` file lists & SHA-256 digests | Obsolete metadata / Auto-provenance | Engine (`MappingCompilationManifest`) | Auto-computed from selected `.mrv` and project assets at Prepare time. Provenance preserved without manual editing. |
| `<models mode="AUTHORED" ...>` | Model definition | Model Frontends (`CslFrontend`, `IStarFrontend`) | Models loaded directly from `.mrv` descriptors; SHA-256 checked during compilation. |
| `<assertions>` & `<selectors>` (XPath / Regex) | Obsolete ETL / Source Fact | Engine (`SourceSymbolCatalog`) | Replaced by direct AST parsing of JCM/ASL/XML/Java. Semantic structure verified by symbol catalog without string regexes. |
| `<datasets>` (`raw-artifacts`, `raw-properties`, etc.) | Generic engine mechanism | Engine (`ObservationPlanCompiler`) | Inferred automatically from source references and target bindings in compact mapping. |
| `<tables>` (`<where>`, `<join>`, `<column>`) | Obsolete ETL / Procedural pipeline | Engine (`RuntimeInstanceLinker` / `NativeProjectionPlanner`) | Removed from user authoring. Engine projects CSL entities and attributes directly from typed source facts. |
| `<populationClosure>` | Generic engine mechanism / Scope | Engine (`ConstraintCoverageResolver`) | Handled by generic population closure policy on admitted source scopes (e.g. CARTAGO_BOARD_INVENTORY). |
| `<selectValues>` (transitions) | Model definition / Lifecycle | Moise Adapter (`MoiseBoardSnapshotSource`) | Decoded directly from Moise scheme board transitions instead of ad-hoc table queries. |
| `<modelRules>` | Model definition | CSL Model (`CslModel`) | Entity, attribute, and context types defined natively in `.csl`. |
| `<istar>` | Model definition | iStar Model (`GoalModel`) | Actors, goals, tasks, refinements, dependencies defined natively in `.istar`. |
| `<static id="..." kind="ROLE" ...>` | Concise mapping choice | Compact XML `<csl><map .../></csl>` | Explicit correspondence between source role/context and CSL role/context. |
| `<static id="..." kind="ACTOR" ...>` | Concise mapping choice | Compact XML `<istar><map .../></istar>` | Explicit correspondence between source agent/capability and iStar actor. |
| `<actorRuntime>` (activationPolicy, episodePolicy) | Concise runtime declaration | Compact XML `<runtime><scope .../></runtime>` | Preserved as concise declarative attribute/element in `<runtime>`. |
| `<capability provider="jason.plan-boundaries.v1">` | Concise runtime declaration | Compact XML `<runtime><task .../></runtime>` or `<istar><map source="asl:.../step:..." target="..."/></istar>` | Source task AST boundaries associated directly with mapped Tasks. |
| `<capability provider="cartago.operation-history.v1">` | Concise runtime declaration | Compact XML `<csl><map source="artifact:.../op:..." target="..."/></csl>` | Operations declared on artifact correspondence. |
| `<capability provider="moise.scheme-lifecycle.v1">` | Concise runtime declaration | Compact XML `<runtime><scheme .../></runtime>` | Moise scheme binding for organizational executions. |
| `<capability provider="npl.lifecycle.v1">` | Concise runtime declaration | Compact XML `<runtime><norm .../></runtime>` | NPL norm lifecycle observation demand. |
| `<presentation milestones="DOMAIN">` | Concise runtime declaration | Compact XML `<runtime><milestone .../></runtime>` | Defines presentation cut milestones for business state browsing. |
| `<omittedConcepts>` | Obsolete metadata / Diagnostic | Documentation / Engine Decisions | Moved to compiler diagnostic catalog; not required for runtime execution. |

## 2. Gap Matrix and Architectural Components

| Component | Responsibility | Existing API / Baseline | Gap / Required Upgrade | Target Owner Class |
|---|---|---|---|---|
| **Compact XML Parser** | Parse `urn:use:jacamo-mapping:3` into immutable spec | `MappingXmlReader` (v2 only) | Needs v3 parser supporting `<csl>`, `<istar>`, `<runtime>` with source-target syntax | `CompactMappingReader` / `CompactMappingSpecification` |
| **Source Symbol Catalog** | Resolve source paths, members, operations, ASL AST steps | Ad-hoc regex/XPath selectors | Needs structured indexing of JCM, ASL (plans/steps), CArtAgO (fields/ops), Moise (roles/schemes) | `SourceSymbolCatalog` / `SourceSymbolResolver` |
| **Native Target Catalog** | Validate target CSL/iStar elements and types | `CanonicalUseBindingAdapter` / `Use75CslModelBackend` | Needs typed indexing of CSL classifiers/attributes and iStar actors/goals/tasks | `NativeTargetCatalog` |
| **Observation Plan Compiler** | Compile compact mapping into typed runtime plan | `SelectedObservationPlanner` / `GenericBindingPlan` | Must synthesize capability requests, projection bindings, and task boundaries without v2 tables | `ObservationPlanCompiler` |
| **Runtime Instance Linker** | Link runtime facts to CSL/iStar instances and scopes | `OccurrenceProjection` / `RuntimeTables` | Decouple from table queries; link facts directly to target occurrences by canonical IDs | `RuntimeInstanceLinker` / `NativeProjectionPlanner` |
| **Checkpoint Policy Adapter** | Map compact plan to checkpoint rules | `CheckpointPolicySelection` | Accept compiled plan nodes directly instead of reading XML DOM nodes | `CheckpointPolicySelection` / `ObservationCapabilityPlan` |
| **Workbench & Facade** | Integrate Prepare, Import, Start with v3 compiler | `DefaultJaCaMoFacade`, `AuthoredModelLoader` | Detect v3 namespace and route to compact compiler while preserving legacy v2 route | `DefaultJaCaMoFacade`, `GenericMappingEngine` |

