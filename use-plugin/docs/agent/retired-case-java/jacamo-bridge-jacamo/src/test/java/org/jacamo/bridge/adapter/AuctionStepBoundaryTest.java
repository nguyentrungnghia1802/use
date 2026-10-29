package org.jacamo.bridge.adapter;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import cartago.CartagoEnvironment;
import cartago.util.agent.CartagoBasicContext;
import org.jacamo.bridge.contract.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import static org.junit.jupiter.api.Assertions.*;

class AuctionStepBoundaryTest {
    @Test @Timeout(20) void independentPollDoesNotConsumeTheCartagoStableInterval() throws Exception {
        Path jcm=Path.of("../../jacamo/examples/auction/auction.jcm").toRealPath();
        var model=new OfficialProjectAdapter().adapt(new OfficialProjectLoader().load(jcm),jcm);
        String revision=System.getProperty("jacamo.bridge.modelRevision"),generation=System.getProperty("jacamo.bridge.generation");
        System.setProperty("jacamo.bridge.modelRevision",model.modelRevision());System.setProperty("jacamo.bridge.generation","1");
        var environment=CartagoEnvironment.getInstance();environment.init();
        var events=new CopyOnWriteArrayList<RuntimeEvent>();var polls=new AtomicInteger();
        var mutatingBoard=new java.util.concurrent.atomic.AtomicBoolean();
        var context=new FrameworkObservationProviders.Context(environment,model,jcm.getParent(),"auction-boundary",1,o->{},reason->fail(reason));
        try(var source=new CartagoSnapshotSource(environment,List.of(),"auction-boundary",model,null,false);
            var providers=new ProductionObservationSession(context,List.of())) {
            source.capabilities(providers);source.attach(events::add);
            source.observationScope(new ObservationCaptureScope(false,Set.of("auction_env.AuctionArtifact"),false,Set.of("running","task","best_bid","winner"),"a".repeat(64)));
            source.observationKinds(Set.of(RuntimeFactKind.WORKSPACE,RuntimeFactKind.AGENT,RuntimeFactKind.ARTIFACT,RuntimeFactKind.PROPERTY));
            SnapshotSource independent=new SnapshotSource(){
                public String sourceId(){return "independent-source";}
                public SourceWatermark watermark(){return new SourceWatermark(sourceId(),0);}
                public String topologyFingerprint(){return "independent";}
                public List<RuntimeFact> capture(){return List.of();}
                public Completeness completeness(){return Completeness.COMPLETE;}
                public void attach(java.util.function.Consumer<RuntimeEvent> observer){}
                public void close(){}
                public void poll()throws Exception {
                    int n=polls.incrementAndGet();
                    if(n==1||mutatingBoard.get()){new CartagoBasicContext("auction-bidder-"+n);if(!mutatingBoard.get())Thread.sleep(1100);}
                }
            };
            var published=new AtomicInteger();
            JaCaMoBridgePlatform.publishStepBoundary(source,List.of(independent),List.of(),published::incrementAndGet);
            assertEquals(1,polls.get());assertEquals(1,published.get());
            var cut=CartagoSourceCut.require(events.getLast());assertEquals(1,cut.validationAttempts());
            assertFalse(JaCaMoBridgePlatform.refreshesCartagoState(events.getLast()),"A source cut must not schedule itself indefinitely");
            assertTrue(events.stream().anyMatch(JaCaMoBridgePlatform::refreshesCartagoState),"Physical callbacks require a fresh selected-scope proof after their burst");
            assertTrue(cut.facts().stream().anyMatch(f->f.kind()==RuntimeFactKind.AGENT&&"auction-bidder-1".equals(f.values().get("name"))));
            mutatingBoard.set(true);
            var unstable=assertThrows(IllegalStateException.class,()->JaCaMoBridgePlatform.publishStepBoundary(source,List.of(),List.of(independent),published::incrementAndGet));
            assertTrue(unstable.getMessage().contains("CARTAGO_BOUNDARY_CUT_NOT_STABLE"));
            assertTrue(unstable.getMessage().contains("topologyChanged=true"));
            assertEquals(1,published.get(),"A genuine mutation inside the coherent interval must not publish a COMPLETE cut");
        }finally{environment.shutdown();
            if(revision==null)System.clearProperty("jacamo.bridge.modelRevision");else System.setProperty("jacamo.bridge.modelRevision",revision);
            if(generation==null)System.clearProperty("jacamo.bridge.generation");else System.setProperty("jacamo.bridge.generation",generation);
        }
    }
}
