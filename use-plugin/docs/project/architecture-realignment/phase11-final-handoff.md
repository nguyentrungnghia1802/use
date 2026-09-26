# Phase 11 — final regression and thesis handoff

Status: PASS with exactly three pre-existing, explicitly allowlisted historical
failures. There are no unexpected failures, errors, or skips.

## Revisions and dependency boundary

- implementation base: USE `deba015212db37563b46b87eee650da6e0fac7d4`;
- official JaCaMo authority: `3866858a7ebf6be85d9199c13a09cf4bfb8191be`;
- pre-handoff working-set fingerprint: SHA-256
  `4a7b0e0586fb501839beccdb32eaf67a657ff97011cd292729a68b5c8d636be0`
  over 135 modified/untracked file path+content hashes;
- contract `1.0.0`, Bridge adapter `1.0.0`, USE plugin `1.0.1`, Java 21,
  JaCaMo 1.3.1, Jason 3.3.2, CArtAgO 3.1, Moise 1.1, NPL 0.6.1;
- runtime dependency graph: neutral contract has only the JDK; the JaCaMo-side
  adapter owns platform dependencies; the USE runtime owns contract + JSON
  validation only. No live JaCaMo object or platform JAR crosses the process
  boundary.

## Final regression

`mvn -B -pl use-plugin -am test` ran contract 8/8, adapter 9/9, USE core
12/12, GUI 1/1, and plugin 255 tests. The plugin result is exactly 252 pass plus
the three Phase-0 historical failures below, with 0 error and 0 skip. A
failure-aware `verify` continued through packaging and ran core IT 1/1, GUI IT
129/129, and plugin packaging/install IT 4/4. The two separate-JVM tests pass
after both the recorded and production TCP boundaries are built. The isolated
official JaCaMo checkout ran 6/6 Gradle tests.

The three unchanged historical failures are:

1. `ConstraintClosureTest.distinctV2OwnersWithJavaHashCollisionsKeepDistinctConstraintIdentities`;
2. `GoldenPipelineTest.auctionArtifactsMatchReviewedGoldenDigests`;
3. `InstanceMaterializationTest.textAndDirectBackendsShareOneVerificationPlanAndPassInitialValidation`.

They retain their original constraint-identity and reviewed command/diagnostic/
trace digest mismatches. No golden, assertion, or skip configuration was changed.

## Canonical and runtime results

| Case | Input SHA-256 | Model revision | Result |
|---|---|---|---|
| Hello | `c81d15c9...b4101` | `dc259e10...74d39` | PASS; 591 semantic elements, 1,095 USE objects, reviewed shadow register |
| Auction | `c766fb0d...7d2fb` | `d144c7b8...6d4c4` | SUPPORTED_SCOPE_PASS; 562 elements, 994 objects, exact `1..1`/`0..300` relations |
| House | `c14ae629...5fef08` | `e2a4700b...ec1f4` | SUPPORTED_SCOPE_PASS; 22 configured agents, 592 elements, 1,024 objects; loaded OS relation evidence includes bricklayer `1..2` |

Runtime projection remains conservative: exact bound target facts are
`MATERIALIZED_FAITHFULLY`; Jason/CArtAgO/Moise/NPL observations without an exact
reviewed target are `EVIDENCE_ONLY`; complete belief/board streams and descriptor
metadata unavailable from official APIs are `UNAVAILABLE`. Only faithful facts
may gate OCL. NPL and authored OCL remain separate.

## Transport, security, packaging, and reproducibility

Authenticated loopback TCP uses canonical length-framed JSON, HMAC-SHA-256,
constant-time MAC comparison, nonce replay bounds, bounded frames/queues,
ack/resume tokens, explicit GAP/resync, and at-least-once idempotent delivery.
Non-loopback endpoints are rejected; remote TLS is out of scope. Fault tests
cover bad MAC, malformed/oversized/deep payloads, schema mismatch, stale
session/generation/revision, conflicting duplicates, unknown resume, overflow,
disconnect and shutdown. The latest 65,536-byte host measurement is 9,900 ns
median for recorded in-memory transport and 637,500 ns for loopback TCP.

The release ZIP contains separate neutral-contract and JaCaMo-adapter JARs plus
the USE plugin JAR. Package inventory, byte-for-byte manifest, SHA-256 sidecar,
installed mapping import, pinned USE plugin discovery, legacy-class exclusion,
listener/server/thread cleanup, and process/classpath separation all pass. The
SBOM is `phase11-sbom.json`; machine-readable results are in
`phase09-11-evidence.json`.

Reproduce with:

```text
mvn -B -pl use-plugin -am test
mvn -B -pl use-plugin -am "-Dmaven.test.failure.ignore=true" verify
mvn -B -pl use-plugin -am "-Dtest=SeparateJvmBridgeTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
C:\Windows\Temp\jacamo-phase0-01a0dcbe\gradlew.bat test --rerun-tasks --console=plain
```

No STOP condition was encountered. Remaining semantic limits are the explicitly
unsupported original Auction plan/deadline equivalence and the unobserved full
House dynamic runtime described in `KNOWN-LIMITATIONS.md`.
