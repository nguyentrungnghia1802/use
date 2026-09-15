package org.tzi.use.plugins.jacamo;

import java.awt.BorderLayout;
import java.util.function.BiConsumer;
import javax.swing.JDialog;
import javax.swing.SwingUtilities;
import org.tzi.use.gui.main.MainWindow;
import org.tzi.use.plugins.jacamo.ui.JaCaMoWorkbenchPanel;
import org.tzi.use.runtime.gui.IPluginAction;
import org.tzi.use.runtime.gui.IPluginActionDelegate;

/** Opens the facade-backed JaCaMo workbench from USE. */
public final class JaCaMoWorkbenchAction implements IPluginActionDelegate {
    private final JaCaMoFacade facade;
    private final BiConsumer<MainWindow, JaCaMoFacade> launcher;

    public JaCaMoWorkbenchAction() {
        this(DefaultJaCaMoFacade.INSTANCE, JaCaMoWorkbenchAction::openDialog);
    }

    JaCaMoWorkbenchAction(JaCaMoFacade facade, BiConsumer<MainWindow, JaCaMoFacade> launcher) {
        this.facade = java.util.Objects.requireNonNull(facade, "facade");
        this.launcher = java.util.Objects.requireNonNull(launcher, "launcher");
    }

    @Override public void performAction(IPluginAction action) {
        MainWindow parent = action == null ? null : action.getParent();
        SwingUtilities.invokeLater(() -> launcher.accept(parent, facade));
    }

    @Override public boolean shouldBeEnabled(IPluginAction action) { return true; }

    private static void openDialog(MainWindow parent, JaCaMoFacade facade) {
        JDialog dialog = new JDialog(parent, "USE JaCaMo Workbench", false);
        dialog.setLayout(new BorderLayout());
        dialog.add(new JaCaMoWorkbenchPanel(facade), BorderLayout.CENTER);
        dialog.setSize(1100, 720);
        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true);
    }
}
