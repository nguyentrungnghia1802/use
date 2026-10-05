package org.jacamo.bridge.adapter;

import static org.junit.jupiter.api.Assertions.*;
import static org.jacamo.bridge.contract.RuntimeControlContract.State.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import jason.architecture.AgArch;
import jason.infra.local.*;
import jason.mas2j.ClassParameters;
import jason.runtime.*;
import org.jacamo.bridge.contract.RuntimeControlContract;
import org.jacamo.bridge.contract.RuntimeControlContract.*;
import org.jacamo.bridge.contract.CanonicalJson;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BridgeExecutionControlTest {
    @TempDir Path root;
    @Test void allRealAgentsAckPauseResumeAndRepeatedRequestsAreIdempotent() throws Exception {child("all");}
    @Test void partialTimeoutArrivalDepartureAndReplacementNeverInventAck() throws Exception {child("membership");}
    @Test void inFlightRealCartagoOperationCompletesWhileJasonAgentsStayPaused() throws Exception {child("inflight");}
    @Test void inFlightOrgBoardActivityCompletesWhileJasonAgentsStayPaused() throws Exception {child("organisation");}
    private void child(String scenario) throws Exception {
        Path log=root.resolve(scenario+".log");
        var p=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java").toString(),"-Djava.awt.headless=true",
                "-cp",System.getProperty("java.class.path"),getClass().getName(),scenario,root.toString()).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try {assertTrue(p.waitFor(35,TimeUnit.SECONDS),"Control runtime timeout: "+Files.readString(log));
            assertEquals(0,p.exitValue(),Files.readString(log));
            Path evidence=Path.of("target/runtime-control-api");Files.createDirectories(evidence);
            Files.copy(root.resolve(scenario+".json"),evidence.resolve("execution-"+scenario+".json"),StandardCopyOption.REPLACE_EXISTING);
        } finally {if(p.isAlive())p.destroyForcibly();}
    }
    public static void main(String[] args) {
        try {run(args[0],Path.of(args[1]));System.out.println("EXECUTION_CONTROL_RUNTIME_PASS");System.exit(0);}
        catch(Throwable t){t.printStackTrace();System.exit(1);}
    }
    public static final class TestRunner extends RunLocalMAS {
        void install(BridgeLocalExecutionControl c){control=c;BridgeRuntimeRegistry.controller(c);}
        void current(){runner=this;}
        @Override public void addAg(LocalAgArch a){super.addAg(a);if(control instanceof BridgeLocalExecutionControl c)c.admit(a);}
    }
    public static final class Probe extends AgArch {
        private static final long serialVersionUID=1;
        final AtomicLong cycles=new AtomicLong();
        static final AtomicBoolean block=new AtomicBoolean();
        static final CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        @Override public void reasoningCycleFinished() {
            cycles.incrementAndGet();
            if(getAgName().equals("a") && block.compareAndSet(true,false)) {
                entered.countDown();try{release.await(10,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}
            }
            super.reasoningCycleFinished();
        }
    }
    public static final class InFlightArtifact extends cartago.Artifact {
        static final CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1),done=new CountDownLatch(1);
        @cartago.OPERATION void completeLater() {
            entered.countDown();try {release.await(10,TimeUnit.SECONDS);done.countDown();}
            catch(InterruptedException e){Thread.currentThread().interrupt();failed("interrupted");}
        }
    }
    /** A real GroupBoard/NPL operation, held only by this diagnostic test fixture. */
    public static final class InFlightGroupBoard extends ora4mas.nopl.GroupBoard {
        static final CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1),done=new CountDownLatch(1);
        static volatile InFlightGroupBoard completed;
        @cartago.OPERATION void completeRoleLater() {
            entered.countDown();
            try {release.await(10,TimeUnit.SECONDS);adoptRole("worker");completed=this;done.countDown();}
            catch(InterruptedException e){Thread.currentThread().interrupt();failed("interrupted");}
        }
    }
    private static LocalAgArch create(TestRunner r,LocalEnvironment env,Path program,String name) throws Exception {
        var a=new LocalAgArch();a.setAgName(name);a.setMASRunner(r);var settings=new Settings();settings.setSync(true);
        a.createArchs(List.of(Probe.class.getName(),BridgeAgArch.class.getName()),"jason.asSemantics.Agent",
                new ClassParameters("jason.bb.DefaultBeliefBase"),program.toString(),settings);
        a.setEnvInfraTier(env);r.addAg(a);a.setThread(new Thread(a));a.startThread();
        ((BridgeLocalExecutionControl)r.getControllerInfraTier()).started(a);return a;
    }
    private static long cycles(LocalAgArch a) {
        for(AgArch arch=a.getFirstAgArch();arch!=null;arch=arch.getNextAgArch())if(arch instanceof Probe p)return p.cycles.get();
        throw new AssertionError("Probe missing");
    }
    private static Request request(Action action,String id){return new Request(RuntimeControlContract.VERSION,"control-session",1,"control-revision",id,action,"test");}
    private static Status send(BridgeLocalExecutionControl c,Action action,String id){return c.request(request(action,id),"control-session",1,"control-revision");}
    private static void await(java.util.function.BooleanSupplier condition) throws Exception {
        long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(8);
        while(!condition.getAsBoolean() && System.nanoTime()<until)Thread.sleep(5);
        assertTrue(condition.getAsBoolean(),"Real control condition timed out");
    }
    private static void run(String scenario,Path root) throws Exception {
        System.setProperty("jacamo.bridge.session","control-session");System.setProperty("jacamo.bridge.generation","1");
        System.setProperty("jacamo.bridge.projectKey","control-project");System.setProperty("jacamo.bridge.control.ackTimeoutSeconds","1");
        var runner=new TestRunner();runner.current();RuntimeServicesFactory.set(new LocalRuntimeServices(runner));
        var env=new LocalEnvironment(new ClassParameters("jason.environment.Environment"),runner);
        var control=new BridgeLocalExecutionControl(runner);runner.install(control);
        var agents=new ArrayList<LocalAgArch>();
        try(var observer=BridgeRuntimeRegistry.attach(event->{})) {
            Path program=root.resolve("worker.asl");Files.writeString(program,"!tick.\n+!tick <- .wait(5); !tick.\n");
            var a=create(runner,env,program,"a");var b=create(runner,env,program,"b");agents.add(a);agents.add(b);
            await(()->cycles(a)>4 && cycles(b)>4);assertTrue(control.capable());
            if(scenario.equals("membership")) {
                Probe.block.set(true);assertTrue(Probe.entered.await(3,TimeUnit.SECONDS));
                var first=send(control,Action.PAUSE,"pause-partial");assertEquals(PAUSE_REQUESTED,first.state());
                await(()->send(control,Action.STATUS,"s").acknowledgedAgents().size()==1);
                Thread.sleep(1100);var timeout=send(control,Action.STATUS,"s");assertEquals(PAUSE_REQUESTED,timeout.state());assertTrue(timeout.diagnostic().startsWith("CONTROL_ACK_TIMEOUT"));
                var newcomer=create(runner,env,program,"newcomer");agents.add(newcomer);Thread.sleep(100);
                assertEquals(0,cycles(newcomer));assertEquals(3,send(control,Action.STATUS,"s").requiredAgents().size());
                String old=BridgeRuntimeRegistry.agentIdentity("a").orElseThrow().canonical();runner.delAg("a");control.departed(old);a.stopAg();
                assertEquals(PAUSE_REQUESTED,send(control,Action.STATUS,"s").state());
                var replacement=create(runner,env,program,"a");agents.add(replacement);
                assertNotEquals(old,BridgeRuntimeRegistry.agentIdentity("a").orElseThrow().canonical());assertEquals(0,cycles(replacement));
                var scoped=send(control,Action.PAUSE,"explicit-new-cohort");assertEquals(PAUSED,scoped.state());
                assertFalse(scoped.requiredAgents().contains(old));assertEquals(scoped.requiredAgents(),scoped.acknowledgedAgents());
                Files.write(root.resolve(scenario+".json"),CanonicalJson.encode(Map.of("partialTimeoutDidNotPause",true,"newAgentHeldBeforeFirstCycle",true,
                        "departedAgentDidNotAck",true,"replacementIdentityIsDistinct",true,"explicitNewCohortAllAck",true)));
            } else {
                cartago.CartagoEnvironment cartagoEnvironment=null;
                if(scenario.equals("inflight")) {
                    cartagoEnvironment=cartago.CartagoEnvironment.getInstance();cartagoEnvironment.init();
                    var context=new cartago.util.agent.CartagoBasicContext("inflightObserver");
                    var artifact=context.makeArtifact(context.getJoinedWspId("main"),"inflight",InFlightArtifact.class.getName());
                    context.doAction(artifact,new cartago.Op("completeLater"));assertTrue(InFlightArtifact.entered.await(3,TimeUnit.SECONDS));
                } else if(scenario.equals("organisation")) {
                    cartagoEnvironment=cartago.CartagoEnvironment.getInstance();cartagoEnvironment.init();
                    var specification=new moise.os.OSBuilder();specification.addRootGroup("team");specification.addRole("team","worker");
                    Path file=root.resolve("control-organisation.xml");specification.save(file.toString());
                    var context=new cartago.util.agent.CartagoBasicContext("organisationObserver");
                    var board=context.makeArtifact(context.getJoinedWspId("main"),"organisation",InFlightGroupBoard.class.getName(),new Object[]{file.toString(),"team"});
                    context.doAction(board,new cartago.Op("completeRoleLater"));assertTrue(InFlightGroupBoard.entered.await(3,TimeUnit.SECONDS));
                }
                send(control,Action.PAUSE,"pause-all");await(()->send(control,Action.STATUS,"s").state()==PAUSED);
                var paused=send(control,Action.STATUS,"s");assertEquals(2,paused.requiredAgents().size());assertEquals(paused.requiredAgents(),paused.acknowledgedAgents());
                var before=List.of(cycles(a),cycles(b));Thread.sleep(250);assertEquals(before,List.of(cycles(a),cycles(b)));
                for(int i=0;i<100;i++)assertEquals("pause-all",send(control,Action.PAUSE,"storm-"+i).requestId());
                if(scenario.equals("inflight")) {
                    InFlightArtifact.release.countDown();assertTrue(InFlightArtifact.done.await(3,TimeUnit.SECONDS));
                    assertEquals(PAUSED,send(control,Action.STATUS,"s").state());assertEquals(before,List.of(cycles(a),cycles(b)));
                } else if(scenario.equals("organisation")) {
                    InFlightGroupBoard.release.countDown();assertTrue(InFlightGroupBoard.done.await(3,TimeUnit.SECONDS));
                    assertNotNull(InFlightGroupBoard.completed);
                    assertEquals(1,InFlightGroupBoard.completed.getGrpState().getPlayers().size(),"Official GroupBoard operation must have changed its actual Moise state");
                    assertEquals(PAUSED,send(control,Action.STATUS,"s").state());assertEquals(before,List.of(cycles(a),cycles(b)));
                }
                send(control,Action.RESUME,"resume-all");await(()->send(control,Action.STATUS,"s").state()==RUNNING);
                await(()->cycles(a)>before.get(0) && cycles(b)>before.get(1));
                assertEquals("resume-all",send(control,Action.RESUME,"duplicate-resume").requestId());
                Files.write(root.resolve(scenario+".json"),CanonicalJson.encode(Map.of("pauseAck",paused.payload(),"cyclesWhilePaused",before,
                        "resumed",true,"repeatedPauseDeduplicated",true,"cartagoCompletedWhilePaused",scenario.equals("inflight"),
                        "orgBoardCompletedWhilePaused",scenario.equals("organisation"),
                        "wholePlatformAtomicPause",false,"recordedAt",Instant.now().toString())));
            }
        } finally {Probe.release.countDown();InFlightArtifact.release.countDown();InFlightGroupBoard.release.countDown();control.stop();
            for(var a:agents){a.stopAg();if(a.getThread()!=null)a.getThread().join(1000);}env.stop();BridgeRuntimeRegistry.clearAgents();}
    }
}
