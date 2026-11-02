package org.tzi.use.plugins.jacamo.codegrounded;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.jacamo.bridge.contract.*;
import org.jacamo.bridge.contract.VerifiedCheckpointProtocol.*;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.*;
import org.tzi.use.plugins.jacamo.verification.VerificationOutcome;

class VerifiedCheckpointServiceTest {
    static final String HASH="a".repeat(64);
    static final class Port implements VerifiedCheckpointService.Port {
        volatile Map<String,Object> request=request();
        final VerificationSnapshot cut;
        final List<Map<String,Object>> audit=new CopyOnWriteArrayList<>();
        volatile String state="VERIFYING",result="",use="";
        volatile Map<String,Check> checks=Map.of();
        volatile boolean failReceipt,unavailable;
        volatile int approvals,acknowledgements;
        volatile CompletableFuture<Void> presentation=CompletableFuture.completedFuture(null);
        volatile boolean presentationRequested;
        public CompletableFuture<Void> presentCut(VerifiedCheckpointService.View checkpoint,VerificationSnapshot cut,Map<String,Check> checks){presentationRequested=true;assertEquals(this.cut,cut);return presentation;}
        public boolean presentationRequired(){return true;}
        final Map<String,Map<String,Object>> receipts=new HashMap<>();
        Port(VerificationOutcome outcome){cut=VerifiedCheckpointInventoryTest.cut(List.of(VerifiedCheckpointInventoryTest.invariant(outcome)),List.of());}
        public boolean ownsRuntime(){return true;}
        public VerificationSnapshot exactCut(Map<String,Object> requested){assertEquals(request,requested);if(unavailable)throw new IllegalStateException("source evidence unavailable");return cut;}
        public VerificationSnapshot lastNativeCut(){return cut;}
        public void audit(Map<String,Object> event,VerificationSnapshot nativeCut){audit.add(event);}
        public synchronized Map<String,Object> command(Command command) {
            if(command.action()==Action.STATUS)return status();
            var prior=receipts.get(command.commandId());if(prior!=null)return prior;
            boolean release=command.action()==Action.APPROVE||command.action()==Action.ACKNOWLEDGE_AND_CONTINUE;
            if(command.action()==Action.ACKNOWLEDGE_AND_CONTINUE){assertEquals(result,command.resultHash());assertEquals(use,command.acceptedUseCutHash());assertEquals(checks,command.checks());acknowledgements++;}
            else {result=command.resultHash();use=command.acceptedUseCutHash();checks=command.checks();}
            if(command.action()==Action.APPROVE)approvals++;
            state=release?"RUNNING":command.action()==Action.HOLD_FAILURE?"HELD_FAILURE":"HELD_UNVERIFIED";
            var response=new LinkedHashMap<>(status());
            response.put("receipt",Map.of("commandId",command.commandId(),"token",command.identity().token(),"resultHash",result,"acceptedUseCutHash",use,"released",release,"state",state,"diagnostic","source receipt"));
            receipts.put(command.commandId(),Map.copyOf(response));
            if(failReceipt){failReceipt=false;throw new IllegalStateException("connection lost after source accepted command");}
            return response;
        }
        Map<String,Object> status(){var rows=new TreeMap<String,Object>();checks.forEach((id,c)->rows.put(id,c.payload()));return Map.of("version",VerifiedCheckpointProtocol.VERSION,"state",state,"request",request,"diagnostic","source status","resultHash",result,"acceptedUseCutHash",use,"checks",rows);}
    }
    static Map<String,Object> request() {
        var identity=new Identity("session","run",1,"revision","active-token",1,1,HASH,VerifiedCheckpointProtocol.hash(Map.of("state","original")),"",Set.of("execution"));
        var map=new LinkedHashMap<String,Object>();map.put("identity",identity.payload());map.put("checkpointId","source-post");map.put("sourceOccurrences",List.of("event"));map.put("requiredChecks",List.of(VerifiedCheckpointProtocol.REQUIRED_CHECK));map.put("inputHashes",Map.of("model",HASH));map.put("watermarks",Map.of("source",1));
        map.put("barrier",Map.of("admissionClosed",true,"clockFrozen",true,"active",Map.of(),"pending",0,"epoch",1));map.put("snapshot",Map.of("state","original"));map.put("ready",true);
        var signed=new LinkedHashMap<>(identity.payload());signed.put("requestHash",VerifiedCheckpointProtocol.hash(map));map.put("identity",signed);return Map.copyOf(map);
    }
    static void await(java.util.function.BooleanSupplier condition)throws Exception {long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(6);while(!condition.getAsBoolean()&&System.nanoTime()<end)Thread.sleep(20);assertTrue(condition.getAsBoolean());}
    @Test void nativePassReleasesOnlyAfterReceiptAndLostReplyRetriesSameCommand()throws Exception {
        var port=new Port(VerificationOutcome.PASS);port.failReceipt=true;
        try(var service=new VerifiedCheckpointService(port)) {
            await(()->service.history().stream().anyMatch(v->v.state().equals("RUNNING")));
            assertEquals(1,port.approvals);assertEquals(0,port.acknowledgements);
            assertTrue(port.audit.stream().anyMatch(a->a.get("kind").equals("DECISION_RECEIPT")));
        }
    }
    @Test void qualifiedPassCannotReleaseUntilTheExactCutIsDisplayed()throws Exception {
        var port=new Port(VerificationOutcome.PASS);port.presentation=new CompletableFuture<>();
        try(var service=new VerifiedCheckpointService(port)) {
            await(()->port.presentationRequested);Thread.sleep(250);
            assertEquals(0,port.approvals);assertEquals("VERIFYING",port.state);
            assertEquals("WAITING_FOR_CHECKPOINT_DISPLAY",service.view().reason());assertFalse(service.view().reviewable());
            port.presentation.complete(null);await(()->port.approvals==1);
            assertTrue(port.audit.stream().anyMatch(e->e.get("kind").equals("DISPLAYED")));
        }
    }
    @Test void failedPresentationHoldsWithoutChangingNativeTruthOrAutoApprovingOnRecovery()throws Exception {
        var port=new Port(VerificationOutcome.PASS);String nativeHash=port.cut.result().resultHash();
        port.presentation=CompletableFuture.failedFuture(new IllegalStateException("view closed"));
        try(var service=new VerifiedCheckpointService(port)) {
            await(()->port.state.equals("HELD_UNVERIFIED"));assertEquals(0,port.approvals);assertFalse(service.view().reviewable());
            assertEquals(nativeHash,port.cut.result().resultHash());
            port.presentation=CompletableFuture.completedFuture(null);await(()->service.view().reviewable());
            assertEquals(0,port.approvals);assertEquals("ERROR",port.checks.get("use.presentation").truth());
            service.acknowledge("active-token",service.view().resultHash()).get(5,TimeUnit.SECONDS);
            assertEquals(1,port.acknowledgements);assertEquals(nativeHash,port.cut.result().resultHash());
        }
    }
    @Test void nativeFailureReconnectAndExactReviewPreserveOriginalFailure()throws Exception {
        var port=new Port(VerificationOutcome.FAIL);
        try(var first=new VerifiedCheckpointService(port)){await(()->first.view().reviewable());assertEquals("HELD_FAILURE",first.view().state());}
        String failedHash=port.result;
        try(var service=new VerifiedCheckpointService(port)) {
            await(()->service.view().reviewable());assertEquals(failedHash,service.view().resultHash());
            assertThrows(IllegalStateException.class,()->service.acknowledge("historical-token",failedHash));assertEquals(0,port.acknowledgements);
            service.acknowledge("active-token",failedHash).get(5,TimeUnit.SECONDS);
            assertEquals(failedHash,port.result);assertEquals("FAIL",port.checks.get(VerifiedCheckpointProtocol.REQUIRED_CHECK).truth());
            assertEquals(1,port.acknowledgements);assertEquals(0,port.approvals);
        }
    }
    @Test void everySubsequentFailedTokenCanBeAcknowledgedAgainWithoutReusingAnOldCommand()throws Exception {
        var port=new Port(VerificationOutcome.FAIL);
        try(var service=new VerifiedCheckpointService(port)){
            for(int i=0;i<3;i++){
                if(i>0){synchronized(port){var body=new LinkedHashMap<>(request());var identity=new LinkedHashMap<>(CanonicalJson.object(body.get("identity")));identity.put("token","token-"+i);identity.put("sequence",i+1);identity.put("barrierEpoch",i+1);identity.put("requestHash","");var barrier=new LinkedHashMap<>(CanonicalJson.object(body.get("barrier")));barrier.put("epoch",i+1);body.put("barrier",barrier);body.put("identity",identity);identity.put("requestHash",VerifiedCheckpointProtocol.hash(body));port.request=Map.copyOf(body);port.state="VERIFYING";port.result="";port.use="";port.checks=Map.of();}}
                String token=i==0?"active-token":"token-"+i;
                long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(6);while((!service.view().token().equals(token)||!service.view().reviewable())&&System.nanoTime()<end)Thread.sleep(20);
                assertTrue(service.view().token().equals(token)&&service.view().reviewable(),"iteration="+i+" view="+service.view());
                service.acknowledge(token,service.view().resultHash()).get(5,TimeUnit.SECONDS);
                assertFalse(service.view().pending());assertEquals("RUNNING",service.view().state());assertEquals(i+1,port.acknowledgements);
            }
        }
    }
    @Test void missingEvidenceCloseAndReconnectCannotBecomePass()throws Exception {
        var port=new Port(VerificationOutcome.PASS);port.unavailable=true;
        try(var first=new VerifiedCheckpointService(port)){await(()->first.view().reviewable());assertEquals("ERROR",port.checks.get(VerifiedCheckpointProtocol.REQUIRED_CHECK).truth());}
        String original=port.result;port.unavailable=false;
        try(var resumed=new VerifiedCheckpointService(port)) {
            await(()->resumed.view().reviewable());assertEquals("HELD_UNVERIFIED",resumed.view().state());assertEquals(original,resumed.view().resultHash());
            resumed.acknowledge("active-token",original).get(5,TimeUnit.SECONDS);
            assertEquals(original,port.result);assertEquals("ERROR",port.checks.get(VerifiedCheckpointProtocol.REQUIRED_CHECK).truth());assertEquals(0,port.approvals);
        }
    }
}
