package org.tzi.use.plugins.jacamo.semantics.binding;

import java.util.*;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.*;

/** Immutable native snapshot image, with no writable MSystem or live MObject references. */
public record ProjectionReadView(String profile,long version,String stateHash,String freshness,Map<String,VerificationSnapshot.ObjectState> objects,List<VerificationSnapshot.LinkState> links,org.tzi.use.plugins.jacamo.semantics.evidence.SemanticVerificationEnvelope semantic) {
    public ProjectionReadView {objects=Map.copyOf(objects);links=List.copyOf(links);}
    public static ProjectionReadView read(RuntimeVerificationCoordinator coordinator) {
        return coordinator.read(()->{var cut=coordinator.verificationSnapshot();var result=cut.result();return new ProjectionReadView(result!=null && result.semantic()!=null?result.semantic().profile():"NATIVE_CURRENT",cut.currentVersion(),result==null?"":result.stateHash(),result==null?"UNAVAILABLE":result.freshness(),cut.image()==null?Map.of():cut.image().objects(),cut.image()==null?List.of():cut.image().links(),result==null?null:result.semantic());});
    }
}
