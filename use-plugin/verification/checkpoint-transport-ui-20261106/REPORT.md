# Checkpoint replies, history reads and startup presentation

Date: 2026-11-06. Repositories remain on `main`. Changes are in `use`; no patched
JaCaMo/Jason/CArtAgO/NPL source or authored case model/assertion was changed.

The shipped installation was refreshed and House Building completed using its
packaged consumer, preparation script and producer JARs. Its 15 native Goals were
fulfilled, all 30 presented cuts had matching source release receipts, and the
live offscreen workbench read no raw history pages during refresh. Measured EDT
heartbeat was p95 **55 ms**, maximum **347 ms**. This is automated live/offscreen
evidence, not manual desktop or native diagram acceptance.

## Reproduced causes and repairs

1. **Replies depended on live runtime capability locks.** The checkpoint handler
   called the general capability collector after deciding a command. That
   collector enters the independent Jason controller and observation-provider
   monitors. An already-authorized release could therefore be followed by a
   stalled reply. `CheckpointReplyIsolationTest` reproduced the dependency with
   the controller monitor held; the old handler timed out. Checkpoint envelopes
   now carry the command/status payload without polling unrelated live providers.
   Handshake admission and exact-cut evidence checks remain unchanged. Empty
   capability metadata in a reply does not mean native PASS.

2. **Retrying an old receipt captured the next cut.** The adapter cached the old
   receipt, but still built a fresh status/request before returning it. If the
   following cut's cohort/evidence was unavailable, recovering an already-persisted
   receipt failed too. `CheckpointReceiptReplayTest` reproduced an actual framed
   TCP EOF after a release. Commands now return a compact immutable receipt bound
   to that command; authenticated duplicates return that same payload directly.
   STATUS remains the authority for the current cut. The regression also proves
   that the next cut stays held with its missing evidence and command-ID
   collisions remain rejected. No later cut is released by replaying an old reply.

3. **Old history was reparsed at later checkpoints.** `visitRange` previously
   parsed the complete earlier prefix to reach a new range. The inventory also
   reevaluated the same frozen cut for each invocation record. A bounded optional
   writer index now records physical offsets and SHA-256 prefix anchors. Readers
   authenticate the entire committed physical prefix, including bytes before the
   requested range, then seek directly to that range. Canonical chain anchors and
   native result hashes are still checked. Audit copies are byte/chain checked
   without decoding their duplicate semantic result. Invocation truth is collected
   once without repeatedly evaluating the current cut. Plain/gzip, concurrent
   appends, truncation, earlier corruption, evicted FAIL, every native verdict and
   index-loss fallback have focused tests. The 65,536-anchor cap falls back to the
   original complete scan; it never discards evidence or grants completeness.

4. **Verification animated raw startup history.** Before the first source cut,
   periodic refresh used incremental raw-history replay; after release it could
   also append late "settled" observations. Those were separate publication paths
   from exact checkpoint presentation. Verified live mode now uses only the source
   checkpoint callback from the first Start. Raw events/completions remain in the
   journal and diagnostics/replay. Execution selection is preserved; if it changes
   during preparation, the new projection is prepared on a worker rather than on
   the EDT. `compare-startup.py` ran the same regression against the prior installed
   plugin and the repair: the original unexpectedly read history (assertion
   `expected 0, was 2`); the repair passed and retained the exact reviewed cut.

5. **Status polling repeatedly serialized immutable snapshots.** Live thread dumps
   located EDT work in `VerificationSnapshot.estimatedBytes`, including retention
   and polling/accounting paths. The same frozen copies were encoded repeatedly
   for completion validation, eviction and `retainedBytes`. Retention now caches
   the exact already-checked encoded cost by immutable snapshot identity. A
   temporary completion wrapper can reuse it only when version and result,
   metadata, image and profile references all match. The cache is pruned to the
   retained/pinned copies, is cleared/restored with lifecycle state, and rejects
   changed oversized images under the original limits. Mutation/evaluation stays
   on its existing owners; no asynchronous persistence or secondary MSystem was
   introduced.

The transport exception now also reports the underlying exception class, such as
EOF or socket timeout, rather than hiding it behind one generic message.

The first House Building fault recorded in the preceding audit has no underlying
stack/thread dump. These two reply failure mechanisms were independently
reproduced and repaired; the historical evidence cannot identify which mechanism
caused that particular earlier occurrence. Its original failure is preserved in
the [preceding audit](../additional-cases-20261106/REPORT.md).

## Verification results

`release-validation.log`: **80 focused tests**, zero failures/errors/skips. No
clean verify was run. This includes authentication/nonce rejection, lost replies,
foreign identities, repeated exact review, whole-source gap/early-boundary and
shared-intention regressions, disk history/quotas/corruption, snapshot retention,
native failed Task/deadline presentation, execution scopes, authored bundle
integrity and required data coverage. No semantic assertion was removed or edited.

| Final code / live method | Native outcome / scope | Presented cuts | Required gaps | EDT p95 / maximum |
| --- | --- | ---: | ---: | --- |
| House Building, development workbench | 15/15 Goals FULFILLED | 29 | 0 | 55 / 327 ms |
| Hello World, development workbench | 2/2 Goals FULFILLED | 14 | 0 | 58 / 295 ms |
| Auction, bounded transport/UI regression | 12-cut prefix; **no business-completion claim** | 12 | 0 | 114 / 252 ms |
| House Building, installed workbench | 15/15 Goals FULFILLED | 30 | 0 | 55 / 347 ms |

Auction encountered one real native Task violation. The automated test retained
the failure, reviewed that exact token/result and continued; the case was not
converted to PASS. Its unknown/unassigned intentional elements are not treated as
fulfilled. Bounded transport acceptance is explicitly separate from Goal
completion and from a whole-run conformance claim.

The workbench probes invoke real Prepare/Import, Start and checkpoint-presentation
callbacks, maintain the same authoritative MSystem, and paint both Verification
facets before completing presentation. Native model/state diagrams are omitted
from the headless harness. The journal's original model/input hashes and native
results are retained in every receipt. `reconcile-evidence.py` checks source
decision/release identities, accepted state hashes, the inventory hash computed
from each authentic native result, and the exact result rendered by the UI.

The earlier development House Building runs also completed; one had a 1,507 ms
heartbeat maximum and prompted the additional snapshot-sizing repair. They remain
in the audit rather than being silently replaced by the final measurements.

On the same authentic House Building result, `history-benchmark.json` records
identical native result/check hashes and bytes: the median range-read time fell
from **975 ms to 234 ms** (about **4.16x**). It authenticated all 3,808,785 stored
bytes while parsing only 30 requested records and decoding 27 non-audit results.

`budget-benchmark.json` records the same 323,026-byte snapshot and 646,052-byte
reported retention budget in both versions. One hundred budget/status reads took
about **495 ms before / 0.073 ms after**; the repaired retention encoded the cost
once. This is a focused accounting microbenchmark, not a whole-application speedup.

`SUMMARY.json` includes every attempt. Two installed harness attempts incorrectly
combined consumer and producer/Maven classpaths and were rejected by the unchanged
engine duplicate-ANTLR guard. The corrected harness uses exactly the shipped
consumer classpath (host + shaded plugin), with the full framework classpath only
in the separate producer. `consumer-resource-origins.log` verifies the single
pinned consumer engine/runtime resource; the guard was not disabled. An earlier
Auction probe lacked a manifest, and another ended without a final receipt while
investigating serialization; neither is counted as acceptance or domain FAIL.
The audit now reads unambiguous authored `.mrv` inputs when no case manifest exists.
No case input file was added or modified to accommodate the harness.

## Runnable installation

Launcher:
`D:\_CODE_BANK\Project_\08_Thesis\use\use-assembly\target\new\bin\start_use.bat`.

`package-current.py` incrementally refreshes the existing installation, preserves
its work/replays and creates backups. The three previously user-deleted legacy
Core release inputs remain absent. No clean release ZIP/assembly is claimed.
The CLI launcher started and exited with `-nogui` / `quit`, exit code 0.

`package-receipt.json` contains **21 freshly loaded CodeSource/hash identities**,
including every changed runtime/presentation class. The actual patched JaCaMo JAR
is `target/new/bridge/runtime/jacamo-1.3.1-checkpoint-SNAPSHOT.jar`, SHA-256
`aca1163c73b854123526f52c22c0ea8c6f9bc772376c5fb73d20a1aa4c6af155`.
The packaged plugin SHA-256 is
`9cbc59e2d716f83558ede416681bc09ec867da3b2b163d64e0cd72ab5b4b56ed`;
the packaged official adapter SHA-256 is
`a2c8296f9c82079384b1db97537a9e36cef3a42a150486be33ee1579f29c384c`.
The installed live run uses its own preparation script, bundled source inputs and
producer framework classpath, not development production classes.

Authored CSL/iStar/XML/OCL/JCM, native Goal/Task/OCL semantics, policy selection,
exact-token release, frozen source clocks and fail-closed behavior are preserved.
`WithinDeadline` remains disabled under the earlier explicit user choice. Raw
supporting coverage/UNKNOWN/SKIPPED remain distinct from required checkpoint data
coverage. Manual acknowledgement records review and never repairs a domain state.

Manual Computer Use and native diagram acceptance were not performed. No finite
test run establishes absence of every future network/OS scheduling failure;
missing evidence, disconnection and protocol errors must continue to retain a
hold rather than becoming PASS.
