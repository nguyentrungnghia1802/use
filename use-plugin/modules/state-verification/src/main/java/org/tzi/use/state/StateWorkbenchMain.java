package org.tzi.use.state;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.tools.ToolProvider;
import org.jacamo.bridge.contract.CanonicalJson;

/** Runnable native USE/CSL/iStar view for the attribute-only synchronization mode. */
public final class StateWorkbenchMain {
    private final CslStateVerifier verifier;
    private final Path project,work;
    private final boolean headless;
    private final ServerSocket server;
    private final byte[] secret=new byte[32];
    private final Map<String,Map<String,Object>> receipts=new LinkedHashMap<>();
    private final Map<String,String> requestHashes=new HashMap<>();
    private Process producer;
    private JFrame window;
    private static final class ReadOnlyTable extends DefaultTableModel {
        ReadOnlyTable(String... columns){super(columns,0);}
        @Override public boolean isCellEditable(int row,int column){return false;}
    }
    private final DefaultTableModel state=new ReadOnlyTable();
    private final DefaultTableModel elements=new ReadOnlyTable("CSL object","Actor","Goal / Task","Status");
    private final DefaultTableModel checks=new ReadOnlyTable("CSL object","Condition","Kind","OCL","Value","Diagnostic");
    private final DefaultTableModel history=new ReadOnlyTable("Checkpoint","Agent","Plan","Source outcome","Decision");
    private final JLabel status=new JLabel("Preparing JaCaMo...");
    private final JTextArea mappingText=new JTextArea();
    private final CountDownLatch finished=new CountDownLatch(1);
    private StateWorkbenchMain(Path project,Path mapping,Path csl,Path istar,Path work,boolean headless)throws Exception {
        this.project=project.toAbsolutePath();this.work=work.toAbsolutePath();this.headless=headless;Files.createDirectories(work);
        verifier=new CslStateVerifier(mapping,csl,istar);new SecureRandom().nextBytes(secret);
        server=new ServerSocket(0,16,InetAddress.getLoopbackAddress());
        mappingText.setText(Files.readString(mapping));mappingText.setEditable(false);
        Path secretFile=work.resolve("bridge-secret.hex");Files.writeString(secretFile,HexFormat.of().formatHex(secret),StandardCharsets.US_ASCII);
        try{Files.setPosixFilePermissions(secretFile,PosixFilePermissions.fromString("rw-------"));}catch(UnsupportedOperationException ignored){ }
        compileArtifacts();
        if(!headless)SwingUtilities.invokeAndWait(this::openWindow);
        Thread.ofPlatform().daemon().name("csl-state-consumer").start(this::serve);
        var command=new ArrayList<String>();command.add(Path.of(System.getProperty("java.home"),"bin","java").toString());
        command.add("-Djava.awt.headless=true");command.add("-cp");command.add(System.getProperty("java.class.path")+File.pathSeparator+work.resolve("artifact-classes"));
        command.add(StateProducerMain.class.getName());command.add(this.project.toString());command.add(mapping.toAbsolutePath().toString());command.add(Integer.toString(server.getLocalPort()));command.add(secretFile.toString());command.add(this.work.toString());
        producer=new ProcessBuilder(command).directory(this.project.getParent().toFile()).redirectErrorStream(true).redirectOutput(work.resolve("producer.log").toFile()).start();
        producer.onExit().thenRun(()->{publishStatus("JaCaMo exited ("+producer.exitValue()+"). Log: "+work.resolve("producer.log"));if(headless)finished.countDown();});
        System.out.println("STATE_WORKBENCH work="+this.work+" pid="+producer.pid());
        Runtime.getRuntime().addShutdownHook(new Thread(this::stop));
    }
    private void compileArtifacts()throws IOException {
        Path source=project.getParent().resolve("src/env"),output=work.resolve("artifact-classes");Files.createDirectories(output);
        if(!Files.isDirectory(source))return;
        List<String> sources;try(var stream=Files.walk(source)){sources=stream.filter(p->p.toString().endsWith(".java")).map(Path::toString).toList();}
        if(sources.isEmpty())return;
        var compiler=ToolProvider.getSystemJavaCompiler();if(compiler==null)throw new IllegalStateException("JDK_21_REQUIRED");
        var args=new ArrayList<>(List.of("-cp",System.getProperty("java.class.path"),"-d",output.toString()));args.addAll(sources);
        args.add("-proc:none");
        if(compiler.run(null,System.out,System.err,args.toArray(String[]::new))!=0)throw new IllegalArgumentException("PROJECT_ARTIFACT_COMPILE_FAILED");
    }
    private void serve() {
        while(!server.isClosed())try(var socket=server.accept()) {
            socket.setSoTimeout(30000);var request=StateWire.read(socket.getInputStream(),secret);String hash=StateWire.digest(request);
            if(!StateWire.VERSION.equals(request.get("version")))throw new IOException("STATE_PROTOCOL_VERSION");
            String token=(String)request.get("token"),run=(String)request.get("run");if(token==null||token.isBlank()||run==null||run.isBlank())throw new IOException("STATE_TOKEN_REQUIRED");
            String key=run+":"+token;Map<String,Object> response=receipts.get(key);
            if(response!=null) {
                if(!hash.equals(requestHashes.get(key)))throw new IOException("STATE_TOKEN_COLLISION");
            }else {
                if(receipts.size()>=16384)throw new IOException("STATE_CHECKPOINT_RECEIPT_CAPACITY");
                CslStateVerifier.Result result=null;String failure="";
                try{var snapshot=StateWire.snapshot(CanonicalJson.object(request.get("snapshot")));if(!run.equals(snapshot.run()))throw new IllegalArgumentException("STATE_ENVELOPE_RUN_MISMATCH");result=verifier.update(snapshot);}
                catch(Exception error){failure=error.getClass().getSimpleName()+":"+error.getMessage();}
                String decision=result==null?"HOLD_UNVERIFIED":result.decision();
                // Source outcome is audit metadata. Only CSL/iStar state verification decides this gate.
                response=Map.of("version",StateWire.VERSION,"token",token,"run",run,"requestHash",hash,"decision",decision,
                        "diagnostic",failure,"result",result==null?Map.of():result.payload());
                receipts.put(key,response);requestHashes.put(key,hash);
                var report=Map.of("request",request,"response",response);
                Files.writeString(work.resolve("latest.json"),new String(CanonicalJson.encode(report),StandardCharsets.UTF_8));
                Files.writeString(work.resolve("consumer-history.jsonl"),new String(CanonicalJson.encode(report),StandardCharsets.UTF_8)+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);
                var displayed=result;var displayedResponse=response;
                if(!headless)SwingUtilities.invokeAndWait(()->display(request,displayedResponse,displayed));
                System.out.println("CSL_STATE sequence="+CanonicalJson.object(request.get("snapshot")).get("sequence")+" decision="+decision+" diagnostic="+failure);
                if(headless&&Boolean.getBoolean("use.state.exit-on-hold")&&!decision.equals("APPROVE"))finished.countDown();
            }
            StateWire.write(socket.getOutputStream(),response,secret);
        }catch(Exception failure){if(!server.isClosed()){System.err.println("STATE_CONSUMER_HELD "+failure);publishStatus("HOLD_UNVERIFIED: "+failure);}}
    }
    private void openWindow() {
        window=new JFrame("USE — CSL state → iStar verification");window.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        var tabs=new JTabbedPane();tabs.addTab("CSL state",new JScrollPane(new JTable(state)));tabs.addTab("iStar Goal / Task",new JScrollPane(new JTable(elements)));
        tabs.addTab("OCL conditions",new JScrollPane(new JTable(checks)));tabs.addTab("Checkpoints",new JScrollPane(new JTable(history)));tabs.addTab("Attribute mapping",new JScrollPane(mappingText));
        var stop=new JButton("Stop JaCaMo");stop.addActionListener(e->stop());
        var bar=new JPanel(new BorderLayout(12,0));bar.setBorder(BorderFactory.createEmptyBorder(10,10,10,10));bar.add(status,BorderLayout.CENTER);bar.add(stop,BorderLayout.EAST);
        window.add(bar,BorderLayout.NORTH);window.add(tabs);window.setSize(1200,640);window.setLocationByPlatform(true);
        window.addWindowListener(new WindowAdapter(){@Override public void windowClosing(WindowEvent e){stop();window.dispose();}});window.setVisible(true);
    }
    private void display(Map<String,Object> request,Map<String,Object> response,CslStateVerifier.Result result) {
        if(result!=null) {
            var columns=new TreeSet<String>();result.state().forEach(row->columns.addAll(row.keySet()));var ordered=new ArrayList<>(columns);state.setColumnIdentifiers(ordered.toArray());state.setRowCount(0);
            result.state().forEach(row->state.addRow(ordered.stream().map(c->row.getOrDefault(c,"")).toArray()));
            elements.setRowCount(0);result.elements().forEach(e->elements.addRow(new Object[]{e.context(),e.actor(),e.element(),e.status()}));
            checks.setRowCount(0);result.checks().forEach(c->checks.addRow(new Object[]{c.context(),c.id(),c.kind(),c.expression(),c.verdict(),c.diagnostic()}));
        }
        var snapshot=CanonicalJson.object(request.get("snapshot"));history.addRow(new Object[]{snapshot.get("sequence"),request.get("agent"),request.get("plan"),request.get("outcome"),response.get("decision")});
        status.setText(response.get("decision")+" · checkpoint "+snapshot.get("sequence")+" · "+response.get("diagnostic"));
        status.setForeground("APPROVE".equals(response.get("decision"))?new Color(20,110,50):new Color(170,30,30));
    }
    private void publishStatus(String text){if(!headless)SwingUtilities.invokeLater(()->status.setText(text));}
    private void stop(){if(producer!=null&&producer.isAlive()){producer.descendants().forEach(ProcessHandle::destroy);producer.destroy();}try{server.close();}catch(IOException ignored){}verifier.close();finished.countDown();}
    public static void main(String[] args)throws Exception {
        if(args.length<4)throw new IllegalArgumentException("Usage: project.jcm mapping.xml model.csl model.istar [--headless] [--seconds=N]");
        boolean headless=Arrays.asList(args).contains("--headless");
        Path work=Path.of(System.getProperty("use.state.work",Path.of(System.getProperty("java.io.tmpdir"),"use-csl-state-"+System.currentTimeMillis()).toString()));
        var app=new StateWorkbenchMain(Path.of(args[0]),Path.of(args[1]),Path.of(args[2]),Path.of(args[3]),work,headless);
        for(String arg:args)if(arg.startsWith("--seconds=")){int seconds=Integer.parseInt(arg.substring(10));Thread.ofPlatform().daemon().start(()->{try{Thread.sleep(seconds*1000L);app.stop();}catch(InterruptedException e){Thread.currentThread().interrupt();}});}
        app.finished.await();app.stop();
    }
}
