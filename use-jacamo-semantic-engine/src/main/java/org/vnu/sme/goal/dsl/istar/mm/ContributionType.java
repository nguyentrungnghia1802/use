package org.vnu.sme.goal.dsl.istar.mm;

/** iStar 2.0 contribution types for Quality links. */
public enum ContributionType {
    MAKE, HELP, HURT, BREAK;

    public static ContributionType from(String s) {
        return switch (s.toLowerCase()) {
            case "make"  -> MAKE;
            case "help"  -> HELP;
            case "hurt"  -> HURT;
            case "break" -> BREAK;
            default      -> throw new IllegalArgumentException("Unknown contribution type: " + s);
        };
    }

    public String label() {
        return switch (this) {
            case MAKE  -> "Make";
            case HELP  -> "Help";
            case HURT  -> "Hurt";
            case BREAK -> "Break";
        };
    }
}
