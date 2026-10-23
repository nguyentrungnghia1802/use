package org.jacamo.bridge.adapter;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Consumer;
import cartago.CartagoEnvironment;
import jason.asSemantics.TransitionSystem;
import org.jacamo.bridge.contract.*;
import org.jacamo.bridge.contract.semantic.JasonSemanticContract.AgentProgramSemantic;

/** Curated provider registration. Runtime bootstrap supplies real owners; XML cannot select implementation classes. */
public final class FrameworkObservationProviders {
    public record Context(CartagoEnvironment environment,ModelSnapshot source,Path root,String run,long generation,Consumer<VerificationObservation> sink,Consumer<String> gap) { }
    public static ObservationCapabilityRegistry runtime(Context context) {
        return new ObservationCapabilityRegistry(Map.of(
            ArtifactFieldObservation.PROVIDER,r->new ArtifactFieldObservation(context.environment(),context.source(),r,context.root()),
            NplClockCapability.PROVIDER,r->new NplClockCapability(r,context.run(),context.generation(),context.source().modelRevision(),context.sink()),
            NplLifecycleCapability.PROVIDER,r->new NplLifecycleCapability(r,context),
            CartagoOperationHistory.PROVIDER,r->new CartagoOperationHistory(r,context),
            MoiseSchemeCapability.PROVIDER,r->new MoiseSchemeCapability(r,context.environment(),context.source(),context.root(),context.run(),context.generation())));
    }
    public static ObservationCapabilityRegistry agent(Context context,TransitionSystem system,BridgeEntityId agent,AgentProgramSemantic source) {
        return new ObservationCapabilityRegistry(Map.of(JasonStepCapability.PROVIDER,r->new JasonStepCapability(r,system,agent,context.run(),context.generation(),context.source().modelRevision(),source,context.root(),context.sink(),context.gap())));
    }
    private FrameworkObservationProviders(){ }
}
