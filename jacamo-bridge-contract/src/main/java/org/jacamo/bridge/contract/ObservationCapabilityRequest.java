package org.jacamo.bridge.contract;
import java.util.*;

/** Versioned declarative request, not proof that the producer can observe anything. */
public record ObservationCapabilityRequest(String id,String provider,boolean required,Map<String,Object> parameters) {
    public ObservationCapabilityRequest {
        if(id==null||!id.matches("[A-Za-z_][A-Za-z0-9_.-]{0,95}")||provider==null||!provider.matches("[a-z][a-z0-9_.-]{1,95}"))throw new ContractException("OBSERVATION_CAPABILITY_ID_INVALID");
        Objects.requireNonNull(parameters);
        if(parameters.size()>32)throw new ContractException("OBSERVATION_CAPABILITY_PARAMETER_CAPACITY");
        parameters=CanonicalJson.object(CanonicalJson.decode(CanonicalJson.encode(parameters,64*1024),64*1024,16,8192));
    }
    public Map<String,Object> evidence(){return Map.of("format","OBSERVATION_CAPABILITY/1.0","id",id,"provider",provider,"required",required,"parameters",parameters);}
}
