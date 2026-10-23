package org.tzi.use.plugins.jacamo.semantics;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.zip.*;
import org.jacamo.bridge.contract.CanonicalJson;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

/** Release artifact gates: no IDE/test/framework classes on installed consumer classpath. */
class Use4GoalPackagedPluginIT {
    /** Repeat the exact four gates against an already-built full USE archive.
     * Does not rebuild/replace its JARs; Maven also discovers all four tests. */
    public static void main(String[] args)throws Exception {
        if(System.getProperty("use.packaged.hostArchive","").isBlank())throw new IllegalArgumentException("FULL_USE_ARCHIVE_REQUIRED");
        var tests=new Use4GoalPackagedPluginIT();
        tests.shippedCounterRunsOnFreshInstalledPluginWithProductionQuotasAndTruePrePost(1);
        tests.shippedCounterRunsOnFreshInstalledPluginWithProductionQuotasAndTruePrePost(2);
        tests.releaseContainsOneOriginalCorePinnedAntlrAndNoHostOrLegacyBackend();
        tests.deploymentGuardRejectsDuplicateOldEngineMissingResourcesAndWrongVersions();
        System.out.println("FULL_USE_ARCHIVE_ACCEPTANCE_PASS checks=4");
    }
    @ParameterizedTest @ValueSource(ints={1,2})
    void shippedCounterRunsOnFreshInstalledPluginWithProductionQuotasAndTruePrePost(int step)throws Exception {
        Path work=Files.createTempDirectory(Path.of("target"),"installed-counter-"+step+"-").toAbsolutePath(),install=install(work);
        var original=hashes(install.resolve("examples/csl-counter"));Path driver=driver(work);
        try(var producer=new PackagedCounterProducer(install,work,step)) {
            Path properties=work.resolve("consumer.properties");try(var output=Files.newOutputStream(properties)){producer.consumerProperties(install,step).store(output,"Test-owned endpoint/startup metadata; no secret contents");}
            Path log=work.resolve("consumer.log");var command=List.of(PackagedCounterProducer.java(),"-cp",install.resolve("lib/use-gui.jar")+java.io.File.pathSeparator+driver,InstalledEngineMain.class.getName(),install.toString(),properties.toString());
            Process consumer=new ProcessBuilder(command).directory(install.toFile()).redirectErrorStream(true).redirectOutput(log.toFile()).start();
            boolean finished=consumer.waitFor(180,TimeUnit.SECONDS);if(!finished){consumer.destroyForcibly();consumer.waitFor(5,TimeUnit.SECONDS);}
            String output=Files.readString(log);assertTrue(finished,output);assertEquals(0,consumer.exitValue(),output);assertTrue(output.contains("INSTALLED_ORIGINAL_ENGINE_LIVE_PASS"),output);
            assertTrue(producer.process.waitFor(5,TimeUnit.SECONDS));assertEquals(0,producer.process.exitValue(),Files.readString(work.resolve("producer.log")));
            var receipt=CanonicalJson.object(CanonicalJson.decode(Files.readAllBytes(work.resolve("consumer-receipt.json"))));
            assertNotEquals(receipt.get("consumerPid"),receipt.get("producerPid"));
            assertEquals(0,((Number)receipt.get("runtimeChildProcesses")).intValue());
            for(Object child:(List<?>)receipt.get("consumerChildren"))assertEquals("WINDOWS_CONSOLE_HOST",CanonicalJson.object(child).get("kind"));
            assertEquals(original,hashes(install.resolve("examples/csl-counter")));
            var evidence=new TreeMap<String,Object>(receipt);evidence.put("install",install.toString());evidence.put("work",work.toString());evidence.put("consumerCommand",command);evidence.put("producerClasspath",producer.command.get(producer.command.indexOf("-cp")+1));evidence.put("producerExitCode",producer.process.exitValue());evidence.put("fullHostArchive",System.getProperty("use.packaged.hostArchive","PLUGIN_RELEASE_PLUS_PINNED_HOST"));
            evidence.put("observationProfile","WORLD; optional Jason mental/execution trace disabled; actual agents/world/artifacts/organisation/control remain enabled");
            Files.write(Path.of("target/use4goal-installed-counter-"+step+".json"),CanonicalJson.encode(evidence));
        }
    }
    @Test void releaseContainsOneOriginalCorePinnedAntlrAndNoHostOrLegacyBackend()throws Exception {
        Path jar=Path.of("target/use-plugin-1.0.1.jar"),engine=Path.of("../use-jacamo-semantic-engine/target/use-jacamo-semantic-engine-7.5.0.jar");
        try(var installed=new ZipFile(jar.toFile());var original=new ZipFile(engine.toFile())) {
            var names=installed.stream().map(ZipEntry::getName).toList();assertEquals(names.size(),new HashSet<>(names).size(),"No duplicate JAR entries");
            var expected=original.stream().filter(e->e.getName().startsWith("org/vnu/sme/goal/")&&e.getName().endsWith(".class")).map(ZipEntry::getName).collect(java.util.stream.Collectors.toSet());
            assertEquals(expected,new HashSet<>(names.stream().filter(n->n.startsWith("org/vnu/sme/goal/")&&n.endsWith(".class")).toList()));assertTrue(expected.size()>200);
            for(String name:expected)try(var a=original.getInputStream(original.getEntry(name));var b=installed.getInputStream(installed.getEntry(name))){assertArrayEquals(a.readAllBytes(),b.readAllBytes(),name);}
            for(String name:List.of("org/tzi/use/uml/sys/MSystem.class","org/tzi/use/parser/ocl/OCLCompiler.class","cartago/Artifact.class","org/jacamo/bridge/adapter/JaCaMoBridgePlatform.class","org/vnu/sme/goal/translate/csl2use/Csl2UseTranslator.class","org/vnu/sme/goal/dsl/aol/state/CslSystemState.class","org/vnu/sme/goal/feature/opencsl/ActionOpenCsl.class"))assertNull(installed.getEntry(name),name);
            for(String name:List.of("META-INF/use4goal-provenance.json","META-INF/licenses/use4Goal-GPL-3.0.txt","org/tzi/use/plugins/jacamo/release/licenses/ANTLR4-4.9.3.txt","grammars/CSL.g4","grammars/IStar.g4","org/antlr/v4/runtime/RuntimeMetaData.class"))assertNotNull(installed.getEntry(name),name);
            assertEquals(1,names.stream().filter(n->n.equals("useplugin.xml")).count());
        }
    }
    @Test void deploymentGuardRejectsDuplicateOldEngineMissingResourcesAndWrongVersions()throws Exception {
        Path work=Files.createTempDirectory(Path.of("target"),"installed-engine-guards-").toAbsolutePath(),base=install(work),driver=driver(work);
        guard(base,driver,work.resolve("positive.log"),true,"","guard");
        var cases=new LinkedHashMap<String,String>();cases.put("grammar","ENGINE_INSTALLATION_RESOURCE_MISSING");cases.put("provenance","ENGINE_INSTALLATION_RESOURCE_MISSING");cases.put("license","ENGINE_INSTALLATION_RESOURCE_MISSING");cases.put("antlrMissing","ENGINE_INSTALLATION_DEPENDENCY_MISSING");cases.put("antlrVersion","ENGINE_INSTALLATION_ANTLR_VERSION_UNSUPPORTED");cases.put("hostVersion","ENGINE_INSTALLATION_HOST_VERSION_UNSUPPORTED");cases.put("duplicate","ENGINE_INSTALLATION_DUPLICATE_INSTALLED_ENGINE");
        cases.put("model","ENGINE_INSTALLATION_DEPENDENCY_MISSING");cases.put("profile","CANONICAL_MAPPING_RULESET_FILE_INVALID");
        for(var entry:cases.entrySet()) {
            Path bad=Files.createDirectory(work.resolve(entry.getKey()));
            Files.createDirectories(bad.resolve("lib/plugins"));
            // Immutable host bytes can be linked inside this test-owned tree. Version
            // negative rewrites a new ZIP and replaces only its own directory entry.
            Files.createLink(bad.resolve("lib/use-gui.jar"),base.resolve("lib/use-gui.jar"));
            Path plugin=bad.resolve("lib/plugins/use-jacamo-plugin-1.0.1.jar");Files.copy(base.resolve("lib/plugins/use-jacamo-plugin-1.0.1.jar"),plugin);
            switch(entry.getKey()) {
                case "grammar"->rewrite(plugin,n->n.equals("grammars/CSL.g4"),null,null);
                case "provenance"->rewrite(plugin,n->n.equals("META-INF/use4goal-provenance.json"),null,null);
                case "license"->rewrite(plugin,n->n.equals("META-INF/licenses/use4Goal-GPL-3.0.txt"),null,null);
                case "antlrMissing"->rewrite(plugin,n->n.startsWith("org/antlr/v4/runtime/"),null,null);
                case "antlrVersion"->rewrite(plugin,n->false,"org/antlr/v4/runtime/RuntimeMetaData.class",new String[]{"4.9.3","4.9.2"});
                case "hostVersion"->rewrite(bad.resolve("lib/use-gui.jar"),n->false,"org/tzi/use/config/Options.class",new String[]{"7.5.0","7.1.1"});
                case "model"->rewrite(plugin,n->n.equals("org/vnu/sme/goal/dsl/istar/mm/GoalModel.class"),null,null);
                case "profile"->{ /* Authored profile file is absent from this isolated negative install. */ }
                case "duplicate"->{try(var source=new ZipFile(plugin.toFile());var zip=new ZipOutputStream(Files.newOutputStream(bad.resolve("lib/plugins/goal-stale.jar")))) {
                    zip.putNextEntry(new ZipEntry("META-INF/MANIFEST.MF"));zip.write("Manifest-Version: 1.0\r\nMain-Class: org.tzi.use.plugins.jacamo.JaCaMoPlugin\r\n\r\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII));zip.closeEntry();
                    String name="org/vnu/sme/goal/dsl/csl/mm/CslModel.class";zip.putNextEntry(new ZipEntry(name));try(var input=source.getInputStream(source.getEntry(name))){input.transferTo(zip);}zip.closeEntry();
                    zip.putNextEntry(new ZipEntry("useplugin.xml"));zip.write("<plugin name=\"StaleGoal\" version=\"0.0.1\" class=\"absent.StaleGoal\"/>".getBytes(java.nio.charset.StandardCharsets.UTF_8));zip.closeEntry();
                }}
                default->throw new AssertionError(entry);
            }
            guard(bad,driver,work.resolve(entry.getKey()+".log"),false,entry.getValue(),entry.getKey().equals("profile")?"profile":"guard");
        }
        Files.write(Path.of("target/use4goal-installed-guards.json"),CanonicalJson.encode(Map.of("status","PASS","negativeCases",cases,"work",work.toString())));
    }
    private static void guard(Path install,Path driver,Path log,boolean success,String diagnostic,String mode)throws Exception {
        var process=new ProcessBuilder(PackagedCounterProducer.java(),"-cp",install.resolve("lib/use-gui.jar")+java.io.File.pathSeparator+driver,InstalledEngineMain.class.getName(),install.toString(),mode).directory(install.toFile()).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        boolean finished=process.waitFor(25,TimeUnit.SECONDS);if(!finished)process.destroyForcibly();String text=Files.readString(log);assertTrue(finished,text);
        if(success){assertEquals(0,process.exitValue(),text);assertTrue(text.contains("INSTALLED_ENGINE_GUARD_PASS"),text);}else{assertNotEquals(0,process.exitValue(),text);assertTrue(text.contains(diagnostic),text);}
    }
    static Path install(Path work)throws Exception {
        Path install=Files.createDirectory(work.resolve("install"));String full=System.getProperty("use.packaged.hostArchive","");
        if(!full.isBlank()) {
            extract(Path.of(full),install);try(var children=Files.list(install)){install=children.filter(p->Files.isDirectory(p.resolve("lib"))).findFirst().orElseThrow();}
        } else {extract(Path.of("target/use-jacamo-plugin-1.0.1-v2-frozen.zip"),install);Files.copy(Path.of("../use-gui/target/use-gui.jar"),install.resolve("lib/use-gui.jar"));}
        assertArrayEquals(Files.readAllBytes(Path.of("target/use-plugin-1.0.1.jar")),Files.readAllBytes(install.resolve("lib/plugins/use-jacamo-plugin-1.0.1.jar")),"Stale full distribution plugin");return install;
    }
    private static Path driver(Path work)throws Exception {return driver(work,InstalledEngineMain.class);}
    static Path driver(Path work,Class<?> driverType)throws Exception {
        Path driver=Files.createDirectory(work.resolve("driver"));String prefix=driverType.getSimpleName(),resource=driverType.getPackageName().replace('.','/');Path source=Path.of("target/test-classes").resolve(resource);
        try(var paths=Files.list(source)){for(Path file:paths.filter(p->p.getFileName().toString().startsWith(prefix)&&p.getFileName().toString().endsWith(".class")).toList()){Path target=driver.resolve(resource).resolve(file.getFileName());Files.createDirectories(target.getParent());Files.copy(file,target);}}
        return driver;
    }
    private static void extract(Path archive,Path destination)throws Exception {
        try(var zip=new ZipFile(archive.toFile())){for(var entry:Collections.list(zip.entries())){Path target=destination.resolve(entry.getName()).normalize();assertTrue(target.startsWith(destination));if(entry.isDirectory()){Files.createDirectories(target);continue;}Files.createDirectories(target.getParent());try(var input=zip.getInputStream(entry)){Files.copy(input,target);}}}
    }
    private static void copy(Path source,Path destination)throws Exception {try(var paths=Files.walk(source)){for(Path file:paths.toList()){Path target=destination.resolve(source.relativize(file));if(Files.isDirectory(file))Files.createDirectories(target);else Files.copy(file,target);}}}
    private static Map<String,String> hashes(Path source)throws Exception {var result=new TreeMap<String,String>();try(var paths=Files.walk(source)){for(Path file:paths.filter(Files::isRegularFile).toList())result.put(source.relativize(file).toString(),HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file))));}return result;}
    private static void rewrite(Path jar,java.util.function.Predicate<String> remove,String patch,String[] replace)throws Exception {
        Path temp=jar.resolveSibling(jar.getFileName()+".test-rewrite");boolean patched=patch==null;
        try(var source=new ZipFile(jar.toFile());var output=new ZipOutputStream(Files.newOutputStream(temp))){for(var entry:Collections.list(source.entries())){if(remove.test(entry.getName()))continue;byte[] bytes;try(var input=source.getInputStream(entry)){bytes=input.readAllBytes();}
            if(entry.getName().equals(patch)){byte[] from=replace[0].getBytes(java.nio.charset.StandardCharsets.US_ASCII),to=replace[1].getBytes(java.nio.charset.StandardCharsets.US_ASCII);assertEquals(from.length,to.length);for(int i=0;i<=bytes.length-from.length;i++){boolean found=true;for(int j=0;j<from.length;j++)if(bytes[i+j]!=from[j])found=false;if(found){System.arraycopy(to,0,bytes,i,to.length);patched=true;}}}
            output.putNextEntry(new ZipEntry(entry.getName()));output.write(bytes);output.closeEntry();}}
        assertTrue(patched,"Negative fixture constant was not patched");Files.move(temp,jar,StandardCopyOption.REPLACE_EXISTING);
    }
}
