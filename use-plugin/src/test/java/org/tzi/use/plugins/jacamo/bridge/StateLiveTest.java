package org.tzi.use.plugins.jacamo.bridge;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.tzi.use.plugins.jacamo.bridge.protocol.CanonicalJson;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class StateLiveTest {
    @TempDir Path work;
    @Test void actualJaCaMoChecksTheGroupedParentAndHoldsItsFinalViolation()throws Exception {
        Path example=Path.of("src/main/resources/example/counter").toAbsolutePath().normalize();
        var command=List.of(Path.of(System.getProperty("java.home"),"bin/java").toString(),"-Duse.state.work="+work,
                "-cp",System.getProperty("java.class.path"),StateWorkbenchMain.class.getName(),
                example.resolve("counter.jcm").toString(),example.resolve("attribute-mapping.xml").toString(),
                example.resolve("counter.csl").toString(),example.resolve("counter.istar").toString(),"--headless","--seconds=6");
        var process=new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(work.resolve("consumer.log").toFile()).start();
        try {
            assertTrue(process.waitFor(20,TimeUnit.SECONDS),"Live verification timed out: "+work);assertEquals(0,process.exitValue(),()->read("consumer.log"));
            assertTrue(Files.isRegularFile(work.resolve("consumer-history.jsonl")),()->read("producer.log"));
            var rows=Files.readAllLines(work.resolve("consumer-history.jsonl")).stream().map(line->CanonicalJson.object(CanonicalJson.decode(line.getBytes(java.nio.charset.StandardCharsets.UTF_8)))).toList();
            var values=new HashSet<String>();boolean held=false,violated=false;int grouped=0;
            for(var row:rows) {
                var request=CanonicalJson.object(row.get("request"));var response=CanonicalJson.object(row.get("response"));var result=CanonicalJson.object(response.get("result"));
                for(Object raw:(List<?>)result.get("state"))values.add((String)CanonicalJson.object(raw).get("value"));
                for(Object raw:(List<?>)result.get("elements")) {
                    var element=CanonicalJson.object(raw);
                    violated|=element.get("element").equals("SafeValue")&&element.get("status").equals("VIOLATED");
                }
                if(request.get("plan").toString().contains("+!run")) {
                    grouped++;held|=response.get("decision").equals("HOLD_FAILURE")&&request.get("outcome").equals("SUCCEEDED");
                    assertEquals(4,((Number)CanonicalJson.object(request.get("checkpoint")).get("mergedPlans")).intValue());
                }
                assertFalse(request.get("plan").toString().contains("+!invalid"),"A synchronous child must not produce its own checkpoint");
            }
            assertTrue(values.containsAll(Set.of("0","-1")),()->"Observed values "+values+"\n"+read("producer.log"));
            assertFalse(values.contains("1"));assertFalse(values.contains("2"));assertTrue(held);assertTrue(violated);assertEquals(1,grouped);
        }finally{if(process.isAlive()){process.descendants().forEach(ProcessHandle::destroyForcibly);process.destroyForcibly();}}
    }
    @Test void realAuctionUsesFewerCheckpointsAndRetainsIndependentInstances() throws Exception {
        Path example=Path.of("src/main/resources/example/auction").toAbsolutePath().normalize();
        var command=List.of(Path.of(System.getProperty("java.home"),"bin/java").toString(),"-Duse.state.work="+work,
                "-cp",System.getProperty("java.class.path"),StateWorkbenchMain.class.getName(),
                example.resolve("auction.jcm").toString(),example.resolve("attribute-mapping.xml").toString(),
                example.resolve("auction.csl").toString(),example.resolve("auction.istar").toString(),"--headless","--seconds=25");
        var process=new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(work.resolve("consumer.log").toFile()).start();
        try {
            assertTrue(process.waitFor(40,TimeUnit.SECONDS),"Auction timed out: "+work);assertEquals(0,process.exitValue(),()->read("consumer.log"));
            var rows=Files.readAllLines(work.resolve("consumer-history.jsonl")).stream().map(line->CanonicalJson.object(CanonicalJson.decode(line.getBytes(java.nio.charset.StandardCharsets.UTF_8)))).toList();
            assertTrue(rows.size()<128,()->"Expected fewer source checkpoints; observed "+rows.size());
            boolean merged=false;var completed=new HashSet<String>();
            for(var row:rows) {
                var request=CanonicalJson.object(row.get("request"));var response=CanonicalJson.object(row.get("response"));
                assertEquals("APPROVE",response.get("decision"),row.toString());
                merged|=((Number)CanonicalJson.object(request.get("checkpoint")).get("mergedPlans")).intValue()>0;
                for(Object raw:(List<?>)CanonicalJson.object(response.get("result")).get("elements")) {
                    var element=CanonicalJson.object(raw);
                    if(element.get("element").equals("AuctionCompleted")&&element.get("status").equals("FULFILLED")) completed.add(element.get("context").toString());
                }
            }
            assertTrue(merged);assertEquals(2,completed.size(),()->read("producer.log"));
            System.out.println("AUCTION_GROUPED_CHECKPOINTS="+rows.size()+" COMPLETED_INSTANCES="+completed.size());
        } finally {if(process.isAlive()){process.descendants().forEach(ProcessHandle::destroyForcibly);process.destroyForcibly();}}
    }
    private String read(String name){try{return Files.readString(work.resolve(name));}catch(Exception error){return error.toString();}}
}
