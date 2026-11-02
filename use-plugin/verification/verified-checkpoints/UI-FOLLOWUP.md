# Main verification workflow — UI retest

Current full-run stability follow-up (2026-11-01):
[checkpoint-stability/REPORT.md](../checkpoint-stability/REPORT.md). The bounded
run below did not exercise full completion, repeated desktop review or independent
13-state navigation; do not substitute it for the later evidence.

2026-10-31. This follow-up supersedes the selectable Passive/Verified UI described
in the initial [support report](FINAL-SUPPORT.md). The supported source boundary
and pinned source hooks remain those established in [S01](S01-supported-boundary.md).

## Result

The main authored workbench has one owned execution path: **Prepare + Import →
Start Runtime**, always using verified checkpoints. There is no passive selector.
Jason cycle-only controls are in Advanced and keep their original meaning.

For each policy-selected source checkpoint, JaCaMo retains its admission/clock
barrier while USE accepts the exact source prefix and evaluates the existing
native MSystem. The interactive owner must render that exact result before a
release decision is sent. A presentation failure holds execution and retains the
original native result. Missing/unknown evidence is not converted to PASS.

Live results receive immutable source checkpoint cuts directly. They do not walk
the disk audit prefix and animate old states over the current cut. Earlier source
checkpoints remain navigable, and the complete native audit stays in its existing
journal/replay. Whole-MAS admission diagnostics are labelled separately from the
native verdict of the selected execution; failed admission does not invent a
failure in another execution. Acknowledgement remains bound to the active token,
cut and original result hashes.

## Current evidence

Final build/test log: [UI-final-package-retest.log](UI-final-package-retest.log).
**23 tests passed, zero failures/errors/skips**: service/presentation ordering 5,
execution scope 7, history/quota 8, main controls 2, real desktop Auction 1.
The service tests prove no APPROVE before presentation completes, presentation
failure retains native truth, and recovery does not silently auto-approve a held
error. The main Prepare test proves verification is enabled without a mode selector.

The real desktop run is
[`ui-auction-1793458813137`](ui-auction-1793458813137/ui-receipt.json):

- **5 displayed source cuts**, **3 automatic release receipts**, **2 reviewed
  release receipts**. Every release is preceded by DISPLAYED for the same token
  and result hash in the exported hash-checked audit.
- A genuine native failed checkpoint remained held. Computer Use inspected the
  results table, CSL/AOL and iStar/ISCN tabs, then clicked the actual
  **Acknowledge & Continue** button. The button disabled while awaiting the receipt;
  native failure evidence was retained. This is an actual desktop interaction,
  not a Swing `doClick` fixture or a human acceptance sign-off. The receipt field
  `manualDesktopReview=true` denotes this external desktop button exercise.
- Swing heartbeat at a 25 ms sampling interval: **p95 66 ms; maximum 177 ms**,
  including the tab interactions. These are measurements of this bounded Auction
  run, not a universal latency guarantee.
- Original external Auction source hashes unchanged; the same authoritative
  MSystem remained in use. Test-only public operation inputs produced the failure
  after an actual native-qualified cut. No model, semantic assertion or verdict
  was edited or injected to obtain these results.

This focused desktop run stops after proving the handshake/review sequence; it
does not claim full domain completion. Earlier full live/lifecycle/replay evidence
remains linked by the initial support report. Both test JVMs exited through their
normal cleanup and explicit Stop path.

## Packaging and limits

[Loaded runtime code sources](UI-final-packaged-code-sources.log),
[installed plugin loader](UI-final-installed-loader-smoke.log) and
[package receipt](UI-final-final-package-receipt.json) passed. Current built
contract/adapter/plugin/GUI JAR bytes match the installation. The consumer uses
one MSystem class identity and does not load producer JaCaMo/CArtAgO classes.

Actual JaCaMo code source:

```text
D:\_CODE_BANK\Project_\08_Thesis\use\use-assembly\target\new\bridge\runtime\jacamo-1.3.1-checkpoint-SNAPSHOT.jar
SHA-256 f5a31568fbc2487322d985e00ab6cecac987f2edc296bf14cd155c56c217275e
```

The retained native journal now defaults to **512 MiB**, consistently in the UI
and bridge launcher; explicit smaller quotas are still honored. A prior desktop
attempt filled the 256 MiB quota: native ENGINE_RESULT records alone occupied
212,610,346 bytes. It held on persistence loss instead of approving. That failed
attempt and all earlier interrupted attempts remain available. No native engine
completion was discarded or semantic check weakened to reduce the journal.
The current default accommodates the measured run; quota exhaustion still holds.

The owned, local source-admission profile and unsupported producer/clock/scheduler
restrictions remain unchanged. Source and packaged byte/evidence hashes are in
[UI-source-artifact-evidence-freeze.json](UI-source-artifact-evidence-freeze.json).
Previous runnable installations and all runtime logs/replays were preserved,
including `use-assembly/target/new-before-ui-checkpoint-20261031-2136`.
No full clean verify, unrelated live case, commit or push was performed.

Run the rebuilt installation:

```text
D:\_CODE_BANK\Project_\08_Thesis\use\use-assembly\target\new\bin\start_use.bat
```
