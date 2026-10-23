package org.jacamo.bridge.contract;

import java.math.BigDecimal;
import java.util.*;

/** Versioned observer evidence, carried by the existing runtime envelope.
 * Schema support never certifies population, clock monotonicity or domain truth. */
public record VerificationObservation(Kind kind,String subject,Completeness quality,String diagnostic,
        SourceTime sourceTime,Map<String,Object> data) {
    public static final String KEY="verificationObservation",VERSION="1.0.0",SOURCE_ID="observer:verification";
    public static final String CAPABILITY="runtime.verification-observation.v1";
    public static final int MAX_BYTES=128*1024;
    public enum Kind { ARTIFACT_FIELD_CUT, JASON_STEP, GOAL_TRANSITION, NPL_LIFECYCLE, CLOCK }
    public record SourceTime(String status,String clockId,String unit,String rawValue,String authority,String diagnostic) {
        public SourceTime {
            for(String value:List.of(status,clockId,unit,rawValue,authority,diagnostic))bounded(value);
            if(status.equals("OBSERVED")) {
                if(clockId.isBlank()||!unit.equals("EPOCH_MILLISECONDS")||authority.isBlank()||!diagnostic.isBlank())throw invalid("SOURCE_TIME_PROOF_REQUIRED");
                try {if(Long.parseLong(rawValue)<0)throw invalid("SOURCE_TIME_RANGE");}catch(NumberFormatException error){throw invalid("SOURCE_TIME_RANGE");}
            }else if(!status.equals("UNAVAILABLE")||!clockId.isEmpty()||!unit.isEmpty()||!rawValue.isEmpty()||!authority.isEmpty()||diagnostic.isBlank())throw invalid("SOURCE_TIME_STATUS");
        }
        public static SourceTime unavailable(String reason){return new SourceTime("UNAVAILABLE","","","","",reason);}
        public Map<String,Object> payload(){return Map.of("status",status,"clockId",clockId,"unit",unit,"rawValue",rawValue,"authority",authority,"diagnostic",diagnostic);}
        static SourceTime read(Object raw) {
            var map=object(raw);keys(map,Set.of("status","clockId","unit","rawValue","authority","diagnostic"));
            return new SourceTime(text(map,"status"),text(map,"clockId"),text(map,"unit"),text(map,"rawValue"),text(map,"authority"),text(map,"diagnostic"));
        }
    }
    public VerificationObservation {
        Objects.requireNonNull(kind);Objects.requireNonNull(quality);Objects.requireNonNull(sourceTime);bounded(subject);bounded(diagnostic);
        if(subject.isBlank()||quality==Completeness.COMPLETE&&!diagnostic.isBlank()||quality!=Completeness.COMPLETE&&diagnostic.isBlank())throw invalid("QUALITY_OR_SUBJECT");
        data=ContractSupport.canonicalObject(Objects.requireNonNull(data));
        if(CanonicalJson.encode(data,MAX_BYTES).length>MAX_BYTES)throw invalid("CAPACITY");
        validateData(kind,data,quality,sourceTime);
    }
    public Map<String,Object> payload(RuntimeEvent scope) {
        return Map.of("version",VERSION,"kind",kind.name(),"subject",subject,"quality",quality.name(),"diagnostic",diagnostic,
                "sourceTime",sourceTime.payload(),"data",data,
                "scope",Map.of("sessionId",scope.sessionId(),"generation",scope.generation(),"modelRevision",scope.modelRevision(),"sourceId",scope.sourceId(),"sourceSequence",scope.sourceSequence()));
    }
    /** Only producer code calls this; it does not read a path, evaluate XML or invoke a framework API. */
    public RuntimeEvent event(String session,long generation,String revision,long sequence,java.time.Instant observedAt,String correlation,String cause) {
        return event(session,generation,revision,sequence,observedAt,correlation,cause,true);
    }
    public RuntimeEvent event(String session,long generation,String revision,long sequence,java.time.Instant observedAt,String correlation,String cause,boolean firstObservation) {
        var id=new BridgeEntityId("observer","runtime","verification-observation",session,kind.name()+":"+ContractSupport.sha256(subject),session);
        var base=new RuntimeEvent(session+":verification:"+sequence,session,generation,revision,"observer",SOURCE_ID,sequence,observedAt,
                firstObservation?RuntimeEventKind.ADDED:RuntimeEventKind.CHANGED,RuntimeFactKind.RUNTIME_EVENT,ProjectionStatus.EVIDENCE_ONLY,id,null,correlation,cause,Map.of(),Map.of(),
                new SourceWatermark(SOURCE_ID,sequence),quality,List.of());
        return new RuntimeEvent(base.eventId(),session,generation,revision,base.subsystem(),base.sourceId(),sequence,observedAt,base.kind(),base.factKind(),base.projectionStatus(),id,null,
                correlation,cause,Map.of(),Map.of(KEY,payload(base)),base.watermark(),quality,List.of());
    }
    public static boolean present(RuntimeEvent event){return event.after().containsKey(KEY);}
    public static VerificationObservation require(RuntimeEvent event) {
        var map=object(event.after().get(KEY));
        if(!VERSION.equals(map.get("version")))throw invalid("VERSION_UNSUPPORTED");
        keys(map,Set.of("version","kind","subject","quality","diagnostic","sourceTime","data","scope"));
        Kind kind;Completeness quality;
        try {kind=Kind.valueOf(text(map,"kind"));quality=Completeness.valueOf(text(map,"quality"));}
        catch(IllegalArgumentException error){throw invalid("KIND_OR_QUALITY_UNSUPPORTED");}
        var value=new VerificationObservation(kind,text(map,"subject"),quality,text(map,"diagnostic"),SourceTime.read(map.get("sourceTime")),object(map.get("data")));
        var scope=object(map.get("scope"));keys(scope,Set.of("sessionId","generation","modelRevision","sourceId","sourceSequence"));
        var id=event.entityId();
        if(!event.subsystem().equals("observer")||!event.sourceId().equals(SOURCE_ID)||!Set.of(RuntimeEventKind.ADDED,RuntimeEventKind.CHANGED).contains(event.kind())||event.factKind()!=RuntimeFactKind.RUNTIME_EVENT
                ||event.projectionStatus()!=ProjectionStatus.EVIDENCE_ONLY||event.completeness()!=quality||id==null||event.relationId()!=null||!event.before().isEmpty()
                ||!id.equals(new BridgeEntityId("observer","runtime","verification-observation",event.sessionId(),kind.name()+":"+ContractSupport.sha256(value.subject()),event.sessionId()))
                ||!event.sessionId().equals(scope.get("sessionId"))||!event.modelRevision().equals(scope.get("modelRevision"))||!event.sourceId().equals(scope.get("sourceId"))
                ||event.generation()!=integer(scope.get("generation"))||event.sourceSequence()!=integer(scope.get("sourceSequence"))
                ||!event.watermark().equals(new SourceWatermark(SOURCE_ID,event.sourceSequence())))throw invalid("MIXED_SCOPE_OR_ENVELOPE");
        return value;
    }
    public static VerificationObservation require(RuntimeFact fact,RuntimeSnapshot snapshot,String run,long generation) {
        var payload=object(fact.values().get(KEY));var scope=object(payload.get("scope"));
        long sequence=integer(scope.get("sourceSequence"));
        var end=snapshot.endWatermarks().get(SOURCE_ID);
        if(end==null||sequence>end.sequence()||sequence<1)throw invalid("SNAPSHOT_CUT_MISMATCH");
        return require(new RuntimeEvent("snapshot-observation:"+sequence,run,generation,snapshot.modelRevision(),"observer",SOURCE_ID,sequence,snapshot.captureEndedAt(),
                RuntimeEventKind.CHANGED,fact.kind(),fact.projectionStatus(),fact.id(),null,"","",Map.of(),fact.values(),new SourceWatermark(SOURCE_ID,sequence),fact.completeness(),fact.evidence()));
    }
    public static Capability schemaCapability(){return new Capability(CAPABILITY,CapabilityStatus.COMPLETE,List.of(),"schema="+VERSION+"; evidence only; each kind/scope/time retains its own quality");}
    private static void validateData(Kind kind,Map<String,Object> data,Completeness quality,SourceTime time) {
        switch(kind) {
            case ARTIFACT_FIELD_CUT -> {
                keys(data,Set.of("artifactIdentity","classHash","fieldCutId","properties","fields","coherence","probeEvidence"));
                required(data,"artifactIdentity","fieldCutId","coherence");hash(data,"classHash");object(data.get("properties"));object(data.get("fields"));object(data.get("probeEvidence"));
                if(!Set.of("LOCKED_ARTIFACT","UNAVAILABLE").contains(text(data,"coherence"))||quality==Completeness.COMPLETE&&!text(data,"coherence").equals("LOCKED_ARTIFACT"))throw invalid("FIELD_CUT_COHERENCE");
            }
            case JASON_STEP -> {
                keys(data,Set.of("agentIdentity","goalInvocation","stepInvocation","sourceStepId","phase","artifactIdentity","schemeIdentity","operationInvocation","bindings"));
                required(data,"agentIdentity","goalInvocation","stepInvocation","sourceStepId","phase");
                for(String name:List.of("artifactIdentity","schemeIdentity","operationInvocation"))text(data,name);object(data.get("bindings"));
                if(!Set.of("ENABLED","SUCCEEDED","FAILED").contains(text(data,"phase")))throw invalid("STEP_PHASE");
            }
            case GOAL_TRANSITION -> {
                keys(data,Set.of("boardIdentity","goalDefinition","previousState","state","sourceCutId","goalEvidence"));
                required(data,"boardIdentity","goalDefinition","state","sourceCutId");text(data,"previousState");object(data.get("goalEvidence"));
                if(quality==Completeness.COMPLETE&&(text(data,"previousState").isEmpty()||!time.status().equals("OBSERVED")))throw invalid("TRANSITION_TIME_OR_BASELINE_UNAVAILABLE");
            }
            case NPL_LIFECYCLE -> {
                keys(data,Set.of("boardIdentity","activationKey","state","origin","norm","identityStatus","sourceObservation"));
                required(data,"boardIdentity","state","origin","identityStatus");text(data,"activationKey");object(data.get("norm"));object(data.get("sourceObservation"));
                if(!Set.of("ACTIVE","FULFILLED","UNFULFILLED","INACTIVE").contains(text(data,"state")))throw invalid("NPL_STATE");
                if(quality==Completeness.COMPLETE&&text(data,"activationKey").isEmpty())throw invalid("NPL_ACTIVATION_UNAVAILABLE");
            }
            case CLOCK -> {keys(data,Set.of("clockIdentity","sourceProof"));required(data,"clockIdentity");object(data.get("sourceProof"));
                if(time.status().equals("OBSERVED")&&!text(data,"clockIdentity").equals(time.clockId()))throw invalid("CLOCK_IDENTITY");
                if(quality==Completeness.COMPLETE&&!time.status().equals("OBSERVED"))throw invalid("CLOCK_SOURCE_TIME_UNAVAILABLE");}
        }
    }
    private static void hash(Map<String,Object> map,String key){if(!text(map,key).matches("[0-9a-f]{64}"))throw invalid("HASH:"+key);}
    private static void required(Map<String,Object> map,String...names){for(String name:names)if(text(map,name).isBlank())throw invalid("TEXT_REQUIRED:"+name);}
    private static Map<String,Object> object(Object value){if(!(value instanceof Map<?,?>))throw invalid("OBJECT_REQUIRED");return CanonicalJson.object(value);}
    private static String text(Map<String,Object> map,String key){if(!(map.get(key) instanceof String value))throw invalid("TEXT_REQUIRED:"+key);bounded(value);return value;}
    private static void bounded(String value){if(value==null||value.length()>16384)throw invalid("TEXT_CAPACITY");}
    private static long integer(Object value) {try {if(!(value instanceof Number))throw invalid("INTEGER_REQUIRED");return new BigDecimal(value.toString()).longValueExact();}catch(NumberFormatException|ArithmeticException error){throw invalid("INTEGER_REQUIRED");}}
    private static void keys(Map<String,Object> map,Set<String> expected){if(!map.keySet().equals(expected))throw invalid("FIELDS_UNSUPPORTED:"+expected);}
    private static ContractException invalid(String reason){return new ContractException("VERIFICATION_OBSERVATION_"+reason);}
}
