package org.tzi.use.plugins.jacamo.codegrounded.trace;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/** Build-filtered versions for the native evidence envelope. */
public final class NativeComponentVersionManifest {
    public static final String RESOURCE = "/org/tzi/use/plugins/jacamo/codegrounded/component-versions.properties";
    private static final List<String> KEYS = List.of("plugin", "use", "jacamo", "jason", "cartago", "moise");

    private NativeComponentVersionManifest() { }

    public static Map<String, String> load() {
        Properties properties = new Properties();
        try (InputStream input = NativeComponentVersionManifest.class.getResourceAsStream(RESOURCE)) {
            if (input == null) throw new IllegalStateException("NATIVE_COMPONENT_VERSION_MANIFEST_MISSING");
            properties.load(input);
        } catch (IOException error) {
            throw new IllegalStateException("NATIVE_COMPONENT_VERSION_MANIFEST_IO_FAILED", error);
        }
        Map<String, String> versions = new LinkedHashMap<>();
        for (String key : KEYS) {
            String value = properties.getProperty(key);
            if (value == null || value.isBlank() || value.contains("${"))
                throw new IllegalStateException("NATIVE_COMPONENT_VERSION_MISSING:" + key);
            versions.put(key, value);
        }
        if (!properties.stringPropertyNames().equals(Set.copyOf(KEYS)))
            throw new IllegalStateException("NATIVE_COMPONENT_VERSION_KEYS_INVALID");
        return Collections.unmodifiableMap(versions);
    }
}
