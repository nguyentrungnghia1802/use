package org.tzi.use.plugins.jacamo.bridge;

import org.tzi.use.plugins.jacamo.bridge.protocol.CanonicalJson;
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

    @Test void approvalRequiresTheSameCutAndResult() throws Exception {
        String run = UUID.randomUUID().toString();
        String token = UUID.randomUUID().toString();
        String revision = CheckpointProtocol.digest(Map.of("model", "selected-model"));
        var cut = CheckpointProtocol.Cut.local(run, 2, revision);
        var request = Map.<String, Object>of("run", run, "token", token, "cut", cut.payload(),
                "snapshot", Map.of("value", 42));
        String requestHash = CheckpointProtocol.digest(request);
        var result = Map.<String, Object>of("decision", "APPROVE", "checks", List.of("PASS"));
        var response = Map.<String, Object>of("version", CheckpointProtocol.VERSION, "run", run, "token", token,
                "requestHash", requestHash, "cut", cut.payload(), "decision", "APPROVE", "result", result,
                "resultHash", CheckpointProtocol.digest(result));
        var decoded = CanonicalJson.object(CanonicalJson.decode(CanonicalJson.encode(response)));
        assertTrue(CheckpointProtocol.approved(decoded, run, token, requestHash, cut));

        for (String field : List.of("run", "token", "requestHash", "resultHash")) {
            var changed = new HashMap<>(response);
            changed.put(field, "mismatch");
            assertThrows(IOException.class, () -> CheckpointProtocol.approved(changed, run, token, requestHash, cut), field);
        }
        for (var change : Map.of("policy", "different-policy", "scope", "different-scope",
                "epoch", 1L, "revision", "different-revision").entrySet()) {
            var changedCut = new HashMap<>(cut.payload());
            changedCut.put(change.getKey(), change.getValue());
            var changed = new HashMap<>(response);
            changed.put("cut", changedCut);
            assertThrows(IOException.class, () -> CheckpointProtocol.approved(changed, run, token, requestHash, cut), change.getKey());
        }
        var held = new HashMap<>(response);
        var unavailable = Map.<String, Object>of("decision", "HOLD_UNVERIFIED", "checks", List.of("UNDEFINED"));
        held.put("decision", "HOLD_UNVERIFIED");
        held.put("result", unavailable);
        held.put("resultHash", CheckpointProtocol.digest(unavailable));
        assertFalse(CheckpointProtocol.approved(held, run, token, requestHash, cut));
        held.put("decision", "APPROVE");
        assertThrows(IOException.class, () -> CheckpointProtocol.approved(held, run, token, requestHash, cut));
    }
}
