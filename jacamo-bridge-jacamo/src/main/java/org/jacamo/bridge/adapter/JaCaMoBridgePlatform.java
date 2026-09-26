package org.jacamo.bridge.adapter;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;
import jacamo.platform.Platform;
import jacamo.project.JaCaMoProject;
import org.jacamo.bridge.contract.Capability;
import org.jacamo.bridge.contract.CapabilityStatus;
import org.jacamo.bridge.contract.Completeness;
import org.jacamo.bridge.contract.ContractCodec;
import org.jacamo.bridge.contract.ContractEnvelope;
import org.jacamo.bridge.contract.ContractPayloads;
import org.jacamo.bridge.contract.DistributionFingerprint;
import org.jacamo.bridge.contract.MessageType;
import org.jacamo.bridge.contract.ModelSnapshot;
import org.jacamo.bridge.contract.RuntimeEvent;
import org.jacamo.bridge.contract.RuntimeEventKind;
import org.jacamo.bridge.contract.RuntimeSnapshot;
import org.jacamo.bridge.contract.SourceWatermark;

/** Official JaCaMo Platform insertion point and optional authenticated loopback Bridge host. */
public final class JaCaMoBridgePlatform implements Platform, AutoCloseable {
    private static final int MAX_FRAME_BYTES = 4 * 1024 * 1024;
    private static final String PLATFORM_SOURCE = "bridge:platform";

    private final AdapterReadinessRegistry readiness = new AdapterReadinessRegistry();
    private final ArrayBlockingQueue<RuntimeEvent> events = new ArrayBlockingQueue<>(8192);
    private final AtomicLong generations = new AtomicLong();
    private JaCaMoProject project;
    private ModelSnapshot modelSnapshot;
    private String sessionId;
    private AutoCloseable registryHandle;
    private volatile boolean configured;
    private Integer bridgePort;
    private Path bridgeSecretFile;
    private String distributionSha256;
    private LocalTcpBridgeServer server;
    private volatile boolean publishing;
    private Thread publisher;

    @Override public synchronized void setJcmProject(JaCaMoProject project) {
        this.project = Objects.requireNonNull(project);
        readiness.update("project", AdapterReadiness.CONFIGURED, "official JaCaMoProject received");
    }

    @Override public synchronized void init(String[] args) {
        if (project == null) throw new IllegalStateException("BRIDGE_PROJECT_NOT_SET");
        for (String arg : args) {
            if (arg.startsWith("eventCapacity="))
                throw new IllegalArgumentException("eventCapacity is fixed and bounded in this build");
            if (arg.startsWith("port=")) bridgePort = Integer.parseInt(arg.substring("port=".length()));
            else if (arg.startsWith("secretFile=")) bridgeSecretFile = Path.of(arg.substring("secretFile=".length()))
                    .toAbsolutePath().normalize();
            else if (arg.startsWith("distributionSha256="))
                distributionSha256 = arg.substring("distributionSha256=".length());
            else if (!arg.isBlank()) throw new IllegalArgumentException("BRIDGE_PLATFORM_ARGUMENT_UNSUPPORTED");
        }
        int configuredValues = (bridgePort == null ? 0 : 1) + (bridgeSecretFile == null ? 0 : 1)
                + (distributionSha256 == null ? 0 : 1);
        if (configuredValues != 0 && configuredValues != 3)
            throw new IllegalArgumentException("BRIDGE_SERVER_CONFIGURATION_INCOMPLETE");
        if (bridgePort != null && (bridgePort < 1 || bridgePort > 65535))
            throw new IllegalArgumentException("BRIDGE_SERVER_PORT_INVALID");
        if (distributionSha256 != null && !distributionSha256.matches("[0-9a-f]{64}"))
            throw new IllegalArgumentException("BRIDGE_DISTRIBUTION_SHA256_REQUIRED");
        sessionId = UUID.randomUUID().toString();
        configured = true;
        readiness.update("bridge", AdapterReadiness.CONFIGURED, "session=" + sessionId);
        readiness.update("transport", bridgePort == null ? AdapterReadiness.UNCONFIGURED : AdapterReadiness.CONFIGURED,
                bridgePort == null ? "loopback server parameters not supplied" : "loopback TCP configured");
    }

    @Override public synchronized void start() {
        if (!configured) throw new IllegalStateException("BRIDGE_NOT_INITIALIZED");
        try {
            Path jcm = project.getProjectFile().toPath();
            modelSnapshot = new OfficialProjectAdapter().adapt(project, jcm);
            long generation = generations.incrementAndGet();
            String projectKey = modelSnapshot.sources().getFirst().id().scope();
            System.setProperty("jacamo.bridge.session", sessionId);
            System.setProperty("jacamo.bridge.generation", Long.toString(generation));
            System.setProperty("jacamo.bridge.modelRevision", modelSnapshot.modelRevision());
            System.setProperty("jacamo.bridge.projectKey", projectKey);
            registryHandle = BridgeRuntimeRegistry.attach(event -> {
                if (!events.offer(event))
                    readiness.update("events", AdapterReadiness.FAILED,
                            "bounded event queue overflow; resync required");
            });
            if (bridgePort != null) startServer(generation);
            readiness.update("project", AdapterReadiness.READY,
                    "modelRevision=" + modelSnapshot.modelRevision());
            readiness.update("events", AdapterReadiness.ATTACHED, "bounded observer attached");
        } catch (Exception error) {
            readiness.update("bridge", AdapterReadiness.FAILED,
                    error.getClass().getSimpleName() + ": " + error.getMessage());
            try { close(); } catch (Exception cleanup) { error.addSuppressed(cleanup); }
            throw new IllegalStateException("BRIDGE_START_FAILED", error);
        }
    }

    private void startServer(long generation) throws Exception {
        byte[] secret = readSecret(bridgeSecretFile);
        String projectKey = modelSnapshot.sources().getFirst().id().scope();
        DistributionFingerprint distribution = new DistributionFingerprint("1.3.1", distributionSha256,
                Map.of("jacamo", "1.3.1", "jason", "3.3.2", "cartago", "3.1", "moise", "1.1"));
        List<Capability> capabilities = List.of(
                new Capability("official.model", CapabilityStatus.COMPLETE, List.of(), ""),
                new Capability("runtime.snapshot", CapabilityStatus.COMPLETE, List.of(),
                        "faithfully projectable facts only; other observations remain evidence-only"),
                new Capability("runtime.events", CapabilityStatus.PARTIAL, List.of(),
                        "official hooks emit explicitly classified evidence"));
        AtomicLong platformWatermark = new AtomicLong();
        java.util.function.Supplier<byte[]> handshake = () -> encode(MessageType.HANDSHAKE, projectKey,
                generation, distribution, capabilities, Completeness.PARTIAL, Map.of("readOnly", true));
        java.util.function.Supplier<byte[]> model = () -> encode(MessageType.MODEL_SNAPSHOT, projectKey,
                generation, distribution, capabilities, Completeness.COMPLETE,
                ContractPayloads.model(modelSnapshot));
        java.util.function.Supplier<byte[]> runtime = () -> {
            long watermark = platformWatermark.get();
            SourceWatermark source = new SourceWatermark(PLATFORM_SOURCE, watermark);
            Instant capturedAt = Instant.now();
            RuntimeSnapshot snapshot = new RuntimeSnapshot("runtime:" + sessionId + ":" + watermark,
                    modelSnapshot.modelRevision(), capturedAt, capturedAt, Map.of(PLATFORM_SOURCE, source),
                    Map.of(PLATFORM_SOURCE, source), 1, List.of(), Map.of(PLATFORM_SOURCE, Completeness.PARTIAL),
                    AdapterEvidence.digest((modelSnapshot.modelRevision() + ":" + watermark).getBytes(StandardCharsets.UTF_8)));
            return encode(MessageType.RUNTIME_SNAPSHOT, projectKey, generation, distribution, capabilities,
                    Completeness.PARTIAL, ContractPayloads.runtime(snapshot));
        };
        java.util.function.Supplier<byte[]> gap = () -> {
            long watermark = platformWatermark.incrementAndGet();
            RuntimeEvent event = new RuntimeEvent("gap:" + sessionId + ":" + watermark, sessionId, generation,
                    modelSnapshot.modelRevision(), "transport", PLATFORM_SOURCE, watermark, Instant.now(),
                    RuntimeEventKind.GAP, null, null, null, null, "", "", Map.of(), Map.of(),
                    new SourceWatermark(PLATFORM_SOURCE, watermark), Completeness.PARTIAL, List.of());
            return encode(MessageType.RUNTIME_EVENT, projectKey, generation, distribution, capabilities,
                    Completeness.PARTIAL, ContractPayloads.event(event));
        };
        server = new LocalTcpBridgeServer(InetAddress.getLoopbackAddress(), bridgePort, secret, MAX_FRAME_BYTES,
                1024, 16L * 1024 * 1024, handshake, model, runtime, gap);
        java.util.Arrays.fill(secret, (byte) 0);
        server.start();
        publishing = true;
        publisher = Thread.ofPlatform().daemon().name("jacamo-bridge-publisher").start(() -> {
            while (publishing) {
                try {
                    RuntimeEvent event = events.poll(500, java.util.concurrent.TimeUnit.MILLISECONDS);
                    if (event != null) server.publish(event.sourceId() + ":" + event.sourceSequence(),
                            encode(MessageType.RUNTIME_EVENT, projectKey, generation, distribution, capabilities,
                                    event.completeness(), ContractPayloads.event(event)));
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (RuntimeException failure) {
                    readiness.update("transport", AdapterReadiness.FAILED, "event publication failed; resync required");
                }
            }
        });
        readiness.update("transport", AdapterReadiness.READY, "loopback TCP port=" + server.port());
    }

    private byte[] encode(MessageType type, String projectKey, long generation,
                          DistributionFingerprint distribution, List<Capability> capabilities,
                          Completeness completeness, Map<String, Object> payload) {
        return ContractCodec.encode(ContractEnvelope.create("1.0.0", type, "jacamo-bridge-1.0.0",
                distribution, projectKey, modelSnapshot.modelRevision(), sessionId, generation,
                type.name() + ":" + UUID.randomUUID(), Instant.now(), capabilities, completeness,
                Map.of(), List.of(), payload));
    }

    private byte[] readSecret(Path path) throws Exception {
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(path))
            throw new IllegalArgumentException("BRIDGE_SECRET_FILE_INVALID");
        String encoded = Files.readString(path, StandardCharsets.US_ASCII).trim();
        if (!encoded.matches("[0-9a-fA-F]{64,}") || (encoded.length() & 1) != 0 || encoded.length() > 4096)
            throw new IllegalArgumentException("BRIDGE_SECRET_FILE_INVALID");
        byte[] secret = HexFormat.of().parseHex(encoded);
        if (secret.length < 32) throw new IllegalArgumentException("BRIDGE_SECRET_TOO_SHORT");
        return secret;
    }

    @Override public synchronized void stop() {
        try { close(); } catch (Exception error) { throw new IllegalStateException("BRIDGE_STOP_FAILED", error); }
    }
    public synchronized ModelSnapshot modelSnapshot() {
        if (modelSnapshot == null) throw new IllegalStateException("MODEL_SNAPSHOT_NOT_READY");
        return modelSnapshot;
    }
    public RuntimeEvent pollEvent() { return events.poll(); }
    public String sessionId() { return sessionId; }
    public long generation() { return generations.get(); }
    public synchronized int serverPort() {
        if (server == null) throw new IllegalStateException("BRIDGE_SERVER_NOT_STARTED");
        return server.port();
    }
    public AdapterReadinessRegistry readiness() { return readiness; }

    @Override public synchronized void close() throws Exception {
        Exception failure = null;
        publishing = false;
        if (publisher != null) {
            publisher.interrupt();
            try { publisher.join(1000); } catch (InterruptedException error) {
                Thread.currentThread().interrupt(); failure = error;
            }
            publisher = null;
        }
        if (server != null) { server.close(); server = null; }
        if (registryHandle != null) {
            try { registryHandle.close(); } catch (Exception error) { failure = error; }
            registryHandle = null;
        }
        if (sessionId != null && sessionId.equals(System.getProperty("jacamo.bridge.session"))) {
            System.clearProperty("jacamo.bridge.session");
            System.clearProperty("jacamo.bridge.generation");
            System.clearProperty("jacamo.bridge.modelRevision");
            System.clearProperty("jacamo.bridge.projectKey");
        }
        events.clear(); modelSnapshot = null; configured = false;
        readiness.update("bridge", AdapterReadiness.STOPPED, "listeners, server and queues closed");
        if (failure != null) throw failure;
    }
}
