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
    private transient Map<WeakIdentityKey,String> goalTokens;
    private transient java.lang.ref.ReferenceQueue<Object> retiredGoals;
    private long nextGoalToken;
    private transient Map<String,Object> previousState;
    private transient java.util.Set<String> authoredBeliefs;

    @Override public void init() {
        observer = BridgeRuntimeRegistry.require(); sequence = new AtomicLong();
        sessionId = System.getProperty("jacamo.bridge.session", "unnegotiated");
        generation = Long.parseLong(System.getProperty("jacamo.bridge.generation", "0"));
        incarnation = UUID.randomUUID().toString();
        correlations = java.util.Collections.synchronizedMap(new java.util.IdentityHashMap<>());
        goalTokens=new java.util.HashMap<>(); retiredGoals=new java.lang.ref.ReferenceQueue<>(); previousState=Map.of();
        runtimeIdentity = new BridgeEntityId("jason", "agent", "runtime-agent",
                System.getProperty("jacamo.bridge.projectKey", "unnegotiated"), getAgName(), incarnation);
        Object parameters=getTS()==null ? null : getTS().getSettings().getUserParameters().get(jason.runtime.Settings.PROJECT_PARAMETER);
        String declaration=parameters instanceof jacamo.project.JaCaMoAgentParameters source ? source.getAgName() : null;
        BridgeRuntimeRegistry.registerAgent(getAgName(), runtimeIdentity,declaration);
        for(AgArch arch=getFirstAgArch();arch!=null;arch=arch.getNextAgArch())
            if(arch instanceof jason.infra.local.LocalAgArch local) BridgeRuntimeRegistry.registerArchitecture(getAgName(),local);
        emit(RuntimeEventKind.CREATED, RuntimeFactKind.AGENT, Map.of(), Map.of("agent", getAgName()), "");
    }
    @Override public void reasoningCycleFinished() {
        super.reasoningCycleFinished();
        if(observer==null || getTS()==null) return;
        var declaration=BridgeRuntimeRegistry.agentDeclarationId(getAgName());
        if(declaration.isEmpty()) return;
        try {
            var values=new LinkedHashMap<String,Object>();
            values.put("normalizedEventKind","UPSERT_JASON_AGENT_STATE"); values.put("semanticId",runtimeIdentity.canonical());
            values.put("agentDeclarationSemanticId",declaration.get()); values.put("name",getAgName());
            var beliefs=new java.util.ArrayList<Map<String,Object>>();
            if(authoredBeliefs==null) authoredBeliefs = BridgeRuntimeRegistry.projectDeclarations().stream().flatMap(p -> p.jasonPrograms().stream())
                    .filter(p -> BridgeRuntimeRegistry.projectDeclarations().orElseThrow().agentDeclarations().stream()
                            .anyMatch(a -> a.metadata().semanticId().equals(declaration.get()) && a.name().equals(p.declarationId())))
                    .flatMap(p -> JasonBeliefEvidence.authoredPredicates(p).stream()).collect(java.util.stream.Collectors.toSet());
            if(getTS().getAg().getBB()!=null) for(var literal:getTS().getAg().getBB()) if(!(literal instanceof jason.asSyntax.Rule))
            {
                var observed = new LinkedHashMap<>(literal("runtime-belief","belief-literal:"+literal,literal.toString(),"BELIEF_BASE"));
                observed.putAll(JasonBeliefEvidence.provenance(literal, authoredBeliefs)); beliefs.add(Map.copyOf(observed));
            }
            beliefs.sort(java.util.Comparator.comparing(v->v.get("semanticId").toString())); values.put("beliefs",beliefs);
            var goals=new java.util.TreeMap<String,Map<String,Object>>(); var circumstance=getTS().getC();
            if(circumstance!=null) {
                var events=circumstance.getEventsPlusAtomic();
                while(events!=null && events.hasNext()) observeGoal(goals,events.next());
                if(circumstance.getPendingEvents()!=null) circumstance.getPendingEvents().values().forEach(e->observeGoal(goals,e));
                observeGoal(goals,circumstance.getSelectedEvent());
                var intentions=circumstance.getAllIntentions();
                while(intentions!=null && intentions.hasNext()) observeIntention(goals,intentions.next());
                observeIntention(goals,circumstance.getSelectedIntention());
            }
            values.put("goals",List.copyOf(goals.values()));
            values.put("goalCoverage","OBSERVED_CIRCUMSTANCE_EVENTS_AND_INTENTION_TRIGGERS");
            BridgeRuntimeRegistry.publishJasonState(getAgName(),values);
            if(!values.equals(previousState)) {
                emit(RuntimeEventKind.CHANGED,RuntimeFactKind.AGENT,previousState,values,""); previousState=Map.copyOf(values);
            }
        } catch(RuntimeException incompleteCut) {
            String diagnostic="JASON_CYCLE_OBSERVATION_INCOMPLETE:"+incompleteCut.getClass().getSimpleName();
            BridgeRuntimeRegistry.invalidateJasonState(getAgName(),diagnostic);
            previousState=Map.of();
            emit(RuntimeEventKind.CHANGED,RuntimeFactKind.AGENT,Map.of(),Map.of(
                "normalizedEventKind","JASON_CYCLE_OBSERVATION_INCOMPLETE","diagnostic",diagnostic),"");
        }
    }
    private Map<String,Object> literal(String kind,String source,String value,String origin) {
        String id=kind+":"+new String(org.jacamo.bridge.contract.CanonicalJson.encode(List.of(runtimeIdentity.canonical(),source)),java.nio.charset.StandardCharsets.UTF_8);
        return Map.of("semanticId",id,"sourceIdentity",source,"literal",value,"origin",origin);
    }
    private void observeGoal(Map<String,Map<String,Object>> goals,jason.asSemantics.Event event) {
        if(event==null) return; var trigger=event.getTrigger();
        if(trigger==null || !trigger.isAchvGoal() || !trigger.isAddition()) return;
        observeGoal(goals,trigger,trigger.getLiteral().toString(),"CIRCUMSTANCE_EVENT");
    }
    private void observeIntention(Map<String,Map<String,Object>> goals,Intention intention) {
        if(intention==null) return;
        for(var means:intention) {
            var trigger=means.getTrigger(); if(trigger==null || !trigger.isAchvGoal() || !trigger.isAddition()) continue;
            observeGoal(goals,trigger,trigger.getLiteral().capply(means.getUnif()).toString(),"INTENTION_TRIGGER");
        }
    }
    private void observeGoal(Map<String,Map<String,Object>> goals,Object source,String value,String origin) {
        java.lang.ref.Reference<?> retired;
        while((retired=retiredGoals.poll())!=null) goalTokens.remove(retired);
        String token=goalTokens.computeIfAbsent(new WeakIdentityKey(source,retiredGoals),
                k->"goal-observation:"+incarnation+":"+(++nextGoalToken));
        var item=literal("runtime-goal",token,value,origin); goals.put(item.get("semanticId").toString(),item);
    }
    /** Trigger equality is structural; occurrence identity must use the actual trigger object. */
    private static final class WeakIdentityKey extends java.lang.ref.WeakReference<Object> {
        private final int hash;
        WeakIdentityKey(Object value,java.lang.ref.ReferenceQueue<Object> queue) { super(value,queue); hash=System.identityHashCode(value); }
        @Override public int hashCode() { return hash; }
        @Override public boolean equals(Object other) {
            return this==other || other instanceof WeakIdentityKey key && get()!=null && get()==key.get();
        }
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
                "UPSERT_JASON_AGENT_STATE".equals(after.get("normalizedEventKind")) ? org.jacamo.bridge.contract.ProjectionStatus.MATERIALIZED_FAITHFULLY
                    : org.jacamo.bridge.contract.ProjectionStatus.EVIDENCE_ONLY, id, null, correlation, "", before, after,
                new SourceWatermark("jason:" + incarnation, current), "UPSERT_JASON_AGENT_STATE".equals(after.get("normalizedEventKind")) ? Completeness.COMPLETE : Completeness.PARTIAL, List.of()));
    }
    private void requireReady() { if (observer == null) throw new IllegalStateException("BRIDGE_AGARCH_NOT_INITIALIZED"); }
}
