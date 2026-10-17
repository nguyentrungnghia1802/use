package org.tzi.use.plugins.jacamo.semantics.evidence;

import java.util.*;
import org.jacamo.bridge.contract.CanonicalJson;
import org.tzi.use.plugins.jacamo.semantics.mapping.MappingRuleCatalog;

/** Loss/source ownership record. Parsed contract preservation alone never promises Java/ASL byte reconstruction. */
public record TraceComplement(String version,String ruleVersion,String sourceHash,String metamodelHash,
        List<Asset> assets,List<Decision> decisions) {
    public record Asset(String id,String format,String sha256,String contentBase64) {
        public Asset{if(!org.tzi.use.plugins.jacamo.codegrounded.constraint.ExternalOclConstraintService.sha256(Base64.getDecoder().decode(contentBase64)).equals(sha256))throw new IllegalArgumentException("TRACE_ASSET_DIGEST_MISMATCH:"+id);}
        public static Asset of(String id,String format,byte[] bytes){return new Asset(id,format,org.tzi.use.plugins.jacamo.codegrounded.constraint.ExternalOclConstraintService.sha256(bytes),Base64.getEncoder().encodeToString(bytes));}
        public byte[] bytes(){return Base64.getDecoder().decode(contentBase64);}
    }
    public record Decision(String sourceId,String feature,String ruleId,MappingRuleCatalog.Disposition disposition,
            List<String> targets,String reason,Object preservedValue,String origin) {
        public Decision{targets=List.copyOf(targets);preservedValue=SemanticJson.freeze(preservedValue);}
    }
    public TraceComplement {
        if(!"1.0".equals(version) || !MappingRuleCatalog.VERSION.equals(ruleVersion))throw new IllegalArgumentException("TRACE_COMPLEMENT_VERSION_UNSUPPORTED");
        assets=List.copyOf(assets);decisions=List.copyOf(decisions);
        var ids=new HashSet<String>();for(var asset:assets)if(!ids.add(asset.id()))throw new IllegalArgumentException("TRACE_ASSET_ID_COLLISION");
        var fields=new HashSet<List<String>>();for(var decision:decisions)if(!fields.add(List.of(decision.sourceId(),decision.feature())))throw new IllegalArgumentException("TRACE_FEATURE_DECISION_DUPLICATE");
    }
}
