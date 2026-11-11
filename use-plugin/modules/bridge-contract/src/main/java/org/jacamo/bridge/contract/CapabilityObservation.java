package org.jacamo.bridge.contract;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Request/epoch-owned capability receipt. Its data still needs the framework consumer's proof checks. */
public record CapabilityObservation(String requestId,String requestHash,String provider,Scope scope,
        Completeness quality,String diagnostic,Map<String,Object> data) {
    public static final String KEY="capabilityObservations",VERSION="1.0";
    private static final int MAX_BYTES=128*1024;
    public record Scope(String run,long generation,String revision,String sourceId,long sourceSequence) {
        public Scope {
            for(String value:List.of(run,revision,sourceId))if(value.isBlank()||value.length()>8192)throw invalid("SCOPE_TEXT_REQUIRED");
            if(generation<0||sourceSequence<0)throw invalid("SCOPE_SEQUENCE_INVALID");
        }
        public Map<String,Object> payload(){return Map.of("run",run,"generation",generation,"revision",revision,"sourceId",sourceId,"sourceSequence",sourceSequence);}
    }
    public CapabilityObservation {
        if(requestId==null||!requestId.matches("[A-Za-z_][A-Za-z0-9_.-]{0,95}")||provider==null||!provider.matches("[a-z][a-z0-9_.-]{1,95}")||requestHash==null||!requestHash.matches("[0-9a-f]{64}"))throw invalid("REQUEST_IDENTITY_INVALID");
        Objects.requireNonNull(scope);Objects.requireNonNull(quality);
        if(diagnostic==null||diagnostic.length()>8192||quality==Completeness.COMPLETE&&!diagnostic.isEmpty()||quality!=Completeness.COMPLETE&&diagnostic.isBlank())throw invalid("QUALITY_DIAGNOSTIC_INVALID");
        data=CanonicalJson.object(CanonicalJson.decode(CanonicalJson.encode(Objects.requireNonNull(data),MAX_BYTES),MAX_BYTES,16,32768));
    }
    public static CapabilityObservation of(ObservationCapabilityRequest request,Scope scope,Completeness quality,String diagnostic,Map<String,Object> data) {
        return new CapabilityObservation(request.id(),requestHash(request),request.provider(),scope,quality,diagnostic,data);
    }
    public static String requestHash(ObservationCapabilityRequest request){return ContractSupport.sha256(new String(CanonicalJson.encode(request.evidence()),StandardCharsets.UTF_8));}
    public Map<String,Object> payload(){return Map.of("version",VERSION,"requestId",requestId,"requestHash",requestHash,"provider",provider,"scope",scope.payload(),"quality",quality.name(),"diagnostic",diagnostic,"data",data);}
    public static CapabilityObservation require(Object raw,ObservationCapabilityRequest request) {
        var value=CanonicalJson.object(raw);keys(value,Set.of("version","requestId","requestHash","provider","scope","quality","diagnostic","data"));
        if(!VERSION.equals(value.get("version"))||!request.id().equals(value.get("requestId"))||!request.provider().equals(value.get("provider"))||!requestHash(request).equals(value.get("requestHash")))throw invalid("REQUEST_OWNERSHIP_MISMATCH");
        var scope=CanonicalJson.object(value.get("scope"));keys(scope,Set.of("run","generation","revision","sourceId","sourceSequence"));
        Completeness quality;try{quality=Completeness.valueOf(text(value,"quality"));}catch(IllegalArgumentException e){throw invalid("QUALITY_UNSUPPORTED");}
        return new CapabilityObservation(text(value,"requestId"),text(value,"requestHash"),text(value,"provider"),
            new Scope(text(scope,"run"),integer(scope.get("generation")),text(scope,"revision"),text(scope,"sourceId"),integer(scope.get("sourceSequence"))),quality,text(value,"diagnostic"),CanonicalJson.object(value.get("data")));
    }
    private static void keys(Map<String,Object> value,Set<String> expected){if(!value.keySet().equals(expected))throw invalid("KEYS_INVALID");}
    private static String text(Map<String,Object> value,String name){if(!(value.get(name) instanceof String text))throw invalid("TEXT_REQUIRED:"+name);return text;}
    private static long integer(Object value){try{if(!(value instanceof Number))throw invalid("INTEGER_REQUIRED");return new BigDecimal(value.toString()).longValueExact();}catch(NumberFormatException|ArithmeticException e){throw invalid("INTEGER_REQUIRED");}}
    private static ContractException invalid(String reason){return new ContractException("CAPABILITY_OBSERVATION:"+reason);}
}
