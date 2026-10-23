package org.tzi.use.plugins.jacamo.semantics.mapping.auction;

import java.util.*;
import org.jacamo.bridge.contract.*;
import org.tzi.use.plugins.jacamo.semantics.evidence.SemanticJson;

/** Pure fold of already admitted observations in NativeRuntimeProjector's
 * existing evidence index. It is not a second domain state or transport. */
public final class AuctionObservationCut {
    public record Advance(Map<String,RuntimeFact> facts,List<RuntimeEvent> discovery) {
        public Advance {facts=Map.copyOf(facts);discovery=List.copyOf(discovery);}
    }
    private AuctionObservationCut(){ }
    public static void validateClockContinuation(Map<String,RuntimeFact> before,RuntimeEvent event) {
        if(!VerificationObservation.present(event))return;var observation=VerificationObservation.require(event);
        var old=before.get(event.entityId().canonical());
        if(observation.kind()==VerificationObservation.Kind.CLOCK&&old!=null)
            AuctionSourceClock.requireContinuation(VerificationObservation.require(factEvent(old,event)),observation);
    }
    public static void validateClockContinuation(Map<String,RuntimeFact> before,RuntimeSnapshot cut,String run,long generation) {
        for(var fact:cut.facts())if(fact.values().containsKey(VerificationObservation.KEY)) {
            var next=VerificationObservation.require(fact,cut,run,generation);var old=before.get(fact.id().canonical());
            if(next.kind()==VerificationObservation.Kind.CLOCK&&old!=null)AuctionSourceClock.requireContinuation(VerificationObservation.require(old,cut,run,generation),next);
        }
    }
    public static boolean relevant(RuntimeEvent event) {
        String kind=Objects.toString(event.after().get("normalizedEventKind"),"");
        if(Set.of("UPSERT_CARTAGO_WORKSPACE","UPSERT_CARTAGO_AGENT_IDENTITY","UPSERT_JASON_AGENT_STATE","UPSERT_CARTAGO_ARTIFACT","DELETE_CARTAGO_ARTIFACT","DELETE_CARTAGO_AGENT_IDENTITY","UPSERT_MOISE_GROUP","UPSERT_MOISE_SCHEME","SET_CARTAGO_FOCUS").contains(kind)
                ||event.factKind()==RuntimeFactKind.NORM_INSTANCE||event.after().containsKey("auctionStateObservation")||event.after().containsKey("auctionSchemeObservation")||event.after().containsKey("auctionOperationObservation")||AuctionArtifactBindingResolver.discoverySignature(event.after().get("operationSignature")))return true;
        return VerificationObservation.present(event)&&Set.of(VerificationObservation.Kind.ARTIFACT_FIELD_CUT,VerificationObservation.Kind.GOAL_TRANSITION,VerificationObservation.Kind.NPL_LIFECYCLE,VerificationObservation.Kind.CLOCK).contains(VerificationObservation.require(event).kind());
    }
    public static Advance advance(Map<String,RuntimeFact> before,List<RuntimeEvent> previous,RuntimeEvent event) {
        var facts=new LinkedHashMap<>(before);var discovery=new ArrayList<>(previous);
        String kind=Objects.toString(event.after().get("normalizedEventKind"),"");
        if(event.factKind()==RuntimeFactKind.NORM_INSTANCE&&event.entityId()!=null)retainNorm(facts,event.entityId(),event.after(),event.completeness());
        if(event.entityId()!=null&&Set.of("UPSERT_CARTAGO_WORKSPACE","UPSERT_CARTAGO_AGENT_IDENTITY","UPSERT_JASON_AGENT_STATE","UPSERT_CARTAGO_ARTIFACT","UPSERT_CARTAGO_PROPERTY_SNAPSHOT","UPSERT_MOISE_GROUP","UPSERT_MOISE_SCHEME","UPSERT_MOISE_GROUP_PARENT","SET_CARTAGO_FOCUS").contains(kind))
            facts.put(event.entityId().canonical(),new RuntimeFact(event.entityId(),event.factKind(),event.after(),List.of(),event.projectionStatus(),event.completeness(),event.evidence()));
        if(event.entityId()!=null&&Set.of("DELETE_CARTAGO_ARTIFACT","DELETE_CARTAGO_AGENT_IDENTITY").contains(kind)) {
            var old=facts.remove(event.entityId().canonical());String owner=old==null?"":Objects.toString(old.values().get("semanticId"),"");
            if(!owner.isBlank())facts.values().removeIf(f->f.kind()==RuntimeFactKind.PROPERTY&&owner.equals(f.values().get("artifactSemanticId")));
        }
        if(AuctionArtifactBindingResolver.discoverySignature(event.after().get("operationSignature"))&&event.kind()==RuntimeEventKind.SUCCEEDED) {
            if(discovery.stream().noneMatch(e->e.eventId().equals(event.eventId())))discovery.add(event);
            if(discovery.size()>8192)throw new ContractException("AUCTION_DISCOVERY_PREFIX_CAPACITY_EXCEEDED");
        }
        if(event.after().get("auctionStateObservation") instanceof Map<?,?> state&&event.after().get("artifactSemanticId") instanceof String owner)
            attach(facts,owner,CanonicalJson.object(state),event.entityId());
        for(String key:List.of("auctionSchemeObservation","auctionOperationObservation"))if(event.after().get(key) instanceof Map<?,?> value) {
            String owner=Objects.toString(event.after().getOrDefault("artifactSemanticId",event.after().getOrDefault("semanticId","")),"");
            attachProperty(facts,owner,key,CanonicalJson.object(value),event.entityId());
        }
        if(VerificationObservation.present(event)) {
            var observation=VerificationObservation.require(event);
            validateClockContinuation(before,event);
            if(observation.kind()==VerificationObservation.Kind.ARTIFACT_FIELD_CUT) {
                var state=new LinkedHashMap<String,Object>(observation.data());state.put("version","1.0.0");
                state.put("status",observation.quality()==Completeness.COMPLETE?"COMPLETE":"INCOMPLETE");
                attach(facts,(String)observation.data().get("artifactIdentity"),state,null);
            }
            if(observation.kind()==VerificationObservation.Kind.GOAL_TRANSITION) {
                var state=CanonicalJson.object(observation.data().get("goalEvidence"));attachProperty(facts,(String)state.get("artifactIdentity"),"auctionSchemeObservation",state,null);
            }
            if(observation.kind()==VerificationObservation.Kind.NPL_LIFECYCLE) {
                var norm=CanonicalJson.object(observation.data().get("sourceObservation"));if(norm.containsKey("sourceLifecycle"))retainNorm(facts,BridgeEntityId.parse(observation.subject()),norm,observation.quality());
            }
            facts.put(event.entityId().canonical(),new RuntimeFact(event.entityId(),event.factKind(),event.after(),List.of(),event.projectionStatus(),event.completeness(),event.evidence()));
        }
        if(facts.size()>16384)throw new ContractException("AUCTION_OBSERVATION_CUT_CAPACITY_EXCEEDED");
        return new Advance(facts,discovery);
    }
    private static void retainNorm(Map<String,RuntimeFact> facts,BridgeEntityId identity,Map<String,Object> values,Completeness quality) {
        if(!identity.authority().equals("npl")||!"NPL_OBSERVATION/1.0".equals(values.get("observationVersion")))return;
        var old=facts.get(identity.canonical());
        if(old!=null&&old.values().get("sourceSequence") instanceof Number prior&&values.get("sourceSequence") instanceof Number next&&next.longValue()<=prior.longValue())return;
        facts.put(identity.canonical(),new RuntimeFact(identity,RuntimeFactKind.NORM_INSTANCE,values,List.of(),ProjectionStatus.EVIDENCE_ONLY,quality,List.of()));
    }
    private static RuntimeEvent factEvent(RuntimeFact fact,RuntimeEvent current) {
        var scope=CanonicalJson.object(CanonicalJson.object(fact.values().get(VerificationObservation.KEY)).get("scope"));long sequence=((Number)scope.get("sourceSequence")).longValue();
        return new RuntimeEvent("prior-clock:"+sequence,current.sessionId(),current.generation(),current.modelRevision(),"observer",VerificationObservation.SOURCE_ID,sequence,current.observedAt(),RuntimeEventKind.CHANGED,
                fact.kind(),fact.projectionStatus(),fact.id(),null,"","",Map.of(),fact.values(),new SourceWatermark(VerificationObservation.SOURCE_ID,sequence),fact.completeness(),fact.evidence());
    }
    private static void attach(Map<String,RuntimeFact> facts,String owner,Map<String,Object> state,BridgeEntityId callback) {
        attachProperty(facts,owner,"auctionStateObservation",state,callback);
    }
    private static void attachProperty(Map<String,RuntimeFact> facts,String owner,String key,Map<String,Object> state,BridgeEntityId callback) {
        var matches=facts.values().stream().filter(f->f.kind()==RuntimeFactKind.ARTIFACT&&owner.equals(f.values().get("semanticId"))).toList();
        if(matches.isEmpty())return; // An absent creation is unavailable, never fabricated.
        if(matches.size()!=1||callback!=null&&!matches.getFirst().id().equals(callback))throw new ContractException("AUCTION_FIELD_CUT_OWNER_MISMATCH");
        var fact=matches.getFirst();var values=new LinkedHashMap<>(fact.values());values.put(key,state);
        facts.put(fact.id().canonical(),new RuntimeFact(fact.id(),fact.kind(),values,fact.relations(),fact.projectionStatus(),fact.completeness(),fact.evidence()));
    }
    public static RuntimeSnapshot eventCut(Map<String,RuntimeFact> facts,Map<String,Long> acceptedWatermarks,RuntimeEvent event) {
        var marks=new TreeMap<String,SourceWatermark>();acceptedWatermarks.forEach((source,sequence)->marks.put(source,new SourceWatermark(source,sequence)));
        marks.put(event.sourceId(),event.watermark());var ordered=facts.values().stream().sorted(Comparator.comparing(f->f.id().canonical())).toList();
        String hash=SemanticJson.hash(ordered.stream().map(f->List.of(f.id().canonical(),f.values(),f.completeness().name(),f.projectionStatus().name())).toList());
        return new RuntimeSnapshot("accepted-prefix:"+event.eventId(),event.modelRevision(),event.observedAt(),event.observedAt(),marks,marks,1,ordered,Map.of(),hash,
                Map.of("assembly","ADMITTED_EVENT_PREFIX_NOT_NEW_SOURCE_SNAPSHOT","globalAtomicity","UNSUPPORTED","sourceTime","UNQUALIFIED_OBSERVATION_TIME"));
    }
}
