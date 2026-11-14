package org.tzi.use.plugins.jacamo.bridge;

import java.nio.file.*;
import java.io.IOException;

/** Inputs for one owned source run. No Swing component or application singleton. */
public record StateRunConfiguration(Path project, Path mapping, Path csl, Path istar) {
    public StateRunConfiguration {
        project = project.toAbsolutePath().normalize();
        mapping = mapping.toAbsolutePath().normalize();
        csl = csl.toAbsolutePath().normalize();
        istar = istar.toAbsolutePath().normalize();
    }
    public void validate() throws IOException {
        for (Path file : new Path[]{project, mapping, csl, istar})
            if (!Files.isRegularFile(file) || !Files.isReadable(file)) throw new IOException("Cannot read " + file);
        if (!project.getFileName().toString().endsWith(".jcm")) throw new IOException("Select a JaCaMo .jcm project");
    }
}
