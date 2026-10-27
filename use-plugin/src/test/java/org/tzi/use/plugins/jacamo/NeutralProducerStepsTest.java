package org.tzi.use.plugins.jacamo;

import java.nio.file.*;
import java.util.*;
import org.jacamo.bridge.contract.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.tzi.use.main.Session;
import org.tzi.use.plugins.jacamo.bridge.BridgeTransportFactory;
import static org.junit.jupiter.api.Assertions.*;

class NeutralProducerStepsTest {
    @TempDir Path root;
    @Test @Timeout(60) void sourceDeclaredBoundariesReachTcpForTwoActualAgents() throws Exception {
        Path source=Files.createDirectories(root.resolve("source")),evidence=root.resolve("run"),plan=root.resolve("requests.json");
        Path asl=Files.writeString(source.resolve("worker.asl"),"+!deliver <- .print(\"ready\"); .print(\"sent\").\n");
        Path jcm=Files.writeString(source.resolve("worker.jcm"),"mas worker {\n agent first: worker.asl {\n goals: deliver\n }\n agent second: worker.asl {\n goals: deliver\n }\n}\n");
        var request=new ObservationCapabilityRequest("steps","jason.plan-boundaries.v1",true,Map.of("sourcePath","worker.asl","sourceRef","worker","sourceHash",org.tzi.use.plugins.jacamo.codegrounded.constraint.ExternalOclConstraintService.sha256(Files.readAllBytes(asl)),"plans",List.of(Map.of("trigger","deliver","context","","steps",List.of(Map.of("task","Deliver","first",0,"after",2,"variables",List.of(),"artifactVariable","","artifactClass","","schemeVariable","","allowMissingArtifactAtEntry",false,"inputs",List.of()))))));
        try(var producer=new ManagedProducerTestSupport(jcm,evidence,false,true,Map.of(ObservationRequestPlan.PROPERTY,plan.toString()),entry->!entry.replace('\\','/').endsWith("/target/test-classes"))) {
            producer.configureWorkflow();
            try(var facade=new DefaultJaCaMoFacade(Path.of("..").toAbsolutePath(),SemanticAuthority.BRIDGE,()->producer.configuration,BridgeTransportFactory.localTcp(),PipelineMode.CODE_GROUNDED_NATIVE,new Session())) {
                facade.configureBridge(producer.configuration);facade.importProject(producer.jcm);
                var authority=facade.authorityStatus();ObservationRequestPlan.publish(plan,authority.sessionId(),authority.generation(),authority.modelRevision(),"a".repeat(64),List.of(request));
                facade.startRuntime();Thread.sleep(2000);facade.resyncRuntime();
                var field=DefaultJaCaMoFacade.class.getDeclaredField("bridgeAccepted");field.setAccessible(true);
                var accepted=(org.tzi.use.plugins.jacamo.bridge.BridgeClient.Accepted)field.get(facade);
                var observed=accepted.runtime().facts().stream().filter(f->f.values().containsKey(VerificationObservation.KEY)).toList();
                assertEquals(2,observed.size(),"Each actual agent must retain its terminal observation: "+observed+" producer="+Files.readString(evidence.resolve("producer.log"))+Files.readString(evidence.resolve("producer.err")));
                assertTrue(observed.stream().allMatch(f->"JASON_STEP".equals(CanonicalJson.object(f.values().get(VerificationObservation.KEY)).get("kind"))));
            }
        }
    }
}
