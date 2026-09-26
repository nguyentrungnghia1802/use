# Jason API inventory

## Inspected baseline and authority

- JaCaMo revision `3866858a7ebf6be85d9199c13a09cf4bfb8191be` resolves `io.github.jason-lang:jason-interpreter:3.3.2` and the matching parser/runtime implementation.
- The inspected Jason 3.3.2 source archive has SHA-256 `2f4e450d1fb116a9bf2d4ab7eddb2265b82fa4a303a97a64570c67710e2d1ce0`.
- Authoritative packages are `jason.asSyntax`, `jason.asSemantics`, `jason.bb`, `jason.runtime` and `jason.architecture`.
- The USE build currently pins Jason 3.3.0. The Bridge must compile against the JaCaMo distribution version and publish that fingerprint; compatibility with 3.3.0 must not be assumed.

## Official parse path and semantic objects

| Concern | Exact 3.3.2 API | What can be exported | Limit |
|---|---|---|---|
| Parse an ASL program | `jason.asSemantics.Agent.parseAS(Reader,String)` and `parseAS(InputStream,String)`; internally the generated `jason.asSyntax.parser.as2j` parser is used | Official initial beliefs, initial goals, rules and plan library | Parsing one source is not the whole JaCaMo directive/source-path lifecycle |
| Agent program | `jason.asSemantics.Agent`; `getInitialBels()`, `getInitialGoals()`, `getPL()` | Program-level semantic graph | This object becomes mutable when used by a running agent |
| Plan library | `jason.pl.PlanLibrary implements Iterable<Plan>`; `getPlans()`, `add(...)`, `remove(...)`, `addListener(jason.pl.PlanLibraryListener)` | Ordered plans, labels and plan-add/remove events | Listener covers the plan library, not every agent-state dimension |
| Plan | `jason.asSyntax.Plan`; `getLabel()`, `getTrigger()`, `getContext()`, `getBody()` | Label, exact trigger/context/body AST | V2 stores selected expressions textually; preserve canonical AST text plus provenance |
| Trigger | `jason.asSyntax.Trigger`; operator/type/literal accessors | Belief/goal signal, add/delete operator and literal | Do not infer CArtAgO `Signal` merely from the literal name |
| Plan body | `jason.asSyntax.PlanBody`; `getBodyType()`, `getBodyTerm()`, `getBodyNext()` | Ordered linked body and exact term type | Flattening loses nested structure unless order/provenance are preserved |
| Beliefs/rules | `jason.asSyntax.Literal`, `Rule`; runtime `Agent.getBB()` -> `jason.bb.BeliefBase` | Initial and live beliefs/rules | Runtime belief-base mutations have no inspected universal `BeliefBaseListener` |
| Goals | initial goals from `getInitialGoals()`; goal runtime through `TransitionSystem`/`Circumstance` | Achievement/test form and runtime lifecycle evidence | A textual match to a Moise goal is not an exact cross-reference |
| External action | `PlanBody.BodyType.action` and its body term; runtime `jason.asSemantics.ActionExec` | Name, arity, arguments, execution correlation | Mapping to a CArtAgO operation requires exact operation-selection evidence |
| Internal action | `PlanBody.BodyType.internalAction` | Exact distinction from external action | Must not be projected as an artifact operation |
| Source location | `jason.asSyntax.SourceInfo`; source file and begin/end source line | File/line provenance | No reliable source column in the inspected API |

This answers the parser question directly: a faithful Jason semantic model can be built from official Jason objects without a custom ASL grammar. A Bridge adapter still has to normalize the official AST into the neutral contract and then into V2; “no custom parser” does not mean “no adapter.”

## Includes and directives

Jason directives are part of the official parse environment. `jason.asSyntax.directives.Include` uses the configured source path; JaCaMo sets that path in `JaCaMoLauncher.init` after `JaCaMoProject.setupDefault()` and `registerDirectives()`. Therefore:

1. Bridge load-only mode must install the same directive registry and source paths as the launcher;
2. it must export included-source provenance, not hide it behind the including file;
3. it must not mask directives and then reconstruct their meaning, as the current `JasonSourceParser` does for selected inputs;
4. a parity test must compare Bridge load-only output with the official launcher-configured parse.

Unknown/custom directives are a capability failure for the affected source, not permission to guess their expansion.

## Runtime object and hook inventory

| Runtime fact | Exact API/hook | Availability | Bridge policy |
|---|---|---|---|
| Agent names/snapshot | `jason.runtime.RuntimeServices.getAgentsName()` and `getAgentSnapshot(String)` | Runtime | Enumerate authoritative live agents and expose unsupported snapshot fields explicitly |
| Agent program/state | `Agent.getPL()`, `getBB()`, `getTS()` | After agent initialization/runtime | Copy into immutable DTOs; never serialize live Jason objects |
| Circumstance events | `Circumstance.addEventListener(CircumstanceListener)` | Runtime | Emit relevant event additions/removals with per-agent ordering |
| Goal lifecycle | `TransitionSystem.addGoalListener(GoalListener)` | Runtime | Emit goal events with the agent incarnation and source sequence |
| Plan library changes | `PlanLibrary.addListener(PlanLibraryListener)` | Initialized/runtime | Update model revision or publish a semantic-model delta |
| External action start | custom `AgArch.act(ActionExec)` in the official architecture chain | Runtime | Generate correlation ID, record requested action, delegate to next architecture |
| External action completion | `AgArch.actionExecuted(ActionExec)` | Runtime | Close the same correlation and export success/failure evidence |
| Agent incarnation | Bridge `AgArch.init()`/`stop()` plus `RuntimeServices.registerDefaultAgArch(...)` | Runtime | Issue a new incarnation ID on every creation; do not identify by local name alone |
| Dynamic agents | `RuntimeServices.registerDefaultAgArch(String)` applies an architecture to subsequently created agents | Runtime | Covers dynamic agents without patching Jason core; prove with integration tests |

`JaCaMoRuntimeServices` already calls `registerDefaultAgArch(...)` for JaCaMo architectures. A Bridge platform can register its own architecture before agent creation, or add it to declared agent parameters. Exact ordering and coexistence with existing architectures are an implementation-phase test gate.

## Listener gaps and fallback

- No generic, public, source-audited listener was found for every `BeliefBase` mutation. `CircumstanceListener` observes belief update events that enter the reasoning cycle, but it is not evidence that every mutation path is captured.
- Runtime snapshots must therefore reconcile the belief base, goals and plan library. Event coverage is reported as a capability bitmap per dimension.
- If an event is not observable, the Bridge emits neither a fabricated event nor an inferred timestamp. It advances state only through the next snapshot/resync and marks the interval incomplete.
- Jason provides per-agent ordering; it does not provide a global order shared with CArtAgO and Moise.

## V2 mapping consequences

| Jason concept | V2 target | Fidelity |
|---|---|---|
| Agent program/incarnation | `Agent` | Exact program fields; runtime incarnation is retained in trace/Bridge identity because V2 has no incarnation field |
| `Plan`/`Trigger`/body steps | `Plan`, `Event`, `Action` | Exact selected structure; full AST retained as contract/provenance where V2 is intentionally compact |
| Initial/live literal | `Belief` | Exact literal text; `Belief.property` only with explicit CArtAgO evidence |
| Achievement/test goal | `AGoal` | Exact kind/literal; `organizationalGoal` only with explicit Moise evidence |
| External/internal body term | `Action.kind` | Exact distinction |
| Action to operation | `Action.operation` | `API_NOT_EXPOSED` from Jason alone; exact binding/runtime correlation is required |
| Trigger to signal | `Event.signal` | `API_NOT_EXPOSED` from Jason alone; exact percept provenance is required |

## Verdict

| Capability | Verdict |
|---|---|
| Official ASL semantic parsing | `FULLY_FEASIBLE` |
| Launcher-equivalent include/directive resolution | `FEASIBLE_WITH_ADAPTER` |
| Static V2 Agent/Plan/Event/Action/Belief/AGoal input | `FEASIBLE_WITH_ADAPTER` |
| Goal/action/plan runtime events | `FEASIBLE_WITH_ADAPTER` |
| Complete belief mutation event stream | `FEASIBLE_WITH_LIMITATION` |
| Dynamic agent coverage without core patch | `FEASIBLE_WITH_ADAPTER` |

## Required implementation probes

1. Parse official Hello, Auction and House-Building ASL with launcher-equivalent include configuration and compare canonical AST digests.
2. Prove that Bridge `AgArch` ordering preserves normal action execution and receives start/completion for local and CArtAgO actions.
3. Create/kill/recreate an agent with the same name and prove distinct incarnation IDs.
4. Mutate beliefs through representative paths, record listener coverage, and prove snapshot reconciliation for uncovered paths.
5. Fail closed on a directive that cannot be resolved by the configured Jason environment.
