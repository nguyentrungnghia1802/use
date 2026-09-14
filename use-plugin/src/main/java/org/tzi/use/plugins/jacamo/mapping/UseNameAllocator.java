package org.tzi.use.plugins.jacamo.mapping;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Collision-safe USE identifier allocation; source identity is always retained by the caller. */
public final class UseNameAllocator {
    private static final Set<String> RESERVED = Set.of("abstract", "association", "associationclass", "attributes",
            "between", "class", "composition", "constraints", "context", "declare", "delete", "destroy",
            "else", "end", "endif", "enum", "existential", "for", "from", "if", "in", "insert", "into",
            "inv", "let", "model", "new", "operations", "ordered", "post", "pre", "redefines", "role",
            "subsets", "then", "union");
    private final Map<String, String> allocated = new HashMap<>();

    public String allocate(String requested, String sourceIdentity) {
        String sanitized = requested.replaceAll("[^A-Za-z0-9_]", "_");
        if (sanitized.isEmpty()) sanitized = "generated";
        if (!Character.isLetter(sanitized.charAt(0)) && sanitized.charAt(0) != '_') sanitized = "_" + sanitized;
        if (RESERVED.contains(sanitized.toLowerCase(java.util.Locale.ROOT))) sanitized = "ecore_" + sanitized;
        String owner = allocated.get(sanitized);
        if (owner == null || owner.equals(sourceIdentity)) {
            allocated.put(sanitized, sourceIdentity);
            return sanitized;
        }
        String candidate = sanitized + "_" + hash(sourceIdentity);
        allocated.put(candidate, sourceIdentity);
        return candidate;
    }

    public Map<String, String> allocateAll(Map<String, String> sourceToRequested) {
        TreeMap<String, String> result = new TreeMap<>();
        sourceToRequested.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(entry -> result.put(entry.getKey(), allocate(entry.getValue(), entry.getKey())));
        return java.util.Collections.unmodifiableMap(result);
    }

    private String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest, 0, 4);
        } catch (Exception exception) { throw new IllegalStateException(exception); }
    }
}
