package org.tzi.use.plugins.jacamo.semantics.roundtrip;

import java.util.*;
import org.tzi.use.plugins.jacamo.semantics.evidence.SemanticJson;

/** Concrete reviewable offline artifact. No method writes to source or a running MAS. */
public record SourcePatchPlan(Status status,String baseDigest,String newDigest,String proposedXmiBase64,
        SupportedEditProfile rebasedProfile,Map<String,Map<String,Object>> normalizedTarget,List<Change> changes,List<String> diagnostics,String preservation) {
    public enum Status{APPLIED,CONFLICT,AMBIGUOUS,UNSUPPORTED}
    public record Change(String targetId,String sourceFragment,String field,Object before,Object after){public Change{before=SemanticJson.freeze(before);after=SemanticJson.freeze(after);}}
    public SourcePatchPlan{var copied=new TreeMap<String,Map<String,Object>>();normalizedTarget.forEach((id,fields)->copied.put(id,SemanticJson.object(fields)));normalizedTarget=Collections.unmodifiableMap(copied);changes=List.copyOf(changes);diagnostics=List.copyOf(diagnostics);}
    public byte[] proposedBytes(){return Base64.getDecoder().decode(proposedXmiBase64);}
    public byte[] portableBytes(){return org.jacamo.bridge.contract.CanonicalJson.encode(SemanticJson.tree(this));}
}
