package org.tzi.use.plugins.jacamo.mapping;

import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.eclipse.emf.common.util.Diagnostic;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.*;
import org.eclipse.emf.ecore.util.Diagnostician;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.junit.jupiter.api.Test;
import org.tzi.use.uml.mm.MAssociation;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.sys.*;
import org.tzi.use.uml.ocl.value.*;

/** Counterexample to full fidelity of independently ordered eOpposite lists. */
class V2OppositeOrderingAuditTest {
    @SuppressWarnings("unchecked")
    private static EList<EObject> values(EObject object, EReference reference) {
        return (EList<EObject>) object.eGet(reference);
    }

    private static EObject object(EClass type, String id) {
        EObject result = EcoreUtil.create(type);
        for (EAttribute attribute : type.getEAllAttributes()) {
            if (attribute.getLowerBound() > 0 && attribute.getDefaultValueLiteral() == null) {
                assertEquals("EString", attribute.getEType().getName(), "Fixture needs explicit typed required values");
                result.eSet(attribute, id + "_" + attribute.getName());
            }
        }
        return result;
    }

    @Test void independentOppositeOrderCounterexampleAndRepresentableControl() throws Exception {
        var pkg = V2EcoreAuditTest.load(Files.readString(V2EcoreAuditTest.SOURCE));
        var mapping = V2MappingAuditTest.mapping(); V2MappingAuditTest.validate(mapping);
        var errors = new StringWriter();
        MModel model = V2MappingAuditTest.compile(V2MappingAuditTest.fixture(mapping), errors);
        assertNotNull(model, errors.toString());
        var permutations = new ArrayList<List<Integer>>();
        permute(new ArrayList<>(), new boolean[4], permutations);
        assertEquals(24, permutations.size());
        var evidence = new ArrayList<Map<String, Object>>();
        for (var rule : mapping.path("referenceMappings")) {
            var target = rule.path("target");
            if (!rule.has("sourceEOpposite") || target.path("kind").asText().endsWith("ALIAS")) continue;
            if (!target.path("firstEnd").path("ordered").asBoolean() || !target.path("secondEnd").path("ordered").asBoolean()) continue;
            EClass sourceType = (EClass) pkg.getEClassifier(rule.path("sourceOwner").asText());
            EReference forward = (EReference) sourceType.getEStructuralFeature(rule.path("sourceName").asText());
            EReference reverse = forward.getEOpposite();
            assertTrue(forward.isOrdered() && reverse.isOrdered());
            EObject a0 = object(sourceType, "a0"), a1 = object(sourceType, "a1");
            EObject b0 = object(forward.getEReferenceType(), "b0"), b1 = object(forward.getEReferenceType(), "b1");
            values(a0, forward).addAll(List.of(b0, b1));
            values(a1, forward).addAll(List.of(b0, b1));
            // A legal EMF move changes one list order, without changing inverse membership.
            values(a1, forward).move(0, 1);
            values(b0, reverse).move(0, 1);
            assertEquals(List.of(b0, b1), values(a0, forward));
            assertEquals(List.of(b1, b0), values(a1, forward));
            assertEquals(List.of(a1, a0), values(b0, reverse));
            assertEquals(List.of(a0, a1), values(b1, reverse));
            for (EObject obj : List.of(a0, a1, b0, b1)) {
                var diagnostic = Diagnostician.INSTANCE.validate(obj);
                assertEquals(Diagnostic.OK, diagnostic.getSeverity(), diagnostic.toString());
            }
            MAssociation association = model.getAssociation(target.path("name").asText());
            int cycleMatches = 0, controlMatches = 0;
            var observations = new ArrayList<Map<String, Object>>();
            for (var permutation : permutations) {
                var system = new MSystem(model); var state = system.state();
                var left = List.of(state.createObject(model.getClass(sourceType.getName()), "a0"), state.createObject(model.getClass(sourceType.getName()), "a1"));
                var right = List.of(state.createObject(model.getClass(forward.getEReferenceType().getName()), "b0"), state.createObject(model.getClass(forward.getEReferenceType().getName()), "b1"));
                var sourceObjects = List.of(a0, a1, b0, b1);
                var targetObjects = List.of(left.get(0), left.get(1), right.get(0), right.get(1));
                for (int i = 0; i < sourceObjects.size(); i++) {
                    EObject src = sourceObjects.get(i); MObject dst = targetObjects.get(i);
                    for (var attribute : src.eClass().getEAllAttributes()) {
                        Object value = src.eGet(attribute);
                        if (value == null) continue;
                        Value typed = value instanceof Boolean b ? BooleanValue.get(b) : new StringValue(value.toString());
                        dst.state(state).setAttributeValue(dst.cls().attribute(attribute.getName(), true), typed);
                    }
                }
                for (int edge : permutation) state.createLink(association, List.of(left.get(edge / 2), right.get(edge % 2)), null);
                var structuralErrors = new StringWriter();
                assertTrue(state.checkStructure(new PrintWriter(structuralErrors)), structuralErrors.toString());
                var ends = association.associationEnds();
                List<List<String>> actual = List.of(names(state.getNavigableObjects(left.get(0), ends.get(0), ends.get(1), null)),
                        names(state.getNavigableObjects(left.get(1), ends.get(0), ends.get(1), null)),
                        names(state.getNavigableObjects(right.get(0), ends.get(1), ends.get(0), null)),
                        names(state.getNavigableObjects(right.get(1), ends.get(1), ends.get(0), null)));
                var cyclic = List.of(List.of("b0", "b1"), List.of("b1", "b0"), List.of("a1", "a0"), List.of("a0", "a1"));
                var control = List.of(List.of("b0", "b1"), List.of("b0", "b1"), List.of("a0", "a1"), List.of("a0", "a1"));
                if (actual.equals(cyclic)) cycleMatches++;
                if (actual.equals(control)) controlMatches++;
                observations.add(Map.of("insertionOrder", permutation, "navigationOrders", actual));
            }
            assertEquals(0, cycleMatches, "Revisit boundary if USE gains independent end-order support");
            assertTrue(controlMatches > 0, "Positive control must be representable through the same API");
            verifyProjection(List.of(a0, a1, b0, b1), rule.path("id").asText());
            evidence.add(Map.of("rule", rule.path("id").asText(), "association", association.name(),
                    "sourceEMFValidation", "PASS", "testedInsertionOrders", permutations.size(),
                    "cyclicSourceMatchingOrders", cycleMatches, "positiveControlMatchingOrders", controlMatches,
                    "precedenceCycle", "e00 < e01 < e11 < e10 < e00", "observations", observations,
                    "sourceOrders", Map.of("a0.forward", List.of("b0", "b1"), "a1.forward", List.of("b1", "b0"),
                            "b0.reverse", List.of("a1", "a0"), "b1.reverse", List.of("a0", "a1")),
                    "targetStructureValidation", "PASS_FOR_ALL_PERMUTATIONS"));
        }
        assertFalse(evidence.isEmpty(), "Audit must exercise supplied ordered opposite mappings");
        Path output = Files.createDirectories(Path.of("target/phase31-mapping-audit"));
        Files.writeString(output.resolve("ordered-opposite-counterexample.json"), V2MappingAuditTest.JSON.writerWithDefaultPrettyPrinter()
                .writeValueAsString(Map.of("status", "MEMBERSHIP_ONLY_COUNTEREXAMPLE_RETAINED", "projectionAcceptance", "PASS_BOTH_AUTHORITATIVE_ORDERS",
                        "scope", "Native EMF source: membership-only insertion cannot preserve both orders; production target-only projection preserves both", "cases", evidence)) + "\n");
    }

    /** Same native-EMF counterexample through production 2.2 loader/planner/backends and ordered OCL. */
    private void verifyProjection(List<EObject> sources, String membershipRule) throws Exception {
        var mapping = new MappingLoader().loadV2(V2MappingAuditTest.MAPPING, V2MappingAuditTest.SCHEMA, V2EcoreAuditTest.SOURCE);
        var structure = new TransformationPlanner().structuralPlan(mapping);
        var names = new IdentityHashMap<EObject, String>();
        for (int i = 0; i < sources.size(); i++) names.put(sources.get(i), List.of("a0", "a1", "b0", "b1").get(i));
        var objects = new ArrayList<org.tzi.use.plugins.jacamo.materialization.ObjectPlan>();
        for (var source : sources) {
            var attributes = new TreeMap<String, org.tzi.use.plugins.jacamo.semantic.AttributeValue>();
            for (var attr : source.eClass().getEAllAttributes()) {
                Object value = source.eGet(attr); if (value == null) continue;
                attributes.put(attr.getName(), value instanceof Boolean b ? new org.tzi.use.plugins.jacamo.semantic.AttributeValue.Bool(b)
                        : new org.tzi.use.plugins.jacamo.semantic.AttributeValue.Text(value.toString()));
            }
            objects.add(new org.tzi.use.plugins.jacamo.materialization.ObjectPlan(names.get(source), source.eClass().getName(), "source:" + names.get(source), attributes));
        }
        var canonical = mapping.associations().stream().filter(r -> r.id().equals(membershipRule)).findFirst().orElseThrow();
        var links = new ArrayList<org.tzi.use.plugins.jacamo.materialization.LinkPlan>();
        for (String a : List.of("a0", "a1")) for (String b : List.of("b0", "b1"))
            links.add(new org.tzi.use.plugins.jacamo.materialization.LinkPlan(canonical.name(), a, b, false, "source:" + a, "source:" + b, canonical.id()));
        var orders = new ArrayList<OrderProjectionPlanner.SourceOrder>();
        for (var spec : structure.orderProjections()) for (var source : sources) if (source.eClass().getName().equals(spec.owner())) {
            var ref = (EReference) source.eClass().getEStructuralFeature(spec.sourceIdentity().substring(spec.sourceIdentity().indexOf('#') + 1));
            orders.add(new OrderProjectionPlanner.SourceOrder(spec.sourceIdentity(), names.get(source), values(source, ref).stream().map(names::get).toList()));
        }
        var membership = new org.tzi.use.plugins.jacamo.materialization.InstancePlan(objects, links, List.of());
        var projected = new OrderProjectionPlanner().project(structure, membership, orders);
        var text = new org.tzi.use.plugins.jacamo.materialization.TextBackend().generate("NativeOrders", structure, projected);
        var direct = new org.tzi.use.plugins.jacamo.materialization.DirectUseBackend().materialize(text, projected);
        assertTrue(direct.structureValid(), direct.validationOutput()); assertTrue(direct.invariantsValid(), direct.validationOutput());
        var api = org.tzi.use.api.UseSystemApi.create(direct.system(), false);
        for (var order : orders) {
            String query = new OrderNavigationBinding().bind(structure, order.sourceIdentity(), order.ownerObject());
            assertEquals(api.evaluate("Sequence{" + String.join(",", order.targets()) + "}"), api.evaluate(query), order.toString());
        }
        assertEquals(4, direct.system().state().linksOfAssociation(direct.system().model().getAssociation(canonical.name())).size());
    }

    private static List<String> names(List<MObject> values) { return values.stream().map(MObject::name).toList(); }
    private static void permute(List<Integer> prefix, boolean[] used, List<List<Integer>> result) {
        if (prefix.size() == used.length) { result.add(List.copyOf(prefix)); return; }
        for (int i = 0; i < used.length; i++) if (!used[i]) {
            used[i] = true; prefix.add(i); permute(prefix, used, result); prefix.removeLast(); used[i] = false;
        }
    }
}
