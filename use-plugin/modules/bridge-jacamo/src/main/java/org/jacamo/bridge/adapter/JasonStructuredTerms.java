package org.jacamo.bridge.adapter;

import java.util.*;
import jason.asSyntax.*;
import org.jacamo.bridge.contract.CanonicalJson;

/** Bounded structured copies of official terms. Raw unsupported syntax is retained, never evaluated. */
public final class JasonStructuredTerms {
    private JasonStructuredTerms(){}
    /** Same bounded copy used by literal terms; no parsing/evaluation of consumer strings. */
    public static Map<String,Object> term(Term value) {
        return term(Objects.requireNonNull(value),0,new IdentityHashMap<>());
    }
    public static Map<String,Object> literal(Literal literal) {
        var result=new LinkedHashMap<String,Object>();result.put("raw",literal.toString());result.put("namespace",literal.getNS().getFunctor());result.put("functor",literal.getFunctor());result.put("negative",literal.negated());result.put("rule",literal instanceof Rule);result.put("ground",literal.isGround());
        var terms=new ArrayList<Map<String,Object>>();for(int i=0;i<literal.getArity();i++)terms.add(term(literal.getTerm(i),0,new IdentityHashMap<>()));result.put("terms",List.copyOf(terms));
        var annotations=new ArrayList<String>();if(literal.getAnnots()!=null)for(Term annotation:literal.getAnnots())annotations.add(annotation.toString());result.put("annotations",annotations.stream().sorted().toList());result.put("annotationTail",literal.getAnnots()==null || literal.getAnnots().getTail()==null?"":literal.getAnnots().getTail().toString());
        boolean supported=!(literal instanceof Rule) && literal.isGround() && !literal.isInternalAction() && !literal.isCyclicTerm() && terms.stream().allMatch(t->Boolean.TRUE.equals(t.get("supported")));result.put("supported",supported);result.put("ruleBody",literal instanceof Rule rule?rule.getBody().toString():"");
        result.put("key",new String(CanonicalJson.encode(Map.of("namespace",literal.getNS().getFunctor(),"functor",literal.getFunctor(),"negative",literal.negated(),"terms",terms.stream().map(t->t.get("key")).toList())),java.nio.charset.StandardCharsets.UTF_8));return immutable(result);
    }
    private static Map<String,Object> term(Term value,int depth,IdentityHashMap<Term,Boolean> path) {
        var result=new LinkedHashMap<String,Object>();result.put("raw",value.toString());
        if(value.isVar() || depth>=32 || value.isCyclicTerm() || path.put(value,true)!=null)return Map.of("raw",value.toString(),"supported",false,"key",Map.of("unsupported",value.toString()));
        try {
            Object key;boolean supported=true;
            if(value instanceof StringTerm string)key=Map.of("kind","STRING","value",string.getString());
            else if(value instanceof NumberTermImpl number && Double.isFinite(number.solve()))key=Map.of("kind","NUMBER","value",new java.math.BigDecimal(Double.toString(number.solve())).stripTrailingZeros());
            else if(value instanceof ListTerm list) {
                var items=new ArrayList<Map<String,Object>>();for(Term item:list)items.add(term(item,depth+1,path));supported=list.isGround() && list.getTail()==null && items.stream().allMatch(t->Boolean.TRUE.equals(t.get("supported")));key=Map.of("kind","LIST","items",items.stream().map(t->t.get("key")).toList(),"tail",list.getTail()==null?"":list.getTail().toString());
            } else if(value instanceof Literal literal && !literal.isVar() && !literal.isRule() && !literal.isInternalAction()) {
                var items=new ArrayList<Map<String,Object>>();for(int i=0;i<literal.getArity();i++)items.add(term(literal.getTerm(i),depth+1,path));supported=literal.isGround() && !literal.hasAnnot() && items.stream().allMatch(t->Boolean.TRUE.equals(t.get("supported")));key=Map.of("kind","LITERAL","namespace",literal.getNS().getFunctor(),"functor",literal.getFunctor(),"negative",literal.negated(),"terms",items.stream().map(t->t.get("key")).toList());
            } else {supported=false;key=Map.of("kind","UNSUPPORTED","raw",value.toString(),"javaClass",value.getClass().getName());}
            result.put("ground",value.isGround());result.put("supported",supported);result.put("key",key);return immutable(result);
        } finally {path.remove(value);}
    }
    public static Map<String,Object> immutable(Map<String,Object> source){return CanonicalJson.object(CanonicalJson.decode(CanonicalJson.encode(source)));}
}
