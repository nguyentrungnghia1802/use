package org.tzi.use.plugins.jacamo;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jacamo.bridge.adapter.OfficialProjectAdapter;
import org.jacamo.bridge.adapter.OfficialProjectLoader;
import org.jacamo.bridge.contract.Capability;
import org.jacamo.bridge.contract.CapabilityStatus;
import org.jacamo.bridge.contract.Completeness;
import org.jacamo.bridge.contract.ContractCodec;
import org.jacamo.bridge.contract.ContractEnvelope;
import org.jacamo.bridge.contract.ContractPayloads;
import org.jacamo.bridge.contract.DistributionFingerprint;
import org.jacamo.bridge.contract.MessageType;
import org.jacamo.bridge.contract.ModelSnapshot;
import org.jacamo.bridge.contract.RuntimeSnapshot;
import org.jacamo.bridge.contract.SourceWatermark;
import org.junit.jupiter.api.Test;
import org.tzi.use.plugins.jacamo.bridge.BridgeConnectionConfig;
import org.tzi.use.plugins.jacamo.bridge.BridgeProtocolException;
import org.tzi.use.plugins.jacamo.bridge.RecordedBridgeTransport;

class DefaultBridgeAuthorityTest {
    private static final String DISTRIBUTION = "1".repeat(64);
    private static final List<Capability> CAPABILITIES = List.of(
            new Capability("official.model", CapabilityStatus.COMPLETE, List.of(), ""),
            new Capability("runtime.snapshot", CapabilityStatus.COMPLETE, List.of(), ""));

    @Test void defaultAuthoritySelectionIsBridgeAndRetiredLegacyFlagFailsClearly() {
        assertEquals(SemanticAuthority.BRIDGE, SemanticAuthority.configured(null));
        assertEquals(SemanticAuthority.BRIDGE, SemanticAuthority.configured("bridge"));
        assertEquals("SEMANTIC_AUTHORITY_REMOVED:legacy-compatibility", assertThrows(IllegalArgumentException.class,
                () -> SemanticAuthority.configured("legacy-compatibility")).getMessage());
        assertThrows(IllegalArgumentException.class, () -> SemanticAuthority.configured("legacy"));
        try (var facade = new DefaultJaCaMoFacade(Path.of("."))) {
            assertEquals(SemanticAuthority.BRIDGE, facade.authorityStatus().authority());
        }
    }

    @Test void missingBridgeConfigurationFailsClearlyWithoutLegacyFallback() {
        Path jcm = hello();
        try (var facade = bridge(() -> { throw new IllegalStateException("BRIDGE_CONFIGURATION_REQUIRED:test"); },
                ignored -> { throw new AssertionError("transport must not open"); })) {
            var error = assertThrows(IllegalStateException.class, () -> facade.importProject(jcm));
            assertEquals("BRIDGE_CONFIGURATION_REQUIRED:test", error.getMessage());
            assertNull(facade.projectSummary());
            assertEquals(SemanticAuthority.BRIDGE, facade.authorityStatus().authority());
        }
    }

    @Test void officialBridgeSnapshotIsTheFacadeAuthorityAndExposesNegotiatedState() throws Exception {
        Path jcm = hello();
        ModelSnapshot model = official(jcm);
        BridgeConnectionConfig configuration = configuration(DISTRIBUTION);
        try (var facade = bridge(() -> configuration,
                ignored -> new RecordedBridgeTransport(frames(model, model.sources().getFirst().id().scope(),
                        DISTRIBUTION)))) {
            JaCaMoFacade.ProjectSummary summary = facade.importProject(jcm);
            assertEquals(model.sources().getFirst().id().scope(), summary.projectId());
            assertTrue(summary.structureValid());
            assertFalse(facade.traces().isEmpty());
            JaCaMoFacade.AuthorityStatus status = facade.authorityStatus();
            assertEquals(SemanticAuthority.BRIDGE, status.authority());
            assertEquals("LIVE", status.readiness().name());
            assertEquals(model.modelRevision(), status.modelRevision());
            assertEquals("COMPLETE", status.completeness());
            assertEquals("COMPLETE", status.capabilities().get("official.model"));
            assertEquals("session-phase9", status.sessionId());
            assertEquals(9, status.generation());
            assertEquals("tcp://127.0.0.1:6553", status.endpoint());
        }
    }

    @Test void distributionMismatchIsFailClosedAndCanBeUpgradedWithExplicitConfiguration() throws Exception {
        Path jcm = hello();
        ModelSnapshot model = official(jcm);
        var frames = frames(model, model.sources().getFirst().id().scope(), DISTRIBUTION);
        try (var facade = bridge(() -> configuration("2".repeat(64)),
                ignored -> new RecordedBridgeTransport(frames))) {
            BridgeProtocolException error = assertThrows(BridgeProtocolException.class,
                    () -> facade.importProject(jcm));
            assertEquals("BRIDGE_DISTRIBUTION_UNSUPPORTED", error.getMessage());
            assertNull(facade.projectSummary());
            facade.configureBridge(configuration(DISTRIBUTION));
            assertTrue(facade.importProject(jcm).structureValid());
        }
    }

    @Test void selectedProjectMustMatchTheAuthoritativeBridgeSnapshot() throws Exception {
        Path jcm = hello();
        ModelSnapshot model = official(jcm);
        try (var facade = bridge(() -> configuration(DISTRIBUTION),
                ignored -> new RecordedBridgeTransport(frames(model, "different-project", DISTRIBUTION)))) {
            BridgeProtocolException error = assertThrows(BridgeProtocolException.class,
                    () -> facade.importProject(jcm));
            assertEquals("BRIDGE_PROJECT_KEY_MISMATCH", error.getMessage());
            assertNull(facade.projectSummary());
        }
    }

    private DefaultJaCaMoFacade bridge(java.util.function.Supplier<BridgeConnectionConfig> configuration,
                                        org.tzi.use.plugins.jacamo.bridge.BridgeTransportFactory factory) {
        return new DefaultJaCaMoFacade(Path.of("."), SemanticAuthority.BRIDGE, configuration, factory);
    }

    private BridgeConnectionConfig configuration(String distribution) {
        return new BridgeConnectionConfig(URI.create("tcp://127.0.0.1:6553"), Path.of("phase9-secret.hex"),
                distribution, Set.of("official.model", "runtime.snapshot"), 4 * 1024 * 1024, 5_000, 64);
    }

    private ModelSnapshot official(Path jcm) throws Exception {
        return new OfficialProjectAdapter().adapt(new OfficialProjectLoader().load(jcm), jcm);
    }

    private List<byte[]> frames(ModelSnapshot model, String projectKey, String distributionDigest) {
        String source = "jason:phase9";
        RuntimeSnapshot runtime = new RuntimeSnapshot("phase9-snapshot", model.modelRevision(), Instant.EPOCH,
                Instant.EPOCH, Map.of(source, new SourceWatermark(source, 0)),
                Map.of(source, new SourceWatermark(source, 0)), 1, List.of(),
                Map.of(source, Completeness.COMPLETE), "f".repeat(64));
        DistributionFingerprint distribution = new DistributionFingerprint("1.3.1", distributionDigest,
                Map.of("jason", "3.3.2", "cartago", "3.1", "moise", "1.1", "npl", "0.6.1"));
        return List.of(
                encode(MessageType.HANDSHAKE, model, projectKey, distribution, Map.of("readOnly", true)),
                encode(MessageType.MODEL_SNAPSHOT, model, projectKey, distribution, ContractPayloads.model(model)),
                encode(MessageType.RUNTIME_SNAPSHOT, model, projectKey, distribution,
                        ContractPayloads.runtime(runtime)));
    }

    private byte[] encode(MessageType type, ModelSnapshot model, String projectKey,
                          DistributionFingerprint distribution, Map<String, Object> payload) {
        return ContractCodec.encode(ContractEnvelope.create("1.0.0", type, "phase9-test", distribution,
                projectKey, model.modelRevision(), "session-phase9", 9, type.name(), Instant.EPOCH,
                CAPABILITIES, Completeness.COMPLETE, Map.of(), List.of(), payload));
    }

    private Path hello() {
        return Path.of("src/test/resources/canonical-cases/hello-world/helloworld.jcm")
                .toAbsolutePath().normalize();
    }
}
