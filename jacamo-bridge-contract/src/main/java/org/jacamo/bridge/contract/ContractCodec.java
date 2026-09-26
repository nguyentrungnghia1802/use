package org.jacamo.bridge.contract;

/** Canonical wire codec. It serializes data only; never classes or executable objects. */
public final class ContractCodec {
    private ContractCodec() { }
    public static byte[] encode(ContractEnvelope envelope) { return CanonicalJson.encode(envelope.toMap()); }
    public static ContractEnvelope decode(byte[] bytes) { return ContractEnvelope.fromMap(CanonicalJson.object(CanonicalJson.decode(bytes))); }
    public static ContractEnvelope decode(byte[] bytes, int maxBytes, int maxDepth, int maxString) {
        return ContractEnvelope.fromMap(CanonicalJson.object(CanonicalJson.decode(bytes, maxBytes, maxDepth, maxString)));
    }
}
