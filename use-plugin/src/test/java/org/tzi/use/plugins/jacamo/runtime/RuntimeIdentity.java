package org.tzi.use.plugins.jacamo.runtime;

/** Length-prefixed subordinate identity: component separators cannot cause collisions. */
public final class RuntimeIdentity {
    private RuntimeIdentity() { }
    public static String key(String kind, String... components) {
        StringBuilder result = new StringBuilder(kind);
        for (String component : components) {
            if (component == null || component.isBlank()) throw new IllegalArgumentException("RUNTIME_IDENTITY_INVALID");
            result.append(':').append(component.length()).append(':').append(component);
        }
        return result.toString();
    }
}
