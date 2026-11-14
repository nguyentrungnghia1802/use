package org.tzi.use.plugins.jacamo.bridge;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.tools.ToolProvider;
import org.tzi.use.plugins.jacamo.bridge.protocol.CanonicalJson;

/** Owns the producer process, authenticated checkpoint channel and native evaluator.
 * Presentation observes events; it has no authority to override the evaluation gate. */
public final class StateVerificationSession implements AutoCloseable {
    public interface Listener {
        default void checkpoint(Map<String,Object> request, Map<String,Object> response, CslStateVerifier.Result result) { }
        default void status(String message) { }
        default void log(String line) { }
    }
    private final StateRunConfiguration configuration;
    private final Path work;
    private final String runtimeClasspath;
    private final Listener listener;
    private final CslStateVerifier verifier;
    private final ServerSocket server;
    private final byte[] secret = new byte[32];
    private final Map<String,Map<String,Object>> receipts = new LinkedHashMap<>();
    private final Map<String,String> requestHashes = new HashMap<>();
    private final CountDownLatch finished = new CountDownLatch(1);
    private final AtomicBoolean closed = new AtomicBoolean();
    private volatile Process producer;
    private volatile Socket client;
    private Thread consumer;
    private Thread shutdownHook;

    private StateVerificationSession(StateRunConfiguration configuration, Path work, String runtimeClasspath, Listener listener) throws Exception {
        configuration.validate();
        this.configuration = configuration;
        this.work = work.toAbsolutePath().normalize();
        this.runtimeClasspath = runtimeClasspath;
        this.listener = Objects.requireNonNull(listener);
        Files.createDirectories(this.work);
        verifier = new CslStateVerifier(configuration.mapping(), configuration.csl(), configuration.istar());
        try { server = new ServerSocket(0, 16, InetAddress.getLoopbackAddress()); }
        catch (Exception failure) { verifier.close(); throw failure; }
    }
    public static StateVerificationSession start(StateRunConfiguration configuration, Path work, String runtimeClasspath, Listener listener) throws Exception {
        var session = new StateVerificationSession(configuration, work, runtimeClasspath, listener);
        try { session.launch(); return session; }
        catch (Exception failure) { session.close(); throw failure; }
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
        compileArtifacts();
        consumer = Thread.ofPlatform().daemon().name("csl-state-consumer").start(this::serve);
        var command = new ArrayList<String>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        command.addAll(List.of("-Djava.awt.headless=true", "-cp", runtimeClasspath + File.pathSeparator + work.resolve("artifact-classes"),
                "org.tzi.use.plugins.jacamo.bridge.StateProducerMain", configuration.project().toString(), configuration.mapping().toString(),
                Integer.toString(server.getLocalPort()), secretFile.toString(), work.toString()));
        producer = new ProcessBuilder(command).directory(configuration.project().getParent().toFile()).redirectErrorStream(true).start();
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
    private void compileArtifacts() throws IOException {
        Path source = configuration.project().getParent().resolve("src/env"), output = work.resolve("artifact-classes");
        Files.createDirectories(output);
        if (!Files.isDirectory(source)) return;
        List<String> sources;
        try (var stream = Files.walk(source)) { sources = stream.filter(p -> p.toString().endsWith(".java")).map(Path::toString).toList(); }
        if (sources.isEmpty()) return;
        var compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) throw new IllegalStateException("JDK_21_REQUIRED");
        var args = new ArrayList<>(List.of("-proc:none", "-cp", runtimeClasspath, "-d", output.toString()));
        args.addAll(sources);
        var diagnostics = new ByteArrayOutputStream();
        int code = compiler.run(null, diagnostics, diagnostics, args.toArray(String[]::new));
        String text = diagnostics.toString(StandardCharsets.UTF_8);
        if (!text.isBlank()) listener.log(text);
        if (code != 0) throw new IllegalArgumentException("PROJECT_ARTIFACT_COMPILE_FAILED\n" + text);
    }
    private void serve() {
        while (!server.isClosed()) try (var socket = server.accept()) {
            client = socket;
            socket.setSoTimeout(30000);
            var request = StateWire.read(socket.getInputStream(), secret);
            String hash = StateWire.digest(request);
            if (!StateWire.VERSION.equals(request.get("version"))) throw new IOException("STATE_PROTOCOL_VERSION");
            String token = (String) request.get("token"), run = (String) request.get("run");
            if (token == null || token.isBlank() || run == null || run.isBlank()) throw new IOException("STATE_TOKEN_REQUIRED");
            String key = run + ":" + token;
            Map<String,Object> response = receipts.get(key);
            if (response != null) {
                if (!hash.equals(requestHashes.get(key))) throw new IOException("STATE_TOKEN_COLLISION");
            } else {
                if (receipts.size() >= 16384) throw new IOException("STATE_CHECKPOINT_RECEIPT_CAPACITY");
                CslStateVerifier.Result result = null;
                String failure = "";
                try {
                    var snapshot = StateWire.snapshot(CanonicalJson.object(request.get("snapshot")));
                    if (!run.equals(snapshot.run())) throw new IllegalArgumentException("STATE_ENVELOPE_RUN_MISMATCH");
                    result = verifier.update(snapshot);
                } catch (Exception error) { failure = error.getClass().getSimpleName() + ":" + error.getMessage(); }
                String decision = result == null ? "HOLD_UNVERIFIED" : result.decision();
                response = Map.of("version", StateWire.VERSION, "token", token, "run", run, "requestHash", hash,
                        "decision", decision, "diagnostic", failure, "result", result == null ? Map.of() : result.payload());
                receipts.put(key, response); requestHashes.put(key, hash);
                var report = Map.of("request", request, "response", response);
                String encoded = new String(CanonicalJson.encode(report), StandardCharsets.UTF_8);
                Files.writeString(work.resolve("latest.json"), encoded);
                Files.writeString(work.resolve("consumer-history.jsonl"), encoded + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                // UI failures must never turn HOLD into APPROVE or prevent delivery of the native decision.
                try { listener.checkpoint(request, response, result); }
                catch (RuntimeException presentationFailure) { System.err.println("STATE_VIEW_ERROR " + presentationFailure); }
                System.out.println("CSL_STATE sequence=" + CanonicalJson.object(request.get("snapshot")).get("sequence") + " decision=" + decision + " diagnostic=" + failure);
                if (Boolean.getBoolean("use.state.exit-on-hold") && !decision.equals("APPROVE")) finished.countDown();
            }
            StateWire.write(socket.getOutputStream(), response, secret);
        } catch (Exception failure) {
            if (!server.isClosed()) { System.err.println("STATE_CONSUMER_HELD " + failure); listener.status("HOLD_UNVERIFIED: " + failure); }
        } finally { client = null; }
    }
    @Override public void close() {
        if (!closed.compareAndSet(false, true)) return;
        try { server.close(); } catch (IOException ignored) { }
        var active = client;
        if (active != null) try { active.close(); } catch (IOException ignored) { }
        var running = producer;
        if (running != null && running.isAlive()) {
            running.descendants().forEach(ProcessHandle::destroy); running.destroy();
            try { if (!running.waitFor(2, TimeUnit.SECONDS)) { running.descendants().forEach(ProcessHandle::destroyForcibly); running.destroyForcibly();running.waitFor(2,TimeUnit.SECONDS); } }
            catch (InterruptedException failure) { Thread.currentThread().interrupt(); running.destroyForcibly(); }
        }
        verifier.close();
        try { Files.deleteIfExists(work.resolve("bridge-secret.hex")); } catch (IOException ignored) { }
        if(shutdownHook!=null&&Thread.currentThread()!=shutdownHook)try{Runtime.getRuntime().removeShutdownHook(shutdownHook);}catch(IllegalStateException ignored){}
        finished.countDown();
    }
}
