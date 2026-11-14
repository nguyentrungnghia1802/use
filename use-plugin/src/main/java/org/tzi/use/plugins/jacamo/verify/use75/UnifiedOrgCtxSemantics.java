package org.tzi.use.plugins.jacamo.verify.use75;

import java.io.*;
import org.tzi.use.api.*;
import org.tzi.use.parser.Symtable;
import org.tzi.use.parser.ocl.OCLCompiler;

/** CSL_ORGCTX_UNIFIED_V1: membership and enactment are independent relations over the same subjects. */
public final class UnifiedOrgCtxSemantics {
    private UnifiedOrgCtxSemantics() { }
    public static void prepare(UseModelApi api,Use75ModelBinding binding)throws UseApiException {
        var reserved=java.util.Set.of("members","memberOf","rolePlayers","roleContexts","participants","roles","enactedRoles");
        for(var declaration:binding.classifiers().values())if(declaration.kind()==Use75ModelBinding.Kind.ORG_CONTEXT)
            for(var attribute:binding.classifier(declaration.declaration()).allAttributes())if(reserved.contains(attribute.name()))throw new IllegalArgumentException("CSL_UNIFIED_RESERVED_MEMBER:"+attribute.name());
        for(var role:binding.classifiers().values())if(role.kind()==Use75ModelBinding.Kind.ROLE&&reserved.contains(role.declaration()))throw new IllegalArgumentException("CSL_UNIFIED_RESERVED_ROLE_NAVIGATION:"+role.declaration());
        api.createAssociation("CslMembership","OrgContext","members","*",0,"OrgContext","memberOf","*",0);
        derived(api,binding,"OrgContext","rolePlayers","Set(OrgContext)","self.roles->collect(r | r.player)->asSet()");
        derived(api,binding,"OrgContext","roleContexts","Set(OrgContext)","self.enactedRoles->collect(r | r.orgContext)->asSet()");
        derived(api,binding,"OrgContext","participants","Set(OrgContext)","self.members->union(self.rolePlayers)");
        api.createInvariant("NoSelfMembership","OrgContext","not self.memberOf->includes(self)",false);
        for(var declaration:binding.classifiers().values()) {
            if(declaration.kind()!=Use75ModelBinding.Kind.ROLE)continue;
            String owner=binding.declarationOwners().get(declaration.declaration());if(owner==null)continue;
            derived(api,binding,owner,declaration.declaration(),"Set("+declaration.symbol()+")",
                    "self.roles->select(r | r.oclIsKindOf("+declaration.symbol()+"))->collect(r | r.oclAsType("+declaration.symbol()+"))->asSet()");
        }
    }
    private static void derived(UseModelApi api,Use75ModelBinding binding,String owner,String name,String type,String body)throws UseApiException {
        var attribute=api.createAttribute(owner,name,type);var variables=new Symtable();var diagnostics=new StringWriter();
        try{variables.add("self",binding.model().getClass(owner),null);}
        catch(org.tzi.use.parser.SemanticException error){throw new IllegalArgumentException(error);}
        var expression=OCLCompiler.compileExpression(binding.model(),body,"unified-context:"+owner+"."+name,new PrintWriter(diagnostics,true),variables);
        if(expression==null||!expression.type().equals(attribute.type()))throw new IllegalArgumentException("UNIFIED_CONTEXT_QUERY_INVALID:"+diagnostics);
        attribute.setDeriveExpression(expression);
    }
}
