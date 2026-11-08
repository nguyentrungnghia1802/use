# M01 — Reconciled Decision Matrix: Goal/Task Semantics and Auction Setup (`do_auction`)

Date: `2026-11-07`

## 1. Sources Reconciled

1. **Paper**: *On Checking the Conformance between iStar and BPMN* (`use/use-plugin/Core/On Checking the Conformance between iStar and BPMN_261012_173041_1.pdf`), specifically:
   - Section IV-A (`Fulfillment Conditions`), Definition 1, Definition 2, and **Table I** (Instantaneous propagated value $\hat{v}_i(x) \in \{\bot, ?, \top\}$).
   - Section IV-B (`Goal Evaluation`), temporal evaluation rules for `Achieve`, `Maintain`, `Sustain`, and `None`, and non-temporal evaluation for Tasks ($v_i(t) = \hat{v}_i(t)$).
   - Section IV-C (`Validating Fulfillment Conditions` & `Checking Conformance`), Definitions 3–7.
2. **Authoring Diagrams & Core Models**:
   - `use/use-plugin/Core/auction/auction_csl.drawio` and `use/use-plugin/Core/auction/auction_csl.csl`
   - `use/use-plugin/Core/auction/auction_istar_final_fixed.drawio` and `use/use-plugin/Core/auction/auction_istar_final.istar`
   - `use/use-plugin/verification/auction-setup-semantics-20261107/REPORT.md` and `AuctionSetupAuthorityProbe.java`
3. **Active Auction Bundle & External JaCaMo Case Study**:
   - `D:\_CODE_BANK\Project_\vnu-sme-lab\jacamo\examples\auction\auction.jcm`
   - `D:\_CODE_BANK\Project_\vnu-sme-lab\jacamo\examples\auction\src\agt\auction_capabilities.asl`
   - `D:\_CODE_BANK\Project_\vnu-sme-lab\jacamo\examples\auction\src\org\auction-os.xml`
   - `D:\_CODE_BANK\Project_\vnu-sme-lab\jacamo\examples\auction\src\env\auction_env\AuctionArtifact.java`
   - `use/use-plugin/examples/auction/auction.csl`, `auction.istar`, `auction-mapping.xml`, `auction.ocl`, `auction.mrv`, `README.md`
4. **Runtime, Mapping, and UI Implementation Classes**:
   - `use-jacamo-semantic-engine`: `GoalTaskStatus`, `GoalMarking`, `TaskMarking`, `IStarMarking`, `IStarPropagation`, `GoalModelInstanceTranslator`, `Use4GoalRuntimeSessionAdapter`, `IStarCapabilityAdmission`
   - `use-plugin`: `RuntimeEngineSessionOwner`, `SemanticResultAdapter`, `ExecutionEvidence`, `OccurrenceGoalModelExpander`, `RequirementInventory`, `VerifiedCheckpointInventory`, `VerifiedCheckpointService`, `ObservedConformancePresenter`, `PresentationResults`, `LiveGoalSummary`, `ExecutionScopeViews`, `VerificationStatusBadge`, `OccurrenceProjection`, `TaskDemandProjection`, `XmlTaskCheckpoints`, `XmlTaskOclBindings`, `JasonStepCutDecoder`, `MoiseSchemeCutDecoder`, `RuntimeFactSources`, `RuntimeTables`, `ObserverProjection`

---

## 2. Paper vs Model vs Runtime Truth & Propagation Matrix

### 2.1 Three-Valued Intentional Evaluation Domain

| Paper Symbol | Paper Meaning | Engine `GoalTaskStatus` | UI `Result` Column (`Goals`, `Tasks`, `Agent instances`, `Occurrences & invocations`) |
|---|---|---|---|
| $\top$ | `satisfied` | `FULFILLED` | `Complete` |
| $\bot$ | `violated` | `VIOLATED` | `Incomplete` |
| $?$ | `undetermined` | `UNKNOWN` (and unachieved in-progress `PENDING`) | `Unknown` |
| N/A (scope/role non-demand) | Player has `required = false` (`SOURCE_NOT_ASSIGNED`) | `UNKNOWN` with quality `SOURCE_NOT_ASSIGNED` | `Not assigned` (in `Agent instances`; does not falsify parent Goal/Task) |

> **Separation of Concerns**:
> - **Intentional Result** (`Unknown`, `Complete`, `Incomplete`) is the three-valued paper evaluation $v_i(x)$ under qualified evidence.
> - **Assignment Status** (`Assigned`, `Not assigned`, `Assignment unknown`) is source demand/role assignment metadata.
> - **Execution Phase** (`Waiting`, `Executing`, `Succeeded`, `Failed`, `Cancelled`) is runtime lifecycle evidence shown in the `Execution` / `Phase` columns, tooltips, and `Evidence details` pane—never mixed into the `Result` vocabulary.
> - **Evidence Quality** (`KNOWN`, `KNOWN_DERIVED`, `KNOWN_HISTORY`, `KNOWN_DERIVED_HISTORY`, `SOURCE_NOT_ASSIGNED`, `UNAVAILABLE:*`, `HISTORY_GAP`) gates whether an engine status is admitted as `Complete`/`Incomplete` or held at `Unknown`.

### 2.2 Leaf Task Evaluation ($v_i(t) = \hat{v}_i(t)$, Table I)

Tasks have **no** temporal operator in the paper; their truth value at cut $i$ for an applicable invocation episode is determined by `PRE` at Task entry and `POST` at Task exit:

| Applicable Demand | Runtime Step Boundary | `PRE` at Entry | `POST` at Exit | Paper $\hat{v}_i(t)$ | Engine `TaskMarking` | UI `Result` | Execution Phase |
|---|---|---|---|---|---|---|---|
| `required = false` (`SOURCE_NOT_ASSIGNED`) | None | N/A | N/A | $?$ (not required for this player) | `UNKNOWN` | `Not assigned` | `Not assigned` |
| `required = true` | Not yet invoked (`Waiting`) | Not evaluated | Not evaluated | $?$ | `UNKNOWN` | `Unknown` | `Waiting` |
| `required = true` | `ENABLED` (in-flight before exit) | `PASS` or `FAIL` | Not at exit boundary | $?$ | `UNKNOWN` | `Unknown` | `Executing` |
| `required = true` | Terminal (`SUCCEEDED` or `FAILED`) | `FAIL` (`false`) | Any | $?$ ($\neg pre_i(t)$) | `UNKNOWN` | `Unknown` | `Succeeded` / `Failed` |
| `required = true` | Terminal (`SUCCEEDED` or accepted failure) | `PASS` (`true`) | `PASS` (`true`) | $\top$ ($pre_i(t) \land post_i(t)$) | `FULFILLED` | `Complete` | `Succeeded` |
| `required = true` | Terminal (`SUCCEEDED` or `FAILED`) | `PASS` (`true`) | `FAIL` (`false`) or unaccepted step failure (`SOURCE_FAILURE_WORLD_POST_UNAVAILABLE`) | $\bot$ ($pre_i(t) \land \neg post_i(t)$) | `VIOLATED` | `Incomplete` | `Failed` / `Succeeded (POST false)` |
| Unknown / Incomplete quality | Any | Unqualified / Gap | Unqualified / Gap | $?$ | `UNKNOWN` | `Unknown` | Diagnostics |

### 2.3 Refinement & Dependency Propagation ($\hat{v}_i(x)$, Table I)

| Construct | Condition for $\top$ (`FULFILLED` / `Complete`) | Condition for $\bot$ (`VIOLATED` / `Incomplete`) | Condition for $?$ (`UNKNOWN` / `Unknown`) |
|---|---|---|---|
| **Leaf Goal $g$** | $c_i(g) = \text{true}$ (`PASS`, qualified) | $c_i(g) = \text{false}$ (`FAIL`, qualified) | Condition unqualified/unavailable |
| **AND-Refinement $x$** | $\forall y \in Ch(x): v_i(y) = \top$ | $\exists y \in Ch(x): v_i(y) = \bot$ | No child is $\bot$, and at least one child is $?$ |
| **OR-Refinement $x$** | $\exists y \in Ch(x): v_i(y) = \top$ | $\forall y \in Ch(x): v_i(y) = \bot$ | No child is $\top$, and at least one child is $?$ |
| **Dependency ($1:1$ or `SAME_PLAYER`)** | $v_i(\text{dependeeElmt}) = \top$ | $v_i(\text{dependeeElmt}) = \bot$ | $v_i(\text{dependeeElmt}) = ?$ |
| **Dependency (`ALL` population)** | All assigned dependee occurrences have $v_i = \top$ | At least one assigned dependee occurrence has $v_i = \bot$ | Otherwise ($?$) |
| **Dependency (`ANY` population, e.g. `D_bid`)** | At least one assigned dependee occurrence has $v_i = \top$ | All assigned dependee occurrences have $v_i = \bot$ | Otherwise ($?$) |

### 2.4 Temporal Goal Evaluation ($v_i(g)$, Section IV-B)

Scoped to an active episode (`active == true`; if `active == false`, $v_i(g) = ?$):

| Goal Type | Instantaneous Input $\hat{v}_i(g)$ (from leaf condition or refinement/dependency) | Temporal State Update & Resulting $v_i(g)$ | UI `Result` |
|---|---|---|---|
| **Achieve** | $\hat{v}_i(g) = \top$ | Latches `everSatisfied = true`; $v_i(g) = \top$ (`FULFILLED`) for the rest of the episode | `Complete` |
| **Achieve** | Prior `everSatisfied == true` in same episode | Retains $v_i(g) = \top$ (`FULFILLED`) even if instantaneous condition later ceases to hold | `Complete` |
| **Achieve** | $\neg \text{everSatisfied}$ and refined/dependency child falsified ($\hat{v}_i(g) = \bot$ from refinement/dependency) | Records `violated = true` (unless later satisfied in an OR/retry episode); $v_i(g) = \bot$ (`VIOLATED`) | `Incomplete` |
| **Achieve** | $\neg \text{everSatisfied}$ and leaf condition $c_i(g) = \text{false}$ (or refined children still $?$) during ongoing episode | Remains unresolved in-progress (`PENDING` / `UNKNOWN`); $v_i(g) = ?$ | `Unknown` |
| **Maintain** | $\hat{v}_i(g) = \bot$ at any cut in active episode | Latches `everViolated = true`; $v_i(g) = \bot$ (`VIOLATED`) for the rest of the episode | `Incomplete` |
| **Maintain** | $\neg \text{everViolated}$ and $\hat{v}_i(g) = \top$ | $v_i(g) = \top$ (`FULFILLED`) | `Complete` |
| **Maintain** | $\neg \text{everViolated}$ and $\hat{v}_i(g) = ?$ | $v_i(g) = ?$ (`UNKNOWN`) | `Unknown` |
| **Sustain** | Prior `everSatisfied == true` and $\hat{v}_i(g) = \bot$ | Latches `everViolatedAfterSatisfied = true`; $v_i(g) = \bot$ (`VIOLATED`) | `Incomplete` |
| **Sustain** | $\neg \text{everViolatedAfterSatisfied}$ and $\hat{v}_i(g) = \top$ | `everSatisfied = true`; $v_i(g) = \top$ (`FULFILLED`) | `Complete` |
| **Sustain** | Before first satisfaction, refined child falsified ($\hat{v}_i(g) = \bot$ from refinement) | $v_i(g) = \bot$ (`VIOLATED`) | `Incomplete` |
| **Sustain** | Before first satisfaction, not falsified | $v_i(g) = ?$ (`UNKNOWN` / `PENDING`) | `Unknown` |
| **None** | Any $\hat{v}_i(g) \in \{\bot, ?, \top\}$ | $v_i(g) = \hat{v}_i(g)$ | `Complete` / `Incomplete` / `Unknown` |

---

## 3. Classification of Checks & Global Verdict Rules

| Check Category | Examples in Auction | Role in Verification | Global / Checkpoint Verdict Effect |
|---|---|---|---|
| **1. Mandatory CSL / Authored OCL Invariants** | `Idle`, `Opened`, `RunningForBid`, `RunningForStop`, `Closed`, `AuctioneerPopulation`, `ParticipantPopulation`, `SchemeOrder`, `BidEvidence` | Universal structural and state safety requirements across every verified cut where active | `FAIL` -> Checkpoint `VIOLATION` (hold). `INCONCLUSIVE`/`ERROR` -> Checkpoint `UNKNOWN` (hold). |
| **2. Mandatory CSL Operation Contracts** | `AuctionArtifact::start`, `AuctionArtifact::bid`, `AuctionArtifact::stop` (`OPERATION_PRE`, `OPERATION_POST`) | Independent operation contracts on domain artifact executions | Applicable `FAIL` -> Checkpoint `VIOLATION` (hold). Applicable `INCONCLUSIVE`/`ERROR` -> Checkpoint `UNKNOWN` (hold). |
| **3. Supporting Intentional Conditions (`GOAL_CONDITION`, `TASK_PRE`, `TASK_POST`, `TASK_INVOCATION`)** | `A_start_t1`, `A_start_t2`, `P_bid_t1`, `P_bid_t2`, `A_decide_t2`, `createScheme`, `commitMission`, `O_start`, `O_bid`, `O_decide` | Determine the local three-valued marking ($\top, \bot, ?$) of their own Goal/Task occurrence and propagate through refinements/dependencies | Propagate through the iStar model; do **not** directly force a whole-MAS checkpoint `VIOLATION` unless they falsify a selected root requirement in its applicable scope. |
| **4. Selected Root Requirements (`<requirement target="..."/>`)** | `do_auction` (scoped to initial-goal setup scopes `scope-do-auction`); `O_root` (scoped to organisational scheme scopes `scope-auction`) | Top-level intentional conformance targets for their respective execution scopes | When `VIOLATED` (`Incomplete`) in an applicable scope -> Checkpoint `VIOLATION` (hold) and `Execution: All` `VIOLATION`. While `Unknown` during an ongoing execution -> non-violating in-progress state; if still `Unknown` or `Incomplete` at terminal/settled completion, `VerificationStatusBadge` cannot report `Completed`. |

---

## 4. Before / After Bug Table

| Area / Class | Current Behavior (Before) | Required Behavior (After) |
|---|---|---|
| `TaskMarking` (`use-jacamo-semantic-engine`) | Rejects `VIOLATED` with `IllegalArgumentException("a Task cannot be VIOLATED")`. Maps `pre=true, post=false` to `PENDING`. | Supports `VIOLATED` ($\bot$). Maps `pre=true, post=true` -> `FULFILLED` ($\top$); `pre=true, post=false` -> `VIOLATED` ($\bot$); `pre=false` or not yet at exit -> `UNKNOWN` ($?$). |
| `GoalMarking` & `IStarPropagation` (`use-jacamo-semantic-engine`) | `GoalMarking.fromPropagatedStatus(ACHIEVE, VIOLATED)` calls `update(true, false)` which returns `PENDING` instead of `VIOLATED`. `IStarPropagation.statusOfAnd` returns `UNKNOWN` when one child is `PENDING` (from a failed task) and another is `UNKNOWN`. | `GoalMarking` preserves `VIOLATED` when refinement/dependency propagation is `VIOLATED` ($\bot$), while keeping unachieved leaf/in-progress `ACHIEVE` goals as `PENDING` ($?$). `statusOfAnd` returns `VIOLATED` whenever any child is `VIOLATED`, `FULFILLED` when all children are `FULFILLED`, and `UNKNOWN`/`PENDING` otherwise. `statusOfOr` returns `FULFILLED` when any child is `FULFILLED`, `VIOLATED` when all children are `VIOLATED`, and `UNKNOWN`/`PENDING` otherwise. |
| `RuntimeEngineSessionOwner` & `ExecutionEvidence` (`use-plugin`) | When a Task step fails with `SOURCE_FAILURE_WORLD_POST_UNAVAILABLE` after `PRE=PASS` (e.g. `P_bid_t2` `.fail`), passes `{true, null}` to the engine instead of `{true, false}`, leaving the engine Task marking `UNKNOWN`. `ExecutionEvidence` only certifies `FULFILLED` leaves in `certified` propagation. | When a Task reaches its terminal boundary with `PRE=PASS` and either `POST=FAIL` or unaccepted step `FAILED` (`SOURCE_FAILURE_WORLD_POST_UNAVAILABLE`), passes `{true, false}` so `TaskMarking` becomes `VIOLATED`, records terminal failure in `ExecutionEvidence`, and includes `VIOLATED` leaves in `SemanticResultAdapter.execution(...)` certified propagation. |
| `PresentationResults` (`use-plugin`) | Maps `PENDING` to `"Incomplete"` for Goals/Tasks (causing future/in-progress Goals to appear `Incomplete`), and returns `"Failed"` / `"Violated"` in `agentResult(...)`. | Uses `Unknown`, `Complete`, `Incomplete` for all Goal/Task `Result` cells (with `Not assigned` for `SOURCE_NOT_ASSIGNED` in `Agent instances`). Maps `FULFILLED` -> `Complete`, `VIOLATED`/`FAILED` -> `Incomplete`, and `UNKNOWN`/`PENDING` -> `Unknown`. Shows `Waiting`, `Executing`, `Succeeded`, `Failed`, `Cancelled` in phase/execution columns and details. |
| `VerifiedCheckpointInventory` & `ObservedConformancePresenter` (`use-plugin`) | Promotes every `TASK_POST` failure and every non-root occurrence `VIOLATED`/`FAILED` status (including one participant's `P_bid_t2` `.fail` under `ANY` dependency `D_bid`) into a whole-MAS `FAIL` check, pinning checkpoint and `Execution: All` verdicts to `VIOLATION`. | Treats `TASK_PRE`, `TASK_POST`, `GOAL_CONDITION`, and non-root occurrences as supporting intentional evaluations that propagate to their parent Goals. Only mandatory invariants, mandatory CSL operation contracts, and selected root requirements (`do_auction`, `O_root`) in their applicable scopes drive checkpoint and `Execution: All` failure verdicts. |
| `examples/auction` & Initial-Goal Runtime (`use-plugin`, `jacamo-bridge-*`) | `do_auction` is omitted; only scheme-anchored scopes exist after `SchemeBoard` creation. | Integrates `goal do_auction : Achieve` refined via AND by `task createScheme` (`[0,3)`) and `task commitMission` (`[3,10)`) plus `resource SchemeBoard` (`needed-by commitMission`, `needed-by A_start_t1`) in `role auction_capabilities`, backed by generic initial-goal scope projection, demand qualification, and CArtAgO/MOISE setup correlation. |

---

## 5. Traceability of `do_auction`, `createScheme`, `commitMission`, and `SchemeBoard`

| Model Element | Owner in `auction.istar` | Source Anchor in `auction_capabilities.asl` / `auction.jcm` | Refinement / Link | Runtime Precondition (`pre`) | Runtime Postcondition (`post`) |
|---|---|---|---|---|---|
| `goal do_auction : Achieve` | `role auction_capabilities` | Initial goal `goals: do_auction("a1",...), do_auction("a2",...)` in `auction.jcm` and plan `+!do_auction(Id,P)` (`auction_capabilities.asl` lines 1–14) | Parent of AND-refinement (`createScheme`, `commitMission`); selected root requirement for initial-goal setup scope | Activated per initial goal demand `(run, player, goalInvocation)` |Derived from `createScheme` $\land$ `commitMission` |
| `task createScheme` | `role auction_capabilities` | `+!do_auction(Id,P)` body ordinals `[0,3)` (lines 4–6): `.concat("sch_",Id,SchName); ?joined(aorg,OrgWks); createScheme(SchName, doAuction, SchArtId)[wid(OrgWks)]` | `> do_auction` (AND) | `step.enabled and auctionId <> '' and service <> '' and schemeName = 'sch_'.concat(auctionId)` | `step.succeeded and setup.schemeCreated and setup.schemeType = 'doAuction' and setup.schemeName = 'sch_'.concat(auctionId)` |
| `task commitMission` | `role auction_capabilities` | `+!do_auction(Id,P)` body ordinals `[3,10)` (lines 7–13): `setArgumentValue(auction,"Id",Id); setArgumentValue(auction,"Service",P); .my_name(Me); setOwner(Me); focus(SchArtId); addScheme(SchName); commitMission(mAuctioneer)` | `> do_auction` (AND) | `step.enabled and setup.schemeCreated and setup.schemeName = 'sch_'.concat(auctionId)` | `step.succeeded and setup.argumentId = auctionId and setup.argumentService = service and setup.owner = player.id and setup.groupResponsible and setup.missionCommitted` |
| `resource SchemeBoard` | `role auction_capabilities` | `SchArtId` / `ora4mas.nopl.SchemeBoard` (`sch_a1`, `sch_a2`) created by `createScheme`, configured by `commitMission`, and read by `+!start` (`?goalArgument(Scheme,auction,"Id",Id)`) | `> needed-by commitMission`, `> needed-by A_start_t1` | Bound as iStar `resource SchemeBoard` (`type="AuctionScheme"`, NOT a CSL `Entity`) | Correlated by physical `SchemeBoard` artifact UUID and scheme identity across `createScheme`, `commitMission`, and `A_start_t1` |

