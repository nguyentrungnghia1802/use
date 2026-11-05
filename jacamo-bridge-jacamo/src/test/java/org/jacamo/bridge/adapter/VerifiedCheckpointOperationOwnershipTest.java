package org.jacamo.bridge.adapter;

import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import cartago.*;
import cartago.util.agent.CartagoBasicContext;
import org.jacamo.bridge.contract.*;
import org.jacamo.checkpoint.runtime.ExecutionBarrier;
import jacamo.checkpoint.VerifiedCheckpointCoordinator;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VerifiedCheckpointOperationOwnershipTest {
    @Test void actualLocalOpIdentityOwnsOnlyItsInvocationAndUnrelatedSuccessStillStops() throws Exception {
        child();
    }
    @Test void selectedTaskOwnershipDoesNotSuppressAnActualOperationFailure() throws Exception { child("failure"); }
    private void child(String... args) throws Exception {
        var command=new ArrayList<>(List.of(Path.of(System.getProperty("java.home"),"bin","java").toString(),
                "-Djava.awt.headless=true","-cp",System.getProperty("java.class.path"),getClass().getName()));command.addAll(List.of(args));
        var child=new ProcessBuilder(command).redirectErrorStream(true).start();
        if(!child.waitFor(25,TimeUnit.SECONDS)){child.destroyForcibly();child.waitFor();fail(new String(child.getInputStream().readAllBytes()));}
        assertEquals(0,child.exitValue(),new String(child.getInputStream().readAllBytes()));
    }
    public static void main(String[] args) throws Exception {
        try{run(args.length>0);System.exit(0);}catch(Throwable failed){failed.printStackTrace();System.exit(1);}
    }
    private static void run(boolean failure) throws Exception {
        String hash="a".repeat(64),type=OperationReceiptArtifact.class.getName();Path policyFile=Files.createTempFile("operation-owner-policy-",".json");
        var policy=new VerifiedCheckpointProtocol.Policy(hash,Map.of("source",hash),List.of(
                new VerifiedCheckpointProtocol.Rule("task","observation:steps",type,Set.of("Offer"),Set.of(VerifiedCheckpointProtocol.REQUIRED_CHECK),"TASK_POST"),
                new VerifiedCheckpointProtocol.Rule("fallback","observation:operations",type,Set.of("offer"),Set.of(VerifiedCheckpointProtocol.REQUIRED_CHECK),"OPERATION_UNMAPPED_POST")));
        Files.write(policyFile,CanonicalJson.encode(policy.payload()));System.setProperty("jacamo.checkpoint.policy",policyFile.toString());System.setProperty("jacamo.execution.mode","VERIFIED_CHECKPOINTS");
        var plan=new ObservationRequestPlan("session",1,"revision",hash,List.of(
                new ObservationCapabilityRequest("steps",JasonStepCapability.PROVIDER,true,Map.of("plans",List.of(Map.of("steps",List.of(Map.of("task","Offer","artifactClass",type,"allowMissingArtifactAtEntry",true)))))),
                new ObservationCapabilityRequest("operations",CartagoOperationHistory.PROVIDER,true,Map.of("class",type,"operations",List.of("offer")))),Map.of());
        var marks=Map.of("source",new SourceWatermark("source",1));
        var snapshot=new RuntimeSnapshot("cut","revision",Instant.EPOCH,Instant.EPOCH,marks,marks,1,List.of(),Map.of("source",Completeness.COMPLETE),"state");
        var env=CartagoEnvironment.getInstance();env.init();var actor=new CartagoBasicContext("performer");
        ArtifactId artifact=actor.makeArtifact(actor.getJoinedWspId("main"),"domain",type);ExecutionBarrier.enable("run");
        var events=new CopyOnWriteArrayList<RuntimeEvent>();var captures=new AtomicInteger();
        try(var bridge=new VerifiedCheckpointBridge("session","run",1,"revision",plan,()->{captures.incrementAndGet();return snapshot;},()->true);
            var source=new CartagoSnapshotSource(env,List.of("/main"),"run")) {
            source.attach(event->{events.add(event);bridge.observe(event);});
            var claim=Map.<String,Object>of("authority","LOCAL_ACTIONEXEC_OP_OBJECT_IDENTITY/1.0","task","Offer","stepInvocation","step-1","agentName","performer");
            if(failure) {
                try(var dispatch=TaskOperationDispatch.enter(claim)){
                    assertThrows(cartago.util.agent.ActionFailedException.class,()->actor.doAction(artifact,new Op("offer",-1.0)));
                }
                long limit=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);while(captures.get()==0&&System.nanoTime()<limit)Thread.sleep(5);
                assertEquals(1,captures.get());assertTrue(ExecutionBarrier.proof().clockFrozen());
                var failed=events.stream().filter(e->e.kind()==RuntimeEventKind.FAILED).findFirst().orElseThrow();
                assertEquals(claim,failed.after().get("taskDispatch"));assertTrue(failed.after().toString().contains("negative"));
                System.out.println("ACTUAL_OWNED_OPERATION_FAILURE_RETAINS_BOUNDARY_AND_EVIDENCE_PASS");return;
            }
            try(var dispatch=TaskOperationDispatch.enter(claim)){actor.doAction(artifact,new Op("offer",5.0));}
            assertFalse(bridge.active(),"Selected Task owns that exact successful operation");
            var owned=events.stream().filter(e->e.kind()==RuntimeEventKind.SUCCEEDED&&"offer".equals(e.after().get("operation"))).findFirst().orElseThrow();
            assertEquals(claim,owned.after().get("taskDispatch"));
            actor.doAction(artifact,new Op("offer",6.0));
            long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);while(captures.get()==0&&System.nanoTime()<end)Thread.sleep(5);
            assertEquals(1,captures.get());assertTrue(bridge.active());assertTrue(ExecutionBarrier.proof().clockFrozen());
            var success=events.stream().filter(e->e.kind()==RuntimeEventKind.SUCCEEDED&&"offer".equals(e.after().get("operation"))).toList();
            assertEquals(2,success.size());assertFalse(success.getLast().after().containsKey("taskDispatch"));
            var field=VerifiedCheckpointBridge.class.getDeclaredField("coordinator");field.setAccessible(true);
            var request=((VerifiedCheckpointCoordinator)field.get(bridge)).view().request();
            assertEquals("fallback",request.boundary().checkpointId());assertEquals(success.getLast().eventId(),request.boundary().sourceOccurrence());
            System.out.println("ACTUAL_OP_IDENTITY_AND_SAME_CLASS_UNMAPPED_SUCCESS_PASS");
        } finally { ExecutionBarrier.stop();env.shutdown();Files.deleteIfExists(policyFile); }
        System.exit(0);
    }
}
