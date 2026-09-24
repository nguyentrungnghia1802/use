package org.tzi.use.plugins.jacamo.materialization;

import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.tzi.use.parser.shell.ShellCommandCompiler;
import org.tzi.use.plugins.jacamo.extraction.StaticProjectImporter;
import org.tzi.use.plugins.jacamo.mapping.*;
import org.tzi.use.plugins.jacamo.semantic.*;
import org.tzi.use.uml.sys.MSystem;

class V2MaterializationTest {
    @org.junit.jupiter.api.io.TempDir Path temporary;
    @Test void repeatedArtifactTypesShareDeclarationsButRetainInstanceProjectionTraces() throws Exception {
        java.nio.file.Files.createDirectories(temporary.resolve("src/env"));
        java.nio.file.Files.writeString(temporary.resolve("app.jcm"),
                "mas app { workspace w { artifact a:Agent() artifact b:Agent() } java-path:src/env }");
        java.nio.file.Files.writeString(temporary.resolve("src/env/Agent.java"),
                "class Agent extends Artifact { void init(){ defineObsProperty(\"name\", 1); defineObsProperty(\"tuple\", 1, 2); } @OPERATION void act(int value) {} }");
        var semantic = new StaticProjectImporter().importProject(temporary.resolve("app.jcm")).model();
        var mapping = new MappingLoader().loadCanonical(Path.of("."));
        var structure = new TransformationPlanner().plan(semantic, mapping);
        var concrete = structure.classes().stream().filter(c -> c.ruleId().equals("VP001")).toList();
        assertEquals(1, concrete.size()); assertNotEquals("Agent", concrete.getFirst().name());
        assertEquals(2, structure.operations().size(), "one exact source binding per operation instance");
        var projected = structure.attributes().stream().filter(a -> a.ruleId().equals("VP002")).toList();
        assertEquals(2, projected.size()); assertEquals(1, projected.stream().map(TargetAttributeSpec::name).distinct().count());
        assertNotEquals("name", projected.getFirst().name(), "inherited Artifact.name remains intact");
        assertEquals(2, structure.diagnostics().stream().filter(d -> d.code().equals("VP002_UNSUPPORTED")).count(), "tuple is not a scalar");
        var instances = new InstancePlanner().plan(semantic, mapping, structure);
        var direct = new DirectUseBackend().materialize(new TextBackend().generate("Repeated", structure, instances), instances);
        assertTrue(direct.structureValid(), direct.validationOutput()); assertTrue(direct.invariantsValid(), direct.validationOutput());
        var trace = new org.tzi.use.plugins.jacamo.trace.TraceBuilder().build(semantic, mapping, structure, instances);
        for (var operation : structure.operations()) assertEquals(1, trace.bySemanticId(operation.sourceIdentity()).stream()
                .filter(t -> t.targetKind().equals("OPERATION")).count());
        for (var attribute : projected) assertEquals(1, trace.bySemanticId(attribute.sourceIdentity()).stream()
                .filter(t -> t.targetKind().equals("ATTRIBUTE")).count());
    }
    @Test void bothProjectsHaveActualSoilDirectParityAndExactDefaults() throws Exception {
        for (String fixture : List.of("auction/auction", "counter-team/counter-team")) {
            var semantic = new StaticProjectImporter().importProject(Path.of("src/test/resources/" + fixture + ".jcm")).model();
            var mapping = new MappingLoader().loadCanonical(Path.of("."));
            var structure = new TransformationPlanner().plan(semantic, mapping);
            var instances = new InstancePlanner().plan(semantic, mapping, structure);
            var text = new TextBackend().generate("V2", structure, instances);
            assertEquals(text, new TextBackend().generate("V2", structure, new InstancePlanner().plan(semantic, mapping, structure)));
            var direct = new DirectUseBackend().materialize(text, instances);
            assertTrue(direct.structureValid(), direct.validationOutput());
            assertTrue(direct.invariantsValid(), direct.validationOutput());
            assertTrue(direct.diagnostics().isEmpty(), direct.diagnostics().toString());
            var soil = new MSystem(direct.system().model());
            var errors = new StringWriter();
            for (String line : text.initialCommands().lines().filter(s -> !s.isBlank()).toList()) {
                var statement = ShellCommandCompiler.compileShellCommand(soil.model(), soil.state(), soil.getVariableEnvironment(),
                        line.substring(1), "v2.cmd", new PrintWriter(errors), false);
                assertNotNull(statement, errors.toString()); soil.execute(statement);
            }
            assertTrue(soil.state().check(new PrintWriter(errors), false, true, true, List.of()), errors.toString());
            assertEquals(snapshot(direct.system()), snapshot(soil));
            assertEquals(instances.links().size(), soil.state().allLinks().size());
            var trace = new org.tzi.use.plugins.jacamo.trace.TraceBuilder().build(semantic, mapping, structure, instances);
            assertEquals(instances.links().size(), trace.records().stream()
                    .filter(t -> t.targetKind().equals("LINK") || t.targetKind().equals("ORDER_LINK")).count());
            assertTrue(trace.records().stream().anyMatch(t -> t.sourceKind().equals("ECORE_EXPLICIT_DEFAULT") && t.mappingRuleId() != null));
            assertEquals(semantic.elements().size(), instances.objects().stream()
                    .filter(o -> semantic.elements().stream().anyMatch(e -> e.id().value().equals(o.semanticId()))).count());
            for (var object : instances.objects()) if (object.className().equals("Group")) {
                var source = semantic.elements().stream().filter(e -> e.id().value().equals(object.semanticId())).findFirst().orElseThrow();
                assertEquals(source.attributes().getOrDefault("minCardinality", new AttributeValue.Text("0")), object.values().get("minCardinality"));
                assertEquals(source.attributes().getOrDefault("maxCardinality", new AttributeValue.Text("unlimited")), object.values().get("maxCardinality"));
            }
        }
    }
    @Test void v2CoreChecksOperationKindWithoutMakingOptionalOperationRequired() throws Exception {
        var semantic = new StaticProjectImporter().importProject(Path.of("src/test/resources/auction/auction.jcm")).model();
        var mapping = new MappingLoader().loadCanonical(Path.of("."));
        var structure = new TransformationPlanner().plan(semantic, mapping);
        var instances = new InstancePlanner().plan(semantic, mapping, structure);
        var text = new TextBackend().generate("CoreV2", structure, instances);
        var generated = new org.tzi.use.plugins.jacamo.ocl.OclGenerator().generate("CoreV2", structure, List.of(),
                List.of(new org.tzi.use.plugins.jacamo.ocl.OclProfileLoader().loadCore()));
        var direct = new DirectUseBackend().materialize(new TextBackend.GeneratedArtifacts(generated.useModel(), text.initialCommands()), instances);
        assertTrue(direct.invariantsValid(), direct.validationOutput());
        var system = direct.system();
        var actionLink = instances.links().stream().filter(l -> l.association().equals("Action_operation_Operation")).findFirst().orElseThrow();
        var action = system.state().objectByName(actionLink.sourceObject());
        action.state(system.state()).setAttributeValue(action.cls().attribute("kind", true),
                new org.tzi.use.uml.ocl.value.EnumValue(system.model().enumType("ActionKind"), "INTERNAL"));
        var errors = new StringWriter();
        assertFalse(system.state().check(new PrintWriter(errors), false, true, true, List.of()));
        action.state(system.state()).setAttributeValue(action.cls().attribute("kind", true),
                new org.tzi.use.uml.ocl.value.EnumValue(system.model().enumType("ActionKind"), "EXTERNAL"));
        var unresolved = system.state().createObject(system.model().getClass("Action"), "unresolvedCall");
        unresolved.state(system.state()).setAttributeValue(unresolved.cls().attribute("kind", true),
                new org.tzi.use.uml.ocl.value.EnumValue(system.model().enumType("ActionKind"), "EXTERNAL"));
        assertTrue(system.state().check(new PrintWriter(errors), false, true, true, List.of()), errors.toString());
    }
    @Test void missingRequiredValueIsDiagnosedAndOnlyExplicitDefaultsAreMaterialized() {
        var imported = new StaticProjectImporter().importProject(Path.of("src/test/resources/auction/auction.jcm")).model();
        var group = imported.elements().stream().filter(e -> e.kind() == MetamodelKind.Group).findFirst().orElseThrow();
        var partial = new SemanticElement(group.id(), group.kind(), group.name(), group.provenance(), Map.of(), List.of());
        var semantic = new JaCaMoSemanticModel(imported.projectRoot(), imported.declaration(), imported.registry(),
                List.of(partial), new ArrayList<>(imported.sourceIndex().values()), List.of());
        var mapping = new MappingLoader().loadCanonical(Path.of("."));
        var plan = new InstancePlanner().plan(semantic, mapping, new TransformationPlanner().structuralPlan(mapping));
        var object = plan.objects().getFirst();
        assertEquals(new AttributeValue.Text("0"), object.values().get("minCardinality"));
        assertEquals(new AttributeValue.Text("unlimited"), object.values().get("maxCardinality"));
        assertFalse(object.values().containsKey("id"));
        assertTrue(plan.diagnostics().stream().anyMatch(d -> d.code().equals("MATERIALIZATION_REQUIRED_ATTRIBUTE_MISSING")
                && d.evidence().equals("agentmetamodel::Group#id")));
    }
    static List<String> snapshot(MSystem system) {
        var rows = new ArrayList<String>();
        system.state().allObjects().forEach(o -> {
            rows.add(o.name() + ":" + o.cls().name());
            o.cls().allAttributes().forEach(a -> rows.add(o.name() + "." + a.name() + "=" + o.state(system.state()).attributeValue(a)));
        });
        system.state().allLinks().forEach(l -> rows.add(l.toString()));
        Collections.sort(rows); return rows;
    }
}
