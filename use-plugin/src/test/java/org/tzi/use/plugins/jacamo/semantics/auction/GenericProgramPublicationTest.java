package org.tzi.use.plugins.jacamo.semantics.auction;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.jacamo.bridge.contract.*;
import org.jacamo.bridge.adapter.*;
import org.tzi.use.main.Session;
import org.tzi.use.plugins.jacamo.JaCaMoFacade;
import org.tzi.use.plugins.jacamo.semantics.mapping.xml.*;
import org.tzi.use.plugins.jacamo.semantics.evidence.*;
import static org.junit.jupiter.api.Assertions.*;

/** Portable production input and public import with recorded transport, not live TCP acceptance. */
class GenericProgramPublicationTest {
    @TempDir Path temp;
    @Test void portableProgramRebuildsOnlyFromFrozenSourceXmlAndOclAndRejectsTamper()throws Exception {
        try(var fixture=new AuctionLiveFixture()) {
            var program=new GenericMappingEngine().map(fixture.source,AuctionDeclarativeSpecificationTest.inputs(AuctionDeclarativeSpecificationTest.CASE));
            var codec=new SemanticProgramManifest();var bytes=codec.encode(program);var encoded=CanonicalJson.object(CanonicalJson.decode(bytes));
            assertEquals("1.2.0",encoded.get("schemaVersion"));assertTrue(encoded.containsKey("declarative"));assertFalse(encoded.containsKey("case"));
            var restored=codec.decode(bytes).program();assertNotNull(restored.xmlPlan());assertNull(restored.casePlan());assertEquals(program.hash(),restored.hash());
            for(String key:List.of("profile","rules","implementation","declarative","programHash","cslHash","goalHash","bindingHash","traceHash")) {
                var changed=new TreeMap<>(encoded);changed.put(key,Set.of("profile","rules","implementation","declarative").contains(key)?Map.of():"invalid");
                assertThrows(RuntimeException.class,()->codec.decode(CanonicalJson.encode(changed)),key);
            }
        }
    }
    @Test void publicImportFreezesThreeInputsWithoutAnyOracleAndPreservesOwnerOnInvalidReimport()throws Exception {
        var root=AuctionDeclarativeSpecificationTest.CASE;var original=AuctionDeclarativeSpecificationTest.inputs(root);
        for(var file:original.specification().root().child("sources").children("file")) {var target=temp.resolve(file.attr("path"));Files.createDirectories(target.getParent());Files.copy(root.resolve(file.attr("path")),target);}
        for(String file:List.of("auction-mapping.xml","auction_constraints_final.ocl"))Files.copy(root.resolve(file),temp.resolve(file));
        try(var fixture=new AuctionLiveFixture()) {
            var jcm=temp.resolve("auction.jcm");var source=new OfficialProjectAdapter().adapt(new OfficialProjectLoader().load(jcm),jcm);var host=new Session();
            try(var facade=AuctionImportTest.facade(source,host)) {
                var selection=new JaCaMoFacade.ImportSelection("JACAMO_DECLARATIVE_XML",temp.resolve("auction-mapping.xml"),temp.resolve("auction_constraints_final.ocl"));
                facade.importProject(jcm,selection);var system=host.system();var owner=AuctionImportTest.projector(facade).coordinator().originalEngine();var evidence=facade.semanticProgramEvidence();
                assertNotNull(system.model().getClass("Auction"));assertEquals(0,system.state().numObjects());assertTrue(evidence.containsKey("declarativeProgram"));assertFalse(evidence.containsKey("caseProgram"));assertEquals(selection,facade.importSelection());
                Files.writeString(selection.specification(),"unsupported changed mapping");Files.writeString(selection.ocl(),"changed authored contract");
                facade.resyncRuntime();assertSame(system,host.system());assertSame(owner,AuctionImportTest.projector(facade).coordinator().originalEngine());assertEquals(evidence,facade.semanticProgramEvidence());
                assertThrows(RuntimeException.class,()->facade.importProject(jcm,selection));assertSame(system,host.system());assertSame(owner,AuctionImportTest.projector(facade).coordinator().originalEngine());
                assertFalse(Files.exists(temp.resolve("auction_csl.csl")));assertFalse(Files.exists(temp.resolve("auction_istar_final.istar")));
            }
        }
    }
}
