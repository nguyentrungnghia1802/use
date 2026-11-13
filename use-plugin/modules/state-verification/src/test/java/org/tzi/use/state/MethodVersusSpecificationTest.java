package org.tzi.use.state;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Counterfactual controls: missing observations versus a missing constraint over available observations. */
class MethodVersusSpecificationTest {
    @TempDir Path temp;
    private final Path example=Path.of("../../examples/auction/state-only").toAbsolutePath().normalize();
    private record RequestOracle(boolean exists,boolean startCompleted,String service) {
        boolean accepts(CslStateVerifier.Snapshot state) {
            if(!exists||!startCompleted)return true;
            return state.artifacts().stream().anyMatch(a->a.name().equals("a1")&&Boolean.TRUE.equals(a.properties().get("running"))&&service.equals(a.properties().get("task"))&&a.observers().contains("b"));
        }
    }
    private CslStateVerifier verifier(String extraAttributes,String extraMapping,String rule)throws Exception {
        String csl=Files.readString(example.resolve("auction.csl"));
        csl=csl.replace("attribute id : String required;","attribute id : String required;\n"+extraAttributes);
        int end=csl.lastIndexOf('}');csl=csl.substring(0,end)+rule+"\n}";
        String mapping=Files.readString(example.resolve("attribute-mapping.xml")).replace("</mapping>",extraMapping+"\n</mapping>");
        Path cslFile=Files.createTempFile(temp,"spec-",".csl"),mappingFile=Files.createTempFile(temp,"map-",".xml");
        Files.writeString(cslFile,csl);Files.writeString(mappingFile,mapping);
        return new CslStateVerifier(mappingFile,cslFile,example.resolve("auction.istar"));
    }
    private CslStateVerifier.Snapshot snapshot(long sequence,boolean present,boolean running,String service,String winner,Map<String,Object> extra) {
        var nodes=List.of(new CslStateVerifier.Node("o","organization","auction","org","/org","",Map.of()),
                new CslStateVerifier.Node("g","group","auctionGroup","group","/org","o",Map.of()),
                new CslStateVerifier.Node("b","agent","auction_capabilities","bob","","",Map.of()),
                new CslStateVerifier.Node("a","agent","auction_capabilities","alice","","",Map.of()));
        var properties=new HashMap<String,Object>(Map.of("running",running,"task",service,"winner",winner,"best_bid",!running&&winner.equals("no_winner")?Double.MAX_VALUE:10));properties.putAll(extra);
        var artifacts=present?List.of(new CslStateVerifier.Artifact("auction-id","auction_env.AuctionArtifact","a1","/org",properties,Set.of("b","a"))):List.<CslStateVerifier.Artifact>of();
        return new CslStateVerifier.Snapshot("run",sequence,true,artifacts,nodes,List.of(new CslStateVerifier.Enactment("g","b","auctioneer"),new CslStateVerifier.Enactment("g","a","participant")),List.of(new CslStateVerifier.Membership("g","o")));
    }
    private CslStateVerifier.Result evaluate(CslStateVerifier.Snapshot s)throws Exception{try(var v=verifier("","","")){return v.update(s);}}
    @Test void completedBadStartAndValidIntermediateStateAreIndistinguishableWithoutAMilestoneObservation()throws Exception {
        var legitimateCreationBeforeStart=snapshot(1,true,false,"service-A","no_winner",Map.of());
        var badStartAlreadyCompleted=snapshot(1,true,false,"service-A","no_winner",Map.of());
        assertTrue(new RequestOracle(true,false,"service-A").accepts(legitimateCreationBeforeStart));
        assertFalse(new RequestOracle(true,true,"service-A").accepts(badStartAlreadyCompleted));
        assertEquals(legitimateCreationBeforeStart,badStartAlreadyCompleted);
        assertEquals(evaluate(legitimateCreationBeforeStart),evaluate(badStartAlreadyCompleted));
        assertEquals("APPROVE",evaluate(badStartAlreadyCompleted).decision());
    }
    @Test void missingObjectAfterARequestAndValidStartupAreIndistinguishableWithoutExpectedRequestState()throws Exception {
        var startup=snapshot(1,false,false,"service-A","no_winner",Map.of());
        var omittedCreationAfterRequest=snapshot(1,false,false,"service-A","no_winner",Map.of());
        assertTrue(new RequestOracle(false,false,"service-A").accepts(startup));
        assertFalse(new RequestOracle(true,true,"service-A").accepts(omittedCreationAfterRequest));
        assertEquals(evaluate(startup),evaluate(omittedCreationAfterRequest));
        assertTrue(evaluate(omittedCreationAfterRequest).elements().isEmpty());
    }
    @Test void arbitraryServiceCannotBeJudgedAgainstAnUnobservedExpectedParameter()throws Exception {
        // The external request can expect A or B; both produce the same currently available CSL data.
        var sameObservedState=snapshot(1,true,true,"service-A","no_winner",Map.of());
        assertTrue(new RequestOracle(true,true,"service-A").accepts(sameObservedState));
        assertFalse(new RequestOracle(true,true,"service-B").accepts(sameObservedState));
        assertEquals("APPROVE",evaluate(sameObservedState).decision());
        assertEquals(evaluate(sameObservedState),evaluate(sameObservedState));
    }
    @Test void onceExpectedServiceIsAvailableOneSpecificationRuleDistinguishesCorrectAndIncorrectOutputs()throws Exception {
        String attributes="attribute expectedService : String required;";
        String mapping="<attribute source='artifact:auction_env.AuctionArtifact/property:expected_service' target='Auction.expectedService'/>";
        var bad=snapshot(1,true,true,"service-A","no_winner",Map.of("expected_service","service-B"));
        try(var v=verifier(attributes,mapping,"")){assertEquals("APPROVE",v.update(bad).decision());}
        String rule="context Auction inv ServiceMatchesRequest: self.running implies self.task = self.expectedService;";
        try(var v=verifier(attributes,mapping,rule)){assertEquals("HOLD_FAILURE",v.update(bad).decision());}
        try(var v=verifier(attributes,mapping,rule)){assertEquals("APPROVE",v.update(snapshot(1,true,true,"service-A","no_winner",Map.of("expected_service","service-A"))).decision());}
    }
    @Test void theExactWinnerRuleNeedsBestBidderDataButNoNewLanguageOperator()throws Exception {
        String attributes="attribute expectedWinner : String required mutable;";
        String mapping="<attribute source='artifact:auction_env.AuctionArtifact/property:best_bidder' target='Auction.expectedWinner'/>";
        String rule="context Auction inv CorrectWinner: not self.running implies self.winner = self.expectedWinner;";
        var bad=snapshot(1,true,false,"service-A","ghost",Map.of("best_bidder","alice"));
        try(var v=verifier(attributes,mapping,"")){assertEquals("APPROVE",v.update(bad).decision());}
        try(var v=verifier(attributes,mapping,rule)){assertEquals("HOLD_FAILURE",v.update(bad).decision());}
        try(var v=verifier(attributes,mapping,rule)){assertEquals("APPROVE",v.update(snapshot(1,true,false,"service-A","alice",Map.of("best_bidder","alice"))).decision());}
        // best_bidder in this diagnostic is oracle-supplied. The real producer has no such observation yet.
    }
}
