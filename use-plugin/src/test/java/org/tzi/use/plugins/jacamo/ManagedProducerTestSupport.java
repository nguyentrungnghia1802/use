package org.tzi.use.plugins.jacamo;

import java.nio.file.Files;
import java.nio.file.Path;
import java.net.URI;
import java.net.ServerSocket;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import javax.tools.ToolProvider;
import org.jacamo.bridge.contract.ManagedStartupControl;
import org.tzi.use.plugins.jacamo.bridge.BridgeConnectionConfig;
import org.tzi.use.plugins.jacamo.codegrounded.constraint.ExternalOclConstraintService;

/** Generic test harness; original ASL/XML/Java/JCM bytes are never edited. */
public final class ManagedProducerTestSupport implements AutoCloseable {
    public final Path root, original, jcm, control, stop;
    public final BridgeConnectionConfig configuration;
    private final Process producer;
    private final Map<String,String> originalHashes;
    public ManagedProducerTestSupport(Path originalJcm,Path evidence) throws Exception {
        root=evidence.toAbsolutePath().normalize(); Files.createDirectories(root);
        original=originalJcm.toAbsolutePath().normalize(); originalHashes=sourceHashes(original.getParent());
        Path stage=Files.createDirectory(root.resolve("staging"));
        try(var paths=Files.walk(original.getParent())) {
            for(Path input:paths.toList()) {
                Path output=stage.resolve(original.getParent().relativize(input));
                if(Files.isDirectory(input)) Files.createDirectories(output); else Files.copy(input,output);
            }
        }
        jcm=stage.resolve(original.getFileName()); control=Files.createDirectory(root.resolve("startup-control"));
        stop=root.resolve("stop"); Path secret=root.resolve("secret.hex"); Files.writeString(secret,"56".repeat(32));
        Path classes=Files.createDirectories(stage.resolve("build/classes/java/main"));
        Path env=stage.resolve("src/env");
        if(Files.isDirectory(env)) try(var sources=Files.walk(env); var files=ToolProvider.getSystemJavaCompiler().getStandardFileManager(null,null,null)) {
            var javaFiles=sources.filter(path->path.toString().endsWith(".java")).toList();
            if(!javaFiles.isEmpty() && !ToolProvider.getSystemJavaCompiler().getTask(null,files,null,
                    java.util.List.of("-classpath",System.getProperty("java.class.path"),"-d",classes.toString()),null,
                    files.getJavaFileObjectsFromPaths(javaFiles)).call()) throw new IllegalStateException("ORIGINAL_PROJECT_JAVA_COMPILE_FAILED");
        }
        String cp=String.join(java.io.File.pathSeparator,classes.toString(),
                Path.of("../jacamo-bridge-contract/target/classes").toAbsolutePath().normalize().toString(),
                Path.of("../jacamo-bridge-jacamo/target/classes").toAbsolutePath().normalize().toString(),
                Path.of("../jacamo-bridge-jacamo/target/test-classes").toAbsolutePath().normalize().toString(),System.getProperty("java.class.path"));
        String javaCommand=JavaProcessSupport.executable();
        Process fingerprint=new ProcessBuilder(javaCommand,"-cp",cp,"org.jacamo.bridge.adapter.RuntimeDistributionFingerprintMain").start();
        String fingerprintText=new String(fingerprint.getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
        if(!fingerprint.waitFor(10,TimeUnit.SECONDS)||fingerprint.exitValue()!=0) throw new IllegalStateException("FINGERPRINT_FAILED");
        String distribution=fingerprintText.lines().filter(line->line.startsWith("BRIDGE_RUNTIME_DISTRIBUTION_SHA256=")).findFirst().orElseThrow().split("=",2)[1];
        int port; try(var socket=new ServerSocket(0)) { port=socket.getLocalPort(); }
        String text=Files.readString(jcm); int end=text.lastIndexOf('}');
        String platform="\n platform: org.jacamo.bridge.adapter.JaCaMoBridgePlatform(\"port="+port+"\", \"secretFile="
                +secret.toString().replace('\\','/')+"\", \"jcmFile="+jcm.toString().replace('\\','/')+"\", \"distributionSha256="+distribution+"\")\n";
        Files.writeString(jcm,text.substring(0,end)+platform+text.substring(end));
        configuration=new BridgeConnectionConfig(URI.create("tcp://127.0.0.1:"+port),secret,distribution,Set.of("official.model","runtime.snapshot"),4*1024*1024,20000,8192);
        producer=new ProcessBuilder(javaCommand,"-cp",cp,"jason.infra.local.LiveJaCaMoLauncherMain",jcm.toString(),stop.toString(),"0","false","","240",control.toString(),"managed-gui-test")
                .directory(stage.toFile()).redirectOutput(root.resolve("producer.log").toFile()).redirectError(root.resolve("producer.err").toFile()).start();
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(60);
        while(!Files.exists(control.resolve("waiting.json"))&&producer.isAlive()&&System.nanoTime()<deadline) Thread.sleep(50);
        if(!Files.exists(control.resolve("waiting.json"))) {
            close(); throw new IllegalStateException("MANAGED_BOOTSTRAP_FAILED:"+Files.readString(root.resolve("producer.log"))+Files.readString(root.resolve("producer.err")));
        }
    }
    public void configureWorkflow() {
        System.setProperty(ManagedStartupControl.DIRECTORY_PROPERTY,control.toString());
        System.setProperty(ManagedStartupControl.RUN_PROPERTY,"managed-gui-test");
    }
    public void clearWorkflowProperties() { System.clearProperty(ManagedStartupControl.DIRECTORY_PROPERTY); System.clearProperty(ManagedStartupControl.RUN_PROPERTY); }
    public static Map<String,String> sourceHashes(Path project) throws Exception {
        var hashes=new java.util.TreeMap<String,String>();
        try(var paths=Files.walk(project)) {
            for(Path path:paths.filter(Files::isRegularFile).filter(p->p.toString().matches(".*\\.(asl|xml|java|jcm|ocl)$")).toList())
                hashes.put(project.relativize(path).toString(),ExternalOclConstraintService.sha256(Files.readAllBytes(path)));
        }
        return Map.copyOf(hashes);
    }
    public boolean sourceUnchanged() throws Exception { return originalHashes.equals(sourceHashes(original.getParent())); }
    @Override public void close() throws Exception {
        clearWorkflowProperties(); Files.writeString(stop,"stop");
        if(!producer.waitFor(10,TimeUnit.SECONDS)) { producer.destroyForcibly(); producer.waitFor(5,TimeUnit.SECONDS); }
        if(!sourceUnchanged()) throw new IllegalStateException("ORIGINAL_CASE_SOURCE_CHANGED");
        Files.deleteIfExists(root.resolve("secret.hex"));
    }
}
