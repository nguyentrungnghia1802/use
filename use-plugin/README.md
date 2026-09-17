# USE JaCaMo Plugin 1.0.1

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
explicit connector configuration; reconnect performs a full authoritative resync.

The included `examples/auction` project is the release acceptance fixture. See
`docs/user-workflow.md`, `docs/architecture.md`, `KNOWN-LIMITATIONS.md`, and
`compatibility.json` for the exact supported scope.

## Verify the download

The build produces `use-jacamo-plugin-1.0.1.zip.sha256` beside the archive. Compare
the first hexadecimal field with a SHA-256 digest of the ZIP before installation.
