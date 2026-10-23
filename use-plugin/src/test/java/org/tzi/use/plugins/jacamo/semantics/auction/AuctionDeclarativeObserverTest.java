package org.tzi.use.plugins.jacamo.semantics.auction;

import java.util.*;
import cartago.*;
import org.jacamo.bridge.adapter.*;
import org.jacamo.bridge.contract.*;
import org.junit.jupiter.api.*;
import org.tzi.use.plugins.jacamo.codegrounded.CodeGroundedNativePipeline;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.NativeRuntimeProjector;
import org.tzi.use.plugins.jacamo.codegrounded.use.NativeProjectionMode;
import org.tzi.use.plugins.jacamo.semantics.mapping.xml.GenericMappingEngine;
import org.tzi.use.uml.ocl.value.*;
import org.tzi.use.uml.sys.MObject;
import static org.junit.jupiter.api.Assertions.*;

/** Actual callback/provider history and native observer projection; not a full MAS/TCP/Task acceptance. */
class AuctionDeclarativeObserverTest {
    @Test @Timeout(45) void sourceSchemeBindingsExistBeforeDomainCreationAndUseCurrentPhysicalResponsibleGroups()throws Exception {
        try(var f=new AuctionOrgFixture(true)) {
            var live=f.live;var program=new GenericMappingEngine().map(live.source,AuctionDeclarativeSpecificationTest.inputs(AuctionDeclarativeSpecificationTest.CASE));
            var requests=program.xmlPlan().requirements();var frames=new ArrayList<VerificationObservation>();
            var context=new FrameworkObservationProviders.Context(live.environment,live.source,AuctionDeclarativeSpecificationTest.CASE.toRealPath(),live.run.id(),live.run.generation(),frames::add,ignored->{});
            var workspace=request(requests,"workspace-operations");var field=request(requests,"candidate-field");
            try(var history=new CartagoOperationHistory(workspace,context);var fields=new ArtifactFieldObservation(live.environment,live.source,field,context.root());
                var driver=new AuctionDeclarativeArtifactTest.SourceDriver(f,request(requests,"execution-steps"),frames);var capture=new Capture(f,context,requests,history,frames)) {
                f.organizationBoard();var group=f.group();var scheme=f.scheme("scopeBeforeCreation","delivery");live.actor.doAction(group,new Op("addScheme",scheme.getName()),5000);live.actor.doAction(scheme,new Op("commitMission","mAuctioneer"),5000);
                var cut=capture.cut(frames,driver.identity,workspace,history,field,fields);
                assertTrue(cut.facts().stream().noneMatch(fact->fact.kind()==RuntimeFactKind.ARTIFACT&&"auction_env.AuctionArtifact".equals(fact.values().get("artifactTypeJavaClassName"))));
                var sources=new org.tzi.use.plugins.jacamo.semantics.mapping.xml.runtime.RuntimeFactSources(program.xmlPlan().inputs().specification(),live.source,requests);
                var row=sources.capture(cut,live.run.id(),live.run.generation()).get("source-scheme-history").rows().getFirst();
                var groupId=cut.facts().stream().filter(fact->fact.kind()==RuntimeFactKind.GROUP_BOARD&&group.getId().toString().equals(fact.values().get("boardArtifactIdentity"))).findFirst().orElseThrow().id().canonical();
                assertEquals(true,row.values().get("responsibilityComplete"));assertEquals(List.of(groupId),row.values().get("responsibleGroups"));
                var publicGoals=(List<?>)row.values().get("publicGoals");assertEquals(4,publicGoals.size());
                var start=publicGoals.stream().map(CanonicalJson::object).filter(g->g.get("name").equals("start")).findFirst().orElseThrow();
                var declaration=live.source.semanticContract().agentDeclarations().stream().filter(a->a.name().equals("bob")).findFirst().orElseThrow().metadata().semanticId();
                assertEquals(List.of(declaration),start.get("committed"),"BridgeRuntimeRegistry.projectedAgentId preserves a singleton declaration alias; it is not a new runtime incarnation");
                assertNotEquals(List.of(driver.identity.canonical()),start.get("committed"));
                var omitted=AuctionDeclarativeArtifactTest.copy(cut,cut.facts().stream().filter(fact->fact.kind()!=RuntimeFactKind.GROUP_BOARD).toList());
                var unknown=sources.capture(omitted,live.run.id(),live.run.generation()).get("source-scheme-history").rows().getFirst();assertEquals(false,unknown.values().get("responsibilityComplete"));assertFalse(unknown.values().containsKey("responsibleGroups"));
                assertEquals(Completeness.COMPLETE,unknown.quality(),"Missing responsibility must not erase independent qualified goal/history fields");
                var counterfeit=AuctionDeclarativeArtifactTest.copy(cut,cut.facts().stream().map(fact->{
                    if(fact.kind()!=RuntimeFactKind.GROUP_BOARD&&fact.kind()!=RuntimeFactKind.SCHEME_BOARD)return fact;
                    var data=new LinkedHashMap<>(fact.values());
                    if(fact.kind()==RuntimeFactKind.GROUP_BOARD)data.put("semanticId","foreign-declaration");else data.put("responsibleGroupSemanticIds",List.of("foreign-declaration"));
                    return new RuntimeFact(fact.id(),fact.kind(),data,fact.relations(),fact.projectionStatus(),fact.completeness(),fact.evidence());
                }).toList());
                assertThrows(IllegalArgumentException.class,()->sources.capture(counterfeit,live.run.id(),live.run.generation()),"Two matching source strings cannot counterfeit the frozen declaration-to-physical-group crosswalk");
            }
        }
    }
    @Test @Timeout(75) void xmlMonitorFreezesActualFirstCommitmentsAndKeepsDoneDistinctFromActualBids()throws Exception {
        try(var f=new AuctionOrgFixture(true)) {
            var live=f.live;var program=new GenericMappingEngine().map(live.source,AuctionDeclarativeSpecificationTest.inputs(AuctionDeclarativeSpecificationTest.CASE));
            var requests=program.xmlPlan().requirements();var frames=new ArrayList<VerificationObservation>();
            var context=new FrameworkObservationProviders.Context(live.environment,live.source,AuctionDeclarativeSpecificationTest.CASE.toRealPath(),live.run.id(),live.run.generation(),frames::add,ignored->{});
            var workspace=request(requests,"workspace-operations");var field=request(requests,"candidate-field");var steps=request(requests,"execution-steps");
            try(var history=new CartagoOperationHistory(workspace,context);var fields=new ArtifactFieldObservation(live.environment,live.source,field,AuctionDeclarativeSpecificationTest.CASE.toRealPath());
                var driver=new AuctionDeclarativeArtifactTest.SourceDriver(f,steps,frames);var capture=new Capture(f,context,requests,history,frames)) {
                f.organizationBoard();var group=f.group();var participant=AuctionMonitorProjectionTest.participant(f,group);var later=AuctionMonitorProjectionTest.participant(f,group);
                var participantId=new BridgeEntityId("jason","agent","runtime-agent",live.source.semanticContract().project().name(),participant.getName(),"participant-incarnation-one");
                var laterId=new BridgeEntityId("jason","agent","runtime-agent",live.source.semanticContract().project().name(),later.getName(),"later-incarnation-one");
                try(var participantBinding=CslRuntimeTestRegistry.bind(live.source.semanticContract(),participant.getName(),"alice",participantId);var laterBinding=CslRuntimeTestRegistry.bind(live.source.semanticContract(),later.getName(),"maria",laterId)) {
                    var scheme=f.scheme("monitorOne","delivery_one");var other=f.scheme("monitorTwo","delivery_two");
                    live.actor.doAction(group,new Op("addScheme",scheme.getName()),5000);
                    AuctionMonitorProjectionTest.await(()->live.boards.getFirst().getSchState().getGroupsResponsibleFor().stream().anyMatch(g->g.hasPlayer(participant.getName(),"participant")));
                    live.actor.doAction(scheme,new Op("commitMission","mAuctioneer"),5000);participant.doAction(scheme,new Op("commitMission","mParticipant"),5000);
                    driver.start(scheme,"monitorOne","delivery_one");driver.start(other,"monitorTwo","delivery_two");
                    var built=new CodeGroundedNativePipeline().buildCanonicalProgram(live.source,NativeProjectionMode.AUTO,program);
                    try(var projector=new NativeRuntimeProjector(built,live.run.id(),live.run.generation(),live.source.modelRevision())) {
                        projector.applySnapshot(capture.cut(frames,driver.identity,workspace,history,field,fields),live.run.id(),live.run.generation());var system=projector.system();
                        assertEquals(2,system.state().allObjects().stream().filter(o->o.cls().name().equals("AuctionMonitor")).count());
                        var monitor=monitor(projector,f,scheme);assertFalse(monitor.cls().isSubClassifierOf(system.model().getClass("DomainEntity")));
                        assertEquals(Set.of(participant.getName()),names(monitor.state(system.state()).attributeValue("bidCommittedBy")));
                        assertEquals(Set.of(),names(monitor.state(system.state()).attributeValue("bidDoneBy")));assertEquals(Set.of(),names(monitor.state(system.state()).attributeValue("actualBidBy")));
                        assertTrue(monitor.state(system.state()).attributeValue("now") instanceof IntegerValue);assertEquals(IntegerValue.valueOf(-1),monitor.state(system.state()).attributeValue("bidSatisfiedAt"));
                        live.actor.doAction(scheme,new Op("goalAchieved","start"),5000);participant.doAction(scheme,new Op("goalAchieved","bid"),5000);later.doAction(scheme,new Op("commitMission","mParticipant"),5000);
                        projector.applySnapshot(capture.cut(frames,driver.identity,workspace,history,field,fields),live.run.id(),live.run.generation());
                        assertEquals(Set.of(participant.getName()),names(monitor.state(system.state()).attributeValue("bidCommittedBy")),"First source satisfaction freezes before a later mission commitment");
                        assertEquals(Set.of(participant.getName()),names(monitor.state(system.state()).attributeValue("bidDoneBy")));assertEquals(Set.of(),names(monitor.state(system.state()).attributeValue("actualBidBy")),"An actual done fact cannot manufacture an Artifact bid");
                        assertTrue(monitor.state(system.state()).attributeValue("startSatisfiedAt").isUndefined(),"Publication time must not substitute for unavailable internal MOISE transition time");
                        var artifact=live.artifacts.stream().filter(a->a.getName().equals("monitorOne")).findFirst().orElseThrow();
                        participant.doAction(artifact,new Op("bid",9.0),5000);later.doAction(artifact,new Op("bid",12.0),5000);
                        projector.applySnapshot(capture.cut(frames,driver.identity,workspace,history,field,fields),live.run.id(),live.run.generation());
                        assertEquals(Set.of(participant.getName(),later.getName()),names(monitor.state(system.state()).attributeValue("actualBidBy")),"A successful non-improving bid still has actual operation evidence");
                        assertEquals(Set.of(),names(monitor(projector,f,other).state(system.state()).attributeValue("actualBidBy")),"An operation on another physical Auction cannot supply evidence for this scope");
                        assertSame(system,projector.system());assertTrue(capture.errors.isEmpty(),capture.errors.toString());
                        var raw=capture.schemes.capture(scheme,"SNAPSHOT",0,"",null,"");
                        var firstBid=((List<?>)raw.get("transitions")).stream().map(CanonicalJson::object).filter(t->t.get("goal").equals("bid")&&t.get("state").equals("SATISFIED")).findFirst().orElseThrow();
                        var frozen=CanonicalJson.object(firstBid.get("commitmentBindings"));assertEquals(true,frozen.get("complete"));
                        var participantRows=((List<?>)frozen.get("rows")).stream().map(CanonicalJson::object).filter(r->r.get("mission").equals("mParticipant")).toList();
                        assertEquals(List.of(participantId.canonical()),participantRows.stream().map(r->r.get("runtimePlayer")).toList());
                        var replacement=new BridgeEntityId("jason","agent","runtime-agent",participantId.scope(),participantId.localId(),"participant-incarnation-two");
                        try(var rebound=CslRuntimeTestRegistry.bind(live.source.semanticContract(),participant.getName(),"alice",replacement)) {
                            var repeated=capture.schemes.capture(scheme,"SNAPSHOT",0,"",null,"");
                            var retained=((List<?>)repeated.get("transitions")).stream().map(CanonicalJson::object).filter(t->t.get("id").equals(firstBid.get("id"))).findFirst().orElseThrow();
                            assertEquals(frozen,retained.get("commitmentBindings"),"A later registry incarnation cannot rewrite the historical capture");
                            var current=CanonicalJson.object(repeated.get("commitmentBindings"));assertTrue(((List<?>)current.get("rows")).stream().map(CanonicalJson::object).anyMatch(r->replacement.canonical().equals(r.get("runtimePlayer"))));
                        }
                    }
                }finally{CslRuntimeTestRegistry.closeCartagoContext(participant);CslRuntimeTestRegistry.closeCartagoContext(later);}
            }
        }
    }
    static ObservationCapabilityRequest request(List<ObservationCapabilityRequest> requests,String id){return requests.stream().filter(r->r.id().equals(id)).findFirst().orElseThrow();}
    static Set<String> names(Value value){assertInstanceOf(SetValue.class,value);return ((SetValue)value).collection().stream().map(v->((StringValue)v).value()).collect(java.util.stream.Collectors.toSet());}
    static MObject monitor(NativeRuntimeProjector projector,AuctionOrgFixture f,ArtifactId scheme) {
        var id=f.cut().facts().stream().filter(fact->fact.kind()==RuntimeFactKind.SCHEME_BOARD&&scheme.getId().toString().equals(fact.values().get("boardArtifactIdentity"))).findFirst().orElseThrow().id().canonical();
        return projector.system().state().allObjects().stream().filter(o->o.cls().name().equals("AuctionMonitor")&&id.equals(projector.mutations().metadata(o,"xml:observer:subject"))).findFirst().orElseThrow();
    }
    static final class Capture implements AutoCloseable {
        final AuctionOrgFixture f;final CartagoOperationHistory workspace,domain,schemeOps;final MoiseSchemeCapability schemes;final NplClockCapability clock;
        final ObservationCapabilityRequest domainRequest,schemeRequest,stateRequest;final List<String> errors=new java.util.concurrent.CopyOnWriteArrayList<>();volatile boolean closed;
        Capture(AuctionOrgFixture f,FrameworkObservationProviders.Context context,List<ObservationCapabilityRequest> requests,CartagoOperationHistory workspace,List<VerificationObservation> frames)throws Exception {
            this.f=f;this.workspace=workspace;domainRequest=request(requests,"auction-operations");schemeRequest=request(requests,"scheme-operations");stateRequest=request(requests,"scheme-history");
            domain=new CartagoOperationHistory(domainRequest,context);schemeOps=new CartagoOperationHistory(schemeRequest,context);schemes=new MoiseSchemeCapability(stateRequest,f.live.environment,f.live.source,context.root(),f.live.run.id(),f.live.run.generation());
            clock=new NplClockCapability(request(requests,"source-clock"),f.live.run.id(),f.live.run.generation(),f.live.source.modelRevision(),frames::add);clock.pulse(false);f.live.eventHook=this::observe;
        }
        void observe(RuntimeEvent event) {
            if(closed)return;workspace.observe(event);domain.observe(event);schemeOps.observe(event);
            if(event.entityId()==null||event.factKind()!=RuntimeFactKind.ARTIFACT)return;
            try {
                if(event.kind()==RuntimeEventKind.DISPOSED)return;
                var id=f.live.environment.getController(event.entityId().scope()).getArtifactInfo(event.entityId().localId()).getId();if(!id.getArtifactType().equals("ora4mas.nopl.SchemeBoard"))return;
                String origin=event.kind()==RuntimeEventKind.CREATED?"ARTIFACT_CREATED":event.kind()==RuntimeEventKind.CHANGED?"PROPERTY_PUBLICATION":"";
                if(!origin.isEmpty()){var state=schemes.capture(id,origin,event.observedAt().toEpochMilli(),"",null,"");if(!"COMPLETE".equals(state.get("status")))errors.add(state.toString());}
            }catch(Exception error){errors.add(error.toString());}
        }
        RuntimeSnapshot cut(List<VerificationObservation> frames,BridgeEntityId player,ObservationCapabilityRequest workspaceRequest,CartagoOperationHistory workspaceHistory,ObservationCapabilityRequest field,ArtifactFieldObservation fields)throws Exception {
            clock.pulse(false);var cut=AuctionDeclarativeArtifactTest.observed(f,frames,player,workspaceRequest,workspaceHistory,field,fields);
            var scope=new CapabilityObservation.Scope(f.live.run.id(),f.live.run.generation(),cut.modelRevision(),"cartago",cut.endWatermarks().get("cartago").sequence());var facts=new ArrayList<RuntimeFact>();
            for(var fact:cut.facts()) {
                if(fact.kind()!=RuntimeFactKind.ARTIFACT){facts.add(fact);continue;}var values=new LinkedHashMap<>(fact.values());var receipts=new LinkedHashMap<String,Object>();if(values.get(CapabilityObservation.KEY) instanceof Map<?,?> old)old.forEach((key,value)->receipts.put((String)key,value));
                var id=f.live.environment.getController(fact.id().scope()).getArtifactInfo(fact.id().localId()).getId();
                if(id.getArtifactType().equals("auction_env.AuctionArtifact"))receipts.put(domainRequest.id(),CapabilityObservation.of(domainRequest,scope,Completeness.COMPLETE,"",domain.capture(id)).payload());
                if(id.getArtifactType().equals("ora4mas.nopl.SchemeBoard")){receipts.put(schemeRequest.id(),CapabilityObservation.of(schemeRequest,scope,Completeness.COMPLETE,"",schemeOps.capture(id)).payload());receipts.put(stateRequest.id(),CapabilityObservation.of(stateRequest,scope,Completeness.COMPLETE,"",schemes.capture(id,"SNAPSHOT",0,"",null,"")).payload());}
                if(!receipts.isEmpty())values.put(CapabilityObservation.KEY,receipts);facts.add(new RuntimeFact(fact.id(),fact.kind(),values,fact.relations(),fact.projectionStatus(),fact.completeness(),fact.evidence()));
            }
            return AuctionDeclarativeArtifactTest.copy(cut,facts);
        }
        @Override public void close(){closed=true;f.live.eventHook=event->{};clock.close();schemes.close();schemeOps.close();domain.close();}
    }
}
