package org.jacamo.bridge.adapter;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import jason.architecture.AgArch;
import jason.asSemantics.ActionExec;
import org.jacamo.bridge.contract.BridgeEntityId;
import org.jacamo.bridge.contract.Completeness;
import org.jacamo.bridge.contract.RuntimeEvent;
import org.jacamo.bridge.contract.RuntimeEventKind;
import org.jacamo.bridge.contract.SourceWatermark;

/** Official Jason architecture hook; it observes and always delegates normal behavior. */
public final class BridgeAgArch extends AgArch {
    private static final long serialVersionUID = 1L;
    private transient Consumer<RuntimeEvent> observer;
    private transient AtomicLong sequence;
    private String sessionId;
    private String incarnation;
    private long generation;
    private transient Map<ActionExec,String> correlations;

    @Override public void init() {
        observer = BridgeRuntimeRegistry.require(); sequence = new AtomicLong();
        sessionId = System.getProperty("jacamo.bridge.session", "unnegotiated");
        generation = Long.parseLong(System.getProperty("jacamo.bridge.generation", "0"));
        incarnation = UUID.randomUUID().toString();
        correlations = java.util.Collections.synchronizedMap(new java.util.IdentityHashMap<>());
        emit(RuntimeEventKind.CREATED, Map.of(), Map.of("agent", getAgName()), "");
    }

    @Override public void act(ActionExec action) {
        requireReady(); String correlation=UUID.randomUUID().toString();correlations.put(action,correlation);emit(RuntimeEventKind.STARTED, Map.of(), Map.of("action", action.getActionTerm().toString()),correlation);
        super.act(action);
    }

    @Override public void actionExecuted(ActionExec action) {
        requireReady(); String correlation=correlations.remove(action);if(correlation==null)correlation="";emit(action.getResult() ? RuntimeEventKind.SUCCEEDED : RuntimeEventKind.FAILED,
                Map.of("action", action.getActionTerm().toString()), Map.of("result", action.getResult()),correlation);
        super.actionExecuted(action);
    }

    @Override public void stop() {
        if (observer != null) emit(RuntimeEventKind.DISPOSED, Map.of("agent", getAgName()), Map.of(),"");
        observer = null;if(correlations!=null)correlations.clear(); super.stop();
    }

    private void emit(RuntimeEventKind kind, Map<String,Object> before, Map<String,Object> after,String correlation) {
        long current = sequence.incrementAndGet();
        String projectKey = System.getProperty("jacamo.bridge.projectKey", "unnegotiated");
        var id = new BridgeEntityId("jason", "agent", "runtime-agent", projectKey, getAgName(), incarnation);
        observer.accept(new RuntimeEvent(sessionId + ":jason:" + incarnation + ":" + current, sessionId, generation,
                System.getProperty("jacamo.bridge.modelRevision", "unnegotiated"), "jason", "jason:" + incarnation,
                current, Instant.now(), kind, org.jacamo.bridge.contract.RuntimeFactKind.AGENT,
                org.jacamo.bridge.contract.ProjectionStatus.EVIDENCE_ONLY, id, null, correlation, "", before, after,
                new SourceWatermark("jason:" + incarnation, current), Completeness.PARTIAL, List.of()));
    }
    private void requireReady() { if (observer == null) throw new IllegalStateException("BRIDGE_AGARCH_NOT_INITIALIZED"); }
}
