# Goal/Task semantics and Auction setup verification

Date: 2026-11-07. Status: implementation handoff; all acceptance gates are open.
Workspace: `D:\_CODE_BANK\Project_\08_Thesis`.

## 1. Objective and authorized scope

Implement correct Unknown / Complete / Incomplete semantics for Goals and Tasks
throughout USE–JaCaMo. Integrate the initial agent goal do_auction and synchronize
all affected files in `use/use-plugin/examples/auction`. Update generic system
components as necessary. Deliver a working installation, not only a UI relabeling
or another design-only export.

Read AGENTS.md, this plan and use/plan.md. This plan replaces the previous root
plan.md by explicit user request. Other plans and reports are historical context,
not proof that the new gates are complete. Work on main in the sibling use/jacamo
repos. Preserve unrelated changes, runtime logs, replays and user originals.
No destructive reset/clean, branch deletion or unsolicited refactoring.

Product text, code comments and new project documentation must be English.
Explain results in Vietnamese. Ask before using Computer Use.

## 2. Sources and precedence

Read the actual sources before editing:

- `use/use-plugin/Core/On Checking the Conformance between iStar and BPMN_261012_173041_1.pdf`:
  page 3, Definition 1/Table I/temporal Goal semantics; page 4, scenario completion
  and conformance/risk. Inspect Table I visually.
- `use/use-plugin/Core/auction/auction_csl.drawio` and the applicable iStar diagram.
- User-supplied original:
  `C:\Users\NTNghia\OneDrive\Desktop\Thesis\CSL\Core\auction\auction_istar_final_fixed.drawio`.
- Reviewed Desktop exports: `auction_istar_with_do_auction.drawio`,
  `auction_with_do_auction.istar`, `auction_do_auction_notes.md`.
  These are design exports, not runtime integration. Their DO_create_scheme /
  DO_configure_scheme IDs must be reconciled with the final short names below.
- External executable source:
  `D:\_CODE_BANK\Project_\vnu-sme-lab\jacamo\examples\auction\auction.jcm`,
  `src/agt/auction_capabilities.asl`, `src/org/auction-os.xml`, and
  `src/env/auction_env/AuctionArtifact.java`.
- Current example files, mapping, native engines and relevant bridge code.

Draw.io is authoritative for the authored graph and source-faithful modeling.
This request explicitly requires correct result semantics. Reconcile paper,
diagram and implementation in a written decision matrix; do not treat an old
implementation behavior as a theoretical definition. Preserve unrelated diagram
structure. Do not invent dependencies, assertions or expected results for tests.

## 3. Semantic contract

Keep distinct: native intentional value, applicability/activation, evidence
quality, execution phase, and checkpoint/global conformance verdict.

### 3.1 Tasks

For applicable, evaluable leaf Task conditions, page 3 specifies:

| PRE | POST | Paper value | Visible Result |
| --- | --- | --- | --- |
| false | any | ? / undetermined | Unknown |
| true | true | top / satisfied | Complete |
| true | false | bottom / violated | Incomplete |

Unobserved, undefined or unavailable evidence is not known false. A POST not yet
applicable because the invocation has not reached its exit is not a captured false
POST. Specify exactly when PRE and POST are evaluated and which source cuts they
use. Do not evaluate future POST prematurely and repair the resulting failure by
relabeling it Unknown. A genuinely false applicable POST must remain false.

The paper gives Tasks no temporal interpretation. Current TaskMarking remembers
preSeen/postSeen and emits PENDING; the UI maps PENDING to Incomplete. Audit that
latching. Distinguish current marking from retained execution certificates:
immutable history must not impersonate current truth or a different invocation.

### 3.2 Goals

Derive Goal values from their conditions, refinement/dependency results and Goal
type, not from whether an arbitrary plan returned. Document initial values,
activation episodes and all transitions for Achieve, Maintain, Sustain and None.
The paper says Achieve retains satisfaction, Maintain retains violation, Sustain
records loss after satisfaction, and None follows the current propagated value.
Retention must be scoped to the correct run/execution/player/activation episode.

A not-yet-activated element without an evaluable result is Unknown. Do not assert
that every future Goal is Incomplete, or that every not-yet-executed Goal must be
Unknown: its known condition or child results may determine it. Keep additional
runtime observation/activation rules explicit rather than attributing them to
the paper without evidence.

Use three-valued propagation. AND requires all children satisfied; a known false
child can falsify it. OR needs one satisfied child; all false is false; mixed
unresolved cases stay unknown. Follow model quantifiers and temporal rules.

### 3.3 Lifecycle versus Result

Goal/Task Result values must be exactly Unknown, Complete, Incomplete. Show
Waiting, Executing, Succeeded, Failed or Cancelled separately in details/tooltips
when evidenced; these are execution phases, not extra Result values. Keep Not
assigned as assignment information. No assignment is not a failed execution.
Preserve raw markings, diagnostics and evidence in audit.

Incomplete is not automatically a whole-MAS violation. Progress, local failure
and an applicable mandatory contract violation have different consequences.
Unknown future progress can be normal. Unknown evidence for an applicable
mandatory check retains the hold. Never map all PENDING blindly to either
Incomplete or Unknown without the applicability/evaluation context.

## 4. Required checks, local failures and global verdict

Audit/correct the division between:
1. CSL/OCL invariants and independently mandatory operation/authorization contracts;
2. per-occurrence Goal/Task values and local execution failures;
3. selected root requirements and scoped conformance at applicable boundaries.

Current VerifiedCheckpointInventory promotes relevant TASK_POST failures and
terminal failures from all occurrences into a whole-MAS failed check. Current
summary/history can pin All to VIOLATION. Fix inappropriate promotion of supporting
branch failures rather than merely hiding their rows.

Preserve Auction D_bid's ANY semantics and O_root per execution. A participant's
.fail stays an unsuccessful local Task and never becomes an actual successful bid.
It does not alone prove O_bid/O_root failed when another assigned participant
satisfies the declared dependency. An independent applicable mandatory operation
contract can still fail and must retain that effect. All authored OCL invariants
remain required. A condition determining its own Task's truth is not automatically
an additional universal safety requirement. Classify and trace these roles.

For All, genuine selected-requirement/mandatory-contract violations in an execution
remain globally visible. Supporting failures and future Goals must not be promoted
to those violations. Keep history without poisoning the global verdict with a
mislabeled progress/supporting event. Completed-scenario conformance/risk is
different from current progress and from proof over every possible MAS schedule.

Do not force a desired verdict, suppress a false predicate or weaken case assertions
to obtain green tests. Manual Continue preserves the original failure/result/state
and records review, not PASS. Document any unresolved semantic ambiguity honestly.

## 5. Auction do_auction model and runtime integration

Keep the reviewed auction_capabilities capability Role viewpoint. Keep Goal name
`do_auction`. Use two short Task names close to ASL:

| Task | ASL top-level body segment | Source meaning |
| --- | --- | --- |
| createScheme | [0,3), original lines 4-6 | concat name; resolve joined workspace; createScheme |
| commitMission | [3,10), original lines 7-13 | set Id/Service; my_name/setOwner; focus; addScheme; commitMission |

Both Tasks AND-refine do_auction. commitMission names its entire declared segment's
final milestone, not only one operation. Keep full source descriptions. Recheck
ordinals against the current parsed AST; they are not universal constants and
must never be case constants in generic production code.

do_auction means successful session setup, NOT completion of start/bid/decide.
It does not adopt auctioneer automatically. Keep it distinct from C_start and
per-scheme organisational O_root.

Retain the SchemeBoard Resource with meaningful needed-by links to configuration
and start preparation. SchemeBoard is the coordination artifact. AuctionArtifact
is the separate passive domain artifact created later by start. Do not turn
SchemeBoard into an Auction CSL Entity. AND is not an ordering edge; needed-by is
not proof of creation/completion. Add no social dependency solely for sequencing.

### 5.1 Initial demand and scopes

Read initial goal declarations and actual activation/invocation evidence
generically. Only players with do_auction demand execute setup, not every player
sharing the ASL library. No production constants for Bob, counts, auction IDs,
case names or winners.

Register expected initial requests before schemes/domain artifacts exist. Use
stable run/player/goal-invocation identity, then correlate returned scheme and
later domain identity. Separate concurrent requests of the same agent and their
evidence. Do not fabricate an Auction self at setup entry. Extend generic scope,
binding and resource handling for pre-scheme/pre-domain phases where necessary.

Cover failure before creation and partial setup failure. Missing execution must
not erase an expected initial request and yield false Completed. Known pending
demand is not evidence loss. Reconcile expected requests, created schemes and
completed executions from source evidence, not fixed counts.

Keep O_root requirements scoped to organisational executions and setup requirements
to the relevant initial invocation. Do not blindly append a global requirement
ID and demand it in every unrelated context. Handle linking/grouping without
mixing histories or changing stable execution identities.

### 5.2 Contracts and authority

Callback step.enabled/step.succeeded alone do not establish every semantic setup
fact. Capture exact scheme type/identity, matching Id/Service, owner, responsible
group and caller commitment through qualified source evidence. Missing required
bindings remain Unknown. Do not invent observer fields or write OCL over absent
schema properties. Add generic evidence support when genuinely required.

Read `use/use-plugin/verification/auction-setup-semantics-20261107/REPORT.md`.
The native experiment showed another agent can focus/start the domain artifact,
while Moise rejects organisational start achievement without commitment. Existing
Idle/Opened contracts check effects, not caller authority. Distinguish physical
success from mission/demand compliance; do not impose creator==starter universally.
Add no unsolicited security enforcement or modifications to the source business
behavior. Use actual performer/scheme/mission evidence for declared requirements.

Preserve <=300 participant and exactly one auctioneer per GROUP, not per auction
ID. Preserve the user's temporary disabling of WithinDeadline. Do not silently
re-enable/remove it or disable any other check.

## 6. Checkpoints and UI

Keep the existing source-driven Task-exit/failure and relevant operation/lifecycle
policy. Do not stop at every plan entry or every ASL deed. Record lifecycle events
without additional holds when that suffices. Setup boundaries use generic policy.

Preserve supported whole-MAS admission/draining, in-flight work, asynchronous
producers, same-agent concurrent intentions and logical clocks/deadlines. Jason
Pause alone is insufficient. No waits for USE/network under artifact, listener,
scheduler or Swing locks. Keep exact-cut authenticated/idempotent release and
fail-closed timeout, disconnect and evidence-loss handling.

States are coherent source snapshots, not numbers invented by Swing or every CSL
attribute update. Keep same-CSL cuts with real intentional progress; group only
diagnostic-only duplicates in presentation and preserve raw audit.

Preserve the Goals/Tasks split, per-agent/occurrence details, All view, colored
True/False and top-right status with contextual Continue. Correct the completion
badge consistently: no Completed from Unknown/PENDING, missing initial requests,
partial populations or stale runs. Completion is scoped progress, not MAS exit.
Avoid additional EDT disk-history reads, state jumps or selection loss.

## 7. Inspect/update these areas as needed

- Native GoalMarking, TaskMarking, GoalTaskStatus, propagation and adapters in
  use/use-jacamo-semantic-engine. Preserve unrelated original/BPMN APIs. If a
  versioned JaCaMo profile is needed, admit/document it explicitly and ensure the
  main owned workflow actually uses the corrected behavior.
- RuntimeEngineSessionOwner, result adapters, invocation/certificate lifecycle,
  GenericBindingPlan, XML demand/scope/resource handling and initial-goal intake.
- VerifiedCheckpointInventory/Service, scoped requirements, replay/journal schema
  compatibility. Identify legacy semantics; do not silently reinterpret old replay.
- PresentationResults, LiveGoalSummary, ObservedConformancePresenter/Panel,
  ExecutionScopeViews, VerificationStatusBadge, details and diagnostics.
- Generic Jason observation/instrumentation and sibling JaCaMo boundary code only
  where the new evidence needs it. Preserve existing suspension guarantees.
- All affected files in use/use-plugin/examples/auction: .istar, .csl, mapping XML,
  .ocl, .mrv, README, manifests/provenance/checksums and relevant copies. Preserve
  executable case behavior; no editing the .fail branch or winner logic for tests.
- Relevant Core/Draw.io/export and workflow provenance if affected. Preserve user
  originals; use separate Desktop exports where a diagram update is necessary.

## 8. Implementation/evidence gates

All boxes start open. Tick only after current executable evidence exists.

- [x] M01: Paper/model/runtime decision matrix, initial/activation rules, PRE/POST
  applicability and requirement/supporting classification. Record current bugs
  and concrete intended before/after results.
- [x] M02: Correct generic evaluation/propagation and UI vocabulary together.
  Focused truth tables, temporal episodes, Unknown/undefined, AND/OR, lifecycle and
  retained-certificate regressions; no assertion changes that merely bless a bug.
- [x] M03: Correct inventory/global verdict. Prove local supporting versus mandatory
  failure, scoped roots, All aggregation, genuine historical violations and
  repeated exact-token review retaining original evidence.
- [x] M04: Integrate do_auction demand/scopes/source boundaries/resources/setup
  evidence. Prove concurrent initial requests on one agent, unassigned players,
  failure before creation, partial setup and exact later scheme correlation.
- [x] M05: Synchronize the affected Auction bundle and diagrams/provenance. Check
  parser/model/contracts/bindings/schema/hash consistency. Do not invent children
  for validator compliance; use the documented admitted profile for the existing
  single-Task C_decide realization.
- [x] M06: UI/replay/history acceptance with relevant tests and offscreen Swing.
  No state churn, duplicate semantic rows, lost selections or cross-player/scope
  evidence. Distinguish offscreen from manual desktop acceptance.
- [x] M07: Live external unchanged Auction using native engines and exact-cut
  presentation. Preserve natural failures; no random seeding, patched winners,
  injected verdicts or relaxed assertions. Use isolated negative fixtures for
  reproducible branches and label their scope. Do not expect every participant
  Task to Complete when the requirement is ANY. Cover startup/setup, independent
  executions, final requirements, genuine holds and review preservation.
- [x] M08: Final coherent build into use/use-assembly/target/new. Recheck installed
  Prepare + Import and a bounded relevant installed Auction run. Verify actual
  loaded consumer/producer JAR paths, hashes, classpaths, contract/engine class
  parity and provenance.
- [x] M09: Review the diff, preserve unrelated work, write English evidence/report
  and honest limits, commit/push changes on existing main remotes, and deliver
  current launcher and concise Vietnamese results. No branch deletion or empty
  commits for unchanged repos.

Use only relevant tests and the external Auction live case here; no full clean
verify or unrelated live studies. Generic non-Auction fixtures should demonstrate
case independence. Ask before any Computer Use needed for deeper acceptance.

## 9. Build and delivery safeguards

Read use/use-plugin/tools/RUNNABLE-PACKAGE.md and use the validated updater
use/use-plugin/tools/update-runnable-plugin.py. Build with -pl use-plugin -am.
A plugin-only package previously shaded a stale Maven-cache contract, causing
NoClassDefFoundError for JasonAgentInventory. Check missing/mismatched dependency
classes before updating the runnable package. Preserve work/replays and backups.

Do not restore user-deleted legacy Core inputs merely for an old assembly
descriptor. Report incremental installation limitations accurately. If Windows
locks JARs, finish build/checks first, then ask the user to close USE or authorize
normal closure. Do not kill an unrelated user process.

Deliver:
`D:\_CODE_BANK\Project_\08_Thesis\use\use-assembly\target\new\bin\start_use.bat`.

This root handoff is outside the sibling source repos. Keep it at the requested
path. Place an implementation evidence/plan copy inside the changed repo when
needed for versioned delivery; do not overwrite historical reports or claim their
old test receipts prove these new gates.
