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
import org.tzi.use.plugins.jacamo.semantics.mapping.compact.*;
import static org.tzi.use.plugins.jacamo.semantics.mapping.xml.MappingSpecification.Node;
public final class GenericMappingEngine {
    public SemanticProgram map(ModelSnapshot input,MappingInputs inputs) {
        EngineInstallationGuard.verify();
        if (inputs.isCompact()) {
            return mapCompact(input, inputs);
        }
        verifySource(input,inputs);
        var source=new SourceModelNormalizer().normalize(input);var selections=SourceSelections.execute(inputs);
        var authored=new AuthoredModelLoader();
        var csl=inputs.authoredModels()?authored.csl(inputs):new CslRuleExecutor().prepare(inputs);
        var goals=inputs.authoredModels()?authored.goals(inputs):new IStarRuleExecutor().prepare(inputs);
        var goalViews=org.tzi.use.plugins.jacamo.semantics.runtime.GoalViewRealizations.compile(inputs.specification(),goals.model());
        for(var view:goalViews.values()) {
            var file=inputs.specification().requireId(view.sourceRef());
            if(!file.name().equals("file")||!file.attr("kind").equals("MOISE"))throw file.location().error("GOAL_VIEW_ADMITTED_MOISE_SOURCE_REQUIRED");
            long matched=input.semanticContract().moiseOrganizations().stream().filter(o->o.metadata().evidence().stream().anyMatch(e->e.sourceDigest().equals(file.attr("sha256"))))
                    .flatMap(o->o.functionalSpecification().schemes().stream()).filter(s->s.schemeId().equals(view.scheme()))
                    .flatMap(s->s.goals().stream()).filter(g->g.goalId().equals(view.sourceGoal())).count();
            if(matched!=1)throw file.location().error("GOAL_VIEW_SOURCE_GOAL_NOT_UNIQUE");
        }
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
                ||goals.model().getDependencies().stream().anyMatch(d->target.equals(d.dependerElmt()))||goalViews.containsKey(target);
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

    public SemanticProgram mapCompact(ModelSnapshot input, MappingInputs inputs) {
        EngineInstallationGuard.verify();
        var official = input.semanticContract();
        if (official.project() != null && !inputs.projectDigest().isEmpty()
                && !official.project().sourceDigest().equals(inputs.projectDigest())) {
            throw new IllegalArgumentException("COMPACT_OFFICIAL_PROJECT_DIGEST_MISMATCH");
        }
        var compactSpec = inputs.compactSpecification();
        if (compactSpec == null) {
            throw new IllegalArgumentException("COMPACT_SPECIFICATION_REQUIRED");
        }

        // 1. Compile Authored Models
        if (!inputs.authoredModels()) {
            throw new IllegalArgumentException("COMPACT_MAPPING_AUTHORED_MODELS_REQUIRED");
        }
        var cslAsset = inputs.require(MappingInputs.AUTHORED_CSL);
        String cslText = new String(cslAsset.bytes(), java.nio.charset.StandardCharsets.UTF_8);
        var cslModel = new org.tzi.use.semantic.engine.frontend.CslFrontend().compile(cslText, cslAsset.id()).requireModel();

        var istarAsset = inputs.require(MappingInputs.AUTHORED_ISTAR);
        String istarText = new String(istarAsset.bytes(), java.nio.charset.StandardCharsets.UTF_8);
        var engineProfile = EngineSemanticsProfile.ROLE_SPECIALIZATION_V1;
        var istarModel = new org.tzi.use.semantic.engine.frontend.IStarFrontend().compile(istarText, istarAsset.id(), engineProfile).requireModel();

        // 2. Build Source and Target Catalogues
        var sourceCatalog = SourceSymbolCatalog.fromSnapshot(official);
        var targetCatalog = NativeTargetCatalog.fromModels(cslModel, istarModel);

        // 3. Compile Observation Plan (performs strict correspondence and type validation)
        String cslFp = CslModelFingerprint.of(cslModel);
        String goalFp = new OwnedGoalModel(istarModel, engineProfile).hash();
        var compactPlan = ObservationPlanCompiler.compile(
                compactSpec, sourceCatalog, targetCatalog,
                cslFp, goalFp, inputs.ocl().sha256(), engineProfile.name()).withInputs(inputs);

        // 4. Build Supporting Structures for SemanticProgram
        var source = new SourceModelNormalizer().normalize(input);
        var bindings = new ArrayList<SemanticBindingIndex.Binding>();
        var seenTargets = new HashSet<List<String>>();

        // 4.1 CSL Bindings
        for (var b : compactPlan.cslBindings()) {
            SemanticBindingIndex.Kind targetKind;
            String targetId;
            String nativeSymbol;
            Map<String, String> meta = new TreeMap<>();
            compactSpec.cslMappings().stream().filter(c->c.source().equals(b.sourceRef().raw())&&c.target().equals(b.targetRef().raw())).findFirst()
                    .ifPresent(c->c.attributes().forEach((k,v)->{if(Set.of("contextAbstraction","enactsRoles","converter").contains(k))meta.put(k,v);}));
            if (b.isAttribute()) {
                targetKind = SemanticBindingIndex.Kind.ATTRIBUTE;
                targetId = b.targetRef().qualifier() + "::" + b.targetRef().member();
                nativeSymbol = targetId;
            } else if (cslModel.findRole(b.targetRef().qualifier()).isPresent()) {
                targetKind = SemanticBindingIndex.Kind.ROLE;
                targetId = b.targetRef().qualifier();
                nativeSymbol = targetId;
            } else if (cslModel.findOrgContext(b.targetRef().qualifier()).isPresent()) {
                targetKind = SemanticBindingIndex.Kind.ORG_CONTEXT;
                targetId = b.targetRef().qualifier();
                nativeSymbol = targetId;
            } else {
                targetKind = SemanticBindingIndex.Kind.ENTITY;
                targetId = b.targetRef().qualifier();
                nativeSymbol = targetId;
            }
            String sourceId = resolveSourceNodeId(source, b.sourceRef(), targetKind, meta);
            if (seenTargets.add(List.of(targetKind.name(), targetId))) {
                bindings.add(new SemanticBindingIndex.Binding(
                        sourceId,
                        targetKind,
                        targetId,
                        nativeSymbol,
                        "CSL3",
                        "Compact mapping correspondence",
                        meta
                ));
            }
        }

        // Unmapped declarations remain model definitions. They do not acquire
        // a source object, role, group or runtime population by position/name.
        String projectNodeId = findProjectNodeId(source);

        // 4.2 iStar Actor and Element Bindings + GoalRuntimeBindingPlan
        var actorTemplates = new TreeMap<String, GoalRuntimeBindingPlan.ActorTemplate>();
        Map<String, ActorRuntimeBinding> explicitActorRuntimes = new LinkedHashMap<>();
        for (var ar : compactPlan.actorBindings()) {
            explicitActorRuntimes.put(ar.actorName(), ar);
        }
        Map<String, IStarBinding> actorIStarBindings = new LinkedHashMap<>();
        Map<String, IStarBinding> elementIStarBindings = new LinkedHashMap<>();
        for (var b : compactPlan.iStarBindings()) {
            if (b.kind() == IStarBinding.BindingKind.ACTOR_MAPPING) {
                actorIStarBindings.putIfAbsent(b.targetRef().qualifier(), b);
            } else {
                String elemId = b.targetRef().member() != null ? b.targetRef().member() : b.targetRef().qualifier();
                elementIStarBindings.putIfAbsent(elemId, b);
            }
        }

        for (Actor actor : istarModel.getActors()) {
            IStarBinding ib = actorIStarBindings.get(actor.name());
            ActorRuntimeBinding ar = explicitActorRuntimes.get(actor.name());
            if (ib == null) throw new IllegalArgumentException("COMPACT_ACTOR_SOURCE_MAPPING_REQUIRED:" + actor.name());
            String actorSourceId = resolveSourceNodeId(source, ib.sourceRef(), SemanticBindingIndex.Kind.ACTOR, new TreeMap<>());
            String roleSourceId = ib.sourceRef() instanceof SourceReference.MoiseRoleRef ? actorSourceId : "";
            String contextSourceId = "";
            String activationPolicy = ar != null ? ar.activationPolicy() : "EXPLICIT_ENACTMENT";
            String episodePolicy = ar != null ? ar.episodePolicy() : "STATIC_LIFETIME";
            actorTemplates.put(actor.name(), new GoalRuntimeBindingPlan.ActorTemplate(
                    actor.name(), actorSourceId, roleSourceId, contextSourceId, "", activationPolicy, episodePolicy));
            if (seenTargets.add(List.of(SemanticBindingIndex.Kind.ACTOR.name(), actor.name()))) {
                bindings.add(new SemanticBindingIndex.Binding(
                        actorSourceId, SemanticBindingIndex.Kind.ACTOR, actor.name(), actor.name(),
                        "ISTAR3", "Compact actor correspondence", Map.of()));
            }
        }

        var elementTemplates = new TreeMap<String, GoalRuntimeBindingPlan.ElementTemplate>();
        for (Actor actor : istarModel.getActors()) {
            String actorSelf = compactSpec.actorRuntimes().stream().filter(a -> a.actor().equals(actor.name()))
                    .map(a -> a.attributes().getOrDefault("self", "")).findFirst().orElse("");
            for (IntentionalElement elem : actor.elements()) {
                if (!(elem instanceof Goal) && !(elem instanceof Task)) continue;
                String elemId = elem.id();
                boolean structural = istarModel.getActors().stream().flatMap(a -> a.refinements().stream()).anyMatch(r -> r.parent().equals(elemId))
                        || istarModel.getDependencies().stream().anyMatch(d -> elemId.equals(d.dependerElmt()));
                if (elem instanceof Task) structural = false;
                if (elem instanceof Goal authored && !authored.conditions().isEmpty()) structural = false;

                String contractExpr = "";
                if (elem instanceof Goal g && !g.conditions().isEmpty()) {
                    contractExpr = g.oclSource();
                } else if (elem instanceof Task t) {
                    contractExpr = t.postconditions().stream().findFirst().map(IStarOclConstraint::oclBody).orElse("");
                }

                String selfDecl = compactSpec.istarMappings().stream()
                        .filter(c -> targetCatalog.resolve(TargetReference.parse(c.target()), "ISTAR").name().equals(elemId))
                        .map(c -> c.attributes().getOrDefault("self", actorSelf)).findFirst().orElse(actorSelf);
                if (!structural && !contractExpr.isBlank() && selfDecl.isBlank())
                    throw new IllegalArgumentException("COMPACT_EXPLICIT_NATIVE_SELF_REQUIRED:" + elemId);
                String selfKind = selfDecl.isBlank() ? "" : cslModel.findEntity(selfDecl).isPresent() ? "ENTITY"
                        : cslModel.findRole(selfDecl).isPresent() ? "ROLE" : cslModel.findOrgContext(selfDecl).isPresent() ? "ORG_CONTEXT" : "";
                if (!selfDecl.isBlank() && selfKind.isBlank()) throw new IllegalArgumentException("COMPACT_NATIVE_SELF_NOT_FOUND:" + selfDecl);
                final String receiver = selfDecl;
                String selfSourceId = selfDecl.isBlank() ? "" : bindings.stream()
                        .filter(b -> Set.of(SemanticBindingIndex.Kind.ENTITY, SemanticBindingIndex.Kind.ROLE, SemanticBindingIndex.Kind.ORG_CONTEXT).contains(b.kind()) && b.targetId().equals(receiver))
                        .map(SemanticBindingIndex.Binding::sourceId).findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("COMPACT_NATIVE_SELF_SOURCE_REQUIRED:" + receiver));
                elementTemplates.put(elemId, new GoalRuntimeBindingPlan.ElementTemplate(
                        elemId, actor.name(), structural, selfDecl, selfSourceId, selfKind, contractExpr));

                IStarBinding eb = elementIStarBindings.get(elemId);
                String elemSourceId = eb != null
                        ? resolveSourceNodeId(source, eb.sourceRef(), elem instanceof Goal ? SemanticBindingIndex.Kind.GOAL : SemanticBindingIndex.Kind.TASK, new TreeMap<>())
                        : actorTemplates.get(actor.name()).sourceId();
                var kind = elem instanceof Goal ? SemanticBindingIndex.Kind.GOAL : SemanticBindingIndex.Kind.TASK;
                if (seenTargets.add(List.of(kind.name(), elemId))) {
                    bindings.add(new SemanticBindingIndex.Binding(
                            elemSourceId, kind, elemId, "", "ISTAR3", "Compact intentional correspondence", Map.of()));
                }
            }
        }

        var goalBindings = new GoalRuntimeBindingPlan(actorTemplates, elementTemplates, List.of());
        var index = new SemanticBindingIndex(bindings);
        var profile = new JaCaMoMappingProfile("jacamo.mapping.compact", "3.0", engineProfile, Set.of("csl.v4", "istar.original", "source.complement", "mapping.xml/3.0"));
        var rules = new MappingRuleSet("3.0", compactSpec.id(), profile.id(), profile.version(), source.sourceHash(), cslText, istarText, index.bindings(), List.of(), List.of(), Map.of());

        var preserved = new SemanticTransformationService().preserveCanonical(input, source, index);
        var assets = new ArrayList<TraceComplement.Asset>(preserved.assets());
        assets.addAll(inputs.sources().values());
        assets.add(inputs.xml());
        assets.add(inputs.ocl());
        assets.add(TraceComplement.Asset.of("compact-plan", "COMPACT_PLAN_JSON", CanonicalJson.encode(compactPlan.evidenceSummary())));
        var complement = new TraceComplement(preserved.version(), preserved.ruleVersion(), preserved.sourceHash(), preserved.metamodelHash(), assets, preserved.decisions());

        return SemanticProgram.fromCompact(profile, rules, source, cslModel, istarModel, index, complement, goalBindings, compactPlan);
    }

    private static String findProjectNodeId(NormalizedSourceModel source) {
        return uniqueSource(source.nodes().stream().filter(n -> "JACAMO_PROJECT".equals(n.sourceKind())).toList(), "project");
    }
    private static String feature(NormalizedSourceModel.Node node,String key) {
        var value=node.features().get(key); return value != null && value.value() instanceof String text ? text : "";
    }
    private static String uniqueSource(List<NormalizedSourceModel.Node> candidates,String reference) {
        if(candidates.size()!=1) throw new IllegalArgumentException("COMPACT_SOURCE_NODE_UNAVAILABLE_OR_AMBIGUOUS:"+reference+":"+candidates.stream().map(NormalizedSourceModel.Node::id).toList());
        return candidates.getFirst().id();
    }
    private static boolean organization(NormalizedSourceModel source,NormalizedSourceModel.Node node,String name) {
        var visited=new HashSet<String>();
        while(node!=null && visited.add(node.id())) {
            if(node.sourceKind().equals("MOISE_OS")) return name.equals(feature(node,"name"));
            String parent=node.container();
            node=source.nodes().stream().filter(n -> n.id().equals(parent)).findFirst().orElse(null);
        }
        return false;
    }
    private static String resolveSourceNodeId(NormalizedSourceModel source, SourceReference ref,
            SemanticBindingIndex.Kind targetKind, Map<String,String> meta) {
        String type=switch(ref) {
            case SourceReference.ArtifactRef r -> r.className();
            case SourceReference.ArtifactPropertyRef r -> r.className();
            case SourceReference.ArtifactFieldRef r -> r.className();
            case SourceReference.ArtifactIdentityRef r -> r.className();
            case SourceReference.ArtifactOpRef r -> r.className();
            default -> "";
        };
        if(!type.isBlank()) {
            String id=org.tzi.use.plugins.jacamo.codegrounded.use.DomainProjection.artifactId(type);
            return uniqueSource(source.nodes().stream().filter(n -> n.id().equals(id)).toList(), ref.raw());
        }
        if(ref instanceof SourceReference.ProjectRef) {
            meta.put("contextAbstraction","PROJECT_EXECUTION_VIEW/1.0");return findProjectNodeId(source);
        }
        if(ref instanceof SourceReference.WorkspaceRef workspace) {
            meta.put("contextAbstraction","WORKSPACE_EXECUTION_VIEW/1.0");
            if(workspace.name().equals("*"))return findProjectNodeId(source);
            return uniqueSource(source.nodes().stream().filter(n->n.sourceKind().equals("JACAMO_WORKSPACE_DECLARATION")&&workspace.name().equals(feature(n,"name"))).toList(),ref.raw());
        }
        if(ref instanceof SourceReference.WorkspaceIdentityRef workspace)
            return resolveSourceNodeId(source,new SourceReference.WorkspaceRef(workspace.name(),"workspace:"+workspace.name()),targetKind,meta);
        if(ref instanceof SourceReference.OrganizationRef organization) {
            meta.put("contextAbstraction","ORGANIZATION_DEPLOYMENT_VIEW");
            return uniqueSource(source.nodes().stream().filter(n->n.sourceKind().equals("JACAMO_ORGANIZATION_DEPLOYMENT")&&organization.name().equals(feature(n,"name"))).toList(),ref.raw());
        }
        if(ref instanceof SourceReference.AgentRef selected) {
            var declarations=source.nodes().stream().filter(n->n.sourceKind().equals("JACAMO_AGENT_DECLARATION")&&selected.agentOrProgram().equals(feature(n,"name"))).toList();
            if(!declarations.isEmpty())return uniqueSource(declarations,ref.raw());
        }
        var candidates=source.nodes().stream().filter(n -> switch(ref) {
            case SourceReference.MoiseGroupRef r -> n.concept().equals("Group") && r.groupName().equals(feature(n,"groupId")) && organization(source,n,r.organization());
            case SourceReference.MoiseRoleRef r -> n.concept().equals("Role") && r.roleName().equals(feature(n,"roleId")) && organization(source,n,r.organization());
            case SourceReference.MoiseSchemeRef r -> n.sourceKind().equals("MOISE_SCHEME") && r.schemeName().equals(feature(n,"schemeId")) && organization(source,n,r.organization());
            case SourceReference.MoiseGoalRef r -> n.sourceKind().equals("MOISE_GOAL") && r.goalName().equals(feature(n,"goalId")) && organization(source,n,r.organization());
            case SourceReference.AgentRef r -> n.sourceKind().equals("JACAMO_AGENT_DECLARATION") && r.agentOrProgram().equals(feature(n,"name"))
                    || n.sourceKind().equals("DERIVED_JASON_PROGRAM_ALIAS") && programPath(feature(n,"sourceUri"), r.agentOrProgram());
            case SourceReference.AslGoalRef r -> n.sourceKind().equals("DERIVED_JASON_PROGRAM_ALIAS") && programPath(feature(n,"sourceUri"),r.aslPath());
            case SourceReference.AslPlanStepRef r -> n.sourceKind().equals("DERIVED_JASON_PROGRAM_ALIAS") && programPath(feature(n,"sourceUri"),r.aslPath());
            default -> false;
        }).toList();
        return uniqueSource(candidates, ref.raw());
    }
    private static boolean programPath(String actual,String selected) {
        String normalized=actual.replace('\\','/').replaceFirst("^project:/", "");
        String path=selected.replace('\\','/');
        if(!path.endsWith(".asl")) path += ".asl";
        return normalized.equals(path) || !path.contains("/") && normalized.endsWith("/"+path);
    }
}
