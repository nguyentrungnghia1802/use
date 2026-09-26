# Test and regression strategy

## Recorded audit baseline

| Scope | Command/source | Result on 2026-09-26 |
|---|---|---|
| USE Maven reactor | `mvn -B -pl use-plugin -am test` from `use` | `use-core`: 12 passed; `use-gui`: 1 passed; `use-plugin`: 228 run, 3 failures, 0 errors, 0 skipped |
| Plugin failures | Surefire reports | `GoldenPipelineTest` digest mismatch; `ConstraintClosureTest` expected 2 but got 0; `InstanceMaterializationTest` golden digest mismatch |
| JaCaMo | audited Gradle tests | 6/6 passed |

The three Maven failures belong to the pre-existing dirty frontend working state. They are not accepted regressions, but this audit does not modify production code or goldens to hide them. The implementation must first reproduce and classify them against the preserved baseline.

JaCaMo's `build.gradle` executes `ant.fixcrlf(...)` while configuring `fixTab`, which can touch checkout line endings. The audit restored the one observed content change and verified a clean content diff. Future dependency tests should run in an isolated copy/worktree or use non-mutating compiled artifacts; test execution must not rewrite the authoritative JaCaMo source checkout.

## Test classification

| Class | Examples/scope | Migration treatment |
|---|---|---|
| Frozen acceptance | V2 Ecore/mapping/runtime mapping/freeze/hash tests | Must remain unchanged and pass in every implementation phase |
| Reusable backend | planner, projections, materialization, trace, OCL, verification | Retain; parameterize with Bridge-derived fixtures where useful |
| Legacy frontend | JCM lexer/parser, source extractor, XML parser/resolver tests | Freeze as shadow oracle; do not delete until replacement gates and approval |
| Current in-process runtime | connector/mirror/mutation tests | Retain algorithm tests; split authority observation into Bridge tests and client contract tests |
| Reduced case/control | local Auction, CounterTeam, programmatic live Auction | Keep scope labels; never promote to upstream acceptance |
| Canonical case | Hello/Auction/House source manifests and future integration tests | New primary migration gates |
| Contract/API drift | new official API signatures/capabilities/schema compatibility | New mandatory tests |
| Non-functional | load/performance/security/determinism/thread leaks | New release gates after functional correctness |

## Test pyramid

### Bridge unit tests

- official-object adapter fixtures for JCM, Jason AST, CArtAgO descriptors and Moise OS;
- canonical ID generation and source provenance;
- immutable contract serialization, schema validation and canonical digests;
- capability/completeness behavior for unavailable fields;
- exact `GroupRoleCardinality` and `ParentSubGroupCardinality` tuples, including repeated endpoints with different contextual bounds and lossy-projection diagnostics;
- per-source event sequencing and listener payload conversion;
- no dependency on USE/EMF in the neutral contract, no live JaCaMo/Jason/CArtAgO/Moise object fields and deterministic serialization without a shared classpath.

### API drift tests

- compile adapters against the exact supported JaCaMo distribution graphs;
- reflective signature assertions for required public classes/methods;
- launcher/Platform/AgArch lifecycle integration;
- descriptor/logger/NPL listener capability probes;
- fail with an actionable report when a required API changes—never silently fall back to text parsing.

At minimum test the frozen-pinned JaCaMo 1.3.0/Jason 3.3.0 graph if still supported and the audited JaCaMo 1.3.1/Jason 3.3.2 graph. CArtAgO 3.1/Moise 1.1 remain explicit coordinates.

### Contract/client tests

- compatible and incompatible schema/version negotiation;
- valid/invalid references, digests and capability sets;
- old session/generation/model-revision rejection;
- duplicate/idempotent and conflicting duplicate events;
- bounded buffers, gaps, acknowledgements and resume tokens;
- per-runtime-fact projection status (`MATERIALIZED_FAITHFULLY`, `EVIDENCE_ONLY`, `UNAVAILABLE`) with full identity/provenance retained;
- record/replay from canonical fixtures independent of live JaCaMo.

### USE adapter/backend tests

- ModelSnapshot -> `JaCaMoSemanticModel` parity;
- deterministic transformation plan and USE MModel compilation;
- RuntimeSnapshot -> transactional MSystemState replacement;
- RuntimeEvent -> Runtime Mapping V2 -> mutation engine;
- captured commitment/goal-state/norm-instance/scheme/group-instance facts remain evidence-only when no faithful target mapping exists, and dependent OCL is `INCONCLUSIVE`/`NOT_EVALUATED`;
- TraceIndex round-trip and sanitized-name collisions;
- missing facts stay unresolved/inconclusive;
- text/direct backend parity.

### Bridge integration tests

- real official JaCaMo launcher with `JaCaMoBridgePlatform`;
- dynamic agent/artifact/board recreation;
- action/operation success/failure correlation;
- belief/goal/property/role/mission/norm snapshots and events;
- snapshot during concurrent changes, retry and resync;
- clean start/stop and no listener/thread leak.

### Early separate-JVM smoke gate (Phase D)

- Process A runs `JaCaMo + Bridge`; Process B runs `USE + BridgeClient` over a minimal/test transport or process-neutral recorded/framed mechanism.
- `ModelSnapshot` crosses the boundary and builds an MModel; `RuntimeSnapshot` initializes/replaces MSystemState; `RuntimeEvent` crosses and applies correctly.
- No live Java/JaCaMo object crosses the boundary, and USE production-side semantic backend needs no JaCaMo runtime classes or shared classpath.
- Restarting Bridge/JaCaMo creates a new session; old-session/old-generation events are rejected.
- Basic reconnect/resnapshot succeeds; schema/contract/version mismatch fails closed.

### Production transport hardening (Phase H)

- benchmark candidate transports and select one through an ADR without changing the neutral semantic contract;
- authenticate, add TLS where remote, enforce bounded backpressure and resume/ack;
- reject malformed/untrusted/fuzzed/oversized payloads;
- kill/restart either process, inject partitions/overflow and prove resume or full resync;
- verify packaging, performance, no classpath/live-object sharing and deterministic replay of captured traffic.

### Canonical case tests

For each of Hello, Auction and House:

1. verify all source hashes;
2. official-load canonical model digest;
3. explicit semantic inventory and unsupported diagnostics;
4. V2/USE compilation, trace and materialization;
5. live snapshot and supported event lifecycle;
6. Core + case OCL positive and negative scenario;
7. reconnect/resync;
8. repeated-run determinism;
9. source scan showing no generic code case constants.

## Snapshot/event race matrix

| Race/failure | Expected assertion |
|---|---|
| Event arrives while snapshot copies state | Buffered and replayed strictly after accepted watermark or snapshot retried |
| Artifact disposed/recreated with same name | Two identities; old event rejected |
| Agent killed/recreated with same name | Two incarnations; roles/actions do not leak |
| Model descriptor appears during snapshot | New model revision required before state/event apply |
| Listener queue overflows | Explicit gap; no further delta application until resync |
| Disconnect after snapshot before acknowledgement | Idempotent retry; no duplicate USE objects |
| Cross-source clocks disagree | Ordering remains partial; timestamp does not decide semantics |
| Unsupported event kind | Quarantine/capability failure, not ignored mutation |

## Golden policy

- Do not edit goldens merely because the new path differs.
- Produce a semantic diff: official source fact, legacy fact, Bridge fact, V2 plan, USE output and reason.
- Approve a new golden only after source authority proves the legacy output wrong/incomplete and frozen mappings remain unchanged.
- Keep old goldens under a labelled legacy evidence path through deprecation.
- Hash manifests are regenerated only by an explicit reviewed acceptance step.

## Regression commands/gates

Implementation phases must define reproducible wrappers, but the mandatory scopes are:

1. focused changed-module tests;
2. Bridge unit/API/integration suite;
3. `mvn -B -pl use-plugin -am test`;
4. JaCaMo integration tests in a content-isolated checkout/distribution;
5. all frozen hash/audit gates;
6. three canonical cases;
7. mandatory Phase-D separate-JVM boundary smoke, plus the Phase-H production transport/reconnect/security suite when transport hardening is in scope;
8. deterministic record/replay and evidence schema validation.

No unexpected skip, ignored failure, assertion weakening or fallback path is allowed. Known baseline failures must be explicitly classified and closed or carried as release blockers; they cannot disappear through a golden update alone.

## Evidence outputs

Each test run records Git revisions/dirty-state fingerprint, dependency graph, contract/build versions, command, OS/JDK, counts, duration, skipped tests, source hashes, capabilities, model/snapshot/report digests and logs. Evidence distinguishes:

- clean-build/scripted control;
- official source/static coverage;
- initialized/runtime supported subset;
- complete original-case semantics;
- explicit unsupported/unknown boundaries.

## Acceptance rule

The migration is accepted only when the new official path passes all applicable gates, legacy-versus-new differences are reviewed, frozen artifacts are byte-identical, and canonical-case claims match the evidence scope. A green supported-subset control never substitutes for original-case E2E evidence.
