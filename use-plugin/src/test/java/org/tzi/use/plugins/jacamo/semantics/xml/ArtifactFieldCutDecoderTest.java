package org.tzi.use.plugins.jacamo.semantics.xml;
import java.nio.file.*;
import java.util.*;
import cartago.*;
import cartago.util.agent.CartagoBasicContext;
import org.jacamo.bridge.adapter.*;
import org.jacamo.bridge.contract.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.tzi.use.plugins.jacamo.semantics.mapping.xml.runtime.ArtifactFieldCutDecoder;
import static org.junit.jupiter.api.Assertions.*;
/** Actual provider and official source/snapshot proof, without Auction identifiers or a live-TCP claim. */
class ArtifactFieldCutDecoderTest {
    @TempDir Path temp;
    @Test @Timeout(40) void validatesActualFieldCutWithoutLoadingProducerClassInConsumerAndRejectsTamperStaleOrWrongIdentity()throws Exception {
        String type=NeutralFieldArtifact.class.getName();Path jcm=Files.writeString(temp.resolve("inventory.jcm"),"mas inventory { workspace main { artifact stock: "+type+" } }\n");
        var source=new OfficialProjectAdapter().adapt(new OfficialProjectLoader().load(jcm),jcm);
        var request=new ObservationCapabilityRequest("token-view",ArtifactFieldObservation.PROVIDER,true,Map.of("class",type,"field","token","type","java.lang.String","properties",Map.of("p","JAVA_INTEGER","ready","BOOLEAN","weight","IEEE754_BINARY64")));
        var environment=CartagoEnvironment.getInstance();environment.init();var actor=new CartagoBasicContext("fieldConsumer"+UUID.randomUUID().toString().replace("-",""));var created=new ArrayList<ArtifactId>();
        var cartago=new CartagoSnapshotSource(environment,List.of("/main"),"run",source);
        try(var snapshots=new SnapshotCoordinator(List.of(cartago),128);var provider=new ArtifactFieldObservation(environment,source,request)) {
            assertTrue(provider.available(),provider.diagnostic());
            for(String name:List.of("stock","other"))created.add(actor.makeArtifact(actor.getJoinedWspId("main"),name,type));
            actor.doAction(created.getFirst(),new Op("bump"),5000);
            var cut=snapshots.capture(source.modelRevision(),3).snapshot();
            var artifacts=cut.facts().stream().filter(f->f.kind()==RuntimeFactKind.ARTIFACT&&f.id().incarnation().equals(created.getFirst().getId().toString())).toList();assertEquals(1,artifacts.size());var artifact=artifacts.getFirst();
            var workspace=cut.facts().stream().filter(f->f.kind()==RuntimeFactKind.WORKSPACE&&Objects.equals(f.values().get("semanticId"),artifact.values().get("workspaceSemanticId"))).findFirst().orElseThrow();
            var scope=new CapabilityObservation.Scope("run",1,source.modelRevision(),"cartago",cut.endWatermarks().get("cartago").sequence());
            var data=provider.capture(created.getFirst(),"SNAPSHOT","");assertEquals("COMPLETE",data.get("status"));
            var receipt=CapabilityObservation.of(request,scope,Completeness.COMPLETE,"",data);
            var decoder=new ArtifactFieldCutDecoder(source,request);var result=decoder.decode(receipt.payload(),artifact,workspace,cut,"run",1);
            assertEquals(Completeness.COMPLETE,result.quality(),result.diagnostic());assertEquals("changed",CanonicalJson.object(result.values().get("fields")).get("token"));
            assertEquals(false,CanonicalJson.object(result.values().get("properties")).get("ready"));assertFalse(result.values().isEmpty());
            assertEquals(Completeness.UNAVAILABLE,decoder.decode(null,artifact,workspace,cut,"run",1).quality());
            var wrongKind=new RuntimeFact(new BridgeEntityId("cartago","environment","agent",artifact.id().scope(),artifact.id().localId(),artifact.id().incarnation()),artifact.kind(),artifact.values(),artifact.relations(),artifact.projectionStatus(),artifact.completeness(),artifact.evidence());
            assertThrows(IllegalArgumentException.class,()->org.tzi.use.plugins.jacamo.semantics.mapping.xml.runtime.CartagoPhysicalReference.aliases(wrongKind,workspace));
            assertEquals(Completeness.UNAVAILABLE,decoder.decode(receipt.payload(),artifact,workspace,cut,"run",2).quality());
            var different=CapabilityObservation.of(request,scope,Completeness.COMPLETE,"",provider.capture(created.getLast(),"SNAPSHOT",""));
            assertEquals(Completeness.UNAVAILABLE,decoder.decode(different.payload(),artifact,workspace,cut,"run",1).quality());
            for(String key:List.of("classHash","fieldCutId")) {
                var tampered=new LinkedHashMap<>(data);tampered.put(key,"0".repeat(64));
                assertEquals(Completeness.UNAVAILABLE,decoder.decode(CapabilityObservation.of(request,scope,Completeness.COMPLETE,"",tampered).payload(),artifact,workspace,cut,"run",1).quality());
            }
            var stale=new CapabilityObservation.Scope("run",1,source.modelRevision(),"cartago",Math.max(0,scope.sourceSequence()-1));
            assertEquals(Completeness.UNAVAILABLE,decoder.decode(CapabilityObservation.of(request,stale,Completeness.COMPLETE,"",data).payload(),artifact,workspace,cut,"run",1).quality());
            var malformed=new LinkedHashMap<>(data);var badProperties=new LinkedHashMap<>(CanonicalJson.object(data.get("properties")));
            var missingEncoding=new LinkedHashMap<>(CanonicalJson.object(badProperties.get("p")));missingEncoding.remove("encoding");badProperties.put("p",missingEncoding);malformed.put("properties",badProperties);
            var proof=CanonicalJson.object(data.get("probeEvidence"));
            malformed.put("fieldCutId",org.tzi.use.plugins.jacamo.semantics.evidence.SemanticJson.hash(Map.of("artifact",data.get("artifactIdentity"),"properties",badProperties,"candidate","changed","origin",proof.get("origin"),"invocation",proof.get("invocation"))));
            assertEquals(Completeness.UNAVAILABLE,decoder.decode(CapabilityObservation.of(request,scope,Completeness.COMPLETE,"",malformed).payload(),artifact,workspace,cut,"run",1).quality(),"A self-consistent hash cannot replace typed framework evidence");
            // Consumer fixture attaches an actual provider receipt. Production publication remains the G11 gate.
            String runtime="""
              <runtime><datasets><fieldCuts id="locked" requestRef="token-view"/></datasets><tables>
               <table id="stock" dataset="locked"><column name="uuid" path="identity" required="true"/><column name="alias" path="artifactSemanticId" required="true"/><column name="p" path="properties.p" required="true"/><identity mode="PHYSICAL" path="identity"/></table>
              </tables><inputs><input id="objects" identityKind="PHYSICAL" identityFields="uuid" tableRef="stock"><field name="uuid" type="String" required="true"/><field name="alias" type="String" required="true"/><field name="p" type="Integer" required="true"/></input></inputs>
              <converters><converter id="integer" inputType="Integer" outputType="Integer" algorithm="preserve-exact-integer" onMismatch="incomplete"/></converters>
              <rule id="locked-state" inputRef="objects"><requireEvidence proof="capability:token-view"/><upsertInstance targetType="Counter" keyFields="uuid" aliasFrom="alias"><assign target="p" sourceField="p" converter="integer"/></upsertInstance></rule></runtime>
              """;
            var specification=MappingXmlReaderTest.parse(MappingXmlReaderTest.XML.replace("<runtime/>",runtime));
            var helper=new M0RuleExecutorTest();helper.temp=temp;var nativeFixture=helper.fixture(M0RuleExecutorTest.RUNTIME,false);
            var batch=new org.tzi.use.plugins.jacamo.semantics.mapping.xml.runtime.XmlRuntimeBatch(specification,nativeFixture.engine().system().model(),nativeFixture.engine().canonicalProgramHash(),source,List.of(request));
            var withReceipt=new LinkedHashMap<>(artifact.values());withReceipt.put(CapabilityObservation.KEY,Map.of(request.id(),receipt.payload()));
            var observed=new RuntimeFact(artifact.id(),artifact.kind(),withReceipt,artifact.relations(),artifact.projectionStatus(),artifact.completeness(),artifact.evidence());
            var facts=new ArrayList<>(cut.facts());facts.replaceAll(f->f.id().equals(artifact.id())?observed:f);
            var qualifiedCut=new RuntimeSnapshot(cut.snapshotId(),cut.modelRevision(),cut.captureStartedAt(),cut.captureEndedAt(),cut.startWatermarks(),cut.endWatermarks(),cut.validationAttempts(),facts,cut.sourceCompleteness(),cut.stateFingerprint(),cut.captureMetadata());
            var qualifiedRows=new org.tzi.use.plugins.jacamo.semantics.mapping.xml.runtime.RuntimeFactSources(specification,source,List.of(request)).capture(qualifiedCut,"run",1).get("locked").rows();
            assertTrue(qualifiedRows.stream().filter(r->r.identity().equals(artifact.id().canonical())).findFirst().orElseThrow().aliases().contains(artifact.values().get("semanticId")),"The qualified physical field cut must bridge its official CArtAgO semantic reference for Jason/self binding");
            var mapped=batch.apply(nativeFixture.port(),"run",1,qualifiedCut);var object=mapped.stream().filter(o->o.status().equals("MAPPED")).findFirst().orElseThrow().object();
            assertEquals(org.tzi.use.uml.ocl.value.IntegerValue.valueOf(2),nativeFixture.port().currentValue(object,"p"));
            assertSame(object,nativeFixture.port().find((String)artifact.values().get("semanticId")));
            var forgedValues=new LinkedHashMap<>(artifact.values());String forgedAlias="cartago:artifact:forged:"+artifact.id().incarnation();forgedValues.put("semanticId",forgedAlias);
            var forgedArtifact=new RuntimeFact(artifact.id(),artifact.kind(),forgedValues,artifact.relations(),artifact.projectionStatus(),artifact.completeness(),artifact.evidence());
            var forgedFacts=new ArrayList<>(cut.facts());forgedFacts.replaceAll(f->f.id().equals(artifact.id())?forgedArtifact:f);
            var forgedCut=new RuntimeSnapshot(cut.snapshotId(),cut.modelRevision(),cut.captureStartedAt(),cut.captureEndedAt(),cut.startWatermarks(),cut.endWatermarks(),cut.validationAttempts(),forgedFacts,cut.sourceCompleteness(),cut.stateFingerprint(),cut.captureMetadata());
            var forgedData=new LinkedHashMap<>(data);forgedData.put("artifactIdentity",forgedAlias);
            forgedData.put("fieldCutId",org.tzi.use.plugins.jacamo.semantics.evidence.SemanticJson.hash(Map.of("artifact",forgedAlias,"properties",data.get("properties"),"candidate","changed","origin",proof.get("origin"),"invocation",proof.get("invocation"))));
            var refused=decoder.decode(CapabilityObservation.of(request,scope,Completeness.COMPLETE,"",forgedData).payload(),forgedArtifact,workspace,forgedCut,"run",1);
            assertEquals(Completeness.UNAVAILABLE,refused.quality());assertTrue(refused.diagnostic().contains("PHYSICAL_SEMANTIC_ALIAS_MISMATCH"));
            batch.apply(nativeFixture.port(),"run",1,cut);assertTrue(nativeFixture.port().currentValue(object,"p").isUndefined());
            assertEquals("INCOMPLETE",nativeFixture.port().metadata(object,"xml:m0:quality"));
            long sequence=scope.sourceSequence()+1;var nextScope=new CapabilityObservation.Scope("run",1,source.modelRevision(),"cartago",sequence);
            var nextReceipt=CapabilityObservation.of(request,nextScope,Completeness.COMPLETE,"",provider.capture(created.getFirst(),"CALLBACK","invocation-1"));
            var event=new RuntimeEvent("field-callback","run",1,source.modelRevision(),"cartago","cartago",sequence,java.time.Instant.EPOCH,RuntimeEventKind.CHANGED,RuntimeFactKind.ARTIFACT,ProjectionStatus.MATERIALIZED_FAITHFULLY,artifact.id(),null,"invocation-1","",Map.of(),Map.of("semanticId",artifact.values().get("semanticId"),CapabilityObservation.KEY,Map.of(request.id(),nextReceipt.payload())),new SourceWatermark("cartago",sequence),Completeness.COMPLETE,List.of());
            var evidence=new LinkedHashMap<String,RuntimeFact>();cut.facts().forEach(f->evidence.put(f.id().canonical(),f));
            var folded=org.tzi.use.plugins.jacamo.semantics.mapping.xml.runtime.RuntimeObservationCut.advance(evidence,event);
            var eventCut=org.tzi.use.plugins.jacamo.semantics.mapping.xml.runtime.RuntimeObservationCut.eventCut(folded,Map.of("cartago",scope.sourceSequence()),cut.sourceCompleteness(),event);
            batch.apply(nativeFixture.port(),"run",1,eventCut);assertEquals(org.tzi.use.uml.ocl.value.IntegerValue.valueOf(2),nativeFixture.port().currentValue(object,"p"));
            assertThrows(IllegalArgumentException.class,()->new org.tzi.use.plugins.jacamo.semantics.mapping.xml.runtime.XmlRuntimeBatch(specification,nativeFixture.engine().system().model(),nativeFixture.engine().canonicalProgramHash(),source,List.of()));
        }finally {
            for(var artifact:created)environment.getController("/main").removeArtifact(artifact.getName());
            for(var id:environment.getController("/main").getCurrentAgents())if(id.getAgentName().equals(actor.getName()))environment.resolveWSP("/main").getWorkspace().quitAgent(id);
            environment.shutdown();
        }
    }
}
