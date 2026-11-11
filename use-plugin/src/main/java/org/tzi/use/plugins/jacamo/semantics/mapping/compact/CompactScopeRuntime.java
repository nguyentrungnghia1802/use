package org.tzi.use.plugins.jacamo.semantics.mapping.compact;

import java.nio.charset.StandardCharsets;
import java.util.*;
import org.jacamo.bridge.contract.*;
import org.tzi.use.api.UseApiException;
import org.tzi.use.plugins.jacamo.semantics.evidence.SemanticJson;
import org.tzi.use.plugins.jacamo.semantics.mapping.*;
import org.tzi.use.plugins.jacamo.semantics.mapping.xml.runtime.*;
import org.tzi.use.plugins.jacamo.semantics.runtime.*;
import org.tzi.use.plugins.jacamo.semantics.runtime.SemanticObservationMapper.Port;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.sys.MObject;
import static org.tzi.use.plugins.jacamo.semantics.mapping.compact.CompactSourceCut.*;

/** Compiles actual source identities into native occurrence/demand metadata.
 * Metadata uses the authoritative writer/savepoint; this is not a live side model. */
public final class CompactScopeRuntime {
    private final SemanticProgram program;
    private final CompactSourceCut decoder;
    private final SemanticObservationMapper mapper;
    private final CompactInvariantActivation activation;
    public CompactScopeRuntime(SemanticProgram program,MModel model) {
        this.program=program;decoder=new CompactSourceCut(program);activation=new CompactInvariantActivation(program,model,false);
        mapper=new SemanticObservationMapper(new CanonicalObservationMappingBindings(program,model));
    }
    public void apply(Port port,RuntimeSnapshot cut,String run,long generation) throws UseApiException {
        String epoch=run+":"+generation;
        for(var object:port.objects())if(program.hash().equals(port.metadata(object,"compact:program"))) {
            port.metadata(object,"compact:source-scopes","");port.metadata(object,"compact:task-demands","");
        }
        for(var fact:cut.facts())if(fact.kind()==RuntimeFactKind.AGENT&&faithful(fact)) {
            String normalized=text(fact.values(),"normalizedEventKind");
            if(!Set.of("UPSERT_JASON_AGENT_STATE","UPSERT_CARTAGO_AGENT_IDENTITY").contains(normalized))continue;
            var mapped=mapper.apply(port,fact.id(),normalized,fact.values());
            mark(port,mapped.object(),epoch,"PLAYER",fact.id().canonical(),cut,true);
            if(fact.id().authority().equals("jason")) {
                var player=mapped.object();
                if(port.metadata(player,"compact:observer-floor").isBlank())port.metadata(player,"compact:observer-floor",Long.toString(floor(cut)));
                for(var binding:program.compactPlan().cslBindings())if(!binding.isAttribute()&&binding.sourceRef() instanceof SourceReference.AgentRef&&matches(binding.sourceSymbol(),fact)) {
                    String id="compact:agent-context:"+SemanticJson.hash(List.of(program.hash(),epoch,binding.targetRef().qualifier(),fact.id().canonical()));
                    var context=port.upsert(id,binding.targetRef().qualifier(),"agent-context");
                    port.metadata(context,"semanticId",id);
                    mark(port,context,epoch,"AGENT_CONTEXT",fact.id().canonical(),cut,true);
                    port.metadata(context,"compact:represented-player",mapped.identity());port.metadata(context,"compact:sourceRef",binding.sourceRef().raw());
                    port.connect("CslAgentContexts",context,player);
                    for(var attribute:program.compactPlan().cslBindings())if(attribute.isAttribute()&&attribute.targetRef().qualifier().equals(context.cls().name())&&attribute.sourceRef() instanceof SourceReference.AgentIdentityRef identity) {
                        var value=new org.tzi.use.uml.ocl.value.StringValue(identity.field().equals("name")?text(fact.values(),"name"):mapped.identity());
                        port.value(context,attribute.targetRef().member(),value);
                        CanonicalObservationMappingBindings.recordPropertyWritten(port,context,context.cls().attribute(attribute.targetRef().member(),true),value);
                        port.metadata(context,"propertySlot:"+attribute.targetRef().member(),"identity:"+fact.id().canonical());
                    }
                }
            }
        }
        for(var fact:cut.facts())if(fact.kind()==RuntimeFactKind.GROUP_BOARD&&faithful(fact)&&fact.id().authority().equals("moise")) {
            decoder.qualify(fact,run);
            if(program.bindings().bindings().stream().noneMatch(b->b.kind()==SemanticBindingIndex.Kind.ORG_CONTEXT&&b.sourceId().equals(fact.values().get("groupSpecSemanticId"))))continue;
            var mapped=mapper.apply(port,fact.id(),"UPSERT_MOISE_GROUP",fact.values());var group=mapped.object();
            mark(port,group,epoch,"GROUP",fact.id().canonical(),cut,true);
            for(var role:port.objects())if(mapped.identity().equals(port.metadata(role,"roleContext"))) {
                mark(port,role,epoch,"ROLE",port.metadata(role,"semanticId"),cut,true);
                port.metadata(role,"compact:lifetime",port.metadata(role,"semanticId"));
                if(port.metadata(role,"compact:observer-floor").isBlank())port.metadata(role,"compact:observer-floor",Long.toString(floor(cut)));
            }
        }
        activation.apply(port,epoch,cut.snapshotId());
        var frames=decoder.frames(cut,run,generation);
        var schemes=decoder.schemes(cut,run,generation);
        for(var scheme:schemes)for(var binding:program.compactPlan().cslBindings())if(!binding.isAttribute()&&binding.sourceRef() instanceof SourceReference.MoiseSchemeRef ref
                &&ref.schemeName().equals(scheme.publicState().definition().schemeId())&&ref.organization().equals(scheme.publicState().organization().name())) {
            String id="compact:scheme-context:"+SemanticJson.hash(List.of(program.hash(),epoch,binding.targetRef().qualifier(),scheme.board().id().canonical()));
            var context=port.upsert(id,binding.targetRef().qualifier(),"scheme-context");port.metadata(context,"semanticId",id);
            mark(port,context,epoch,"SCHEME",scheme.board().id().canonical(),cut,scheme.history().quality()==Completeness.COMPLETE);
            port.metadata(context,"compact:sourceRef",ref.raw());
            if(scheme.workspace()!=null)port.metadata(context,"compact:workspace",scheme.workspace().id().canonical());
        }
        var scopes=new ArrayList<OccurrenceProjection.Scope>();var demands=new ArrayList<TaskDemandProjection.Demand>();
        var used=new HashSet<String>();
        for(var scheme:schemes) {
            String schemeId=scheme.board().id().canonical();
            var related=frames.stream().filter(f->f.step().scheme().equals(schemeId)).toList();
            var initial=related.stream().filter(f->initialTask(f.step().task())).map(f->f.step().agent()+"|"+f.step().goalInvocation()).distinct().toList();
            if(initial.size()>1)throw new IllegalArgumentException("COMPACT_SCHEME_INITIAL_REQUEST_AMBIGUOUS");
            var episodes=new HashSet<String>();for(var f:related)episodes.add(f.step().agent()+"|"+f.step().goalInvocation());
            var selected=frames.stream().filter(f->episodes.contains(f.step().agent()+"|"+f.step().goalInvocation())).toList();
            selected.forEach(f->used.add(f.step().agent()+"|"+f.step().goalInvocation()));
            String scopeId="compact:execution:"+SemanticJson.hash(List.of(program.hash(),epoch,initial.isEmpty()?schemeId:initial.getFirst()));
            build(port,cut,epoch,scopeId,scheme,selected,scopes,demands);
        }
        var byEpisode=new LinkedHashMap<String,List<Frame>>();
        for(var frame:frames)if(!used.contains(frame.step().agent()+"|"+frame.step().goalInvocation())&&frame.step().scheme().isBlank())
            byEpisode.computeIfAbsent(frame.step().agent()+"|"+frame.step().goalInvocation(),k->new ArrayList<>()).add(frame);
        for(var entry:byEpisode.entrySet()) {
            String scopeId="compact:execution:"+SemanticJson.hash(List.of(program.hash(),epoch,entry.getKey()));
            build(port,cut,epoch,scopeId,null,entry.getValue(),scopes,demands);
        }
        // An Agent viewpoint without selected Tasks still requires actual players.
        for(var binding:program.compactPlan().istarBindings())if(binding.kind()==IStarBinding.BindingKind.ACTOR_MAPPING&&binding.sourceRef() instanceof SourceReference.AgentRef
                &&program.goalBindings().elements().values().stream().noneMatch(e->e.actorId().equals(binding.targetSymbol().name())&&program.goalModel().findElement(e.elementId()).orElse(null) instanceof org.vnu.sme.goal.dsl.istar.mm.Task)) {
            if("MODEL_ONLY_CAPABILITY_ROLE".equals(program.goalBindings().actors().get(binding.targetSymbol().name()).activationPolicy()))continue;
            for(var context:port.objects())if(epoch.equals(port.metadata(context,"compact:epoch"))&&binding.sourceRef().raw().equals(port.metadata(context,"compact:sourceRef"))) {
                var player=port.find(port.metadata(context,"compact:represented-player"));if(player==null)continue;
                String id="compact:execution:"+SemanticJson.hash(List.of(program.hash(),epoch,binding.targetSymbol().name(),port.metadata(context,"semanticId")));
                var member=member(port,binding.targetSymbol().name(),player,null,context,null,id);
                scopes.add(new OccurrenceProjection.Scope(id,binding.sourceRef().raw(),port.metadata(context,"semanticId"),port.metadata(context,"semanticId"),id,cut.snapshotId(),List.of(member),List.of(),Map.of(),Map.of()));
            }
        }
        // Store each exact scope once. The native transaction owns rollback.
        var owners=new LinkedHashMap<MObject,List<OccurrenceProjection.Scope>>();
        for(var scope:scopes){var anchor=port.find(scope.anchor());if(anchor==null)throw new IllegalArgumentException("COMPACT_SCOPE_ANCHOR_UNAVAILABLE");owners.computeIfAbsent(anchor,k->new ArrayList<>()).add(scope);}
        for(var entry:owners.entrySet()) {
            port.metadata(entry.getKey(),"compact:source-scopes",json(entry.getValue()));
            var ids=entry.getValue().stream().map(OccurrenceProjection.Scope::id).collect(java.util.stream.Collectors.toSet());
            port.metadata(entry.getKey(),"compact:task-demands",json(demands.stream().filter(d->ids.contains(d.scope())).toList()));
        }
    }
    private void build(Port port,RuntimeSnapshot cut,String epoch,String id,Scheme scheme,List<Frame> frames,List<OccurrenceProjection.Scope> scopes,List<TaskDemandProjection.Demand> demands) {
        var members=new ArrayList<OccurrenceProjection.Member>();var issues=new ArrayList<String>();var resources=new TreeMap<String,String>();
        MObject domain=null,context=null;
        if(scheme!=null) {
            var candidates=frames.stream().map(f->f.step().artifact()).filter(s->!s.isBlank()).map(port::find).filter(Objects::nonNull).distinct().toList();
            if(candidates.size()>1)throw new IllegalArgumentException("COMPACT_SCHEME_DOMAIN_REFERENCE_AMBIGUOUS");
            if(!candidates.isEmpty())domain=candidates.getFirst();
            for(String group:scheme.publicState().responsibleContexts()){var found=port.find(group);if(found!=null){if(context!=null&&context!=found)throw new IllegalArgumentException("COMPACT_MULTIPLE_RESPONSIBLE_CONTEXTS_REQUIRE_EXPLICIT_VIEW");context=found;}}
            if(context==null&&domain!=null)issues.add("COMPACT_RESPONSIBLE_CONTEXT_UNAVAILABLE");
            if(scheme.history().quality()!=Completeness.COMPLETE)issues.add("COMPACT_SCHEME_CUT_UNAVAILABLE:"+scheme.history().diagnostic());
            if(context!=null) {
                if(!"COMPLETE_OBSERVED_BOARD".equals(port.metadata(context,"rolePopulationQuality")))issues.add("COMPACT_RESPONSIBLE_ROSTER_UNRESOLVED");
                if(scheme.workspace()!=null)port.metadata(context,"compact:workspace",scheme.workspace().id().canonical());
            }
            if(domain!=null&&context!=null)try{port.connect("CslEntityScopes",context,domain);}catch(UseApiException invalid){throw new IllegalArgumentException("COMPACT_DOMAIN_DECLARATION_SCOPE_INVALID",invalid);}
            for(var binding:program.compactPlan().istarBindings())if(binding.kind()==IStarBinding.BindingKind.RESOURCE_MAPPING) {
                final var sourceDomain=domain;
                if(binding.sourceRef() instanceof SourceReference.ArtifactRef&&sourceDomain!=null&&program.compactPlan().cslBindings().stream().anyMatch(c->!c.isAttribute()&&c.sourceRef().raw().equals(binding.sourceRef().raw())&&c.targetRef().qualifier().equals(sourceDomain.cls().name())))resources.put(binding.targetSymbol().name(),port.metadata(sourceDomain,"semanticId"));
                if(binding.sourceRef() instanceof SourceReference.MoiseSchemeRef) {
                    var contexts=port.objects().stream().filter(o->epoch.equals(port.metadata(o,"compact:epoch"))&&"SCHEME".equals(port.metadata(o,"compact:kind"))&&scheme.board().id().canonical().equals(port.metadata(o,"compact:physical"))&&binding.sourceRef().raw().equals(port.metadata(o,"compact:sourceRef"))).toList();
                    if(contexts.size()==1)resources.put(binding.targetSymbol().name(),port.metadata(contexts.getFirst(),"semanticId"));
                }
            }
        }
        for(var binding:program.compactPlan().istarBindings())if(binding.kind()==IStarBinding.BindingKind.ACTOR_MAPPING) {
            String actor=binding.targetSymbol().name();
            if("MODEL_ONLY_CAPABILITY_ROLE".equals(program.goalBindings().actors().get(actor).activationPolicy()))continue;
            if(binding.sourceRef() instanceof SourceReference.AgentRef) {
                for(var player:port.objects())if(epoch.equals(port.metadata(player,"compact:epoch"))&&"PLAYER".equals(port.metadata(player,"compact:kind"))) {
                    String playerId=port.metadata(player,"semanticId");
                    boolean observed=frames.stream().anyMatch(f->port.find(f.step().agent())==player&&program.goalBindings().elements().containsKey(f.step().task())&&program.goalBindings().elements().get(f.step().task()).actorId().equals(actor));
                    if(!observed&&scheme==null)continue;
                    if(!observed&&scheme!=null) {
                        final var responsible=context;
                        boolean roleMember=port.objects().stream().anyMatch(o->port.find(port.metadata(o,"rolePlayer"))==player&&port.find(port.metadata(o,"roleContext"))==responsible);
                        boolean committed=scheme.publicState().commitments().stream().anyMatch(c->port.find(c.player())==player);
                        if(!roleMember&&!committed)continue;
                    }
                    var contexts=port.objects().stream().filter(o->epoch.equals(port.metadata(o,"compact:epoch"))&&binding.sourceRef().raw().equals(port.metadata(o,"compact:sourceRef"))&&playerId.equals(port.metadata(o,"compact:represented-player"))).toList();
                    if(contexts.size()>1)throw new IllegalArgumentException("COMPACT_AGENT_CONTEXT_AMBIGUOUS");
                    var selfContext=contexts.isEmpty()?player:contexts.getFirst();
                    var member=member(port,actor,player,null,selfContext,domain,id);members.add(member);
                    if(context==null)context=selfContext;
                }
            }else if(binding.sourceRef() instanceof SourceReference.MoiseRoleRef&&scheme!=null&&context!=null) {
                String source=program.bindings().find(SemanticBindingIndex.Kind.ACTOR,actor).orElseThrow().sourceId();
                for(var role:port.objects())if(source.equals(port.metadata(role,"roleSourceId"))&&port.find(port.metadata(role,"roleContext"))==context) {
                    var player=port.find(port.metadata(role,"rolePlayer"));if(player!=null)members.add(member(port,actor,player,role,context,domain,id));
                }
            }else if(binding.sourceRef() instanceof SourceReference.ArtifactRef&&domain!=null) {
                final var actualDomain=domain;
                if(program.compactPlan().cslBindings().stream().anyMatch(c->!c.isAttribute()&&c.sourceRef().raw().equals(binding.sourceRef().raw())&&domainClass(c,actualDomain)))
                    members.add(member(port,actor,domain,null,context==null?domain:context,domain,id));
            }
        }
        if(members.isEmpty())return;
        if(context==null)throw new IllegalArgumentException("COMPACT_ACTUAL_CONTEXT_REQUIRED");
        var anchor=domain==null?context:domain;
        var bindings=new TreeMap<String,Object>();bindings.put("scheme",scheme==null?"":scheme.board().id().canonical());bindings.put("artifact",domain==null?"":port.metadata(domain,"semanticId"));
        bindings.put("goalInvocations",frames.stream().map(f->f.step().goalInvocation()).distinct().toList());
        bindings.put("workspace",port.metadata(anchor,"compact:workspace"));
        for(var frame:frames)for(var input:object(frame.step().bindings().get("inputs")).entrySet())bindings.putIfAbsent("input:"+input.getKey(),object(input.getValue()).get("value"));
        if(domain==null) {
            var selectedPlans=frames.stream().map(f->program.compactPlan().istarBindings().stream().filter(b->b.kind()==IStarBinding.BindingKind.TASK_MAPPING&&b.targetSymbol().name().equals(f.step().task())).findFirst().orElse(null)).filter(Objects::nonNull).map(IStarBinding::sourceRef).filter(SourceReference.AslPlanStepRef.class::isInstance).map(SourceReference.AslPlanStepRef.class::cast).toList();
            var leaves=program.compactPlan().istarBindings().stream().filter(b->b.kind()==IStarBinding.BindingKind.TASK_MAPPING&&b.sourceRef() instanceof SourceReference.AslPlanStepRef step&&selectedPlans.stream().anyMatch(p->p.aslPath().equals(step.aslPath())&&p.planTrigger().equals(step.planTrigger()))).map(b->b.targetSymbol().name()).sorted().toList();
            bindings.put("sourceBootstrap",true);bindings.put("sourceLeaves",leaves);
        }
        String definition=scheme==null?program.compactPlan().istarBindings().stream().filter(b->b.kind()==IStarBinding.BindingKind.ACTOR_MAPPING&&b.sourceRef() instanceof SourceReference.AgentRef&&members.stream().anyMatch(m->m.actor().equals(b.targetSymbol().name()))).findFirst().orElseThrow().sourceRef().raw():"source:scheme-execution";
        var scope=new OccurrenceProjection.Scope(id,definition,port.metadata(anchor,"semanticId"),port.metadata(context,"semanticId"),id,cut.snapshotId(),members,issues,bindings,resources);scopes.add(scope);
        for(var member:members)for(var task:program.compactPlan().istarBindings())if(task.kind()==IStarBinding.BindingKind.TASK_MAPPING&&program.goalBindings().elements().get(task.targetSymbol().name()).actorId().equals(member.actor())) {
            var observed=frames.stream().filter(f->f.step().task().equals(task.targetSymbol().name())&&port.find(f.step().agent())==port.find(member.player())).toList();
            var episodes=observed.stream().map(f->f.step().goalInvocation()).distinct().toList();
            if(episodes.size()>1)throw new IllegalArgumentException("COMPACT_OVERLAPPING_TASK_DEMAND_REQUIRES_EPISODE_SCOPE");
            Boolean required=observed.isEmpty()?assignment(port,member,task.targetSymbol().name(),scheme):Boolean.TRUE;
            String episode=episodes.isEmpty()?"demand:"+SemanticJson.hash(List.of(id,member.player(),task.targetSymbol().name())):episodes.getFirst();
            demands.add(new TaskDemandProjection.Demand("COMPACT_QUALIFIED_SOURCE_DEMAND/1.0",task.targetSymbol().name(),id,member.actor(),member.player(),member.role(),member.interval(),required,episode,cut.snapshotId(),required==null?Completeness.UNAVAILABLE:Completeness.COMPLETE,required==null?"COMPACT_CURRENT_ASSIGNMENT_UNAVAILABLE":"",required==null?Map.of("authority","ADMITTED_INVOCATION_OR_PUBLIC_SCHEME_COMMITMENT"):Map.of("authority","ADMITTED_INVOCATION_OR_PUBLIC_SCHEME_COMMITMENT","assigned",required.toString())));
        }
    }
    private Boolean assignment(Port port,OccurrenceProjection.Member member,String task,Scheme scheme) {
        if(scheme==null)return null;
        var goals=sourceGoals(task);if(goals.isEmpty())return false;
        var applicable=scheme.publicState().definition().missions().stream().filter(m->m.goalSemanticIds().stream().anyMatch(id->scheme.publicState().definition().goals().stream().anyMatch(g->g.metadata().semanticId().equals(id)&&goals.contains(g.goalId())))).map(m->m.metadata().semanticId()).toList();
        if(applicable.isEmpty())return false;
        return scheme.publicState().commitments().stream().anyMatch(c->port.find(c.player())==port.find(member.player())&&applicable.contains(c.mission()));
    }
    private Set<String> sourceGoals(String task) {
        var result=new TreeSet<String>();var ancestors=new HashSet<String>();ancestors.add(task);boolean changed;
        do {changed=false;for(var actor:program.goalModel().getActors())for(var ref:actor.refinements()) {
            var children=ref instanceof org.vnu.sme.goal.dsl.istar.mm.AndRefinement a?a.children():List.of(((org.vnu.sme.goal.dsl.istar.mm.OrRefinement)ref).child());
            if(children.stream().anyMatch(ancestors::contains))changed|=ancestors.add(ref.parent());
        }}while(changed);
        for(var view:program.compactPlan().specification().goalRealizations())if(ancestors.contains(view.source()))result.add(view.sourceGoal());
        return result;
    }
    private boolean initialTask(String task){return program.compactPlan().istarBindings().stream().filter(b->b.kind()==IStarBinding.BindingKind.TASK_MAPPING&&b.targetSymbol().name().equals(task)).anyMatch(b->b.sourceRef() instanceof SourceReference.AslPlanStepRef s&&program.compactPlan().istarBindings().stream().anyMatch(g->g.sourceRef() instanceof SourceReference.AslGoalRef goal&&goal.aslPath().equals(s.aslPath())&&s.planTrigger().startsWith(goal.goalTrigger())));}
    private boolean matches(SourceSymbol selected,RuntimeFact fact) {
        String declaration=text(fact.values(),"agentDeclarationSemanticId");
        var agents=decoder.source().semanticContract().agentDeclarations().stream().filter(a->a.metadata().semanticId().equals(declaration)).toList();
        if(agents.size()!=1)return false;
        var agent=agents.getFirst();if(selected.owner()==null)return selected.simpleName().equals(agent.name());
        return decoder.source().semanticContract().jasonPrograms().stream().anyMatch(p->p.declarationId().equals(agent.name())&&p.sourceUri().equals("project:/"+selected.owner()));
    }
    private OccurrenceProjection.Member member(Port port,String actor,MObject player,MObject role,MObject context,MObject domain,String scope) {
        String playerId=port.metadata(player,"semanticId"),roleId=role==null?"":port.metadata(role,"semanticId");var selves=new TreeMap<String,String>();
        for(var e:program.goalBindings().elements().values())if(e.actorId().equals(actor)&&!e.derived()&&!e.selfDeclaration().isBlank()) {
            MObject self=domain!=null&&domain.cls().name().equals(e.selfDeclaration())?domain:role!=null&&role.cls().name().equals(e.selfDeclaration())?role:context!=null&&context.cls().name().equals(e.selfDeclaration())?context:null;
            if(self!=null)selves.put(e.elementId(),port.metadata(self,"semanticId"));
        }
        String interval=role==null?playerId:port.metadata(role,"compact:lifetime");
        return new OccurrenceProjection.Member(actor,actor+"_"+SemanticJson.hash(List.of(playerId,roleId,interval)).substring(0,24),"member_"+SemanticJson.hash(List.of(scope,actor,playerId,roleId,interval)),playerId,roleId,port.metadata(context,"semanticId"),interval,selves);
    }
    private static boolean domainClass(CslBinding binding,MObject domain){return binding.targetRef().qualifier().equals(domain.cls().name());}
    private void mark(Port port,MObject object,String epoch,String kind,String physical,RuntimeSnapshot cut,boolean complete) {
        port.metadata(object,"compact:program",program.hash());port.metadata(object,"compact:epoch",epoch);port.metadata(object,"compact:kind",kind);port.metadata(object,"compact:physical",physical);
        port.metadata(object,"compact:quality",complete?"COMPLETE":"UNAVAILABLE");port.metadata(object,"compact:cut",cut.snapshotId());
    }
    private static long floor(RuntimeSnapshot cut){return cut.endWatermarks().getOrDefault(VerificationObservation.SOURCE_ID,new SourceWatermark(VerificationObservation.SOURCE_ID,0)).sequence();}
    private static String json(Object value){return new String(CanonicalJson.encode(SemanticJson.tree(value)),StandardCharsets.UTF_8);}
    public static List<OccurrenceProjection.Scope> scopes(Port port,String program,String epoch) {
        var result=new ArrayList<OccurrenceProjection.Scope>();
        for(var object:port.objects())if(program.equals(port.metadata(object,"compact:program"))&&epoch.equals(port.metadata(object,"compact:epoch"))) {
            for(var raw:rows(port.metadata(object,"compact:source-scopes"))) {
                var s=CanonicalJson.object(raw);var members=new ArrayList<OccurrenceProjection.Member>();
                for(var item:(List<?>)s.get("members")){var m=CanonicalJson.object(item);members.add(new OccurrenceProjection.Member(text(m,"actor"),text(m,"actorType"),text(m,"instance"),text(m,"player"),text(m,"role"),text(m,"context"),text(m,"interval"),strings(object(m.get("selves")))));}
                result.add(new OccurrenceProjection.Scope(text(s,"id"),text(s,"definition"),text(s,"anchor"),text(s,"context"),text(s,"revision"),text(s,"cut"),members,((List<?>)s.get("unavailable")).stream().map(String.class::cast).toList(),object(s.get("bindings")),strings(object(s.get("resources")))));
            }
        }
        return List.copyOf(result);
    }
    public static List<TaskDemandProjection.Demand> demands(Port port,String program,String epoch) {
        var result=new ArrayList<TaskDemandProjection.Demand>();
        for(var object:port.objects())if(program.equals(port.metadata(object,"compact:program"))&&epoch.equals(port.metadata(object,"compact:epoch")))for(var raw:rows(port.metadata(object,"compact:task-demands"))) {
            var d=CanonicalJson.object(raw);result.add(new TaskDemandProjection.Demand(text(d,"rule"),text(d,"target"),text(d,"scope"),text(d,"actor"),text(d,"player"),text(d,"role"),text(d,"roleInterval"),(Boolean)d.get("required"),text(d,"episode"),text(d,"cut"),Completeness.valueOf(text(d,"quality")),text(d,"diagnostic"),strings(object(d.get("witness")))));
        }
        return List.copyOf(result);
    }
    private static Map<String,String> strings(Map<String,Object> values){var r=new TreeMap<String,String>();values.forEach((k,v)->r.put(k,(String)v));return r;}
    private static List<?> rows(String raw){return raw.isBlank()?List.of():(List<?>)CanonicalJson.decode(raw.getBytes(StandardCharsets.UTF_8));}
}
