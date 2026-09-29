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
import jason.asSemantics.Circumstance;
import jason.asSemantics.Event;
import jason.asSemantics.Intention;
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
            TransitionSystem transitionSystem = null;
            try {
                AgArch user = local.getUserAgArch();
                transitionSystem = user == null ? null : user.getTS();
                Agent agent = transitionSystem == null ? null : transitionSystem.getAg();
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
            if (transitionSystem != null)
                addRuntimeEvidence(facts, name, transitionSystem, local.getCycles());
        }
        completeness = allIdentities ? Completeness.COMPLETE : Completeness.PARTIAL;
        facts.sort(Comparator.comparing(fact -> fact.id().canonical()));
        return facts;
    }

    @Override public Completeness completeness() { return completeness; }

    @Override public void close() { observer = null; }

    private void addRuntimeEvidence(List<RuntimeFact> facts, String agentName,
                                    TransitionSystem transitionSystem, long cycles) {
        Circumstance circumstance = transitionSystem.getC();
        String projectKey = System.getProperty("jacamo.bridge.projectKey", "unnegotiated");
        String runtimeScope = sessionId + ":" + agentName;
        Map<String, Object> controller = new TreeMap<>();
        controller.put("runtimeConcept", "TransitionSystem");
        controller.put("sourceLayer", "RUNTIME");
        controller.put("normalizedEventKind", "TRANSITION_SYSTEM");
        controller.put("agent", agentName);
        controller.put("cycles", cycles);
        controller.put("runningIntentions", circumstance == null ? 0 : circumstance.getNbRunningIntentions());
        controller.put("allIntentions", circumstance == null ? 0 : count(circumstance.getAllIntentions()));
        controller.put("events", circumstance == null || circumstance.getEvents() == null
                ? 0 : circumstance.getEvents().size());
        controller.put("pendingEvents", circumstance == null || circumstance.getPendingEvents() == null
                ? 0 : circumstance.getPendingEvents().size());
        controller.put("pendingIntentions", circumstance == null || circumstance.getPendingIntentions() == null
                ? 0 : circumstance.getPendingIntentions().size());
        controller.put("pendingActions", circumstance == null || circumstance.getPendingActions() == null
                ? 0 : circumstance.getPendingActions().size());
        if (circumstance != null) {
            put(controller, "selectedEvent", circumstance.getSelectedEvent());
            put(controller, "selectedIntention", circumstance.getSelectedIntention());
            put(controller, "action", circumstance.getAction());
        }
        controller.put("stateEvidence", transitionSystem.toString());
        facts.add(new RuntimeFact(new BridgeEntityId("jason", "agent", "transition-system",
                        projectKey, agentName, runtimeScope), RuntimeFactKind.TRANSITION_SYSTEM,
                controller, List.of(), ProjectionStatus.EVIDENCE_ONLY, Completeness.COMPLETE, List.of()));

        if (circumstance == null) return;
        int intentionOrdinal = 0;
        for (var iterator = circumstance.getAllIntentions(); iterator != null && iterator.hasNext();) {
            Intention intention = iterator.next();
            Map<String, Object> values = new TreeMap<>();
            values.put("runtimeConcept", "Intention");
            values.put("sourceLayer", "RUNTIME");
            values.put("normalizedEventKind", "INTENTION");
            values.put("agent", agentName);
            values.put("intentionId", intention.getId());
            values.put("state", intention.getStateBasedOnPlace() == null
                    ? "UNKNOWN" : intention.getStateBasedOnPlace().name());
            values.put("place", intention.getPlace() == null ? "UNKNOWN" : intention.getPlace().name());
            values.put("term", intention.getAsTerm().toString());
            facts.add(new RuntimeFact(new BridgeEntityId("jason", "agent", "intention",
                            projectKey, agentName, runtimeScope + ":" + intentionOrdinal++),
                    RuntimeFactKind.INTENTION, values, List.of(), ProjectionStatus.EVIDENCE_ONLY,
                    Completeness.COMPLETE, List.of()));
        }

        int eventOrdinal = 0;
        if (circumstance.getEvents() != null)
            for (Event event : circumstance.getEvents())
                addEventEvidence(facts, projectKey, agentName, runtimeScope, "EVENT_QUEUE", event,
                        eventOrdinal++);
        if (circumstance.getPendingEvents() != null)
            for (Map.Entry<String, Event> entry : circumstance.getPendingEvents().entrySet())
                addEventEvidence(facts, projectKey, agentName, runtimeScope,
                        "PENDING_EVENT:" + entry.getKey(), entry.getValue(), eventOrdinal++);
        if (circumstance.getSelectedEvent() != null)
            addEventEvidence(facts, projectKey, agentName, runtimeScope, "SELECTED_EVENT",
                    circumstance.getSelectedEvent(), eventOrdinal);
    }

    private void addEventEvidence(List<RuntimeFact> facts, String projectKey, String agentName,
                                  String runtimeScope, String queue, Event event, int ordinal) {
        Map<String, Object> values = new TreeMap<>();
        values.put("runtimeConcept", "Event");
        values.put("sourceLayer", "RUNTIME");
        values.put("normalizedEventKind", "RUNTIME_EVENT");
        values.put("agent", agentName);
        values.put("queue", queue);
        values.put("event", event.toString());
        if (event.getTrigger() != null) values.put("trigger", event.getTrigger().toString());
        if (event.getIntention() != null) values.put("intentionId", event.getIntention().getId());
        facts.add(new RuntimeFact(new BridgeEntityId("jason", "agent", "runtime-event",
                        projectKey, agentName, runtimeScope + ":" + ordinal),
                RuntimeFactKind.RUNTIME_EVENT, values, List.of(), ProjectionStatus.EVIDENCE_ONLY,
                Completeness.COMPLETE, List.of()));
    }

    private static int count(java.util.Iterator<?> iterator) {
        int count = 0;
        while (iterator != null && iterator.hasNext()) {
            iterator.next();
            count++;
        }
        return count;
    }

    private static void put(Map<String, Object> values, String key, Object value) {
        if (value != null) values.put(key, value.toString());
    }
}
