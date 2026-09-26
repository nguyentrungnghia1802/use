package org.tzi.use.plugins.jacamo.bridge;

import java.net.InetAddress;
import java.nio.file.Path;
import java.util.HexFormat;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.tzi.use.plugins.jacamo.mapping.ActiveBaseline;
import org.tzi.use.plugins.jacamo.mapping.TransformationPlanner;
import org.tzi.use.plugins.jacamo.materialization.DirectUseBackend;
import org.tzi.use.plugins.jacamo.materialization.InstancePlanner;
import org.tzi.use.plugins.jacamo.materialization.TextBackend;

/** Process-B executable with a classpath deliberately stripped of platform runtime jars. */
public final class TcpBridgeConsumerMain {
    public static void main(String[] args)throws Exception{
        int port=Integer.parseInt(args[0]);byte[] secret=HexFormat.of().parseHex(args[1]);Path projectRoot=Path.of(args[2]);String project=args[3];
        var mirror=new BridgeMirrorStateMachine(128);
        try(var client=new BridgeClient(new LocalTcpBridgeTransport(InetAddress.getLoopbackAddress(),port,secret,4*1024*1024,5000),mirror,"1".repeat(64),Set.of("official.model","runtime.snapshot"),64)){
            var accepted=client.synchronize();long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);while(mirror.facts().values().stream().anyMatch(v->Boolean.TRUE.equals(v.values().get("active")))&&System.nanoTime()<deadline)Thread.onSpinWait();
            var adapted=new NativeSemanticAdapter().adapt(accepted.model(),projectRoot,project);var baseline=new ActiveBaseline().packaged();var structure=new TransformationPlanner().plan(adapted.model(),baseline.mapping());var instances=new InstancePlanner().plan(adapted.model(),baseline.mapping(),structure);var text=new TextBackend().generate(project.replace('-','_')+"_tcp",structure,instances);DirectUseBackend.Result use=new DirectUseBackend().materialize(text,instances);
            if(!use.structureValid()||mirror.state()!=BridgeClientState.LIVE||mirror.facts().values().stream().anyMatch(v->Boolean.TRUE.equals(v.values().get("active"))))throw new IllegalStateException("TCP_CONSUMER_INVALID");
            System.out.println("BRIDGE_TCP_CONSUMER_OK "+accepted.model().modelRevision()+" "+mirror.fingerprint()+" objects="+use.system().state().numObjects());
        }
    }
}
