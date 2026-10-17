package org.tzi.use.plugins.jacamo.semantics;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import cartago.*;
import cartago.util.agent.CartagoBasicContext;
import org.jacamo.bridge.adapter.*;
import org.jacamo.bridge.contract.*;
import org.junit.jupiter.api.Test;
import org.tzi.use.plugins.jacamo.codegrounded.*;
import org.tzi.use.plugins.jacamo.codegrounded.use.*;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.*;
import org.tzi.use.plugins.jacamo.semantics.mapping.*;
import org.tzi.use.plugins.jacamo.semantics.istar.*;
import org.tzi.use.plugins.jacamo.semantics.observer.*;

class FunctionalLiveBoardTest {
    @Test void inheritedBoardOperationsGiveWaitingEnabledSatisfiedAndExactCommittedDoneAgents()throws Exception {
        var source=FunctionalObservationTest.source();var env=CartagoEnvironment.getInstance();env.init();String actor="flow"+UUID.randomUUID().toString().replace("-","");var context=new CartagoBasicContext(actor);
        var path=Path.of("src/test/resources/semantics/functional/src/org/functional.xml").toAbsolutePath().toString();var ids=new ArrayList<ArtifactId>();
        try(var registration=CslRuntimeTestRegistry.bind(source.semanticContract(),actor,"a")) {
            var organization=context.makeArtifact(context.getJoinedWspId("main"),"organization"+UUID.randomUUID().toString().replace("-",""),"ora4mas.nopl.OrgBoard",new Object[]{path});ids.add(organization);
            var group=context.makeArtifact(context.getJoinedWspId("main"),"team"+UUID.randomUUID().toString().replace("-",""),CslGroupBoard.class.getName(),new Object[]{path,"team"});ids.add(group);context.doAction(group,new Op("adoptRole","worker"));
            var board=context.makeArtifact(context.getJoinedWspId("main"),"service"+UUID.randomUUID().toString().replace("-",""),CslSchemeBoard.class.getName(),new Object[]{path,"service",organization.getName()});ids.add(board);context.doAction(group,new Op("addScheme",board.getName()));
            var observed=new MoiseBoardSnapshotSource("org","live",()->List.of(CslGroupBoard.instance),()->List.of(CslSchemeBoard.instances.get(board.getName())),source.semanticContract(),Path.of("src/test/resources/semantics/functional").toAbsolutePath());
            var initialFacts=new ArrayList<RuntimeFact>(new CartagoSnapshotSource(env,List.of("/main"),"acknowledgement").capture());initialFacts.addAll(observed.capture());var before=cut(observed);assertTrue(before.goals().stream().allMatch(g->g.state()==FunctionalObservation.State.WAITING));
            context.doAction(board,new Op("commitMission","work"));var committed=cut(observed);var g1=committed.definition().goals().stream().filter(g->g.goalId().equals("g1")).findFirst().orElseThrow();var g2=committed.definition().goals().stream().filter(g->g.goalId().equals("g2")).findFirst().orElseThrow();
            assertEquals(FunctionalObservation.State.ENABLED,committed.goals().stream().filter(g->g.definition().equals(g1.metadata().semanticId())).findFirst().orElseThrow().state());assertEquals(FunctionalObservation.State.WAITING,committed.goals().stream().filter(g->g.definition().equals(g2.metadata().semanticId())).findFirst().orElseThrow().state());
            context.doAction(board,new Op("goalAchieved","g1"));var done=cut(observed);var first=done.goals().stream().filter(g->g.definition().equals(g1.metadata().semanticId())).findFirst().orElseThrow();assertEquals(FunctionalObservation.State.SATISFIED,first.state());assertEquals(1,first.committed().size());assertEquals(first.committed(),first.achieved());
            assertEquals(FunctionalObservation.State.ENABLED,done.goals().stream().filter(g->g.definition().equals(g2.metadata().semanticId())).findFirst().orElseThrow().state());
             verifyIntentionalAcknowledgement(source,observed,initialFacts,g1.metadata().semanticId());
            context.doAction(board,new Op("goalAchieved","g2"));assertTrue(cut(observed).goals().stream().allMatch(g->g.state()==FunctionalObservation.State.SATISFIED));
        } finally {for(var id:ids){env.getController("/main").removeArtifact(id.getName());CslSchemeBoard.instances.remove(id.getName());}CslRuntimeTestRegistry.quitCartagoActor(env,context.getName());env.shutdown();}
    }
    private static void verifyIntentionalAcknowledgement(ModelSnapshot source,MoiseBoardSnapshotSource observed,List<RuntimeFact> initialFacts,String sourceGoal)throws Exception {
        var cfg=new OrganizationalMappingRules().derive(source,List.of(),List.of(),List.of(),List.of(),new RequirementModel(List.of()));var role=cfg.schema().classifiers().stream().filter(c->c.kind()==org.tzi.use.plugins.jacamo.semantics.csl.CslSchema.Kind.ROLE && !c.abstractType()).findFirst().orElseThrow();var context=initialFacts.stream().filter(f->f.kind()==RuntimeFactKind.GROUP_BOARD).findFirst().orElseThrow();var actor=new RequirementModel.Binding("actor",source.semanticContract().agentDeclarations().getFirst().metadata().semanticId(),role.id(),context.id().canonical(),"unused","observer:"+FunctionalUseBinding.GOAL,"episode");var node=new MoiseRequirementBinder().bind("board-goal","Acknowledged",RequirementModel.Temporal.NONE,actor,cut(observed),sourceGoal);var config=new org.tzi.use.plugins.jacamo.semantics.profile.CslMonitorConfiguration(cfg.version(),cfg.profile(),cfg.schema(),new RequirementModel(List.of(node)),cfg.decisions());var built=new CodeGroundedNativePipeline().build(source,NativeProjectionMode.AUTO,config);
        try(var projector=new NativeRuntimeProjector(built,"acknowledgement",1,source.modelRevision())) {var currentFacts=new ArrayList<RuntimeFact>(new CartagoSnapshotSource(CartagoEnvironment.getInstance(),List.of("/main"),"acknowledgement").capture());currentFacts.addAll(observed.capture());int seq=0;for(var facts:List.of(initialFacts,currentFacts)){var now=java.time.Instant.EPOCH;projector.applySnapshot(new RuntimeSnapshot("ack-"+seq,source.modelRevision(),now,now,Map.of(),Map.of(),1,facts,Map.of("moise:org",Completeness.PARTIAL,"cartago",Completeness.COMPLETE),"public-board"));var result=projector.coordinator().latest().semantic();var row=result.constraints().stream().filter(r->r.id().equals("REQUIREMENT:board-goal")).findFirst().orElseThrow();assertEquals(FunctionalUseBinding.GOAL,row.contextClass());assertEquals(seq++==0?org.tzi.use.plugins.jacamo.semantics.evidence.SemanticVerdict.FAIL:org.tzi.use.plugins.jacamo.semantics.evidence.SemanticVerdict.PASS,row.verdict(),row.diagnostic());}}
    }
    private static FunctionalObservation cut(MoiseBoardSnapshotSource source) {var fact=source.capture().stream().filter(f->f.kind()==RuntimeFactKind.SCHEME_BOARD).findFirst().orElseThrow();assertEquals(ProjectionStatus.MATERIALIZED_FAITHFULLY,fact.projectionStatus(),fact.values().toString());return FunctionalObservation.decode(fact.id(),fact.values());}
    @Test void realPublicSchemeBoardCutCarriesIndependentArgumentsAndUuidIntoSameNativeState()throws Exception {
        var source=FunctionalObservationTest.source();var env=CartagoEnvironment.getInstance();env.init();var context=new CartagoBasicContext("functional"+UUID.randomUUID().toString().replace("-",""));
        var path=Path.of("src/test/resources/semantics/functional/src/org/functional.xml").toAbsolutePath().toString();var ids=new ArrayList<ArtifactId>();
        try {
            for(int i=0;i<2;i++) {var board=context.makeArtifact(context.getJoinedWspId("main"),"scheme"+UUID.randomUUID().toString().replace("-",""),CslSchemeBoard.class.getName(),new Object[]{path,"service"});ids.add(board);context.doAction(board,new Op("setArgumentValue","g1","item",i==0?"alpha":"beta"));}
            var observed=new MoiseBoardSnapshotSource("org","live",List::of,()->ids.stream().map(i->CslSchemeBoard.instances.get(i.getName())).toList(),source.semanticContract(),Path.of("src/test/resources/semantics/functional").toAbsolutePath());
            var facts=observed.capture().stream().filter(f->f.kind()==RuntimeFactKind.SCHEME_BOARD).toList();assertEquals(2,facts.size());
            var cfg=new OrganizationalMappingRules().derive(source,List.of(),List.of(),List.of(),List.of(),new RequirementModel(List.of()));var pipeline=new CodeGroundedNativePipeline().build(source,NativeProjectionMode.AUTO,cfg);
            try(var projector=new NativeRuntimeProjector(pipeline,"live",1,source.modelRevision())) {
                var now=java.time.Instant.now();projector.applySnapshot(new RuntimeSnapshot("live-board-cut",source.modelRevision(),now,now,Map.of("moise:org",new SourceWatermark("moise:org",0)),Map.of("moise:org",new SourceWatermark("moise:org",0)),1,facts,Map.of("moise:org",Completeness.PARTIAL),"PUBLIC_BOARD_POLL"));
                for(var fact:facts) {assertEquals(ProjectionStatus.MATERIALIZED_FAITHFULLY,fact.projectionStatus(),fact.values().toString());var cut=FunctionalObservation.decode(fact.id(),fact.values());assertTrue(ids.stream().anyMatch(i->i.getId().toString().equals(cut.artifactUuid())));var scheme=projector.mutations().objectForSemanticId(cut.schemeIdentity());assertNotNull(scheme);assertEquals(FunctionalUseBinding.SCHEME,scheme.cls().name());for(var goal:cut.goals())assertNotNull(projector.mutations().objectForSemanticId(FunctionalObservation.goalIdentity(cut.schemeIdentity(),goal.definition(),goal.arguments())));}
                assertEquals(2,projector.system().state().objectsOfClass(projector.system().model().getClass(FunctionalUseBinding.SCHEME)).size());assertEquals("PARTIAL",projector.coordinator().latest().semantic().coverage().get("moise:org"));
            }
        } finally {for(var id:ids){env.getController("/main").removeArtifact(id.getName());CslSchemeBoard.instances.remove(id.getName());}}
    }
}
