package org.tzi.use.plugins.jacamo.semantics;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import javax.tools.ToolProvider;
import org.jacamo.bridge.contract.CanonicalJson;

/** Shared installed-project harness; producer has released bridge and framework JARs only. */
class PackagedProjectProducer implements AutoCloseable {
    final Path work,jcm,control,secret,stop,ready,release,stopMas;
    final String distribution,endpoint,runId;
    final List<String> command;
    final Process process;
    PackagedProjectProducer(Path install,Path work,int step,boolean auction)throws Exception {
        this(install,work,step,auction,false);
    }
    PackagedProjectProducer(Path install,Path work,int step,boolean auction,boolean controlled)throws Exception {
        this(install,work,step,auction,controlled,null);
    }
    PackagedProjectProducer(Path install,Path work,int step,boolean auction,boolean controlled,Path caseOverride)throws Exception {
        this(install,work,step,auction,controlled,caseOverride,false);
    }
    PackagedProjectProducer(Path install,Path work,int step,boolean auction,boolean controlled,Path caseOverride,boolean declarative)throws Exception {
        if(declarative&&(!auction||controlled||caseOverride!=null))throw new IllegalArgumentException("GENERIC_CANONICAL_INPUTS_REQUIRED");
        if(controlled&&!auction)throw new IllegalArgumentException("E02_AUCTION_FIXTURE_ONLY");
        if(caseOverride!=null&&!controlled)throw new IllegalArgumentException("E02_EXPLICIT_CONTROLS_REQUIRED");
        this.work=work;runId=auction?"installed-auction":"installed-counter";Path example=caseOverride==null?install.resolve(auction?"examples/auction-semantic":"examples/csl-counter"):caseOverride.toRealPath(),stage=Files.createDirectory(work.resolve("staging"));
        try(var paths=Files.walk(example)){for(Path source:paths.toList()){Path target=stage.resolve(example.relativize(source));if(Files.isDirectory(source))Files.createDirectories(target);else if(!declarative||Set.of("auction.jcm","auction-mapping.xml","auction_constraints_final.ocl","build.gradle").contains(example.relativize(source).toString())||example.relativize(source).toString().replace('\\','/').startsWith("src/"))Files.copy(source,target);}}
        jcm=stage.resolve(auction?"auction.jcm":"counter.jcm");control=Files.createDirectory(work.resolve("startup-control"));secret=work.resolve("secret.hex");stop=work.resolve("stop");ready=work.resolve("ready");release=work.resolve("release");stopMas=work.resolve("stop-mas");
        byte[] random=new byte[32];new java.security.SecureRandom().nextBytes(random);Files.writeString(secret,HexFormat.of().formatHex(random));
        var jars=new ArrayList<String>();jars.add(install.resolve("bridge/lib/jacamo-bridge-contract-1.0.0.jar").toString());jars.add(install.resolve("bridge/lib/jacamo-bridge-jacamo-1.0.0.jar").toString());
        for(String entry:System.getProperty("java.class.path").split(java.io.File.pathSeparator)) {
            Path file=Path.of(entry);String normalized=entry.replace('\\','/').toLowerCase(Locale.ROOT);
            if(Files.isRegularFile(file)&&entry.endsWith(".jar")&&!normalized.matches(".*(jacamo-bridge-(contract|jacamo)|use-(plugin|core|gui|jacamo-semantic-engine)|/org/tzi/use/|junit|surefire|archunit|opentest4j|apiguardian).*"))jars.add(file.toAbsolutePath().toString());
        }
        Path classes=Files.createDirectory(stage.resolve("artifact-classes"));String dependencies=String.join(java.io.File.pathSeparator,jars);
        Path fixture=controlled?Files.createDirectory(work.resolve("fixture-control")):null;
        if(controlled)for(String file:List.of("AuctionControlProducerMain.java","control.asl"))Files.copy(Path.of("src/test/resources/auction-controls").resolve(file),fixture.resolve(file));
        try(var files=ToolProvider.getSystemJavaCompiler().getStandardFileManager(null,null,null);var sources=Files.walk(stage.resolve("src/env"))) {
            var compile=new ArrayList<>(sources.filter(p->p.toString().endsWith(".java")).toList());if(controlled)compile.add(fixture.resolve("AuctionControlProducerMain.java"));
            if(!ToolProvider.getSystemJavaCompiler().getTask(null,files,null,List.of("-proc:none","-encoding","UTF-8","-classpath",dependencies,"-d",classes.toString()),null,
                    files.getJavaFileObjectsFromPaths(compile)).call())throw new IllegalStateException("PACKAGED_PROJECT_COMPILE_FAILED");
        }
        String cp=classes+java.io.File.pathSeparator+dependencies;
        if(cp.contains("test-classes")||cp.contains("target/classes"))throw new IllegalStateException("DEVELOPMENT_PRODUCER_CLASSPATH");
        Path fingerprintLog=work.resolve("fingerprint.log");Process fingerprint=new ProcessBuilder(java(),"-cp",cp,"org.jacamo.bridge.adapter.RuntimeDistributionFingerprintMain").redirectErrorStream(true).redirectOutput(fingerprintLog.toFile()).start();
        if(!fingerprint.waitFor(20,TimeUnit.SECONDS)){fingerprint.destroyForcibly();throw new IllegalStateException("FINGERPRINT_TIMEOUT");}
        if(fingerprint.exitValue()!=0)throw new IllegalStateException(Files.readString(fingerprintLog));
        distribution=Files.readAllLines(fingerprintLog).stream().filter(l->l.startsWith("BRIDGE_RUNTIME_DISTRIBUTION_SHA256=")).findFirst().orElseThrow().split("=",2)[1];
        int port;try(var socket=new java.net.ServerSocket(0)){port=socket.getLocalPort();}endpoint="tcp://127.0.0.1:"+port;
        String text=Files.readString(jcm);int end=text.lastIndexOf('}');String platform=(auction?"    ":"\n ")+"platform: org.jacamo.bridge.adapter.JaCaMoBridgePlatform(\"port="+port+"\", \"secretFile="+secret.toString().replace('\\','/')+"\", \"jcmFile="+jcm.toString().replace('\\','/')+"\", \"distributionSha256="+distribution+"\")\n";
        Files.writeString(jcm,text.substring(0,end)+platform+text.substring(end));
        var arguments=new ArrayList<String>(List.of(java(),"-Djacamo.launch.captureConsole=true","-Djacamo.bridge.structured-observation=false","-Dcounter.demo.step="+step,"-Dcounter.demo.ready="+ready,"-Dcounter.demo.release="+release,"-Dcounter.demo.stopMas="+stopMas,
                "-cp",cp,"jason.infra.local.LiveJaCaMoLauncherMain",jcm.toString(),stop.toString(),auction?"900":"240","true","","240",control.toString(),runId));
        if(declarative)arguments.add(1,"-D"+org.jacamo.bridge.contract.ObservationRequestPlan.PROPERTY+"="+work.resolve("observation-requests.json"));
        if(auction&&!declarative)arguments.addAll(1,List.of("-Djacamo.bridge.auction.step-markers=true","-Djacamo.bridge.auction.clock=true","-Djacamo.bridge.auction.scheme-observer=true"));
        if(controlled) {
            arguments.set(arguments.indexOf("jason.infra.local.LiveJaCaMoLauncherMain"),"auctionfixture.AuctionControlProducerMain");arguments.add(1,"-Dauction.fixture.directory="+fixture);
            boolean late=caseOverride!=null&&Boolean.TRUE.equals(CanonicalJson.object(CanonicalJson.decode(Files.readAllBytes(work.resolve("control-profile.json")))).get("lateRole"));
            if(late)arguments.add(1,"-Dauction.fixture.lateRole=true");
            var hashedFiles=new ArrayList<>(List.of(fixture.resolve("control.asl"),fixture.resolve("AuctionControlProducerMain.java")));try(var generated=Files.walk(classes.resolve("auctionfixture"))){hashedFiles.addAll(generated.filter(Files::isRegularFile).toList());}
            var hashes=new TreeMap<String,String>();for(Path file:hashedFiles)
                hashes.put(file.getFileName().toString(),HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file))));
            Files.write(fixture.resolve("manifest.json"),CanonicalJson.encode(Map.of("profile","AUCTION_DETERMINISTIC_CONTROLS/1.0","sourceHashes",hashes,"inputCaseManifestSha256",HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(example.resolve("case-manifest.json")))),"lateRole",late,"expectedDifference","per-agent official seeds, explicit initial mission commitments and separately sourced supplemental control plans; canonical min=1 alone does not guarantee all players commit both schemes; no canonical file or task-body edits","authority","all semantic outcomes must be observed on existing TCP bridge, never inferred from command files")));
        }
        command=List.copyOf(arguments);
        process=new ProcessBuilder(command).directory(stage.toFile()).redirectErrorStream(true).redirectOutput(work.resolve("producer.log").toFile()).start();
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(60);while(process.isAlive()&&!Files.exists(control.resolve("waiting.json"))&&System.nanoTime()<deadline)Thread.sleep(25);
        if(!Files.exists(control.resolve("waiting.json"))){close();throw new IllegalStateException("PACKAGED_PRODUCER_BOOT_FAILED "+Files.readString(work.resolve("producer.log")));}
        if(controlled) {
            while(process.isAlive()&&!Files.exists(fixture.resolve("ready.json"))&&!Files.exists(fixture.resolve("failure.txt"))&&System.nanoTime()<deadline)Thread.sleep(25);
            if(!Files.exists(fixture.resolve("ready.json"))){close();throw new IllegalStateException("E02_FIXTURE_BOOT_FAILED "+Files.readString(work.resolve("producer.log")));}
        }
    }
    static String java(){return Path.of(System.getProperty("java.home"),"bin",System.getProperty("os.name").startsWith("Windows")?"java.exe":"java").toString();}
    Properties consumerProperties(Path install,int step) {
        var p=new Properties();p.setProperty("use.jacamo.authority","bridge");p.setProperty("use.jacamo.bridge.endpoint",endpoint);p.setProperty("use.jacamo.bridge.secret-file",secret.toString());p.setProperty("use.jacamo.bridge.distribution-sha256",distribution);
        p.setProperty("use.jacamo.startup.directory",control.toString());p.setProperty("use.jacamo.startup.run-id",runId);p.setProperty("jcm",jcm.toString());p.setProperty("mapping",install.resolve("examples/csl-counter/mapping.json").toString());p.setProperty("evidence",work.toString());
        p.setProperty("ready",ready.toString());p.setProperty("release",release.toString());p.setProperty("stopMas",stopMas.toString());p.setProperty("producerStop",stop.toString());p.setProperty("producerPid",Long.toString(process.pid()));p.setProperty("step",Integer.toString(step));
        if(command.stream().anyMatch(a->a.startsWith("-D"+org.jacamo.bridge.contract.ObservationRequestPlan.PROPERTY+"="))) {
            p.setProperty(org.jacamo.bridge.contract.ObservationRequestPlan.PROPERTY,work.resolve("observation-requests.json").toString());
            p.setProperty("profile","JACAMO_DECLARATIVE_XML");p.setProperty("mapping",jcm.resolveSibling("auction-mapping.xml").toString());p.setProperty("ocl",jcm.resolveSibling("auction_constraints_final.ocl").toString());
        }
        return p;
    }
    @Override public void close()throws Exception {
        Files.writeString(stop,"stop owned producer");
        if(process!=null&&!process.waitFor(10,TimeUnit.SECONDS)){process.destroyForcibly();process.waitFor(5,TimeUnit.SECONDS);}
        Files.deleteIfExists(secret);
    }
}
