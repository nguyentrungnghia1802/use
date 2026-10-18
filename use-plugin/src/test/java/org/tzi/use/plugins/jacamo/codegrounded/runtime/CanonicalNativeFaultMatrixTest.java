package org.tzi.use.plugins.jacamo.codegrounded.runtime;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.tzi.use.api.UseSystemApi;
import org.tzi.use.plugins.jacamo.semantics.*;
import static org.junit.jupiter.api.Assertions.*;

class CanonicalNativeFaultMatrixTest {
    @Test void failuresBeforeCommitRestoreNativeBindingsHistoryAndFrozenCuts() throws Exception {
        for(String point:List.of("AFTER_NATIVE_ACTION","AFTER_NATIVE_VALIDATION"))try(var f=fixture()) {
            f.settled();var p=f.projector();var c=p.coordinator();var history=c.originalEngine().checkpoint().get(5,TimeUnit.SECONDS);var metadata=p.mutations().objectMetadata(f.counter());long version=c.verificationSnapshot().currentVersion();
            c.faultProbe(stage->{if(stage.equals(point))throw new IllegalStateException(point);});
            assertThrows(IllegalStateException.class,()->change(f,"rejected",7));f.settled();
            assertEquals("0",value(f));assertEquals(metadata,p.mutations().objectMetadata(f.counter()));assertEquals(version,c.verificationSnapshot().currentVersion());assertEquals(history.histories(),c.originalEngine().checkpoint().get(5,TimeUnit.SECONDS).histories());assertFalse(c.journal().hasGap());assertEquals("STALE",c.latest().freshness());
        }
    }
    @Test void rollbackFailureQuarantinesTheOwnerAndPreventsSubsequentMutation() throws Exception {
        try(var f=fixture()) {
            f.settled();var c=f.projector().coordinator();c.faultProbe(stage->{if(Set.of("AFTER_NATIVE_ACTION","BEFORE_NATIVE_ROLLBACK").contains(stage))throw new IllegalStateException(stage);});
            var failure=assertThrows(IllegalStateException.class,()->change(f,"quarantine",7));assertTrue(failure.getSuppressed().length>0);assertEquals("STALE",c.latest().freshness());assertEquals(true,c.originalEngine().metrics().get("closed"));
            assertEquals("NATIVE_RUNTIME_OWNER_QUARANTINED",assertThrows(IllegalStateException.class,()->change(f,"forbidden",8)).getMessage());assertEquals("7",value(f),"An incomplete rollback must be quarantined, never presented as restored");
        }
    }
    @Test void checkpointAndSnapshotPersistenceFailuresKeepNativeFactsAndNeverPublishCurrentSuccess() throws Exception {
        for(String point:List.of("BEFORE_CHECKPOINT","BEFORE_SNAPSHOT_FREEZE","BEFORE_SNAPSHOT_COMPLETE"))try(var f=fixture()) {
            f.settled();var c=f.projector().coordinator();c.faultProbe(stage->{if(stage.equals(point))throw new IllegalStateException(point);});
            assertDoesNotThrow(()->change(f,"accepted",1));f.settled();assertEquals("1",value(f));assertTrue(c.journal().hasGap());assertEquals("STALE",c.latest().freshness());assertEquals("INCOMPLETE",c.latest().coverage());
            assertTrue(c.snapshots().snapshots().stream().filter(s->s.result().eventId().equals("accepted")).allMatch(s->!s.result().freshness().equals("CURRENT_OBSERVED")));
        }
    }
    @Test void aViewFailureDoesNotRetryOrRollBackTheOriginalEngineBoundary() throws Exception {
        try(var f=fixture()) {
            f.settled();var c=f.projector().coordinator();var before=c.originalEngine().checkpoint().get(5,TimeUnit.SECONDS);c.snapshotObserver(cut->{throw new IllegalStateException("VIEW_FAULT");});
            change(f,"observed",1);f.settled();var after=c.originalEngine().checkpoint().get(5,TimeUnit.SECONDS);
            assertEquals("1",value(f));assertFalse(c.journal().hasGap());assertEquals("CURRENT_OBSERVED",c.latest().freshness());assertTrue(c.observerDiagnostic().startsWith("VIEW_NOTIFICATION_FAILED"));
            before.histories().forEach((key,image)->assertEquals(image.boundary()+1,after.histories().get(key).boundary()));
            assertEquals(1,c.history().stream().filter(r->r.eventId().equals("observed")&&r.semantic()!=null&&r.semantic().observation().get("originalEngine") instanceof Map<?,?> core&&"PINNED_NATIVE_CUT".equals(core.get("evaluated"))).count());
        }
    }
    private static CanonicalRuntimeFixture fixture() throws Exception{return new CanonicalRuntimeFixture(CslCounterArtifact.class,CanonicalGoalMappingTest::rules);}
    private static void change(CanonicalRuntimeFixture f,String id,int value){f.projector().coordinator().transaction("EVENT",id,"unit-fault",1,Instant.now(),Map.of(),true,()->{UseSystemApi.create(f.projector().system(),false).setAttributeValue(f.counter().name(),"p",Integer.toString(value));return new RuntimeVerificationCoordinator.Mutation<>(null,true);});}
    private static String value(CanonicalRuntimeFixture f){return f.projector().coordinator().read(()->f.counter().state(f.projector().system().state()).attributeValue("p").toString());}
}
