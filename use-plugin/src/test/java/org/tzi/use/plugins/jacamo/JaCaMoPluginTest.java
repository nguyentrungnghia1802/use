package org.tzi.use.plugins.jacamo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import org.junit.jupiter.api.Test;
import org.tzi.use.runtime.MainPluginRuntime;
import org.tzi.use.runtime.IPlugin;
import org.tzi.use.runtime.gui.IPluginActionDelegate;
import org.tzi.use.runtime.gui.impl.ActionExtensionPoint;
import org.tzi.use.runtime.impl.PluginRuntime;
import org.tzi.use.runtime.model.PluginModel;
import org.tzi.use.runtime.shell.impl.ShellExtensionPoint;
import org.tzi.use.runtime.util.PluginParser;
import org.xml.sax.InputSource;

class JaCaMoPluginTest {
    @Test
    void manifestNamesLoadablePlugin() throws Exception {
        try (InputStream stream = getClass().getResourceAsStream("/useplugin.xml")) {
            assertNotNull(stream, "plugin descriptor must be packaged at JAR root");
            PluginModel model = new PluginParser().parsePlugin(new InputSource(stream));
            assertEquals("JaCaMo", model.getName());
            Class<?> pluginClass = Class.forName(model.getPluginClass());
            assertTrue(IPlugin.class.isAssignableFrom(pluginClass));
            IPlugin plugin = (IPlugin) pluginClass.getDeclaredConstructor().newInstance();
            assertEquals(model.getName(), plugin.getName());
        }
    }

    @Test
    void useDiscoversJarAndRegistersStatusCommand() throws Exception {
        Path pluginDirectory = Path.of("target/plugin-smoke");
        Files.createDirectories(pluginDirectory);
        Path jar = pluginDirectory.resolve("jacamo.jar");
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS, JaCaMoPlugin.class.getName());
        Path classes = Path.of("target/classes");
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar), manifest);
             var entries = Files.walk(classes)) {
            for (Path file : entries.filter(Files::isRegularFile).toList()) {
                output.putNextEntry(new JarEntry(classes.relativize(file).toString().replace('\\', '/')));
                Files.copy(file, output);
                output.closeEntry();
            }
        }

        MainPluginRuntime.run(pluginDirectory);
        var descriptor = ((PluginRuntime) PluginRuntime.getInstance()).getPlugin("JaCaMo");
        assertNotNull(descriptor, "USE must discover the plugin JAR");
        assertEquals("JaCaMo", descriptor.getPluginClass().getName());
        var commands = ((ShellExtensionPoint) ShellExtensionPoint.getInstance()).getRegisteredCmds();
        var statusCommand = commands.stream().filter(command ->
                "jacamo status".equals(command.getPluginCmdModel().getShellCmd())
                && command.getCmdClass() != null).findFirst();
        assertTrue(statusCommand.isPresent());
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PrintStream original = System.out;
        try {
            System.setOut(new PrintStream(bytes));
            statusCommand.get().getCmdClass().performCommand(null);
        } finally {
            System.setOut(original);
        }
        assertTrue(bytes.toString().contains("JaCaMo plugin ready"));

        var actions = descriptor.getPluginModel().getActions();
        assertEquals(1, actions.size(), "a model-independent status menu action is registered");
        Class<?> actionClass = descriptor.getPluginClassLoader()
                .loadClass(actions.get(0).getActionClass());
        assertTrue(IPluginActionDelegate.class.isAssignableFrom(actionClass));
        IPluginActionDelegate action = (IPluginActionDelegate)
                actionClass.getDeclaredConstructor().newInstance();
        assertTrue(action.shouldBeEnabled(null), "status action works before a USE model is loaded");
        var useActions = ((ActionExtensionPoint) ActionExtensionPoint.getInstance())
                .createPluginActions(null, null);
        assertEquals(1, useActions.size(), "USE action extension point must expose the menu action");
        var useAction = useActions.values().iterator().next();
        useAction.calculateEnabled();
        assertTrue(useAction.isEnabled());
    }

    @Test
    void menuActionGetsStatusFromFacadeWithoutAUseModel() {
        var shown = new ArrayList<String>();
        var action = new JaCaMoStatusAction(() -> "facade supplied status", shown::add);
        assertTrue(action.shouldBeEnabled(null));
        action.performAction(null);
        assertEquals(java.util.List.of("facade supplied status"), shown);
    }
}
