# Task — Cleanup Exposed USE Attributes

> **Mục tiêu:** làm gọn Class Diagram/Object Model bằng cách chỉ giữ **domain state + verification-relevant state** trong USE.
> Metadata kỹ thuật như `semanticId`, runtime identity, source provenance, mapping/binding metadata phải chuyển xuống **Trace/Binding infrastructure**, không hiện thành `MAttribute` nếu không cần cho OCL/domain semantics.

---

# 1. Quy tắc

- [x] Đọc `docs/agent/agent.md` trước khi sửa.
- [x] Audit code hiện tại trước khi thay đổi.
- [x] Không hard-code Auction.
- [x] Không fuzzy/name-only mapping.
- [x] Không xóa metadata khỏi hệ thống nếu Trace/Runtime vẫn cần.
- [x] Chỉ bỏ metadata khỏi **exposed USE model**.
- [x] Giữ exact identity/trace cho runtime mutation và violation tracing.
- [x] Không sửa frozen metamodel/mapping chỉ để làm diagram đẹp.
- [x] UI không chứa mapping/domain logic.

---

# 2. Audit hiện trạng

## 2.1 Inventory attributes

- [x] Liệt kê toàn bộ `MClass` đang expose.
- [x] Liệt kê toàn bộ `MAttribute`.
- [x] Phân loại mỗi attribute:

```text
DOMAIN
VERIFICATION_STATE
IDENTITY_TRACE
RUNTIME_BINDING
SOURCE_PROVENANCE
DUPLICATED_RELATION
DEBUG_ONLY
UNKNOWN
```

- [x] Tạo bảng:

```text
Class | Attribute | Category | Used by OCL? | Used by runtime? | Decision
```

## 2.2 Kiểm tra usage trước khi remove

Với mỗi candidate:

- [x] search production code;
- [x] search OCL;
- [x] search tests;
- [x] search UI;
- [x] search runtime mutation;
- [x] search trace/binding;
- [x] search export/import.

Không remove nếu chưa biết consumer.

---

# 3. Projection policy

## 3.1 Chỉ expose attribute khi

- [x] là domain value;
- [x] là runtime state cần verify;
- [x] được OCL dùng;
- [x] không thể biểu diễn tốt hơn bằng association/object;
- [x] thực sự cần trong Object Diagram.

## 3.2 Technical attributes mặc định không expose

Audit và chuyển khỏi `MClass` nếu chỉ phục vụ kỹ thuật:

```text
semanticId
specSemanticId
schemeSpecSemanticId
schemeInstanceIdentity
runtimeIdentity
sourceLayer
sourceUri
stateEvidence
goalStateEvidence
artifactTypeSemanticId
creatorAgentSemanticId
workspaceSemanticId
environmentSemanticId
```

- [x] Không blacklist bằng tên trong generic core.
- [x] Rule dựa trên semantic category/provenance.

## 3.3 Metadata phải vẫn tồn tại internal

```text
MObject / semantic element
        ↓
TraceIndex / Binding
        ├─ semanticId
        ├─ runtime identity
        ├─ specification identity
        ├─ source file/span
        ├─ source layer
        ├─ mapping/projection provenance
        └─ runtime key
```

- [x] Runtime mutation vẫn exact.
- [x] Violation vẫn trace về source được.
- [x] Trace/Diagnostics UI vẫn xem được metadata.

---

# 4. Cleanup theo class

## Agent

Target:

```text
name
host   // chỉ giữ nếu thực sự cần verify
```

- [x] audit/remove exposed `semanticId`.
- [x] audit/remove exposed `sourceUri`.
- [x] giữ internal identity/trace.

## AgentGoal

Target:

```text
literal
```

- [x] remove exposed `semanticId` nếu chỉ trace.
- [x] remove exposed `sourceLayer` nếu chỉ provenance.
- [x] giữ exact trace internal.

## Belief

Target:

```text
literal
```

- [x] remove exposed `semanticId`.
- [x] remove exposed `sourceLayer`.
- [x] giữ selective Belief projection.
- [x] không mirror lại full BeliefBase.

## Organization

Target:

```text
name
```

- [x] move `semanticId` to trace.

## Group

Target:

```text
name
```

- [x] move `semanticId` to trace.

## Scheme

Target tối thiểu:

```text
name
arguments   // nếu meaningful
```

Audit/remove:

- [x] `semanticId`
- [x] `specSemanticId`
- [x] `runtimeIdentity`
- [x] `sourceLayer`
- [x] `goalStateEvidence`

Internal trace vẫn phải giữ spec identity + runtime instance identity.

## Mission

Target:

```text
id / name
min
max
```

Audit/remove:

- [x] `semanticId`
- [x] `specSemanticId`
- [x] `schemeInstanceIdentity`
- [x] `schemeSpecSemanticId`
- [x] `sourceLayer`

- [x] Mission↔Scheme, Mission↔Goal, Agent↔Mission phải dùng associations/links thay vì duplicate String ids.

## OrganizationalGoal

Target đề xuất:

```text
id
name
arguments
goalType
runtimeState
decompositionOperator
orderInParent
minAgentsToSatisfy
ttf
```

Audit/remove:

- [x] `semanticId`
- [x] `specSemanticId`
- [x] `schemeInstanceIdentity`
- [x] `schemeSpecSemanticId`
- [x] `sourceLayer`
- [x] `stateEvidence`

- [x] vẫn phân biệt đúng goal spec và runtime goal occurrence bằng Trace/Binding, không cần duplicate bằng String attrs nếu object/link identity đã đủ.

## Workspace

Target:

```text
name
```

Audit/remove nếu chỉ technical:

- [x] `semanticId`
- [x] `environmentSemanticId`
- [x] `uuid`
- [x] `fullName`

Giữ `uuid/fullName` chỉ nếu có OCL hoặc user-facing requirement thật.

## Artifact

Target:

```text
name
```

Audit/remove:

- [x] `semanticId`
- [x] `artifactTypeSemanticId`
- [x] `creatorAgentSemanticId`
- [x] `workspaceSemanticId`
- [x] `uuid`

- [x] Relations phải dùng associations khi đã có.
- [x] Không duplicate relation bằng String ids.

## Concrete Artifact subclasses

Ví dụ `AuctionArtifact`:

- [x] giữ `running`;
- [x] giữ `best_bid`;
- [x] giữ `winner` nếu có;
- [x] giữ observable domain state cần verify;
- [x] không thêm technical trace attrs.

---

# 5. Role / association-class cleanup

Audit các class như:

```text
auctioneer
participant
```

- [x] xác định exact USE representation hiện tại.
- [x] nếu association class/link đã xác định Agent + Group:
  - [x] bỏ `agentSemanticId`;
  - [x] bỏ `groupInstanceId`;
  - [x] bỏ `semanticId` nếu chỉ trace.
- [x] giữ role/group cardinality semantics.
- [x] không duplicate hai đầu relation bằng String attrs.

---

# 6. Runtime & Trace compatibility

Sau cleanup:

- [x] runtime event vẫn resolve đúng object;
- [x] create/update/remove vẫn đúng;
- [x] no bare-name fallback;
- [x] reconnect/resync vẫn đúng;
- [x] snapshot verification vẫn đúng;
- [x] pause/violation workflow vẫn trace ngược được.

Test:

- [x] Agent update;
- [x] Goal state update;
- [x] Mission commitment update;
- [x] Artifact observable update;
- [x] Scheme instance update;
- [x] object removal;
- [x] reconnect/resync;
- [x] violation → source trace.

---

# 7. OCL compatibility

- [x] Inventory OCL đang reference technical attrs.
- [x] Nếu OCL dùng `semanticId`/`sourceLayer`/identity strings:
  - [x] xác định có thật sự là domain constraint không;
  - [x] nếu không, remove/rewrite;
  - [x] nếu là relation, chuyển sang navigation qua association/object.
- [x] Không giữ technical attr chỉ để cứu OCL cũ.
- [x] Compile/type-check toàn bộ OCL sau cleanup.

---

# 8. UI cleanup

## Class Diagram / Object Diagram

- [x] Class Diagram không còn đầy technical metadata.
- [x] Object Diagram tập trung vào domain/runtime verification state.
- [x] Auction dễ đọc hơn rõ rệt.
- [x] Hello World và House Building không regression.

## Trace / Diagnostics

- [x] Trace view hiển thị `semanticId`.
- [x] Trace view hiển thị runtime identity.
- [x] Trace view hiển thị source file/span.
- [x] Diagnostics hiển thị mapping/binding ids khi lỗi.
- [x] Goal View vẫn trace source được.

Không giữ metadata trong domain class chỉ để UI đọc tiện.

---

# 9. Remove dead code

Sau migration:

- [x] xóa materialization code chỉ tạo technical attrs đã bỏ;
- [x] xóa runtime mutation code update các attrs đó;
- [x] xóa duplicated DTO fields nếu không còn consumer;
- [x] xóa UI code đọc attrs đã bỏ;
- [x] xóa stale tests/golden outputs;
- [x] không để compatibility branch production song song.

Không xóa Trace/Binding metadata còn cần.

---

# 10. Regression

## Auction

- [x] exposed classes đúng.
- [x] attribute count giảm rõ rệt.
- [x] `OrganizationalGoal` không còn metadata noise.
- [x] `Scheme`, `Mission`, `Artifact`, `Agent` gọn.
- [x] runtime update PASS.
- [x] OCL PASS.
- [x] violation trace PASS.

## Hello World

- [x] import PASS.
- [x] runtime PASS.
- [x] domain artifact attrs không bị xóa nhầm.
- [x] OCL PASS.

## House Building

- [x] dynamic artifact/org/scheme PASS.
- [x] nested Goal structure PASS.
- [x] no identity collision.
- [x] OCL/runtime PASS.

---

# 11. Final evidence

Báo cáo:

```text
BEFORE
Class → exposed attributes

AFTER
Class → exposed attributes

REMOVED FROM USE
technical attrs

PRESERVED INTERNALLY
Trace/Binding metadata

OCL IMPACT
changed constraints/navigation

RUNTIME IMPACT
tests

UI IMPACT
Class Diagram / Object Diagram / Trace

REGRESSION
Auction
Hello World
House Building
```

Ghi rõ:

```text
Removed from exposed USE != deleted from system
```

---

# 12. Definition of Done

- [x] Class Diagram chỉ còn domain + verification-relevant state.
- [x] Technical identity/provenance không còn leak thành `MAttribute` không cần thiết.
- [x] Exact trace/runtime binding vẫn đầy đủ.
- [x] OCL không phụ thuộc technical String ids nếu association/object navigation thay thế được.
- [x] Runtime synchronization không regression.
- [x] Violation source tracing không regression.
- [x] Auction diagram gọn hơn rõ ràng.
- [x] Hello World PASS.
- [x] House Building PASS.
- [x] Dead projection/runtime/UI code đã remove.
- [x] Focused tests PASS.
- [x] Full relevant regression PASS.
- [x] Docs cập nhật đúng implementation.
- [x] Không còn production dual path hoặc compatibility hack cho attrs cũ.

---

# 13. Evidence hoàn thành — 2026-10-06

Toàn bộ **148 checklist items** ở §§1–12 đã hoàn thành. Không còn blocker hoặc
item deferred trong task cleanup attributes này. Những giới hạn semantic/runtime
đã có (Norm/temporal semantics deferred, span Moise unavailable, Jason pause không
atomic với CArtAgO/Moise in-flight) vẫn được giữ, không được báo thành semantic PASS.

## Preflight / audit

- Đã đọc toàn bộ agent.md trước task.md. Branch main, HEAD e68680c5;
  git status/diff/staged/log đã kiểm tra trước sửa. Chỉ task.md đã được user thay
  specification; staged rỗng. Revision đó được giữ, không reset/discard hoặc tạo branch.
- Audit production → native state → runtime mutation → OCL → trace/binding →
  Goal/Trace UI → JSON/SOIL/export/replay/reanalysis. Không scan hoặc sửa JaCaMo core.
- [Inventory từng class/attribute, category, OCL/runtime usage và quyết định](exposed-attribute-audit.md).
  Concrete application properties được phân loại theo provenance, không blacklist tên.

## BEFORE / AFTER

| Class / kind | BEFORE exposed attributes | AFTER exposed attributes |
| --- | --- | --- |
| Agent | name, semanticId, sourceUri, host | name |
| AgentGoal | literal, semanticId, sourceLayer | literal |
| Belief | literal, semanticId, sourceLayer | literal |
| Organization / Group | name, semanticId | name |
| Scheme | name, arguments, semanticId, specSemanticId, runtimeIdentity, sourceLayer, goalStateEvidence | name, arguments |
| Mission | id, name, min, max, semanticId, specSemanticId, schemeSpecSemanticId, schemeInstanceIdentity, sourceLayer | id, name, min, max |
| OrganizationalGoal | id, name, description, arguments, goalType, runtimeState, decompositionOperator, orderInParent, minAgentsToSatisfy, ttf, semanticId, specSemanticId, schemeSpecSemanticId, schemeInstanceIdentity, sourceLayer, stateEvidence | id, name, description, arguments, goalType, runtimeState, decompositionOperator, orderInParent, minAgentsToSatisfy, ttf |
| Workspace | name, semanticId, environmentSemanticId, uuid, fullName | name |
| Artifact | name, semanticId, artifactTypeSemanticId, creatorAgentSemanticId, workspaceSemanticId, uuid | name |
| Role association classes | semanticId, agentSemanticId, groupInstanceId | no attributes; exact endpoints/context/multiplicity retained |
| Application Artifact subclasses | authored observable state | retained: AuctionArtifact best_bid/running/task/winner; GUIConsole numMsg; AuctionArt currentBid/currentWinner/maxValue/task |

Goal description remains authored domain text. There is no verified host/fullName/
UUID requirement requiring those technical fields to remain exposed. Name stays a
domain value and its existing artifact-incarnation consistency check is preserved.
Mission/Scheme/Goal/Agent relations keep native associations/links and cardinalities.

**Removed from exposed USE != deleted from system.** NativeObjectBindings replaces
the former plain exact alias map with aliases plus metadata; runtime mutation,
baseline/rollback, resync, removal and operation incarnation checks read it.
TraceIndex and internal binding metadata preserve source identity/evidence/span,
specification and runtime occurrence identities, layer, UUID/path/provider and
projection context. Snapshot 1.2.0 freezes bindingMetadata separately from attributes.
Failing violation source resolution uses that immutable failing cut, before pause.
Replay 2.0.0 includes hashed bindings.json; earlier replay schemas are rejected
explicitly. No old attribute-reading compatibility production path remains.

An application observable named semanticId, uuid, sourceLayer or runtimeIdentity
is allowed and remains independent of internal metadata (executable collision test).
Metadata is available in Trace/Diagnostics and Goal View. Official unavailable
Moise source spans remain line 0 / unavailable rather than fabricated line numbers.

## OCL / runtime / UI evidence

- Native Goal OCL uses goalScheme, missionScheme and Agent/Mission navigation,
  plus runtimeState and existing capability gates. The redundant technical
  SchemeGoalContext is removed; six domain/structural Goal invariants remain.
  Exact specification/occurrence foreign-key checks stay in projection bindings;
  foreign functional links are rejected transactionally.
- Identity-only acceptance OCL is replaced with domain checks; identity uniqueness
  is asserted from binding/snapshot identities. Auction's approved deliberately
  false CASE OCL still produces a concrete false Goal context with exact diagnostic
  targets, source traces, all-agent ACK, CONFIRMED resync, Resume and reconnect.
- AttributeProjectionCleanupTest: five tests for schema/role attributes, domain
  name collisions, snapshot/rollback/resync/removal, hashed replay metadata,
  foreign functional contexts and unchanged artifact name/UUID binding.
- Existing focused tests cover Agent/literal lifecycle, Scheme instance updates,
  Goal state, Mission commitments, Artifact observables, real PRE/POST/@pre,
  reconnect/resync, snapshot/control/violation source tracing, UI and replay.
- ObjectDiagramLifecycleEvidence renders native attributes ON and verifies every
  ObjectNode value against the active MSystemState. Each run preserves one active
  MSystem and MSystemState through all listed phases; no stale/missing graph nodes
  or links were found. Native Class Model inventory/type-check/export proves the
  diagram schema has no redundant technical MAttributes. Full graph overviews
  remain dense where the runtime actually has many relations; no links are hidden
  to falsify the complete inventory. Domain views are explicitly filtered views.

## Final case-study measurements

BEFORE is the preserved previously verified 2.1.0 export; AFTER is the final
3.0.0 reactor run. Object/link/Belief counts vary with the runtime cut; source fact
parity is checked at each acceptance cut. Attribute/class counts compare schemas.

| Case | Classes before/after | Declared attributes before/after | Inherited attributes before/after | Final objects / links | Beliefs before/after | Phases / atomic graph checks |
| --- | --- | --- | --- | --- | --- | --- |
| Auction | 17 / 17 | 67 / 27 | 88 / 33 | 36 / 92 | 0 / 0 | 8 / 138 |
| Hello World AUTO | 20 / 20 | 70 / 24 | 95 / 31 | 47 / 118 | 6 / 6 | 12 / 647 |
| Hello World FULL | 20 / 20 | 70 / 24 | 95 / 31 | 47 / 118 | 6 / 6 | 12 / 656 |
| House Building | 30 / 30 | 88 / 27 | 135 / 39 | 284 / 499 | 217 / 216 | 7 / 546 |

Final evidence directories (all include diagram-lifecycle PNG/JSON/SOIL and exact source checks):

- **Auction:** `use-plugin/target/runtime-control-acceptance/auction-1791250380988`
- **Hello World AUTO:** `use-plugin/target/hello-world-object-audit/AUTO-1791250435048`
- **Hello World FULL:** `use-plugin/target/hello-world-object-audit/FULL-1791250591526`
- **House Building:** `use-plugin/target/house-domain-live/1791250746920`

Auction confirms all five agents ACK; the immutable failing cut precedes the
pause request and differs from the paused confirmation cut. Verification remains
false → CONFIRMED. Resume uses the same control API and authoritative resync.
Hello World AUTO/FULL cover import, live state, repeated resync, reconnect,
reimport/profile reload and isolated recorded replay/reanalysis. House Building
checks dynamic artifacts/org/scheme, nested goals, exact identities, live OCL and
repeated cut/reconnect. Its bounded acceptance is not a claim that construction
has completed. Difference 217→216 Beliefs is a different live cut, not a change to
the selective Belief rule or a missing removal event.

## Commands / test results

Focused logs:

- `target/attribute-cleanup-focused-final.log`: 103 tests PASS.
- `target/attribute-cleanup-ocl-focused.log`: 16 tests PASS.
- `target/attribute-cleanup-final-binding-guard.log`: 41 tests PASS on the final guard/UI changes.

Final gate:

```powershell
Set-Location 'D:\_CODE_BANK\Project_\08_Thesis\use'
mvn -B -pl use-plugin -am verify
```

`target/attribute-cleanup-final-reactor.log`: BUILD SUCCESS, all six reactor modules,
734 tests / 171 executed suites, zero failures/errors/skips (584 unit + 150 integration).
Package, release checks, all three live cases, packaged replay, native GUI, legacy
authority exclusion and USE Shell/OCL gates PASS. Evidence counts exclude old
Surefire XML leftovers for integration classes; only the suites executed by this
specific goal/module are counted. `target/attribute-cleanup-final-evidence.json`
contains the exact report and complete before/after classifier inventory.

Final diff/status review preserves the user specification and all valid prior
changes. Only plugin production/tests/resources/docs are changed; frozen inputs,
upstream repositories and USE core/GUI implementation remain unchanged. Git stays
on main; changes are local, unstaged, uncommitted. No branch/reset/merge/push was
performed for this task.


---

# 14. Workbench UI simplification — yêu cầu tiếp theo, 2026-10-06

Yêu cầu trực tiếp mới của user thay thế phần UI của task attributes đã hoàn thành
ở §§1–13: bỏ Project / Verification / Goal View / Trace-Source / Diagnostics khỏi
Workbench, không tạo verification UI riêng. Evidence UI ở §13 là revision trước;
exact metadata/trace vẫn được giữ internal và qua backend API.

- [x] Trace dependency từng action/tab và phân biệt exclusive UI với shared/core.
- [x] Bỏ Rebuild / Export Report / Export Replay / Recorded Replay / Re-analysis / Verify imported model khỏi Workbench, cùng handlers chuyên biệt.
- [x] Bỏ năm tab và UI state/model/listener/filters/source-navigation/violation/history chỉ phục vụ chúng.
- [x] Giữ Projection Rules thành tab độc lập, ba cột Rule / JaCaMo Concept / USE Concept; toàn bộ exact catalog entries và metadata presentation được giữ.
- [x] Giữ nguyên Import JaCaMo Project / Load OCL / Start Runtime calls, lifecycle, busy/replay guards, background worker, auto-import và ready marker.
- [x] Xóa GoalViewPanel / RuntimeHistoryRows và stale UI tests; không để view/action code đã bỏ trong production package.
- [x] Giữ shared facade/CLI, official parsing/import, projection/model/native USE, identity/trace, runtime/control/OCL/snapshot/journal/replay APIs; không thay core behavior.
- [x] Focused UI + nearby facade/runtime/OCL/journal regressions PASS.
- [x] Headful workflow Import → generated active USE model → Load OCL → Start Runtime và native USE OCL/check PASS.
- [x] Full relevant reactor verify/package, Auction / Hello World AUTO+FULL / House Building và packaged/core replay regressions PASS.
- [x] Docs phản ánh UI hiện tại; review diff/status và ghi final executable evidence.


## Dependency audit / final disposition

| Removed presentation | Exclusive code removed | Shared/core kept and reason |
| --- | --- | --- |
| Rebuild / Verify imported model / Export Report | Toolbar buttons, public panel wrappers, chooser/action handlers and enablement entries | Facade atomic workspace lifecycle/full native checking; Bridge CLI uses runFullVerification and report export |
| Export Replay / Recorded Replay / Re-analysis | Workbench export/open/reanalysis choosers, wrappers, step buttons/status fields and handlers | NativeRuntimeReplay, NativeRuntimeReanalysis and NativeReplayStepController retain hash/binding validation, isolated native views, CLI/evidence and programmatic regressions |
| Project | Summary/source tables, fields and connection-action UI | Official Bridge project import/parsing, summary/source records, model generation/activation and connection/startup lifecycle |
| Verification | Result tables, counters, detail/violation panes, source/Goal navigation, UI HARD-approval and Resume handlers | Existing native OCL/evaluation/constraints, RuntimeVerificationCoordinator/ControlService, immutable snapshots/violations and policy/control APIs |
| Goal View | GoalViewPanel tree/selection/detail/navigation implementation | GoalViewSnapshot remains immutable backend data used by facade/replay and semantic acceptance |
| Trace/Source | Trace tables/models/cache/filters, clipboard/source selection/listeners/detail UI | TraceIndex, NativeObjectBindings, exact provenance/source APIs and CLI/report/runtime consumers |
| Diagnostics | Diagnostics table plus runtime/history/recorded replay views; RuntimeHistoryRows formatter/filter | Diagnostic records/provider, bridge/runtime status, journal/tail/disk persistence and shared services |
| Projection Rules | No mapping removed; the old combined cell is split into source/target columns | All 105 CodeGroundedRuleCatalog entries unchanged; fidelity/implementation/capability/policy data kept in tooltips |

Production changes for this follow-up are confined to ui/JaCaMoWorkbenchPanel.java
and ui/MappingRulesPanel.java, with ui/GoalViewPanel.java and ui/RuntimeHistoryRows.java
deleted. The preceding independent facade/projection/runtime/attribute changes are
preserved. No shared facade/core API is removed or changed in this UI refactor.

JaCaMoWorkbenchPanelTest retains import/OCL busy guards, EDT/background publication,
one-shot launcher/ready marker, runtime cached refresh, detach guards and error wrapping;
it asserts absent retired views/handlers and exact three-column catalog preservation.
The obsolete GoalVerificationWorkbenchTest is removed. RuntimeHistoryTest now tests
the raw persisted journal rather than the retired UI filter. Live CArtAgO uses native
USE OCL evaluation. GoalWorkbenchEvidence becomes WorkbenchEvidence for all cases.
StepReplayProof navigates preserved core APIs and still checks native Object Diagram,
Model Browser, invariant view and OCL dialog, exact state hashes/identity and listeners.
ManagedAuctionWorkbenchIT uses the real panel for Import → Load OCL → Start Runtime,
then checks native USE OCL and authoritative domain links on the same active Session.

## Executable evidence

- Focused command: mvn -B -pl use-plugin -am -Dtest=JaCaMoWorkbenchPanelTest,RuntimeHistoryTest,LiveCartagoNativeVerificationTest,GoalViewSnapshotTest,DefaultJaCaMoFacadeTest,HotfixLifecycleTest,ReplayStepFacadeTest -Dsurefire.failIfNoSpecifiedTests=false test
- Focused result: target/workbench-cleanup-focused-pass.log — 32 tests PASS, zero failures/errors/skips.
- Final command: mvn -B -pl use-plugin -am verify
- Final result: target/workbench-cleanup-reactor.log — BUILD SUCCESS, six reactor modules,
  722 tests / 170 executed suites, zero failures/errors/skips. Packaging, staged plugin,
  release ZIP/isolated replay, USE core/Shell, headful/native OCL/views and all three
  live case-study gates PASS. Counts come from this log's executed suite summaries,
  not stale Surefire/Failsafe XML. OCL profiles containing deliberate FAIL examples
  remain unchanged; test PASS does not claim every authored domain condition is true.
- target/workbench-cleanup-evidence.json records the final tests, paths and package checks.
- Source UI search contains no retired action/tab/handler references; package checks
  reject GoalViewPanel/RuntimeHistoryRows and all their inner classes, while proving
  required facade/projection/binding/OCL/control/Goal/replay classes remain packaged.

Final headful evidence (workbench.png plus native model/state/identity evidence):

- Auction: use-plugin/target/runtime-control-acceptance/auction-1791259339392
- Hello World AUTO: use-plugin/target/hello-world-object-audit/AUTO-1791259388832
- Hello World FULL: use-plugin/target/hello-world-object-audit/FULL-1791259514992
- House Building: use-plugin/target/house-domain-live/1791259637462
- Actual GUI workflow: use-plugin/target/workbench-acceptance/auction-1791259698526

Native Swing screenshots were visually checked: one Projection Rules tab, three
clear column headers, the three retained primary controls and existing .use/.cmd
exports. No replacement verification/Goal/diagnostic UI was created.

Current maintained docs: docs/project/12-plugin-ui-workflow.md, system architecture,
control ADR and runtime inventory. Attribute audit/evidence is preserved and marks
its preceding UI revision. All 11 follow-up items are complete; no cleanup blocker.
Git remains main with existing work preserved, staged empty, local/uncommitted changes.
No branch, reset, merge or push was performed.
