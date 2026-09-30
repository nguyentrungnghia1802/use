# Code-Grounded Native USE path

**Status:** operational native path, evidence-bounded (2026-09-29)

This document is the operational README for the code-grounded path. The three
design/mapping documents in this directory remain the source design material;
the frozen V2/Ecore/golden artifacts remain unchanged and historical/release
scoped.

## Architecture

```text
official JaCaMo/Jason/CArtAgO/Moise Java objects
        -> JaCaMo-side typed adapters
        -> neutral ModelSnapshot / semantic contract
        -> JacamoSpecificationModel
        -> CodeGroundedRuleCatalog (J01-J11/A01-A22/C01-C20/M01-M43/X01-X09)
        -> NativeUseModelBuilder + NativeUseStateBuilder
        -> one MModel + one MSystem
        -> Session.setSystem(system)
        -> USE OCL, verification, runtime projector, and UI facade
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
state is reconciled by names. Failed import/activation leaves the previous
session system intact. Existing USE OCL evaluates against the activated
system.

## Mapping and inspection

`CodeGroundedRuleCatalog` has 105 explicit rule IDs and version `1.0.0`.
`CodeGroundedTraceIndex` records exact source/target identities, source FQCN,
evidence authority, fidelity, capability status, and diagnostics. The Mapping
Inspector displays that trace and supports dimension/status filters; it never
selects or changes semantic mappings.

## Runtime boundary

Static facts are built from official adapters. Runtime facts are accepted only
through typed native runtime rules and exact bindings. Jason action execution,
intention, event, and transition-system records are evidence-only unless a
separate rule provides authoritative materialization. Missing or incomplete
facts remain `UNAVAILABLE`/`UNSUPPORTED`; they are not guessed. In particular,
there is no automatic Norm-to-OCL conversion and no claim of full original
Auction deadline/self-reference equivalence.

## Export and reproducibility

- `NativeUseExporter` emits deterministic `.use` text with USE's official
  printer and recompiles it, checking structural signature/hash equality.
- `NativeUseStateExporter` emits separate deterministic state JSON from the
  native `MSystem`.
- `CodeGroundedTraceExporter` emits the exact native trace JSON; it does not
  translate it to the V2 trace schema.
- `JaCaMoFacade.exportVerificationReport` remains the verification report
  export surface.

An optional `.cmd`, component-version manifest, USE-version field, and
production trace-size telemetry are not currently part of the native export
contract.

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
mvn -B -pl use-plugin -am "-Dtest=CodeGroundedExportTest,CodeGroundedLargePlanTest,CodeGroundedDeterminismTest,NativeUseSessionActivationTest,RuntimeFoundationTest,JaCaMoWorkbenchPanelTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

The acceptance scope is documented in `CODE-GROUNDED-NATIVE-MIGRATION-REPORT.md`
and `task.md`. Remaining unchecked boxes in `task.md` are intentional
limitations or require explicit cleanup approval.
