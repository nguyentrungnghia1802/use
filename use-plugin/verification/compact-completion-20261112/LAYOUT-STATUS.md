# Final layout and runnable package

Date: 2026-11-12. Functional baseline: USE commit `2020712e`.

## Organization

- Plugin-owned contract, producer adapter and native engine modules moved from the
  USE root to `use-plugin/modules/`. Artifact IDs and installed JAR names are stable.
- Compact runtime implementation moved into `semantics.mapping.compact.runtime`;
  compiler/schema/catalogue entry types remain in `semantics.mapping.compact`.
  Compiler version is 3.2.0 and implementation identities fence old caches.
- Reactor parents, runtime bootstrap, package tools, assembly paths, active tests
  and sibling JaCaMo dependency-build tools use the new module locations.
- The current plugin README is a concise English entry point. Its previous content
  remains in `docs/history`. Ownership and directory purposes are documented in
  `docs/WORKSPACE-LAYOUT.md` under the plugin root.
- `LAYOUT-MOVES.json` records preservation of 2,031 files / 1,989,973,680 bytes of
  workspace logs, failure reports and accidental nested runtime output. No reset,
  clean, recording rewrite or branch deletion was performed.

## Focused evidence

- `layout-acceptance.log`: 40 tests across the three selected modules; zero
  failures/errors/skips. Includes original engine source-integrity checks,
  bootstrap at the new paths, native scope/Task and checkpoint tests, all active
  example imports, and the unchanged external Auction through actual TCP.
- `LAYOUT-ACCEPTANCE.json`: two exact terminal scope approvals, 28 automatic
  releases, zero manual acknowledgements. This is service-level acceptance.
- `final-layout-package.log`: successful final reactor package, with tests already
  proven by the preceding focused invocation. No full clean verify was run.
- Packaging guard: two Python tests prove current dependency parity and rejection
  of changed installed/producer framework binaries. Source locks are not rewritten.
- The installed dependency lock had stale hashes even though the framework JARs
  already matched the reviewed source build. The updater now refreshes that lock
  only after all reviewed source, producer-build and installed binaries agree.
  The installed launcher classpath preparation now succeeds.
- `installed-layout-probe.log`: a fresh headless JVM loads compact runtime,
  neutral contract and engine classes from the installed plugin JAR, and loads
  JaCaMo/Jason/CArtAgO/NPL from the installed runtime directory. The required
  native adapter method and compiler version 3.2.0 resolve successfully.

The installed plugin SHA-256 is
`490472004d2de773826857aba31eded0095a9fdc00df1be79f11649a394f08c3`.
The actual JaCaMo JAR SHA-256 remains
`4e446dbc8ea2fd41f98a398204fd2d5a9bbe694ab44493012246557c5838e27e`,
matching the sibling source build, tested producer dependency and installed JAR.

Launcher:
`use/use-assembly/target/new/bin/start_use.bat` relative to the thesis workspace.
Authored case definitions/conditions and ASL behavior were not changed by this
organization pass. Existing historical evidence remains separate from fresh
acceptance. No manual desktop/Computer Use acceptance is claimed.
