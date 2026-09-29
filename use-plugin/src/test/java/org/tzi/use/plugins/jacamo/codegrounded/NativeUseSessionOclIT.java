package org.tzi.use.plugins.jacamo.codegrounded;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.tzi.use.api.UseSystemApi;
import org.tzi.use.main.Session;

class NativeUseSessionOclIT {
    @Test void helloUsesTheExactActivatedSystemAndNativeOcl() throws Exception {
        var result = CodeGroundedTestFixtures.helloPipeline();
        Session session = new Session();
        new NativeUseSessionActivator().activate(session, result);
        assertSame(result.state().system(), session.system());
        assertSame(result.model().model(), session.system().model());
        assertEquals("true", UseSystemApi.create(session.system(), false).evaluate(
                "PlanLibrary.allInstances->forAll(p | p.plans->isUnique(ordinal) and "
                        + "p.plans->forAll(e | e.ordinal >= 0 and e.ordinal < p.plans->size()))").toString());
        assertEquals("true", UseSystemApi.create(session.system(), false).evaluate(
                "Plan.allInstances->forAll(p | p.bodyElements->isUnique(ordinal) and "
                        + "p.bodyElements->forAll(e | e.ordinal >= 0 and e.ordinal < p.bodyElements->size()))").toString());
    }
}
