package org.tzi.use.plugins.jacamo.semantics.mapping.ocl;
import java.util.*;
import org.jacamo.bridge.contract.ModelSnapshot;
import org.tzi.use.api.UseModelApi;
import org.tzi.use.plugins.jacamo.codegrounded.trace.CodeGroundedTraceCollector;
import org.tzi.use.plugins.jacamo.semantics.mapping.*;
import org.tzi.use.semantic.engine.use75.Use75ModelBinding;

/** Builds an unpublished native candidate; no MSystem or runtime/history is created here. */
public final class XmlNativePreparation {
    public record Result(Use75ModelBinding binding,List<XmlOperationBindings.Operation> operations,
            AuthoredOclSpecificationLoader.Prepared authored,JaCaMoOclContractMapping.Result intentional,ObserverSchema observers,Map<String,Object> preflight) {
        public Result {operations=List.copyOf(operations);preflight=Map.copyOf(preflight);}
    }
    public Result prepare(ModelSnapshot source,SemanticProgram program,CodeGroundedTraceCollector trace) {
        program.assertUnchanged();program.requireSourceHash(org.tzi.use.plugins.jacamo.semantics.evidence.SemanticJson.hash(org.jacamo.bridge.contract.ContractPayloads.model(source)));
        var inputs=Objects.requireNonNull(program.xmlPlan(),"XML_PLAN_REQUIRED").inputs();
        var observers=observerSchema(inputs.specification());
        var binding=new CslScopeModelAdapter().compile(program.cslModel(),observers);
        new CanonicalUseBindingAdapter().install(program,binding,trace);
        org.tzi.use.plugins.jacamo.semantics.mapping.xml.runtime.InvariantActivation.declare(inputs.specification(),binding.model(),program.hash());
        for(var presentation:inputs.specification().root().children("presentation"))for(var selection:presentation.children("state")) {
            var cls=binding.model().getClass(selection.attr("owner"));
            if(cls==null)throw selection.location().error("PRESENTATION_CLASS_OR_FIELDS_REQUIRED");
            for(String field:selection.optional("attributes").isBlank()?List.<String>of():Arrays.asList(selection.attr("attributes").trim().split("\\s+")))if(cls.attribute(field,true)==null)throw selection.location().error("PRESENTATION_ATTRIBUTE_UNRESOLVED:"+field);
        }
        var loader=new AuthoredOclSpecificationLoader();var authored=ModelNativeContracts.read(inputs);
        var operations=new XmlOperationBindings().install(source,program,binding,authored);
        var clauses=new LinkedHashMap<String,AuthoredOclSpecificationLoader.Context>();
        for(var node:inputs.specification().root().child("bindings").children("oclClause"))if(clauses.putIfAbsent(node.attr("name"),AuthoredOclSpecificationLoader.parseContext(node.attr("context")))!=null)throw node.location().error("OCL_CLAUSE_BINDING_DUPLICATE");
        var prepared=loader.compile(authored,binding,authored.sourceHash(),clauses);
        var intentional=new XmlIntentionalOclBindings().compile(program,binding,observers);
        // Validate the complete authored runtime plan before any live MSystem is
        // created/published. This compiles instructions only; no writer/history.
        if(inputs.authoredModels())new org.tzi.use.plugins.jacamo.semantics.mapping.xml.runtime.XmlRuntimeStateMapping(program,binding.model());
        prepared.install(new UseModelApi(binding.model()));
        var preflight=inputs.authoredModels()?XmlBindingPreflight.inspect(program,binding.model(),prepared,intentional):Map.<String,Object>of();
        return new Result(binding,operations,prepared,intentional,observers,preflight);
    }
    public static ObserverSchema observerSchema(org.tzi.use.plugins.jacamo.semantics.mapping.xml.MappingSpecification specification) {
        var classIds=new LinkedHashSet<String>();var valueIds=new LinkedHashSet<String>();
        for(var view:specification.views())switch(view.attr("category")) {
            case "CLASS"->classIds.add(view.attr("id"));case "TUPLE"->valueIds.add(view.attr("id"));
            default->throw view.location().error("OBSERVER_CATEGORY_UNSUPPORTED");
        }
        return new ObserverSchema(specification.root(),specification.views(),classIds,valueIds,specification.sourceHash(),"DeclarativeObserverView","2.0");
    }
}
