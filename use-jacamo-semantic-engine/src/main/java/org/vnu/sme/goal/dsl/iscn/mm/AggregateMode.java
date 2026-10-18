package org.vnu.sme.goal.dsl.iscn.mm;

/** Combinator for an {@code aggregate} check across every declared instance. */
public enum AggregateMode {
    ALL, ANY;

    public static AggregateMode from(String text) {
        return switch (text.toLowerCase()) {
            case "all" -> ALL;
            case "any" -> ANY;
            default -> throw new IllegalArgumentException("Unknown aggregate mode: " + text);
        };
    }
}
