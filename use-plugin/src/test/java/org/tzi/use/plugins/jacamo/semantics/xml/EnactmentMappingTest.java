package org.tzi.use.plugins.jacamo.semantics.xml;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tzi.use.plugins.jacamo.semantics.mapping.ocl.CslScopeModelAdapter;
import org.tzi.use.plugins.jacamo.semantics.mapping.xml.*;
import org.tzi.use.plugins.jacamo.semantics.runtime.SemanticResultAdapter;
import org.tzi.use.plugins.jacamo.codegrounded.CodeGroundedNativePipeline;
import org.tzi.use.plugins.jacamo.codegrounded.use.NativeProjectionMode;
import org.tzi.use.plugins.jacamo.ui.use4goal.state.RuntimeStateViews;
import static org.junit.jupiter.api.Assertions.*;

class EnactmentMappingTest {
    @TempDir Path temp;
    @Test void neutralTwoPlayerJoinRequiresExactRepresentedAgentAndActualRoleLinkAndSurvivesFrozenNavigation()throws Exception {
        var csl=new org.tzi.use.semantic.engine.frontend.CslFrontend().compile("csl v4.0 Neutral { orgContext Collective { role Operator; } orgContext Skill { } }","neutral").requireModel();
        var model=new CslScopeModelAdapter().compile(csl).model();
        model.getClass("Skill").addAnnotation(new org.tzi.use.uml.mm.MElementAnnotation("CslEnactmentView",Map.of("version","SINGLE_AGENT_ENACTMENT/1.0","roles","Operator")));
        var system=new org.tzi.use.uml.sys.MSystem(model);var api=org.tzi.use.api.UseSystemApi.create(system,false);
        for(String name:List.of("one","two")){api.createObject("Skill","skill_"+name);api.createObject("AtomicAgent","agent_"+name);api.createLink("CslAgentContexts","skill_"+name,"agent_"+name);}
        api.createObject("Operator","actualRole");api.createLink("Plays","agent_one","actualRole");
        var selected=Set.of("skill_one","skill_two","actualRole");
        var frozen=SemanticResultAdapter.presentationLinks(system.state(),selected);
        assertEquals(1,frozen.size());assertEquals(List.of("skill_one","actualRole"),frozen.getFirst().get("participants"));assertEquals("agent_one",frozen.getFirst().get("via"));
        assertEquals(List.of("CslAgentContexts","Plays"),frozen.getFirst().get("witness"));
        assertTrue(SemanticResultAdapter.presentationLinks(system.state(),Set.of("skill_two","actualRole")).isEmpty(),"No cross-player or type-only enactment");
        var evidence=Map.<String,Object>of("presentationState",Map.of("qualifiedValues",true,"objects",Map.of("Skill:skill_one",Map.of(),"Skill:skill_two",Map.of(),"Operator:actualRole",Map.of()),"links",frozen));
        api.deleteObject("actualRole");
        assertTrue(SemanticResultAdapter.presentationLinks(system.state(),selected).isEmpty(),"Removal must change current view");
        var recorded=RuntimeStateViews.aol(csl,evidence,"!new Something('raw_soil')");assertEquals(1,recorded.associationLinks().size(),"Frozen link must remain browsable without a live image");
        assertEquals("enacts",recorded.associationLinks().getFirst().relationName());assertEquals(3,recorded.objects().size());
    }
    @Test void misspelledOrDuplicateRoleAndWrongContextAbstractionAreRejected()throws Exception {
        Path root=Path.of("examples/auction");var source=GenericMappingEngineTest.official(root.resolve("auction.jcm"));
        String original=Files.readString(root.resolve("auction-mapping.xml"));
        for(var bad:Map.of("value=\"auctioneer participant\"","value=\"auctioneer MissingRole\"","value=\"auctioneer participant\" ","value=\"auctioneer auctioneer\"","SINGLE_AGENT_CONTEXT/1.0","PROJECT_EXECUTION_VIEW/1.0").entrySet()){
            Path xml=temp.resolve("invalid.xml");Files.writeString(xml,original.replace(bad.getKey().trim(),bad.getValue()));
            var program=new GenericMappingEngine().map(source,MappingInputs.read(root.resolve("auction.jcm"),xml,root.resolve("auction.ocl"),root.resolve("auction.csl"),root.resolve("auction.istar"),false));
            var error=assertThrows(IllegalArgumentException.class,()->new CodeGroundedNativePipeline().buildCanonicalProgram(source,NativeProjectionMode.AUTO,program));
            assertTrue(error.getMessage().contains("ENACTMENT_"),error.toString());
        }
    }
}
