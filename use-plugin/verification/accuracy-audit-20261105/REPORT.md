# Current-system accuracy audit - 2026-11-05

## Result and evidence boundary

Two fresh runs of the unchanged external Auction completed the source-defined
settled boundaries for both executions. Both runs detected an actual failed
bidding Task, held its exact checkpoint, and continued after a simulated explicit
review without changing the failed native evidence. This is successful detection
of a case-study failure, not a claim that the case study satisfies every contract.

No production logic, source case, mapping, assertion, verdict, random seed, winner,
or installed application was changed in this audit. The added Java main and Python
scripts are test/evidence tools only. They use the ordinary owned runtime and
native verification service. No extra stop/bid operation or input Java agent was
injected. In particular, these runs do not reuse the older negative-operation
injection scenario as evidence of natural Auction behavior.

| Natural source run | Presented checkpoints | Automatic release receipts | Reviewed failure receipts | Settled execution scopes | Applicable missing/error checks |
| --- | ---: | ---: | ---: | ---: | ---: |
| 20261105-231419-582-2fdbc54a | 15 | 14 | 1 | 2 | 0 |
| 20261105-231912-509-7829aa1b | 13 | 12 | 1 | 2 | 0 |

These are source checkpoint counts, not expected fixed UI State counts. Source
task concurrency and causal coalescing can produce different counts. No expected
count or verdict was inserted into production to obtain these results.

## The actual failures

The external project was:

`D:/_CODE_BANK/Project_/vnu-sme-lab/jacamo/examples/auction/auction.jcm`.

Its `src/agt/auction_capabilities.asl` lines 34-41 contain a genuine branch:
`math.random < 0.9` executes the bid; the other branch prints the expected-error
message and executes `.fail`. Thus even the ordinary Auction can fail a bidding
Task. The source comment saying 80 percent does not change its executable 0.9
threshold. The source was left unchanged.

In the first run, alice failed in sch_a1. In the second run, giacomo failed in
sch_a1. In both cases, the native `TASK_POST` row for `P_bid_t2` evaluated the
authored `step.succeeded` predicate to `false`. Its witness recorded `phase=FAILED`
and `observationQuality=COMPLETE_FOR_EXPRESSION`, with an exact source invocation,
player, state version and matching state hashes. Producer stderr independently
reported `code(.fail)`, `code_line(41)` and `scheme(sch_a1)`.

The failure came from source behavior and a real postcondition, not a checkpoint
number, a synthetic aggregate row, a guessed winner, or a forced test result.
The aggregate checkpoint check merely carried that failed invocation to the hold.

The harness waited at the held token, checked its identity/result remained stable,
then simulated Acknowledge & Continue for that exact token/result. It checked the
original presented native result hash remained in runtime verification history.
Review did not repair the Task, change its predicate, or certify PASS.

The source subsequently handles an unfulfilled bid obligation through
`admCommand("goalSatisfied(bid)")`. That administrative action permits scheme
progress. It does not prove that the failed participant bid successfully.
Accordingly, an organizational root can finish under the current model while a
failed individual Task and its evidence remain recorded. Raw native Task marking,
execution certificates, contract truth, and organizational completion must not be
treated as the same value.

## Independent release and barrier checks

`summarize.py` reread the producer's checksummed binary ledger and independently
reconciled all 28 release receipts with the persisted presented native cuts. It
checked token, run, session, generation, revision, sequence, policy hash, execution
scope, command ID, accepted USE state hash, native result hash and combined
inventory result hash. The native result hashes were also validated by the Java
native result decoder during presentation.

All 26 automatic receipts corresponded to exclusively PASS applicable checks.
Both manual receipts corresponded to concrete failed applicable checks. Neither
run acknowledged a missing/unknown/error check. Each presented barrier had closed
admission, a frozen clock, no active admitted work and zero pending work.

Closing each harness observer created a separate `observer-disconnected` hold in
the source ledger. No release receipt exists for that shutdown cut. Only the
harness's explicit owned-runtime Stop then terminated its own producer. Native
CArtAgO cleanup emits `CHECKPOINT_RUN_STOPPED` cancellation traces after Stop;
these are retained in the local stderr logs and are not domain-verification
failures. The user's existing USE process was left alone.

Five isolated component probes were also rerun on the current pinned runtime:

- Real Jason cycles, in-flight and newly admitted CArtAgO operations, external
  sessions, an internal operation timer and a real NPL obligation deadline.
- Source admission rejection of unsupported clocks/asynchronous producers.
- Notification drain and timer suspension/resumption.
- Admitted causal queue drain.
- Stop cancellation without converting Stop into release.

All passed. The positive probe held for 2,500 wall-clock milliseconds and proved
`actualNplDeadlineFrozen=true`, `actualJasonWaitFrozen=true` and
`newOperationsAndSessionsHeld=true`. This is operation/clock evidence in addition
to Jason cycle evidence. It is not suspension of arbitrary unmanaged/distributed
Java code outside the admitted local profile.

## Coverage remains explicit

The final native runtime coverage in both runs is still `INCOMPLETE`; it was not
rewritten to COMPLETE. Global Moise/NPL observation sources retain partial-history
quality. The generated supporting `OrgContext::CslV4RoleCompatibility` invariant
remains `SKIPPED / POPULATION_UNPROVEN`, even though its observed body rows pass.
That does not establish universal population coverage and is not a False result.
It is listed as supporting, not as one of the four required authored invariants
in the admitted requirement inventory.

At the final cuts, the required BidEvidence, SchemeOrder, AuctioneerPopulation
and ParticipantPopulation invariants all pass. Both organizational roots have
native FULFILLED aggregates with `KNOWN_DERIVED_HISTORY` execution evidence; their
raw input-quality limitations remain visible in the receipt. Admission of the
settled scopes uses those existing history certificates, not a blanket conversion
of UNKNOWN into PASS. Supporting/unassigned/failed occurrences must not be inferred
to have all completed from the roots alone.

`WithinDeadline` remains temporarily inactive in the current Auction bundle by
the user's explicit prior choice. It was absent from both runs' native admission
inventories. This audit proves clock/barrier behavior independently; it does not
claim verification of the disabled deadline assertion. ParticipantPopulation
remains active. No assertion was weakened during this audit.

## Focused tests, package and delivery

82 focused tests passed with zero failures, errors or skips. They cover the
protocol/authentication, early/unknown boundaries, shared-intention draining,
operation ownership, task yield, exact presentation before release, lost-reply
idempotence, stale review rejection, native failure persistence, mapping/input
hash guards, scope isolation, source Task contracts, repeated review-button use,
All-execution historical violations and current iStar instance presentation.
`focused-build.log` records BUILD SUCCESS. No full clean verify or unrelated live
case was run. A separate final test-compile also passed for the audit harness.
The first compile-only invocation failed on PowerShell argument quoting before
compilation; it is retained locally and excluded from acceptance.

`check-runtime.py` loaded installed runtime/native classes and checked their actual
CodeSource paths and JAR hashes. The installed production JAR entries exactly match
the freshly built production entries. No package overwrite was needed because
production code did not change. Both source and installed authored-hash checks
passed without writes.

Installed JaCaMo: `bridge/runtime/jacamo-1.3.1-checkpoint-SNAPSHOT.jar`.

SHA-256: `aca1163c73b854123526f52c22c0ea8c6f9bc772376c5fb73d20a1aa4c6af155`.

Launcher:
`D:/_CODE_BANK/Project_/08_Thesis/use/use-assembly/target/new/bin/start_use.bat`.

The existing incremental-distribution/legacy clean-release limitation remains
unchanged; no clean release ZIP is claimed. No Computer Use or fresh manual
desktop acceptance was performed. Swing unit evidence is offscreen evidence.

Compact reconciled evidence is in `summary.json` and `runtime-receipt.json`.
Compressed per-cut snapshots, hold details, final native results and producer logs
remain local at the paths/digests recorded there. Transport credentials and large
raw spool files are not copied into the committed report. Existing logs, replays,
layouts and Draw.io backups are preserved.

To reproduce from the plugin directory after focused test compilation, run
`python verification/accuracy-audit-20261105/run-natural.py`, then from the checkout
root run the corresponding `summarize.py` and `check-runtime.py`. Review simulation
only continues concrete native failures and deliberately stops on required
unavailable evidence. It does not prescribe how many failures must occur.
