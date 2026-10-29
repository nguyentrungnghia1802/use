package org.tzi.use.plugins.jacamo;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.jacamo.bridge.contract.CanonicalJson;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.tzi.use.main.Session;

/** Real GUI-facing service, preparation script, official producer and TCP. No UI mock. */
class OwnedProducerRuntimeIT {
    @Test @Timeout(360) void authoredInputsPrepareStartStopAndRestartWithoutReplacingTheUseProcess()throws Exception {
        Path checkout=Path.of("..").toAbsolutePath().normalize(),project=Path.of("src/test/resources/auction-reference/auction.jcm").toAbsolutePath();
        Path inputs=project.getParent().resolve("authored-v1");
        var selection=new JaCaMoFacade.ImportSelection("JACAMO_DECLARATIVE_XML",inputs.resolve("auction-mapping.xml"),inputs.resolve("auction_constraints_final_fixed.ocl"),inputs.resolve("auction_csl.csl"),inputs.resolve("auction_istar_final.istar"));
        var session=new Session();long usePid=ProcessHandle.current().pid();
        Path evidence=checkout.resolve("use-plugin/verification/authored-models/P08-live");Files.createDirectories(evidence);
        String budget=System.getProperty("use.jacamo.runtime.journal.maxBytes");System.setProperty("use.jacamo.runtime.journal.maxBytes","268435456");
        try(var facade=new DefaultJaCaMoFacade(checkout,session)) {
            try {
                facade.prepareOwnedProducer(project,selection,null);
                long child=facade.ownedProducerStatus().pid();assertNotEquals(usePid,child);assertTrue(ProcessHandle.of(child).orElseThrow().isAlive());
                var first=session.system();assertNotNull(first);assertSame(first.model(),facade.semanticModels().nativeModel());
                Path stagedProject=facade.projectSummary().entry().getParent();
                assertTrue(facade.workflowStatus().startAvailable(),facade.workflowStatus().toString());
                facade.startRuntime();assertEquals("RUNNING",facade.ownedProducerStatus().state());awaitAuctions(facade,session,first);
                var projection=projector(facade);
                var beliefs=projection.coordinator().read(()->first.state().allObjects().stream().filter(o->Set.of("auctioneer","participant").contains(o.cls().name())).map(o->Map.of("role",o.cls().name(),"player",projection.mutations().metadata(o,"rolePlayer"),"viewSource",projection.mutations().metadata(o,"belief:view:source:beliefBase"),"quality",projection.mutations().metadata(o,"belief:view:quality:beliefBase"),"storedLiterals",o.state(first.state()).attributeValue("beliefBase").toString())).toList());
                assertEquals(5,beliefs.size());for(var belief:beliefs){assertEquals("COMPLETE",belief.get("quality"));assertEquals(belief.get("player"),belief.get("viewSource"));assertFalse(belief.get("storedLiterals").equals("Undefined"));}
                Files.write(evidence.resolve("belief-views.json"),CanonicalJson.encode(beliefs));
                Files.write(evidence.resolve("first-result.json"),CanonicalJson.encode(facade.runtimeVerificationResult().toMap()));
                Files.write(evidence.resolve("program.json"),CanonicalJson.encode(facade.semanticProgramEvidence()));
                String firstRun=facade.authorityStatus().sessionId();
                facade.stopOwnedProducer();assertFalse(ProcessHandle.of(child).map(ProcessHandle::isAlive).orElse(false));assertTrue(ProcessHandle.current().isAlive());
                assertFalse(Files.exists(stagedProject),"Owned copied sources and credentials must be removed after process termination");
                facade.restartOwnedProducer();long nextChild=facade.ownedProducerStatus().pid();assertNotEquals(child,nextChild);assertNotEquals(firstRun,facade.authorityStatus().sessionId());
                assertNotSame(first,session.system());assertSame(session.system().model(),facade.semanticModels().nativeModel());
                facade.startRuntime();awaitAuctions(facade,session,session.system());
                facade.stopOwnedProducer();assertFalse(ProcessHandle.of(nextChild).map(ProcessHandle::isAlive).orElse(false));
                Files.write(evidence.resolve("receipt.json"),CanonicalJson.encode(Map.of("usePid",usePid,"producerPid",child,"restartProducerPid",nextChild,"sameUsePid",ProcessHandle.current().pid()==usePid,"authoredModels",true,"transport","TCP","stoppedChildren",true,"oneAuthoritativeSession",true)));
                Files.deleteIfExists(evidence.resolve("failure.txt"));
            }catch(Exception|AssertionError failure){
                Files.writeString(evidence.resolve("failure.txt"),failure+"\n"+facade.ownedProducerStatus()+"\n"+facade.status());
                var last=facade.runtimeVerificationResult();if(last!=null)Files.write(evidence.resolve("failure-result.json"),CanonicalJson.encode(last.toMap()));
                var projector=projector(facade);
                if(projector!=null)Files.write(evidence.resolve("failure-state.json"),CanonicalJson.encode(projector.coordinator().read(()->projector.system().state().allObjects().stream().map(o->Map.of("class",o.cls().name(),"name",o.name(),"attributes",o.state(projector.system().state()).attributeValueMap().toString())).toList())));
                throw failure;
            }
        }finally{if(budget==null)System.clearProperty("use.jacamo.runtime.journal.maxBytes");else System.setProperty("use.jacamo.runtime.journal.maxBytes",budget);}
    }
    private static void awaitAuctions(DefaultJaCaMoFacade facade,Session session,org.tzi.use.uml.sys.MSystem system)throws Exception {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(55);
        while(System.nanoTime()<deadline){
            assertSame(system,session.system());
            var projector=projector(facade);
            long count=projector.coordinator().read(()->system.state().allObjects().stream().filter(o->o.cls().name().equals("Auction")).count());
            long beliefs=projector.coordinator().read(()->system.state().allObjects().stream().filter(o->Set.of("auctioneer","participant").contains(o.cls().name())&&"COMPLETE".equals(projector.mutations().metadata(o,"belief:view:quality:beliefBase"))).count());
            if(count>=2&&beliefs==5)return;
            Thread.sleep(100);
        }
        fail("Two live Auctions not projected: "+facade.status()+" "+facade.formalStateStatus());
    }
    static org.tzi.use.plugins.jacamo.codegrounded.runtime.NativeRuntimeProjector projector(DefaultJaCaMoFacade facade)throws Exception {
        var field=DefaultJaCaMoFacade.class.getDeclaredField("nativeWorkspace");field.setAccessible(true);Object workspace=field.get(facade);if(workspace==null)return null;
        var p=workspace.getClass().getDeclaredField("runtimeProjector");p.setAccessible(true);return (org.tzi.use.plugins.jacamo.codegrounded.runtime.NativeRuntimeProjector)p.get(workspace);
    }
}
