# Task 1 Report — Phase 13 inherited hardening slice

## Status

`DONE_WITH_CONCERNS`

Implementation commit: `d4d7c51f7098d86b8dbe736eb2aeb83a464b08f3` (`fix: harden Phase 13 quality gates`).

The requested P13.2–P13.5 inherited slice is implemented and the final module suite is green. The explicit concern is that no clean-environment run was performed; the compatibility manifest records `cleanEnvironmentStatus` as `not-run`, and the corresponding checklist item remains unchecked.

## Changed files

- `use-plugin/compatibility.json` — records the pinned USE/JaCaMo component versions, verified Windows/Maven/JVM host, real runtime scope, and honest clean-environment status.
- `use-plugin/docs/agent/task.md` — checks only the Phase 13 items proved by fresh tests/evidence in this task.
- `use-plugin/src/main/java/org/tzi/use/plugins/jacamo/DefaultJaCaMoFacade.java` — validates export formats, rejects linked/reparse-point destinations, writes reports atomically with actionable cleanup failures, and exposes import/generation/full-check/runtime/memory metrics.
- `use-plugin/src/main/java/org/tzi/use/plugins/jacamo/JaCaMoFacade.java` — adds the immutable `PerformanceMetrics` facade contract.
- `use-plugin/src/main/java/org/tzi/use/plugins/jacamo/ocl/OclProfileLoader.java` — applies real-path containment after lexical containment, blocking symlink/junction escapes.
- `use-plugin/src/main/java/org/tzi/use/plugins/jacamo/runtime/RuntimeEventObserver.java` — adds an event-receipt lifecycle hook.
- `use-plugin/src/main/java/org/tzi/use/plugins/jacamo/runtime/RuntimeMirrorService.java` — marks live/buffered events received before queue submission so latency includes steady-state queueing.
- `use-plugin/src/main/java/org/tzi/use/plugins/jacamo/verification/RuntimeVerificationEngine.java` — measures state-change latency from receipt through USE mutation and verification result; accepts a test clock for deterministic coverage.
- `use-plugin/src/test/java/org/tzi/use/plugins/jacamo/CompatibilityManifestTest.java` — derives manifest expectations from both Maven models and the current Maven JVM/OS rather than duplicating dependency pins.
- `use-plugin/src/test/java/org/tzi/use/plugins/jacamo/DefaultJaCaMoFacadeTest.java` — covers JSON/Markdown exports, unsupported extensions, linked destinations, cleanup-error preservation, and performance metrics without arbitrary wall-clock ceilings.
- `use-plugin/src/test/java/org/tzi/use/plugins/jacamo/PathLinkSupport.java` — creates a real symbolic directory link or an unprivileged Windows junction and fails rather than skipping when neither is possible.
- `use-plugin/src/test/java/org/tzi/use/plugins/jacamo/constraint/ConstraintOclTest.java` — makes the OCL linked-path escape regression effective on Windows with zero skips.
- `use-plugin/src/test/java/org/tzi/use/plugins/jacamo/runtime/RuntimeFoundationTest.java` — proves a failed USE mutation moves the mirror to `ERROR`, increments failure metrics, and preserves prior state.
- `use-plugin/src/test/java/org/tzi/use/plugins/jacamo/verification/RuntimeVerificationEngineTest.java` — proves actual latency is emitted and deterministically verifies that receipt, mutation, and verification are all covered.
- `.superpowers/sdd/task/task-1-report.md` — this report.

## Decisions

1. Export remains user-selected rather than constrained to a repository root, so the relevant escape hazard is following an existing symlink/junction/reparse point. Every existing destination component is inspected with `NOFOLLOW_LINKS`; both symbolic links and Windows junctions (`isOther`) are rejected.
2. Report content is written to a same-directory temporary file and moved atomically where supported, with a normal replace move only when atomic moves are unsupported. A failed write/move attempts cleanup; cleanup failure is included in the thrown diagnostic and attached as a suppressed exception instead of being ignored.
3. OCL profiles retain their selected lexical origin for traceability, but both the allowed root and selected file are resolved to real paths before reading. The real file must remain under the real allowed root.
4. Runtime latency starts at connector receipt for steady-state events and just before buffered events enter the queue, then ends only after USE mutation and verification reporting. This covers event-to-result work rather than only OCL evaluation.
5. Performance regression coverage asserts measurement invariants (positive phase timings, heap use positive and within JVM maximum) and records raw evidence. It deliberately avoids machine-dependent upper wall-clock assertions.
6. Compatibility evidence compares actual Maven dependency declarations (`provided` Jason/CArtAgO/Moise versions), root USE/Java pins, and the Maven test JVM/OS to the manifest. Maven CLI and PATH Java evidence was also captured separately.
7. No performance optimization was made because the measured Auction baseline did not establish a bottleneck. No clean-environment claim was made.

## RED/GREEN evidence

### Inherited baseline

- Command: `mvn -pl use-plugin '-Dtest=DefaultJaCaMoFacadeTest,ConstraintOclTest,RuntimeFoundationTest,CompatibilityManifestTest' test`
- Result: `20` tests passed, `1` skipped. The skipped test was the Windows OCL symbolic-link escape regression, confirming the inherited gate was not effective on this host.

### Export cleanup and deterministic latency contract — RED

- Command: `mvn -pl use-plugin '-Dtest=DefaultJaCaMoFacadeTest,ConstraintOclTest,RuntimeVerificationEngineTest' test`
- Result: build failed in test compilation because `cleanupTemporaryReport(Path, IOException)` and the clock-aware `RuntimeVerificationEngine` constructor did not exist. These were the required observable seams for cleanup-failure preservation and deterministic latency semantics.

### Real linked-path behavior — RED

- Command: `mvn -pl use-plugin '-Dtest=DefaultJaCaMoFacadeTest,ConstraintOclTest,RuntimeVerificationEngineTest' test`
- Result: `21` tests ran with `3` failures and `0` skips. The meaningful failures were:
  - `caseLoaderRejectsSymlinkThatResolvesOutsideProject`: no exception; lexical containment followed the Windows junction outside the project.
  - `reportExportRejectsLinkedDirectoryDestinations`: no exception; export followed the Windows junction and wrote outside the selected path.
  - The Markdown test initially expected a nonexistent `## Results` heading; inspection of the real exporter showed the stable results contract is the `| Constraint | Outcome | Context |` table, so the test expectation was corrected without changing production behavior.

### Linked paths, cleanup, and latency — GREEN

- Command: `mvn -pl use-plugin '-Dtest=DefaultJaCaMoFacadeTest,ConstraintOclTest,RuntimeVerificationEngineTest' test`
- Result: `21/21` passed, `0` failures, `0` errors, `0` skips.

### Connector-receipt latency — RED

- Command: `mvn -pl use-plugin '-Dtest=RuntimeVerificationEngineTest' test`
- Result: test compilation failed because `eventReceived(RuntimeEvent)` did not exist. This proved latency could not yet start at connector receipt.

### Connector-receipt latency — GREEN

- Command: `mvn -pl use-plugin '-Dtest=RuntimeVerificationEngineTest' test`
- Result: `5/5` passed with `0` skips. The deterministic clock test measured exactly `100 ns` across receipt, queue/mutation setup, mutation, and result generation.

### Final focused gate

- Command: `mvn -pl use-plugin '-Dtest=DefaultJaCaMoFacadeTest,ConstraintOclTest,RuntimeFoundationTest,RuntimeVerificationEngineTest,CompatibilityManifestTest' test`
- Result: `28/28` passed, `0` failures, `0` errors, `0` skips.
- Recorded sample: import `84,073,000 ns`; generation `18,114,200 ns`; full check `356,000 ns`; heap used `37,451,016 B` of `4,219,469,824 B`; runtime event-to-result `2,535,700 ns`.

## Final commands and results

- `mvn -pl use-plugin test` — PASS on final production code: `84/84`, `0` failures, `0` errors, `0` skips; total Maven time `19.084 s`.
  - Final recorded sample: import `84,357,700 ns`; generation `18,544,100 ns`; full check `298,200 ns`; heap used `53,608,472 B` of `4,219,469,824 B`; runtime event-to-result `320,200 ns`.
- `mvn -pl use-plugin dependency:tree '-Dincludes=io.github.jason-lang:jason-interpreter,org.jacamo:cartago,org.jacamo:moise'` — PASS; resolves Jason `3.3.0`, CArtAgO `3.1`, and Moise `1.1`, all with `provided` scope.
- `mvn -version` — Maven `3.9.9`, Java `21.0.5` (Oracle), Windows 11 `10.0` amd64.
- `java -version` — PATH Java is Oracle JRE `1.8.0_501`; it is not used by Maven and is recorded separately in the manifest.
- `git diff --check` and `git diff --staged --check` — PASS before the implementation commit.

## Checklist updates

Checked in Phase 13 because this task has fresh evidence:

- P13.2: `USE mutation error`.
- P13.3: `path traversal tests`, `no arbitrary code execution during static import`, `safe classpath handling`, `safe export paths`.
- P13.4: `baseline import timing`, `baseline full-check timing`, `runtime latency metrics`, `regression benchmark`.
- P13.5: `pinned USE version`, `pinned JaCaMo version`, `compatibility matrix`.

Deliberately left unchecked:

- P13.4 `optimize only measured bottlenecks` (no bottleneck/optimization task was justified by the measurements).
- P13.5 `clean environment test` (not run).
- Other Phase 13 items outside this inherited slice and the phase acceptance checkbox.

## Self-review

- Re-read the Task 1 brief and all four named authority sources.
- Inspected the inherited unstaged diff before editing and preserved all valid changes.
- Confirmed the Windows path test creates a real junction when symbolic-link creation is unavailable and never converts inability to test into a skip.
- Confirmed JSON and Markdown use their actual exporters, unsupported extensions create no output, linked output creates nothing outside the selected path, and cleanup failure remains actionable.
- Confirmed the mutation regression checks both `ERROR`/failed metrics and the pre-existing `open=true` state after the failed mutation.
- Confirmed runtime latency includes event receipt and mutation with a deterministic clock test; no arbitrary timing ceiling remains.
- Confirmed manifest dependency versions are derived from the plugin POM and host fields from the Maven JVM; clean environment is explicitly `not-run`.
- Inspected the staged diff and passed both diff whitespace checks before committing.
- Did not reset, revert, clean, discard, push, merge, or modify Core Ecore/mapping artifacts.

## Concerns

1. No clean-environment build/plugin-load test was run, so P13.5 is intentionally incomplete at phase level.
2. Compilation reports a pre-existing deprecated-API warning in `DirectUseBackend`; this task did not alter that file or API usage.
3. Tests report the pre-existing absence of an SLF4J provider and use the NOP logger.
4. PATH `java` is Java 8 while Maven correctly uses `JAVA_HOME` JDK 21; direct Java commands must use the JDK 21 binary or the environment must be corrected.
5. The task was committed locally only; it was not pushed or merged as required.

---

## Fix round 1 — preliminary root-cause analysis and hypotheses

### Runtime event/timestamp/queue/lifecycle data flow

1. A connector calls the subscription callback in `RuntimeMirrorService.synchronizeSnapshot`.
2. For a live queue, that callback calls `RuntimeVerificationEngine.eventReceived` synchronously and then `OrderedRuntimeEventQueue.submit`; during initial synchronization it buffers first and currently calls `eventReceived` only later, immediately before submission.
3. The queue worker calls `beforeMutation`, applies the USE mutation, and calls `afterMutation`; the verification engine holds its intrinsic monitor throughout both report-producing callbacks and the OCL work they invoke.
4. The current `eventReceived` method is synchronized on that same monitor. Therefore a connector callback arriving while `afterMutation` runs blocks before enqueue and records a timestamp only after the ongoing check releases the monitor. The metric omits exactly that contention/queueing delay.
5. The timestamp map only stores state-changing event IDs. `OP_ENTER` starts inside `beforeMutation`; `OP_EXIT`, `OP_FAIL`, and mutation diagnostics fall back to the start of `afterMutation`. This is not one receipt-to-result contract.
6. A timestamp is inserted before `submit`, but ordering, capacity, and shutdown rejection paths throw without notifying the observer. Disconnect/resync/failed synchronization also provide no timing-state cleanup. These paths can retain entries indefinitely.

### Root causes and testable hypotheses

- **RC1 — shared monitor coupling:** receipt tracking was placed in a synchronized verification coordinator instead of a lock-independent timing tracker. Hypothesis: a controlled blocked verification will also block a second `eventReceived` call; moving receipt storage to a concurrent map and keeping receipt/rejection/completion callbacks non-blocking will make receipt independent.
- **RC2 — incomplete lifecycle protocol:** `RuntimeEventObserver` has receipt and mutation callbacks but no accepted-event completion, submission-rejection, or stream-close notifications. Hypothesis: adding explicit callbacks and invoking them in `finally`/rejection/lifecycle paths will prevent stale timestamps without weakening queue rejection behavior.
- **RC3 — phase-specific start points:** only `STATE_CHANGES` use receipt timestamps, and buffered events are timestamped at drain/submission rather than connector arrival. Hypothesis: record every event at the subscription boundary, use the same receipt lookup for every report-producing branch, and remove the entry only after the whole event completes or is rejected.
- **RC4 — manifest model conflation:** compatibility requirements are stored alongside one dated verification host, while the test requires every future compatible run to reproduce that host and requires clean-environment status to remain `not-run`. Hypothesis: separate stable `requirements` from appendable `evidence` records and validate structure/pins without comparing evidence to the current test machine or a fixed clean status.

---

## Fix round 1 — completed lifecycle, latency, and portability hardening

### Status

`DONE_WITH_CONCERNS`

Implementation commit: `7415f2544b9537e2bf3fc12795974bcf5b4029ab` (`fix: harden Phase 13 runtime lifecycle`).

### Root causes and fixes

1. Receipt timestamps contended on the verification monitor, so a connector callback could wait behind a full OCL check. `RuntimeVerificationEngine` now stores all event-receipt timestamps in a `ConcurrentHashMap`; receipt has no verification-monitor acquisition.
2. Queue rejection, stream close, and accepted-event completion were not a complete observer protocol. The observer now has explicit rejection, completion, and close hooks. The mirror invokes them for ordering/backpressure/shutdown rejection, buffered events covered by a snapshot, every worker completion (including mutation failure), and stream termination.
3. A subscription callback could arrive after a failed initial sync or close and be retained in the initial buffer. The subscription wrapper marks the stream closed under the existing callback gate before delegating close; late callbacks receive an observer rejection, so receipt state is removed. The gate still serializes buffered-to-live submission and preserves queue ordering.
4. Operation correlations from a closed stream could be completed by a later stream. `eventStreamClosed` now clears both receipt timings and active operation correlations.
5. Only state-changing events originally had receipt starts. All report-producing normal, operation, and buffered paths now use the same connector-boundary receipt start.
6. `compatibility.json` conflated stable requirements with one dated host snapshot. It now separates stable requirements from appendable evidence records; the test validates pins and evidence shape without requiring a historical host or clean-environment result.
7. Link diagnostics named the path but did not tell users how to recover. Export and OCL-profile escape diagnostics now identify the rejected path/root and instruct the user to select a non-linked destination or an in-root profile. The public performance metric units/semantics and observer lifecycle callbacks are documented.

### Files changed

- `use-plugin/compatibility.json`
- `use-plugin/src/main/java/org/tzi/use/plugins/jacamo/DefaultJaCaMoFacade.java`
- `use-plugin/src/main/java/org/tzi/use/plugins/jacamo/JaCaMoFacade.java`
- `use-plugin/src/main/java/org/tzi/use/plugins/jacamo/ocl/OclProfileLoader.java`
- `use-plugin/src/main/java/org/tzi/use/plugins/jacamo/runtime/RuntimeEventObserver.java`
- `use-plugin/src/main/java/org/tzi/use/plugins/jacamo/runtime/RuntimeMirrorService.java`
- `use-plugin/src/main/java/org/tzi/use/plugins/jacamo/verification/RuntimeVerificationEngine.java`
- `use-plugin/src/test/java/org/tzi/use/plugins/jacamo/CompatibilityManifestTest.java`
- `use-plugin/src/test/java/org/tzi/use/plugins/jacamo/DefaultJaCaMoFacadeTest.java`
- `use-plugin/src/test/java/org/tzi/use/plugins/jacamo/constraint/ConstraintOclTest.java`
- `use-plugin/src/test/java/org/tzi/use/plugins/jacamo/runtime/RuntimeFoundationTest.java`
- `use-plugin/src/test/java/org/tzi/use/plugins/jacamo/verification/RuntimeVerificationEngineTest.java`
- `.superpowers/sdd/task/task-1-report.md`

### RED evidence

- Inherited partial-state focused run: `mvn -pl use-plugin '-Dtest=RuntimeFoundationTest,RuntimeVerificationEngineTest,CompatibilityManifestTest' test` failed with `1/17` failure: `CompatibilityManifestTest` expected required USE version `7.5.0` below `requirements`, but the manifest still used the old top-level host-specific layout.
- Reconstructable inherited evidence from the preliminary report: the original focused baseline had an ineffective Windows OCL linked-path skip; the first linked-path regression run then had `3/21` failures (OCL junction escape, report-export junction escape, and one corrected Markdown assertion); the original receipt hook test failed to compile until the hook existed.
- New lifecycle RED: `mvn -pl use-plugin '-Dtest=RuntimeFoundationTest,RuntimeVerificationEngineTest' test` failed `2/18`. A callback after failed initial synchronization produced no rejection, and an exit on the next stream incorrectly completed `POST-AUCTION-OPEN` instead of reporting `RUNTIME_OPERATION_EXIT_UNMATCHED`.
- New actionable-diagnostic RED: `mvn -pl use-plugin '-Dtest=DefaultJaCaMoFacadeTest,ConstraintOclTest' test` failed `2/16` because linked-destination and linked-profile errors lacked the tested remediation text.

### GREEN evidence

- `mvn -pl use-plugin '-Dtest=CompatibilityManifestTest' test` — PASS, `1/1`.
- `mvn -pl use-plugin '-Dtest=RuntimeFoundationTest,RuntimeVerificationEngineTest,CompatibilityManifestTest' test` — PASS, `19/19`; covers controlled concurrent receipt, normal/operation/buffered latency, ordering and shutdown rejection, failed-sync late callbacks, and cross-stream operation cleanup.
- `mvn -pl use-plugin '-Dtest=DefaultJaCaMoFacadeTest,ConstraintOclTest' test` — PASS, `16/16`; includes real Windows symlink/junction escape paths, export safety, and actionable messages.
- `mvn -pl use-plugin test` — PASS, `91/91`, `0` failures, `0` errors, `0` skips. Recorded samples: import `93,546,600 ns`; generation `22,905,100 ns`; full check `882,900 ns`; heap `50,024,152 B` of `4,219,469,824 B`; runtime event-to-result `316,400 ns`.
- `git diff --check` and `git diff --cached --check` — PASS before the implementation commit.

### Self-review

- Preserved and audited all six inherited partial files; no reset, revert, clean, discard, push, merge, or Core Ecore/mapping change was made.
- Receipt is deliberately before the callback gate and verifier monitor; queue submission remains under the callback gate, so the change does not introduce callback-order races.
- Every received event now has exactly one terminal cleanup path: rejection, worker completion, or stream close. Stream close also clears operation correlations.
- Controlled concurrency proves receipt continues while targeted verification is held; deterministic clocks prove receipt-to-result timing for normal, operation, rejected/reused, stream-closed/reused, and buffered paths.
- The portability test derives pinned dependencies from the POM but validates only evidence structure, not the current host or a historical clean-environment value.
- The Phase 13 checklist was already updated only for independently proven items in `d4d7c51f`; this round adds coverage/correctness for those existing checked claims and does not claim the still-unchecked clean-environment or measured-optimization items.

### Residual concerns

1. No clean-environment build/plugin-load test was run; evidence remains explicitly `not-run` and that Phase 13 checklist item stays unchecked.
2. Export validation remains subject to the documented pathname TOCTOU race if an adversary replaces a validated directory component after validation; handle-relative/platform-specific authorization is out of scope for this slice.
3. Maven continues to report the pre-existing deprecated API warning in `DirectUseBackend`; test logs also retain the pre-existing SLF4J NOP-provider warning. Neither file/configuration was changed here.
4. The implementation is committed locally only; it was not pushed or merged.

### Export TOCTOU scope/risk note

The current same-directory temporary file plus atomic replacement prevents partially written final reports, and the `NOFOLLOW_LINKS`/reparse-point walk rejects links present during validation. It **cannot guarantee** safety against an adversary concurrently replacing a validated directory component between the walk, temporary-file creation, and move. Pathname-based Java NIO calls re-resolve directory components at each operation; eliminating that race requires a handle-relative/descriptor-based design (for example, a trustworthy `SecureDirectoryStream` implementation or platform-specific APIs) and a stronger authorization boundary for export roots. The task/spec currently requires safe user-selected export paths and link/path escape regressions, not adversarial filesystem isolation. This round therefore records the concurrent-replacement race as a residual risk and does not broaden into a platform-specific export subsystem redesign.
