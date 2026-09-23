package org.tzi.use.plugins.jacamo.mapping;

import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.tzi.use.api.UseSystemApi;
import org.tzi.use.parser.shell.ShellCommandCompiler;
import org.tzi.use.plugins.jacamo.materialization.*;
import org.tzi.use.uml.sys.MSystem;
import org.tzi.use.plugins.jacamo.trace.*;
import org.tzi.use.plugins.jacamo.runtime.OrderProjectionRuntimeBinding;
import org.tzi.use.plugins.jacamo.runtime.*;

class OrderProjectionTest {
    @Test void workingLoaderRejectsStaleOrSemanticallyChangedOrderContract() throws Exception {
        for (String mutation : List.of("hash", "ordered", "opposite", "target", "contract", "duplicate")) {
            var root = (com.fasterxml.jackson.databind.node.ObjectNode) V2MappingAuditTest.mapping();
            switch (mutation) {
                case "hash" -> ((com.fasterxml.jackson.databind.node.ObjectNode) root.path("sourceMetamodel")).put("sha256", "0".repeat(64));
                case "ordered" -> ((com.fasterxml.jackson.databind.node.ObjectNode) root.path("referenceMappings").get(0)).put("sourceOrdered", false);
                case "opposite" -> root.path("referenceMappings").forEach(r -> { if (r.has("sourceEOpposite")) ((com.fasterxml.jackson.databind.node.ObjectNode) r).put("sourceEOpposite", "agentmetamodel::Missing#missing"); });
                case "target" -> ((com.fasterxml.jackson.databind.node.ObjectNode) root.path("referenceMappings").get(0).path("target").path("secondEnd")).put("class", "Missing");
                case "contract" -> ((com.fasterxml.jackson.databind.node.ObjectNode) root.path("orderProjection")).put("rankBase", 1);
                case "duplicate" -> ((com.fasterxml.jackson.databind.node.ObjectNode) root.path("referenceMappings").get(0)).put("id", root.path("referenceMappings").get(1).path("id").asText());
            }
            byte[] changed = V2MappingAuditTest.JSON.writeValueAsBytes(root);
            var loader = new MappingLoader(path -> path.equals(V2MappingAuditTest.MAPPING) ? changed : java.nio.file.Files.readAllBytes(path));
            assertThrows(MappingException.class, () -> loader.loadWorking(V2MappingAuditTest.MAPPING, V2MappingAuditTest.SCHEMA, V2EcoreAuditTest.SOURCE), mutation);
        }
    }

    @Test void compositionKeepsOwnershipWhileProjectionCarriesSequence() {
        var base = structure(false);
        var relation = base.associations().getFirst();
        var structure = new TransformationPlan(base.classes(), List.of(), List.of(new TargetAssociationSpec("Members", "USE_COMPOSITION",
                new MappingModel.AssociationEnd("Left", "0..1", "lefts", false), relation.secondEnd(), relation.sourceIdentity(), relation.ruleId())),
                List.of(), List.of(), base.orderProjections());
        var members = new InstancePlan(members().objects(), members().links().stream().filter(l -> l.sourceObject().equals("a0")).toList(), List.of());
        var orders = List.of(new OrderProjectionPlanner.SourceOrder("p::Left#rights", "a0", List.of("b1", "b0")),
                new OrderProjectionPlanner.SourceOrder("p::Left#rights", "a1", List.of()));
        var plan = new OrderProjectionPlanner().project(structure, members, orders);
        var result = new DirectUseBackend().materialize(new TextBackend().generate("Composition", structure, plan), plan);
        assertTrue(result.structureValid(), result.validationOutput()); assertTrue(result.invariantsValid(), result.validationOutput());
    }
    @Test void workingLoaderDerivesAllOrdersAndFullV2TargetCompiles() throws Exception {
        var mapping = new MappingLoader().loadWorking(V2MappingAuditTest.MAPPING, V2MappingAuditTest.SCHEMA, V2EcoreAuditTest.SOURCE);
        var plan = new TransformationPlanner().structuralPlan(mapping);
        var root = V2MappingAuditTest.mapping();
        long expected = java.util.stream.StreamSupport.stream(root.path("referenceMappings").spliterator(), false)
                .filter(r -> r.path("sourceOrdered").asBoolean() && r.path("sourceMultiplicity").path("upper").asInt() != 1).count();
        assertEquals(expected, plan.orderProjections().size());
        var errors = new StringWriter();
        String generated = new StructuralUseGenerator().generate("V2Ordered", plan);
        assertNotNull(V2MappingAuditTest.compile(generated, errors), errors.toString());
        java.nio.file.Path output = java.nio.file.Files.createDirectories(java.nio.file.Path.of("target/phase31-order-projection"));
        java.nio.file.Files.writeString(output.resolve("mapping-v2-ordered.use"), generated);
        assertEquals(mapping.associations().stream().filter(r -> !r.reverse()).count(), plan.associations().size());
    }
    static TransformationPlan structure(boolean reverse) {
        var classes = List.of(new TargetClassSpec("Left", false, List.of(), "p::Left", "C1"),
                new TargetClassSpec("Right", false, List.of(), "p::Right", "C2"));
        var association = new TargetAssociationSpec("Members", "USE_ASSOCIATION",
                new MappingModel.AssociationEnd("Left", "*", "lefts", reverse),
                new MappingModel.AssociationEnd("Right", "*", "rights", true), "p::Left#rights", "R1");
        var orders = new ArrayList<OrderProjectionSpec>();
        orders.add(new OrderProjectionSpec("p::Left#rights", "R1", "Left", "Right", "Members", false, "rights"));
        if (reverse) orders.add(new OrderProjectionSpec("p::Right#lefts", "R2", "Right", "Left", "Members", true, "lefts"));
        return new TransformationPlan(classes, List.of(), List.of(association), List.of(), List.of(), orders);
    }
    static InstancePlan members() {
        var objects = List.of(new ObjectPlan("a0", "Left", "s:a0", Map.of()), new ObjectPlan("a1", "Left", "s:a1", Map.of()),
                new ObjectPlan("b0", "Right", "s:b0", Map.of()), new ObjectPlan("b1", "Right", "s:b1", Map.of()));
        var links = new ArrayList<LinkPlan>();
        for (String a : List.of("a0", "a1")) for (String b : List.of("b0", "b1"))
            links.add(new LinkPlan("Members", a, b, false, "s:" + a, "s:" + b, "R1"));
        return new InstancePlan(objects, links, List.of());
    }
    static List<OrderProjectionPlanner.SourceOrder> orders() {
        return List.of(new OrderProjectionPlanner.SourceOrder("p::Left#rights", "a0", List.of("b0", "b1")),
                new OrderProjectionPlanner.SourceOrder("p::Left#rights", "a1", List.of("b1", "b0")),
                new OrderProjectionPlanner.SourceOrder("p::Right#lefts", "b0", List.of("a1", "a0")),
                new OrderProjectionPlanner.SourceOrder("p::Right#lefts", "b1", List.of("a0", "a1")));
    }
    static void assertOrders(MSystem system) throws Exception {
        var api = UseSystemApi.create(system, false);
        assertEquals(api.evaluate("Sequence{b0,b1}"), api.evaluate("a0.ordered_R1()"));
        assertEquals(api.evaluate("Sequence{b1,b0}"), api.evaluate("a1.ordered_R1()"));
        assertEquals(api.evaluate("Sequence{a1,a0}"), api.evaluate("b0.ordered_R2()"));
        assertEquals(api.evaluate("Sequence{a0,a1}"), api.evaluate("b1.ordered_R2()"));
        assertEquals(4, system.state().linksOfAssociation(system.model().getAssociation("Members")).size());
    }
    @Test void cyclicIndependentOrdersHaveTextDirectParityAndDeterministicOutput() throws Exception {
        var structure = structure(true); var planner = new OrderProjectionPlanner();
        var instances = planner.project(structure, members(), orders());
        var text = new TextBackend().generate("Ordering", structure, instances);
        var reversed = new ArrayList<>(orders()); Collections.reverse(reversed);
        assertEquals(text, new TextBackend().generate("Ordering", structure, planner.project(structure, members(), reversed)));
        var direct = new DirectUseBackend().materialize(text, instances);
        assertTrue(direct.structureValid(), direct.validationOutput());
        assertTrue(direct.invariantsValid(), direct.validationOutput()); assertOrders(direct.system());
        var system = new MSystem(direct.system().model());
        for (String line : text.initialCommands().lines().toList()) {
            var errors = new StringWriter();
            var statement = ShellCommandCompiler.compileShellCommand(system.model(), system.state(), system.getVariableEnvironment(),
                    line.substring(1), "order.cmd", new PrintWriter(errors), false);
            assertNotNull(statement, errors.toString()); system.execute(statement);
        }
        assertOrders(system);
        assertEquals(direct.system().state().numObjects(), system.state().numObjects());
    }
    @Test void sourceOrderCannotBeMissingDuplicatedOrInferredFromMembership() {
        var planner = new OrderProjectionPlanner();
        assertThrows(MappingException.class, () -> planner.project(structure(true), members(), List.of()));
        var bad = new ArrayList<>(orders()); bad.set(0, new OrderProjectionPlanner.SourceOrder("p::Left#rights", "a0", List.of("b0", "b0")));
        assertThrows(MappingException.class, () -> planner.project(structure(true), members(), bad));
        bad.set(0, new OrderProjectionPlanner.SourceOrder("p::Left#rights", "a0", List.of("b0")));
        assertThrows(MappingException.class, () -> planner.project(structure(true), members(), bad));
    }
    @Test void nonOppositeOrderedAndUnorderedMembershipRemainIndependent() {
        var s = structure(false); var projected = new OrderProjectionPlanner().project(s, members(), orders().subList(0, 2));
        var direct = new DirectUseBackend().materialize(new TextBackend().generate("OneWay", s, projected), projected);
        assertTrue(direct.invariantsValid(), direct.validationOutput());
        var unordered = new TransformationPlan(s.classes(), s.attributes(), s.associations(), s.operations(), s.diagnostics());
        assertEquals(members(), new OrderProjectionPlanner().project(unordered, members(), List.of()));
    }

    @Test void tracedOrderOnlyMutationAndAuthoritativeReconnectPreserveMembershipAndBothOrders() throws Exception {
        var structure = structure(true); var planner = new OrderProjectionPlanner();
        var initial = new ArrayList<>(orders());
        initial.set(1, new OrderProjectionPlanner.SourceOrder("p::Left#rights", "a1", List.of("b0", "b1")));
        initial.set(2, new OrderProjectionPlanner.SourceOrder("p::Right#lefts", "b0", List.of("a0", "a1")));
        var instances = planner.project(structure, members(), initial);
        var trace = new TraceIndex(new OrderProjectionTrace().build(structure, instances));
        var runtime = new OrderProjectionRuntimeBinding();
        var system = runtime.resync("Reconnect", structure, members(), initial).system();
        var membershipLinks = new HashSet<>(system.state().linksOfAssociation(system.model().getAssociation("Members")).links());
        assertThrows(IllegalArgumentException.class, () -> runtime.reorder(system, structure, members(), orders(), new TraceIndex(List.of())));
        assertEquals(4, runtime.reorder(system, structure, members(), orders(), trace));
        assertEquals(0, runtime.reorder(system, structure, members(), orders(), trace));
        assertEquals(membershipLinks, system.state().linksOfAssociation(system.model().getAssociation("Members")).links());
        assertOrders(system);
        var ocl = new OrderNavigationBinding().bind(structure, "p::Right#lefts", "b0");
        assertEquals(UseSystemApi.create(system, false).evaluate("Sequence{a1,a0}"), UseSystemApi.create(system, false).evaluate(ocl));
        assertThrows(MappingException.class, () -> new OrderNavigationBinding().bind(structure, "lefts", "b0"));
        assertOrders(runtime.resync("Reconnect", structure, members(), orders()).system());
        var row = instances.objects().stream().filter(o -> o.className().equals("OrderEntry_R1")).findFirst().orElseThrow();
        var obj = system.state().objectByName(row.name());
        obj.state(system.state()).setAttributeValue(obj.cls().attribute("rank", true), new org.tzi.use.uml.ocl.value.IntegerValue(999));
        assertFalse(system.state().check(new PrintWriter(new StringWriter()), false, true, true, List.of()));
        assertOrders(runtime.resync("Reconnect", structure, members(), orders()).system());
    }

    @Test void existingRuntimeQueueLifecycleReconnectAndDriftUseIndependentOrderBinding() throws Exception {
        var structure = structure(true); var membership = members();
        var instances = new OrderProjectionPlanner().project(structure, membership, orders());
        var records = new ArrayList<>(new OrderProjectionTrace().build(structure, instances));
        for (var o : membership.objects()) records.add(new TraceRecord("trace:" + o.name(), o.semanticId(), "object:" + o.name(),
                o.className(), "OBJECT", "TEST_SOURCE", null, null, null, "runtime:" + o.name(), TraceRecord.Status.RESOLVED));
        var trace = new TraceIndex(records);
        var system = new OrderProjectionRuntimeBinding().resync("RuntimeOrder", structure, membership, orders()).system();
        var rule = new RuntimeMapping.Rule("ORDER_V1", "NORMALIZED", "ANY", RuntimeEventKind.REPLACE_ORDER, true,
                "EXACT_TRACE", false, List.of("orders"), RuntimeSemanticAction.RELATION_REORDER, "ORDER_NAVIGATION",
                "orderProjection", true, "REPLACE_ORDER", "AFTER_MUTATION", "SUPPORTED", List.of("generic test"), List.of(), List.of(), "WORKING");
        var mapping = new RuntimeMapping("1.0.0", "WORKING", "V2_ORDER_V1", List.of(rule));
        new RuntimeMappingValidator(new OrderRuntimeBindingContract(structure)).validate(mapping);
        var engine = new RuntimeMutationEngine(system, trace, mapping, new TraceRuntimeTargetAdapter(trace), structure, membership);
        var payload = orders().stream().map(o -> Map.<String, Object>of("sourceIdentity", o.sourceIdentity(),
                "ownerSemanticId", "s:" + o.ownerObject(), "targetSemanticIds", o.targets().stream().map(t -> "s:" + t).toList())).toList();
        var event = RuntimeEvent.create("order:1", java.time.Instant.EPOCH, 1, org.tzi.use.plugins.jacamo.semantic.Dimension.AGENT,
                RuntimeEventKind.REPLACE_ORDER, "runtime:a0", "s:a0", Map.of("orders", payload), null);
        var snapshot = new RuntimeSnapshot("order-snapshot", java.time.Instant.EPOCH, 1, List.of(event), "authoritative-order-1");
        var replay = java.nio.file.Files.createTempFile("order-replay", ".jsonl");
        var connector = new SyntheticRuntimeConnector("order-test", snapshot, replay, new RuntimeEventCodec());
        try (var mirror = new RuntimeMirrorService(connector, engine, 8)) {
            mirror.connect(java.net.URI.create("synthetic://orders")); assertEquals(MirrorState.LIVE, mirror.state()); assertOrders(system);
            var changed = new ArrayList<>(payload);
            changed.set(0, Map.of("sourceIdentity", "p::Left#rights", "ownerSemanticId", "s:a0", "targetSemanticIds", List.of("s:b1", "s:b0")));
            var delta = RuntimeEvent.create("order:2", java.time.Instant.EPOCH, 2, org.tzi.use.plugins.jacamo.semantic.Dimension.AGENT,
                    RuntimeEventKind.REPLACE_ORDER, "runtime:a0", "s:a0", Map.of("orders", changed), null);
            new RuntimeEventCodec().writeEvents(replay, List.of(delta)); connector.replayAll(); mirror.awaitIdle(java.time.Duration.ofSeconds(5));
            assertEquals(MirrorState.LIVE, mirror.state());
            assertEquals(UseSystemApi.create(system, false).evaluate("Sequence{b1,b0}"), UseSystemApi.create(system, false).evaluate("a0.ordered_R1()"));
            assertFalse(engine.compareSnapshot(snapshot).isEmpty());
            mirror.disconnect(); assertEquals(MirrorState.STALE, mirror.state());
            var row = instances.objects().stream().filter(o -> o.className().equals("OrderEntry_R1")).findFirst().orElseThrow();
            var obj = system.state().objectByName(row.name());
            obj.state(system.state()).setAttributeValue(obj.cls().attribute("rank", true), new org.tzi.use.uml.ocl.value.IntegerValue(99));
            mirror.reconnectAndResync(); assertEquals(MirrorState.LIVE, mirror.state()); assertOrders(system);
            assertTrue(engine.compareSnapshot(snapshot).isEmpty());
        } finally { java.nio.file.Files.deleteIfExists(replay); }
    }
}

