package org.tzi.use.plugins.jacamo;

import java.net.URI;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
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
import org.jacamo.bridge.contract.RuntimeEvent;
import org.jacamo.bridge.contract.SourceWatermark;
import org.tzi.use.plugins.jacamo.bridge.BridgeConnectionConfig;
import org.tzi.use.plugins.jacamo.bridge.RecordedBridgeTransport;
import org.tzi.use.main.Session;

/** Official-snapshot facade fixture. Historical parser tests remain separate and never feed production. */
final class BridgeFacadeTestSupport {
    static final String DISTRIBUTION = "1".repeat(64);
    private static final List<Capability> CAPABILITIES = List.of(
            new Capability("official.model", CapabilityStatus.COMPLETE, List.of(), ""),
            new Capability("runtime.snapshot", CapabilityStatus.COMPLETE, List.of(), ""));

    static DefaultJaCaMoFacade facade(Path jcm) {
        return facade(jcm, Path.of("."), () -> false);
    }

    static DefaultJaCaMoFacade facade(Path jcm, java.util.function.BooleanSupplier unavailable) {
        return facade(jcm, Path.of("."), unavailable);
    }

    static DefaultJaCaMoFacade facade(Path jcm, Path checkout) {
        return facade(jcm, checkout, () -> false);
    }

    static DefaultJaCaMoFacade facade(Path jcm, Path checkout,
                                      java.util.function.BooleanSupplier unavailable) {
        Path selected = jcm.toAbsolutePath().normalize();
        return new DefaultJaCaMoFacade(checkout, SemanticAuthority.BRIDGE,
                BridgeFacadeTestSupport::configuration, ignored -> {
                    if (unavailable.getAsBoolean()) throw new IllegalStateException("TEST_BRIDGE_UNAVAILABLE");
                    try {
                        ModelSnapshot model = new OfficialProjectAdapter().adapt(
                                new OfficialProjectLoader().load(selected), selected);
                        return new RecordedBridgeTransport(frames(model, model.sources().getFirst().id().scope()));
                    } catch (RuntimeException error) {
                        throw error;
                    } catch (Exception error) {
                        throw new IllegalStateException("TEST_OFFICIAL_ADAPTER_FAILED", error);
                    }
                });
    }

    static DefaultJaCaMoFacade nativeFacade(Path jcm, Session session,
                                            java.util.function.BooleanSupplier unavailable) {
        return nativeFacadeWithEvents(jcm, session, unavailable, List.of());
    }

    static DefaultJaCaMoFacade nativeFacadeWithEvents(Path jcm, Session session,
                                                      java.util.function.BooleanSupplier unavailable,
                                                      List<RuntimeEvent> events) {
        Path selected = jcm.toAbsolutePath().normalize();
        return new DefaultJaCaMoFacade(Path.of("."), SemanticAuthority.BRIDGE,
                BridgeFacadeTestSupport::configuration, ignored -> {
                    if (unavailable.getAsBoolean()) throw new IllegalStateException("TEST_BRIDGE_UNAVAILABLE");
                    try {
                        ModelSnapshot model = new OfficialProjectAdapter().adapt(
                                new OfficialProjectLoader().load(selected), selected);
                        return new RecordedBridgeTransport(frames(model, model.sources().getFirst().id().scope(), events));
                    } catch (RuntimeException error) {
                        throw error;
                    } catch (Exception error) {
                        throw new IllegalStateException("TEST_OFFICIAL_ADAPTER_FAILED", error);
                    }
                }, PipelineMode.CODE_GROUNDED_NATIVE, session);
    }

    static BridgeConnectionConfig configuration() {
        return new BridgeConnectionConfig(URI.create("tcp://127.0.0.1:6553"), Path.of("test-secret.hex"),
                DISTRIBUTION, Set.of("official.model", "runtime.snapshot"), 4 * 1024 * 1024, 5_000, 64);
    }

    private static List<byte[]> frames(ModelSnapshot model, String projectKey) {
        return frames(model, projectKey, List.of());
    }

    private static List<byte[]> frames(ModelSnapshot model, String projectKey, List<RuntimeEvent> events) {
        String source = "jason:test-facade";
        RuntimeSnapshot runtime = new RuntimeSnapshot("test-snapshot", model.modelRevision(), Instant.EPOCH,
                Instant.EPOCH, Map.of(source, new SourceWatermark(source, 0)),
                Map.of(source, new SourceWatermark(source, 0)), 1, List.of(),
                Map.of(source, Completeness.COMPLETE), "f".repeat(64));
        DistributionFingerprint distribution = new DistributionFingerprint("1.3.1", DISTRIBUTION,
                Map.of("jason", "3.3.2", "cartago", "3.1", "moise", "1.1", "npl", "0.6.1"));
        List<byte[]> frames = new ArrayList<>(List.of(
                encode(MessageType.HANDSHAKE, model, projectKey, distribution, Map.of("readOnly", true)),
                encode(MessageType.MODEL_SNAPSHOT, model, projectKey, distribution, ContractPayloads.model(model)),
                encode(MessageType.RUNTIME_SNAPSHOT, model, projectKey, distribution,
                        ContractPayloads.runtime(runtime))));
        for (RuntimeEvent event : events)
            frames.add(encode(MessageType.RUNTIME_EVENT, model, projectKey, distribution, ContractPayloads.event(event)));
        return frames;
    }

    private static byte[] encode(MessageType type, ModelSnapshot model, String projectKey,
                                 DistributionFingerprint distribution, Map<String, Object> payload) {
        return ContractCodec.encode(ContractEnvelope.create("1.0.0", type, "test-facade", distribution,
                projectKey, model.modelRevision(), "test-session", 1, type.name(), Instant.EPOCH,
                CAPABILITIES, Completeness.COMPLETE, Map.of(), List.of(), payload));
    }

    private BridgeFacadeTestSupport() { }
}
