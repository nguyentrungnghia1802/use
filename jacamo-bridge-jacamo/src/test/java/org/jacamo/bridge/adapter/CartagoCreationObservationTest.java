package org.jacamo.bridge.adapter;

import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import cartago.*;
import cartago.util.agent.CartagoBasicContext;
import org.jacamo.bridge.contract.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import static org.junit.jupiter.api.Assertions.*;

class CartagoCreationObservationTest {
    @Test @Timeout(30) void twoRealPinnedAuctionCreationsCarryActualOutputUuidWorkspaceAndCreator()throws Exception {
        var classes=SourceActionArtifactTypeProofTest.compilePinnedAuction();
        var previous=Thread.currentThread().getContextClassLoader();var environment=CartagoEnvironment.getInstance();
        var created=new ArrayList<ArtifactId>();String actor="creationObserver"+UUID.randomUUID().toString().replace("-","");
        try(var loader=new URLClassLoader(new URL[]{classes.toUri().toURL()},previous)) {
            Thread.currentThread().setContextClassLoader(loader);environment.init();
            environment.addArtifactFactory("/main",new ArtifactFactory("pinned-source-fixture") {
                @Override public Artifact createArtifact(String template)throws CartagoException {
                    if(!template.equals("auction_env.AuctionArtifact"))throw new CartagoException("NOT_SELECTED_TEST_TYPE");
                    try{return (Artifact)Class.forName(template,false,loader).getDeclaredConstructor().newInstance();}
                    catch(ReflectiveOperationException failure){throw new CartagoException(failure.toString());}
                }
            });
            var events=new CopyOnWriteArrayList<RuntimeEvent>();
            try(var source=new CartagoSnapshotSource(environment,List.of(),"creation-observation")) {
                source.attach(events::add);var context=new CartagoBasicContext(actor);var workspace=context.getJoinedWspId("main");
                assertEquals(0,source.capture().stream().filter(f->"auction_env.AuctionArtifact".equals(f.values().get("artifactTypeJavaClassName"))).count());
                for(String name:List.of("a1","a2")) {
                    var output=new OpFeedbackParam<ArtifactId>();
                    context.doAction(new Op("makeArtifact",name,"auction_env.AuctionArtifact",new Object[0],output),workspace,5000);
                    assertNotNull(output.get());created.add(output.get());
                }
                long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
                while(events.stream().filter(e->e.kind()==RuntimeEventKind.SUCCEEDED&&"makeArtifact".equals(e.after().get("operation"))).count()<2&&System.nanoTime()<deadline)Thread.sleep(10);
                var completions=events.stream().filter(e->e.kind()==RuntimeEventKind.SUCCEEDED&&"makeArtifact".equals(e.after().get("operation"))).toList();assertEquals(2,completions.size());
                var facts=source.capture();
                for(var artifact:created) {
                    var expected=OfficialCartagoAdapter.artifactId(environment.getId().toString(),artifact);
                    var completion=completions.stream().filter(e->{
                        var values=(List<?>)e.after().get("typedArgumentValues");
                        var feedback=CanonicalJson.object(values.get(3));
                        return expected.equals(CanonicalJson.object(feedback.get("value")).get("semanticId"));
                    }).findFirst().orElseThrow();
                    assertEquals(CartagoArgumentObservation.VERSION,completion.after().get("argumentObservationVersion"));
                    assertEquals("CARTAGO_OPID_AGENT_BODY_ID",completion.after().get("performerAuthority"));
                    var fact=facts.stream().filter(f->f.kind()==RuntimeFactKind.ARTIFACT&&expected.equals(f.values().get("semanticId"))).findFirst().orElseThrow();
                    assertEquals(completion.after().get("performerSemanticId"),fact.values().get("creatorAgentSemanticId"));
                    var args=(List<?>)completion.after().get("typedArgumentValues");var ref=CanonicalJson.object(CanonicalJson.object(args.get(3)).get("value"));
                    assertEquals(artifact.getId().toString(),ref.get("uuid"));assertEquals(workspace.getUUID().toString(),ref.get("workspaceUuid"));
                    assertEquals(artifact.getName(),CanonicalJson.object(args.getFirst()).get("value"));
                    assertEquals("auction_env.AuctionArtifact",fact.values().get("artifactTypeJavaClassName"));
                    var provider=CanonicalJson.object(fact.values().get("artifactProviderObservation"));
                    assertEquals("COMPLETE",provider.get("status"));assertEquals(artifact.getId().toString(),provider.get("artifactUuid"));
                    assertEquals(AdapterEvidence.digest(java.nio.file.Files.readAllBytes(classes.resolve("auction_env/AuctionArtifact.class"))),provider.get("classHash"));
                    assertEquals(classes.toUri().toURL().toExternalForm(),provider.get("provider"));
                }
                assertNotEquals(created.get(0).getId(),created.get(1).getId());
                for(var artifact:created)context.doAction(new Op("disposeArtifact",artifact),workspace,5000);created.clear();
            }
        } finally {
            try {
                for(var artifact:created)environment.getController("/main").removeArtifact(artifact.getName());
                for(var agent:environment.getController("/main").getCurrentAgents())if(actor.equals(agent.getAgentName()))environment.resolveWSP("/main").getWorkspace().quitAgent(agent);
            } finally {environment.shutdown();Thread.currentThread().setContextClassLoader(previous);}
        }
    }
}
