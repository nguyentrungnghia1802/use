package org.tzi.use.plugins.jacamo.semantics.mapping;

import java.util.Objects;
import org.tzi.use.plugins.jacamo.semantics.evidence.SemanticJson;

public record UnsupportedMappingDecision(String sourceId,String feature,String ruleId,boolean mandatory,String reason,Object preservedValue,String origin) {
    public UnsupportedMappingDecision {
        Objects.requireNonNull(sourceId);Objects.requireNonNull(feature);Objects.requireNonNull(ruleId);Objects.requireNonNull(reason);Objects.requireNonNull(origin);
        if(reason.isBlank())throw new IllegalArgumentException("UNSUPPORTED_MAPPING_REASON_REQUIRED");preservedValue=SemanticJson.freeze(preservedValue);
    }
    public MappingDiagnostic diagnostic(){return new MappingDiagnostic(MappingDiagnostic.Kind.UNSUPPORTED,"SOURCE_MAPPING_UNSUPPORTED",sourceId,"",ruleId,origin,0,reason);}
}
