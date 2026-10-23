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
import org.tzi.use.uml.ocl.value.*;
import static org.junit.jupiter.api.Assertions.*;

class AuctionObligationEventTest {
    @Test void actualFulfillmentFlowsThroughRawAndTypedEventsAndAnOlderCopyCannotRewindTheNormCut()throws Exception {
        String old=System.getProperty(AuctionSchemeObservation.PROPERTY);System.setProperty(AuctionSchemeObservation.PROPERTY,"true");
        try(var fixture=new AuctionOrgFixture(true);var wire=new VerificationObservationSource(fixture.live.run.id(),fixture.live.run.generation(),fixture.live.source.modelRevision())) {
            var live=fixture.live;wire.attach(live.events::add);var clocks=new ArrayList<VerificationObservation>();
            try(var clock=new AuctionClockObservation(live.run.id(),live.run.generation(),live.source.modelRevision(),clocks::add)) {
                clock.pulse(false);fixture.organizationBoard();var group=fixture.group();var scheme=fixture.scheme("eventsNorm","service");live.auction("eventsNorm");var participant=AuctionMonitorProjectionTest.participant(fixture,group);
                CslRuntimeTestRegistry.bind(live.source.semanticContract(),participant.getName(),"alice");
                try {
                    var program=new AuctionStaticMappingAdapter().map(live.source,new AuctionStaticMappingAdapter.Selection(live.bundle,live.specification));var pipeline=new CodeGroundedNativePipeline().buildCanonicalProgram(live.source,NativeProjectionMode.AUTO,program);
                    live.actor.doAction(group,new Op("addScheme",scheme.getName()),5000);var board=live.boards.getFirst();AuctionMonitorProjectionTest.await(()->board.getSchState().getGroupsResponsibleFor().stream().anyMatch(g->g.hasPlayer(participant.getName(),"participant")));
                    live.actor.doAction(scheme,new Op("commitMission","mAuctioneer"),5000);participant.doAction(scheme,new Op("commitMission","mParticipant"),5000);live.actor.doAction(scheme,new Op("goalAchieved","start"),5000);
                    AuctionMonitorProjectionTest.await(()->live.events.stream().filter(VerificationObservation::present).map(VerificationObservation::require).anyMatch(o->o.kind()==VerificationObservation.Kind.NPL_LIFECYCLE&&o.data().get("state").equals("ACTIVE")&&CanonicalJson.object(o.data().get("sourceObservation")).get("aim").toString().contains(",bid,")));
                    var initial=fixture.cut();
                    // Clock uses a separate test-owned capture; include it as
                    // the next real typed observation on the same observer lane.
                    wire.publish(clocks.getLast(),"","");var facts=new ArrayList<>(initial.facts());facts.addAll(wire.capture());var marks=new TreeMap<>(initial.endWatermarks());marks.put(wire.sourceId(),wire.watermark());
                    var baseline=new RuntimeSnapshot("norm-event-clock-baseline",initial.modelRevision(),initial.captureStartedAt(),initial.captureEndedAt(),marks,marks,1,facts,initial.sourceCompleteness(),"actual-norm-event-clock-baseline");
                    try(var projector=new NativeRuntimeProjector(pipeline,live.run.id(),live.run.generation(),live.source.modelRevision())) {
                        projector.applySnapshot(baseline,live.run.id(),live.run.generation());var system=projector.system();var obligation=system.state().allObjects().stream().filter(o->o.cls().name().equals("AuctionObligation")).findFirst().orElseThrow();String identity=projector.mutations().metadata(obligation,"semanticId");
                        participant.doAction(scheme,new Op("goalAchieved","bid"),5000);
                        AuctionMonitorProjectionTest.await(()->live.events.stream().filter(VerificationObservation::present).map(VerificationObservation::require).anyMatch(o->o.kind()==VerificationObservation.Kind.NPL_LIFECYCLE&&o.subject().equals(identity)&&o.data().get("state").equals("FULFILLED")));
                        for(var event:List.copyOf(live.events))if(event.sourceSequence()>marks.getOrDefault(event.sourceId(),new SourceWatermark(event.sourceId(),0)).sequence())projector.apply(event);
                        assertFalse(projector.resyncRequired());assertSame(system,projector.system());assertEquals("FULFILLED",projector.mutations().metadata(obligation,"normReportedState"));
                        assertEquals(BooleanValue.FALSE,obligation.state(system.state()).attributeValue("unfulfilled"));assertFalse(obligation.state(system.state()).attributeValue("endedAt").isUndefined());
                        var oldCopy=live.events.stream().filter(VerificationObservation::present).filter(e->{var o=VerificationObservation.require(e);return o.kind()==VerificationObservation.Kind.NPL_LIFECYCLE&&o.subject().equals(identity)&&o.data().get("state").equals("ACTIVE");}).findFirst().orElseThrow();
                        var current=projector.evidence().get(identity);var folded=AuctionObservationCut.advance(Map.of(identity,current),List.of(),oldCopy);
                        assertEquals("FULFILLED",folded.facts().get(identity).values().get("state"));
                        var monitor=AuctionMonitorProjectionTest.monitor(projector,"sch_eventsNorm");assertEquals(Set.of(participant.getName()),AuctionMonitorProjectionTest.names(monitor.state(system.state()).attributeValue("bidDoneBy")));assertEquals(Set.of(),AuctionMonitorProjectionTest.names(monitor.state(system.state()).attributeValue("actualBidBy")));
                    }
                }finally{CslRuntimeTestRegistry.closeCartagoContext(participant);}
            }
        }finally{if(old==null)System.clearProperty(AuctionSchemeObservation.PROPERTY);else System.setProperty(AuctionSchemeObservation.PROPERTY,old);}
    }
}
