package org.tzi.use.plugins.jacamo.semantics.csl;

import java.util.*;
import org.tzi.use.api.*;
import org.tzi.use.uml.mm.*;
import org.tzi.use.plugins.jacamo.codegrounded.use.DomainProjection;

public final class CslStructuralPolicyCompiler {
    private CslStructuralPolicyCompiler(){}
    public static void install(UseModelApi api,CslSchema schema) throws UseApiException {
        if(!schema.policy().nesting().isEmpty()) {
        api.createAssociation("ContextNesting","OrgContext","parentContext","0..1",0,"OrgContext","childContexts","*",0);
        api.getModel().getAssociation("ContextNesting").addAnnotation(new MElementAnnotation("CslAssociation",Map.of("kind","RUNTIME_CONTEXT")));
        }
        for(var declaration:schema.policy().declarations())api.getModel().getClass(schema.classifier(declaration.memberType()).symbol()).addAnnotation(new MElementAnnotation("CslDeclarationOwner",Map.of("contextId64",DomainProjection.encode(declaration.contextType()),"scope","M1_NAMESPACE_ONLY","runtimeOwnership","UNSUPPORTED_UNTIL_EXPLICIT_POLICY")));
        for(var nesting:schema.policy().nesting()) {
            String size="self.childContexts->select(c | c.oclIsKindOf("+schema.classifier(nesting.childContext()).symbol()+"))->size()";
            var inv=api.createInvariant("CslPolicy_"+DomainProjection.hash(nesting.id()),schema.classifier(nesting.parentContext()).symbol(),size+" >= "+nesting.minimum()+(nesting.maximum()<0?"":" and "+size+" <= "+nesting.maximum()),false);
            inv.addAnnotation(new MElementAnnotation("CslConstraint",Map.of("id64",DomainProjection.encode(nesting.id()),"authority","AUTHORED_CSL_POLICY","coverage","COMPLETE_CONTEXT_POPULATION_REQUIRED")));
        }
        for(var card:schema.policy().cardinalities()) {
            String role=schema.classifier(card.roleType()).symbol();String size="self.roles->select(r | r.oclIsKindOf("+role+"))->size()";
            invariant(api,schema.classifier(card.contextType()).symbol(),card.id(),size+" >= "+card.minimum()+(card.maximum()<0?"":" and "+size+" <= "+card.maximum()));
        }
        if(schema.policy().enforceCompatibility()) {
            var roles=schema.classifiers().stream().filter(c->c.kind()==CslSchema.Kind.ROLE).toList();
            var permissions=new ArrayList<String>();
            for(var first:roles)for(var second:roles) {
                var a=api.getModel().getClass(first.symbol());var b=api.getModel().getClass(second.symbol());
                boolean related=a.isSubClassifierOf(b)||b.isSubClassifierOf(a);
                if(related)permissions.add(pair(first.symbol(),second.symbol()));
            }
            for(var permission:schema.policy().compatibilities()) {
                String a=schema.classifier(permission.firstRole()).symbol(),b=schema.classifier(permission.secondRole()).symbol();
                permissions.add(pair(a,b));permissions.add(pair(b,a));
            }
            String allowed=permissions.isEmpty()?"false":String.join(" or ",permissions);
            for(var context:schema.classifiers())if(context.kind()==CslSchema.Kind.ORG_CONTEXT)
                invariant(api,context.symbol(),"compatibility:"+context.id(),"self.roles->forAll(r1,r2 | r1.player <> r2.player or "+allowed+")");
        }
    }
    private static String pair(String first,String second){return "(r1.oclIsKindOf("+first+") and r2.oclIsKindOf("+second+"))";}
    private static void invariant(UseModelApi api,String owner,String id,String body)throws UseApiException {
        var inv=api.createInvariant("CslPolicy_"+DomainProjection.hash(id),owner,body,false);
        inv.addAnnotation(new MElementAnnotation("CslConstraint",Map.of("id64",DomainProjection.encode(id),"scopedContext","true","authority","AUTHORED_CSL_POLICY")));
    }
}
