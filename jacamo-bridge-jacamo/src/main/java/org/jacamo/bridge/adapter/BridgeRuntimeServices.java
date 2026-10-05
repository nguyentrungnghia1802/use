package org.jacamo.bridge.adapter;

import java.util.ArrayList;
import java.util.List;
import jason.asSemantics.Agent;
import jason.infra.local.BaseLocalMAS;
import jason.mas2j.ClassParameters;
import jason.runtime.Settings;
import jacamo.infra.JaCaMoRuntimeServices;

/** Dynamic agents enter the same official controlled architecture before their first cycle. */
public final class BridgeRuntimeServices extends JaCaMoRuntimeServices {
    private final BaseLocalMAS runner;
    public BridgeRuntimeServices(BaseLocalMAS runner) {super(runner);this.runner=runner;registerDefaultAgArch(BridgeAgArch.class.getName());}
    @Override public String createAgent(String name,String source,String agentClass,List<String> architectures,ClassParameters beliefs,Settings settings,Agent father) throws Exception {
        var chain=new ArrayList<String>(architectures==null || architectures.isEmpty()?
                father==null?getDefaultAgArchs():father.getTS().getAgArch().getAgArchClassesChain():architectures);
        if(!chain.contains(BridgeAgArch.class.getName()))chain.add(BridgeAgArch.class.getName());
        var effective=settings==null?new Settings():settings;
        if(runner.getControllerInfraTier() instanceof BridgeLocalExecutionControl)effective.setSync(true);
        return super.createAgent(name,source,agentClass,chain,beliefs,effective,father);
    }
    @Override public void startAgent(String name) {
        super.startAgent(name);
        if(runner.getControllerInfraTier() instanceof BridgeLocalExecutionControl execution)execution.started(runner.getAg(name));
    }
}
