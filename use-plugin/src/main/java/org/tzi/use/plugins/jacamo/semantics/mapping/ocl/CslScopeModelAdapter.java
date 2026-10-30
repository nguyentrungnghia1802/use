package org.tzi.use.plugins.jacamo.semantics.mapping.ocl;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Map;
import org.tzi.use.api.*;
import org.tzi.use.parser.SemanticException;
import org.tzi.use.parser.Symtable;
import org.tzi.use.parser.ocl.OCLCompiler;
import org.tzi.use.semantic.engine.use75.*;
import org.tzi.use.uml.mm.MElementAnnotation;
import org.vnu.sme.goal.dsl.csl.mm.CslModel;

/** Native declaration-scope preparation; canonical CSL remains unchanged. All names come from M1 declarations. */
public class CslScopeModelAdapter {
    public static final String CONTEXT_SCOPES = "CslContextScopes";
    public static final String ENTITY_SCOPES = "CslEntityScopes";
    public static final String AGENT_CONTEXTS = "CslAgentContexts";

    public Use75ModelBinding compile(CslModel source) {
        return new Use75CslModelBackend().compile(source, M0ExtensionSpec.observedPlayers(), this::prepare);
    }
    public Use75ModelBinding compile(CslModel source,ObserverSchema observers) {
        return new Use75CslModelBackend().compile(source,M0ExtensionSpec.observedPlayers(),(api,binding)->{prepare(api,binding);observers.install(api,binding);});
    }
    public void prepare(UseModelApi api, Use75ModelBinding binding) throws UseApiException {
        if (api.getModel() != binding.model() || !binding.extension().players()) throw new IllegalArgumentException("CSL_SCOPE_PREPARATION_MODEL_MISMATCH");
        // Declaration scope is observed membership, never physical ownership or disposal cascade.
        api.createAssociation(CONTEXT_SCOPES, "OrgContext", "cslScopeParent", "0..1", 0, "OrgContext", "cslNestedContexts", "*", 0);
        api.createAssociation(ENTITY_SCOPES, "OrgContext", "cslEntityScope", "0..1", 0, "DomainEntity", "cslScopedEntities", "*", 0);
        api.createAssociation(AGENT_CONTEXTS, "OrgContext", "singletonContexts", "*", 0, "AtomicAgent", "representedAgent", "0..1", 0);
        api.getModel().getAssociation(AGENT_CONTEXTS).addAnnotation(new MElementAnnotation(Use75CslModelBackend.HELPER,
                Map.of("kind", "SINGLE_AGENT_CONTEXT_REPRESENTATION", "createsAgent", "false")));
        for (String association : new String[]{CONTEXT_SCOPES, ENTITY_SCOPES})
            api.getModel().getAssociation(association).addAnnotation(new MElementAnnotation(Use75CslModelBackend.HELPER,
                    Map.of("kind", "DECLARATION_SCOPE_ONLY", "physicalOwnership", "false")));
        for (var declaration : binding.classifiers().values()) {
            if (declaration.kind() != Use75ModelBinding.Kind.ROLE) continue;
            String owner = binding.declarationOwners().get(declaration.declaration());
            if (owner == null) continue;
            var context = binding.classifier(owner);
            if (binding.classifiers().get(owner).kind() != Use75ModelBinding.Kind.ORG_CONTEXT) throw new IllegalArgumentException("CSL_ROLE_OWNER_CONTEXT_REQUIRED");
            var attribute = api.createAttribute(owner, declaration.declaration(), "Set(" + declaration.symbol() + ")");
            String body = "self.roles->select(r | r.oclIsKindOf(" + declaration.symbol() + "))->collect(r | r.oclAsType(" + declaration.symbol() + "))->asSet()";
            var diagnostics = new StringWriter();
            try {
                var variables = new Symtable(); variables.add("self", context, null);
                var expression = OCLCompiler.compileExpression(binding.model(), body, "declared-role:" + owner + "::" + declaration.declaration(), new PrintWriter(diagnostics,true), variables);
                if (expression == null || !expression.type().equals(attribute.type())) throw new IllegalArgumentException("CSL_ROLE_NAVIGATION_TYPE_OR_COMPILATION_ERROR:" + diagnostics);
                attribute.setDeriveExpression(expression);
                attribute.addAnnotation(new MElementAnnotation(Use75CslModelBackend.HELPER, Map.of("kind", "DECLARED_ROLE_NAVIGATION", "id64", Use75CslModelBackend.encode(declaration.declaration()), "owner64", Use75CslModelBackend.encode(owner))));
            } catch (SemanticException error) { throw new IllegalArgumentException("CSL_ROLE_NAVIGATION_BINDING_ERROR", error); }
        }
    }
}
