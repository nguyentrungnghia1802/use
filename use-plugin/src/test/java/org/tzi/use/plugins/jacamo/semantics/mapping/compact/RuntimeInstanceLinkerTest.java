package org.tzi.use.plugins.jacamo.semantics.mapping.compact;

import org.junit.jupiter.api.Test;
import org.tzi.use.parser.use.USECompiler;
import org.tzi.use.uml.mm.ModelFactory;
import org.tzi.use.uml.ocl.value.RealValue;
import org.tzi.use.uml.ocl.value.StringValue;
import org.tzi.use.uml.ocl.value.Value;
import org.tzi.use.uml.sys.MObject;
import org.tzi.use.uml.sys.MSystem;
import org.tzi.use.semantic.engine.frontend.CslFrontend;
import org.vnu.sme.goal.dsl.csl.mm.CslModel;

import java.io.ByteArrayInputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RuntimeInstanceLinkerTest {

    @Test
    void linksArtifactInstancesAndProjectsToMSystemWithRollback() {
        // Compile miniature CSL
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
                <mapping id="auction-runtime" xmlns="urn:use:jacamo-mapping:3" version="3.0">
                  <csl>
                    <map source="artifact:auction_env.AuctionArtifact" target="Auction"/>
                    <map source="artifact:auction_env.AuctionArtifact/property:winner" target="Auction.winner"/>
                    <map source="artifact:auction_env.AuctionArtifact/field:currentWinner" target="Auction.currentWinner"/>
                    <map source="artifact:auction_env.AuctionArtifact/property:best_bid" target="Auction.best_bid"/>
                  </csl>
                </mapping>
                """;
        CompactMappingSpecification spec = CompactMappingReader.read(xml, "auction-runtime.xml");
        CompiledObservationPlan plan = ObservationPlanCompiler.compile(spec, sourceCatalog, targetCatalog);

        // Create cut 1: artifact created, currentWinner is 'bob', best_bid is 15.5, winner is 'no_winner'
        var artCut1 = new PhysicalCutFact.ArtifactInstance(
                "auction_art",
                "auction_env.AuctionArtifact",
                "auction_wsp",
                "run-1", "incarnation-1",
                Map.of("winner", "no_winner", "best_bid", 15.5),
                Map.of("currentWinner", "bob")
        );
        PhysicalCutFact cut1 = new PhysicalCutFact("cut-1", 1, List.of(artCut1), List.of(), List.of(), List.of());

        // Link instances
        RuntimeInstanceLinker.ProjectionTransaction tx1 = RuntimeInstanceLinker.link(plan, cut1);
        assertEquals("cut-1", tx1.cutId());
        assertEquals(1, tx1.objectMutations().size());

        RuntimeInstanceLinker.ObjectMutation mut1 = tx1.objectMutations().get(0);
        assertEquals("Auction", mut1.className());
        assertEquals(RuntimeInstanceLinker.artifactObjectName("Auction", artCut1), mut1.objectName());
        assertEquals("no_winner", ((StringValue) mut1.attributes().get("winner")).value());
        assertEquals("bob", ((StringValue) mut1.attributes().get("currentWinner")).value());
        assertEquals(15.5, ((RealValue) mut1.attributes().get("best_bid")).value());

        // Create MSystem for projection
        String useModelText = """
                model AuctionSystem
                class Auction
                attributes
                    winner : String
                    currentWinner : String
                    best_bid : Real
                end
                """;
        var model = USECompiler.compileSpecification(
                new ByteArrayInputStream(useModelText.getBytes(StandardCharsets.UTF_8)),
                "model.use",
                new PrintWriter(new StringWriter()),
                new ModelFactory()
        );
        assertNotNull(model);
        MSystem system = new MSystem(model);

        // Project cut 1 into MSystem
        AuthoritativeMSystemProjector.AppliedTransaction applied1 = AuthoritativeMSystemProjector.project(system, tx1);
        assertNotNull(applied1);
        assertEquals("cut-1", applied1.cutId());

        MObject auctionObj = system.state().objectByName(RuntimeInstanceLinker.artifactObjectName("Auction", artCut1));
        assertNotNull(auctionObj);
        var cls = model.getClass("Auction");
        assertEquals("no_winner", ((StringValue) auctionObj.state(system.state()).attributeValue(cls.attribute("winner", true))).value());
        assertEquals("bob", ((StringValue) auctionObj.state(system.state()).attributeValue(cls.attribute("currentWinner", true))).value());

        // Cut 2: bob wins, winner becomes 'bob', best_bid becomes 20.0
        var artCut2 = new PhysicalCutFact.ArtifactInstance(
                "auction_art",
                "auction_env.AuctionArtifact",
                "auction_wsp",
                "run-1", "incarnation-1",
                Map.of("winner", "bob", "best_bid", 20.0),
                Map.of("currentWinner", "bob")
        );
        PhysicalCutFact cut2 = new PhysicalCutFact("cut-2", 2, List.of(artCut2), List.of(), List.of(), List.of());
        RuntimeInstanceLinker.ProjectionTransaction tx2 = RuntimeInstanceLinker.link(plan, cut2);
        AuthoritativeMSystemProjector.AppliedTransaction applied2 = AuthoritativeMSystemProjector.project(system, tx2);

        // Verify updated state
        assertEquals("bob", ((StringValue) auctionObj.state(system.state()).attributeValue(cls.attribute("winner", true))).value());
        assertEquals(20.0, ((RealValue) auctionObj.state(system.state()).attributeValue(cls.attribute("best_bid", true))).value());

        // Test Rollback of cut 2
        applied2.rollback(system);
        assertTrue(applied2.isRolledBack());
        // Winner should be rolled back to 'no_winner' and best_bid to 15.5!
        assertEquals("no_winner", ((StringValue) auctionObj.state(system.state()).attributeValue(cls.attribute("winner", true))).value());
        assertEquals(15.5, ((RealValue) auctionObj.state(system.state()).attributeValue(cls.attribute("best_bid", true))).value());
    }

    @Test
    void handlesAbsenceWhenArtifactNotYetCreated() {
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
        sourceCatalog.register(SourceSymbol.artifact("auction_env.AuctionArtifact", "h1"));
        sourceCatalog.register(SourceSymbol.property("auction_env.AuctionArtifact", "winner"));

        String xml = """
                <mapping id="test" xmlns="urn:use:jacamo-mapping:3" version="3.0">
                  <csl>
                    <map source="artifact:auction_env.AuctionArtifact" target="Auction"/>
                    <map source="artifact:auction_env.AuctionArtifact/property:winner" target="Auction.winner"/>
                  </csl>
                </mapping>
                """;
        CompactMappingSpecification spec = CompactMappingReader.read(xml, "test.xml");
        CompiledObservationPlan plan = ObservationPlanCompiler.compile(spec, sourceCatalog, targetCatalog);

        // Empty cut (no artifacts observed yet)
        PhysicalCutFact emptyCut = new PhysicalCutFact("initial-cut", 0, List.of(), List.of(), List.of(), List.of());
        RuntimeInstanceLinker.ProjectionTransaction tx = RuntimeInstanceLinker.link(plan, emptyCut);

        assertTrue(tx.objectMutations().isEmpty(), "No object mutations when artifact is not present in cut");
    }
}

