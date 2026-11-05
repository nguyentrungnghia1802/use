package org.tzi.use.plugins.jacamo.codegrounded;
import static org.junit.jupiter.api.Assertions.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.jacamo.bridge.contract.VerifiedCheckpointProtocol;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.*;
import org.tzi.use.plugins.jacamo.codegrounded.constraint.ExternalOclConstraintService;
import org.tzi.use.plugins.jacamo.semantics.evidence.*;
import org.tzi.use.plugins.jacamo.verification.VerificationOutcome;

class VerifiedCheckpointInventoryTest {
    @Test void persistedPrefixEvictionIsNotMissingButRetainsActualFailedInvocation(@org.junit.jupiter.api.io.TempDir java.nio.file.Path directory)throws Exception {
        var current=cut(List.of(invariant(VerificationOutcome.PASS)),List.of());var journal=new RuntimeEventJournal(directory,2,4*1024*1024);
        var failed=cut(List.of(invariant(VerificationOutcome.PASS)),List.of(new SemanticConstraintResult("post","POST","Domain","x","source","self.valid",SemanticVerdict.FAIL,"false","original native failure",1,"invocation",Map.of())));
        assertTrue(journal.append("OPERATION_POST",Map.of(),failed.result()));for(int i=0;i<8;i++)assertTrue(journal.append("EVENT",Map.of(),current.result()));
        assertTrue(journal.retainedEntries().getFirst().ordinal()>0);
        var rows=VerifiedCheckpointInventory.evaluatePrefix(current,journal,0,journal.persistedEntries());assertEquals("FAIL",rows.get(VerifiedCheckpointProtocol.REQUIRED_CHECK).truth());assertFalse(rows.containsKey("native.invocation-prefix"));
        assertEquals("PASS",VerifiedCheckpointInventory.evaluatePrefix(current,journal,1,journal.persistedEntries()).get(VerifiedCheckpointProtocol.REQUIRED_CHECK).truth());
        java.nio.file.Files.writeString(journal.path(),"corrupted\n");assertThrows(RuntimeException.class,()->VerifiedCheckpointInventory.evaluatePrefix(current,journal,0,journal.persistedEntries()));
    }
    static VerificationSnapshot cut(List<ExternalOclConstraintService.Outcome> outcomes,List<SemanticConstraintResult> semanticRows) {
        return cut(outcomes,semanticRows,Map.of());
    }
    static VerificationSnapshot cut(List<ExternalOclConstraintService.Outcome> outcomes,List<SemanticConstraintResult> semanticRows,Map<String,Object> extra) {
        String soil="!create x : Domain\n";String hash=ExternalOclConstraintService.sha256(soil.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var observation=new TreeMap<String,Object>(extra);observation.putIfAbsent("requirementInventory",Map.of("version","NATIVE_CONTRACT_INVENTORY/1.0","requiredInvariants",List.of("NATIVE:Domain::Required")));
        var semantic=new SemanticVerificationEnvelope("1.0","native","configuration","boundary",semanticRows,Map.of(),Map.of("history","OBSERVED_PREFIX"),List.of(),observation);
        var result=new RuntimeVerificationResult("session",1,"revision",1,"event","source",1,Instant.EPOCH,Instant.EPOCH,Instant.EPOCH,"cut","constraints",hash,outcomes,0,"INCOMPLETE","CURRENT_OBSERVED","",semantic);
        return new VerificationSnapshot(1,result,null,null,new VerificationSnapshot.StateImage(Map.of(),List.of(),soil));
    }
    static ExternalOclConstraintService.Outcome invariant(VerificationOutcome outcome){return new ExternalOclConstraintService.Outcome("NATIVE:Domain::Required","Domain",outcome,"native expression evidence","self.value > 0");}
    @Test void declaredSettledScopeRequiresItsNativeRootsWhileOtherScopesRemainFuture() {
        String marker=VerifiedCheckpointProtocol.SETTLED_CHECK_PREFIX+"source:scheme-one";
        for(String status:List.of("FULFILLED","PENDING","UNKNOWN","VIOLATED","FAILED")) {
            var observation=Map.<String,Object>of("requirementInventory",Map.of("version","NATIVE_CONTRACT_INVENTORY/1.0","requiredInvariants",List.of("NATIVE:Domain::Required"),"selectedIntentionalElements",List.of("root")),
                    "originalEngine",Map.of("evaluated","PINNED_NATIVE_CUT","historyQuality","OBSERVED_PREFIX","contexts",List.of(
                            Map.of("scopeView",Map.of("bindings",Map.of("scheme","scheme-one")),"occurrences",Map.of("root-instance",Map.of("sourceElement","root","aggregateStatus",status,"quality","KNOWN_DERIVED"))),
                            Map.of("scopeView",Map.of("bindings",Map.of("scheme","scheme-two")),"occurrences",Map.of("other",Map.of("sourceElement","root","aggregateStatus","PENDING","quality","KNOWN_DERIVED"))))));
            var current=cut(List.of(invariant(VerificationOutcome.PASS)),List.of(),observation);String original=current.result().resultHash();
            var initial=VerifiedCheckpointInventory.evaluate(current);
            var settled=VerifiedCheckpointInventory.settled(current,initial,List.of(marker));
            assertEquals(status.equals("FULFILLED")?"PASS":Set.of("VIOLATED","FAILED").contains(status)?"FAIL":"UNKNOWN",settled.get(marker).truth());
            assertEquals(original,current.result().resultHash(),"Applicability cannot rewrite the original marking/result");
            String foreign=VerifiedCheckpointProtocol.SETTLED_CHECK_PREFIX+"source:foreign-scheme";
            assertEquals("UNKNOWN",VerifiedCheckpointInventory.settled(current,initial,List.of(foreign)).get(foreign).truth());
            if(status.equals("PENDING"))assertEquals("PASS",initial.get(VerifiedCheckpointProtocol.REQUIRED_CHECK).truth(),"Ordinary source boundaries still allow future progress");
        }
    }
    @Test void falseFutureGoalDoesNotMasqueradeAsAnApplicableFailure() {
        var future=new SemanticConstraintResult("goal","GOAL_CONDITION","Domain","x","source","self.completed",SemanticVerdict.FAIL,"false","future condition",1,"",Map.of());
        var result=VerifiedCheckpointInventory.evaluate(cut(List.of(invariant(VerificationOutcome.PASS)),List.of(future)));
        assertEquals("PASS",result.get(VerifiedCheckpointProtocol.REQUIRED_CHECK).truth());assertEquals("FUTURE",result.get("condition:goal").applicability());
    }
    @Test void settledHistoricalRootsRequireTheRuntimeOwnersCertificate() {
        String marker=VerifiedCheckpointProtocol.SETTLED_CHECK_PREFIX+"source:scheme";
        for(boolean certificate:List.of(false,true)) {
            var root=Map.<String,Object>of("sourceElement","root","aggregateStatus","FULFILLED","quality","DERIVED_INPUT_UNAVAILABLE",
                    "executionQuality","KNOWN_DERIVED_HISTORY","executionEvidence",certificate?Map.of("leaf",Map.of("policy","CERTIFIED_EXECUTION/1.0","stateHash","a".repeat(64))):Map.of());
            var observation=Map.<String,Object>of("requirementInventory",Map.of("version","NATIVE_CONTRACT_INVENTORY/1.0","requiredInvariants",List.of("NATIVE:Domain::Required"),"selectedIntentionalElements",List.of("root")),
                    "originalEngine",Map.of("evaluated","PINNED_NATIVE_CUT","historyQuality","OBSERVED_PREFIX","contexts",List.of(
                            Map.of("scopeView",Map.of("bindings",Map.of("scheme","scheme")),"occurrences",Map.of("root",root)))));
            var current=cut(List.of(invariant(VerificationOutcome.PASS)),List.of(),observation);
            assertEquals(certificate?"PASS":"UNKNOWN",VerifiedCheckpointInventory.settled(current,VerifiedCheckpointInventory.evaluate(current),List.of(marker)).get(marker).truth());
        }
    }
    @Test void requiredUnknownErrorMissingAndActualPostFailureRetainNativeMeaning() {
        for(var outcome:List.of(VerificationOutcome.SKIPPED,VerificationOutcome.ERROR))assertNotEquals("PASS",VerifiedCheckpointInventory.evaluate(cut(List.of(invariant(outcome)),List.of())).get(VerifiedCheckpointProtocol.REQUIRED_CHECK).truth());
        assertEquals("UNKNOWN",VerifiedCheckpointInventory.evaluate(cut(List.of(),List.of())).get(VerifiedCheckpointProtocol.REQUIRED_CHECK).truth());
        var post=new SemanticConstraintResult("post","TASK_POST","Domain","x","source","self.value > oldMax",SemanticVerdict.FAIL,"false","actual terminal postcondition",1,"invocation",Map.of());
        var result=VerifiedCheckpointInventory.evaluate(cut(List.of(invariant(VerificationOutcome.PASS)),List.of(post)));
        assertEquals("FAIL",result.get(VerifiedCheckpointProtocol.REQUIRED_CHECK).truth());assertEquals("FAIL",result.get("semantic:post:invocation").truth());
    }
    @Test void frozenSnapshotCannotEraseAnApplicableInvocationOutcomeFromItsAcceptedPrefix() {
        var current=cut(List.of(invariant(VerificationOutcome.PASS)),List.of());
        for(var verdict:List.of(SemanticVerdict.FAIL,SemanticVerdict.INCONCLUSIVE)) {
            var invocation=new SemanticConstraintResult("post","TASK_INVOCATION","Domain","x","source","",verdict,"","actual invocation evidence",1,"invocation",Map.of());
            var prior=cut(List.of(invariant(VerificationOutcome.PASS)),List.of(invocation));
            var history=List.of(new RuntimeHistoryPage.Entry(4,"TASK_POST","interval",prior.result(),true));
            assertNotEquals("PASS",VerifiedCheckpointInventory.evaluatePrefix(current,history,4,5).get(VerifiedCheckpointProtocol.REQUIRED_CHECK).truth());
            assertEquals("PASS",VerifiedCheckpointInventory.evaluatePrefix(current,history,5,5).get(VerifiedCheckpointProtocol.REQUIRED_CHECK).truth(),"Review advances the window while preserving the recorded failed result");
        }
        assertEquals("UNKNOWN",VerifiedCheckpointInventory.evaluatePrefix(current,List.of(),4,5).get(VerifiedCheckpointProtocol.REQUIRED_CHECK).truth());
        var readiness=new SemanticConstraintResult("cast","RUNTIME_BINDING","Domain","","","",SemanticVerdict.INCONCLUSIVE,"","future cast not available",1,"",Map.of());
        var earlier=cut(List.of(invariant(VerificationOutcome.PASS)),List.of(readiness));
        assertEquals("PASS",VerifiedCheckpointInventory.evaluatePrefix(current,List.of(new RuntimeHistoryPage.Entry(4,"ENGINE_RESULT","interval",earlier.result(),true)),4,5).get(VerifiedCheckpointProtocol.REQUIRED_CHECK).truth());
        assertEquals("UNKNOWN",VerifiedCheckpointInventory.evaluate(earlier).get(VerifiedCheckpointProtocol.REQUIRED_CHECK).truth(),"Current required readiness stays unknown");
        var nativePre=new SemanticConstraintResult("pre","PRE","Domain","x","source","self.running",SemanticVerdict.FAIL,"false","native declared operation PRE",1,"operation",Map.of());
        var operation=cut(List.of(invariant(VerificationOutcome.PASS)),List.of(nativePre));
        assertEquals("FAIL",VerifiedCheckpointInventory.evaluatePrefix(current,List.of(new RuntimeHistoryPage.Entry(4,"OPERATION_PRE","interval",operation.result(),true)),4,5).get(VerifiedCheckpointProtocol.REQUIRED_CHECK).truth());
        assertEquals("PASS",VerifiedCheckpointInventory.evaluatePrefix(current,List.of(new RuntimeHistoryPage.Entry(5,"CHECKPOINT_AUDIT","interval",operation.result(),true)),5,6).get(VerifiedCheckpointProtocol.REQUIRED_CHECK).truth(),"Audit repeats evidence without re-admitting that invocation");
    }
}
