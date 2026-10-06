package org.tzi.use.plugins.jacamo.release;

import java.nio.file.Path;
import org.tzi.use.runtime.MainPluginRuntime;
import org.tzi.use.runtime.impl.PluginRuntime;

/** Child process launched with only the USE distribution, test driver, and installed ZIP contents. */
public final class ReleaseIsolatedSmokeMain {
    private ReleaseIsolatedSmokeMain() { }

    public static void main(String[] args) throws Exception {
        Path install = Path.of(args[0]);
        Path expectedJar = Path.of(args[1]).toRealPath();
        MainPluginRuntime.run(install.resolve("lib/plugins"));
        var descriptor = ((PluginRuntime) PluginRuntime.getInstance()).getPlugin("JaCaMo");
        if (descriptor == null) throw new AssertionError("USE did not discover the installed plugin");
        Class<?> loaderClass = descriptor.getPluginClassLoader()
                .loadClass("org.tzi.use.plugins.jacamo.mapping.MappingLoader");
        Path loadedFrom = Path.of(loaderClass.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath();
        if (!expectedJar.equals(loadedFrom)) {
            throw new AssertionError("MappingLoader came from " + loadedFrom + " rather than " + expectedJar);
        }
        Object loader = loaderClass.getDeclaredConstructor().newInstance();
        loaderClass.getMethod("loadCanonical", Path.class).invoke(loader, install);
        // Frozen canonical import remains reproducible, but the current runtime
        // must load its native authority rather than the removed mapping engine.
        var pluginLoader=descriptor.getPluginClassLoader();
        Class<?> json=pluginLoader.loadClass("org.jacamo.bridge.contract.CanonicalJson");
        Object tree=json.getMethod("decode",byte[].class).invoke(null,(Object)java.nio.file.Files.readAllBytes(Path.of(args[2])));
        Class<?> payloads=pluginLoader.loadClass("org.jacamo.bridge.contract.ContractPayloads");
        Object model=payloads.getMethod("model",java.util.Map.class).invoke(null,tree);
        Class<?> pipeline=pluginLoader.loadClass("org.tzi.use.plugins.jacamo.codegrounded.CodeGroundedNativePipeline");
        if(!expectedJar.equals(Path.of(pipeline.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath()))
            throw new AssertionError("Native authority did not come from the installed JAR");
        Object result=pipeline.getMethod("build",model.getClass()).invoke(pipeline.getDeclaredConstructor().newInstance(),model);
        Object state=result.getClass().getMethod("state").invoke(result);
        var system=(org.tzi.use.uml.sys.MSystem)state.getClass().getMethod("system").invoke(state);
        if(system.model().getClass("Agent")==null || system.state().allObjects().isEmpty())
            throw new AssertionError("Installed native pipeline failed to build the official model");
        Class<?> snapshots=pluginLoader.loadClass("org.tzi.use.plugins.jacamo.codegrounded.runtime.VerificationSnapshot");
        if(!"1.2.0".equals(snapshots.getField("CONTRACT_VERSION").get(null)))
            throw new AssertionError("Installed snapshot contract is stale");
        pluginLoader.loadClass("org.tzi.use.plugins.jacamo.codegrounded.runtime.RuntimeControlService");
        System.out.println("ISOLATED_RELEASE_NATIVE_IMPORT_PASS objects="+system.state().allObjects().size());
    }
}
