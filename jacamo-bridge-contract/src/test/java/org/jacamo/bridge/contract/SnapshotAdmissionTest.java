package org.jacamo.bridge.contract;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;import java.time.Instant;
import org.junit.jupiter.api.Test;
class SnapshotAdmissionTest {
    RuntimeSnapshot snapshot(Map<String,SourceWatermark> start,Map<String,SourceWatermark> end){return new RuntimeSnapshot("cut","revision",Instant.EPOCH,Instant.EPOCH,start,end,1,List.of(),Map.of(),"cut");}
    Map<String,SourceWatermark> wm(long n){return Map.of("source",new SourceWatermark("source",n));}
    @Test void staleRegressingMissingAndMislabelledVectorsAreRejected() {
        assertThrows(ContractException.class,()->SnapshotAdmission.require(snapshot(wm(0),wm(0)),Map.of("source",1L)));
        assertThrows(ContractException.class,()->SnapshotAdmission.require(snapshot(wm(2),wm(1)),Map.of()));
        assertThrows(ContractException.class,()->SnapshotAdmission.require(snapshot(Map.of(),Map.of()),Map.of("source",0L)));
        assertThrows(ContractException.class,()->SnapshotAdmission.require(snapshot(wm(0),Map.of()),Map.of()));
        assertThrows(ContractException.class,()->SnapshotAdmission.require(snapshot(wm(0),Map.of("source",new SourceWatermark("different",1))),Map.of()));
    }
    @Test void equalAndNewerCutsAreValidAndNewEpochHasIndependentPrefixes() {
        assertDoesNotThrow(()->SnapshotAdmission.require(snapshot(wm(1),wm(1)),Map.of("source",1L)));
        assertDoesNotThrow(()->SnapshotAdmission.require(snapshot(wm(2),wm(2)),Map.of("source",1L)));
        assertDoesNotThrow(()->SnapshotAdmission.require(snapshot(wm(0),wm(0)),Map.of()));
    }
}
