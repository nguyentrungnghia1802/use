# iStar executable capability crosswalk

The original grammar, AST, model, builder, factory and validator are retained.
The runtime owner must not mutate their M1 definition: `OwnedGoalModel` records
the complete stored definition and fails its owner fence if any mutable original
field changes. It is an ownership guard, not a claim that original POJOs are
immutable. UI adapters must receive read-only views, not these model references.

| Syntax/model feature | Original implementation | New-profile frontend |
|---|---|---|
| Agent / Role | real concrete Actor implementations | accept, original validator + unique/indexed actor/element identities |
| Goal Achieve/Maintain/Sustain/None and condition | real Goal/GoalType/typed contract | accept; native domain OCL compilation remains a separate gate |
| Task pre/post or legacy ocl clause | real distinct typed slots; legacy clause is post | preserve original slots; empty authored body rejected before visitor drops it |
| AND / OR | real refinement hierarchy and validation | accept original structural semantics; runtime domain gates in Phase 7 |
| Social dependency and dependum | real Dependency composition-owned intentional element | preserve; dependum is not an ordinary actor-owned element |
| In-body actor association | stored Association and original validator | representation retained; no claim that it executes actor generalization live |
| Resource / Quality / Help/Hurt / qualification / needed-by | model representations exist | retain M1; live feature must pass the runtime capability gate, not automatically yield a verdict |
| Model/actor header text, description, explicit condLang | builder/model discard fields | UNSUPPORTED with token span/raw text, including explicit lang OCL |
| Declared activation | parsed but absent from Goal field implementation | UNSUPPORTED before builder; GoalActivationGraph is a different implemented feature |
| forall / pick | grammar alternatives; builder dispatch absent | UNSUPPORTED before the original visitor can throw |
| Top-level actor association | grammar accepts, builder ignores | UNSUPPORTED before builder |

## Parity diagnostic normalization

The original `GoalTypeRefinementPolicy.allowedChildren()` returns a Set. Its
rendering in one validator error can order enum values differently in independent
ClassLoaders because enum hash identity differs. The initial D parity run captured
this nonsemantic difference (`[MAINTAIN, NONE]` vs `[NONE, MAINTAIN]`). The oracle
normalizes only the `allowed child goal types are [...]` suffix, validating its
members against actual GoalType values. Error wording/IDs/list order, full M1
fields and AST remain exact. Raw diagnostics from both loaders are retained next
to normalized parity evidence. No production/core code or expected verdict is
changed for this formatting difference.

Additional admission checks for duplicate actor IDs/stale lookup indexes close
ambiguous ownership in programmatically mutated models; they do not patch the
original validator or reinterpret a verification failure.
