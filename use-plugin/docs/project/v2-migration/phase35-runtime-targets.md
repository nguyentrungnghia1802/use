# Phase 35 downstream runtime target reconciliation

> Current acceptance: downstream consumer gates PASS after **350/350 clean reactor**
> and **11/11 Python tests**. Earlier run counts/OPEN descriptions below retain
> their historical stage. See [final acceptance](phase35-acceptance.md); phase
> merge/post-merge/push closure remains pending. V2 remains WORKING_BASELINE.

Status: focused tests PASS; full reactor and phase closure OPEN.

Runtime Mapping V2 uses schema 3.0.0 and WORKING status. Its targetContract pins
Mapping 2.2.0 mappingId, Ecore SHA-256 and mapping SHA-256 to ActiveBaseline.
The loader rejects fingerprint mismatch and historical schema versions. The
V2RuntimeBindingAdapter validates structural, projection and order anchors against
the active descriptor; the V1 production adapter has been removed. Historical
V1 resources and their immutable freeze checks remain separate regression evidence.

The existing 36 event rules preserve connector/event payload and authority
semantics. A generic REPLACE_ORDER rule accepts complete authoritative orders via
exact trace identity. Membership associations and independent directional ranks
remain separate. The default facade supplies its current TransformationPlan and
InstancePlan to the mutation engine; order rows are stripped when extracting the
membership plan, preventing duplicate order materialization on reconnect.

Rank-only changes preserve membership and object identity. Reconnect rebuilds from
the authoritative projection snapshot before LIVE. Bare ordered membership insert,
delete or endpoint destruction lacks directional order evidence and is rejected
before mutation with RUNTIME_ORDER_MEMBERSHIP_REQUIRES_RESYNC. Callers must provide
a complete authoritative projection for resynchronization. This is an explicit
runtime event boundary, not a restriction on valid V2 static instances. Unordered
membership and unbound object lifecycle retain their existing behavior. Connector,
queue and lifecycle mechanics are unchanged.

Evidence: 47 runtime/evidence focused tests PASS (phase35-runtime-evidence.log),
17 default order/runtime tests PASS (phase35-runtime-order.log), then 25/25 PASS
in phase35-last-consumers.log including RuntimeFoundationTest, V2RuntimeOrderTest
and ReleasePackageContractTest. V2RuntimeOrderTest proves independent rank updates,
unchanged membership, reconnect/resync and rejection before unsafe membership
mutation. Native V2OppositeOrderingAuditTest remains a mandatory full regression.

The preceding full plugin snapshot was 204 tests, 2 failures, 0 errors. Both were
identified migration assumptions: old release resource inventory and destruction
of an order-bound endpoint without order evidence. The package now inventories V2;
the lifecycle positive control uses an independent traced object while a new
negative control retains order-bound endpoint safety. Full reactor rerun is pending.

Golden V2 output was accepted after actual SOIL/direct parity and OCL controls.
Historical V1 goldens remain unchanged. V2 diagnostics retain the unsupported
Jason rule and ownerless Moise plan; XML diagnostic provenance now points to the
exact XML plan. Original Auction standalone semantic equivalence is not claimed.

Classification: RUNTIME TARGET CONTRACT CHANGE; TEST/EVIDENCE CHANGE.
No V2 Ecore, Mapping 2.2.0 bytes, USE core or upstream runtime semantics changed.
