package org.tzi.use.plugins.jacamo.codegrounded;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.jacamo.bridge.adapter.OfficialProjectAdapter;
import org.jacamo.bridge.adapter.OfficialProjectLoader;
import org.junit.jupiter.api.Test;

/** Initial Phase 9 acceptance probe: every case uses the same official-to-native pipeline. */
class CodeGroundedPhase9Test {
    private record Case(String name, Path entry) { }

    @Test
    void canonicalCasesBuildThroughTheNativePipeline() throws Exception {
        Path examples = Path.of("..", "..", "JaCaMo", "examples").toAbsolutePath().normalize();
        List<Case> cases = List.of(
                new Case("hello", CodeGroundedTestFixtures.hello()),
                new Case("auction", examples.resolve("auction/auction.jcm")),
                new Case("house-building", examples.resolve("house-building/house-building.jcm")));
        for (Case value : cases) {
            var snapshot = new OfficialProjectAdapter().adapt(
                    new OfficialProjectLoader().load(value.entry()), value.entry());
            var result = new CodeGroundedNativePipeline().build(snapshot);
            assertTrue(result.state().structureValid(), value.name());
            assertTrue(result.state().system().state().numObjects() > 0, value.name());
            assertTrue(result.trace().records().stream().anyMatch(record -> record.phase()
                    == org.tzi.use.plugins.jacamo.codegrounded.trace.TracePhase.EXPORT), value.name());
        }
    }
}
