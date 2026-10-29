package org.tzi.use.plugins.jacamo.codegrounded.runtime;

import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.jacamo.bridge.contract.*;
import org.tzi.use.api.UseSystemApi;
import org.tzi.use.plugins.jacamo.codegrounded.trace.NativeObjectBindings;
import org.tzi.use.plugins.jacamo.semantics.evidence.*;
import org.tzi.use.plugins.jacamo.semantics.runtime.CanonicalObservationMappingBindings;
import static org.junit.jupiter.api.Assertions.*;

/** Synthetic admitted callback component; the installed suite owns the actual TCP proof. */
class DeclarativeOperationCheckpointTest {
    @TempDir Path directory;
    @Test void genericPhysicalIdentityOwnsNativePrePostWithoutLegacyUuidMetadata()throws Exception {
        var built=org.tzi.use.plugins.jacamo.semantics.xml.XmlNativePreparationTest.operationFixture(directory);
        var system=built.nativeResult().state().system();var api=UseSystemApi.create(system,false);var self=api.createObject("Counter","counter");api.setAttributeValue("counter","p","4");
        var id=new BridgeEntityId("cartago","environment","artifact","/main","counter",UUID.randomUUID().toString());var index=new NativeObjectBindings();
        index.put(id.canonical(),self);index.metadata(self,"semanticId","xml:m0:counter");index.metadata(self,"xml:m0:identity",id.canonical());index.metadata(self,"xml:m0:program",built.program().hash());index.metadata(self,"xml:m0:quality","COMPLETE");
        index.metadata(self,"observedValueHash:p",CanonicalObservationMappingBindings.valueHash(self.state(system.state()).attributeValue("p")));index.metadata(self,"propertyAbsent:p","false");
        var engine=new NativeRuntimeMutationEngine(system,index,new CodeGroundedRuntimeRuleRegistry(),built.program());var checks=new NativeOperationCheckpoints(engine);
        var portMethod=NativeRuntimeMutationEngine.class.getDeclaredMethod("cslPort");portMethod.setAccessible(true);
        var port=(org.tzi.use.plugins.jacamo.semantics.runtime.SemanticObservationMapper.Port)portMethod.invoke(engine);
        assertTrue(engine.xmlCase());assertEquals("",engine.metadata(self,"uuid"),"The generic writer owns a physical identity, not the legacy metadata shape");
        var pre=event(built.program(),id,1,"started");assertEquals("OPERATION_PRE",checks.eligibleKind(pre));checks.observe(pre,Map.of("cartago",Completeness.COMPLETE),false,Set.of());
        var before=checks.drainDetailed();assertTrue(before.stream().anyMatch(r->r.kind().equals("PRE")&&r.verdict()==SemanticVerdict.PASS),before.toString());
        assertEquals("increment-interface",before.stream().filter(r->r.kind().equals("PRE")).findFirst().orElseThrow().witness().get("sourceRule"));
        checks.retireUnavailableContexts("unrelated-boundary");assertEquals(1,checks.savepoint().active().size());
        api.setAttributeValue("counter","p","5");port.metadata(self,"observedValueHash:p",CanonicalObservationMappingBindings.valueHash(self.state(system.state()).attributeValue("p")));
        checks.observe(event(built.program(),id,2,"completed"),Map.of("cartago",Completeness.COMPLETE),false,Set.of());var after=checks.drainDetailed();
        assertTrue(after.stream().anyMatch(r->r.kind().equals("POST")&&r.verdict()==SemanticVerdict.PASS&&"4".equals(r.witness().get("before.p"))&&"5".equals(r.witness().get("after.p"))),after.toString());
        var wrong=new BridgeEntityId(id.authority(),id.dimension(),id.kind(),"/other",id.localId(),id.incarnation());port.alias(wrong.canonical(),self);
        checks.observe(event(built.program(),wrong,3,"started"),Map.of("cartago",Completeness.COMPLETE),false,Set.of());
        assertTrue(checks.drainDetailed().stream().noneMatch(r->r.kind().equals("PRE")&&r.verdict()==SemanticVerdict.PASS),"Same UUID via a foreign physical scope is not this owner");
    }
    private RuntimeEvent event(org.tzi.use.plugins.jacamo.semantics.mapping.SemanticProgram program,BridgeEntityId id,long sequence,String phase) {
        var signature=List.of("org.tzi.use.plugins.jacamo.semantics.CslCounterArtifact","increment",List.of(),"void");
        // Real OfficialCartagoAdapter uses its framework semantic ID here,
        // while generic raw M0 rules bind only the verified BridgeEntityId.
        var data=Map.<String,Object>of("artifactSemanticId","cartago:artifact:environment:"+id.scope()+":"+id.incarnation(),"phase",phase,"operationSignature",signature,"argumentValues",List.of(),"argumentTypes",List.of(),
                "invocationBoundary",Map.of("version","1.0","authority","ICARTAGOLOGGER_OBSERVED_PREFIX","sourceId","cartago","artifactUuid",id.incarnation(),"previousWatermark",sequence-1));
        return new RuntimeEvent("op-"+sequence,"run",1,program.source().sourceHash(),"cartago","cartago",sequence,Instant.ofEpochMilli(sequence),phase.equals("started")?RuntimeEventKind.STARTED:RuntimeEventKind.SUCCEEDED,RuntimeFactKind.ARTIFACT,ProjectionStatus.EVIDENCE_ONLY,id,null,"invocation","",Map.of(),data,new SourceWatermark("cartago",sequence),Completeness.COMPLETE,List.of());
    }
}
