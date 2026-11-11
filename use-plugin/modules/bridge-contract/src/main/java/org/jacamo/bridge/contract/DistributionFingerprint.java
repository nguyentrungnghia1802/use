package org.jacamo.bridge.contract;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;

public record DistributionFingerprint(String jacamoVersion, String distributionDigest, Map<String, String> components) {
    public DistributionFingerprint {
        jacamoVersion = ContractSupport.required(jacamoVersion, "jacamoVersion");
        distributionDigest = ContractSupport.required(distributionDigest, "distributionDigest");
        components = ContractSupport.map(components);
    }

    /** Computes a deterministic distribution identity from the code sources actually loaded by a JVM. */
    public static DistributionFingerprint fromCodeSources(String jacamoVersion, Map<String, Path> codeSources) {
        if (codeSources == null || codeSources.isEmpty()) throw new ContractException("codeSources are required");
        var components = new TreeMap<String, String>();
        codeSources.forEach((name, path) -> components.put(ContractSupport.required(name, "component"), digestPath(path)));
        String distribution = sha256(CanonicalJson.encode(components));
        return new DistributionFingerprint(jacamoVersion, distribution, components);
    }

    public static String digestPath(Path path) {
        try {
            Path value = path.toAbsolutePath().normalize();
            if (Files.isRegularFile(value)) return sha256(Files.readAllBytes(value));
            if (!Files.isDirectory(value)) throw new ContractException("code source is not a file or directory: " + value);
            var entries = new TreeMap<String, String>();
            try (var stream = Files.walk(value)) {
                stream.filter(Files::isRegularFile).forEach(file -> {
                    String relative = value.relativize(file).toString().replace('\\', '/');
                    entries.put(relative, digestPath(file));
                });
            }
            return sha256(CanonicalJson.encode(entries));
        } catch (IOException error) {
            throw new ContractException("code source cannot be read", error);
        }
    }

    private static String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (Exception error) { throw new IllegalStateException(error); }
    }
}
