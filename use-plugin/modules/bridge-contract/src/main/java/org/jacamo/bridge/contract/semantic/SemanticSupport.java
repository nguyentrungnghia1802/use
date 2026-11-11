package org.jacamo.bridge.contract.semantic;

import java.text.Normalizer;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

final class SemanticSupport {
    private SemanticSupport() { }
    static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return Normalizer.normalize(value, Normalizer.Form.NFC);
    }
    static <T> T required(T value, String field) {
        if (value == null) throw new IllegalArgumentException(field + " is required");
        return value;
    }
    static String text(String value) { return value == null ? "" : Normalizer.normalize(value, Normalizer.Form.NFC); }
    static Map<String,String> stringMap(Map<String,String> value) {
        var ordered = new TreeMap<String,String>();
        if (value != null) value.forEach((key, item) -> ordered.put(required(key, "map key"), text(item)));
        return Collections.unmodifiableMap(ordered);
    }
}
