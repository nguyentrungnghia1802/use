package org.jacamo.bridge.adapter;
import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.jacamo.bridge.contract.*;
import org.junit.jupiter.api.Test;

class VerifiedCheckpointAuthenticationTest {
    @Test void separateCheckpointChannelAuthenticatesTheWholeCommandAndRejectsReplayAndTampering() throws Exception {
        byte[] secret=new byte[32];Arrays.fill(secret,(byte)7);var calls=new AtomicInteger();
        try(var server=new LocalTcpBridgeServer(InetAddress.getLoopbackAddress(),0,secret,65536,32,65536,()->new byte[]{1},()->new byte[]{1},()->new byte[]{1},()->new byte[]{1})) {
            server.checkpointHandler(command->{calls.incrementAndGet();return CanonicalJson.encode(Map.of("commandId",command.commandId()));},()->{});server.start();
            var command=VerifiedCheckpointProtocol.Command.status().payload();String nonce=UUID.randomUUID().toString();
            var mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(secret,"HmacSHA256"));
            String text="CHECKPOINT\n\n"+nonce+"\n"+new String(CanonicalJson.encode(command),StandardCharsets.UTF_8);
            var signed=new LinkedHashMap<String,Object>(Map.of("operation","CHECKPOINT","resumeToken","","nonce",nonce,"mac",HexFormat.of().formatHex(mac.doFinal(text.getBytes(StandardCharsets.UTF_8))),"checkpoint",command));
            assertEquals(command.get("commandId"),CanonicalJson.object(CanonicalJson.decode(exchange(server.port(),signed))).get("commandId"));assertEquals(1,calls.get());
            assertThrows(EOFException.class,()->exchange(server.port(),signed));assertEquals(1,calls.get());
            var tampered=new LinkedHashMap<>(signed);tampered.put("nonce",UUID.randomUUID().toString());
            var body=new LinkedHashMap<>(command);body.put("action","MONITOR_CLOSED");tampered.put("checkpoint",body);
            assertThrows(EOFException.class,()->exchange(server.port(),tampered));assertEquals(1,calls.get());
        }
    }
    static byte[] exchange(int port,Map<String,Object> body)throws Exception {
        try(var socket=new Socket(InetAddress.getLoopbackAddress(),port)) {
            socket.setSoTimeout(1500);byte[] frame=CanonicalJson.encode(body);var out=new DataOutputStream(socket.getOutputStream());out.writeInt(frame.length);out.write(frame);out.flush();
            var input=new DataInputStream(socket.getInputStream());int size=input.readInt();return input.readNBytes(size);
        }
    }
}
