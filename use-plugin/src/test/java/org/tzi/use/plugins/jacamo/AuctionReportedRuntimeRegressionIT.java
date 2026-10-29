package org.tzi.use.plugins.jacamo;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.jacamo.bridge.contract.CanonicalJson;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.tzi.use.main.Session;
import org.tzi.use.plugins.jacamo.bridge.BridgeClientState;
import static org.junit.jupiter.api.Assertions.*;

/** Reproduce the user's Auction run at its original external source location. */
class AuctionReportedRuntimeRegressionIT {
    @Test @Timeout(240) void externalAuctionRemainsObservedThroughBothAuctionCompletions() throws Exception {
        Path checkout=Path.of("..").toAbsolutePath().normalize();
        Path project=checkout.getParent().resolve("jacamo/examples/auction/auction.jcm");
        Path inputs=checkout.resolve("use-plugin/examples/auction");
        Path evidence=Files.createTempDirectory(Files.createDirectories(checkout.resolve("use-plugin/verification/auction-reported-fix")),"run-");
        byte[] original=Files.readAllBytes(project);
        var selection=new JaCaMoFacade.ImportSelection("JACAMO_DECLARATIVE_XML",inputs.resolve("source-confirmed-v1/auction-mapping.xml"),
                inputs.resolve("source-confirmed-v1/auction_confirmed.ocl"),inputs.resolve("authored-v1/auction_csl.csl"),inputs.resolve("authored-v1/auction_istar_final.istar"));
        var session=new Session();long child=0;String run="";
        boolean showGui=Boolean.getBoolean("use.auction.regression.gui");
        try(var facade=new DefaultJaCaMoFacade(checkout,session)) {
            assertEquals(256L*1024*1024,facade.nextJournalBudgetBytes());
            try {
                facade.prepareOwnedProducer(project,selection,null,showGui);child=facade.ownedProducerStatus().pid();run=facade.ownedProducerStatus().runId();
                assertNotEquals(ProcessHandle.current().pid(),child);assertTrue(ProcessHandle.of(child).orElseThrow().isAlive());
                var system=session.system();assertNotNull(system);assertSame(system.model(),facade.semanticModels().nativeModel());
                var projector=OwnedProducerRuntimeIT.projector(facade);
                var timeline=new org.tzi.use.plugins.jacamo.ui.use4goal.conformance.ObservedConformancePresenter(facade);
                int previousCuts=0;
                facade.startRuntime();long began=System.nanoTime(),end=began+TimeUnit.SECONDS.toNanos(110);
                long samples=0;List<Map<String,String>> auctions=List.of();
                while(System.nanoTime()<end) {
                    assertSame(system,session.system());samples++;
                    assertEquals(BridgeClientState.LIVE,facade.authorityStatus().readiness(),()->"Coverage lost: "+facade.status()+" evidence="+evidence);
                    if(samples%25==0){int count=timeline.capture().size();assertTrue(count>=previousCuts,"The active Auction timeline must not shrink: "+previousCuts+" -> "+count);previousCuts=count;}
                    auctions=projector.coordinator().read(()->system.state().allObjects().stream().filter(o->o.cls().name().equals("Auction"))
                            .map(o->Map.of("object",o.name(),"running",o.state(system.state()).attributeValue("running").toString(),"winner",o.state(system.state()).attributeValue("winner").toString())).toList());
                    if(System.nanoTime()-began>TimeUnit.SECONDS.toNanos(45)&&auctions.size()==2&&auctions.stream().allMatch(a->a.get("running").equals("false")))break;
                    Thread.sleep(200);
                }
                assertEquals(2,auctions.size(),"Both actual source-created Auction artifacts must be projected");
                assertTrue(auctions.stream().allMatch(a->a.get("running").equals("false")),auctions.toString());
                var evaluated=projector.coordinator().awaitEvaluation(projector.coordinator().manualVerify(),30000);
                var core=CanonicalJson.object(evaluated.semantic().observation().get("originalEngine"));
                assertEquals("PINNED_NATIVE_CUT",core.get("evaluated"));assertNotEquals("STALE",evaluated.freshness());
                long known=evaluated.semantic().constraints().stream().filter(c->Set.of("PASS","FAIL").contains(c.verdict().name())).count();
                assertTrue(known>0,"The accepted cut must reach native OCL; pending evaluation is not a completed verdict");
                var cuts=timeline.capture();assertTrue(cuts.size()>=previousCuts);
                var cutIds=cuts.stream().map(c->c.id()).collect(java.util.stream.Collectors.toSet());
                long journalEnd=facade.runtimeHistoryTail().persistedEntries();var expectedCuts=new HashSet<String>();
                for(long offset=0;offset<journalEnd;){var page=facade.runtimeHistoryPage(offset,(int)Math.min(128,journalEnd-offset));
                    assertTrue(page.nextOrdinal()>offset);
                    for(var entry:page.entries()){var r=entry.result();if(r.stateVersion()<=evaluated.stateVersion())expectedCuts.add(r.sessionId()+":"+r.generation()+":"+r.modelRevision()+":"+r.stateVersion()+":"+r.eventId()+":"+r.checkpointId());}
                    offset=page.nextOrdinal();}
                assertTrue(cutIds.containsAll(expectedCuts),"Every persisted Auction boundary through the verified cut must be navigable");
                assertFalse(projector.coordinator().journal().hasGap());
                Files.write(evidence.resolve("result.json"),CanonicalJson.encode(evaluated.toMap()));
                var receipt=new LinkedHashMap<String,Object>(Map.of("status","PASS","source",project.toString(),"usePid",ProcessHandle.current().pid(),"producerPid",child,"runId",run,"samples",samples,"sameMSystem",session.system()==system,"auctions",auctions,"producerGui",showGui));
                receipt.put("nativeKnownVerdicts",known);receipt.put("engineCut",core.get("evaluated"));receipt.put("coverage",evaluated.coverage());receipt.put("freshness",evaluated.freshness());
                receipt.put("timelineCuts",cuts.size());receipt.put("expectedTimelinePrefix",expectedCuts.size());receipt.put("journalEntries",journalEnd);
                receipt.put("memoryTailEntries",facade.runtimeVerificationHistory().size());receipt.put("journalBytes",projector.coordinator().journal().persistedBytes());receipt.put("journalMaxBytes",projector.coordinator().journal().maxBytes());receipt.put("journalGap",projector.coordinator().journal().hasGap());
                Files.write(evidence.resolve("receipt.json"),CanonicalJson.encode(receipt));
            } finally {
                var result=facade.runtimeVerificationResult();if(result!=null)Files.write(evidence.resolve("last-result.json"),CanonicalJson.encode(result.toMap()));
                var failedProjector=OwnedProducerRuntimeIT.projector(facade);
                if(failedProjector!=null)Files.write(evidence.resolve("native-state.json"),CanonicalJson.encode(failedProjector.coordinator().read(()->session.system().state().allObjects().stream().filter(o->o.cls().name().equals("Auction"))
                        .map(o->Map.of("object",o.name(),"metadata",failedProjector.mutations().objectMetadata(o),"attributes",o.state(session.system().state()).attributeValueMap().toString())).toList())));
                facade.stopOwnedProducer();
                if(child!=0)assertFalse(ProcessHandle.of(child).map(ProcessHandle::isAlive).orElse(false));
                for(Path base:List.of(checkout.resolve("use-plugin/target/owned-runtime"),checkout.resolve("work/owned-runtime")))if(Files.isDirectory(base)) {
                    try(var dirs=Files.list(base)){for(Path dir:dirs.toList()) {
                        Path manifest=dir.resolve("prepared.json");if(!Files.isRegularFile(manifest)||run.isEmpty()||!Files.readString(manifest).contains(run))continue;
                        for(String name:List.of("producer.stdout.log","producer.stderr.log"))if(Files.isRegularFile(dir.resolve(name)))Files.copy(dir.resolve(name),evidence.resolve(name),StandardCopyOption.REPLACE_EXISTING);
                    }}
                }
                assertArrayEquals(original,Files.readAllBytes(project));
                System.out.println("AUCTION_REPORTED_RUNTIME_EVIDENCE="+evidence);
            }
        }
    }
}
