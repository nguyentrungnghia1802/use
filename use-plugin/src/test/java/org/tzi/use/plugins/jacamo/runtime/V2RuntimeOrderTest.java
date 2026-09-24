package org.tzi.use.plugins.jacamo.runtime;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import java.time.*;
import org.junit.jupiter.api.Test;
import org.tzi.use.plugins.jacamo.extraction.StaticProjectImporter;
import org.tzi.use.plugins.jacamo.mapping.*;
import org.tzi.use.plugins.jacamo.materialization.*;
import org.tzi.use.plugins.jacamo.trace.*;

class V2RuntimeOrderTest {
    @org.junit.jupiter.api.io.TempDir Path temporary;
    @Test void activeDefaultReordersMaterializedV2PlanAndReconnectRestoresAuthoritativeOrders() throws Exception {
        var semantic = new StaticProjectImporter().importProject(Path.of("src/test/resources/auction/auction.jcm")).model();
        var mapping = new MappingLoader().loadCanonical(Path.of("."));
        var structure = new TransformationPlanner().plan(semantic, mapping);
        var instances = new InstancePlanner().plan(semantic, mapping, structure);
        var system = new DirectUseBackend().materialize(new TextBackend().generate("RuntimeV2", structure, instances), instances).system();
        var trace = new TraceBuilder().build(semantic, mapping, structure, instances);
        var owner = semantic.elements().getFirst();
        var ownerTrace = trace.bySemanticId(owner.id().value()).stream().filter(t -> t.targetKind().equals("OBJECT")).findFirst().orElseThrow();
        trace.registerRuntimeKey(ownerTrace.traceId(), "runtime:source");
        var engine = new RuntimeMutationEngine(system, trace, new RuntimeMappingLoader().loadDefault(),
                new TraceRuntimeTargetAdapter(trace), structure, instances);
        List<Map<String, Object>> payload = new ArrayList<>();
        for (var spec : structure.orderProjections()) for (var source : semantic.elements()) {
            if (!semantic.registry().owners(source.kind().name()).contains(spec.owner())) continue;
            String feature = spec.sourceIdentity().substring(spec.sourceIdentity().indexOf('#') + 1);
            payload.add(Map.of("sourceIdentity", spec.sourceIdentity(), "ownerSemanticId", source.id().value(),
                    "targetSemanticIds", source.references().stream().filter(r -> r.feature().equals(feature)).map(r -> r.targetId().value()).toList()));
        }
        var initial = RuntimeEvent.create("v2-order-initial", Instant.EPOCH, 1, owner.kind().dimension(),
                RuntimeEventKind.REPLACE_ORDER, "runtime:source", owner.id().value(), Map.of("orders", payload), null);
        var snapshot = new RuntimeSnapshot("v2-orders", Instant.EPOCH, 1, List.of(initial), "exact-source-orders");
        Path replay = temporary.resolve("events.json"); new RuntimeEventCodec().writeEvents(replay, List.of());
        var connector = new SyntheticRuntimeConnector("v2-orders", snapshot, replay, new RuntimeEventCodec());
        var linkSet = system.state().allLinks().stream().map(Object::toString).collect(java.util.stream.Collectors.toSet());
        try (var mirror = new RuntimeMirrorService(connector, engine, 8)) {
            mirror.connect(java.net.URI.create("synthetic://v2-orders")); assertEquals(MirrorState.LIVE, mirror.state());
            assertTrue(engine.compareSnapshot(snapshot).isEmpty());
            List<Map<String, Object>> changed = new ArrayList<>();
            for (var order : payload) {
                var targets = new ArrayList<>((List<?>) order.get("targetSemanticIds")); Collections.reverse(targets);
                changed.add(Map.of("sourceIdentity", order.get("sourceIdentity"), "ownerSemanticId", order.get("ownerSemanticId"), "targetSemanticIds", targets));
            }
            var delta = RuntimeEvent.create("v2-order-delta", Instant.EPOCH, 2, owner.kind().dimension(),
                    RuntimeEventKind.REPLACE_ORDER, "runtime:source", owner.id().value(), Map.of("orders", changed), null);
            new RuntimeEventCodec().writeEvents(replay, List.of(delta)); connector.replayAll(); mirror.awaitIdle(Duration.ofSeconds(5));
            assertEquals(MirrorState.LIVE, mirror.state()); assertFalse(engine.compareSnapshot(snapshot).isEmpty());
            assertTrue(engine.compareSnapshot(new RuntimeSnapshot("changed", Instant.EPOCH, 2, List.of(delta), "changed")).isEmpty());
            assertEquals(linkSet, system.state().allLinks().stream().map(Object::toString).collect(java.util.stream.Collectors.toSet()));
            mirror.disconnect(); assertEquals(MirrorState.STALE, mirror.state());
            mirror.reconnectAndResync(); assertEquals(MirrorState.LIVE, mirror.state());
            assertTrue(engine.compareSnapshot(snapshot).isEmpty());
            assertEquals(instances.objects().size(), system.state().numObjects());
        }
        var link = instances.links().stream().filter(l -> structure.orderProjections().stream()
                .anyMatch(p -> p.association().equals(l.association()))).findFirst().orElseThrow();
        var deletion = RuntimeEvent.create("insufficient-membership-delta", Instant.EPOCH, 3, owner.kind().dimension(),
                RuntimeEventKind.DELETE_LINK, "runtime:source", owner.id().value(),
                Map.of("association", link.association(), "participants", List.of(link.sourceObject(), link.targetObject())), null);
        var rejected = engine.apply(deletion);
        assertEquals(MutationStatus.FAILED, rejected.status());
        assertTrue(rejected.diagnostic().contains("RUNTIME_ORDER_MEMBERSHIP_REQUIRES_RESYNC"), rejected.diagnostic());
        assertEquals(linkSet, system.state().allLinks().stream().map(Object::toString).collect(java.util.stream.Collectors.toSet()));
        var destroy = RuntimeEvent.create("insufficient-destroy", Instant.EPOCH, 4, owner.kind().dimension(),
                RuntimeEventKind.DESTROY_OBJECT, "runtime:source", owner.id().value(), Map.of(), null);
        assertTrue(engine.apply(destroy).diagnostic().contains("RUNTIME_ORDER_MEMBERSHIP_REQUIRES_RESYNC"));
        assertEquals(instances.objects().size(), system.state().numObjects());
    }
}
