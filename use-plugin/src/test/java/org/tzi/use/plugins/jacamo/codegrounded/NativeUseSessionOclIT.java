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
                "PlanLibrary.allInstances->forAll(p | p.a17Entries->isUnique(rank) and "
                        + "p.a17Entries->forAll(e | e.rank >= 0 and e.rank < p.a17Entries->size()))").toString());
        assertEquals("true", UseSystemApi.create(session.system(), false).evaluate(
                "Plan.allInstances->forAll(p | p.a19Entries->isUnique(rank) and "
                        + "p.a19Entries->collect(member)->asSet() = p.bodyElements->asSet())").toString());
    }
}
