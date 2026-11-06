# Verification status header

The workbench and detached results window share a right-aligned status badge.
The short `Continue` button is adjacent and visible only when the existing live
checkpoint view is reviewable and no acknowledgement is pending. Its action still
uses the current exact token and result hash. Review does not replace native truth.

| State | Presentation |
| --- | --- |
| Ready / Not started / Stopped | Neutral badge |
| Preparing / Running / Verifying / Continuing | Blue badge |
| Paused due to violation | Red badge; Continue when reviewable |
| Paused due to unavailable verification | Amber badge; Continue when reviewable |
| Completed | Green badge |
| Completed with issues | Amber badge |

## Completion scope

Completion is a presentation summary of terminal, qualified, selected requirements
in **all currently observed bound executions** at the last displayed native cut.
Profiles without an explicit root selection use their required native occurrences.
Missing, unknown or pending required occurrences prevent completion. An explicitly
unassigned occurrence is not treated as a failed assignment. This does not assert
that the MAS process has exited, that future executions cannot be created, or that
every supporting capability was invoked. The badge tooltip states this scope.

Previously applicable non-PASS checks remain reflected as issues. Completion never
converts an unknown or failed check into PASS. A held/new checkpoint takes precedence
over completion. Empty RUNNING status after token release retains the last displayed
summary; Start, Restart, re-import and a changed native owner reset the UI summary.
No history reads or additional native verification are added to the refresh timer.

## Validation

- 13 focused Java tests passed: status presentation (5), repeated exact-token review
  controls (3), checkpoint presentation regression (3), agent inventory (2).
- The actual header component was painted offscreen using installed plugin classes;
  `preview.png` shows UI fixtures, not domain verdicts or a manual desktop session.
- Persisted House Building and Hello World native results were replayed through the
  current presenter/badge without changing native hashes. Both reported Completed
  with issues for their reconstructed inventories; unknown/supporting evidence was
  preserved. This is not a fresh live run of those cases.
- Final installed Prepare + Import of the external Auction passed without starting
  agent reasoning; the owned producer was stopped in cleanup.
- The validated updater checked all 133 contract and 297 semantic-engine classes
  before/after deployment. `package.json` records final installed artifact hashes.

No execution policy, barrier, semantic assertion, domain model or verdict engine was
modified. The runnable launcher is `use-assembly/target/new/bin/start_use.bat`.
