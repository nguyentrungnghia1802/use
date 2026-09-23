# Phase 29 gate dependency � staged migration authorized

No phase is declared complete. No production semantics or canonical input changed.

The active instructions simultaneously require:

1. `task.md` P29.3: production import/transformation defaults to V2, one active
   metamodel and mapping, no silent V1 fallback.
2. `task.md` Phase 31: actual USE compiler/mutation gate for the supplied mapping.
3. `task.md` Phase 33–35: migrate IR, parsers, MappingLoader and TransformationPlan,
   enums/opposites/defaults, initial state, text/direct parity and Auction V2.
4. `agent.md` §16: "full feasible suite passes", merge, post-merge regression and
   push before a phase is DONE.
5. The user's instruction: execute phases sequentially without skipping dependencies
   or acceptance gates.

Fresh pre-migration regression is already red after the supplied V1 relocation:
306 executed / 3 failures / 73 errors. Restoring old V1 paths could restore some
historical execution, but would not satisfy P29.3's V2 production acceptance.
Switching filenames without migrating enum/opposite/default/IR contracts cannot
produce a correct V2 pipeline. Therefore executable P29 acceptance depends on
work currently scheduled in later phases.

Two concrete schedules preserve all correctness gates:

- Staged migration: perform P29–34 work sequentially, leave phase acceptance OPEN,
  then close their integration gates after P35 and a passing full regression.
  No early merge/release or V2 completion claim.
- Strict phase closure: explicitly move the minimum P31–35 dependency work into
  Phase 29, achieve production V2 and full regression there, then resume the
  remaining audit/hardening tasks in their original order.

User decision (2026-09-23): choose staged migration. Execute Phase 29 through 35 in
order; do not pull production migration into Phase 29. Mark independently completed
tasks only. Keep consumer-dependent acceptance/regression OPEN and rerun it after
downstream migration. Baseline failures do not authorize rollback of V2. This
dependency is resolved as a scheduling decision, not a semantic blocker.

Phase 31 update: this scheduling authorization remains in force. A separate
executable semantic counterexample is recorded in
[V2-ORDER-001](phase31-ordered-opposite-decision.md). It requires an ordering
representation/domain decision, not a scheduling change or V2 rollback.
Latest regression retains the same 76 failing identities (317 executed including
new audits); see phase31-regression.json.
