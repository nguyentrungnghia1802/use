package org.tzi.use.plugins.jacamo.ocl;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tzi.use.plugins.jacamo.mapping.TransformationPlan;
import org.tzi.use.plugins.jacamo.ocl.OclProfileLoader.LoadedProfile;
import org.tzi.use.plugins.jacamo.ocl.OclProfileLoader.SourceKind;

class OclProfileOrderingTest {
    @TempDir Path temporary;

    @Test void profileSourceKindIsExplicitAndNeverInferredFromTheFilename() throws Exception {
        Path file = temporary.resolve("jacamo-core-v2.ocl");
        Files.writeString(file, "context A inv Authored: true\n");
        var loader = new OclProfileLoader();
        assertEquals(SourceKind.AUTHORED_FILE, loader.loadCase(temporary, file).sourceKind());
        assertEquals(SourceKind.AUTHORED_FILE, loader.loadUser(temporary, file).sourceKind());
        assertEquals(SourceKind.PACKAGED_CORE, loader.loadCore().sourceKind());
    }

    @Test void authoredThenCoreOrderIsStableForWindowsAndUnixPathsAndInputOrder() {
        var core = new LoadedProfile(Path.of("/org/tzi/use/plugins/jacamo/ocl/jacamo-core-v2.ocl"),
                "-- packaged core\n", "core-hash", SourceKind.PACKAGED_CORE);
        var plan = new TransformationPlan(List.of(), List.of(), List.of(), List.of(), List.of());
        String expectedModel = null;
        for (Path origin : List.of(Path.of("C:/workspace/verification/case.ocl"),
                Path.of("/tmp/workspace/verification/case.ocl"))) {
            var authored = new LoadedProfile(origin, "-- authored policy\n", "authored-hash");
            for (List<LoadedProfile> input : List.of(List.of(core, authored), List.of(authored, core))) {
                var result = new OclGenerator().generate("Profiles", plan, List.of(), input);
                assertTrue(result.useModel().indexOf("-- authored policy")
                        < result.useModel().indexOf("-- packaged core"));
                assertEquals(List.of("PROFILE|" + origin.toString().replace('\\', '/') + "|authored-hash",
                                "PROFILE|" + core.origin().toString().replace('\\', '/') + "|core-hash"),
                        result.provenanceManifest().lines().toList());
                if (expectedModel == null) expectedModel = result.useModel();
                else assertEquals(expectedModel, result.useModel());
            }
        }
    }
}
