package org.tzi.use.plugins.jacamo.constraint;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tzi.use.plugins.jacamo.PathLinkSupport;
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
import org.tzi.use.plugins.jacamo.verification.ConstraintOrigin;
import org.tzi.use.plugins.jacamo.verification.ConstraintRegistry;
import org.tzi.use.plugins.jacamo.verification.profile.VerificationProfileLoader;
import org.tzi.use.plugins.jacamo.verification.profile.VerificationSemanticLayer;
import org.tzi.use.uml.mm.ModelFactory;
import org.tzi.use.uml.ocl.value.BooleanValue;

class ConstraintOclTest {
    @TempDir Path temporary;

    @Test
    void profileFingerprintAndContentAreIndependentOfCheckoutLineEndings() throws Exception {
        Path lf = Files.createDirectories(temporary.resolve("lf")).resolve("case.ocl");
        Path crlf = Files.createDirectories(temporary.resolve("crlf")).resolve("case.ocl");
        Files.writeString(lf, "context AuctionArtifact inv Always: true\n");
        Files.writeString(crlf, "context AuctionArtifact inv Always: true\r\n");
        OclProfileLoader loader = new OclProfileLoader();
        var canonical = loader.loadCase(lf.getParent(), Path.of("case.ocl"));
        var windowsCheckout = loader.loadCase(crlf.getParent(), Path.of("case.ocl"));
        assertEquals(canonical.sha256(), windowsCheckout.sha256());
        assertEquals(canonical.content(), windowsCheckout.content());
    }

    @Test
    void multipleContractsUnderOneAuthoredOperationContextKeepCaseProvenance() throws Exception {
        String model = """
                model Contracts
                class A
                operations
                  ping(x : Integer)
                end
                class B
                operations
                  ping(x : Integer)
                end
                constraints
                context A::ping(x : Integer)
                pre First: x > 0
                pre Second: x < 10
                post Third: true
                context B::ping(x : Integer)
                pre First: x > 0
                """;
        Path profile = temporary.resolve("case.ocl");
        Files.writeString(profile, """
                context A::ping(x : Integer)
                pre First: x > 0
                pre Second: x < 10
                post Third: true
                context B::ping(x : Integer)
                pre First: x > 0
                """);
        StringWriter errors = new StringWriter();
        var compiled = USECompiler.compileSpecification(new ByteArrayInputStream(model.getBytes(StandardCharsets.UTF_8)),
                "contracts.use", URI.create("memory:/contracts.use"), new PrintWriter(errors), new ModelFactory());
        assertNotNull(compiled, errors.toString());
        var loaded = new OclProfileLoader().loadCase(temporary, Path.of("case.ocl"));
        var registry = ConstraintRegistry.load(compiled, new OclGenerator.GeneratedOcl(model, "", List.of()),
                List.of(ConstraintRegistry.profile(ConstraintOrigin.CASE, loaded)));
        for (String name : List.of("First", "Second", "Third")) {
            var descriptor = registry.descriptors().stream().filter(value -> name.equals(value.name())
                            && "A".equals(value.context()))
                    .findFirst().orElseThrow();
            assertEquals(ConstraintOrigin.CASE, descriptor.origin(), name);
            assertEquals(profile.toAbsolutePath().normalize(), descriptor.sourcePath(), name);
        }
        assertEquals(4, registry.descriptors().stream().map(value -> value.id()).distinct().count(),
                "same-named contracts on different operation owners must have distinct IDs");
        for (var descriptor : registry.descriptors())
            assertEquals(descriptor, registry.byId(descriptor.id()), "ID must identify exactly this descriptor");
    }

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
                new VerificationProfileLoader().loadActive(mapping)).transformation();
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
        assertTrue(generated.useModel().contains("pre Guard_canBid: (amount >= 0)"));
        assertTrue(generated.useModel().contains("pre AuctionOpenForBid:"));
        assertTrue(generated.useModel().contains("self.open = true"));
        assertTrue(generated.useModel().contains("pre PositiveBidAmount:"));
        assertTrue(generated.useModel().contains("amount > 0"));
        assertTrue(generated.useModel().contains("post OpenUnchanged: (self.open = self.open@pre)"));
        assertTrue(generated.provenanceManifest().contains("CARTAGO_GUARD|EXACT"));
        assertFalse(generated.provenanceManifest().contains("\\"),
                "generated provenance paths must be portable across operating systems");
        assertTrue(generated.provenanceManifest().contains(
                "PROFILE|/org/tzi/use/plugins/jacamo/ocl/jacamo-core-v2.ocl|"));
        assertEquals(64, loader.loadCase(project, Path.of("verification/auction.ocl")).sha256().length());
        assertEquals("1393b98d7e4df6afe615792bca7be904317820099b5f288281dd0d991532ba86",
                loader.loadCore().sha256(), "core profile fingerprint must use canonical LF content");

        StringWriter errors = new StringWriter();
        assertNotNull(USECompiler.compileSpecification(new ByteArrayInputStream(generated.useModel().getBytes(StandardCharsets.UTF_8)),
                "phase6.use", URI.create("memory:/phase6.use"), new PrintWriter(errors), new ModelFactory()), errors.toString());
        var instances = new InstancePlanner().plan(semantic, mapping, structure);
        var commands = new TextBackend().generate("auction", structure, instances).initialCommands();
        DirectUseBackend.Result direct = new DirectUseBackend().materialize(
                new TextBackend.GeneratedArtifacts(generated.useModel(), commands), instances);
        var registry = ConstraintRegistry.load(direct.system().model(), generated,
                List.of(ConstraintRegistry.profile(ConstraintOrigin.CORE, loader.loadCore()),
                        ConstraintRegistry.profile(ConstraintOrigin.CASE,
                                loader.loadCase(project, Path.of("verification/auction.ocl")))));
        var openPrecondition = registry.descriptors().stream()
                .filter(descriptor -> descriptor.name().equals("AuctionOpenForBid"))
                .findFirst().orElseThrow();
        assertEquals(ConstraintOrigin.CASE, openPrecondition.origin());
        assertEquals(project.resolve("verification/auction.ocl").toAbsolutePath().normalize(),
                openPrecondition.sourcePath());
        assertFalse(openPrecondition.id().contains("\\"), "constraint IDs must use portable path separators");
        assertTrue(registry.fingerprints().keySet().stream().noneMatch(key -> key.contains("\\")),
                "fingerprint keys must use portable path separators");
        var initialOpen = registry.descriptors().stream()
                .filter(descriptor -> descriptor.name().equals("AuctionInitiallyOpen"))
                .findFirst().orElseThrow();
        assertEquals("self.open = true", initialOpen.oclSource(),
                "a constraint must not absorb the comment belonging to the next context");
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

    @Test
    void caseLoaderRejectsSymlinkThatResolvesOutsideProject() throws Exception {
        Path project = Files.createDirectory(temporary.resolve("project"));
        Path outside = Files.createDirectory(temporary.resolve("outside"));
        Files.writeString(outside.resolve("outside.ocl"), "context X inv ok: true");
        Path link = PathLinkSupport.createDirectoryLink(project.resolve("linked"), outside);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new OclProfileLoader().loadCase(project, link.getFileName().resolve("outside.ocl")));
        assertTrue(error.getMessage().contains("OCL_PROFILE_PATH_ESCAPE"));
        assertTrue(error.getMessage().contains("choose a profile within the allowed root"));
    }
}
