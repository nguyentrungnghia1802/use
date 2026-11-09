# Remove the evidence panel and clarify certificate history

The iStar Verification interface no longer mounts the Evidence details text area or its vertical split. Agent instances and Occurrences & invocations now share the full lower section. Derived diagnostic text remains available through a read-only audit accessor; native cuts, evaluations, certificates and replay/history data are unchanged.

An Occurrence row records the selected element's status at a historical cut. An Invocation row describes an identified execution. Repeated occurrence IDs at different States are status history, not additional executions. Unknown can correctly precede a later Incomplete result.

Source review found a separate presentation defect: a retained successful `TASK` certificate from `ExecutionEvidence.leaf` was labelled Invocation even when its invocation ID was empty, and its displayed Result fell back to the parent Goal's current result. The certificate now appears as Execution evidence when no call ID exists. A success certificate with policy `CERTIFIED_EXECUTION/1.0` and kind TASK/ACHIEVE (or the supported TASK_COMPLETED spelling) displays Complete. This certifies that fact only; it never completes its parent Goal or changes a native result. Unknown occurrence history and failed invocation evidence remain intact.

## Executed evidence

- [x] 19 selected tests passed, with zero failures/errors/skips: Goal/Task/history display, exact failure-point navigation, scoped evidence isolation and population overview.
- [x] A regression proves that a parent Goal remains Unknown while a retained successful child Task certificate displays Complete as Execution evidence with no invented invocation ID. The evidence component/split are absent from the iStar panel.
- [x] Existing tests still verify retained full IDs, failed/successful invocation histories, source diagnostics, selection persistence and unchanged verdicts through the read-only audit accessor.
- [x] Replayed the frozen unchanged Auction result and rendered offscreen Swing with the failed performer selected. The evidence pane is absent and the remaining history uses its space. See `verification.png` and `frozen-receipt.json`.
- [x] Built and installed into `use-assembly/target/new` with the validated updater. 143 packaged UI classes match compiled output; contract/engine parity checks and installed class-loading checks passed. See `class-parity.json`, `install.log` and `installed-identity.json`.

The evidence is focused tests, frozen-source replay and offscreen Swing; no new live MAS or manual desktop acceptance was performed. The existing USE window was closed normally to release the installed plugin JAR lock. There are no changes to checkpoint control, models, OCL, source ASL or the sibling JaCaMo checkout.

`HistoryPanelProbe.java` takes an uncompressed result JSON, authored iStar path, output directory and XML mapping path. Its input recording is archived at `../execution-presentation-20261110/frozen-source-result.json.gz`.

Launcher: `D:\_CODE_BANK\Project_\08_Thesis\use\use-assembly\target\new\bin\start_use.bat`.
