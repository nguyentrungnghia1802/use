# Phase 44 final V2 freeze and release evidence

Status: **DONE — V2 FROZEN RELEASE CANDIDATE**. Metamodel V2, Structural
Mapping V2 and Runtime Mapping V2 are `FROZEN`. All final semantic, module,
reactor, relocated-checkout, installed-package and reproducibility gates pass with
zero failures, errors or skipped correctness tests. No Git release tag is published.

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

Frozen source revision: `7c435addcc91d7bbe7928953a11778f1b8e32d73`.

| Gate | Result |
| --- | ---: |
| Pre-freeze candidate audit | 37/37 PASS |
| Focused final semantic/runtime gate | 154/154 PASS |
| Full plugin module verify | 231/231 PASS |
| Full clean reactor | 374/374 PASS |
| Relocated no-hardlink clean reactor | 374/374 PASS |
| Final compatibility/package/installed smoke | 8/8 PASS |

Every count has zero failures, errors and skips. The focused gate covers native
Ecore, Mapping/schema/projection, parser fixtures, transformation/golden outputs,
OCL, trace/binding, Runtime Mapping, synthetic and real pinned connectors, Auction,
CounterTeam, genericity, hardening, determinism and package contracts. The clean
reactor covers `use-core` (13), `use-gui` (130) and `use-plugin` (231).

The relocated checkout builds the same frozen source at a different absolute path.
Its normalized `auction.use`, `auction.cmd`, generated OCL, OCL provenance, trace
and diagnostics are byte-identical to the primary checkout. Runtime UUIDs,
timestamps and performance durations remain deliberately run-specific.

The final release archive contains exactly 30 declared entries. Every entry is
compared byte-for-byte with its source; the installed smoke loads the JAR and both
frozen mapping contracts without Maven's test classpath.

| Artifact | SHA-256 |
| --- | --- |
| `use-jacamo-plugin-1.0.1-v2-frozen.zip` | `a3d9d1e9ec42275b04cbad8872214916d1a121c0755ca370f4efc09c5be7aaa0` |
| `use-plugin-1.0.1.jar` | `5cf0c3cc3c58da98d8eec223b9db67da82c26ebaf36318a5c7fb4f13b0ebac16` |
| `v2-final-evidence.zip` | `31ae1c76b23df754400473ebebc61d342cc4c84fa16014e80205d240185763f5` |
| `v2-final-evidence.json` | `e8e485534efdcc3058871f084b675fba48798dd9b0462796dff46e0236ef4b2a` |

The durable bundle is
`docs/project/evidence/v2-final/v2-final-evidence.zip` with its SHA-256 sidecar.
It contains 112 source/test/evidence artifacts: exact frozen resources, generated
`.use`/`.cmd`, OCL/provenance, trace, event logs, verification reports,
reconnect/resync evidence, both case summaries, compatibility/release manifests,
JUnit XML, all Phase 44 logs, release ZIP and JAR. Machine-readable gate records
are `phase44-*-gate.json` / `phase44-*-verify.json`, with the freeze and relocation
diff records alongside this document.

## Explicit boundaries

- Original Auction plan/deadline equivalence remains explicitly unsupported.
- General Moise deontic lifecycle is not automatically converted to OCL.
- Runtime UUIDs, timestamps and observed performance durations are run-specific.
- Unsupported or unbound runtime entities cannot mutate USE.
- The frozen candidate is not a published Git tag.
