package org.tzi.use.plugins.jacamo.semantics.observer;

import java.util.*;
import org.tzi.use.api.*;
import org.tzi.use.uml.mm.*;
import org.tzi.use.plugins.jacamo.codegrounded.use.DomainProjection;

/** Public board observations in an explicitly labelled helper metamodel. */
public final class FunctionalUseBinding {
    public static final String SCHEME="FunctionalSchemeObservation",MISSION="FunctionalMissionObservation",GOAL="FunctionalGoalObservation";
    public static final String COMMITMENT="FunctionalCommitmentObservation";
    public static final Set<String> TYPES=Set.of(SCHEME,MISSION,GOAL,COMMITMENT);
    private FunctionalUseBinding(){}
    public static boolean typeId(String id){return id.startsWith("observer:") && TYPES.contains(id.substring(9));}
    public static String typeName(String id){if(!typeId(id))throw new IllegalArgumentException("FUNCTIONAL_HELPER_TYPE_UNRESOLVED:"+id);return id.substring(9);}
    public static void install(UseModelApi api)throws UseApiException {
        for(String type:TYPES.stream().sorted().toList()) {
            var cls=api.createClass(type,false);DomainProjection.annotate(cls,"observer-helper","observer:"+type,"");
            cls.addAnnotation(new MElementAnnotation("CslClassifier",Map.of("kind","OBSERVER_HELPER","sourceId64",DomainProjection.encode("observer:"+type))));
            cls.addAnnotation(new MElementAnnotation("FunctionalObservation",Map.of("authority","MOISE_PUBLIC_BOARD","domainEntity","false")));
            for(String attribute:List.of("sourceId","name"))api.createAttribute(type,attribute,"String");
        }
        for(String attribute:List.of("state","goalType","arguments","ttf","location"))api.createAttribute(GOAL,attribute,"String");
        for(String attribute:List.of("minAgents","committedCount","achievedCount"))api.createAttribute(GOAL,attribute,"Integer");
        api.createAttribute(GOAL,"allAgents","Boolean");
        for(String attribute:List.of("minimum","maximum","committedCount"))api.createAttribute(MISSION,attribute,"Integer");
        association(api,"FunctionalGoals",SCHEME,"observedScheme","1",GOAL,"observedGoals","*");
        association(api,"FunctionalMissions",SCHEME,"missionScheme","1",MISSION,"observedMissions","*");
        association(api,"FunctionalMissionGoals",MISSION,"observedMissions","*",GOAL,"missionGoals","*");
        association(api,"FunctionalCommitments","AtomicAgent","committedActors","*",MISSION,"committedMissions","*");
        association(api,"FunctionalGoalCommitted","AtomicAgent","goalCommittedActors","*",GOAL,"committedGoals","*");
        association(api,"FunctionalGoalAchieved","AtomicAgent","goalAchievedActors","*",GOAL,"performedGoals","*");
        association(api,"FunctionalCommitmentActor","AtomicAgent","commitmentActor","1",COMMITMENT,"missionCommitmentOccurrences","*");
        association(api,"FunctionalCommitmentMission",MISSION,"commitmentMission","1",COMMITMENT,"commitmentOccurrences","*");
        var bounds=api.createInvariant("FunctionalMissionAllocation",MISSION,"self.committedCount >= self.minimum and (self.maximum = -1 or self.committedCount <= self.maximum)",false);
        bounds.addAnnotation(new MElementAnnotation("CslConstraint",Map.of("scopedFunctional","true")));
        association(api,"FunctionalResponsibility","OrgContext","responsibleContexts","*",SCHEME,"responsibleSchemes","*");
    }
    private static void association(UseModelApi api,String id,String one,String roleOne,String countOne,String two,String roleTwo,String countTwo)throws UseApiException {
        api.createAssociation(id,one,roleOne,countOne,0,two,roleTwo,countTwo,0);
        api.getModel().getAssociation(id).addAnnotation(new MElementAnnotation("CslAssociation",Map.of("kind","FUNCTIONAL_OBSERVER")));
    }
}
