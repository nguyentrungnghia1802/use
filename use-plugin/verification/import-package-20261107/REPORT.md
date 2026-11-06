# Prepare/import package repair

## Cause

The shipped shaded plugin lacked `org.jacamo.bridge.contract.JasonAgentInventory`,
although the standalone bridge contract contained it. The final plugin-only Maven
packaging invocation used the stale contract from the local Maven cache rather
than the reactor artifact. The renderer-only test could pass while runtime import
failed when mapping first referenced the missing class.

This was an installation/build regression, not a failed Auction assertion.

## Repair

- Rebuilt with `-pl use-plugin -am package` and focused checks, without clean verify.
- Updated the installed plugin, contract and adapter from the same reactor build.
- Added `tools/update-runnable-plugin.py` to check every contract and semantic-engine
  class against reactor bytes before deployment, and check the installed result.
- Added packaging instructions and a missing/mismatched/matching class regression.
- Existing installation files were backed up; runtime work and unrelated changes
  were preserved. The user closed USE to release Windows JAR locks.

No domain model, verdict, engine, execution barrier or runtime assertion was edited.
The requested top-right status/Continue redesign is deferred to a separate task.

## Evidence

- `before.log`: the installed old plugin is rejected for the exact missing class.
- `build.log.gz`: BUILD SUCCESS; 2 JasonAgentInventory tests and 8 LiveGoalSummary tests
  passed, zero failures/errors/skips.
- `guard.log`: packaging regression passed for missing, mismatched and equal bytes.
- `package.json`: 133 contract classes and 297 semantic-engine classes match;
  installed JAR hashes and the actual prepared producer classpath are recorded.
- `prepare-import.log`: the installed consumer loaded JasonAgentInventory and
  RuntimeFactSources from the shipped plugin, then successfully prepared the real
  external Auction and imported AuctionState into the authoritative USE MSystem.
  The owned producer was stopped in cleanup. Agent reasoning was not started.

The probe used only the shipped host/plugin consumer classpath and its own probe
class. This is an automated headless Prepare + Import acceptance, not a manual
desktop or full Auction conformance run. No forced verdicts or model edits.

Launcher: `use-assembly/target/new/bin/start_use.bat`.
