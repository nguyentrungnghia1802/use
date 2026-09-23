# V2-ORDER-001 — ordered eOpposite representation decision

Status: SEMANTIC_FIDELITY_BLOCKER. This is independent of the resolved Phase 29
scheduling decision and independent of old V1 consumer/path failures.

## Verified conflict

The supplied Ecore declares both directions of Agent.roles/Role.agents,
Agent.workspaces/Workspace.agents and Agent.artifacts/Artifact.agents as ordered
many-valued EReferences (Ecore's default `ordered=true`). Mapping 2.1 uses one
canonical ordered USE association per pair plus a non-emitting opposite alias:
R021/R008, R022/R032, R023/R035. Both source directions are authoritative.

Schema, exact source coverage and structural USE compilation pass. But valid
Ecore instances can carry independent orders at the two ends which that canonical
USE association plus current `createLink`/`!insert` operations cannot reproduce.

`V2OppositeOrderingAuditTest` creates native EMF instances, populates both sides,
performs legal EList moves and validates every source object with Diagnostician.
It then compiles the actual supplied mapping into USE, supplies source attributes,
and tests every insertion permutation. Every resulting target passes structural
validation. The counterexample is not malformed input or a compiler failure.

For two source objects a0/a1 and two target objects b0/b1:

| Navigation | Required source order | Required link precedence |
|---|---|---|
| a0.forward | b0, b1 | e00 < e01 |
| a1.forward | b1, b0 | e11 < e10 |
| b0.reverse | a1, a0 | e10 < e00 |
| b1.reverse | a0, a1 | e01 < e11 |

Together: `e00 < e01 < e11 < e10 < e00`, a cycle. No global link insertion order
can satisfy all four independently ordered lists. Native EMF permits these moves
without changing opposite membership. Current USE stores ordered link/cache sets
using LinkedHashSet and updates both ends on insertion; navigation returns their
insertion order (`MLinkSet.createInternalLinkSetImpl/add`,
`MSystemState.getNavigableObjects`). Deleting and reinserting links still gives a
global order of last insertions and does not remove this contradiction.

Fresh executable evidence: each of R021/R022/R023 tests 24 insertion orders;
**0/24** preserve the cyclic source orders, while **2/24** preserve the positive
control through the same APIs. In total 72 negative permutations are checked.
Full observations are in `ordered-opposite-counterexample.json`.

## Decision required before consumer representation is fixed

The source Ecore remains unchanged. Do not silently make the references unordered,
choose one authoritative direction, or sort by name. Do not claim compile PASS
means exact instance fidelity. Two viable contracts need different implementations:

1. **Preserve the full V2 ordered domain with an explicit target representation.**
   Introduce a reviewed target-only order projection (independent source-side and
   target-side ranks per relation occurrence, exact membership/uniqueness and
   source trace). Specify how source navigation becomes an ordered query using
   those ranks. Membership associations alone must not be exposed as if they
   carried the full source order. Update Mapping/schema, source-to-target navigation,
   TransformationPlan, text/direct materialization, trace/OCL bindings and later
   runtime updates through the controlled evolution loop. This preserves Ecore
   semantics but expands the target mapping beyond its current one-association
   representation. An alternative full-fidelity design is an approved USE API/SOIL
   extension supporting independent end reorder; that changes the tested USE target.
2. **Approve a bounded representable subset.** Keep the current target shape,
   derive a precedence graph from all authoritative source lists, and emit only
   when a deterministic topological order exists. Otherwise reject with a located
   `V2_ORDER_NOT_REPRESENTABLE` diagnostic before mutation. This never loses order
   silently, but explicitly excludes some valid V2 instances and therefore narrows
   the supported domain. A schema compile PASS must remain distinct from this
   instance-level gate.

Neither narrowing the supported domain nor adding a new ordering projection/API
is silently selected. This choice affects IR order representation, target bindings
and OCL navigation, so the dependent Phase 32–35 design is held here. Independent
Phase 31 schema/source/compiler/projection audits remain reviewable evidence.

## Required follow-up gates for either approved choice

- Preserve the native EMF counterexample and positive control.
- Prove both authoritative navigation orders or prove explicit pre-mutation rejection.
- Test deterministic output, link/alias deduplication, text/direct parity and traces.
- Test order-only updates, reconnect and authoritative resync at runtime migration.
- Reconcile Mapping compatibility, hashes and docs after the semantic choice;
  no hash-only or golden-only fix.
