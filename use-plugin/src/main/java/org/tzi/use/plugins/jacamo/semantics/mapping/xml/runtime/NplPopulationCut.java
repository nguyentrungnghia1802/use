package org.tzi.use.plugins.jacamo.semantics.mapping.xml.runtime;

import java.util.*;
import org.jacamo.bridge.contract.*;

/** Closed current board inventory plus continuous, retained source activation catalogs.
 * Selected callbacks alone never certify absence, including an empty result. */
public final class NplPopulationCut {
    private NplPopulationCut() { }
    public static boolean closed(RuntimeSnapshot cut,String boardClass) {
        if(boardClass.isBlank()||cut.sourceCompleteness().get("cartago")!=Completeness.COMPLETE
                ||cut.sourceCompleteness().getOrDefault("npl",Completeness.UNAVAILABLE)==Completeness.UNAVAILABLE)return false;
        for(String source:List.of("cartago","npl"))if(!cut.startWatermarks().containsKey(source)
                ||!Objects.equals(cut.startWatermarks().get(source),cut.endWatermarks().get(source)))return false;
        try {
            var metadata=CanonicalJson.object(CanonicalJson.object(cut.captureMetadata().get("sourceEvidence")).get("npl"));
            if(!(metadata.get("activationCatalogs") instanceof List<?> catalogs))return false;
            var indexed=new HashMap<String,Map<String,Object>>();
            for(Object raw:catalogs) {var catalog=CanonicalJson.object(raw);String uuid=(String)catalog.get("boardArtifactIdentity");
                if(indexed.putIfAbsent(uuid,catalog)!=null)return false;}
            for(var board:cut.facts())if(board.kind()==RuntimeFactKind.ARTIFACT&&board.id().authority().equals("cartago")
                    &&boardClass.equals(board.values().get("artifactTypeJavaClassName"))) {
                if(board.completeness()!=Completeness.COMPLETE||board.projectionStatus()!=ProjectionStatus.MATERIALIZED_FAITHFULLY)return false;
                var catalog=indexed.get(board.id().incarnation());if(catalog==null)return false;
                var subscription=CanonicalJson.object(catalog.get("subscription"));
                var workspaces=cut.facts().stream().filter(f->f.kind()==RuntimeFactKind.WORKSPACE&&f.id().authority().equals("cartago")
                        &&Objects.equals(f.values().get("semanticId"),board.values().get("workspaceSemanticId"))).toList();
                if(workspaces.size()!=1||workspaces.getFirst().completeness()!=Completeness.COMPLETE
                        ||workspaces.getFirst().projectionStatus()!=ProjectionStatus.MATERIALIZED_FAITHFULLY
                        ||!workspaces.getFirst().id().incarnation().equals(subscription.get("workspaceUuid"))
                        ||!Objects.equals(workspaces.getFirst().values().get("fullName"),subscription.get("workspaceFullName")))return false;
                if(!"NPL_RETAINED_ACTIVATION_CATALOG/1.0".equals(catalog.get("version"))||!Boolean.TRUE.equals(catalog.get("complete"))
                        ||!(catalog.get("sourceSequence") instanceof Number sequence)||sequence.longValue()!=cut.endWatermarks().get("npl").sequence()
                        ||!"NPL_BOARD_SUBSCRIPTION/1.0".equals(subscription.get("version"))
                        ||!Boolean.TRUE.equals(subscription.get("emptyBaselineAfterListener"))
                        ||!"CARTAGO_CREATION_BEFORE_RETURN".equals(subscription.get("origin"))
                        ||!"CONTINUOUS_FROM_EMPTY_CREATION".equals(subscription.get("scopeQuality"))
                        ||!board.id().incarnation().equals(subscription.get("boardArtifactIdentity"))
                        ||!boardClass.equals(subscription.get("artifactClass"))
                        ||!Objects.equals(board.values().get("name"),subscription.get("artifactName"))
                        ||!board.id().scope().equals(subscription.get("workspaceFullName")))return false;
                if(!(catalog.get("activationKeys") instanceof List<?> keys)||new HashSet<>(keys).size()!=keys.size())return false;
                var actual=new HashSet<String>();
                for(var norm:cut.facts())if(norm.kind()==RuntimeFactKind.NORM_INSTANCE&&norm.id().authority().equals("npl")
                        &&board.id().incarnation().equals(norm.values().get("boardArtifactIdentity"))) {
                    if(norm.completeness()!=Completeness.COMPLETE||!"CREATED_EVENT_OBSERVED".equals(norm.values().get("history"))
                            ||!(norm.values().get("activationKey") instanceof String key)||!actual.add(key)
                            ||!subscription.equals(CanonicalJson.object(norm.values().get("boardObservationProof"))))return false;
                }
                if(!actual.equals(new HashSet<>(keys)))return false;
            }
            return true;
        }catch(RuntimeException invalid){return false;}
    }
}
