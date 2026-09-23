# Verdict

**METAMODEL MAPPING V1 = FROZEN**, ngày 2026-09-14, schema **1.1.0**.
Phạm vi là canonical Ecore → USE-side metamodel correspondence, không phải
Java/Jason/Moise program semantics hay concrete/runtime binding.
Core Ecore không đổi: SHA-256 `c0aafab786c5ff3fcb468aeaf1b18b62865292e6590ffca2b9b2e962a9067fe7`.

# Coverage

Đếm lại bằng XML/JSON parser, so sánh source identities và từng target declaration:

| Nhóm | Canonical / mapped / resolved | Coverage |
| --- | --- | --- |
| EClass | 37 / 37 / 37; 3 abstract | 100% |
| Declared EAttribute | 67 / 67 / 67 | 100% |
| EReference | 63 / 63 / 63; 27 containment | 100% |
| Direct inheritance | 14 / 14 / 14 | 100% |
| Projection contracts | 7 / 7 / 7 audited | 100% trong registry V1 |

Kiểm tra exact source kind, declaring owner, name, types/defaults, bounds,
source/target direction, containment, uniqueness/ordering, inheritance và projection anchors.
Không orphan/stale mapping; không duplicate identity hoặc ambiguous navigation,
kể cả namespace thừa kế. Ba tên clipped không phải declared EAttribute nên không
đưa vào denominator 67; không tuyên bố coverage của thuộc tính ẩn chưa biết.

# Ambiguities found

Source keys từ trước đã owner-qualified; lỗi nằm ở reverse USE roles sinh chỉ theo
feature name. Bốn cặp trùng được thay bằng owner-qualified roles:

| Bindings | Reverse navigation cũ | Tên role mới |
| --- | --- | --- |
| R035/R058 | AbsOperation.source_operation | source_Artifact_operation / source_ExternalAction_operation |
| R031/R046 | Artifact.source_artifact | source_Workspace_artifact / source_Agent_artifact |
| R023/R025 | TriggeringEvent.source_Splan | source_Scheme_Splan / source_OPlan_Splan |
| R047/R048 | TriggeringEvent.source_triggeredBy | source_Belief_triggeredBy / source_Goal_triggeredBy |

USE compiler còn phát hiện tên `from` ở FormationConstraints/Link và `Sequence`
ở OPlan không phải identifier hợp lệ trong vị trí attribute declaration.
Đây là lỗi target thực tế mà validator cũ bỏ lọt, không phải nhận xét theo trực giác.

# Fixes applied

- Giữ C/A/R/I IDs, source identities, Core bytes và forward reference roles.
- Đổi đúng tám reverse ends nói trên; không giữ lookup alias cũ bị trùng.
- Target-only escapes: `ecore_FormationConstraints_from`, `ecore_Link_from`,
  `ecore_OPlan_Sequence`. Bảng source↔target trong JSON giữ truy vết exact spelling.
- Thêm sourceOrdered/sourceUnique, bảo toàn ordered trên 41 forward ends nhiều giá trị.
- Thêm JSON Schema Draft 2020-12, closed object shapes, typed projection contracts,
  normative scope/policies; chuyển lỗi structural/projection từ bỏ lọt/warning thành FAIL.
- `schemaVersion` 1.0.0 → 1.1.0; additive contract/schema/ordering fields và sửa target
  không compile hợp lệ. Không đổi source metamodel version/hash. Consumer dùng các target
  strings cũ phải migrate; không hứa tương thích ngược cho các tên đã sửa.
- Context runtime đã có được đánh dấu LATER_LAYER_CONTEXT/NON_EXECUTABLE_LEGACY_TEMPLATE,
  không dùng để resolve metamodel identity. Không thêm runtime implementation.

# Projection audit

Tất cả có hướng **JACAMO_TO_USE**, `baselineMutation=false`. SourceElements là
canonical owner-qualified anchors; structuralBindings resolve duy nhất tới C/A/R.
TargetConcept được kiểm chứng qua USE model API/compiler, không qua tên class đoán.

| ID / purpose | Source / target hợp lệ | Assumptions và loss | Quyết định |
| --- | --- | --- | --- |
| VP001 ConcreteArtifactTypeProjection | Artifact, Artifact#className / C016 → MClass subtype template, base Artifact | Cần external implementation type đã resolve; không diễn tả Java behavior hoặc tạo instance identity | Giữ: cần một owner type cho VP002/003; hợp lệ như template |
| VP002 ObservablePropertyStateProjection | Artifact, ObsProperty, Artifact#obsproperty / C016,C018,R034 → MAttribute template trên VP001 | Cần name/type USE-compatible và không đụng inherited names; không đoán initialValue. Structural representation vẫn giữ; update semantics ngoài scope | Giữ: bổ sung typed attribute concept, không thay ObsProperty |
| VP003 CArtAgOOperationProjection | Artifact, AbsOperation và 4 subtypes, Artifact#operation / C016,C019,R035 → MOperation template trên VP001 | Cần complete ordered signature phù hợp USE. Core không có EOperation/EParameter; execution/guards/effects không được dịch | Giữ: operation metaconcept riêng, không nhầm Operation EClass với signature |
| VP004 ExternalActionOperationConsistency | ExternalAction#operation / R058 → MAssociation hiện có | Không resolve receiver. Trace VP003 là optional later layer; không mất structural link | Giữ như verification anchor/reuse; không emit association trùng |
| VP005 ObservablePropertyBeliefConsistency | ObsProperty#obsproperty / R037 → MAssociation hiện có | Không suy percept delivery/synchronization; VP002 không thay link; không mất cấu trúc | Giữ, direction ObsProperty→Belief |
| VP006 OrganisationalGoalAgentGoalConsistency | OGoal#OGoalToGoal / R028 → MAssociation hiện có | Không suy achievement/satisfaction; không mất cấu trúc | Giữ cho cross-dimensional correspondence |
| VP007 NormPreservation | Norm + type/timeConstraint/Nrole/NMission / C006,A004,A005,R012,R013 → class/attributes/associations | Giữ strings; không dịch deontic meaning/OCL; không mất cấu trúc | Giữ như preservation guard, không thêm transformation dư |

VP001–003 không hứa tồn tại concrete target trong baseline; đó là conditional
metamodel templates có điều kiện explicit. Fixture compiler chỉ dựng subtype,
attribute, operation signature giả định để kiểm tra tính hợp lệ của target concepts;
không có JaCaMo objects, case-study binding hoặc MSystemState.

# Remaining limitations

Không còn known **structural metamodel-mapping issue** trong phạm vi V1 đã định nghĩa.
Không hiểu câu này thành lossless runtime/program verification:

- Ba datatype clipped và default isBroadcast tiếp tục unresolved theo Core; no guess.
- Namespace/attribute bounds mặc định thuộc canonical reconstruction, không phải bằng chứng tác giả.
- USE Integer không tự giới hạn range EInt; strings không mã hóa đầy đủ Jason/Java/Moise semantics.
- Default materialization, global instance containment validity và conditional template
  instantiation thuộc tầng sau. Contract không suy chúng từ per-end multiplicity.
- Receiver/object binding, live state/events, concrete case study, OCL/deontic execution
  **OUT OF SCOPE**, không phải blocker bị bỏ qua để đóng task.
- Target gate pin USE 7.5.0 tại commit dưới đây; nâng USE version phải chạy lại gate.

# Validation results

- Canonical Ecore parse/baseline/EMF Diagnostician PASS; 20 Ecore mutation controls PASS.
- JSON Schema và semantic resolution PASS; 0 errors, 0 structural warnings.
- 14 unittest methods PASS: 1 valid control và **50 mutation scenarios** trong 13 methods
  còn lại (loops/subtests). Có missing/stale source, wrong kind/owner, duplicate identity,
  bad reference/inheritance, malformed/unknown projection, missing projection anchors/targets,
  schema/version drift, reverse/inherited namespace collision và reserved identifiers.
- USE **7.5.0**, commit `30d480dbcca2f404b1350039516a56f46c1efb1f`: baseline compile PASS
  (37 classes, 67 attributes, 63 associations, 14 inheritance, 41 ordered ends, 0 operations).
- Hypothetical projection type fixture compile PASS (thêm 1 subtype/attribute/operation).
- Bốn reverse collision và ba reserved attribute regressions được USE compiler từ chối
  độc lập; không chỉ dựa vào validator Python tự chấm PASS.

Evidence: [semantic report](../audit/mapping-validation.json),
[tests log](../audit/mapping-tests.txt), [USE log](../audit/use-mapping-validation.txt).
Reproduce commands: [README](README.md). Không chạy upstream USE test suite; build dùng
`-DskipTests` để lấy compiler, sau đó thực hiện các gate của workspace này.

USE-side reference inspected at the pinned revision:
[MModel.addAssociation](https://github.com/useocl/use/blob/30d480dbcca2f404b1350039516a56f46c1efb1f/use-core/src/main/java/org/tzi/use/uml/mm/MModel.java)
checks navigable role conflicts;
[USE grammar](https://github.com/useocl/use/blob/30d480dbcca2f404b1350039516a56f46c1efb1f/use-core/src/main/resources/grammars/base/USEBase.gpart)
defines attribute/association-end declarations;
[lexer](https://github.com/useocl/use/blob/30d480dbcca2f404b1350039516a56f46c1efb1f/use-core/src/main/resources/grammars/base/OCLLexerRules.gpart)
defines identifiers. Compiler results, not wording alone, establish the target fixes.

# Freeze decision

**FROZEN**: schema PASS, 100% declared source coverage, all references/inheritance
uniquely resolved, seven projection contracts audited, no stale/orphan/ambiguous mapping,
positive and negative gates PASS, docs aligned, Core unchanged.
[freeze-manifest.json](freeze-manifest.json) pins source/mapping/schema/checker/test evidence
bytes. Any change to pinned artifacts invalidates this freeze evidence until all gates
are rerun and the manifest deliberately refreshed. This is not a runtime certification.
