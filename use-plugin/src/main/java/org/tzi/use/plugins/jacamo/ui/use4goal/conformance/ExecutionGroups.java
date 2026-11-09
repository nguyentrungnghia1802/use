package org.tzi.use.plugins.jacamo.ui.use4goal.conformance;

import java.util.*;
import org.jacamo.bridge.contract.BridgeEntityId;
import org.tzi.use.plugins.jacamo.ui.use4goal.conformance.ObservedConformancePanel.Cut;

/** Presentation index only. Native contexts, requirements and certificates keep their owners. */
public final class ExecutionGroups {
    private final Map<String,List<String>> members;
    private final Map<String,String> aliases;
    private final Map<String,Map<String,Object>> latest;

    private ExecutionGroups(Map<String,List<String>> members,Map<String,String> aliases,Map<String,Map<String,Object>> latest) {
        this.members=Collections.unmodifiableMap(members);this.aliases=Map.copyOf(aliases);this.latest=Map.copyOf(latest);
    }

    public static ExecutionGroups of(List<Cut> cuts) {
        var latest=new LinkedHashMap<String,Map<String,Object>>();
        var references=new HashMap<String,Set<String>>();
        for(var cut:cuts)for(var context:ExecutionScopeViews.contexts(cut))if(ExecutionScopeViews.boundContext(context)) {
            String id=Objects.toString(context.get("context"));latest.put(id,context);
            String ref=reference(context);if(!ref.isEmpty())references.computeIfAbsent(id,k->new HashSet<>()).add(ref);
        }
        var members=new LinkedHashMap<String,List<String>>();var aliases=new HashMap<String,String>();
        latest.forEach((id,context)->{
            var refs=references.getOrDefault(id,Set.of());
            // Ambiguous/changing source identities never join two histories.
            String group=refs.size()==1?"execution:"+refs.iterator().next():id;
            members.computeIfAbsent(group,k->new ArrayList<>()).add(id);aliases.put(id,group);
        });
        members.replaceAll((id,values)->values.stream().sorted(Comparator.comparingInt(value->
                Boolean.FALSE.equals(ExecutionScopeViews.object(latest.get(value).get("scopeView")).get("domainBound"))?0:1)).toList());
        return new ExecutionGroups(members,aliases,latest);
    }

    private static String reference(Map<String,Object> context) {
        var view=ExecutionScopeViews.object(context.get("scopeView"));
        if(!Boolean.TRUE.equals(view.get("complete")))return "";
        var refs=new TreeSet<String>();
        for(Object value:ExecutionScopeViews.object(view.get("bindings")).values())if(value instanceof String text)try {
            var id=BridgeEntityId.parse(text);
            // A MOISE scheme is the source execution identity, independent of model/table/column names.
            if(id.authority().equals("moise")&&id.dimension().equals("organisation")&&id.kind().equals("scheme-board"))refs.add(id.canonical());
        }catch(org.jacamo.bridge.contract.ContractException ignored){ }
        return refs.size()==1?refs.first():"";
    }

    public String canonical(String id){return aliases.getOrDefault(id,id);}
    public List<String> members(String id){return members.getOrDefault(canonical(id),List.of(id));}
    public List<String> ids(){return List.copyOf(members.keySet());}
    public Map<String,String> choices(){
        var result=new LinkedHashMap<String,String>();int ordinal=0;
        for(String id:ids())result.put(id,label(id,++ordinal));return Collections.unmodifiableMap(result);
    }
    public String label(String id,int ordinal){
        var contexts=members(id).stream().map(latest::get).filter(Objects::nonNull).toList();
        var domain=contexts.stream().filter(c->!ExecutionScopeViews.object(ExecutionScopeViews.object(c.get("scopeView")).get("entities")).isEmpty()).findFirst();
        return ExecutionScopeViews.label(domain.orElseGet(()->contexts.isEmpty()?Map.of():contexts.getFirst()),ordinal);
    }
    public List<Map<String,Object>> contexts(Cut cut,String id){
        var current=new HashMap<String,Map<String,Object>>();ExecutionScopeViews.contexts(cut).forEach(c->current.put(Objects.toString(c.get("context")),c));
        return members(id).stream().map(current::get).filter(Objects::nonNull).toList();
    }
    public Cut project(Cut cut,String id){return ExecutionScopeViews.projectMembers(cut,canonical(id),members(id));}
}
