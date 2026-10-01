# TASK — Runtime Verification + External OCL

## Goal

Hoàn thiện **runtime verification thực sự** cho pipeline JaCaMo → USE:

```text
project.jcm
→ native MModel / MSystemState
→ load external constraints.ocl
→ runtime event / snapshot
→ mutate cùng MSystemState
→ evaluate OCL
→ record PASS / FAIL / ERROR / SKIPPED theo state/event
→ hiển thị và replay được
```

Không mở lại Ecore/Mapping V2. Không tạo runtime model thứ hai. Không fuzzy mapping.

**Status: DONE — supported observed projection (2026-10-01).** Toàn bộ 63/63
checkbox có code + focused/full/package evidence; không còn checkbox mở trong task này.
Phạm vi là observed supported projection, không phải toàn bộ JaCaMo semantics.

---

# 1. External `.ocl` trên native model

- [x] Thêm native constraint service dùng **USE OCL compiler/API hiện có**, không viết parser/engine mới.
- [x] Load file `.ocl` gồm named invariants.
- [x] Compile/type-check trực tiếp với **current native `MModel`**.
- [x] Attach toàn bộ profile theo kiểu **all-or-none**; lỗi syntax/type/context/conflict thì không attach dở dang.
- [x] Registry lưu tối thiểu: constraint name, context class, source file/hash, model revision, enabled status.
- [x] Baseline verify ngay sau khi load.
- [x] Khi model rebuild/resync: recompile/rebind profile nếu tương thích; nếu không thì báo rõ, không silently drop.
- [x] Nếu OCL yêu cầu class/association không có trong `AUTO`: chỉ mở rộng projection khi semantic source thật sự có dữ liệu; nếu capability không tồn tại thì `SKIPPED/UNAVAILABLE`, không tạo stub và không vacuous PASS.

## Acceptance

- [x] Valid `.ocl` load + compile + baseline PASS/FAIL đúng.
- [x] Invalid `.ocl` fail atomic với diagnostic rõ.
- [x] Constraint giữ/rebind đúng qua supported resync.
- [x] Không tạo `MSystem` mới.

Evidence: `ExternalOclConstraintServiceTest` (3 tests) và
`NativeRuntimeFacadeIntegrationTest` chứng minh USE `ASSLCompiler`, multi-context
PASS/FAIL/ERROR, atomic syntax/type/context/name rejection, saved-byte rebind và
same-system resync. Missing AUTO context/association được báo compiler
incompatibility/UNAVAILABLE; không tự mở rộng bằng stub. Runtime dependency không
COMPLETE là SKIPPED; C08 thiếu trong AUTO bị reject incompatible, hoặc SKIPPED khi
class có trong FULL nhưng live API unavailable. Named declarations là format profile; raw expression
chỉ thuộc dialog OCL thông thường, không thuộc loader này.

---

# 2. Runtime verification coordinator

Tạo **single-writer coordinator** cho runtime state:

```text
RuntimeEvent / RuntimeSnapshot
→ validate identity/order/coverage
→ apply atomic mutation
→ increment stateVersion
→ evaluate OCL
→ record result
→ publish UI/report update
```

- [x] Serialize mutation + verification trên cùng active `MSystemState`.
- [x] Mỗi accepted mutation/checkpoint có `stateVersion` tăng đơn điệu.
- [x] Không full-check hai lần cho cùng một event.
- [x] Baseline/resync/profile-change → full-check.
- [x] Faithful runtime event → verify sau atomic mutation.
- [x] Evidence-only event không được coi là state mutation.
- [x] Manual verify/export chỉ đọc state nhất quán.

## Violation policy

- [x] **User OCL invariant FAIL không rollback faithful runtime state.**
- [x] Giữ state vi phạm, ghi `FAIL`, tiếp tục quan sát runtime.
- [x] Chỉ rollback/quarantine khi malformed payload, identity/protocol mismatch, structural mutation không hợp lệ hoặc transaction không an toàn.

Evidence: `RuntimeVerificationCoordinatorTest` (9 tests),
`NativeRuntimeFacadeIntegrationTest` (4 tests) và `NativeUseGuiEndToEndIT` (2 tests).
Single EDT writer + read barrier; monotonic local version; one post-commit full
verification; failed transaction giữ hash/version, original object/link identity
và không acknowledge ledger. GUI không full-check lại kết quả atomic native.
Invariant FAIL giữ state vi phạm; lỗi protocol/structure cần snapshot resync.

---

# 3. Runtime results + journal/checkpoints

- [x] Tạo immutable runtime verification result cho mỗi event/checkpoint.
- [x] Lưu tối thiểu:
  - `sessionId`, `generation`, `modelRevision`, `stateVersion`;
  - `eventId/sourceId/sourceSequence`;
  - `observedAt/appliedAt/verifiedAt`;
  - `checkpointId`, `constraintSetHash`, `stateHash`;
  - per-constraint `PASS | FAIL | ERROR | SKIPPED`;
  - diagnostic, duration, coverage/freshness.
- [x] Tạo bounded/persisted `RuntimeEventJournal`.
- [x] Tạo checkpoint store đủ để replay từ baseline.
- [x] GAP/overflow/missing coverage → `STALE/INCOMPLETE`, không báo PASS.
- [x] Không dùng mutable `MSystemState` reference làm history.

## Acceptance

- [x] Xem được chuỗi `PASS → FAIL → PASS` theo `stateVersion`.
- [x] Transient violation không mất dù final state PASS.
- [x] GAP/overflow không bị che giấu.

Evidence: immutable `RuntimeVerificationResult`; JSONL `RuntimeEventJournal`
hash-chain (default 512 memory results / 64 MiB disk), retained baseline SOIL +
8 recent checkpoints (16 MiB/file). Results lưu toàn bộ identity/time/version,
constraint-set/state/result hashes và outcome từng invariant. Coordinator tests
chứng minh A-B-A/transient FAIL, bounded tail và disk overflow STALE/no PASS.
Journal persistence GAP là sticky; resync không được xóa lịch sử đã mất.

---

# 4. Faithful live runtime deltas

Ưu tiên **CArtAgO C09 observable-property snapshot/delta**, vì đây là phần runtime có authority rõ nhất.

- [x] Normalize create/update/delete property snapshot thành typed runtime mutation.
- [x] Giữ exact artifact/workspace/property identity.
- [x] Không đồng nhất C09 với C08 live `ObsProperty`.
- [x] Không parse string để giả typed semantics.
- [x] Add/remove artifact/property giữ incarnation chính xác.
- [x] Verify OCL sau atomic property/artifact transaction.

Jason/Moise/NPL:

- [x] Chỉ materialize live state khi official source cung cấp faithful typed evidence.
- [x] Phần chưa đủ authority tiếp tục `EVIDENCE_ONLY/SKIPPED`.
- [x] Không hard-code case study.

Evidence: `LiveCartagoNativeVerificationTest` (1 test) dùng CArtAgO environment,
controller/ILogger và artifact thật, qua SnapshotCoordinator → production wire
codec/BridgeClient → facade/Session/OCL → Workbench → replay. A-B-A,
property remove/add và artifact dispose/recreate UUID khác đều được quan sát.
Property init callback trước artifactCreated được defer vào atomic creation.
Đây là live official-API evidence với in-process byte transport, không phải claim
separate-JVM full Auction/House E2E. C09 lưu values/types/annotations từ official
arrays (deterministic serialization), không phải live C08 objects. Jason/Moise/NPL
dynamic concepts chưa có faithful materialization tiếp tục EVIDENCE_ONLY/SKIPPED.

---

# 5. Workbench/runtime observability

- [x] Runtime panel/report đọc **verification result mới nhất**, không dùng cached import report.
- [x] Hiển thị tối thiểu:
  - current `stateVersion`;
  - last event/checkpoint;
  - PASS/FAIL/ERROR/SKIPPED counts;
  - last failing constraints;
  - queue/backlog/drop/GAP status;
  - last verification duration;
  - coverage status.
- [x] Object Diagram / invariant views refresh sau committed native mutation.
- [x] Publish UI sau atomic commit/checkpoint, không spam theo từng low-level write.

Evidence: `AtomicStateChangedEvent`, `ClassInvariantView`,
`NewObjectDiagramView`, `JaCaMoWorkbenchPanel`; GUI end-to-end và live facade test
chứng minh cùng Session system, diagram membership/links refresh và precomputed
FAIL/PASS không bị worker cũ overwrite. Read-only UI getters không giữ facade
monitor khi writer chờ EDT; concurrency test chứng minh không lock inversion.

---

# 6. Offline replay

Dùng:

```text
.use
+ baseline .cmd
+ constraints.ocl
+ runtime journal/checkpoints
```

- [x] Load `.use`.
- [x] Replay baseline `.cmd`.
- [x] Load/register `.ocl`.
- [x] Replay runtime journal qua **cùng native mutation/verification rules**.
- [x] So sánh state hashes, stateVersion/checkpoint sequence và per-constraint outcomes.
- [x] GAP/corrupt/missing payload phải fail closed hoặc replay partial có đánh dấu.

Evidence: `NativeRuntimeReplayTest` (2 tests). Bundle gồm `model.use`,
`baseline.cmd`, `constraints.ocl`, typed `runtime.jsonl`, `manifest.json`.
Replay dùng USE compiler/SOIL và chính NativeRuntimeProjector/coordinator/rules;
kiểm entry ordinal/chain, stateVersion, stateHash và semantic resultHash (gồm
per-constraint outcomes). Mất/corrupt data fail closed; marked coverage GAP là
explicit partial. Không evaluate những bước SOIL baseline chưa xây xong state.
Offline system tách khỏi live Session, không phải runtime authority thứ hai.

---

# 7. Required tests

- [x] External `.ocl`: valid/invalid/type error/missing context/duplicate/atomic rejection.
- [x] Runtime: `PASS → FAIL → PASS` trên cùng invariant.
- [x] Transient violation được lưu dù final state PASS.
- [x] Faithful C09 property A→B→A.
- [x] Artifact create/dispose/recreate với exact identity.
- [x] Duplicate/rewind/GAP/stale generation/model revision.
- [x] Queue overflow → `STALE/INCOMPLETE`, không PASS.
- [x] Resync giữ/rebind constraint profile đúng.
- [x] GUI/report cập nhật từ native runtime result.
- [x] Concurrent manual verify/export không đọc partial state.
- [x] Offline replay cho cùng state/result hashes.
- [x] Full reactor `verify`.
- [x] Package/release tests.

Build evidence và exact counts ở mục Closure evidence bên dưới. Lượt bị ngắt
và các lượt fail trước đó không được tính PASS; chỉ dùng các lượt cuối hoàn chỉnh.

---

# Final acceptance

Task chỉ DONE khi chứng minh được:

```text
JaCaMo runtime
→ faithful observed mutation/checkpoint
→ same native MSystemState
→ external .ocl
→ runtime OCL evaluation
→ attributable PASS/FAIL/ERROR/SKIPPED
→ persisted result/history
→ visible in Workbench
→ replayable offline
```

Không claim “verify every JaCaMo state”.

Chỉ claim:

> **Verified observed runtime states/checkpoints của supported projection, với coverage/GAP được ghi rõ.**

## Final report

Báo ngắn gọn:

1. runtime production call graph;
2. external `.ocl` workflow;
3. runtime concepts thật sự materialize;
4. result/journal/checkpoint format;
5. violation policy;
6. UI behavior;
7. replay result;
8. test counts;
9. remaining unsupported runtime semantics.

## Closure evidence

Focused gate PASS (2026-10-01): **89 tests, 0 failures, 0 errors, 0 skips**
(Bridge contract 8 + SnapshotCoordinator 4 + plugin focused 77).
Log: `use-plugin/target/runtime-final-focused.log`.

Full reactor **PASS: 537 tests (397 unit + 140 integration), 0 failures,
0 errors, 0 skips**; `mvn -B verify` hoàn tất 2026-10-01 10:18:53 +07:00.
Log: `use-plugin/target/runtime-full-reactor-verify.log`.

| Module | Unit | Integration | Total |
|---|---:|---:|---:|
| Bridge contract | 12 | 0 | 12 |
| Official adapters | 24 | 0 | 24 |
| USE core | 12 | 1 | 13 |
| USE GUI | 1 | 129 | 130 |
| USE plugin | 348 | 10 | 358 |
| Total | 397 | 140 | 537 |

Package/release **PASS**, thực thi trong chính lifecycle full `verify`:
package → shaded JAR/ZIP → GUI staging → checksum → integration-test → verify.
10 plugin integration tests gồm `NativeRuntimePackagedReplayIT` (1),
`NativeUseExportRecompileIT` (1), `NativeUseGuiEndToEndIT` (2),
`NativeUseSessionOclIT` (1), `GuiPluginStagingIT` (1),
`LegacyAuthorityPackagingIT` (1), `ReleasePackageIT` (3).
Installed-release replay chạy JVM riêng chỉ với USE distribution + test driver
và plugin lấy từ release ZIP: không Maven/JaCaMo classpath; xác nhận class được
load từ installed JAR và state/result hashes khớp A-B-A journal.

Reproduction commands, chạy từ `D:\_CODE_BANK\Project_\08_Thesis\use`:

```powershell
mvn -B -pl use-plugin -am "-Dtest=ContractTest,SnapshotCoordinatorTest,CodeGroundedPhase7Test,ExternalOclConstraintServiceTest,RuntimeVerificationCoordinatorTest,NativeRuntimeReplayTest,NativeRuntimeBridgeCoverageTest,LiveCartagoNativeVerificationTest,NativeRuntimeFacadeIntegrationTest,NativeUseGuiEndToEndIT,NativeUseSessionActivationTest,NativeUseSoilExporterTest,GenericLauncherScriptTest,JaCaMoWorkbenchPanelTest,LegacyV2OclIsolationTest,LocalTcpBridgeTransportTest,BridgeMirrorStateMachineTest,DefaultJaCaMoFacadeTest,V2HardeningAuditTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
mvn -B verify
```

Release ZIP SHA-256:
`8900d864b6d737674418658a9dc65286c549312691829eca22e1a70577ae3a5b`.
Production `target/use-plugin-1.0.1.jar` và GUI staged
`use-gui/lib/plugins/use-jacamo-plugin-1.0.1.jar` byte-identical:
`014177cb7031c905442906afd155b3be7d4674b50b954dbac6b8df4ace8fda60`.

Final production flow:

```text
official CArtAgO ILogger/controller
→ CartagoSnapshotSource / SnapshotCoordinator
→ JaCaMoBridgePlatform bounded queue / typed Bridge
→ BridgeClient identity/order/coverage validation
→ NativeRuntimeProjector exact binding/incarnation
→ RuntimeVerificationCoordinator single EDT writer
→ atomic NativeRuntimeMutationEngine / same Session MSystemState
→ stateVersion++ / USE-compiled invariants
→ immutable results / persisted hash-chain journal / SOIL checkpoints
→ AtomicStateChangedEvent / normal USE views / Workbench / offline replay
```

OCL workflow: file named invariants → USE `ASSLCompiler` against current native
`MModel` → atomic install/registry → baseline check → post-transaction check
→ per-constraint PASS/FAIL/ERROR/SKIPPED with event/state metadata. Resync retains
saved bytes and rebinds; incompatible contexts/types are explicit rejection.

Diff review PASS (`git diff --check`, including new runtime files via no-index).
Frozen V2/Ecore/golden/resource paths have no diff; native runtime does not
import/load mapping V2. Git remains on `main` / `872c187b`, with 46 tracked changes
and 22 untracked entries. Overall tracked diff: 46 files, +1963/-2550 (includes
pre-existing user work/document deletions, not just this task; excludes untracked
new implementation/task files and generated artifacts). Existing changes were
preserved; no reset/clean/staging/commit of unrelated work.

TCP idle regression giữ deadline 200 ms và mọi assertions. Immutable fixture
wire frames được chuẩn bị trước request thay vì đo cả cold model digest/JSON
generation trong deadline idle-stream; interrupted/repeated test logs ghi nhận
model encoding 232–785 ms. Không đổi transport timeout hoặc production semantics.

Remaining unsupported scope: live C08 ObsProperty; chưa verify mọi JaCaMo internal
state hoặc pause/step runtime; unmaterialized Jason/Moise/NPL dynamics; tự mở rộng
AUTO khi thiếu semantic authority. Manual SOIL/flag edits ngoài recorded
coordinator flow không tự replay: hash mismatch phải báo lỗi, không fuzzy repair.

## Initial live evidence — original Auction external OCL (2026-10-01, historical)

This section records the initial, unchanged 11-invariant profile. That source
is preserved in the cited run's `replay/constraints.ocl`; the current USE-side
profile is superseded by the re-audit below. Historical counts are not results
for the corrected profile.

User-supplied profile retained byte-for-byte at
`src/test/resources/jacamo/ocl/auction/auction_demo.ocl` (SHA-256
`c33533d5387e532376c8bf36f05015a4852224755e944508394dd4eda7401555`).
Generic launcher input is the original
`D:\_CODE_BANK\Project_\08_Thesis\jacamo\examples\auction\auction.jcm`.
Whole-project staging preserves relative resources; Bridge injection/generated
evidence stays in USE, not JaCaMo. All 12 original project files remain unchanged.

Optional `-OclProfilePath` headless flow activates the native Session, uses the
existing facade / USE `ASSLCompiler` to compile/attach the profile and record its
baseline, then releases the exact official `JaCaMoLauncher.startAgs()` hook.
This bounded startup barrier prevents fast agents from finishing before OCL
loads; it is not runtime pause/step or a change to Auction ASL/XML semantics.

Live evidence:
`use-plugin/evidence/auction-ocl/20261001-113340-452-8a8172c7/`.
The same active Session/MSystem is asserted through profile installation and
resync. Baseline version 2 → 86 faithful mutation/re-check boundaries through
version 88 → resync version 89. All 86 changed states are retained with external
FAIL outcomes. C09 includes actual Auction `commitment` / `goalState` deltas.
815 hash-chained journal entries, no GAP/STALE; 723 evidence-only events are
recorded as OBSERVED/SKIPPED, not counted as formal OCL evaluations.

All 11 invariants compile. Baseline and faithful runtime evaluations have
7 PASS / 4 FAIL / 0 ERROR / 0 SKIPPED, with no outcome transitions in this run.
Two `DEMO_FAIL` checks are deliberately false. The supplied
`PASS_AuctionHasTwoRoles` is also false: official Moise exposes 3 native Roles
including implicit abstract `soc`. `DEMO_RUNTIME_NoObservablePropertySnapshots`
is already false at baseline because it ranges over existing framework/organization
C09 snapshots. Do not weaken the profile or remove faithful objects to force PASS.
The per-invariant table and reproduction command are maintained in
`src/test/resources/jacamo/ocl/README.md`.

Full reactor / package / release PASS: `mvn -B verify`, 545 tests
(405 unit + 140 integration), 0 failures/errors/skips, finished at
2026-10-01T11:44:56+07:00. The 10 plugin integration tests include native OCL,
Session, GUI, export, installed replay and release checks.
Actual Auction installed-release replay PASS: 815 journal entries replayed by the
plugin extracted from the release ZIP, without Maven/JaCaMo application classpath;
final state/result hashes match the live manifest (`ISOLATED_NATIVE_RUNTIME_REPLAY_PASS`).
Logs are under the explicit USE evidence directory, outside disposable `target`.

Final focused gate PASS (2026-10-01T11:49:07+07:00): 29 tests, 0 failures/errors/skips.
Command, from USE:

```powershell
mvn -B -pl use-plugin -am "-Dtest=LiveJaCaMoLauncherLifetimeTest,AuctionExternalOclProfileTest,NativeOclLaunchEvidenceTest,GenericLauncherScriptTest,ExternalOclConstraintServiceTest,RuntimeVerificationCoordinatorTest,NativeRuntimeFacadeIntegrationTest,Phase9CaseStudyAcceptanceTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

`focused-tests.log`, `full-reactor-verify.log`, `auction-installed-replay.log`
and `original-project-integrity.json` retain test/replay/project-integrity evidence.
Release ZIP SHA-256:
`9e1957ece9482d28e723f70c1ff76f94ad49d00a006d9d455aec224dc3791acc`.
Installed release plugin SHA-256:
`9b3c616b1c6718e019cb58d70560143df96c6d44a3a4a1c07fcb50b9159b169e`.

Scope remains `OBSERVED_SUPPORTED_PROJECTION_ONLY`: CArtAgO/Jason observed
sources COMPLETE, Moise/NPL evidence PARTIAL; Jason action execution remains
evidence-only. This does not establish every internal state, live C08, full
Auction deadline/norm semantics, or runtime pause/step support.

Final diff review PASS: tracked `git diff --check` plus no-index whitespace checks
for the new launcher/evidence/profile-test/docs files. Frozen V2/Ecore/golden
paths remain unchanged. Git is `main` / `872c187b`, 50 tracked changes and
25 untracked entries; tracked diff is 50 files, +2250/-2575. These totals include
pre-existing and concurrent user work, not just the Auction OCL integration.
No reset/clean/staging/commit of unrelated changes was performed.

## Auction OCL re-audit — real runtime transitions (2026-10-01)

No production code or Auction semantics changed. Inspection of the official
specification, actual native schema and live C09 journal justified two profile
corrections at `src/test/resources/jacamo/ocl/auction/auction_demo.ocl`:

- `PASS_AuctionHasTwoRoles` -> `PASS_AuctionHasTwoConcreteRoles` plus
  `PASS_AuctionHasOneAbstractRole`, using native `Role.isAbstract` (2 concrete,
  1 implicit abstract `soc`). All 3 faithful Role objects remain present.
- Global C09 emptiness -> `DEMO_RUNTIME_NoRunningAuction`, using the exact
  `auction_env.AuctionArtifact` type through native C15/C17 associations and
  its real `running` property. Values/types use the current canonical JSON
  String representation; exactly one Boolean-typed running snapshot is required.
  No fake events, numeric reinterpretation, or case-specific production branch.

Profile SHA-256: `231e18551d84034d2fe8c97da5b235079dc75f3d37f5c3be4caae08ccc7ca3b6`.
The original Desktop profile retains its original hash. All 12 original Auction
files retain their hashes with no added files; all 1,159 main Java/tool source
files retain their pre-audit hashes and paths. Frozen assets remain unchanged.

Clean live run:
`use-plugin/evidence/auction-ocl-transition/20261001-141513-461-03ed0df4/`.
Generic launcher receives the original `jacamo/examples/auction/auction.jcm`,
preserves whole-project relative resources, and loads the OCL from USE.
Observation 75 s, queue 8192, timeout 240 s; `-SkipBuild` is used only after the
focused reactor rebuild and explicit compiled-consumer/classpath checks.
Same Session `446a8dc8-1405-4bdf-9d83-8d3f19ed92c8`, MSystem identity
`1047460013`, generation 1, retained through OCL installation/events/resync.

All 12 invariants compile/type-check with USE. Baseline v2: 10 PASS / 2
intentional FAIL / 0 ERROR / 0 SKIPPED. Only the runtime policy changes:

| stateVersion / event | Actual observed property | Runtime policy |
|---|---|---|
| 2 / profile baseline | No live AuctionArtifact yet; Cartago COMPLETE | PASS |
| 107 / cartago:706 | a2 created, running `["false"]` | PASS |
| 110 / cartago:714 | a1 created, running `["false"]` | PASS |
| 112 / cartago:721 | a2 started, running `["true"]` | FAIL |
| 114 / cartago:731 | a1 started, running `["true"]` | FAIL |
| 148 / cartago:872 | a1 stopped, a2 still running | FAIL |
| 160 / cartago:916 | a2 stopped, both running `["false"]` | PASS |

PASS -> FAIL -> PASS is recorded on normal, faithfully materialized events,
before resync v163. The runtime FAIL persists in 48 event-state results
(v112..159), without rollback. 160 faithful event mutations/re-checks through
v162 retain external results. 1,133 journal entries include 967 evidence-only
OBSERVED/SKIPPED events; those are not formal OCL re-checks. No GAP/STALE or
external ERROR/SKIPPED. Cartago 1..924 and Jason 1..203 are contiguous/ordered;
consumed sequences match the final snapshot watermarks. Real observed-to-applied
latency (~13 s around Auction start) is retained, not presented as lockstep.

The initial 20 s run stopped consumption at Cartago 656 before resync watermark
922: AuctionArtifact events were still buffered. The read-only 75 s audit under
`auction-ocl-audit/20261001-140913-960-648c9cc2/` exposes original false/true/false
events. The earlier interrupted `auction-ocl-audit/20261001-122611-458-417dc07b/`
has a missing-consumer-class failure and is not PASS evidence.

Focused PASS: 30 tests, 0 failures/errors/skips (same focused command above),
finished 14:14:57 +07. Fresh full reactor/package/release PASS: `mvn -B verify`,
546 tests (406 unit / 140 integration), 0 failures/errors/skips, finished
14:22:14 +07; includes all 10 plugin integration/release tests.
The freshly built installed-release plugin replayed all 1,133 actual Auction
journal entries in an isolated JVM, validating every state/result hash and the
final live manifest: `ISOLATED_NATIVE_RUNTIME_REPLAY_PASS`.

Evidence: `auction-ocl-transition-evidence.json` (ordered transition timestamps,
state/event IDs, actual property payloads, source ordering),
`original-project-integrity.json`, `profile-changes.diff`, native schema,
OCL baseline/registry, journal/replay bundle and `auction-installed-replay.log`.
Focused/full logs are in `use-plugin/evidence/auction-ocl-transition/`.
The maintained reproduction command and profile rationale are in
`src/test/resources/jacamo/ocl/README.md`.

Scope remains OBSERVED_SUPPORTED_PROJECTION_ONLY. C08 is UNAVAILABLE_BY_API;
Moise/NPL dynamic semantics remain PARTIAL, Jason actions evidence-only.
This run does not prove AuctionArtifact dispose/recreate (the original case
does not dispose them), every internal JaCaMo state, complete deadline/norm
equivalence, or runtime pause/step. No task checkbox is broadened by this demo.

Final diff review PASS: normal Windows CRLF-aware tracked `git diff --check`
and no-index checks on the updated profile/test/docs. Git remains `main` /
`872c187b`, 50 tracked changes and 25 untracked status entries; overall tracked
diff is +2253/-2575 across 50 files, including preserved pre-existing work.
No unrelated staging/reset/clean/commit. `test-results.json` and
`executed-command.ps1` retain the final gates and exact successful command.

## Launcher projection selection — AUTO/FULL (2026-10-01)

The generic launcher now accepts `-ProjectionMode AUTO|FULL` (default AUTO)
and passes `use.jacamo.projection.mode` to both USE GUI and native headless
consumer. The native facade captures that selection once and supplies it to
`CodeGroundedNativePipeline.build(snapshot, mode)`, including rebuild/resync.
Actual mode is recorded in GUI readiness and headless summary evidence.
No semantic mapping, frozen resource, JaCaMo case source or runtime authority
was changed; one active Session/MSystem remains the verification authority.

Focused PASS: **40 tests, 0 failures/errors/skips**, including the 3 new
`NativeProjectionLaunchTest` checks (configuration rejection/default, FULL
Workbench activation/program objects/links/export, same-system resync).
Log: `target/projection-focused-tests.log`.
Full reactor/package/release PASS: **549 tests, 0 failures/errors/skips**,
`mvn -B verify` finished 2026-10-01T16:01:06+07:00; includes all 10 plugin
integration/release tests. Log: `target/projection-full-reactor-verify.log`.
Production/staged GUI plugin JARs are byte-identical (SHA-256
`838f1dc8e4ba92def05553b1fa88fc64bc67ccf90eebee4418b9caddbf9c032c`).
Windows PowerShell 5.1 parse/ValidateSet PASS; invalid mode is rejected before
launching any producer.

Real Hello World headless FULL launcher PASS on PowerShell 5.1:
`target/projection-full-live/20261001-160135-713-0438d3f2/summary.json` records
48 classes, 1,549 objects, 1,967 links, `projectionMode=FULL`, resync and the
same active MSystem. Export contains 5 AgentPrograms, 5 PlanLibraries,
1 AgentGoal, 145 Triggers and 243 Actions; Belief class exists but has 0 source
objects in this run. All 13 original JCM/ASL/XML/Java/Gradle source hashes remain
unchanged. Launcher removed only its managed staging copy; exported evidence
remains under the run directory.

This is FULL **projection** evidence, not full JaCaMo/live BDI completeness or
a visual separate-process GUI audit. Beliefs/goals are program-source facts;
unsupported live dynamics and C08 remain unsupported/unavailable. This narrow
launcher change does not add a deployed Agent–AgentProgram association and does
not broaden the earlier runtime acceptance scope or tick unrelated task items.
