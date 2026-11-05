package org.tzi.use.plugins.jacamo;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.jacamo.bridge.contract.*;
import org.junit.jupiter.api.Test;
import org.tzi.use.main.Session;

/** External Auction with unchanged sources/models and public operation inputs;
 * no injected verdict/constraint or repaired state. */
public class VerifiedCheckpointsAuctionIT {
    @Test void unchangedExternalAuctionUsesSourceHoldsAndNativeVerification()throws Exception {
        Path evidence=Path.of("verification/verified-checkpoints/live-auction-"+System.currentTimeMillis()).toAbsolutePath();Files.createDirectories(evidence);
        Path agent=evidence.resolve("source-evidence-input.jar");var manifest=new java.util.jar.Manifest();manifest.getMainAttributes().putValue("Manifest-Version","1.0");manifest.getMainAttributes().putValue("Premain-Class",AuctionEvidenceInputAgent.class.getName());
        try(var jar=new java.util.jar.JarOutputStream(Files.newOutputStream(agent),manifest)) {
            for(String type:List.of(AuctionEvidenceInputAgent.class.getName(),AuctionEvidenceInputAgent.class.getName()+"$Input")) {
                String resource=type.replace('.','/')+".class";
                try(var input=getClass().getClassLoader().getResourceAsStream(resource)){jar.putNextEntry(new java.util.jar.JarEntry(resource));jar.write(input.readAllBytes());jar.closeEntry();}
            }
        }
        var builder=new ProcessBuilder(JavaProcessSupport.executable(),"-Djava.awt.headless=true","-Duse.jacamo.runtime.journal.maxBytes=536870912","-cp",System.getProperty("java.class.path"),getClass().getName(),evidence.toString()).redirectErrorStream(true).redirectOutput(evidence.resolve("consumer.log").toFile());
        builder.environment().put("JAVA_TOOL_OPTIONS",Objects.toString(builder.environment().get("JAVA_TOOL_OPTIONS"),"")+" \"-javaagent:"+agent+"="+evidence+"\"");
        var process=builder.start();
        try {assertTrue(process.waitFor(480,TimeUnit.SECONDS),"Auction timeout; "+evidence);assertEquals(0,process.exitValue(),Files.readString(evidence.resolve("consumer.log")));}
        finally{if(process.isAlive()){process.destroy();if(!process.waitFor(5,TimeUnit.SECONDS))process.destroyForcibly();}}
    }
    public static void main(String[] args) {
        try{run(Path.of(args[0]));System.out.println("VERIFIED_CHECKPOINT_AUCTION_PASS");System.exit(0);}
        catch(Throwable failed){failed.printStackTrace();System.exit(1);}
    }
    static void run(Path evidence)throws Exception {
        Path checkout=Path.of("..").toRealPath();Path original=checkout.getParent().resolve("jacamo/examples/auction/auction.jcm");
        var hashes=sourceHashes(original.getParent());Path models=Path.of("examples/auction").toRealPath();
        var selection=new JaCaMoFacade.ImportSelection("JACAMO_DECLARATIVE_XML",models.resolve("auction-mapping.xml"),models.resolve("auction.ocl"),models.resolve("auction.csl"),models.resolve("auction.istar"),false);
        var session=new Session();var facade=new DefaultJaCaMoFacade(checkout,session);facade.verifiedCheckpointMode(true);facade.nextJournalBudgetBytes(536870912);
        var observed=new LinkedHashMap<String,Object>();int reviews=0,failures=0,automatic=0;String previousToken="";boolean lastHeld=false,reconnected=false;
        var recordings=new ArrayList<Path>();
        try {
            facade.prepareOwnedProducer(original,selection,checkout.resolve("jacamo-bridge-jacamo/target/jacamo-bridge-classpath.txt"));
            assertNotNull(session.system());facade.startRuntime();
            long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(240);long lastActivity=System.nanoTime();
            while(System.nanoTime()<end) {
                var view=facade.verifiedCheckpointState();
                if(!view.token().isBlank()&&!view.token().equals(previousToken)) {
                    if(!previousToken.isBlank()&&!lastHeld)automatic++;
                    previousToken=view.token();lastHeld=false;lastActivity=System.nanoTime();
                    System.out.println("SOURCE_CHECKPOINT token="+view.token()+" checkpoint="+view.checkpoint()+" state="+view.state()+" reason="+view.reason());
                }
                if(automatic>=1&&!Files.exists(evidence.resolve("request-evidence-input")))Files.writeString(evidence.resolve("request-evidence-input"),"Actual AuctionArtifact.stop() twice as an existing source performer; no model, native state or result mutation");
                if(view.reviewable()) {
                    if(view.checkpoint().endsWith(":A_start_t1"))Files.write(evidence.resolve("initial-held.json"),CanonicalJson.encode(Map.of("state",view.state(),"reason",view.reason(),"request",view.request(),"native",facade.verificationSnapshot().toMap())));
                    assertFalse(view.checkpoint().endsWith(":A_start_t1"),"Both concurrent creation Tasks must drain and verify without an initialization hold");
                    System.out.println("NATIVE_HELD token="+view.token()+" result="+view.resultHash()+" state="+view.state()+" reason="+view.reason());
                    var originalCut=facade.verificationSnapshot();var originalTruth=originalCut.result()==null?"":originalCut.result().resultHash();
                    Files.write(evidence.resolve("native-"+view.token()+".json"),CanonicalJson.encode(originalCut.toMap()));
                    // Keep each full immutable cut in its native evidence file;
                    // do not duplicate all snapshots into one bounded JSON frame.
                    observed.put(view.token(),Map.of("state",view.state(),"reason",view.reason(),"resultHash",view.resultHash(),"useHash",view.useHash(),
                            "requestIdentity",view.request().get("identity"),"checkpointId",view.request().get("checkpointId"),
                            "sourceOccurrences",view.request().get("sourceOccurrences"),"nativeEvidence","native-"+view.token()+".json"));
                    if(view.state().equals("HELD_FAILURE"))failures++;
                    Thread.sleep(300);assertEquals(view.token(),facade.verifiedCheckpointState().token());
                    assertEquals(view.resultHash(),facade.verifiedCheckpointState().resultHash());
                    if(!reconnected&&view.state().equals("HELD_FAILURE")) {
                        Path beforeDisconnect=evidence.resolve("replay-before-disconnect");facade.exportRuntimeReplay(beforeDisconnect);recordings.add(beforeDisconnect);
                        var intact=facade.replayRuntime(beforeDisconnect);writeReplayReport(beforeDisconnect.resolve("report.json"),intact);assertTrue(intact.complete(),intact.diagnostics().toString());
                        facade.disconnectRuntime();Thread.sleep(500);facade.connectRuntime();
                        long recovery=System.nanoTime()+TimeUnit.SECONDS.toNanos(35);
                        while(!facade.verifiedCheckpointState().reviewable()&&System.nanoTime()<recovery)Thread.sleep(50);
                        var recovered=facade.verifiedCheckpointState();assertTrue(recovered.reviewable(),recovered.reason());
                        assertEquals(view.token(),recovered.token());assertEquals(view.resultHash(),recovered.resultHash());assertEquals(view.useHash(),recovered.useHash());
                        Files.write(evidence.resolve("disconnect-reconnect.json"),CanonicalJson.encode(Map.of("token",recovered.token(),"originalFailedResult",view.resultHash(),"reconciledResult",recovered.resultHash(),"state",recovered.state())));
                        Path interrupted=evidence.resolve("replay-interrupted-monitor");facade.exportRuntimeReplay(interrupted);recordings.add(interrupted);
                        facade.stopMonitorSession();Thread.sleep(400);facade.restartMonitorSession();
                        recovery=System.nanoTime()+TimeUnit.SECONDS.toNanos(35);
                        while(!facade.verifiedCheckpointState().reviewable()&&System.nanoTime()<recovery)Thread.sleep(50);
                        var restarted=facade.verifiedCheckpointState();assertTrue(restarted.reviewable(),restarted.reason());
                        assertEquals(view.token(),restarted.token());assertEquals(view.resultHash(),restarted.resultHash());
                        assertTrue(facade.runtimeVerificationHistory().stream().anyMatch(r->r.resultHash().equals(originalTruth)),"Monitor restart must retain reviewed original native truth");
                        Files.write(evidence.resolve("monitor-stop-restart.json"),CanonicalJson.encode(Map.of("token",restarted.token(),"originalFailedResult",view.resultHash(),"reconciledResult",restarted.resultHash(),"state",restarted.state())));
                        reconnected=true;
                    }
                    facade.acknowledgeVerifiedCheckpoint(view.token(),view.resultHash()).get(20,TimeUnit.SECONDS);
                    reviews++;lastHeld=true;lastActivity=System.nanoTime();
                    assertTrue(facade.runtimeVerificationHistory().stream().anyMatch(r->r.resultHash().equals(originalTruth)),"Reviewed original native result must remain in history after release");
                }
                if(view.state().equals("HELD_UNVERIFIED")&&!view.reviewable()&&!view.reason().contains("WAITING")&&!view.reason().contains("DRAINING")) {
                    System.out.println("UNAVAILABLE token="+view.token()+" reason="+view.reason());
                    Files.write(evidence.resolve("unavailable.json"),CanonicalJson.encode(Map.of("reason",view.reason(),"request",view.request(),"snapshot",facade.verificationSnapshot().toMap())));
                    if(System.nanoTime()-lastActivity>TimeUnit.SECONDS.toNanos(12))throw new AssertionError("Native cut unavailable: "+view.reason());
                }
                if(System.nanoTime()-lastActivity>TimeUnit.SECONDS.toNanos(15)&&observed.size()+automatic>4)break;
                Thread.sleep(100);
            }
            Path replay=evidence.resolve("replay");facade.exportRuntimeReplay(replay);recordings.add(replay);
            var approvals=new HashSet<String>();var acknowledgements=new HashSet<String>();
            for(Path recording:recordings)try(var lines=Files.lines(recording.resolve("runtime.jsonl"))) {
                for(String line:(Iterable<String>)lines::iterator) {
                    var entry=CanonicalJson.object(CanonicalJson.decode(line.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                    if(!"CHECKPOINT_AUDIT".equals(entry.get("kind")))continue;
                    var audit=CanonicalJson.object(CanonicalJson.object(entry.get("payload")).get("checkpointAudit"));
                    if(!audit.containsKey("receipt"))continue;
                    var receiptRow=CanonicalJson.object(audit.get("receipt"));var command=CanonicalJson.object(audit.get("command"));
                    if(Boolean.TRUE.equals(receiptRow.get("released"))) {
                        if("APPROVE".equals(command.get("action")))approvals.add((String)command.get("commandId"));
                        if("ACKNOWLEDGE_AND_CONTINUE".equals(command.get("action")))acknowledgements.add((String)command.get("commandId"));
                    }
                }
            }
            var offline=facade.replayRuntime(replay);
            writeReplayReport(evidence.resolve("replay-report.json"),offline);
            var interruptedReport=facade.replayRuntime(evidence.resolve("replay-interrupted-monitor"));writeReplayReport(evidence.resolve("replay-interrupted-report.json"),interruptedReport);
            var interruptedManifest=CanonicalJson.object(CanonicalJson.decode(Files.readAllBytes(evidence.resolve("replay-interrupted-monitor/manifest.json"))));
            assertEquals(((Number)interruptedManifest.get("entries")).intValue(),interruptedReport.checkedEntries());
            assertFalse(interruptedReport.complete(),"Intentional observation disconnect must stay marked as a coverage gap");assertEquals(List.of("REPLAY_PARTIAL_COVERAGE_GAP"),interruptedReport.diagnostics());
            var receipt=Map.of("distinctHeldCuts",observed.size(),"reviewedCuts",reviews,"genuineNativeFailedCuts",failures,"automaticCuts",approvals.size(),"reviewedReleaseReceipts",acknowledgements.size(),"held",observed,"originalAuctionUnchanged",hashes.equals(sourceHashes(original.getParent())),"producer",facade.ownedProducerStatus().runId());
            Files.write(evidence.resolve("live-receipt.json"),CanonicalJson.encode(receipt));
            assertTrue(hashes.equals(sourceHashes(original.getParent())),"External source changed");
            assertTrue(observed.size()+approvals.size()>4,"Repeated source checkpoints must occur");assertTrue(approvals.size()>0,"Qualified native PASS must continue automatically");
            assertEquals(reviews,acknowledgements.size(),"Every reviewed release needs its producer receipt");
            assertTrue(reviews>=2,"Review must work for at least two distinct exact checkpoint tokens");
            var finalManifest=CanonicalJson.object(CanonicalJson.decode(Files.readAllBytes(replay.resolve("manifest.json"))));
            assertEquals(((Number)finalManifest.get("entries")).intValue(),offline.checkedEntries());
            assertFalse(offline.complete(),"The retained journal must preserve its intentional disconnect gap");assertEquals(List.of("REPLAY_PARTIAL_COVERAGE_GAP"),offline.diagnostics());
            assertTrue(failures>0,"Need a genuine unchanged-model failure, not an injected verdict");
            assertTrue(Files.isRegularFile(evidence.resolve("evidence-input-operations.txt")),"Need the actual public source-operation receipts");
            long producerPid=facade.ownedProducerStatus().pid();String run=facade.ownedProducerStatus().runId();
            long ledgerBefore=sourceLedger(run).stream().mapToLong(e->((Number)e.get("ledgerSequence")).longValue()).max().orElse(0);
            facade.close();Thread.sleep(600);
            assertTrue(ProcessHandle.of(producerPid).map(ProcessHandle::isAlive).orElse(false),"Closing the workbench must retain the strict producer hold; explicit Stop owns termination");
            var afterClose=sourceLedger(run).stream().filter(e->((Number)e.get("ledgerSequence")).longValue()>ledgerBefore).toList();
            assertTrue(afterClose.stream().anyMatch(e->e.get("kind").equals("HOLD_UNVERIFIED")),"Source must persist the observer-loss hold");
            assertFalse(afterClose.stream().anyMatch(e->Set.of("RELEASE_RECEIPT","STOP").contains(e.get("kind"))),"Workbench close must neither release nor terminate the cut");
            Files.write(evidence.resolve("workbench-close.json"),CanonicalJson.encode(Map.of("producerPid",producerPid,"producerAliveAfterMonitorClose",true,"sourceRecords",afterClose,"explicitStopFollowsInFinally",true)));
        }finally{facade.stopOwnedProducer();facade.close();}
    }
    static void writeReplayReport(Path output,org.tzi.use.plugins.jacamo.codegrounded.runtime.NativeRuntimeReplay.ReplayReport report)throws Exception {
        Files.write(output,CanonicalJson.encode(Map.of("complete",report.complete(),"checkedEntries",report.checkedEntries(),"diagnostics",report.diagnostics(),"finalStateHash",report.finalStateHash(),"finalResultHash",report.finalResultHash())));
    }
    static List<Map<String,Object>> sourceLedger(String run)throws Exception {
        Path root=Path.of(System.getProperty("java.io.tmpdir"),"jacamo-bridge-spool");
        try(var paths=Files.list(root)) {
            for(Path file:paths.filter(p->p.getFileName().toString().startsWith("verified-checkpoints-")&&p.toString().endsWith(".frames")).sorted(Comparator.comparingLong((Path p)->p.toFile().lastModified()).reversed()).toList()) {
                var records=new ArrayList<Map<String,Object>>();
                try(var input=new java.io.DataInputStream(Files.newInputStream(file))) {
                    while(input.available()>0) {
                        int size=input.readInt();byte[] hash=input.readNBytes(32),frame=input.readNBytes(size);if(frame.length!=size)break;
                        assertArrayEquals(hash,java.security.MessageDigest.getInstance("SHA-256").digest(frame));
                        var record=CanonicalJson.object(CanonicalJson.decode(frame,CanonicalJson.DEFAULT_MAX_BYTES,CanonicalJson.DEFAULT_MAX_DEPTH,CanonicalJson.DEFAULT_MAX_BYTES));
                        if(records.isEmpty()&&!run.equals(record.get("run")))break;
                        if(record.containsKey("ledgerSequence")) {
                            var summary=new LinkedHashMap<>(record);
                            var details=new LinkedHashMap<>(CanonicalJson.object(record.get("details")));details.remove("snapshotBase64");summary.put("details",details);
                            records.add(Collections.unmodifiableMap(summary));
                        }
                    }
                }
                if(!records.isEmpty())return List.copyOf(records);
            }
        }
        throw new IllegalStateException("SOURCE_CHECKPOINT_LEDGER_NOT_FOUND:"+run);
    }
    static Map<String,String> sourceHashes(Path root)throws Exception {
        var result=new TreeMap<String,String>();try(var files=Files.walk(root)){for(Path file:files.filter(Files::isRegularFile).filter(p->!root.relativize(p).toString().startsWith("build")).toList())result.put(root.relativize(file).toString(),DistributionFingerprint.digestPath(file));}return result;
    }
}
