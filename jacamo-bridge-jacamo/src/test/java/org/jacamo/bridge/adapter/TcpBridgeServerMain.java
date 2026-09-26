package org.jacamo.bridge.adapter;

import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.jacamo.bridge.contract.*;

/** Process-A executable for the Phase-8 production TCP boundary proof. */
public final class TcpBridgeServerMain {
    public static void main(String[] args)throws Exception{
        Path jcm=Path.of(args[0]);int port=Integer.parseInt(args[1]);byte[] secret=HexFormat.of().parseHex(args[2]);Path stop=Path.of(args[3]);
        ModelSnapshot model=new OfficialProjectAdapter().adapt(new OfficialProjectLoader().load(jcm),jcm);String session="tcp-process-"+java.util.UUID.randomUUID();long generation=1;String source="jason:tcp";
        var distribution=new DistributionFingerprint("1.3.1","1".repeat(64),Map.of("jason","3.3.2","cartago","3.1","moise","1.1","npl","0.6.1"));
        var caps=List.of(new Capability("official.model",CapabilityStatus.COMPLETE,List.of(),""),new Capability("runtime.snapshot",CapabilityStatus.COMPLETE,List.of(),""));
        var id=new BridgeEntityId("jason","agent","runtime-agent",model.sources().getFirst().id().scope(),"tcp-agent",session+":"+generation);
        var fact=new RuntimeFact(id,RuntimeFactKind.AGENT,Map.of("active",true),List.of(),ProjectionStatus.MATERIALIZED_FAITHFULLY,Completeness.COMPLETE,List.of());
        var runtime=new RuntimeSnapshot("tcp-snapshot",model.modelRevision(),Instant.EPOCH,Instant.EPOCH,Map.of(source,new SourceWatermark(source,0)),Map.of(source,new SourceWatermark(source,0)),1,List.of(fact),Map.of(source,Completeness.COMPLETE),"f".repeat(64));
        var event=new RuntimeEvent("tcp-event",session,generation,model.modelRevision(),"jason",source,1,Instant.EPOCH,RuntimeEventKind.CHANGED,RuntimeFactKind.AGENT,ProjectionStatus.MATERIALIZED_FAITHFULLY,id,null,"tcp-correlation","",Map.of("active",true),Map.of("active",false),new SourceWatermark(source,1),Completeness.COMPLETE,List.of());
        var gap=new RuntimeEvent("tcp-gap",session,generation,model.modelRevision(),"transport",source,2,Instant.EPOCH,RuntimeEventKind.GAP,null,null,null,null,"","",Map.of(),Map.of(),new SourceWatermark(source,2),Completeness.PARTIAL,List.of());
        ContractEnvelope handshake=envelope(MessageType.HANDSHAKE,distribution,model,session,generation,caps,Map.of("readOnly",true));
        ContractEnvelope modelFrame=envelope(MessageType.MODEL_SNAPSHOT,distribution,model,session,generation,caps,ContractPayloads.model(model));
        ContractEnvelope runtimeFrame=envelope(MessageType.RUNTIME_SNAPSHOT,distribution,model,session,generation,caps,ContractPayloads.runtime(runtime));
        ContractEnvelope eventFrame=envelope(MessageType.RUNTIME_EVENT,distribution,model,session,generation,caps,ContractPayloads.event(event));
        ContractEnvelope gapFrame=envelope(MessageType.RUNTIME_EVENT,distribution,model,session,generation,caps,ContractPayloads.event(gap));
        var ref=new AtomicReference<LocalTcpBridgeServer>();
        try(var server=new LocalTcpBridgeServer(InetAddress.getLoopbackAddress(),port,secret,4*1024*1024,64,16L*1024*1024,
                ()->ContractCodec.encode(handshake),()->ContractCodec.encode(modelFrame),()->{ref.get().publish(source+":1",ContractCodec.encode(eventFrame));return ContractCodec.encode(runtimeFrame);},()->ContractCodec.encode(gapFrame))){
            ref.set(server);server.start();System.out.println("BRIDGE_TCP_SERVER_READY "+server.port()+" "+model.modelRevision());System.out.flush();long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(30);while(!Files.exists(stop)&&System.nanoTime()<deadline)Thread.sleep(20);
        }
    }
    private static ContractEnvelope envelope(MessageType type,DistributionFingerprint distribution,ModelSnapshot model,String session,long generation,List<Capability> caps,Map<String,Object> payload){return ContractEnvelope.create("1.0.0",type,"phase8-process",distribution,"canonical",model.modelRevision(),session,generation,type.name(),Instant.EPOCH,caps,Completeness.COMPLETE,Map.of(),List.of(),payload);}
}
