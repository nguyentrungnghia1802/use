package org.tzi.use.plugins.jacamo.semantics.mapping.xml.runtime;

import java.util.*;
import java.nio.charset.StandardCharsets;
import org.jacamo.bridge.contract.CanonicalJson;

/** Exact physical identities from admitted relational joins, never attribute-name matching. */
public final class SourceJoinOwnership {
    private SourceJoinOwnership(){}
    public static String encode(Map<String,String> proofs){
        var identities=new TreeSet<String>();proofs.forEach((key,value)->{if(key.startsWith("join:")&&key.endsWith(":identity")&&!value.isBlank())identities.add(value);});
        return new String(CanonicalJson.encode(List.copyOf(identities)),StandardCharsets.UTF_8);
    }
    public static Set<String> decode(String value){
        if(value==null||value.isBlank())return Set.of();
        var result=new TreeSet<String>();for(Object item:(List<?>)CanonicalJson.decode(value.getBytes(StandardCharsets.UTF_8)))result.add((String)item);return Set.copyOf(result);
    }
}
