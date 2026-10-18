package org.tzi.use.semantic.engine;

import org.junit.jupiter.api.Test;
import org.tzi.use.api.*;
import org.tzi.use.parser.ocl.OCLCompiler;
import org.tzi.use.uml.ocl.expr.Evaluator;
import org.tzi.use.uml.ocl.value.*;
import org.tzi.use.uml.sys.*;
import org.vnu.sme.goal.dsl.csl.parser.CslCompiler;
import java.io.*;
import static org.junit.jupiter.api.Assertions.*;

class HostCompatibilityTest {
    @Test void originalFrontendUsesTheSameHostModelAndCompilerClasses() throws Exception {
        ClassLoader engine = CslCompiler.class.getClassLoader();
        assertSame(MSystemState.class, engine.loadClass(MSystemState.class.getName()));
        assertSame(OCLCompiler.class, engine.loadClass(OCLCompiler.class.getName()));
        assertSame(org.tzi.use.uml.mm.MModel.class, engine.loadClass("org.tzi.use.uml.mm.MModel"));
        assertEquals("4.9.3", org.antlr.v4.runtime.RuntimeMetaData.VERSION);
        assertNotNull(Class.forName("org.antlr.runtime.Token"));
        assertNotEquals(org.antlr.v4.runtime.Token.class, Class.forName("org.antlr.runtime.Token"));
    }

    @Test void nativeBooleanUndefinedAndAtPreEvaluateOnPinnedStatesOfOneSystem() throws Exception {
        var api = new UseModelApi("Host75");
        api.createClass("Counter", false); api.createAttribute("Counter", "p", "Integer");
        api.createOperation("Counter", "increment", new String[0][0], null);
        var post = api.createPrePostCondition("Counter", "increment", "Incremented", "self.p = self.p@pre + 1", false);
        var system = new MSystem(api.getModel());
        var stateApi = UseSystemApi.create(system, false);
        var object = stateApi.createObject("Counter", "counter");
        var variables = new VarBindings(); variables.push("self", object.value());
        assertTrue(object.state(system.state()).attributeValue("p").isUndefined());
        // USE equality is SPECIAL: undefined = undefined is true. It is not a
        // strict Boolean operator. Coverage must be checked independently.
        assertEquals(BooleanValue.TRUE,
                new Evaluator().eval(post.expression(), system.state(), system.state(), variables));
        var unknownPredicate = api.createPrePostCondition("Counter", "increment", "KnownPositive", "self.p > 0", true);
        assertTrue(new Evaluator().eval(unknownPredicate.expression(), system.state(), system.state(), variables).isUndefined());
        stateApi.setAttributeValue("counter", "p", "0");
        var before = new MSystemState("before", system.state());
        stateApi.setAttributeValue("counter", "p", "1");
        var after = new MSystemState("after", system.state());
        assertSame(system, before.system()); assertSame(system, after.system());
        assertEquals(BooleanValue.TRUE, new Evaluator().eval(post.expression(), before, after, variables));
        assertEquals(BooleanValue.FALSE, new Evaluator().eval(post.expression(), after, after, variables));
        var diagnostic = new StringWriter();
        var bool = OCLCompiler.compileExpression(api.getModel(), "Counter.allInstances()->size() = 1", "host-linkage",
                new PrintWriter(diagnostic), new VarBindings());
        assertNotNull(bool, diagnostic.toString());
        assertEquals(BooleanValue.TRUE, new Evaluator().eval(bool, after, after, new VarBindings()));
    }
}
