package org.tzi.use.plugins.jacamo.semantics.xml;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.tzi.use.plugins.jacamo.semantics.mapping.*;
import org.tzi.use.semantic.engine.frontend.CslFrontend;
import org.vnu.sme.goal.dsl.istar.mm.GoalModel;
import static org.junit.jupiter.api.Assertions.*;

class InheritedBindingTest {
    private static final String MODEL="""
        csl v4.0 Inheritance {
          role Capability { attribute knowledge : String optional mutable; }
          role Intermediate specializes Capability;
          role Performer specializes Intermediate;
          entity Base { attribute amount : Integer optional mutable; }
          entity Concrete specializes Base;
          orgContext Context { attribute label : String optional mutable; }
        }
        """;
    private static SemanticBindingIndex index(String target) {
        return new SemanticBindingIndex(List.of(new SemanticBindingIndex.Binding("source",SemanticBindingIndex.Kind.ATTRIBUTE,target,"","rule","Explicit binding",Map.of())));
    }
    private static NormalizedSourceModel source() {
        return new NormalizedSourceModel("test","hash","metamodel",List.of(new NormalizedSourceModel.Node("source","Artifact",NormalizedSourceModel.Layer.MODEL_DEFINITION,"TYPE","","",Map.of())),List.of());
    }
    @Test void resolvesInheritedAttributesWithoutDuplicatingDeclarations() {
        var model=new CslFrontend().compile(MODEL,"inheritance.csl").requireModel();
        for(String target:List.of("Performer::knowledge","Intermediate::knowledge","Concrete::amount","Context::label"))
            assertDoesNotThrow(()->index(target).validate(source(),model,new GoalModel("Empty"),List.of()),target);
        assertTrue(model.findRole("Performer").orElseThrow().attributes().isEmpty());
    }
    @Test void missingOrUnrelatedAttributeStillFailsClosed() {
        var model=new CslFrontend().compile(MODEL,"inheritance.csl").requireModel();
        for(String target:List.of("Performer::amount","Concrete::knowledge","Child::missing","Missing::label","Performer"))
            assertThrows(IllegalArgumentException.class,()->index(target).validate(source(),model,new GoalModel("Empty"),List.of()),target);
    }
}
