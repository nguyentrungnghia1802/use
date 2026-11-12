package org.tzi.use.plugins.jacamo;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.GZIPOutputStream;
import org.jacamo.bridge.contract.*;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.*;
import org.tzi.use.main.Session;
import static org.junit.jupiter.api.Assertions.*;

/** Standalone live audit: unchanged external Auction, no input agent, seeded
 * randomness, extra operations, altered assertions or prescribed winner/verdict. */
public final class NaturalAuctionVerificationAudit {
    static void save(Path path,Object value)throws Exception {
        try(var output=new GZIPOutputStream(Files.newOutputStream(path))){output.write(CanonicalJson.encode(value,16*1024*1024));}
    }
    public static void main(String[] args) {
        try{run(Path.of(args[0]),Path.of(args[1]));System.out.println("NATURAL_AUCTION_VERIFICATION_AUDIT_PASS");System.exit(0);}
        catch(Throwable failure){failure.printStackTrace();System.exit(1);}
    }
    static void run(Path evidence,Path project)throws Exception {
        Files.createDirectories(evidence);Path checkout=Path.of("..").toRealPath(),models=Path.of("examples/auction").toRealPath();
        var original=VerifiedCheckpointsAuctionIT.sourceHashes(project.getParent());
        var facade=new DefaultJaCaMoFacade(checkout,new Session());facade.verifiedCheckpointMode(true);facade.nextJournalBudgetBytes(536870912);
        var presented=new ConcurrentHashMap<String,Map<String,VerifiedCheckpointProtocol.Check>>();
        var presentedResultHashes=new ConcurrentHashMap<String,String>();
        var receipts=new LinkedHashMap<String,Object>();var reviewed=new HashSet<String>();var settled=new HashSet<String>();
        var failures=new ArrayList<Object>();var unknowns=new ArrayList<Object>();String lastToken="";long activity=System.nanoTime();
        AutoCloseable presentation=facade.registerCheckpointPresenter((view,cut,checks)->{
            try {
                assertNotNull(cut.result());assertEquals(cut.result().resultHash(),RuntimeVerificationResult.fromMap(cut.result().toMap()).resultHash());
                assertTrue(checks.keySet().stream().noneMatch(k->k.contains("WithinDeadline")),"Explicitly disabled assertion must not reappear in native admission");
                save(evidence.resolve("cut-"+view.token()+".json.gz"),Map.of("view",Map.of("token",view.token(),"checkpoint",view.checkpoint(),"reason",view.reason()),"native",cut.toMap(),"checks",org.tzi.use.plugins.jacamo.semantics.evidence.SemanticJson.tree(checks),"identity",view.request().get("identity"),"barrier",view.request().get("barrier")));
                presentedResultHashes.put(view.token(),cut.result().resultHash());
                presented.put(view.token(),Map.copyOf(checks));return CompletableFuture.completedFuture(null);
            }catch(Throwable error){return CompletableFuture.failedFuture(error);}
        });
        try {
            facade.prepareOwnedProducer(project,new JaCaMoFacade.ImportSelection("JACAMO_DECLARATIVE_XML",models.resolve("auction-compact-mapping.xml"),models.resolve("auction.ocl"),models.resolve("auction.csl"),models.resolve("auction.istar"),false),checkout.resolve("use-plugin/modules/bridge-jacamo/target/jacamo-bridge-classpath.txt"));
            facade.startRuntime();long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(240);VerificationSnapshot latest=null;
            while(System.nanoTime()<end) {
                var view=facade.verifiedCheckpointState();latest=facade.verificationSnapshot();
                if(!view.token().isBlank()&&!view.token().equals(lastToken)){lastToken=view.token();activity=System.nanoTime();System.out.println("NATURAL_CUT "+view.token()+" "+view.checkpoint()+" "+view.state());}
                var checks=presented.get(view.token());
                if(checks!=null)for(String key:checks.keySet())if(key.startsWith(VerifiedCheckpointProtocol.SETTLED_CHECK_PREFIX))settled.add(key.substring(VerifiedCheckpointProtocol.SETTLED_CHECK_PREFIX.length()));
                if(view.reviewable()&&!reviewed.contains(view.token())) {
                    assertNotNull(checks,"Review may only inspect the persisted exact presented cut");
                    var detail=new LinkedHashMap<String,Object>();detail.put("token",view.token());detail.put("checkpoint",view.checkpoint());detail.put("state",view.state());detail.put("reason",view.reason());
                    detail.put("checks",org.tzi.use.plugins.jacamo.semantics.evidence.SemanticJson.tree(checks));
                    var nativeRows=new TreeMap<String,Object>();
                    for(var entry:facade.runtimeHistoryTail().entries())if(entry.result().semantic()!=null)for(var row:entry.result().semantic().constraints())
                        if(row.verdict()!=org.tzi.use.plugins.jacamo.semantics.evidence.SemanticVerdict.PASS&&!row.kind().equals("GOAL_CONDITION")&&!row.kind().equals("INVARIANT_BODY"))nativeRows.put(row.id()+":"+row.kind()+":"+row.invocation()+":"+row.verdict(),org.tzi.use.plugins.jacamo.semantics.evidence.SemanticJson.tree(row));
                    detail.put("retainedNativeNonPass",List.copyOf(nativeRows.values()));save(evidence.resolve("hold-"+view.token()+".json.gz"),detail);
                    boolean failed=checks.values().stream().anyMatch(c->c.applicability().equals("APPLICABLE")&&c.truth().equals("FAIL"));
                    boolean unavailable=checks.values().stream().anyMatch(c->c.applicability().equals("APPLICABLE")&&!Set.of("PASS","FAIL").contains(c.truth()));
                    if(unavailable){unknowns.add(Map.of("token",view.token(),"checkpoint",view.checkpoint(),"reason",view.reason(),"checks",org.tzi.use.plugins.jacamo.semantics.evidence.SemanticJson.tree(checks)));save(evidence.resolve("unexplained-hold.json.gz"),detail);break;}
                    assertTrue(failed,"Reviewable native hold must have a concrete failed check");
                    failures.add(Map.of("token",view.token(),"checkpoint",view.checkpoint(),"checks",org.tzi.use.plugins.jacamo.semantics.evidence.SemanticJson.tree(checks)));
                    Thread.sleep(300);assertEquals(view.token(),facade.verifiedCheckpointState().token());assertEquals(view.resultHash(),facade.verifiedCheckpointState().resultHash());
                    String originalHash=presentedResultHashes.get(view.token());
                    facade.acknowledgeVerifiedCheckpoint(view.token(),view.resultHash()).get(20,TimeUnit.SECONDS);
                    assertTrue(facade.runtimeVerificationHistory().stream().anyMatch(r->r.resultHash().equals(originalHash)),"Review must retain the original failed native truth");
                    reviewed.add(view.token());activity=System.nanoTime();
                }
                if(view.state().equals("HELD_UNVERIFIED")&&!view.reviewable()&&!view.reason().contains("WAITING")&&!view.reason().contains("DRAINING")&&System.nanoTime()-activity>TimeUnit.SECONDS.toNanos(15)) {
                    save(evidence.resolve("unavailable.json.gz"),Map.of("state",view.state(),"reason",view.reason(),"request",view.request(),"native",latest.toMap()));
                    unknowns.add(Map.of("state",view.state(),"reason",view.reason()));break;
                }
                if(settled.size()==2&&view.state().equals("RUNNING")&&System.nanoTime()-activity>TimeUnit.SECONDS.toNanos(6))break;
                Thread.sleep(100);
            }
            var ledger=VerifiedCheckpointsAuctionIT.sourceLedger(facade.ownedProducerStatus().runId());
            for(var entry:ledger)if(entry.get("kind").equals("RELEASE_RECEIPT"))receipts.put(Objects.toString(entry.get("ledgerSequence")),entry);
            if(latest!=null)save(evidence.resolve("final-native.json.gz"),latest.toMap());
            var result=new LinkedHashMap<String,Object>();result.put("method","UNCHANGED_EXTERNAL_AUCTION_WITHOUT_INPUT_AGENT_OR_EXTRA_OPERATIONS");result.put("sourceUnchanged",original.equals(VerifiedCheckpointsAuctionIT.sourceHashes(project.getParent())));result.put("sourceHashes",original);result.put("presentedCuts",presented.size());result.put("reviewedFailedCuts",reviewed.size());result.put("nonReviewedPresentedCuts",presented.size()-reviewed.size());result.put("settledScopes",settled);result.put("failures",failures);result.put("unavailable",unknowns);result.put("sourceReleaseReceipts",receipts);result.put("manualDesktopAcceptance",false);
            Files.write(evidence.resolve("audit-receipt.json"),CanonicalJson.encode(result));
            assertTrue((Boolean)result.get("sourceUnchanged"));assertTrue(unknowns.isEmpty(),"Unexpected required evidence gaps: "+unknowns);
            assertEquals(2,settled.size(),"Both source-defined Auction executions must reach a settled boundary");
            assertTrue(presented.size()>0);assertFalse(receipts.isEmpty(),"Need producer release receipts, not inferred approvals");
        }finally{try{presentation.close();}finally{facade.stopOwnedProducer();facade.close();}}
    }
}
