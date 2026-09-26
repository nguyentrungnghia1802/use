package org.jacamo.bridge.contract;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Explicit DTO-to-neutral-tree mapping shared by recorded and network transports. */
public final class ContractPayloads {
    private ContractPayloads() { }

    public static Map<String, Object> model(ModelSnapshot snapshot) {
        var map = new LinkedHashMap<String, Object>();
        map.put("modelRevision", snapshot.modelRevision()); map.put("sources", facts(snapshot.sources()));
        map.put("agentDeclarations", facts(snapshot.agentDeclarations())); map.put("workspaces", facts(snapshot.workspaces()));
        map.put("configuredArtifacts", facts(snapshot.configuredArtifacts())); map.put("organisationFacts", facts(snapshot.organisationFacts()));
        map.put("groupRoleCardinalities", snapshot.groupRoleCardinalities().stream().map(ContractPayloads::cardinality).toList());
        map.put("parentSubGroupCardinalities", snapshot.parentSubGroupCardinalities().stream().map(ContractPayloads::cardinality).toList());
        map.put("crossDimensionalRelations", facts(snapshot.crossDimensionalRelations()));
        map.put("unresolvedFacts", snapshot.unresolvedFacts().stream().map(ContractPayloads::unresolved).toList());
        map.put("projectionProvenance", snapshot.projectionProvenance()); return map;
    }

    public static ModelSnapshot model(Map<String, Object> map) {
        return new ModelSnapshot(text(map, "modelRevision"), modelFacts(map, "sources"), modelFacts(map, "agentDeclarations"),
                modelFacts(map, "workspaces"), modelFacts(map, "configuredArtifacts"), modelFacts(map, "organisationFacts"),
                cardinalities(map, "groupRoleCardinalities"), cardinalities(map, "parentSubGroupCardinalities"),
                modelFacts(map, "crossDimensionalRelations"), unresolved(map, "unresolvedFacts"), stringMap(map, "projectionProvenance"));
    }

    public static Map<String, Object> runtime(RuntimeSnapshot snapshot) {
        var map = new LinkedHashMap<String, Object>();
        map.put("snapshotId", snapshot.snapshotId()); map.put("modelRevision", snapshot.modelRevision());
        map.put("captureStartedAt", snapshot.captureStartedAt().toString()); map.put("captureEndedAt", snapshot.captureEndedAt().toString());
        map.put("startWatermarks", watermarkMap(snapshot.startWatermarks())); map.put("endWatermarks", watermarkMap(snapshot.endWatermarks()));
        map.put("validationAttempts", snapshot.validationAttempts()); map.put("facts", snapshot.facts().stream().map(ContractPayloads::runtimeFact).toList());
        var complete = new TreeMap<String, Object>(); snapshot.sourceCompleteness().forEach((key, value) -> complete.put(key, value.name()));
        map.put("sourceCompleteness", complete); map.put("stateFingerprint", snapshot.stateFingerprint()); return map;
    }

    public static RuntimeSnapshot runtime(Map<String, Object> map) {
        var facts = new ArrayList<RuntimeFact>();
        for (Object item : array(map, "facts")) facts.add(runtimeFact(object(item)));
        var complete = new TreeMap<String, Completeness>(); object(map.get("sourceCompleteness")).forEach((key, value) -> complete.put(key, enumeration(Completeness.class, value)));
        return new RuntimeSnapshot(text(map, "snapshotId"), text(map, "modelRevision"), Instant.parse(text(map, "captureStartedAt")),
                Instant.parse(text(map, "captureEndedAt")), watermarks(map, "startWatermarks"), watermarks(map, "endWatermarks"),
                integer(map, "validationAttempts"), facts, complete, text(map, "stateFingerprint"));
    }

    public static Map<String, Object> event(RuntimeEvent event) {
        var map = new LinkedHashMap<String, Object>();
        map.put("eventId", event.eventId()); map.put("sessionId", event.sessionId()); map.put("generation", event.generation());
        map.put("modelRevision", event.modelRevision()); map.put("subsystem", event.subsystem()); map.put("sourceId", event.sourceId());
        map.put("sourceSequence", event.sourceSequence()); map.put("observedAt", event.observedAt().toString()); map.put("kind", event.kind().name());
        map.put("factKind", event.factKind() == null ? null : event.factKind().name());
        map.put("projectionStatus", event.projectionStatus() == null ? null : event.projectionStatus().name());
        map.put("entityId", event.entityId() == null ? null : id(event.entityId())); map.put("relationId", event.relationId() == null ? null : relation(event.relationId()));
        map.put("correlationId", event.correlationId()); map.put("causationId", event.causationId()); map.put("before", event.before()); map.put("after", event.after());
        map.put("watermark", watermark(event.watermark())); map.put("completeness", event.completeness().name());
        map.put("evidence", event.evidence().stream().map(ContractPayloads::evidence).toList()); return map;
    }

    public static RuntimeEvent event(Map<String, Object> map) {
        return new RuntimeEvent(text(map, "eventId"), text(map, "sessionId"), number(map, "generation"), text(map, "modelRevision"),
                text(map, "subsystem"), text(map, "sourceId"), number(map, "sourceSequence"), Instant.parse(text(map, "observedAt")),
                enumeration(RuntimeEventKind.class, map.get("kind")), map.get("factKind") == null ? null : enumeration(RuntimeFactKind.class,map.get("factKind")),
                map.get("projectionStatus") == null ? null : enumeration(ProjectionStatus.class,map.get("projectionStatus")),
                map.get("entityId") == null ? null : id(object(map.get("entityId"))),
                map.get("relationId") == null ? null : relation(object(map.get("relationId"))), text(map, "correlationId"), text(map, "causationId"),
                object(map.get("before")), object(map.get("after")), watermark(object(map.get("watermark"))),
                enumeration(Completeness.class, map.get("completeness")), evidenceList(map, "evidence"));
    }

    private static List<Map<String, Object>> facts(List<ModelFact> facts) { return facts.stream().map(ContractPayloads::fact).toList(); }
    private static Map<String, Object> fact(ModelFact fact) {
        var refs = new TreeMap<String, Object>(); fact.references().forEach((key, value) -> refs.put(key, value.stream().map(ContractPayloads::id).toList()));
        return Map.of("factKind", fact.factKind(), "id", id(fact.id()), "attributes", fact.attributes(), "references", refs,
                "completeness", fact.completeness().name(), "evidence", fact.evidence().stream().map(ContractPayloads::evidence).toList());
    }
    private static ModelFact fact(Map<String, Object> map) {
        var refs = new TreeMap<String, List<BridgeEntityId>>(); object(map.get("references")).forEach((key, value) -> {
            var ids = new ArrayList<BridgeEntityId>(); for (Object item : array(value)) ids.add(id(object(item))); refs.put(key, ids); });
        return new ModelFact(text(map, "factKind"), id(object(map.get("id"))), stringMap(map, "attributes"), refs,
                enumeration(CapabilityStatus.class, map.get("completeness")), evidenceList(map, "evidence"));
    }
    private static List<ModelFact> modelFacts(Map<String, Object> map, String key) { var result = new ArrayList<ModelFact>(); for (Object item : array(map, key)) result.add(fact(object(item))); return result; }

    private static Map<String, Object> cardinality(RelationCardinality value) { return Map.of("id", relation(value.id()), "context", id(value.context()), "member", id(value.member()), "min", value.min(), "max", value.max(), "evidence", value.evidence().stream().map(ContractPayloads::evidence).toList()); }
    private static RelationCardinality cardinality(Map<String, Object> map) { return new RelationCardinality(relation(object(map.get("id"))), id(object(map.get("context"))), id(object(map.get("member"))), integer(map, "min"), integer(map, "max"), evidenceList(map, "evidence")); }
    private static List<RelationCardinality> cardinalities(Map<String, Object> map, String key) { var result = new ArrayList<RelationCardinality>(); for (Object item : array(map, key)) result.add(cardinality(object(item))); return result; }

    private static Map<String, Object> unresolved(UnresolvedFact value) { return Map.of("kind", value.kind(), "stableKey", value.stableKey(), "status", value.status().name(), "reason", value.reason(), "evidence", value.evidence().stream().map(ContractPayloads::evidence).toList()); }
    private static List<UnresolvedFact> unresolved(Map<String, Object> map, String key) { var result = new ArrayList<UnresolvedFact>(); for (Object item : array(map, key)) { var value=object(item); result.add(new UnresolvedFact(text(value,"kind"),text(value,"stableKey"),enumeration(CapabilityStatus.class,value.get("status")),text(value,"reason"),evidenceList(value,"evidence"))); } return result; }

    private static Map<String, Object> runtimeFact(RuntimeFact value) { return Map.of("id", id(value.id()), "kind", value.kind().name(), "values", value.values(), "relations", value.relations().stream().map(ContractPayloads::relation).toList(), "projectionStatus", value.projectionStatus().name(), "completeness", value.completeness().name(), "evidence", value.evidence().stream().map(ContractPayloads::evidence).toList()); }
    private static RuntimeFact runtimeFact(Map<String, Object> map) { var relations = new ArrayList<BridgeRelationId>(); for(Object item:array(map,"relations")) relations.add(relation(object(item))); return new RuntimeFact(id(object(map.get("id"))), enumeration(RuntimeFactKind.class,map.get("kind")),object(map.get("values")),relations,enumeration(ProjectionStatus.class,map.get("projectionStatus")),enumeration(Completeness.class,map.get("completeness")),evidenceList(map,"evidence")); }

    private static Map<String, Object> id(BridgeEntityId value) { return Map.of("authority",value.authority(),"dimension",value.dimension(),"kind",value.kind(),"scope",value.scope(),"localId",value.localId(),"incarnation",value.incarnation(),"canonical",value.canonical()); }
    private static BridgeEntityId id(Map<String, Object> map) { var value = new BridgeEntityId(text(map,"authority"),text(map,"dimension"),text(map,"kind"),text(map,"scope"),text(map,"localId"),text(map,"incarnation")); if(!value.canonical().equals(text(map,"canonical"))) throw new ContractException("BridgeEntityId canonical mismatch"); return value; }
    private static Map<String, Object> relation(BridgeRelationId value) { return Map.of("relationKind",value.relationKind(),"endpoints",value.endpoints().stream().map(ContractPayloads::id).toList(),"authorityEvidenceId",value.authorityEvidenceId(),"incarnation",value.incarnation(),"canonical",value.canonical()); }
    private static BridgeRelationId relation(Map<String, Object> map) { var endpoints=new ArrayList<BridgeEntityId>(); for(Object item:array(map,"endpoints")) endpoints.add(id(object(item))); var value=new BridgeRelationId(text(map,"relationKind"),endpoints,text(map,"authorityEvidenceId"),text(map,"incarnation")); if(!value.canonical().equals(text(map,"canonical"))) throw new ContractException("BridgeRelationId canonical mismatch"); return value; }
    private static Map<String, Object> evidence(Evidence value) { return Map.of("evidenceId",value.evidenceId(),"authority",value.authority(),"sourceUri",value.sourceUri(),"sourceDigest",value.sourceDigest(),"detail",value.detail()); }
    private static Evidence evidence(Map<String,Object> map) { return new Evidence(text(map,"evidenceId"),text(map,"authority"),text(map,"sourceUri"),text(map,"sourceDigest"),text(map,"detail")); }
    private static List<Evidence> evidenceList(Map<String,Object> map,String key) { var result=new ArrayList<Evidence>(); for(Object item:array(map,key)) result.add(evidence(object(item))); return result; }
    private static Map<String,Object> watermark(SourceWatermark value) { return Map.of("sourceId",value.sourceId(),"sequence",value.sequence()); }
    private static SourceWatermark watermark(Map<String,Object> map) { return new SourceWatermark(text(map,"sourceId"),number(map,"sequence")); }
    private static Map<String,Object> watermarkMap(Map<String,SourceWatermark> values) { var result=new TreeMap<String,Object>(); values.forEach((key,value)->result.put(key,watermark(value))); return result; }
    private static Map<String,SourceWatermark> watermarks(Map<String,Object> map,String key) { var result=new TreeMap<String,SourceWatermark>(); object(map.get(key)).forEach((name,value)->result.put(name,watermark(object(value)))); return result; }

    @SuppressWarnings("unchecked") private static Map<String,Object> object(Object value) { return CanonicalJson.object(value); }
    private static List<?> array(Map<String,Object> map,String key) { return array(map.get(key)); }
    private static List<?> array(Object value) { if(!(value instanceof List<?> list)) throw new ContractException("array required"); return list; }
    private static String text(Map<String,Object> map,String key) { if(!(map.get(key) instanceof String value)) throw new ContractException(key+" must be a string"); return value; }
    private static long number(Map<String,Object> map,String key) { if(!(map.get(key) instanceof Number value)) throw new ContractException(key+" must be a number"); return value.longValue(); }
    private static int integer(Map<String,Object> map,String key) { return Math.toIntExact(number(map,key)); }
    private static Map<String,String> stringMap(Map<String,Object> map,String key) { var result=new TreeMap<String,String>(); object(map.get(key)).forEach((name,value)->{ if(!(value instanceof String text)) throw new ContractException(key+" values must be strings"); result.put(name,text); }); return result; }
    private static <E extends Enum<E>> E enumeration(Class<E> type,Object value) { if(!(value instanceof String text)) throw new ContractException(type.getSimpleName()+" must be a string"); try{return Enum.valueOf(type,text);}catch(IllegalArgumentException error){throw new ContractException("unknown "+type.getSimpleName()+": "+text,error);} }
}
