# Exposed attribute audit — native projection 3.0.0


UI follow-up on 2026-10-06 retires Workbench Goal/Trace/Diagnostics displays.
The attribute/identity contract below is preserved; metadata and immutable Goal
evidence remain backend APIs. Earlier UI evidence describes the preceding revision.

Specification: [current task](task.md). Preflight: main at e68680c5; only task.md
was already modified, with empty staged diff. That replacement specification is retained.
Frozen metamodel/mapping, upstream JaCaMo and USE core/diagram implementation are unchanged by this cleanup.

Removed from exposed USE != deleted from system.

## Root cause and final boundary

DomainProjection.installBases installed technical attributes on every base;
NativeUseStateBuilder, MoiseDomainProjection and NativeRuntimeMutationEngine wrote
identity/provenance into native attribute values. Goal View, operation incarnation
checks, snapshots, replay and several OCL rules read them back. Hiding attributes
in a diagram would leave that schema and runtime coupling intact.

The same pipeline now exposes only semantic-contract domain values and official
application-observable verification state. ProjectionAttribute annotations declare
category/provenance. Application observables use verified provider/type/ownership
proof and PropertySource anchors; no generic technical-name blacklist exists.
The only property rename reservation is the real inherited Artifact.name collision
(and USE identifier syntax). An observable literally named semanticId, uuid,
sourceLayer or runtimeIdentity is accepted as domain state and cannot mutate bindings.

NativeObjectBindings is the single exact alias index plus per-native-object metadata.
It replaces the former plain semanticObjectIndex map, not the native MSystem.
Copies participate in rollback/baseline restoration; removal forgets all exact aliases
and metadata. Declaration/UUID redirect checks, operation PRE/POST incarnation and
workspace path resolution read internal bindings. No bare-name runtime fallback.
Full raw beliefs remain internal; selective current domain Belief projection is unchanged.

Snapshots 1.2.0 freeze attributes and bindingMetadata separately before requesting
pause. Violation tracing reads the failing cut's specification identity, not a later
live object attribute. Goal View and Trace/Diagnostics expose identity/provenance
from the binding/trace infrastructure. Trace table selection/filtering use the same
presented immutable row list. Official unavailable Moise spans remain line 0;
we do not guess XML lines. Existing all-agent ExecutionControl ACK, confirmation,
resume/resync and in-flight CArtAgO/Moise limitations remain in force.

Replay 2.0.0 hashes bindings.json alongside model.use/baseline.cmd/constraints.ocl/
runtime.jsonl. Aliases address object names only inside that exact hashed baseline,
with complete object-set, classifier and primary-identity validation. This is an
explicit binding manifest, not semantic matching by name. Older replay schemas are
rejected explicitly; the removed technical-attribute reader is not retained.
Source evidence/projection provenance is retained in TraceIndex and binding metadata.

## Attribute usage inventory

Search scope included current production, mutation, trace/binding, Goal/Workbench UI,
native model/JSON/SOIL export, replay/reanalysis, generated/core/external OCL and tests.
Historical test-only/frozen mappings are preserved. Runtime usage below means either
typed source materialization or an actual runtime consumer; 'technical' OCL usage
was an identity/provenance check, not a required domain String field.
DOMAIN/VERIFICATION_STATE stay exposed. IDENTITY_TRACE/RUNTIME_BINDING/
SOURCE_PROVENANCE/DUPLICATED_RELATION move internal. No DEBUG_ONLY or UNKNOWN
field was retained without semantic evidence.

| Class | Attribute | Category | Used by OCL? | Used by runtime? | Decision |
| --- | --- | --- | --- | --- | --- |
| Agent | host | SOURCE_PROVENANCE | no direct attribute dependency required | yes | internal binding/trace |
| Agent | name | DOMAIN | domain/native navigation | yes | KEEP exposed |
| Agent | semanticId | IDENTITY_TRACE | technical selectors/context; migrated | yes | internal binding/trace |
| Agent | sourceUri | SOURCE_PROVENANCE | no direct attribute dependency required | yes | internal binding/trace |
| AgentGoal | literal | DOMAIN | domain/native navigation | yes | KEEP exposed |
| AgentGoal | semanticId | IDENTITY_TRACE | no direct attribute dependency required | yes | internal binding/trace |
| AgentGoal | sourceLayer | SOURCE_PROVENANCE | no direct attribute dependency required | yes | internal binding/trace |
| Artifact | artifactTypeSemanticId | RUNTIME_BINDING | no direct attribute dependency required | yes | internal binding/trace |
| Artifact | creatorAgentSemanticId | SOURCE_PROVENANCE | no direct attribute dependency required | yes | internal binding/trace |
| Artifact | name | DOMAIN | domain/native navigation | yes | KEEP exposed |
| Artifact | semanticId | IDENTITY_TRACE | technical selectors/context; migrated | yes | internal binding/trace |
| Artifact | uuid | RUNTIME_BINDING | no direct attribute dependency required | yes | internal binding/trace |
| Artifact | workspaceSemanticId | DUPLICATED_RELATION | no direct attribute dependency required | yes | internal binding/trace |
| AuctionArt | currentBid | VERIFICATION_STATE | domain/native navigation | yes | KEEP exposed |
| AuctionArt | currentWinner | VERIFICATION_STATE | no direct attribute dependency required | yes | KEEP exposed |
| AuctionArt | maxValue | VERIFICATION_STATE | domain/native navigation | yes | KEEP exposed |
| AuctionArt | task | VERIFICATION_STATE | no direct attribute dependency required | yes | KEEP exposed |
| AuctionArtifact | best_bid | VERIFICATION_STATE | no direct attribute dependency required | yes | KEEP exposed |
| AuctionArtifact | running | VERIFICATION_STATE | domain/native navigation | yes | KEEP exposed |
| AuctionArtifact | task | VERIFICATION_STATE | no direct attribute dependency required | yes | KEEP exposed |
| AuctionArtifact | winner | VERIFICATION_STATE | no direct attribute dependency required | yes | KEEP exposed |
| Belief | literal | DOMAIN | domain/native navigation | yes | KEEP exposed |
| Belief | semanticId | IDENTITY_TRACE | technical selectors/context; migrated | yes | internal binding/trace |
| Belief | sourceLayer | SOURCE_PROVENANCE | no direct attribute dependency required | yes | internal binding/trace |
| GUIConsole | numMsg | VERIFICATION_STATE | no direct attribute dependency required | yes | KEEP exposed |
| Group | name | DOMAIN | domain/native navigation | yes | KEEP exposed |
| Group | semanticId | IDENTITY_TRACE | no direct attribute dependency required | yes | internal binding/trace |
| Mission | id | DOMAIN | no direct attribute dependency required | yes | KEEP exposed |
| Mission | max | DOMAIN | no direct attribute dependency required | yes | KEEP exposed |
| Mission | min | DOMAIN | no direct attribute dependency required | yes | KEEP exposed |
| Mission | name | DOMAIN | domain/native navigation | yes | KEEP exposed |
| Mission | schemeInstanceIdentity | DUPLICATED_RELATION | technical selectors/context; migrated | yes | internal binding/trace |
| Mission | schemeSpecSemanticId | IDENTITY_TRACE | technical selectors/context; migrated | yes | internal binding/trace |
| Mission | semanticId | IDENTITY_TRACE | no direct attribute dependency required | yes | internal binding/trace |
| Mission | sourceLayer | SOURCE_PROVENANCE | no direct attribute dependency required | yes | internal binding/trace |
| Mission | specSemanticId | IDENTITY_TRACE | no direct attribute dependency required | yes | internal binding/trace |
| Organization | name | DOMAIN | domain/native navigation | yes | KEEP exposed |
| Organization | semanticId | IDENTITY_TRACE | no direct attribute dependency required | yes | internal binding/trace |
| OrganizationalGoal | arguments | DOMAIN | no direct attribute dependency required | yes | KEEP exposed |
| OrganizationalGoal | decompositionOperator | DOMAIN | domain/native navigation | yes | KEEP exposed |
| OrganizationalGoal | description | DOMAIN | no direct attribute dependency required | yes | KEEP exposed |
| OrganizationalGoal | goalType | DOMAIN | no direct attribute dependency required | yes | KEEP exposed |
| OrganizationalGoal | id | DOMAIN | no direct attribute dependency required | yes | KEEP exposed |
| OrganizationalGoal | minAgentsToSatisfy | DOMAIN | no direct attribute dependency required | yes | KEEP exposed |
| OrganizationalGoal | name | DOMAIN | domain/native navigation | yes | KEEP exposed |
| OrganizationalGoal | orderInParent | DOMAIN | domain/native navigation | yes | KEEP exposed |
| OrganizationalGoal | runtimeState | VERIFICATION_STATE | domain/native navigation | yes | KEEP exposed |
| OrganizationalGoal | schemeInstanceIdentity | DUPLICATED_RELATION | technical selectors/context; migrated | yes | internal binding/trace |
| OrganizationalGoal | schemeSpecSemanticId | IDENTITY_TRACE | technical selectors/context; migrated | yes | internal binding/trace |
| OrganizationalGoal | semanticId | IDENTITY_TRACE | technical selectors/context; migrated | yes | internal binding/trace |
| OrganizationalGoal | sourceLayer | SOURCE_PROVENANCE | no direct attribute dependency required | yes | internal binding/trace |
| OrganizationalGoal | specSemanticId | IDENTITY_TRACE | no direct attribute dependency required | yes | internal binding/trace |
| OrganizationalGoal | stateEvidence | SOURCE_PROVENANCE | technical selectors/context; migrated | yes | internal binding/trace |
| OrganizationalGoal | ttf | DOMAIN | no direct attribute dependency required | yes | KEEP exposed |
| Scheme | arguments | DOMAIN | no direct attribute dependency required | yes | KEEP exposed |
| Scheme | goalStateEvidence | SOURCE_PROVENANCE | no direct attribute dependency required | yes | internal binding/trace |
| Scheme | name | DOMAIN | domain/native navigation | yes | KEEP exposed |
| Scheme | runtimeIdentity | RUNTIME_BINDING | no direct attribute dependency required | yes | internal binding/trace |
| Scheme | semanticId | IDENTITY_TRACE | technical selectors/context; migrated | yes | internal binding/trace |
| Scheme | sourceLayer | SOURCE_PROVENANCE | no direct attribute dependency required | yes | internal binding/trace |
| Scheme | specSemanticId | IDENTITY_TRACE | technical selectors/context; migrated | yes | internal binding/trace |
| Workspace | environmentSemanticId | RUNTIME_BINDING | no direct attribute dependency required | yes | internal binding/trace |
| Workspace | fullName | RUNTIME_BINDING | no direct attribute dependency required | yes | internal binding/trace |
| Workspace | name | DOMAIN | domain/native navigation | yes | KEEP exposed |
| Workspace | semanticId | IDENTITY_TRACE | technical selectors/context; migrated | yes | internal binding/trace |
| Workspace | uuid | RUNTIME_BINDING | no direct attribute dependency required | yes | internal binding/trace |
| auctioneer | agentSemanticId | DUPLICATED_RELATION | no direct attribute dependency required | yes | internal binding/trace |
| auctioneer | groupInstanceId | DUPLICATED_RELATION | no direct attribute dependency required | yes | internal binding/trace |
| auctioneer | semanticId | IDENTITY_TRACE | no direct attribute dependency required | yes | internal binding/trace |
| bricklayer | agentSemanticId | DUPLICATED_RELATION | no direct attribute dependency required | yes | internal binding/trace |
| bricklayer | groupInstanceId | DUPLICATED_RELATION | no direct attribute dependency required | yes | internal binding/trace |
| bricklayer | semanticId | IDENTITY_TRACE | no direct attribute dependency required | yes | internal binding/trace |
| door_fitter | agentSemanticId | DUPLICATED_RELATION | no direct attribute dependency required | yes | internal binding/trace |
| door_fitter | groupInstanceId | DUPLICATED_RELATION | no direct attribute dependency required | yes | internal binding/trace |
| door_fitter | semanticId | IDENTITY_TRACE | no direct attribute dependency required | yes | internal binding/trace |
| electrician | agentSemanticId | DUPLICATED_RELATION | no direct attribute dependency required | yes | internal binding/trace |
| electrician | groupInstanceId | DUPLICATED_RELATION | no direct attribute dependency required | yes | internal binding/trace |
| electrician | semanticId | IDENTITY_TRACE | no direct attribute dependency required | yes | internal binding/trace |
| house_owner | agentSemanticId | DUPLICATED_RELATION | no direct attribute dependency required | yes | internal binding/trace |
| house_owner | groupInstanceId | DUPLICATED_RELATION | no direct attribute dependency required | yes | internal binding/trace |
| house_owner | semanticId | IDENTITY_TRACE | no direct attribute dependency required | yes | internal binding/trace |
| painter | agentSemanticId | DUPLICATED_RELATION | no direct attribute dependency required | yes | internal binding/trace |
| painter | groupInstanceId | DUPLICATED_RELATION | no direct attribute dependency required | yes | internal binding/trace |
| painter | semanticId | IDENTITY_TRACE | no direct attribute dependency required | yes | internal binding/trace |
| participant | agentSemanticId | DUPLICATED_RELATION | no direct attribute dependency required | yes | internal binding/trace |
| participant | groupInstanceId | DUPLICATED_RELATION | no direct attribute dependency required | yes | internal binding/trace |
| participant | semanticId | IDENTITY_TRACE | no direct attribute dependency required | yes | internal binding/trace |
| plumber | agentSemanticId | DUPLICATED_RELATION | no direct attribute dependency required | yes | internal binding/trace |
| plumber | groupInstanceId | DUPLICATED_RELATION | no direct attribute dependency required | yes | internal binding/trace |
| plumber | semanticId | IDENTITY_TRACE | no direct attribute dependency required | yes | internal binding/trace |
| rc | agentSemanticId | DUPLICATED_RELATION | no direct attribute dependency required | yes | internal binding/trace |
| rc | groupInstanceId | DUPLICATED_RELATION | no direct attribute dependency required | yes | internal binding/trace |
| rc | semanticId | IDENTITY_TRACE | no direct attribute dependency required | yes | internal binding/trace |
| rl | agentSemanticId | DUPLICATED_RELATION | no direct attribute dependency required | yes | internal binding/trace |
| rl | groupInstanceId | DUPLICATED_RELATION | no direct attribute dependency required | yes | internal binding/trace |
| rl | semanticId | IDENTITY_TRACE | no direct attribute dependency required | yes | internal binding/trace |
| roofer | agentSemanticId | DUPLICATED_RELATION | no direct attribute dependency required | yes | internal binding/trace |
| roofer | groupInstanceId | DUPLICATED_RELATION | no direct attribute dependency required | yes | internal binding/trace |
| roofer | semanticId | IDENTITY_TRACE | no direct attribute dependency required | yes | internal binding/trace |
| rs | agentSemanticId | DUPLICATED_RELATION | no direct attribute dependency required | yes | internal binding/trace |
| rs | groupInstanceId | DUPLICATED_RELATION | no direct attribute dependency required | yes | internal binding/trace |
| rs | semanticId | IDENTITY_TRACE | no direct attribute dependency required | yes | internal binding/trace |
| rv | agentSemanticId | DUPLICATED_RELATION | no direct attribute dependency required | yes | internal binding/trace |
| rv | groupInstanceId | DUPLICATED_RELATION | no direct attribute dependency required | yes | internal binding/trace |
| rv | semanticId | IDENTITY_TRACE | no direct attribute dependency required | yes | internal binding/trace |
| site_prep_contractor | agentSemanticId | DUPLICATED_RELATION | no direct attribute dependency required | yes | internal binding/trace |
| site_prep_contractor | groupInstanceId | DUPLICATED_RELATION | no direct attribute dependency required | yes | internal binding/trace |
| site_prep_contractor | semanticId | IDENTITY_TRACE | no direct attribute dependency required | yes | internal binding/trace |
| window_fitter | agentSemanticId | DUPLICATED_RELATION | no direct attribute dependency required | yes | internal binding/trace |
| window_fitter | groupInstanceId | DUPLICATED_RELATION | no direct attribute dependency required | yes | internal binding/trace |
| window_fitter | semanticId | IDENTITY_TRACE | no direct attribute dependency required | yes | internal binding/trace |

Application subclass properties are VERIFICATION_STATE with application-observable
provenance (AuctionArtifact.running/best_bid/winner/task, GUIConsole.numMsg and
AuctionArt.currentBid/currentWinner/maxValue/task). Concrete Agent/OS/group/scheme subclasses
inherit the base surface without adding technical fields. Role MAssociationClasses
have no attributes after cleanup; native endpoints/context annotations/multiplicity
remain authoritative. Goal description is retained as authored domain text; no
runtime identity or provenance is concealed in this field.

## Complete exposed classifier inventory (inherited attributes included)

Before exports are completed, previously verified cuts at policy 2.1.0, preserved
under target. The AFTER column follows the actual 3.0.0 schema asserted by focused
tests; the final case exports/counts and gates are indexed in task.md section 11.
Counts may differ between runtime cuts; this migration changes attributes, not topology.

| Case | Class | BEFORE | AFTER |
| --- | --- | --- | --- |
| Auction | Agent | host, name, semanticId, sourceUri | name |
| Auction | AgentGoal | literal, semanticId, sourceLayer | literal |
| Auction | Artifact | artifactTypeSemanticId, creatorAgentSemanticId, name, semanticId, uuid, workspaceSemanticId | name |
| Auction | AuctionArtifact | artifactTypeSemanticId, best_bid, creatorAgentSemanticId, name, running, semanticId, task, uuid, winner, workspaceSemanticId | best_bid, name, running, task, winner |
| Auction | Belief | literal, semanticId, sourceLayer | literal |
| Auction | Group | name, semanticId | name |
| Auction | Mission | id, max, min, name, schemeInstanceIdentity, schemeSpecSemanticId, semanticId, sourceLayer, specSemanticId | id, max, min, name |
| Auction | Organization | name, semanticId | name |
| Auction | OrganizationalGoal | arguments, decompositionOperator, description, goalType, id, minAgentsToSatisfy, name, orderInParent, runtimeState, schemeInstanceIdentity, schemeSpecSemanticId, semanticId, sourceLayer, specSemanticId, stateEvidence, ttf | arguments, decompositionOperator, description, goalType, id, minAgentsToSatisfy, name, orderInParent, runtimeState, ttf |
| Auction | Scheme | arguments, goalStateEvidence, name, runtimeIdentity, semanticId, sourceLayer, specSemanticId | arguments, name |
| Auction | Workspace | environmentSemanticId, fullName, name, semanticId, uuid | name |
| Auction | auctionGroup | name, semanticId | name |
| Auction | auction_Organization | name, semanticId | name |
| Auction | auction_capabilities_Agent | host, name, semanticId, sourceUri | name |
| Auction | auctioneer | agentSemanticId, groupInstanceId, semanticId | (none) |
| Auction | doAuction_Scheme | arguments, goalStateEvidence, name, runtimeIdentity, semanticId, sourceLayer, specSemanticId | arguments, name |
| Auction | participant | agentSemanticId, groupInstanceId, semanticId | (none) |
| Hello World | Agent | host, name, semanticId, sourceUri | name |
| Hello World | AgentGoal | literal, semanticId, sourceLayer | literal |
| Hello World | Artifact | artifactTypeSemanticId, creatorAgentSemanticId, name, semanticId, uuid, workspaceSemanticId | name |
| Hello World | Belief | literal, semanticId, sourceLayer | literal |
| Hello World | GUIConsole | artifactTypeSemanticId, creatorAgentSemanticId, name, numMsg, semanticId, uuid, workspaceSemanticId | name, numMsg |
| Hello World | Group | name, semanticId | name |
| Hello World | Mission | id, max, min, name, schemeInstanceIdentity, schemeSpecSemanticId, semanticId, sourceLayer, specSemanticId | id, max, min, name |
| Hello World | Organization | name, semanticId | name |
| Hello World | OrganizationalGoal | arguments, decompositionOperator, description, goalType, id, minAgentsToSatisfy, name, orderInParent, runtimeState, schemeInstanceIdentity, schemeSpecSemanticId, semanticId, sourceLayer, specSemanticId, stateEvidence, ttf | arguments, decompositionOperator, description, goalType, id, minAgentsToSatisfy, name, orderInParent, runtimeState, ttf |
| Hello World | Scheme | arguments, goalStateEvidence, name, runtimeIdentity, semanticId, sourceLayer, specSemanticId | arguments, name |
| Hello World | Workspace | environmentSemanticId, fullName, name, semanticId, uuid | name |
| Hello World | hello_Agent | host, name, semanticId, sourceUri | name |
| Hello World | hello_sch_Scheme | arguments, goalStateEvidence, name, runtimeIdentity, semanticId, sourceLayer, specSemanticId | arguments, name |
| Hello World | hf_Agent | host, name, semanticId, sourceUri | name |
| Hello World | o1_Organization | name, semanticId | name |
| Hello World | rc | agentSemanticId, groupInstanceId, semanticId | (none) |
| Hello World | rl | agentSemanticId, groupInstanceId, semanticId | (none) |
| Hello World | rs | agentSemanticId, groupInstanceId, semanticId | (none) |
| Hello World | rv | agentSemanticId, groupInstanceId, semanticId | (none) |
| Hello World | team | name, semanticId | name |
| House Building | Agent | host, name, semanticId, sourceUri | name |
| House Building | AgentGoal | literal, semanticId, sourceLayer | literal |
| House Building | Artifact | artifactTypeSemanticId, creatorAgentSemanticId, name, semanticId, uuid, workspaceSemanticId | name |
| House Building | AuctionArt | artifactTypeSemanticId, creatorAgentSemanticId, currentBid, currentWinner, maxValue, name, semanticId, task, uuid, workspaceSemanticId | currentBid, currentWinner, maxValue, name, task |
| House Building | Belief | literal, semanticId, sourceLayer | literal |
| House Building | Group | name, semanticId | name |
| House Building | House | artifactTypeSemanticId, creatorAgentSemanticId, name, semanticId, uuid, workspaceSemanticId | name |
| House Building | Mission | id, max, min, name, schemeInstanceIdentity, schemeSpecSemanticId, semanticId, sourceLayer, specSemanticId | id, max, min, name |
| House Building | Organization | name, semanticId | name |
| House Building | OrganizationalGoal | arguments, decompositionOperator, description, goalType, id, minAgentsToSatisfy, name, orderInParent, runtimeState, schemeInstanceIdentity, schemeSpecSemanticId, semanticId, sourceLayer, specSemanticId, stateEvidence, ttf | arguments, decompositionOperator, description, goalType, id, minAgentsToSatisfy, name, orderInParent, runtimeState, ttf |
| House Building | Scheme | arguments, goalStateEvidence, name, runtimeIdentity, semanticId, sourceLayer, specSemanticId | arguments, name |
| House Building | Workspace | environmentSemanticId, fullName, name, semanticId, uuid | name |
| House Building | bricklayer | agentSemanticId, groupInstanceId, semanticId | (none) |
| House Building | build_house_sch_Scheme | arguments, goalStateEvidence, name, runtimeIdentity, semanticId, sourceLayer, specSemanticId | arguments, name |
| House Building | companyA_Agent | host, name, semanticId, sourceUri | name |
| House Building | companyB_Agent | host, name, semanticId, sourceUri | name |
| House Building | companyC_Agent | host, name, semanticId, sourceUri | name |
| House Building | companyD_Agent | host, name, semanticId, sourceUri | name |
| House Building | companyE_Agent | host, name, semanticId, sourceUri | name |
| House Building | door_fitter | agentSemanticId, groupInstanceId, semanticId | (none) |
| House Building | electrician | agentSemanticId, groupInstanceId, semanticId | (none) |
| House Building | giacomo_Agent | host, name, semanticId, sourceUri | name |
| House Building | house_contruction_Organization | name, semanticId | name |
| House Building | house_group | name, semanticId | name |
| House Building | house_owner | agentSemanticId, groupInstanceId, semanticId | (none) |
| House Building | painter | agentSemanticId, groupInstanceId, semanticId | (none) |
| House Building | plumber | agentSemanticId, groupInstanceId, semanticId | (none) |
| House Building | roofer | agentSemanticId, groupInstanceId, semanticId | (none) |
| House Building | site_prep_contractor | agentSemanticId, groupInstanceId, semanticId | (none) |
| House Building | window_fitter | agentSemanticId, groupInstanceId, semanticId | (none) |

## OCL impact

GoalDecompositionContext compares goalScheme navigation; MissionGoalContext compares
missionScheme/goalScheme navigation. GoalStateContext requires an owning Scheme
for defined runtimeState. GoalCommittedResponsibility uses runtimeState and the
existing exact capability gate, then compares Agent relations to Mission relations.
SchemeGoalContext was a redundant technical identity test and is removed (inverse
association already determines ownership). Specification and occurrence binding
consistency is validated in projection against exact internal specification/instance
keys; foreign functional INSERT_LINK is rejected transactionally. The semantic
contract validates source foreign keys before schema construction.

Acceptance OCL uses domain fields/navigation. Identity uniqueness is tested from
internal exact binding/snapshot identities instead of technical attribute invariants.
Auction's deliberately false CASE constraint uses authored goal id, owning Scheme
name and the artifact name carried by the root argument; exact evidence targets and
source-qualified trace remain internal. It does not create a production mapping
rule or modify JaCaMo domain state to make verification pass.

## Runtime, UI and regression evidence

AttributeProjectionCleanupTest checks exact base/role schemas, collision-safe domain
names, metadata snapshots, rollback, authoritative resync, removal, hashed replay
metadata and foreign functional context rejection. Existing focused suites check
Agent/literal updates, Scheme/Goal/Mission relations, Artifact state, same active
system/state, OCL typing, real operation PRE/POST, pause/violation and replay.
The native ObjectDiagramLifecycleEvidence renders attributes ON and verifies native
ObjectNode attribute values against the active state at each phase. Metadata remains
accessible in Trace/Diagnostics and Goal View instead of domain diagram attributes.

Final executable case-study gates, before/after numeric measurements, native PNG/
JSON/SOIL exports and full reactor results are recorded directly in task.md section 11.

## Final executable measurements

All counts below are from the final reactor's native schema/state and exact phase
checks. The prior verified cuts are the BEFORE baseline; runtime data depends on
cut timing. All native views retain the same active system/state per run.

| Case | Classes | Declared attributes BEFORE → AFTER | Inherited attributes BEFORE → AFTER | Objects / links | Beliefs | Phases / atomic graph checks |
| --- | --- | --- | --- | --- | --- | --- |
| Auction | 17 | 67 → 27 | 88 → 33 | 36 / 92 | 0 | 8 / 138 |
| Hello World AUTO | 20 | 70 → 24 | 95 → 31 | 47 / 118 | 6 | 12 / 647 |
| Hello World FULL | 20 | 70 → 24 | 95 → 31 | 47 / 118 | 6 | 12 / 656 |
| House Building | 30 | 88 → 27 | 135 → 39 | 284 / 499 | 216 | 7 / 546 |

Final reactor: 734 tests in 171 suites; no failures, errors or skips.
Exact logs/case paths and checklist evidence are recorded in task.md.
