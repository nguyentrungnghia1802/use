package org.tzi.use.plugins.jacamo.semantics;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.tzi.use.plugins.jacamo.semantics.evidence.*;

class DetailedVerdictTest {
    @Test void portableEvidenceRetainsAllSevenOutcomesAndOriginalWitness() {
        var results=Arrays.stream(SemanticVerdict.values()).map(v->new SemanticConstraintResult(v.name(),"TEST","X","x","source:x","self.p > 0",v,"backend","reason",3,"op-1",Map.of("before.p","0","after.p","1"))).toList();
        var envelope=new SemanticVerificationEnvelope("1.0","CSL_OBSERVER_V1","configuration-digest","run:1:3",results,Map.of(),Map.of("cartago","PARTIAL"),List.of("explicit unsupported evidence"));
        assertEquals(envelope,SemanticVerificationEnvelope.fromMap(envelope.toMap()));
        assertEquals(7,new HashSet<>(envelope.constraints().stream().map(SemanticConstraintResult::verdict).toList()).size());
        assertEquals(org.tzi.use.plugins.jacamo.verification.VerificationOutcome.SKIPPED,results.stream().filter(r->r.verdict()==SemanticVerdict.UNDEFINED).findFirst().orElseThrow().legacy().outcome());
        assertThrows(UnsupportedOperationException.class,()->envelope.constraints().clear());
        var bad=new HashMap<>(envelope.toMap());bad.put("version","unsupported");assertThrows(IllegalArgumentException.class,()->SemanticVerificationEnvelope.fromMap(bad));
    }
}
