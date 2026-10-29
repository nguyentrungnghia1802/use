# Semantic authority và compatibility routing

Profile được chọn tại Import; default vẫn là `NATIVE_CURRENT`. Đây là quyết định
giữ compatibility, không phải chuyển tất cả project sang CSL/iStar mới.

| Route | Canonical M1 | Runtime authority | Replay | Trạng thái |
|---|---|---|---|---|
| NATIVE_CURRENT | Native JaCaMo specification | Native projector + USE OCL | schema2.0 | KEEP |
| CSL_OBSERVER_V1/V2 | CslSchema + RequirementModel | SemanticRuntimeVerifier → TemporalGoalMonitor, native OCL | schema2.0 + mapping.json | KEEP cho compatibility |
| JACAMO_DECLARATIVE_XML | Original CslModel + GoalModel từ explicit inputs/XML | Generic XML mapping, neutral capability registry, original use4Goal + native OCL | schema3.3/3.4, exact hash fence | Production authored/generated route |
| USE4GOAL_OBSERVED_V1 | Original CslModel + GoalModel trong SemanticProgram | RuntimeEngineSessionOwner → Use4GoalRuntimeSessionAdapter → original marking/propagation; native OCL | schema3.0 + program.json, exact implementation hashes | Route mới, opt-in |

`CodeGroundedNativePipeline.buildCanonicalProgram` gọi original backend trực tiếp;
không chuyển qua `CslSchema`, `RequirementModel` hoặc legacy `Csl2UseTranslator`.
`CanonicalUseBindingAdapter` chỉ gắn source/native trace và binding, không tạo M1
thứ hai. `SemanticProgram` giữ original M1; `ProjectionBindingRegistry` là read-only
inventory của chính MModel đã compile, không phải editable semantic representation.

`NativeRuntimeMutationEngine` từ chối model mang cả `CslMonitorProfile` và original
backend/program. Không có catch/retry chuyển sang local engine nếu native typecheck,
original validator hoặc capability admission thất bại. Không có shadow live writer.
Parity sessions thuộc test/offline, không activate song song vào Session của USE.

## Inventory và quyết định theo owner

Đường dẫn sau tương đối với `src/main/java/org/tzi/use/plugins/jacamo/`.
Inventory executable gồm mọi line tham chiếu trong
`verification/semantic-migration/phase-19/legacy-callers.json`.

| File/class | Callers/role hiện tại | Quyết định và điều kiện retire |
|---|---|---|
| semantics/csl/CslSchema | CslMonitorConfiguration, CslUseBinding, OrganizationalMappingRules, structural policy | RETIRE_LATER khỏi APIs cho program mới; KEEP cho old schemas/config/readers |
| semantics/csl/CslSchemaValidator | CslSchema/End, original custom structural policy | KEEP; không dùng thay original CslSemanticValidator |
| semantics/csl/CslStructuralPolicy | CslSchema, OrganizationalMappingRules | KEEP cho old nesting/cardinality/compatibility policy |
| semantics/csl/CslStructuralPolicyCompiler | CslUseBinding | KEEP cho old backend; original backend có admission/semantics riêng |
| semantics/csl/CslUseBinding | NativeUseModelBuilder/StateBuilder, MutationEngine, checkpoint old branches, registry/audit | KEEP; chỉ branch CSL_OBSERVER, không canonical M1 |
| semantics/istar/RequirementModel | config, old mapping/binders, monitor, TransitionProfile | RETIRE_LATER cho usage mới; KEEP historical readers/offline |
| semantics/istar/TemporalGoalMonitor | SemanticRuntimeVerifier, BoundedExplorer | KEEP cho V1/V2/explorer; cấm làm original recurrence authority |
| semantics/istar/RequirementBindingResolver | SemanticRuntimeVerifier | KEEP old binding; không thay canonical incarnation/context adapter |
| semantics/istar/TaskBinding | CslMonitorConfiguration, JasonAbstractionPolicy | KEEP authored old invocation/abstraction profile |
| semantics/istar/MoiseRequirementBinder | old authored functional observers | KEEP; không tự suy từ MOISE goal sang satisfaction |
| semantics/istar/OPlanOutcomeMapper | old authored outcome-only abstraction | KEEP; order descriptor vẫn riêng, không scheduling equivalence |
| semantics/profile/CslMonitorConfiguration | old import/config/MappingBundle | KEEP version1.0; MappingRuleSet2.0 riêng, unknown version reject |
| semantics/mapping/OrganizationalMappingRules | SemanticTransformationService.fromOfficial, authored old configs | KEEP; canonical JaCaMoCslMappingRules riêng |
| semantics/mapping/MappingBundle + Codec | old official/UML frontends, roundtrip, replay2.0 | KEEP; không tự đổi thành SemanticProgram |
| semantics/mapping/SemanticTransformationService | old official/UML transformation/roundtrip | KEEP; new profile dùng JaCaMoStaticMappingAdapter |
| semantics/runtime/SemanticObservationMapper + LegacyObservationMappingBindings | MutationEngine CSL_OBSERVER branch | KEEP old source/quality semantics |
| semantics/runtime/SemanticRuntimeVerifier | RuntimeVerificationCoordinator khi cslProfile | KEEP; originalEngine và local verifier loại trừ nhau |
| semantics/exploration/TransitionProfile + BoundedExplorer | finite authored offline exploration | KEEP; không tuyên bố original iStar explorer equivalence |
| semantics/evidence/SemanticMappingAttachment | old MModel mapping.json metadata | KEEP old replay; original program có codec riêng |
| codegrounded/runtime/NativeOperationCheckpoints | old and canonical operation admission | KEEP shared correlation/pins; canonical exact bindings + deferred native invocation path |
| codegrounded/CodeGroundedNativePipeline + DefaultJaCaMoFacade | explicit import routes | KEEP explicit selection; no fallback/default switch |
| codegrounded/runtime/NativeRuntimeReplay + NativeRuntimeReanalysis | versioned offline readers | KEEP old2.0, new3.0 exact engine; canonical retrospective reanalysis hiện reject rõ |
| semantics/binding/ProjectionBindingRegistry | native/old/new view inventory | KEEP; read-only, không recurrence hoặc MSystem |

## Expected differences — không gọi là parity tương đương

| ID | Tình huống | CSL_OBSERVER/local | Original profile | Evidence/gate |
|---|---|---|---|---|
| ED01 | NONE active, known false | VIOLATED | GoalMarking PENDING | SemanticAuthorityRoutingTest; full original oracle Phase7 |
| ED02 | Task exact pre=true/post=false | local known invocation FAIL → VIOLATED | TaskMarking(preSeen=true,postSeen=false) PENDING; invocation FAIL riêng | SemanticAuthorityRoutingTest, CanonicalAsyncInvocationTest, CanonicalReplayTest |
| ED03 | Pre được thấy trước, post=true ở cut sau | local tuple không coi pre lịch sử là valid invocation contract | original TaskMarking tích lũy preSeen/postSeen, có thể FULFILLED | OriginalRuntimeSessionTest, routing test; không thay invocation correlation |
| ED04 | Task đã FULFILLED, tuple false sau đó | local Task có thể VIOLATED cho invocation mới | original historical postSeen latch vẫn FULFILLED | routing test + original Task parity; UI tách hai kết quả |
| ED05 | Unknown/undefined/partial/gap | local seven-valued quality | original Boolean core không nhận unknown tuple; quality adapter giữ raw history riêng | ObservationQualityGate, CanonicalRuntimeAdmissionTest, CanonicalReplayTest |
| ED06 | Cyclic/ambiguous/full-stack topology, HELP/HURT verdict | old model có policy riêng | bounded admission, không claim mọi grammar feature chạy | IStarFrontendParityTest, OriginalRuntimeSessionTest, CanonicalGoalMappingTest |
| ED07 | SEQUENCE/PARALLEL | old/new execution descriptor giữ operator/order | outcome-only explicit abstraction; AND không biểu diễn scheduling | CanonicalGoalMappingTest; old outcome tests |
| ED08 | Recorded schema2.0 vs3.0 | original local recurrence của recording cũ | exact canonical implementation/profile/hash; incompatible version reject | old replay suites + CanonicalReplayTest |

Source binding, population coverage, runtime identity, NPL observation và policy pause
vẫn thuộc adapters/coordinator. Original validators chịu well-formedness của original
CSL/iStar M1. Shared USE OCL là production backend cho cả hai route; không weak
regex evaluator. Không mang legacy runtime `CslSystemState`/translator vào route mới.

## Deprecation/rollback gate

Không xóa semantic source, không loại reader cũ khỏi JAR, không đổi default tại
Phase19. Không gắn `@Deprecated` vào shared APIs khi offline/explorer còn dùng:
điều đó tạo tín hiệu migration sai. Usage mới dùng canonical route đã có.

Owner chịu trách nhiệm cho retire sau này là maintainer profile/recording format.
Điều kiện tối thiểu: caller inventory bằng không hoặc có adapter cùng version,
golden recording/source parity được giữ, negative/roundtrip/explorer tests pass,
package và live gates20–22 pass, documented upgrade policy riêng. ED01–ED08 là
khác biệt chủ ý, không thể lấy new status để ghi đè expected của old profile.

Rollback là chọn lại old profile tại Import và dùng reader đúng schema; không reset
source hoặc sửa recordings. Physical deletion không thuộc Definition of Done.

## Explicit case-route retirement (current)

Case-specific Java implementations and source probes were removed from production
and retained only in `docs/agent/retired-case-java` with source hashes. The old
case profile is unsupported; portable case program schema 1.1 / archive 3.1–3.2
are rejected, never interpreted as declarative XML. XML replay still requires
exact current executable fingerprints. This intentional compatibility change
follows the requirement to prohibit case-specific production hardcoding; it is
not a claim that archived legacy tests still execute or that old recordings migrate.
