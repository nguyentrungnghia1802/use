package org.tzi.use.plugins.jacamo.gui.main;

import java.nio.file.Files;
import java.nio.file.Path;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class RunDialogTest {
    @TempDir Path root;

    @Test void theChooserOpensTheSourceExamplesInAWorkspace() throws Exception {
        Path source = Files.createDirectories(root.resolve("use-plugin/src/main/resources/example"));
        Path home = Files.createDirectories(root.resolve("use-assembly/target/new"));
        Files.createDirectories(home.resolve("examples"));
        SwingUtilities.invokeAndWait(() -> {
            var chooser = RunDialog.createChooser(home);
            assertEquals(source, chooser.getCurrentDirectory().toPath());
            assertFalse(chooser.isAcceptAllFileFilterUsed());
            assertTrue(chooser.getFileFilter().accept(source.resolve("experiment.MRV").toFile()));
            assertFalse(chooser.getFileFilter().accept(source.resolve("experiment.jcm").toFile()));
        });
    }

    @Test void aStandaloneInstallUsesItsPackagedExamples() throws Exception {
        Path home = root.resolve("install");
        Path examples = Files.createDirectories(home.resolve("examples"));
        SwingUtilities.invokeAndWait(() -> assertEquals(examples,
                RunDialog.createChooser(home).getCurrentDirectory().toPath()));
    }
}
