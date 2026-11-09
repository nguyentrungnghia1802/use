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
  assertEquals(3,rules.size());assertTrue(rules.stream().noneMatch(r->r.sourceIdentity().equals("observation:construction")));
  assertEquals(List.of("TASK_PRE","TASK_POST","OPERATION_UNMAPPED_POST"),rules.stream().map(VerifiedCheckpointProtocol.Rule::selectorKind).toList());
  assertEquals(Set.of("CompleteTask"),rules.get(0).operations());assertEquals(Set.of("CompleteTask"),rules.get(1).operations());
  assertEquals("OPERATION_UNMAPPED_POST",CheckpointPolicySelection.rules(requests,id->false).getFirst().selectorKind());
 }
 @Test void everyNativeSegmentIsSelectedIncludingUnboundEntryAndSourceOnlyTasks(){
  var plans=List.of(Map.of("steps",List.of(
    Map.of("task","Create","artifactClass","domain.Service","allowMissingArtifactAtEntry",true),
    Map.of("task","Trace","artifactClass","domain.Service"),
    Map.of("task","Operate","artifactClass","domain.Service"))),
    Map.of("steps",List.of(Map.of("task","Notify","artifactClass",""))));
  var request=new ObservationCapabilityRequest("steps","jason.plan-boundaries.v1",true,Map.of("plans",plans));
  var rules=CheckpointPolicySelection.rules(List.of(request),Set.of("Create","Operate","Notify")::contains);
  assertEquals(List.of("task-pre:steps:Create","task-post:steps:Create","task-post:steps:Operate","task-pre:steps:Notify","task-post:steps:Notify"),rules.stream().map(VerifiedCheckpointProtocol.Rule::id).toList());
  assertEquals(List.of("TASK_PRE","TASK_POST","TASK_POST","TASK_PRE","TASK_POST"),rules.stream().map(VerifiedCheckpointProtocol.Rule::selectorKind).toList());
  assertEquals(List.of(Set.of("Create"),Set.of("Create"),Set.of("Operate"),Set.of("Notify"),Set.of("Notify")),rules.stream().map(VerifiedCheckpointProtocol.Rule::operations).toList());
  assertEquals("",rules.getLast().artifactClass());
  var policy=new VerifiedCheckpointProtocol.Policy("a".repeat(64),Map.of("source","b".repeat(64)),rules);
  assertEquals(policy,VerifiedCheckpointProtocol.Policy.decode(policy.payload()));
 }
}
