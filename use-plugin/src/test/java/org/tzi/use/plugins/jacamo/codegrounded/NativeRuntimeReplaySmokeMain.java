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
    }
}
