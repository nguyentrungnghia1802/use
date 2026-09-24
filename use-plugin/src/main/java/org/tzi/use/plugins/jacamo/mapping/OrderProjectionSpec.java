package org.tzi.use.plugins.jacamo.mapping;

/** One authoritative source navigation, independent of membership insertion order. */
public record OrderProjectionSpec(String sourceIdentity, String ruleId, String owner,
        String target, String association, boolean reverse, String membershipRole) {
    public String entryClass() { return "OrderEntry_" + ruleId; }
    public String ownerAssociation() { return "OrderOwner_" + ruleId; }
    public String targetAssociation() { return "OrderTarget_" + ruleId; }
    public String entriesRole() { return "orderEntries_" + ruleId; }
    public String query() { return "ordered_" + ruleId; }
    public String expression(String receiver) {
        return receiver + "." + entriesRole() + "->sortedBy(rank)->collect(value)->asSequence()";
    }
}
