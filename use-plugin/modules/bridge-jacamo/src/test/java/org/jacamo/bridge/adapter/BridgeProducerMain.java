package org.jacamo.bridge.adapter;

import java.io.DataOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.jacamo.bridge.contract.*;

/** Process-A executable for the recorded/framed Phase-3 proof. */
public final class BridgeProducerMain {
    private static final String DISTRIBUTION="1".repeat(64);
    public static void main(String[] args)throws Exception{
        Path output=Path.of(args[0]);Path jcm=Path.of(args[1]);var model=new OfficialProjectAdapter().adapt(new OfficialProjectLoader().load(jcm),jcm);
        String session="separate-jvm-session";long generation=7;String source="jason:separate";
        var distribution=new DistributionFingerprint("1.3.1",DISTRIBUTION,Map.of("jason","3.3.2","cartago","3.1","moise","1.1","npl","0.6.1"));
        var capabilities=List.of(new Capability("official.model",CapabilityStatus.COMPLETE,List.of(),""),new Capability("runtime.snapshot",CapabilityStatus.COMPLETE,List.of(),""));
        var live=new BridgeEntityId("jason","agent","runtime-agent",model.sources().getFirst().id().scope(),"alice",session+":"+generation);
        var fact=new RuntimeFact(live,RuntimeFactKind.AGENT,Map.of("active",true),List.of(),ProjectionStatus.MATERIALIZED_FAITHFULLY,Completeness.COMPLETE,List.of());
        var runtime=new RuntimeSnapshot("snapshot-1",model.modelRevision(),Instant.EPOCH,Instant.EPOCH,Map.of(source,new SourceWatermark(source,0)),Map.of(source,new SourceWatermark(source,0)),1,List.of(fact),Map.of(source,Completeness.COMPLETE),"f".repeat(64));
        var event=new RuntimeEvent("event-1",session,generation,model.modelRevision(),"jason",source,1,Instant.EPOCH,RuntimeEventKind.CHANGED,RuntimeFactKind.AGENT,ProjectionStatus.MATERIALIZED_FAITHFULLY,live,null,"corr-1","",Map.of("active",true),Map.of("active",false),new SourceWatermark(source,1),Completeness.COMPLETE,List.of());
        List<ContractEnvelope> envelopes=List.of(
                envelope(MessageType.HANDSHAKE,distribution,model.modelRevision(),session,generation,capabilities,Map.of("readOnly",true)),
                envelope(MessageType.MODEL_SNAPSHOT,distribution,model.modelRevision(),session,generation,capabilities,ContractPayloads.model(model)),
                envelope(MessageType.RUNTIME_SNAPSHOT,distribution,model.modelRevision(),session,generation,capabilities,ContractPayloads.runtime(runtime)),
                envelope(MessageType.RUNTIME_EVENT,distribution,model.modelRevision(),session,generation,capabilities,ContractPayloads.event(event)));
        try(var out=new DataOutputStream(Files.newOutputStream(output))){for(var envelope:envelopes){byte[] bytes=ContractCodec.encode(envelope);out.writeInt(bytes.length);out.write(bytes);}}
        System.out.println("BRIDGE_PRODUCER_OK "+model.modelRevision());
    }
    private static ContractEnvelope envelope(MessageType type,DistributionFingerprint distribution,String revision,String session,long generation,List<Capability> capabilities,Map<String,Object> payload){return ContractEnvelope.create("1.0.0",type,"phase3-test",distribution,"helloworld",revision,session,generation,type.name(),Instant.EPOCH,capabilities,Completeness.COMPLETE,Map.of(),List.of(),payload);}
}
