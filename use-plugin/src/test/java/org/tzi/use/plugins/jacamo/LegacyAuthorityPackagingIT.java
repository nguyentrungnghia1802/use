package org.tzi.use.plugins.jacamo;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;

class LegacyAuthorityPackagingIT {
    @Test void releaseJarShipsBridgeAuthorityAndOmitsProvenLegacyAuthorities() throws Exception {
        Path jar = Path.of("target", "use-plugin-1.0.1.jar").toAbsolutePath().normalize();
        assertTrue(Files.isRegularFile(jar), jar.toString());
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            for (String required : List.of(
                    "org/tzi/use/plugins/jacamo/DefaultJaCaMoFacade.class",
                    "org/tzi/use/plugins/jacamo/bridge/BridgeClient.class",
                    "org/tzi/use/plugins/jacamo/codegrounded/runtime/CodeGroundedRuntimeRuleRegistry.class",
                    "org/tzi/use/plugins/jacamo/codegrounded/runtime/NativeRuntimeMutationEngine.class",
                    "org/tzi/use/plugins/jacamo/codegrounded/runtime/NativeRuntimeProjector.class",
                    "org/tzi/use/plugins/jacamo/codegrounded/runtime/RuntimeVerificationCoordinator.class",
                    "org/tzi/use/plugins/jacamo/codegrounded/runtime/RuntimeControlService.class",
                    "org/tzi/use/plugins/jacamo/codegrounded/runtime/VerificationSnapshot.class",
                    "org/tzi/use/plugins/jacamo/codegrounded/runtime/SnapshotRetention.class",
                    "org/tzi/use/plugins/jacamo/codegrounded/runtime/GoalViewSnapshot.class",
                    "org/tzi/use/plugins/jacamo/ui/JaCaMoWorkbenchPanel.class",
                    "org/tzi/use/plugins/jacamo/ui/MappingRulesPanel.class",
                    "org/tzi/use/plugins/jacamo/runtime/MirrorState.class",
                    "org/tzi/use/plugins/jacamo/codegrounded/runtime/NativeRuntimeTraceRecord.class",
                    "org/tzi/use/plugins/jacamo/codegrounded/rule/CodeGroundedRuleCatalog.class",
                    "org/tzi/use/plugins/jacamo/codegrounded/use/NativeUseModelBuilder.class",
                    "org/tzi/use/plugins/jacamo/codegrounded/use/NativeUseStateBuilder.class",
                    "org/tzi/use/plugins/jacamo/codegrounded/use/NativeUseStateExporter.class",
                    "org/tzi/use/plugins/jacamo/codegrounded/trace/CodeGroundedTraceExporter.class",
                    "org/tzi/use/plugins/jacamo/extraction/OrderEvidenceLoader.class",
                    "org/tzi/use/plugins/jacamo/semantic/JaCaMoSemanticModel.class",
                    "org/tzi/use/plugins/jacamo/trace/TraceIndex.class")) {
                assertNotNull(zip.getEntry(required), "required production foundation missing: " + required);
            }
            for (String removed : List.of(
                    "org/tzi/use/plugins/jacamo/ui/GoalViewPanel.class",
                    "org/tzi/use/plugins/jacamo/ui/RuntimeHistoryRows.class",
                    "org/tzi/use/plugins/jacamo/bridge/NativeSemanticAdapter.class",
                    "org/tzi/use/plugins/jacamo/bridge/BridgeRuntimeProjector.class",
                    "org/tzi/use/plugins/jacamo/bridge/BridgeVerificationGate.class",
                    "org/tzi/use/plugins/jacamo/runtime/RuntimeMutationEngine.class",
                    "org/tzi/use/plugins/jacamo/verification/RuntimeVerificationEngine.class",
                    "org/tzi/use/plugins/jacamo/SkeletonJaCaMoFacade.class",
                    "org/tzi/use/plugins/jacamo/ui/BindingResolutionPanel.class",
                    "org/tzi/use/plugins/jacamo/extraction/StaticProjectImporter.class",
                    "org/tzi/use/plugins/jacamo/extraction/JcmSemanticParser.class",
                    "org/tzi/use/plugins/jacamo/extraction/JasonSourceParser.class",
                    "org/tzi/use/plugins/jacamo/extraction/CartagoSourceExtractor.class",
                    "org/tzi/use/plugins/jacamo/extraction/MoiseXmlParser.class",
                    "org/tzi/use/plugins/jacamo/project/JcmLexer.class",
                    "org/tzi/use/plugins/jacamo/project/JcmProjectLoader.class",
                    "org/tzi/use/plugins/jacamo/runtime/JasonRuntimeConnector.class",
                    "org/tzi/use/plugins/jacamo/runtime/CartagoRuntimeConnector.class",
                    "org/tzi/use/plugins/jacamo/runtime/MoiseRuntimeConnector.class",
                    "org/tzi/use/plugins/jacamo/runtime/CompositeRuntimeConnector.class",
                    "org/tzi/use/plugins/jacamo/runtime/RuntimeConnector.class",
                    "org/tzi/use/plugins/jacamo/runtime/RuntimeMirrorService.class",
                    "org/tzi/use/plugins/jacamo/runtime/RuntimeService.class",
                    "org/tzi/use/plugins/jacamo/runtime/RuntimeSubscription.class")) {
                assertNull(zip.getEntry(removed), "legacy authority leaked into release: " + removed);
            }
            assertTrue(zip.stream().noneMatch(entry -> entry.getName().startsWith("org/jacamo/bridge/adapter/")),
                    "JaCaMo-side official adapter must remain in its separate process distribution");
        }
    }
}
