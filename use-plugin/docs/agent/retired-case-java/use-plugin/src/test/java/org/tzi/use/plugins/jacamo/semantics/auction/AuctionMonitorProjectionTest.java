package org.tzi.use.plugins.jacamo.semantics.auction;

import cartago.*;
import cartago.util.agent.CartagoBasicContext;
import java.util.*;
import org.jacamo.bridge.adapter.*;
import org.jacamo.bridge.contract.*;
import org.junit.jupiter.api.Test;
import org.tzi.use.plugins.jacamo.codegrounded.*;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.*;
import org.tzi.use.plugins.jacamo.codegrounded.use.NativeProjectionMode;
import org.tzi.use.plugins.jacamo.semantics.mapping.auction.*;
import org.tzi.use.plugins.jacamo.semantics.ocl.ConstraintCoverageResolver;
import org.tzi.use.semantic.engine.use75.*;
import org.tzi.use.uml.ocl.value.*;
import static org.junit.jupiter.api.Assertions.*;

class AuctionMonitorProjectionTest {
    @Test void actualDoneWithoutABidAndFirstSatisfactionFreezeRequireCurrentPlayerEvidence()throws Exception {
        String previous=System.getProperty(AuctionSchemeObservation.PROPERTY);System.setProperty(AuctionSchemeObservation.PROPERTY,"true");
        try(var fixture=new AuctionOrgFixture()) {
            var live=fixture.live;fixture.organizationBoard();var group=fixture.group();var scheme=fixture.scheme("freeze","service");live.auction("freeze");
            var first=participant(fixture,group);var later=participant(fixture,group);
            // Same adapter-boundary registration used by AuctionOrgFixture;
            // these contexts do not claim to be a running Jason MAS.
            CslRuntimeTestRegistry.bind(live.source.semanticContract(),first.getName(),"alice");
            CslRuntimeTestRegistry.bind(live.source.semanticContract(),later.getName(),"maria");
            try {
                live.actor.doAction(group,new Op("addScheme",scheme.getName()),5000);
                var board=live.boards.getFirst();await(()->board.getSchState().getGroupsResponsibleFor().stream().anyMatch(g->g.hasPlayer(first.getName(),"participant")));
                live.actor.doAction(scheme,new Op("commitMission","mAuctioneer"),5000);first.doAction(scheme,new Op("commitMission","mParticipant"),5000);
                var program=new AuctionStaticMappingAdapter().map(live.source,new AuctionStaticMappingAdapter.Selection(live.bundle,live.specification));
                var pipeline=new CodeGroundedNativePipeline().buildCanonicalProgram(live.source,NativeProjectionMode.AUTO,program);
                try(var projector=new NativeRuntimeProjector(pipeline,live.run.id(),live.run.generation(),live.source.modelRevision())) {
                    projector.applySnapshot(fixture.cut(),live.run.id(),live.run.generation());var monitor=monitor(projector,"sch_freeze");
                    assertEquals(Set.of(first.getName()),names(monitor.state(projector.system().state()).attributeValue("bidCommittedBy")));
                    live.actor.doAction(scheme,new Op("goalAchieved","start"),5000);first.doAction(scheme,new Op("goalAchieved","bid"),5000);
                    later.doAction(scheme,new Op("commitMission","mParticipant"),5000);
                    var cut=fixture.cut();projector.applySnapshot(cut,live.run.id(),live.run.generation());
                    assertTrue(board.getSchState().getPlayers().stream().anyMatch(p->p.getAg().equals(later.getName())),"Real later mission commitment must be present");
                    assertEquals(Set.of(first.getName()),names(monitor.state(projector.system().state()).attributeValue("bidCommittedBy")),"Freeze the actual first satisfaction, not a later poll population");
                    assertEquals(Set.of(first.getName()),names(monitor.state(projector.system().state()).attributeValue("bidDoneBy")));
                    assertEquals(Set.of(),names(monitor.state(projector.system().state()).attributeValue("actualBidBy")),"MOISE done is not evidence of an Artifact bid");
                    var facts=cut.facts().stream().filter(f->f.kind()!=RuntimeFactKind.AGENT||!first.getName().equals(f.values().get("name"))).toList();
                    var partial=new RuntimeSnapshot("missing-current-player",cut.modelRevision(),cut.captureStartedAt(),cut.captureEndedAt(),cut.startWatermarks(),cut.endWatermarks(),1,facts,Map.of("cartago",Completeness.PARTIAL),"test-player-omission");
                    projector.applySnapshot(partial,live.run.id(),live.run.generation());
                    assertTrue(monitor.state(projector.system().state()).attributeValue("bidDoneBy").isUndefined(),"Retained native player is not current-cut identity evidence");
                    assertTrue(monitor.state(projector.system().state()).attributeValue("bidCommittedBy").isUndefined());
                }
            }finally{CslRuntimeTestRegistry.closeCartagoContext(first);CslRuntimeTestRegistry.closeCartagoContext(later);}
        }finally{if(previous==null)System.clearProperty(AuctionSchemeObservation.PROPERTY);else System.setProperty(AuctionSchemeObservation.PROPERTY,previous);}
    }
    static CartagoBasicContext participant(AuctionOrgFixture fixture,ArtifactId group)throws Exception {
        var actor=new CartagoBasicContext("participant"+UUID.randomUUID().toString().replace("-",""));var joined=new OpFeedbackParam<WorkspaceId>();
        actor.doAction(new Op("joinWorkspace",fixture.organization.getFullName(),joined),actor.getJoinedWspId("main"),5000);assertEquals(fixture.organization,joined.get());
        actor.doAction(group,new Op("adoptRole","participant"),5000);return actor;
    }
    static void await(java.util.function.BooleanSupplier condition)throws Exception {await(condition,5000);}
    static void await(java.util.function.BooleanSupplier condition,long timeoutMillis)throws Exception {long limit=System.nanoTime()+java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(timeoutMillis);while(!condition.getAsBoolean()&&System.nanoTime()<limit)Thread.sleep(10);assertTrue(condition.getAsBoolean());}
    @Test void originalSuccessfulLosingBidFailedBidAndAdminStayDistinctInOneNativeState()throws Exception {
        String previous=System.getProperty(AuctionSchemeObservation.PROPERTY);System.setProperty(AuctionSchemeObservation.PROPERTY,"true");
        try(var fixture=new AuctionOrgFixture()) {
            var live=fixture.live;fixture.organizationBoard();fixture.group();var one=fixture.scheme("a1","service(one)");fixture.scheme("a2","service(two)");
            var artifact=live.auction("a1");live.auction("a2");var other=new CartagoBasicContext("nonWinning"+UUID.randomUUID().toString().replace("-",""));
            try {
                var program=new AuctionStaticMappingAdapter().map(live.source,new AuctionStaticMappingAdapter.Selection(live.bundle,live.specification));
                var pipeline=new CodeGroundedNativePipeline().buildCanonicalProgram(live.source,NativeProjectionMode.AUTO,program);
                try(var projector=new NativeRuntimeProjector(pipeline,live.run.id(),live.run.generation(),live.source.modelRevision())) {
                    projector.applySnapshot(fixture.cut(),live.run.id(),live.run.generation());var system=projector.system();var first=monitor(projector,"sch_a1");var second=monitor(projector,"sch_a2");
                    assertEquals(BooleanValue.FALSE,first.state(system.state()).attributeValue("adminBidOverride"));assertEquals(Set.of(),names(first.state(system.state()).attributeValue("actualBidBy")));
                    live.actor.doAction(artifact,new Op("start","source service"),5000);live.actor.doAction(artifact,new Op("bid",42.5),5000);other.doAction(artifact,new Op("bid",100.0),5000);
                    projector.applySnapshot(fixture.cut(),live.run.id(),live.run.generation());assertSame(system,projector.system());assertSame(first,monitor(projector,"sch_a1"));
                    assertEquals(Set.of(live.actor.getName(),other.getName()),names(first.state(system.state()).attributeValue("actualBidBy")),"Actual losing success is evidence even when price is unchanged");
                    assertEquals(Set.of(),names(second.state(system.state()).attributeValue("actualBidBy")));assertEquals(Set.of(),names(first.state(system.state()).attributeValue("bidDoneBy")));
                    live.actor.doAction(artifact,new Op("stop"),5000);assertThrows(cartago.util.agent.ActionFailedException.class,()->other.doAction(artifact,new Op("bid",1.0),5000));
                    live.actor.doAction(one,new Op("admCommand","goalSatisfied(bid)"),5000);projector.applySnapshot(fixture.cut(),live.run.id(),live.run.generation());
                    assertEquals(BooleanValue.TRUE,first.state(system.state()).attributeValue("adminBidOverride"));assertEquals(BooleanValue.FALSE,second.state(system.state()).attributeValue("adminBidOverride"));
                    assertEquals(Set.of(),names(first.state(system.state()).attributeValue("bidDoneBy")));assertEquals(Set.of(live.actor.getName(),other.getName()),names(first.state(system.state()).attributeValue("actualBidBy")));
                    assertTrue(first.state(system.state()).attributeValue("bidSatisfiedAt").isUndefined(),"Public callback time cannot fill an XML internal-engine-time field");
                    try(var ocl=new NativeOclBackend(system.model(),"N02","AUCTION_XML/1.0")) {
                        var expression=ocl.compile(new OclContractSpec("internal-time",OclContractSpec.Kind.INVARIANT,first.cls(),null,"self.bidSatisfiedAt >= 0"));
                        assertTrue(new ConstraintCoverageResolver().unavailableSelf(expression.expression(),projector.mutations(),first,Map.of("cartago",Completeness.COMPLETE),false).contains("INTERNAL_TRANSITION_TIME_UNAVAILABLE"));
                    }
                    assertTrue(projector.mutations().metadata(first,"auctionMonitorSource").contains("PUBLICATION_ONLY_INTERNAL_TRANSITION_TIME_UNAVAILABLE"));
                }
            }finally{CslRuntimeTestRegistry.closeCartagoContext(other);}
        }finally{if(previous==null)System.clearProperty(AuctionSchemeObservation.PROPERTY);else System.setProperty(AuctionSchemeObservation.PROPERTY,previous);}
    }
    static org.tzi.use.uml.sys.MObject monitor(NativeRuntimeProjector projector,String scheme){return projector.system().state().allObjects().stream().filter(o->o.cls().name().equals("AuctionMonitor")&&BridgeEntityId.parse(projector.mutations().metadata(o,"auctionMonitorScheme")).localId().equals(scheme)).findFirst().orElseThrow();}
    static Set<String> names(Value value){assertFalse(value.isUndefined());return ((SetValue)value).collection().stream().map(v->((StringValue)v).value()).collect(java.util.stream.Collectors.toSet());}
}
