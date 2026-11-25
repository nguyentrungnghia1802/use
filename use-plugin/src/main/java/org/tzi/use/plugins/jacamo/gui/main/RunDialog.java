package org.tzi.use.plugins.jacamo.gui.main;

import java.awt.Component;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;
import org.tzi.use.config.Options;

/** Selects the experiment package. Unpacking and process startup happen off the EDT. */
public final class RunDialog {
    private RunDialog() { }

    public static Optional<Path> show(Component owner) {
        var chooser = createChooser(Options.homeDir);
        return chooser.showDialog(owner, "Run") == JFileChooser.APPROVE_OPTION
                ? Optional.of(chooser.getSelectedFile().toPath()) : Optional.empty();
    }

    static JFileChooser createChooser(Path useHome) {
        var chooser = new JFileChooser();
        Path examples = examplesDirectory(useHome);
        if (examples != null) chooser.setCurrentDirectory(examples.toFile());
        chooser.setName("bridge-mrv");
        chooser.setDialogTitle("Run JaCaMo in USE");
        chooser.setAcceptAllFileFilterUsed(false);
        chooser.setFileFilter(new FileNameExtensionFilter("MRV experiment (*.mrv)", "mrv"));
        return chooser;
    }

    private static Path examplesDirectory(Path useHome) {
        Path home = (useHome == null ? Path.of("") : useHome).toAbsolutePath().normalize();
        for (Path folder = home; folder != null; folder = folder.getParent()) {
            Path source = folder.resolve("use-plugin/src/main/resources/example");
            if (Files.isDirectory(source)) return source;
        }
        Path installed = home.resolve("examples");
        return Files.isDirectory(installed) ? installed : null;
    }
}
