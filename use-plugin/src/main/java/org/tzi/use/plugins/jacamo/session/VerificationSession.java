package org.tzi.use.plugins.jacamo.session;

import org.tzi.use.plugins.jacamo.bridge.protocol.CheckpointProtocol;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.tzi.use.plugins.jacamo.bridge.protocol.CanonicalJson;

/** Owns the producer process, authenticated checkpoint channel and native evaluator.
 * Presentation observes events; it has no authority to override the evaluation gate. */
public final class VerificationSession implements AutoCloseable {
    /** Inputs for one owned source run. No Swing component or application singleton. */
    public record Configuration(Path project, Path mapping, Path csl, Path istar, Path artifactSources) {
        public Configuration(Path project, Path mapping, Path csl, Path istar) {
            this(project, mapping, csl, istar, null);
        }

        public Configuration {
            project = project.toAbsolutePath().normalize();
            mapping = mapping.toAbsolutePath().normalize();
            csl = csl.toAbsolutePath().normalize();
            istar = istar.toAbsolutePath().normalize();
            if (artifactSources != null) {
                artifactSources = project.getParent().resolve(artifactSources).normalize();
            }
        }

        public Path artifactSourceDirectory() {
            return artifactSources == null ? project.getParent().resolve("src/env") : artifactSources;
        }

        public void validate() throws IOException {
            for (Path file : new Path[]{project, mapping, csl, istar})
                if (!Files.isRegularFile(file) || !Files.isReadable(file)) throw new IOException("Cannot read " + file);
            if (!project.getFileName().toString().endsWith(".jcm")) throw new IOException("Select a JaCaMo .jcm project");
            if (artifactSources != null && (!Files.isDirectory(artifactSources) || !Files.isReadable(artifactSources))) {
                throw new IOException("Cannot read Artifact source directory " + artifactSources);
            }
        }
    }

    public interface Listener {
        default void checkpoint(Map<String,Object> request, Map<String,Object> response, SnapshotVerifier.Result result) { }
        default void status(String message) { }
        default void log(String line) { }
    }
    private final Configuration configuration;
    private final Path work;
    private final String runtimeClasspath;
    private final Listener listener;
    private final SnapshotVerifier verifier;
    private final String inputRevision;
    private final ServerSocket server;
    private final byte[] secret = new byte[32];
    private record Receipt(String requestHash, Map<String, Object> response) { }

    private final Map<String, Receipt> receipts = new LinkedHashMap<>();
    private final CountDownLatch finished = new CountDownLatch(1);
    private final AtomicBoolean closed = new AtomicBoolean();
    private volatile Process producer;
    private volatile Socket client;
    private Thread shutdownHook;

    private VerificationSession(Configuration configuration, Path work, String runtimeClasspath, Listener listener) throws Exception {
        configuration.validate();
        this.configuration = configuration;
        this.work = work.toAbsolutePath().normalize();
        this.runtimeClasspath = runtimeClasspath;
        this.listener = Objects.requireNonNull(listener);
        Files.createDirectories(this.work);
        verifier = new SnapshotVerifier(configuration.mapping(), configuration.csl(), configuration.istar());
        try {
            inputRevision = CheckpointProtocol.inputRevision(verifier.modelRevision(), configuration.project(), verifier.mapping());
            server = new ServerSocket(0, 16, InetAddress.getLoopbackAddress());
        } catch (Exception failure) { verifier.close(); throw failure; }
    }
    public static VerificationSession start(Configuration configuration, Path work, String runtimeClasspath, Listener listener) throws Exception {
        var session = new VerificationSession(configuration, work, runtimeClasspath, listener);
        try { session.launch(); return session; }
        catch (Exception failure) { session.close(); throw failure; }
    }

    public static VerificationSession start(Configuration configuration, Listener listener) throws Exception {
        String classpath = ProducerProcess.resolveClasspath();
        Path work = Files.createTempDirectory("use-jacamo-");
        return start(configuration, work, classpath, listener);
    }
    public Path work() { return work; }
    public org.vnu.sme.goal.dsl.csl.mm.CslModel cslModel() { return verifier.cslModel(); }
    public org.vnu.sme.goal.dsl.istar.mm.GoalModel goalModel() { return verifier.goalModel(); }
    /** Borrowed for read-only diagram rendering; all writes remain in the checkpoint consumer. */
    public org.tzi.use.uml.mm.MModel nativeModel() { return verifier.system().model(); }
    public long producerPid() { return producer == null ? -1 : producer.pid(); }
    public boolean isRunning() { return !closed.get() && producer != null && producer.isAlive(); }
    public void await() throws InterruptedException { finished.await(); }

    private void launch() throws Exception {
        shutdownHook=new Thread(this::close,"stop-owned-jacamo");Runtime.getRuntime().addShutdownHook(shutdownHook);
        new SecureRandom().nextBytes(secret);
        Path secretFile = work.resolve("bridge-secret.hex");
        Files.writeString(secretFile, HexFormat.of().formatHex(secret), StandardCharsets.US_ASCII);
        try { Files.setPosixFilePermissions(secretFile, PosixFilePermissions.fromString("rw-------")); }
        catch (UnsupportedOperationException ignored) { }
        ProducerProcess.compileArtifacts(configuration.artifactSourceDirectory(), work, runtimeClasspath, listener::log);
        Thread.ofPlatform().daemon().name("csl-state-consumer").start(this::serve);
        producer = ProducerProcess.start(configuration, work, runtimeClasspath, server.getLocalPort(), secretFile, verifier.modelRevision());
        Process running = producer;
        Thread.ofPlatform().daemon().name("jacamo-console").start(() -> {
            try (var reader = running.inputReader(); var log = Files.newBufferedWriter(work.resolve("producer.log"))) {
                String line;
                while ((line = reader.readLine()) != null) { log.write(line); log.newLine(); log.flush(); listener.log(line); }
            } catch (IOException failure) { if (!closed.get()) listener.status("Console unavailable: " + failure.getMessage()); }
        });
        running.onExit().thenRun(() -> {
            listener.status(closed.get() ? "JaCaMo stopped" : "JaCaMo exited (" + running.exitValue() + "). See Console.");
            finished.countDown();
        });
        listener.status("JaCaMo started");
        System.out.println("STATE_WORKBENCH work=" + work + " pid=" + running.pid());
    }

    private void serve() {
        while (!server.isClosed()) try (var socket = server.accept()) {
            client = socket;
            socket.setSoTimeout(30000);
            var request = CheckpointProtocol.read(socket.getInputStream(), secret);
            var response = handleCheckpoint(request);
            CheckpointProtocol.write(socket.getOutputStream(), response, secret);
        } catch (Exception failure) {
            if (!server.isClosed()) { System.err.println("STATE_CONSUMER_HELD " + failure); listener.status("HOLD_UNVERIFIED: " + failure); }
        } finally { client = null; }
    }

    private Map<String, Object> handleCheckpoint(Map<String, Object> request) throws Exception {
        String hash = CheckpointProtocol.digest(request);
        if (!CheckpointProtocol.VERSION.equals(request.get("version"))) throw new IOException("STATE_PROTOCOL_VERSION");
        String token = (String) request.get("token");
        String run = (String) request.get("run");
        if (token == null || token.isBlank() || run == null || run.isBlank()) throw new IOException("STATE_TOKEN_REQUIRED");
        var cut = CheckpointProtocol.Cut.require(request.get("cut"), run, inputRevision);

        String key = run + ":" + token;
        Receipt previous = receipts.get(key);
        if (previous != null) {
            if (!hash.equals(previous.requestHash())) throw new IOException("STATE_TOKEN_COLLISION");
            return previous.response();
        }
        if (receipts.size() >= 16384) throw new IOException("STATE_CHECKPOINT_RECEIPT_CAPACITY");

        SnapshotVerifier.Result result = null;
        String failure = "";
        try {
            var snapshot = CheckpointProtocol.decodeSnapshot(CanonicalJson.object(request.get("snapshot")));
            if (!run.equals(snapshot.run())) throw new IllegalArgumentException("STATE_ENVELOPE_RUN_MISMATCH");
            result = verifier.update(snapshot);
        } catch (Exception error) {
            failure = error.getClass().getSimpleName() + ":" + error.getMessage();
        }

        String decision = result == null ? "HOLD_UNVERIFIED" : result.decision();
        Map<String, Object> payload = result == null ? Map.of() : result.payload();
        Map<String, Object> response = Map.of("version", CheckpointProtocol.VERSION, "token", token, "run", run,
                "requestHash", hash, "cut", cut.payload(), "decision", decision, "diagnostic", failure,
                "result", payload, "resultHash", CheckpointProtocol.digest(payload));
        receipts.put(key, new Receipt(hash, response));
        recordResult(request, response);
        // UI failures must never turn HOLD into APPROVE or prevent delivery of the native decision.
        try { listener.checkpoint(request, response, result); }
        catch (RuntimeException presentationFailure) { System.err.println("STATE_VIEW_ERROR " + presentationFailure); }
        System.out.println("CSL_STATE sequence=" + CanonicalJson.object(request.get("snapshot")).get("sequence")
                + " decision=" + decision + " diagnostic=" + failure);
        if (Boolean.getBoolean("use.state.exit-on-hold") && !decision.equals("APPROVE")) finished.countDown();
        return response;
    }

    private void recordResult(Map<String, Object> request, Map<String, Object> response) throws IOException {
        var report = Map.of("request", request, "response", response);
        String encoded = new String(CanonicalJson.encode(report), StandardCharsets.UTF_8);
        Files.writeString(work.resolve("latest.json"), encoded);
        Files.writeString(work.resolve("consumer-history.jsonl"), encoded + "\n",
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }
    @Override public void close() {
        if (!closed.compareAndSet(false, true)) return;
        try { server.close(); } catch (IOException ignored) { }
        var active = client;
        if (active != null) try { active.close(); } catch (IOException ignored) { }
        ProducerProcess.stop(producer);
        verifier.close();
        try { Files.deleteIfExists(work.resolve("bridge-secret.hex")); } catch (IOException ignored) { }
        if(shutdownHook!=null&&Thread.currentThread()!=shutdownHook)try{Runtime.getRuntime().removeShutdownHook(shutdownHook);}catch(IllegalStateException ignored){}
        finished.countDown();
    }
}
