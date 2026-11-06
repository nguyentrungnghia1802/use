package org.tzi.use.plugins.jacamo.codegrounded;

import static org.junit.jupiter.api.Assertions.*;
import static org.tzi.use.plugins.jacamo.codegrounded.RuntimeVerificationFixtures.*;
import org.junit.jupiter.api.Test;

class SnapshotRetentionTest {
    @Test void statusPollingAndExactCompletionReuseTheSameCheckedEncodedCost() throws Exception {
        try(var projector=projector()) {
            var original=projector.coordinator().verificationSnapshot();long encoded=original.estimatedBytes();
            var history=new org.tzi.use.plugins.jacamo.codegrounded.runtime.SnapshotRetention(4,16L*1024*1024);
            history.retain(original);long before=history.retainedBytes();
            var validated=new org.tzi.use.plugins.jacamo.codegrounded.runtime.VerificationSnapshot(original.currentVersion(),original.result(),original.profile(),original.metadata(),original.image());
            history.validateCompletion(validated);
            var published=new org.tzi.use.plugins.jacamo.codegrounded.runtime.VerificationSnapshot(original.currentVersion(),original.result(),original.profile(),original.metadata(),original.image());
            history.complete(published);
            for(int i=0;i<100;i++)assertEquals(before,history.retainedBytes());
            assertEquals(encoded,published.estimatedBytes());assertSame(published,history.snapshots().getFirst());
            var counter=history.getClass().getDeclaredField("sizingOperations");counter.setAccessible(true);assertEquals(1L,counter.getLong(history),"Immutable retained/pinned copies must not be re-encoded by status polling");
            history.clear();assertEquals(0,history.retainedBytes());
        }
    }

    @Test void changedImageOrResultCannotReuseThePreviousCutCostOrBypassTheBudget() throws Exception {
        try(var projector=projector()) {
            var original=projector.coordinator().verificationSnapshot();
            var history=new org.tzi.use.plugins.jacamo.codegrounded.runtime.SnapshotRetention(4,Math.max(1024,original.estimatedBytes()*2+100));history.retain(original);
            var large=new org.tzi.use.plugins.jacamo.codegrounded.runtime.VerificationSnapshot.StateImage(original.image().objects(),original.image().links(),original.image().soil()+"x".repeat(200000));
            var changed=new org.tzi.use.plugins.jacamo.codegrounded.runtime.VerificationSnapshot(original.currentVersion(),original.result(),original.profile(),original.metadata(),large);
            assertThrows(IllegalStateException.class,()->history.validateCompletion(changed));
            assertSame(original,history.snapshots().getFirst());assertEquals(original.result().resultHash(),history.snapshots().getFirst().result().resultHash());
        }
    }
    @Test void pinsFailurePreviousAndConfirmationAcrossTailEviction() throws Exception {
        try(var projector=projector()) {
            var coordinator=projector.coordinator();var history=coordinator.snapshots();
            var previous=coordinator.verificationSnapshot();
            projector.apply(delta("failing",1,"B"));var failing=coordinator.verificationSnapshot();history.pinFailure(failing.snapshotId());
            assertEquals(previous.snapshotId(),history.previousFailure().snapshotId());
            assertNotNull(history.passingBeforeFailure());
            for(int i=2;i<35;i++) projector.apply(delta("tail-"+i,i,"value-"+i));
            assertTrue(history.snapshots().size()<=8);assertFalse(history.snapshots().contains(failing));
            history.pinFailure(coordinator.verificationSnapshot().snapshotId());assertEquals(failing.snapshotId(),history.failure().snapshotId());
            history.pinConfirmation(coordinator.verificationSnapshot());assertNotEquals(history.failure().snapshotId(),history.confirmation().snapshotId());
            assertTrue(history.failure().image().soil().contains("'B'"));assertTrue(history.retainedBytes()<16L*1024*1024);
            assertThrows(IllegalArgumentException.class,()->history.pinConfirmation(failing));
            history.clear();assertTrue(history.snapshots().isEmpty());assertNull(history.failure());
            assertNull(history.previousFailure());assertNull(history.confirmation());assertEquals(0,history.retainedBytes());
        }
    }
}
