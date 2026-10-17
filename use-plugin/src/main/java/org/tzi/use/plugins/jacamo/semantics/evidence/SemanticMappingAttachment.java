package org.tzi.use.plugins.jacamo.semantics.evidence;

import java.util.*;
import org.tzi.use.uml.mm.*;
import org.tzi.use.plugins.jacamo.semantics.mapping.MappingBundle;
import org.tzi.use.plugins.jacamo.semantics.csl.CslUseBinding;

/** Immutable static provenance on the same model; exported annotations survive native replay. */
public final class SemanticMappingAttachment {
    private static final String KEY="SemanticMappingBundle";public static final int MAX_BYTES=8*1024*1024;
    private SemanticMappingAttachment(){}
    public static void attach(MModel model,MappingBundle bundle) {
        if(!CslUseBinding.active(model) || !Arrays.equals(CslUseBinding.configuration(model).bytes(),bundle.configuration().bytes()))throw new IllegalArgumentException("MAPPING_ATTACHMENT_CONFIGURATION_MISMATCH");
        byte[] bytes=new MappingBundleCodec().encode(bundle);if(bytes.length>MAX_BYTES)throw new IllegalArgumentException("MAPPING_ATTACHMENT_TOO_LARGE");
        if(model.getAnnotation(KEY)!=null)throw new IllegalStateException("MAPPING_ATTACHMENT_ALREADY_PRESENT");
        model.addAnnotation(new MElementAnnotation(KEY,Map.of("version","1.0","hash",org.tzi.use.plugins.jacamo.codegrounded.constraint.ExternalOclConstraintService.sha256(bytes),"payload64",Base64.getEncoder().encodeToString(bytes))));
    }
    public static Optional<MappingBundle> read(MModel model) {
        if(model.getAnnotation(KEY)==null)return Optional.empty();
        String raw=model.getAnnotationValue(KEY,"payload64");if(!"1.0".equals(model.getAnnotationValue(KEY,"version")) || raw.length()>(MAX_BYTES*4L/3+8))throw new IllegalArgumentException("MAPPING_ATTACHMENT_INVALID");
        byte[] bytes=Base64.getDecoder().decode(raw);if(bytes.length>MAX_BYTES || !org.tzi.use.plugins.jacamo.codegrounded.constraint.ExternalOclConstraintService.sha256(bytes).equals(model.getAnnotationValue(KEY,"hash")))throw new IllegalArgumentException("MAPPING_ATTACHMENT_HASH_MISMATCH");
        var bundle=new MappingBundleCodec().decode(bytes);
        if(!Arrays.equals(CslUseBinding.configuration(model).bytes(),bundle.configuration().bytes()))throw new IllegalArgumentException("MAPPING_ATTACHMENT_CONFIGURATION_MISMATCH");return Optional.of(bundle);
    }
    public static Map<String,Object> provenance(MModel model) {
        return read(model).map(b->Map.<String,Object>of("bundleHash",model.getAnnotationValue(KEY,"hash"),"sourceHash",b.source().sourceHash(),"metamodelHash",b.complement().metamodelHash(),"ruleVersion",b.complement().ruleVersion())).orElse(Map.of());
    }
}
