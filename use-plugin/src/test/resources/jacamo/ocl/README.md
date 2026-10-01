# Native case-study OCL profiles

Store external USE named-invariant profiles under `<case-study>/<profile>.ocl`.
These are user-authored checks of the code-grounded native projection, not
Moise Norm translations. Keep profiles and generated evidence outside JaCaMo
source projects. Load a profile only after its native model is active; missing
required runtime coverage is SKIPPED, never a synthetic PASS.

## Audited Auction profile

`auction/auction_demo.ocl` contains 12 invariants. The user's original Desktop
file remains unchanged. The repository copy was corrected after inspecting the
official Auction specification, native MModel, and real runtime history:

- `PASS_AuctionHasTwoRoles` was replaced by
  `PASS_AuctionHasTwoConcreteRoles` and `PASS_AuctionHasOneAbstractRole`.
  Official Moise retains abstract `soc` alongside concrete `auctioneer` and
  `participant`. The checks use the native Boolean `Role.isAbstract`; no role
  is removed or filtered from the faithful state.
- `DEMO_RUNTIME_NoObservablePropertySnapshots` was replaced by
  `DEMO_RUNTIME_NoRunningAuction`. Framework/organization C09 properties already
  exist at baseline, so global emptiness cannot demonstrate Auction's lifecycle.
  The new deliberately restrictive demo policy observes only the real
  `auction_env.AuctionArtifact`, through C15 `Artifact.artifacts`
  (the actual native role name for ArtifactType) and C17 `Artifact.properties`.
  Exactly one `running` snapshot must have `valueTypes = '["java.lang.Boolean"]'`
  and `values = '["false"]'`. These attributes are canonical JSON Strings, not
  a Boolean/numeric projection. No bids are numerically parsed.
- The two `DEMO_FAIL` constraints are still intentionally false. Other static
  counts describe deployed declarations/specification objects, not live
  SchemeBoard/GroupBoard instance counts.

USE's existing `ASSLCompiler.compileInvariants(current MModel, ...)` compiles
and type-checks the complete profile before atomic installation on the same
Session/MSystem. Compiler-AST dependencies for the runtime policy are precisely
Artifact / ArtifactType / ObservablePropertySnapshot (C04/C03/C09, Cartago).
These are observational policies: FAIL does not imply Auction itself is buggy,
and does not stop the producer or roll back faithful state.

## Reproduction

From `D:\_CODE_BANK\Project_\08_Thesis\use`:

```powershell
& '.\use-plugin\tools\jacamo-bridge.ps1' `
  -JcmPath 'D:\_CODE_BANK\Project_\08_Thesis\jacamo\examples\auction\auction.jcm' `
  -OclProfilePath '.\use-plugin\src\test\resources\jacamo\ocl\auction\auction_demo.ocl' `
  -EvidenceDirectory '.\use-plugin\evidence\auction-ocl-transition' `
  -TimeoutSeconds 240 -ObservationSeconds 75 -MaxBufferedEvents 8192
```

The evidenced run added `-SkipBuild` only after the focused reactor had rebuilt
the native consumer and the official-runtime classpath was confirmed present.
Omit that option for a fresh checkout. Never assume disposable `target` classes
still exist after cleanup or an interrupted build.

The generic launcher stages the **whole original project**, including relative
ASL/XML/Java resources, before injecting only its Bridge platform. The original
JCM is the launcher input; this is not a detached JCM copy with missing resources.
With `-OclProfilePath`, a bounded startup barrier delays the exact official
`JaCaMoLauncher.startAgs()` hook until the consumer activates one native Session,
compiles/attaches the USE-side profile and records the baseline. Platforms/Bridge
start first. This is not runtime pause/step or a modification of Auction ASL/XML.
Interactive USE can load this profile with the Workbench OCL control;
automatic launcher OCL loading is headless-only.

## Real live evidence (2026-10-01)

Run: `use-plugin/evidence/auction-ocl-transition/20261001-141513-461-03ed0df4/`.
Profile SHA-256:
`231e18551d84034d2fe8c97da5b235079dc75f3d37f5c3be4caae08ccc7ca3b6`.

Baseline version 2 has **10 PASS / 2 intentional FAIL / 0 ERROR / 0 SKIPPED**
for the external profile. The two corrected Role checks PASS. All other external
outcomes remain unchanged except `DEMO_RUNTIME_NoRunningAuction`:

| State version / source event | Actual Auction property | Runtime policy |
|---|---|---|
| 2 / profile baseline | No live AuctionArtifact yet; Cartago coverage COMPLETE | PASS |
| 107 / cartago:706 | a2 created, running = `["false"]` | PASS |
| 110 / cartago:714 | a1 created, running = `["false"]` | PASS |
| 112 / cartago:721 | a2 started, running = `["true"]` | FAIL |
| 114 / cartago:731 | a1 started, running = `["true"]` | FAIL |
| 148 / cartago:872 | a1 stopped, a2 still running | FAIL |
| 160 / cartago:916 | a2 stopped; both running = `["false"]` | PASS |

Thus **PASS -> FAIL -> PASS** comes from original live create/start/stop events,
not fixture events, inferred numeric semantics, or resync. Each incarnation's
running property keeps its exact semantic ID through false -> true -> false.
The earliest FAIL was observed at 07:15:28.2176972Z, applied at
07:15:41.0171018Z, and verified at 07:15:41.0295969Z. Return to PASS was observed
at 07:15:30.6850972Z, applied at 07:15:44.630449Z, and verified at
07:15:44.640485Z. These timestamps expose real queue latency, not lockstep
verification of JaCaMo at the original observation time.

The same active MSystem (identity `1047460013`), Session
`446a8dc8-1405-4bdf-9d83-8d3f19ed92c8`, generation 1, is retained through
installation, 160 faithful mutation/re-check boundaries (versions 3..162),
and resync version 163. The transient runtime FAIL remains in 48 event-state
results (versions 112..159); faithful state is retained. All 160 changing states
also retain the two deliberate static FAILs.

The journal retains 1,133 hash-chained entries: 1,127 events, of which 160
materialize faithful mutations and 967 are evidence-only. Evidence-only
OBSERVED/SKIPPED records do not advance stateVersion or count as external OCL
re-checks. There are no GAP/STALE entries or external OCL ERROR/SKIPPED outcomes.
Cartago events 1..924 and Jason events 1..203 are ordered and contiguous; the
last consumed sequences match the final snapshot watermarks. All 12 original
Auction source files and the Desktop OCL remain unchanged, with no added files
in JaCaMo. No production-source change was needed for this re-audit.

`auction-ocl-transition-evidence.json` records ordered transitions, actual
property payloads, source ordering/watermarks, state hashes and scope.
`ocl-registry.json`, `native-model-inspection.json`, `ocl-baseline.json`,
`consumer-ready.json`, `ocl-runtime-evidence.json`, `original-project-integrity.json`,
and `replay/runtime.jsonl` retain compiler/source/identity/history evidence.
`producer.log` contains eight autonomous bids. All artifacts stay under USE.
`executed-command.ps1` retains the exact successful launcher invocation;
`profile-changes.diff` compares the current profile with the unchanged Desktop
source, and `test-results.json` records focused/full/release/replay gates.

## Audit limits and previous evidence

The earlier unchanged-profile run is preserved under
`use-plugin/evidence/auction-ocl/20261001-113340-452-8a8172c7/`; its 11-invariant
7-PASS/4-FAIL results describe the **old** profile only. The subsequent read-only
75-second audit is under `auction-ocl-audit/20261001-140913-960-648c9cc2/`.
An interrupted `-SkipBuild` attempt in `auction-ocl-audit/20261001-122611-458-417dc07b/`
failed because consumer classes were missing; it is not passing live evidence.

The earlier 20-second run consumed Cartago only through sequence 656 before
resync at watermark 922. Actual AuctionArtifact events were still queued, so
that prefix did not prove the protocol's transitions. The longer clean run above
drains the source stream before resync. Observation duration is not a universal
drain/latency guarantee; changing producer load may require a longer window.

Scope remains `OBSERVED_SUPPORTED_PROJECTION_ONLY`: Cartago/Jason observed
sources COMPLETE, Moise/NPL evidence PARTIAL, Jason action execution evidence-only.
This is not every JaCaMo internal state, complete Auction deadline/norm semantics,
live C08 ObsProperty, runtime pause/step, or a guarantee of identical random bids
on every execution. Upstream headless logging/shutdown warnings are retained
separately; they are not USE OCL ERROR outcomes.

Focused gate: 30 tests (4 official launcher + 26 plugin), zero failures/errors/skips,
finished 2026-10-01T14:14:57+07:00. Log:
`use-plugin/evidence/auction-ocl-transition/focused-tests.log`.

Full reactor gate: `mvn -B verify` PASS at 2026-10-01T14:22:14+07:00:
546 tests (406 unit / 140 integration), zero failures/errors/skips, including
all 10 plugin package/release integration tests. Log:
`use-plugin/evidence/auction-ocl-transition/full-reactor-verify.log`.
The plugin extracted from this freshly built release ZIP replayed the actual
1,133-entry Auction bundle in an isolated JVM using only the USE distribution
and test driver on the application classpath: `ISOLATED_NATIVE_RUNTIME_REPLAY_PASS`.
All recorded state/result hashes, including transient FAILs, are checked by the
production replay engine; the final hashes match the live manifest. Log:
the run directory's `auction-installed-replay.log`.
