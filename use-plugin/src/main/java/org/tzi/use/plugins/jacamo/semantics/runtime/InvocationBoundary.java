package org.tzi.use.plugins.jacamo.semantics.runtime;

import java.util.*;
import org.jacamo.bridge.contract.*;

/** Official logger callback plus the consumed contiguous CArtAgO prefix, not a global MAS transaction. */
public record InvocationBoundary(String key,String correlation,String artifactUuid,String phase,long sourceSequence,long previousWatermark,String authority,Set<String> overlaps) {
    public InvocationBoundary {overlaps=Set.copyOf(overlaps);}
    public static String key(RuntimeEvent event){return event.sessionId()+":"+event.generation()+":"+event.entityId().canonical()+":"+event.correlationId();}
    public static InvocationBoundary from(RuntimeEvent event,Set<String> overlap) {
        if(!(event.after().get("invocationBoundary") instanceof Map<?,?>))throw new IllegalArgumentException("PRE_BOUNDARY_UNCONFIRMED");
        var proof=CanonicalJson.object(event.after().get("invocationBoundary"));
        if(!"1.0".equals(proof.get("version")) || !"ICARTAGOLOGGER_OBSERVED_PREFIX".equals(proof.get("authority")) || !event.sourceId().equals(proof.get("sourceId")) || !event.entityId().incarnation().equals(proof.get("artifactUuid")))throw new IllegalArgumentException("PRE_BOUNDARY_UNCONFIRMED");
        if(!(proof.get("previousWatermark") instanceof Number))throw new IllegalArgumentException("INVOCATION_WATERMARK_EVIDENCE_UNAVAILABLE");
        long previous=new java.math.BigDecimal(proof.get("previousWatermark").toString()).longValueExact();if(previous!=event.sourceSequence()-1)throw new IllegalArgumentException("INVOCATION_SOURCE_PREFIX_MISMATCH");
        return new InvocationBoundary(key(event),event.correlationId(),event.entityId().incarnation(),Objects.toString(event.after().get("phase"),""),event.sourceSequence(),previous,"ICARTAGOLOGGER_OBSERVED_PREFIX",overlap);
    }
}
