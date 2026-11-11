package org.jacamo.bridge.contract;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class IsolatedContractConsumerMain {
    private IsolatedContractConsumerMain() { }
    public static void main(String[] args) {
        var envelope = ContractEnvelope.create("1.0.0", MessageType.HANDSHAKE, "isolated",
                new DistributionFingerprint("1.3.1", "d".repeat(64), Map.of()), "p", "m", "s", 1, "id", Instant.EPOCH,
                List.of(), Completeness.PARTIAL, Map.of(), List.of(), Map.of("ready", true));
        var decoded = ContractCodec.decode(ContractCodec.encode(envelope));
        new ContractValidator().validate(decoded);
        if (!envelope.equals(decoded)) throw new AssertionError("round trip differs");
        System.out.println("ISOLATED_CONTRACT_OK");
    }
}
