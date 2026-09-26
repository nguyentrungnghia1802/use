package org.jacamo.bridge.contract;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ContractTest {
    private static final Evidence EVIDENCE = new Evidence("ev-1", "official-api", "project:/hello.jcm", "a".repeat(64), "exact");
    private static final BridgeEntityId AGENT = new BridgeEntityId("jason", "agent", "incarnation", "hello", "bob", "session-1:1");

    @Test void envelopeRoundTripsCanonicallyAndOrderingDoesNotChangeDigest() {
        Map<String,Object> payloadA = Map.of("z", List.of(2, 1), "a", Map.of("b", true, "a", "é"));
        Map<String,Object> payloadB = new java.util.LinkedHashMap<>(); payloadB.put("a", Map.of("a", "e\u0301", "b", true)); payloadB.put("z", List.of(2, 1));
        var first = envelope(payloadA);
        var second = envelope(payloadB);
        byte[] encoded = ContractCodec.encode(first);
        var decoded = ContractCodec.decode(encoded);
        new ContractValidator().validate(decoded);
        assertEquals(first, decoded);
        assertArrayEquals(encoded, ContractCodec.encode(decoded));
        assertNotEquals(first.payloadDigest(), second.payloadDigest(), "string normalization is explicit, not guessed by the codec");
        var reordered = envelope(new java.util.TreeMap<>(payloadA));
        assertEquals(first.payloadDigest(), reordered.payloadDigest());
    }

    @Test void typedModelRuntimeAndEventPayloadsRoundTrip() {
        var role = new BridgeEntityId("moise", "organisation", "role", "hello", "visitor", "model");
        var group = new BridgeEntityId("moise", "organisation", "group", "hello", "team", "model");
        var relation = new BridgeRelationId("group-role-cardinality", List.of(group, role), EVIDENCE.evidenceId(), "model");
        var fact = new ModelFact("agent-declaration", AGENT, Map.of("policy", "one"), Map.of("role", List.of(role)), CapabilityStatus.COMPLETE, List.of(EVIDENCE));
        var model = new ModelSnapshot("model-1", List.of(), List.of(fact), List.of(), List.of(), List.of(),
                List.of(new RelationCardinality(relation, group, role, 1, 2, List.of(EVIDENCE))), List.of(), List.of(),
                List.of(new UnresolvedFact("parameter-type", "op/0", CapabilityStatus.UNAVAILABLE, "not exposed", List.of(EVIDENCE))),
                Map.of("groupRoleCardinality", "EVIDENCE_ONLY"));
        Map<String,Object> modelTree = CanonicalJson.object(CanonicalJson.decode(CanonicalJson.encode(ContractPayloads.model(model))));
        assertEquals(model, ContractPayloads.model(modelTree));

        var runtimeFact = new RuntimeFact(AGENT, RuntimeFactKind.AGENT, Map.of("running", true), List.of(),
                ProjectionStatus.MATERIALIZED_FAITHFULLY, Completeness.COMPLETE, List.of(EVIDENCE));
        var runtime = new RuntimeSnapshot("snap-1", "model-1", Instant.EPOCH, Instant.EPOCH.plusSeconds(1),
                Map.of("jason", new SourceWatermark("jason", 1)), Map.of("jason", new SourceWatermark("jason", 2)),
                1, List.of(runtimeFact), Map.of("jason", Completeness.COMPLETE), "f".repeat(64));
        assertEquals(runtime, ContractPayloads.runtime(CanonicalJson.object(CanonicalJson.decode(CanonicalJson.encode(ContractPayloads.runtime(runtime))))));

        var event = new RuntimeEvent("event-1", "session-1", 1, "model-1", "jason", "jason", 3, Instant.EPOCH,
                RuntimeEventKind.CHANGED, RuntimeFactKind.AGENT, ProjectionStatus.MATERIALIZED_FAITHFULLY, AGENT, null, "corr", "cause", Map.of("running", false), Map.of("running", true),
                new SourceWatermark("jason", 3), Completeness.COMPLETE, List.of(EVIDENCE));
        assertEquals(event, ContractPayloads.event(CanonicalJson.object(CanonicalJson.decode(CanonicalJson.encode(ContractPayloads.event(event))))));
    }

    @Test void rejectsMalformedOversizedDeepUnknownMajorDigestAndContradictoryCapability() {
        assertThrows(ContractException.class, () -> CanonicalJson.decode("{\"a\":1,\"a\":2}".getBytes(StandardCharsets.UTF_8)));
        assertThrows(ContractException.class, () -> CanonicalJson.decode("[[[[0]]]]".getBytes(StandardCharsets.UTF_8), 100, 2, 100));
        assertThrows(ContractException.class, () -> CanonicalJson.decode(new byte[11], 10, 10, 10));
        assertThrows(ContractException.class, () -> CanonicalJson.decode("\"abcdef\"".getBytes(StandardCharsets.UTF_8), 100, 10, 3));
        var valid = envelope(Map.of("x", 1));
        var major = copy(valid, "2.0.0", valid.payloadDigest(), valid.capabilities());
        assertThrows(ContractException.class, () -> new ContractValidator().validate(major));
        var badDigest = copy(valid, valid.schemaVersion(), "0".repeat(64), valid.capabilities());
        assertThrows(ContractException.class, () -> new ContractValidator().validate(badDigest));
        var duplicate = copy(valid, valid.schemaVersion(), valid.payloadDigest(), List.of(
                valid.capabilities().getFirst(), valid.capabilities().getFirst()));
        assertThrows(ContractException.class, () -> new ContractValidator().validate(duplicate));
    }

    @Test void envelopeCanonicalizationPreservesExplicitNullOptionalFields() {
        var event=event("null-relation");
        var envelope=ContractEnvelope.create("1.0.0",MessageType.RUNTIME_EVENT,"test",
                new DistributionFingerprint("1.3.1","d".repeat(64),Map.of()),"p","model-1","session-1",1,
                "m",Instant.EPOCH,List.of(),Completeness.COMPLETE,Map.of(),List.of(),ContractPayloads.event(event));
        var decoded=ContractCodec.decode(ContractCodec.encode(envelope));new ContractValidator().validate(decoded);
        assertEquals(event,ContractPayloads.event(decoded.payload()));
    }

    @Test void identityIsReversibleIncarnationAwareAndDisplayCollisionSafe() {
        var composed = new BridgeEntityId("jason", "agent", "live", "prøj", "é/a%b", "s:1");
        var decomposed = new BridgeEntityId("jason", "agent", "live", "prøj", "e\u0301/a%b", "s:1");
        assertEquals(composed, decomposed);
        assertEquals(composed, BridgeEntityId.parse(composed.canonical()));
        assertNotEquals(composed, new BridgeEntityId("jason", "agent", "live", "prøj", "é/a%b", "s:2"));
        var names = new UseDisplayNameAllocator();
        var one = new BridgeEntityId("jason", "agent", "live", "p", "a-b", "s:1");
        var two = new BridgeEntityId("jason", "agent", "live", "p", "a b", "s:1");
        assertNotEquals(names.allocate(one), names.allocate(two));
    }

    @Test void cardinalityIdentityPreservesMultipleContexts() {
        var role = new BridgeEntityId("moise", "org", "role", "os", "member", "model");
        var g1 = new BridgeEntityId("moise", "org", "group", "os", "g1", "model");
        var g2 = new BridgeEntityId("moise", "org", "group", "os", "g2", "model");
        var r1 = new BridgeRelationId("group-role-cardinality", List.of(g1, role), "e1", "model");
        var r2 = new BridgeRelationId("group-role-cardinality", List.of(g2, role), "e2", "model");
        assertNotEquals(r1.canonical(), r2.canonical());
        assertEquals(role, r1.endpoints().get(1));
        assertEquals(role, r2.endpoints().get(1));
    }

    @Test void duplicateEventsAreIdempotentButConflictingPayloadFailsClosed() {
        var event = event("event-1"); var ledger = new EventLedger(4);
        assertEquals(EventLedger.Result.APPLIED, ledger.accept(event, ContractPayloads.event(event)));
        assertEquals(EventLedger.Result.DUPLICATE, ledger.accept(event, ContractPayloads.event(event)));
        var conflicting = new RuntimeEvent(event.eventId(), event.sessionId(), event.generation(), event.modelRevision(), event.subsystem(),
                event.sourceId(), event.sourceSequence(), event.observedAt(), RuntimeEventKind.REMOVED, event.factKind(), event.projectionStatus(), event.entityId(), null, "", "",
                Map.of(), Map.of(), event.watermark(), event.completeness(), event.evidence());
        assertThrows(ContractException.class, () -> ledger.accept(conflicting, ContractPayloads.event(conflicting)));
    }

    @Test void contractSourcesAndRuntimeClasspathArePlatformIndependent() throws Exception {
        String sources = Files.walk(Path.of("src/main/java")).filter(Files::isRegularFile)
                .map(path -> { try { return Files.readString(path); } catch (Exception e) { throw new RuntimeException(e); } })
                .reduce("", String::concat);
        for (String forbidden : List.of("org.tzi.use", "org.eclipse.emf", "jacamo.", "jason.", "cartago.", "moise.", "npl."))
            assertFalse(sources.contains("import " + forbidden), forbidden);
        String java = Path.of(System.getProperty("java.home"), "bin", "java.exe").toString();
        String cp = String.join(System.getProperty("path.separator"), "target/classes", "target/test-classes");
        Process process = new ProcessBuilder(java, "-cp", cp, IsolatedContractConsumerMain.class.getName()).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, process.waitFor(), output);
        assertEquals("ISOLATED_CONTRACT_OK", output.trim());
    }

    private static ContractEnvelope envelope(Map<String,Object> payload) {
        return ContractEnvelope.create("1.1.0", MessageType.MODEL_SNAPSHOT, "dev",
                new DistributionFingerprint("1.3.1", "d".repeat(64), Map.of("jason", "3.3.2")), "hello", "model-1",
                "session-1", 1, "message-1", Instant.EPOCH,
                List.of(new Capability("jason.ast", CapabilityStatus.COMPLETE, List.of("ev-1"), "")), Completeness.COMPLETE,
                Map.of("jason", new SourceWatermark("jason", 3)), List.of(EVIDENCE), payload);
    }
    private static ContractEnvelope copy(ContractEnvelope e, String schema, String digest, List<Capability> capabilities) {
        return new ContractEnvelope(schema,e.messageType(),e.bridgeBuild(),e.distribution(),e.projectKey(),e.modelRevision(),e.sessionId(),e.generation(),e.messageId(),e.producedAt(),capabilities,e.completeness(),e.watermarks(),e.evidence(),digest,e.payload());
    }
    private static RuntimeEvent event(String id) { return new RuntimeEvent(id,"session-1",1,"model-1","jason","jason",1,Instant.EPOCH,RuntimeEventKind.ADDED,RuntimeFactKind.AGENT,ProjectionStatus.MATERIALIZED_FAITHFULLY,AGENT,null,"","",Map.of(),Map.of("x",1),new SourceWatermark("jason",1),Completeness.COMPLETE,List.of(EVIDENCE)); }
}
