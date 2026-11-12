# USE JaCaMo Plugin 1.0.1

The active workflow is **Prepare + Import -> Start -> Verified checkpoints**.
Select the project's `.jcm` and its `.mrv` system specification. The `.mrv` names
its CSL, iStar, compact XML and optional OCL inputs. JaCaMo owns execution;
USE evaluates the authoritative native state and preserves exact-cut history.
Continue reviews the active held checkpoint without changing its verdict.

## Start here

- [Repository layout and ownership](docs/WORKSPACE-LAYOUT.md)
- [Compact runtime and semantic boundaries](docs/project/compact-runtime.md)
- [Build and refresh the runnable installation](tools/RUNNABLE-PACKAGE.md)
- [Verified checkpoint protocol](docs/project/verified-checkpoints.md)
- [Cleanup scope and validation](docs/project/current-cleanup.md)

## Directory guide

| Directory | Purpose |
| --- | --- |
| `src/main` | Plugin integration, source compiler, native projection, runtime coordination and UI. |
| `src/test` | Component/integration tests and isolated reference fixtures. |
| `modules/bridge-contract` | Framework-neutral protocol, identities and evidence contracts. |
| `modules/bridge-jacamo` | Official Jason, CArtAgO, MOISE and NPL adapters and producer bootstrap. |
| `modules/semantic-engine` | Pinned native CSL/iStar engine plus its neutral USE adapter. |
| `examples` | Authored case inputs and original case source programs. |
| `Core` | Theory/reference diagrams and source metamodel material. |
| `tools` | Stable public build, model-maintenance and verification entry points. |
| `release`, `licenses` | Distribution description and license notices. |
| `docs` | Architecture and operational guidance. |
| `verification`, `evidence` | Disposable run output; not runtime inputs. |
| `target` | Generated build artifacts and owned runtime work. |

Run Maven from the USE repository root; the command remains `-pl use-plugin -am`.
Module artifact IDs and the installed `bin/start_use.bat` path are unchanged.
No source case is inferred from a directory name, and the compact workflow has no
legacy XML fallback. Unsupported evidence remains unknown or fails admission.

The active examples select only their compact XML through `.mrv`. Legacy XML in
isolated test fixtures supports regression checks and is not an active-example
fallback. Old run output and package backups are disposable.
