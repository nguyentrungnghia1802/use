# Agent Working Rules — USE JaCaMo Plugin

> Goal: implement the code-grounded JaCaMo → native USE architecture consistently, safely, and incrementally.

# 1. Role

You are the implementation agent for the USE JaCaMo Plugin.

Your job is to keep these aligned:

```text
Official JaCaMo/Jason/CArtAgO/Moise semantics
→ semantic contract
→ J/A/C/M/X mapping rules
→ native USE MModel
→ native USE MSystemState
→ existing USE Session / GUI / OCL / verification
→ tests / evidence / documentation
```

Do not optimize for “code compiles”. Optimize for semantic correctness, exact evidence, traceability, determinism, and maintainability.

# 2. Read these files first

The following files are in the same directory as this `agent.md` and define the active direction:

1. `NEW-CODE-GROUNDED-JACAMO-USE-ARCHITECTURE-DESIGN.md`
2. `task.md`
3. `JACAMO-USE-JAVA-MODEL-TRANSFORMATION-SPEC.md`
4. `JACAMO-USE-CONCEPT-MAPPING-RULES.md`

Use them as:

```text
Architecture design      = target architecture
task.md                  = active execution plan and gates
Java transformation spec = source/target semantic rationale
Concept mapping rules    = rule contract Jxx/Axx/Cxx/Mxx/Xxx
```

For actual implementation behavior, current source code and executable tests remain authoritative.

If code and documents disagree, do not guess. Record the discrepancy and resolve it with code/API evidence.

# 3. Source-of-truth order

Use this priority:

1. current production source code and exact dependency APIs/bytecode;
2. current executable tests/runtime evidence;
3. current Bridge contract and semantic DTOs;
4. the four active files listed above;
5. old Ecore / Mapping V2 / historical docs only as compatibility/regression evidence.

Old V2 artifacts are no longer the semantic authority for `CODE_GROUNDED_NATIVE`.

Do not silently edit frozen V2/Ecore/mapping/golden artifacts.

# 4. Core architecture

JaCaMo executes; USE models and verifies.

```text
JaCaMo official objects
        ↓
official adapters
        ↓
typed semantic contract
        ↓
JacamoSpecificationModel
        ↓
Jxx / Axx / Cxx / Mxx / Xxx rules
        ↓
native MModel
        ↓
native MSystemState
        ↓
Session.setSystem(...)
        ↓
existing USE GUI / OCL / verification
```

`.use` is an export artifact from `MModel`, not the semantic authority.

The JaCaMo Workbench is an import/control/mapping-inspection UI only. It must not become a second model runtime, OCL engine, class diagram, object diagram, or state owner.

# 5. Hard invariants

## INV-001 — Official APIs are semantic authority
Do not reconstruct semantics with custom parsers when official JaCaMo/Jason/CArtAgO/Moise APIs already expose them.

## INV-002 — No semantic guessing
Unknown, unsupported, ambiguous, or unresolved facts remain explicit.

## INV-003 — No fuzzy/name-based formal mapping
Never infer semantic identity or relation from equal/similar names.

Especially forbidden:

```text
Action.name == Operation.name
Belief.literal == Property.name
AgentGoal.literal == OrganizationalGoal.id
JCM agent name == CArtAgO AgentId
artifact declaration name == ArtifactId
```

## INV-004 — Keep semantic layers separate

Distinguish:

```text
JCM deployment/configuration
Jason program/specification
CArtAgO type/runtime
Moise specification
runtime state/events
```

Do not collapse these layers into one object model.

## INV-005 — Type model and runtime state are separate

```text
semantic type → MClass/MAttribute/MAssociation/MOperation/EnumType → MModel
concrete object → MObject/value/link → MSystemState
```

## INV-006 — One active USE system
Facade, Session, runtime mutation, and verification must operate on the same `MSystem`.

## INV-007 — Trace before runtime mutation
An untraced or stale runtime fact must not mutate USE state.

## INV-008 — Moise cardinality keeps relation context

Preserve:

```text
(group, role, min, max)
(parentGroup, subgroup, min, max)
(scheme, mission, min, max)
```

Do not flatten these into universal attributes on Role/Group/Mission.

## INV-009 — Norm semantics remain distinct
Do not automatically translate Moise norms/deontics into OCL.

## INV-010 — Mapping is generic
Do not hard-code Hello World, Auction, House Building, or any case-specific identity.

## INV-011 — Fail closed
Missing capability/evidence becomes `UNKNOWN`, `UNAVAILABLE`, `UNRESOLVED`, or `SKIPPED_CAPABILITY`; never fabricate a target.

## INV-012 — Native mode is isolated from V2
`CODE_GROUNDED_NATIVE` must not silently load Mapping V2, V2 OCL, Runtime Mapping V2, or V2 verification profiles.

# 6. Rule contract

Rule families:

```text
Jxx = JCM / JaCaMo deployment
Axx = Jason / Agent / BDI
Cxx = CArtAgO
Mxx = Moise
Xxx = exact cross-dimension relations
```

The full catalog contains 105 rules:

```text
J01–J11
A01–A22
C01–C20
M01–M43
X01–X09
```

Every generated target element must be traceable to:

```text
ruleId
sourceKind/FQCN
sourceSemanticId
evidence
targetUseKind
targetUseId
fidelity
capability/status
diagnostic
```

Missing or duplicate rule IDs are build failures.

# 7. Implementation boundaries

## JaCaMo-side adapters
Allowed:
- official project/program/runtime inspection;
- exact identity/evidence capture;
- typed DTO creation.

Forbidden:
- USE classes;
- target-model decisions;
- fuzzy resolution.

## Semantic contract
Allowed:
- immutable typed facts;
- IDs, evidence, fidelity, capability.

Forbidden:
- live JaCaMo objects;
- USE runtime objects.

## Mapping/rules
Allowed:
- source semantic concept → native USE construct;
- exact cross-dimension resolution when evidence exists.

Forbidden:
- parsing source text;
- case-specific rules;
- guessed bindings.

## USE adapter
Allowed:
- native `MModel` construction;
- `MSystem` / `MSystemState` materialization;
- native invariants/pre/postconditions;
- Session activation.

Forbidden:
- source parsing;
- semantic guessing.

## Runtime adapter
Allowed:
- exact event/snapshot projection;
- trace-based target resolution;
- ordered state mutation;
- reconnect/resync.

Forbidden:
- reparsing project source per event;
- mutating stale/unresolved targets.

## UI
Allowed:
- import/control/status;
- mapping trace/evidence/fidelity/diagnostics;
- navigation to existing USE views.

Forbidden:
- transformation/domain logic;
- independent runtime/model ownership.

# 8. Development protocol

Before editing:

1. read `task.md`;
2. read the relevant sections of the other three active design/mapping files;
3. inspect current callers/tests for the affected subsystem;
4. run `git status`, branch, and recent log;
5. preserve any existing user work;
6. identify exact acceptance gate for the current phase.

During implementation:

```text
spec/evidence
→ focused failing test when practical
→ smallest correct change
→ focused tests
→ nearby regressions
→ diff review
→ documentation/evidence update
→ phase gate
```

Do not continue to the next phase if the mandatory gate fails.

# 9. Git and scope

- Do not destructive-reset or clean unknown work.
- Do not force-push the primary branch.
- Keep commits coherent and tested.
- Prefer Conventional Commits.
- Do not combine unrelated refactors with the active phase.
- Do not delete historical code/tests until `task.md` cleanup criteria are satisfied.

# 10. Testing requirements

Every meaningful change should include relevant:

- happy path;
- invalid/missing evidence;
- ambiguity/unresolved case;
- deterministic output;
- negative control;
- regression test.

Always test the layer changed.

Key native gates include:

```text
MModel construction
MSystemState materialization
Session.setSystem(...)
existing USE OCL evaluation
mapping trace completeness
no V2 load in native mode
.use export + recompile
runtime stale/resync handling
```

Case-study progression:

```text
Hello World
→ Auction
→ House Building
```

No case-specific production branches.

# 11. Runtime correctness

Runtime facts are accepted only when their session/model revision/generation/evidence is valid.

Use explicit states such as:

```text
MATERIALIZED_FAITHFULLY
EVIDENCE_ONLY
UNAVAILABLE
UNKNOWN
```

Only faithful materializable facts may mutate `session.system().state()`.

Reconnect/resync must re-establish authoritative state before LIVE verification resumes.

# 12. Security

Treat imported projects as untrusted.

Do not automatically execute arbitrary shell commands, project scripts, or arbitrary Java merely to infer static semantics.

Validate paths, symlinks, archives, classpath entries, OCL paths, and export destinations.

Reflection/class loading must be justified by the supported framework contract.

# 13. Documentation and evidence

After each phase/task:

- update `task.md` only when gates actually pass;
- update active architecture/mapping docs only when behavior truly changed;
- preserve historical evidence as historical;
- search for stale claims;
- do not rewrite old evidence as if freshly rerun.

A phase report must include:

1. Git diff summary;
2. files/classes changed;
3. rule IDs implemented;
4. current production call graph;
5. tests and exact results;
6. acceptance evidence;
7. remaining `UNKNOWN` / `UNAVAILABLE`;
8. legacy code still present/called;
9. plan-vs-code discrepancies;
10. risks/technical debt;
11. rollback point;
12. recommended next phase.

# 14. Definition of done

A task is DONE only when applicable items pass:

- [ ] semantics are code/API-grounded;
- [ ] correct rule IDs are implemented/traced;
- [ ] focused and regression tests pass;
- [ ] native USE model/state are valid;
- [ ] Session integration is correct;
- [ ] OCL/verification uses the same active system;
- [ ] no hidden V2 dependency exists in native mode;
- [ ] unresolved facts remain explicit;
- [ ] documentation/evidence matches implementation;
- [ ] final diff is reviewed.

# 15. Final rule

The agent owns project consistency, not only code generation.

```text
Official semantics must agree with Mapping Rules
Mapping Rules must agree with Native USE Model/State
Tests must prove Implementation
Runtime must mutate the same active USE System
Documentation and Evidence must match current behavior
```

If any relationship is broken, the task is not complete.
