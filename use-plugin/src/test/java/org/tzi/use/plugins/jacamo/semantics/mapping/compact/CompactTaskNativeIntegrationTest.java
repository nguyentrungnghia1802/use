package org.tzi.use.plugins.jacamo.semantics.mapping.compact;

import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import jason.asSemantics.*;
import jason.asSyntax.ASSyntax;
import org.jacamo.bridge.adapter.*;
import org.jacamo.bridge.contract.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.tzi.use.plugins.jacamo.codegrounded.*;
import org.tzi.use.plugins.jacamo.codegrounded.constraint.ExternalOclConstraintService;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.*;
import org.tzi.use.plugins.jacamo.codegrounded.use.*;
import org.tzi.use.plugins.jacamo.semantics.mapping.xml.*;
import org.tzi.use.plugins.jacamo.semantics.mapping.xml.runtime.*;
import org.tzi.use.plugins.jacamo.semantics.runtime.RuntimeEngineSessionOwner;
import static org.junit.jupiter.api.Assertions.*;

/** Actual Jason callbacks -> compact native writer -> original PRE/POST and Goal engines. */
class CompactTaskNativeIntegrationTest {
    @TempDir Path temp;
    @Test @Timeout(40) void sharedProgramKeepsNativeInvocationsIndependent() throws Exception {exercise(false);}
    @Test @Timeout(40) void actualSourceFailureRemainsAnIncompleteTask() throws Exception {exercise(true);}

    private void exercise(boolean failing) throws Exception {
        Files.createDirectories(temp.resolve("src/agt"));
        var behavior=Files.writeString(temp.resolve("src/agt/worker.asl"),"+!work <- "+(failing?".fail":".print(\"delivered\")")+".\n");
        var project=Files.writeString(temp.resolve("dispatch.jcm"),"mas dispatch { agent first : worker.asl agent second : worker.asl }");
        var source=new OfficialProjectAdapter().adapt(new OfficialProjectLoader().load(project),project);
        var csl=Files.writeString(temp.resolve("state.csl"),"csl v4.0 Dispatch { orgContext WorkerState { attribute name : String required; } }");
        var istar=Files.writeString(temp.resolve("goals.istar"),"istar Deliveries { role Courier { goal Delivered : Achieve task Work > Delivered pre {[ step.enabled ]} post {[ step.succeeded ]} } }");
        var xml=Files.writeString(temp.resolve("mapping.xml"),"""
            <mapping xmlns="urn:use:jacamo-mapping:3" version="3.0" id="callbacks">
              <csl><map source="agent:src/agt/worker.asl" target="WorkerState" contextAbstraction="SINGLE_AGENT_CONTEXT/1.0"/>
                <map source="agent:src/agt/worker.asl/identity:name" target="WorkerState.name"/></csl>
              <istar><map source="agent:src/agt/worker.asl" target="Courier"/>
                <map source="asl:src/agt/worker.asl/plan:work/step:execute[0..1]" target="Courier.Work" self="WorkerState"/></istar>
              <runtime><actorRuntime actor="Courier" activationPolicy="AGENT_ENACTMENT" episodePolicy="RUN_SCHEME_PLAYER_INVOCATION" self="WorkerState"/>
                <verify target="Courier.Delivered"/></runtime>
            </mapping>
            """);
        var program=new GenericMappingEngine().map(source,MappingInputs.read(project,xml,null,csl,istar));
        var built=new CodeGroundedNativePipeline().buildCanonicalProgram(source,NativeProjectionMode.AUTO,program);
        var engine=new NativeRuntimeMutationEngine(built.nativeResult().state().system(),built.nativeResult().state().semanticObjectIndex(),new CodeGroundedRuntimeRuleRegistry(),program);
        var facts=new LinkedHashMap<String,RuntimeFact>();
        for(var declaration:source.semanticContract().agentDeclarations()) {
            var id=new BridgeEntityId("jason","agent","runtime-agent","dispatch",declaration.name(),"life-"+declaration.name());
            var data=Map.<String,Object>of("normalizedEventKind","UPSERT_JASON_AGENT_STATE","semanticId",id.canonical(),"name",declaration.name(),"agentDeclarationSemanticId",declaration.metadata().semanticId(),"agentRuntimeSemanticId",id.canonical(),"active",true);
            facts.put(id.canonical(),new RuntimeFact(id,RuntimeFactKind.AGENT,data,List.of(),ProjectionStatus.MATERIALIZED_FAITHFULLY,Completeness.COMPLETE,List.of()));
        }
        var marks=new TreeMap<String,SourceWatermark>();marks.put("jason",new SourceWatermark("jason",1));marks.put(VerificationObservation.SOURCE_ID,new SourceWatermark(VerificationObservation.SOURCE_ID,0));
        var quality=Map.of("jason",Completeness.COMPLETE,VerificationObservation.SOURCE_ID,Completeness.COMPLETE,"cartago",Completeness.COMPLETE);
        engine.applyMappingCut(cut(source,facts,marks,quality),"run",1,List.of());
        assertTrue(engine.xmlOccurrences("run",1,source.modelRevision()).isEmpty());
        var frames=new ArrayList<VerificationObservation>();
        var request=program.compactPlan().capabilityRequests().getFirst();
        for(String name:List.of("first","second")) {
            var agent=new Agent();agent.setConsiderToAddMIForThisAgent(false);agent.initAg();agent.setASLSrc(behavior.toUri().toString());
            try(var input=Files.newInputStream(behavior)){agent.parseAS(input,behavior.toUri().toString());}
            var ts=new TransitionSystem(agent,new Circumstance(),new jason.runtime.Settings(),new jason.architecture.AgArch(){@Override public String getAgName(){return name;}});
            var id=facts.values().stream().filter(f->name.equals(f.values().get("name"))).findFirst().orElseThrow().id();
            var original=source.semanticContract().jasonPrograms().stream().filter(p->p.declarationId().equals(name)).findFirst().orElseThrow();
            try(var provider=new JasonStepCapability(request,ts,id,"run",1,source.modelRevision(),original,temp,frames::add,reason->fail(reason))) {
                assertEquals(CapabilityStatus.COMPLETE,provider.capability().status(),provider.capability().toString());
                ts.getC().addAchvGoal(ASSyntax.parseLiteral("work"),Intention.EmptyInt);for(int i=0;i<30;i++)ts.reasoningCycle();
            } finally {agent.stopAg();}
        }
        assertEquals(4,frames.size());
        try(var owner=new RuntimeEngineSessionOwner(engine,"run",1,source.modelRevision())) {
            long version=0;RuntimeEngineSessionOwner.Evaluation latest=null;
            for(var frame:frames) {
                long sequence=++version;var event=frame.event("run",1,source.modelRevision(),sequence,Instant.EPOCH,"","");
                facts=new LinkedHashMap<>(RuntimeObservationCut.advance(facts,event));marks.put(VerificationObservation.SOURCE_ID,event.watermark());
                var cut=cut(source,facts,marks,quality);engine.applyMappingCut(cut,"run",1,List.of(),event);
                owner.taskCheckpoint(event,quality,false,cut);
                String stateHash=ExternalOclConstraintService.sha256(new NativeUseSoilExporter().exportState(engine.system().state()).commands().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                var prepared=owner.prepare(version,event.eventId(),stateHash,quality,false,true,List.of(),new ExternalOclConstraintService.PreparedEvaluation(engine.system().model(),List.of()),owner.drainTaskPrepared(),4096);
                latest=owner.submit(prepared).get(10,TimeUnit.SECONDS);
            }
            assertNotNull(latest);var core=CanonicalJson.object(latest.evidence().observation().get("originalEngine"));
            var contexts=(List<?>)core.get("contexts");assertEquals(2,contexts.size());
            for(var raw:contexts)for(var value:CanonicalJson.object(CanonicalJson.object(raw).get("occurrences")).values()) {
                var occurrence=CanonicalJson.object(value);
                if("Work".equals(occurrence.get("sourceElement")))assertEquals(failing?"PENDING":"FULFILLED",occurrence.get("leafStatus"),occurrence.toString());
            }
            assertEquals(2,engine.xmlTaskDemands("run",1,source.modelRevision()).stream().map(d->d.player()).distinct().count());
            if(failing) {
                assertTrue(latest.evidence().constraints().stream().anyMatch(row->row.kind().equals("TASK_POST")&&row.verdict()==org.tzi.use.plugins.jacamo.semantics.evidence.SemanticVerdict.FAIL),latest.evidence().constraints().toString());
                assertTrue(owner.checkpoint().get().certificates().values().stream().anyMatch(c->c.kind().equals("TASK_FAILED")));
            }
        }
    }
    private static RuntimeSnapshot cut(ModelSnapshot source,Map<String,RuntimeFact> facts,Map<String,SourceWatermark> marks,Map<String,Completeness> quality) {
        return new RuntimeSnapshot("cut-"+marks.get(VerificationObservation.SOURCE_ID).sequence(),source.modelRevision(),Instant.EPOCH,Instant.EPOCH,marks,marks,1,List.copyOf(facts.values()),quality,"component",Map.of());
    }
}
