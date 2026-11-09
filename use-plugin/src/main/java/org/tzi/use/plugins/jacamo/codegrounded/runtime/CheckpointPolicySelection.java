package org.tzi.use.plugins.jacamo.codegrounded.runtime;
import java.util.*;
import java.util.function.Predicate;
import org.jacamo.bridge.contract.*;
/** Source selection only. Task PRE binding readiness does not exclude its POST. */
public final class CheckpointPolicySelection {
    public static List<VerifiedCheckpointProtocol.Rule> rules(List<ObservationCapabilityRequest> requests,Predicate<String> nativeTask){
        var rules=new ArrayList<VerifiedCheckpointProtocol.Rule>();
        for(var request:requests)if(request.provider().equals("jason.plan-boundaries.v1")){
            var selected=new LinkedHashMap<String,String>();
            var entryTasks=new LinkedHashSet<String>();
            var postTasks=new LinkedHashSet<String>();
            for(Object raw:(List<?>)request.parameters().get("plans")){
                var steps=((List<?>)CanonicalJson.object(raw).get("steps")).stream().map(CanonicalJson::object).toList();
                if(steps.stream().noneMatch(s->nativeTask.test((String)s.get("task"))))continue;
                var firstStep=steps.getFirst();
                String entryTask=(String)firstStep.get("task");
                String entryType=Objects.toString(firstStep.get("artifactClass"),"");
                String priorEntry=selected.putIfAbsent(entryTask,entryType);
                if(priorEntry!=null&&!priorEntry.equals(entryType))throw new IllegalArgumentException("CHECKPOINT_TASK_SCOPE_CONFLICT:"+entryTask);
                entryTasks.add(entryTask);
                for(var step:steps){
                    String task=(String)step.get("task");
                    if(!nativeTask.test(task))continue;
                    String type=Objects.toString(step.get("artifactClass"),"");
                    String prior=selected.putIfAbsent(task,type);
                    if(prior!=null&&!prior.equals(type))throw new IllegalArgumentException("CHECKPOINT_TASK_SCOPE_CONFLICT:"+task);
                    postTasks.add(task);
                }
            }
            selected.forEach((task,type)->{
                if(entryTasks.contains(task))rules.add(new VerifiedCheckpointProtocol.Rule("task-pre:"+request.id()+":"+task,"observation:"+request.id(),type,Set.of(task),Set.of(VerifiedCheckpointProtocol.REQUIRED_CHECK),"TASK_PRE"));
                if(postTasks.contains(task))rules.add(new VerifiedCheckpointProtocol.Rule("task-post:"+request.id()+":"+task,"observation:"+request.id(),type,Set.of(task),Set.of(VerifiedCheckpointProtocol.REQUIRED_CHECK),"TASK_POST"));
            });
        }
        for(var request:requests)if(request.provider().equals("cartago.operation-history.v1")&&request.parameters().containsKey("sourceRef")&&request.parameters().get("operations") instanceof List<?> names&&!names.isEmpty()){
            String type=(String)request.parameters().get("class");
            rules.add(new VerifiedCheckpointProtocol.Rule("operation-post:"+request.id(),"observation:"+request.id(),type,names.stream().map(String.class::cast).collect(java.util.stream.Collectors.toSet()),Set.of(VerifiedCheckpointProtocol.REQUIRED_CHECK),"OPERATION_UNMAPPED_POST"));
        }
        for(var request:requests) {
            if(request.provider().equals("moise.scheme-lifecycle.v1")&&request.parameters().containsKey("sourceRef"))
                rules.add(new VerifiedCheckpointProtocol.Rule("scheme-post:"+request.id(),"observation:"+request.id(),"",Set.of("terminal","administrative"),Set.of(VerifiedCheckpointProtocol.REQUIRED_CHECK),"SCHEME_POST"));
            if(request.required()&&request.provider().equals("npl.lifecycle.v1"))
                rules.add(new VerifiedCheckpointProtocol.Rule("norm-terminal:"+request.id(),"observation:"+request.id(),"",Set.of("unfulfilled"),Set.of(VerifiedCheckpointProtocol.REQUIRED_CHECK),"NORM_TERMINAL"));
        }
        return List.copyOf(rules);
    }
    private CheckpointPolicySelection(){}
}
