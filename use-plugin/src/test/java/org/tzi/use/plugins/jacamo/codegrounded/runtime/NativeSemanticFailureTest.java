package org.tzi.use.plugins.jacamo.codegrounded.runtime;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.jacamo.bridge.contract.Completeness;
import org.tzi.use.api.UseModelApi;
import org.tzi.use.plugins.jacamo.semantics.evidence.*;

class NativeSemanticFailureTest {
    @Test void lostJournalPreservesSevenValuedEvidenceButCannotKeepCurrentPass()throws Exception {
        var f=new NativeInvocationSemanticsTest().fixture();new UseModelApi(f.engine().system().model()).createInvariant("Nonnegative","Counter","self.p >= 0",false);var directory=Files.createTempDirectory(Path.of("target"),"semantic-failure-");
        try(var coordinator=new RuntimeVerificationCoordinator(f.engine(),"run",1,"revision",directory,16,1024*1024)) {
            coordinator.acceptedSnapshot("run",1,"revision",Map.of("cartago",Completeness.COMPLETE));var passed=coordinator.manualVerify();assertTrue(passed.semantic().constraints().stream().anyMatch(r->r.kind().equals("INVARIANT") && r.verdict()==SemanticVerdict.PASS));
            var journal=coordinator.journal().path();Files.delete(journal);Files.createDirectory(journal);var stale=coordinator.manualVerify();assertEquals("STALE",stale.freshness());assertNotNull(stale.semantic());assertTrue(coordinator.journal().hasGap());assertTrue(stale.semantic().constraints().stream().allMatch(r->r.verdict()==SemanticVerdict.SKIPPED || r.verdict()==SemanticVerdict.UNSUPPORTED));assertTrue(stale.semantic().constraints().stream().anyMatch(r->r.witness().getOrDefault("lastObservedVerdict","").equals("PASS")));assertEquals(stale,RuntimeVerificationResult.fromMap(stale.toMap()));assertEquals("0",f.engine().system().state().objectByName("counter").state(f.engine().system().state()).attributeValue("p").toString());
        }
    }
    @Test void unexpectedSemanticEvaluationErrorPublishesTypedStaleEvidenceInsteadOfKeepingAnOldPass()throws Exception {
        var f=new NativeInvocationSemanticsTest().fixture();new UseModelApi(f.engine().system().model()).createInvariant("Nonnegative","Counter","self.p >= 0",false);
        try(var coordinator=new RuntimeVerificationCoordinator(f.engine(),"run",1,"revision")) {
            coordinator.acceptedSnapshot("run",1,"revision",Map.of("cartago",Completeness.COMPLETE));var previous=coordinator.manualVerify();var foreign=new UseModelApi("foreign");foreign.createClass("Counter",false);foreign.createAttribute("Counter","p","Integer");foreign.createInvariant("ForeignModelInvariant","Counter","self.p >= 0",false);f.engine().system().model().addClassInvariant(foreign.getModel().classInvariants().iterator().next());
            var failed=assertDoesNotThrow(coordinator::manualVerify);assertEquals("STALE",failed.freshness());assertTrue(failed.diagnostic().contains("CONSTRAINT_CONTEXT_MODEL_MISMATCH"),failed.diagnostic());assertTrue(failed.semantic().constraints().stream().anyMatch(r->r.verdict()==SemanticVerdict.ERROR && r.kind().equals("EVALUATION_ERROR")));assertFalse(failed.semantic().constraints().stream().anyMatch(r->r.verdict()==SemanticVerdict.PASS));assertTrue(failed.semantic().constraints().stream().anyMatch(r->r.witness().getOrDefault("lastObservedVerdict","").equals("PASS")));assertEquals(previous.stateHash(),failed.stateHash());assertNotNull(coordinator.verificationSnapshot());assertEquals(failed,RuntimeVerificationResult.fromMap(failed.toMap()));
        }
    }
    @Test void failingViewObserverCannotRollBackAlreadyCommittedNativeStateOrCorruptItsResult()throws Exception {
        var f=new NativeInvocationSemanticsTest().fixture();new UseModelApi(f.engine().system().model()).createInvariant("Nonnegative","Counter","self.p >= 0",false);
        try(var coordinator=new RuntimeVerificationCoordinator(f.engine(),"run",1,"revision")) {coordinator.acceptedSnapshot("run",1,"revision",Map.of("cartago",Completeness.COMPLETE));coordinator.snapshotObserver(s->{throw new IllegalStateException("fixture view failure");});var result=coordinator.manualVerify();assertEquals("CURRENT_OBSERVED",result.freshness());assertFalse(coordinator.journal().hasGap());assertTrue(coordinator.observerDiagnostic().startsWith("VIEW_NOTIFICATION_FAILED"));assertTrue(result.semantic().constraints().stream().anyMatch(r->r.kind().equals("INVARIANT") && r.verdict()==SemanticVerdict.PASS));}
    }
}
