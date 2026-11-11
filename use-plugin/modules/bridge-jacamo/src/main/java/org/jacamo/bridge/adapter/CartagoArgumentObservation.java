package org.jacamo.bridge.adapter;

import java.util.*;
import cartago.*;

/** Bounded copies of actual operation arguments/output handles; not a domain mapper. */
final class CartagoArgumentObservation {
    static final String VERSION="1.0.0";
    private CartagoArgumentObservation(){ }
    static Map<String,Object> value(Object value,String environment) {
        return value(value,Objects.requireNonNull(environment),0,new IdentityHashMap<>());
    }
    private static Map<String,Object> value(Object value,String environment,int depth,IdentityHashMap<Object,Boolean> path) {
        String javaType=value==null?"null":value.getClass().getName();
        if(value==null)return Map.of("kind","NULL","javaType",javaType);
        if(depth>=8||path.put(value,true)!=null)return unsupported(javaType,"VALUE_DEPTH_OR_CYCLE");
        try {
            var out=new LinkedHashMap<String,Object>();out.put("javaType",javaType);
            if(value instanceof String string) {
                if(string.length()>16384)return unsupported(javaType,"VALUE_STRING_CAPACITY");out.put("kind","STRING");out.put("value",string);
            } else if(value instanceof Boolean truth) {out.put("kind","BOOLEAN");out.put("value",truth);}
            else if(value instanceof Double number) {
                if(!Double.isFinite(number))return unsupported(javaType,"NONFINITE_BINARY64");
                out.put("kind","NUMBER");out.put("encoding","IEEE754_BINARY64");out.put("value",Double.toString(number));
                out.put("rawBits",String.format(Locale.ROOT,"%016x",Double.doubleToRawLongBits(number)));
            } else if(value instanceof Float number) {
                if(!Float.isFinite(number))return unsupported(javaType,"NONFINITE_BINARY32");
                out.put("kind","NUMBER");out.put("encoding","IEEE754_BINARY32");out.put("value",Float.toString(number));
                out.put("rawBits",String.format(Locale.ROOT,"%08x",Float.floatToRawIntBits(number)));
            } else if(value instanceof Integer||value instanceof Long||value instanceof Short||value instanceof Byte) {
                out.put("kind","NUMBER");out.put("encoding","JAVA_INTEGER");out.put("value",value.toString());
            } else if(value.getClass()==ArtifactId.class) {
                var artifact=(ArtifactId)value;var workspace=artifact.getWorkspaceId();
                out.put("kind","ARTIFACT_ID");out.put("semanticId",OfficialCartagoAdapter.artifactId(environment,artifact));
                out.put("uuid",artifact.getId().toString());out.put("name",artifact.getName());out.put("typeHint",artifact.getArtifactType());
                out.put("workspaceUuid",workspace.getUUID().toString());out.put("workspaceName",workspace.getFullName());
            } else if(value.getClass()==OpFeedbackParam.class) {
                Object actual=((OpFeedbackParam<?>)value).get();out.put("kind","FEEDBACK");out.put("available",actual!=null);
                if(actual!=null)out.put("value",value(actual,environment,depth+1,path));
            } else if(value instanceof Object[] array) {
                if(array.length>128)return unsupported(javaType,"VALUE_ARRAY_CAPACITY");
                var items=new ArrayList<Map<String,Object>>();for(Object item:array)items.add(value(item,environment,depth+1,path));
                out.put("kind","ARRAY");out.put("values",List.copyOf(items));
            } else if(value instanceof jason.asSyntax.Term term) {
                out.put("kind","JASON_TERM");out.put("term",JasonStructuredTerms.term(term));
            } else return unsupported(javaType,"VALUE_KIND_UNSUPPORTED");
            return JasonStructuredTerms.immutable(out);
        } finally {path.remove(value);}
    }
    private static Map<String,Object> unsupported(String type,String reason){return Map.of("kind","UNSUPPORTED","javaType",type,"diagnostic",reason);}
}
