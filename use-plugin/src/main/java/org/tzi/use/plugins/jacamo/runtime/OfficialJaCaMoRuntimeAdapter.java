package org.tzi.use.plugins.jacamo.runtime;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import jacamo.infra.JaCaMoLauncher;

/** Imports JaCaMo unchanged; its extension points install source checkpoint observation/admission. */
public final class OfficialJaCaMoRuntimeAdapter implements JaCaMoRuntimeAdapter {
    private final JaCaMoLauncher launcher;
    private final JaCaMoCheckpoints checkpoints;
    private final PrintStream output = System.out;

    public OfficialJaCaMoRuntimeAdapter(String run, CheckpointHandler handler) {
        checkpoints = new JaCaMoCheckpoints(run, handler);
        launcher = new JaCaMoLauncher() {
            {
                runner = this;
                jason.runtime.RuntimeServicesFactory.set(new jacamo.infra.JaCaMoRuntimeServices(this));
            }
            @Override public synchronized void setupLogger(String ignored) {
                String configuration = "handlers=java.util.logging.ConsoleHandler\n.level=WARNING\njava.util.logging.ConsoleHandler.level=WARNING\n";
                try {
                    java.util.logging.LogManager.getLogManager().readConfiguration(
                            new ByteArrayInputStream(configuration.getBytes(StandardCharsets.UTF_8)));
                } catch (IOException failure) { throw new IllegalStateException(failure); }
            }
            @Override protected void startAgs() { checkpoints.initial(); super.startAgs(); }
        };
    }
    @Override public JaCaMoLauncher launcher() { return launcher; }
    @Override public void initialize(Path project) {
        if (launcher.init(new String[]{project.toAbsolutePath().toString()}) != 0)
            throw new IllegalArgumentException("JACAMO_PROJECT_INIT_FAILED");
        System.setOut(output); System.setErr(output);
        for (var agent : launcher.getJaCaMoProject().getAgents()) agent.addArchClass(JaCaMoCheckpoints.Architecture.class.getName());
    }
    @Override public void create() throws Exception {
        launcher.create();
        for (var local : launcher.getAgs().values()) {
            if (local.getClass() != jason.infra.local.LocalAgArch.class)
                throw new IllegalArgumentException("STATE_GATE_LOCAL_SCHEDULER_REQUIRED");
            local.setCycles(1); local.setCyclesAct(1); local.getTS().getSettings().setTRO(false);
            checkpoints.install(local.getTS(), local.getAgName());
        }
    }
    @Override public void start() { launcher.start(); }
    @Override public void close() { checkpoints.close(); }
}
