package org.tzi.use.plugins.jacamo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import org.junit.jupiter.api.Test;
import org.tzi.use.runtime.IPlugin;
import org.tzi.use.runtime.model.PluginModel;
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
}
