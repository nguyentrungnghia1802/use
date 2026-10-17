package org.tzi.use.plugins.jacamo.semantics.observer;

import java.util.*;
import org.jacamo.bridge.contract.CanonicalJson;
import org.tzi.use.plugins.jacamo.semantics.evidence.SemanticJson;

/** Stored BB content with an epistemic owner and cycle. It does not assert world truth or arbitrary entailment. */
public record MentalStateObservation(String owner,long cycle,boolean complete,String beliefBaseClass,List<Map<String,Object>> beliefs,Map<String,Object> raw) {
    public MentalStateObservation {beliefs=beliefs.stream().map(SemanticJson::object).toList();raw=SemanticJson.object(raw);}
    public static MentalStateObservation decode(String owner,Object value) {
        var map=CanonicalJson.object(value);if(!"1.0".equals(map.get("version")) || !owner.equals(map.get("owner")) || !"AGENT_CYCLE_BB_ITERATOR".equals(map.get("authority")))throw new IllegalArgumentException("MENTAL_CUT_VERSION_OWNER_AUTHORITY");
        long cycle;try{cycle=new java.math.BigDecimal(map.get("cycle").toString()).longValueExact();}catch(RuntimeException e){throw new IllegalArgumentException("MENTAL_CYCLE_INVALID",e);}if(cycle<0)throw new IllegalArgumentException("MENTAL_CYCLE_INVALID");
        if(!(map.get("complete") instanceof Boolean complete) || !(map.get("beliefBaseClass") instanceof String bb) || !(map.get("beliefs") instanceof List<?> values))throw new IllegalArgumentException("MENTAL_CUT_SHAPE");
        var beliefs=new ArrayList<Map<String,Object>>();var raws=new HashSet<String>();for(Object raw:values) {var belief=CanonicalJson.object(raw);for(String field:List.of("raw","namespace","functor","key","ruleBody","annotationTail"))if(!(belief.get(field) instanceof String))throw new IllegalArgumentException("MENTAL_BELIEF_TEXT:"+field);for(String field:List.of("negative","rule","ground","supported"))if(!(belief.get(field) instanceof Boolean))throw new IllegalArgumentException("MENTAL_BELIEF_FLAG:"+field);if(!(belief.get("terms") instanceof List<?>) || !(belief.get("annotations") instanceof List<?>))throw new IllegalArgumentException("MENTAL_BELIEF_TERMS");if(!raws.add((String)belief.get("raw")))throw new IllegalArgumentException("MENTAL_DUPLICATED_STORED_ROW");beliefs.add(belief);}
        return new MentalStateObservation(owner,cycle,complete,bb,beliefs,map);
    }
}
