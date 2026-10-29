package org.tzi.use.plugins.jacamo.semantics.mapping.xml;
import org.jacamo.bridge.contract.ModelSnapshot;
import org.tzi.use.plugins.jacamo.semantics.mapping.SemanticProgram;
import java.util.*;
import java.util.regex.*;
import org.jacamo.bridge.contract.CanonicalJson;
import org.tzi.use.plugins.jacamo.semantics.mapping.*;
import org.tzi.use.plugins.jacamo.semantics.evidence.*;
import org.tzi.use.semantic.engine.api.*;
import org.vnu.sme.goal.dsl.istar.mm.*;
import static org.tzi.use.plugins.jacamo.semantics.mapping.xml.MappingSpecification.Node;
public final class GenericMappingEngine {
    public SemanticProgram map(ModelSnapshot input,MappingInputs inputs) {
        EngineInstallationGuard.verify();verifySource(input,inputs);
        var source=new SourceModelNormalizer().normalize(input);var selections=SourceSelections.execute(inputs);
        var authored=new AuthoredModelLoader();
        var csl=inputs.authoredModels()?authored.csl(inputs):new CslRuleExecutor().prepare(inputs);
        var goals=inputs.authoredModels()?authored.goals(inputs):new IStarRuleExecutor().prepare(inputs);
        var bindings=new ArrayList<SemanticBindingIndex.Binding>();var instructions=new LinkedHashMap<String,List<SemanticBindingIndex.Binding>>();
        for(Node node:inputs.specification().root().child("bindings").children("static")) {
            var selected=node.optional("selectRef").isEmpty()?List.<Map<String,String>>of(Map.of()):SourceSelections.require(selections,node.attr("selectRef"),node).stream().map(SourceSelectors.Row::values).toList();
            var results=new ArrayList<SemanticBindingIndex.Binding>();
            for(var captures:selected) {
                String kind=render(node.attr("sourceKind"),captures,node),feature=node.attr("feature"),value=render(node.attr("value"),captures,node);
                var found=source.nodes().stream().filter(n->n.sourceKind().equals(kind)&&n.features().containsKey(feature)&&Objects.equals(n.features().get(feature).value(),value)).toList();
                if(found.size()!=1)throw node.location().error("STATIC_SOURCE_QUERY_NOT_UNIQUE:"+kind+":"+feature);
                var metadata=new TreeMap<String,String>();
                for(Node entry:node.children("metadata"))if(metadata.putIfAbsent(entry.attr("key"),render(entry.attr("value"),captures,entry))!=null)throw entry.location().error("STATIC_METADATA_DUPLICATE");
                SemanticBindingIndex.Kind targetKind;
                try{targetKind=SemanticBindingIndex.Kind.valueOf(node.attr("kind"));}catch(IllegalArgumentException error){throw node.location().error("STATIC_BINDING_KIND_UNSUPPORTED");}
                String target=render(node.attr("target"),captures,node);
                String symbol=node.optional("nativeSymbol");
                if(symbol.isBlank()&&Set.of(SemanticBindingIndex.Kind.ENTITY,SemanticBindingIndex.Kind.ROLE,SemanticBindingIndex.Kind.ORG_CONTEXT).contains(targetKind))symbol=target;
                var binding=new SemanticBindingIndex.Binding(found.getFirst().id(),targetKind,target,render(symbol,captures,node),node.attr("id"),node.attr("reason"),metadata);
                results.add(binding);bindings.add(binding);
            }
            instructions.put(node.attr("id"),List.copyOf(results));
        }
        var actorTemplates=new TreeMap<String,GoalRuntimeBindingPlan.ActorTemplate>();
        for(Node node:inputs.specification().root().child("bindings").children("actorRuntime")) {
            var binding=single(instructions,node.attr("bindingRef"),node);
            if(binding.kind()!=SemanticBindingIndex.Kind.ACTOR||!binding.targetId().equals(node.attr("actor")))throw node.location().error("ACTOR_RUNTIME_BINDING_MISMATCH");
            if(node.attr("activationPolicy").equals("MODEL_ONLY_CAPABILITY_ROLE")
                    &&(!goals.profile().roleSpecialization()
                        ||!RoleSpecialization.definitionOnly(goals.model(),goals.model().findActor(node.attr("actor")).orElseThrow())
                        ||!node.optional("roleBindingRef").isBlank()||!node.optional("contextBindingRef").isBlank()
                        ||!node.attr("episodePolicy").equals("MODEL_REVISION")
                        ||!source.require(binding.sourceId()).sourceKind().equals("JASON_AGENT_PROGRAM")||binding.abstractionReason().isBlank()))
                throw node.location().error("MODEL_ONLY_CAPABILITY_ROLE_REQUIRES_EXPLICIT_EMPTY_ROLE_ABSTRACTION");
            String role=optionalSource(instructions,node.optional("roleBindingRef"),node),context=optionalSource(instructions,node.optional("contextBindingRef"),node);
            if(actorTemplates.putIfAbsent(node.attr("actor"),new GoalRuntimeBindingPlan.ActorTemplate(node.attr("actor"),binding.sourceId(),role,context,"",node.attr("activationPolicy"),node.attr("episodePolicy")))!=null)throw node.location().error("ACTOR_RUNTIME_DUPLICATE");
        }
        if(!actorTemplates.keySet().equals(goals.model().getActors().stream().map(Actor::name).collect(java.util.stream.Collectors.toSet())))throw new IllegalArgumentException("XML_ACTOR_BINDING_COVERAGE");
        var elementTemplates=new TreeMap<String,GoalRuntimeBindingPlan.ElementTemplate>();
        for(Node node:inputs.specification().root().child("bindings").children("elementRuntime")) {
            String target=node.attr("target"),actor=node.attr("actor");
            var element=goals.model().findElement(target).orElseThrow(()->node.location().error("ELEMENT_RUNTIME_TARGET_UNRESOLVED"));
            if(!goals.model().ownerOf(target).orElse("").equals(actor)||!actorTemplates.containsKey(actor))throw node.location().error("ELEMENT_RUNTIME_WRONG_OWNER");
            if(!(element instanceof Goal)&&!(element instanceof Task))throw node.location().error("ELEMENT_RUNTIME_KIND_UNSUPPORTED");
            if(!(element instanceof Task)&&(!node.children("actionReceipt").isEmpty()||!node.children("acceptedFailure").isEmpty()||List.of("stepScope","scopePolicy","entryPolicy","failurePolicy","stepSchemeColumn","stepArtifactColumn").stream().anyMatch(k->!node.optional(k).isBlank())))throw node.location().error("TASK_OBSERVATION_POLICY_ON_NON_TASK");
            if(!Set.of("true","false").contains(node.attr("derived")))throw node.location().error("DERIVED_BOOLEAN_REQUIRED");
            boolean derived=Boolean.parseBoolean(node.attr("derived"));
            boolean structural=goals.model().getActors().stream().flatMap(a->a.refinements().stream()).anyMatch(r->r.parent().equals(target))
                ||goals.model().getDependencies().stream().anyMatch(d->target.equals(d.dependerElmt()));
            if(derived!=structural||element instanceof Task&&derived)throw node.location().error("DERIVED_STRUCTURE_MISMATCH");
            var self=bindings.stream().filter(b->Set.of(SemanticBindingIndex.Kind.ENTITY,SemanticBindingIndex.Kind.ROLE,SemanticBindingIndex.Kind.ORG_CONTEXT).contains(b.kind())&&b.targetId().equals(node.attr("self"))).toList();
            if(self.size()!=1)throw node.location().error("ELEMENT_SELF_BINDING_NOT_UNIQUE");
            var template=new GoalRuntimeBindingPlan.ElementTemplate(target,actor,derived,node.attr("self"),self.getFirst().sourceId(),node.attr("selfKind"),node.optional("contract"));
            if(elementTemplates.putIfAbsent(target,template)!=null)throw node.location().error("ELEMENT_RUNTIME_DUPLICATE");
            bindings.add(new SemanticBindingIndex.Binding(actorTemplates.get(actor).sourceId(),element instanceof Goal?SemanticBindingIndex.Kind.GOAL:SemanticBindingIndex.Kind.TASK,target,"",node.location().ruleId(),node.attr("reason"),Map.of()));
        }
        var expected=new TreeSet<String>();goals.model().getActors().forEach(a->a.elements().forEach(e->{if(e instanceof Goal||e instanceof Task)expected.add(e.id());}));
        if(!expected.equals(elementTemplates.keySet()))throw new IllegalArgumentException("XML_ELEMENT_BINDING_COVERAGE");
        var index=new SemanticBindingIndex(bindings);index.validate(source,csl.model(),goals.model(),List.of());
        for(var entity:csl.model().entities())requireTarget(index,SemanticBindingIndex.Kind.ENTITY,entity.name());
        for(var role:csl.model().roles())requireTarget(index,SemanticBindingIndex.Kind.ROLE,role.name());
        for(var context:csl.model().orgContexts())requireTarget(index,SemanticBindingIndex.Kind.ORG_CONTEXT,context.name());
        var goalBindings=new GoalRuntimeBindingPlan(actorTemplates,elementTemplates,List.of());
        var plan=new GenericBindingPlan(inputs,source,input,csl,goals,index,goalBindings);
        var profile=new JaCaMoMappingProfile(GenericBindingPlan.PROFILE_ID,GenericBindingPlan.VERSION,goals.profile(),Set.of("csl.v4","istar.original","source.complement","mapping.xml/2.0"));
        var rules=new MappingRuleSet("2.0",inputs.specification().root().attr("id"),profile.id(),profile.version(),source.sourceHash(),csl.generatedSource(),goals.generatedSource(),index.bindings(),List.of(),List.of(),plan.ruleOptions());
        var preserved=new SemanticTransformationService().preserveCanonical(input,source,index);
        var assets=new ArrayList<>(preserved.assets());assets.addAll(inputs.sources().values());assets.add(inputs.xml());assets.add(inputs.ocl());
        assets.add(TraceComplement.Asset.of("generic-plan","GENERIC_MAPPING_PLAN_JSON",CanonicalJson.encode(plan.evidence())));
        var complement=new TraceComplement(preserved.version(),preserved.ruleVersion(),preserved.sourceHash(),preserved.metamodelHash(),assets,preserved.decisions());
        return SemanticProgram.fromXml(profile,rules,source,csl.model(),goals.model(),index,complement,goalBindings,plan);
    }
    private static void verifySource(ModelSnapshot input,MappingInputs inputs) {
        var official=input.semanticContract();
        if(official.project()==null||!official.project().sourceDigest().equals(inputs.projectDigest()))throw new IllegalArgumentException("XML_OFFICIAL_PROJECT_DIGEST_MISMATCH");
        for(Node file:inputs.specification().root().child("sources").children("file")) {
            String digest=inputs.require(file.attr("id")).sha256();
            if(file.attr("kind").equals("ASL")&&official.jasonPrograms().stream().noneMatch(p->p.sourceDigest().equals(digest)))throw file.location().error("OFFICIAL_ASL_DIGEST_MISMATCH");
            if(file.attr("kind").equals("MOISE")&&official.moiseOrganizations().stream().noneMatch(o->o.metadata().evidence().stream().anyMatch(e->e.sourceDigest().equals(digest))))throw file.location().error("OFFICIAL_OS_DIGEST_MISMATCH");
        }
    }
    private static void requireTarget(SemanticBindingIndex index,SemanticBindingIndex.Kind kind,String target) {
        if(index.find(kind,target).isEmpty())throw new IllegalArgumentException("XML_CLASSIFIER_BINDING_REQUIRED:"+target);
    }
    private static SemanticBindingIndex.Binding single(Map<String,List<SemanticBindingIndex.Binding>> bindings,String id,Node at) {
        var found=bindings.get(id);if(found==null||found.size()!=1)throw at.location().error("STATIC_BINDING_REFERENCE_NOT_UNIQUE:"+id);return found.getFirst();
    }
    private static String optionalSource(Map<String,List<SemanticBindingIndex.Binding>> bindings,String id,Node at){return id.isEmpty()?"":single(bindings,id,at).sourceId();}
    private static String render(String text,Map<String,String> values,Node at) {
        var matcher=Pattern.compile("\\$\\{([A-Za-z_][A-Za-z0-9_]*)}").matcher(text);var output=new StringBuilder();
        while(matcher.find()){var value=values.get(matcher.group(1));if(value==null)throw at.location().error("STATIC_CAPTURE_REQUIRED");matcher.appendReplacement(output,Matcher.quoteReplacement(value));}
        matcher.appendTail(output);if(output.indexOf("${")>=0)throw at.location().error("STATIC_TEMPLATE_UNSUPPORTED");return output.toString();
    }
}
