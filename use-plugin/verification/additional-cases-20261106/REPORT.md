# Hello World and House Building verification audit

Date: 2026-11-06. Current source on `main`, following population coverage delivery
`6f13f3a8`. This report concerns the authored bundles under `use-plugin/examples`,
not an unmodified external tutorial. The user explicitly extended live-case scope.

Both cases completed their native Goal outcomes using the existing production
engine. House Building also had one unsuccessful attempt caused by unavailable
checkpoint control requests. Three subsequent diagnostic runs completed. This is
evidence for the successful path, not a claim that the intermittent fault is fixed.

## Executed evidence

| Authored case / attempt | Native Goal outcomes | Presented cuts | Observed native PRE / POST checks | Required evidence gaps |
| --- | --- | ---: | --- | ---: |
| Hello World | 2 FULFILLED | 14 | 13 PASS / 13 PASS | 0 |
| House Building 1 | 15 PENDING; run stopped by audit | 10 | 27 PASS / 27 PASS | 1 control-channel error |
| House Building 2 | 15 FULFILLED | 31 | 126 PASS / 126 PASS | 0 |
| House Building 3 | 15 FULFILLED | 31 | 120 PASS / 120 PASS | 0 |
| House Building 4 | 15 FULFILLED | 34 | 126 PASS / 126 PASS | 0 |

All five attempts retained unchanged source/model/mapping/OCL hashes. No domain
operation, agent, seed, expected winner or verdict was injected. No production
code or authored assertions changed in this audit. No review override was needed
in the successful runs. The harness can retain and acknowledge a genuine native
failure, but that branch was not exercised by these natural runs.

The cut totals and bid invocation totals are observations, not expected constants.
Concurrent source operations may be drained into the same cut; scheduling changes
their grouping. They are not one cut per passive CSL attribute update. The raw
native cuts and source decision identities remain available separately from UI
state navigation.

The 52 focused tests passed, with zero failures/errors/skips. They cover authored
bundle integrity/provenance, the existing House Building wait fixture, mapping,
operation-history cuts, empty observer populations, read-set coverage, checkpoint
inventory/control and execution-scoped UI/data coverage. See the retained log
`../additional-cases-20261106-focused.log`. No full clean verify was run.

## What the authored models actually verify

Hello World maps the five console artifacts and creates one `GreetingRun` for the
workspace selected by its XML mapping. `TwelveMessagesObserved` and
`NonnegativeCounts` are the authored Goal requirements. In this run the selected
console reached `numMsg = 12`; native operation checks verified `CanPrint` and
`CountIncremented` against real invocation PRE/POST states. This is not a proof of
message text/order or normative compliance in every workspace.

House Building uses its workspace `BuildingRun`, eight `ProcurementAuction`
objects and an observer `BuildProgress`. The 15 authored Goal conditions were
fulfilled, including `HouseRealized`. All 11 authored invariant definitions
(budget, nonnegative bid and nine construction-order checks) passed at the final
cut. Actual `bid` PRE/POST checks verify nonnegative offers and retaining the best
offer. Construction order means source sequence order of successful operation
returns, as explicitly stated in `house.ocl`; it is not first internal MOISE truth
time. The model does not claim full normative or TaskMarking coverage.

Both profiles intentionally omit organization/mental-state verification from
their admitted authored requirements. Generated supporting
`OrgContext::CslV4RoleCompatibility` remains `SKIPPED` for unproven population
coverage; raw source coverage remains `INCOMPLETE`. These facts are retained.
They are distinct from `Data coverage: COMPLETE` for the required checks at the
actual verified checkpoints. A future, not-yet-achieved Goal remains pending,
not an applicable failed invocation.

## Presentation and installation

`check-presentations.py` decoded authentic native result hashes and replayed the
actual Hello World cuts and the final House Building run into the production
Swing presenter/panel. Both CSL and iStar Verification facets passed:

- All and every admitted execution showed complete required data coverage.
- Final All verdict was `COMPLETE_OBSERVED` and all authored Goals were Complete.
- Every raw checkpoint remained in the audit history; execution switching did not
  erase the accepted state or its results.
- Offscreen CSL/iStar screenshots were rendered and inspected. This was not a
  live desktop interaction, diagram acceptance or a manual review-button test.

`verify-install.py` independently loaded 14 installed classes and checked their
CodeSource and SHA-256. Installed production class bytes match the current built
plugin/bridge; both cases' five installed inputs match their authored sources.
The patched JaCaMo JAR is
`use-assembly/target/new/bridge/runtime/jacamo-1.3.1-checkpoint-SNAPSHOT.jar`, SHA-256
`aca1163c73b854123526f52c22c0ea8c6f9bc772376c5fb73d20a1aa4c6af155`.
See `installed-runtime.json`. Test/evidence-only additions require no production
JAR replacement; no clean release assembly is claimed. The installed CLI launcher
also started and exited normally with `-nogui` and `quit` (exit code 0).

Runnable launcher:
`D:\_CODE_BANK\Project_\08_Thesis\use\use-assembly\target\new\bin\start_use.bat`.

## Remaining system issue and evidence boundary

Attempt `house-building-1793904755891`, producer
`20261106-015236-877-96614a70`, ended with consumer status `HELD_UNVERIFIED`:
`BridgeProtocolException:VERIFIED_CHECKPOINT_REQUEST_FAILED`.
The last visible token was `c5819739-00a3-4f35-9dc0-74b29f091acd`.
This is a system/control error, not an authored constraint failure or proof that
House Building is semantically false.

The source ledger actually contains APPROVE and RELEASE_RECEIPT for that last
token. Therefore the consumer's stale held view alone does not establish the
producer's next active cut or suspension state. No subsequent source cut was
proved by this attempt. The producer's shutdown also reported
`REAL_JACAMO_CLEANUP_TIMEOUT`. Its owned-runtime logs are retained locally under
`use-plugin/target/owned-runtime/a73ff2e5-6c56-4178-a71c-0d6bba949140`.
The initial attempt predates thread-dump diagnostics, so the underlying exception
and the exact blocked source thread cannot be recovered from that evidence.

Three reruns used process-local FINE transport logging; the last two captured
producer/consumer thread dumps after a cut took more than 15 seconds. These runs
finished without required evidence gaps. One consumer dump directly locates time
in `RuntimeEventJournal.visitRange` / `VerifiedCheckpointInventory.evaluatePrefix`
decoding native semantic history. The source checkpoint capture worker was idle.
This establishes a costly verification step; it does not establish that the same
step caused the initial control failure. A complete checksum-validated prefix is
currently read when required invocation results have left the RAM tail. Its
authority must be preserved in any future optimization.

No timeout was increased and no checks were removed to obtain these passes.
Transport root cause and performance optimization remain open. Fresh desktop
acceptance remains unperformed. The retained initial error prevents a blanket
stability sign-off.

## Reproduction and retained artifacts

`AuthoredCaseVerificationAudit` is a test-only, manifest-selected runner. It owns
and stops only its own producer, uses one authoritative MSystem, persists exact
native cuts before presentation completion, records source decision receipts and
fails if required evidence is unavailable. No production case-name branches were
introduced. `run-cases.py` runs it; `check-presentations.py`, `verify-install.py`
and `summarise.py` provide the remaining checks.

`SUMMARY.json` reconciles every attempt, including the failed one. Per-attempt
directories retain native cuts, final snapshots, input hashes, actual Goal and
operation outcomes, control receipts and logs. Successful offscreen presentation
receipts are in the
Hello World directory and the third/fourth House Building directories. Diagnostic
thread dumps remain attached to their original attempts. Compiled probe classes
and unrelated historical work are not part of the audit commit.

Focused Maven invocation, from the `use` repository:

```powershell
mvn.cmd -o -B '-Dtest=NewCaseStudyBundlesTest,HouseBuildingWaitFixtureTest,EmptyObserverPopulationTest,ObservationReadSetTest,ExecutionScopeViewsTest,VerificationDataCoverageTest,OperationHistoryCutDecoderTest,XmlRuntimeMappingTest,VerifiedCheckpointServiceTest,VerifiedCheckpointInventoryTest,RuntimeFactSourcesTest,XmlObserverProjectionTest' '-Dsurefire.failIfNoSpecifiedTests=false' '-Dassembly.skipAssembly=true' '-Dmaven.antrun.skip=true' -pl use-plugin -am package
python use-plugin/verification/additional-cases-20261106/run-cases.py
python use-plugin/verification/additional-cases-20261106/check-presentations.py
python use-plugin/verification/additional-cases-20261106/verify-install.py
python use-plugin/verification/additional-cases-20261106/summarise.py
```

Run builds and live producers sequentially; do not replace loaded runtime JARs
during a live attempt. The runner permits only the two requested bundles, while
the Java audit itself selects inputs from the case manifest. This restriction
belongs to the audit, not the production checkpoint engine.
