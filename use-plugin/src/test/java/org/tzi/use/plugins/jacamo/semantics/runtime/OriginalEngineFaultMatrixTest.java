package org.tzi.use.plugins.jacamo.semantics.runtime;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.tzi.use.api.UseSystemApi;
import org.tzi.use.plugins.jacamo.semantics.*;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.RuntimeVerificationCoordinator;
import org.tzi.use.plugins.jacamo.semantics.evidence.SemanticVerdict;
import static org.junit.jupiter.api.Assertions.*;

class OriginalEngineFaultMatrixTest {
    @Test void inputAndPostPropagationFailuresDiscardStagedHistoryWithoutInventingSourceGap() throws Exception {
        for(String point:List.of("BEFORE_ENGINE_STEP","AFTER_PROPAGATION"))try(var f=new CanonicalRuntimeFixture(CslCounterArtifact.class,CanonicalGoalMappingTest::rules)) {
            f.settled();var p=f.projector();var c=p.coordinator();var owner=c.originalEngine();var before=owner.checkpoint().get(5,TimeUnit.SECONDS);owner.faultProbe(stage->{if(stage.equals(point))throw new IllegalStateException(point);});
            c.transaction("EVENT","accepted-native","unit-fault",1,Instant.now(),Map.of(),true,()->{UseSystemApi.create(p.system(),false).setAttributeValue(f.counter().name(),"p","1");return new RuntimeVerificationCoordinator.Mutation<>(null,true);});f.settled();
            assertEquals("1",c.read(()->f.counter().state(p.system().state()).attributeValue("p").toString()));assertEquals(before.histories(),owner.checkpoint().get(5,TimeUnit.SECONDS).histories());assertFalse(c.journal().hasGap());assertFalse(p.resyncRequired());assertEquals("STALE",c.latest().freshness());assertTrue(c.latest().semantic().constraints().stream().anyMatch(r->r.kind().equals("EVALUATION_ERROR")&&r.verdict()==SemanticVerdict.ERROR));
            owner.faultProbe(stage->{});c.manualVerify();f.settled();assertEquals("GAP",((Map<?,?>)c.latest().semantic().observation().get("originalEngine")).get("historyQuality"));
        }
    }
    @Test void topologyConstructionFailureClosesTheStagedContextAndRestoresPreviousTopologyAndHistory() throws Exception {
        try(var f=new CanonicalRuntimeFixture(CslCounterArtifact.class,CanonicalGoalMappingTest::rules)) {
            f.settled();var c=f.projector().coordinator();var owner=c.originalEngine();var before=owner.checkpoint().get(5,TimeUnit.SECONDS);var builds=owner.metrics().get("topologyBuilds");
            f.reviewerEnactment(false);f.resync();f.settled();
            owner.faultProbe(stage->{if(stage.equals("AFTER_TOPOLOGY_BUILD"))throw new IllegalStateException(stage);});
            f.reviewerEnactment(true);f.resync();f.settled();
            assertEquals("STALE",c.latest().freshness());assertTrue(c.latest().diagnostic().contains("AFTER_TOPOLOGY_BUILD"));assertEquals(0,((Number)owner.metrics().get("pendingPins")).intValue());
            assertEquals(before.histories(),owner.checkpoint().get(5,TimeUnit.SECONDS).histories());assertEquals(builds,owner.metrics().get("topologyBuilds"));
        }
    }
}
