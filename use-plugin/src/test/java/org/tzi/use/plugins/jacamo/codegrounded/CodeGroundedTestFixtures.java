package org.tzi.use.plugins.jacamo.codegrounded;

import java.nio.file.Path;
import org.jacamo.bridge.adapter.OfficialProjectAdapter;
import org.jacamo.bridge.adapter.OfficialProjectLoader;
import org.jacamo.bridge.contract.ModelSnapshot;

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
}
