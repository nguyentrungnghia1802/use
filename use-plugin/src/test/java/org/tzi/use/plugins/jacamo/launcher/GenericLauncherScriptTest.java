package org.tzi.use.plugins.jacamo.launcher;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/** Static contract evidence for the production generic .jcm launcher. */
class GenericLauncherScriptTest {
    @Test
    void launcherIsGenericAndUsesNativeProductionConsumer() throws IOException {
        String script = Files.readString(findLauncher()).toLowerCase(Locale.ROOT);

        assertTrue(script.contains("$jcmpath"));
        assertTrue(script.contains("jcm_path_required"));
        assertTrue(script.contains("jcm_file_not_found"));
        assertTrue(script.contains("jacamobridgenativeconsumermain"));
        assertTrue(script.contains("code_grounded_native"));
        assertTrue(script.contains("interactivegui"));
        assertTrue(script.contains("runtimeprojectclasses"));
        assertFalse(script.contains("live-hello-bridge.ps1"));
        assertFalse(script.contains("helloworld"));
        assertFalse(script.contains("auction"));
        assertFalse(script.contains("house"));
        assertFalse(script.contains("projectkey"));
        assertFalse(script.contains("casename"));
    }

    private static Path findLauncher() {
        Path cursor = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        for (int depth = 0; depth < 8 && cursor != null; depth++) {
            Path modulePath = cursor.resolve("tools/jacamo-bridge.ps1");
            if (Files.isRegularFile(modulePath)) return modulePath;
            Path repositoryPath = cursor.resolve("use-plugin/tools/jacamo-bridge.ps1");
            if (Files.isRegularFile(repositoryPath)) return repositoryPath;
            cursor = cursor.getParent();
        }
        throw new AssertionError("generic launcher script not found from " + System.getProperty("user.dir"));
    }
}
