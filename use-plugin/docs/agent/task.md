# USE–JaCaMo Plugin — Runtime Snapshot Verification, Pause/Resume & Goal-Centric Diagnostics

> **Mục tiêu của task này:** chuyển hệ thống từ runtime mirroring/verification cũ sang luồng chính:
>
> `JaCaMo execution → verification checkpoint → snapshot → USE MSystemState → OCL → FAIL → preserve snapshot → pause JaCaMo → trace/localize lỗi → resume`
>
> Đồng thời phải **dọn bỏ các runtime path, projection và UI cũ không còn phù hợp**, tránh giữ hai kiến trúc song song, tránh duplicate state, duplicate listener, stale code và dashboard rác.

> **Approved control contract 2026-10-05:** dùng capability-gated Jason
> ExecutionControl tại reasoning-cycle boundary, ACK từng agent, resync sau
> pause/resume. PAUSED chỉ có nghĩa “Jason agents paused”; activity CArtAgO/Moise
> đã in-flight có thể tiếp tục. Supported-subset migration và final reactor đã
> hoàn tất lúc 20:09:22 +07, 2026-10-05. **710/711 checkbox có evidence**; J3
> `impossible` vẫn UNSUPPORTED bởi pinned public Moise API, không suy diễn.
> Xem closure/evidence mục 23; không claim toàn bộ semantics JaCaMo được hỗ trợ.

---

# 0. Quy tắc bắt buộc

- [x] Đọc `use-plugin/docs/agent/agent.md` trước khi sửa code.
- [x] Đọc toàn bộ file task này.
- [x] Chạy preflight:
  - [x] `git status`
  - [x] `git diff`
  - [x] `git diff --staged`
  - [x] `git branch --show-current`
  - [x] `git log -n 10 --oneline`
- [x] Không discard/reset work hiện có nếu chưa hiểu.
- [x] Không hard-code Auction, Hello World, House Building hoặc tên object cụ thể trong core.
- [x] Không resolve runtime identity bằng bare name/fuzzy matching.
- [x] Mọi runtime mutation phải đi qua exact identity/trace.
- [x] Không sửa frozen V2 Ecore/Mapping chỉ để implementation mới chạy.
- [x] Nếu cần semantic-contract change thật sự, dừng phần đó, lập impact analysis và tạo versioned contract mới.
- [x] JaCaMo vẫn là execution engine.
- [x] USE vẫn là verification model/mirror.
- [x] Extension mới chỉ cho phép **pause/resume phục vụ verification**; không được biến USE thành engine điều khiển nghiệp vụ JaCaMo.
- [x] Không kill agent/MAS khi OCL fail.
- [x] Không auto-repair Goal/Belief/Artifact/Mission sau violation.
- [x] Không sửa state JaCaMo để làm OCL pass.
- [x] Không parse MAS Console text làm authoritative runtime state.
- [x] Không giả lập click Swing nút Pause/Resume của MAS Console.
- [x] Phải audit và gọi đúng runtime control API/path mà JaCaMo/Jason đang dùng.
- [x] Không giữ hai runtime verification pipelines production song song sau migration.
- [x] Không giữ UI cũ chỉ vì backward compatibility nếu nó đã bị thay thế và không còn consumer hợp lệ.

---

# 1. Target architecture phải đạt

```text
JaCaMo Runtime
     │
     │ runtime events / supported hooks
     ▼
Runtime Connector
     │
     ▼
Ordered Event / Correlation Layer
     │
     ├──────────────► normal mirror updates
     │
     ▼
Verification Checkpoint
     │
     ▼
Authoritative / verification-relevant Snapshot
     │
     ▼
Exact RuntimeKey → SemanticId → USE target
     │
     ▼
same active MSystemState
     │
     ▼
Constraint Selection
     │
     ▼
OCL Evaluation
  ┌──┴───────────┐
 PASS            FAIL
  │               │
continue          ├─ preserve failing snapshot
                  ├─ create violation record
                  ├─ request JaCaMo PAUSE
                  ▼
          all Jason agents ACK pause
                  │
                  ▼
           authoritative resync
                  │
                  ▼
            confirm violation
                  │
                  ▼
 Goal / Agent / Mission / Artifact / source trace
                  │
                  ▼
               UI report
                  │
                  ▼
                RESUME
```

## 1.1 Checkpoint policy

Primary verification checkpoints:

- [x] `SNAPSHOT`
  - authoritative initial/reconnect/resume synchronization;
  - full invariant evaluation.
- [x] `AFTER_MUTATION`
  - after a verification-relevant runtime mutation has been applied to USE;
  - targeted constraint evaluation.
- [x] `OPERATION_POST`
  - after matching successful CArtAgO operation completion;
  - postcondition + related cross-dimensional constraints.
- [x] `STREAM_BOUNDARY`
  - after a correlated event batch / stream boundary;
  - cross-dimensional/full checks where appropriate.
- [x] `OPERATION_PRE`
  - retain only for true precondition semantics;
  - not the main Goal verification checkpoint.

Do **not** make arbitrary `every N ms` polling the primary verification semantics. Timer polling may exist only as fallback/health/drift detection if already justified.

---

# 2. Phase A — Current-state audit before migration

## A1. Runtime inventory

- [x] Inventory all current runtime entry points.
- [x] Inventory all Jason listeners/hooks.
- [x] Inventory all CArtAgO listeners/loggers/hooks.
- [x] Inventory all Moise/OrgBoard runtime sources.
- [x] Inventory all polling loops.
- [x] Inventory all `RuntimeEvent` producers.
- [x] Inventory runtime queues/executors.
- [x] Inventory snapshot coordinators/services.
- [x] Inventory mutation engines.
- [x] Inventory OCL verification triggers.
- [x] Inventory reconnect/resync logic.
- [x] Inventory drift detection.
- [x] Inventory operation PRE/POST correlation code.
- [x] Inventory every place that creates/replaces `MSystem`.
- [x] Inventory every place that mutates `MSystemState`.
- [x] Inventory every place that evaluates runtime OCL.

Produce:

```text
runtime-current-inventory.md

For each path:
- producer
- event/snapshot type
- ordering model
- identity model
- mutation target
- verification trigger
- lifecycle owner
- current consumer
- KEEP / MIGRATE / REMOVE / HISTORICAL
```

## A2. Detect duplicate/legacy runtime paths

- [x] Search for legacy V1 runtime loaders.
- [x] Search for old V2 runtime mapping paths no longer used by active projection.
- [x] Search for parallel mutation engines.
- [x] Search for duplicate listener installation.
- [x] Search for multiple active event queues.
- [x] Search for private/secondary `MSystem` instances not attached to active USE session.
- [x] Search for old snapshot DTOs that are no longer consumed.
- [x] Search for old verification schedulers/timers.
- [x] Search for old “live verification dashboard” code that computes its own state instead of reading the active `MSystemState`.
- [x] Search for code that reparses source per runtime event.
- [x] Search for code that derives runtime identity from names.
- [x] Search for dead adapters retained only by historical tests.
- [x] Mark each as:
  - [x] KEEP
  - [x] MIGRATE
  - [x] REMOVE
  - [x] TEST_ONLY
  - [x] HISTORICAL_ONLY
  - [x] BLOCKED_REVIEW

## A3. UI inventory

Audit current `JaCaMoWorkbenchPanel` and related UI/actions.

- [x] List current tabs/panels/actions.
- [x] Identify ownership of:
  - [x] Project
  - [x] Trace
  - [x] Diagnostics
  - [x] Verification
  - [x] Runtime
  - [x] Binding
- [x] Identify duplicated information between tabs.
- [x] Identify UI controls tied to old runtime semantics.
- [x] Identify UI tables fed by private facade state instead of active USE state.
- [x] Identify stale V1/V2 baseline fields no longer useful to normal workflow.
- [x] Identify UI elements that expose raw runtime internals instead of verification-relevant state.
- [x] Identify buttons/actions that bypass facade/service boundaries.
- [x] Identify dead actions/menu items.
- [x] Identify old “manual sync/check” paths that conflict with checkpoint-driven verification.
- [x] Identify whether Model Browser/Object Diagram/Invariant view already provide information duplicated in Workbench.

Produce:

```text
ui-current-inventory.md

component | current purpose | data source | target status
KEEP / MERGE / REWRITE / REMOVE
```

## A4. Baseline evidence

Before changes:

- [x] Run focused runtime tests.
- [x] Run focused verification tests.
- [x] Run focused trace tests.
- [x] Run focused Workbench/UI tests.
- [x] Run Auction runtime smoke.
- [x] Record current object counts.
- [x] Record current exposed classes.
- [x] Record current Belief count.
- [x] Record current runtime checkpoint behavior.
- [x] Record current UI screenshots/evidence if existing workflow depends on visuals.
- [x] Preserve test commands + exact results.

---

# 3. Phase B — Architecture contract update

Current project documentation contains an observe-only rule. New direction intentionally adds controlled pause/resume.

## B1. Explicit architecture decision

Create an architecture decision record:

```text
ADR — Runtime Verification Pause/Resume Extension
```

Must state:

- [x] JaCaMo remains execution engine.
- [x] USE remains verification mirror.
- [x] OCL failure never directly mutates domain state.
- [x] USE may request only:
  - [x] `pause`
  - [x] `resume`
- [x] Pause/resume exists only for inspection after verification failure or explicit user request.
- [x] No kill.
- [x] No rollback in this task.
- [x] No automatic repair.
- [x] No forced Goal/Belief/Mission/Artifact mutation.
- [x] Pause request is not claimed to be instruction-level instantaneous.
- [x] JaCaMo/Jason safe-pause semantics are authoritative.
- [x] Failing snapshot is captured before control request.
- [x] After pause, an authoritative resync is required before final diagnostic is declared confirmed.
- [x] Resume requires runtime state to re-enter a valid synchronization lifecycle.

## B2. Reconcile `agent.md`

- [x] Update the old “runtime adapter forbidden to control JaCaMo” rule.
- [x] Replace it with a narrow rule:
  - runtime adapter may not control domain behavior;
  - dedicated RuntimeControlService may request pause/resume only.
- [x] Keep all existing exact identity, ordering, trace, lifecycle rules.
- [x] Do not weaken safety rules just to support control.
- [x] Add rule: verification FAIL and control failure are separate states.
- [x] Add rule: OCL ERROR/UNDEFINED must not automatically pause unless explicitly configured.
- [x] Add rule: pause/resume API availability is capability-gated.

## B3. Runtime lifecycle extension

Define explicit control state:

```text
RUNNING
PAUSE_REQUESTED
PAUSED
RESUME_REQUESTED
```

Keep synchronization state separate:

```text
OFFLINE
MODEL_READY
CONNECTING
SYNCING
LIVE
STALE
ERROR
```

- [x] Do not overload one enum with both connection and pause state.
- [x] Define legal transitions.
- [x] Define idempotent repeated pause.
- [x] Define idempotent repeated resume.
- [x] Define disconnect while paused.
- [x] Define reconnect while paused.
- [x] Define violation while `PAUSE_REQUESTED`.
- [x] Define user resume while resync is incomplete.
- [x] Define control API unavailable behavior.

---

# 4. Phase C — Exposed model/projection cleanup

This phase prevents runtime snapshots from filling USE with implementation noise.

## C1. Belief projection cleanup

- [x] Measure current Jason BeliefBase count per agent.
- [x] Compare with USE `Belief` MObject count.
- [x] Determine whether current issue is:
  - [x] true high current belief count;
  - [x] stale belief objects not deleted;
  - [x] both.
- [x] Fix add/remove lifecycle before filtering if stale objects exist.
- [x] Keep `Belief` semantic concept.
- [x] Do not mirror the complete Jason BeliefBase by default.
- [x] Expose only:
  - [x] user/domain-authored beliefs;
  - [x] beliefs explicitly required by selected OCL;
  - [x] beliefs without a more authoritative USE representation.
- [x] Keep infrastructure/bookkeeping beliefs internal/trace-only when represented better by:
  - Agent↔Artifact focus;
  - Agent↔Role/Group relation;
  - Artifact identity/state;
  - Scheme/Mission/Goal objects/links.
- [x] Filtering must use provenance/category/evidence, not Auction predicate-name blacklist.
- [x] Add negative test proving a legitimate user belief with a similar name is not dropped accidentally.

## C2. Infrastructure artifact cleanup

Audit `Console`, `TupleSpace` and similar framework artifacts.

- [x] Identify exact provenance/type/owner for `Console`.
- [x] Identify exact provenance/type/owner for `TupleSpace`.
- [x] Determine whether they are:
  - [x] project/domain-declared;
  - [x] framework/runtime infrastructure;
  - [x] required by selected OCL.
- [x] If infrastructure + not verification-relevant:
  - [x] keep runtime/trace evidence if needed;
  - [x] do not materialize exposed class/object.
- [x] Do not blacklist exact names.
- [x] Build generic projection rule:
  - framework/system artifact
  - AND not explicitly domain-selected
  - AND no verification-relevant observable state
  - → `INTERNAL_ONLY`.
- [x] Add synthetic test proving a user-selected artifact is still exposable even if its type resembles an infrastructure artifact.

## C3. Preserve verification vocabulary

After cleanup, verify the intended exposed vocabulary remains available where supported:

- [x] Agent
- [x] AgentGoal
- [x] selective Belief
- [x] Workspace
- [x] Artifact + concrete domain Artifact subclasses
- [x] Organization
- [x] Group
- [x] Role relationship / approved representation
- [x] Scheme
- [x] OrganizationalGoal
- [x] Mission

Do not reintroduce old internal wrappers merely for runtime convenience.

---

# 5. Phase D — Verification snapshot contract

## D1. Define `VerificationSnapshot`

Create a typed snapshot contract independent from UI.

Required metadata:

- [x] `snapshotId`
- [x] monotonic runtime sequence / boundary sequence
- [x] timestamp
- [x] stream generation
- [x] checkpoint type
- [x] correlation id(s)
- [x] connection lifecycle state
- [x] source runtime capability/version
- [x] snapshot completeness status
- [x] provenance/evidence

## D2. Minimum state to capture

### Agent dimension

- [x] stable agent identity
- [x] selected AgentGoal state if supported/required
- [x] selected domain Beliefs only when required
- [x] relevant runtime relations used by OCL

### Organization dimension

- [x] Organization identity
- [x] Group identity
- [x] Role participation/context
- [x] Scheme runtime instance identity
- [x] Scheme specification identity
- [x] Scheme arguments
- [x] Mission identity
- [x] Mission commitment
- [x] OrganizationalGoal identity
- [x] Goal runtime state
- [x] Goal committed agents if authoritative
- [x] Goal achieved agents if authoritative
- [x] Goal decomposition/order metadata required by verification

### Environment dimension

- [x] Workspace identity
- [x] ArtifactId/runtime identity
- [x] concrete artifact type
- [x] verification-relevant observable property values
- [x] operation correlation when checkpoint is operation-related

### Cross-dimensional relations

Capture only evidence-backed relations required by selected constraints:

- [x] Agent ↔ Role/Group
- [x] Group ↔ Scheme responsibility
- [x] Agent ↔ Mission commitment
- [x] Mission ↔ OrganizationalGoal
- [x] Scheme ↔ OrganizationalGoal
- [x] Agent ↔ Artifact focus if required
- [x] Goal ↔ relevant Artifact mapping only when explicit trace/evidence exists

## D3. Explicitly exclude snapshot noise

Do not expose by default:

- [x] full Jason PlanLibrary
- [x] full intention/event stack
- [x] all transient options/intended means
- [x] all runtime beliefs
- [x] MAS Console output
- [x] framework Console/TupleSpace artifacts
- [x] transport-only DTOs
- [x] duplicate state already represented authoritatively elsewhere

## D4. Snapshot retention

Implement bounded history:

- [x] configurable ring buffer, not unbounded history.
- [x] retain at least:
  - failing snapshot;
  - previous snapshot;
  - checkpoint/correlation context.
- [x] choose a sane bounded default after measurement.
- [x] no memory leak when runtime runs for a long time.
- [x] snapshot history is diagnostic evidence, not active domain model.
- [x] history must survive pause long enough for UI inspection.
- [x] history reset/replacement semantics defined on reconnect/new project.

---

# 6. Phase E — Checkpoint capture

Reuse existing checkpoint infrastructure where semantically correct; do not rewrite only for naming.

## E1. SNAPSHOT

- [x] Capture after initial authoritative synchronization.
- [x] Capture after reconnect authoritative resync.
- [x] Capture after pause authoritative resync.
- [x] Capture after resume resync if required by runtime control semantics.
- [x] Full OCL set eligible.
- [x] No verification while lifecycle is STALE/ERROR.

## E2. AFTER_MUTATION

Trigger only when mutation is verification-relevant.

- [x] Goal state change.
- [x] Mission commitment change.
- [x] Role/group relation change.
- [x] Artifact observable state change.
- [x] Scheme state/arguments change if used by constraints.
- [x] Selected AgentGoal/Belief change if used by constraints.
- [x] Skip UI/infrastructure-only mutation.
- [x] Build dependency set to select affected constraints.

## E3. OPERATION_PRE

- [x] Exact MObject.
- [x] Exact MOperation if supported.
- [x] Exact arguments.
- [x] Preserve pre-state once.
- [x] Evaluate only actual preconditions.
- [x] PRE false behavior explicitly configured.
- [x] Do not treat PRE as a Goal completion checkpoint unless evidence says so.

## E4. OPERATION_POST

- [x] Require matching successful operation exit/correlation.
- [x] Preserve `@pre`.
- [x] Evaluate postconditions.
- [x] Evaluate related Artifact/Goal consistency constraints when dependencies match.
- [x] OP_FAIL/abort → POST constraints SKIPPED, not fabricated FAIL.

## E5. STREAM_BOUNDARY

- [x] Define exact source of boundary.
- [x] No guessed “quiet time”.
- [x] Correlation/generation must be known.
- [x] Evaluate cross-dimensional constraints.
- [x] Use full fallback if dependency analysis cannot prove a safe subset.

---

# 7. Phase F — Snapshot → USE MSystemState synchronization

## F1. One active system

- [x] One active `MModel`.
- [x] One active `MSystem`.
- [x] One active `MSystemState`.
- [x] Same active system used by:
  - [x] Object Diagram
  - [x] Model Browser
  - [x] OCL evaluator
  - [x] verification engine
  - [x] Goal View
  - [x] violation diagnostics
- [x] Remove private/secondary verification system if still present.
- [x] No UI-local shadow state.

## F2. Exact target resolution

For every snapshot item:

```text
RuntimeKey
→ exact trace/binding
→ SemanticId
→ target MClass/MObject/association/attribute
```

- [x] Trace miss → explicit diagnostic.
- [x] Trace miss must not mutate a guessed object.
- [x] Ambiguity → reject.
- [x] Stale generation → reject.
- [x] Old workspace callback → reject.
- [x] No bare-name fallback.

## F3. Mutation semantics

Support:

- [x] object create
- [x] object remove/tombstone according to approved policy
- [x] attribute update
- [x] undefined/unset
- [x] link create
- [x] link delete
- [x] ordered relation update where required
- [x] no duplicate application
- [x] rollback USE mutation transaction if a multi-step local mutation fails

## F4. Drift/resync

- [x] Preserve existing authoritative drift detection if still correct.
- [x] On successful authoritative resync, require zero supported-subset drift.
- [x] Do not evaluate current runtime OCL on partially synchronized state.
- [x] Mark state STALE/ERROR explicitly when resync fails.

---

# 8. Phase G — OCL checkpoint routing

## G1. Constraint registry

Every runtime constraint must declare:

- [x] id
- [x] origin: CORE / CASE / USER / TRANSLATED
- [x] context
- [x] required classes/features
- [x] required capabilities
- [x] applicable checkpoints
- [x] severity
- [x] enforcement policy
- [x] provenance
- [x] dependencies used for targeted re-evaluation

## G2. Enforcement policy

At minimum:

```text
REPORT_ONLY
PAUSE_ON_FAIL
```

- [x] Default unknown/user constraint must not silently gain pause authority.
- [x] Only explicitly approved HARD constraints use `PAUSE_ON_FAIL`.
- [x] OCL `false` ≠ OCL evaluation ERROR.
- [x] Undefined/error handling stays distinct.
- [x] SKIPPED stays distinct.
- [x] Capability missing → SKIPPED/UNSUPPORTED, never fake PASS.

## G3. Routing

- [x] `SNAPSHOT` → full eligible invariants.
- [x] `AFTER_MUTATION` → dependency-targeted invariants.
- [x] `OPERATION_PRE` → preconditions only.
- [x] `OPERATION_POST` → postconditions + linked invariants.
- [x] `STREAM_BOUNDARY` → cross-dimensional/full eligible invariants.
- [x] Unknown dependency → conservative full fallback.

## G4. Initial Goal-centric constraint set

Implement generic/evidence-backed rules first:

- [x] Goal structure / decomposition consistency.
- [x] Sequence ordering consistency only where exact source semantics are represented.
- [x] Mission ↔ Goal membership consistency.
- [x] Agent achievement/commitment responsibility consistency where runtime evidence supports it.
- [x] Scheme instance context integrity.
- [x] Goal state ownership/context integrity.

Add case-specific Auction rules separately:

- [x] selected Goal ↔ AuctionArtifact consistency.
- [x] no Auction-specific rule in core registry.
- [x] every case rule has source/domain rationale.

---

# 9. Phase H — Violation capture & diagnostics

## H1. `VerificationViolation` contract

Must contain:

- [x] violation id
- [x] constraint id/name
- [x] result = FAIL
- [x] severity
- [x] enforcement policy
- [x] checkpoint type
- [x] failing snapshot id
- [x] runtime sequence/generation
- [x] timestamp
- [x] context MObject
- [x] involved MObjects
- [x] expected/actual values when derivable
- [x] Goal id/context if relevant
- [x] Agent id if relevant
- [x] Mission id if relevant
- [x] ArtifactId/type if relevant
- [x] source trace/provenance
- [x] pause request state
- [x] post-pause confirmation state

## H2. Preserve failing state

Order on FAIL:

```text
1. Freeze/copy failing VerificationSnapshot
2. Create VerificationViolation
3. Publish diagnostic
4. Request pause
5. After pause → authoritative resync
6. Re-evaluate/confirm
7. Update violation status
```

- [x] Never overwrite the original failing snapshot with post-pause state.
- [x] Preserve before/after-pause snapshots separately.
- [x] UI must be able to compare them.

## H3. Source trace

For each involved semantic element:

- [x] resolve via TraceIndex/provenance.
- [x] show file path.
- [x] show exact source span/line if extractor has it.
- [x] show semantic element id.
- [x] show runtime identity.
- [x] show mapping/trace id.
- [x] if exact line unavailable, show file + element and label line unavailable.
- [x] never guess line numbers.
- [x] dynamic runtime object should trace to both runtime creation identity and source/spec origin where available.

## H4. Diagnostic output example target

```text
OCL VIOLATION: INV-G3

Checkpoint:
STREAM_BOUNDARY #128

Scheme:
sch_a1 : doAuction

Goal:
decide
state = satisfied

Agent:
bob

Mission:
mAuctioneer

Artifact:
a1 : AuctionArtifact
running = true

Expected:
running = false

Source:
auction-os.xml → goal decide
auction_capabilities.asl → +!decide
AuctionArtifact.java → stop()

JaCaMo control:
PAUSED

Snapshot:
before-pause #128
confirmed #129
```

---

# 10. Phase I — JaCaMo Pause/Resume integration

This is the intentional new architecture capability.

Approved semantics: official Jason ExecutionControl gates synchronous reasoning
cycles. Pause targets every Jason agent in the current runtime with exact agent
incarnation identity; PAUSED requires every requested agent's boundary ACK. Agent
arrival/departure changes admission and must not produce a false all-agent ACK.
Unsupported scheduler/controller ownership is capability-gated and fails closed.
This is **not atomic pause of the whole JaCaMo platform**: in-flight CArtAgO
operations and Moise/OrgBoard activity may complete after Jason agents pause.
MASConsoleGUI.setPause and GUI click automation are not runtime control.

## I1. Audit real JaCaMo/Jason control path

- [x] Locate source code behind MAS Console `Pause` button.
- [x] Locate source code behind Resume/continue behavior.
- [x] Identify exact runtime controller/service/API.
- [x] Verify behavior for:
  - [x] one agent
  - [x] whole MAS
  - [x] reasoning cycle
  - [x] CArtAgO operations in flight
  - [x] Moise/OrgBoard activity
- [x] Record what “paused” guarantees and what it does not guarantee.
- [x] Pin evidence to actual dependency/source version.
- [x] Do not infer from button label alone.

Produce:

```text
jacamo-pause-resume-audit.md
```

## I2. Introduce narrow control abstraction

Create a dedicated boundary such as:

```text
RuntimeControlService
- capability()
- requestPause(reason)
- requestResume()
- state()
```

Exact names may differ, but responsibilities must remain narrow.

- [x] Verification engine must not call Swing UI.
- [x] UI must not call JaCaMo internals directly.
- [x] Connector must not embed OCL logic.
- [x] RuntimeControlService must not mutate domain state.

## I3. Pause flow

On eligible HARD FAIL:

- [x] deduplicate repeated pause requests for same active violation/boundary.
- [x] transition `RUNNING → PAUSE_REQUESTED`.
- [x] call real JaCaMo runtime pause path.
- [x] wait for authoritative reasoning-boundary ACK from every requested Jason agent.
- [x] reconcile agent creation/removal/incarnation changes during pause before declaring PAUSED.
- [x] transition to `PAUSED`.
- [x] capture authoritative snapshot/resync.
- [x] re-run confirmation constraint set.
- [x] mark violation:
  - [x] CONFIRMED
  - [x] TRANSIENT/NOT_REPRODUCED
  - [x] CONFIRMATION_ERROR
- [x] do not auto-resume.

## I4. Resume flow

User chooses Resume:

- [x] reject resume if no active runtime.
- [x] `PAUSED → RESUME_REQUESTED`.
- [x] call real resume path.
- [x] resubscribe/revalidate stream generation if needed.
- [x] wait for ExecutionControl resume re-enable/ACK for the current agent set.
- [x] authoritative resync after resume before returning LIVE.
- [x] only then return verification lifecycle to `LIVE`.
- [x] continue checkpoint capture.
- [x] preserve previous violation history.

## I5. Failure modes

Test explicitly:

- [x] pause API unavailable.
- [x] pause request throws.
- [x] pause confirmation timeout.
- [x] disconnect during pause request.
- [x] runtime dies instead of pausing.
- [x] resume API fails.
- [x] reconnect while paused.
- [x] repeated FAIL storm.
- [x] user clicks Resume multiple times.
- [x] project/workspace replaced while paused.
- [x] partial agent ACK/timeout never becomes PAUSED.
- [x] agent appears/disappears during pause.
- [x] in-flight CArtAgO operation may complete while Jason agents are paused.

No silent fallback to kill.

---

# 11. Phase J — Goal View

Goal View is a **view over the same active USE state**, not a second semantic model.

## J1. Data source

- [x] Read only facade/service APIs.
- [x] Use active `MSystemState`.
- [x] Use existing TraceIndex/verification result services.
- [x] No parser logic in UI.
- [x] No runtime mutation in UI.
- [x] No duplicated Goal objects.

## J2. Show Goal structure

Per Scheme runtime instance:

```text
sch_a1 : doAuction
└── auction
    └── sequence
        ├── start
        ├── bid
        └── decide
```

- [x] distinguish scheme specification vs runtime scheme instance.
- [x] distinguish same goal spec across multiple runtime scheme instances.
- [x] show decomposition operator.
- [x] show order where meaningful.
- [x] support nested sequence/parallel for House Building.

## J3. Show runtime state

- [x] waiting
- [x] enabled
- [x] satisfied
- [ ] impossible
- [x] unknown/unavailable explicitly

`impossible` remains unsupported: pinned Moise 1.1's public SchemeBoard
`goalState(Scheme,Goal,Committed,Achieved,State)` exposes waiting/enabled/satisfied.
`MoiseGoalObservationTest.unavailableStateAndMalformedOrDuplicatedEvidenceFailClosed`
rejects an invented impossible observation; Goal View reports UNKNOWN / UNAVAILABLE.
This item cannot be completed by guessing from an unsatisfied goal or failed operation.

## J4. Show responsibility

- [x] Mission.
- [x] committed Agent(s).
- [x] Role/Group context where supported.
- [x] achievedBy only if authoritative runtime evidence exists.

## J5. Show verification

- [x] PASS/FAIL badge per relevant Goal.
- [x] constraint id.
- [x] related Agent.
- [x] related Mission.
- [x] related Artifact.
- [x] expected/actual.
- [x] source link/navigation.
- [x] failing snapshot id.
- [x] paused/running state.

---

# 12. Phase K — Workbench/UI redesign and cleanup

Current Workbench historically had separate Project / Trace / Diagnostics / Verification / Runtime / Binding areas. New UI must follow the new workflow, not preserve old tab count for compatibility.

## K1. Target user workflow

Primary workflow:

```text
Import project
→ inspect USE model
→ connect runtime
→ see LIVE state
→ snapshots/checkpoints happen automatically
→ OCL violation
→ JaCaMo pauses
→ UI opens violation/Goal context
→ inspect source + snapshot
→ Resume
```

## K2. Proposed UI information architecture

Audit first, then converge toward:

### Project
- [x] imported project/status.
- [x] active model/projection compatibility.
- [x] runtime connection state.
- [x] no low-value hash/debug noise in primary area.

### Verification
- [x] current verification status.
- [x] latest checkpoint.
- [x] latest PASS/FAIL.
- [x] violations list.
- [x] failing snapshot.
- [x] expected/actual.
- [x] pause state.
- [x] Resume action when paused.

### Goal View
- [x] goal tree.
- [x] runtime state.
- [x] responsibility.
- [x] related violation.
- [x] source navigation.

### Trace / Source
- [x] semantic id.
- [x] runtime id.
- [x] USE target.
- [x] source file/span.
- [x] mapping/projection provenance.

### Diagnostics
- [x] connection errors.
- [x] trace misses.
- [x] unsupported capability.
- [x] OCL ERROR/UNDEFINED.
- [x] pause/resume control errors.

## K3. Merge/remove old tabs

After inventory:

- [x] Merge old `Binding` tab into Trace/Source if it has no independent workflow.
- [x] Merge old raw `Runtime` and `Verification` dashboards if they duplicate the same state.
- [x] Remove any tab that reads a deprecated private runtime state.
- [x] Remove old V1-specific UI.
- [x] Remove stale “manual live check” controls replaced by checkpoint automation.
- [x] Remove duplicate diagnostics shown in multiple tabs.
- [x] Remove legacy raw-event tables from primary UI; keep optional debug view only if tests/support need it.
- [x] Do not remove a component until all production consumers are migrated.

## K4. Runtime controls in UI

- [x] Show JaCaMo control state:
  - RUNNING
  - PAUSE_REQUESTED
  - PAUSED
  - RESUME_REQUESTED
- [x] Show synchronization state separately.
- [x] `Resume` enabled only when valid.
- [x] Optional manual `Pause` may exist for debugging only if runtime API supports it and requirements approve it.
- [x] OCL automatic pause must use same service as manual control.
- [x] No UI button directly owns control semantics.

## K5. Violation-focused UX

On FAIL:

- [x] automatically select violation.
- [x] show failing snapshot, not only current mutable state.
- [x] show Goal if relevant.
- [x] show involved Agent/Mission/Artifact.
- [x] show source.
- [x] preserve last valid state comparison when available.
- [x] allow user to navigate to USE Object Diagram.
- [x] allow user to navigate to source location.
- [x] Resume only after user inspection/explicit action.

## K6. JaCaMo MAS Console UI boundary

- [x] Do not fork/modify upstream MAS Console UI merely to add plugin behavior.
- [x] Do not depend on Swing button click automation.
- [x] Use capability-gated Jason ExecutionControl at reasoning-cycle boundaries; do not use MASConsoleGUI.setPause.
- [x] Label PAUSED as “Jason agents paused”; never claim atomic whole-platform suspension.
- [x] If upstream UI must be changed due to a proven API blocker, document blocker and isolate patch; do not mix it into USE Workbench logic.

---

# 13. Phase L — Remove obsolete runtime implementation

Only after new path passes focused tests.

## L1. Delete old production paths

For every item marked `REMOVE` in Phase A:

- [x] confirm no production caller.
- [x] confirm no reflective/service-loader caller.
- [x] migrate tests first.
- [x] delete production code.
- [x] delete dead configuration.
- [x] delete dead resource JSON/OCL/runtime mapping only if no historical/reproducibility requirement.
- [x] delete dead feature flags.
- [x] delete dead DTOs.
- [x] delete dead event types.
- [x] delete dead UI adapters.
- [x] delete duplicate facade methods.
- [x] delete obsolete docs statements.

## L2. No permanent dual architecture

Final production path must not contain:

```text
old live verifier
+
new snapshot verifier
```

or:

```text
old private MSystem
+
new active session MSystem
```

or:

```text
old runtime dashboard state
+
new active verification state
```

unless a documented compatibility mode is explicitly required.

## L3. Historical evidence

If old code/resources are required only for reproducibility:

- [x] move/mark historical where project policy allows.
- [x] ensure production does not load them automatically.
- [x] tests for current production must not depend on historical fallback.
- [x] no silent fallback to V1/old runtime mapping.

---

# 14. Phase M — Remove obsolete UI implementation

- [x] Delete UI classes with no remaining menu/action/tab consumer.
- [x] Delete old table models bound to removed runtime DTOs.
- [x] Delete old listeners that subscribe directly to deprecated runtime events.
- [x] Delete duplicate refresh timers.
- [x] Delete duplicated verification result rendering.
- [x] Delete obsolete Binding/Runtime/Verification panels after merge.
- [x] Remove menu actions pointing to removed panels.
- [x] Remove dead preference/config keys.
- [x] Remove stale screenshots/docs.
- [x] Ensure UI uses facade/service APIs only.
- [x] Ensure closing/reopening Workbench does not install duplicate runtime listeners.
- [x] Ensure workspace replacement detaches old UI consumers.

---

# 15. Phase N — Auction goal-verification vertical slice

This is the primary demonstrator.

## N1. Runtime objects

Verify at runtime:

- [x] 5 Agent instances.
- [x] 2 Scheme instances: `sch_a1`, `sch_a2`.
- [x] goal occurrences are instance-qualified.
- [x] missions/commitments are instance-qualified.
- [x] 2 relevant AuctionArtifact instances.
- [x] infrastructure artifacts excluded from exposed verification model according to policy.
- [x] Belief count is selective and current, not accumulated history.

## N2. Goal state

For each scheme instance:

- [x] auction/root
- [x] start
- [x] bid
- [x] decide

- [x] runtime state updates are observed.
- [x] no global `bid`/`decide` identity collision between `sch_a1` and `sch_a2`.

## N3. Checkpoint behavior

- [x] Goal state mutation → checkpoint.
- [x] Artifact observable mutation → checkpoint.
- [x] operation POST → checkpoint.
- [x] stream boundary → cross-dimensional check.
- [x] failing checkpoint snapshot retained.

## N4. Deliberate violation tests

Create controlled test fixtures/fault injection without hard-coding production core.

At minimum:

- [x] Goal sequence violation.
- [x] Responsibility/commitment violation where runtime fixture permits.
- [x] Goal ↔ Artifact inconsistency.
- [x] OCL false causes pause request.
- [x] failing snapshot preserved.
- [x] exact Goal/Scheme/Agent/Artifact identified.
- [x] source trace displayed.
- [x] Resume continues runtime.

---

# 16. Phase O — Generic regression

## O1. Hello World

- [x] Same production pipeline.
- [x] No Auction branch in core.
- [x] selective Belief projection works.
- [x] GUIConsole domain artifact remains if genuinely project-declared/verification-relevant.
- [x] Goal sequence representation works.
- [x] Goal View renders.
- [x] runtime connect/snapshot works.
- [x] no infrastructure artifact rule accidentally removes domain artifact.

## O2. House Building

Critical genericity test:

- [x] dynamic organization creation works.
- [x] dynamic artifacts work.
- [x] dynamic schemes work.
- [x] nested sequence/parallel goal structure works.
- [x] role inheritance/context remains correct where supported.
- [x] multiple runtime instances do not collide.
- [x] checkpoint/snapshot pipeline works without relying on static JCM completeness.
- [x] Goal View handles nested tree.
- [x] no Auction-specific logic.

## O3. Negative genericity

- [x] unknown runtime entity → no mutation.
- [x] ambiguous identity → reject.
- [x] unsupported Goal runtime field → UNKNOWN/SKIPPED.
- [x] no guessed AgentGoal↔OrganizationalGoal mapping.
- [x] no guessed Goal↔Artifact relation.

---

# 17. Phase P — Concurrency, ordering & performance

## P1. Ordered ingestion

- [x] callback enqueue remains fast/non-blocking.
- [x] bounded queue.
- [x] monotonic sequence.
- [x] correlation IDs.
- [x] backpressure explicit.
- [x] no silent drops.
- [x] old-stream callbacks rejected.

## P2. Snapshot cost

Measure:

- [x] snapshot frequency.
- [x] snapshot build latency.
- [x] USE mutation latency.
- [x] OCL evaluation latency.
- [x] pause request latency.
- [x] post-pause resync latency.
- [x] memory footprint of snapshot history.
- [x] runtime overhead on Auction.
- [x] runtime overhead on House Building.

Do not optimize by sacrificing correctness/order/trace.

## P3. Pause storm protection

- [x] one active pause request at a time.
- [x] repeated FAILs attach to active violation or queue diagnostics without repeated control calls.
- [x] configurable policy for distinct simultaneous HARD violations.
- [x] no deadlock between verification worker and runtime control worker.
- [x] no pause request while synchronization lifecycle is invalid unless explicitly handled.

---

# 18. Phase Q — Test plan

Add/update tests at appropriate layers.

## Q1. Snapshot tests

- [x] `VerificationSnapshotContractTest`
- [x] `SnapshotCheckpointRoutingTest`
- [x] `SnapshotRetentionTest`
- [x] `ReconnectSnapshotTest`
- [x] `PausedResyncSnapshotTest`

## Q2. Projection tests

- [x] `BeliefProjectionPolicyTest`
- [x] `BeliefRemovalLifecycleTest`
- [x] `InfrastructureArtifactProjectionTest`
- [x] domain artifact negative control

## Q3. Mutation/trace tests

- [x] exact object update
- [x] link update
- [x] removal
- [x] trace miss
- [x] ambiguity
- [x] stale generation
- [x] duplicate event

## Q4. OCL routing tests

- [x] full snapshot constraints
- [x] targeted mutation constraints
- [x] PRE only
- [x] POST only
- [x] stream boundary cross-dimensional
- [x] unknown dependency fallback
- [x] FAIL vs ERROR vs SKIPPED

## Q5. Violation diagnostics tests

- [x] involved object extraction
- [x] expected/actual
- [x] source file/span
- [x] no guessed line
- [x] before-pause snapshot preserved
- [x] after-pause confirmation preserved

## Q6. Runtime control tests

- [x] pause success
- [x] resume success
- [x] pause idempotency
- [x] resume idempotency
- [x] API unavailable
- [x] timeout
- [x] disconnect
- [x] repeated FAIL
- [x] resume after resync
- [x] all-agent ACK
- [x] partial ACK/timeout
- [x] agent creation/removal during pause
- [x] in-flight CArtAgO operation

## Q7. UI tests

- [x] Workbench opens.
- [x] no duplicate listener after reopen.
- [x] violation appears.
- [x] Goal View selects failing goal.
- [x] source navigation available.
- [x] Resume enabled/disabled correctly.
- [x] old removed tabs/actions absent.
- [x] Object Diagram still shows same active system state.

## Q8. End-to-end

- [x] Auction PASS run.
- [x] Auction deliberate FAIL → pause → inspect → resume.
- [x] Hello World regression.
- [x] House Building regression.
- [x] full relevant Maven reactor.
- [x] packaged plugin smoke.

---

# 19. Phase R — Documentation cleanup

Review and update at minimum:

- [x] `docs/agent/agent.md`
- [x] `docs/agent/task.md`
- [x] architecture docs
- [x] runtime adapter docs
- [x] verification docs
- [x] trace/binding docs
- [x] UI/workflow docs
- [x] README
- [x] known limitations
- [x] compatibility/release docs if behavior is externally visible

Search and remove/mark stale statements:

- [x] “observe-only / never controls JaCaMo” without pause/resume exception.
- [x] old runtime pipeline.
- [x] old Workbench tab workflow.
- [x] old private verification system claims.
- [x] old full BeliefBase projection claims.
- [x] old infrastructure artifact projection claims.
- [x] old Goal Model/View deferred statements that conflict with current direction.
- [x] stale “not implemented” status for completed work.
- [x] stale “supported” status for removed paths.

Preserve historical documents as historical; do not rewrite old evidence as current evidence.

---

# 20. Required final runtime behavior

A valid happy path:

```text
1. Import JaCaMo project
2. Build selective USE model
3. Connect runtime
4. Authoritative snapshot
5. State = LIVE
6. JaCaMo executes
7. Relevant checkpoints captured
8. USE state updated
9. OCL PASS
10. Continue
```

A valid violation path:

```text
1. Checkpoint captured
2. Snapshot mapped to same active MSystemState
3. HARD OCL = FALSE
4. Failing snapshot preserved
5. VerificationViolation created
6. Pause requested through real JaCaMo runtime API
7. Every requested Jason agent ACKs its reasoning boundary; control reaches PAUSED
8. Authoritative resync
9. Violation re-evaluated/confirmed
10. UI shows:
    - constraint
    - snapshot
    - Goal
    - Agent
    - Mission
    - Artifact
    - expected/actual
    - source file/span
11. User chooses Resume
12. JaCaMo resumes
13. USE resynchronizes
14. State returns LIVE
15. Verification continues
```

---

# 21. Explicit non-goals for this task

Do not implement:

Checked exclusions below mean that their absence/control boundary was verified,
not that these non-goals were implemented. Conditional cleanup items likewise
preserve frozen historical bytes where reproducibility requires them.

- [x] model checking over all future states.
- [x] temporal logic/liveness proof.
- [x] rollback JaCaMo execution.
- [x] automatic repair.
- [x] automatic Goal achievement.
- [x] automatic belief correction.
- [x] automatic role/mission reassignment.
- [x] agent kill/restart on violation.
- [x] arbitrary Java body → OCL semantic inference.
- [x] generic deontic Moise/NPL → OCL equivalence without formal evidence.
- [x] parsing console logs as runtime truth.
- [x] GUI click automation as runtime control.
- [x] unbounded runtime history.

---

# 22. Definition of Done

Task chỉ DONE khi tất cả điều sau có executable evidence:

## Architecture

- [x] New snapshot/checkpoint runtime flow is the single production verification path.
- [x] Pause/resume extension documented and narrowly scoped.
- [x] No accidental domain control by USE.
- [x] Old conflicting runtime path removed or historical-only.

## Model/projection

- [x] USE exposed model contains verification-relevant state only.
- [x] Belief no longer floods model through full BeliefBase mirroring.
- [x] Belief removals are reflected correctly.
- [x] framework Console/TupleSpace-like artifacts are internal-only by generic rule when irrelevant.
- [x] Scheme/OrganizationalGoal/Mission available for Goal verification.
- [x] runtime identities are instance-qualified and traceable.

## Runtime

- [x] checkpoints work.
- [x] snapshots are bounded and reproducible.
- [x] same active `MSystemState` is used everywhere.
- [x] reconnect/resync remains correct.
- [x] no silent event drop.
- [x] no guessed runtime target.

## OCL

- [x] checkpoint-aware routing works.
- [x] FAIL/ERROR/SKIPPED distinct.
- [x] HARD constraints can request pause.
- [x] unsupported semantics stay unsupported.

## Pause/Resume

- [x] real JaCaMo/Jason API path audited.
- [x] no Swing button automation.
- [x] automatic pause on approved HARD violation works.
- [x] failing snapshot preserved before pause.
- [x] post-pause authoritative confirmation works.
- [x] explicit Resume works.
- [x] no kill fallback.

## Diagnostics

- [x] exact violating constraint.
- [x] exact relevant USE objects.
- [x] Goal/Agent/Mission/Artifact context when applicable.
- [x] source file/span when available.
- [x] no fabricated line.
- [x] before/after-pause snapshots inspectable.

## UI

- [x] workflow follows runtime verification use case.
- [x] Goal View works.
- [x] violation workflow works.
- [x] old redundant runtime/binding/verification UI removed or merged.
- [x] no duplicate UI listener/state.
- [x] UI contains no domain/runtime mapping logic.

## Genericity

- [x] Auction E2E PASS.
- [x] Auction deliberate violation → pause → diagnose → resume PASS.
- [x] Hello World regression PASS.
- [x] House Building regression PASS.
- [x] no case-specific core branches.

## Cleanup

- [x] dead runtime code removed.
- [x] dead UI code removed.
- [x] dead resources/config removed.
- [x] stale docs updated.
- [x] historical evidence preserved correctly.

## Final evidence

Final report must include:

```text
1. Architecture before
2. Architecture after
3. Runtime paths removed
4. UI paths removed/merged
5. Projection cleanup before/after
6. Checkpoint implementation
7. Snapshot contract
8. OCL routing
9. Pause/resume API evidence
10. Violation trace example
11. Auction E2E evidence
12. Hello World evidence
13. House Building evidence
14. Focused tests
15. Full regression
16. Known limitations
17. Final git diff / commit(s)
```

Do not claim DONE from screenshots alone.

Do not claim DONE from compiling alone.

Do not claim DONE while old conflicting production runtime/UI paths remain.

---

# 23. Evidence checkpoint — 2026-10-05

## Object Diagram audit across runtime states — follow-up 2026-10-05

The later persistent-view audit found three production counterexamples to the
earlier single-cut checks. They are corrected without changing the projection
policy, adding a second model/pipeline, or modifying JaCaMo/Jason/CArtAgO/Moise.

1. Dynamic artifact/property/organisation schema extension called
   `MSystem.reset()`. The same `MSystem` then contained a **different
   `MSystemState`**. `NativeRuntimeMutationEngine` now initializes additive
   stored slots and ordinary association link sets in the existing state through
   `MSystemState.initializeStoredModelExtensions()`. Existing state, object states,
   values and link references survive schema extension; unsupported initialized /
   derived slots and read-only mutation fail before slot initialization.
2. USE `MObject.equals()` compares object names. An authoritative replacement can
   reuse a name for another object/class: in the actual House Building run,
   `ora4mas` changed from an organization node binding to a Workspace binding.
   The old diagram retained the old `ObjectNode`, causing an attribute-owner error
   on paint. Native atomic notification now refreshes object bindings before
   inserting new links, using actual objects/links from the same active state.
   Hidden objects/links and fixed node positions are preserved. No semantic relation is
   invented from the reused name.
3. Binary association-class rendering searched only visible node maps. Recreating
   a hidden enactment at resync caused a native `NullPointerException`; inserting
   a new enactment whose endpoint was hidden had the same unsupported path.
   Binary link-object edges now resolve current visible/hidden nodes, initialize
   hidden edges before reveal, and retain hidden membership. Rebinding drops
   calculated position strategies tied to disposed edge waypoints, while retaining
   fixed placement. The deterministic test covers hidden recreation, insertion
   with a hidden endpoint, reveal and actual native paint.

`ObjectDiagramLifecycleEvidence` is test-only instrumentation over one persistent
native `NewObjectDiagramView`. It checks every atomic notification and each
recorded phase: same Session/MSystem/MSystemState, exact visible-plus-hidden native
object/link sets, actual `ObjectNode` object references, no orphan endpoints,
facade snapshot object parity, native painted attribute values, and unchanged
SOIL after render. Each phase exports complete JSON/SOIL and actual native PNGs:
`*-all.png` includes every native object/link; `*-domain.png` is a clearly counted
native selection excluding Belief/AgentGoal/OrganizationalGoal/Mission for legibility.
No screenshot or selected view alone is used as complete-state evidence.

Authoritative source/native relation comparison uses exact current CArtAgO agent
identities (not a multi-instance JCM declaration such as `companyC`), workspace
identities, artifact ownership/focus, contextual role assignments, mission
commitments, Group→Scheme responsibility and Organization→Scheme ownership.
Initial MODEL_READY may lack enacted roles and fail role multiplicity while agents
are held at the execution gate. Disconnect retains a **last observed** diagram;
this does not establish current producer state. Agent→Organization context follows
the approved Agent/role→Group→Organization associations; no direct association is
fabricated. House Building acceptance remains a bounded progressing run.

Executable focused evidence:

- `target/object-diagram-binding-focused.log`: BUILD SUCCESS 22:32:15 +07,
  **22 tests**, including the two new native stored-state extension tests,
  `NativeObjectDiagramBindingTest`, generic environment lifecycle, coordinator
  rollback/idempotency, object count and Session activation tests.
- `target/object-diagram-stable-state-confirmation.log`: Auction PASS through
  8 phases / 130 atomic checks, no graph errors; later House paint exposed the
  reused-name binding defect described above (this attempted reactor FAILED).
- `target/object-diagram-house-confirmed.log`: BUILD SUCCESS 22:44:11 +07;
  7 House Building phases / 649 atomic checks, no graph errors. Model-ready
  **29 objects / 28 links**, live authoritative cut **285 / 501**; repeated exact
  Bridge cut reproduces the state and all audited source relations exactly.
  State identity `711283065` is unchanged across all seven phases.
- `target/object-diagram-hidden-link-counterexample.log`: FAILED 23:20:27 +07
  before the hidden association-class fix, with the precise null node in
  `BinaryAssociationClassOrObject` reached through native atomic rebinding.
- `target/object-diagram-hidden-link-fixed-focused.log`: BUILD SUCCESS 23:22:23
  +07, **17 tests** (2 core stored-state tests, 3 native diagram tests, 12
  coordinator tests), zero failures/errors/skips. The controlled property test
  proves A→B→A→removed/null through the same native painted node and active state.

The earlier fixture-only failures (missing standalone diagram dimension properties
and using a multi-instance declaration as an agent endpoint) were corrected in
test instrumentation; they are not production projection defects. Their failed
logs are retained in `target/object-diagram-*.log` and are not reported as PASS.

`target/object-diagram-multistate-confirmed-reactor.log` FAILED 22:58:17 +07
because a new test incorrectly required Auction's short-lived `running=true`
to occur at an accepted Bridge cut. The run observed only `false`; source may
advance between observation cuts. This was a test claim error, not evidence of
stale native values. The test now reports whether both values were actually
observed, asserts the completed value, and separately proves update/removal
behavior with deterministic A→B→A→removed native mutation tests. A real run that
misses a transient does not claim complete transient/event coverage.

`target/object-diagram-final-reactor.log` completed BUILD SUCCESS 23:14:16 +07,
**728 tests / 170 suites**, zero failures/errors/skips. It includes all three
real cases and packaging/GUI/replay regressions, before the subsequent hidden
association-class counterexample and fix. It is preserved as earlier evidence.

Files changed by this Object Diagram follow-up (the larger pre-existing migration
diff is retained, not attributed to this follow-up):

- Production: `use-core/.../uml/sys/MObjectState.java`, `MSystemState.java`;
  `use-gui/.../objectdiagram/NewObjectDiagram.java`, `NewObjectDiagramView.java`;
  `use-plugin/.../codegrounded/runtime/NativeRuntimeMutationEngine.java`.
- Tests/evidence: new `MSystemStateExtensionTest.java`,
  `NativeObjectDiagramBindingTest.java`, `ObjectDiagramLifecycleEvidence.java`;
  updated `RuntimeVerificationCoordinatorTest.java`, `AuctionPauseResumeIT.java`,
  `HelloWorldSemanticInventoryIT.java`, `HouseBuildingDomainProjectionIT.java`.
- Documentation: this `task.md` evidence section and
  `docs/agent/runtime-current-inventory.md`. Packaged architecture/ADR resources
  and projection policy are unchanged by this follow-up.

Final-code case gates have PASS evidence in
`target/object-diagram-hidden-link-final-reactor.log` and the reconciled artifact
index `target/object-diagram-final-case-summary.json`:

| Case/profile | Recorded phases | Atomic native checks | Model-ready objects/links | Accepted resync objects/links | Belief objects | Same native state identity |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Auction | 8 | 134 | 17 / 14 | 36 / 95 | 0 | 951399761 |
| Hello World AUTO | 12 | 569 | 38 / 54 | 47 / 118 | 6 | 373808124 |
| Hello World FULL | 12 | 572 | 38 / 54 | 47 / 118 | 6 | 1874165917 |
| House Building | 7 | 788 | 29 / 28 | 285 / 501 | 217 | 456130271 |

All **39 phase cuts / 2,063 accepted atomic native checks** have zero recorded
graph errors; each run retains its original MSystem and MSystemState. Source
comparison is exact at explicit accepted authoritative cuts (four for Auction,
one per Hello profile, two for House). Live counts are observations, not fixed
case-specific constants. Auction's start cut had 50 objects / 100 links; its
completed, paused, resumed, disconnected and reconnected cuts had 36 / 95.
The earlier run's 36 / 91 is also valid for its different observed source cut.

Auction's completed graph contains 5 agents, 2 workspaces (`main`, organization
workspace), 2 application artifacts, 1 organization, 1 group, 2 runtime schemes,
12 organizational goals, 6 missions and 5 contextual role link objects. Native
links include **10 joins, 10 focus, 2 artifact ownership, 5 role enactments,
1 organization/group, 2 organization/scheme, 2 group/scheme and 10 mission
commitments**; goal/mission structure and agent goal-state relations account for
the remaining links. Generic `Role`, `RoleEnactment`, Plan/PlanLibrary/PlanBody,
infrastructure Console/TupleSpace and duplicate main workspace objects are absent.
`auctioneer`/`participant` remain the approved contextual association classes
under current section C3, not generic Role classes or agent attributes.

Multiplicities are read from the final native `paused.use`: joins/focus are
many-to-many; Workspace[1]↔Artifact[*]; Organization[1]↔Group[*]; contextual
auctioneer Agent[1]↔auctionGroup[*]; participant Agent[0..300]↔auctionGroup[*];
Organization[0..1]↔Scheme[*]; Group[*]↔Scheme[*]. No Auction-specific override was
added. The single domain class `GUIConsole` in Hello World is the application's
declared artifact type, not automatically exposed infrastructure Console.

Final evidence directories (each includes `diagram-lifecycle/summary.json`,
complete per-phase JSON/SOIL, all-object native PNGs, counted domain PNGs and
`atomic-checks.json`):

- `use-plugin/target/runtime-control-acceptance/auction-1791217668797`
- `use-plugin/target/hello-world-object-audit/AUTO-1791217724167`
- `use-plugin/target/hello-world-object-audit/FULL-1791217875212`
- `use-plugin/target/house-domain-live/1791218041370`

Native images were visually inspected at import, start, completed resync,
pause/reconnect, and across all three cases. Automated graph and native-painted
attribute checks cover every recorded phase. House is a bounded progressing
acceptance run; source completeness stays PARTIAL and unsupported J3 impossible
remains unchecked. No full platform atomic pause or unobserved transient coverage
is claimed. Source case studies and upstream core are unchanged.

Final focused command (17 tests, PASS):

```powershell
mvn -B -pl use-plugin -am test `
  '-Dtest=MSystemStateExtensionTest,NativeObjectDiagramBindingTest,RuntimeVerificationCoordinatorTest' `
  '-Dsurefire.failIfNoSpecifiedTests=false'
```

Unfiltered final reactor command (no test filter or skip flags):

```powershell
mvn -B -pl use-plugin -am verify
```

`target/object-diagram-hidden-link-final-reactor.log`: **BUILD SUCCESS at
23:36:41 +07**, duration 13:59; all six reactor modules/package gates PASS.
**729 tests / 170 suites**, 579 unit + 150 integration tests, zero
failures/errors/skips. Counts were reconciled against the XML report for each
suite actually executed in this log, not accumulated stale target reports;
`target/object-diagram-final-test-summary.json` records the reconciliation.

| Module | Unit tests | Integration tests |
| --- | ---: | ---: |
| jacamo-bridge-contract | 18 | 0 |
| jacamo-bridge-jacamo | 57 | 0 |
| use-core | 14 | 1 |
| use-gui | 1 | 129 |
| use-plugin | 489 | 20 |

Includes actual Auction pause/resume, both Hello World profiles, House Building,
129 Shell regressions, native USE GUI/session/export/recompile, authenticated
transport/identity/rollback/replay, legacy exclusion, isolated release packaging
and managed Auction Workbench startup. The follow-up's runtime/diagram audit is
complete for the documented supported subset. The checklist remains **710 / 711**;
only J3 `impossible` is unchecked for the previously audited pinned API limitation.
Final git inspection: branch `main`, HEAD `55f38081`, staged diff empty,
`git diff --check` PASS. The retained working tree has 205 tracked status entries
and 124 untracked entries from the larger ongoing migration plus this follow-up;
this is not a clean-tree claim and those entries are not all this follow-up's work.
Final status is saved in `target/object-diagram-final-git-status.txt`. No branch,
commit, discard/reset, or upstream-core edit was made by this follow-up.
The previous 20:09 closure below is historical evidence, not this rerun's result.

## Final supported-subset closure — 20:09:22 +07

**SUPPORTED_SUBSET_COMPLETE**, not full JaCaMo semantic completeness. All 49
Definition-of-Done items and Q8's final reactor/package gates now have executable
evidence. The only unchecked item is **J3 `impossible`**: Moise 1.1's pinned public
SchemeBoard goal-state observation provides waiting/enabled/satisfied; unsupported
evidence is rejected and rendered UNKNOWN / UNAVAILABLE. Its exact source/API
audit and negative test remain beside J3. No invented Goal state closes this box.

Final command (unfiltered; no test/build skips):

```powershell
Set-Location 'D:\_CODE_BANK\Project_\08_Thesis\use'
mvn -B -pl use-plugin -am verify
```

`target/runtime-final-clean-package-reactor.log`: **BUILD SUCCESS**, all six reactor
modules SUCCESS, finished 2026-10-05T20:09:22+07:00, elapsed **16:48 min**.
`target/runtime-final-test-summary.json` reconciles each executed log suite against
its current Surefire/Failsafe XML report (not leftover reports from earlier runs):
**723 tests / 168 suites, 0 failures, 0 errors, 0 skips**. Breakdown:

| Module | Unit tests | Integration tests |
| --- | ---: | ---: |
| jacamo-bridge-contract | 18 | 0 |
| jacamo-bridge-jacamo | 57 | 0 |
| use-core | 12 | 1 |
| use-gui | 1 | 129 |
| use-plugin | 485 | 20 |
| Total | **573** | **150** |

The plugin ITs include real Auction control, Managed Auction Workbench, Hello AUTO
and FULL, House Building, four native USE GUI cases, three Step GUI cases, native
USE export/recompile, installed-JAR replay smoke, exact Session OCL, GUI staging,
legacy-authority packaging and all three ReleasePackageIT cases. The archive's
declared inventory, source bytes and checksum sidecar passed together. Package
document SHA-256 values remain equal to the frozen-for-this-run catalog in
`target/runtime-final-package-document-hashes.json`; task.md/agent.md are not
manifest-listed package entries. The earlier document-byte race remains recorded
below as a failed attempt, not relabelled PASS.

Final actual case inventory (paths relative to `use-plugin/target`; counts describe
the stated observed cut, not a hard-coded expectation of all random agents):

| Case / evidence directory | Native classifiers / objects / links | Exposed / current Jason Beliefs |
| --- | --- | --- |
| Auction control — runtime-control-acceptance/auction-1791205042225 | 17 / 36 / 95 | 0 / 305 |
| Auction Workbench — workbench-acceptance/auction-1791205580674 | 17 / 36 / 96 | 0 / 305 in the separate control source audit |
| Hello AUTO — hello-world-object-audit/AUTO-1791205096675 | 20 / 47 / 118 | 6 / 197 |
| Hello FULL — hello-world-object-audit/FULL-1791205264285 | 20 / 47 / 118 | 6 / 197 |
| House Building — house-domain-live/1791205515460 | 30 / 285 / 501 | 217 / 1358 |

Auction Workbench's `authoritative-relations.json` / `gui-acceptance.json` prove
exact source-to-native endpoint parity and repeated resync parity: **10 joins,
10 domain focuses, 5 contextual role links, 10 mission commitments, 2 workspaces**.
Its 96 links include authoritative Goal commitment/achievement and instance-qualified
Scheme/Mission links. The distinct control run's 95 links are its own observed
state, not a missing-link tolerance. Both tests reject missing, extra, orphan,
duplicate, stale or guessed endpoint identities.

Its classifiers are Agent, AgentGoal, Artifact, AuctionArtifact, Belief, Group,
Mission, Organization, OrganizationalGoal, Scheme, Workspace, auctionGroup,
auction_Organization, auction_capabilities_Agent, auctioneer, doAuction_Scheme,
participant. The last two role names represent approved **contextual association
classes**, not standalone Role entities; their five native link objects are
Agent↔Group enactment links. Actual role end multiplicities are
`auctioneer: Agent[1] ↔ auctionGroup[*]` and
`participant: Agent[0..300] ↔ auctionGroup[*]`. Generic joins/focus/commitment ends
remain `* ↔ *`; Artifact workspace ownership is `Workspace[1] ↔ Artifact[*]`.
`Role`, `RoleEnactment`, `bobRole`, `aliceRole`, Plan, PlanLibrary, PlanBody and
irrelevant framework Console/TupleSpace objects are absent. These claims do not
erase association-class names that the current C3 approved representation retains.

Auction has five program-subclass agent objects, two workspaces, two domain
AuctionArtifacts, one Organization, one Group, two runtime Scheme instances,
six Missions (two specifications/four instance-qualified), twelve OrganizationalGoals
(four specifications/eight occurrences), five role link objects and zero Beliefs
or AgentGoals. Baseline audit was already **17 / 36 / 72, Belief 0**, with 305 raw
Jason beliefs. This migration adds actual runtime Goal/mission/achievement state
and relations; it does not claim to remove 305 exposed Beliefs again. Earlier full
BeliefBase mirroring was over-projection, while executable removal-lifecycle tests
independently prove deletion. Projection policy 2.1.0 requires authored/domain
evidence, compiled verification demand and no better authoritative representation.
Framework artifact ownership/provider/type evidence suppresses irrelevant objects
without a Console/TupleSpace name blacklist; Hello's five project GUIConsole
objects remain exposed and continue syncing normally.

Final report requirements 1–17:

| Required evidence | Implementation / executable result |
| --- | --- |
| 1. Architecture before | Phase A runtime/UI inventories below record mirroring/full-check triggers, legacy consumers and the actual 17/36/72 baseline; their historical claims are preserved. |
| 2. Architecture after | One DefaultJaCaMoFacade → official typed Bridge snapshot/events → native RuntimeVerificationCoordinator → the active USE Session System/State → routed OCL → immutable violation → gated control/resync workflow. EDT is the sole native writer; UI consumes evidence. |
| 3. Runtime paths removed | 61 legacy Java sources moved out of main into test scope; production contains no legacy transformer/mirror/verifier path. LEGACY_V2 production loading fails closed. LegacyAuthorityPackagingIT, ProductionAuthorityPhase8Test and V2 isolation tests PASS. |
| 4. UI paths removed/merged | Five primary tabs: Project, Verification, Goal View, Trace / Source, Diagnostics. Runtime/binding/verification duplication and Skeleton/Binding entry paths removed; listener attach/detach/reset tests and native GUI tests PASS. |
| 5. Projection cleanup | Actual inventories and policy/multiplicities above; no Plan/Role wrapper projection, no full BeliefBase mirror, exact instance-qualified Goal/Mission/Scheme state retained. SelectiveVerificationProjectionTest and generic provider/lifecycle negative controls PASS. |
| 6. Checkpoints | Typed AFTER_MUTATION, PRE, POST, SNAPSHOT and explicit source STREAM_BOUNDARY with exact watermarks/correlation. A quiet timer is not evidence of a boundary. SnapshotCoordinatorTest and CheckpointRoutingTest PASS; accepted/rejected-cut callbacks are delivered or explicitly rejected, never silently dropped. |
| 7. Snapshot contract | Immutable 1.1 metadata/values/links/SOIL/profile/results, original and confirmation cuts separately pinned. Tail: 8 cuts/16 MiB serialized, each cut ≤8 MiB, 5 fixed pins, 64 violations, one writer/queue 8. Retention/overflow/clear/reconnect tests PASS; isolated heap clear released 3,436,144 bytes and 260 mutations remained bounded. |
| 8. OCL routing | Seven native CORE Goal rules; CASE/USER profiles stay separate. FAIL, ERROR and SKIPPED remain distinct. Every rule defaults REPORT_ONLY; only explicit fingerprint-bound HARD approval grants PAUSE_ON_FAIL. Exact successful typed POST supports @pre; failed/uncorrelated operations cannot fabricate POST. Native operation/routing/constraint tests PASS. |
| 9. Pause/resume API | Plugin BridgeLocalExecutionControl uses the pinned real Jason ExecutionControl at reasoning boundaries with per-incarnation ACK/capability gating. MASConsoleGUI.setPause is output-only and unused for control. Real all/partial ACK, membership change/timeout, CArtAgO in-flight and GroupBoard/NPL in-flight child JVM tests PASS; no kill/repair fallback. |
| 10. Violation trace | Final Auction failure cut **320** is immutable before request; all **5/5** agents ACK; authoritative post-pause cut **398** remains false → CONFIRMED. Exact decide context, source identity, Bob/mission/Artifact context and original values remain inspectable; unavailable XML position is not fabricated. RuntimeControlServiceTest also proves TRANSIENT_NOT_REPRODUCED and CONFIRMATION_ERROR. Explicit Resume ACK/resync returns LIVE. |
| 11. Auction E2E | Both final actual Auction ITs PASS; same active System/State and unchanged original sources. Control IT proves approved failure/pause/diagnose/resume and recorded replay; Workbench IT proves relations parity, recorded replay, reanalysis and complete Step replay. verification-paused.png was inspected: Jason agents paused, ACK 5/5, original/confirmation cuts and in-flight limitation; four control-state transitions also have executable tests. |
| 12. Hello World | AUTO and FULL final ITs PASS; 5 agents, 5 project GUIConsole, 13 authored Goals (root plus sequence ordinals 0..11) SATISFIED, six selected Beliefs. Same active System, exact identity parity, reconnect, recorded replay/reanalysis/Step and unchanged sources; final Goal View inspected. |
| 13. House Building | Final IT PASS: 22 agents, 8 dynamic AuctionArt, 1 House, 13 Goals/10 Missions, actual organization/group/scheme, exact owner relation and reconnect/resync same cut. Nested sequence/parallel Goal View inspected; root ENABLED with satisfied children. This is a bounded supported-state run, not whole-platform termination. |
| 14. Focused tests | Logs below retain each passing focused gate and corrections. Replay/control/Auction confirmation reactor: **177 PASS**, 12 suites, 19:34:57. Generic late-operation replay repro failed before the fix; EVENT/SNAPSHOT discovery and unrecorded-contract rejection regressions now PASS, preserving strict recorded state/result hashes. Counts overlap the final reactor and are not summed. |
| 15. Full regression | Final **723/723 PASS**, 168 suites, all six modules/package/verify SUCCESS; full command, log and XML reconciliation above. All native GUI/export/installed-JAR/release gates executed, not skipped. |
| 16. Known limitations | Jason pause is not atomic platform suspension; already in-flight CArtAgO/Moise activity may finish. Pinned goal-state API does not expose impossible. Unknown/ambiguous identities, unsupported formal norm/temporal semantics and unsupported contracts stay fail closed/partial. Published measurements are observed verification cost, not an uninstrumented platform slowdown experiment. Historical invalid replay bundles remain rejected. |
| 17. Final git / commits | Direct work on **main, HEAD 55f38081**; no new branch, commit, staging, reset or upstream JaCaMo edits. Existing work was preserved. Final status/diff checks are recorded in target/runtime-final-git-summary.json; 201 tracked paths and 121 untracked paths, staged 0; tracked diff **200 files, +7317 / −10456 lines**. Diff includes retained prior work, not solely this continuation. |

Key changed implementation files are BridgeLocalExecutionControl,
SnapshotCoordinator, RuntimeVerificationCoordinator, VerificationSnapshot,
RuntimeControlService, NativeRuntimeReplay, DefaultJaCaMoFacade,
JaCaMoWorkbenchPanel, GoalViewPanel and the focused tests indexed below. Contract,
ADR/API audit, inventories, maintained architecture/UI/release/limitations docs
and this checklist carry the same boundary. No second production pipeline was
introduced. The final replay correction records baseline operation availability:
later typed descriptors are replayed at their original event/snapshot, preventing
future operations from changing earlier PRE results.

## Historical execution checkpoints

The following entries retain the actual earlier results, failures, measurements
and intermediate pending statements. Final current completion/evidence is the
20:09:22 closure above; these older runs are not additional unique test totals.

## Genericity / ordering / measured costs — current compiled code

The unfiltered `target/runtime-final-supported-reactor.log` completed at 19:51:51:
**723 tests executed / 722 PASS / 1 release document-byte failure**, no errors or
skips. All **573 unit tests** and **149/150 ITs** passed. This remains a failed
final reactor, and Q8's reactor/package boxes remain open until the rebuilt gate.
The four actual case tests and native GUI/export/replay paths below passed on the
same compiled code; only package documentation was subsequently held/rebuilt.

Phase O evidence:

| Actual case | Evidence directory under use-plugin/target | Classifiers / objects / links | Exposed / current raw Beliefs |
| --- | --- | --- | --- |
| Auction pause/resume | runtime-control-acceptance/auction-1791204011335 | 17 / 36 / 96 | 0 / 305 |
| Hello AUTO | hello-world-object-audit/AUTO-1791204062169 | 20 / 47 / 118 | 6 / 197 |
| Hello FULL | hello-world-object-audit/FULL-1791204216926 | 20 / 47 / 118 | 6 / 197 |
| House Building | house-domain-live/1791204453228 | 30 / 285 / 501 | 217 / 1358 |

Hello summary.json proves sameActiveSystem, identityParity, sourceUnchanged,
recorded replay, current-profile reanalysis and complete Step replay. Its five
project GUIConsole objects survive; all thirteen Goals show authored sequence
ordinals 0..11 and SATISFIED. House summary.json proves 22 agents, eight dynamically
created AuctionArt objects, one House, actual organisation/group/scheme creation,
direct owner relation and exact reconnect/resync on the same system. Both Goal View
PNGs were visually inspected; House shows nested sequence/parallel and an ENABLED
root with satisfied children. This is a bounded supported-state regression, not
a claim that the entire House platform has terminated.

Negative genericity: NativeRuntimeBridgeCoverageTest, NativeEnvironmentRelationsTest,
GenericFunctionalRuntimeProjectionTest, CodeGroundedPhase7Test, MoiseGoalObservationTest
and GoalViewSnapshotTest pass unknown/ambiguous/stale rejection, unavailable Goal
state and no guessed AgentGoal–organizational-Goal or Goal–Artifact links.

Phase P1/P3: SnapshotCoordinatorTest proves rejected/accepted-cut callback delivery,
explicit overflow, exact watermarks and typed boundary causation; LocalTcpBridgeTransportTest
proves idle connection, batched ACK, concurrent subscription/reconnect and exact
snapshot-covered callbacks; BridgeMirrorStateMachineTest proves duplicate/gap,
stale/incarnation rejection. Callback paths enqueue through bounded offer/transport
queues without OCL/network waiting. RuntimeControlServiceTest and
ReasoningCycleGateTest prove one active request, repeated failure coalescing,
distinct-violation policy, ACK lifecycle, disconnect/stale admission and independent
verification/control workers. SnapshotRetentionTest plus the isolated measured
heap-clear/260-mutation run prove bounded history and release.

Phase P2 measurements from the actual JSON counters above (means in milliseconds):

| Metric | Auction | House Building |
| --- | --- | --- |
| Retained checkpoint frequency | 317 cuts / 22.658 s = 13.99/s | 197 cuts / 37.339 s = 5.28/s |
| Snapshot build mean / maximum | 2.434 / 21.274 | 21.501 / 178.912 |
| USE mutation mean / maximum | 1.619 / 567.415 | 16.453 / 3780.548 |
| OCL processing mean / maximum | 0.453 / 23.787 | 0.473 / 65.073 |
| Pause request / all-agent ACK | 3.433 / 32.301 | no pause requested in this case |
| Post-pause authoritative resync | 1837.457 | no pause requested |
| Resume request / ACK / resync | 3.231 / 32.842 / 1817.911 | no resume requested |
| Last snapshot serialized bytes | 96,562 | 526,047 |
| Retained serialized bytes / tail cuts | 1,253,892 / 8 | 4,862,882 / 8 |
| Single-writer high watermark | 2 | 2 |

Counters include observed no-state/evidence-only processing and schema/resync
mutations; they are not pure invariant-only microbenchmarks. The first of exactly
two ordered control resync samples is post-pause (total minus the last/resume
sample). Serialized bytes measure captured payloads, not JVM object overhead;
the separately scoped GC heap delta is above. These measurements report actual
verifier/control cost, not a controlled uninstrumented JaCaMo slowdown comparison.

## Maintained documentation / packaging boundary

Phase R review covers agent.md/task.md, 02-system-architecture, the accepted ADR,
runtime-event-identity, runtime/UI inventories, pause API audit, 12-plugin-ui-workflow,
15-build-release-operations, 16-research-evidence-boundaries, root/plugin README
and KNOWN-LIMITATIONS. Current documents describe one native production path,
five primary tabs, exact trace/capability checks, selective authored beliefs,
framework ownership policy and narrow ExecutionControl with non-atomic limits.
01-vision-scope was searched for conflicting runtime/Goal/control statements;
its source/baseline research boundaries remain valid. Frozen compatibility/release
manifests and historical sections are preserved and explicitly labelled historical.

ReleasePackageIT compares the archive's exact bytes with the declared source files.
The 19:35 unfiltered run exposed a documentation timing error: KNOWN-LIMITATIONS
changed after package had created the ZIP. The source and ZIP must be rebuilt
together. All manifest-listed document inputs are now held unchanged during the
replacement gate; task.md/agent.md are not package entries. The failed checksum
gate is retained honestly, and final passing evidence is supplied in the closure above.

## Auction replay/control/GUI confirmation — 19:35 +07

`target/runtime-auction-replay-confirmation-reactor.log`: **177 PASS / 12 suites**,
zero failures/errors/skips, six-module package/verify PASS, completed 19:34:57 +07.
Counts are **45 plugin unit / 130 core+GUI IT / 2 real Auction IT**; no overlap is
added from earlier logs. Command:

```powershell
mvn -B -pl use-plugin -am verify `
  '-Dtest=NativeRuntimeReplayTest,NativeRuntimeReanalysisTest,NativeOperationCheckpointTest,RuntimeVerificationCoordinatorTest,NativeStepReplayTest,PausedResyncSnapshotTest,RuntimeControlServiceTest,CheckpointRoutingTest' `
  '-Dsurefire.failIfNoSpecifiedTests=false' `
  '-Dit.test=OCLExpressionIT,ShellIT,AuctionPauseResumeIT,ManagedAuctionWorkbenchIT' `
  '-Dfailsafe.failIfNoSpecifiedTests=false'
```

The first filtered attempt `runtime-auction-replay-confirmation-gate.log` stopped
at use-core because its older Failsafe selected no IT; it is not a passing gate.
The command above includes the actual core/GUI IT names and runs those parents.

Phase N evidence:
`runtime-control-acceptance/auction-1791203478106/summary.json` records five ACKs,
failure cut **396** captured before request, confirmation cut **399 CONFIRMED**,
explicit Resume/resync, same System/State and immutable original cut. Native
inventory is **17 classifiers / 36 objects / 95 links / 0 exposed Belief**, versus
305 current raw Jason beliefs. There are two Scheme instances, eight instance
Goal occurrences plus four specification goals, and four instance Missions plus
two specification Missions. Source XML/ASL/Java files remain unchanged.

Its performance.json records **403** cuts: AFTER_MUTATION 291, PRE 2, POST 2,
SNAPSHOT 22, STREAM_BOUNDARY 86. verification-paused.png and goal-view.png were
visually inspected: five primary tabs, exact failing `decide`, distinct original/
confirmation cuts, ACK 5/5, explicit Resume and in-flight activity limitation.
The sequence/responsibility faults are the USE-only
`GoalConstraintViolationTest.representedSequenceOrdinalsAndCurrentResponsibilityFailOnExactContexts`
fixtures; the real CASE fault inverts the explicitly traced decide/Artifact
condition while leaving JaCaMo domain state untouched.

`workbench-acceptance/auction-1791203518118/gui-acceptance.json` records PASS,
sameActiveSystem, sourceUnchanged, relationResyncParity, recordedReplay,
reanalysis and complete Step replay. Exact authoritative edge parity in this run:
**10 joins / 10 domain focuses / 5 contextual roles / 10 commitments / 2 workspaces**.
The earlier passing run had nine focuses/commitments; the oracle compares actual
source endpoints, never assumes a random bidder always commits/focuses.

The new unrecorded-contract rejection test passes alongside EVENT and SNAPSHOT
operation-discovery regressions. The unfiltered final reactor is recorded separately.

## Replay operation discovery correction — 19:27 +07

`target/runtime-final-all-gates-pass.log` is a **failed** unfiltered gate despite
its filename: at 19:10:17, all 570 unit tests passed, but AuctionPauseResumeIT and
ManagedAuctionWorkbenchIT rejected replay at journal ordinals 1155 and 1142.
The actual all-agent ACK, immutable failure, confirmation and resume assertions
had already passed; the rejected replay is not passing final acceptance.

Exact evidence: Auction PRE `opId(0,start,a1,bob)` precedes the first authoritative
`start` descriptor snapshot (ordinal 1257 in
`runtime-control-acceptance/auction-1791201636329/recorded-replay/runtime.jsonl`).
Live PRE correctly records `PRE_EXACT_OPERATION_UNAVAILABLE`; the old export put
future operation declarations into the baseline schema, so replay used them early.
The same defect is reproduced without Auction by
`NativeRuntimeReplayTest.runtimeOperationDiscoveryDoesNotBecomeAvailableBeforeItsRecordedDescriptor`:
`target/runtime-operation-discovery-repro.log` fails at ordinal 2.

The coordinator now retains immutable baseline operation identities. Bundle export
includes those operations only; later operations are installed by their actual
typed descriptor EVENT/SNAPSHOT. Unrecorded late PRE/POST contracts fail closed.
No hash comparison, exact binding, native operation correlation or original
evidence was relaxed or rewritten. Replay manifest remains 1.1.0; this fixes schema
export ordering without changing the Bridge wire/semantic contract.

`target/runtime-operation-discovery-focused-confirmed.log`: **29 PASS**, zero
failures/errors/skips, six-module focused reactor, completed 19:26:58 +07:

```powershell
mvn -B -pl use-plugin -am test `
  '-Dtest=NativeRuntimeReplayTest,NativeRuntimeReanalysisTest,NativeOperationCheckpointTest,RuntimeVerificationCoordinatorTest,NativeStepReplayTest' `
  '-Dsurefire.failIfNoSpecifiedTests=false'
```

Both EVENT and SNAPSHOT late discovery reproduce exact results after the fix;
baseline native PRE/POST and `@pre` still replay. The earlier
`runtime-operation-discovery-focused.log` failed on a test-fixture accessor typo,
corrected against the actual RuntimeSnapshot record before the passing gate.
The subsequent gate includes explicit unrecorded-contract rejection plus real
Auction control and Workbench acceptance; its result is recorded at completion.

## Isolated snapshot-history heap measurement — 19:13 +07

`use-plugin/target/runtime-heap-evidence/snapshot-heap.json`: **PASS**, JDK 21.0.5,
SerialGC, `-Xms128m -Xmx256m`, four explicit GCs per measurement (minimum used heap).
The native fixture warms up, creates forty cuts with 200,000-character values,
retains eight, then clears history while the active model/latest snapshot/journal
remain alive. Used heap **19,330,192 → 15,894,048 bytes**, releasing **3,436,144
bytes** of historical copies; serialized retained data was **4,316,634 bytes**.
After 260 mutations the tail remains eight cuts / **716,909 serialized bytes**.
This measures an isolated history delta, not per-case/process heap or total JaCaMo
slowdown. Per-case timing/serialized counters have their separate scope below.

Repeated with the final compiled code after its 573 unit tests:
`snapshot-heap-final.json` / `run-final-confirmed.log` **PASS**. Used heap
**19,333,224 → 15,897,080 bytes**; the released-history delta is again **3,436,144
bytes**, with identical serialized budgets and eight retained cuts after 260
mutations. The earlier `run-final.log` is a failed harness invocation from the
wrong working directory; it is not measurement evidence.

Reproduction artifact: `target/runtime-heap-evidence/SnapshotHeapEvidence.java`
(SHA-256 `81cd89516946fbbf5fc42ed8cd33098f671347a87a3e485a2d58bf224d4044ec`),
with compile.log/run.log. From `use-plugin`, use the exact `java.class.path` property
of its current SnapshotRetentionTest Surefire XML:

```powershell
$report = [xml](Get-Content -Raw 'target/surefire-reports/TEST-org.tzi.use.plugins.jacamo.codegrounded.SnapshotRetentionTest.xml')
$fixtureClasspath = ($report.testsuite.properties.property | Where-Object name -eq 'java.class.path').value
javac -proc:none -cp $fixtureClasspath -d 'target/runtime-heap-evidence' 'target/runtime-heap-evidence/SnapshotHeapEvidence.java'
java '-Xms128m' '-Xmx256m' '-XX:+UseSerialGC' '-Djava.awt.headless=true' `
  -cp ((Resolve-Path 'target/runtime-heap-evidence').Path + ';' + $fixtureClasspath) `
  org.tzi.use.plugins.jacamo.codegrounded.SnapshotHeapEvidence 'target/runtime-heap-evidence/snapshot-heap.json'
```

## Final regression corrections — 18:26 +07

`target/runtime-full-gate-assertion-confirmed.log`: **30 PASS**, zero failures,
errors or skips, completed 18:26:36 +07. Six-module focused command:

```powershell
mvn -B -pl use-plugin -am test `
  '-Dtest=AuctionExternalOclProfileTest,CodeGroundedPhase7Test,DomainRuntimeProjectionTest,ExternalOclConstraintServiceTest,V2HardeningAuditTest,NativeSemanticAdapterTest' `
  '-Dsurefire.failIfNoSpecifiedTests=false'
```

These tests now distinguish external-profile outcomes from the seven native Goal
rules: structural checks can PASS, while absent Goal-state capability stays
SKIPPED. Supported Moise domain completeness is declared separately from global
Moise event completeness. Rejected profiles cannot install vacuous unsupported
property checks. Historical runtime mapper/DTO-adapter tests read test-only files
and assert that their production files are absent.

The preceding full gate (`target/runtime-final-full-reactor.log`, 18:15) failed on
eight obsolete assertions and two obsolete main-source paths. It is not passing
evidence. A fresh unfiltered `mvn -B -pl use-plugin -am verify` is running; final
three-case/package acceptance and checklist closure await that result.

`target/runtime-final-native-policy-tests.log`: **38 PASS**, completed 18:08:14
+07. `CheckpointRoutingTest.nativeHardSeverityDoesNotGrantPauseAuthorityWithoutExplicitApproval`
proves that native CORE HARD severity does not grant control authority. All rules
default REPORT_ONLY; explicit fingerprint-bound approval is required to pause.
This corrected the implicit native-rule pause discovered by the actual Auction
acceptance. No JaCaMo domain state or upstream source was changed to make OCL pass.

`target/runtime-policy-authority-correction.log` includes the real four-scenario
`BridgeExecutionControlTest`: all-agent ACK/resume, partial ACK and membership,
CArtAgO in-flight operation, and actual GroupBoard/NPL role adoption in flight.
`jacamo-bridge-jacamo/target/runtime-control-api/execution-organisation.json`
records `orgBoardCompletedWhilePaused=true`, exact two-agent ACKs and resume.
This is executable evidence of the Moise/OrgBoard limitation, not a global barrier.

## Checklist-to-executable-evidence index

The unfiltered reactor has passed all **482 plugin unit tests**, plus **18 neutral
contract / 57 adapter / 12 core / 1 GUI unit tests**. Final case/package results
are recorded separately below when the integration gate completes. Existing
focused logs in this section remain independently reproducible evidence; their
test counts overlap and must not be added together as unique tests.

| Checklist | Executable evidence in the current implementation |
| --- | --- |
| 0/B; narrow authority, exact identity, no domain repair, version impact | RuntimeControlContractTest, ReasoningCycleGateTest, V2HardeningAuditTest, LegacyV2OclIsolationTest, RuntimeControlServiceTest; accepted ADR and immutable upstream case source hashes |
| 1/E; checkpoint source/routing and supported hooks | CheckpointRoutingTest, SnapshotCoordinatorTest (including BridgeCheckpointSource boundary cases), NativeOperationCheckpointTest; typed correlation and exact watermarks |
| C/D2; selected authored Belief and infrastructure ownership | SelectiveVerificationProjectionTest (five tests), GenericAgentProjectionTest, OfficialCartagoAdapterTest, NativeEnvironmentRelationsTest; exact provider proof and domain negative controls |
| D2; Scheme/Goal/Mission/Agent/Artifact state and exact links | GenericFunctionalRuntimeProjectionTest, MoiseGoalObservationTest, VerificationSnapshotContractTest, DomainRuntimeProjectionTest; actual Auction instance-qualified cuts |
| D4; bounded immutable history and lifecycle reset | SnapshotRetentionTest, VerificationSnapshotContractTest, ReconnectSnapshotTest, PausedResyncSnapshotTest, RuntimeVerificationCoordinatorTest; tail eviction, pins, byte overflow, explicit clear and new generation |
| F; one active system, transactions, duplicate/stale/ambiguity/drift | RuntimeVerificationCoordinatorTest (11), CodeGroundedPhase7Test (8), NativeEnvironmentRelationsTest, NativeUseSessionActivationTest, NativeRuntimeBridgeCoverageTest; actual Session/Object Diagram identity assertions |
| G; native Goal rules, external separation, policy authority | NativeConstraintInstallerTest, CodeGroundedPhase5Test, CheckpointRoutingTest (4), GoalConstraintViolationTest; seven CORE Goal rules, exact false contexts and capability SKIPPED |
| H; original/confirmed cuts and exact context/source | RuntimeControlServiceTest, GoalVerificationWorkbenchTest, PausedResyncSnapshotTest, NativeRuntimeFacadeIntegrationTest; source position 0 explicitly unavailable, accepted OCL bytes survive file change/deletion |
| I; real API, all/partial ACK, membership and in-flight limits | JasonConsolePauseSemanticsTest; four real child-JVM BridgeExecutionControlTest scenarios; RuntimeControlServiceTest (10) covers confirmation variants, timeout, unavailable/throwing APIs, disconnect/lost runtime ownership, storm, reconnect and explicit resume |
| I5; dead runtime cannot become LIVE/PAUSED | ManagedRuntimeWorkflowTest.deadProducerAndTimeoutCannotBecomeLive, RuntimeControlServiceTest.disconnectAndRuntimeReplacementDuringAckFailClosedAndResumeErrorKeepsOriginalConfirmation, transport close/coverage tests; runtime death is detected as lost connection/ownership, not a fabricated pause ACK |
| J/K/M; Goal tree, selected failure, source/navigation, four states, detached consumers | GoalViewSnapshotTest, GoalVerificationWorkbenchTest (3), JaCaMoWorkbenchPanelTest (21), ReplayStepFacadeTest; actual headful GoalWorkbenchEvidence images and same-System diagrams |
| L; remove dual production path, preserve test-only history | LegacyV2OclIsolationTest, V2HardeningAuditTest, ProductionAuthorityPhase8Test, DefaultJaCaMoFacadeTest; LegacyAuthorityPackagingIT/ReleasePackageIT from the 17:55 packaged correction gate, then repeated in final unfiltered verify |
| Q1 requested SnapshotCheckpointRoutingTest | Covered by CheckpointRoutingTest rather than creating a second routing test/implementation |
| Q2 requested BeliefProjectionPolicyTest / BeliefRemovalLifecycleTest / InfrastructureArtifactProjectionTest | Covered by the combined SelectiveVerificationProjectionTest and GenericAgentProjectionTest lifecycle/provider cases; no parallel projection |
| Q3/Q4/Q5/Q6/Q7 | Coordinator/environment identity tests; CheckpointRoutingTest/NativeOperationCheckpointTest; violation/Goal UI/control tests listed above |

## Authoritative Auction relation correction — 18:49 +07

The unfiltered gate `target/runtime-final-verified-reactor.log` ended at 18:40:01:
all 570 unit tests and 149/150 integration tests passed, but the Workbench test
still assumed ten focus links and ten mission commitments. This is **not** a
passing final reactor. The separate Auction pause/resume, both Hello World modes,
House Building, native GUI/export/OCL and packaged smoke all passed.

The failing run `target/workbench-acceptance/auction-1791200336076/failure-replay`
contains actual `UPSERT_MOISE_SCHEME` source observations: each Scheme has four
committed agents (alice, bob, francois, maria); giacomo is absent from the official
Scheme players. The native state likewise has eight commitments and eight domain
focus links. Original `auction_capabilities.asl` explicitly permits random failed
bids and dropped obligation intentions. Group membership therefore does not prove
all five agents have focused/committed in both auctions.

The test now captures the real Bridge resync, resolves each source endpoint through
the immutable native cut's exact identity aliases, and compares the complete join,
focus, contextual-role and instance-qualified commitment edge sets. Missing or
invented endpoints still fail. It does not synthesize links or relax production
identity rules. `HelloWorldSemanticInventoryIT.RecordingTransportFactory` is reused
as a public **test-only** recorder rather than adding another production pipeline.

`target/workbench-acceptance/auction-1791200925048/authoritative-relations.json`
already proves exact parity for all ten joins, ten focuses, five role links and ten
commitments in the next run. That focused gate then exposed a second obsolete
test-helper restriction: it searched only EVENT records for `running=true`, while
the accepted typed value was captured at a SNAPSHOT checkpoint. The helper now
accepts actual SNAPSHOT/EVENT/native PRE/POST evidence and records its origin;
live verification and retrospective reanalysis remain explicitly separate.
Final passing results must be supplied by the subsequent gates, not these failures.

## Goal View / Workbench refinement — 16:47 +07

`target/runtime-goal-ui-refinement-focused.log`: **46 PASS** at 16:47:19 +07.
The Goal View reads the selected active replay coordinator, with no live control
or original-workspace violation data leaking into replay. Current Goal-state
availability is checked against the exact SchemeBoard incarnation; stale copied
values are explicitly last-observed/unavailable. UI selects the exact failing Goal,
handles bounded-history replacement with unchanged row count, renders retained
previous/last-passing/failure/confirmation values, and navigates source with line
unavailable. Four control states and stale/disconnect/missing-ACK/error Resume
gating pass. No EDT read waits while holding the control diagnostic monitor.
Unchanged Goal DTOs preserve tree model/selection. Full headful/case acceptance
and final packaging remain required.

## Replay / retention / source evidence — 16:34 +07

`target/runtime-replay-control-focused.log`: **26 PASS** at 16:27:13 +07.
Recorded replay now includes capability/version changes, approval policies bound
to the compiled-condition fingerprint, exact STREAM_BOUNDARY and native PRE/POST
with preserved `@pre`. An unrecorded contract change is rejected, not normalized
into a different recorded baseline. Replay manifest 1.1.0 retains 1.0.0 reading.

`target/runtime-retention-trace-control-focused.log`: **41 PASS** (8 adapter,
33 plugin), six-module focused reactor PASS at 16:34:53 +07. Snapshot byte budgets
now include the full metadata/result/profile/image serialization; earlier 88,425
and 33,478 byte measurements counted the state image only. Oversized snapshot
capture creates an explicit persistence GAP and STALE/SKIPPED result, never a
recorded passing cut. REPORT_ONLY failures are inspectable across tail eviction.
Distinct simultaneous HARD violations use one control request and configurable
`COALESCE_CONFIRM` (default) or `REPORT_DISTINCT`, with separate diagnostic records.
Source trace reports mapping rule ids, and Moise public object graphs explicitly
report XML line unavailable (0) instead of inventing line 1. Native/OCL descriptors
without extractor positions likewise carry no fabricated SourceSpan.

## Current acceptance update — 16:10 +07

`target/runtime-auction-pause-focused.log`: **15 tests PASS**, zero failures,
errors or skips, completed 16:10:44 +07. Command after current reactor artifacts
were installed locally:

```powershell
mvn -B -pl use-plugin test `
  '-Dtest=LocalTcpBridgeTransportTest,BridgeMirrorStateMachineTest,AuctionPauseResumeIT'
```

Real separate-JVM Auction evidence:
`use-plugin/target/runtime-control-acceptance/auction-1791191403776`.
The explicit CASE false condition changes only the verification specification.
All **5 Jason agents ACK**, original cut **142** is frozen before the request,
authoritative confirmation cut **143** still fails (**CONFIRMED**), and explicit
Resume ACK/resync returns LIVE on the **same active MSystem/MSystemState**. The
original cut remains immutable after resume. Measured **38 objects / 92 links**,
failing snapshot **88,425 serialized bytes**. Original JaCaMo sources are unchanged.
This proves the real control path; final UI/three-case/package gates remain open.

This acceptance found and fixed three production integration defects:

- STREAM_BOUNDARY was admitted as a domain mutation. It now advances only its
  watermarked checkpoint source, with shared strict owner/correlation/observed
  causation validation. No invented boundary MObject or relaxed entity lookup.
- Pinned Moise 1.1 publishes `goalState` using JasonTermWrapper and `goalArgument`
  using direct Jason Term values. The adapter supports both official typed forms,
  rejects untyped string guesses and keeps impossible explicitly unavailable.
- Producer publication can deliver callbacks covered by a snapshot after client
  bootstrap ends. Exact snapshot watermarks now prove coverage for those delayed
  callbacks, with explicit ACK/counter; post-cut rewind/gap guards remain strict.
  Startup ACK now also requires authoritative resync/control-readiness refresh.

`target/runtime-boundary-admission-focused.log`: **24 tests PASS** (5 adapter,
19 plugin), completed 15:47:44 +07. Includes immutable domain fingerprint across
boundaries, duplicate/gap/stale/unknown-cause rejection, checkpoint routing and
native coordinator regression. `target/runtime-single-pipeline-focused.log`:
**56 PASS**, completed 15:31:03 +07; one facade pipeline, session ownership,
reconnect, UI and legacy isolation. `target/runtime-exact-violation-focused.log`:
**27 PASS**, completed 14:40:23 +07; exact operation context and stale approval
revocation included. Build preparation `target/runtime-working-artifacts-package.log`
is a six-module **package/install PASS with tests skipped**, not a final test gate.

The following audit/baseline subsections describe earlier checkpoints. Statements
that migration had not yet started or that the old production branch still existed
are historical observations, not current support claims.

## Preflight và phần được giữ lại

Đã đọc agent.md rồi toàn bộ revision task này trước khi sửa. Branch `main`, HEAD
`55f38081`; preflight gồm status, diff, staged diff, branch và log 10 commits.
Ban đầu có 94 tracked modified / 18 untracked, staged diff rỗng. Diff hiện có được
lưu tại `target/runtime-snapshot-preflight.diff`; không reset/discard hoặc tạo
branch. Giữ native projection policy 2.1.0, selective belief và exact runtime
relations đã có. Chưa thay production runtime/UI path khi chưa có replacement.

Phase A inventories:

- [Runtime paths, hooks, queues, mutation/OCL triggers, legacy consumers and baseline](runtime-current-inventory.md).
- [UI tabs, action consumers, facade-owned data and proposed dispositions](ui-current-inventory.md).
- [Pinned control API/source audit and executable counterexample](jacamo-pause-resume-audit.md).
- [Proposed architecture and versioned-control impact](../project/ADR-runtime-verification-pause-resume.md).

I1 được audit sớm vì semantics của API là điều kiện để chốt Phase B. Chỉ đánh dấu
các item đã có source/test evidence; chưa chứng minh pause toàn bộ environment,
operation CArtAgO đang chạy hoặc Moise/OrgBoard.

## Executable baseline và regression

Chạy tại repository `use`, PowerShell. Các gate dưới đây đều BUILD SUCCESS,
zero failures/errors/skips. Số test của các gate có thể trùng nhau; không cộng
chúng thành số unique tests.

```powershell
mvn -B -pl use-plugin -am verify `
  '-Dtest=SnapshotCoordinatorTest,OfficialCartagoAdapterTest,RuntimeVerificationCoordinatorTest,NativeRuntimeReplayTest,NativeRuntimeFacadeIntegrationTest,NativeRuntimeBridgeCoverageTest,RuntimeHistoryTest,ManagedRuntimeWorkflowTest,NativeEnvironmentRelationsTest,GenericFunctionalRuntimeProjectionTest,SelectiveVerificationProjectionTest,BridgeMirrorStateMachineTest,JaCaMoWorkbenchPanelTest' `
  '-Dsurefire.failIfNoSpecifiedTests=false' `
  '-Dit.test=OCLExpressionIT,ShellIT,ManagedAuctionWorkbenchIT' `
  '-Dfailsafe.failIfNoSpecifiedTests=false'
```

`target/runtime-snapshot-baseline.log`: **205 tests / 16 suites PASS**, relevant
six-module package/verify PASS; completed 2026-10-05 10:47:55 +07. Includes real
Auction managed startup, live events, same active MSystem, native OCL transitions,
authoritative resync parity, replay/reanalysis and GUI screenshots.

```powershell
mvn -B -pl jacamo-bridge-jacamo -am test `
  '-Dtest=JasonConsolePauseSemanticsTest,SnapshotCoordinatorTest,LiveJaCaMoLauncherLifetimeTest' `
  '-Dsurefire.failIfNoSpecifiedTests=false'

mvn -B -pl jacamo-bridge-jacamo -am test

mvn -B -pl use-plugin -am test `
  '-Dtest=RuntimeTraceTest,CodeGroundedPhase6Test,NativeRuntimeBridgeCoverageTest' `
  '-Dsurefire.failIfNoSpecifiedTests=false'
```

## Executable snapshot/control evidence — 2026-10-05

The current producer uses the plugin-owned `BridgeLocalExecutionControl` and
`BridgeControlledLauncher` extension of official Jason/JaCaMo infrastructure.
`MASConsoleGUI.setPause()` is never the execution-control path. The control ledger
is keyed by exact agent incarnation and grants real `LocalAgArch.receiveSyncSignal`
cycles. Producer capability depends on controller ownership and supported scheduler.

- `target/runtime-execution-control-real-agents.log`: **9 tests PASS** at 13:25:01
  +07, including three child-JVM scenarios with actual Jason agents. All-agent
  ACK freezes reasoning, resume re-enables it, partial timeout/arrival/departure
  remain fail-closed, and an actual in-flight CArtAgO operation completes after
  agents pause. Producer JSON evidence is under
  `jacamo-bridge-jacamo/target/runtime-control-api/execution-{all,membership,inflight}.json`.
- `target/runtime-control-confirmation-focused.log`: **36 tests PASS** at 13:47:47
  +07. Includes authenticated/versioned control transport (foreign session and
  authentication rejection), original failure captured before requests, all-ACK
  resync, CONFIRMED / TRANSIENT_NOT_REPRODUCED / CONFIRMATION_ERROR, timeout,
  disconnect while paused, resume resync, storm deduplication and native exact
  operation PRE/POST with preserved `@pre` on the same active system.
- `target/runtime-goal-snapshot-focused.log`: **28 tests PASS** at 13:58:26 +07
  (15 adapter + 13 plugin). Explicit callback boundaries participate in snapshot
  watermarks; timer observations never manufacture a boundary. Snapshot metadata,
  immutable native values/links, bounded retention, reconnect/pause cuts and generic
  Goal/mission/agent relations pass. The measured generic Goal snapshot is
  **33,478 serialized bytes**; final case measurements/performance gates are pending.
- `target/runtime-goal-workbench-focused.log`: **53 tests PASS** at 14:23:35 +07.
  Includes six control-service tests covering unavailable/throwing APIs,
  disconnect/replaced ownership during ACK, resume error without overwriting the
  original confirmation, exact native operation checks, generic functional model,
  Goal View copies from the active state, native facade and 22 Workbench regressions.

The minimum organization snapshot now includes Scheme specification/runtime
identity, arguments, exact Goal context/operator/ordinal, mission commitment,
and committed/achieved Goal agents from SchemeBoard's public observable contract.
Pinned Moise 1.1 `goalState(Scheme,Goal,Committed,Achieved,State)` exposes only
waiting/enabled/satisfied. **Impossible is unavailable through this API**, not
inferred from unsatisfied or failed operations. Agent identities are resolved from
the current Bridge registry; unknown agent/context/type rejects the projection.
Global Moise event completeness stays PARTIAL; a separate supported-domain status
and versioned Goal-state capability guard selected checks. No full platform atomic
pause guarantee is asserted.

These gates do **not** yet prove the new real JaCaMo launcher end-to-end, final
Auction/Hello World/House Building runs, complete UI migration, obsolete-path
removal, full final reactor/package gates or final performance measurements.
Unproved conditional details and those phases remain unchecked.

- `target/runtime-pause-api-focused.log`: **13 PASS**, completed 10:50:06 +07.
- `target/runtime-pause-api-adapter-regression.log`: **59 PASS**, completed 10:52:14 +07; full adapter + neutral contract test gate.
- `target/runtime-snapshot-trace-baseline.log`: **7 PASS**, completed 10:58:16 +07.

Fresh Auction run directory:
`use-plugin/target/workbench-acceptance/auction-1791171951433`. Files include
selective-projection.json, gui-acceptance.json, relations-before/after-resync.json,
object-diagram-before/after.png, workbench.png and recorded-replay/runtime.jsonl.

Measured baseline: **17 classifiers / 36 objects / 72 links**, including 5 agents,
2 workspaces, 2 domain artifacts, 5 contextual role link objects, 10 focus links,
10 joins and 10 mission commitments. Exposed Belief objects: **0**; Jason current
raw beliefs: **305**, 61 per agent. Exact classifier list and object breakdown are
in runtime-current-inventory.md and selective-projection.json. Runtime checkpoints
are still snapshot plus full invariant checks after materialized events. There
is no new retained failing-snapshot workflow, Goal View or automatic pause yet.

## Resolved semantic blocker — audit history and approved contract

K6 requires **“Use the same runtime API behind Pause/Resume.”** Target architecture
and I3 require safe runtime PAUSED confirmation, authoritative resync and violation
confirmation. Actual JaCaMo 1.3.1 inherits Jason 3.3.2's MAS Console button path:
`RunLocalMAS.createPauseButton → MASConsoleGUI.setPause`. This API pauses console
output only. The real two-agent test shows completed cycles **[16,16] → [28,28]**
while its pause flag is true; a console append thread blocks and is released by
Continue. Therefore this API cannot satisfy the requested runtime pause guarantee.

A different official LocalExecutionControl/ExecutionControl path gates synchronous
reasoning cycles. The second test proves two sync agents remain at [0,0], advance
to [1,1] and [2,2] only after official signals. It does not prove global suspension
of CArtAgO/Moise, equivalent asynchronous scheduling or a full JaCaMo safe barrier.
Audit JSON files: `jacamo-bridge-jacamo/target/runtime-control-api/{console,sync}.json`.

The user approved capability-gated Jason synchronous reasoning-cycle pause on
2026-10-05, replacing K6's same-console-API requirement. Document in-flight
environment/organisation work, require exact admitted-agent ACKs and fail closed
for unsupported schedulers. A versioned,
authenticated Bridge control contract is necessary because current protocol is
readOnly and has no pause/resume command. The accepted ADR records this narrow
execution-control exception and mandatory post-pause/resume authoritative resync.

New changes for this revision are the executable API audit test, these inventories,
audit document, proposed ADR and checklist evidence. No upstream core was edited.
Phase B–R migration, full final regression, new violation→pause→resume acceptance,
Hello World and House Building regressions remain open. Existing legacy runtime/UI
consumers remain until the replacement passes, as Phase L requires.

Final audit git check: still on `main`; 94 tracked modified / 23 untracked,
staged diff empty. This revision adds one test and four documents and updates
task.md; the pre-existing working changes are retained. `git diff --check` PASS
after removing the task document's two trailing-space hard breaks. No commit.

## Approved-scope implementation — Phase B/C

2026-10-05: accepted ADR and agent.md now define the narrow ExecutionControl
exception, per-agent boundary ACK, separate sync/control states, retained original
failure and mandatory pause/resume resync. K6 no longer requires the console API.
The versioned RuntimeControlContract rejects malformed/foreign versions and false
PAUSED statuses. ReasoningCycleGate defines idempotency, incarnation admission,
departure-before-ACK, partial ACK timeout and resume re-enable ACK. These ledger
tests prove the lifecycle contract; official controller integration remains Phase I.

`target/runtime-control-contract-regression.log`: **65 tests PASS**, zero
failures/errors/skips, full adapter/neutral contract regression; completed 11:43:20
+07. Command: `mvn -B -pl jacamo-bridge-jacamo -am test`.

Phase C reuses the tested production selection/lifecycle policy rather than adding
another projection. Auction baseline proves true high *current* raw belief count,
not stale exposed accumulation. The stale/both classification possibilities were
evaluated and rejected; the conditional lifecycle-fix item is satisfied by existing
add/remove/profile-reload/incarnation regression. Expose requires authored AST
evidence, compiler-derived OCL demand and no authoritative duplicate. Provider code
source identifies framework artifacts; application classes with the same simple
names remain eligible. Console/TupleSpace audit found framework ownership and no
domain verification demand in the measured Auction; retained trace is internal.

`target/runtime-selective-projection-regression.log`: **35 tests PASS** (2 adapter,
33 plugin), zero failures/errors/skips; completed 11:47:23 +07. Includes authored
`focused(...)` negative control, removal/profile reload, exact generic functional
state and environment relation/coordinator regressions. Command:

```powershell
mvn -B -pl use-plugin -am test `
  '-Dtest=JasonLiteralObservationTest,SelectiveVerificationProjectionTest,GenericAgentProjectionTest,GenericFunctionalRuntimeProjectionTest,NativeEnvironmentRelationsTest,DomainRuntimeProjectionTest,RuntimeVerificationCoordinatorTest' `
  '-Dsurefire.failIfNoSpecifiedTests=false'
```
