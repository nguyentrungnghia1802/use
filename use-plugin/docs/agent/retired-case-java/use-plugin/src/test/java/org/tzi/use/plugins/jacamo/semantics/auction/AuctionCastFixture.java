package org.tzi.use.plugins.jacamo.semantics.auction;

import java.util.*;
import cartago.*;
import cartago.util.agent.CartagoBasicContext;
import org.jacamo.bridge.adapter.*;
import org.tzi.use.plugins.jacamo.codegrounded.CodeGroundedNativePipeline;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.NativeRuntimeProjector;
import org.tzi.use.plugins.jacamo.codegrounded.use.NativeProjectionMode;
import org.tzi.use.plugins.jacamo.semantics.mapping.SemanticProgram;
import org.tzi.use.plugins.jacamo.semantics.mapping.auction.*;

/** Four real role/mission players on two real schemes. Source-level Jason
 * execution is deliberately absent; this fixture cannot certify a Task. */
public final class AuctionCastFixture implements AutoCloseable {
    private final String previous=System.getProperty(AuctionSchemeObservation.PROPERTY);
    final AuctionOrgFixture org;final List<CartagoBasicContext> participants=new ArrayList<>();
    final List<ArtifactId> schemes=new ArrayList<>(),artifacts=new ArrayList<>();
    final ArtifactId group;final SemanticProgram program;final NativeRuntimeProjector projector;
    private final AuctionClockObservation clock;private final List<org.jacamo.bridge.contract.VerificationObservation> clocks=new ArrayList<>();private long clockSequence;
    public AuctionCastFixture(int count)throws Exception {
        this(count,count);
    }
    public AuctionCastFixture(int count,int committed)throws Exception {this(count,committed,false);}
    public AuctionCastFixture(int count,int committed,boolean normative)throws Exception {this(count,committed,normative,model->{});}
    /** Test-authored native contracts are installed before the runtime owner is
     * constructed; the canonical source/model/oracles remain unchanged. */
    AuctionCastFixture(int count,int committed,boolean normative,java.util.function.Consumer<org.tzi.use.uml.mm.MModel> prepare)throws Exception {
        System.setProperty(AuctionSchemeObservation.PROPERTY,"true");org=new AuctionOrgFixture(normative);var live=org.live;
        clock=normative?new AuctionClockObservation(live.run.id(),live.run.generation(),live.source.modelRevision(),clocks::add):null;if(clock!=null)clock.pulse(false);
        org.organizationBoard();group=org.group();
        var names=List.of("alice","maria","francois","giacomo");
        for(int i=0;i<count;i++){var actor=AuctionMonitorProjectionTest.participant(org,group);participants.add(actor);CslRuntimeTestRegistry.bind(live.source.semanticContract(),actor.getName(),names.get(i));}
        for(String name:List.of("castOne","castTwo")) {
            var scheme=org.scheme(name,"service("+name+")");schemes.add(scheme);artifacts.add(live.auction(name));live.actor.doAction(group,new Op("addScheme",scheme.getName()),5000);
            var board=live.boards.getLast();AuctionMonitorProjectionTest.await(()->board.getSchState().getGroupsResponsibleFor().stream().anyMatch(g->g.hasPlayer(live.actor.getName(),"auctioneer")&&participants.stream().allMatch(p->g.hasPlayer(p.getName(),"participant"))));
            live.actor.doAction(scheme,new Op("commitMission","mAuctioneer"),5000);for(int i=0;i<committed;i++)participants.get(i).doAction(scheme,new Op("commitMission","mParticipant"),5000);
        }
        program=new AuctionStaticMappingAdapter().map(live.source,new AuctionStaticMappingAdapter.Selection(live.bundle,live.specification));
        var pipeline=new CodeGroundedNativePipeline().buildCanonicalProgram(live.source,NativeProjectionMode.AUTO,program);prepare.accept(pipeline.binding().model());projector=new NativeRuntimeProjector(pipeline,live.run.id(),live.run.generation(),live.source.modelRevision());resync();
    }
    public void resync(){projector.applySnapshot(cut(),org.live.run.id(),org.live.run.generation());}
    public void disposeArtifact(int index)throws Exception {org.live.dispose(artifacts.get(index));}
    public org.jacamo.bridge.contract.RuntimeSnapshot cut(){var cut=org.cut();if(clock==null)return cut;clock.pulse(false);return AuctionObligationProjectionTest.withClock(cut,clocks.getLast(),org.live,++clockSequence);}
    public NativeRuntimeProjector projector(){return projector;}
    public SemanticProgram program(){return program;}
    public AuctionArtifactBindingResolver.Run run(){return org.live.run;}
    public List<org.jacamo.bridge.contract.RuntimeEvent> eventsSince(Map<String,Long> marks){return List.copyOf(org.live.events).stream().filter(e->e.sourceSequence()>marks.getOrDefault(e.sourceId(),-1L)).toList();}
    public void startAndEnableBid(int index)throws Exception {org.live.actor.doAction(artifacts.get(index),new Op("start","actual fixture service"),5000);org.live.actor.doAction(schemes.get(index),new Op("goalAchieved","start"),5000);}
    public void commitParticipant(int participant,int scheme)throws Exception {participants.get(participant).doAction(schemes.get(scheme),new Op("commitMission","mParticipant"),5000);}
    public void completeBid(int participant,int scheme)throws Exception {participants.get(participant).doAction(schemes.get(scheme),new Op("goalAchieved","bid"),5000);}
    public void additionalScheme(String name)throws Exception {
        var scheme=org.scheme(name,"service("+name+")");schemes.add(scheme);artifacts.add(org.live.auction(name));org.live.actor.doAction(group,new Op("addScheme",scheme.getName()),5000);
        var board=org.live.boards.getLast();AuctionMonitorProjectionTest.await(()->board.getSchState().getGroupsResponsibleFor().stream().anyMatch(g->g.hasPlayer(org.live.actor.getName(),"auctioneer")&&participants.stream().allMatch(p->g.hasPlayer(p.getName(),"participant"))));
        org.live.actor.doAction(scheme,new Op("commitMission","mAuctioneer"),5000);for(var actor:participants)actor.doAction(scheme,new Op("commitMission","mParticipant"),5000);
    }
    @Override public void close()throws Exception {
        try{projector.close();if(clock!=null)clock.close();for(var actor:participants)CslRuntimeTestRegistry.closeCartagoContext(actor);org.close();}
        finally{if(previous==null)System.clearProperty(AuctionSchemeObservation.PROPERTY);else System.setProperty(AuctionSchemeObservation.PROPERTY,previous);}
    }
}
