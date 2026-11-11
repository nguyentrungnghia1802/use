# Compact mapping v3 independent review

Date: 2026-11-11. Result: NOT ACCEPTABLE AS A COMPLETED MIGRATION.

Scope: current uncommitted compact implementation and its integration points, against the root plan P00–P09. No production/model/business code was changed by this review. Review helpers and receipts are confined to this directory. Existing work and the installation were preserved.

## Executed checks

1. Re-ran the seven declared compact test classes through the reactor. 29 tests passed, zero failures/errors/skips. See `focused-tests.log`. An initial PowerShell/Maven argument quoting failure is retained in `invocation-error.log`; it is not a test failure.
2. Compiled and ran `CompactReviewProbe.java` against the freshly compiled reactor classpath. It uses a synthetic Delivery/Parcel case, not Auction. Nine checks were executed: one setup/control check passed and eight required properties failed. See `probe-results.log` and `run-probe.py`.
3. Inspected production call sites, source resolver, example descriptors/mappings, coordinator and native runtime integration. Findings below distinguish executable reproduction from source review.
4. Read the installed plugin ZIP and compared all 58 compact class files with current compiled output. No missing/mismatched compact classes. The installed plugin SHA-256 is `dd1ea89bec8345f00cc28160e0990761ea2180b7885802b5d2dead38bb45a381`, matching the implementation report. This is class parity, not fresh class loading or a live runtime acceptance. See `installed-parity.json`.

No new live MAS or Computer Use run was performed: the missing runtime integration and component-level failures already establish that the claimed acceptance is incomplete. No full clean verify, unrelated test suite, package replacement, commit or push was performed.

## Findings, ordered by impact

### F1 [P1] The compact runtime components are not connected to the real event/evaluation path

Production references to `RuntimeInstanceLinker`, `AuthoritativeMSystemProjector`, `PhysicalCutFact` and compact `NativeVerificationCoordinator` are confined to their own new package; no actual runtime consumer invokes the link/project/evaluate sequence. Facade changes publish compact capability requests/policy, but that alone does not install runtime handling.

`JaCaMoRuntimeStateMapping.caseMapping()` still returns a runtime case mapping only for `program.xmlPlan()!=null`; a compact program has a null xmlPlan. `RuntimeEngineSessionOwner` similarly creates Task OCL bindings, task checkpoint processing and occurrence expansion only for xmlPlan. The compact route instead falls into older generic mapping code with different binding assumptions. Its bindings use raw source-reference strings, whereas `CanonicalObservationMappingBindings`/`JaCaMoCslMappingRules.javaClass` expect admitted normalized source IDs and source features.

References: `semantics/mapping/JaCaMoRuntimeStateMapping.java` around `caseMapping`; `semantics/runtime/RuntimeEngineSessionOwner.java:98`; `semantics/mapping/xml/GenericMappingEngine.java:178`; `semantics/mapping/JaCaMoCslMappingRules.java:109`.

Impact: compiling a model and creating an MSystem is not evidence that live callbacks populate that MSystem, drive native Task/Goal conditions or supply the existing history/UI/checkpoint inventory. P04–P06 are not proven complete. `CompactWorkflowIntegrationTest` stops after canonical model creation and does not test this missing runtime path.

### F2 [P1] The new coordinator approves missing Task evidence and fabricates success from a null status

`NativeVerificationCoordinator.java:126` assigns PASS/APPLICABLE when a selected Task rule has no receipt. At line 115 it treats ENABLED as sufficient for PASS even when handling TASK_POST. It selects receipts by Task name only, with no run/player/scheme/invocation correlation, and does not evaluate native Task PRE/POST or iStar propagation. Its required global check can consequently become PASS.

`PhysicalCutFact.java:69` converts an absent receipt status to `SUCCEEDED`.

Executable reproduction: a compiled plan with three selected Task checkpoint rules and no source receipts yields `recommendedAction=APPROVE`, with all Task checks PASS. A null receipt status becomes SUCCEEDED. These are failures in the new component; because F1 remains, this review does not claim that the actual currently deployed checkpoint service has already released a live cut through this component.

Required correction: connect to the real native semantic inventory, preserve applicability and evidence quality, and prove fail-closed behavior at the exact active checkpoint. Do not patch the test or merely change the final enum without implementing the missing evidence/semantic path.

### F3 [P1] Multiple physical instances are lost and object identity ignores workspace/incarnation

`RuntimeInstanceLinker.java:57` obtains one artifact via `PhysicalCutFact.findArtifactByClass()`, which uses `findFirst`. It does not iterate the source population. `RuntimeInstanceLinker.java:60` constructs identity from target class plus display name, omitting workspace/run/incarnation. The linker creates neither group/role links nor disposed-object mutations: the returned link/disposal lists remain empty. Actor lookup compares a declared source name directly with runtime agent names, which does not expand a shared program into its users.

Executable reproduction: two artifacts of the same class produce one object mutation. Equally named artifacts in different workspaces receive the identical target name `parcel_first`.

Impact: the core multi-auction/multi-workspace isolation requirement is not implemented. This is a generic identity defect, even without an Auction-name hard-code.

### F4 [P1] Source resolution accepts symbols that do not exist

`SourceSymbolCatalog.java:230` returns a new Agent symbol after lookup fails; line 235 constructs an ASL Goal without checking source presence; line 252 constructs a plan-region symbol even when the path/plan was never found. Property/field/operation and organisational member branches also manufacture symbols rather than requiring corresponding source evidence. Plan matching uses substring containment, and range checking allows an extra step (`stepCount + 1`).

Executable reproduction: an empty catalogue accepts `agent:does_not_exist` and `asl:missing.asl/plan:unknown/step:attempt[0..1]`.

Impact: Prepare can accept a mapping that cannot be tied to the selected source. Tests supplying manually registered catalogues do not establish correctness of real-source preparation, particularly for dynamically created artifacts or source paths/plan signatures.

### F5 [P1] Compact preparation bypasses native contract installation rather than replacing it

`CodeGroundedNativePipeline.java:31` skips `CanonicalUseBindingAdapter` for compact programs and constructs an empty `JaCaMoOclContractMapping.Result`. `GenericMappingEngine.java:159` constructs v3 rules with empty contracts/options; lines 170–171 build intentional element templates with empty receiver/source context fields and qualified raw target strings. The existing native session consumers still expect native IDs and receiver bindings, and have no compact implementation.

The new standalone coordinator evaluates class invariants/receipt strings, not the authored iStar Task PRE/POST, temporal marking, ANY/ALL realization graph and selected-scope requirements. Selected additional OCL bytes are retained as an asset but that is not proof that their clauses are compiled/installed on the compact route.

Impact: the report's statement that native OCL + iStar runtime verification is synthesized from the selected models is unsupported by the code. Do not fix this by adding a second evaluator or forcing all observed successful source steps to Complete.

### F6 [P1] Shipped case descriptors still select v2, and the new examples omit required semantics

Current `auction.mrv`, `hello.mrv` and `house.mrv` still select their original `*-mapping.xml` files. Adding `*-compact-mapping.xml` beside them did not migrate the default workflow.

Auction compact XML maps the two setup Tasks only; it does not map the remaining existing business Tasks or carry the D_bid ANY policy, exact setup/scheme scopes, realization views, selected requirements or OCL operation bindings. The compact specification has actorRuntime/requirements/milestones records but no implementation for much of that agreed runtime vocabulary. Several explicit requirements also use `provider="cartago"`/`"jason"`/`"moise"` instead of the actual versioned capability provider IDs; their successful parsing is not adapter admission.

Impact: normal user runs still exercise the old mapping, while selecting compact manually is not semantically equivalent. Case test registration of a few source symbols and assertion of binding counts is not frozen runtime equivalence or end-to-end case acceptance.

### F7 [P2] Projection rollback is incomplete and requested link mutations are ignored

`AuthoritativeMSystemProjector.java:92–97` deletes disposed objects without registering restoration; its own comment defers restoration. The method never processes the supplied link mutation list and silently skips an unknown attribute. These paths contradict the stated atomic projection guarantee.

Executable reproduction: delete an existing object in a transaction and call rollback; the object remains absent (`null`).

### F8 [P2] Invalid conversion can become a valid false value

`RuntimeInstanceLinker.java:130` uses `Boolean.parseBoolean` on arbitrary input. Invalid non-boolean text therefore becomes a known false Boolean rather than an explicit conversion diagnostic/undefined result. Number-to-integer paths also use `intValue`, risking silent truncation/overflow.

Executable reproduction: `not-a-boolean` converts to false. This can change a model condition's truth instead of preserving unavailable/invalid evidence.

### F9 [P2] Fingerprint/admission and parser validation remain incomplete

Source review: the plan fingerprint includes raw correspondence strings and selected models but omits actual source symbol digests, native engine profile, additional OCL bytes, resolved source/target semantics and full capability/rule parameters. `MappingInputs` compact read freezes JCM plus models but not referenced ASL/MOISE/Java assets; `fromFrozen` bypasses the v2 aggregate asset validation. `SemanticProgram` explicitly skips complement and binding validation whenever a compact plan is present. These omissions require targeted admission/replay tests before claiming immutable source ownership.

The compact reader checks required attributes but does not reject all unknown attributes/nested foreign namespaces or validate the policy vocabulary. These are source-review findings, not executed security exploits in this review.

## Hard-code / hidden XML assessment

No explicit Auction/Hello/House verdict branch or legacy XML overlay was found in the inspected new package. This is a limited source finding, not a complete proof across the product. The critical problems are missing general mechanisms, invented source symbols and unsafe defaults. In particular, the compiler chooses the first mapped artifact class as the primary class for source plan steps, which is an unjustified inference when more than one artifact class is present.

The `provesZeroRuntimeDependenceOnLegacyMappingXml` test uses manually supplied catalogues. It does not exercise a full source import/runtime path with a poisoned old pipeline resolver. Its result therefore cannot establish P03/P07 as described in the root plan.

## Reproduction

From the USE repository root:

```powershell
mvn.cmd -o -B -pl use-plugin -am test '-Dtest=CompactMappingReaderTest,SourceAndTargetCatalogTest,ObservationPlanCompilerTest,RuntimeInstanceLinkerTest,NativeVerificationCoordinatorTest,CompactWorkflowIntegrationTest,CaseExamplesMigrationAndGenericityTest' '-Dsurefire.failIfNoSpecifiedTests=false' '-Dassembly.skipAssembly=true' '-Dmaven.antrun.skip=true'
python -X utf8 use-plugin/verification/compact-review-20261111/run-probe.py
python -X utf8 use-plugin/verification/compact-review-20261111/inspect-package.py
```

The Java probe prints FAIL for unmet requirements; its normal process exit indicates that the probe ran, not that acceptance passed. Read `REVIEW_FAILURES=8`. It does not inject a production verdict or modify production conditions. The helper obtains the current test classpath from the selected Surefire receipt; run the focused tests first.

## Acceptance conclusion

The reader, typed correspondence structures and some compiler scaffolding exist. The installed compact classes match current output. However, the implementation report's claim that P00–P09 are fully complete is contradicted by executable counterexamples and missing runtime integration. It is not ready for acceptance as the compact replacement of the existing verification system.

Repair order: source/admission validation -> typed identity/scope/projection -> real native Task/Goal/OCL integration -> exact checkpoint evidence -> complete migrated policies/descriptors -> full frozen/live/UI/package acceptance. Preserve existing valid v2 behavior and all source evidence while doing so. Do not switch the normal shipped descriptors to the incomplete route as a cosmetic completion step.
