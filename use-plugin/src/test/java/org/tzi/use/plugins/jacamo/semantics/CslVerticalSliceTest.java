package org.tzi.use.plugins.jacamo.semantics;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import cartago.*;
import cartago.util.agent.CartagoBasicContext;
import org.jacamo.bridge.adapter.*;
import org.jacamo.bridge.contract.*;
import org.jacamo.bridge.contract.semantic.*;
import org.junit.jupiter.api.Test;
import org.tzi.use.plugins.jacamo.codegrounded.*;
import org.tzi.use.plugins.jacamo.codegrounded.use.*;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.*;
import org.tzi.use.plugins.jacamo.semantics.csl.*;
import org.tzi.use.plugins.jacamo.semantics.evidence.*;
import org.tzi.use.plugins.jacamo.semantics.istar.*;
import org.tzi.use.plugins.jacamo.semantics.profile.*;

class CslVerticalSliceTest {
    private final AtomicReference<Throwable> failure=new AtomicReference<>();
    @Test void realArtifactAndAcceptedGroupBoardFeedSameSystemInvariantTransitionAchieveAndReplay() throws Exception {runVertical(false);}
    @Test void acceptedRuntimeEnactmentRetainsAnAuthoredPerContextCardinalityViolation() throws Exception {runVertical(true);}
    private void runVertical(boolean structural) throws Exception {
        Path fixture=Path.of("src/test/resources/semantics/profile-v1/monitor.jcm").toAbsolutePath();
        var base=new OfficialProjectAdapter().adapt(new OfficialProjectLoader().load(fixture),fixture);
        var environment=CartagoEnvironment.getInstance();environment.init();
        String suffix=UUID.randomUUID().toString().replace("-","");String artifactName="counter"+suffix,boardName="team"+suffix,agentName="a"+suffix;
        var context=new CartagoBasicContext(agentName);
        var artifact=context.makeArtifact(context.getJoinedWspId("main"),artifactName,CslCounterArtifact.class.getName());
        var board=context.makeArtifact(context.getJoinedWspId("main"),boardName,CslGroupBoard.class.getName(),new Object[]{fixture.getParent().resolve("src/org/monitor.xml").toString(),"team"});
        context.doAction(board,new Op("adoptRole","worker"));
        assertEquals(1,CslGroupBoard.instance.getGrpState().getPlayers().size());
        String revisionBefore=System.getProperty("jacamo.bridge.modelRevision"),generationBefore=System.getProperty("jacamo.bridge.generation");
        System.setProperty("jacamo.bridge.generation","1");
        var observedEvents=new CopyOnWriteArrayList<RuntimeEvent>();var target=new AtomicReference<NativeRuntimeProjector>();
        try(var registration=CslRuntimeTestRegistry.bind(base.semanticContract(),agentName,"a")) {
            var cartago=new CartagoSnapshotSource(environment,List.of("/main"),"csl-session");
            var moise=new MoiseBoardSnapshotSource("org","csl-session",()->List.of(CslGroupBoard.instance),List::of,base.semanticContract(),fixture.getParent());
            var official=new OfficialCartagoAdapter().capture(environment);var source=base.semanticContract();
            var semantic=new JacamoSemanticSnapshot(source.contractVersion(),source.project(),source.agentDeclarations(),source.workspaceDeclarations(),source.artifactDeclarations(),
                source.organizationDeployments(),source.groupDeployments(),source.schemeDeployments(),source.institutionDeployments(),source.rawRoleTuples(),source.rawFocusTuples(),
                source.importProvenance(),source.jasonPrograms(),List.of(official),source.moiseOrganizations(),source.exactBindings(),source.diagnostics());
            var model=new ModelSnapshot(base.modelRevision(),base.sources(),base.agentDeclarations(),base.workspaces(),base.configuredArtifacts(),base.organisationFacts(),base.groupRoleCardinalities(),
                base.parentSubGroupCardinalities(),base.crossDimensionalRelations(),base.unresolvedFacts(),base.projectionProvenance(),semantic);
            System.setProperty("jacamo.bridge.modelRevision",model.modelRevision());
            var groupFact=moise.capture().stream().filter(f->f.kind()==RuntimeFactKind.GROUP_BOARD).findFirst().orElseThrow();
            assertEquals(ProjectionStatus.MATERIALIZED_FAITHFULLY,groupFact.projectionStatus(),groupFact.values().toString());
            var os=source.moiseOrganizations().getFirst();var role=os.structuralSpecification().roles().stream().filter(r->r.roleId().equals("worker")).findFirst().orElseThrow();
            String artifactId=official.artifacts().stream().filter(a->a.uuid().equals(artifact.getId().toString())).findFirst().orElseThrow().metadata().semanticId();
            var schema=new CslSchema("g1",List.of(
                new CslSchema.Classifier("context",(String)groupFact.values().get("groupSpecSemanticId"),"C",CslSchema.Kind.ORG_CONTEXT,"",false,List.of()),
                new CslSchema.Classifier("role",role.metadata().semanticId(),"R",CslSchema.Kind.ROLE,"",false,List.of()),
                new CslSchema.Classifier("domain",DomainProjection.artifactId(CslCounterArtifact.class.getName()),"X",CslSchema.Kind.ENTITY,"",false,List.of(new CslSchema.Attribute("p","p","Integer","p",false)))),
                List.of(),List.of(new CslSchema.Invariant("nonnegative","domain","Nonnegative","self.p >= 0")),
                List.of(new CslSchema.OperationContract("increment","domain",List.of(CslCounterArtifact.class.getName(),"increment",List.of(),"void"),"self.p >= 0","self.p = self.p@pre + 1"),
                    new CslSchema.OperationContract("incrementTwo","domain",List.of(CslCounterArtifact.class.getName(),"incrementTwo",List.of(),"void"),"self.p >= 0","self.p = self.p@pre + 1")));
            if(structural)schema=new CslSchema(schema.id(),schema.classifiers(),schema.associations(),schema.invariants(),schema.contracts(),
                new CslStructuralPolicy(List.of(),List.of(new CslStructuralPolicy.RoleCardinality("no-workers","context","role",0,0)),List.of(),false));
            var binding=new RequirementModel.Binding("worker-view",source.agentDeclarations().getFirst().metadata().semanticId(),"role",groupFact.id().canonical(),artifactId,"domain","g1");
            var requirements=new RequirementModel(List.of(new RequirementModel.Node("ready","XReady",RequirementModel.Kind.GOAL,RequirementModel.Temporal.ACHIEVE,binding,"self.p >= 1","true",RequirementModel.Refinement.NONE,List.of(),"","")));
            var pipeline=new CodeGroundedNativePipeline().build(model,NativeProjectionMode.AUTO,new CslMonitorConfiguration("1.0",structural?SemanticProjectionProfile.CSL_OBSERVER_V2:SemanticProjectionProfile.CSL_OBSERVER_V1,schema,requirements,List.of()));
            try(var projector=new NativeRuntimeProjector(pipeline,"csl-session",1,model.modelRevision());var captures=new SnapshotCoordinator(List.of(cartago,moise),8192,event->{
                observedEvents.add(event);var p=target.get();if(p!=null)try{p.apply(event);}catch(Throwable e){failure.compareAndSet(null,new IllegalStateException("Official event "+event.kind()+":"+event.after(),e));}
            })) {
                target.set(projector);projector.applySnapshot(captures.capture(model.modelRevision(),3).snapshot());
                var system=projector.system();var x=projector.mutations().objectForSemanticId(artifactId);assertNotNull(x);assertEquals("X",x.cls().name());
                assertEquals("Integer",x.cls().attribute("p",true).type().toString());assertEquals("0",x.state(system.state()).attributeValue("p").toString());
                assertEquals(2,system.state().allLinks().stream().filter(l->Set.of("Plays","InContext").contains(l.association().name())).count());
                assertEquals(SemanticVerdict.PASS,invariant(projector));assertEquals(IntentionalMarking.Status.UNDETERMINED,mark(projector));
                assertEquals("PARTIAL",projector.coordinator().latest().semantic().coverage().get("moise:org"));
                if(structural)assertTrue(projector.coordinator().latest().semantic().constraints().stream().anyMatch(r->r.id().contains("CslPolicy_") && r.verdict()==SemanticVerdict.FAIL),"Faithfully observed accepted role must remain as a cardinality FAIL, not roll back");
                context.doAction(artifact,new Op("increment"));
                await(()->projector.coordinator().latest().semantic().constraints().stream().anyMatch(r->r.kind().equals("POST") && r.id().equals("increment:post")));
                assertNull(failure.get(),()->String.valueOf(failure.get()));
                assertEquals(SemanticVerdict.PASS,projector.coordinator().latest().semantic().constraints().stream().filter(r->r.id().equals("increment:post")).findFirst().orElseThrow().verdict());
                assertEquals(IntentionalMarking.Status.SATISFIED,mark(projector));assertSame(x,projector.mutations().objectForSemanticId(artifactId));
                assertEquals("1",x.state(system.state()).attributeValue("p").toString());
                context.doAction(artifact,new Op("set",-1));await(()->invariant(projector)==SemanticVerdict.FAIL);
                assertEquals(IntentionalMarking.Status.SATISFIED,mark(projector));assertEquals("-1",x.state(system.state()).attributeValue("p").toString());
                projector.applySnapshot(captures.capture(model.modelRevision(),3).snapshot());assertSame(x,projector.mutations().objectForSemanticId(artifactId));assertEquals(IntentionalMarking.Status.SATISFIED,mark(projector));
                context.doAction(artifact,new Op("set",0));await(()->x.state(system.state()).attributeValue("p").toString().equals("0"));
                context.doAction(artifact,new Op("incrementTwo"));await(()->projector.coordinator().latest().semantic().constraints().stream().anyMatch(r->r.id().equals("incrementTwo:post")));
                var wrong=projector.coordinator().latest().semantic().constraints().stream().filter(r->r.id().equals("incrementTwo:post")).findFirst().orElseThrow();
                assertEquals(SemanticVerdict.FAIL,wrong.verdict());assertEquals("0",wrong.witness().get("before.p"));assertEquals("2",wrong.witness().get("after.p"));
                var full=captures.capture(model.modelRevision(),3).snapshot();
                var partial=new RuntimeSnapshot("partial",full.modelRevision(),full.captureStartedAt(),full.captureEndedAt(),full.startWatermarks(),full.endWatermarks(),1,List.of(),Map.of("cartago",Completeness.PARTIAL,"moise:org",Completeness.PARTIAL),"partial-observation");
                projector.applySnapshot(partial);assertSame(x,projector.mutations().objectForSemanticId(artifactId));assertEquals(SemanticVerdict.SKIPPED,invariant(projector));
                assertEquals(IntentionalMarking.Status.UNDETERMINED,mark(projector));
                projector.applySnapshot(full);assertSame(x,projector.mutations().objectForSemanticId(artifactId));assertEquals(IntentionalMarking.Status.SATISFIED,mark(projector));
                Path continuous=Files.createTempDirectory(Path.of("target"),"csl-continuous-replay-");
                new NativeRuntimeReplay().exportBundle(projector,pipeline.export().useText(),continuous);
                var completeReplay=new NativeRuntimeReplay().replay(continuous);assertTrue(completeReplay.complete(),completeReplay.diagnostics().toString());
                assertReplayEndpoint(continuous,completeReplay);
                var corrupted=full.facts().stream().map(f->{
                    if(f.kind()!=RuntimeFactKind.PROPERTY || !artifactId.equals(f.values().get("artifactSemanticId")))return f;
                    var values=new HashMap<>(f.values());values.put("values",List.of("wrong"));values.put("valueTypes",List.of("java.lang.String"));
                    return new RuntimeFact(f.id(),f.kind(),values,f.relations(),f.projectionStatus(),f.completeness(),f.evidence());
                }).toList();
                var invalid=new RuntimeSnapshot("invalid",full.modelRevision(),full.captureStartedAt(),full.captureEndedAt(),full.startWatermarks(),full.endWatermarks(),1,corrupted,full.sourceCompleteness(),"malformed-property");
                String validHash=projector.coordinator().latest().stateHash();assertThrows(NativeRuntimeProtocolException.class,()->projector.applySnapshot(invalid));
                assertSame(x,projector.mutations().objectForSemanticId(artifactId));assertEquals(validHash,projector.coordinator().latest().stateHash());
                projector.applySnapshot(full);assertEquals(IntentionalMarking.Status.SATISFIED,mark(projector));
                projector.coordinator().coverageGap("TEST_MISSED_PROPERTY_LIFETIME_INTERVAL");target.set(null);
                context.doAction(artifact,new Op("remove"));context.doAction(artifact,new Op("add",7));
                projector.applySnapshot(captures.capture(model.modelRevision(),3).snapshot());target.set(projector);
                assertSame(x,projector.mutations().objectForSemanticId(artifactId));assertEquals("7",x.state(system.state()).attributeValue("p").toString());
                context.doAction(new Op("disposeArtifact",artifact),context.getJoinedWspId("main"));await(()->projector.mutations().objectForSemanticId(artifactId)==null);
                var replacement=context.makeArtifact(context.getJoinedWspId("main"),artifactName,CslCounterArtifact.class.getName());
                await(()->system.state().allObjects().stream().anyMatch(o->replacement.getId().toString().equals(projector.mutations().metadata(o,"uuid"))));
                var x2=system.state().allObjects().stream().filter(o->replacement.getId().toString().equals(projector.mutations().metadata(o,"uuid"))).findFirst().orElseThrow();
                assertNotSame(x,x2);assertNotEquals(x.name(),x2.name());assertEquals("0",x2.state(system.state()).attributeValue("p").toString());
                assertEquals(IntentionalMarking.Status.UNDETERMINED,mark(projector),"Old source binding cannot transfer its achievement to a replacement artifact");
                var oldContext=projector.mutations().objectForSemanticId(groupFact.id().canonical());
                 String oldRole=system.state().allObjects().stream().filter(o->groupFact.id().canonical().equals(projector.mutations().metadata(o,"roleContext"))).map(o->projector.mutations().metadata(o,"semanticId")).findFirst().orElseThrow();
                 context.doAction(new Op("disposeArtifact",board),context.getJoinedWspId("main"));
                  await(()->projector.mutations().objectForSemanticId(groupFact.id().canonical())==null && projector.mutations().objectForSemanticId(oldRole)==null);
                 var nextBoard=context.makeArtifact(context.getJoinedWspId("main"),boardName,CslGroupBoard.class.getName(),new Object[]{fixture.getParent().resolve("src/org/monitor.xml").toString(),"team"});context.doAction(nextBoard,new Op("adoptRole","worker"));
                 var newGroupFact=moise.capture().stream().filter(f->f.kind()==RuntimeFactKind.GROUP_BOARD).findFirst().orElseThrow();assertNotEquals(groupFact.id(),newGroupFact.id());assertEquals(nextBoard.getId().toString(),newGroupFact.values().get("boardArtifactIdentity"));
                 projector.applySnapshot(captures.capture(model.modelRevision(),3).snapshot());assertNull(projector.mutations().objectForSemanticId(groupFact.id().canonical()));assertNull(projector.mutations().objectForSemanticId(oldRole));var nextContext=projector.mutations().objectForSemanticId(newGroupFact.id().canonical());assertNotNull(nextContext);assertNotSame(oldContext,nextContext);assertNotEquals(oldContext.name(),nextContext.name());
                 assertTrue(projector.coordinator().latest().semantic().decisions().stream().anyMatch(d->d.contains("CONTINUITY_UNPROVEN")));
                 assertEquals(projector.coordinator().latest(),RuntimeVerificationResult.fromMap(projector.coordinator().latest().toMap()));
                Path output=Files.createTempDirectory(Path.of("target"),"csl-replay-");new NativeRuntimeReplay().exportBundle(projector,pipeline.export().useText(),output);
                var replay=new NativeRuntimeReplay().replay(output);
                assertFalse(replay.complete(),"A known missed interval cannot be certified as a continuous trace");
                assertEquals(List.of("REPLAY_PARTIAL_COVERAGE_GAP"),replay.diagnostics());
                assertReplayEndpoint(output,replay);
                assertSame(system,projector.system());assertNull(failure.get(),()->String.valueOf(failure.get()));
            }
        } finally {
            environment.getController("/main").removeArtifact(artifactName);environment.getController("/main").removeArtifact(boardName);
            restore("jacamo.bridge.modelRevision",revisionBefore);restore("jacamo.bridge.generation",generationBefore);CslRuntimeTestRegistry.quitCartagoActor(environment,agentName);environment.shutdown();
        }
    }
    static void assertReplayEndpoint(Path directory,NativeRuntimeReplay.ReplayReport replay) throws Exception {
        var manifest=CanonicalJson.object(CanonicalJson.decode(Files.readAllBytes(directory.resolve("manifest.json"))));
        assertEquals(manifest.get("finalStateHash"),replay.finalStateHash());assertEquals(manifest.get("finalResultHash"),replay.finalResultHash());
        assertEquals(((Number)manifest.get("entries")).intValue(),replay.checkedEntries());
    }
    static SemanticVerdict invariant(NativeRuntimeProjector p){return p.coordinator().latest().semantic().constraints().stream().filter(r->r.kind().equals("INVARIANT") && r.id().contains("Nonnegative")).findFirst().orElseThrow().verdict();}
    static IntentionalMarking.Status mark(NativeRuntimeProjector p){return p.coordinator().latest().semantic().requirements().get("ready").evaluated();}
    void await(java.util.function.BooleanSupplier predicate) throws Exception {long end=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(10);while(!predicate.getAsBoolean() && System.nanoTime()<end){if(failure.get()!=null)throw new AssertionError("Official event projection failed",failure.get());Thread.sleep(10);}if(failure.get()!=null)throw new AssertionError("Official event projection failed",failure.get());assertTrue(predicate.getAsBoolean(),"Official callback timed out");}
    static void restore(String key,String old){if(old==null)System.clearProperty(key);else System.setProperty(key,old);}
}
