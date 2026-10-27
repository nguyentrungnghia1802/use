package org.tzi.use.plugins.jacamo.semantics.auction;

import java.util.*;
import cartago.*;
import org.jacamo.bridge.adapter.*;
import org.jacamo.bridge.contract.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.tzi.use.plugins.jacamo.codegrounded.CodeGroundedNativePipeline;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.NativeRuntimeProjector;
import org.tzi.use.plugins.jacamo.codegrounded.use.NativeProjectionMode;
import org.tzi.use.plugins.jacamo.semantics.mapping.xml.GenericMappingEngine;
import org.tzi.use.plugins.jacamo.semantics.mapping.xml.MappingInputs;
import org.tzi.use.plugins.jacamo.semantics.ocl.ConstraintCoverageResolver;
import org.tzi.use.semantic.engine.use75.*;
import org.tzi.use.uml.ocl.value.*;
import org.tzi.use.uml.sys.MObject;
import static org.junit.jupiter.api.Assertions.*;
import static org.tzi.use.plugins.jacamo.semantics.auction.AuctionDeclarativeObserverTest.*;

/** Real unmodified ten-second NPL deadline; generic XML/native component gate, not MAS/TCP acceptance. */
class AuctionDeclarativeObligationTest {
    @ParameterizedTest(name="authored={0}") @ValueSource(booleans={false,true}) @Timeout(75)
    void xmlProjectsActualActivationDeadlineFailureAndAdminRecoveryWithoutManufacturingABid(boolean authored)throws Exception {
        try(var f=new AuctionOrgFixture(true)) {
            var root=AuctionDeclarativeSpecificationTest.CASE;var selected=root.resolve("authored-v1");
            var inputs=authored?MappingInputs.read(root.resolve("auction.jcm"),selected.resolve("auction-mapping.xml"),selected.resolve("auction_constraints_final_fixed.ocl"),selected.resolve("auction_csl.csl"),selected.resolve("auction_istar_final.istar")):AuctionDeclarativeSpecificationTest.inputs(root);
            var live=f.live;var program=new GenericMappingEngine().map(live.source,inputs);
            var requests=program.xmlPlan().requirements();var frames=new ArrayList<VerificationObservation>();
            var context=new FrameworkObservationProviders.Context(live.environment,live.source,AuctionDeclarativeSpecificationTest.CASE.toRealPath(),live.run.id(),live.run.generation(),frames::add,ignored->{});
            var workspace=request(requests,"workspace-operations");var field=request(requests,"candidate-field");
            try(var history=new CartagoOperationHistory(workspace,context);var fields=new ArtifactFieldObservation(live.environment,live.source,field,context.root());
                var driver=new AuctionDeclarativeArtifactTest.SourceDriver(f,request(requests,"execution-steps"),frames);var capture=new Capture(f,context,requests,history,frames)) {
                f.organizationBoard();var group=f.group();var participant=AuctionMonitorProjectionTest.participant(f,group);
                try(var participantBinding=CslRuntimeTestRegistry.bind(live.source.semanticContract(),participant.getName(),"alice")) {
                    var scheme=f.scheme("xmlDeadline","delivery");live.actor.doAction(group,new Op("addScheme",scheme.getName()),5000);
                    AuctionMonitorProjectionTest.await(()->live.boards.getFirst().getSchState().getGroupsResponsibleFor().stream().anyMatch(g->g.hasPlayer(participant.getName(),"participant")));
                    live.actor.doAction(scheme,new Op("commitMission","mAuctioneer"),5000);participant.doAction(scheme,new Op("commitMission","mParticipant"),5000);driver.start(scheme,"xmlDeadline","delivery");
                    var built=new CodeGroundedNativePipeline().buildCanonicalProgram(live.source,NativeProjectionMode.AUTO,program);
                    try(var projector=new NativeRuntimeProjector(built,live.run.id(),live.run.generation(),live.source.modelRevision())) {
                        live.actor.doAction(scheme,new Op("goalAchieved","start"),5000);
                        AuctionMonitorProjectionTest.await(()->live.events.stream().anyMatch(e->e.kind()==RuntimeEventKind.NORM_ACTIVATED&&Objects.toString(e.after().get("aim")).contains(",bid,")));
                        projector.applySnapshot(capture.cut(frames,driver.identity,workspace,history,field,fields),live.run.id(),live.run.generation());var system=projector.system();var duty=duty(projector,"bid");
                        assertEquals(BooleanValue.FALSE,value(projector,duty,"unfulfilled"));assertEquals(IntegerValue.valueOf(-1),value(projector,duty,"endedAt"));
                        var created=(IntegerValue)value(projector,duty,"activatedAt");var deadline=(IntegerValue)value(projector,duty,"deadlineAt");assertTrue(Math.abs(deadline.value()-created.value()-10_000)<1000);
                        assertEquals(1,system.state().allLinks().stream().filter(l->l.association().name().equals("Observer_view_obligation_monitor")&&l.linkedObjects().getFirst()==duty&&l.linkedObjects().get(1)==monitor(projector,f,scheme)).count());
                        assertFalse(duty.cls().isSubClassifierOf(system.model().getClass("DomainEntity")));
                        long limit=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(16);
                        while(System.nanoTime()<limit&&live.events.stream().noneMatch(e->e.kind()==RuntimeEventKind.NORM_UNFULFILLED&&Objects.toString(e.after().get("aim")).contains(",bid,")))Thread.sleep(75);
                        assertTrue(live.events.stream().anyMatch(e->e.kind()==RuntimeEventKind.NORM_UNFULFILLED&&Objects.toString(e.after().get("aim")).contains(",bid,")));
                        projector.applySnapshot(capture.cut(frames,driver.identity,workspace,history,field,fields),live.run.id(),live.run.generation());
                        assertEquals(BooleanValue.TRUE,value(projector,duty,"unfulfilled"));var ended=value(projector,duty,"endedAt");assertInstanceOf(IntegerValue.class,ended);
                        live.actor.doAction(scheme,new Op("admCommand","goalSatisfied(bid)"),5000);
                        projector.applySnapshot(capture.cut(frames,driver.identity,workspace,history,field,fields),live.run.id(),live.run.generation());
                        assertEquals(ended,value(projector,duty,"endedAt"));assertEquals(BooleanValue.TRUE,value(projector,duty,"unfulfilled"));
                        var monitor=monitor(projector,f,scheme);assertEquals(BooleanValue.TRUE,value(projector,monitor,"adminBidOverride"));assertEquals(Set.of(),names(value(projector,monitor,"actualBidBy")));assertEquals(Set.of(),names(value(projector,monitor,"bidDoneBy")));
                        var decide=duty(projector,"decide");assertTrue(Math.abs(((IntegerValue)value(projector,decide,"deadlineAt")).value()-((IntegerValue)value(projector,decide,"activatedAt")).value()-3_600_000)<1000);
                        try(var ocl=new NativeOclBackend(system.model(),"xml-npl","generic")) {
                            var clause=built.xmlPreparation().authored().clauses().stream().map(c->c.specification()).filter(c->c.name().equals("WithinDeadline")).findFirst().orElseThrow();
                            var compiled=ocl.compile(new OclContractSpec("actual-deadline",OclContractSpec.Kind.INVARIANT,duty.cls(),null,clause.body()));
                            assertEquals("",new ConstraintCoverageResolver().unavailableSelf(compiled.expression(),projector.mutations(),duty,Map.of(),false));
                            assertEquals(NativeOclOutcome.Status.FAIL,ocl.evaluate(compiled,UseStateReadView.pin(null,system.state()),Map.of("self",duty.value()),"").status());
                            var current=capture.cut(frames,driver.identity,workspace,history,field,fields);
                            var noClock=AuctionDeclarativeArtifactTest.copy(current,current.facts().stream().filter(fact->!fact.values().containsKey(VerificationObservation.KEY)||VerificationObservation.require(fact,current,live.run.id(),live.run.generation()).kind()!=VerificationObservation.Kind.CLOCK).toList());
                            projector.applySnapshot(noClock,live.run.id(),live.run.generation());
                            assertTrue(value(projector,duty,"deadlineAt").isUndefined());assertTrue(value(projector,monitor,"now").isUndefined());assertEquals(BooleanValue.TRUE,value(projector,duty,"unfulfilled"),"Source failure history is independent of current clock availability");
                            var unavailable=new ConstraintCoverageResolver().unavailableSelf(compiled.expression(),projector.mutations(),duty,Map.of(),false);assertFalse(unavailable.isBlank());
                            assertEquals(NativeOclOutcome.Status.SKIPPED,ocl.evaluate(compiled,UseStateReadView.pin(null,system.state()),Map.of("self",duty.value()),unavailable).status());
                            var missingNormCut=capture.cut(frames,driver.identity,workspace,history,field,fields);
                            projector.applySnapshot(AuctionDeclarativeArtifactTest.copy(missingNormCut,missingNormCut.facts().stream().filter(fact->fact.kind()!=RuntimeFactKind.NORM_INSTANCE).toList()),live.run.id(),live.run.generation());
                            assertTrue(value(projector,decide,"unfulfilled").isUndefined(),"Omitted active activation is unknown, not a newly observed false");
                            assertTrue(value(projector,duty,"endedAt").isUndefined());assertEquals(BooleanValue.TRUE,value(projector,duty,"unfulfilled"),"Positive terminal evidence stays latched independently");
                        }
                        assertSame(system,projector.system());assertTrue(capture.errors.isEmpty(),capture.errors.toString());
                    }
                }finally{CslRuntimeTestRegistry.closeCartagoContext(participant);}
            }
        }
    }
    static MObject duty(NativeRuntimeProjector projector,String goal) {
        return projector.system().state().allObjects().stream().filter(o->o.cls().name().equals("AuctionObligation")&&new StringValue(goal).equals(value(projector,o,"goal"))).findFirst().orElseThrow(()->new AssertionError("No qualified XML obligation for "+goal));
    }
    static Value value(NativeRuntimeProjector projector,MObject object,String field){return object.state(projector.system().state()).attributeValue(field);}
}
