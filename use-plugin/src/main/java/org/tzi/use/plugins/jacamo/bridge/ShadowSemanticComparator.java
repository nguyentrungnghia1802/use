package org.tzi.use.plugins.jacamo.bridge;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import org.jacamo.bridge.contract.CanonicalJson;

/** Deterministic shadow comparison by canonical identity/provenance, never display name. */
public final class ShadowSemanticComparator {
    public enum Stage { OFFICIAL_SOURCE, LEGACY, BRIDGE, V2_PLAN, USE_RESULT }
    public enum Classification { ADAPTER_BUG, LEGACY_LIMITATION, UNSUPPORTED_FACT, REPRESENTATION_LOSS, INTENTIONAL_CORRECTION }
    public record Fact(Stage stage, String canonicalId, String provenanceDigest, Map<String,Object> value) {
        public Fact {
            if(stage==null||canonicalId==null||canonicalId.isBlank()||provenanceDigest==null||provenanceDigest.isBlank())
                throw new IllegalArgumentException("shadow fact identity/provenance required");
            value=Map.copyOf(value);
        }
        String digest(){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(CanonicalJson.encode(value)));}catch(Exception e){throw new IllegalStateException(e);}}
    }
    public record Difference(Stage baseline, Stage candidate, String canonicalId, String baselineDigest,
                             String candidateDigest, String provenanceDigest, Classification classification) { }

    public List<Difference> compare(Stage baseline, Stage candidate, List<Fact> facts,
                                    Function<Difference,Classification> classifier) {
        var left=index(baseline,facts);var right=index(candidate,facts);var keys=new java.util.TreeSet<String>();keys.addAll(left.keySet());keys.addAll(right.keySet());
        var differences=new ArrayList<Difference>();
        for(String key:keys){Fact a=left.get(key),b=right.get(key);String ad=a==null?"MISSING":a.digest(),bd=b==null?"MISSING":b.digest();if(ad.equals(bd))continue;
            String provenance=a!=null?a.provenanceDigest():b.provenanceDigest();
            var draft=new Difference(baseline,candidate,key,ad,bd,provenance,Classification.ADAPTER_BUG);
            Classification classification=classifier.apply(draft);if(classification==null)throw new IllegalArgumentException("SHADOW_DIFF_UNCLASSIFIED:"+key);
            differences.add(new Difference(baseline,candidate,key,ad,bd,provenance,classification));
        }
        return differences.stream().sorted(Comparator.comparing(Difference::canonicalId)
                .thenComparing(d->d.baseline().name()).thenComparing(d->d.candidate().name())).toList();
    }
    public String fingerprint(List<Difference> differences){
        var rows=differences.stream().map(d->Map.<String,Object>of("baseline",d.baseline().name(),"candidate",d.candidate().name(),"id",d.canonicalId(),"baselineDigest",d.baselineDigest(),"candidateDigest",d.candidateDigest(),"provenance",d.provenanceDigest(),"classification",d.classification().name())).toList();
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(CanonicalJson.encode(rows)));}catch(Exception e){throw new IllegalStateException(e);}
    }
    private Map<String,Fact> index(Stage stage,List<Fact> facts){var result=new TreeMap<String,Fact>();for(Fact fact:facts)if(fact.stage()==stage&&result.put(fact.canonicalId()+"|"+fact.provenanceDigest(),fact)!=null)throw new IllegalArgumentException("SHADOW_DUPLICATE_FACT:"+fact.canonicalId());return result;}
}
