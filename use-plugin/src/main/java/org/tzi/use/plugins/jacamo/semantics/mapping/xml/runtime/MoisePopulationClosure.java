package org.tzi.use.plugins.jacamo.semantics.mapping.xml.runtime;

import java.util.*;
import org.jacamo.bridge.contract.*;

/** Current physical board inventory, not continuity of intermediate MOISE events.
 * No native object count, display name or omitted board may supply closure. */
final class MoisePopulationClosure {
    static final String POLICY="CARTAGO_BOARD_INVENTORY_V1",PROOF="moise-population-inventory";
    private static final String ARTIFACT="cartago.Artifact",ARTIFACT_HASH="67830177f2cb450876ac3b43aa90741cbc0b72079f5fcdc8320ff2f11b17c012";
    private MoisePopulationClosure(){ }
    static boolean complete(RuntimeSnapshot cut,RuntimeFactKind kind,String run) {
        String base=switch(kind){case GROUP_BOARD->"ora4mas.nopl.GroupBoard";case SCHEME_BOARD->"ora4mas.nopl.SchemeBoard";default->throw new IllegalArgumentException("MOISE_POPULATION_KIND_UNSUPPORTED");};
        String expected=kind==RuntimeFactKind.GROUP_BOARD?"b2adb4931af77396503d792f2682f123c10e9db852caaba01a4a088e852c490d":"79f28443c5701d5b424de5d952db81f17274a7d3c170c2fc2f9ab6dbb4628ba6";
        if(cut.sourceCompleteness().get("cartago")!=Completeness.COMPLETE||!stable(cut,"cartago")||!CartagoSourceCut.kinds(cut).contains(RuntimeFactKind.ARTIFACT))return false;
        var authorities=cut.sourceCompleteness().keySet().stream().filter(s->s.startsWith("moise:")).toList();
        if(authorities.isEmpty()||authorities.stream().anyMatch(s->cut.sourceCompleteness().get(s)==Completeness.UNAVAILABLE||!stable(cut,s)))return false;
        var physical=new HashSet<String>();var currentRosters=new HashMap<String,Object>();
        for(var artifact:cut.facts())if(artifact.kind()==RuntimeFactKind.ARTIFACT&&artifact.id().authority().equals("cartago")) {
            if(!faithful(artifact))return false;
            if(!(artifact.values().get("artifactProviderObservation") instanceof Map<?,?> raw))return false;
            var provider=CanonicalJson.object(raw);
            if(!"COMPLETE".equals(provider.get("status"))||!artifact.id().incarnation().equals(provider.get("artifactUuid"))
                    ||!Objects.equals(artifact.values().get("artifactTypeJavaClassName"),provider.get("javaClass")))return false;
            if(!(provider.get("classHierarchy") instanceof Map<?,?> hierarchy)||!"JAVA_ARTIFACT_LINEAGE/1.0".equals(hierarchy.get("version"))
                    ||!(hierarchy.get("types") instanceof List<?> types)||types.isEmpty()||types.size()>64)return false;
            var first=CanonicalJson.object(types.getFirst());var last=CanonicalJson.object(types.getLast());
            if(!Objects.equals(first.get("javaClass"),provider.get("javaClass"))||!Objects.equals(first.get("classHash"),provider.get("classHash"))
                    ||!ARTIFACT.equals(last.get("javaClass"))||!ARTIFACT_HASH.equals(last.get("classHash")))return false;
            var names=new HashSet<String>();boolean selected=false;
            for(var entry:types) {
                var type=CanonicalJson.object(entry);
                if(!(type.get("javaClass") instanceof String name)||!names.add(name)||!(type.get("classHash") instanceof String hash)||!hash.matches("[0-9a-f]{64}"))return false;
                if(name.equals(base)){if(!hash.equals(expected))return false;selected=true;}
            }
            if(selected) {
                if(!physical.add(artifact.id().incarnation()))return false;
                if(kind==RuntimeFactKind.GROUP_BOARD) {
                    if(!(provider.get("publicGroupRoster") instanceof Map<?,?> roster)||!"MOISE_PUBLIC_ROSTER/1.0".equals(roster.get("version"))||!"COMPLETE".equals(roster.get("status")))return false;
                    currentRosters.put(artifact.id().incarnation(),roster);
                }
            }
        }
        var boards=new HashSet<String>();
        for(var board:cut.facts())if(board.kind()==kind&&board.id().authority().equals("moise")) {
            if(!faithful(board)||!(board.values().get("boardArtifactIdentity") instanceof String uuid)
                    ||!board.id().incarnation().equals(run+":"+uuid)||!physical.contains(uuid)||!boards.add(uuid))return false;
            if(kind==RuntimeFactKind.GROUP_BOARD&&!Objects.equals(currentRosters.get(uuid),board.values().get("publicGroupRoster")))return false;
        }
        return physical.equals(boards);
    }
    private static boolean stable(RuntimeSnapshot cut,String source){return cut.startWatermarks().containsKey(source)&&Objects.equals(cut.startWatermarks().get(source),cut.endWatermarks().get(source));}
    private static boolean faithful(RuntimeFact fact){return fact.completeness()==Completeness.COMPLETE&&fact.projectionStatus()==ProjectionStatus.MATERIALIZED_FAITHFULLY;}
}
