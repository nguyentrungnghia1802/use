package org.vnu.sme.goal.verify.conformance.semantics;

import org.vnu.sme.goal.dsl.istar.mm.ContributionType;

/**
 * Collapses iStar 2.0's {@link ContributionType} values down to the 2 "sufficient" classes
 * that the Caballero-Villalobos propagation rules (P_Make / P_Break, Fig. 7) are defined
 * over.
 */
public final class ContributionPolarity {

    private ContributionPolarity() {}

    public static boolean isSufficientPositive(ContributionType t) {
        return t == ContributionType.MAKE || t == ContributionType.HELP;
    }

    public static boolean isSufficientNegative(ContributionType t) {
        return t == ContributionType.HURT || t == ContributionType.BREAK;
    }
}
