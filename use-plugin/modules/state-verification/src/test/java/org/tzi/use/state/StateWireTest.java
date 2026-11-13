package org.tzi.use.state;

import java.io.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StateWireTest {
    @Test void roundTripAndTamperingAreBoundToTheFullBody()throws Exception {
        byte[] secret=new byte[32];Arrays.fill(secret,(byte)42);var payload=Map.<String,Object>of("token","first","sequence",1L);
        var out=new ByteArrayOutputStream();StateWire.write(out,payload,secret);
        var received=StateWire.read(new ByteArrayInputStream(out.toByteArray()),secret);
        assertEquals("first",received.get("token"));assertEquals(1,((Number)received.get("sequence")).longValue());
        assertEquals(StateWire.digest(payload),StateWire.digest(received));
        byte[] corrupt=out.toByteArray();corrupt[10]^=1;
        assertThrows(IOException.class,()->StateWire.read(new ByteArrayInputStream(corrupt),secret));
        assertNotEquals(StateWire.digest(payload),StateWire.digest(Map.of("token","second","sequence",1L)));
    }
}
