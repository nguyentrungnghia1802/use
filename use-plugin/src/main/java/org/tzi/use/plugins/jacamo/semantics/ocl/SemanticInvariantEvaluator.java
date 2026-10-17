package org.tzi.use.plugins.jacamo.semantics.ocl;

import java.util.*;
import org.jacamo.bridge.contract.Completeness;
import org.tzi.use.uml.mm.MClassInvariant;
import org.tzi.use.uml.sys.MObject;
import org.tzi.use.uml.ocl.value.VarBindings;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.NativeRuntimeMutationEngine;
import org.tzi.use.plugins.jacamo.semantics.evidence.*;

/** Quantification and checker negation apply to the complete invariant, not separately to each body row. */
public final class SemanticInvariantEvaluator {
    private final ConstraintCoverageResolver coverage=new ConstraintCoverageResolver();private final OclEvaluationAdapter backend=new OclEvaluationAdapter();
    public List<SemanticConstraintResult> evaluate(MClassInvariant inv,NativeRuntimeMutationEngine engine,Map<String,Completeness> sources,boolean lost,Set<String> fresh,long version) {
        String id="NATIVE:"+inv.qualifiedName(),expression=inv.bodyExpression().toString();var state=engine.system().state();
        boolean context="true".equals(inv.getAnnotationValue("CslConstraint","scopedContext")),functional="true".equals(inv.getAnnotationValue("CslConstraint","scopedFunctional"));
        String unavailable=context || functional?coverage.unavailablePopulations(inv.bodyExpression(),sources,lost):coverage.unavailable(inv.bodyExpression(),engine,sources,lost);
        if(!inv.isActive())unavailable="CONSTRAINT_DISABLED";if(inv.var()!=null && inv.var().contains(","))unavailable="UNSUPPORTED:MULTI_CONTEXT_VARIABLE_BINDING";
        if(!unavailable.isBlank())return List.of(result(id,"INVARIANT",inv,null,engine,new OclEvaluationAdapter.Result(unavailable.startsWith("UNSUPPORTED:")?SemanticVerdict.UNSUPPORTED:SemanticVerdict.SKIPPED,"",unavailable),version,Map.of()));
        var objects=state.objectsOfClassAndSubClasses(inv.cls()).stream().sorted(Comparator.comparing(MObject::name)).toList();var evaluated=new ArrayList<OclEvaluationAdapter.Result>();var rows=new ArrayList<SemanticConstraintResult>();
        for(var object:objects) {
            String missing=functional?(fresh.contains(engine.metadata(object,"functionalScheme"))?"":"EXACT_FUNCTIONAL_BOARD_CUT_UNAVAILABLE"):context?(fresh.contains(engine.metadata(object,"semanticId"))?"":"EXACT_CONTEXT_CUT_UNAVAILABLE"):coverage.unavailableSelf(inv.bodyExpression(),engine,object,sources,lost,fresh);
            var variables=new VarBindings(state);variables.push(Objects.requireNonNullElse(inv.var(),"self"),object.value());var value=missing.isBlank()?backend.evaluate(inv.bodyExpression(),state,state,variables):OclEvaluationAdapter.Result.skipped(missing);evaluated.add(value);
            var witness=new TreeMap<String,String>();for(var attribute:object.cls().allAttributes())witness.put(attribute.name(),object.state(state).attributeValue(attribute).toString());rows.add(result(id,"INVARIANT_BODY",inv,object,engine,value,version,witness));
        }
        boolean population=coverage.populationComplete(inv.cls(),sources);var aggregate=aggregate(evaluated.stream().map(OclEvaluationAdapter.Result::verdict).toList(),inv.isExistential(),inv.isNegated(),population);
        var witness=new TreeMap<String,String>();for(int i=0;i<objects.size();i++)witness.put(objects.get(i).name(),evaluated.get(i).verdict().name());witness.put("population",population?"COMPLETE":"UNPROVEN");witness.put("quantifier",inv.isExistential()?"EXISTS":"FOR_ALL");witness.put("negated",Boolean.toString(inv.isNegated()));
        rows.add(result(id,"INVARIANT",inv,objects.size()==1?objects.getFirst():null,engine,new OclEvaluationAdapter.Result(aggregate,aggregate==SemanticVerdict.PASS?"true":aggregate==SemanticVerdict.FAIL?"false":"",aggregate==SemanticVerdict.PASS || aggregate==SemanticVerdict.FAIL?"QUANTIFIED_NATIVE_INVARIANT":"INVARIANT_UNDETERMINED_OR_POPULATION_UNPROVEN"),version,witness));return List.copyOf(rows);
    }
    public static SemanticVerdict aggregate(List<SemanticVerdict> bodies,boolean existential,boolean negated,boolean complete) {
        SemanticVerdict value;
        if(bodies.contains(existential?SemanticVerdict.PASS:SemanticVerdict.FAIL))value=existential?SemanticVerdict.PASS:SemanticVerdict.FAIL;
        else if(bodies.contains(SemanticVerdict.ERROR))value=SemanticVerdict.ERROR;
        else if(bodies.contains(SemanticVerdict.UNDEFINED))value=SemanticVerdict.UNDEFINED;
        else if(bodies.contains(SemanticVerdict.UNSUPPORTED))value=SemanticVerdict.UNSUPPORTED;
        else if(bodies.contains(SemanticVerdict.SKIPPED) || !complete)value=SemanticVerdict.SKIPPED;
        else if(bodies.contains(SemanticVerdict.INCONCLUSIVE))value=SemanticVerdict.INCONCLUSIVE;
        else value=existential?SemanticVerdict.FAIL:SemanticVerdict.PASS;
        if(negated && (value==SemanticVerdict.PASS || value==SemanticVerdict.FAIL))value=value==SemanticVerdict.PASS?SemanticVerdict.FAIL:SemanticVerdict.PASS;
        return value;
    }
    private SemanticConstraintResult result(String id,String kind,MClassInvariant inv,MObject object,NativeRuntimeMutationEngine engine,OclEvaluationAdapter.Result value,long version,Map<String,String> witness) {return new SemanticConstraintResult(id,kind,inv.cls().name(),object==null?"":object.name(),object==null?"":engine.metadata(object,"semanticId"),inv.bodyExpression().toString(),value.verdict(),value.backendValue(),value.diagnostic(),version,"",witness);}
}
