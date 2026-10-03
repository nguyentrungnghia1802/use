package org.tzi.use.plugins.jacamo.codegrounded;

import java.nio.file.Path;
import org.tzi.use.runtime.MainPluginRuntime;
import org.tzi.use.runtime.impl.PluginRuntime;

/** Test-only child driver: USE distribution and installed release, without Maven/JaCaMo dependencies. */
public final class NativeRuntimeReplaySmokeMain {
    private NativeRuntimeReplaySmokeMain() { }

    public static void main(String[] args) throws Exception {
        Path expectedJar = Path.of(args[1]).toRealPath();
        MainPluginRuntime.run(Path.of(args[0]));
        var descriptor = ((PluginRuntime) PluginRuntime.getInstance()).getPlugin("JaCaMo");
        if (descriptor == null) throw new AssertionError("Installed native plugin was not discovered");
        var loader = descriptor.getPluginClassLoader();
        Class<?> replayClass = loader.loadClass("org.tzi.use.plugins.jacamo.codegrounded.runtime.NativeRuntimeReplay");
        Class<?> coordinatorClass = loader.loadClass("org.tzi.use.plugins.jacamo.codegrounded.runtime.RuntimeVerificationCoordinator");
        for (Class<?> type : new Class<?>[] { replayClass, coordinatorClass }) {
            Path loadedFrom = Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath();
            if (!expectedJar.equals(loadedFrom)) throw new AssertionError(type + " loaded from " + loadedFrom);
        }
        Object report = replayClass.getMethod("replay", Path.class)
                .invoke(replayClass.getDeclaredConstructor().newInstance(), Path.of(args[2]));
        Class<?> reportClass = report.getClass();
        if (!Boolean.TRUE.equals(reportClass.getMethod("complete").invoke(report)))
            throw new AssertionError("Replay failed closed: " + reportClass.getMethod("diagnostics").invoke(report));
        if (!args[3].equals(reportClass.getMethod("finalStateHash").invoke(report))
                || !args[4].equals(reportClass.getMethod("finalResultHash").invoke(report)))
            throw new AssertionError("Installed replay state/result hashes differ from live native checkpoints");
        System.out.println("ISOLATED_NATIVE_RUNTIME_REPLAY_PASS");
        var session=new org.tzi.use.main.Session();
        Class<?> controllerClass=loader.loadClass("org.tzi.use.plugins.jacamo.codegrounded.runtime.NativeReplayStepController");
        Object controller=controllerClass.getConstructor(org.tzi.use.main.Session.class).newInstance(session);
        try {
            controllerClass.getMethod("open",Path.class).invoke(controller,Path.of(args[2]));
            controllerClass.getMethod("reset").invoke(controller);
            controllerClass.getMethod("next").invoke(controller);controllerClass.getMethod("next").invoke(controller);
            var forward=session.system();controllerClass.getMethod("previous").invoke(controller);
            if(forward==session.system())throw new AssertionError("Previous did not reconstruct");
            controllerClass.getMethod("next").invoke(controller);
            while(true) {
                Object status=controllerClass.getMethod("status").invoke(controller);Class<?> type=status.getClass();
                if(type.getMethod("step").invoke(status).equals(type.getMethod("total").invoke(status)))break;
                controllerClass.getMethod("next").invoke(controller);
            }
            Object snapshot=controllerClass.getMethod("verificationSnapshot").invoke(controller);
            Object result=snapshot.getClass().getMethod("result").invoke(snapshot);
            if(!args[3].equals(result.getClass().getMethod("stateHash").invoke(result))
                    ||!args[4].equals(result.getClass().getMethod("resultHash").invoke(result)))throw new AssertionError("Installed Step parity mismatch");
            if(!session.system().isReadOnly())throw new AssertionError("Recorded system is not read-only");
            try{session.reset();throw new AssertionError("Manual reset was allowed");}catch(IllegalStateException expected){}
        } finally {controllerClass.getMethod("close").invoke(controller);}
        if(session.hasSystem())throw new AssertionError("Replay Session was not cleaned up");
        System.out.println("ISOLATED_NATIVE_STEP_REPLAY_PASS");
    }
}
