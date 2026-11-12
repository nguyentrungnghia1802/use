# Workspace layout and ownership

Updated 2026-11-12. Run build commands from the `use` repository root.

## Workspace

| Location | Owner and purpose |
| --- | --- |
| `use/` | USE application and plugin reactor. |
| `use/use-plugin/` | All plugin-specific implementation, integration modules, authored examples, tools and documentation. |
| `jacamo/` | Independent JaCaMo runtime repository and reviewed checkpoint dependency build tools. |
| `checkpoint-deps/` | Managed CArtAgO/NPL source checkouts used by the pinned runtime build; not disposable output. |
| `use4Goal/` | Independent upstream/theory reference repository; not an implicit runtime dependency. |
| `work/` | Workspace work products and preserved historical logs/reports. |

Independent Git repositories retain their original roots and remotes. Runtime
source provenance and user-authored case paths are preserved.

## Plugin reactor modules

The former USE-root directories `jacamo-bridge-contract`,
`jacamo-bridge-jacamo` and `use-jacamo-semantic-engine` now live under:

```text
use-plugin/modules/
  bridge-contract/   # neutral protocol and evidence types
  bridge-jacamo/     # official producer adapters and bootstrap
  semantic-engine/   # pinned original CSL/iStar engine and neutral adapter
```

Their Maven artifact IDs, Java packages, JAR filenames and installed distribution
paths are unchanged. The root reactor and explicit parent paths declare the new
locations. To address a single moved module, use the artifact selector, such as
`-pl :jacamo-bridge-jacamo`, rather than an obsolete directory name.

The pinned `org.vnu.sme.goal` sources retain their original package structure and
source-integrity checks. Reorganizing plugin ownership does not authorize editing
the original recurrence or semantic assertions.

## Main Java responsibilities

Under `org.tzi.use.plugins.jacamo`:

| Package | Responsibility |
| --- | --- |
| Root facade / owned runtime | Application lifecycle, import orchestration and producer ownership. |
| `codegrounded.use`, `codegrounded.trace` | Native USE construction, physical identity bindings and provenance. |
| `codegrounded.runtime` | Single writer, checkpoints, exact PRE/POST journal, durable history and replay. |
| `semantics.mapping.compact` | Compact grammar, source/target references, catalogues, validation, typed compiler and native preparation. |
| `semantics.mapping.compact.runtime` | Source-cut decoding, physical projection, invocation scopes/demands, typed observers and Task bindings. |
| `semantics.mapping.ocl`, `semantics.ocl` | Authored/native OCL compilation, exact bindings and coverage evidence. |
| `semantics.mapping.xml` | Explicit legacy workflow and shared typed framework decoders. Compact runtime does not load its historical case XML. |
| `semantics.runtime` | Original engine session ownership, occurrence expansion and result/evidence adaptation. |
| `ui` | Swing presentation and user interaction; no domain verifier or checkpoint authority. |

Compiler entry types remain in the compact package. Runtime implementation types
are separated without introducing a second state model. Compiler version 3.2.0
and implementation class identities invalidate incompatible compiled caches.

## Tools and generated material

`tools/` retains stable public entry points; their purpose is explicit:

- Runtime: `jacamo-bridge.ps1`, `prepare-runtime-classpath.ps1`.
- Packaging: `update-runnable-plugin.py`, `test_update_runnable_plugin.py`.
- Authored model maintenance: `refresh-authored-hashes.py`.
- Evidence validation: `semantic-acceptance.py`, `compact-verification-evidence.py`, `tests/`.

Build outputs stay in each module's `target/`. The runnable distribution remains
`use-assembly/target/new`; installed launchers and runtime paths do not follow the
source module moves. Pass `--no-backup` to the validated updater when old binary
backups are not wanted. Existing backups and historical run directories are
disposable; see `project/current-cleanup.md` for the current removal limitation.

Historical workspace `build-log/` and `failure-reports/` moved into
`work/history/layout-20261112/`. The accidental nested
`use-plugin/use-plugin/` runtime output moved into
`use-plugin/target/history/layout-20261112/legacy-working-directory/`.
`verification/compact-completion-20261112/LAYOUT-MOVES.json` records old/new paths
and verified file counts/byte totals. No recording content or verdict was rewritten.

`verification/` and `evidence/` preserve chronological receipts and replay material.
They are not compiler inputs, application configuration or alternative mapping
sources. Existing historical paths recorded inside signed evidence remain history;
use the move index to locate archived files.

The previous plugin README is in `docs/history/`. Current instructions begin at
the plugin README and `tools/RUNNABLE-PACKAGE.md`.
