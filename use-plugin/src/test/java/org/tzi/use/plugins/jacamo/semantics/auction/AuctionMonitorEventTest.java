package org.tzi.use.plugins.jacamo.semantics.auction;

import java.util.*;
import cartago.Op;
import org.jacamo.bridge.adapter.*;
import org.jacamo.bridge.contract.*;
import org.junit.jupiter.api.Test;
import org.tzi.use.plugins.jacamo.codegrounded.CodeGroundedNativePipeline;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.NativeRuntimeProjector;
import org.tzi.use.plugins.jacamo.codegrounded.use.NativeProjectionMode;
import org.tzi.use.plugins.jacamo.semantics.mapping.auction.*;
import org.tzi.use.uml.ocl.value.BooleanValue;
import static org.junit.jupiter.api.Assertions.*;

class AuctionMonitorEventTest {
    @Test void mixedRawAndTypedEventsRetainOneMonitorWithoutAnotherSnapshot()throws Exception {
        String old=System.getProperty(AuctionSchemeObservation.PROPERTY);System.setProperty(AuctionSchemeObservation.PROPERTY,"true");
        try(var fixture=new AuctionOrgFixture();var observer=new VerificationObservationSource(fixture.live.run.id(),fixture.live.run.generation(),fixture.live.source.modelRevision())) {
            var live=fixture.live;observer.attach(live.events::add);fixture.organizationBoard();fixture.group();var scheme=fixture.scheme("events","service");var artifact=live.auction("events");
            var program=new AuctionStaticMappingAdapter().map(live.source,new AuctionStaticMappingAdapter.Selection(live.bundle,live.specification));
            var pipeline=new CodeGroundedNativePipeline().buildCanonicalProgram(live.source,NativeProjectionMode.AUTO,program);
            try(var projector=new NativeRuntimeProjector(pipeline,live.run.id(),live.run.generation(),live.source.modelRevision())) {
                var baseline=fixture.cut();var facts=new ArrayList<>(baseline.facts());facts.addAll(observer.capture());var marks=new TreeMap<>(baseline.endWatermarks());marks.put(observer.sourceId(),observer.watermark());
                var cut=new RuntimeSnapshot("mixed-baseline",baseline.modelRevision(),baseline.captureStartedAt(),baseline.captureEndedAt(),marks,marks,1,facts,baseline.sourceCompleteness(),"actual-mixed-component");
                projector.applySnapshot(cut,live.run.id(),live.run.generation());var system=projector.system();var monitor=AuctionMonitorProjectionTest.monitor(projector,"sch_events");
                live.actor.doAction(artifact,new Op("start","event service"),5000);live.actor.doAction(artifact,new Op("bid",45.0),5000);
                live.actor.doAction(scheme,new Op("admCommand","goalSatisfied(bid)"),5000);
                var events=List.copyOf(live.events).stream().filter(e->e.sourceSequence()>marks.getOrDefault(e.sourceId(),new SourceWatermark(e.sourceId(),0)).sequence()).toList();
                assertTrue(events.stream().anyMatch(VerificationObservation::present));
                for(var event:events)projector.apply(event);
                assertFalse(projector.resyncRequired(),projector.evidence().toString());assertSame(system,projector.system());assertSame(monitor,AuctionMonitorProjectionTest.monitor(projector,"sch_events"));
                assertEquals(Set.of(live.actor.getName()),AuctionMonitorProjectionTest.names(monitor.state(system.state()).attributeValue("actualBidBy")));
                assertEquals(BooleanValue.TRUE,monitor.state(system.state()).attributeValue("adminBidOverride"));
                assertEquals(Set.of(),AuctionMonitorProjectionTest.names(monitor.state(system.state()).attributeValue("bidDoneBy")));
            }
        }finally{if(old==null)System.clearProperty(AuctionSchemeObservation.PROPERTY);else System.setProperty(AuctionSchemeObservation.PROPERTY,old);}
    }
}
