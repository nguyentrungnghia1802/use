package org.tzi.use.plugins.jacamo.semantics.xml;

import java.nio.file.*;
import java.util.*;
import org.jacamo.bridge.contract.CanonicalJson;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tzi.use.plugins.jacamo.semantics.evidence.TraceComplement;
import org.tzi.use.plugins.jacamo.semantics.mapping.xml.*;
import org.tzi.use.plugins.jacamo.semantics.mapping.xml.runtime.RuntimeValueConverters;
import org.tzi.use.plugins.jacamo.semantics.mapping.ocl.*;
import org.tzi.use.uml.ocl.value.RealValue;
import static org.junit.jupiter.api.Assertions.*;

class NewCaseStudyBundlesTest {
    @TempDir Path temporary;
    @Test void bothBundlesLoadOriginalModelsValidateSourcesAndCompileNativeSchemas() throws Exception {
        for(String name:List.of("hello-world","house-building")) {
            Path root=Path.of("examples",name);String prefix=name.equals("hello-world")?"hello":"house";
            var inputs=CanonicalJson.object(CanonicalJson.decode(Files.readAllBytes(root.resolve("case-manifest.json"))));
            var pins=CanonicalJson.object(inputs.get("sha256"));assertEquals(5,pins.size());
            for(var pin:pins.entrySet())assertEquals(pin.getValue(),sha(root.resolve(pin.getKey())));
            var manifest=CanonicalJson.object(CanonicalJson.decode(Files.readAllBytes(root.resolve("provenance.json"))));
            for(Object entry:(List<?>)manifest.get("files")) {
                var file=CanonicalJson.object(entry);
                assertEquals(file.get("localSha256"),sha(root.resolve((String)file.get("path"))));
                assertEquals(file.get("upstreamSha256"),sha(Path.of((String)file.get("upstream"))),"Reference source must remain unchanged");
                if(!file.get("localSha256").equals(file.get("upstreamSha256")))assertFalse(((List<?>)file.get("patches")).isEmpty());
            }
            var input=MappingInputs.read(root.resolve((String)manifest.get("jcm")),root.resolve(prefix+"-mapping.xml"),root.resolve(prefix+".ocl"),root.resolve(prefix+".csl"),root.resolve(prefix+".istar"));
            assertFalse(SourceSelections.execute(input).isEmpty());
            assertTrue(input.specification().root().child("requirements").children("capability").stream()
                    .anyMatch(n->n.attr("provider").equals("cartago.operation-history.v1")&&n.attr("operations").contains(name.equals("hello-world")?"printMsg":"bid")),"Authored pre/post operations must be selected in the production producer");
            var loader=new AuthoredModelLoader();var csl=loader.csl(input);var goals=loader.goals(input);
            assertEquals(1,csl.model().entities().size());
            assertEquals(name.equals("hello-world")?2:15,goals.model().getActors().getFirst().elements().size());
            var model=new CslScopeModelAdapter().compile(csl.model(),XmlNativePreparation.observerSchema(input.specification())).model();
            assertNotNull(model.getClass(name.equals("hello-world")?"Console":"ProcurementAuction"));
            assertNotNull(new AuthoredOclSpecificationLoader().parse(input.ocl().bytes(),"authored"));
            if(name.equals("house-building"))assertNotNull(model.getClass("BuildProgress"));
            var restored=MappingInputs.fromFrozen(input.xml().bytes(),input.ocl().bytes(),input.sources(),"offline");
            assertEquals(csl.fingerprint(),loader.csl(restored).fingerprint());
        }
    }
    @Test void alteredAuthoredModelIsRejectedRatherThanSilentlyUsedWithAnotherMapping() throws Exception {
        Path root=Path.of("examples/hello-world"),changed=Files.writeString(temporary.resolve("other.csl"),Files.readString(root.resolve("hello.csl"))+"\n//different bytes\n");
        var error=assertThrows(IllegalArgumentException.class,()->MappingInputs.read(root.resolve("helloworld.jcm"),root.resolve("hello-mapping.xml"),root.resolve("hello.ocl"),changed,root.resolve("hello.istar")));
        assertTrue(error.getMessage().contains("AUTHORED_MODEL_CHECKSUM_MISMATCH"));
    }
    @Test void helloMaintenanceUsesEquivalentDirectIteratorFormWithoutChangingWorkspaceScope()throws Exception {
        Path root=Path.of("examples/hello-world");var csl=org.vnu.sme.goal.dsl.csl.parser.CslCompiler.compile(Files.readString(root.resolve("hello.csl"))).model();
        var model=new CslScopeModelAdapter().compile(csl).model();var system=new org.tzi.use.uml.sys.MSystem(model);var api=org.tzi.use.api.UseSystemApi.create(system,false);
        var self=api.createObject("GreetingRun","run");api.setAttributeValue("run","workspace","'common'");
        String selected="Console.allInstances()->select(c | c.workspace = self.workspace)->notEmpty() and Console.allInstances()->select(c | c.workspace = self.workspace)->forAll(c | c.numMsg >= 0)";
        var goal=(org.vnu.sme.goal.dsl.istar.mm.Goal)org.vnu.sme.goal.dsl.istar.parser.IStarCompiler.compile(Files.readString(root.resolve("hello.istar"))).model().findElement("NonnegativeCounts").orElseThrow();
        try(var backend=new org.tzi.use.semantic.engine.use75.NativeOclBackend(model,"test","equivalent-maintenance")) {
            var original=backend.compile(new org.tzi.use.semantic.engine.use75.OclContractSpec("selected",org.tzi.use.semantic.engine.use75.OclContractSpec.Kind.GOAL,self.cls(),null,selected));
            var direct=backend.compile(new org.tzi.use.semantic.engine.use75.OclContractSpec("direct",org.tzi.use.semantic.engine.use75.OclContractSpec.Kind.GOAL,self.cls(),null,goal.oclSource()));
            for(int scenario=0;scenario<4;scenario++) {
                if(scenario==1){api.createObject("Console","other");api.setAttributeValue("other","workspace","'other'");api.setAttributeValue("other","numMsg","-1");}
                if(scenario==2){api.createObject("Console","console");api.setAttributeValue("console","workspace","'common'");api.setAttributeValue("console","numMsg","0");}
                if(scenario==3)api.setAttributeValue("console","numMsg","-1");
                var state=org.tzi.use.semantic.engine.use75.UseStateReadView.pin(null,system.state());var variables=Map.<String,org.tzi.use.uml.ocl.value.Value>of("self",self.value());
                var expected=backend.evaluate(original,state,variables,"");var actual=backend.evaluate(direct,state,variables,"");
                assertEquals(expected.status(),actual.status());assertEquals(scenario==2?org.tzi.use.semantic.engine.use75.NativeOclOutcome.Status.PASS:org.tzi.use.semantic.engine.use75.NativeOclOutcome.Status.FAIL,actual.status());
            }
        }
    }
    @Test void explicitNumericTextConverterPreservesPublicFiniteValuesWithoutClamping() {
        var node=new MappingSpecification.Node("converter",Map.of("id","numeric-text","inputType","String","outputType","Real","algorithm","parse-finite-java-number-text","onMismatch","incomplete","onOverflowOrUnsupportedBackend","incomplete-do-not-clamp","sentinelReplacement","forbidden"),"",List.of(),new MappingSpecification.Location("mapping.xml",1,1,"numeric-text"));
        RuntimeValueConverters.validate(node);
        for(String source:List.of("2000","300.0","-0.0","2.2250738585072014E-308","1.7976931348623157E308"))
            assertEquals(Double.doubleToRawLongBits(Double.parseDouble(source)),Double.doubleToRawLongBits(((RealValue)RuntimeValueConverters.decode(node,source)).value()));
        for(Object bad:List.of("NaN","Infinity","1e9999","abc","", " 300 ",300))assertThrows(RuntimeValueConverters.Unavailable.class,()->RuntimeValueConverters.decode(node,bad));
    }
    private static String sha(Path path)throws Exception{return TraceComplement.Asset.of("hash","bytes",Files.readAllBytes(path)).sha256();}

    @Test void projectExecutionContextRequiresAnExplicitVersionedAbstractionAndNeverAnAgentSource() throws Exception {
        var csl=org.vnu.sme.goal.dsl.csl.parser.CslCompiler.compile("csl v4.0 ContextView { orgContext Run {} }").model();
        var goals=org.vnu.sme.goal.dsl.istar.parser.IStarCompiler.compile("istar View { role Observer { goal Outcome : Achieve } }").model();
        var node=new org.tzi.use.plugins.jacamo.semantics.mapping.NormalizedSourceModel.Node("project","Project",org.tzi.use.plugins.jacamo.semantics.mapping.NormalizedSourceModel.Layer.MODEL_DEFINITION,"JACAMO_PROJECT","jacamo.project.JaCaMoProject","",Map.of());
        var source=new org.tzi.use.plugins.jacamo.semantics.mapping.NormalizedSourceModel("official","hash","",List.of(node),List.of());
        var binding=new org.tzi.use.plugins.jacamo.semantics.mapping.SemanticBindingIndex.Binding("project",org.tzi.use.plugins.jacamo.semantics.mapping.SemanticBindingIndex.Kind.ORG_CONTEXT,"Run","Run","context","Physical workspace execution viewpoint",Map.of("contextAbstraction","PROJECT_EXECUTION_VIEW/1.0"));
        var index=new org.tzi.use.plugins.jacamo.semantics.mapping.SemanticBindingIndex(List.of(binding));
        assertDoesNotThrow(()->index.validate(source,csl,goals,List.of()));
        var unapproved=new org.tzi.use.plugins.jacamo.semantics.mapping.SemanticBindingIndex.Binding("project",binding.kind(),"Run","Run","context","",Map.of());
        assertThrows(IllegalArgumentException.class,()->new org.tzi.use.plugins.jacamo.semantics.mapping.SemanticBindingIndex(List.of(unapproved)).validate(source,csl,goals,List.of()));
        var agent=new org.tzi.use.plugins.jacamo.semantics.mapping.NormalizedSourceModel.Node("project","Agent",node.layer(),"JACAMO_AGENT_DECLARATION","jacamo.project.JaCaMoAgentParameters","",Map.of());
        assertThrows(IllegalArgumentException.class,()->index.validate(new org.tzi.use.plugins.jacamo.semantics.mapping.NormalizedSourceModel("official","hash","",List.of(agent),List.of()),csl,goals,List.of()));
    }
}
