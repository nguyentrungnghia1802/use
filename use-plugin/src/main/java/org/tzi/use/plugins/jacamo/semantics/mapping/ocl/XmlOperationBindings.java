package org.tzi.use.plugins.jacamo.semantics.mapping.ocl;

import java.nio.charset.StandardCharsets;
import java.util.*;
import org.jacamo.bridge.contract.*;
import org.jacamo.bridge.contract.semantic.SourceEvidence;
import org.tzi.use.api.*;
import org.tzi.use.plugins.jacamo.codegrounded.use.DomainProjection;
import org.tzi.use.plugins.jacamo.semantics.mapping.*;
import org.tzi.use.semantic.engine.use75.Use75ModelBinding;
import org.tzi.use.uml.mm.*;

/** Exact producer signatures and authored aliases on the construction candidate. Never reflects in USE. */
public final class XmlOperationBindings {
    public record Operation(String bindingId,String taskId,String sourceId,List<Object> signature,List<String> aliases,MOperation nativeOperation) {
        public Operation {signature=List.copyOf(signature);aliases=List.copyOf(aliases);}
    }
    private record Proof(String source,SourceEvidence evidence) { }
    private record Plan(String id,String task,String owner,List<Object> signature,AuthoredOclSpecificationLoader.Context context,Proof proof) { }
    public List<Operation> install(ModelSnapshot source,SemanticProgram program,Use75ModelBinding target,AuthoredOclSpecificationLoader.Specification ocl) {
        var xml=program.xmlPlan().inputs().specification();var plans=new ArrayList<Plan>();var covered=new HashSet<AuthoredOclSpecificationLoader.Context>();var names=new HashSet<String>();
        for(var node:xml.root().child("bindings").children("operation")) {
            var signature=signature(node.attr("signature"));
            var binding=program.bindings().find(SemanticBindingIndex.Kind.ENTITY,node.attr("owner")).orElseThrow(()->node.location().error("OPERATION_ENTITY_BINDING_REQUIRED"));
            String fqcn=JaCaMoCslMappingRules.javaClass(program.source(),binding);
            var clauses=List.of(node.attr("clauses").trim().split("\\s+"));
            var selected=ocl.clauses().stream().filter(c->clauses.contains(c.name())).toList();
            if(selected.size()!=clauses.size()||new HashSet<>(clauses).size()!=clauses.size())throw node.location().error("OPERATION_CLAUSES_UNRESOLVED");
            var contexts=selected.stream().map(AuthoredOclSpecificationLoader.Clause::context).distinct().toList();
            if(contexts.size()!=1)throw node.location().error("OPERATION_CONTEXT_NOT_UNIQUE");
            var context=contexts.getFirst();
            if(!context.owner().equals(node.attr("owner"))||context.operation().isBlank()||!names.add(context.owner()+"::"+context.operation()))throw node.location().error("OPERATION_CONTEXT_OWNER_OR_DUPLICATE");
            var parameters=(List<?>)signature.get(2);
            if(parameters.size()!=context.parameters().size()||!((String)signature.get(3)).equals("void")&&!DomainProjection.operationType((String)signature.get(3)).equals(context.resultType())
                    ||signature.get(3).equals("void")&&!context.resultType().isEmpty())throw node.location().error("OPERATION_SIGNATURE_TYPE_MISMATCH");
            for(int i=0;i<parameters.size();i++)if(!DomainProjection.operationType((String)parameters.get(i)).equals(context.parameters().get(i).type()))throw node.location().error("OPERATION_PARAMETER_TYPE_MISMATCH");
            String canonical=new String(CanonicalJson.encode(signature),StandardCharsets.UTF_8);var proofs=new ArrayList<Proof>();
            for(var declared:source.semanticContract().artifactDeclarations())if(declared.javaClass().equals(fqcn))for(var proof:declared.metadata().evidence())
                if(proof.sourceSemanticId().equals(declared.metadata().semanticId())&&proof.diagnostics().contains("DECLARED_CARTAGO_ARTIFACT_OPERATION")&&proof.diagnostics().contains("SIGNATURE:"+canonical))proofs.add(new Proof(declared.metadata().semanticId(),proof));
            for(var reference:SourceArtifactTypeReferences.fromActions(source))if(reference.javaClass().equals(fqcn))for(var proof:reference.operations())
                if(proof.diagnostics().contains("SIGNATURE:"+canonical))proofs.add(new Proof(reference.sourceId(),proof));
            if(proofs.isEmpty())throw node.location().error("UNSUPPORTED:EXACT_PRODUCER_OPERATION_PROOF_REQUIRED");
            if(proofs.stream().map(p->List.of(p.evidence.sourceUri(),p.evidence.sourceDigest(),p.evidence.sourceJavaFqcn())).distinct().count()!=1)throw node.location().error("OPERATION_PROVIDER_CONFLICT");
            if(!node.optional("task").isEmpty()&&!(program.goalModel().findElement(node.attr("task")).orElse(null) instanceof org.vnu.sme.goal.dsl.istar.mm.Task))throw node.location().error("OPERATION_TASK_UNRESOLVED");
            if(target.classifier(context.owner()).operation(context.operation(),false)!=null)throw node.location().error("OPERATION_NATIVE_NAME_COLLISION");
            covered.add(context);plans.add(new Plan(node.attr("id"),node.optional("task"),node.attr("owner"),signature,context,proofs.getFirst()));
        }
        if(!covered.equals(new HashSet<>(ocl.operations())))throw new IllegalArgumentException("XML_UNBOUND_AUTHORED_OPERATION");
        var result=new ArrayList<Operation>();var api=new UseModelApi(target.model());
        try {
            for(var plan:plans) {
                var aliases=plan.context.parameters().stream().map(AuthoredOclSpecificationLoader.Parameter::name).toList();
                var operation=DomainProjection.declaredOperation(api,plan.owner,plan.signature,plan.proof.source,plan.proof.evidence,new DomainProjection.OperationExposure(plan.context.operation(),aliases));
                operation.addAnnotation(new MElementAnnotation("DeclarativeOperationBinding",Map.of("id",plan.id,"mappingHash",xml.sourceHash(),"oclHash",ocl.sourceHash(),"task",plan.task)));
                result.add(new Operation(plan.id,plan.task,plan.proof.source,plan.signature,aliases,operation));
            }
        }catch(UseApiException failure){throw new IllegalArgumentException("XML_OPERATION_CANDIDATE_REJECTED",failure);}
        return List.copyOf(result);
    }
    private static List<Object> signature(String json) {
        var decoded=CanonicalJson.decode(json.getBytes(StandardCharsets.UTF_8));
        if(!(decoded instanceof List<?> list)||list.size()!=4||!(list.get(0) instanceof String)||!(list.get(1) instanceof String)
                ||!(list.get(2) instanceof List<?> parameters)||parameters.size()>16||parameters.stream().anyMatch(p->!(p instanceof String))||!(list.get(3) instanceof String))throw new IllegalArgumentException("XML_OPERATION_SIGNATURE_INVALID");
        return List.copyOf(list);
    }
}
