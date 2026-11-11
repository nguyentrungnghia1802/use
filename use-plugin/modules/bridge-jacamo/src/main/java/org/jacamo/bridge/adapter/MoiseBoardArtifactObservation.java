package org.jacamo.bridge.adapter;

import java.util.*;
import cartago.*;
import ora4mas.nopl.OrgArt;

/** Locate the exact public artifact instance and revalidate its descriptor/UUID. Names alone are insufficient. */
final class MoiseBoardArtifactObservation {
    private MoiseBoardArtifactObservation(){}
    static ArtifactInfo capture(OrgArt board)throws Exception {
        var environment=CartagoEnvironment.getInstance();var matches=new ArrayList<ArtifactInfo>();collect(environment,environment.getRootWSP(),board,matches);
        if(matches.size()!=1)throw new IllegalStateException("MOISE_BOARD_ARTIFACT_IDENTITY_UNAVAILABLE");return matches.getFirst();
    }
    private static void collect(CartagoEnvironment environment,WorkspaceDescriptor workspace,OrgArt board,List<ArtifactInfo> matches)throws Exception {
        if(workspace==null || workspace.getWorkspace()==null)return;var current=workspace.getWorkspace();
        for(String name:current.getArtifactList()) {var descriptor=current.getArtifactDescriptor(name);if(descriptor==null || descriptor.getArtifact()!=board)continue;var info=environment.getController(workspace.getId().getFullName()).getArtifactInfo(name);
            if(info==null || !info.getId().getName().equals(name) || !info.getId().getWorkspaceId().equals(workspace.getId()) || current.getArtifactDescriptor(name)!=descriptor)throw new IllegalStateException("MOISE_BOARD_ARTIFACT_REPLACED");matches.add(info);}
        for(var child:current.getChildWSPs())collect(environment,child,board,matches);
    }
}
