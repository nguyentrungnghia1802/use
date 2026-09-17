# v1.0.1 Correctness Hotfix — release candidate

No tag or push is performed. The immutable v1.0.0 tag is retained.

## DoD reconciliation

Inventory before edits: 65 open boxes in the canonical task checklist, 11 in the historical
Phase 14/15 execution plan, and 2 upstream issue-template boxes. Every item is listed below.
A = evidence exists; B = genuinely incomplete; C = superseded; D = outside this hotfix/thesis scope.
Only A items are checked in the canonical checklist. Historical evidence is labelled; an old
EMF/Python pass is not claimed as a fresh run. Details and fresh test totals are in ../../report.md.

{'A': 56, 'D': 3, 'B': 3, 'C': 16}

| CHECKBOX | STATUS | EVIDENCE | ACTION |
| --- | --- | --- | --- |
| docs/agent/task.md:7 — Canonical Ecore baseline audited and frozen. | A | Core/Mapping/freeze-manifest.json; MappingTransformationTest | Complete within documented supported scope; evidence linked here. |
| docs/agent/task.md:8 — Mapping V1 structurally complete, internally consistent, ambiguity-free. | A | MappingTransformationTest; Core/Mapping/METAMODEL-MAPPING-AUDIT.md (historical structural audit) | Complete within documented supported scope; evidence linked here. |
| docs/agent/task.md:9 — Plugin loads in pinned USE. | A | ReleasePackageIT (USE loader and isolated JVM) | Complete within documented supported scope; evidence linked here. |
| docs/agent/task.md:10 — JaCaMo project import works from `.jcm`. | A | DefaultJaCaMoFacadeTest; HotfixBindingTest | Complete within documented supported scope; evidence linked here. |
| docs/agent/task.md:11 — Jason/CArtAgO/Moise extraction works for supported project. | A | StaticProjectImporterTest; LiveJaCaMoAuctionIntegrationTest; AuctionSourceRuntimeTest | Complete within documented supported scope; evidence linked here. |
| docs/agent/task.md:12 — Semantic model + trace complete. | A | TraceBindingTest; HotfixBindingTest (supported subset) | Complete within documented supported scope; evidence linked here. |
| docs/agent/task.md:13 — USE model/state generation works. | A | MappingTransformationTest; DirectUseBackend materialization tests | Complete within documented supported scope; evidence linked here. |
| docs/agent/task.md:14 — Constraint translation supported subset works. | A | ConstraintOclTest (supported subset) | Complete within documented supported scope; evidence linked here. |
| docs/agent/task.md:15 — Core and case-study OCL work. | A | DefaultJaCaMoFacadeTest; HotfixLifecycleTest | Complete within documented supported scope; evidence linked here. |
| docs/agent/task.md:16 — Binding/resolver ambiguity path works. | A | HotfixBindingTest; ExactSemanticResolver | Complete within documented supported scope; evidence linked here. |
| docs/agent/task.md:17 — Runtime adapter works against real JaCaMo. | D | LiveJaCaMoAuctionIntegrationTest; AuctionSourceRuntimeTest (in-process component APIs only; standalone launcher excluded) | Full external JaCaMo launcher is outside hotfix scope; in-process APIs remain tested. |
| docs/agent/task.md:18 — Runtime OCL detects violations. | A | RuntimeVerificationEngineTest; HotfixLifecycleTest | Complete within documented supported scope; evidence linked here. |
| docs/agent/task.md:19 — Auction E2E positive and negative scenarios work. | A | LiveJaCaMoAuctionIntegrationTest; AuctionSourceRuntimeTest; Auction offline/golden tests | Complete within documented supported scope; evidence linked here. |
| docs/agent/task.md:20 — Full tests/build/docs/release complete. | B | Candidate verification; releaseStatus=candidate-not-tagged | Open, non-blocking candidate limitation: no tag/push; original external EMF/Python suite not rerun. |
| docs/agent/task.md:32 — Parse canonical `JaCaMo-Metamodel.ecore` programmatically. | A | MappingLoader.ecoreKeys; MappingTransformationTest | Complete within documented supported scope; evidence linked here. |
| docs/agent/task.md:33 — Assert expected EClass count from file. | A | MappingTransformationTest (37 EClasses) | Complete within documented supported scope; evidence linked here. |
| docs/agent/task.md:34 — Enumerate all EAttributes with owner/type/bounds/default. | A | Core/Mapping/jacamo-use-mapping-v1.json attributes; historical mapping audit | Complete within documented supported scope; evidence linked here. |
| docs/agent/task.md:35 — Enumerate all EReferences with owner/target/bounds/containment. | A | Core/Mapping/jacamo-use-mapping-v1.json associations; historical mapping audit | Complete within documented supported scope; evidence linked here. |
| docs/agent/task.md:36 — Enumerate all inheritance edges. | A | Core/Mapping/jacamo-use-mapping-v1.json inheritance (14) | Complete within documented supported scope; evidence linked here. |
| docs/agent/task.md:37 — Extract unresolved annotations. | A | Core/Mapping/jacamo-use-mapping-v1.json unresolvedSourceFeatures | Complete within documented supported scope; evidence linked here. |
| docs/agent/task.md:38 — Record Ecore SHA-256. | A | MappingTransformationTest validates frozen Ecore SHA-256 | Complete within documented supported scope; evidence linked here. |
| docs/agent/task.md:39 — Create/update `metamodel-audit.md`. | C | Core/Mapping/METAMODEL-MAPPING-AUDIT.md | Superseded by the named canonical audit/manifest; do not create duplicate baselines. |
| docs/agent/task.md:40 — Create/update `metamodel-freeze-manifest.json`. | C | Core/Mapping/freeze-manifest.json | Superseded by the named canonical audit/manifest; do not create duplicate baselines. |
| docs/agent/task.md:41 — Add tests that fail on unnoticed structural drift. | A | MappingTransformationTest negative schema/hash/source tests | Complete within documented supported scope; evidence linked here. |
| docs/agent/task.md:44 — Create `jacamo-use-mapping.schema.json`. | A | Core/Mapping/jacamo-use-mapping.schema.json; MappingTransformationTest | Complete within documented supported scope; evidence linked here. |
| docs/agent/task.md:45 — Require qualified source identity. | A | Core/Mapping/jacamo-use-mapping.schema.json; MappingTransformationTest | Complete within documented supported scope; evidence linked here. |
| docs/agent/task.md:46 — Define target kinds. | A | Core/Mapping/jacamo-use-mapping.schema.json; MappingTransformationTest | Complete within documented supported scope; evidence linked here. |
| docs/agent/task.md:47 — Define multiplicity structure. | A | Core/Mapping/jacamo-use-mapping.schema.json; MappingTransformationTest | Complete within documented supported scope; evidence linked here. |
| docs/agent/task.md:48 — Define association/composition end schema. | A | Core/Mapping/jacamo-use-mapping.schema.json; MappingTransformationTest | Complete within documented supported scope; evidence linked here. |
| docs/agent/task.md:49 — Define projection schema. | A | Core/Mapping/jacamo-use-mapping.schema.json; MappingTransformationTest | Complete within documented supported scope; evidence linked here. |
| docs/agent/task.md:50 — Define unresolved/review flag schema. | A | Core/Mapping/jacamo-use-mapping.schema.json; MappingTransformationTest | Complete within documented supported scope; evidence linked here. |
| docs/agent/task.md:51 — Define mapping/evolution metadata. | A | Core/Mapping/jacamo-use-mapping.schema.json; MappingTransformationTest | Complete within documented supported scope; evidence linked here. |
| docs/agent/task.md:52 — Add schema validation test. | A | Core/Mapping/jacamo-use-mapping.schema.json; MappingTransformationTest | Complete within documented supported scope; evidence linked here. |
| docs/agent/task.md:55 — Verify every EClass has exactly one structural mapping. | A | Core/Mapping/METAMODEL-MAPPING-AUDIT.md and frozen JSON (historical); MappingTransformationTest validates unchanged hash/identities | Complete within documented supported scope; evidence linked here. |
| docs/agent/task.md:56 — Verify every declared EAttribute has mapping. | A | Core/Mapping/METAMODEL-MAPPING-AUDIT.md and frozen JSON (historical); MappingTransformationTest validates unchanged hash/identities | Complete within documented supported scope; evidence linked here. |
| docs/agent/task.md:57 — Verify every EReference has mapping. | A | Core/Mapping/METAMODEL-MAPPING-AUDIT.md and frozen JSON (historical); MappingTransformationTest validates unchanged hash/identities | Complete within documented supported scope; evidence linked here. |
| docs/agent/task.md:58 — Verify every inheritance edge has mapping. | A | Core/Mapping/METAMODEL-MAPPING-AUDIT.md and frozen JSON (historical); MappingTransformationTest validates unchanged hash/identities | Complete within documented supported scope; evidence linked here. |
| docs/agent/task.md:59 — Verify source owner exists. | A | Core/Mapping/METAMODEL-MAPPING-AUDIT.md and frozen JSON (historical); MappingTransformationTest validates unchanged hash/identities | Complete within documented supported scope; evidence linked here. |
| docs/agent/task.md:60 — Verify source feature exists. | A | Core/Mapping/METAMODEL-MAPPING-AUDIT.md and frozen JSON (historical); MappingTransformationTest validates unchanged hash/identities | Complete within documented supported scope; evidence linked here. |
| docs/agent/task.md:61 — Verify reference target matches Ecore. | A | Core/Mapping/METAMODEL-MAPPING-AUDIT.md and frozen JSON (historical); MappingTransformationTest validates unchanged hash/identities | Complete within documented supported scope; evidence linked here. |
| docs/agent/task.md:62 — Verify containment matches Ecore. | A | Core/Mapping/METAMODEL-MAPPING-AUDIT.md and frozen JSON (historical); MappingTransformationTest validates unchanged hash/identities | Complete within documented supported scope; evidence linked here. |
| docs/agent/task.md:63 — Verify forward multiplicity matches Ecore. | A | Core/Mapping/METAMODEL-MAPPING-AUDIT.md and frozen JSON (historical); MappingTransformationTest validates unchanged hash/identities | Complete within documented supported scope; evidence linked here. |
| docs/agent/task.md:64 — Verify target association names unique. | A | Core/Mapping/METAMODEL-MAPPING-AUDIT.md and frozen JSON (historical); MappingTransformationTest validates unchanged hash/identities | Complete within documented supported scope; evidence linked here. |
| docs/agent/task.md:65 — Verify source keys unique. | A | Core/Mapping/METAMODEL-MAPPING-AUDIT.md and frozen JSON (historical); MappingTransformationTest validates unchanged hash/identities | Complete within documented supported scope; evidence linked here. |
| docs/agent/task.md:66 — Reject unqualified `operation`-style IDs. | A | Core/Mapping/METAMODEL-MAPPING-AUDIT.md and frozen JSON (historical); MappingTransformationTest validates unchanged hash/identities | Complete within documented supported scope; evidence linked here. |
| docs/agent/task.md:67 — Explicitly preserve unresolved visible attributes as unresolved, not guessed. | A | Core/Mapping/METAMODEL-MAPPING-AUDIT.md and frozen JSON (historical); MappingTransformationTest validates unchanged hash/identities | Complete within documented supported scope; evidence linked here. |
| docs/agent/task.md:68 — Audit all review-flag inheritance entries. | A | Core/Mapping/METAMODEL-MAPPING-AUDIT.md and frozen JSON (historical); MappingTransformationTest validates unchanged hash/identities | Complete within documented supported scope; evidence linked here. |
| docs/agent/task.md:72 — source defined; | A | Core/Mapping/jacamo-use-mapping-v1.json metamodelContract/projections; METAMODEL-MAPPING-AUDIT.md projection table | Complete within documented supported scope; evidence linked here. |
| docs/agent/task.md:73 — target defined; | A | Core/Mapping/jacamo-use-mapping-v1.json metamodelContract/projections; METAMODEL-MAPPING-AUDIT.md projection table | Complete within documented supported scope; evidence linked here. |
| docs/agent/task.md:74 — need/rationale defined; | A | Core/Mapping/jacamo-use-mapping-v1.json metamodelContract/projections; METAMODEL-MAPPING-AUDIT.md projection table | Complete within documented supported scope; evidence linked here. |
| docs/agent/task.md:75 — direction defined; | A | Core/Mapping/jacamo-use-mapping-v1.json metamodelContract/projections; METAMODEL-MAPPING-AUDIT.md projection table | Complete within documented supported scope; evidence linked here. |
| docs/agent/task.md:76 — lossless/lossy status defined; | A | Core/Mapping/jacamo-use-mapping-v1.json metamodelContract/projections; METAMODEL-MAPPING-AUDIT.md projection table | Complete within documented supported scope; evidence linked here. |
| docs/agent/task.md:77 — assumptions defined; | A | Core/Mapping/jacamo-use-mapping-v1.json metamodelContract/projections; METAMODEL-MAPPING-AUDIT.md projection table | Complete within documented supported scope; evidence linked here. |
| docs/agent/task.md:78 — fallback defined; | A | Core/Mapping/jacamo-use-mapping-v1.json metamodelContract/projections; METAMODEL-MAPPING-AUDIT.md projection table | Complete within documented supported scope; evidence linked here. |
| docs/agent/task.md:79 — trace requirement defined; | A | Core/Mapping/jacamo-use-mapping-v1.json metamodelContract/projections; METAMODEL-MAPPING-AUDIT.md projection table | Complete within documented supported scope; evidence linked here. |
| docs/agent/task.md:80 — test case defined. | B | Core/Mapping/jacamo-use-mapping-v1.json metamodelContract/projections; METAMODEL-MAPPING-AUDIT.md projection table | Open, non-blocking: no new per-projection executable audit suite; retain historical projection evidence. |
| docs/agent/task.md:83 — Generate mapping SHA-256. | A | MappingLoader.validateMappingFingerprint; freeze-manifest.json | Complete within documented supported scope; evidence linked here. |
| docs/agent/task.md:84 — Create `mapping-audit.md`. | C | Core/Mapping/METAMODEL-MAPPING-AUDIT.md | Superseded by the named canonical audit/manifest; do not create duplicate baselines. |
| docs/agent/task.md:85 — Create `mapping-freeze-manifest.json`. | C | Core/Mapping/freeze-manifest.json | Superseded by the named canonical audit/manifest; do not create duplicate baselines. |
| docs/agent/task.md:86 — Record Ecore fingerprint expected by mapping. | A | MappingLoader.validateFingerprint; freeze-manifest.json | Complete within documented supported scope; evidence linked here. |
| docs/agent/task.md:87 — Mark status `LOCKED_BASELINE_V1`. | C | Core/Mapping/freeze-manifest.json uses FROZEN | Superseded by the named canonical audit/manifest; do not create duplicate baselines. |
| docs/agent/task.md:88 — Run full audit from clean checkout. | B | Current checkout gate differs from original external Python/EMF suite | Open, non-blocking candidate limitation: no tag/push; original external EMF/Python suite not rerun. |
| docs/agent/task.md:89 — Commit and merge Phase 0. | A | git history contains frozen Core baseline before immutable v1.0.0 | Complete within documented supported scope; evidence linked here. |
| docs/agent/task.md:92 — "Mapping V1 is canonical, structurally complete for the frozen metamodel, internally consistent, and has no known mapping ambiguity." | A | Frozen structural contract; concrete ambiguity is handled by HotfixBindingTest | Complete within documented supported scope; evidence linked here. |
| docs/agent/task.md:868 — Thesis demo can be repeated from clean checkout. | A | ReleasePackageIT and relocated clone gate; in-process Auction demo scope | Supported scripted in-process demo only; interactive standalone launcher remains excluded. |
| docs/superpowers/plans/2026-09-16-phase-14-15-completion.md:95 — Commit Phase 14 in coherent conventional commits and ensure the branch is clean. | C | Historical v1.0.0 plan; commits 19846fa5, a4951e91, 848e18e3; current hotfix report | Superseded for v1.0.1; do not repeat historical push/tag steps. |
| docs/superpowers/plans/2026-09-16-phase-14-15-completion.md:96 — Push `phase/14-auction-final-e2e`, merge it into `main`, and run the required post-merge module/full smoke test. | C | Historical v1.0.0 plan; commits 19846fa5, a4951e91, 848e18e3; current hotfix report | Superseded for v1.0.1; do not repeat historical push/tag steps. |
| docs/superpowers/plans/2026-09-16-phase-14-15-completion.md:97 — Push `main` and verify local/remote SHAs match. | C | Historical v1.0.0 plan; commits 19846fa5, a4951e91, 848e18e3; current hotfix report | Superseded for v1.0.1; do not repeat historical push/tag steps. |
| docs/superpowers/plans/2026-09-16-phase-14-15-completion.md:111 — Read the release operations document and inspect existing release/package conventions. | C | Historical v1.0.0 plan; commits 19846fa5, a4951e91, 848e18e3; current hotfix report | Superseded for v1.0.1; do not repeat historical push/tag steps. |
| docs/superpowers/plans/2026-09-16-phase-14-15-completion.md:112 — Add RED release-manifest/package-content tests or executable audit checks before packaging changes. | C | Historical v1.0.0 plan; commits 19846fa5, a4951e91, 848e18e3; current hotfix report | Superseded for v1.0.1; do not repeat historical push/tag steps. |
| docs/superpowers/plans/2026-09-16-phase-14-15-completion.md:113 — Complete install/workflow/architecture/limitations/compatibility/changelog documentation without duplicating sources of truth. | C | Historical v1.0.0 plan; commits 19846fa5, a4951e91, 848e18e3; current hotfix report | Superseded for v1.0.1; do not repeat historical push/tag steps. |
| docs/superpowers/plans/2026-09-16-phase-14-15-completion.md:114 — Build the release package from a clean checkout and audit its inventory/hashes. | C | Historical v1.0.0 plan; commits 19846fa5, a4951e91, 848e18e3; current hotfix report | Superseded for v1.0.1; do not repeat historical push/tag steps. |
| docs/superpowers/plans/2026-09-16-phase-14-15-completion.md:115 — Run the full build/tests, mapping audit, Auction E2E, and plugin load smoke defined by the repository. | C | Historical v1.0.0 plan; commits 19846fa5, a4951e91, 848e18e3; current hotfix report | Superseded for v1.0.1; do not repeat historical push/tag steps. |
| docs/superpowers/plans/2026-09-16-phase-14-15-completion.md:116 — Verify every end-to-end acceptance checkbox from executable evidence and record any honest supported-scope limitation. | C | Historical v1.0.0 plan; commits 19846fa5, a4951e91, 848e18e3; current hotfix report | Superseded for v1.0.1; do not repeat historical push/tag steps. |
| docs/superpowers/plans/2026-09-16-phase-14-15-completion.md:117 — Request independent release review and resolve every Critical/Important finding. | C | Historical v1.0.0 plan; commits 19846fa5, a4951e91, 848e18e3; current hotfix report | Superseded for v1.0.1; do not repeat historical push/tag steps. |
| docs/superpowers/plans/2026-09-16-phase-14-15-completion.md:118 — Commit/merge/push Phase 15, run post-merge release verification on `main`, create the repository-specified release tag, push it, and verify remote tag/package/SHA. | C | Historical v1.0.0 plan; commits 19846fa5, a4951e91, 848e18e3; current hotfix report | Superseded for v1.0.1; do not repeat historical push/tag steps. |
| ../.github/ISSUE_TEMPLATE/feature_request.md:41 — Criterion 1 | D | Reusable upstream issue template | Not a plugin release task. |
| ../.github/ISSUE_TEMPLATE/feature_request.md:42 — Criterion 2 | D | Reusable upstream issue template | Not a plugin release task. |

## Final hotfix validation

P1 FIXED; P2 FIXED; DoD RECONCILED. Full reactor: 271/271 tests PASS (0 failures/errors/skips),
repeated from a separate clean validation clone. Both release ZIPs (27 entries) are byte-identical:
SHA-256 `4271f497a9716011cac494803054aca029c503b0080e78540da1e25e4b527bcf`.
The same final facade regression tests on baseline code produce four expected failures
(rebuild, profile, reimport, valid explicit binding) and one passing failed-build control.
See ../../report.md and evidence/v1.0.1/validation.json for commands, scope and retained logs.
No hotfix blockers remain. No v1.0.1 tag or push was performed.
