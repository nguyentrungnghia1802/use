# Testing and Quality Strategy

> Current V2 target contract: [Phase 35 reconciliation](v2-migration/phase35-runtime-targets.md). Default runtime loading uses Mapping V2 fingerprints and schema 3.0.0; older frozen V1 results below are historical. Full clean reactor PASS 350/350; see Phase 35 acceptance for workflow closure.

## 1. Principle

Mỗi phase phải có test trước khi merge.
Không chấp nhận "GUI chạy được" là evidence duy nhất.

---

## 2. Test pyramid

### Unit
- source key parsing;
- mapping schema;
- type mapping;
- multiplicity;
- name sanitizer;
- expression translation;
- trace lookup;
- event normalization.

### Parser fixtures
- valid/minimal;
- invalid syntax;
- include;
- duplicate symbols;
- ambiguous references;
- edge cases mỗi dimension.

### Contract tests
- Ecore ↔ mapping coverage;
- mapping ↔ USE target validity;
- semantic IR invariants;
- generated OCL compile.

### Golden tests
Input project → exact deterministic:
- `.use`;
- `.cmd`;
- generated OCL;
- trace;
- diagnostics.

### Integration
- load generated model into USE;
- create state;
- run check;
- operation pre/post.

### Runtime integration
- synthetic event stream;
- reconnect/resync;
- ordering;
- trace miss;
- state drift.

### End-to-end
Auction project:
- import;
- offline check;
- runtime;
- positive/negative scenarios;
- report.

---

## 3. Mapping audit test

Automated assert:
- all 37 classes;
- all 67 attributes;
- all 63 references;
- all 14 inheritance;
- unresolved list exact;
- no duplicate source key;
- no duplicate generated association name;
- projections schema-valid.

Counts must come from canonical Ecore at test runtime, not duplicated constants only.

---

## 4. Mutation/negative tests

Bắt buộc có:
- remove mapping entry → audit fails;
- wrong owner → audit fails;
- wrong reference target → audit fails;
- wrong multiplicity → audit fails;
- mapping hash mismatch → block;
- invalid OCL → compile fail;
- ambiguous operation → require binding;
- runtime unknown object → no wrong state mutation.

---

## 5. Quality gates

Before merge:
- format/lint pass;
- unit pass;
- integration pass affected modules;
- no new warnings unless documented;
- docs updated;
- task checklist updated.

Before release:
- full test suite;
- Auction E2E;
- clean checkout build;
- plugin load test in target USE distribution;
- generated artifact reproducibility.

---

## 6. Performance gates

Measure, not optimize prematurely.

Record:
- project import time;
- model generation time;
- full OCL check time;
- runtime event-to-result latency;
- memory.

`JaCaMoFacade.performanceMetrics()` exposes the latest observed values: durations are nanoseconds, used memory is
the current JVM heap sample in bytes, and zero means the corresponding measurement is not yet available. Runtime
event-to-result latency begins at connector receipt and ends when that event's verification result is reported; it
therefore includes queueing and mutation work. These values are evidence/diagnostics, not performance guarantees.

Set project-specific thresholds after baseline measurements; do not invent performance guarantees before data.

---

## 7. Compatibility matrix

Track:
- Java version;
- Maven version;
- USE commit/version;
- JaCaMo version;
- Jason/CArtAgO/Moise versions;
- OS used for verification.

Pin versions for thesis experiments.

---

## 8. Phase 13 verification evidence (2026-09-16)

Scope: `phase/13-hardening-quality`, production/test revision `0f24e5c2` (including
Task 2 hardening `34768a6e`). The subsequent evidence commit changes documentation
and the compatibility evidence record only. Phase integration/merge/push remains a
separate gate; this evidence does not mark the phase merged or released.

### Coverage mapping

All Java tests below run in the normal Maven suite; no live tests are disabled or
replaced with mocks. Names refer to `src/test/java/org/tzi/use/plugins/jacamo/`.

| Gate | Existing executable evidence |
| --- | --- |
| P13.1 unit | `SemanticIdTest`, `SemanticModelTest`, `ProjectModelTest`, `TraceBindingTest`, `MappingTransformationTest`, `ConstraintOclTest` |
| P13.1 parser fixtures | `JcmProjectLoaderTest` (11), `StaticProjectImporterTest` (8): minimal/include/cycle, duplicates, missing/ambiguous sources, unsupported syntax, three Auction dimensions |
| P13.1 mapping audit | `MappingTransformationTest` plus independent Ecore/schema/semantic audit, mutation suite and USE compiler gate described below |
| P13.1 golden | `GoldenPipelineTest.auctionArtifactsMatchReviewedGoldenDigests`: exact SHA-256 for model, initial commands, OCL model, trace and diagnostics |
| P13.1 USE integration | `InstanceMaterializationTest`, `ConstraintOclTest`, `OfflineVerificationServiceTest`: actual USE model/commands/state/OCL compilation, positive/negative checks and pre/post state |
| P13.1 runtime synthetic | `RuntimeFoundationTest` (14), `RuntimeVerificationEngineTest` (14), `CompositeRuntimeConnectorTest`: ordered events, backpressure, trace miss, drift, reconnect, correlation lifecycle and receipt-to-result timing |
| P13.1 runtime live | `JasonRuntimeConnectorTest`, `CartagoRuntimeConnectorTest`, `MoiseRuntimeConnectorTest`: real pinned libraries and official callbacks/state |
| P13.1 Auction E2E | `LiveJaCaMoAuctionIntegrationTest.mirrorsRealJasonCartagoAndMoiseAuctionThenReconnectsWithFullResync`: static import, generated USE/OCL, real Jason/CArtAgO/Moise, traced violation, failed bid, disconnect, reconnect and authoritative property removal; offline positive/negative/report coverage in `OfflineVerificationServiceTest` and `DefaultJaCaMoFacadeTest` |
| P13.2 malformed/partial/unsupported | `malformedSourcesRecoverWithLocatedDiagnostics`, `partialProjectRetainsValidAgentSourceWithoutInventingMissingSourceContents`, `unsupportedJasonAndDynamicJavaArePreservedAsDiagnostics`, `secondMasDeclarationIsRejectedInsteadOfSilentlyIgnored` |
| P13.2 mapping mismatch | `invalidSchemaAndFingerprintBlockLoading`, `alteredMappingTargetsAreBlockedByTheFrozenMappingFingerprint` |
| P13.2 stale binding | `TraceBindingTest.bindingSchemaPersistsChoiceAndMarksChangedSourceStale`: changed and absent source hashes do not reuse the old exact selection |
| P13.2 disconnect/mutation error | `initialSynchronizationBuffersConcurrentDeltaAndDetectsLostConnection`, `syntheticConnectorMirrorsJsonReplayThenReconnectResyncRepairsDrift`, real Auction reconnect, `useMutationFailureMovesMirrorToErrorAndDoesNotCorruptExistingState` |
| P13.3 paths/classpath | JCM include traversal, explicit external roots, linked default classpath and invalid archive tests; `ConstraintOclTest.caseLoaderRejectsPathEscape` / `caseLoaderRejectsSymlinkThatResolvesOutsideProject` |
| P13.3 static non-execution | `StaticProjectImporterTest.staticImportNeverInitializesProjectSourceOrClasspathClasses`: compiled sentinel plus positive execution control |
| P13.3 export | `DefaultJaCaMoFacadeTest`: reject unsupported format/linked directories, verify JSON/Markdown and retain primary error on cleanup failure |
| P13.4 measurements/regression | `auctionPerformanceMetricsAreMeasuredWithoutFlakyWallClockThresholds`, `eventDrivenChecksCorrelateViolationAndOperationPrePostUsingCapturedPreState`, deterministic clock/blocked-queue latency tests, targeted/full invariant equivalence and tombstone retirement tests |
| P13.5 pins/compatibility | `CompatibilityManifestTest`, resolved Maven dependency tree, clean checkout with fresh Maven repository; [compatibility.json](../../compatibility.json) |
| Stable repeated runs | Three fresh Maven runs of golden + real Auction + import/check metrics + runtime verification; all four tests pass in each run with unchanged golden digests |

### Fresh commands and results

From the USE repository root:

```powershell
mvn -pl use-plugin package
mvn verify
mvn -pl use-plugin dependency:tree '-Dincludes=io.github.jason-lang:jason-interpreter,org.jacamo:cartago,org.jacamo:moise,org.tzi.use:use-core,org.tzi.use:use-gui'
mvn -pl use-plugin '-Dtest=GoldenPipelineTest,LiveJaCaMoAuctionIntegrationTest,DefaultJaCaMoFacadeTest#auctionPerformanceMetricsAreMeasuredWithoutFlakyWallClockThresholds,RuntimeVerificationEngineTest#eventDrivenChecksCorrelateViolationAndOperationPrePostUsingCapturedPreState' test
git diff --check 581f514d..HEAD
```

The last Maven command was run three times. Module package: **108/108**, zero
failures/errors/skips, JAR produced. Reactor `verify`: **251/251** = use-core 12
Surefire + 1 Failsafe, use-gui 1 Surefire + 129 Failsafe, plugin 108; all five reactor
modules and distribution ZIP/TAR packaging passed. Repeated focused runs: **4/4
each**, zero failures/errors/skips. Dependency tree resolves USE 7.5.0, Jason 3.3.0,
CArtAgO 3.1 and Moise 1.1 with `provided` host/runtime scope.

Independent mapping tools come from the sibling JaCaMo repository at
`849dc33bf6baddfa3d4f4394364d8b2685bc815e`. Their expected layout differs from this
plugin: copy the scripts unchanged into a scratch `audit/`, and copy this plugin's
canonical files byte-for-byte into scratch `Core/JaCaMo-Metamodel.ecore` and
`mapping/`. No canonical file or freeze manifest is rewritten. Run:

```powershell
python <scratch>/audit/check_mapping.py --output <evidence>/mapping-audit.json
python -m unittest discover -s <scratch>/audit -p 'test_mapping*.py' -v
python <scratch>/validate_dsml4jacamo_ecore.py --self-test --emf-classpath $emfCp --java "$env:JAVA_HOME/bin/java.exe"
mvn -q -pl use-core dependency:build-classpath '-Dmdep.outputFile=target/phase13-final-dependency-classpath.txt'
python <scratch>/audit/compile_mapping_use.py --use-classpath $useCp --java "$env:JAVA_HOME/bin/java.exe" --output <evidence>/mapping-use.txt
```

`$useCp` is the generated dependency classpath plus this checkout's freshly built
`use-core/target/use-core-7.5.0.jar`. `$emfCp` contains EMF common 2.42.0, ecore
2.39.0 and ecore.xmi 2.39.0. The Ecore validator and `ValidateEcore.java` also come
from the pinned audit repository. Results: **37 classes, 67 attributes, 63
references, 14 inheritance, 7 projections**, zero audit errors/warnings; **14
mutation test methods / 49 negative cases plus one baseline control**; **20 Ecore
mutations rejected**, real EMF load/Diagnostician/proxy and inheritance checks
passed; USE compiles both baseline and projection fixture and rejects **four
reverse-role collisions plus three reserved-identifier cases**. The external
compiler script prints the mapping's historical validated revision
`30d480db...`; that header is metadata, not the compiler revision used in this run.
The actual compiler was built from this checkout at `0f24e5c2`.

Local raw evidence: `use-plugin/target/phase13-task2-final-*` and
`phase13-task2-repeat-{1,2,3}.log` (ignored build output). Durable command/results
are summarized here so cleanup of `target` does not erase the acceptance record.

### Warnings and scope

The reactor reports inherited filename-based automatic-module warnings, GUI
`finalize()` deprecation, assembly `appendAssemblyId`/`finalName` warnings and long
TAR paths; use GNU-compatible TAR or the generated ZIP. SLF4J has no provider in
the test runtime. None is a new plugin warning introduced by this hardening slice.

Live means real in-process Jason/CArtAgO/Moise objects and their connector hooks;
the standalone JaCaMo 1.3.0 launcher, other component versions, another OS/JDK and a
long-running production workload are not validated. The USE plugin discovery,
status command and action extension points are exercised by `JaCaMoPluginTest`;
interactive installed-distribution GUI testing remains outside this automated gate.

### Performance baseline and optimization decision

Durations below are milliseconds converted from the printed nanosecond metrics;
memory is the current used-heap sample, not peak RSS. These runs share one Windows
host and include JVM warm-up differences, so they are observations rather than a
latency SLA. Runtime latency is the synthetic event receipt-to-verification-result
measurement; real runtime correctness is covered separately by the Auction test.

| Run | Import ms | Generation ms | Full check ms | Runtime ms | Used heap bytes |
| --- | ---: | ---: | ---: | ---: | ---: |
| Full module | 116.9876 | 25.0835 | 1.1550 | 0.3733 | 55,299,376 |
| Full reactor | 257.7871 | 39.2000 | 0.6801 | 0.7670 | 55,599,664 |
| Focused repeat 1 | 410.0064 | 634.9438 | 1.8778 | 0.7688 | 35,222,752 |
| Focused repeat 2 | 419.3273 | 598.7135 | 2.0014 | 1.0112 | 36,123,168 |
| Focused repeat 3 | 433.0863 | 566.2359 | 1.7150 | 0.4549 | 35,810,560 |

All sampled heaps are below the reported JVM maximum of 4,219,469,824 bytes. The
focused runs execute generation early in a new JVM, while the full suites have
already exercised it; their timings must not be treated as a regression ratio.
For the scoped Auction fixture, no sustained backlog or failed progress was
observed, and the live test asserts zero dropped events. No optimization is
justified by these measurements. P13.4's optimization process gate is complete
with no speculative optimization. Regression gates assert semantic equivalence,
positive measurements, exact injected-clock timing, bounded queue behavior and
retirement of rejected-operation state; wall-clock values are not pass/fail limits.
Larger-project scaling, peak process memory and long-duration load remain unmeasured.

### Clean environment evidence and reproduction

At production revision `0f24e5c2c5a9736b370878f7186bbd5c0a53164c`, a separate
`git clone --no-local --branch phase/13-hardening-quality` was built with an
explicitly empty Maven repository. `mvn -Dmaven.repo.local=<fresh-repository>
verify` downloaded dependencies, compiled from absent `target` directories,
generated parsers/resources and packages, and passed **251/251** in **4m37s**.
`git status --short` remained empty. The original checkout's branch and HEAD were
never moved. The clone and logs are retained under
`<temporary-validation-directory>/` for inspection. The original machine-specific
path was intentionally removed from active documentation.

This proves clean source/build/dependency resolution on the recorded Windows 11
amd64 host with Maven 3.9.9 / Oracle JDK 21.0.5. It is not a freshly provisioned OS:
the installed JDK, Maven, Git and host configuration remain shared. The exact
final evidence commit is subsequently rebuilt with `mvn clean verify` in that
separate clone; its SHA and final results are recorded in the task execution
report to avoid a self-referencing commit hash in this document.

### Review fix: consistent frozen-input snapshots (2026-09-16)

The results above identify the earlier `0f24e5c2`/`b7486661` revisions. Independent
review then found that `MappingLoader` reopened mapping/Ecore/manifest paths for
hashing and parsing. Each input is now read once into privately owned byte arrays;
hashes and parsed semantics use the same bytes. The secure XML settings remain.

Three deterministic tests in `MappingTransformationTest` inject successive byte
snapshots through a package-private reader seam: modified mapping then canonical
mapping; canonical Ecore then a renamed classifier; and a rejecting manifest then
a permitting manifest. Before the fix, **9 tests / 3 expected failures** reproduced
the mismatch. After the fix, **9/9** passed; module package passed **111/111**,
including unchanged golden and real Auction integration. Independent mapping
audit, **14 Python mutation methods**, and USE baseline/projection compilation plus
seven negative compiler cases passed again.
Full reactor `mvn verify` also passed **254/254** (111 plugin + 13 core + 130 GUI,
including Failsafe), all five modules, in 2m10s.
Logs are
`use-plugin/target/phase13-task2-fix1-*`; the appended task report records the fix
commit and final reactor/clean-checkout results. Earlier counts remain historical.

### Review fix round 2: validation precedence (2026-09-16)

Independent re-review found that fix round 1 eagerly snapshotted Ecore and the
freeze manifest before validating the mapping schema. A missing downstream input
could therefore replace the actionable `MAPPING_SCHEMA_INVALID` diagnostic with
the generic `MAPPING_LOAD_FAILED`. The deterministic regression uses an invalid
mapping file and absent Ecore/manifest paths. It failed RED with the generic code,
then passed after downstream snapshots were moved behind successful schema and
mapping JSON validation. Mapping, schema, manifest and Ecore are still each read at
most once, and every validator/hash/parser still consumes the same owned bytes.
The focused mapping class passed **10/10** and the full plugin package passed
**112/112**, including golden, plugin-load, live Auction and runtime tests. Final
reactor `mvn verify` passed **255/255** (112 plugin + 13 core + 130 GUI, including
Failsafe) across all five modules. Exact production commit `eae54750` then passed
`mvn clean verify` from a separate clone: **255/255**, zero failures, errors or
skips; the clone remained clean after its build log was retained outside the
checkout. Independent review of `f50a6af2..eae54750` found no Critical or Important
issue and confirmed the fix preserves snapshot ownership, canonical Core bytes and
public API behavior. The only Minor finding was this evidence's earlier reference
to a Git-ignored execution report; the directly tracked evidence here supersedes
that reference.

## 9. v1.0.1 current verification (2026-09-18)

The earlier Phase 13 counts above are historical evidence and must not be quoted as
the current suite total. The v1.0.1 retained validation and the documentation-sync
fresh reactor run both report **271/271 PASS**, zero failures, errors, or skips:

| Module | Tests |
| --- | ---: |
| `use-core` | 13 |
| `use-gui` | 130 |
| `use-plugin` | 128 (125 unit/component + 3 release integration) |

`HotfixLifecycleTest` covers LIVE rebuild, user OCL profile load, reimport, continued
events/violations, alias preservation, single subscription, reconnect/resync drift,
and failed-build preservation. `HotfixBindingTest` covers the real importer/facade path
for ambiguity without binding, valid binding, exact trace identity, invalid targets,
malformed JSON, and stale hashes.

Package validation checks 27 declared ZIP entries, source bytes, embedded canonical
resources, SHA sidecar, USE plugin discovery, and isolated child-JVM loading. Two
separate builds produced the same ZIP hash only under the recorded identical source,
dependency, JDK, and Maven scope; no cross-toolchain guarantee is inferred.

## Runtime mapping draft (2026-09-19)

Phase 18 introduces a strict declarative draft loader, semantic validator and derived
compatibility report. See [runtime-mapping-draft.md](runtime-mapping-draft.md).
Initial module regression: 135/135 PASS. V1 target bindings are temporary; no runtime
mapping freeze or OCL expansion is implied.

Phase 19: module regression 139/139 PASS; final focused mapping/mirror/Auction
24/24 PASS. Wrong semantic target/create regressions were RED before the fix.
Strict scalar conversion and exact-object recreation are covered. Golden trace
negative control verifies byte-identical prior records after excluding newly
added projected attribute declarations. Live derived evidence is located at
`target/phase19-mirror-evidence`; its exclusions are part of the gate scope.

## Phase 18-20 execution scope (2026-09-19)

Runtime Mapping Draft is integrated; the supported mirror subset has executable
zero-drift evidence. Full-project Phase 20 uses its documented technical-limitation
exit alternative. The pinned launcher probe exposes .jcm syntax and Moise OS schema
gaps in the static fixture. See [phase20-runtime-evidence.md](phase20-runtime-evidence.md).
This does not claim full autonomous Agent -> Artifact -> Organisation E2E.

Fresh continuation regression: `mvn -B verify` **285/285 PASS**, zero failures,
errors or skips (core 13, GUI 130, plugin 139 unit/component + 3 release integration).
The Phase 19 mirror summary records zero drift and zero failed/rejected/dropped.
The Phase 20 probe asserts original/namespace-only OS rejection and successful
OSBuilder control startup; the control is explicitly not Auction-equivalent.
Source, input, schema and jar hashes are retained in
[the combined evidence](evidence/phase18-20-2026-09-19.json).

Post-merge validation at `016e74b6`: `mvn -B -pl use-plugin verify` **142/142 PASS**,
including all three release/package tests after compatibility and limitations updates.
The full reactor count above is from the preceding combined gate, not a second full
reactor run. The final probe is source-hashed separately after Git line-ending normalization.

## Phase 24 regression (2026-09-19)

Full reactor `mvn --batch-mode verify`: 298/298 PASS, zero failures/errors/skips
(core 13, GUI 130, plugin 152, release integration 3). Includes Auction and the local
Counter Team in-process case, pure guard closure and unsupported-boundary negatives.
[Phase 24 evidence](phase24-translation-multicase-evidence.md) records exact scope,
commands, output/hash locations and the measured ~1.21 s / 33-event Counter scenario.
Standalone launcher and full normative lifecycle claims remain excluded.

## Current engineering closure

See [Phase 27 hardening](phase27-hardening-audit.md) and the
[final acceptance matrix](phase28-project-closure.md) for current scope and evidence.
Earlier phase test totals and draft/temporary-target descriptions are historical.
That final-target statement belongs to the historical Phase 28 V1 closure. Current
V2 gates and counts are recorded under `v2-migration/`; Phase 44 freezes the three
V2 contracts only after focused, module, reactor, relocated and installed-package
checks. A published Git release remains separate from autonomous engineering verification.

## Phase 44 final V2 gate

The frozen source revision `7c435addcc91d7bbe7928953a11778f1b8e32d73`
passes focused 154/154, module 231/231, clean reactor 374/374 and relocated clean
reactor 374/374. All have zero failures, errors and skipped correctness tests.
Installed-package smoke and exact 30-entry inventory validation pass. Six normalized
static production artifacts are byte-identical across the primary and relocated
checkouts. See `v2-migration/phase44-final-v2-freeze.md` and the durable bundle in
`evidence/v2-final/`.
