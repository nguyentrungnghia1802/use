package org.tzi.use.plugins.jacamo.codegrounded;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.jacamo.bridge.adapter.OfficialProjectAdapter;
import org.jacamo.bridge.adapter.OfficialProjectLoader;
import org.jacamo.bridge.contract.ModelSnapshot;
import org.jacamo.bridge.contract.semantic.JacamoSemanticSnapshot;

final class CodeGroundedTestFixtures {
    private CodeGroundedTestFixtures() { }
    static Path hello() {
        return Path.of("src", "test", "resources", "canonical-cases", "hello-world", "helloworld.jcm")
                .toAbsolutePath().normalize();
    }
    static ModelSnapshot helloSnapshot() throws Exception {
        Path path = hello();
        return new OfficialProjectAdapter().adapt(new OfficialProjectLoader().load(path), path);
    }
    static CodeGroundedNativePipeline.Result helloPipeline() throws Exception {
        return new CodeGroundedNativePipeline().build(helloSnapshot());
    }

    /** Small native schema fixture for bounded Swing interaction tests. */
    static CodeGroundedNativePipeline.Result guiPipeline() throws Exception {
        ModelSnapshot base = helloSnapshot();
        var contract = base.semanticContract();
        var semantic = new JacamoSemanticSnapshot(contract.contractVersion(), contract.project(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        ModelSnapshot snapshot = new ModelSnapshot("native-gui", base.sources(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), Map.of(), semantic);
        return new CodeGroundedNativePipeline().build(snapshot);
    }
}
