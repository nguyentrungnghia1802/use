# USE-JaCaMo Plugin — Task Plan từ Phase 29

> **Mục tiêu:** chuyển dự án hiện tại sang **Metamodel V2 + Mapping V2** làm baseline phát triển chính, đồng thời giữ lại tối đa hạ tầng đã được kiểm chứng: import framework, USE adapter, trace/binding infrastructure, runtime connectors, RuntimeEvent/RuntimeTrace, synchronization, verification engine, UI/reporting, build/test infrastructure.
>
> **Trạng thái V2:** `WORKING_BASELINE`, chưa `FROZEN`. Cho phép thay đổi nhỏ trong quá trình phát triển, nhưng mọi thay đổi phải đi qua diff → impact analysis → migration → regression; không sửa hash/fixture chỉ để làm test pass.
>
> **Active source locations do người dùng chỉ định**
>
> - Metamodel V2: `D:\_CODE_BANK\Project_\08_Thesis\use\use-plugin\Core\Metamodel\version-2`
> - Mapping V2: `D:\_CODE_BANK\Project_\08_Thesis\use\use-plugin\Core\Mapping\version-2`
>
> **Assumption cho kế hoạch này:** V2 thay thế V1 trong active development. V1 chỉ giữ làm historical/reproducibility baseline, không duy trì hai pipeline production song song trừ khi có yêu cầu riêng.

---

> **2026-09-24 final consumer gate:** clean reactor **350/350 PASS**, Python **11/11 PASS**. All component and downstream integration gates in Phase 29–35 now pass. Earlier counts/OPEN paragraphs below are historical migration snapshots, superseded by [final acceptance](../project/v2-migration/phase35-acceptance.md) and its per-test evidence. Phase branches are merged and pushed; post-merge full regression also PASS 350/350. Phase 29–35 are DONE; no final V2 freeze/release is claimed.

# Global Rules cho Phase 29+

## Source of Truth mới

Trong active V2 development, ưu tiên:

1. `Core/Metamodel/version-2/**`
2. `Core/Mapping/version-2/**`
3. specification/audit đi kèm V2;
4. `docs/project/**` đã cập nhật cho V2;
5. runtime capability evidence của Jason/CArtAgO/Moise;
6. production code;
7. historical V1 artifacts chỉ dùng để diff/migration/reproducibility.

Nếu code hiện tại mâu thuẫn V2, **sửa code**, không sửa V2 để chiều code cũ.

Nếu Mapping V2 mâu thuẫn Metamodel V2, **dừng transformation**, tạo diagnostic/audit issue; không tự đoán semantics.

## Invariants bắt buộc

- [x] JaCaMo vẫn là execution engine; USE là verification mirror.
- [x] Structural Mapping và Runtime Mapping vẫn là hai tầng riêng.
- [x] Parser không tạo USE construct trực tiếp.
- [x] Runtime connector không phụ thuộc tên EClass cụ thể của V2 nếu không bắt buộc.
- [x] RuntimeEvent/RuntimeTrace không hard-code case study.
- [x] Mọi runtime mutation phải qua exact identity/trace.
- [x] Không dùng fuzzy/name similarity làm formal mapping.
- [x] Không auto-create semantic object chỉ vì runtime xuất hiện tên gần giống.
- [x] Không auto-convert Moise Norm thành OCL nếu chưa có translation contract.
- [x] Không duplicate structural constraints bằng OCL nếu USE structure đã kiểm được.
- [x] Không giữ hard-coded V1 counts (`37/67/63/14`) trong production logic.
- [x] Không sửa generated output thủ công.
- [x] Mọi V2.x change phải có impact report trước khi regenerate/freeze.
- [x] Không đánh dấu V2 `FROZEN` cho tới Phase 44.

## Definition of Done chung cho một task

Một task chỉ được `[x]` khi:

- implementation hoặc audit output đã hoàn thành;
- test mục tiêu PASS;
- regression liên quan PASS;
- không còn silent fallback;
- diagnostics/provenance được cập nhật nếu contract đổi;
- docs liên quan được sync;
- diff đã review;
- không còn dependency V1 ngoài danh sách historical/compatibility được phép.

---

# Phase 29 — V2 Takeover & Migration Baseline

**DONE — 2026-09-24.** Component, downstream integration, clean full regression, merge, post-merge regression and push gates PASS. [Acceptance](../project/v2-migration/phase35-acceptance.md). Older progress snapshots below are historical.

**Objective:** chuyển source-of-truth active từ V1 sang V2 một cách có kiểm soát trước khi sửa sâu production code.

> Execution update 2026-09-23: P29.1/P29.2 intake captured; Phase 29 remains OPEN. Fresh baseline: 306 tests, 3 failures, 73 errors, zero skips. See [baseline](../project/v2-migration/phase29-pre-migration-baseline.md), [inventory](../project/v2-migration/v2-input-inventory.md), and [gate dependency decision](../project/v2-migration/phase29-gate-dependency.md). User authorized staged Phase 29–35 migration: downstream-dependent gates remain OPEN until their actual regressions pass. No rollback to V1; no production tasks moved into Phase 29.

## P29.1 — Capture repository baseline trước migration

### Tasks

- [x] Ghi `git status`.
- [x] Ghi current branch.
- [x] Ghi current HEAD.
- [x] Ghi full test count hiện tại.
- [x] Ghi current plugin/version/compatibility pins.
- [x] Ghi hash của V1 Ecore/Mapping đang dùng.
- [x] Lưu danh sách canonical resources đang được package trong plugin.
- [x] Lưu baseline Auction + Case Study #2 expected outputs.
- [x] Không chỉnh V1 historical artifacts trong task này.

### Evidence

- [x] `docs/project/v2-migration/phase29-pre-migration-baseline.md`.

---

## P29.2 — Inventory toàn bộ V2 input

### Read first

- Chỉ đọc:
  - `Core/Metamodel/version-2/**`
  - `Core/Mapping/version-2/**`

### Tasks

- [x] Liệt kê tất cả file V2.
- [x] Xác định file Ecore canonical chính.
- [x] Xác định mapping JSON canonical chính.
- [x] Xác định schema Mapping V2.
- [x] Xác định audit/manifest/hash/provenance file nếu đã có.
- [x] Xác định namespace/package/version metadata.
- [x] Xác định file nào là source-of-truth, file nào là generated/reference.
- [x] Phát hiện duplicate/obsolete V2 files.
- [x] Không suy file canonical chỉ từ filename nếu trong folder có nhiều candidate.

### Output

- [x] `docs/project/v2-migration/v2-input-inventory.md`.

---

## P29.3 — Define V1/V2 active-baseline policy

Executable selection gates below now PASS after P32.2/P35.1–P35.7; policy is recorded in `v2-migration/active-baseline-policy.md`.

### Tasks

- [x] V2 = `WORKING_BASELINE`.
- [x] V1 = `HISTORICAL_BASELINE`.
- [x] Production import/transformation mặc định dùng V2.
- [x] V1 chỉ được load qua explicit compatibility/test path nếu còn cần.
- [x] Không có silent fallback từ V2 sang V1.
- [x] Nếu V2 load fail → explicit error; không chạy V1 thay thế.
- [x] Định nghĩa resource lookup path mới.
- [x] Định nghĩa version selector/fingerprint contract.

### Acceptance

- [x] Có đúng một active default metamodel.
- [x] Có đúng một active default structural mapping.

---

## P29.4 — Audit toàn repository cho V1 coupling

Search production code/docs/tests/resources cho:

- [x] V1 Ecore path.
- [x] V1 Mapping path.
- [x] old namespace URI/prefix/package name.
- [x] hard-coded class names chỉ tồn tại ở V1.
- [x] hard-coded attribute/reference names chỉ tồn tại ở V1.
- [x] `MetamodelKind`/enum phụ thuộc V1.
- [x] frozen C/A/R/I/VP IDs.
- [x] V1 projection IDs.
- [x] V1 hash/fingerprint.
- [x] hard-coded counts.
- [x] golden output assumptions.
- [x] OCL contexts/navigation phụ thuộc V1.
- [x] runtime target-binding phụ thuộc V1.
- [x] release package paths phụ thuộc V1.

Classify mỗi occurrence:

- [x] `MIGRATE`.
- [x] `KEEP_HISTORICAL`.
- [x] `VERSION_ABSTRACTION`.
- [x] `REMOVE`.
- [x] `REVIEW_REQUIRED`.

### Output

- [x] `v1-coupling-inventory.md`.

---

## P29.5 — Phase 29 gate

Independent inventory, executable selection and integration regression PASS. Phase workflow closure PASS; see final acceptance. REMOVE is an audited empty classification, not authorization to delete.

- [x] Không sửa parser/transformation sâu trước khi inventory hoàn thành.
- [x] Không xóa V1.
- [x] V2 active-baseline policy được document.
- [x] V1-coupling inventory hoàn chỉnh.
- [x] Test baseline trước migration được lưu.

---

# Phase 30 — Metamodel V2 Structural Audit

**DONE — 2026-09-24.** Component, downstream integration, clean full regression, merge, post-merge regression and push gates PASS. [Acceptance](../project/v2-migration/phase35-acceptance.md). Older progress snapshots below are historical.

**Objective:** hiểu chính xác Metamodel V2 như một contract máy đọc được, không dựa vào V1 assumptions.

## P30.1 — Parse và validate Ecore V2

### Tasks

- [x] Load Ecore V2 bằng parser/EMF gate hiện có hoặc tooling tương đương.
- [x] Validate XML/Ecore syntax.
- [x] Resolve all classifiers.
- [x] Resolve all EReference targets.
- [x] Resolve eSuperTypes.
- [x] Detect unresolved proxies.
- [x] Detect invalid containment.
- [x] Detect duplicate names trong cùng namespace.
- [x] Detect invalid datatype references.
- [x] Compute SHA-256.
- [x] Record nsURI/nsPrefix/package/version.

### Acceptance

- [x] Ecore V2 load sạch hoặc mọi unresolved fact có diagnostic explicit.
- [x] Không tiếp tục mapping nếu Ecore structurally invalid.

---

## P30.2 — Generate V2 structural inventory tự động

Không hard-code count.

Generate:

- [x] all EClasses.
- [x] abstract/concrete status.
- [x] all EAttributes.
- [x] datatype/default/bounds.
- [x] all EReferences.
- [x] source/target.
- [x] containment.
- [x] lower/upper bounds.
- [x] ordered/unique.
- [x] all inheritance edges.
- [x] eOpposite nếu có.
- [x] annotations/provenance quan trọng.
- [x] unresolved fields nếu có.

### Output

- [x] `metamodel-v2-inventory.json`.
- [x] `metamodel-v2-audit.md`.

---

## P30.3 — Exact V1 → V2 structural diff

### Tasks

- [x] added classes.
- [x] removed classes.
- [x] same-name but changed classes.
- [x] added/removed attributes.
- [x] type/default/bounds changes.
- [x] added/removed references.
- [x] target changes.
- [x] containment changes.
- [x] multiplicity changes.
- [x] ordering/uniqueness changes.
- [x] inheritance changes.
- [x] namespace changes.
- [x] annotation/provenance differences.
- [x] rename candidates chỉ ghi `CANDIDATE`; không auto-accept fuzzy rename.

### Output

- [x] `metamodel-v1-to-v2-diff.json`.
- [x] `metamodel-v1-to-v2-impact.md`.

---

## P30.4 — Classify semantic breakage

Với mỗi breaking diff:

- [x] source-language concept còn tồn tại không?
- [x] chỉ đổi representation hay đổi semantics?
- [x] parser có bị ảnh hưởng?
- [x] Semantic IR có bị ảnh hưởng?
- [x] structural mapping có bị ảnh hưởng?
- [x] projection có bị ảnh hưởng?
- [x] trace identity có bị ảnh hưởng?
- [x] runtime target-binding có bị ảnh hưởng?
- [x] OCL navigation/context có bị ảnh hưởng?
- [x] case studies có bị ảnh hưởng?

Classification vocabulary audited: actual rows use conservative breaking/added/removed dispositions; representation-only or compatible statuses are not asserted without proof.

- [x] `REPRESENTATION_ONLY`.
- [x] `SEMANTIC_COMPATIBLE_CHANGE`.
- [x] `SEMANTIC_BREAKING_CHANGE`.
- [x] `ADDED_CAPABILITY`.
- [x] `REMOVED_CAPABILITY`.
- [x] `UNCERTAIN_REQUIRES_DECISION`.

---

## P30.5 — Establish V2 working manifest

Create/update manifest containing:

- [x] V2 Ecore path.
- [x] hash.
- [x] package/nsURI.
- [x] structural counts generated dynamically.
- [x] status = `WORKING_BASELINE`.
- [x] created/updated date.
- [x] provenance.
- [x] known unresolved items.
- [x] allowed evolution policy.

### Important

- [x] Không dùng từ `FROZEN`.
- [x] Không khóa mapping hash như final release nếu đang active development; hash vẫn phải được record để reproducibility.

---

## P30.6 — Phase 30 gate

Native structural audit, generated inventory and downstream consumer regression PASS; phase workflow closure PASS; see final acceptance. See `v2-migration/metamodel-v2-audit.md` and exact impact report.

- [x] V2 Ecore structurally valid.
- [x] Exact inventory tồn tại.
- [x] V1→V2 diff tồn tại.
- [x] Breaking changes đã classify.
- [x] Không còn production decision dựa trên V1 counts.

---

# Phase 31 — Mapping V2 Audit & USE Target Contract

**DONE — 2026-09-24.** Component, downstream integration, clean full regression, merge, post-merge regression and push gates PASS. [Acceptance](../project/v2-migration/phase35-acceptance.md). Older progress snapshots below are historical.

**Objective:** kiểm chứng Mapping V2 đã có, không regenerate hoặc rewrite mù quáng.

**2026-09-23 — PARTIAL / ordering decision approved:**
Target-only generic independent rank projection is authorized.
[Implementation and current gates](../project/v2-migration/phase31-order-projection.md).
The following counts and blocker description are the pre-projection 2.1 audit;
the membership-only limitation remains a negative regression, not a request for
a new decision. Production default selection/IR consumers still require migration.

**Historical pre-projection audit:**
[Mapping audit](../project/v2-migration/mapping-v2-audit.md),
[ordered-opposite decision](../project/v2-migration/phase31-ordered-opposite-decision.md).
Independent schema/source/compiler gates PASS (11 focused tests). Full regression:
317 executed, 3 failures, 73 errors; same 76 failing identities as baseline.
V2-ORDER-001 proves current USE link insertion cannot preserve every valid pair
of independently ordered opposite lists. Phase and fidelity gates remain OPEN.
Checked items below refer to audit fixtures/contracts, not production migration.
Unused disposition categories are not asserted. Source inheritance is N/A (zero
edges); the explicit subtype projection compiler control passes. Multiplicity
bounds are structurally checked; complete source-semantic preservation, generated
navigation/trace and executable projection/runtime/OCL gates remain OPEN.

## P31.1 — Load Mapping V2 + schema

- [x] Validate JSON syntax.
- [x] Validate JSON schema.
- [x] Validate mapping version.
- [x] Validate declared source metamodel fingerprint/version.
- [x] Reject mapping trỏ sang V1 fingerprint.
- [x] Detect unknown fields nếu schema yêu cầu closed shape.
- [x] Detect duplicate rule IDs.

---

## P31.2 — Source coverage audit dựa trên Ecore V2

- [x] Mỗi EClass V2 có disposition.
- [x] Mỗi declared EAttribute V2 có disposition.
- [x] Mỗi EReference V2 có disposition.
- [x] Mỗi inheritance edge V2 có disposition.
- [x] Không orphan mapping entry.
- [x] Không stale source key.
- [x] Không bare-name ambiguity.
- [x] Owner-qualified identity được dùng.
- [x] Removed V1 elements không còn active mapping entry.
- [x] New V2 elements không silently ignored.

Allowed disposition (alternative categories below are audited N/A: all current structural sources are MAPPED):

- [x] `MAPPED`.
- [x] `INTENTIONALLY_NOT_MAPPED` với reason.
- [x] `REVIEW_REQUIRED`.
- [x] `UNSUPPORTED` với evidence.

---

## P31.3 — Target USE validity audit

Với từng mapping:

- [x] USE target construct hợp lệ.
- [x] datatype conversion hợp lệ.
- [x] inheritance hợp lệ.
- [x] association/composition direction hợp lệ.
- [x] multiplicity preserve intended source semantics.
- [x] role names deterministic và không collision.
- [x] reserved USE identifiers được escape có trace.
- [x] generated reverse navigation không bị hiểu nhầm source-authored.
- [x] no duplicate classifier/association/role names.

---

## P31.4 — Projection V2 audit

Nếu Mapping V2 có projection/profile extension:

- [x] inventory tất cả projections.
- [x] xác định source anchors.
- [x] xác định target concepts.
- [x] prerequisites.
- [x] assumptions.
- [x] information loss.
- [x] runtime relevance.
- [x] OCL relevance.
- [x] case-study independence.
- [x] unsupported conditions.

Đặc biệt audit:

- [x] concrete Artifact projection.
- [x] observable property projection.
- [x] operation signature projection.
- [x] Agent ↔ Environment cross-dimensional anchors.
- [x] Organisation anchors.
- [x] Norm preservation.
- [x] mọi V2 projection mới.

---

## P31.5 — USE compiler gate

Generate structural fixture từ Mapping V2:

- [x] classes compile.
- [x] attributes compile.
- [x] associations/compositions compile.
- [x] inheritance compile.
- [x] projection fixture compile.
- [x] negative mutations fail như expected.

### Output

- [x] `mapping-v2-audit.md`.
- [x] `mapping-v2-validation.json`.
- [x] `mapping-v2-use-compile.log`.

---

## P31.6 — Mapping V2 working status

- [x] Mapping V2 = `WORKING_BASELINE`.
- [x] Không freeze final.
- [x] Có hash/version record.
- [x] Có compatibility pointer tới exact Metamodel V2 hash.
- [x] Mọi future Ecore change phải invalidate/reconcile mapping status.

### V2-ORDER-001 — approved target-only projection component gates

- [x] Mapping/schema 2.2.0 and exact working loader validation.
- [x] Generic plan, typed order entries, independent authoritative directions.
- [x] Native EMF counterexample retained and projected source navigation PASS.
- [x] Determinism, actual text/direct parity, rank validity and membership bijection.
- [x] Trace and exact source-feature OCL query binding.
- [x] Rank-only runtime action, queue, drift and authoritative reconnect/resync.
- [x] Composition, unordered and non-opposite controls; no Ecore/USE core edits.
- [x] Default V2 facade/IR/parser integration and full consumer regression (P32–35).

Evidence: `phase31-order-projection.md`, `order-projection-working-manifest.json`.
27 focused tests and 9 Python regressions PASS. Full reactor: 325 executed,
3 failures, 73 errors, same failing identities as pre-migration baseline.
Phase closure remains OPEN; this is not an end-to-end V2 release acceptance.

---

# Phase 32 — V2 Evolution Architecture & Change-Resilience

**DONE — 2026-09-24.** Component, downstream integration, clean full regression, merge, post-merge regression and push gates PASS. [Acceptance](../project/v2-migration/phase35-acceptance.md). Older progress snapshots below are historical.

**Objective:** làm cho các thay đổi nhỏ V2.1/V2.2 sau này rẻ và có kiểm soát.

**Component tasks PASS; overall phase closure OPEN.** See
[selection/evolution evidence](../project/v2-migration/active-baseline-policy.md).
20 focused Java tests and 11 Python tests PASS. Mapping defaults now select V2;
IR/parser/facade integration and full regression still depend on P33–35.
No phase merge/release acceptance is implied by the checked component tasks.

## P32.1 — Remove hard-coded metamodel inventory from production logic

- [x] Không hard-code class count.
- [x] Không hard-code attribute/reference count.
- [x] Không hard-code inheritance count.
- [x] Không hard-code V1 projection count.
- [x] Registry/descriptor được load từ V2 mapping/metamodel contract.

---

## P32.2 — Centralize active metamodel/mapping selection

Create one component/service responsible for:

- [x] active metamodel path.
- [x] active mapping path.
- [x] version.
- [x] hash.
- [x] compatibility status.
- [x] resource packaging path.
- [x] diagnostics.

Không để nhiều class tự nối path `Core/...`.

---

## P32.3 — Implement/reuse exact metamodel diff tool

Input:

- [x] old Ecore.
- [x] new Ecore.

Output:

- [x] structural diff.
- [x] mapping impact.
- [x] semantic IR impact.
- [x] parser impact.
- [x] projection impact.
- [x] trace impact.
- [x] runtime target-binding impact.
- [x] OCL context/navigation impact.
- [x] golden output impact.

### Tests

- [x] add class.
- [x] remove class.
- [x] add attribute.
- [x] datatype change.
- [x] multiplicity change.
- [x] containment change.
- [x] target reference change.
- [x] inheritance change.
- [x] rename candidate without auto acceptance.
- [x] no-op/self diff.

---

## P32.4 — Working-baseline update command/process

Define a repeatable process for V2 minor change:

```text
replace/update V2 input
→ compute diff
→ fail affected compatibility gates
→ generate impact report
→ update only affected layers
→ regenerate outputs
→ regression
→ update working manifest/hash
```

Checklist:

- [x] Không manual checklist-only process; có automation nơi hợp lý.
- [x] Không silently accept fingerprint mismatch.
- [x] Không update only hash to silence test.
- [x] Unaffected runtime connectors must remain green.

---

## P32.5 — Phase 32 gate

- [x] Synthetic V2.1 change chứng minh pipeline phát hiện đúng impacted layers.
- [x] Unaffected subsystems không cần sửa.
- [x] V2 future minor changes có documented migration loop.

---

# Phase 33 — Semantic IR V2 Migration

**DONE — 2026-09-24.** Component, downstream integration, clean full regression, merge, post-merge regression and push gates PASS. [Acceptance](../project/v2-migration/phase35-acceptance.md). Older progress snapshots below are historical.

**Objective:** thay vocabulary/IR V1 bằng representation phù hợp V2 nhưng vẫn source-preserving và traceable.

**Component gates PASS; phase closure OPEN.** [IR evidence](../project/v2-migration/phase33-semantic-ir.md).
29 focused tests PASS; full reactor 333 executed, 7 failures/65 errors in pending
V1 consumers (historical snapshot). Parser declaration/instance migration and
production no-V1-leakage now PASS: 28 focused tests; full closure remains OPEN.

V2 profile/path/trace follow-up: [consumer evidence](../project/v2-migration/phase33-verification-profile.md),
30 focused tests PASS. Post-profile plugin regression: 192 executed, 9F/62E
(71 failing identities versus previous 72); later V1 parser/OCL assumptions exposed.
Alias trace collision identified and fixed with regression. No phase closure claimed.

## P33.1 — Audit current IR against V2

- [x] `SemanticElement`.
- [x] semantic kind registry/enum.
- [x] attributes storage.
- [x] references storage.
- [x] source provenance.
- [x] symbol index.
- [x] typed subclasses/records nếu có.
- [x] cross-dimensional references.
- [x] runtime identity separation.

Classify:

- [x] reusable unchanged.
- [x] adapt.
- [x] replace.
- [x] remove historical.

---

## P33.2 — Define V2 semantic identity contract

Requirements:

- [x] stable project ID.
- [x] dimension.
- [x] kind.
- [x] owner path.
- [x] local ID.
- [x] source spelling.
- [x] no name-only global identity.
- [x] deterministic.
- [x] reversible/provenance-preserving where possible.

### Important

Nếu V2 thay kind names:

- [x] semantic IDs chỉ đổi khi semantics thực sự đổi.
- [x] không giữ V1 kind name chỉ để tránh migration nếu V2 semantics khác.

---

## P33.3 — Implement V2 semantic kind registry

Prefer data/descriptor-driven design nếu V2 còn có thể đổi nhẹ.

- [x] Registry được derive/validate against V2 Ecore.
- [x] Không cần sửa hàng chục switch chỉ vì thêm một EClass nếu logic generic có thể xử lý.
- [x] Những kind cần custom behavior vẫn explicit.
- [x] Unknown/unsupported kind → diagnostic, không crash/silent ignore.

---

## P33.4 — V2 references/resolution model

- [x] declaration vs instance vs symbolic reference vs runtime identity tách riêng.
- [x] exact canonical ID first.
- [x] explicit source reference.
- [x] owner-qualified exact symbol.
- [x] unique typed scope.
- [x] optional explicit binding.
- [x] otherwise unresolved.
- [x] no fuzzy acceptance.

---

## P33.5 — IR serialization/debug output

- [x] deterministic.
- [x] versioned.
- [x] includes V2 metamodel fingerprint.
- [x] includes source provenance.
- [x] includes unresolved references.
- [x] useful cho audit/tests.

---

## P33.6 — Phase 33 tests

- [x] minimal valid V2 IR.
- [x] each dimension.
- [x] duplicate names.
- [x] ambiguous reference.
- [x] missing reference.
- [x] inheritance-derived feature.
- [x] cross-dimensional reference.
- [x] deterministic serialization.
- [x] no V1 kind leakage ngoài migration fixtures.

---

# Phase 34 — Parser & Extraction Migration to V2

**DONE — 2026-09-24.** Component, downstream integration, clean full regression, merge, post-merge regression and push gates PASS. [Acceptance](../project/v2-migration/phase35-acceptance.md). Older progress snapshots below are historical.

**Objective:** giữ parser machinery reusable, đổi semantic output theo V2.

**Parser component gates PASS; phase closure OPEN.** [Evidence](../project/v2-migration/phase34-extraction.md).
34 focused tests PASS. Post-parser plugin regression: 197 executed, 8F/59E;
remaining projection/constraint/runtime consumers require P35 migration.
No merged/full-regression acceptance is claimed.

## P34.1 — JCM extraction

- [x] MAS/project root semantics theo V2.
- [x] Agent declarations.
- [x] workspace/artifact declarations.
- [x] organisation instances.
- [x] source/include paths.
- [x] roles/focus/project parameters.
- [x] exact source spans.
- [x] V2 references.

---

## P34.2 — Jason parser/extractor

- [x] beliefs.
- [x] rules.
- [x] goals.
- [x] plans.
- [x] triggering events.
- [x] contexts.
- [x] bodies/body terms.
- [x] internal/external actions.
- [x] messages nếu V2 vẫn model.
- [x] V2 ownership/reference direction.
- [x] unsupported syntax preserved with diagnostics.

Không implement Jason interpreter.

---

## P34.3 — CArtAgO Java extraction

- [x] artifact type.
- [x] observable properties.
- [x] operations.
- [x] guards.
- [x] signals/await/internal operations nếu V2 cần.
- [x] signatures/parameter metadata.
- [x] source positions.
- [x] V2 links.
- [x] parse-only/static safety vẫn giữ.

Không execute arbitrary project Java để infer semantics.

---

## P34.4 — Moise extraction

- [x] organisation structure.
- [x] groups.
- [x] roles.
- [x] links/formation constraints.
- [x] schemes.
- [x] missions.
- [x] organisational goals/plans.
- [x] norms.
- [x] V2 relationship directions.
- [x] instance config từ JCM.
- [x] DTD/entity disabled.

---

## P34.5 — Cross-file resolver V2

- [x] resolve all V2 exact relations.
- [x] binding only after exact typed ambiguity.
- [x] no binding creates nonexistent relation.
- [x] stale source hash rejected.
- [x] diagnostics list candidates/owners/scopes.

---

## P34.6 — Parser regression

- [x] existing source fixtures still parse where source language unchanged.
- [x] expected semantic outputs updated intentionally.
- [x] malformed input recovery.
- [x] partial project.
- [x] source path safety.
- [x] deterministic results.
- [x] Auction import reaches V2 IR.
- [x] Case Study #2 import reaches V2 IR.

---

# Phase 35 — USE Transformation V2 & Initial State

**DONE — 2026-09-24.** Component, downstream integration, clean full regression, merge, post-merge regression and push gates PASS. [Acceptance](../project/v2-migration/phase35-acceptance.md). Older progress snapshots below are historical.

**Objective:** `JaCaMoSemanticModel V2 + Mapping V2 → USE MModel + initial MSystemState` chính xác và deterministic.

**IN PROGRESS; closure OPEN.** [Implementation/evidence](../project/v2-migration/phase35-transformation.md).
Latest broad plugin snapshot: 201 tests, 8F/3E (11 failing identities; down from 67).
Focused V2 SOIL/direct parity and guard tests pass; final consumer/evidence/resource
regressions and full reactor remain pending. No dependent P29-P34 gate is closed.

## P35.1 — Mapping loader/planner migration

- [x] active loader reads Mapping V2.
- [x] validates exact V2 Ecore hash/version.
- [x] builds V2 TransformationPlan.
- [x] removed V1 rules cannot resolve accidentally.
- [x] diagnostics include mapping rule IDs/provenance.

---

## P35.2 — Structural generation

- [x] classes.
- [x] abstract/concrete.
- [x] attributes.
- [x] inheritance.
- [x] associations.
- [x] compositions.
- [x] multiplicities.
- [x] ordered/unique.
- [x] deterministic names.
- [x] USE keyword escapes.
- [x] V2 projections.

---

## P35.3 — Concrete verification projections

Audit/implement only when V2 supports them:

- [x] concrete Artifact subtype.
- [x] observable property → typed state slot.
- [x] CArtAgO operation → MOperation.
- [x] action-operation anchor.
- [x] percept/belief or equivalent V2 relation.
- [x] organisational goal/agent goal or equivalent.
- [x] normative preservation.

No projection by intuition; every projection requires V2/source evidence.

---

## P35.4 — Initial state materialization

Order must be explicit:

- [x] create objects.
- [x] scalar attributes.
- [x] containment/composition links.
- [x] associations.
- [x] projected state.
- [x] multiplicity/structure validation.
- [x] initial invariants.

Rules:

- [x] no fabricated default.
- [x] unresolved source value remains undefined/unset.
- [x] no missing required link fabricated.
- [x] object/link trace created.

---

## P35.5 — Text vs Direct backend parity

- [x] `.use`.
- [x] `.cmd`.
- [x] direct `MModel`.
- [x] direct `MSystemState`.
- [x] same effective semantics.
- [x] same deterministic source trace.

---

## P35.6 — Golden regeneration policy

- [x] Old V1 golden kept under historical path nếu cần.
- [x] New V2 golden outputs generated intentionally.
- [x] Review structural diff.
- [x] Không snapshot-update tự động khi compile fail.
- [x] Hashes recorded.

---

## P35.7 — Phase 35 gate

- [x] generated V2 `.use` compiles.
- [x] initial state valid.
- [x] text/direct parity PASS.
- [x] no V1 active mapping dependency.
- [x] Auction static V2 transformation PASS.

---

# Phase 36 — Trace, Binding & Runtime Identity V2

**DONE — 2026-09-24.** 52 focused, 352 full-reactor and 14 post-merge tests PASS; zero skips. Commit df369365 merged and pushed; concurrent metrics-only commits preserved at 946b6596. [Audit](../project/v2-migration/phase36-trace-identity.md).

**Objective:** mọi source/semantic/USE/runtime identity tiếp tục truy vết chính xác sau V2 migration.

## P36.1 — Trace schema impact audit

- [x] Can existing TraceRecord schema remain?
- [x] sourceSemanticId format impact.
- [x] targetUseId impact.
- [x] mappingRuleId versioning.
- [x] projectionRuleId versioning.
- [x] target kind changes.
- [x] runtime alias compatibility.
- [x] stale V1 trace behavior.

---

## P36.2 — V2 transformation trace

Mỗi generated:

- [x] MClass.
- [x] MAttribute.
- [x] MAssociation/composition.
- [x] MOperation.
- [x] MObject.
- [x] MLink.
- [x] projected state slot.

phải có trace tới V2 semantic source/provenance.

---

## P36.3 — Binding migration

- [x] V1 binding files không auto-apply nếu semantic IDs changed.
- [x] Mark incompatible bindings `STALE`.
- [x] Migrate only with exact proof.
- [x] Recompute source hashes.
- [x] Validate target kinds V2.
- [x] Keep binding optional.
- [x] No fuzzy migration.

---

## P36.4 — Runtime alias model

Maintain:

```text
RuntimeKey
→ SemanticId V2
→ UseId / MObject / MOperation / MAssociation target
```

- [x] Jason alias.
- [x] CArtAgO workspace/artifact/property/OpId alias.
- [x] Moise agent/group/scheme/role/mission/goal aliases.
- [x] one semantic Agent may have multiple runtime aliases.
- [x] no collapse by approximate names.

---

## P36.5 — Unknown runtime entity policy

- [x] discoverable.
- [x] quarantine/unbound.
- [x] no USE mutation.
- [x] actionable diagnostic.
- [x] possible later explicit binding only if semantics exist.

---

## P36.6 — Tests

- [x] exact one-to-one.
- [x] projection one-to-many.
- [x] ambiguity.
- [x] stale V1 trace.
- [x] stale binding.
- [x] duplicate operation names across artifact types.
- [x] multi-agent same source.
- [x] multiple org instances.
- [x] runtime alias reconnect/rebuild.
- [x] reverse violation navigation.

---

# Phase 37 — OCL & Constraint Architecture Migration to V2

**DONE — 2026-09-24.** 43 focused, 354 full-reactor and 11 post-merge tests PASS, zero skips. Commit 738f6bdd merged and pushed; concurrent metrics-only update preserved at 2be340d0. [Audit](../project/v2-migration/phase37-ocl-audit.md).

**Objective:** tất cả constraint compile/evaluate trên V2 mà không dùng V1 navigation giả.

## P37.1 — Inventory OCL origins

Classify each constraint:

- [x] `TRANSLATED`.
- [x] `CORE`.
- [x] `CASE`.
- [x] `USER`.

For every OCL:

- [x] context class.
- [x] navigation path.
- [x] referenced operation.
- [x] referenced attribute.
- [x] V2 compatibility.
- [x] source/provenance.

---

## P37.2 — Rebind translated constraints

- [x] CArtAgO guards only for supported exact subset.
- [x] Jason contexts only when V2 state binding proven.
- [x] no arbitrary Java body → postcondition.
- [x] unsupported stays `UNSUPPORTED`.
- [x] assumptions/dependencies updated to V2 IDs.
- [x] generated OCL deterministic.

---

## P37.3 — Rebuild core OCL for V2

- [x] remove V1-only navigation.
- [x] preserve only evidence-backed generic rules.
- [x] structural checks not duplicated unnecessarily.
- [x] cross-dimensional rules use V2 relations.
- [x] rationale + evidence for every core constraint.

---

## P37.4 — Migrate case/user OCL

- [x] Auction OCL V2.
- [x] Case Study #2 OCL V2.
- [x] examples do not leak into core.
- [x] user-authored OCL loader remains independent.
- [x] invalid V1 OCL fails with actionable context/navigation diagnostic.

---

## P37.5 — Compile/evaluation gate

- [x] parse.
- [x] type-check.
- [x] exact context binding.
- [x] PRE.
- [x] POST.
- [x] `@pre`.
- [x] invariant PASS/FAIL.
- [x] undefined → ERROR where contract says.
- [x] positive/negative fixtures.

---

# Phase 38 — Runtime Mapping V2 Reconciliation

**DONE — 2026-09-24.** 19 focused, 355 full-reactor and 10 post-merge tests PASS, zero skips. Commit 9cefe347 merged and pushed; metrics-only remote update preserved. [Audit](../project/v2-migration/phase38-runtime-mapping.md).

**Objective:** dùng runtime semantics thật của JaCaMo, map chúng vào V2 targets; không thiết kế runtime từ Ecore bằng suy đoán.

## P38.1 — Reconfirm upstream runtime capability baseline

Read research evidence for:

- [x] Jason.
- [x] CArtAgO.
- [x] Moise.
- [x] JaCaMo integration.

Record exact pinned versions used by plugin.

- [x] Do not silently mix current JaCaMo main dependencies with plugin's pinned runtime.
- [x] If version pin changes, run dedicated compatibility audit first.

---

## P38.2 — Preserve source RuntimeEvent semantics

Audit whether these source events remain valid independent of V2:

- [x] observable property delta.
- [x] artifact operation enter/exit/fail.
- [x] artifact lifecycle.
- [x] workspace membership/focus where supported.
- [x] Jason goal lifecycle.
- [x] Jason action lifecycle.
- [x] Jason belief deltas where exact.
- [x] Moise role players.
- [x] mission commitments.
- [x] organisational goal state.
- [x] normative lifecycle only if API proves it.

Do not rename upstream meaning just to match V2 class names.

---

## P38.3 — Generic Runtime Semantic Action vocabulary

Keep generic actions:

- [x] `CREATE_OBJECT`.
- [x] `DESTROY_OBJECT`.
- [x] `SET_ATTRIBUTE`.
- [x] `INSERT_LINK`.
- [x] `DELETE_LINK`.
- [x] `OPERATION_ENTER`.
- [x] `OPERATION_EXIT`.
- [x] `OPERATION_FAIL`.
- [x] `TRACE_ONLY` where no state mutation is justified.

Audit if V2 requires a genuinely new generic action; do not add one just because a class name changed.

---

## P38.4 — V2 runtime target binding

For each supported runtime rule:

- [x] source runtime/dimension.
- [x] raw upstream callback/API.
- [x] normalized RuntimeEvent kind.
- [x] required RuntimeKey.
- [x] required SemanticId V2.
- [x] exact USE target kind.
- [x] mapping/projection rule anchor.
- [x] mutation action.
- [x] payload conversion.
- [x] checkpoint.
- [x] unsupported/error behavior.
- [x] provenance.

---

## P38.5 — CArtAgO first-pass mapping

At minimum audit:

- [x] obs property add.
- [x] obs property change.
- [x] obs property remove.
- [x] operation started.
- [x] operation completed.
- [x] operation failed.
- [x] artifact created/disposed.
- [x] agent joined/quit workspace.
- [x] focus/unfocus.
- [x] artifact links.
- [x] signal/percept trace-only unless V2 projection exists.

---

## P38.6 — Jason mapping

- [x] belief add/remove only with exact semantic representation.
- [x] goal lifecycle target semantics.
- [x] action start/result.
- [x] message lifecycle boundary.
- [x] intention lifecycle remains deferred unless V2 explicitly models it.
- [x] no duplicate CArtAgO operation execution from Jason action.

Authority recommendation:

- [x] Jason action = agent-side evidence.
- [x] CArtAgO `OpId` = environment operation lifecycle authority.

---

## P38.7 — Moise mapping

- [x] role player add/remove.
- [x] mission commitment add/remove.
- [x] scheme/group runtime instance policy.
- [x] organisational goal state.
- [x] responsible group relation.
- [x] permission/obligation state only within supported semantics.
- [x] no full norm activation/violation claim without API evidence.

Authority:

- [x] Moise OE = organisation semantic authority.
- [x] CArtAgO organisation-board events are not double-applied.

---

## P38.8 — Canonical Runtime Mapping V2 candidate

Create/update:

- [x] runtime mapping schema.
- [x] runtime mapping JSON.
- [x] loader.
- [x] validator.
- [x] exact structural compatibility validation.
- [x] negative mutation tests.
- [x] no Auction names.
- [x] no object-specific runtime IDs.
- [x] no OCL expressions inside runtime mapping.
- [x] status remains `WORKING` until runtime E2E gates pass.

---

# Phase 39 — Runtime Mirror Correctness on V2

**DONE — 2026-09-24.** 58 focused, 356 full-reactor and 31 post-merge tests PASS, zero skips. Commit 58cd40bd merged and pushed; [audit](../project/v2-migration/phase39-mirror-correctness.md).

**Objective:** chứng minh USE mirror phản ánh đúng authoritative JaCaMo runtime trước khi dựa vào OCL verdict.

## P39.1 — Authoritative snapshot V2

- [x] Jason supported state.
- [x] CArtAgO artifacts/properties.
- [x] Moise supported organisation state.
- [x] exact runtime aliases.
- [x] exact V2 semantic bindings.
- [x] snapshot fingerprint/version.

---

## P39.2 — Ordered event pipeline

- [x] callback enqueue fast/non-blocking.
- [x] bounded queue.
- [x] single consumer or documented ordering model.
- [x] monotonic sequence.
- [x] correlation IDs.
- [x] no silent drop.
- [x] explicit backpressure failure.
- [x] late old-stream callback isolation.

---

## P39.3 — State mutation correctness

Verify each mutation against authoritative runtime:

- [x] object existence.
- [x] attribute values.
- [x] links.
- [x] operation correlation.
- [x] undefined/removal semantics.
- [x] tombstone/destroy policy.
- [x] no duplicate application.

---

## P39.4 — Connection lifecycle

States:

- [x] OFFLINE.
- [x] MODEL_READY.
- [x] CONNECTING.
- [x] SYNCING.
- [x] LIVE.
- [x] STALE.
- [x] ERROR.

Rules:

- [x] only LIVE is current.
- [x] disconnect → STALE.
- [x] reconnect → authoritative full resync.
- [x] failed snapshot → ERROR/disconnect.
- [x] rebuild/reimport/profile load installs one coherent workspace.
- [x] no duplicate listener.

---

## P39.5 — Drift detection

Compare:

```text
JaCaMo authoritative snapshot
vs
USE V2 mirror
```

- [x] object drift.
- [x] scalar drift.
- [x] link drift.
- [x] operation/correlation drift where applicable.
- [x] detailed diagnostics.
- [x] report-only mode.
- [x] auto-resync mode.
- [x] zero-drift required after successful resync.

---

## P39.6 — Mirror Correctness Gate

- [x] Auction runtime mirror PASS.
- [x] Case Study #2 runtime mirror PASS for supported subset.
- [x] unknown/unbound runtime entity does not mutate.
- [x] no double-source organisation mutation.
- [x] no silent event drops.
- [x] reconnect converges.

---

# Phase 40 — Runtime Verification V2

**DONE — 2026-09-24.** 69 focused, 356 full-reactor and 21 post-merge tests PASS, zero skips. Commit d781afd9 merged and pushed. [Audit](../project/v2-migration/phase40-runtime-verification.md).

**Objective:** chạy OCL/checking trên một mirror đã được chứng minh current/correct.

## P40.1 — Verification checkpoints

Implement/confirm:

- [x] `SNAPSHOT`.
- [x] `AFTER_MUTATION`.
- [x] `OPERATION_PRE`.
- [x] `OPERATION_POST`.
- [x] `STREAM_BOUNDARY`.

For each:

- [x] trigger.
- [x] required mirror state.
- [x] selected constraints.
- [x] event/correlation context.
- [x] result behavior.
- [x] STALE/ERROR behavior.

---

## P40.2 — Runtime invariants

- [x] full after authoritative snapshot.
- [x] targeted/conservative after mutation.
- [x] full fallback when dependency unknown.
- [x] global invariants not accidentally skipped.
- [x] undefined/error distinguished from FAIL.

---

## P40.3 — Operation PRE/POST

- [x] exact MObject.
- [x] exact MOperation.
- [x] exact args/type conversion.
- [x] capture pre-state once.
- [x] PRE at operation enter.
- [x] observe-only; no blocking JaCaMo.
- [x] POST only on matching successful exit.
- [x] preserve `@pre`.
- [x] OP_FAIL/abort → POST `SKIPPED`.
- [x] duplicate/stale terminal rejected.

---

## P40.4 — Runtime ordering/history

Choose least invasive V2-compatible representation:

- [x] reuse RuntimeTrace/verification projection where justified.
- [x] otherwise dedicated trace evaluator, clearly distinguished from OCL.
- [x] no speculative large runtime metamodel chỉ để lưu history.

Verify:

- [x] start-before-terminal.
- [x] same correlation.
- [x] stream generation.
- [x] case-specific ordering outside core.

---

## P40.5 — Cross-dimensional verification V2

Only approved/evidence-backed relations:

- [x] Agent action ↔ environment operation.
- [x] Agent ↔ Artifact accessibility/focus if represented.
- [x] observable state ↔ belief relation if V2/source proves it.
- [x] organisational goal ↔ agent goal if V2/source proves it.
- [x] role/mission ↔ performed behavior only when rule is explicit.

No behavioral invariant inferred from structural EReference alone.

---

## P40.6 — Violation reporting

Every runtime violation should include:

- [x] constraint ID/name.
- [x] origin.
- [x] checkpoint.
- [x] outcome.
- [x] USE context.
- [x] RuntimeEvent ID.
- [x] sequence.
- [x] correlation.
- [x] V2 SemanticId.
- [x] source span/provenance.
- [x] mapping/runtime rule ID.
- [x] actionable explanation.

---

# Phase 41 — Case Studies V2 & Genericity

**Objective:** chứng minh V2 pipeline không chỉ chạy với một fixture.

## P41.1 — Auction migration

- [x] source project imports.
- [x] V2 semantic model.
- [x] V2 mapping.
- [x] V2 `.use`.
- [x] V2 initial state.
- [x] V2 trace.
- [x] V2 OCL.
- [x] runtime sync.
- [x] positive scenario.
- [x] negative scenario.
- [x] PRE/POST where source/profile supports.
- [x] reconnect/resync.
- [x] exact violation navigation.

Do not invent `highestBid/currentBid` or operation names not present in the actual fixture.

---

## P41.2 — Case Study #2 migration

- [x] import.
- [x] transform.
- [x] OCL where evidence exists.
- [x] runtime supported subset.
- [x] positive.
- [x] negative.
- [x] trace.
- [x] reconnect.
- [x] no core special case.

---

## P41.3 — Genericity audit

Search core production code for:

- [x] Auction names.
- [x] Case Study #2 names.
- [x] object IDs.
- [x] operation names.
- [x] hard-coded runtime bindings.
- [x] special-case branch by project name.
- [x] V1 class names.
- [x] V1 hashes.

All example-specific logic must remain in:

- [x] example.
- [x] fixture.
- [x] case OCL/profile.
- [x] explicit binding.
- [x] test.

---

## P41.4 — Multi-case acceptance

- [x] same production pipeline.
- [x] same Mapping V2 engine.
- [x] same Runtime Mapping V2 engine.
- [x] no example-specific source dispatch.
- [x] supported/unsupported boundaries explicit.

---

# Phase 42 — UI, Packaging & Compatibility Migration

**Objective:** user workflow và release resources phản ánh V2, không còn hiển thị V1 như active baseline.

## P42.1 — Workbench workflow

- [x] Import JaCaMo Project.
- [x] show active Metamodel V2 version/hash.
- [x] show Mapping V2 compatibility.
- [x] diagnostics.
- [x] trace V2.
- [x] load OCL.
- [x] offline verification.
- [x] runtime connect.
- [x] runtime sync state.
- [x] live violations.
- [x] source navigation.

---

## P42.2 — Compatibility manifest

Record exact:

- [x] plugin version.
- [x] Java.
- [x] Maven.
- [x] USE version/commit.
- [x] JaCaMo baseline if directly used.
- [x] Jason.
- [x] CArtAgO.
- [x] Moise.
- [x] Metamodel V2 version/hash.
- [x] Mapping V2 version/hash.
- [x] Runtime Mapping version/hash/provisional status.
- [x] OCL profile hashes.

If project currently pins Jason 3.3.0 while upstream JaCaMo main uses another version, keep pin explicit until a deliberate compatibility update is tested.

---

## P42.3 — Resource packaging

Plugin JAR/ZIP must include the active canonical resources:

- [x] V2 Ecore.
- [x] Mapping V2.
- [x] schemas.
- [x] working/final manifests as applicable.
- [x] Runtime Mapping.
- [x] core OCL.
- [x] compatibility metadata.
- [x] release manifest.
- [x] licenses.

V1 historical resources:

- [x] either excluded from active package;
- [x] or clearly placed under historical/compatibility namespace.

Không có hai file cùng “canonical” status.

---

## P42.4 — UI regression

- [x] plugin load.
- [x] import.
- [x] rebuild.
- [x] OCL load.
- [x] full verify.
- [x] runtime tab.
- [x] disconnect/reconnect/resync.
- [x] report export.
- [x] no transformation logic inside UI.

---

# Phase 43 — Hardening, Security, Determinism & Performance

**Objective:** đóng các correctness gaps phát sinh từ V2 migration trước final freeze.

**Status:** DONE — focused 111/111, module 229/229 and reactor 372/372 PASS;
zero failures/errors/skips. See `docs/project/v2-migration/phase43-hardening.md`.

## P43.1 — Requirement → code → test traceability

For every V2 capability:

- [x] requirement.
- [x] implementation.
- [x] tests.
- [x] evidence.
- [x] status.

Allowed:

- [x] COMPLETE.
- [x] SUPPORTED_SUBSET_COMPLETE.
- [x] EXPLICITLY_UNSUPPORTED.
- [x] OUT_OF_SCOPE.

---

## P43.2 — TODO/FIXME/stale V1 audit

- [x] TODO.
- [x] FIXME.
- [x] `V1`.
- [x] old namespace.
- [x] old mapping IDs.
- [x] obsolete projection IDs.
- [x] old golden paths.
- [x] dead migration code.
- [x] duplicate V1/V2 dispatch.
- [x] commented-out fallback.

No unresolved correctness TODO in active V2 path.

---

## P43.3 — Determinism

Same:

- [x] project bytes.
- [x] V2 Ecore.
- [x] Mapping V2.
- [x] plugin version.
- [x] OCL profiles.

must yield same:

- [x] SemanticIds.
- [x] `.use`.
- [x] `.cmd`.
- [x] trace.
- [x] generated OCL.
- [x] diagnostics ordering.
- [x] mapping decisions.

Runtime UUID/timestamps may be run-specific but semantics/correlation must remain deterministic where expected.

---

## P43.4 — Security

- [x] path traversal.
- [x] symlink escape.
- [x] XML external entity/DTD.
- [x] Java static analysis does not initialize project code.
- [x] classpath/archive safety.
- [x] case OCL path restricted.
- [x] report export path handling.
- [x] no arbitrary shell execution.
- [x] logging avoids unnecessary sensitive data.

---

## P43.5 — Runtime resource/lifecycle

- [x] listener cleanup.
- [x] queue shutdown.
- [x] scheduler cleanup.
- [x] reconnect no duplicate subscription.
- [x] workspace replacement isolation.
- [x] operation correlation bounded cleanup.
- [x] stale aliases retired.
- [x] no old V1 target surviving V2 rebuild.

---

## P43.6 — Performance evidence

Measure:

- [x] import time.
- [x] Ecore/Mapping validation.
- [x] transformation.
- [x] OCL compile.
- [x] full verification.
- [x] runtime event→result latency.
- [x] queue depth/high-watermark.
- [x] memory.
- [x] resync latency.

Do not optimize semantics for benchmark.

---

# Phase 44 — V2 Release Candidate, Freeze & Evidence

**Objective:** chỉ freeze khi V2 đã đủ ổn cho thesis/release; trước đó vẫn là working baseline.

## P44.1 — Decide final V2 candidate

Before freeze:

- [ ] no pending known metamodel change expected immediately.
- [ ] V2 Ecore audit PASS.
- [ ] Mapping V2 audit PASS.
- [ ] V2 transformation PASS.
- [ ] Trace/binding PASS.
- [ ] Runtime Mapping PASS.
- [ ] OCL compile/check PASS.
- [ ] both case studies disposed.
- [ ] hardening PASS.

If metamodel changes here:

- [ ] return through Phase 32 change loop.
- [ ] regenerate affected evidence.
- [ ] do not patch hash only.

---

## P44.2 — Freeze Metamodel V2

Only now:

- [ ] status `FROZEN`.
- [ ] final hash.
- [ ] final inventory.
- [ ] audit.
- [ ] provenance.
- [ ] version.
- [ ] unresolved boundaries documented.
- [ ] mutation controls/negative tests PASS.

---

## P44.3 — Freeze Structural Mapping V2

- [ ] schema validation.
- [ ] full V2 source coverage/disposition.
- [ ] target USE compile.
- [ ] projection audit.
- [ ] negative mutation tests.
- [ ] mapping hash.
- [ ] freeze manifest.
- [ ] exact Metamodel V2 hash compatibility.

---

## P44.4 — Freeze Runtime Mapping

- [ ] source runtime capabilities reconciled to pinned APIs.
- [ ] target bindings reconciled to frozen V2.
- [ ] no Auction-specific rule.
- [ ] schema/semantic validator PASS.
- [ ] negative tests.
- [ ] runtime integration tests.
- [ ] audit.
- [ ] version/hash manifest.

---

## P44.5 — Final reproducibility bundle

Preserve:

- [ ] repository revision.
- [ ] V2 Ecore/hash.
- [ ] Mapping V2/schema/hash.
- [ ] Runtime Mapping/schema/hash.
- [ ] generated `.use`.
- [ ] generated `.cmd`.
- [ ] OCL profiles/provenance.
- [ ] trace.
- [ ] runtime event logs.
- [ ] verification reports.
- [ ] reconnect/resync evidence.
- [ ] case-study summaries.
- [ ] compatibility manifest.
- [ ] test logs/counts.
- [ ] release ZIP/JAR checksums.

---

## P44.6 — Final validation

- [ ] focused metamodel tests.
- [ ] focused mapping tests.
- [ ] parser fixtures.
- [ ] transformation/golden.
- [ ] OCL.
- [ ] trace/binding.
- [ ] synthetic runtime.
- [ ] real pinned Jason/CArtAgO/Moise connector tests.
- [ ] Auction E2E.
- [ ] Case Study #2 E2E.
- [ ] full module verify.
- [ ] full reactor verify.
- [ ] clean checkout/relocated build.
- [ ] installed plugin smoke.
- [ ] package inventory/hash.
- [ ] zero unexpected skipped correctness tests.

---

# V2 Minor-Change Fast Path

Dùng quy trình này nếu Metamodel V2 thay đổi nhỏ trong khi Phase 29–43 đang triển khai.

## Step A — Intake

- [ ] Replace/update only canonical V2 source files.
- [ ] Do not edit production code first.
- [ ] Compute new hash.
- [ ] Run Ecore structural diff against previous working V2.

## Step B — Impact classification

- [ ] metamodel-only metadata.
- [ ] mapping.
- [ ] semantic IR.
- [ ] parser/extractor.
- [ ] transformation.
- [ ] projection.
- [ ] trace/binding.
- [ ] OCL.
- [ ] runtime target binding.
- [ ] case fixtures.

## Step C — Selective migration

- [ ] update only impacted layers.
- [ ] no unrelated refactor.
- [ ] preserve stable semantic IDs where semantics unchanged.
- [ ] mark incompatible bindings/traces stale.
- [ ] regenerate affected golden files intentionally.

## Step D — Gates

- [ ] Ecore validation.
- [ ] Mapping validation.
- [ ] USE compile.
- [ ] affected parser tests.
- [ ] affected transformation tests.
- [ ] affected OCL compile.
- [ ] runtime target-binding tests if impacted.
- [ ] Auction smoke.
- [ ] full module regression before accepting new working baseline.

## Step E — Working manifest update

- [ ] version.
- [ ] hash.
- [ ] diff summary.
- [ ] impacted layers.
- [ ] tests.
- [ ] date.
- [ ] status remains `WORKING_BASELINE` until Phase 44.

---

# Final Acceptance Matrix for V2 Migration

## Metamodel/Mapping

- [ ] V2 active baseline selected.
- [ ] V1 historical only.
- [ ] Ecore V2 audited.
- [ ] Mapping V2 audited.
- [ ] no hidden V1 structural dependency.

## Static Pipeline

- [ ] V2 Semantic IR.
- [ ] JCM extraction.
- [ ] Jason extraction.
- [ ] CArtAgO extraction.
- [ ] Moise extraction.
- [ ] exact resolver.
- [ ] V2 `.use`.
- [ ] V2 `.cmd`.
- [ ] text/direct parity.

## Trace & OCL

- [ ] V2 trace.
- [ ] binding/staleness.
- [ ] core OCL.
- [ ] translated OCL subset.
- [ ] case/user OCL.
- [ ] PRE/POST.

## Runtime

- [ ] runtime capabilities pinned.
- [ ] RuntimeEvent preserved/reconciled.
- [ ] Runtime Mapping V2.
- [ ] authoritative snapshot.
- [ ] ordered mutation.
- [ ] no silent drop.
- [ ] drift detection.
- [ ] reconnect/resync.
- [ ] exact runtime identity.

## Verification

- [ ] snapshot invariants.
- [ ] after-mutation verification.
- [ ] operation PRE/POST.
- [ ] ordering/history bounded.
- [ ] cross-dimensional supported subset.
- [ ] normative supported subset.
- [ ] exact violation trace.

## Genericity

- [ ] Auction V2.
- [ ] Case Study #2 V2.
- [ ] no case-specific core logic.
- [ ] future V2 minor-change loop tested.

## Engineering

- [ ] security.
- [ ] determinism.
- [ ] performance evidence.
- [ ] clean build.
- [ ] plugin smoke.
- [ ] docs synchronized.
- [ ] release/evidence bundle.

---

# Project Completion Rule

Dự án chỉ được coi là **V2 LOGIC / CODING COMPLETE** khi:

- [ ] V2 active pipeline không còn dependency ngầm vào V1;
- [ ] mọi capability in-scope là `COMPLETE` hoặc `SUPPORTED_SUBSET_COMPLETE`;
- [ ] mọi capability còn lại là `EXPLICITLY_UNSUPPORTED` hoặc `OUT_OF_SCOPE` có evidence;
- [ ] mirror correctness được chứng minh trước khi dùng runtime OCL verdict;
- [ ] full regression PASS;
- [ ] V2 Metamodel + Structural Mapping + Runtime Mapping được freeze ở Phase 44;
- [ ] documentation/evidence/release artifacts đồng bộ.
