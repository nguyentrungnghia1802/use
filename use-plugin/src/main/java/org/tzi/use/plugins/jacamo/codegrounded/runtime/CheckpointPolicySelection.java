package org.tzi.use.plugins.jacamo.codegrounded.runtime;
import java.util.*;
import java.util.function.Predicate;
import org.jacamo.bridge.contract.*;
/** Selection, not verification: observation infrastructure is not a business
 * checkpoint. Prefer an admitted native Task's terminal source step; never
 * stop halfway through its source construction with expected missing bindings. */
public final class CheckpointPolicySelection {
    public static List<VerifiedCheckpointProtocol.Rule> rules(List<ObservationCapabilityRequest> requests,Predicate<String> nativeTask){
        var rules=new ArrayList<VerifiedCheckpointProtocol.Rule>();var taskClasses=new HashSet<String>();
        for(var request:requests)if(request.provider().equals("jason.plan-boundaries.v1")){
            for(Object raw:(List<?>)request.parameters().get("plans")){
                var steps=(List<?>)CanonicalJson.object(raw).get("steps");if(steps.isEmpty())continue;
                var step=CanonicalJson.object(steps.getLast());String task=(String)step.get("task"),type=(String)step.get("artifactClass");
                if(type==null||type.isBlank()||Boolean.TRUE.equals(step.get("allowMissingArtifactAtEntry"))||!nativeTask.test(task))continue;
                taskClasses.add(type);
                rules.add(new VerifiedCheckpointProtocol.Rule("task-post:"+request.id()+":"+task,"observation:"+request.id(),type,Set.of(task),Set.of(VerifiedCheckpointProtocol.REQUIRED_CHECK),"TASK_POST"));
            }
        }
        for(var request:requests)if(request.provider().equals("cartago.operation-history.v1")&&request.parameters().containsKey("sourceRef")&&request.parameters().get("operations") instanceof List<?> names&&!names.isEmpty()){
            String type=(String)request.parameters().get("class");
            rules.add(new VerifiedCheckpointProtocol.Rule("operation-post:"+request.id(),"observation:"+request.id(),type,names.stream().map(String.class::cast).collect(java.util.stream.Collectors.toSet()),Set.of(VerifiedCheckpointProtocol.REQUIRED_CHECK),taskClasses.contains(type)?"OPERATION_FAILURE":"OPERATION_POST"));
        }
        return List.copyOf(rules);
    }
    private CheckpointPolicySelection(){}
}
