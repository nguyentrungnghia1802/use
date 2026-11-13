package org.tzi.use.state;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.jacamo.bridge.contract.CanonicalJson;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class StateLiveTest {
    @TempDir Path work;
    @Test void actualJaCaMoWaitsForNativeStateDecisionAndNeverExecutesPastViolation()throws Exception {
        Path example=Path.of("../../examples/state-only-counter").toAbsolutePath().normalize();
        var command=List.of(Path.of(System.getProperty("java.home"),"bin/java").toString(),"-Duse.state.work="+work,
                "-cp",System.getProperty("java.class.path"),StateWorkbenchMain.class.getName(),
                example.resolve("counter.jcm").toString(),example.resolve("attribute-mapping.xml").toString(),
                example.resolve("counter.csl").toString(),example.resolve("counter.istar").toString(),"--headless","--seconds=6");
        var process=new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(work.resolve("consumer.log").toFile()).start();
        try {
            assertTrue(process.waitFor(20,TimeUnit.SECONDS),"Live verification timed out: "+work);assertEquals(0,process.exitValue(),()->read("consumer.log"));
            assertTrue(Files.isRegularFile(work.resolve("consumer-history.jsonl")),()->read("producer.log"));
            var rows=Files.readAllLines(work.resolve("consumer-history.jsonl")).stream().map(line->CanonicalJson.object(CanonicalJson.decode(line.getBytes(java.nio.charset.StandardCharsets.UTF_8)))).toList();
            var values=new HashSet<String>();boolean held=false,fulfilled=false,violated=false;
            for(var row:rows) {
                var request=CanonicalJson.object(row.get("request"));var response=CanonicalJson.object(row.get("response"));var result=CanonicalJson.object(response.get("result"));
                for(Object raw:(List<?>)result.get("state"))values.add((String)CanonicalJson.object(raw).get("value"));
                for(Object raw:(List<?>)result.get("elements")) {
                    var element=CanonicalJson.object(raw);
                    fulfilled|=element.get("element").equals("TargetReached")&&element.get("status").equals("FULFILLED");
                    violated|=element.get("element").equals("SafeValue")&&element.get("status").equals("VIOLATED");
                }
                held|=response.get("decision").equals("HOLD_FAILURE")&&request.get("outcome").equals("SUCCEEDED")&&request.get("plan").toString().contains("+!invalid");
                assertFalse(request.get("plan").toString().contains("+!must_not_run"),"Parent continued after failed native verdict");
            }
            assertTrue(values.containsAll(Set.of("0","1","2","-1")),()->"Observed values "+values+"\n"+read("producer.log"));
            assertFalse(values.contains("999"));assertTrue(held);assertTrue(fulfilled);assertTrue(violated);
        }finally{if(process.isAlive()){process.descendants().forEach(ProcessHandle::destroyForcibly);process.destroyForcibly();}}
    }
    private String read(String name){try{return Files.readString(work.resolve(name));}catch(Exception error){return error.toString();}}
}
