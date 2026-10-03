# TASK — Refactor JaCaMo → USE Domain / Runtime-State Projection

## 0. Mục tiêu

Refactor transformation JaCaMo → USE theo đúng kiến trúc sau:

> **JaCaMo chịu trách nhiệm execution. USE chỉ nhận domain/runtime state cần thiết để verification bằng OCL/invariants.**

Không copy 1:1 metamodel, Java classes, parser objects, runtime bookkeeping objects hoặc internal execution structures của JaCaMo sang USE.

Target USE model phải là một **domain-specific semantic projection**, gọn, dễ đọc, giữ đúng identity/relation/state cần kiểm chứng.

---

# 1. Phạm vi task

## 1.1. Làm trong task này

- Refactor Class Model / Object Model / Association / Attribute / Link được sinh từ:
  - JCM
  - Moise structural specification
  - CArtAgO Artifact runtime/type information
  - Agent declarations / agent program identity
- Giữ trace source → target.
- Giữ runtime object identity và relation cần thiết.
- Loại bỏ các class kỹ thuật/internal không cần cho state verification.
- Chuẩn bị boundary rõ ràng để Goal Model được làm riêng sau.
- Norm không được sinh thành class/object; phải đi về OCL layer.
- Chạy regression + case-study acceptance tests.

## 1.2. KHÔNG làm trong task này

- Không chuyển Jason `Plan`, `Trigger`, `PlanBody`, `PlanLibrary`, `Action`, `Intention`, `Option`, `ActionExec` thành USE class.
- Không chuyển CArtAgO `@OPERATION` thành USE `MOperation`.
- Không copy Java helper/private implementation fields nếu chúng không phải public runtime state cần verify.
- Không đưa Scheme / Goal / Mission vào USE Class Model.
- Không xây Goal View hoàn chỉnh trong task này.
- Không sửa JaCaMo/Jason/CArtAgO/Moise core.
- Không hardcode theo Auction, Hello World, House Building hoặc bất kỳ case study cụ thể nào.

---

# 2. Nguyên tắc bắt buộc

## 2.1. Semantic projection, không phải metamodel copy

Sai:

```text
Moise Organization → class Organization
Moise Role         → class Role
Moise Norm         → class Norm

Jason Plan         → class Plan
Jason PlanLibrary  → class PlanLibrary

CArtAgO ObsPropertySnapshot → class ObservablePropertySnapshot
```

Đúng:

```text
source semantic/domain element
        ↓
chọn representation phù hợp cho verification
        ↓
USE Class / Attribute / Association / Object / Link / OCL
```

## 2.2. Chỉ tạo `MClass` cho domain/runtime classifier thực sự cần tồn tại như một type

Một Java object/class tồn tại ở JaCaMo KHÔNG phải lý do đủ để sinh một USE class.

Mỗi class target phải trả lời được:

> Class này đại diện cho domain/runtime concept nào cần hiển thị hoặc kiểm chứng trong USE?

Nếu không trả lời được rõ ràng → không sinh class.

## 2.3. Phân biệt Type và Runtime Object

Ví dụ:

```text
organisation specification id="auction"
→ USE class auction : organisation

JCM:
organisation aorg : auction-os.xml
→ USE object aorg : auction
```

Không tạo thêm:

```text
AuctionOrganizationInstance
```

Tương tự:

```text
group-specification id="auctionGroup"
→ class auctionGroup : group

JCM:
group agrp : auctionGroup
→ object agrp : auctionGroup
```

Không tạo:

```text
AuctionGroupInstance
```

---

# 3. Mapping Contract — JCM

## 3.1. MAS

JCM:

```text
mas X {
    ...
}
```

Mapping:

```text
X → USE model/system/root name
```

KHÔNG tạo:

```text
class MAS
class XMas
```

MAS chỉ là root/container của model.

## 3.2. Agent program/type

JCM:

```text
agent bob : auction_capabilities.asl
agent alice : auction_capabilities.asl
```

Mapping tổng quát:

```text
<asl source basename> → USE class <basename> : agent
```

Ví dụ:

```text
auction_capabilities.asl
→ class auction_capabilities : agent
```

Runtime declarations:

```text
bob   → object bob   : auction_capabilities
alice → object alice : auction_capabilities
```

Nếu nhiều agents dùng cùng một `.asl`, tất cả phải là instances của cùng một agent-program class.

KHÔNG tạo class riêng cho từng agent declaration.

Sai:

```text
class Bob
class Alice
```

Đúng:

```text
class auction_capabilities : agent

bob   : auction_capabilities
alice : auction_capabilities
```

## 3.3. Nội dung `.asl`

Nội dung AgentSpeak bên trong `.asl` KHÔNG sinh USE Class Model elements.

Không tạo:

```text
Plan
PlanLibrary
Trigger
PlanBodyElement
Action
Event
Intention
Option
ActionExec
```

Jason vẫn parse và execute `.asl`.

USE chỉ nhận runtime/domain state khi state đó cần verification.

Nếu sau này cần Goal Model hoặc agent-program verification, làm bằng task riêng.

---

# 4. Mapping Contract — Moise Structural Specification

## 4.1. Organisation

Moise:

```xml
<organisational-specification id="X">
```

Mapping:

```text
class X : organisation
```

Ví dụ:

```xml
<organisational-specification id="auction">
```

→

```text
class auction : organisation
```

`organisation` là semantic kind/tag của target class.

Không được tạo generic class `Organization` rồi tạo object `auction` chỉ vì source metamodel có class `OS`.

## 4.2. Role definitions

Moise:

```xml
<role id="R"/>
```

Mapping:

```text
class R : role
```

Ví dụ:

```xml
<role id="auctioneer"/>
<role id="participant"/>
```

→

```text
class auctioneer : role
class participant : role
```

Không tạo generic class `Role`.

Không tạo class kỹ thuật kiểu `AuctionRoleEnactment`.

## 4.3. Group specification

Moise:

```xml
<group-specification id="G">
```

Mapping:

```text
class G : group
```

Ví dụ:

```xml
<group-specification id="auctionGroup">
```

→

```text
class auctionGroup : group
```

Không tạo `GroupInstance` class riêng.

Runtime JCM instance:

```text
group agrp : auctionGroup
```

→

```text
agrp : auctionGroup
```

## 4.4. Group–Role relation + cardinality

Ví dụ:

```xml
<group-specification id="auctionGroup">
  <roles>
    <role id="auctioneer"  min="1" max="1"/>
    <role id="participant" min="0" max="300"/>
  </roles>
</group-specification>
```

Hai `role` ở đây là references tới role definitions đã có.

KHÔNG tạo thêm classes.

Phải giữ relation:

```text
auctionGroup ↔ auctioneer
auctionGroup ↔ participant
```

và giữ cardinality:

```text
auctioneer  : 1..1
participant : 0..300
```

Representation target:

- `MAssociation` / association ends / multiplicity;
- nếu USE API cần OCL bổ sung để biểu diễn bound đặc biệt thì thêm invariant tương ứng;
- không reify relation thành class trừ khi USE bắt buộc về kỹ thuật và có lý do rõ ràng. Nếu buộc phải reify nội bộ, class đó KHÔNG được xuất hiện như domain class trong Class Browser.

---

# 5. Mapping Contract — JCM Organisation Runtime

Ví dụ tổng quát:

```text
organisation aorg : auction-os.xml {
    group agrp : auctionGroup {
        players:
            bob   auctioneer
            alice participant
    }
}
```

Phải resolve `auction-os.xml` → organisational-specification id.

Nếu XML có:

```text
id="auction"
```

thì:

```text
aorg : auction
agrp : auctionGroup
```

Không tạo:

```text
AuctionOrganizationInstance
AuctionGroupInstance
```

## 5.1. Player–Role runtime relation

JCM:

```text
players:
    bob   auctioneer
    alice participant
```

Phải giữ runtime semantics:

```text
bob enacts auctioneer in agrp
alice enacts participant in agrp
```

Không tạo class:

```text
AuctionRoleEnactment
```

Representation target phải là association/link/runtime relation.

Nếu cần role-instance object để multiplicity/runtime links đúng với USE, được phép tạo object thuộc role class, nhưng KHÔNG tạo một MClass trung gian `RoleEnactment`.

Ví dụ hợp lệ:

```text
bobRole   : auctioneer
aliceRole : participant

bob   --enacts--> bobRole
alice --enacts--> aliceRole

agrp --containsRole--> bobRole
agrp --containsRole--> aliceRole
```

Hoặc representation tương đương nếu USE model hiện tại cho phép relation trực tiếp mà vẫn giữ đúng cardinality/context.

Yêu cầu quan trọng:

- phải giữ context của group;
- không chỉ match role bằng tên global;
- không fuzzy match;
- không tạo dangling links;
- không làm mất multiplicity semantics.

---

# 6. Mapping Contract — Moise Functional Specification

Functional specification gồm:

- Scheme
- Goal
- Goal arguments
- plan/decomposition operator
- ttf
- Mission
- Mission–Goal relation

Ví dụ:

```xml
<scheme id="doAuction">
  <goal id="auction">
    <argument id="Id"/>
    <argument id="Service"/>
    <plan operator="sequence">
      <goal id="start"/>
      <goal id="bid" ttf="10 seconds"/>
      <goal id="decide" ttf="1 hour"/>
    </plan>
  </goal>

  <mission id="mAuctioneer">
    <goal id="start"/>
    <goal id="decide"/>
  </mission>
</scheme>
```

## Quyết định bắt buộc

KHÔNG đưa các phần tử này vào USE Class Model hiện tại.

Không được sinh:

```text
DoAuction
DoAuctionAuction
DoAuctionStart
DoAuctionBid
DoAuctionDecide
DoAuctionMAuctioneer
DoAuctionMParticipant
AuctionSchemeInstance
AuctionMissionCommitment
```

Không biến Goal thành enum trong task này.

Phải giữ source semantics trong semantic layer/trace đủ để Goal Model task sau có thể sử dụng.

Goal Model task sau sẽ chịu trách nhiệm biểu diễn:

```text
scheme
goal
goal decomposition
sequence / parallel / choice / ...
goal arguments
ttf
mission
mission-goal relations
runtime goal/mission state
```

Class Browser trong task hiện tại không được chứa các Goal/Scheme/Mission classes.

---

# 7. Mapping Contract — Norms

Moise:

```xml
<norm id="n1"
      type="obligation"
      role="auctioneer"
      mission="mAuctioneer"/>
```

## Quyết định bắt buộc

Norm KHÔNG trở thành:

```text
class Norm
class n1
object n1 : Norm
```

Norm thuộc OCL/constraint layer.

Target intention:

```text
Norm → OCL constraint
```

Tuy nhiên:

- không invent nghĩa của `obligation`;
- không tạo OCL giả nếu target runtime/goal state cần thiết chưa tồn tại;
- phải giữ norm source + references trong semantic/trace layer;
- khi OCL có thể biểu diễn chính xác trên target model thì generate;
- nếu Goal Model chưa được implement và norm cần Mission state, đánh dấu rõ là `PENDING_GOAL_MODEL_OCL`, KHÔNG thay bằng Norm class.

Acceptance tối thiểu của task hiện tại:

- không có `Norm`/`N1`/`N2` trong Classes;
- normative information không bị mất;
- có explicit target/hook/status cho OCL generation.

---

# 8. Mapping Contract — CArtAgO Artifact

## 8.1. Artifact subclass

Java:

```java
public class X extends Artifact
```

Mapping:

```text
class X : artifact
```

Ví dụ:

```java
public class AuctionArtifact extends Artifact
```

→

```text
class AuctionArtifact : artifact
```

Không cần generic `ArtifactType` class trong domain target.

Nếu cần base metadata nội bộ thì giữ ngoài exposed domain Class Model.

## 8.2. Observable properties

Ví dụ:

```java
defineObsProperty("running", false);
defineObsProperty("task", "no_task");
defineObsProperty("best_bid", Double.MAX_VALUE);
defineObsProperty("winner", ...);
```

Mapping:

```text
running  → attribute
task     → attribute
best_bid → attribute
winner   → attribute
```

Runtime:

```text
artifact instance
→ MObject

observable property current value
→ attribute value
```

Không tạo:

```text
ObservablePropertySnapshot
```

như một domain class.

Snapshot/transport DTO có thể tồn tại nội bộ trong plugin nhưng không được materialize thành exposed USE MClass.

## 8.3. Artifact operations

Không map:

```text
@OPERATION start(...)
@OPERATION stop()
@OPERATION bid(...)
```

sang `MOperation` trong task này.

JaCaMo/CArtAgO chịu trách nhiệm execution.

USE chỉ nhận state sau execution.

## 8.4. Private/helper implementation state

Ví dụ:

```java
String currentWinner;
```

không tự động map thành USE attribute nếu nó chỉ là implementation detail.

Ưu tiên runtime/public semantic state như observable property:

```text
winner
```

Chỉ map private/helper field khi có explicit requirement/evidence rằng field đó là state cần verification.

---

# 9. Những class hiện tại phải audit và loại khỏi exposed Class Model

Nếu còn được sinh bởi current transformer, phải xử lý:

```text
Plan
PlanBodyElement
PlanLibrary
ArtifactType
ObservablePropertySnapshot
CartagoAgentIdentity
AuctionGroupInstance
AuctionRoleEnactment
AuctionMissionCommitment
AuctionSchemeInstance
DoAuction
DoAuctionAuction
DoAuctionBid
DoAuctionDecide
DoAuctionMAuctioneer
DoAuctionMParticipant
DoAuctionStart
```

Các tên Auction ở trên chỉ là ví dụ từ acceptance case.

Phải loại bỏ bằng rule tổng quát, không bằng `if (name.equals("Auction..."))`.

Ví dụ:

```text
Scheme → không project Class Model
Goal → không project Class Model
Mission → không project Class Model
RoleEnactment wrapper → relation/link
GroupInstance wrapper → runtime object of group class
ObsPropertySnapshot wrapper → attribute value
Plan structures → không project
```

---

# 10. `Agent`, `Artifact`, `Workspace`, `Environment`, `Soc`

Không được mặc định giữ các generic framework classes chỉ vì chúng tồn tại trong semantic DTO/metamodel cũ.

## 10.1. Agent

Preferred target:

```text
<asl basename> : agent
```

Không bắt buộc exposed generic `Agent` class nếu semantic kind/tag đã đủ.

Nếu current USE implementation cần một base `Agent` để support inheritance/type checks, được giữ ONLY khi:

- nó có chức năng rõ ràng trong target model;
- không làm Class Browser trở lại framework-centric;
- concrete agent program classes vẫn là phần người dùng nhìn thấy.

Nếu không cần → bỏ generic `Agent`.

## 10.2. Artifact

Tương tự:

```text
AuctionArtifact : artifact
```

Không bắt buộc exposed generic `Artifact` class.

Chỉ giữ generic base nếu target USE type system thực sự cần.

## 10.3. Workspace

Chỉ giữ `Workspace` nếu runtime verification hiện tại cần quan hệ:

```text
agent → workspace
artifact → workspace
```

Nếu workspace chỉ là technical container và không có invariant/query nào dùng nó, không cần expose như domain class.

Không được quyết định chỉ dựa trên việc CArtAgO có Java class `Workspace`.

## 10.4. Environment / Soc

Không expose `Environment` hoặc `Soc` nếu chúng chỉ là generic container của old metamodel.

Giữ semantic information nếu cần trong internal layer, nhưng không sinh exposed MClass nếu không có target verification meaning.

---

# 11. Association Transformation — bắt buộc 2-pass

## Pass 1 — Elements

Tạo target elements và trace:

```text
source element
→ target USE element
```

Trace phải dùng stable semantic identity, không dùng fuzzy name matching.

Ví dụ:

```text
Moise OS auction                → MClass auction
Moise Role auctioneer           → MClass auctioneer
Moise Group auctionGroup        → MClass auctionGroup
JCM agent-program source        → MClass auction_capabilities
JCM agent declaration bob       → MObject bob
JCM organisation instance aorg  → MObject aorg
JCM group instance agrp         → MObject agrp
Artifact type AuctionArtifact   → MClass AuctionArtifact
runtime artifact a1             → MObject a1
```

## Pass 2 — Relations

Mỗi source relation phải được phân loại explicit thành một trong:

```text
PRESERVE_AS_ASSOCIATION
PRESERVE_AS_LINK
FLATTEN
CONVERT_TO_ATTRIBUTE
CONVERT_TO_OCL
DEFER_TO_GOAL_MODEL
IGNORE_NOT_NEEDED
```

Không copy relation mù quáng.

Không tạo association khi endpoint target không tồn tại.

Không tạo fake class chỉ để giữ association cũ.

---

# 12. Runtime synchronization

Runtime sync phải update:

- MObject creation/removal nếu supported;
- attribute values;
- links;
- role enactment/player relations;
- organisation/group runtime instances;
- artifact observable state.

Không cần sync:

- Plan stack;
- PlanLibrary;
- Trigger;
- operation descriptors;
- Java reflection metadata;
- internal Jason execution stack;
- source parser objects.

---

# 13. Expected Auction acceptance model

Auction chỉ là acceptance example, KHÔNG phải hardcoded design.

Với các source:

```text
auction-os.xml
auction.jcm
auction_capabilities.asl
AuctionArtifact.java
```

Expected exposed USE Classes phải gần với:

```text
auction               : organisation
auctionGroup          : group
auctioneer            : role
participant           : role
auction_capabilities  : agent
AuctionArtifact       : artifact
```

Có thể có thêm generic base class ONLY nếu thực sự cần cho USE typing, nhưng không được xuất hiện hàng loạt framework/internal classes.

Expected runtime objects phải có khả năng biểu diễn:

```text
aorg       : auction
agrp       : auctionGroup

bob        : auction_capabilities
alice      : auction_capabilities
maria      : auction_capabilities
francois   : auction_capabilities
giacomo    : auction_capabilities

a1/a2/...  : AuctionArtifact
```

và các relation:

```text
aorg ↔ agrp
agrp ↔ role enactments / players
bob ↔ auctioneer
alice/maria/francois/giacomo ↔ participant

artifact ↔ workspace only if workspace is kept
```

Artifact state:

```text
running
task
best_bid
winner
```

phải được biểu diễn bằng attributes/values, không bằng snapshot classes.

---

# 14. Expected Auction classes that MUST NOT exist

Sau refactor, Auction acceptance test phải fail nếu exposed Class Model có các class sau:

```text
Plan
PlanLibrary
PlanBodyElement
Trigger

ObservablePropertySnapshot
CartagoAgentIdentity
ArtifactType

AuctionGroupInstance
AuctionRoleEnactment
AuctionMissionCommitment
AuctionSchemeInstance

DoAuction
DoAuctionAuction
DoAuctionStart
DoAuctionBid
DoAuctionDecide
DoAuctionMAuctioneer
DoAuctionMParticipant

Norm
```

Nếu internal DTO có tên tương tự nhưng KHÔNG materialize thành USE MClass thì được phép.

---

# 15. Generalization / naming / identity rules

## 15.1. Naming

Tên target domain classes lấy từ semantic IDs/source identities:

```text
organisation id
role id
group id
ASL source basename
Artifact Java simple class name
```

Không thêm prefix case-study kiểu:

```text
AuctionRoleEnactment
DoAuctionBid
```

chỉ để tránh collision.

Collision phải được giải quyết bằng semantic identity/namespace/trace, không bằng việc biến internal path thành domain class name.

## 15.2. Semantic kind

Mỗi target class phải giữ được kind tối thiểu:

```text
organisation
role
group
agent
artifact
```

Có thể implement bằng:

- annotation/stereotype-like metadata trong plugin;
- trace metadata;
- internal target descriptor;
- mechanism khác phù hợp với USE API.

Không bắt buộc literal syntax `class X : role` nếu USE không hỗ trợ cú pháp đó.

Nhưng GUI/trace/debug phải có cách xác định:

```text
X đại diện cho semantic kind gì?
```

---

# 16. Tests bắt buộc

## 16.1. Unit tests

Phải có tests cho generic rules:

- OS id → organisation class
- role id → role class
- group id → group class
- group-role cardinality preservation
- JCM organisation declaration → object of resolved organisation class
- JCM group declaration → object of resolved group class
- ASL basename → agent class
- agent declaration → MObject of agent-program class
- Artifact subclass → artifact class
- ObsProperty → attribute/value
- no Plan MClass
- no Goal/Scheme/Mission MClass
- no Norm MClass
- no RoleEnactment wrapper MClass
- no GroupInstance wrapper MClass
- no ObsPropertySnapshot MClass

## 16.2. Auction acceptance test

Assert exact/near-exact exposed classes expected from Section 13.

Assert forbidden classes from Section 14 are absent.

Assert:

```text
aorg : auction
agrp : auctionGroup
bob/alice/... : auction_capabilities
```

Assert group-role cardinalities preserved.

Assert player-role runtime links preserved.

Assert artifact properties appear as values/attributes.

## 16.3. Generic regression

Run existing Hello World and House Building cases.

Purpose:

- prove transformer is generic;
- no case-specific name logic;
- dynamic creation paths do not crash;
- unsupported Goal Model pieces are deferred, not converted into fake classes.

House Building is especially important because organisation/artifacts may be created dynamically rather than fully declared in `.jcm`.

---

# 17. Fail-closed rules

Transformation phải fail hoặc emit explicit diagnostic khi:

- organisation source file resolve được nhưng không có usable OS id;
- group references unknown group specification;
- role assignment references unknown role;
- association endpoint không có valid target mapping;
- runtime object cannot be typed safely;
- name collision cannot be resolved with semantic identity;
- norm OCL cannot be generated because required Goal Model state is absent.

Không được:

- đoán bằng string similarity;
- chọn class gần tên nhất;
- silently drop required relation;
- tạo generic fallback class để “cho chạy được”;
- hardcode case-study names.

---

# 18. Implementation procedure checklist

## Phase A — Preflight

- [ ] `git status`
- [ ] xác định branch hiện tại
- [ ] đọc current transformation pipeline
- [ ] đọc current native USE builder/state builder
- [ ] tìm tất cả nơi tạo `MClass`
- [ ] tìm tất cả nơi tạo `MAssociation`
- [ ] tìm tất cả nơi tạo `MObject`
- [ ] tìm tất cả nơi materialize Jason Plan classes
- [ ] tìm tất cả nơi materialize Moise Goal/Scheme/Mission classes
- [ ] tìm tất cả nơi materialize runtime wrapper classes
- [ ] ghi lại current Auction Class Browser output

## Phase B — Target projection model

- [ ] implement semantic kinds: organisation / role / group / agent / artifact
- [ ] implement stable source→target trace
- [ ] remove 1:1 Java/metamodel class materialization assumption
- [ ] ensure target class creation is rule-based, not DTO-class-based

## Phase C — JCM

- [ ] MAS → model name only
- [ ] ASL basename → agent class
- [ ] JCM agent declaration → MObject
- [ ] organisation instance resolve XML OS id → MObject of organisation class
- [ ] group instance → MObject of group class
- [ ] player-role relation preserved

## Phase D — Moise structural

- [ ] OS id → organisation class
- [ ] role definitions → role classes
- [ ] group definitions → group classes
- [ ] group-role references reuse existing role classes
- [ ] multiplicity preserved
- [ ] no generic Organization/Role/Group copy classes unless technically required as hidden/internal bases

## Phase E — Functional boundary

- [ ] remove Scheme classes from exposed Class Model
- [ ] remove Goal classes from exposed Class Model
- [ ] remove Mission classes from exposed Class Model
- [ ] retain functional semantics in source/semantic layer for future Goal Model
- [ ] mark related target transformation as `DEFER_TO_GOAL_MODEL`

## Phase F — Norm boundary

- [ ] remove Norm classes/objects
- [ ] retain Norm source references
- [ ] route Norm toward OCL layer
- [ ] if OCL cannot yet be expressed faithfully, mark `PENDING_GOAL_MODEL_OCL`
- [ ] no invented OCL semantics

## Phase G — CArtAgO

- [ ] Artifact subclass → artifact class
- [ ] runtime artifact → MObject
- [ ] ObsProperty → attribute/value
- [ ] remove exposed `ArtifactType`
- [ ] remove exposed `ObservablePropertySnapshot`
- [ ] do not map `@OPERATION` to MOperation
- [ ] do not map private helper fields by default

## Phase H — Runtime relation cleanup

- [ ] remove exposed `RoleEnactment` classes
- [ ] remove exposed `GroupInstance` classes
- [ ] remove exposed `SchemeInstance` classes from Class Model
- [ ] remove exposed `MissionCommitment` classes from Class Model
- [ ] rewrite required runtime semantics to links/objects/attributes
- [ ] no dangling associations

## Phase I — Jason cleanup

- [ ] remove exposed `Plan`
- [ ] remove exposed `PlanLibrary`
- [ ] remove exposed `PlanBodyElement`
- [ ] remove exposed `Trigger`
- [ ] remove action/execution internals from target Class Model
- [ ] preserve only runtime state actually needed

## Phase J — Tests

- [ ] focused unit tests PASS
- [ ] Auction acceptance PASS
- [ ] Hello World regression PASS
- [ ] House Building regression PASS
- [ ] full relevant reactor/module tests PASS
- [ ] package/build PASS

## Phase K — Manual USE GUI acceptance

- [ ] import/synchronize Auction
- [ ] Model Browser shows only intended domain/runtime classes
- [ ] no forbidden internal classes
- [ ] Class Diagram can open
- [ ] Object Diagram shows runtime objects
- [ ] artifact observable values visible in state
- [ ] player/role/group relations visible
- [ ] OCL/invariant engine still runs on same active MSystem

---

# 19. Definition of Done

Task chỉ được DONE khi tất cả điều sau đúng:

- [ ] USE target no longer mirrors JaCaMo internal metamodel/object graph.
- [ ] Exposed Class Model is domain/runtime oriented.
- [ ] Auction Class Browser is reduced to intended semantic classes.
- [ ] Plan/PlanLibrary/PlanBody are absent from Class Model.
- [ ] Scheme/Goal/Mission are absent from Class Model and deferred to Goal Model.
- [ ] Norm is absent from Class Model and routed to OCL.
- [ ] Artifact observable properties are attributes/values, not snapshot classes.
- [ ] JCM organisation/group declarations create objects of specification-derived classes.
- [ ] Agent declarations create objects of ASL-program classes.
- [ ] Player-role/group relations are preserved without RoleEnactment MClass.
- [ ] Group-role cardinalities are preserved.
- [ ] Traceability remains available.
- [ ] No case-study hardcoding.
- [ ] No fuzzy/name-only formal mapping.
- [ ] Runtime synchronization still uses one active USE MSystem.
- [ ] Relevant automated tests pass.
- [ ] Manual USE GUI smoke test passes.

---

# 20. Required final report from coding agent

Sau khi hoàn thành, báo cáo ngắn gọn nhưng phải có evidence:

```text
1. Root cause of old over-modeling
2. New generic mapping rules
3. Exact production files changed
4. Exact tests added/updated
5. Auction exposed classes BEFORE
6. Auction exposed classes AFTER
7. Auction runtime objects/links AFTER
8. Forbidden classes confirmed absent
9. Hello World result
10. House Building result
11. Full test/build result
12. Remaining deferred work:
    - Goal Model/View
    - faithful Norm → OCL dependent on Goal/runtime state
    - any unsupported runtime relation
```

Không claim DONE nếu chưa có executable/test evidence.
