package org.jacamo.bridge.adapter;

import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import javax.crypto.*;
import javax.crypto.spec.SecretKeySpec;
import org.jacamo.bridge.contract.*;
import org.jacamo.checkpoint.runtime.ExecutionBarrier;
import jacamo.checkpoint.VerifiedCheckpointCoordinator;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Transport/identity fixture only: it supplies no native domain verdict. */
class CheckpointReceiptReplayTest {
    public static class Runner extends jason.infra.local.RunLocalMAS {void current(){runner=this;}}
    @Test void aLostDecisionReplyCanBeRecoveredWithoutCapturingTheFollowingCut()throws Exception {
        var child=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java.exe").toString(),"-cp",System.getProperty("java.class.path"),getClass().getName()).redirectErrorStream(true).start();
        assertTrue(child.waitFor(25,TimeUnit.SECONDS));assertEquals(0,child.exitValue(),new String(child.getInputStream().readAllBytes(),StandardCharsets.UTF_8));
    }
    public static void main(String[] args)throws Exception {
        String hash="a".repeat(64);Path policyFile=Files.createTempFile("receipt-replay-policy-",".json");
        var policy=new VerifiedCheckpointProtocol.Policy(hash,Map.of("source",hash),List.of(new VerifiedCheckpointProtocol.Rule("post","observation:ops","fixture.Domain",Set.of("change"),Set.of(VerifiedCheckpointProtocol.REQUIRED_CHECK))));
        Files.write(policyFile,CanonicalJson.encode(policy.payload()));System.setProperty("jacamo.execution.mode","VERIFIED_CHECKPOINTS");System.setProperty("jacamo.checkpoint.policy",policyFile.toString());ExecutionBarrier.enable("fixture-run");
        var runner=new Runner();runner.current();
        var plan=new ObservationRequestPlan("session",1,"revision",hash,List.of(new ObservationCapabilityRequest("ops",CartagoOperationHistory.PROVIDER,true,Map.of("class","fixture.Domain","operations",List.of("change")))),Map.of());
        var mark=Map.of("source",new SourceWatermark("source",1));var snapshot=new RuntimeSnapshot("cut","revision",Instant.EPOCH,Instant.EPOCH,mark,mark,1,List.of(),Map.of("source",Completeness.COMPLETE),"state");
        byte[] secret=new byte[32];Arrays.fill(secret,(byte)7);
        try(var bridge=new VerifiedCheckpointBridge("session","fixture-run",1,"revision",plan,()->snapshot,()->true);
            var server=new LocalTcpBridgeServer(InetAddress.getLoopbackAddress(),0,secret,1024*1024,32,1024*1024,()->new byte[]{1},()->new byte[]{1},()->new byte[]{1},()->new byte[]{1})) {
            server.checkpointHandler(command->CanonicalJson.encode(bridge.command(command)),()->{});server.start();
            var field=VerifiedCheckpointBridge.class.getDeclaredField("coordinator");field.setAccessible(true);var coordinator=(VerifiedCheckpointCoordinator)field.get(bridge);
            coordinator.trigger(new VerifiedCheckpointCoordinator.Boundary(policy.hash(),"post","first",Set.of("scope"),Set.of(VerifiedCheckpointProtocol.REQUIRED_CHECK),policy.inputHashes()));awaitCut(coordinator);
            var status=CanonicalJson.object(CanonicalJson.decode(call(server.port(),secret,VerifiedCheckpointProtocol.Command.status())));
            var identity=VerifiedCheckpointProtocol.Identity.decode(CanonicalJson.object(CanonicalJson.object(status.get("request")).get("identity")));
            var command=new VerifiedCheckpointProtocol.Command(VerifiedCheckpointProtocol.Action.APPROVE,"lost-reply",identity,hash,hash,Map.of(VerifiedCheckpointProtocol.REQUIRED_CHECK,new VerifiedCheckpointProtocol.Check("PASS","APPLICABLE","protocol fixture")),"");
            byte[] original=call(server.port(),secret,command);assertTrue(Boolean.TRUE.equals(CanonicalJson.object(CanonicalJson.object(CanonicalJson.decode(original)).get("receipt")).get("released")));
            // The consumer did not receive/use the reply. Another cut now has
            // missing cohort evidence and MUST remain held; that does not revoke
            // the already persisted receipt for the preceding command.
            var unadmitted=new jason.infra.local.LocalAgArch();unadmitted.setAgName("unadmitted-fixture");runner.getAgs().put(unadmitted.getAgName(),unadmitted);
            coordinator.trigger(new VerifiedCheckpointCoordinator.Boundary(policy.hash(),"post","second",Set.of("scope"),Set.of(VerifiedCheckpointProtocol.REQUIRED_CHECK),policy.inputHashes()));awaitCut(coordinator);
            String next=coordinator.view().request().token();assertNotEquals(identity.token(),next);
            assertArrayEquals(original,call(server.port(),secret,command));
            assertEquals(next,coordinator.view().request().token());assertTrue(ExecutionBarrier.proof().admissionClosed());
            assertThrows(RuntimeException.class,()->bridge.command(VerifiedCheckpointProtocol.Command.status()),"New missing cohort evidence is not repaired by the old receipt");
            var collision=new VerifiedCheckpointProtocol.Command(command.action(),command.commandId(),identity,hash,"b".repeat(64),command.checks(),"");
            assertThrows(IllegalArgumentException.class,()->bridge.command(collision));assertTrue(ExecutionBarrier.proof().admissionClosed());
        }finally{Files.deleteIfExists(policyFile);}System.out.println("LOST_REPLY_RECOVERED_NEXT_CUT_STILL_HELD");
    }
    static void awaitCut(VerifiedCheckpointCoordinator coordinator)throws Exception {long limit=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);while(coordinator.view().request().cut()==null&&System.nanoTime()<limit)Thread.sleep(5);assertNotNull(coordinator.view().request().cut());}
    static byte[] call(int port,byte[] secret,VerifiedCheckpointProtocol.Command command)throws Exception {
        String nonce=UUID.randomUUID().toString();var mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(secret,"HmacSHA256"));
        String text="CHECKPOINT\n\n"+nonce+"\n"+new String(CanonicalJson.encode(command.payload()),StandardCharsets.UTF_8);
        return VerifiedCheckpointAuthenticationTest.exchange(port,Map.of("operation","CHECKPOINT","resumeToken","","nonce",nonce,"mac",HexFormat.of().formatHex(mac.doFinal(text.getBytes(StandardCharsets.UTF_8))),"checkpoint",command.payload()));
    }
}
