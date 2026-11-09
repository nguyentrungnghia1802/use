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
    @Test void indexedDiskAndRetainedPrefixKeepIdenticalInvocationTruthIncludingUnknownAndError(@org.junit.jupiter.api.io.TempDir java.nio.file.Path directory)throws Exception {
        var current=cut(List.of(invariant(VerificationOutcome.PASS)),List.of());
        for(var verdict:SemanticVerdict.values()) {
            var journal=new RuntimeEventJournal(directory,2,4*1024*1024,true);var retained=new ArrayList<RuntimeHistoryPage.Entry>();
            var invocation=cut(List.of(invariant(VerificationOutcome.PASS)),List.of(new SemanticConstraintResult("post","POST","Domain","x","source","self.valid",verdict,"native value","original invocation evidence",1,"invocation",Map.of())));
            for(int i=0;i<8;i++) {assertTrue(journal.append(i==1?"CHECKPOINT_AUDIT":"OPERATION_POST",Map.of(),i==0||i==1?invocation.result():current.result()));retained.add(journal.retainedEntries().getLast());}
            var expected=evaluateExpected(verdict);
            var disk=VerifiedCheckpointInventory.evaluatePrefix(current,journal,0,8);
            assertEquals(expected.equals("ERROR")?"UNKNOWN":expected,disk.get(VerifiedCheckpointProtocol.REQUIRED_CHECK).truth());
            assertEquals(VerifiedCheckpointInventory.evaluatePrefix(current,retained,0,8),disk);
            assertEquals(expected,disk.get("prefix:0:post:invocation").truth());assertFalse(disk.keySet().stream().anyMatch(id->id.startsWith("prefix:1:")));
        }
    }
    private static String evaluateExpected(SemanticVerdict verdict){return switch(verdict){case PASS->"PASS";case FAIL->"FAIL";case ERROR->"ERROR";default->"UNKNOWN";};}
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
    @Test void supportingTaskFailureUnderSelectedRootStaysSupportingWhileSelectedRootViolationFailsInventory() {
        var inventory=Map.<String,Object>of(
                "version","NATIVE_CONTRACT_INVENTORY/1.0",
                "requiredInvariants",List.of("NATIVE:Domain::Required"),
                "selectedIntentionalElements",List.of("O_root","do_auction"),
                "scopeSelectedIntentionalElements",Map.of("initial-auction-setup",List.of("do_auction"),"auction-execution",List.of("O_root")));
        var supportingPost=new SemanticConstraintResult("TASK:P_bid_t2:post:1","TASK_POST","AuctionArtifact","a1","source","self.running",SemanticVerdict.SKIPPED,"","SOURCE_FAILURE_WORLD_POST_UNAVAILABLE",1,"inv-1",Map.of("targetTaskId","P_bid_t2"));
        var occurrences=Map.<String,Object>of(
                "O_root",Map.of("sourceElement","O_root","aggregateStatus","FULFILLED","quality","KNOWN_DERIVED"),
                "P_bid_t2_alice",Map.of("sourceElement","P_bid_t2","aggregateStatus","PENDING","executionStatus","FAILED","executionQuality","KNOWN_HISTORY"),
                "C_bid_alice",Map.of("sourceElement","C_bid","aggregateStatus","PENDING","executionStatus","VIOLATED","executionQuality","KNOWN_DERIVED_HISTORY"),
                "P_bid_alice",Map.of("sourceElement","P_bid","aggregateStatus","PENDING","executionStatus","VIOLATED","executionQuality","KNOWN_DERIVED_HISTORY"));
        var setupOccurrences=Map.<String,Object>of(
                "do_auction",Map.of("sourceElement","do_auction","aggregateStatus","FULFILLED","quality","KNOWN_DERIVED"));
        var observation=Map.<String,Object>of(
                "requirementInventory",inventory,
                "originalEngine",Map.of("evaluated","PINNED_NATIVE_CUT","historyQuality","OBSERVED_PREFIX","contexts",List.of(
                        Map.of("context","ctx-setup","scopeDefinition","initial-auction-setup","scopeView",Map.of("definition","initial-auction-setup","bindings",Map.of("setup","setup-1")),"occurrences",setupOccurrences),
                        Map.of("context","ctx-auction","scopeDefinition","auction-execution","scopeView",Map.of("definition","auction-execution","bindings",Map.of("scheme","sch-1")),"occurrences",occurrences))));
        var localFailureCut=cut(List.of(invariant(VerificationOutcome.PASS)),List.of(supportingPost),observation);
        var evaluated=VerifiedCheckpointInventory.evaluate(localFailureCut);
        assertEquals("PASS",evaluated.get(VerifiedCheckpointProtocol.REQUIRED_CHECK).truth(),"Local supporting task failure under ANY must not fail whole-MAS inventory when selected roots pass");
        assertEquals("NOT_APPLICABLE",evaluated.get("semantic:TASK:P_bid_t2:post:1:inv-1").applicability());
        assertEquals("NOT_APPLICABLE",evaluated.get("original:ctx-auction:P_bid_t2_alice").applicability());
        assertEquals("NOT_APPLICABLE",evaluated.get("original:ctx-auction:C_bid_alice").applicability());
        var settled=VerifiedCheckpointInventory.settled(localFailureCut,evaluated,List.of(
                VerifiedCheckpointProtocol.SETTLED_CHECK_PREFIX+"source:setup-1",
                VerifiedCheckpointProtocol.SETTLED_CHECK_PREFIX+"source:sch-1"));
        assertEquals("PASS",settled.get(VerifiedCheckpointProtocol.SETTLED_CHECK_PREFIX+"source:setup-1").truth());
        assertEquals("PASS",settled.get(VerifiedCheckpointProtocol.SETTLED_CHECK_PREFIX+"source:sch-1").truth());
        assertEquals("PASS",settled.get(VerifiedCheckpointProtocol.REQUIRED_CHECK).truth());

        var violatedRootOccurrences=new TreeMap<String,Object>(occurrences);
        violatedRootOccurrences.put("O_root",Map.of("sourceElement","O_root","aggregateStatus","PENDING","executionStatus","VIOLATED","executionQuality","KNOWN_DERIVED_HISTORY","executionEvidence",Map.of("P_bid_t2",Map.of("kind","TASK_FAILED","policy","CERTIFIED_TERMINAL_EXECUTION/1.0","invocation","inv-1"))));
        var rootViolatedObservation=Map.<String,Object>of(
                "requirementInventory",inventory,
                "originalEngine",Map.of("evaluated","PINNED_NATIVE_CUT","historyQuality","OBSERVED_PREFIX","contexts",List.of(
                        Map.of("context","ctx-auction","scopeDefinition","auction-execution","scopeView",Map.of("definition","auction-execution","bindings",Map.of("scheme","sch-1")),"occurrences",violatedRootOccurrences))));
        var rootViolatedCut=cut(List.of(invariant(VerificationOutcome.PASS)),List.of(supportingPost),rootViolatedObservation);
        var rootEvaluated=VerifiedCheckpointInventory.evaluate(rootViolatedCut);
        assertEquals("APPLICABLE",rootEvaluated.get("original:ctx-auction:O_root").applicability());
        assertEquals("FAIL",rootEvaluated.get(VerifiedCheckpointProtocol.REQUIRED_CHECK).truth());
        var rootSettled=VerifiedCheckpointInventory.settled(rootViolatedCut,rootEvaluated,List.of(VerifiedCheckpointProtocol.SETTLED_CHECK_PREFIX+"source:sch-1"));
        assertEquals("FAIL",rootSettled.get(VerifiedCheckpointProtocol.SETTLED_CHECK_PREFIX+"source:sch-1").truth());
    }
    @Test void prePlanCheckpointEvaluatesTaskPreAndActivationWithoutTreatingMissingPostAsFailure() {
        var inventory=Map.<String,Object>of(
                "version","NATIVE_CONTRACT_INVENTORY/1.0",
                "requiredInvariants",List.of("NATIVE:Domain::Required","NATIVE:Group::Population"),
                "selectedIntentionalElements",List.of("O_root","do_auction"),
                "scopeSelectedIntentionalElements",Map.of("initial-auction-setup",List.of("do_auction"),"auction-execution",List.of("O_root")));
        var outcomes=List.of(
                invariant(VerificationOutcome.PASS),
                new ExternalOclConstraintService.Outcome("NATIVE:Group::Population","Group",VerificationOutcome.SKIPPED,"INVARIANT_UNDETERMINED_OR_POPULATION_UNPROVEN","self.players->size() = 1"));
        var awaitingBody=new SemanticConstraintResult("NATIVE:Group::Population","INVARIANT_BODY","Group","grp1","source-grp1","self.players->size() = 1",SemanticVerdict.SKIPPED,"","CONSTRAINT_AWAITING_QUALIFIED_ACTIVATION",1,"",Map.of());
        var awaitingAggregate=new SemanticConstraintResult("NATIVE:Group::Population","INVARIANT","Group","grp1","source-grp1","self.players->size() = 1",SemanticVerdict.SKIPPED,"","INVARIANT_UNDETERMINED_OR_POPULATION_UNPROVEN",1,"",Map.of("grp1","SKIPPED","population","COMPLETE","quantifier","FOR_ALL","negated","false"));
        var taskPrePass=new SemanticConstraintResult("TASK:createScheme:pre:1","TASK_PRE","auction_capabilities","bob","source-bob","step.enabled",SemanticVerdict.PASS,"true","QUALIFIED_TASK_PRE",1,"step-1",Map.of("targetTaskId","createScheme"));
        var setupOccurrences=Map.<String,Object>of(
                "do_auction",Map.of("sourceElement","do_auction","aggregateStatus","PENDING","quality","DERIVED_INPUT_UNAVAILABLE"),
                "createScheme",Map.of("sourceElement","createScheme","aggregateStatus","PENDING","quality","TASK_TERMINAL_CALLBACK_PENDING"));
        var observation=Map.<String,Object>of(
                "requirementInventory",inventory,
                "originalEngine",Map.of("evaluated","PINNED_NATIVE_CUT","historyQuality","OBSERVED_PREFIX","contexts",List.of(
                        Map.of("context","ctx-setup","scopeDefinition","initial-auction-setup","scopeView",Map.of("definition","initial-auction-setup","bindings",Map.of("setup","setup-1")),"occurrences",setupOccurrences))));
        var preCut=cut(outcomes,List.of(awaitingBody,awaitingAggregate,taskPrePass),observation);
        var evaluated=VerifiedCheckpointInventory.evaluate(preCut);
        assertEquals("PASS",evaluated.get(VerifiedCheckpointProtocol.REQUIRED_CHECK).truth());
        assertEquals("APPLICABLE",evaluated.get("semantic:TASK:createScheme:pre:1:step-1").applicability());
        assertEquals("PASS",evaluated.get("semantic:TASK:createScheme:pre:1:step-1").truth());
        assertEquals("NOT_APPLICABLE",evaluated.get("NATIVE:Group::Population").applicability());

        var taskPreFail=new SemanticConstraintResult("TASK:A_decide_t2:pre:1","TASK_PRE","Auction","a1","source-a1","self.running = true",SemanticVerdict.FAIL,"false","QUALIFIED_TASK_PRE",1,"step-2",Map.of("targetTaskId","A_decide_t2"));
        var failedPreCut=cut(List.of(invariant(VerificationOutcome.PASS)),List.of(taskPreFail),observation);
        var failedEvaluated=VerifiedCheckpointInventory.evaluate(failedPreCut);
        assertEquals("APPLICABLE",failedEvaluated.get("semantic:TASK:A_decide_t2:pre:1:step-2").applicability());
        assertEquals("FAIL",failedEvaluated.get(VerifiedCheckpointProtocol.REQUIRED_CHECK).truth());
    }
}
