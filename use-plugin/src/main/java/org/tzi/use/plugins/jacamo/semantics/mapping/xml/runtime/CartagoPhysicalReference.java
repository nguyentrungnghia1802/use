package org.tzi.use.plugins.jacamo.semantics.mapping.xml.runtime;

import java.util.*;
import org.jacamo.bridge.contract.*;

/** Framework identity crosswalk on the admitted cut. No class loading, name lookup or native state. */
public record CartagoPhysicalReference(RuntimeFact artifact,RuntimeFact workspace,Set<String> aliases) {
    public CartagoPhysicalReference {aliases=Set.copyOf(aliases);}
    public static Set<String> aliases(RuntimeFact artifact,RuntimeFact workspace) {
        if(!faithful(artifact,RuntimeFactKind.ARTIFACT)||!faithful(workspace,RuntimeFactKind.WORKSPACE))throw invalid();
        String environment=text(workspace.values(),"environmentId"),path=text(workspace.values(),"fullName");
        if(!workspace.id().scope().equals(environment)||!workspace.id().localId().equals(path)||!artifact.id().scope().equals(path)
                ||!artifact.id().localId().equals(artifact.values().get("name"))
                ||!artifact.id().incarnation().equals(artifact.values().get("uuid"))||!workspace.id().incarnation().equals(workspace.values().get("uuid"))
                ||!Objects.equals(artifact.values().get("workspaceSemanticId"),workspace.values().get("semanticId"))
                ||!("cartago:artifact:"+environment+":"+path+":"+artifact.id().incarnation()).equals(artifact.values().get("semanticId")))throw invalid();
        return Set.of(artifact.id().canonical(),(String)artifact.values().get("semanticId"));
    }
    public static CartagoPhysicalReference require(Object raw,RuntimeSnapshot cut) {
        var typed=CanonicalJson.object(raw);
        if(!typed.keySet().equals(Set.of("javaType","kind","semanticId","uuid","name","typeHint","workspaceUuid","workspaceName"))
                ||!"ARTIFACT_ID".equals(typed.get("kind"))||!"cartago.ArtifactId".equals(typed.get("javaType"))
                ||cut.sourceCompleteness().getOrDefault("cartago",Completeness.UNAVAILABLE)==Completeness.UNAVAILABLE
                ||!cut.startWatermarks().containsKey("cartago")||!Objects.equals(cut.startWatermarks().get("cartago"),cut.endWatermarks().get("cartago")))throw invalid();
        var matches=cut.facts().stream().filter(f->faithful(f,RuntimeFactKind.ARTIFACT)&&f.id().incarnation().equals(typed.get("uuid"))&&Objects.equals(f.values().get("semanticId"),typed.get("semanticId"))).toList();
        if(matches.size()!=1)throw invalid();var artifact=matches.getFirst();
        var workspaces=cut.facts().stream().filter(f->faithful(f,RuntimeFactKind.WORKSPACE)&&Objects.equals(f.values().get("semanticId"),artifact.values().get("workspaceSemanticId"))).toList();
        if(workspaces.size()!=1)throw invalid();var workspace=workspaces.getFirst();
        if(!artifact.id().localId().equals(typed.get("name"))||!Objects.equals(artifact.values().get("artifactTypeJavaClassName"),typed.get("typeHint"))
                ||!workspace.id().incarnation().equals(typed.get("workspaceUuid"))||!workspace.id().localId().equals(typed.get("workspaceName")))throw invalid();
        return new CartagoPhysicalReference(artifact,workspace,aliases(artifact,workspace));
    }
    private static boolean faithful(RuntimeFact fact,RuntimeFactKind kind){return fact!=null&&fact.kind()==kind&&fact.id().authority().equals("cartago")&&fact.id().dimension().equals("environment")&&fact.id().kind().equals(kind==RuntimeFactKind.ARTIFACT?"artifact":"workspace")&&fact.completeness()==Completeness.COMPLETE&&fact.projectionStatus()==ProjectionStatus.MATERIALIZED_FAITHFULLY;}
    private static String text(Map<String,Object> values,String key){if(!(values.get(key) instanceof String text)||text.isBlank())throw invalid();return text;}
    private static IllegalArgumentException invalid(){return new IllegalArgumentException("FIELD_CUT_PHYSICAL_SEMANTIC_ALIAS_MISMATCH_OR_REFERENCE_UNAVAILABLE");}
}
