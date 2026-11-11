package org.tzi.use.plugins.jacamo.semantics.mapping.compact;

import org.jacamo.bridge.contract.VerifiedCheckpointProtocol;
import org.junit.jupiter.api.Test;
import org.tzi.use.parser.use.USECompiler;
import org.tzi.use.uml.mm.ModelFactory;
import org.tzi.use.uml.sys.MSystem;
import org.tzi.use.semantic.engine.frontend.CslFrontend;
import org.vnu.sme.goal.dsl.csl.mm.CslModel;
import org.vnu.sme.goal.dsl.istar.mm.GoalModel;

import java.io.ByteArrayInputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class NativeVerificationCoordinatorTest {

    @Test
    void evaluatesNativeInvariantsButCannotApproveWithoutNativeInventory() {
        // CSL model 1: invariant requires best_bid >= 0 (satisfied)
        String useModelPassing = """
                model AuctionSystem
                class Auction
                attributes
                    winner : String
                    currentWinner : String
                    best_bid : Real
                constraints
                    inv BidNonNegative:
                        self.best_bid >= 0
                end
                """;
        var model1 = USECompiler.compileSpecification(
                new ByteArrayInputStream(useModelPassing.getBytes(StandardCharsets.UTF_8)),
                "modelPassing.use",
                new PrintWriter(new StringWriter()),
                new ModelFactory()
        );
        assertNotNull(model1);
        MSystem system1 = new MSystem(model1);

        String cslText = """
                csl v4.0 AuctionSystem {
                    entity Auction {
                        attribute winner : String;
                        attribute currentWinner : String;
                        attribute best_bid : Real;
                    }
                }
                """;
        CslModel cslModel = new CslFrontend().compile(cslText, "AuctionSystem.csl").requireModel();
        NativeTargetCatalog targetCatalog = NativeTargetCatalog.fromModels(cslModel, null);

        SourceSymbolCatalog sourceCatalog = new SourceSymbolCatalog();
        sourceCatalog.register(SourceSymbol.artifact("auction_env.AuctionArtifact", "h1"));
        sourceCatalog.register(SourceSymbol.property("auction_env.AuctionArtifact", "winner"));
        sourceCatalog.register(SourceSymbol.field("auction_env.AuctionArtifact", "currentWinner", "java.lang.String", "a".repeat(64)));
        sourceCatalog.register(SourceSymbol.property("auction_env.AuctionArtifact", "best_bid"));

        String xml = """
                <mapping id="auction" xmlns="urn:use:jacamo-mapping:3" version="3.0">
                  <csl>
                    <map source="artifact:auction_env.AuctionArtifact" target="Auction"/>
                    <map source="artifact:auction_env.AuctionArtifact/property:winner" target="Auction.winner"/>
                    <map source="artifact:auction_env.AuctionArtifact/field:currentWinner" target="Auction.currentWinner"/>
                    <map source="artifact:auction_env.AuctionArtifact/property:best_bid" target="Auction.best_bid"/>
                  </csl>
                </mapping>
                """;
        CompactMappingSpecification spec = CompactMappingReader.read(xml, "auction.xml");
        CompiledObservationPlan plan = ObservationPlanCompiler.compile(spec, sourceCatalog, targetCatalog);

        // Cut with best_bid = 25.0
        var artCut = new PhysicalCutFact.ArtifactInstance(
                "auction_art",
                "auction_env.AuctionArtifact",
                "auction_wsp",
                "run-1", "incarnation-1",
                Map.of("winner", "alice", "best_bid", 25.0),
                Map.of("currentWinner", "alice")
        );
        PhysicalCutFact cut = new PhysicalCutFact("cut-eval", 1, List.of(artCut), List.of(), List.of(), List.of());

        // Project cut into system1
        RuntimeInstanceLinker.ProjectionTransaction tx = RuntimeInstanceLinker.link(plan, cut);
        AuthoritativeMSystemProjector.project(system1, tx);

        // Verification on passing model
        NativeVerificationCoordinator.VerificationAssessment assessment1 =
                NativeVerificationCoordinator.evaluate(plan, system1, cut);

        assertFalse(assessment1.allPassed());
        assertEquals("PASS", assessment1.invariantResults().getFirst().verdict());
        assertFalse(assessment1.hasFailures());
        assertEquals(VerifiedCheckpointProtocol.Action.HOLD_UNVERIFIED, assessment1.recommendedAction());
        assertEquals("UNKNOWN", assessment1.checkpointChecks().get(VerifiedCheckpointProtocol.REQUIRED_CHECK).truth());

        // CSL model 2: changing model bytes to require best_bid > 50 (will fail since actual is 25.0)
        String useModelFailing = """
                model AuctionSystem
                class Auction
                attributes
                    winner : String
                    currentWinner : String
                    best_bid : Real
                constraints
                    inv HighBidOnly:
                        self.best_bid > 50.0
                end
                """;
        var model2 = USECompiler.compileSpecification(
                new ByteArrayInputStream(useModelFailing.getBytes(StandardCharsets.UTF_8)),
                "modelFailing.use",
                new PrintWriter(new StringWriter()),
                new ModelFactory()
        );
        assertNotNull(model2);
        MSystem system2 = new MSystem(model2);

        // Project same cut into system2
        AuthoritativeMSystemProjector.project(system2, tx);

        // Verification on failing model
        NativeVerificationCoordinator.VerificationAssessment assessment2 =
                NativeVerificationCoordinator.evaluate(plan, system2, cut);

        assertFalse(assessment2.allPassed());
        assertTrue(assessment2.hasFailures());
        assertEquals(VerifiedCheckpointProtocol.Action.HOLD_FAILURE, assessment2.recommendedAction());
        assertEquals("FAIL", assessment2.checkpointChecks().get(VerifiedCheckpointProtocol.REQUIRED_CHECK).truth());
        assertEquals(1, assessment2.invariantResults().size());
        assertEquals("FAIL", assessment2.invariantResults().get(0).verdict());
        assertTrue(assessment2.invariantResults().get(0).violatingObjects().contains(RuntimeInstanceLinker.artifactObjectName("Auction", artCut)));
    }

    @Test
    void holdsUnverifiedWhenTaskStatusIsUnknown() {
        String cslText = """
                csl v4.0 Mini {
                    entity Auction {
                        attribute status : String;
                    }
                }
                """;
        CslModel cslModel = new CslFrontend().compile(cslText, "Mini.csl").requireModel();

        String istarText = """
                istar MiniGoals {
                    role bidder {
                        goal buy : Achieve
                        task bid > buy
                        task wait_bid > buy
                    }
                }
                """;
        GoalModel goalModel = new org.tzi.use.semantic.engine.frontend.IStarFrontend().compile(istarText, "mini.istar").requireModel();
        NativeTargetCatalog targetCatalog = NativeTargetCatalog.fromModels(cslModel, goalModel);

        SourceSymbolCatalog sourceCatalog = new SourceSymbolCatalog();
        sourceCatalog.register(SourceSymbol.artifact("env.Art", "h"));
        sourceCatalog.register(SourceSymbol.property("env.Art", "status"));
        sourceCatalog.registerPlan("src/agt/bidder.asl", "+!buy", "buy_plan", 4, "", "a".repeat(64));

        String xml = """
                <mapping id="mini" xmlns="urn:use:jacamo-mapping:3" version="3.0">
                  <csl>
                    <map source="artifact:env.Art" target="Auction"/>
                    <map source="artifact:env.Art/property:status" target="Auction.status"/>
                  </csl>
                  <istar>
                    <map source="asl:src/agt/bidder.asl/plan:buy_plan/step:bid[1..2]" target="bidder.bid"/>
                  </istar>
                </mapping>
                """;
        CompactMappingSpecification spec = CompactMappingReader.read(xml, "mini.xml");
        CompiledObservationPlan plan = ObservationPlanCompiler.compile(spec, sourceCatalog, targetCatalog);

        String useModel = """
                model Mini
                class Auction
                attributes
                    status : String
                end
                """;
        var model = USECompiler.compileSpecification(
                new ByteArrayInputStream(useModel.getBytes(StandardCharsets.UTF_8)),
                "mini.use",
                new PrintWriter(new StringWriter()),
                new ModelFactory()
        );
        MSystem system = new MSystem(model);

        // Cut with step receipt whose status is unknown/inconclusive
        var step = new PhysicalCutFact.StepReceipt("+!buy", "step1", "bid", 1, "INCONCLUSIVE");
        PhysicalCutFact cut = new PhysicalCutFact("cut-unknown", 1, List.of(), List.of(), List.of(), List.of(step));

        NativeVerificationCoordinator.VerificationAssessment assessment =
                NativeVerificationCoordinator.evaluate(plan, system, cut);

        assertFalse(assessment.allPassed());
        assertTrue(assessment.hasUnknowns());
        assertEquals(VerifiedCheckpointProtocol.Action.HOLD_UNVERIFIED, assessment.recommendedAction());
        assertEquals("UNKNOWN", assessment.checkpointChecks().get(VerifiedCheckpointProtocol.REQUIRED_CHECK).truth());
    }
}

