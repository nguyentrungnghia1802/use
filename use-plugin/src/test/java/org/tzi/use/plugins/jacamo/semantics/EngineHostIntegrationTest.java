package org.tzi.use.plugins.jacamo.semantics;

import org.junit.jupiter.api.Test;
import org.tzi.use.runtime.util.PluginClassLoader;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.sys.MSystemState;
import org.tzi.use.uml.ocl.expr.Expression;
import org.vnu.sme.goal.dsl.csl.parser.CslCompiler;
import static org.junit.jupiter.api.Assertions.*;

class EngineHostIntegrationTest {
    @Test void actualUsePluginLoaderKeepsHostClassIdentityAndLoadsOneOriginalEngine() throws Exception {
        var loader = new PluginClassLoader(CslCompiler.class.getProtectionDomain().getCodeSource().getLocation());
        assertSame(MModel.class, loader.loadClass(MModel.class.getName()));
        assertSame(MSystemState.class, loader.loadClass(MSystemState.class.getName()));
        assertSame(Expression.class, loader.loadClass(Expression.class.getName()));
        assertSame(CslCompiler.class, loader.loadClass(CslCompiler.class.getName()));
        assertTrue(CslCompiler.compile("csl v4.0 Plugin { role Worker; }").ok());
        assertNull(MModel.class.getResource("/org/vnu/sme/goal/GoalPlugin.class"));
    }
}
