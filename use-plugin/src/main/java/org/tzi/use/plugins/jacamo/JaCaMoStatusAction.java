package org.tzi.use.plugins.jacamo;

import java.util.function.Consumer;
import javax.swing.JOptionPane;
import org.tzi.use.runtime.gui.IPluginAction;
import org.tzi.use.runtime.gui.IPluginActionDelegate;

/** Model-independent menu action that reports the facade status. */
public final class JaCaMoStatusAction implements IPluginActionDelegate {
    private final JaCaMoFacade facade;
    private final Consumer<String> showStatus;

    public JaCaMoStatusAction() {
        this(null,
                message -> JOptionPane.showMessageDialog(null, message, "JaCaMo", JOptionPane.INFORMATION_MESSAGE));
    }

    JaCaMoStatusAction(JaCaMoFacade facade, Consumer<String> showStatus) {
        this.facade = facade;
        this.showStatus = showStatus;
    }

    @Override
    public void performAction(IPluginAction action) {
        var service=facade!=null?facade:action!=null && action.getSession()!=null?DefaultJaCaMoFacade.forSession(action.getSession()):DefaultJaCaMoFacade.INSTANCE;
        showStatus.accept(service.status());
    }

    @Override
    public boolean shouldBeEnabled(IPluginAction action) {
        return true;
    }
}
