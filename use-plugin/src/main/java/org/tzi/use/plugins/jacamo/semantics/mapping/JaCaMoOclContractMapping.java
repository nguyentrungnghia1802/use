package org.tzi.use.plugins.jacamo.semantics.mapping;

import java.util.*;
import org.jacamo.bridge.contract.*;
import org.tzi.use.api.*;
import org.tzi.use.plugins.jacamo.codegrounded.use.*;
import org.tzi.use.plugins.jacamo.semantics.evidence.SemanticJson;
import org.tzi.use.semantic.engine.use75.*;
import org.tzi.use.uml.mm.*;
import org.tzi.use.uml.ocl.type.Type;
import org.vnu.sme.goal.dsl.istar.mm.*;

/** Canonical model slots and authored contracts resolved on the exact native MModel. */
public final class JaCaMoOclContractMapping {
    public record BoundContract(String id,String elementId,OclContractSpec specification,String bodyHash,String origin,List<Object> signature) {
        public BoundContract {signature=List.copyOf(signature);}
    }
    public record Result(List<BoundContract> contracts){public Result{contracts=List.copyOf(contracts);}}
    public Result install(ModelSnapshot input,SemanticProgram program,Use75ModelBinding binding) {
        program.assertUnchanged();program.requireSourceHash(SemanticJson.hash(ContractPayloads.model(input)));
        var api=new UseModelApi(binding.model());var bound=new LinkedHashMap<String,BoundContract>();
        try(var compiler=new NativeOclBackend(binding.model(),program.hash(),program.profile().engine().name())) {
            for(var invariant:program.cslModel().invariants()) {
                var spec=new OclContractSpec("CSL:"+invariant.qualifiedName(),OclContractSpec.Kind.INVARIANT,binding.classifier(invariant.contextType()),null,invariant.expression());
                compiler.compile(spec);add(bound,spec,"","CANONICAL_CSL_INVARIANT",List.of());
            }
            for(var template:program.goalBindings().elements().values()) {
                if(template.derived())continue;var element=program.goalModel().findElement(template.elementId()).orElseThrow();
                if(element instanceof Goal goal) {
                    var overrides=program.contracts().stream().filter(c->c.kind()==OclContractSpec.Kind.GOAL&&c.targetElementId().equals(goal.id())).toList();
                    if(overrides.size()>1)reject("GOAL_CONTRACT_BINDING_AMBIGUOUS",goal.id(),"Only one canonical condition binding is admitted");
                    var variables=new LinkedHashMap<String,Type>();variables.put("player",binding.model().getClass("RuntimePlayer"));variables.put("orgContext",binding.model().getClass("OrgContext"));
                    if(!overrides.isEmpty())for(var declared:types(api,overrides.getFirst().variableTypes()).entrySet())
                        if(variables.get(declared.getKey())!=declared.getValue())reject("GOAL_IDENTITY_BINDINGS_MUST_MATCH_PROFILE",goal.id(),declared.getKey());
                    var spec=new OclContractSpec(overrides.isEmpty()?"GOAL:"+goal.id():overrides.getFirst().id(),OclContractSpec.Kind.GOAL,binding.classifier(template.selfDeclaration()),null,goal.oclSource(),variables);
                    var compiled=compiler.compile(spec);
                    if(!overrides.isEmpty()) {
                        var authored=overrides.getFirst();requireOrigin(authored);
                        if(!authored.contextDeclaration().equals(template.selfDeclaration())||!authored.operationSignature().isEmpty())reject("GOAL_CONTRACT_CONTEXT_MISMATCH",goal.id(),authored.contextDeclaration());
                        equivalent(compiled.expression().toString(),NativeOclCompilation.compile(binding.model(),new OclContractSpec(authored.id()+":source",spec.kind(),spec.self(),null,authored.body(),variables)).toString(),goal.id());
                    }
                    add(bound,spec,goal.id(),"CANONICAL_ISTAR_GOAL_CONDITION",List.of());
                }
            }
            for(var contract:program.contracts()) {
                requireOrigin(contract);
                if(contract.kind()==OclContractSpec.Kind.GOAL) {
                    if(!bound.containsKey(contract.id()))reject("GOAL_CONTRACT_TARGET_UNRESOLVED",contract.id(),contract.targetElementId());
                    continue;
                }
                if(contract.kind()==OclContractSpec.Kind.INVARIANT) {
                    if(!contract.operationSignature().isEmpty())reject("INVARIANT_OPERATION_SIGNATURE_INVALID",contract.id(),"State invariant cannot acquire invocation semantics");
                    var spec=new OclContractSpec(contract.id(),contract.kind(),binding.classifier(contract.contextDeclaration()),null,contract.body(),types(api,contract.variableTypes()));
                    if(!spec.bindings().isEmpty())reject("GLOBAL_INVARIANT_FREE_BINDINGS_UNSUPPORTED",contract.id(),"Native quantification has no implicit external variables");
                    compiler.compile(spec);var invariant=api.createInvariant("Authored_"+DomainProjection.hash(contract.id()),spec.self().name(),contract.body(),false);
                    invariant.addAnnotation(new MElementAnnotation("CslConstraint",Map.of("id64",DomainProjection.encode(contract.id()),"origin64",DomainProjection.encode(contract.origin()))));
                    add(bound,spec,"",contract.origin(),List.of());continue;
                }
                if(contract.kind()!=OclContractSpec.Kind.TASK_PRE&&contract.kind()!=OclContractSpec.Kind.TASK_POST)reject("MAPPED_CONTRACT_KIND_UNSUPPORTED",contract.id(),contract.kind().name());
                var template=program.goalBindings().elements().get(contract.targetElementId());
                if(template==null||template.derived()||!(program.goalModel().findElement(template.elementId()).orElse(null) instanceof Task task))reject("TASK_CONTRACT_TARGET_UNRESOLVED",contract.id(),contract.targetElementId());
                var task=(Task)program.goalModel().findElement(contract.targetElementId()).orElseThrow();
                if(!template.selfDeclaration().equals(contract.contextDeclaration()))reject("TASK_CONTRACT_SELF_CONTEXT_MISMATCH",contract.id(),contract.contextDeclaration());
                boolean pre=contract.kind()==OclContractSpec.Kind.TASK_PRE;
                if(!contract.id().equals(template.contractId()+(pre?":pre":":post")))reject("TASK_CONTRACT_ID_MISMATCH",contract.id(),template.contractId());
                var operation=operation(input,api,contract);
                var variables=types(api,contract.variableTypes());
                // Checkpoint evaluation binds actual native parameters/result. Extra task
                // variables would require producer/runtime binding evidence not in V1.
                if(!variables.isEmpty())reject("INVOCATION_EXTRA_BINDINGS_UNSUPPORTED",contract.id(),variables.keySet().toString());
                var slots=pre?task.preconditions():task.postconditions();
                if(slots.size()!=1)reject("MULTIPLE_TASK_CONTRACT_SLOTS_UNSUPPORTED",contract.id(),Integer.toString(slots.size()));
                var spec=new OclContractSpec(contract.id(),contract.kind(),binding.classifier(contract.contextDeclaration()),operation,slots.getFirst().oclBody());
                var compiled=compiler.compile(spec);
                equivalent(compiled.expression().toString(),NativeOclCompilation.operationPredicate(binding.model(),operation,contract.id()+":source",contract.body(),pre).toString(),contract.id());
                var condition=api.createPrePostConditionEx((pre?"Pre_":"Post_")+DomainProjection.hash(contract.id()),operation,pre,compiled.expression());
                condition.addAnnotation(new MElementAnnotation("CslConstraint",Map.of("id64",DomainProjection.encode(contract.id()),"targetTask64",DomainProjection.encode(task.id()),"contractBase64",DomainProjection.encode(template.contractId()),
                        "canonical","true","bodyHash",SemanticJson.hash(slots.getFirst().oclBody()),"origin64",DomainProjection.encode(contract.origin()))));
                add(bound,spec,task.id(),contract.origin(),contract.operationSignature());
            }
            for(var template:program.goalBindings().elements().values())if(!template.derived()&&program.goalModel().findElement(template.elementId()).orElse(null) instanceof Task) {
                var pre=bound.get(template.contractId()+":pre");var post=bound.get(template.contractId()+":post");
                if(pre==null||post==null||pre.specification().operation()!=post.specification().operation())reject("TASK_PRE_POST_OPERATION_IDENTITY_MISMATCH",template.elementId(),template.contractId());
            }
            return new Result(List.copyOf(bound.values()));
        }catch(UseApiException|IllegalArgumentException failure) {
            if(failure instanceof MappingAdmissionException mapped)throw mapped;
            var diagnostic=new MappingDiagnostic(MappingDiagnostic.Kind.ERROR,"NATIVE_OCL_CONTRACT_COMPILATION_FAILED","",program.hash(),"NATIVE_OCL_7.5","AUTHORED_AND_CANONICAL_CONTRACTS",0,failure.getMessage()==null?failure.getClass().getName():failure.getMessage());
            var mapped=new MappingAdmissionException(List.of(diagnostic));mapped.initCause(failure);throw mapped;
        }
    }
    private MOperation operation(ModelSnapshot input,UseModelApi api,MappingRuleSet.Contract contract) throws UseApiException {
        var signature=contract.operationSignature();
        if(signature.size()!=4||!(signature.get(0) instanceof String)||!(signature.get(1) instanceof String)||!(signature.get(2) instanceof List<?>)||!(signature.get(3) instanceof String))reject("EXACT_OPERATION_SIGNATURE_REQUIRED",contract.id(),signature.toString());
        String canonical=NativeUseModelBuilder.canonicalJson(signature);var cls=api.getModel().getClass(contract.contextDeclaration());
        var existing=cls.allOperations().stream().filter(op->canonical.equals(DomainProjection.decode(op.getAnnotationValue("CartagoOperation","signature64")))).toList();
        if(existing.size()>1)reject("EXACT_OPERATION_SIGNATURE_AMBIGUOUS",contract.id(),canonical);
        if(existing.size()==1)return existing.getFirst();
        String fqcn=DomainProjection.decode(cls.getAnnotationValue(DomainProjection.ANNOTATION,"javaClass64"));MOperation result=null;
        for(var environment:input.semanticContract().cartagoEnvironments())for(var descriptor:environment.operations()) {
            var artifact=environment.artifacts().stream().filter(a->a.metadata().semanticId().equals(descriptor.artifactSemanticId())).findFirst().orElseThrow();
            var type=environment.artifactTypes().stream().filter(t->t.metadata().semanticId().equals(artifact.artifactTypeSemanticId())).findFirst().orElseThrow();
            if(!fqcn.equals(type.javaClassName()))continue;
            for(var backing:environment.backingOperations())if(backing.operationDescriptorId().equals(descriptor.metadata().semanticId())&&canonical.equals(NativeUseModelBuilder.canonicalJson(List.of(backing.declaringClass(),backing.methodName(),backing.parameterTypes(),backing.returnType()))))result=DomainProjection.operation(api,cls.name(),descriptor,backing);
        }
        if(result!=null)return result;
        var proofs=input.semanticContract().artifactDeclarations().stream().filter(d->d.javaClass().equals(fqcn))
                .flatMap(d->d.metadata().evidence().stream().filter(e->e.sourceSemanticId().equals(d.metadata().semanticId())))
                .filter(e->e.diagnostics().contains("DECLARED_CARTAGO_ARTIFACT_OPERATION")&&e.diagnostics().contains("SIGNATURE:"+canonical))
                // Two declarations of one byte-identical Java type are aliases,
                // not conflicting interface authorities. Conflicting bytes remain distinct.
                .collect(java.util.stream.Collectors.toMap(e->NativeUseModelBuilder.canonicalJson(List.of(e.sourceJavaFqcn(),e.sourceUri(),e.sourceDigest(),e.authority().name(),e.fidelity().name(),e.capabilityStatus().name(),e.diagnostics())),e->e,(a,b)->a,TreeMap::new)).values().stream().toList();
        if(proofs.size()>1)reject("DECLARED_OPERATION_PRODUCER_PROOF_AMBIGUOUS",contract.id(),canonical);
        return proofs.isEmpty()?DomainProjection.declaredOperation(api,cls.name(),signature,contract.id()):DomainProjection.declaredOperation(api,cls.name(),signature,contract.id(),proofs.getFirst());
    }
    private Map<String,Type> types(UseModelApi api,Map<String,String> declarations) throws UseApiException {
        var result=new TreeMap<String,Type>();for(var variable:declarations.entrySet())result.put(variable.getKey(),api.getType(variable.getValue()));return result;
    }
    private void add(Map<String,BoundContract> result,OclContractSpec spec,String element,String origin,List<Object> signature) {
        if(result.putIfAbsent(spec.id(),new BoundContract(spec.id(),element,spec,SemanticJson.hash(spec.body()),origin,signature))!=null)reject("COMPILED_CONTRACT_ID_AMBIGUOUS",spec.id(),element);
    }
    private static void requireOrigin(MappingRuleSet.Contract contract){if(!contract.origin().equals("AUTHORED_OCL")&&!contract.origin().equals("EXPLICIT_SOURCE_OCL"))reject("OCL_SOURCE_LANGUAGE_ABSTRACTION_REQUIRED",contract.id(),contract.origin());}
    private static void equivalent(String original,String authored,String id){if(!original.equals(authored))reject("CONTRACT_CANONICAL_SLOT_MISMATCH",id,"Canonical original model condition differs from separately authored contract");}
    private static void reject(String code,String target,String reason){throw new MappingAdmissionException(List.of(new MappingDiagnostic(MappingDiagnostic.Kind.UNSUPPORTED,code,"",target,"OCL_CONTRACT_MAPPING","AUTHORED_OR_OFFICIAL_INTERFACE",0,reason)));}
}
