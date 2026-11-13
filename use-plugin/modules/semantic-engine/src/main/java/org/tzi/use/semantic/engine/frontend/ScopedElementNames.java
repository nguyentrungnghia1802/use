package org.tzi.use.semantic.engine.frontend;

import java.util.*;
import org.vnu.sme.goal.dsl.istar.ast.*;

/** Opt-in lexical scoping: equal local names in different actors retain distinct declaration identities. */
public final class ScopedElementNames {
    private ScopedElementNames() { }
    public static IStarModelCS qualify(IStarModelCS source) {
        var counts=new HashMap<String,Integer>();
        for(var actor:source.actors()) {
            var local=new HashSet<String>();
            for(var body:actor.body()) {
                String id=id(body);if(id==null)continue;
                if(!local.add(id))throw new IllegalArgumentException("DUPLICATE_LOCAL_ELEMENT:"+actor.id()+"."+id);
                counts.merge(id,1,Integer::sum);
            }
        }
        var actors=new ArrayList<ActorDefCS>();
        for(var actor:source.actors()) {
            java.util.function.UnaryOperator<String> name=id->qualified(counts,actor.id(),id);
            var body=actor.body().stream().map(item->switch(item) {
                case ElementBodyCS.GoalCS g -> (ElementBodyCS)new ElementBodyCS.GoalCS(name.apply(g.id()),g.goalType(),rels(g.rels(),name),g.constraints());
                case ElementBodyCS.TaskCS t -> new ElementBodyCS.TaskCS(name.apply(t.id()),rels(t.rels(),name),t.constraints());
                case ElementBodyCS.ResourceCS r -> new ElementBodyCS.ResourceCS(name.apply(r.id()),rels(r.rels(),name));
                case ElementBodyCS.QualityCS q -> new ElementBodyCS.QualityCS(name.apply(q.id()),rels(q.rels(),name));
                default -> item;
            }).toList();
            actors.add(new ActorDefCS(actor.id(),actor.kind(),body));
        }
        var dependencies=source.dependencies().stream().map(d->new DependencyCS(d.depender(),qualified(counts,d.depender(),d.dependerElmt()),d.dependumKind(),d.dependum(),d.dependee(),qualified(counts,d.dependee(),d.dependeeElmt()))).toList();
        return new IStarModelCS(source.name(),actors,dependencies);
    }
    public static String localName(String actor,String id){return id.startsWith(actor+".")?id.substring(actor.length()+1):id;}
    private static String qualified(Map<String,Integer> counts,String actor,String id){return id!=null&&counts.getOrDefault(id,0)>1?actor+"."+id:id;}
    private static String id(ElementBodyCS body){return switch(body){case ElementBodyCS.GoalCS x->x.id();case ElementBodyCS.TaskCS x->x.id();case ElementBodyCS.ResourceCS x->x.id();case ElementBodyCS.QualityCS x->x.id();default->null;};}
    private static List<RelCS> rels(List<RelCS> rels,java.util.function.UnaryOperator<String> name){return rels.stream().map(r->switch(r){case RelCS.RefineCS x->(RelCS)new RelCS.RefineCS(name.apply(x.target()),x.or());case RelCS.ContributesCS x->new RelCS.ContributesCS(name.apply(x.target()),x.type());case RelCS.QualifiesCS x->new RelCS.QualifiesCS(name.apply(x.target()));case RelCS.NeededByCS x->new RelCS.NeededByCS(name.apply(x.target()));}).toList();}
}
