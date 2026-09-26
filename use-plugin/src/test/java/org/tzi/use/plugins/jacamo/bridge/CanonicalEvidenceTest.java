package org.tzi.use.plugins.jacamo.bridge;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.jacamo.bridge.adapter.OfficialMoiseAdapter;
import org.jacamo.bridge.adapter.OfficialProjectAdapter;
import org.jacamo.bridge.adapter.OfficialProjectLoader;
import org.jacamo.bridge.contract.CanonicalJson;
import org.junit.jupiter.api.Test;
import org.tzi.use.plugins.jacamo.mapping.ActiveBaseline;
import org.tzi.use.plugins.jacamo.mapping.TransformationPlanner;
import org.tzi.use.plugins.jacamo.materialization.DirectUseBackend;
import org.tzi.use.plugins.jacamo.materialization.InstancePlanner;
import org.tzi.use.plugins.jacamo.materialization.TextBackend;

class CanonicalEvidenceTest {
    @Test void writeDeterministicCanonicalAcceptanceMetrics() throws Exception {
        Path examples=Path.of("..","..","JaCaMo","examples").toAbsolutePath().normalize();
        var cases=Map.of("hello",Path.of("src/test/resources/canonical-cases/hello-world/helloworld.jcm").toAbsolutePath().normalize(),"auction",examples.resolve("auction/auction.jcm"),"house",examples.resolve("house-building/house-building.jcm"));
        var output=new TreeMap<String,Object>();
        for(var entry:cases.entrySet())output.put(entry.getKey(),metrics(entry.getKey(),entry.getValue()));
        var houseOs=new OfficialMoiseAdapter().load(examples.resolve("house-building"),examples.resolve("house-building/src/org/house-os.xml"),"house_building");
        output.put("houseRuntimeLoadedOs",Map.of("factsByKind",counts(houseOs.facts()),"groupRoleCardinalities",houseOs.groupRoleCardinalities().stream().map(c->Map.of("id",c.id().canonical(),"context",c.context().canonical(),"member",c.member().canonical(),"min",c.min(),"max",c.max())).toList(),"parentSubGroupCardinalities",houseOs.parentSubGroupCardinalities().size()));
        Path target=Path.of("target/architecture-realignment/canonical-evidence.json");Files.createDirectories(target.getParent());Files.write(target,CanonicalJson.encode(output));
        assertTrue(Files.size(target)>1000);assertEquals(22,((Number)((Map<?,?>)output.get("house")).get("configuredAgentInstances")).intValue());assertEquals(13,((Map<?,?>)((Map<?,?>)output.get("houseRuntimeLoadedOs")).get("factsByKind")).get("organisational-goal"));
    }
    private Map<String,Object> metrics(String name,Path jcm)throws Exception{var snapshot=new OfficialProjectAdapter().adapt(new OfficialProjectLoader().load(jcm),jcm);var adapted=new NativeSemanticAdapter().adapt(snapshot,jcm.getParent(),name);var baseline=new ActiveBaseline().packaged();var structure=new TransformationPlanner().plan(adapted.model(),baseline.mapping());var instances=new InstancePlanner().plan(adapted.model(),baseline.mapping(),structure);var direct=new DirectUseBackend().materialize(new TextBackend().generate(name+"_evidence",structure,instances),instances);var all=new ArrayList<org.jacamo.bridge.contract.ModelFact>();all.addAll(snapshot.agentDeclarations());all.addAll(snapshot.workspaces());all.addAll(snapshot.configuredArtifacts());all.addAll(snapshot.organisationFacts());all.addAll(snapshot.crossDimensionalRelations());int configured=snapshot.agentDeclarations().stream().filter(f->f.factKind().equals("agent-declaration")).mapToInt(f->Integer.parseInt(f.attributes().get("instances"))).sum();var cards=snapshot.groupRoleCardinalities().stream().map(c->Map.<String,Object>of("id",c.id().canonical(),"context",c.context().canonical(),"member",c.member().canonical(),"min",c.min(),"max",c.max())).toList();var result=new LinkedHashMap<String,Object>();result.put("jcm",jcm.toString());result.put("jcmSha256",sha(jcm));result.put("modelRevision",snapshot.modelRevision());result.put("factsByKind",counts(all));result.put("configuredAgentInstances",configured);result.put("groupRoleCardinalities",cards);result.put("parentSubGroupCardinalities",snapshot.parentSubGroupCardinalities().size());result.put("unresolved",snapshot.unresolvedFacts().stream().map(u->Map.of("kind",u.kind(),"status",u.status().name(),"reason",u.reason())).toList());result.put("semanticElements",adapted.model().elements().size());result.put("useObjects",direct.system().state().numObjects());result.put("structureValid",direct.structureValid());return result;}
    private Map<String,Integer> counts(List<org.jacamo.bridge.contract.ModelFact> facts){var counts=new TreeMap<String,Integer>();for(var fact:facts)counts.merge(fact.factKind(),1,Integer::sum);return counts;}
    private String sha(Path path)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));}
}
