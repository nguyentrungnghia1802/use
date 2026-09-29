package org.tzi.use.plugins.jacamo.codegrounded;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Comparator;
import org.junit.jupiter.api.Test;

class CodeGroundedDeterminismTest {
    @Test void repeatedNativeBuildHasIdenticalSchemaExportNamesAndTrace() throws Exception {
        var snapshot = CodeGroundedTestFixtures.helloSnapshot();
        var first = new CodeGroundedNativePipeline().build(snapshot);
        var second = new CodeGroundedNativePipeline().build(snapshot);
        assertEquals(first.model().structuralHash(), second.model().structuralHash());
        assertEquals(first.export().useText(), second.export().useText());
        assertEquals(first.trace().records(), second.trace().records());
        assertEquals(first.state().system().state().allObjects().stream().map(value -> value.name()).sorted().toList(),
                second.state().system().state().allObjects().stream().map(value -> value.name()).sorted().toList());
    }
}
