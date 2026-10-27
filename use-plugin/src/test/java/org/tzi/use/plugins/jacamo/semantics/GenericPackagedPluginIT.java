package org.tzi.use.plugins.jacamo.semantics;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.jacamo.bridge.contract.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/** Reuses the cold host/Workbench scenario, with only project/XML/authored OCL
 * staged as inputs and the production neutral provider bootstrap. */
class GenericPackagedPluginIT {
    @Test @Timeout(120) void installedCaseBReplaysGeneratedModelsNativeM0AndPartialOclWithoutACaseJavaMapper()throws Exception {
        Path work=Files.createTempDirectory(Path.of("target"),"installed-declarative-case-b-").toAbsolutePath();
        Path recording=org.tzi.use.plugins.jacamo.semantics.xml.GenericMappingArchitectureTest.caseBRecording(work.resolve("fixture"));
        Path install=Use4GoalPackagedPluginIT.install(work),driver=Use4GoalPackagedPluginIT.driver(work,PackagedCanonicalReplayMain.class);
        String archive=System.getProperty("use.packaged.hostArchive","");String archiveHash=archive.isBlank()?"":hash(Path.of(archive));
        var manifest=CanonicalJson.object(CanonicalJson.decode(Files.readAllBytes(recording.resolve("manifest.json"))));
        var program=CanonicalJson.object(CanonicalJson.decode(Files.readAllBytes(recording.resolve("program.json"))));
        assertTrue(program.containsKey("declarative"));assertFalse(program.containsKey("case"));
        var command=List.of(PackagedProjectProducer.java(),"-cp",install.resolve("lib/use-gui.jar")+java.io.File.pathSeparator+driver,
                PackagedCanonicalReplayMain.class.getName(),install.toString(),recording.toString(),manifest.get("finalStateHash").toString(),manifest.get("finalResultHash").toString(),
                "Pallet.allInstances()->one(p | p.weight.oclIsUndefined())");
        Path log=work.resolve("consumer.log");var process=new ProcessBuilder(command).directory(install.toFile()).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        boolean finished=process.waitFor(90,TimeUnit.SECONDS);if(!finished){process.destroyForcibly();process.waitFor(5,TimeUnit.SECONDS);}
        assertTrue(finished,Files.readString(log));assertEquals(0,process.exitValue(),Files.readString(log));
        if(!archive.isBlank())assertEquals(archiveHash,hash(Path.of(archive)));
        Files.write(Path.of("target/generic-installed-case-b.json"),CanonicalJson.encode(Map.of("status","PASS","scope","INSTALLED_DECLARATIVE_COMPONENT_REPLAY_NOT_LIVE_CASE_B",
                "work",work.toString(),"programHash",program.get("programHash"),"cslHash",program.get("cslHash"),"goalHash",program.get("goalHash"),"command",command,
                "stateHash",manifest.get("finalStateHash"),"resultHash",manifest.get("finalResultHash"),"fullHostArchiveSha256",archiveHash)));
    }
    @Test @Timeout(1000) void installedDeclarativeAuctionUsesGeneratedModelsNeutralProvidersAndExactReplay()throws Exception {
        Path work=Files.createTempDirectory(Path.of("target"),"installed-declarative-live-").toAbsolutePath();
        Path install=Use4GoalPackagedPluginIT.install(work),driver=Use4GoalPackagedPluginIT.driver(work,InstalledAuctionMain.class);
        String archive=System.getProperty("use.packaged.hostArchive","");
        String archiveHash=archive.isBlank()?"":hash(Path.of(archive));
        try(var producer=new PackagedProjectProducer(install,work,0,true,false,null,true)) {
            assertTrue(producer.command.stream().noneMatch(a->a.startsWith("-Djacamo.bridge.auction.")));
            try(var files=Files.walk(producer.jcm.getParent())) {
                assertTrue(files.noneMatch(p->p.toString().matches(".*\\.(csl|istar|drawio)$")),"No production oracle inputs");
            }
            var properties=producer.consumerProperties(install,0);
            properties.setProperty("live","true");properties.setProperty("use.jacamo.runtime.journal.maxBytes","268435456");
            Path config=work.resolve("consumer.properties");try(var out=Files.newOutputStream(config)){properties.store(out,"Owned generic input/endpoint metadata");}
            var command=List.of(PackagedProjectProducer.java(),"-cp",install.resolve("lib/use-gui.jar")+java.io.File.pathSeparator+driver,
                    InstalledAuctionMain.class.getName(),install.toString(),config.toString());
            Path log=work.resolve("consumer.log");var consumer=new ProcessBuilder(command).directory(install.toFile()).redirectErrorStream(true).redirectOutput(log.toFile()).start();
            boolean finished=consumer.waitFor(900,TimeUnit.SECONDS);if(!finished){consumer.destroyForcibly();consumer.waitFor(5,TimeUnit.SECONDS);}
            String output=Files.readString(log);assertTrue(finished,output);assertEquals(0,consumer.exitValue(),output);
            var receipt=CanonicalJson.object(CanonicalJson.decode(Files.readAllBytes(work.resolve("consumer-receipt.json"))));
            assertEquals("INSTALLED_DECLARATIVE_AUCTION_LIVE",receipt.get("scope"));assertEquals("JACAMO_DECLARATIVE_XML",receipt.get("profile"));
            assertFalse(receipt.containsKey("caseProgram"));assertTrue(receipt.containsKey("declarativeProgram"));
            assertNotEquals(receipt.get("consumerPid"),receipt.get("producerPid"));assertEquals(0,((Number)receipt.get("runtimeChildProcesses")).intValue());
            assertEquals(Set.of("A_start_t1","A_start_t2","A_decide_t1","A_decide_t2","P_bid_t1","P_bid_t2"),new HashSet<>((List<?>)receipt.get("sourceTerminalSites")));
            assertEquals(2,CanonicalJson.object(receipt.get("scopedTerminals")).size());
            var requirements=CanonicalJson.object(CanonicalJson.decode(Files.readAllBytes(work.resolve("observation-requests.json"))));
            assertEquals(8,((List<?>)requirements.get("requests")).size());
            assertTrue(Files.readString(work.resolve("producer.log")).contains("BRIDGE_OBSERVATION_PLAN_ACTIVE"));
            assertTrue(producer.process.waitFor(10,TimeUnit.SECONDS));assertEquals(0,producer.process.exitValue(),Files.readString(work.resolve("producer.log")));
            var accepted=CanonicalJson.object(CanonicalJson.decode(Files.readAllBytes(work.resolve("accepted.json"))));
            var core=CanonicalJson.object(CanonicalJson.object(CanonicalJson.object(accepted.get("semantic")).get("observation")).get("originalEngine"));
            assertEquals("OBSERVED_PREFIX",core.get("historyQuality"));assertEquals(2,((List<?>)core.get("contexts")).size());
            var fulfilled=new TreeSet<String>();var derived=new TreeSet<String>();
            for(Object raw:(List<?>)core.get("contexts"))for(Object value:CanonicalJson.object(CanonicalJson.object(raw).get("occurrences")).values()) {
                var row=CanonicalJson.object(value);if("FULFILLED".equals(row.get("leafStatus")))fulfilled.add(row.get("sourceElement").toString());
                if(row.containsKey("aggregateStatus"))derived.add(row.get("sourceElement").toString());
            }
            assertTrue(fulfilled.containsAll(Set.of("A_start_t1","A_start_t2","A_decide_t1","A_decide_t2","P_bid_t1")),fulfilled.toString());
            assertTrue(derived.containsAll(Set.of("O_root","O_start","O_bid","O_decide","A_start","A_decide","P_bid")));
            if(!archive.isBlank())assertEquals(archiveHash,hash(Path.of(archive)));
            var evidence=new TreeMap<String,Object>();evidence.put("status","PASS");evidence.put("work",work.toString());evidence.put("receipt",receipt);
            evidence.put("producerCommand",producer.command);evidence.put("consumerCommand",command);evidence.put("fullHostArchive",archive);evidence.put("fullHostArchiveSha256",archiveHash);
            evidence.put("inputPolicy","PROJECT_XML_AUTHORED_OCL_NO_ORACLES");evidence.put("originalFulfilledTasks",fulfilled);evidence.put("originalDerivedElements",derived);
            Files.write(Path.of("target/generic-installed-live.json"),CanonicalJson.encode(evidence));
        }
    }
    private static String hash(Path file)throws Exception {
        var digest=java.security.MessageDigest.getInstance("SHA-256");try(var input=Files.newInputStream(file)){byte[] buffer=new byte[65536];int count;while((count=input.read(buffer))!=-1)digest.update(buffer,0,count);}
        return HexFormat.of().formatHex(digest.digest());
    }
}
