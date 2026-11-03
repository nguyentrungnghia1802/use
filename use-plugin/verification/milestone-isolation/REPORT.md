# Execution milestone isolation — 2026-11-01

## Confirmed defect and evidence

The reported run is preserved, not reset. `user-fixture-provenance.json` identifies
the original disk journal, its SHA-256, its checked 1,891-entry chain and eight
exact EVALUATED native result hashes. The compressed regression fixture contains
those original results and source requests. Java `RuntimeVerificationResult.fromMap`
checks their result hashes again before using the real presenter and projection.

There is a presentation bug: the checkpoint timeline signature included diagnostic
quality and constraint invocation identifiers. A checkpoint for another execution
can turn `KNOWN` into `KNOWN_HISTORY`, or change the explanation of an unchanged
UNKNOWN. That produced another visible milestone for a2 without changing a2's
Entity or qualified results. There is no evidence here of a1's Entity values being
copied into a2.

The recorded exact checkpoint sequence for a2 is:

| Journal ordinal | Scoped change | New compact milestone |
|---|---|---|
| 994 | Initial bound, evaluated state | 1 |
| 1089 | a1 start; a2 diagnostic quality only | 1 |
| 1384 | a2 bid state and its own results change | 2 |
| 1457 | Same a2 CSL, but additional a2 Task/Goal occurrences become FULFILLED | 3 |
| 1570 | a2 bid state and its own results change | 4 |
| 1687 | a1 bid; a2 diagnostic quality only | 4 |
| 1785 | a2 closes and its own results complete | 5 |
| 1862 | a1 closes; a2 diagnostic quality only | 5 |

This distinguishes two visually identical CSL panels with different native
Task results from a genuinely duplicate presentation transition. The transient
desktop milestone numbers were not persisted in the journal; the table identifies
exact source cuts rather than asserting an unrecorded old UI number.

## Change

`CheckpointTimeline` groups consecutive scoped cuts by selected Entity state and
qualified local results. It preserves kind, element, context, Goal occurrence,
player, status and required/qualified distinction; row ordering, proof invocation
and diagnostic wording cannot create a business transition. Current and retained
certificates count as the same qualification for grouping only. The native results,
proofs and hashes themselves are never rewritten.

All qualified scoped boundaries now remain in Audit details, including the ones
grouped into an existing milestone. That milestone displays its latest exact cut
and source token. Audit-to-milestone numbers and navigation fallback retain the
selected milestone when its representative changes. An own Task/result change or
failure still creates a milestone even when the CSL Entity is unchanged.

No source policy, barrier, scheduler, model assertion, verdict, or external Auction
source was changed for this fix. No JaCaMo production change was necessary.

## Verification

- Reproduction before fix: `red-tests.log`, two failures, eight milestones instead
  of five using the real recorded native cuts.
- Initial focused run: `focused-tests.log`, 41 passed, including authentic recording,
  offscreen Swing navigation, audit history, certified Goal history and tab state.
- `live-tests.log` / `live-1793519916722`: observation-mode harness baseline only.
  The first new harness omitted explicit verified-mode setup; its successful
  completion does **not** establish source checkpoint acceptance.
- `live-verified-tests.log` / `live-1793520132787/receipt.json`: explicit verified
  mode passes with eight automatic APPROVE releases, each after DISPLAYED with
  the same token/result hash, no manual acknowledgement, two native fulfilled
  roots and two COMPLETE_OBSERVED UI executions. Both timelines retain nine
  scoped audit cuts, grouped into seven and five milestones respectively. Repeated
  execution/CSL/iStar-tab changes and Audit/Milestones navigation pass. Different
  runs need not have equal milestone counts: their own Task progress is real.
- `final-package.log`: 42 focused tests passed; no failures/errors/skips, then
  `use-assembly` package succeeded. Includes retained FAILED Task truth, qualification
  labels and engine row-order changes without extra milestones.
- `milestone-final-package-receipt.json`, `milestone-packaged-code-sources.log` and
  `milestone-installed-loader-smoke.log`: built/installed plugin, bridge and GUI
  byte parity and real installed plugin loader pass. The actual loaded JaCaMo is
  `target/new/bridge/runtime/jacamo-1.3.1-checkpoint-SNAPSHOT.jar`, SHA-256
  `f5a31568fbc2487322d985e00ab6cecac987f2edc296bf14cd155c56c217275e`;
  `BRIDGE_CHECKPOINT_STRICT_SUPPORTED=true`.
- `preserved-install.json`: previous complete installation and user history are at
  `use-assembly/target/new-before-milestone-isolation-20261101`. The original USE
  owner had already exited; its orphan producer received its normal owned
  `stop.flag` before the directory move. The user journal hash is unchanged.
- `final-freeze.json` binds these source files, fixture, live/package evidence,
  installed JAR hashes and the preserved journal identity.

Run the updated installation with
`D:\_CODE_BANK\Project_\08_Thesis\use\use-assembly\target\new\bin\start_use.bat`.

The recorded/offscreen checks are separate from a fresh live MAS test and from
manual desktop acceptance. No commit/push or full clean verify.
