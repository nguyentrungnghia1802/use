# Agent Working Rules — USE JaCaMo Runtime Verification

> Goal: implement `task_runtime_verification.md` so JaCaMo runtime states/checkpoints can be verified by external OCL on the same native USE system, with attributable results, coverage status, history, and replay.

# 1. Role

You are the implementation agent for the USE JaCaMo Plugin runtime-verification phase.

Keep this chain consistent:

```text
Official JaCaMo/Jason/CArtAgO/Moise runtime semantics
→ typed runtime snapshot/event contract
→ exact J/A/C/M/X mapping + verification projection
→ one native USE MModel / MSystemState
→ external OCL constraints
→ runtime mutation/checkpoint
→ OCL verification
→ PASS / FAIL / ERROR / SKIPPED result
→ journal/checkpoint/history
→ USE Session / Workbench / replay
```

Do not optimize for “events arrive” or “tests compile”.

Optimize for:
- exact runtime authority;
- one active `MSystem`;
- deterministic state mutation;
- attributable verification results;
- explicit coverage/gaps;
- replayability;
- no semantic guessing.

---

# 2. Read these files first

Read in this order:

1. `task_runtime_verification.md`
2. `JACAMO-USE-CONCEPT-MAPPING-RULES.md`
3. `CODE-GROUNDED-NATIVE-README.md`
4. current runtime/constraint/export source and tests

Use them as:

```text
task_runtime_verification.md = active implementation gates
mapping rules                = semantic mapping contract
native README                = current operational behavior/boundaries
source + tests               = implementation authority
```

If source and docs disagree:
- do not guess;
- record the discrepancy;
- resolve from current code/API/runtime evidence;
- update docs only after behavior is proven.

Historical Ecore / Mapping V2 material is not semantic authority for this task.

---

# 3. Source-of-truth order

Use this priority:

1. current production source code;
2. exact framework APIs/bytecode;
3. executable tests and live runtime evidence;
4. current Bridge protocol and semantic/runtime DTOs;
5. `task_runtime_verification.md` and maintained docs;
6. historical V2/Ecore/mapping material only as regression evidence.

Do not silently edit frozen V2/Ecore/mapping/golden artifacts.

---

# 4. Active architecture

Static/bootstrap path:

```text
project.jcm
→ official JaCaMo APIs
→ typed semantic contract
→ JacamoSpecificationModel
→ J/A/C/M/X mapping
→ verification projection
→ native MModel
→ native MSystemState
→ Session.setSystem(...)
```

Runtime verification path:

```text
official runtime listener/snapshot
→ typed RuntimeEvent / RuntimeSnapshot
→ Bridge ordering/identity/coverage validation
→ RuntimeVerificationCoordinator
→ atomic mutation/checkpoint on current MSystemState
→ stateVersion++
→ evaluate registered OCL invariants
→ immutable verification result
→ journal/checkpoint store
→ Workbench/report/event bus
```

Offline replay path:

```text
.use
+ baseline .cmd
+ constraints.ocl
+ runtime journal/checkpoints
→ same native mutation/verification semantics
→ state/result hash comparison
```

`.use` and `.cmd` are export/replay artifacts, not semantic authorities.

---

# 5. Hard invariants

## INV-001 — One active USE system

Facade, Session, runtime mutation, OCL verification, UI inspection, export, and replay of the live workspace must refer to the same active `MSystem`.

Do not create a second hidden runtime model/state.

## INV-002 — Official runtime evidence only

Only runtime facts supported by official JaCaMo/Jason/CArtAgO/Moise APIs or exact Bridge evidence may mutate native USE state.

Unknown/unsupported data remains explicit.

## INV-003 — No fuzzy runtime identity

Never bind runtime elements by similar/equal names alone.

Forbidden examples:

```text
action name == operation name
belief literal == property name
agent name == CArtAgO AgentId
artifact declaration name == runtime ArtifactId
goal string == organizational goal id
```

Require exact semantic ID, trace, incarnation, correlation, or other documented evidence.

## INV-004 — Runtime state and evidence are distinct

Use explicit statuses such as:

```text
MATERIALIZED_FAITHFULLY
EVIDENCE_ONLY
UNAVAILABLE
UNKNOWN
SKIPPED
STALE
INCOMPLETE
```

Evidence-only events may be journaled but must not be presented as formal state mutation.

## INV-005 — External OCL uses USE itself

Use the existing USE compiler/model APIs.

Do not implement a second OCL parser, evaluator, or constraint language.

## INV-006 — OCL profile installation is atomic

An external `.ocl` profile is either fully accepted for the current model revision or not installed.

No partially installed profile after syntax/type/context/name-conflict failure.

## INV-007 — No vacuous PASS from missing projection

If a constraint requires a class/attribute/association that is absent from AUTO projection:

- expand projection only when the semantic source truly provides the concept/data; or
- report incompatibility / `SKIPPED` / `UNAVAILABLE`.

Never create empty/stub classes merely to make OCL compile or make `allInstances()` pass.

## INV-008 — User invariant FAIL is observational

For a faithful runtime state:

```text
apply state
→ evaluate OCL
→ record FAIL if violated
→ keep the faithful state
→ continue observation
```

Do not rollback a faithful runtime state merely because a user invariant is false.

Rollback/quarantine is only for:
- malformed payload;
- invalid identity/protocol;
- structurally invalid mutation;
- incomplete/unsafe atomic transaction.

Enforcement is a separate feature and is outside this task unless explicitly added later.

## INV-009 — Atomic runtime boundary

Do not evaluate constraints on half-applied object/link/property updates.

Verification runs after the complete accepted mutation/checkpoint transaction.

## INV-010 — Monotonic state version

Every committed formal runtime mutation/checkpoint gets a monotonic local `stateVersion`.

`generation`, timestamp, source sequence, and model revision do not replace `stateVersion`.

## INV-011 — Coverage must be explicit

Queue overflow, GAP, stale snapshot, missing payload, source discontinuity, unsupported capability, or failed resync must never be reported as continuous PASS.

Use `STALE`, `INCOMPLETE`, `SKIPPED`, or equivalent explicit status.

## INV-012 — Runtime history is immutable evidence

Do not store mutable `MSystemState` references as historical records.

Journal/checkpoint/result records must be immutable or persisted in a form suitable for deterministic replay.

## INV-013 — Runtime ordering is not inferred from wall-clock time

Use source sequence/incarnation/Bridge ordering semantics.

Timestamps are metadata, not proof of causal order.

## INV-014 — No hidden V2 dependency

`CODE_GROUNDED_NATIVE` runtime verification must not silently load:
- Mapping V2;
- Runtime Mapping V2;
- V2 OCL profiles;
- legacy verification engines as semantic authority.

## INV-015 — Generic implementation

No Hello/Auction/House-specific production branch, identity, constraint, or runtime binding.

Case studies are tests only.

---

# 6. External OCL contract

Supported target format is named invariant declarations, e.g.:

```ocl
context Workspace
inv WorkspaceUuidDefined:
    not self.uuid.oclIsUndefined()
```

Rules:

- compile/type-check against the current native `MModel`;
- exact class/attribute/association names;
- no fuzzy aliases;
- register exact `Class::Invariant` identity;
- retain source path/hash and model revision;
- baseline verify immediately after successful install;
- recompile/rebind after compatible model replacement;
- reject or mark incompatible if required structure no longer exists.

Keep at least:

```text
constraintId
contextClass
sourceFile
sourceHash
modelRevision
enabled
requiredCapabilities/rules
```

Do not silently drop external constraints during resync.

---

# 7. Runtime coordinator contract

There must be one serialization point for runtime apply/check/report.

Preferred flow:

```text
accept event/checkpoint
→ validate session/generation/modelRevision/incarnation/order
→ verify coverage
→ begin atomic mutation
→ apply native changes
→ structural validation
→ commit
→ stateVersion++
→ evaluate constraints
→ store result
→ publish native/UI update
```

Requirements:

- no duplicate full-check for the same committed event;
- baseline/resync/profile change => full check;
- faithful mutation => post-commit invariant verification;
- evidence-only event => journal/status only, no fake state version unless the chosen versioning contract explicitly includes observation-only records;
- manual OCL check/export must see a consistent committed state;
- avoid concurrent mutation/read races between Bridge thread, Swing/UI, exporters, and shell actions.

Prefer a single-writer coordinator plus safe read snapshots/locking over scattered locks.

---

# 8. Verification result semantics

Per-constraint outcome must distinguish:

```text
PASS
FAIL
ERROR
SKIPPED
```

Meaning:

- `PASS`: expression evaluated to true on an adequately covered state;
- `FAIL`: expression evaluated to false;
- `ERROR`: undefined/invalid/evaluation/compiler/runtime error as defined by USE semantics;
- `SKIPPED`: required state/capability/coverage is unavailable or unsafe to claim.

Do not convert all constraints to FAIL from one aggregate boolean.

A result/checkpoint should retain at least:

```text
sessionId
generation
modelRevision
stateVersion
checkpointId
eventId
sourceId
sourceSequence
observedAt
appliedAt
verifiedAt
constraintSetHash
stateHash
coverageStatus
perConstraintOutcome
diagnostic
duration
```

---

# 9. Runtime source priorities

Implement faithful runtime materialization incrementally.

Priority:

1. CArtAgO C09 observable-property snapshot/delta and exact artifact lifecycle;
2. other runtime facts only when official typed authority exists;
3. Jason/Moise/NPL facts remain `EVIDENCE_ONLY/SKIPPED` until their materialization semantics are proven.

Important:

- C09 is a property snapshot, not C08 live `ObsProperty`;
- do not invent C08 support;
- do not parse arbitrary strings into typed semantics;
- preserve artifact/workspace/property identity and incarnation;
- create/dispose/recreate must not accidentally reuse stale target identity.

---

# 10. Journal and checkpoints

Runtime journal/checkpoint storage must support:

```text
baseline
→ accepted runtime transaction/event
→ verification result
→ later replay
```

Required behavior:

- bounded resource usage;
- explicit retention policy;
- explicit GAP/overflow marker;
- no silent loss;
- no claim of complete verification after coverage loss;
- checkpoint/state hashes sufficient to detect replay divergence.

If full lossless history cannot be guaranteed, report exactly what interval/checkpoints are verified.

The thesis claim is:

> verified observed runtime states/checkpoints of the supported projection

not:

> verified every internal JaCaMo state.

---

# 11. Workbench/UI boundary

Workbench is an observer/controller over the existing USE system.

Allowed:
- load/replace external OCL profile;
- show current runtime stateVersion/checkpoint;
- show PASS/FAIL/ERROR/SKIPPED;
- show coverage/GAP/backlog/drop diagnostics;
- show last failing constraints;
- navigate to normal USE model/object/invariant views;
- request resync/manual verify/export.

Forbidden:
- independent runtime state;
- independent OCL evaluator;
- separate model ownership;
- case-study-specific runtime logic.

Publish UI updates after atomic commit/checkpoint, not after every low-level field write.

---

# 12. Offline replay contract

Replay must reuse production-native semantics, not implement a second interpretation.

Input:

```text
.use
baseline .cmd
constraints.ocl
runtime journal/checkpoints
```

Flow:

```text
load .use
→ replay baseline .cmd
→ install .ocl
→ baseline verify
→ replay each supported transaction
→ evaluate at the same boundaries
→ compare stateHash/resultHash/outcomes
```

Rules:

- same identity/mutation rules as live path;
- no fuzzy repair during replay;
- GAP/corrupt/missing payload => fail closed or explicit partial replay;
- do not evaluate incomplete `.cmd` construction steps as JaCaMo runtime checkpoints;
- replay must not become a competing live semantic authority.

---

# 13. Development protocol

Before editing:

1. read `task_runtime_verification.md`;
2. inspect current callers/tests for runtime, constraints, Session, export, UI;
3. inspect exact USE APIs before creating new abstractions;
4. run `git status`, branch, recent log;
5. preserve existing user work;
6. identify the smallest acceptance gate for the current task section.

During implementation:

```text
current behavior/evidence
→ failing focused test where practical
→ smallest semantic change
→ focused tests
→ runtime negative controls
→ nearby regressions
→ diff review
→ task evidence update
```

Do not combine unrelated cleanup/refactoring with this runtime phase.

Do not proceed past a mandatory gate that fails.

---

# 14. Testing requirements

At minimum prove:

## External OCL
- valid multi-context profile;
- syntax error;
- missing context;
- missing attribute/association;
- type error/non-Boolean body;
- duplicate/conflicting invariant;
- atomic rejection;
- resync/rebind behavior.

## Runtime mutation
- `PASS → FAIL → PASS`;
- transient FAIL retained in history;
- C09 value `A → B → A`;
- artifact create/dispose/recreate;
- exact identity/incarnation;
- malformed payload rollback without corrupting state.

## Ordering/coverage
- duplicate;
- conflicting duplicate;
- rewind;
- sequence GAP;
- stale generation/session/model revision;
- queue overflow;
- incomplete snapshot/coverage;
- reconnect/resync.

## Concurrency/UI
- manual verify sees committed state;
- export sees committed state;
- Workbench report changes after native runtime mutation;
- object/invariant views refresh from the same `MSystem`;
- no stale result overwrites newer stateVersion.

## Replay
- same state hashes;
- same per-constraint outcomes;
- corrupted journal detected;
- GAP produces explicit partial/incomplete result.

Always run:
- focused tests;
- nearby regression tests;
- full reactor `verify`;
- package/release tests before marking DONE.

---

# 15. Git and scope

- Do not destructive-reset/clean unknown work.
- Do not force-push primary branch.
- Keep commits coherent and tested.
- Prefer Conventional Commits.
- Do not modify frozen V2/Ecore/golden artifacts.
- Do not reintroduce deleted legacy runtime paths.
- Do not add case-study hard coding.
- Do not change unrelated mapping semantics to make runtime tests pass.

---

# 16. Documentation/evidence

Update `task_runtime_verification.md` only when the corresponding acceptance gate actually passes.

Update maintained runtime docs only after implementation is proven.

Final evidence must include:

1. current production runtime call graph;
2. external `.ocl` installation path;
3. exact runtime concepts materialized;
4. verification-result schema;
5. journal/checkpoint strategy;
6. violation policy;
7. Workbench behavior;
8. replay behavior;
9. exact test commands/results;
10. remaining `EVIDENCE_ONLY`, `UNAVAILABLE`, `SKIPPED`;
11. coverage limitations;
12. final git diff/stat.

Do not present fixture/unit coverage as proof of a live producer path unless an end-to-end test actually proves it.

---

# 17. Definition of done

`task_runtime_verification.md` is DONE only when all applicable items are true:

- [ ] external `.ocl` compiles/installs atomically on current native model;
- [ ] baseline verification is attributable per constraint;
- [ ] runtime mutation and OCL verification use the same active `MSystem`;
- [ ] faithful runtime mutation creates a new stateVersion;
- [ ] `PASS / FAIL / ERROR / SKIPPED` are distinguished correctly;
- [ ] invariant FAIL does not erase the faithful violating state;
- [ ] transient violations remain visible in history;
- [ ] GAP/overflow/unsupported capability cannot become false PASS;
- [ ] runtime results are journaled/checkpointed;
- [ ] Workbench shows current runtime verification status;
- [ ] offline replay reproduces supported checkpoints/results;
- [ ] no hidden V2/fuzzy/case-specific path exists;
- [ ] focused/full/package tests pass;
- [ ] documentation matches actual behavior.

---

# 18. Final rule

The runtime-verification phase is not complete merely because JaCaMo emitted events or USE called `check()`.

It is complete only when:

```text
observed JaCaMo runtime fact
→ exact accepted mutation/checkpoint
→ same native USE state
→ registered external OCL
→ attributable verification result
→ explicit coverage status
→ durable evidence/history
→ visible/replayable result
```

If any link is missing, do not claim runtime verification is complete.
