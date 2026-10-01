package org.tzi.use.plugins.jacamo.codegrounded;

import static org.junit.jupiter.api.Assertions.*;
import static org.tzi.use.plugins.jacamo.codegrounded.RuntimeVerificationFixtures.*;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import org.jacamo.bridge.contract.*;
import org.junit.jupiter.api.Test;
import org.tzi.use.plugins.jacamo.bridge.*;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.NativeRuntimeProjector;
import org.tzi.use.plugins.jacamo.verification.VerificationOutcome;

/** Negative controls exercise the actual bounded BridgeClient and native coverage callback. */
class NativeRuntimeBridgeCoverageTest {
    @Test void actualBootstrapBufferOverflowInvalidatesPreviouslyPassingNativeResults() throws Exception {
        var model = CodeGroundedTestFixtures.helloSnapshot(); var pipeline = new CodeGroundedNativePipeline().build(model);
        var transport = new Transport(model, 3);
        var projector = new NativeRuntimeProjector(pipeline, SESSION, 1, transport.model.modelRevision());
        projector.applySnapshot(transport.snapshot); projector.coordinator().loadProfileSource("user.ocl", RuntimeVerificationCoordinatorTest.PROFILE);
        String before = projector.coordinator().latest().stateHash(); long version = projector.coordinator().latest().stateVersion();
        try (var client = client(transport, projector)) {
            assertThrows(BridgeProtocolException.class, client::synchronize);
            assertEquals("STALE", projector.coordinator().latest().freshness());
            assertEquals("INCOMPLETE", projector.coordinator().latest().coverage());
            assertEquals(0, projector.coordinator().latest().count(VerificationOutcome.PASS));
            assertTrue(projector.coordinator().latest().diagnostic().contains("BUFFER_OVERFLOW"));
            assertEquals(before, projector.coordinator().latest().stateHash()); assertEquals(version, projector.coordinator().latest().stateVersion());
        }
    }
    @Test void mirrorRejectionAndSubscriptionFailureReachNativeCoverageInsteadOfLeavingCachedPass() throws Exception {
        var model = CodeGroundedTestFixtures.helloSnapshot(); var pipeline = new CodeGroundedNativePipeline().build(model);
        var transport = new Transport(model, 0);
        var projector = new NativeRuntimeProjector(pipeline, SESSION, 1, transport.model.modelRevision());
        try (var client = client(transport, projector)) {
            projector.applySnapshot(client.synchronize().runtime());
            projector.coordinator().loadProfileSource("user.ocl", RuntimeVerificationCoordinatorTest.PROFILE);
            assertTrue(client.receive(transport.frame(MessageType.RUNTIME_EVENT, ContractPayloads.event(transport.event(1)))));
            assertTrue(projector.coordinator().latest().count(VerificationOutcome.PASS) > 0);
            assertThrows(BridgeProtocolException.class, () -> client.receive(transport.frame(MessageType.RUNTIME_EVENT,
                    ContractPayloads.event(transport.event(3)))));
            assertEquals(0, projector.coordinator().latest().count(VerificationOutcome.PASS));
            assertEquals("STALE", projector.coordinator().latest().freshness());
            assertEquals("bridge-3", projector.coordinator().latest().eventId());
            assertEquals("cartago", projector.coordinator().latest().sourceId());
            assertEquals(3, projector.coordinator().latest().sourceSequence());
            projector.applySnapshot(transport.snapshot);
            assertTrue(projector.coordinator().latest().count(VerificationOutcome.PASS) > 0);
            transport.failure.accept(new BridgeProtocolException("TEST_SUBSCRIPTION_LOST"));
            assertEquals(0, projector.coordinator().latest().count(VerificationOutcome.PASS));
            assertTrue(projector.coordinator().latest().diagnostic().contains("SUBSCRIPTION_LOST"));
        }
    }
    private BridgeClient client(Transport transport, NativeRuntimeProjector projector) {
        return new BridgeClient(transport, new BridgeMirrorStateMachine(16), "1".repeat(64),
                Set.of("official.model", "runtime.snapshot"), 2, projector::apply,
                (event,diagnostic) -> projector.coordinator().coverageGap(event,diagnostic));
    }
    private static final class Transport implements BridgeTransport {
        final ModelSnapshot model; final RuntimeSnapshot snapshot; final int burst;
        Consumer<byte[]> receiver; Consumer<RuntimeException> failure;
        Transport(ModelSnapshot model, int burst) {
            this.model = model; this.burst = burst; var base = RuntimeVerificationFixtures.snapshot("bridge-baseline", 0);
            snapshot = new RuntimeSnapshot(base.snapshotId(), model.modelRevision(), base.captureStartedAt(), base.captureEndedAt(),
                    base.startWatermarks(), base.endWatermarks(), base.validationAttempts(), base.facts(), base.sourceCompleteness(), base.stateFingerprint());
        }
        RuntimeEvent event(long sequence) {
            var base = delta("bridge-" + sequence, sequence, "A");
            return new RuntimeEvent(base.eventId(), SESSION, 1, model.modelRevision(), base.subsystem(), base.sourceId(), sequence,
                    base.observedAt(), base.kind(), base.factKind(), base.projectionStatus(), base.entityId(), base.relationId(),
                    "", "", base.before(), base.after(), base.watermark(), base.completeness(), base.evidence());
        }
        @Override public byte[] handshake() { return frame(MessageType.HANDSHAKE, Map.of("readOnly", true)); }
        @Override public byte[] modelSnapshot() { return frame(MessageType.MODEL_SNAPSHOT, ContractPayloads.model(model)); }
        @Override public byte[] runtimeSnapshot() {
            for (int i = 1; i <= burst; i++) receiver.accept(frame(MessageType.RUNTIME_EVENT, ContractPayloads.event(event(i))));
            return frame(MessageType.RUNTIME_SNAPSHOT, ContractPayloads.runtime(snapshot));
        }
        @Override public Subscription subscribe(String token, Consumer<byte[]> receiver) { return subscribe(token, receiver, ignored -> {}); }
        @Override public Subscription subscribe(String token, Consumer<byte[]> receiver, Consumer<RuntimeException> failure) {
            this.receiver = receiver; this.failure = failure; return () -> {};
        }
        @Override public void acknowledge(String token) { }
        @Override public void close() { }
        byte[] frame(MessageType type, Map<String, Object> payload) {
            return ContractCodec.encode(ContractEnvelope.create("1.0.0", type, "coverage-test",
                    new DistributionFingerprint("1.3.1", "1".repeat(64), Map.of()), model.sources().getFirst().id().scope(),
                    model.modelRevision(), SESSION, 1, type.name(), Instant.EPOCH,
                    List.of(new Capability("official.model", CapabilityStatus.COMPLETE, List.of(), ""),
                            new Capability("runtime.snapshot", CapabilityStatus.COMPLETE, List.of(), "")),
                    Completeness.COMPLETE, Map.of(), List.of(), payload));
        }
    }
}
