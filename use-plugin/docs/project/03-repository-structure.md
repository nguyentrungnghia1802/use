# Repository Structure

This document describes the checked-in structure at v1.0.1. It is not a proposed
layout. Generated files under `target/` are build outputs and are not sources of truth.

## Reactor

```text
use/
├── pom.xml
├── use-core/
├── use-gui/
├── use-assembly/
├── manual/
├── documentation/
├── docs/report/
└── use-plugin/
```

The root Maven reactor builds five projects: the root POM, `use-core`, `use-gui`,
`use-assembly`, and `use-plugin`.

## Plugin module

```text
use-plugin/
├── pom.xml
├── README.md
├── CHANGELOG.md
├── KNOWN-LIMITATIONS.md
├── compatibility.json
├── NOTICE
├── licenses/
├── Core/
│   ├── Metamodel/
│   │   ├── JaCaMo-Metamodel.ecore
│   │   ├── Metamodel-2024.jpg
│   │   └── README.md
│   └── Mapping/
│       ├── jacamo-use-mapping-v1.json
│       ├── jacamo-use-mapping.schema.json
│       ├── freeze-manifest.json
│       ├── METAMODEL-MAPPING-AUDIT.md
│       └── README.md
├── docs/
│   ├── DOCUMENTATION-MANIFEST.json
│   ├── project/
│   ├── agent/
│   │   └── tasks/task-01.md
│   └── superpowers/plans/
├── release/
│   ├── release-manifest.json
│   ├── HOTFIX-1.0.1.md
│   └── evidence/v1.0.1/
└── src/
    ├── assembly/release.xml
    ├── main/java/org/tzi/use/plugins/jacamo/
    ├── main/resources/
    └── test/
```

## Production packages

| Package | Responsibility |
| --- | --- |
| root plugin package | facade, plugin registration, command/actions |
| `project` | `.jcm` discovery and project graph |
| `extraction` | static Jason/CArtAgO/Moise extraction and semantic resolution |
| `semantic` | source-independent semantic model and stable IDs |
| `binding`, `resolution` | schema-valid explicit binding and exact typed resolution |
| `mapping` | frozen mapping load, validation, transformation planning |
| `materialization` | textual and direct USE model/state materialization |
| `constraint`, `ocl` | supported expression extraction and OCL generation/loading |
| `trace` | source-to-USE trace and runtime aliases |
| `verification` | constraint registry, offline/runtime checks and reports |
| `runtime` | connectors, ordered queue, mutations, snapshots and lifecycle |
| `ui` | facade-backed Swing workbench |
| `evidence` | deterministic acceptance evidence helpers |

## Resources and project-local inputs

The plugin descriptor is `src/main/resources/useplugin.xml`. Canonical Ecore,
mapping, compatibility, release manifest, schemas, core OCL, and verification
profile are also embedded as classpath resources during packaging.

A JaCaMo project may contain:

```text
<project-root>/
├── <project>.jcm
├── src/...
├── binding.json                    # optional exact ambiguity resolution
└── verification/
    └── <project-id>.ocl            # optional case OCL
```

`binding.json` is at project root, not under `verification/`. Production import
loads it automatically when present. A user-selected OCL file is separate from the
automatic case profile.

## Test and evidence boundaries

`src/test/resources/auction/` is the pinned acceptance fixture. It may demonstrate
a supported path without establishing generic support. `release/evidence/v1.0.1/`
is retained release evidence; it does not replace a fresh test run when current
verification is required. `docs/agent/tasks/task-01.md` and `docs/superpowers/plans/`
are historical execution records, not active architecture specifications.
