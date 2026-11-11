package org.jacamo.bridge.contract;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

final class ContractSupport {
    private ContractSupport() { }

    static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new ContractException(field + " is required");
        return Normalizer.normalize(value, Normalizer.Form.NFC);
    }

    static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
    }

    static <T> List<T> list(List<T> value) { return List.copyOf(Objects.requireNonNull(value)); }
    static <K, V> Map<K, V> map(Map<K, V> value) { return Map.copyOf(Objects.requireNonNull(value)); }
    static Map<String, Object> sorted(Map<String, ?> value) {
        var result = new TreeMap<String, Object>();
        value.forEach((key, item) -> result.put(required(key, "map key"), item));
        // Canonical JSON has an explicit null value (for absent optional event
        // entity/relation ends). Map.copyOf rejects nulls before the codec can
        // validate them, so retain a read-only sorted map instead.
        return java.util.Collections.unmodifiableMap(result);
    }

    static Map<String, Object> canonicalObject(Map<String, ?> value) {
        return CanonicalJson.object(CanonicalJson.decode(CanonicalJson.encode(sorted(value))));
    }
}
