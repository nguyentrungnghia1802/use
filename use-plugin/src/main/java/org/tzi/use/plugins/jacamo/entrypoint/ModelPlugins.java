package org.tzi.use.plugins.jacamo.entrypoint;

import javax.swing.SwingUtilities;
import org.tzi.use.plugins.jacamo.gui.main.ModelWindow;
import org.tzi.use.runtime.IPlugin;
import org.tzi.use.runtime.IPluginRuntime;
import org.tzi.use.runtime.gui.IPluginAction;
import org.tzi.use.runtime.gui.IPluginActionDelegate;

/** The four model plugin descriptors share their registration and open-window behavior. */
public final class ModelPlugins {
    private ModelPlugins() { }

    private abstract static class NamedPlugin implements IPlugin {
        private final String name;

        NamedPlugin(String name) { this.name = name; }

        @Override public String getName() { return name; }
        @Override public void run(IPluginRuntime runtime) { }
    }

    private abstract static class OpenModelAction implements IPluginActionDelegate {
        private final ModelWindow.Kind kind;

        OpenModelAction(ModelWindow.Kind kind) { this.kind = kind; }

        @Override public boolean shouldBeEnabled(IPluginAction action) { return true; }

        @Override public void performAction(IPluginAction action) {
            SwingUtilities.invokeLater(() -> ModelWindow.open(action.getParent(), kind));
        }
    }

    public static final class CslModelPlugin extends NamedPlugin {
        public CslModelPlugin() { super("CSL Model"); }
    }

    public static final class CslModelAction extends OpenModelAction {
        public CslModelAction() { super(ModelWindow.Kind.CSL); }
    }

    public static final class OalModelPlugin extends NamedPlugin {
        public OalModelPlugin() { super("OAL State"); }
    }

    public static final class OalModelAction extends OpenModelAction {
        public OalModelAction() { super(ModelWindow.Kind.OAL); }
    }

    public static final class IStarModelPlugin extends NamedPlugin {
        public IStarModelPlugin() { super("iStar Model"); }
    }

    public static final class IStarModelAction extends OpenModelAction {
        public IStarModelAction() { super(ModelWindow.Kind.ISTAR); }
    }

    public static final class IStarScnPlugin extends NamedPlugin {
        public IStarScnPlugin() { super("iStarSCN State"); }
    }

    public static final class IStarScnAction extends OpenModelAction {
        public IStarScnAction() { super(ModelWindow.Kind.ISTAR_SCN); }
    }
}
