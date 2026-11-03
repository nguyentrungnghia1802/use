# Checkpoint stability — 2026-11-01

This follow-up addresses the four issues reported with the normal desktop
workflow. Earlier receipts under `verified-checkpoints` proved bounded
handshakes, not full execution/navigation stability. They are retained as history.

## Findings and changes

1. **Initialization holds and mixed/blank states.** The old policy used every
   observation requirement as a milestone, including framework `makeArtifact`.
   The user's current run stopped with unproven monitor populations while the
   other execution was still being constructed. Policy `CHECKPOINT_POLICY/1.1`
   now explicitly selects admitted native Task terminal source steps with bound
   artifacts. Source-domain failures still hold; operation completion is the
   fallback when no admitted Task terminal covers that domain source class.
   Framework observation providers remain evidence, not milestones. No native
   assertion, source case or verdict was changed. Unknown/error at an applicable
   selected checkpoint still retains the hold.

   Each admitted context has its own presentation timeline and navigation cursor.
   Only its qualified bound Entity cuts enter that timeline; changes of another
   execution cannot add fictitious states or replace it with an unbound cut.
   An unavailable current observation is not certified by borrowing an older
   state: retained states remain historical selections and the active checkpoint
   hold remains separately visible. These are projections of one MSystem, not
   independent native runtimes or schedulers. Shared physical agents still use
   the global source barrier.

2. **Repeated Acknowledge & Continue.** Clearing the review-pending flag now
   precedes publication of the final receipt view. Each new failed token gets
   its own review state. Regression tests exercise three distinct tokens, and
   the live desktop test clicks the same real button for two separate failed
   checkpoints. Source/result identity, idempotency and retained failure truth
   are unchanged.

3. **Milestone count shrinks after switching.** Switching execution used the
   compact BusinessTimeline path instead of the source-checkpoint timeline mode;
   programmatic diagram scope synchronization could trigger the selector again.
   The same mode, per-context history and selected cursor now survive execution
   and tab changes. Programmatic synchronization does not pretend to be a user
   scope change. A late preparation worker also respects the user's current
   execution choice. The focused regression keeps 13 states and a selected
   historical state across repeated scope changes, including an unrelated cut
   where that execution is absent.

4. **Execution 2 ends INCOMPLETE.** In the user's prior complete recording
   (`run-12740185204100819032`), both exact native roots were FULFILLED with
   `KNOWN_DERIVED_HISTORY` and their native execution constraints had no non-PASS
   result. The UI retained a source cut before the final native completion.
   Settled native completions now enter the same presentation history after
   source release. A separate false missing-evidence diagnostic came from
   consulting only the 512-record RAM tail even when the prefix existed on disk;
   that prefix is now recovered by a streaming hash-chain-checked read off EDT.
   Real corruption, missing persistence and required unknowns still hold.

Read-only thread dumps and extracted diagnostics from the user's actual runs
are `user-use-threads.txt`, `user-producer-threads.txt` and
`user-history-diagnostics.json`. Their original journals and owned-runtime files
remain in `use-assembly/target/new-before-stability-20261101`.

## Executable evidence

- [Final package build](final-package.log): BUILD SUCCESS, **35 focused tests**,
  zero failures/errors/skips (2 contract, 1 adapter, 32 plugin). These include
  policy selection, required native truth, persisted-prefix integrity/corruption,
  repeated review, independent histories/cursors, 13-state navigation, diagrams
  and controls. No full clean verify was run.
- [Full desktop Auction run](focused-live-2.log) and
  [live receipt](live-1793507424631/receipt.json): both exact native roots
  FULFILLED and both UI executions **COMPLETE_OBSERVED**; **8 automatic releases**,
  **2 desktop-reviewed releases**, **10 displayed cuts**. Every release follows
  DISPLAYED for the same token/result hash in the exported audit. The natural
  domain run required no manual review; only the later intentional invalid public
  source operations did.
- [Navigation receipt](live-1793507424631/navigation.json): both execution histories
  had 8 states at natural completion, distinct Entity state/identity and their
  own final values. Repeated CSL/iStar/results-tab switches retained the exact
  selected cut/count/scope. The 13-state case is an independent focused regression;
  no Auction state count is hard-coded in production.
- [Completed native snapshot](live-1793507424631/completed-native.json) preserves
  original native Goal/Task/OCL results. The two late public `stop()` inputs fail
  the unchanged running precondition after completion. Their source receipts,
  two exact review requests and all audit records remain in the live directory.
  Test-only `AuctionEvidenceInputAgent` is not included in production JARs.
- The two real **Acknowledge & Continue** clicks were performed through
  [computer-use skill](C:/Users/NTNghia/.codex/plugins/cache/openai-bundled/computer-use/26.1027.30824/skills/computer-use/SKILL.md).
  The second checkpoint re-enabled the button; both cuts retained their original
  failure diagnostics. This is actual automated desktop interaction, not human
  acceptance sign-off.
- Original external Auction source hashes unchanged; one authoritative MSystem
  throughout. Measured Swing heartbeat p95 **120 ms**, max **746 ms**, over this
  full run including diagram/tab navigation. This is bounded measured evidence,
  not a claim of universally zero UI latency.

The live run preceded the final package's last guards for concurrent scope choice,
matching a settled state image to its exact native version, and safe persisted
paths. The final focused suite checks the packaged source; the full live result
is not represented as an installed-GUI run. Early unsuccessful harness attempts
remain in `focused-initial.log`, `focused-live.log` and their live directories.

`COMPLETE_OBSERVED` is scoped to supported observed requirements. Overall source
coverage may remain `INCOMPLETE` under the bounded observation profile. It is
reported separately and was not upgraded or used to force a native PASS.

## Installed artifact

[Package identity receipt](stability-final-package-receipt.json),
[actual code sources](stability-packaged-code-sources.log) and
[installed plugin loader](stability-installed-loader-smoke.log) pass. The current
built bridge/contract/plugin/GUI JARs match installed bytes. Consumer isolation
and the one-MSystem class identity pass.

Actual JaCaMo loaded:

```text
D:\_CODE_BANK\Project_\08_Thesis\use\use-assembly\target\new\bridge\runtime\jacamo-1.3.1-checkpoint-SNAPSHOT.jar
SHA-256 f5a31568fbc2487322d985e00ab6cecac987f2edc296bf14cd155c56c217275e
```

The main JaCaMo/kernel JARs are unchanged from the proven source boundary;
the packaged adapter/contract/plugin implement the new selector and UI fixes.
The previous USE process had closed with its producer held. That exact detached
run was terminated using its normal stop.flag before replacing the installation;
no thread suspension, kill-as-pause, reset or clean was used. The entire previous
installation, work directory, histories and logs were moved intact to
`use-assembly/target/new-before-stability-20261101`.

Run:

```text
D:\_CODE_BANK\Project_\08_Thesis\use\use-assembly\target\new\bin\start_use.bat
```

Use **Prepare + Import → Start Runtime** in the single Verified checkpoints
workflow. [Final source/JAR/evidence freeze](final-freeze.json) binds this report
and the evidence to the working source. Both repositories remain on `main`;
no commit or push was performed.
