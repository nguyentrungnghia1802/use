# Phase 29–35 V2 working acceptance

Status: **DONE for Phase 29–35 and their downstream-dependent gates**.
Clean and post-merge full reactors PASS 350/350; phase branches merged and pushed.
This is working-baseline acceptance, not a final V2 freeze/release.

## Cross-layer consistency review

| Boundary | Contract and executable evidence |
|---|---|
| Ecore → Mapping | Native EMF exact source inventory, enum/default/bounds/opposite checks; V2EcoreAuditTest, V2MappingAuditTest, OrderProjectionTest |
| Mapping → IR | ActiveBaseline snapshot/fingerprint selection; descriptor-derived kinds, inherited features, exact enum/datatype validation; ActiveBaselineTest, V2SemanticModelTest |
| Parser → IR | Source spans/hash, ProjectDeclaration outside EClass, sourceFacts boundary, instance-qualified symbols; V2ExtractionTest, StaticProjectImporterTest |
| IR → resolution | Exact candidate set and owner suffix; unresolved/ambiguous facts stay explicit; SemanticIdTest, TraceBindingTest, HotfixBindingTest |
| IR → transformation | V2 projection evidence, explicit defaults/requiredness, canonical opposite membership; MappingTransformationTest, V2MaterializationTest |
| Transformation → text/direct | Shared plan, actual SOIL execution compared with direct MSystemState in Auction and CounterTeam; no duplicate membership; V2MaterializationTest, OrderProjectionTest |
| Membership → order | Independent ranks for each authoritative direction; rank validity/bijection and rank-derived navigation; mandatory native V2OppositeOrderingAuditTest retains every counterexample |
| Transformation → trace | Exact mapping/source identity, escaped target declarations, membership/order links, defaults and per-instance projections; TraceBindingTest and V2MaterializationTest |
| Trace → OCL | Exact source-feature query binding; V2 optional Action.operation contract, guard sourceFacts provenance and explicit unsupported semantics; ConstraintOclTest, ConstraintClosureTest, OrderProjectionTest |
| Trace → runtime | V2 target fingerprint/anchor validation, exact trace before mutation, rank-only events, reconnect/resync; RuntimeMappingTest, V2RuntimeOrderTest, RuntimeFoundationTest |
| Runtime → real components | Unchanged connector mechanics; Auction and CounterTeam component callbacks/resync; LiveJaCaMoAuctionIntegrationTest, CounterTeamIntegrationTest |
| Package → installed plugin | V2 canonical ZIP/JAR byte parity, checksum, plugin discovery and isolated loader without Maven test dependencies; ReleasePackageContractTest, ReleasePackageIT |

P31 disposition review: every structural source is MAPPED. The alternative
INTENTIONALLY_NOT_MAPPED, REVIEW_REQUIRED and UNSUPPORTED categories have no
structural entries; their audit is N/A-complete, not a claim of hidden unsupported
structural classes. VP001–003 remain conditional on exact external type/signature
facts. VP004–006 preserve structural anchors, including Property.percepts and
AGoal.organizationalGoal; they do not infer runtime causal/deontic behavior.
VP007 preserves Norm values/relations, never interprets strings as OCL. Inheritance
is empty in current V2; synthetic reconciled subtype tests cover generic support.
Generated reverse helpers are target support; source navigation binds only exact
Mapping source identities and reconstructed order queries.

## Failure disposition

The 333-test snapshot had 7 failures and 65 errors: 60 V1 profile assumptions
(including 59 VSP001 errors), 6 V1 resource assumptions, 3 V1 mapping/projection
assumptions and 3 stale evidence expectations. Its complete classification remains
in phase33-regression.json. Follow-up snapshots retain their original results.
Implementation defects exposed during migration (alias trace collision and order
trace identifier schema mismatch) were fixed with regression controls. Historical
V1 goldens were not overwritten; V2 outputs were accepted after actual semantic
parity, positive/negative OCL and trace persistence checks. The first clean package
run exposed one new metadata inconsistency, recorded in phase35-clean-preflight.json;
explicit null tag synchronization fixed it without weakening the consistency gate.

## Supported boundaries

All valid V2 structural instances retain the generic membership/order representation;
no Ecore or USE core change and no case-specific production branch was introduced.
Unknown inverse source order remains explicit until authoritative evidence exists.
Rank-only runtime events require complete exact orders. Bare ordered membership or
endpoint changes require authoritative projection resync; no connector insertion
order is promoted into source order. This is not an unrestricted live source-order
adapter claim. Unordered/non-opposite and generic lifecycle controls remain tested.

Original Auction standalone plan/deadline equivalence remains unsupported. Real
in-process component evidence and historical derived launcher control are distinct.
The V2 working archive has no release tag/freeze. Later roadmap phases are not
silently marked complete by this Phase 29–35 acceptance.

## Documentation and compatibility review

Updated active baseline, IR/extraction/transformation/trace/OCL/runtime/testing/build
records, task checklist, README, limitations, changelog, compatibility and package
manifest. Historical immutable V1 inputs, goldens and earlier run counts are retained.
Binding/trace/RuntimeEvent serialization and upstream runtime versions are unchanged;
Runtime Mapping target schema moves to 3.0.0 with V2 fingerprints. Phase 29 gates
close only after downstream work, preserving the user-approved sequential schedule.

## Final clean regression

`mvn -B clean verify` at c77db92a: **350 tests, 0 failures, 0 errors, 0 skipped**.
Core: 13; GUI: 130; plugin: 204 unit/component + 3 package integration tests.
Python evolution/diff: **11/11 PASS**. Exact Ecore self-diff is SOURCE_COMPATIBLE.
See [machine-readable run and all 72 rerun dispositions](phase35-final-regression.json).
64 old failing identities now pass unchanged; seven loader controls moved to the
explicit historical test class and one profile control was renamed historical;
all eight also pass. No valid test was disabled. New V2 controls cover the active path.

Package tests compare every declared ZIP entry with its source, verify the sidecar,
load the extracted JAR in USE and load canonical V2/runtime contracts from an
isolated child without the Maven dependency classpath. This is a clean build with
the existing dependency cache, not a new fresh-cache/toolchain portability claim.

## Post-merge checkout portability repair

The first post-merge run exposed two historical trace hash failures. The original
Jackson output has CRLF in its JSON body and a final LF. A broad text/eol=lf Git
rule had normalized those files in the index; the original worktree passed until
branch checkout recreated normalized bytes. Both original byte streams were
recovered and verified against the unchanged recorded SHA-256. Specific -text
attributes now preserve them. No manifest hash, golden expectation, JSON value or
V2 behavior was changed. PreMigrationBaselineTest remains the exact-byte regression.
Post-merge rerun remains required after this portability repair.

## Workflow closure

Migration merged into main at f05a5c6e; exact historical-byte portability repair
merged at cd185d3b. Post-merge `mvn -B verify`: **350/350 PASS**, including historical
hash controls and all three package integration tests. See
[post-merge evidence](phase35-postmerge-regression.json). Main cd185d3b and every
Phase 29–35 branch were pushed successfully to origin. All phase component and
integration checklists now satisfy their dependencies in the approved order.
Subsequent closure commits synchronize documentation only; production sources,
canonical inputs, golden expectations and build configuration match the passing run.
No semantic decision blocker remains for this Phase 29–35 scope.
