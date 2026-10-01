# Code-Grounded Native USE path

**Status:** observed-state runtime verification, evidence-bounded (2026-10-01)

This is the concise operational reference. `task_workbench_ocl_runtime.md` is
the current Workbench/startup/re-analysis task; `task_runtime_verification.md`
records the underlying runtime implementation. `task.md` retains earlier
native-projection gates. `JACAMO-USE-CONCEPT-MAPPING-RULES.md` is the maintained semantic
rule contract. `agent.md` contains working rules. Production source and
executable tests remain authoritative; frozen V2/Ecore/golden artifacts are
compatibility/release evidence, not the native semantic authority.

## Architecture

```text
official JaCaMo/Jason/CArtAgO/Moise Java objects
        -> JaCaMo-side typed adapters
        -> neutral ModelSnapshot / semantic contract
        -> JacamoSpecificationModel
        -> CodeGroundedRuleCatalog (J01-J11/A01-A22/C01-C20/M01-M43/X01-X09)
        -> NativeUseModelBuilder + NativeUseStateBuilder
        -> one MModel + one MSystem
        -> validate/apply authoritative runtime snapshot and buffered events
        -> Session.setSystem(system)
        -> external USE-compiled OCL + runtime coordinator + normal USE views
```

The production facade entry point is `DefaultJaCaMoFacade`. Its implicit mode
is `CODE_GROUNDED_NATIVE`; `LEGACY_V2` is an explicit compatibility choice.
`JaCaMoWorkbenchPanel` is an inspection/UI surface and does not perform
semantic mapping.

## Build and run

Use the USE repository root (`D:\_CODE_BANK\Project_\08_Thesis\use`), with JDK
21:

```powershell
mvn -B -pl use-plugin -am test
mvn -B -pl use-plugin -am verify
```

The second command builds the plugin JAR, release archive, GUI staging copy,
and checksum before running the release/integration gates. The JaCaMo-side
adapter artifact is `jacamo-bridge-jacamo`; it is not packaged into the USE
GUI plugin JAR.

Launch a project in the existing USE GUI after the build:

```powershell
& '.\use-plugin\tools\jacamo-bridge.ps1' `
  -JcmPath 'D:\path\to\project.jcm' `
  -EvidenceDirectory 'D:\_CODE_BANK\Project_\08_Thesis\use\use-plugin\target\jacamo-bridge-evidence' `
  -InteractiveGui -SkipBuild
```

Wait for `INTERACTIVE_GUI_MODEL_READY`: the Workbench has imported and activated
the model automatically, but managed local agents have not started reasoning.
Optionally use **Load OCL...** and wait for **OCL_READY**, then click
**Start Runtime**. **LIVE** requires the owned producer's actual start ACK;
baseline invariant FAIL does not block Start. Platforms, artifacts and timers
are already bootstrapped: this boundary does not pause an entire JaCaMo MAS.
Close USE to stop that run. Projects containing only
ASL/JCM/XML may legitimately produce `compileJava NO-SOURCE`; the launcher
records `GRADLE_CLASSES_NOT_REQUIRED` and uses the official runtime classpath.
Missing Java output without that Gradle evidence remains a launcher error.

The launcher accepts `-ProjectionMode AUTO|FULL` for both GUI and headless runs.
Omission preserves `AUTO`. Add `-ProjectionMode FULL` to materialize the existing
full native projection, including `AgentProgram`, `Belief`, `AgentGoal`,
`Trigger`, `Action` and their supported program relations. The selection is
captured once by the facade through `use.jacamo.projection.mode` and retained
across rebuild/resync; invalid values are rejected. `gui-ready.json` and the
headless `summary.json` record the actual facade projection mode.

`FULL` is a projection/audit choice, not complete live JaCaMo semantics. Initial
beliefs/goals remain program-source facts, unsupported dynamic facts remain
evidence-only, C08 stays unavailable, and selecting FULL does not invent a
deployed `Agent` → `AgentProgram` association.

In interactive mode, `TimeoutSeconds` bounds Bridge startup, GUI import and
the managed producer's wait for Start (the control record carries its deadline).
It does not bound the lifetime after successful Start. The producer remains alive
until USE closes and the launcher writes its stop file. Headless observations
remain timeout-bounded. `gui-ready.json` proves initial activation only; the
Workbench Runtime tab refreshes Bridge readiness and the latest native
verification result, including state version, outcome counts, history,
coverage and `RESYNC_REQUIRED`/stale diagnostics after a subscription failure.

## Bridge configuration

`BridgeConnectionConfig` describes the separate-JVM endpoint, shared secret,
timeouts, queue limits, and project selection. `BridgeClient` consumes the
neutral contract; official adapters remain on the JaCaMo side. Runtime
connection, snapshot, event, and resync evidence are accepted only when the
contract identity, completeness, projection status, and exact binding checks
pass.

## Native USE session

`CodeGroundedNativePipeline` creates the native `MModel` and `MSystem`. The
validated system is activated through `NativeUseSessionActivator`. The facade,
runtime projector, and verification use that same system; no second runtime
state is reconciled by names. Initial activation follows validated snapshot
application and buffered-event delivery. A compatible resync reuses the same
`MSystem`, rebinds saved external constraint bytes, and atomically applies the
new snapshot. Failed resync preserves the prior Session and marks its coverage
stale. An incompatible model rebuild installs/recompiles the saved profile on
the candidate before replacing the Session; incompatibility is reported, not
silently dropped.

## External OCL

The Workbench's OCL profile loader calls `ExternalOclConstraintService` through
`RuntimeVerificationCoordinator`. Files must contain USE named invariant
declarations, not raw dialog expressions. For example:

```ocl
context ObservablePropertySnapshot
inv NoB: self.values <> '["B"]'
```

`values` is the C09 snapshot's deterministic JSON serialization of official
property values; it is not a live C08 `ObsProperty`. USE's existing
`ASSLCompiler.compileInvariants` performs syntax/context/type checking against
the current native `MModel`. The complete profile is compiled and conflict
checked before any invariant is attached. The registry retains context/name,
source bytes/file/hash, model revision, enabled status and compiler-AST
dependencies. Installation triggers a baseline verification. Invalid profiles
leave the current profile and state version unchanged.

Verification shows the profile, actual loaded stateVersion, current stateVersion,
latest result per invariant and PASS/FAIL/ERROR/SKIPPED counts. SYSTEM/CORE and
EXTERNAL/USER results remain distinct; evidence-only observations are not formal
invariant evaluations. A late install explicitly has no LIVE verification claim
for states before its loaded version.

Missing `AUTO` classes/associations produce explicit compiler incompatibility:
there is no inferred expansion or placeholder object/class. Constraints whose
runtime source is incomplete, disabled, or unavailable (including C08) are
`SKIPPED`; undefined bodies/evaluation exceptions are `ERROR`.

The generic headless launcher accepts `-OclProfilePath <absolute-or-relative-file>`.
With this option it starts official platforms/Bridge first, delays the official
`startAgs()` hook until the native consumer has activated its Session and loaded,
compiled/attached and baseline-verified the profile, then starts agent reasoning.
The startup wait is timeout-bounded/cancellable; this does not pause/step an
already-running MAS or roll back user-invariant FAIL. Without a profile, startup
remains autonomous. Interactive GUI profiles still use the Workbench loader;
automatic `-OclProfilePath` is explicitly headless-only.

Case-study profiles live under `src/test/resources/jacamo/ocl/<case-study>/`.
See its README for the Auction command and audited case-study profile. Its Role
checks distinguish concrete roles from Moise's implicit abstract `soc`; its
runtime policy observes the real AuctionArtifact `running` C09 property using
the native JSON String representation, without filtering faithful objects.
Headless evidence includes `ocl-baseline.json`, registry/source SHA-256,
`native-model-inspection.json`, per-event `ocl-runtime-evidence.json`, one-system
identity checks, and a replay bundle. A launcher execution PASS is not an
assertion that every OCL invariant passed; deliberate policy violations and
coverage limitations are reported separately.

## Mapping and inspection

`CodeGroundedRuleCatalog` has 105 explicit rule IDs and version `1.0.0`.
`CodeGroundedTraceIndex` records exact source/target identities, source FQCN,
evidence authority, fidelity, capability status, and diagnostics. The Mapping
Inspector displays that trace and supports dimension/status filters; it never
selects or changes semantic mappings. Native trace schema `1.2.0` preserves
the original `SourceEvidence` (URI, content SHA-256 and line range), including
each Plan's local or dependency-JAR include source. The inspector displays
JAR entry URIs directly; it never labels them as the agent root or JCM file.

The five top-level tabs are **Project | Mapping Inspector | Mapping Rules |
Verification | Runtime**. Mapping Rules has exactly **Rule | JaCaMo → USE**
columns and lists the catalog, not mapped instances. Capability/fidelity labels
remain visible in those descriptions. Diagnostics and explicit-binding backends
remain available without separate top-level tabs. Project semantic-record counts
include program nodes, containers and cardinality helpers, not only live domain
objects; exact CROSS counts are not inferred from matching names.

## Runtime boundary

Static facts are built from official adapters. The active runtime flow is:

```text
official CArtAgO ILogger / controller snapshot
 -> CartagoSnapshotSource / SnapshotCoordinator
 -> JaCaMoBridgePlatform bounded queue / typed Bridge transport
 -> BridgeClient ordering/coverage checks
 -> NativeRuntimeProjector exact identity/incarnation rules
 -> RuntimeVerificationCoordinator single EDT writer
 -> NativeRuntimeMutationEngine atomic MSystemState mutation
 -> stateVersion++ / USE invariant evaluation
 -> immutable RuntimeVerificationResult / journal / checkpoint
 -> AtomicStateChangedEvent / normal USE views / Workbench
```

Faithful C09 create/update/remove property deltas and artifact
create/dispose/recreate are materialized with exact workspace/artifact UUID
and property ID bindings. Values/types/annotations come from official arrays,
not parsing diagnostic strings. Artifact creation combines its initial
properties into one transaction; pre-creation property notifications are
deferred until that authoritative boundary.

User invariant `FAIL` is observational: the violating state remains visible.
Malformed/protocol/identity/structural failures roll back and quarantine
delivery pending resync. Source GAP, subscription/queue failure or lost journal
persistence invalidates prior PASS and produces `STALE/INCOMPLETE` with
`SKIPPED` outcomes. Each committed mutation/snapshot/profile change increments
local `stateVersion`; evidence-only observations and manual verify do not.
One post-commit verification is published; invariant views consume that result
without an additional background full-check. Manual verify/export use the same
writer barrier and cannot inspect half-applied native transactions.

Jason action execution,
intention, event, and transition-system records are evidence-only unless a
separate rule provides authoritative materialization. Missing or incomplete
facts remain `UNAVAILABLE`/`UNSUPPORTED`; they are not guessed. In particular,
there is no automatic Norm-to-OCL conversion and no claim of full original
Auction deadline/self-reference equivalence.

History contains immutable results, not mutable `MSystemState` references.
The default journal has a 512-result memory tail and a bounded 64 MiB persisted
hash-chained JSONL file. Baseline `.cmd` is retained plus eight recent
checkpoint files (each capped at 16 MiB). Loss of persistence is sticky and
requires a new recording session; resync cannot erase an earlier journal GAP.
These are observed supported states/checkpoints, not every internal JaCaMo
state. There is no JaCaMo pause/step enforcement.

Runtime expands formal results into **StateVersion / Event / Constraint / Result /
Time** rows. **Show result changes only** compares each exact invariant within
its recorded interval. The UI's 128-record live tail is explicitly bounded;
64-record disk pages expose older persisted history. Memory eviction is not a
disk GAP, and persistence loss is never labelled as complete history.

## Export and reproducibility

- `NativeUseExporter` emits deterministic `.use` text with USE's official
  printer and recompiles it, checking structural signature/hash equality.
- `NativeUseStateExporter` emits separate deterministic state JSON from the
  native `MSystem`.
- `CodeGroundedTraceExporter` emits the exact native trace JSON; it does not
  translate it to the V2 trace schema.
- `JaCaMoFacade.exportVerificationReport` remains the verification report
  export surface.
- `NativeUseSoilExporter` exports a consistent standalone `.cmd` using native
  objects/links/values and USE SOIL, with official compiler/replay tests.
- Workbench **Export replay...** / `JaCaMoFacade.exportRuntimeReplay` writes an
  empty destination directory containing `model.use`, `baseline.cmd`,
  `constraints.ocl`, `runtime.jsonl`, and a hash/count manifest.

`NativeRuntimeReplay.replay(directory)` loads the `.use` with USE's compiler,
constructs the baseline via SOIL, then replays profile/snapshot/event boundaries
through the same native projector/coordinator. The offline system is isolated
from the active live Session. Per-entry chain, state version, state hash and
semantic result hash must match. Corrupt/missing data fails closed; recorded
coverage gaps return explicitly incomplete replay. Export rejects a lost
journal. Invariant checks are not run on intermediate baseline SOIL steps.

**Recorded replay...** checks that original timeline. **Re-analyze with current
OCL...** first validates it, reconstructs its states in an isolated offline
system, and evaluates the current profile there. Output is labelled
`REPLAY_REANALYSIS`, not LIVE or original-result parity. It neither replaces
the live Session nor overwrites its current result/history, and does not turn
late-loaded OCL into past LIVE evidence. Use a separate empty output directory.

Manual SOIL mutations or constraint-flag edits outside this recorded workflow
are not reconstructed automatically; a replay hash mismatch is an error, not
fuzzy repair. Component-version manifest, USE-version field, and production
trace-size telemetry remain optional outside this runtime export contract.

## Compatibility and release

The native facade is the implicit production authority. V2 resources are
explicit compatibility/historical resources and remain in their frozen
namespace for audit/release reproducibility. `V2RuntimeRuleRegistry` is an
explicit adapter used only by legacy-compatible constructors; the native
pipeline uses `CodeGroundedRuntimeRuleRegistry` and does not load V2 mapping
semantics.

Packaging tests verify native classes are present, obsolete parser/connector
authorities are absent from the plugin JAR, the JaCaMo adapter is separate,
the GUI staging JAR is byte-identical, and the release ZIP/checksum are
consistent.

## Evidence commands

Focused Workbench/native/runtime checks (unit tests and actual GUI/live ITs):

```powershell
mvn -B -pl use-plugin -am `
  "-Dtest=ManagedStartupControlTest,LiveJaCaMoLauncherLifetimeTest,ManagedRuntimeWorkflowTest,ManagedBootstrapTest,CodeGroundedRuleCatalogTest,ExternalOclConstraintServiceTest,NativeMOperationProjectionTest,ProductionAuthorityPhase8Test,LegacyV2OclIsolationTest,NativeProjectionAuditTest,NativeUseSessionActivationTest,NativeRuntimeReanalysisTest,NativeRuntimeReplayTest,NativeRuntimeFacadeIntegrationTest,RuntimeVerificationCoordinatorTest,RuntimeHistoryTest,JaCaMoWorkbenchPanelTest,GenericLauncherScriptTest" `
  "-Dit.test=NativeUseGuiEndToEndIT,ManagedAuctionWorkbenchIT" `
  "-Dsurefire.failIfNoSpecifiedTests=false" `
  "-Dfailsafe.failIfNoSpecifiedTests=false" `
  "-Dit.failIfNoSpecifiedTests=false" "-DfailIfNoTests=false" verify
```

The acceptance scope and dated evidence are recorded in
`task_workbench_ocl_runtime.md`, `task_runtime_verification.md` and the earlier `task.md`; the active
mapping contract is in `JACAMO-USE-CONCEPT-MAPPING-RULES.md`. Unchecked task
items remain open where implementation, API availability, or live evidence is
missing; they are not implied to pass by this README.

## Maven CI portability

The GitHub Maven job checks out USE and the audited JaCaMo source side by side.
The case-study revision is pinned to `3866858a7ebf6be85d9199c13a09cf4bfb8191be`;
the local JaCaMo documentation-only commit does not change those case sources.
Linux keeps a `JaCaMo` path alias for historical fixture paths, without editing
the examples. Child-JVM tests use `java` on Unix and `java.exe` on Windows from
the configured JDK. GUI integration tests run under Xvfb, not a headless skip.

The previous missing-ZIP failure was secondary: Maven failed first while a
contract test tried to launch Windows `java.exe` on Ubuntu, and `mvn | tee`
masked that failure. CI now uses explicit Bash/pipefail, requires both release
archive families, uploads their actual paths, and retains build/test logs on
failure. No test, frozen asset, mapping or runtime capability is bypassed.
