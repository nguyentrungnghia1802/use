package org.tzi.use.plugins.jacamo.semantics.mapping.auction;

import java.nio.charset.StandardCharsets;
import java.util.*;
import org.jacamo.bridge.contract.*;
import org.jacamo.bridge.contract.semantic.SourceEvidence;
import org.tzi.use.api.*;
import org.tzi.use.plugins.jacamo.codegrounded.use.DomainProjection;
import org.tzi.use.plugins.jacamo.semantics.mapping.*;
import org.tzi.use.semantic.engine.use75.Use75ModelBinding;
import org.tzi.use.uml.mm.*;

/** Explicit case operation interfaces. These are not iStar Task-history contracts. */
public final class AuctionOclBindings {
    public record Operation(String bindingId,String taskId,String sourceId,List<Object> signature,List<String> aliases,MOperation nativeOperation) {
        public Operation {signature=List.copyOf(signature);aliases=List.copyOf(aliases);}
    }
    private record Plan(AuctionMappingSpecification.Node binding,AuctionOclSpecificationLoader.Context context,
            SourceArtifactTypeReferences.Reference reference,List<Object> signature,SourceEvidence method) { }
    public List<Operation> install(ModelSnapshot source,AuctionSelectorResult selectors,AuctionMappingSpecification mapping,
            AuctionOclSpecificationLoader.Specification ocl,Use75ModelBinding target) {
        if(!selectors.specificationHash().equals(mapping.sourceHash())||!selectors.sourceHash().equals(new SourceModelNormalizer().normalize(source).sourceHash()))
            throw invalid("SELECTOR_OWNERSHIP_MISMATCH");
        if(!ocl.sourceHash().equals(mapping.requireId("input-ocl").attr("sha256"))
                ||!ocl.equals(new AuctionOclSpecificationLoader().parse(ocl.source(),ocl.sourceName())))throw invalid("OCL_OWNERSHIP_MISMATCH");
        String fqcn=selectors.require("sel-create").getFirst().values().get("className");
        var references=SourceArtifactTypeReferences.fromActions(source).stream().filter(r->r.javaClass().equals(fqcn)).toList();
        if(references.isEmpty())throw invalid("SOURCE_INTERFACE_REQUIRED");
        var reference=references.getFirst();
        if(references.stream().anyMatch(r->!r.classProof().sourceDigest().equals(reference.classProof().sourceDigest())
                ||!r.classProof().sourceUri().equals(reference.classProof().sourceUri())))throw invalid("SOURCE_PROVIDER_CONFLICT");
        var plans=new ArrayList<Plan>();var names=new HashSet<String>();
        for(var binding:mapping.root().child("verificationBindings").children("operationBinding")) {
            var contexts=ocl.operations().stream().filter(c->c.owner().equals(binding.attr("cslStateOwner"))&&c.operation().equals(binding.attr("sourceOperation"))).toList();
            if(contexts.size()!=1)throw invalid("AUTHORED_CONTEXT_UNRESOLVED:"+binding.attr("id"));
            var context=contexts.getFirst();var owner=target.model().getClass(context.owner());
            if(owner==null||!target.classifiers().containsKey(context.owner())||target.classifiers().get(context.owner()).kind()!=Use75ModelBinding.Kind.ENTITY
                    ||!names.add(context.owner()+"::"+context.operation()))throw invalid("OWNER_OR_DUPLICATE_BINDING");
            String existing=DomainProjection.decode(owner.getAnnotationValue(DomainProjection.ANNOTATION,"javaClass64"));
            if(!existing.isEmpty()&&!existing.equals(fqcn))throw invalid("OWNER_SOURCE_CONFLICT");
            if(owner.operation(context.operation(),false)!=null)throw invalid("NATIVE_OPERATION_ALREADY_DEFINED:"+context.operation());
            var methods=new ArrayList<Plan>();
            for(var proof:reference.operations()) {
                var signature=signature(proof);
                if(!signature.get(0).equals(fqcn)||!signature.get(1).equals(context.operation())||!(signature.get(2) instanceof List<?> parameters)
                        ||parameters.size()!=context.parameters().size())continue;
                boolean matching=signature.get(3).equals("void")&&context.resultType().isEmpty();
                try {for(int i=0;i<parameters.size();i++)matching&=DomainProjection.operationType((String)parameters.get(i)).equals(context.parameters().get(i).type());}
                catch(IllegalArgumentException unsupported){matching=false;}
                if(matching)methods.add(new Plan(binding,context,reference,signature,proof));
            }
            if(methods.size()!=1)throw invalid("EXACT_METHOD_UNRESOLVED_OR_OVERLOADED:"+context.operation());
            var selected=methods.getFirst();String signatureText=new String(CanonicalJson.encode(selected.signature()),StandardCharsets.UTF_8);
            if(owner.allOperations().stream().anyMatch(o->signatureText.equals(DomainProjection.decode(o.getAnnotationValue("CartagoOperation","signature64")))))
                throw invalid("DUPLICATE_NATIVE_SIGNATURE");
            new DomainProjection.OperationExposure(context.operation(),context.parameters().stream().map(AuctionOclSpecificationLoader.Parameter::name).toList());
            plans.add(selected);
        }
        if(plans.size()!=ocl.operations().size())throw invalid("UNBOUND_AUTHORED_OPERATION");
        var api=new UseModelApi(target.model());var result=new ArrayList<Operation>();
        try {
            for(var plan:plans) {
                var owner=target.model().getClass(plan.context().owner());
                DomainProjection.annotate(owner,"artifact",DomainProjection.artifactId(fqcn),fqcn);
                var aliases=plan.context().parameters().stream().map(AuctionOclSpecificationLoader.Parameter::name).toList();
                var operation=DomainProjection.declaredOperation(api,owner.name(),plan.signature(),plan.reference().sourceId(),plan.method(),
                        new DomainProjection.OperationExposure(plan.context().operation(),aliases));
                operation.addAnnotation(new MElementAnnotation("AuctionOperationBinding",Map.of("bindingId",plan.binding().attr("id"),"mappingHash",mapping.sourceHash(),
                        "oclHash",ocl.sourceHash(),"taskId",plan.binding().attr("istarTask"),"taskContractInstalled","false")));
                result.add(new Operation(plan.binding().attr("id"),plan.binding().attr("istarTask"),plan.reference().sourceId(),plan.signature(),aliases,operation));
            }
        }catch(UseApiException nativeFailure){throw new IllegalArgumentException("AUCTION_OPERATION_NATIVE_INSTALLATION_FAILED_DISCARD_CANDIDATE",nativeFailure);}
        return List.copyOf(result);
    }
    @SuppressWarnings("unchecked") private static List<Object> signature(SourceEvidence proof) {
        String json=proof.diagnostics().stream().filter(t->t.startsWith("SIGNATURE:")).findFirst().orElseThrow(()->invalid("METHOD_SIGNATURE_REQUIRED")).substring(10);
        return (List<Object>)CanonicalJson.decode(json.getBytes(StandardCharsets.UTF_8));
    }
    private static IllegalArgumentException invalid(String reason){return new IllegalArgumentException("AUCTION_OPERATION_"+reason);}
}
