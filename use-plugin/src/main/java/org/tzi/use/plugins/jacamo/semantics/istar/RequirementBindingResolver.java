package org.tzi.use.plugins.jacamo.semantics.istar;

import java.util.*;
import org.tzi.use.uml.sys.MObject;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.NativeRuntimeMutationEngine;
import org.tzi.use.plugins.jacamo.codegrounded.use.NativeUseModelBuilder;
import org.tzi.use.plugins.jacamo.semantics.csl.CslUseBinding;

public final class RequirementBindingResolver {
    private final org.tzi.use.uml.mm.MModel boundModel;private final org.tzi.use.plugins.jacamo.semantics.profile.CslMonitorConfiguration configuration;private final Map<String,org.tzi.use.uml.mm.MClass> cached;
    public RequirementBindingResolver(){boundModel=null;configuration=null;cached=Map.of();}
    public RequirementBindingResolver(org.tzi.use.uml.mm.MModel model,org.tzi.use.plugins.jacamo.semantics.profile.CslMonitorConfiguration config){this(model,config,true);}
    public RequirementBindingResolver(org.tzi.use.uml.mm.MModel model,org.tzi.use.plugins.jacamo.semantics.profile.CslMonitorConfiguration config,boolean cache){boundModel=model;configuration=config;var values=new HashMap<String,org.tzi.use.uml.mm.MClass>();if(cache)for(var cls:config.schema().classifiers())values.put(cls.id(),model.getClass(cls.symbol()));if(cache)for(var node:config.requirements().nodes())values.put(node.binding().selfTypeId(),model.getClass(CslUseBinding.typeName(config,node.binding().selfTypeId())));cached=Map.copyOf(values);}
    private org.tzi.use.uml.mm.MClass type(NativeRuntimeMutationEngine engine,String id,org.tzi.use.plugins.jacamo.semantics.profile.CslMonitorConfiguration config){if(boundModel!=null && boundModel!=engine.system().model())throw new IllegalArgumentException("REQUIREMENT_BINDING_MODEL_MISMATCH");return cached.containsKey(id)?cached.get(id):engine.system().model().getClass(CslUseBinding.typeName(config,id));}
    public record Resolved(MObject self,String token,String diagnostic) {public boolean available(){return self!=null;}}
    public Resolved resolve(NativeRuntimeMutationEngine engine,RequirementModel.Binding binding,Set<String> freshContexts) {
        var self=engine.objectForSemanticId(binding.selfIdentity());var player=engine.objectForSemanticId(binding.playerIdentity());
        var context=engine.objectForSemanticId(binding.contextIdentity());
        if(self==null || player==null || context==null)return new Resolved(null,"","EXACT_REQUIREMENT_BINDING_UNAVAILABLE");
        var config=configuration==null?CslUseBinding.configuration(engine.system().model()):configuration;
        var expected=type(engine,binding.selfTypeId(),config);
        if(!self.cls().isSubClassifierOf(expected))return new Resolved(null,"","REQUIREMENT_SELF_TYPE_MISMATCH");
        String contextId=engine.metadata(context,"semanticId"),playerId=engine.metadata(player,"semanticId");
        if(!freshContexts.contains(contextId))return new Resolved(null,"","BOUND_CONTEXT_OBSERVATION_INCOMPLETE");
        var roles=engine.system().state().allObjects().stream().filter(r->contextId.equals(engine.metadata(r,"roleContext"))
            && playerId.equals(engine.metadata(r,"rolePlayer")) && r.cls().isSubClassifierOf(type(engine,binding.roleTypeId(),config))).toList();
        if(roles.size()!=1)return new Resolved(null,"",roles.isEmpty()?"ACCEPTED_ENACTMENT_UNAVAILABLE":"ENACTMENT_BINDING_AMBIGUOUS");
        String token=NativeUseModelBuilder.canonicalJson(List.of(binding.actorId(),playerId,contextId,engine.metadata(roles.getFirst(),"semanticId"),engine.metadata(self,"semanticId"),binding.episodeId()));
        return new Resolved(self,token,"");
    }
}
