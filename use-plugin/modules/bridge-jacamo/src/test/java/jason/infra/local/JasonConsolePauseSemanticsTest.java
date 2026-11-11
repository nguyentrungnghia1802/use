package jason.infra.local;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import javax.swing.SwingUtilities;
import jason.architecture.AgArch;
import jason.control.ExecutionControl;
import jason.mas2j.ClassParameters;
import jason.runtime.MASConsoleGUI;
import jason.runtime.RuntimeServicesFactory;
import jason.runtime.Settings;
import org.jacamo.bridge.contract.CanonicalJson;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Executable API audit, not a production pause implementation or Swing click test. */
class JasonConsolePauseSemanticsTest {
    @TempDir Path temporary;

    @Test void consolePauseBlocksOutputButDoesNotStopSilentAgents() throws Exception { child("console"); }
    @Test void officialSyncSignalsGateAgentCyclesSeparatelyFromConsolePause() throws Exception { child("sync"); }

    private void child(String scenario) throws Exception {
        Path log=temporary.resolve(scenario+".log");
        var process=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java").toString(),
                "-Djava.awt.headless="+scenario.equals("sync"),"-cp",System.getProperty("java.class.path"),
                getClass().getName(),scenario,temporary.toString()).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try {
            assertTrue(process.waitFor(30,TimeUnit.SECONDS),"Official runtime audit timed out");
            assertEquals(0,process.exitValue(),Files.readString(log));
            assertTrue(Files.readString(log).contains("RUNTIME_PAUSE_API_AUDIT_PASS"));
            Path evidence=Path.of("target/runtime-control-api"); Files.createDirectories(evidence);
            Files.copy(temporary.resolve(scenario+".json"),evidence.resolve(scenario+".json"),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } finally { if(process.isAlive()) process.destroyForcibly(); }
    }

    public static void main(String[] args) {
        try { runAudit(args[0],Path.of(args[1])); System.out.println("RUNTIME_PAUSE_API_AUDIT_PASS"); System.exit(0); }
        catch(Throwable failure) { failure.printStackTrace(); System.exit(1); }
    }

    private static void runAudit(String scenario,Path root) throws Exception {
        var runner=new RunLocalMAS(); BaseLocalMAS.runner=runner;
        RuntimeServicesFactory.set(new LocalRuntimeServices(runner));
        var environment=new LocalEnvironment(new ClassParameters("jason.environment.Environment"),runner);
        var agents=new ArrayList<LocalAgArch>();
        LocalExecutionControl control=null; MASConsoleGUI console=null;
        try {
            if(scenario.equals("sync")) control=new LocalExecutionControl(new ClassParameters(AuditStepControl.class.getName()),runner);
            Path program=root.resolve("worker.asl"); Files.writeString(program,"!tick.\n+!tick <- .wait(5); !tick.\n");
            for(int i=0;i<2;i++) {
                var agent=new LocalAgArch(); agent.setAgName("worker"+i); agent.setMASRunner(runner);
                var settings=new Settings(); settings.setSync(scenario.equals("sync"));
                agent.createArchs(List.of(CycleProbe.class.getName()),"jason.asSemantics.Agent",
                        new ClassParameters("jason.bb.DefaultBeliefBase"),program.toString(),settings);
                agent.setEnvInfraTier(environment); if(control!=null) agent.setControlInfraTier(control);
                runner.addAg(agent); agents.add(agent);
                agent.setThread(new Thread(agent)); agent.startThread();
            }
            if(scenario.equals("console")) {
                await(()->cycles(agents).stream().allMatch(c->c>=3));
                SwingUtilities.invokeAndWait(MASConsoleGUI::get); console=MASConsoleGUI.get();
                console.setPause(true); assertTrue(console.isPause());
                List<Long> before=cycles(agents);
                await(()->{var now=cycles(agents); return now.get(0)>before.get(0)+10 && now.get(1)>before.get(1)+10;});
                List<Long> during=cycles(agents);
                var activeConsole=console;
                Thread output=new Thread(()->activeConsole.append("audit","output after pause\n"),"official-console-append-audit");
                output.setDaemon(true); output.start();
                await(()->output.getState()==Thread.State.WAITING);
                assertTrue(output.isAlive(),"Official append must wait on the console pause flag");
                console.setPause(false); output.join(3000); assertFalse(output.isAlive());
                Files.write(root.resolve("console.json"),CanonicalJson.encode(java.util.Map.of(
                        "consolePauseFlag",true,"cyclesBeforePause",before,"cyclesWhileConsolePaused",during,
                        "silentAgentsContinued",true,"consoleAppendBlocked",true,"consoleAppendReleased",true,
                        "pauseGuarantee","CONSOLE_OUTPUT_ONLY")));
            } else {
                var step=control;
                Thread.sleep(150); assertEquals(List.of(0L,0L),cycles(agents));
                step.informAllAgsToPerformCycle(1);
                await(()->cycles(agents).equals(List.of(1L,1L)));
                Thread.sleep(250); assertEquals(List.of(1L,1L),cycles(agents));
                step.informAllAgsToPerformCycle(2);
                await(()->cycles(agents).equals(List.of(2L,2L)));
                Thread.sleep(250); assertEquals(List.of(2L,2L),cycles(agents));
                Files.write(root.resolve("sync.json"),CanonicalJson.encode(java.util.Map.of(
                        "initialCycles",List.of(0,0),"afterFirstOfficialSignal",List.of(1,1),
                        "afterSecondOfficialSignal",List.of(2,2),"automaticAdvancement",false,
                        "scope","TWO_SYNC_LOCAL_AGENT_REASONING_CYCLES_ONLY",
                        "globalCartagoMoisePauseProved",false)));
            }
        } finally {
            if(console!=null) { console.setPause(false); var current=console; SwingUtilities.invokeAndWait(()->current.getFrame().dispose()); }
            for(var agent:agents) { agent.stopAg(); agent.getThread().join(2000); }
            if(control!=null) control.stop(); environment.stop();
        }
    }

    public static final class CycleProbe extends AgArch {
        private static final long serialVersionUID=1L;
        final AtomicLong cycles=new AtomicLong();
        @Override public void reasoningCycleFinished() { cycles.incrementAndGet(); super.reasoningCycleFinished(); }
    }
    public static final class AuditStepControl extends ExecutionControl {
        public AuditStepControl() { super(); }
        @Override public void init(String[] args) { setRunningCycle(false); }
    }
    private static List<Long> cycles(List<LocalAgArch> agents) {
        return agents.stream().map(a->((CycleProbe)a.getFirstAgArch()).cycles.get()).toList();
    }
    private static void await(java.util.function.BooleanSupplier ready) throws Exception {
        long deadline=System.nanoTime()+Duration.ofSeconds(10).toNanos();
        while(!ready.getAsBoolean() && System.nanoTime()<deadline) Thread.sleep(5);
        assertTrue(ready.getAsBoolean(),"Official API audit condition timed out");
    }
}
