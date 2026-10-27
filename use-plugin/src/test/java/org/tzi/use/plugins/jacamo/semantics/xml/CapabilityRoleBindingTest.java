package org.tzi.use.plugins.jacamo.semantics.xml;

import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tzi.use.plugins.jacamo.semantics.mapping.xml.*;
import static org.junit.jupiter.api.Assertions.*;

class CapabilityRoleBindingTest {
    @TempDir Path root;
    @Test void sharedProgramIsAnExplicitModelOnlyRoleAbstractionNeverAnExtraMoiseRole()throws Exception {
        var helper=new GenericMappingEngineTest();helper.temp=root;helper.input();
        var source=GenericMappingEngineTest.official(GenericMappingEngineTest.JCM);
        String declaration=source.semanticContract().jasonPrograms().getFirst().declarationId();
        Path csl=Files.writeString(root.resolve("view.csl"),"csl v4.0 CounterView { role Capability { attribute knowledge : String optional mutable; } role Worker specializes Capability; entity Counter { attribute p : Integer required mutable; } }");
        Path istar=Files.writeString(root.resolve("view.istar"),"istar Requirements { role Capability {} role Worker { Worker is-a Capability goal Positive : Achieve condition {[ self.p >= 0 ]} } }");
        String xml=Files.readString(root.resolve("mapping.xml"))
            .replace("USE4GOAL_OBSERVED_V1","ROLE_SPECIALIZATION_V1")
            .replaceFirst("(?s)<modelRules .*?</modelRules>","<modelRules targetVersion=\"v4.0\" targetModel=\"CounterView\"/>")
            .replaceFirst("(?s)<istar .*?</istar>","<istar name=\"Requirements\"/>")
            .replace("<selectors>","<models mode=\"AUTHORED\" cslSha256=\""+GenericMappingEngineTest.hash(csl)+"\" istarSha256=\""+GenericMappingEngineTest.hash(istar)+"\"/><selectors>")
            .replace("</bindings>","""
                <static id="capability-role" kind="ROLE" target="Capability" sourceKind="JASON_AGENT_PROGRAM" feature="declarationId" value="%s" reason="Shared program capability abstraction">
                  <metadata key="roleAbstraction" value="ASL_CAPABILITY_VIEW"/>
                </static>
                <static id="worker-role" kind="ROLE" target="Worker" sourceKind="MOISE_ROLE" feature="roleId" value="worker" reason="Actual role definition"/>
                <static id="capability-actor" kind="ACTOR" target="Capability" sourceKind="JASON_AGENT_PROGRAM" feature="declarationId" value="%s" reason="Empty intentional capability abstraction"/>
                <actorRuntime actor="Capability" bindingRef="capability-actor" activationPolicy="MODEL_ONLY_CAPABILITY_ROLE" episodePolicy="MODEL_REVISION"/>
                <oclClause name="Nonnegative" context="context Counter"/>
                </bindings>
                """.formatted(declaration,declaration));
        Path mapping=Files.writeString(root.resolve("mapping.xml"),xml);
        var program=new GenericMappingEngine().map(source,MappingInputs.read(GenericMappingEngineTest.JCM,mapping,root.resolve("requirements.ocl"),csl,istar));
        assertEquals(2,program.goalModel().getActors().size());assertEquals(2,program.cslModel().roles().size());
        assertDoesNotThrow(()->new org.tzi.use.plugins.jacamo.semantics.mapping.ocl.XmlNativePreparation().prepare(source,program,new org.tzi.use.plugins.jacamo.codegrounded.trace.CodeGroundedTraceCollector()));
        for(String invalid:new String[]{xml.replace("ASL_CAPABILITY_VIEW","IMPLICIT"),xml.replace("activationPolicy=\"MODEL_ONLY_CAPABILITY_ROLE\"","activationPolicy=\"MODEL_ONLY_CAPABILITY_ROLE\" roleBindingRef=\"capability-role\"")}) {
            Files.writeString(mapping,invalid);
            assertThrows(IllegalArgumentException.class,()->new GenericMappingEngine().map(source,MappingInputs.read(GenericMappingEngineTest.JCM,mapping,root.resolve("requirements.ocl"),csl,istar)));
        }
    }
}
