# Source checkpoint selection review

Status: proposal only. No source selection, barrier, clock, verification engine,
release command or semantic assertion is changed by the UI work on 2026-11-04.

## What the current implementation does

There are two separate sequences: source checkpoints that control JaCaMo, and
presentation states that compact the accepted history. A displayed Entity change
does not itself issue a source pause command.

* `CheckpointPolicySelection.rules` derives `TASK_POST` rules from admitted
  `jason.plan-boundaries.v1` observations. It selects the **last configured Task
  segment in each plan**, requires a native Task and an artifact class, and excludes
  entries with an intentionally missing artifact at entry. It does not select
  every mapped Task or every ASL statement.
* `JasonPlanInstrumentation` verifies the original plan AST against its admitted
  source. A segment has `first` (inclusive) and `after` (exclusive) body ordinals.
  It inserts ENABLED before that segment and SUCCEEDED after it. Segments partition
  the admitted body; the last `after` equals the source body's length. The proof
  retains the source URI, plan semantic ID, line, original/executable AST and hashes.
* `VerifiedCheckpointBridge.beforeObservation` ignores ENABLED for the current
  POST selection. Matching terminal phase observations synchronously request a
  checkpoint, using the source step ID and exact occurrence/phase. The call closes
  admission without waiting for the verifier. This is distinct from displaying a
  completed plan, receiving a belief change, or parsing a line number.
* For classes without a selected Task terminal, admitted CArtAgO operation
  completion can select `OPERATION_POST`. Classes with a Task terminal use
  `OPERATION_FAILURE` to catch operation failures without creating a second normal
  POST checkpoint for the same operation. Internal source failures remain evidence.
* `VerifiedCheckpointCoordinator.trigger/freeze` records the requested event,
  settles admitted work through `ExecutionBarrier`, freezes supported logical time,
  then publishes the actual frozen snapshot and drained boundaries. Concurrent
  admitted work can advance after the requested event but before quiescence.
* `BusinessTimeline` and `CheckpointTimeline` group accepted cuts for UI navigation.
  CSL Entity changes and qualified Task progress can affect that grouping. These
  display groups are not the source policy and cannot authorize a release.

Relevant source paths are under `use-plugin/.../codegrounded/runtime/CheckpointPolicySelection.java`,
`use-plugin/modules/bridge-jacamo/.../adapter/JasonPlanInstrumentation.java`,
`use-plugin/modules/bridge-jacamo/.../adapter/VerifiedCheckpointBridge.java`, and the sibling
`jacamo/src/main/java/jacamo/checkpoint/VerifiedCheckpointCoordinator.java`.

## Recommended next design

Use explicit, versioned **semantic source boundaries** as the main sequence. Prefer
POST of each *selected mapped Task segment*, with a plan completion boundary when
the authored mapping maps a complete plan to a Goal. Use operation completion only
as a declared fallback for behavior without an ASL Task binding. Belief/percept
changes and changed Entity fields should be evidence captured at those boundaries;
they should not automatically become additional control boundaries.

| Boundary | Source identity | Intended use |
|---|---|---|
| Task POST | source hash, plan semantic ID, AST segment, mapped Task, invocation | Verify the effect of a meaningful completed action, including actions that leave CSL values unchanged |
| Task PRE, explicitly selected | same identity plus enabled occurrence | Verify applicable entry contracts when the required bindings already exist |
| Plan completion / failure | admitted plan ID and intention episode | Verify a mapped Goal after its plan returns, or retain an execution failure |
| Operation POST / failure | artifact incarnation, operation and call occurrence | Cover admitted domain actions outside a mapped ASL segment |
| Final settled boundary | run, source watermark and termination evidence | Verify settled obligations/results before the final result is published |

Selecting all reasoning cycles would be too fine grained: a cycle may select an
event, evaluate a context, suspend an intention, or execute unrelated bookkeeping.
Likewise one plan can contain several meaningful Tasks, wait for a subgoal or
operation, and fail or be retried. A plan name alone does not identify an invocation
or establish a complete snapshot. Timers and normative producers still require the
existing platform barrier; a Jason callback alone cannot stop the whole MAS.

The policy should select boundaries through mapping/source identities, never case
names, agent names, winners or UI State numbers. A source State should show its
Task/plan/operation reason and requested versus actual cut. Repeated CSL values
can legitimately appear at distinct source boundaries when Task/Goal evidence has
changed; the UI can explain that without removing the underlying checkpoint.

Keep the existing exact-token approval, failed/unknown hold, clock and review
protocol. `All` is a read-only cumulative result across executions; it does not
turn manual review into PASS or change the source admission guarantee.

## Acceptance required before implementation can be claimed

Prove intermediate Task boundaries, equal-CSL/different-Task cuts, nested goals,
failure/retry, shared agents, operation fallback deduplication, in-flight drain,
logical timers/deadlines, unavailable entry evidence, and final completion. Preserve
the original native assertions and compare requested and actual frozen cuts.

This proposal is intentionally not implemented in this change.
