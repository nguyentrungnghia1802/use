package jason.infra.local;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import jacamo.infra.JaCaMoLauncher;
import jacamo.infra.JaCaMoRuntimeServices;
import jason.JasonException;
import jason.runtime.RuntimeServicesFactory;
import org.jacamo.bridge.adapter.JaCaMoBridgePlatform;

/**
 * Starts an actual JaCaMo project in a separate JVM with the production Bridge platform.
 * The process remains alive until the explicit stop file is created by the harness.
 */
public final class LiveJaCaMoLauncherMain {
    private LiveJaCaMoLauncherMain() { }

    public static void main(String[] args) throws Exception {
        if (args.length < 2) throw new IllegalArgumentException("REAL_JACAMO_ARGS_REQUIRED");
        Path project = Path.of(args[0]).toAbsolutePath().normalize();
        Path stop = Path.of(args[1]).toAbsolutePath().normalize();
        long timeoutSeconds = args.length > 2 ? Long.parseLong(args[2]) : 60L;
        boolean headless = args.length <= 3 || Boolean.parseBoolean(args[3]);
        System.setProperty("java.awt.headless", Boolean.toString(headless));
        if (!Files.isRegularFile(project, LinkOption.NOFOLLOW_LINKS))
            throw new IllegalArgumentException("REAL_JACAMO_PROJECT_MISSING:" + project);

        BaseLocalMAS.logger = Logger.getLogger(JaCaMoLauncher.class.getName());
        JaCaMoLauncher launcher = new JaCaMoLauncher() {
            /**
             * A disposable production-launcher JVM must not inherit a user's Jason GUI/FileHandler
             * configuration.  In headless mode that configuration can instantiate
             * MASConsoleLogHandler and fail before JaCaMo parses the supplied project.  The
             * launcher still uses the official JaCaMo bootstrap; only its default logging input
             * is made deterministic and process-local.
             */
            @Override protected InputStream getDefaultLogProperties() {
                return new ByteArrayInputStream(("handlers=java.util.logging.ConsoleHandler\n"
                        + ".level=INFO\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
        };
        BaseLocalMAS.runner = launcher;
        RuntimeServicesFactory.set(new JaCaMoRuntimeServices(launcher));
        boolean created = false;
        JaCaMoBridgePlatform bridge = null;
        try {
            System.out.println("REAL_JACAMO_INIT project=" + project + " headless=" + headless);
            System.out.flush();
            int status = launcher.init(new String[] { project.toString() });
            if (status != 0) throw new IllegalStateException("REAL_JACAMO_INIT_FAILED:" + status);
            // JaCaMo 1.3.1's parser leaves this metadata unset for a direct launcher call.
            // Supplying the already validated source path keeps the official adapters rooted
            // in the staged project without changing JaCaMo itself.
            launcher.getJaCaMoProject().setProjectFile(project.toFile());
            System.out.println("REAL_JACAMO_INIT_OK");
            System.out.flush();
            launcher.create();
            created = true;
            System.out.println("REAL_JACAMO_CREATE_OK platforms=" + launcher.getPlatforms().size()
                    + " agents=" + launcher.getAgs().size());
            System.out.flush();
            launcher.start();
            System.out.println("REAL_JACAMO_START_OK");
            System.out.flush();
            bridge = launcher.getPlatforms().stream()
                    .filter(JaCaMoBridgePlatform.class::isInstance)
                    .map(JaCaMoBridgePlatform.class::cast)
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("REAL_JACAMO_BRIDGE_PLATFORM_MISSING"));
            System.out.println("REAL_JACAMO_BRIDGE_READY port=" + bridge.serverPort()
                    + " session=" + bridge.sessionId() + " agents=" + launcher.getAgs().size());
            System.out.flush();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds);
            while (!Files.exists(stop, LinkOption.NOFOLLOW_LINKS) && System.nanoTime() < deadline)
                Thread.sleep(50L);
            if (!Files.exists(stop, LinkOption.NOFOLLOW_LINKS))
                throw new IllegalStateException("REAL_JACAMO_TIMEOUT");
        } catch (JasonException error) {
            throw error;
        } finally {
            if (created) {
                System.out.println("REAL_JACAMO_BRIDGE_STOP_REQUESTED");
                System.out.flush();
                // The official launcher performs agent shutdown on a worker.
                // Some JaCaMo 1.3.1 CArtAgO agents can block that worker while
                // closing an already detached workspace.  Keep the bounded
                // cleanup attempt, then terminate this disposable evidence JVM
                // explicitly; the production bridge has already been closed by
                // JaCaMoBridgePlatform.stop() on the launcher path.
                Thread cleanup = Thread.ofPlatform().daemon().name("real-jacamo-cleanup")
                        .start(() -> {
                            try {
                                launcher.finish(1000, false, 0);
                            } catch (Throwable failure) {
                                failure.printStackTrace(System.err);
                            }
                        });
                try {
                    cleanup.join(5000L);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
                if (cleanup.isAlive()) {
                    System.err.println("REAL_JACAMO_CLEANUP_TIMEOUT");
                    JaCaMoBridgePlatform bridgeToClose = bridge;
                    if (bridgeToClose != null) {
                        Thread.ofPlatform().daemon().name("real-jacamo-bridge-close")
                                .start(() -> {
                                    try {
                                        bridgeToClose.close();
                                    } catch (Exception failure) {
                                        failure.printStackTrace(System.err);
                                    }
                                });
                    }
                }
                System.out.println("REAL_JACAMO_BRIDGE_STOPPED");
                System.out.flush();
                System.exit(0);
            }
        }
    }
}
