package org.tzi.use.plugins.jacamo.bridge;

import java.util.ArrayDeque;
import java.util.Set;
import org.jacamo.bridge.contract.ContractCodec;
import org.jacamo.bridge.contract.ContractEnvelope;
import org.jacamo.bridge.contract.ContractPayloads;
import org.jacamo.bridge.contract.ContractValidator;
import org.jacamo.bridge.contract.MessageType;
import org.jacamo.bridge.contract.ModelSnapshot;
import org.jacamo.bridge.contract.RuntimeEvent;
import org.jacamo.bridge.contract.RuntimeSnapshot;

/** Fail-closed client: validates envelope and identity before typed payload decoding. */
public final class BridgeClient implements AutoCloseable {
    public record CoverageFailure(RuntimeEvent event, String diagnostic) { }
    public record Accepted(ModelSnapshot model, RuntimeSnapshot runtime, String distributionDigest,
                           String projectKey, java.util.List<org.jacamo.bridge.contract.Capability> capabilities,
                           org.jacamo.bridge.contract.Completeness completeness,
                           String sessionId, long generation, String modelRevision,java.util.Map<String,String> sourceVersions) {
        public Accepted { capabilities=java.util.List.copyOf(capabilities);sourceVersions=java.util.Map.copyOf(sourceVersions); }
    }
    private final BridgeTransport transport; private final BridgeMirrorStateMachine mirror;
    private final String requiredDistribution; private final Set<String> requiredCapabilities;
    private final java.util.function.Consumer<RuntimeEvent> acceptedEvent;
    private final java.util.function.BiConsumer<RuntimeEvent, String> coverageFailure;
    private final Object deliveryLock = new Object();
    private final int maxBufferedEvents; private final ArrayDeque<byte[]> buffered=new ArrayDeque<>();
    private BridgeTransport.Subscription subscription;
    private boolean bootstrapping;
    private java.util.Map<String,org.jacamo.bridge.contract.SourceWatermark> snapshotWatermarks=java.util.Map.of();
    private final java.util.concurrent.atomic.AtomicLong snapshotCoveredEvents=new java.util.concurrent.atomic.AtomicLong();
    private String asynchronousFailure;
    public org.jacamo.bridge.contract.RuntimeControlContract.Status control(org.jacamo.bridge.contract.RuntimeControlContract.Request request) {
        var response=decode(transport.control(request),MessageType.CONTROL_STATUS);requireSame(response);
        var status=org.jacamo.bridge.contract.RuntimeControlContract.Status.decode(response.payload());
        if(!status.sessionId().equals(request.sessionId()) || status.generation()!=request.generation() || !status.modelRevision().equals(request.modelRevision()))
            throw new BridgeProtocolException("BRIDGE_CONTROL_RESPONSE_IDENTITY_STALE");
        return status;
    }
    public BridgeClient(BridgeTransport transport,BridgeMirrorStateMachine mirror,String requiredDistribution,
                        Set<String> requiredCapabilities,int maxBufferedEvents){
        this(transport,mirror,requiredDistribution,requiredCapabilities,maxBufferedEvents,event->{});
    }
    public BridgeClient(BridgeTransport transport,BridgeMirrorStateMachine mirror,String requiredDistribution,
                        Set<String> requiredCapabilities,int maxBufferedEvents,java.util.function.Consumer<RuntimeEvent> acceptedEvent){
        this(transport,mirror,requiredDistribution,requiredCapabilities,maxBufferedEvents,acceptedEvent,diagnostic->{});
    }
    public BridgeClient(BridgeTransport transport,BridgeMirrorStateMachine mirror,String requiredDistribution,
                        Set<String> requiredCapabilities,int maxBufferedEvents,java.util.function.Consumer<RuntimeEvent> acceptedEvent,
                        java.util.function.Consumer<String> coverageFailure){
        this(transport,mirror,requiredDistribution,requiredCapabilities,maxBufferedEvents,acceptedEvent,
                (event,diagnostic)->coverageFailure.accept(diagnostic));
        java.util.Objects.requireNonNull(coverageFailure);
    }
    public BridgeClient(BridgeTransport transport,BridgeMirrorStateMachine mirror,String requiredDistribution,
                        Set<String> requiredCapabilities,int maxBufferedEvents,java.util.function.Consumer<RuntimeEvent> acceptedEvent,
                        java.util.function.BiConsumer<RuntimeEvent,String> coverageFailure){
        this.transport=java.util.Objects.requireNonNull(transport);this.mirror=java.util.Objects.requireNonNull(mirror);
        this.requiredDistribution=java.util.Objects.requireNonNull(requiredDistribution);this.requiredCapabilities=Set.copyOf(requiredCapabilities);
        this.acceptedEvent=java.util.Objects.requireNonNull(acceptedEvent);
        this.coverageFailure=java.util.Objects.requireNonNull(coverageFailure);
        if(maxBufferedEvents<1)throw new IllegalArgumentException("maxBufferedEvents");this.maxBufferedEvents=maxBufferedEvents;
    }
    public synchronized Accepted synchronize(){
        mirror.transition(BridgeClientState.NEGOTIATING); ContractEnvelope handshake=decode(transport.handshake(),MessageType.HANDSHAKE);
        if(!requiredDistribution.equals(handshake.distribution().distributionDigest()))throw fail("BRIDGE_DISTRIBUTION_UNSUPPORTED");
        var capabilities=handshake.capabilities().stream().filter(c->c.status()==org.jacamo.bridge.contract.CapabilityStatus.COMPLETE).map(c->c.name()).collect(java.util.stream.Collectors.toSet());
        if(!capabilities.containsAll(requiredCapabilities))throw fail("BRIDGE_CAPABILITY_REQUIRED:"+requiredCapabilities);
        mirror.acceptIdentity(handshake.sessionId(),handshake.generation(),handshake.modelRevision());
        mirror.transition(BridgeClientState.MODEL_SYNC); ContractEnvelope modelEnvelope=decode(transport.modelSnapshot(),MessageType.MODEL_SNAPSHOT); requireSame(modelEnvelope);
        ModelSnapshot model=ContractPayloads.model(modelEnvelope.payload()); if(!model.modelRevision().equals(modelEnvelope.modelRevision()))throw fail("BRIDGE_MODEL_PAYLOAD_REVISION");
        synchronized(buffered){
            buffered.clear(); asynchronousFailure=null; bootstrapping=true;
            subscription=transport.subscribe("",this::acceptSubscriptionEvent,this::acceptSubscriptionFailure);
        }
        mirror.transition(BridgeClientState.SNAPSHOT_SYNC); ContractEnvelope runtimeEnvelope=decode(transport.runtimeSnapshot(),MessageType.RUNTIME_SNAPSHOT); requireSame(runtimeEnvelope);
        RuntimeSnapshot runtime=ContractPayloads.runtime(runtimeEnvelope.payload()); mirror.replace(runtime);
        synchronized(buffered){
            if(asynchronousFailure!=null)throw fail(asynchronousFailure);
            snapshotWatermarks=runtime.endWatermarks();
            while(!buffered.isEmpty())applyBufferedEvent(buffered.removeFirst(),runtime);
            bootstrapping=false;
        }
        var versions=new java.util.TreeMap<String,String>(handshake.distribution().components());
        versions.put("jacamo",handshake.distribution().jacamoVersion()); versions.put("contract",handshake.schemaVersion());
        return new Accepted(model,runtime,handshake.distribution().distributionDigest(),handshake.projectKey(),
                handshake.capabilities(),handshake.completeness(),handshake.sessionId(),handshake.generation(),
                handshake.modelRevision(),versions);
    }
    private void acceptSubscriptionEvent(byte[] bytes){
        synchronized(buffered){
            if(bootstrapping){
                if(buffered.size()>=maxBufferedEvents){
                    asynchronousFailure="BRIDGE_BUFFER_OVERFLOW";
                    mirror.transition(BridgeClientState.RESYNC_REQUIRED);
                    coverageFailure.accept(null,asynchronousFailure);
                    return;
                }
                buffered.add(bytes);
                return;
            }
        }
        try{synchronized(deliveryLock){applyEvent(bytes);}}
        catch(RuntimeException error){synchronized(buffered){if(asynchronousFailure==null)asynchronousFailure=diagnostic(error);}throw error;}
    }
    private void acceptSubscriptionFailure(RuntimeException error){
        synchronized(buffered){if(asynchronousFailure==null)asynchronousFailure=diagnostic(error);}
        mirror.transition(BridgeClientState.RESYNC_REQUIRED);
        coverageFailure.accept(null,diagnostic(error));
    }
    public String diagnostic(){synchronized(buffered){return asynchronousFailure==null?"":asynchronousFailure;}}
    public boolean receive(byte[] bytes){synchronized(deliveryLock){return applyEvent(bytes);}}
    private boolean applyBufferedEvent(byte[] bytes,RuntimeSnapshot snapshot){return applyEvent(decodeEvent(bytes));}
    private boolean applyEvent(byte[] bytes){return applyEvent(decodeEvent(bytes));}
    private RuntimeEvent decodeEvent(byte[] bytes){ContractEnvelope envelope=decode(bytes,MessageType.RUNTIME_EVENT);requireSame(envelope);return ContractPayloads.event(envelope.payload());}
    private boolean applyEvent(RuntimeEvent event){
        // Publication queues can deliver pre-cut callbacks after bootstrap ends.
        // The authoritative watermark, rather than arrival time, proves coverage.
        var covered=snapshotWatermarks.get(event.sourceId());
        if(covered!=null && event.sourceSequence()<=covered.sequence()
                && event.kind()!=org.jacamo.bridge.contract.RuntimeEventKind.GAP
                && event.kind()!=org.jacamo.bridge.contract.RuntimeEventKind.MODEL_REVISION_CHANGED) {
            snapshotCoveredEvents.incrementAndGet();
            transport.acknowledge(event.sourceId()+":"+event.sourceSequence());return false;
        }
        boolean result;
        try { result=mirror.apply(event); }
        catch(RuntimeException error){throw fail("BRIDGE_EVENT_ORDER_REJECTED",error,event);}
        if(!result&&mirror.state()==BridgeClientState.RESYNC_REQUIRED){String message="BRIDGE_EVENT_"+event.kind()+":source="+event.sourceId()+":sequence="+event.sourceSequence()+":"+event.after().getOrDefault("diagnostic", "");synchronized(buffered){if(asynchronousFailure==null)asynchronousFailure=message;}coverageFailure.accept(event,message);}
        if(result)try{acceptedEvent.accept(event);}catch(RuntimeException error){throw fail("BRIDGE_EVENT_PROJECTION_REJECTED",error,event);}
        transport.acknowledge(event.sourceId()+":"+event.sourceSequence());return result;
    }
    public long snapshotCoveredEvents(){return snapshotCoveredEvents.get();}
    private ContractEnvelope decode(byte[] bytes,MessageType expected){try{ContractEnvelope value=ContractCodec.decode(bytes,4*1024*1024,64,256*1024);new ContractValidator().validate(value);if(value.messageType()!=expected)throw fail("BRIDGE_MESSAGE_TYPE:"+value.messageType());return value;}catch(BridgeProtocolException e){throw e;}catch(RuntimeException e){throw fail("BRIDGE_ENVELOPE_REJECTED",e);}}
    private void requireSame(ContractEnvelope value){if(!value.sessionId().equals(mirror.sessionId()))throw fail("BRIDGE_SESSION_STALE");if(value.generation()!=mirror.generation())throw fail("BRIDGE_GENERATION_STALE");if(!value.modelRevision().equals(mirror.modelRevision()))throw fail("BRIDGE_MODEL_REVISION_STALE");}
    private BridgeProtocolException fail(String message){mirror.transition(BridgeClientState.RESYNC_REQUIRED);coverageFailure.accept(null,message);return new BridgeProtocolException(message);}
    private BridgeProtocolException fail(String message,Throwable cause){return fail(message,cause,null);}
    private BridgeProtocolException fail(String message,Throwable cause,RuntimeEvent event){mirror.transition(BridgeClientState.RESYNC_REQUIRED);coverageFailure.accept(event,message+":"+diagnostic(cause));return new BridgeProtocolException(message,cause);}
    private static String diagnostic(Throwable error){
        var parts=new java.util.ArrayList<String>();
        for(Throwable current=error;current!=null&&parts.size()<8;current=current.getCause()){
            String message=current.getMessage();
            parts.add(current.getClass().getSimpleName()+(message==null||message.isBlank()?"":":"+message));
        }
        return String.join(" <- ",parts);
    }
    @Override public synchronized void close(){
        synchronized(buffered){bootstrapping=false;buffered.clear();asynchronousFailure=null;}
        if(subscription!=null){subscription.close();subscription=null;}transport.close();mirror.transition(BridgeClientState.DISCONNECTED);
    }
}
