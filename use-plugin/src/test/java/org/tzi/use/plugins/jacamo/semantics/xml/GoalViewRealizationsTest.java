package org.tzi.use.plugins.jacamo.semantics.xml;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.tzi.use.plugins.jacamo.semantics.runtime.*;
import org.tzi.use.semantic.engine.api.*;
import org.tzi.use.semantic.engine.runtime.*;
import org.vnu.sme.goal.dsl.istar.mm.*;
import org.vnu.sme.goal.verify.conformance.semantics.GoalTaskStatus;
import static org.junit.jupiter.api.Assertions.*;

class GoalViewRealizationsTest {
    static GoalModel model() {
        return org.vnu.sme.goal.dsl.istar.parser.IStarCompiler.compile("""
            istar Delivery {
              role Customer { goal received : Achieve }
              role Courier { goal delivered : Achieve }
              role Skills {
                Skills participates-in Courier
                goal capable : Achieve
                task prepare > capable pre {[ true ]} post {[ true ]}
                task transport > capable pre {[ true ]} post {[ true ]}
              }
              depend Customer.received -> goal delivery -> Courier.delivered
            }
            """,EngineSemanticsProfile.ROLE_SPECIALIZATION_V1.validationPolicy()).model();
    }
    static String xml() {
        return MappingXmlReaderTest.XML.replace("<bindings/>","""
            <bindings><goalRealization target="delivered" source="capable" sourceGoal="deliver" sourceRef="project" scheme="shipment" policy="SAME_SCOPE_PLAYER_REFINEMENT_VIEW/1.0" reason="Explicit two views of one authored source Goal"/></bindings>
            """);
    }
    static List<OccurrenceGoalModelExpander.Player> members() {
        var rows=new ArrayList<OccurrenceGoalModelExpander.Player>();
        rows.add(new OccurrenceGoalModelExpander.Player("Customer","Customer","customer","organization","context","context-life"));
        for(String player:List.of("one","two")) {
            rows.add(new OccurrenceGoalModelExpander.Player("Courier","Courier_"+player,"role_"+player,player,"role_"+player,"role-life_"+player));
            rows.add(new OccurrenceGoalModelExpander.Player("Skills","Skills_"+player,"view_"+player,player,"","player-life_"+player));
        }
        return rows;
    }
    @Test void declaredViewUsesOriginalRefinementAndSamePlayerWithoutAddingSocialDependencies() {
        var model=model();String original=OwnedGoalModel.fingerprint(model);
        var views=GoalViewRealizations.compile(MappingXmlReaderTest.parse(xml()),model);
        var expander=new OccurrenceGoalModelExpander(model,EngineSemanticsProfile.ROLE_SPECIALIZATION_V1,Map.of("delivery",OccurrenceGoalModelExpander.Quantifier.ANY),views,Set.of("Skills"));
        var expanded=expander.expand(members(),"DeliveryRun");
        assertEquals(2,expanded.model().getDependencies().size(),"Only population expansion of the ONE authored Dependency");
        assertTrue(expanded.model().getDependencies().stream().allMatch(d->expanded.trace().get(d.dependum().id()).source().equals("delivery")));
        for(Boolean post:Arrays.asList(Boolean.TRUE,Boolean.FALSE,null))try(var engine=new Use4GoalRuntimeSessionAdapter(expanded.model(),expanded.cast(),(a,x,b,y)->true,"delivery",EngineSemanticsProfile.ROLE_SPECIALIZATION_V1,EngineTopologyPolicy.SINGLE_OWNER_CONTEXT_V1)) {
            var tasks=new TreeMap<String,EngineInputs.TaskInput>();
            for(var occurrence:engine.topology().occurrences().values()) {
                var trace=expanded.trace().get(occurrence.sourceElementId());
                if(Set.of("prepare","transport").contains(trace.source()))tasks.put(occurrence.occurrenceId(),new EngineInputs.TaskInput("episode_"+trace.player(),true,true,trace.player().equals("one")?post:Boolean.FALSE));
            }
            var result=engine.step(new EngineInputs(Map.of(),tasks,Map.of()));
            assertTrue(result.originalApiCalls().contains("TaskMarking.update"));
            assertTrue(result.originalApiCalls().contains("GoalModelInstanceTranslator.propagateDependencies"));
            for(var occurrence:engine.topology().occurrences().values()) {
                var trace=expanded.trace().get(occurrence.sourceElementId());
                if(trace.source().equals("delivered"))assertEquals(trace.player().equals("one")&&Boolean.TRUE.equals(post),result.aggregate().goalTaskStatus(occurrence.occurrenceId())==GoalTaskStatus.FULFILLED,"No success from another player or a false/missing POST");
            }
        }
        assertEquals(original,OwnedGoalModel.fingerprint(model),"The canonical diagram graph must remain unchanged");
    }
    @Test void missingAndAmbiguousPhysicalCounterpartsRejectRatherThanBorrowAnotherPlayer() {
        var model=model();var views=GoalViewRealizations.compile(MappingXmlReaderTest.parse(xml()),model);
        var expander=new OccurrenceGoalModelExpander(model,EngineSemanticsProfile.ROLE_SPECIALIZATION_V1,Map.of("delivery",OccurrenceGoalModelExpander.Quantifier.ANY),views,Set.of("Skills"));
        var missing=new ArrayList<>(members());missing.removeIf(p->p.sourceActor().equals("Skills")&&p.player().equals("two"));
        assertThrows(IllegalArgumentException.class,()->expander.expand(missing,"Missing"));
        var duplicate=new ArrayList<>(members());duplicate.add(new OccurrenceGoalModelExpander.Player("Skills","Skills_another","other-view","one","","another-life"));
        assertThrows(IllegalArgumentException.class,()->expander.expand(duplicate,"Ambiguous"));
    }
    @Test void invalidPoliciesUnrelatedGoalsAndDuplicateTargetsCannotAuthorizeAView() {
        for(String bad:List.of(xml().replace("SAME_SCOPE_PLAYER_REFINEMENT_VIEW/1.0","ASSUME_SUCCESS"),xml().replace("source=\"capable\"","source=\"received\""),xml().replace("target=\"delivered\"","target=\"capable\""),xml().replace("</bindings>",xml().substring(xml().indexOf("<goalRealization"),xml().indexOf("</bindings>"))+"</bindings>")))
            assertThrows(IllegalArgumentException.class,()->GoalViewRealizations.compile(MappingXmlReaderTest.parse(bad),model()));
    }
    @Test void reenactedRoleCannotBorrowThePhysicalPlayersOlderTaskHistory() {
        var model=model();var views=GoalViewRealizations.compile(MappingXmlReaderTest.parse(xml()),model);
        var expander=new OccurrenceGoalModelExpander(model,EngineSemanticsProfile.ROLE_SPECIALIZATION_V1,Map.of("delivery",OccurrenceGoalModelExpander.Quantifier.ANY),views,Set.of("Skills"));
        var before=expander.expand(members(),"Before");var changed=new ArrayList<>(members());
        changed.replaceAll(p->p.sourceActor().equals("Courier")&&p.player().equals("one")?new OccurrenceGoalModelExpander.Player(p.sourceActor(),p.actorType(),p.instance(),p.player(),p.role(),"new-role-life"):p);
        var after=expander.expand(changed,"After");
        try(var previous=new Use4GoalRuntimeSessionAdapter(before.model(),before.cast(),(a,x,b,y)->true,"before",EngineSemanticsProfile.ROLE_SPECIALIZATION_V1,EngineTopologyPolicy.SINGLE_OWNER_CONTEXT_V1);
            var next=new Use4GoalRuntimeSessionAdapter(after.model(),after.cast(),(a,x,b,y)->true,"after",EngineSemanticsProfile.ROLE_SPECIALIZATION_V1,EngineTopologyPolicy.SINGLE_OWNER_CONTEXT_V1)) {
            previous.step(tasks(previous,before,true));var episodes=new TreeMap<String,String>();
            next.topology().occurrences().forEach((id,o)->episodes.put(id,"episode_"+after.trace().get(o.sourceElementId()).player()));
            next.seedCompatibleLeafHistory(previous,episodes);var result=next.step(tasks(next,after,false));
            boolean inspected=false;
            for(var occurrence:next.topology().occurrences().values()) {
                var trace=after.trace().get(occurrence.sourceElementId());
                if(!trace.player().equals("one"))continue;
                if(trace.source().equals("capable"))assertEquals(GoalTaskStatus.FULFILLED,result.aggregate().goalTaskStatus(occurrence.occurrenceId()),"The same physical Agent retains its old admitted Task history");
                if(trace.source().equals("delivered")){inspected=true;assertNotEquals(GoalTaskStatus.FULFILLED,result.aggregate().goalTaskStatus(occurrence.occurrenceId()),"A new Role interval needs its own matching realization evidence");}
            }
            assertTrue(inspected);
        }
    }
    private static EngineInputs tasks(Use4GoalRuntimeSessionAdapter engine,OccurrenceGoalModelExpander.Expansion expansion,boolean includeOne) {
        var tasks=new TreeMap<String,EngineInputs.TaskInput>();
        for(var occurrence:engine.topology().occurrences().values()) {
            var trace=expansion.trace().get(occurrence.sourceElementId());
            if(Set.of("prepare","transport").contains(trace.source())&&(includeOne||!trace.player().equals("one")))tasks.put(occurrence.occurrenceId(),new EngineInputs.TaskInput("episode_"+trace.player(),true,true,trace.player().equals("one")));
        }
        return new EngineInputs(Map.of(),tasks,Map.of());
    }
}
