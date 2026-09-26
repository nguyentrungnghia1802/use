# USE JaCaMo Plugin 1.0.1

> **V2 frozen release candidate:** Metamodel V2 and Mapping 2.2.0 drive IR,
> extraction, transformation, trace, OCL and runtime target binding. Independent
> directional order is represented by generic target-only ranks. Phase 35 clean
> acceptance is 350/350 PASS; the final Phase 44 reactor and relocated checkout are
> each 374/374 PASS with zero skips. See
> [transformation](docs/project/v2-migration/phase35-transformation.md) and
> [final freeze evidence](docs/project/v2-migration/phase44-final-v2-freeze.md).
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

This release makes official JaCaMo objects, exported through the neutral Bridge,
the only production semantic authority for USE 7.5.0. Custom parser and in-process
connector implementations remain historical test evidence and are absent from the
release JAR.

## Install

1. Use JDK 21 and USE 7.5.0.
2. Extract this archive into the USE installation root. This places the plugin JAR
   in `lib/plugins` and the active metamodel/mapping under `Core/*/version-2/`.
   The JAR also embeds byte-identical canonical V2 Ecore, mapping/schema,
   compatibility metadata, release manifest and unified freeze manifest resources.
3. Add `bridge/lib/jacamo-bridge-contract-1.0.0.jar` and
   `bridge/lib/jacamo-bridge-jacamo-1.0.0.jar` to the JaCaMo application's
   classpath. JaCaMo/Jason/CArtAgO/Moise dependencies stay in that process and do
   not enter the USE JVM.
4. Create a 32-byte-or-longer random secret encoded as hexadecimal in a regular,
   non-symlink file readable only by the two local processes. Configure the JCM's
   official `platform:` entry with
   `org.jacamo.bridge.adapter.JaCaMoBridgePlatform("port=7777", "secretFile=/absolute/path/bridge-secret.hex", "distributionSha256=<64-lowercase-hex>")`.
   Add `org.jacamo.bridge.adapter.BridgeAgArch` to agents whose Jason lifecycle
   evidence is required.
5. Start USE with matching properties:
   `-Duse.jacamo.bridge.endpoint=tcp://127.0.0.1:7777`,
   `-Duse.jacamo.bridge.secret-file=/absolute/path/bridge-secret.hex`, and
   `-Duse.jacamo.bridge.distribution-sha256=<same-64-lowercase-hex>`.
6. Confirm `Plugins > JaCaMo > Status` or run `jacamo status`. Missing Bridge,
   schema/distribution mismatch, authentication failure, or selected-project
   mismatch fails explicitly; there is no parser fallback.

The automated load smoke validates discovery, descriptor parsing, the shell command
and both menu actions. Interactive GUI execution in a separately installed binary
distribution remains a documented manual environment check.

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
