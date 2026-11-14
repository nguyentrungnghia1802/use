package org.tzi.use.plugins.jacamo.bridge;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.*;

/** Compatibility CLI. USE plugins embed StateVerificationSession directly. */
public final class StateWorkbenchMain {
    public static void main(String[] args) throws Exception {
        if (args.length < 4) throw new IllegalArgumentException("Usage: project.jcm mapping.xml model.csl model.istar [--headless] [--seconds=N]");
        boolean headless = Arrays.asList(args).contains("--headless");
        var configuration = new StateRunConfiguration(Path.of(args[0]), Path.of(args[1]), Path.of(args[2]), Path.of(args[3]));
        Path work = Path.of(System.getProperty("use.state.work", Path.of(System.getProperty("java.io.tmpdir"), "use-csl-state-" + System.currentTimeMillis()).toString()));
        var owner = new AtomicReference<StateVerificationSession>();
        var view = new AtomicReference<StandaloneStateView>();
        if (!headless) SwingUtilities.invokeAndWait(() -> view.set(new StandaloneStateView(() -> {
            var running = owner.get(); if (running != null) Thread.ofPlatform().daemon().start(running::close);
        })));
        StateVerificationSession.Listener listener = headless ? new StateVerificationSession.Listener() { } : view.get();
        try (var session = StateVerificationSession.start(configuration, work, System.getProperty("java.class.path"), listener)) {
            owner.set(session);
            var hook = new Thread(session::close); Runtime.getRuntime().addShutdownHook(hook);
            try {
                for (String arg : args) if (arg.startsWith("--seconds=")) {
                    int seconds = Integer.parseInt(arg.substring(10));
                    Thread.ofPlatform().daemon().start(() -> { try { Thread.sleep(seconds * 1000L); session.close(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } });
                }
                session.await();
            } finally { try { Runtime.getRuntime().removeShutdownHook(hook); } catch (IllegalStateException ignored) { } }
        }
    }
}
