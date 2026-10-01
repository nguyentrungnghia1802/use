package org.tzi.use.plugins.jacamo.codegrounded;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.tzi.use.plugins.jacamo.codegrounded.RuntimeVerificationFixtures.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.NativeRuntimeProjector;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.NativeRuntimeReplay;

class NativeRuntimePackagedReplayIT {
    @TempDir Path directory;

    @Test void installedReleaseReplaysNativeOclHistoryWithoutMavenOrJaCaMoClasspath() throws Exception {
        Path root = Path.of(".").toRealPath();
        var pipeline = CodeGroundedTestFixtures.helloPipeline();
        var projector = new NativeRuntimeProjector(pipeline, SESSION, 1, REVISION);
        projector.applySnapshot(snapshot("initial", 0));
        projector.coordinator().loadProfileSource("constraints.ocl", RuntimeVerificationCoordinatorTest.PROFILE);
        projector.apply(delta("B", 1, "B"));
        projector.apply(delta("A", 2, "A"));
        Path bundle = Files.createDirectory(directory.resolve("bundle"));
        new NativeRuntimeReplay().exportBundle(projector, pipeline.export().useText(), bundle);

        Path install = Files.createDirectory(directory.resolve("install"));
        Path plugins = Files.createDirectories(install.resolve("lib/plugins"));
        Path pluginJar = plugins.resolve("use-jacamo-plugin-1.0.1.jar");
        try (var zip = new ZipFile(root.resolve("target/use-jacamo-plugin-1.0.1-v2-frozen.zip").toFile());
             var input = zip.getInputStream(zip.getEntry("lib/plugins/use-jacamo-plugin-1.0.1.jar"))) {
            Files.copy(input, pluginJar);
        }
        String javaName = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
        Path javaExecutable = Path.of(System.getProperty("java.home"), "bin", javaName);
        String classpath = root.resolve("../use-gui/target/use-gui.jar").toRealPath()
                + java.io.File.pathSeparator + root.resolve("target/test-classes");
        Path output = directory.resolve("isolated-replay.log");
        var expected = projector.coordinator().latest();
        Process process = new ProcessBuilder(javaExecutable.toString(), "-cp", classpath,
                NativeRuntimeReplaySmokeMain.class.getName(), plugins.toString(), pluginJar.toString(),
                bundle.toString(), expected.stateHash(), expected.resultHash())
                .directory(root.toFile()).redirectErrorStream(true).redirectOutput(output.toFile()).start();
        boolean finished = process.waitFor(30, TimeUnit.SECONDS);
        if (!finished) process.destroyForcibly();
        assertTrue(finished, "isolated native replay timed out");
        String diagnostic = Files.readString(output);
        assertEquals(0, process.exitValue(), diagnostic);
        assertTrue(diagnostic.contains("ISOLATED_NATIVE_RUNTIME_REPLAY_PASS"), diagnostic);
    }
}
