# Attribute-only CSL state synchronization

**Superseded model:** this initial demo flattened Auction to an entity. The
organizational correction and fresh live evidence are recorded in
[`../state-only-org-20261112/REPORT.md`](../state-only-org-20261112/REPORT.md).

Date: 2026-11-12. Implementation: `modules/state-verification`.

The new mapping namespace admits attribute correspondences only. Neither the
producer configuration nor the native iStar evaluator requires a Jason-to-Task
binding. The iStar model selects a CSL context type; OCL reads that native state,
and the original use4Goal recurrence and propagation compute markings.

## Executed evidence

- Nine focused tests passed: seven native state/mapping tests, one authenticated
  framing test, and one live two-JVM JaCaMo/USE test. No failures, errors or skips.
- The live Counter test observed `0`, `1`, `2`, `-1`. At `2`, TargetReached was
  fulfilled. At `-1`, the CSL Nonnegative invariant failed and the iStar Maintain
  goal was violated. The producer remained held before the `must_not_run` plan;
  `999` never appeared in the accepted states.
- A separate test makes only iStar fail while all CSL invariants pass; the
  returned decision is still HOLD_FAILURE.
- The existing Auction `.jcm`, ASL and Java sources ran in a real JaCaMo producer
  with the new five-attribute mapping and the new state-defined CSL/iStar models.
  There were 129 accepted checkpoints, all APPROVE. Both Auction objects ended
  with `running=false` and published winners; both state achievement roots were
  fulfilled. One source plan FAILED at checkpoint 89 and still received APPROVE
  from the valid CSL/iStar state. Source outcome therefore did not masquerade as
  an iStar Task result.
- The Counter desktop window was opened and its held native state was inspected;
  `counter-hold.png` records the actual window at checkpoint 10, value `-1`.

`receipt.json` retains the exact inputs, final state, markings and test inventory.
Large producer/native audit streams remain in the listed temporary work directory;
the authentication secret is not copied into repository evidence.

## Delivery boundary

Run with `bash use-plugin/tools/run-state-only.sh` from `use`; pass `--counter`
for the deterministic failing example. The new namespace is handled by this
separate native window, not the existing compact Workbench Import action.

This delivery supports local Artifact scalar attributes and one CSL context type
per intentional graph. It uses state-defined iStar requirements; it does not
claim equivalence to the existing source-invocation Auction Task contracts.
Full project reactor/package verification and arbitrary scheduler/remote/Java
thread suspension were not claimed. See the runnable example README for scope.
