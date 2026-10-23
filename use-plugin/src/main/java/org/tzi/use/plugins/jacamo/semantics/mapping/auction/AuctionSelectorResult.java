package org.tzi.use.plugins.jacamo.semantics.mapping.auction;

import java.util.*;

/** Source rows, never runtime population or live state evidence. */
public record AuctionSelectorResult(String bundleHash, String specificationHash, String sourceHash,
        Map<String, List<Row>> selections, List<String> qualifications) {
    public record Row(String selectorId, String sourceId, String sourceDigest, Map<String, String> values,
                      int line, int column, String locator, String matched) {
        public Row { values = Collections.unmodifiableMap(new LinkedHashMap<>(values)); }
    }
    public AuctionSelectorResult {
        var copy = new LinkedHashMap<String, List<Row>>(); selections.forEach((id, rows) -> copy.put(id, List.copyOf(rows)));
        selections = Collections.unmodifiableMap(copy); qualifications = List.copyOf(qualifications);
    }
    public List<Row> require(String id) {
        var rows = selections.get(id);
        if (rows == null) throw new IllegalArgumentException("SELECTOR_RESULT_REQUIRED:" + id);
        return rows;
    }
}
