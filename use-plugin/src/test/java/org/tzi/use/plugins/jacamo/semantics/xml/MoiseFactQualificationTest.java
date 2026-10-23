package org.tzi.use.plugins.jacamo.semantics.xml;
import java.nio.file.*;
import java.util.*;
import cartago.*;
import cartago.util.agent.CartagoBasicContext;
import org.jacamo.bridge.adapter.*;
import org.jacamo.bridge.contract.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.tzi.use.plugins.jacamo.semantics.CslGroupBoard;
import org.tzi.use.plugins.jacamo.semantics.mapping.xml.runtime.RuntimeFactSources;
import static org.junit.jupiter.api.Assertions.*;
class MoiseFactQualificationTest {
    @Test @Timeout(35) void actualPublicGroupMatchesFrozenDefinitionAndRejectsRoleOrSemanticShapeDrift()throws Exception {
        Path jcm=Path.of("src/test/resources/semantics/original-engine/monitor.jcm").toRealPath();
        var source=GenericMappingEngineTest.official(jcm);
        var spec=MappingXmlReaderTest.parse(MappingXmlReaderTest.XML.replace("<runtime/>","<runtime><datasets><facts id=\"groups\" authority=\"moise\" kind=\"GROUP_BOARD\"/></datasets></runtime>"));
        var dataset=new RuntimeFactSources(spec,source,List.of());var environment=CartagoEnvironment.getInstance();environment.init();
        var actor=new CartagoBasicContext("qualification"+UUID.randomUUID().toString().replace("-",""));ArtifactId artifact=null;AutoCloseable registration=null;
        try {
            registration=CslRuntimeTestRegistry.bind(source.semanticContract(),actor.getName(),"alice");
            artifact=actor.makeArtifact(actor.getJoinedWspId("main"),"qualification-group",CslGroupBoard.class.getName(),new Object[]{jcm.getParent().resolve("src/org/organization.xml").toString(),"team"});
            actor.doAction(artifact,new Op("adoptRole","worker"));
            var boards=new MoiseBoardSnapshotSource("org","run",()->List.of(CslGroupBoard.instance),List::of,source.semanticContract(),jcm.getParent());
            try(var snapshots=new SnapshotCoordinator(List.of(boards),128)) {
                var cut=snapshots.capture(source.modelRevision(),3).snapshot();
                var first=dataset.capture(cut,"run",1).get("groups");assertEquals(1,first.rows().size());assertEquals(Completeness.COMPLETE,first.rows().getFirst().quality());
                var fact=cut.facts().stream().filter(f->f.kind()==RuntimeFactKind.GROUP_BOARD).findFirst().orElseThrow();
                for(String key:List.of("groupSpecSemanticId","organisationSpecSemanticId")) {
                    var values=new LinkedHashMap<>(fact.values());values.put(key,"wrong-definition");
                    assertThrows(IllegalArgumentException.class,()->dataset.capture(replace(cut,fact,values),"run",1));
                }
                var values=new LinkedHashMap<>(fact.values());values.put("players",List.of(Map.of("agentSemanticId","actual-agent","roleSemanticId","unknown-role")));
                assertThrows(IllegalArgumentException.class,()->dataset.capture(replace(cut,fact,values),"run",1));
                var definition=new LinkedHashMap<>(CanonicalJson.object(fact.values().get("organisationDefinition")));definition.put("name","mutated-name");var changed=new LinkedHashMap<>(fact.values());changed.put("organisationDefinition",definition);
                assertThrows(IllegalArgumentException.class,()->dataset.capture(replace(cut,fact,changed),"run",1));
            }
        }finally {
            if(artifact!=null){CslGroupBoard.instance.getNormativeEngine().stop();environment.getController("/main").removeArtifact(artifact.getName());}
            if(registration!=null)registration.close();CslRuntimeTestRegistry.closeCartagoContext(actor);environment.shutdown();
        }
    }
    RuntimeSnapshot replace(RuntimeSnapshot cut,RuntimeFact old,Map<String,Object> values) {
        var replacement=new RuntimeFact(old.id(),old.kind(),values,old.relations(),old.projectionStatus(),old.completeness(),old.evidence());
        return new RuntimeSnapshot(cut.snapshotId(),cut.modelRevision(),cut.captureStartedAt(),cut.captureEndedAt(),cut.startWatermarks(),cut.endWatermarks(),1,cut.facts().stream().map(f->f.id().equals(old.id())?replacement:f).toList(),cut.sourceCompleteness(),cut.stateFingerprint());
    }
}
