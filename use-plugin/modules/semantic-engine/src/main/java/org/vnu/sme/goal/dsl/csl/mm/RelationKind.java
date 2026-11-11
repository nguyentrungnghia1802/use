package org.vnu.sme.goal.dsl.csl.mm;

public enum RelationKind {
    ASSOCIATION("association"), AGGREGATION("aggregation"), COMPOSITION("composition");
    private final String sourceName;
    RelationKind(String sourceName) { this.sourceName = sourceName; }
    public String sourceName() { return sourceName; }
    public static RelationKind fromSource(String source) {
        return switch (source) {
            case "association", "relationship" -> ASSOCIATION;
            case "aggregation" -> AGGREGATION;
            case "composition", "partOf" -> COMPOSITION;
            default -> throw new IllegalArgumentException("unknown RelationKind: " + source);
        };
    }
}
