package org.tzi.use.plugins.jacamo.codegrounded;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.tzi.use.main.Session;
import org.tzi.use.plugins.jacamo.codegrounded.model.JacamoSpecificationModel;
import org.tzi.use.plugins.jacamo.codegrounded.trace.CodeGroundedTraceCollector;
import org.tzi.use.plugins.jacamo.codegrounded.use.*;
import org.tzi.use.plugins.jacamo.semantics.binding.UseProjectionBinding;
import org.tzi.use.semantic.engine.api.EngineAdmissionException;
import org.tzi.use.semantic.engine.frontend.CslFrontend;
import org.tzi.use.semantic.engine.use75.*;
import static org.junit.jupiter.api.Assertions.*;

class CanonicalCslNativePipelineTest {
    @Test void originalCanonicalModelUsesExistingStateBuilderExporterAndSessionActivation() throws Exception {
        var csl = new CslFrontend().compile("csl v4.0 Canonical { datatype Money; entity Counter { p : Integer mutable default 7; } orgContext Team { role Worker; } context Counter inv Nonnegative: self.p >= 0; }", "canonical.csl").requireModel();
        var binding = new Use75CslModelBackend().compile(csl, M0ExtensionSpec.observedPlayers());
        var source = new JacamoSpecificationModel(CodeGroundedTestFixtures.helloSnapshot().semanticContract());
        var trace = new CodeGroundedTraceCollector();
        var schema = new NativeUseModelBuilder().buildCsl(binding,trace,NativeProjectionMode.AUTO);
        var state = new NativeUseStateBuilder().build(source,schema,trace);
        var export = new NativeUseExporter().export(schema.model());
        assertSame(binding.model(),schema.model()); assertSame(binding.model(),state.system().model());
        assertTrue(state.system().state().allObjects().isEmpty());
        assertTrue(state.semanticObjectIndex().isEmpty());
        assertEquals(export.originalStructuralHash(),export.recompiledStructuralHash());
        var inventory = UseProjectionBinding.of(schema.model()).inventory();
        assertEquals("USE4GOAL_OBSERVED_V1",inventory.profileId());
        assertEquals("ENTITY",inventory.types().stream().filter(t->t.symbol().equals("Counter")).findFirst().orElseThrow().semanticKind());
        assertEquals(1,inventory.forSource("csl:Counter").size());
        var result = new CodeGroundedNativePipeline.Result(source,schema,state,export,trace.index(),schema.profile());
        var session = new Session(); new NativeUseSessionActivator().activate(session,result);
        assertSame(state.system(),session.system());
        assertThrows(EngineAdmissionException.class,()->new Use75CslModelBackend().compile(new CslFrontend().compile(
                "csl v4.0 Invalid { entity Counter; context Counter inv Bad: self.missing = 1; }","bad.csl").requireModel()));
        assertSame(state.system(),session.system(),"A rejected pending model cannot replace the active host system");
    }
    @Test void liveAdmissionRejectsMissingTypeCodecAndUnverifiedNaryWriter() {
        for (String text : List.of("csl v4.0 Opaque { datatype Money; entity E { price : Money optional; } }",
                "csl v4.0 Nary { entity A; entity B; entity C; association N { A [*]; B [*]; C [*]; } }")) {
            var binding = new Use75CslModelBackend().compile(new CslFrontend().compile(text,"gate.csl").requireModel(),M0ExtensionSpec.observedPlayers());
            var error = assertThrows(EngineAdmissionException.class,()->new NativeUseModelBuilder().buildCsl(binding,new CodeGroundedTraceCollector(),NativeProjectionMode.AUTO));
            assertTrue(error.diagnostics().stream().allMatch(d->d.kind()==org.tzi.use.semantic.engine.api.EngineDiagnostic.Kind.UNSUPPORTED));
        }
    }
}
