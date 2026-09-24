package org.tzi.use.plugins.jacamo.verification;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tzi.use.plugins.jacamo.constraint.ConstraintExtractor;
import org.tzi.use.plugins.jacamo.constraint.ConstraintSpec;
import org.tzi.use.plugins.jacamo.constraint.Expression;
import org.tzi.use.plugins.jacamo.extraction.StaticProjectImporter;
import org.tzi.use.plugins.jacamo.mapping.MappingLoader;
import org.tzi.use.plugins.jacamo.mapping.TransformationPlanner;
import org.tzi.use.plugins.jacamo.materialization.DirectUseBackend;
import org.tzi.use.plugins.jacamo.materialization.InstancePlan;
import org.tzi.use.plugins.jacamo.materialization.InstancePlanner;
import org.tzi.use.plugins.jacamo.materialization.TextBackend;
import org.tzi.use.plugins.jacamo.ocl.OclGenerator;
import org.tzi.use.plugins.jacamo.ocl.OclProfileLoader;
import org.tzi.use.plugins.jacamo.semantic.MetamodelKind;
import org.tzi.use.plugins.jacamo.trace.TraceBuilder;
import org.tzi.use.plugins.jacamo.trace.TraceIndex;
import org.tzi.use.plugins.jacamo.verification.profile.VerificationProfileLoader;
import org.tzi.use.plugins.jacamo.verification.profile.VerificationSemanticLayer;
import org.tzi.use.uml.ocl.value.BooleanValue;
import org.tzi.use.uml.ocl.value.IntegerValue;
import org.tzi.use.uml.ocl.value.StringValue;

class OfflineVerificationServiceTest {
    @TempDir Path temporary;

    @Test
    void registryPreservesTranslatedCoreCaseAndUserOriginsWithDependencies() {
        Fixture fixture = fixture();
        Path user = temporary.resolve("user.ocl");
        assertDoesNotThrow(() -> java.nio.file.Files.writeString(user,
                "context AuctionArtifact inv UserOpen:\n  self.open = true\n"));
        OclProfileLoader loader = new OclProfileLoader();
        var userProfile = loader.loadUser(temporary, Path.of("user.ocl"));
        var generated = new OclGenerator().generate("auction", fixture.structure(), fixture.constraints(),
                List.of(loader.loadCore(), loader.loadCase(fixture.project(), Path.of("verification/auction.ocl")), userProfile));
        DirectUseBackend.Result direct = materialize(fixture, generated);

        ConstraintRegistry registry = ConstraintRegistry.load(direct.system().model(), generated, List.of(
                ConstraintRegistry.profile(ConstraintOrigin.CORE, loader.loadCore()),
                ConstraintRegistry.profile(ConstraintOrigin.CASE,
                        loader.loadCase(fixture.project(), Path.of("verification/auction.ocl"))),
                ConstraintRegistry.profile(ConstraintOrigin.USER, userProfile)));

        assertTrue(registry.descriptors().stream().anyMatch(d -> d.origin() == ConstraintOrigin.TRANSLATED
                && !d.dependencies().isEmpty()));
        assertTrue(registry.descriptors().stream().anyMatch(d -> d.origin() == ConstraintOrigin.CORE));
        assertTrue(registry.descriptors().stream().anyMatch(d -> d.origin() == ConstraintOrigin.CASE
                && d.name().equals("AuctionInitiallyOpen")));
        assertTrue(registry.descriptors().stream().anyMatch(d -> d.origin() == ConstraintOrigin.USER
                && d.name().equals("UserOpen") && d.sourceSpan().startLine() == 1));
        fixture.structure().orderProjections().forEach(order -> {
            var descriptor = registry.descriptors().stream().filter(d -> d.context().equals(order.owner())
                    && d.name().equals("order_" + order.ruleId())).findFirst().orElseThrow();
            assertEquals(ConstraintOrigin.CORE, descriptor.origin());
            assertEquals("CORE:ORDER:" + order.sourceIdentity(), descriptor.id());
            assertTrue(descriptor.dependencies().isEmpty(), "mapping provenance is not proof of complete runtime dependencies");
            assertTrue(descriptor.sourcePath().toString().endsWith("jacamo-use-mapping-v2.json"));
        });
        var withoutProof = ConstraintRegistry.load(direct.system().model(),
                new OclGenerator.GeneratedOcl(generated.useModel(), generated.provenanceManifest(), generated.emitted()), List.of());
        assertTrue(withoutProof.descriptors().stream().filter(d -> d.name().startsWith("order_"))
                .allMatch(d -> d.origin() == ConstraintOrigin.USER), "a matching name alone cannot claim generated CORE provenance");
    }

    @Test
    void fullCheckReportsStructureAndTracedAuctionViolationWithoutCollapsingErrors() {
        Fixture fixture = fixture();
        OclProfileLoader loader = new OclProfileLoader();
        var generated = new OclGenerator().generate("auction", fixture.structure(), fixture.constraints(),
                List.of(loader.loadCore(), loader.loadCase(fixture.project(), Path.of("verification/auction.ocl"))));
        DirectUseBackend.Result direct = materialize(fixture, generated);
        ConstraintRegistry registry = ConstraintRegistry.load(direct.system().model(), generated, List.of(
                ConstraintRegistry.profile(ConstraintOrigin.CORE, loader.loadCore()),
                ConstraintRegistry.profile(ConstraintOrigin.CASE,
                        loader.loadCase(fixture.project(), Path.of("verification/auction.ocl")))));
        TraceIndex trace = new TraceBuilder().build(fixture.semantic(), fixture.mapping(), fixture.structure(), fixture.instances());
        VerificationService service = new DefaultVerificationService();

        VerificationReport positive = service.runFullVerification(direct.system(), registry, trace);
        assertTrue(positive.structureValid());
        assertEquals(64, positive.fingerprints().get("useModelSha256").length());
        assertTrue(positive.results().stream().noneMatch(r -> r.outcome() == VerificationOutcome.FAIL
                || r.outcome() == VerificationOutcome.ERROR));

        var artifact = direct.system().state().allObjects().stream()
                .filter(object -> object.cls().name().equals("AuctionArtifact")).findFirst().orElseThrow();
        artifact.state(direct.system().state()).setAttributeValue(
                artifact.cls().attribute("open", true), BooleanValue.FALSE);
        VerificationReport negative = service.runFullVerification(direct.system(), registry, trace);
        VerificationResult failure = negative.results().stream()
                .filter(result -> result.constraintId().contains("AuctionInitiallyOpen")).findFirst().orElseThrow();
        assertEquals(VerificationOutcome.FAIL, failure.outcome());
        assertEquals(artifact.name(), failure.contextObject());
        assertTrue(failure.sourceTrace().stream().anyMatch(id -> id.contains(":Artifact:")));
        assertTrue(failure.oclSource().contains("self.open"));

        var invariant = direct.system().model().classInvariants().stream()
                .filter(candidate -> candidate.name().equals("AuctionInitiallyOpen")).findFirst().orElseThrow();
        invariant.setActive(false);
        assertEquals(VerificationOutcome.SKIPPED, service.runFullVerification(direct.system(), registry, trace).results()
                .stream().filter(result -> result.constraintId().contains("AuctionInitiallyOpen"))
                .findFirst().orElseThrow().outcome());

        Path undefinedFile = temporary.resolve("undefined.ocl");
        assertDoesNotThrow(() -> java.nio.file.Files.writeString(undefinedFile,
                "context AuctionArtifact inv UndefinedRule:\n  oclUndefined(Boolean)\n"));
        var undefinedProfile = loader.loadUser(temporary, Path.of("undefined.ocl"));
        var undefinedGenerated = new OclGenerator().generate("auction", fixture.structure(), fixture.constraints(),
                List.of(loader.loadCore(), undefinedProfile));
        DirectUseBackend.Result undefinedSystem = materialize(fixture, undefinedGenerated);
        ConstraintRegistry undefinedRegistry = ConstraintRegistry.load(undefinedSystem.system().model(), undefinedGenerated,
                List.of(ConstraintRegistry.profile(ConstraintOrigin.CORE, loader.loadCore()),
                        ConstraintRegistry.profile(ConstraintOrigin.USER, undefinedProfile)));
        assertEquals(VerificationOutcome.ERROR,
                service.runFullVerification(undefinedSystem.system(), undefinedRegistry, trace).results()
                .stream().filter(result -> result.constraintId().contains("UndefinedRule"))
                .findFirst().orElseThrow().outcome());
    }

    @Test
    void operationCheckKeepsCorrelationAndUsesPreStateForPreAndPostconditions() {
        Fixture fixture = fixture();
        var operationEvidence = fixture.semantic().elements().stream()
                .filter(element -> element.kind() == MetamodelKind.Operation).findFirst().orElseThrow();
        Expression self = new Expression.VariableRef("self", Expression.ValueType.OBJECT);
        ConstraintSpec post = new ConstraintExtractor().explicitPostcondition("POST-AUCTION-OPEN", "AuctionArtifact",
                "placeBid", "OpenUnchanged", new Expression.BinaryOp(
                new Expression.PropertyRef(self, "open", Expression.ValueType.BOOLEAN, false), "=",
                new Expression.PropertyRef(self, "open", Expression.ValueType.BOOLEAN, true),
                Expression.ValueType.BOOLEAN), operationEvidence, List.of("explicit fixture"),
                List.of(operationEvidence.id().value()));
        var constraints = new java.util.ArrayList<>(fixture.constraints());
        constraints.add(post);
        OclProfileLoader loader = new OclProfileLoader();
        var generated = new OclGenerator().generate("auction", fixture.structure(), constraints, List.of(loader.loadCore()));
        DirectUseBackend.Result direct = materialize(fixture, generated);
        ConstraintRegistry registry = ConstraintRegistry.load(direct.system().model(), generated,
                List.of(ConstraintRegistry.profile(ConstraintOrigin.CORE, loader.loadCore())));
        TraceIndex trace = new TraceBuilder().build(fixture.semantic(), fixture.mapping(), fixture.structure(), fixture.instances());
        VerificationService service = new DefaultVerificationService();
        String artifact = direct.system().state().allObjects().stream()
                .filter(object -> object.cls().name().equals("AuctionArtifact")).findFirst().orElseThrow().name();

        OperationCheck check = service.beginOperation(direct.system(), registry, trace,
                new OperationRequest(artifact, "placeBid", List.of(new StringValue("item1"), IntegerValue.valueOf(10)),
                        "bid-42", List.of("event-enter")));
        assertTrue(check.preconditions().results().stream().allMatch(r -> r.outcome() == VerificationOutcome.PASS));
        var object = direct.system().state().objectByName(artifact);
        object.state(direct.system().state()).setAttributeValue(object.cls().attribute("open", true), BooleanValue.FALSE);
        VerificationReport postconditions = service.completeOperation(check, direct.system().state(), null,
                List.of("event-exit"));
        VerificationResult postFailure = postconditions.results().stream()
                .filter(result -> result.constraintId().equals("POST-AUCTION-OPEN")).findFirst().orElseThrow();
        assertEquals(VerificationOutcome.FAIL, postFailure.outcome());
        assertEquals("bid-42", postFailure.correlationId());
        assertEquals(List.of("event-enter", "event-exit"), postFailure.runtimeEventIds());

        OperationCheck invalid = service.beginOperation(direct.system(), registry, trace,
                new OperationRequest(artifact, "placeBid", List.of(new StringValue("item1"), IntegerValue.valueOf(-1)),
                        "bid-43", List.of()));
        assertTrue(invalid.preconditions().results().stream().anyMatch(r -> r.outcome() == VerificationOutcome.FAIL));
    }

    @Test
    void exportersEmitMachineReadableAndHumanReadableOutcomeDetails() {
        VerificationResult result = new VerificationResult("constraint-1", VerificationOutcome.FAIL, "auction1",
                "violated", "self.open = true", List.of("jacamo:test:environment:Artifact:MAS/ws:auction1"),
                "bid-1", List.of("event-1"));
        VerificationReport report = VerificationReport.offline("run-1", false, List.of(result),
                Map.of("useModelSha256", "a".repeat(64)));
        VerificationReportExporter exporter = new VerificationReportExporter();

        String json = exporter.toJson(report);
        String markdown = exporter.toMarkdown(report);
        assertTrue(json.contains("\"outcome\" : \"FAIL\""));
        assertTrue(json.contains("\"correlationId\" : \"bid-1\""));
        assertTrue(json.contains("\"useModelSha256\" : \"" + "a".repeat(64) + "\""));
        assertTrue(markdown.contains("| constraint-1 | FAIL | auction1 |"));
        assertTrue(markdown.contains("jacamo:test:environment:Artifact:MAS/ws:auction1"));
    }

    private Fixture fixture() {
        Path project = Path.of("src/test/resources/auction");
        var semantic = new StaticProjectImporter().importProject(project.resolve("auction.jcm")).model();
        var mapping = new MappingLoader().loadCanonical(Path.of("."));
        var baseline = new TransformationPlanner().plan(semantic, mapping);
        var structure = new VerificationSemanticLayer().apply(baseline,
                new VerificationProfileLoader().loadActive(mapping)).transformation();
        var constraints = new ConstraintExtractor().extract(semantic, structure, Map.of());
        var instances = new InstancePlanner().plan(semantic, mapping, structure);
        return new Fixture(project, semantic, mapping, structure, constraints, instances);
    }

    private DirectUseBackend.Result materialize(Fixture fixture, OclGenerator.GeneratedOcl generated) {
        var commands = new TextBackend().generate("auction", fixture.structure(), fixture.instances()).initialCommands();
        return new DirectUseBackend().materialize(new TextBackend.GeneratedArtifacts(generated.useModel(), commands),
                fixture.instances());
    }

    private record Fixture(Path project,
                           org.tzi.use.plugins.jacamo.semantic.JaCaMoSemanticModel semantic,
                           org.tzi.use.plugins.jacamo.mapping.MappingModel mapping,
                           org.tzi.use.plugins.jacamo.mapping.TransformationPlan structure,
                           List<ConstraintSpec> constraints,
                           InstancePlan instances) { }
}
