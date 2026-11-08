package org.tzi.use.plugins.jacamo;

import java.lang.instrument.Instrumentation;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import cartago.*;

/** Test-only live negative input. Calls public CArtAgO operations; never edits a
 * source field, model, assertion, native state, result or production policy. */
public final class AuctionEvidenceInputAgent {
    public static void premain(String evidence,Instrumentation ignored) {
        if(!System.getProperty("sun.java.command","").startsWith("jason.infra.local.LiveJaCaMoLauncherMain "))return;
        Input.run(evidence);
    }
    public static final class Input {
    public static void run(String evidence) {
        Thread.ofPlatform().daemon().name("auction-evidence-input-fixture").start(()->{
            Path root=Path.of(evidence);
            try {
                long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(180);
                while(!Files.isRegularFile(root.resolve("request-evidence-input"))&&System.nanoTime()<end)Thread.sleep(50);
                if(!Files.isRegularFile(root.resolve("request-evidence-input")))return;
                var environment=CartagoEnvironment.getInstance();var workspace=environment.resolveWSP("/main/aorg").getWorkspace();
                while(workspace.getArtifactDescriptor("a1")==null&&System.nanoTime()<end)Thread.sleep(50);
                long id=900000;
                // A second real stop violates the unchanged RunningForStop PRE.
                // The operation itself reports failure; the native engine owns
                // the contract verdict. No source field or assertion is edited.
                var performer=Arrays.stream(environment.getController("/main/aorg").getCurrentAgents()).filter(a->a.getAgentName().equals("bob")).findFirst().orElseThrow();
                for(int attempt=0;attempt<2;attempt++) {
                    String receipt=action(workspace,performer,"a1",new Op("stop"),++id);
                    Files.writeString(root.resolve("evidence-input-operations.txt"),"bob a1 stop() "+receipt+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);
                }
            }catch(Throwable failure){try{Files.writeString(root.resolve("evidence-input-error.txt"),failure.toString());}catch(Exception suppressed){failure.printStackTrace();}}
        });
    }
    static String action(Workspace workspace,AgentId performer,String artifact,Op operation,long id)throws Exception {
        var done=new CountDownLatch(1);var result=new AtomicReference<String>();
        workspace.execOp(id,performer,event->{result.set(event.getClass().getSimpleName()+":"+(event instanceof cartago.events.ActionFailedEvent failed?failed.getFailureMsg()+":"+failed.getFailureDescr():event));done.countDown();},artifact,operation,10000,null);
        if(!done.await(25,TimeUnit.SECONDS))throw new IllegalStateException("SOURCE_OPERATION_INPUT_TIMEOUT:"+operation);
        return result.get();
    }
    }
}
