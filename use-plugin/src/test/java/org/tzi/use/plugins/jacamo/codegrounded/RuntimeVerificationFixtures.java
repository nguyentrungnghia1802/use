package org.tzi.use.plugins.jacamo.codegrounded;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.jacamo.bridge.contract.*;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.NativeRuntimeProjector;

final class RuntimeVerificationFixtures {
    static final String SESSION = "observed-session", REVISION = "observed-model";
    static final BridgeEntityId ID = new BridgeEntityId("cartago", "environment", "artifact", "/main", "box", "uuid-1");
    static final String WORKSPACE = "cartago:workspace:env:/main:ws-1", ARTIFACT = "cartago:artifact:env:/main:uuid-1";
    static final String PROPERTY = "cartago:property:" + ARTIFACT + ":status-1";
    static Map<String, Object> property(String value) {
        return Map.of("normalizedEventKind", "UPSERT_CARTAGO_PROPERTY_SNAPSHOT", "semanticId", PROPERTY,
                "artifactSemanticId", ARTIFACT, "propertyId", "status-1", "name", "status",
                "values", List.of(value), "valueTypes", List.of("java.lang.String"), "annotations", List.of());
    }
    static Map<String, Object> artifact(String semanticId, String uuid) {
        return Map.of("normalizedEventKind", "UPSERT_CARTAGO_ARTIFACT", "semanticId", semanticId,
                "name", "box", "uuid", uuid, "artifactTypeSemanticId", "cartago:artifact-type:env:cartago.Artifact",
                "artifactTypeJavaClassName", "cartago.Artifact", "artifactTypeClassLoaderIdentity", "official-loader",
                "workspaceSemanticId", WORKSPACE, "creatorAgentSemanticId", "");
    }
    static RuntimeSnapshot snapshot(String id, long watermark) {
        var workspace = Map.<String, Object>ofEntries(Map.entry("normalizedEventKind", "UPSERT_CARTAGO_WORKSPACE"),
                Map.entry("semanticId", WORKSPACE), Map.entry("environmentSemanticId", "cartago:environment:env"),
                Map.entry("environmentId", "env"), Map.entry("environmentName", "env"), Map.entry("environmentVersion", "1"),
                Map.entry("defaultInfrastructureLayer", ""), Map.entry("name", "main"), Map.entry("fullName", "/main"),
                Map.entry("uuid", "ws-1"), Map.entry("parentSemanticId", ""), Map.entry("local", true),
                Map.entry("protocol", ""), Map.entry("remotePath", ""), Map.entry("address", ""));
        var w = Map.of("cartago", new SourceWatermark("cartago", watermark));
        return new RuntimeSnapshot(id, REVISION, Instant.EPOCH, Instant.EPOCH, w, w, 1,
                List.of(fact(new BridgeEntityId("cartago", "environment", "workspace", "env", "/main", "ws-1"), RuntimeFactKind.WORKSPACE, workspace),
                        fact(ID, RuntimeFactKind.ARTIFACT, artifact(ARTIFACT, "uuid-1")),
                        fact(new BridgeEntityId("cartago", "environment", "observable-property-snapshot", ARTIFACT, "status-1", "snapshot"),
                                RuntimeFactKind.PROPERTY, property("A"))), Map.of("cartago", Completeness.COMPLETE), "snapshot-" + id);
    }
    private static RuntimeFact fact(BridgeEntityId id, RuntimeFactKind kind, Map<String, Object> payload) {
        return new RuntimeFact(id, kind, payload, List.of(), ProjectionStatus.MATERIALIZED_FAITHFULLY, Completeness.COMPLETE, List.of());
    }
    static RuntimeEvent event(String id, long sequence, RuntimeEventKind kind, BridgeEntityId entity, Map<String, Object> payload) {
        return new RuntimeEvent(id, SESSION, 1, REVISION, "cartago", "cartago", sequence, Instant.EPOCH, kind,
                RuntimeFactKind.ARTIFACT, ProjectionStatus.MATERIALIZED_FAITHFULLY, entity, null, "", "", Map.of(), payload,
                new SourceWatermark("cartago", sequence), Completeness.COMPLETE, List.of());
    }
    static RuntimeEvent delta(String id, long sequence, String value) {
        return event(id, sequence, RuntimeEventKind.CHANGED, ID, Map.of("normalizedEventKind", "APPLY_CARTAGO_PROPERTY_DELTA",
                "semanticId", ARTIFACT, "properties", List.of(property(value)), "removedPropertySemanticIds", List.of()));
    }
    static NativeRuntimeProjector projector() throws Exception {
        var projector = new NativeRuntimeProjector(CodeGroundedTestFixtures.helloPipeline(), SESSION, 1, REVISION);
        projector.applySnapshot(snapshot("initial", 0)); return projector;
    }
}
