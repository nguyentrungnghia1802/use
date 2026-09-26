# USE–JaCaMo architecture realignment audit

Status: **AUDIT COMPLETE — IMPLEMENTATION NOT STARTED — AWAITING USER APPROVAL**
Audit date: 2026-09-26
Decision baseline: `AR-2026-09-26`
Overall verdict: **FEASIBLE_WITH_ADAPTER**

## Scope and authority

This set is the implementation hand-off requested by `AI_AUDIT_REALIGN_USE_JACAMO.md`. It audits the current checkout without changing production code, frozen V2 resources, runtime mappings, hashes, golden files, or tests. Claims are ranked by: production source, executable tests/examples, versioned dependency source/API, active documentation, historical documentation, then explicitly labelled inference.

The inspected source baselines are:

| System | Exact inspected baseline | Build/API note |
|---|---|---|
| USE | branch `codex/phase-45-canonical-case-studies`, `215784b648a906e4db6086352946aba5bda10f98` | Maven; USE `7.5.0`; plugin `1.0.1`; Java 21 |
| JaCaMo | branch `main`, `3866858a7ebf6be85d9199c13a09cf4bfb8191be` | Gradle; declares `1.3.1`; this revision is two commits after tag `v1.3.1` (`3e48d3b`) |
| Jason | `io.github.jason-lang:jason-interpreter:3.3.2` | Exact source jar SHA-256 `2f4e450d1fb116a9bf2d4ab7eddb2265b82fa4a303a97a64570c67710e2d1ce0` |
| CArtAgO | tag `v3.1`, `440cd41c1810ceef6a627477c461776b3200236b` | Official repository `CArtAgO-lang/cartago` |
| Moise | tag `v1.1`, `c68d4b7068c56b7a42e657bdc160f55f2d366ea8` | Official repository `moise-lang/moise` |
| NPL | `org.jacamo:npl:0.6.1` | Public bytecode API inspected with `javap`; source is an external JaCaMo dependency |

The current USE plugin itself pins Jason `3.3.0`, CArtAgO `3.1`, and Moise `1.1`. The frozen V2 release manifest pins JaCaMo `1.3.0`/Jason `3.3.0`, while the actual JaCaMo workspace uses declared JaCaMo `1.3.1`/Jason `3.3.2`. This is a required API-drift gate, not a reason to edit frozen hashes.

## Executive decisions

1. JaCaMo/Jason/CArtAgO/Moise remain semantic authorities. Ecore V2 is the target vocabulary and conformance schema, not a source parser.
2. The preferred bridge is an independent JaCaMo-side module loaded through the official `jacamo.platform.Platform` extension point and a Jason `AgArch` observer. It may also expose a load-only entry point. No JaCaMo core patch is justified by inspected evidence.
3. `ModelSnapshot`, `RuntimeSnapshot`, and `RuntimeEvent` are separate, versioned neutral contracts. Transport remains deliberately undecided.
4. The current semantic IR, frozen V2 metamodel/mappings, transformation, materialization, trace, OCL, verification, and most runtime mutation/mirror logic remain valuable. The source-authority frontend and in-process connector boundary are what change.
5. A global atomic snapshot is not exposed by the four official subsystems. The achievable contract is a buffered, validated consistent cut with per-subsystem watermarks, gap detection, and authoritative resync.
6. No V2 gap blocks this architecture migration, but frozen V2 is not claimed to be a 100% faithful representation of every official fact. In particular, Moise role and subgroup cardinalities are relation-scoped while V2 stores intrinsic-looking attributes; the Bridge preserves the exact relation facts and marks any context-losing V2 projection `SUPPORTED_SUBSET`/`REPRESENTATION_LOSS`.
7. Runtime mission commitments, organizational-goal state, norm lifecycle and group/scheme instance context remain exact Bridge evidence even when no faithful frozen-V2/USE runtime representation exists. OCL may depend on them only after faithful materialization into `MSystemState`; otherwise the affected result is capability-blocked, `INCONCLUSIVE` or `NOT_EVALUATED`.
8. Process independence is proved early: Phase B makes the contract classpath-neutral and deterministic, and Phase D runs a mandatory separate-JVM smoke gate. Phase H still selects and hardens the production transport.
9. Legacy extraction stays available through shadow comparison and is deprecated only after official-load parity gates pass for Hello World, Auction, and House-Building.

## Workspace inventory

USE contains Maven modules `use-core`, `use-gui`, `use-plugin`, and `use-assembly`. The plugin has 165 production Java files, 65 test Java files, 27 current test resources, 172 `docs/project` files, and packages `binding`, `constraint`, `diagnostics`, `extraction`, `mapping`, `materialization`, `ocl`, `project`, `resolution`, `runtime`, `semantic`, `trace`, `ui`, and `verification`.

JaCaMo contains 24 handwritten main Java files plus the JavaCC JCM grammar/generated parser, eight test Java sources, and three top-level examples. Jason, CArtAgO, Moise, NPL, JaCa and other platform code are external dependencies; they are not duplicated in the JaCaMo repository.

Frozen active assets are:

| Asset | Version/status | SHA-256 |
|---|---|---|
| `Core/Metamodel/version-2/jacamo_v2_complete.ecore` | V2 frozen; 7 enums, 21 classes, 48 attributes, 37 references, 3 opposite pairs | `4ae51638a078f0a933982844063993084f17d420694ac8a868d95b9df063c35c` |
| `Core/Mapping/version-2/jacamo-use-mapping-v2.json` | Mapping `2.2.0`, frozen | `fc03b90cf0729260747bfeffa6a6cd463eefd2259c0c3cd60ed22bd140ec48b1` |
| `runtime/jacamo-use-runtime-mapping-v2.json` | Runtime Mapping `2.0.0`, schema `3.0.0`, frozen | `5b2c00f052010fb35a71eb7f50ae4650f8a09c7647332b47a7199c12cbf8a5f0` |
| `release/v2-freeze-manifest.json` | frozen release candidate, not tagged | manifest remains unchanged |

## Executed baseline

| Command | Result |
|---|---|
| `mvn -B -pl use-plugin -am test` | build reached all four modules; `use-core` 12/12 pass, `use-gui` 1/1 pass, `use-plugin` 228 tests with 3 failures, 0 errors, 0 skipped |
| `gradlew.bat test --no-daemon` in JaCaMo | 6/6 tests pass (`JaCamoProjectTest`, `WorkspaceCreationTest`) |
| JaCaMo `compileClasspath` resolution | succeeds; confirms Jason 3.3.2, CArtAgO 3.1, JaCa 3.1, Moise 1.1, NPL 0.6.1 |

The three plugin failures are preserved as input evidence: two assertions expose changed Auction golden digests (`GoldenPipelineTest`, `InstanceMaterializationTest`), and `ConstraintClosureTest` expects two constraints but receives zero. They occur in the pre-existing dirty frontend work and were not repaired or hidden during this audit.

JaCaMo's `fixTab` task executes `ant.fixcrlf` during Gradle configuration (statements are in the task body rather than an action). Running Gradle therefore touched line endings; the one content-level newline delta was restored and `git diff --quiet` was clean afterwards. The build-side effect is recorded as risk `R-12`.

## Document set

| File | Purpose |
|---|---|
| [01-current-architecture-audit.md](01-current-architecture-audit.md) | Exact current static/runtime call paths and boundary diagnosis |
| [02-target-architecture.md](02-target-architecture.md) | Target understanding and authority boundaries |
| [03-jacamo-project-api-inventory.md](03-jacamo-project-api-inventory.md) | Official JCM/project/load/launch lifecycle |
| [04-jason-api-inventory.md](04-jason-api-inventory.md) | Jason parser, AST, state, and listeners |
| [05-cartago-api-inventory.md](05-cartago-api-inventory.md) | Workspace/artifact/operation/property/runtime API |
| [06-moise-api-inventory.md](06-moise-api-inventory.md) | OS, runtime boards/OE, and NPL lifecycle |
| [07-static-runtime-availability-matrix.md](07-static-runtime-availability-matrix.md) | Parse/initialized/runtime availability |
| [08-jacamo-to-v2-coverage.md](08-jacamo-to-v2-coverage.md) | Official concepts to all V2 classes/features |
| [09-bridge-contract-design.md](09-bridge-contract-design.md) | Bridge options and neutral snapshot/event contract |
| [10-identity-trace-contract.md](10-identity-trace-contract.md) | Canonical identity, reconnect, and trace chain |
| [11-use-component-disposition.md](11-use-component-disposition.md) | KEEP/REFACTOR/REPLACE/deprecation table |
| [12-metamodel-mapping-impact.md](12-metamodel-mapping-impact.md) | Frozen V2 impact and versioning policy |
| [13-runtime-architecture.md](13-runtime-architecture.md) | Consistent-cut, ordering, backpressure, resync |
| [14-ocl-verification-impact.md](14-ocl-verification-impact.md) | MModel/MSystemState/OCL and PRE/POST strategy |
| [15-case-study-migration.md](15-case-study-migration.md) | Hello/Auction/House evidence-driven migration |
| [16-test-strategy.md](16-test-strategy.md) | Regression matrix and acceptance gates |
| [17-migration-roadmap.md](17-migration-roadmap.md) | Phase A–K implementation-ready roadmap |
| [18-risk-blocker-register.md](18-risk-blocker-register.md) | Risks, blockers, mitigations, owners/gates |
| [19-definition-of-done.md](19-definition-of-done.md) | Audit and later implementation completion gates |
| [20-final-feasibility-verdict.md](20-final-feasibility-verdict.md) | Capability verdict and answers to the 60 questions |

## Consistency rules for implementation

- `AR-01`: Bridge-side dependencies match the launched JaCaMo distribution; USE-side code depends only on the neutral contract.
- `AR-02`: No name similarity, filename inference, or silent fallback can create a semantic relation.
- `AR-03`: Every emitted field records an official API/source evidence key or an explicit `UNAVAILABLE` capability.
- `AR-04`: Snapshot completeness and consistency are data, never assumptions.
- `AR-05`: Runtime events are accepted only for the active `(sessionId, generation)` and after exact identity binding.
- `AR-06`: A norm is not OCL. OCL remains authored/validated verification policy.
- `AR-07`: Frozen V2 files and hashes remain byte-identical until a separately approved, evidence-backed version proposal.
- `AR-08`: Legacy frontend removal requires proven replacement, rollback evidence, and user approval.
- `AR-09`: Exact relation-scoped cardinalities remain keyed by both endpoints in Bridge/provenance; a context-losing V2 projection is never labelled `EXACT` or chosen by arbitrary merge.
- `AR-10`: Capture in `RuntimeSnapshot`/`RuntimeEvent` does not make a fact OCL-queryable. Only a faithful `MSystemState` projection enables OCL dependencies on that fact.
- `AR-11`: The first separate-JVM proof occurs in Phase D with a minimal/test boundary; Phase H productionizes transport without changing semantic contracts.

## Stop condition

This audit ends at documentation and planning. The next action is user review. No bridge, adapter, parser deletion, resource migration, or production refactor is authorized by this document set.
