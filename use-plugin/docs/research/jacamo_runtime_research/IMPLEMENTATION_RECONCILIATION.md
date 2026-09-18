# Runtime research reconciliation — Phase 16

Audited 2026-09-19 against baseline `c38d61f3`. This is project implementation evidence,
not a change to the frozen Ecore, Structural Mapping V1, or OCL semantics.

## Versions and provenance

The research pack is already installed (ten files including SOURCE_MANIFEST.md).
Its source/API claims are upstream facts at the revisions listed in that manifest;
event normalization and identity examples are project proposals; USE targets in
04_USE_MAPPING_CANDIDATES.md and runtime-mapping-candidate-v0.json are unapproved
candidate mappings. They are not canonical resources.

Resolved `mvn -B -pl use-plugin dependency:tree` agrees with pom.xml and
compatibility.json: Jason 3.3.0, CArtAgO 3.1, Moise 1.1, USE 7.5.0, Java 21.
The research JaCaMo 1.3.1 commit 3866858a7ebf6be85d9199c13a09cf4bfb8191be
uses Jason 3.3.2: VERSION_CONFLICT. Plugin target remains JaCaMo 1.3.0;
no dependency upgrade is made. JaCaMo launcher, JaCa and NPL are not direct
plugin dependencies. Research does not establish standalone launch evidence.

## Project-current capability matrix

All class names below are in the runtime package. Connector tests use real pinned
libraries. SUPPORTED means the stated scoped capability, not every upstream API.
All current events carry event ID, timestamp, sequence, dimension, runtime key,
optional semantic ID, payload and optional correlation. Composite adds source
connector/event IDs and prefixes correlation by connector.

| Family | API / implementation | Identity, payload, strategy | Reconciliation / status | USE effect / evidence |
|---|---|---|---|---|
| Jason beliefs | Agent.getBB; CircumstanceListener.eventAdded; JasonRuntimeConnector.fullSnapshot/installHooks | explicit agent binding; belief literal; snapshot and update triggers | IMPLEMENTED_MATCH / PARTIAL | trace-only; JasonRuntimeConnectorTest |
| Jason goals | GoalListener.goalStarted/goalFinished/goalFailed | agent plus goal trigger/state; callback | IMPLEMENTED_MATCH / PARTIAL | trace-only; JasonRuntimeConnectorTest |
| Goal suspended/waiting/resumed/executing | GoalListener | upstream lifecycle; no connector override | RESEARCH_ONLY / DEFERRED | none |
| Jason actions | JasonMonitorAgArch.act/actionExecuted | exact ActionExec identity correlation; action, args, error | IMPLEMENTED_MATCH / SUPPORTED | agent-side trace only; JasonRuntimeConnectorTest |
| Messages | explicit messageSent/messageReceived bridge | sender/receiver, performative/content; no mailbox listener | IMPLEMENTED_DIFFERENT / PARTIAL | trace only; outbound explicit bridge tested; automatic inbound unsupported |
| Intentions/cycles | CircumstanceListener / AgArch | no implemented capture | RESEARCH_ONLY / DEFERRED | no frozen dynamic target |
| CArtAgO properties | controller.getArtifactInfo; WorkspaceLogger.newPercept | workspace/artifact binding + property; values/type/attribute; snapshot and callbacks | IMPLEMENTED_MATCH / SUPPORTED | set projected attribute, removed -> undefined; CartagoRuntimeConnectorTest and LiveJaCaMoAuctionIntegrationTest |
| CArtAgO operations | WorkspaceLogger.opStarted/opCompleted/opFailed | bound artifact + OpId numeric ID; operation, arguments, agent, error | IMPLEMENTED_MATCH / SUPPORTED | enter/exit/fail correlation; same two tests |
| Requested/suspended/resumed operations | ICartagoLogger | available upstream, not normalized | RESEARCH_ONLY / DEFERRED | none |
| Artifact discovery/lifecycle | getCurrentArtifacts; artifactCreated/artifactDisposed | workspace/artifact name, type, creator/disposer | IMPLEMENTED_DIFFERENT / PARTIAL | lifecycle trace-only; unknown artifacts discoverable, callbacks currently omitted; CartagoRuntimeConnectorTest |
| Signals | newPercept signal Tuple | artifact, signal label/values | IMPLEMENTED_MATCH / SUPPORTED | trace-only; CartagoRuntimeConnectorTest |
| Focus/links/workspace membership | ICartagoLogger / getCurrentAgents | available upstream, absent connector overrides | RESEARCH_ONLY / UNSUPPORTED | no proven runtime relation policy |
| Moise groups/schemes | OE.getGroups/getSchemes; MoiseRuntimeConnector.capture | organisation + instance ID, separate specification ID, wellFormed; poll/diff | IMPLEMENTED_MATCH / PARTIAL | trace-only; MoiseRuntimeConnectorTest |
| Moise roles/missions | OE.getAgents; OEAgent.getRoles/getMissions | organisation, agent, group/role or scheme/mission; deterministic diff | IMPLEMENTED_MATCH / PARTIAL | trace-only; MoiseRuntimeConnectorTest |
| Moise goals | SchemeInstance.getGoals; GoalInstance.getState | organisation + scheme + goal; state; poll/diff | IMPLEMENTED_MATCH / PARTIAL | trace-only; MoiseRuntimeConnectorTest |
| OE agent existence | OE.getAgents | currently only role/mission events carry agents | IMPLEMENTED_DIFFERENT / PARTIAL | not a complete agent lifecycle |
| Responsible groups/plans | Moise instance APIs | upstream only | RESEARCH_ONLY / DEFERRED | no mutation |
| Norm permissions/obligations | OEAgent derived APIs | readable upstream, not captured by connector | RESEARCH_ONLY / DEFERRED | no deontic translation |
| NPL norm lifecycle | no proven public OE callback | unknown correlation | UNSUPPORTED / UNSUPPORTED | NORM_STATE_CHANGED enum is not a capability claim |
| Generic mutations | RuntimeMutationEngine.apply | synthetic explicit payloads plus trace | CODE_ONLY / PARTIAL | create/destroy/set/link/operation; RuntimeFoundationTest; target authorization needs Phase 19 audit |
| Composite stream | CompositeRuntimeConnector.fullSnapshot/forward | child provenance, aggregate sequence | CODE_ONLY / SUPPORTED | CompositeRuntimeConnectorTest; live Auction reconnect |

## Semantic authority and duplicate-source policy

Jason owns mind observations; CArtAgO owns environment property state and artifact
operation execution; Moise OE owns organisation observations. Organisation-board
transport observations must never be reinterpreted as a second role/mission
mutation. Jason ACTION_* is trace evidence and never invokes a second OP_ENTER.
Current Moise callbacks are trace-only. RuntimeEventValidator rejects an intrinsic
event kind assigned to another dimension with RUNTIME_AUTHORITY_CONFLICT;
RuntimeAuthorityTest is the negative control. Generic synthetic mutation kinds
remain compatible and require target-level authorization in Phase 19.

Polling only observes captured endpoints, not intermediate Moise transitions.
Composite snapshots are sequential observations, not an atomic three-runtime cut.
No global causal-order claim is made. Phase 19 must prove supported mirror state
at a quiescent checkpoint and explicitly handle concurrent-delta boundaries.

## Follow-on correctness findings

Phase 17: Jason attachAgent removes previous hooks from the replacement TS rather
than the old TS; mutable nested payloads are not deeply copied; CArtAgO operation
correlation lacks artifact incarnation evidence; unbound CArtAgO callbacks are
currently omitted. Phase 19: create accepts any existing semantic record before
trusting payload class/object, link participants trust USE names, terminal operation
correlation does not compare its source target, and trace-only events cannot prove
state equality. These are scoped findings to fix and test, not accepted guarantees.

## Evidence

Fresh focused command: `mvn -B -pl use-plugin
-Dtest=JasonRuntimeConnectorTest,CartagoRuntimeConnectorTest,MoiseRuntimeConnectorTest,CompositeRuntimeConnectorTest,LiveJaCaMoAuctionIntegrationTest test`:
5 tests PASS, zero skipped. RuntimeAuthorityTest first failed because no exception
was thrown (RED). Phase closure regression is recorded in the active task checklist.
Raw local logs: target/phase16-{dependencies.txt,focused.log,authority-red.log,regression.log}.
