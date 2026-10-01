# Code-Grounded Native USE path

**Status:** observed-state runtime verification, evidence-bounded (2026-10-01)

This is the concise operational reference. `task_runtime_verification.md` is
the active runtime implementation/evidence record; `task.md` retains earlier
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
the model automatically. Close USE to stop that run. Projects containing only
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

In interactive mode, `TimeoutSeconds` bounds Bridge startup and GUI import,
not the lifetime of an already imported session. The producer remains alive
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

Focused native/export/runtime checks:

```powershell
mvn -B -pl use-plugin -am "-Dtest=ExternalOclConstraintServiceTest,RuntimeVerificationCoordinatorTest,NativeRuntimeReplayTest,NativeRuntimeBridgeCoverageTest,LiveCartagoNativeVerificationTest,NativeRuntimeFacadeIntegrationTest,NativeUseGuiEndToEndIT,NativeUseSoilExporterTest,GenericLauncherScriptTest,JaCaMoWorkbenchPanelTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

The acceptance scope and dated evidence are recorded in
`task_runtime_verification.md` and the earlier `task.md`; the active
mapping contract is in `JACAMO-USE-CONCEPT-MAPPING-RULES.md`. Unchecked task
items remain open where implementation, API availability, or live evidence is
missing; they are not implied to pass by this README.
