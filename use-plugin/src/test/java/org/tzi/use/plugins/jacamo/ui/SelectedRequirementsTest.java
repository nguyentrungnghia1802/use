package org.tzi.use.plugins.jacamo.ui;
import java.util.*;import java.time.Instant;import javax.swing.*;
import org.junit.jupiter.api.Test;
import org.tzi.use.plugins.jacamo.*;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.*;
import org.tzi.use.plugins.jacamo.semantics.evidence.*;
import org.tzi.use.plugins.jacamo.ui.use4goal.conformance.*;
import static org.junit.jupiter.api.Assertions.*;
class SelectedRequirementsTest {
 private ObservedConformancePanel.Cut cut(boolean scoped,String status,String freshness,boolean resource){return cut(scoped,status,freshness,resource,"Domain",false);}
 private ObservedConformancePanel.Cut cut(boolean scoped,String status,String freshness,boolean resource,String owner,boolean inventory){
  var entries=new TreeMap<String,Object>();
  for(String id:List.of("Done","Work"))entries.put(id,Map.of("sourceElement",id,"elementKind",id.equals("Done")?"GOAL":"TASK","aggregateStatus","FULFILLED","leafStatus","FULFILLED","quality","KNOWN"));
  if(resource)entries.put("support",Map.of("sourceElement","Support","elementKind","RESOURCE","aggregateStatus","UNKNOWN","quality","DERIVED_INPUT_UNAVAILABLE"));
  var core=Map.of("contexts",List.of(Map.of("context","context","occurrences",entries)),"historyQuality","OBSERVED_PREFIX","evaluated","PINNED_NATIVE_CUT");
  var observation=new TreeMap<String,Object>();observation.put("originalEngine",core);observation.put("requirementContexts",List.of("Domain"));if(scoped)observation.put("requirementCoveragePolicy","SELECTED_REQUIREMENTS/1.0");
  if(inventory)observation.put("requirementInventory",Map.of("version","NATIVE_CONTRACT_INVENTORY/1.0","requiredInvariants",List.of("check"),"supportingInvariants",List.of()));
  var constraints=new ArrayList<SemanticConstraintResult>();
  if(!status.isEmpty())for(String kind:List.of("INVARIANT_BODY","INVARIANT"))constraints.add(new SemanticConstraintResult("check",kind,owner,kind.endsWith("BODY")?"one":"","","true",SemanticVerdict.valueOf(status),"","",1,"",Map.of()));
  var envelope=new SemanticVerificationEnvelope("1.0","profile","hash","boundary",constraints,Map.of(),Map.of(),List.of(),observation);
  var r=new RuntimeVerificationResult("run",1,"revision",1,"event","cartago",1,Instant.EPOCH,Instant.EPOCH,Instant.EPOCH,"checkpoint","contracts","state",List.of(),0,"INCOMPLETE",freshness,"",envelope);
  return new ObservedConformancePresenter(new JaCaMoFacade(){public String status(){return "fixture";}public RuntimeVerificationResult runtimeVerificationResult(){return r;}}).capture().getLast();
 }
 @Test void resourcesAreSupportingInputsNotUnknownGoalTaskRequirements(){var c=cut(true,"PASS","CURRENT_OBSERVED",true);assertEquals(2,c.evaluations().stream().filter(r->r.kind().equals("GOAL_TASK")).count());assertTrue(c.requirements().startsWith("Requirements: KNOWN"),c.requirements());}
 @Test void aggregateInvariantCountsOnceButEveryBodyRemainsVisible(){var c=cut(true,"SKIPPED","CURRENT_OBSERVED",false);assertTrue(c.requirements().startsWith("Requirements: INCOMPLETE (1 unavailable)"),c.requirements());assertEquals(2,c.evaluations().stream().filter(r->r.element().equals("check")).count());}
 @Test void scopedCompletionDoesNotRequireUnrelatedFullSourceEnumerationButMissingStillBlocks()throws Exception{
  SwingUtilities.invokeAndWait(()->{var panel=ObservedConformancePanel.liveResults(LiveGoalSummaryTest.model());
   for(var spec:List.of(new String[]{"true","PASS","CURRENT_OBSERVED","COMPLETE_OBSERVED"},new String[]{"true","SKIPPED","CURRENT_OBSERVED","INCOMPLETE"},new String[]{"true","UNDEFINED","CURRENT_OBSERVED","INCOMPLETE"},new String[]{"true","UNSUPPORTED","CURRENT_OBSERVED","INCOMPLETE"},new String[]{"true","ERROR","CURRENT_OBSERVED","INCOMPLETE"},new String[]{"true","PASS","STALE","INCOMPLETE"},new String[]{"false","PASS","CURRENT_OBSERVED","INCOMPLETE"})){
    panel.setCuts(List.of(cut(Boolean.parseBoolean(spec[0]),spec[1],spec[2],false)));assertEquals(spec[3],DetachedAuthoredWorkflowTest.find(panel,"observed-verdict",JLabel.class).getText(),Arrays.toString(spec));
   }
  });
 }
 @Test void authoredHelperContextAndMissingContractMustBlockComplete()throws Exception{
  SwingUtilities.invokeAndWait(()->{var panel=ObservedConformancePanel.liveResults(LiveGoalSummaryTest.model());
   for(String status:List.of("SKIPPED","UNDEFINED","UNSUPPORTED","ERROR","")){
    var c=cut(true,status,"CURRENT_OBSERVED",false,"AtomicAgent",true);
    panel.setCuts(List.of(c));assertEquals("INCOMPLETE",DetachedAuthoredWorkflowTest.find(panel,"observed-verdict",JLabel.class).getText(),"authored helper or missing contract: "+status);
   }
   panel.setCuts(List.of(cut(true,"PASS","CURRENT_OBSERVED",false,"AtomicAgent",true)));
   assertEquals("COMPLETE_OBSERVED",DetachedAuthoredWorkflowTest.find(panel,"observed-verdict",JLabel.class).getText());
  });
 }

 @Test void xmlSelectedRootUsesOriginalPropagationAndSupportingFailedAttemptDoesNotVetoAny()throws Exception {
  SwingUtilities.invokeAndWait(()->{var panel=ObservedConformancePanel.liveResults(LiveGoalSummaryTest.model());
   for(String root:List.of("FULFILLED","PENDING","UNKNOWN","MISSING"))for(boolean invariantFailure:List.of(false,true)){
    var occurrences=new TreeMap<String,Object>();
    if(!root.equals("MISSING"))occurrences.put("root",Map.of("sourceElement","Done","elementKind","GOAL","aggregateStatus",root,"leafStatus",root,"quality",root.equals("UNKNOWN")?"UNAVAILABLE":"KNOWN"));
    occurrences.put("support",Map.of("sourceElement","Work","elementKind","TASK","aggregateStatus","PENDING","leafStatus","PENDING","quality","KNOWN"));
    var observation=Map.<String,Object>of("requirementCoveragePolicy","SELECTED_REQUIREMENTS/1.0","requirementInventory",Map.of("selectedIntentionalElements",List.of("Done"),"requiredInvariants",List.of("check")),"originalEngine",Map.of("contexts",List.of(Map.of("context","c","occurrences",occurrences)),"historyQuality","OBSERVED_PREFIX","evaluated","PINNED_NATIVE_CUT"));
    var check=new SemanticConstraintResult("check","INVARIANT","Domain","","","true",invariantFailure?SemanticVerdict.FAIL:SemanticVerdict.PASS,"","",1,"",Map.of());
    var envelope=new SemanticVerificationEnvelope("1.0","profile","hash","boundary",List.of(check),Map.of(),Map.of(),List.of(),observation);
    var result=new RuntimeVerificationResult("run",1,"revision",1,"event","cartago",1,Instant.EPOCH,Instant.EPOCH,Instant.EPOCH,"checkpoint","contracts","state",List.of(),0,"INCOMPLETE","CURRENT_OBSERVED","",envelope);
    var cut=new ObservedConformancePresenter(new JaCaMoFacade(){public String status(){return "fixture";}public RuntimeVerificationResult runtimeVerificationResult(){return result;}}).capture().getLast();panel.setCuts(List.of(cut));
    String expected=invariantFailure?"VIOLATION":switch(root){case "FULFILLED"->"COMPLETE_OBSERVED";case "PENDING"->"PENDING";default->"INCOMPLETE";};
    assertEquals(expected,DetachedAuthoredWorkflowTest.find(panel,"observed-verdict",JLabel.class).getText(),root+" invariantFailure="+invariantFailure);
    assertFalse(cut.evaluations().stream().filter(r->r.element().equals("Work")).anyMatch(r->r.required()));
   }
  });
 }

}
