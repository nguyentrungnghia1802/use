package org.tzi.use.plugins.jacamo.trace;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tzi.use.plugins.jacamo.binding.BindingEntry;
import org.tzi.use.plugins.jacamo.binding.BindingFile;
import org.tzi.use.plugins.jacamo.binding.BindingStore;
import org.tzi.use.plugins.jacamo.extraction.StaticProjectImporter;
import org.tzi.use.plugins.jacamo.mapping.MappingLoader;
import org.tzi.use.plugins.jacamo.mapping.TransformationPlanner;
import org.tzi.use.plugins.jacamo.materialization.InstancePlanner;
import org.tzi.use.plugins.jacamo.project.SourceSpan;
import org.tzi.use.plugins.jacamo.resolution.ExactSemanticResolver;
import org.tzi.use.plugins.jacamo.resolution.ResolutionRequest;
import org.tzi.use.plugins.jacamo.resolution.ResolutionResult;
import org.tzi.use.plugins.jacamo.semantic.MetamodelKind;
import org.tzi.use.plugins.jacamo.semantic.SemanticElement;
import org.tzi.use.plugins.jacamo.semantic.SemanticId;
import org.tzi.use.plugins.jacamo.semantic.SourceProvenance;
import org.tzi.use.plugins.jacamo.verification.profile.VerificationProfileLoader;
import org.tzi.use.plugins.jacamo.verification.profile.VerificationSemanticLayer;

class TraceBindingTest {
    @TempDir Path temporary;

    @Test
    void auctionTracePersistsDeclarationsObjectsOperationsAndRuntimeIndexes() {
        var semantic = new StaticProjectImporter().importProject(Path.of("src/test/resources/auction/auction.jcm")).model();
        var mapping = new MappingLoader().loadCanonical(Path.of("."));
        var baseline = new TransformationPlanner().plan(semantic, mapping);
        var structure = new VerificationSemanticLayer().apply(baseline, new VerificationProfileLoader().loadActive(mapping)).transformation();
        var instances = new InstancePlanner().plan(semantic, mapping, structure);
        TraceIndex trace = new TraceBuilder().build(semantic, mapping, structure, instances);
        structure.classes().forEach(c -> assertFalse(trace.byUseId("class:" + c.name()).isEmpty(), c.name()));
        structure.attributes().forEach(a -> assertFalse(trace.byUseId("attribute:" + a.owner() + "." + a.name()).isEmpty(), a.toString()));
        structure.associations().forEach(a -> assertFalse(trace.byUseId("association:" + a.name()).isEmpty(), a.name()));
        structure.orderProjections().forEach(p -> {
            for (String target : List.of("class:" + p.entryClass(), "attribute:" + p.entryClass() + ".rank",
                    "association:" + p.ownerAssociation(), "association:" + p.targetAssociation(),
                    "operation:" + p.owner() + "." + p.query()))
                assertFalse(trace.byUseId(target).isEmpty(), target);
        });
        assertFalse(trace.byTargetKind("CLASS").isEmpty());
        assertFalse(trace.byTargetKind("ATTRIBUTE").isEmpty());
        assertFalse(trace.byTargetKind("ASSOCIATION").isEmpty());
        assertEquals(semantic.elements().size(), trace.byTargetKind("OBJECT").size());
        assertEquals(instances.objects().size(), trace.byTargetKind("OBJECT").size() + trace.byTargetKind("ORDER_ENTRY").size());
        assertFalse(trace.byTargetKind("OPERATION").isEmpty());
        String artifactId = instances.objects().stream().filter(o -> o.className().equals("AuctionArtifact"))
                .findFirst().orElseThrow().semanticId();
        TraceRecord artifact = trace.bySemanticId(artifactId).stream()
                .filter(record -> record.targetKind().equals("OBJECT")).findFirst().orElseThrow();
        trace.registerRuntimeKey(artifact.traceId(), "cartago:artifact:market/auction1");
        trace.registerRuntimeKey(artifact.traceId(), "cartago:artifact:remote/auction1");
        assertEquals(artifact.traceId(), trace.byRuntimeKey("cartago:artifact:market/auction1").orElseThrow().traceId());
        assertEquals(artifact.traceId(), trace.byRuntimeKey("cartago:artifact:remote/auction1").orElseThrow().traceId());
        assertEquals(artifact.traceId(), trace.byUseId(artifact.targetUseId()).stream()
                .filter(record -> record.traceId().equals(artifact.traceId())).findFirst().orElseThrow().traceId());
        Path file = temporary.resolve("trace.json");
        new TraceStore().write(file, trace);
        TraceIndex loaded = new TraceStore().read(file);
        assertEquals(trace.records(), loaded.records());
        assertTrue(loaded.byRuntimeKey("cartago:artifact:market/auction1").isEmpty(), "persisted aliases are historical evidence");
        assertThrows(IllegalArgumentException.class, () -> loaded.registerRuntimeKey(artifact.traceId(), "new-runtime"));
        var direct = new org.tzi.use.plugins.jacamo.materialization.DirectUseBackend().materialize(
                new org.tzi.use.plugins.jacamo.materialization.TextBackend().generate("traceGate", structure, instances), instances);
        assertThrows(IllegalArgumentException.class, () -> new org.tzi.use.plugins.jacamo.runtime.RuntimeMutationEngine(direct.system(), loaded));
        TraceIndex rebuilt = new TraceBuilder().build(semantic, mapping, structure, instances);
        rebuilt.copyRuntimeKeysFrom(loaded);
        assertTrue(rebuilt.runtimeKeysFor(artifactId).isEmpty());
    }

    @Test
    void resolverUsesOnlyExactOrderedStrategiesAndBindingForRealAmbiguity() {
        SemanticElement source = element(MetamodelKind.Action, List.of("MAS", "agent"), "placeBid", "1");
        SemanticElement first = element(MetamodelKind.Operation, List.of("MAS", "ws", "artifact1"), "placeBid", "2");
        SemanticElement second = element(MetamodelKind.Operation, List.of("MAS", "ws", "artifact2"), "placeBid", "3");
        ExactSemanticResolver resolver = new ExactSemanticResolver(List.of(source, first, second), BindingFile.empty());
        assertEquals(ResolutionResult.Status.AMBIGUOUS, resolver.resolve(new ResolutionRequest(source.id().value(),
                "placeBid", Set.of(MetamodelKind.Operation), null, List.of())).status());
        var ownerQualified = resolver.resolve(new ResolutionRequest(source.id().value(), "artifact1.placeBid",
                Set.of(MetamodelKind.Operation), null, List.of()));
        assertEquals(ResolutionResult.Strategy.OWNER_QUALIFIED, ownerQualified.strategy());
        assertEquals(first.id().value(), ownerQualified.target().id().value());
        var explicit = resolver.resolve(new ResolutionRequest(source.id().value(), "placeBid",
                Set.of(MetamodelKind.Operation), second.id().value(), List.of()));
        assertEquals(ResolutionResult.Strategy.EXPLICIT_REFERENCE, explicit.strategy());

        BindingEntry binding = BindingEntry.active(source.id().value(), second.id().value(), "OPERATION_BINDING",
                "two exact operations", source.provenance().getFirst().sourceHash(), "auction fixture");
        resolver = new ExactSemanticResolver(List.of(source, first, second), new BindingFile("1.0.0", List.of(binding)));
        var bound = resolver.resolve(new ResolutionRequest(source.id().value(), "placeBid",
                Set.of(MetamodelKind.Operation), null, List.of()));
        assertEquals(ResolutionResult.Strategy.EXPLICIT_BINDING, bound.strategy());
        assertEquals(second.id().value(), bound.target().id().value());
        assertEquals(ResolutionResult.Status.UNRESOLVED, resolver.resolve(new ResolutionRequest(source.id().value(),
                "missingOperation", Set.of(MetamodelKind.Operation), null, List.of())).status(),
                "binding cannot fabricate a link when the source spelling has no exact candidate");
        var invalidKind = new ExactSemanticResolver(List.of(source, first, second),
                new BindingFile("1.0.0", List.of(BindingEntry.active(source.id().value(), source.id().value(),
                        "GOAL_BINDING", "invalid target kind", source.provenance().getFirst().sourceHash(), "test"))))
                .resolve(new ResolutionRequest(source.id().value(), "placeBid", Set.of(MetamodelKind.Operation), null, List.of()));
        assertEquals(ResolutionResult.Status.INVALID_BINDING, invalidKind.status());
    }

    @Test
    void historicalV1TraceCannotAuthorizeRuntimeAndChangedContractRetiresAliases() {
        var archived = new TraceStore().read(Path.of("docs/project/v2-migration/historical-static/auction/trace.json"));
        var historicalObject = archived.byTargetKind("OBJECT").getFirst();
        assertThrows(IllegalArgumentException.class, () -> archived.registerRuntimeKey(historicalObject.traceId(), "historical"));
        var mapping = new MappingLoader().loadCanonical(Path.of("."));
        var changed = new org.tzi.use.plugins.jacamo.mapping.MappingModel(mapping.schemaVersion(), "different-mapping-contract",
                mapping.status(), mapping.classes(), mapping.attributes(), mapping.associations(), mapping.inheritance(),
                mapping.projections(), mapping.enums(), mapping.orderProjections());
        var record = new TraceRecord("trace:" + "0".repeat(24), "semantic:a", "object:a", "Agent", "OBJECT",
                "C001", null, null, null, null, TraceRecord.Status.RESOLVED);
        var old = new TraceIndex(List.of(record), mapping);
        old.registerRuntimeKey(record.traceId(), "jason:agent:a");
        var current = new TraceIndex(List.of(record), changed);
        current.copyRuntimeKeysFrom(old);
        assertTrue(current.runtimeKeysFor(record.sourceSemanticId()).isEmpty());
    }

    @Test
    void duplicateLocalSymbolsAndOrganisationInstancesRemainAmbiguousWithoutScope() {
        SemanticElement source = element(MetamodelKind.Agent, List.of("MAS"), "caller", "1");
        var goal1 = element(MetamodelKind.AGoal, List.of("MAS", "a1"), "start", "2");
        var goal2 = element(MetamodelKind.AGoal, List.of("MAS", "a2"), "start", "3");
        var org1 = element(MetamodelKind.Organization, List.of("MAS", "deployment1"), "org", "4");
        var org2 = element(MetamodelKind.Organization, List.of("MAS", "deployment2"), "org", "5");
        var resolver = new ExactSemanticResolver(List.of(source, goal1, goal2, org1, org2), BindingFile.empty());
        assertEquals(ResolutionResult.Status.AMBIGUOUS, resolver.resolve(new ResolutionRequest(source.id().value(),
                "start", Set.of(MetamodelKind.AGoal), null, List.of())).status());
        assertEquals(goal1.id().value(), resolver.resolve(new ResolutionRequest(source.id().value(), "start",
                Set.of(MetamodelKind.AGoal), null, List.of("a1"))).target().id().value());
        assertEquals(ResolutionResult.Status.AMBIGUOUS, resolver.resolve(new ResolutionRequest(source.id().value(),
                "org", Set.of(MetamodelKind.Organization), null, List.of())).status());
        assertEquals(ResolutionResult.Status.UNRESOLVED, resolver.resolve(new ResolutionRequest(source.id().value(),
                "starts", Set.of(MetamodelKind.AGoal), null, List.of())).status(), "no fuzzy matching");
    }

    @Test
    void bindingSchemaPersistsChoiceAndMarksChangedSourceStale() {
        SemanticElement source = element(MetamodelKind.Action, List.of("MAS", "a"), "act", "1");
        SemanticElement target = element(MetamodelKind.Operation, List.of("MAS", "w", "x"), "act", "2");
        BindingEntry entry = BindingEntry.active(source.id().value(), target.id().value(), "OPERATION_BINDING",
                "ambiguous exact candidates", source.provenance().getFirst().sourceHash(), "user selection");
        Path file = temporary.resolve("binding.json");
        BindingStore store = new BindingStore();
        store.write(file, new BindingFile("1.0.0", List.of(entry)));
        assertEquals(BindingEntry.Status.ACTIVE, store.read(file, Map.of(source.id().value(), "1".repeat(64), target.id().value(), "2".repeat(64)))
                .entries().getFirst().status());
        assertEquals(BindingEntry.Status.STALE, store.read(file, Map.of(source.id().value(), "1".repeat(64)))
                .entries().getFirst().status(), "removed V1 target cannot remain ACTIVE with an unchanged source hash");
        assertEquals(BindingEntry.Status.STALE, store.read(file, Map.of(source.id().value(), "9".repeat(64)))
                .entries().getFirst().status());
        SemanticElement other = element(MetamodelKind.Operation, List.of("MAS", "w", "y"), "act", "3");
        var request = new ResolutionRequest(source.id().value(), "act", Set.of(MetamodelKind.Operation), null, List.of());
        for (Map<String, String> hashes : List.of(Map.of(source.id().value(), "9".repeat(64)), Map.<String, String>of())) {
            var resolution = new ExactSemanticResolver(List.of(source, target, other), store.read(file, hashes))
                    .resolve(request);
            assertEquals(ResolutionResult.Status.AMBIGUOUS, resolution.status());
            assertNull(resolution.target(), "changed or absent source must not reuse a stale binding");
        }
    }

    private SemanticElement element(MetamodelKind kind, List<String> owner, String name, String hashDigit) {
        SourceProvenance provenance = new SourceProvenance(new SourceSpan(Path.of("fixture.txt"), 1, 1, 1, 1),
                "test", hashDigit.repeat(64), name);
        return new SemanticElement(SemanticId.of("test", kind.dimension(), kind.name(), owner, name), kind, name,
                List.of(provenance), Map.of(), List.of());
    }
}
