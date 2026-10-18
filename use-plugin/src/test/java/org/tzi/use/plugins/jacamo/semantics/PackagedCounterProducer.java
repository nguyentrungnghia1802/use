package org.tzi.use.plugins.jacamo.semantics;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import javax.tools.ToolProvider;
import org.jacamo.bridge.contract.CanonicalJson;

/** Only released bridge JARs, user-supplied framework JARs and compiled shipped Counter. */
final class PackagedCounterProducer implements AutoCloseable {
    final Path work,jcm,control,secret,stop,ready,release,stopMas;
    final String distribution,endpoint,runId="installed-counter";
    final List<String> command;
    final Process process;
    PackagedCounterProducer(Path install,Path work,int step)throws Exception {
        this.work=work;Path example=install.resolve("examples/csl-counter"),stage=Files.createDirectory(work.resolve("staging"));
        try(var paths=Files.walk(example)){for(Path source:paths.toList()){Path target=stage.resolve(example.relativize(source));if(Files.isDirectory(source))Files.createDirectories(target);else Files.copy(source,target);}}
        jcm=stage.resolve("counter.jcm");control=Files.createDirectory(work.resolve("startup-control"));secret=work.resolve("secret.hex");stop=work.resolve("stop");ready=work.resolve("ready");release=work.resolve("release");stopMas=work.resolve("stop-mas");
        byte[] random=new byte[32];new java.security.SecureRandom().nextBytes(random);Files.writeString(secret,HexFormat.of().formatHex(random));
        var jars=new ArrayList<String>();jars.add(install.resolve("bridge/lib/jacamo-bridge-contract-1.0.0.jar").toString());jars.add(install.resolve("bridge/lib/jacamo-bridge-jacamo-1.0.0.jar").toString());
        for(String entry:System.getProperty("java.class.path").split(java.io.File.pathSeparator)) {
            Path file=Path.of(entry);String normalized=entry.replace('\\','/').toLowerCase(Locale.ROOT);
            if(Files.isRegularFile(file)&&entry.endsWith(".jar")&&!normalized.matches(".*(jacamo-bridge-(contract|jacamo)|use-(plugin|core|gui|jacamo-semantic-engine)|/org/tzi/use/|junit|surefire|archunit|opentest4j|apiguardian).*"))jars.add(file.toAbsolutePath().toString());
        }
        Path classes=Files.createDirectory(stage.resolve("artifact-classes"));String dependencies=String.join(java.io.File.pathSeparator,jars);
        try(var files=ToolProvider.getSystemJavaCompiler().getStandardFileManager(null,null,null);var sources=Files.walk(stage.resolve("src/env"))) {
            if(!ToolProvider.getSystemJavaCompiler().getTask(null,files,null,List.of("-proc:none","-encoding","UTF-8","-classpath",dependencies,"-d",classes.toString()),null,
                    files.getJavaFileObjectsFromPaths(sources.filter(p->p.toString().endsWith(".java")).toList())).call())throw new IllegalStateException("PACKAGED_COUNTER_COMPILE_FAILED");
        }
        String cp=classes+java.io.File.pathSeparator+dependencies;
        if(cp.contains("test-classes")||cp.contains("target/classes"))throw new IllegalStateException("DEVELOPMENT_PRODUCER_CLASSPATH");
        Path fingerprintLog=work.resolve("fingerprint.log");Process fingerprint=new ProcessBuilder(java(),"-cp",cp,"org.jacamo.bridge.adapter.RuntimeDistributionFingerprintMain").redirectErrorStream(true).redirectOutput(fingerprintLog.toFile()).start();
        if(!fingerprint.waitFor(20,TimeUnit.SECONDS)){fingerprint.destroyForcibly();throw new IllegalStateException("FINGERPRINT_TIMEOUT");}
        if(fingerprint.exitValue()!=0)throw new IllegalStateException(Files.readString(fingerprintLog));
        distribution=Files.readAllLines(fingerprintLog).stream().filter(l->l.startsWith("BRIDGE_RUNTIME_DISTRIBUTION_SHA256=")).findFirst().orElseThrow().split("=",2)[1];
        int port;try(var socket=new java.net.ServerSocket(0)){port=socket.getLocalPort();}endpoint="tcp://127.0.0.1:"+port;
        String text=Files.readString(jcm);int end=text.lastIndexOf('}');String platform="\n platform: org.jacamo.bridge.adapter.JaCaMoBridgePlatform(\"port="+port+"\", \"secretFile="+secret.toString().replace('\\','/')+"\", \"jcmFile="+jcm.toString().replace('\\','/')+"\", \"distributionSha256="+distribution+"\")\n";
        Files.writeString(jcm,text.substring(0,end)+platform+text.substring(end));
        command=List.of(java(),"-Djacamo.launch.captureConsole=true","-Djacamo.bridge.structured-observation=false","-Dcounter.demo.step="+step,"-Dcounter.demo.ready="+ready,"-Dcounter.demo.release="+release,"-Dcounter.demo.stopMas="+stopMas,
                "-cp",cp,"jason.infra.local.LiveJaCaMoLauncherMain",jcm.toString(),stop.toString(),"240","true","","240",control.toString(),runId);
        process=new ProcessBuilder(command).directory(stage.toFile()).redirectErrorStream(true).redirectOutput(work.resolve("producer.log").toFile()).start();
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(60);while(process.isAlive()&&!Files.exists(control.resolve("waiting.json"))&&System.nanoTime()<deadline)Thread.sleep(25);
        if(!Files.exists(control.resolve("waiting.json"))){close();throw new IllegalStateException("PACKAGED_PRODUCER_BOOT_FAILED "+Files.readString(work.resolve("producer.log")));}
    }
    static String java(){return Path.of(System.getProperty("java.home"),"bin",System.getProperty("os.name").startsWith("Windows")?"java.exe":"java").toString();}
    Properties consumerProperties(Path install,int step) {
        var p=new Properties();p.setProperty("use.jacamo.authority","bridge");p.setProperty("use.jacamo.bridge.endpoint",endpoint);p.setProperty("use.jacamo.bridge.secret-file",secret.toString());p.setProperty("use.jacamo.bridge.distribution-sha256",distribution);
        p.setProperty("use.jacamo.startup.directory",control.toString());p.setProperty("use.jacamo.startup.run-id",runId);p.setProperty("jcm",jcm.toString());p.setProperty("mapping",install.resolve("examples/csl-counter/mapping.json").toString());p.setProperty("evidence",work.toString());
        p.setProperty("ready",ready.toString());p.setProperty("release",release.toString());p.setProperty("stopMas",stopMas.toString());p.setProperty("producerPid",Long.toString(process.pid()));p.setProperty("step",Integer.toString(step));return p;
    }
    @Override public void close()throws Exception {
        Files.writeString(stop,"stop owned producer");
        if(process!=null&&!process.waitFor(10,TimeUnit.SECONDS)){process.destroyForcibly();process.waitFor(5,TimeUnit.SECONDS);}
        Files.deleteIfExists(secret);
    }
}
