# Truth result colors

The shared CSL/OCL result table displays `True` in bold dark green (#166534)
and `False` in bold dark red (#B91C1C). Selected result cells use pale green
(#DCFCE7) or pale red (#FEE2E2). Text remains visible. Other result labels retain
the table defaults, including selection colors and font, on every renderer reuse.

Only the result-column renderer and its workflow provenance were changed.
Native truth, contracts, checkpoint selection and release behavior were not edited.

Validation on 2026-11-07:
- Incremental reactor package: BUILD SUCCESS; LiveGoalSummaryTest 8 passed.
- Pinned workflow provenance test: 1 passed.
- Offscreen probe checked unchanged labels, selected/unselected colors and renderer
  reuse through True, False, Unknown and Error; preview.png was visually inspected.
- The same probe passed with the renderer loaded from the installed target/new
  plugin JAR. package.json records its SHA-256 and backup location.
- The installed JaCaMo JAR hash still matches the previous verified package.
- No live MAS or Computer Use was needed for this presentation change.

Launcher: `use-assembly/target/new/bin/start_use.bat`.
An already running USE process needs to be restarted to load the updated plugin.
