package org.tzi.use.plugins.jacamo.semantics;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.tzi.use.plugins.jacamo.semantics.istar.*;
import org.tzi.use.plugins.jacamo.semantics.profile.SemanticsProfile;
import org.tzi.use.plugins.jacamo.semantics.evidence.SemanticVerdict;
import static org.tzi.use.plugins.jacamo.semantics.istar.IntentionalMarking.Status.*;

class TemporalGoalMonitorTest {
    static final RequirementModel.Binding B=new RequirementModel.Binding("actor","agent","role","context","self","domain","episode");
    static RequirementModel.Node goal(String id,RequirementModel.Temporal type){return new RequirementModel.Node(id,id,RequirementModel.Kind.GOAL,type,B,"self.p > 0","true",RequirementModel.Refinement.NONE,List.of(),"","");}
    static TemporalGoalMonitor.Input observation(SemanticVerdict value){return new TemporalGoalMonitor.Input(SemanticVerdict.PASS,value,"binding","");}
    @Test void independentlyExpectedTemporalTraces() {
        var types=RequirementModel.Temporal.values();var model=new RequirementModel(Arrays.stream(types).map(t->goal(t.name(),t)).toList());model.admit(SemanticsProfile.v2());
        var monitor=new TemporalGoalMonitor(model);
        var input=new HashMap<String,TemporalGoalMonitor.Input>();
        var trace=List.of(SemanticVerdict.FAIL,SemanticVerdict.PASS,SemanticVerdict.FAIL,SemanticVerdict.PASS);
        var expected=List.of(List.of(VIOLATED,UNDETERMINED,VIOLATED,UNDETERMINED),List.of(SATISFIED,SATISFIED,VIOLATED,SATISFIED),List.of(VIOLATED,SATISFIED,VIOLATED,VIOLATED),List.of(SATISFIED,SATISFIED,VIOLATED,VIOLATED));
        for(int i=0;i<trace.size();i++) {
            var value=trace.get(i);for(var type:types)input.put(type.name(),observation(value));
            var output=monitor.evaluate(i,input,false);
            for(int t=0;t<types.length;t++)assertEquals(expected.get(i).get(t),output.get(types[t].name()).evaluated(),types[t]+":"+i);
        }
    }
    @Test void historyGapReactivationAndIncarnationHaveExplicitSemantics() {
        var monitor=new TemporalGoalMonitor(new RequirementModel(List.of(goal("a",RequirementModel.Temporal.ACHIEVE),goal("m",RequirementModel.Temporal.MAINTAIN),goal("s",RequirementModel.Temporal.SUSTAIN))));
        var trueInput=new HashMap<String,TemporalGoalMonitor.Input>();for(String id:List.of("a","m","s"))trueInput.put(id,observation(SemanticVerdict.PASS));
        assertEquals(SATISFIED,monitor.evaluate(0,trueInput,false).get("m").evaluated());
        var missing=new HashMap<String,TemporalGoalMonitor.Input>();for(String id:trueInput.keySet())missing.put(id,observation(SemanticVerdict.SKIPPED));
        assertEquals(SATISFIED,monitor.evaluate(1,missing,true).get("a").evaluated());
        var repaired=monitor.evaluate(2,trueInput,false);assertEquals(UNDETERMINED,repaired.get("m").evaluated());assertEquals(UNDETERMINED,repaired.get("s").evaluated());
        var off=new HashMap<String,TemporalGoalMonitor.Input>();for(String id:trueInput.keySet())off.put(id,new TemporalGoalMonitor.Input(SemanticVerdict.FAIL,SemanticVerdict.PASS,"binding",""));
        monitor.evaluate(3,off,false);assertEquals(SATISFIED,monitor.evaluate(4,trueInput,false).get("m").evaluated());
        var newBinding=new HashMap<>(trueInput);newBinding.put("a",new TemporalGoalMonitor.Input(SemanticVerdict.PASS,SemanticVerdict.FAIL,"new-incarnation",""));
        assertEquals(UNDETERMINED,monitor.evaluate(5,newBinding,false).get("a").evaluated());
    }
    @Test void missingBindingCannotRepairMaintainHistoryAndKnownWitnessSurvivesOnlySameEpisode() {
        var monitor=new TemporalGoalMonitor(new RequirementModel(List.of(goal("m",RequirementModel.Temporal.MAINTAIN),goal("a",RequirementModel.Temporal.ACHIEVE))));
        monitor.evaluate(0,Map.of("m",observation(SemanticVerdict.PASS),"a",observation(SemanticVerdict.PASS)),false);
        var missing=new TemporalGoalMonitor.Input(SemanticVerdict.SKIPPED,SemanticVerdict.SKIPPED,"","BINDING_UNAVAILABLE");monitor.evaluate(1,Map.of("m",missing,"a",missing),false);
        var resumed=monitor.evaluate(2,Map.of("m",observation(SemanticVerdict.PASS),"a",observation(SemanticVerdict.PASS)),false);assertEquals(UNDETERMINED,resumed.get("m").evaluated());assertTrue(resumed.get("m").historyGap());assertEquals(SATISFIED,resumed.get("a").evaluated());
        var changed=monitor.evaluate(3,Map.of("m",new TemporalGoalMonitor.Input(SemanticVerdict.PASS,SemanticVerdict.PASS,"new-binding",""),"a",new TemporalGoalMonitor.Input(SemanticVerdict.PASS,SemanticVerdict.FAIL,"new-binding","")),false);assertEquals(SATISFIED,changed.get("m").evaluated());assertEquals(UNDETERMINED,changed.get("a").evaluated());
    }
    @Test void taskCannotUseHistoricalPreconditionSuccessAndUnknownIsNotFalse() {
        var node=new RequirementModel.Node("t","t",RequirementModel.Kind.TASK,RequirementModel.Temporal.NONE,B,"","true",RequirementModel.Refinement.NONE,List.of(),"","contract");var monitor=new TemporalGoalMonitor(new RequirementModel(List.of(node)));
        assertEquals(UNDETERMINED,monitor.evaluate(0,Map.of("t",observation(SemanticVerdict.SKIPPED)),false).get("t").evaluated());assertEquals(SATISFIED,monitor.evaluate(1,Map.of("t",observation(SemanticVerdict.PASS)),false).get("t").evaluated());assertEquals(UNDETERMINED,monitor.evaluate(2,Map.of("t",observation(SemanticVerdict.UNDEFINED)),false).get("t").evaluated());assertEquals(VIOLATED,monitor.evaluate(3,Map.of("t",observation(SemanticVerdict.FAIL)),false).get("t").evaluated());
        var image=monitor.savepoint();var once=monitor.evaluate(4,Map.of("t",observation(SemanticVerdict.PASS)),false);assertSame(once,monitor.evaluate(4,Map.of("t",observation(SemanticVerdict.PASS)),false));monitor.restore(image);assertEquals(once,monitor.evaluate(4,Map.of("t",observation(SemanticVerdict.PASS)),false));
    }
    @Test void refinementUsesEvaluatedChildrenAndDependencyUsesBoundOccurrence() {
        var a=goal("a",RequirementModel.Temporal.ACHIEVE);var b=goal("b",RequirementModel.Temporal.NONE);
        var parent=new RequirementModel.Node("p","p",RequirementModel.Kind.GOAL,RequirementModel.Temporal.NONE,B,"","true",RequirementModel.Refinement.OR,List.of("a","b"),"","");
        var dep=new RequirementModel.Node("d","d",RequirementModel.Kind.GOAL,RequirementModel.Temporal.NONE,
            new RequirementModel.Binding("other","other-agent","role","other-context","other-self","domain","episode"),"","true",RequirementModel.Refinement.NONE,List.of(),"p","");
        var monitor=new TemporalGoalMonitor(new RequirementModel(List.of(parent,dep,a,b)));
        var input=new HashMap<String,TemporalGoalMonitor.Input>();for(String id:List.of("a","b","p","d"))input.put(id,observation(SemanticVerdict.PASS));
        assertEquals(SATISFIED,monitor.evaluate(0,input,false).get("d").evaluated());
        input.put("a",observation(SemanticVerdict.FAIL));input.put("b",observation(SemanticVerdict.FAIL));assertEquals(SATISFIED,monitor.evaluate(1,input,false).get("p").evaluated());
        assertEquals(UNDETERMINED,TemporalGoalMonitor.and(List.of(SATISFIED,UNDETERMINED)));assertEquals(VIOLATED,TemporalGoalMonitor.and(List.of(VIOLATED,UNDETERMINED)));
        assertEquals(SATISFIED,TemporalGoalMonitor.or(List.of(SATISFIED,UNDETERMINED)));assertEquals(UNDETERMINED,TemporalGoalMonitor.or(List.of(VIOLATED,UNDETERMINED)));
        assertThrows(IllegalArgumentException.class,()->TemporalGoalMonitor.and(List.of()));
    }
}
