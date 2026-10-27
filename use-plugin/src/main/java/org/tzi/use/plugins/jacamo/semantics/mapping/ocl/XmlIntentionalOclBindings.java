package org.tzi.use.plugins.jacamo.semantics.mapping.ocl;
import java.util.*;
import org.tzi.use.api.UseModelApi;
import org.tzi.use.plugins.jacamo.semantics.mapping.*;
import org.tzi.use.plugins.jacamo.semantics.evidence.SemanticJson;
import org.tzi.use.semantic.engine.use75.*;
import org.tzi.use.uml.ocl.type.Type;
import org.vnu.sme.goal.dsl.istar.mm.*;

/** Native predicates from original generated M1 slots; execution/history remains session-owned. */
public final class XmlIntentionalOclBindings {
    public JaCaMoOclContractMapping.Result compile(SemanticProgram program,Use75ModelBinding binding,ObserverSchema observers) {
        return compile(program,binding.model(),observers);
    }
    /** Compile against the installed model; rebuilding a backend or MSystem is unnecessary. */
    public JaCaMoOclContractMapping.Result compile(SemanticProgram program,org.tzi.use.uml.mm.MModel model) {
        return compile(program,model,XmlNativePreparation.observerSchema(program.xmlPlan().inputs().specification()));
    }
    private JaCaMoOclContractMapping.Result compile(SemanticProgram program,org.tzi.use.uml.mm.MModel model,ObserverSchema observers) {
        var result=new ArrayList<JaCaMoOclContractMapping.BoundContract>();var api=new UseModelApi(model);
        String origin=program.xmlPlan().inputs().authoredModels()?"AUTHORED":"GENERATED";
        try(var compiler=new NativeOclBackend(model,program.hash(),program.profile().engine().name())) {
            for(var invariant:program.cslModel().invariants())add(result,compiler,new OclContractSpec("CSL:"+invariant.qualifiedName(),OclContractSpec.Kind.INVARIANT,model.getClass(invariant.contextType()),null,invariant.expression()),"",origin+"_CSL");
            for(var node:program.xmlPlan().inputs().specification().root().child("bindings").children("elementRuntime")) {
                var template=program.goalBindings().elements().get(node.attr("target"));if(template.derived())continue;
                var variables=new TreeMap<String,Type>();
                for(var variable:node.children("variable")) {
                    if(variable.optional("type").isBlank()==variable.optional("viewRef").isBlank())throw variable.location().error("VARIABLE_EXACTLY_ONE_TYPE_SOURCE_REQUIRED");
                    var type=variable.optional("viewRef").isEmpty()?api.getType(variable.attr("type")):observers.valueType(variable.attr("viewRef"));
                    if(variable.attr("origin").isBlank()||variables.putIfAbsent(variable.attr("name"),type)!=null)throw variable.location().error("INTENTIONAL_VARIABLE_BINDING_INVALID");
                }
                var element=program.goalModel().findElement(template.elementId()).orElseThrow();var self=model.getClass(template.selfDeclaration());
                if(element instanceof Goal goal) {
                    if(goal.oclSource()==null||goal.oclSource().isBlank())throw node.location().error("INCOMPLETE:LEAF_GOAL_CONDITION_REQUIRED");
                    add(result,compiler,new OclContractSpec("GOAL:"+goal.id(),OclContractSpec.Kind.GOAL,self,null,goal.oclSource(),variables),goal.id(),origin+"_ISTAR_CONDITION");
                }
                if(element instanceof Task task) {
                    if(task.preconditions().size()!=1||task.postconditions().size()!=1)throw node.location().error("TASK_PREDICATE_SLOTS_REQUIRED");
                    add(result,compiler,new OclContractSpec("TASK:"+task.id()+":pre",OclContractSpec.Kind.TASK_PRE,self,null,task.preconditions().getFirst().oclBody(),variables),task.id(),origin+"_ISTAR_PRE");
                    add(result,compiler,new OclContractSpec("TASK:"+task.id()+":post",OclContractSpec.Kind.TASK_POST,self,null,task.postconditions().getFirst().oclBody(),variables),task.id(),origin+"_ISTAR_POST");
                }
            }
        }catch(org.tzi.use.api.UseApiException invalid){throw new IllegalArgumentException("XML_INTENTIONAL_TYPE_UNRESOLVED",invalid);}
        return new JaCaMoOclContractMapping.Result(result);
    }
    private static void add(List<JaCaMoOclContractMapping.BoundContract> result,NativeOclBackend compiler,OclContractSpec specification,String element,String origin) {
        compiler.compile(specification);result.add(new JaCaMoOclContractMapping.BoundContract(specification.id(),element,specification,SemanticJson.hash(specification.body()),origin,List.of()));
    }
}
