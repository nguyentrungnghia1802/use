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

- [ ] JaCaMo vẫn là execution engine; USE là verification mirror.
- [ ] Structural Mapping và Runtime Mapping vẫn là hai tầng riêng.
- [ ] Parser không tạo USE construct trực tiếp.
- [ ] Runtime connector không phụ thuộc tên EClass cụ thể của V2 nếu không bắt buộc.
- [ ] RuntimeEvent/RuntimeTrace không hard-code case study.
- [ ] Mọi runtime mutation phải qua exact identity/trace.
- [ ] Không dùng fuzzy/name similarity làm formal mapping.
- [ ] Không auto-create semantic object chỉ vì runtime xuất hiện tên gần giống.
- [ ] Không auto-convert Moise Norm thành OCL nếu chưa có translation contract.
- [ ] Không duplicate structural constraints bằng OCL nếu USE structure đã kiểm được.
- [ ] Không giữ hard-coded V1 counts (`37/67/63/14`) trong production logic.
- [ ] Không sửa generated output thủ công.
- [ ] Mọi V2.x change phải có impact report trước khi regenerate/freeze.
- [ ] Không đánh dấu V2 `FROZEN` cho tới Phase 44.

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

Executable selection gates below remain OPEN on P32.2/P35.1–P35.7; policy is recorded in `v2-migration/active-baseline-policy.md`.

### Tasks

- [x] V2 = `WORKING_BASELINE`.
- [x] V1 = `HISTORICAL_BASELINE`.
- [ ] Production import/transformation mặc định dùng V2.
- [ ] V1 chỉ được load qua explicit compatibility/test path nếu còn cần.
- [ ] Không có silent fallback từ V2 sang V1.
- [ ] Nếu V2 load fail → explicit error; không chạy V1 thay thế.
- [x] Định nghĩa resource lookup path mới.
- [x] Định nghĩa version selector/fingerprint contract.

### Acceptance

- [ ] Có đúng một active default metamodel.
- [ ] Có đúng một active default structural mapping.

---

## P29.4 — Audit toàn repository cho V1 coupling

Search production code/docs/tests/resources cho:

- [ ] V1 Ecore path.
- [ ] V1 Mapping path.
- [ ] old namespace URI/prefix/package name.
- [ ] hard-coded class names chỉ tồn tại ở V1.
- [ ] hard-coded attribute/reference names chỉ tồn tại ở V1.
- [ ] `MetamodelKind`/enum phụ thuộc V1.
- [ ] frozen C/A/R/I/VP IDs.
- [ ] V1 projection IDs.
- [ ] V1 hash/fingerprint.
- [ ] hard-coded counts.
- [ ] golden output assumptions.
- [ ] OCL contexts/navigation phụ thuộc V1.
- [ ] runtime target-binding phụ thuộc V1.
- [ ] release package paths phụ thuộc V1.

Classify mỗi occurrence:

- [ ] `MIGRATE`.
- [ ] `KEEP_HISTORICAL`.
- [ ] `VERSION_ABSTRACTION`.
- [ ] `REMOVE`.
- [ ] `REVIEW_REQUIRED`.

### Output

- [ ] `v1-coupling-inventory.md`.

---

## P29.5 — Phase 29 gate

- [ ] Không sửa parser/transformation sâu trước khi inventory hoàn thành.
- [ ] Không xóa V1.
- [ ] V2 active-baseline policy được document.
- [ ] V1-coupling inventory hoàn chỉnh.
- [ ] Test baseline trước migration được lưu.

---

# Phase 30 — Metamodel V2 Structural Audit

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

- [ ] source-language concept còn tồn tại không?
- [ ] chỉ đổi representation hay đổi semantics?
- [ ] parser có bị ảnh hưởng?
- [ ] Semantic IR có bị ảnh hưởng?
- [ ] structural mapping có bị ảnh hưởng?
- [ ] projection có bị ảnh hưởng?
- [ ] trace identity có bị ảnh hưởng?
- [ ] runtime target-binding có bị ảnh hưởng?
- [ ] OCL navigation/context có bị ảnh hưởng?
- [ ] case studies có bị ảnh hưởng?

Status:

- [ ] `REPRESENTATION_ONLY`.
- [ ] `SEMANTIC_COMPATIBLE_CHANGE`.
- [ ] `SEMANTIC_BREAKING_CHANGE`.
- [ ] `ADDED_CAPABILITY`.
- [ ] `REMOVED_CAPABILITY`.
- [ ] `UNCERTAIN_REQUIRES_DECISION`.

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

Native structural audit and generated inventory PASS; full consumer regression/phase closure remains OPEN on Phase 32–35. See `v2-migration/metamodel-v2-audit.md` and exact impact report.

- [x] V2 Ecore structurally valid.
- [x] Exact inventory tồn tại.
- [x] V1→V2 diff tồn tại.
- [ ] Breaking changes đã classify.
- [ ] Không còn production decision dựa trên V1 counts.

---

# Phase 31 — Mapping V2 Audit & USE Target Contract

**Objective:** kiểm chứng Mapping V2 đã có, không regenerate hoặc rewrite mù quáng.

## P31.1 — Load Mapping V2 + schema

- [ ] Validate JSON syntax.
- [ ] Validate JSON schema.
- [ ] Validate mapping version.
- [ ] Validate declared source metamodel fingerprint/version.
- [ ] Reject mapping trỏ sang V1 fingerprint.
- [ ] Detect unknown fields nếu schema yêu cầu closed shape.
- [ ] Detect duplicate rule IDs.

---

## P31.2 — Source coverage audit dựa trên Ecore V2

- [ ] Mỗi EClass V2 có disposition.
- [ ] Mỗi declared EAttribute V2 có disposition.
- [ ] Mỗi EReference V2 có disposition.
- [ ] Mỗi inheritance edge V2 có disposition.
- [ ] Không orphan mapping entry.
- [ ] Không stale source key.
- [ ] Không bare-name ambiguity.
- [ ] Owner-qualified identity được dùng.
- [ ] Removed V1 elements không còn active mapping entry.
- [ ] New V2 elements không silently ignored.

Allowed disposition:

- [ ] `MAPPED`.
- [ ] `INTENTIONALLY_NOT_MAPPED` với reason.
- [ ] `REVIEW_REQUIRED`.
- [ ] `UNSUPPORTED` với evidence.

---

## P31.3 — Target USE validity audit

Với từng mapping:

- [ ] USE target construct hợp lệ.
- [ ] datatype conversion hợp lệ.
- [ ] inheritance hợp lệ.
- [ ] association/composition direction hợp lệ.
- [ ] multiplicity preserve intended source semantics.
- [ ] role names deterministic và không collision.
- [ ] reserved USE identifiers được escape có trace.
- [ ] generated reverse navigation không bị hiểu nhầm source-authored.
- [ ] no duplicate classifier/association/role names.

---

## P31.4 — Projection V2 audit

Nếu Mapping V2 có projection/profile extension:

- [ ] inventory tất cả projections.
- [ ] xác định source anchors.
- [ ] xác định target concepts.
- [ ] prerequisites.
- [ ] assumptions.
- [ ] information loss.
- [ ] runtime relevance.
- [ ] OCL relevance.
- [ ] case-study independence.
- [ ] unsupported conditions.

Đặc biệt audit:

- [ ] concrete Artifact projection.
- [ ] observable property projection.
- [ ] operation signature projection.
- [ ] Agent ↔ Environment cross-dimensional anchors.
- [ ] Organisation anchors.
- [ ] Norm preservation.
- [ ] mọi V2 projection mới.

---

## P31.5 — USE compiler gate

Generate structural fixture từ Mapping V2:

- [ ] classes compile.
- [ ] attributes compile.
- [ ] associations/compositions compile.
- [ ] inheritance compile.
- [ ] projection fixture compile.
- [ ] negative mutations fail như expected.

### Output

- [ ] `mapping-v2-audit.md`.
- [ ] `mapping-v2-validation.json`.
- [ ] `mapping-v2-use-compile.log`.

---

## P31.6 — Mapping V2 working status

- [ ] Mapping V2 = `WORKING_BASELINE`.
- [ ] Không freeze final.
- [ ] Có hash/version record.
- [ ] Có compatibility pointer tới exact Metamodel V2 hash.
- [ ] Mọi future Ecore change phải invalidate/reconcile mapping status.

---

# Phase 32 — V2 Evolution Architecture & Change-Resilience

**Objective:** làm cho các thay đổi nhỏ V2.1/V2.2 sau này rẻ và có kiểm soát.

## P32.1 — Remove hard-coded metamodel inventory from production logic

- [ ] Không hard-code class count.
- [ ] Không hard-code attribute/reference count.
- [ ] Không hard-code inheritance count.
- [ ] Không hard-code V1 projection count.
- [ ] Registry/descriptor được load từ V2 mapping/metamodel contract.

---

## P32.2 — Centralize active metamodel/mapping selection

Create one component/service responsible for:

- [ ] active metamodel path.
- [ ] active mapping path.
- [ ] version.
- [ ] hash.
- [ ] compatibility status.
- [ ] resource packaging path.
- [ ] diagnostics.

Không để nhiều class tự nối path `Core/...`.

---

## P32.3 — Implement/reuse exact metamodel diff tool

Input:

- [ ] old Ecore.
- [ ] new Ecore.

Output:

- [ ] structural diff.
- [ ] mapping impact.
- [ ] semantic IR impact.
- [ ] parser impact.
- [ ] projection impact.
- [ ] trace impact.
- [ ] runtime target-binding impact.
- [ ] OCL context/navigation impact.
- [ ] golden output impact.

### Tests

- [ ] add class.
- [ ] remove class.
- [ ] add attribute.
- [ ] datatype change.
- [ ] multiplicity change.
- [ ] containment change.
- [ ] target reference change.
- [ ] inheritance change.
- [ ] rename candidate without auto acceptance.
- [ ] no-op/self diff.

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

- [ ] Không manual checklist-only process; có automation nơi hợp lý.
- [ ] Không silently accept fingerprint mismatch.
- [ ] Không update only hash to silence test.
- [ ] Unaffected runtime connectors must remain green.

---

## P32.5 — Phase 32 gate

- [ ] Synthetic V2.1 change chứng minh pipeline phát hiện đúng impacted layers.
- [ ] Unaffected subsystems không cần sửa.
- [ ] V2 future minor changes có documented migration loop.

---

# Phase 33 — Semantic IR V2 Migration

**Objective:** thay vocabulary/IR V1 bằng representation phù hợp V2 nhưng vẫn source-preserving và traceable.

## P33.1 — Audit current IR against V2

- [ ] `SemanticElement`.
- [ ] semantic kind registry/enum.
- [ ] attributes storage.
- [ ] references storage.
- [ ] source provenance.
- [ ] symbol index.
- [ ] typed subclasses/records nếu có.
- [ ] cross-dimensional references.
- [ ] runtime identity separation.

Classify:

- [ ] reusable unchanged.
- [ ] adapt.
- [ ] replace.
- [ ] remove historical.

---

## P33.2 — Define V2 semantic identity contract

Requirements:

- [ ] stable project ID.
- [ ] dimension.
- [ ] kind.
- [ ] owner path.
- [ ] local ID.
- [ ] source spelling.
- [ ] no name-only global identity.
- [ ] deterministic.
- [ ] reversible/provenance-preserving where possible.

### Important

Nếu V2 thay kind names:

- [ ] semantic IDs chỉ đổi khi semantics thực sự đổi.
- [ ] không giữ V1 kind name chỉ để tránh migration nếu V2 semantics khác.

---

## P33.3 — Implement V2 semantic kind registry

Prefer data/descriptor-driven design nếu V2 còn có thể đổi nhẹ.

- [ ] Registry được derive/validate against V2 Ecore.
- [ ] Không cần sửa hàng chục switch chỉ vì thêm một EClass nếu logic generic có thể xử lý.
- [ ] Những kind cần custom behavior vẫn explicit.
- [ ] Unknown/unsupported kind → diagnostic, không crash/silent ignore.

---

## P33.4 — V2 references/resolution model

- [ ] declaration vs instance vs symbolic reference vs runtime identity tách riêng.
- [ ] exact canonical ID first.
- [ ] explicit source reference.
- [ ] owner-qualified exact symbol.
- [ ] unique typed scope.
- [ ] optional explicit binding.
- [ ] otherwise unresolved.
- [ ] no fuzzy acceptance.

---

## P33.5 — IR serialization/debug output

- [ ] deterministic.
- [ ] versioned.
- [ ] includes V2 metamodel fingerprint.
- [ ] includes source provenance.
- [ ] includes unresolved references.
- [ ] useful cho audit/tests.

---

## P33.6 — Phase 33 tests

- [ ] minimal valid V2 IR.
- [ ] each dimension.
- [ ] duplicate names.
- [ ] ambiguous reference.
- [ ] missing reference.
- [ ] inheritance-derived feature.
- [ ] cross-dimensional reference.
- [ ] deterministic serialization.
- [ ] no V1 kind leakage ngoài migration fixtures.

---

# Phase 34 — Parser & Extraction Migration to V2

**Objective:** giữ parser machinery reusable, đổi semantic output theo V2.

## P34.1 — JCM extraction

- [ ] MAS/project root semantics theo V2.
- [ ] Agent declarations.
- [ ] workspace/artifact declarations.
- [ ] organisation instances.
- [ ] source/include paths.
- [ ] roles/focus/project parameters.
- [ ] exact source spans.
- [ ] V2 references.

---

## P34.2 — Jason parser/extractor

- [ ] beliefs.
- [ ] rules.
- [ ] goals.
- [ ] plans.
- [ ] triggering events.
- [ ] contexts.
- [ ] bodies/body terms.
- [ ] internal/external actions.
- [ ] messages nếu V2 vẫn model.
- [ ] V2 ownership/reference direction.
- [ ] unsupported syntax preserved with diagnostics.

Không implement Jason interpreter.

---

## P34.3 — CArtAgO Java extraction

- [ ] artifact type.
- [ ] observable properties.
- [ ] operations.
- [ ] guards.
- [ ] signals/await/internal operations nếu V2 cần.
- [ ] signatures/parameter metadata.
- [ ] source positions.
- [ ] V2 links.
- [ ] parse-only/static safety vẫn giữ.

Không execute arbitrary project Java để infer semantics.

---

## P34.4 — Moise extraction

- [ ] organisation structure.
- [ ] groups.
- [ ] roles.
- [ ] links/formation constraints.
- [ ] schemes.
- [ ] missions.
- [ ] organisational goals/plans.
- [ ] norms.
- [ ] V2 relationship directions.
- [ ] instance config từ JCM.
- [ ] DTD/entity disabled.

---

## P34.5 — Cross-file resolver V2

- [ ] resolve all V2 exact relations.
- [ ] binding only after exact typed ambiguity.
- [ ] no binding creates nonexistent relation.
- [ ] stale source hash rejected.
- [ ] diagnostics list candidates/owners/scopes.

---

## P34.6 — Parser regression

- [ ] existing source fixtures still parse where source language unchanged.
- [ ] expected semantic outputs updated intentionally.
- [ ] malformed input recovery.
- [ ] partial project.
- [ ] source path safety.
- [ ] deterministic results.
- [ ] Auction import reaches V2 IR.
- [ ] Case Study #2 import reaches V2 IR.

---

# Phase 35 — USE Transformation V2 & Initial State

**Objective:** `JaCaMoSemanticModel V2 + Mapping V2 → USE MModel + initial MSystemState` chính xác và deterministic.

## P35.1 — Mapping loader/planner migration

- [ ] active loader reads Mapping V2.
- [ ] validates exact V2 Ecore hash/version.
- [ ] builds V2 TransformationPlan.
- [ ] removed V1 rules cannot resolve accidentally.
- [ ] diagnostics include mapping rule IDs/provenance.

---

## P35.2 — Structural generation

- [ ] classes.
- [ ] abstract/concrete.
- [ ] attributes.
- [ ] inheritance.
- [ ] associations.
- [ ] compositions.
- [ ] multiplicities.
- [ ] ordered/unique.
- [ ] deterministic names.
- [ ] USE keyword escapes.
- [ ] V2 projections.

---

## P35.3 — Concrete verification projections

Audit/implement only when V2 supports them:

- [ ] concrete Artifact subtype.
- [ ] observable property → typed state slot.
- [ ] CArtAgO operation → MOperation.
- [ ] action-operation anchor.
- [ ] percept/belief or equivalent V2 relation.
- [ ] organisational goal/agent goal or equivalent.
- [ ] normative preservation.

No projection by intuition; every projection requires V2/source evidence.

---

## P35.4 — Initial state materialization

Order must be explicit:

- [ ] create objects.
- [ ] scalar attributes.
- [ ] containment/composition links.
- [ ] associations.
- [ ] projected state.
- [ ] multiplicity/structure validation.
- [ ] initial invariants.

Rules:

- [ ] no fabricated default.
- [ ] unresolved source value remains undefined/unset.
- [ ] no missing required link fabricated.
- [ ] object/link trace created.

---

## P35.5 — Text vs Direct backend parity

- [ ] `.use`.
- [ ] `.cmd`.
- [ ] direct `MModel`.
- [ ] direct `MSystemState`.
- [ ] same effective semantics.
- [ ] same deterministic source trace.

---

## P35.6 — Golden regeneration policy

- [ ] Old V1 golden kept under historical path nếu cần.
- [ ] New V2 golden outputs generated intentionally.
- [ ] Review structural diff.
- [ ] Không snapshot-update tự động khi compile fail.
- [ ] Hashes recorded.

---

## P35.7 — Phase 35 gate

- [ ] generated V2 `.use` compiles.
- [ ] initial state valid.
- [ ] text/direct parity PASS.
- [ ] no V1 active mapping dependency.
- [ ] Auction static V2 transformation PASS.

---

# Phase 36 — Trace, Binding & Runtime Identity V2

**Objective:** mọi source/semantic/USE/runtime identity tiếp tục truy vết chính xác sau V2 migration.

## P36.1 — Trace schema impact audit

- [ ] Can existing TraceRecord schema remain?
- [ ] sourceSemanticId format impact.
- [ ] targetUseId impact.
- [ ] mappingRuleId versioning.
- [ ] projectionRuleId versioning.
- [ ] target kind changes.
- [ ] runtime alias compatibility.
- [ ] stale V1 trace behavior.

---

## P36.2 — V2 transformation trace

Mỗi generated:

- [ ] MClass.
- [ ] MAttribute.
- [ ] MAssociation/composition.
- [ ] MOperation.
- [ ] MObject.
- [ ] MLink.
- [ ] projected state slot.

phải có trace tới V2 semantic source/provenance.

---

## P36.3 — Binding migration

- [ ] V1 binding files không auto-apply nếu semantic IDs changed.
- [ ] Mark incompatible bindings `STALE`.
- [ ] Migrate only with exact proof.
- [ ] Recompute source hashes.
- [ ] Validate target kinds V2.
- [ ] Keep binding optional.
- [ ] No fuzzy migration.

---

## P36.4 — Runtime alias model

Maintain:

```text
RuntimeKey
→ SemanticId V2
→ UseId / MObject / MOperation / MAssociation target
```

- [ ] Jason alias.
- [ ] CArtAgO workspace/artifact/property/OpId alias.
- [ ] Moise agent/group/scheme/role/mission/goal aliases.
- [ ] one semantic Agent may have multiple runtime aliases.
- [ ] no collapse by approximate names.

---

## P36.5 — Unknown runtime entity policy

- [ ] discoverable.
- [ ] quarantine/unbound.
- [ ] no USE mutation.
- [ ] actionable diagnostic.
- [ ] possible later explicit binding only if semantics exist.

---

## P36.6 — Tests

- [ ] exact one-to-one.
- [ ] projection one-to-many.
- [ ] ambiguity.
- [ ] stale V1 trace.
- [ ] stale binding.
- [ ] duplicate operation names across artifact types.
- [ ] multi-agent same source.
- [ ] multiple org instances.
- [ ] runtime alias reconnect/rebuild.
- [ ] reverse violation navigation.

---

# Phase 37 — OCL & Constraint Architecture Migration to V2

**Objective:** tất cả constraint compile/evaluate trên V2 mà không dùng V1 navigation giả.

## P37.1 — Inventory OCL origins

Classify each constraint:

- [ ] `TRANSLATED`.
- [ ] `CORE`.
- [ ] `CASE`.
- [ ] `USER`.

For every OCL:

- [ ] context class.
- [ ] navigation path.
- [ ] referenced operation.
- [ ] referenced attribute.
- [ ] V2 compatibility.
- [ ] source/provenance.

---

## P37.2 — Rebind translated constraints

- [ ] CArtAgO guards only for supported exact subset.
- [ ] Jason contexts only when V2 state binding proven.
- [ ] no arbitrary Java body → postcondition.
- [ ] unsupported stays `UNSUPPORTED`.
- [ ] assumptions/dependencies updated to V2 IDs.
- [ ] generated OCL deterministic.

---

## P37.3 — Rebuild core OCL for V2

- [ ] remove V1-only navigation.
- [ ] preserve only evidence-backed generic rules.
- [ ] structural checks not duplicated unnecessarily.
- [ ] cross-dimensional rules use V2 relations.
- [ ] rationale + evidence for every core constraint.

---

## P37.4 — Migrate case/user OCL

- [ ] Auction OCL V2.
- [ ] Case Study #2 OCL V2.
- [ ] examples do not leak into core.
- [ ] user-authored OCL loader remains independent.
- [ ] invalid V1 OCL fails with actionable context/navigation diagnostic.

---

## P37.5 — Compile/evaluation gate

- [ ] parse.
- [ ] type-check.
- [ ] exact context binding.
- [ ] PRE.
- [ ] POST.
- [ ] `@pre`.
- [ ] invariant PASS/FAIL.
- [ ] undefined → ERROR where contract says.
- [ ] positive/negative fixtures.

---

# Phase 38 — Runtime Mapping V2 Reconciliation

**Objective:** dùng runtime semantics thật của JaCaMo, map chúng vào V2 targets; không thiết kế runtime từ Ecore bằng suy đoán.

## P38.1 — Reconfirm upstream runtime capability baseline

Read research evidence for:

- [ ] Jason.
- [ ] CArtAgO.
- [ ] Moise.
- [ ] JaCaMo integration.

Record exact pinned versions used by plugin.

- [ ] Do not silently mix current JaCaMo main dependencies with plugin's pinned runtime.
- [ ] If version pin changes, run dedicated compatibility audit first.

---

## P38.2 — Preserve source RuntimeEvent semantics

Audit whether these source events remain valid independent of V2:

- [ ] observable property delta.
- [ ] artifact operation enter/exit/fail.
- [ ] artifact lifecycle.
- [ ] workspace membership/focus where supported.
- [ ] Jason goal lifecycle.
- [ ] Jason action lifecycle.
- [ ] Jason belief deltas where exact.
- [ ] Moise role players.
- [ ] mission commitments.
- [ ] organisational goal state.
- [ ] normative lifecycle only if API proves it.

Do not rename upstream meaning just to match V2 class names.

---

## P38.3 — Generic Runtime Semantic Action vocabulary

Keep generic actions:

- [ ] `CREATE_OBJECT`.
- [ ] `DESTROY_OBJECT`.
- [ ] `SET_ATTRIBUTE`.
- [ ] `INSERT_LINK`.
- [ ] `DELETE_LINK`.
- [ ] `OPERATION_ENTER`.
- [ ] `OPERATION_EXIT`.
- [ ] `OPERATION_FAIL`.
- [ ] `TRACE_ONLY` where no state mutation is justified.

Audit if V2 requires a genuinely new generic action; do not add one just because a class name changed.

---

## P38.4 — V2 runtime target binding

For each supported runtime rule:

- [ ] source runtime/dimension.
- [ ] raw upstream callback/API.
- [ ] normalized RuntimeEvent kind.
- [ ] required RuntimeKey.
- [ ] required SemanticId V2.
- [ ] exact USE target kind.
- [ ] mapping/projection rule anchor.
- [ ] mutation action.
- [ ] payload conversion.
- [ ] checkpoint.
- [ ] unsupported/error behavior.
- [ ] provenance.

---

## P38.5 — CArtAgO first-pass mapping

At minimum audit:

- [ ] obs property add.
- [ ] obs property change.
- [ ] obs property remove.
- [ ] operation started.
- [ ] operation completed.
- [ ] operation failed.
- [ ] artifact created/disposed.
- [ ] agent joined/quit workspace.
- [ ] focus/unfocus.
- [ ] artifact links.
- [ ] signal/percept trace-only unless V2 projection exists.

---

## P38.6 — Jason mapping

- [ ] belief add/remove only with exact semantic representation.
- [ ] goal lifecycle target semantics.
- [ ] action start/result.
- [ ] message lifecycle boundary.
- [ ] intention lifecycle remains deferred unless V2 explicitly models it.
- [ ] no duplicate CArtAgO operation execution from Jason action.

Authority recommendation:

- [ ] Jason action = agent-side evidence.
- [ ] CArtAgO `OpId` = environment operation lifecycle authority.

---

## P38.7 — Moise mapping

- [ ] role player add/remove.
- [ ] mission commitment add/remove.
- [ ] scheme/group runtime instance policy.
- [ ] organisational goal state.
- [ ] responsible group relation.
- [ ] permission/obligation state only within supported semantics.
- [ ] no full norm activation/violation claim without API evidence.

Authority:

- [ ] Moise OE = organisation semantic authority.
- [ ] CArtAgO organisation-board events are not double-applied.

---

## P38.8 — Canonical Runtime Mapping V2 candidate

Create/update:

- [ ] runtime mapping schema.
- [ ] runtime mapping JSON.
- [ ] loader.
- [ ] validator.
- [ ] exact structural compatibility validation.
- [ ] negative mutation tests.
- [ ] no Auction names.
- [ ] no object-specific runtime IDs.
- [ ] no OCL expressions inside runtime mapping.
- [ ] status remains `WORKING` until runtime E2E gates pass.

---

# Phase 39 — Runtime Mirror Correctness on V2

**Objective:** chứng minh USE mirror phản ánh đúng authoritative JaCaMo runtime trước khi dựa vào OCL verdict.

## P39.1 — Authoritative snapshot V2

- [ ] Jason supported state.
- [ ] CArtAgO artifacts/properties.
- [ ] Moise supported organisation state.
- [ ] exact runtime aliases.
- [ ] exact V2 semantic bindings.
- [ ] snapshot fingerprint/version.

---

## P39.2 — Ordered event pipeline

- [ ] callback enqueue fast/non-blocking.
- [ ] bounded queue.
- [ ] single consumer or documented ordering model.
- [ ] monotonic sequence.
- [ ] correlation IDs.
- [ ] no silent drop.
- [ ] explicit backpressure failure.
- [ ] late old-stream callback isolation.

---

## P39.3 — State mutation correctness

Verify each mutation against authoritative runtime:

- [ ] object existence.
- [ ] attribute values.
- [ ] links.
- [ ] operation correlation.
- [ ] undefined/removal semantics.
- [ ] tombstone/destroy policy.
- [ ] no duplicate application.

---

## P39.4 — Connection lifecycle

States:

- [ ] OFFLINE.
- [ ] MODEL_READY.
- [ ] CONNECTING.
- [ ] SYNCING.
- [ ] LIVE.
- [ ] STALE.
- [ ] ERROR.

Rules:

- [ ] only LIVE is current.
- [ ] disconnect → STALE.
- [ ] reconnect → authoritative full resync.
- [ ] failed snapshot → ERROR/disconnect.
- [ ] rebuild/reimport/profile load installs one coherent workspace.
- [ ] no duplicate listener.

---

## P39.5 — Drift detection

Compare:

```text
JaCaMo authoritative snapshot
vs
USE V2 mirror
```

- [ ] object drift.
- [ ] scalar drift.
- [ ] link drift.
- [ ] operation/correlation drift where applicable.
- [ ] detailed diagnostics.
- [ ] report-only mode.
- [ ] auto-resync mode.
- [ ] zero-drift required after successful resync.

---

## P39.6 — Mirror Correctness Gate

- [ ] Auction runtime mirror PASS.
- [ ] Case Study #2 runtime mirror PASS for supported subset.
- [ ] unknown/unbound runtime entity does not mutate.
- [ ] no double-source organisation mutation.
- [ ] no silent event drops.
- [ ] reconnect converges.

---

# Phase 40 — Runtime Verification V2

**Objective:** chạy OCL/checking trên một mirror đã được chứng minh current/correct.

## P40.1 — Verification checkpoints

Implement/confirm:

- [ ] `SNAPSHOT`.
- [ ] `AFTER_MUTATION`.
- [ ] `OPERATION_PRE`.
- [ ] `OPERATION_POST`.
- [ ] `STREAM_BOUNDARY`.

For each:

- [ ] trigger.
- [ ] required mirror state.
- [ ] selected constraints.
- [ ] event/correlation context.
- [ ] result behavior.
- [ ] STALE/ERROR behavior.

---

## P40.2 — Runtime invariants

- [ ] full after authoritative snapshot.
- [ ] targeted/conservative after mutation.
- [ ] full fallback when dependency unknown.
- [ ] global invariants not accidentally skipped.
- [ ] undefined/error distinguished from FAIL.

---

## P40.3 — Operation PRE/POST

- [ ] exact MObject.
- [ ] exact MOperation.
- [ ] exact args/type conversion.
- [ ] capture pre-state once.
- [ ] PRE at operation enter.
- [ ] observe-only; no blocking JaCaMo.
- [ ] POST only on matching successful exit.
- [ ] preserve `@pre`.
- [ ] OP_FAIL/abort → POST `SKIPPED`.
- [ ] duplicate/stale terminal rejected.

---

## P40.4 — Runtime ordering/history

Choose least invasive V2-compatible representation:

- [ ] reuse RuntimeTrace/verification projection where justified.
- [ ] otherwise dedicated trace evaluator, clearly distinguished from OCL.
- [ ] no speculative large runtime metamodel chỉ để lưu history.

Verify:

- [ ] start-before-terminal.
- [ ] same correlation.
- [ ] stream generation.
- [ ] case-specific ordering outside core.

---

## P40.5 — Cross-dimensional verification V2

Only approved/evidence-backed relations:

- [ ] Agent action ↔ environment operation.
- [ ] Agent ↔ Artifact accessibility/focus if represented.
- [ ] observable state ↔ belief relation if V2/source proves it.
- [ ] organisational goal ↔ agent goal if V2/source proves it.
- [ ] role/mission ↔ performed behavior only when rule is explicit.

No behavioral invariant inferred from structural EReference alone.

---

## P40.6 — Violation reporting

Every runtime violation should include:

- [ ] constraint ID/name.
- [ ] origin.
- [ ] checkpoint.
- [ ] outcome.
- [ ] USE context.
- [ ] RuntimeEvent ID.
- [ ] sequence.
- [ ] correlation.
- [ ] V2 SemanticId.
- [ ] source span/provenance.
- [ ] mapping/runtime rule ID.
- [ ] actionable explanation.

---

# Phase 41 — Case Studies V2 & Genericity

**Objective:** chứng minh V2 pipeline không chỉ chạy với một fixture.

## P41.1 — Auction migration

- [ ] source project imports.
- [ ] V2 semantic model.
- [ ] V2 mapping.
- [ ] V2 `.use`.
- [ ] V2 initial state.
- [ ] V2 trace.
- [ ] V2 OCL.
- [ ] runtime sync.
- [ ] positive scenario.
- [ ] negative scenario.
- [ ] PRE/POST where source/profile supports.
- [ ] reconnect/resync.
- [ ] exact violation navigation.

Do not invent `highestBid/currentBid` or operation names not present in the actual fixture.

---

## P41.2 — Case Study #2 migration

- [ ] import.
- [ ] transform.
- [ ] OCL where evidence exists.
- [ ] runtime supported subset.
- [ ] positive.
- [ ] negative.
- [ ] trace.
- [ ] reconnect.
- [ ] no core special case.

---

## P41.3 — Genericity audit

Search core production code for:

- [ ] Auction names.
- [ ] Case Study #2 names.
- [ ] object IDs.
- [ ] operation names.
- [ ] hard-coded runtime bindings.
- [ ] special-case branch by project name.
- [ ] V1 class names.
- [ ] V1 hashes.

All example-specific logic must remain in:

- [ ] example.
- [ ] fixture.
- [ ] case OCL/profile.
- [ ] explicit binding.
- [ ] test.

---

## P41.4 — Multi-case acceptance

- [ ] same production pipeline.
- [ ] same Mapping V2 engine.
- [ ] same Runtime Mapping V2 engine.
- [ ] no example-specific source dispatch.
- [ ] supported/unsupported boundaries explicit.

---

# Phase 42 — UI, Packaging & Compatibility Migration

**Objective:** user workflow và release resources phản ánh V2, không còn hiển thị V1 như active baseline.

## P42.1 — Workbench workflow

- [ ] Import JaCaMo Project.
- [ ] show active Metamodel V2 version/hash.
- [ ] show Mapping V2 compatibility.
- [ ] diagnostics.
- [ ] trace V2.
- [ ] load OCL.
- [ ] offline verification.
- [ ] runtime connect.
- [ ] runtime sync state.
- [ ] live violations.
- [ ] source navigation.

---

## P42.2 — Compatibility manifest

Record exact:

- [ ] plugin version.
- [ ] Java.
- [ ] Maven.
- [ ] USE version/commit.
- [ ] JaCaMo baseline if directly used.
- [ ] Jason.
- [ ] CArtAgO.
- [ ] Moise.
- [ ] Metamodel V2 version/hash.
- [ ] Mapping V2 version/hash.
- [ ] Runtime Mapping version/hash/provisional status.
- [ ] OCL profile hashes.

If project currently pins Jason 3.3.0 while upstream JaCaMo main uses another version, keep pin explicit until a deliberate compatibility update is tested.

---

## P42.3 — Resource packaging

Plugin JAR/ZIP must include the active canonical resources:

- [ ] V2 Ecore.
- [ ] Mapping V2.
- [ ] schemas.
- [ ] working/final manifests as applicable.
- [ ] Runtime Mapping.
- [ ] core OCL.
- [ ] compatibility metadata.
- [ ] release manifest.
- [ ] licenses.

V1 historical resources:

- [ ] either excluded from active package;
- [ ] or clearly placed under historical/compatibility namespace.

Không có hai file cùng “canonical” status.

---

## P42.4 — UI regression

- [ ] plugin load.
- [ ] import.
- [ ] rebuild.
- [ ] OCL load.
- [ ] full verify.
- [ ] runtime tab.
- [ ] disconnect/reconnect/resync.
- [ ] report export.
- [ ] no transformation logic inside UI.

---

# Phase 43 — Hardening, Security, Determinism & Performance

**Objective:** đóng các correctness gaps phát sinh từ V2 migration trước final freeze.

## P43.1 — Requirement → code → test traceability

For every V2 capability:

- [ ] requirement.
- [ ] implementation.
- [ ] tests.
- [ ] evidence.
- [ ] status.

Allowed:

- [ ] COMPLETE.
- [ ] SUPPORTED_SUBSET_COMPLETE.
- [ ] EXPLICITLY_UNSUPPORTED.
- [ ] OUT_OF_SCOPE.

---

## P43.2 — TODO/FIXME/stale V1 audit

- [ ] TODO.
- [ ] FIXME.
- [ ] `V1`.
- [ ] old namespace.
- [ ] old mapping IDs.
- [ ] obsolete projection IDs.
- [ ] old golden paths.
- [ ] dead migration code.
- [ ] duplicate V1/V2 dispatch.
- [ ] commented-out fallback.

No unresolved correctness TODO in active V2 path.

---

## P43.3 — Determinism

Same:

- [ ] project bytes.
- [ ] V2 Ecore.
- [ ] Mapping V2.
- [ ] plugin version.
- [ ] OCL profiles.

must yield same:

- [ ] SemanticIds.
- [ ] `.use`.
- [ ] `.cmd`.
- [ ] trace.
- [ ] generated OCL.
- [ ] diagnostics ordering.
- [ ] mapping decisions.

Runtime UUID/timestamps may be run-specific but semantics/correlation must remain deterministic where expected.

---

## P43.4 — Security

- [ ] path traversal.
- [ ] symlink escape.
- [ ] XML external entity/DTD.
- [ ] Java static analysis does not initialize project code.
- [ ] classpath/archive safety.
- [ ] case OCL path restricted.
- [ ] report export path handling.
- [ ] no arbitrary shell execution.
- [ ] logging avoids unnecessary sensitive data.

---

## P43.5 — Runtime resource/lifecycle

- [ ] listener cleanup.
- [ ] queue shutdown.
- [ ] scheduler cleanup.
- [ ] reconnect no duplicate subscription.
- [ ] workspace replacement isolation.
- [ ] operation correlation bounded cleanup.
- [ ] stale aliases retired.
- [ ] no old V1 target surviving V2 rebuild.

---

## P43.6 — Performance evidence

Measure:

- [ ] import time.
- [ ] Ecore/Mapping validation.
- [ ] transformation.
- [ ] OCL compile.
- [ ] full verification.
- [ ] runtime event→result latency.
- [ ] queue depth/high-watermark.
- [ ] memory.
- [ ] resync latency.

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
