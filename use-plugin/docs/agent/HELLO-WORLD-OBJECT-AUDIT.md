# Hello World — source, semantic identity và native USE object audit

Ngày audit: **2026-10-01**. Kết quả: **PASS cho audit và các regression gate**, không có nghĩa là mọi runtime concept đều được API/Bridge materialize đầy đủ.

Source được chọn là `jacamo/doc/tutorials/hello-world/code/helloworld/helloworld.jcm`. Không cộng các cấu hình `helloworld-grid.jcm`, `america.jcm`, `europe.jcm`, hoặc các ASL/Java không được cấu hình này sử dụng.

## Kết luận chính

**75 là 75 instance Artifact thật, có UUID khác nhau trong CArtAgO API**, không phải 75 khai báo trong `.jcm`, không phải tổng gộp ArtifactType/ArtifactInfo/property snapshot, và không phải object của lịch sử runtime.

- Source khai báo **5 GUIConsole**; cả 5 có mặt trong USE.
- CArtAgO tạo **35 artifact mặc định** cho 7 workspace, **26 AgentBodyArtifact** cho các lượt join và **5 AgentSessionArtifact**.
- MOISE/ORA4MAS tạo **4 board**: OrgBoard, GroupBoard, SchemeBoard và NormativeBoard.
- Tổng: **5 + 35 + 26 + 5 + 4 = 75**.
- Sau authoritative resync: **0 duplicate semantic identity, 0 stale runtime identity, 0 orphan/double link** trong inventory AUTO và FULL.
- Có hai lỗi production riêng đã được tìm thấy và sửa: baseline runtime bị hồi sinh khi resync, và Object Count không cập nhật theo atomic native event. Dispose cũng được dọn các alias/helper thuộc artifact.

## Evidence mới

Evidence dùng trong báo cáo được tạo lại trong full reactor verify, không lấy PASS của run lịch sử:

- [AUTO summary](../../target/hello-world-object-audit/AUTO-1790864406293/summary.json).
- [FULL summary](../../target/hello-world-object-audit/FULL-1790864504621/summary.json).
- [AUTO: toàn bộ object](../../target/hello-world-object-audit/AUTO-1790864406293/11-profile-reload-objects.json), [toàn bộ link](../../target/hello-world-object-audit/AUTO-1790864406293/11-profile-reload-links.json).
- [FULL: toàn bộ object](../../target/hello-world-object-audit/FULL-1790864504621/11-profile-reload-objects.json), [toàn bộ link](../../target/hello-world-object-audit/FULL-1790864504621/11-profile-reload-links.json).
- [AUTO: expected/actual cho đủ 27 class](../../target/hello-world-object-audit/AUTO-1790864406293/11-authoritative-identity-comparison.json).
- [FULL: expected/actual cho đủ 48 class](../../target/hello-world-object-audit/FULL-1790864504621/11-authoritative-identity-comparison.json).
- [AUTO atomic timeline](../../target/hello-world-object-audit/AUTO-1790864406293/atomic-count-timeline.json), [FULL atomic timeline](../../target/hello-world-object-audit/FULL-1790864504621/atomic-count-timeline.json).
- [Fresh XML test counts](../../target/hello-world-object-audit/final-evidence/fresh-xml-test-counts.json), [full verify log](../../target/hello-world-full-reactor-verify.log).

Mỗi object dump có tên USE, semantic ID, class, attributes, source semantic record/trace hoặc exact official runtime payload, runtime alias/trace, first materialized stateVersion, session/generation và trạng thái active. `firstMaterializedVersion=0` chỉ baseline static; snapshot đầu có version 1. Các tạo mới sau Start được lấy từ `AtomicStateChangedEvent`, không đoán theo tên object hoặc timestamp lấy mẫu.

Producer đã được dừng và consumer disconnect sau audit. Inventory cuối là **snapshot đã ghi**, không phải bằng chứng producer đang LIVE hiện tại.

## SOURCE HELLO WORLD

| Concept | Source khai báo | Exact API / runtime inventory | Evidence |
|---|---:|---:|---|
| JCM Agent | 5 | 5 Jason runtime agent | `helloworld.jcm`; `JaCaMoProject.getAgents`; Jason snapshot |
| AgentProgram | 2 ASL root khác nhau | 5 owner-qualified program | 4 agent dùng `hello.asl`, Bob dùng `hf.asl`; `Agent.parseAS` |
| PlanLibrary | Không khai báo tường minh | 5 | Mỗi parsed program có một `Agent.getPL()` |
| Plan viết tại project | 12 trong `hello.asl`, 2 trong `hf.asl` | 48 + 2 = 50 loaded plan instance | `Plan.getSrcInfo`, source URI/hash và owner agent |
| Plan từ include | 3 include/library | 95: 30 Cartago + 40 MOISE + 25 org-obedient | Official PlanLibrary: 6 + 8 + 5 plan mỗi agent |
| Plan tổng | Không chỉ đếm plan local | 145 = 50 + 95 | 31 mỗi Alice/Francois/Giacomo/Maria; 21 Bob |
| Trigger | Một trigger/plan | 145 | `Plan.getTrigger()`; FULL materialize, AUTO trace-only |
| PlanBodyElement | Body AST, không phải runtime execution | 303 | 98 project + 205 include; `Plan.getBody` / `getBodyNext` |
| Action | Action node của AST | 243: 108 external + 135 internal | Official `PlanBody.BodyType`; FULL reification |
| Initial ASL belief | 0 ordinary belief | 0 Belief MObject | `Agent.getInitialBels()`; 10 Rule được tách riêng |
| JCM initial belief configuration | 6 literal | Giữ trong 5 Agent.options | 4 `message`, Bob `country` + `message`; không giả là live BeliefBase |
| AgentGoal | `!start` trong `hf.asl` | 1 | `Agent.getInitialGoals()`; không đồng nhất với 13 MOISE goal |
| BeliefRule | Include có 2 rule/program | 10 | Official Jason parsed Rule; owner-qualified |
| Workspace declaration | 5 explicit | 6 declaration: 5 explicit + implicit root `main` | OfficialProjectAdapter; J03 |
| Runtime Workspace | Không chỉ declaration | 7: `main`, 5 country/common, `hello_org` | `CartagoEnvironment.getRootWSP` và child descriptors |
| Artifact declaration | 5 `gui` | 5 declaration | J04; không phải runtime ArtifactId |
| Project Java artifact type | 2 class có trong folder | 1 được dùng: `display.GUIConsole` | GridDisplay không dùng trong cấu hình chọn |
| Runtime Artifact type | Không chỉ Java trong project | 12 Java FQCN | `ArtifactId.getArtifactType`; 7 Cartago + GUIConsole + 4 MOISE |
| Runtime Artifact / ArtifactId | 5 artifact tùy biến được khai báo | 75 | `ICartagoController.getCurrentArtifacts`; full UUID + workspace identity |
| ArtifactInfo | Không có declaration | 75 info được controller revalidate khi capture | Dùng lấy operation/property; không tạo thêm 75 Artifact |
| ObservablePropertySnapshot | GUIConsole có `numMsg`; framework có property riêng | 84 | `ArtifactInfo.getObsProperties`: ArtifactObsProperty, không phải C08 ObsProperty |
| CartagoAgentIdentity | Không phải Agent declaration | 26 workspace-local identity | 5 Jason agent và launcher agents join nhiều workspace |
| Organization specification/deployment | 1 OS `o1`, 1 deployment `hello_org` | 1 + 1, khác lớp/ngữ nghĩa | `OS.loadOSFromURI`; J05 vs M01 |
| Group specification/deployment | 1 `team`, 1 `jacamo_team` | 1 + 1 | M05 vs J06; GroupBoard là Artifact runtime khác |
| Role | 4 explicit | 5, thêm abstract `soc` của official MOISE API | `SS.getRolesDef`; 4 super-role link tới `soc` |
| Scheme specification/deployment | 1 `hello_sch`, 1 `hello_eng` | 1 + 1 | M10 vs J07; SchemeBoard là Artifact runtime khác |
| Mission | 4 | 4 | Official Scheme.getMissions |
| OrganizationalGoal | 13 goal definition | 13 | 1 root + 12 leaf; mission goal references không tạo goal thứ hai |
| OrganizationalPlan | 1 sequence | 1 | Official Goal.getPlan; không phải Jason Plan |
| Norm | 4 obligation | 4 static Norm; runtime NPL facts là evidence | OS NS; không biến normative fact thành formal OCL |
| SS / FS / NS containers | 1 mỗi container | 3 supporting MObject | Official OS object graph |
| GroupRoleCardinality / SchemeMissionCardinality | 4 + 4 | 8 supporting MObject | Owner-qualified Cardinality, không phải 8 group/mission mới |
| SubGroup / RoleRelation / Link / Compatibility | 0 trong cấu hình này | 0 | MOISE graph; superclass link không phải RoleRelation object |

Static semantic contract có **933 unique semantic entities**. Authoritative runtime cuối có **848 fact records** (trong đó 619 operation descriptor là evidence-only); không được cộng các fact này thành số domain instance. CArtAgO có 192 materialized upsert fact cho workspace/agent identity/artifact/property, tạo thêm 13 shared Environment/ArtifactType target: tổng **205 CArtAgO MObject**.

Source project có `build.gradle` khai báo JaCaMo **1.3.0**; production Bridge dependency/fingerprint được audit chạy JaCaMo **1.3.1**, Jason **3.3.2**, CArtAgO **3.1**, MOISE **1.1**. Các số ở đây là evidence của distribution thực chạy, không phải khẳng định runtime tương đương bit-for-bit với một lần chạy Gradle 1.3.0 độc lập.

## USE MODEL và USE STATE — toàn bộ class

AUTO: **27 MClass, 41 MAssociation, 14 composition, 0 MOperation**.
FULL: **48 MClass, 56 MAssociation, 21 composition, 0 MOperation**.

`—` nghĩa là class bị policy AUTO loại khỏi MModel, không phải thiếu object của class đang có. Các count số dưới đây đều đã có expected identity set đối chiếu với actual identity set. D = domain concept; S = supporting/internal; T = type; C = configuration/declaration; P = snapshot. Classification không đồng nghĩa tất cả instance đều do người dùng viết: Plan có include và Role có default API role.

| Class | Rule | Loại | AUTO expected = actual | FULL expected = actual | Status / reason |
|---|---|---|---:|---:|---|
| Agent | J02 | D | 5 | 5 | EXPECTED; JCM declaration |
| AgentProgram | A01 | S | — | 5 | Program wrapper; AUTO evidence-only |
| PlanLibrary | A02 | S | 5 | 5 | EXPECTED; một library/program |
| Plan | A03 | D | 145 | 145 | EXPLAINED_EXPANSION; 50 local + 95 include |
| Trigger | A04 | S | — | 145 | One trigger/plan; AUTO evidence-only |
| PlanBodyElement | A05 | S | 303 | 303 | AST representation, không phải execution history |
| Action | A06/A07 | D | — | 243 | External/internal action AST, không phải ActionExec |
| Belief | A08 | D | — | 0 | Không có ordinary initial ASL belief |
| AgentGoal | A09 | D | — | 1 | Initial goal Bob |
| BeliefRule | A10 | D | — | 10 | 2 include rule/program |
| A17PlanOrderEntry | A17 | S | — | 145 | HELPER_OBJECT; một entry/plan |
| A19BodyOrderEntry | A19 | S | — | 303 | HELPER_OBJECT; một entry/body element |
| Environment | C01 | D | 1 | 1 | EXPECTED; exact environment identity |
| Workspace | C02 | D | 7 | 7 | EXPLAINED_EXPANSION; 5 explicit + main + org |
| ArtifactType | C03 | T | 12 | 12 | TYPE_CLASS; shared per environment/FQCN |
| Artifact | C04 | D | 75 | 75 | EXPLAINED_EXPANSION; classification bên dưới |
| Operation | C05 | D | — | 0 | 619 runtime descriptors vẫn EVIDENCE_ONLY ở channel hiện hành |
| BackingJavaOperation | C06 | S | — | 0 | Current model contract không mang backing-method DTO; không dựng MOperation giả |
| Guard | C07 | D | — | 0 | Không có typed static guard record trong accepted model |
| LiveObservableProperty | C08 | D | — | 0 | UNAVAILABLE_BY_API; fail closed |
| ObservablePropertySnapshot | C09 | P | 84 | 84 | SNAPSHOT_OBJECT; exact property ID; update không append history object |
| ArtifactInfo | C10 | S | — | 0 | Controller helper được dùng lúc capture; không có C10 static DTO trong accepted model |
| Signal | C11 | D | — | 0 | Runtime signal evidence không thành static signal instance |
| CartagoAgentIdentity | C12 | S | 26 | 26 | Workspace-local identity, không phải 26 JCM Agent |
| Organization | M01 | D | 1 | 1 | OS specification |
| StructuralSpecification | M02 | S | 1 | 1 | Supporting container |
| FunctionalSpecification | M03 | S | 1 | 1 | Supporting container |
| NormativeSpecification | M04 | S | 1 | 1 | Supporting container |
| Group | M05 | D | 1 | 1 | Group specification, khác GroupBoard |
| Role | M06 | D | 5 | 5 | 4 explicit + abstract soc |
| RoleRelation | M07 | S | 0 | 0 | Không có source record |
| Link | M08 | S | 0 | 0 | Không có source record |
| Compatibility | M09 | S | 0 | 0 | Không có source record |
| Scheme | M10 | D | 1 | 1 | Scheme specification |
| Mission | M11 | D | 4 | 4 | EXPECTED |
| OrganizationalGoal | M12 | D | 13 | 13 | Mission references được dedup theo official object identity |
| OrganizationalPlan | M13 | D | 1 | 1 | Một sequence plan |
| Norm | M14 | D | 4 | 4 | Static norm specification, không phải live NPL state |
| GroupRoleCardinality | M15 | S | 4 | 4 | HELPER_OBJECT, exact owner/member |
| SubGroupCardinality | M16 | S | 0 | 0 | Không có subgroup |
| SchemeMissionCardinality | M17 | S | 4 | 4 | HELPER_OBJECT, exact owner/member |
| WorkspaceDeclaration | J03 | C | — | 6 | 5 explicit + implicit root declaration |
| ArtifactDeclaration | J04 | C | — | 5 | Không nhân thêm C04 Artifact |
| OrganizationDeployment | J05 | C | — | 1 | Khác Organization specification |
| GroupDeployment | J06 | C | — | 1 | Khác Group specification |
| SchemeDeployment | J07 | C | — | 1 | Khác Scheme specification |
| InstitutionDeployment | J08 | C | — | 0 | Không có institution |
| ExactBindingEvidence | X01/helper | S | — | 0 | Không có explicit exact binding record; không fuzzy join |
| **Total MObject** | | | **704** | **1,570** | **0 duplicate, 0 stale sau authoritative snapshot** |
| **Total MLink** | | | **945** | **1,992** | **0 orphan, 0 double link; structure/multiplicity/ownership PASS** |

J09 raw role tuple (4), J10 raw focus tuple (11), J11/A11 provenance và A12–A15 runtime observations là trace/evidence, không có class runtime-history. X associations chỉ tạo link khi có exact binding record; raw tuple hoặc tên giống nhau không đủ evidence. Trong case này các X binding link không được tự phát minh.

FULL tăng **866 object**, gồm 418 source-level declaration/program/trigger/action/goal/rule reification và **448 order helper**. AUTO không chứa hai class order helper. FULL không có runtime API/data mạnh hơn AUTO.

## ARTIFACT INVESTIGATION

| Category / Java type | Before Start | After authoritative snapshot | Expected / actual | Explanation |
|---|---:|---:|---|---|
| `display.GUIConsole` | 5 | 5 | 5 / 5 | Source Artifact declaration |
| `cartago.WorkspaceArtifact` | 7 | 7 | 7 / 7 | Một default artifact/workspace |
| `cartago.SystemArtifact` | 7 | 7 | 7 / 7 | Một default artifact/workspace |
| `cartago.ManRepoArtifact` | 7 | 7 | 7 / 7 | Một default artifact/workspace |
| `cartago.tools.Console` | 7 | 7 | 7 / 7 | Một default artifact/workspace |
| `cartago.tools.TupleSpace` | 7 | 7 | 7 / 7 | `blackboard`, một/workspace |
| `cartago.AgentBodyArtifact` | 11 | 26 | 26 / 26 | Launcher/agent join identities, scoped theo workspace |
| `cartago.AgentSessionArtifact` | 5 | 5 | 5 / 5 | Một session artifact/Jason agent |
| `ora4mas.nopl.OrgBoard` | 1 | 1 | 1 / 1 | `hello_org` runtime board |
| `ora4mas.nopl.GroupBoard` | 1 | 1 | 1 / 1 | `jacamo_team` runtime board |
| `ora4mas.nopl.SchemeBoard` | 1 | 1 | 1 / 1 | `hello_eng` runtime board |
| `ora4mas.nopl.NormativeBoard` | 0 | 1 | 1 / 1 | Runtime organizational/normative coordination board |
| **Artifact total** | **59** | **75** | **75 / 75** | **Không có duplicate ArtifactId** |

Phân bố cuối: `/main` 15, `/main/brazil` 8, `/main/france` 8, `/main/hello_org` 14, `/main/italy` 10, `/main/jacamo` 11, `/main/usa` 9.

84 property snapshot là class riêng, không được gộp vào Artifact: GUIConsole 5, AgentBodyArtifact 27, AgentSessionArtifact 20, GroupBoard 9, OrgBoard 4, SchemeBoard 19. Các runtime board/default artifact là **internal framework Artifact instance thật**, không phải helper MObject bị mapping nhầm thành Artifact.

Exact bytecode/API evidence cho default workspace artifacts và agent body/session creation được lưu tại `target/hello-world-object-audit/api-evidence/cartago-Workspace.javap.txt`; constructor tạo 5 default artifacts, `joinWorkspace` tạo AgentBodyArtifact, session lifecycle tạo AgentSessionArtifact. Current snapshots và recorded creation events chứng minh từng UUID/type/workspace thực tế.

## Identity, dedup và stale-state call graph

| Đường | Exact behavior được kiểm tra | Kết quả |
|---|---|---|
| Static import | OfficialProjectAdapter → typed semantic contract → unique-ID validation → NativeUseStateBuilder | 499 AUTO / 1,365 FULL static object; không nhân theo runtime history |
| Initial runtime snapshot | CartagoSnapshotSource → getCurrentArtifacts/getArtifactInfo → normalized typed facts → NativeRuntimeProjector | Thêm 122 CArtAgO object trước Start; active state 621 / 1,487 |
| Live creation/property delta | Official ICartagoLogger callbacks → exact ArtifactId/property full ID → mutation engine upsert | 16 artifact mới; property change cập nhật object cùng identity |
| Authoritative resync | Same model revision → cùng pipeline/MSystem; reset + apply + reconcile COMPLETE Cartago snapshot | 75 artifact / 84 property / 26 identity; không hồi sinh bootstrap stale |
| Reconnect / repeated resync | Protocol snapshot replacement, aliases/ledger reset, same current MSystem | Count và identity set không tăng thêm |
| Repeated import | DefaultJaCaMoFacade compatibleResync, không build/system thứ hai cho cùng revision | Count vẫn 704 / 1,570 |
| Profile reload | Atomic constraint attachment/evaluation, không gọi state builder | Count không đổi |
| Replay / re-analysis | Isolated reconstruction, không activate vào Session | Live state hash và Session system không đổi |
| Dispose/recreate | Xóa artifact-owned property/helper và alias; tombstone old ID; recreate phải có UUID khác | Native regression + actual CArtAgO dispose/recreate PASS |

Key không phải display name:

- Workspace: environment ID + full workspace name + workspace UUID.
- Artifact: environment ID + full workspace name + artifact UUID; `name` chỉ dùng display/typed identity validation.
- ArtifactType: environment ID + exact Java FQCN.
- Property snapshot: artifact semantic ID + full property ID; value change không đổi identity.
- CartagoAgentIdentity: environment ID + workspace full name + global agent ID + local ID.
- Jason source AST: project + agent declaration owner + library/ordinal/canonical AST digest.
- Event acceptance còn ràng buộc session, generation, modelRevision và source sequence; duplicate event idempotent, stale/conflicting event fail closed.

Object name có human prefix và hash semantic ID. Hai `body_alice`/`system` ở workspace khác nhau không là duplicate. Một AST/member có source trace tới order helper khác là reification có chủ đích, không phải thêm một Plan/BodyElement cùng semantic ID.

## BUGS FOUND và fix

### 1. Bootstrap runtime instance bị giữ lại khi authoritative resync

Root cause: `resetToBaseline()` khôi phục cả CArtAgO instance đã có ở model bootstrap; snapshot mới chỉ upsert nên không xóa record bootstrap vắng mặt. Red test chứng minh Artifact expected 0 nhưng actual 1 sau COMPLETE empty snapshot.

Fix trong `NativeRuntimeMutationEngine` / `NativeRuntimeProjector`: sau upsert, reconcile exact identity của COMPLETE Cartago source; xóa obsolete property/artifact/agent/workspace/type/environment theo child-before-owner. PARTIAL/UNAVAILABLE không được suy ra absence. Artifact-owned Operation/Guard/BackingJavaOperation/ArtifactInfo/Signal cũng được dọn; alias trỏ tới object đã xóa được bỏ.

Regression: `CodeGroundedPhase7Test.authoritativeCartagoResyncMustNotResurrectArtifactsFromTheBootstrapModel`, `fullBootstrapSnapshotHelpersAreRemovedWithTheirDisposedArtifact`, `unavailableCartagoSnapshotMustNotPretendAbsenceOrDeleteBootstrapObjects`; dispose alias assertion trong `RuntimeVerificationCoordinatorTest`.

**Lỗi này có thật nhưng không phải nguyên nhân của 75 Artifact trong Hello World hiện hành:** accepted static Hello model không chứa Cartago bootstrap instance; 75 đến từ authoritative runtime records thật.

### 2. Object Count view giữ số cũ sau native atomic mutation

Root cause: `ObjectCountView` chỉ subscribe ObjectCreated/ObjectDestroyed, trong khi native writer publish `AtomicStateChangedEvent`. Red regression tạo Artifact/property snapshot qua direct API rồi atomic commit: Artifact expected 1, GUI count vẫn 0.

Fix: ObjectCountView subscribe atomic event và refresh từ đúng `fSystem.state()`. Không thay logic đếm/gộp class.

Regression: `NativeObjectCountViewTest`; HelloWorldSemanticInventoryIT so sánh actual ObjectCountView với current state ở mọi phase. Count Artifact vẫn tách riêng khỏi property/helper.

## FINAL CLEAN COUNTS và lifecycle

AUTO full-gate run, cùng một active Session system:

| Phase | stateVersion | MObject | MLink | Artifact | Note |
|---|---:|---:|---:|---:|---|
| Static materialization, trước runtime snapshot | 0 | 499 | 678 | 0 | Static semantic build; không có Cartago environment DTO trong model |
| MODEL_READY, chưa Start | 1 | 621 | 847 | 59 | Environment đã bootstrap; reasoning chưa start |
| OCL_READY | 2 | 621 | 847 | 59 | Load identity audit OCL không tạo object |
| Start producer ACK | 2 | 621 | 847 | 59 | ACK thật từ startup control |
| Sau live mutation | 130 | 689 | 930 | 75 | CartagoAgentIdentity event coverage còn partial |
| Authoritative snapshot | 131 | 704 | 945 | 75 | 26 agent identities; exact current identity parity |
| Resync lần nữa | 132 | 704 | 945 | 75 | Không tăng object |
| Reconnect | 133 | 704 | 945 | 75 | Không tăng object |
| Import cùng project lại | 134 | 704 | 945 | 75 | Không tăng object |
| Reload OCL | 135 | 704 | 945 | 75 | Không tăng object |
| Isolated replay/re-analysis | 135 | 704 | 945 | 75 | State hash không đổi; consumer disconnected |

FULL: static **1,365 object / 1,725 links**, MODEL_READY **1,487 / 1,894 links**, authoritative/final **1,570 / 1,992 links**; final stateVersion **56** trong full-gate run. Version/event count giữa hai JVM run không cần bằng nhau; artifact/identity/property semantic cardinality sau authoritative capture phải bằng nhau và đã được đối chiếu.

## Tests và repository

- Focused native/model/state/identity/resync/GUI-count/replay, bao gồm actual CArtAgO dispose/recreate: **87/87 PASS**.
- Full reactor `mvn -B -Djava.awt.headless=false verify`: **584/584 PASS**, 0 failure/error/skipped; **441 unit + 143 integration**. Gate kết thúc 21:25:03 +07.
- Plugin integration **13/13 PASS**, gồm Hello World AUTO/FULL **2/2**, existing GUI/native Session/OCL and live managed Auction gates.
- Package/release subset **6/6 PASS**: ReleasePackageIT 3, GuiPluginStagingIT 1, LegacyAuthorityPackagingIT 1, NativeRuntimePackagedReplayIT 1. Đây là subset của full gate, không cộng hai lần vào 584.
- `git diff --check`: PASS. Không commit/push; giữ toàn bộ thay đổi có trước audit.
- Hash trước/sau cho đủ **20 file** project Hello World: không đổi. Frozen Core/mapping manifest **14 file**: không đổi. JaCaMo working tree không có generated source/log mới; `examples/auction.zip` là untracked đã tồn tại trước audit.
- Output mới nằm trong `use-plugin/target/hello-world-object-audit`, runtime journal trong owned output dưới `use-plugin/target`; source gốc không được chỉnh. Các run lỗi/harness-debug cũ không bị xóa hay dùng làm PASS evidence.

## VERDICT và limitations

**EXPLAINED_EXPANSION**: 75 Artifact là đúng với runtime engine/API đã chạy; source có 5 GUIConsole nhưng framework/MOISE tạo thêm 70 Artifact instance. 145 Plan và 303 BodyElement cũng được truy tới exact source/include và owner, không phải runtime history hoặc double import.

**HELPER_OBJECT / SNAPSHOT_OBJECT**: SS/FS/NS/cardinalities và FULL order entries là supporting representation; 84 property snapshots là class riêng. AUTO không materialize FULL-only order helper.

**DUPLICATE_BUG / STALE_OBJECT**: không thấy trong final Hello state; stale-bootstrap bug riêng đã có red/green regression và fix. Object Count refresh bug đã sửa, nhưng view không hề gộp các class thành Artifact.

**UNSUPPORTED / INCOMPLETE**: không nâng kết luận lên complete-live-semantic verification. `runtime.events` là PARTIAL; Cartago join/quit callbacks hiện có evidence nhưng không upsert/delete mọi CartagoAgentIdentity live, vì vậy 11 → 26 identity chỉ trở nên đầy đủ sau authoritative resync. MOISE/NPL runtime records vẫn có evidence-only/partial coverage. C08 vẫn UNAVAILABLE_BY_API. FULL không tự bổ sung backing-operation/guard/signal DTO khi channel chưa cung cấp chúng. Recorded disconnect làm GAP/STALE đúng nghĩa; bundle trước disconnect replay có semantic parity, bundle chứa disconnect được đánh dấu `REPLAY_PARTIAL_COVERAGE_GAP`, không giả complete history.

Kết luận dựa trên đối chiếu source/API → semantic ID → mỗi object/link và snapshot identity parity, **không chỉ dựa vào tests PASS**.
