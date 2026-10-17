package org.tzi.use.plugins.jacamo.semantics.exploration;

import java.util.*;
import org.tzi.use.plugins.jacamo.semantics.istar.RequirementModel;
import org.tzi.use.plugins.jacamo.semantics.csl.ScalarValueCodec;

/** Authored finite relation, not an effect inferred from a Java/ASL body or an OCL postcondition. */
public record TransitionProfile(String version,String sourceRevision,String approval,boolean transitionCoverageComplete,
        List<ObjectDeclaration> population,List<Link> fixedLinks,Map<String,State> states,Set<String> initial,Set<String> terminal,
        List<Transition> transitions,RequirementModel requirements,Set<String> roots,List<String> assumptions) {
    public static final String VERSION="FINITE_FIXED_POPULATION_V1",APPROVAL="EXPLICIT_FINITE_RELATION_WITH_COMPLETE_VALUE_VECTORS";
    public record Scalar(String type,String text) {
        public Scalar {ScalarValueCodec.requireType(type);Objects.requireNonNull(text);}
        public org.tzi.use.uml.ocl.value.Value value(){return ScalarValueCodec.decode(type,List.of(text),List.of(switch(type){case "Integer"->"java.lang.Integer";case "Real"->"java.lang.Double";case "Boolean"->"java.lang.Boolean";default->"java.lang.String";}));}
    }
    public record ObjectDeclaration(String identity,String type,String sourceId){public ObjectDeclaration{required(identity);required(type);required(sourceId);}}
    public record Link(String association,List<String> participants){public Link{required(association);participants=List.copyOf(participants);}}
    /** All scalar attributes of every population member are supplied. Topology is an explicit fixed frame. */
    public record State(Map<String,Map<String,Scalar>> values,Map<String,String> executive,long clock) {
        public State {var copy=new TreeMap<String,Map<String,Scalar>>();values.forEach((k,v)->copy.put(k,Collections.unmodifiableMap(new TreeMap<>(v))));values=Collections.unmodifiableMap(copy);executive=Collections.unmodifiableMap(new TreeMap<>(executive));if(clock<0)throw new IllegalArgumentException("NEGATIVE_LOGICAL_CLOCK");}
    }
    public record Transition(String id,String sourceId,String actor,String from,String to,String self,String guard,String post,Map<String,org.tzi.use.plugins.jacamo.semantics.evidence.SemanticVerdict> taskOutcomes) {
        public Transition {for(String s:List.of(id,sourceId,actor,from,to,self,guard,post))required(s);taskOutcomes=Map.copyOf(taskOutcomes);}
    }
    public TransitionProfile {
        required(version);required(sourceRevision);required(approval);population=List.copyOf(population);fixedLinks=List.copyOf(fixedLinks);states=Collections.unmodifiableMap(new TreeMap<>(states));initial=Set.copyOf(initial);terminal=Set.copyOf(terminal);transitions=List.copyOf(transitions);Objects.requireNonNull(requirements);roots=Set.copyOf(roots);assumptions=List.copyOf(assumptions);
        if(population.isEmpty() || states.isEmpty() || initial.isEmpty() || roots.isEmpty() || assumptions.isEmpty())throw new IllegalArgumentException("FINITE_PROFILE_SCOPE_REQUIRED");
        if(population.stream().map(ObjectDeclaration::identity).distinct().count()!=population.size() || transitions.stream().map(Transition::id).distinct().count()!=transitions.size())throw new IllegalArgumentException("FINITE_PROFILE_DUPLICATED_ID");
        if(!states.keySet().containsAll(initial) || !states.keySet().containsAll(terminal))throw new IllegalArgumentException("FINITE_PROFILE_STATE_UNRESOLVED");
        var nodes=new HashSet<String>();requirements.nodes().forEach(n->nodes.add(n.id()));if(!nodes.containsAll(roots))throw new IllegalArgumentException("FINITE_PROFILE_ROOT_UNRESOLVED");
        for(var t:transitions){if(!states.containsKey(t.from()) || !states.containsKey(t.to()))throw new IllegalArgumentException("FINITE_PROFILE_TRANSITION_STATE_UNRESOLVED");if(terminal.contains(t.from()))throw new IllegalArgumentException("FINITE_TERMINAL_HAS_SUCCESSOR");if(states.get(t.to()).clock()<states.get(t.from()).clock())throw new IllegalArgumentException("FINITE_CLOCK_REGRESSION");}
    }
    private static void required(String s){if(s==null || s.isBlank())throw new IllegalArgumentException("FINITE_PROFILE_ID_REQUIRED");}
}
