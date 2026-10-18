package org.tzi.use.plugins.jacamo.semantics;

import cartago.Op;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.NativeRuntimeReplay;
import static org.junit.jupiter.api.Assertions.*;

class Use4GoalPackagedReplayIT {
    @Test void installedCanonicalReplayUsesReleaseEngineAndHostWithoutFrameworkOrTestClasspath() throws Exception {
        Path root=Path.of(".").toRealPath(),work=Files.createTempDirectory(root.resolve("target"),"canonical-installed-replay-");
        String state,result;Path bundle=Files.createDirectory(work.resolve("bundle"));
        try(var fixture=new CanonicalRuntimeFixture(CslCounterArtifact.class,CanonicalGoalMappingTest::rules)) {
            fixture.settled();fixture.alice.doAction(fixture.artifact,new Op("increment"));fixture.await(()->fixture.value("p").equals("1"));fixture.settled();
            fixture.alice.doAction(fixture.artifact,new Op("increment"));fixture.await(()->fixture.value("p").equals("2"));fixture.settled();fixture.resync();fixture.settled();
            var expected=fixture.projector.coordinator().latest();state=expected.stateHash();result=expected.resultHash();
            new NativeRuntimeReplay().exportBundle(fixture.projector,fixture.built.nativeResult().export().useText(),bundle);
        }
        Path install=Files.createDirectory(work.resolve("install"));
        try(var zip=new ZipFile(root.resolve("target/use-jacamo-plugin-1.0.1-v2-frozen.zip").toFile())) {
            var entry=zip.getEntry("lib/plugins/use-jacamo-plugin-1.0.1.jar");assertNotNull(entry);
            Path destination=install.resolve(entry.getName());Files.createDirectories(destination.getParent());
            try(var input=zip.getInputStream(entry)){Files.copy(input,destination);}
        }
        Path host=install.resolve("lib/use-gui.jar");Files.copy(root.resolve("../use-gui/target/use-gui.jar"),host);
        // Copy exactly this dependency-free driver, not target/test-classes or any fixture.
        Path driver=Files.createDirectory(work.resolve("driver"));String name=PackagedCanonicalReplayMain.class.getName().replace('.','/')+".class";
        Path file=driver.resolve(name);Files.createDirectories(file.getParent());
        try(var input=PackagedCanonicalReplayMain.class.getResourceAsStream("/"+name)){assertNotNull(input);Files.copy(input,file);}
        String cp=host+java.io.File.pathSeparator+driver;assertFalse(cp.contains("test-classes"));
        Path log=work.resolve("installed.log");
        var command=List.of(Path.of(System.getProperty("java.home"),"bin",System.getProperty("os.name").startsWith("Windows")?"java.exe":"java").toString(),"-cp",cp,
                PackagedCanonicalReplayMain.class.getName(),install.toString(),bundle.toString(),state,result);
        Process process=new ProcessBuilder(command).directory(install.toFile()).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        boolean ended=process.waitFor(60,TimeUnit.SECONDS);if(!ended)process.destroyForcibly();
        String text=Files.readString(log);assertTrue(ended,text);assertEquals(0,process.exitValue(),text);assertTrue(text.contains("ISOLATED_ORIGINAL_ENGINE_REPLAY_PASS"),text);
        Files.write(Path.of("target/use4goal-packaged-replay.json"),org.jacamo.bridge.contract.CanonicalJson.encode(Map.of("install",install.toString(),"log",log.toString(),"classpath",cp,"stateHash",state,"resultHash",result,"command",command,"driverClassFiles",1,"testClasspath",false)));
    }
}
