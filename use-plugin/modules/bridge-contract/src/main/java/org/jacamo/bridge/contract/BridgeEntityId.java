package org.jacamo.bridge.contract;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/** Structured canonical identity. Display names are never identity. */
public record BridgeEntityId(
        String authority, String dimension, String kind, String scope,
        String localId, String incarnation) implements Comparable<BridgeEntityId> {
    public BridgeEntityId {
        authority = ContractSupport.required(authority, "authority");
        dimension = ContractSupport.required(dimension, "dimension");
        kind = ContractSupport.required(kind, "kind");
        scope = ContractSupport.required(scope, "scope");
        localId = ContractSupport.required(localId, "localId");
        incarnation = ContractSupport.required(incarnation, "incarnation");
    }

    public String canonical() {
        return "beid:v1:" + String.join("/", List.of(authority, dimension, kind, scope, localId, incarnation)
                .stream().map(BridgeEntityId::escape).toList());
    }

    public static BridgeEntityId parse(String value) {
        if (value == null || !value.startsWith("beid:v1:")) throw new ContractException("invalid BridgeEntityId");
        String[] parts = value.substring(8).split("/", -1);
        if (parts.length != 6) throw new ContractException("invalid BridgeEntityId component count");
        var decoded = new ArrayList<String>();
        for (String part : parts) decoded.add(unescape(part));
        return new BridgeEntityId(decoded.get(0), decoded.get(1), decoded.get(2), decoded.get(3), decoded.get(4), decoded.get(5));
    }

    private static String escape(String value) {
        var out = new StringBuilder();
        for (byte item : value.getBytes(StandardCharsets.UTF_8)) {
            int b = item & 0xff;
            if ((b >= 'a' && b <= 'z') || (b >= 'A' && b <= 'Z') || (b >= '0' && b <= '9') || b == '-' || b == '_' || b == '.') out.append((char)b);
            else out.append('%').append(HexFormat.of().withUpperCase().toHexDigits((byte)b));
        }
        return out.toString();
    }

    private static String unescape(String value) {
        var out = new ByteArrayOutputStream();
        for (int i = 0; i < value.length();) {
            char c = value.charAt(i);
            if (c == '%') {
                if (i + 2 >= value.length()) throw new ContractException("invalid identity escape");
                try { out.write(Integer.parseInt(value.substring(i + 1, i + 3), 16)); }
                catch (NumberFormatException error) { throw new ContractException("invalid identity escape", error); }
                i += 3;
            } else {
                if (c > 127) throw new ContractException("identity encoding must be ASCII");
                out.write((byte)c); i++;
            }
        }
        return out.toString(StandardCharsets.UTF_8);
    }

    @Override public int compareTo(BridgeEntityId other) { return canonical().compareTo(other.canonical()); }
}
