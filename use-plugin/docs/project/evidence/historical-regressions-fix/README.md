# Historical Regression Root-Cause Fix — 2026-09-28

## Verdict

The three historical USE–JaCaMo regressions are fixed in production extraction
code on branch `fix/historical-regressions`. The unchanged regression tests now
pass, the full Maven reactor passes, reviewed Auction golden digests still match,
and the original Hello World, Auction and House-Building projects pass the same
separate-JVM live Bridge gate within the previously approved supported scope.

No test assertion, golden, OCL/profile, frozen Ecore V2, Mapping V2.2, Runtime
Mapping V2, JaCaMo source/core, or case-study input was changed. No commit, push,
or merge was performed for this fix.

## Repository guard and preflight

- Working repository: `D:\_CODE_BANK\Project_\08_Thesis\use`.
- Fix branch: `fix/historical-regressions`.
- Fix base: `747c6ad4645629894115482a40a1cd250072135d`.
- During preflight, an external operation advanced `main` from `3c9094f3` to
  `747c6ad4`, pushed that acceptance commit, and removed the first temporary fix
  branch. This task did not create or push that commit; it recreated the requested
  fix branch at the new repository state and preserved it.
- Pre-existing untracked root `target/` remains unmodified and uncommitted.
- Required `agent.md`, `task.md`, root `report.md`, regression tests and existing
  evidence were read before changing production code.
- The pre-fix full baseline was 292 tests: 289 PASS, exactly the three failures
  below, 0 errors, 0 skipped.
- A detached Phase-44 control at `7c435addcc91d7bbe7928953a11778f1b8e32d73`
  passed all three relevant classes, 9/9. The regression entered with
  `40b696b479139ef847766ea897fcc2b7ff208fcb` (`feat: switch JaCaMo semantics to
  production Bridge`).

## RED reproduction and root causes

| Regression | Before-fix observation | Root cause and category |
|---|---|---|
| `ConstraintClosureTest.distinctV2OwnersWithJavaHashCollisionsKeepDistinctConstraintIdentities` | Expected two source constraints, got zero at line 25. | Jason's official AST normalizes an explicitly authored universally true plan context (`: true`) to a null context. The new parser treated null as “no authored context”, so `ConstraintExtractor` received no context to translate. This was deterministic source-semantic loss in the Jason extraction adapter, not a constraint-ID hash collision. Once the explicit lexical context is restored, the unchanged test proves that two dependencies with equal Java `hashCode()` values still produce two distinct canonical constraint IDs. |
| `GoldenPipelineTest.auctionArtifactsMatchReviewedGoldenDigests` | `auction.cmd`: expected `a630838c...`, got `61dd7135...`; `diagnostics.txt`: expected `477b0b93...`, got `b34a96c8...`; `trace.json`: expected `4db0faea...`, got `e48a4427...`. | JCM initial beliefs used owner path `MAS/<agent>/belief`, while ASL beliefs used `MAS/<agent>`, so one semantic belief could become two objects/commands. When duplicates did merge, JCM provenance remained primary instead of the more precise ASL origin. The official AST also canonicalized lexical spelling used by diagnostics/trace. These were identity/provenance/lexical-evidence regressions in extraction; the reviewed golden was not obsolete. |
| `InstanceMaterializationTest.textAndDirectBackendsShareOneVerificationPlanAndPassInitialValidation` | The same `auction.cmd` digest mismatch (`a630838c...` expected, `61dd7135...` actual) failed before direct materialization assertions ran. | Both backends already consumed one `InstancePlan`; the upstream JCM/ASL belief-owner mismatch changed that shared plan. This was not a text-vs-direct backend divergence. Canonicalizing the source identity restores one deterministic plan for both backends. |

The failing inputs were the existing synthetic Java-hash-collision fixture and the
existing LF-normalized Auction fixture. No test-only values or case names were
introduced into production code.

## Production repair and invariants

Only these production files implement the repair:

1. `ExtractionContext.java`
   - adds deterministic primary-provenance promotion without dropping secondary
     provenance;
2. `JcmSemanticParser.java`
   - gives JCM initial beliefs the same canonical owner path as equivalent ASL
     beliefs; goals retain their distinct goal owner path;
3. `JasonSourceParser.java`
   - recovers any explicitly authored top-level plan context when the official AST
     normalizes it away;
   - retains official Jason AST objects as semantic authority;
   - uses bounded lexical source recovery only for authored spelling/provenance;
   - promotes an ASL declaration to primary provenance when it merges with a JCM
     declaration;
   - normalizes only the statement terminator required by the existing evidence
     contract.

The maintained invariants are:

- semantic identity is the canonical owner/kind/local identity, never a Java hash;
- equivalent JCM and ASL declarations materialize one semantic element;
- distinct owners remain distinct even if Java hashes collide;
- text and direct backends consume the same deterministic verification plan;
- official AST semantics and lexical source evidence are not conflated;
- no case-specific branch, fallback, or hard-coded collision value exists in
  production code.

## GREEN verification

### Exact regressions

| Gate | Result |
|---|---|
| Collision regression method | 1/1 PASS |
| `GoldenPipelineTest` | 1/1 PASS |
| `InstanceMaterializationTest` | 2/2 PASS |
| Three control classes at Phase-44 revision | 9/9 PASS |

The three test files and all their assertions are byte-unchanged from `HEAD`.

### Full Maven gates

| Command | Result |
|---|---|
| `mvn -B -pl use-plugin -am test` | 292/292 PASS; 0 failures, errors or skips |
| `mvn -B -pl use-plugin verify` | 266/266 PASS: 261 unit/component plus 5 packaging/integration tests |
| `mvn -B verify` | 427/427 PASS: contract 8, official adapter 10, core 13, GUI 130, plugin 266; 0 failures, errors or skips |

The full-reactor count is independently reconstructed from fresh Surefire/Failsafe
XML reports timestamped 13:48–13:50 local time. Four older plugin report files were
excluded from that freshness window rather than double-counted.

### Reviewed Auction artifacts

| Artifact | Current SHA-256 | Reviewed SHA-256 | Result |
|---|---|---|---|
| `auction.use` | `dd8fdf8d3a822be770d20e5bae5253142db9897932f7975c5c2effd1184e300c` | same | MATCH |
| `auction.cmd` | `a630838c653adfaf86f59e7b8d66e37ff04c0c19464589c962921d07a25cdff1` | same | MATCH |
| `diagnostics.txt` | `477b0b9336f65e73cc39faebe5e324d203dc935211e0cddb4da69bc93521800c` | same | MATCH |
| `trace.json` | `4db0faea1c2379f03459ca82307e4b31f3b4edbfc6d3be89ef9da46075c95edd` | same | MATCH |

No golden file was edited.

### Packaging

- Shaded plugin JAR:
  `c68030334d6f15fd83476a79f7c6833da69064652461457a2c3b7667ef47ae1a`.
- Release ZIP:
  `7c4da8a8ce8bbc5db39fde98988881d329b0f74a2bcc2e12266deb3be9dc5175`.
- The ZIP sidecar declares that exact digest.
- GUI staging, legacy-authority packaging and release-package integration gates all
  passed as part of the module/full-reactor verification.

## Live original-case regression

All three runs used a real JaCaMo producer JVM and a separate USE consumer JVM with
JaCaMo runtime JARs absent from the consumer. Each stayed live across reconnect,
materialized a valid USE state and returned 28/28 authored OCL PASS.

| Case and evidence run | Runtime/USE result | Evidence SHA-256 (`summary`, `consumer`, `events`) |
|---|---|---|
| Hello `live-hello/20260928-135125` | 836 reconnect facts; 162 events; 1,095 objects; 1,641 links; state `c3b6fa09...` | `bd2e33f4...`, `607e3e8a...`, `2ef3975d...` |
| Auction `live-auction/20260928-135243` | 483 reconnect facts; 111 events; 994 objects; 1,432 links; state `91b3c1af...` | `141cd1ce...`, `9ed885fb...`, `1e87ee21...` |
| House `live-house/20260928-135336` | 957 reconnect facts; 174 events; 1,024 objects; 1,448 links; state `7f2d5e82...` | `3efae31b...`, `403a245c...`, `9d81fe55...` |

House is a supported-scope Bridge/OCL PASS only. Its reconnect snapshot explicitly
records `bhsch/house_built = NOT_SATISFIED`; this evidence does not claim complete
original House termination.

## Immutability proof

Frozen resources match the pre-fix values:

| Resource | SHA-256 |
|---|---|
| Ecore V2 | `4AE51638A078F0A933982844063993084F17D420694AC8A868D95B9DF063C35C` |
| Mapping V2.2 | `FC03B90CF0729260747BFEFFA6A6CD463EEFD2259C0C3CD60ED22BD140EC48B1` |
| Runtime Mapping V2 | `5B2C00F052010FB35A71EB7F50AE4650F8A09C7647332B47A7199C12CBF8A5F0` |
| Freeze manifest | `7965AEF8EAE099E398DE50CB30A65F1B6C5B9207A6500DB678825340BCA73C77` |

JaCaMo remained at `3866858a7ebf6be85d9199c13a09cf4bfb8191be`. The exact
pre/post fingerprints are:

| Scope | Files | SHA-256 |
|---|---:|---|
| Complete tracked/unignored JaCaMo worktree | 761 | `2B3825C99F53F3EDF3E9B20F6EE7A16909386D1B24A6BABCDD5F051459B64DC1` |
| Hello tutorial subtree | 56 | `4E088324651D8A63DB12C9B004E4A8A82B438394FB9A322769F917ADE55331AD` |
| Auction subtree | 12 | `61CE6572D24A8C6F26590AC3A702255B9F1DAA5F67C2E73E5E24E50DBE03D540` |
| House subtree | 37 | `95A196CADCA77134D54945A0C47266FD2CA4C8E8C6921261DA95A504A98CA3E3` |

The same 45 pre-existing line-ending-only dirty JaCaMo paths remain. No JaCaMo
file was added, removed, or changed by this task.

## Scans and STOP result

- `git diff --check`: PASS.
- Changed production source contains no `Aa`, `BB`, Hello, Auction, House, or
  `hashCode()` literal/branch.
- The only `fallback` identifiers found are generic pre-existing source-spelling
  fallback parameters; no authority fallback or case-specific behavior was added.
- Changed-file classification contains production extraction code only before this
  evidence/documentation update; no test, golden, frozen resource or case input.
- No semantic/public contract change was required, the golden was not obsolete,
  and no STOP condition was crossed.

The adjacent `summary.json` is the machine-readable index for this record.
