# USE JaCaMo Plugin 1.0.1

> **V2 frozen release candidate:** Metamodel V2 and Mapping 2.2.0 drive IR,
> extraction, transformation, trace, OCL and runtime target binding. Independent
> directional order is represented by generic target-only ranks. Phase 35 clean
> acceptance is 350/350 PASS; the latest Phase 43 full reactor is 372/372 PASS. See
> [transformation](docs/project/v2-migration/phase35-transformation.md) and
> [runtime contract](docs/project/v2-migration/phase35-runtime-targets.md).
> The archive is `use-jacamo-plugin-1.0.1-v2-frozen.zip`, status
> `FROZEN_V2_RELEASE_CANDIDATE`. The contracts are frozen; no Git release tag is
> published by this engineering freeze.

> **2026-09-20 final completeness update:** Direct launcher-board observation and
> AgentSpeak-driven standalone control now PASS. Original Auction plan/deadline
> equivalence remains unsupported (B). The new audit supersedes older adapter-gap
> and component-only claims below; historical results remain historical. See
> [final audit](docs/project/phase20-final-completeness-audit.md).


Current repository state: Maven artifact and plugin descriptor version `1.0.1`.
The historical annotated tag `v1.0.1` points to `7f77b1f4`; current development
has advanced through the Phase 44 V2 freeze. The manifest name `use-jacamo-plugin-v1.0.1`
is not a Git tag in this checkout, so it must not be reported as published.

This release adds conservative, traceable JaCaMo project import and offline/live
verification to USE 7.5.0. It supports the pinned Auction example and the verified
Jason 3.3.0, CArtAgO 3.1 and Moise 1.1 integration scope.

## Install

1. Use JDK 21 and USE 7.5.0.
2. Extract this archive into the USE installation root. This places the plugin JAR
   in `lib/plugins` and the active metamodel/mapping under `Core/*/version-2/`.
   The JAR also embeds byte-identical canonical V2 Ecore, mapping/schema,
   compatibility metadata, and release manifest resources.
3. Make Jason 3.3.0, CArtAgO 3.1, Moise 1.1 and their required runtime dependencies
   available on the USE JVM classpath. They are intentionally not redistributed in
   this archive. The plugin's JSON Schema validator and its dependencies are
   embedded in the plugin JAR.
4. Start USE from the installation root and confirm `Plugins > JaCaMo > Status` or
   run `jacamo status` in the USE shell.

The automated load smoke validates discovery, descriptor parsing, the shell command
and both menu actions. Interactive GUI execution in a separately installed binary
distribution remains a documented manual environment check.

## Workflow

Open `Plugins > JaCaMo > Open Workbench...`, select a `.jcm` entry, inspect import
diagnostics and mapping compatibility, generate the USE model/state, run full
verification, and export JSON or Markdown reports. Runtime verification requires an
explicit connector configuration through the service API; the workbench does not
launch an external `.jcm` application or provide connector configuration fields.
Reconnect performs a full authoritative resync.

If exact typed resolution is ambiguous, place the schema-valid `binding.json` in
the JaCaMo project root. Production import reads and validates it, rejects stale or
invalid entries, and never guesses a target.

The included `examples/auction` project is the release acceptance fixture. See
`docs/user-workflow.md`, `docs/architecture.md`, `KNOWN-LIMITATIONS.md`, and
`compatibility.json` for the exact supported scope.

The historical v1.0.1 hotfix suite was 271/271 tests: 13 in `use-core`, 130 in `use-gui`,
and 128 in `use-plugin` (125 unit/component plus 3 release integration tests).
See `docs/project/00-README.md` for the canonical onboarding path.

## Verify the download

The build produces `use-jacamo-plugin-1.0.1-v2-frozen.zip.sha256` beside the archive. Compare
the first hexadecimal field with a SHA-256 digest of the ZIP before installation.


### Historical Phase 26 runtime contract

Phase 26 froze the historical V1 contract. Active production now selects Metamodel
V2, Structural Mapping 2.2.0 and Runtime Mapping V2/schema 3.0.0 with exact frozen hashes.
The historical audit remains at
docs/project/phase26-runtime-mapping-audit.md. Standalone/NPL limitations are
unchanged unless a later evidence document explicitly promotes them.
