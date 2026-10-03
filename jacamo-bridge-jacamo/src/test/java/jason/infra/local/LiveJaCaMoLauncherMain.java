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
        Path consumerReady = args.length > 4 && !args[4].isBlank()
                ? Path.of(args[4]).toAbsolutePath().normalize() : null;
        long startupSeconds = args.length > 5 ? Long.parseLong(args[5]) : 60L;
        Path startupDirectory = args.length > 6 && !args[6].isBlank() ? Path.of(args[6]).toAbsolutePath().normalize() : null;
        String runId = args.length > 7 ? args[7] : "";
        Path controlledProject = project;
        if (consumerReady != null && startupSeconds < 1)
            throw new IllegalArgumentException("REAL_JACAMO_STARTUP_TIMEOUT_INVALID");
        // Zero lifetime is an explicit stop-file-controlled interactive session, not a startup timeout.
        if (timeoutSeconds < 0 || (headless && timeoutSeconds == 0))
            throw new IllegalArgumentException("REAL_JACAMO_LIFETIME_INVALID");
        System.setProperty("java.awt.headless", Boolean.toString(headless));
        if (!Files.isRegularFile(project, LinkOption.NOFOLLOW_LINKS))
            throw new IllegalArgumentException("REAL_JACAMO_PROJECT_MISSING:" + project);

        BaseLocalMAS.logger = Logger.getLogger(JaCaMoLauncher.class.getName());
        JaCaMoLauncher launcher = new JaCaMoLauncher() {
            /** Official platforms (including Bridge) start before RunLocalMAS calls this hook. */
            @Override protected void startAgs() {
                if (startupDirectory != null) {
                    var owner = org.jacamo.bridge.contract.ManagedStartupControl.owner(runId, controlledProject,
                            System.getProperty("jacamo.bridge.session", ""),
                            Long.parseLong(System.getProperty("jacamo.bridge.generation", "0")),
                            System.getProperty("jacamo.bridge.modelRevision", ""), ProcessHandle.current().pid());
                    org.jacamo.bridge.contract.ManagedStartupControl.waiting(startupDirectory, owner, startupSeconds);
                    System.out.println("REAL_JACAMO_MANAGED_WAIT_START run=" + runId);
                    System.out.flush();
                    try {
                        var request = org.jacamo.bridge.contract.ManagedStartupControl.awaitRequest(startupDirectory, owner, stop);
                        super.startAgs();
                        org.jacamo.bridge.contract.ManagedStartupControl.acknowledged(startupDirectory, request);
                        System.out.println("REAL_JACAMO_MANAGED_START_ACK run=" + runId);
                        System.out.flush();
                        return;
                    } catch (InterruptedException error) {
                        Thread.currentThread().interrupt(); throw new IllegalStateException("STARTUP_INTERRUPTED", error);
                    }
                }
                if (consumerReady != null) {
                    System.out.println("REAL_JACAMO_WAIT_CONSUMER_READY");
                    System.out.flush();
                    try { awaitConsumerReady(consumerReady, stop, startupSeconds); }
                    catch (InterruptedException error) {
                        Thread.currentThread().interrupt(); throw new IllegalStateException("REAL_JACAMO_START_INTERRUPTED", error);
                    }
                    System.out.println("REAL_JACAMO_CONSUMER_READY_START_AGENTS");
                    System.out.flush();
                }
                super.startAgs();
            }
            /**
             * A disposable production-launcher JVM must not inherit a user's Jason GUI/FileHandler
             * configuration.  In headless mode that configuration can instantiate
             * MASConsoleLogHandler and fail before JaCaMo parses the supplied project.  The
             * launcher still uses the official JaCaMo bootstrap; only its default logging input
             * is made deterministic and process-local.
             */
            @Override protected InputStream getDefaultLogProperties() {
                return new ByteArrayInputStream(launchLogProperties(headless)
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            @Override public synchronized void setupLogger(String ignoredProjectLoggingFile) {
                // Keep headless runs process-local and deterministic.  Interactive runs use the
                // official JaCaMo MAS console handler so the shared launcher exposes the same
                // native runtime window for every case study, even when the project has no
                // logging.properties file.  No project source is modified or interpreted here.
                try (InputStream configuration = getDefaultLogProperties()) {
                    java.util.logging.LogManager.getLogManager().readConfiguration(configuration);
                } catch (java.io.IOException failure) { throw new IllegalStateException("LAUNCH_LOG_CONFIGURATION_FAILED", failure); }
            }
        };
        BaseLocalMAS.runner = launcher;
        RuntimeServicesFactory.set(new JaCaMoRuntimeServices(launcher));
        boolean created = false;
        JaCaMoBridgePlatform bridge = null;
        boolean stoppedNormally = false;
        try {
            System.out.println("REAL_JACAMO_INIT project=" + project + " headless=" + headless);
            System.out.flush();
            int status = launcher.init(new String[] { project.toString() });
            if (status != 0) throw new IllegalStateException("REAL_JACAMO_INIT_FAILED:" + status);
            // JaCaMo 1.3.1's parser leaves this metadata unset for a direct launcher call.
            // Supplying the already validated source path keeps the official adapters rooted
            // in the staged project without changing JaCaMo itself.
            launcher.getJaCaMoProject().setProjectFile(project.toFile());
            if (startupDirectory != null && launcher.getProject().isJade())
                throw new IllegalStateException("UNSUPPORTED_STARTUP_CONTROL:JADE starts agents in platform.start()");
            System.out.println("REAL_JACAMO_INIT_OK");
            System.out.flush();
            launcher.create();
            created = true;
            if (startupDirectory != null) validateControlledPlatforms(launcher.getPlatforms().stream()
                    .map(platform -> platform.getClass().getName()).toList());
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
            awaitStopFile(stop, timeoutSeconds);
            stoppedNormally = true;
        } catch (Exception error) {
            error.printStackTrace(System.err);
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
                // Never turn a timeout/bootstrap failure into a successful process exit.
                System.exit(stoppedNormally ? 0 : 1);
            }
        }
    }

    static String launchLogProperties(boolean headless) {
        if (headless) {
            return "handlers=java.util.logging.ConsoleHandler\n"
                    + ".level=INFO\n";
        }
        return "handlers=jason.runtime.MASConsoleLogHandler\n"
                + ".level=INFO\n"
                + "jason.runtime.MASConsoleLogHandler.level=ALL\n"
                + "jason.runtime.MASConsoleLogHandler.formatter=jason.runtime.MASConsoleLogFormatter\n"
                + "jason.runtime.MASConsoleLogHandler.tabbed=true\n"
                + "jason.runtime.MASConsoleLogHandler.colors=false\n"
                + "java.level=OFF\n"
                + "javax.level=OFF\n"
                + "sun.level=OFF\n"
                + "jade.level=OFF\n";
    }

    static void awaitStopFile(Path stop, long lifetimeSeconds) throws Exception {
        if (lifetimeSeconds < 0) throw new IllegalArgumentException("REAL_JACAMO_LIFETIME_INVALID");
        long started = System.nanoTime();
        while (!Files.exists(stop, LinkOption.NOFOLLOW_LINKS)) {
            if (lifetimeSeconds > 0 && System.nanoTime() - started >= TimeUnit.SECONDS.toNanos(lifetimeSeconds))
                throw new IllegalStateException("REAL_JACAMO_TIMEOUT");
            Thread.sleep(50L);
        }
    }

    static void validateControlledPlatforms(java.util.List<String> platforms) {
        var audited = java.util.Set.of("jacamo.platform.Cartago", "jacamo.platform.Moise", "jacamo.platform.Sai",
                "jacamo.platform.EnvironmentWebInspector",
                "org.jacamo.bridge.adapter.JaCaMoBridgePlatform");
        for (String platform : platforms) if (!audited.contains(platform))
            throw new IllegalStateException("UNSUPPORTED_STARTUP_CONTROL:" + platform);
    }

    static void awaitConsumerReady(Path ready, Path stop, long timeoutSeconds) throws InterruptedException {
        if (timeoutSeconds < 1) throw new IllegalArgumentException("REAL_JACAMO_STARTUP_TIMEOUT_INVALID");
        long started = System.nanoTime();
        while (!Files.isRegularFile(ready, LinkOption.NOFOLLOW_LINKS)) {
            if (Files.exists(stop, LinkOption.NOFOLLOW_LINKS)) throw new IllegalStateException("REAL_JACAMO_START_CANCELLED");
            if (System.nanoTime() - started >= TimeUnit.SECONDS.toNanos(timeoutSeconds))
                throw new IllegalStateException("REAL_JACAMO_CONSUMER_READY_TIMEOUT");
            Thread.sleep(20L);
        }
    }
}
