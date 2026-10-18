package org.tzi.use.plugins.jacamo.semantics;

import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import cartago.Op;
import org.junit.jupiter.api.Test;
import org.tzi.use.api.UseModelApi;
import org.tzi.use.plugins.jacamo.semantics.evidence.SemanticVerdict;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.RuntimeVerificationCoordinator;
import static org.junit.jupiter.api.Assertions.*;

class CanonicalTransactionFailureTest {
    @Test void manualEnactmentLinkEditCannotBeCertifiedAsAnAcceptedContextCast() throws Exception {
        try(var f=new CanonicalRuntimeFixture(CslCounterArtifact.class,CanonicalGoalMappingTest::rules)) {
            f.settled();f.target.set(null);var resolver=new org.tzi.use.plugins.jacamo.semantics.runtime.JaCaMoOccurrenceBindingAdapter();
            f.projector.coordinator().read(()->{
                var cast=resolver.resolve(f.program,f.projector.mutations()).getFirst();assertTrue(cast.complete());var worker=cast.actors().get("Worker");var reviewer=cast.actors().get("Reviewer");
                var role=f.projector.mutations().objectForSemanticId(worker.roleId());var alice=f.projector.mutations().objectForSemanticId(worker.playerId());var bob=f.projector.mutations().objectForSemanticId(reviewer.playerId());
                var api=org.tzi.use.api.UseSystemApi.create(f.projector.system(),false);api.deleteLink("Plays",new String[]{alice.name(),role.name()});api.createLink("Plays",bob.name(),role.name());
                assertFalse(resolver.resolve(f.program,f.projector.mutations()).getFirst().complete(),"Accepted metadata must agree with the native links");return null;
            });
        }
    }
    @Test void rejectedSnapshotDoesNotRebindEngineOrDiscardPreviousFrozenCuts() throws Exception {
        try(var f=new CanonicalRuntimeFixture(CslCounterArtifact.class,CanonicalGoalMappingTest::rules)) {
            f.settled();f.target.set(null);var c=f.projector.coordinator();var owner=c.originalEngine();var identity=owner.identity();var history=owner.checkpoint().get(5,TimeUnit.SECONDS);var retained=c.snapshots().snapshots();
            assertThrows(IllegalArgumentException.class,()->c.transaction("SNAPSHOT","rejected-new-run","test",0,Instant.now(),Map.of(),true,()->{
                c.acceptedSnapshot("never-accepted",2,f.model.modelRevision(),Map.of());throw new IllegalArgumentException("AFTER_STAGED_EPOCH_CHANGE");
            }));f.settled();
            assertEquals(identity,owner.identity());assertEquals(history.histories(),owner.checkpoint().get(5,TimeUnit.SECONDS).histories());assertTrue(c.snapshots().snapshots().stream().anyMatch(s->s.snapshotId().equals(retained.getLast().snapshotId())));assertEquals("0",f.value("p"));
        }
    }
    @Test void failurePreparingSemanticEvaluationAfterCommitKeepsNativeAndProtocolCutWithTypedError() throws Exception {
        try(var f=new CanonicalRuntimeFixture(CslCounterArtifact.class,CanonicalGoalMappingTest::rules)) {
            f.settled();f.target.set(null);var c=f.projector.coordinator();var previous=c.originalEngine().checkpoint().get(5,TimeUnit.SECONDS);
            var foreign=new UseModelApi("foreign");foreign.createClass("Counter",false);foreign.createAttribute("Counter","p","Integer");foreign.createInvariant("Foreign","Counter","self.p >= 0",false);
            c.read(()->{f.projector.system().model().addClassInvariant(foreign.getModel().classInvariants().iterator().next());return null;});
            f.alice.doAction(f.artifact,new Op("increment"));var snapshot=f.captures.capture(f.model.modelRevision(),3).snapshot();
            assertDoesNotThrow(()->f.projector.applySnapshot(snapshot));f.settled();assertEquals("1",f.value("p"));assertEquals(snapshot.snapshotId(),f.projector.snapshotId());assertEquals("STALE",c.latest().freshness());assertTrue(c.latest().semantic().constraints().stream().anyMatch(r->r.kind().equals("EVALUATION_ERROR")&&r.verdict()==SemanticVerdict.ERROR));
            assertEquals(previous.histories(),c.originalEngine().checkpoint().get(5,TimeUnit.SECONDS).histories());
        }
    }
    @Test void missingJournalCannotCertifyAcceptedFactsOrRemainReplayable() throws Exception {
        try(var f=new CanonicalRuntimeFixture(CslCounterArtifact.class,CanonicalGoalMappingTest::rules)) {
            f.settled();f.target.set(null);var c=f.projector.coordinator();var journal=c.journal().path();assertTrue(journal.toAbsolutePath().startsWith(c.directory()));Files.delete(journal);Files.createDirectory(journal);
            f.alice.doAction(f.artifact,new Op("increment"));var snapshot=f.captures.capture(f.model.modelRevision(),3).snapshot();assertDoesNotThrow(()->f.projector.applySnapshot(snapshot));f.settled();
            assertEquals("1",f.value("p"));assertEquals(snapshot.snapshotId(),f.projector.snapshotId());assertTrue(c.journal().hasGap());assertEquals("STALE",c.latest().freshness());assertEquals("INCOMPLETE",c.latest().coverage());
            assertThrows(IllegalStateException.class,()->new org.tzi.use.plugins.jacamo.codegrounded.runtime.NativeRuntimeReplay().exportBundle(f.projector,f.built.nativeResult().export().useText(),c.directory().resolve("replay")));
        }
    }
}
