package org.tzi.use.uml.sys;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.tzi.use.api.UseModelApi;
import org.tzi.use.api.UseSystemApi;
import org.tzi.use.uml.ocl.expr.ExpressionWithValue;
import org.tzi.use.uml.ocl.value.BooleanValue;
import org.tzi.use.uml.ocl.value.StringValue;

class MSystemStateExtensionTest {
    @Test void storedExtensionPreservesStateObjectsValuesAndLinks() throws Exception {
        var model = new UseModelApi("extension");
        model.createClass("Base", false);
        var child = model.createClass("Child", false);
        model.createGeneralization("Child", "Base");
        model.createClass("Peer", false);
        var originalAttribute = model.createAttribute("Base", "value", "String");
        var originalAssociation = model.createAssociation("Original", "Child", "children", "*", 0,
                "Peer", "peers", "*", 0);
        var api = UseSystemApi.create(model.getModel(), false);
        var system = api.getSystem();var state = system.state();
        var object = api.createObjectEx(child, "child");var peer = api.createObject("Peer", "peer");
        var objectState = object.state(state);
        api.setAttributeValueEx(object, originalAttribute, new StringValue("preserved"));
        var link = api.createLinkEx(originalAssociation, new MObject[]{object, peer});
        var inheritedAttribute = model.createAttribute("Base", "observed", "Boolean");
        var association = model.createAssociation("Discovered", "Child", "observedChildren", "*", 0,
                "Peer", "observedPeers", "*", 0);

        state.initializeStoredModelExtensions();
        assertSame(state, system.state());assertSame(object, state.objectByName("child"));
        assertSame(objectState, object.state(state));
        assertTrue(state.allLinks().stream().anyMatch(existing -> existing == link));
        assertEquals("preserved", ((StringValue)objectState.attributeValue(originalAttribute)).value());
        assertTrue(objectState.attributeValue(inheritedAttribute).isUndefined());
        api.setAttributeValueEx(object, inheritedAttribute, BooleanValue.TRUE);
        var discovered = api.createLinkEx(association, new MObject[]{object, peer});
        state.initializeStoredModelExtensions();
        assertSame(state, system.state());assertSame(objectState, object.state(state));
        assertEquals(BooleanValue.TRUE, objectState.attributeValue(inheritedAttribute));
        assertTrue(state.allLinks().stream().anyMatch(existing -> existing == link));
        assertTrue(state.allLinks().stream().anyMatch(existing -> existing == discovered));
        assertEquals(2, state.numObjects());
    }

    @Test void unsupportedSlotsAndReadOnlyMutationAreRejectedBeforeInitialization() throws Exception {
        var model = new UseModelApi("extension");var cls = model.createClass("Value", false);
        var api = UseSystemApi.create(model.getModel(), false);var state = api.getSystem().state();
        var object = api.createObjectEx(cls, "value");
        var stored = model.createAttribute("Value", "stored", "String");
        var initialized = model.createAttribute("Value", "initialized", "String");
        initialized.setInitExpression(new ExpressionWithValue(new StringValue("initial")));
        assertThrows(IllegalArgumentException.class, state::initializeStoredModelExtensions);
        assertFalse(object.state(state).attributeValueMap().containsKey(stored));
        initialized.setInitExpression(null);
        api.getSystem().setReadOnly(true);
        assertThrows(IllegalStateException.class, state::initializeStoredModelExtensions);
        assertFalse(object.state(state).attributeValueMap().containsKey(stored));
        api.getSystem().setReadOnly(false);state.initializeStoredModelExtensions();
        assertTrue(object.state(state).attributeValue(stored).isUndefined());
        assertSame(state, api.getSystem().state());
    }
}
