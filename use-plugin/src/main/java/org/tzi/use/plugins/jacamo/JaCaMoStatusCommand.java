package org.tzi.use.plugins.jacamo;

import org.tzi.use.main.shell.runtime.IPluginShellCmd;
import org.tzi.use.runtime.shell.IPluginShellCmdDelegate;

/** Harmless command used to verify plugin discovery in a USE shell. */
public final class JaCaMoStatusCommand implements IPluginShellCmdDelegate {
    @Override
    public void performCommand(IPluginShellCmd command) {
        System.out.println(SkeletonJaCaMoFacade.INSTANCE.status());
    }
}
