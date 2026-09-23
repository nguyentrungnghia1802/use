# Agent Working Rules — USE JaCaMo Plugin
> Version: 3.0 — V2 Working Baseline
> Goal: preserve semantic correctness, traceability, reproducibility, controlled Metamodel/Mapping V2 evolution, and document consistency with minimal context overhead.
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

The project is now migrating to **Metamodel V2 + Mapping V2**. V2 is the active working semantic baseline even though small changes are still expected. V1 remains historical evidence only unless an explicit migration/audit task requires it.

## 3.1 Semantic authority

For semantic/metamodel/mapping questions, use this priority:

1. `Core/Metamodel/version-2/**` — active Metamodel V2 artifacts;
2. `Core/Mapping/version-2/**` — active Mapping V2 artifacts, schemas, manifests, audits, and related metadata;
3. explicit current V2 migration/decision records in `docs/project/**` and the active `docs/agent/task.md`;
4. current implementation that consumes the active V2 baseline;
5. current tests/evidence proving that implementation;
6. historical V1 artifacts and V1 freeze evidence;
7. assumptions.

If V2 Ecore and V2 Mapping disagree, do not guess which one is correct. Record the exact mismatch and reconcile only with evidence or an explicitly authorized task.

## 3.2 Implementation-status authority

For claims about what the plugin **currently does**, use:

1. current source code;
2. current executable/build/runtime configuration;
3. current automated tests;
4. current release/build scripts and machine-readable manifests;
5. Git history/tags;
6. active documentation;
7. historical documentation.

Source code, executable configuration, and tests determine implemented behavior. The active V2 semantic contracts determine what that implementation is supposed to represent. Do not change V2 semantics merely to preserve obsolete V1 implementation behavior.

Always distinguish:

```text
CURRENT V2 SPECIFICATION / WORKING BASELINE
CURRENT IMPLEMENTATION
CURRENT EVIDENCE
HISTORICAL V1 BASELINE
FUTURE / PROPOSED
```

Tests/build/evidence determine implementation status. Roadmap items are not automatically implemented behavior.

Evidence discipline:

- preserve `[2024]`, `[SEMANTIC-CLARIFICATION]`, `[OUR-EXT]`, `[UNCERTAIN]` when relevant;
- do not turn inference into source fact, case policy into generic rule, or lossy translation into equivalence;
- do not present historical evidence as freshly rerun evidence;
- do not overclaim beyond tested versions, case studies, runtime scope, or supported translation subset;
- explicitly label V1 evidence as historical when V2 is the active baseline.

# 4. Active V2 Working Baseline and Historical V1

## 4.1 Active baseline

The active development baseline is located under:

```text
Core/Metamodel/version-2/**
Core/Mapping/version-2/**
```

Treat these V2 artifacts as the default semantic source for all work from Phase 29 onward.

V2 is currently a **WORKING_BASELINE**, not a final frozen release. Small evidence-backed changes are allowed through the controlled evolution process in this file and `docs/agent/task.md`. Do not wait for a hypothetical 100% final metamodel before progressing with implementation.

Do not hard-code V2 class/feature counts into production logic. Inventory and coverage checks should derive the active structure from the V2 Ecore/Mapping. Counts may be pinned only in explicit evidence/freeze tests when intentionally approved.

## 4.2 Historical V1

The former V1 baseline (37 EClass, 67 declared EAttribute, 63 EReference, 14 inheritance edges, 7 verification projections) remains preserved for:

- historical evidence;
- V1→V2 diff/migration analysis;
- reproducibility of earlier releases;
- regression reference where explicitly useful.

V1 is **not** the active production semantic contract. Do not preserve V1 compatibility by adding permanent dual semantics unless an explicit task requires it. Do not modify V2 to imitate obsolete V1 structure.

## 4.3 Controlled V2 evolution

For every intentional V2 metamodel/mapping change:

```text
V2 structural diff
→ classify added/removed/changed semantics
→ mapping reconciliation
→ source/target identity validation
→ projection/target-binding impact analysis
→ Semantic IR/parser impact analysis
→ USE transformation + compile/type-check
→ trace/binding impact analysis
→ OCL context/navigation impact analysis
→ runtime target-binding impact analysis
→ focused + regression tests
→ hashes/version/evidence update
→ documentation synchronization
```

Never update only a hash/fingerprint or golden output to silence a mismatch.

Final V2 freeze occurs only when an explicit release/freeze task authorizes it. Until then, hashes and version identifiers provide reproducibility for each working baseline revision; they do not prohibit legitimate V2 evolution.

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

## INV-018 — V2 is the active semantic baseline

From Phase 29 onward, default semantic work targets `Core/Metamodel/version-2/**` and `Core/Mapping/version-2/**`.

V1 is historical unless an explicit migration/audit task requires it.

## INV-019 — Keep metamodel dependency behind boundaries

Runtime connectors, normalized `RuntimeEvent`, ordered queue/lifecycle, and generic runtime semantic actions must not depend directly on V2 class/feature names when that dependency can be isolated in semantic/mapping/target-binding adapters.

A small V2 change must not force a rewrite of Jason/CArtAgO/Moise connector mechanics.

## INV-020 — V2 evolution is diff-driven, not patch-driven

When V2 changes, compute and classify the exact diff first. Update only affected semantic/mapping/transformation/trace/OCL/runtime-binding layers and their tests.

Do not make scattered compatibility patches before impact is understood.

# 6. Reading Protocol
Do not read the whole repository by default.

Before each task:

1. read the active `docs/agent/task.md` (Phase 29+); read archived/historical task files only when the active task requires provenance or migration evidence;

2. read this `agent.md`;

3. identify the affected subsystem;

4. read directly relevant `docs/project/\*`;

5. search relevant symbols/classes/tests;

6. inspect files likely to change;

7. expand only when dependencies require it.

Minimum review guide:

| Area | Read first |

|---|---|

| Ecore | active V2 metamodel + V2 mapping + V2 evolution/impact docs + tests |

| Mapping | active V2 mapping + schema/audit/status + transformation + target-binding tests |

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

Active-baseline / contract impact:

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

Mapping / Metamodel V2 changes:

- parse/inventory the active V2 Ecore;

- schema validation;

- exact V2 structural diff and impact classification when baseline bytes changed;

- semantic mapping + source/target identity validation;

- projection and runtime target-binding compatibility checks;

- active V2 version/hash consistency; frozen hash checks only when the active V2 release is intentionally frozen;

- mutation/negative tests;

- USE compilation/type-check;

- affected OCL compile/type-check;

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

## 12.1 Classify the change
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

## 12.2 Build affected-document set
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

docs/project/\*

docs/agent/tasks/\*

agent.md

compatibility.json

KNOWN-LIMITATIONS.md

CHANGELOG.md

release/\*

Core/Mapping/\*

case-study docs

evidence manifests

```

Do not edit every file mechanically.

But review every relevant document.

## 12.3 Trace impact across layers
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

## 12.4 Search stale statements
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

## 12.5 Preserve document meaning
Keep separate:

```text

Specification

Implementation

Evidence

Limitation

Future Work

```

Do not rewrite historical evidence as if it were freshly rerun.

## 12.6 Cross-document consistency
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

| Metamodel V2 | V2 Ecore, Mapping V2, diff/impact evidence, semantic IR/parser, transformation, trace/binding, OCL, runtime target binding, tests, acceptance, risk |

| Mapping V2/schema | mapping docs, status/version/hash evidence, transformation, target binding, tests, release/compatibility |

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
The active execution checklist is `docs/agent/task.md`, beginning with Phase 29 for the V2 migration/evolution program. Older task files/checklists are historical evidence and must not override the active Phase 29+ plan.

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

- [ ] semantic and active-V2 baseline impact reviewed;

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

   active V2 semantic source / frozen historical source / project spec / implementation / evidence / roadmap / historical

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

ACTIVE BASELINE / CONTRACT IMPACT

<none / V2 reconciliation / final-freeze impact>

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

# 24. V2 Evolution Protocol

Use this fast path when Metamodel V2 or Mapping V2 changes after Phase 29. Do not restart the project from scratch.

## 24.1 Precondition

Before editing implementation:

1. identify the exact V2 files changed under `Core/Metamodel/version-2/**` and/or `Core/Mapping/version-2/**`;
2. preserve the previous baseline hash/version as evidence;
3. compute an exact structural/mapping diff;
4. classify each change as additive, removal, rename candidate, type/bounds change, containment/reference change, inheritance change, projection change, or mapping-only change.

Never accept a rename by fuzzy similarity alone.

## 24.2 Impact set

Inspect only affected areas, but always consider:

```text
Metamodel V2 / Mapping V2
→ Semantic kind registry / IR
→ parsers and extractors
→ resolver and stable IDs
→ TransformationPlan / USE generation
→ trace and binding targets
→ OCL contexts/navigation/provenance
→ runtime target-binding adapter
→ golden fixtures / evidence / docs
```

Jason/CArtAgO/Moise connectors, `RuntimeEvent`, queue/lifecycle, and generic mutation mechanics should remain unchanged unless the upstream runtime semantics themselves changed.

## 24.3 Change policy

For small V2 changes, prefer selective reconciliation over broad rewrites.

For a breaking semantic change, create an explicit migration task and preserve old evidence, but still reuse metamodel-independent infrastructure.

Do not maintain permanent V1/V2 dual production code solely for backward compatibility unless explicitly required.

## 24.4 Verification gate

A V2 baseline revision is accepted for continued development only after all applicable checks pass:

- [ ] Ecore parses and inventories deterministically;
- [ ] Mapping V2 covers the intended active V2 vocabulary;
- [ ] source/target identities resolve exactly;
- [ ] USE model compiles/type-checks;
- [ ] initial state materializes correctly;
- [ ] trace/binding remains exact;
- [ ] affected OCL compiles/type-checks;
- [ ] runtime target binding resolves or explicitly reports unsupported/unresolved;
- [ ] affected case studies/regressions pass;
- [ ] hashes/version/evidence/docs are synchronized.

Working-baseline acceptance is not the same as final freeze.

# 25. Final Rule
The agent owns project consistency, not only code generation.

Preserve:

```text

Code must agree with Specification

Tests must prove Code

Documentation must describe Specification + Current Implementation

Evidence must support Status Claims

```

For current development, “Specification” means the active V2 working baseline plus explicitly approved verification/runtime extensions. Historical V1 contracts remain evidence, not the active target.

If any relationship is broken, the task is not complete.
