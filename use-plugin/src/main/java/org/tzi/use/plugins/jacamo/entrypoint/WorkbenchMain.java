package org.tzi.use.plugins.jacamo.entrypoint;

import org.tzi.use.plugins.jacamo.gui.main.StandaloneWindow;
import org.tzi.use.plugins.jacamo.session.ExperimentBundle;
import org.tzi.use.plugins.jacamo.session.VerificationSession;
import org.tzi.use.plugins.jacamo.session.VerificationSession.Configuration;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/** Compatibility CLI. USE plugins embed VerificationSession directly. */
public final class WorkbenchMain {
    public static void main(String[] args) throws Exception {
        boolean bundled = args.length > 0 && ExperimentBundle.hasBundleExtension(Path.of(args[0]));
        int inputCount = bundled ? 1 : 4;
        if (args.length < inputCount) throw new IllegalArgumentException(
                "Usage: experiment.mrv [--headless] [--seconds=N]\n"
                + "   or: project.jcm mapping.xml model.csl model.istar [--headless] [--seconds=N]");
        var options = Arrays.asList(args).subList(inputCount, args.length);
        for (String option : options) {
            if (!option.equals("--headless") && !option.startsWith("--seconds=")) {
                throw new IllegalArgumentException("Unknown option: " + option);
            }
        }
        boolean headless = options.contains("--headless");
        Path work = Path.of(System.getProperty("use.state.work", Path.of(System.getProperty("java.io.tmpdir"), "use-csl-state-" + System.currentTimeMillis()).toString()));
        var configuration = bundled ? ExperimentBundle.unpack(Path.of(args[0]), work)
                : new Configuration(Path.of(args[0]), Path.of(args[1]), Path.of(args[2]), Path.of(args[3]));
        var owner = new AtomicReference<VerificationSession>();
        VerificationSession.Listener listener = headless ? new VerificationSession.Listener() { }
                : StandaloneWindow.open(() -> {
                    var running = owner.get();
                    if (running != null) Thread.ofPlatform().daemon().start(running::close);
                });
        try (var session = VerificationSession.start(configuration, work, System.getProperty("java.class.path"), listener)) {
            owner.set(session);
            var hook = new Thread(session::close); Runtime.getRuntime().addShutdownHook(hook);
            try {
                for (String arg : options) if (arg.startsWith("--seconds=")) {
                    int seconds = Integer.parseInt(arg.substring(10));
                    Thread.ofPlatform().daemon().start(() -> { try { Thread.sleep(seconds * 1000L); session.close(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } });
                }
                session.await();
            } finally { try { Runtime.getRuntime().removeShutdownHook(hook); } catch (IllegalStateException ignored) { } }
        }
    }
}
