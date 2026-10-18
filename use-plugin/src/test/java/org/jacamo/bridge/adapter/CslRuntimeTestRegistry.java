package org.jacamo.bridge.adapter;

import org.jacamo.bridge.contract.BridgeEntityId;
import org.jacamo.bridge.contract.semantic.JacamoSemanticSnapshot;

/** Adapter-boundary fixture registration. It does not claim a complete running Jason MAS. */
public final class CslRuntimeTestRegistry {
    public static void quitCartagoActor(cartago.CartagoEnvironment env,String name)throws Exception {
        // One actor can have memberships in the implicit root and JCM children.
        for(var workspace:new OfficialCartagoAdapter().capture(env).workspaces())
            for(var id:env.getController(workspace.fullName()).getCurrentAgents())
                if(name.equals(id.getAgentName()))env.resolveWSP(workspace.fullName()).getWorkspace().quitAgent(id);
    }
    public static AutoCloseable bind(JacamoSemanticSnapshot project,String actualName,String declaration) {
        BridgeRuntimeRegistry.configureDeclarations(project);
        var id=new BridgeEntityId("jason","agent","runtime-agent",project.project().metadata().semanticId(),actualName,"test-boundary-instance");
        BridgeRuntimeRegistry.registerAgent(actualName,id,declaration);
        if(BridgeRuntimeRegistry.projectedAgentId(actualName).isEmpty())
            throw new IllegalStateException("FIXTURE_AGENT_DECLARATION_NOT_BOUND:"+declaration+":"+
                    project.agentDeclarations().stream().map(a->a.name()+"="+a.metadata().semanticId()).toList());
        return ()->BridgeRuntimeRegistry.clearAgents();
    }
}
