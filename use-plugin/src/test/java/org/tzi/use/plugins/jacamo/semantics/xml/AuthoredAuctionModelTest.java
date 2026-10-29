package org.tzi.use.plugins.jacamo.semantics.xml;

import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.tzi.use.semantic.engine.api.*;
import org.tzi.use.semantic.engine.frontend.*;
import org.tzi.use.plugins.jacamo.semantics.mapping.ocl.CslScopeModelAdapter;
import org.vnu.sme.goal.dsl.istar.mm.*;
import static org.junit.jupiter.api.Assertions.*;

class AuthoredAuctionModelTest {
    private static final Path INPUT=Path.of("src/test/resources/auction-reference/authored-v1");
    @Test void exactUpdatedAuthoredModelsRetainGraphAndUseExplicitProfileInsteadOfInventedTask()throws Exception {
        var csl=new CslFrontend().compile(Files.readString(INPUT.resolve("auction_csl.csl")),"auction_csl.csl").requireModel();
        var result=new IStarFrontend().compile(Files.readString(INPUT.resolve("auction_istar_final.istar")),"auction_istar_final.istar",EngineSemanticsProfile.ROLE_SPECIALIZATION_V1);
        assertTrue(result.ok(),result.diagnostics().toString());var goals=result.requireModel();
        assertEquals(4,GoalModelValidator.validate(goals).size(),"Strict-original limitations remain explicit");
        assertEquals(4,goals.getActors().size());assertEquals(5,goals.getActors().stream().flatMap(a->a.elements().stream()).filter(Task.class::isInstance).count());
        assertEquals(2,goals.getActors().stream().flatMap(a->a.associations().stream()).filter(a->a.kind()==AssocKind.IS_A).count());
        assertEquals(3,goals.getDependencies().size());assertTrue(goals.findElement("O_bid_accepted").isPresent());assertTrue(goals.findElement("O_bid_attempt").isPresent());assertTrue(goals.findElement("A_decide_t1").isEmpty());
        assertEquals(3,csl.roles().size());assertEquals(2,csl.generalizations().size());
        var binding=new CslScopeModelAdapter().compile(csl);
        for(String role:new String[]{"auctioneer","participant"}) {
            var type=binding.model().getClass(role);
            assertTrue(type.isSubClassifierOf(binding.model().getClass("auction_capabilities")));
            assertEquals(9,type.allAttributes().stream().filter(a->a.name().startsWith("belief")).count());
        }
        assertEquals("0d0872612661235c0d7865c177dc31dfe2ef649913a99750eb9e42f87163de37",GenericMappingEngineTest.hash(INPUT.resolve("auction_csl.csl")));
        assertEquals("a3bb1203367f570d8a3cb9b9f5b1a6619d5e4db189ef304f409ca6cb49aea2a9",GenericMappingEngineTest.hash(INPUT.resolve("references/auction_istar_success_only_v2.istar")));
        assertEquals("891c77f07981e03c808cfdaf23285d5a6d9050859954f5f561f7308aab81039a",GenericMappingEngineTest.hash(INPUT.resolve("auction_constraints_final_fixed.ocl")));
    }
}
