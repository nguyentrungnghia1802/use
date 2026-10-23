package org.jacamo.bridge.adapter;
import java.util.*;
import org.jacamo.bridge.contract.*;

/** Trusted Java factories only. XML selects a registered identifier, never a Java class or script. */
public final class ObservationCapabilityRegistry {
    public interface Handle extends AutoCloseable {
        Capability capability();
        @Override void close();
    }
    @FunctionalInterface public interface Provider {Handle open(ObservationCapabilityRequest request)throws Exception;}
    private final Map<String,Provider> providers;
    public ObservationCapabilityRegistry(Map<String,Provider> providers) {this.providers=Map.copyOf(providers);}
    public Session open(List<ObservationCapabilityRequest> requests) {
        if(requests.size()>64)throw new IllegalArgumentException("OBSERVATION_CAPABILITY_REQUEST_CAPACITY");
        var ids=new HashSet<String>();for(var request:requests)if(!ids.add(request.id()))throw new IllegalArgumentException("OBSERVATION_CAPABILITY_REQUEST_DUPLICATE");
        var opened=new LinkedHashMap<String,Handle>();
        try {
            for(var request:requests) {
                Handle handle;var provider=providers.get(request.provider());
                if(provider==null)handle=unavailable(request,"UNSUPPORTED:PROVIDER_NOT_REGISTERED:"+request.provider());
                else try {
                    handle=Objects.requireNonNull(provider.open(request));
                    try {
                        if(!handle.capability().name().equals(request.id()))throw new IllegalArgumentException("PROVIDER_REQUEST_IDENTITY_MISMATCH");
                    }catch(RuntimeException|Error rejected){try{handle.close();}catch(RuntimeException cleanup){rejected.addSuppressed(cleanup);}throw rejected;}
                }catch(Exception|LinkageError failure){handle=unavailable(request,"INCOMPLETE:PROVIDER_OPEN_REJECTED:"+failure.getClass().getSimpleName()+":"+failure.getMessage());}
                opened.put(request.id(),handle);
            }
            return new Session(requests,opened);
        }catch(RuntimeException|Error failure){try{closeAll(opened.values());}catch(RuntimeException cleanup){failure.addSuppressed(cleanup);}throw failure;}
    }
    private static Handle unavailable(ObservationCapabilityRequest request,String reason) {
        return new Handle() {
            public Capability capability(){return new Capability(request.id(),CapabilityStatus.UNAVAILABLE,List.of(),reason);}
            public void close(){ }
        };
    }
    private static void closeAll(Collection<Handle> handles) {
        var order=new ArrayList<>(handles);Collections.reverse(order);
        RuntimeException failure=null;for(var handle:order)try{handle.close();}catch(RuntimeException error){if(failure==null)failure=error;else failure.addSuppressed(error);}
        if(failure!=null)throw failure;
    }
    public static final class Session implements AutoCloseable {
        private final List<ObservationCapabilityRequest> requests;
        private final Map<String,Handle> handles;
        private boolean closed;
        private Session(List<ObservationCapabilityRequest> requests,Map<String,Handle> handles){this.requests=List.copyOf(requests);this.handles=Collections.unmodifiableMap(new LinkedHashMap<>(handles));}
        public synchronized List<Capability> capabilities(){requireOpen();return handles.values().stream().map(Handle::capability).toList();}
        public synchronized List<Capability> requiredUnavailable(){requireOpen();return requests.stream().filter(ObservationCapabilityRequest::required).map(r->handles.get(r.id()).capability()).filter(c->c.status()!=CapabilityStatus.COMPLETE).toList();}
        public synchronized <T extends Handle> Optional<T> handle(String id,Class<T> type){requireOpen();var value=handles.get(id);return type.isInstance(value)?Optional.of(type.cast(value)):Optional.empty();}
        private void requireOpen(){if(closed)throw new IllegalStateException("OBSERVATION_CAPABILITY_SESSION_CLOSED");}
        @Override public synchronized void close(){if(closed)return;closed=true;closeAll(handles.values());}
    }
}
