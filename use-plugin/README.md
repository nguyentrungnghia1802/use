# USE JaCaMo Plugin 1.0.1

> **Current production authority:** the active path is
> `official JaCaMo/Jason/CArtAgO/Moise objects → typed Bridge semantic contract →
> JacamoSpecificationModel → J/A/C/M/X rules → native USE MModel/MSystemState →
> Session/OCL/runtime verification`. `CODE_GROUNDED_NATIVE` is the default and
> does not load V2 mappings, V2 OCL, or legacy text generation.

> **Historical 2026-09-27 executable live-runtime closure:** the production
> `JaCaMoBridgePlatform` now injects `BridgeAgArch`, owns the official
> `SnapshotCoordinator` sources, and serves a non-empty authenticated runtime cut.
> The reproducible Windows/JDK 21 helper is
> `tools/jacamo-bridge.ps1`; its final Hello run used the canonical JCM SHA-256
> `c81d15c9aa80c6e75ee8ead017f8daaddb1038ec9cfbc80c6a3057bde10b4101`, derived
> distribution fingerprint
> `a8cd26dadf45393fb4c73ffc7fc7a6f309b17a7af3a243a6a17f4712505fd6c9`, and
> produced `756` runtime facts and `1,095` USE objects in a separate consumer JVM.
> `BridgeVerificationGate` is now part of the production runtime verification path.
> This run is retained as the first live Hello closure and is superseded for current
> three-case acceptance by the 2026-09-28 record above.

> **Historical compatibility evidence:** frozen V1/V2 Ecore, mapping, OCL and
> runtime resources remain only for explicit compatibility/regression audits. They
> are not the semantic authority of the native path and are not modified by cleanup.

> **Current closure boundary:** the native path is covered by the focused and full
> reactor gates recorded in `docs/agent/task.md`. Original Auction plan/deadline
> equivalence remains unsupported; no historical live-run report is treated as
> native semantic evidence.


Current repository state: Maven artifact and plugin descriptor version `1.0.1`.
The historical annotated tag `v1.0.1` points to `7f77b1f4`; the native production
path is the authority in this checkout. Historical release manifests remain
evidence and must not be reported as a current published tag.

This release makes official JaCaMo objects, exported through the neutral Bridge,
the only production semantic authority for USE 7.5.0. Custom parser and in-process
connector implementations remain historical test evidence and are absent from the
release JAR.

## Install

1. Use JDK 21 and USE 7.5.0.
2. Extract the plugin archive into the USE installation root. This places the
   plugin JAR in `lib/plugins`; the active model is built from the official Bridge
   snapshot through native USE APIs at import time.
3. Add `bridge/lib/jacamo-bridge-contract-1.0.0.jar` and
   `bridge/lib/jacamo-bridge-jacamo-1.0.0.jar` to the JaCaMo application's
   classpath. JaCaMo/Jason/CArtAgO/Moise dependencies stay in that process and do
   not enter the USE JVM.
4. Create a 32-byte-or-longer random secret encoded as hexadecimal in a regular,
   non-symlink file readable only by the two local processes. Configure the JCM's
   official `platform:` entry with
   `org.jacamo.bridge.adapter.JaCaMoBridgePlatform("port=7777", "secretFile=/absolute/path/bridge-secret.hex", "distributionSha256=<64-lowercase-hex>")`.
   The platform injects `org.jacamo.bridge.adapter.BridgeAgArch` into configured
   agents before creation; do not edit the original case solely for observation.
5. Start USE with matching properties:
   `-Duse.jacamo.bridge.endpoint=tcp://127.0.0.1:7777`,
   `-Duse.jacamo.bridge.secret-file=/absolute/path/bridge-secret.hex`, and
   `-Duse.jacamo.bridge.distribution-sha256=<same-64-lowercase-hex>`.
6. Confirm `Plugins > JaCaMo > Status` or run `jacamo status`. Missing Bridge,
   schema/distribution mismatch, authentication failure, or selected-project
   mismatch fails explicitly; there is no parser fallback.

The automated load smoke validates discovery, descriptor parsing, the shell command
and both menu actions. `GuiPluginStagingIT` additionally prevents the source-tree GUI
from loading an obsolete plugin copy. Interactive GUI execution in a separately
installed binary distribution remains a documented manual environment check.

## Workflow

Open `Plugins > JaCaMo > Open Workbench...` and select the same `.jcm` entry hosted
by the Bridge. Selection is checked by exact JCM digest and project key before the
official snapshot can materialize a USE model. The Runtime tab exposes authority,
readiness, negotiated capabilities, completeness, model revision,
session/generation, redacted endpoint and stale/resync state. Import, Connect,
Reconnect and Resync perform an authoritative Bridge synchronization; the
workbench never launches or reconstructs a JaCaMo application.

Historical `binding.json` and custom parser behavior are retained only in tests.
Production Bridge identity is exact and never invokes fuzzy/source reconstruction.

The included `examples/auction` project is the release acceptance fixture. See
`docs/user-workflow.md`, `docs/architecture.md`, `KNOWN-LIMITATIONS.md`, and
`compatibility.json` for the exact supported scope.

The current onboarding and architecture contract are in
`docs/agent/CODE-GROUNDED-NATIVE-README.md` and `docs/agent/task.md`.

## Reproducible live Bridge evidence

From the `use` repository root, run:

```powershell
powershell -ExecutionPolicy Bypass -File .\use-plugin\tools\jacamo-bridge.ps1 `
  -JcmPath ..\jacamo\doc\tutorials\hello-world\code\helloworld\helloworld.jcm `
  -ObservationSeconds 15 -Headless:$false `
  -EvidenceDirectory .\use-plugin\target\final-system-acceptance\hello-rerun
```

The generic launcher copies the selected JCM project to a temporary directory, injects only the
official Bridge platform entry, generates a random secret, derives the runtime
fingerprint, starts JaCaMo and a classpath-isolated USE consumer, and records
`summary.json`, producer/consumer logs and fingerprint components. It never edits
the source project. Use the same `-JcmPath` form for any valid `.jcm`; the selected
project identity is checked by the official Bridge and no semantic name is inferred
by the launcher.

For a presentation that opens the configured USE GUI and keeps the derived JaCaMo
producer alive until the GUI closes, add `-InteractiveGui -TimeoutSeconds 1800`.
The helper prints the exact derived `.jcm` path to select in the workbench. This is
a launch convenience only; JaCaMo remains semantic/execution authority.

## Verify the download

The build produces `use-jacamo-plugin-1.0.1-v2-frozen.zip.sha256` beside the archive. Compare
the first hexadecimal field with a SHA-256 digest of the ZIP before installation.


### Historical V1/V2 compatibility contract

The V1/V2 resources and their exact freeze evidence are retained only because the
explicit `LEGACY_V2` compatibility API and regression/freeze tests still call them.
They do not define native production semantics. No native caller loads those
resources implicitly.
