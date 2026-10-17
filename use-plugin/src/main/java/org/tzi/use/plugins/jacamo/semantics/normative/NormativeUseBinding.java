package org.tzi.use.plugins.jacamo.semantics.normative;

import java.util.*;
import org.tzi.use.api.*;
import org.tzi.use.uml.mm.*;
import org.tzi.use.plugins.jacamo.codegrounded.use.DomainProjection;

public final class NormativeUseBinding {
    public static final String TYPE="NormativeObservation";
    private NormativeUseBinding(){}
    public static void install(UseModelApi api)throws UseApiException {
        var cls=api.createClass(TYPE,false);DomainProjection.annotate(cls,"observer-helper","observer:"+TYPE,"");cls.addAnnotation(new MElementAnnotation("CslClassifier",Map.of("kind","OBSERVER_HELPER","sourceId64",DomainProjection.encode("observer:"+TYPE))));cls.addAnnotation(new MElementAnnotation("NormativeObservation",Map.of("authority","NPL_REPORTED_ONLY","independentDerivation","UNSUPPORTED")));
        for(String name:List.of("normDefinition","activationKey","subject","substitution","modality","state","aim","maintenance","deadlineKind","deadlineRaw","deadline","clockEvidence","sourceVersion"))api.createAttribute(TYPE,name,"String");
    }
}
