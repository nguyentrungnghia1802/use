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
                architecture.setCycleNumber(1);architecture.reasoningCycleFinished();
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
