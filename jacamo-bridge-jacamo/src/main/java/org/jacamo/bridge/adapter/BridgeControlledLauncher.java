package org.jacamo.bridge.adapter;

import jason.JasonException;
import jason.infra.local.LocalAgArch;
import jacamo.infra.JaCaMoLauncher;

/** Plugin-side extension: JaCaMo's createController hook is disabled in 1.3.1; no upstream modification. */
public class BridgeControlledLauncher extends JaCaMoLauncher {
    @Override public void create() throws JasonException {
        super.create();
        if(getProject().isJade() || getControllerInfraTier()!=null || getProject().getControlClass()!=null
                || getAgs().values().stream().anyMatch(a->a.getClass()!=LocalAgArch.class))return;
        var execution=new BridgeLocalExecutionControl(this);
        control=execution;BridgeRuntimeRegistry.controller(execution);
        getAgs().values().forEach(execution::admit);
    }
    @Override public void addAg(LocalAgArch agent) {
        super.addAg(agent);
        if(control instanceof BridgeLocalExecutionControl execution)execution.admit(agent);
    }
    @Override public LocalAgArch delAg(String name) {
        var identity=BridgeRuntimeRegistry.agentIdentity(name);var removed=super.delAg(name);
        if(control instanceof BridgeLocalExecutionControl execution && removed!=null)identity.ifPresent(id->execution.departed(id.canonical()));
        return removed;
    }
}
