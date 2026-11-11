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
        for(var quantifier:List.of(OccurrenceGoalModelExpander.Quantifier.ALL,OccurrenceGoalModelExpander.Quantifier.ANY)) {
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

    @Test void agentCapabilitiesDelegateOnlyToTheSamePhysicalPlayerAndKeepOriginalEngine() {
        var model=org.vnu.sme.goal.dsl.istar.parser.IStarCompiler.compile("""
          istar Delivery {
            role Courier { goal delivered : Achieve }
            agent Skills {
              Skills participates-in Courier
              goal capable : Achieve
              task act > capable pre {[ true ]} post {[ true ]}
            }
            depend Courier.delivered -> goal execution -> Skills.capable
          }
          """,EngineSemanticsProfile.ROLE_SPECIALIZATION_V1.validationPolicy()).model();
        var members=new ArrayList<OccurrenceGoalModelExpander.Player>();
        for(String player:List.of("A","B")) {
            members.add(new OccurrenceGoalModelExpander.Player("Courier","Courier_"+player,"role"+player,player,"role"+player,"interval"+player));
            members.add(new OccurrenceGoalModelExpander.Player("Skills","Skills_"+player,"agent"+player,player,"",""));
        }
        var expander=new OccurrenceGoalModelExpander(model,EngineSemanticsProfile.ROLE_SPECIALIZATION_V1,Map.of("execution",OccurrenceGoalModelExpander.Quantifier.SAME_PLAYER));
        var expanded=expander.expand(members,"DeliveryRun");
        assertEquals(2,expanded.model().getDependencies().size());
        for(var dependency:expanded.model().getDependencies())assertEquals(expanded.trace().get(dependency.dependerElmt()).player(),expanded.trace().get(dependency.dependeeElmt()).player());
        for(var actor:expanded.model().getActors())for(var association:actor.associations())assertEquals(actor.name().substring(actor.name().length()-1),association.target().substring(association.target().length()-1));
        try(var engine=new Use4GoalRuntimeSessionAdapter(expanded.model(),expanded.cast(),(a,x,b,y)->true,"delivery",EngineSemanticsProfile.ROLE_SPECIALIZATION_V1,EngineTopologyPolicy.SINGLE_OWNER_CONTEXT_V1)) {
            var tasks=new TreeMap<String,EngineInputs.TaskInput>();
            for(var occurrence:engine.topology().occurrences().values()) {
                var trace=expanded.trace().get(occurrence.sourceElementId());
                if(trace.source().equals("act"))tasks.put(occurrence.occurrenceId(),new EngineInputs.TaskInput("episode:"+trace.player(),true,true,trace.player().equals("A")));
            }
            var result=engine.step(new EngineInputs(Map.of(),tasks,Map.of()));
            assertTrue(result.originalApiCalls().contains("TaskMarking.update"));
            for(var occurrence:engine.topology().occurrences().values()) {
                var trace=expanded.trace().get(occurrence.sourceElementId());
                if(trace.source().equals("delivered"))assertEquals(trace.player().equals("A"),result.aggregate().goalTaskStatus(occurrence.occurrenceId())==GoalTaskStatus.FULFILLED);
            }
        }
        var missing=new ArrayList<>(members);missing.removeIf(p->p.sourceActor().equals("Skills")&&p.player().equals("B"));
        assertThrows(IllegalArgumentException.class,()->expander.expand(missing,"MissingPlayer"));
    }
    @Test void sourceBootstrapDoesNotInstantiateAnActorBeforeItsSourceExists() {
        var expander=new OccurrenceGoalModelExpander(source(),EngineSemanticsProfile.DERIVED_AND_RESOURCE_V1,Map.of("shipment",OccurrenceGoalModelExpander.Quantifier.ANY));
        var providers=cast().stream().filter(p->p.sourceActor().equals("Carrier")).toList();
        assertThrows(IllegalArgumentException.class,()->expander.expand(providers,"Unqualified"));
        var initial=expander.expand(providers,"SourceBootstrap",Set.of("ship"),true);
        assertEquals(2,initial.cast().size());assertTrue(initial.model().getDependencies().isEmpty());
        assertTrue(initial.trace().values().stream().noneMatch(t->t.actor().equals("Dispatch")));
        assertEquals(2,expander.expand(cast(),"BoundDomain").model().getDependencies().size());
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
    @Test void scopedExpansionIsolatesInitialGoalSetupConcurrentRequestsFailureBeforeCreationAndPartialSetup() {
        var profile=EngineSemanticsProfile.ROLE_SPECIALIZATION_V1;
        var model=org.vnu.sme.goal.dsl.istar.parser.IStarCompiler.compile("""
          istar AuctionGoals {
            agent Auction {
              goal O_root : Achieve
              goal O_start : Achieve > O_root
              goal O_decide : Achieve > O_root
            }
            role auction_capabilities {
              auction_capabilities participates-in Auctioneer
              goal do_auction : Achieve
              task createScheme > do_auction pre {[ true ]} post {[ true ]}
              task commitMission > do_auction pre {[ true ]} post {[ true ]}
              resource SchemeBoard > needed-by commitMission > needed-by A_start_t1
              goal C_start : Achieve
              task A_start_t1 > C_start pre {[ true ]} post {[ true ]}
              task A_start_t2 > C_start pre {[ true ]} post {[ true ]}
              goal C_decide : Achieve
              task A_decide_t2 > C_decide pre {[ true ]} post {[ true ]}
              resource AuctionArtifact > needed-by A_start_t2 > needed-by A_decide_t2
            }
            role Auctioneer {
              goal A_start : Achieve
              goal A_decide : Achieve
            }
            depend Auction.O_start -> goal D_start -> Auctioneer.A_start
            depend Auction.O_decide -> goal D_decide -> Auctioneer.A_decide
          }
          """,profile.validationPolicy()).model();
        var views=Map.of(
            "A_start",new org.tzi.use.plugins.jacamo.semantics.runtime.GoalViewRealizations.View("A_start","C_start","start(Id)","auctioneer","doAuction","Same-player view"),
            "A_decide",new org.tzi.use.plugins.jacamo.semantics.runtime.GoalViewRealizations.View("A_decide","C_decide","decide","auctioneer","doAuction","Same-player view"));
        var expander=new OccurrenceGoalModelExpander(model,profile,Map.of(),views,Set.of("auction_capabilities"));
        var setupMembers=List.of(
            new OccurrenceGoalModelExpander.Player("auction_capabilities","cap_bob","inst_bob","bob","","interval_bob"),
            new OccurrenceGoalModelExpander.Player("auction_capabilities","cap_alice","inst_alice","alice","","interval_alice"));
        var setupA1=expander.expand(setupMembers,"Setup_a1",Set.of("createScheme","commitMission"));
        var setupA2=expander.expand(setupMembers,"Setup_a2",Set.of("createScheme","commitMission"));
        assertTrue(setupA1.trace().values().stream().map(OccurrenceGoalModelExpander.Trace::source).collect(java.util.stream.Collectors.toSet()).containsAll(Set.of("do_auction","createScheme","commitMission","SchemeBoard")));
        assertTrue(setupA1.trace().values().stream().noneMatch(t->Set.of("O_root","C_start","A_start_t1","AuctionArtifact").contains(t.source())));
        // 1. Failure before creation on setupA1 (bob fails createScheme, alice is unassigned)
        try(var engineA1=new Use4GoalRuntimeSessionAdapter(setupA1.model(),setupA1.cast(),(a,x,b,y)->true,"setup-a1",profile,EngineTopologyPolicy.SINGLE_OWNER_CONTEXT_V1);
            var engineA2=new Use4GoalRuntimeSessionAdapter(setupA2.model(),setupA2.cast(),(a,x,b,y)->true,"setup-a2",profile,EngineTopologyPolicy.SINGLE_OWNER_CONTEXT_V1)) {
            var tasksA1=new TreeMap<String,EngineInputs.TaskInput>();
            for(var occ:engineA1.topology().occurrences().values()) {
                var trace=setupA1.trace().get(occ.sourceElementId());
                if(trace.source().equals("createScheme"))tasksA1.put(occ.occurrenceId(),new EngineInputs.TaskInput("ep-a1-"+trace.player(),trace.player().equals("bob"),trace.player().equals("bob")?true:null,trace.player().equals("bob")?false:null));
                if(trace.source().equals("commitMission"))tasksA1.put(occ.occurrenceId(),new EngineInputs.TaskInput("ep-a1-cm-"+trace.player(),trace.player().equals("bob"),null,null));
            }
            var resA1=engineA1.step(new EngineInputs(Map.of(),tasksA1,Map.of(),Map.of()));
            for(var occ:engineA1.topology().occurrences().values()) {
                var trace=setupA1.trace().get(occ.sourceElementId());
                if(trace.player().equals("alice"))assertEquals(GoalTaskStatus.UNKNOWN,resA1.aggregate().goalTaskStatus(occ.occurrenceId()),"Unassigned player stays UNKNOWN");
                if(trace.player().equals("bob")&&trace.source().equals("createScheme")) {
                    assertTrue(resA1.history().tasks().get(occ.occurrenceId()).preSeen());
                    assertFalse(resA1.history().tasks().get(occ.occurrenceId()).postSeen());
                    assertNotEquals(GoalTaskStatus.FULFILLED,resA1.aggregate().goalTaskStatus(occ.occurrenceId()));
                }
                if(trace.player().equals("bob")&&trace.source().equals("commitMission")) {
                    assertEquals(GoalTaskStatus.UNKNOWN,resA1.history().tasks().get(occ.occurrenceId()).status());
                    assertEquals(GoalTaskStatus.UNKNOWN,resA1.aggregate().goalTaskStatus(occ.occurrenceId()));
                }
                if(trace.player().equals("bob")&&trace.source().equals("do_auction"))assertNotEquals(GoalTaskStatus.FULFILLED,resA1.aggregate().goalTaskStatus(occ.occurrenceId()));
            }
            // 2. Concurrent request a2 on same agent bob: partial setup (createScheme succeeds, SchemeBoard bound, commitMission pending) then complete
            var tasksA2Step1=new TreeMap<String,EngineInputs.TaskInput>();var resMapA2=new HashMap<EngineInputs.ResourceKey,EngineInputs.ResourceInput>();
            for(var occ:engineA2.topology().occurrences().values()) {
                var trace=setupA2.trace().get(occ.sourceElementId());
                if(trace.source().equals("createScheme"))tasksA2Step1.put(occ.occurrenceId(),new EngineInputs.TaskInput("ep-a2-"+trace.player(),trace.player().equals("bob"),trace.player().equals("bob")?true:null,trace.player().equals("bob")?true:null));
                if(trace.source().equals("commitMission")) {
                    tasksA2Step1.put(occ.occurrenceId(),new EngineInputs.TaskInput("ep-a2-cm-"+trace.player(),trace.player().equals("bob"),null,null));
                    setupA2.model().getActors().stream().flatMap(a->a.neededBys().stream()).filter(n->n.task().equals(occ.sourceElementId())).forEach(n->resMapA2.put(new EngineInputs.ResourceKey(occ.occurrenceId(),n.resource()),new EngineInputs.ResourceInput("ep-a2-cm-"+trace.player(),true,"moise://aorg")));
                }
            }
            var partialA2=engineA2.step(new EngineInputs(Map.of(),tasksA2Step1,Map.of(),resMapA2));
            for(var occ:engineA2.topology().occurrences().values()) {
                var trace=setupA2.trace().get(occ.sourceElementId());
                if(trace.player().equals("bob")&&trace.source().equals("createScheme"))assertEquals(GoalTaskStatus.FULFILLED,partialA2.aggregate().goalTaskStatus(occ.occurrenceId()));
                if(trace.player().equals("bob")&&trace.source().equals("commitMission")) {
                    assertEquals(GoalTaskStatus.UNKNOWN,partialA2.history().tasks().get(occ.occurrenceId()).status());
                    assertEquals(GoalTaskStatus.UNKNOWN,partialA2.aggregate().goalTaskStatus(occ.occurrenceId()));
                }
                if(trace.player().equals("bob")&&trace.source().equals("do_auction"))assertEquals(GoalTaskStatus.UNKNOWN,partialA2.aggregate().goalTaskStatus(occ.occurrenceId()));
            }
            // Now commitMission succeeds in a2
            var tasksA2Step2=new TreeMap<String,EngineInputs.TaskInput>(tasksA2Step1);
            for(var occ:engineA2.topology().occurrences().values()) {
                var trace=setupA2.trace().get(occ.sourceElementId());
                if(trace.player().equals("bob")&&trace.source().equals("commitMission"))tasksA2Step2.put(occ.occurrenceId(),new EngineInputs.TaskInput("ep-a2-cm-bob",true,true,true));
            }
            var fullA2=engineA2.step(new EngineInputs(Map.of(),tasksA2Step2,Map.of(),resMapA2));
            for(var occ:engineA2.topology().occurrences().values()) {
                var trace=setupA2.trace().get(occ.sourceElementId());
                if(trace.player().equals("bob")&&Set.of("createScheme","commitMission","do_auction").contains(trace.source()))assertEquals(GoalTaskStatus.FULFILLED,fullA2.aggregate().goalTaskStatus(occ.occurrenceId()));
            }
        }
    }

}
