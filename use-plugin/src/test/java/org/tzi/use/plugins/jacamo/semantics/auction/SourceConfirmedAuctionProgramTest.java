package org.tzi.use.plugins.jacamo.semantics.auction;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.tzi.use.plugins.jacamo.semantics.mapping.xml.*;
import org.tzi.use.plugins.jacamo.codegrounded.*;
import org.tzi.use.plugins.jacamo.codegrounded.use.NativeProjectionMode;
import static org.junit.jupiter.api.Assertions.*;

class SourceConfirmedAuctionProgramTest {
    @Test void explicitProfileUsesUnchangedAuthoredModelsAndNativeConfirmedContracts()throws Exception {
        Path root=AuctionDeclarativeSpecificationTest.CASE,strict=root.resolve("authored-v1"),confirmed=root.resolve("source-confirmed-v1");
        try(var f=new AuctionLiveFixture()) {
            var engine=new GenericMappingEngine();
            var a=engine.map(f.source,MappingInputs.read(root.resolve("auction.jcm"),strict.resolve("auction-mapping.xml"),strict.resolve("auction_constraints_final_fixed.ocl"),strict.resolve("auction_csl.csl"),strict.resolve("auction_istar_final.istar")));
            var b=engine.map(f.source,MappingInputs.read(root.resolve("auction.jcm"),confirmed.resolve("auction-mapping.xml"),confirmed.resolve("auction_confirmed.ocl"),strict.resolve("auction_csl.csl"),strict.resolve("auction_istar_final.istar")));
            assertEquals(a.cslHash(),b.cslHash());assertEquals(a.goalHash(),b.goalHash());assertNotEquals(a.hash(),b.hash());assertNull(b.casePlan());
            var built=new CodeGroundedNativePipeline().buildCanonicalProgram(f.source,NativeProjectionMode.AUTO,b);
            assertEquals(8,built.xmlPreparation().authored().clauses().size());
            var monitor=built.binding().model().getClass("AuctionMonitor");
            for(String name:List.of("startSatisfied","bidEnabled","bidSatisfied","decideEnabled","decideSatisfied")) {
                assertNotNull(monitor.attribute(name+"ConfirmedAt",true));assertNull(monitor.attribute(name+"At",true));
            }
            String body=Files.readString(confirmed.resolve("auction_confirmed.ocl"));assertTrue(body.contains("ConfirmedSchemeOrder"));assertTrue(body.contains("ConfirmedBidEvidence"));
            assertFalse(Files.readString(strict.resolve("auction_constraints_final_fixed.ocl")).contains("ConfirmedAt"));
        }
    }
}
