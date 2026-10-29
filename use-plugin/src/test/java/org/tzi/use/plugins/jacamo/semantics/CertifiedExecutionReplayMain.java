package org.tzi.use.plugins.jacamo.semantics;

import java.nio.file.*;
import org.tzi.use.runtime.MainPluginRuntime;
import org.tzi.use.runtime.impl.PluginRuntime;

/** Isolated, batch-only archive regression driver. No plugin/engine dependency
 * on the application classpath, and no repeated step-controller reconstruction. */
public final class CertifiedExecutionReplayMain {
    public static void main(String[] args)throws Exception {
        var install=Path.of(args[0]).toRealPath();
        MainPluginRuntime.run(install.resolve("lib/plugins"));
        var descriptor=((PluginRuntime)PluginRuntime.getInstance()).getPlugin("JaCaMo");
        if(descriptor==null)throw new AssertionError("Installed plugin missing");
        var loader=descriptor.getPluginClassLoader();
        var expected=install.resolve("lib/plugins/use-jacamo-plugin-1.0.1.jar").toRealPath();
        for(String name:new String[]{"org.tzi.use.plugins.jacamo.codegrounded.runtime.NativeRuntimeReplay",
                "org.tzi.use.plugins.jacamo.semantics.runtime.ExecutionEvidence",
                "org.vnu.sme.goal.verify.conformance.semantics.TaskMarking"}) {
            var type=loader.loadClass(name);
            if(!expected.equals(Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath()))throw new AssertionError("Wrong installed class: "+name);
        }
        var type=loader.loadClass("org.tzi.use.plugins.jacamo.codegrounded.runtime.NativeRuntimeReplay");
        var report=type.getMethod("replay",Path.class).invoke(type.getConstructor().newInstance(),Path.of(args[1]));
        if(!Boolean.TRUE.equals(report.getClass().getMethod("complete").invoke(report)))throw new AssertionError(report.toString());
        System.out.println("INSTALLED_CERTIFIED_EXECUTION_REPLAY_PASS "+report);
    }
}
