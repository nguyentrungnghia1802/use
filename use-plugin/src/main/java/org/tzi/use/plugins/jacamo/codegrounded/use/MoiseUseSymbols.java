package org.tzi.use.plugins.jacamo.codegrounded.use;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** USE symbols are presentation, never identity or a lookup heuristic. */
final class MoiseUseSymbols {
    record Candidate(String semanticId, String kind, String label) { }
    private static final Set<String> BUILTIN_TYPES = Set.of(
            "String", "Integer", "Real", "Boolean", "OclAny", "OclVoid", "UnlimitedNatural");

    private MoiseUseSymbols() { }

    static Map<String, String> allocate(List<Candidate> candidates, Set<String> reserved) {
        Map<String, List<Candidate>> byLabel = new LinkedHashMap<>();
        Set<String> identities = new TreeSet<>();
        for (var candidate : candidates.stream().sorted(Comparator.comparing(Candidate::semanticId)).toList()) {
            if (!identities.add(candidate.semanticId()))
                throw new IllegalArgumentException("MOISE_DUPLICATE_SCHEMA_ID:" + candidate.semanticId());
            byLabel.computeIfAbsent(label(candidate.label()), ignored -> new ArrayList<>()).add(candidate);
        }
        Set<String> used = new TreeSet<>(reserved);
        used.addAll(BUILTIN_TYPES);
        // Reserve every unambiguous readable name before allocating collision names.
        byLabel.forEach((name, values) -> { if (values.size() == 1 && !used.contains(name)) used.add(name); });
        Map<String, String> result = new LinkedHashMap<>();
        for (var entry : byLabel.entrySet()) for (var candidate : entry.getValue()) {
            String name = entry.getKey();
            if (entry.getValue().size() > 1 || reserved.contains(name) || BUILTIN_TYPES.contains(name)) {
                name = candidate.kind() + "_" + name + "_" + hash(candidate.semanticId());
                if (!used.add(name))
                    throw new IllegalArgumentException("MOISE_USE_SYMBOL_COLLISION:" + candidate.semanticId());
            }
            result.put(candidate.semanticId(), name);
        }
        return java.util.Collections.unmodifiableMap(result);
    }

    static String label(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("DOMAIN_NAME_REQUIRED");
        String result = value.replaceAll("[^A-Za-z0-9_]", "_");
        if (Character.isDigit(result.charAt(0))) result = "Element_" + result;
        return result;
    }

    static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8))).substring(0, 16); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
