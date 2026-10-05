package org.tzi.use.plugins.jacamo;

import org.tzi.use.main.shell.runtime.IPluginShellCmd;
import org.tzi.use.runtime.shell.IPluginShellCmdDelegate;

/** Read-only status from the active Session's native facade. */
public final class JaCaMoStatusCommand implements IPluginShellCmdDelegate {
    @Override
    public void performCommand(IPluginShellCmd command) {
        var facade=command!=null && command.getSession()!=null?DefaultJaCaMoFacade.forSession(command.getSession()):DefaultJaCaMoFacade.INSTANCE;
        System.out.println(facade.status());
    }
}
