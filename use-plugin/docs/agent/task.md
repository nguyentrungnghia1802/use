# USE–JaCaMo — Full Implementation Task Plan (Phase 16 → Project Closure)

> **Document type:** Active implementation checklist after the current v1.0.1 baseline  
> **Roadmap source:** `docs/project/19-roadmap.md`
> **Historical task source:** `docs/agent/tasks/task-01.md` (Phases 0–15; do not reopen completed work unless regression evidence proves it is necessary)  
> **Goal:** let the Agent complete the remaining engineering A–Z with minimal user interruption.  
> **Human-dependent work:** collect and defer to Phase 25 whenever it does not block independent engineering work.

---

# 0. Global Execution Contract

## 0.1 Baseline assumptions

Phases 0–15 are considered implemented/historical baseline unless current executable evidence proves otherwise.

The Agent must **not** restart completed subsystems from zero merely because a later phase uses different terminology.

Existing baseline includes at least:

- structural Ecore/Mapping V1;
- semantic extraction;
- `.use` generation / direct `MModel`;
- `.cmd` generation / direct `MSystemState`;
- TraceIndex / binding;
- Jason/CArtAgO/Moise runtime connectors;
- RuntimeEvent foundation;
- RuntimeMutationEngine;
- RuntimeMirrorService;
- RuntimeVerificationEngine;
- Auction offline/live evidence;
- OCL infrastructure;
- packaging/tests/docs baseline.

## 0.2 Selective-reading rule — mandatory

The Agent must **not** read the whole repository documentation before every task.

For each task:

1. read only the files listed under **Read first**;
2. inspect only the production/test code named by the task or discovered as its direct dependency;
3. open files under **Read only if needed** only when a concrete question remains unresolved;
4. do not reread large research/doc trees that were already summarized into a task-local audit artifact;
5. when a task depends on a prior phase output, prefer that output over rereading all original evidence;
6. perform a targeted stale-statement search after implementation rather than rereading every document;
7. update only affected docs plus task status.

### Special rule for runtime mapping tasks

Any task that designs, validates, implements, or freezes JaCaMo runtime mapping **must read the relevant material under**:

`use-plugin/docs/research/jacamo_runtime_research/`

Minimum research files for runtime mapping:

- `00_RESEARCH_BASELINE.md`
- `01_JACAMO_RUNTIME_ARCHITECTURE.md`
- `02_RUNTIME_CAPABILITY_MATRIX.md`
- `03_RUNTIME_IDENTITY_MODEL.md`
- `04_USE_MAPPING_CANDIDATES.md`
- `runtime-capabilities-v1.json`
- `SOURCE_MANIFEST.md`

Read `05_AUCTION_RUNTIME_WALKTHROUGH.md` only for Auction integration/evidence tasks.

Research documents are **evidence/input**, not automatically canonical implementation truth. Current code, pinned dependency APIs, tests, and canonical project contracts must be reconciled with them.

## 0.3 Autonomous execution rule

The Agent should continue automatically through Phases 16–24.

Do **not** ask the user for routine engineering choices such as:

- helper class names;
- private API layout;
- JSON serialization library usage already established by the repository;
- test organization;
- refactor strategy that preserves semantics;
- conservative unsupported behavior;
- diagnostic code naming consistent with current conventions;
- exact collection implementation;
- file placement that follows current repository conventions.

When evidence is insufficient for a semantic feature:

- classify it `PARTIAL`, `UNSUPPORTED`, or `DEFERRED`;
- implement safe diagnostics/boundary behavior;
- continue independent work;
- record any genuinely human/researcher-specific decision for Phase 25.

## 0.4 Non-negotiable semantic rules

- [ ] Never hard-code Auction behavior into generic core logic.
- [ ] Never use fuzzy/name-similarity as formal runtime identity resolution.
- [ ] Never auto-materialize an unknown runtime entity without exact semantic policy/trace evidence.
- [ ] Never mutate frozen Structural Mapping V1 merely to carry runtime semantics.
- [ ] Keep structural mapping and runtime mapping separate.
- [ ] Keep JaCaMo as the execution engine; USE is the verification mirror.
- [ ] Runtime connectors observe/acquire; they do not implement OCL business rules.
- [ ] OCL is evaluated only over a state/operation/trace representation whose synchronization semantics are defined.
- [ ] No silent runtime event drop in correctness mode.
- [ ] No silent fallback on invalid mapping/identity/payload.
- [ ] Unsupported semantics remain explicit and traceable.

## 0.5 Task execution loop

For every coding task:

- [ ] Inspect current production implementation.
- [ ] Inspect current focused tests.
- [ ] Determine whether work is new implementation, formalization, correction, or refactor.
- [ ] Write/update a RED regression test when the behavior is correctness-sensitive and reproducible.
- [ ] Implement the smallest correct change.
- [ ] Run focused GREEN tests.
- [ ] Run affected subsystem regression.
- [ ] Inspect `git diff` / changed files.
- [ ] Update affected documentation.
- [ ] Update this checklist with evidence when working inside the repository.
- [ ] Do not claim complete without executable evidence.

## 0.6 Phase completion evidence

Every phase must leave:

- [ ] implementation or explicit unsupported boundary;
- [ ] focused tests;
- [ ] regression result;
- [ ] generated/audit artifact when required;
- [ ] documentation synchronization;
- [ ] explicit remaining limitations;
- [ ] clean classification of any deferred human input.

---

# Phase 16 — JaCaMo Runtime Research Integration

**Objective:** turn the external JaCaMo runtime research into repository-local, implementation-reconciled evidence without changing Ecore or extending OCL.

---

## P16.1 — Install and register runtime research pack

### Read first

- `docs/project/19-roadmap.md` — Phase 16 only.
- `docs/project/16-research-evidence-boundaries.md`.
- `use-plugin/docs/research/jacamo_runtime_research/README.md`.
- `use-plugin/docs/research/jacamo_runtime_research/00_RESEARCH_BASELINE.md`.

### Tasks

- [x] Verify `use-plugin/docs/research/jacamo_runtime_research/` exists and contains the expected research files.
- [x] Preserve the research pack as research evidence; do not move it into canonical Mapping resources.
- [x] Add/update a short repository index entry pointing to the research folder if no index exists.
- [x] Record the JaCaMo/Jason/CArtAgO/Moise versions/commits claimed by the research pack.
- [x] Compare these versions with current `pom.xml`, `compatibility.json`, and resolved dependency tree.
- [x] Create a discrepancy list for any version mismatch (for example current plugin pin vs upstream JaCaMo main).
- [x] Do not update dependency versions in this task.
- [x] Classify each research source as upstream fact / project interpretation / proposed normalization.

### Tests / evidence

- [x] Repository docs links to the research pack resolve.
- [x] Existing build/tests remain unchanged and green.

### Acceptance

- [x] Research pack is repository-local and discoverable.
- [x] Version drift is explicit.
- [x] No canonical semantics changed.

---

## P16.2 — Audit existing runtime implementation against research

### Read first

- `docs/project/10-runtime-adapter.md`.
- Research:
  - `01_JACAMO_RUNTIME_ARCHITECTURE.md`
  - `02_RUNTIME_CAPABILITY_MATRIX.md`
- Production runtime package only.
- Existing Jason/CArtAgO/Moise runtime connector tests only.

### Tasks

- [x] Inventory current runtime connector classes and their actual source APIs.
- [x] Inventory current snapshot APIs.
- [x] Inventory current event/callback APIs.
- [x] Inventory current identity/correlation fields.
- [x] Compare each research capability with current code.
- [x] Mark each row:
  - `IMPLEMENTED_MATCH`
  - `IMPLEMENTED_DIFFERENT`
  - `RESEARCH_ONLY`
  - `CODE_ONLY`
  - `VERSION_CONFLICT`
  - `UNSUPPORTED`
- [x] Record exact class/method/test evidence for every implemented capability.
- [x] Detect any connector behavior that is more speculative than the research evidence permits.
- [x] Detect any research statement that does not apply to the currently pinned dependency version.
- [x] Do not refactor yet unless a concrete correctness bug is found.

### Output

Create/update a concise implementation reconciliation document, e.g.:

`use-plugin/docs/research/jacamo_runtime_research/IMPLEMENTATION_RECONCILIATION.md`

### Tests

- [x] Focused connector tests.
- [x] No production behavior change unless a bug fix was required.

### Acceptance

- [x] Research and implementation differences are explicit.
- [x] Every live connector capability has code/test evidence.

---

## P16.3 — Establish project runtime capability matrix

### Read first

- `02_RUNTIME_CAPABILITY_MATRIX.md`.
- P16.2 reconciliation output.
- `docs/project/10-runtime-adapter.md` only for current project terminology.

### Tasks

- [x] Convert the research matrix into a project-current matrix.
- [x] For every capability record:
  - runtime dimension;
  - upstream API/source;
  - pinned-version evidence;
  - snapshot/event/diff strategy;
  - runtime identity available;
  - payload available;
  - correlation available;
  - normalized event candidate/current event;
  - current connector support;
  - current USE mutation support;
  - status.
- [x] Use only statuses:
  - `SUPPORTED`
  - `PARTIAL`
  - `UNSUPPORTED`
  - `DEFERRED`
- [x] Separate “observable upstream” from “safe to mutate USE”.
- [x] Explicitly record Moise polling/diff boundaries.
- [x] Explicitly record message/intention/norm lifecycle boundaries.
- [x] Explicitly record CArtAgO operation/property strength.
- [x] Explicitly record Jason belief/goal/action acquisition limitations.

### Output

- [x] `runtime-capability-matrix.md` or update an existing canonical research matrix.
- [x] `runtime-capabilities-v1.json` reconciled to the plugin pin, preserving research provenance.

### Acceptance

- [x] The matrix can drive Runtime Mapping tasks without rereading upstream repositories.

---

## P16.4 — Runtime authority and duplicate-source policy

### Read first

- `01_JACAMO_RUNTIME_ARCHITECTURE.md`.
- P16.3 capability matrix.
- Current composite connector / runtime mirror code.

### Tasks

- [x] Define authoritative semantic source per dimension:
  - Jason for agent-mind facts;
  - CArtAgO for environment/artifact facts;
  - Moise OE for organisation facts;
  - or a different evidence-backed rule if current implementation proves it.
- [x] Identify duplicate observation paths, especially Moise state also visible via CArtAgO organisation-board artifacts.
- [x] Identify Jason external action vs CArtAgO operation correlation overlap.
- [x] Define “semantic authority” vs “correlation/diagnostic evidence”.
- [x] Define no-double-apply policy.
- [x] Add diagnostic behavior for conflicting authoritative observations.
- [x] Add focused tests if current code could double-apply the same semantic change.

### Acceptance

- [x] Each semantic fact family has one authority policy.
- [x] Duplicate-source events cannot produce duplicate USE mutation.

---

## P16.5 — Phase 16 regression and documentation closure

### Read first

- Only documents changed in P16.1–P16.4.
- `docs/project/13-testing-quality.md` for required commands.

### Tasks

- [x] Run focused runtime connector tests.
- [x] Run current Auction live integration test.
- [x] Run affected module regression.
- [x] Search docs for stale runtime API/version claims touched by this phase.
- [x] Update `KNOWN-LIMITATIONS.md` only if support boundaries changed.
- [x] Record Phase 16 evidence.

### Exit criteria

- [x] Runtime research integrated.
- [x] Current supported runtime surface is evidence-backed.
- [x] Runtime authority policy explicit.
- [x] No Ecore/Structural Mapping/OCL semantics changed.

---

Execution evidence (2026-09-19): implementation commit 898191a5; focused connector/Auction 5/5; authority negative control RED then GREEN; module 126/126; full reactor verify 272/272 (13 core, 130 GUI, 129 plugin including release IT). See [reconciliation](../research/jacamo_runtime_research/IMPLEMENTATION_RECONCILIATION.md). No frozen-contract or dependency changes. Integration/push recorded in Git and subsequent closure evidence.

# Phase 17 — Runtime Event, Trace & Identity Hardening

**Objective:** make event semantics, ordering, stream lifecycle, and runtime identity exact and independent from future Ecore V2.

---

## P17.1 — Audit RuntimeEvent V1 and schema

### Read first

- `docs/project/10-runtime-adapter.md` sections for RuntimeEvent.
- Runtime event model/schema classes.
- Runtime foundation tests.
- P16.3 capability matrix.

### Tasks

- [x] Inventory existing RuntimeEvent fields.
- [x] Inventory existing event kinds.
- [x] Inventory payload types/validation.
- [x] Inventory sequence semantics.
- [x] Inventory correlation semantics.
- [x] Inventory source/runtime/semantic identity fields.
- [x] Compare against supported capability matrix.
- [x] Do not create `RuntimeEvent2`.
- [x] Preserve existing compatible names unless a correctness issue requires migration.
- [x] Add missing metadata only when a supported runtime need proves it necessary.
- [x] Version schema only if contract compatibility actually changes.

### Tests

- [x] Serialization round-trip.
- [x] Invalid structural field rejection.
- [x] Kind-specific payload validation.
- [x] Sequence/correlation field tests.

### Acceptance

- [x] One RuntimeEvent abstraction remains canonical.

---

## P17.2 — Normalize supported event taxonomy

### Read first

- P16.3 capability matrix.
- P17.1 audit artifact.
- Existing connector event construction code.

### Tasks

For every supported event kind:

- [x] Define source runtime.
- [x] Define authoritative/non-authoritative status.
- [x] Define required runtime identity.
- [x] Define payload schema.
- [x] Define correlation policy.
- [x] Define whether it changes mirrored state.
- [x] Define whether it creates an operation checkpoint.
- [x] Define terminal event expectations.
- [x] Define unsupported conditions.

At minimum review:

- [x] observable property add/change/remove;
- [x] artifact operation requested/started/suspended/resumed/completed/failed;
- [x] artifact lifecycle;
- [x] workspace membership/focus/links where supported;
- [x] Jason belief/goal/action events;
- [x] Moise role/mission/group/scheme/goal deltas;
- [x] trace-only events.

### Acceptance

- [x] Event kinds have semantics, not just names.

---

## P17.3 — Implement/complete RuntimeTrace

### Read first

- Existing queue/event observer implementation.
- Runtime foundation tests.
- `docs/project/10-runtime-adapter.md` ordering/lifecycle sections.

### Tasks

- [x] Reuse existing history abstraction if one already exists.
- [x] Otherwise implement a generic `RuntimeTrace` domain abstraction.
- [x] Store immutable accepted event entries.
- [x] Track stream ID/generation.
- [x] Track accepted sequence ordering.
- [x] Lookup by event ID.
- [x] Lookup by correlation ID.
- [x] Support deterministic ordered iteration/range query.
- [x] Represent reconnect/resync/workspace-replacement boundaries.
- [x] Define whether rejected/quarantined events are stored and in what channel.
- [x] Bound retention if necessary without affecting correctness of current verification window.
- [x] Do not embed Auction operation names.

### Tests

- [x] in-order append;
- [x] duplicate sequence;
- [x] decreasing sequence;
- [x] correlation query;
- [x] stream generation;
- [x] reconnect boundary;
- [x] late event from retired generation;
- [x] deterministic iteration.

### Acceptance

- [x] Runtime history has one source of truth.

---

## P17.4 — Runtime identity model: Jason

### Read first

- Research `03_RUNTIME_IDENTITY_MODEL.md` — Jason section.
- `docs/project/09-traceability-binding-resolver.md`.
- Jason connector + TraceIndex code/tests.

### Tasks

- [x] Define exact Jason Agent runtime key format.
- [x] Define goal/action/message subordinate identity only where needed.
- [x] Preserve agent-name-to-semantic-ID binding semantics.
- [x] Support multiple runtime aliases for one semantic Agent where proven.
- [x] Prevent bare action/goal text from becoming a unique long-lived identity when concurrency makes it unsafe.
- [x] Add reverse lookup for diagnostics/reporting.
- [x] Add stale generation ownership.

### Tests

- [x] exact success;
- [x] missing binding;
- [x] duplicate/ambiguous key;
- [x] reconnect alias restoration;
- [x] stale alias rejection.

---

## P17.5 — Runtime identity model: CArtAgO

### Read first

- Research `03_RUNTIME_IDENTITY_MODEL.md` — CArtAgO section.
- Current CArtAgO connector tests.
- TraceIndex/runtime binding code.

### Tasks

- [x] Define workspace identity component.
- [x] Define Artifact runtime key using upstream identity, not display name alone.
- [x] Define observable-property runtime key.
- [x] Define operation invocation correlation using `OpId` fields available in pinned API.
- [x] Preserve AgentId/ArtifactId/opName/id evidence.
- [x] Ensure `OP_START/EXIT/FAIL` use the same exact operation correlation.
- [x] Ensure unknown runtime Artifact/property is discoverable but not mutating without semantic target.

### Tests

- [x] same property name on two artifacts does not collide;
- [x] same operation name on two artifacts does not collide;
- [x] concurrent operations do not collide;
- [x] late operation terminal callback rejected after stream retirement.

---

## P17.6 — Runtime identity model: Moise

### Read first

- Research `03_RUNTIME_IDENTITY_MODEL.md` — Moise section.
- Current Moise connector/poll-diff implementation.
- Organisation resolver tests.

### Tasks

- [x] Separate specification identity from runtime instance identity.
- [x] Define keys for OEAgent.
- [x] Define keys for GroupInstance.
- [x] Define role-player composite identity.
- [x] Define SchemeInstance identity.
- [x] Define mission-player identity.
- [x] Define organisational goal-instance identity.
- [x] Preserve organisation context.
- [x] Do not collapse runtime instance into static spec object unless current USE representation explicitly does so.

### Tests

- [x] multiple groups of same spec;
- [x] multiple schemes of same spec;
- [x] same agent in different group/role contexts;
- [x] deterministic snapshot-diff identity.

---

## P17.7 — Stream/generation lifecycle hardening

### Read first

- `RuntimeMirrorService`.
- `TraceIndex` runtime alias lifecycle.
- v1.0.1 lifecycle hotfix tests.

### Tasks

- [x] Ensure each connection/workspace stream has explicit generation ownership.
- [x] Retire old event correlations on replacement/resync according to existing contract.
- [x] Reject late callbacks from old consumers.
- [x] Preserve only aliases whose semantic/USE identity remains exact.
- [x] Ensure reimport of incompatible project cannot silently reuse runtime aliases.
- [x] Ensure disconnect marks state stale.
- [x] Ensure full authoritative synchronization is required before LIVE.

### Tests

- [x] rebuild while LIVE;
- [x] OCL/profile replacement while LIVE;
- [x] reimport while LIVE;
- [x] failed replacement;
- [x] reconnect;
- [x] old callback isolation.

---

## P17.8 — Phase 17 closure

### Read first

- Only Phase 17 changed docs/tests.
- `docs/project/13-testing-quality.md` runtime gates.

### Tasks

- [x] Run RuntimeEvent/schema tests.
- [x] Run RuntimeTrace tests.
- [x] Run TraceIndex/binding tests.
- [x] Run all runtime connector tests.
- [x] Run lifecycle hotfix regression.
- [x] Run Auction integration.
- [x] Update runtime/trace documentation only where behavior changed.

### Exit criteria

- [x] RuntimeEvent canonical.
- [x] RuntimeTrace deterministic.
- [x] Runtime identities exact across 3 dimensions.
- [x] Lifecycle generations safe.
- [x] No Ecore V2 dependency introduced.

---

Execution evidence (2026-09-19): 4c6b170d; two identity regressions RED then GREEN; focused trace/identity/lifecycle 26/26; final module 131/131; full reactor verify 277/277 (13 core, 130 GUI, 134 plugin including release IT). [Identity contract](../project/runtime-event-identity.md) records supported taxonomy, canonical aliases, generation and retention boundaries. Deferred upstream lifecycle and concurrent goal/message identity remain explicit; no fabricated targets. Phase 16 post-merge smoke passed 2/2.

# Phase 18 — Runtime Mapping Draft

**Objective:** formalize runtime event/state → generic semantic action → current USE mutation as a declarative draft that can later migrate to Ecore V2.

**All tasks in this phase MUST use the research folder `use-plugin/docs/research/jacamo_runtime_research/` selectively as specified below.**

---

## P18.1 — Audit existing procedural runtime mapping

### Read first

- Research:
  - `02_RUNTIME_CAPABILITY_MATRIX.md`
  - `04_USE_MAPPING_CANDIDATES.md`
  - `runtime-capabilities-v1.json`
- `docs/project/10-runtime-adapter.md` state mutation mapping section.
- `RuntimeMutationEngine` and direct callers.
- Current runtime mutation tests.

### Tasks

- [x] Enumerate every current event/state → mutation decision in Java.
- [x] Record source event kind.
- [x] Record identity requirement.
- [x] Record target resolution path.
- [x] Record mutation kind.
- [x] Record verification checkpoint side effect if any.
- [x] Record current test.
- [x] Detect hard-coded semantic dispatch.
- [x] Detect duplicate dispatch logic in connectors vs mutation engine.
- [x] Detect Auction-specific code.
- [x] Detect mutation paths without exact trace requirement.
- [x] Produce an audit table before writing JSON.

### Acceptance

- [x] Existing runtime semantics are understood before formalization.

---

## P18.2 — Define Generic Runtime Semantic Action vocabulary

### Read first

- P18.1 audit output.
- Research `04_USE_MAPPING_CANDIDATES.md`.
- Current mutation enum/types.

### Tasks

Define a vocabulary that does not depend on Ecore class names or Auction names.

At minimum evaluate/define:

- [x] `ATTRIBUTE_STATE_SET`.
- [x] `ATTRIBUTE_STATE_UNSET`.
- [x] `OBJECT_AVAILABLE`.
- [x] `OBJECT_UNAVAILABLE`.
- [x] `RELATION_INSERT`.
- [x] `RELATION_DELETE`.
- [x] `OPERATION_ENTER`.
- [x] `OPERATION_EXIT`.
- [x] `OPERATION_FAIL`.
- [x] `TRACE_ONLY`.
- [x] `NO_MUTATION`.
- [x] `UNSUPPORTED`.

For each action:

- [x] define required identity category;
- [x] define payload category;
- [x] define idempotency expectations;
- [x] define whether a USE mutation is mandatory/optional/forbidden;
- [x] define whether it survives metamodel migration unchanged.

### Acceptance

- [x] Generic action vocabulary is Ecore-independent.

---

## P18.3 — Design runtime mapping draft schema

### Read first

- Research:
  - `00_RESEARCH_BASELINE.md`
  - `03_RUNTIME_IDENTITY_MODEL.md`
  - `04_USE_MAPPING_CANDIDATES.md`
- Current project JSON schema/versioning conventions.
- Structural mapping schema only for style/validation patterns, not for semantics duplication.

### Tasks

Create `runtime-mapping.schema.json` in the repository-appropriate runtime resource location.

Each rule must support fields equivalent to:

- [x] rule ID;
- [x] source runtime/dimension;
- [x] normalized event kind;
- [x] authoritative-source requirement;
- [x] required runtime identity kind;
- [x] correlation requirement;
- [x] payload contract/reference;
- [x] generic semantic action;
- [x] current V1 target kind/binding anchor;
- [x] trace requirement;
- [x] concrete RuntimeMutation kind;
- [x] verification checkpoint or `NONE`;
- [x] support status;
- [x] evidence references;
- [x] assumptions;
- [x] unsupported conditions;
- [x] migration risk / Ecore V2 note.

Schema rules:

- [x] reject unknown structural fields unless project convention explicitly allows extensions;
- [x] require unique rule IDs at semantic validation layer;
- [x] prohibit Auction object names in canonical generic rules;
- [x] encode status including `DRAFT_WAITING_FOR_METAMODEL_V2` at document level;
- [x] schema version explicit.

### Tests

- [x] valid minimal document;
- [x] missing required field;
- [x] invalid action;
- [x] invalid event kind;
- [x] malformed identity requirement;
- [x] unknown field behavior.

---

## P18.4 — Author `jacamo-use-runtime-mapping-draft.json`

### Read first

- Research:
  - `02_RUNTIME_CAPABILITY_MATRIX.md`
  - `03_RUNTIME_IDENTITY_MODEL.md`
  - `04_USE_MAPPING_CANDIDATES.md`
  - `runtime-capabilities-v1.json`
- P18.1 procedural mapping audit.
- Current Structural Mapping V1 only for target compatibility lookup.

### Tasks

Add rules only where evidence is sufficient.

Required high-priority rules:

- [x] observable property changed → attribute state set.
- [x] observable property removed → attribute state unset/undefined policy.
- [x] artifact operation started → operation enter.
- [x] artifact operation completed → operation exit.
- [x] artifact operation failed → operation fail.
- [x] artifact lifecycle → restricted object lifecycle policy.
- [x] workspace/focus/link events only if current semantic target exists.
- [x] Jason action events → trace/cross-dimensional correlation according to evidence.
- [x] belief/goal events only if current target semantics are proven; otherwise `DEFERRED`/`TRACE_ONLY`.
- [x] Moise role/mission/goal deltas only where current structural target exists and identity is exact.
- [x] normative lifecycle not exposed → explicit unsupported/deferred rules or capability entry; no fabricated mutation.

For every rule:

- [x] evidence cites source API/research and current code support;
- [x] no concrete Auction instance name;
- [x] no fuzzy target lookup;
- [x] no duplicate semantic authority;
- [x] V2 migration risk classified.

### Document metadata

- [x] status = `DRAFT_WAITING_FOR_METAMODEL_V2`.
- [x] current target baseline identified as temporary V1 compatibility target.
- [x] do not create freeze manifest yet.

---

## P18.5 — Implement RuntimeMapping domain model + loader

### Read first

- Runtime mapping schema/draft only.
- Existing project mapping-loader patterns.
- Diagnostics conventions.

### Tasks

- [x] Create typed runtime mapping model.
- [x] Load mapping bytes once per load.
- [x] Validate JSON schema.
- [x] Parse typed rules.
- [x] Preserve deterministic rule order.
- [x] Preserve source/evidence metadata.
- [x] Return structured diagnostics.
- [x] Fail clearly on malformed document.
- [x] Do not silently fallback to hard-coded defaults.
- [x] Keep loader independent of Swing/UI.

### Tests

- [x] valid load;
- [x] malformed JSON;
- [x] schema invalid;
- [x] duplicate rule IDs delegated to semantic validator;
- [x] deterministic reload.

---

## P18.6 — Implement semantic RuntimeMappingValidator

### Read first

- Runtime mapping model/schema/draft.
- Current RuntimeEvent kinds.
- Current mutation kinds.
- Trace target kinds.
- Structural Mapping V1 target/projection registry only as needed.

### Tasks

Validate:

- [x] duplicate rule IDs;
- [x] duplicate/conflicting selector rules;
- [x] unsupported source runtime;
- [x] unknown event kind;
- [x] invalid semantic action;
- [x] invalid mutation kind;
- [x] impossible payload requirement;
- [x] operation rule missing correlation;
- [x] mutation rule missing trace requirement where required;
- [x] invalid target kind;
- [x] target structural anchor does not exist;
- [x] rule violates semantic authority policy;
- [x] rule could double-apply an organisation fact;
- [x] V1 compatibility anchors resolve exactly;
- [x] deferred/unsupported rule cannot accidentally mutate.

### Diagnostics

- [x] each failure has stable diagnostic code;
- [x] include rule ID and field/context;
- [x] no silent downgrade from ERROR to warning for correctness failures.

---

## P18.7 — Runtime mapping compatibility report

### Read first

- Runtime mapping draft.
- RuntimeMappingValidator output.
- Structural Mapping V1 and verification projection registry.
- Trace target kinds.

### Tasks

Create a machine/human-readable compatibility report:

- [x] event rule;
- [x] generic action;
- [x] current V1 semantic anchor;
- [x] generated USE target type;
- [x] TraceIndex target kind;
- [x] mutation support;
- [x] status;
- [x] V2 migration risk.

Classify each rule:

- [x] `READY_V1_TEMPORARY`.
- [x] `TRACE_ONLY`.
- [x] `DEFERRED_FOR_V2`.
- [x] `UNSUPPORTED`.

### Acceptance

- [x] No runtime rule targets a non-existent USE construct.

---

## P18.8 — Phase 18 tests and docs

### Read first

- `docs/project/10-runtime-adapter.md`.
- `docs/project/09-traceability-binding-resolver.md` only if identity wording changed.
- `docs/project/13-testing-quality.md`.

### Tasks

- [x] Schema test suite.
- [x] Loader test suite.
- [x] Semantic validator test suite.
- [x] Negative conflicting-rule tests.
- [x] V1 compatibility tests.
- [x] Existing runtime tests remain green.
- [x] Add `docs/project/runtime-mapping-draft.md` or repository-consistent equivalent.
- [x] Document six distinct layers:
  - upstream runtime fact;
  - RuntimeEvent;
  - identity;
  - generic semantic action;
  - runtime mapping/binding;
  - USE mutation.
- [x] Explicitly state OCL is not part of this mapping layer.

### Exit criteria

- [x] Declarative Runtime Mapping Draft exists.
- [x] Loader/validator exist.
- [x] Draft is not frozen.
- [x] Draft can be migrated to V2 by replacing/reconciling target-binding layer.

---

Execution evidence (2026-09-19): draft implementation c2031744; initial RuntimeMappingTest 4/4 and module regression 135/135 PASS (historical phase-local counts). Capability input is the reconciled research runtime-capabilities-v1.json. Derived compatibility report: target/runtime-mapping-compatibility.json. No freeze manifest. Engine integration is Phase 19. Fresh combined reactor gate: 285/285 PASS; integration closure is recorded below Phase 20.

# Phase 19 — Runtime Mapping Integration & Mirror Correctness

**Objective:** use Runtime Mapping Draft as the semantic dispatch source and prove supported JaCaMo authoritative state equals the USE mirror.

---

## P19.1 — Integrate mapping rule selection into runtime pipeline

### Read first

- Runtime mapping draft/model/validator.
- `RuntimeMutationEngine`.
- Runtime connector → queue → mutation call path.
- P18.1 procedural audit.

### Tasks

- [x] Introduce one mapping-resolution service/interface.
- [x] Event semantic dispatch uses declarative rules where a rule exists.
- [x] Mutation mechanics remain in RuntimeMutationEngine.
- [x] Remove or isolate duplicate Java event→mutation semantic tables.
- [x] Preserve unsupported/deferred event diagnostics.
- [x] Ensure no fallback Java branch contradicts JSON rule.
- [x] Mapping lookup must be deterministic.
- [x] Mapping lookup must occur after event validation and before mutation.

### Tests

- [x] valid rule dispatch;
- [x] missing rule;
- [x] unsupported rule;
- [x] conflicting rule prevented at load;
- [x] existing Auction behavior unchanged.

---

## P19.2 — Attribute state synchronization

### Read first

- Research `04_USE_MAPPING_CANDIDATES.md` CArtAgO property rows.
- Current observable-property connector code.
- USE adapter attribute mutation methods.

### Tasks

- [x] Resolve runtime Artifact exactly.
- [x] Resolve projected property exactly.
- [x] Convert runtime value to compiled USE attribute type.
- [x] Apply `ATTRIBUTE_STATE_SET`.
- [x] Apply removal/unset as USE undefined according to existing state policy.
- [x] Reject unknown/unprojected property mutation.
- [x] Preserve diagnostic/evidence for discovered but unbound property.
- [x] Ensure snapshot missing bound property clears stale state.

### Tests

- [x] bool/string/number supported conversions;
- [x] undefined/removal;
- [x] unknown property;
- [x] wrong type;
- [x] same property name on two artifacts;
- [x] resync removal.

---

## P19.3 — Operation lifecycle synchronization

### Read first

- Research `04_USE_MAPPING_CANDIDATES.md` operation rows.
- CArtAgO OpId handling.
- Runtime operation correlation engine/tests.

### Tasks

- [x] `OP_STARTED` resolves exact Artifact + projected USE operation.
- [x] Bind arguments by exact signature/order/type.
- [x] Preserve runtime Agent/Artifact/OpId correlation.
- [x] Create operation enter state exactly once.
- [x] `OP_COMPLETED` closes same correlation.
- [x] `OP_FAILED` fails same correlation and cannot masquerade as successful exit.
- [x] suspended/resumed events are trace-only unless an explicit operation-state model exists.
- [x] requested event is not treated as actual operation enter if upstream semantics do not guarantee start.
- [x] retired stream operation cannot receive a late terminal mutation.

### Tests

- [x] normal enter/exit;
- [x] enter/fail;
- [x] concurrent same-name ops;
- [x] terminal without enter;
- [x] duplicate terminal;
- [x] stale terminal;
- [x] parameter conversion failure.

---

## P19.4 — Object and relation lifecycle rules

### Read first

- Runtime mapping rules for object/relation actions.
- `docs/project/09-traceability-binding-resolver.md`.
- USE adapter object/link mutation APIs.

### Tasks

- [x] Define when `OBJECT_AVAILABLE` may create/activate an object.
- [x] Require exact pre-existing semantic trace or explicit dynamic-instance policy.
- [x] Unknown runtime artifact must not auto-create arbitrary semantic object.
- [x] Define object disposal/tombstone policy.
- [x] Define link insert/delete exact association resolution.
- [x] Prevent duplicate link insertion.
- [x] Prevent deleting an unrelated similarly named link.
- [x] Enforce multiplicity failure behavior explicitly.

### Tests

- [x] traced dynamic lifecycle allowed path;
- [x] unknown dynamic object quarantined;
- [x] exact link insert/delete;
- [x] duplicate link;
- [x] wrong association kind;
- [x] disposal with dependent correlations.

---

## P19.5 — Organisation state synchronization

### Read first

- Research:
  - `02_RUNTIME_CAPABILITY_MATRIX.md` Moise rows
  - `03_RUNTIME_IDENTITY_MODEL.md` Moise section
  - `04_USE_MAPPING_CANDIDATES.md` Moise section
- Current Moise snapshot/diff code.
- Organisation structural mapping targets only as needed.

### Tasks

- [x] Make Moise OE the semantic authority for organisation state unless code evidence requires documented exception.
- [x] Map exact supported role-player deltas.
- [x] Map mission-player deltas when target exists.
- [x] Map organisational goal-state changes only where current representation has a safe target.
- [x] Map group/scheme instance lifecycle only if instance semantics are represented.
- [x] Prevent CArtAgO organisation-board events from double-applying Moise state.
- [x] Keep derived permission/obligation state trace/report-only until Phase 23 semantic scope.

### Tests

- [x] deterministic snapshot diff;
- [x] role add/remove;
- [x] mission add/remove where supported;
- [x] no duplicate application from board artifact events;
- [x] unbound OE entity blocks/diagnoses according to current LIVE contract.

---

## P19.6 — Jason state synchronization and safe boundaries

### Read first

- P16 capability matrix Jason rows.
- Current Jason connector.
- Current structural/instance representation for Belief/Goal/ExternalAction only.

### Tasks

- [x] Classify each current Jason event as state mutation, correlation evidence, or trace-only.
- [x] Belief add/remove mutates USE only if exact semantic/object/link policy exists.
- [x] Goal lifecycle mutates USE only if a runtime state slot/representation is defined.
- [x] Action events correlate with CArtAgO operation when exact static/runtime relation exists.
- [x] Do not create a second USE operation execution for the same physical action/operation lifecycle.
- [x] Messages remain trace-only/unsupported unless exact current target semantics are proven.
- [x] Intentions remain trace-only/deferred unless current project explicitly models them.

### Tests

- [x] exact mapped action correlation;
- [x] unknown belief/goal quarantined or trace-only;
- [x] no duplicate action/operation execution;
- [x] message limitation protected.

---

## P19.7 — Idempotency and double-apply protection

### Read first

- Ordered queue.
- RuntimeTrace.
- Snapshot synchronization code.
- Mapping dispatch code.

### Tasks

- [x] Define accepted event idempotency key.
- [x] Prevent same accepted event from mutating twice.
- [x] Prevent reconnect replay of old accepted deltas.
- [x] Buffer and order deltas around initial snapshot according to existing contract.
- [x] Ensure snapshot + buffered delta does not double-apply same fact.
- [x] Ensure stream generation protects workspace replacement.
- [x] Track processed/rejected/failed/dropped counts separately.

### Tests

- [x] duplicate event ID;
- [x] duplicate sequence;
- [x] reconnect replay;
- [x] initial sync concurrent delta;
- [x] old-stream callback;
- [x] zero silent drop assertion.

---

## P19.8 — Authoritative mirror drift comparator

### Read first

- Current drift detection implementation.
- Runtime snapshot abstraction.
- USE mirror state reading APIs.

### Tasks

Compare all **supported projected runtime facts**:

- [x] object existence/lifecycle;
- [x] scalar attributes;
- [x] supported association links;
- [x] operation-open correlations where appropriate;
- [x] organisation state where represented.

For each drift:

- [x] runtime identity;
- [x] semantic identity;
- [x] USE target;
- [x] expected authoritative value;
- [x] actual mirror value;
- [x] diagnostic code;
- [x] recovery policy.

- [x] `AUTO_RESYNC` must perform fresh authoritative sync.
- [x] state returns LIVE only after successful full sync.

### Tests

- [x] each drift category;
- [x] no-drift baseline;
- [x] resync repairs drift;
- [x] failed resync → ERROR/disconnect.

---

## P19.9 — Runtime evidence export / replay artifacts

### Read first

- Existing Phase 14 evidence generator.
- Current `.use/.cmd` generator.
- Runtime report/event serializers.

### Tasks

Keep runtime source of truth in memory; exports are derived evidence.

- [x] Ensure reproducible `model.use` or project-named `.use` export.
- [x] Ensure initial-state `.cmd` export.
- [x] Export normalized runtime event log.
- [x] Export resolved runtime mapping decisions if useful.
- [x] Export trace.
- [x] Add optional `runtime-replay.cmd` only if USE command semantics can faithfully represent the supported mutation sequence.
- [x] If replay cannot preserve a semantic fact, document omission rather than fabricating commands.
- [x] Include manifest/hashes where repository conventions require.

### Acceptance

- [x] Evidence makes static vs runtime state clearly distinguishable.

---

## P19.10 — Mirror Correctness Gate

### Read first

- P19.8 drift comparator results.
- P19.9 evidence outputs.
- `docs/project/13-testing-quality.md`.

### Required gate

For supported runtime subset:

```text
JaCaMo authoritative runtime state == USE MSystemState mirror
```

### Tasks

- [x] Run full initial sync comparison.
- [x] Run state-changing event comparison.
- [x] Run operation lifecycle comparison.
- [x] Run organisation diff comparison.
- [x] Run disconnect/reconnect comparison.
- [x] Run forced drift + resync comparison.
- [x] Assert zero unexplained drift after resync.
- [x] Assert zero silent dropped accepted events.
- [x] Assert zero wrong-target mutation.
- [x] Assert unknown/unbound facts are explicit, not silently ignored.
- [x] Produce machine-readable mirror-correctness summary.

### Exit criteria

- [x] Mirror correctness PASS for supported subset.
- [x] Any unsupported facts are explicitly excluded with rationale.
- [x] OCL expansion may proceed only after this gate passes.

---

Execution evidence (2026-09-19): two wrong-target regressions reproduced RED (2/2 failures), then passed. Fresh module unit/component gate 139/139 plus release integration 3/3 PASS; live mirror summary records zero unexplained post-resync drift and zero queue failures/rejections/drops. RuntimeMapping drives mutation/comparison/checkpoints; projected attribute trace is now explicit. Golden old-trace negative control confirms only new projection records changed. See runtime-mapping-draft.md for scope and exclusions. Tasks concerning unavailable dynamic Moise/Jason state, cross-dimensional invocation joins and in-flight snapshot operation state are resolved as explicit unsupported boundaries, not implemented equivalence. Combined reactor 285/285 PASS; integration closure follows below.

# Phase 20 — Full JaCaMo Runtime End-to-End

**Objective:** move beyond isolated in-process component evidence toward the closest feasible real `.jcm` execution path on the pinned environment.

---

## P20.1 — Audit standalone JaCaMo launch path

### Read first

- Research `01_JACAMO_RUNTIME_ARCHITECTURE.md`.
- Current compatibility manifest.
- Existing Auction runtime integration test.
- JaCaMo launcher integration code/dependency APIs only as needed.

### Tasks

- [x] Identify exact JaCaMo launcher/runtime API available in pinned dependency set.
- [x] Determine whether current plugin dependencies include the full launcher or only component libraries.
- [x] Determine classpath/project layout requirements for `.jcm` execution.
- [x] Determine safe test isolation/shutdown requirements.
- [x] Identify gap between current in-process test and true JaCaMo project launch.
- [x] Record blockers as technical, environment, or unsupported-version blockers.
- [x] Do not upgrade runtime versions solely to make launcher easier.

---

## P20.2 — Build a standalone/full-project runtime test harness

### Read first

- P20.1 launch-path audit.
- Existing Auction fixture files.
- Existing test process/resource conventions.

### Tasks

- [ ] Reuse checked-in Auction project source.
- [ ] Launch through the most authentic supported JaCaMo path.
- [ ] Ensure deterministic startup wait/ready condition.
- [ ] Attach plugin/runtime observation connectors without duplicate listeners.
- [ ] Ensure controlled shutdown.
- [ ] Capture stdout/log only when needed for diagnostics.
- [ ] Avoid arbitrary user-project command execution beyond explicit test fixture.

### Tests

- [ ] launch succeeds;
- [ ] launch failure has actionable diagnostic;
- [ ] teardown leaves no runtime thread/listener leak.

---

## P20.3 — Capture actual runtime trace

### Read first

- Research `05_AUCTION_RUNTIME_WALKTHROUGH.md`.
- RuntimeTrace/event schema.
- Full-project harness.

### Capture at minimum

- [ ] sequence;
- [ ] timestamp;
- [ ] runtime source;
- [ ] raw callback category;
- [ ] agent runtime ID;
- [ ] workspace ID;
- [ ] artifact ID;
- [ ] operation ID/name;
- [ ] arguments;
- [ ] property name/value;
- [ ] Moise snapshot delta;
- [ ] normalized event;
- [ ] resolved SemanticId;
- [ ] resolved USE target;
- [ ] mapping rule ID;
- [ ] mutation result.

### Scenarios

- [ ] platform boot / initial focus-role setup;
- [ ] operation start;
- [ ] improving/state-changing operation;
- [ ] operation with no state change where applicable;
- [ ] operation failure;
- [ ] stop/close;
- [ ] disconnect/reconnect/resync.

---

## P20.4 — Compare actual trace with capability/mapping contracts

### Read first

- P20.3 captured trace.
- P16 capability matrix.
- Runtime Mapping Draft.

### Tasks

- [ ] Confirm every observed event is known or explicitly unknown.
- [ ] Confirm payload assumptions match actual callbacks.
- [ ] Confirm operation correlation matches actual lifecycle.
- [ ] Confirm property delta timing assumptions.
- [ ] Confirm organisation polling/diff assumptions.
- [ ] Identify events present in code but absent in scenario without claiming they never occur.
- [ ] Update mapping/capability docs only when evidence justifies it.
- [ ] Add regression tests for any discovered mismatch.

---

## P20.5 — Full runtime mirror E2E

### Read first

- Full-project harness.
- P19 Mirror Correctness Gate.

### Tasks

- [ ] Import project statically.
- [ ] Generate/load USE model/state.
- [ ] Start actual JaCaMo project.
- [ ] Bind runtime identities exactly.
- [ ] Full authoritative sync.
- [ ] Reach LIVE.
- [ ] Execute deterministic scenario.
- [ ] Apply Runtime Mapping Draft.
- [ ] Compare mirror to runtime at checkpoints.
- [ ] Disconnect.
- [ ] Mutate/advance runtime if scenario supports it.
- [ ] Reconnect/full resync.
- [ ] Assert zero post-resync drift.

### Exit criteria

- [ ] Full-project path works, OR
- [x] exact technical limitation is documented and the closest supported in-process path remains bounded/evidence-backed.

---

Execution evidence (2026-09-19): the second P20.5 exit alternative is satisfied, not the full-project-success alternative. `tools/runtime/launcher_probe.py` executes pinned JaCaMo 1.3.0 in isolated JVMs. Original fixture fails .jcm parsing; syntax/path adaptation reaches real Jason/CArtAgO startup but fails Moise XML schema/OrgBoard initialization. See [phase20-runtime-evidence.md](../project/phase20-runtime-evidence.md). Unchecked full-project items remain explicitly unproven; no full Agent -> Artifact -> Organisation claim. Closest supported component timeline and mapping/mirror evidence are exercised by LiveJaCaMoAuctionIntegrationTest. No version upgrade or invented normative fixture semantics.

Continuation audit: repository was already clean at `bbdb6ef7` on `phase/20-runtime-evidence`;
the earlier Phase 18/19 implementation and validator changes were committed and retained.
The namespace/version-only XML probe still reports six XSD diagnostics. A separate
OSBuilder control loads and launches with the pinned jars, so this is not a proven
runtime-version impossibility. Launcher boards expose `ora4mas.nopl.oe.Group/Scheme`,
not the connector's `moise.oe.OE`; a proper board-state adapter is still required.
No semantics-preserving repair of the source self-referencing plan/natural-language
deadline is established. Full-project acceptance stays unchecked. Probe assertions,
input/jar/schema hashes and fresh 285/285 reactor results are retained in
[machine-readable evidence](../project/evidence/phase18-20-2026-09-19.json).

Integration closure (2026-09-19): Phase 18/19/20 branches were fast-forward integrated
into `main` through `016e74b6` and pushed to origin together with all three phase branches.
Post-merge `mvn -B -pl use-plugin verify`: **142/142 PASS** (139 unit/component,
3 package integration; zero failures/errors/skips). The compatibility metadata test
first caught missing host fields in the newly appended evidence; those were filled
from `mvn -v`, and both the focused test and final module gate passed unchanged.
The final probe input hashes and mirror bundle hashes were checked. Frozen Core,
runtime dependency versions and static Auction source were unchanged.
Phase 18 is complete as a draft; Phase 19 is `SUPPORTED_SUBSET_COMPLETE`;
Phase 20 closes only the documented technical-limitation alternative. Its full-project
tasks above remain unproven and must not be presented as complete standalone E2E.

# Phase 21 — Metamodel-Decoupling & V2 Migration Readiness

**Objective:** ensure the runtime core can continue now and later migrate to a new Ecore without rewriting connectors/queue/trace/mutation mechanics.

---

## P21.1 — Audit runtime dependencies on V1 metamodel vocabulary

### Read first

- Runtime package imports/usages.
- Semantic model enum/kinds.
- Trace target kinds.
- Runtime Mapping Draft.

### Tasks

Search for:

- [x] direct `MetamodelKind` checks inside runtime core;
- [x] direct V1 EClass names inside connectors;
- [x] direct V1 EAttribute names inside generic runtime dispatch;
- [x] structural mapping rule IDs hard-coded into connectors;
- [x] Auction-specific names;
- [x] generated USE classifier names hard-coded outside target adapter.

Classify each dependency:

- [x] legitimate adapter boundary;
- [x] removable coupling;
- [x] required current V1 binding;
- [x] test-only;
- [x] bug.

---

## P21.2 — Introduce/solidify Ecore-independent runtime target abstraction

### Read first

- P21.1 audit.
- Runtime Mapping generic action vocabulary.
- TraceIndex public API.

### Tasks

- [x] Define generic runtime target request/descriptor if current code lacks one.
- [x] Runtime core expresses intent as object/attribute/relation/operation target category, not V1 class names.
- [x] Ecore/USE-specific binding is delegated to resolver/adapter.
- [x] Preserve exact semantic ID requirement.
- [x] Preserve diagnostics/provenance.
- [x] Avoid creating another parallel TraceIndex.

### Tests

- [x] runtime connector tests do not require V1-specific target names except fixture bindings;
- [x] adapter tests prove V1 binding still works.

---

## P21.3 — Structural/runtime binding adapter boundary

### Read first

- Structural Mapping V1 output/TransformationPlan target registry.
- Runtime Mapping Draft.
- USE adapter.

### Tasks

- [x] Define one component responsible for current metamodel-specific runtime target binding.
- [x] Input: runtime semantic action + exact semantic identity/trace.
- [x] Output: exact current USE target or explicit unresolved result.
- [x] Keep structural mapping read-only.
- [x] Ensure future V2 adapter can replace/reconcile this layer without changing connectors.

### Tests

- [x] current V1 target success;
- [x] missing target;
- [x] incompatible target kind;
- [x] stale trace;
- [x] deterministic result.

---

## P21.4 — Create Metamodel V1→V2 diff/migration tooling skeleton

### Read first

- `docs/project/04-jacamo-metamodel-baseline.md`.
- `docs/project/05-metamodel-mapping-contract.md`.
- Existing mapping audit utilities/tests.

### Tasks

Implement tooling capable of later comparing two Ecore baselines:

- [x] class added/removed/renamed candidate (exact structural diff; no fuzzy auto-rename acceptance);
- [x] attribute added/removed/type/bounds change;
- [x] reference added/removed/target/bounds/containment change;
- [x] inheritance change;
- [x] affected structural mapping entries;
- [x] affected projection anchors;
- [x] affected runtime mapping target bindings;
- [x] affected OCL contexts/navigation;
- [x] affected golden `.use/.cmd` outputs.

Do not require V2 to exist yet; add synthetic fixture diff tests.

---

## P21.5 — V2 migration-readiness test

### Read first

- P21.2/P21.3 abstractions.
- Runtime connector tests.

### Tasks

Using a synthetic alternate target vocabulary/adapter:

- [x] prove Jason connector unchanged;
- [x] prove CArtAgO connector unchanged;
- [x] prove Moise connector unchanged;
- [x] prove RuntimeEvent unchanged;
- [x] prove RuntimeTrace unchanged;
- [x] prove queue/lifecycle unchanged;
- [x] prove mapping rule source semantics can stay while target binding changes;
- [x] prove RuntimeMutation mechanics remain reusable.

### Exit criteria

- [x] Waiting for Ecore V2 no longer blocks runtime engineering.
- [x] V2 impact is concentrated in semantic/mapping/binding/transformation layers.

---

Evidence: [Phase 21 migration readiness](../project/phase21-migration-readiness.md).

# Phase 22 — Runtime Verification Completion

**Objective:** run verification on top of the now-proven mirror, not use OCL to hide synchronization uncertainty.

---

## P22.1 — Verification checkpoint contract

### Read first

- `docs/project/11-verification-engine.md`.
- Runtime mapping rule checkpoint fields.
- Current RuntimeVerificationEngine.

### Tasks

Define/validate first-class checkpoints:

- [x] `SNAPSHOT`.
- [x] `AFTER_MUTATION`.
- [x] `OPERATION_PRE`.
- [x] `OPERATION_POST`.
- [x] `STREAM_BOUNDARY`.

For each:

- [x] trigger;
- [x] valid mirror state requirement;
- [x] verification mode;
- [x] event/trace context;
- [x] result handling;
- [x] behavior when mirror is STALE/ERROR.

### Acceptance

- [x] Verification cannot report current runtime truth while mirror is not LIVE/current.

---

## P22.2 — Runtime invariants after mutation/snapshot

### Read first

- Verification engine invariant path.
- Dependency index.
- Mirror version/snapshot state.

### Tasks

- [x] Full invariant check after authoritative snapshot.
- [x] Targeted/conservative invariant check after state-changing delta.
- [x] Keep full-check fallback.
- [x] Preserve event ID/correlation/snapshot version.
- [x] OCL undefined remains ERROR where contract says so.
- [x] Unknown runtime data does not become false PASS.

### Tests

- [x] PASS;
- [x] FAIL;
- [x] ERROR/undefined;
- [x] targeted/full equivalence;
- [x] STALE state no-current-result behavior.

---

## P22.3 — Runtime operation PRE/POST completion

### Read first

- Current operation verification code.
- P19.3 operation mapping.

### Tasks

- [x] PRE occurs on exact mapped operation enter.
- [x] Capture pre-state once.
- [x] Bind self/args exactly.
- [x] Do not block JaCaMo baseline execution.
- [x] POST only after successful matching exit.
- [x] Preserve `@pre`.
- [x] failure/abort → POST `SKIPPED`.
- [x] retired correlation cannot produce a POST result.
- [x] mapping/argument resolution error → explicit ERROR diagnostic.

### Tests

- [x] pre pass/fail;
- [x] post pass/fail;
- [x] @pre;
- [x] op fail;
- [x] duplicate terminal;
- [x] stream boundary.

---

## P22.4 — Runtime ordering/history verification representation

### Read first

- RuntimeTrace implementation.
- Existing generated verification model/profile extension mechanism.
- USE/OCL capabilities already used by the project.

### Tasks

Agent chooses the least invasive evidence-backed representation without changing frozen Structural Mapping V1.

Preferred decision order:

1. reuse existing verification-profile extension mechanism if it can expose minimal trace state safely;
2. otherwise use a dedicated trace evaluator for ordering and clearly distinguish it from OCL;
3. do not create a large speculative runtime metamodel solely for ordering.

Implement:

- [x] generic happened-before / start-before-terminal constraints;
- [x] stream-generation validity;
- [x] correlation ordering;
- [x] exact event attribution;
- [x] case-specific Auction ordering outside core.

### Tests

- [x] generic synthetic ordering;
- [x] Auction valid order;
- [x] Auction invalid order;
- [x] retired-stream event violation/rejection.

---

## P22.5 — Runtime violation model and navigation

### Read first

- Verification result/report model.
- TraceIndex reverse lookup.
- RuntimeTrace lookup.

### Tasks

Ensure every result carries where available:

- [x] constraint/rule ID;
- [x] checkpoint;
- [x] runtime event ID;
- [x] sequence;
- [x] correlation ID;
- [x] runtime key;
- [x] SemanticId;
- [x] USE context object/operation;
- [x] source span/provenance;
- [x] mirror version/fingerprint;
- [x] PASS/FAIL/ERROR/SKIPPED.

Navigation path:

- [x] result → USE target;
- [x] USE target → trace;
- [x] trace → semantic source;
- [x] semantic source → runtime identity/event;
- [x] no similarly-named fallback.

---

## P22.6 — Runtime verification E2E gate

### Read first

- P19 mirror-correctness evidence.
- P20 full runtime harness/evidence.
- Current case OCL only as needed.

### Tasks

- [x] Run valid scenario.
- [x] Run invariant violation scenario.
- [x] Run PRE violation scenario.
- [x] Run operation failure scenario.
- [x] Run ordering violation scenario if supported.
- [x] Disconnect/reconnect/resync.
- [x] Ensure violations are evaluated against current mirror only.
- [x] Ensure exact trace attribution.

### Exit criteria

- [x] Runtime verification complete for supported mirrored state.
- [x] Mirror correctness remains independently tested from OCL correctness.

---

Evidence: [Phase 22 verification](../project/phase22-verification-evidence.md).

# Phase 23 — Cross-Dimensional & Supported Normative Verification

**Objective:** verify meaningful Agent–Environment–Organisation consistency without inventing semantics.

---

## P23.1 — Cross-dimensional relation inventory

### Read first

- `docs/project/04-jacamo-metamodel-baseline.md` cross-dimensional relations.
- `docs/project/05-metamodel-mapping-contract.md` VP004–VP007.
- P16/P19 runtime capability/mapping outputs.

### Tasks

For each candidate relation classify:

- [x] structural only;
- [x] offline verifiable;
- [x] runtime verifiable;
- [x] requires exact runtime correlation;
- [x] requires additional semantics;
- [x] unsupported.

Review at minimum:

- [x] `Agent.artifact`;
- [x] `Agent.joinWorkspace`;
- [x] `ExternalAction.operation`;
- [x] `Plan.RefArtifact`;
- [x] `ObsProperty.obsproperty`;
- [x] `Role.players`;
- [x] `Organisation.deploysAgent`;
- [x] `OGoal.OGoalToGoal`.

Do not turn a structural EReference into a behavioral invariant automatically.

---

## P23.2 — Implement safe Agent ↔ Environment checks

### Read first

- P23.1 approved-by-evidence subset.
- Current trace/correlation data.

### Tasks

Where evidence is sufficient:

- [x] Agent ↔ Workspace consistency.
- [x] Agent ↔ Artifact accessibility/binding consistency.
- [x] ExternalAction ↔ Artifact Operation correlation.
- [x] Plan.RefArtifact target exactness.
- [x] runtime action ↔ CArtAgO operation cross-evidence.

### Tests

- [x] positive exact link;
- [x] wrong target;
- [x] same-name wrong artifact negative case;
- [x] missing evidence becomes unsupported/error, not guessed pass/fail.

---

## P23.3 — Implement safe Environment ↔ Agent checks

### Read first

- P23.1 inventory.
- Current ObsProperty↔Belief static trace.
- Jason/CArtAgO runtime evidence.

### Tasks

- [x] Verify percept/property↔belief consistency only when project contains an exact semantic relation and connector semantics support the runtime claim.
- [x] Do not assume every observable property change becomes a Jason belief.
- [x] Preserve unsupported delivery semantics explicitly.
- [x] Add positive/negative fixtures for the supported subset.

---

## P23.4 — Implement safe Organisation ↔ Agent/Goal checks

### Read first

- P23.1 inventory.
- Moise runtime mapping outputs.

### Tasks

Where supported:

- [x] Role.players ↔ Agent consistency.
- [x] Organisation deployment relation consistency.
- [x] OGoal ↔ Jason Goal consistency.
- [x] mission/goal runtime alignment if identity/evidence exists.
- [x] Do not infer action permission/obligation from role unless normative semantics are explicitly established.

---

## P23.5 — Normative runtime API evidence audit

### Read first

- Research capability matrix normative notes.
- Current pinned Moise APIs/source only for unresolved normative questions.
- `docs/project/16-research-evidence-boundaries.md` normative section.

### Tasks

For each concept record:

- [x] role adoption;
- [x] mission commitment;
- [x] group/scheme membership;
- [x] organisational goal state;
- [x] derived obligation;
- [x] derived permission;
- [x] prohibition if exposed;
- [x] activation;
- [x] fulfilment;
- [x] violation;
- [x] expiration/deadline.

Classify:

- [x] `SUPPORTED_EXACT`;
- [x] `SUPPORTED_PARTIAL`;
- [x] `DERIVABLE_WITH_ASSUMPTION`;
- [x] `NOT_EXPOSED`;
- [x] `UNSAFE_TO_INFER`.

Do not implement unproven lifecycle semantics.

---

## P23.6 — Implement normative supported subset only

### Read first

- P23.5 classification.
- Current organisation runtime state representation.

### Tasks

- [x] Implement only `SUPPORTED_EXACT` by default.
- [x] `SUPPORTED_PARTIAL` may be exposed as state/evidence with explicit limitation, not as stronger semantic claim.
- [x] Keep structural Norm separate from runtime normative state.
- [x] Keep runtime normative state separate from OCL verification result.
- [x] Do not auto-compile obligation/permission/prohibition into OCL invariants.
- [x] Add unsupported-boundary diagnostics/tests for everything not implemented.

### Exit criteria

- [x] Cross-dimensional supported rules work.
- [x] Normative scope is bounded and evidence-backed.

---

Evidence: [Phase 23 supported subset and boundaries](../project/phase23-cross-dimensional-evidence.md).

# Phase 24 — Constraint Translation Closure & Multi-Case Validation

**Objective:** close the supported translation subset and prove generic reuse beyond Auction without waiting for human input unless no suitable case exists.

---

## P24.1 — Audit current constraint translation inventory

### Read first

- `docs/project/07-constraint-translation-and-ocl.md`.
- Constraint IR/translator tests.
- Current translated/core/case OCL manifests.

### Tasks

For each translator rule record:

- [ ] source construct;
- [ ] binding requirements;
- [ ] target OCL form;
- [ ] status EXACT/SOUND_SUBSET/LOSSY/UNSUPPORTED;
- [ ] provenance;
- [ ] positive test;
- [ ] negative test;
- [ ] ambiguity test;
- [ ] limitation.

---

## P24.2 — Complete EXACT translations

### Read first

- P24.1 inventory entries marked EXACT candidates.
- Source parser/AST code only for those constructs.

### Tasks

- [ ] Finish missing EXACT supported rules.
- [ ] Deterministic generation.
- [ ] Exact context/operation binding.
- [ ] Source dependency trace.
- [ ] Generated OCL parse/type-check.
- [ ] Positive + negative fixtures.
- [ ] No arbitrary Java body translation.

---

## P24.3 — Complete safe SOUND_SUBSET translations

### Read first

- Only P24.1 SOUND_SUBSET candidates.
- Research/evidence supporting one-direction soundness.

### Tasks

- [ ] Implement only when one-direction guarantee can be documented.
- [ ] Record assumption and preservation direction.
- [ ] Do not label as equivalent.
- [ ] Add regression proving unsupported constructs do not emit partial fake formulas.

---

## P24.4 — Preserve LOSSY/UNSUPPORTED boundaries

### Read first

- P24.1 LOSSY/UNSUPPORTED list.

### Tasks

- [ ] LOSSY rules disabled by default unless already explicitly approved by current project contract.
- [ ] UNSUPPORTED expressions remain source-traceable.
- [ ] Diagnostic includes reason.
- [ ] Unrelated supported extraction continues.
- [ ] No silent formula truncation.
- [ ] Add boundary tests.

---

## P24.5 — Select Case Study #2 autonomously if possible

### Read first

- Existing checked-in examples/fixtures list.
- Local JaCaMo examples available in pinned source/dependencies if repository already vendors/references them.
- Do not web-research broadly unless project workflow explicitly allows it and no local candidate exists.

### Candidate requirements

Prefer a case with:

- [ ] Agent;
- [ ] CArtAgO Artifact;
- [ ] observable property;
- [ ] external action/operation;
- [ ] organisation;
- [ ] role;
- [ ] goal/mission;
- [ ] deterministic runnable scenario.

### Tasks

- [ ] Evaluate local candidates.
- [ ] Select the best technically suitable public/local example without user input if semantics are clear.
- [ ] Record why selected.
- [ ] If no suitable candidate exists, record `HUMAN_INPUT_REQUIRED_CASE_STUDY_2` for Phase 25 and continue all other Phase 24 tasks.

---

## P24.6 — Import/transform Case Study #2

### Read first

- Only selected case files.
- Parser/mapping docs only for failures encountered.

### Tasks

- [ ] Project discovery.
- [ ] Semantic extraction.
- [ ] Exact resolution.
- [ ] USE transformation.
- [ ] `.use` generation.
- [ ] `.cmd` generation.
- [ ] trace generation.
- [ ] OCL profile only where evidence requires.
- [ ] runtime connector compatibility.
- [ ] initial mirror sync.

Forbidden:

- [ ] no core special case for Case Study #2.

---

## P24.7 — Case Study #2 runtime positive/negative scenarios

### Read first

- Selected case source + generated trace.
- Runtime capability/mapping rules applicable to that case.

### Tasks

- [ ] Positive state synchronization.
- [ ] At least one negative verification scenario.
- [ ] Exact violation attribution.
- [ ] Reconnect/resync.
- [ ] Cross-dimensional rule where applicable.
- [ ] Unsupported features reported explicitly.

---

## P24.8 — Generic reuse audit

### Read first

- Core production code diff since Phase 16.
- Auction and Case Study #2 tests.

### Tasks

Search core for:

- [ ] Auction class names;
- [ ] Auction operation names;
- [ ] Case Study #2 names;
- [ ] hard-coded runtime object IDs;
- [ ] hidden case bindings;
- [ ] branch logic keyed by example project.

- [ ] Remove unjustified case-specific core logic.
- [ ] Keep example-specific configuration under example/test/profile locations.

### Exit criteria

- [ ] Translation supported subset closed.
- [ ] Generic reuse demonstrated on two cases, OR Case Study #2 is the only remaining explicit Phase 25 input.

---

# Phase 25 — Human Inputs / Research Decisions

**Objective:** collect only the information that could not be safely produced by the Agent after completing all independent engineering work.

The Agent must arrive here with a prepared decision package for every requested input.

---

## P25.1 — Metamodel V2 input package

### Agent prepares before asking

- [ ] Current V1 structural summary.
- [ ] Runtime concepts actually needed after Phases 16–24.
- [ ] Classes/attributes/references that appear unused or over-complex.
- [ ] Proposed minimum verification-oriented vocabulary if useful.
- [ ] Exact V1→future-V2 impact matrix.
- [ ] Migration tooling ready.

### Human input

- [ ] User supplies/approves Metamodel V2 if it is externally authored/required.
- [ ] User states whether V2 replaces V1 canonical baseline or acts as a verification-specific metamodel/profile.

If user decides no V2 is required:

- [ ] record V1 as final target and proceed to Phase 26 accordingly.

---

## P25.2 — Remaining research-semantic decisions

Only ask for entries that could not be resolved by source/runtime evidence.

For each open decision provide:

- [ ] question;
- [ ] current facts;
- [ ] safe default;
- [ ] options;
- [ ] impact;
- [ ] recommendation;
- [ ] exact files/tests affected.

Potential topics only if still unresolved:

- [ ] intentionally LOSSY translation enablement;
- [ ] interpretation of a normative/deontic semantic not exposed by runtime;
- [ ] thesis-specific desired cross-dimensional rule not present in source;
- [ ] alternative RuntimeTrace/OCL representation if current evidence-backed implementation is unacceptable.

---

## P25.3 — Case Study #2 user input if still required

Only if P24.5 found no suitable autonomous candidate:

- [ ] present required selection criteria;
- [ ] present any partial candidates already evaluated;
- [ ] request project/case selection from user.

---

## P25.4 — Human input freeze

- [ ] Record all user decisions in a decision log.
- [ ] Record date/version/source.
- [ ] Convert decisions into explicit Phase 26 inputs.
- [ ] No remaining hidden human dependency before Phase 26 starts.

### Exit criteria

- [ ] Metamodel target decided.
- [ ] Required external case supplied if needed.
- [ ] Any thesis-specific semantics explicitly approved/rejected.

---

# Phase 26 — Metamodel V2 Reconciliation & Final Runtime Mapping

**Objective:** reconcile the final metamodel decision, regenerate static artifacts, migrate runtime binding, and freeze the final Runtime Mapping contract.

---

## P26.1 — Import and audit final metamodel target

### Read first

- User-supplied/final Ecore only.
- P21 diff/migration tooling.
- `docs/project/04-jacamo-metamodel-baseline.md` for evolution policy.

### Tasks

- [ ] Parse final Ecore.
- [ ] Enumerate EClasses/EAttributes/EReferences/inheritance.
- [ ] Compute fingerprint/hash.
- [ ] Diff against V1.
- [ ] Classify added/removed/changed.
- [ ] Detect invalid/unresolved structural facts.
- [ ] Record provenance: reconstructed baseline vs thesis verification extension.
- [ ] Do not silently infer rename by fuzzy match.

---

## P26.2 — Build/finalize Structural Mapping for final metamodel

### Read first

- Final Ecore diff.
- Current Structural Mapping contract/schema.
- Existing mapping engine tests.

### Tasks

- [ ] Create Mapping V2 or reconcile V1 depending on Phase 25 decision.
- [ ] Map all final classes.
- [ ] Map attributes.
- [ ] Map references/containment/multiplicity.
- [ ] Map inheritance.
- [ ] Reconcile projection contracts.
- [ ] Ensure deterministic target names.
- [ ] Schema validate.
- [ ] Coverage audit.
- [ ] Negative mutation tests.
- [ ] Do not freeze until USE compiler gate passes.

---

## P26.3 — Reconcile Semantic IR / parser outputs

### Read first

- Final Ecore/Mapping diff impact report.
- Only parser code for affected kinds.

### Tasks

- [ ] Update semantic kind registry/enum.
- [ ] Preserve stable IDs where semantics remain the same.
- [ ] Remove obsolete kinds only after references/tests are migrated.
- [ ] Update parsers/extractors only for affected vocabulary.
- [ ] Preserve unsupported source facts as diagnostics rather than dropping silently.
- [ ] Update resolver target kinds.
- [ ] Update trace schema only if required.

### Tests

- [ ] parser fixtures for affected dimensions;
- [ ] Auction import;
- [ ] Case Study #2 import.

---

## P26.4 — Regenerate USE structural model and initial state

### Read first

- Final Mapping.
- USE transformation tests.

### Tasks

- [ ] Generate final `.use`.
- [ ] Generate final `.cmd`.
- [ ] Build direct `MModel`.
- [ ] Build direct `MSystemState`.
- [ ] Compare text/direct semantics.
- [ ] Regenerate trace.
- [ ] Revalidate multiplicity/structure.
- [ ] Regenerate golden outputs intentionally.
- [ ] Review diffs rather than blindly accepting snapshots.

---

## P26.5 — Reconcile runtime mapping target-binding layer

### Read first

- **Required research folder:**
  - `02_RUNTIME_CAPABILITY_MATRIX.md`
  - `03_RUNTIME_IDENTITY_MODEL.md`
  - `04_USE_MAPPING_CANDIDATES.md`
- Runtime Mapping Draft.
- Final Ecore/Mapping/Trace target registry.
- P21 metamodel-decoupling artifacts.

### Tasks

- [ ] Keep source RuntimeEvent selectors unchanged where upstream runtime semantics did not change.
- [ ] Keep Generic Runtime Semantic Actions unchanged where possible.
- [ ] Rebind each rule to final semantic/USE target kind.
- [ ] Remove obsolete V1 anchors.
- [ ] Add required final-metamo del anchors.
- [ ] Revalidate exact trace requirements.
- [ ] Revalidate property/operation projections.
- [ ] Revalidate organisation target semantics.
- [ ] Reclassify deferred rules that V2 now enables.
- [ ] Keep still-unproven semantics unsupported.
- [ ] Produce migration report showing which runtime rules changed only target binding vs source semantics.

---

## P26.6 — Promote Runtime Mapping Draft to final version

### Read first

- Final reconciled mapping document.
- RuntimeMappingValidator.
- Final structural target registry.

### Tasks

- [ ] Rename/version according to repository convention, e.g. `jacamo-use-runtime-mapping-v1.json` if this is the first canonical runtime mapping.
- [ ] Remove `DRAFT_WAITING_FOR_METAMODEL_V2` status.
- [ ] Validate schema.
- [ ] Validate semantic rules.
- [ ] Validate structural compatibility.
- [ ] Run negative mutation tests.
- [ ] Run runtime integration tests.
- [ ] Create runtime mapping audit document.
- [ ] Create freeze/version/hash manifest if canonical project conventions require it.
- [ ] Ensure Java semantic dispatch has no conflicting source of truth.

### Freeze rule

Do **not** update only hashes to make tests pass. Any structural/rule change requires reconciliation and review evidence.

---

## P26.7 — Reconcile OCL and verification profiles

### Read first

- Final generated model diff.
- Only OCL/profile files whose context/navigation target changed.
- Constraint provenance manifests.

### Tasks

- [ ] Rebind translated OCL.
- [ ] Rebind core OCL.
- [ ] Rebind case OCL.
- [ ] Remove navigation to deleted classes/features.
- [ ] Preserve authored vs translated provenance.
- [ ] Compile/type-check all OCL.
- [ ] Update positive/negative fixtures.

---

## P26.8 — Final mirror correctness after metamodel reconciliation

### Read first

- Final Runtime Mapping.
- P19 Mirror Correctness Gate test harness.

### Tasks

- [ ] Initial full sync.
- [ ] state deltas;
- [ ] operation lifecycle;
- [ ] organisation state;
- [ ] reconnect/resync;
- [ ] forced drift repair;
- [ ] unknown/unbound boundary;
- [ ] zero unexplained drift after resync.

### Exit criteria

- [ ] Final metamodel and Structural Mapping coherent.
- [ ] Final Runtime Mapping frozen/canonical.
- [ ] `.use/.cmd` regenerated.
- [ ] Runtime mirror correct.
- [ ] OCL compiles against final model.

---

# Phase 27 — Final Engineering Hardening

**Objective:** remove remaining correctness/coding gaps; introduce no new research feature.

---

## P27.1 — Requirement → code → test → evidence traceability audit

### Read first

- `docs/project/19-roadmap.md` Phase 16–28.
- Current `task.md` statuses.
- `docs/project/17-end-to-end-acceptance.md`.

### Tasks

For every major capability classify:

- [ ] COMPLETE;
- [ ] SUPPORTED_SUBSET_COMPLETE;
- [ ] EXPLICITLY_UNSUPPORTED;
- [ ] OUT_OF_SCOPE;
- [ ] MISSING;
- [ ] CONFLICT.

For COMPLETE capabilities record:

- [ ] requirement;
- [ ] production component;
- [ ] test;
- [ ] evidence;
- [ ] documentation.

Any MISSING/CONFLICT becomes a blocking corrective task.

---

## P27.2 — TODO/FIXME/temporary/unsupported audit

### Read first

- Production source only; search first, open targeted files second.

### Search

- [ ] TODO;
- [ ] FIXME;
- [ ] HACK;
- [ ] TEMP;
- [ ] workaround;
- [ ] placeholder;
- [ ] not implemented;
- [ ] UnsupportedOperationException;
- [ ] stale “temporary V1” comments.

### Tasks

- [ ] Resolve correctness-relevant items.
- [ ] Convert legitimate unsupported items into documented/tested boundaries.
- [ ] Remove stale comments.

---

## P27.3 — Duplicate/dead logic audit

### Read first

- Search results only.

### Tasks

- [ ] duplicate runtime mapper;
- [ ] old procedural dispatch bypassing canonical Runtime Mapping;
- [ ] duplicate trace/history systems;
- [ ] obsolete verifier path;
- [ ] obsolete V1-only adapter after V2 migration;
- [ ] test-only production behavior;
- [ ] dead binding code.

Remove only with test evidence.

---

## P27.4 — Diagnostic completeness audit

### Read first

- Diagnostics enum/model.
- Failure branches found by targeted search.

### Tasks

Major failures must carry:

- [ ] stable code;
- [ ] severity;
- [ ] phase;
- [ ] source/semantic/runtime context when available;
- [ ] actionable message;
- [ ] evidence/cause where safe.

No silent catch/fallback.

---

## P27.5 — Determinism audit

### Tasks

Verify repeated-run stability for:

- [ ] semantic IDs;
- [ ] generated USE names;
- [ ] structural mapping selection;
- [ ] runtime mapping selection;
- [ ] trace ordering;
- [ ] event ordering;
- [ ] diagnostic ordering;
- [ ] report ordering;
- [ ] `.use/.cmd` output;
- [ ] non-runtime manifest hashes where deterministic.

Runtime UUID/timestamp evidence must be compared semantically, not by impossible byte equality.

---

## P27.6 — Runtime lifecycle/resource audit

### Read first

- RuntimeMirrorService/connectors/queue/executors/polling schedulers.
- Lifecycle tests.

### Tasks

- [ ] subscribe/unsubscribe;
- [ ] duplicate listener prevention;
- [ ] queue drain/shutdown;
- [ ] thread/executor cleanup;
- [ ] Moise polling scheduler cleanup;
- [ ] late callbacks;
- [ ] reconnect;
- [ ] resync;
- [ ] workspace replacement;
- [ ] profile replacement;
- [ ] failed synchronization;
- [ ] ERROR recovery policy.

---

## P27.7 — Security audit

### Read first

- `docs/project/18-risk-register.md`.
- Existing security/path tests.

### Tasks

- [ ] path traversal;
- [ ] symlink escape;
- [ ] archive handling;
- [ ] classpath handling;
- [ ] static Java class initialization protection;
- [ ] XML parser entity/DTD safety;
- [ ] OCL profile path safety;
- [ ] export path safety;
- [ ] no arbitrary command execution from imported project.

---

## P27.8 — Full runtime regression matrix

### Run

- [ ] RuntimeEvent/schema.
- [ ] RuntimeTrace.
- [ ] runtime identity.
- [ ] Runtime Mapping schema/validator.
- [ ] mapping→mutation integration.
- [ ] synthetic connector.
- [ ] Jason live connector.
- [ ] CArtAgO live connector.
- [ ] Moise live connector.
- [ ] snapshot/full sync.
- [ ] ordering.
- [ ] drift/resync.
- [ ] reconnect.
- [ ] workspace replacement.
- [ ] PRE/POST.
- [ ] cross-dimensional rules.
- [ ] normative supported subset.
- [ ] violation attribution.

---

## P27.9 — Multi-case E2E

### Tasks

Run both:

- [ ] Auction;
- [ ] Case Study #2.

Each must demonstrate where applicable:

- [ ] import;
- [ ] `.use/.cmd`;
- [ ] exact trace;
- [ ] initial state;
- [ ] runtime sync;
- [ ] Runtime Mapping;
- [ ] positive verification;
- [ ] negative verification;
- [ ] reconnect/resync;
- [ ] no case-specific core branch.

---

## P27.10 — Performance evidence

### Tasks

Measure without inventing SLA:

- [ ] import duration;
- [ ] transformation/generation;
- [ ] full verification;
- [ ] runtime event→mirror latency;
- [ ] runtime event→verification result latency;
- [ ] queue depth/high-water mark;
- [ ] memory sample where practical.

Optimize only reproducible blocking bottlenecks.

---

## P27.11 — Clean checkout / relocated build

### Tasks

- [ ] clean checkout;
- [ ] no stale build outputs;
- [ ] same pinned JDK/Maven/dependencies;
- [ ] relocated filesystem path if supported;
- [ ] `mvn clean verify` or repository canonical full command;
- [ ] record exact test counts/results;
- [ ] verify worktree cleanliness after build where expected.

---

## P27.12 — Package/plugin load gate

### Read first

- `docs/project/15-build-release-operations.md`.
- Release integration tests.

### Tasks

- [ ] JAR/ZIP inventory;
- [ ] canonical Ecore/Structural Mapping resources;
- [ ] final Runtime Mapping resources;
- [ ] schemas/manifests;
- [ ] OCL resources;
- [ ] compatibility metadata;
- [ ] license/notice;
- [ ] checksum;
- [ ] isolated USE plugin load;
- [ ] no test-classpath dependency.

---

## P27.13 — Full documentation synchronization audit

### Read first

Do not read everything at once. First search for stale terms/versions/status, then open only affected docs.

### Search at minimum

- [ ] old runtime mapping “draft” wording if mapping is now frozen;
- [ ] old metamodel V1 counts if V2 is final;
- [ ] stale dependency versions;
- [ ] old Auction operation names;
- [ ] stale “not implemented” statements;
- [ ] unsupported claims that are now implemented;
- [ ] supported claims that are too broad;
- [ ] old test counts;
- [ ] old known limitations;
- [ ] obsolete release status.

### Review affected docs

Potentially:

- [ ] README;
- [ ] architecture;
- [ ] metamodel baseline;
- [ ] structural mapping contract;
- [ ] semantic/extraction;
- [ ] OCL/constraint translation;
- [ ] USE transformation;
- [ ] trace/binding;
- [ ] runtime adapter;
- [ ] runtime mapping;
- [ ] verification engine;
- [ ] UI workflow;
- [ ] testing strategy;
- [ ] case studies;
- [ ] build/release;
- [ ] research boundaries;
- [ ] acceptance;
- [ ] risk register;
- [ ] known limitations;
- [ ] compatibility;
- [ ] agent/task docs.

### Exit criteria

- [ ] No known in-scope correctness/coding gap remains.

---

# Phase 28 — Final Evidence & Project Closure

**Objective:** produce the final evidence bundle and move engineering status to logic/coding complete only when every in-scope capability has an explicit final status.

---

## P28.1 — Final acceptance matrix

### Read first

- P27.1 traceability audit.
- Current acceptance criteria.
- Known limitations.

### Required capability rows

- [ ] metamodel baseline;
- [ ] Structural Mapping;
- [ ] static import;
- [ ] Semantic IR;
- [ ] `.use` generation;
- [ ] `.cmd`/initial state;
- [ ] trace/binding;
- [ ] RuntimeEvent;
- [ ] RuntimeTrace;
- [ ] runtime identity;
- [ ] Runtime Mapping;
- [ ] mirror synchronization;
- [ ] full runtime E2E;
- [ ] OCL/runtime verification;
- [ ] PRE/POST;
- [ ] ordering/history verification;
- [ ] cross-dimensional verification;
- [ ] normative supported subset;
- [ ] constraint translation subset;
- [ ] violation reporting/navigation;
- [ ] UI workflow;
- [ ] packaging;
- [ ] reproducibility;
- [ ] Case Study #1 Auction;
- [ ] Case Study #2.

Allowed final statuses only:

- [ ] `COMPLETE`.
- [ ] `SUPPORTED_SUBSET_COMPLETE`.
- [ ] `EXPLICITLY_UNSUPPORTED`.
- [ ] `OUT_OF_SCOPE`.

No blank/ambiguous status.

---

## P28.2 — Final reproducible evidence bundle

### Tasks

Preserve/generate:

- [ ] final source/project revision;
- [ ] final Ecore + hash;
- [ ] final Structural Mapping + schema + audit/hash;
- [ ] final Runtime Mapping + schema + audit/hash;
- [ ] generated `.use`;
- [ ] generated `.cmd`;
- [ ] OCL profiles + provenance;
- [ ] trace JSON;
- [ ] runtime event log;
- [ ] mirror-correctness summary;
- [ ] verification results/reports;
- [ ] reconnect/resync evidence;
- [ ] multi-case scenario summaries;
- [ ] compatibility manifest;
- [ ] test results;
- [ ] release package/checksum.

---

## P28.3 — Final supported/unsupported boundary report

### Tasks

For every unsupported/subset capability:

- [ ] what is unsupported;
- [ ] why;
- [ ] upstream/API evidence;
- [ ] user-visible behavior;
- [ ] diagnostic behavior;
- [ ] test protecting the boundary;
- [ ] whether future work could enable it.

No vague “future work” without current boundary.

---

## P28.4 — Final test/release verification

### Tasks

- [ ] focused mapping tests;
- [ ] focused runtime tests;
- [ ] Auction E2E;
- [ ] Case Study #2 E2E;
- [ ] full module verify;
- [ ] full reactor verify;
- [ ] clean-checkout verify;
- [ ] installed plugin smoke;
- [ ] package inventory/hash verification;
- [ ] zero unexpected skipped correctness tests.

Record exact commands and counts.

---

## P28.5 — Final documentation status

### Tasks

- [ ] `roadmap.md` reflects completed phases without pretending unsupported features are implemented.
- [ ] `task.md` checkboxes/evidence synchronized.
- [ ] README describes final user workflow.
- [ ] architecture reflects final metamodel/runtime mapping.
- [ ] known limitations final.
- [ ] compatibility final.
- [ ] thesis evidence paths final.
- [ ] no document uses historical test counts as current evidence without labeling them historical.

---

## P28.6 — Final user acceptance

Agent presents only after all autonomous work is complete:

- [ ] final acceptance matrix;
- [ ] final test results;
- [ ] final runtime mapping status;
- [ ] final metamodel/mapping status;
- [ ] mirror-correctness result;
- [ ] multi-case result;
- [ ] unsupported boundaries;
- [ ] package/evidence locations;
- [ ] remaining non-engineering thesis/demo work.

The user only needs to confirm final project acceptance.

### Final exit condition

The project may be marked:

`CORE LOGIC / CODING COMPLETE`

only when:

- [ ] every in-scope capability is implemented/tested/traceable **or** explicitly unsupported/out-of-scope;
- [ ] no hidden “partially working” state remains;
- [ ] full regression passes;
- [ ] final documentation/evidence is synchronized;
- [ ] user confirms final acceptance.

---

# Final Global Checklist

## Runtime foundation

- [ ] JaCaMo runtime research integrated.
- [ ] Runtime capability matrix reconciled to pinned implementation.
- [ ] Runtime authority policy defined.
- [ ] RuntimeEvent canonical.
- [ ] RuntimeTrace canonical.
- [ ] Runtime identities exact.

## Runtime Mapping

- [ ] Generic semantic-action vocabulary defined.
- [ ] Runtime Mapping schema implemented.
- [ ] Runtime Mapping Draft implemented.
- [ ] Loader/validator implemented.
- [ ] V1 compatibility audited.
- [ ] Mapping integrated into RuntimeMutationEngine.
- [ ] Duplicate Java semantic dispatch removed/isolated.
- [ ] Final Runtime Mapping reconciled to final metamodel.
- [ ] Final Runtime Mapping frozen/audited.

## Mirror correctness

- [ ] Attribute synchronization exact.
- [ ] Operation lifecycle exact.
- [ ] Object/relation lifecycle safe.
- [ ] Organisation synchronization exact for supported subset.
- [ ] Jason state/correlation boundaries explicit.
- [ ] No double application.
- [ ] No silent drop.
- [ ] Authoritative drift detection works.
- [ ] Reconnect/resync repairs drift.
- [ ] Mirror-correctness gate PASS.

## Full runtime

- [ ] Closest feasible real JaCaMo `.jcm` launch path tested.
- [ ] Actual runtime trace captured.
- [ ] Capability/mapping assumptions reconciled with actual trace.
- [ ] Full runtime → USE mirror E2E evidence exists.

## Metamodel evolution

- [ ] Runtime core decoupled from unnecessary V1 vocabulary.
- [ ] Metamodel diff/migration tooling exists.
- [ ] Human Metamodel V2 input handled only in Phase 25.
- [ ] Final metamodel audited.
- [ ] Final Structural Mapping built/frozen.
- [ ] `.use/.cmd` regenerated.
- [ ] Runtime target bindings migrated.

## Verification

- [ ] Mirror correctness established before OCL expansion.
- [ ] Verification checkpoints defined.
- [ ] Runtime invariants work.
- [ ] PRE/POST work.
- [ ] Ordering/history verification bounded and implemented.
- [ ] Exact violation navigation works.
- [ ] Cross-dimensional supported subset works.
- [ ] Normative supported subset bounded correctly.
- [ ] Constraint translation subset closed.

## Genericity

- [ ] No Auction-specific core logic.
- [ ] Auction passes.
- [ ] Case Study #2 passes or explicit final scope decision recorded.
- [ ] Generic reuse audit passes.

## Engineering closure

- [ ] TODO/FIXME/correctness audit clean.
- [ ] Duplicate/dead logic audit clean.
- [ ] Diagnostics complete.
- [ ] Determinism verified.
- [ ] Runtime resources/lifecycle clean.
- [ ] Security regression pass.
- [ ] Performance evidence recorded.
- [ ] Clean checkout/relocated build pass.
- [ ] Package/plugin load pass.
- [ ] Documentation synchronized.
- [ ] Final acceptance matrix complete.
- [ ] Final evidence bundle complete.
- [ ] Final user acceptance received.
