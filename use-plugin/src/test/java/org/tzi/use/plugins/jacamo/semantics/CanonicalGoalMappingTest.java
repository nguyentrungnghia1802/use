package org.tzi.use.plugins.jacamo.semantics;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.jacamo.bridge.adapter.*;
import org.jacamo.bridge.contract.*;
import org.tzi.use.plugins.jacamo.semantics.mapping.*;
import org.tzi.use.plugins.jacamo.semantics.model.ExecutionDescriptor;
import org.tzi.use.semantic.engine.api.OwnedGoalModel;
import org.tzi.use.semantic.engine.runtime.*;
import org.tzi.use.semantic.engine.use75.OclContractSpec;
import org.vnu.sme.goal.dsl.istar.mm.*;
import org.vnu.sme.goal.dsl.istar.parser.IStarCompiler;
import org.vnu.sme.goal.dsl.iscn.mm.*;
import org.vnu.sme.goal.dsl.iscn.goalmodelinstance.parser.GoalModelInstanceTranslator;
import org.vnu.sme.goal.verify.conformance.semantics.*;
import static org.tzi.use.plugins.jacamo.semantics.mapping.SemanticBindingIndex.Kind.*;
import static org.junit.jupiter.api.Assertions.*;

public class CanonicalGoalMappingTest {
    static ModelSnapshot source;static final JaCaMoMappingProfile PROFILE=JaCaMoMappingProfile.observedV1();
    @BeforeAll static void load() throws Exception {source=source();}
    public static ModelSnapshot source() throws Exception {var path=Path.of("src/test/resources/semantics/original-engine/monitor.jcm").toAbsolutePath();return new OfficialProjectAdapter().adapt(new OfficialProjectLoader().load(path),path);}
    static String istar(){return """
        istar Requirements {
          role Worker {
            goal all : None
            goal left : Achieve > all condition {[ self.p >= 1 ]}
            goal right : Achieve > all condition {[ self.p >= 2 ]}
            goal any : None
            goal opt1 : Achieve > or any condition {[ self.p >= 1 ]}
            goal opt2 : Achieve > or any condition {[ self.p >= 2 ]}
            goal supplied : Achieve condition {[ self.p >= 1 ]}
            task increment pre {[ self.p >= 0 ]} post {[ self.p = self.p@pre + 1 ]}
          }
          role Reviewer { goal requested : Achieve }
          depend Reviewer.requested -> goal promise -> Worker.supplied
        }
        """;}
    public static MappingRuleSet rules(ModelSnapshot input) {
        var base=CanonicalCslMappingTest.rules(input,CanonicalCslMappingTest.csl());var bindings=new ArrayList<>(base.bindings());
        var ss=input.semanticContract().moiseOrganizations().getFirst().structuralSpecification();String context=ss.groups().getFirst().metadata().semanticId();
        String worker=ss.roles().stream().filter(r->r.roleId().equals("worker")).findFirst().orElseThrow().metadata().semanticId();
        String reviewer=ss.roles().stream().filter(r->r.roleId().equals("reviewer")).findFirst().orElseThrow().metadata().semanticId();
        bindings.add(actor(worker,"Worker",context,input.semanticContract().agentDeclarations().stream().filter(a->a.name().equals("alice")).findFirst().orElseThrow().metadata().semanticId()));
        bindings.add(actor(reviewer,"Reviewer",context,input.semanticContract().agentDeclarations().stream().filter(a->a.name().equals("bob")).findFirst().orElseThrow().metadata().semanticId()));
        var scheme=input.semanticContract().moiseOrganizations().getFirst().functionalSpecification().schemes().getFirst();
        String self=input.semanticContract().artifactDeclarations().getFirst().metadata().semanticId();
        for(String target:List.of("all","left","right","any","opt1","opt2","supplied","requested")) {
            String original=switch(target){case "all"->scheme.rootGoalSemanticId();case "left","right"->scheme.goals().stream().filter(g->g.goalId().equals(target.equals("left")?"first":"second")).findFirst().orElseThrow().metadata().semanticId();default->worker;};
            bindings.add(new SemanticBindingIndex.Binding(original,GOAL,target,"",original.equals(worker)?"M03":"M08","AUTHORED_WORLD_OCL_AND_TEMPORAL_TYPE",Map.of("selfDeclaration","Counter","selfSourceId",self)));
        }
        bindings.add(new SemanticBindingIndex.Binding(self,TASK,"increment","","M19","AUTHORED_INVOCATION_ABSTRACTION",Map.of("selfDeclaration","Counter","selfSourceId",self,"contractId","increment")));
        bindings.add(new SemanticBindingIndex.Binding(scheme.plans().getFirst().metadata().semanticId(),REFINEMENT,"all","","M09","OUTCOME_CONJUNCTION_ONLY_PRESERVE_EXECUTION_ORDER",Map.of("abstraction","OUTCOME_ONLY")));
        var signature=List.<Object>of(CslCounterArtifact.class.getName(),"increment",List.of(),"void");
        var contracts=List.of(new MappingRuleSet.Contract("increment:pre",OclContractSpec.Kind.TASK_PRE,"Counter","increment","self.p >= 0",signature,Map.of(),"AUTHORED_OCL"),
                new MappingRuleSet.Contract("increment:post",OclContractSpec.Kind.TASK_POST,"Counter","increment","self.p = self.p@pre + 1",signature,Map.of(),"AUTHORED_OCL"));
        return new MappingRuleSet(base.version(),"original-goal-counter",base.profileId(),base.profileVersion(),"",base.cslSource(),istar(),bindings,contracts,List.of(),base.options());
    }
    private static SemanticBindingIndex.Binding actor(String source,String target,String context,String player){return new SemanticBindingIndex.Binding(source,ACTOR,target,"","M03","EXPLICIT_INTENTIONAL_ROLE_VIEW",Map.of("contextSourceId",context,"playerSourceId",player,"activationPolicy","ENACTMENT_INTERVAL","episodePolicy","ENACTMENT_INCARNATION"));}
    @Test void actualSourceAndAuthoredAbstractionProduceOriginalModelAndExactDefinitionTemplates() throws Exception {
        var rules=rules(source);var program=new JaCaMoStaticMappingAdapter().map(source,PROFILE,rules);
        assertTrue(GoalModelValidator.validate(program.goalModel()).isEmpty());assertEquals(2,program.goalModel().getActors().size());
        assertEquals(new OwnedGoalModel(IStarCompiler.compile(istar()).model()).hash(),program.goalHash());
        assertEquals(2,program.goalBindings().actors().size());assertTrue(program.goalBindings().elements().get("all").derived());assertTrue(program.goalBindings().elements().get("requested").derived());
        assertEquals("increment",program.goalBindings().elements().get("increment").contractId());assertFalse(program.goalBindings().elements().get("left").derived());
        assertEquals(1,program.goalBindings().executionDescriptors().size());var plan=program.goalBindings().executionDescriptors().getFirst().plans().getFirst();
        assertEquals(ExecutionDescriptor.Operator.SEQUENCE,plan.operator());assertEquals(List.of(0,1),plan.children().stream().map(ExecutionDescriptor.Child::ordinal).toList());
        assertTrue(program.diagnostics().stream().anyMatch(d->d.code().equals("OUTCOME_ONLY_ORDER_RETAINED")));assertTrue(program.diagnostics().stream().anyMatch(d->d.code().equals("MOISE_TYPE_NOT_CAST_TO_TEMPORAL")));
        assertEquals(GoalType.ACHIEVE,((Goal)program.goalModel().findElement("left").orElseThrow()).goalType());
        Path evidence=Path.of("target/semantic-migration/goal-mapping.json");Files.createDirectories(evidence.getParent());Files.write(evidence,CanonicalJson.encode(Map.of("programHash",program.hash(),"goalHash",program.goalHash(),"templates",org.tzi.use.plugins.jacamo.semantics.evidence.SemanticJson.tree(program.goalBindings()))));
    }
    @Test void independentContextSessionsUseOriginalAndOrDependencyWithoutCrossContextHistory() {
        var program=new JaCaMoStaticMappingAdapter().map(source,PROFILE,rules(source));
        try(var first=session(program,"context1");var second=session(program,"context2")) {
            var firstResult=first.step(inputs(first,false));var secondResult=second.step(inputs(second,true));
            assertEquals(GoalTaskStatus.PENDING,firstResult.aggregate().goalTaskStatus(occ(first,"all")));assertEquals(GoalTaskStatus.FULFILLED,secondResult.aggregate().goalTaskStatus(occ(second,"all")));
            assertEquals(GoalTaskStatus.FULFILLED,firstResult.aggregate().goalTaskStatus(occ(first,"any")));
            var dependency=first.topology().instance().model().getDependencies().getFirst();assertEquals(GoalTaskStatus.FULFILLED,firstResult.aggregate().goalTaskStatus(dependency.dependerElmt()));
            assertEquals(firstResult.history(),first.checkpoint());assertNotEquals(first.checkpoint().ownerToken(),second.checkpoint().ownerToken());
            var script=new IStarScenarioModel("live","<memory>",List.of(new ScenarioInstance("context1-alice","Worker"),new ScenarioInstance("context1-bob","Reviewer")),
                    List.of(new ScenarioStmt.Assign("context1-alice","left","Fulfilled"),new ScenarioStmt.Assign("context1-alice","right","Pending"),new ScenarioStmt.Assign("context1-alice","opt1","Fulfilled"),new ScenarioStmt.Assign("context1-alice","opt2","Pending"),new ScenarioStmt.Assign("context1-alice","supplied","Fulfilled")));
            var oracle=GoalModelInstanceTranslator.evaluate(program.goalModel(),script,(a,x,b,y)->true);
            assertEquals(oracle.instanceMarking().goalTaskStatuses(),firstResult.aggregate().goalTaskStatuses());
        }
    }
    @Test void missingBindingsRootPolicyTaskContractAndTemporalCastingAreUnsupported() {
        var base=rules(source);var absent=base.bindings().stream().filter(b->!(b.kind()==ACTOR&&b.targetId().equals("Worker"))).toList();assertUnsupported(base,absent,"ACTOR_BINDING_REQUIRED");
        var wrongDemand=change(base,"Worker",ACTOR,Map.of("activationPolicy","ACTIVE_JASON_GOAL"));assertUnsupported(base,wrongDemand,"EXPLICIT_ROOT_DEMAND_POLICY_REQUIRED");
        var wrongTemporal=change(base,"left",GOAL,Map.of("temporalType","achievement"));assertUnsupported(base,wrongTemporal,"TEMPORAL_TYPE_MUST_MATCH_AUTHORED_ISTAR");
        var wrongTask=change(base,"increment",TASK,Map.of("contractId","not-a-real-contract"));assertUnsupported(base,wrongTask,"TASK_OPERATION_CONTRACT_BINDING_REQUIRED");
        var wrongContext=change(base,"Worker",ACTOR,Map.of("contextSourceId","display-name-team"));assertUnsupported(base,wrongContext,"ACTOR_CONTEXT_DEFINITION_REQUIRED");
    }
    @Test void sequenceAsPlainAndAndActiveGoalAsSatisfactionCannotBeAdmitted() {
        var base=rules(source);assertUnsupported(base,change(base,"all",REFINEMENT,Map.of("abstraction","PLAIN_AND")),"OPLAN_ORDER_NOT_PLAIN_REFINEMENT");
        var jason=new SourceModelNormalizer().normalize(source).nodes().stream().filter(n->n.sourceKind().equals("JASON_AGENT_GOAL")).findFirst().orElseThrow();
        var bindings=new ArrayList<>(base.bindings());bindings.replaceAll(b->b.kind()==GOAL&&b.targetId().equals("left")?new SemanticBindingIndex.Binding(jason.id(),GOAL,b.targetId(),"","M12",b.abstractionReason(),b.metadata()):b);
        assertUnsupported(base,bindings,"ACTIVE_JASON_GOAL_IS_NOT_SATISFACTION");
    }
    @Test void normativeStateAndUnrelatedRefinementCannotStandInForSourceSemantics() {
        var base=rules(source);String norm=source.semanticContract().moiseOrganizations().getFirst().normativeSpecification().norms().getFirst().metadata().semanticId();
        var bindings=new ArrayList<>(base.bindings());bindings.replaceAll(b->b.kind()==GOAL&&b.targetId().equals("left")?new SemanticBindingIndex.Binding(norm,GOAL,b.targetId(),"","M05",b.abstractionReason(),b.metadata()):b);
        assertUnsupported(base,bindings,"NORM_IS_NOT_INTENTIONAL_STATE_CONDITION");
        bindings=new ArrayList<>(base.bindings());bindings.replaceAll(b->b.kind()==REFINEMENT?new SemanticBindingIndex.Binding(b.sourceId(),REFINEMENT,"any","",b.ruleId(),b.abstractionReason(),b.metadata()):b);
        assertUnsupported(base,bindings,"OPLAN_OUTCOME_GRAPH_MISMATCH");
    }
    private Use4GoalRuntimeSessionAdapter session(SemanticProgram program,String context){return new Use4GoalRuntimeSessionAdapter(program.goalModel(),List.of(new ScenarioInstance(context+"-alice","Worker"),new ScenarioInstance(context+"-bob","Reviewer")),(a,x,b,y)->x.startsWith(context+"-")&&y.startsWith(context+"-"),context);}
    private String occ(Use4GoalRuntimeSessionAdapter session,String id){return session.topology().occurrences().values().stream().filter(b->b.sourceElementId().equals(id)).map(b->b.occurrenceId()).findFirst().orElseThrow();}
    private EngineInputs inputs(Use4GoalRuntimeSessionAdapter session,boolean second) {var goals=new HashMap<String,EngineInputs.GoalInput>();for(String id:List.of("left","right","opt1","opt2","supplied"))goals.put(occ(session,id),new EngineInputs.GoalInput("episode",true,!Set.of("right","opt2").contains(id)||second));return new EngineInputs(goals,Map.of(),Map.of());}
    private List<SemanticBindingIndex.Binding> change(MappingRuleSet base,String target,SemanticBindingIndex.Kind kind,Map<String,String> changes){return base.bindings().stream().map(b->{if(b.kind()!=kind||!b.targetId().equals(target))return b;var meta=new HashMap<>(b.metadata());meta.putAll(changes);return new SemanticBindingIndex.Binding(b.sourceId(),b.kind(),b.targetId(),b.nativeSymbol(),b.ruleId(),b.abstractionReason(),meta);}).toList();}
    private void assertUnsupported(MappingRuleSet base,List<SemanticBindingIndex.Binding> bindings,String code) {
        var rules=new MappingRuleSet(base.version(),base.id(),base.profileId(),base.profileVersion(),"",base.cslSource(),base.iStarSource(),bindings,base.contracts(),base.decisions(),base.options());
        var error=assertThrows(MappingAdmissionException.class,()->new JaCaMoStaticMappingAdapter().map(source,PROFILE,rules));assertTrue(error.diagnostics().stream().anyMatch(d->d.code().equals(code)),error.toString());
    }
}
