package org.tzi.use.plugins.jacamo.bridge;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.jacamo.bridge.adapter.OfficialProjectAdapter;
import org.jacamo.bridge.adapter.OfficialProjectLoader;
import org.jacamo.bridge.contract.*;
import org.junit.jupiter.api.Test;
import org.tzi.use.plugins.jacamo.mapping.ActiveBaseline;
import org.tzi.use.plugins.jacamo.mapping.TransformationPlanner;
import org.tzi.use.plugins.jacamo.materialization.DirectUseBackend;
import org.tzi.use.plugins.jacamo.materialization.InstancePlanner;
import org.tzi.use.plugins.jacamo.materialization.TextBackend;
import org.tzi.use.plugins.jacamo.runtime.RuntimeMutationEngine;
import org.tzi.use.plugins.jacamo.trace.TraceBuilder;

class BridgeRuntimeProjectorTest {
    @Test void exactRuntimeBindingInitializesAndMutatesUseStateWhileEvidenceOnlyStaysOut() throws Exception {
        Path jcm=Path.of("src/test/resources/canonical-cases/hello-world/helloworld.jcm").toAbsolutePath().normalize();
        var model=new OfficialProjectAdapter().adapt(new OfficialProjectLoader().load(jcm),jcm);var adapted=new NativeSemanticAdapter().adapt(model,jcm.getParent(),"hello");
        var baseline=new ActiveBaseline().packaged();var structure=new TransformationPlanner().plan(adapted.model(),baseline.mapping());var instances=new InstancePlanner().plan(adapted.model(),baseline.mapping(),structure);var direct=new DirectUseBackend().materialize(new TextBackend().generate("hello_runtime_bridge",structure,instances),instances);var trace=new TraceBuilder().build(adapted.model(),baseline.mapping(),structure,instances);
        var declaration=model.configuredArtifacts().getFirst().id();var live=new BridgeEntityId("cartago","environment","observable-property","runtime","consoleType","artifact-uuid");
        var binding=new BridgeRelationId("runtime-model-binding",List.of(live,declaration),"official-artifact-uuid","artifact-uuid");String source="cartago";
        var faithful=new RuntimeFact(live,RuntimeFactKind.PROPERTY,Map.of("normalizedEventKind","SET_ATTRIBUTE","attribute","type","valueType","STRING","value","snapshot-type"),List.of(binding),ProjectionStatus.MATERIALIZED_FAITHFULLY,Completeness.COMPLETE,List.of());
        var norm=new RuntimeFact(new BridgeEntityId("npl","organisation","norm-instance","org","n1","session"),RuntimeFactKind.NORM_INSTANCE,Map.of("state","ACTIVE"),List.of(),ProjectionStatus.EVIDENCE_ONLY,Completeness.COMPLETE,List.of());
        var snapshot=new RuntimeSnapshot("snapshot",model.modelRevision(),Instant.EPOCH,Instant.EPOCH,Map.of(source,new SourceWatermark(source,0)),Map.of(source,new SourceWatermark(source,0)),1,List.of(faithful,norm),Map.of(source,Completeness.COMPLETE),"f".repeat(64));
        var projector=new BridgeRuntimeProjector(source,adapted,trace,new RuntimeMutationEngine(direct.system(),trace));var result=projector.applySnapshot(snapshot);
        assertEquals(1,result.materialized());assertEquals(List.of(norm.id().canonical()),result.evidenceOnly());assertEquals("'snapshot-type'",attribute(direct,trace,adapted.bridgeIdentityMap().get(declaration.canonical()).value(),"type"));
        var event=new RuntimeEvent("change","session",1,model.modelRevision(),"cartago",source,1,Instant.EPOCH,RuntimeEventKind.CHANGED,RuntimeFactKind.PROPERTY,ProjectionStatus.MATERIALIZED_FAITHFULLY,live,binding,"correlation","",Map.of(),Map.of("normalizedEventKind","SET_ATTRIBUTE","attribute","type","valueType","STRING","value","event-type"),new SourceWatermark(source,1),Completeness.COMPLETE,List.of());
        assertTrue(projector.apply(event));assertEquals("'event-type'",attribute(direct,trace,adapted.bridgeIdentityMap().get(declaration.canonical()).value(),"type"));
        var dynamic=new BridgeEntityId("jason","agent","runtime-agent","hello","late-agent","incarnation");
        var evidence=new RuntimeEvent("late","session",1,model.modelRevision(),"jason","jason:late",1,
                Instant.EPOCH,RuntimeEventKind.CREATED,RuntimeFactKind.AGENT,ProjectionStatus.EVIDENCE_ONLY,
                dynamic,null,"","",Map.of(),Map.of("name","late-agent"),new SourceWatermark("jason:late",1),
                Completeness.PARTIAL,List.of());
        assertFalse(projector.apply(evidence),"dynamic evidence-only source must remain outside USE state");
        assertEquals("BRIDGE_RUNTIME_EVENT_REWIND",
                assertThrows(BridgeProtocolException.class,()->projector.apply(evidence)).getMessage());
    }
    private String attribute(DirectUseBackend.Result direct,org.tzi.use.plugins.jacamo.trace.TraceIndex trace,String semantic,String name){var record=trace.bySemanticId(semantic).stream().filter(r->r.targetKind().equals("OBJECT")).findFirst().orElseThrow();var object=direct.system().state().objectByName(record.targetUseId().substring("object:".length()));return object.state(direct.system().state()).attributeValue(object.cls().attribute(name,true)).toString();}
}
