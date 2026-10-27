package org.jacamo.bridge.contract;

import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CartagoSourceCutTest {
    RuntimeEvent event(RuntimeSnapshot cut,String session,String version) {
        var values=new LinkedHashMap<>(CartagoSourceCut.payload(cut));values.put("sourceCutVersion",version);
        return new RuntimeEvent("cut","run",1,"revision","cartago","cartago",3,Instant.EPOCH,RuntimeEventKind.CHANGED,RuntimeFactKind.RUNTIME_EVENT,ProjectionStatus.EVIDENCE_ONLY,new BridgeEntityId("cartago","environment","source-cut","environment","capture",session),null,"","",Map.of(),values,new SourceWatermark("cartago",3),Completeness.COMPLETE,List.of());
    }
    RuntimeSnapshot cut(String authority,long watermark,Completeness quality) {
        var mark=new SourceWatermark("cartago",watermark);
        var fact=new RuntimeFact(new BridgeEntityId(authority,"environment","workspace","environment","main","physical"),RuntimeFactKind.WORKSPACE,Map.of("name","main"),List.of(),ProjectionStatus.MATERIALIZED_FAITHFULLY,Completeness.COMPLETE,List.of());
        return new RuntimeSnapshot("cut","revision",Instant.EPOCH,Instant.EPOCH,Map.of("cartago",mark),Map.of("cartago",mark),1,List.of(fact),Map.of("cartago",quality),"captured");
    }
    @Test void localCutRequiresExactEpochOwnerVersionWatermarkAndCompleteSourcePopulation() {
        var valid=cut("cartago",3,Completeness.COMPLETE);
        assertEquals(valid,CartagoSourceCut.require(event(valid,"run",CartagoSourceCut.VERSION)));
        assertThrows(ContractException.class,()->CartagoSourceCut.require(event(valid,"previous-run",CartagoSourceCut.VERSION)));
        assertThrows(ContractException.class,()->CartagoSourceCut.require(event(valid,"run","future")));
        assertThrows(ContractException.class,()->CartagoSourceCut.require(event(cut("cartago",2,Completeness.COMPLETE),"run",CartagoSourceCut.VERSION)));
        assertThrows(ContractException.class,()->CartagoSourceCut.require(event(cut("jason",3,Completeness.COMPLETE),"run",CartagoSourceCut.VERSION)));
        assertThrows(ContractException.class,()->CartagoSourceCut.require(event(cut("cartago",3,Completeness.PARTIAL),"run",CartagoSourceCut.VERSION)));
    }
    @Test void captureDeclaresExactlyWhichPopulationsItReplaces() {
        var all=cut("cartago",3,Completeness.COMPLETE);
        var scoped=new RuntimeSnapshot(all.snapshotId(),all.modelRevision(),all.captureStartedAt(),all.captureEndedAt(),all.startWatermarks(),all.endWatermarks(),1,all.facts(),all.sourceCompleteness(),all.stateFingerprint(),Map.of("capturedFactKinds",List.of("WORKSPACE")));
        assertEquals(Set.of(RuntimeFactKind.WORKSPACE),CartagoSourceCut.kinds(CartagoSourceCut.require(event(scoped,"run",CartagoSourceCut.VERSION))));
        var wrong=new RuntimeSnapshot(all.snapshotId(),all.modelRevision(),all.captureStartedAt(),all.captureEndedAt(),all.startWatermarks(),all.endWatermarks(),1,all.facts(),all.sourceCompleteness(),all.stateFingerprint(),Map.of("capturedFactKinds",List.of("ARTIFACT")));
        assertThrows(ContractException.class,()->CartagoSourceCut.require(event(wrong,"run",CartagoSourceCut.VERSION)));
    }
    @Test void supplementalOperationEvidenceDoesNotClaimOperationPopulationCompleteness() {
        var all=cut("cartago",3,Completeness.COMPLETE);var facts=new ArrayList<>(all.facts());
        facts.add(new RuntimeFact(new BridgeEntityId("cartago","environment","operation-descriptor","physical","bump","snapshot"),RuntimeFactKind.OPERATION,Map.of("semanticId","bump"),List.of(),ProjectionStatus.MATERIALIZED_FAITHFULLY,Completeness.COMPLETE,List.of()));
        var scoped=new RuntimeSnapshot(all.snapshotId(),all.modelRevision(),all.captureStartedAt(),all.captureEndedAt(),all.startWatermarks(),all.endWatermarks(),1,facts,all.sourceCompleteness(),all.stateFingerprint(),Map.of("capturedFactKinds",List.of("WORKSPACE"),"supplementalFactKinds",List.of("OPERATION")));
        assertEquals(scoped,CartagoSourceCut.require(event(scoped,"run",CartagoSourceCut.VERSION)));
        assertEquals(Set.of(RuntimeFactKind.WORKSPACE),CartagoSourceCut.kinds(scoped));
        assertEquals(Set.of(RuntimeFactKind.OPERATION),CartagoSourceCut.supplemental(scoped));
    }
}
