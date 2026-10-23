package org.tzi.use.plugins.jacamo.semantics.mapping.auction;

import java.util.*;
import org.jacamo.bridge.contract.ModelSnapshot;
import org.tzi.use.plugins.jacamo.codegrounded.use.DomainProjection;
import org.tzi.use.plugins.jacamo.semantics.evidence.SemanticJson;
import org.tzi.use.plugins.jacamo.semantics.mapping.*;
import org.tzi.use.semantic.engine.api.*;
import org.vnu.sme.goal.dsl.csl.mm.CslModel;
import org.vnu.sme.goal.dsl.istar.mm.*;
import static org.tzi.use.plugins.jacamo.semantics.mapping.SemanticBindingIndex.Kind.*;

/** Frozen static execution bindings. Runtime UUIDs, values, histories and USE objects do not belong here. */
public final class AuctionBindingPlan {
    public static final String PROFILE_ID="JACAMO_AUCTION_XML", VERSION="1.0";
    public record ActorBinding(String cell,String actor,String kind,String declaration,String sourceId,String roleSourceId,String contextSourceId) { }
    public record TaskBinding(String task,String actor,String selfDeclaration,String sourceId,List<String> operations,List<String> observerViews) {
        public TaskBinding {operations=List.copyOf(operations);observerViews=List.copyOf(observerViews);}
    }
    private final AuctionCaseBundle bundle;
    private final AuctionMappingSpecification specification;
    private final AuctionSelectorResult selectors;
    private final AuctionIStarSourceAdapter.Prepared iStar;
    private final AuctionOclSpecificationLoader.Specification ocl;
    private final AuctionJcmDerivation jcmDerivation;
    private final List<ActorBinding> actors;
    private final List<TaskBinding> tasks;
    private final SemanticBindingIndex bindings;
    private final GoalRuntimeBindingPlan goalBindings;
    private final String cslHash,cslSource,goalHash,hash;

    AuctionBindingPlan(ModelSnapshot input,NormalizedSourceModel source,AuctionCaseBundle bundle,AuctionMappingSpecification specification,
            AuctionSelectorResult selectors,AuctionCslRuleExecutor.Result csl,GoalModel goals,AuctionIStarSourceAdapter.Prepared iStar,
            AuctionOclSpecificationLoader.Specification ocl,AuctionJcmDerivation jcmDerivation) {
        this.bundle=bundle;this.specification=specification;this.selectors=selectors;this.iStar=iStar;this.ocl=ocl;
        jcmDerivation.requireOwnership(bundle,input);this.jcmDerivation=jcmDerivation;
        var typed=input.semanticContract();
        var selectedOrg=selectors.require("sel-org").getFirst().values();
        var deployment=exact(typed.organizationDeployments().stream().filter(o->o.name().equals(selectedOrg.get("instance"))&&AuctionSourceSelector.organizationSourceMatches(o,selectedOrg.get("spec"),bundle,typed)).toList(),"ORGANIZATION_DEPLOYMENT");
        var organization=exact(typed.moiseOrganizations().stream().filter(o->o.name().equals(selectors.require("sel-os-root").getFirst().values().get("id"))).toList(),"ORGANIZATION_DEFINITION");
        var group=exact(organization.structuralSpecification().groups().stream().filter(g->g.groupId().equals(selectors.require("sel-os-group").getFirst().values().get("id"))).toList(),"GROUP_DEFINITION");
        String root=specification.root().child("profile").child("scopeAlias").attr("targetOrgCtx");
        if(csl.model().findOrgContext(root).isEmpty())throw invalid("ROOT_CONTEXT_UNRESOLVED");
        String fqcn=selectors.require("sel-create").getFirst().values().get("className"),entitySource=DomainProjection.artifactId(fqcn);
        var references=SourceArtifactTypeReferences.fromActions(input).stream().filter(r->r.javaClass().equals(fqcn)).toList();
        if(references.isEmpty())throw invalid("DYNAMIC_CLASS_PROOF_REQUIRED");
        var proof=references.getFirst().classProof();
        if(references.stream().anyMatch(r->!r.classProof().sourceDigest().equals(proof.sourceDigest())||!r.classProof().sourceUri().equals(proof.sourceUri())))throw invalid("DYNAMIC_CLASS_PROOF_CONFLICT");
        var entityTrace=exact(csl.traces().stream().filter(t->t.ruleId().equals("M05")).toList(),"ENTITY_RULE_TRACE");
        String entity=entityTrace.target();
        var staticBindings=new ArrayList<SemanticBindingIndex.Binding>();
        staticBindings.add(binding(deployment.metadata().semanticId(),ORG_CONTEXT,root,"M01","Explicit organization deployment viewpoint, not a Group or Entity",
                Map.of("contextAbstraction","ORGANIZATION_DEPLOYMENT_VIEW","organizationDefinition",organization.metadata().semanticId())));
        staticBindings.add(binding(group.metadata().semanticId(),ORG_CONTEXT,group.groupId(),"M02","Nested group declaration scope",Map.of()));
        for(var role:organization.structuralSpecification().roles())if(csl.model().findRole(role.roleId()).isPresent())
            staticBindings.add(binding(role.metadata().semanticId(),ROLE,role.roleId(),"M03","Explicit MOISE role declaration",Map.of("contextSourceId",group.metadata().semanticId())));
        staticBindings.add(binding(entitySource,ENTITY,entity,"M05","Passive Artifact state; configured requests are not instances",
                Map.of("javaClass",fqcn,"classHash",proof.sourceDigest(),"classResource",proof.sourceUri())));
        var actorRows=new ArrayList<ActorBinding>();var actorTemplates=new TreeMap<String,GoalRuntimeBindingPlan.ActorTemplate>();
        var verification=specification.root().child("verificationBindings");
        for(var node:verification.children("roleActorBinding")) {
            String actor=actor(iStar,node.attr("istarCell"),goals);
            var role=exact(organization.structuralSpecification().roles().stream().filter(r->r.roleId().equals(node.attr("cslRole"))).toList(),"ACTOR_ROLE_DECLARATION");
            if(!(goals.findActor(actor).orElseThrow() instanceof Role)||csl.model().findRole(role.roleId()).isEmpty())throw invalid("ROLE_ACTOR_KIND_MISMATCH");
            actorRows.add(new ActorBinding(node.attr("istarCell"),actor,"ROLE_OCCURRENCE",role.roleId(),role.metadata().semanticId(),role.metadata().semanticId(),group.metadata().semanticId()));
        }
        for(var node:verification.children("organizationActorBinding")) {
            if(!node.attr("kind").equals("viewpoint-only")||!node.attr("inventCoordinatorRole").equals("false")||!node.attr("cslRoleCorrespondence").equals("not-supplied")||!node.attr("cslContext").equals(root))throw invalid("ORGANIZATION_VIEWPOINT_BOUNDARY");
            actorRows.add(new ActorBinding(node.attr("istarCell"),actor(iStar,node.attr("istarCell"),goals),"ORGANIZATION_VIEWPOINT",root,deployment.metadata().semanticId(),"",deployment.metadata().semanticId()));
        }
        if(actorRows.size()!=goals.getActors().size()||actorRows.stream().map(ActorBinding::actor).distinct().count()!=actorRows.size())throw invalid("ACTOR_BINDING_COVERAGE");
        actorRows.sort(Comparator.comparing(ActorBinding::cell));actors=List.copyOf(actorRows);
        for(var actor:actors) {
            staticBindings.add(binding(actor.sourceId(),ACTOR,actor.actor(),"XML_ACTOR_BINDING",actor.kind(),Map.of("cell",actor.cell(),"cslDeclaration",actor.declaration())));
            actorTemplates.put(actor.actor(),new GoalRuntimeBindingPlan.ActorTemplate(actor.actor(),actor.sourceId(),actor.roleSourceId(),actor.contextSourceId(),"","OBSERVED_SCHEME_COMMITMENT","RUN_SCHEME_PLAYER_INVOCATION"));
        }
        var taskRows=new ArrayList<TaskBinding>();var elementTemplates=new TreeMap<String,GoalRuntimeBindingPlan.ElementTemplate>();
        for(var actor:goals.getActors())for(var element:actor.elements()) {
            var actorBinding=exact(actors.stream().filter(a->a.actor().equals(actor.name())).toList(),"ELEMENT_ACTOR");
            if(element instanceof Task task) {
                var operations=verification.children("operationBinding").stream().filter(n->n.attr("istarTask").equals(task.id())).map(n->n.attr("id")).sorted().toList();
                var views=verification.children("taskBinding").stream().filter(n->n.attr("istarCell").equals(task.id())).map(n->n.attr("observerViewRef")).distinct().sorted().toList();
                if(operations.isEmpty()&&views.isEmpty()||task.preconditions().size()!=1||task.postconditions().size()!=1)throw invalid("TASK_BINDING_OR_PREDICATES_UNRESOLVED:"+task.id());
                for(String view:views)if(!specification.requireId(view).name().equals("view"))throw invalid("TASK_VIEW_UNRESOLVED");
                taskRows.add(new TaskBinding(task.id(),actor.name(),entity,actorBinding.sourceId(),operations,views));
                elementTemplates.put(task.id(),new GoalRuntimeBindingPlan.ElementTemplate(task.id(),actor.name(),false,entity,entitySource,"SCHEME_ARTIFACT",""));
                staticBindings.add(binding(actorBinding.sourceId(),TASK,task.id(),"XML_TASK_BINDING","Reviewed Jason execution abstraction; no state-only completion",Map.of("sourceAsset","src-asl")));
            }else if(element instanceof Goal goal) {
                boolean derived=actor.refinements().stream().anyMatch(r->r.parent().equals(goal.id()))||goals.getDependencies().stream().anyMatch(d->goal.id().equals(d.dependerElmt()));
                if(!derived||goal.oclSource()!=null&&!goal.oclSource().isBlank())throw invalid("GOAL_PROPAGATION_BOUNDARY:"+goal.id());
                elementTemplates.put(goal.id(),new GoalRuntimeBindingPlan.ElementTemplate(goal.id(),actor.name(),true,entity,entitySource,"SCHEME_ARTIFACT",""));
                staticBindings.add(binding(actorBinding.sourceId(),GOAL,goal.id(),"XML_GOAL_EVALUATION","Original AND/dependency propagation; no administrative truth cast",Map.of()));
            }
        }
        for(var node:verification.children("operationBinding"))if(!(goals.findElement(node.attr("istarTask")).orElse(null) instanceof Task))throw invalid("OPERATION_TASK_UNRESOLVED");
        for(var node:verification.children("taskBinding"))if(!(goals.findElement(node.attr("istarCell")).orElse(null) instanceof Task))throw invalid("STEP_TASK_UNRESOLVED");
        var expectedGoals=new TreeSet<String>();goals.allElements().forEach((id,e)->{if(e instanceof Goal)expectedGoals.add(id);});goals.getDependencies().forEach(d->expectedGoals.add(d.dependum().id()));
        if(!expectedGoals.equals(new TreeSet<>(List.of(verification.child("goalEvaluation").attr("istarCells").split("\\s+")))))throw invalid("GOAL_EVALUATION_COVERAGE");
        var goalPolicy=verification.child("goalEvaluation");
        if(!goalPolicy.attr("method").equals("existing-AND-and-Dependency-propagation")||!goalPolicy.attr("adminSatisfiedFlagDefinesActualAchievement").equals("false")||!goalPolicy.attr("emitCSLGoalFlags").equals("false"))throw invalid("GOAL_EVALUATION_POLICY");
        var observer=new AuctionObserverUseBinding(specification);observer.valueType("view-step");
        for(var node:verification.children("externalInvariant"))if(!observer.classViews().containsKey(node.attr("observerViewRef"))||ocl.clauses().stream().noneMatch(c->c.name().equals(node.attr("name"))&&c.kind()==AuctionOclSpecificationLoader.Kind.INVARIANT))throw invalid("EXTERNAL_INVARIANT_BINDING");
        taskRows.sort(Comparator.comparing(TaskBinding::task));tasks=List.copyOf(taskRows);bindings=new SemanticBindingIndex(staticBindings);
        goalBindings=new GoalRuntimeBindingPlan(actorTemplates,elementTemplates,List.of());
        cslHash=csl.fingerprint();cslSource=csl.generatedSource();goalHash=OwnedGoalModel.fingerprint(goals);
        hash=SemanticJson.hash(evidence());
    }
    public AuctionCaseBundle bundle(){return bundle;} public AuctionMappingSpecification specification(){return specification;}
    public AuctionSelectorResult selectors(){return selectors;} public AuctionIStarSourceAdapter.Prepared iStar(){return iStar;}
    public AuctionOclSpecificationLoader.Specification ocl(){return ocl;} public List<ActorBinding> actors(){return actors;}
    public List<TaskBinding> tasks(){return tasks;} public SemanticBindingIndex bindings(){return bindings;}
    public GoalRuntimeBindingPlan goalBindings(){return goalBindings;} public String hash(){return hash;}
    public Map<String,Object> evidence() {
        var evidence=new TreeMap<String,Object>();evidence.put("inputFormat","AUCTION_XML/1.0");evidence.put("bundleHash",bundle.hash());
        evidence.put("specificationHash",specification.sourceHash());evidence.put("sourceHash",selectors.sourceHash());
        evidence.put("cslHash",cslHash);evidence.put("goalHash",goalHash);evidence.put("oclHash",ocl.sourceHash());
        evidence.put("iStarOriginalHash",iStar.originalHash());evidence.put("iStarExecutableHash",iStar.executableHash());
        evidence.put("jcmDerivation",jcmDerivation.evidence());
        evidence.put("actors",SemanticJson.tree(actors));evidence.put("tasks",SemanticJson.tree(tasks));
        evidence.put("views",SemanticJson.tree(specification.views()));evidence.put("verificationBindings",SemanticJson.tree(specification.root().child("verificationBindings")));
        evidence.put("assetHashes",bundle.assets().values().stream().map(a->Map.of("id",a.id(),"sha256",a.sha256())).toList());return Collections.unmodifiableMap(evidence);
    }
    public void requireOwnership(JaCaMoMappingProfile profile,MappingRuleSet rules,NormalizedSourceModel source,CslModel csl,GoalModel goals,
            SemanticBindingIndex actualBindings,GoalRuntimeBindingPlan actualGoalBindings) {
        if(!profile.id().equals(PROFILE_ID)||!profile.version().equals(VERSION)||profile.engine()!=EngineSemanticsProfile.DERIVED_AND_RESOURCE_V1
                ||!rules.profileId().equals(PROFILE_ID)||!rules.profileVersion().equals(VERSION)||!hash.equals(rules.options().get("casePlanHash"))
                ||!rules.id().equals(specification.root().child("profile").attr("id"))||!source.sourceHash().equals(rules.expectedSourceHash())
                ||!rules.cslSource().equals(cslSource)||!rules.iStarSource().equals(iStar.executableSource())
                ||!rules.bindings().equals(bindings.bindings())||!actualBindings.hash().equals(bindings.hash())
                ||!SemanticJson.hash(SemanticJson.tree(actualGoalBindings)).equals(SemanticJson.hash(SemanticJson.tree(goalBindings)))
                ||!rules.contracts().isEmpty()||!rules.decisions().isEmpty()
                ||!rules.options().equals(Map.of("inputFormat","AUCTION_XML/1.0","bundleHash",bundle.hash(),"specificationHash",specification.sourceHash(),"casePlanHash",hash))
                ||!source.sourceHash().equals(selectors.sourceHash())||!cslHash.equals(CslModelFingerprint.of(csl))||!goalHash.equals(OwnedGoalModel.fingerprint(goals)))throw invalid("PROGRAM_OWNERSHIP_MISMATCH");
    }
    private static String actor(AuctionIStarSourceAdapter.Prepared source,String cell,GoalModel model) {
        String name=source.actorAliases().get(cell);if(name==null||model.findActor(name).isEmpty())throw invalid("ACTOR_CELL_UNRESOLVED:"+cell);return name;
    }
    private static <T> T exact(List<T> rows,String what){if(rows.size()!=1)throw invalid(what+"_NOT_UNIQUE");return rows.getFirst();}
    private static SemanticBindingIndex.Binding binding(String source,SemanticBindingIndex.Kind kind,String target,String rule,String reason,Map<String,String> metadata) {
        return new SemanticBindingIndex.Binding(source,kind,target,Set.of(ENTITY,ROLE,ORG_CONTEXT).contains(kind)?target:"",rule,reason,metadata);
    }
    private static IllegalArgumentException invalid(String reason){return new IllegalArgumentException("AUCTION_BINDING_"+reason);}
}
