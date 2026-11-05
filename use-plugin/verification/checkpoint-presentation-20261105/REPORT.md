# Checkpoint, execution and All-State presentation audit

## Findings from the user's actual retained run

The installed journal contains 1941 linked entries and
14 distinct source checkpoint request tokens.
Its final native result was frozen unchanged and its original result hash was
verified by RuntimeVerificationResult.fromMap in the regression test:
`f3bed981619a9ee38cfba2e17f47c0321b2d7e452895c42092252235f4412bc7`.

A checkpoint is the whole-MAS hold/verify/release boundary. A row headed
Checkpoint (whole MAS) is a control inventory/aggregate diagnostic, not another
authored OCL predicate. Its False reports an underlying required failure.

The two EXECUTION:<hash> rows are NOT Execution 1 and Execution 2. Both refer to
one failed source invocation of P_bid_t2 by maria in the a2 scope, with separate
capability/refinement-view certificates. The existing ASL contains a random .fail
branch. Both original certificates remain audit evidence; neither is a new OCL
expression or an independently failed business execution.

WithinDeadline has actual native counterexamples:

| Evidence | activatedAt | deadlineAt | endedAt | unfulfilled | Meaning |
| --- | ---: | ---: | ---: | --- | --- |
| First obligation | 7402 | 17402 | 17410 | true | Actual unfulfilled obligation, terminal 8 ms after deadline |
| Second obligation | 7401 | 17402 | 13142 | false | Completed before deadline, but deadline minus activation is 10001 ms; the authored OCL requires exact equality to 10000 |

The second result exposes a strict equality/timestamp precision issue rather
than an expired duty. No tolerance, timestamp repair, OCL rewrite or forced verdict
was applied. The exact Draw.io-authorized assertion remains authoritative. Review
records acknowledgement and cannot erase either original result.

There is also an existing scope-ownership gap: AuctionObligation objects are not
in the per-execution owner sets. Scoped WithinDeadline results are therefore
Unknown while the global native aggregate has definite counterexamples. This
change does not invent owners or copy the global False into both executions.
It explicitly labels that native aggregate Whole system in All.

## Why All could show 33 States

Both verified checkpoint cuts and settled native completion cuts enter the UI
ledger. The per-execution path applied CheckpointTimeline grouping; All instead
returned every raw ledger cut as a State. Thus the user's reported 33 did not
establish 33 distinct domain states. It included ungrouped audit/control cuts.
The exact historical UI polling sequence is not persisted, so no invented
33-to-N count is claimed for this user's original window.

All now applies the same grouping rule: selected CSL state plus qualified terminal
Goal/Task progress. A repeated control/evidence cut with the same signature shares
the State number and updates its representative to the latest exact cut. Every
original cut/token remains available through Audit details. Genuine progress in
either execution remains distinct even when some CSL values are unchanged; the
correct All count is not hard-coded to either individual execution's count.

## Changes confined to presentation

- Apply State grouping in All; preserve all audit identities and retained failures.
- Main OCL table shows constraint/runtime-binding predicates, excluding checkpoint
  inventory rows, source execution certificates and duplicate per-object OCL body
  proof rows. Full evaluations remain on the immutable Cut and in audit/evidence. A body-only counterexample is kept visible when no aggregate is available.
- Show actual global invariant failure as Whole system when the scoped rows cannot
  localize that counterexample. No failure is silently hidden by scoped Unknown.
- A short note reports checkpoint diagnostics and distinct failed source invocation
  counts, so two certificates of one failed Task are reported as one Task failure.
- Failure-point navigation uses the retained row model, fixing an old access to
  column 4 after the OCL table had been reduced to three columns.

No scheduler/barrier/control protocol, native engine, verdict, OCL assertion,
authored case model, ASL or source state was modified.

## Executed evidence

Final evidence: final-guarded-build.log.

38 focused tests passed, with zero failures/errors/skips; reactor package
BUILD SUCCESS. Tests cover All/per-execution history and navigation, original
failure retention, repeated review, model preparation and actual native result
hash parity. The previous test expecting a checkpoint row in the OCL table was
updated to require it in immutable audit plus the diagnostic note, while keeping
all independent-execution verdict assertions.

The regression with unchanged native fixtures and controlled repeated render cuts
retains all 16 audit cuts and displays 8 grouped States. This is a regression
fixture, not the user's original 33-State polling sequence. The original recorded
native result from the user's run displays 13 real/scoped/global check rows;
its one failed source invocation is counted once and its global OCL failure remains
False. Both original viewpoint certificates and native result hash are retained.

No new live MAS or Computer Use/manual desktop acceptance was performed. This
scope uses the user's existing actual MAS evidence and offscreen Swing tests.
No full clean verify was run.

## Runnable installation

`D:\_CODE_BANK\Project_\08_Thesis\use\use-assembly\target\new\bin\start_use.bat`

The source-built plugin installed at target/new has SHA-256 `b236f0cf905df67396e39216850d7a004bf91cd9b3649c32fb70933afc36f214`.
Packaged CodeSource/JAR hashes passed for the plugin/native engines and unchanged
pinned JaCaMo runtime. The installed batch launcher -nogui/quit exited with code 0.
This uses the existing incremental package process and preserves runtime work,
logs, replay data and layouts. The earlier legacy clean-release ZIP limitation is
unchanged; see the source-checkpoints-20261105 report.
