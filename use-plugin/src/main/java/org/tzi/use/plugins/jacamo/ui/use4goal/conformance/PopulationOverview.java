package org.tzi.use.plugins.jacamo.ui.use4goal.conformance;

import java.util.*;
import org.jacamo.bridge.contract.CanonicalJson;
import org.vnu.sme.goal.dsl.istar.mm.*;
import org.tzi.use.plugins.jacamo.ui.use4goal.conformance.ObservedConformancePanel.Evaluation;

/** Display-only population policies from the admitted graph and mapping. No native marking writes. */
final class PopulationOverview {
    private final Map<String,Set<String>> policies=new HashMap<>();

    PopulationOverview(GoalModel model,Map<String,Object> mapping) {
        var quantifiers=object(mapping.get("dependencyPopulations"));var views=object(mapping.get("goalRealizations"));
        for(var dependency:model.getDependencies()) {
            String policy=Objects.toString(quantifiers.get(dependency.dependum().id()),"");
            if(Set.of("ANY","ALL").contains(policy)&&dependency.dependeeElmt()!=null)
                propagate(model,views,dependency.dependeeElmt(),policy,new HashSet<>());
        }
    }
    private void propagate(GoalModel model,Map<String,Object> views,String id,String policy,Set<String> visited) {
        if(!visited.add(id)||model.findElement(id).isEmpty())return;
        policies.computeIfAbsent(id,k->new HashSet<>()).add(policy);
        String source=Objects.toString(object(views.get(id)).get("source"),"");
        if(!source.isEmpty())propagate(model,views,source,policy,visited);
        for(var actor:model.getActors())for(var refinement:actor.refinements())if(refinement.parent().equals(id)) {
            var children=refinement instanceof AndRefinement and?and.children():List.of(((OrRefinement)refinement).child());
            for(String child:children)propagate(model,views,child,policy,visited);
        }
    }
    String description(String id){return String.join(" / ",new TreeSet<>(policies.getOrDefault(id,Set.of("ALL"))));}
    String status(String id,List<Evaluation> instances) {
        // ANY is local to one execution. A successful execution cannot mask another failed execution in All.
        var scopes=new LinkedHashMap<String,List<Evaluation>>();
        for(var instance:instances)if(!notAssigned(instance))scopes.computeIfAbsent(instance.context(),k->new ArrayList<>()).add(instance);
        if(scopes.isEmpty())return instances.isEmpty()?"UNKNOWN":"NOT_ASSIGNED";
        var results=new ArrayList<String>();
        for(var scope:scopes.values()) {
            var possible=new HashSet<String>();for(String policy:policies.getOrDefault(id,Set.of("ALL")))possible.add(aggregate(scope,policy));
            results.add(possible.size()==1?possible.iterator().next():"UNKNOWN");
        }
        if(results.contains("VIOLATED"))return "VIOLATED";
        if(results.contains("FAILED"))return "FAILED";
        if(results.stream().allMatch("FULFILLED"::equals))return "FULFILLED";
        return results.contains("UNKNOWN")?"UNKNOWN":"PENDING";
    }
    private static String aggregate(List<Evaluation> rows,String policy) {
        var values=rows.stream().map(PresentationResults::agentResult).toList();
        String failure=rows.stream().anyMatch(r->r.status().equals("VIOLATED")&&PresentationResults.agentResult(r).equals("Incomplete"))?"VIOLATED":"FAILED";
        if(policy.equals("ANY")) {
            if(values.contains("Complete"))return "FULFILLED";
            return values.stream().allMatch("Incomplete"::equals)?failure:"UNKNOWN";
        }
        if(values.contains("Incomplete"))return failure;
        if(values.stream().allMatch("Complete"::equals))return "FULFILLED";
        return rows.stream().allMatch(r->Set.of("FULFILLED","PENDING").contains(r.status())&&r.quality().startsWith("KNOWN"))?"PENDING":"UNKNOWN";
    }
    static boolean notAssigned(Evaluation row){return row.status().equals("NOT_ASSIGNED")&&row.quality().equals("SOURCE_NOT_ASSIGNED");}
    private static Map<String,Object> object(Object value){return value instanceof Map<?,?>?CanonicalJson.object(value):Map.of();}
}
