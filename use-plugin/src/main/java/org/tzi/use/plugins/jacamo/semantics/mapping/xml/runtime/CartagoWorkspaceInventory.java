package org.tzi.use.plugins.jacamo.semantics.mapping.xml.runtime;

import java.util.*;
import org.jacamo.bridge.contract.*;

/** Current workspace enumeration from a stable, explicitly scoped CArtAgO capture.
 * This proves neither intermediate history nor an atomic cut across frameworks. */
final class CartagoWorkspaceInventory {
    private CartagoWorkspaceInventory() { }

    static boolean complete(RuntimeSnapshot cut) {
        if(cut.sourceCompleteness().get("cartago")!=Completeness.COMPLETE
                ||!cut.startWatermarks().containsKey("cartago")
                ||!Objects.equals(cut.startWatermarks().get("cartago"),cut.endWatermarks().get("cartago")))return false;
        try {
            Map<String,Object> metadata=cut.captureMetadata();
            if(metadata.get("sourceEvidence") instanceof Map<?,?> sources)
                metadata=CanonicalJson.object(sources.get("cartago"));
            if(!local(metadata)&&!coordinated(cut.captureMetadata(),metadata))return false;
            var identities=new HashSet<String>();
            for(var fact:cut.facts())if(fact.id().authority().equals("cartago")&&fact.kind()==RuntimeFactKind.WORKSPACE) {
                if(fact.completeness()!=Completeness.COMPLETE
                        ||fact.projectionStatus()!=ProjectionStatus.MATERIALIZED_FAITHFULLY
                        ||!identities.add(fact.id().canonical()))return false;
            }
            return true;
        }catch(RuntimeException invalid){return false;}
    }

    private static boolean local(Map<String,Object> metadata) {
        if(!"LOCAL_SOURCE_STABLE_INTERVAL".equals(metadata.get("cutPolicy"))
                ||!(metadata.get("topologyStart") instanceof String topology)||!topology.matches("[0-9a-f]{64}")
                ||!topology.equals(metadata.get("topologyEnd"))
                ||!(metadata.get("capturedFactKinds") instanceof List<?> kinds)
                ||!kinds.contains("WORKSPACE")||new HashSet<>(kinds).size()!=kinds.size())return false;
        for(Object kind:kinds)RuntimeFactKind.valueOf((String)kind);
        return true;
    }

    private static boolean coordinated(Map<String,Object> cut,Map<String,Object> source) {
        if(!"PER_SOURCE_WATERMARK_AND_TOPOLOGY_VALIDATED".equals(cut.get("cutPolicy"))
                ||!(cut.get("topologyStart") instanceof Map<?,?> start)||!(cut.get("topologyEnd") instanceof Map<?,?> end)
                ||!(start.get("cartago") instanceof String topology)||!topology.matches("[0-9a-f]{64}")
                ||!topology.equals(end.get("cartago"))
                ||!"OFFICIAL_CONTROLLER_ENUMERATION_THEN_SELECTED_ARTIFACT_INFO_AND_VALUES".equals(source.get("inventoryAcquisition")))return false;
        // This admitted capture profile filters artifact classes/properties only;
        // its controller enumeration always includes every registered workspace.
        var scope=ObservationCaptureScope.fromMap(CanonicalJson.object(source.get("captureScope")));
        return scope.hash().equals(source.get("captureScopeHash"));
    }
}
