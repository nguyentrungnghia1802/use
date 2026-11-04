# CSL / iStar presentation review

## Scope

This change affects presentation: execution selectors, result vocabulary, diagram
geometry, model/runtime separation and the read-only graph projection. Native
MSystem state, authored assertions, Goal/Task marking, checkpoint selection and
exact-token hold/release are unchanged. The source-boundary review is in
[source-checkpoint-proposal-20261104.md](source-checkpoint-proposal-20261104.md).

## Behavior

* Every execution selector in CSL/iStar State and Verification includes `All`.
  The existing execution choices remain available and retain separate cursors.
* `All` uses admitted execution identities, evaluates no new expressions, and
  displays the scoped native rows together. Repeated OCL definitions include the
  execution label. Missing evidence remains unknown. An execution already seen
  in the prefix cannot disappear from verification by being absent at a later cut.
* An actual verification violation anywhere in the selected history prefix keeps
  the `All` result at `VIOLATION`, even when later states recover. Navigating back
  before the first violation still shows the result of that earlier prefix. Native
  recovered verdicts and all original evidence remain intact. False guard/condition
  queries are not reclassified as violated invariants.
* Visible navigation uses `State`. Internal IDs and audit identities stay stable.
* CSL State/Verification no longer synthesize `participates (execution)` links
  from execution-cast witnesses. Native declared links remain visible. Cast and
  ownership evidence remain available for verification and diagnostics.
* CSL verification has `Name`, `Body`, `Result`. True/False comes from native
  results; Unknown/Error remain distinguishable. Exact quality/witness details are
  retained rather than replaced by the shorter table text.
* iStar verification has `Goal / Task`, `Result`. Prefixes `Goal:` and `Task:`
  identify the type in one ordered list. Complete requires qualified fulfillment;
  pending/failed/violated is Incomplete; unavailable/unassigned is Unknown. This
  is a display translation, not a new semantic status or a forced verdict.

## Model and State

The workbench already supplies the authored `GoalModel` to Model and an expanded,
qualified occurrence graph to State. The problematic overlap was runtime badges
being added to the authored Model view. Model now stays unmarked. State retains
player labels and qualified runtime badges. `All` shows separately titled instance
graphs on one scrollable page, so identical element names from different
executions cannot collide. Stable topologies/views are reused between states.

The reference Draw.io files are authoring examples, not production constants.
`Core/auction/auction_csl.drawio` declares organization/role/entity structure and
does not declare the injected execution-participation association. The iStar
reference declares actor rationale, specialization, refinements and dependencies.
The generic renderer takes the models selected in the system specification; it
does not substitute a particular reference file or copy Auction coordinates.

The current `Core/auction/auction_csl.csl` and the embedded `cslSource` of the
reference Draw.io differ in where Auction is contained (auctionGroup versus
AuctionOrg). This UI change does not silently rewrite that authored semantic
choice. A view reflects the exact selected CSL input.

## Diagram components

CSL, state and iStar diagrams continue to inherit USE DiagramView, PlaceableNode,
EdgeBase and USE waypoints. Model associations that already use USE binary
association edges keep that implementation. State/iStar now also inherit the full
USE popup for alignment, layout, waypoints and display options. Custom edges share
a cached obstacle router, and user waypoints take priority. iStar rationale uses
refinement layers; dependency-connected actors are placed next to each other and
dependum nodes are kept out of unrelated rationale boxes.

These changes reduce overlap; they do not promise a crossing-free drawing for
every possible nonplanar graph. Native layout and manual waypoints remain useful
for dense models. No association is removed just to improve geometry.

## Delivery notes

The user explicitly requested committing the existing Core model additions and
nine resource deletions as-is. Those resources are still referenced by the POM
and release manifests; this independent source-layout inconsistency is retained
as requested. It must not be presented as a proven clean release build.

## Executed checks

29 focused tests passed with zero failures/errors/skips: All execution/history,
scope isolation, missing evidence, State navigation, native relation projection,
Goal/Task display, refinement geometry, obstacle routing and relocated-view
provenance. Test classes are `AllExecutionPresentationTest`, `ExecutionScopeViewsTest`,
`WorkspaceExecutionViewTest`, `RuntimeStateViewsTest`, `LiveGoalSummaryTest`,
`CheckpointMilestoneIsolationTest`, `ReusedSemanticViewsTest` and
`CapabilityApplicabilityViewsTest`. Recorded/offscreen CSL/iStar images were
rendered under `target/state-views` and `target/ui-model-review`; the model and
All instance images were visually inspected. This is not fresh manual desktop
acceptance.

`VerifiedCheckpointsAuctionIT` also passed (1 test, 261.8 seconds). Its unmodified
external Auction source and existing public-operation evidence harness produced
2 automatic cuts, 11 distinct reviewed held cuts/release receipts, and 8 genuine
native failed cuts. Failed/unverified holds and review receipts remained intact.
The interruption/reconnection replay reports retain `REPLAY_PARTIAL_COVERAGE_GAP`;
they are not claimed as complete replay coverage. Full local evidence is at
`verification/verified-checkpoints/live-auction-1793780758307`.

The incremental Maven `package` attempt compiled the code and passed the live
test, then failed at release assembly because the explicitly deleted
`Core/Metamodel/version-2/jacamo_v2_complete.ecore` is still referenced. No full
clean/verify was run, and this failure was not hidden by repairing the model inputs.

A runnable **UI preview**, not a new source-complete release, was assembled at
`use-assembly/target/ui-preview-20261104/bin/start_use.bat` from the previous
successful distribution plus the newly built plugin, GUI, core and bridge JARs.
The copied JARs match current build bytes. Runtime fingerprint and installed
plugin-loader smoke passed; patched JaCaMo SHA-256 is
`dede2e2cde568c747a774e33f821cdcee6501515997a710816aa9c02c7c901f5`.
The canonical `target/new` and its running user sessions were retained.

See [the compact receipt](evidence/ui-state-review-20261104.json). Generated
journals, replay folders and build artifacts remain local.
