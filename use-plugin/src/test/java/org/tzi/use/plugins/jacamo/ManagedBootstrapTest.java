package org.tzi.use.plugins.jacamo;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.net.URI;
import java.net.ServerSocket;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.jacamo.bridge.adapter.RuntimeDistributionFingerprint;
import org.jacamo.bridge.contract.ManagedStartupControl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tzi.use.main.Session;
import org.tzi.use.plugins.jacamo.bridge.BridgeConnectionConfig;

/** Real separate producer JVM; official platforms are available while reasoning is held. */
class ManagedBootstrapTest {
    @TempDir Path root;
    @Test void officialLocalProducerCanImportBeforeStartAndReleaseOnce() throws Exception {
        Path original = Path.of("../..", "jacamo/examples/writing-paper").toAbsolutePath().normalize();
        Path stage = Files.createDirectory(root.resolve("project"));
        try (var paths = Files.walk(original)) {
            for (Path source : paths.toList()) {
                Path target = stage.resolve(original.relativize(source));
                if (Files.isDirectory(source)) Files.createDirectories(target); else Files.copy(source, target);
            }
        }
        Path jcm = stage.resolve("writing-paper.jcm"), secret = root.resolve("secret.hex"), stop = root.resolve("stop");
        Path control = Files.createDirectory(root.resolve("control"));
        String cp = String.join(java.io.File.pathSeparator,
                Path.of("../jacamo-bridge-jacamo/target/test-classes").toAbsolutePath().normalize().toString(),
                Path.of("../jacamo-bridge-jacamo/target/classes").toAbsolutePath().normalize().toString(),
                Path.of("../jacamo-bridge-contract/target/classes").toAbsolutePath().normalize().toString(),
                System.getProperty("java.class.path"));
        String javaCommand = JavaProcessSupport.executable();
        Process fingerprint = new ProcessBuilder(javaCommand, "-cp", cp, "org.jacamo.bridge.adapter.RuntimeDistributionFingerprintMain").start();
        String fingerprintText = new String(fingerprint.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(fingerprint.waitFor(10, TimeUnit.SECONDS)); assertEquals(0, fingerprint.exitValue());
        String distribution = fingerprintText.lines().filter(line -> line.startsWith("BRIDGE_RUNTIME_DISTRIBUTION_SHA256="))
                .findFirst().orElseThrow().split("=", 2)[1];
        Files.writeString(secret, "12".repeat(32));
        int port; try (var socket = new ServerSocket(0)) { port = socket.getLocalPort(); }
        String text = Files.readString(jcm); int end = text.lastIndexOf('}');
        String platform = "\n platform: org.jacamo.bridge.adapter.JaCaMoBridgePlatform(\"port=" + port
                + "\", \"secretFile=" + secret.toString().replace('\\','/') + "\", \"jcmFile="
                + jcm.toString().replace('\\','/') + "\", \"distributionSha256=" + distribution + "\")\n";
        Files.writeString(jcm, text.substring(0, end) + platform + text.substring(end));
        Process producer = new ProcessBuilder(javaCommand,
                "-cp", cp, "jason.infra.local.LiveJaCaMoLauncherMain", jcm.toString(), stop.toString(), "120", "true", "", "60", control.toString(), "real-run")
                .directory(stage.toFile()).redirectOutput(root.resolve("producer.log").toFile())
                .redirectError(root.resolve("producer.err").toFile()).start();
        try {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(40);
            while (!Files.exists(control.resolve("waiting.json")) && producer.isAlive() && System.nanoTime() < deadline) Thread.sleep(50);
            assertTrue(Files.exists(control.resolve("waiting.json")), () -> {
                try { return "producer exit=" + (producer.isAlive() ? "RUNNING" : producer.exitValue()) + "\n"
                        + Files.readString(root.resolve("producer.log")) + Files.readString(root.resolve("producer.err")); }
                catch (Exception error) { return error.toString(); }
            });
            System.setProperty(ManagedStartupControl.DIRECTORY_PROPERTY, control.toString());
            System.setProperty(ManagedStartupControl.RUN_PROPERTY, "real-run");
            Session session = new Session();
            try (var facade = new DefaultJaCaMoFacade(Path.of("."), session)) {
                System.clearProperty(ManagedStartupControl.DIRECTORY_PROPERTY); System.clearProperty(ManagedStartupControl.RUN_PROPERTY);
                facade.configureBridge(new BridgeConnectionConfig(URI.create("tcp://127.0.0.1:" + port), secret, distribution,
                        Set.of("official.model", "runtime.snapshot"), 4*1024*1024, 20000, 8192));
                facade.importProject(jcm);
                assertEquals("MODEL_READY", facade.workflowStatus().state());
                assertSame(session.system(), facade.materializedSystem());
                assertTrue(session.system().state().numObjects() > 0);
                assertFalse(Files.exists(control.resolve("started.json")));
                var system=session.system();
                facade.disconnectRuntime();
                assertFalse(facade.workflowStatus().startAvailable());
                assertThrows(IllegalStateException.class,facade::startRuntime);
                assertFalse(Files.exists(control.resolve("request.json")));
                facade.resyncRuntime();
                assertSame(system,session.system());
                assertTrue(facade.workflowStatus().startAvailable());
                facade.startRuntime();
                assertEquals("LIVE", facade.workflowStatus().state());
                assertTrue(Files.exists(control.resolve("started.json")));
                assertThrows(IllegalStateException.class, facade::startRuntime);
            }
        } finally {
            System.clearProperty(ManagedStartupControl.DIRECTORY_PROPERTY); System.clearProperty(ManagedStartupControl.RUN_PROPERTY);
            Files.writeString(stop, "stop");
            if (!producer.waitFor(10, TimeUnit.SECONDS)) { producer.destroyForcibly(); producer.waitFor(5, TimeUnit.SECONDS); }
        }
    }
}
