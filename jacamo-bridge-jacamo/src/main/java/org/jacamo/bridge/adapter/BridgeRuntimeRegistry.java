package org.jacamo.bridge.adapter;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.jacamo.bridge.contract.BridgeEntityId;
import org.jacamo.bridge.contract.RuntimeEvent;

/** Process-local hook used only by official JaCaMo extension instances. */
public final class BridgeRuntimeRegistry {
    private static final AtomicReference<Consumer<RuntimeEvent>> ACTIVE = new AtomicReference<>();
    private static final ConcurrentMap<String, BridgeEntityId> AGENT_IDENTITIES = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String,jason.infra.local.LocalAgArch> AGENT_ARCHITECTURES=new ConcurrentHashMap<>();
    private static volatile BridgeLocalExecutionControl controller;
    static void registerArchitecture(String name,jason.infra.local.LocalAgArch architecture) {AGENT_ARCHITECTURES.put(name,architecture);}
    static Optional<jason.infra.local.LocalAgArch> agentArchitecture(String name) {return Optional.ofNullable(AGENT_ARCHITECTURES.get(name));}
    static void controller(BridgeLocalExecutionControl value) {controller=value;}
    static Optional<BridgeLocalExecutionControl> controller() {return Optional.ofNullable(controller);}
    private static final ConcurrentMap<String, String> DECLARATIONS = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, String> AGENT_DECLARATION_NAMES = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, Integer> DECLARATION_INSTANCES = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String,java.util.Map<String,Object>> JASON_STATES=new ConcurrentHashMap<>();
    private static final ConcurrentMap<String,String> JASON_STATE_ERRORS=new ConcurrentHashMap<>();
    private static volatile org.jacamo.bridge.contract.semantic.JacamoSemanticSnapshot projectDeclarations;
    private BridgeRuntimeRegistry() { }
    public static AutoCloseable attach(Consumer<RuntimeEvent> observer) {
        Objects.requireNonNull(observer);
        if (!ACTIVE.compareAndSet(null, observer)) throw new IllegalStateException("BRIDGE_OBSERVER_ALREADY_ATTACHED");
        return () -> ACTIVE.compareAndSet(observer, null);
    }
    static Consumer<RuntimeEvent> require() {
        Consumer<RuntimeEvent> observer = ACTIVE.get();
        if (observer == null) throw new IllegalStateException("BRIDGE_OBSERVER_NOT_ATTACHED");
        return observer;
    }

    static void registerAgent(String agentName, BridgeEntityId identity) {
        AGENT_IDENTITIES.put(Objects.requireNonNull(agentName), Objects.requireNonNull(identity));
    }

    static void registerAgent(String agentName,BridgeEntityId identity,String declarationName) {
        registerAgent(agentName,identity);
        if(declarationName!=null && !declarationName.isBlank()) AGENT_DECLARATION_NAMES.put(agentName,declarationName);
    }

    static void unregisterAgent(String agentName, BridgeEntityId identity) {
        var control=controller;if(control!=null)control.departed(identity.canonical());
        AGENT_IDENTITIES.remove(agentName, identity);
        AGENT_ARCHITECTURES.remove(agentName);
        AGENT_DECLARATION_NAMES.remove(agentName);
        JASON_STATES.remove(agentName);
        JASON_STATE_ERRORS.remove(agentName);
    }

    static Optional<BridgeEntityId> agentIdentity(String agentName) {
        return Optional.ofNullable(AGENT_IDENTITIES.get(agentName));
    }

    static void configureDeclarations(org.jacamo.bridge.contract.semantic.JacamoSemanticSnapshot project) {
        projectDeclarations=project;
        DECLARATIONS.clear();
        DECLARATION_INSTANCES.clear();
        project.agentDeclarations().forEach(a -> { DECLARATIONS.put(a.name(),a.metadata().semanticId()); DECLARATION_INSTANCES.put(a.name(),a.instances()); });
    }
    static Optional<org.jacamo.bridge.contract.semantic.JacamoSemanticSnapshot> projectDeclarations() { return Optional.ofNullable(projectDeclarations); }
    static void publishJasonState(String name,java.util.Map<String,Object> values) {
        JASON_STATES.put(name,java.util.Map.copyOf(values)); JASON_STATE_ERRORS.remove(name);
    }
    static void invalidateJasonState(String name,String diagnostic) {
        JASON_STATES.remove(name); JASON_STATE_ERRORS.put(name,diagnostic);
    }
    static Optional<String> jasonStateError(String name) { return Optional.ofNullable(JASON_STATE_ERRORS.get(name)); }
    static Optional<java.util.Map<String,Object>> jasonState(String name) { return Optional.ofNullable(JASON_STATES.get(name)); }
    /** Only an agent registered by the actual BridgeAgArch may bind to this project's declaration. */
    static Optional<String> agentDeclarationId(String actualAgentName) {
        String declaration=AGENT_DECLARATION_NAMES.getOrDefault(actualAgentName,actualAgentName);
        return AGENT_IDENTITIES.containsKey(actualAgentName) ? Optional.ofNullable(DECLARATIONS.get(declaration)) : Optional.empty();
    }
    static Optional<String> projectedAgentId(String actualAgentName) {
        String name=AGENT_DECLARATION_NAMES.getOrDefault(actualAgentName,actualAgentName);
        return agentDeclarationId(actualAgentName).map(id -> DECLARATION_INSTANCES.getOrDefault(name,1)>1
            ? AGENT_IDENTITIES.get(actualAgentName).canonical() : id);
    }

    static void clearAgents() {
        AGENT_ARCHITECTURES.clear();controller=null;
        AGENT_IDENTITIES.clear();
        DECLARATIONS.clear();
        AGENT_DECLARATION_NAMES.clear(); DECLARATION_INSTANCES.clear();
        JASON_STATES.clear(); JASON_STATE_ERRORS.clear(); projectDeclarations=null;
    }
}
