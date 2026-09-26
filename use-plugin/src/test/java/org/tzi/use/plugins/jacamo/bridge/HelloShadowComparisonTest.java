package org.tzi.use.plugins.jacamo.bridge;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.jacamo.bridge.adapter.OfficialProjectAdapter;
import org.jacamo.bridge.adapter.OfficialProjectLoader;
import org.jacamo.bridge.contract.CanonicalJson;
import org.junit.jupiter.api.Test;
import org.tzi.use.plugins.jacamo.extraction.StaticProjectImporter;
import org.tzi.use.plugins.jacamo.mapping.ActiveBaseline;
import org.tzi.use.plugins.jacamo.mapping.TransformationPlanner;
import org.tzi.use.plugins.jacamo.materialization.DirectUseBackend;
import org.tzi.use.plugins.jacamo.materialization.InstancePlanner;
import org.tzi.use.plugins.jacamo.materialization.TextBackend;

class HelloShadowComparisonTest {
    @Test void officialLegacyBridgeV2AndUseDifferencesAreExhaustivelyClassified() throws Exception {
        Path jcm=Path.of("src/test/resources/canonical-cases/hello-world/helloworld.jcm").toAbsolutePath().normalize();String provenance=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(jcm)));
        var snapshot=new OfficialProjectAdapter().adapt(new OfficialProjectLoader().load(jcm),jcm);var bridge=new NativeSemanticAdapter().adapt(snapshot,jcm.getParent(),"hello");var legacy=new StaticProjectImporter().importProject(jcm);assertTrue(legacy.success(),legacy.diagnostics().toString());var baseline=new ActiveBaseline().packaged();var structure=new TransformationPlanner().plan(bridge.model(),baseline.mapping());var instances=new InstancePlanner().plan(bridge.model(),baseline.mapping(),structure);var direct=new DirectUseBackend().materialize(new TextBackend().generate("hello_shadow",structure,instances),instances);
        var facts=new ArrayList<ShadowSemanticComparator.Fact>();add(facts,ShadowSemanticComparator.Stage.OFFICIAL_SOURCE,officialCounts(snapshot),provenance);add(facts,ShadowSemanticComparator.Stage.LEGACY,semanticCounts(legacy.model()),provenance);add(facts,ShadowSemanticComparator.Stage.BRIDGE,semanticCounts(bridge.model()),provenance);add(facts,ShadowSemanticComparator.Stage.V2_PLAN,instances.objects().stream().collect(java.util.stream.Collectors.groupingBy(o->o.className(),TreeMap::new,java.util.stream.Collectors.counting())),provenance);add(facts,ShadowSemanticComparator.Stage.USE_RESULT,direct.system().state().allObjects().stream().collect(java.util.stream.Collectors.groupingBy(o->o.cls().name(),TreeMap::new,java.util.stream.Collectors.counting())),provenance);
        var comparator=new ShadowSemanticComparator();var register=new ArrayList<ShadowSemanticComparator.Difference>();
        var officialBridge=comparator.compare(ShadowSemanticComparator.Stage.OFFICIAL_SOURCE,ShadowSemanticComparator.Stage.BRIDGE,facts,d->ShadowSemanticComparator.Classification.ADAPTER_BUG);register.addAll(officialBridge);assertTrue(officialBridge.isEmpty(),officialBridge.toString());
        register.addAll(comparator.compare(ShadowSemanticComparator.Stage.OFFICIAL_SOURCE,ShadowSemanticComparator.Stage.LEGACY,facts,d->ShadowSemanticComparator.Classification.LEGACY_LIMITATION));
        register.addAll(comparator.compare(ShadowSemanticComparator.Stage.OFFICIAL_SOURCE,ShadowSemanticComparator.Stage.V2_PLAN,facts,d->ShadowSemanticComparator.Classification.REPRESENTATION_LOSS));
        register.addAll(comparator.compare(ShadowSemanticComparator.Stage.V2_PLAN,ShadowSemanticComparator.Stage.USE_RESULT,facts,d->ShadowSemanticComparator.Classification.ADAPTER_BUG));
        assertTrue(register.stream().noneMatch(d->d.classification()==ShadowSemanticComparator.Classification.ADAPTER_BUG),register.toString());
        var rows=register.stream().map(d->Map.<String,Object>of("baseline",d.baseline().name(),"candidate",d.candidate().name(),"canonicalId",d.canonicalId(),"baselineDigest",d.baselineDigest(),"candidateDigest",d.candidateDigest(),"provenanceDigest",d.provenanceDigest(),"classification",d.classification().name())).toList();Path output=Path.of("target/architecture-realignment/hello-shadow-register.json");Files.createDirectories(output.getParent());Files.write(output,CanonicalJson.encode(Map.of("fingerprint",comparator.fingerprint(register),"differences",rows)));assertFalse(register.isEmpty());
    }
    private void add(List<ShadowSemanticComparator.Fact> facts,ShadowSemanticComparator.Stage stage,Map<String,? extends Number> counts,String provenance){counts.forEach((kind,count)->facts.add(new ShadowSemanticComparator.Fact(stage,"kind:"+kind,provenance,Map.of("count",count.longValue()))));}
    private Map<String,Long> semanticCounts(org.tzi.use.plugins.jacamo.semantic.JaCaMoSemanticModel model){return model.elements().stream().collect(java.util.stream.Collectors.groupingBy(e->e.kind().name(),TreeMap::new,java.util.stream.Collectors.counting()));}
    private Map<String,Long> officialCounts(org.jacamo.bridge.contract.ModelSnapshot value){var result=new TreeMap<String,Long>();var facts=new ArrayList<org.jacamo.bridge.contract.ModelFact>();facts.addAll(value.agentDeclarations());facts.addAll(value.workspaces());facts.addAll(value.configuredArtifacts());facts.addAll(value.organisationFacts());for(var fact:facts){String kind=switch(fact.factKind()){case "agent-declaration"->"Agent";case "plan"->"Plan";case "event"->"Event";case "action"->"Action";case "belief"->"Belief";case "goal"->"AGoal";case "workspace-declaration"->"Workspace";case "artifact-declaration"->"Artifact";case "role"->"Role";case "group"->"Group";case "scheme"->"Scheme";case "mission"->"Mission";case "organisational-goal"->"OGoal";case "organisational-plan"->"OPlan";case "norm"->"Norm";default->null;};if(kind!=null)result.merge(kind,1L,Long::sum);}result.put("Environment",1L);result.put("Organization",1L);return result;}
}
