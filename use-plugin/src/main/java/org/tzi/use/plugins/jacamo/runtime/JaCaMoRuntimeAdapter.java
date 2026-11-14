package org.tzi.use.plugins.jacamo.runtime;

import java.nio.file.Path;
import java.util.List;
import jacamo.infra.JaCaMoLauncher;

/** Source-side integration: JaCaMo owns execution and checkpoint selection, not the bridge. */
public interface JaCaMoRuntimeAdapter extends AutoCloseable {
    record Checkpoint(String token, String agent, String plan, String outcome, String boundary,
                      String invocation, int intention, int mergedPlans, List<String> messages, boolean complete) {
        public Checkpoint { messages = List.copyOf(messages); }
    }
    @FunctionalInterface interface CheckpointHandler {
        /** Called only while the source is frozen. False keeps JaCaMo stopped. */
        boolean verify(Checkpoint checkpoint) throws Exception;
    }
    JaCaMoLauncher launcher();
    void initialize(Path project);
    void create() throws Exception;
    void start();
    @Override void close();
}
