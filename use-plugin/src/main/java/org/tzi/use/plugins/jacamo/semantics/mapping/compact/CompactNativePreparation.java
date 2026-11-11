package org.tzi.use.plugins.jacamo.semantics.mapping.compact;

import org.tzi.use.plugins.jacamo.semantics.mapping.compact.runtime.*;

import java.util.*;
import org.jacamo.bridge.contract.*;
import org.jacamo.bridge.contract.semantic.SourceEvidence;
import org.tzi.use.api.*;
import org.tzi.use.plugins.jacamo.codegrounded.trace.CodeGroundedTraceCollector;
import org.tzi.use.plugins.jacamo.codegrounded.use.DomainProjection;
import org.tzi.use.plugins.jacamo.semantics.evidence.SemanticJson;
import org.tzi.use.plugins.jacamo.semantics.mapping.*;
import org.tzi.use.plugins.jacamo.semantics.mapping.ocl.*;
import org.tzi.use.semantic.engine.use75.*;
import org.tzi.use.uml.mm.*;
import org.tzi.use.uml.ocl.type.Type;
import org.vnu.sme.goal.dsl.istar.mm.*;

/** Installs selected authored contracts on the existing unpublished native model.
 * No inferred classes/field types, source signatures or alternate evaluation engine. */
public final class CompactNativePreparation {
    public JaCaMoOclContractMapping.Result prepare(ModelSnapshot source,SemanticProgram program,
            Use75ModelBinding binding,CodeGroundedTraceCollector trace) {
        program.assertUnchanged();program.requireSourceHash(SemanticJson.hash(ContractPayloads.model(source)));
        var api=new UseModelApi(binding.model());
        try {
            new CslScopeModelAdapter().prepare(api,binding);
            if(program.compactPlan().observerSchema().isPresent() && binding.model().getAnnotation(CompactObserverSchema.ANNOTATION) == null) {
                program.compactPlan().observerSchema().get().install(api,binding);
            }
            new CanonicalUseBindingAdapter().install(program,binding,trace);
            var inputs=program.compactPlan().mappingInputs().orElseThrow(()->new IllegalArgumentException("COMPACT_OWNED_INPUTS_REQUIRED"));
            var authored=ModelNativeContracts.read(inputs);
            for(var clause:authored.clauses())if(binding.model().getClass(clause.context().owner())==null)
                throw new IllegalArgumentException("COMPACT_NATIVE_CONTEXT_REQUIRES_TYPED_MODEL_OR_VIEW_BINDING:"+clause.context().owner());
            for(var context:authored.operations())installOperation(source,program,api,context);
            var expected=new LinkedHashMap<String,AuthoredOclSpecificationLoader.Context>();
            authored.clauses().forEach(c->expected.put(c.name(),c.context()));
            var prepared=new AuthoredOclSpecificationLoader().compile(authored,binding,authored.sourceHash(),expected);
            prepared.install(api);
            new CompactInvariantActivation(program,binding.model(),true);
            var intentional=intentional(program,binding.model());
            requireSupportedRuntime(program);
            org.tzi.use.plugins.jacamo.semantics.runtime.RequirementInventory.from(program,binding.model());
            return intentional;
        }catch(UseApiException error){throw new IllegalArgumentException("COMPACT_NATIVE_PREPARATION_FAILED",error);}
    }
    /** Runtime support is explicit; unsupported source families fail before MODEL_READY. */
    public static void requireSupportedRuntime(SemanticProgram program) {
        var plan=program.compactPlan();var spec=plan.specification();
        if(!spec.observerViews().isEmpty())new CompactObserverSchema(spec.observerViews(),spec.sha256());
        for(var task:program.goalModel().getActors().stream().flatMap(a->a.elements().stream()).filter(Task.class::isInstance).toList())
            if(plan.istarBindings().stream().filter(b->b.kind()==IStarBinding.BindingKind.TASK_MAPPING&&b.targetSymbol().name().equals(task.id())).count()!=1)
                throw new IllegalArgumentException("COMPACT_TASK_EXACT_SOURCE_MAPPING_REQUIRED:"+task.id());
        org.tzi.use.plugins.jacamo.semantics.runtime.GoalViewRealizations.compile(spec.goalRealizations(),program.goalModel());
        org.tzi.use.plugins.jacamo.semantics.runtime.OccurrenceGoalModelExpander.policies(spec.dependencyPopulations(),program.goalModel());
        for(var binding:plan.cslBindings())if(!(binding.sourceRef() instanceof SourceReference.WorkspaceRef
                ||binding.sourceRef() instanceof SourceReference.WorkspaceIdentityRef||binding.sourceRef() instanceof SourceReference.ArtifactRef
                ||binding.sourceRef() instanceof SourceReference.ArtifactIdentityRef||binding.sourceRef() instanceof SourceReference.ArtifactPropertyRef
                ||binding.sourceRef() instanceof SourceReference.ArtifactFieldRef||binding.sourceRef() instanceof SourceReference.AgentRef
                ||binding.sourceRef() instanceof SourceReference.AgentIdentityRef||binding.sourceRef() instanceof SourceReference.MoiseGroupRef
                ||binding.sourceRef() instanceof SourceReference.MoiseRoleRef||binding.sourceRef() instanceof SourceReference.MoiseSchemeRef))
            throw new IllegalArgumentException("COMPACT_SOURCE_FAMILY_UNSUPPORTED:"+binding.sourceRef().raw());
        for(var actor:plan.istarBindings())if(actor.kind()==IStarBinding.BindingKind.ACTOR_MAPPING) {
            var policy=program.goalBindings().actors().get(actor.targetSymbol().name());
            if(spec.actorRuntimes().stream().noneMatch(a->a.actor().equals(policy.actorId())))throw new IllegalArgumentException("COMPACT_SOURCE_QUALIFIED_SCOPE_AND_TASK_INTEGRATION_REQUIRED:"+policy.actorId());
            if("MODEL_ONLY_CAPABILITY_ROLE".equals(policy.activationPolicy())) {
                if(program.goalBindings().elements().values().stream().anyMatch(e->e.actorId().equals(policy.actorId())&&program.goalModel().findElement(e.elementId()).orElse(null) instanceof Task))
                    throw new IllegalArgumentException("COMPACT_DEFINITION_ONLY_ACTOR_CANNOT_EXECUTE:"+policy.actorId());
                continue;
            }
            if(!(actor.sourceRef() instanceof SourceReference.WorkspaceRef||actor.sourceRef() instanceof SourceReference.ArtifactRef
                    ||actor.sourceRef() instanceof SourceReference.AgentRef||actor.sourceRef() instanceof SourceReference.MoiseRoleRef))
                throw new IllegalArgumentException("COMPACT_ACTOR_SOURCE_FAMILY_UNSUPPORTED:"+actor.sourceRef().raw());
            if(actor.sourceRef() instanceof SourceReference.WorkspaceRef||actor.sourceRef() instanceof SourceReference.ArtifactRef&&"PROJECT_EXECUTION_VIEW".equals(policy.activationPolicy())) {
                if(!"PROJECT_EXECUTION_VIEW".equals(policy.activationPolicy()))throw new IllegalArgumentException("COMPACT_CONTEXT_VIEW_ACTIVATION_POLICY_REQUIRED:"+policy.actorId());
                if(!"STATIC_LIFETIME".equals(policy.episodePolicy()))throw new IllegalArgumentException("COMPACT_WORKSPACE_EPISODE_POLICY_UNSUPPORTED:"+policy.actorId());
                var contexts=plan.cslBindings().stream().filter(c->!c.isAttribute()&&c.sourceRef().raw().equals(actor.sourceRef().raw())).toList();
                if(contexts.size()!=1)throw new IllegalArgumentException("COMPACT_CONTEXT_VIEW_NOT_UNIQUE:"+policy.actorId());
                for(var element:program.goalBindings().elements().values())if(element.actorId().equals(policy.actorId())&&!element.selfDeclaration().isBlank()
                        &&!element.selfDeclaration().equals(contexts.getFirst().targetRef().qualifier()))throw new IllegalArgumentException("COMPACT_CONTEXT_SELF_BINDING_UNSUPPORTED:"+element.elementId());
            }
        }
    }
    /** Compiles the original Goal/Task slots; runtime evaluation remains native-session owned. */
    public static JaCaMoOclContractMapping.Result intentional(SemanticProgram program,MModel model) {
        var contracts=new ArrayList<JaCaMoOclContractMapping.BoundContract>();
        try(var compiler=new NativeOclBackend(model,program.hash(),program.profile().engine().name())) {
            for(var template:program.goalBindings().elements().values()) {
                if(template.derived())continue;
                var element=program.goalModel().findElement(template.elementId()).orElseThrow();
                if(element instanceof Goal goal&&goal.conditions().isEmpty())continue;
                if(element instanceof Task task&&(task.preconditions().size()!=1||task.postconditions().size()!=1))
                    throw new IllegalArgumentException("COMPACT_TASK_AUTHORED_PRE_POST_REQUIRED:"+task.id());
                var self=model.getClass(template.selfDeclaration());
                if(self==null)throw new IllegalArgumentException("COMPACT_INTENTIONAL_SELF_UNRESOLVED:"+template.elementId());
                if(element instanceof Goal goal&&!goal.conditions().isEmpty())
                    add(contracts,compiler,new OclContractSpec("GOAL:"+goal.id(),OclContractSpec.Kind.GOAL,self,null,goal.oclSource()),goal.id());
                if(element instanceof Task task) {
                    if(task.preconditions().size()!=1||task.postconditions().size()!=1)continue;
                    for(boolean pre:List.of(true,false)) {
                        var body=(pre?task.preconditions():task.postconditions()).getFirst().oclBody();
                        var vars=new TreeMap<String,Type>();
                        if(body.contains("step."))try {vars.put("step",new UseModelApi(model).getType("Tuple(enabled:Boolean,succeeded:Boolean,completed:Boolean)"));}
                            catch(UseApiException invalid){throw new IllegalArgumentException("COMPACT_STEP_TYPE_INVALID",invalid);}
                        for(var op:self.allOperations())for(var parameter:op.paramList())
                            if(body.matches("(?s).*\\b"+java.util.regex.Pattern.quote(parameter.name())+"\\b.*")) {
                                Type old=vars.putIfAbsent(parameter.name(),parameter.type());
                                if(old!=null&&!old.equals(parameter.type()))throw new IllegalArgumentException("COMPACT_PARAMETER_TYPE_AMBIGUOUS:"+parameter.name());
                            }
                        add(contracts,compiler,new OclContractSpec("TASK:"+task.id()+(pre?":pre":":post"),pre?OclContractSpec.Kind.TASK_PRE:OclContractSpec.Kind.TASK_POST,self,null,body,vars),task.id());
                    }
                }
            }
        }
        return new JaCaMoOclContractMapping.Result(contracts);
    }
    private static void add(List<JaCaMoOclContractMapping.BoundContract> out,NativeOclBackend compiler,OclContractSpec spec,String element) {
        compiler.compile(spec);out.add(new JaCaMoOclContractMapping.BoundContract(spec.id(),element,spec,SemanticJson.hash(spec.body()),"AUTHORED_COMPACT_NATIVE_CONDITION",List.of()));
    }
    private record Candidate(String source,SourceEvidence proof,List<Object> signature) {}
    private static void installOperation(ModelSnapshot source,SemanticProgram program,UseModelApi api,AuthoredOclSpecificationLoader.Context context) throws UseApiException {
        var entity=program.bindings().find(SemanticBindingIndex.Kind.ENTITY,context.owner()).orElseThrow(()->new IllegalArgumentException("COMPACT_OPERATION_ENTITY_MAPPING_REQUIRED:"+context.owner()));
        String type=JaCaMoCslMappingRules.javaClass(program.source(),entity);var proofs=new ArrayList<Candidate>();
        for(var declaration:source.semanticContract().artifactDeclarations())if(declaration.javaClass().equals(type))
            for(var proof:declaration.metadata().evidence())collect(proofs,declaration.metadata().semanticId(),proof,context);
        for(var reference:SourceArtifactTypeReferences.fromActions(source))if(reference.javaClass().equals(type))
            for(var proof:reference.operations())collect(proofs,reference.sourceId(),proof,context);
        var signatures=proofs.stream().map(c->CanonicalJson.encode(c.signature())).map(java.util.Base64.getEncoder()::encodeToString).distinct().toList();
        if(signatures.size()!=1)throw new IllegalArgumentException("COMPACT_OPERATION_SOURCE_SIGNATURE_UNAVAILABLE_OR_AMBIGUOUS:"+context.owner()+"::"+context.operation());
        var candidate=proofs.getFirst();
        DomainProjection.declaredOperation(api,context.owner(),candidate.signature(),candidate.source(),candidate.proof(),
                new DomainProjection.OperationExposure(context.operation(),context.parameters().stream().map(AuthoredOclSpecificationLoader.Parameter::name).toList()));
    }
    private static void collect(List<Candidate> out,String sourceId,SourceEvidence proof,AuthoredOclSpecificationLoader.Context context) {
        for(var diagnostic:proof.diagnostics())if(diagnostic.startsWith("SIGNATURE:")) {
            Object raw=CanonicalJson.decode(diagnostic.substring("SIGNATURE:".length()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            if(!(raw instanceof List<?> signature)||signature.size()!=4||!context.operation().equals(signature.get(1))||!(signature.get(2) instanceof List<?> params)||params.size()!=context.parameters().size())continue;
            boolean match=true;
            for(int i=0;i<params.size();i++)match&=DomainProjection.operationType(params.get(i).toString()).equals(context.parameters().get(i).type());
            String result=signature.get(3).equals("void")?"":DomainProjection.operationType(signature.get(3).toString());
            if(match&&result.equals(context.resultType()))out.add(new Candidate(sourceId,proof,new ArrayList<Object>(signature)));
        }
    }
}
