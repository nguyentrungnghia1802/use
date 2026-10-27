package org.tzi.use.plugins.jacamo.semantics;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.jacamo.bridge.contract.CanonicalJson;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Timeout;

class InstalledAuthoredPluginIT {
    /** Qualify the frozen full archive without rebuilding its already-tested JARs. */
    public static void main(String[] args)throws Exception {
        if(System.getProperty("use.packaged.hostArchive","").isBlank())throw new IllegalArgumentException("FULL_USE_ARCHIVE_REQUIRED");
        var tests=new InstalledAuthoredPluginIT();
        tests.coldAuthoredCasesUseTheSameInstalledEngineAndReplayWithoutSelectedInputs("stock");
        tests.coldAuthoredCasesUseTheSameInstalledEngineAndReplayWithoutSelectedInputs("auction");
        tests.coldAuthoredCasesUseTheSameInstalledEngineAndReplayWithoutSelectedInputs("auction-confirmed");
        System.out.println("FULL_USE_ARCHIVE_AUTHORED_ACCEPTANCE_PASS checks=3");
    }
    @ParameterizedTest @ValueSource(strings={"stock","auction","auction-confirmed"}) @Timeout(500)
    void coldAuthoredCasesUseTheSameInstalledEngineAndReplayWithoutSelectedInputs(String mode)throws Exception {
        Path work=Files.createTempDirectory(Path.of("target"),"cold-authored-"+mode+"-").toAbsolutePath();
        Path install=Use4GoalPackagedPluginIT.install(work),driver=Use4GoalPackagedPluginIT.driver(work,InstalledAuthoredMain.class);
        Path evidence=Files.createTempDirectory(Files.createDirectories(Path.of("verification/authored-models/P09-cold-installed")),mode+"-").toAbsolutePath();
        Path framework=Path.of("../jacamo-bridge-jacamo/target/jacamo-bridge-classpath.txt").toRealPath();
        var command=List.of(PackagedProjectProducer.java(),"-cp",install.resolve("lib/use-gui.jar")+java.io.File.pathSeparator+driver,InstalledAuthoredMain.class.getName(),install.toString(),mode,framework.toString(),evidence.toString());
        var process=new ProcessBuilder(command).directory(install.toFile()).redirectErrorStream(true).redirectOutput(evidence.resolve("consumer.log").toFile()).start();
        boolean finished=process.waitFor(440,TimeUnit.SECONDS);if(!finished){process.descendants().forEach(ProcessHandle::destroy);process.destroy();if(!process.waitFor(10,TimeUnit.SECONDS))process.destroyForcibly();}
        assertTrue(finished,evidence.toString());assertEquals(0,process.exitValue(),()->read(evidence.resolve("consumer.log")));
        var receipt=CanonicalJson.object(CanonicalJson.decode(Files.readAllBytes(evidence.resolve("receipt.json"))));assertEquals("PASS",receipt.get("status"));assertEquals(true,receipt.get("sameMSystem"));assertEquals(true,receipt.get("selectedInputsDeleted"));
        assertNotEquals(receipt.get("usePid"),receipt.get("producerPid"));
        assertTrue(receipt.get("captureScopeHash").toString().matches("[0-9a-f]{64}"));
        assertTrue(((Number)receipt.get("scopedEventCount")).longValue()>0,"Actual external TCP scoped callbacks");
        if(mode.equals("stock"))assertEquals("NOT_REQUESTED",receipt.get("producerNplPolicy"));
        String expected=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(Path.of("target/use-plugin-1.0.1.jar"))));assertEquals(expected,receipt.get("pluginSha256"));
        String archive=System.getProperty("use.packaged.hostArchive","");
        var qualification=new TreeMap<String,Object>();qualification.put("status","PASS");qualification.put("case",mode);qualification.put("pluginSha256",expected);
        qualification.put("fullHostArchive",archive.isBlank()?"PLUGIN_RELEASE_PLUS_PINNED_HOST":archive);
        if(!archive.isBlank())qualification.put("fullHostArchiveSha256",HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(Path.of(archive)))));
        Files.write(evidence.resolve("qualification.json"),CanonicalJson.encode(qualification));
    }
    private static String read(Path file){try{return Files.readString(file);}catch(Exception error){return error.toString();}}
}
