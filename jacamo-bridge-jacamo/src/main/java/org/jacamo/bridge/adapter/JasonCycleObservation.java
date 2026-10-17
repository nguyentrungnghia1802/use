package org.jacamo.bridge.adapter;

import java.util.*;
import jason.asSemantics.*;
import jason.asSyntax.*;

/** Called on the agent cycle thread. Polling code consumes this frozen copy, not live interpreter collections. */
public final class JasonCycleObservation {
    private JasonCycleObservation(){}
    public static Map<String,Object> mental(Agent agent,String owner,long cycle) {
        var beliefs=new ArrayList<Map<String,Object>>();boolean available=agent.getBB()!=null;
        if(available)for(Literal literal:agent.getBB())beliefs.add(JasonStructuredTerms.literal(literal));beliefs.sort(Comparator.comparing(b->b.get("raw").toString()));
        return JasonStructuredTerms.immutable(Map.of("version","1.0","owner",owner,"cycle",cycle,"complete",available,"beliefBaseClass",available?agent.getBB().getClass().getName():"","beliefs",beliefs,"authority","AGENT_CYCLE_BB_ITERATOR"));
    }
    public static Map<String,Object> execution(TransitionSystem ts,String owner,long cycle,java.util.function.Function<Object,String> tokens) {
        var rows=new TreeMap<Integer,Intention>();var events=new ArrayList<Map<String,Object>>();var c=ts.getC();
        if(c!=null) {var all=c.getAllIntentions();while(all!=null && all.hasNext()){var i=all.next();rows.put(i.getId(),i);}if(c.getSelectedIntention()!=null)rows.put(c.getSelectedIntention().getId(),c.getSelectedIntention());
            var allEvents=c.getEventsPlusAtomic();var seen=Collections.newSetFromMap(new IdentityHashMap<Event,Boolean>());while(allEvents!=null && allEvents.hasNext())addEvent(events,seen,allEvents.next(),tokens);if(c.getPendingEvents()!=null)c.getPendingEvents().values().forEach(e->addEvent(events,seen,e,tokens));addEvent(events,seen,c.getSelectedEvent(),tokens);}
        var intentions=new ArrayList<Map<String,Object>>();for(var intention:rows.values()) {
            var means=new ArrayList<Map<String,Object>>();int ordinal=0;for(var im:intention) {var row=new LinkedHashMap<String,Object>();row.put("ordinal",ordinal++);row.put("occurrence",tokens.apply(im));row.put("trigger",trigger(im.getTrigger()));row.put("substitution",im.getUnif().toString());row.put("currentStep",im.getCurrentStep()==null?"":im.getCurrentStep().toString());row.put("plan",im.getPlan().toString());row.put("planLabel",im.getPlan().getLabel()==null?"":im.getPlan().getLabel().toString());row.put("planSource",im.getPlan().getSrcInfo()==null?"":im.getPlan().getSrcInfo().getSrcFile());row.put("planLine",im.getPlan().getSrcInfo()==null?0:im.getPlan().getSrcInfo().getBeginSrcLine());means.add(row);}
            intentions.add(Map.of("intentionId",intention.getId(),"suspended",intention.isSuspended(),"place",Objects.toString(intention.getPlace(),"UNKNOWN"),"state",Objects.toString(intention.getStateBasedOnPlace(),"UNKNOWN"),"means",means));
        }
        return JasonStructuredTerms.immutable(Map.of("version","1.0","owner",owner,"cycle",cycle,"complete",c!=null,"intentions",intentions,"events",events,"authority","AGENT_CYCLE_CIRCUMSTANCE"));
    }
    private static void addEvent(List<Map<String,Object>> result,Set<Event> seen,Event event,java.util.function.Function<Object,String> tokens){if(event==null||!seen.add(event))return;result.add(Map.of("occurrence",tokens.apply(event),"trigger",trigger(event.getTrigger()),"intentionId",event.getIntention()==null?-1:event.getIntention().getId(),"external",event.isExternal()));}
    private static Map<String,Object> trigger(Trigger trigger){if(trigger==null)return Map.of("unavailable",true);return Map.of("operator",trigger.getOperator().name(),"type",trigger.getType().name(),"literal",JasonStructuredTerms.literal(trigger.getLiteral()));}
}
