package org.tzi.use.plugins.jacamo.codegrounded;

import org.jacamo.bridge.contract.ModelSnapshot;
import org.tzi.use.plugins.jacamo.codegrounded.model.JacamoSpecificationModel;
import org.tzi.use.plugins.jacamo.codegrounded.trace.CodeGroundedTraceCollector;
import org.tzi.use.plugins.jacamo.codegrounded.trace.CodeGroundedTraceIndex;
import org.tzi.use.plugins.jacamo.codegrounded.trace.CodeGroundedTraceRecord;
import org.tzi.use.plugins.jacamo.codegrounded.trace.TracePhase;
import org.tzi.use.plugins.jacamo.codegrounded.use.NativeUseExporter;
import org.tzi.use.plugins.jacamo.codegrounded.use.NativeUseModelBuilder;
import org.tzi.use.plugins.jacamo.codegrounded.use.NativeUseStateBuilder;

/** Atomic Phase-1A/1B native build. No historical mapping/profile service is reachable from this class. */
public final class CodeGroundedNativePipeline {
    public Result build(ModelSnapshot snapshot) {
        JacamoSpecificationModel source = new JacamoSpecificationModel(snapshot.semanticContract());
        CodeGroundedTraceCollector trace = new CodeGroundedTraceCollector();
        NativeUseModelBuilder.Result model = new NativeUseModelBuilder().build(source, trace);
        NativeUseStateBuilder.Result state = new NativeUseStateBuilder().build(source, model, trace);
        NativeUseExporter.Result export = new NativeUseExporter().export(model.model());
        trace.add(new CodeGroundedTraceRecord("J01", TracePhase.EXPORT,
                source.project().metadata().sourceKind(), source.project().metadata().sourceJavaFqcn(),
                source.project().metadata().semanticId(), "USE_FILE", model.model().name() + ".use",
                source.project().metadata().evidenceAuthority(), source.project().metadata().fidelity(),
                source.project().metadata().capabilityStatus(), java.util.List.of()));
        if (state.system().model() != model.model()) throw new IllegalStateException("NATIVE_SYSTEM_MODEL_IDENTITY_DIVERGED");
        if (!export.originalStructuralHash().equals(export.recompiledStructuralHash()))
            throw new IllegalStateException("NATIVE_EXPORT_HASH_MISMATCH");
        return new Result(source, model, state, export, trace.index());
    }

    public record Result(JacamoSpecificationModel source, NativeUseModelBuilder.Result model,
                         NativeUseStateBuilder.Result state, NativeUseExporter.Result export,
                         CodeGroundedTraceIndex trace) { }
}
