package org.tzi.use.plugins.jacamo;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.jacamo.bridge.contract.CanonicalJson;
import org.jacamo.bridge.contract.RuntimeControlContract.State;
import org.junit.jupiter.api.Test;
import org.tzi.use.main.Session;
import org.tzi.use.plugins.jacamo.codegrounded.constraint.RuntimeConstraintPolicy;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.*;
import org.tzi.use.plugins.jacamo.verification.ConstraintOrigin;
import org.tzi.use.plugins.jacamo.verification.VerificationOutcome;

/** Real separate producer, authenticated control and the active native USE Session. */
class AuctionPauseResumeIT {
    @Test void approvedFalseFreezesCutThenAllFiveAgentsAckAndResumeResynchronizes() throws Exception {
        Path evidence=Path.of("target/runtime-control-acceptance","auction-"+System.currentTimeMillis()).toAbsolutePath();
        try(var producer=new ManagedProducerTestSupport(Path.of("../../jacamo/examples/auction/auction.jcm"),evidence)) {
            producer.configureWorkflow();var session=new Session();
            var recording=new HelloWorldSemanticInventoryIT.RecordingTransportFactory(evidence);
            try(var facade=new DefaultJaCaMoFacade(Path.of("."),SemanticAuthority.BRIDGE,()->producer.configuration,
                    recording,PipelineMode.CODE_GROUNDED_NATIVE,session)) {
                producer.clearWorkflowProperties();facade.configureBridge(producer.configuration);facade.importProject(producer.jcm);
                var system=session.system();
                try(var diagramAudit=new org.tzi.use.plugins.jacamo.codegrounded.ObjectDiagramLifecycleEvidence(facade,session,evidence)) {
                diagramAudit.capture("01-model-ready");
                Path pass=evidence.resolve("pass.ocl");Files.writeString(pass,"context Agent inv Identity: Agent.allInstances()->isUnique(semanticId)\n");
                facade.loadVerificationProfile(pass);facade.startRuntime();
                diagramAudit.capture("02-start-ack");
                await(()->facade.runtimeControlState()!=null && facade.runtimeControlState().capable(),40,facade);
                await(()->facade.goalView().schemes().stream().filter(GoalViewSnapshot.Scheme::runtime).count()==2,60,facade);
                diagramAudit.capture("03-live-schemes");
                await(()->facade.goalView().schemes().stream().filter(GoalViewSnapshot.Scheme::runtime).flatMap(s->s.goals().stream())
                        .anyMatch(g->g.id().equals("decide") && g.state().equals("SATISFIED")),60,facade);
                facade.resyncRuntime();
                diagramAudit.capture("04-completed-resync");
                diagramAudit.assertSourceRelations(recording.runtime,"04-completed-resync");
                diagramAudit.recordAttributeObservations("AuctionArtifact","running","true","false");
                var state=system.state();
                assertSame(system,session.system());assertSame(state,system.state());
                var happy=facade.runFullVerification();
                var identity=happy.results().stream().filter(o->o.constraintId().equals("EXTERNAL:Agent::Identity")).findFirst().orElseThrow();
                var diagnostic=Map.of("runtime",facade.runtimeStatus().toString(),"authority",facade.authorityStatus().toString(),
                        "latest",facade.runtimeVerificationResult().toMap(),"historyDiagnostic",facade.runtimeHistoryTail().diagnostic(),
                        "first",facade.runtimeHistoryPage(0,128).entries().stream().filter(e->!e.result().diagnostic().isBlank())
                            .map(e->Map.of("kind",e.kind(),"result",e.result().toMap())).toList(),
                        "tail",facade.runtimeHistoryTail().entries().stream().filter(e->!e.result().diagnostic().isBlank())
                            .map(e->Map.of("kind",e.kind(),"result",e.result().toMap())).toList());
                Files.write(evidence.resolve("happy-diagnostic.json"),CanonicalJson.encode(diagnostic));
                assertEquals(VerificationOutcome.PASS,identity.outcome(),identity+" / "+diagnostic.get("historyDiagnostic")+" / "+facade.authorityStatus());
                assertTrue(happy.results().stream().noneMatch(o->o.outcome()==VerificationOutcome.FAIL || o.outcome()==VerificationOutcome.ERROR),happy.results().toString());
                var selectedScheme=facade.goalView().schemes().stream().filter(GoalViewSnapshot.Scheme::runtime)
                        .filter(s->s.goals().stream().anyMatch(g->g.id().equals("decide") && g.state().equals("SATISFIED"))).findFirst().orElseThrow();
                var selectedGoal=selectedScheme.goals().stream().filter(g->g.id().equals("decide")).findFirst().orElseThrow();
                var root=selectedScheme.goals().stream().filter(g->g.id().equals("auction")).findFirst().orElseThrow();
                String argumentId=org.tzi.use.plugins.jacamo.codegrounded.StepReplayProof.onEdt(()->{
                    var arguments=(org.tzi.use.uml.ocl.value.StringValue)system.state().objectByName(root.object()).state(state).attributeValue("arguments");
                    return (String)CanonicalJson.object(CanonicalJson.decode(arguments.value().getBytes(java.nio.charset.StandardCharsets.UTF_8))).get("Id");
                });
                var cut=facade.verificationSnapshot();
                var artifacts=cut.image().objects().values().stream().filter(o->o.className().equals("AuctionArtifact")
                        && ("'"+argumentId+"'").equals(o.attributes().get("name"))).toList();
                assertEquals(1,artifacts.size(),"Source argument Id must resolve uniquely in this current case cut; no name fallback in core");
                var selectedArtifact=artifacts.getFirst();assertEquals("false",selectedArtifact.attributes().get("running"));
                var targets=new LinkedHashMap<String,String>();targets.put(selectedArtifact.semanticId(),"Exact artifact UUID selected by root argument Id="+argumentId+"; decide plan lines 27-30 and stop operation lines 26-30");
                targets.put(selectedScheme.semanticId(),"Exact owning SchemeBoard incarnation of the failing Goal");
                selectedGoal.missions().forEach(m->targets.put(cut.image().objects().get(m.object()).semanticId(),"Exact Mission->Goal membership in this Scheme instance"));
                java.util.stream.Stream.concat(selectedGoal.committedAgents().stream(),selectedGoal.achievedAgents().stream()).forEach(a->
                        targets.put(cut.image().objects().get(a.object()).semanticId(),"Typed committed/achieved agent evidence for this Goal; not inferred achievement responsibility"));
                String correct="self.semanticId = '"+selectedGoal.semanticId()+"' implies AuctionArtifact.allInstances()->one(a | a.semanticId = '"+selectedArtifact.semanticId()+"' and not a.running)";
                Files.writeString(pass,"context Agent inv Identity: Agent.allInstances()->isUnique(semanticId)\ncontext OrganizationalGoal inv DecideArtifact: "+correct+"\n");
                facade.loadVerificationProfile(pass);assertTrue(facade.runFullVerification().results().stream().noneMatch(o->o.outcome()==VerificationOutcome.FAIL));
                // Deliberately false CASE policy. It changes the verification specification only,
                // never any Jason Goal/Belief, CArtAgO property or Moise mission/goal state.
                Path fail=evidence.resolve("deliberate-fail.ocl");
                Files.writeString(fail,"context OrganizationalGoal inv AcceptanceFalse: "+correct.replace("and not a.running","and a.running")+"\n");
                facade.loadVerificationProfile(fail);
                String constraint="EXTERNAL:OrganizationalGoal::AcceptanceFalse";
                facade.configureRuntimeConstraint(constraint,new RuntimeConstraintPolicy(ConstraintOrigin.CASE,RuntimeConstraintPolicy.Severity.HARD,
                        RuntimeConstraintPolicy.Enforcement.PAUSE_ON_FAIL,Set.of(CheckpointType.SNAPSHOT,CheckpointType.AFTER_MUTATION,CheckpointType.STREAM_BOUNDARY),
                        Set.of("runtime.moise.goalState.v1"),true,"CASE diagnostic fault: invert the approved decide->stopped-artifact expectation; original domain source/state untouched",targets));
                await(()->facade.runtimeViolations().stream().anyMatch(v->v.constraintId().equals(constraint)
                        && v.confirmation()!=VerificationViolation.Confirmation.PENDING && v.confirmation()!=VerificationViolation.Confirmation.REPORT_ONLY),50,facade);
                var violation=facade.runtimeViolations().stream().filter(v->v.constraintId().equals(constraint)
                        && v.enforcement()==RuntimeConstraintPolicy.Enforcement.PAUSE_ON_FAIL).findFirst().orElseThrow();
                assertEquals(VerificationViolation.Confirmation.CONFIRMED,violation.confirmation(),violation.diagnostic());
                assertEquals(State.PAUSED,facade.runtimeControlState().state());
                assertEquals(5,facade.runtimeControlState().required().size());
                assertEquals(facade.runtimeControlState().required(),facade.runtimeControlState().acknowledged());
                assertEquals("LIVE",facade.goalView().synchronization());assertEquals("PAUSED",facade.goalView().control());
                assertEquals(org.tzi.use.plugins.jacamo.runtime.MirrorState.LIVE,facade.runtimeStatus().state());
                var failing=facade.failingSnapshot();var confirmation=facade.confirmationSnapshot();
                assertNotNull(failing);assertNotNull(confirmation);assertNotEquals(failing.snapshotId(),confirmation.snapshotId());
                assertTrue(failing.metadata().capturedAt().compareTo(confirmation.metadata().capturedAt())<0);
                assertFalse(violation.contextObject().isBlank());assertTrue(violation.involvedObjects().containsKey(violation.contextObject()));
                assertEquals(selectedGoal.object(),violation.contextObject());assertTrue(violation.involvedObjects().containsKey(selectedArtifact.name()));
                assertTrue(violation.involvedObjects().values().stream().anyMatch(o->o.className().equals("Mission")));
                assertTrue(violation.traces().stream().anyMatch(t->t.file().endsWith("auction-os.xml") && t.line()==0 && !t.mappingRule().equals("EXACT")));
                var originalValues=failing.image();
                Thread.sleep(250);assertSame(originalValues,failing.image());assertSame(system,session.system());assertSame(state,system.state());
                facade.exportNativeUse(evidence.resolve("paused.use"));facade.exportNativeSoil(evidence.resolve("paused.cmd"));
                Files.write(evidence.resolve("failure.json"),CanonicalJson.encode(failing.toMap()));
                Files.write(evidence.resolve("confirmation.json"),CanonicalJson.encode(confirmation.toMap()));
                diagramAudit.capture("05-paused-confirmed");
                diagramAudit.assertSourceRelations(recording.runtime,"05-paused-confirmed");
                org.tzi.use.plugins.jacamo.codegrounded.GoalWorkbenchEvidence.capture(facade,session,evidence,true);
                Files.write(evidence.resolve("goals.json"),CanonicalJson.encode(facade.goalView().schemes().stream().map(s->Map.of(
                        "object",s.object(),"instance",s.runtimeIdentity(),"spec",s.specId(),"runtime",s.runtime(),"arguments",s.arguments(),
                        "goals",s.goals().stream().map(g->Map.of("object",g.object(),"id",g.id(),"state",g.state(),"evidence",g.evidence(),
                                "spec",g.specId(),"committed",g.committedAgents().stream().map(GoalViewSnapshot.Agent::object).toList(),
                                "achieved",g.achievedAgents().stream().map(GoalViewSnapshot.Agent::object).toList())).toList())).toList()));
                // Removing the deliberate policy is an explicit specification change, not repair.
                facade.loadVerificationProfile(pass);
                facade.resumeRuntime().get(50,TimeUnit.SECONDS);
                assertEquals(State.RUNNING,facade.runtimeControlState().state());assertTrue(facade.runtimeControlState().liveReady());
                assertEquals(org.tzi.use.plugins.jacamo.runtime.MirrorState.LIVE,facade.runtimeStatus().state());
                assertSame(failing,facade.failingSnapshot());assertSame(system,session.system());assertSame(state,system.state());
                diagramAudit.capture("06-resumed");
                diagramAudit.assertSourceRelations(recording.runtime,"06-resumed");
                assertTrue(producer.sourceUnchanged());
                Files.write(evidence.resolve("performance.json"),CanonicalJson.encode(facade.runtimePerformanceMetrics()));
                Files.write(evidence.resolve("selective-projection.json"),CanonicalJson.encode(org.tzi.use.plugins.jacamo.codegrounded.ProjectionExposureEvidence.assertAndReport(facade)));
                Path bundle=evidence.resolve("recorded-replay");facade.exportRuntimeReplay(bundle);
                var replay=facade.replayRuntime(bundle);assertTrue(replay.complete(),replay.diagnostics().toString());
                facade.disconnectRuntime();diagramAudit.capture("07-disconnected");
                facade.connectRuntime();facade.resyncRuntime();diagramAudit.capture("08-reconnected");
                diagramAudit.assertSourceRelations(recording.runtime,"08-reconnected");
                Files.write(evidence.resolve("summary.json"),CanonicalJson.encode(Map.of("status","PASS","allAgentAck",5,"sameSystem",true,"sameState",true,
                        "failingSnapshot",failing.snapshotId(),"confirmationSnapshot",confirmation.snapshotId(),"confirmation",violation.confirmation().name(),
                        "objects",facade.formalStateStatus().objectCount(),"links",facade.formalStateStatus().linkCount(),"snapshotBytes",failing.estimatedBytes())));
                System.out.println("AUCTION_PAUSE_RESUME_PASS="+evidence);
                }
            }
        }
    }
    private static void await(java.util.function.BooleanSupplier condition,int seconds,DefaultJaCaMoFacade facade) throws Exception {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(seconds);
        long nextHealthCut=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
        while(!condition.getAsBoolean() && System.nanoTime()<deadline) {
            Thread.sleep(100);
            if(System.nanoTime()>=nextHealthCut) {facade.resyncRuntime();nextHealthCut=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);}
        }
        assertTrue(condition.getAsBoolean(),"Runtime deadline: "+facade.runtimeControlState()+" / "+facade.runtimeStatus()+" / "+facade.runtimeViolations()
                +" / "+facade.runtimeVerificationResult().diagnostic()+" / "+facade.authorityStatus().diagnostic()+" / "+facade.goalView());
    }
}
