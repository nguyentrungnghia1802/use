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
        requireStaticCohort();
        var chain=new ArrayList<String>(architectures==null || architectures.isEmpty()?
                father==null?getDefaultAgArchs():father.getTS().getAgArch().getAgArchClassesChain():architectures);
        if(!chain.contains(BridgeAgArch.class.getName()))chain.add(BridgeAgArch.class.getName());
        var effective=settings==null?new Settings():settings;
        if(runner.getControllerInfraTier() instanceof BridgeLocalExecutionControl)effective.setSync(true);
        return super.createAgent(name,source,agentClass,chain,beliefs,effective,father);
    }
    @Override public void startAgent(String name) {
        requireStaticCohort();
        super.startAgent(name);
        if(runner.getControllerInfraTier() instanceof BridgeLocalExecutionControl execution)execution.started(runner.getAg(name));
    }
    @Override public void clone(Agent agent,List<String> architectures,String name)throws jason.JasonException {requireStaticCohort();super.clone(agent,architectures,name);}
    @Override public boolean killAgent(String name,String by,int deadline){requireStaticCohort();return super.killAgent(name,by,deadline);}
    @Override public String loadASL(String agent,String code,String source,boolean replace){requireStaticCohort();return super.loadASL(agent,code,source,replace);}
    @Override public String runAsAgent(String agent,String code){requireStaticCohort();return super.runAsAgent(agent,code);}
    private static void requireStaticCohort(){if(jacamo.checkpoint.CheckpointBoot.cohortActive())throw new IllegalStateException("CHECKPOINT_DYNAMIC_AGENT_OR_EXTERNAL_REASONING_MUTATION_UNSUPPORTED");}
}
