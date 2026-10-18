package org.tzi.use.semantic.engine;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.tzi.use.api.*;
import org.tzi.use.semantic.engine.use75.*;
import org.tzi.use.uml.mm.*;
import org.tzi.use.uml.ocl.expr.*;
import org.tzi.use.uml.ocl.type.TypeFactory;
import org.tzi.use.uml.ocl.value.*;
import org.tzi.use.uml.sys.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.tzi.use.semantic.engine.use75.NativeOclOutcome.Status.*;
import static org.tzi.use.semantic.engine.use75.OclContractSpec.Kind.*;

class NativeOclBackendTest {
    record Fixture(UseModelApi schema,MClass type,MOperation operation,MSystem system,UseSystemApi api,MObject object,MObject peer){}
    private Fixture fixture() throws Exception {
        var schema=new UseModelApi("NativeEngine");schema.createClass("Base",true);schema.createAttribute("Base","p","Integer");
        var type=schema.createClass("Counter",false);schema.createGeneralization("Counter","Base");
        schema.createEnumeration("Color",new String[]{"RED","BLUE"});schema.createAttribute("Counter","color","Color");
        schema.createAssociation("Peers","Counter","owners","*",0,"Counter","peers","*",0);
        var op=schema.createOperation("Counter","increment",new String[][]{{"amount","Integer"}},"Integer");
        var system=new MSystem(schema.getModel());var api=UseSystemApi.create(system,false);var object=api.createObject("Counter","counter");var peer=api.createObject("Counter","peer");
        api.setAttributeValue("counter","p","0");api.setAttributeValue("counter","color","Color::RED");api.setAttributeValue("peer","p","0");
        return new Fixture(schema,type,op,system,api,object,peer);
    }
    private NativeOclBackend backend(Fixture f){return new NativeOclBackend(f.system().model(),"program-1","USE4GOAL_OBSERVED_V1");}
    private OclContractSpec spec(Fixture f,OclContractSpec.Kind kind,String body){return new OclContractSpec("contract-"+kind,kind,f.type(),kind==TASK_PRE||kind==TASK_POST?f.operation():null,body);}
    private Map<String,Value> values(Fixture f){return Map.of("self",f.object().value(),"amount",IntegerValue.valueOf(1),"result",IntegerValue.valueOf(1));}
    @Test void actualNativeAtPreUsesDistinctPinnedStatesAndDetectsIncorrectIncrement() throws Exception {
        var f=fixture();try(var backend=backend(f)) {
            var contract=backend.compile(spec(f,TASK_POST,"self.p = self.p@pre + amount and result = self.p"));
            var before=new MSystemState("pre",f.system().state());f.api().setAttributeValue("counter","p","1");var pair=UseStateReadView.pin(before,f.system().state());
            assertEquals(PASS,backend.evaluate(contract,pair,values(f),"").status());
            assertEquals(BooleanValue.TRUE,new Evaluator().eval(contract.expression(),pair.before(),pair.after(),bindings(values(f))));
            assertSame(f.system(),pair.before().system());assertSame(f.system(),pair.after().system());
            f.api().setAttributeValue("counter","p","2");assertEquals(FAIL,backend.evaluate(contract,UseStateReadView.pin(before,f.system().state()),values(f),"").status());
            assertEquals(PASS,backend.evaluate(contract,pair,values(f),"").status(),"later live writes cannot alter accepted pinned cuts");
            assertEquals(FAIL,backend.evaluate(contract,UseStateReadView.pin(pair.after(),pair.after()),values(f),"").status());
            assertEquals(SKIPPED,backend.evaluate(contract,UseStateReadView.pin(null,pair.after()),values(f),"").status());
            assertTrue(backend.metrics().evaluationNanos()>0);assertEquals(4,backend.metrics().evaluations());
        }
    }
    @Test void navigationCollectionsInheritanceEnumsAndTypedContextBindingsUseHostLanguage() throws Exception {
        var f=fixture();f.api().createLinkEx(f.schema().getModel().getAssociation("Peers"),new MObject[]{f.object(),f.peer()});
        var before=new MSystemState("pre",f.system().state());f.api().deleteLinkEx(f.schema().getModel().getAssociation("Peers"),new MObject[]{f.object(),f.peer()});
        try(var backend=backend(f)) {
            for(String body:List.of("self.p >= 0 and self.color = Color::RED","Counter.allInstances()->forAll(x | x.p >= 0)","Sequence{1,2,3}->select(n | n > 1)->size() = 2","Tuple{n:Integer=2}.n = 2"))
                assertEquals(PASS,backend.evaluate(backend.compile(spec(f,GOAL,body)),UseStateReadView.pin(null,f.system().state()),values(f),"").status());
            var transition=backend.compile(spec(f,TRANSITION,"self.peers@pre->size() = 1 and self.peers->isEmpty()"));
            assertEquals(PASS,backend.evaluate(transition,UseStateReadView.pin(before,f.system().state()),values(f),"").status());
            var contextual=new OclContractSpec("context",GOAL,f.type(),null,"self.p = player.p and orgContext.p >= 0",Map.of("player",f.type(),"orgContext",f.type()));
            var compiled=backend.compile(contextual);var values=new HashMap<String,Value>(values(f));values.put("player",f.peer().value());values.put("orgContext",f.object().value());
            assertEquals(PASS,backend.evaluate(compiled,UseStateReadView.pin(null,f.system().state()),values,"").status());
            values.remove("orgContext");assertEquals(SKIPPED,backend.evaluate(compiled,UseStateReadView.pin(null,f.system().state()),values,"").status());
        }
    }
    @Test void booleanTypeGateAndPreContextRejectInvalidProgramsWithoutCachingFailures() throws Exception {
        var f=fixture();try(var backend=backend(f)) {
            for(var kind:OclContractSpec.Kind.values())assertThrows(IllegalArgumentException.class,()->backend.compile(spec(f,kind,"self.p + 1")));
            for(var kind:List.of(GOAL,TASK_PRE,INVARIANT))assertThrows(IllegalArgumentException.class,()->backend.compile(spec(f,kind,"self.p = self.p@pre")));
            assertThrows(IllegalArgumentException.class,()->backend.compile(spec(f,TASK_PRE,"result > 0")));
            assertThrows(IllegalArgumentException.class,()->backend.compile(spec(f,GOAL,"self.missing > 0")));
            assertEquals(0,backend.metrics().expressions());
        }
    }
    @Test void falseUndefinedErrorsAndCoverageRemainDistinctAndNeverCoerceToFalse() throws Exception {
        var f=fixture();try(var backend=backend(f)) {
            var view=UseStateReadView.pin(null,f.system().state());
            assertEquals(Boolean.FALSE,backend.evaluate(backend.compile(spec(f,INVARIANT,"self.p < 0")),view,values(f),"").knownBoolean());
            var undefined=backend.evaluate(backend.compile(spec(f,GOAL,"1 div 0 > 1")),view,values(f),"");assertEquals(UNDEFINED,undefined.status());assertNull(undefined.knownBoolean());
            var equality=backend.compile(spec(f,GOAL,"oclUndefined(Integer) = oclUndefined(Integer)"));
            assertEquals(PASS,backend.evaluate(equality,view,values(f),"").status());
            for(var reason:List.of("PARTIAL","GAP","ARGUMENT_UNAVAILABLE","INTERFERENCE","INCARNATION_MISMATCH","STALE_CORRELATION")) {
                var result=backend.evaluate(equality,view,values(f),reason);assertEquals(SKIPPED,result.status());assertNull(result.knownBoolean());assertEquals(reason,result.diagnostic());
            }
            assertEquals(ERROR,NativeOclOutcome.evaluate(null,view.after(),view.after(),new VarBindings()).status());
        }
    }
    @Test void exactArgumentsResultsAndForeignSelfBindingsAreFenced() throws Exception {
        var f=fixture();var foreign=fixture();try(var backend=backend(f)) {
            var compiled=backend.compile(spec(f,TASK_POST,"result = amount and self.p >= self.p@pre"));var view=UseStateReadView.pin(f.system().state(),f.system().state());
            for(String missing:List.of("amount","result","self")) {var values=new HashMap<>(values(f));values.remove(missing);assertEquals("OCL_BINDING_UNAVAILABLE:"+missing,backend.evaluate(compiled,view,values,"").diagnostic());}
            var wrong=new HashMap<>(values(f));wrong.put("amount",new StringValue("1"));assertEquals(ERROR,backend.evaluate(compiled,view,wrong,"").status());
            wrong=new HashMap<>(values(f));wrong.put("self",foreign.object().value());assertEquals(ERROR,backend.evaluate(compiled,view,wrong,"").status());
            assertThrows(IllegalArgumentException.class,()->new UseStateReadView(f.system().state(),foreign.system().state()));
            assertEquals(ERROR,backend.evaluate(compiled,UseStateReadView.pin(foreign.system().state(),foreign.system().state()),values(foreign),"").status());
        }
    }
    @Test void cacheOwnershipCapacityClearAndCloseRevokeAstReuse() throws Exception {
        var f=fixture();var foreign=fixture();try(var backend=new NativeOclBackend(f.system().model(),"p","profile",1);var other=backend(f)) {
            var spec=spec(f,GOAL,"true");var first=backend.compile(spec);assertSame(first,backend.compile(spec));
            assertThrows(IllegalStateException.class,()->backend.compile(spec(f,GOAL,"false")));
            assertThrows(IllegalArgumentException.class,()->other.compile(spec(foreign,GOAL,"true")));
            assertEquals(ERROR,other.evaluate(first,UseStateReadView.pin(null,f.system().state()),values(f),"").status());
            backend.clear();assertNotSame(first,backend.compile(spec));assertEquals(ERROR,backend.evaluate(first,UseStateReadView.pin(null,f.system().state()),values(f),"").status());
            backend.close();assertEquals(0,backend.metrics().expressions());assertThrows(IllegalStateException.class,()->backend.compile(spec));
        }
    }
    @Test void compiledInvariantMatchesHostAndPinnedEvaluationCanRunOffTheUiThread() throws Exception {
        var f=fixture();var invariant=f.schema().createInvariant("NonNegative","Counter","self.p >= 0",false);
        var pinned=UseStateReadView.pin(null,f.system().state());f.api().setAttributeValue("counter","p","-1");
        try(var backend=backend(f);var worker=java.util.concurrent.Executors.newSingleThreadExecutor()) {
            var compiled=backend.compile(spec(f,INVARIANT,"self.p >= 0"));
            var outcome=worker.submit(()->backend.evaluate(compiled,pinned,values(f),"")).get(5,java.util.concurrent.TimeUnit.SECONDS);
            assertEquals(PASS,outcome.status());
            assertEquals(BooleanValue.TRUE,new Evaluator().eval(invariant.bodyExpression(),pinned.after(),pinned.after(),bindings(values(f))));
            assertEquals(FAIL,backend.evaluate(compiled,UseStateReadView.pin(null,f.system().state()),values(f),"").status());
        }
    }
    private VarBindings bindings(Map<String,Value> values){var result=new VarBindings();values.forEach(result::push);return result;}
}
