package org.tzi.use.plugins.jacamo.bridge;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class StateSessionLifecycleTest {
    @TempDir Path directory;
    @Test void embeddedOwnerReceivesRealInspectorStateStopsItsProducerAndCanStartAFreshRun()throws Exception {
        Path example=Path.of("src/main/resources/example/counter").toAbsolutePath().normalize();
        var config=new StateRunConfiguration(example.resolve("counter.jcm"),example.resolve("attribute-mapping.xml"),example.resolve("counter.csl"),example.resolve("counter.istar"));
        String previousRun="";
        for(int attempt=0;attempt<2;attempt++) {
            var held=new CountDownLatch(1);var request=new AtomicReference<Map<String,Object>>();
            var session=StateVerificationSession.start(config,directory.resolve("run-"+attempt),System.getProperty("java.class.path"),new StateVerificationSession.Listener(){
                public void checkpoint(Map<String,Object> source,Map<String,Object> response,CslStateVerifier.Result result){
                    if(response.get("decision").equals("HOLD_FAILURE")){request.set(source);held.countDown();}
                }
            });
            long pid=session.producerPid();
            try {
                assertTrue(held.await(15,TimeUnit.SECONDS),"Expected the actual source to be held at the negative parent-plan boundary");
                var source=request.get();assertNotEquals(previousRun,source.get("run"));previousRun=source.get("run").toString();
                assertTrue(source.get("sourceState") instanceof Map<?,?>);
                var inspector=(Map<?,?>)source.get("sourceState");var agents=(List<?>)inspector.get("agents");assertFalse(agents.isEmpty());
                var agent=(Map<?,?>)agents.getFirst();assertTrue(agent.get("beliefs") instanceof List<?>);assertTrue(agent.get("intentions") instanceof List<?>);assertTrue(agent.get("plans") instanceof List<?>);
                assertTrue(session.isRunning());assertTrue(Files.exists(session.work().resolve("bridge-secret.hex")));
            }finally{session.close();session.close();}
            assertFalse(session.isRunning());assertFalse(Files.exists(session.work().resolve("bridge-secret.hex")));
            assertTrue(ProcessHandle.of(pid).isEmpty()||!ProcessHandle.of(pid).orElseThrow().isAlive());
        }
    }
}
