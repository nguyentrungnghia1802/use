# Metamodel Mapping V1

**FROZEN — schema 1.1.0, 2026-09-14.** Canonical:
[jacamo-use-mapping-v1.json](jacamo-use-mapping-v1.json); structural schema:
[jacamo-use-mapping.schema.json](jacamo-use-mapping.schema.json).
[Audit và freeze evidence](METAMODEL-MAPPING-AUDIT.md).

Contract trả lời: **JaCaMo metamodel element nào tương ứng với USE-side concept nào?**
Nguồn duy nhất là [Core/Metamodel/JaCaMo-Metamodel.ecore](../Metamodel/JaCaMo-Metamodel.ecore),
path trong JSON thuộc repository mapping gốc; checkout này dùng adapter module-local. Fingerprint Ecore giữ nguyên
`c0aafab786c5ff3fcb468aeaf1b18b62865292e6590ffca2b9b2e962a9067fe7`.
Không có `bdiMetamodelVersion` riêng; baseline được nhận diện bằng package/URI/hash.

## Coverage và identity

| Loại | Coverage | Target |
| --- | --- | --- |
| C / EClass | 37/37 | MClass, giữ abstract/concrete |
| A / EAttribute | 67/67 khai báo | MAttribute; EString→String, EInt→Integer, EBoolean→Boolean |
| R / EReference | 63/63 | 27 composition + 36 association; 41 forward ends many-valued ordered |
| I / eSuperType | 14/14 | Subclass → Superclass |
| VP / projection | 7/7 audit | 3 conditional type templates + 4 reuse/verification anchors |

Identity là `dSML4JaCaMo::<Class>`, `dSML4JaCaMo::<DeclaringOwner>#<feature>` hoặc
`dSML4JaCaMo::<Subclass>->super::<Superclass>`. Bare `operation` không phải lookup key.
`Artifact#operation` và `ExternalAction#operation` là hai nguồn khác nhau.
Inherited attribute như Scheme.id resolve qua Organisation#id, không tạo bản khai báo thứ hai.
C/A/R/I/VP IDs là stable binding IDs; không thay source identity bằng label hiển thị.

Forward reference names giữ nguyên. Reverse roles mặc định `source_<feature>`;
chỉ tám ends bị collision đổi thành `source_<Owner>_<feature>`:

| Cặp | Reverse role mới |
| --- | --- |
| R035 / R058 | source_Artifact_operation / source_ExternalAction_operation |
| R031 / R046 | source_Workspace_artifact / source_Agent_artifact |
| R023 / R025 | source_Scheme_Splan / source_OPlan_Splan |
| R047 / R048 | source_Belief_triggeredBy / source_Goal_triggeredBy |

Reverse roles là USE-side support, không phải eOpposite của paper. Không hỗ trợ
lookup bằng alias cũ bị trùng. Validator kiểm tra cả namespace có kế thừa và attribute/role collision.

USE lexer từ chối ba attribute names; chỉ target được escape có thể truy ngược:

| Source identity | Target attribute |
| --- | --- |
| FormationConstraints#from | FormationConstraints.ecore_FormationConstraints_from |
| Link#from | Link.ecore_Link_from |
| OPlan#Sequence | OPlan.ecore_OPlan_Sequence |

Các tên nguồn/Ecore không đổi. JSON `targetAttributeEscapes` là bảng tên target chính thức.
Schema 1.1.0 thêm contract/projection anchors/ordering metadata; không cần schema major
vì cấu trúc mới là bổ sung và các đổi tên sửa target vốn không compile hợp lệ.
Consumer dùng reverse strings/ba attribute target cũ phải migrate theo hai bảng trên;
không tuyên bố các target names này backward-compatible. Forward/source IDs được giữ.

## Bảy projection

`metamodelContract` là phần máy kiểm tra: sourceElements, structuralBindings,
targetConcept/baseClass, dependsOn, mode, direction, assumptions, informationLoss.
Văn bản `source`/`target` cũ là mô tả, không dùng như bare-name resolver.

| ID | Ý nghĩa / target / điều kiện |
| --- | --- |
| VP001 | Artifact + className → template MClass subtype of Artifact; cần external implementation type đã resolve; không sinh concrete class trong baseline |
| VP002 | Artifact.obsproperty + ObsProperty → template MAttribute trên subtype VP001; cần external property name/type; vẫn giữ structural objects/links |
| VP003 | Artifact.operation + AbsOperation/subtypes → template MOperation trên subtype VP001; cần complete ordered signature hợp lệ với USE; không coi Operation EClass là EOperation |
| VP004 | Reuse R058 ExternalAction.operation→AbsOperation; trace tới VP003 nếu có thuộc tầng sau; không thêm association |
| VP005 | Reuse R037 ObsProperty.obsproperty→Belief; không đảo direction hoặc suy đồng bộ runtime |
| VP006 | Reuse R028 OGoal.OGoalToGoal→Goal; không suy goal achievement |
| VP007 | Reuse Norm/C006, A004/A005, R012/R013; giữ strings/links, không chuyển deontic labels thành OCL |

Tất cả anchors resolve từ Ecore và target concept/structural binding có thật. VP001–003
là **metamodel templates**, không phải concrete operations đã được bind. USE type fixture
kiểm tra subtype + attribute + operation declaration; không chứa JaCaMo case study hay objects.
Không projection nào xóa baseline information. Java behavior/percept synchronization/
goal satisfaction/deontic semantics không được đại diện đầy đủ và không thuộc lời hứa V1.

## Multiplicity, ordering và unresolved

Copy exact forward bounds/containment. USE diamond ở firstEnd; inverse bound 0..1
cho composition, * cho non-containment. Copy sourceOrdered/sourceUnique; many-valued
forward ends giữ ordered=true, scalar ends không thêm modifier ordered. Classifier-level
containment recursion theo Ecore vẫn được giữ. Instance single-container/acyclic validation
là trách nhiệm tầng thực thi, không thêm OCL hay workaround vào metamodel mapping.

Ba tên `ObsProperty.initialValue`, `AbsOperation.paramName`, `TriggeringEvent.addAndDel`
chỉ có annotation, không có EAttribute: giữ NOT_MAPPED_NOT_AN_EATTRIBUTE. A062 isBroadcast
không có explicit default. Không lấy intrinsic false làm tác giả default.
Attribute bounds 0..1/namespace là canonical reconstruction choices, không chứng minh paper.
13 explicit defaults được giữ trong mapping metadata; materialization unset/effective values
nằm ngoài phạm vi. Integer USE rộng hơn EInt; không tuyên bố runtime range enforcement.

## Validation và OUT OF SCOPE

Schema Draft 2020-12 đóng cấu trúc; semantic validator tính coverage từ Ecore, kiểm
source kind/owner/exact key, types/defaults, target/forward/reverse ends, inheritance,
duplicate/stale/orphan identity, inherited namespace, ordering và projection anchors/dependencies.
Schema kiểm version và normative contract; malformed projection không còn chỉ là warning.
Errors trả exit 1. Các codes chính: SCHEMA_INVALID, MISSING_SOURCE, SOURCE_KEY_MISMATCH,
BINDING_KIND, DUPLICATE_BINDING, COVERAGE_MISMATCH, TARGET_MISMATCH, ROLE_COLLISION,
INHERITED_NAME_COLLISION, INHERITANCE_MISMATCH, ORDERING_MISMATCH, RECONCILE_REQUIRED.

**OUT OF SCOPE / LATER LAYER:** auctioneer→auction1, concrete-model binding,
receiver/object runtime resolver, live JaCaMo state, current USE MSystemState,
event subscription/synchronization, parser integration, OCL/deontic execution.
Existing runtimeMaterialization/runtimeLinkCommand/architecture project examples remain
non-executable legacy context per contract. They do not determine mapping identity and
are not requirements of the frozen layer. No runtime logic was implemented.

## Reproduce checks

Run from the root of **this USE repository**, at any filesystem location, with JDK 21 and Maven:

```powershell
mvn --batch-mode -pl use-plugin -am '-Dtest=MappingTransformationTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn --batch-mode clean verify
```

The first command validates frozen hashes, schema, Ecore identities, negative mutations,
and generated USE models through this reactor's USE compiler. The second includes Auction,
runtime and packaged plugin load checks. `MappingLoader.loadCanonical` resolves module-local
paths; it does not interpret the original mapping repository's `sourceMetamodel.path` as a
path in this checkout. Ecore here is `Core/Metamodel/JaCaMo-Metamodel.ecore` relative to
use-plugin; canonical mapping and freeze JSON bytes remain unchanged.

The Python/EMF logs mentioned in METAMODEL-MAPPING-AUDIT.md are historical evidence
from the original mapping repository. Those scripts are not shipped in this checkout;
this hotfix does not claim to rerun that separate Python/EMF suite.

When Ecore/schema/mapping changes, freeze evidence becomes stale: rerun all gates,
review structural diff and projections, reconcile hash/version deliberately. Never
update only the fingerprint to silence RECONCILE_REQUIRED. See [freeze manifest](freeze-manifest.json).
