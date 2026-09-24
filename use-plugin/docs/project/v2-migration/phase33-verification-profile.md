# Verification profile migration to V2

Component status: focused PASS; integrated parser/materialization acceptance OPEN.

The V1 profile's VSP001-VSP004 removed inheritance from Norm/Group/Role/Scheme
to Organisation. V2 defines none of those inheritance edges. VSP005 weakened
the old R047 Belief-to-TriggeringEvent lower bound; that V1 relation is absent
from V2. Applying these decisions to differently numbered V2 rules is invalid.

The active profile is JACAMO_VERIFICATION_PROFILE_V2 version 2.0.0, explicitly
bound to JaCaMo-agentmetamodel-v2__to__USE-v2.2. It makes **no structural
overrides**. The baseline's classes, types, multiplicities, independent order
projection and enums pass through unchanged. This is an identity profile, not
a relaxation to make old tests pass. A future change requires explicit review.
The loader rejects a mapping ID mismatch; the mapping-aware layer also rejects
cross-baseline profile use before applying any decision.

Default facade and active integration fixtures select this profile. loadV1 is
retained only for explicit historical tests; there is no fallback to it. The
historical profile's five decisions remain exercised with versioned V1 input.

Focused: 19 tests PASS (VerificationSemanticLayerTest, V2SemanticModelTest,
OrderProjectionTest, V2OppositeOrderingAuditTest, PreMigrationBaselineTest).
No Ecore, Mapping, golden digest, connector or lifecycle changes are involved.

The preceding 333-test full run is exhaustively classified in phase33-regression.json:
60 V1 profile incompatibilities (59 errors and one profile assertion), 6 V1
path/resource assumptions, 3 V1 mapping/projection assumptions, 3 stale
evidence/release expectations, zero new failing identities against the initial
migration baseline. Removing the profile incompatibility exposes later V1
parser/materialization/OCL assumptions; this is not yet a claim that those 59
end-to-end tests pass.

Frozen mapping loader controls are separated as HistoricalMappingLoaderTest.
They use the unchanged versioned historical bytes and retain all fingerprint,
source-identity, validation-precedence and single-snapshot assertions. Active
loader/registry/ordering controls continue in ActiveBaselineTest,
V2MappingAuditTest and OrderProjectionTest. Active projection tests are not
reclassified as historical simply because their migration is still pending.

Post-profile plugin regression: 192 tests, 9 failures, 62 errors (71 failing
identities, down from 72; no new failing identities). See
[observations](phase33-profile-regression.json). The 59 VSP001 errors disappeared;
later V1 Body/ExternalAction/materialization/OCL assumptions remain visible.
This run predates the seven historical loader path fixes.

One newly exposed **real V2 defect** was investigated: TraceBuilder used only
target association name for declaration trace IDs, so authoritative eOpposite
aliases collided with canonical declarations. Alias IDs now include their exact
source feature; canonical and historical IDs remain unchanged. A focused control
checks both source traces, distinct trace IDs and the shared membership target.
This is a correctness fix, not an evidence/golden rewrite.

Final component regression: 30/30 PASS after historical-path separation and alias
trace fix. Command: `mvn -B -pl use-plugin -Dtest=HistoricalMappingLoaderTest,VerificationSemanticLayerTest,ActiveBaselineTest,V2SemanticModelTest,OrderProjectionTest,V2OppositeOrderingAuditTest,PreMigrationBaselineTest test`.
