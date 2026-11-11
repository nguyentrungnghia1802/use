package org.tzi.use.plugins.jacamo.semantics.mapping.compact;

import org.jacamo.bridge.contract.ObservationCapabilityRequest;
import org.jacamo.bridge.contract.VerifiedCheckpointProtocol;
import org.junit.jupiter.api.Test;
import org.tzi.use.semantic.engine.frontend.CslFrontend;
import org.tzi.use.semantic.engine.frontend.IStarFrontend;
import org.vnu.sme.goal.dsl.csl.mm.CslModel;
import org.vnu.sme.goal.dsl.istar.mm.GoalModel;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ObservationPlanCompilerTest {

    @Test
    void compilesValidPlanWithSynthesizedDemandsAndRules() {
        String cslText = """
                csl v4.0 AuctionSystem {
                    entity Auction {
                        attribute winner : String;
                        attribute currentWinner : String;
                    }
                    role auctioneer;
                }
                """;
        CslModel cslModel = new CslFrontend().compile(cslText, "AuctionSystem.csl").requireModel();

        String istarText = """
                istar AuctionSystemGoals {
                    role auctioneer {
                        goal do_auction : Achieve
                        task bid > do_auction
                        task close_auction > do_auction
                    }
                }
                """;
        GoalModel goalModel = new IStarFrontend().compile(istarText, "AuctionSystem.istar").requireModel();

        NativeTargetCatalog targetCatalog = NativeTargetCatalog.fromModels(cslModel, goalModel);

        SourceSymbolCatalog sourceCatalog = new SourceSymbolCatalog();
        sourceCatalog.register(SourceSymbol.artifact("auction_env.AuctionArtifact", "art-hash"));
        sourceCatalog.register(SourceSymbol.property("auction_env.AuctionArtifact", "winner"));
        sourceCatalog.register(SourceSymbol.field("auction_env.AuctionArtifact", "currentWinner"));
        sourceCatalog.register(SourceSymbol.agent("auctioneer"));
        sourceCatalog.registerPlan("src/agt/auctioneer.asl", "+!do_auction", "do_auction", 5);

        String compactXml = """
                <mapping id="auction-test" xmlns="urn:use:jacamo-mapping:3" version="3.0">
                  <csl>
                    <map source="artifact:auction_env.AuctionArtifact" target="Auction"/>
                    <map source="artifact:auction_env.AuctionArtifact/property:winner" target="Auction.winner"/>
                    <map source="artifact:auction_env.AuctionArtifact/field:currentWinner" target="Auction.currentWinner"/>
                  </csl>
                  <istar>
                    <map source="agent:auctioneer" target="auctioneer"/>
                    <map source="asl:src/agt/auctioneer.asl/goal:do_auction" target="auctioneer.do_auction"/>
                    <map source="asl:src/agt/auctioneer.asl/plan:do_auction/step:bid[1..2]" target="auctioneer.bid"/>
                  </istar>
                  <runtime>
                    <actor-runtime actor="auctioneer" activation-policy="EXPLICIT_ENACTMENT" episode-policy="STATIC_LIFETIME"/>
                  </runtime>
                </mapping>
                """;

        CompactMappingSpecification spec = CompactMappingReader.read(compactXml, "compact-auction.xml");
        CompiledObservationPlan plan = ObservationPlanCompiler.compile(spec, sourceCatalog, targetCatalog, "csl123", "istar123");

        assertNotNull(plan);
        assertEquals("auction-test", plan.planId());
        assertNotNull(plan.planFingerprint());
        assertEquals(3, plan.cslBindings().size());
        assertEquals(3, plan.istarBindings().size());
        assertEquals(1, plan.actorRuntimes().size());

        // Check synthesized capability requests:
        // 1) Artifact field capability synthesized
        assertTrue(plan.capabilityRequests().stream().anyMatch(r -> r.provider().equals("cartago.artifact-field.v1")),
                "Expected cartago.artifact-field.v1 request");
        // 2) Jason plan-boundaries capability synthesized
        assertTrue(plan.capabilityRequests().stream().anyMatch(r -> r.provider().equals("jason.plan-boundaries.v1")),
                "Expected jason.plan-boundaries.v1 request");

        // Check synthesized checkpoint rules
        List<VerifiedCheckpointProtocol.Rule> rules = plan.checkpointRules();
        assertFalse(rules.isEmpty(), "Expected synthesized checkpoint rules");
        assertTrue(rules.stream().anyMatch(r -> r.selectorKind().equals("TASK_PRE")), "Expected TASK_PRE rule");
        assertTrue(rules.stream().anyMatch(r -> r.selectorKind().equals("TASK_POST")), "Expected TASK_POST rule");

        // Check transparent explanations
        assertEquals(6, plan.explanations().size());
        assertTrue(plan.explanations().stream().anyMatch(e -> e.frameworkRule().equals("CSL_ATTRIBUTE_FROM_FIELD")));
        assertTrue(plan.explanations().stream().anyMatch(e -> e.frameworkRule().equals("ISTAR_TASK_FROM_PLAN_STEP")));

        // Check evidence summary
        var summary = plan.evidenceSummary();
        assertEquals("3.0.0", summary.get("compilerVersion"));
        assertEquals("auction-test", summary.get("planId"));
    }

    @Test
    void verifiesDeterminismAndSensitivityOfFingerprint() {
        String cslText = """
                csl v4.0 TestDomain {
                    entity Item {
                        attribute status : String;
                    }
                }
                """;
        CslModel cslModel = new CslFrontend().compile(cslText, "TestDomain.csl").requireModel();
        NativeTargetCatalog targetCatalog = NativeTargetCatalog.fromModels(cslModel, null);

        SourceSymbolCatalog sourceCatalog = new SourceSymbolCatalog();
        sourceCatalog.register(SourceSymbol.artifact("env.ItemArtifact", "h1"));
        sourceCatalog.register(SourceSymbol.property("env.ItemArtifact", "status"));

        String xml1 = """
                <mapping id="m1" xmlns="urn:use:jacamo-mapping:3" version="3.0">
                  <csl>
                    <map source="artifact:env.ItemArtifact" target="Item"/>
                    <map source="artifact:env.ItemArtifact/property:status" target="Item.status"/>
                  </csl>
                </mapping>
                """;

        CompactMappingSpecification spec1a = CompactMappingReader.read(xml1, "m1.xml");
        CompactMappingSpecification spec1b = CompactMappingReader.read(xml1, "m1.xml");

        CompiledObservationPlan plan1a = ObservationPlanCompiler.compile(spec1a, sourceCatalog, targetCatalog, "shaA", "shaB");
        CompiledObservationPlan plan1b = ObservationPlanCompiler.compile(spec1b, sourceCatalog, targetCatalog, "shaA", "shaB");

        // Determinism: same inputs -> exact same fingerprint
        assertEquals(plan1a.planFingerprint(), plan1b.planFingerprint());

        // Changing CSL sha changes fingerprint
        CompiledObservationPlan planDiffCsl = ObservationPlanCompiler.compile(spec1a, sourceCatalog, targetCatalog, "shaA_changed", "shaB");
        assertNotEquals(plan1a.planFingerprint(), planDiffCsl.planFingerprint());

        // Changing mapping id changes fingerprint
        String xml2 = """
                <mapping id="m2" xmlns="urn:use:jacamo-mapping:3" version="3.0">
                  <csl>
                    <map source="artifact:env.ItemArtifact" target="Item"/>
                    <map source="artifact:env.ItemArtifact/property:status" target="Item.status"/>
                  </csl>
                </mapping>
                """;
        CompactMappingSpecification spec2 = CompactMappingReader.read(xml2, "m2.xml");
        CompiledObservationPlan plan2 = ObservationPlanCompiler.compile(spec2, sourceCatalog, targetCatalog, "shaA", "shaB");
        assertNotEquals(plan1a.planFingerprint(), plan2.planFingerprint());
    }
}

