package org.tzi.use.plugins.jacamo.bridge;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class SeparateJvmBridgeTest {
    @Test void modelSnapshotRuntimeSnapshotAndEventCrossTwoIndependentJvms() throws Exception {
        Path recording=Files.createTempFile("jacamo-bridge-phase3-",".frames");
        try{
            String full=exactPlatformClasspath();
            String producer=run(full,"org.jacamo.bridge.adapter.BridgeProducerMain",recording.toString(),Path.of("src/test/resources/canonical-cases/hello-world/helloworld.jcm").toAbsolutePath().toString());
            assertTrue(producer.contains("BRIDGE_PRODUCER_OK"),producer);
            String isolated=Arrays.stream(full.split(Pattern.quote(java.io.File.pathSeparator))).filter(this::allowedUseClasspath).collect(java.util.stream.Collectors.joining(java.io.File.pathSeparator));
            assertFalse(isolated.toLowerCase(Locale.ROOT).contains("jacamo-bridge-jacamo"));
            for(String forbidden:java.util.List.of("jason-interpreter","cartago-","moise-","npl-","jacamo-1.3","jaca-","intmas-","sai-"))assertFalse(isolated.toLowerCase(Locale.ROOT).contains(forbidden),forbidden);
            String first=run(isolated,BridgeConsumerMain.class.getName(),recording.toString(),Path.of("src/test/resources/canonical-cases/hello-world").toAbsolutePath().toString());
            String second=run(isolated,BridgeConsumerMain.class.getName(),recording.toString(),Path.of("src/test/resources/canonical-cases/hello-world").toAbsolutePath().toString());
            assertTrue(first.contains("BRIDGE_CONSUMER_OK"),first);assertEquals(lastLine(first),lastLine(second));
        }finally{Files.deleteIfExists(recording);}
    }

    @Test void selectedProductionTcpTransportCrossesSeparateJvmsForAllCanonicalCases() throws Exception {
        String full=exactPlatformClasspath();
        String isolated=Arrays.stream(full.split(Pattern.quote(java.io.File.pathSeparator))).filter(this::allowedUseClasspath).collect(java.util.stream.Collectors.joining(java.io.File.pathSeparator));
        Path examples=Path.of("..","..","JaCaMo","examples").toAbsolutePath().normalize();
        for(Path jcm:java.util.List.of(Path.of("src/test/resources/canonical-cases/hello-world/helloworld.jcm").toAbsolutePath().normalize(),examples.resolve("auction/auction.jcm"),examples.resolve("house-building/house-building.jcm"))){
            int port;try(var reservation=new java.net.ServerSocket(0,1,java.net.InetAddress.getLoopbackAddress())){port=reservation.getLocalPort();}
            Path stop=Files.createTempFile("bridge-tcp-stop-",".flag");Files.deleteIfExists(stop);String secret="0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
            Process server=start(full,"org.jacamo.bridge.adapter.TcpBridgeServerMain",jcm.toString(),Integer.toString(port),secret,stop.toString());
            try{
                waitForPort(port,server);
                String output=run(isolated,TcpBridgeConsumerMain.class.getName(),Integer.toString(port),secret,jcm.getParent().toString(),jcm.getParent().getFileName().toString());
                assertTrue(output.contains("BRIDGE_TCP_CONSUMER_OK"),output);
            }finally{
                Files.writeString(stop,"stop");if(!server.waitFor(10,TimeUnit.SECONDS))server.destroyForcibly();String serverOutput=new String(server.getInputStream().readAllBytes(),StandardCharsets.UTF_8);assertEquals(0,server.exitValue(),serverOutput);assertTrue(serverOutput.contains("BRIDGE_TCP_SERVER_READY"),serverOutput);Files.deleteIfExists(stop);
            }
        }
    }
    private boolean allowedUseClasspath(String value){String name=Path.of(value).getFileName().toString().toLowerCase(Locale.ROOT);String full=value.toLowerCase(Locale.ROOT);if(full.contains("jacamo-bridge-jacamo"))return false;return !(name.startsWith("jason-interpreter-")||name.startsWith("cartago-")||name.startsWith("moise-")||name.startsWith("npl-")||name.matches("jacamo-1\\.3(?:\\..*)?\\.jar")||name.startsWith("jaca-")||name.startsWith("intmas-")||name.startsWith("sai-"));}
    private String exactPlatformClasspath(){String separator=java.io.File.pathSeparator;String base=Arrays.stream(System.getProperty("java.class.path").split(Pattern.quote(separator))).filter(value->!Path.of(value).getFileName().toString().toLowerCase(Locale.ROOT).startsWith("jason-interpreter-")).collect(java.util.stream.Collectors.joining(separator));Path jason=Path.of(System.getProperty("user.home"),".m2","repository","io","github","jason-lang","jason-interpreter","3.3.2","jason-interpreter-3.3.2.jar");assertTrue(Files.isRegularFile(jason),jason.toString());return base+separator+jason+separator+Path.of("..","jacamo-bridge-jacamo","target","test-classes").toAbsolutePath();}
    private String run(String cp,String main,String...args)throws Exception{var command=new java.util.ArrayList<String>();command.add(Path.of(System.getProperty("java.home"),"bin","java.exe").toString());command.add("-cp");command.add(cp);command.add(main);command.addAll(java.util.List.of(args));Process process=new ProcessBuilder(command).redirectErrorStream(true).start();String output=new String(process.getInputStream().readAllBytes(),StandardCharsets.UTF_8);assertEquals(0,process.waitFor(),output);return output;}
    private Process start(String cp,String main,String...args)throws Exception{var command=new java.util.ArrayList<String>();command.add(Path.of(System.getProperty("java.home"),"bin","java.exe").toString());command.add("-cp");command.add(cp);command.add(main);command.addAll(java.util.List.of(args));return new ProcessBuilder(command).redirectErrorStream(true).start();}
    private void waitForPort(int port,Process process)throws Exception{long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(15);while(System.nanoTime()<deadline){if(!process.isAlive())return;try(var socket=new java.net.Socket()){socket.connect(new java.net.InetSocketAddress(java.net.InetAddress.getLoopbackAddress(),port),100);return;}catch(Exception ignored){Thread.sleep(25);}}fail("TCP bridge server did not open port "+port);}
    private String lastLine(String output){return output.strip().lines().reduce((a,b)->b).orElse("");}
}
