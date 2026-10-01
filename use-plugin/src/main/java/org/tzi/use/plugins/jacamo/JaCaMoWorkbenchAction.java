package org.tzi.use.plugins.jacamo;

import java.awt.BorderLayout;
import java.awt.event.WindowAdapter;
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
        this(null, JaCaMoWorkbenchAction::openDialog);
    }

    JaCaMoWorkbenchAction(JaCaMoFacade facade, BiConsumer<MainWindow, JaCaMoFacade> launcher) {
        this.facade = facade;
        this.launcher = java.util.Objects.requireNonNull(launcher, "launcher");
    }

    @Override public void performAction(IPluginAction action) {
        MainWindow parent = action == null ? null : action.getParent();
        JaCaMoFacade service = facade != null ? facade
                : action != null && action.getSession() != null
                ? DefaultJaCaMoFacade.forSession(action.getSession()) : DefaultJaCaMoFacade.INSTANCE;
        SwingUtilities.invokeLater(() -> launcher.accept(parent, service));
    }

    @Override public boolean shouldBeEnabled(IPluginAction action) { return true; }

    private static void openDialog(MainWindow parent, JaCaMoFacade facade) {
        JDialog dialog = new JDialog(parent, "USE JaCaMo Workbench", false);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(java.awt.event.WindowEvent event) { closeFacade(facade); }
            @Override public void windowClosed(java.awt.event.WindowEvent event) { closeFacade(facade); }
        });
        dialog.setLayout(new BorderLayout());
        dialog.add(new JaCaMoWorkbenchPanel(facade), BorderLayout.CENTER);
        dialog.setSize(1100, 720);
        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true);
    }

    private static void closeFacade(JaCaMoFacade facade) {
        if (facade instanceof AutoCloseable closeable) {
            try { closeable.close(); } catch (Exception ignored) { }
        } else {
            facade.disconnectRuntime();
        }
    }
}
