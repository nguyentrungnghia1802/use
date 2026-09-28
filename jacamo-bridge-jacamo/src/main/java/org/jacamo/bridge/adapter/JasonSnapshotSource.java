package org.jacamo.bridge.adapter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import jason.architecture.AgArch;
import jason.asSemantics.Agent;
import jason.asSemantics.TransitionSystem;
import jason.infra.local.BaseLocalMAS;
import jason.infra.local.LocalAgArch;
import org.jacamo.bridge.contract.BridgeEntityId;
import org.jacamo.bridge.contract.Completeness;
import org.jacamo.bridge.contract.ProjectionStatus;
import org.jacamo.bridge.contract.RuntimeEvent;
import org.jacamo.bridge.contract.RuntimeFact;
import org.jacamo.bridge.contract.RuntimeFactKind;
import org.jacamo.bridge.contract.SourceWatermark;

/**
 * Read-only observation of the official Jason runner.  BridgeAgArch remains the
 * event authority; this source supplies the validated cut and gives those
 * events one aggregate source watermark for the cross-subsystem coordinator.
 */
public final class JasonSnapshotSource implements SnapshotSource {
    private final String sessionId;
    private final long generation;
    private final AtomicLong sequence = new AtomicLong();
    private volatile Consumer<RuntimeEvent> observer;
    private volatile Completeness completeness = Completeness.UNAVAILABLE;

    public JasonSnapshotSource(String sessionId) {
        this.sessionId = java.util.Objects.requireNonNull(sessionId);
        this.generation = Long.parseLong(System.getProperty("jacamo.bridge.generation", "0"));
    }

    @Override public String sourceId() { return "jason"; }

    @Override public void attach(Consumer<RuntimeEvent> observer) {
        if (this.observer != null) throw new IllegalStateException("JASON_ALREADY_ATTACHED");
        this.observer = java.util.Objects.requireNonNull(observer);
    }

    /** Normalizes an event emitted by an installed BridgeAgArch. */
    public synchronized void observe(RuntimeEvent original) {
        Consumer<RuntimeEvent> sink = observer;
        if (sink == null) return;
        long next = sequence.incrementAndGet();
        RuntimeEvent normalized = new RuntimeEvent(original.eventId(), original.sessionId(),
                original.generation(), original.modelRevision(), "jason", sourceId(), next,
                original.observedAt(), original.kind(), original.factKind(), original.projectionStatus(),
                original.entityId(), original.relationId(), original.correlationId(), original.causationId(),
                original.before(), original.after(), new SourceWatermark(sourceId(), next),
                original.completeness(), original.evidence());
        sink.accept(normalized);
    }

    @Override public SourceWatermark watermark() {
        return new SourceWatermark(sourceId(), sequence.get());
    }

    @Override public String topologyFingerprint() {
        BaseLocalMAS runner = BaseLocalMAS.getRunner();
        if (runner == null) {
            completeness = Completeness.UNAVAILABLE;
            return AdapterEvidence.digest("jason:runner-unavailable".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        List<String> agents = new ArrayList<>();
        runner.getAgs().values().stream().sorted(Comparator.comparing(LocalAgArch::getAgName))
                .forEach(local -> agents.add(local.getAgName() + ":" +
                        BridgeRuntimeRegistry.agentIdentity(local.getAgName()).map(BridgeEntityId::canonical).orElse("unobserved")));
        completeness = agents.stream().allMatch(value -> !value.endsWith(":unobserved"))
                ? Completeness.COMPLETE : Completeness.PARTIAL;
        return AdapterEvidence.digest(String.join("\n", agents).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    @Override public List<RuntimeFact> capture() {
        BaseLocalMAS runner = BaseLocalMAS.getRunner();
        if (runner == null) {
            completeness = Completeness.UNAVAILABLE;
            return List.of();
        }
        var facts = new ArrayList<RuntimeFact>();
        boolean allIdentities = true;
        for (LocalAgArch local : runner.getAgs().values().stream()
                .sorted(Comparator.comparing(LocalAgArch::getAgName)).toList()) {
            String name = local.getAgName();
            BridgeEntityId identity = BridgeRuntimeRegistry.agentIdentity(name).orElseGet(() ->
                    new BridgeEntityId("jason", "agent", "runtime-agent",
                            System.getProperty("jacamo.bridge.projectKey", "unnegotiated"), name,
                            "unobserved:" + sessionId));
            if (identity.incarnation().startsWith("unobserved:")) allIdentities = false;
            var values = new TreeMap<String, Object>();
            values.put("name", name);
            values.put("active", local.isRunning());
            values.put("cycles", local.getCycles());
            try {
                AgArch user = local.getUserAgArch();
                TransitionSystem ts = user == null ? null : user.getTS();
                Agent agent = ts == null ? null : ts.getAg();
                if (agent != null) {
                    values.put("beliefs", agent.getBB() == null ? List.of()
                            : java.util.stream.StreamSupport.stream(agent.getBB().spliterator(), false)
                                    .map(Object::toString).sorted().toList());
                    values.put("initialGoals", agent.getInitialGoals() == null ? List.of()
                            : agent.getInitialGoals().stream().map(Object::toString).sorted().toList());
                    values.put("plans", agent.getPL() == null ? "" : agent.getPL().toString());
                }
            } catch (RuntimeException observationFailure) {
                values.put("observationError", observationFailure.getClass().getSimpleName());
                allIdentities = false;
            }
            facts.add(new RuntimeFact(identity, RuntimeFactKind.AGENT, Map.copyOf(values), List.of(),
                    ProjectionStatus.EVIDENCE_ONLY, Completeness.COMPLETE, List.of()));
        }
        completeness = allIdentities ? Completeness.COMPLETE : Completeness.PARTIAL;
        facts.sort(Comparator.comparing(fact -> fact.id().canonical()));
        return facts;
    }

    @Override public Completeness completeness() { return completeness; }

    @Override public void close() { observer = null; }
}
