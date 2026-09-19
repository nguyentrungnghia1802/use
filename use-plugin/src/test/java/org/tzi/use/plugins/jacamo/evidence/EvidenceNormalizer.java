package org.tzi.use.plugins.jacamo.evidence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

public final class EvidenceNormalizer {
    private static final ObjectMapper JSON = new ObjectMapper();

    private EvidenceNormalizer() {
    }

    public static String normalizeJson(String value, Path project, String placeholder) {
        try {
            JsonNode normalized = normalizeNode(JSON.readTree(value), project, placeholder);
            return (JSON.writerWithDefaultPrettyPrinter().writeValueAsString(normalized) + "\n")
                    .replace("\r\n", "\n");
        } catch (Exception exception) {
            throw new IllegalArgumentException("EVIDENCE_JSON_NORMALIZATION_FAILED", exception);
        }
    }

    public static String normalizeText(String value, Path project, String placeholder) {
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(project, "project");
        Objects.requireNonNull(placeholder, "placeholder");
        return normalizePaths(value, project, placeholder).replace("\r\n", "\n");
    }

    private static String normalizePaths(String value, Path project, String placeholder) {
        String nativeProject = project.toString();
        String forwardProject = nativeProject.replace('\\', '/');
        return replaceExactPath(replaceExactPath(value, nativeProject, placeholder), forwardProject, placeholder);
    }

    private static JsonNode normalizeNode(JsonNode node, Path project, String placeholder) {
        if (node.isTextual()) return TextNode.valueOf(normalizePaths(node.asText(), project, placeholder));
        if (node.isArray()) {
            ArrayNode result = JSON.createArrayNode();
            node.forEach(value -> result.add(normalizeNode(value, project, placeholder)));
            return result;
        }
        if (node.isObject()) {
            ObjectNode result = JSON.createObjectNode();
            for (Map.Entry<String, JsonNode> entry : node.properties()) {
                result.set(normalizePaths(entry.getKey(), project, placeholder),
                        normalizeNode(entry.getValue(), project, placeholder));
            }
            return result;
        }
        return node.deepCopy();
    }

    private static String replaceExactPath(String value, String project, String placeholder) {
        StringBuilder result = new StringBuilder(value.length());
        int copiedThrough = 0;
        int candidate;
        while ((candidate = value.indexOf(project, copiedThrough)) >= 0) {
            int after = candidate + project.length();
            if (!leftBoundary(value, candidate) || !rightBoundary(value, after)) {
                result.append(value, copiedThrough, after);
                copiedThrough = after;
                continue;
            }
            result.append(value, copiedThrough, candidate).append(placeholder);
            int cursor = after;
            if (cursor < value.length() && isSeparator(value.charAt(cursor))) {
                result.append('/');
                cursor++;
                while (cursor < value.length() && !isPathTerminator(value.charAt(cursor))) {
                    char current = value.charAt(cursor++);
                    result.append(isSeparator(current) ? '/' : current);
                }
            }
            copiedThrough = cursor;
        }
        return result.append(value, copiedThrough, value.length()).toString();
    }

    private static boolean leftBoundary(String value, int index) {
        return index == 0 || Character.isWhitespace(value.charAt(index - 1))
                || "|:=([{\"'".indexOf(value.charAt(index - 1)) >= 0;
    }

    private static boolean rightBoundary(String value, int index) {
        return index == value.length() || isSeparator(value.charAt(index)) || isPathTerminator(value.charAt(index));
    }

    private static boolean isSeparator(char value) {
        return value == '\\' || value == '/';
    }

    private static boolean isPathTerminator(char value) {
        return Character.isWhitespace(value) || "|:,;)]}\"'".indexOf(value) >= 0;
    }
}
