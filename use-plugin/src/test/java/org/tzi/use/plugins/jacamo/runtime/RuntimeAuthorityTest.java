package org.tzi.use.plugins.jacamo.runtime;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.tzi.use.plugins.jacamo.semantic.Dimension;

class RuntimeAuthorityTest {
    @Test void rejectsOrganisationFactsClaimedByEnvironmentAuthority() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () ->
            RuntimeEvent.create("conflict", Instant.now(), 1, Dimension.ENVIRONMENT,
                RuntimeEventKind.ROLE_ADOPTED, "board", null,
                Map.of("agent", "a", "role", "r", "group", "g"), null));
        assertTrue(error.getMessage().contains("RUNTIME_AUTHORITY_CONFLICT"));
    }
}
