package org.tzi.use.plugins.jacamo.ui;
import java.util.*;import java.time.Instant;
import org.jacamo.bridge.contract.CanonicalJson;
import org.junit.jupiter.api.Test;
import org.tzi.use.plugins.jacamo.*;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.*;
import org.tzi.use.plugins.jacamo.semantics.evidence.*;
import org.tzi.use.plugins.jacamo.ui.use4goal.conformance.*;
import static org.junit.jupiter.api.Assertions.*;
class CertifiedGoalHistoryTest {
 @Test void runtimeIssuedHistoricalProofSurvivesUiReopenButRawHistoryCannotCreateOne() {
  var base=result("run",2,"XML_TASK_TUPLE_UNAVAILABLE","alice");
  var original=base.semantic();var core=new TreeMap<>(CanonicalJson.object(original.observation().get("originalEngine")));
  var context=new TreeMap<>(CanonicalJson.object(((List<?>)core.get("contexts")).getFirst()));
  var occurrence=new TreeMap<>(CanonicalJson.object(CanonicalJson.object(context.get("occurrences")).get("occurrence")));
  occurrence.put("executionQuality","KNOWN_HISTORY");occurrence.put("executionEvidence",Map.of("task",Map.of("policy","CERTIFIED_EXECUTION/1.0","stateVersion",1,"stateHash","accepted-hash")));
  context.put("occurrences",Map.of("occurrence",occurrence));core.put("contexts",List.of(context));core.put("historyQuality","GAP");
  var envelope=new SemanticVerificationEnvelope("1.0","profile","hash","boundary",List.of(),Map.of(),Map.of(),List.of(),Map.of("originalEngine",core));
  var checked=new RuntimeVerificationResult(base.sessionId(),base.generation(),base.modelRevision(),base.stateVersion(),base.eventId(),base.sourceId(),base.sourceSequence(),Instant.EPOCH,Instant.EPOCH,Instant.EPOCH,base.checkpointId(),"contracts",base.stateHash(),List.of(),0,"INCOMPLETE","CURRENT_OBSERVED","",envelope);
  var facade=new JaCaMoFacade(){public String status(){return "test";}public RuntimeVerificationResult runtimeVerificationResult(){return checked;}};
  var cut=new ObservedConformancePresenter(facade).captureCurrent().getLast();
  var row=cut.evaluations().getFirst();assertEquals("FULFILLED",row.status());assertEquals("KNOWN_HISTORY",row.quality());assertTrue(row.evidence().contains("accepted-hash"));assertEquals("INCOMPLETE",cut.coverage());
  assertEquals("FULFILLED",new ObservedConformancePresenter(facade).capture().getLast().evaluations().getFirst().status(),"No UI cache is needed: proof belongs to persisted runtime result");
 }
 @Test void internalPopulationReceiversAreNotCountedAsSourceGoalOccurrences() {
  var base=result("run",1,"KNOWN","alice");var core=new TreeMap<>(CanonicalJson.object(base.semantic().observation().get("originalEngine")));
  var context=new TreeMap<>(CanonicalJson.object(((List<?>)core.get("contexts")).getFirst()));
  var source=new TreeMap<>(CanonicalJson.object(CanonicalJson.object(context.get("occurrences")).get("occurrence")));source.put("expansionKind","SOURCE_ELEMENT");
  var helper=new TreeMap<>(source);helper.put("expansionKind","ANY_DEPENDENCY_RECEIVER");helper.put("quality","UNAVAILABLE");helper.put("aggregateStatus","UNKNOWN");
  context.put("occurrences",Map.of("source",source,"helper",helper));core.put("contexts",List.of(context));
  var envelope=new SemanticVerificationEnvelope("1.0","profile","hash","boundary",List.of(),Map.of(),Map.of(),List.of(),Map.of("originalEngine",core));
  var checked=new RuntimeVerificationResult("run",1,"revision",1,"event","cartago",1,Instant.EPOCH,Instant.EPOCH,Instant.EPOCH,"checkpoint","contracts","state",List.of(),0,"INCOMPLETE","CURRENT_OBSERVED","",envelope);
  var facade=new JaCaMoFacade(){public String status(){return "test";}public RuntimeVerificationResult runtimeVerificationResult(){return checked;}};
  var rows=new ObservedConformancePresenter(facade).capture().getLast().evaluations();assertEquals(1,rows.size(),"An internal ANY receiver is not another user Goal instance");assertEquals("FULFILLED",rows.getFirst().status());
 }

 @Test void certifiedTerminalFailureIsNotLostOrInventedFromRawPending() {
  for(boolean certified:List.of(false,true)){
   var base=result("run",2,"CURRENT_STEP_MARKER_MISSING","alice");var core=new TreeMap<>(CanonicalJson.object(base.semantic().observation().get("originalEngine")));
   var context=new TreeMap<>(CanonicalJson.object(((List<?>)core.get("contexts")).getFirst()));
   var occurrence=new TreeMap<>(CanonicalJson.object(CanonicalJson.object(context.get("occurrences")).get("occurrence")));occurrence.put("aggregateStatus","PENDING");occurrence.put("leafStatus","PENDING");
   occurrence.put("executionStatus","FAILED");occurrence.put("executionQuality","KNOWN_HISTORY");
   occurrence.put("executionEvidence",certified?Map.of("task",Map.of("policy","CERTIFIED_TERMINAL_EXECUTION/1.0","kind","TASK_FAILED","invocation","exact-call","stateHash","failed-cut")):Map.of());
   context.put("occurrences",Map.of("occurrence",occurrence));core.put("contexts",List.of(context));
   var envelope=new SemanticVerificationEnvelope("1.0","profile","hash","boundary",List.of(),Map.of(),Map.of(),List.of(),Map.of("originalEngine",core));
   var checked=new RuntimeVerificationResult("run",1,"revision",2,"event","cartago",2,Instant.EPOCH,Instant.EPOCH,Instant.EPOCH,"checkpoint","contracts","state",List.of(),0,"INCOMPLETE","CURRENT_OBSERVED","",envelope);
   var cut=new ObservedConformancePresenter(new JaCaMoFacade(){public String status(){return "fixture";}public RuntimeVerificationResult runtimeVerificationResult(){return checked;}}).capture().getLast();
   assertEquals(certified?"FAILED":"UNKNOWN",cut.evaluations().getFirst().status());assertFalse(cut.failed(),"Execution failure is not automatically a violated requirement");assertEquals("PENDING",cut.evaluations().getFirst().originalStatus());
  }
 }
 @Test void pendingSelectedProjectionNeverFallsBackToInfrastructureSoilInBusinessPreview(){
  var base=result("run",1,"KNOWN","alice");
  var semantic=new SemanticVerificationEnvelope("1.0","profile","hash","boundary",List.of(),Map.of(),Map.of(),List.of(),Map.of("originalEngine",Map.of("evaluated","ORDERED_EVALUATION_PENDING"),"presentationState",Map.of("pending",true,"qualifiedValues",false,"executionMilestones",false)));
  var r=new RuntimeVerificationResult("run",1,"revision",1,"event","cartago",1,Instant.EPOCH,Instant.EPOCH,Instant.EPOCH,"checkpoint","contracts","hash",List.of(),0,"EVALUATION_PENDING","PENDING_EVALUATION","",semantic);
  var facade=new JaCaMoFacade(){public String status(){return "fixture";}public VerificationSnapshot verificationSnapshot(){return new VerificationSnapshot(1,r,null,null,new VerificationSnapshot.StateImage(Map.of(),List.of(),"!new Infrastructure('internal')"));}};
  var cut=new ObservedConformancePresenter(facade).captureCurrent().getLast();assertFalse(cut.businessState().isBlank());assertFalse(cut.businessState().contains("!new"));assertTrue(cut.state().contains("!new"),"Native state remains available in audit");assertTrue(BusinessTimeline.summarize(List.of(cut)).isEmpty());
 }
 RuntimeVerificationResult result(String run,int n,String quality,String player){
  var occurrence=Map.<String,Object>of("sourceElement","Goal","aggregateStatus","FULFILLED","leafStatus","FULFILLED","quality",quality,"player",player,"key",Map.of("episode","same","player",player));
  var context=Map.of("context","context","occurrences",Map.of("occurrence",occurrence));
  var core=Map.of("contexts",List.of(context),"historyQuality","OBSERVED_PREFIX","evaluated","PINNED_NATIVE_CUT");
  var envelope=new SemanticVerificationEnvelope("1.0","profile","hash","boundary",List.of(),Map.of(),Map.of(),List.of(),Map.of("originalEngine",core));
  return new RuntimeVerificationResult(run,1,"revision",n,"event"+n,"cartago",n,Instant.EPOCH,Instant.EPOCH,Instant.EPOCH,"checkpoint"+n,"contracts","state"+n,List.of(),0,"INCOMPLETE","CURRENT_OBSERVED","",envelope);
 }
 @Test void derivedCertifiesAndHistoricalCertificateDoesNotPromoteMissingCurrentOrAnotherPlayer() {
  var history=new ArrayList<RuntimeVerificationResult>();
  var facade=new JaCaMoFacade(){public String status(){return "test";}public RuntimeVerificationResult runtimeVerificationResult(){return history.getLast();}public List<RuntimeVerificationResult> runtimeVerificationHistory(){return List.copyOf(history);}};
  var presenter=new ObservedConformancePresenter(facade);history.add(result("run",1,"KNOWN_DERIVED","alice"));
  assertEquals("FULFILLED",presenter.capture().getLast().evaluations().getFirst().status());
  history.add(result("run",2,"XML_TASK_TUPLE_UNAVAILABLE","alice"));var missing=presenter.capture().getLast().evaluations().getFirst();assertEquals("UNKNOWN",missing.status());assertTrue(missing.certifiedHistory().startsWith("FULFILLED @ state 1"));
  history.add(result("run",3,"XML_TASK_TUPLE_UNAVAILABLE","bob"));assertTrue(presenter.capture().getLast().evaluations().getFirst().certifiedHistory().startsWith("No certified"));
  history.clear();history.add(result("restart",1,"XML_TASK_TUPLE_UNAVAILABLE","alice"));assertTrue(presenter.capture().getLast().evaluations().getFirst().certifiedHistory().startsWith("No certified"));
 }
 @Test void undefinedRequirementCannotLookKnownWhileLegacyProfilesRetainNativeStateChanges() {
  var base=result("run",1,"KNOWN","alice");
  var row=new SemanticConstraintResult("UndefinedRequirement","INVARIANT","Domain","self","","undefined",SemanticVerdict.UNDEFINED,"undefined","USE_OCL_UNDEFINED",1,"",Map.of());
  var original=base.semantic();
  var envelope=new SemanticVerificationEnvelope("1.0","profile","hash","boundary",List.of(row),Map.of(),Map.of(),List.of(),original.observation());
  var checked=new RuntimeVerificationResult(base.sessionId(),base.generation(),base.modelRevision(),base.stateVersion(),base.eventId(),base.sourceId(),base.sourceSequence(),Instant.EPOCH,Instant.EPOCH,Instant.EPOCH,base.checkpointId(),"contracts",base.stateHash(),List.of(),0,"INCOMPLETE","CURRENT_OBSERVED","",envelope);
  var facade=new JaCaMoFacade(){public String status(){return "test";}public RuntimeVerificationResult runtimeVerificationResult(){return checked;}};
  var cut=new ObservedConformancePresenter(facade).capture().getLast();
  assertTrue(cut.requirements().startsWith("Requirements: INCOMPLETE"),cut.requirements());
  assertEquals("UNDEFINED",cut.evaluations().stream().filter(r->r.element().equals("UndefinedRequirement")).findFirst().orElseThrow().status());
  assertFalse(cut.failed());assertEquals(checked.stateHash(),cut.businessKey());
 }

 @Test void pendingEvaluationIsNotAQualifiedBusinessStateButItsAuditBoundaryRemains() {
  var base=result("run",1,"KNOWN","alice");
  var pending=new SemanticVerificationEnvelope("1.0","profile","hash","boundary",List.of(),Map.of(),Map.of(),List.of(),Map.of("originalEngine",Map.of("evaluated","EVALUATION_PENDING")));
  var checked=new RuntimeVerificationResult(base.sessionId(),base.generation(),base.modelRevision(),base.stateVersion(),base.eventId(),base.sourceId(),base.sourceSequence(),Instant.EPOCH,Instant.EPOCH,Instant.EPOCH,base.checkpointId(),"contracts",base.stateHash(),List.of(),0,"EVALUATION_PENDING","PENDING_EVALUATION","",pending);
  var facade=new JaCaMoFacade(){public String status(){return "test";}public RuntimeVerificationResult runtimeVerificationResult(){return checked;}};
  var cuts=new ObservedConformancePresenter(facade).capture();assertEquals(1,cuts.size());assertEquals("",cuts.getFirst().businessKey());assertEquals("EVALUATION_PENDING",cuts.getFirst().coverage());
 }

 @Test void exactJournalCompletionWinsOverItsStalePendingCopyButNeverAChangedCutOrGap() {
  var completed=result("run",1,"KNOWN","alice");var pendingEnvelope=new SemanticVerificationEnvelope("1.0","profile","hash","boundary",List.of(),Map.of(),Map.of(),List.of(),Map.of("originalEngine",Map.of("evaluated","ORDERED_EVALUATION_PENDING")));
  var pending=new RuntimeVerificationResult(completed.sessionId(),1,"revision",1,completed.eventId(),completed.sourceId(),1,Instant.EPOCH,Instant.EPOCH,Instant.EPOCH,completed.checkpointId(),"contracts",completed.stateHash(),List.of(),0,"EVALUATION_PENDING","PENDING_EVALUATION","",pendingEnvelope);
  var anchor=new RuntimeVerificationResult[]{pending};
  var facade=new JaCaMoFacade(){public String status(){return "test";}public RuntimeVerificationResult runtimeVerificationResult(){return anchor[0];}public List<RuntimeVerificationResult> runtimeVerificationHistory(){return List.of(completed);}};
  var presenter=new ObservedConformancePresenter(facade);assertEquals("FULFILLED",presenter.capture().getLast().evaluations().getFirst().status());
  anchor[0]=new RuntimeVerificationResult(pending.sessionId(),1,"revision",1,pending.eventId(),pending.sourceId(),1,Instant.EPOCH,Instant.EPOCH,Instant.EPOCH,pending.checkpointId(),"changed-profile",pending.stateHash(),List.of(),0,"EVALUATION_PENDING","PENDING_EVALUATION","",pendingEnvelope);
  assertTrue(presenter.capture().getLast().evaluations().isEmpty(),"A different profile is never certified by the old completion");
  anchor[0]=new RuntimeVerificationResult(pending.sessionId(),1,"revision",1,pending.eventId(),pending.sourceId(),1,Instant.EPOCH,Instant.EPOCH,Instant.EPOCH,pending.checkpointId(),"contracts",pending.stateHash(),List.of(),0,"INCOMPLETE","STALE","gap",pendingEnvelope);
  assertEquals("STALE",presenter.capture().getLast().freshness());assertTrue(presenter.capture().getLast().evaluations().isEmpty());
 }
 @Test void falseGoalPredicateIsTruthEvidenceAndOriginalMarkingOwnsViolation() {
  var row=new SemanticConstraintResult("GoalPredicate","GOAL_CONDITION","Domain","self","","false",SemanticVerdict.FAIL,"false","",1,"",Map.of());
  var envelope=new SemanticVerificationEnvelope("1.0","profile","hash","boundary",List.of(row),Map.of(),Map.of(),List.of(),Map.of());
  var checked=new RuntimeVerificationResult("run",1,"revision",1,"event","cartago",1,Instant.EPOCH,Instant.EPOCH,Instant.EPOCH,"checkpoint","contracts","state",List.of(row.legacy()),0,"INCOMPLETE","CURRENT_OBSERVED","",envelope);
  var facade=new JaCaMoFacade(){public String status(){return "test";}public RuntimeVerificationResult runtimeVerificationResult(){return checked;}};
  var cut=new ObservedConformancePresenter(facade).capture().getLast();assertFalse(cut.failed());assertEquals(1,cut.evaluations().size());assertEquals("false",cut.evaluations().getFirst().raw());assertEquals("CONDITION_FALSE",cut.evaluations().getFirst().status());
 }

 @Test void liveCaptureReadsOnlyTheCurrentCutAndNeverRescansTheDiskPrefix(){
  var latest=result("run",1,"KNOWN","alice");
  var facade=new JaCaMoFacade(){public String status(){return "live";}public RuntimeVerificationResult runtimeVerificationResult(){return latest;}
   public RuntimeHistoryPage runtimeHistoryPage(long offset,int limit){throw new AssertionError("Live view must not scan disk history");}
   public java.util.List<VerificationSnapshot> retainedVerificationSnapshots(){throw new AssertionError("Live view must not scan/hash all retained states");}};
  var cuts=new ObservedConformancePresenter(facade).captureCurrent();assertEquals(1,cuts.size());assertEquals("FULFILLED",cuts.getFirst().evaluations().getFirst().status());
 }

 @Test void taskPredicateFalseIsNotAStandaloneViolationButNativeInvariantFalseIs() {
  for(String kind:List.of("TASK_PRE","TASK_POST","INVARIANT")){
   var predicate=new SemanticConstraintResult("predicate",kind,"Domain","self","","false",SemanticVerdict.FAIL,"false","",1,"call",Map.of());
   var semantic=new SemanticVerificationEnvelope("1.0","profile","hash","boundary",List.of(predicate),Map.of(),Map.of(),List.of(),Map.of());
   var result=new RuntimeVerificationResult("run",1,"revision",1,"event","cartago",1,Instant.EPOCH,Instant.EPOCH,Instant.EPOCH,"checkpoint","contracts","state",List.of(),0,"COMPLETE","CURRENT_OBSERVED","",semantic);
   var cut=new ObservedConformancePresenter(new JaCaMoFacade(){public String status(){return "fixture";}public RuntimeVerificationResult runtimeVerificationResult(){return result;}}).capture().getLast();
   assertEquals(kind.equals("INVARIANT"),cut.failed(),kind);
   assertEquals(kind.equals("INVARIANT")?"FAIL":"CONDITION_FALSE",cut.evaluations().getFirst().status());
  }
 }

}
