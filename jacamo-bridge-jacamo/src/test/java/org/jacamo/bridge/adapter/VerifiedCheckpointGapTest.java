package org.jacamo.bridge.adapter;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.jacamo.bridge.contract.*;
import org.jacamo.checkpoint.runtime.ExecutionBarrier;
import jacamo.checkpoint.*;
import jacamo.checkpoint.VerifiedCheckpointCoordinator.*;

class VerifiedCheckpointGapTest {
    @Test void sourceGapLatchesEvenWhenTheVerifierLaterClaimsCompletePass()throws Exception {
        String javaBinary = System.getProperty("os.name").toLowerCase().contains("win") ? "java.exe" : "java";
        var child=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin",javaBinary).toString(),"-cp",System.getProperty("java.class.path"),getClass().getName()).redirectErrorStream(true).start();
        assertTrue(child.waitFor(25,TimeUnit.SECONDS));assertEquals(0,child.exitValue(),new String(child.getInputStream().readAllBytes()));
    }
    public static void main(String[] args)throws Exception {
        String hash="a".repeat(64);Path file=Files.createTempFile("checkpoint-gap-policy-",".json");
        var policy=new VerifiedCheckpointProtocol.Policy(hash,Map.of("model",hash),List.of(new VerifiedCheckpointProtocol.Rule("post","observation:ops","fixture.Domain",Set.of("change"),Set.of(VerifiedCheckpointProtocol.REQUIRED_CHECK))));
        Files.write(file,CanonicalJson.encode(policy.payload()));System.setProperty("jacamo.execution.mode","VERIFIED_CHECKPOINTS");System.setProperty("jacamo.checkpoint.policy",file.toString());ExecutionBarrier.enable("run");
        var plan=new ObservationRequestPlan("session",1,"revision",hash,List.of(new ObservationCapabilityRequest("ops",CartagoOperationHistory.PROVIDER,true,Map.of("class","fixture.Domain","operations",List.of("change")))),Map.of());
        var mark=Map.of("source",new SourceWatermark("source",1));
        var snapshot=new RuntimeSnapshot("cut","revision",Instant.EPOCH,Instant.EPOCH,mark,mark,1,List.of(),Map.of("source",Completeness.COMPLETE),"state");
        try(var bridge=new VerifiedCheckpointBridge("session","run",1,"revision",plan,()->snapshot,()->true)) {
            bridge.observe(new RuntimeEvent("gap","session",1,"revision","source","source",1,Instant.EPOCH,RuntimeEventKind.GAP,null,null,null,null,"","",Map.of(),Map.of("diagnostic","required evidence lost"),new SourceWatermark("source",1),Completeness.UNAVAILABLE,List.of()));
            var field=VerifiedCheckpointBridge.class.getDeclaredField("coordinator");field.setAccessible(true);var coordinator=(VerifiedCheckpointCoordinator)field.get(bridge);
            long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);while(coordinator.view().request().cut()==null&&System.nanoTime()<end)Thread.sleep(10);
            var request=coordinator.view().request();assertNotNull(request.cut());
            var reply=coordinator.decide(new Decision(request.owner(),request.token(),request.sequence(),request.barrierEpoch(),policy.hash(),request.cut().hash(),hash,hash,request.executionScope(),"complete-but-gap",Action.APPROVE,Map.of(VerifiedCheckpointProtocol.REQUIRED_CHECK,new Check(Truth.PASS,Applicability.APPLICABLE,"unit complete verifier result")),""));
            assertFalse(reply.released());assertEquals(State.HELD_UNVERIFIED,reply.state());assertTrue(ExecutionBarrier.proof().admissionClosed());
            var logField=VerifiedCheckpointBridge.class.getDeclaredField("history");logField.setAccessible(true);var log=(DurableFrameLog)logField.get(bridge);boolean originalGap=false;
            for(long i=0;i<log.size();i++){var record=CanonicalJson.object(CanonicalJson.decode(log.read(i),CanonicalJson.DEFAULT_MAX_BYTES,64,CanonicalJson.DEFAULT_MAX_BYTES));if("SOURCE_EVIDENCE_GAP".equals(record.get("kind")))originalGap="required evidence lost".equals(CanonicalJson.object(CanonicalJson.object(record.get("event")).get("after")).get("diagnostic"));}
            assertTrue(originalGap);
        }finally{Files.deleteIfExists(file);}System.out.println("SOURCE_GAP_LATCH_PASS");
    }
}
