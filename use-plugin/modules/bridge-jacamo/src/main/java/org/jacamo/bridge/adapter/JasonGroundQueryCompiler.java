package org.jacamo.bridge.adapter;

import java.util.*;
import jason.asSyntax.*;

/** Admits only a ground, side-effect-free Boolean AST. Interpretation mode is chosen explicitly in USE. */
public final class JasonGroundQueryCompiler {
    public Map<String,Object> compile(String expression) {
        try {var ast=ASSyntax.parseFormula(expression);return Map.of("version","1.0","source",expression,"ast",node(ast,0));}
        catch(Exception unsupported){return Map.of("version","1.0","source",expression,"ast",Map.of("op","UNSUPPORTED","reason","JASON_FORMULA_PARSE_OR_FRAGMENT:"+unsupported.getClass().getSimpleName()));}
    }
    private Map<String,Object> node(LogicalFormula formula,int depth) {
        if(depth>=32)return unsupported("QUERY_DEPTH_LIMIT");
        if(formula.equals(Literal.LTrue))return Map.of("op","TRUE");if(formula.equals(Literal.LFalse))return Map.of("op","FALSE");
        if(formula instanceof LogExpr expression) {
            return switch(expression.getOp()) {case and,or->Map.of("op",expression.getOp().name().toUpperCase(Locale.ROOT),"children",List.of(node(expression.getLHS(),depth+1),node(expression.getRHS(),depth+1)));case not->Map.of("op","NOT","children",List.of(node(expression.getLHS(),depth+1)));default->unsupported("LOGICAL_OPERATOR_UNSUPPORTED");};
        }
        if(formula instanceof Literal literal && Set.of(LiteralImpl.class,Pred.class,Atom.class).contains(literal.getClass())) {
            var value=JasonStructuredTerms.literal(literal);
            if(Boolean.TRUE.equals(value.get("supported")) && !literal.hasAnnot())return Map.of("op","LITERAL","key",value.get("key"));
            return unsupported("OPEN_ANNOTATED_SIDE_EFFECT_OR_RULE_QUERY");
        }
        return unsupported("LOGICAL_FORMULA_CLASS_UNSUPPORTED:"+formula.getClass().getName());
    }
    private Map<String,Object> unsupported(String reason){return Map.of("op","UNSUPPORTED","reason",reason);}
}
