package org.tzi.use.plugins.jacamo.semantics.evidence;

import java.util.*;
import org.jacamo.bridge.contract.CanonicalJson;
import org.tzi.use.plugins.jacamo.codegrounded.constraint.ExternalOclConstraintService;

public final class SemanticJson {
    private static final com.fasterxml.jackson.databind.ObjectMapper JSON=new com.fasterxml.jackson.databind.ObjectMapper();
    private SemanticJson(){}
    /** Trusted in-memory records can contain a Base64 TraceComplement asset larger
     * than a single wire string. Keep the same total byte/depth bounds; this does
     * not change CanonicalJson.decode or any untrusted transport/config reader. */
    public static Object tree(Object record){try{return CanonicalJson.decode(JSON.writeValueAsBytes(record),CanonicalJson.DEFAULT_MAX_BYTES,CanonicalJson.DEFAULT_MAX_DEPTH,CanonicalJson.DEFAULT_MAX_BYTES);}catch(java.io.IOException e){throw new IllegalArgumentException("SEMANTIC_RECORD_ENCODING_FAILED",e);}}
    public static Object freeze(Object value) {
        if(value instanceof Map<?,?> map){var result=new TreeMap<String,Object>();map.forEach((k,v)->result.put((String)k,freeze(v)));return Collections.unmodifiableMap(result);}
        if(value instanceof List<?> list)return Collections.unmodifiableList(list.stream().map(SemanticJson::freeze).toList());
        if(value instanceof Number number) {
            // Untyped JSON numbers have one representation across EMF, contract and Jackson frontends.
            var canonical=new java.math.BigDecimal(number.toString()).stripTrailingZeros();
            return canonical.signum()==0?java.math.BigDecimal.ZERO:canonical;
        }
        if(value==null || value instanceof String || value instanceof Boolean)return value;
        throw new IllegalArgumentException("SEMANTIC_JSON_VALUE_UNSUPPORTED:"+value.getClass().getName());
    }
    public static Map<String,Object> object(Object value){return CanonicalJson.object(freeze(value));}
    public static String hash(Object value){return ExternalOclConstraintService.sha256(CanonicalJson.encode(value,CanonicalJson.DEFAULT_MAX_BYTES));}
}
