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
        System.out.println("ISOLATED_RELEASE_MAPPING_IMPORT_PASS");
    }
}
