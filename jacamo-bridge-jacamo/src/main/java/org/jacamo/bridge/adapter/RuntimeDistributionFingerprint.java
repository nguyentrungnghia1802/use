package org.jacamo.bridge.adapter;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jacamo.bridge.contract.DistributionFingerprint;

/** Machine-derived identity for the exact JaCaMo/Bridge classes loaded by this JVM. */
public final class RuntimeDistributionFingerprint {
    public static final String JACAMO_VERSION = "1.3.1";

    private RuntimeDistributionFingerprint() { }

    public static DistributionFingerprint compute() {
        var classes = new LinkedHashMap<String, Class<?>>();
        classes.put("bridge-contract", org.jacamo.bridge.contract.ContractEnvelope.class);
        classes.put("bridge-jacamo", JaCaMoBridgePlatform.class);
        classes.put("jacamo", jacamo.project.JaCaMoProject.class);
        classes.put("jason", jason.asSemantics.Agent.class);
        classes.put("cartago", cartago.CartagoEnvironment.class);
        classes.put("moise", moise.os.OS.class);
        classes.put("npl", npl.NPLInterpreter.class);
        var paths = new LinkedHashMap<String, Path>();
        classes.forEach((name, type) -> paths.put(name, codeSource(type)));
        return DistributionFingerprint.fromCodeSources(JACAMO_VERSION, paths);
    }

    private static Path codeSource(Class<?> type) {
        try { return Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()); }
        catch (URISyntaxException | NullPointerException error) {
            throw new IllegalStateException("BRIDGE_CODE_SOURCE_UNAVAILABLE:" + type.getName(), error);
        }
    }
}
