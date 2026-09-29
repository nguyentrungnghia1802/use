package org.jacamo.bridge.adapter;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import jason.architecture.AgArch;
import jason.asSemantics.ActionExec;
import jason.asSemantics.Intention;
import org.jacamo.bridge.contract.BridgeEntityId;
import org.jacamo.bridge.contract.Completeness;
import org.jacamo.bridge.contract.RuntimeEvent;
import org.jacamo.bridge.contract.RuntimeEventKind;
import org.jacamo.bridge.contract.RuntimeFactKind;
import org.jacamo.bridge.contract.SourceWatermark;

/** Official Jason architecture hook; it observes and always delegates normal behavior. */
public final class BridgeAgArch extends AgArch {
    private static final long serialVersionUID = 1L;
    private transient Consumer<RuntimeEvent> observer;
    private transient AtomicLong sequence;
    private String sessionId;
    private String incarnation;
    private long generation;
    private transient BridgeEntityId runtimeIdentity;
    private transient Map<ActionExec,String> correlations;

    @Override public void init() {
        observer = BridgeRuntimeRegistry.require(); sequence = new AtomicLong();
        sessionId = System.getProperty("jacamo.bridge.session", "unnegotiated");
        generation = Long.parseLong(System.getProperty("jacamo.bridge.generation", "0"));
        incarnation = UUID.randomUUID().toString();
        correlations = java.util.Collections.synchronizedMap(new java.util.IdentityHashMap<>());
        runtimeIdentity = new BridgeEntityId("jason", "agent", "runtime-agent",
                System.getProperty("jacamo.bridge.projectKey", "unnegotiated"), getAgName(), incarnation);
        BridgeRuntimeRegistry.registerAgent(getAgName(), runtimeIdentity);
        emit(RuntimeEventKind.CREATED, RuntimeFactKind.AGENT, Map.of(), Map.of("agent", getAgName()), "");
    }

    @Override public void act(ActionExec action) {
        requireReady();
        String correlation = UUID.randomUUID().toString();
        correlations.put(action, correlation);
        emitAction(RuntimeEventKind.STARTED, action, correlation);
        super.act(action);
    }

    @Override public void actionExecuted(ActionExec action) {
        requireReady();
        String correlation = correlations.remove(action);
        if (correlation == null) correlation = "";
        emitAction(action.getResult() ? RuntimeEventKind.SUCCEEDED : RuntimeEventKind.FAILED, action, correlation);
        super.actionExecuted(action);
    }

    @Override public void stop() {
        if (observer != null) emit(RuntimeEventKind.DISPOSED, RuntimeFactKind.AGENT,
                Map.of("agent", getAgName()), Map.of(), "");
        if (runtimeIdentity != null) BridgeRuntimeRegistry.unregisterAgent(getAgName(), runtimeIdentity);
        runtimeIdentity = null;
        observer = null;if(correlations!=null)correlations.clear(); super.stop();
    }

    private void emitAction(RuntimeEventKind kind, ActionExec action, String correlation) {
        Map<String, Object> before = runtimeActionValues(action);
        Map<String, Object> after = new LinkedHashMap<>(before);
        after.put("result", action.getResult());
        if (action.getFailureReason() != null) after.put("failureReason", action.getFailureReason().toString());
        if (action.getFailureMsg() != null && !action.getFailureMsg().isBlank())
            after.put("failureMessage", action.getFailureMsg());
        after.put("normalizedEventKind", "ACTION_EXECUTION_" + kind.name());
        emit(kind, RuntimeFactKind.ACTION_EXECUTION, before, after, correlation);
    }

    private Map<String, Object> runtimeActionValues(ActionExec action) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("runtimeConcept", "ActionExec");
        values.put("sourceLayer", "RUNTIME");
        values.put("action", action.getActionTerm().toString());
        Intention intention = action.getIntention();
        if (intention != null) {
            values.put("intentionId", intention.getId());
            if (intention.getStateBasedOnPlace() != null)
                values.put("intentionState", intention.getStateBasedOnPlace().name());
        }
        return values;
    }

    private void emit(RuntimeEventKind kind, RuntimeFactKind factKind,
                       Map<String,Object> before, Map<String,Object> after, String correlation) {
        long current = sequence.incrementAndGet();
        String projectKey = System.getProperty("jacamo.bridge.projectKey", "unnegotiated");
        var id = runtimeIdentity == null
                ? new BridgeEntityId("jason", "agent", "runtime-agent", projectKey, getAgName(), incarnation)
                : runtimeIdentity;
        observer.accept(new RuntimeEvent(sessionId + ":jason:" + incarnation + ":" + current, sessionId, generation,
                System.getProperty("jacamo.bridge.modelRevision", "unnegotiated"), "jason", "jason:" + incarnation,
                current, Instant.now(), kind, factKind,
                org.jacamo.bridge.contract.ProjectionStatus.EVIDENCE_ONLY, id, null, correlation, "", before, after,
                new SourceWatermark("jason:" + incarnation, current), Completeness.PARTIAL, List.of()));
    }
    private void requireReady() { if (observer == null) throw new IllegalStateException("BRIDGE_AGARCH_NOT_INITIALIZED"); }
}
