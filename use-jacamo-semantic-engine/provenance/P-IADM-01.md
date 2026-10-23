# P-IADM-01 — Versioned derived AND dependee admission

Upstream: `6f3e73801239fd8bfb94ca8d04ba531b903c0ac9`, exact frozen bytes in
`src/test/resources/oracle/pinned-original-core.zip`. Original FQCNs and GPL
attribution remain. `record_admission_patch.py` reverses only the registered
dispatch/admission block and checks all other original source against the ZIP.
Target/source/patch/API hashes are recorded in `META-INF/use4goal-provenance.json`.

Default `IStarCompiler.compile` and `GoalModelValidator.validate` remain strict.
The explicit `DERIVED_AND_RESOURCE_V1` engine profile selects
`DERIVED_AND_DEPENDEE_V1` at compiler, frontend, M1 ownership and runtime admission.
All original validation stages still run. This is a documented extension rather
than a claim that the upstream validator admits the extended model.

Only a condition-free Achieve Goal with one same-owner AND refinement containing
at least two children may be a refined **dependee**. A refined depender, Task
parent, OR parent, direct condition, foreign owner, activation syntax, cycle or
graph exceeding the bounds remains rejected. The extension preflights the graph
before the original recursive validators. Source endpoints are never detached,
reversed or redirected. The three Auction endpoints remain `A_start`, `A_decide`
and `P_bid`; strict validation still reports the three original errors.

Runtime admission also retains the explicit complete cast, exact context matcher,
unique-instance and original owner-fallback guards. `participates-in` requires
the explicit matcher to prove every admitted cast pair shares context. It is
metadata, not an extra propagation rule. `is-a` and qualifications remain outside
the admitted runtime subset. The plugin must supply that context proof from
runtime identity, not from actor names.

For `needed-by`, the adapter gates each original `TaskMarking.update` on an
external resource input keyed by the **exact task occurrence + source resource**,
with the same episode and a nonblank evidence reference. Missing, partial,
unavailable, false or foreign-episode evidence defers the update; it is never
converted to a failed Task. No relation is silently delegated to an upstream
algorithm that does not implement it. The producer/plugin is responsible for
proving artifact incarnation and binding; the neutral engine cannot attest those
facts from a string. Direct forged keys are rejected atomically.

The original topology drops associations/needed-by from the instantiated model;
the adapter retains their frozen M1 resource requirements separately and applies
the gate at input. It does not modify that topology algorithm. Definition
ownership guards both the source and instantiated graphs. Extended checkpoint
identity includes the profile, so history cannot seed a strict session. Same
policy cast rebuilds may retain only exact structured keys and episodes.

`GoalMarking`, `TaskMarking`, `IStarMarking`, `IStarPropagation` and original
dependency propagation remain byte-identical. Focused tests assert strict parity
against the pinned original, opt-in positive/negative admission, exact Auction
endpoint preservation, resource gates, context rejection, history ownership,
AND/dependency calls, and registered-source scope. Tests with explicit Boolean
fixtures prove this component, not live source observations. Production import,
scoped player expansion, replay versioning and live acceptance remain later gates
in `use-plugin/docs/agent/task.md`.
