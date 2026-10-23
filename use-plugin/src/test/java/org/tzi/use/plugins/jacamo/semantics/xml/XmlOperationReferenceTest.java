package org.tzi.use.plugins.jacamo.semantics.xml;

import java.nio.file.*;
import java.util.*;
import cartago.*;
import cartago.util.agent.CartagoBasicContext;
import org.jacamo.bridge.adapter.*;
import org.jacamo.bridge.contract.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.tzi.use.plugins.jacamo.semantics.evidence.TraceComplement;
import org.tzi.use.plugins.jacamo.semantics.mapping.xml.runtime.*;
import static org.junit.jupiter.api.Assertions.*;

/** Public operation feedback establishes a physical reference, not a case-specific binding or Task result. */
class XmlOperationReferenceTest {
    @TempDir Path temp;
    @Test @Timeout(35) void actualCorrelatedFeedbackResolvesCurrentObjectsWithoutACompleteWorldHistory()throws Exception {
        String type=NeutralFieldArtifact.class.getName();var jcm=Files.writeString(temp.resolve("feedback.jcm"),"mas feedback { workspace main { artifact selected: "+type+" } }\n");
        var source=GenericMappingEngineTest.official(jcm);String hash;
        try(var in=WorkspaceArtifact.class.getResourceAsStream("WorkspaceArtifact.class")){hash=TraceComplement.Asset.of("class","test",in.readAllBytes()).sha256();}
        var request=new ObservationCapabilityRequest("workspace-receipts",CartagoOperationHistory.PROVIDER,true,Map.of("class",WorkspaceArtifact.class.getName(),"hash",hash));
        String runtime="<runtime><datasets><operationReferences id=\"references\" requestRef=\"workspace-receipts\" argumentIndex=\"3\"/></datasets></runtime>";
        var specification=MappingXmlReaderTest.parse(MappingXmlReaderTest.XML.replace("<runtime/>",runtime));
        var environment=CartagoEnvironment.getInstance();environment.init();var actor=new CartagoBasicContext("feedback"+UUID.randomUUID().toString().replace("-",""));var created=new ArrayList<ArtifactId>();
        String oldRevision=System.getProperty("jacamo.bridge.modelRevision"),oldGeneration=System.getProperty("jacamo.bridge.generation");System.setProperty("jacamo.bridge.modelRevision",source.modelRevision());System.setProperty("jacamo.bridge.generation","2");
        try(var provider=new CartagoOperationHistory(request,new FrameworkObservationProviders.Context(environment,source,temp,"run",2,ignored->{},reason->fail(reason)));
            var cartago=new CartagoSnapshotSource(environment,List.of("/main"),"run");var snapshots=new SnapshotCoordinator(List.of(cartago),2048,provider::observe)) {
            for(String name:List.of("feedbackOne","feedbackTwo"))created.add(actor.makeArtifact(actor.getJoinedWspId("main"),name,type));
            var cut=snapshots.capture(source.modelRevision(),3).snapshot();
            var workspaceArtifact=cut.facts().stream().filter(f->f.kind()==RuntimeFactKind.ARTIFACT&&WorkspaceArtifact.class.getName().equals(f.values().get("artifactTypeJavaClassName"))).findFirst().orElseThrow();
            var actual=environment.getController("/main").getArtifactInfo(workspaceArtifact.id().localId()).getId();
            var data=provider.capture(actual);assertEquals(false,data.get("fromCreation"),"A pre-existing workspace does not grant history from its creation");
            var scope=new CapabilityObservation.Scope("run",2,source.modelRevision(),"cartago",cut.endWatermarks().get("cartago").sequence());
            var observed=observe(cut,workspaceArtifact,request,CapabilityObservation.of(request,scope,Completeness.COMPLETE,"",data));
            var sources=new RuntimeFactSources(specification,source,List.of(request));var relation=sources.capture(observed,"run",2).get("references");
            assertEquals(Completeness.PARTIAL,relation.populationQuality());assertEquals(2,relation.rows().size());
            assertEquals(Set.copyOf(created.stream().map(a->a.getId().toString()).toList()),relation.rows().stream().map(r->(String)r.values().get("artifactUuid")).collect(java.util.stream.Collectors.toSet()));
            for(var row:relation.rows()) {
                assertEquals(Completeness.COMPLETE,row.quality());assertEquals("makeArtifact",row.values().get("operation"));
                assertEquals(actor.getName(),row.values().get("performerName"));assertEquals(true,row.values().get("correlated"));
                assertEquals(row.identity(),row.proofs().get("physical-identity"));assertTrue(row.aliases().contains(row.values().get("artifactSemanticId")));
            }
            // Fixture protocol crosswalk: the receipt still comes from the actual CArtAgO logger.
            var membership=observed.facts().stream().filter(f->f.kind()==RuntimeFactKind.AGENT&&Objects.equals(f.values().get("name"),actor.getName())).findFirst().orElseThrow();
            var identity=new BridgeEntityId("jason","agent","runtime-agent",source.semanticContract().project().name(),actor.getName(),"current");
            var memberValues=new LinkedHashMap<>(membership.values());memberValues.put("agentRuntimeSemanticId",identity.canonical());
            var player=new RuntimeFact(identity,RuntimeFactKind.AGENT,Map.of("name",actor.getName()),List.of(),ProjectionStatus.MATERIALIZED_FAITHFULLY,Completeness.COMPLETE,List.of());
            var facts=new ArrayList<>(observed.facts());facts.replaceAll(f->f==membership?new RuntimeFact(f.id(),f.kind(),memberValues,f.relations(),f.projectionStatus(),f.completeness(),f.evidence()):f);facts.add(player);
            var marks=new LinkedHashMap<>(observed.endWatermarks());marks.put("jason",new SourceWatermark("jason",1));var coverage=new LinkedHashMap<>(observed.sourceCompleteness());coverage.put("jason",Completeness.COMPLETE);
            var qualified=new RuntimeSnapshot(observed.snapshotId(),observed.modelRevision(),observed.captureStartedAt(),observed.captureEndedAt(),marks,marks,1,facts,coverage,observed.stateFingerprint());
            assertTrue(sources.capture(qualified,"run",2).get("references").rows().stream().allMatch(r->identity.canonical().equals(r.values().get("performerRuntimeId"))));
            var shadow=new BridgeEntityId("jason","agent","runtime-agent",identity.scope(),identity.localId(),"shadow");facts.add(new RuntimeFact(shadow,player.kind(),player.values(),List.of(),player.projectionStatus(),player.completeness(),List.of()));
            var ambiguous=XmlNplDatasetTest.copy(qualified,facts);
            assertTrue(sources.capture(ambiguous,"run",2).get("references").rows().stream().noneMatch(r->r.values().containsKey("performerRuntimeId")),"Conflicting current incarnations with the same name cannot gain a current-player binding");
            var absent=XmlNplDatasetTest.copy(observed,observed.facts().stream().filter(f->!created.getFirst().getId().toString().equals(f.id().incarnation())).toList());
            assertEquals(1,sources.capture(absent,"run",2).get("references").rows().size(),"A retained receipt cannot resurrect a disposed object");
            assertTrue(sources.capture(cut,"run",2).get("references").rows().isEmpty(),"No receipt means no discovery authority");
            var unpaired=new LinkedHashMap<>(data);unpaired.put("events",((List<?>)data.get("events")).stream().map(CanonicalJson::object).filter(e->!e.get("kind").equals("STARTED")).toList());OperationHistoryCutDecoderTest.rehash(unpaired);
            assertTrue(sources.capture(observe(cut,workspaceArtifact,request,CapabilityObservation.of(request,scope,Completeness.COMPLETE,"",unpaired)),"run",2).get("references").rows().isEmpty(),"An incomplete observed prefix needs an actual matching STARTED receipt for every positive reference");
        }finally {
            for(var id:created)environment.getController("/main").removeArtifact(id.getName());CslRuntimeTestRegistry.closeCartagoContext(actor);environment.shutdown();
            NplLifecycleCutTest.restore("jacamo.bridge.modelRevision",oldRevision);NplLifecycleCutTest.restore("jacamo.bridge.generation",oldGeneration);
        }
        assertThrows(IllegalArgumentException.class,()->new RuntimeFactSources(MappingXmlReaderTest.parse(MappingXmlReaderTest.XML.replace("<runtime/>",runtime.replace("argumentIndex=\"3\"","argumentIndex=\"128\""))),source,List.of(request)));
    }
    static RuntimeSnapshot observe(RuntimeSnapshot cut,RuntimeFact target,ObservationCapabilityRequest request,CapabilityObservation receipt) {
        var values=new LinkedHashMap<>(target.values());values.put(CapabilityObservation.KEY,Map.of(request.id(),receipt.payload()));
        var fact=new RuntimeFact(target.id(),target.kind(),values,target.relations(),target.projectionStatus(),target.completeness(),target.evidence());
        return XmlNplDatasetTest.copy(cut,cut.facts().stream().map(f->f==target?fact:f).toList());
    }
}
