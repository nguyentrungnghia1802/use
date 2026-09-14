package org.tzi.use.plugins.jacamo;

import org.tzi.use.runtime.IPlugin;
import org.tzi.use.runtime.IPluginRuntime;

/** USE entry point; Phase 1 only registers the plugin. */
public final class JaCaMoPlugin implements IPlugin {
    @Override
    public String getName() {
        return "JaCaMo";
    }

    @Override
    public void run(IPluginRuntime runtime) {
        // No startup side effects are needed for the skeleton.
    }
}
