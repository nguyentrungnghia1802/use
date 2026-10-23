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
    private Process producer;
    private final java.util.List<String> producerCommand;
    private final Map<String,String> originalHashes;
    public ManagedProducerTestSupport(Path originalJcm,Path evidence) throws Exception {
        this(originalJcm,evidence,false);
    }
    public ManagedProducerTestSupport(Path originalJcm,Path evidence,boolean structuredJasonObservation) throws Exception {
        this(originalJcm,evidence,structuredJasonObservation,false,Map.of());
    }
    /** Same official managed-start hook; headless mode is bounded and test-owned. */
    public ManagedProducerTestSupport(Path originalJcm,Path evidence,boolean structuredJasonObservation,boolean headless,Map<String,String> vmProperties) throws Exception {
        this(originalJcm,evidence,structuredJasonObservation,headless,vmProperties,entry->true);
    }
    /** Optional test classpath selection; production classes and exact official
     * dependencies are reused rather than launching a second test main. */
    public ManagedProducerTestSupport(Path originalJcm,Path evidence,boolean structuredJasonObservation,boolean headless,Map<String,String> vmProperties,java.util.function.Predicate<String> classpathEntry) throws Exception {
        this(originalJcm,evidence,structuredJasonObservation,headless,vmProperties,classpathEntry,stage->{ });
    }
    /** Explicit test-owned fixture preparation; default callers keep original bytes. */
    public ManagedProducerTestSupport(Path originalJcm,Path evidence,boolean structuredJasonObservation,boolean headless,Map<String,String> vmProperties,java.util.function.Predicate<String> classpathEntry,java.util.function.Consumer<Path> fixture) throws Exception {
        root=evidence.toAbsolutePath().normalize(); Files.createDirectories(root);
        original=originalJcm.toAbsolutePath().normalize(); originalHashes=sourceHashes(original.getParent());
        Path stage=Files.createDirectory(root.resolve("staging"));
        try(var paths=Files.walk(original.getParent())) {
            for(Path input:paths.toList()) {
                Path output=stage.resolve(original.getParent().relativize(input));
                if(Files.isDirectory(input)) Files.createDirectories(output); else Files.copy(input,output);
            }
        }
        fixture.accept(stage);
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
                java.util.Arrays.stream(System.getProperty("java.class.path").split(java.util.regex.Pattern.quote(java.io.File.pathSeparator))).filter(classpathEntry).collect(java.util.stream.Collectors.joining(java.io.File.pathSeparator)));
        String javaCommand=JavaProcessSupport.executable();
        Process fingerprint=new ProcessBuilder(javaCommand,"-cp",cp,"org.jacamo.bridge.adapter.RuntimeDistributionFingerprintMain").start();
        String fingerprintText=new String(fingerprint.getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
        if(!fingerprint.waitFor(10,TimeUnit.SECONDS)||fingerprint.exitValue()!=0) throw new IllegalStateException("FINGERPRINT_FAILED");
        String distribution=fingerprintText.lines().filter(line->line.startsWith("BRIDGE_RUNTIME_DISTRIBUTION_SHA256=")).findFirst().orElseThrow().split("=",2)[1];
        int port; try(var socket=new ServerSocket(0)) { port=socket.getLocalPort(); }
        String text=Files.readString(jcm); int end=text.lastIndexOf('}');
        String platform="    platform: org.jacamo.bridge.adapter.JaCaMoBridgePlatform(\"port="+port+"\", \"secretFile="
                +secret.toString().replace('\\','/')+"\", \"jcmFile="+jcm.toString().replace('\\','/')+"\", \"distributionSha256="+distribution+"\")\n";
        Files.writeString(jcm,text.substring(0,end)+platform+text.substring(end));
        configuration=new BridgeConnectionConfig(URI.create("tcp://127.0.0.1:"+port),secret,distribution,Set.of("official.model","runtime.snapshot"),org.jacamo.bridge.contract.CanonicalJson.DEFAULT_MAX_BYTES,20000,8192);
        Path transportLogging=root.resolve("transport-logging.properties");
        Files.writeString(transportLogging,"handlers=java.util.logging.ConsoleHandler\n.level=INFO\njava.util.logging.ConsoleHandler.level=FINE\norg.jacamo.bridge.adapter.LocalTcpBridgeServer.level=FINE\n");
        var command=new java.util.ArrayList<String>(java.util.List.of(javaCommand,"-Djava.util.logging.config.file="+transportLogging,
                "-Djacamo.bridge.structured-observation="+structuredJasonObservation,"-Djacamo.launch.captureConsole=true"));
        vmProperties.forEach((key,value)->command.add("-D"+key+"="+value));
        command.addAll(java.util.List.of("-cp",cp,"jason.infra.local.LiveJaCaMoLauncherMain",jcm.toString(),stop.toString(),headless?"240":"0",Boolean.toString(headless),"","240",control.toString(),"managed-gui-test"));
        producerCommand=java.util.List.copyOf(command);
        producer=new ProcessBuilder(producerCommand)
                .directory(stage.toFile()).redirectOutput(root.resolve("producer.log").toFile()).redirectError(root.resolve("producer.err").toFile()).start();
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(60);
        while(!Files.exists(control.resolve("waiting.json"))&&producer.isAlive()&&System.nanoTime()<deadline) Thread.sleep(50);
        if(!Files.exists(control.resolve("waiting.json"))) {
            close(); throw new IllegalStateException("MANAGED_BOOTSTRAP_FAILED:exit="+producer.exitValue()+":"+Files.readString(root.resolve("producer.log"))+Files.readString(root.resolve("producer.err")));
        }
    }
    public void configureWorkflow() {
        System.setProperty(ManagedStartupControl.DIRECTORY_PROPERTY,control.toString());
        System.setProperty(ManagedStartupControl.RUN_PROPERTY,"managed-gui-test");
    }
    public void clearWorkflowProperties() { System.clearProperty(ManagedStartupControl.DIRECTORY_PROPERTY); System.clearProperty(ManagedStartupControl.RUN_PROPERTY); }
    public long producerPid(){return producer.pid();}
    public boolean producerAlive(){return producer.isAlive();}
    public boolean awaitProducerExit(long timeoutSeconds) throws InterruptedException{return producer.waitFor(timeoutSeconds,TimeUnit.SECONDS);}
    public int producerExitCode(){return producer.exitValue();}
    /** Restart exactly the staged project and authenticated endpoint owned by this harness. */
    public void restartProducer() throws Exception {
        Files.writeString(stop,"restart test-owned process");
        if(!producer.waitFor(15,TimeUnit.SECONDS))throw new IllegalStateException("TEST_PRODUCER_DID_NOT_STOP");
        var ownerRoot=root.toRealPath();var controlRoot=control.toRealPath();
        if(!controlRoot.startsWith(ownerRoot))throw new IllegalStateException("TEST_CONTROL_OUTSIDE_EVIDENCE_ROOT");
        Files.deleteIfExists(stop);
        for(String file:java.util.List.of("waiting.json","request.json","request.lock","started.json","cancel.json"))Files.deleteIfExists(controlRoot.resolve(file));
        producer=new ProcessBuilder(producerCommand).directory(jcm.getParent().toFile())
                .redirectOutput(root.resolve("producer-restart.log").toFile()).redirectError(root.resolve("producer-restart.err").toFile()).start();
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(60);
        while(!Files.exists(control.resolve("waiting.json"))&&producer.isAlive()&&System.nanoTime()<deadline)Thread.sleep(25);
        if(!Files.exists(control.resolve("waiting.json")))throw new IllegalStateException("TEST_RESTART_BOOTSTRAP_FAILED");
    }
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
