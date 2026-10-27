package org.tzi.use.plugins.jacamo;

import java.nio.file.*;
import java.util.Map;
import org.jacamo.bridge.contract.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Real disposable JaCaMo process: intentional pre-start cancellation is not bootstrap failure. */
class ManagedProducerCancellationTest {
    @TempDir Path root;
    private static final Path PROJECT=Path.of("src/test/resources/semantics/profile-v1/monitor.jcm");
    @Test void stopFileBeforeStartExitsNormallyWithoutStartingAgents()throws Exception {cancel(false);}
    @Test void controlCancellationBeforeStartExitsNormallyWithoutStartingAgents()throws Exception {cancel(true);}
    private void cancel(boolean control)throws Exception {
        Path evidence=root.resolve(control?"cancel":"stop");
        try(var producer=new ManagedProducerTestSupport(PROJECT,evidence,false,true,Map.of())) {
            assertFalse(Files.exists(producer.control.resolve("started.json")));
            if(control)ManagedStartupControl.cancel(producer.control,"user closed Workbench before Start");else Files.writeString(producer.stop,"user closed Workbench before Start");
            assertTrue(producer.awaitProducerExit(15),"Cancelled producer must terminate boundedly");
            String log=Files.readString(evidence.resolve("producer.log"))+Files.readString(evidence.resolve("producer.err"));
            assertEquals(0,producer.producerExitCode(),log);assertTrue(log.contains("REAL_JACAMO_START_CANCELLED"),log);
            assertFalse(Files.exists(producer.control.resolve("started.json")));assertFalse(log.contains("REAL_JACAMO_MANAGED_START_ACK"));assertTrue(producer.sourceUnchanged());
        }
    }
    @Test void invalidOwnershipRemainsBootstrapFailureRatherThanSuccessfulCancellation()throws Exception {
        Path evidence=root.resolve("wrong-owner");
        try(var producer=new ManagedProducerTestSupport(PROJECT,evidence,false,true,Map.of())) {
            Files.write(producer.control.resolve("request.json"),CanonicalJson.encode(Map.of("owner",Map.of("runId","foreign"))));
            assertTrue(producer.awaitProducerExit(15));assertEquals(1,producer.producerExitCode());
            String log=Files.readString(evidence.resolve("producer.log"))+Files.readString(evidence.resolve("producer.err"));
            assertTrue(log.contains("STARTUP_OWNERSHIP_MISMATCH"),log);assertFalse(log.contains("REAL_JACAMO_START_CANCELLED"));assertFalse(Files.exists(producer.control.resolve("started.json")));
        }
    }
}
