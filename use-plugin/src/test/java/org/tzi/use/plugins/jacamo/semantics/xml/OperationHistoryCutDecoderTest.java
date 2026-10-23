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
import org.tzi.use.plugins.jacamo.semantics.evidence.*;
import org.tzi.use.plugins.jacamo.semantics.mapping.xml.runtime.*;
import static org.junit.jupiter.api.Assertions.*;

class OperationHistoryCutDecoderTest {
    @TempDir Path temp;
    @Test @Timeout(30) void actualReceiptsReachXmlSetsWithExactActorInvocationAndNoSuccessValueChangeInference()throws Exception {
        String type=NeutralOperationArtifact.class.getName();var jcm=Files.writeString(temp.resolve("receipt.jcm"),"mas receipts { workspace main { artifact service: "+type+" } }\n");
        var source=new OfficialProjectAdapter().adapt(new OfficialProjectLoader().load(jcm),jcm);
        String hash;try(var in=NeutralOperationArtifact.class.getResourceAsStream("NeutralOperationArtifact.class")){hash=TraceComplement.Asset.of("class","test",in.readAllBytes()).sha256();}
        var request=new ObservationCapabilityRequest("receipts",CartagoOperationHistory.PROVIDER,true,Map.of("class",type,"hash",hash));
        var environment=CartagoEnvironment.getInstance();environment.init();var actorA=new CartagoBasicContext("offerA"+UUID.randomUUID().toString().replace("-",""));var actorB=new CartagoBasicContext("offerB"+UUID.randomUUID().toString().replace("-",""));ArtifactId artifact=null;
        String oldRevision=System.getProperty("jacamo.bridge.modelRevision"),oldGeneration=System.getProperty("jacamo.bridge.generation");System.setProperty("jacamo.bridge.modelRevision",source.modelRevision());System.setProperty("jacamo.bridge.generation","2");
        try(var provider=new CartagoOperationHistory(request,new FrameworkObservationProviders.Context(environment,source,temp,"run",2,ignored->{},reason->fail(reason)));
            var cartago=new CartagoSnapshotSource(environment,List.of("/main"),"run");var snapshots=new SnapshotCoordinator(List.of(cartago),2048,provider::observe)) {
            artifact=actorA.makeArtifact(actorA.getJoinedWspId("main"),"receiptTarget",type);actorA.doAction(artifact,new Op("offer",5.0),5000);actorB.doAction(artifact,new Op("offer",7.0),5000);
            var selected=artifact;assertThrows(cartago.util.agent.ActionFailedException.class,()->actorB.doAction(selected,new Op("offer",-1.0),5000));
            var cut=snapshots.capture(source.modelRevision(),3).snapshot();var target=cut.facts().stream().filter(f->f.kind()==RuntimeFactKind.ARTIFACT&&f.id().incarnation().equals(selected.getId().toString())).findFirst().orElseThrow();
            var workspace=cut.facts().stream().filter(f->f.kind()==RuntimeFactKind.WORKSPACE&&Objects.equals(f.values().get("semanticId"),target.values().get("workspaceSemanticId"))).findFirst().orElseThrow();
            var scope=new CapabilityObservation.Scope("run",2,source.modelRevision(),"cartago",cut.endWatermarks().get("cartago").sequence());var data=provider.capture(artifact);var receipt=CapabilityObservation.of(request,scope,Completeness.COMPLETE,"",data);
            var decoder=new OperationHistoryCutDecoder(request);var result=decoder.decode(receipt.payload(),target,workspace,cut,"run",2);assertEquals(Completeness.COMPLETE,result.quality(),result.diagnostic());
            var events=((List<?>)result.values().get("events")).stream().map(CanonicalJson::object).toList();assertEquals(6,events.size());
            assertEquals(Set.of(actorA.getName(),actorB.getName()),events.stream().filter(e->e.get("outcome").equals("SUCCEEDED")).map(e->e.get("performerName")).collect(java.util.stream.Collectors.toSet()));
            assertTrue(events.stream().allMatch(e->Boolean.TRUE.equals(e.get("performerKnown"))));
            assertEquals(Completeness.UNAVAILABLE,decoder.decode(null,target,workspace,cut,"run",2).quality());assertEquals(Completeness.UNAVAILABLE,decoder.decode(receipt.payload(),target,workspace,cut,"run",3).quality());
            var bad=new LinkedHashMap<>(data);bad.put("events",((List<?>)data.get("events")).stream().map(CanonicalJson::object).filter(e->e.get("kind").equals("SUCCEEDED")).toList());rehash(bad);
            assertEquals(Completeness.UNAVAILABLE,decoder.decode(CapabilityObservation.of(request,scope,Completeness.COMPLETE,"",bad).payload(),target,workspace,cut,"run",2).quality(),"A terminal without its exact started predecessor cannot claim continuous history");
            var wrongActor=new LinkedHashMap<>(data);wrongActor.put("events",((List<?>)data.get("events")).stream().map(CanonicalJson::object).map(e->{var changed=new LinkedHashMap<>(e);var after=new LinkedHashMap<>(CanonicalJson.object(e.get("after")));after.put("performerName","not-the-recorded-agent");changed.put("after",after);return changed;}).toList());rehash(wrongActor);
            assertEquals(Completeness.UNAVAILABLE,decoder.decode(CapabilityObservation.of(request,scope,Completeness.COMPLETE,"",wrongActor).payload(),target,workspace,cut,"run",2).quality(),"Rehashing receipts cannot redirect the recorded physical performer to another name");
            var missingPlayer=XmlNplDatasetTest.copy(cut,cut.facts().stream().filter(f->f.kind()!=RuntimeFactKind.AGENT).toList());
            var unbound=decoder.decode(receipt.payload(),target,workspace,missingPlayer,"run",2);assertEquals(Completeness.COMPLETE,unbound.quality(),unbound.diagnostic());
            assertTrue(((List<?>)unbound.values().get("events")).stream().map(CanonicalJson::object).noneMatch(e->e.containsKey("performerId")),"A reported name is not an admitted current player identity");
            String runtime="""
              <runtime><datasets><operationCuts id="history" requestRef="receipts"/></datasets><tables><table id="successes" dataset="history">
               <selectValues path="events" as="players" valuePath="performerId" valueType="String" completePath="continuous" distinct="true" matchPath="outcome" matchType="String" equals="SUCCEEDED"/>
               <column name="players" path="players" required="true"/>
              </table></tables></runtime>
              """;
            var specification=MappingXmlReaderTest.parse(MappingXmlReaderTest.XML.replace("<runtime/>",runtime));var sources=new RuntimeFactSources(specification,source,List.of(request));
            var values=new LinkedHashMap<>(target.values());values.put(CapabilityObservation.KEY,Map.of(request.id(),receipt.payload()));
            var observed=new RuntimeFact(target.id(),target.kind(),values,target.relations(),target.projectionStatus(),target.completeness(),target.evidence());
            var withReceipt=XmlNplDatasetTest.copy(cut,cut.facts().stream().map(f->f==target?observed:f).toList());
            var rows=new RuntimeTables(specification).execute(sources.capture(withReceipt,"run",2)).get("successes");assertEquals(Completeness.COMPLETE,rows.rows().getFirst().quality());assertEquals(2,((List<?>)rows.rows().getFirst().values().get("players")).size());
            assertEquals(Completeness.UNAVAILABLE,sources.capture(cut,"run",2).get("history").rows().getFirst().quality());
        }finally {
            if(artifact!=null)environment.getController("/main").removeArtifact(artifact.getName());CslRuntimeTestRegistry.closeCartagoContext(actorA);CslRuntimeTestRegistry.closeCartagoContext(actorB);environment.shutdown();NplLifecycleCutTest.restore("jacamo.bridge.modelRevision",oldRevision);NplLifecycleCutTest.restore("jacamo.bridge.generation",oldGeneration);
        }
    }
    static void rehash(Map<String,Object> data){data.remove("hash");data.put("hash",SemanticJson.hash(data));}
}
