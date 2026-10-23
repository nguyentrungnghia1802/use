package org.jacamo.bridge.adapter;

import org.jacamo.bridge.contract.BridgeEntityId;
import org.jacamo.bridge.contract.semantic.JacamoSemanticSnapshot;

/** Adapter-boundary fixture registration. It does not claim a complete running Jason MAS. */
public final class CslRuntimeTestRegistry {
    /** BasicContext exposes no close/getSession in pinned CArtAgO 3.1.
     * Test-only access obtains this context's exact public IAgentSession;
     * closeSession releases both memberships and its AgentSessionArtifact.
     * Quitting a body alone deliberately remains available for leave tests. */
    public static void closeCartagoContext(cartago.util.agent.CartagoBasicContext context)throws Exception {
        var field=cartago.util.agent.CartagoBasicContext.class.getDeclaredField("session");
        if(!field.trySetAccessible())throw new IllegalStateException("FIXTURE_SESSION_ACCESS_UNAVAILABLE");
        ((cartago.IAgentSession)field.get(context)).closeSession();
    }
    public static void quitCartagoActor(cartago.CartagoEnvironment env,String name)throws Exception {
        // One actor can have memberships in the implicit root and JCM children.
        for(var workspace:new OfficialCartagoAdapter().capture(env).workspaces())
            for(var id:env.getController(workspace.fullName()).getCurrentAgents())
                if(name.equals(id.getAgentName()))env.resolveWSP(workspace.fullName()).getWorkspace().quitAgent(id);
    }
    public static AutoCloseable bind(JacamoSemanticSnapshot project,String actualName,String declaration) {
        var id=new BridgeEntityId("jason","agent","runtime-agent",project.project().metadata().semanticId(),actualName,"test-boundary-instance");
        return bind(project,actualName,declaration,id);
    }
    /** Explicit actual interpreter identity for the neutral callback/cut tests. */
    public static AutoCloseable bind(JacamoSemanticSnapshot project,String actualName,String declaration,BridgeEntityId id) {
        if(!id.authority().equals("jason")||!id.dimension().equals("agent")||!id.kind().equals("runtime-agent")||!id.localId().equals(actualName))throw new IllegalArgumentException("FIXTURE_RUNTIME_AGENT_IDENTITY_REQUIRED");
        BridgeRuntimeRegistry.configureDeclarations(project);
        BridgeRuntimeRegistry.registerAgent(actualName,id,declaration);
        if(BridgeRuntimeRegistry.projectedAgentId(actualName).isEmpty())
            throw new IllegalStateException("FIXTURE_AGENT_DECLARATION_NOT_BOUND:"+declaration+":"+
                    project.agentDeclarations().stream().map(a->a.name()+"="+a.metadata().semanticId()).toList());
        return ()->BridgeRuntimeRegistry.clearAgents();
    }
}
