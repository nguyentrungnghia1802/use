package org.jacamo.bridge.adapter;

import java.util.*;
import cartago.*;
import ora4mas.nopl.GroupBoard;

/** Read-only current public roster. Neither operation history nor role-incarnation inference. */
final class MoisePublicRoster {
    static final String VERSION="MOISE_PUBLIC_ROSTER/1.0";
    private static final boolean PINNED=pinned();
    private MoisePublicRoster(){ }
    static Map<String,Object> of(ora4mas.nopl.oe.Group state) {
        var players=state.getPlayers().stream().map(p->Map.of("agent",p.getAg(),"role",p.getTarget(),
                        "agentSemanticId",BridgeRuntimeRegistry.projectedAgentId(p.getAg()).orElseThrow(()->new IllegalStateException("MOISE_ROSTER_PLAYER_UNAVAILABLE")),
                        "runtimePlayer",BridgeRuntimeRegistry.agentIdentity(p.getAg()).map(org.jacamo.bridge.contract.BridgeEntityId::canonical).orElse("")))
                .sorted(Comparator.comparing((Map<String,String> p)->p.get("agent")).thenComparing(p->p.get("role"))).toList();
        if(players.size()>4096)throw new IllegalArgumentException("MOISE_ROSTER_CAPACITY");
        return Map.of("version",VERSION,"status","COMPLETE","group",state.getId(),"players",players);
    }
    static Map<String,Object> capture(CartagoEnvironment environment,ArtifactId id) {
        if(!id.getArtifactType().equals(GroupBoard.class.getName()))return Map.of();
        try {
            if(!PINNED)throw new IllegalArgumentException("MOISE_ROSTER_BINARY_UNSUPPORTED");
            return CartagoArtifactReadScope.read(environment,id,GroupBoard.class,(board,descriptor,held)->of(board.getGrpState().clone())).value();
        }catch(Exception|LinkageError unavailable){return Map.of("version",VERSION,"status","UNAVAILABLE","diagnostic",unavailable.getClass().getSimpleName()+":"+unavailable.getMessage());}
    }
    private static boolean pinned() {
        try {var jar=java.nio.file.Path.of(GroupBoard.class.getProtectionDomain().getCodeSource().getLocation().toURI());return java.nio.file.Files.isRegularFile(jar)&&java.nio.file.Files.size(jar)<=16*1024*1024&&MoiseSchemeRead.MOISE_SHA.equals(AdapterEvidence.digest(java.nio.file.Files.readAllBytes(jar)));}
        catch(Exception|LinkageError unavailable){return false;}
    }
}
