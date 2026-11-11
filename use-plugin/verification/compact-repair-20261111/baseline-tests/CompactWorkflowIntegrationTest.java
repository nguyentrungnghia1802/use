package org.tzi.use.plugins.jacamo.semantics.mapping.compact;

import org.jacamo.bridge.contract.ModelSnapshot;
import org.jacamo.bridge.contract.semantic.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tzi.use.plugins.jacamo.codegrounded.CodeGroundedNativePipeline;
import org.tzi.use.plugins.jacamo.codegrounded.use.NativeProjectionMode;
import org.tzi.use.plugins.jacamo.semantics.evidence.TraceComplement;
import org.tzi.use.plugins.jacamo.semantics.mapping.SemanticProgram;
import org.tzi.use.plugins.jacamo.semantics.mapping.xml.GenericMappingEngine;
import org.tzi.use.plugins.jacamo.semantics.mapping.xml.MappingInputs;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class CompactWorkflowIntegrationTest {

    @TempDir
    Path tempDir;

    private static SemanticMetadata meta(String id, String kind, String fqcn) {
        return new SemanticMetadata(id, kind, fqcn,
                org.jacamo.bridge.contract.semantic.EvidenceAuthority.OFFICIAL_JACAMO_API,
                org.jacamo.bridge.contract.semantic.Fidelity.EXACT,
                org.jacamo.bridge.contract.CapabilityStatus.COMPLETE,
                List.of(), List.of());
    }

    @Test
    void prepareAndImportCompactWorkflowSuccessfully() throws IOException {
        String cslText = """
                csl v4.0 AuctionSystem {
                    entity Auction {
                        attribute winner : String;
                    }
                    role auctioneer;
                }
                """;

        String istarText = """
                istar AuctionSystemGoals {
                    role auctioneer {
                        goal do_auction : Achieve
                        task bid > do_auction
                        task close_auction > do_auction
                    }
                }
                """;

        String compactXml = """
                <?xml version="1.0" encoding="utf-8"?>
                <mapping id="auction-compact-test" xmlns="urn:use:jacamo-mapping:3" version="3.0">
                  <csl>
                    <map source="artifact:auction_env.AuctionArtifact" target="Auction"/>
                    <map source="artifact:auction_env.AuctionArtifact/property:winner" target="Auction.winner"/>
                  </csl>
                  <istar>
                    <map source="agent:auctioneer" target="auctioneer"/>
                    <map source="asl:src/agt/auctioneer.asl/goal:do_auction" target="auctioneer.do_auction"/>
                    <map source="asl:src/agt/auctioneer.asl/plan:do_auction/step:bid[0..1]" target="auctioneer.bid"/>
                    <map source="asl:src/agt/auctioneer.asl/plan:do_auction/step:close_auction[1..2]" target="auctioneer.close_auction"/>
                  </istar>
                  <runtime>
                    <actorRuntime actor="auctioneer" activationPolicy="EXPLICIT_ENACTMENT" episodePolicy="STATIC_LIFETIME"/>
                  </runtime>
                </mapping>
                """;

        Path jcmFile = tempDir.resolve("test.jcm");
        Files.writeString(jcmFile, "mas test {}");

        Path xmlFile = tempDir.resolve("compact-mapping.xml");
        Files.writeString(xmlFile, compactXml);

        Path cslFile = tempDir.resolve("model.csl");
        Files.writeString(cslFile, cslText);

        Path istarFile = tempDir.resolve("goals.istar");
        Files.writeString(istarFile, istarText);

        MappingInputs inputs = MappingInputs.read(jcmFile, xmlFile, null, cslFile, istarFile);
        assertTrue(inputs.isCompact(), "MappingInputs must be recognized as compact v3");
        assertNotNull(inputs.compactSpecification(), "CompactMappingSpecification must be populated");
        assertEquals("auction-compact-test", inputs.compactSpecification().id());

        // Create ModelSnapshot with corresponding source facts
        JacamoSemanticSnapshot semanticSnapshot = new JacamoSemanticSnapshot(
                JacamoSemanticSnapshot.CURRENT_VERSION,
                new JcmSemanticContract.ProjectSemantic(meta("jcm:project:test", "JACAMO_PROJECT", "jcm.project"), "test", inputs.projectDigest()),
                List.of(new JcmSemanticContract.AgentDeclarationSemantic(meta("ag-1", "JACAMO_AGENT_DECLARATION", "jcm.agent"), "auctioneer", "src/agt/auctioneer.asl", Map.of(), List.of(), "", "", "", 1)),
                List.of(),
                List.of(new JcmSemanticContract.ArtifactDeclarationSemantic(meta("art-1", "JACAMO_ARTIFACT_DECLARATION", "jcm.artifact"), "auction", "wsp", "auction_env.AuctionArtifact", List.of())),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(new JasonSemanticContract.AgentProgramSemantic(
                        meta("prog-1", "JASON_AGENT_PROGRAM", "jason.asl"),
                        "ag-1",
                        "src/agt/auctioneer.asl",
                        "asl-hash",
                        new JasonSemanticContract.PlanLibrarySemantic(
                                meta("lib-1", "JASON_PLAN_LIBRARY", "jason.lib"),
                                List.of(new JasonSemanticContract.PlanSemantic(
                                        meta("plan-1", "JASON_PLAN", "jason.plan"),
                                        0,
                                        "do_auction",
                                        "",
                                        new JasonSemanticContract.TriggerSemantic(meta("trig-1", "JASON_TRIGGER", "jason.trigger"), "add", "achieve", "+!do_auction"),
                                        List.of(
                                                new JasonSemanticContract.PlanBodyElementSemantic(meta("b-1", "JASON_PLAN_BODY", "jason.body"), 0, "action", "bid", ""),
                                                new JasonSemanticContract.PlanBodyElementSemantic(meta("b-2", "JASON_PLAN_BODY", "jason.body"), 1, "action", "close_auction", "")
                                        )
                                ))
                        ),
                        List.of(),
                        List.of(),
                        List.of(new JasonSemanticContract.AgentGoalSemantic(meta("goal-1", "JASON_GOAL", "jason.goal"), 0, "do_auction", "achieve")),
                        List.of()
                )),
                List.of(new CartagoSemanticContract.EnvironmentSemantic(
                        meta("env-1", "CARTAGO_ENVIRONMENT", "c4j.env"),
                        "env", "env-1", "1.0", "local",
                        List.of(),
                        List.of(new CartagoSemanticContract.ArtifactTypeSemantic(meta("at-1", "CARTAGO_ARTIFACT_TYPE", "auction_env.AuctionArtifact"), "auction_env.AuctionArtifact", "")),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(new CartagoSemanticContract.LiveObservablePropertySemantic(meta("prop-1", "CARTAGO_PROPERTY", "auction_env.AuctionArtifact"), "art-1", "prop-1", "winner", List.of(), List.of())),
                        List.of(), List.of(), List.of(), List.of(), List.of()
                )),
                List.of(), List.of(), List.of()
        );

        ModelSnapshot snapshot = new ModelSnapshot(
                "rev-1", List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), Map.of(), semanticSnapshot
        );

        GenericMappingEngine engine = new GenericMappingEngine();
        SemanticProgram program = engine.map(snapshot, inputs);

        assertNotNull(program, "SemanticProgram must be compiled");
        assertTrue(program.isCompact(), "Program must be compact");
        assertNotNull(program.compactPlan(), "Compact plan must be present");
        assertEquals(2, program.compactPlan().cslBindings().size());
        assertEquals(4, program.compactPlan().iStarBindings().size());
        assertFalse(program.compactPlan().fingerprint().isBlank(), "Fingerprint must be computed");

        // Canonical build must create authoritative USE MSystem
        CodeGroundedNativePipeline pipeline = new CodeGroundedNativePipeline();
        CodeGroundedNativePipeline.CanonicalBuildResult canonical = pipeline.buildCanonicalProgram(
                snapshot, NativeProjectionMode.AUTO, program);

        assertNotNull(canonical.nativeResult().state().system());
        assertNotNull(canonical.nativeResult().state().system().model().getClass("Auction"),
                "MSystem model must contain the projected Auction entity");
    }

    @Test
    void rejectsProceduralLegacyConstructsInCompactMapping() {
        String invalidXml = """
                <mapping xmlns="urn:use:jacamo-mapping:3" version="3.0">
                  <csl>
                    <table name="legacy_table"/>
                  </csl>
                </mapping>
                """;
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                CompactMappingReader.read(invalidXml, "invalid.xml"));
        assertTrue(ex.getMessage().contains("FORBIDDEN_PROCEDURAL_ELEMENT"),
                "Must reject procedural table element with clear diagnostic");
    }

    @Test
    void failsExplicitlyWhenModelFilesAreMissing() throws IOException {
        Path jcmFile = tempDir.resolve("test2.jcm");
        Files.writeString(jcmFile, "mas test2 {}");

        Path xmlFile = tempDir.resolve("compact2.xml");
        Files.writeString(xmlFile, """
                <mapping id="missing-models" xmlns="urn:use:jacamo-mapping:3" version="3.0">
                  <csl>
                    <map source="artifact:Test" target="Test"/>
                  </csl>
                </mapping>
                """);

        MappingInputs inputs = MappingInputs.read(jcmFile, xmlFile, null, null, null);
        assertTrue(inputs.isCompact());
        assertFalse(inputs.authoredModels(), "Must detect absence of authored CSL and iStar models");

        GenericMappingEngine engine = new GenericMappingEngine();
        ModelSnapshot snapshot = new ModelSnapshot(
                "rev-1", List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), Map.of(), JacamoSemanticSnapshot.empty()
        );

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                engine.map(snapshot, inputs));
        assertTrue(ex.getMessage().contains("COMPACT_MAPPING_AUTHORED_MODELS_REQUIRED"),
                "Must explicitly demand authored models without silent fallback");
    }
}
