package org.jacamo.bridge.adapter;

import cartago.CartagoEnvironment;
import cartago.WorkspaceDescriptor;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import jacamo.platform.Platform;
import jacamo.project.JaCaMoProject;
import jason.mas2j.AgentParameters;
import org.jacamo.bridge.contract.BridgeEntityId;
import org.jacamo.bridge.contract.Capability;
import org.jacamo.bridge.contract.CapabilityStatus;
import org.jacamo.bridge.contract.Completeness;
import org.jacamo.bridge.contract.ContractCodec;
import org.jacamo.bridge.contract.ContractEnvelope;
import org.jacamo.bridge.contract.ContractPayloads;
import org.jacamo.bridge.contract.DistributionFingerprint;
import org.jacamo.bridge.contract.MessageType;
import org.jacamo.bridge.contract.ModelSnapshot;
import org.jacamo.bridge.contract.ProjectionStatus;
import org.jacamo.bridge.contract.RuntimeEvent;
import org.jacamo.bridge.contract.RuntimeEventKind;
import org.jacamo.bridge.contract.RuntimeFact;
import org.jacamo.bridge.contract.RuntimeFactKind;
import org.jacamo.bridge.contract.RuntimeSnapshot;
import org.jacamo.bridge.contract.SourceWatermark;
import ora4mas.nopl.GroupBoard;
import ora4mas.nopl.NormativeBoard;
import ora4mas.nopl.SchemeBoard;

/** Official JaCaMo Platform insertion point and authenticated loopback Bridge host. */
public final class JaCaMoBridgePlatform implements Platform, AutoCloseable {
    private static final int MAX_FRAME_BYTES = 4 * 1024 * 1024;
    private static final int EVENT_CAPACITY = 8192;
    private static final String BRIDGE_ARCH = BridgeAgArch.class.getName();

    private final AdapterReadinessRegistry readiness = new AdapterReadinessRegistry();
    private final ArrayBlockingQueue<RuntimeEvent> events = new ArrayBlockingQueue<>(EVENT_CAPACITY);
    private final java.util.concurrent.atomic.AtomicBoolean publicationGap = new java.util.concurrent.atomic.AtomicBoolean();
    private final AtomicLong generations = new AtomicLong();
    private final AtomicLong transportWatermark = new AtomicLong();
    private BridgeCheckpointSource checkpointSource;
    private JaCaMoProject project;
    private ModelSnapshot modelSnapshot;
    private String sessionId;
    private AutoCloseable registryHandle;
    private SnapshotCoordinator runtimeCoordinator;
    private JasonSnapshotSource jasonSource;
    private volatile RuntimeSnapshot latestRuntimeSnapshot;
    private volatile boolean configured;
    private Integer bridgePort;
    private Path bridgeSecretFile;
    private Path configuredProjectFile;
    private String distributionSha256;
    private LocalTcpBridgeServer server;
    private volatile boolean publishing;
    private volatile boolean started;
    private Thread publisher;

    @Override public synchronized void setJcmProject(JaCaMoProject project) {
        this.project = Objects.requireNonNull(project);
        int injected = 0;
        for (AgentParameters agent : project.getAgents()) {
            if (!agent.getAgArchClasses().contains(BRIDGE_ARCH)) {
                agent.addArchClass(BRIDGE_ARCH);
                injected++;
            }
        }
        readiness.update("project", AdapterReadiness.CONFIGURED, "official JaCaMoProject received");
        readiness.update("agents", AdapterReadiness.READY,
                "BridgeAgArch=" + BRIDGE_ARCH + "; injected=" + injected);
    }

    @Override public synchronized void init(String[] args) {
        if (project == null) throw new IllegalStateException("BRIDGE_PROJECT_NOT_SET");
        for (String arg : args == null ? new String[0] : args) {
            if (arg.startsWith("eventCapacity="))
                throw new IllegalArgumentException("eventCapacity is fixed and bounded in this build");
            if (arg.startsWith("port=")) bridgePort = Integer.parseInt(arg.substring("port=".length()));
            else if (arg.startsWith("secretFile=")) bridgeSecretFile = Path.of(arg.substring("secretFile=".length()))
                    .toAbsolutePath().normalize();
            else if (arg.startsWith("jcmFile=")) configuredProjectFile = Path.of(arg.substring("jcmFile=".length()))
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
        long nextGeneration = generations.get() + 1;
        System.setProperty("jacamo.bridge.session", sessionId);
        System.setProperty("jacamo.bridge.generation", Long.toString(nextGeneration));
        System.setProperty("jacamo.bridge.projectKey", project.getSocName());
        registryHandle = BridgeRuntimeRegistry.attach(this::receiveRuntimeEvent);
        configured = true;
        readiness.update("bridge", AdapterReadiness.CONFIGURED, "session=" + sessionId);
        readiness.update("transport", bridgePort == null ? AdapterReadiness.UNCONFIGURED : AdapterReadiness.CONFIGURED,
                bridgePort == null ? "loopback server parameters not supplied" : "loopback TCP configured");
    }

    @Override public synchronized void start() {
        if (!configured) throw new IllegalStateException("BRIDGE_NOT_INITIALIZED");
        try {
            Path jcm = resolveProjectFile();
            prepareOfficialProject(jcm);
            modelSnapshot = new OfficialProjectAdapter().adapt(project, jcm);
            BridgeRuntimeRegistry.configureDeclarations(modelSnapshot.semanticContract());
            long generation = generations.incrementAndGet();
            String projectKey = modelSnapshot.sources().getFirst().id().scope();
            System.setProperty("jacamo.bridge.session", sessionId);
            System.setProperty("jacamo.bridge.generation", Long.toString(generation));
            System.setProperty("jacamo.bridge.modelRevision", modelSnapshot.modelRevision());
            System.setProperty("jacamo.bridge.projectKey", projectKey);
            installRuntimeSources();
            if (bridgePort != null) startServer(generation);
            started = true;
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

    private void installRuntimeSources() throws Exception {
        jasonSource = new JasonSnapshotSource(sessionId);
        var sources = new ArrayList<SnapshotSource>();
        sources.add(jasonSource);
        sources.add(new CartagoSnapshotSource(CartagoEnvironment.getInstance(), configuredWorkspaces(), sessionId));
        Set<String> declaredOrganisations = project.getOrgs().stream()
                .map(value -> value.getName()).collect(java.util.stream.Collectors.toUnmodifiableSet());
        for (String name : declaredOrganisations) {
            sources.add(new MoiseBoardSnapshotSource(name, sessionId, JaCaMoBridgePlatform::officialGroupBoards,
                    JaCaMoBridgePlatform::officialSchemeBoards,modelSnapshot.semanticContract(),resolveProjectFile().getParent()));
        }
        // OrgBoard/SchemeBoard instances may be created by agent plans even when the JCM has no static
        // organisation declaration. Keep those official boards in a
        // disjoint dynamic source, and always attach NPL so later-created normative engines are discovered.
        sources.add(new MoiseBoardSnapshotSource(declaredOrganisations, sessionId,
                JaCaMoBridgePlatform::officialGroupBoards, JaCaMoBridgePlatform::officialSchemeBoards,modelSnapshot.semanticContract(),resolveProjectFile().getParent()));
        sources.add(new NplBoardSnapshotSource(sessionId, NormativeBoard::getNormativeBoards));
        checkpointSource=new BridgeCheckpointSource();sources.add(checkpointSource);
        runtimeCoordinator = new SnapshotCoordinator(sources, EVENT_CAPACITY, this::enqueueEvent);
        readiness.update("runtime", AdapterReadiness.ATTACHED,
                "SnapshotCoordinator sources=" + sources.stream().map(SnapshotSource::sourceId).toList());
    }

    private List<String> configuredWorkspaces() {
        return project.getWorkspaces().stream().map(value -> value.getName()).toList();
    }

    private static Collection<GroupBoard> officialGroupBoards() {
        return officialBoardArtifacts(GroupBoard.class, GroupBoard.getGroupBoards());
    }

    private static Collection<SchemeBoard> officialSchemeBoards() {
        return officialBoardArtifacts(SchemeBoard.class, SchemeBoard.getSchemeBoards());
    }

    private static <T> Collection<T> officialBoardArtifacts(Class<T> type, Collection<? extends T> registry) {
        var result = new LinkedHashSet<T>(registry);
        collectBoardArtifacts(CartagoEnvironment.getInstance().getRootWSP(), type, result);
        return List.copyOf(result);
    }

    private static <T> void collectBoardArtifacts(WorkspaceDescriptor descriptor, Class<T> type, Set<T> result) {
        if (descriptor == null || descriptor.getWorkspace() == null) return;
        var workspace = descriptor.getWorkspace();
        for (String name : workspace.getArtifactList()) {
            var artifact = workspace.getArtifactDescriptor(name);
            if (artifact != null && type.isInstance(artifact.getArtifact()))
                result.add(type.cast(artifact.getArtifact()));
        }
        for (WorkspaceDescriptor child : workspace.getChildWSPs()) collectBoardArtifacts(child, type, result);
    }

    private void receiveRuntimeEvent(RuntimeEvent event) {
        if (!started) return;
        if (jasonSource != null && "jason".equals(event.subsystem())) jasonSource.observe(event);
        else enqueueEvent(event);
    }

    private Path resolveProjectFile() {
        Path candidate = configuredProjectFile;
        if (candidate == null && project.getProjectFile() != null) candidate = project.getProjectFile().toPath();
        if (candidate == null || !Files.isRegularFile(candidate.toAbsolutePath().normalize(), LinkOption.NOFOLLOW_LINKS))
            throw new IllegalStateException("BRIDGE_PROJECT_FILE_REQUIRED: configure jcmFile= or supply JaCaMo project metadata");
        return candidate.toAbsolutePath().normalize();
    }

    private void prepareOfficialProject(Path jcm) {
        Path root = jcm.getParent();
        project.setProjectFile(jcm.toFile());
        project.setDirectory(root.toString());
        project.getSourcePaths().setRoot(root.toString());
        project.getOrgPaths().setRoot(root.toString());
        project.getJavaSourcePaths().setRoot(root.toString());
        project.addSourcePath(root.toUri().toString());
        project.addSourcePath(root.resolve("src/agt").toUri().toString());
        project.addSourcePath(root.resolve("src/agt/inc").toUri().toString());
        project.addOrgSourcePath(root.toUri().toString());
        project.addOrgSourcePath(root.resolve("src/org").toUri().toString());
        project.addJavaSourcePath(root.resolve("src").toUri().toString());
    }

    private void enqueueEvent(RuntimeEvent event) {
        if (!events.offer(event)) {
            publicationGap.set(true);
            readiness.update("events", AdapterReadiness.FAILED,
                    "bounded event queue overflow; resync required");
        }
    }

    private void startServer(long generation) throws Exception {
        byte[] secret = readSecret(bridgeSecretFile);
        String projectKey = modelSnapshot.sources().getFirst().id().scope();
        DistributionFingerprint distribution = RuntimeDistributionFingerprint.compute();
        if (!distribution.distributionDigest().equals(distributionSha256))
            throw new IllegalArgumentException("BRIDGE_DISTRIBUTION_FINGERPRINT_MISMATCH: expected="
                    + distributionSha256 + ", actual=" + distribution.distributionDigest());
        java.util.function.Supplier<byte[]> handshake = () -> {
            RuntimeSnapshot snapshot = captureRuntime();
            return encode(MessageType.HANDSHAKE, projectKey, generation, distribution,
                    capabilities(snapshot), envelopeCompleteness(snapshot), Map.of("readOnly", !controlCapable(),"domainReadOnly",true));
        };
        java.util.function.Supplier<byte[]> model = () -> encode(MessageType.MODEL_SNAPSHOT, projectKey,
                generation, distribution, capabilities(latestRuntimeSnapshot), Completeness.COMPLETE,
                ContractPayloads.model(modelSnapshot));
        java.util.function.Supplier<byte[]> runtime = () -> {
            RuntimeSnapshot snapshot = captureRuntime();
            return encode(MessageType.RUNTIME_SNAPSHOT, projectKey, generation, distribution,
                    capabilities(snapshot), envelopeCompleteness(snapshot), ContractPayloads.runtime(snapshot));
        };
        java.util.function.Supplier<byte[]> gap = () -> {
            long watermark = transportWatermark.incrementAndGet();
            RuntimeEvent event = new RuntimeEvent("gap:" + sessionId + ":" + watermark, sessionId, generation,
                    modelSnapshot.modelRevision(), "transport", "bridge:transport", watermark, Instant.now(),
                    RuntimeEventKind.GAP, null, null, null, null, "", "", Map.of(), Map.of(),
                    new SourceWatermark("bridge:transport", watermark), Completeness.PARTIAL, List.of());
            return encode(MessageType.RUNTIME_EVENT, projectKey, generation, distribution,
                    capabilities(latestRuntimeSnapshot), Completeness.PARTIAL, ContractPayloads.event(event));
        };
        // Match the per-subscriber frame bound to the platform's already bounded event capacity. A busy case can
        // emit more than 1,024 official callbacks while an ACK-heavy consumer catches up; a smaller transport queue
        // manufactured a GAP even though the platform queue still retained the complete ordered stream.
        server = new LocalTcpBridgeServer(InetAddress.getLoopbackAddress(), bridgePort, secret, MAX_FRAME_BYTES,
                EVENT_CAPACITY, 64L * 1024 * 1024, handshake, model, runtime, gap,request->encode(MessageType.CONTROL_STATUS,
                        projectKey,generation,distribution,capabilities(latestRuntimeSnapshot),Completeness.COMPLETE,control(request).payload()));
        java.util.Arrays.fill(secret, (byte) 0);
        server.start();
        publishing = true;
        publisher = Thread.ofPlatform().daemon().name("jacamo-bridge-publisher").start(() -> {
            long nextObservation=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
            while (publishing) {
                try {
                    if (publicationGap.getAndSet(false)) server.publish("platform:gap", gap.get());
                    RuntimeEvent event = events.poll(500, TimeUnit.MILLISECONDS);
                    if (event != null) server.publish(event.sourceId() + ":" + event.sourceSequence(),
                            encode(MessageType.RUNTIME_EVENT, projectKey, generation, distribution,
                            capabilities(latestRuntimeSnapshot), event.completeness(), ContractPayloads.event(event)));
                    if(event!=null && boundaryTrigger(event)) {
                        // A real source callback closes this observation batch. Copy supported
                        // board state now, enqueue its deltas first, then the explicit boundary.
                        runtimeCoordinator.poll();
                        checkpointSource.boundary(event);
                        nextObservation=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
                    }
                    if(System.nanoTime()>=nextObservation) {
                        // Fallback drift observation only; this timer does not invent a checkpoint.
                        runtimeCoordinator.poll();
                        nextObservation=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
                    }
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (Exception failure) {
                    publicationGap.set(true);
                    readiness.update("transport", AdapterReadiness.FAILED,
                            "event publication failed; resync required");
                }
            }
        });
        readiness.update("transport", AdapterReadiness.READY, "loopback TCP port=" + server.port());
    }

    private static boolean boundaryTrigger(RuntimeEvent event) {
        return "jason".equals(event.subsystem()) && event.kind()==RuntimeEventKind.CHANGED
                || "cartago".equals(event.subsystem()) && (event.kind()==RuntimeEventKind.SUCCEEDED || event.kind()==RuntimeEventKind.FAILED
                    || event.projectionStatus()==ProjectionStatus.MATERIALIZED_FAITHFULLY);
    }

    private RuntimeSnapshot captureRuntime() {
        RuntimeSnapshot last = latestRuntimeSnapshot;
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(12);
        while (System.nanoTime() < deadline) {
            try {
                SnapshotCoordinator.Capture capture = runtimeCoordinator.capture(modelSnapshot.modelRevision(), 4);
                last = capture.snapshot();
                latestRuntimeSnapshot = last;
                boolean usable = !last.facts().isEmpty() && last.sourceCompleteness().values().stream()
                        .noneMatch(value -> value == Completeness.UNAVAILABLE);
                readiness.update("runtime", usable ? AdapterReadiness.READY : AdapterReadiness.PARTIAL,
                        "snapshot=" + last.snapshotId() + "; facts=" + last.facts().size()
                                + "; sourceCompleteness=" + last.sourceCompleteness());
                if (usable) return last;
            } catch (RuntimeException failure) {
                readiness.update("runtime", AdapterReadiness.PARTIAL,
                        failure.getClass().getSimpleName() + ": " + failure.getMessage());
            } catch (Exception failure) {
                readiness.update("runtime", AdapterReadiness.PARTIAL,
                        failure.getClass().getSimpleName() + ": " + failure.getMessage());
            }
            try { Thread.sleep(50); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); break; }
        }
        if (last != null) return last;
        RuntimeSnapshot unavailable = unavailableRuntimeSnapshot();
        latestRuntimeSnapshot = unavailable;
        return unavailable;
    }

    private RuntimeSnapshot unavailableRuntimeSnapshot() {
        Instant now = Instant.now();
        SourceWatermark watermark = jasonSource == null ? new SourceWatermark("jason", 0) : jasonSource.watermark();
        var id = new BridgeEntityId("bridge", "runtime", "unavailable", sessionId, "runtime", sessionId);
        var fact = new RuntimeFact(id, RuntimeFactKind.AGENT, Map.of("reason", "official Jason runner not ready"),
                List.of(), ProjectionStatus.UNAVAILABLE, Completeness.UNAVAILABLE, List.of());
        return new RuntimeSnapshot("runtime-unavailable:" + sessionId, modelSnapshot.modelRevision(), now, now,
                Map.of(watermark.sourceId(), watermark), Map.of(watermark.sourceId(), watermark), 1,
                List.of(fact), Map.of(watermark.sourceId(), Completeness.UNAVAILABLE),
                AdapterEvidence.digest((modelSnapshot.modelRevision() + ":unavailable").getBytes(StandardCharsets.UTF_8)));
    }

    private List<Capability> capabilities(RuntimeSnapshot snapshot) {
        boolean runtimeReady = snapshot != null && !snapshot.facts().isEmpty()
                && snapshot.sourceCompleteness().values().stream().noneMatch(value -> value == Completeness.UNAVAILABLE);
        return List.of(
                new Capability("official.model", CapabilityStatus.COMPLETE, List.of(), ""),
                new Capability("runtime.snapshot", runtimeReady ? CapabilityStatus.COMPLETE : CapabilityStatus.PARTIAL,
                        List.of(), runtimeReady ? "validated official runtime cut; evidence-only facts remain labelled"
                                : "runtime authorities are not ready or the cut is incomplete"),
                new Capability("runtime.events", CapabilityStatus.PARTIAL, List.of(),
                        "official hooks are bounded and classified; Moise board intermediate deltas remain evidence-only"),
                new Capability("runtime.checkpoint.boundary.v1",CapabilityStatus.COMPLETE,List.of(),"source callback plus supported board observation batch"),
                new Capability(org.jacamo.bridge.contract.RuntimeControlContract.CAPABILITY,controlCapable()?CapabilityStatus.COMPLETE:CapabilityStatus.UNAVAILABLE,
                        List.of(),org.jacamo.bridge.contract.RuntimeControlContract.LIMITATION));
    }
    private boolean controlCapable() {return BridgeRuntimeRegistry.controller().map(BridgeLocalExecutionControl::capable).orElse(false);}
    public org.jacamo.bridge.contract.RuntimeControlContract.Status control(org.jacamo.bridge.contract.RuntimeControlContract.Request request) {
        if(!request.sessionId().equals(sessionId) || request.generation()!=generation() || !request.modelRevision().equals(modelSnapshot.modelRevision()))
            throw new IllegalArgumentException("CONTROL_RUNTIME_IDENTITY_STALE");
        var controller=BridgeRuntimeRegistry.controller();
        if(controller.isPresent())return controller.get().request(request,sessionId,generation(),modelSnapshot.modelRevision());
        if(request.action()!=org.jacamo.bridge.contract.RuntimeControlContract.Action.STATUS)throw new IllegalStateException("CONTROL_EXECUTION_CAPABILITY_UNAVAILABLE");
        return new org.jacamo.bridge.contract.RuntimeControlContract.Status(org.jacamo.bridge.contract.RuntimeControlContract.VERSION,sessionId,generation(),modelSnapshot.modelRevision(),
                false,org.jacamo.bridge.contract.RuntimeControlContract.State.RUNNING,"",Set.of(),Set.of(),Set.of(),"CONTROL_EXECUTION_CAPABILITY_UNAVAILABLE",Instant.now());
    }

    private Completeness envelopeCompleteness(RuntimeSnapshot snapshot) {
        return snapshot != null && !snapshot.facts().isEmpty()
                && snapshot.sourceCompleteness().values().stream().allMatch(value -> value == Completeness.COMPLETE)
                ? Completeness.COMPLETE : Completeness.PARTIAL;
    }

    private byte[] encode(MessageType type, String projectKey, long generation,
                          DistributionFingerprint distribution, List<Capability> capabilities,
                          Completeness completeness, Map<String, Object> payload) {
        return ContractCodec.encode(ContractEnvelope.create("1.1.0", type, "jacamo-bridge-1.1.0",
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
        started = false;
        publishing = false;
        if (publisher != null) {
            publisher.interrupt();
            try { publisher.join(1000); }
            catch (InterruptedException error) { Thread.currentThread().interrupt(); failure = error; }
            publisher = null;
        }
        if (server != null) { server.close(); server = null; }
        if (registryHandle != null) {
            try { registryHandle.close(); }
            catch (Exception error) { failure = error; }
            registryHandle = null;
        }
        if (runtimeCoordinator != null) {
            try { runtimeCoordinator.close(); }
            catch (Exception error) { if (failure == null) failure = error; else failure.addSuppressed(error); }
            runtimeCoordinator = null;
        }
        BridgeRuntimeRegistry.clearAgents();
        if (sessionId != null && sessionId.equals(System.getProperty("jacamo.bridge.session"))) {
            System.clearProperty("jacamo.bridge.session");
            System.clearProperty("jacamo.bridge.generation");
            System.clearProperty("jacamo.bridge.modelRevision");
            System.clearProperty("jacamo.bridge.projectKey");
        }
        events.clear();
        latestRuntimeSnapshot = null;
        modelSnapshot = null;
        configuredProjectFile = null;
        configured = false;
        readiness.update("bridge", AdapterReadiness.STOPPED, "listeners, server and queues closed");
        if (failure != null) throw failure;
    }
}
