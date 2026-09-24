package org.tzi.use.plugins.jacamo.extraction;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tzi.use.plugins.jacamo.semantic.*;

class V2ExtractionTest {
    @TempDir Path directory;
    @Test void sameFunctorWithDistinctLiteralsHasDistinctStableIdsAndExactSpans() throws Exception {
        Files.writeString(directory.resolve("p.jcm"), "mas p { agent a:a.asl }");
        Files.writeString(directory.resolve("a.asl"), "  value(1).\n  value(2).\n");
        var result = new StaticProjectImporter().importProject(directory.resolve("p.jcm"));
        assertTrue(result.success(), result.diagnostics().toString());
        var values = result.model().elements().stream().filter(e -> e.kind().equals(MetamodelKind.Belief)).toList();
        assertEquals(2, values.size()); assertEquals(2, values.stream().map(SemanticElement::id).distinct().count());
        assertEquals(2, result.model().symbolIndex().get("value").size());
        assertTrue(values.stream().allMatch(e -> e.provenance().getFirst().span().startColumn() == 3));
    }
    @Test void counterTeamUsesOnlyActiveKindsAndExactEnums() {
        var result = new StaticProjectImporter().importProject(Path.of("src/test/resources/counter-team/counter-team.jcm"));
        assertTrue(result.success(), result.diagnostics().toString());
        assertNotNull(result.model().registry());
        assertTrue(result.model().elements().stream().allMatch(e -> MetamodelKind.registry().resolve(e.kind().name()).kind() != null));
        var norm = result.model().elements().stream().filter(e -> e.kind().equals(MetamodelKind.Norm)).findFirst().orElseThrow();
        assertEquals(new AttributeValue.EnumLiteral("NormType", "PERMISSION"), norm.attributes().get("type"));
        assertEquals(new SemanticDebugWriter().write(result.model()), new SemanticDebugWriter().write(
                new StaticProjectImporter().importProject(Path.of("src/test/resources/counter-team/counter-team.jcm")).model()));
        assertTrue(MetamodelKind.registry().enumValue("NormType", "Permission").isEmpty());
    }
    @Test void independentInverseOrderCannotBeInventedFromTwoJoinDeclarations() throws Exception {
        Files.writeString(directory.resolve("p.jcm"), "mas p { agent a:a.asl { join: w } agent b:b.asl { join: w } workspace w {} }");
        Files.writeString(directory.resolve("a.asl"), "ready.\n"); Files.writeString(directory.resolve("b.asl"), "ready.\n");
        var result = new StaticProjectImporter().importProject(directory.resolve("p.jcm"));
        assertNotNull(result.model(), result.diagnostics().toString());
        var workspace = result.model().elements().stream().filter(e -> e.kind().equals(MetamodelKind.Workspace)).findFirst().orElseThrow();
        assertEquals(2, workspace.references().stream().filter(r -> r.feature().equals("agents")).count());
        assertTrue(workspace.sourceFacts().containsKey("orderUnresolved:agents"));
        var mapping = new org.tzi.use.plugins.jacamo.mapping.MappingLoader().loadCanonical(Path.of("."));
        var error = assertThrows(org.tzi.use.plugins.jacamo.materialization.MaterializationException.class,
                () -> new org.tzi.use.plugins.jacamo.materialization.InstancePlanner().plan(result.model(), mapping,
                        new org.tzi.use.plugins.jacamo.mapping.TransformationPlanner().structuralPlan(mapping)));
        assertEquals("ORDER_SOURCE_UNRESOLVED", error.code());
    }
    @Test void sharedGroupSpecificationProducesOwnerQualifiedOccurrencesAndExactXmlSpans() throws Exception {
        Files.writeString(directory.resolve("p.jcm"), "mas p { organisation o:o.xml { group first:g group second:g } }");
        Files.writeString(directory.resolve("o.xml"), """
                <organisational-specification id="o">
                  <structural-specification>
                    <role-def id="r"/>
                    <group-specification id="g"><roles><role id="r"/></roles></group-specification>
                  </structural-specification>
                </organisational-specification>
                """);
        var result = new StaticProjectImporter().importProject(directory.resolve("p.jcm"));
        assertTrue(result.success(), result.diagnostics().toString());
        var roles = result.model().elements().stream().filter(e -> e.kind().equals(MetamodelKind.Role)).toList();
        assertEquals(2, roles.size()); assertNotEquals(roles.get(0).id(), roles.get(1).id());
        assertEquals(Set.of("first", "second"), roles.stream().map(e -> e.id().ownerPath().getLast()).collect(java.util.stream.Collectors.toSet()));
        for (var role : roles) {
            var origin = role.provenance().getFirst();
            assertEquals(4, origin.span().startLine());
            String line = Files.readAllLines(origin.span().path()).get(origin.span().startLine() - 1);
            assertEquals(origin.originalSpelling(), line.substring(origin.span().startColumn() - 1, origin.span().endColumn()));
        }
    }
    @Test void explicitlyNestedGoalPlanPreservesContainmentAndRejectsExternalEntities() throws Exception {
        Files.writeString(directory.resolve("p.jcm"), "mas p { organisation o:o.xml }");
        String source = "<organisational-specification id=\"o\"><functional-specification><scheme id=\"s\">"
                + "<goal id=\"root\"><plan operator=\"sequence\"><goal id=\"child\"/></plan></goal>"
                + "<mission id=\"m\"><goal id=\"child\"/></mission></scheme></functional-specification></organisational-specification>";
        Files.writeString(directory.resolve("o.xml"), source);
        var result = new StaticProjectImporter().importProject(directory.resolve("p.jcm"));
        assertTrue(result.success(), result.diagnostics().toString());
        var plan = result.model().elements().stream().filter(e -> e.kind().equals(MetamodelKind.OPlan)).findFirst().orElseThrow();
        assertEquals(new AttributeValue.EnumLiteral("OPlanOperator", "SEQUENCE"), plan.attributes().get("operator"));
        assertEquals(1, plan.references().stream().filter(r -> r.feature().equals("subGoals") && r.targetId() != null).count());
        Files.writeString(directory.resolve("o.xml"), "<!DOCTYPE x [<!ENTITY external SYSTEM 'file:///forbidden'>]>" + source);
        assertTrue(new StaticProjectImporter().importProject(directory.resolve("p.jcm")).diagnostics().stream().anyMatch(d -> d.code().equals("MOISE_XML_INVALID")));
    }
}
