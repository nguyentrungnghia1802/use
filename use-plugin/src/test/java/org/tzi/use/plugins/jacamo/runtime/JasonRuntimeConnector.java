package org.tzi.use.plugins.jacamo.runtime;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import jason.asSemantics.ActionExec;
import jason.asSemantics.CircumstanceListener;
import jason.asSemantics.Event;
import jason.asSemantics.GoalListener;
import jason.asSemantics.TransitionSystem;
import jason.asSyntax.Structure;
import jason.asSyntax.Trigger;
import org.tzi.use.plugins.jacamo.semantic.Dimension;

/** In-process Jason 3.3 connector based only on the official listener and agent architecture hooks. */
public final class JasonRuntimeConnector implements RuntimeConnector {
    private final String id;
    private final Map<String, TransitionSystem> agents = new LinkedHashMap<>();
    private final Map<String, String> semanticIds;
    private final Map<String, Hooks> hooks = new LinkedHashMap<>();
    private final Map<ActionExec, String> actionCorrelations = Collections.synchronizedMap(new IdentityHashMap<>());
    private final CopyOnWriteArrayList<Consumer<RuntimeEvent>> listeners = new CopyOnWriteArrayList<>();
    private final AtomicLong sequence = new AtomicLong();
    private final AtomicLong correlationSequence = new AtomicLong();
    private volatile ConnectorState state = ConnectorState.DISCONNECTED;

    public JasonRuntimeConnector(String id, Map<String, TransitionSystem> agents, Map<String, String> semanticIds) {
        if (id == null || id.isBlank() || agents == null || semanticIds == null
                || !semanticIds.keySet().containsAll(agents.keySet()))
            throw new IllegalArgumentException("JASON_CONNECTOR_INVALID");
        this.id = id;
        this.agents.putAll(agents);
        this.semanticIds = Map.copyOf(semanticIds);
    }

    @Override public String connectorId() { return id; }
    @Override public Set<ConnectorCapability> capabilities() {
        return Set.of(ConnectorCapability.FULL_SNAPSHOT, ConnectorCapability.EVENT_SUBSCRIPTION,
                ConnectorCapability.RECONNECT, ConnectorCapability.AGENT_STATE, ConnectorCapability.OPERATION_EVENTS);
    }
    @Override public ConnectorState state() { return state; }

    @Override public synchronized void connect(URI endpoint) {
        if (endpoint == null || !"jacamo".equalsIgnoreCase(endpoint.getScheme()))
            throw new IllegalArgumentException("JASON_ENDPOINT_INVALID");
        if (state == ConnectorState.CONNECTED) throw new IllegalStateException("CONNECTOR_ALREADY_CONNECTED");
        try {
            JasonRuntimeConnectorRegistry.activate(this);
            agents.forEach(this::installHooks);
            state = ConnectorState.CONNECTED;
        } catch (RuntimeException exception) {
            hooks.forEach((name, value) -> value.removeFrom(agents.get(name)));
            hooks.clear();
            JasonRuntimeConnectorRegistry.deactivateIfActive(this);
            state = ConnectorState.ERROR;
            throw exception;
        }
    }

    public synchronized void attachAgent(String agentName, TransitionSystem transitionSystem) {
        if (agentName == null || agentName.isBlank() || transitionSystem == null || !semanticIds.containsKey(agentName))
            throw new IllegalArgumentException("JASON_AGENT_BINDING_INVALID");
        TransitionSystem previousSystem = agents.get(agentName);
        Hooks previous = hooks.remove(agentName);
        if (previous != null) previous.removeFrom(previousSystem);
        agents.put(agentName, transitionSystem);
        if (state == ConnectorState.CONNECTED) installHooks(agentName, transitionSystem);
    }

    public synchronized String agentNameFor(TransitionSystem transitionSystem) {
        return agents.entrySet().stream().filter(entry -> entry.getValue() == transitionSystem)
                .map(Map.Entry::getKey).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("JASON_TRANSITION_SYSTEM_UNBOUND"));
    }

    @Override public synchronized RuntimeSnapshot fullSnapshot() {
        requireConnected();
        List<RuntimeEvent> snapshot = new ArrayList<>();
        agents.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            List<String> beliefs = new ArrayList<>();
            entry.getValue().getAg().getBB().forEach(literal -> beliefs.add(literal.toString()));
            beliefs.stream().sorted().forEach(belief -> snapshot.add(event(entry.getKey(), RuntimeEventKind.BELIEF_ADDED,
                    Map.of("belief", belief), null)));
        });
        String fingerprint = sha256(snapshot.stream().map(value -> value.runtimeSourceId() + "|"
                + value.payload().get("belief")).sorted().toList().toString());
        long last = snapshot.isEmpty() ? sequence.get() : snapshot.getLast().sequence();
        return new RuntimeSnapshot("jason-" + last, Instant.now(), last, snapshot, fingerprint);
    }

    @Override public RuntimeSubscription subscribe(Consumer<RuntimeEvent> listener) {
        requireConnected();
        if (listener == null) throw new IllegalArgumentException("RUNTIME_LISTENER_REQUIRED");
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    public void actionStarted(String agentName, ActionExec action) {
        requireAgent(agentName);
        Structure term = action.getActionTerm();
        String correlation = "jason-action-" + agentName + "-" + correlationSequence.incrementAndGet();
        actionCorrelations.put(action, correlation);
        emit(event(agentName, RuntimeEventKind.ACTION_STARTED,
                Map.of("action", term.getFunctor(), "arguments", arguments(term)), correlation));
    }

    public void actionCompleted(String agentName, ActionExec action) {
        requireAgent(agentName);
        String correlation = actionCorrelations.remove(action);
        if (correlation == null) throw new IllegalStateException("JASON_ACTION_CORRELATION_MISSING");
        RuntimeEventKind kind = action.getResult() ? RuntimeEventKind.ACTION_SUCCEEDED : RuntimeEventKind.ACTION_FAILED;
        Map<String, Object> payload = action.getResult() ? Map.of("action", action.getActionTerm().getFunctor())
                : Map.of("action", action.getActionTerm().getFunctor(), "error",
                action.getFailureMsg() == null ? "Jason action failed" : action.getFailureMsg());
        emit(event(agentName, kind, payload, correlation));
    }

    public void messageSent(String sender, String receiver, String performative, String content) {
        requireAgent(sender);
        emit(event(sender, RuntimeEventKind.MESSAGE_SENT,
                Map.of("receiver", receiver, "performative", performative, "content", content), null));
    }

    public void messageReceived(String receiver, String sender, String performative, String content) {
        requireAgent(receiver);
        emit(event(receiver, RuntimeEventKind.MESSAGE_RECEIVED,
                Map.of("sender", sender, "performative", performative, "content", content), null));
    }

    @Override public synchronized void disconnect() {
        hooks.forEach((name, value) -> value.removeFrom(agents.get(name)));
        hooks.clear();
        listeners.clear();
        actionCorrelations.clear();
        JasonRuntimeConnectorRegistry.deactivateIfActive(this);
        state = ConnectorState.DISCONNECTED;
    }

    private void installHooks(String agentName, TransitionSystem transitionSystem) {
        Hooks previous = hooks.remove(agentName);
        if (previous != null) previous.removeFrom(transitionSystem);
        CircumstanceListener circumstance = new CircumstanceListener() {
            @Override public void eventAdded(Event event) {
                if (state != ConnectorState.CONNECTED || agents.get(agentName) != transitionSystem) return;
                Trigger trigger = event.getTrigger();
                if (!trigger.isUpdate()) return;
                emit(JasonRuntimeConnector.this.event(agentName,
                        trigger.isAddition() ? RuntimeEventKind.BELIEF_ADDED : RuntimeEventKind.BELIEF_REMOVED,
                        Map.of("belief", trigger.getLiteral().toString()), null));
            }
        };
        GoalListener goals = new GoalListener() {
            @Override public void goalStarted(Event goal) {
                if (state != ConnectorState.CONNECTED || agents.get(agentName) != transitionSystem) return;
                emit(JasonRuntimeConnector.this.event(agentName, RuntimeEventKind.GOAL_ADOPTED,
                        Map.of("goal", goal.getTrigger().getLiteral().toString()), null));
            }
            @Override public void goalFinished(Trigger goal, GoalStates result) {
                if (state != ConnectorState.CONNECTED || agents.get(agentName) != transitionSystem) return;
                RuntimeEventKind kind = result == GoalStates.achieved
                        ? RuntimeEventKind.GOAL_ACHIEVED : RuntimeEventKind.GOAL_REMOVED;
                emit(JasonRuntimeConnector.this.event(agentName, kind,
                        Map.of("goal", goal.getLiteral().toString(), "state", String.valueOf(result)), null));
            }
            @Override public void goalFailed(Trigger goal, jason.asSyntax.Term reason) {
                if (state != ConnectorState.CONNECTED || agents.get(agentName) != transitionSystem) return;
                emit(JasonRuntimeConnector.this.event(agentName, RuntimeEventKind.GOAL_FAILED,
                        Map.of("goal", goal.getLiteral().toString(), "reason", String.valueOf(reason)), null));
            }
        };
        transitionSystem.getC().addEventListener(circumstance);
        transitionSystem.addGoalListener(goals);
        hooks.put(agentName, new Hooks(circumstance, goals));
    }

    private synchronized RuntimeEvent event(String agentName, RuntimeEventKind kind, Map<String, Object> payload,
                                            String correlation) {
        long next = sequence.incrementAndGet();
        return RuntimeEvent.create(id + "-" + next, Instant.now(), next, Dimension.AGENT, kind,
                "jason:agent:" + agentName, semanticIds.get(agentName), payload, correlation);
    }

    private List<String> arguments(Structure action) {
        List<String> result = new ArrayList<>();
        for (int i = 0; i < action.getArity(); i++) result.add(action.getTerm(i).toString());
        return List.copyOf(result);
    }

    private void emit(RuntimeEvent event) { listeners.forEach(listener -> listener.accept(event)); }
    private void requireConnected() {
        if (state != ConnectorState.CONNECTED) throw new IllegalStateException("CONNECTOR_NOT_CONNECTED");
    }
    private void requireAgent(String agentName) {
        requireConnected();
        if (!agents.containsKey(agentName)) throw new IllegalArgumentException("JASON_AGENT_UNKNOWN: " + agentName);
    }
    private String sha256(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(hash);
        } catch (Exception exception) { throw new IllegalStateException("SHA256_UNAVAILABLE", exception); }
    }

    private record Hooks(CircumstanceListener circumstance, GoalListener goals) {
        void removeFrom(TransitionSystem transitionSystem) {
            if (transitionSystem == null) return;
            transitionSystem.getC().removeEventListener(circumstance);
            transitionSystem.removeGoalListener(goals);
        }
    }
}
