package org.jacamo.bridge.adapter;

import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import jason.infra.local.*;
import jason.mas2j.ClassParameters;
import jason.runtime.*;
import org.jacamo.bridge.contract.*;
import org.jacamo.checkpoint.runtime.ExecutionBarrier;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VerifiedCheckpointTaskYieldTest {
    public static class Runner extends RunLocalMAS { void current(){runner=this;} }
    @Test void actualIntermediateTaskYieldsBeforeTheNextDeedAndResumesOnlyAfterRelease() throws Exception {
        var child=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java").toString(),
                "-Djava.awt.headless=true","-cp",System.getProperty("java.class.path"),getClass().getName()).redirectErrorStream(true).start();
        if(!child.waitFor(25,TimeUnit.SECONDS)){child.destroyForcibly();child.waitFor();fail(new String(child.getInputStream().readAllBytes()));}
        assertEquals(0,child.exitValue(),new String(child.getInputStream().readAllBytes()));
    }
    public static void main(String[] args) throws Exception {
        try{run();System.exit(0);}catch(Throwable failed){failed.printStackTrace();System.exit(1);}
    }
    private static void run() throws Exception {
        jason.util.Config.get().setProperty(jason.util.Config.START_WEB_MI,"false");
        Path root=Files.createTempDirectory("source-task-yield-");
        String text="!deliver. !other.\n+!deliver <- +ready; +sent.\n+!other <- "+String.join("; ",Collections.nCopies(50,"+aux"))+".\n";
        Path file=Files.writeString(root.resolve("worker.asl"),text);
        var source=new OfficialJasonAdapter().adapt(root,file,"dispatch","worker").program();
        var plan=source.planLibrary().plans().stream().filter(p->p.trigger().literal().contains("deliver")).findFirst().orElseThrow();
        var specification=new JasonPlanInstrumentation.Specification(JasonPlanInstrumentation.RECIPE,source.sourceDigest(),
                List.of(new JasonPlanInstrumentation.PlanSelection(plan.metadata().semanticId(),List.of(
                        new JasonPlanInstrumentation.Boundary("Ready",0,1),new JasonPlanInstrumentation.Boundary("Send",1,2)))));
        var runner=new Runner();runner.current();RuntimeServicesFactory.set(new LocalRuntimeServices(runner));
        var env=new LocalEnvironment(new ClassParameters("jason.environment.Environment"),runner);
        var agent=new LocalAgArch();agent.setAgName("worker");agent.setMASRunner(runner);
        agent.createArchs(List.of(),"jason.asSemantics.Agent",new ClassParameters("jason.bb.DefaultBeliefBase"),file.toString(),new Settings());
        agent.setEnvInfraTier(env);agent.setCyclesAct(20);runner.addAg(agent);
        var environment=cartago.CartagoEnvironment.getInstance();environment.init();ExecutionBarrier.enable("yield-probe");
        BridgeRuntimeRegistry.checkpointTaskSources(Set.of(source.sourceDigest()+"/Ready",source.sourceDigest()+"/Send"));
        var epoch=new AtomicLong();var runningAtBoundary=new AtomicInteger();var callbacks=new CopyOnWriteArrayList<VerificationObservation>();var callbackReturned=new CountDownLatch(1);
        var captures=List.of(new JasonStepObservation.Capture("Ready",List.of(),"","","",false,List.of()),
                new JasonStepObservation.Capture("Send",List.of(),"","","",false,List.of()));
        try(var observer=new JasonStepObservation(agent.getTS(),new BridgeEntityId("jason","agent","runtime-agent","dispatch","worker","incarnation"),
                "run",1,"revision",source,root,observation->{
                    callbacks.add(observation);
                    if("Ready".equals(observation.data().get("sourceStepId"))&&"SUCCEEDED".equals(observation.data().get("phase"))) {
                        // Jason removes the selected intention from the runnable
                        // queue for act(); it still participates in this cycle.
                        runningAtBoundary.set(agent.getTS().getC().getNbRunningIntentions()+1);
                        epoch.set(ExecutionBarrier.closeAdmission());callbackReturned.countDown();
                    }
                },reason->{throw new AssertionError(reason);},specification,captures)) {
            agent.setThread(new Thread(agent,"actual-source-task-agent"));agent.startThread();
            assertTrue(callbackReturned.await(5,TimeUnit.SECONDS),"Actual exit marker returned without waiting");
            var proof=ExecutionBarrier.awaitFrozen(epoch.get(),Duration.ofSeconds(5));
            assertTrue(proof.active().isEmpty());assertEquals(0,proof.pending());
            assertTrue(proof.completed().containsKey("jason.task-boundary-yield"));
            assertTrue(runningAtBoundary.get()>1,"Multiple runnable intentions exercise the multi-act path");
            assertEquals(List.of("ENABLED","SUCCEEDED"),callbacks.stream().map(o->o.data().get("phase")).toList());
            assertNull(agent.getTS().getAg().getBB().contains(jason.asSyntax.ASSyntax.createLiteral("sent")));
            Thread.sleep(150);assertEquals(2,callbacks.size(),"Next Task entry must not execute while held");
            ExecutionBarrier.release(epoch.get());
            long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
            while(callbacks.size()<4&&System.nanoTime()<end)Thread.sleep(5);
            assertEquals(List.of("ENABLED","SUCCEEDED","ENABLED","SUCCEEDED"),callbacks.stream().map(o->o.data().get("phase")).toList());
            assertNotNull(agent.getTS().getAg().getBB().contains(jason.asSyntax.ASSyntax.createLiteral("sent")));
            assertEquals(text,Files.readString(file));
            System.out.println("ACTUAL_INTERMEDIATE_TASK_YIELD_AND_EXACT_RELEASE_PASS cyclesAct=20 proof="+proof);
        } finally { ExecutionBarrier.stop();agent.stopAg();agent.getThread().join(1000);env.stop();environment.shutdown(); }
        System.exit(0);
    }
}
