package org.tzi.use.plugins.jacamo.semantics.auction;

import java.util.*;
import org.jacamo.bridge.adapter.AuctionSchemeObservation;
import org.jacamo.bridge.contract.*;
import org.junit.jupiter.api.Test;
import org.tzi.use.plugins.jacamo.semantics.evidence.SemanticJson;
import org.tzi.use.plugins.jacamo.semantics.mapping.auction.*;
import static org.junit.jupiter.api.Assertions.*;

class AuctionSchemeDecoderTest {
    @Test void actualSourceCutDecodesAndRehashedWrongSourceScopeTimeOrAdminCannotGainAuthority()throws Exception {
        try(var fixture=new AuctionOrgFixture();var source=new AuctionSchemeObservation(fixture.live.environment,fixture.live.source,fixture.live.run.id(),fixture.live.run.generation())) {
            var live=fixture.live;var id=fixture.scheme("decode","service");var captured=source.capture(id,"ARTIFACT_CREATED",System.currentTimeMillis(),"",null,"");
            var program=new AuctionStaticMappingAdapter().map(live.source,new AuctionStaticMappingAdapter.Selection(live.bundle,live.specification));var decoder=new AuctionSchemeObservationDecoder(program);
            var actual=decoder.decode(captured,live.run.id(),live.run.generation(),live.source.modelRevision());assertTrue(actual.continuous());assertEquals(List.of(),actual.doneBy());assertEquals(Boolean.FALSE,actual.adminOverride());
            for(String name:List.of("source","generation","clock","admin","goal","identity","hash")) {
                var variant=new com.fasterxml.jackson.databind.ObjectMapper().readValue(CanonicalJson.encode(captured),new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>(){});
                switch(name) {
                    case "source"->variant.put("sourceOsSha256","0".repeat(64));case "generation"->variant.put("generation",live.run.generation()+1);
                    case "clock"->variant.put("timeAuthority","MOISE_INTERNAL_TRANSITION_TIME");case "admin"->variant.put("adminBidOverride",true);
                    case "goal"->CanonicalJson.object(((List<?>)variant.get("goals")).getFirst()).put("definition","another-source-goal");
                    case "identity"->{var old=BridgeEntityId.parse((String)variant.get("schemeIdentity"));variant.put("schemeIdentity",new BridgeEntityId(old.authority(),old.dimension(),old.kind(),"foreign-organization",old.localId(),old.incarnation()).canonical());}
                    case "hash"->variant.put("cutHash","0".repeat(64));
                }
                if(!name.equals("hash")){variant.remove("cutHash");variant.put("cutHash",SemanticJson.hash(variant));}
                assertThrows(IllegalArgumentException.class,()->decoder.decode(variant,live.run.id(),live.run.generation(),live.source.modelRevision()),name);
            }
        }
    }
}
