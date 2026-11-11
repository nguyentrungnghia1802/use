package org.tzi.use.plugins.jacamo.semantics.mapping.compact;

import java.nio.file.*;
import java.util.*;
import cartago.*;
import cartago.util.agent.CartagoBasicContext;
import org.jacamo.bridge.adapter.*;
import org.jacamo.bridge.contract.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.tzi.use.plugins.jacamo.codegrounded.*;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.*;
import org.tzi.use.plugins.jacamo.codegrounded.use.*;
import org.tzi.use.plugins.jacamo.semantics.mapping.xml.*;
import org.tzi.use.uml.ocl.value.*;
import static org.junit.jupiter.api.Assertions.*;

/** Actual generated request -> producer -> pinned decoder -> existing native writer.
 * A local CArtAgO component gate, not a live JaCaMo/TCP or package acceptance. */
class CompactFieldProviderIntegrationTest {
    @TempDir Path temp;
    @Test @Timeout(40) void generatedTypedFieldRequestReadsTheActualProviderAndCannotReuseAnOldReceipt() throws Exception {
        String type=NeutralFieldArtifact.class.getName();
        var project=Files.writeString(temp.resolve("inventory.jcm"),"mas inventory { workspace main { artifact stock: "+type+" } }");
        var source=new OfficialProjectAdapter().adapt(new OfficialProjectLoader().load(project),project);
        var csl=Files.writeString(temp.resolve("state.csl"),"""
            csl v4.0 Inventory { orgContext Run { attribute path : String required; }
              entity Stock { attribute p : Integer required mutable;
                attribute ready : Boolean required mutable;
                attribute weight : Real required mutable;
                attribute signal : String required mutable;
                attribute token : String required mutable; }
            }
            """);
        var istar=Files.writeString(temp.resolve("goals.istar"),"istar InventoryGoals { role Observer { goal Seen : Achieve } }");
        var xml=Files.writeString(temp.resolve("mapping.xml"),"""
            <mapping xmlns="urn:use:jacamo-mapping:3" version="3.0" id="field-test"><csl>
              <map source="workspace:*" target="Run"/><map source="workspace:*/identity:path" target="Run.path"/>
              <map source="artifact:%s" target="Stock"/>
              <map source="artifact:%s/property:p" target="Stock.p" policy="OBSERVED_PROPERTY/1.0"/>
              <map source="artifact:%s/property:ready" target="Stock.ready" policy="OBSERVED_PROPERTY/1.0"/>
              <map source="artifact:%s/property:weight" target="Stock.weight" policy="OBSERVED_PROPERTY/1.0"/>
              <map source="artifact:%s/property:signal" target="Stock.signal" policy="OBSERVED_PROPERTY/1.0" converter="JASON_ATOM_TO_STRING/1.0"/>
              <map source="artifact:%s/field:token" target="Stock.token"/>
              </csl><istar><map source="workspace:*" target="Observer"/></istar><runtime>
              <actorRuntime actor="Observer" activationPolicy="PROJECT_EXECUTION_VIEW" episodePolicy="STATIC_LIFETIME" self="Run"/>
              <verify target="Observer.Seen"/></runtime></mapping>
            """.formatted(type,type,type,type,type,type));
        var program=new GenericMappingEngine().map(source,MappingInputs.read(project,xml,null,csl,istar));
        var request=program.compactPlan().capabilityRequests().stream().filter(r->r.provider().equals(ArtifactFieldObservation.PROVIDER)).findFirst().orElseThrow();
        var built=new CodeGroundedNativePipeline().buildCanonicalProgram(source,NativeProjectionMode.AUTO,program);
        var engine=new NativeRuntimeMutationEngine(built.nativeResult().state().system(),built.nativeResult().state().semanticObjectIndex(),new CodeGroundedRuntimeRuleRegistry(),program);
        var environment=CartagoEnvironment.getInstance();environment.init();
        var actor=new CartagoBasicContext("fieldWriter"+UUID.randomUUID().toString().replace("-",""));
        ArtifactId created=null;
        var cartago=new CartagoSnapshotSource(environment,List.of("/main"),"run",source);
        cartago.observationScope(program.compactPlan().captureScope());cartago.observationKinds(program.compactPlan().cartagoKinds());
        try(var snapshots=new SnapshotCoordinator(List.of(cartago),128);var provider=new ArtifactFieldObservation(environment,source,request,temp)) {
            assertTrue(provider.available(),provider.diagnostic());
            created=actor.makeArtifact(actor.getJoinedWspId("main"),"stock",type);actor.doAction(created,new Op("bump"),5000);
            var cut=snapshots.capture(source.modelRevision(),3).snapshot();String uuid=created.getId().toString();
            var artifact=cut.facts().stream().filter(f->f.kind()==RuntimeFactKind.ARTIFACT&&f.id().incarnation().equals(uuid)).findFirst().orElseThrow();
            var receipt=CapabilityObservation.of(request,new CapabilityObservation.Scope("run",1,source.modelRevision(),"cartago",cut.endWatermarks().get("cartago").sequence()),
                    Completeness.COMPLETE,"",provider.capture(created,"SNAPSHOT",""));
            var values=new TreeMap<>(artifact.values());values.put(CapabilityObservation.KEY,Map.of(request.id(),receipt.payload()));
            var facts=cut.facts().stream().map(f->f.id().equals(artifact.id())?new RuntimeFact(f.id(),f.kind(),values,f.relations(),f.projectionStatus(),f.completeness(),f.evidence()):f).toList();
            var qualified=new RuntimeSnapshot(cut.snapshotId(),cut.modelRevision(),cut.captureStartedAt(),cut.captureEndedAt(),cut.startWatermarks(),cut.endWatermarks(),cut.validationAttempts(),facts,cut.sourceCompleteness(),cut.stateFingerprint(),cut.captureMetadata());
            engine.applyMappingCut(qualified,"run",1,List.of());
            var object=engine.objectForSemanticId(artifact.id().canonical());assertNotNull(object);
            var scopes=engine.xmlOccurrences("run",1,source.modelRevision());assertFalse(scopes.isEmpty());
            assertTrue(scopes.stream().allMatch(s->s.complete()),"The actual producer's selected inventory metadata must qualify the native workspace scope");
            assertEquals(new StringValue("changed"),object.state(engine.system().state()).attributeValue("token"));
            assertEquals(IntegerValue.valueOf(2),object.state(engine.system().state()).attributeValue("p"));
            assertEquals(new RealValue(Double.MAX_VALUE),object.state(engine.system().state()).attributeValue("weight"));
            assertEquals(new StringValue("ready"),object.state(engine.system().state()).attributeValue("signal"));
            engine.applyMappingCut(cut,"run",1,List.of());
            assertSame(object,engine.objectForSemanticId(artifact.id().canonical()));
            assertTrue(object.state(engine.system().state()).attributeValue("token").isUndefined(),"An earlier receipt cannot supply the next cut's missing field evidence");
            assertEquals("true",engine.metadata(object,"propertyAbsent:token"));
        } finally {
            if(created!=null)environment.getController("/main").removeArtifact(created.getName());
            for(var id:environment.getController("/main").getCurrentAgents())if(id.getAgentName().equals(actor.getName()))environment.resolveWSP("/main").getWorkspace().quitAgent(id);
            environment.shutdown();
        }
    }
}
