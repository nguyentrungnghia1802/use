package org.tzi.use.plugins.jacamo.semantic;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.tzi.use.plugins.jacamo.project.*;
import org.tzi.use.plugins.jacamo.mapping.*;

class V2SemanticModelTest {
    @TempDir Path directory;
    SourceFile source;
    SourceProvenance origin;
    SemanticKindRegistry registry;
    @BeforeEach void prepare() throws Exception {
        Path path = directory.resolve("test.jcm"); Files.writeString(path, "mas sample {}\n");
        source = SourceFile.read(path, SourceKind.JCM);
        origin = new SourceProvenance(new SourceSpan(path, 1, 1, 1, 13), "fixture", source.sha256(), "sample");
        registry = new SemanticKindRegistry(new ActiveBaseline().packaged());
    }
    SemanticElement element(MetamodelKind kind, String owner, String name, Map<String, AttributeValue> attrs,
                            List<SemanticReference> refs) {
        return new SemanticElement(SemanticId.of("sample", kind.dimension(), kind.name(), List.of(owner), name),
                kind, name, List.of(origin), attrs, refs);
    }
    JaCaMoSemanticModel model(List<SemanticElement> elements) {
        return new JaCaMoSemanticModel(new ProjectRoot(directory, "sample"),
                new ProjectDeclaration("sample", List.of(origin), Map.of()), registry, elements, List.of(source), List.of());
    }
    @Test void descriptorInventoryAndUnknownKindsDoNotAcceptHistoricalOrFuzzyNames() {
        assertEquals(registry.mapping().classes().size(), registry.kinds().size());
        assertEquals("SEMANTIC_KIND_UNKNOWN:Organisation", registry.resolve("Organisation").diagnostic());
        assertNull(registry.resolve("agent").kind());
        assertThrows(IllegalArgumentException.class, () -> model(List.of(element(new MetamodelKind("MAS", Dimension.PROJECT), "root", "sample", Map.of(), List.of()))));
    }
    @Test void eachDimensionAndCrossDimensionalOrdersSurviveDeterministicExport() throws Exception {
        var role = element(MetamodelKind.Role, "g", "r", Map.of("id", new AttributeValue.Text("r")), List.of());
        var workspace = element(MetamodelKind.Workspace, "env", "w", Map.of("name", new AttributeValue.Text("w")), List.of());
        var agent = element(MetamodelKind.Agent, "root", "a", Map.of("name", new AttributeValue.Text("a")), List.of(
                new SemanticReference("roles", "r", role.id()), new SemanticReference("workspaces", "w", workspace.id()),
                new SemanticReference("roles", "missing", null)));
        String first = new SemanticDebugWriter().write(model(List.of(agent, role, workspace)));
        assertEquals(first, new SemanticDebugWriter().write(model(List.of(workspace, role, agent))));
        var json = new com.fasterxml.jackson.databind.ObjectMapper().readTree(first);
        assertEquals("2.0.0", json.path("formatVersion").asText());
        assertEquals(registry.ecoreHash(), json.path("metamodelSha256").asText());
        assertTrue(first.contains("UNRESOLVED")); assertTrue(first.contains(source.sha256()));
        assertEquals(agent.references(), model(List.of(agent, role, workspace)).elements().stream()
                .filter(e -> e.id().equals(agent.id())).findFirst().orElseThrow().references());
        assertFalse(first.contains("\"kind\" : \"MAS\""));
    }
    @Test void duplicateNamesKeepDistinctIdsAndInvalidTypedFactsAreRejected() {
        var a = element(MetamodelKind.Agent, "one", "same", Map.of(), List.of());
        var b = element(MetamodelKind.Agent, "two", "same", Map.of(), List.of());
        assertEquals(2, model(List.of(a, b)).symbolIndex().get("same").size());
        assertThrows(IllegalArgumentException.class, () -> model(List.of(a, a)));
        assertThrows(IllegalArgumentException.class, () -> model(List.of(element(MetamodelKind.Agent, "one", "a",
                Map.of("Name", new AttributeValue.Text("a")), List.of()))));
        assertThrows(IllegalArgumentException.class, () -> model(List.of(element(MetamodelKind.Action, "p", "a",
                Map.of("arity", new AttributeValue.Text("one")), List.of()))));
        var bad = element(MetamodelKind.Agent, "one", "a", Map.of(), List.of(new SemanticReference("roles", "same", b.id())));
        assertThrows(IllegalArgumentException.class, () -> model(List.of(bad, b)));
        assertThrows(IllegalArgumentException.class, () -> model(List.of(bad)));
    }
    @Test void enumerationIsTypedAndParserFactsCannotMasqueradeAsAttributes() {
        var action = element(MetamodelKind.Action, "p", "a",
                Map.of("kind", new AttributeValue.EnumLiteral("ActionKind", "EXTERNAL")), List.of());
        assertDoesNotThrow(() -> model(List.of(action)));
        assertThrows(IllegalArgumentException.class, () -> model(List.of(element(MetamodelKind.Action, "p", "a",
                Map.of("kind", new AttributeValue.EnumLiteral("ActionKind", "invented")), List.of()))));
        var withFact = new SemanticElement(action.id(), action.kind(), action.name(), action.provenance(),
                action.attributes(), action.references(), Map.of("rawBody", new AttributeValue.Text("untranslated")));
        assertTrue(new SemanticDebugWriter().write(model(List.of(withFact))).contains("untranslated"));
        assertFalse(withFact.attributes().containsKey("rawBody"));
        assertThrows(UnsupportedOperationException.class, () -> withFact.sourceFacts().clear());
    }
    @Test void typedEnumMaterializesThroughTextAndDirectBackends() throws Exception {
        var plan = new TransformationPlanner().structuralPlan(registry.mapping());
        var instance = new org.tzi.use.plugins.jacamo.materialization.InstancePlan(List.of(
                new org.tzi.use.plugins.jacamo.materialization.ObjectPlan("action1", "Action", "fixture:action1",
                        Map.of("name", new AttributeValue.Text("run"), "arity", new AttributeValue.IntegerNumber(0),
                                "kind", new AttributeValue.EnumLiteral("ActionKind", "EXTERNAL")))), List.of(), List.of());
        var artifacts = new org.tzi.use.plugins.jacamo.materialization.TextBackend().generate("EnumParity", plan, instance);
        var direct = new org.tzi.use.plugins.jacamo.materialization.DirectUseBackend().materialize(artifacts, instance);
        var text = new org.tzi.use.uml.sys.MSystem(direct.system().model());
        for (String command : artifacts.initialCommands().lines().toList()) {
            var errors = new java.io.StringWriter();
            var statement = org.tzi.use.parser.shell.ShellCommandCompiler.compileShellCommand(text.model(), text.state(),
                    text.getVariableEnvironment(), command.substring(1), "enum.cmd", new java.io.PrintWriter(errors), false);
            assertNotNull(statement, errors.toString()); text.execute(statement);
        }
        assertEquals(org.tzi.use.api.UseSystemApi.create(direct.system(), false).evaluate("action1.kind"),
                org.tzi.use.api.UseSystemApi.create(text, false).evaluate("action1.kind"));
    }
    @Test void oppositeAliasesHaveIndependentSourceTracesToTheSameMembershipAssociation() {
        var structure = new TransformationPlanner().structuralPlan(registry.mapping());
        var empty = new org.tzi.use.plugins.jacamo.materialization.InstancePlan(List.of(), List.of(), List.of());
        var trace = new org.tzi.use.plugins.jacamo.trace.TraceBuilder().build(model(List.of()), registry.mapping(), structure, empty);
        for (var alias : registry.mapping().associations().stream().filter(MappingModel.ReferenceMapping::reverse).toList()) {
            var records = trace.records().stream().filter(r -> r.targetUseId().equals("association:" + alias.name())).toList();
            assertEquals(2, records.size());
            assertEquals(2, records.stream().map(r -> r.traceId()).distinct().count());
            assertTrue(records.stream().anyMatch(r -> r.sourceSemanticId().equals(alias.source()) && r.mappingRuleId().equals(alias.id())));
        }
    }
    @Test void exactResolverNeverBindsOutsideCandidatesOrMatchesAnArbitraryAncestor() {
        var first = element(MetamodelKind.Operation, "left", "run", Map.of(), List.of());
        var second = element(MetamodelKind.Operation, "right", "run", Map.of(), List.of());
        var unrelated = element(MetamodelKind.Operation, "right", "other", Map.of(), List.of());
        var caller = element(MetamodelKind.Action, "p", "call", Map.of(), List.of());
        var bindings = new org.tzi.use.plugins.jacamo.binding.BindingFile("1.0.0", List.of(
                org.tzi.use.plugins.jacamo.binding.BindingEntry.active(caller.id().value(), unrelated.id().value(),
                        "OPERATION_BINDING", "negative", source.sha256(), "test")));
        var resolver = new org.tzi.use.plugins.jacamo.resolution.ExactSemanticResolver(List.of(first, second, unrelated, caller), bindings);
        var request = new org.tzi.use.plugins.jacamo.resolution.ResolutionRequest(caller.id().value(), "run",
                Set.of(MetamodelKind.Operation), null, List.of());
        assertEquals(org.tzi.use.plugins.jacamo.resolution.ResolutionResult.Status.INVALID_BINDING, resolver.resolve(request).status());
        var unbound = new org.tzi.use.plugins.jacamo.resolution.ExactSemanticResolver(List.of(second, first), null);
        assertEquals(org.tzi.use.plugins.jacamo.resolution.ResolutionResult.Status.AMBIGUOUS, unbound.resolve(request).status());
        assertEquals(List.of(first.id(), second.id()), unbound.resolve(request).candidates().stream().map(SemanticElement::id).toList());
        assertEquals(first.id(), unbound.resolve(new org.tzi.use.plugins.jacamo.resolution.ResolutionRequest(caller.id().value(),
                "left.run", Set.of(MetamodelKind.Operation), null, List.of())).target().id());
        var nested = new SemanticElement(SemanticId.of("sample", Dimension.ENVIRONMENT, "Operation", List.of("left", "nested"), "run"),
                MetamodelKind.Operation, "run", List.of(origin), Map.of(), List.of());
        assertEquals(org.tzi.use.plugins.jacamo.resolution.ResolutionResult.Status.UNRESOLVED,
                new org.tzi.use.plugins.jacamo.resolution.ExactSemanticResolver(List.of(nested), null).resolve(
                        new org.tzi.use.plugins.jacamo.resolution.ResolutionRequest(caller.id().value(), "left.run",
                                Set.of(MetamodelKind.Operation), null, List.of())).status());
    }
}
