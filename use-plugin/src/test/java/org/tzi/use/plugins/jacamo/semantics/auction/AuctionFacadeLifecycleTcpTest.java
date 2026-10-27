package org.tzi.use.plugins.jacamo.semantics.auction;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import javax.swing.SwingUtilities;
import org.jacamo.bridge.adapter.*;
import org.jacamo.bridge.contract.*;
import org.junit.jupiter.api.Test;
import org.tzi.use.main.Session;
import org.tzi.use.plugins.jacamo.*;
import org.tzi.use.plugins.jacamo.bridge.BridgeTransportFactory;
import org.tzi.use.plugins.jacamo.semantics.runtime.RuntimeEngineSessionOwner;
import static org.junit.jupiter.api.Assertions.*;

/** Public facade over real external canonical Auction. This exercises Session
 * ownership, not the installed ZIP/Workbench UI acceptance E01/E02. */
class AuctionFacadeLifecycleTcpTest {
    @Test void managedAuctionPauseResumeDisconnectStopRestartAndProducerReplacementKeepOneLiveOwner()throws Exception {
        Path evidence=Files.createTempDirectory(Files.createDirectories(Path.of("target/auction-implementation")),"T02-facade-live-").toAbsolutePath();
        String key="use.jacamo.runtime.journal.maxBytes",previous=System.getProperty(key);System.setProperty(key,Long.toString(256L*1024*1024));
        try(var producer=new ManagedProducerTestSupport(Path.of("examples/auction/auction.jcm"),evidence,false,true,Map.of(AuctionStepObservation.PROPERTY,"true",AuctionSchemeObservation.PROPERTY,"true",AuctionClockObservation.PROPERTY,"true"),entry->!entry.replace('\\','/').endsWith("/target/test-classes"))) {
            long firstPid=producer.producerPid();producer.configureWorkflow();var host=new Session();var resources=new ArrayList<Object>();
            var connections=new java.util.concurrent.atomic.AtomicInteger();
            try(var facade=new DefaultJaCaMoFacade(Path.of("."),SemanticAuthority.BRIDGE,()->producer.configuration,configuration->{connections.incrementAndGet();return BridgeTransportFactory.localTcp().open(configuration);},PipelineMode.CODE_GROUNDED_NATIVE,host)) {
                producer.clearWorkflowProperties();var selection=new JaCaMoFacade.ImportSelection("AUCTION_OBSERVED_V1",Path.of("examples/auction/auction_jacamo_to_csl_rules.xml"));
                facade.importProject(producer.jcm,selection);assertEquals("MODEL_READY",facade.workflowStatus().state());assertTrue(facade.workflowStatus().startAvailable());
                var first=AuctionImportTest.projector(facade);var firstOwner=first.coordinator().originalEngine();var system=host.system();String run=firstOwner.identity().run();
                facade.resyncRuntime();assertSame(system,host.system());assertSame(firstOwner,AuctionImportTest.projector(facade).coordinator().originalEngine());
                int beforeStart=connections.get();
                facade.startRuntime();assertEquals("LIVE",facade.workflowStatus().state());assertThrows(IllegalStateException.class,facade::startRuntime);
                assertEquals(beforeStart,connections.get(),"Start must retain the already-admitted stream and source Task prefix rather than replacing it with a snapshot");
                await(()->first.coordinator().read(()->system.state().allObjects().stream().filter(o->o.cls().name().equals("Auction")).count()==2),120,facade);
                facade.pauseRuntime("T02 actual Auction ownership baseline").get(45,TimeUnit.SECONDS);
                assertEquals(RuntimeControlContract.State.PAUSED,facade.runtimeControlState().state());assertEquals(facade.runtimeControlState().required(),facade.runtimeControlState().acknowledged());assertEquals(5,facade.runtimeControlState().required().size());
                assertSame(system,host.system());assertTrue(producer.producerAlive());
                var checkpoint=firstOwner.checkpoint().get(5,TimeUnit.SECONDS);facade.resyncRuntime();assertSame(system,host.system());
                assertEquals(checkpoint.histories(),firstOwner.checkpoint().get(5,TimeUnit.SECONDS).histories(),"Unchanged paused source cut cannot advance the recurrence");
                facade.resumeRuntime().get(45,TimeUnit.SECONDS);assertEquals(RuntimeControlContract.State.RUNNING,facade.runtimeControlState().state());
                facade.pauseRuntime("T02 stable monitor retirement").get(45,TimeUnit.SECONDS);
                facade.disconnectRuntime();assertEquals("STALE",facade.runtimeVerificationResult().freshness());assertTrue(producer.producerAlive());facade.connectRuntime();
                assertSame(system,host.system());assertSame(firstOwner,AuctionImportTest.projector(facade).coordinator().originalEngine());
                for(int cycle=0;cycle<2;cycle++) {
                    var old=AuctionImportTest.projector(facade);var owner=old.coordinator().originalEngine();var retainedSystem=host.system();
                    long began=System.nanoTime();SwingUtilities.invokeAndWait(facade::stopMonitorSession);
                    assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-began)<750,"Stop must revoke ownership without transport/file wait on EDT");
                    await(()->Boolean.TRUE.equals(owner.metrics().get("terminated")),10,facade);assertSame(retainedSystem,host.system());assertEquals("STALE",facade.runtimeVerificationResult().freshness());
                    assertEquals(0,owner.metrics().get("expressions"));assertEquals(0,owner.metrics().get("pendingPins"));assertTrue(owner.admission().steps().active().isEmpty());assertTrue(owner.admission().steps().early().isEmpty());
                    facade.restartMonitorSession();var active=AuctionImportTest.projector(facade);assertNotSame(retainedSystem,host.system());assertNotSame(owner,active.coordinator().originalEngine());
                    assertEquals(run,active.coordinator().originalEngine().identity().run());assertEquals(firstPid,producer.producerPid());assertTrue(producer.producerAlive());assertEquals(selection,facade.importSelection());
                    assertTrue(active.coordinator().originalEngine().checkpoint().get(5,TimeUnit.SECONDS).taskRows().isEmpty(),"A current snapshot cannot manufacture old Jason PRE/terminal pairs");
                    assertEquals(1,threads("use-original-engine-"));assertEquals(1,threads("jacamo-bridge-tcp-subscription"));
                    resources.add(Map.of("cycle",cycle+1,"retiredOwner",owner.metrics(),"activeOwner",active.coordinator().originalEngine().metrics(),"engineThreads",threads("use-original-engine-"),"subscriptionThreads",threads("jacamo-bridge-tcp-subscription")));
                }
                var before=AuctionImportTest.projector(facade);var beforeIdentity=before.coordinator().originalEngine().identity();
                producer.restartProducer();assertNotEquals(firstPid,producer.producerPid());facade.importProject(producer.jcm,selection);
                var replacement=AuctionImportTest.projector(facade);var replacementOwner=replacement.coordinator().originalEngine();
                assertNotEquals(beforeIdentity.run(),replacementOwner.identity().run());assertTrue(replacementOwner.checkpoint().get(5,TimeUnit.SECONDS).taskRows().isEmpty());assertTrue(replacementOwner.admission().steps().active().isEmpty());
                assertTrue(host.system().state().allObjects().stream().noneMatch(o->Set.of("AuctionMonitor","AuctionObligation").contains(o.cls().name())));assertTrue(facade.workflowStatus().startAvailable());
                // Stopping at MODEL_READY is the launcher's intentional
                // STARTUP_CANCELLED/exit1 path. Normal runtime shutdown must
                // first receive the replacement producer's real Start ACK.
                facade.startRuntime();assertEquals("LIVE",facade.workflowStatus().state());
                Files.writeString(producer.stop,"T02 official producer shutdown");assertTrue(producer.awaitProducerExit(20));assertEquals(0,producer.producerExitCode());assertTrue(host.hasSystem());assertTrue(ProcessHandle.current().isAlive());
                assertEquals("true",org.tzi.use.api.UseSystemApi.create(host.system(),false).evaluate("1 + 1 = 2").toString());assertTrue(producer.sourceUnchanged());
                Files.write(evidence.resolve("acceptance.json"),CanonicalJson.encode(Map.of("scope","T02_PUBLIC_FACADE_REAL_EXTERNAL_AUCTION_NOT_INSTALLED_WORKBENCH","consumerPid",ProcessHandle.current().pid(),"producerPid",firstPid,"replacementProducerPid",producer.producerPid(),"originalIdentity",org.tzi.use.plugins.jacamo.semantics.evidence.SemanticJson.tree(beforeIdentity),"replacementIdentity",org.tzi.use.plugins.jacamo.semantics.evidence.SemanticJson.tree(replacementOwner.identity()),"resourceCycles",resources,"nativeOclAfterProducerExit",true,"sourceUnchanged",true)));
            }
        }finally{if(previous==null)System.clearProperty(key);else System.setProperty(key,previous);}
    }
    static long threads(String prefix){return Thread.getAllStackTraces().keySet().stream().filter(t->t.isAlive()&&t.getName().startsWith(prefix)).count();}
    static void await(BooleanSupplier ready,int seconds,DefaultJaCaMoFacade facade)throws Exception {
        long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(seconds);while(!ready.getAsBoolean()&&System.nanoTime()<until)Thread.sleep(50);
        assertTrue(ready.getAsBoolean(),()->facade.status()+"; "+facade.runtimeVerificationResult().diagnostic());
    }
}
