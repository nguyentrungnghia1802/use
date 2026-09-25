# USE–JaCaMo Project Roadmap

> Mục tiêu: đưa dự án từ baseline hiện tại đến trạng thái **logic/coding complete**, ưu tiên để Agent tự thực hiện toàn bộ công việc kỹ thuật A-Z.  
> `task.md` sẽ mô tả chi tiết từng task; file này chỉ định hướng phase, dependency và điểm kết thúc.  
> Các việc thật sự cần người dùng cung cấp/ quyết định được dồn xuống gần cuối roadmap.

---

## Nguyên tắc thực hiện

- Agent luôn đọc code + test + tài liệu hiện tại trước khi tạo implementation mới.
- Không làm lại phần đã có nếu chỉ cần audit, formalize hoặc refactor.
- Không hard-code Auction vào core.
- Không dùng fuzzy/guessing cho semantic identity.
- Runtime core phải tách khỏi Ecore cụ thể để dễ migrate sang Metamodel V2.
- D25-01 (2026-09-19, historical): Ecore/Structural Mapping V1 từng là canonical final cho supported scope; quyết định này đã được supersede bởi migration V2 Phase 29–35.
- Runtime Mapping là layer riêng, không nhét runtime semantics vào Structural Mapping.
- Unknown/unbound runtime entity phải quarantine, không tự tạo semantic target.
- OCL chỉ mở rộng sau khi runtime mirror đã chứng minh đồng bộ đúng.
- Mỗi phase phải có test + regression + documentation synchronization.
- Agent tự tiếp tục qua các phase kỹ thuật; chỉ dừng ở phase "Human Inputs" khi thật sự cần dữ liệu/quyết định từ người dùng.

---

# PHASE 0 — Baseline Freeze

**Status:** COMPLETED

- Audit canonical Ecore.
- Freeze Structural Mapping baseline.
- Mapping schema, hashes, audit evidence.
- Documentation baseline.

**Exit:** structural source-of-truth ổn định.

---

# PHASE 1 — Plugin Skeleton

**Status:** COMPLETED

- USE plugin module.
- Plugin metadata/menu/service wiring.
- Basic build/CI.

**Exit:** plugin load được trong USE.

---

# PHASE 2 — Semantic IR & Project Discovery

**Status:** COMPLETED

- `.jcm` entry point.
- Project graph.
- Semantic IR.
- Source index / diagnostics.

**Exit:** project đa dimension biểu diễn được mà không phụ thuộc USE.

---

# PHASE 3 — Dimension Parsers

**Status:** COMPLETED

- Jason parser.
- CArtAgO extractor.
- Moise parser.
- Cross-file resolver.

**Exit:** project JaCaMo đủ semantic data để transform.

---

# PHASE 4 — Structural Mapping & USE Transformation

**Status:** COMPLETED

- Mapping loader/validator.
- TransformationPlan.
- `.use` generation.
- Direct `MModel`.
- Structural trace.

**Exit:** generated USE model compile/type-check được.

---

# PHASE 5 — Instance Materialization

**Status:** COMPLETED

- Objects.
- Attributes.
- Links.
- `.cmd`.
- Direct `MSystemState`.
- Initial state checks.

**Exit:** initial USE state khớp semantic model.

---

# PHASE 6 — Constraint/OCL Foundation

**Status:** COMPLETED

- Constraint IR.
- Supported translation.
- Core OCL.
- Case OCL loader.
- Generated OCL provenance.

**Exit:** OCL supported subset compile/check được.

---

# PHASE 7 — Trace, Resolver & Binding

**Status:** COMPLETED

- Exact owner-qualified resolution.
- TraceIndex.
- Binding schema.
- Ambiguity handling.
- Stale binding detection.

**Exit:** semantic/runtime target không bị resolve bằng guessing.

---

# PHASE 8 — Verification Service & Reporting

**Status:** COMPLETED

- Full/targeted verification.
- PRE/POST.
- Result model.
- Violation trace.
- Export/report.

**Exit:** offline positive/negative verification hoạt động.

---

# PHASE 9 — Runtime Adapter Foundation

**Status:** COMPLETED

- RuntimeConnector abstraction.
- RuntimeEvent foundation.
- Synthetic runtime stream.
- RuntimeMutationEngine.
- Ordered queue/lifecycle.

**Exit:** synthetic runtime mirror hoạt động.

---

# PHASE 10 — Live JaCaMo Connectors

**Status:** COMPLETED

- Jason connector.
- CArtAgO connector.
- Moise connector/polling where supported.
- Snapshot/reconnect/resync.

**Exit:** real JaCaMo APIs có thể cấp runtime state/events cho plugin.

---

# PHASE 11 — Runtime Verification Foundation

**Status:** COMPLETED

- Event-driven checks.
- Operation enter/exit/failure.
- PRE/POST runtime correlation.
- Runtime reports.

**Exit:** runtime violation có thể được phát hiện và trace.

---

# PHASE 12 — UI Workflow

**Status:** COMPLETED

- Import.
- Trace/diagnostics.
- Verification.
- Runtime controls.
- Binding view.

**Exit:** core workflow truy cập được từ plugin UI.

---

# PHASE 13 — Hardening

**Status:** COMPLETED

- Resilience.
- Security/path safety.
- Compatibility.
- Performance evidence.
- Regression suite.

**Exit:** repeated E2E runs ổn định.

---

# PHASE 14 — Reproducible Case Study & Release Evidence

**Status:** COMPLETED

- Auction evidence.
- `.use` / `.cmd`.
- Runtime event log.
- Verification reports.
- Release package.

**Exit:** reproducible baseline evidence tồn tại.

---

# PHASE 15 — Correctness Hotfix / Current Baseline

**Status:** COMPLETED

- Workspace/runtime lifecycle correctness.
- Binding integration correctness.
- Reconnect/reimport/profile replacement regression.
- Full test/release validation.

**Exit:** đây là baseline kỹ thuật để phát triển tiếp.

---

# PHASE 16 — JaCaMo Runtime Research Integration

**Mode:** AGENT AUTO

- Đưa runtime research của Jason/CArtAgO/Moise/JaCaMo vào repository.
- Audit lại API, version, callback, snapshot, identity và capability thực tế.
- Đối chiếu research với connector implementation hiện có.
- Tạo runtime capability matrix và evidence manifest.
- Phân loại `SUPPORTED / PARTIAL / UNSUPPORTED / DEFERRED`.

**Không làm:** sửa Ecore hoặc mở rộng OCL.

**Exit:** biết chính xác runtime JaCaMo có thể cung cấp gì.

---

# PHASE 17 — Runtime Event, Trace & Identity Hardening

**Mode:** AGENT AUTO

- Audit/finalize `RuntimeEvent` hiện có, không tạo abstraction trùng.
- Hoàn thiện event taxonomy, payload, sequence, correlation.
- Hoàn thiện `RuntimeTrace`, ordering, stream generation/boundary.
- Hoàn thiện runtime identity keys/aliases cho Jason, CArtAgO, Moise.
- Reconnect/reimport/workspace replacement/stale identity/late callback.
- Giữ layer này độc lập Ecore V1/V2.

**Exit:** runtime event + ordering + identity deterministic và reusable.

---

# PHASE 18 — Runtime Mapping Draft

**Mode:** AGENT AUTO

Tạo layer declarative:

```text
JaCaMo RuntimeEvent
→ Generic Runtime Semantic Action
→ Current Semantic/USE Binding
→ RuntimeMutation
```

Deliverables chính:

- `runtime-capabilities-v1.json`
- `runtime-mapping.schema.json`
- `jacamo-use-runtime-mapping-draft.json`
- mapping loader
- mapping validator
- compatibility audit
- tests

Generic actions nên bao phủ tối thiểu:

- state set/unset;
- relation insert/delete;
- operation enter/exit/fail;
- trace-only;
- no-mutation;
- unsupported.

**Quan trọng:** mapping ở phase này là `DRAFT_WAITING_FOR_METAMODEL_V2`, target Ecore V1 chỉ là compatibility target tạm thời.

**Exit:** runtime event→mutation semantics được formalize mà không hard-code case study.

---

# PHASE 19 — Runtime Mapping Integration & Mirror Correctness

**Mode:** AGENT AUTO

- Integrate mapping draft vào `RuntimeMutationEngine`.
- Loại bỏ semantic dispatch trùng giữa JSON và Java.
- Exact trace resolution trước mọi mutation.
- Unknown/unbound/stale target → quarantine.
- Idempotency / double-apply protection.
- Snapshot + delta + reconnect/resync correctness.
- Drift comparison giữa JaCaMo authoritative state và USE `MSystemState`.
- Export evidence khi cần:
  - `model.use`
  - `initial-state.cmd`
  - `runtime-events.json`
  - `trace.json`
  - optional `runtime-replay.cmd`

**Primary gate:**

```text
Supported JaCaMo authoritative state
==
USE mirrored state
```

với:
- zero silent drop;
- zero wrong-target mutation;
- zero unexplained drift sau full resync.

**Exit:** USE là faithful runtime mirror cho supported subset trước khi mở rộng OCL.

---

# PHASE 20 — Full JaCaMo Runtime End-to-End

**Mode:** AGENT AUTO

- Chạy project JaCaMo thực tế từ `.jcm`/launcher path phù hợp.
- Capture actual Jason + CArtAgO + Moise runtime timeline.
- Đối chiếu actual trace với runtime capability/mapping.
- Test full Agent → Artifact → Organisation interaction.
- Test startup, operation, state mutation, disconnect, reconnect, resync.
- Xử lý standalone runtime integration thay vì chỉ in-process component evidence nếu khả thi trong pinned environment.

**Exit:** từ JaCaMo project chạy thật đến USE mirror có E2E evidence.

---

# PHASE 21 — Metamodel-Decoupling & V2 Migration Readiness

**Status:** COMPLETED — see [migration readiness evidence](phase21-migration-readiness.md).

**Mode:** AGENT AUTO

Mục tiêu: không để việc chờ Ecore mới chặn runtime.

- Loại bỏ dependency trực tiếp không cần thiết từ runtime core tới V1 `EClass/EAttribute`.
- Runtime core chỉ phụ thuộc generic runtime concepts + trace/mapping interfaces.
- Tách Ecore-specific binding adapter.
- Tạo migration/diff tooling cho future Metamodel V2.
- Xác định chính xác phần nào V2 sẽ tác động:
  - semantic kinds;
  - structural mapping;
  - projections;
  - generated `.use/.cmd`;
  - OCL bindings;
  - runtime semantic binding.
- Viết compatibility tests để chứng minh connectors/queue/trace/mutation mechanics không phải viết lại khi đổi metamodel.

**Exit:** khi V2 đến, chỉ semantic/binding layer cần reconcile thay vì viết lại runtime pipeline.

---

# PHASE 22 — Runtime Verification Completion

**Status:** COMPLETED for supported mirror subset — [evidence](phase22-verification-evidence.md).

**Mode:** AGENT AUTO

Chỉ bắt đầu sau khi Phase 19 mirror correctness pass.

- Chuẩn hóa verification checkpoints:
  - `SNAPSHOT`
  - `AFTER_MUTATION`
  - `OPERATION_PRE`
  - `OPERATION_POST`
  - `STREAM_BOUNDARY`
- Hoàn thiện PRE/POST với exact runtime correlation.
- Runtime invariant evaluation.
- Runtime ordering/history verification nếu representation hiện tại hỗ trợ.
- Violation attribution:
  - event;
  - runtime identity;
  - SemanticId;
  - USE target;
  - source span.
- PASS / FAIL / ERROR / SKIPPED phải tách rõ.

**Exit:** OCL/runtime verification chạy trên một mirror đã được chứng minh đúng.

---

# PHASE 23 — Cross-Dimensional & Supported Normative Verification

**Status:** COMPLETED for exact source-binding/OE evidence subset — [evidence](phase23-cross-dimensional-evidence.md).

**Mode:** AGENT AUTO

- Audit các relation Agent ↔ Environment ↔ Organisation.
- Chỉ implement rule có source/runtime evidence rõ.
- Agent ↔ Artifact / Workspace.
- ExternalAction ↔ Artifact Operation.
- ObsProperty ↔ Belief khi binding/evidence đầy đủ.
- Role/Agent, Mission/Goal state where supported.
- Audit Moise runtime normative capability.
- Implement supported exact subset.
- Phần không đủ evidence phải explicit `UNSUPPORTED`, không fabricate deontic lifecycle.

**Exit:** cross-dimensional verification có generic evidence; normative boundary rõ ràng.

---

# PHASE 24 — Constraint Translation Closure & Multi-Case Validation

**Mode:** AGENT AUTO — implemented and regression-validated (2026-09-19)

- Audit toàn bộ translator hiện có.
- Hoàn thiện `EXACT` và approved-safe `SOUND_SUBSET`.
- Unsupported/lossy semantics phải được preserve + diagnostic.
- Không reverse-engineer arbitrary Java thành OCL.
- Chọn hoặc chuẩn bị Case Study #2 từ nguồn JaCaMo/public fixture phù hợp nếu không cần user-specific domain input.
- Chạy Auction + Case Study #2.
- Kiểm tra core không có Auction-specific branch.
- Performance/reproducibility evidence.

**Exit:** PASS for Auction + local Counter Team in-process supported subsets;
full reactor 298/298 PASS. No SOUND_SUBSET is enabled without a preservation contract.
See [Phase 24 evidence](phase24-translation-multicase-evidence.md). This does not
close Phase 20 standalone launcher limitations or Phase 25 research inputs.

---

# PHASE 25 — Human Inputs / Research Decisions

**Mode:** USER INPUT ONLY WHEN NECESSARY

Mọi việc có thể tự động phải hoàn thành trước khi đến phase này.

Chỉ gom những input thật sự Agent không thể tự tạo một cách đúng đắn, ví dụ:

1. Cung cấp/confirm **Metamodel V2** nếu đây là artifact bắt buộc từ phía nghiên cứu.
2. Xác nhận V2 có thay thế V1 làm canonical baseline hay chỉ là verification profile/metamodel phụ.
3. Xác nhận các semantics nghiên cứu không thể suy ra từ source/runtime evidence nếu thesis bắt buộc phải chọn một interpretation.
4. Cung cấp/approve Case Study #2 nếu bắt buộc dùng một project do người dùng chỉ định.
5. Approve bất kỳ LOSSY/normative semantics nào nếu muốn đưa chúng vào final supported scope.
6. Confirm final thesis scope nếu còn lựa chọn research-level chưa thể tự quyết.

Agent phải chuẩn bị trước:
- diff;
- recommendation;
- impact;
- affected files;
- migration plan;
- tests cần chạy.

**Exit:** tất cả external/human inputs cần thiết đã có.

---

# PHASE 26 — Metamodel V2 Reconciliation & Final Runtime Mapping

**Mode:** AGENT AUTO AFTER PHASE 25 INPUT

Nếu Metamodel V2 được cung cấp:

- Import/audit V2.
- Structural diff V1 → V2.
- Tạo Mapping V2.
- Update semantic kinds/parsers/resolver only where required.
- Reconcile projections.
- Regenerate `.use` / `.cmd`.
- Migrate trace bindings.
- Rebind Runtime Mapping draft sang V2.
- Chạy full runtime compatibility audit.
- Chạy full mirror-correctness gate.
- Chạy OCL compile/runtime verification regression.
- Freeze final Runtime Mapping only sau khi target metamodel ổn định.

Nếu V2 không thay thế V1:
- giữ V1 canonical;
- document decision;
- freeze runtime mapping against approved target.

**Exit:** final metamodel + structural mapping + runtime mapping thống nhất.

---

# PHASE 27 — Final Engineering Hardening

**Mode:** AGENT AUTO

- Requirement → code → test → evidence traceability audit.
- TODO/FIXME/temporary/duplicate logic audit.
- Runtime lifecycle/resource leak audit.
- Determinism audit.
- Diagnostics audit.
- Security audit.
- Full synthetic runtime regression.
- Full live runtime regression.
- Multi-case E2E.
- Clean checkout / relocated build.
- Package/plugin load.
- Reproducibility.
- Documentation synchronization.

**Exit:** không còn known in-scope correctness/coding gap.

---

# PHASE 28 — Final Evidence & Project Closure

**Mode:** AGENT AUTO, USER ONLY CONFIRMS FINAL ACCEPTANCE

Agent tạo:

- final acceptance matrix;
- supported/unsupported capability matrix;
- full test results;
- runtime mapping audit/freeze evidence;
- metamodel/mapping hashes;
- Auction + Case Study #2 evidence;
- generated `.use/.cmd`;
- runtime event traces;
- verification reports;
- known limitations;
- final package;
- thesis/demo evidence bundle.

Final status cho từng capability chỉ được là:

```text
COMPLETE
SUPPORTED_SUBSET_COMPLETE
EXPLICITLY_UNSUPPORTED
OUT_OF_SCOPE
```

Không để trạng thái mơ hồ kiểu:
- "probably works";
- "partial nhưng không rõ";
- "roadmap nói là có".

**Final exit:**

```text
CORE LOGIC / CODING COMPLETE
```

sau khi full regression pass và người dùng xác nhận final acceptance.

---

# Global Dependency

```text
Phase 0–15
Existing completed baseline
        ↓
Phase 16
Runtime research evidence
        ↓
Phase 17
Runtime event / trace / identity
        ↓
Phase 18
Runtime Mapping Draft
        ↓
Phase 19
Mirror correctness
        ↓
Phase 20
Full JaCaMo E2E
        ↓
Phase 21
Metamodel-decoupling / V2 readiness
        ↓
Phase 22
Runtime verification completion
        ↓
Phase 23
Cross-dimensional + normative supported subset
        ↓
Phase 24
Translation closure + multi-case
        ↓
Phase 25
Human inputs collected once, near the end
        ↓
Phase 26
V2 reconciliation + final Runtime Mapping
        ↓
Phase 27
Final hardening
        ↓
Phase 28
Evidence + closure
```

---

# Agent Execution Rule

Agent phải tự chạy liên tục từ phase hiện tại tới Phase 24 mà không yêu cầu người dùng xử lý các quyết định kỹ thuật thông thường.

Nếu gặp vấn đề chưa đủ evidence:

```text
1. research;
2. audit source/code/tests;
3. chọn conservative safe behavior;
4. mark unsupported/deferred khi cần;
5. tiếp tục các phần độc lập;
6. gom đúng phần bắt buộc người dùng quyết định vào Phase 25.
```

Không được dừng sớm chỉ vì:
- Ecore V2 chưa có;
- một semantic capability chưa support;
- một case-study rule chưa đủ evidence;
- một optional research feature chưa thể quyết định.

Mục tiêu của roadmap này là tối đa hóa phần Agent có thể hoàn thành trước khi cần người dùng can thiệp.

## Phase 18-20 execution scope (2026-09-19)

Runtime Mapping Draft is integrated; the supported mirror subset has executable
zero-drift evidence. Full-project Phase 20 uses its documented technical-limitation
exit alternative. The pinned launcher probe exposes .jcm syntax and Moise OS schema
gaps in the static fixture. See [phase20-runtime-evidence.md](phase20-runtime-evidence.md).
This does not claim full autonomous Agent -> Artifact -> Organisation E2E.

Continuation verification: Phase 18 draft and Phase 19 supported mirror pass the
285-test reactor gate. Phase 20's namespace-only repair still fails the pinned XSD;
a non-equivalent OSBuilder control starts. Remaining full-project work is fixture
semantic reconciliation and an adapter from launcher `ora4mas.nopl.oe` board state
to the observation pipeline. It is not established to require a runtime upgrade.


## Phase 25–26 disposition (2026-09-19)

D25-01 selects unchanged canonical V1; no required V2 target was found.
Phase 18/19 draft wording above is historical. Phase 26 promotes the supported
runtime contract to mapping V1/schema 2.0.0 with explicit freeze and compatibility
checks; see phase26-runtime-mapping-audit.md. No standalone/NPL support promotion.

## Current engineering closure

See [Phase 27 hardening](phase27-hardening-audit.md) and the
[final acceptance matrix](phase28-project-closure.md) for current scope and evidence.
Earlier phase test totals and draft/temporary-target descriptions are historical.
That statement describes the historical Phase 28 V1 closure. Current execution is
tracked in `docs/agent/task.md`; V2 is the active working baseline and remains
unfrozen until Phase 44. Final user acceptance remains separate from autonomous
engineering verification.

## Phase 27–28 final engineering disposition (2026-09-20)

Phase 27 implementation and clean/relocated reactors passed 307/307 and were
already integrated on main at 8981723e. Final post-merge module verification
passed 164/164, zero failures/errors/skips, with unchanged executable inputs.
Phase 28 acceptance matrix, durable evidence bundles, boundary report and
engineering checklist are complete; see phase28-project-closure.md.
At that historical checkpoint final user confirmation was pending; the
CORE LOGIC / CODING COMPLETE label was reserved until confirmation. Standalone JaCaMo
mirror E2E and NPL lifecycle remain unsupported/unproven as documented in Phase 20.

## Final completeness reconciliation (2026-09-20)

Phase 20 P20.2–P20.5 now have standalone control SUPPORTED_SUBSET_COMPLETE
status, including real AgentSpeak role/mission/goal, artifact operation lifecycle,
exact shared mirror sync and zero unexplained post-resync drift. Direct board
observation closes the earlier engineering adapter gap. Older component-only
and adapter-gap statements above are historical. Original Auction plan/deadline
semantics and general NPL lifecycle remain EXPLICITLY_UNSUPPORTED.
See [final audit](phase20-final-completeness-audit.md). Final user acceptance
was confirmed on 2026-09-20; Phases 16–28 are closed within documented boundaries.

## Final user acceptance and project closure (2026-09-20)

The user explicitly confirmed FINAL USER ACCEPTANCE of the current implementation,
final acceptance matrix, supported capabilities, SUPPORTED_SUBSET_COMPLETE and
EXPLICITLY_UNSUPPORTED boundaries, original Auction technical limitations and
all final evidence. Project status: **CORE LOGIC / CODING COMPLETE**.
Roadmap **Phase 16 → 28 is CLOSED** under those exact dispositions.
No actionable engineering or user-acceptance task remains (A=0, B=2, C=29, D=0).
B boundaries and C execution templates remain intentionally unchecked.

This closure changes documentation/acceptance metadata only. Existing focused
33/33, clean reactor 309/309, relocated reactor 309/309, post-merge module 166/166,
and both positive/negative standalone controls remain the final test evidence;
all suites had zero failures/errors/skips. No tests were rerun or relabelled fresh.
Executable/test/build inputs and evidence archives remain unchanged. See
[evidence/final-completeness/user-acceptance.json](evidence/final-completeness/user-acceptance.json)
for the user authorization, accepted revision and closure scope. Acceptance does
not turn original Auction full semantics or any unsupported capability into PASS.
