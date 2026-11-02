package org.tzi.use.plugins.jacamo.codegrounded;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.jacamo.bridge.contract.*;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.CheckpointPolicySelection;
import org.junit.jupiter.api.Test;
class CheckpointPolicySelectionTest {
 @Test void observationConstructionIsNotAMilestoneAndMappedTerminalTasksOwnTheSuccessCut(){
  var requests=List.of(
   new ObservationCapabilityRequest("construction","cartago.operation-history.v1",true,Map.of("class","framework.Workspace","operations",List.of("create"))),
   new ObservationCapabilityRequest("domain","cartago.operation-history.v1",true,Map.of("sourceRef","source","class","domain.Service","operations",List.of("open","close"))),
   new ObservationCapabilityRequest("steps","jason.plan-boundaries.v1",true,Map.of("plans",List.of(Map.of("steps",List.of(Map.of("task","setup","artifactClass","domain.Service","allowMissingArtifactAtEntry",true),Map.of("task","CompleteTask","artifactClass","domain.Service","allowMissingArtifactAtEntry",false)))))));
  var rules=CheckpointPolicySelection.rules(requests,id->id.equals("CompleteTask"));
  assertEquals(2,rules.size());assertTrue(rules.stream().noneMatch(r->r.sourceIdentity().equals("observation:construction")));
  assertEquals("TASK_POST",rules.getFirst().selectorKind());assertEquals(Set.of("CompleteTask"),rules.getFirst().operations());assertEquals("OPERATION_FAILURE",rules.getLast().selectorKind());
  assertEquals("OPERATION_POST",CheckpointPolicySelection.rules(requests,id->false).getFirst().selectorKind());
 }
}
