package org.tzi.use.plugins.jacamo.ui.use4goal.csl;

import java.util.*;
import org.tzi.use.uml.mm.*;
import org.vnu.sme.goal.dsl.csl.mm.CslModel;

/** View adapter: borrow exact installed classifiers; never build a shadow MModel/MSystem. */
final class CslUseViewModel {
    final MModel model;
    final Map<String,MClass> classes;
    final Map<String,MAssociation> associations;
    private CslUseViewModel(MModel model,Map<String,MClass> classes,Map<String,MAssociation> associations) {
        this.model=model;this.classes=Map.copyOf(classes);this.associations=Map.copyOf(associations);
    }
    static CslUseViewModel build(CslModel csl,MModel installed) {
        Objects.requireNonNull(installed,"INSTALLED_NATIVE_MODEL_REQUIRED");
        var classes=new LinkedHashMap<String,MClass>();var names=new ArrayList<String>();
        csl.entities().forEach(v->names.add(v.name()));csl.roles().forEach(v->names.add(v.name()));csl.groups().forEach(v->names.add(v.name()));
        for(String name:names) {var type=installed.getClass(name);if(type==null)throw new IllegalArgumentException("CANONICAL_VIEW_CLASS_UNRESOLVED:"+name);classes.put(name,type);}
        var associations=new LinkedHashMap<String,MAssociation>();installed.associations().forEach(a->associations.put(a.name(),a));
        return new CslUseViewModel(installed,classes,associations);
    }
}
