package org.jacamo.bridge.adapter;
import java.nio.file.*;
import java.util.*;
import cartago.*;
import cartago.util.agent.CartagoBasicContext;
import org.jacamo.bridge.contract.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
class ArtifactFieldObservationTest {
    @TempDir Path temp;
    @Test @Timeout(30) void sameProviderReadsAnotherCaseOnlyWithActualProofAndExplicitFieldType()throws Exception {
        String type=FieldProbeArtifact.class.getName();var jcm=Files.writeString(temp.resolve("probe.jcm"),"mas probe { workspace main { artifact sample: "+type+" } }\n");
        var model=new OfficialProjectAdapter().adapt(new OfficialProjectLoader().load(jcm),jcm);
        var environment=CartagoEnvironment.getInstance();environment.init();var actor=new CartagoBasicContext("fieldProbe"+UUID.randomUUID().toString().replace("-",""));var created=new ArrayList<ArtifactId>();
        var request=new ObservationCapabilityRequest("token",ArtifactFieldObservation.PROVIDER,true,Map.of("class",type,"field","token","type","java.lang.String","properties",Map.of("p","JAVA_INTEGER")));
        var registry=new ObservationCapabilityRegistry(Map.of(ArtifactFieldObservation.PROVIDER,r->new ArtifactFieldObservation(environment,model,r)));
        try(var session=registry.open(List.of(request))) {
            assertTrue(session.requiredUnavailable().isEmpty());var probe=session.handle("token",ArtifactFieldObservation.class).orElseThrow();
            for(String name:List.of("first","second"))created.add(actor.makeArtifact(actor.getJoinedWspId("main"),name,type));
            actor.doAction(created.getFirst(),new Op("bump"),5000);
            var cut=probe.capture(created.getFirst(),"TEST","");assertEquals("COMPLETE",cut.get("status"),cut.toString());
            assertEquals("change-1",CanonicalJson.object(CanonicalJson.object(cut.get("fields")).get("token")).get("value"));
            assertEquals("initial",CanonicalJson.object(CanonicalJson.object(probe.capture(created.getLast(),"TEST","").get("fields")).get("token")).get("value"));
            assertEquals(type+".token:java.lang.String",CanonicalJson.object(cut.get("probeEvidence")).get("field"));
            for(var wrong:List.of(Map.<String,Object>of("class",type,"field","token","type","int","properties",Map.of("p","JAVA_INTEGER")),Map.<String,Object>of("class",type,"field","token","type","java.lang.String","hash","0".repeat(64)))) {
                try(var unavailable=new ArtifactFieldObservation(environment,model,new ObservationCapabilityRequest("bad",ArtifactFieldObservation.PROVIDER,true,wrong))) {
                    assertFalse(unavailable.available());assertFalse(unavailable.capture(created.getFirst(),"TEST","").containsKey("fields"));
                }
            }
            var denied=new ArtifactFieldObservation(environment,model,request,field->false);assertFalse(denied.available());denied.close();
        }finally {
            for(var artifact:created)environment.getController("/main").removeArtifact(artifact.getName());
            for(var id:environment.getController("/main").getCurrentAgents())if(id.getAgentName().equals(actor.getName()))environment.resolveWSP("/main").getWorkspace().quitAgent(id);
            environment.shutdown();
        }
    }
}
