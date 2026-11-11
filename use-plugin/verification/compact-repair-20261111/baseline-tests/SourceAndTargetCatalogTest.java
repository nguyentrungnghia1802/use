package org.tzi.use.plugins.jacamo.semantics.mapping.compact;

import org.junit.jupiter.api.Test;
import org.tzi.use.semantic.engine.frontend.CslFrontend;
import org.tzi.use.semantic.engine.frontend.IStarFrontend;
import org.vnu.sme.goal.dsl.csl.mm.CslModel;
import org.vnu.sme.goal.dsl.istar.mm.GoalModel;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SourceAndTargetCatalogTest {

    @Test
    void resolvesSymbolsFromMiniatureModels() {
        // Miniature CSL
        String cslText = """
                csl v4.0 MiniatureDomain {
                    entity Auction {
                        attribute winner : String;
                        attribute currentWinner : String;
                    }
                    role Auctioneer;
                }
                """;
        CslModel cslModel = new CslFrontend().compile(cslText, "MiniatureDomain.csl").requireModel();

        // Miniature iStar
        String istarText = """
                istar AuctionGoals {
                    role auctioneer {
                        goal do_auction : Achieve
                        task bid > do_auction
                        task finalize_auction > do_auction
                    }
                }
                """;
        GoalModel goalModel = new IStarFrontend().compile(istarText, "auction.istar").requireModel();

        NativeTargetCatalog targetCatalog = NativeTargetCatalog.fromModels(cslModel, goalModel);

        // Verify target catalog resolution
        TargetSymbol auctionEntity = targetCatalog.resolve(TargetReference.parse("Auction"));
        assertEquals(TargetSymbol.TargetKind.CSL_ENTITY, auctionEntity.kind());

        TargetSymbol winnerAttr = targetCatalog.resolve(TargetReference.parse("Auction.winner"));
        assertEquals(TargetSymbol.TargetKind.CSL_ATTRIBUTE, winnerAttr.kind());
        assertEquals("Auction", winnerAttr.owner());

        TargetSymbol auctioneerActor = targetCatalog.resolve(TargetReference.parse("auctioneer"));
        assertEquals(TargetSymbol.TargetKind.ISTAR_ACTOR, auctioneerActor.kind());

        TargetSymbol doAuctionGoal = targetCatalog.resolve(TargetReference.parse("auctioneer.do_auction"));
        assertEquals(TargetSymbol.TargetKind.ISTAR_GOAL, doAuctionGoal.kind());

        TargetSymbol bidTask = targetCatalog.resolve(TargetReference.parse("auctioneer.bid"));
        assertEquals(TargetSymbol.TargetKind.ISTAR_TASK, bidTask.kind());

        // Setup Source Symbol Catalog
        SourceSymbolCatalog sourceCatalog = new SourceSymbolCatalog();
        sourceCatalog.register(SourceSymbol.artifact("auction_env.AuctionArtifact", "hash123"));
        sourceCatalog.register(SourceSymbol.property("auction_env.AuctionArtifact", "winner"));
        sourceCatalog.register(SourceSymbol.field("auction_env.AuctionArtifact", "currentWinner"));
        sourceCatalog.register(SourceSymbol.agent("auctioneer"));
        sourceCatalog.registerPlan("src/agt/auctioneer.asl", "+!do_auction", "do_auction", 3);

        // Verify source catalog resolution
        SourceSymbol art = sourceCatalog.resolve(SourceReference.parse("artifact:auction_env.AuctionArtifact"));
        assertEquals(SourceReference.Kind.ARTIFACT, art.kind());

        SourceSymbol prop = sourceCatalog.resolve(SourceReference.parse("artifact:auction_env.AuctionArtifact/property:winner"));
        assertEquals(SourceReference.Kind.ARTIFACT_PROPERTY, prop.kind());

        SourceSymbol step = sourceCatalog.resolve(SourceReference.parse("asl:src/agt/auctioneer.asl/plan:do_auction/step:bid[1..2]"));
        assertEquals(SourceReference.Kind.ASL_PLAN_STEP, step.kind());

        // Test correspondence validator on valid compact XML
        String xml = """
                <mapping id="test" xmlns="urn:use:jacamo-mapping:3" version="3.0">
                  <csl>
                    <map source="artifact:auction_env.AuctionArtifact" target="Auction"/>
                    <map source="artifact:auction_env.AuctionArtifact/property:winner" target="Auction.winner"/>
                    <map source="artifact:auction_env.AuctionArtifact/field:currentWinner" target="Auction.currentWinner"/>
                  </csl>
                  <istar>
                    <map source="asl:src/agt/auctioneer.asl/goal:do_auction" target="auctioneer.do_auction"/>
                    <map source="asl:src/agt/auctioneer.asl/plan:do_auction/step:bid[1..2]" target="auctioneer.bid"/>
                  </istar>
                  <runtime>
                    <actor-runtime actor="auctioneer"/>
                  </runtime>
                </mapping>
                """;
        CompactMappingSpecification spec = CompactMappingReader.read(xml, "test.xml");
        List<CorrespondenceValidator.Diagnostic> diags = CorrespondenceValidator.validate(spec, sourceCatalog, targetCatalog);
        assertTrue(diags.isEmpty(), "Expected no diagnostics but got: " + diags);
    }

    @Test
    void rejectsMissingTargetAndWrongOwner() {
        String cslText = """
                csl v4.0 TestDomain {
                    entity Item {
                        attribute price : Integer;
                    }
                }
                """;
        CslModel cslModel = new CslFrontend().compile(cslText, "TestDomain.csl").requireModel();
        NativeTargetCatalog targetCatalog = NativeTargetCatalog.fromModels(cslModel, null);

        // Missing entity
        assertThrows(IllegalArgumentException.class, () -> targetCatalog.resolve(TargetReference.parse("NonExistent")));

        // Missing member on existing entity
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> targetCatalog.resolve(TargetReference.parse("Item.unknownAttribute")));
        assertTrue(ex.getMessage().contains("TARGET_MEMBER_NOT_FOUND"));

        // Wrong owner
        IllegalArgumentException ex2 = assertThrows(IllegalArgumentException.class,
                () -> targetCatalog.resolve(TargetReference.parse("WrongEntity.price")));
        assertTrue(ex2.getMessage().contains("TARGET_OWNER_NOT_FOUND"));
    }

    @Test
    void rejectsStepOutOfRangeInSourceCatalog() {
        SourceSymbolCatalog sourceCatalog = new SourceSymbolCatalog();
        sourceCatalog.registerPlan("src/agt/bob.asl", "+!bid", "bid_plan", 2);

        // Requesting step 1..10 when body has only 2 steps
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> sourceCatalog.resolve(SourceReference.parse("asl:src/agt/bob.asl/plan:bid_plan/step:act[1..10]")));
        assertTrue(ex.getMessage().contains("PLAN_STEP_OUT_OF_BOUNDS"));
    }

    @Test
    void detectsIncompatibleTypeMappingInValidator() {
        String cslText = """
                csl v4.0 TestDomain {
                    entity Auction {
                        attribute winner : String;
                    }
                }
                """;
        CslModel cslModel = new CslFrontend().compile(cslText, "TestDomain.csl").requireModel();
        NativeTargetCatalog targetCatalog = NativeTargetCatalog.fromModels(cslModel, null);

        SourceSymbolCatalog sourceCatalog = new SourceSymbolCatalog();
        sourceCatalog.register(SourceSymbol.artifact("AuctionArtifact", "hash"));
        sourceCatalog.register(SourceSymbol.property("AuctionArtifact", "winner"));

        // Incompatible mapping: mapping an artifact (concept) to an attribute (winner)
        String badXml = """
                <mapping id="bad" xmlns="urn:use:jacamo-mapping:3" version="3.0">
                  <csl>
                    <map source="artifact:AuctionArtifact" target="Auction.winner"/>
                  </csl>
                </mapping>
                """;
        CompactMappingSpecification spec = CompactMappingReader.read(badXml, "bad.xml");
        List<CorrespondenceValidator.Diagnostic> diags = CorrespondenceValidator.validate(spec, sourceCatalog, targetCatalog);
        assertFalse(diags.isEmpty());
        assertTrue(diags.stream().anyMatch(d -> d.code().equals("INCOMPATIBLE_TYPE_MAPPING")));
    }
}
