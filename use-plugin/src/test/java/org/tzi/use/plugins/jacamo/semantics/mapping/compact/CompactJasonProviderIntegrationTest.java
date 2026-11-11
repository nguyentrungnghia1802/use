package org.tzi.use.plugins.jacamo.semantics.mapping.compact;

import java.nio.file.*;
import java.util.*;
import jason.asSemantics.*;
import jason.asSyntax.ASSyntax;
import org.jacamo.bridge.adapter.*;
import org.jacamo.bridge.contract.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.tzi.use.plugins.jacamo.semantics.mapping.xml.runtime.JasonStepCutDecoder;
import org.tzi.use.semantic.engine.frontend.*;
import static org.junit.jupiter.api.Assertions.*;

/** Generated plan requests exercised through the actual Jason provider/decoder.
 * This does not certify compact native Task demand/scope integration. */
class CompactJasonProviderIntegrationTest {
    @TempDir Path temp;
    @Test @Timeout(30) void sharedProgramRequestPreservesPlayersAndUnmappedSourceRegionsWithoutChangingSourceFiles() throws Exception {
        Files.createDirectories(temp.resolve("src/agt"));
        var behavior=Files.writeString(temp.resolve("src/agt/worker.asl"),"+!work <- .print(\"prefix\"); .print(\"selected\"); .print(\"suffix\").\n");
        var project=Files.writeString(temp.resolve("dispatch.jcm"),"mas dispatch { agent first : worker.asl agent second : worker.asl }\n");
        var source=new OfficialProjectAdapter().adapt(new OfficialProjectLoader().load(project),project);
        var csl=new CslFrontend().compile("csl v4.0 Dispatch { entity Package { attribute status : String; } }","state.csl").requireModel();
        var goals=new IStarFrontend().compile("istar Delivery { role Courier { goal Delivered : Achieve task trace_delivery pre {[ step.enabled ]} post {[ step.succeeded ]} } }","goals.istar").requireModel();
        var spec=CompactMappingReader.read("""
            <mapping xmlns="urn:use:jacamo-mapping:3" version="3.0" id="source-test">
              <istar><map source="agent:src/agt/worker.asl" target="Courier"/>
                <map source="asl:src/agt/worker.asl/plan:work/step:move[1..2]" target="Courier.trace_delivery"/></istar>
            </mapping>
            ""","mapping.xml");
        var plan=ObservationPlanCompiler.compile(spec,SourceSymbolCatalog.fromSnapshot(source.semanticContract()),NativeTargetCatalog.fromModels(csl,goals));
        var requests=plan.capabilityRequests();assertEquals(1,requests.size());
        var request=requests.getFirst();assertEquals("jason.plan-boundaries.v1",request.provider());
        var frames=new ArrayList<VerificationObservation>();var decoded=new ArrayList<JasonStepCutDecoder.Step>();
        var decoder=new JasonStepCutDecoder(source,request);
        for(String name:List.of("first","second")) {
            var agent=new Agent();agent.setConsiderToAddMIForThisAgent(false);agent.initAg();agent.setASLSrc(behavior.toUri().toString());
            try(var input=Files.newInputStream(behavior)){agent.parseAS(input,behavior.toUri().toString());}
            var ts=new TransitionSystem(agent,new Circumstance(),new jason.runtime.Settings(),new jason.architecture.AgArch(){@Override public String getAgName(){return name;}});
            var identity=new BridgeEntityId("jason","agent","runtime-agent",source.semanticContract().project().name(),name,"life-"+name);
            var program=source.semanticContract().jasonPrograms().stream().filter(p->p.declarationId().equals(name)).findFirst().orElseThrow();
            try(var provider=new JasonStepCapability(request,ts,identity,"run",1,source.modelRevision(),program,temp,frames::add,reason->fail(reason))) {
                assertEquals(CapabilityStatus.COMPLETE,provider.capability().status(),provider.capability().toString());
                ts.getC().addAchvGoal(ASSyntax.parseLiteral("work"),Intention.EmptyInt);
                for(int cycle=0;cycle<30;cycle++)ts.reasoningCycle();
            } finally {agent.stopAg();}
            assertEquals(program.sourceDigest(),org.tzi.use.plugins.jacamo.codegrounded.constraint.ExternalOclConstraintService.sha256(Files.readAllBytes(behavior)));
        }
        for(var frame:frames)decoded.add(decoder.decode(frame,"run",1,source.modelRevision()));
        assertEquals(12,decoded.size());assertEquals(2,decoded.stream().map(s->s.agent()).distinct().count());
        for(var player:decoded.stream().map(s->s.agent()).distinct().toList()) {
            var steps=decoded.stream().filter(s->s.agent().equals(player)).toList();
            assertEquals(3,steps.stream().map(s->s.task()).distinct().count());
            assertEquals(2,steps.stream().filter(s->s.task().equals("trace_delivery")).count());
            assertEquals(Set.of("ENABLED","SUCCEEDED"),new HashSet<>(steps.stream().filter(s->s.task().equals("trace_delivery")).map(s->s.phase()).toList()));
            assertFalse(plan.unmappedTraceSteps().contains("trace_delivery"));
            assertEquals(4,steps.stream().filter(s->plan.unmappedTraceSteps().contains(s.task())).count());
        }
    }
}
