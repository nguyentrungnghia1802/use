package org.tzi.use.plugins.jacamo.bridge;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BridgeConnectionConfigTest {
    @TempDir Path temporary;

    @Test void validatesLoopbackEndpointDistributionCapabilitiesAndLimits() {
        assertThrows(IllegalArgumentException.class, () -> config("tcp://example.com:7777", "1".repeat(64)));
        assertThrows(IllegalArgumentException.class, () -> config("http://127.0.0.1:7777", "1".repeat(64)));
        assertThrows(IllegalArgumentException.class, () -> config("tcp://user@127.0.0.1:7777", "1".repeat(64)));
        assertThrows(IllegalArgumentException.class, () -> config("tcp://127.0.0.1:7777", "bad"));
        assertThrows(IllegalArgumentException.class, () -> new BridgeConnectionConfig(
                URI.create("tcp://127.0.0.1:7777"), temporary.resolve("secret"), "1".repeat(64), Set.of(),
                4096, 1000, 8));
        assertEquals("tcp://127.0.0.1:7777", config("tcp://127.0.0.1:7777", "1".repeat(64))
                .displayEndpoint());
    }

    @Test void secretMustBeARegularNonSymlinkHexFileAndIsNeverRenderedInStatus() throws Exception {
        Path secret = Files.writeString(temporary.resolve("bridge-secret.hex"), "ab".repeat(32));
        BridgeConnectionConfig configuration = new BridgeConnectionConfig(URI.create("tcp://localhost:7777"),
                secret, "1".repeat(64), Set.of("official.model"), 4096, 1000, 8);
        assertArrayEquals(java.util.HexFormat.of().parseHex("ab".repeat(32)), configuration.readSecret());
        assertFalse(configuration.displayEndpoint().contains(secret.toString()));
        Files.writeString(secret, "too-short");
        assertEquals("BRIDGE_SECRET_FILE_INVALID",
                assertThrows(IllegalArgumentException.class, configuration::readSecret).getMessage());
    }

    private BridgeConnectionConfig config(String endpoint, String distribution) {
        return new BridgeConnectionConfig(URI.create(endpoint), temporary.resolve("secret"), distribution,
                Set.of("official.model"), 4096, 1000, 8);
    }
}
