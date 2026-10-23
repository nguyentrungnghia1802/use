package org.tzi.use.plugins.jacamo.semantics.xml;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tzi.use.plugins.jacamo.semantics.mapping.xml.*;
import org.tzi.use.plugins.jacamo.semantics.mapping.auction.AuctionIStarSourceAdapter;
import org.tzi.use.semantic.engine.api.*;
import org.tzi.use.semantic.engine.frontend.IStarFrontend;
import static org.junit.jupiter.api.Assertions.*;
class IStarRuleExecutorTest {
    @TempDir Path temp;
    @Test void generatesOriginalGoalModelMatchingOracleAndItsStrictPolicyLimitations()throws Exception {
        var inputs=CslRuleExecutorTest.auction(CslRuleExecutorTest.CASE);var generated=new IStarRuleExecutor().generate(inputs);
        // Historical metadata adapter is test-only here: the new compiler must never call it.
        String original=Files.readString(CslRuleExecutorTest.CASE.resolve("auction_istar_final.istar"));
        String view=new AuctionIStarSourceAdapter().prepare(original,"test-only-oracle").executableSource();
        var oracle=new IStarFrontend().compile(view,"test-only-oracle",EngineSemanticsProfile.DERIVED_AND_RESOURCE_V1).requireModel();
        assertEquals(OwnedGoalModel.fingerprint(oracle),OwnedGoalModel.fingerprint(generated));
        assertEquals(3,new IStarFrontend().validate(generated,"strict").diagnostics().size());
        assertEquals(3,generated.getDependencies().size());assertEquals(3,generated.getActors().stream().mapToInt(a->a.neededBys().size()).sum());
    }
    private MappingInputs variant(String editFrom,String editTo)throws Exception {
        Path root=CslRuleExecutorTest.CASE;var inputs=CslRuleExecutorTest.auction(root);
        for(var f:inputs.specification().root().child("sources").children("file")){Path t=temp.resolve(f.attr("path"));Files.createDirectories(t.getParent());Files.copy(root.resolve(f.attr("path")),t,StandardCopyOption.REPLACE_EXISTING);}
        Files.copy(root.resolve("auction_constraints_final.ocl"),temp.resolve("auction_constraints_final.ocl"),StandardCopyOption.REPLACE_EXISTING);
        Files.writeString(temp.resolve("auction-mapping.xml"),Files.readString(root.resolve("auction-mapping.xml")).replace(editFrom,editTo));
        return CslRuleExecutorTest.auction(temp);
    }
    @Test void oracleAbsenceAndCorruptionCannotBuildOrChangeTheGraphButXmlCan()throws Exception {
        var first=new IStarRuleExecutor().generate(variant("impossible-original","unused"));
        Files.writeString(temp.resolve("auction_istar_final.istar"),"not an iStar model");Files.writeString(temp.resolve("auction_csl.csl"),"not CSL");
        assertEquals(OwnedGoalModel.fingerprint(first),OwnedGoalModel.fingerprint(new IStarRuleExecutor().generate(CslRuleExecutorTest.auction(temp))));
        var changed=new IStarRuleExecutor().generate(variant("O_root","O_finished"));
        assertTrue(changed.findElement("O_finished").isPresent());assertNotEquals(OwnedGoalModel.fingerprint(first),OwnedGoalModel.fingerprint(changed));
    }
    @Test void unresolvedEndpointAndSequenceAreRejectedRatherThanRewrittenToAnd()throws Exception {
        assertThrows(IllegalArgumentException.class,()->new IStarRuleExecutor().generate(variant("to=\"A_start\"","to=\"missing\"")));
        assertThrows(IllegalArgumentException.class,()->new IStarRuleExecutor().generate(variant("kind=\"AND\"","kind=\"SEQUENCE\"")));
        assertThrows(IllegalArgumentException.class,()->new IStarRuleExecutor().generate(variant("toActor=\"Auctioneer\"","toActor=\"Participant\"")));
        assertThrows(IllegalArgumentException.class,()->new IStarRuleExecutor().generate(variant("engineProfile=\"DERIVED_AND_RESOURCE_V1\"","engineProfile=\"USE4GOAL_OBSERVED_V1\"")));
    }
    @Test void secondCaseGeneratesSelectedActorAndNativeAndOrDependencyResourceWithMetadata()throws Exception {
        var jcm=Files.writeString(temp.resolve("demo.jcm"),"mas Worker {}\n");
        var ocl=Files.writeString(temp.resolve("demo.ocl"),"context Item inv Valid: true\n");
        String xml=MappingXmlReaderTest.XML.replace("1".repeat(64),hash(jcm)).replace("2".repeat(64),hash(ocl))
            .replace("<selectors/>","<selectors><select id=\"actor-name\" sourceRef=\"project\" syntax=\"regex\" expectedCount=\"1\"><pattern>mas\\s+(?P&lt;name&gt;\\w+)</pattern></select></selectors>")
            .replace("<istar name=\"SampleGoals\"/>","""
                <istar name="Dispatch">
                  <actor id="planner" name="Planner" kind="agent">
                    <goal id="external" name="External" type="Achieve"/>
                    <goal id="root" name="Root" type="Achieve"/>
                    <task id="left" name="Left"><refine kind="AND" target="Root"/><pre>true</pre><post>true</post></task>
                    <task id="right" name="Right"><refine kind="AND" target="Root"/><pre>true</pre><post>true</post></task>
                    <goal id="choice" name="Choice" type="Achieve"/>
                    <task id="route-a" name="RouteA"><refine kind="OR" target="Choice"/><pre>true</pre><post>true</post></task>
                    <task id="route-b" name="RouteB"><refine kind="OR" target="Choice"/><pre>true</pre><post>true</post></task>
                    <resource id="tool" name="Tool"><description>Explicit tool evidence</description><neededBy task="RouteA"/></resource>
                  </actor>
                  <actor id="worker" name="${name}" kind="role" selectRef="actor-name">
                    <goal id="leaf" name="Leaf" type="Achieve"><condition>true</condition></goal>
                  </actor>
                  <dependency id="dispatch" name="DispatchDuty" kind="goal" fromActor="Planner" from="External" toActor="Worker" to="Leaf"/>
                </istar>
                """);
        Path mapping=Files.writeString(temp.resolve("mapping.xml"),xml);
        var result=new IStarRuleExecutor().prepare(MappingInputs.read(jcm,mapping,ocl));
        var model=result.model();assertTrue(model.findActor("Worker").isPresent());
        var planner=model.findActor("Planner").orElseThrow();
        assertEquals(3,planner.refinements().size());assertEquals(1,planner.neededBys().size());assertEquals(1,model.getDependencies().size());
        assertTrue(result.traces().stream().anyMatch(t->t.target().equals("Planner.Tool")&&t.description().equals("Explicit tool evidence")));
        assertTrue(new IStarFrontend().validate(model,"Case B",result.profile()).ok());
        Files.writeString(mapping,xml.replace("name=\"Leaf\"","name=\"Right\""));
        assertThrows(IllegalArgumentException.class,()->new IStarRuleExecutor().generate(MappingInputs.read(jcm,mapping,ocl)));
    }
    private static String hash(Path path)throws Exception{return org.tzi.use.plugins.jacamo.semantics.evidence.TraceComplement.Asset.of("check","bytes",Files.readAllBytes(path)).sha256();}
}
