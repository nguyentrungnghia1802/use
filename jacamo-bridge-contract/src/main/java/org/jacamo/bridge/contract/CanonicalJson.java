package org.jacamo.bridge.contract;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Small dependency-free canonical JSON codec with duplicate-key, size and depth guards. */
public final class CanonicalJson {
    public static final int DEFAULT_MAX_BYTES = 4 * 1024 * 1024;
    public static final int DEFAULT_MAX_DEPTH = 64;
    public static final int DEFAULT_MAX_STRING = 1024 * 1024;

    private CanonicalJson() { }

    public static byte[] encode(Object value) {
        var out = new StringBuilder();
        write(value, out, 0, DEFAULT_MAX_DEPTH);
        byte[] bytes = out.toString().getBytes(StandardCharsets.UTF_8);
        if (bytes.length > DEFAULT_MAX_BYTES) throw new ContractException("payload exceeds maximum encoded size");
        return bytes;
    }

    public static Object decode(byte[] bytes) {
        return decode(bytes, DEFAULT_MAX_BYTES, DEFAULT_MAX_DEPTH, DEFAULT_MAX_STRING);
    }

    public static Object decode(byte[] bytes, int maxBytes, int maxDepth, int maxString) {
        if (bytes == null || bytes.length > maxBytes) throw new ContractException("payload exceeds maximum input size");
        var parser = new Parser(new String(bytes, StandardCharsets.UTF_8), maxDepth, maxString);
        Object value = parser.value(0);
        parser.space();
        if (!parser.end()) throw new ContractException("trailing JSON data at " + parser.position());
        return value;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> object(Object value) {
        if (!(value instanceof Map<?, ?> map)) throw new ContractException("JSON object required");
        return (Map<String, Object>) map;
    }

    private static void write(Object value, StringBuilder out, int depth, int maxDepth) {
        if (depth > maxDepth) throw new ContractException("payload exceeds maximum depth");
        if (value == null) { out.append("null"); return; }
        if (value instanceof String text) { string(text, out); return; }
        if (value instanceof Boolean || value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long) {
            out.append(value); return;
        }
        if (value instanceof Float || value instanceof Double || value instanceof BigDecimal) {
            BigDecimal number = new BigDecimal(value.toString()).stripTrailingZeros();
            out.append(number.signum() == 0 ? "0" : number.toPlainString()); return;
        }
        if (value instanceof Enum<?> item) { string(item.name(), out); return; }
        if (value instanceof Map<?, ?> map) {
            out.append('{'); boolean first = true;
            var sorted = new TreeMap<String, Object>();
            for (var entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String key)) throw new ContractException("JSON object key must be a string");
                if (sorted.put(key, entry.getValue()) != null) throw new ContractException("duplicate object key: " + key);
            }
            for (var entry : sorted.entrySet()) {
                if (!first) out.append(','); first = false;
                string(entry.getKey(), out); out.append(':'); write(entry.getValue(), out, depth + 1, maxDepth);
            }
            out.append('}'); return;
        }
        if (value instanceof Iterable<?> items) {
            out.append('['); boolean first = true;
            for (Object item : items) {
                if (!first) out.append(','); first = false; write(item, out, depth + 1, maxDepth);
            }
            out.append(']'); return;
        }
        throw new ContractException("unsupported canonical JSON type: " + value.getClass().getName());
    }

    private static void string(String value, StringBuilder out) {
        if (value.length() > DEFAULT_MAX_STRING) throw new ContractException("string exceeds maximum length");
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) out.append(String.format("\\u%04x", (int)c));
                    else out.append(c);
                }
            }
        }
        out.append('"');
    }

    private static final class Parser {
        private final String text; private final int maxDepth; private final int maxString; private int at;
        Parser(String text, int maxDepth, int maxString) { this.text = text; this.maxDepth = maxDepth; this.maxString = maxString; }
        int position() { return at; } boolean end() { return at == text.length(); }
        void space() { while (!end() && Character.isWhitespace(text.charAt(at))) at++; }
        Object value(int depth) {
            if (depth > maxDepth) throw new ContractException("payload exceeds maximum depth");
            space(); if (end()) throw new ContractException("unexpected end of JSON");
            return switch (text.charAt(at)) {
                case '{' -> object(depth + 1); case '[' -> array(depth + 1); case '"' -> string();
                case 't' -> literal("true", Boolean.TRUE); case 'f' -> literal("false", Boolean.FALSE);
                case 'n' -> literal("null", null); default -> number();
            };
        }
        Object literal(String token, Object value) {
            if (!text.startsWith(token, at)) throw new ContractException("invalid token at " + at);
            at += token.length(); return value;
        }
        Map<String, Object> object(int depth) {
            at++; space(); var result = new LinkedHashMap<String, Object>();
            if (!end() && text.charAt(at) == '}') { at++; return Map.copyOf(result); }
            while (true) {
                space(); if (end() || text.charAt(at) != '"') throw new ContractException("object key required at " + at);
                String key = string(); space(); expect(':'); Object value = value(depth);
                if (result.containsKey(key)) throw new ContractException("duplicate object key: " + key);
                result.put(key, value);
                space(); if (!end() && text.charAt(at) == '}') { at++; return java.util.Collections.unmodifiableMap(result); }
                expect(',');
            }
        }
        List<Object> array(int depth) {
            at++; space(); var result = new ArrayList<>();
            if (!end() && text.charAt(at) == ']') { at++; return List.copyOf(result); }
            while (true) {
                result.add(value(depth)); space(); if (!end() && text.charAt(at) == ']') { at++; return List.copyOf(result); }
                expect(',');
            }
        }
        String string() {
            expect('"'); var out = new StringBuilder();
            while (!end()) {
                char c = text.charAt(at++);
                if (c == '"') {
                    if (out.length() > maxString) throw new ContractException("string exceeds maximum length");
                    return out.toString();
                }
                if (c == '\\') {
                    if (end()) throw new ContractException("incomplete string escape");
                    char e = text.charAt(at++);
                    switch (e) {
                        case '"', '\\', '/' -> out.append(e); case 'b' -> out.append('\b'); case 'f' -> out.append('\f');
                        case 'n' -> out.append('\n'); case 'r' -> out.append('\r'); case 't' -> out.append('\t');
                        case 'u' -> { if (at + 4 > text.length()) throw new ContractException("incomplete unicode escape");
                            try { out.append((char)Integer.parseInt(text.substring(at, at + 4), 16)); }
                            catch (NumberFormatException error) { throw new ContractException("invalid unicode escape", error); } at += 4; }
                        default -> throw new ContractException("invalid string escape: " + e);
                    }
                } else {
                    if (c < 0x20) throw new ContractException("unescaped control character"); out.append(c);
                }
                if (out.length() > maxString) throw new ContractException("string exceeds maximum length");
            }
            throw new ContractException("unterminated string");
        }
        BigDecimal number() {
            int start = at;
            if (!end() && text.charAt(at) == '-') at++;
            digits(); if (!end() && text.charAt(at) == '.') { at++; digits(); }
            if (!end() && (text.charAt(at) == 'e' || text.charAt(at) == 'E')) {
                at++; if (!end() && (text.charAt(at) == '+' || text.charAt(at) == '-')) at++; digits();
            }
            try { return new BigDecimal(text.substring(start, at)); }
            catch (NumberFormatException error) { throw new ContractException("invalid number at " + start, error); }
        }
        void digits() { int start = at; while (!end() && Character.isDigit(text.charAt(at))) at++; if (start == at) throw new ContractException("digit required at " + at); }
        void expect(char expected) { space(); if (end() || text.charAt(at) != expected) throw new ContractException("expected '" + expected + "' at " + at); at++; }
    }
}
