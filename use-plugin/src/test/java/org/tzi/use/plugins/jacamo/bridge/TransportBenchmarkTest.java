package org.tzi.use.plugins.jacamo.bridge;

import static org.junit.jupiter.api.Assertions.*;

import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import org.jacamo.bridge.adapter.LocalTcpBridgeServer;
import org.jacamo.bridge.contract.CanonicalJson;
import org.junit.jupiter.api.Test;

class TransportBenchmarkTest {
    @Test void recordRealisticLocalRoundTripMeasurementsWithoutInventedBudget() throws Exception {
        byte[] secret="0123456789abcdef0123456789abcdef".getBytes();byte[] payload=new byte[64*1024];Arrays.fill(payload,(byte)'x');
        var recorded=new RecordedBridgeTransport(java.util.List.of(payload,payload,payload));var direct=new ArrayList<Long>();for(int i=0;i<100;i++){long start=System.nanoTime();assertEquals(payload.length,recorded.handshake().length);direct.add(System.nanoTime()-start);}recorded.close();
        var tcp=new ArrayList<Long>();try(var server=new LocalTcpBridgeServer(InetAddress.getLoopbackAddress(),0,secret,1024*1024,32,4L*1024*1024,()->payload,()->payload,()->payload,()->payload)){server.start();try(var transport=new LocalTcpBridgeTransport(InetAddress.getLoopbackAddress(),server.port(),secret,1024*1024,3000)){for(int i=0;i<30;i++){long start=System.nanoTime();assertEquals(payload.length,transport.handshake().length);tcp.add(System.nanoTime()-start);}}}
        direct.sort(Long::compare);tcp.sort(Long::compare);var evidence=Map.of("payloadBytes",payload.length,"recordedIterations",direct.size(),"tcpIterations",tcp.size(),"recordedMedianNanos",direct.get(direct.size()/2),"tcpMedianNanos",tcp.get(tcp.size()/2),"deployment","loopback-only","remoteMode",false,"serialization","canonical-json-length-frame");Path output=Path.of("target/architecture-realignment/transport-benchmark.json");Files.createDirectories(output.getParent());Files.write(output,CanonicalJson.encode(evidence));assertTrue(Files.size(output)>100);
    }
}
