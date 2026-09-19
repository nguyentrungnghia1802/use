package org.tzi.use.plugins.jacamo.constraint;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.tzi.use.parser.use.USECompiler;
import org.tzi.use.plugins.jacamo.extraction.StaticProjectImporter;
import org.tzi.use.plugins.jacamo.mapping.MappingLoader;
import org.tzi.use.plugins.jacamo.mapping.TargetClassSpec;
import org.tzi.use.plugins.jacamo.mapping.TargetOperationSpec;
import org.tzi.use.plugins.jacamo.mapping.TransformationPlan;
import org.tzi.use.plugins.jacamo.mapping.TransformationPlanner;
import org.tzi.use.plugins.jacamo.materialization.DirectUseBackend;
import org.tzi.use.plugins.jacamo.materialization.InstancePlanner;
import org.tzi.use.plugins.jacamo.materialization.TextBackend;
import org.tzi.use.plugins.jacamo.ocl.OclGenerator;
import org.tzi.use.plugins.jacamo.ocl.OclProfileLoader;
import org.tzi.use.plugins.jacamo.semantic.MetamodelKind;
import org.tzi.use.plugins.jacamo.verification.profile.VerificationProfileLoader;
import org.tzi.use.plugins.jacamo.verification.profile.VerificationSemanticLayer;
import org.tzi.use.uml.mm.ModelFactory;
import org.tzi.use.uml.ocl.value.BooleanValue;

class ConstraintOclTest {
    @Test
    void operationContractIsInsertedOnlyInItsBoundOwner() {
        var operation = new TargetOperationSpec("A", "ping", List.of(), null, "a", "VP003");
        var other = new TargetOperationSpec("B", "ping", List.of(), null, "b", "VP003");
        var plan = new TransformationPlan(List.of(
                new TargetClassSpec("A", false, List.of(), "a", "C1"),
                new TargetClassSpec("B", false, List.of(), "b", "C2")), List.of(), List.of(),
                List.of(operation, other), List.of());
        var semantic = new StaticProjectImporter().importProject(
                Path.of("src/test/resources/auction/auction.jcm")).model();
        var provenance = semantic.elements().getFirst().provenance().getFirst();
        var constraint = new ConstraintSpec("A-PRE", ConstraintSpec.SourceKind.EXPLICIT_CONTRACT,
                ConstraintSpec.Kind.PRE, "A", "ping", "OnlyA",
                new Expression.Literal("true", Expression.ValueType.BOOLEAN), Expression.ValueType.BOOLEAN,
                TranslationStatus.EXACT, provenance, List.of(), List.of());
        String model = new OclGenerator().generate("owners", plan, List.of(constraint), List.of()).useModel();
        assertEquals(1, model.split("pre OnlyA", -1).length - 1);
        assertFalse(model.substring(model.indexOf("class B")).contains("pre OnlyA"));
    }

    @Test
    void expressionSubsetCoversLogicalComparisonArithmeticPropertyCollectionAndUnsupportedCall() {
        var parser = new ConstraintExpressionParser();
        var environment = new TypeEnvironment(Map.of("amount", Expression.ValueType.INTEGER),
                Map.of("open", new TypeEnvironment.PropertyBinding("self.open", Expression.ValueType.BOOLEAN)));
        var exact = parser.parse("open & amount + 1 >= 2", environment);
        assertEquals(TranslationStatus.EXACT, exact.status());
        assertEquals("(self.open and ((amount + 1) >= 2))", new OclGenerator().render(exact.expression()));
        assertEquals(TranslationStatus.UNSUPPORTED, parser.parse("budget(B)", environment).status());
        Expression collection = new Expression.CollectionPredicate(new Expression.VariableRef("items",
                Expression.ValueType.COLLECTION), "forAll", "i", new Expression.BinaryOp(
                new Expression.VariableRef("i", Expression.ValueType.INTEGER), ">",
                new Expression.Literal("0", Expression.ValueType.INTEGER), Expression.ValueType.BOOLEAN),
                Expression.ValueType.BOOLEAN);
        assertEquals("items->forAll(i | (i > 0))", new OclGenerator().render(collection));
    }

    @Test
    void auctionGuardCoreCaseAndExplicitPostconditionCompileAndEvaluatePositiveNegative() throws Exception {
        Path project = Path.of("src/test/resources/auction");
        var semantic = new StaticProjectImporter().importProject(project.resolve("auction.jcm")).model();
        var mapping = new MappingLoader().loadCanonical(Path.of("."));
        var baseline = new TransformationPlanner().plan(semantic, mapping);
        var structure = new VerificationSemanticLayer().apply(baseline,
                new VerificationProfileLoader().loadV1()).transformation();
        List<ConstraintSpec> extracted = new ConstraintExtractor().extract(semantic, structure, Map.of());
        ConstraintSpec guard = extracted.stream().filter(c -> c.sourceKind() == ConstraintSpec.SourceKind.CARTAGO_GUARD)
                .findFirst().orElseThrow();
        assertEquals(TranslationStatus.EXACT, guard.status());
        assertEquals("AuctionArtifact", guard.contextClass());
        assertEquals("placeBid", guard.operationName());
        assertTrue(extracted.stream().anyMatch(c -> c.sourceKind() == ConstraintSpec.SourceKind.JASON_CONTEXT
                && c.status() == TranslationStatus.UNSUPPORTED));
        assertTrue(extracted.stream().noneMatch(c -> c.dependencies().stream().anyMatch(id -> id.contains(":Norm:"))),
                "Moise Norm is preserved structurally and never collapsed into OCL");

        var operationEvidence = semantic.elements().stream().filter(e -> e.kind() == MetamodelKind.Operation).findFirst().orElseThrow();
        Expression self = new Expression.VariableRef("self", Expression.ValueType.OBJECT);
        ConstraintSpec post = new ConstraintExtractor().explicitPostcondition("POST-AUCTION-OPEN", "AuctionArtifact",
                "placeBid", "OpenUnchanged", new Expression.BinaryOp(
                new Expression.PropertyRef(self, "open", Expression.ValueType.BOOLEAN, false), "=",
                new Expression.PropertyRef(self, "open", Expression.ValueType.BOOLEAN, true),
                Expression.ValueType.BOOLEAN), operationEvidence,
                List.of("[OUR-EXT] explicit fixture contract"), List.of(operationEvidence.id().value()));
        List<ConstraintSpec> constraints = new ArrayList<>(extracted); constraints.add(post);
        OclProfileLoader loader = new OclProfileLoader();
        var generated = new OclGenerator().generate("auction", structure, constraints,
                List.of(loader.loadCore(), loader.loadCase(project, Path.of("verification/auction.ocl"))));
        assertTrue(generated.useModel().contains("pre Guard_canBid: (amount > 0)"));
        assertTrue(generated.useModel().contains("post OpenUnchanged: (self.open = self.open@pre)"));
        assertTrue(generated.provenanceManifest().contains("CARTAGO_GUARD|EXACT"));
        assertEquals(64, loader.loadCase(project, Path.of("verification/auction.ocl")).sha256().length());

        StringWriter errors = new StringWriter();
        assertNotNull(USECompiler.compileSpecification(new ByteArrayInputStream(generated.useModel().getBytes(StandardCharsets.UTF_8)),
                "phase6.use", URI.create("memory:/phase6.use"), new PrintWriter(errors), new ModelFactory()), errors.toString());
        var instances = new InstancePlanner().plan(semantic, mapping, structure);
        var commands = new TextBackend().generate("auction", structure, instances).initialCommands();
        DirectUseBackend.Result direct = new DirectUseBackend().materialize(
                new TextBackend.GeneratedArtifacts(generated.useModel(), commands), instances);
        assertTrue(direct.invariantsValid(), direct.validationOutput());
        var artifact = direct.system().state().allObjects().stream()
                .filter(object -> object.cls().name().equals("AuctionArtifact")).findFirst().orElseThrow();
        artifact.state(direct.system().state()).setAttributeValue(artifact.cls().attribute("open", true), BooleanValue.get(false));
        StringWriter negative = new StringWriter();
        assertFalse(direct.system().state().check(new PrintWriter(negative), false, true, true, List.of()));
        assertTrue(negative.toString().contains("AuctionInitiallyOpen"));
    }

    @Test
    void caseLoaderRejectsPathEscape() {
        assertThrows(IllegalArgumentException.class, () -> new OclProfileLoader().loadCase(
                Path.of("src/test/resources/auction"), Path.of("../outside.ocl")));
    }
}
