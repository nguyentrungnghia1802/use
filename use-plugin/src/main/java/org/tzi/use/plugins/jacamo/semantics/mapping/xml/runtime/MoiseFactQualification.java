package org.tzi.use.plugins.jacamo.semantics.mapping.xml.runtime;

import java.util.*;
import org.jacamo.bridge.contract.*;
import org.jacamo.bridge.contract.semantic.*;
import org.tzi.use.plugins.jacamo.codegrounded.use.MoiseDomainProjection;
import org.tzi.use.plugins.jacamo.semantics.evidence.SemanticJson;
import org.tzi.use.plugins.jacamo.semantics.observer.FunctionalObservation;

/** Frozen official MOISE definition and public board cut qualification. Does not infer goal truth/time. */
final class MoiseFactQualification {
    private final ModelSnapshot source;
    MoiseFactQualification(ModelSnapshot source){this.source=Objects.requireNonNull(source);}
    void require(RuntimeFact fact,String run) {
        if(!Set.of(RuntimeFactKind.GROUP_BOARD,RuntimeFactKind.SCHEME_BOARD).contains(fact.kind()))return;
        var data=fact.values();String expectedKind=fact.kind()==RuntimeFactKind.GROUP_BOARD?"group-board":"scheme-board";
        if(!fact.id().dimension().equals("organisation")||!fact.id().kind().equals(expectedKind)||!fact.id().incarnation().startsWith(run+":"))throw invalid("BOARD_EPOCH_OR_KIND_MISMATCH");
        var definition=SemanticContractCodec.organizationFromTree(data.get("organisationDefinition"));MoiseDomainProjection.validate(definition);
        var originals=source.semanticContract().moiseOrganizations().stream().filter(o->o.metadata().semanticId().equals(data.get("organisationSpecSemanticId"))).toList();
        if(originals.size()!=1||!definition.metadata().semanticId().equals(data.get("organisationSpecSemanticId"))
                ||!SemanticJson.hash(shape(SemanticContractCodec.organizationToTree(originals.getFirst()))).equals(SemanticJson.hash(shape(SemanticContractCodec.organizationToTree(definition)))))throw invalid("FROZEN_ORGANIZATION_DEFINITION_CHANGED");
        if(definition.metadata().evidenceAuthority()!=EvidenceAuthority.OFFICIAL_MOISE_API||definition.metadata().fidelity()!=Fidelity.EXACT
                ||definition.metadata().capabilityStatus()!=CapabilityStatus.COMPLETE||definition.metadata().evidence().isEmpty())throw invalid("OFFICIAL_DEFINITION_EVIDENCE_REQUIRED");
        for(var evidence:definition.metadata().evidence())if(evidence.authority()!=EvidenceAuthority.OFFICIAL_MOISE_API||evidence.fidelity()!=Fidelity.EXACT||evidence.capabilityStatus()!=CapabilityStatus.COMPLETE
                ||!evidence.sourceDigest().matches("[0-9a-f]{64}")||!evidence.sourceSemanticId().equals(definition.metadata().semanticId())||!evidence.sourceJavaFqcn().equals(definition.metadata().sourceJavaFqcn()))throw invalid("OFFICIAL_DEFINITION_PROVENANCE_MISMATCH");
        if(!fact.id().scope().equals(data.get("organisationName"))||!fact.id().localId().equals(data.get("name"))||!fact.id().canonical().equals(data.get("runtimeIdentity")))throw invalid("PUBLIC_BOARD_IDENTITY_MISMATCH");
        if(fact.kind()==RuntimeFactKind.SCHEME_BOARD) {FunctionalObservation.decode(fact.id(),data);return;}
        var groups=definition.structuralSpecification().groups().stream().filter(g->g.metadata().semanticId().equals(data.get("groupSpecSemanticId"))).toList();
        if(groups.size()!=1)throw invalid("FROZEN_GROUP_DEFINITION_UNRESOLVED");
        if(!(data.get("players") instanceof List<?> players))throw invalid("PUBLIC_GROUP_PLAYERS_REQUIRED");
        var tuples=new HashSet<String>();
        for(Object raw:players) {
            var player=CanonicalJson.object(raw);
            if(!player.keySet().equals(Set.of("agentSemanticId","roleSemanticId"))||!(player.get("agentSemanticId") instanceof String agent)||agent.isBlank()
                    ||!(player.get("roleSemanticId") instanceof String role)||!groups.getFirst().roleSemanticIds().contains(role))throw invalid("PUBLIC_PLAYER_ROLE_SCOPE_MISMATCH");
            if(!tuples.add(SemanticJson.hash(List.of(agent,role))))throw invalid("PUBLIC_PLAYER_ROLE_DUPLICATE");
        }
    }
    /** Same legacy semantic comparison: only evidence carrier and source URI differ for a live OS graph. */
    static Object shape(Object raw) {
        if(raw instanceof List<?> list)return list.stream().map(MoiseFactQualification::shape).toList();
        if(raw instanceof Map<?,?>) {
            var map=CanonicalJson.object(raw);var result=new TreeMap<String,Object>();
            boolean metadata=map.containsKey("semanticId")&&map.containsKey("sourceKind"),organization=map.containsKey("structuralSpecification")&&map.containsKey("functionalSpecification")&&map.containsKey("normativeSpecification");
            map.forEach((key,value)->{if(!(metadata&&key.equals("evidence"))&&!(organization&&key.equals("sourceUri")))result.put(key,shape(value));});return result;
        }
        return raw;
    }
    private static IllegalArgumentException invalid(String reason){return new IllegalArgumentException("MOISE_FACT:"+reason);}
}
