package org.tzi.use.plugins.jacamo.semantics.mapping.xml.runtime;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.jacamo.bridge.contract.*;
import org.tzi.use.plugins.jacamo.semantics.mapping.xml.MappingSpecification.Node;

/** Exact stored-iterator views of the existing Jason cycle publication. Never
 * executes entailment, decodes guessed beliefs, or copies mental state into world state. */
public final class JasonBeliefViews {
    private JasonBeliefViews() { }
    public static void validate(Node node) {
        if(!Set.of("FACTS","RULES","STORED").contains(node.attr("selection"))
                ||!node.attr("arity").matches("\\*|0|[1-9][0-9]{0,2}")
                ||node.attr("namespace").isBlank()||node.attr("predicate").isBlank()
                ||node.attr("namespace").length()>256||node.attr("predicate").length()>256)
            throw node.location().error("UNSUPPORTED:BELIEF_ITERATOR_SELECTION");
    }
    public static RuntimeTables.Dataset capture(Node node,RuntimeSnapshot cut,String run,long generation,String proof) {
        var rows=new ArrayList<RuntimeTables.Row>();
        Completeness population=cut.sourceCompleteness().getOrDefault("jason",Completeness.UNAVAILABLE);
        for(var fact:cut.facts())if(fact.kind()==RuntimeFactKind.AGENT&&fact.id().authority().equals("jason")
                &&fact.id().dimension().equals("agent")&&fact.id().kind().equals("runtime-agent")) {
            String identity=fact.id().canonical();var values=new TreeMap<String,Object>();
            values.put("identity",identity);values.put("player",identity);values.put("run",run);values.put("generation",generation);values.put("revision",cut.modelRevision());
            values.put("incarnation",fact.id().incarnation());values.put("selection",node.attr("selection"));
            Completeness quality=Completeness.PARTIAL;
            try {
                if(population==Completeness.UNAVAILABLE||!cut.startWatermarks().containsKey("jason")||!Objects.equals(cut.startWatermarks().get("jason"),cut.endWatermarks().get("jason"))
                        ||fact.completeness()!=Completeness.COMPLETE||fact.projectionStatus()!=ProjectionStatus.MATERIALIZED_FAITHFULLY)
                    throw new IllegalArgumentException("BELIEF_SOURCE_CUT_INCOMPLETE");
                boolean lexical=fact.values().containsKey("storedBeliefObservation");
                var mental=CanonicalJson.object(fact.values().get(lexical?"storedBeliefObservation":"mentalObservation"));
                if(!(lexical?"STORED_LITERALS/1.0":"1.0").equals(mental.get("version"))||!identity.equals(mental.get("owner"))
                        ||!Boolean.TRUE.equals(mental.get("complete"))||!"AGENT_CYCLE_BB_ITERATOR".equals(mental.get("authority"))
                        ||!(mental.get("beliefBaseClass") instanceof String cls)||cls.isBlank())throw new IllegalArgumentException("BELIEF_ITERATOR_PUBLICATION_UNAVAILABLE");
                long cycle=integer(mental.get("cycle"));if(cycle!=integer(fact.values().get("cycles")))throw new IllegalArgumentException("BELIEF_CYCLE_MISMATCH");
                if(!(mental.get("beliefs") instanceof List<?> literals)||literals.size()>4096)throw new IllegalArgumentException("BELIEF_ITERATOR_CAPACITY_OR_SHAPE");
                var selected=new TreeSet<String>();var seen=new HashSet<String>();
                for(var raw:literals) {
                    var literal=CanonicalJson.object(raw);
                    int arity;
                    if(lexical) {long actual=integer(literal.get("arity"));if(actual>1024)throw new IllegalArgumentException("BELIEF_ARITY_UNSUPPORTED");arity=(int)actual;}
                    else {if(!(literal.get("terms") instanceof List<?> terms))throw new IllegalArgumentException("BELIEF_TERMS_REQUIRED");arity=terms.size();}
                    if(!(literal.get("raw") instanceof String text)||text.isBlank()||text.length()>65536||!seen.add(text)
                            ||!(literal.get("namespace") instanceof String namespace)||!(literal.get("functor") instanceof String predicate)
                            ||!(literal.get("rule") instanceof Boolean rule))throw new IllegalArgumentException("BELIEF_LITERAL_SHAPE_OR_DUPLICATE");
                    if(matches(node.attr("namespace"),namespace)&&matches(node.attr("predicate"),predicate)
                            &&(node.attr("arity").equals("*")||Integer.parseInt(node.attr("arity"))==arity)
                            &&(node.attr("selection").equals("STORED")||node.attr("selection").equals(rule?"RULES":"FACTS")))selected.add(text);
                }
                byte[] json=CanonicalJson.encode(List.copyOf(selected));if(json.length>1024*1024)throw new IllegalArgumentException("BELIEF_VIEW_CAPACITY");
                values.put("beliefJson",new String(json,StandardCharsets.UTF_8));values.put("cycle",cycle);values.put("authority","AGENT_CYCLE_BB_ITERATOR");quality=Completeness.COMPLETE;
            }catch(IllegalArgumentException|ContractException unavailable) {
                values.put("diagnostic",Objects.toString(unavailable.getMessage(),"BELIEF_VIEW_INCOMPLETE"));
                population=population==Completeness.UNAVAILABLE?population:Completeness.PARTIAL;
            }
            rows.add(new RuntimeTables.Row(identity,values,quality,Map.of("source-cut",proof,"physical-identity",identity),Set.of(identity)));
        }
        return new RuntimeTables.Dataset(rows,population);
    }
    private static boolean matches(String selector,String value){return selector.equals("*")||selector.equals(value);}
    private static long integer(Object value) {
        if(!(value instanceof Number n))throw new IllegalArgumentException("BELIEF_CYCLE_REQUIRED");
        try {long cycle=new BigDecimal(n.toString()).longValueExact();if(cycle<0)throw new ArithmeticException();return cycle;}
        catch(ArithmeticException invalid){throw new IllegalArgumentException("BELIEF_CYCLE_INVALID",invalid);}
    }
}
