package org.jacamo.bridge.adapter;

import java.nio.file.Path;
import java.util.*;
import jason.asSemantics.*;
import jason.asSyntax.ASSyntax;
import jason.runtime.Settings;
import org.jacamo.bridge.contract.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JasonObservationProfileTest {
    @Test void explicitBeliefViewPublishesContentChangesRatherThanIdleCycleHeartbeats()throws Exception {
        var file=Path.of("../use-plugin/src/test/resources/canonical-cases/hello-world/helloworld.jcm").toAbsolutePath();
        var project=new OfficialProjectAdapter().adapt(new OfficialProjectLoader().load(file),file).semanticContract();
        var agent=new Agent();agent.initAg();var architecture=new BridgeAgArch();var events=new ArrayList<RuntimeEvent>();
        architecture.setTS(new TransitionSystem(agent,new Circumstance(),new Settings(),architecture));
        try(var attached=BridgeRuntimeRegistry.attach(events::add)) {
            BridgeRuntimeRegistry.configureDeclarations(project);architecture.init();
            BridgeRuntimeRegistry.registerAgent(architecture.getAgName(),BridgeRuntimeRegistry.agentIdentity(architecture.getAgName()).orElseThrow(),project.agentDeclarations().getFirst().name());
            architecture.observationFields(List.of("normalizedEventKind","semanticId","name","cycles","storedBeliefObservation"));
            agent.getBB().add(ASSyntax.parseLiteral("selected(1)"));architecture.setCycleNumber(1);architecture.reasoningCycleFinished();
            long before=events.stream().filter(e->e.kind()==RuntimeEventKind.CHANGED).count();
            for(int cycle=2;cycle<=1001;cycle++){architecture.setCycleNumber(cycle);architecture.reasoningCycleFinished();}
            assertEquals(before,events.stream().filter(e->e.kind()==RuntimeEventKind.CHANGED).count(),"Idle cycles must not flood the transport with unchanged stored belief views");
            assertEquals(1,((Number)BridgeRuntimeRegistry.jasonState(architecture.getAgName()).orElseThrow().get("cycles")).intValue(),"Cache and watermark retain the same last published observation");
            agent.getBB().add(ASSyntax.parseLiteral("selected(2)"));architecture.setCycleNumber(1002);architecture.reasoningCycleFinished();
            assertEquals(before+1,events.stream().filter(e->e.kind()==RuntimeEventKind.CHANGED).count());
            var state=BridgeRuntimeRegistry.jasonState(architecture.getAgName()).orElseThrow();assertEquals(1002,((Number)state.get("cycles")).intValue());
            assertEquals(1002,((Number)CanonicalJson.object(state.get("storedBeliefObservation")).get("cycle")).intValue());
            BridgeRuntimeRegistry.invalidateJasonState(architecture.getAgName(),"EXPLICIT_CUT_INVALIDATION");
            architecture.setCycleNumber(1003);architecture.reasoningCycleFinished();
            var recovered=BridgeRuntimeRegistry.jasonState(architecture.getAgName()).orElseThrow();
            assertEquals(before+2,events.stream().filter(e->e.kind()==RuntimeEventKind.CHANGED).count(),"Recovery must publish a new cut even with unchanged stored content");
            assertEquals(1003,((Number)recovered.get("cycles")).intValue());
            assertEquals(1003,((Number)CanonicalJson.object(recovered.get("storedBeliefObservation")).get("cycle")).intValue());
            assertTrue(BridgeRuntimeRegistry.jasonStateError(architecture.getAgName()).isEmpty());
            architecture.setCycleNumber(1004);architecture.reasoningCycleFinished();
            assertEquals(before+2,events.stream().filter(e->e.kind()==RuntimeEventKind.CHANGED).count(),"Recovery must not re-enable idle heartbeat publication");
        }finally{architecture.stop();BridgeRuntimeRegistry.clearAgents();}
    }
    @Test void explicitMentalFieldRequestUsesCycleProviderEvenInMinimalProfileWithoutExecutionDump()throws Exception {
        var file=Path.of("../use-plugin/src/test/resources/canonical-cases/hello-world/helloworld.jcm").toAbsolutePath();
        var project=new OfficialProjectAdapter().adapt(new OfficialProjectLoader().load(file),file).semanticContract();
        String before=System.getProperty("jacamo.bridge.structured-observation");var agent=new Agent();agent.initAg();var architecture=new BridgeAgArch();
        architecture.setTS(new TransitionSystem(agent,new Circumstance(),new Settings(),architecture));
        try(var attached=BridgeRuntimeRegistry.attach(event->{})) {
            System.setProperty("jacamo.bridge.structured-observation","false");BridgeRuntimeRegistry.configureDeclarations(project);architecture.init();
            BridgeRuntimeRegistry.registerAgent(architecture.getAgName(),BridgeRuntimeRegistry.agentIdentity(architecture.getAgName()).orElseThrow(),project.agentDeclarations().getFirst().name());
            architecture.observationFields(List.of("normalizedEventKind","semanticId","name","cycles","mentalObservation"));
            agent.getBB().add(ASSyntax.parseRule("known(X) :- observed(X)."));architecture.setCycleNumber(4);architecture.reasoningCycleFinished();
            var state=BridgeRuntimeRegistry.jasonState(architecture.getAgName()).orElseThrow();
            assertEquals(4,((Number)CanonicalJson.object(state.get("mentalObservation")).get("cycle")).intValue());
            assertFalse(state.containsKey("executionObservation"));assertEquals(1,((List<?>)CanonicalJson.object(state.get("mentalObservation")).get("beliefs")).size());
        }finally {architecture.stop();BridgeRuntimeRegistry.clearAgents();if(before==null)System.clearProperty("jacamo.bridge.structured-observation");else System.setProperty("jacamo.bridge.structured-observation",before);}
    }
    @Test void nativeDeduplicatesUnchangedLiteralsAndCslKeepsEveryCycleCut() throws Exception {
        var source=Path.of("../use-plugin/src/test/resources/canonical-cases/hello-world/helloworld.jcm").toAbsolutePath();
        var project=new OfficialProjectAdapter().adapt(new OfficialProjectLoader().load(source),source).semanticContract();
        String profileBefore=System.getProperty("jacamo.bridge.structured-observation");
        for(boolean structured:List.of(false,true)) {
            var events=new ArrayList<RuntimeEvent>();var agent=new Agent();agent.initAg();
            var architecture=new BridgeAgArch();architecture.setTS(new TransitionSystem(agent,new Circumstance(),new Settings(),architecture));
            System.setProperty("jacamo.bridge.structured-observation",Boolean.toString(structured));
            try(var attached=BridgeRuntimeRegistry.attach(events::add)) {
                BridgeRuntimeRegistry.configureDeclarations(project);architecture.init();
                BridgeRuntimeRegistry.registerAgent(architecture.getAgName(),BridgeRuntimeRegistry.agentIdentity(architecture.getAgName()).orElseThrow(),project.agentDeclarations().getFirst().name());
                agent.getBB().add(ASSyntax.parseLiteral("ready(1)"));
                var plan=ASSyntax.parsePlan("+!work <- act.");var intention=new Intention();
                intention.push(new IntendedMeans(new Option(plan,new Unifier()),plan.getTrigger()));agent.getTS().getC().addRunningIntention(intention);
                intention.setPlace(Circumstance.IntentionPlace.None);
                architecture.setCycleNumber(1);architecture.reasoningCycleFinished();
                assertEquals(Circumstance.IntentionPlace.None,intention.getPlace(),"Cycle hook must not rewrite live intention metadata in either observation profile");
                long first=events.stream().filter(e->e.kind()==RuntimeEventKind.CHANGED).count();
                architecture.setCycleNumber(2);architecture.reasoningCycleFinished();
                assertEquals(first+(structured?1:0),events.stream().filter(e->e.kind()==RuntimeEventKind.CHANGED).count());
                var cut=BridgeRuntimeRegistry.jasonState(architecture.getAgName()).orElseThrow();
                assertEquals(structured,cut.containsKey("mentalObservation"));assertEquals(structured,cut.containsKey("executionObservation"));
                if(structured)assertEquals(2,((Number)CanonicalJson.object(cut.get("mentalObservation")).get("cycle")).intValue());
                agent.getBB().add(ASSyntax.parseLiteral("changed(2)"));architecture.setCycleNumber(3);architecture.reasoningCycleFinished();
                assertEquals(2,((List<?>)BridgeRuntimeRegistry.jasonState(architecture.getAgName()).orElseThrow().get("beliefs")).size());
            } finally {
                architecture.stop();BridgeRuntimeRegistry.clearAgents();
                if(profileBefore==null)System.clearProperty("jacamo.bridge.structured-observation");
                else System.setProperty("jacamo.bridge.structured-observation",profileBefore);
            }
        }
    }
}
