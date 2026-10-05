package org.tzi.use.plugins.jacamo.codegrounded;

import static org.junit.jupiter.api.Assertions.*;

import java.io.PrintWriter;
import java.io.StringWriter;
import org.junit.jupiter.api.Test;
import org.tzi.use.api.UseModelApi;
import org.tzi.use.api.UseSystemApi;
import org.tzi.use.plugins.jacamo.codegrounded.use.NativeUseExporter;
import org.tzi.use.uml.mm.MAssociationClass;
import org.tzi.use.uml.sys.MLinkObject;

/** Executable native API gate, independent of the JaCaMo projection implementation. */
class NativeRoleAssociationSupportTest {
    @Test void nativeAssociationClassKeepsBothClassAndAssociationSemantics() throws Exception {
        var model = new UseModelApi("NativeRoleSupport");
        model.createClass("Agent", false);
        model.createClass("Group", false);
        var role = model.createAssociationClass("member", false,
                "Agent", "players", "1", 0, "Group", "groups", "0..*", 0);
        model.createAttribute(role.name(), "semanticId", "String");
        role.addAnnotation(new org.tzi.use.uml.mm.MElementAnnotation("ExactContext", java.util.Map.of("id64", "ZXhhY3Q")));
        assertSame(role, model.getModel().getClass("member"));
        assertSame(role, model.getModel().getAssociation("member"));
        var state = UseSystemApi.create(model.getModel(), false);
        state.createObjects("Agent", "a");
        state.createObjects("Group", "g1", "g2");
        MLinkObject first = state.createLinkObject("member", "r1", new String[]{"a", "g1"});
        MLinkObject second = state.createLinkObject("member", "r2", new String[]{"a", "g2"});
        assertSame(first, state.getSystem().state().objectByName("r1"));
        assertTrue(state.getSystem().state().allLinks().contains(first));
        assertTrue(state.getSystem().state().allLinks().contains(second));
        assertTrue(state.getSystem().state().checkStructure(new PrintWriter(new StringWriter())));
        var exported = new NativeUseExporter().export(model.getModel());
        assertInstanceOf(MAssociationClass.class, exported.recompiledModel().getClass("member"));
        assertSame(exported.recompiledModel().getClass("member"), exported.recompiledModel().getAssociation("member"));
        assertEquals("ZXhhY3Q", exported.recompiledModel().getClass("member").getAnnotationValue("ExactContext", "id64"));
        assertEquals(exported.originalStructuralHash(), exported.recompiledStructuralHash());
        var soil = new org.tzi.use.plugins.jacamo.codegrounded.use.NativeUseSoilExporter();
        var replay = soil.replay(exported.recompiledModel(), soil.export(state.getSystem()).commands());
        assertInstanceOf(MLinkObject.class, replay.state().objectByName("r1"));
        assertEquals(5, replay.state().numObjects()); assertEquals(2, replay.state().allLinks().size());
    }
}
