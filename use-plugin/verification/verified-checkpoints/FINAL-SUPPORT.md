# Verified checkpoints — final support and evidence

2026-10-31. Implemented in sibling `use` and `jacamo` on `main`; no commit or push.
S01 was proven before the subsequent implementation gates. Historical failed
attempts and the original unpatched S01 findings remain available.

## Supported guarantee

One owned local, source-admitted MAS uses a shared execution barrier in the pinned
Jason/CArtAgO/NPL dependencies and Moise integration. Admission closes at a policy
selected source completion. Already admitted operations and causal queued work
drain or yield at a supported await boundary; READY requires no active or pending
admitted work. The requested event and actual frozen cut are recorded separately.
The logical source clock then freezes, including artifact waits, Jason `.wait`
and actual NPL deadline scheduling. Publisher, control and durable ingress remain
available. Jason Pause retains its separate cycle-only guarantee.

Strict preflight rejects unsupported remote/custom platforms, schedulers, agent
architectures, dynamic code/cohort mutation, raw timer/thread/clock producers and
unadmitted source dependencies. This is a conservative supported source profile,
not arbitrary Java isolation or distributed suspension. A non-yielding operation
prevents READY and retains the hold. Stop cancels; it does not release a token.

USE verifies the frozen source watermark prefix in its existing authoritative
MSystem and native Goal/Task/OCL engines. Applicable required checks must pass;
future model-dependent PENDING goals remain progress. Native invocation failures
since the previous reviewed/released cut remain applicable even if a later state
looks valid. Original native result and checkpoint inventory/result hashes are
linked separately. No semantic assertion or case source was rewritten to obtain
approval; checkpoint selectors are admitted versioned policy identities.

Authenticated idempotent commands bind the active run, session, generation,
revision, policy, execution scope, token/sequence/epoch and source/native/result
hashes. Missing evidence, errors, source drift, timeout and disconnect latch a hold.
`Acknowledge & Continue` releases only the reviewed cut and preserves its state,
failure, native truth and evidence. Reconnect and monitor restart reconcile that
same token/result. Strict workbench/USE closure retains the producer in a hold;
use explicit **Stop JaCaMo** for termination.

## Executable evidence

| Gate | Evidence and result |
|---|---|
| S01 source boundary | [Supported boundary](S01-supported-boundary.md), [final probes](S01-final-probes.log), `S01-extended-runtime/positive-proof.txt`: real in-flight CArtAgO operation, external sessions, two Jason agents, guard await and actual two-second NPL deadline. A 2.5-second wall-time hold preserves source time/state; exact release resumes. Separate probes cover queued/notification drain, admission rejection, timeout and Stop. |
| S02 exact protocol | [Source tests](S02-final-source-tests.log), [authenticated protocol tests](S02-auth-protocol-tests.log): exact/stale/foreign/duplicate commands, required unknown/error/skipped/future pending, immutable failure review, persistence/source drift and nonce/body tampering. |
| S03–S05 consumer/UI | [Focused service/UI tests](S05-service-ui-tests.log): inventory, required versus optional source completeness, receipt-bound review and offscreen controls; [final live run](S07-final-confirmed.log) reruns the strengthened prefix-inventory and service tests. [Offscreen image](offscreen-controls.png) is not a manual desktop exercise. |
| Final source-evidence loss | [Final package focused tests](S08-final-package-confirmed.log): `VerifiedCheckpointGapTest` calls the real adapter GAP path, verifies the source loss latch rejects a later complete PASS, preserves the original diagnostic, and keeps admission closed. This final defensive branch was added after the live run; the final package contains it. |
| S06 loaded artifacts | [Actual code sources](S06-packaged-code-sources.log), [installed plugin loader](S06-installed-loader-smoke.log), [package receipt](S06-final-package-receipt.json): strict hooks supported, installed runtime paths and SHA-256 verified; current built bridge/contract/plugin/GUI bytes equal installed bytes, one MSystem class identity and producer isolation in the consumer. |
| S07 live MAS | [Successful focused run](S07-final-confirmed.log), [live receipt](live-auction-1793449383488/live-receipt.json): **3 actual automatic release receipts; 11 held cuts, 11 reviews and 11 reviewed release receipts; 6 genuinely native failed cuts**. Original external Auction source hashes remain unchanged. Native images contain both Auction and both AuctionMonitor instances. |
| Lifecycle | [Disconnect/reconnect](live-auction-1793449383488/disconnect-reconnect.json) and [monitor restart](live-auction-1793449383488/monitor-stop-restart.json) retain the exact original failed result hash/token. [Workbench close](live-auction-1793449383488/workbench-close.json) leaves the real PID alive and persists observer-loss HOLD/FROZEN, without RELEASE or STOP. Explicit Stop subsequently completed; that test PID exited. |
| Replay | [Before disconnect](live-auction-1793449383488/replay-before-disconnect/report.json): all 1,211 entries verified, complete for the observed supported projection. [Final replay](live-auction-1793449383488/replay-report.json): all 1,976 entries verified; intentional disconnect remains `REPLAY_PARTIAL_COVERAGE_GAP`. [Interrupted monitor export](live-auction-1793449383488/replay-interrupted-report.json): all 1,220 entries verified with the same honest gap. Offline replay sends no source control. |
| S08 delivery | [Successful package build](S08-final-package-confirmed.log), [source/artifact/evidence freeze](final-source-artifact-evidence-freeze.json). Only relevant tests and the external Auction live case were run; no full clean verify. |

The live failure stimulus is test-only `AuctionEvidenceInputAgent`: two public
source `AuctionArtifact.stop()` operations as an existing performer. The second
operation exercises the original `stop::RunningForStop` precondition. It changes
neither authored models nor native results/state, and is absent from production
JARs. Missing evidence/unknown held cuts remain distinct from native violations.

Live acceptance uses a real producer MAS and the facade/native engine offscreen.
Installed-loader and runtime fingerprint checks exercise the packaged JARs.
**Fresh manual desktop acceptance has not been performed.** The final Main JaCaMo
and patched kernel JARs match those used by the successful live run; the later GAP
adapter guard is covered by its final focused test rather than a repeated live run.

## Run and identify the installation

Launch:

```text
D:\_CODE_BANK\Project_\08_Thesis\use\use-assembly\target\new\bin\start_use.bat
```

Open **Plugins → JaCaMo → use4Goal verification**, select **Verified checkpoints**
before **Prepare + Import**, supply the project/CSL/iStar/mapping/optional OCL,
then start the owned runtime. Review held evidence before **Acknowledge & Continue**.
Protocol identities and diagnostics remain in the audit/history views.

Actual JaCaMo code source:

```text
D:\_CODE_BANK\Project_\08_Thesis\use\use-assembly\target\new\bridge\runtime\jacamo-1.3.1-checkpoint-SNAPSHOT.jar
SHA-256 f5a31568fbc2487322d985e00ab6cecac987f2edc296bf14cd155c56c217275e
```

The installed launcher verifies `bridge/dependency-lock.json` and generates its
own runtime classpath. Rebuild/provenance instructions are in
`jacamo/checkpoint-dependencies/README.md`. The previous runnable tree, including
its work directory, is preserved in
`use-assembly/target/new-before-verified-20261031-193121`; unrelated runtime logs
and replay files were retained. Repository HEADs remain USE `fded6f66` and
JaCaMo `1dc70db`.
