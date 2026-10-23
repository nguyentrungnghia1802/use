package org.jacamo.bridge.adapter;
import java.util.*;
import java.util.function.Consumer;
import org.jacamo.bridge.contract.*;

/** Registry-owned clock provider; the existing source publication lane calls pulse(). */
public final class NplClockCapability implements ObservationCapabilityRegistry.Handle {
    public static final String PROVIDER="npl.source-clock.v1";
    private final ObservationCapabilityRequest request;
    private final NplSourceClockObservation clock;
    private boolean closed;
    public NplClockCapability(ObservationCapabilityRequest request,String run,long generation,String revision,Consumer<VerificationObservation> sink) {
        if(!request.provider().equals(PROVIDER)||!Set.of("hash").containsAll(request.parameters().keySet()))throw new IllegalArgumentException("NPL_CLOCK_REQUEST_UNSUPPORTED");
        if(request.parameters().containsKey("hash")&&!NplRuntimeProfile.evidence().get("artifactSha256").equals(request.parameters().get("hash")))throw new IllegalArgumentException("NPL_CLOCK_REQUESTED_BINARY_MISMATCH");
        this.request=request;clock=new NplSourceClockObservation(run,generation,revision,sink);
    }
    public synchronized void pulse(boolean pending){if(!closed)clock.pulse(pending);}
    public synchronized Capability capability(){return new Capability(request.id(),closed?CapabilityStatus.UNAVAILABLE:CapabilityStatus.COMPLETE,List.of(NplSourceClockObservation.VERSION),closed?"PROVIDER_CLOSED":"Same-process pinned NPL wall clock; per-frame regression/range quality remains authoritative");}
    @Override public synchronized void close(){if(closed)return;closed=true;clock.close();}
}
