package org.tzi.use.plugins.jacamo.bridge;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ShadowSemanticComparatorTest {
    @Test void outputIsDeterministicMutationSensitiveAndRequiresClassification() {
        var comparator=new ShadowSemanticComparator();
        var facts=new ArrayList<>(List.of(
                fact(ShadowSemanticComparator.Stage.OFFICIAL_SOURCE,"beid:v1:a","source-sha",1),
                fact(ShadowSemanticComparator.Stage.BRIDGE,"beid:v1:a","source-sha",2),
                fact(ShadowSemanticComparator.Stage.OFFICIAL_SOURCE,"beid:v1:b","source-sha",1)));
        var first=comparator.compare(ShadowSemanticComparator.Stage.OFFICIAL_SOURCE,ShadowSemanticComparator.Stage.BRIDGE,facts,d->d.canonicalId().contains("a")?ShadowSemanticComparator.Classification.ADAPTER_BUG:ShadowSemanticComparator.Classification.UNSUPPORTED_FACT);
        java.util.Collections.reverse(facts);
        var second=comparator.compare(ShadowSemanticComparator.Stage.OFFICIAL_SOURCE,ShadowSemanticComparator.Stage.BRIDGE,facts,d->d.canonicalId().contains("a")?ShadowSemanticComparator.Classification.ADAPTER_BUG:ShadowSemanticComparator.Classification.UNSUPPORTED_FACT);
        assertEquals(first,second);assertEquals(comparator.fingerprint(first),comparator.fingerprint(second));assertEquals(2,first.size());
        var corrected=new ArrayList<>(facts);corrected.removeIf(f->f.stage()==ShadowSemanticComparator.Stage.BRIDGE);corrected.add(fact(ShadowSemanticComparator.Stage.BRIDGE,"beid:v1:a","source-sha",1));
        var mutated=comparator.compare(ShadowSemanticComparator.Stage.OFFICIAL_SOURCE,ShadowSemanticComparator.Stage.BRIDGE,corrected,d->ShadowSemanticComparator.Classification.UNSUPPORTED_FACT);
        assertNotEquals(comparator.fingerprint(first),comparator.fingerprint(mutated));
        assertThrows(IllegalArgumentException.class,()->comparator.compare(ShadowSemanticComparator.Stage.OFFICIAL_SOURCE,ShadowSemanticComparator.Stage.BRIDGE,facts,d->null));
    }
    private ShadowSemanticComparator.Fact fact(ShadowSemanticComparator.Stage stage,String id,String provenance,int value){return new ShadowSemanticComparator.Fact(stage,id,provenance,Map.of("value",value));}
}
