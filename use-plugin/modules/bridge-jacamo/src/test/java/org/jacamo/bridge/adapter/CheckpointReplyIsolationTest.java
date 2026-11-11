package org.jacamo.bridge.adapter;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.jacamo.bridge.contract.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CheckpointReplyIsolationTest {
    @Test void checkpointReplyDoesNotAcquireTheIndependentJasonControlMonitor() throws Exception {
        var project=Path.of("../../src/test/resources/canonical-cases/hello-world/helloworld.jcm").toAbsolutePath();
        var model=new OfficialProjectAdapter().adapt(new OfficialProjectLoader().load(project),project);
        var platform=new JaCaMoBridgePlatform();var field=JaCaMoBridgePlatform.class.getDeclaredField("modelSnapshot");field.setAccessible(true);field.set(platform,model);
        var session=JaCaMoBridgePlatform.class.getDeclaredField("sessionId");session.setAccessible(true);session.set(platform,"reply-isolation-fixture");
        var controller=new BridgeLocalExecutionControl(new jason.infra.local.RunLocalMAS());BridgeRuntimeRegistry.controller(controller);
        var held=new CountDownLatch(1);var release=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try {
            var blocker=pool.submit(()->{synchronized(controller){held.countDown();assertTrue(release.await(5,TimeUnit.SECONDS));}return null;});assertTrue(held.await(2,TimeUnit.SECONDS));
            var response=pool.submit(()->platform.checkpointReply(VerifiedCheckpointProtocol.Command.status(),model.sources().getFirst().id().scope(),1,new DistributionFingerprint("fixture","a".repeat(64),Map.of())));
            byte[] bytes=response.get(500,TimeUnit.MILLISECONDS);
            var envelope=ContractCodec.decode(bytes,CanonicalJson.DEFAULT_MAX_BYTES,64,CanonicalJson.DEFAULT_MAX_STRING);new ContractValidator().validate(envelope);
            assertEquals(MessageType.CHECKPOINT_STATUS,envelope.messageType());assertEquals("NOT_STARTED",envelope.payload().get("state"));
            release.countDown();blocker.get(2,TimeUnit.SECONDS);
        }finally{release.countDown();pool.shutdownNow();pool.awaitTermination(3,TimeUnit.SECONDS);BridgeRuntimeRegistry.controller(null);controller.stop();}
    }
}
