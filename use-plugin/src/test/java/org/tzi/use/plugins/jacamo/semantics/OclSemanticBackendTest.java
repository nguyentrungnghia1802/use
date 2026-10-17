package org.tzi.use.plugins.jacamo.semantics;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.tzi.use.api.*;
import org.tzi.use.uml.mm.*;
import org.tzi.use.uml.sys.*;
import org.tzi.use.uml.ocl.value.*;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.*;
import org.tzi.use.plugins.jacamo.codegrounded.trace.NativeObjectBindings;
import org.tzi.use.plugins.jacamo.semantics.ocl.*;
import org.tzi.use.plugins.jacamo.semantics.evidence.*;
import org.jacamo.bridge.contract.Completeness;

class OclSemanticBackendTest {
    record Fixture(UseModelApi model,UseSystemApi api,MSystem system,NativeRuntimeMutationEngine engine,MObject one,MObject two){}
    static Fixture fixture()throws Exception {
        var model=new UseModelApi("OclSemantics");model.createClass("DomainEntity",true);var x=model.createClass("X",false);model.createGeneralization("X","DomainEntity");var p=model.createAttribute("X","p","Integer");p.addAnnotation(new MElementAnnotation("CslAttribute",Map.of("optional","false")));
        model.createAssociation("Peers","X","owners","*",0,"X","peers","*",0);var system=new MSystem(model.getModel());var api=UseSystemApi.create(system,false);var a=api.createObject("X","a");var b=api.createObject("X","b");api.setAttributeValue("a","p","1");api.setAttributeValue("b","p","-1");var index=new NativeObjectBindings();index.put("a",a);index.put("b",b);index.metadata(a,"propertySlot:p","observed-a");index.metadata(b,"propertySlot:p","observed-b");return new Fixture(model,api,system,new NativeRuntimeMutationEngine(system,index,new CodeGroundedRuntimeRuleRegistry()),a,b);
    }
    static SemanticVerdict invariant(Fixture f,MClassInvariant inv,boolean complete)throws Exception {return new SemanticInvariantEvaluator().evaluate(inv,f.engine(),Map.of("cartago",complete?Completeness.COMPLETE:Completeness.PARTIAL),false,Set.of(),1).stream().filter(r->r.kind().equals("INVARIANT")).findFirst().orElseThrow().verdict();}
    @Test void quantifiedNegationUndefinedAndIncompletePopulationUseNativeInvariantMeaning()throws Exception {
        var f=fixture();var all=f.model().createInvariant("All","X","self.p > 0",false);var exists=f.model().createInvariant("Exists","X","self.p > 0",true);
        assertEquals(SemanticVerdict.FAIL,invariant(f,all,true));assertEquals(SemanticVerdict.PASS,invariant(f,exists,true));all.setNegated(true);exists.setNegated(true);assertEquals(SemanticVerdict.PASS,invariant(f,all,true));assertEquals(SemanticVerdict.FAIL,invariant(f,exists,true));
        all.setNegated(false);exists.setNegated(false);f.api().setAttributeValueEx(f.two(),f.two().cls().attribute("p",true),UndefinedValue.instance);assertEquals(SemanticVerdict.UNDEFINED,invariant(f,all,true));assertEquals(SemanticVerdict.PASS,invariant(f,exists,true));assertEquals(SemanticVerdict.SKIPPED,invariant(f,all,false));
        assertEquals(SemanticVerdict.PASS,SemanticInvariantEvaluator.aggregate(List.of(),false,false,true));assertEquals(SemanticVerdict.FAIL,SemanticInvariantEvaluator.aggregate(List.of(),true,false,true));assertEquals(SemanticVerdict.SKIPPED,SemanticInvariantEvaluator.aggregate(List.of(),false,false,false));assertEquals(SemanticVerdict.ERROR,SemanticInvariantEvaluator.aggregate(List.of(SemanticVerdict.ERROR),true,false,true));
    }
    @Test void collectionsNavigationAtPreAndTypedResultUsePinnedNativeStates()throws Exception {
        var f=fixture();f.api().createLinkEx(f.model().getModel().getAssociation("Peers"),new MObject[]{f.one(),f.two()});var op=f.model().createOperation("X","advance",new String[][]{{"amount","Integer"}},"Integer");var condition=SemanticOclCompiler.condition(f.model(),op,"Post","self.p = self.p@pre + amount and result = self.p and self.peers@pre->size() = 1 and self.peers->isEmpty()",false);
        var before=new MSystemState("pinned",f.system().state());f.api().deleteLinkEx(f.model().getModel().getAssociation("Peers"),new MObject[]{f.one(),f.two()});f.api().setAttributeValue("a","p","2");var vars=new VarBindings();vars.push("self",f.one().value());vars.push("amount",IntegerValue.valueOf(1));vars.push("result",IntegerValue.valueOf(2));assertEquals(SemanticVerdict.PASS,new OclEvaluationAdapter().evaluate(condition.expression(),before,f.system().state(),vars).verdict());assertEquals("1",f.one().state(before).attributeValue("p").toString());
        for(String body:List.of("Set{1,2,3}->forAll(n | n > 0)","Sequence{1,2,3}->select(n | n > 1)->size() = 2","X.allInstances()->exists(x | x.p > 0)","Tuple{n:Integer=2}.n = 2"))assertEquals(SemanticVerdict.PASS,new OclEvaluationAdapter().evaluate(SemanticOclCompiler.predicate(f.system().model(),f.one().cls(),"host",body),before,f.system().state(),vars).verdict(),body);
        assertEquals(SemanticVerdict.UNDEFINED,new OclEvaluationAdapter().evaluate(SemanticOclCompiler.predicate(f.system().model(),f.one().cls(),"division","1 div 0 > 1"),before,f.system().state(),vars).verdict());
    }
    @Test void compilationCacheTracksModelProfileAndNestedPropertyDependency()throws Exception {
        var f=fixture();var cache=new SemanticConstraintRegistry(f.system().model(),"revision-1","profile-1");var first=cache.predicate("positive","GOAL",f.one().cls(),"self.p > 0");assertSame(first,cache.predicate("positive","GOAL",f.one().cls(),"self.p > 0"));assertEquals(Set.of("X.p"),first.attributes());assertTrue(first.populationTypes().isEmpty());var global=cache.predicate("population","GOAL",f.one().cls(),"X.allInstances()->forAll(x | x.p > 0)");assertEquals(Set.of("X"),global.populationTypes());assertEquals(2,cache.inventory().size());assertThrows(IllegalArgumentException.class,()->cache.predicate("syntax","GOAL",f.one().cls(),"self.missing > 0"));assertEquals(2,cache.inventory().size());
        var foreign=fixture();assertThrows(IllegalArgumentException.class,()->cache.predicate("foreign","GOAL",foreign.one().cls(),"true"));
        var index=new NativeObjectBindings();index.put("a",f.one());index.put("b",f.two());index.metadata(f.one(),"propertySlot:p","a");var engine=new NativeRuntimeMutationEngine(f.system(),index,new CodeGroundedRuntimeRuleRegistry());
        var query=SemanticOclCompiler.predicate(f.system().model(),f.one().cls(),"nested","X.allInstances()->exists(x | x.p.oclIsUndefined())");assertTrue(new ConstraintCoverageResolver().unavailableSelf(query,engine,f.one(),Map.of("cartago",Completeness.COMPLETE),false).startsWith("REQUIRED_PROPERTY_OBSERVATION_UNAVAILABLE:"));assertEquals("",new ConstraintCoverageResolver().unavailableSelf(first.expression(),engine,f.one(),Map.of("cartago",Completeness.COMPLETE),false));
    }
}
