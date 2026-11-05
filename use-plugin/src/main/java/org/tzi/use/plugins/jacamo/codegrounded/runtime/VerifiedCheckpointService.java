package org.tzi.use.plugins.jacamo.codegrounded.runtime;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.jacamo.bridge.contract.*;
import org.jacamo.bridge.contract.VerifiedCheckpointProtocol.*;

/** One worker per admitted native owner. UI only reads immutable live status. */
public final class VerifiedCheckpointService implements AutoCloseable {
    public interface Port {
        Map<String,Object> command(Command command);
        VerificationSnapshot exactCut(Map<String,Object> request) throws Exception;
        VerificationSnapshot lastNativeCut();
        void audit(Map<String,Object> event,VerificationSnapshot cut);
        boolean ownsRuntime();
        default Map<String,Check> nativeChecks(VerificationSnapshot cut){return VerifiedCheckpointInventory.evaluate(cut);}
        default CompletableFuture<Void> presentCut(View checkpoint,VerificationSnapshot cut,Map<String,Check> checks){return CompletableFuture.completedFuture(null);}
        default boolean presentationRequired(){return false;}
    }
    public record View(String state,String token,String checkpoint,String reason,String producerHash,String useHash,String resultHash,
                       Set<String> executionScope,boolean reviewable,boolean pending,Map<String,Object> request) {
        public View{executionScope=Set.copyOf(executionScope);request=Collections.unmodifiableMap(new LinkedHashMap<>(request));}
        public static View idle(){return new View("NOT_STARTED","","","Waiting for runtime","","","",Set.of(),false,false,Map.of());}
    }
    private final Port port;
    private final ScheduledExecutorService worker=Executors.newSingleThreadScheduledExecutor(r->Thread.ofPlatform().daemon().name("use-verified-checkpoints").unstarted(r));
    private final AtomicBoolean closed=new AtomicBoolean(),reviewPending=new AtomicBoolean();
    private volatile View view=View.idle();
    private Identity evaluated;private VerificationSnapshot nativeCut;private Map<String,Check> checks=Map.of();private String useHash="",resultHash="";
    private boolean presentationReady;
    private Command pendingCommand;private final Map<String,View> reviewedHistory=new LinkedHashMap<>();
    public VerifiedCheckpointService(Port port){this.port=Objects.requireNonNull(port);worker.scheduleWithFixedDelay(this::poll,0,100,TimeUnit.MILLISECONDS);}
    public View view(){return view;}
    public synchronized List<View> history(){return List.copyOf(reviewedHistory.values());}
    private void poll() {
        if(closed.get()||!port.ownsRuntime())return;
        try {
            if(pendingCommand!=null){deliver();return;}
            var status=port.command(Command.status());
            if(!VerifiedCheckpointProtocol.VERSION.equals(status.get("version")))throw new IllegalStateException("CHECKPOINT_STATUS_VERSION");
            String state=(String)status.get("state");Object raw=status.get("request");
            if(!(raw instanceof Map<?,?>)) {
                view=new View(state,"","",Objects.toString(status.get("diagnostic"),""),"","","",Set.of(),false,false,Map.of());return;
            }
            var request=CanonicalJson.object(raw);VerifiedCheckpointProtocol.requireRequest(request);
            var identity=Identity.decode(CanonicalJson.object(request.get("identity")));
            view=present(state,identity,request,Objects.toString(status.get("diagnostic"),""),false);
            if(!Boolean.TRUE.equals(request.get("ready")))return;
            if(identity.equals(evaluated)) {
                if(!presentationReady&&nativeCut!=null)displayCut(identity,request,nativeCut);
                view=present(state,identity,request,Objects.toString(status.get("diagnostic"),""),false);return;
            }
            presentationReady=false;
            var cut=port.exactCut(request);
            if(closed.get()||!port.ownsRuntime())return;
            if(Set.of("HELD_FAILURE","HELD_UNVERIFIED").contains(state)&&status.get("resultHash") instanceof String retained&&!retained.isBlank()) {
                String retainedUse=Objects.toString(status.get("acceptedUseCutHash"),"");
                var heldChecks=new TreeMap<String,Check>();CanonicalJson.object(status.get("checks")).forEach((id,rawCheck)->{var row=CanonicalJson.object(rawCheck);heldChecks.put(id,new Check((String)row.get("truth"),(String)row.get("applicability"),(String)row.get("diagnostic")));});
                String unavailableHash=VerifiedCheckpointProtocol.hash(Map.of("status","EXACT_NATIVE_CUT_UNAVAILABLE","requestHash",identity.requestHash()));
                boolean unavailableEvidence=retainedUse.equals(unavailableHash)&&heldChecks.containsKey(VerifiedCheckpointProtocol.REQUIRED_CHECK)
                        &&heldChecks.get(VerifiedCheckpointProtocol.REQUIRED_CHECK).truth().equals("ERROR");
                if(cut.result()==null||!retainedUse.equals(cut.result().stateHash())&&!unavailableEvidence)throw new IllegalStateException("CHECKPOINT_RECONNECTED_NATIVE_STATE_DIFFERS_FROM_REVIEWED_CUT");
                nativeCut=cut;useHash=retainedUse;resultHash=retained;checks=Map.copyOf(heldChecks);evaluated=identity;
                port.audit(Map.of("version","VERIFIED_CHECKPOINT_AUDIT/1.0","kind","RECONCILED_HELD","request",request,"originalResultHash",retained,"originalUseHash",retainedUse,"checks",checksPayload(checks)),cut);
                displayCut(identity,request,cut);
                view=present(state,identity,request,Objects.toString(status.get("diagnostic")),false);return;
            }
            checks=VerifiedCheckpointInventory.settled(cut,port.nativeChecks(cut),((List<?>)request.get("requiredChecks")).stream().map(String.class::cast).toList());nativeCut=cut;useHash=cut.result().stateHash();
            resultHash=VerifiedCheckpointProtocol.hash(Map.of("version",VerifiedCheckpointProtocol.INVENTORY,"nativeResultHash",cut.result().resultHash(),"checks",checksPayload(checks)));evaluated=identity;
            port.audit(Map.of("version","VERIFIED_CHECKPOINT_AUDIT/1.0","kind","EVALUATED","request",request,"state",state,"checks",checksPayload(checks),"acceptedUseCutHash",useHash,"resultHash",resultHash,"nativeResultHash",cut.result().resultHash()),cut);
            try {displayCut(identity,request,cut);}
            catch(Exception displayFailure) {
                var retained=new TreeMap<>(checks);
                retained.put("use.presentation",new Check("ERROR","APPLICABLE","CHECKPOINT_DISPLAY_UNAVAILABLE:"+displayFailure.getClass().getSimpleName()+":"+displayFailure.getMessage()));
                retained.put(VerifiedCheckpointProtocol.REQUIRED_CHECK,new Check("ERROR","APPLICABLE","CHECKPOINT_DISPLAY_UNAVAILABLE"));checks=Map.copyOf(retained);
                resultHash=VerifiedCheckpointProtocol.hash(Map.of("version",VerifiedCheckpointProtocol.INVENTORY,"nativeResultHash",cut.result().resultHash(),"checks",checksPayload(checks)));
                port.audit(Map.of("version","VERIFIED_CHECKPOINT_AUDIT/1.0","kind","PRESENTATION_UNAVAILABLE","request",request,"checks",checksPayload(checks),"resultHash",resultHash,"nativeResultHash",cut.result().resultHash()),cut);
            }
            if(closed.get()||!port.ownsRuntime())return;
            var aggregate=checks.get(VerifiedCheckpointProtocol.REQUIRED_CHECK);
            Action action=aggregate.truth().equals("PASS")?Action.APPROVE:aggregate.truth().equals("FAIL")?Action.HOLD_FAILURE:Action.HOLD_UNVERIFIED;
            pendingCommand=new Command(action,UUID.randomUUID().toString(),identity,useHash,resultHash,checks,aggregate.diagnostic());deliver();
        }catch(RuntimeException unavailable){unavailable(unavailable);}
        catch(Exception unavailable){unavailable(new IllegalStateException("CHECKPOINT_NATIVE_CUT_UNAVAILABLE",unavailable));}
    }
    private void displayCut(Identity identity,Map<String,Object> request,VerificationSnapshot cut)throws Exception {
        view=present("VERIFYING",identity,request,"WAITING_FOR_CHECKPOINT_DISPLAY",false);
        port.presentCut(view,cut,checks).get(30,TimeUnit.SECONDS);
        if(closed.get()||!port.ownsRuntime())throw new IllegalStateException("CHECKPOINT_VIEW_OWNER_RETIRED");
        presentationReady=true;
        port.audit(Map.of("version","VERIFIED_CHECKPOINT_AUDIT/1.0","kind",port.presentationRequired()?"DISPLAYED":"VERIFIED_RESULT_READY","request",request,"resultHash",resultHash,"nativeResultHash",cut.result().resultHash()),cut);
    }
    private void deliver() {
        var command=pendingCommand;if(command==null)return;
        var response=port.command(command);var receipt=CanonicalJson.object(response.get("receipt"));
        if(!command.commandId().equals(receipt.get("commandId"))||!command.identity().token().equals(receipt.get("token"))
                ||!command.resultHash().equals(receipt.get("resultHash"))||!command.acceptedUseCutHash().equals(receipt.get("acceptedUseCutHash")))throw new IllegalStateException("CHECKPOINT_RELEASE_RECEIPT_IDENTITY_MISMATCH");
        port.audit(Map.of("version","VERIFIED_CHECKPOINT_AUDIT/1.0","kind",command.action()==Action.ACKNOWLEDGE_AND_CONTINUE?"ACKNOWLEDGEMENT_RECEIPT":"DECISION_RECEIPT","command",command.payload(),"receipt",receipt),nativeCut);
        boolean released=Boolean.TRUE.equals(receipt.get("released"));String state=Objects.toString(receipt.get("state"));
        Map<String,Object> request=view.request();
        if(!released&&response.get("request") instanceof Map<?,?> current)request=CanonicalJson.object(current);
        view=present(released?"RUNNING":state,command.identity(),request,Objects.toString(receipt.get("diagnostic")),false);
        synchronized(this){reviewedHistory.put(command.identity().token(),view);}
        pendingCommand=null;reviewPending.set(false);
        view=present(released?"RUNNING":state,command.identity(),request,Objects.toString(receipt.get("diagnostic")),false);
    }
    private View present(String state,Identity identity,Map<String,Object> request,String reason,boolean pending) {
        boolean reviewed=identity.equals(evaluated)&&!resultHash.isBlank();
        boolean held=Set.of("HELD_FAILURE","HELD_UNVERIFIED").contains(state);
        return new View(state,identity.token(),Objects.toString(request.get("checkpointId")),reason,identity.producerHash(),reviewed?useHash:"",reviewed?resultHash:"",identity.executionScope(),held&&reviewed&&presentationReady&&!pending&&!reviewPending.get(),pending||reviewPending.get(),request);
    }
    /** Expected token/result are captured from live controls, never history selection. */
    public CompletableFuture<View> acknowledge(String token,String reviewedResultHash) {
        View current=view;
        if(closed.get()||!current.reviewable()||!current.token().equals(token)||!current.resultHash().equals(reviewedResultHash)||!reviewPending.compareAndSet(false,true))throw new IllegalStateException("CHECKPOINT_ACTIVE_REVIEW_REQUIRED");
        view=new View(current.state(),current.token(),current.checkpoint(),current.reason(),current.producerHash(),current.useHash(),current.resultHash(),current.executionScope(),false,true,current.request());
        var future=new CompletableFuture<View>();worker.execute(()->{
            try {
                if(!port.ownsRuntime()||evaluated==null||!evaluated.token().equals(token)||!resultHash.equals(reviewedResultHash))throw new IllegalStateException("CHECKPOINT_REVIEW_REPLACED");
                pendingCommand=new Command(Action.ACKNOWLEDGE_AND_CONTINUE,UUID.randomUUID().toString(),evaluated,useHash,resultHash,checks,"USER_REVIEWED_EXACT_HELD_CHECKPOINT");
                port.audit(Map.of("version","VERIFIED_CHECKPOINT_AUDIT/1.0","kind","ACKNOWLEDGEMENT_REQUESTED","command",pendingCommand.payload()),nativeCut);deliver();future.complete(view);
            }catch(RuntimeException failed){unavailable(failed);future.completeExceptionally(failed);}
        });return future;
    }
    private void unavailable(RuntimeException failure) {
        View current=view;view=new View("HELD_UNVERIFIED",current.token(),current.checkpoint(),"Verification unavailable: "+failure.getClass().getSimpleName()+":"+failure.getMessage(),current.producerHash(),current.useHash(),current.resultHash(),current.executionScope(),current.reviewable()&&!reviewPending.get(),reviewPending.get(),current.request());
        if(pendingCommand==null&&(evaluated==null||!evaluated.token().equals(current.token()))&&Boolean.TRUE.equals(current.request().get("ready"))&&port.ownsRuntime()) {
            try {
                var identity=Identity.decode(CanonicalJson.object(current.request().get("identity")));
                var available=port.lastNativeCut();if(available==null||available.result()==null)return;
                String reason=view.reason();
                var error=Map.<String,Object>of("version","CHECKPOINT_VERIFICATION_UNAVAILABLE/1.0","requestHash",identity.requestHash(),"diagnostic",reason,"lastNativeResultHash",available.result().resultHash(),"acceptedExactNativeCut",false);
                useHash=VerifiedCheckpointProtocol.hash(Map.of("status","EXACT_NATIVE_CUT_UNAVAILABLE","requestHash",identity.requestHash()));resultHash=VerifiedCheckpointProtocol.hash(error);
                checks=Map.of(VerifiedCheckpointProtocol.REQUIRED_CHECK,new Check("ERROR","APPLICABLE",reason));nativeCut=available;evaluated=identity;
                port.audit(Map.of("version","VERIFIED_CHECKPOINT_AUDIT/1.0","kind","VERIFICATION_UNAVAILABLE","request",current.request(),"error",error,"resultHash",resultHash,"acceptedUseCutHash",useHash),available);
                pendingCommand=new Command(Action.HOLD_UNVERIFIED,UUID.randomUUID().toString(),identity,useHash,resultHash,checks,reason);
                view=present("HELD_UNVERIFIED",identity,current.request(),reason,true);
            }catch(RuntimeException persistenceOrIdentityFailure){/* Original unavailability keeps the producer hold; retry requires a valid cut. */}
        }
    }
    private static Map<String,Object> checksPayload(Map<String,Check> checks){var rows=new TreeMap<String,Object>();checks.forEach((id,c)->rows.put(id,c.payload()));return Map.copyOf(rows);}
    @Override public void close(){if(closed.compareAndSet(false,true)){worker.shutdownNow();}}
}
