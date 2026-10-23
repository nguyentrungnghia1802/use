package org.tzi.use.plugins.jacamo.semantics.xml;
import java.nio.file.*;
import java.util.*;
import cartago.*;
import cartago.util.agent.CartagoBasicContext;
import org.jacamo.bridge.adapter.*;
import org.jacamo.bridge.contract.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import static org.junit.jupiter.api.Assertions.*;
class MoiseSchemeCapabilityTest {
    @Test @Timeout(30) void sameNeutralProviderCapturesAnotherCaseWithTwoIncarnationsAndQualifiedHistory()throws Exception {
        Path root=Path.of("examples/csl-counter").toRealPath(),jcm=root.resolve("counter.jcm");
        var source=new OfficialProjectAdapter().adapt(new OfficialProjectLoader().load(jcm),jcm);
        var environment=CartagoEnvironment.getInstance();var previous=jason.util.Config.get().getProperty(jason.util.Config.START_WEB_OI);jason.util.Config.get().setProperty(jason.util.Config.START_WEB_OI,"false");
        environment.init();var actor=new CartagoBasicContext("schemeCapability"+UUID.randomUUID().toString().replace("-",""));var artifacts=new ArrayList<ArtifactId>();
        var request=new ObservationCapabilityRequest("scheme",MoiseSchemeCapability.PROVIDER,true,Map.of("organization","counter_service","scheme","work"));
        var registry=FrameworkObservationProviders.runtime(new FrameworkObservationProviders.Context(environment,source,root,"run",2,ignored->{},ignored->{}));
        try(var session=registry.open(List.of(request))) {
            assertTrue(session.requiredUnavailable().isEmpty(),session.capabilities().toString());
            var provider=session.handle("scheme",MoiseSchemeCapability.class).orElseThrow();
            for(String name:List.of("one","two")) {
                var output=new OpFeedbackParam<ArtifactId>();actor.doAction(new Op("makeArtifact",name,"ora4mas.nopl.SchemeBoard",new Object[]{root.resolve("src/org/organization.xml").toString(),"work"},output),actor.getJoinedWspId("main"),5000);artifacts.add(output.get());
            }
            var first=provider.capture(artifacts.getFirst(),"ARTIFACT_CREATED",System.currentTimeMillis(),"",null,"");
            assertEquals("COMPLETE",first.get("status"),first.toString());assertEquals(MoiseSchemeCapability.VERSION,first.get("version"));
            assertEquals("COMPLETE",first.get("historyQuality"));assertEquals("PUBLICATION_ONLY_INTERNAL_TRANSITION_TIME_UNAVAILABLE",first.get("timeAuthority"));
            var names=((List<?>)first.get("goals")).stream().map(CanonicalJson::object).map(g->g.get("name")).collect(java.util.stream.Collectors.toSet());
            assertEquals(Set.of("root","first","second"),names);assertFalse(first.containsKey("adminBidOverride"));assertFalse(first.containsKey("bidCommittedAtSatisfaction"));
            var late=provider.capture(artifacts.getLast(),"SNAPSHOT",0,"",null,"");assertEquals("INCOMPLETE",late.get("historyQuality"));assertNotEquals(first.get("schemeIdentity"),late.get("schemeIdentity"));
            provider.retire(artifacts.getFirst());assertEquals("INCOMPLETE",provider.capture(artifacts.getFirst(),"SNAPSHOT",0,"",null,"").get("historyQuality"));
        }finally {
            for(var id:artifacts) {
                var board=(ora4mas.nopl.SchemeBoard)environment.resolveWSP("/main").getWorkspace().getArtifactDescriptor(id.getName()).getArtifact();board.getNormativeEngine().stop();environment.getController("/main").removeArtifact(id.getName());
            }
            for(var id:environment.getController("/main").getCurrentAgents())if(id.getAgentName().equals(actor.getName()))environment.resolveWSP("/main").getWorkspace().quitAgent(id);
            environment.shutdown();if(previous==null)jason.util.Config.get().remove(jason.util.Config.START_WEB_OI);else jason.util.Config.get().setProperty(jason.util.Config.START_WEB_OI,previous);
        }
    }
}
