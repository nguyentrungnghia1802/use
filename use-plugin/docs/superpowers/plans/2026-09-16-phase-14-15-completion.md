# Phase 14 and Phase 15 Completion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close the Phase 14 Auction evidence blockers with real JaCaMo lifecycle evidence, then complete and publish the Phase 15 release from the verified merged `main` branch.

**Architecture:** CArtAgO operation contracts are driven only by the official `opStarted` to `opCompleted`/`opFailed` lifecycle. The checked-in Auction fixture permits zero through its guard and rejects it explicitly in the operation body, while authored case-study OCL states the positive amount and open-auction contracts with source-backed provenance. Exactly fourteen normalized evidence artifacts are regenerated and audited before Phase 14 integration; Phase 15 packages the already-verified plugin, canonical resources, profiles, example, and release metadata.

**Tech Stack:** Java 21, Maven reactor, JUnit 5, USE 7.5.0, Jason 3.3.0, CArtAgO 3.1, Moise 1.1, Jackson, Git/PowerShell.

**Spec:** `use-plugin/docs/agent/task.md` (Phase 14 and Phase 15), `use-plugin/docs/project/14-auction-case-study.md`, `use-plugin/docs/project/17-end-to-end-acceptance.md`

## Global Constraints

- Preserve all valid uncommitted Phase 14 work; do not reset, checkout, clean, or discard it.
- Do not change the frozen Core Ecore or Mapping V1 without new evidence.
- Use real in-process Jason, CArtAgO, Moise, and USE components where acceptance requires runtime evidence.
- Do not represent Moise runtime semantics beyond the real programmatic subset and polling capabilities exercised.
- Do not mark a phase complete until its acceptance gate, review, merge, push, and required post-merge verification succeed.

---

### Task 1: Close the CArtAgO orphan-correlation regression

**Files:**
- Modify: `use-plugin/src/test/java/org/tzi/use/plugins/jacamo/runtime/CartagoRuntimeConnectorTest.java`
- Modify: `use-plugin/src/test/java/org/tzi/use/plugins/jacamo/runtime/TestAuctionArtifact.java`
- Verify: `use-plugin/src/main/java/org/tzi/use/plugins/jacamo/runtime/CartagoRuntimeConnector.java`
- Verify: `use-plugin/src/test/resources/auction/src/env/auction/AuctionArtifact.java`

**Interfaces:**
- Consumes: real `ICartagoLogger` callbacks from CArtAgO 3.1.
- Produces: one `OP_ENTER` per observed `opStarted`, followed by exactly one same-correlation `OP_EXIT` or `OP_FAIL` for the terminating fixture scenarios. CArtAgO can start and suspend a guard-rejected request, so the negative-amount request is not used as a terminal lifecycle scenario.

- [x] Add a real guarded test Artifact whose guard permits zero into a body-level `failed(...)` branch; confirm an unfulfilled negative guard can suspend rather than finish.
- [x] Add a regression assertion that every emitted `OP_ENTER` has exactly one terminal event and no terminal event lacks an enter in the terminating fixture run.
- [x] Temporarily mutate the connector to use `opRequested`, run `mvn -pl use-plugin -Dtest=CartagoRuntimeConnectorTest test`, and record the expected correlation RED.
- [x] Restore the production `opStarted` lifecycle and rerun the focused test GREEN.
- [x] Run `AuctionSourceRuntimeTest` and `LiveJaCaMoAuctionIntegrationTest` to confirm zero reaches the body and produces correlated `OP_FAIL`.

### Task 2: Close provenance and evidence-contract gaps

**Files:**
- Modify: `use-plugin/src/test/java/org/tzi/use/plugins/jacamo/GoldenPipelineTest.java`
- Modify: `use-plugin/src/test/java/org/tzi/use/plugins/jacamo/runtime/LiveJaCaMoAuctionIntegrationTest.java`
- Modify: `use-plugin/src/test/java/org/tzi/use/plugins/jacamo/evidence/EvidenceNormalizerTest.java`
- Modify: `use-plugin/src/test/resources/auction/verification/auction.ocl`
- Modify: `use-plugin/src/test/resources/golden/auction-sha256.properties`
- Modify: `use-plugin/src/main/java/org/tzi/use/plugins/jacamo/verification/ConstraintRegistry.java`

**Interfaces:**
- Consumes: generated model/state/OCL/trace, real runtime event stream, repository/tool/source hashes.
- Produces: exactly 14 portable evidence artifacts with a manifest, explicit assumptions/limitations, accurate translated-versus-authored provenance, and conservative Moise claims.

- [x] Add RED assertions for `offline/manifest.json`, the exact 14-path artifact set, required hash/version/assumption fields, lifecycle pairing, and precise Moise provenance labels.
- [x] Rename/restructure the offline semantic summary as the Phase 14 manifest without increasing the artifact count.
- [x] State that the runtime Moise `OS`/`OE` subset is programmatically constructed to exercise real Moise APIs and is not parsed/executed from the checked-in XML.
- [x] State authored case OCL separately from exact guard translation and do not claim semantic equivalence beyond source evidence.
- [x] Normalize portable paths and nondeterministic fields only where justified; preserve semantic JSON escaping.
- [x] Review and update only intentional golden hashes after inspecting artifact outputs and the fixture/source changes.
- [x] Run focused evidence, constraint, extraction, materialization, and live runtime tests (21/21).

### Task 3: Regenerate and audit Phase 14 evidence

**Files:**
- Generate: `use-plugin/target/phase14-auction-evidence/offline/*`
- Generate: `use-plugin/target/phase14-auction-evidence/runtime/*`
- Modify: `use-plugin/docs/project/14-auction-case-study.md`
- Modify: `use-plugin/docs/agent/task.md`

**Interfaces:**
- Consumes: Tasks 1-2 verified implementation.
- Produces: audited evidence with valid JSON, portable paths, verified SHA-256 fields, expected event/report counts, and documented limitations.

- [x] Regenerate evidence from the focused golden and live tests.
- [x] Parse every JSON artifact with Jackson/PowerShell JSON parsing.
- [x] Verify the exact relative path set and total count is 14.
- [x] Recompute every declared SHA-256 and compare it with the manifest/summary.
- [x] Verify operation correlations are balanced and invalid amount ends with `OP_FAIL`.
- [x] Verify provenance distinguishes translated guard OCL, authored case OCL, structural Moise source, and programmatic runtime subset.
- [x] Run final `mvn -pl use-plugin verify` (118/118) and full reactor `mvn verify` (261/261).
- [x] Dispatch an independent Phase 14 reviewer against requirements, diff, tests, and evidence; no Critical/Important issue remained.

### Task 4: Integrate Phase 14 only after PASS

**Files:**
- Modify: `use-plugin/docs/agent/task.md`
- Modify: Phase 14 evidence/checklist documentation selected by repository conventions.

**Interfaces:**
- Consumes: independent review with no Critical/Important findings and fresh full-suite evidence.
- Produces: pushed Phase 14 branch, merged and pushed `main`, and fresh post-merge test evidence.

- [x] Record Phase 14 commands/results, limitations, artifact inventory, and review outcome.
- [ ] Commit Phase 14 in coherent conventional commits and ensure the branch is clean.
- [ ] Push `phase/14-auction-final-e2e`, merge it into `main`, and run the required post-merge module/full smoke test.
- [ ] Push `main` and verify local/remote SHAs match.

### Task 5: Complete Phase 15 release

**Files:**
- Read/modify: `use-plugin/docs/project/15-build-release-operations.md`
- Modify: `use-plugin/docs/project/17-end-to-end-acceptance.md`
- Modify: `use-plugin/docs/agent/task.md`
- Modify/create only repository-defined README, changelog, manifest, packaging, and release files required by Phase 15.

**Interfaces:**
- Consumes: pushed Phase 14 `main` and all repository-defined release requirements.
- Produces: clean verified release commit/tag and distributable package containing the plugin JAR, canonical metamodel/mapping resources, OCL profiles, Auction example, notices, and release manifest.

- [ ] Read the release operations document and inspect existing release/package conventions.
- [ ] Add RED release-manifest/package-content tests or executable audit checks before packaging changes.
- [ ] Complete install/workflow/architecture/limitations/compatibility/changelog documentation without duplicating sources of truth.
- [ ] Build the release package from a clean checkout and audit its inventory/hashes.
- [ ] Run the full build/tests, mapping audit, Auction E2E, and plugin load smoke defined by the repository.
- [ ] Verify every end-to-end acceptance checkbox from executable evidence and record any honest supported-scope limitation.
- [ ] Request independent release review and resolve every Critical/Important finding.
- [ ] Commit/merge/push Phase 15, run post-merge release verification on `main`, create the repository-specified release tag, push it, and verify remote tag/package/SHA.
