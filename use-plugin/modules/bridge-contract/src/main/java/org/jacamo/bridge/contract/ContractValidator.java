package org.jacamo.bridge.contract;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

/** Semantic validation performed before any payload is materialized. */
public final class ContractValidator {
    public static final int SUPPORTED_MAJOR = 1;

    public void validate(ContractEnvelope envelope) {
        String[] version = envelope.schemaVersion().split("\\.", -1);
        if (version.length != 3) throw new ContractException("schemaVersion must be semantic x.y.z");
        try {
            if (Integer.parseInt(version[0]) != SUPPORTED_MAJOR) throw new ContractException("unsupported schema major: " + version[0]);
            Integer.parseInt(version[1]); Integer.parseInt(version[2]);
        } catch (NumberFormatException error) { throw new ContractException("invalid schemaVersion", error); }
        String actual = ContractSupport.sha256(new String(CanonicalJson.encode(envelope.payload()), StandardCharsets.UTF_8));
        if (!actual.equals(envelope.payloadDigest())) throw new ContractException("payload digest mismatch");
        Set<String> names = new HashSet<>();
        for (Capability capability : envelope.capabilities()) {
            if (!names.add(capability.name())) throw new ContractException("contradictory duplicate capability: " + capability.name());
        }
        envelope.watermarks().forEach((key, value) -> {
            if (!key.equals(value.sourceId())) throw new ContractException("watermark key/source mismatch: " + key);
        });
    }
}
