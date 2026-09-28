package org.tzi.use.plugins.jacamo;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;

/** Prevents the source-tree GUI launcher from loading an obsolete plugin copy. */
class GuiPluginStagingIT {
    @Test
    void developmentGuiLoadsTheCurrentProductionPluginArtifact() throws Exception {
        Path module = Path.of(".").toRealPath();
        Path production = module.resolve("target/use-plugin-1.0.1.jar");
        Path staged = module.resolve("../use-gui/lib/plugins/use-jacamo-plugin-1.0.1.jar").normalize();

        assertTrue(Files.isRegularFile(production), "production plugin JAR must be built before integration tests");
        assertTrue(Files.isRegularFile(staged), "package phase must stage the plugin used by java -jar use-gui/target/use-gui.jar");
        assertArrayEquals(Files.readAllBytes(production), Files.readAllBytes(staged),
                "the GUI plugin copy must be byte-identical to the current production JAR");

        try (ZipFile zip = new ZipFile(staged.toFile())) {
            assertNotNull(zip.getEntry("useplugin.xml"));
            assertNotNull(zip.getEntry("org/tzi/use/plugins/jacamo/bridge/BridgeClient.class"));
            assertNotNull(zip.getEntry("org/jacamo/bridge/contract/ModelSnapshot.class"));
            assertNull(zip.getEntry("org/tzi/use/plugins/jacamo/extraction/JcmSemanticParser.class"));
            assertNull(zip.getEntry("org/tzi/use/plugins/jacamo/runtime/CompositeRuntimeConnector.class"));
            assertTrue(zip.stream().noneMatch(entry -> entry.getName().startsWith("org/jacamo/bridge/adapter/")),
                    "JaCaMo-side adapter classes must stay outside the USE GUI process");
        }
    }
}
