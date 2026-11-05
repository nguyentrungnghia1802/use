package org.tzi.use.plugins.jacamo;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.jacamo.bridge.contract.*;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.*;
import org.tzi.use.plugins.jacamo.semantics.evidence.*;
import org.tzi.use.main.Session;
import static org.junit.jupiter.api.Assertions.*;

/** Manifest-selected live audit. No injected operations, verdicts, seeds or GUI windows. */
public final class AuthoredCaseVerificationAudit {
    static void dump(DefaultJaCaMoFacade facade,Path evidence,String prefix)throws Exception {
        for(long pid:List.of(facade.ownedProducerStatus().pid(),ProcessHandle.current().pid())) {
            Path file=evidence.resolve(prefix+(pid==ProcessHandle.current().pid()?"-consumer-threads.txt":"-producer-threads.txt"));
            var command=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","jcmd.exe").toString(),Long.toString(pid),"Thread.print","-l")
                    .redirectErrorStream(true).redirectOutput(file.toFile()).start();
            if(!command.waitFor(15,TimeUnit.SECONDS))command.destroy();
        }
    }
    static Map<String,String> goals(VerificationSnapshot snapshot) {
        var result=new TreeMap<String,String>();if(snapshot.result()==null||snapshot.result().semantic()==null)return result;
        var core=CanonicalJson.object(snapshot.result().semantic().observation().getOrDefault("originalEngine",Map.of()));
        for(Object raw:(List<?>)core.getOrDefault("contexts",List.of())) {
            var context=CanonicalJson.object(raw);
            for(Object value:CanonicalJson.object(context.getOrDefault("occurrences",Map.of())).values()) {
                var occurrence=CanonicalJson.object(value);
                result.put(context.get("context")+" / "+occurrence.get("sourceElement"),Objects.toString(occurrence.get("aggregateStatus")));
            }
        }
        return result;
    }
    public static void main(String[] args) {
        try{run(Path.of(args[0]),Path.of(args[1]));System.out.println("AUTHORED_CASE_VERIFICATION_AUDIT_PASS");System.exit(0);}
        catch(Throwable failure){failure.printStackTrace();System.exit(1);}
    }
    static void run(Path models,Path evidence)throws Exception {
        models=models.toRealPath();Files.createDirectories(evidence);Path checkout=Path.of("..").toRealPath();
        var manifest=CanonicalJson.object(CanonicalJson.decode(Files.readAllBytes(models.resolve("case-manifest.json"))));
        var inputs=CanonicalJson.object(manifest.get("inputs"));
        var original=VerifiedCheckpointsAuctionIT.sourceHashes(models);
        var host=new Session();var facade=new DefaultJaCaMoFacade(checkout,host);facade.verifiedCheckpointMode(true);facade.nextJournalBudgetBytes(536870912);
        var cuts=new ConcurrentHashMap<String,Map<String,VerifiedCheckpointProtocol.Check>>();
        var cutHashes=new ConcurrentHashMap<String,String>();var operations=new ConcurrentHashMap<String,Object>();
        var failures=new ArrayList<Object>();var gaps=new ArrayList<Object>();var reviewed=new HashSet<String>();
        var ledgerSummary=new ArrayList<Object>();boolean reached=false;String last="",dumped="";long activity=System.nanoTime();
        AutoCloseable presentation=facade.registerCheckpointPresenter((view,cut,checks)->{
            try {
                assertEquals(cut.result().resultHash(),RuntimeVerificationResult.fromMap(cut.result().toMap()).resultHash());
                NaturalAuctionVerificationAudit.save(evidence.resolve("cut-"+view.token()+".json.gz"),Map.of("view",Map.of("token",view.token(),"checkpoint",view.checkpoint(),"reason",view.reason()),
                        "native",cut.toMap(),"checks",SemanticJson.tree(checks),"identity",view.request().get("identity"),"barrier",view.request().get("barrier")));
                for(var entry:facade.runtimeHistoryTail().entries())if(entry.result().semantic()!=null)for(var row:entry.result().semantic().constraints())
                    if(Set.of("PRE","POST").contains(row.kind())&&!row.invocation().isBlank()) {
                        var witness=new TreeMap<String,String>();row.witness().forEach((key,value)->{if(key.startsWith("before.")||key.startsWith("after.")||key.equals("observationQuality"))witness.put(key,value);});
                        operations.put(row.id()+":"+row.kind()+":"+row.invocation(),Map.of("id",row.id(),"kind",row.kind(),"verdict",row.verdict().name(),"invocation",row.invocation(),"expression",row.expression(),"witness",witness));
                    }
                cutHashes.put(view.token(),cut.result().resultHash());cuts.put(view.token(),Map.copyOf(checks));
                return CompletableFuture.completedFuture(null);
            }catch(Throwable error){return CompletableFuture.failedFuture(error);}
        });
        try {
            var selection=new JaCaMoFacade.ImportSelection("JACAMO_DECLARATIVE_XML",models.resolve((String)inputs.get("mapping")),models.resolve((String)inputs.get("ocl")),models.resolve((String)inputs.get("csl")),models.resolve((String)inputs.get("istar")),false);
            facade.prepareOwnedProducer(models.resolve((String)inputs.get("jcm")),selection,checkout.resolve("jacamo-bridge-jacamo/target/jacamo-bridge-classpath.txt"));
            var system=host.system();assertNotNull(system);facade.startRuntime();long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(420);
            while(System.nanoTime()<end) {
                assertSame(system,host.system());var view=facade.verifiedCheckpointState();
                if(!view.token().isBlank()&&!view.token().equals(last)){last=view.token();activity=System.nanoTime();System.out.println("SOURCE_CUT "+view.token()+" "+view.checkpoint()+" "+view.state());}
                if(!view.token().isBlank()&&!view.state().equals("RUNNING")&&!dumped.equals(view.token())&&System.nanoTime()-activity>TimeUnit.SECONDS.toNanos(15)) {
                    dumped=view.token();dump(facade,evidence,"stalled-"+view.token());
                }
                if(view.reviewable()&&!reviewed.contains(view.token())) {
                    var checks=cuts.get(view.token());assertNotNull(checks);
                    NaturalAuctionVerificationAudit.save(evidence.resolve("hold-"+view.token()+".json.gz"),Map.of("state",view.state(),"reason",view.reason(),"checks",SemanticJson.tree(checks),"native",facade.verificationSnapshot().toMap()));
                    boolean unknown=checks.values().stream().anyMatch(c->c.applicability().equals("APPLICABLE")&&!Set.of("PASS","FAIL").contains(c.truth()));
                    var detail=Map.of("token",view.token(),"checkpoint",view.checkpoint(),"state",view.state(),"reason",view.reason(),"checks",SemanticJson.tree(checks));
                    if(unknown){gaps.add(detail);break;}
                    assertTrue(checks.values().stream().anyMatch(c->c.applicability().equals("APPLICABLE")&&c.truth().equals("FAIL")));
                    failures.add(detail);Thread.sleep(300);assertEquals(view.token(),facade.verifiedCheckpointState().token());
                    facade.acknowledgeVerifiedCheckpoint(view.token(),view.resultHash()).get(20,TimeUnit.SECONDS);
                    assertTrue(facade.runtimeVerificationHistory().stream().anyMatch(r->r.resultHash().equals(cutHashes.get(view.token()))));
                    reviewed.add(view.token());activity=System.nanoTime();
                }
                if(view.state().equals("HELD_UNVERIFIED")&&!view.reviewable()&&!view.reason().contains("WAITING")&&!view.reason().contains("DRAIN")&&System.nanoTime()-activity>TimeUnit.SECONDS.toNanos(20)) {
                    gaps.add(Map.of("token",view.token(),"state",view.state(),"reason",view.reason()));break;
                }
                var status=goals(facade.verificationSnapshot());
                if(!status.isEmpty()&&status.values().stream().allMatch("FULFILLED"::equals)&&view.state().equals("RUNNING")&&System.nanoTime()-activity>TimeUnit.SECONDS.toNanos(6)){reached=true;break;}
                Thread.sleep(100);
            }
            if(!gaps.isEmpty()||!reached)dump(facade,evidence,"failed");
            var snapshot=facade.verificationSnapshot();NaturalAuctionVerificationAudit.save(evidence.resolve("final-native.json.gz"),snapshot.toMap());
            String run=facade.ownedProducerStatus().runId();
            for(var record:VerifiedCheckpointsAuctionIT.sourceLedger(run))if(Set.of("RELEASE_RECEIPT","ACKNOWLEDGED","DECISION").contains(record.get("kind")))ledgerSummary.add(record);
            var summary=new TreeMap<String,Object>();summary.put("method","MANIFEST_SELECTED_UNCHANGED_SOURCE_NATIVE_CHECKPOINTS");summary.put("inputs",inputs);summary.put("producerRun",run);
            summary.put("presentedCuts",cuts.size());summary.put("reviewedFailures",failures);summary.put("requiredEvidenceGaps",gaps);summary.put("nativeGoals",goals(snapshot));summary.put("nativeOutcomesReached",reached);
            summary.put("sourceUnchanged",original.equals(VerifiedCheckpointsAuctionIT.sourceHashes(models)));summary.put("sourceHashes",original);summary.put("sourceDecisions",ledgerSummary);summary.put("operationChecks",operations.values());summary.put("manualDesktopAcceptance",false);
            Files.write(evidence.resolve("audit-receipt.json"),CanonicalJson.encode(summary));
            assertTrue((Boolean)summary.get("sourceUnchanged"));assertTrue(gaps.isEmpty(),"Required evidence unavailable: "+gaps);assertTrue(reached,"Native outcome not reached; "+summary.get("nativeGoals"));assertFalse(cuts.isEmpty());
            assertTrue(operations.values().stream().map(CanonicalJson::object).anyMatch(r->r.get("kind").equals("PRE")&&r.get("verdict").equals("PASS")),"Need a real native operation PRE");
            assertTrue(operations.values().stream().map(CanonicalJson::object).anyMatch(r->r.get("kind").equals("POST")&&r.get("verdict").equals("PASS")),"Need a real native operation POST");
        }finally{try{presentation.close();}finally{facade.stopOwnedProducer();facade.close();}}
    }
}
