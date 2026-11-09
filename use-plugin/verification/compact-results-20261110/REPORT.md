# Compact Goal/Task results and hidden non-assignment bookkeeping

The Goal/Task Result cells now show only Complete, Incomplete or Unknown. Instance counts are removed from those cells and selected-element captions. Confirmed non-assignment rows are hidden from the ordinary agent/occurrence views and their displayed details; the original evaluation rows, assignment logic and audit/replay evidence remain intact. Missing evidence without confirmed non-assignment is still shown as Unknown.

The overview reads ANY/ALL from the admitted mapping and source dependency graph. It follows explicit Goal realization views and their refinements to identify the relevant capability Goals and Tasks, without matching case, actor or Goal names. ANY is evaluated within each native execution scope; overview results across executions use ALL, so another execution's success cannot conceal a failed execution. Conflicting declared population policies yield Unknown when their outcomes disagree. Without an explicit population declaration, the existing conservative ALL overview is retained.

This is presentation only: individual results, native Goal/Task/OCL evaluation, selected-requirement conformance and checkpoint admission/release were not changed. In particular, a failed performer remains Incomplete even when an ANY population overview is Complete. The normal iStar State badges use the same three-valued overview instead of population numbers.

## Evidence

- [x] 34 focused tests passed, zero failures/errors/skips. Coverage includes mapping-driven ANY versus ALL, explicit capability views and Task descendants, Unknown evidence, execution isolation, hidden non-assignment with retained raw rows, phase grouping, native inventory and original engine population propagation.
- [x] Replayed the existing unchanged Auction recording with the current authored iStar model and actual XML policy/realization declarations. Both native organisational roots retain COMPLETE_OBSERVED; bid overview rows show Complete, while the unsuccessful performer remains Incomplete in Agent instances. The selected bid shows four visible agent rows, with confirmed non-assignment omitted. See `frozen-receipt.json` and `verification.png`.
- [x] Offscreen Swing checks ensure all visible Goal/Task/agent Result cells use the three allowed labels. The authored graph fingerprint remains unchanged. No fresh live MAS or manual desktop acceptance was performed.
- [x] Reactor package built with the selected tests, without clean/full verify. The validated updater refreshed `use-assembly/target/new`; 133 contract and 297 engine classes passed parity checks.
- [x] All 143 packaged UI classes match compiled output. The installed class-loading probe confirms the plugin, adapter, JaCaMo and barrier JAR paths/hashes; see `class-parity.json` and `installed-identity.json`.

`CompactResultsProbe.java` takes four arguments: an uncompressed source result JSON, authored iStar path, output directory and mapping XML path. The unchanged recording is archived in `../execution-presentation-20261110/frozen-source-result.json.gz`; its hashes are recorded in that directory's `acceptance.json`.

Launcher: `D:\_CODE_BANK\Project_\08_Thesis\use\use-assembly\target\new\bin\start_use.bat`.
