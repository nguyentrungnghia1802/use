package org.tzi.use.plugins.jacamo.semantics.runtime;

import java.util.*;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.NativeRuntimeMutationEngine;
import org.tzi.use.plugins.jacamo.semantics.mapping.*;
import org.tzi.use.plugins.jacamo.semantics.evidence.SemanticJson;
import org.tzi.use.uml.sys.MObject;
import org.vnu.sme.goal.dsl.iscn.mm.ScenarioInstance;

/** Called under the native reader/writer barrier. Every cast belongs to one actual GroupBoard incarnation. */
public final class JaCaMoOccurrenceBindingAdapter {
    public record ActorBinding(String actorId,String instanceId,String roleId,String playerId) { }
    public record ContextCast(String contextId,String episode,List<ScenarioInstance> instances,
            Map<String,ActorBinding> actors,Map<String,String> selves,List<String> unavailable) {
        public ContextCast {instances=List.copyOf(instances);actors=Map.copyOf(actors);selves=Map.copyOf(selves);unavailable=List.copyOf(unavailable);}
        public boolean complete(){return unavailable.isEmpty();}
    }
    public List<ContextCast> resolve(SemanticProgram program,NativeRuntimeMutationEngine engine) {
        program.assertUnchanged();if(!program.hash().equals(engine.canonicalProgramHash()))throw new IllegalArgumentException("OCCURRENCE_PROGRAM_OWNER_MISMATCH");
        var objects=engine.system().state().allObjects();var result=new ArrayList<ContextCast>();
        for(var context:objects.stream().filter(o->!engine.metadata(o,"groupSpecSemanticId").isBlank()).sorted(Comparator.comparing(o->engine.metadata(o,"semanticId"))).toList()) {
            String contextId=engine.metadata(context,"semanticId"),source=engine.metadata(context,"groupSpecSemanticId");
            var templates=program.goalBindings().actors().values().stream().filter(a->a.contextSourceId().equals(source)).toList();
            if(templates.isEmpty())continue;
            var actors=new TreeMap<String,ActorBinding>();var selves=new TreeMap<String,String>();var unavailable=new ArrayList<String>();var cast=new ArrayList<ScenarioInstance>();
            for(var template:templates) {
                MObject selected=template.playerSourceId().isBlank()?null:engine.objectForSemanticId(template.playerSourceId());
                var roles=objects.stream().filter(o->contextId.equals(engine.metadata(o,"roleContext"))&&template.roleSourceId().equals(engine.metadata(o,"roleSourceId")))
                        .filter(o->template.playerSourceId().isBlank()||selected!=null&&engine.metadata(selected,"semanticId").equals(engine.metadata(o,"rolePlayer"))).toList();
                if(roles.size()!=1){unavailable.add("EXACT_ACTOR_ENACTMENT_REQUIRED:"+template.actorId()+":"+roles.size());continue;}
                var role=roles.getFirst();String roleId=engine.metadata(role,"semanticId"),playerId=engine.metadata(role,"rolePlayer");
                if(engine.objectForSemanticId(playerId)==null){unavailable.add("EXACT_PLAYER_UNAVAILABLE:"+template.actorId());continue;}
                var player=engine.objectForSemanticId(playerId);
                if(!bound(engine,"Plays",player,role)||!bound(engine,"InContext",context,role)){unavailable.add("NATIVE_ENACTMENT_LINK_DIFFERS_FROM_ACCEPTED_OBSERVATION:"+template.actorId());continue;}
                String instance="cast_"+SemanticJson.hash(List.of(contextId,template.actorId(),roleId,playerId));
                actors.put(template.actorId(),new ActorBinding(template.actorId(),instance,roleId,playerId));cast.add(new ScenarioInstance(instance,template.actorId()));
            }
            // A social dependency must never fall through the original first-owner fallback.
            if(templates.size()!=program.goalBindings().actors().size())unavailable.add("PROFILE_REQUIRES_COMPLETE_SINGLE_CONTEXT_CAST");
            for(var element:program.goalBindings().elements().values())if(!element.derived()) {
                var actor=actors.get(element.actorId());if(actor==null)continue;
                var self=element.selfKind().equals("ROLE_OCCURRENCE")?engine.objectForSemanticId(actor.roleId()):engine.objectForSemanticId(element.selfSourceId());
                var expected=engine.system().model().getClass(element.selfDeclaration());
                if(self==null||expected==null||!self.cls().isSubClassifierOf(expected)){unavailable.add("EXACT_SELF_UNAVAILABLE:"+element.elementId());continue;}
                selves.put(element.elementId(),engine.metadata(self,"semanticId"));
            }
            var selfEpisodes=new TreeMap<String,List<String>>();selves.forEach((id,value)->selfEpisodes.put(id,List.of(value,engine.metadata(engine.objectForSemanticId(value),"incarnation"))));
            String episode=SemanticJson.hash(List.of(program.hash(),contextId,engine.metadata(context,"incarnation"),SemanticJson.tree(actors),selfEpisodes));
            result.add(new ContextCast(contextId,episode,cast,actors,selves,unavailable));
            if(result.size()>4096)throw new IllegalStateException("CONTEXT_CAST_CAPACITY_EXCEEDED");
        }
        return List.copyOf(result);
    }
    private static boolean bound(NativeRuntimeMutationEngine engine,String association,MObject left,MObject right) {
        return engine.system().state().allLinks().stream().anyMatch(link->link.association().name().equals(association)&&link.linkedObjects().equals(List.of(left,right)));
    }
}
