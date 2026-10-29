package org.tzi.use.plugins.jacamo.semantics.xml;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.tzi.use.plugins.jacamo.semantics.runtime.OccurrenceGoalModelExpander;
import org.tzi.use.semantic.engine.api.*;
import org.tzi.use.semantic.engine.runtime.*;
import org.vnu.sme.goal.dsl.istar.mm.*;
import org.vnu.sme.goal.verify.conformance.semantics.GoalTaskStatus;
import static org.junit.jupiter.api.Assertions.*;

class OccurrenceGoalModelExpanderTest {
    static GoalModel source() {
        var model=new GoalModel("Shipment");
        model.addActor(new Role("Dispatch",List.of(new Goal("complete",GoalType.ACHIEVE)),List.of(),List.of(),List.of(),List.of(),List.of()));
        model.addActor(new Role("Carrier",List.of(new Task("ship",new IStarOclConstraint("true"),new IStarOclConstraint("true")),new Goal("ready",GoalType.ACHIEVE,new IStarOclConstraint("true")),new Resource("vehicle")),List.of(),List.of(),List.of(),List.of(new NeededBy("vehicle","ship")),List.of()));
        model.addDependency(new Dependency("Dispatch","complete",new Goal("shipment",GoalType.ACHIEVE),"Carrier","ship"));return model;
    }
    static List<OccurrenceGoalModelExpander.Player> cast(){return List.of(new OccurrenceGoalModelExpander.Player("Dispatch","Dispatch","dispatch-instance","org","",""),new OccurrenceGoalModelExpander.Player("Carrier","Carrier_A","carrier-a","physical-A","role-A","interval-A"),new OccurrenceGoalModelExpander.Player("Carrier","Carrier_B","carrier-b","physical-B","role-B","interval-B"));}
    @Test void explicitAllAndAnyUseOriginalMarkingsAndPropagationWithoutOverwritingOneDependencyReceiver() {
        for(var quantifier:OccurrenceGoalModelExpander.Quantifier.values()) {
            var model=source();String canonical=OwnedGoalModel.fingerprint(model);var expander=new OccurrenceGoalModelExpander(model,EngineSemanticsProfile.DERIVED_AND_RESOURCE_V1,Map.of("shipment",quantifier));
            var expanded=expander.expand(cast(),"ShipmentOccurrences");assertEquals(expanded.owned().hash(),expander.expand(cast(),"ShipmentOccurrences").owned().hash());assertEquals(2,expanded.model().getDependencies().size());
            try(var engine=new Use4GoalRuntimeSessionAdapter(expanded.model(),expanded.cast(),(a,x,b,y)->true,"scope-one",EngineSemanticsProfile.DERIVED_AND_RESOURCE_V1,EngineTopologyPolicy.SINGLE_OWNER_CONTEXT_V1)) {
                var result=engine.step(input(engine,expanded,Set.of("physical-A"),true));
                assertTrue(result.originalApiCalls().contains("TaskMarking.update"));assertTrue(result.originalApiCalls().contains("GoalMarking.update"));assertTrue(result.originalApiCalls().contains("GoalModelInstanceTranslator.propagateDependencies"));
                var aggregate=engine.topology().occurrences().values().stream().filter(o->expanded.trace().get(o.sourceElementId()).source().equals("complete")&&expanded.trace().get(o.sourceElementId()).kind().equals("SOURCE_ELEMENT")).findFirst().orElseThrow();
                // GoalModelInstanceTranslator.saturateDependencies transfers FULFILLED only.
                // A PENDING dependee does not activate its Goal receiver; original raw UNKNOWN
                // must remain distinct from both the known Task PENDING and a Boolean false.
                assertEquals(quantifier==OccurrenceGoalModelExpander.Quantifier.ANY?GoalTaskStatus.FULFILLED:GoalTaskStatus.UNKNOWN,result.aggregate().goalTaskStatus(aggregate.occurrenceId()));
                var pendingTask=engine.topology().occurrences().values().stream().filter(o->expanded.trace().get(o.sourceElementId()).source().equals("ship")&&expanded.trace().get(o.sourceElementId()).player().equals("physical-B")).findFirst().orElseThrow();
                assertEquals(GoalTaskStatus.PENDING,result.history().tasks().get(pendingTask.occurrenceId()).status());
                var before=result.history();var unknown=engine.step(input(engine,expanded,Set.of(),false));assertEquals(before.tasks(),unknown.history().tasks());assertFalse(unknown.originalApiCalls().contains("TaskMarking.update"));
                var all=engine.step(input(engine,expanded,Set.of("physical-A","physical-B"),true));assertEquals(GoalTaskStatus.FULFILLED,all.aggregate().goalTaskStatus(aggregate.occurrenceId()));
            }
            assertEquals(canonical,OwnedGoalModel.fingerprint(model));
        }
    }
    static EngineInputs input(Use4GoalRuntimeSessionAdapter engine,OccurrenceGoalModelExpander.Expansion expanded,Set<String> complete,boolean known) {
        var tasks=new TreeMap<String,EngineInputs.TaskInput>();var goals=new TreeMap<String,EngineInputs.GoalInput>();var resources=new HashMap<EngineInputs.ResourceKey,EngineInputs.ResourceInput>();
        for(var occurrence:engine.topology().occurrences().values()) {
            var trace=expanded.trace().get(occurrence.sourceElementId());String episode="scope-one:"+trace.player()+":"+trace.role();
            if(trace.source().equals("ship")) {
                tasks.put(occurrence.occurrenceId(),new EngineInputs.TaskInput(episode,true,known?true:null,known?complete.contains(trace.player()):null));
                expanded.model().getActors().stream().flatMap(a->a.neededBys().stream()).filter(n->n.task().equals(occurrence.sourceElementId())).forEach(n->resources.put(new EngineInputs.ResourceKey(occurrence.occurrenceId(),n.resource()),new EngineInputs.ResourceInput(episode,known?true:null,known?"actual-resource":"")));
            }
            if(trace.source().equals("ready"))goals.put(occurrence.occurrenceId(),new EngineInputs.GoalInput(episode,true,known?true:null));
        }
        return new EngineInputs(goals,tasks,Map.of(),resources);
    }
    @Test void xmlOwnsQuantifierAndMissingUnknownOrEmptyPopulationNeverMeansVacuousSuccess() {
        var model=source();String xml=MappingXmlReaderTest.XML.replace("<bindings/>","<bindings><dependencyPopulation target=\"shipment\" quantifier=\"ALL\"/></bindings>");
        assertEquals(Map.of("shipment",OccurrenceGoalModelExpander.Quantifier.ALL),OccurrenceGoalModelExpander.policies(MappingXmlReaderTest.parse(xml),model));
        for(String bad:List.of(xml.replace("ALL","SEQUENCE"),xml.replace("target=\"shipment\"","target=\"not-present\"")))assertThrows(IllegalArgumentException.class,()->OccurrenceGoalModelExpander.policies(MappingXmlReaderTest.parse(bad),model));
        assertThrows(IllegalArgumentException.class,()->new OccurrenceGoalModelExpander(model,EngineSemanticsProfile.DERIVED_AND_RESOURCE_V1,Map.of()).expand(cast(),"MissingPolicy"));
        assertThrows(IllegalArgumentException.class,()->new OccurrenceGoalModelExpander(model,EngineSemanticsProfile.DERIVED_AND_RESOURCE_V1,Map.of("shipment",OccurrenceGoalModelExpander.Quantifier.ALL)).expand(List.of(cast().getFirst()),"MissingActors"));
    }
    @Test void specializationKeepsCanonicalRelationWithoutInventingParentEnactmentOrSharingPlayerHistory() {
        var original=source();var model=new GoalModel("SpecializedShipment");
        model.addActor(original.findActor("Dispatch").orElseThrow());
        var carrier=original.findActor("Carrier").orElseThrow();
        model.addActor(new Role(carrier.name(),carrier.elements(),carrier.refinements(),carrier.contributions(),carrier.qualifications(),carrier.neededBys(),List.of(new Association("Carrier",AssocKind.IS_A,"Capability"))));
        model.addActor(new Role("Capability",List.of(),List.of(),List.of(),List.of(),List.of(),List.of()));
        original.getDependencies().forEach(model::addDependency);
        var profile=EngineSemanticsProfile.ROLE_SPECIALIZATION_V1;String canonical=OwnedGoalModel.fingerprint(model);
        var expander=new OccurrenceGoalModelExpander(model,profile,Map.of("shipment",OccurrenceGoalModelExpander.Quantifier.ANY));
        var expanded=expander.expand(cast(),"Execution");
        assertEquals(3,expanded.cast().size());assertTrue(expanded.cast().stream().noneMatch(p->p.actorType().equals("Capability")));
        assertEquals(2,expanded.trace().values().stream().filter(t->t.kind().equals("DEFINITION_ONLY_SPECIALIZATION")).count());
        assertEquals(canonical,OwnedGoalModel.fingerprint(model));assertEquals(1,model.findActor("Carrier").orElseThrow().associations().size());
        try(var engine=new Use4GoalRuntimeSessionAdapter(expanded.model(),expanded.cast(),(a,x,b,y)->true,"scope",profile,EngineTopologyPolicy.SINGLE_OWNER_CONTEXT_V1)) {
            var result=engine.step(input(engine,expanded,Set.of("physical-A"),true));
            var other=engine.topology().occurrences().values().stream().filter(o->expanded.trace().get(o.sourceElementId()).source().equals("ship")&&expanded.trace().get(o.sourceElementId()).player().equals("physical-B")).findFirst().orElseThrow();
            assertEquals(GoalTaskStatus.PENDING,result.history().tasks().get(other.occurrenceId()).status());
        }
        var invalid=new ArrayList<>(cast());invalid.add(new OccurrenceGoalModelExpander.Player("Capability","Capability","fake","invented-player","fake-role","fake-interval"));
        assertThrows(IllegalArgumentException.class,()->expander.expand(invalid,"Invalid"));
        assertThrows(IllegalArgumentException.class,()->new OccurrenceGoalModelExpander(model,EngineSemanticsProfile.DERIVED_AND_RESOURCE_V1,Map.of()));
    }
    @Test void completingEveryAttemptWithoutActualAcceptanceCannotFulfillCollectiveOutcome() {
        var model=org.vnu.sme.goal.dsl.istar.parser.IStarCompiler.compile("istar Dispatch { role Dispatch { goal complete : Achieve goal attempted : Achieve > complete goal accepted : Achieve > complete condition {[ true ]} } role Carrier { task ship pre {[ true ]} post {[ true ]} } depend Dispatch.attempted -> goal shipment -> Carrier.ship }").model();
        var expanded=new OccurrenceGoalModelExpander(model,EngineSemanticsProfile.DERIVED_AND_RESOURCE_V1,Map.of("shipment",OccurrenceGoalModelExpander.Quantifier.ANY)).expand(cast(),"Attempts");
        for(Boolean accepted:Arrays.asList(null,Boolean.FALSE,Boolean.TRUE))try(var engine=new Use4GoalRuntimeSessionAdapter(expanded.model(),expanded.cast(),(a,x,b,y)->true,"scope-one",EngineSemanticsProfile.DERIVED_AND_RESOURCE_V1,EngineTopologyPolicy.SINGLE_OWNER_CONTEXT_V1)) {
            var tasks=new TreeMap<String,EngineInputs.TaskInput>();var goals=new TreeMap<String,EngineInputs.GoalInput>();String root="";
            for(var occurrence:engine.topology().occurrences().values()) {
                String source=expanded.trace().get(occurrence.sourceElementId()).source();
                if(source.equals("ship"))tasks.put(occurrence.occurrenceId(),new EngineInputs.TaskInput("episode",true,true,true));
                if(source.equals("accepted"))goals.put(occurrence.occurrenceId(),new EngineInputs.GoalInput("episode",true,accepted));
                if(source.equals("complete"))root=occurrence.occurrenceId();
            }
            var result=engine.step(new EngineInputs(goals,tasks,Map.of()));
            assertEquals(Boolean.TRUE.equals(accepted),result.aggregate().goalTaskStatus(root)==GoalTaskStatus.FULFILLED,"Every attempt is complete, actual accepted="+accepted);
            assertTrue(result.originalApiCalls().contains("TaskMarking.update"));
            assertTrue(result.originalApiCalls().contains("GoalModelInstanceTranslator.propagateDependencies"));
        }
    }

}
