package org.tzi.use.plugins.jacamo.semantics.csl;

import java.util.*;

public final class CslSchemaValidator {
    private CslSchemaValidator(){}
    public static void validatePolicy(List<CslSchema.Classifier> classifiers,CslStructuralPolicy policy) {
        var types=new HashMap<String,CslSchema.Classifier>();classifiers.forEach(c->types.put(c.id(),c));
        var parent=new HashMap<String,String>();var ids=new HashSet<String>();
        for(var link:policy.nesting()) {
            unique(ids,link.id());require(types,link.parentContext(),CslSchema.Kind.ORG_CONTEXT);require(types,link.childContext(),CslSchema.Kind.ORG_CONTEXT);
            if(parent.putIfAbsent(link.childContext(),link.parentContext())!=null)throw new IllegalArgumentException("CSL_CONTEXT_DECLARATION_MULTIPLE_OWNERS");
        }
        for(String child:parent.keySet()) {
            var seen=new HashSet<String>();
            for(String at=child;at!=null;at=parent.get(at))if(!seen.add(at))throw new IllegalArgumentException("CSL_CONTEXT_NESTING_CYCLE");
        }
        var owners=new HashMap<String,String>(parent);
        for(var declaration:policy.declarations()) {
            unique(ids,declaration.id());require(types,declaration.contextType(),CslSchema.Kind.ORG_CONTEXT);
            if(!types.containsKey(declaration.memberType()) || types.get(declaration.memberType()).kind()==CslSchema.Kind.ORG_CONTEXT)throw new IllegalArgumentException("CSL_DECLARATION_USE_NESTING_FOR_CONTEXT");
            if(owners.putIfAbsent(declaration.memberType(),declaration.contextType())!=null)throw new IllegalArgumentException("CSL_DECLARATION_MULTIPLE_OWNERS");
        }
        for(var card:policy.cardinalities()){unique(ids,card.id());require(types,card.contextType(),CslSchema.Kind.ORG_CONTEXT);require(types,card.roleType(),CslSchema.Kind.ROLE);}
        var pairs=new HashSet<Set<String>>();
        for(var compatibility:policy.compatibilities()) {
            unique(ids,compatibility.id());require(types,compatibility.firstRole(),CslSchema.Kind.ROLE);require(types,compatibility.secondRole(),CslSchema.Kind.ROLE);
            if(!pairs.add(Set.of(compatibility.firstRole(),compatibility.secondRole())))throw new IllegalArgumentException("CSL_COMPATIBILITY_DUPLICATE_SYMMETRIC_PAIR");
        }
    }
    private static void unique(Set<String> ids,String id){if(!ids.add(id))throw new IllegalArgumentException("CSL_POLICY_ID_DUPLICATE:"+id);}
    private static void require(Map<String,CslSchema.Classifier> types,String id,CslSchema.Kind kind){if(!types.containsKey(id)||types.get(id).kind()!=kind)throw new IllegalArgumentException("CSL_POLICY_CLASSIFIER_KIND_OR_REFERENCE:"+id);}
    public static void compositionMultiplicity(String value) {
        var multiplicity=org.tzi.use.parser.use.USECompiler.compileMultiplicity(value,"CSL composition",new java.io.PrintWriter(java.io.Writer.nullWriter()),new org.tzi.use.uml.mm.ModelFactory());
        if(multiplicity==null || multiplicity.getRanges().size()!=1 || multiplicity.getRanges().getFirst().getUpper()!=1)throw new IllegalArgumentException("CSL_COMPOSITION_WHOLE_UPPER_ONE_REQUIRED");
    }
    public static void multiplicity(String value) {
        var diagnostic=new java.io.StringWriter();
        if(value==null || org.tzi.use.parser.use.USECompiler.compileMultiplicity(value,"CSL schema",new java.io.PrintWriter(diagnostic),new org.tzi.use.uml.mm.ModelFactory())==null)
            throw new IllegalArgumentException("CSL_MULTIPLICITY_INVALID:"+value+":"+diagnostic);
    }
}
