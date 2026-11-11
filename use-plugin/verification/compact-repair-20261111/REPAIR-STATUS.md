# Compact mapping continuation: verified repairs and remaining work

Date: 2026-11-11. This is a partial implementation checkpoint, not migration acceptance.

Latest source/provider/native-workspace/replay progress and remaining integration:
[CONTINUATION-STATUS.md](CONTINUATION-STATUS.md). The earlier receipts and tables
below describe the first repair checkpoint and are retained as historical evidence.

## Baseline and ownership

- USE: `main`, `9b72a95c9718cadc0873129dd3b6f8974fa18c5e`.
- JaCaMo sibling: `main`, `fd3a46767cd12aaf52327938ccfd07c60b63282d`.
- Existing tracked edits, new compact sources, test fixtures, journals and earlier evidence were retained. `baseline-status.txt`, `baseline-tracked.patch`, `baseline-source/compact/` and `baseline-tests/` preserve the inspected starting point.
- The starting continuation did not compile. `baseline-tests.log` records production compilation failures, including APIs that do not exist in the selected native engine. Older test receipts are not acceptance of that source revision.
- No business ASL, authored CSL/iStar/OCL, native semantic assertions, barrier implementation or UI was changed in this continuation. No commit, push, branch cleanup, desktop operation or package installation was performed.

JaCaMo still owns execution/admission. The native USE writer and native session/checkpoint service remain the state/evaluation/release authorities. These repairs add no second MAS interpreter, competing MSystem, alternative iStar recurrence, replacement clock or network wait under execution locks. No new whole-platform suspension guarantee is asserted.

## Repairs implemented

| Area | Result and limits |
|---|---|
| API compatibility | Compact code now compiles against the actual model, mapping, Port and native OCL APIs. The selected reactor test build compiles production and test sources. |
| Source resolution | Unknown sources, ambiguous ASL paths/plans, invalid ranges, conflicting symbols and wrong operation overloads are rejected. An argument-bearing selector cannot fall back to an unrelated plan with the same functor. An exact JCM agent declaration takes precedence over a same-named program shorthand; the qualified program path remains independently selectable. |
| Copied field interface | `DeclaredArtifactTypeProof` copies sorted, non-static declared field names/types into source-qualified evidence. It does not initialize the class, read an instance field or grant reflection access. The consumer indexes those copied facts and validates their class hash/resource ownership; it no longer scans constant pools or opens class files to fabricate properties/operations. |
| Parser | The JDK SAX provider is pinned; DTD/external entity/schema/XInclude access is disabled. Duplicate sections/runtime declarations/parameters, unsupported attributes, text payloads and unsafe ASL paths are rejected. Required protection failures are not swallowed. |
| Static mapping | The compact route no longer assigns the first available entity/group/role as a source or native receiver. Unmapped declarations stay definitions. Required receiver bindings are explicit and typed. Ambiguous static program/group identities fail instead of picking the first candidate. |
| Fingerprints | Canonical structured hashing replaces order-sensitive string/map concatenation. Actual native model fingerprints, copied symbol signatures/digests, runtime declarations, selected additional OCL identity, requests and checkpoint rules contribute to identity. |
| Native projection | Compact artifact/property values flow through `NativeRuntimeMutationEngine.applyMappingCut`, the existing Port, semantic alias index and savepoint. Canonical artifact/workspace crosswalks and program/run/generation/physical identities prevent same-name cross-workspace mixing. |
| Invalid values | Invalid booleans, fractional/overflowing integers, non-finite numbers and arbitrary object stringification do not become valid native values. Actual runtime observable-property projection uses the existing strict typed codec. |
| Lifecycle | Transport `COMPLETE` does not by itself authorize deleting unobserved objects. Removal requires a declared source inventory cut or exact disposal identity. Event/cut epochs must match. Partial source values become undefined with unavailable metadata. |
| Atomicity | Native-writer tests prove rollback of earlier writes, new objects, aliases and metadata after a later row failure. The separate component transaction utility also restores original object identities, both link endpoints, links and attributes; wrong-owner/stale undo is rejected. That utility is not the live runtime owner. |
| Evaluation | The independent receipt-based Goal/Task evaluator was removed. An absent/successful receipt does not independently establish an authored Task postcondition or authorize APPROVE. Component invariant inspection always retains unverified release authority without the native checkpoint inventory. |
| Admission | Original authored native contract installation is attempted on the existing unpublished native model. Unsupported observer contexts are reported, not fabricated from case names. Missing scope/Task integration rejects compact preparation before publishing readiness. Requiring every supporting Goal/Task automatically has also been removed: explicit native requirement selection is still a pending integration gate. |

## Necessary source/model/choice/mechanism inventory

`active-bundle-vocabulary.json` records every current example `.mrv`, its selected mapping version and the complete XML element/attribute vocabulary. Five descriptors were found: Auction, Auction False, Hello World, House Building and the authored counter reference. All still explicitly select version 2 mappings. Draft compact files beside them do not constitute migration.

| Feature | Classification | Authoritative owner / required replacement |
|---|---|---|
| Entity/Role/context declarations and domain relations | Model definition | Actual native CSL. Source correspondence must not invent associations or derive role adoption from containment. |
| Actor kinds, Goal/Task graph, refinements and dependencies | Model definition | Actual native iStar. `participates-in` is not an achievement dependency. |
| Native predicates, invariants and operation PRE/POST | Model definition | Selected CSL/iStar annotations plus selected additional OCL, compiled by the original native backend. |
| Artifact/property/field/operation/agent/OS symbols | Source facts | Typed producer contracts and source-qualified schema evidence. Field schema is copied; values require a distinct admitted runtime receipt. |
| Canonical physical identity, incarnation, invocation | Source facts plus engine mechanism | Bridge identities and native semantic index. Business IDs, agent names and display State numbers cannot provide these keys. |
| Role compatibility and current adoption | Source facts plus native constraints | Frozen MOISE/CSL definitions, exact current group roster/enactments and native helper coverage. Role definition is not an active role occurrence. |
| Capability/physical Actor views and activation episodes | Concise semantic choice | Explicit typed actor/runtime declarations; exact source evidence must establish each occurrence. Shared ASL programs must retain all physical players. |
| Initial requests before scheme/artifact creation | Source facts plus concise scope choice | Actual Jason initial intention/invocation and source-confirmed creation references. Never pair by matching IDs or timestamps. |
| Source Task partitions | Concise semantic choice | Admitted AST regions/anchors. Producer request compiler must pin the actual source digest/path/plan/context and preserve unmapped trace-only regions. |
| Task `self`, parameters and callback tuple | Concise binding plus generic mechanism | Typed native receiver and exact source variable/action evidence; existing native PRE journal and evaluation lane. |
| Resources | Model plus concise identity binding | Original native needed-by graph, source-confirmed current resource identity/context and per-invocation evidence. |
| Dependency population ANY/ALL and same-player views | Concise semantic choice | Explicit declared populations/views and the original occurrence expansion/propagation engine. Never aggregate ANY across unrelated scopes. |
| Selected intentional requirements | Concise semantic choice | Explicit required roots per supported scope. Mapping every leaf does not make every supporting leaf a system requirement. |
| Source inventories and observation freshness | Source facts plus generic mechanism | Producer capture declarations, qualified source cut/watermarks and native coverage resolver. A filtered/query result is not a completeness certificate. |
| Ordering/deadline/source observers | Concise observation binding plus generic mechanism | Typed source lifecycle/clock adapters and declared observer schemas. No source-specific observer class/field recipe in production Java. |
| Handwritten tables/joins/select/derive pipelines | Existing technical configuration | Replace with typed plan nodes derived from admitted sources/models/concise choices; never load them behind compact XML. |
| Copied literal hashes/counts/known outcomes | Obsolete or audit-only metadata | Compute admission identities mechanically. Preserve source evidence in immutable complements; never use expected counts/outcomes as runtime truth. |

Archived review evidence in `../compact-review-20261111/` is useful for bounded bug reproductions, not for claiming current end-to-end acceptance. `../compact-auction/` contains earlier receipts; its old verdicts have not been adopted as the oracle for these repairs. No archive/replay/log was deleted or interpreted as new live evidence.

## Verification

The final focused repair command is in `focused-final2.log`. It passed **54 tests**, with no failures/errors/skips: 47 plugin tests and 7 producer adapter tests. Machine-readable counts are in `focused-final-results.json`.

It covers the compact parser/catalog/compiler components, strict conversions, native projection/identity/partial data/disposal/epoch fencing/rollback, explicit refusal of unimplemented compact runtime admission, producer field-interface extraction and the existing version 2 mapping/native writer regressions. DSL predicate bodies were not changed to satisfy tests. One old helper test now correctly requires HOLD_UNVERIFIED after passing invariants without a native inventory, while independently asserting that the authored invariant still evaluates PASS.

This evidence is **source/component/native-writer testing**. It is not a live MAS run, desktop/offscreen Swing acceptance, identical-cut old/new semantic equivalence, or proof that a final installed binary was loaded. `target/new` has not been replaced by this partial implementation.

The two old workflow/example acceptance classes are audited separately in `acceptance-audit-final.log`; they must not be conflated with the 54 repair tests. Their incomplete fixture/case expectations are retained rather than disabled or rewritten to conceal unfinished migration. Earlier `acceptance-audit.log` is an intermediate receipt, including the declaration/program ambiguity repaired afterward.

The final acceptance audit ran 8 tests: 4 passed and 4 errored. The current errors are a mapped Task fixture without authored PRE/POST, an Auction fixture whose selected plan signature does not exist in its supplied catalogue, and two draft cases with unversioned capability providers. `acceptance-audit-results.json` records each exact diagnostic. These are open acceptance failures, not suppressed results. The installed plugin hash still matches the pre-repair inspection (`dd1ea89bec8345f00cc28160e0990761ea2180b7885802b5d2dead38bb45a381`); `package-status.json` explicitly records that no repaired package or fresh class-loading/live proof was delivered.

## Remaining implementation gates

| Gate | Concrete remaining work / implementation owner |
|---|---|
| P01/P02: complete declaration vocabulary and catalogue coverage | Finalize source references for deployment/workspaces/identity metadata/missions/initial requests and concise requirement/scope/resource bindings. Add a supported static/producer interface for not-yet-created observable properties; do not restore string-constant or intrinsic-property guesses. Implement exact source-program template aliases across multiple agent declarations without collapsing players or selecting one record. |
| P03: provider-accepted capability plan | Replace the current draft request synthesis in `ObservationPlanCompiler`. Jason requests still need sourceHash/sourcePath/context/full capture fields and valid source partitions. Field requests need the provider's exact per-field type/hash/property-interface contract rather than a grouped `fields` approximation. Scheme requests need admitted organisation/scheme/source identities. Prove each generated request with the existing actual decoder/provider, not merely provider-name/count assertions. |
| P04: source-qualified scopes and demands | Implement `CompactRuntimeStateMapping.occurrences/taskDemands` from admitted physical actor/enactment/commitment/invocation evidence and native metadata. Include initial-request-to-scheme linkage, group/player sharing, exact resources and role interval changes. They currently raise explicit unsupported-integration diagnostics. |
| P05: native session | Refactor the typed callback/binding interface used by `XmlTaskOclBindings` and `XmlTaskCheckpoints` so compact IR can reuse the existing native PRE/POST journal, original occurrence expansion, `RuntimeEngineSessionOwner`, explicit requirement inventory and exact checkpoint service. Do not manufacture v2 DOM nodes or add another evaluator. Connect authored ANY/ALL and same-scope/player Goal realizations. |
| P05: typed observer views | Compile explicit typed observation views from admitted definitions/bindings for native observer contexts. `CompactNativePreparation` currently rejects unknown contexts instead of guessing an Auction/House observer class, field types or operation signatures. |
| P06/P07 | Wire the completed typed plan into Prepare/Import/Start/history/UI, then migrate all required active bundles and manifests. Preserve source business behavior and model assertions. Prove fresh-source preparation and identical frozen-cut evidence comparisons with old XML inaccessible. |
| P08/P09 | Only after those gates: bounded external Auction live acceptance, coherent reactor package build/validated installation, actual loaded JaCaMo/JAR hashes and final runnable path. No clean/full verify. No Computer Use without approval. Commit/push only when requested. |

The root checklist remains open. This continuation repairs unsafe partial work and records a reproducible implementation boundary; it does not declare P01–P09 complete.
