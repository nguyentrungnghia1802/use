package org.jacamo.bridge.contract;

import java.text.Normalizer;
import java.util.HashMap;
import java.util.Map;

/** Collision-safe display mapping. The reversible Bridge ID remains the authoritative key. */
public final class UseDisplayNameAllocator {
    private final Map<String, BridgeEntityId> allocated = new HashMap<>();
    public synchronized String allocate(BridgeEntityId id) {
        String base = Normalizer.normalize(id.localId(), Normalizer.Form.NFKC).replaceAll("[^A-Za-z0-9_]", "_");
        if (base.isEmpty() || Character.isDigit(base.charAt(0))) base = "id_" + base;
        BridgeEntityId owner = allocated.get(base);
        if (owner == null || owner.equals(id)) { allocated.put(base, id); return base; }
        String resolved = base + "__" + ContractSupport.sha256(id.canonical()).substring(0, 12);
        while (allocated.containsKey(resolved) && !allocated.get(resolved).equals(id))
            resolved = resolved + "_";
        allocated.put(resolved, id); return resolved;
    }
}
