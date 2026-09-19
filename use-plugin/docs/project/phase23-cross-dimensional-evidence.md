# Phase 23 cross-dimensional and normative boundary

Classification: supported evidence API + exact source-binding verification. No
frozen metamodel/mapping changes, no runtime mapping support promotion.

## Relation inventory / executable subset

CrossDimensionalVerifier verifies only relations actually declared in semantic IR,
using exact source reference identity -> association trace and exact endpoint
SemanticIds -> object traces. Positive means that this declared link exists in the
USE state, not that a behavioral delivery/permission claim has been proven.

| Relation | Implemented verification | Runtime boundary |
|---|---|---|
| Agent.artifact | offline exact accessibility/binding link | focus is not automatically accessibility; runtime focus mutation unsupported |
| Agent.joinWorkspace | offline declared membership link | no projected join/quit callback contract; unsupported current-membership claim |
| ExternalAction.operation | offline exact operation relation; existing core OCL | Jason and CArtAgO correlations cannot be joined by name; cross-runtime invocation join unsupported |
| Plan.RefArtifact | offline exact artifact target | no inference from arbitrary plan execution |
| ObsProperty.obsproperty | offline exact property/belief relation when declared | delivery semantics unsupported; not every percept becomes a belief |
| Role.players | offline exact role/agent relation | OE role facts observable, instance-sensitive role mutation remains trace-only |
| Organisation.deploysAgent | offline exact deployment link | static deployment is not proof of active organisation membership |
| OGoal.OGoalToGoal | offline declared goal alignment | goal instance states are observable; cross-dimension lifecycle equivalence unsupported |

Unresolved source target produces SKIPPED/UNSUPPORTED; missing, stale or ambiguous
trace produces ERROR; absent exact link produces FAIL. Similar display names never
substitute. Explicit unsupportedRuntime results preserve event/correlation/source
attribution for requests beyond this subset. No rule is silently applied to a
relation absent from source. The reusable verifier is a headless service; callers
must distinguish its structural results from current-runtime OCL results.

## Pinned Moise 1.1 audit

Evidence: local pinned moise-1.1.jar public signatures and RolePlayer.getNorms
bytecode (`javap -c -private`), existing MoiseRuntimeConnector source and real OE
fixture tests. No inference from current upstream branches is needed.

| Concept | Classification | Evidence/implementation |
|---|---|---|
| Role adoption | SUPPORTED_EXACT observation | OEAgent.getRoles; connector polling preserves agent/role/group |
| Mission commitment | SUPPORTED_EXACT observation | OEAgent.getMissions; agent/mission/scheme |
| Group/scheme membership | SUPPORTED_EXACT for captured role/mission instance tuples | no static-spec/runtime-instance collapse |
| Organisation goal state | SUPPORTED_EXACT observation | SchemeInstance.getGoals/getState; fact key includes scheme instance |
| Derived obligation | SUPPORTED_EXACT API snapshot | OEAgent.getObligations -> Permission records |
| Derived permission | SUPPORTED_EXACT API snapshot | OEAgent.getPermissions -> Permission records |
| Prohibition | NOT_EXPOSED by these audited OE APIs | explicit UNSUPPORTED |
| Activation/fulfilment/violation/expiration/deadline | UNSAFE_TO_INFER from derived sets | explicit UNSUPPORTED; no NPL lifecycle claim |
| Norm-to-OCL | UNSAFE_TO_INFER | structural Norm remains separate |

MoiseNormativeSnapshot reports modality + organisation/agent/group/role/scheme/
mission from the API's exact objects, sorted deterministically. Connector access
requires a connection. It neither mutates USE nor declares an OCL truth value.
A derived set disappearing is not labelled fulfilment/expiration/violation.
This applies to the observed OE object, not the standalone launcher's ora4mas
board state; Phase 20's adapter limitation remains.

The negative control matters: an obligation declaration with default minimum
cardinality yields derived permission but no outstanding obligation. Setting the
fixture mission minimum to 1 produces a derived obligation; commitment removes
outstanding derived facts. These are tested OE results, not generalized deontic
translation laws.

## Tests and remaining limits

CrossDimensionalVerifierTest imports/materializes Auction, verifies source links,
rejects missing identity and a valid same-name/wrong-owner target, detects a deleted
link, and preserves unsupported belief-delivery evidence. MoiseRuntimeConnectorTest
covers exact role/mission/goal observations, default/minimum cardinality controls,
derived snapshots before/after commitment and stable repeated capture. Focused gate
2/2 PASS. Full reactor evidence follows below.

Runtime percept delivery, Jason-action/OpId joining, instance-specific organisational
link mutation and full normative lifecycle remain explicit unsupported capabilities.
No speculative implementation or Phase 25 permission is needed to finish this bounded
phase. Frozen contracts and standalone limitations are unchanged.

Full reactor `mvn --batch-mode verify`: 293/293 PASS, zero failures/errors/skips (2026-09-19).

Resume audit: main/origin main contain Phase 23 commit `10225d84` with unrelated
metrics commit `0e9474a0` preserved as its parent. The recorded post-merge focused
CrossDimensionalVerifier/Moise connector smoke passed 2/2. No Phase 23 implementation
was discarded or repeated; Phase 24's full reactor also covers these regressions.
