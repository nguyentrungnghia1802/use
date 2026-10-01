package org.tzi.use.plugins.jacamo;

import java.nio.file.Path;

/** Uses the configured JDK on both Windows development hosts and Unix CI runners. */
public final class JavaProcessSupport {
    private JavaProcessSupport() { }

    public static String executable() {
        String name = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
        return Path.of(System.getProperty("java.home"), "bin", name).toString();
    }
}
