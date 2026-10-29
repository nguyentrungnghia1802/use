package org.tzi.use.plugins.jacamo.semantics;

import java.nio.file.*;
import java.net.URI;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.jacamo.bridge.contract.*;
import org.tzi.use.plugins.jacamo.bridge.*;
import static org.junit.jupiter.api.Assertions.*;

/** E02 source producer focus only; this does not replace native/GUI/replay acceptance. */
class AuctionControlledProducerIT {
    public static void main(String[] args)throws Exception {var tests=new AuctionControlledProducerIT();if(Boolean.getBoolean("use.auction.fixture.lateRole"))tests.actualLateRoleAdoptionAndMissionCommitmentReachBothOriginalBidIntentions();else tests.officialSeedsFailBothOriginalBidIntentionsForEveryActualParticipant();System.out.println("E02_REAL_SOURCE_SEED_FOCUS_PASS");}
    @Test void officialSeedsFailBothOriginalBidIntentionsForEveryActualParticipant()throws Exception {
        run(false);
    }
    @Test void actualLateRoleAdoptionAndMissionCommitmentReachBothOriginalBidIntentions()throws Exception {
        run(true);
    }
    private static void run(boolean late)throws Exception {
        Path work=Files.createTempDirectory(Path.of("target"),"controlled-auction-producer-").toAbsolutePath(),install=Use4GoalPackagedPluginIT.install(work);
        var frames=new LinkedBlockingQueue<RuntimeEvent>(8192);var errors=new CopyOnWriteArrayList<String>();
        Path variant=late?AuctionControlledCase.prepare(install.resolve("examples/auction-semantic"),work,true):null;
        try(var producer=new PackagedProjectProducer(install,work,0,true,true,variant)) {
            var config=new BridgeConnectionConfig(URI.create(producer.endpoint),producer.secret,producer.distribution,Set.of("official.model",VerificationObservation.CAPABILITY),CanonicalJson.DEFAULT_MAX_BYTES,60000,1024);
            try(var client=new BridgeClient(BridgeTransportFactory.localTcp().open(config),new BridgeMirrorStateMachine(8192),producer.distribution,config.requiredCapabilities(),1024,
                    event->{if(!frames.offer(event))errors.add("E02_TEST_QUEUE_OVERFLOW");},(String error)->errors.add(error))) {
                client.synchronize();var waiting=ManagedStartupControl.read(producer.control,"waiting.json");ManagedStartupControl.request(producer.control,CanonicalJson.object(waiting.get("owner")));
                var failed=new TreeSet<String>();var players=new TreeSet<String>();long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(60);var evidence=new ArrayList<Object>();
                while(failed.size()<8&&System.nanoTime()<until&&producer.process.isAlive()&&errors.isEmpty()) {
                    var event=frames.poll(100,TimeUnit.MILLISECONDS);if(event==null||!VerificationObservation.present(event))continue;
                    var frame=VerificationObservation.require(event);if(frame.kind()!=VerificationObservation.Kind.JASON_STEP)continue;
                    if("P_bid_t2".equals(frame.data().get("sourceStepId"))&&Set.of("FAILED","SUCCEEDED").contains(frame.data().get("phase"))) {
                        evidence.add(ContractPayloads.event(event));assertEquals("FAILED",frame.data().get("phase"),"Seeded actual branch must fail rather than invent a source outcome");
                        failed.add(frame.data().get("stepInvocation").toString());players.add(BridgeEntityId.parse(frame.data().get("agentIdentity").toString()).localId());
                    }
                }
                Files.write(work.resolve("source-step-evidence.json"),CanonicalJson.encode(evidence));
                assertTrue(errors.isEmpty(),errors.toString());assertEquals(8,failed.size(),work.toString());assertEquals(Set.of("alice","maria","francois","giacomo"),players);
                assertTrue(Files.isRegularFile(work.resolve("fixture-control/initial-commit-request.json")),"Explicit fixture commitments, not min=1 inference, must establish the requested test roster");
                if(late)assertTrue(Files.isRegularFile(work.resolve("fixture-control/late-role-request.json")),"Actual six source failures must precede late role adoption");
                Files.write(work.resolve("source-focus.json"),CanonicalJson.encode(Map.of("status","PASS","scope","ACTUAL_SEEDED_EXTERNAL_SOURCE_TCP_ONLY_NOT_FULL_E02","producerPid",producer.process.pid(),"consumerPid",ProcessHandle.current().pid(),"failedInvocations",failed,"players",players,"controlManifest",CanonicalJson.decode(Files.readAllBytes(work.resolve("fixture-control/manifest.json"))))));
            }
        }
    }
}
