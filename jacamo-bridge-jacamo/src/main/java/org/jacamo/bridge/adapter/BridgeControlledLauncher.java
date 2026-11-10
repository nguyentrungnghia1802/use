package org.jacamo.bridge.adapter;

import jason.JasonException;
import jason.infra.local.LocalAgArch;
import jacamo.infra.JaCaMoLauncher;

/** Plugin-side extension: JaCaMo's createController hook is disabled in 1.3.1; no upstream modification. */
public class BridgeControlledLauncher extends JaCaMoLauncher {
    @Override public void create() throws JasonException {
        super.create();
        if (!System.getProperty(org.jacamo.bridge.contract.ObservationRequestPlan.PROPERTY, "").isBlank()) {
            // Attach the observer before declarative platform artifacts are born.
            // Preserve relative ordering of every domain platform; no agent runs here.
            var observers=getPlatforms().stream().filter(JaCaMoBridgePlatform.class::isInstance).toList();
            if(observers.size()!=1)throw new IllegalStateException("OBSERVATION_BRIDGE_OWNER_NOT_UNIQUE");
            var observer=observers.getFirst();getPlatforms().remove(observer);getPlatforms().addFirst(observer);
        }
        if(getProject().isJade() || getControllerInfraTier()!=null || getProject().getControlClass()!=null
                || getAgs().values().stream().anyMatch(a->a.getClass()!=LocalAgArch.class)) {
            if(jacamo.checkpoint.CheckpointBoot.verified())throw new IllegalStateException("CHECKPOINT_AGENT_SCHEDULER_UNSUPPORTED");
            return;
        }
        var execution=new BridgeLocalExecutionControl(this);
        control=execution;BridgeRuntimeRegistry.controller(execution);
        getAgs().values().forEach(execution::admit);
    }
    @Override protected void afterPlatformStart(jacamo.platform.Platform platform) {
        if(platform instanceof JaCaMoBridgePlatform
                && !System.getProperty(org.jacamo.bridge.contract.ObservationRequestPlan.PROPERTY, "").isBlank())beforeDomainPlatformsStart();
    }
    /** No platform/source/scheduler lock is held while the consumer imports. */
    protected void beforeDomainPlatformsStart() { }
    @Override public void addAg(LocalAgArch agent) {
        super.addAg(agent);
        admitAddedAgent(agent);
    }
    protected void admitAddedAgent(LocalAgArch agent) {
        if(control instanceof BridgeLocalExecutionControl execution)execution.admit(agent);
    }
    @Override public LocalAgArch delAg(String name) {
        var identity=BridgeRuntimeRegistry.agentIdentity(name);var removed=super.delAg(name);
        if(control instanceof BridgeLocalExecutionControl execution && removed!=null)identity.ifPresent(id->execution.departed(id.canonical()));
        return removed;
    }
}
