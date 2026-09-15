package org.tzi.use.plugins.jacamo.runtime;

import jason.architecture.AgArch;
import jason.asSemantics.ActionExec;

/** Jason custom architecture that observes action enter/exit without controlling execution. */
public final class JasonMonitorAgArch extends AgArch {
    private static final long serialVersionUID = 1L;
    private transient JasonRuntimeConnector connector;
    private String agentName;

    @Override public void init() {
        connector = JasonRuntimeConnectorRegistry.requireActive();
        agentName = connector.agentNameFor(getTS());
    }

    @Override public void act(ActionExec action) {
        requireInitialized();
        connector.actionStarted(agentName, action);
        super.act(action);
    }

    @Override public void actionExecuted(ActionExec action) {
        requireInitialized();
        connector.actionCompleted(agentName, action);
        super.actionExecuted(action);
    }

    private void requireInitialized() {
        if (connector == null) throw new IllegalStateException("JASON_MONITOR_NOT_INITIALIZED");
    }
}
