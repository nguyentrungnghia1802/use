package org.tzi.use.plugins.jacamo.semantics.mapping.compact;

import org.junit.jupiter.api.Test;
import org.jacamo.bridge.adapter.OfficialProjectAdapter;
import org.jacamo.bridge.adapter.OfficialProjectLoader;
import org.jacamo.bridge.contract.ModelSnapshot;
import org.tzi.use.plugins.jacamo.codegrounded.CodeGroundedNativePipeline;
import org.tzi.use.plugins.jacamo.codegrounded.use.NativeProjectionMode;
import org.tzi.use.plugins.jacamo.semantics.mapping.xml.GenericMappingEngine;
import org.tzi.use.plugins.jacamo.semantics.mapping.xml.MappingInputs;
import org.tzi.use.semantic.engine.frontend.CslFrontend;
import org.tzi.use.semantic.engine.frontend.IStarFrontend;
import org.vnu.sme.goal.dsl.csl.mm.CslModel;
import org.vnu.sme.goal.dsl.istar.mm.GoalModel;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CaseExamplesMigrationAndGenericityTest {

    private static final Path EXAMPLES_DIR = Path.of("examples");

    @Test
    void verifiesAllMigratedExamplesHaveNoProceduralPipelines() throws IOException {
        List<Path> compactFiles = List.of(
                EXAMPLES_DIR.resolve("auction/auction-compact-mapping.xml"),
                EXAMPLES_DIR.resolve("hello-world/hello-compact-mapping.xml"),
                EXAMPLES_DIR.resolve("house-building/house-compact-mapping.xml"),
                EXAMPLES_DIR.resolve("auction-false/auction-compact-mapping.xml")
        );

        for (Path file : compactFiles) {
            assertTrue(Files.exists(file), "Migrated compact mapping must exist: " + file);
            String content = Files.readString(file);
            assertFalse(content.contains("<table"), "Must not contain procedural <table> in " + file);
            assertFalse(content.contains("<join"), "Must not contain procedural <join> in " + file);
            assertFalse(content.contains("<where"), "Must not contain procedural <where> in " + file);
            assertFalse(content.contains("<derive"), "Must not contain procedural <derive> in " + file);
            assertFalse(content.contains("<select"), "Must not contain procedural <select> in " + file);

            CompactMappingSpecification spec = CompactMappingReader.read(file);
            assertNotNull(spec);
            assertEquals("3.0", spec.version());
            assertFalse(spec.cslMappings().isEmpty(), "CSL mappings must be present in " + file);
            assertFalse(spec.istarMappings().isEmpty(), "iStar mappings must be present in " + file);
        }
    }

    @Test
    void compilesAuctionCaseModelsAndCompactPlanWithoutCaseBranching() throws Exception {
        Path cslPath = EXAMPLES_DIR.resolve("auction/auction.csl");
        Path istarPath = EXAMPLES_DIR.resolve("auction/auction.istar");
        Path xmlPath = EXAMPLES_DIR.resolve("auction/auction-compact-mapping.xml");

        CslModel cslModel = new CslFrontend().compile(Files.readString(cslPath), "auction.csl").requireModel();
        GoalModel goalModel = new IStarFrontend().compile(Files.readString(istarPath), "auction.istar", org.tzi.use.semantic.engine.api.EngineSemanticsProfile.ROLE_SPECIALIZATION_V1).requireModel();
        NativeTargetCatalog targetCatalog = NativeTargetCatalog.fromModels(cslModel, goalModel);

        SourceSymbolCatalog sourceCatalog = SourceSymbolCatalog.fromSnapshot(official(EXAMPLES_DIR.resolve("auction/auction.jcm")).semanticContract());

        CompactMappingSpecification spec = CompactMappingReader.read(xmlPath);
        CompiledObservationPlan plan = ObservationPlanCompiler.compile(spec, sourceCatalog, targetCatalog);

        assertNotNull(plan);
        assertEquals(13, plan.cslBindings().size());
        var field=plan.capabilityRequests().stream().filter(r->r.provider().equals("cartago.artifact-field.v1")).findFirst().orElseThrow();
        assertEquals("JASON_ATOM",org.jacamo.bridge.contract.CanonicalJson.object(field.parameters().get("properties")).get("winner"));
        assertEquals(14, plan.istarBindings().size());
        assertEquals(4, plan.actorBindings().size());
        assertTrue(plan.capabilityDemands().stream().anyMatch(d -> d.id().equals("cartago.artifact-field.v1")));
        assertTrue(plan.capabilityDemands().stream().anyMatch(d -> d.id().equals("jason.plan-boundaries.v1")));
        assertTrue(plan.capabilityDemands().stream().anyMatch(d -> d.id().equals("moise.scheme-lifecycle.v1")));
    }

    @Test
    void compilesHelloWorldCaseModelsAndCompactPlanGenerically() throws Exception {
        Path cslPath = EXAMPLES_DIR.resolve("hello-world/hello.csl");
        Path istarPath = EXAMPLES_DIR.resolve("hello-world/hello.istar");
        Path xmlPath = EXAMPLES_DIR.resolve("hello-world/hello-compact-mapping.xml");

        CslModel cslModel = new CslFrontend().compile(Files.readString(cslPath), "hello.csl").requireModel();
        GoalModel goalModel = new IStarFrontend().compile(Files.readString(istarPath), "hello.istar", org.tzi.use.semantic.engine.api.EngineSemanticsProfile.ROLE_SPECIALIZATION_V1).requireModel();
        NativeTargetCatalog targetCatalog = NativeTargetCatalog.fromModels(cslModel, goalModel);

        SourceSymbolCatalog sourceCatalog = SourceSymbolCatalog.fromSnapshot(official(EXAMPLES_DIR.resolve("hello-world/helloworld.jcm")).semanticContract());

        CompactMappingSpecification spec = CompactMappingReader.read(xmlPath);
        CompiledObservationPlan plan = ObservationPlanCompiler.compile(spec, sourceCatalog, targetCatalog);

        assertNotNull(plan);
        assertEquals(6, plan.cslBindings().size());
        assertFalse(plan.checkpointRules().isEmpty());
        assertEquals(1, plan.istarBindings().size());
        assertEquals(1, plan.actorBindings().size());
        assertFalse(plan.fingerprint().isBlank());
    }

    @Test
    void compilesHouseBuildingCaseModelsAndCompactPlanGenerically() throws Exception {
        Path cslPath = EXAMPLES_DIR.resolve("house-building/house.csl");
        Path istarPath = EXAMPLES_DIR.resolve("house-building/house.istar");
        Path xmlPath = EXAMPLES_DIR.resolve("house-building/house-compact-mapping.xml");

        CslModel cslModel = new CslFrontend().compile(Files.readString(cslPath), "house.csl").requireModel();
        GoalModel goalModel = new IStarFrontend().compile(Files.readString(istarPath), "house.istar", org.tzi.use.semantic.engine.api.EngineSemanticsProfile.ROLE_SPECIALIZATION_V1).requireModel();
        NativeTargetCatalog targetCatalog = NativeTargetCatalog.fromModels(cslModel, goalModel);

        SourceSymbolCatalog sourceCatalog = SourceSymbolCatalog.fromSnapshot(official(EXAMPLES_DIR.resolve("house-building/house-building.jcm")).semanticContract());

        CompactMappingSpecification spec = CompactMappingReader.read(xmlPath);
        CompiledObservationPlan plan = ObservationPlanCompiler.compile(spec, sourceCatalog, targetCatalog);

        assertNotNull(plan);
        assertEquals(9, plan.cslBindings().size());
        assertEquals(1, plan.istarBindings().size());
        assertEquals(1, plan.actorBindings().size());
        assertFalse(plan.fingerprint().isBlank());
    }

    @Test
    void provesZeroRuntimeDependenceOnLegacyMappingXml() throws IOException {
        // Poison test: In an isolated environment without any v2 XML files accessible,
        // compact mapping compilation and validation execute purely from native models and compact v3 specification.
        String cslText = "csl v4.0 StandaloneModel { entity TestEntity { attribute val : Integer; } role actorRole; }";
        String istarText = "istar StandaloneGoals { role actorRole { goal g1 : Achieve } }";
        String compactXml = """
                <mapping id="poison-proof" xmlns="urn:use:jacamo-mapping:3" version="3.0">
                  <csl>
                    <map source="artifact:standalone.TestArtifact" target="TestEntity"/>
                    <map source="artifact:standalone.TestArtifact/property:val" target="TestEntity.val"/>
                  </csl>
                  <istar>
                    <map source="agent:actorRole" target="actorRole"/>
                    <map source="asl:src/test.asl/goal:g1" target="actorRole.g1"/>
                  </istar>
                </mapping>
                """;

        CslModel cslModel = new CslFrontend().compile(cslText, "Standalone.csl").requireModel();
        GoalModel goalModel = new IStarFrontend().compile(istarText, "Standalone.istar").requireModel();
        NativeTargetCatalog targetCatalog = NativeTargetCatalog.fromModels(cslModel, goalModel);

        SourceSymbolCatalog sourceCatalog = new SourceSymbolCatalog();
        sourceCatalog.register(SourceSymbol.artifact("standalone.TestArtifact", "art-hash"));
        sourceCatalog.register(SourceSymbol.property("standalone.TestArtifact", "val"));
        sourceCatalog.register(SourceSymbol.agent("actorRole"));
        sourceCatalog.registerPlan("src/test.asl", "+!g1", "g1", 1);

        CompactMappingSpecification spec = CompactMappingReader.read(compactXml, "poison-test.xml");
        CompiledObservationPlan plan = ObservationPlanCompiler.compile(spec, sourceCatalog, targetCatalog);

        assertNotNull(plan);
        assertEquals("poison-proof", plan.specification().id());
        assertEquals(2, plan.cslBindings().size());
        assertEquals(2, plan.istarBindings().size());
    }

    private static ModelSnapshot official(Path jcm) throws Exception {
        return new OfficialProjectAdapter().adapt(new OfficialProjectLoader().load(jcm), jcm);
    }

    /** The workbench encodes this exact facade map while refreshing after Import. */
    private static void assertProgramEvidenceIsCanonicalJson(CodeGroundedNativePipeline.CanonicalBuildResult built) throws Exception {
        var method=org.tzi.use.plugins.jacamo.DefaultJaCaMoFacade.class.getDeclaredMethod("programEvidence",CodeGroundedNativePipeline.CanonicalBuildResult.class,String.class);
        method.setAccessible(true);
        var evidence=org.jacamo.bridge.contract.CanonicalJson.object(method.invoke(null,built,""));
        byte[] json=assertDoesNotThrow(()->org.jacamo.bridge.contract.CanonicalJson.encode(evidence));
        var restored=org.jacamo.bridge.contract.CanonicalJson.object(org.jacamo.bridge.contract.CanonicalJson.decode(json));
        var expected=built.program().compactPlan().capabilityDemands().stream().map(org.jacamo.bridge.contract.ObservationCapabilityRequest::evidence).toList();
        assertFalse(expected.isEmpty());
        assertEquals(expected,restored.get("compactCapabilityDemands"),"All request identities, provider selections, flags and parameters must survive JSON encoding");
        assertEquals(built.program().hash(),restored.get("programHash"));
    }

    @Test
    void buildsCanonicalProgramForAuctionCompact() throws Exception {
        Path root = EXAMPLES_DIR.resolve("auction");
        var source = official(root.resolve("auction.jcm"));
        var inputs = MappingInputs.read(root.resolve("auction.jcm"), root.resolve("auction-compact-mapping.xml"), root.resolve("auction.ocl"), root.resolve("auction.csl"), root.resolve("auction.istar"), false);
        var program = new GenericMappingEngine().map(source, inputs);
        assertTrue(program.isCompact());
        var built = new CodeGroundedNativePipeline().buildCanonicalProgram(source, NativeProjectionMode.AUTO, program);
        assertNotNull(built.nativeResult().state().system());
        assertProgramEvidenceIsCanonicalJson(built);
        assertNotNull(built.nativeResult().state().system().model().getClass("Auction"));
    }

    @Test
    void buildsCanonicalProgramForAuctionFalseCompact() throws Exception {
        Path root = EXAMPLES_DIR.resolve("auction-false");
        var source = official(root.resolve("auction.jcm"));
        var inputs = MappingInputs.read(root.resolve("auction.jcm"), root.resolve("auction-compact-mapping.xml"), root.resolve("auction.ocl"), root.resolve("auction.csl"), root.resolve("auction.istar"), false);
        var program = new GenericMappingEngine().map(source, inputs);
        assertTrue(program.isCompact());
        var built = new CodeGroundedNativePipeline().buildCanonicalProgram(source, NativeProjectionMode.AUTO, program);
        assertNotNull(built.nativeResult().state().system());
        assertProgramEvidenceIsCanonicalJson(built);
        assertNotNull(built.nativeResult().state().system().model().getClass("Auction"));
    }

    @Test
    void buildsCanonicalProgramForHelloWorldCompact() throws Exception {
        Path root = EXAMPLES_DIR.resolve("hello-world");
        var source = official(root.resolve("helloworld.jcm"));
        var inputs = MappingInputs.read(root.resolve("helloworld.jcm"), root.resolve("hello-compact-mapping.xml"), root.resolve("hello.ocl"), root.resolve("hello.csl"), root.resolve("hello.istar"), false);
        var program = new GenericMappingEngine().map(source, inputs);
        assertTrue(program.isCompact());
        var built = new CodeGroundedNativePipeline().buildCanonicalProgram(source, NativeProjectionMode.AUTO, program);
        assertNotNull(built.nativeResult().state().system());
        assertProgramEvidenceIsCanonicalJson(built);
        assertNotNull(built.nativeResult().state().system().model().getClass("Console"));
    }

    @Test
    void buildsCanonicalProgramForHouseBuildingCompact() throws Exception {
        Path root = EXAMPLES_DIR.resolve("house-building");
        var source = official(root.resolve("house-building.jcm"));
        var inputs = MappingInputs.read(root.resolve("house-building.jcm"), root.resolve("house-compact-mapping.xml"), root.resolve("house.ocl"), root.resolve("house.csl"), root.resolve("house.istar"), false);
        var program = new GenericMappingEngine().map(source, inputs);
        assertTrue(program.isCompact());
        var built = new CodeGroundedNativePipeline().buildCanonicalProgram(source, NativeProjectionMode.AUTO, program);
        assertNotNull(built.nativeResult().state().system());
        assertProgramEvidenceIsCanonicalJson(built);
        assertNotNull(built.nativeResult().state().system().model().getClass("ProcurementAuction"));
    }
}
