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
    public record Accepted(ModelSnapshot model, RuntimeSnapshot runtime, String distributionDigest,
                           String projectKey, java.util.List<org.jacamo.bridge.contract.Capability> capabilities,
                           org.jacamo.bridge.contract.Completeness completeness,
                           String sessionId, long generation, String modelRevision) {
        public Accepted { capabilities=java.util.List.copyOf(capabilities); }
    }
    private final BridgeTransport transport; private final BridgeMirrorStateMachine mirror;
    private final String requiredDistribution; private final Set<String> requiredCapabilities;
    private final java.util.function.Consumer<RuntimeEvent> acceptedEvent;
    private final int maxBufferedEvents; private final ArrayDeque<byte[]> buffered=new ArrayDeque<>();
    private BridgeTransport.Subscription subscription;
    private boolean bootstrapping;
    private String asynchronousFailure;
    public BridgeClient(BridgeTransport transport,BridgeMirrorStateMachine mirror,String requiredDistribution,
                        Set<String> requiredCapabilities,int maxBufferedEvents){
        this(transport,mirror,requiredDistribution,requiredCapabilities,maxBufferedEvents,event->{});
    }
    public BridgeClient(BridgeTransport transport,BridgeMirrorStateMachine mirror,String requiredDistribution,
                        Set<String> requiredCapabilities,int maxBufferedEvents,java.util.function.Consumer<RuntimeEvent> acceptedEvent){
        this.transport=java.util.Objects.requireNonNull(transport);this.mirror=java.util.Objects.requireNonNull(mirror);
        this.requiredDistribution=java.util.Objects.requireNonNull(requiredDistribution);this.requiredCapabilities=Set.copyOf(requiredCapabilities);
        this.acceptedEvent=java.util.Objects.requireNonNull(acceptedEvent);
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
            while(!buffered.isEmpty())applyEvent(buffered.removeFirst());
            bootstrapping=false;
        }
        return new Accepted(model,runtime,handshake.distribution().distributionDigest(),handshake.projectKey(),
                handshake.capabilities(),handshake.completeness(),handshake.sessionId(),handshake.generation(),
                handshake.modelRevision());
    }
    private void acceptSubscriptionEvent(byte[] bytes){
        synchronized(buffered){
            if(bootstrapping){
                if(buffered.size()>=maxBufferedEvents){
                    asynchronousFailure="BRIDGE_BUFFER_OVERFLOW";
                    mirror.transition(BridgeClientState.RESYNC_REQUIRED);
                    return;
                }
                buffered.add(bytes);
                return;
            }
        }
        synchronized(this){applyEvent(bytes);}
    }
    private void acceptSubscriptionFailure(RuntimeException error){
        synchronized(buffered){asynchronousFailure="BRIDGE_SUBSCRIPTION_DISCONNECTED";}
        mirror.transition(BridgeClientState.RESYNC_REQUIRED);
    }
    public synchronized boolean receive(byte[] bytes){return applyEvent(bytes);}
    private boolean applyEvent(byte[] bytes){ContractEnvelope envelope=decode(bytes,MessageType.RUNTIME_EVENT);requireSame(envelope);RuntimeEvent event=ContractPayloads.event(envelope.payload());boolean result=mirror.apply(event);if(result)try{acceptedEvent.accept(event);}catch(RuntimeException error){throw fail("BRIDGE_EVENT_PROJECTION_REJECTED",error);}transport.acknowledge(event.sourceId()+":"+event.sourceSequence());return result;}
    private ContractEnvelope decode(byte[] bytes,MessageType expected){try{ContractEnvelope value=ContractCodec.decode(bytes,4*1024*1024,64,256*1024);new ContractValidator().validate(value);if(value.messageType()!=expected)throw fail("BRIDGE_MESSAGE_TYPE:"+value.messageType());return value;}catch(BridgeProtocolException e){throw e;}catch(RuntimeException e){throw fail("BRIDGE_ENVELOPE_REJECTED",e);}}
    private void requireSame(ContractEnvelope value){if(!value.sessionId().equals(mirror.sessionId()))throw fail("BRIDGE_SESSION_STALE");if(value.generation()!=mirror.generation())throw fail("BRIDGE_GENERATION_STALE");if(!value.modelRevision().equals(mirror.modelRevision()))throw fail("BRIDGE_MODEL_REVISION_STALE");}
    private BridgeProtocolException fail(String message){mirror.transition(BridgeClientState.RESYNC_REQUIRED);return new BridgeProtocolException(message);}
    private BridgeProtocolException fail(String message,Throwable cause){mirror.transition(BridgeClientState.RESYNC_REQUIRED);return new BridgeProtocolException(message,cause);}
    @Override public synchronized void close(){
        synchronized(buffered){bootstrapping=false;buffered.clear();asynchronousFailure=null;}
        if(subscription!=null){subscription.close();subscription=null;}transport.close();mirror.transition(BridgeClientState.DISCONNECTED);
    }
}
