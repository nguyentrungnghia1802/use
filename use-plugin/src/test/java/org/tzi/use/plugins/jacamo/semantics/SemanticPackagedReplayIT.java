package org.tzi.use.plugins.jacamo.semantics;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.tzi.use.plugins.jacamo.codegrounded.*;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.*;
import org.tzi.use.plugins.jacamo.codegrounded.use.*;
import org.tzi.use.plugins.jacamo.semantics.mapping.*;
import org.tzi.use.plugins.jacamo.semantics.evidence.*;

/** Installed release oracle: child sees the USE distribution and plugin, no Maven adapter classpath. */
class SemanticPackagedReplayIT {
    @Test void installedReleaseReplaysMappedSevenValuedAndTemporalHistory() throws Exception {
        Path root=Path.of(".").toRealPath();
        var fixture=VerificationLoadScenarioTest.fixture(new VerificationLoadScenarioTest.Workload(1,1,1,1,4,1,0,1));
        var mapping=new SemanticTransformationService().fromOfficial(fixture.source(),fixture.config());
        var built=new CodeGroundedNativePipeline().buildSemanticBundle(fixture.source(),NativeProjectionMode.AUTO,mapping).nativeResult();
        Path directory=Files.createTempDirectory(root.resolve("target"),"installed-semantic-replay-");
        try(var projector=new NativeRuntimeProjector(built,"benchmark",1,fixture.source().modelRevision())) {
            projector.applySnapshot(VerificationLoadScenarioTest.snapshot(fixture));
            projector.apply(VerificationLoadScenarioTest.delta(fixture,"cartago",1,0,1));
            projector.apply(VerificationLoadScenarioTest.delta(fixture,"cartago",2,0,-1));
            var expected=projector.coordinator().lastObservation();
            assertTrue(expected.semantic().constraints().stream().anyMatch(r->r.verdict()==SemanticVerdict.FAIL));
            assertEquals(4,expected.semantic().requirements().size());assertEquals(org.tzi.use.plugins.jacamo.semantics.istar.IntentionalMarking.Status.SATISFIED,expected.semantic().requirements().get("goal1").evaluated());assertTrue(expected.semantic().requirements().values().stream().noneMatch(m->m.historyGap()));
            Path bundle=Files.createDirectory(directory.resolve("bundle"));new NativeRuntimeReplay().exportBundle(projector,built.export().useText(),bundle);
            assertTrue(Files.isRegularFile(bundle.resolve("mapping.json")));
            Path plugins=Files.createDirectories(directory.resolve("install/lib/plugins"));Path jar=plugins.resolve("use-jacamo-plugin-1.0.1.jar");
            try(var zip=new ZipFile(root.resolve("target/use-jacamo-plugin-1.0.1-v2-frozen.zip").toFile());var input=zip.getInputStream(zip.getEntry("lib/plugins/use-jacamo-plugin-1.0.1.jar"))){Files.copy(input,jar);}
            String javaName=System.getProperty("os.name").startsWith("Windows")?"java.exe":"java";
            String classpath=root.resolve("../use-gui/target/use-gui.jar").toRealPath()+java.io.File.pathSeparator+root.resolve("target/test-classes");Path output=directory.resolve("installed-semantic-replay.log");
            var process=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin",javaName).toString(),"-cp",classpath,NativeRuntimeReplaySmokeMain.class.getName(),plugins.toString(),jar.toString(),bundle.toString(),expected.stateHash(),expected.resultHash(),"semantic").directory(root.toFile()).redirectErrorStream(true).redirectOutput(output.toFile()).start();
            boolean finished=process.waitFor(45,TimeUnit.SECONDS);if(!finished)process.destroyForcibly();assertTrue(finished,"Installed semantic replay timeout");String diagnostic=Files.readString(output);assertEquals(0,process.exitValue(),diagnostic);assertTrue(diagnostic.contains("ISOLATED_SEMANTIC_REPLAY_PASS"),diagnostic);assertTrue(diagnostic.contains("ISOLATED_NATIVE_STEP_REPLAY_PASS"),diagnostic);
        }
    }
}
