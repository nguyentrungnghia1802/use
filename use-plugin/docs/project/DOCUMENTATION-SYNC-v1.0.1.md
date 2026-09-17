# Documentation Synchronization Inventory & Audit (v1.0.1)

> **Document type:** Documentation Synchronization Audit and Reconciliation Ledger  
> **Status:** ACTIVE AUDIT INVENTORY  
> **Repository baseline:** Commit `31ceee6e` (branch `main`)  
> **Release baseline:** v1.0.1 Correctness Hotfix (commit `7f77b1f4`, git tag `v1.0.1`)  
> **Date:** 2026-09-18  

---

## 1. Executive Summary

This document records the comprehensive audit and synchronization ledger for the documentation across the USE–JaCaMo repository. Following the v1.0.1 Correctness Hotfix, documentation across root, plugin, architecture, release, and agent guidelines was synchronized with the authoritative repository implementation, build manifests, test evidence, and Git tags.

All documentation drift identified between implementation reality and existing documents has been reconciled. Historical documents have been clearly labeled and preserved without rewriting historical evidence, and broken internal links resulting from archived files or relative path shifts have been repaired.

---

## 2. Authoritative Evidence Baseline

When synchronizing documentation, evidence follows this strict hierarchy of authority:

```text
1. Current source code
2. Current executable / build / runtime configuration (pom.xml, useplugin.xml)
3. Current automated tests (JUnit / Failsafe execution)
4. Machine-readable manifests (release-manifest.json, compatibility.json, DOCUMENTATION-MANIFEST.json)
5. Git repository history and tags
6. Frozen specification contracts (JaCaMo-Metamodel.ecore, jacamo-use-mapping-v1.json, freeze-manifest.json)
7. Active documentation (docs/project/*, README.md, agent.md)
8. Historical documentation (tasks/task-01.md, historical plans, archived reports)
9. Assumptions / unverified claims
```

### 2.1 Release and Version Identity

| Entity | Verified Value | Evidence Source | Notes |
| --- | --- | --- | --- |
| **Plugin / Artifact Version** | `1.0.1` | `pom.xml`, `useplugin.xml` | Authoritative application version |
| **Release Artifact** | `use-jacamo-plugin-1.0.1.zip` | `pom.xml`, `release-manifest.json` | 27 manifest-declared entries |
| **Existing Git Tag** | `v1.0.1` | `git tag -l` points to `7f77b1f4` | Annotated tag created on hotfix commit |
| **Manifest Expected Git Tag** | `use-jacamo-plugin-v1.0.1` | `release-manifest.json:4` | Planned tag name; **absent** from Git tags |
| **Release Status** | `candidate-not-tagged` | `release-manifest.json:136`, `compatibility.json:5` | Local release candidate; not published/pushed |
| **Prior Release Tag** | `use-jacamo-plugin-v1.0.0` | Commit `848e18e3` | Preserved immutable historical release tag |

> **IMPORTANT DISTINCTION:**  
> - Application version = `1.0.1`  
> - Existing Git tag = `v1.0.1`  
> - Manifest planned release tag = `use-jacamo-plugin-v1.0.1` (not yet created in git)  
> - Publication state = local release candidate (`candidate-not-tagged`)  
> Documentation must never claim that `v1.0.1` or `use-jacamo-plugin-v1.0.1` is fully published or remotely released.

### 2.2 Test Evidence and Counts

| Scope | Test Count | Status | Authoritative Source |
| --- | ---: | --- | --- |
| `use-core` | 13 | PASS (0 failures, 0 errors, 0 skips) | `validation.json`, `final-verify.log` |
| `use-gui` | 130 | PASS (0 failures, 0 errors, 0 skips) | `validation.json`, `final-verify.log` |
| `use-plugin` | 128 | PASS (125 unit/component + 3 release IT) | `validation.json`, `final-verify.log` |
| **Total Reactor Suite** | **271** | **271/271 PASS** | `use-plugin/release/evidence/v1.0.1/validation.json` |
| Historical v1.0.0 suite | 265 | Historical PASS (Phase 15, commit `a4951e91`) | `17-end-to-end-acceptance.md` |
| Historical Phase 13 suite | 251 | Historical PASS (Phase 13, commit `00140fd0`) | `13-testing-quality.md#8` |

### 2.3 Correctness Hotfix Scope (P1 & P2)

- **P1 (Workspace Replacement Lifecycle):**
  - Production facade (`DefaultJaCaMoFacade`) channels rebuild, user OCL load, and reimport through `installWorkspace`.
  - `RuntimeMirrorService.replaceWorkspace` drains the old event stream, transfers exact matching runtime aliases, swaps mutation and verification consumers together, and synchronizes an authoritative snapshot before returning `LIVE`.
  - Failed builds preserve the existing workspace; failed snapshot replacements disconnect with `ERROR` instead of mixing consumers or silently holding a stale `LIVE` state.
- **P2 (Binding Configuration Location and Resolution):**
  - The authoritative production binding file location is `<project-root>/binding.json`.
  - `StaticProjectImporter` reads `<project-root>/binding.json` after source extraction, computes semantic hashes, and invokes `ExactSemanticResolver`.
  - Stale hashes or invalid entries result in explicit `BINDING_STALE` or `BINDING_INVALID` diagnostics; ambiguities without binding remain `RESOLUTION_AMBIGUOUS`.

---

## 3. Active vs. Historical Documentation Classification

| Document | Classification | Role & Retention Policy |
| --- | --- | --- |
| `README.md` | **ACTIVE** | Root repository overview with pointer to `use-plugin` |
| `use-plugin/README.md` | **ACTIVE** | Plugin entry point, 1.0.1 versioning, installation, and run instructions |
| `use-plugin/CHANGELOG.md` | **ACTIVE** | Version history with 1.0.1 hotfix entries and verified 271-test total |
| `use-plugin/KNOWN-LIMITATIONS.md` | **ACTIVE** | Authoritative active limitations (workspace replacement, binding scope) |
| `use-plugin/compatibility.json` | **ACTIVE** | Machine-readable compatibility matrix and release status |
| `use-plugin/docs/DOCUMENTATION-MANIFEST.json` | **ACTIVE** | Documentation inventory and project guidelines |
| `use-plugin/docs/agent/agent.md` | **ACTIVE** | Authoritative agent operating instructions, invariants, and source-of-truth priority |
| `use-plugin/docs/project/00-README.md` | **ACTIVE** | Canonical onboarding guide and documentation index |
| `use-plugin/docs/project/01-vision-scope.md` | **ACTIVE** | Scope boundaries and research context |
| `use-plugin/docs/project/02-system-architecture.md` | **ACTIVE** | Architecture specifications |
| `use-plugin/docs/project/03-repository-structure.md` | **ACTIVE** | Checked-in tree structure and package responsibilities at v1.0.1 |
| `use-plugin/docs/project/04-jacamo-metamodel-baseline.md` | **ACTIVE** | Ecore baseline specifications |
| `use-plugin/docs/project/05-metamodel-mapping-contract.md` | **ACTIVE** | Mapping V1 contract and projection specifications |
| `use-plugin/docs/project/06-semantic-model-and-extraction.md` | **ACTIVE** | Static discovery and extraction contracts |
| `use-plugin/docs/project/07-constraint-translation-and-ocl.md` | **ACTIVE** | OCL translation boundaries and provenance |
| `use-plugin/docs/project/08-use-transformation.md` | **ACTIVE** | Materialization contracts |
| `use-plugin/docs/project/09-traceability-binding-resolver.md` | **ACTIVE** | Traceability and `<project-root>/binding.json` specifications |
| `use-plugin/docs/project/10-runtime-adapter.md` | **ACTIVE** | Runtime lifecycle, mirror service, and workspace replacement contracts |
| `use-plugin/docs/project/11-verification-engine.md` | **ACTIVE** | Offline and runtime verification engine contracts |
| `use-plugin/docs/project/12-plugin-ui-workflow.md` | **ACTIVE** | Swing workbench user workflow and binding persistence |
| `use-plugin/docs/project/13-testing-quality.md` | **ACTIVE** | Testing pyramid with Section 9 recording current v1.0.1 271-test validation |
| `use-plugin/docs/project/14-auction-case-study.md` | **ACTIVE** | Acceptance fixture and tested runtime boundaries |
| `use-plugin/docs/project/15-build-release-operations.md` | **ACTIVE** | Release operations, package verification, and tag semantics |
| `use-plugin/docs/project/16-research-evidence-boundaries.md` | **ACTIVE** | Research vs engineering boundaries and status vocabulary |
| `use-plugin/docs/project/18-risk-register.md` | **ACTIVE** | Active risk ledger and mitigations |
| `use-plugin/docs/project/190-roadmap-2.md` | **ACTIVE** | Post-1.0.1 engineering roadmap for thesis completion (Phases 16–22) |
| `use-plugin/docs/project/17-end-to-end-acceptance.md` | **HISTORICAL** | Phase 15 release acceptance criteria (v1.0.0 / 265 tests); historical note added |
| `use-plugin/docs/project/19-roadmap.md` | **HISTORICAL** | Original v1.0.0 roadmap (Phases 0–15); historical note added |
| `use-plugin/docs/agent/tasks/task-01.md` | **HISTORICAL** | Original implementation checklist (archived from `task.md`); historical note added |
| `docs/report/report.md` | **HISTORICAL** | v1.0.1 Correctness Hotfix report; retained release evidence |
| `use-plugin/release/HOTFIX-1.0.1.md` | **HISTORICAL** | DoD reconciliation table for v1.0.1 hotfix; retained release evidence |

---

## 4. Documentation Drift & Reconciliation Ledger

| Item | File | Stale Statement / Drift | Root Cause / Evidence | Correction Applied |
| --- | --- | --- | --- | --- |
| **D1** | `agent.md:65` | Normative hierarchy placed `docs/project/*` and `task.md` above implementation code; instructed to "fix code if it conflicts with spec". | Inverted authority hierarchy. Source code and tests are authoritative for implemented behavior; docs must sync with code. | Replaced with evidence priority hierarchy (code + config + tests > frozen specs > active docs > historical docs). |
| **D2** | `agent.md:158`, `366`, `454`, `484` | Referenced `docs/agent/task.md` as active task tracker. | `task.md` was archived to `docs/agent/tasks/task-01.md` in commit `f1fe6b4c`. | Updated to reference `docs/agent/tasks/task-<n>.md` with historical notice for `task-01.md`. |
| **D3** | `task-01.md:799` | Link to `../project/13-testing-quality.md` was broken. | Moving `task.md` to `tasks/task-01.md` moved it one directory deeper. | Updated relative link to `../../project/13-testing-quality.md#8-phase-13-verification-evidence-2026-09-16`. Added historical header. |
| **D4** | `17-end-to-end-acceptance.md` | Document listed 265 tests without version scope distinction, risking confusion with v1.0.1's 271 tests. | Recorded Phase 15 / v1.0.0 acceptance gate at commit `a4951e91`. | Added `HISTORICAL_EVIDENCE` note clarifying v1.0.0 scope and pointing to v1.0.1 evidence in `13-testing-quality.md`. |
| **D5** | `report.md:27, 44, 54, 55` | Relative links `use-plugin/release/...` were broken. | `report.md` is in `docs/report/`, not repo root; needed `../../use-plugin/...`. | Fixed relative links to `../../use-plugin/release/...` and `../../use-plugin/target/...`. |
| **D6** | `HOTFIX-1.0.1.md:11, 103` | Relative links `../../report.md` were broken. | `report.md` is at `docs/report/report.md`, not repository root. | Updated to `../../docs/report/report.md`. |
| **D7** | `00-README.md:227` | Plain-text paths for `HOTFIX-1.0.1.md`, `report.md`, and `DOCUMENTATION-SYNC-v1.0.1.md`. | Text paths were not valid relative markdown links. | Updated to clickable relative links `[HOTFIX-1.0.1.md](../../release/HOTFIX-1.0.1.md)`, `[report.md](../../../docs/report/report.md)`, and `[DOCUMENTATION-SYNC-v1.0.1.md](DOCUMENTATION-SYNC-v1.0.1.md)`. |
| **D8** | `README.md:105` | Root README had no pointer to `use-plugin/`. | Root README is upstream USE overview. | Added link to `use-plugin/README.md` and `use-plugin/docs/project/00-README.md` under `## Documentation`. |
| **D9** | `DOCUMENTATION-MANIFEST.json:33` | Note stated `"agent.md must remain under 400 lines."` | Outdated restriction prior to comprehensive agent operating protocol integration. | Updated note to reflect `agent.md` as authoritative agent operating protocol. |
| **D10** | `190-roadmap-2.md:3-6` | Trailing spaces in blockquote caused `git diff --check` warnings. | Markdown hard line break formatting. | Replaced with `<br>` line breaks; `git diff --check` now clean. |
| **D11** | Multiple docs | Stale binding location `verification/binding.json`. | Resolved in earlier audit pass; implementation uses `<project-root>/binding.json`. | Verified 0 occurrences of `verification/binding.json`; all active docs state `<project-root>/binding.json`. |

---

## 5. Broken Link Audit & Resolution

| Source Document | Link Target | Status | Resolution |
| --- | --- | --- | --- |
| `docs/report/report.md:27` | `use-plugin/release/HOTFIX-1.0.1.md` | BROKEN | Updated to `../../use-plugin/release/HOTFIX-1.0.1.md` |
| `docs/report/report.md:44` | `use-plugin/release/evidence/v1.0.1/*.log` | BROKEN | Updated to `../../use-plugin/release/evidence/v1.0.1/*.log` |
| `docs/report/report.md:54` | `use-plugin/release/evidence/v1.0.1/validation.json` | BROKEN | Updated to `../../use-plugin/release/evidence/v1.0.1/validation.json` |
| `docs/report/report.md:55` | `use-plugin/target/use-jacamo-plugin-1.0.1.zip` | BROKEN | Updated to `../../use-plugin/target/use-jacamo-plugin-1.0.1.zip` |
| `use-plugin/release/HOTFIX-1.0.1.md:11` | `../../report.md` | BROKEN | Updated to `../../docs/report/report.md` |
| `use-plugin/release/HOTFIX-1.0.1.md:103` | `../../report.md` | BROKEN | Updated to `../../docs/report/report.md` |
| `use-plugin/docs/agent/tasks/task-01.md:799` | `../project/13-testing-quality.md#8...` | BROKEN | Updated to `../../project/13-testing-quality.md#8...` |
| `use-plugin/docs/project/00-README.md:227` | Plain string `DOCUMENTATION-SYNC-v1.0.1.md` | UNRESOLVED | Created this document in `docs/project/` and linked directly |

---

## 6. Verification Summary

1. **Automated Tests:**
   - Command: `mvn --batch-mode -pl use-plugin -am "-Dtest=HotfixLifecycleTest,HotfixBindingTest" "-Dsurefire.failIfNoSpecifiedTests=false" test`
   - Result: 5 tests run, 0 failures, 0 errors, 0 skipped. BUILD SUCCESS.
   - Retained reactor suite: 271/271 tests PASS across `use-core` (13), `use-gui` (130), `use-plugin` (128).
2. **Git Whitespace & Syntax Check:**
   - Command: `git diff --check`
   - Result: Exit code 0, clean.
3. **Link Integrity:**
   - Python markdown link scanner confirmed zero broken relative links across all active documentation.
4. **Keyword Stale Scan:**
   - `verification/binding.json`: 0 occurrences.
   - `candidate-not-tagged`: Accurately documented in `release-manifest.json`, `compatibility.json`, and audit records.
   - `use-jacamo-plugin-v1.0.1`: Correctly labeled as planned manifest tag name, absent from git tags.
