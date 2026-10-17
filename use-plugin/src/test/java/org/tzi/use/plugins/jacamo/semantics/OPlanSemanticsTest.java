package org.tzi.use.plugins.jacamo.semantics;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import moise.os.OS;
import org.jacamo.bridge.adapter.OfficialMoiseAdapter;
import org.jacamo.bridge.contract.semantic.MoiseSemanticContract.*;
import org.tzi.use.plugins.jacamo.semantics.model.ExecutionDescriptor;
import org.tzi.use.plugins.jacamo.semantics.runtime.OPlanTransitionMonitor;
import org.tzi.use.plugins.jacamo.semantics.observer.FunctionalObservation.State;
import org.tzi.use.plugins.jacamo.semantics.istar.*;
import org.tzi.use.plugins.jacamo.semantics.evidence.*;

class OPlanSemanticsTest {
    record Source(moise.os.fs.Scheme official,SchemeSemantic contract){}
    static Source source(String operator,boolean nested)throws Exception {
        String children=nested?"<goal id='a'><plan operator='parallel'><goal id='a1'/><goal id='a2'/></plan></goal><goal id='b'/>":"<goal id='a'/><goal id='b'/>";
        String xml="<organisational-specification id='plans' os-version='1.1' xmlns='http://moise.sourceforge.net/os'><structural-specification><role-definitions><role id='worker'/></role-definitions><group-specification id='team'><roles><role id='worker' min='0' max='3'/></roles></group-specification></structural-specification><functional-specification><scheme id='s'><goal id='root'><plan operator='"+operator+"'>"+children+"</plan></goal><mission id='work' min='0' max='2'><goal id='a'/><goal id='b'/></mission></scheme></functional-specification><normative-specification/></organisational-specification>";
        Path path=Files.createTempFile(Path.of("target"),"oplan-oracle-",".xml");Files.writeString(path,xml);var os=OS.loadOSFromURI(path.toAbsolutePath().toString());var org=new OfficialMoiseAdapter().capture(os,path.getParent(),"oplan").organization();return new Source(os.getFS().findScheme("s"),org.functionalSpecification().schemes().getFirst());
    }
    static String id(SchemeSemantic scheme,String name){return scheme.goals().stream().filter(g->g.goalId().equals(name)).findFirst().orElseThrow().metadata().semanticId();}
    @Test void orderedAndNestedPreconditionsMatchActualMoiseGoalApiForEveryGoal()throws Exception {
        for(String operator:List.of("sequence","parallel","choice"))for(boolean nested:List.of(false,true)) {
            var source=source(operator,nested);var descriptor=ExecutionDescriptor.from(source.contract());
            for(var goal:source.contract().goals()) {
                var expected=source.official().getGoal(goal.goalId()).getPreConditionGoals().stream().map(g->id(source.contract(),g.getId())).toList();
                assertEquals(expected,descriptor.enabling().stream().filter(e->e.goal().equals(goal.metadata().semanticId())).findFirst().orElseThrow().preconditions(),operator+":"+goal.goalId());
            }
            var plan=descriptor.plans().stream().filter(p->p.target().equals(id(source.contract(),"root"))).findFirst().orElseThrow();assertEquals(operator,plan.rawOperator());assertEquals(List.of(id(source.contract(),"a"),id(source.contract(),"b")),plan.children().stream().map(ExecutionDescriptor.Child::goal).toList());
        }
    }
    @Test void sequenceCounterexampleParallelInterleavingsAndMissingCausalityAreDifferent()throws Exception {
        var monitor=new OPlanTransitionMonitor();var sequence=source("sequence",false).contract();var seq=ExecutionDescriptor.from(sequence);String sa=id(sequence,"a"),sb=id(sequence,"b");
        var wrong=monitor.check(seq,new OPlanTransitionMonitor.Transition("inv-b",sb,OPlanTransitionMonitor.Phase.ENABLED,Map.of(sa,State.WAITING,sb,State.WAITING),true,true,false));assertEquals(SemanticVerdict.FAIL,wrong.verdict());assertEquals("WAITING",wrong.witness().get(sa));
        assertEquals(SemanticVerdict.PASS,monitor.check(seq,new OPlanTransitionMonitor.Transition("inv-b",sb,OPlanTransitionMonitor.Phase.ENABLED,Map.of(sa,State.SATISFIED,sb,State.WAITING),true,true,false)).verdict());
        assertEquals(SemanticVerdict.INCONCLUSIVE,monitor.check(seq,new OPlanTransitionMonitor.Transition("arrival-b",sb,OPlanTransitionMonitor.Phase.ENABLED,Map.of(sa,State.WAITING),true,false,false)).verdict());
        var parallel=source("parallel",false).contract();var par=ExecutionDescriptor.from(parallel);String pa=id(parallel,"a"),pb=id(parallel,"b");
        for(var order:List.of(List.of(pa,pb),List.of(pb,pa))) {var state=new HashMap<String,State>();state.put(pa,State.WAITING);state.put(pb,State.WAITING);for(String goal:order){assertEquals(SemanticVerdict.PASS,monitor.check(par,new OPlanTransitionMonitor.Transition("enabled-"+goal,goal,OPlanTransitionMonitor.Phase.ENABLED,state,true,true,false)).verdict());state.put(goal,State.SATISFIED);}}
        assertNotEquals(SemanticJson.hash(SemanticJson.tree(seq)),SemanticJson.hash(SemanticJson.tree(par)));
    }
    @Test void outcomeChoiceAcceptsOneOrBothAndSequenceStillRetainsItsOrder()throws Exception {
        var binding=TemporalGoalMonitorTest.B;
        for(String operator:List.of("choice","sequence","parallel")) {
            var source=source(operator,false).contract();var descriptor=ExecutionDescriptor.from(source);var plan=descriptor.plans().getFirst();
            var parent=new OPlanOutcomeMapper().bind(plan,"parent","parent",RequirementModel.Temporal.NONE,binding,Map.of(id(source,"a"),"a",id(source,"b"),"b"));
            assertEquals(operator.equals("choice")?RequirementModel.Refinement.OR:RequirementModel.Refinement.AND,parent.refinement());
            var model=new RequirementModel(List.of(parent,TemporalGoalMonitorTest.goal("a",RequirementModel.Temporal.NONE),TemporalGoalMonitorTest.goal("b",RequirementModel.Temporal.NONE)));
            var monitor=new TemporalGoalMonitor(model);var inputs=new HashMap<String,TemporalGoalMonitor.Input>();inputs.put("parent",TemporalGoalMonitorTest.observation(SemanticVerdict.PASS));inputs.put("a",TemporalGoalMonitorTest.observation(SemanticVerdict.PASS));inputs.put("b",TemporalGoalMonitorTest.observation(SemanticVerdict.FAIL));
            assertEquals(operator.equals("choice")?IntentionalMarking.Status.SATISFIED:IntentionalMarking.Status.VIOLATED,monitor.evaluate(0,inputs,false).get("parent").evaluated());inputs.put("b",TemporalGoalMonitorTest.observation(SemanticVerdict.PASS));assertEquals(IntentionalMarking.Status.SATISFIED,monitor.evaluate(1,inputs,false).get("parent").evaluated());
        }
    }
    @Test void invalidOrdinalDuplicateAndCustomOperatorProduceExplicitDecisions()throws Exception {
        assertThrows(IllegalArgumentException.class,()->new ExecutionDescriptor.Plan("p","g","sequence",ExecutionDescriptor.Operator.SEQUENCE,1,List.of(new ExecutionDescriptor.Child("c",1))));
        var source=source("sequence",false).contract();var original=source.plans().getFirst();var custom=new OrganizationalPlanSemantic(original.metadata(),original.planId(),original.schemeSemanticId(),original.targetGoalSemanticId(),"custom",1,original.orderedSubGoalSemanticIds());
        var descriptor=ExecutionDescriptor.from(new SchemeSemantic(source.metadata(),source.schemeId(),source.functionalSpecificationSemanticId(),source.rootGoalSemanticId(),source.missions(),source.goals(),List.of(custom)));
        assertEquals(SemanticVerdict.UNSUPPORTED,new OPlanTransitionMonitor().check(descriptor,new OPlanTransitionMonitor.Transition("inv",id(source,"a"),OPlanTransitionMonitor.Phase.ENABLED,Map.of(),true,true,false)).verdict());
    }
}
