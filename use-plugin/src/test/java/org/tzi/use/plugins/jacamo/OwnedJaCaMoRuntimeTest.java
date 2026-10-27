package org.tzi.use.plugins.jacamo;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayInputStream;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import org.jacamo.bridge.contract.CanonicalJson;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OwnedJaCaMoRuntimeTest {
    @TempDir Path temp;
    @Test void installedHostHomeWinsOverAnUnrelatedWorkingCheckout()throws Exception {
        var before=org.tzi.use.config.Options.homeDir;
        try {
            Files.createDirectory(temp.resolve("Core"));org.tzi.use.config.Options.homeDir=temp;
            var detect=DefaultJaCaMoFacade.class.getDeclaredMethod("detectCheckout");detect.setAccessible(true);
            assertEquals(temp.toAbsolutePath().normalize(),detect.invoke(null));
        }finally{org.tzi.use.config.Options.homeDir=before;}
    }
    @Test void javaLaunchDoesNotLeakPowerShellSevenModulesIntoWindowsPowerShell()throws Exception {
        var process=new ProcessBuilder("powershell.exe","-NoProfile","-NonInteractive","-Command","Get-Command Get-FileHash -ErrorAction Stop | Select-Object -ExpandProperty Name");
        process.environment().put("PSModulePath","not-a-windows-powershell-module-directory");
        OwnedJaCaMoRuntime.configurePreparationEnvironment(process);assertFalse(process.environment().containsKey("PSModulePath"));
        if(System.getProperty("os.name").startsWith("Windows")) {
            var child=process.redirectErrorStream(true).start();
            assertTrue(child.waitFor(10,java.util.concurrent.TimeUnit.SECONDS));
            String output=new String(child.getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
            assertEquals(0,child.exitValue(),output);assertTrue(output.contains("Get-FileHash"),output);
        }
    }
    @Test void preparedManifestPinsOwnerSourceDerivedBytesAndOwnedPaths()throws Exception {
        Path root=Files.createDirectory(temp.resolve("owned")),source=temp.resolve("source.jcm");Files.writeString(source,"mas sample {}");
        Path stage=Files.createDirectory(root.resolve("stage")),derived=stage.resolve("derived.jcm");Files.writeString(derived,"mas derived {}");
        var m=new LinkedHashMap<String,Object>();m.put("version","OWNED_JACAMO_PREPARATION/1.0");m.put("preparationId","nonce");m.put("sourceJcm",source.toString());m.put("sourceSha256",hash(source));
        m.put("derivedJcm",derived.toString());m.put("derivedSha256",hash(derived));m.put("stagedProject",stage.toString());
        m.put("stopFile",stage.resolve("stop.flag").toString());m.put("secretFile",stage.resolve("secret.hex").toString());m.put("startupDirectory",root.resolve("startup").toString());m.put("runEvidence",root.resolve("evidence").toString());m.put("observationPlan",root.resolve("observation.json").toString());m.put("classpath","selected-framework-jars");
        Path file=root.resolve("prepared.json");Files.write(file,CanonicalJson.encode(m));
        assertEquals(m,OwnedJaCaMoRuntime.readPreparation(file,root,"nonce",source));
        assertThrows(IllegalArgumentException.class,()->OwnedJaCaMoRuntime.readPreparation(file,root,"foreign",source));
        Files.writeString(source,"mas changed {}");assertThrows(IllegalArgumentException.class,()->OwnedJaCaMoRuntime.readPreparation(file,root,"nonce",source));
        m.put("sourceSha256",hash(source));m.put("stopFile",temp.resolve("unowned.flag").toString());Files.write(file,CanonicalJson.encode(m));
        assertThrows(IllegalArgumentException.class,()->OwnedJaCaMoRuntime.readPreparation(file,root,"nonce",source));
        assertThrows(IllegalArgumentException.class,()->OwnedJaCaMoRuntime.inside(root,root.toString()));
        assertThrows(IllegalArgumentException.class,()->OwnedJaCaMoRuntime.inside(root,root.resolve("../other").toString()));
    }
    @Test void outputCaptureHasFiniteMemoryAndFileEvenWithoutNewlines()throws Exception {
        byte[] flood=new byte[3*1024*1024];Arrays.fill(flood,(byte)'x');Path log=temp.resolve("flood.log");
        var capture=new OwnedJaCaMoRuntime.Capture(new ByteArrayInputStream(flood),log);
        long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
        while(capture.tail().length()<32768&&System.nanoTime()<deadline)Thread.sleep(10);
        capture.close();assertEquals(32768,capture.tail().length());assertEquals(1024*1024,Files.size(log));
    }
    @Test void ownershipServiceRejectsEdtAndDoesNotTouchUnownedProcess()throws Exception {
        var runtime=new OwnedJaCaMoRuntime(temp);
        try {
            javax.swing.SwingUtilities.invokeAndWait(()->assertThrows(IllegalStateException.class,()->runtime.prepare(temp.resolve("model.jcm"),new JaCaMoFacade.ImportSelection("NATIVE_CURRENT",null),null)));
            assertEquals("IDLE",runtime.view().state());runtime.stop();assertEquals("STOPPED",runtime.view().state());
        }finally{runtime.close();}
        assertTrue(ProcessHandle.current().isAlive());
    }
    private static String hash(Path p)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(p)));}
}
