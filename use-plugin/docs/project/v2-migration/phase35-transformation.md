# Phase 35 — V2 transformation and consumer reconciliation

Status: implementation and regression in progress; full phase closure OPEN.
Ecore V2 and Mapping 2.2.0 bytes/schema are unchanged. No frozen release is claimed.

## Projection and materialization contract

VP001 consumes Artifact.type plus confirmed source evidence. VP002 consumes
Property.name and external typed value evidence, only for a supported scalar
property (arity one). VP003 consumes Operation.name/arity plus the exact ordered
Java signature from sourceFacts; a mismatched arity or unknown result type cannot
produce an invented operation signature. Guards remain source facts, not Operations.
VP004–007 reuse the existing V2 structural relations and preserve Norm data.

Concrete classes reserve structural/order-projection names. Members reserve
inherited Artifact attributes and navigations. Repeated instances of one concrete
type share target declarations while retaining each source projection binding.
Conflicting declarations fail explicitly. No case-study names enter production logic.

InstancePlanner reads requiredness and explicit defaults from the validated Mapping.
Source values win; only sourceExplicitDefaultLiteral supplies an absent value.
Enum defaults decode through exact declared literal spellings. Missing required
values produce MATERIALIZATION_REQUIRED_ATTRIBUTE_MISSING; no value/link is invented.
Association membership and generic independent source ordering remain separate.

Trace records now cover ordinary membership links and explicit-default assignments,
alongside source objects and order entries/links. Repeated projection declarations
have separate source traces. An integration test exposed a pre-existing order-trace
serialization defect: order object identifiers had been reused as trace identifiers
and violated trace-v1's trace:<24 hex> contract. Trace IDs now use the existing trace
hash convention; order object IDs, ranks, membership, Ecore and schema are unchanged.

## Dependent constraint consumers

ConstraintExtractor reads Plan.context and owner-qualified Artifact guard sourceFacts.
The exact original guard slice anchors provenance. Primitive signature equality,
pure-body restrictions and unsupported Java arithmetic/reference semantics remain.
Jason applicability is still UNSUPPORTED; Norm text is never auto-translated.

The active core profile is jacamo-core-v2.ocl. V1 ExternalAction.Expression has no
V2 equivalent, and optional Action.operation (R027, 0..1) cannot become required by
renaming an old rule. The V2 rule checks VP004's evidenced condition: a present
operation anchor requires EXTERNAL kind. Unresolved operation completeness stays a
resolver/diagnostic concern. The old profile remains historical. The new rule uses
single-valued membership, not ordered navigation; order queries remain rank-derived.

CrossDimensionalVerifier derives relations from the V2 registry's dimensions and
exact source identities; reverse aliases respect canonical endpoint orientation.

## Evidence

- Projection/loader/order focused regression: 29 tests PASS before the final trace additions.
- Actual SOIL versus direct API parity for Auction and CounterTeam: every object,
  attribute and link compared; structure and invariants PASS. Source defaults and
  explicit source overrides are checked separately.
- Repeated concrete types, structural name collisions and nonscalar property
  rejection have dedicated tests.
- Guard sourceFacts migration: ConstraintClosureTest 4/4 PASS, including unsupported
  impure bodies, arithmetic, mismatched names and distinct shared-guard IDs.
- Plugin regression snapshot: 201 tests, 8 failures, 3 errors; down from 67 to 11
  failing identities. See phase35-regression.json. This is not full-suite acceptance.

Remaining gates: finish trace persistence regression, review V2 goldens only after
semantic/parity proof, reconcile active evidence/resource paths and runtime binding,
rerun full reactor and synchronize dependent gates. Historical V1 artifacts/hashes
remain intact. No downstream gate is closed by this progress record.

## Reviewed static golden migration

The historical Auction model/command hashes remain unchanged in both the historical
manifest and old golden properties; PreMigrationBaselineTest replays those artifacts.
New static fingerprints live separately in src/test/resources/golden/v2. Their review
followed V2MaterializationTest's actual SOIL/direct state comparison for both projects:
V2 enums/kinds and keyword escapes, 24 source objects plus 24 independent order rows
in Auction, canonical membership, explicit defaults, three projected operations and
one Boolean slot. No GuardOperation/Context/Body/Project EClass is recreated. The
unowned original Moise plan remains diagnosed/source-only. OCL/trace golden review
is still pending and is not implied by the two accepted static fingerprints.

Final component run for this unit: 43 tests, 0 failures/errors/skips via
`mvn -B -pl use-plugin -Dtest=V2MaterializationTest,InstanceMaterializationTest,TraceBindingTest,ConstraintClosureTest,ConstraintOclTest,DefaultJaCaMoFacadeTest,OrderProjectionTest,V2OppositeOrderingAuditTest,CrossDimensionalVerifierTest,CounterTeamIntegrationTest test`.
This includes trace schema persistence, V2 core positive/negative and optional-link
controls, facade import, CounterTeam real component callbacks/reconnect and order
projection regression. CounterTeam's scalar runtime control still uses the legacy
runtime mapping adapter, so this does not close the V2 default runtime binding gate.

## Downstream completion follow-up

Default runtime V2 reconciliation and reviewed OCL/trace/diagnostic golden evidence
are now implemented; see [runtime contract](phase35-runtime-targets.md). The earlier
OPEN descriptions above identify the state of the cf3cdfc8 component commit.
Current package uses V2 canonical resources and WORKING_V2_NOT_RELEASED status.
Clean full reactor and merged acceptance remain pending.
