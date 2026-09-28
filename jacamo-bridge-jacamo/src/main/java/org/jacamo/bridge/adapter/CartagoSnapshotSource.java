package org.jacamo.bridge.adapter;

import cartago.AgentId;
import cartago.ArtifactId;
import cartago.ArtifactObsProperty;
import cartago.CartagoEnvironment;
import cartago.ICartagoController;
import cartago.ICartagoLogger;
import cartago.IEventFilter;
import cartago.Op;
import cartago.OpId;
import cartago.Tuple;
import cartago.WorkspaceDescriptor;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import org.jacamo.bridge.contract.BridgeEntityId;
import org.jacamo.bridge.contract.Completeness;
import org.jacamo.bridge.contract.ProjectionStatus;
import org.jacamo.bridge.contract.RuntimeEvent;
import org.jacamo.bridge.contract.RuntimeEventKind;
import org.jacamo.bridge.contract.RuntimeFact;
import org.jacamo.bridge.contract.RuntimeFactKind;
import org.jacamo.bridge.contract.SourceWatermark;

/** Read-only official CArtAgO controller/logger adapter with UUID identity revalidation. */
public final class CartagoSnapshotSource implements SnapshotSource, ICartagoLogger {
    private final CartagoEnvironment environment;
    private final Set<String> configuredWorkspaces;
    private final Set<String> registeredWorkspaces = new LinkedHashSet<>();
    private final String sessionId;
    private final long generation;
    private final AtomicLong sequence = new AtomicLong();
    private volatile Consumer<RuntimeEvent> observer;
    private volatile Completeness completeness = Completeness.UNAVAILABLE;

    public CartagoSnapshotSource(CartagoEnvironment environment, List<String> workspaces, String sessionId) {
        this.environment = java.util.Objects.requireNonNull(environment);
        this.configuredWorkspaces = new LinkedHashSet<>(workspaces == null ? List.of() : workspaces);
        this.sessionId = java.util.Objects.requireNonNull(sessionId);
        this.generation = Long.parseLong(System.getProperty("jacamo.bridge.generation", "0"));
    }

    @Override public String sourceId() { return "cartago"; }

    @Override public synchronized void attach(Consumer<RuntimeEvent> observer) throws Exception {
        if (this.observer != null) throw new IllegalStateException("CARTAGO_ALREADY_ATTACHED");
        this.observer = java.util.Objects.requireNonNull(observer);
        refreshRegistrations();
    }

    @Override public SourceWatermark watermark() { return new SourceWatermark(sourceId(), sequence.get()); }

    @Override public synchronized String topologyFingerprint() {
        refreshRegistrations();
        var ids = new TreeSet<String>();
        boolean complete = true;
        for (String workspace : registeredWorkspaces) {
            try {
                ICartagoController controller = environment.getController(workspace);
                for (AgentId id : controller.getCurrentAgents()) ids.add("a:" + id.getGlobalId());
                for (ArtifactId id : controller.getCurrentArtifacts()) ids.add("r:" + id.getId());
            } catch (Exception error) { complete = false; }
        }
        completeness = registeredWorkspaces.isEmpty() ? Completeness.UNAVAILABLE
                : complete ? Completeness.COMPLETE : Completeness.PARTIAL;
        return AdapterEvidence.digest(String.join("\n", ids).getBytes(StandardCharsets.UTF_8));
    }

    @Override public synchronized List<RuntimeFact> capture() {
        refreshRegistrations();
        var facts = new ArrayList<RuntimeFact>();
        boolean complete = !registeredWorkspaces.isEmpty();
        for (String workspace : List.copyOf(registeredWorkspaces)) {
            try {
                ICartagoController controller = environment.getController(workspace);
                for (AgentId agent : controller.getCurrentAgents()) {
                    facts.add(new RuntimeFact(agentId(agent), RuntimeFactKind.AGENT,
                            Map.of("name", safe(agent.getAgentName()), "role", safe(agent.getAgentRole())), List.of(),
                            ProjectionStatus.EVIDENCE_ONLY, Completeness.COMPLETE, List.of()));
                }
                for (ArtifactId artifact : controller.getCurrentArtifacts()) {
                    var info = controller.getArtifactInfo(artifact.getName());
                    if (info == null || !artifact.equals(info.getId()))
                        throw new IllegalStateException("CARTAGO_ARTIFACT_NAME_RACE: " + artifact.getName());
                    var aid = artifactId(artifact);
                    facts.add(new RuntimeFact(aid, RuntimeFactKind.ARTIFACT,
                            Map.of("name", safe(artifact.getName()), "type", safe(artifact.getArtifactType()),
                                    "workspace", artifact.getWorkspaceId().getFullName()), List.of(),
                            ProjectionStatus.EVIDENCE_ONLY, Completeness.COMPLETE, List.of()));
                    info.getOperations().stream().sorted(Comparator.comparing(op -> op.getKeyId())).forEach(op ->
                            facts.add(new RuntimeFact(new BridgeEntityId("cartago", "environment", "operation-descriptor",
                                    aid.canonical(), op.getKeyId(), artifact.getId().toString()), RuntimeFactKind.OPERATION,
                                    Map.of("signature", op.getKeyId(), "dynamic", op.isDynamic(), "link", op.isLinkOperation()),
                                    List.of(), ProjectionStatus.EVIDENCE_ONLY, Completeness.COMPLETE, List.of())));
                    for (ArtifactObsProperty property : info.getObsProperties()) {
                        facts.add(new RuntimeFact(new BridgeEntityId("cartago", "environment", "observable-property",
                                aid.canonical(), property.getFullId(), artifact.getId().toString()), RuntimeFactKind.PROPERTY,
                                Map.of("name", safe(property.getName()), "values",
                                        Arrays.stream(property.getValues()).map(String::valueOf).toList()), List.of(),
                                ProjectionStatus.EVIDENCE_ONLY, Completeness.COMPLETE, List.of()));
                    }
                }
            } catch (Exception error) { complete = false; }
        }
        completeness = registeredWorkspaces.isEmpty() ? Completeness.UNAVAILABLE
                : complete ? Completeness.COMPLETE : Completeness.PARTIAL;
        facts.sort(Comparator.comparing(fact -> fact.id().canonical()));
        return facts;
    }

    @Override public Completeness completeness() { return completeness; }

    @Override public synchronized void close() throws Exception {
        if (observer == null) return;
        Exception failure = null;
        for (String workspace : List.copyOf(registeredWorkspaces)) {
            try { environment.unregisterLogger(workspace, this); }
            catch (Exception error) { if (failure == null) failure = error; else failure.addSuppressed(error); }
        }
        registeredWorkspaces.clear();
        observer = null;
        if (failure != null) throw failure;
    }

    private synchronized void refreshRegistrations() {
        var discovered = new LinkedHashSet<>(configuredWorkspaces);
        try { collect(environment.getRootWSP(), discovered); }
        catch (RuntimeException ignored) { /* readiness remains explicit in completeness */ }
        for (String workspace : discovered) {
            if (workspace == null || workspace.isBlank() || registeredWorkspaces.contains(workspace)) continue;
            try {
                environment.registerLogger(workspace, this);
                registeredWorkspaces.add(workspace);
            } catch (Exception ignored) { /* built-in Cartago may not have created it yet */ }
        }
    }

    private void collect(WorkspaceDescriptor descriptor, Set<String> names) {
        if (descriptor == null) return;
        if (descriptor.getId() != null) names.add(descriptor.getId().getFullName());
        if (descriptor.getWorkspace() != null)
            for (WorkspaceDescriptor child : descriptor.getWorkspace().getChildWSPs()) collect(child, names);
    }

    private String safe(Object value) { return value == null ? "" : value.toString(); }
    private BridgeEntityId artifactId(ArtifactId id) { return new BridgeEntityId("cartago", "environment", "artifact",
            id.getWorkspaceId().getFullName(), id.getName(), id.getId().toString()); }
    private BridgeEntityId agentId(AgentId id) { return new BridgeEntityId("cartago", "environment", "agent",
            id.getWorkspaceId().getFullName(), id.getGlobalId(), Integer.toString(id.getLocalId())); }

    private synchronized void event(RuntimeEventKind kind, ArtifactId artifact, AgentId agent, Map<String,Object> values) {
        Consumer<RuntimeEvent> sink = observer;
        if (sink == null) return;
        BridgeEntityId id = artifact != null ? artifactId(artifact) : agentId(agent);
        long next = sequence.incrementAndGet();
        String correlation = values.get("opId") instanceof String value ? value : "";
        sink.accept(new RuntimeEvent(sessionId + ":cartago:" + next, sessionId, generation,
                System.getProperty("jacamo.bridge.modelRevision", "unnegotiated"), "cartago", sourceId(), next,
                Instant.now(), kind, artifact != null ? RuntimeFactKind.ARTIFACT : RuntimeFactKind.AGENT,
                ProjectionStatus.EVIDENCE_ONLY, id, null, correlation, "", Map.of(), values,
                new SourceWatermark(sourceId(), next), Completeness.COMPLETE, List.of()));
    }

    @Override public void opRequested(long time, AgentId agent, ArtifactId artifact, Op op) { event(RuntimeEventKind.STARTED, artifact, agent,
            Map.of("phase", "requested", "operation", op.getName(), "arity", op.getParamValues().length)); }
    @Override public void opStarted(long time, OpId id, ArtifactId artifact, Op op) { event(RuntimeEventKind.STARTED, artifact, null,
            Map.of("phase", "started", "operation", op.getName(), "opId", id.toString())); }
    @Override public void opSuspended(long time, OpId id, ArtifactId artifact, Op op) { event(RuntimeEventKind.CHANGED, artifact, null,
            Map.of("phase", "suspended", "opId", id.toString())); }
    @Override public void opResumed(long time, OpId id, ArtifactId artifact, Op op) { event(RuntimeEventKind.CHANGED, artifact, null,
            Map.of("phase", "resumed", "opId", id.toString())); }
    @Override public void opCompleted(long time, OpId id, ArtifactId artifact, Op op) { event(RuntimeEventKind.SUCCEEDED, artifact, null,
            Map.of("operation", op.getName(), "opId", id.toString())); }
    @Override public void opFailed(long time, OpId id, ArtifactId artifact, Op op, String message, Tuple descriptor) { event(RuntimeEventKind.FAILED,
            artifact, null, Map.of("operation", op.getName(), "opId", id.toString(), "message", safe(message), "descriptor", safe(descriptor))); }
    @Override public void newPercept(long time, ArtifactId artifact, Tuple signal, ArtifactObsProperty[] added,
                                     ArtifactObsProperty[] removed, ArtifactObsProperty[] changed) { event(RuntimeEventKind.CHANGED, artifact, null,
            Map.of("signal", safe(signal), "added", Arrays.toString(added), "removed", Arrays.toString(removed), "changed", Arrays.toString(changed))); }
    @Override public void artifactCreated(long time, ArtifactId artifact, AgentId creator) { event(RuntimeEventKind.CREATED, artifact, creator, Map.of()); }
    @Override public void artifactDisposed(long time, ArtifactId artifact, AgentId disposer) { event(RuntimeEventKind.DISPOSED, artifact, disposer, Map.of()); }
    @Override public void artifactFocussed(long time, AgentId agent, ArtifactId artifact, IEventFilter filter) { event(RuntimeEventKind.FOCUSED,
            artifact, agent, Map.of("agent", agent.getGlobalId())); }
    @Override public void artifactNoMoreFocussed(long time, AgentId agent, ArtifactId artifact) { event(RuntimeEventKind.UNFOCUSED,
            artifact, agent, Map.of("agent", agent.getGlobalId())); }
    @Override public void artifactsLinked(long time, AgentId agent, ArtifactId source, ArtifactId target) { event(RuntimeEventKind.CHANGED,
            source, agent, Map.of("linkedArtifact", target.getId().toString())); }
    @Override public void agentJoined(long time, AgentId agent) { event(RuntimeEventKind.JOINED, null, agent, Map.of()); }
    @Override public void agentQuit(long time, AgentId agent) { event(RuntimeEventKind.QUIT, null, agent, Map.of()); }
}
