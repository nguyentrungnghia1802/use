package org.jacamo.bridge.adapter;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import cartago.*;
import cartago.util.agent.CartagoBasicContext;
import org.jacamo.bridge.contract.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ProductionObservationSessionTest {
    @TempDir Path root;
    @Test void neutralJasonMarkersUseTheProductionVerificationLane() throws Exception {
        Path asl=Files.writeString(root.resolve("worker.asl"),"+!deliver <- .print(\"ready\"); .print(\"sent\").\n");
        Path jcm=Files.writeString(root.resolve("worker.jcm"),"mas worker { agent worker: worker.asl }\n");
        var source=new OfficialProjectAdapter().adapt(new OfficialProjectLoader().load(jcm),jcm);
        var program=source.semanticContract().jasonPrograms().getFirst();
        var request=new ObservationCapabilityRequest("steps",JasonStepCapability.PROVIDER,true,Map.of("sourcePath","worker.asl","sourceRef","worker","sourceHash",program.sourceDigest(),"plans",List.of(Map.of("trigger","deliver","context","","steps",List.of(Map.of("task","Deliver","first",0,"after",2,"variables",List.of(),"artifactVariable","","artifactClass","","schemeVariable","","allowMissingArtifactAtEntry",false,"inputs",List.of()))))));
        var actor=new jason.asSemantics.Agent();actor.setConsiderToAddMIForThisAgent(false);actor.initAg();actor.setASLSrc(asl.toUri().toString());
        try(var input=Files.newInputStream(asl)){actor.parseAS(input,asl.toUri().toString());}
        var ts=new jason.asSemantics.TransitionSystem(actor,new jason.asSemantics.Circumstance(),new jason.runtime.Settings(),new jason.architecture.AgArch(){@Override public String getAgName(){return "worker";}});
        var events=new ArrayList<RuntimeEvent>();var environment=CartagoEnvironment.getInstance();environment.init();
        try(var observations=new VerificationObservationSource("run",1,source.modelRevision());
            var coordinator=new SnapshotCoordinator(List.of(observations),64,events::add);
            var providers=new ProductionObservationSession(new FrameworkObservationProviders.Context(environment,source,root,"run",1,o->observations.publish(o,"",""),reason->fail(reason)),List.of(request))) {
            providers.agent(ts,new BridgeEntityId("jason","agent","runtime-agent","worker","worker","physical"),program);
            assertTrue(providers.requiredUnavailable().isEmpty());
            ts.getC().addAchvGoal(jason.asSyntax.ASSyntax.parseLiteral("deliver"),jason.asSemantics.Intention.EmptyInt);for(int i=0;i<30;i++)ts.reasoningCycle();
            assertEquals(2,events.size());assertTrue(events.stream().allMatch(e->VerificationObservation.require(e).kind()==VerificationObservation.Kind.JASON_STEP));
        }finally{actor.stopAg();environment.shutdown();}
    }
    @Test @Timeout(30) void productionLanePublishesNeutralFieldAndOperationReceiptsForAnotherCaseAndReusesNplSource() throws Exception {
        String type=FieldProbeArtifact.class.getName();
        Path jcm=Files.writeString(root.resolve("probe.jcm"),"mas probe { workspace main { artifact sample: "+type+" } }\n");
        var model=new OfficialProjectAdapter().adapt(new OfficialProjectLoader().load(jcm),jcm);
        var environment=CartagoEnvironment.getInstance();environment.init();
        var actor=new CartagoBasicContext("neutral"+UUID.randomUUID().toString().replace("-",""));
        String hash=AdapterEvidence.digest(DeclaredArtifactTypeProof.readActualBinary(FieldProbeArtifact.class).bytes());
        var field=new ObservationCapabilityRequest("token",ArtifactFieldObservation.PROVIDER,true,Map.of("class",type,"field","token","type","java.lang.String","hash",hash,"properties",Map.of("p","JAVA_INTEGER")));
        var operation=new ObservationCapabilityRequest("history",CartagoOperationHistory.PROVIDER,true,Map.of("class",type,"hash",hash));
        var npl=new ObservationCapabilityRequest("norms",NplLifecycleCapability.PROVIDER,true,Map.of("hash",NplRuntimeProfile.evidence().get("artifactSha256")));
        var absent=new ObservationCapabilityRequest("absent","example.not-installed.v1",true,Map.of());
        System.setProperty("jacamo.bridge.generation","4");System.setProperty("jacamo.bridge.modelRevision",model.modelRevision());
        var events=new CopyOnWriteArrayList<RuntimeEvent>();ArtifactId id=null;
        try(var norms=new NplBoardSnapshotSource("run",4,model.modelRevision(),List::of);
            var source=new CartagoSnapshotSource(environment,List.of("/main"),"run",model,norms::artifactCreated,false);
            var coordinator=new SnapshotCoordinator(List.of(source,norms),512,events::add);
            var providers=new ProductionObservationSession(new FrameworkObservationProviders.Context(environment,model,root,"run",4,ignored->{},reason->fail(reason),norms),List.of(field,operation,npl,absent))) {
            assertEquals(List.of("absent"),providers.requiredUnavailable().stream().map(Capability::name).toList());
            source.capabilities(providers);assertFalse(source.fieldProbeAvailable());assertFalse(source.schemeProbeAvailable());
            id=actor.makeArtifact(actor.getJoinedWspId("main"),"neutralOne",type);actor.doAction(id,new Op("bump"),5000);
            NplLiveBoardObservationTest.await(()->events.stream().anyMatch(e->e.kind()==RuntimeEventKind.SUCCEEDED&&e.after().get("operation")!=null&&e.after().get("operation").equals("bump")));
            var cut=coordinator.capture(model.modelRevision(),3).snapshot();
            var fact=cut.facts().stream().filter(f->f.id().incarnation().equals(idFor(events))).findFirst().orElseThrow();
            var receipts=CanonicalJson.object(fact.values().get(CapabilityObservation.KEY));
            var actual=CapabilityObservation.require(receipts.get(field.id()),field);
            assertEquals(Completeness.COMPLETE,actual.quality(),actual.toString());
            assertEquals(cut.endWatermarks().get("cartago").sequence(),actual.scope().sourceSequence());
            assertEquals("change-1",CanonicalJson.object(CanonicalJson.object(actual.data().get("fields")).get("token")).get("value"));
            var history=CapabilityObservation.require(receipts.get(operation.id()),operation);assertEquals("COMPLETE",history.data().get("historyQuality"));assertEquals(2,((List<?>)history.data().get("events")).size());
            assertTrue(events.stream().anyMatch(e->e.after().containsKey(CapabilityObservation.KEY)));assertTrue(events.stream().noneMatch(e->e.after().containsKey("auctionStateObservation")));
            for(var event:events)if(event.after().containsKey(CapabilityObservation.KEY))assertEquals(OfficialCartagoAdapter.artifactId(environment.getId().toString(),id),event.after().get("semanticId"),"Every capability callback carries its exact physical semantic owner");
            source.observationKinds(Set.of(RuntimeFactKind.WORKSPACE,RuntimeFactKind.ARTIFACT,RuntimeFactKind.PROPERTY,RuntimeFactKind.AGENT));
            int count=events.size();source.publishObservation(()->assertEquals(count+1,events.size(),"Complete local capture is published before the step callback"));
            var boundary=events.getLast();var live=CartagoSourceCut.require(boundary);
            String owner=OfficialCartagoAdapter.artifactId(environment.getId().toString(),id);
            assertTrue(live.facts().stream().anyMatch(f->f.kind()==RuntimeFactKind.OPERATION&&f.id().scope().equals(owner)));
            assertFalse(CartagoSourceCut.kinds(live).contains(RuntimeFactKind.OPERATION));
            assertEquals(Set.of(RuntimeFactKind.OPERATION),CartagoSourceCut.supplemental(live));
            var current=live.facts().stream().filter(f->f.id().incarnation().equals(idFor(events))).findFirst().orElseThrow();
            var currentReceipts=CanonicalJson.object(current.values().get(CapabilityObservation.KEY));
            assertEquals(boundary.sourceSequence(),CapabilityObservation.require(currentReceipts.get(field.id()),field).scope().sourceSequence());
            assertEquals(boundary.sourceSequence(),((Number)CapabilityObservation.require(currentReceipts.get(operation.id()),operation).data().get("lastSourceSequence")).longValue());
            providers.close();assertThrows(IllegalStateException.class,providers::capabilities);
            assertDoesNotThrow(norms::capture,"Capability close must not close the shared authoritative NPL source");
        }finally {
            if(id!=null)environment.getController("/main").removeArtifact(id.getName());
            for(var agent:environment.getController("/main").getCurrentAgents())if(agent.getAgentName().equals(actor.getName()))environment.resolveWSP("/main").getWorkspace().quitAgent(agent);
            environment.shutdown();System.clearProperty("jacamo.bridge.generation");System.clearProperty("jacamo.bridge.modelRevision");
        }
    }
    private static String idFor(List<RuntimeEvent> events){return events.stream().filter(e->e.kind()==RuntimeEventKind.CREATED&&"neutralOne".equals(e.after().get("name"))).findFirst().orElseThrow().entityId().incarnation();}
}
