package org.jacamo.bridge.adapter;

import java.util.*;
import cartago.*;

/** Read-only official registry/workspace discovery. Selection is not a global population proof. */
final class OfficialBoardInventory {
    static <T> Collection<T> collect(CartagoEnvironment environment,Class<T> type,Collection<? extends T> registry) {
        var result=new LinkedHashSet<T>();
        // Framework registries are mutable ArrayLists; never retain their iterator.
        for(Object value:registry.toArray())if(value!=null)result.add(type.cast(value));
        visit(environment.getRootWSP(),type,result,Collections.newSetFromMap(new IdentityHashMap<>()));
        return List.copyOf(result);
    }
    private static <T> void visit(WorkspaceDescriptor descriptor,Class<T> type,Set<T> result,Set<Workspace> visited) {
        if(descriptor==null||descriptor.getWorkspace()==null)return;
        var workspace=descriptor.getWorkspace();if(!visited.add(workspace))return;
        if(visited.size()>4096||result.size()>4096)throw new IllegalStateException("BOARD_INVENTORY_CAPACITY");
        for(String name:workspace.getArtifactList()) {
            var artifact=workspace.getArtifactDescriptor(name);
            if(artifact!=null&&type.isInstance(artifact.getArtifact()))result.add(type.cast(artifact.getArtifact()));
            if(result.size()>4096)throw new IllegalStateException("BOARD_INVENTORY_CAPACITY");
        }
        for(var child:workspace.getChildWSPs())visit(child,type,result,visited);
    }
    private OfficialBoardInventory(){ }
}
