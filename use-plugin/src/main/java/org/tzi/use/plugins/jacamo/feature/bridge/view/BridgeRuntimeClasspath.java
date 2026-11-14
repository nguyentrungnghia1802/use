package org.tzi.use.plugins.jacamo.feature.bridge.view;

import java.io.File;
import java.nio.file.*;
import java.util.*;

/** Resolves the packaged producer libraries, independently of the USE JVM classpath. */
public final class BridgeRuntimeClasspath {
    private BridgeRuntimeClasspath() { }
    public static String resolve() throws Exception {
        String override=System.getProperty("use.state.runtime.classpath","");
        if(!override.isBlank())return Files.readString(Path.of(override)).trim();
        Path location=Path.of(BridgeRuntimeClasspath.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        var candidates=new ArrayList<Path>();
        if(Files.isRegularFile(location)) {
            Path packaged=location.getParent().getParent().getParent().resolve("bridge/state");
            if(Files.isDirectory(packaged))return jars(packaged);
        }
        Path current=Path.of("").toAbsolutePath();
        for(Path root=current;root!=null;root=root.getParent()) {
            Path module=root.resolve("use-plugin");
            Path file=module.resolve("target/runtime-classpath.txt");
            if(Files.isRegularFile(file))return module.resolve("target/classes")+File.pathSeparator+Files.readString(file).trim();
            candidates.add(root.resolve("bridge/state"));
        }
        for(Path directory:candidates)if(Files.isDirectory(directory)) {
            return jars(directory);
        }
        throw new IllegalStateException("JaCaMo runtime libraries are missing. Build the USE distribution or set use.state.runtime.classpath to a runtime-classpath.txt file.");
    }
    private static String jars(Path directory)throws Exception {
        try(var files=Files.list(directory)) {
            var paths=files.filter(p->p.toString().endsWith(".jar")).sorted().map(p->p.toAbsolutePath().toString()).toList();
            if(paths.isEmpty())throw new IllegalStateException("No JaCaMo runtime JARs in "+directory);
            return String.join(File.pathSeparator,paths);
        }
    }
}
