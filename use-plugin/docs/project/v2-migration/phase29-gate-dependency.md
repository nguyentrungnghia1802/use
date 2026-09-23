# Phase 29 gate dependency requiring a plan decision

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

The decision changes the user's explicit phase/gate ordering, so it is requested
instead of silently marking a failing phase complete. While pending, independent
P29 capture/inventory and audit-tool tests are completed. P30+ generated inventory
files are preliminary intake evidence only, not acceptance of those later phases.
