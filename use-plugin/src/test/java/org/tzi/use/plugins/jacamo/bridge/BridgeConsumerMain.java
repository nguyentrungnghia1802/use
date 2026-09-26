package org.tzi.use.plugins.jacamo.bridge;

import java.io.DataInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Set;
import org.tzi.use.plugins.jacamo.mapping.ActiveBaseline;
import org.tzi.use.plugins.jacamo.mapping.TransformationPlanner;
import org.tzi.use.plugins.jacamo.materialization.DirectUseBackend;
import org.tzi.use.plugins.jacamo.materialization.InstancePlanner;
import org.tzi.use.plugins.jacamo.materialization.TextBackend;

/** Process-B executable. It has no JaCaMo/Jason/CArtAgO/Moise classes on its class path. */
public final class BridgeConsumerMain {
    public static void main(String[] args)throws Exception{
        var frames=new ArrayList<byte[]>();try(var in=new DataInputStream(Files.newInputStream(Path.of(args[0])))){while(in.available()>0){int length=in.readInt();if(length<1||length>4*1024*1024)throw new IllegalStateException("FRAME_LIMIT");frames.add(in.readNBytes(length));}}
        var mirror=new BridgeMirrorStateMachine(64);BridgeClient.Accepted accepted;
        try(var client=new BridgeClient(new RecordedBridgeTransport(frames),mirror,"1".repeat(64),Set.of("official.model","runtime.snapshot"),32)){accepted=client.synchronize();
            var adapted=new NativeSemanticAdapter().adapt(accepted.model(),Path.of(args[1]),"helloworld");var baseline=new ActiveBaseline().packaged();var structure=new TransformationPlanner().plan(adapted.model(),baseline.mapping());var instances=new InstancePlanner().plan(adapted.model(),baseline.mapping(),structure);var text=new TextBackend().generate("helloworld_bridge",structure,instances);DirectUseBackend.Result use=new DirectUseBackend().materialize(text,instances);
            if(use.system().state().numObjects()==0||mirror.facts().isEmpty()||mirror.state()!=BridgeClientState.LIVE)throw new IllegalStateException("CONSUMER_STATE_EMPTY");
            System.out.println("BRIDGE_CONSUMER_OK "+accepted.model().modelRevision()+" "+mirror.fingerprint()+" objects="+use.system().state().numObjects());}
    }
}
