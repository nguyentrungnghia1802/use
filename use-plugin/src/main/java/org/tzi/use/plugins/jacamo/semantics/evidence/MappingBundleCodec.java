package org.tzi.use.plugins.jacamo.semantics.evidence;

import java.util.*;
import org.jacamo.bridge.contract.CanonicalJson;
import org.tzi.use.plugins.jacamo.semantics.mapping.MappingBundle;

/** Versioned static transformation evidence. This is not a runtime-state restoration format. */
public final class MappingBundleCodec {
    private static final com.fasterxml.jackson.databind.ObjectMapper JSON=new com.fasterxml.jackson.databind.ObjectMapper();
    public byte[] encode(MappingBundle bundle) {
        Object tree=SemanticJson.tree(bundle);return CanonicalJson.encode(Map.of("version","1.0","bundle",tree,"bundleHash",SemanticJson.hash(tree)));
    }
    public MappingBundle decode(byte[] bytes) {
        var envelope=CanonicalJson.object(CanonicalJson.decode(bytes));
        if(!"1.0".equals(envelope.get("version")) || !SemanticJson.hash(envelope.get("bundle")).equals(envelope.get("bundleHash")))throw new IllegalArgumentException("MAPPING_BUNDLE_VERSION_OR_HASH_MISMATCH");
        try{return JSON.readValue(CanonicalJson.encode(envelope.get("bundle")),MappingBundle.class);}
        catch(java.io.IOException e){throw new IllegalArgumentException("MAPPING_BUNDLE_INVALID",e);}
    }
}
