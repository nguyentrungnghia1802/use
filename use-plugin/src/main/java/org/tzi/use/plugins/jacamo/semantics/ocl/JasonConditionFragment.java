package org.tzi.use.plugins.jacamo.semantics.ocl;

import java.util.*;
import org.jacamo.bridge.contract.CanonicalJson;
import org.tzi.use.util.StringUtil;

/** Consumes the official AST producer contract, never reparses arbitrary Jason code as OCL. */
public final class JasonConditionFragment {
    public String compile(Map<String,Object> query) {
        if(!"1.0".equals(query.get("version")))throw new IllegalArgumentException("UNSUPPORTED:JASON_QUERY_VERSION");
        return node(CanonicalJson.object(query.get("ast")),0);
    }
    private String node(Map<String,Object> node,int depth) {
        if(depth>=32)throw new IllegalArgumentException("UNSUPPORTED:JASON_QUERY_DEPTH");
        if(!(node.get("op") instanceof String op))throw new IllegalArgumentException("UNSUPPORTED:JASON_QUERY_SHAPE");
        return switch(op) {
            case "TRUE" -> "true";case "FALSE" -> "false";
            case "LITERAL" -> {if(!(node.get("key") instanceof String key))throw new IllegalArgumentException("UNSUPPORTED:JASON_LITERAL_KEY");yield "self.storedBeliefs->exists(b | not b.rule and b.supported and b.literalKey = '"+StringUtil.escapeString(key,'\'')+"')";}
            case "AND","OR","NOT" -> {
                if(!(node.get("children") instanceof List<?> children) || children.size()!=(op.equals("NOT")?1:2))throw new IllegalArgumentException("UNSUPPORTED:JASON_QUERY_ARITY");
                String first=node(CanonicalJson.object(children.getFirst()),depth+1);yield op.equals("NOT")?"not ("+first+")":"("+first+") "+op.toLowerCase(Locale.ROOT)+" ("+node(CanonicalJson.object(children.get(1)),depth+1)+")";
            }
            case "UNSUPPORTED" -> throw new IllegalArgumentException("UNSUPPORTED:"+Objects.toString(node.get("reason"),"JASON_QUERY_FRAGMENT"));
            default -> throw new IllegalArgumentException("UNSUPPORTED:JASON_QUERY_OPERATOR:"+op);
        };
    }
}
