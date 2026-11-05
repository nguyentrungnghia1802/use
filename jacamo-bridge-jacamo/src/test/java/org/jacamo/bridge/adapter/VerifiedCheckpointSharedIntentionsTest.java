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

/** Real interpreter and clock; no USE verdict or application source rewriting. */
class VerifiedCheckpointSharedIntentionsTest {
    public static class Runner extends RunLocalMAS {void current(){runner=this;}}
    @Test void sharedAgentDrainsOtherAdmittedTaskWithoutStartingTheNextTask() throws Exception { child("parallel"); }
    @Test void synchronousCycleControllerAlsoDrainsTheOtherAdmittedIntention()throws Exception {child("synchronous");}
    @Test void parkedCycleGrantsCannotStarveAnotherAgentsCausalNotification()throws Exception {child("synchronous-many");}
    @Test void waitingTaskYieldsAndItsLogicalTimerStaysFrozen() throws Exception { child("wait"); }
    @Test void nestedTaskParksItsAdmittedParentAtTheChildBoundary() throws Exception { child("nested"); }
    @Test void atomicIntentionCannotBypassTheSelectedTaskBoundary() throws Exception { child("atomic"); }
    @Test void bootstrapWakeDoesNotInventWorkForAnAgentThreadThatHasNotStarted()throws Exception {child("bootstrap");}
    private void child(String mode) throws Exception {
        var process=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java").toString(),"-Djava.awt.headless=true","-cp",System.getProperty("java.class.path"),getClass().getName(),mode).redirectErrorStream(true).start();
        if(!process.waitFor(25,TimeUnit.SECONDS)){process.destroyForcibly();process.waitFor();fail(new String(process.getInputStream().readAllBytes()));}
        assertEquals(0,process.exitValue(),new String(process.getInputStream().readAllBytes()));
    }
    public static void main(String[] args){try{run(args[0]);System.exit(0);}catch(Throwable failure){failure.printStackTrace();System.exit(1);}}
    static void run(String mode) throws Exception {
        if(mode.equals("bootstrap")) {
            var dormant=new LocalAgArch();dormant.setThread(new Thread(dormant,"not-started"));ExecutionBarrier.enable("bootstrap-probe");
            try {dormant.wake();var proof=ExecutionBarrier.awaitFrozen(ExecutionBarrier.closeAdmission(),Duration.ofSeconds(1));assertEquals(0,proof.pending());assertTrue(proof.active().isEmpty());System.out.println("BOOTSTRAP_WAKE_WITHOUT_PHANTOM_WORK_PASS "+proof);}
            finally{ExecutionBarrier.stop();}
            return;
        }
        jason.util.Config.get().setProperty(jason.util.Config.START_WEB_MI,"false");
        String text=switch(mode){
            case "parallel","synchronous","synchronous-many"->"!one. !two.\n+!one <- +one_created; +one_ready; +one_after.\n+!two <- +two_created; +two_focused; +two_ready; +two_after.\n";
            case "wait"->"!two. !one.\n+!one <- +one_a; +one_b; +one_c; +one_ready; +one_after.\n+!two <- .wait(1000); +timer_done; +two_after.\n";
            case "nested"->"!outer.\n+!outer <- !one; +outer_after.\n+!one <- +one_ready; +one_after.\n";
            case "atomic"->"!one.\n@serial[atomic] +!one <- +one_ready; +one_after.\n";
            default->throw new IllegalArgumentException(mode);
        };
        Path directory=Files.createTempDirectory("shared-intentions-"),file=Files.writeString(directory.resolve("worker.asl"),text);
        var source=new OfficialJasonAdapter().adapt(directory,file,"fixture","worker").program();
        var selections=new ArrayList<JasonPlanInstrumentation.PlanSelection>();var captures=new ArrayList<JasonStepObservation.Capture>();var admitted=new HashSet<String>();
        for(var plan:source.planLibrary().plans()) {
            String name=plan.trigger().literal().startsWith("outer")?"Outer":plan.trigger().literal().startsWith("two")?"Two":"One";
            int size=plan.body().size(),end=name.equals("Outer")?size:size-1;
            var boundaries=new ArrayList<JasonPlanInstrumentation.Boundary>();boundaries.add(new JasonPlanInstrumentation.Boundary("Prepare"+name,0,end));
            if(end<size)boundaries.add(new JasonPlanInstrumentation.Boundary("After"+name,end,size));
            selections.add(new JasonPlanInstrumentation.PlanSelection(plan.metadata().semanticId(),boundaries));
            for(var boundary:boundaries){captures.add(new JasonStepObservation.Capture(boundary.task(),List.of(),"","","",false,List.of()));admitted.add(source.sourceDigest()+"/"+boundary.task());}
        }
        var runner=new Runner();runner.current();RuntimeServicesFactory.set(new LocalRuntimeServices(runner));
        var env=new LocalEnvironment(new ClassParameters("jason.environment.Environment"),runner);var agent=new LocalAgArch();agent.setAgName("worker");agent.setMASRunner(runner);
        agent.createArchs(List.of(),"jason.asSemantics.Agent",new ClassParameters("jason.bb.DefaultBeliefBase"),file.toString(),new Settings());agent.setEnvInfraTier(env);agent.setCyclesAct(2);runner.addAg(agent);
        var environment=cartago.CartagoEnvironment.getInstance();environment.init();ExecutionBarrier.enable("shared-intention-probe");BridgeRuntimeRegistry.checkpointTaskSources(admitted);
        BridgeLocalExecutionControl control=null;var idleAgents=new ArrayList<LocalAgArch>();
        if(mode.startsWith("synchronous")) {
            BridgeRuntimeRegistry.registerAgent("worker",new BridgeEntityId("jason","agent","runtime-agent","fixture","worker","incarnation"));BridgeRuntimeRegistry.registerArchitecture("worker",agent);
            control=new BridgeLocalExecutionControl(runner);control.admit(agent);
            if(mode.equals("synchronous-many")) {
                var idleSource=Files.writeString(directory.resolve("idle.asl"),"idle.\n");
                for(int index=0;index<6;index++) {
                    var idle=new LocalAgArch();idle.setAgName("idle"+index);idle.setMASRunner(runner);idle.createArchs(List.of(),"jason.asSemantics.Agent",new ClassParameters("jason.bb.DefaultBeliefBase"),idleSource.toString(),new Settings());idle.setEnvInfraTier(env);runner.addAg(idle);
                    BridgeRuntimeRegistry.registerAgent(idle.getAgName(),new BridgeEntityId("jason","agent","runtime-agent","fixture",idle.getAgName(),"incarnation"));BridgeRuntimeRegistry.registerArchitecture(idle.getAgName(),idle);control.admit(idle);idleAgents.add(idle);
                }
            }
        }
        var frames=new CopyOnWriteArrayList<VerificationObservation>();var closed=new CountDownLatch(1);var epoch=new AtomicLong();
        Thread delayed=null;
        if(!idleAgents.isEmpty()) {
            var ready=new CountDownLatch(1);
            delayed=Thread.ofPlatform().daemon().start(()->{try(var permit=ExecutionBarrier.enter("fixture-delayed-notification")){ready.countDown();if(!closed.await(5,TimeUnit.SECONDS))throw new AssertionError("No source boundary");Thread.sleep(250);idleAgents.getLast().wake();}catch(InterruptedException failure){Thread.currentThread().interrupt();}});
            assertTrue(ready.await(3,TimeUnit.SECONDS));
        }
        try(var observer=new JasonStepObservation(agent.getTS(),new BridgeEntityId("jason","agent","runtime-agent","fixture","worker","incarnation"),"run",1,"revision",source,directory,
                frame->{frames.add(frame);if("PrepareOne".equals(frame.data().get("sourceStepId"))&&"SUCCEEDED".equals(frame.data().get("phase"))){epoch.set(ExecutionBarrier.closeAdmission());closed.countDown();}},reason->{throw new AssertionError(reason);},
                new JasonPlanInstrumentation.Specification(JasonPlanInstrumentation.RECIPE,source.sourceDigest(),selections),captures)) {
            for(var idle:idleAgents){idle.setThread(new Thread(idle,idle.getAgName()));idle.startThread();control.started(idle);}
            agent.setThread(new Thread(agent,"shared-intentions"));agent.startThread();if(control!=null)control.started(agent);assertTrue(closed.await(5,TimeUnit.SECONDS));
            var proof=ExecutionBarrier.awaitFrozen(epoch.get(),Duration.ofSeconds(5));assertTrue(proof.active().isEmpty());assertEquals(0,proof.pending());
            assertTrue(frames.stream().noneMatch(f->f.data().get("sourceStepId").toString().startsWith("After")),"No next Task may start while admission is held");
            if(Set.of("parallel","synchronous","synchronous-many").contains(mode))assertTrue(frames.stream().anyMatch(f->"PrepareTwo".equals(f.data().get("sourceStepId"))&&"SUCCEEDED".equals(f.data().get("phase"))),"The other admitted Task on the SAME agent must finish before READY");
            if(mode.equals("nested"))assertTrue(ExecutionBarrier.taskContinuations().containsValue("DEFERRED_AT_SELECTED_TASK"),"The parent yields without completing past its selected child");
            long now=ExecutionBarrier.nowMillis();Thread.sleep(mode.equals("wait")?1200:100);assertEquals(now,ExecutionBarrier.nowMillis());
            if(mode.equals("wait")){assertTrue(ExecutionBarrier.taskContinuations().containsValue("PARKED_SOURCE_WAIT"));assertNull(agent.getTS().getAg().getBB().contains(jason.asSyntax.ASSyntax.createLiteral("timer_done")));}
            ExecutionBarrier.release(epoch.get());long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
            String last=mode.equals("nested")?"outer_after":Set.of("wait","parallel","synchronous","synchronous-many").contains(mode)?"two_after":"one_after";
            while(agent.getTS().getAg().getBB().contains(jason.asSyntax.ASSyntax.createLiteral(last))==null&&System.nanoTime()<end)Thread.sleep(5);
            assertNotNull(agent.getTS().getAg().getBB().contains(jason.asSyntax.ASSyntax.createLiteral(last)));assertEquals(text,Files.readString(file));
            System.out.println("SHARED_INTENTION_SOURCE_BOUNDARY_PASS mode="+mode+" proof="+proof);
        }finally{ExecutionBarrier.stop();if(control!=null)control.stop();agent.stopAg();agent.getThread().join(1000);for(var idle:idleAgents){idle.stopAg();idle.getThread().join(1000);}if(delayed!=null)delayed.join(1000);env.stop();environment.shutdown();BridgeRuntimeRegistry.checkpointTaskSources(Set.of());}
    }
}
