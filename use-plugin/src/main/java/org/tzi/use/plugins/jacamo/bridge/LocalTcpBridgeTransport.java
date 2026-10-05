package org.tzi.use.plugins.jacamo.bridge;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.jacamo.bridge.contract.CanonicalJson;

/** Production local-only authenticated, length-framed JSON transport. */
public final class LocalTcpBridgeTransport implements BridgeTransport {
    private static final byte[] SUBSCRIPTION_READY = "BRIDGE_SUBSCRIPTION_READY".getBytes(StandardCharsets.US_ASCII);
    private static final int ACK_BATCH_SIZE = 128;
    private final InetAddress address; private final int port; private final byte[] secret;
    private final int maxFrameBytes; private final int timeoutMillis; private final AtomicBoolean closed=new AtomicBoolean();
    private final Object ackLock=new Object();private final java.util.LinkedHashMap<String,String> pendingAcks=new java.util.LinkedHashMap<>();private int pendingAckEvents;
    private volatile Socket subscriptionSocket; private volatile Thread subscriptionThread;

    public LocalTcpBridgeTransport(InetAddress address,int port,byte[] secret,int maxFrameBytes,int timeoutMillis){
        this.address=Objects.requireNonNull(address);if(!address.isLoopbackAddress())throw new IllegalArgumentException("BRIDGE_REMOTE_MODE_UNSUPPORTED");
        if(port<1||port>65535)throw new IllegalArgumentException("port");if(secret==null||secret.length<32)throw new IllegalArgumentException("BRIDGE_SECRET_TOO_SHORT");
        if(maxFrameBytes<1024||timeoutMillis<100)throw new IllegalArgumentException("transport limits");
        this.port=port;this.secret=secret.clone();this.maxFrameBytes=maxFrameBytes;this.timeoutMillis=timeoutMillis;
    }
    @Override public byte[] handshake(){return request("HANDSHAKE","");}
    @Override public byte[] modelSnapshot(){return request("MODEL_SNAPSHOT","");}
    @Override public byte[] runtimeSnapshot(){return request("RUNTIME_SNAPSHOT","");}
    @Override public byte[] control(org.jacamo.bridge.contract.RuntimeControlContract.Request request) {
        ensureOpen();try(Socket socket=connect()) {
            String nonce=UUID.randomUUID().toString();var body=request.payload();
            String content="CONTROL\n\n"+nonce+"\n"+new String(CanonicalJson.encode(body),StandardCharsets.UTF_8);
            byte[] payload=CanonicalJson.encode(Map.of("operation","CONTROL","resumeToken","","nonce",nonce,"mac",hmac(content),"control",body));
            if(payload.length>maxFrameBytes)throw new BridgeProtocolException("BRIDGE_REQUEST_TOO_LARGE");
            var out=new DataOutputStream(socket.getOutputStream());out.writeInt(payload.length);out.write(payload);out.flush();
            return readFrame(new DataInputStream(socket.getInputStream()));
        } catch(Exception failed) {throw new BridgeProtocolException("BRIDGE_CONTROL_REQUEST_FAILED",failed);}
    }
    @Override public Subscription subscribe(String resumeToken,Consumer<byte[]> next){return subscribe(resumeToken,next,error->{});}
    @Override public synchronized Subscription subscribe(String resumeToken,Consumer<byte[]> next,Consumer<RuntimeException> failure){
        ensureOpen();if(subscriptionSocket!=null)throw new IllegalStateException("BRIDGE_ALREADY_SUBSCRIBED");Objects.requireNonNull(next);
        Objects.requireNonNull(failure);
        try{
            Socket socket=connect();subscriptionSocket=socket;writeRequest(socket,"SUBSCRIBE",resumeToken==null?"":resumeToken);
            byte[] ready=readFrame(new DataInputStream(socket.getInputStream()));
            if(!MessageDigest.isEqual(ready,SUBSCRIPTION_READY))throw new BridgeProtocolException("BRIDGE_SUBSCRIBE_BARRIER_REJECTED");
            // Request/response calls are bounded by timeoutMillis, but an authenticated event stream may be
            // legitimately idle for longer than that.  Closing the transport still closes this socket and wakes
            // the reader, so the subscription must not interpret an idle interval as a network partition.
            socket.setSoTimeout(0);
            Thread reader=Thread.ofPlatform().daemon().name("jacamo-bridge-tcp-subscription").start(()->readSubscription(socket,next,failure));subscriptionThread=reader;
            return ()->closeSubscription(socket,reader);
        }catch(Exception e){throw new BridgeProtocolException("BRIDGE_SUBSCRIBE_FAILED",e);}
    }
    @Override public void acknowledge(String resumeToken){if(resumeToken==null||resumeToken.isBlank())throw new IllegalArgumentException("BRIDGE_ACK_TOKEN_REQUIRED");java.util.List<String> batch=java.util.List.of();synchronized(ackLock){int separator=resumeToken.lastIndexOf(':');String source=separator>0?resumeToken.substring(0,separator):resumeToken;pendingAcks.put(source,resumeToken);if(++pendingAckEvents>=ACK_BATCH_SIZE)batch=drainAcks();}flushAcks(batch);}
    private java.util.List<String> drainAcks(){if(pendingAcks.isEmpty()){pendingAckEvents=0;return java.util.List.of();}var result=java.util.List.copyOf(pendingAcks.values());pendingAcks.clear();pendingAckEvents=0;return result;}
    private void flushAcks(java.util.List<String> tokens){for(String token:tokens)request("ACK",token);}
    private void flushPendingAcks(){java.util.List<String> batch;synchronized(ackLock){batch=drainAcks();}flushAcks(batch);}
    private byte[] request(String operation,String resumeToken){ensureOpen();try(Socket socket=connect()){writeRequest(socket,operation,resumeToken);return readFrame(new DataInputStream(socket.getInputStream()));}catch(Exception e){throw new BridgeProtocolException("BRIDGE_TCP_REQUEST_FAILED:"+operation,e);}}
    private Socket connect()throws Exception{Socket socket=new Socket();socket.connect(new InetSocketAddress(address,port),timeoutMillis);socket.setSoTimeout(timeoutMillis);return socket;}
    private void writeRequest(Socket socket,String operation,String resumeToken)throws Exception{
        String nonce=UUID.randomUUID().toString();String content=operation+"\n"+resumeToken+"\n"+nonce;
        byte[] payload=CanonicalJson.encode(Map.of("operation",operation,"resumeToken",resumeToken,"nonce",nonce,"mac",hmac(content)));
        if(payload.length>maxFrameBytes)throw new BridgeProtocolException("BRIDGE_REQUEST_TOO_LARGE");var out=new DataOutputStream(socket.getOutputStream());out.writeInt(payload.length);out.write(payload);out.flush();
    }
    private void readSubscription(Socket socket,Consumer<byte[]> next,Consumer<RuntimeException> failure){try{var input=new DataInputStream(socket.getInputStream());while(!closed.get()&&!socket.isClosed())next.accept(readFrame(input));}catch(Exception error){if(!closed.get()&&!socket.isClosed())failure.accept(new BridgeProtocolException("BRIDGE_SUBSCRIPTION_DISCONNECTED",error));}finally{try{socket.close();}catch(Exception ignored){}synchronized(this){if(subscriptionSocket==socket){subscriptionSocket=null;subscriptionThread=null;}}}}
    private byte[] readFrame(DataInputStream input)throws Exception{int length=input.readInt();if(length<1||length>maxFrameBytes)throw new BridgeProtocolException("BRIDGE_FRAME_SIZE_REJECTED:"+length);return input.readNBytes(length);}
    private String hmac(String value)throws Exception{Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(secret,"HmacSHA256"));return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));}
    private void ensureOpen(){if(closed.get())throw new IllegalStateException("BRIDGE_TRANSPORT_CLOSED");}
    private synchronized void closeSubscription(Socket socket,Thread reader){try{socket.close();}catch(Exception ignored){}if(reader!=Thread.currentThread())try{reader.join(Math.min(timeoutMillis,1000));}catch(InterruptedException e){Thread.currentThread().interrupt();}if(subscriptionSocket==socket){subscriptionSocket=null;subscriptionThread=null;}}
    @Override public synchronized void close(){if(closed.get())return;try{flushPendingAcks();}catch(RuntimeException ignored){/* Active batches fail closed; shutdown flush is best effort. */}if(!closed.compareAndSet(false,true))return;Socket socket=subscriptionSocket;Thread reader=subscriptionThread;if(socket!=null)closeSubscription(socket,reader);java.util.Arrays.fill(secret,(byte)0);}
}
