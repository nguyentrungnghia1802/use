package org.tzi.use.plugins.jacamo.semantics.observer;

import java.util.*;
import org.jacamo.bridge.contract.CanonicalJson;
import org.tzi.use.plugins.jacamo.semantics.evidence.SemanticJson;

/** Raw/structured execution observation. A missing intention or event is never success evidence. */
public record ExecutionObservation(String owner,long cycle,boolean complete,List<Map<String,Object>> intentions,List<Map<String,Object>> events,Map<String,Object> raw) {
    public ExecutionObservation {intentions=intentions.stream().map(SemanticJson::object).toList();events=events.stream().map(SemanticJson::object).toList();raw=SemanticJson.object(raw);}
    public static ExecutionObservation decode(String owner,Object value) {
        var map=CanonicalJson.object(value);if(!"1.0".equals(map.get("version")) || !owner.equals(map.get("owner")) || !"AGENT_CYCLE_CIRCUMSTANCE".equals(map.get("authority")))throw new IllegalArgumentException("EXECUTION_CUT_VERSION_OWNER_AUTHORITY");
        if(!(map.get("intentions") instanceof List<?> intentions) || !(map.get("events") instanceof List<?> events) || !(map.get("complete") instanceof Boolean complete))throw new IllegalArgumentException("EXECUTION_CUT_SHAPE");
        long cycle=new java.math.BigDecimal(map.get("cycle").toString()).longValueExact();if(cycle<0)throw new IllegalArgumentException("EXECUTION_CYCLE_INVALID");
        var ids=new HashSet<Object>();for(Object row:intentions) {var i=CanonicalJson.object(row);if(!ids.add(i.get("intentionId")) || !(i.get("means") instanceof List<?>))throw new IllegalArgumentException("EXECUTION_INTENTION_ID_OR_STACK_INVALID");}
        return new ExecutionObservation(owner,cycle,complete,intentions.stream().map(CanonicalJson::object).toList(),events.stream().map(CanonicalJson::object).toList(),map);
    }
}
