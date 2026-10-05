package org.tzi.use.plugins.jacamo.runtime;

import java.util.Map;

/** Exact, non-fuzzy binding between one live CArtAgO artifact and the imported USE trace. */
public record CartagoArtifactBinding(String workspace, String artifact, String semanticId,
                                     Map<String, String> observableAttributes,
                                     Map<String, String> operations) {
    public CartagoArtifactBinding {
        if (workspace == null || workspace.isBlank() || artifact == null || artifact.isBlank()
                || semanticId == null || semanticId.isBlank() || observableAttributes == null || operations == null)
            throw new IllegalArgumentException("CARTAGO_BINDING_INVALID");
        workspace = normalize(workspace);
        observableAttributes = Map.copyOf(observableAttributes);
        operations = Map.copyOf(operations);
    }

    public String runtimeSourceId() { return "cartago:artifact:" + workspace.substring(1) + "/" + artifact; }
    public String qualifiedName() { return workspace + "/" + artifact; }

    private static String normalize(String value) {
        String normalized = value.replace('\\', '/');
        if (!normalized.startsWith("/")) normalized = "/" + normalized;
        return normalized.replaceAll("/+$", "");
    }
}
