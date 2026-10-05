package org.tzi.use.plugins.jacamo;

import org.tzi.use.runtime.IPlugin;
import org.tzi.use.runtime.IPluginRuntime;

/** USE entry point; semantic work begins through the Session-bound facade. */
public final class JaCaMoPlugin implements IPlugin {
    @Override
    public String getName() {
        return "JaCaMo";
    }

    @Override
    public void run(IPluginRuntime runtime) {
        // Runtime observation/control is explicitly opened by the Workbench.
    }
}
