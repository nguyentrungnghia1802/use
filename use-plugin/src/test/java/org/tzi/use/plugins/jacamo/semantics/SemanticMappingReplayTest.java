package org.tzi.use.plugins.jacamo.semantics;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.tzi.use.plugins.jacamo.codegrounded.*;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.*;
import org.tzi.use.plugins.jacamo.codegrounded.use.*;
import org.tzi.use.plugins.jacamo.semantics.evidence.*;
import org.tzi.use.plugins.jacamo.semantics.mapping.*;
import org.tzi.use.plugins.jacamo.semantics.istar.*;

class SemanticMappingReplayTest {
    @Test void staticComplementIsBoundToTheNativeModelAndVerifiedInPortableReplay()throws Exception {
        var source=SemanticTransformationTest.source();var config=new OrganizationalMappingRules().derive(source,List.of(),List.of(),List.of(),List.of(),new RequirementModel(List.of()));var bundle=new SemanticTransformationService().fromOfficial(source,config);var built=new CodeGroundedNativePipeline().buildSemanticBundle(source,NativeProjectionMode.AUTO,bundle).nativeResult();
        assertEquals(bundle,SemanticMappingAttachment.read(built.model().model()).orElseThrow());
        Files.createDirectories(Path.of("target"));var dir=Files.createTempDirectory(Path.of("target"),"semantic-static-replay-");
        try(var projector=new NativeRuntimeProjector(built,"static-replay",1,source.modelRevision())) {
            assertFalse(projector.coordinator().latest().semantic().observation().isEmpty());assertTrue(projector.coordinator().latest().semantic().observation().containsKey("staticMapping"));
            new NativeRuntimeReplay().exportBundle(projector,built.export().useText(),dir);
            try(var replay=new NativeRuntimeReplay().openBundle(dir)){assertTrue(replay.report().complete(),replay.report().diagnostics().toString());assertEquals(bundle,replay.mapping().orElseThrow());}
            Files.writeString(dir.resolve("mapping.json"),"{}");var corrupt=new NativeRuntimeReplay().replay(dir);assertFalse(corrupt.complete());assertTrue(corrupt.diagnostics().getFirst().contains("REPLAY_FILE_HASH_MISMATCH:mapping.json"));
        }
    }
}
