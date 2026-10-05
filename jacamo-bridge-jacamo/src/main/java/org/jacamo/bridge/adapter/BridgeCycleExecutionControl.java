package org.jacamo.bridge.adapter;

import jason.control.ExecutionControl;

/** Official Jason extension. Default timeout auto-advance is disabled; the ACK ledger owns admission. */
public final class BridgeCycleExecutionControl extends ExecutionControl {
    @Override public void init(String[] args) {super.init(args);setRunningCycle(false);}
    @Override public void receiveFinishedCycle(String name,boolean breakpoint,int cycle) {
        ((BridgeLocalExecutionControl)infraControl).finished(name,cycle);
    }
}
