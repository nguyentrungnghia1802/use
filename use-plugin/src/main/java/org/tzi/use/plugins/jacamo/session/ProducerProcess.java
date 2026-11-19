package org.tzi.use.plugins.jacamo.session;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import javax.tools.ToolProvider;

/** Prepares, launches and stops the isolated JaCaMo JVM for a verification session. */
final class ProducerProcess {
    private ProducerProcess() { }

    static String resolveClasspath() throws Exception {
        String override = System.getProperty("use.state.runtime.classpath", "");
        if (!override.isBlank()) return Files.readString(Path.of(override)).trim();

        Path location = Path.of(ProducerProcess.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        if (Files.isRegularFile(location)) {
            Path packaged = location.getParent().getParent().getParent().resolve("bridge/state");
            if (Files.isDirectory(packaged)) return jarClasspath(packaged);
        }
        var candidates = new ArrayList<Path>();
        for (Path root = Path.of("").toAbsolutePath(); root != null; root = root.getParent()) {
            Path module = root.resolve("use-plugin");
            Path classpath = module.resolve("target/runtime-classpath.txt");
            if (Files.isRegularFile(classpath)) {
                return module.resolve("target/classes") + File.pathSeparator + Files.readString(classpath).trim();
            }
            candidates.add(root.resolve("bridge/state"));
        }
        for (Path directory : candidates) {
            if (Files.isDirectory(directory)) return jarClasspath(directory);
        }
        throw new IllegalStateException("JaCaMo runtime libraries are missing. Build the USE distribution or set use.state.runtime.classpath to a runtime-classpath.txt file.");
    }

    private static String jarClasspath(Path directory) throws IOException {
        try (var files = Files.list(directory)) {
            var paths = files.filter(p -> p.toString().endsWith(".jar")).sorted()
                    .map(p -> p.toAbsolutePath().toString()).toList();
            if (paths.isEmpty()) throw new IllegalStateException("No JaCaMo runtime JARs in " + directory);
            return String.join(File.pathSeparator, paths);
        }
    }

    static void compileArtifacts(Path project, Path work, String classpath, Consumer<String> log) throws IOException {
        Path source = project.getParent().resolve("src/env");
        Path output = work.resolve("artifact-classes");
        Files.createDirectories(output);
        if (!Files.isDirectory(source)) return;

        List<String> sources;
        try (var files = Files.walk(source)) {
            sources = files.filter(p -> p.toString().endsWith(".java")).map(Path::toString).toList();
        }
        if (sources.isEmpty()) return;
        var compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) throw new IllegalStateException("JDK_21_REQUIRED");
        var args = new ArrayList<>(List.of("-proc:none", "-cp", classpath, "-d", output.toString()));
        args.addAll(sources);
        var diagnostics = new ByteArrayOutputStream();
        int code = compiler.run(null, diagnostics, diagnostics, args.toArray(String[]::new));
        String text = diagnostics.toString(StandardCharsets.UTF_8);
        if (!text.isBlank()) log.accept(text);
        if (code != 0) throw new IllegalArgumentException("PROJECT_ARTIFACT_COMPILE_FAILED\n" + text);
    }

    static Process start(VerificationSession.Configuration configuration, Path work, String classpath,
                         int port, Path secretFile, String modelRevision) throws IOException {
        var command = List.of(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-Djava.awt.headless=true", "-cp", classpath + File.pathSeparator + work.resolve("artifact-classes"),
                // The child JVM has the JaCaMo classes on its own classpath.
                "org.tzi.use.plugins.jacamo.runtime.StateProducer", configuration.project().toString(), configuration.mapping().toString(),
                Integer.toString(port), secretFile.toString(), work.toString(), modelRevision);
        return new ProcessBuilder(command).directory(configuration.project().getParent().toFile())
                .redirectErrorStream(true).start();
    }

    static void stop(Process running) {
        if (running == null || !running.isAlive()) return;
        running.descendants().forEach(ProcessHandle::destroy);
        running.destroy();
        try {
            if (!running.waitFor(2, TimeUnit.SECONDS)) {
                running.descendants().forEach(ProcessHandle::destroyForcibly);
                running.destroyForcibly();
                running.waitFor(2, TimeUnit.SECONDS);
            }
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            running.destroyForcibly();
        }
    }
}
