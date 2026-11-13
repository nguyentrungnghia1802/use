# Auction iStar actor and responsibility correction

**Superseded semantics:** the six-dependency model below was corrected to three
dependencies and two participates-in relations with shared Goal views. See
[`../state-only-semantics-20261112/REPORT.md`](../state-only-semantics-20261112/REPORT.md)
for the action-level semantic audit, current evidence and remaining gaps.

The state-only model now has exactly four intentional actor nodes:

| Declaration | Responsibility |
| --- | --- |
| `agent AuctionGroup` | Organizational goals and state safety |
| `role Auctioneer` | Opening and concluding the auction |
| `role Participant` | Bid participation |
| `actor auction_capabilities` | Shared capability goals and state achievement Tasks |

Six dependencies delegate opening/bidding/concluding from organizational goals
to the responsible roles and then to the capability goals. Each capability goal
has a single realization Task. This uses the existing ROLE_SPECIALIZATION_V1
admission profile and SINGLE_OWNER_CONTEXT_V1 topology, preserving original
Goal/Task recurrence and dependency propagation.

`actor` is the explicit P-ACTOR-01 project extension. Its GenericActor type is
preserved in both native instance topology paths. The source provenance manifest,
exact patch diff, source-scope guard and deployment grammar hash were updated;
existing Agent/Role parity tests against the independent original oracle pass.

`@csl-state Auction` selects the CSL object on which OCL is evaluated. There is
one independent four-view graph per physical Auction. These intentional views
do not claim one occurrence per Bob/Alice; physical players and enactments stay
in CSL and are navigated through the Auction's organizational scope. No Jason
plan, action outcome or task receipt is an input to these predicates. The XML
mapping remains eight attribute correspondences.

Validation:

- Semantic engine: 105 tests, zero failures/errors, package build passed.
- State verification: 13 tests, zero failures/errors, package build passed.
- The example test advances only CSL state through initial/opened/bid/closed
  snapshots and checks propagation to role and organizational goals. A second
  Auction retains independent history.
- The real two-JVM Counter test still holds after a state violation and never
  executes the subsequent plan.
- Real Auction: 127 checkpoints, all APPROVE. Both auctions complete. Output
  contains all four actor names; capability, role and organizational completion
  goals are FULFILLED for each Auction.

`receipt.json` records test suites, final statuses, model hashes and the runtime
directory. `last-checkpoint.json` contains the final source snapshot and native
result. Authentication secrets are excluded.
