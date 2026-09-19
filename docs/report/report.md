# v1.0.1 Correctness Hotfix

## P1
Root cause: DefaultJaCaMoFacade replaced its Workspace in rebuild/import/profile load while RuntimeMirrorService retained the old RuntimeMutationEngine and RuntimeVerificationEngine. Both engines own final MSystem/TraceIndex references; the verifier also owns the old ConstraintRegistry.

Fix: all three replacement paths use one installWorkspace lifecycle. A failed build leaves the previous live workspace untouched. Successful replacement closes the subscription and drains/stops its queue, transfers exact runtime keys and aliases to corresponding semantic objects, installs new mutation/verification consumers, and synchronizes the connected transport's authoritative snapshot before returning LIVE. Full verification then runs on that same active MSystem. No reconnect or duplicate transport listener is introduced. A failed snapshot switches to ERROR and disconnects; the published workspace and consumers still agree. Late callbacks are rejected by their original stream's observer/engine, never by the replacement consumers. Pending operation checks end at the stream boundary, as for existing resync behavior.

Files: DefaultJaCaMoFacade.java, runtime/RuntimeMirrorService.java, trace/TraceIndex.java under use-plugin/src/main/java/org/tzi/use/plugins/jacamo.

Tests: HotfixLifecycleTest covers LIVE rebuild, OCL load, reimport, continued violation events, preserved runtime aliases, one subscription, resync/reconnect drift=0, and failed-build preservation. RuntimeFoundationTest adds late-callback isolation; existing queue/order/shutdown/error tests remain enabled.

Result: final validation below. On untouched baseline code, the final lifecycle regression tests fail separately for rebuild, profile load and reimport; the failed-build control passes.

## P2
Root cause: production StaticProjectImporter called SemanticResolver directly and never read the project's binding.json; ExactSemanticResolver was only used separately.

Fix: after source extraction, StaticProjectImporter reads project-root binding.json with current semantic-source hashes. SemanticResolver validates source/target candidates, duplicate entries and stale status, and uses ExactSemanticResolver for typed reference resolution. No binding retains the original resolver path. Invalid or malformed bindings produce BINDING_INVALID; changed/missing source hashes produce BINDING_STALE. Remaining operation ambiguity stays RESOLUTION_AMBIGUOUS. No names or Auction-specific targets are guessed. The chosen original SemanticId flows into SemanticReference and TraceBuilder unchanged.

Files: extraction/StaticProjectImporter.java and extraction/SemanticResolver.java.

Tests: HotfixBindingTest goes through the real importer and facade, covering ambiguity without binding, successful valid binding, exact resolved identity/trace, absent target, wrong-kind target, malformed JSON and stale hash. Existing StaticProjectImporterTest protects no-binding unambiguous behavior; TraceBindingTest protects the resolver/store contract.

Result: final validation below. The same valid-binding regression fails with RESOLUTION_AMBIGUOUS against the unchanged baseline.

## DoD reconciliation

See [the complete CHECKBOX / STATUS / EVIDENCE / ACTION table](../../use-plugin/release/HOTFIX-1.0.1.md).
Inventory: 65 canonical open items, 11 historical plan items, 2 upstream template items. Classification: A=56, B=3, C=16, D=3. Only the 56 evidence-backed A items were marked complete. Historical structural audit evidence is distinguished from fresh Java gates.

B items retained: publication/tag/push has not occurred; the original external Python/EMF audit suite is not present/rerun here; no new comprehensive per-projection executable audit suite was added. These are documented candidate limitations, not P1/P2 blockers. Superseded audit filenames/status values point to the existing canonical FROZEN manifest rather than duplicate baselines.

## Reproducibility

Paths: Core/Mapping/README.md now gives commands that run from this USE checkout at any path, using MappingLoader's module-root resolution and reactor compiler. Removed commands referenced scripts only present in the original mapping repository. Canonical mapping/schema/Ecore/freeze bytes remain unchanged; original JSON source paths and historical audit links remain provenance, not runnable local instructions.

Archives: project.build.outputTimestamp is pinned to 2026-09-18T00:00:00Z for JAR and assembly. Separate-build comparison and SHA-256 results are recorded below. Reproducibility is scoped to identical source bytes/dependencies/JDK/Maven; no cross-toolchain claim is made.

## Full validation

Command: `mvn --batch-mode clean verify` from the repository root, repeated in a separate local clone at a different path with no build outputs. The clone contains a local validation commit of the hotfix snapshot solely to give it a clean worktree; the user's main checkout was not committed, tagged or pushed. Both runs use the existing Maven dependency cache, not an empty dependency repository.

Baseline RED command: `mvn --batch-mode -pl use-plugin -am '-Dtest=HotfixLifecycleTest,HotfixBindingTest' '-Dsurefire.failIfNoSpecifiedTests=false' test` in an unchanged baseline clone plus the new tests. Result: 5 tests, 4 expected failures, 0 errors; all three P1 paths and valid P2 binding fail at their correctness assertions.

Evidence: [baseline-red.log](../../use-plugin/release/evidence/v1.0.1/baseline-red.log), [final-verify.log](../../use-plugin/release/evidence/v1.0.1/final-verify.log), [relocated-final-verify.log](../../use-plugin/release/evidence/v1.0.1/relocated-final-verify.log). Intermediate failed runs are retained and are not final PASS evidence.

Fresh Ecore XML recount: 37 classes, 67 attributes, 63 references, 14 inheritance edges. SHA-256: c0aafab786c5ff3fcb468aeaf1b18b62865292e6590ffca2b9b2e962a9067fe7. MappingTransformationTest exercises frozen schema/hash/identity checks, negative mutations and USE compilation. ReleasePackageIT checks all archive entries against declared sources, embedded canonical resources, checksum sidecar, USE plugin load and isolated child-JVM loading. AuctionSourceRuntimeTest and LiveJaCaMoAuctionIntegrationTest exercise in-process live Auction behavior.

Tests: **271/271 PASS** in both builds: use-core 13, use-gui 130, use-plugin 128 (125 unit + 3 release integration). Failures=0, errors=0, skipped=0. All five reactor projects SUCCESS.

Result: **V1.0.1 HOTFIX STATUS: PASS**; P1 FIXED; P2 FIXED; DoD RECONCILED; reproducibility FIXED for the tested identical toolchain/source/dependencies.

The two 27-entry release ZIPs are byte-identical. SHA-256: `4271f497a9716011cac494803054aca029c503b0080e78540da1e25e4b527bcf`.
The relocated validation clone is clean after its build; its local-only snapshot commit is `e487112e6243b2bdafe2571bb215b33def716a18`.
Machine-readable counts/hash comparison: [validation.json](../../use-plugin/release/evidence/v1.0.1/validation.json).
Final package: [use-jacamo-plugin-1.0.1.zip](../../use-plugin/target/use-jacamo-plugin-1.0.1.zip).
Remaining blockers: none within the agreed hotfix scope. Publication/tag/push awaits a separate user request.


## Remaining known limitations

- Standalone external .jcm launch from the GUI and interactive installed GUI workflows were not exercised; real in-process Jason/CArtAgO/Moise APIs are the supported runtime evidence.
- Full normative/deontic lifecycle, arbitrary Java body-to-OCL translation, enforcement/control agents and UI redesign remain outside this hotfix.
- Workspace replacement deliberately ends in-flight operation correlations and starts from an authoritative snapshot, matching resync semantics. Historical runtime report lists are workspace-local; this hotfix preserves semantic trace bindings, not a cross-workspace report archive.
- Reimport of a different semantic project can fail authoritative snapshot application and disconnect with ERROR; it cannot silently keep a stale LIVE model. Runtime aliases transfer only to identical semantic and USE targets.
- External historical Python/EMF gates and exhaustive per-projection research verification were not rerun. Current Java mapping gates and fresh Ecore counts are reported separately.
- No tag/push requested or performed. Version 1.0.1 remains a local release candidate. The original v1.0.0 tag object is 0b9f203ba46770a430672aab4b97c129495a87c1, pointing to 848e18e34b048bac1bfcbc491b14200a62abd25d.

## Changed files

Original user file USE_JaCaMo_Project_Orientation_AZ.md was untouched.

- `use-plugin/CHANGELOG.md`
- `use-plugin/Core/Mapping/README.md`
- `use-plugin/README.md`
- `use-plugin/compatibility.json`
- `use-plugin/docs/agent/task.md`
- `use-plugin/docs/project/15-build-release-operations.md`
- `use-plugin/pom.xml`
- `use-plugin/release/release-manifest.json`
- `use-plugin/src/assembly/release.xml`
- `use-plugin/src/main/java/org/tzi/use/plugins/jacamo/DefaultJaCaMoFacade.java`
- `use-plugin/src/main/java/org/tzi/use/plugins/jacamo/extraction/SemanticResolver.java`
- `use-plugin/src/main/java/org/tzi/use/plugins/jacamo/extraction/StaticProjectImporter.java`
- `use-plugin/src/main/java/org/tzi/use/plugins/jacamo/runtime/RuntimeMirrorService.java`
- `use-plugin/src/main/java/org/tzi/use/plugins/jacamo/trace/TraceIndex.java`
- `use-plugin/src/main/resources/useplugin.xml`
- `use-plugin/src/test/java/org/tzi/use/plugins/jacamo/release/ReleasePackageContractTest.java`
- `use-plugin/src/test/java/org/tzi/use/plugins/jacamo/release/ReleasePackageIT.java`
- `use-plugin/src/test/java/org/tzi/use/plugins/jacamo/runtime/RuntimeFoundationTest.java`
- `report.md`
- `use-plugin/release/HOTFIX-1.0.1.md`
- `use-plugin/src/test/java/org/tzi/use/plugins/jacamo/HotfixBindingTest.java`
- `use-plugin/src/test/java/org/tzi/use/plugins/jacamo/HotfixLifecycleTest.java`
- `use-plugin/release/evidence/v1.0.1/` (retained build/reproduction logs and machine-readable validation)
