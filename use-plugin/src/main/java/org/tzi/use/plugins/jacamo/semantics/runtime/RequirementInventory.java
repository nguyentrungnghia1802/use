package org.tzi.use.plugins.jacamo.semantics.runtime;

import java.util.*;
import org.tzi.use.plugins.jacamo.semantics.mapping.SemanticProgram;
import org.tzi.use.semantic.engine.use75.Use75CslModelBackend;
import org.tzi.use.uml.mm.MModel;

/** Native identities of requirements, independent of a case or context name.
 * Only backend-created helper invariants can be supporting checks. Authored
 * clauses on those same helpers remain mandatory. No runtime availability here. */
public final class RequirementInventory {
    private RequirementInventory() { }
    public static Map<String,Object> from(SemanticProgram program,MModel model) {
        var explicit=new TreeSet<String>();
        program.cslModel().invariants().forEach(i->explicit.add("NATIVE:"+i.contextType()+"::"+i.name()));
        if(program.xmlPlan()!=null) {
            var source=program.xmlPlan().inputs().ocl();
            var specification=org.tzi.use.plugins.jacamo.semantics.mapping.ocl.ModelNativeContracts.read(program.xmlPlan().inputs());
            specification.clauses().stream().filter(c->c.kind()==org.tzi.use.plugins.jacamo.semantics.mapping.ocl.AuthoredOclSpecificationLoader.Kind.INVARIANT)
                .forEach(c->explicit.add("NATIVE:"+c.context().owner()+"::"+c.name()));
        }
        var required=new TreeSet<>(explicit);var supporting=new TreeSet<String>();
        for(var invariant:model.classInvariants()) {
            String id="NATIVE:"+invariant.qualifiedName();
            if(explicit.contains(id)||invariant.getAnnotation("AuthoredOclSource")!=null
                    ||invariant.cls().getAnnotation(Use75CslModelBackend.DECLARATION)!=null)required.add(id);
            else supporting.add(id);
        }
        var targets=program.xmlPlan()==null?List.<String>of():program.xmlPlan().inputs().specification().root().child("bindings").children("requirement").stream().map(n->n.attr("target")).sorted().toList();
        return Map.of("version","NATIVE_CONTRACT_INVENTORY/1.0","requiredInvariants",List.copyOf(required),"supportingInvariants",List.copyOf(supporting),"selectedIntentionalElements",targets);
    }
}
