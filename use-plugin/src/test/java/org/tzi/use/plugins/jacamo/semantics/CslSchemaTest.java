package org.tzi.use.plugins.jacamo.semantics;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.tzi.use.plugins.jacamo.semantics.csl.*;
import org.tzi.use.plugins.jacamo.semantics.istar.RequirementModel;
import org.tzi.use.plugins.jacamo.semantics.profile.*;
import org.tzi.use.plugins.jacamo.semantics.ocl.*;
import org.tzi.use.plugins.jacamo.semantics.evidence.SemanticVerdict;
import org.tzi.use.api.*;
import org.tzi.use.uml.sys.*;
import org.tzi.use.uml.ocl.value.*;

class CslSchemaTest {
    private CslSchema.Classifier type(String id, CslSchema.Kind kind, String parent) {
        return new CslSchema.Classifier(id, "source:" + id, id, kind, parent, false, List.of());
    }
    @Test void strictScalarBindingDoesNotWidenOrTruncateObservations() {
        assertEquals("Boolean",ScalarValueCodec.decode("Boolean",List.of("true"),List.of("java.lang.Boolean")).type().toString());
        assertEquals("String",ScalarValueCodec.decode("String",List.of("world"),List.of("java.lang.String")).type().toString());
        assertEquals("Real",ScalarValueCodec.decode("Real",List.of("12.5"),List.of("java.lang.Double")).type().toString());
        assertThrows(IllegalArgumentException.class,()->ScalarValueCodec.decode("Real",List.of("NaN"),List.of("java.lang.Double")));
        assertThrows(IllegalArgumentException.class,()->ScalarValueCodec.decode("String",List.of("null"),List.of("null")));
        assertEquals("Integer", ScalarValueCodec.decode("Integer", List.of("12"), List.of("java.lang.Integer")).type().toString());
        assertThrows(IllegalArgumentException.class, () -> ScalarValueCodec.decode("Integer", List.of("12.5"), List.of("java.lang.Double")));
        assertThrows(IllegalArgumentException.class, () -> ScalarValueCodec.decode("Integer", List.of("1","2"), List.of("java.lang.Integer","java.lang.Integer")));
        assertThrows(IllegalArgumentException.class, () -> ScalarValueCodec.decode("String", List.of("world"), List.of("arbitrary.JavaObject")));
    }
    @Test void cslDoesNotAcceptRuntimeEnactmentAsGenericRoleRoleDomainAssociation() {
        var a = new CslSchema.Association("a", "A", List.of(new CslSchema.End("R","from","*"), new CslSchema.End("S","to","*")), 0);
        assertThrows(IllegalArgumentException.class, () -> new CslSchema("test", List.of(
                type("R",CslSchema.Kind.ROLE,""), type("S",CslSchema.Kind.ROLE,"")), List.of(a), List.of(), List.of()));
        assertThrows(IllegalArgumentException.class, () -> new CslSchema("cycle", List.of(
                type("R",CslSchema.Kind.ROLE,"S"), type("S",CslSchema.Kind.ROLE,"R")), List.of(), List.of(), List.of()));
    }
    @Test void typedConfigurationRoundTripAndUnknownFieldsFailClosed() {
        var spec = new CslSchema("example", List.of(type("X",CslSchema.Kind.ENTITY,"")), List.of(), List.of(), List.of());
        var config = new CslMonitorConfiguration("1.0", SemanticProjectionProfile.CSL_OBSERVER_V1, spec, new RequirementModel(List.of()), List.of());
        assertEquals(config, CslMonitorConfiguration.parse(config.bytes()));
        assertFalse(org.jacamo.bridge.contract.CanonicalJson.object(org.jacamo.bridge.contract.CanonicalJson.object(org.jacamo.bridge.contract.CanonicalJson.decode(config.bytes())).get("schema")).containsKey("policy"),"Empty extension must preserve v1 configuration hash encoding");
        String malformed = new String(config.bytes(), java.nio.charset.StandardCharsets.UTF_8).replace("\"version\":\"1.0\"", "\"version\":\"1.0\",\"unknown\":true");
        assertThrows(IllegalArgumentException.class, () -> CslMonitorConfiguration.parse(malformed.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }
    @Test void hostCompilerTwoStatesAndUndefinedRemainDistinct() throws Exception {
        var api = new UseModelApi("TypedCondition");
        api.createClass("X",false); api.createAttribute("X","p","Integer");
        var op = api.createOperation("X","bump",new String[][]{{"amount","Integer"}},null);
        var post = SemanticOclCompiler.condition(api,op,"Increment","self.p = self.p@pre + amount",false);
        var system = new MSystem(api.getModel()); var state = UseSystemApi.create(system,false);
        var object = state.createObject("X","x"); state.setAttributeValue("x","p","0");
        var before = new MSystemState("before",system.state());
        state.setAttributeValue("x","p","1"); var bindings = new VarBindings(); bindings.push("self",object.value()); bindings.push("amount",IntegerValue.valueOf(1));
        var evaluator = new OclEvaluationAdapter();
        assertEquals(SemanticVerdict.PASS,evaluator.evaluate(post.expression(),before,system.state(),bindings).verdict());
        state.setAttributeValue("x","p","2");
        assertEquals(SemanticVerdict.FAIL,evaluator.evaluate(post.expression(),before,system.state(),bindings).verdict());
        state.setAttributeValueEx(object,object.cls().attribute("p",true),UndefinedValue.instance);
        var predicate = SemanticOclCompiler.predicate(api.getModel(),object.cls(),"Defined","self.p >= 0");
        assertEquals(SemanticVerdict.UNDEFINED,evaluator.evaluate(predicate,before,system.state(),bindings).verdict());
        assertEquals("0",object.state(before).attributeValue("p").toString());
        assertThrows(IllegalArgumentException.class, () -> SemanticOclCompiler.predicate(api.getModel(),object.cls(),"BadPre","self.p@pre >= 0"));
    }
    @org.junit.jupiter.api.Test void compositionWholeMultiplicityFollowsCoreV4() {
        assertThrows(IllegalArgumentException.class,()->new CslSchema.Association("bad","Bad",List.of(new CslSchema.End("a","whole","*"),new CslSchema.End("b","parts","*")),2));
    }

}
