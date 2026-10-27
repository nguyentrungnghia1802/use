package org.jacamo.bridge.adapter;

import java.util.*;
import ora4mas.nopl.*;
import org.jacamo.bridge.contract.*;

/** Registry ownership of the existing public NPL source; no extra engine, transport or timer. */
public final class NplLifecycleCapability implements ObservationCapabilityRegistry.Handle {
    public static final String PROVIDER="npl.lifecycle.v1";
    private final ObservationCapabilityRequest request;
    private final NplBoardSnapshotSource source;
    private final boolean ownsSource;
    private boolean closed;
    public NplLifecycleCapability(ObservationCapabilityRequest request,FrameworkObservationProviders.Context context) {
        if(!request.provider().equals(PROVIDER)||!request.parameters().keySet().equals(Set.of("hash")))throw new IllegalArgumentException("NPL_LIFECYCLE_REQUEST_UNSUPPORTED");
        NplRuntimeProfile.requireSupported();
        if(!NplRuntimeProfile.evidence().get("artifactSha256").equals(request.parameters().get("hash")))throw new IllegalArgumentException("NPL_LIFECYCLE_REQUESTED_BINARY_MISMATCH");
        this.request=request;
        ownsSource=context.nplSource()==null;
        source=ownsSource?new NplBoardSnapshotSource(context.run(),context.generation(),context.source().modelRevision(),
                ()->OfficialBoardInventory.collect(context.environment(),OrgArt.class,NormativeBoard.getNormativeBoards())):context.nplSource();
    }
    public NplBoardSnapshotSource source(){return source;}
    public synchronized Capability capability(){return new Capability(request.id(),closed?CapabilityStatus.UNAVAILABLE:CapabilityStatus.COMPLETE,List.of("NPL_OBSERVATION/1.0"),closed?"PROVIDER_CLOSED":"Pinned public NPL lifecycle source; individual activation and selected-population quality remain authoritative");}
    @Override public synchronized void close(){if(closed)return;closed=true;if(ownsSource)source.close();}
}
