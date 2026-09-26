package org.jacamo.bridge.contract;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/** Bounded idempotency ledger: duplicate bytes are accepted, conflicting IDs fail closed. */
public final class EventLedger {
    public enum Result { APPLIED, DUPLICATE }
    private final int capacity;
    private final LinkedHashMap<String, String> digests = new LinkedHashMap<>();
    public EventLedger(int capacity) { if (capacity < 1) throw new IllegalArgumentException("capacity"); this.capacity = capacity; }
    public synchronized Result accept(RuntimeEvent event, Map<String, Object> canonicalEvent) {
        String digest = ContractSupport.sha256(new String(CanonicalJson.encode(canonicalEvent), StandardCharsets.UTF_8));
        String known = digests.get(event.eventId());
        if (known != null) {
            if (!known.equals(digest)) throw new ContractException("same eventId has conflicting payload: " + event.eventId());
            return Result.DUPLICATE;
        }
        digests.put(event.eventId(), digest);
        while (digests.size() > capacity) digests.remove(digests.keySet().iterator().next());
        return Result.APPLIED;
    }
}
