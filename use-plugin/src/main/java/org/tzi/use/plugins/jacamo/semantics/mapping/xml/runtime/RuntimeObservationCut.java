package org.tzi.use.plugins.jacamo.semantics.mapping.xml.runtime;

import java.util.*;
import org.jacamo.bridge.contract.*;
import org.tzi.use.plugins.jacamo.semantics.evidence.SemanticJson;

/** Pure framework fold over the projector's admitted evidence index, never a second live state. */
public final class RuntimeObservationCut {
    private static final Map<String,RuntimeFactKind> UPSERT=Map.of(
        "UPSERT_CARTAGO_WORKSPACE",RuntimeFactKind.WORKSPACE,"UPSERT_CARTAGO_AGENT_IDENTITY",RuntimeFactKind.AGENT,
        "UPSERT_JASON_AGENT_STATE",RuntimeFactKind.AGENT,"UPSERT_CARTAGO_ARTIFACT",RuntimeFactKind.ARTIFACT,
        "UPSERT_CARTAGO_PROPERTY_SNAPSHOT",RuntimeFactKind.PROPERTY,"UPSERT_MOISE_GROUP",RuntimeFactKind.GROUP_BOARD,
        "UPSERT_MOISE_SCHEME",RuntimeFactKind.SCHEME_BOARD,"UPSERT_MOISE_GROUP_PARENT",RuntimeFactKind.RELATION_STATE);
    private RuntimeObservationCut(){ }
    public static boolean requiresMapping(Map<String,RuntimeFact> before,Map<String,RuntimeFact> after,
            RuntimeEvent event,long previousSequence,Completeness admittedCoverage) {
        // A replacement cut carries population/provenance proof even if all values
        // equal the prior partial cut. It must reach mapping and verification.
        if(CartagoSourceCut.KIND.equals(event.after().get("normalizedEventKind"))
                ||event.completeness()!=Completeness.COMPLETE&&admittedCoverage!=Completeness.PARTIAL||VerificationObservation.present(event)
                ||Set.of(RuntimeEventKind.REMOVED,RuntimeEventKind.DISPOSED).contains(event.kind())||!before.equals(after))return true;
        // Advancing CArtAgO once past a current receipt changes its qualification even
        // without a fact delta. Subsequent raw callbacks cannot invalidate that old receipt again.
        if(event.sourceId().equals("cartago"))for(var fact:before.values())
            if(fact.values().get(CapabilityObservation.KEY) instanceof Map<?,?> receipts)for(Object raw:receipts.values()) {
                var receipt=CanonicalJson.object(raw);var scope=CanonicalJson.object(receipt.get("scope"));
                if(scope.get("sourceSequence") instanceof Number n&&n.longValue()==previousSequence)return true;
            }
        return false;
    }
    public static Map<String,RuntimeFact> advance(Map<String,RuntimeFact> before,RuntimeEvent event) {
        var facts=new LinkedHashMap<>(before);String kind=Objects.toString(event.after().get("normalizedEventKind"),"");
        if(kind.equals(CartagoSourceCut.KIND)) {
            var cut=CartagoSourceCut.require(event);
            var kinds=CartagoSourceCut.kinds(cut);
            facts.values().removeIf(f->f.id().authority().equals("cartago")&&kinds.contains(f.kind()));
            for(var fact:cut.facts())facts.put(fact.id().canonical(),fact);
        }
        if(UPSERT.containsKey(kind)) {
            if(event.entityId()==null||event.factKind()!=UPSERT.get(kind))throw new IllegalArgumentException("XML_SOURCE_EVENT_KIND_MISMATCH");
            facts.put(event.entityId().canonical(),new RuntimeFact(event.entityId(),event.factKind(),event.after(),List.of(),event.projectionStatus(),event.completeness(),event.evidence()));
            if(kind.equals("UPSERT_CARTAGO_ARTIFACT")&&event.after().containsKey("properties"))properties(facts,event,event.after().get("properties"),List.of());
        }
        if(kind.equals("APPLY_CARTAGO_PROPERTY_DELTA"))properties(facts,event,event.after().get("properties"),event.after().get("removedPropertySemanticIds"));
        if(Set.of(RuntimeEventKind.NORM_ACTIVATED,RuntimeEventKind.NORM_FULFILLED,RuntimeEventKind.NORM_UNFULFILLED,RuntimeEventKind.NORM_INACTIVATED).contains(event.kind())) {
            if(!event.sourceId().equals("npl")||!event.subsystem().equals("npl")||event.entityId()==null||!event.entityId().authority().equals("npl")||!event.entityId().kind().equals("norm-instance")
                    ||event.factKind()!=RuntimeFactKind.NORM_INSTANCE||event.projectionStatus()!=ProjectionStatus.EVIDENCE_ONLY||!"NPL_OBSERVATION/1.0".equals(event.after().get("observationVersion")))throw new IllegalArgumentException("XML_NPL_CALLBACK_SOURCE_INVALID");
            // Same activation key survives ACTIVE and terminal callbacks. The dataset decoder still
            // qualifies public created annotations, physical board identity, binary, time and history.
            facts.put(event.entityId().canonical(),new RuntimeFact(event.entityId(),event.factKind(),event.after(),List.of(),event.projectionStatus(),event.completeness(),event.evidence()));
        }
        if(event.after().containsKey(CapabilityObservation.KEY)) {
            if(event.entityId()==null||!event.entityId().authority().equals("cartago")||!event.sourceId().equals("cartago"))throw new IllegalArgumentException("XML_CAPABILITY_CALLBACK_SOURCE_INVALID");
            var artifact=facts.get(event.entityId().canonical());var receipts=CanonicalJson.object(event.after().get(CapabilityObservation.KEY));
            if(artifact==null||artifact.kind()!=RuntimeFactKind.ARTIFACT||!Objects.equals(artifact.values().get("semanticId"),event.after().get("semanticId"))||receipts.size()>32)throw new IllegalArgumentException("XML_CAPABILITY_CALLBACK_OWNER_INVALID");
            var values=new LinkedHashMap<>(artifact.values());values.put(CapabilityObservation.KEY,receipts);
            // Replacement, not merge: a prior callback's omitted capability remains unavailable in this cut.
            facts.put(artifact.id().canonical(),new RuntimeFact(artifact.id(),artifact.kind(),values,artifact.relations(),artifact.projectionStatus(),artifact.completeness(),artifact.evidence()));
        }
        if(Set.of("DELETE_CARTAGO_ARTIFACT","DELETE_CARTAGO_AGENT_IDENTITY").contains(kind)) {
            if(event.entityId()==null||!event.entityId().authority().equals("cartago")||event.kind()!=RuntimeEventKind.DISPOSED&&event.kind()!=RuntimeEventKind.REMOVED)
                throw new IllegalArgumentException("XML_SOURCE_DISPOSAL_PROOF_REQUIRED");
            var old=facts.remove(event.entityId().canonical());
            if(old!=null&&old.kind()==RuntimeFactKind.ARTIFACT) {
                Object owner=old.values().get("semanticId");
                if(owner instanceof String id)facts.values().removeIf(f->f.kind()==RuntimeFactKind.PROPERTY&&id.equals(f.values().get("artifactSemanticId")));
            }
        }
        if(VerificationObservation.present(event)) {
            VerificationObservation.require(event);
            facts.put(event.entityId().canonical(),new RuntimeFact(event.entityId(),event.factKind(),event.after(),List.of(),event.projectionStatus(),event.completeness(),event.evidence()));
        }
        if(facts.size()>16384)throw new IllegalArgumentException("XML_OBSERVATION_CUT_CAPACITY");
        return Collections.unmodifiableMap(facts);
    }
    private static void properties(Map<String,RuntimeFact> facts,RuntimeEvent event,Object raw,Object removals) {
        if(event.entityId()==null||!event.entityId().authority().equals("cartago")||!(raw instanceof List<?> values)||!(removals instanceof List<?> removed))throw new IllegalArgumentException("XML_PROPERTY_DELTA_SHAPE");
        var artifact=facts.get(event.entityId().canonical());
        if(artifact==null||artifact.kind()!=RuntimeFactKind.ARTIFACT||!(artifact.values().get("semanticId") instanceof String owner)||!owner.equals(event.after().get("semanticId")))throw new IllegalArgumentException("XML_PROPERTY_OWNER_NOT_IN_ACCEPTED_CUT");
        var ids=new HashSet<String>();
        for(Object value:values) {
            var payload=CanonicalJson.object(value);
            if(!owner.equals(payload.get("artifactSemanticId"))||!(payload.get("propertyId") instanceof String id)||id.isBlank()||!ids.add(id))throw new IllegalArgumentException("XML_PROPERTY_DELTA_IDENTITY_MISMATCH");
            var propertyId=new BridgeEntityId("cartago","environment","observable-property-snapshot",owner,id,"snapshot");
            var data=new LinkedHashMap<>(payload);data.put("normalizedEventKind","UPSERT_CARTAGO_PROPERTY_SNAPSHOT");
            facts.put(propertyId.canonical(),new RuntimeFact(propertyId,RuntimeFactKind.PROPERTY,data,List.of(),event.projectionStatus(),event.completeness(),event.evidence()));
        }
        for(Object value:removed) {
            if(!(value instanceof String id)||id.isBlank())throw new IllegalArgumentException("XML_REMOVED_PROPERTY_ID_REQUIRED");
            var matches=facts.values().stream().filter(f->f.kind()==RuntimeFactKind.PROPERTY&&id.equals(f.values().get("semanticId"))).toList();
            if(matches.stream().anyMatch(f->!owner.equals(f.values().get("artifactSemanticId"))))throw new IllegalArgumentException("XML_REMOVED_PROPERTY_OWNER_MISMATCH");
            for(var match:matches)facts.remove(match.id().canonical());
        }
    }
    public static RuntimeSnapshot eventCut(Map<String,RuntimeFact> facts,Map<String,Long> accepted,
            Map<String,Completeness> coverage,RuntimeEvent event) {
        return eventCut(facts,accepted,coverage,event,Set.of(RuntimeFactKind.values()));
    }
    public static RuntimeSnapshot eventCut(Map<String,RuntimeFact> facts,Map<String,Long> accepted,
            Map<String,Completeness> coverage,RuntimeEvent event,Set<RuntimeFactKind> cartagoKinds) {
        var marks=new TreeMap<String,SourceWatermark>();accepted.forEach((s,n)->marks.put(s,new SourceWatermark(s,n)));marks.put(event.sourceId(),event.watermark());
        var quality=new TreeMap<String,Completeness>();marks.keySet().forEach(s->quality.put(s,coverage.getOrDefault(s,Completeness.UNAVAILABLE)));
        if(event.completeness()!=Completeness.COMPLETE)quality.put(event.sourceId(),Completeness.PARTIAL);
        var ordered=facts.values().stream().filter(f->!f.id().authority().equals("cartago")||cartagoKinds.contains(f.kind())).sorted(Comparator.comparing(f->f.id().canonical())).toList();
        String hash=SemanticJson.hash(ordered.stream().map(f->List.of(f.id().canonical(),f.values(),f.completeness().name(),f.projectionStatus().name())).toList());
        return new RuntimeSnapshot("accepted-prefix:"+event.eventId(),event.modelRevision(),event.observedAt(),event.observedAt(),marks,marks,1,ordered,quality,hash,
            Map.of("assembly","ADMITTED_EVENT_PREFIX_NOT_NEW_SOURCE_SNAPSHOT","globalAtomicity","UNSUPPORTED","sourceTime","UNQUALIFIED_OBSERVATION_TIME","cartagoQueryFactKinds",cartagoKinds.stream().map(Enum::name).sorted().toList()));
    }
}
