package org.tzi.use.plugins.jacamo.codegrounded;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class LegacyV2OclIsolationTest {
    @Test void nativeAuthorityHasNoProductionDependencyOnMappingV2OrV2Profiles() throws Exception {
        String sources = Files.walk(Path.of("src/main/java/org/tzi/use/plugins/jacamo/codegrounded"))
                .filter(Files::isRegularFile).map(path -> {
                    try { return Files.readString(path); }
                    catch (Exception error) { throw new RuntimeException(error); }
                }).reduce("", String::concat);
        for (String forbidden : List.of("ActiveBaseline", "MappingLoader", "MappingModel",
                "TransformationPlanner", "StructuralUseGenerator", "jacamo-core-v2.ocl", "OclGenerator",
                "OclProfileLoader", "VerificationProfileLoader", "VerificationSemanticLayer",
                "RuntimeMappingLoader", "loadDefault()", "TextBackend", "DirectUseBackend", "version-2"))
            assertFalse(sources.contains(forbidden), forbidden);
    }

    @Test void facadeContainsOnlyTheNativeProjection() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/org/tzi/use/plugins/jacamo/DefaultJaCaMoFacade.java"));
        int synchronizedSnapshot = source.indexOf("BridgeClient.Accepted accepted = candidate.synchronize()");
        assertTrue(synchronizedSnapshot >= 0);
        assertFalse(source.contains("NativeSemanticAdapter"));assertFalse(source.contains("BridgeRuntimeProjector"));
        assertFalse(source.contains("LEGACY_V2"));assertFalse(source.contains("RuntimeVerificationEngine"));
        String branch = section(source,"private ProjectSummary synchronizeBridge","private void validateBridgeSelection");
        assertTrue(branch.contains("buildNativeSemantic(selectedJcm, accepted.model(), importNanos)"));
        assertTrue(branch.contains("new NativeUseSessionActivator().activate(session, next.pipeline)"));
        assertTrue(branch.contains("return next.summary"));
        assertForbiddenAbsent(branch);

        String nativeBuild = section(source, "private NativeWorkspace buildNativeSemantic",
                "private VerificationReport runNativeVerification");
        String nativeVerification = section(source, "private VerificationReport runNativeVerification",
                "private String sha256");
        assertForbiddenAbsent(nativeBuild + nativeVerification);
    }

    private static String section(String source, String startMarker, String endMarker) {
        int start = source.indexOf(startMarker);
        int end = source.indexOf(endMarker, start);
        assertTrue(start >= 0 && end > start, startMarker);
        return source.substring(start, end);
    }

    private static void assertForbiddenAbsent(String source) {
        for (String forbidden : List.of("ActiveBaseline", "MappingLoader", "TransformationPlanner",
                "StructuralUseGenerator", "jacamo-core-v2.ocl", "VerificationProfileLoader",
                "VerificationSemanticLayer", "RuntimeMappingLoader", "loadDefault()", "TextBackend"))
            assertFalse(source.contains(forbidden), forbidden);
    }
}
