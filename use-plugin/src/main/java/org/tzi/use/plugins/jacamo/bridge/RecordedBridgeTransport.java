package org.tzi.use.plugins.jacamo.bridge;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

/** Deterministic framed test transport used by the mandatory early process proof. */
public final class RecordedBridgeTransport implements BridgeTransport {
    private final List<byte[]> frames; private Consumer<byte[]> receiver; private boolean closed;
    public RecordedBridgeTransport(List<byte[]> frames){if(frames.size()<3)throw new IllegalArgumentException("BRIDGE_RECORDING_INCOMPLETE");this.frames=frames.stream().map(byte[]::clone).toList();}
    @Override public byte[] handshake(){return frame(0);}
    @Override public byte[] modelSnapshot(){return frame(1);}
    @Override public synchronized byte[] runtimeSnapshot(){byte[] result=frame(2);if(receiver!=null)for(int i=3;i<frames.size();i++)receiver.accept(frame(i));return result;}
    @Override public synchronized Subscription subscribe(String resumeToken,Consumer<byte[]> next){if(receiver!=null)throw new IllegalStateException("BRIDGE_RECORDING_ALREADY_SUBSCRIBED");receiver=next;return ()->{synchronized(this){receiver=null;}};}
    @Override public void acknowledge(String resumeToken){if(resumeToken==null||resumeToken.isBlank())throw new IllegalArgumentException("BRIDGE_ACK_TOKEN_REQUIRED");}
    private byte[] frame(int index){if(closed)throw new IllegalStateException("BRIDGE_TRANSPORT_CLOSED");return frames.get(index).clone();}
    @Override public synchronized void close(){closed=true;receiver=null;}
}
