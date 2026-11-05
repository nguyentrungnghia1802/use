package org.jacamo.bridge.adapter;

import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import jacamo.checkpoint.VerifiedCheckpointCoordinator;
import org.jacamo.bridge.contract.*;
import org.jacamo.checkpoint.runtime.ExecutionBarrier;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Selector/protocol fixtures; not a native semantic verdict or a live MAS run. */
class VerifiedCheckpointLifecycleSelectionTest {
    @Test void admittedSchemeRootAndAdministrativeEffectsAndNormTerminalsSelectCuts() throws Exception {
        for(String kind:List.of("terminal","administrative","norm")) {
            var child=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java").toString(),
                    "-Djava.awt.headless=true","-cp",System.getProperty("java.class.path"),getClass().getName(),kind).redirectErrorStream(true).start();
            if(!child.waitFor(25,TimeUnit.SECONDS)){child.destroyForcibly();child.waitFor();fail(new String(child.getInputStream().readAllBytes()));}
            assertEquals(0,child.exitValue(),new String(child.getInputStream().readAllBytes()));
        }
    }
    public static void main(String[] args) {
        try{run(args[0]);System.exit(0);}catch(Throwable failure){failure.printStackTrace();System.exit(1);}
    }
    private static void run(String kind) throws Exception {
        Path jcm=Path.of("../use-plugin/examples/auction/auction.jcm").toRealPath();
        var model=new OfficialProjectAdapter().adapt(new OfficialProjectLoader().load(jcm),jcm);
        var organization=model.semanticContract().moiseOrganizations().getFirst();var scheme=organization.functionalSpecification().schemes().getFirst();
        String root=scheme.goals().stream().filter(g->g.metadata().semanticId().equals(scheme.rootGoalSemanticId())).findFirst().orElseThrow().goalId();
        String hash="a".repeat(64),selector=kind.equals("norm")?"NORM_TERMINAL":"SCHEME_POST";
        var request=new ObservationCapabilityRequest("lifecycle",kind.equals("norm")?NplLifecycleCapability.PROVIDER:MoiseSchemeCapability.PROVIDER,true,
                kind.equals("norm")?Map.of("hash",hash):Map.of("sourceRef","source","organization",organization.name(),"scheme",scheme.schemeId()));
        var policy=new VerifiedCheckpointProtocol.Policy(hash,Map.of("source",hash),List.of(new VerifiedCheckpointProtocol.Rule("lifecycle","observation:lifecycle","",kind.equals("norm")?Set.of("unfulfilled"):Set.of("terminal","administrative"),Set.of(VerifiedCheckpointProtocol.REQUIRED_CHECK),selector)));
        Path file=Files.createTempFile("lifecycle-policy-",".json");Files.write(file,CanonicalJson.encode(policy.payload()));
        System.setProperty("jacamo.execution.mode","VERIFIED_CHECKPOINTS");System.setProperty("jacamo.checkpoint.policy",file.toString());ExecutionBarrier.enable("run");
        var plan=new ObservationRequestPlan("session",1,model.modelRevision(),hash,List.of(request),Map.of());var marks=Map.of("source",new SourceWatermark("source",1));
        var snapshot=new RuntimeSnapshot("cut",model.modelRevision(),Instant.EPOCH,Instant.EPOCH,marks,marks,1,List.of(),Map.of("source",Completeness.COMPLETE),"state");var captures=new AtomicInteger();
        try(var bridge=new VerifiedCheckpointBridge("session","run",1,model.modelRevision(),plan,()->{captures.incrementAndGet();return snapshot;},()->true,model)) {
            RuntimeEvent terminal;
            if(kind.equals("norm"))terminal=event(model.modelRevision(),RuntimeEventKind.NORM_UNFULFILLED,2,Map.of());
            else {
                bridge.observe(receiptEvent(request,model.modelRevision(),root,"WAITING",1,false));assertFalse(bridge.active(),"Initialization is not a terminal boundary");
                terminal=receiptEvent(request,model.modelRevision(),root,kind.equals("terminal")?"SATISFIED":"WAITING",2,kind.equals("administrative"));
            }
            bridge.observe(terminal);
            long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);while(captures.get()==0&&System.nanoTime()<end)Thread.sleep(5);
            assertEquals(1,captures.get());assertTrue(ExecutionBarrier.proof().clockFrozen());
            var field=VerifiedCheckpointBridge.class.getDeclaredField("coordinator");field.setAccessible(true);var coordinator=(VerifiedCheckpointCoordinator)field.get(bridge);
            var held=coordinator.view().request();assertEquals(terminal.eventId(),held.boundary().sourceOccurrence());
            assertEquals(kind.equals("terminal"),held.requiredChecks().stream().anyMatch(id->id.startsWith(VerifiedCheckpointProtocol.SETTLED_CHECK_PREFIX)));
            bridge.observe(terminal);assertEquals(held,coordinator.view().request(),"Duplicate source callback cannot create another cut or alter a frozen cut");
            System.out.println("ADMITTED_LIFECYCLE_SELECTION_AND_DUPLICATE_PASS "+kind);
        }finally{ExecutionBarrier.stop();Files.deleteIfExists(file);}
    }
    private static RuntimeEvent receiptEvent(ObservationCapabilityRequest request,String revision,String root,String state,long sequence,boolean administrative) {
        var data=Map.<String,Object>of("schemeIdentity","scheme-incarnation","historyQuality","COMPLETE","goals",List.of(Map.of("name",root,"state",state)),
                "administrativeEffect",administrative?Map.of("invocation","actual-receipt"):Map.of());
        var receipt=CapabilityObservation.of(request,new CapabilityObservation.Scope("run",1,revision,"cartago",sequence),Completeness.COMPLETE,"",data);
        return event(revision,administrative?RuntimeEventKind.SUCCEEDED:RuntimeEventKind.CHANGED,sequence,Map.of(CapabilityObservation.KEY,Map.of(request.id(),receipt.payload())));
    }
    private static RuntimeEvent event(String revision,RuntimeEventKind kind,long sequence,Map<String,Object> after) {
        return new RuntimeEvent("source:"+sequence,"run",1,revision,"cartago","cartago",sequence,Instant.EPOCH,kind,RuntimeFactKind.ARTIFACT,
                ProjectionStatus.EVIDENCE_ONLY,new BridgeEntityId("cartago","environment","artifact","main","scheme","incarnation"),null,"","",Map.of(),after,
                new SourceWatermark("cartago",sequence),Completeness.COMPLETE,List.of());
    }
}
