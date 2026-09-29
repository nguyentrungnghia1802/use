package org.tzi.use.plugins.jacamo.codegrounded;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.jacamo.bridge.contract.BridgeEntityId;
import org.junit.jupiter.api.Test;
import org.tzi.use.plugins.jacamo.runtime.RuntimeIdentity;

/** Exact identity evidence for native semantic links and runtime incarnations. */
class CodeGroundedIdentitySafetyTest {
    @Test
    void runtimeIdentityIsOpaqueAndDelimiterSafe() {
        String first = RuntimeIdentity.key("cartago-artifact", "workspace/a", "artifact:1");
        String second = RuntimeIdentity.key("cartago-artifact", "workspace/a:artifact", "1");
        assertNotEquals(first, second);

        BridgeEntityId value = new BridgeEntityId("cartago", "environment", "artifact", "scope/a",
                "artifact:1", "incarnation-7");
        assertEquals(value, BridgeEntityId.parse(value.canonical()));
        assertNotEquals(value.canonical(), new BridgeEntityId("cartago", "environment", "artifact", "scope/a",
                "artifact:1", "incarnation-8").canonical());
    }

    @Test
    void invalidIdentityComponentsCannotCollapseToAnImplicitNameKey() {
        assertThrows(IllegalArgumentException.class, () -> RuntimeIdentity.key("artifact", "", "id"));
        assertThrows(IllegalArgumentException.class, () -> RuntimeIdentity.key("artifact", "workspace", " "));
    }
}
