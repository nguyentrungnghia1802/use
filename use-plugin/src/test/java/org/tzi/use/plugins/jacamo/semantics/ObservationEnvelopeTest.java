package org.tzi.use.plugins.jacamo.semantics;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.jacamo.bridge.contract.*;
import org.tzi.use.plugins.jacamo.semantics.runtime.*;

class ObservationEnvelopeTest {
    @Test void selectedBoardCompletenessDoesNotCertifyGlobalPopulationAndInputTreesCannotMutateEvidence() {
        var nested=new HashMap<String,Object>();nested.put("players",new ArrayList<>(List.of("a")));nested.put("continuity","POLLING_ONLY_SOURCE_LIFETIME_UNPROVEN");var id=new BridgeEntityId("moise","organisation","group-board","org","team","uuid");var fact=new RuntimeFact(id,RuntimeFactKind.GROUP_BOARD,nested,List.of(),ProjectionStatus.MATERIALIZED_FAITHFULLY,Completeness.COMPLETE,List.of());var now=java.time.Instant.EPOCH;var snapshot=new RuntimeSnapshot("s","model",now,now,Map.of(),Map.of(),1,List.of(fact),Map.of("moise:org",Completeness.PARTIAL),"fingerprint",Map.of("version","1.0","topologyStart",Map.of("moise:org","board-uuid")));
        var envelope=SemanticObservationEnvelope.from(snapshot,"run",1,"CSL_OBSERVER_V2","config");((List)nested.get("players")).clear();assertEquals(1,((List<?>)fact.values().get("players")).size());var coverage=envelope.scopes().getFirst();assertEquals(Completeness.COMPLETE,coverage.selectedFields());assertFalse(coverage.populationComplete());assertTrue(coverage.continuity().contains("UNPROVEN"));assertThrows(UnsupportedOperationException.class,()->((List)fact.values().get("players")).clear());assertThrows(UnsupportedOperationException.class,()->envelope.payload().clear());assertEquals(snapshot,ContractPayloads.runtime(envelope.payload()));
    }
}
