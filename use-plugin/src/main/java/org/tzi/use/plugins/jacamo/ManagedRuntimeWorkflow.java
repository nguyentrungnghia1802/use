package org.tzi.use.plugins.jacamo;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import org.jacamo.bridge.contract.CanonicalJson;
import org.jacamo.bridge.contract.ManagedStartupControl;

/** GUI workflow ownership. Acknowledgement comes from the official producer's startAgs hook. */
final class ManagedRuntimeWorkflow {
    private final Path directory;
    private final String run;
    private volatile boolean importing, imported, oclReady, starting;
    private volatile Map<String,Object> owner = Map.of();
    private volatile String error = "";
    ManagedRuntimeWorkflow() {
        String configured = System.getProperty(ManagedStartupControl.DIRECTORY_PROPERTY, "");
        directory = configured.isBlank() ? null : Path.of(configured).toAbsolutePath().normalize();
        run = System.getProperty(ManagedStartupControl.RUN_PROPERTY, "");
    }
    void importing() { importing = true; }
    void imported(Path project, JaCaMoFacade.AuthorityStatus authority, boolean sessionActive) {
        if (directory != null) {
            var waiting = ManagedStartupControl.read(directory, "waiting.json");
            if (waiting.isEmpty()) throw new IllegalStateException("STARTUP_PRODUCER_NOT_WAITING");
            var actual = CanonicalJson.object(waiting.get("owner"));
            long pid = ((Number)actual.get("producerPid")).longValue();
            var selected = ManagedStartupControl.owner(run, project, authority.sessionId(), authority.generation(), authority.modelRevision(), pid);
            ManagedStartupControl.requireOwner(selected, waiting);
            if (!sessionActive) throw new IllegalStateException("STARTUP_SESSION_NOT_ACTIVE");
            owner = selected;
        }
        imported = true; importing = false; error = "";
    }
    void importFailed(String diagnostic) {
        importing = false; error = diagnostic;
        if (directory != null && !imported) ManagedStartupControl.cancel(directory, "IMPORT_FAILED:" + diagnostic);
    }
    void profileReady() { oclReady = true; }
    boolean owns(JaCaMoFacade.AuthorityStatus authority) {
        var acceptedOwner=owner;
        return authority.sessionId().equals(acceptedOwner.get("sessionId"))
                && Long.valueOf(authority.generation()).equals(acceptedOwner.get("generation"))
                && authority.modelRevision().equals(acceptedOwner.get("modelRevision"));
    }
    JaCaMoFacade.WorkflowStatus status() {
        if (importing) return view("IMPORTING", false, "Import / Session activation in progress", "", "");
        if (directory == null) return view(imported ? "LIVE" : "NOT_IMPORTED", false,
                "External producer; verification pause/resume requires negotiated ExecutionControl capability. Disconnect does not control JaCaMo.", "", "");
        try {
            if (!error.isBlank()) return view("ERROR", false, error, "", "");
            var waiting = ManagedStartupControl.read(directory, "waiting.json");
            if (waiting.isEmpty()) return view("BOOTSTRAPPING", false, "Waiting for official local startAgs boundary", "", "");
            var actualOwner = CanonicalJson.object(waiting.get("owner"));
            long pid = ((Number)actualOwner.get("producerPid")).longValue();
            String bootstrap = waiting.get("bootstrapAt").toString();
            if (ProcessHandle.of(pid).filter(ProcessHandle::isAlive).isEmpty())
                return view("STOPPED", false, "PRODUCER_EXITED", bootstrap, "");
            var ack = ManagedStartupControl.read(directory, "started.json");
            if (!ack.isEmpty()) {
                ManagedStartupControl.requireOwner(owner, ack);
                var request = ManagedStartupControl.read(directory, "request.json");
                if (!ack.get("requestedAt").equals(request.get("requestedAt"))) throw new IllegalStateException("STARTUP_ACK_MISMATCH");
                return view("LIVE", false, "OBSERVED_SUPPORTED_PROJECTION_ONLY; platforms/timers already bootstrapped", bootstrap, ack.get("startedAt").toString());
            }
            if (!ManagedStartupControl.read(directory, "cancel.json").isEmpty())
                return view("STOPPED", false, "STARTUP_CANCELLED", bootstrap, "");
            if (!Instant.now().isBefore(Instant.parse(waiting.get("deadline").toString())))
                return view("ERROR", false, "STARTUP_TIMEOUT", bootstrap, "");
            return view(starting ? "STARTING" : imported ? oclReady ? "OCL_READY" : "MODEL_READY" : "BOOTSTRAPPING",
                    imported && !starting, "Local reasoning waiting; platform side effects are not paused", bootstrap, "");
        } catch (RuntimeException failure) { return view("ERROR", false, failure.getMessage(), "", ""); }
    }
    synchronized void start() {
        var before = status();
        if (!before.startAvailable()) throw new IllegalStateException("START_RUNTIME_UNAVAILABLE:" + before.diagnostic());
        starting = true;
        try {
            ManagedStartupControl.request(directory, owner);
            while (true) {
                var current = status();
                if (current.state().equals("LIVE")) return;
                if (current.state().equals("ERROR") || current.state().equals("STOPPED"))
                    throw new IllegalStateException(current.diagnostic());
                Thread.sleep(20);
            }
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt(); error = "START_RUNTIME_INTERRUPTED"; cancel();
            throw new IllegalStateException(error, failure);
        } catch (RuntimeException failure) { error = failure.getMessage(); cancel(); throw failure; }
    }
    void cancel() {
        if (directory != null && ManagedStartupControl.read(directory, "started.json").isEmpty())
            ManagedStartupControl.cancel(directory, "WORKBENCH_CANCEL");
    }
    private JaCaMoFacade.WorkflowStatus view(String state, boolean enabled, String diagnostic, String bootstrap, String started) {
        return new JaCaMoFacade.WorkflowStatus(state, enabled, directory != null, run, diagnostic, bootstrap, started);
    }
}
