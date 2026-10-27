package org.tzi.use.plugins.jacamo;

import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.jacamo.bridge.contract.CanonicalJson;
import org.jacamo.bridge.contract.ProcessOwnerLease;
import org.tzi.use.plugins.jacamo.bridge.BridgeConnectionConfig;

/** Owns only processes created by this USE instance. No JaCaMo classes enter the USE VM. */
final class OwnedJaCaMoRuntime implements AutoCloseable {
    static final String CLASSPATH_PROPERTY="use.jacamo.runtime.classpath-file";
    private final Path checkout;
    private final Thread shutdown;
    private volatile Attempt active;
    private volatile View view=new View("IDLE",0,"","");
    private volatile boolean closed;
    record View(String state,long pid,String runId,String diagnostic) { }
    record Prepared(BridgeConnectionConfig bridge,Path project,Path startup,String runId,Path observations) { }
    private static final class Attempt {
        final Path root; final String nonce; volatile boolean cancelled;
        volatile Process preparing,producer; volatile Path stop,stage; volatile String run="";
        final List<Capture> captures=new java.util.concurrent.CopyOnWriteArrayList<>();
        Attempt(Path root,String nonce){this.root=root;this.nonce=nonce;}
    }
    OwnedJaCaMoRuntime(Path checkout) {
        this.checkout=checkout.toAbsolutePath().normalize();
        shutdown=Thread.ofPlatform().unstarted(this::close);
        Runtime.getRuntime().addShutdownHook(shutdown);
    }
    View view(){var a=active;var current=view;if(a!=null&&a.producer!=null&&!a.producer.isAlive()&&!a.cancelled)return new View("EXITED",a.producer.pid(),a.run,"PRODUCER_EXITED:"+a.producer.exitValue());return current;}
    boolean active(){var a=active;return a!=null&&!a.cancelled;}
    synchronized void started(){var a=active;if(a!=null&&!a.cancelled&&a.producer!=null&&a.producer.isAlive())view=new View("RUNNING",a.producer.pid(),a.run,"");}
    Prepared prepare(Path source,JaCaMoFacade.ImportSelection selection,Path classpath) {
        if(javax.swing.SwingUtilities.isEventDispatchThread())throw new IllegalStateException("PRODUCER_PREPARATION_REQUIRES_WORKER");
        if(selection==null)throw new IllegalArgumentException("EXPLICIT_IMPORT_SELECTION_REQUIRED");
        Attempt a;
        synchronized(this) {
            if(closed)throw new IllegalStateException("PRODUCER_OWNER_CLOSED");
            if(active!=null)throw new IllegalStateException("OWNED_PRODUCER_ALREADY_EXISTS");
            String nonce=UUID.randomUUID().toString();
            Path base=Files.isDirectory(checkout.resolve("use-plugin"))?checkout.resolve("use-plugin/target/owned-runtime"):checkout.resolve("work/owned-runtime");
            a=new Attempt(base.resolve(nonce),nonce);active=a;
            view=new View("PREPARING",0,"","");
        }
        try {
            Files.createDirectories(a.root);
            Path script=checkout.resolve("use-plugin/tools/jacamo-bridge.ps1");
            if(!Files.isRegularFile(script))script=checkout.resolve("tools/jacamo-bridge.ps1");
            if(!Files.isRegularFile(script))throw new IllegalStateException("PRODUCER_PREPARATION_SCRIPT_MISSING");
            Path result=a.root.resolve("prepared.json");
            Path runtimeCp=classpath!=null?classpath:defaultClasspath(checkout);
            List<String> command=new ArrayList<>(List.of("powershell.exe","-NoProfile","-NonInteractive","-ExecutionPolicy","Bypass","-File",script.toString(),
                    "-PrepareOnly","-SkipBuild","-PreparationResultPath",result.toString(),"-PreparationId",a.nonce,
                    "-EvidenceDirectory",a.root.resolve("evidence").toString(),"-TimeoutSeconds","180","-RuntimeClasspathFile",runtimeCp.toString(),
                    "-JcmPath",source.toAbsolutePath().normalize().toString(),"-SemanticProfile",selection.profile()));
            argument(command,"-MappingSpecificationPath",selection.specification());argument(command,"-AuthoredOclPath",selection.ocl());
            argument(command,"-AuthoredCslPath",selection.csl());argument(command,"-AuthoredIstarPath",selection.istar());
            a.preparing=launch(a,command,checkout,"prepare");
            await(a,a.preparing,Duration.ofMinutes(3));
            if(a.preparing.exitValue()!=0)throw new IllegalStateException("PRODUCER_PREPARATION_FAILED:"+tail(a));
            check(a);
            var manifest=readPreparation(result,a.root,a.nonce,source);
            a.stop=inside(a.root,string(manifest,"stopFile"));a.stage=inside(a.root,string(manifest,"stagedProject"));a.run=string(manifest,"runId");
            Path project=inside(a.root,string(manifest,"derivedJcm")),startup=inside(a.root,string(manifest,"startupDirectory"));
            Path observations=string(manifest,"observationPlan").isBlank()?null:inside(a.root,string(manifest,"observationPlan"));
            var bridge=new BridgeConnectionConfig(URI.create(string(manifest,"endpoint")),inside(a.root,string(manifest,"secretFile")),
                    string(manifest,"distributionSha256"),Set.of("official.model","runtime.snapshot"),CanonicalJson.DEFAULT_MAX_BYTES,60_000,1024);
            List<String> java=new ArrayList<>(List.of(javaExecutable()));
            if(!(manifest.get("producerJvmArguments") instanceof List<?> options))throw new IllegalArgumentException("PRODUCER_JVM_ARGUMENTS_REQUIRED");
            for(Object item:options) {
                if(!(item instanceof String option)||!option.startsWith("-Djacamo.bridge.")||option.indexOf('\0')>=0)throw new IllegalArgumentException("PRODUCER_JVM_ARGUMENT_INVALID");
                java.add(option);
            }
            var owner=ProcessOwnerLease.current();
            java.addAll(List.of("-cp",string(manifest,"classpath"),"jason.infra.local.LiveJaCaMoLauncherMain",project.toString(),a.stop.toString(),
                    "0","true","",string(manifest,"startupSeconds"),startup.toString(),a.run,Long.toString(owner.pid()),Long.toString(owner.startedAtEpochMillis())));
            a.producer=launch(a,java,a.stage,"producer");
            long deadline=System.nanoTime()+Duration.ofMinutes(3).toNanos();
            while(!Files.isRegularFile(startup.resolve("waiting.json"))) {
                check(a);if(!a.producer.isAlive())throw new IllegalStateException("OWNED_PRODUCER_BOOTSTRAP_FAILED:"+tail(a));
                if(System.nanoTime()>deadline)throw new IllegalStateException("OWNED_PRODUCER_BOOTSTRAP_TIMEOUT");Thread.sleep(30);
            }
            check(a);view=new View("WAITING_FOR_START",a.producer.pid(),a.run,"");
            return new Prepared(bridge,project,startup,a.run,observations);
        }catch(Exception failure) {
            stopAttempt(a);view=new View("ERROR",0,a.run,failure.getMessage());
            if(failure instanceof InterruptedException)Thread.currentThread().interrupt();
            if(failure instanceof RuntimeException runtime)throw runtime;
            throw new IllegalStateException("OWNED_PRODUCER_PREPARATION_FAILED",failure);
        }
    }
    static Path defaultClasspath(Path checkout) {
        String configured=System.getProperty(CLASSPATH_PROPERTY,"").trim();
        return configured.isEmpty()?checkout.resolve("jacamo-bridge-jacamo/target/jacamo-bridge-classpath.txt"):Path.of(configured).toAbsolutePath().normalize();
    }
    private static void argument(List<String> command,String key,Path value){if(value!=null){command.add(key);command.add(value.toString());}}
    private static String javaExecutable(){return Path.of(System.getProperty("java.home"),"bin",System.getProperty("os.name").startsWith("Windows")?"java.exe":"java").toString();}
    private Process launch(Attempt a,List<String> command,Path directory,String name)throws Exception {
        check(a);var builder=new ProcessBuilder(command).directory(directory.toFile());
        if(command.getFirst().equalsIgnoreCase("powershell.exe"))configurePreparationEnvironment(builder);
        builder.environment().put("JAVA_HOME",System.getProperty("java.home"));
        builder.environment().put("PATH",Path.of(System.getProperty("java.home"),"bin")+java.io.File.pathSeparator+builder.environment().getOrDefault("PATH",""));
        Process p=builder.start();
        a.captures.add(new Capture(p.getInputStream(),a.root.resolve(name+".stdout.log")));
        a.captures.add(new Capture(p.getErrorStream(),a.root.resolve(name+".stderr.log")));
        if(a.cancelled){terminate(p);throw new IllegalStateException("OWNED_PRODUCER_CANCELLED");}return p;
    }
    static void configurePreparationEnvironment(ProcessBuilder builder) {
        // Unlike a direct pwsh invocation, Java does not sanitize PS7's module
        // paths before starting Windows PowerShell 5.1. Let that host construct
        // its own module path; otherwise even Get-FileHash may be unavailable.
        builder.environment().keySet().removeIf(key->key.equalsIgnoreCase("PSModulePath"));
    }
    private static void await(Attempt a,Process process,Duration timeout)throws Exception {
        long end=System.nanoTime()+timeout.toNanos();
        while(!process.waitFor(100,TimeUnit.MILLISECONDS)){check(a);if(System.nanoTime()>end)throw new IllegalStateException("OWNED_PREPARATION_TIMEOUT");}
    }
    private static void check(Attempt a){if(a.cancelled||Thread.currentThread().isInterrupted())throw new IllegalStateException("OWNED_PRODUCER_CANCELLED");}
    private static String tail(Attempt a){return a.captures.stream().map(Capture::tail).reduce("",(l,r)->l+"\n"+r);}
    static Map<String,Object> readPreparation(Path file,Path root,String nonce,Path source)throws Exception {
        if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)||Files.size(file)>4*1024*1024)throw new IllegalArgumentException("PRODUCER_PREPARATION_MANIFEST_INVALID");
        var m=CanonicalJson.object(CanonicalJson.decode(Files.readAllBytes(file)));
        if(!"OWNED_JACAMO_PREPARATION/1.0".equals(m.get("version"))||!nonce.equals(m.get("preparationId")))throw new IllegalArgumentException("PRODUCER_PREPARATION_OWNER_MISMATCH");
        if(!source.toAbsolutePath().normalize().equals(Path.of(string(m,"sourceJcm")).toAbsolutePath().normalize())||!hash(source).equals(m.get("sourceSha256")))throw new IllegalArgumentException("PRODUCER_SOURCE_CHANGED");
        Path stage=inside(root,string(m,"stagedProject")),derived=inside(root,string(m,"derivedJcm"));
        if(!derived.startsWith(stage)||!hash(derived).equals(m.get("derivedSha256")))throw new IllegalArgumentException("PRODUCER_DERIVED_PROJECT_MISMATCH");
        for(String key:List.of("stopFile","secretFile"))if(!inside(root,string(m,key)).startsWith(stage))throw new IllegalArgumentException("PRODUCER_STAGE_PATH_MISMATCH");
        inside(root,string(m,"startupDirectory"));inside(root,string(m,"runEvidence"));
        if(!string(m,"observationPlan").isBlank())inside(root,string(m,"observationPlan"));
        if(string(m,"classpath").isBlank())throw new IllegalArgumentException("PRODUCER_CLASSPATH_EMPTY");
        return m;
    }
    static Path inside(Path root,String text)throws Exception {
        Path base=root.toAbsolutePath().normalize(),path=Path.of(text).toAbsolutePath().normalize();
        if(!path.startsWith(base)||path.equals(base))throw new IllegalArgumentException("OWNED_PATH_ESCAPES_PREPARATION");
        for(Path part=path;part!=null&&part.startsWith(base);part=part.getParent()) {
            if(Files.exists(part,LinkOption.NOFOLLOW_LINKS)&&(!Files.isDirectory(part,LinkOption.NOFOLLOW_LINKS)&&!Files.isRegularFile(part,LinkOption.NOFOLLOW_LINKS)||Files.isSymbolicLink(part)))throw new IllegalArgumentException("OWNED_PATH_LINK_FORBIDDEN");
        }
        if(Files.exists(path)&&!path.toRealPath().startsWith(base.toRealPath()))throw new IllegalArgumentException("OWNED_REAL_PATH_ESCAPES_PREPARATION");
        return path;
    }
    private static String hash(Path path)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));}
    private static String string(Map<String,Object> m,String key){var v=m.get(key);if(v==null)throw new IllegalArgumentException("PRODUCER_PREPARATION_FIELD_MISSING:"+key);return v.toString();}
    void stop(){var a=active;if(a!=null)stopAttempt(a);if(!view.state().equals("CLEANUP_ERROR"))view=new View("STOPPED",0,a==null?"":a.run,"");}
    private void stopAttempt(Attempt a) {
        a.cancelled=true;
        try {
            if(a.stop!=null){inside(a.root,a.stop.toString());Files.writeString(a.stop,"OWNED_USE_STOP",StandardCharsets.UTF_8,StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING,LinkOption.NOFOLLOW_LINKS);}
            if(a.producer!=null&&!a.producer.waitFor(8,TimeUnit.SECONDS))terminate(a.producer);
        }catch(Exception failure){if(failure instanceof InterruptedException)Thread.currentThread().interrupt();if(a.producer!=null)terminate(a.producer);}
        if(a.preparing!=null)terminate(a.preparing);
        a.captures.forEach(Capture::close);
        // This fresh nonce directory owns the entire staging/control subtree,
        // including partial preparations that failed before producing a manifest.
        // Keep only the bounded outer diagnostics and preparation receipt.
        try {
            if(a.stage!=null)Files.deleteIfExists(inside(a.root,a.stage.resolve("bridge-secret.hex").toString()));
            Path evidence=inside(a.root,a.root.resolve("evidence").toString());
            if(Files.exists(evidence)) {
                try(var files=Files.walk(evidence)){for(Path p:files.sorted(Comparator.reverseOrder()).toList()){inside(a.root,p.toString());Files.deleteIfExists(p);}}
            }
        }catch(Exception failure){view=new View("CLEANUP_ERROR",0,a.run,failure.toString());}
        synchronized(this){if(active==a)active=null;}
    }
    private static void terminate(Process process) {
        var children=process.descendants().toList();children.reversed().forEach(ProcessHandle::destroy);process.destroy();
        try{if(!process.waitFor(1500,TimeUnit.MILLISECONDS))process.destroyForcibly();}catch(InterruptedException e){Thread.currentThread().interrupt();process.destroyForcibly();}
        children.stream().filter(ProcessHandle::isAlive).forEach(ProcessHandle::destroyForcibly);
    }
    @Override public void close(){closed=true;stop();if(Thread.currentThread()!=shutdown)try{Runtime.getRuntime().removeShutdownHook(shutdown);}catch(IllegalStateException ignored){/* JVM shutdown already owns cleanup. */}}
    /** Fixed memory and file bounds even when a child never emits a newline. */
    static final class Capture implements AutoCloseable {
        private final InputStream input;private final Thread reader;private final StringBuilder tail=new StringBuilder();
        Capture(InputStream input,Path log){this.input=input;reader=Thread.ofPlatform().daemon().name("owned-jacamo-output").start(()->{
            try(var output=Files.newOutputStream(log)){byte[] buffer=new byte[4096];long written=0;int n;
                while((n=input.read(buffer))>=0){synchronized(tail){tail.append(new String(buffer,0,n,StandardCharsets.UTF_8));if(tail.length()>32768)tail.delete(0,tail.length()-32768);}
                    int keep=(int)Math.min(n,Math.max(0,1024*1024-written));if(keep>0){output.write(buffer,0,keep);written+=keep;}}
            }catch(Exception ignored){/* Process exit closes streams; the process status remains authoritative. */}
        });}
        String tail(){synchronized(tail){return tail.toString();}}
        @Override public void close(){try{input.close();reader.join(1500);}catch(Exception e){if(e instanceof InterruptedException)Thread.currentThread().interrupt();}}
    }
}
