package org.tzi.use.plugins.jacamo.semantics.observer;

import java.util.*;
import org.tzi.use.api.*;
import org.tzi.use.uml.mm.*;
import org.tzi.use.plugins.jacamo.codegrounded.use.DomainProjection;

public final class MentalUseBinding {
    public static final String STATE="MentalStateObservation",BELIEF="StoredBeliefObservation",EXECUTION="ExecutionObservation";
    public static final Set<String> TYPES=Set.of(STATE,BELIEF,EXECUTION);
    private MentalUseBinding(){}
    public static boolean typeId(String id){return id.startsWith("observer:") && TYPES.contains(id.substring(9));}
    public static String typeName(String id){if(!typeId(id))throw new IllegalArgumentException("MENTAL_HELPER_TYPE_UNRESOLVED");return id.substring(9);}
    public static void install(UseModelApi api)throws UseApiException {
        for(String type:TYPES.stream().sorted().toList()) {var cls=api.createClass(type,false);DomainProjection.annotate(cls,"observer-helper","observer:"+type,"");cls.addAnnotation(new MElementAnnotation("CslClassifier",Map.of("kind","OBSERVER_HELPER","sourceId64",DomainProjection.encode("observer:"+type))));cls.addAnnotation(new MElementAnnotation("MentalObservation",Map.of("authority","JASON_AGENT_CYCLE","domainEntity","false")));}
        for(String attribute:List.of("cycle","beliefBaseClass"))api.createAttribute(STATE,attribute,"String");for(String attribute:List.of("complete","hasRules","groundFactsOnly"))api.createAttribute(STATE,attribute,"Boolean");
        for(String attribute:List.of("raw","namespace","functor","arguments","annotations","literalKey","ruleBody"))api.createAttribute(BELIEF,attribute,"String");for(String attribute:List.of("negative","rule","ground","supported"))api.createAttribute(BELIEF,attribute,"Boolean");
        for(String attribute:List.of("cycle","payload"))api.createAttribute(EXECUTION,attribute,"String");api.createAttribute(EXECUTION,"complete","Boolean");
        association(api,"AgentMentalState","AtomicAgent","epistemicOwner","1",STATE,"mentalState","0..1");association(api,"StoredBeliefs",STATE,"mentalState","1",BELIEF,"storedBeliefs","*");association(api,"AgentExecution","AtomicAgent","executiveOwner","1",EXECUTION,"executionState","0..1");
    }
    private static void association(UseModelApi api,String id,String one,String roleOne,String countOne,String two,String roleTwo,String countTwo)throws UseApiException {api.createAssociation(id,one,roleOne,countOne,0,two,roleTwo,countTwo,0);api.getModel().getAssociation(id).addAnnotation(new MElementAnnotation("CslAssociation",Map.of("kind","MENTAL_OBSERVER")));}
}
