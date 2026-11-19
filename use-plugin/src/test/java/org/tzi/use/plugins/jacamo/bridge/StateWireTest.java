package org.tzi.use.plugins.jacamo.bridge;

import org.tzi.use.plugins.jacamo.bridge.protocol.CheckpointProtocol;
import java.io.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StateWireTest {
    @Test void roundTripAndTamperingAreBoundToTheFullBody()throws Exception {
        byte[] secret=new byte[32];Arrays.fill(secret,(byte)42);var payload=Map.<String,Object>of("token","first","sequence",1L);
        var out=new ByteArrayOutputStream();CheckpointProtocol.write(out,payload,secret);
        var received=CheckpointProtocol.read(new ByteArrayInputStream(out.toByteArray()),secret);
        assertEquals("first",received.get("token"));assertEquals(1,((Number)received.get("sequence")).longValue());
        assertEquals(CheckpointProtocol.digest(payload),CheckpointProtocol.digest(received));
        byte[] corrupt=out.toByteArray();corrupt[10]^=1;
        assertThrows(IOException.class,()->CheckpointProtocol.read(new ByteArrayInputStream(corrupt),secret));
        assertNotEquals(CheckpointProtocol.digest(payload),CheckpointProtocol.digest(Map.of("token","second","sequence",1L)));
    }
}
