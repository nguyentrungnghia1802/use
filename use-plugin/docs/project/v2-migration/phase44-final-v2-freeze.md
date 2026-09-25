# Phase 44 final V2 freeze and release evidence

Status: **FREEZE CANDIDATE IMPLEMENTED; FINAL VALIDATION OPEN**. Metamodel V2,
Structural Mapping V2 and Runtime Mapping V2 are marked `FROZEN` only after the
Phase 36-43 semantic, mirror, genericity, packaging and hardening gates passed.
Phase 44 is not DONE until every final gate and reproducibility check below passes.

## Freeze decision

The pre-freeze candidate gate runs native Ecore and Mapping audits, materialization,
trace/binding, Runtime Mapping, OCL, multi-case genericity and hardening. It passes
37/37 tests with no failures, errors or skips. No semantic blocker or expected
near-term metamodel change remains. The V2 Minor-Change Fast Path was not invoked:
the Metamodel V2 bytes are unchanged from the accepted Phase 29-43 baseline.

`release/v2-freeze-manifest.json` is the single authoritative final contract. The
former working manifest is retained only under `release/historical/`; there is no
second active canonical baseline and no fallback to V1.

| Frozen contract | Version/status | SHA-256 |
| --- | --- | --- |
| Metamodel V2 | V2 / FROZEN | `4ae51638a078f0a933982844063993084f17d420694ac8a868d95b9df063c35c` |
| Structural Mapping V2 | 2.2.0 / FROZEN | `fc03b90cf0729260747bfeffa6a6cd463eefd2259c0c3cd60ed22bd140ec48b1` |
| Structural Mapping schema | 2.2.0 | `e9f555ad2e19cbf8382ff4c17d44179d3b47a13d03ee9a58c2b491f99b72c919` |
| Runtime Mapping V2 | 2.0.0 / FROZEN | `5b2c00f052010fb35a71eb7f50ae4650f8a09c7647332b47a7199c12cbf8a5f0` |
| Runtime Mapping schema | 3.0.0 | `6ffd20d1acf03e9112997ae3c95d8b3f4c44aab96f412e49ec8763fbb6e3203a` |
| Core OCL V2 | 2.0.0 | `a725c1fe0524f2b477327b58c54d317df693a0fbe37d3773bbe44372beeefab5` |
| Verification profile V2 | 2.0.0 | `91c85d42080b56754e8c2f5e9d37127e80c1483fdb4d71f2d61df7eab58fcab2` |

The structural mapping hash changed because reviewed freeze status and provenance
replaced obsolete pre-migration text. Runtime mapping changed only from `WORKING`
to `FROZEN` and to the resulting exact Structural Mapping hash; its schema accepts
that final status. `tools/v2_freeze_diff.py` rejects any other delta. Ecore,
class/enum/attribute/reference/inheritance rules, verification projections,
runtime selectors/actions, target bindings and target-only order projection remain
semantically unchanged.

## Fail-closed active contract

`ActiveBaseline` validates packaged or checkout Ecore, Mapping and schema bytes
against the unified freeze manifest before returning the V2 mapping. Runtime
Mapping loading independently validates its mapping/schema hashes and its exact
Structural Mapping target fingerprint. Missing, stale, mutated or historical input
fails closed; neither selector silently loads V1.

The consistency chain remains:

`Metamodel V2 -> Structural Mapping V2 -> Semantic IR -> transformation ->`
`trace/binding -> OCL -> Runtime Mapping V2 -> MSystemState -> verification ->`
`tests/evidence`.

Every runtime mutation still requires exact `RuntimeKey -> SemanticId V2 -> USE`
target resolution. Unknown or unbound entities are quarantined. JaCaMo remains the
execution authority; USE mirrors and verifies it. Moise deontic semantics are not
automatically converted to OCL. The independent target-only ordering contract is
unchanged.

## Final gates and evidence

The final record will include focused metamodel/mapping/parser/transformation/OCL/
trace/runtime gates, full module and reactor verification, a clean relocated clone,
installed-package smoke, exact package inventory, zero skipped correctness tests,
and `target/v2-final-evidence.zip` with its manifest and SHA-256 sidecar.

These gates are still open in this candidate revision. Their commands, tested
revision, counts, release ZIP/JAR hashes and durable bundle location will replace
this paragraph only after all checks pass.

## Explicit boundaries

- Original Auction plan/deadline equivalence remains explicitly unsupported.
- General Moise deontic lifecycle is not automatically converted to OCL.
- Runtime UUIDs, timestamps and observed performance durations are run-specific.
- Unsupported or unbound runtime entities cannot mutate USE.
- The frozen candidate is not a published Git tag.
