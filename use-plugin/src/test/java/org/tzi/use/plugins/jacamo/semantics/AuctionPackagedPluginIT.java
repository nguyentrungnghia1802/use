package org.tzi.use.plugins.jacamo.semantics;

import java.nio.file.*;
import java.util.concurrent.TimeUnit;
import org.jacamo.bridge.contract.CanonicalJson;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Separate cold P01 static preparation and E01 canonical headful live acceptance. */
class AuctionPackagedPluginIT {
    public static void main(String[] args)throws Exception {
        if(args.length==2&&args[0].equals("verify-existing")){verifyOriginalMarkings(Path.of(args[1]));System.out.println("INSTALLED_AUCTION_ORIGINAL_MARKINGS_PASS");return;}
        if(args.length==2&&args[0].equals("verify-controls-existing")){verifyControlledMarkings(Path.of(args[1]));System.out.println("AUCTION_CONTROLLED_ATTRIBUTION_PASS");return;}
        var tests=new AuctionPackagedPluginIT();
        if(Boolean.getBoolean("use.packaged.auction.controlled")){run(true,true,Boolean.getBoolean("use.auction.controlled.source"));System.out.println("FULL_ARCHIVE_AUCTION_CONTROLLED_PASS");return;}
        if(Boolean.getBoolean("use.packaged.auction.live")){tests.shippedCanonicalAuctionRunsThroughHeadfulWorkbenchNativeAndOriginalEngine();System.out.println("FULL_ARCHIVE_AUCTION_LIVE_PASS");}
        else {tests.shippedCanonicalAuctionImportsThroughColdInstalledPluginAndActualTcpModel();System.out.println("FULL_ARCHIVE_AUCTION_STATIC_PASS");}
    }
    @Test void shippedCanonicalAuctionImportsThroughColdInstalledPluginAndActualTcpModel()throws Exception {
        run(false);
    }
    @Test void shippedCanonicalAuctionRunsThroughHeadfulWorkbenchNativeAndOriginalEngine()throws Exception {
        run(true);
    }
    private static void run(boolean live)throws Exception {
        run(live,false);
    }
    @Test void controlledAuctionUsesActualActorsNativeAtPreAndExactReplay()throws Exception {run(true,true);}
    @Test void sourceControlledAuctionUsesSameNativeScenarioAndExactReplay()throws Exception {run(true,true,true);}
    private static void run(boolean live,boolean controlled)throws Exception {
        run(live,controlled,false);
    }
    private static void run(boolean live,boolean controlled,boolean source)throws Exception {
        String hostArchive=System.getProperty("use.packaged.hostArchive","");String frozen=hostArchive.isBlank()?"":sha256(Path.of(hostArchive));
        Path work=Files.createTempDirectory(Path.of("target"),live?"installed-auction-live-":"installed-auction-static-").toAbsolutePath();
        Path install=Use4GoalPackagedPluginIT.install(work),driver=Use4GoalPackagedPluginIT.driver(work,InstalledAuctionMain.class);
        Path variant=controlled?AuctionControlledCase.prepare(install.resolve("examples/auction-semantic"),work,true):null;
        try(var producer=new PackagedProjectProducer(install,work,0,true,controlled,variant)) {
            var properties=producer.consumerProperties(install,0);
            properties.setProperty("mapping",(controlled?variant:install.resolve("examples/auction-semantic")).resolve("auction_jacamo_to_csl_rules.xml").toString());
            properties.setProperty("use.jacamo.runtime.journal.maxBytes",controlled?"402653184":"268435456");
            properties.setProperty("controlled",Boolean.toString(controlled));
            if(controlled)properties.setProperty("fixture",work.resolve("fixture-control").toString());
            properties.setProperty("live",Boolean.toString(live));
            Path config=work.resolve("consumer.properties");try(var output=Files.newOutputStream(config)){properties.store(output,"Owned endpoint metadata; no secret contents");}
            var command=new java.util.ArrayList<String>(java.util.List.of(PackagedProjectProducer.java(),"-cp",
                    source?System.getProperty("java.class.path"):install.resolve("lib/use-gui.jar")+java.io.File.pathSeparator+driver,InstalledAuctionMain.class.getName()));
            if(source)command.add("--source");command.add(install.toString());command.add(config.toString());
            Path log=work.resolve("consumer.log");Process consumer=new ProcessBuilder(command)
                    .directory(install.toFile()).redirectErrorStream(true).redirectOutput(log.toFile()).start();
            boolean finished=consumer.waitFor(live?900:100,TimeUnit.SECONDS);if(!finished){consumer.destroyForcibly();consumer.waitFor(5,TimeUnit.SECONDS);}
            String output=Files.readString(log);assertTrue(finished,output);assertEquals(0,consumer.exitValue(),output);assertTrue(output.contains(source?"SOURCE_AUCTION_CONTROLLED_PASS":live?"INSTALLED_AUCTION_LIVE_PASS":"INSTALLED_AUCTION_STATIC_PASS"),output);
            var receipt=CanonicalJson.object(CanonicalJson.decode(Files.readAllBytes(work.resolve("consumer-receipt.json"))));
            assertEquals(source?"DEVELOPMENT_CONTROLLED_AUCTION_LIVE":controlled?"INSTALLED_CONTROLLED_AUCTION_LIVE":live?"INSTALLED_CANONICAL_AUCTION_LIVE":"INSTALLED_STATIC_IMPORT_ONLY",receipt.get("scope"));assertNotEquals(receipt.get("producerPid"),receipt.get("consumerPid"));
            assertEquals(controlled?402653184L:268435456L,((Number)receipt.get("journalLimit")).longValue());assertEquals(0,((Number)receipt.get("auctionObjects")).intValue());
            if(live){if(controlled)verifyControlledMarkings(work);else verifyOriginalMarkings(work);assertTrue(producer.process.waitFor(10,TimeUnit.SECONDS));assertEquals(0,producer.process.exitValue(),Files.readString(work.resolve("producer.log")));}
            if(!hostArchive.isBlank())assertEquals(frozen,sha256(Path.of(hostArchive)),"Full host archive must remain frozen through the actual run");
            var evidence=new java.util.TreeMap<String,Object>(java.util.Map.of("work",work.toString(),"receipt",receipt,"producerCommand",producer.command,"fullHostArchive",System.getProperty("use.packaged.hostArchive","PLUGIN_ZIP_PLUS_PINNED_HOST")));
            evidence.put("fullHostArchiveSha256",frozen);
            Files.write(Path.of(source?"target/auction-source-controlled.json":controlled?"target/auction-installed-controlled.json":live?"target/auction-installed-live.json":"target/auction-installed-static.json"),CanonicalJson.encode(evidence));
        }
    }
    private static String sha256(Path path)throws Exception {
        var digest=java.security.MessageDigest.getInstance("SHA-256");try(var input=Files.newInputStream(path)){byte[] buffer=new byte[65536];int read;while((read=input.read(buffer))!=-1)digest.update(buffer,0,read);}
        return java.util.HexFormat.of().formatHex(digest.digest());
    }
    private static void verifyControlledMarkings(Path work)throws Exception {
        var proof=CanonicalJson.object(CanonicalJson.decode(Files.readAllBytes(work.resolve("controlled-operations.json"))));
        var operations=((java.util.List<?>)proof.get("operations")).stream().map(CanonicalJson::object).toList();assertEquals(5,operations.size());
        var correlations=new java.util.HashSet<String>();for(var event:operations)assertTrue(correlations.add((String)event.get("correlationId")),"Each physical invocation must remain distinct");
        assertEquals(java.util.List.of("SUCCEEDED","SUCCEEDED","SUCCEEDED","SUCCEEDED","FAILED"),operations.stream().map(e->e.get("kind")).toList());
        assertEquals(java.util.List.of("bob","alice","maria","bob","giacomo"),operations.stream().map(e->CanonicalJson.object(e.get("after")).get("performerName")).toList());
        var accepted=CanonicalJson.object(CanonicalJson.decode(Files.readAllBytes(work.resolve("controlled-after.json"))));
        String artifact=(String)CanonicalJson.object(operations.getFirst().get("after")).get("artifactSemanticId");int monitors=0;
        for(Object raw:(java.util.List<?>)CanonicalJson.object(accepted.get("semantic")).get("constraints")) {
            var row=CanonicalJson.object(raw);if(!"INVARIANT_BODY".equals(row.get("kind"))||!"NATIVE:AuctionMonitor::BidEvidence".equals(row.get("id")))continue;
            monitors++;var witness=CanonicalJson.object(row.get("witness"));
            assertEquals(artifact.equals(witness.get("artifact"))?"Set{'alice','maria'}":"Set{}",witness.get("actualBidBy"),"Non-winning success is actual; failed/other-player calls cannot gain credit");
            assertEquals("true",witness.get("adminBidOverride"));assertEquals("SKIPPED",row.get("verdict"),"Internal MOISE timestamps remain unavailable");
        }
        assertEquals(2,monitors);
        var starts=new java.util.TreeMap<String,Long>();var stops=new java.util.TreeMap<String,Long>();
        for(Object raw:(java.util.List<?>)CanonicalJson.decode(Files.readAllBytes(work.resolve("source-task-evidence.json")))) {
            var row=CanonicalJson.object(raw);if(!"TASK_POST".equals(row.get("kind"))||!"PASS".equals(row.get("verdict")))continue;var witness=CanonicalJson.object(row.get("witness"));
            var target="A_start_t2".equals(witness.get("targetTaskId"))?starts:"A_decide_t2".equals(witness.get("targetTaskId"))?stops:null;
            if(target!=null)target.put((String)witness.get("scheme"),((Number)row.get("stateVersion")).longValue());
        }
        assertEquals(2,starts.size());assertEquals(starts.keySet(),stops.keySet());
        assertTrue(java.util.Collections.max(starts.values())<java.util.Collections.min(stops.values()),"Both actual auctions must start before either completes stop; two serialized runs cannot substitute interleaving");
        Files.write(work.resolve("controlled-attribution-acceptance.json"),CanonicalJson.encode(java.util.Map.of("status","PASS","scope","ACTUAL_ACCEPTED_NATIVE_CONTROLLED_CUT","actualBidPlayers",java.util.List.of("alice","maria"),"failedClosedBidPlayer","giacomo","distinctInvocations",correlations,"stateHash",accepted.get("stateHash"),"resultHash",accepted.get("resultHash"),"originalStartVersions",starts,"originalStopVersions",stops)));
    }
    /** Verifies actual durable native/core evidence; does not rerun or simulate the producer. */
    private static void verifyOriginalMarkings(Path work)throws Exception {
        var accepted=CanonicalJson.object(CanonicalJson.decode(Files.readAllBytes(work.resolve("accepted.json"))));
        var semantic=CanonicalJson.object(accepted.get("semantic"));
        var core=CanonicalJson.object(CanonicalJson.object(semantic.get("observation")).get("originalEngine"));
        assertEquals("DERIVED_AND_RESOURCE_V1",core.get("engineProfile"));assertEquals("OBSERVED_PREFIX",core.get("historyQuality"));
        var fulfilled=new java.util.TreeSet<String>();var goals=new java.util.TreeSet<String>();var schemes=new java.util.TreeSet<String>();
        var goalIds=java.util.Set.of("O_root","O_start","O_bid","O_decide","A_start","A_decide","P_bid");
        for(Object raw:(java.util.List<?>)core.get("contexts")) {
            var context=CanonicalJson.object(raw);schemes.add((String)context.get("scheme"));
            for(Object value:CanonicalJson.object(context.get("occurrences")).values()) {
                var row=CanonicalJson.object(value);String source=(String)row.get("sourceElement");
                if("FULFILLED".equals(row.get("leafStatus")))fulfilled.add(source);
                if(goalIds.contains(source)&&row.containsKey("aggregateStatus"))goals.add(source);
            }
        }
        assertEquals(2,schemes.size());assertEquals(goalIds,goals,"Original derived Goal statuses must be present");
        assertTrue(fulfilled.containsAll(java.util.Set.of("A_start_t1","A_start_t2","A_decide_t1","A_decide_t2","P_bid_t1")),"Actual native truth must reach original raw TaskMarking: "+fulfilled);
        var failureProof=new java.util.ArrayList<Object>();
        var taskRows=(java.util.List<?>)CanonicalJson.decode(Files.readAllBytes(work.resolve("source-task-evidence.json")));
        var constraints=((java.util.List<?>)semantic.get("constraints")).stream().map(CanonicalJson::object).toList();
        for(Object value:taskRows) {
            var task=CanonicalJson.object(value);var witness=CanonicalJson.object(task.get("witness"));
            if(!"TASK_POST".equals(task.get("kind"))||!"FAIL".equals(task.get("verdict"))||!"P_bid_t2".equals(witness.get("targetTaskId")))continue;
            String player=(String)witness.get("player"),scheme=(String)witness.get("scheme");String name=org.jacamo.bridge.contract.BridgeEntityId.parse(player).localId();
            var norm=constraints.stream().filter(c->"INVARIANT_BODY".equals(c.get("kind"))&&"NATIVE:AuctionObligation::WithinDeadline".equals(c.get("id")))
                    .filter(c->{var w=CanonicalJson.object(c.get("witness"));return scheme.equals(w.get("scheme"))&&name.equals(w.get("playerName"))&&"'bid'".equals(w.get("goal"));}).findFirst().orElseThrow();
            var n=CanonicalJson.object(norm.get("witness"));assertEquals("FAIL",norm.get("verdict"));assertEquals("UNFULFILLED",n.get("sourceNormState"));assertEquals("true",n.get("unfulfilled"));
            var times=CanonicalJson.object(CanonicalJson.decode(n.get("rawTimes").toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            var created=new java.math.BigInteger(times.get("created").toString());var deadline=new java.math.BigInteger(times.get("deadline").toString());
            // NPL samples created and the relative deadline separately. Preserve both
            // actual timestamps; the frozen OCL duration predicate is evaluated unchanged.
            assertEquals(new java.math.BigInteger(n.get("deadlineAt").toString()).subtract(new java.math.BigInteger(n.get("activatedAt").toString())),deadline.subtract(created));
            assertTrue(new java.math.BigInteger(times.get("ended").toString()).compareTo(deadline)>=0);
            var monitor=constraints.stream().filter(c->"INVARIANT_BODY".equals(c.get("kind"))&&"NATIVE:AuctionMonitor::BidEvidence".equals(c.get("id")))
                    .filter(c->scheme.equals(CanonicalJson.object(c.get("witness")).get("scheme"))).findFirst().orElseThrow();
            var m=CanonicalJson.object(monitor.get("witness"));assertEquals("true",m.get("adminBidOverride"));
            assertFalse(m.get("actualBidBy").toString().contains("'"+name+"'"),"Source failure/admin cannot create an actual bid");
            var context=((java.util.List<?>)core.get("contexts")).stream().map(CanonicalJson::object).filter(c->scheme.equals(c.get("scheme"))).findFirst().orElseThrow();
            var occurrences=CanonicalJson.object(context.get("occurrences")).values().stream().map(CanonicalJson::object).toList();
            var leaf=occurrences.stream().filter(r->"P_bid_t2".equals(r.get("sourceElement"))&&player.equals(r.get("player"))).findFirst().orElseThrow();
            assertEquals("PENDING",leaf.get("leafStatus"),"Original historical Task status is separate from source failure");
            var aggregate=occurrences.stream().filter(r->"O_bid".equals(r.get("sourceElement"))&&"SOURCE_ELEMENT".equals(r.get("expansionKind"))).findFirst().orElseThrow();
            assertNotEquals("FULFILLED",aggregate.get("aggregateStatus"),"AND/dependency cannot borrow another player's successful bid");
            failureProof.add(java.util.Map.of("player",player,"scheme",scheme,"rawTask",leaf.get("leafStatus"),"aggregateBid",aggregate.get("aggregateStatus"),"normVerdict",norm.get("verdict"),"sourceNormState",n.get("sourceNormState"),"rawTimes",times,"admin",true,"actualBid",false));
        }
        var raw=new java.util.TreeMap<String,Object>();raw.putAll(java.util.Map.of("status","PASS","scope","ACTUAL_INSTALLED_ACCEPTED_NATIVE_CORE_CUT","schemes",schemes,"rawFulfilledLeaves",fulfilled,"derivedGoalStatuses",goals,"classHashes",core.get("classHashes"),"stateHash",accepted.get("stateHash"),"resultHash",accepted.get("resultHash")));
        raw.put("actualRandomFailureProof",failureProof);raw.put("conditionalFailureCheck","No failure is invented when the actual random run has none");
        Files.write(work.resolve("original-marking-acceptance.json"),CanonicalJson.encode(raw));
    }
}
