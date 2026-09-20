# USE JaCaMo Plugin 1.0.1

> **2026-09-20 final completeness update:** Direct launcher-board observation and
> AgentSpeak-driven standalone control now PASS. Original Auction plan/deadline
> equivalence remains unsupported (B). The new audit supersedes older adapter-gap
> and component-only claims below; historical results remain historical. See
> [final audit](docs/project/phase20-final-completeness-audit.md).


Current repository state: Maven artifact and plugin descriptor version `1.0.1`.
The historical annotated tag `v1.0.1` points to `7f77b1f4`; current development
has advanced through the final runtime mapping and engineering hardening phases. The manifest name `use-jacamo-plugin-v1.0.1`
is not a Git tag in this checkout, so it must not be reported as published.

This release adds conservative, traceable JaCaMo project import and offline/live
verification to USE 7.5.0. It supports the pinned Auction example and the verified
Jason 3.3.0, CArtAgO 3.1 and Moise 1.1 integration scope.

## Install

1. Use JDK 21 and USE 7.5.0.
2. Extract this archive into the USE installation root. This places the plugin JAR
   in `lib/plugins` and the frozen metamodel/mapping under `Core`.
   The JAR also embeds byte-identical canonical Ecore, mapping, freeze manifest,
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

The build produces `use-jacamo-plugin-1.0.1.zip.sha256` beside the archive. Compare
the first hexadecimal field with a SHA-256 digest of the ZIP before installation.


### Phase 26 runtime contract

Final metamodel remains unchanged canonical V1. Runtime Mapping V1 is frozen
with schema 2.0.0 and exact resource hashes; legacy draft schema 1.0.0 is rejected
with RUNTIME_MAPPING_VERSION_UNSUPPORTED. See docs/project/phase26-runtime-mapping-audit.md.
Supported runtime semantics and standalone/NPL limitations are unchanged.
