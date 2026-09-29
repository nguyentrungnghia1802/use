package org.tzi.use.plugins.jacamo.codegrounded;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.tzi.use.plugins.jacamo.codegrounded.use.NativeProjectionAudit;
import org.tzi.use.plugins.jacamo.codegrounded.use.NativeProjectionMode;

/** Fail-closed audit evidence for every item that reaches the AUTO native system. */
class NativeProjectionAuditTest {
    @Test
    void everyAutoClassAssociationAndObjectHasAnExplicitDependencyReason() throws Exception {
        var pipeline = new CodeGroundedNativePipeline().build(CodeGroundedTestFixtures.helloSnapshot(),
                NativeProjectionMode.AUTO);
        NativeProjectionAudit.Audit audit = new NativeProjectionAudit().audit(pipeline.model(),
                pipeline.state().system());

        assertFalse(audit.classes().isEmpty());
        assertFalse(audit.associations().isEmpty());
        assertFalse(audit.objects().isEmpty());
        assertTrue(audit.classes().stream().allMatch(value -> !value.reason().isBlank()));
        assertTrue(audit.associations().stream().allMatch(value -> !value.reason().isBlank()));
        assertTrue(audit.objects().stream().allMatch(value -> !value.reason().isBlank()));
        assertTrue(audit.classes().stream().noneMatch(value -> value.identity().equals("A17PlanOrderEntry")));
        assertTrue(audit.classes().stream().noneMatch(value -> value.identity().equals("A19BodyOrderEntry")));
        System.out.println("NATIVE_PROJECTION_AUDIT " + audit.summary());
    }
}
