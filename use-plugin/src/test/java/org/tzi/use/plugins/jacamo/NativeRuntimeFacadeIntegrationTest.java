package org.tzi.use.plugins.jacamo;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.jacamo.bridge.adapter.OfficialProjectAdapter;
import org.jacamo.bridge.adapter.OfficialProjectLoader;
import org.jacamo.bridge.contract.BridgeEntityId;
import org.jacamo.bridge.contract.BridgeRelationId;
import org.jacamo.bridge.contract.Completeness;
import org.jacamo.bridge.contract.ModelSnapshot;
import org.jacamo.bridge.contract.ProjectionStatus;
import org.jacamo.bridge.contract.RuntimeEvent;
import org.jacamo.bridge.contract.RuntimeEventKind;
import org.jacamo.bridge.contract.RuntimeFactKind;
import org.jacamo.bridge.contract.SourceWatermark;
import org.junit.jupiter.api.Test;
import org.tzi.use.main.Session;
import org.tzi.use.plugins.jacamo.runtime.MirrorState;
import org.tzi.use.uml.ocl.value.StringValue;

/** Proves that the production native facade consumes a buffered runtime event in the session system. */
class NativeRuntimeFacadeIntegrationTest {
    @Test
    void bufferedFaithfulEventMutatesTheActivatedSessionSystem() throws Exception {
        java.nio.file.Path jcm = java.nio.file.Path.of(
                "src/test/resources/canonical-cases/hello-world/helloworld.jcm").toAbsolutePath().normalize();
        ModelSnapshot model = new OfficialProjectAdapter().adapt(new OfficialProjectLoader().load(jcm), jcm);
        String agentSemanticId = model.semanticContract().agentDeclarations().getFirst().metadata().semanticId();
        BridgeEntityId staticAgent = BridgeEntityId.parse(agentSemanticId);
        BridgeEntityId runtimeAgent = new BridgeEntityId("jason", "agent", "runtime-agent", "helloworld",
                "francois", "incarnation-1");
        BridgeRelationId binding = new BridgeRelationId("runtime-model-binding", List.of(runtimeAgent, staticAgent),
                "phase7-facade-binding", "incarnation-1");
        RuntimeEvent event = new RuntimeEvent("phase7-facade-event", "test-session", 1, model.modelRevision(),
                "jason", "jason", 1, Instant.EPOCH, RuntimeEventKind.ADDED, RuntimeFactKind.AGENT,
                ProjectionStatus.MATERIALIZED_FAITHFULLY, runtimeAgent, binding, "", "", Map.of(),
                Map.of("normalizedEventKind", "SET_ATTRIBUTE", "attribute", "host", "valueType", "STRING",
                        "value", "facade-runtime-host"), new SourceWatermark("jason", 1),
                Completeness.COMPLETE, List.of());
        Session session = new Session();
        try (var facade = BridgeFacadeTestSupport.nativeFacadeWithEvents(jcm, session, () -> false, List.of(event))) {
            facade.importProject(jcm);
            var system = session.system();
            var agent = system.state().objectByName(system.state().allObjects().stream()
                    .filter(object -> object.cls().name().equals("Agent")
                            && object.state(system.state()).attributeValue("semanticId").toString()
                            .equals("'" + agentSemanticId + "'")).findFirst().orElseThrow().name());
            assertEquals("facade-runtime-host", ((StringValue) agent.state(system.state())
                    .attributeValue(agent.cls().attribute("host", true))).value());
            assertSame(system, facade.materializedSystem());
            assertSame(system, session.system());
            assertEquals(1, facade.runtimeStatus().processed());
            assertEquals(MirrorState.LIVE, facade.runtimeStatus().state());
            assertTrue(facade.latestVerification().results().stream()
                    .allMatch(result -> result.outcome() != org.tzi.use.plugins.jacamo.verification.VerificationOutcome.FAIL));
        }
    }
}
