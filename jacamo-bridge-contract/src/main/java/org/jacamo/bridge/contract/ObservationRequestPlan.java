package org.jacamo.bridge.contract;

import java.nio.file.*;
import java.util.*;

/** Frozen consumer requirements handed to the owning producer before startAgs, never domain commands. */
public record ObservationRequestPlan(String session,long generation,String revision,String programHash,
        List<ObservationCapabilityRequest> requests,Map<String,List<String>> sourceFields,Set<RuntimeFactKind> cartagoCaptureKinds,
        ObservationCaptureScope captureScope) {
    public static final String PROPERTY="jacamo.bridge.observation.plan";
    private static final String VERSION="OBSERVATION_REQUEST_PLAN/1.1";
    private static final String SELECTIVE_VERSION="OBSERVATION_REQUEST_PLAN/1.2";
    private static final int MAX_BYTES=512*1024;
    public ObservationRequestPlan {
        if(session==null||session.isBlank()||revision==null||revision.isBlank()||generation<1||programHash==null||!programHash.matches("[0-9a-f]{64}"))throw bad("OWNER_REQUIRED");
        requests=List.copyOf(requests);
        if(requests.size()>64||requests.stream().map(ObservationCapabilityRequest::id).distinct().count()!=requests.size())throw bad("REQUEST_CAPACITY_OR_DUPLICATE");
        if(sourceFields.size()>8)throw bad("SOURCE_FIELD_CAPACITY");
        var fields=new TreeMap<String,List<String>>();sourceFields.forEach((source,names)->{
            if(!source.matches("[a-z][a-z0-9_.:-]{0,95}")||names.isEmpty()||names.size()>64||new HashSet<>(names).size()!=names.size()||names.stream().anyMatch(n->!n.matches("[A-Za-z_][A-Za-z0-9_]{0,95}")))throw bad("SOURCE_FIELDS_INVALID");
            fields.put(source,List.copyOf(names));
        });sourceFields=Collections.unmodifiableMap(fields);
        cartagoCaptureKinds=Set.copyOf(cartagoCaptureKinds);if(cartagoCaptureKinds.isEmpty())throw bad("CAPTURE_KINDS_REQUIRED");
    }
    public ObservationRequestPlan(String session,long generation,String revision,String programHash,List<ObservationCapabilityRequest> requests,Map<String,List<String>> sourceFields,Set<RuntimeFactKind> kinds){this(session,generation,revision,programHash,requests,sourceFields,kinds,null);}
    public ObservationRequestPlan(String session,long generation,String revision,String programHash,List<ObservationCapabilityRequest> requests,Map<String,List<String>> sourceFields){this(session,generation,revision,programHash,requests,sourceFields,Set.of(RuntimeFactKind.values()));}
    public Map<String,Object> payload(){var value=new TreeMap<String,Object>(Map.of("version",captureScope==null?VERSION:SELECTIVE_VERSION,"session",session,"generation",generation,"revision",revision,"programHash",programHash,"requests",requests.stream().map(ObservationCapabilityRequest::evidence).toList(),"sourceFields",sourceFields,"cartagoCaptureKinds",cartagoCaptureKinds.stream().map(Enum::name).sorted().toList()));if(captureScope!=null){value.put("captureScope",captureScope.evidence());value.put("captureScopeHash",captureScope.hash());}return Collections.unmodifiableMap(value);}
    public static void publish(Path file,String session,long generation,String revision,String hash,List<ObservationCapabilityRequest> requests) {
        publish(file,session,generation,revision,hash,requests,Map.of());
    }
    public static void publish(Path file,String session,long generation,String revision,String hash,List<ObservationCapabilityRequest> requests,Map<String,List<String>> sourceFields) {
        publish(file,session,generation,revision,hash,requests,sourceFields,Set.of(RuntimeFactKind.values()));
    }
    public static void publish(Path file,String session,long generation,String revision,String hash,List<ObservationCapabilityRequest> requests,Map<String,List<String>> sourceFields,Set<RuntimeFactKind> kinds) {
        publish(file,session,generation,revision,hash,requests,sourceFields,kinds,null);
    }
    public static void publish(Path file,String session,long generation,String revision,String hash,List<ObservationCapabilityRequest> requests,Map<String,List<String>> sourceFields,Set<RuntimeFactKind> kinds,ObservationCaptureScope scope) {
        byte[] bytes=CanonicalJson.encode(new ObservationRequestPlan(session,generation,revision,hash,requests,sourceFields,kinds,scope).payload(),MAX_BYTES);
        if(bytes.length>MAX_BYTES)throw bad("BYTE_CAPACITY");
        file=file.toAbsolutePath().normalize();
        try {
            if(!Files.isDirectory(file.getParent(),LinkOption.NOFOLLOW_LINKS)||Files.isSymbolicLink(file))throw bad("PATH_INVALID");
            Path temporary=Files.createTempFile(file.getParent(),"observation-",".tmp");
            try{Files.write(temporary,bytes);Files.move(temporary,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
            finally{Files.deleteIfExists(temporary);}
        }catch(java.io.IOException error){throw new IllegalArgumentException("OBSERVATION_PLAN_WRITE_FAILED",error);}
    }
    public static ObservationRequestPlan read(Path file,String session,long generation,String revision) {
        try {
            if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)||Files.size(file)>MAX_BYTES)throw bad("FILE_INVALID");
            var value=CanonicalJson.object(CanonicalJson.decode(Files.readAllBytes(file),MAX_BYTES,24,65536));
            var keys=new HashSet<>(Set.of("version","session","generation","revision","programHash","requests","sourceFields","cartagoCaptureKinds"));
            boolean selective=SELECTIVE_VERSION.equals(value.get("version"));
            if(selective)keys.addAll(Set.of("captureScope","captureScopeHash"));
            if(!value.keySet().equals(keys)||!selective&&!VERSION.equals(value.get("version")))throw bad("SHAPE_OR_VERSION");
            var scope=selective?ObservationCaptureScope.fromMap(CanonicalJson.object(value.get("captureScope"))):null;
            if(scope!=null&&!scope.hash().equals(value.get("captureScopeHash")))throw bad("CAPTURE_SCOPE_HASH_MISMATCH");
            if(!session.equals(value.get("session"))||!revision.equals(value.get("revision"))||!(value.get("generation") instanceof Number n)||new java.math.BigDecimal(n.toString()).longValueExact()!=generation)throw bad("EPOCH_MISMATCH");
            if(!(value.get("requests") instanceof List<?> raw)||raw.size()>64)throw bad("REQUESTS_INVALID");
            var requests=new ArrayList<ObservationCapabilityRequest>();
            for(Object item:raw) {
                var r=CanonicalJson.object(item);
                if(!r.keySet().equals(Set.of("format","id","provider","required","parameters"))||!"OBSERVATION_CAPABILITY/1.0".equals(r.get("format"))||!(r.get("required") instanceof Boolean required)||!(r.get("id") instanceof String id)||!(r.get("provider") instanceof String provider))throw bad("REQUEST_SHAPE");
                requests.add(new ObservationCapabilityRequest(id,provider,required,CanonicalJson.object(r.get("parameters"))));
            }
            if(!(value.get("programHash") instanceof String hash))throw bad("PROGRAM_HASH_REQUIRED");
            var sourceFields=new TreeMap<String,List<String>>();
            CanonicalJson.object(value.get("sourceFields")).forEach((source,rawFields)->{
                if(!(rawFields instanceof List<?> fields)||fields.stream().anyMatch(f->!(f instanceof String)))throw bad("SOURCE_FIELDS_INVALID");
                sourceFields.put(source,fields.stream().map(String.class::cast).toList());
            });
            if(!(value.get("cartagoCaptureKinds") instanceof List<?> kinds)||kinds.isEmpty()||new HashSet<>(kinds).size()!=kinds.size())throw bad("CAPTURE_KINDS_INVALID");
            var selected=EnumSet.noneOf(RuntimeFactKind.class);for(Object kind:kinds)selected.add(RuntimeFactKind.valueOf((String)kind));
            return new ObservationRequestPlan(session,generation,revision,hash,requests,sourceFields,selected,scope);
        }catch(java.io.IOException|ArithmeticException error){throw new IllegalArgumentException("OBSERVATION_PLAN_READ_FAILED",error);}
    }
    private static IllegalArgumentException bad(String reason){return new IllegalArgumentException("OBSERVATION_PLAN:"+reason);}
}
