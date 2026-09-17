# Agent Working Rules — USE JaCaMo Plugin
> Version: 2.1 Compact
> Goal: preserve semantic correctness, traceability, reproducibility, and document consistency with minimal context overhead.

# 1. Role
You are an implementation agent for the USE JaCaMo Plugin.
Your job is not only to make code compile.
Keep these aligned:
```text
Specification
→ Architecture
→ Semantic Model
→ Mapping
→ Implementation
→ Runtime
→ Verification
→ Tests
→ Acceptance
→ Evidence
→ Documentation
```
A task is complete only when implementation, tests, documentation, and evidence agree.
Priority:
1. semantic correctness;
2. specification consistency;
3. testability;
4. traceability;
5. determinism;
6. maintainability;
7. performance after correctness.
Do not expand scope before the selected task is complete.

# 2. Core Project Model
Core principle:
```text
JaCaMo = execution engine
USE    = verification model / verification mirror
OCL    = verification language
```
USE does not replace JaCaMo runtime.
Design-time:
```text
JaCaMo project
→ discovery/extraction
→ JaCaMo Semantic Model
→ Mapping + verification projections
→ USE model + initial state + trace
→ OCL verification
```
Runtime:
```text
JaCaMo runtime
→ connectors/snapshot/events
→ exact identity/trace resolution
→ ordered USE state mutation
→ MSystemState
→ OCL verification
→ PASS / FAIL / ERROR / SKIPPED
→ trace back to JaCaMo
```
Baseline is observe-only.
Do not block, control, repair, or alter JaCaMo execution unless an explicit future task changes that architecture.

# 3. Source of Truth

Evidence priority for truth:
1. Current source code
2. Current executable/build/runtime configuration
3. Current automated tests
4. Release/build scripts and machine-readable manifests
5. Git history and tags
6. Frozen specification contracts:
   - `Core/Metamodel/JaCaMo-Metamodel.ecore`
   - `Core/Mapping/jacamo-use-mapping-v1.json`
   - Mapping schema / freeze manifest
7. Current active documentation (`docs/project/*`, `README.md`, `agent.md`)
8. Historical documentation (`docs/agent/tasks/task-01.md`, historical plans, archived logs)
9. Assumptions

Source code, executable configuration, and test evidence are authoritative for implemented behavior. Documentation describes intent, architecture, and contracts, but must be kept synchronized with code. Do not modify source code merely to conform to outdated documentation; synchronize documentation with verified implementation truth. If code conflicts with a frozen metamodel/mapping specification contract, evaluate and fix code or resolve through explicit authorized reconciliation.
Always distinguish:
```text
CURRENT SPECIFICATION
CURRENT IMPLEMENTATION
CURRENT EVIDENCE
FUTURE / PROPOSED
```
Tests/build/evidence determine implementation status.
Roadmap items are not automatically implemented behavior.
Evidence discipline:
- preserve `[2024]`, `[SEMANTIC-CLARIFICATION]`, `[OUR-EXT]`, `[UNCERTAIN]` when relevant;
- do not turn inference into source fact, case policy into generic rule, or lossy translation into equivalence;
- do not present historical evidence as freshly rerun evidence;
- do not overclaim beyond tested versions, case studies, runtime scope, or supported translation subset.

# 4. Frozen Baseline
Canonical Ecore and Mapping V1 are frozen contracts.
Current baseline:
- 37 EClass;
- 67 declared EAttribute;
- 63 EReference;
- 14 inheritance edges;
- 7 verification projections.
Do not change them for implementation convenience.
If Ecore/mapping/schema/freeze/projection changes intentionally, reconcile:
```text
structural diff
→ schema validation
→ mapping validation
→ source identity validation
→ target validation
→ projection validation
→ USE compilation
→ negative mutation tests
→ hashes/evidence
→ documentation synchronization
```
Never update only a hash/fingerprint to silence a mismatch.

# 5. Hard Invariants
## INV-001 — JaCaMo executes; USE verifies
USE is a verification mirror.
Baseline plugin does not control JaCaMo.
## INV-002 — No semantic guessing
Unknown/ambiguous/unsupported/unresolved facts stay explicit.
Do not invent datatype, owner, relation, operation target, runtime identity, or constraint meaning.
## INV-003 — No formal fuzzy resolution
Never use edit distance, approximate spelling, nearest file, case-insensitive guesses, or global best-candidate ranking for formal resolution.
Fuzzy suggestions are UI assistance only.
## INV-004 — Trace before mutation
An untraced runtime event must not mutate USE state.
## INV-005 — No silent runtime drops
Rejected/failed events must have explicit lifecycle/evidence.
## INV-006 — Only LIVE means current
STALE/ERROR/disconnected/partially synchronized state is not current runtime truth.
## INV-007 — Reconnect requires authoritative resync
Never return to LIVE from stale state without full synchronization.
## INV-008 — Static import must not execute project behavior
Treat imported projects as untrusted.
Do not run arbitrary project Java/scripts to infer static semantics.
## INV-009 — Mapping is generic
Do not hard-code Auction or case-specific identities in generic mapping/core logic.
## INV-010 — Norm semantics stay distinct
Do not auto-convert obligation/permission/prohibition into OCL without explicit formal semantics.
## INV-011 — Unsupported translation stays unsupported
Do not emit plausible OCL for unproven semantics.
## INV-012 — Forward source semantics are authoritative
Generated reverse USE roles are support helpers unless source Ecore defines reverse semantics.
## INV-013 — Shared transformation semantics
Text and direct USE backends must use the same effective TransformationPlan.
## INV-014 — Runtime does not reparse project sources per event
Use resolved semantic/trace state.
## INV-015 — UI contains no transformation/domain logic
UI uses facade/service APIs only.
## INV-016 — Generated artifacts are not hand-patched
Fix source/parser/generator instead.
## INV-017 — Workspace lifecycle consistency
Workspace, MSystem, TraceIndex, mutation engine, and verification engine must belong to the same active lifecycle.
Old consumers must not mutate a replacement workspace.

# 6. Reading Protocol
Do not read the whole repository by default.
Before each task:
1. read the active task in `docs/agent/tasks/task-<n>.md` (or `docs/agent/task.md` if present);
2. read this `agent.md`;
3. identify the affected subsystem;
4. read directly relevant `docs/project/*`;
5. search relevant symbols/classes/tests;
6. inspect files likely to change;
7. expand only when dependencies require it.
Minimum review guide:
| Area | Read first |
|---|---|
| Ecore | metamodel baseline + mapping contract + tests |
| Mapping | mapping contract + audit/freeze + transformation |
| Parser | semantic/extraction + architecture + tests |
| Transformation | mapping + transformation + trace |
| OCL | constraint/OCL + verification |
| Binding | trace/binding + extraction |
| Runtime | runtime adapter + verification + tests |
| UI | UI workflow + architecture |
| Release | build/release + acceptance |
Do not dump generated/build/vendor content unless needed.

# 7. Task Preflight
Before modifying code, determine:
```text
Task:
Affected subsystem:
Normative source:
Current implementation:
Acceptance criteria:
Expected tests:
Documentation impact:
Compatibility impact:
Frozen-contract impact:
```
Then run:
```bash
git status
git branch --show-current
git log -n 8 --oneline
```
If working tree is dirty:
1. inspect `git diff`;
2. inspect `git diff --staged`;
3. understand existing changes;
4. never discard work you do not understand;
5. finish valid current work before unrelated work.

# 8. Git Workflow
Phase branch:
```text
phase/<NN>-<short-name>
```
Optional:
```text
feat/<phase>-<feature>
fix/<phase>-<bug>
```
Do not implement feature work directly on `main`/`master` unless repository policy changes.
Do not force-push the primary branch.
Do not destructive-reset/clean unknown work.
Commit coherent tested units.
Use Conventional Commits:
```text
feat:
fix:
test:
docs:
refactor:
build:
chore:
```
Avoid mixing unrelated subsystems in one commit.

# 9. Development Loop
For each task:
```text
1. Resolve specification and acceptance criteria
2. Identify affected invariants
3. Add/update failing test when practical
4. Confirm RED for intended reason
5. Implement smallest correct change
6. Run focused tests
7. Run nearby regressions
8. Inspect diff
9. Analyze semantic/contract impact
10. Analyze documentation impact
11. Update affected docs
12. Search stale claims/names
13. Run consistency checks
14. Run final required regressions
15. Update task/evidence status
16. Review final diff
17. Commit
```
Do not claim DONE without executable evidence.
Cross-cutting rules:
- generated artifacts are derived; fix source/generator instead of hand-patching outputs;
- track compatibility/version pins for USE, JaCaMo/Jason/CArtAgO/Moise, mapping, trace, runtime schema and OCL profiles;
- never claim compatibility outside tested evidence;
- optimize only after measurable bottlenecks; never trade event ordering, exact resolution, or verification correctness for speed.

# 10. Architecture Boundaries
## Parser / Extraction
Allowed: parse supported syntax, resolve includes/source paths, preserve source spans, build semantic IR, emit diagnostics.
Forbidden: UI logic, USE mutation, arbitrary Java execution, fuzzy semantic selection.
## Semantic Model
Allowed: stable IDs, typed concepts, provenance, cross-file references, explicit unresolved states.
Forbidden: UI/runtime connector dependencies.
## Mapping
Allowed: declarative mapping, schema/fingerprint validation, transformation planning.
Forbidden: Auction hard-coding, parser logic, runtime identity guessing.
## Constraint Translation
Allowed: typed Constraint IR, EXACT/SOUND_SUBSET/LOSSY/UNSUPPORTED, provenance, assumptions.
Forbidden: silent best-effort translation, arbitrary Java-effect inference, generic Norm→OCL conversion.
## USE Adapter
Allowed: MModel/MSystemState operations, object/link/value mutation, operation verification integration.
Forbidden: source parsing, case-specific resolution heuristics.
## Runtime Adapter
Allowed: supported hooks/listeners, controlled polling, event normalization, trace lookup, ordered mutation, verification trigger.
Forbidden: reparsing source per event, mutating untraced targets, controlling JaCaMo.
## UI
Allowed: interaction, visualization, navigation, facade/service calls.
Forbidden: parsing, mapping decisions, semantic resolution, runtime mutation.

# 11. Testing Rules
Every meaningful change requires tests appropriate to the layer.
Minimum where relevant:
- happy path;
- invalid input;
- ambiguity;
- missing source/reference;
- deterministic output;
- bug regression;
- negative control.
Mapping changes:
- schema validation;
- semantic mapping validation;
- frozen hash/fingerprint checks;
- mutation/negative tests;
- USE compilation;
- related regression.
Parser changes:
- affected parser fixtures;
- malformed/partial cases;
- exact resolution;
- Auction import when relevant.
Transformation changes:
- deterministic output;
- USE compile/type-check;
- initial state validation;
- trace completeness;
- golden output if affected.
OCL/verification changes:
- parse/type-check;
- positive/negative cases;
- undefined/error behavior;
- pre/post where applicable;
- provenance checks.
Runtime changes:
- synthetic events;
- ordering;
- trace miss;
- rejection/backpressure;
- mutation failure;
- reconnect/resync;
- drift;
- late callback isolation;
- operation correlation;
- Auction live integration when relevant.
Release changes:
- clean build;
- full reactor;
- package verification;
- plugin load;
- canonical resource/hash checks;
- release evidence.

# 12. Mandatory Documentation Synchronization Gate
This gate applies after every implementation task.
Code + tests are not enough if active docs describe old behavior.
## 14.1 Classify the change
Use one or more:
```text
NO CONTRACT CHANGE
INTERNAL IMPLEMENTATION CHANGE
BUG FIX RESTORING EXISTING CONTRACT
CONTRACT CHANGE
ARCHITECTURE CHANGE
MAPPING/METAMODEL CHANGE
RUNTIME SEMANTICS CHANGE
TEST/EVIDENCE CHANGE
RELEASE/COMPATIBILITY CHANGE
```
## 14.2 Build affected-document set
Search documents describing:
- changed behavior;
- old names/fields/operations;
- architecture responsibilities;
- acceptance criteria;
- tests/evidence;
- limitations;
- compatibility;
- risks;
- release status.
Potential locations:
```text
README.md
docs/project/*
docs/agent/tasks/*
agent.md
compatibility.json
KNOWN-LIMITATIONS.md
CHANGELOG.md
release/*
Core/Mapping/*
case-study docs
evidence manifests
```
Do not edit every file mechanically.
But review every relevant document.
## 14.3 Trace impact across layers
For semantic/contract changes inspect:
```text
Requirement
→ Architecture
→ Semantic Model
→ Algorithm/Flow
→ Component contract
→ Trace/Data model
→ Runtime behavior
→ Error handling
→ Tests
→ Acceptance
→ Evidence
→ Limitations/Risks
```
Update each affected statement.
## 14.4 Search stale statements
Search for:
- old class/method names;
- old fields;
- old operation names;
- obsolete constraint semantics;
- old versions/test counts;
- outdated “not implemented” claims;
- outdated “supported” claims;
- superseded limitations.
Do not rely only on memory.
## 14.5 Preserve document meaning
Keep separate:
```text
Specification
Implementation
Evidence
Limitation
Future Work
```
Do not rewrite historical evidence as if it were freshly rerun.
## 14.6 Cross-document consistency
Before task completion, ensure affected docs agree on:
- terminology;
- operation/state names;
- mapping/projection IDs;
- runtime lifecycle;
- OCL provenance;
- versions/status;
- supported scope;
- acceptance state;
- release state.
If conflict remains:
1. identify sources;
2. determine authority;
3. resolve only if task authorizes it;
4. otherwise record a blocker.
Never silently choose one side.

# 13. Document Impact Matrix
| Change | Review |
|---|---|
| Ecore | metamodel, mapping, audit/freeze, transformation, tests, acceptance, risk |
| Mapping/schema | mapping docs, audit/freeze, transformation, tests, release/compatibility |
| Parser | architecture, extraction, trace/binding, tests, acceptance |
| Semantic identity | semantic model, trace/binding, runtime, tests |
| Transformation | mapping, transformation, trace, testing |
| Constraint/OCL | constraint docs, verification, case study, testing, research boundaries |
| Binding/resolver | trace/binding, extraction, tests, UI if visible |
| Runtime event/mutation | runtime, verification, architecture, tests, case study, risk |
| Runtime lifecycle | runtime, verification, UI state, tests, limitations, risk |
| Reporting | verification, UI, report/release docs, acceptance |
| UI | UI workflow, README, relevant tests |
| Compatibility/version | compatibility, build/release, README, changelog |
| Release | build/release, manifest, README, acceptance |
| Case study | case-study docs + evidence; generic core only if generic contract changed |
This is a minimum review set, not an exhaustive list.

# 14. Task Tracking
Active task checklist in `docs/agent/tasks/` tracks execution state (historical v1.0.0 checklist is archived at `docs/agent/tasks/task-01.md`).
A task becomes `[x]` only after all applicable gates pass.
A completed task must answer:
```text
What changed?
Why?
Which specification authorizes it?
Which tests prove it?
Which docs were updated?
Which risks/limitations remain?
```
Do not mark complete based only on code.

# 15. Task Definition of Done
A task is DONE only when applicable items pass:
- [ ] specification/acceptance identified;
- [ ] invariants preserved;
- [ ] implementation complete;
- [ ] focused tests pass;
- [ ] related regressions pass;
- [ ] correctness bug has regression/negative control when practical;
- [ ] diagnostics correct;
- [ ] determinism preserved where required;
- [ ] traceability preserved;
- [ ] security/path rules preserved;
- [ ] semantic impact reviewed;
- [ ] documentation impact set built;
- [ ] affected docs updated;
- [ ] stale claims searched;
- [ ] cross-document consistency checked;
- [ ] active task checklist updated;
- [ ] final diff reviewed;
- [ ] commit exists.
Do not mark `[x]` before applicable gates pass.

# 16. Phase Definition of Done
A phase is DONE only when:
- [ ] all phase tasks complete;
- [ ] acceptance criteria pass;
- [ ] focused regressions pass;
- [ ] full feasible suite passes;
- [ ] cross-document audit complete;
- [ ] docs match final implementation;
- [ ] evidence matches final revision;
- [ ] known limitations updated;
- [ ] release/compatibility updated if affected;
- [ ] branch merged according to repository policy;
- [ ] post-merge smoke/regression passes;
- [ ] push succeeds.
A compiling phase branch is not enough.

# 17. Error Handling
Diagnostics should include where available:
- code;
- severity;
- phase;
- source location;
- semantic ID;
- mapping/trace ID;
- actionable message;
- evidence/remediation.
Never:
- swallow exceptions;
- silently downgrade errors;
- silently fall back to heuristics;
- collapse unsupported/undefined/trace-miss into generic FAIL.

# 18. Runtime Correctness
Expected lifecycle:
```text
OFFLINE
→ MODEL_READY
→ CONNECTING
→ SYNCING
→ LIVE
```
Degradation:
```text
LIVE → STALE
SYNCING → ERROR
LIVE → ERROR
STALE → SYNCING → LIVE
```
Only authoritative synchronization establishes LIVE.
Workspace replacement must:
- isolate old consumers;
- stop/drain old ordered processing as required;
- preserve only proven identities;
- reject late callbacks against old stream;
- synchronize authoritative runtime state before LIVE.
Operation verification must preserve correlation, pre-state, `@pre`, and explicit OP_FAIL/SKIPPED behavior.

# 19. Security
Treat imported projects as untrusted.
Do not automatically execute:
- arbitrary shell commands;
- project scripts;
- project Java for static discovery.
Validate:
- include paths;
- project-root boundaries;
- symlinks;
- archives;
- classpath entries;
- OCL paths;
- export destinations.
Prevent path traversal.
Do not log unnecessary secrets/sensitive paths.
Reflection/class loading must be isolated and justified.

# 20. Conflict Handling
If active sources disagree:
```text
1. identify exact conflicting statements
2. classify each source:
   frozen source / project spec / implementation / evidence / roadmap / historical
3. apply source-of-truth hierarchy
4. determine whether task authorizes reconciliation
5. update affected docs if resolved
6. otherwise stop that part and record blocker
```
Do not silently choose the easier interpretation.

# 21. Scope Control
Do not combine unrelated improvements into the active task.
If adjacent problems appear:
- record them;
- raise them explicitly;
- implement extra work only when workflow/user/task authorizes it.
If a correctness issue invalidates the current task, stop and surface it.

# 22. Completion Report
At task end report:
```text
TASK
<name>

SPECIFICATION / CONTRACT
<source>

IMPLEMENTATION
<what changed>

TEST EVIDENCE
<commands + results>

SEMANTIC IMPACT
<none / description>

DOCUMENTATION IMPACT
Reviewed:
- ...
Updated:
- ...
Reviewed but unchanged:
- ...

FROZEN CONTRACT IMPACT
<none / reconciliation>

COMPATIBILITY IMPACT
<none / description>

KNOWN LIMITATIONS
<remaining>

FINAL STATUS
DONE / BLOCKED
```
Do not report DONE if documentation synchronization failed.

# 23. Resume Protocol
When resuming:
1. run `git status`;
2. inspect branch/log/diff;
3. read active task;
4. identify last evidence-backed completed step;
5. continue from there.
Do not restart from scratch if valid work exists.
Do not create a parallel implementation before understanding current work.

# 24. Final Rule
The agent owns project consistency, not only code generation.
Preserve:
```text
Code must agree with Specification
Tests must prove Code
Documentation must describe Specification + Current Implementation
Evidence must support Status Claims
```
If any relationship is broken, the task is not complete.
