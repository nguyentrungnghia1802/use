# End-to-End Acceptance Criteria

> **HISTORICAL_EVIDENCE:** This document records the original Phase 15 release acceptance criteria and validation state (v1.0.0, 265 tests passed at commit `a4951e91`, tag `use-jacamo-plugin-v1.0.0`). It is retained as historical evidence. For current v1.0.1 hotfix validation (271 tests), see [13-testing-quality.md](13-testing-quality.md#9-v101-current-verification-2026-09-18), [HOTFIX-1.0.1.md](../../release/HOTFIX-1.0.1.md), and [report.md](../../../docs/report/report.md).

## 1. Metamodel/Mapping

- [x] Ecore baseline parses.
- [x] Mapping schema validates.
- [x] 100% structural coverage.
- [x] No known unqualified ambiguity.
- [x] Projection semantics documented.
- [x] Freeze manifests valid.

## 2. Import

- [x] `.jcm` selected as entry point.
- [x] includes/source paths resolve.
- [x] Jason dimension parsed.
- [x] CArtAgO dimension parsed.
- [x] Moise dimension parsed.
- [x] source spans preserved.
- [x] diagnostics actionable.

## 3. Semantic model

- [x] stable IDs.
- [x] cross-file references resolved.
- [x] unresolved refs explicit.
- [x] no USE dependency in parser/IR.

## 4. USE transformation

- [x] `.use` deterministic.
- [x] compile/type-check success.
- [x] `.cmd`/initial state valid.
- [x] associations/multiplicities valid.
- [x] concrete Artifact operations project correctly.
- [x] trace complete for generated elements.

## 5. OCL

- [x] translated OCL provenance recorded.
- [x] unsupported semantics not guessed.
- [x] core OCL loads.
- [x] case OCL loads.
- [x] positive/negative offline checks work.
- [x] pre/post operation checks work.

## 6. Binding/resolution

- [x] exact resolution preferred.
- [x] ambiguous case blocks automatic mapping.
- [x] explicit binding resolves ambiguity.
- [x] stale binding detected.

## 7. Runtime

- [x] connect.
- [x] initial full sync.
- [x] Agent events.
- [x] Artifact events.
- [x] Organisation events where supported.
- [x] trace lookup.
- [x] state updates.
- [x] operation enter/exit.
- [x] disconnect/reconnect.
- [x] full resync.
- [x] no silent dropped events.

## 8. Verification reporting

- [x] violation result includes OCL constraint.
- [x] USE context object identified.
- [x] JaCaMo semantic/source trace available.
- [x] runtime event link available.
- [x] report export works.

## 9. Auction

- [x] valid scenario passes.
- [x] closed-auction bid fails expected rule.
- [x] invalid amount fails.
- [x] trace ambiguity test works.
- [x] reconnect scenario converges state.

## 10. Engineering

- [x] clean build.
- [x] full tests.
- [x] no destructive warnings ignored.
- [x] docs updated.
- [x] release package.
- [x] tagged commit.

## Phase 15 verification evidence (2026-09-16)

The full `use-plugin verify` passed 119 unit and 3 release integration tests;
the five-module reactor and a separate candidate clean clone both passed
`mvn verify`/`mvn clean verify` with 265 tests and no failures, errors, or skips.
The canonical JaCaMo revision `849dc33` Ecore/EMF, mapping, negative mutation,
and USE compiler gates passed, and the four canonical resource hashes matched
this checkout. Focused Auction offline/live, plugin smoke, and auto-resync tests
passed 7/7. The generated evidence had 14 artifacts, 13 runtime events, 11
reports, and zero reconnect drift differences. The built ZIP had exactly 27
manifest-declared entries; its JAR contained byte-identical canonical mapping,
metamodel, release metadata, and licenses, and an isolated USE child JVM loaded
the installed JAR and canonical mapping without Maven test dependencies.

Supported runtime scope and limits are in `../../KNOWN-LIMITATIONS.md`,
`../../compatibility.json`, and the Auction case-study record.

Final integration (2026-09-16) merged Phase 15 as `a4951e91`; the post-merge
five-module reactor passed 265/265 with no failures, errors or skips. The final
main commit was regression-tested and pushed, and remote tag
`use-jacamo-plugin-v1.0.0` was verified at that same commit. The generated ZIP,
embedded JAR resources and SHA-256 sidecar were rechecked before tagging.
