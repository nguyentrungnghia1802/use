# Execution phases and intentional population presentation

## Result and scope

Setup and organisational scopes sharing the same qualified physical MOISE scheme identity now appear as one execution. Their phase names, native context IDs, player occurrences, requirements, certificates and raw histories remain separate. A setup request without a created scheme remains visible on its own; no future domain state is substituted.

The presentation index reads canonical source identities from qualified bindings, independently of XML column names. It does not match displayed business IDs, source/model/agent names, counts or expected outcomes. Ambiguous references or a changing scheme identity do not join histories. Models without a qualified shared scheme keep their existing execution scopes. Legacy diagnostic instance graphs remain available.

Multiple Goal/Task instances now display result counts instead of an invented failure-dominant Goal above them. Individual results remain Unknown, Complete or Incomplete; confirmed non-assignment remains assignment information. The native ANY/ALL dependency result and selected-requirement conformance are unchanged. Current iStar State renders one authored graph per display execution, showing its admitted phase elements and resources. Elements absent from that execution are hidden rather than marked Unknown. Population badges count assigned instances and expose their distribution in the tooltip.

The execution label no longer depends on an `auctionId` field. Short labels use the native Entity state or generic scalar scope bindings. No production case-specific verdict branch was introduced. Case-specific authored mappings/assertions remain model data.

## Executed evidence

- [x] 41 focused tests passed, with zero failures/errors/skips. The selected classes cover phase grouping, initial failure before scheme creation, renamed binding columns, repeated display names with different incarnations, scoped failure isolation, historical navigation, instance graphs, result distribution, native inventory and generic ANY/ALL propagation.
- [x] Frozen replay of the user's unchanged external Auction result: four native contexts appear as two executions and two iStar State graphs. Both organisational roots remain Complete. Each mixed bid population keeps three fulfilled, one violated and one unassigned capability occurrence. The failed performer remains Incomplete. The setup results are present in the same execution view.
- [x] Offscreen Swing rendering completed and the authored model fingerprint remained unchanged. See `verification.png` and `frozen-receipt.json`.
- [x] Reactor package built with the selected tests, without clean or full verify. `focused-package.log` contains the final test/package result.
- [x] Validated updater installed the package into `use-assembly/target/new`. All 123 packaged presentation classes matched current compiled output; the updater also verified 133 contract classes and 297 engine classes. See `class-parity.json` and `install.log`.
- [x] Installed class-loading probe resolved the new presentation classes, adapter, JaCaMo launcher and barrier runtime from the installation. `installed-identity.json` records the exact paths and SHA-256 hashes. The packaged dependency-lock/classpath preparation script passed.

The replay input is preserved as `frozen-source-result.json.gz`; `acceptance.json` records its hashes. `FrozenPresentationProbe.java` accepts an uncompressed result JSON, authored iStar path and output directory. `InstalledExecutionIdentityProbe.java` accepts the installation path and receipt path.

## Boundaries and delivery

This change is confined to presentation and its tests. Native MSystem evaluation, Goal/Task engines, OCL, mapping semantics, checkpoint selection/admission/release, source clocks and business ASL were not changed. The sibling JaCaMo checkout required no change. The user's disabled deadline assertion remains unchanged.

Evidence is frozen-source replay, focused unit/component tests, offscreen Swing and an installed class-loading probe. No new live MAS or manual desktop acceptance was performed for this presentation-only change. Original runtime journals and unrelated untracked artifacts are preserved.

Launcher: `D:\_CODE_BANK\Project_\08_Thesis\use\use-assembly\target\new\bin\start_use.bat`.
