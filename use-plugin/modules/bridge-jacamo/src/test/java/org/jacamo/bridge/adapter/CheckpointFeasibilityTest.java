package org.jacamo.bridge.adapter;

import static org.junit.jupiter.api.Assertions.*;
import jacamo.checkpoint.CheckpointFeasibility;
import org.junit.jupiter.api.Test;

class CheckpointFeasibilityTest {
    @Test void fingerprintNamesTheRebuiltJarThatOwnsTheLauncherAndGate() throws Exception {
        var sources=RuntimeDistributionFingerprint.codeSources();
        var source=sources.get("jacamo");
        assertTrue(source.getFileName().toString().contains("1.3.1-checkpoint-SNAPSHOT"));
        assertEquals(source,java.nio.file.Path.of(CheckpointFeasibility.class.getProtectionDomain().getCodeSource().getLocation().toURI()));
        assertEquals(source,java.nio.file.Path.of(jacamo.infra.JaCaMoLauncher.class.getProtectionDomain().getCodeSource().getLocation().toURI()));
        assertEquals(RuntimeDistributionFingerprint.JACAMO_VERSION,CheckpointFeasibility.class.getPackage().getImplementationVersion());
        assertEquals(org.jacamo.bridge.contract.DistributionFingerprint.digestPath(source),
                RuntimeDistributionFingerprint.compute().components().get("jacamo"));
    }
    @Test void bridgeLauncherCannotStartStrictModeWithoutAdmittedPolicy() {
        String previous=System.getProperty(CheckpointFeasibility.MODE_PROPERTY);
        try {
            System.setProperty(CheckpointFeasibility.MODE_PROPERTY,"VERIFIED_CHECKPOINTS");
            var launcher=new BridgeControlledLauncher();
            assertThrows(IllegalStateException.class,()->launcher.init(new String[0]));
            assertThrows(IllegalStateException.class,launcher::create);
            assertThrows(IllegalStateException.class,launcher::start);
            assertTrue(CheckpointFeasibility.strictSupported());
        } finally {
            if(previous==null)System.clearProperty(CheckpointFeasibility.MODE_PROPERTY);
            else System.setProperty(CheckpointFeasibility.MODE_PROPERTY,previous);
        }
    }
}
