# Compact import evidence JSON repair

The workbench encodes `semanticProgramEvidence()` during its post-import refresh.
The compact branch returned `ObservationCapabilityRequest` Java records in
`compactCapabilityDemands`. `CanonicalJson` correctly rejects arbitrary Java
records, causing the reported import/display error even after model preparation.

The facade now exports each request through its existing versioned `evidence()`
contract. Request IDs, providers, required flags and parameters remain intact.
The canonical JSON encoder, runtime mapping, native assertions, checkpoint
selection/control and domain verdicts are unchanged.

Focused evidence: `focused-package.log` reports 9 tests with zero failures,
errors or skips. Native imports for Auction, Auction False, Hello World and
House Building now also encode/decode the exact facade evidence map consumed
by the workbench and verify that no request metadata is lost. The existing live
Auction test gained the same guard before Start; that live test was not rerun
for this presentation-only repair.

The validated updater refreshed `use-assembly/target/new`. Its installed facade
class matches the reactor bytecode, all contract/engine dependency classes were
checked, and launcher runtime classpath preparation succeeded. `receipt.json`
records the test and installed plugin hashes. No manual desktop test is claimed.
