package org.tzi.use.plugins.jacamo;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.jacamo.bridge.adapter.*;
import org.tzi.use.main.Session;
import org.tzi.use.plugins.jacamo.bridge.*;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.NativeRuntimeProjector;
import org.tzi.use.plugins.jacamo.semantics.*;
import org.tzi.use.plugins.jacamo.semantics.mapping.MappingRuleSet;
import org.tzi.use.plugins.jacamo.semantics.profile.SemanticProjectionProfile;
import static org.junit.jupiter.api.Assertions.*;

class CanonicalMonitorLifecycleTest {
    @Test void clearingHostSessionIsValidAndTerminatesTheRevokedEngine() throws Exception {
        try(var configuration=new Configuration();var facade=BridgeFacadeTestSupport.nativeFacade(configuration.jcm,configuration.host,()->false)) {
            facade.importProject(configuration.jcm);var owner=projector(facade).coordinator().originalEngine();
            SwingUtilities.invokeAndWait(()->assertDoesNotThrow(()->configuration.host.setSystem(null)));
            await(()->Boolean.TRUE.equals(owner.metrics().get("terminated")));
            assertEquals(0,owner.metrics().get("expressions"));assertFalse(configuration.host.hasSystem());
        }
    }
    @Test void resetRetiresIngressAndWorkerWithoutWaitingForTransportOnEdt() throws Exception {
        try(var configuration=new Configuration()) {
            var closing=new CountDownLatch(1);var release=new CountDownLatch(1);var closed=new AtomicBoolean();
            try(var facade=new DefaultJaCaMoFacade(Path.of("."),SemanticAuthority.BRIDGE,BridgeFacadeTestSupport::configuration,ignored->{
                // Obtain recorded frames through the same already-tested source adapter helper.
                try {var model=CanonicalGoalMappingTest.source();var method=BridgeFacadeTestSupport.class.getDeclaredMethod("frames",org.jacamo.bridge.contract.ModelSnapshot.class,String.class);method.setAccessible(true);
                    @SuppressWarnings("unchecked") var frames=(List<byte[]>)method.invoke(null,model,model.sources().getFirst().id().scope());
                    var recorded=new RecordedBridgeTransport(frames);
                    return new BridgeTransport(){
                        public byte[] handshake(){return recorded.handshake();}public byte[] modelSnapshot(){return recorded.modelSnapshot();}public byte[] runtimeSnapshot(){return recorded.runtimeSnapshot();}
                        public Subscription subscribe(String token,java.util.function.Consumer<byte[]> next){return recorded.subscribe(token,next);}
                        public void acknowledge(String token){recorded.acknowledge(token);}
                        public void close(){closing.countDown();try{if(!release.await(5,TimeUnit.SECONDS))throw new IllegalStateException("TEST_CLOSE_RELEASE_TIMEOUT");}catch(InterruptedException e){Thread.currentThread().interrupt();}finally{closed.set(true);recorded.close();}}
                    };
                }catch(Exception error){throw new IllegalStateException(error);}
            },PipelineMode.CODE_GROUNDED_NATIVE,configuration.host)) {
                facade.importProject(configuration.jcm);var owner=projector(facade).coordinator().originalEngine();
                long started=System.nanoTime();SwingUtilities.invokeAndWait(configuration.host::reset);
                assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-started)<750,"Session listener waited for TCP teardown on EDT");
                try{assertTrue(closing.await(2,TimeUnit.SECONDS),"Superseded ingress was never closed");await(()->Boolean.TRUE.equals(owner.metrics().get("terminated")));}
                finally{release.countDown();}
                await(closed::get);assertSame(configuration.host.system(),facade.materializedSystem());
            }finally{release.countDown();}
        }
    }
    @Test void sameWatermarkResyncDoesNotAdvanceOriginalHistoryTwice() throws Exception {
        try(var fixture=new CanonicalRuntimeFixture(CslCounterArtifact.class,CanonicalGoalMappingTest::rules)) {
            fixture.settled();var c=fixture.projector().coordinator();var waters=fixture.projector().sourceWatermarks();var before=c.originalEngine().checkpoint().get(5,TimeUnit.SECONDS);
            fixture.resync();fixture.settled();assertEquals(waters,fixture.projector().sourceWatermarks());
            assertEquals(before.histories(),c.originalEngine().checkpoint().get(5,TimeUnit.SECONDS).histories(),"Unchanged admitted source cut was counted twice");
        }
    }
    @Test void simultaneousHostResetAndBackgroundStopRetireOneOwnerWithoutThrowing() throws Exception {
        try(var configuration=new Configuration();var facade=BridgeFacadeTestSupport.nativeFacade(configuration.jcm,configuration.host,()->false)) {
            facade.importProject(configuration.jcm);var owner=projector(facade).coordinator().originalEngine();
            var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var reset=new CompletableFuture<Void>();var failure=new AtomicReference<Throwable>();
            SwingUtilities.invokeLater(()->{entered.countDown();try{if(!release.await(5,TimeUnit.SECONDS))throw new IllegalStateException("TEST_EDT_RELEASE_TIMEOUT");configuration.host.reset();reset.complete(null);}catch(Throwable error){reset.completeExceptionally(error);}});
            assertTrue(entered.await(2,TimeUnit.SECONDS));
            var closer=new Thread(()->{try{facade.stopMonitorSession();}catch(Throwable error){failure.set(error);}},"test-monitor-close-race");closer.start();
            try {
                await(()->Arrays.stream(closer.getStackTrace()).anyMatch(frame->frame.getMethodName().equals("invokeAndWait")));
                release.countDown();reset.get(5,TimeUnit.SECONDS);closer.join(5000);assertFalse(closer.isAlive());assertNull(failure.get(),()->"Concurrent close failed: "+failure.get());
                await(()->Boolean.TRUE.equals(owner.metrics().get("terminated")));assertEquals(0,owner.metrics().get("expressions"));
            }finally{release.countDown();closer.join(5000);}
        }
    }
    static NativeRuntimeProjector projector(DefaultJaCaMoFacade facade) throws Exception {
        var workspace=DefaultJaCaMoFacade.class.getDeclaredField("nativeWorkspace");workspace.setAccessible(true);var value=workspace.get(facade);var field=value.getClass().getDeclaredField("runtimeProjector");field.setAccessible(true);return (NativeRuntimeProjector)field.get(value);
    }
    static void await(java.util.function.BooleanSupplier ready) throws Exception {long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);while(!ready.getAsBoolean()&&System.nanoTime()<until)Thread.sleep(10);assertTrue(ready.getAsBoolean());}
    static final class Configuration implements AutoCloseable {
        final Path jcm=Path.of("src/test/resources/semantics/original-engine/monitor.jcm").toAbsolutePath();final Session host=new Session();
        final String previousProfile=System.getProperty(SemanticProjectionProfile.PROPERTY),previousRules=System.getProperty(MappingRuleSet.PROPERTY);
        Configuration() throws Exception {var file=Files.createTempDirectory(Path.of("target"),"canonical-lifecycle-").resolve("rules.json");Files.write(file,CanonicalGoalMappingTest.rules(CanonicalGoalMappingTest.source()).bytes());System.setProperty(SemanticProjectionProfile.PROPERTY,"USE4GOAL_OBSERVED_V1");System.setProperty(MappingRuleSet.PROPERTY,file.toAbsolutePath().toString());}
        public void close(){restore(SemanticProjectionProfile.PROPERTY,previousProfile);restore(MappingRuleSet.PROPERTY,previousRules);}
        static void restore(String name,String value){if(value==null)System.clearProperty(name);else System.setProperty(name,value);}
    }
}
