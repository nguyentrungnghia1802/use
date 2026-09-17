# USE–JaCaMo Final Engineering Roadmap

> **Document type:** Final implementation roadmap for logic/coding completion  
> **Target:** USE–JaCaMo Runtime Verification Framework  
> **Starting baseline:** after v1.0.1 Correctness Hotfix  
> **Roadmap scope:** Phase 16 → Phase 22  
> **Primary goal:** complete the in-scope engineering logic of the thesis project without violating frozen semantics, fabricating unsupported semantics, or confusing future research with current implementation.
>
> This roadmap is intentionally stricter than a normal feature roadmap. It defines:
>
> - what the AI coding agent may implement autonomously;
> - what evidence must exist before a task is complete;
> - where the agent **MUST STOP** and wait for human/researcher approval;
> - which questions require research before implementation;
> - which semantics may legitimately end as **EXPLICITLY UNSUPPORTED**;
> - when the project can be considered **LOGIC/CODING COMPLETE**.

---

# 0. Current Baseline and Non-Negotiable Context

The current project baseline already contains substantial implementation.

Do **not** restart these systems from zero unless an explicit corrective task proves that the existing implementation is invalid.

Current baseline includes, at minimum:

- v1.0.1 Correctness Hotfix completed;
- workspace/runtime lifecycle fix;
- project binding integration fix;
- existing `RuntimeMirrorService`;
- existing `RuntimeMutationEngine`;
- existing `RuntimeVerificationEngine`;
- existing `TraceIndex`;
- existing `MSystem` / `MSystemState` path;
- existing Runtime Event V1 foundation;
- existing Auction in-process runtime evidence;
- frozen structural JaCaMo → USE mapping;
- canonical DSML4JaCaMo Ecore baseline;
- existing OCL/constraint infrastructure;
- existing offline and runtime verification tests.

The architectural direction remains:

```text
JaCaMo = actual execution engine

USE = formal verification model / verification mirror

OCL = verification language
```

Runtime direction:

```text
JaCaMo Runtime
    ↓
Runtime event/state acquisition
    ↓
Runtime identity / trace resolution
    ↓
Runtime mapping
    ↓
Ordered USE state mutation
    ↓
MSystemState
    ↓
OCL / verification engine
    ↓
PASS / FAIL / ERROR / SKIPPED
    ↓
Exact trace back to JaCaMo
```

The project does **not** attempt to run JaCaMo inside USE.

---

# 1. Global Execution Rules

These rules apply to every phase and every task.

## 1.1 Source-of-truth discipline

The agent must respect the source hierarchy defined in `agent.md`.

In particular:

- frozen Ecore is not modified for implementation convenience;
- frozen Structural Mapping V1 is not modified to carry runtime semantics;
- runtime mapping is a separate layer;
- case-study behavior must not leak into generic core semantics;
- roadmap text is a future plan, not proof of current implementation;
- implementation status must be supported by executable evidence.

---

## 1.2 Existing implementation first

Before implementing any task:

```text
1. inspect current code;
2. inspect current tests;
3. inspect current specification;
4. determine what already exists;
5. determine whether the task is:
   - new implementation,
   - formalization of existing implementation,
   - correction,
   - extension,
   - documentation closure.
```

Do not create duplicate abstractions because the roadmap uses a new name for something that already exists.

---

## 1.3 No semantic fabrication

When evidence is insufficient:

```text
SUPPORTED
PARTIALLY_SUPPORTED
UNSUPPORTED
UNKNOWN
```

are all legitimate outcomes.

The agent must not force every roadmap bullet into implemented code.

A correct explicit unsupported boundary is preferable to an unsound implementation.

---

## 1.4 Mandatory RED → GREEN → REGRESSION

For correctness-sensitive tasks:

```text
RED
→ reproduce missing/wrong behavior

GREEN
→ smallest correct implementation

REGRESSION
→ relevant subsystem + project tests remain green
```

When RED is impractical, the task report must explain why.

---

## 1.5 Mandatory documentation synchronization

After every task:

```text
Implementation
→ tests
→ semantic impact review
→ documentation impact discovery
→ update every affected document
→ stale-statement search
→ cross-document consistency review
→ task status update
→ commit
```

The task is not complete when documentation still describes obsolete behavior.

---

# 2. Stop-Gate Protocol

This roadmap uses three execution classifications.

## AUTO — Agent may continue

The AI may continue to the next task automatically when:

- requirements are already defined;
- no research choice changes semantics;
- no architecture decision remains open;
- frozen contracts are unaffected;
- acceptance criteria are objective;
- required tests pass.

---

## REVIEW — Agent completes work, then pauses for approval

The AI may implement the specified task, but after producing evidence it must pause before the next dependent task.

Use this for:

- important architecture formalization;
- major contract freezing;
- case-study acceptance;
- broad behavior changes.

---

## HARD STOP — Agent must not implement the dependent semantics yet

At a HARD STOP, the agent must:

1. finish the research/audit task only;
2. produce a structured decision package;
3. explicitly list options;
4. explain evidence and consequences;
5. identify recommendation only as **PROPOSED**, not as decided specification;
6. stop;
7. wait for the user/researcher to choose.

The agent must **not** silently select an option.

---

# 3. Mandatory Stop Report Format

At every REVIEW or HARD STOP, return:

```text
STOP GATE:
<gate ID>

WHY THIS REQUIRES HUMAN DECISION:
...

EVIDENCE COLLECTED:
...

CURRENT FACTS:
...

OPEN QUESTIONS:
...

OPTIONS:
A. ...
B. ...
C. ...

IMPACT OF EACH OPTION:
...

PROPOSED RECOMMENDATION:
...
(this is a proposal, not a decision)

FILES / CODE AFFECTED IF APPROVED:
...

TESTS THAT WILL BE REQUIRED:
...

WAITING FOR:
<exact human decision required>
```

Do not continue into dependent coding until the user answers.

---

# 4. Definition of Final Logic/Coding Completion

The project logic is complete when every in-scope semantic path is one of:

## A. IMPLEMENTED

```text
specified
+ implemented
+ tested
+ traceable
+ documented
+ accepted
```

or:

## B. EXPLICITLY UNSUPPORTED

```text
evidence explains why
+ no semantic guessing
+ diagnostic behavior defined
+ tests protect the boundary
+ documentation states the limitation
```

The project does **not** need to implement arbitrary semantics merely to remove every unsupported label.

The following are not required for final logic completion unless separately approved:

- OCL-controlled JaCaMo execution;
- automatic action blocking;
- automatic recovery;
- automatic plan selection;
- arbitrary Java body → OCL;
- full replacement deontic engine;
- large UI redesign;
- speculative runtime metamodel redesign.

---

# 5. Phase Dependency Graph

```text
Phase 16
Runtime Trace & Ordering Semantics
        ↓
Phase 17
Runtime Mapping & Identity Formalization
        ↓
Phase 18
Runtime Verification Pipeline Completion
        ↓
         v1.1 COMPLETE
        ↓
Phase 19
Cross-Dimensional Verification + Case Study #2
        ↓
         v1.2 COMPLETE
        ↓
Phase 20
Normative Runtime Feasibility + Approved Semantics
        ↓
         v1.3 BOUNDED COMPLETE
        ↓
Phase 21
Constraint Translation Supported-Subset Closure
        ↓
         v1.4 BOUNDED COMPLETE
        ↓
Phase 22
Final Logic Hardening & Engineering Closure
        ↓
LOGIC / CODING COMPLETE
```

---

# PHASE 16 — Runtime Trace & Ordering Semantics

## 16.0 Objective

Do **not** rebuild Runtime Event V1 from zero.

The purpose of Phase 16 is to audit and finalize the existing runtime event abstraction, introduce a generic ordered runtime history, and define how execution ordering becomes formally verifiable.

Target pipeline:

```text
Existing RuntimeEvent V1
        ↓
canonical event semantics
        ↓
RuntimeTrace
        ↓
ordering/checkpoint semantics
        ↓
USE-visible verification representation
        ↓
ordering verification
```

No Auction-specific logic may enter the core runtime trace layer.

---

## P16-T01 — Audit Existing RuntimeEvent V1

**Mode:** AUTO

### Responsibility

Establish exactly what already exists before adding anything.

### Required inspection

Review:

- Runtime Event model;
- JSON/schema if present;
- queue/event normalization;
- runtime connectors;
- correlation logic;
- sequence logic;
- event kinds;
- current runtime reports/tests;
- roadmap-required event subset.

### Compare current fields with desired concepts

Check whether existing implementation already represents:

- `eventId`;
- sequence;
- timestamp;
- source dimension/kind;
- semantic source ID;
- runtime source/object key;
- event type;
- correlation ID;
- typed payload.

### Output

Create an internal audit matrix:

| Concern | Existing | Missing | Different naming | Semantic gap | Action |
|---|---|---|---|---|---|

### Forbidden

- creating `RuntimeEvent2`;
- replacing a correct existing schema solely to match roadmap wording;
- breaking compatible event consumers without reason.

### Tests

Existing event/schema tests must remain green.

### Documentation

Update runtime documentation if roadmap terminology differs from actual implementation.

### Acceptance

- [ ] Existing RuntimeEvent contract is completely understood.
- [ ] No duplicate event abstraction is created.
- [ ] Gaps are enumerated explicitly.
- [ ] Current event consumers are identified.

---

## P16-T02 — Normalize Runtime Event Taxonomy

**Mode:** AUTO unless semantic conflict is discovered.

### Responsibility

Make event-kind semantics explicit and generic.

Initial categories to support where existing runtime APIs provide evidence:

```text
AGENT_ACTION_STARTED
AGENT_ACTION_COMPLETED
ARTIFACT_OPERATION_STARTED
ARTIFACT_OPERATION_COMPLETED
ARTIFACT_OPERATION_FAILED
OBS_PROPERTY_CHANGED
BELIEF_ADDED
BELIEF_REMOVED
GOAL_ADDED
GOAL_REMOVED
```

Existing names may remain if they are already contractual.

Do not rename merely for cosmetic uniformity.

### Required rules

For each event type define:

- origin runtime;
- source identity expectation;
- payload contract;
- whether it changes mirrored state;
- whether it creates an operation checkpoint;
- correlation behavior;
- whether a terminal event is required;
- unsupported runtime limitations.

### Edge cases

- operation never reaches terminal callback;
- duplicate callback;
- late callback;
- connector disconnect;
- unknown runtime identity;
- ambiguous semantic target.

### Acceptance

- [ ] Event taxonomy documented.
- [ ] Each event kind has a semantic purpose.
- [ ] Unsupported callbacks are explicit.
- [ ] Existing runtime evidence remains valid.

---

## P16-T03 — RuntimeTrace Domain Contract

**Mode:** AUTO for model design; REVIEW before freezing.

### Responsibility

Introduce a generic ordered event-history abstraction.

Conceptual model:

```text
RuntimeTrace
- streamId
- streamGeneration
- ordered RuntimeEvent entries
- lastAcceptedSequence
- lifecycle/boundary metadata
```

Exact fields must be derived from existing implementation needs.

### Required behaviors

RuntimeTrace must support:

- append accepted event;
- deterministic event order;
- lookup by event ID;
- lookup by correlation ID;
- ordered range/query;
- stream generation/boundary;
- reconnect boundary;
- workspace replacement boundary;
- immutable event entries;
- rejection of invalid order when required.

### Explicitly decide

- whether trace retains quarantined events;
- whether rejected events appear in trace;
- whether resync resets or starts a new generation;
- trace memory/retention behavior for current version.

If these are implementation-level only and do not affect verification semantics, the agent may choose conservative defaults and document them.

If they affect OCL-visible semantics, defer to Gate P16-G1.

### Tests

At minimum:

- append in order;
- duplicate sequence;
- decreasing sequence;
- correlation query;
- boundary generation;
- reconnect;
- late old-stream event;
- deterministic iteration.

### Acceptance

- [ ] Trace ordering is deterministic.
- [ ] Events are immutable once accepted.
- [ ] Stream boundary behavior is explicit.
- [ ] No case-study knowledge exists in RuntimeTrace.

---

# P16-G1 — HARD STOP: How RuntimeTrace Becomes Verifiable by USE/OCL

**Mode:** HARD STOP

This is the first mandatory research/architecture stop.

## Why stop here

A Java `List<RuntimeEvent>` is not automatically visible to USE/OCL.

Before implementing ordering constraints, the project must decide how execution history is represented to the verification layer.

## Research package required

The agent must investigate current USE/plugin capabilities and current project architecture.

Determine feasible options such as:

### Option A — Represent trace/event history inside USE model/state

Potentially creates verification-visible event objects/links.

Questions:

- Does this require new generated USE classes?
- Does it create a runtime verification profile rather than mutate frozen Mapping V1?
- What is the lifecycle/cost?

### Option B — Dedicated runtime-trace evaluator outside OCL

Ordering is verified by a dedicated engine while OCL remains state/operation verification.

Questions:

- Does this violate the research objective “ordering through OCL”?
- Is attribution still unified?

### Option C — Expose derived trace state into an existing verification representation

For example, derived state/checkpoint information visible to OCL without building a full runtime metamodel.

Questions:

- What is lost?
- Can arbitrary ordering properties be expressed?

### Option D — Another evidence-backed approach

Allowed only if clearly justified.

## Required evidence

The stop report must include:

- relevant current code paths;
- relevant USE API capabilities;
- impact on frozen Mapping V1;
- impact on TransformationPlan;
- impact on generated model;
- runtime lifecycle impact;
- OCL capability;
- testing implications;
- migration cost.

## Frozen constraint

The solution must **not** modify frozen structural Mapping V1 merely to insert runtime history.

## Waiting for human decision

The researcher/user must approve:

> **Which RuntimeTrace verification representation will become the v1.1 contract?**

The agent must not proceed to P16-T04 until approved.

---

## P16-T04 — Implement Approved RuntimeTrace Verification Representation

**Mode:** AUTO after P16-G1 approval.

### Responsibility

Implement exactly the approved architecture.

### Requirements

- no hidden alternative representation;
- no second competing runtime history;
- exact traceability between runtime event and verification representation;
- deterministic generation/update;
- clean lifecycle across reconnect/resync/workspace replacement.

### Tests

Must include:

- trace visible to verification layer;
- stable ordering;
- stream boundary;
- reconnect;
- replacement;
- exact event attribution.

### Acceptance

- [ ] RuntimeTrace can be consumed by the chosen verification mechanism.
- [ ] Frozen Structural Mapping V1 remains unchanged unless separately approved.
- [ ] No duplicate runtime history source of truth exists.

---

# P16-G2 — REVIEW: Canonical Auction Operation Identity

**Mode:** REVIEW

The roadmap uses names such as:

```text
openAuction
placeBid
closeAuction
```

while existing case material may use:

```text
open
placeBid
close
```

The agent must not invent aliases merely to satisfy roadmap prose.

## Required work

Inspect the actual Auction fixture and trace identities.

Produce:

```text
source operation name
→ semantic ID
→ projected USE operation
→ runtime operation identity
```

## Decision

If existing canonical names are unambiguous, retain them and update roadmap/test terminology.

If multiple legitimate names exist, ask the user which naming contract should be documented.

Do not continue to ordering acceptance tests while canonical identity remains ambiguous.

---

## P16-T05 — Generic Ordering Semantics

**Mode:** AUTO after gates.

### Responsibility

Define reusable ordering predicates/rules independent of Auction.

Examples of generic concepts:

- `happenedBefore(A, B)`;
- same-stream ordering;
- same-correlation ordering;
- operation started before completed;
- no operation terminal event before start;
- no later event accepted from retired stream generation.

Do not hard-code:

```text
open before placeBid
```

inside core.

Case-specific ordering belongs in Auction verification profile/tests.

### Tests

Generic unit tests must use synthetic event identities rather than Auction names.

---

## P16-T06 — Auction Ordering Verification

**Mode:** AUTO

### Required scenarios

Positive:

```text
open
→ placeBid
→ close
```

Negative:

```text
placeBid
→ open
→ close
```

Negative:

```text
open
→ close
→ placeBid
```

Use canonical names approved in P16-G2.

### Requirements

- violation points to exact event(s);
- event sequence is available;
- trace can return to relevant semantic/runtime element;
- no Auction-specific branch is added to runtime core.

---

## P16-T07 — Phase 16 Regression + Documentation

**Mode:** REVIEW

### Run

- focused RuntimeTrace tests;
- runtime foundation tests;
- runtime verification tests;
- Auction integration;
- relevant full module tests;
- full feasible regression.

### Documentation synchronization

At minimum review:

- runtime adapter;
- verification engine;
- testing strategy;
- Auction case study;
- architecture;
- acceptance;
- risk register;
- task tracking;
- known limitations.

### Phase 16 exit criteria

- [ ] Runtime Event current contract audited.
- [ ] RuntimeTrace implemented.
- [ ] Ordering is deterministic.
- [ ] Verification representation is approved and implemented.
- [ ] Generic ordering has no Auction dependency.
- [ ] Auction positive/negative ordering is verified.
- [ ] lifecycle correctness from v1.0.1 remains intact.
- [ ] documentation is synchronized.

---

# PHASE 17 — Runtime Mapping & Identity Formalization

## 17.0 Objective

Formalize runtime event/state → USE mutation semantics without duplicating Structural Mapping V1.

Target:

```text
RuntimeEvent
    ↓
Runtime identity resolution
    ↓
Runtime Mapping V1 rule
    ↓
RuntimeMutation
    ↓
exact USE target
```

This phase should formalize existing valid procedural semantics rather than replace working code blindly.

---

## P17-T01 — Audit Existing Runtime Mutation Rules

**Mode:** AUTO

Build an inventory:

| Event/state source | Current code path | Mutation | Target resolution | Existing test |
|---|---|---|---|---|

Cover:

- observable property update;
- belief change;
- goal change;
- object/link mutations;
- operation enter;
- operation exit;
- operation fail;
- snapshot mutations;
- organisation changes where supported.

Identify:

- generic rules;
- hidden hard-coded assumptions;
- Auction-specific code;
- duplicate mapping logic;
- missing validation.

---

# P17-G1 — REVIEW: Runtime Mapping V1 Contract Boundary

**Mode:** REVIEW

Before creating JSON/schema, define precisely:

```text
Structural Mapping V1:
JaCaMo metamodel
→ USE metamodel

Runtime Mapping V1:
JaCaMo runtime semantic event/state
→ USE mutation / verification action
```

The review package must state what Runtime Mapping V1:

- owns;
- does not own;
- references from Structural Mapping V1;
- references from trace/identity;
- never duplicates.

Human approval is required before freezing the contract shape.

---

## P17-T02 — Runtime Mapping V1 Schema

**Mode:** AUTO after P17-G1.

Define schema for rules such as:

```text
OBS_PROPERTY_CHANGED
→ SET_ATTRIBUTE

ARTIFACT_OPERATION_STARTED
→ OPERATION_ENTER

ARTIFACT_OPERATION_COMPLETED
→ OPERATION_EXIT
```

Do not assume that all beliefs/goals require object creation.

Each rule must encode enough conditions to prevent unsafe generic behavior.

### Required rule metadata

As appropriate:

- rule ID;
- source event kind;
- source dimension;
- required source identity kind;
- target mutation kind;
- target trace requirement;
- payload requirements;
- correlation requirement;
- lifecycle/checkpoint behavior;
- compatibility anchors;
- unsupported conditions.

---

## P17-T03 — Runtime Mapping Loader + Validator

**Mode:** AUTO

Validate:

- schema;
- duplicate rule IDs;
- unsupported event kinds;
- invalid mutation target;
- incompatible structural anchor;
- missing required trace;
- impossible payload contract;
- conflicting rules.

No silent fallback.

---

## P17-T04 — Integrate Mapping into Existing RuntimeMutationEngine

**Mode:** AUTO

Goal:

> Runtime Mapping V1 becomes the canonical declarative rule source where appropriate.

Avoid:

```text
JSON says A
Java hard-code says B
```

The engine may still contain mutation mechanics.

But semantic event→mutation selection should not have two divergent sources of truth.

### Tests

- valid mapping rule;
- missing rule;
- ambiguous/conflicting rule;
- invalid payload;
- untraced target;
- stale trace;
- existing Auction behavior.

---

## P17-T05 — Runtime Identity Registry Audit

**Mode:** AUTO

Audit current:

```text
runtime key / alias
→ SemanticId
→ USE ID
→ MObject / MOperation / link target
```

Check:

- Jason aliases;
- CArtAgO identities;
- Moise identities;
- reconnect;
- reimport;
- workspace replacement;
- stale source identity;
- reverse lookup;
- multiple runtime aliases for one semantic identity.

---

## P17-T06 — Complete Runtime Identity Registry

**Mode:** AUTO unless new semantics are required.

Required behaviors:

- exact lookup;
- alias preservation;
- no fuzzy lookup;
- stale generation rejection;
- workspace-generation ownership;
- reverse lookup for reports;
- reconnect alias restoration where proven;
- isolation of late callback from retired workspace/stream.

### Tests

- exact success;
- missing;
- ambiguity;
- stale alias;
- reconnect;
- rebuild;
- reimport;
- workspace replacement;
- old callback rejection.

---

## P17-T07 — Structural ↔ Runtime Mapping Compatibility Gate

**Mode:** AUTO

Validate that Runtime Mapping V1 only targets structures that actually exist or are valid verification projections.

Do not allow runtime mapping to fabricate:

- non-existent attribute;
- invalid association;
- unknown operation;
- unproven object identity.

---

## P17-T08 — Freeze Runtime Mapping V1

**Mode:** REVIEW

If Runtime Mapping V1 becomes a canonical contract, freeze only after:

- schema pass;
- semantic validator pass;
- positive tests;
- negative mutation tests;
- compatibility validation;
- integration tests;
- documentation update;
- version/hash manifest if project conventions require it.

### Phase 17 exit criteria

- [ ] Event→mutation semantics have one canonical rule source.
- [ ] Runtime identity is exact.
- [ ] stale/old identities are rejected.
- [ ] reconnect/replacement works.
- [ ] runtime mapping does not alter Structural Mapping V1.
- [ ] mapping contract is tested and documented.

---

# PHASE 18 — Runtime Verification Pipeline Completion

## 18.0 Objective

Close v1.1 as an end-to-end deterministic runtime verification system.

Target:

```text
runtime event
→ identity
→ runtime mapping
→ ordered mutation
→ checkpoint
→ USE state / RuntimeTrace
→ verification
→ result
→ exact violation attribution
```

---

## P18-T01 — Verification Checkpoint Contract

**Mode:** AUTO

Define first-class checkpoint kinds:

```text
SNAPSHOT
AFTER_MUTATION
OPERATION_PRE
OPERATION_POST
STREAM_BOUNDARY
```

Each checkpoint must specify:

- trigger;
- valid state;
- verification mode;
- trace/event context;
- expected result behavior;
- lifecycle on failure.

---

## P18-T02 — Idempotency / Double-Apply Protection

**Mode:** AUTO

Ensure:

- accepted runtime event is not applied twice;
- reconnect does not replay old accepted mutation incorrectly;
- buffered initial synchronization events are handled exactly once;
- operation terminal events cannot complete a retired operation state.

Tests must reproduce duplicate/double-apply risk.

---

## P18-T03 — Authoritative Synchronization Contract

**Mode:** AUTO

Guarantee:

```text
subscribe/buffer if applicable
→ authoritative snapshot
→ apply snapshot
→ apply valid post-snapshot deltas
→ verification
→ LIVE
```

Requirements:

- failed snapshot → ERROR/disconnect;
- no claim of LIVE before successful sync;
- removed runtime state clears stale USE state;
- resync repairs drift;
- no duplicate listener.

---

## P18-T04 — Stream Boundary Semantics

**Mode:** AUTO

Apply to:

- reconnect;
- resync;
- workspace replacement;
- reimport where runtime is connected;
- verification profile replacement when runtime consumers change.

Trace and identity generations must remain consistent.

---

## P18-T05 — Runtime OCL Profile Packaging

**Mode:** AUTO with semantic review.

Maintain separation:

```text
Generic reusable OCL
Case-study OCL
Generated/translated OCL
```

No Auction-specific constraint belongs in generic runtime core.

Review/load:

- `jacamo-core.ocl`;
- runtime-specific generic profile if adopted;
- Auction runtime profile;
- generated/translated constraints.

---

# P18-G1 — HARD STOP if Runtime Ordering Requires New Frozen Structural Semantics

**Mode:** CONDITIONAL HARD STOP

Trigger this gate only if implementing runtime OCL/profile requires:

- modifying frozen Ecore;
- modifying frozen Structural Mapping V1;
- introducing a new runtime metamodel that changes the research architecture;
- changing foundational USE representation beyond the approval from P16-G1.

If none of these occur, continue automatically.

If triggered, stop and request human research approval.

---

## P18-T06 — Runtime Verification Selection

**Mode:** AUTO

Ensure correct selection between:

- full invariant evaluation;
- dependency-targeted evaluation;
- operation PRE;
- operation POST;
- ordering/trace evaluation;
- conservative full-check fallback.

Do not skip constraints merely for performance.

---

## P18-T07 — Violation Report Model

**Mode:** AUTO

Minimum attribution where available:

```text
constraintId
checkpoint
runtimeEventId
sequence
semanticId
runtime key
USE context object
agent/artifact/operation identity
timestamp
correlationId
source trace
```

Preserve:

```text
PASS
FAIL
ERROR
SKIPPED
```

Do not collapse semantics.

---

## P18-T08 — Exact Violation Navigation

**Mode:** AUTO

Prove:

```text
Violation
→ OCL/rule
→ USE context
→ trace
→ SemanticId
→ runtime object/event
→ source span when available
```

Negative test must prove that attribution does not jump to a similarly named target.

---

## P18-T09 — v1.1 Auction End-to-End

**Mode:** REVIEW

Required scenarios:

### Positive

Valid open → bid → close.

### Negative ordering

Invalid ordering produces expected violation.

### Negative operation/precondition

Invalid bid/state produces expected result.

### Identity

Violation resolves to exact Auction runtime/semantic target.

### Lifecycle

- disconnect;
- mutate authoritative runtime;
- reconnect;
- full resync;
- no stale state;
- no stale consumer.

### Workspace replacement

Rebuild/reimport/profile replacement does not leave old consumers mutating new workspace.

---

## P18-T10 — v1.1 Release Gate

**Mode:** REVIEW

Run:

```text
focused runtime tests
runtime mapping validation
Auction runtime E2E
OCL/profile compilation
workspace replacement regression
reconnect/resync regression
full module package/verify
full feasible reactor verification
```

### v1.1 exit criteria

- [ ] runtime trace semantics complete;
- [ ] runtime mapping complete;
- [ ] runtime identity exact;
- [ ] state synchronization deterministic;
- [ ] runtime OCL/profile loading works;
- [ ] violations are exactly attributable;
- [ ] lifecycle correctness remains intact;
- [ ] full tests green;
- [ ] docs synchronized.

---

# PHASE 19 — Cross-Dimensional Verification

## 19.0 Objective

Prove that the framework verifies generic consistency across JaCaMo dimensions rather than only an Auction-specific runtime.

Dimensions:

```text
Agent
Environment
Organisation
```

---

## P19-T01 — Cross-Dimensional Rule Inventory

**Mode:** AUTO

Start from source-backed relations such as:

```text
Agent.artifact
Agent.joinWorkspace
ExternalAction.operation
Plan.RefArtifact
ObsProperty.obsproperty
Role.players
Organisation.deploysAgent
OGoal.OGoalToGoal
```

For each relation classify:

```text
STRUCTURAL_ONLY
OFFLINE_VERIFIABLE
RUNTIME_VERIFIABLE
NEEDS_ADDITIONAL_BINDING
UNSUPPORTED_WITH_CURRENT_RUNTIME_DATA
```

Do not turn every EReference into a runtime invariant automatically.

---

# P19-G1 — HARD STOP: Approve Cross-Dimensional Verification Rule Set

**Mode:** HARD STOP

The researcher must approve which relations become actual verification rules.

For every proposed rule provide:

```text
Rule ID
Source evidence
Meaning
Required runtime data
USE representation
OCL / evaluator form
Expected positive case
Expected negative case
Potential false-positive risk
```

The agent must not invent a semantic rule merely because two concepts are linked structurally.

Wait for approval.

---

## P19-T02 — Implement Approved Agent ↔ Environment Rules

**Mode:** AUTO after approval.

Candidate categories only if approved:

- Agent ↔ Artifact consistency;
- Agent ↔ Workspace consistency;
- ExternalAction ↔ Artifact Operation consistency;
- Plan.RefArtifact accessibility.

---

## P19-T03 — Implement Approved Environment ↔ Agent Rules

**Mode:** AUTO

Potential:

- ObsProperty ↔ Belief correspondence;
- state/percept consistency where runtime evidence genuinely supports it.

Do not imply percept delivery semantics that the connector cannot observe.

---

## P19-T04 — Implement Approved Organisation ↔ Agent Rules

**Mode:** AUTO

Potential:

- Role.players ↔ Agent;
- Organisation.deploysAgent;
- action/role consistency only if formally defined.

Do not infer permission/obligation here.

---

## P19-T05 — Implement Approved Organisation ↔ Goal/Plan Rules

**Mode:** AUTO

Potential:

- OGoal ↔ Goal;
- Mission ↔ runtime behavior where evidence exists;
- Scheme/OPlan ↔ triggering plan/action if semantics are proven.

---

# P19-G2 — HARD STOP: Select Case Study #2

**Mode:** HARD STOP

A second case study is required to demonstrate generic reuse.

The agent must not invent the case study autonomously.

## Researcher must choose a project with enough coverage

Desirable:

- at least one Agent;
- at least one Artifact;
- observable property;
- external action;
- organisation;
- role;
- goal/mission;
- deterministic reproducible scenario.

## Agent output before stopping

Produce candidate selection criteria, not fabricated project semantics.

Wait for the user to choose or provide Case Study #2.

---

## P19-T06 — Import and Baseline Case Study #2

**Mode:** AUTO after case-study approval.

Tasks:

- project discovery;
- semantic extraction;
- mapping;
- trace;
- USE generation;
- OCL/profile;
- runtime connector compatibility;
- positive baseline.

Do not add core special cases.

---

## P19-T07 — Cross-Dimensional Negative Scenarios on Case Study #2

**Mode:** AUTO

At least one intentional negative scenario for each approved applicable rule family.

Every violation must trace exactly.

---

## P19-T08 — Generic-Reuse Audit

**Mode:** REVIEW

Search core implementation for:

- Auction-specific class names;
- Auction-specific operation names;
- case-specific `if` branches;
- hard-coded runtime object names;
- case-specific bindings hidden in generic code.

Any such dependency must either be removed or explicitly justified outside core.

### v1.2 exit criteria

- [ ] approved cross-dimensional rules implemented;
- [ ] generic rule catalog documented;
- [ ] Auction still passes;
- [ ] Case Study #2 passes;
- [ ] negative scenarios work;
- [ ] no case-specific generic-core logic;
- [ ] traceability remains exact.

---

# PHASE 20 — Normative Runtime Feasibility & Approved Semantics

## 20.0 Objective

Determine what normative/deontic runtime semantics can actually be verified from supported Moise runtime evidence.

This phase must not assume:

```text
Norm
→ automatically OCL invariant
```

---

## P20-T01 — Real Moise Runtime API Evidence Audit

**Mode:** AUTO research only.

Investigate current pinned Moise capabilities for:

- role adoption;
- group membership;
- scheme instances;
- mission commitment;
- organisational goal state;
- obligation;
- permission;
- prohibition;
- norm activation;
- applicability;
- fulfilment;
- violation;
- expiration/deadline.

For each concept record:

| Concept | API/source | Observable? | Exact semantics? | Event/polling? | Stable? |
|---|---|---|---|---|---|

No implementation beyond diagnostic probes/test research is allowed yet.

---

## P20-T02 — Normative Semantic Classification

**Mode:** AUTO research only.

Classify each:

```text
SUPPORTED_EXACT
SUPPORTED_PARTIAL
DERIVABLE_WITH_EXPLICIT_ASSUMPTION
NOT_EXPOSED
UNSAFE_TO_INFER
```

For partial/derivable items explain information loss.

---

# P20-G1 — HARD STOP: Normative Verification Scope Approval

**Mode:** HARD STOP

This is a mandatory researcher decision.

The stop package must present:

- what Moise actually exposes;
- what cannot be observed;
- what can be verified safely;
- what would require new semantics;
- whether OCL is appropriate;
- whether a dedicated deontic evaluator is required.

Human must explicitly approve the v1.3 subset.

The agent must **not** manufacture obligation/permission/violation lifecycle logic.

---

## P20-T03 — Normative Runtime State Model for Approved Subset

**Mode:** AUTO after approval.

Represent only approved concepts.

Preserve distinction between:

```text
structural Norm
normative runtime state
verification result
```

---

## P20-T04 — Runtime Acquisition / Polling

**Mode:** AUTO

Use supported official/runtime APIs.

Where no listener exists, controlled deterministic polling is acceptable only where already consistent with architecture.

---

## P20-T05 — Normative Verification Rules

**Mode:** AUTO for approved rules.

Each rule must have:

- semantic source;
- runtime evidence;
- context;
- exact assumptions;
- evaluation mechanism;
- positive case;
- negative case;
- traceability.

---

## P20-T06 — Unsupported Boundary Tests

**Mode:** AUTO

For every rejected normative capability, test that the system:

- does not fabricate state;
- does not claim verification;
- reports unsupported/missing evidence appropriately.

This counts as valid phase completion.

---

## P20-T07 — Normative E2E

**Mode:** REVIEW

Demonstrate approved subset only.

### v1.3 bounded completion criteria

- [ ] every targeted normative concept classified;
- [ ] approved supported subset implemented;
- [ ] unsupported subset explicitly protected;
- [ ] no Norm→OCL semantic collapse;
- [ ] tests/evidence match claims;
- [ ] docs state limitations precisely.

---

# PHASE 21 — Constraint Translation Supported-Subset Closure

## 21.0 Objective

Complete the translation framework only for semantics that can be justified.

Do not rebuild existing Constraint IR.

Existing categories such as:

```text
EXACT
SOUND_SUBSET
LOSSY
UNSUPPORTED
```

must remain meaningful.

---

## P21-T01 — Translation Rule Inventory

**Mode:** AUTO

Audit current support for:

- CArtAgO guards;
- Jason plan context;
- structural consistency;
- authored operation contracts;
- operation effects;
- case OCL;
- core OCL.

For each current rule identify:

- parser source;
- binding requirements;
- generated OCL;
- translation status;
- evidence;
- limitations;
- tests.

---

## P21-T02 — Candidate Translation Feasibility Matrix

**Mode:** AUTO research only.

Evaluate candidates:

### CArtAgO

- guard → precondition;
- selected declarative effects → postcondition.

### Jason

- plan context → condition only when state binding is complete.

### Structural

- only when USE structure does not already encode the rule and additional verification is meaningful.

### Moise

No generic deontic translation without approved formal semantics.

---

# P21-G1 — HARD STOP: Approve Final Translation Subset

**Mode:** HARD STOP

For each proposed translation rule show:

```text
source construct
source semantics
target OCL semantics
binding requirements
assumptions
information loss
classification
positive fixture
negative fixture
unsupported boundary
```

Human must approve the final v1.4 subset.

The agent may not turn `LOSSY` into an accepted default without explicit approval.

---

## P21-T03 — Implement Approved EXACT Rules

**Mode:** AUTO

Requirements:

- deterministic;
- provenance recorded;
- parse/type-check in USE;
- positive and negative fixtures;
- exact source dependency trace.

---

## P21-T04 — Implement Approved SOUND_SUBSET Rules

**Mode:** AUTO only if approved.

Documentation must explain the one-direction preservation property.

Do not call these equivalent translations.

---

## P21-T05 — LOSSY Rules

**Mode:** HARD STOP per rule unless already approved in P21-G1.

Default behavior:

```text
do not enable automatically
```

If approved:

- explicit profile opt-in;
- visible warning;
- assumptions in manifest/report.

---

## P21-T06 — Unsupported Expression Preservation

**Mode:** AUTO

Unsupported expressions must:

- remain source-traceable;
- emit appropriate diagnostic;
- produce no fake partial formula;
- not break unrelated supported extraction.

---

## P21-T07 — Translation Regression Matrix

**Mode:** REVIEW

Required:

- positive;
- negative;
- malformed;
- unsupported;
- ambiguous binding;
- generated OCL compile;
- deterministic output;
- provenance.

### v1.4 bounded completion criteria

- [ ] supported subset is explicit;
- [ ] all supported rules have evidence;
- [ ] unsupported semantics are preserved safely;
- [ ] no arbitrary Java translation;
- [ ] no heuristic semantic guessing.

---

# PHASE 22 — Final Logic Hardening & Engineering Closure

## 22.0 Objective

No new research feature is introduced here.

The purpose is to prove that the project has no known in-scope logic/coding gap hidden behind documentation, TODOs, weak tests, stale behavior, or unsupported claims.

---

## P22-T01 — Requirement → Code → Test Traceability Audit

**Mode:** AUTO

For every major project requirement:

```text
Requirement
→ component
→ implementation
→ tests
→ evidence
→ documentation
```

Classify:

```text
COMPLETE
PARTIAL
UNSUPPORTED_BY_DESIGN
MISSING
CONFLICT
```

Any `MISSING` or `CONFLICT` becomes a blocking task before closure.

---

## P22-T02 — TODO / FIXME / Temporary Implementation Audit

**Mode:** AUTO

Search:

```text
TODO
FIXME
HACK
TEMP
temporary
workaround
not implemented
unsupported
throw UnsupportedOperationException
placeholder
```

Classify each.

Not every TODO is blocking.

But every correctness-relevant TODO must be resolved or explicitly accepted as out of scope.

---

## P22-T03 — Dead / Duplicate / Parallel Logic Audit

**Mode:** AUTO

Search for:

- unused old runtime mapper;
- duplicate trace systems;
- obsolete verifier path;
- old binding path;
- stale profile loader;
- duplicate mapping rules;
- test-only behavior leaking into production.

Remove only when evidence proves safe.

---

## P22-T04 — Diagnostic Completeness Audit

**Mode:** AUTO

Ensure major failure paths have:

- code;
- severity;
- phase;
- source/semantic context where possible;
- actionable explanation.

No silent fallback.

---

## P22-T05 — Determinism Audit

**Mode:** AUTO

Verify stable behavior for:

- semantic IDs;
- generated names;
- runtime mapping rule selection;
- output ordering;
- trace ordering;
- report ordering;
- generated artifacts;
- repeatable test scenarios.

---

## P22-T06 — Runtime Lifecycle / Resource Audit

**Mode:** AUTO

Check:

- connector subscribe/unsubscribe;
- duplicate listeners;
- queue shutdown;
- late callbacks;
- executor/thread cleanup;
- polling scheduler cleanup;
- reconnect;
- profile replacement;
- workspace replacement;
- failed synchronization;
- error recovery.

---

## P22-T07 — Security Audit

**Mode:** AUTO

Review:

- path traversal;
- symlink escape;
- archive handling;
- classpath handling;
- Java class initialization;
- XML parser security;
- OCL path loading;
- export path;
- arbitrary command execution.

---

## P22-T08 — Frozen Contract Audit

**Mode:** AUTO

Verify:

- canonical Ecore unchanged unless explicitly reconciled;
- Structural Mapping V1 hash/schema valid;
- mapping audit passes;
- runtime mapping contract valid;
- freeze evidence current.

Never update hashes without semantic review.

---

## P22-T09 — Full Runtime Regression

**Mode:** AUTO

Cover:

- synthetic event stream;
- ordering;
- identity;
- mapping;
- mutation;
- full sync;
- reconnect;
- resync;
- drift;
- workspace replacement;
- operation pre/post;
- RuntimeTrace;
- violation attribution.

---

## P22-T10 — Multi-Case End-to-End Acceptance

**Mode:** AUTO

Run:

```text
Auction
+
Case Study #2
```

Both must prove:

- generic pipeline;
- no hidden case-specific core logic;
- exact trace;
- positive and negative scenarios.

Normative subset only where approved/applicable.

---

## P22-T11 — Performance Evidence

**Mode:** AUTO

Measure:

- import;
- generation;
- full verification;
- runtime event→result latency;
- queue depth;
- memory where practical.

Do not invent SLA.

Optimize only if a reproducible bottleneck blocks acceptable operation.

---

## P22-T12 — Clean Checkout / Relocated Build

**Mode:** AUTO

Run project build in:

- clean checkout;
- no stale target/build outputs;
- relocated filesystem path where project supports it;
- recorded JDK/Maven/dependency versions.

---

## P22-T13 — Reproducibility Gate

**Mode:** AUTO

For deterministic artifacts, compare hashes/expected output where meaningful.

For runtime-specific timestamps/UUIDs, compare semantic evidence rather than requiring impossible byte equality.

---

## P22-T14 — Plugin Packaging / Load Gate

**Mode:** AUTO

Verify:

- package inventory;
- embedded canonical resources;
- license/resources;
- checksum;
- plugin loading in target USE distribution/environment;
- no dependency on test-only classpath.

---

## P22-T15 — Full Documentation Synchronization Audit

**Mode:** AUTO

Review at minimum:

```text
README
architecture
metamodel baseline
mapping contract
semantic/extraction
constraint/OCL
USE transformation
trace/binding
runtime adapter
verification engine
UI workflow
testing strategy
case studies
build/release
research boundaries
acceptance
risk register
known limitations
compatibility
agent/task docs
```

Search for:

- obsolete versions;
- stale test counts;
- old operation names;
- “not implemented” statements that are now false;
- “supported” statements that are too broad;
- outdated known limitations;
- roadmap items incorrectly described as current.

---

## P22-T16 — Final Acceptance Matrix

**Mode:** REVIEW

Produce a final table:

| Capability | Status | Evidence | Limitation |
|---|---|---|---|
| Structural mapping | | | |
| Static import | | | |
| Semantic model | | | |
| USE transformation | | | |
| Trace/binding | | | |
| Runtime events | | | |
| Runtime trace | | | |
| Runtime mapping | | | |
| Synchronization | | | |
| OCL verification | | | |
| Cross-dimensional | | | |
| Normative supported subset | | | |
| Constraint translation subset | | | |
| Reporting | | | |
| Packaging | | | |
| Reproducibility | | | |

No blank status is allowed.

Valid statuses:

```text
COMPLETE
SUPPORTED_SUBSET_COMPLETE
EXPLICITLY_UNSUPPORTED
OUT_OF_SCOPE
BLOCKED
```

---

# P22-G1 — FINAL HARD STOP: Researcher Project Closure Approval

**Mode:** HARD STOP

The AI must not independently declare the thesis project's engineering complete.

At this point it must present:

```text
1. Final acceptance matrix
2. Full test results
3. Multi-case E2E results
4. Remaining limitations
5. Explicit unsupported semantics
6. Frozen contract status
7. Runtime Mapping status
8. Documentation consistency status
9. Release/package status
10. Any remaining TODO/FIXME classification
```

The researcher/user decides whether:

```text
A. LOGIC/CODING COMPLETE

B. ADDITIONAL CORRECTNESS WORK REQUIRED

C. ONE OR MORE UNSUPPORTED AREAS MUST BECOME NEW RESEARCH SCOPE
```

Only after explicit approval may the project be marked:

```text
CORE LOGIC / CODING COMPLETE
```

---

# 6. Human Research Gates Summary

The following gates require explicit human/researcher input.

| Gate | Phase | Question | Type |
|---|---|---|---|
| P16-G1 | 16 | How will RuntimeTrace become formally visible/verifiable by USE/OCL? | HARD STOP |
| P16-G2 | 16 | What are canonical Auction operation identities if source/evidence is inconsistent? | REVIEW |
| P17-G1 | 17 | Exact Runtime Mapping V1 responsibility/boundary | REVIEW |
| P17-T08 | 17 | Freeze Runtime Mapping V1 | REVIEW |
| P18-G1 | 18 | Any need to change frozen structural semantics/runtime metamodel architecture? | CONDITIONAL HARD STOP |
| P18-T09 | 18 | v1.1 E2E semantic acceptance | REVIEW |
| P19-G1 | 19 | Which structural relations become semantic verification rules? | HARD STOP |
| P19-G2 | 19 | Select/provide Case Study #2 | HARD STOP |
| P20-G1 | 20 | Approve normative/deontic supported subset | HARD STOP |
| P21-G1 | 21 | Approve final constraint translation subset | HARD STOP |
| P21-T05 | 21 | Approve any individual LOSSY rule not already approved | HARD STOP |
| P22-T16 | 22 | Review final acceptance matrix | REVIEW |
| P22-G1 | 22 | Declare engineering completion | HARD STOP |

---

# 7. What the AI Must Do When Reaching a HARD STOP

The AI must **not**:

- continue implementation “using best judgment”;
- silently pick the simplest option;
- treat its recommendation as a requirement;
- change frozen semantics to avoid the decision;
- create a fake placeholder implementation and move on.

It must:

```text
research
→ summarize evidence
→ classify FACT / INFERENCE / ASSUMPTION / OPEN QUESTION
→ present alternatives
→ explain impact
→ stop
→ wait for user decision
```

---

# 8. What the AI May Continue Without Asking

The agent should not interrupt the user for routine engineering decisions when the semantics are already fixed.

Examples:

- test fixture organization;
- private helper naming;
- refactoring that does not change behavior;
- validation code structure;
- deterministic collection choice;
- local error plumbing consistent with existing diagnostics;
- adding tests required by established contract;
- documentation synchronization;
- implementing an already approved rule.

The purpose of stop gates is to protect research/semantic decisions, not to make implementation unnecessarily interactive.

---

# 9. Recommended Task Execution Order

Within each phase, execute tasks in order unless dependency evidence proves otherwise.

Recommended global order:

```text
P16-T01
P16-T02
P16-T03
P16-G1  ← STOP
P16-T04
P16-G2
P16-T05
P16-T06
P16-T07

P17-T01
P17-G1
P17-T02
P17-T03
P17-T04
P17-T05
P17-T06
P17-T07
P17-T08

P18-T01
P18-T02
P18-T03
P18-T04
P18-T05
P18-G1 if triggered
P18-T06
P18-T07
P18-T08
P18-T09
P18-T10

P19-T01
P19-G1  ← STOP
P19-T02
P19-T03
P19-T04
P19-T05
P19-G2  ← STOP
P19-T06
P19-T07
P19-T08

P20-T01
P20-T02
P20-G1  ← STOP
P20-T03
P20-T04
P20-T05
P20-T06
P20-T07

P21-T01
P21-T02
P21-G1  ← STOP
P21-T03
P21-T04
P21-T05 when applicable
P21-T06
P21-T07

P22-T01
P22-T02
P22-T03
P22-T04
P22-T05
P22-T06
P22-T07
P22-T08
P22-T09
P22-T10
P22-T11
P22-T12
P22-T13
P22-T14
P22-T15
P22-T16
P22-G1  ← FINAL STOP
```

---

# 10. Final Scope Boundary

## Must be complete

Before final closure, the project should have:

- RuntimeTrace;
- ordering semantics;
- Runtime Mapping V1 or the approved equivalent declarative contract;
- exact runtime identity;
- deterministic synchronization;
- runtime verification checkpoints;
- runtime OCL/profile path;
- exact violation attribution;
- generic cross-dimensional verification;
- at least two case studies proving reuse;
- supported normative subset or explicit unsupported boundary;
- supported constraint translation subset or explicit unsupported boundary;
- full lifecycle correctness;
- complete test/evidence/documentation closure.

---

## May legitimately remain unsupported

If evidence is insufficient, the final project may explicitly retain:

- arbitrary Java effect translation;
- arbitrary Jason semantic translation;
- complete NPL/deontic lifecycle;
- runtime entities with no provable semantic identity;
- automatic agent enforcement/control;
- automatic repair;
- broad UI redesign;
- untested runtime/library versions;
- large-scale production performance guarantees.

These must be documented and tested as boundaries where appropriate.

---

# 11. Final Engineering Principle

The final project is not judged by how many roadmap ideas were converted into code.

It is judged by whether its claims are correct.

The final invariant is:

```text
Every in-scope behavior
    ↓
either
    ↓
IMPLEMENTED + TESTED + TRACEABLE
    ↓
or
    ↓
EXPLICITLY UNSUPPORTED + JUSTIFIED + PROTECTED
```

No hidden third state is allowed:

```text
“sort of implemented”
“probably works”
“roadmap says it exists”
“best-effort guessed semantics”
```

When Phase 22 closes with researcher approval, the project can legitimately move from:

```text
engineering development
```

to:

```text
thesis writing
research analysis
demo preparation
presentation
optional future extensions
```
