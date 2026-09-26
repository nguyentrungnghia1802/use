package org.jacamo.bridge.adapter;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.jacamo.bridge.contract.CanonicalJson;

/** Local-only read-only Bridge server with authenticated bounded length-framed JSON. */
public final class LocalTcpBridgeServer implements AutoCloseable {
    private record Outbound(String resumeToken,byte[] frame){Outbound{frame=frame.clone();}}
    private final InetAddress address;private final int requestedPort;private final byte[] secret;private final int maxFrameBytes;
    private final int queueCount;private final long queueBytes;private final Supplier<byte[]> handshake,model,runtime,gap;
    private final AtomicBoolean running=new AtomicBoolean();private final Set<String> nonces=ConcurrentHashMap.newKeySet();
    private final CopyOnWriteArrayList<Subscriber> subscribers=new CopyOnWriteArrayList<>();private final ArrayDeque<Outbound> replay=new ArrayDeque<>();private long replayBytes;
    private final Set<Socket> clients=ConcurrentHashMap.newKeySet();
    private ServerSocket server;private Thread acceptThread;private ThreadPoolExecutor workers;

    public LocalTcpBridgeServer(InetAddress address,int port,byte[] secret,int maxFrameBytes,int queueCount,long queueBytes,
                                Supplier<byte[]> handshake,Supplier<byte[]> model,Supplier<byte[]> runtime,Supplier<byte[]> gap){
        this.address=java.util.Objects.requireNonNull(address);if(!address.isLoopbackAddress())throw new IllegalArgumentException("BRIDGE_REMOTE_MODE_UNSUPPORTED");
        if(port<0||port>65535)throw new IllegalArgumentException("port");if(secret==null||secret.length<32)throw new IllegalArgumentException("BRIDGE_SECRET_TOO_SHORT");
        if(maxFrameBytes<1024||queueCount<1||queueBytes<maxFrameBytes)throw new IllegalArgumentException("transport limits");
        this.requestedPort=port;this.secret=secret.clone();this.maxFrameBytes=maxFrameBytes;this.queueCount=queueCount;this.queueBytes=queueBytes;
        this.handshake=handshake;this.model=model;this.runtime=runtime;this.gap=gap;
    }
    public synchronized void start()throws Exception{if(!running.compareAndSet(false,true))throw new IllegalStateException("BRIDGE_SERVER_ALREADY_RUNNING");
        server=new ServerSocket();server.bind(new InetSocketAddress(address,requestedPort));
        workers=new ThreadPoolExecutor(2,4,30,TimeUnit.SECONDS,new ArrayBlockingQueue<>(64),r->Thread.ofPlatform().daemon().name("jacamo-bridge-tcp-worker").unstarted(r),new ThreadPoolExecutor.AbortPolicy());
        acceptThread=Thread.ofPlatform().daemon().name("jacamo-bridge-tcp-accept").start(this::acceptLoop);
    }
    public synchronized int port(){if(server==null)throw new IllegalStateException("BRIDGE_SERVER_NOT_STARTED");return server.getLocalPort();}
    public void publish(String resumeToken,byte[] frame){if(resumeToken==null||resumeToken.isBlank())throw new IllegalArgumentException("resumeToken");checkFrame(frame);
        Outbound item=new Outbound(resumeToken,frame);synchronized(replay){while(!replay.isEmpty()&&(replay.size()>=queueCount||replayBytes+frame.length>queueBytes)){replayBytes-=replay.removeFirst().frame().length;}replay.addLast(item);replayBytes+=frame.length;}
        for(Subscriber subscriber:subscribers)subscriber.offer(item);
    }
    private void acceptLoop(){while(running.get())try{Socket socket=server.accept();socket.setSoTimeout(10000);clients.add(socket);try{workers.execute(()->handle(socket));}catch(RuntimeException rejected){clients.remove(socket);socket.close();throw rejected;}}catch(Exception e){if(running.get()&&workers!=null&&workers.isShutdown())break;}}
    private void handle(Socket socket){try(socket){Map<String,Object> request=readRequest(socket);String operation=(String)request.get("operation");String resume=(String)request.get("resumeToken");switch(operation){
            case "HANDSHAKE"->write(socket,handshake.get());case "MODEL_SNAPSHOT"->write(socket,model.get());case "RUNTIME_SNAPSHOT"->write(socket,runtime.get());case "ACK"->write(socket,new byte[]{'O','K'});case "SUBSCRIBE"->subscribe(socket,resume);default->throw new IllegalArgumentException("BRIDGE_OPERATION_REJECTED");}}
        catch(Exception ignored){/* Authentication/protocol details are intentionally not reflected to peers. */}finally{clients.remove(socket);}}
    private void subscribe(Socket socket,String resume)throws Exception{var subscriber=new Subscriber(socket);subscribers.add(subscriber);try{
        if(!resume.isBlank()){List<Outbound> retained; synchronized(replay){retained=new ArrayList<>(replay);}int index=-1;for(int i=0;i<retained.size();i++)if(retained.get(i).resumeToken().equals(resume)){index=i;break;}if(index<0)subscriber.offer(new Outbound("GAP",gap.get()));else for(int i=index+1;i<retained.size();i++)subscriber.offer(retained.get(i));}
        subscriber.drain();
    }finally{subscribers.remove(subscriber);}}
    private Map<String,Object> readRequest(Socket socket)throws Exception{byte[] bytes=readFrame(new DataInputStream(socket.getInputStream()));Map<String,Object> map=CanonicalJson.object(CanonicalJson.decode(bytes,maxFrameBytes,32,8192));
        if(!map.keySet().equals(Set.of("operation","resumeToken","nonce","mac")))throw new IllegalArgumentException("BRIDGE_REQUEST_SCHEMA");
        String operation=text(map,"operation"),resume=text(map,"resumeToken"),nonce=text(map,"nonce"),mac=text(map,"mac");
        if(nonce.length()>128||resume.length()>1024||!nonces.add(nonce))throw new IllegalArgumentException("BRIDGE_NONCE_REJECTED");
        if(nonces.size()>16384)nonces.clear();String expected=hmac(operation+"\n"+resume+"\n"+nonce);
        if(!MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),mac.getBytes(StandardCharsets.US_ASCII)))throw new IllegalArgumentException("BRIDGE_AUTH_REJECTED");return map;}
    private String text(Map<String,Object> map,String key){Object value=map.get(key);if(!(value instanceof String text))throw new IllegalArgumentException("BRIDGE_REQUEST_FIELD:"+key);return text;}
    private byte[] readFrame(DataInputStream input)throws Exception{int length=input.readInt();if(length<1||length>maxFrameBytes)throw new IllegalArgumentException("BRIDGE_FRAME_SIZE_REJECTED");byte[] bytes=input.readNBytes(length);if(bytes.length!=length)throw new IllegalArgumentException("BRIDGE_FRAME_TRUNCATED");return bytes;}
    private void write(Socket socket,byte[] frame)throws Exception{checkFrame(frame);var out=new DataOutputStream(socket.getOutputStream());out.writeInt(frame.length);out.write(frame);out.flush();}
    private void checkFrame(byte[] frame){if(frame==null||frame.length<1||frame.length>maxFrameBytes)throw new IllegalArgumentException("BRIDGE_FRAME_SIZE_REJECTED");}
    private String hmac(String value)throws Exception{Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(secret,"HmacSHA256"));return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));}
    @Override public synchronized void close(){if(!running.compareAndSet(true,false))return;for(Subscriber s:subscribers)s.close();subscribers.clear();for(Socket socket:clients)try{socket.close();}catch(Exception ignored){}clients.clear();try{server.close();}catch(Exception ignored){}if(workers!=null){workers.shutdownNow();try{workers.awaitTermination(3,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}}if(acceptThread!=null)try{acceptThread.join(1000);}catch(InterruptedException e){Thread.currentThread().interrupt();}java.util.Arrays.fill(secret,(byte)0);}
    private final class Subscriber{private final Socket socket;private final ArrayBlockingQueue<Outbound> queue=new ArrayBlockingQueue<>(queueCount);private final AtomicLong bytes=new AtomicLong();private volatile boolean overflow;
        Subscriber(Socket socket){this.socket=socket;}void offer(Outbound item){long next=bytes.addAndGet(item.frame().length);if(next>queueBytes||!queue.offer(item)){bytes.addAndGet(-item.frame().length);overflow=true;queue.clear();bytes.set(0);Outbound marker=new Outbound("GAP",gap.get());if(marker.frame().length<=queueBytes){queue.offer(marker);bytes.set(marker.frame().length);}}}
        void drain()throws Exception{while(running.get()&&!socket.isClosed()){Outbound item=queue.poll(1,TimeUnit.SECONDS);if(item!=null){bytes.addAndGet(-item.frame().length);write(socket,item.frame());if(item.resumeToken().equals("GAP"))return;}}}
        void close(){try{socket.close();}catch(Exception ignored){}}
    }
}
