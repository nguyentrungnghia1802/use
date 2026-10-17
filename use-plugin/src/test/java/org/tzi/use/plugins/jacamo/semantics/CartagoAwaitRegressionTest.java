package org.tzi.use.plugins.jacamo.semantics;

import static org.junit.jupiter.api.Assertions.*;

import cartago.CartagoEnvironment;
import cartago.Op;
import cartago.util.agent.CartagoBasicContext;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import org.jacamo.bridge.adapter.*;
import org.jacamo.bridge.contract.*;
import org.junit.jupiter.api.Test;
import org.tzi.use.plugins.jacamo.codegrounded.*;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.*;
import org.tzi.use.plugins.jacamo.codegrounded.use.*;
import org.tzi.use.plugins.jacamo.semantics.csl.*;
import org.tzi.use.plugins.jacamo.semantics.evidence.*;
import org.tzi.use.plugins.jacamo.semantics.istar.RequirementModel;
import org.tzi.use.plugins.jacamo.semantics.profile.*;

/** Real CArtAgO await, with two overlapping callers and independent pinned pre-states. */
public class CartagoAwaitRegressionTest {
    private static void waitUntil(BooleanSupplier condition) throws Exception {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
        while(!condition.getAsBoolean() && System.nanoTime()<deadline)Thread.sleep(2);
        assertTrue(condition.getAsBoolean(),"Timed out waiting for an actual operation callback");
    }

    @Test void actualAwaitReportsPartialLifecycleAndKeepsBothInvocationPreStates() throws Exception {
        Path directory=Files.createTempDirectory(Path.of("target"),"live-overlap-");
        Files.createDirectories(directory.resolve("src/agt"));
        Files.createDirectories(directory.resolve("src/org"));
        for(String source:List.of("src/agt/a.asl","src/org/monitor.xml"))
            Files.copy(Path.of("src/test/resources/semantics/profile-v1").resolve(source),directory.resolve(source));
        Files.writeString(directory.resolve("monitor.jcm"),
                Files.readString(Path.of("src/test/resources/semantics/profile-v1/monitor.jcm"))
                        .replace(CslCounterArtifact.class.getName(),AwaitRegressionArtifact.class.getName()));
        Path jcm=directory.resolve("monitor.jcm").toAbsolutePath();
        var source=new OfficialProjectAdapter().adapt(new OfficialProjectLoader().load(jcm),jcm);
        var type=new CslSchema.Classifier("domain",DomainProjection.artifactId(AwaitRegressionArtifact.class.getName()),
                "OverlapCounter",CslSchema.Kind.ENTITY,"",false,
                List.of(new CslSchema.Attribute("p","p","Integer","p",false)));
        var contracts=new ArrayList<CslSchema.OperationContract>();
        for(String operation:List.of("slow","fast"))
            contracts.add(new CslSchema.OperationContract(operation,"domain",
                    List.of(AwaitRegressionArtifact.class.getName(),operation,List.of(),"void"),
                    "true","self.p = self.p@pre + 1"));
        var config=new CslMonitorConfiguration("1.0",SemanticProjectionProfile.CSL_OBSERVER_V1,
                new CslSchema("overlap",List.of(type),List.of(),List.of(),contracts),
                new RequirementModel(List.of()),List.of());
        var built=new CodeGroundedNativePipeline().build(source,NativeProjectionMode.AUTO,config);
        String oldRevision=System.getProperty("jacamo.bridge.modelRevision");
        String oldGeneration=System.getProperty("jacamo.bridge.generation");
        var environment=CartagoEnvironment.getInstance();
        environment.init();
        String name="awaitRegression"+UUID.randomUUID().toString().replace("-","");
        var a=new CartagoBasicContext("awaitA");
        var b=new CartagoBasicContext("awaitB");
        var artifact=a.makeArtifact(a.getJoinedWspId("main"),name,AwaitRegressionArtifact.class.getName());
        System.setProperty("jacamo.bridge.modelRevision",source.modelRevision());
        System.setProperty("jacamo.bridge.generation","1");
        var results=new CopyOnWriteArrayList<SemanticConstraintResult>();
        var events=new CopyOnWriteArrayList<RuntimeEvent>();
        var error=new AtomicReference<Throwable>();
        var target=new AtomicReference<NativeRuntimeProjector>();
        var cartago=new CartagoSnapshotSource(environment,List.of("/main"),"await-regression");
        try(var projector=new NativeRuntimeProjector(built,"await-regression",1,source.modelRevision());
            var captures=new SnapshotCoordinator(List.of(cartago),8192,event->{
                events.add(event);
                if(target.get()!=null)try {
                    target.get().apply(event);
                    if("completed".equals(event.after().get("phase")))
                        results.addAll(target.get().coordinator().latest().semantic().constraints().stream()
                                .filter(row->row.kind().equals("POST") && row.invocation().equals(event.correlationId()))
                                .toList());
                } catch(Throwable failure) { error.compareAndSet(null,failure); }
            })) {
            assertEquals("PARTIAL",cartago.captureMetadata().get("operationLifecycleCoverage"));
            target.set(projector);
            projector.applySnapshot(captures.capture(source.modelRevision(),3).snapshot());
            int iterations=Integer.getInteger("use.fix.awaitIterations",3);
            assertTrue(iterations>0,"The real await workload must execute");
            var pool=Executors.newSingleThreadExecutor();
            try {
                for(int iteration=0;iteration<iterations;iteration++) {
                    int previous=results.size();
                    AwaitRegressionArtifact.suspended=new CountDownLatch(1);
                    a.doAction(artifact,new Op("reset"));
                    var pending=pool.submit(()->{
                        try { a.doAction(artifact,new Op("slow")); }
                        catch(Exception failure) { throw new RuntimeException(failure); }
                    });
                    assertTrue(AwaitRegressionArtifact.suspended.await(5,TimeUnit.SECONDS),"A did not start");
                    waitUntil(()->error.get()!=null || projector.system().state().allObjects().stream()
                            .filter(object->object.cls().name().equals("OverlapCounter"))
                            .anyMatch(object->object.state(projector.system().state()).attributeValue("p").toString().equals("1")));
                    if(error.get()!=null)throw new AssertionError(error.get());
                    b.doAction(artifact,new Op("fast"));
                    waitUntil(()->results.size()>=previous+1 || error.get()!=null);
                    b.doAction(artifact,new Op("unblock"));
                    pending.get(5,TimeUnit.SECONDS);
                    waitUntil(()->results.size()>=previous+2 || error.get()!=null);
                    if(error.get()!=null)throw new AssertionError(error.get());
                    var current=results.subList(previous,results.size());
                    var fast=current.stream().filter(row->row.id().equals("fast:post")).findFirst().orElseThrow();
                    var slow=current.stream().filter(row->row.id().equals("slow:post")).findFirst().orElseThrow();
                    assertEquals(SemanticVerdict.PASS,fast.verdict(),fast.toString());
                    assertEquals("1",fast.witness().get("before.p"));assertEquals("2",fast.witness().get("after.p"));
                    assertEquals(SemanticVerdict.FAIL,slow.verdict(),slow.toString());
                    assertEquals("0",slow.witness().get("before.p"));assertEquals("3",slow.witness().get("after.p"));
                }
            } finally { pool.shutdownNow(); }
            assertTrue(events.stream().noneMatch(event->Set.of("suspended","resumed")
                    .contains(Objects.toString(event.after().get("phase"),""))),
                    "CArtAgO binary phase coverage changed; re-audit the declared capability");
            assertTrue(events.stream().filter(event->"started".equals(event.after().get("phase")))
                    .allMatch(event->"CARTAGO_OPID_AGENT_BODY_ID".equals(event.after().get("performerAuthority"))));
            assertTrue(results.stream().allMatch(row->"PARTIAL".equals(row.witness().get("operationLifecycleCoverage"))));
            Path replayDirectory=directory.resolve("replay");
            new NativeRuntimeReplay().exportBundle(projector,built.export().useText(),replayDirectory);
            var replay=new NativeRuntimeReplay().replay(replayDirectory);
            assertTrue(replay.complete(),replay.toString());
            assertEquals(projector.coordinator().latest().resultHash(),replay.finalResultHash());
            Files.write(Path.of("target/cartago-await-regression.json"),CanonicalJson.encode(Map.of(
                    "iterations",iterations,"postChecks",results.size(),"actualCartagoEvents",events.size(),
                    "suspendedCallbacks",events.stream().filter(event->"suspended".equals(event.after().get("phase"))).count(),
                    "resumedCallbacks",events.stream().filter(event->"resumed".equals(event.after().get("phase"))).count(),
                    "lastChecks",SemanticJson.tree(results.subList(results.size()-2,results.size())),
                    "replay",SemanticJson.tree(replay),"directory",directory.toAbsolutePath().toString())));
            System.out.println("ACTUAL_CARTAGO_OVERLAP_PASS iterations="+iterations+" replay="+replay);
        } catch(Throwable failure) {
            Files.write(Path.of("target/cartago-await-regression-failure.json"),CanonicalJson.encode(Map.of(
                    "failure",failure.toString(),"sourceError",String.valueOf(error.get()),
                    "events",events.stream().map(event->Map.of("kind",event.kind().name(),"seq",event.sourceSequence(),"after",event.after())).toList())));
            throw failure;
        } finally {
            environment.getController("/main").removeArtifact(name);
            CslRuntimeTestRegistry.quitCartagoActor(environment,a.getName());
            CslRuntimeTestRegistry.quitCartagoActor(environment,b.getName());
            environment.shutdown();
            CslVerticalSliceTest.restore("jacamo.bridge.modelRevision",oldRevision);
            CslVerticalSliceTest.restore("jacamo.bridge.generation",oldGeneration);
        }
    }
}
