package org.tzi.use.plugins.jacamo.semantics;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.tzi.use.plugins.jacamo.semantics.profile.*;
import org.tzi.use.plugins.jacamo.semantics.evidence.SemanticVerdict;
import org.tzi.use.plugins.jacamo.verification.VerificationOutcome;

class SemanticsProfileTest {
    @Test void importProfileFailsClosedAndDoesNotRedefineAutoFull() {
        assertEquals(SemanticProjectionProfile.NATIVE_CURRENT, SemanticProjectionProfile.configured(null));
        assertEquals(SemanticProjectionProfile.CSL_OBSERVER_V1, SemanticProjectionProfile.configured("csl_observer_v1"));
        assertThrows(IllegalArgumentException.class, () -> SemanticProjectionProfile.configured("FULL"));
        assertThrows(IllegalArgumentException.class, () -> SemanticProjectionProfile.configured("CSL"));
    }
    @Test void v1CannotAdmitUnimplementedTemporalOrNormativeSemantics() {
        SemanticsProfile.v1().require("istar.achieve");
        assertThrows(IllegalArgumentException.class, () -> SemanticsProfile.v1().require("istar.maintain"));
        assertThrows(IllegalArgumentException.class, () -> SemanticsProfile.v2().require("npl.equivalence"));
        assertThrows(UnsupportedOperationException.class, () -> SemanticsProfile.v1().capabilities().clear());
    }
    @Test void missingAndUnsupportedEvidenceCannotAppearAsViolationInLegacyView() {
        assertEquals(VerificationOutcome.FAIL, SemanticVerdict.FAIL.legacyView());
        for (var value : new SemanticVerdict[] { SemanticVerdict.UNDEFINED, SemanticVerdict.UNSUPPORTED,
                SemanticVerdict.SKIPPED, SemanticVerdict.INCONCLUSIVE })
            assertEquals(VerificationOutcome.SKIPPED, value.legacyView());
    }
}
