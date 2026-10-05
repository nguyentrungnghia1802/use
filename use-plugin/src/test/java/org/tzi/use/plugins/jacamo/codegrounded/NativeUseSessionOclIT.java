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
                "Agent.allInstances()->size() = 5 and Agent.allInstances()->isUnique(semanticId)").toString());
        assertEquals("true", UseSystemApi.create(session.system(), false).evaluate(
                "team.allInstances()->size() = 1").toString());
        assertNull(session.system().model().getClass("Plan"));
        assertNull(session.system().model().getClass("Role"));
    }
}
