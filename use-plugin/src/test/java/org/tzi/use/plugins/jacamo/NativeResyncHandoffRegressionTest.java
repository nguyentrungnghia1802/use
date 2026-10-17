package org.tzi.use.plugins.jacamo;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;
import org.jacamo.bridge.adapter.*;
import org.jacamo.bridge.contract.*;
import org.junit.jupiter.api.Test;
import org.tzi.use.main.Session;
import org.tzi.use.plugins.jacamo.bridge.*;
import org.tzi.use.uml.ocl.value.StringValue;

/** An old observer must settle before the candidate captures its replacement source cut. */
public class NativeResyncHandoffRegressionTest {
    @Test public void oldSubscriptionCannotAdvanceTheProjectorPastTheReplacementCut() throws Exception {
        Path jcm=Path.of("src/test/resources/canonical-cases/hello-world/helloworld.jcm").toAbsolutePath();
        var model=new OfficialProjectAdapter().adapt(new OfficialProjectLoader().load(jcm),jcm);
        var transports=new ArrayList<HandoffTransport>();
        var session=new Session();
        try(var facade=new DefaultJaCaMoFacade(Path.of("."),SemanticAuthority.BRIDGE,
                BridgeFacadeTestSupport::configuration,ignored->{
                    var transport=new HandoffTransport(model,transports);
                    transports.add(transport);return transport;
                },PipelineMode.CODE_GROUNDED_NATIVE,session)) {
            facade.importProject(jcm);var system=session.system();
            assertDoesNotThrow(facade::resyncRuntime);
            assertSame(system,session.system());
            assertTrue(transports.getFirst().closed,"The superseded observer must be closed");
            var declaration=model.semanticContract().agentDeclarations().getFirst().metadata().semanticId();
            var name=facade.verificationSnapshot().image().objects().values().stream()
                    .filter(object->object.semanticId().equals(declaration)).findFirst().orElseThrow().name();
            assertEquals("after-cut",((StringValue)system.state().objectByName(name).state(system.state())
                    .attributeValue("name")).value());
            assertEquals(1,facade.runtimeStatus().processed(),"Only the candidate's post-cut callback may mutate the projector");
            assertEquals("CURRENT_OBSERVED",facade.runtimeVerificationResult().freshness());
        }
    }

    private static final class HandoffTransport implements BridgeTransport {
        private final ModelSnapshot model;
        private final List<HandoffTransport> transports;
        private Consumer<byte[]> receiver;
        private boolean closed;
        HandoffTransport(ModelSnapshot model,List<HandoffTransport> transports){this.model=model;this.transports=transports;}
        @Override public byte[] handshake(){return frame(MessageType.HANDSHAKE,Map.of("readOnly",true));}
        @Override public byte[] modelSnapshot(){return frame(MessageType.MODEL_SNAPSHOT,ContractPayloads.model(model));}
        @Override public byte[] runtimeSnapshot(){
            var waters=Map.of("jason",new SourceWatermark("jason",0));
            var cut=new RuntimeSnapshot("cut-"+transports.size(),model.modelRevision(),Instant.EPOCH,Instant.EPOCH,
                    waters,waters,1,List.of(),Map.of("jason",Completeness.COMPLETE),"cut");
            if(transports.size()==2) {
                // The source cut has been copied. A real callback now reaches both overlapping subscribers.
                transports.getFirst().receiver.accept(event());
                receiver.accept(event());
            }
            return frame(MessageType.RUNTIME_SNAPSHOT,ContractPayloads.runtime(cut));
        }
        private byte[] event(){
            var declaration=BridgeEntityId.parse(model.semanticContract().agentDeclarations().getFirst().metadata().semanticId());
            var runtime=new BridgeEntityId("jason","agent","runtime-agent","helloworld","francois","incarnation");
            var binding=new BridgeRelationId("runtime-model-binding",List.of(runtime,declaration),"handoff","incarnation");
            return frame(MessageType.RUNTIME_EVENT,ContractPayloads.event(new RuntimeEvent("after-cut","test-session",1,
                    model.modelRevision(),"jason","jason",1,Instant.EPOCH,RuntimeEventKind.ADDED,RuntimeFactKind.AGENT,
                    ProjectionStatus.MATERIALIZED_FAITHFULLY,runtime,binding,"","",Map.of(),
                    Map.of("normalizedEventKind","SET_ATTRIBUTE","attribute","name","valueType","STRING","value","after-cut"),
                    new SourceWatermark("jason",1),Completeness.COMPLETE,List.of())));
        }
        private byte[] frame(MessageType type,Map<String,Object> payload){
            return ContractCodec.encode(ContractEnvelope.create("1.0.0",type,"handoff",
                    new DistributionFingerprint("1.3.1",BridgeFacadeTestSupport.DISTRIBUTION,Map.of()),
                    model.sources().getFirst().id().scope(),model.modelRevision(),"test-session",1,type.name(),Instant.EPOCH,
                    List.of(new Capability("official.model",CapabilityStatus.COMPLETE,List.of(),""),
                            new Capability("runtime.snapshot",CapabilityStatus.COMPLETE,List.of(),"")),
                    Completeness.COMPLETE,Map.of(),List.of(),payload));
        }
        @Override public Subscription subscribe(String token,Consumer<byte[]> next){receiver=next;return ()->closed=true;}
        @Override public void acknowledge(String token){}
        @Override public void close(){closed=true;}
    }
}
