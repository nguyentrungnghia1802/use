package org.tzi.use.plugins.jacamo.semantics.mapping;

import java.util.*;
import org.tzi.use.plugins.jacamo.semantics.evidence.SemanticJson;
import org.vnu.sme.goal.dsl.csl.mm.CslModel;
import org.vnu.sme.goal.dsl.istar.mm.*;

/** Static declaration crosswalk only. Runtime UUID/incarnation bindings belong to the session owner. */
public final class SemanticBindingIndex {
    public enum Kind { ENTITY, ROLE, ORG_CONTEXT, ACTOR, GOAL, TASK, CONTRACT, ATTRIBUTE, ASSOCIATION, REFINEMENT }
    public record Binding(String sourceId,Kind kind,String targetId,String nativeSymbol,String ruleId,String abstractionReason,Map<String,String> metadata) {
        public Binding {
            Objects.requireNonNull(sourceId);Objects.requireNonNull(kind);Objects.requireNonNull(targetId);Objects.requireNonNull(nativeSymbol);Objects.requireNonNull(ruleId);Objects.requireNonNull(abstractionReason);
            if(sourceId.isBlank()||targetId.isBlank()||ruleId.isBlank())throw new IllegalArgumentException("STATIC_BINDING_ID_REQUIRED");metadata=Collections.unmodifiableMap(new TreeMap<>(metadata));
            for(String key:metadata.keySet())if(Set.of("runtimeUuid","incarnation","runId").contains(key))throw new IllegalArgumentException("RUNTIME_IDENTITY_IN_STATIC_BINDING");
        }
    }
    private final List<Binding> bindings;
    public SemanticBindingIndex(List<Binding> bindings) {
        var sorted=new ArrayList<>(bindings);sorted.sort(Comparator.comparing(Binding::sourceId).thenComparing(b->b.kind().name()).thenComparing(Binding::targetId));
        var targets=new HashSet<List<String>>();for(var binding:sorted)if(!targets.add(List.of(binding.kind().name(),binding.targetId())))throw new IllegalArgumentException("STATIC_TARGET_BINDING_AMBIGUOUS:"+binding.targetId());
        this.bindings=List.copyOf(sorted);
    }
    public List<Binding> bindings(){return bindings;}
    public Optional<Binding> find(Kind kind,String target){return bindings.stream().filter(b->b.kind()==kind&&b.targetId().equals(target)).findFirst();}
    public List<Binding> source(String id){return bindings.stream().filter(b->b.sourceId().equals(id)).toList();}
    public String hash(){return SemanticJson.hash(SemanticJson.tree(bindings));}
    public void validate(NormalizedSourceModel source,CslModel csl,GoalModel goals,List<MappingRuleSet.Contract> contracts) {
        for(var binding:bindings) {
            var node=source.require(binding.sourceId());
            boolean exists=switch(binding.kind()) {
                case ENTITY->csl.findEntity(binding.targetId()).isPresent();case ROLE->csl.findRole(binding.targetId()).isPresent();case ORG_CONTEXT->csl.findOrgContext(binding.targetId()).isPresent();
                case ACTOR->goals.findActor(binding.targetId()).isPresent();case GOAL->goals.findElement(binding.targetId()).orElse(null) instanceof Goal;
                case TASK->goals.findElement(binding.targetId()).orElse(null) instanceof Task;case CONTRACT->contracts.stream().anyMatch(c->c.id().equals(binding.targetId()));
                case ATTRIBUTE->attributeExists(csl,binding.targetId());case ASSOCIATION->csl.relations().stream().anyMatch(r->r.name().equals(binding.targetId()));
                case REFINEMENT->goals.getActors().stream().flatMap(a->a.refinements().stream()).anyMatch(r->r.parent().equals(binding.targetId()));
            };
            if(!exists)throw new IllegalArgumentException("STATIC_BINDING_TARGET_UNRESOLVED:"+binding.targetId());
            if(binding.kind()==Kind.ENTITY&&!node.concept().equals("Artifact"))throw new IllegalArgumentException("ENTITY_REQUIRES_REGISTERED_ARTIFACT_VIEW:"+node.concept());
            if(binding.kind()==Kind.ENTITY&&(node.layer()!=NormalizedSourceModel.Layer.MODEL_DEFINITION
                    ||Set.of("CARTAGO_ARTIFACT","CARTAGO_ARTIFACT_INFO").contains(node.sourceKind())))throw new IllegalArgumentException("ENTITY_REQUIRES_ARTIFACT_TYPE_VIEW:"+node.sourceKind());
            if(binding.kind()==Kind.ROLE&&!node.concept().equals("Role")
                    &&!(node.sourceKind().equals("JASON_AGENT_PROGRAM")&&node.layer()==NormalizedSourceModel.Layer.MODEL_DEFINITION
                        && "ASL_CAPABILITY_VIEW".equals(binding.metadata().get("roleAbstraction"))&&!binding.abstractionReason().isBlank()
                        &&csl.generalizations().stream().anyMatch(g->g.general().equals(binding.targetId()))))throw new IllegalArgumentException("ROLE_SOURCE_KIND_REQUIRED");
            if(binding.kind()==Kind.ORG_CONTEXT&&!node.concept().equals("Group")
                    && !(node.sourceKind().equals("JACAMO_ORGANIZATION_DEPLOYMENT")
                        && "ORGANIZATION_DEPLOYMENT_VIEW".equals(binding.metadata().get("contextAbstraction"))
                        && !binding.abstractionReason().isBlank())
                    && !(node.sourceKind().equals("JACAMO_PROJECT")&&node.layer()==NormalizedSourceModel.Layer.MODEL_DEFINITION
                        && Set.of("PROJECT_EXECUTION_VIEW/1.0","SINGLE_AGENT_CONTEXT/1.0").contains(binding.metadata().get("contextAbstraction"))
                        && !binding.abstractionReason().isBlank()))throw new IllegalArgumentException("ORG_CONTEXT_SOURCE_GROUP_OR_EXPLICIT_ORGANIZATION_VIEW_REQUIRED");
            if(Set.of("Belief","AGoal","Plan","OPlan","Norm").contains(node.concept())&&binding.abstractionReason().isBlank())throw new IllegalArgumentException("EXPLICIT_SEMANTIC_ABSTRACTION_REQUIRED:"+node.concept());
        }
    }
    private static boolean attributeExists(CslModel model,String id) {
        int split=id.indexOf("::");if(split<1)return false;String type=id.substring(0,split),name=id.substring(split+2);
        // Same-instance CSL inheritance: the binding names the concrete receiver,
        // while the property may be declared on an ancestor. No declaration copy.
        var pending=new ArrayDeque<String>();pending.add(type);var visited=new HashSet<String>();
        while(!pending.isEmpty()) {
            String current=pending.remove();if(!visited.add(current))continue;
            if(model.entities().stream().anyMatch(e->e.name().equals(current)&&e.attributes().stream().anyMatch(a->a.name().equals(name)))
                    ||model.roles().stream().anyMatch(e->e.name().equals(current)&&e.attributes().stream().anyMatch(a->a.name().equals(name)))
                    ||model.orgContexts().stream().anyMatch(e->e.name().equals(current)&&e.attributes().stream().anyMatch(a->a.name().equals(name))))return true;
            model.generalizations().stream().filter(e->e.specific().equals(current)).forEach(e->pending.add(e.general()));
        }
        return false;
    }
}
