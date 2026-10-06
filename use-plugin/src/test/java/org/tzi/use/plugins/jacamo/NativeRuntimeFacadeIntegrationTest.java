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
    @Test void disconnectKeepsSameSystemProfileAndHistoryButMarksStaleUntilResync(@org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception {
        var jcm=java.nio.file.Path.of("src/test/resources/canonical-cases/hello-world/helloworld.jcm").toAbsolutePath();
        Session session=new Session();
        try(var facade=BridgeFacadeTestSupport.nativeFacade(jcm,session,()->false)) {
            facade.importProject(jcm); var system=session.system();
            var path=directory.resolve("user.ocl");java.nio.file.Files.writeString(path,"context Agent inv Demo: true"); facade.loadVerificationProfile(path);
            var profile=facade.verificationSnapshot().profile(); int entries=facade.runtimeVerificationHistory().size();
            facade.disconnectRuntime(); assertSame(system,session.system()); assertSame(system,facade.materializedSystem());
            assertEquals(profile,facade.verificationSnapshot().profile());assertTrue(facade.runtimeVerificationHistory().size()>entries);
            assertEquals("STALE",facade.runtimeVerificationResult().freshness());
            facade.resyncRuntime(); assertSame(system,session.system()); assertEquals(profile,facade.verificationSnapshot().profile());
            assertEquals("CURRENT_OBSERVED",facade.runtimeVerificationResult().freshness());
        }
    }
    @Test void failedProductionResyncCannotLeaveAPassingCachedResult(@org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception {
        var jcm = java.nio.file.Path.of("src/test/resources/canonical-cases/hello-world/helloworld.jcm").toAbsolutePath();
        var unavailable = new java.util.concurrent.atomic.AtomicBoolean(); Session session = new Session();
        try (var facade = BridgeFacadeTestSupport.nativeFacade(jcm, session, unavailable::get)) {
            facade.importProject(jcm); var system = session.system();
            var profile = directory.resolve("defined.ocl");
            java.nio.file.Files.writeString(profile, "context Agent inv Defined: not self.name.oclIsUndefined()");
            facade.loadVerificationProfile(profile); facade.resyncRuntime();
            assertTrue(facade.runtimeVerificationResult().count(org.tzi.use.plugins.jacamo.verification.VerificationOutcome.PASS) > 0);
            unavailable.set(true); assertThrows(RuntimeException.class, facade::resyncRuntime);
            assertSame(system, session.system());
            assertEquals("STALE", facade.runtimeVerificationResult().freshness());
            assertEquals(0, facade.runtimeVerificationResult().count(org.tzi.use.plugins.jacamo.verification.VerificationOutcome.PASS));
            unavailable.set(false); facade.resyncRuntime();
            assertSame(system, session.system()); assertEquals("CURRENT_OBSERVED", facade.runtimeVerificationResult().freshness());
        }
    }
    @Test void uiReadsCannotDeadlockBehindTheWriterMonitorBeforeItsEdtBarrier(@org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception {
        var jcm = java.nio.file.Path.of("src/test/resources/canonical-cases/hello-world/helloworld.jcm").toAbsolutePath();
        Session session = new Session();
        try (var facade = BridgeFacadeTestSupport.nativeFacade(jcm, session, () -> false);
             var workers = java.util.concurrent.Executors.newSingleThreadExecutor()) {
            facade.importProject(jcm); var system = session.system();
            var writerHasMonitor = new java.util.concurrent.CountDownLatch(1);
            var uiFinished = new java.util.concurrent.CountDownLatch(1);
            var writer = workers.submit(() -> {
                synchronized (facade) {
                    writerHasMonitor.countDown();
                    assertTrue(uiFinished.await(5, java.util.concurrent.TimeUnit.SECONDS), "UI reads waited for the facade writer monitor");
                    facade.runFullVerification(); facade.exportNativeSoil(directory.resolve("consistent.cmd"));
                }
                return null;
            });
            assertTrue(writerHasMonitor.await(5, java.util.concurrent.TimeUnit.SECONDS));
            var ui = new java.util.concurrent.FutureTask<Void>(() -> {
                facade.status(); facade.runtimeStatus(); facade.latestVerification(); facade.constraints();
                facade.formalStateStatus(); facade.performanceMetrics(); uiFinished.countDown(); return null;
            });
            javax.swing.SwingUtilities.invokeLater(ui);
            ui.get(10, java.util.concurrent.TimeUnit.SECONDS); writer.get(10, java.util.concurrent.TimeUnit.SECONDS);
            assertSame(system, session.system()); assertSame(system, facade.materializedSystem());
            assertTrue(java.nio.file.Files.size(directory.resolve("consistent.cmd")) > 0);
        }
    }
    @Test void externalProfileAndStateVersionSurviveProductionResyncOnTheSameSession(@org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception {
        var jcm = java.nio.file.Path.of("src/test/resources/canonical-cases/hello-world/helloworld.jcm").toAbsolutePath();
        Session session = new Session();
        try (var facade = BridgeFacadeTestSupport.nativeFacade(jcm, session, () -> false)) {
            facade.importProject(jcm); var system = session.system();
            var profile = directory.resolve("constraints.ocl"); java.nio.file.Files.writeString(profile, "context Agent inv ObservedViolation: false");
            facade.loadVerificationProfile(profile);
            assertTrue(facade.sourceExcerpt(profile,0).contains("line unavailable"));
            assertTrue(facade.sourceExcerpt(profile,0).contains("ObservedViolation"));
            assertTrue(facade.constraints().stream().filter(c->c.id().equals("EXTERNAL:Agent::ObservedViolation")).allMatch(c->c.sourceSpan()==null));
            long version = facade.runtimeVerificationResult().stateVersion();
            String hash = facade.runtimeVerificationResult().constraintSetHash();
            assertTrue(facade.latestVerification().results().stream().anyMatch(result -> result.constraintId().equals("EXTERNAL:Agent::ObservedViolation")
                    && result.outcome() == org.tzi.use.plugins.jacamo.verification.VerificationOutcome.FAIL));
            facade.resyncRuntime();
            assertSame(system, session.system()); assertSame(system, facade.materializedSystem());
            assertTrue(facade.runtimeVerificationResult().stateVersion() > version);
            assertEquals(hash, facade.runtimeVerificationResult().constraintSetHash());
            assertTrue(facade.constraints().stream().anyMatch(constraint -> constraint.id().equals("EXTERNAL:Agent::ObservedViolation")));
            java.nio.file.Files.writeString(profile, "context Missing inv Broken: true");
            assertThrows(IllegalArgumentException.class, () -> facade.loadVerificationProfile(profile));
            assertEquals(hash, facade.runtimeVerificationResult().constraintSetHash());
            String excerpt=facade.sourceExcerpt(profile,0);
            assertTrue(excerpt.contains("ObservedViolation"));
            assertFalse(excerpt.contains("inv Broken"),"Source navigation must retain the bytes of the accepted interval");
            assertTrue(excerpt.contains("Accepted OCL source SHA-256"));
            facade.resyncRuntime(); // Rebind the accepted source bytes, not the now-invalid on-disk file.
            assertSame(system, session.system()); assertEquals(hash, facade.runtimeVerificationResult().constraintSetHash());
            java.nio.file.Files.delete(profile);
            assertTrue(facade.sourceExcerpt(profile,0).contains("ObservedViolation"));
        }
    }
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
                Map.of("normalizedEventKind", "SET_ATTRIBUTE", "attribute", "name", "valueType", "STRING",
                        "value", "facade-runtime-name"), new SourceWatermark("jason", 1),
                Completeness.COMPLETE, List.of());
        Session session = new Session();
        try (var facade = BridgeFacadeTestSupport.nativeFacadeWithEvents(jcm, session, () -> false, List.of(event))) {
            facade.importProject(jcm);
            var system = session.system();
            var agent = system.state().objectByName(system.state().allObjects().stream()
                    .filter(object -> "agent-program".equals(object.cls().getAnnotationValue("DomainProjection", "kind"))
                            && facade.verificationSnapshot().image().objects().get(object.name()).semanticId()
                            .equals(agentSemanticId)).findFirst().orElseThrow().name());
            assertEquals("facade-runtime-name", ((StringValue) agent.state(system.state())
                    .attributeValue(agent.cls().attribute("name", true))).value());
            assertSame(system, facade.materializedSystem());
            assertSame(system, session.system());
            assertEquals(1, facade.runtimeStatus().processed());
            assertEquals(MirrorState.LIVE, facade.runtimeStatus().state());
            assertTrue(facade.latestVerification().results().stream()
                    .allMatch(result -> result.outcome() != org.tzi.use.plugins.jacamo.verification.VerificationOutcome.FAIL));
        }
    }
}
