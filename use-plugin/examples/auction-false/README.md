# Auction authored verification bundle

Open Plugins > JaCaMo > use4Goal verification. Select auction.jcm, auction.csl,
auction.istar, auction-mapping.xml and optional auction.ocl; then Prepare + Import
and Start Runtime. Verified checkpoints is the main workflow. JaCaMo owns source
execution; USE evaluates the exact admitted cut in its authoritative native MSystem.

The Core auction_csl.drawio and auction_istar_final_fixed.drawio are the model
authorities selected by the user on 2026-11-05. Their embedded canonical sources
match this bundle. Historical test fixtures are preserved separately.

CSL Auction belongs to auctionGroup.entities. auction_capabilities is a separate
singleton-player OrgCtx with actual enactments, not Role inheritance. It does not
participate in the Auction Entity through an invented execution association.
Optional belief_best_bid is Real; Atom maps to String; ambiguous/missing optional
beliefs remain undefined. The visible winner default spelling defaul is retained;
actual observed source values supply runtime state.

The iStar capability node is a Role, as declared in Draw.io. Its physical-player
occurrence view represents the shared program available before mission assignment;
it is neither an additional running agent nor a fabricated MOISE role enactment.
Five Tasks share one Resource; the three original external Dependencies and two
participates-in links are retained. There are no extra capability Dependencies.
Explicit SAME_SCOPE_PLAYER_REFINEMENT_VIEW/1.0 XML declarations connect repeated
Goal views with equal structured owner/sourceGoal metadata. Native evaluation
uses the same player's captured source Task evidence in the same execution scope
and Role interval. Unrelated players, reenactments and missing evidence cannot
borrow achievement. The native Goal/Task engines remain authoritative.

P_bid_t2 requires step.succeeded. The source .fail branch is an actual failure;
D_bid uses the explicitly drawn ANY successful assigned performer policy. Winner
is not bid evidence. Administrative recovery permits source progress but never
repairs failed Tasks, native truth or expired obligations.

SchemeOrder and BidEvidence use actual public MOISE state history, successful bid
receipts and the exact observed administrative override. Their time fields are
source-clock MOISE transition publication times, not TCP receipt time or an
assertion about private internal engine timestamps. WithinDeadline uses actual
NPL activation/deadline/first-terminal times on the qualified logical source
clock. It retains unfulfilled evidence after recovery. Norm population is closed
only with same-cut physical board inventory, empty creation subscription and an
exact continuous retained activation catalog. Missing evidence stays unknown.

AuctioneerPopulation keeps size() = 1. Readiness activation latches only after a
qualified roster and matching same-cut native population; later role loss fails.
Native operation contracts and observer clauses are embedded @use-native-ocl 1
annotations. Optional auction.ocl is comment-only to avoid duplicate clauses.

Each admitted native Task terminal holds its intention before the next deed.
Other admitted Tasks on the same agent drain or park; operation/notification
admission and source logical time join the whole supported barrier before READY.
Source checkpoint identities come from the versioned policy and admitted mapping,
not displayed State numbers. Unchanged CSL can accompany genuine local Task
progress; other executions do not manufacture States in this execution.

Qualified PASS releases automatically after exact-cut presentation. Failure,
missing evidence, disconnect or timeout retains the hold. Acknowledge & Continue
records review and releases only that exact token; repeated failures can be
reviewed independently. Every original state, error and evidence remains in history.

See verification/source-checkpoints-20261105/MODEL-AUDIT.md and REPORT.md for
source correspondence, executed evidence and acceptance limits.

This variant intentionally omits running=false in AuctionArtifact.stop(). Its
stop postcondition and decide Task can fail; the fault is not repaired by review.
