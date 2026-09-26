# TASK — USE–JaCaMo Architecture Realignment Implementation

> **Execution mode:** ONE CHAT · ONE AI · END-TO-END OWNERSHIP  
> **Workspace assumption:** the workspace contains both `use` and `jacamo`.  
> **Primary implementation target:** `use/use-plugin` plus a JaCaMo-side Bridge module/component as justified by the dependency spike.  
> **Audit authority:** the 21 files `00-README.md` … `20-final-feasibility-verdict.md` under `use/use-plugin/docs/architecture-realignment/`.  
> **Overall audited verdict:** `FEASIBLE_WITH_ADAPTER`.  
> **Important:** this task authorizes staged implementation of the approved architecture, **not** arbitrary redesign of frozen V2 resources.

> **Execution record — 2026-09-26:** Phase 0 PASS; Phase 1 PASS; Phase 2 PASS for the declared official-API capability set; Phase 3 PASS including the mandatory separate-JVM proof; Phase 4 PASS; Phase 5 `SUPPORTED_SCOPE_PASS`; Phase 6 `SUPPORTED_SCOPE_PASS`; Phase 7 review complete with no frozen-resource change and no V2.x/V3 STOP; Phase 8 PASS with authenticated loopback TCP. STOP before Phase 9 as requested. Reproducible evidence is under `docs/project/architecture-realignment/phase00-08-evidence.json` and the adjacent phase records. Original Auction plan/deadline equivalence and unobserved original House dynamic lifecycle remain explicit limitations, not hidden PASS claims.
>
> **Checklist interpretation:** checked phase gates and items are executable claims backed by the adjacent evidence records. Conditional original-runtime observations that were not available remain deliberately unchecked and are classified `UNAVAILABLE`; remote-auth/TLS items are not applicable because Phase 8 selects loopback-only transport. They are not silently treated as completed.

---

## 0. How the AI must execute this task

This task is designed for **one AI in one continuous chat**. Do not split the work across independent agents/chats. Keep one coherent implementation state, one decision log, and one checklist.

The ordering below is **hard-first where dependencies allow**. Dependency correctness always wins over cosmetic ordering: a later hard phase must not be pulled earlier if its prerequisites are not proven.

### Mandatory operating rules

- [x] Read this entire `task.md` before touching code.
- [x] Re-read all `00`–`20` architecture-realignment documents before implementation.
- [x] Treat production source and executable evidence as authority; never invent JaCaMo/Jason/CArtAgO/Moise APIs.
- [x] Inspect the live workspace before relying on paths/classes named in the audit; the audit revisions are evidence anchors, **not reset targets**.
- [x] Preserve all pre-existing user changes. Never use `git reset --hard`, destructive `git clean`, blanket checkout/revert, or overwrite unrelated dirty files.
- [x] Do not commit/push unless explicitly requested by the user.
- [x] Do not pause for confirmation between normal phases. Continue automatically when the phase gate passes.
- [x] Stop only if a true blocker or a forbidden architectural change is required.
- [x] Update the checklist in this file as work completes.
- [x] At every phase boundary record: files changed, commands run, test counts, hashes checked, evidence generated, remaining risks.
- [ ] If execution is interrupted by usage/time limits, persist the exact state/checklist/evidence and resume **in this same chat**. Do not ask the user to restate context.
- [x] Prefer small, reviewable implementation slices; no big-bang rewrite.

### Absolute prohibitions

Do **not** do any of the following unless a new evidence-backed proposal is produced and the user explicitly approves it:

- [ ] modify frozen Ecore V2;
- [ ] modify Mapping V2.2 semantic rules or hashes;
- [ ] modify frozen Runtime Mapping V2 semantic targets/hashes;
- [ ] change frozen OCL/profile hashes to make tests pass;
- [ ] update goldens merely because Bridge output differs;
- [ ] patch/fork JaCaMo core;
- [ ] introduce fuzzy/name/arity/literal-similarity semantic resolution;
- [ ] auto-translate Moise/NPL norms into OCL;
- [ ] hard-code Hello/Auction/House names or behavior into production logic;
- [ ] silently fall back from the Bridge path to legacy parsing;
- [ ] silently drop observable runtime facts that lack a USE target;
- [ ] treat wall-clock timestamps as a global causal order;
- [ ] choose a production transport before the Phase-H-equivalent transport phase.

---

# 1. Immutable architectural invariants

These rules apply to **every phase**.

### Authority

```text
JaCaMo/Jason/CArtAgO/Moise
        = semantic + execution authority

Bridge
        = observation/adaptation/serialization authority

Frozen Ecore V2
        = bounded target vocabulary, not source parser

Mapping V2.2
        = structural Ecore/V2 -> USE transformation

Runtime Mapping V2
        = normalized faithful runtime mutation mapping

USE
        = formal mirror + OCL verification authority
```

- [x] USE must not reconstruct JaCaMo meaning when official objects already own that meaning.
- [x] Bridge-side code may depend on the exact JaCaMo distribution.
- [x] USE production-side semantic code must depend only on the neutral contract, not live JaCaMo/Jason/CArtAgO/Moise objects.
- [x] No shared-classpath assumption is allowed across the JaCaMo/USE process boundary.

### Identity

Every live fact must be anchored by canonical identity, not display name.

```text
official/source identity
 -> BridgeEntityId / BridgeRelationId
 -> SemanticId
 -> TraceIndex
 -> USE classifier/object/link
```

- [x] Runtime identity carries `sessionId` and `generation` directly or through an incarnation.
- [x] Recreate-after-dispose/stop always creates a new live identity even if the local name is reused.
- [x] Unknown/stale/dangling identity fails closed.
- [x] Cross-dimensional links require exact official/config/runtime/binding evidence.

### Snapshot/event semantics

- [x] Never claim a globally atomic Jason+CArtAgO+Moise snapshot.
- [x] Use buffered validated cuts with per-source watermarks.
- [x] Use per-source monotonic sequence plus explicit causation/correlation; no fabricated global total order.
- [x] Gap/overflow/drift/unknown incarnation => quarantine + resync.
- [x] At-least-once delivery is acceptable only with idempotent event IDs.
- [x] Exactly-once delivery must not be claimed.

### Frozen-V2 fidelity boundaries

Relation-scoped Moise cardinality is exact in Bridge/provenance:

```text
GroupRoleCardinality(groupId, roleId, min, max)
ParentSubGroupCardinality(parentGroupId, subGroupId, min, max)
```

- [x] Never arbitrarily choose/merge contextual cardinalities into `Role.min/max` or `Group.min/max`.
- [x] Context-collapsing V2 projection is `SUPPORTED_SUBSET` or `REPRESENTATION_LOSS`, never `EXACT`.

Runtime facts such as:

- mission commitment,
- organizational-goal runtime state,
- norm instance/lifecycle,
- group/scheme runtime-instance context,

must always be captured when observable.

- [x] If faithfully materialized into `MSystemState`: mark `MATERIALIZED_FAITHFULLY`.
- [x] If captured but no faithful USE target exists: retain as `EVIDENCE_ONLY`.
- [x] If unavailable from the authority: mark `UNAVAILABLE`.
- [x] OCL may depend only on faithfully materialized runtime dependencies.
- [x] Evidence-only/unavailable OCL dependency => `INCONCLUSIVE`, `NOT_EVALUATED`, or capability-blocked; never guessed/defaulted.

---

# 2. Baseline anchors that must be preserved and revalidated

These are **audit-time anchors**, not instructions to reset current HEAD.

### Audited revisions / versions

- USE audit revision: `215784b648a906e4db6086352946aba5bda10f98`
- JaCaMo audit revision: `3866858a7ebf6be85d9199c13a09cf4bfb8191be`
- USE: `7.5.0`
- JaCaMo audited workspace: `1.3.1`
- Jason audited runtime: `3.3.2`
- CArtAgO: `3.1`
- Moise: `1.1`
- NPL: `0.6.1`
- Frozen release compatibility evidence also references JaCaMo `1.3.0` / Jason `3.3.0`.

### Frozen artifact hashes

- Ecore V2: `4ae51638a078f0a933982844063993084f17d420694ac8a868d95b9df063c35c`
- Mapping V2.2: `fc03b90cf0729260747bfeffa6a6cd463eefd2259c0c3cd60ed22bd140ec48b1`
- Runtime Mapping V2: `5b2c00f052010fb35a71eb7f50ae4650f8a09c7647332b47a7199c12cbf8a5f0`
- Freeze manifest audit hash: `7965aef8eae099e398de50cb30a65f1b6c5b9207a6500db678825340bca73c77`

### Audited test baseline

- `use-core`: 12 passed
- `use-gui`: 1 passed
- `use-plugin`: 228 run, **3 pre-existing failures**, 0 errors, 0 skipped
  - `GoldenPipelineTest` digest mismatch
  - `ConstraintClosureTest` expected 2 but got 0
  - `InstanceMaterializationTest` golden digest mismatch
- JaCaMo audited Gradle tests: 6/6 passed

The three USE failures are not acceptable forever, but they are **baseline evidence**. Do not hide them or change goldens/assertions merely to get green.

### Canonical case roots / audit hashes

- Hello World JCM: `c81d15c9aa80c6e75ee8ead017f8daaddb1038ec9cfbc80c6a3057bde10b4101`
- Auction JCM: `c766fb0dc5fc6f4085cf6c1fc26d2df09229256c2e6138dfd4d44ef21fb7d2fb`
- House-Building JCM: `c14ae6299b0d2e0034d7daaa233b9bf1b94a7b337aa39477ec865c52ca5fef08`

Canonical case copies used as acceptance evidence must remain byte-identical to the recorded upstream inputs or receive a new explicit source manifest.

---

# PHASE 0 — Mandatory preflight and reproducibility guard
**Difficulty: LOW, but prerequisite — keep this phase short and do it first.**  
**Corresponds primarily to original Phase A.**

## Goal

Establish a safe implementation baseline without destroying dirty work or confusing old failures with new regressions.

## Checklist

### Workspace state
- [x] Locate the `use` and `jacamo` repositories.
- [x] Record branch, HEAD, remote relationship, and dirty status for both.
- [x] Record all modified/untracked paths before changing anything.
- [x] Compare current revisions with the audit anchors; do not reset solely because they differ.
- [x] Record JDK, Maven, Gradle and OS/runtime environment.
- [x] Resolve the actual JaCaMo dependency graph.

### Frozen resources
- [x] Recompute Ecore V2 hash.
- [x] Recompute Mapping V2.2 hash.
- [x] Recompute Runtime Mapping V2 hash.
- [x] Recompute/read freeze-manifest hash.
- [x] Fail the phase if any frozen artifact changed without prior explicit approval.

### Baseline tests
- [x] Run the USE reactor baseline: `mvn -B -pl use-plugin -am test`.
- [x] Record exact counts and failure signatures.
- [x] Classify any difference from the 3 known failures before proceeding.
- [x] Run JaCaMo tests in an **isolated copy/worktree/distribution**, because its Gradle configuration may run `fixcrlf` and touch the checkout.
- [x] Verify no JaCaMo authoritative source content changed as a side effect.

### Evidence output
- [x] Create/update a machine-readable baseline manifest with revisions, dirty-state fingerprint, dependency versions, frozen hashes, commands and test results.
- [x] Create a short human-readable baseline summary.
- [x] Record canonical-case source manifests.

## Phase gate

Proceed only when:
- [x] pre-existing changes are preserved;
- [x] all frozen artifacts match expected state or an approved newer state is documented;
- [x] baseline failures are reproducible/classified;
- [x] the JaCaMo test process cannot mutate the authoritative checkout unnoticed.

---

# PHASE 1 — Neutral contract, canonical identity, capability semantics
**Difficulty: CRITICAL — do early while reasoning quality is highest.**  
**Corresponds primarily to original Phase B plus identity/contract portions of D.**

## Goal

Create the transport-neutral semantic contract and canonical identity layer before any large Bridge or USE refactor.

## 1.1 Physical module/dependency spike

- [ ] Inspect current Maven/Gradle layout and select the smallest clean module boundary.
- [ ] Ensure the neutral contract can compile without USE, EMF, JaCaMo, Jason, CArtAgO, Moise, NPL or a production transport library.
- [ ] Ensure Bridge-side adapters can compile against the launched JaCaMo distribution.
- [ ] Ensure USE-side production semantic code can compile without JaCaMo runtime libraries.
- [ ] Record the decision in an ADR/evidence note.
- [ ] Do not select the production network transport yet.

## 1.2 Contract envelope

Implement and validate immutable/versioned contract structures containing at least:

- [ ] `schemaVersion`
- [ ] `messageType`
- [ ] `bridgeBuild`
- [ ] exact `distribution` fingerprint
- [ ] `projectKey`
- [ ] `modelRevision`
- [ ] `sessionId`
- [ ] `generation`
- [ ] `messageId`
- [ ] diagnostic `producedAt`
- [ ] `capabilities`
- [ ] `completeness`
- [ ] per-source `watermarks`
- [ ] `evidence`
- [ ] canonical `payloadDigest`

Unknown required fields, unsupported major schema versions, invalid digest, contradictory capability state => reject before materialization.

## 1.3 ModelSnapshot

Implement a deterministic neutral model containing at least:

- [ ] merged official project/configuration facts;
- [ ] canonical source URI/digest/kind and import/include provenance;
- [ ] agent declarations + instance policy + official Jason AST representation;
- [ ] workspace declarations and configured artifacts;
- [ ] official Moise organization graph/configured instances;
- [ ] `groupRoleCardinalities[]`;
- [ ] `parentSubGroupCardinalities[]`;
- [ ] descriptor/reflection facts only when officially evidenced;
- [ ] exact cross-dimensional relations;
- [ ] typed unresolved/unsupported facts;
- [ ] projection provenance.

## 1.4 RuntimeSnapshot

Implement a deterministic neutral runtime baseline containing at least:

- [ ] `snapshotId`;
- [ ] `modelRevision`;
- [ ] capture start/end metadata;
- [ ] start/end per-source watermarks;
- [ ] validation attempt count;
- [ ] agents / beliefs / goals / plans;
- [ ] workspaces / artifacts / operations / properties;
- [ ] group boards / scheme boards / role players;
- [ ] mission commitments;
- [ ] organizational-goal states;
- [ ] norm states/lifecycle;
- [ ] relation state;
- [ ] source completeness;
- [ ] projection status per runtime fact;
- [ ] deterministic state fingerprint.

## 1.5 RuntimeEvent

Implement a versioned event envelope containing at least:

- [ ] `eventId`
- [ ] `sessionId`
- [ ] `generation`
- [ ] `modelRevision`
- [ ] subsystem / source ID / monotonic `sourceSequence`
- [ ] diagnostic observed time
- [ ] closed/versioned event kind
- [ ] entity/relation ID
- [ ] `correlationId` / `causationId`
- [ ] `before` / `after` / payload
- [ ] watermark
- [ ] completeness
- [ ] evidence

## 1.6 Canonical identity

Implement/refactor:

- [ ] `BridgeEntityId`
- [ ] `BridgeRelationId`
- [ ] bridge-aware `SemanticId`
- [ ] reversible TraceIndex identity chain
- [ ] canonical encoding with Unicode/reserved-character normalization
- [ ] collision-safe USE display-name mapping
- [ ] model-revision-scoped immutable identity maps

Required identity cases:

- [ ] agent declaration vs live agent incarnation;
- [ ] workspace declaration vs runtime `WorkspaceId`;
- [ ] artifact declaration vs runtime `ArtifactId`;
- [ ] operation descriptor vs operation execution;
- [ ] property descriptor/instance;
- [ ] signal occurrence;
- [ ] OS definitions;
- [ ] group/scheme board incarnation;
- [ ] role-play relation;
- [ ] mission commitment;
- [ ] organizational-goal state;
- [ ] NPL norm instance;
- [ ] relation-scoped cardinality facts.

## 1.7 Capability and projection status

- [ ] Define `COMPLETE`, `PARTIAL`, `UNAVAILABLE` semantics.
- [ ] Define runtime projection status including `MATERIALIZED_FAITHFULLY`, `EVIDENCE_ONLY`, `UNAVAILABLE`.
- [ ] Preserve exact evidence for unsupported or evidence-only facts.
- [ ] Never encode “unknown” as a normal empty/default value.

## 1.8 Tests — must pass before Phase 2

- [ ] canonical serialization round-trip;
- [ ] stable digest under canonical ordering;
- [ ] malformed/oversized/deep payload rejection;
- [ ] schema major/minor compatibility rules;
- [ ] same ID + different payload rejection;
- [ ] duplicate event idempotency/conflict behavior;
- [ ] Unicode/reserved-char/sanitized-name collision tests;
- [ ] relation-cardinality multi-context test;
- [ ] contract classpath isolation test;
- [ ] serialize in one process/classpath and deserialize in an isolated consumer;
- [ ] source scan proving no live platform object exists in neutral DTOs.

## Phase gate

- [x] Neutral contract is independent of USE/EMF/platform libraries.
- [x] No live object/shared-classpath assumption exists.
- [x] Exact identity and relation-cardinality facts are preserved.
- [x] Frozen artifacts remain byte-identical.

---

# PHASE 2 — JaCaMo-side Bridge, official API adapters, consistent snapshot protocol
**Difficulty: CRITICAL — highest semantic/runtime risk.**  
**Corresponds primarily to original Phase C and Bridge-side portions of B/D.**

## Goal

Move source/runtime semantic authority beside JaCaMo and obtain all model/runtime facts through official APIs and official extension points.

## 2.1 JaCaMo insertion and lifecycle

Implement `JaCaMoBridgePlatform` through official `jacamo.platform.Platform`.

- [ ] `setJcmProject(JaCaMoProject)` receives the exact parsed project.
- [ ] `init` validates Bridge configuration/capabilities.
- [ ] `start` starts only the Bridge service/observers appropriate to readiness.
- [ ] `stop` detaches listeners and terminates bounded executors.
- [ ] Do not assume `Platform.start()` means all CArtAgO/Moise authorities are ready.
- [ ] Implement explicit adapter readiness/capability state.

Implement `BridgeAgArch`.

- [ ] attach before agent creation without breaking existing architecture order;
- [ ] support dynamically created agents;
- [ ] assign new incarnation on every `init`;
- [ ] observe/delegate `act(ActionExec)`;
- [ ] observe `actionExecuted(ActionExec)`;
- [ ] detach/close cleanly on stop;
- [ ] prove normal action behavior is unchanged.

## 2.2 Official project/JCM adapter

Use official:
- `JaCaMoProjectParser`
- `JaCaMoProject`
- launcher-equivalent default/source-path/directive/package setup.

Checklist:
- [ ] consume merged official `uses` result, not text-splice imports;
- [ ] preserve imported-source provenance;
- [ ] distinguish declaration/template, instance policy, and runtime incarnation;
- [ ] reproduce Jason source-path/directive environment;
- [ ] do not call unsafe launcher behavior blindly just to imitate `loadOnly`;
- [ ] parity-test Bridge load-only against launcher-configured official parsing.

## 2.3 Jason adapter

Use official Jason AST/runtime APIs.

- [ ] parse via official `Agent.parseAS` environment;
- [ ] preserve plan label, trigger, context, ordered body AST;
- [ ] distinguish external action vs internal action exactly;
- [ ] preserve source file/line provenance;
- [ ] keep full AST/provenance outside compact V2 fields where necessary;
- [ ] GoalListener support;
- [ ] Circumstance listener support;
- [ ] PlanLibrary listener support;
- [ ] snapshot belief base/goals/plan library for reconciliation;
- [ ] do not pretend every belief mutation has a complete event hook;
- [ ] mark event-coverage capability explicitly.

## 2.4 CArtAgO adapter

Use official environment/controller/descriptors/logger.

- [ ] enumerate root/local child workspaces;
- [ ] get controller per workspace;
- [ ] enumerate current agents/artifacts;
- [ ] obtain `ArtifactInfo`;
- [ ] revalidate `ArtifactInfo.getId()` against enumerated `ArtifactId` to close name-race;
- [ ] preserve runtime UUID identity;
- [ ] export operation name/arity exactly;
- [ ] use reflection only when a method-backed descriptor actually exists;
- [ ] represent unknown parameter names/types explicitly;
- [ ] snapshot observable property values after creation;
- [ ] capture create/dispose/join/quit/focus/unfocus/op lifecycle/percept-property events;
- [ ] never make Java source the runtime semantic authority;
- [ ] keep Java source extraction only as labelled optional provenance/enrichment if still useful.

## 2.5 Moise / ORA4MAS / NPL adapter

Static authority:
- [ ] load official `OS` object graph via `OS.loadOSFromURI`;
- [ ] traverse SS/FS/NS objects;
- [ ] preserve role hierarchy, groups, links, schemes, missions, goals, plans, norms;
- [ ] export exact relation-scoped role/subgroup cardinality tuples.

Runtime authority:
- [ ] discover real `OrgBoard`, `GroupBoard`, `SchemeBoard`;
- [ ] use board/artifact UUID + instance context;
- [ ] capture role players;
- [ ] capture mission commitments;
- [ ] capture organizational-goal states;
- [ ] capture responsible groups/group formation state when available;
- [ ] attach NPL listeners after interpreter creation;
- [ ] reconcile NPL active/fulfilled/unfulfilled/inactive collections;
- [ ] capture lifecycle events without translating them to OCL.

## 2.6 SnapshotCoordinator — critical correctness task

Implement buffer-first validated cuts:

1. [ ] attach all available listeners;
2. [ ] start bounded per-source buffers;
3. [ ] record start watermarks;
4. [ ] copy subsystem state into immutable DTOs;
5. [ ] record end watermarks;
6. [ ] re-enumerate identity/topology sets;
7. [ ] reject/retry on inconsistent topology, missed source, overflow, or model revision change;
8. [ ] publish capture interval + completeness + accepted per-source watermarks;
9. [ ] replay only events after accepted source watermarks.

Also:
- [ ] no global timestamp ordering;
- [ ] no silent drop on buffer overflow;
- [ ] bounded callback work;
- [ ] thread/listener leak test;
- [ ] model revision changes when a semantic descriptor required by MModel changes.

## 2.7 Hello World Bridge-side vertical proof

Before USE integration, prove the JaCaMo side on canonical Hello:

- [ ] canonical source hash verified;
- [ ] official JCM project loaded;
- [ ] official Jason programs/directives loaded;
- [ ] official Moise OS loaded;
- [ ] initialized artifact descriptors observed;
- [ ] focus/player/configuration relations preserved;
- [ ] authoritative RuntimeSnapshot captured;
- [ ] representative events captured;
- [ ] restart gives a new session;
- [ ] same-name recreation gives a new incarnation;
- [ ] Bridge stops cleanly.

## Phase gate

- [x] No JaCaMo core patch.
- [x] No custom parser is the accepted Bridge semantic authority.
- [x] Snapshot cut correctness is proven under concurrent mutation.
- [x] Exact source/runtime identity survives recreation.
- [x] Unavailable facts remain explicit.
- [x] Hello Bridge trace is reproducible.

---

# PHASE 3 — USE Bridge client, semantic adapter, mirror state machine, OCL capability gating, separate-JVM proof
**Difficulty: CRITICAL — central integration phase.**  
**Corresponds primarily to original Phase D.**

## Goal

Feed the existing V2/USE backend from Bridge contracts while preserving frozen mappings and proving real process independence.

## 3.1 BridgeClient and ContractValidator

- [ ] handshake schema/distribution/capabilities;
- [ ] reject unsupported distribution/schema before model materialization;
- [ ] fetch/validate ModelSnapshot;
- [ ] fetch/validate RuntimeSnapshot;
- [ ] subscribe to bounded RuntimeEvent stream;
- [ ] support acknowledgements/resume tokens at SPI level;
- [ ] reject stale session/generation/model revision before decoding a mutation;
- [ ] quarantine unknown entity/reference/event kind;
- [ ] expose readiness and stale/resync state.

## 3.2 NativeSemanticAdapter

- [ ] map `ModelSnapshot` into `JaCaMoSemanticModel`;
- [ ] preserve canonical evidence/provenance/completeness;
- [ ] preserve relation-scoped cardinality facts even when V2 projection is lossy;
- [ ] resolve only exact Bridge IDs or exact `binding.json` selectors;
- [ ] leave unresolved relations unresolved;
- [ ] never use name/arity/literal similarity.

## 3.3 Existing backend reuse

Keep/adapt:
- [ ] `TransformationPlanner`
- [ ] `VerificationSemanticLayer`
- [ ] `InstancePlanner`
- [ ] text backend
- [ ] `DirectUseBackend`
- [ ] structural mapping loader/validator
- [ ] projections/order evidence
- [ ] `TraceBuilder` / `TraceIndex`
- [ ] OCL/profile infrastructure
- [ ] verification engine

Requirements:
- [ ] `MModel` is built from the accepted model revision.
- [ ] Dynamic descriptor enrichment causes a new model revision, not ad-hoc mutation under the old one.
- [ ] Direct/text backend parity remains tested.
- [ ] frozen mapping rules do not change.

## 3.4 Runtime mirror state machine

Implement/refactor state machine:

```text
DISCONNECTED
 -> NEGOTIATING
 -> MODEL_SYNC
 -> SNAPSHOT_SYNC
 -> LIVE
 -> RESYNC_REQUIRED / STALE
 -> ...
```

Checklist:
- [ ] subscribe/buffer before snapshot acceptance;
- [ ] transactional state replacement;
- [ ] only same-session/generation/model-revision deltas can mutate;
- [ ] idempotent duplicate handling;
- [ ] conflicting duplicate => corruption/resync;
- [ ] gap/overflow/drift/unknown incarnation => stop mutation + resync;
- [ ] Bridge restart => new session + full handshake;
- [ ] USE restart => current model/snapshot then stream;
- [ ] model revision change => pause, compile new MModel, replace state, resume.

## 3.5 Runtime Mapping and mutation

- [ ] Keep frozen Runtime Mapping V2 target semantics.
- [ ] Add a pre-mapping Bridge contract adapter outside the frozen artifact.
- [ ] Never edit the frozen runtime mapping merely to mirror contract fields.
- [ ] Materialize only facts with a faithful target.
- [ ] Preserve evidence-only facts in trace/report/history without fabricated USE mutation.

## 3.6 OCL/verification capability gating

- [ ] Preserve OCL/NPL separation.
- [ ] Bind constraint evaluation to accepted model/state revision.
- [ ] A runtime dependency must be faithfully materialized before OCL evaluates it.
- [ ] Evidence-only/unavailable dependency => `INCONCLUSIVE`, `NOT_EVALUATED`, or capability-blocked.
- [ ] PRE only against an accepted pre-request state.
- [ ] POST only after completion/failure and required state watermark.
- [ ] Missing correlation/gap/stale source => inconclusive; do not bind by nearest name/time.
- [ ] Reports include session/generation/model revision/snapshot/event/correlation/constraint hash/capabilities.

## 3.7 Mandatory early separate-JVM smoke — MUST PASS

Run two distinct JVMs:

```text
Process A: JaCaMo + Bridge
Process B: USE + BridgeClient
```

Use only a **minimal/test transport** or process-neutral recorded/framed mechanism. Do not choose production transport yet.

Prove:
- [ ] ModelSnapshot crosses the boundary and builds MModel.
- [ ] RuntimeSnapshot crosses the boundary and transactionally initializes/replaces MSystemState.
- [ ] RuntimeEvent crosses and mutates state correctly.
- [ ] No live JaCaMo/Jason/CArtAgO/Moise object crosses the boundary.
- [ ] USE production semantic backend has no JaCaMo runtime classpath requirement.
- [ ] Bridge/JaCaMo restart creates new session.
- [ ] old-session/old-generation events are rejected.
- [ ] reconnect/resnapshot works.
- [ ] schema/version mismatch fails closed.
- [ ] deterministic recording/replay yields the same state/report fingerprint.

## Phase gate

Phase 3 is not complete until the separate-JVM smoke passes.  
**Do not proceed to semantic replacement claims if it does not.**

- [x] Minimal recorded and production TCP separate-JVM smoke passes.
- [x] The USE consumer JVM contains no JaCaMo/Jason/CArtAgO/Moise/NPL runtime jars.
- [x] Snapshot replacement and runtime-event mutation are revision/identity safe.
- [x] OCL capability gating fails closed for evidence-only or unavailable dependencies.
- [x] Frozen Runtime Mapping V2 remains byte-identical.

---

# PHASE 4 — Hello full vertical acceptance + shadow semantic/runtime comparison
**Difficulty: HIGH.**  
**Corresponds to remaining Hello work + original Phase E.**

## Goal

Prove that the Bridge path reaches USE correctly and compare it exhaustively with the legacy path without treating legacy output as semantic truth.

## Checklist

### Hello full path
- [x] official load;
- [x] ModelSnapshot;
- [x] NativeSemanticAdapter;
- [x] frozen V2 + Mapping V2.2;
- [x] USE MModel compile;
- [x] RuntimeSnapshot -> MSystemState;
- [x] RuntimeEvent application;
- [x] trace round-trip;
- [x] core + Hello OCL;
- [x] reconnect/resync;
- [x] deterministic replay.

### Shadow comparator
- [x] define canonical comparison form by IDs/provenance, not display names;
- [x] compare official source fact vs legacy fact vs Bridge fact vs V2 plan vs USE result;
- [x] classify every difference as adapter bug, legacy bug/limitation, unsupported fact, representation loss, or intentional correction;
- [x] preserve old goldens as historical evidence;
- [x] do not update golden automatically;
- [x] prove comparator mutation sensitivity;
- [x] prove deterministic diff output.

### Baseline failures
- [x] reproduce the three historical USE failures;
- [x] diagnose them independently;
- [ ] if one is closed by legitimate implementation change, record exact reason/evidence;
- [x] never weaken assertion or change digest without reviewed semantic justification.

## Phase gate

- [x] Hello official path works end to end.
- [x] Every legacy-vs-Bridge difference is classified.
- [x] No hidden legacy fallback on accepted Bridge path.
- [x] Frozen artifacts unchanged.
- [x] Phase-D separate-JVM gate remains green.

---

# PHASE 5 — Original Auction migration
**Difficulty: HIGH — dynamic cross-dimensional semantics.**  
**Corresponds to original Phase F.**

## Goal

Run the **original upstream Auction** through the generic Bridge/USE architecture and clearly separate supported runtime verification from unsupported original semantics.

## Checklist

### Source integrity
- [ ] verify original Auction source hashes;
- [ ] record exact dependency/distribution fingerprint;
- [ ] do not replace original source with the reduced/programmatic control.

### Static/model path
- [ ] official JCM load;
- [ ] official Jason program;
- [ ] official Moise OS graph;
- [ ] exact role/cardinality facts;
- [ ] mission/goal/plan/norm definitions;
- [ ] deterministic ModelSnapshot;
- [ ] V2/USE compile and trace.

### Runtime
- [ ] observe dynamic `AuctionArt` creation only when runtime evidence establishes it;
- [ ] distinguish artifact UUID incarnations;
- [ ] observe operation descriptors and property state;
- [ ] correlate Jason external action -> CArtAgO operation only with exact evidence;
- [ ] capture success/failure PRE/POST boundaries;
- [ ] capture role assignment;
- [ ] capture mission commitments;
- [ ] capture goal states;
- [ ] capture NPL norm lifecycle;
- [ ] explicitly mark runtime facts as faithful projection vs evidence-only.

### Verification
- [ ] evaluate only authored OCL whose required dependencies are materialized;
- [ ] no automatic deontic/norm translation;
- [ ] unsupported self-referencing plan/deadline/natural-language semantics remain explicit;
- [ ] reduced/programmatic Auction remains separately labelled.

### Resilience
- [ ] create/dispose/recreate test;
- [ ] kill/reconnect/resync;
- [ ] record/replay deterministic final fingerprint;
- [ ] stale old-generation events rejected.

## Phase gate

- [x] Original Auction **supported scope** passes the generic path.
- [x] No claim of “full original E2E” is made beyond evidence.
- [x] No case-specific production branch exists.
- [x] Hello remains green.

---

# PHASE 6 — Original House-Building migration and scale/resync stress
**Difficulty: HIGH — broadest canonical integration case.**  
**Corresponds to original Phase G.**

## Goal

Prove includes, instance expansion, large dynamic identity sets, organizational phases and resync behavior through the same generic architecture.

## Checklist

### Static/source
- [ ] verify canonical source/dependency manifests;
- [ ] use official local/external Jason include semantics;
- [ ] prove JCM `instances` expansion;
- [ ] distinguish declarations/templates from live incarnations;
- [ ] prove expected 22 live agent incarnations when official runtime evidence supports them;
- [ ] preserve role hierarchy, relation-scoped cardinality, links, missions, 13 organizational goals and plan operators from official OS objects.

### Runtime
- [ ] observe dynamic contracting artifacts exclusively through official APIs/events;
- [ ] prove the eight-auction lifecycle when actually observed;
- [ ] observe simulator `House` artifact when actually observed;
- [ ] capture winner-driven role changes;
- [ ] capture scheme/group boards and organizational phase transitions;
- [ ] preserve formation-compatibility limitation/provenance instead of inventing a V2 feature;
- [ ] handle dynamic model revision if descriptors appear.

### Scale/resync
- [ ] snapshot pre-contracting state;
- [ ] stream contracting phase;
- [ ] snapshot organization/build phase;
- [ ] disconnect during a phase transition;
- [ ] resync and prove old-generation messages cannot mutate replacement state;
- [ ] test bounded queues/event volume;
- [ ] measure runtime/memory/event metrics rather than inventing budgets.

### Genericity
- [ ] source scan for Hello/Auction/House constants in generic production code;
- [ ] parameterized integration gate feeds all three project roots through the same Bridge/adapter/planner/materializer/runtime/verification classes;
- [ ] mutation test proves case fixtures do not alter generic behavior.

## Phase gate

- [x] House supported scope passes.
- [x] Hello and Auction still pass.
- [x] No collapsed/stale identity.
- [x] No hard-coded case behavior.
- [x] All context-losing cardinality projection is explicitly labelled.

---

# PHASE 7 — Post-canonical semantic decision reviews
**Difficulty: HIGH reasoning, but NO frozen-resource implementation without approval.**  
**This phase resolves the two deliberately deferred semantic questions.**

## 7.1 Runtime Verification Projection Review

For each captured runtime fact:

- mission commitment;
- organizational-goal runtime state;
- norm instance/lifecycle;
- scheme/group runtime-instance context;
- any other evidence-only runtime fact discovered generically;

decide and document exactly one:

- [x] remain `EVIDENCE_ONLY`;
- [ ] target-only USE runtime projection outside frozen Runtime Mapping V2 is justified;
- [ ] a new metamodel version may be required.

For every decision record:
- [x] official source/API evidence;
- [x] case(s) that require it;
- [x] OCL/verification need;
- [x] current representation status;
- [x] identity/lifecycle requirements;
- [x] implementation/migration impact;
- [x] why evidence-only is or is not sufficient.

## 7.2 Cardinality V2.x/V3 review

Using actual Hello/Auction/House evidence:

- [x] list all relation-scoped role/subgroup cardinality occurrences;
- [x] identify any repeated endpoint used in multiple contexts with different bounds;
- [x] demonstrate whether frozen V2 loses required thesis semantics;
- [x] determine whether Bridge/provenance alone is sufficient;
- [ ] if not sufficient, prepare a **proposal only** for V2.x/V3 or target-side relation projection.

### Hard stop

- [x] Do **not** edit Ecore/Mapping/Runtime Mapping/OCL in this phase.
- [ ] If a new metamodel/mapping version is actually necessary, STOP and request explicit user approval with exact impact plan.

This phase does not block the architecture if evidence-only status satisfies the validated thesis scope.

## Phase gate

- [x] Every reviewed runtime-fact family has one explicit projection disposition.
- [x] Relation-scoped cardinality remains exact in Bridge/provenance.
- [x] Canonical evidence does not justify reopening frozen V2.
- [x] No V2.x/V3 hard-stop condition was reached.
- [x] Frozen Ecore, mappings and OCL remain unchanged.

---

# PHASE 8 — Production transport selection, security, backpressure and resilience
**Difficulty: HIGH, but dependency-bound to earlier phases.**  
**Corresponds to original Phase H.**

## Goal

Productionize the already-proved independent-process boundary without changing semantic contracts to fit a transport.

## Checklist

### Transport selection
- [x] benchmark realistic candidate transports;
- [x] evaluate payload size, latency, event rate, reconnect, packaging/classloader constraints;
- [x] evaluate local/remote deployment needs;
- [x] write an ADR explaining the selected production transport;
- [x] preserve `BridgeTransport` SPI.

### Security
- [x] localhost-only safe default;
- [ ] authenticated remote access if remote mode is supported;
- [ ] TLS/encryption where remote;
- [x] no native Java deserialization;
- [x] no remote class loading;
- [x] no arbitrary reflective invocation from payload;
- [x] size/depth/string/decompression limits;
- [x] redact secrets and sensitive paths from transport/evidence;
- [x] authorization remains read-only for this architecture.

### Reliability
- [x] bounded queues by count and bytes;
- [x] acknowledgements;
- [x] resume tokens;
- [x] explicit GAP on overflow;
- [x] idempotent replay;
- [x] restart either process;
- [x] network partition/failure injection;
- [x] schema mismatch;
- [x] auth failure;
- [x] malformed/fuzzed payloads;
- [x] resource/thread cleanup.

### Production separate-JVM E2E
- [x] Hello over selected transport;
- [x] Auction supported scope over selected transport;
- [x] House supported scope over selected transport;
- [x] compare semantics with Phase-D minimal boundary;
- [x] deterministic record/replay.

## Phase gate

- [x] Production transport preserves contract semantics.
- [x] No shared classpath/live object crossing.
- [x] Security/resilience gates pass.
- [x] No false exactly-once claim.
- [x] Frozen semantic artifacts unchanged.

---

# PHASE 9 — Switch production semantic authority to Bridge
**Difficulty: MEDIUM.**  
**Corresponds to original Phase I.**

## Goal

Make official Bridge input the default production authority only after all replacement gates pass.

## Checklist

- [x] `DefaultJaCaMoFacade` defaults to Bridge path.
- [x] Legacy path requires an explicit compatibility/deprecation flag.
- [x] No silent fallback.
- [x] Configuration validates endpoint/schema/distribution.
- [x] UI/status exposes readiness, capabilities, completeness, model revision, session/generation, stale/resync state.
- [x] Missing Bridge is a clear error, not permission to reconstruct semantics.
- [x] Upgrade configuration tested.
- [x] Rollback configuration tested.
- [x] Full canonical/regression suite passes.
- [x] User-visible documentation updated.

## Phase gate

- [x] Supported installations use Bridge authority by default.
- [x] Rollback rehearsal passes.
- [x] Legacy path still exists only as explicit compatibility mode.
- [x] No unsupported semantic claim is promoted.

---

# PHASE 10 — Deprecate/remove proven legacy authority
**Difficulty: MEDIUM/LOW, after replacement evidence.**  
**Corresponds to original Phase J.**

## Goal

Remove duplicate semantic authority only when each capability has a proven replacement.

Candidate legacy pieces include:
- `JcmLexer`
- `JcmSemanticParser`
- `JcmProjectLoader` semantic role
- `JasonSourceParser` legacy authority role
- `CartagoSourceExtractor` authority role
- `MoiseXmlParser`
- old in-process connector registry/composite
- legacy-only registrations/bootstrap

## Checklist

For **each** removal:
- [x] identify exact replacement component;
- [x] identify replacement tests/evidence;
- [x] verify no remaining production caller;
- [x] preserve historical fixture/golden evidence;
- [x] remove registration/call path before deleting implementation;
- [x] compile/test after the small slice;
- [x] update disposition/removal ledger;
- [x] scan for fallback reintroduction.

Do **not** remove:
- [x] semantic IR merely because input changed;
- [x] frozen mappings;
- [x] planners/materialization;
- [x] trace;
- [x] OCL/verification;
- [x] a legacy component whose capability is not yet replaced.

## Phase gate

- [x] No production semantics depend on custom parser/in-process authority.
- [x] No test was merely deleted to make removal pass.
- [x] One-to-one replacement evidence exists for every removed capability.
- [x] Historical evidence remains reproducible.

---

# PHASE 11 — Final regression, packaging, evidence and thesis handoff
**Difficulty: LOW/MEDIUM — mostly consolidation and reproducibility.**  
**Corresponds to original Phase K.**

## Goal

Produce a clean, auditable, reproducible release candidate and evidence package.

## Full regression checklist

- [x] focused changed-module tests;
- [x] Bridge unit suite;
- [x] API drift suite;
- [x] Bridge integration suite;
- [x] contract/client suite;
- [x] USE adapter/backend suite;
- [x] frozen V2 audit/hash suites;
- [x] full `mvn -B -pl use-plugin -am test`;
- [x] JaCaMo tests in content-isolated checkout/distribution;
- [x] canonical Hello;
- [x] original Auction supported scope;
- [x] House supported scope;
- [x] Phase-D minimal separate-JVM smoke;
- [x] Phase-8 production transport/security/resilience suite;
- [x] deterministic snapshot+event record/replay;
- [x] package install/uninstall smoke;
- [x] no listener/thread leak;
- [x] no unexpected skip/ignored failure;
- [x] source scan for case-specific core logic and silent fallback.

## Frozen-resource proof

- [x] Ecore V2 hash unchanged.
- [x] Mapping V2.2 hash unchanged.
- [x] Runtime Mapping V2 hash unchanged.
- [x] frozen OCL/profile/golden/manifest changes are absent unless separately reviewed and explicitly approved.
- [x] architecture migration did not silently reopen V2.

## Evidence package

Record at minimum:

- [x] Git revisions and dirty-state fingerprints;
- [x] dependency/version graphs;
- [x] contract/schema/build versions;
- [x] commands and test counts;
- [x] source/canonical-case hashes;
- [x] capability/completeness matrices;
- [x] ModelSnapshot/RuntimeSnapshot/event-log digests;
- [x] model/state/trace/report digests;
- [x] transport ADR;
- [x] security/failure-injection results;
- [x] performance measurements;
- [x] known limitations;
- [x] semantic diff register;
- [x] legacy removal ledger;
- [x] SBOM/package manifest;
- [x] reproducibility guide.

## Thesis claim discipline

Every capability claim must name:

```text
source revision + dependency versions
contract/model/snapshot revision
case/input hashes
capability/completeness set
test/evidence artifact
known limitations
```

Allowed scope labels include:
- static/load-only;
- initialized snapshot;
- runtime supported subset;
- original-case E2E where actually proven;
- unavailable/unsupported;
- representation loss;
- evidence-only.

Never promote a narrower result to a broader claim.

---

# 3. Component migration contract

Before editing a major existing component, consult `11-use-component-disposition.md`.

### Keep / keep with adapter

The following foundation should generally be preserved and adapted rather than rewritten:

- `JaCaMoSemanticModel`
- `ExactSemanticResolver`
- `TransformationPlanner`
- `VerificationSemanticLayer`
- `InstancePlanner`
- text backend
- `DirectUseBackend`
- `TraceBuilder` / `TraceIndex`
- structural mapping loader/validator
- verification/order projections
- Runtime Mapping loader/model
- `RuntimeMutationEngine`
- OCL/profile/generator infrastructure
- `ConstraintRegistry` / dependency index
- `DefaultVerificationService`
- `RuntimeVerificationEngine` / history verification
- diagnostics/reporting
- UI/action surface

### Refactor / replace boundary

Expected central changes:

- `StaticProjectImporter` -> Bridge-driven orchestration
- `SemanticResolver` -> canonical Bridge/reference validation
- `SemanticId` -> Bridge-aware structured identity
- `RuntimeMirrorService` -> session/generation/modelRevision/watermark aware
- runtime codecs/validators -> neutral contract validators
- `JasonRuntimeConnector` observation -> Bridge side
- `CartagoRuntimeConnector` observation -> Bridge side
- `MoiseRuntimeConnector` observation -> Bridge side
- `CompositeRuntimeConnector` -> BridgeClient/session
- `JcmSemanticParser` -> official JaCaMo project adapter
- legacy `JasonSourceParser` authority -> official launcher-configured Jason adapter
- `MoiseXmlParser` -> official Moise OS adapter
- `CartagoSourceExtractor` -> non-authoritative optional enrichment only

### Required new components

At minimum:

- neutral Bridge contract/schema
- `JaCaMoBridgePlatform`
- `BridgeAgArch`
- project/Jason/CArtAgO/Moise adapters
- `SnapshotCoordinator`
- `BridgeTransport` SPI
- USE `BridgeClient`
- `ContractValidator`
- `NativeSemanticAdapter`
- shadow semantic/runtime comparator

Physical package/module names may change if the dependency spike proves a better layout, but logical boundaries must not collapse.

---

# 4. Cross-cutting test requirements

These tests are not optional “later cleanup”; place them next to the relevant implementation.

## API drift

- [ ] exact supported JaCaMo distribution graph;
- [ ] Jason API signature probes;
- [ ] CArtAgO controller/logger/descriptor probes;
- [ ] Moise/ORA4MAS/NPL probes;
- [ ] unsupported distribution fails clearly;
- [ ] no silent parser fallback.

## Identity/recreation

- [ ] agent same-name recreation;
- [ ] artifact same-name recreation;
- [ ] board same-name recreation;
- [ ] old-generation event rejection;
- [ ] model revision rejection;
- [ ] sanitized-name collision;
- [ ] exact binding zero/one/multiple candidates.

## Snapshot/event races

- [ ] event during snapshot;
- [ ] topology change during snapshot;
- [ ] overflow while snapshotting;
- [ ] disconnect before acknowledgement;
- [ ] model descriptor appears mid-run;
- [ ] cross-source clocks disagree;
- [ ] unsupported event kind;
- [ ] mutation rollback.

## Semantic fidelity

- [ ] no fuzzy relation inference;
- [ ] action-operation correlation success and deliberately uncorrelated case;
- [ ] belief-property relation only from exact percept evidence;
- [ ] AGoal-OGoal only from explicit/correlated evidence;
- [ ] relation-scoped cardinality multi-context loss test;
- [ ] evidence-only runtime fact blocks dependent OCL;
- [ ] norm lifecycle never enters OCL registry implicitly.

---

# 5. STOP conditions — require user approval before continuing

Stop and present an evidence-backed proposal if any implementation requires:

1. changing Ecore V2;
2. changing Mapping V2.2 semantics/hashes;
3. changing Runtime Mapping V2 semantics/hashes;
4. changing frozen OCL/profile/golden/manifest solely to make new output pass;
5. patching/forking JaCaMo core;
6. introducing a case-specific production rule;
7. weakening a regression assertion;
8. using fuzzy resolution;
9. auto-translating Moise/NPL norm semantics into OCL;
10. deleting a legacy capability without proven replacement;
11. claiming full original Auction/House semantics beyond evidence;
12. a new semantic target that satisfies the formal V2-gap gate and truly requires a new metamodel version.

A blocker report must include:
- exact revision/version;
- minimal reproducer;
- affected capability/case;
- official API/source evidence;
- attempted solution A/B;
- why snapshot/provenance/evidence-only treatment cannot preserve soundness;
- exact proposed migration impact.

---

# 6. Final Definition of Done

The complete implementation is DONE only when all are true:

- [x] JaCaMo official objects are the default production semantic authority.
- [x] Bridge runs through official extension points without a JaCaMo core patch.
- [x] Neutral contract has no USE/EMF/live-platform/shared-classpath dependency.
- [x] Separate-JVM boundary is proven before and after production transport selection.
- [x] ModelSnapshot deterministically produces the accepted V2/USE MModel.
- [x] RuntimeSnapshot initializes authoritative runtime state from only faithfully projectable facts.
- [x] All other observable runtime facts remain explicitly evidence-only, not lost/fabricated.
- [x] RuntimeEvent application is identity/session/generation/modelRevision/watermark safe.
- [x] Gap/overflow/disconnect/drift trigger explicit stale/resync behavior.
- [x] Frozen V2/Mapping V2.2/Runtime Mapping V2 remain unchanged unless separately approved.
- [x] Relation-scoped Moise cardinality survives exactly in Bridge/provenance.
- [x] OCL only evaluates faithfully materialized runtime dependencies.
- [x] NPL normative semantics and OCL results remain separate.
- [x] Hello generic path passes.
- [x] Original Auction supported scope passes with limitations explicit.
- [x] House supported scope passes with limitations explicit.
- [x] No generic production code contains case-specific discovery/behavior.
- [x] Shadow differences are exhausted/classified.
- [x] Bridge is the default authority with zero silent fallback.
- [x] Legacy semantic authority is removed/deprecated only with proven replacement.
- [x] Production transport security/resilience gates pass.
- [x] Full regression has no unexpected failures/skips.
- [x] Final evidence/reproducibility package is complete.
- [x] Every thesis/public claim is scoped to evidence.

---

# 7. Required final handoff from the AI

At the end of this task, produce one concise but complete final report containing:

1. **Architecture status** — what is now authoritative and how data flows end to end.
2. **Phase checklist** — completed / incomplete / blocked.
3. **Files/modules added, refactored, deprecated, removed**.
4. **Exact tests run and results**.
5. **Known baseline failures** — which remain, which were legitimately closed, why.
6. **Frozen resource hashes before/after**.
7. **Hello/Auction/House capability matrix**.
8. **Runtime fact projection matrix** — `MATERIALIZED_FAITHFULLY` / `EVIDENCE_ONLY` / `UNAVAILABLE`.
9. **Relation-cardinality fidelity result**.
10. **Separate-JVM proof and production transport result**.
11. **Security/resilience result**.
12. **Legacy replacement/removal ledger**.
13. **Remaining limitations / deferred semantic decisions**.
14. **Reproduction commands**.
15. **Explicit statement whether any STOP condition was encountered**.

Do not declare the project complete because focused tests are green. Completion requires the global DoD above and reproducible evidence.

---

# 8. Execution priority summary

The intended difficulty gradient is:

```text
MANDATORY PREFLIGHT
    ↓
CRITICAL  Phase 1 — Contract + identity + capability model
CRITICAL  Phase 2 — Official Bridge + adapters + validated snapshot protocol
CRITICAL  Phase 3 — USE integration + runtime/OCL gating + separate-JVM proof
    ↓
HIGH      Phase 4 — Hello acceptance + shadow comparator
HIGH      Phase 5 — Original Auction
HIGH      Phase 6 — Original House-Building
HIGH      Phase 7 — Deferred semantic decision reviews
HIGH      Phase 8 — Production transport/security/resilience
    ↓
MEDIUM    Phase 9 — Switch production authority
MED/LOW   Phase 10 — Remove proven legacy authority
LOW/MED   Phase 11 — Final packaging/evidence/thesis handoff
```

This ordering deliberately gives the most architecture-sensitive and error-prone work to the earliest/highest-capability part of the single-AI session, while leaving mechanical cleanup, cut-over, packaging and evidence consolidation toward the end. Dependency gates must still be obeyed.
