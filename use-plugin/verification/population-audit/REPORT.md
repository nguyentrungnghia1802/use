# Audit Auction: capability summaries and intermittent population failures

Audit only, 2026-10-29. No production, case-study, semantic assertions, plan or build artifacts modified. New files are this report and standalone audit evidence. No new MAS run, no other case study, no full clean verify. Installed final package was inspected directly.

## Evidence and limits

- User images show 17 milestones and session prefix `e69b8a82-9141-40a2-8d5b-7030af0a90c`.
- The exact user runtime journal is unavailable in the examined installation after the app closed. This audit does not claim it recovered that session.
- Reproduced the same Milestone 1 / Milestone 6 failure pattern using a genuine accepted live Auction recording: `../live-results-ui/auction-10902800656564572545/replay/` (19 milestones, 1028 displayed audit cuts, 1618 journal entries).
- `PopulationAudit.java` loads the real installed plugin and its original presenter, grouping logic, native models, OCL evaluator and coverage resolver. The coverage counterexample uses a separate test-owned in-memory system, never a live user system.
- `findings.json`, `recurrence.json`, `integrity.json`, `audit-final.log` and `installed-replay.log` contain executable evidence.

## PASS — UNKNOWN / MIXED explained by exact instances

The capability Agent declares all skills, while the project has five concrete agents and two scheme episodes. Each capability definition consequently has 10 observed instances. The source responsibilities are Bob as auctioneer; Alice, Maria, Francois and Giacomo as participants.

| Definitions | Certified instances | Exact players |
| --- | --- | --- |
| C_start, A_start_t1, A_start_t2 | 2 FULFILLED, 8 UNKNOWN | Bob fulfilled twice; four participants have no admitted start demand, twice each |
| C_decide, A_decide_t2 | 2 FULFILLED, 8 UNKNOWN | Bob fulfilled twice; four participants have no admitted stop demand, twice each |
| C_bid, P_bid_t1, P_bid_t2 | 8 FULFILLED, 2 UNKNOWN | Four participants fulfilled twice; Bob has no admitted bid demand in either episode |

Unknown Task quality is `XML_TASK_TUPLE_UNAVAILABLE:DEMAND_INCOMPLETE`; parent capability Goals use `DERIVED_INPUT_UNAVAILABLE`. The original marks differ across these instances, so the raw summary is MIXED. MIXED is a UI summary, not an original engine failure verdict. No evidence was coerced into fulfillment.

This is a conservative applicability/presentation limitation: the runtime instantiates every skill for every capability player. Missing demand is not itself proof of NOT_APPLICABLE, so automatically replacing UNKNOWN with N/A or FULFILLED would be unsound. A future applicability improvement must use explicit role/demand evidence and preserve unclassified instances.

The selected requirement is explicitly `O_root` in the XML. Role dependencies delegate to the same physical player's capability; the organization requires the declared ANY participant branch plus real Artifact acceptance. Supporting capability summaries are not a universal requirement that every agent performs every skill. Their UNKNOWN values therefore do not alone block the selected root. This is declared mapping semantics, not an Auction-name shortcut in Java.

Sources: `examples/auction/auction.jcm:11`, `auction.istar:22-58`, `auction-mapping.xml:531-546,665-669`; `LiveGoalSummary.java:21-29`.

## FAIL at Milestone 1 — predicate and lifecycle

CSL declares an unconditional invariant:

```ocl
context auctionGroup inv AuctioneerPopulation:
    self.auctioneer->size() = 1
```

The first evaluated group state has AuctionOrg and auctionGroup, zero materialized auctioneer occurrences and no enactment links. The native OCL predicate is false (0 = 1). The source group's declared bounds also say auctioneer min=1, max=1, but the launcher constructs the group before agents finish adopting roles. Thus a transient initialization state can violate an unconditional invariant without the final auction execution being broken.

The case model has no guard meaning “only once group initialization/role population is ready”. Whether startup should count as a violation is a specification/lifecycle decision. If the requirement is operational readiness, it needs an explicit observed activation condition. Do not delete the minimum or infer readiness from a winner/string/time delay.

There are TWO rows with the same name because the evaluator emits `INVARIANT_BODY` for each group and `INVARIANT` for the quantified aggregate. With one group, both are false. This is one underlying constraint/counterexample, not two independently violated rules. The main failure selector currently exposes both, which adds unnecessary duplication.

`QUANTIFIED_NATIVE_INVARIANT;POPULATION_COMPLETE` refers to the quantified context population (auctionGroup instances), not a claim that all JaCaMo sources, startup, roles or tasks are complete. Global sources can remain PARTIAL. More seriously, the separate derived-role completeness check is currently defective (ISSUE 1 below).

## ISSUE 1 — confirmed coverage gate bypass through a derived attribute

`auctionGroup.auctioneer` is a derived native attribute, calculated as:

```ocl
self.roles->select(r | r.oclIsKindOf(auctioneer))
    ->collect(r | r.oclAsType(auctioneer))->asSet()
```

The dependency visitor records only the outer attribute read; it does not visit this attribute's derive expression. The existing completeness guard for `InContext.roles` therefore does not run. It sees zero navigation reads instead of the underlying role membership navigation.

Executable negative probe against the installed package:

| Property | Actual |
| --- | --- |
| Context binding quality | COMPLETE |
| Role materialization quality | INCOMPLETE |
| OCL | self.auctioneer->size() = 1 |
| Coverage rejection reason | empty |
| Native body result admitted | FAIL |
| Navigation dependencies discovered | 0 |
| Equivalent explicit self.roles expression | rejected: XML_CURRENT_ROLE_MATERIALIZATION_INCOMPLETE |

The two equivalent predicates receive different evidence admission. This is a system coding bug, not merely a case-study issue. It can classify incomplete materialization as a real empty set; a true native result may likewise lack sufficient evidence. The source of this defect is not the OCL arithmetic.

Root cause: `ExternalOclConstraintService.DependencyVisitor.visitAttrOp` / `AbstractCoverageVisitor.visitAttrOp` do not traverse derived-attribute bodies, while `ConstraintCoverageResolver.unavailableSelf` checks role evidence through discovered navigation reads only.

Proposed repair: generic transitive derived-attribute dependency analysis (with recursion-cycle detection, exact receiver/self and pre-state handling), shared by coverage and dependency scheduling. Ensure that partial roles yield SKIPPED/unavailable, complete empty roles yield a genuine false result, complete one-role states yield PASS, and role removal/re-enactment re-evaluates the invariant. No case-name conditions, no assumption that undefined means empty, no skipped-to-pass coercion.

Sources: `CslScopeModelAdapter.java:43-49`; `ExternalOclConstraintService.java:449-452`; `use-core/.../AbstractCoverageVisitor.java:79-82`; `ConstraintCoverageResolver.java:78-85`.

## ISSUE 2 — confirmed false recurrence and duplicate Failure Points

The current Failure Point implementation compares failures only with the immediately preceding cut. A pending, NOT_EVALUATED or SKIPPED cut has no active FAIL rows, clearing its remembered active set. If the same predicate fails on a later evaluation, it is added as a new point without any qualified recovery.

The recording reproduces a point at Milestone 1 and repeated points at Milestone 6. Between the first failure and the first Milestone 6 repeat there are 7 FAIL evaluations and 5 NOT_EVALUATED cuts, **zero PASS evaluations**. Around the repeated point:

- Ordinal 26: Jason event; NOT_EVALUATED; no population rows.
- Ordinal 27: another Jason event; NOT_EVALUATED; no population rows.
- Ordinal 29: stream boundary evaluation; same population body/aggregate FAIL.

Therefore Milestone 6 does not establish “it was repaired and then failed again”. It is a re-evaluation of an unresolved early failure. Across the recording every admitted population FAIL is associated with zero projected auctioneer objects. No zero-role FAIL occurs after the auctioneer first appears in this recording.

Other milestones can show no failure because their representative cut is after role materialization or because the rule is not evaluated at that checkpoint. SKIPPED/NOT_EVALUATED means no new judgment, not recovery. A milestone groups business projection; it is not a unique OCL evaluation, and Failure Point retains the exact failing audit cut within that group.

Proposed repair: track the last qualified failure/recovery per constraint and exact group/occurrence/epoch; pending/SKIPPED/unknown do not clear it. Add a new failure episode only after qualified recovery (with an explicit lifecycle/reset rule for disappearance). Keep the earliest witness and subsequent observations available. Present the body/aggregate as one constraint failure, expanding object witnesses below; aggregate and body are not independent failures.

Source: `ObservedConformancePanel.java:150-162`, particularly `previous=active`.

## Current completion versus trace-wide correctness

At the pinned final complete source cut, O_root is FULFILLED for both auctions, and these required aggregate invariants PASS:

- ConfirmedSchemeOrder
- ObservedBidEvidence
- AuctioneerPopulation
- ParticipantPopulation

The supporting helper CslV4RoleCompatibility remains SKIPPED; source coverage remains INCOMPLETE. It is inaccurate to say that literally every OCL row or every source is fully verified.

`COMPLETE_OBSERVED` is calculated for the selected cut/requirements. It does not mean that the run was violation-free at all previous states. If an invariant is intended as a safety property over the entire trace, a later PASS does not erase an earlier genuine counterexample. Current UI should distinguish current conformance from historical violations instead of implying whole-trace success.

The fresh isolated installed replay passed all 1618 entries and matches the recording's final state/result hashes. This proves reproducibility and integrity, not semantic freedom from the bugs above: replay can faithfully reproduce an incorrect coverage decision.

## Overall assessment

**PASS:** exact instance accounting, conservative UNKNOWN, explicit selected root/SAME_PLAYER/ANY mapping, final recorded root and required predicates, installed replay integrity, source preservation. Scan of 613 production Java sources found no tested case-name constants. All 650 frozen production/system files match the latest accepted source overlay.

**ISSUE:** derived-role coverage bypass, false failure recurrence across unassessed cuts, duplicate body/aggregate presentation. The unconditional startup invariant also requires a deliberate lifecycle specification decision.

**UNCERTAIN:** exact source/materialization quality and exact event sequence in the user's separate 17-milestone session cannot be certified from screenshots. This report reproduces the pattern in a real 19-milestone recording; it does not pretend it is the same run.

**Conclusion:** no evidence of a hardcoded success result in this path, but the tool must not yet be called fully correct. Final selected-cut completion can coexist with supporting unknown skills and prior failures. Repair the generic coverage gate and failure episode tracking before considering this audit closed. No fixes were made in this audit.
