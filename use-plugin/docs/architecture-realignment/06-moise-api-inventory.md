# Moise/ORA4MAS/NPL API inventory

## Inspected baseline and authority

- JaCaMo resolves Moise 1.1 and NPL 0.6.1.
- Official Moise source inspected: annotated tag `v1.1`, peeled commit `c68d4b7068c56b7a42e657bdc160f55f2d366ea8`.
- Static authority is the `moise.os` object graph loaded by Moise. Runtime authority in current JaCaMo is the ORA4MAS artifact layer (`ora4mas.nopl`), backed by CArtAgO, plus its NPL interpreter—not an assumed classic `moise.oe.OE` root.

## Static OS loading and object graph

| Concept | Exact official API/object | Exportable facts | V2 target |
|---|---|---|---|
| OS root/parser | `moise.os.OS.loadOSFromURI(String)` -> `OS` | OS ID/source; `getSS()`, `getFS()`, `getNS()` | `Organization` root plus provenance |
| Role | `moise.os.ss.Role` | ID, abstract flag, super-role hierarchy | `Role` |
| Group specification | `moise.os.ss.Group` | IDs, nested groups, relation-scoped role/subgroup cardinalities and links | `Group`, `Role`, `Link`, plus exact Bridge relation facts |
| Link | `moise.os.ss.Link` | source/target role, type, scope, inheritance/bidirection information where exposed | `Link` |
| Functional scheme | `moise.os.fs.Scheme` | scheme ID, goal tree and missions | `Scheme` |
| Mission | `moise.os.fs.Mission` | mission ID, cardinality and goal membership | `Mission` |
| Organizational goal | `moise.os.fs.Goal` | ID, type, description, satisfaction/time properties and decomposition | `OGoal` |
| Organizational plan | goal plan/decomposition object and operator | ordered subgoals and sequence/parallel/choice operator | `OPlan` |
| Normative specification | `moise.os.ns.NS` and norm objects | norm ID, role, mission, modality, condition and time constraint | `Norm` |

The official loader resolves schema/object semantics that `MoiseXmlParser` currently duplicates with DOM traversal. The Bridge must walk the official OS graph and preserve exact object IDs and source URI. XML can remain evidence/debug input, not semantic authority.

## Relation-scoped cardinality

The official APIs are `Group.getRoleCardinality(Role)` and `Group.getSubGroupCardinality(Group)`. The exact source facts are therefore keyed by both endpoints:

```text
GroupRoleCardinality(groupId, roleId, min, max)
ParentSubGroupCardinality(parentGroupId, subGroupId, min, max)
```

The Bridge contract/provenance preserves every such tuple exactly. Frozen V2 instead exposes `Role.minCardinality/maxCardinality` and `Group.minCardinality/maxCardinality`, which cannot preserve multiple different bounds for the same role or subgroup in different contexts. Such a V2 projection is `SUPPORTED_SUBSET` when one context is demonstrably the selected scope and `REPRESENTATION_LOSS` otherwise. The adapter must never choose an arbitrary occurrence, merge bounds, or label the projection `EXACT`; verification that requires the lost context is capability-blocked/inconclusive.

## Runtime authority in JaCaMo 1.3.1

The current JaCaMo/Moise integration creates CArtAgO organizational artifacts:

| Runtime concept | Exact class/API | State obtainable | Identity consequence |
|---|---|---|---|
| Organization board | `ora4mas.nopl.OrgBoard`; board registry/accessors and loaded OS | Organizational authority and created boards | Namespace by project/session plus its CArtAgO `ArtifactId` |
| Group instance | `ora4mas.nopl.GroupBoard`; static registry/accessors, state/spec getters | Group instance, players/roles and formation state | Board/artifact UUID + group instance ID |
| Scheme instance | `ora4mas.nopl.SchemeBoard`; static registry/accessors, state/spec getters | Responsible groups, mission commitments and goal states | Board/artifact UUID + scheme instance ID |
| Board snapshot | board state getter/clone exposed by ORA4MAS artifacts | Copy of current organizational state | Copy before serialization; never transport a mutable board object |
| Normative engine | `ora4mas.nopl.OrgArt.getNormativeEngine()` | The board's `npl.NPLInterpreter` | Engine is scoped to the board instance |

Classic Moise exposes `moise.oe.OE` and related runtime objects, but the inspected JaCaMo path does not use a single classic OE as the authoritative root. A Bridge implementation must adapt the actual ORA4MAS boards it observes. It may support classic OE later behind a separate capability adapter; it must not cast one model into the other by assumption.

## Runtime organizational facts

| Fact | Official source | Availability | V2/state policy |
|---|---|---|---|
| Role player | Group board state/player-role relation | Runtime | Materialize `Role.agents` / `Agent.roles`; retain group instance in relation identity/provenance |
| Mission commitment | Scheme board state | Runtime | Trace tuple `(scheme instance, mission, agent)`; V2 has structural missions but no commitment EClass |
| Goal state | Scheme board state | Runtime | Runtime state/verification fact; do not mutate static `OGoal` definition |
| Responsible group | Scheme board state | Runtime | Cross-runtime relation retained in contract/trace |
| Group well-formedness | Group board/NPL state | Runtime | Runtime predicate/evidence, not a fabricated Ecore attribute |
| Norm lifecycle | NPL interpreter and listener | Runtime | Keep distinct from OCL verification results |

These rows distinguish static definitions (`Mission`, `OGoal`, `Norm`, `Scheme`) from runtime facts/instances (commitment, satisfaction state, norm lifecycle, scheme/group incarnation). Runtime facts remain first-class Bridge evidence even when frozen V2 and the current Runtime Mapping V2 have no faithful target object/link/attribute.

## NPL listener surface

`npl.NPLInterpreter` 0.6.1 exposes `addListener(...)`/`removeListener(...)` and collections/accessors for active, fulfilled, unfulfilled and inactive norms. `npl.NormativeListener` callbacks cover norm creation, fulfillment, unfulfillment, inactivation, failure and sanction-related changes in the inspected API.

Bridge policy:

1. register listeners after each organizational artifact creates its interpreter;
2. snapshot the interpreter collections while events are buffered;
3. include board identity, norm instance identity, lifecycle state, source sequence and official payload;
4. treat any missed listener registration or board replacement as a resync boundary;
5. never translate norm lifecycle automatically into OCL constraints or OCL pass/fail.
6. retain every observable runtime fact with canonical identity, session/generation, board/artifact incarnation, source evidence, snapshot/event provenance and completeness/capability status;
7. record an explicit projection status (`MATERIALIZED_FAITHFULLY`, `EVIDENCE_ONLY` or `UNAVAILABLE`) rather than silently discarding or coercing a fact.

There is no source evidence for a globally atomic cut across all boards and the Jason/CArtAgO state. NPL/board observations therefore participate in the same buffered validated-cut protocol as the other subsystems.

## Canonical IDs and recreation

- OS definition IDs are authoritative only within the OS/project namespace.
- A group/scheme local instance name is not sufficient across session restart or dispose/recreate.
- Runtime board identity uses the CArtAgO `ArtifactId` UUID plus Bridge session/generation.
- A role-play tuple is identified by board incarnation + group instance + role definition + agent incarnation.
- A mission commitment is identified by scheme-board incarnation + mission definition + agent incarnation.
- A norm instance uses the official NPL identity/string representation only as a source token; the Bridge assigns a canonical scoped ID and retains all source fields because stability across engine rebuilds has not been proven.

## V2 fidelity and gaps

The static OS concept set is usable with frozen V2 for this migration, but relation-scoped role/subgroup cardinality can lose context in the intrinsic-looking V2 attributes. Runtime role assignment is representable through existing opposites. Mission commitments, goal lifecycle and norm lifecycle are retained in RuntimeSnapshot/RuntimeEvent/trace/report; their capture does not imply that current `MSystemState` can represent or OCL can query them faithfully.

Potential loss points requiring explicit status:

- OS features that have no selected V2 attribute remain in provenance/extension facts and are marked `REPRESENTATION_LOSS`.
- Unsupported NPL modalities or lifecycle data are never coerced into `NormType`.
- Cross-reference from an `AGoal` to an `OGoal` requires official binding/runtime evidence; same literal is insufficient.
- Different role/subgroup cardinalities across contexts remain exact Bridge relation facts and are `REPRESENTATION_LOSS` in a context-collapsing V2 projection.

After Bridge and canonical-case stabilization, a separately reviewed **Runtime Verification Projection Review** decides per runtime fact whether it remains evidence-only, receives a target-only USE runtime projection outside frozen Runtime Mapping V2, or justifies a new metamodel version. The same evidence can motivate a separate V2.x/V3 cardinality review if full relation-level verification is required; neither review is part of the current architecture migration.

## Verdict

| Capability | Verdict |
|---|---|
| Official OS parse and static V2 projection | `FEASIBLE_WITH_ADAPTER` |
| ORA4MAS board enumeration/state extraction | `FEASIBLE_WITH_ADAPTER` |
| Role/mission/goal runtime state | `FEASIBLE_WITH_ADAPTER` |
| Norm lifecycle events | `FEASIBLE_WITH_ADAPTER` |
| One classic `OE` root for current JaCaMo | `NOT_FEASIBLE_WITH_CURRENT_API` as an architecture assumption |
| Cross-subsystem atomic snapshot | `NOT_FEASIBLE_WITH_CURRENT_API`; validated consistent cut is feasible |
| Moise core patch | Not required by current evidence |

## Required implementation probes

1. Load official Auction and House OS through `OS.loadOSFromURI`, export canonical graphs and compare stable digests.
2. Discover dynamically created group/scheme boards and prove snapshot/listener registration before accepted readiness.
3. Exercise role adoption/removal, mission commit/uncommit and organizational-goal state changes.
4. Exercise NPL active/fulfilled/unfulfilled/inactive/failure callbacks and snapshot reconciliation.
5. Dispose/recreate a board with the same local name and prove incarnation-safe identity.
6. Exercise one role or subgroup reused under multiple groups/parents with different bounds and prove that all exact tuples survive while the V2 projection reports context loss.
