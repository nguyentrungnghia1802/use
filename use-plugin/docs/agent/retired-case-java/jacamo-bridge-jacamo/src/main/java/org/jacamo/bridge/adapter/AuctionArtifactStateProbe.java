package org.jacamo.bridge.adapter;
import java.lang.reflect.Field;
import java.util.*;
import java.util.function.Predicate;
import cartago.*;
import org.jacamo.bridge.contract.*;
/** Historical selection policy over the neutral bounded field provider. */
public final class AuctionArtifactStateProbe implements AutoCloseable {
    public static final String VERSION="1.0.0",CLASS="auction_env.AuctionArtifact";
    private final ArtifactFieldObservation provider;
    public AuctionArtifactStateProbe(CartagoEnvironment environment,ModelSnapshot source){this(environment,source,Field::trySetAccessible);}
    public AuctionArtifactStateProbe(CartagoEnvironment environment,ModelSnapshot source,Predicate<Field> accessPolicy) {
        provider=new ArtifactFieldObservation(environment,source,new ObservationCapabilityRequest("legacy-field",ArtifactFieldObservation.PROVIDER,true,
            Map.of("class",CLASS,"field","currentWinner","type","java.lang.String","properties",Map.of("running","BOOLEAN","task","STRING","best_bid","IEEE754_BINARY64","winner","JASON_ATOM"))),accessPolicy);
    }
    public boolean available(){return provider.available();}
    public String diagnostic(){return provider.diagnostic();}
    public Map<String,Object> capture(ArtifactId identity,String origin,String invocation){return provider.capture(identity,origin,invocation);}
    @Override public void close(){provider.close();}
}
