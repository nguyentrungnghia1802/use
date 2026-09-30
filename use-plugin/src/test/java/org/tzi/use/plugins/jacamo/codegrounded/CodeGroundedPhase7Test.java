package org.tzi.use.plugins.jacamo.codegrounded;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.jacamo.bridge.contract.BridgeEntityId;
import org.jacamo.bridge.contract.BridgeRelationId;
import org.jacamo.bridge.contract.CapabilityStatus;
import org.jacamo.bridge.contract.Completeness;
import org.jacamo.bridge.contract.ModelSnapshot;
import org.jacamo.bridge.contract.ProjectionStatus;
import org.jacamo.bridge.contract.RuntimeEvent;
import org.jacamo.bridge.contract.RuntimeEventKind;
import org.jacamo.bridge.contract.RuntimeFact;
import org.jacamo.bridge.contract.RuntimeFactKind;
import org.jacamo.bridge.contract.RuntimeSnapshot;
import org.jacamo.bridge.contract.SourceWatermark;
import org.jacamo.bridge.contract.semantic.CartagoSemanticContract.ArtifactSemantic;
import org.jacamo.bridge.contract.semantic.CartagoSemanticContract.ArtifactTypeSemantic;
import org.jacamo.bridge.contract.semantic.CartagoSemanticContract.CartagoAgentIdentitySemantic;
import org.jacamo.bridge.contract.semantic.CartagoSemanticContract.EnvironmentSemantic;
import org.jacamo.bridge.contract.semantic.CartagoSemanticContract.FocusSemantic;
import org.jacamo.bridge.contract.semantic.CartagoSemanticContract.WorkspaceSemantic;
import org.jacamo.bridge.contract.semantic.EvidenceAuthority;
import org.jacamo.bridge.contract.semantic.Fidelity;
import org.jacamo.bridge.contract.semantic.JacamoSemanticSnapshot;
import org.jacamo.bridge.contract.semantic.SemanticMetadata;
import org.junit.jupiter.api.Test;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.NativeRuntimeMutationEngine;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.NativeRuntimeProjector;
import org.tzi.use.uml.ocl.value.StringValue;

/** Phase 7 proves native runtime synchronization without importing Runtime Mapping V2 state. */
class CodeGroundedPhase7Test {
    private static final String SESSION = "phase7-session";
    private static final long GENERATION = 7L;
    private static final String REVISION = "phase7-revision";

    @Test
    void faithfulJasonMutationAndRelationUseThePipelineSystem() throws Exception {
        var result = CodeGroundedTestFixtures.helloPipeline();
        var system = result.state().system();
        String agentSemanticId = result.source().snapshot().agentDeclarations().getFirst().metadata().semanticId();
        BridgeEntityId staticAgent = BridgeEntityId.parse(agentSemanticId);
        BridgeEntityId runtimeAgent = new BridgeEntityId("jason", "agent", "runtime-agent", "helloworld",
                "francois", "incarnation-1");
        BridgeRelationId binding = binding(runtimeAgent, staticAgent, "jason-agent-binding");
        var projector = new NativeRuntimeProjector(result, SESSION, GENERATION, REVISION);
        projector.applySnapshot(snapshot("phase7-initial", List.of(), Map.of("jason", 0L)));

        assertTrue(projector.apply(event("agent-host", 1, RuntimeEventKind.CHANGED, RuntimeFactKind.AGENT,
                runtimeAgent, binding, Map.of("normalizedEventKind", "SET_ATTRIBUTE", "attribute", "host",
                        "valueType", "STRING", "value", "runtime-host"))));
        var agent = result.state().semanticObjectIndex().get(agentSemanticId);
        assertSame(system, projector.system());
        assertEquals("runtime-host", ((StringValue) agent.state(system.state())
                .attributeValue(agent.cls().attribute("host", true))).value());
        assertTrue(projector.lastOclGate().passed());
        assertTrue(projector.trace().stream().anyMatch(trace -> trace.ruleId().equals("R-JASON-AGENT-ATTRIBUTE")
                && trace.outcome().equals(NativeRuntimeMutationEngine.Status.MATERIALIZED.name())));
        var alias = projector.runtimeAliases().get(runtimeAgent.canonical());
        assertNotNull(alias);
        assertEquals(agentSemanticId, alias.targetSemanticId());
        assertEquals(agent.name(), alias.targetUseId());
        assertEquals(SESSION, alias.sessionId());
        assertEquals(GENERATION, alias.generation());
        assertEquals(REVISION, alias.modelRevision());

    }

    @Test
    void cartagoArtifactMutationUsesExactBindingAndMoiseNplRemainEvidenceOnly() throws Exception {
        ModelSnapshot model = withEnvironment(CodeGroundedTestFixtures.helloSnapshot());
        var result = new CodeGroundedNativePipeline().build(model);
        String artifactSemanticId = model.semanticContract().cartagoEnvironments().getFirst().artifacts().getFirst()
                .metadata().semanticId();
        BridgeEntityId staticArtifact = BridgeEntityId.parse(artifactSemanticId);
        BridgeEntityId runtimeArtifact = new BridgeEntityId("cartago", "environment", "artifact", "phase7",
                "artifact", "incarnation-1");
        var projector = new NativeRuntimeProjector(result, SESSION, GENERATION, REVISION);
        var snapshot = snapshot("phase7-evidence", List.of(
                new RuntimeFact(new BridgeEntityId("moise", "organisation", "group-board", "phase7", "g", "inc"),
                        RuntimeFactKind.GROUP_BOARD, Map.of("state", "PLAYERS"), List.of(),
                        ProjectionStatus.EVIDENCE_ONLY, Completeness.COMPLETE, List.of()),
                new RuntimeFact(new BridgeEntityId("npl", "organisation", "norm-instance", "phase7", "n", "inc"),
                        RuntimeFactKind.NORM_INSTANCE, Map.of("state", "ACTIVE"), List.of(),
                        ProjectionStatus.EVIDENCE_ONLY, Completeness.COMPLETE, List.of())),
                Map.of("moise", 1L, "npl", 1L));
        NativeRuntimeProjector.ProjectionResult evidenceResult = projector.applySnapshot(snapshot);
        assertEquals(0, evidenceResult.materialized());
        assertEquals(2, evidenceResult.evidenceOnly().size());
        assertEquals(2, projector.evidence().size());
        assertTrue(projector.trace().stream().allMatch(trace -> trace.outcome()
                .equals(NativeRuntimeMutationEngine.Status.EVIDENCE_ONLY.name())));
        assertTrue(projector.lastOclGate().passed());

        assertTrue(projector.apply(event("artifact-name", 1, RuntimeEventKind.CHANGED, RuntimeFactKind.ARTIFACT,
                runtimeArtifact, binding(runtimeArtifact, staticArtifact, "cartago-artifact-binding"),
                Map.of("normalizedEventKind", "SET_ATTRIBUTE", "attribute", "name", "valueType", "STRING",
                        "value", "live-artifact"))));
        var artifact = result.state().semanticObjectIndex().get(artifactSemanticId);
        assertEquals("live-artifact", ((StringValue) artifact.state(result.state().system().state())
                .attributeValue(artifact.cls().attribute("name", true))).value());
        assertTrue(projector.trace().stream().anyMatch(trace -> trace.ruleId().equals("R-CARTAGO-ARTIFACT-ATTRIBUTE")));
        assertTrue(projector.apply(event("artifact-unset", 2, RuntimeEventKind.CHANGED, RuntimeFactKind.ARTIFACT,
                runtimeArtifact, binding(runtimeArtifact, staticArtifact, "cartago-artifact-binding"),
                Map.of("normalizedEventKind", "UNSET_ATTRIBUTE", "attribute", "name"))));
        assertTrue(artifact.state(result.state().system().state()).attributeValue(artifact.cls().attribute("name", true))
                .isUndefined());

        String agentSemanticId = model.semanticContract().agentDeclarations().getFirst().metadata().semanticId();
        String workspaceSemanticId = model.semanticContract().cartagoEnvironments().getFirst().workspaces().getFirst()
                .metadata().semanticId();
        BridgeEntityId staticAgent = BridgeEntityId.parse(agentSemanticId);
        BridgeEntityId runtimeRelation = new BridgeEntityId("cartago", "environment", "relation", "phase7",
                "agent-workspace", "incarnation-1");
        assertTrue(projector.apply(event("agent-workspace", 3, RuntimeEventKind.ADDED, RuntimeFactKind.RELATION_STATE,
                runtimeRelation, binding(runtimeRelation, staticAgent, "cartago-relation-binding"),
                Map.of("normalizedEventKind", "INSERT_LINK", "association", "X05AgentWorkspace",
                        "participantSemanticIds", List.of(agentSemanticId, workspaceSemanticId)))));
        assertEquals(1, result.state().system().state()
                .linksOfAssociation(result.state().system().model().getAssociation("X05AgentWorkspace")).size());
        assertTrue(projector.apply(event("agent-workspace-delete", 4, RuntimeEventKind.REMOVED,
                RuntimeFactKind.RELATION_STATE, runtimeRelation,
                binding(runtimeRelation, staticAgent, "cartago-relation-binding"),
                Map.of("normalizedEventKind", "DELETE_LINK", "association", "X05AgentWorkspace",
                        "participantSemanticIds", List.of(agentSemanticId, workspaceSemanticId)))));
        assertEquals(0, result.state().system().state()
                .linksOfAssociation(result.state().system().model().getAssociation("X05AgentWorkspace")).size());
    }

    @Test
    void exactCartagoSnapshotMaterializesRuntimeObjectsInThePipelineSystem() throws Exception {
        var result = CodeGroundedTestFixtures.helloPipeline();
        var system = result.state().system();
        int baselineObjects = system.state().numObjects();
        String environment = "cartago:environment:runtime-env";
        String workspace = "cartago:workspace:runtime-env:/main:workspace-1";
        String agent = "cartago:agent:runtime-env:agent-global:1";
        String type = "cartago:artifact-type:runtime-env:example.RuntimeArtifact";
        String artifact = "cartago:artifact:runtime-env:/main:artifact-1";
        String property = "cartago:property:" + artifact + ":status-1";
        List<RuntimeFact> facts = List.of(
                faithfulFact("workspace", RuntimeFactKind.WORKSPACE, Map.ofEntries(
                        Map.entry("normalizedEventKind", "UPSERT_CARTAGO_WORKSPACE"),
                        Map.entry("semanticId", workspace), Map.entry("fullName", "/main"),
                        Map.entry("name", "main"), Map.entry("uuid", "workspace-1"),
                        Map.entry("parentSemanticId", ""), Map.entry("environmentSemanticId", environment),
                        Map.entry("local", true), Map.entry("protocol", ""), Map.entry("remotePath", ""),
                        Map.entry("address", ""), Map.entry("environmentName", "runtime"),
                        Map.entry("environmentId", "runtime-env"), Map.entry("environmentVersion", "1"),
                        Map.entry("defaultInfrastructureLayer", "local"))),
                faithfulFact("agent", RuntimeFactKind.AGENT, Map.ofEntries(
                        Map.entry("normalizedEventKind", "UPSERT_CARTAGO_AGENT_IDENTITY"),
                        Map.entry("semanticId", agent), Map.entry("globalId", "agent-global"),
                        Map.entry("localId", 1), Map.entry("name", "worker"), Map.entry("role", "worker"),
                        Map.entry("workspaceSemanticId", workspace))),
                faithfulFact("artifact", RuntimeFactKind.ARTIFACT, Map.ofEntries(
                        Map.entry("normalizedEventKind", "UPSERT_CARTAGO_ARTIFACT"),
                        Map.entry("semanticId", artifact), Map.entry("name", "runtimeArtifact"),
                        Map.entry("uuid", "artifact-1"), Map.entry("artifactTypeSemanticId", type),
                        Map.entry("artifactTypeJavaClassName", "example.RuntimeArtifact"),
                        Map.entry("artifactTypeClassLoaderIdentity", "runtime-loader"),
                        Map.entry("workspaceSemanticId", workspace), Map.entry("creatorAgentSemanticId", agent))),
                faithfulFact("property", RuntimeFactKind.PROPERTY, Map.ofEntries(
                        Map.entry("normalizedEventKind", "UPSERT_CARTAGO_PROPERTY_SNAPSHOT"),
                        Map.entry("semanticId", property), Map.entry("artifactSemanticId", artifact),
                        Map.entry("propertyId", "status-1"), Map.entry("name", "status"),
                        Map.entry("values", List.of("ready")), Map.entry("valueTypes", List.of("java.lang.String")),
                        Map.entry("annotations", List.of()))));
        var projector = new NativeRuntimeProjector(result, SESSION, GENERATION, REVISION);

        NativeRuntimeProjector.ProjectionResult projection = projector.applySnapshot(
                snapshot("cartago-live", facts, Map.of("cartago", 1L)));

        assertSame(system, projector.system());
        assertEquals(4, projection.materialized());
        assertEquals(baselineObjects + 6, system.state().numObjects());
        assertEquals(1, system.state().linksOfAssociation(system.model()
                .getAssociation("C13EnvironmentWorkspace")).size());
        assertEquals(1, system.state().linksOfAssociation(system.model()
                .getAssociation("C14WorkspaceArtifact")).size());
        assertEquals(1, system.state().linksOfAssociation(system.model()
                .getAssociation("C15ArtifactType")).size());
        assertEquals(1, system.state().linksOfAssociation(system.model()
                .getAssociation("C17ArtifactObservableProperty")).size());
        assertEquals(1, system.state().linksOfAssociation(system.model()
                .getAssociation("C19WorkspaceAgent")).size());
        assertEquals(4, projector.runtimeAliases().size());
        assertTrue(projector.lastOclGate().passed());

        projector.applySnapshot(snapshot("cartago-empty", List.of(), Map.of("cartago", 2L)));
        assertEquals(baselineObjects, system.state().numObjects(), "authoritative resync removes stale runtime objects");
        assertSame(system, projector.system());
    }

    @Test
    void staleSessionAndReplayEventsAreRejectedAndResyncRestoresTheSameSystemDeterministically() throws Exception {
        var result = CodeGroundedTestFixtures.helloPipeline();
        var projector = new NativeRuntimeProjector(result, SESSION, GENERATION, REVISION);
        String agentSemanticId = result.source().snapshot().agentDeclarations().getFirst().metadata().semanticId();
        BridgeEntityId staticAgent = BridgeEntityId.parse(agentSemanticId);
        BridgeEntityId runtimeAgent = new BridgeEntityId("jason", "agent", "runtime-agent", "helloworld",
                "francois", "incarnation-1");
        BridgeRelationId binding = binding(runtimeAgent, staticAgent, "jason-agent-binding");
        RuntimeEvent first = event("first", 1, RuntimeEventKind.CHANGED, RuntimeFactKind.AGENT,
                runtimeAgent, binding, Map.of("normalizedEventKind", "SET_ATTRIBUTE", "attribute", "host",
                        "valueType", "STRING", "value", "first-host"));
        projector.apply(first);
        String beforeReplay = digest(result.state().system());
        RuntimeEvent undeclaredRule = event("undeclared-rule", 2, RuntimeEventKind.CHANGED, RuntimeFactKind.AGENT,
                runtimeAgent, binding, Map.of("normalizedEventKind", "INVENT_UNDECLARED_TYPE"));
        assertThrows(RuntimeException.class, () -> projector.apply(undeclaredRule));
        assertEquals(beforeReplay, digest(result.state().system()));
        assertThrows(RuntimeException.class, () -> projector.apply(first));
        assertTrue(projector.resyncRequired());
        assertEquals(beforeReplay, digest(result.state().system()));

        RuntimeEvent wrongSession = new RuntimeEvent("wrong-session", "other-session", GENERATION, REVISION,
                "jason", "jason", 2, Instant.EPOCH, RuntimeEventKind.CHANGED, RuntimeFactKind.AGENT,
                ProjectionStatus.MATERIALIZED_FAITHFULLY, runtimeAgent, binding, "", "", Map.of(),
                Map.of("normalizedEventKind", "SET_ATTRIBUTE", "attribute", "host", "valueType", "STRING",
                        "value", "wrong"), new SourceWatermark("jason", 2), Completeness.COMPLETE, List.of());
        assertThrows(RuntimeException.class, () -> projector.apply(wrongSession));
        assertEquals(beforeReplay, digest(result.state().system()));

        RuntimeSnapshot resync = snapshot("phase7-resync", List.of(new RuntimeFact(runtimeAgent, RuntimeFactKind.AGENT,
                Map.of("normalizedEventKind", "SET_ATTRIBUTE", "attribute", "host", "valueType", "STRING",
                        "value", "resync-host"), List.of(binding), ProjectionStatus.MATERIALIZED_FAITHFULLY,
                Completeness.COMPLETE, List.of())), Map.of("jason", 1L));
        MSystemIdentity identity = new MSystemIdentity(result.state().system());
        projector.applySnapshot(resync);
        String resyncedDigest = digest(result.state().system());
        assertFalse(projector.resyncRequired());
        assertSame(identity.system(), projector.system());
        projector.applySnapshot(resync);
        assertEquals(resyncedDigest, digest(result.state().system()));
        assertTrue(projector.lastOclGate().passed());
    }

    @Test
    void jasonA12ToA15RemainTypedRuntimeEvidenceWithoutStaticModelPollution() throws Exception {
        var result = CodeGroundedTestFixtures.helloPipeline();
        String before = digest(result.state().system());
        String projectKey = "phase7-jason";
        List<RuntimeFact> facts = List.of(
                runtimeEvidence(projectKey, "action", RuntimeFactKind.ACTION_EXECUTION,
                        Map.of("normalizedEventKind", "ACTION_EXECUTION_FAILED", "runtimeConcept", "ActionExec",
                                "sourceLayer", "RUNTIME", "action", "fail_action", "result", false,
                                "failureReason", "missing-resource")),
                runtimeEvidence(projectKey, "intention", RuntimeFactKind.INTENTION,
                        Map.of("normalizedEventKind", "INTENTION", "runtimeConcept", "Intention",
                                "sourceLayer", "RUNTIME", "intentionId", 7)),
                runtimeEvidence(projectKey, "event", RuntimeFactKind.RUNTIME_EVENT,
                        Map.of("normalizedEventKind", "RUNTIME_EVENT", "runtimeConcept", "Event",
                                "sourceLayer", "RUNTIME", "trigger", "+!goal")),
                runtimeEvidence(projectKey, "transition", RuntimeFactKind.TRANSITION_SYSTEM,
                        Map.of("normalizedEventKind", "TRANSITION_SYSTEM", "runtimeConcept", "TransitionSystem",
                                "sourceLayer", "RUNTIME", "runningIntentions", 1)));
        var projector = new NativeRuntimeProjector(result, SESSION, GENERATION, REVISION);

        NativeRuntimeProjector.ProjectionResult projection = projector.applySnapshot(
                snapshot("phase7-jason-evidence", facts, Map.of("jason", 4L)));

        assertEquals(0, projection.materialized());
        assertEquals(4, projection.evidenceOnly().size());
        assertEquals(4, projector.evidence().size());
        assertTrue(projector.runtimeAliases().isEmpty());
        assertEquals(before, digest(result.state().system()));
        assertTrue(projector.trace().stream().allMatch(value ->
                value.outcome().equals(NativeRuntimeMutationEngine.Status.EVIDENCE_ONLY.name())));
        assertTrue(projector.trace().stream().map(value -> value.ruleId()).toList().containsAll(List.of(
                "R-JASON-A12-ACTION-EXECUTION-EVIDENCE",
                "R-JASON-A13-INTENTION-EVIDENCE",
                "R-JASON-A14-RUNTIME-EVENT-EVIDENCE",
                "R-JASON-A15-TRANSITION-SYSTEM-EVIDENCE")));
        assertTrue(result.state().system().model().classes().stream()
                .noneMatch(value -> value.name().equals("Intention") || value.name().equals("RuntimeEvent")
                        || value.name().equals("TransitionSystem")));
    }

    private static RuntimeFact runtimeEvidence(String projectKey, String local,
                                                RuntimeFactKind kind, Map<String, Object> values) {
        return new RuntimeFact(new BridgeEntityId("jason", "agent", kind.name().toLowerCase(),
                        projectKey, local, "incarnation-1"), kind, values, List.of(),
                ProjectionStatus.EVIDENCE_ONLY, Completeness.COMPLETE, List.of());
    }

    private static RuntimeFact faithfulFact(String local, RuntimeFactKind kind, Map<String, Object> values) {
        return new RuntimeFact(new BridgeEntityId("cartago", "environment", kind.name().toLowerCase(),
                "phase7-runtime", local, "snapshot"), kind, values, List.of(),
                ProjectionStatus.MATERIALIZED_FAITHFULLY, Completeness.COMPLETE, List.of());
    }

    private static RuntimeEvent event(String id, long sequence, RuntimeEventKind kind, RuntimeFactKind factKind,
                                      BridgeEntityId runtime, BridgeRelationId binding, Map<String, Object> after) {
        return new RuntimeEvent(id, SESSION, GENERATION, REVISION, "native-test", "jason", sequence,
                Instant.EPOCH, kind, factKind, ProjectionStatus.MATERIALIZED_FAITHFULLY, runtime, binding, "", "",
                Map.of(), after, new SourceWatermark("jason", sequence), Completeness.COMPLETE, List.of());
    }

    private static BridgeRelationId binding(BridgeEntityId runtime, BridgeEntityId target, String evidence) {
        return new BridgeRelationId("runtime-model-binding", List.of(runtime, target), evidence, "incarnation-1");
    }

    private static RuntimeSnapshot snapshot(String id, List<RuntimeFact> facts, Map<String, Long> watermarks) {
        Map<String, SourceWatermark> values = watermarks.entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey,
                        entry -> new SourceWatermark(entry.getKey(), entry.getValue())));
        Map<String, Completeness> completeness = watermarks.keySet().stream()
                .collect(java.util.stream.Collectors.toMap(value -> value, value -> Completeness.COMPLETE));
        return new RuntimeSnapshot(id, REVISION, Instant.EPOCH, Instant.EPOCH, values, values, 1, facts,
                completeness, "phase7-fingerprint-" + id);
    }

    private static String digest(org.tzi.use.uml.sys.MSystem system) {
        return system.state().allObjects().stream().map(object -> object.name() + object.state(system.state())
                .attributeValueMap().toString()).sorted().toList().toString()
                + system.state().allLinks().stream().map(Object::toString).sorted().toList();
    }

    private static ModelSnapshot withEnvironment(ModelSnapshot base) {
        BridgeEntityId environmentId = new BridgeEntityId("cartago", "environment", "environment", "phase7",
                "environment", "model");
        BridgeEntityId workspaceId = new BridgeEntityId("cartago", "environment", "workspace", "phase7",
                "workspace", "model");
        BridgeEntityId typeId = new BridgeEntityId("cartago", "environment", "artifact-type", "phase7",
                "type", "model");
        BridgeEntityId artifactId = new BridgeEntityId("cartago", "environment", "artifact", "phase7",
                "artifact", "model");
        BridgeEntityId cartagoAgentId = new BridgeEntityId("cartago", "environment", "agent", "phase7",
                "agent", "model");
        EnvironmentSemantic environment = new EnvironmentSemantic(meta(environmentId, "CARTAGO_ENVIRONMENT"),
                "phase7", "environment-1", "1.0", "default",
                List.of(new WorkspaceSemantic(meta(workspaceId, "CARTAGO_WORKSPACE"), "/phase7", "phase7",
                        "workspace-1", "", environmentId.canonical(), true, "local", "", "")),
                List.of(new ArtifactTypeSemantic(meta(typeId, "CARTAGO_ARTIFACT_TYPE"), "phase7.Type", "loader")),
                List.of(new ArtifactSemantic(meta(artifactId, "CARTAGO_ARTIFACT"), "phase7-artifact", "artifact-1",
                        typeId.canonical(), workspaceId.canonical(), "")),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(new CartagoAgentIdentitySemantic(meta(cartagoAgentId, "CARTAGO_AGENT_IDENTITY"),
                        "phase7-global-agent", 1, "phase7-agent", "worker", workspaceId.canonical())),
                List.of(new FocusSemantic(meta("beid:v1:cartago/environment/focus/phase7/focus/model", "CARTAGO_FOCUS"),
                        cartagoAgentId.canonical(), artifactId.canonical(), true, 0)));
        var contract = base.semanticContract();
        var semantic = new JacamoSemanticSnapshot(contract.contractVersion(), contract.project(),
                contract.agentDeclarations(), contract.workspaceDeclarations(), contract.artifactDeclarations(),
                contract.organizationDeployments(), contract.groupDeployments(), contract.schemeDeployments(),
                contract.institutionDeployments(), contract.rawRoleTuples(), contract.rawFocusTuples(),
                contract.importProvenance(), contract.jasonPrograms(), List.of(environment), contract.moiseOrganizations(),
                contract.exactBindings(), contract.diagnostics());
        return new ModelSnapshot("phase7-cartago", base.sources(), base.agentDeclarations(), base.workspaces(),
                base.configuredArtifacts(), base.organisationFacts(), base.groupRoleCardinalities(),
                base.parentSubGroupCardinalities(), base.crossDimensionalRelations(), base.unresolvedFacts(),
                base.projectionProvenance(), semantic);
    }

    private static SemanticMetadata meta(BridgeEntityId id, String kind) {
        return meta(id.canonical(), kind);
    }

    private static SemanticMetadata meta(String id, String kind) {
        return new SemanticMetadata(id, kind, CodeGroundedPhase7Test.class.getName(),
                EvidenceAuthority.OFFICIAL_CARTAGO_API, Fidelity.EXACT, CapabilityStatus.COMPLETE, List.of(), List.of());
    }

    private record MSystemIdentity(org.tzi.use.uml.sys.MSystem system) { }
}
